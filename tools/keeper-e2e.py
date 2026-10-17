"""Single-client Forge keeper camera E2E: flash, sepia filter, polaroid HUD."""
import json
import csv
import hashlib
import os
from pathlib import Path
import shlex
import shutil
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RESULTS = Path(os.environ.get("KEEPER_QA_RESULTS", str(ROOT / "qa-evidence" / time.strftime("%Y%m%d-%H%M%S-200ms")))).resolve()
if any(RESULTS.glob("*.pass")) or (RESULTS / "client.log").exists():
    raise RuntimeError("Evidence directory already used; choose a fresh KEEPER_QA_RESULTS")
RESULTS.mkdir(parents=True, exist_ok=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/keeper-test-classpath.txt").read_text().strip()
RUNTIME_JAR = Path(os.environ.get("KEEPER_QA_RUNTIME_JAR", str(ROOT / "build/keeper-runtime/keeper-runtime.jar"))).resolve()
DELIVERY_JAR = Path(os.environ.get("KEEPER_QA_DELIVERY_JAR", str(ROOT / "build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar"))).resolve()
for role, artifact in (("RUNTIME", RUNTIME_JAR), ("DELIVERY", DELIVERY_JAR)):
    assert artifact.is_file() and artifact.is_relative_to(ROOT), "Artifact must exist within this workspace"
    expected = os.environ.get("KEEPER_QA_" + role + "_SHA256")
    if "KEEPER_QA_" + role + "_JAR" in os.environ:
        assert expected, "An explicitly selected artifact requires its SHA256"
    if expected:
        assert hashlib.sha256(artifact.read_bytes()).hexdigest() == expected, role + " artifact fingerprint mismatch"
CLASSPATH = ":".join(p for p in CLASSPATH.split(":") if p not in
                     [str(ROOT / "build/classes/java/main"), str(ROOT / "build/resources/main"),
                      str(ROOT / "build/keeper-runtime/keeper-runtime.jar"), str(RUNTIME_JAR)]) + ":" + str(RUNTIME_JAR)
PORT = int(os.environ.get("KEEPER_QA_PORT", "25578"))
processes, logs = [], []
displays = []


def expand(value):
    return value.replace("$" + "{workspaceFolder}", str(ROOT))


def launch(role):
    config = CONFIGS["runServer" if role == "server" else "runClient"]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if role == "server":
        world = ROOT / "qa-evidence/20261016-e2e-x11/server/world"
        if world.is_dir() and not (directory / "world").exists():
            shutil.copytree(world, directory / "world", ignore=shutil.ignore_patterns("session.lock"))
            (RESULTS / "world-source.txt").write_text(str(world) + "\nCopied; original left unchanged.\n")
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=2\n"
            "network-compression-threshold=-1\nsimulation-distance=2\n"
            "level-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n")
    else:
        (directory / "config").mkdir(exist_ok=True)
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "renderDistance:2\nsimulationDistance:5\nguiScale:2\nmaxFps:60\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n"
            "skipMultiplayerWarning:true\njoinedFirstServer:true\n")
        if os.environ.get("KEEPER_QA_FAST_GRAPHICS") == "1":
            # Standard game options reduce software-rendering load, without changing test gates.
            with (directory / "options.txt").open("a") as options:
                options.write("graphicsMode:0\nao:false\nbiomeBlendRadius:0\n"
                              "mipmapLevels:0\nparticles:2\nentityShadows:false\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env["MOD_CLASSES"] = ":".join(entry for entry in env["MOD_CLASSES"].split(":")
                                 if not entry.startswith("sound_isolating_eraser%%")) + ":sound_isolating_eraser%%" + str(RUNTIME_JAR)
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    env["LP_NUM_THREADS"] = os.environ.get("KEEPER_QA_RENDER_THREADS", "2")
    if role != "server":
        env["DISPLAY"] = displays[0]
    args = shlex.split(expand(config["args"]))
    if role != "server":
        args += ["--mixin.config", "keeper-qa.mixins.json"]
        for option in ("--username", "--uuid", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "KeeperUser", "--uuid", "11111111-1111-1111-1111-111111111111",
                 "--width", os.environ.get("KEEPER_QA_WIDTH", "854"),
                 "--height", os.environ.get("KEEPER_QA_HEIGHT", "480"),
                 "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-XX:ActiveProcessorCount=2", "-Xmx1536M" if role != "server" else "-Xmx768M",
               f"-Dkeeper.qa.results={RESULTS}", f"-Dkeeper.qa.role={role}"]
    command.append(f"-Xlog:gc*,safepoint:file={RESULTS / (role + '-gc.log')}:time,uptime,level,tags")
    if role != "server" and os.environ.get("KEEPER_QA_PROFILE") == "1":
        command.append("-XX:StartFlightRecording=name=keeper_qa,settings=profile,disk=true,"
                       + f"maxsize=64m,filename={RESULTS / 'client.jfr'},dumponexit=true")
    vm = expand(config["vmArgs"]).replace("forge.logging.console.level=debug", "forge.logging.console.level=info")
    command += shlex.split(vm)
    # legacyClassPath also must not silently reintroduce loose main classes.
    legacy = next(a for a in command if a.startswith("-DlegacyClassPath.file="))
    legacy_path = Path(legacy.split("=", 1)[1])
    original = legacy_path.read_text()
    for entry in (str(ROOT / "build/classes/java/main"), str(ROOT / "build/resources/main")):
        original = original.replace(entry, str(RUNTIME_JAR))
    local_legacy = RESULTS / (role + "-legacy-classpath.txt")
    local_legacy.write_text(original)
    command[command.index(legacy)] = "-DlegacyClassPath.file=" + str(local_legacy)
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    (RESULTS / (role + "-launch.json")).write_text(json.dumps({"command": command, "MOD_CLASSES": env["MOD_CLASSES"]}, indent=2))
    log = (RESULTS / f"{role}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdin=subprocess.PIPE,
                               stdout=log, stderr=subprocess.STDOUT, text=True)
    processes.append(process)
    return process


