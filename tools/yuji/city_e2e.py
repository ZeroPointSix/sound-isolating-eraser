#!/usr/bin/env python3
"""Run the opt-in Rain City QA client and server in a disposable, private world.

Requires Java 17, Xvfb and the project's normal Forge development dependencies.
No third-party Python packages, external game account or Devin service is used.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import selectors
import shlex
import shutil
import signal
import socket
import struct
import subprocess
import sys
import time
import zlib


PHASES = ("grey", "black", "buddha_idle", "buddha_fall", "beam", "returned")
CLIENT_OPTIONS = (
    "version:3465\nrenderDistance:5\nsimulationDistance:5\nmaxFps:30\nenableVsync:false\n"
    "fullscreen:false\npauseOnLostFocus:false\ngraphicsMode:1\nparticles:0\n"
    "soundCategory_master:0.0\nclouds:false\nfov:1.0\ngamma:1.0\nautoJump:false\n"
    # This flag requests onboarding, rather than recording that it was completed.
    "onboardAccessibility:false\nskipMultiplayerWarning:true\n"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def atomic_json(path: Path, value: dict) -> None:
    temporary = path.with_name(path.name + ".python-tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def paeth(left: int, above: int, upper_left: int) -> int:
    prediction = left + above - upper_left
    a, b, c = abs(prediction - left), abs(prediction - above), abs(prediction - upper_left)
    return left if a <= b and a <= c else above if b <= c else upper_left


def png_pixels(encoded: bytes) -> tuple[int, int, int, bytes]:
    require(encoded[:8] == b"\x89PNG\r\n\x1a\n", "Screenshot is not a PNG")
    offset, image_data = 8, bytearray()
    header = None
    ended = False
    while offset + 12 <= len(encoded):
        size = struct.unpack_from(">I", encoded, offset)[0]
        require(size <= 64 * 1024 * 1024 and offset + 12 + size <= len(encoded), "Invalid PNG chunk length")
        kind = encoded[offset + 4:offset + 8]
        payload = encoded[offset + 8:offset + 8 + size]
        crc = struct.unpack_from(">I", encoded, offset + 8 + size)[0]
        require(zlib.crc32(kind + payload) & 0xFFFFFFFF == crc, "PNG checksum mismatch")
        if kind == b"IHDR":
            require(header is None and size == 13, "Invalid PNG header")
            header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            image_data.extend(payload)
        elif kind == b"IEND":
            ended = True
            break
        offset += 12 + size
    require(header is not None and ended, "Incomplete PNG screenshot")
    width, height, depth, color, compression, filtering, interlace = header
    require(1 <= width <= 4096 and 1 <= height <= 4096, "Unsupported screenshot dimensions")
    require(depth == 8 and color in (2, 6) and compression == filtering == interlace == 0,
            "Expected an 8-bit, non-interlaced RGB/RGBA Minecraft screenshot")
    channels = 3 if color == 2 else 4
    stride = width * channels
    expected = height * (stride + 1)
    decoder = zlib.decompressobj()
    raw = decoder.decompress(bytes(image_data), expected + 1)
    require(len(raw) == expected and decoder.eof and not decoder.unconsumed_tail, "Invalid PNG pixel data length")
    result = bytearray(height * stride)
    previous = bytearray(stride)
    for y in range(height):
        start = y * (stride + 1)
        mode = raw[start]
        require(0 <= mode <= 4, "Invalid PNG scanline filter")
        row = bytearray(raw[start + 1:start + 1 + stride])
        if mode:
            for x in range(stride):
                left = row[x - channels] if x >= channels else 0
                above = previous[x]
                upper_left = previous[x - channels] if x >= channels else 0
                prediction = (left if mode == 1 else above if mode == 2 else
                              (left + above) // 2 if mode == 3 else paeth(left, above, upper_left))
                row[x] = (row[x] + prediction) & 255
        result[y * stride:(y + 1) * stride] = row
        previous = row
    return width, height, channels, bytes(result)


def inspect_png(path: Path) -> dict:
    encoded = path.read_bytes()
    width, height, channels, pixels = png_pixels(encoded)
    require(width >= 320 and height >= 240, "Screenshot is too small to inspect")
    colors, lumas = set(), []
    opaque, nonblack = 0, 0
    step = max(1, min(width, height) // 80)
    for y in range(height // 10, height * 9 // 10, step):
        for x in range(width // 10, width * 9 // 10, step):
            index = (y * width + x) * channels
            red, green, blue = pixels[index:index + 3]
            if channels == 4 and pixels[index + 3] < 128:
                continue
            opaque += 1
            colors.add((red, green, blue))
            lumas.append((red * 299 + green * 587 + blue * 114) / 1000)
            nonblack += max(red, green, blue) > 8
    require(opaque > 100, "Screenshot has no opaque world pixels")
    mean = sum(lumas) / opaque
    deviation = math.sqrt(sum((value - mean) ** 2 for value in lumas) / opaque)
    require(nonblack / opaque >= .02 and len(colors) >= 32 and max(lumas) - min(lumas) >= 12 and deviation >= 3,
            "Screenshot is blank or visually uniform")
    return {"width": width, "height": height, "sha256": hashlib.sha256(encoded).hexdigest(),
            "distinctSampleColors": len(colors), "nonblackFraction": nonblack / opaque,
            "meanLuminance": mean, "luminanceDeviation": deviation}


def expand(value: str, root: Path) -> str:
    value = re.sub(r"\$\{workspaceFolder(?::[^}]+)?\}", lambda _: str(root), value)
    value = re.sub(r"\$\{env:([^}]+)\}", lambda match: os.environ.get(match.group(1), ""), value)
    require("${" not in value, "Unresolved launch variable: " + value)
    return value


def words(value: str | list, root: Path) -> list[str]:
    return [expand(str(item), root) for item in value] if isinstance(value, list) else shlex.split(expand(value, root))


def launch_configuration(root: Path, name: str) -> dict:
    document = json.loads((root / ".vscode/launch.json").read_text(encoding="utf-8"))
    matches = [item for item in document["configurations"]
               if re.search(r"(?:^|[\s:/])" + re.escape(name) + r"(?:$|[\s(])", item.get("name", ""))]
    require(len(matches) == 1, f"Expected exactly one generated {name} launch configuration, found {len(matches)}")
    return matches[0]


def set_argument(arguments: list[str], key: str, value: str) -> list[str]:
    result, index = [], 0
    while index < len(arguments):
        item = arguments[index]
        if item == key:
            require(index + 1 < len(arguments), "Missing launch argument value for " + key)
            index += 2
        elif item.startswith(key + "="):
            index += 1
        else:
            result.append(item)
            index += 1
    return result + [key, value]


def qa_mod_classes(value: str) -> str:
    entries = value.split(os.pathsep)
    entries = [entry for entry in entries if not re.search(r"(?:^|[/\\])yujiTest(?:[/\\]|$)", entry)]
    require(any(entry.startswith("raincity_qa%%") for entry in entries),
            "MOD_CLASSES lacks raincity_qa; regenerate VS Code runs with -PcityE2E")
    require(all("%%" in entry for entry in entries), "Invalid generated MOD_CLASSES")
    return os.pathsep.join(entries)


def game_command(root: Path, run: Path, side: str, port: int, display: str, java: str) -> tuple[list[str], dict]:
    config = launch_configuration(root, "runClient" if side == "client" else "runServer")
    environment = os.environ.copy()
    for key, value in config.get("env", {}).items():
        if value is not None:
            environment[key] = expand(str(value), root)
    require("MOD_CLASSES" in config.get("env", {}), "Generated launch configuration has no MOD_CLASSES")
    environment["MOD_CLASSES"] = qa_mod_classes(environment["MOD_CLASSES"])
    environment.update({"DISPLAY": display, "ALSOFT_DRIVERS": "null", "LIBGL_ALWAYS_SOFTWARE": "1"})
    classpath = (root / "build/city-test-classpath.txt").read_text(encoding="utf-8").strip()
    require(classpath, "Empty city-test classpath")
    classpath = os.pathsep.join(part for part in classpath.split(os.pathsep)
                               if not re.search(r"(?:^|[/\\])yujiTest(?:[/\\]|$)", part))
    vm = words(config.get("vmArgs", ""), root)
    cleaned, index = [], 0
    while index < len(vm):
        value = vm[index]
        if value in ("-cp", "-classpath", "--class-path"):
            index += 2
            continue
        if not (value.startswith(("-Xmx", "-Xms", "-Dyuji.city.qa.", "--class-path="))):
            cleaned.append(value)
        index += 1
    memory = "1536m" if side == "client" else "1024m"
    cleaned += ["-Xms256m", "-Xmx" + memory, "-Dyuji.city.qa.enabled=true",
                "-Dyuji.city.qa.dir=" + str(run), "-Dyuji.city.qa.port=" + str(port)]
    arguments = words(config.get("args", ""), root)
    arguments = set_argument(arguments, "--gameDir", str(run / side))
    if side == "client":
        arguments = set_argument(arguments, "--username", "RainCityQA")
        arguments = set_argument(arguments, "--width", "1280")
        arguments = set_argument(arguments, "--height", "720")
    elif "--nogui" not in arguments and "nogui" not in arguments:
        arguments.append("--nogui")
    return [java, *cleaned, "-cp", classpath, expand(config["mainClass"], root), *arguments], environment


class Processes:
    """Track only process groups created by this invocation; never search/kill by name."""

    def __init__(self) -> None:
        self.items: list[tuple[str, subprocess.Popen]] = []
        self.logs = []

    def start(self, name: str, command: list[str], cwd: Path, log: Path, env: dict | None = None,
              display_pipe: bool = False) -> subprocess.Popen:
        handle = log.open("wb")
        self.logs.append(handle)
        process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE if display_pipe else handle,
                                   stderr=handle if display_pipe else subprocess.STDOUT, start_new_session=True)
        self.items.append((name, process))
        return process

    def cleanup(self) -> None:
        for _, process in reversed(self.items):
            if process.poll() is not None:
                continue
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        deadline = time.monotonic() + 12
        for _, process in reversed(self.items):
            try:
                process.wait(timeout=max(.1, deadline - time.monotonic()))
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=10)
            if process.stdout is not None:
                process.stdout.close()
        for handle in self.logs:
            handle.close()


def wait_display(process: subprocess.Popen) -> str:
    with selectors.DefaultSelector() as selector:
        selector.register(process.stdout, selectors.EVENT_READ)
        require(bool(selector.select(20)), "Xvfb did not allocate a display within 20 seconds")
        number = process.stdout.readline().decode("ascii").strip()
    require(number.isdigit() and process.poll() is None, "Xvfb failed to start; see xvfb.log")
    return ":" + number


def reject_failure_markers(run: Path) -> None:
    for side in ("server", "client"):
        path = run / (side + "-failure.json")
        if path.exists():
            value = json.loads(path.read_text(encoding="utf-8"))
            raise RuntimeError(f"{side} failed in {value.get('phase')}: {value.get('error')}")


def failure_log_excerpt(text: str) -> str:
    lines = text.splitlines()
    selected = set(range(max(0, len(lines) - 15), len(lines)))
    issue = re.compile(r"(?:/|\s|\[)(?:ERROR|FATAL)[ \]/:]|Missing metadata in pack|"
                       r"ModLoadingException|LoadingFailedException|LoadingErrorScreen|brokenresources")
    matches = [index for index, line in enumerate(lines) if issue.search(line)]
    for index in matches[:5]:
        selected.update(range(max(0, index - 2), min(len(lines), index + 13)))
    return "\n".join(f"{index + 1}: {lines[index][:1200]}" for index in sorted(selected))


def verify_capture(run: Path, phase: str, previous: dict) -> dict:
    captured = json.loads((run / (phase + ".captured.json")).read_text(encoding="utf-8"))
    require(captured["phase"] == phase, "Screenshot phase mismatch")
    expected_dimension = "minecraft:overworld" if phase == "returned" else "qihai:yuji_fodu"
    require(captured["dimension"] == expected_dimension, "Screenshot captured in wrong dimension")
    if phase == "grey":
        require(abs(captured["fogFar"] - 64) < .5, "Grey-rain fog was not actually applied")
    if phase in ("black", "beam"):
        require(abs(captured["fogFar"] - 32) < .5, "Black-rain fog was not actually applied")
    if phase in ("grey", "black", "beam"):
        expected = (.5, 32, 12.5) if phase == "beam" else (96.5, 132, 136.5)
        require(sum((captured[axis] - position) ** 2 for axis, position in zip(("x", "y", "z"), expected)) < .04,
                "Screenshot camera drifted from its requested position")
        if phase != "beam":
            require(abs((captured["yaw"] - 180 + 180) % 360 - 180) < .5 and abs(captured["pitch"] - 8) < .5,
                    "Rain screenshot camera orientation drifted")
    if phase.startswith("buddha"):
        require(captured["phaseBuddhaRenders"] > 0 and captured.get("buddhaDrawnInCapturedFrame") is True,
                "No actual GeoRenderEvent.Entity.Post for Buddha in the captured frame")
    if phase == "buddha_fall":
        require(35 < captured["renderedBuddhaY"] < 120, "Buddha was not actually rendered while falling")
    pixels = inspect_png(run / "screenshots" / (phase + ".png"))
    if phase == "black":
        require("grey" in previous and pixels["sha256"] != previous["grey"]["pixels"]["sha256"],
                "Grey and black screenshots are identical")
    result = {"passed": True, "phase": phase, "client": captured, "pixels": pixels}
    atomic_json(run / (phase + ".pass.json"), result)
    return result


def run_smoke(options: argparse.Namespace) -> int:
    root = options.root.resolve()
    require(1 <= options.port <= 65535, "Invalid private server port")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", options.port))
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else shutil.which("java")
    require(bool(java) and Path(java).is_file(), "Java executable not found")
    xvfb = shutil.which("Xvfb")
    require(xvfb is not None, "Xvfb is required for genuine rendered screenshots")
    run = root / "build" / ("yuji-city-e2e-" + time.strftime("%Y%m%d-%H%M%S") + "-" + str(os.getpid()))
    run.mkdir(parents=True, exist_ok=False)
    for side in ("server", "client", "screenshots"):
        (run / side).mkdir()
    print("City QA evidence: " + str(run), flush=True)
    processes = Processes()
    try:
        if not options.skip_build:
            gradle = root / "gradlew"
            require(gradle.is_file(), "Gradle wrapper not found in " + str(root))
            build = processes.start("build", [str(gradle), "--no-daemon", "-PcityE2E", "writeCityTestClasspath"],
                                    root, run / "build.log")
            try:
                code = build.wait(timeout=options.timeout)
            except subprocess.TimeoutExpired as failure:
                raise RuntimeError("City QA build timed out; see build.log") from failure
            require(code == 0, "City QA build failed; see build.log")
        (run / "server/eula.txt").write_text("eula=true\n", encoding="utf-8")
        (run / "server/server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=" + str(options.port) + "\nonline-mode=false\n"
            "enforce-secure-profile=false\nlevel-name=world\nlevel-type=minecraft:flat\n"
            "gamemode=creative\ndifficulty=normal\nallow-flight=true\nspawn-protection=0\n"
            "view-distance=5\nsimulation-distance=5\nmax-players=1\nmax-tick-time=120000\n"
            "enable-rcon=false\nenable-query=false\nwhite-list=false\nsync-chunk-writes=false\n",
            encoding="utf-8")
        (run / "client/options.txt").write_text(CLIENT_OPTIONS, encoding="utf-8")
        xserver = processes.start("Xvfb", [xvfb, "-displayfd", "1", "-screen", "0", "1280x720x24",
                                             "-nolisten", "tcp", "-noreset"], run, run / "xvfb.log", display_pipe=True)
        display = wait_display(xserver)
        server_command, server_env = game_command(root, run, "server", options.port, display, java)
        server = processes.start("server", server_command, run / "server", run / "server.log", server_env)
        deadline = time.monotonic() + options.timeout
        while True:
            reject_failure_markers(run)
            require(server.poll() is None, "Server exited before becoming ready; see server.log")
            require(xserver.poll() is None, "Xvfb exited unexpectedly")
            require(time.monotonic() < deadline, "Server startup timed out; see server.log")
            if re.search(r'Done \([\d.]+s\)!', (run / "server.log").read_text(encoding="utf-8", errors="replace")):
                break
            time.sleep(.5)
        client_command, client_env = game_command(root, run, "client", options.port, display, java)
        client = processes.start("client", client_command, run / "client", run / "client.log", client_env)
        captures = {}
        while True:
            reject_failure_markers(run)
            for phase in PHASES:
                if phase not in captures and (run / (phase + ".captured.json")).exists():
                    captures[phase] = verify_capture(run, phase, captures)
                    print("Verified rendered screenshot: " + phase, flush=True)
            success = run / "server-success.json"
            if success.exists():
                result = json.loads(success.read_text(encoding="utf-8"))
                require(result.get("passed") is True and result.get("actualKills") == 50
                        and result.get("physicalMarkKinds") == 10 and result.get("homeRestored") is True
                        and result.get("actualBellInteraction") is True and result.get("automaticBeamReturn") is True,
                        "Incomplete server success evidence")
                require(set(captures) == set(PHASES), "Server finished without every validated screenshot")
                atomic_json(run / "summary.json", {"passed": True, "server": result, "screenshots": captures})
                print("PASS: hospital entry, grey/black rain, 50 real kills, physical marks, bell, Buddha render/fall, beam and home return", flush=True)
                return 0
            require(server.poll() is None, "Server exited before success; see server.log")
            require(client.poll() is None, "Client exited before success; see client.log")
            require(xserver.poll() is None, "Xvfb exited before success")
            require(time.monotonic() < deadline, "City client smoke test timed out; see server-phase.json and logs")
            time.sleep(.1)
    except (Exception, KeyboardInterrupt) as failure:
        atomic_json(run / "summary.json", {"passed": False, "error": str(failure), "evidenceDirectory": str(run)})
        print("FAIL: " + str(failure), file=sys.stderr, flush=True)
        for name in ("server", "client", "build"):
            path = run / (name + ".log")
            if path.exists():
                print("\n" + name + " failure context and final log lines:\n"
                      + failure_log_excerpt(path.read_text(encoding="utf-8", errors="replace")),
                      file=sys.stderr)
        return 1
    finally:
        processes.cleanup()


def self_test() -> int:
    import tempfile
    import unittest

    def make_png(width: int, height: int, mode: int = 0, blank: bool = False, alpha: bool = False) -> bytes:
        channels = 4 if alpha else 3
        previous, raw = bytes(width * channels), bytearray()
        for y in range(height):
            row = bytes(channel for x in range(width) for channel in
                        ((0, 0, 0) if blank else (x % 256, y % 256, (x * 3 + y * 7) % 256)) + ((255,) if alpha else ()))
            raw.append(mode)
            for x, value in enumerate(row):
                left = row[x - channels] if x >= channels else 0
                above = previous[x]
                upper_left = previous[x - channels] if x >= channels else 0
                prediction = (0 if mode == 0 else left if mode == 1 else above if mode == 2 else
                              (left + above) // 2 if mode == 3 else paeth(left, above, upper_left))
                raw.append((value - prediction) & 255)
            previous = row

        def chunk(kind: bytes, payload: bytes) -> bytes:
            return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6 if alpha else 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

    class Checks(unittest.TestCase):
        def test_city_qa_has_valid_pack_metadata(self):
            path = Path(__file__).resolve().parents[2] / "src/cityTest/resources/pack.mcmeta"
            pack = json.loads(path.read_text(encoding="utf-8"))["pack"]
            self.assertEqual(pack["pack_format"], 15)
            self.assertTrue(pack["description"])

        def test_failure_context_keeps_early_pack_warning(self):
            text = "\n".join(["startup", "[Render thread/WARN] Missing metadata in pack mod:raincity_qa",
                              "[Render thread/ERROR] LoadingFailedException: broken resources"]
                             + ["atlas loaded"] * 80 + ["City QA client screen: LoadingErrorScreen"])
            excerpt = failure_log_excerpt(text)
            self.assertIn("Missing metadata in pack mod:raincity_qa", excerpt)
            self.assertIn("LoadingFailedException: broken resources", excerpt)
            self.assertIn("LoadingErrorScreen", excerpt)
            self.assertLess(len(excerpt.splitlines()), len(text.splitlines()))

        def test_fresh_client_does_not_request_onboarding(self):
            options = dict(line.split(":", 1) for line in CLIENT_OPTIONS.splitlines())
            self.assertEqual(options["onboardAccessibility"], "false")
            self.assertEqual(options["skipMultiplayerWarning"], "true")
            self.assertEqual(options["pauseOnLostFocus"], "false")

        def test_rgb_and_rgba_all_filters(self):
            for alpha in (False, True):
                expected = png_pixels(make_png(17, 11, alpha=alpha))
                for mode in range(5):
                    self.assertEqual(png_pixels(make_png(17, 11, mode, alpha=alpha)), expected)

        def test_valid_nonblank(self):
            with tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / "frame.png"
                path.write_bytes(make_png(320, 240, 4))
                self.assertGreater(inspect_png(path)["distinctSampleColors"], 32)

        def test_blank_is_failure(self):
            with tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / "frame.png"
                path.write_bytes(make_png(320, 240, blank=True))
                with self.assertRaisesRegex(RuntimeError, "blank"):
                    inspect_png(path)

        def test_corrupt_and_truncated_are_failure(self):
            valid = make_png(17, 11)
            with self.assertRaises(RuntimeError):
                png_pixels(valid[:-5])
            with self.assertRaisesRegex(RuntimeError, "checksum"):
                png_pixels(valid[:40] + bytes([valid[40] ^ 1]) + valid[41:])

        def test_only_city_qa_classes(self):
            value = os.pathsep.join(("main%%/a/main", "yuji_qa%%/a/yujiTest", "raincity_qa%%/a/cityTest"))
            self.assertNotIn("yujiTest", qa_mod_classes(value))
            with self.assertRaises(RuntimeError):
                qa_mod_classes("main%%/a/main")

        def test_expansion_and_argument_replacement(self):
            self.assertEqual(expand("${workspaceFolder}/build", Path("/test")), "/test/build")
            self.assertEqual(set_argument(["--username", "old", "--width=640"], "--username", "RainCityQA"),
                             ["--width=640", "--username", "RainCityQA"])
            with self.assertRaises(RuntimeError):
                expand("${unknown}", Path("/test"))

        def test_launch_uses_private_directories_and_limits(self):
            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                (root / ".vscode").mkdir()
                (root / "build").mkdir()
                config = {"name": "runClient", "mainClass": "example.Main", "vmArgs": "-Xmx8G -Xms2G -Dtest=1",
                          "args": "--gameDir run --username OldPlayer", "env": {"MOD_CLASSES":
                          "main%%${workspaceFolder}/main" + os.pathsep + "raincity_qa%%${workspaceFolder}/cityTest"
                          + os.pathsep + "yuji_qa%%${workspaceFolder}/yujiTest"}}
                (root / ".vscode/launch.json").write_text(json.dumps({"configurations": [config]}), encoding="utf-8")
                (root / "build/city-test-classpath.txt").write_text(os.pathsep.join(("/main", "/cityTest", "/yujiTest")), encoding="utf-8")
                command, environment = game_command(root, root / "private", "client", 25591, ":42", "/fake/java")
                self.assertIn("-Xmx1536m", command)
                self.assertNotIn("-Xmx8G", command)
                self.assertEqual(command[command.index("--username") + 1], "RainCityQA")
                self.assertEqual(command[command.index("--gameDir") + 1], str(root / "private/client"))
                self.assertNotIn("yujiTest", environment["MOD_CLASSES"])
                self.assertNotIn("yujiTest", command[command.index("-cp") + 1])
                self.assertEqual(environment["DISPLAY"], ":42")
                self.assertEqual(environment["ALSOFT_DRIVERS"], "null")

        def test_buddha_capture_requires_real_render_and_motion(self):
            with tempfile.TemporaryDirectory() as folder:
                run = Path(folder)
                marker = run / "buddha_fall.captured.json"
                capture = {"phase": "buddha_fall", "dimension": "qihai:yuji_fodu", "phaseBuddhaRenders": 0,
                           "renderedBuddhaY": 90}
                atomic_json(marker, capture)
                with self.assertRaisesRegex(RuntimeError, "GeoRenderEvent"):
                    verify_capture(run, "buddha_fall", {})
                capture.update(phaseBuddhaRenders=12, renderedBuddhaY=90, buddhaDrawnInCapturedFrame=False)
                atomic_json(marker, capture)
                with self.assertRaisesRegex(RuntimeError, "GeoRenderEvent"):
                    verify_capture(run, "buddha_fall", {})
                capture.update(renderedBuddhaY=123, buddhaDrawnInCapturedFrame=True)
                atomic_json(marker, capture)
                with self.assertRaisesRegex(RuntimeError, "falling"):
                    verify_capture(run, "buddha_fall", {})
                self.assertFalse((run / "buddha_fall.pass.json").exists())

        def test_wrong_fog_cannot_pass(self):
            with tempfile.TemporaryDirectory() as folder:
                run = Path(folder)
                atomic_json(run / "black.captured.json", {"phase": "black", "dimension": "qihai:yuji_fodu", "fogFar": 64})
                with self.assertRaisesRegex(RuntimeError, "fog"):
                    verify_capture(run, "black", {})
                self.assertFalse((run / "black.pass.json").exists())

        def test_camera_drift_cannot_pass(self):
            with tempfile.TemporaryDirectory() as folder:
                run = Path(folder)
                atomic_json(run / "grey.captured.json", {"phase": "grey", "dimension": "qihai:yuji_fodu", "fogFar": 64,
                            "x": 96.5, "y": 128, "z": 136.5, "yaw": 180, "pitch": 8})
                with self.assertRaisesRegex(RuntimeError, "camera drifted"):
                    verify_capture(run, "grey", {})
                self.assertFalse((run / "grey.pass.json").exists())

        def test_old_wall_camera_cannot_pass(self):
            with tempfile.TemporaryDirectory() as folder:
                run = Path(folder)
                for phase, fog in (("grey", 64), ("black", 32)):
                    atomic_json(run / (phase + ".captured.json"), {"phase": phase, "dimension": "qihai:yuji_fodu", "fogFar": fog,
                                "x": 96.5, "y": 132, "z": 120.5, "yaw": 180, "pitch": 8})
                    with self.assertRaisesRegex(RuntimeError, "camera drifted"):
                        verify_capture(run, phase, {})
                    self.assertFalse((run / (phase + ".pass.json")).exists())

    return 0 if unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Checks)).wasSuccessful() else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--port", type=int, default=25591)
    parser.add_argument("--timeout", type=int, default=900, help="Maximum seconds for the build and for the full game run")
    parser.add_argument("--skip-build", action="store_true", help="Use an already generated city-test classpath and launch configuration")
    parser.add_argument("--self-test", action="store_true", help="Test PNG parsing and launcher helpers without Minecraft")
    options = parser.parse_args()
    if options.self_test:
        return self_test()
    def terminate(signum, _frame):
        raise KeyboardInterrupt("Received signal " + str(signum))

    previous = signal.signal(signal.SIGTERM, terminate)
    try:
        return run_smoke(options)
    except Exception as failure:
        print("FAIL: " + str(failure), file=sys.stderr)
        return 1
    finally:
        signal.signal(signal.SIGTERM, previous)


if __name__ == "__main__":
    raise SystemExit(main())