def wait_ready(server):
    startup_seconds = int(os.environ.get("KEEPER_QA_STARTUP_SECONDS", "900"))
    deadline = time.monotonic() + startup_seconds
    log = RESULTS / "server.log"
    while time.monotonic() < deadline and server.poll() is None:
        text = log.read_text(errors="replace") if log.exists() else ""
        if "For help, type" in text:
            return
        time.sleep(1)
    raise RuntimeError(f"Keeper dedicated server did not become ready within {startup_seconds}s")


def wait_evidence(required, running):
    deadline = time.monotonic() + int(os.environ.get("KEEPER_QA_TIMEOUT_SECONDS", "1200"))
    while not all((RESULTS / (name + ".pass")).exists() for name in required):
        # Flash failure is terminal for acceptance, not for independent photo diagnostics.
        failures = [p for p in RESULTS.glob("*.failed")
                    if p.name not in ("flash-render.failed", "sampler.failed")]
        messages = [path.name + ": " + path.read_text() for path in failures if path.stat().st_size > 0]
        if messages:
            raise AssertionError("; ".join(messages))
        if any(p.poll() is not None for p in running):
            raise RuntimeError("Minecraft exited before keeper evidence completed")
        if time.monotonic() >= deadline:
            raise TimeoutError("Keeper real-client validation timed out")
        time.sleep(0.2)


def verify_flash_timing():
    rows = list(csv.DictReader((RESULTS / "flash-x11-times.csv").open()))
    normal = [r for r in rows if 350 <= float(r["start_ms"]) <= 500]
    assert len(normal) >= 3, "Need a stable post-flash reference"
    reference = [sum(float(r[c]) for r in normal) / len(normal) for c in ("r", "g", "b")]
    denominator = sum((255 - c) ** 2 for c in reference)
    assert denominator > 1000, "Reference scene too white to measure a flash"
    analyzed = []
    for row in rows:
        alpha = sum((float(row[c]) - reference[i]) * (255 - reference[i])
                    for i, c in enumerate(("r", "g", "b"))) / denominator
        analyzed.append({**row, "estimated_alpha": alpha})
    with (RESULTS / "flash-pixel-timing.csv").open("w") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(analyzed[0]))
        writer.writeheader()
        writer.writerows(analyzed)
    visible = [r for r in analyzed if 0 <= float(r["start_ms"]) <= 200 and r["estimated_alpha"] >= .08]
    clear = [r for r in analyzed if 200 <= float(r["start_ms"]) <= 250 and abs(r["estimated_alpha"]) < .05]
    assert visible and clear, "X11 pixels must show shutter and clear by 250ms"
    assert max(r["estimated_alpha"] for r in visible) >= .25, "No strong interior shutter evidence"
    late = [r for r in analyzed if float(r["start_ms"]) >= 300]
    assert all(abs(r["estimated_alpha"]) < .08 for r in late), "Persistent white overlay after deadline"
    gaps = [float(b["start_ms"]) - float(a["start_ms"]) for a, b in zip(analyzed, analyzed[1:])]
    assert max(gaps) <= 50, "Capture cadence is too sparse to resolve 200ms"
    (RESULTS / "flash.pass").write_text(json.dumps({"reference_rgb": reference,
        "first_visible_ms": float(visible[0]["start_ms"]), "last_visible_ms": float(visible[-1]["end_ms"]),
        "first_clear_ms": float(clear[0]["start_ms"]), "max_capture_gap_ms": max(gaps),
        "roi": "320x180+480+270; no photo-frame border"}, indent=2))


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def x11_grab(name):
    if not displays:
        return
    subprocess.run(["import", "-display", displays[0], "-window", "root", str(RESULTS / name)],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


try:
    # Reuse the already generated superflat world to skip spawn prep.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    displays.append(":" + xvfb.stdout.readline().strip())
    server = launch("server")
    wait_ready(server)
    sampler_classes = ROOT / "build/keeper-sampler"
    sampler_classes.mkdir(parents=True, exist_ok=True)
    subprocess.run(["javac", "-d", str(sampler_classes), str(ROOT / "tools/KeeperX11Sampler.java")], check=True)
    sampler_log = (RESULTS / "sampler.log").open("w")
    logs.append(sampler_log)
    # Keep the real X11 capture ring away from small-heap collection pressure.
    sampler = subprocess.Popen(["java", "-Xms512m", "-Xmx512m", "-XX:+UseG1GC",
                                "-XX:MaxGCPauseMillis=10", "-XX:ActiveProcessorCount=1",
                                f"-Xlog:gc*,safepoint:file={RESULTS / 'sampler-gc.log'}:time,uptime,level,tags",
                                "-Djava.awt.headless=false", "-cp", str(sampler_classes),
                                "KeeperX11Sampler", str(RESULTS)],
                               env={**os.environ, "DISPLAY": displays[0]},
                               stdout=sampler_log, stderr=subprocess.STDOUT)
    processes.append(sampler)
    client = launch("client")
    wait_evidence(["sepia", "hud", "countdown", "exit", "server"], [server, client])
    failures = list(RESULTS.glob("*.failed"))
    assert not failures, "; ".join(p.name + ": " + p.read_text() for p in failures)
    assert sampler.wait(timeout=20) == 0, "X11 sampler failed"
    assert (RESULTS / "flash-observed.txt").exists(), "Missing rendered-frame timing evidence"
    verify_flash_timing()
    x11_grab("xvfb-final.png")
    print("KEEPER_REAL_CLIENT_FLASH_SEPIA_HUD_E2E_PASSED", flush=True)
finally:
    try:
        x11_grab("xvfb-exit.png")
    except Exception:
        pass
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    passed = not list(RESULTS.glob("*.failed")) and all(
        (RESULTS / (n + ".pass")).exists() for n in ["flash", "sepia", "hud", "countdown", "exit", "server"])
    (RESULTS / "results.md").write_text(
        "# Keeper camera client E2E\n\n"
        + "commit=" + commit + "\n"
        + "jar=" + str(DELIVERY_JAR) + "\n"
        + "jar_sha256=" + hashlib.sha256(DELIVERY_JAR.read_bytes()).hexdigest() + "\n"
        + "runtime_jar=" + str(RUNTIME_JAR) + "\n"
        + "runtime_jar_sha256=" + hashlib.sha256(RUNTIME_JAR.read_bytes()).hexdigest() + "\n"
        + "display=" + (displays[0] if displays else "none") + "\n"
        + "port=" + str(PORT) + "\n"
        + "passed=" + str(passed) + "\n"
    )
    for path in sorted(RESULTS.glob("*.log")):
        if path.name == "harness.log":
            continue
        print("--- " + path.name + " ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-40:]))
