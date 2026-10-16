"""Opt-in real Forge client and dedicated-server rendering/audio smoke test."""
import json
import os
import re
from pathlib import Path
import shlex
import shutil
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
BOUNDARIES = os.environ.get("YUJI_QA_BOUNDARIES") == "1"
ISOLATION = os.environ.get("YUJI_QA_ISOLATION") == "1"
GREEN = os.environ.get("FLARE_QA_GREEN") == "1"
VISUALS = os.environ.get("FLARE_QA_VISUALS") == "1"
RECORD_AUDIO = os.environ.get("YUJI_QA_RECORD_AUDIO") == "1"
PROFILES = os.environ.get("YUJI_QA_PROFILES", "")
CLIENT_FPS = int(os.environ.get("YUJI_QA_CLIENT_FPS", "30"))
FRAME_DELAY_MS = int(os.environ.get("YUJI_QA_FRAME_DELAY_MS", "0"))
if not 10 <= CLIENT_FPS <= 260 or not 0 <= FRAME_DELAY_MS <= 2000:
    raise ValueError("Client FPS must be 10..260; gallery frame delay must be 0..2000 ms")
if PROFILES and (BOUNDARIES or ISOLATION):
    raise ValueError("Focused profiles cannot replace multiplayer acceptance")
if GREEN and (PROFILES or BOUNDARIES or ISOLATION):
    raise ValueError("Green flare evidence runs separately from monster acceptance")
if VISUALS and (GREEN or PROFILES or BOUNDARIES or ISOLATION):
    raise ValueError("Three-color visual evidence runs separately from other acceptance modes")
if VISUALS and shutil.which("ffmpeg") is None:
    raise RuntimeError("Three-color visual evidence requires ffmpeg for actual framebuffer video")
RESULTS = ROOT / "build" / (("flare-visuals-e2e-" if VISUALS else "flare-green-e2e-" if GREEN else "yuji-isolation-" if ISOLATION else "yuji-boundaries-" if BOUNDARIES else "yuji-e2e-") + str(time.time_ns()))
RESULTS.mkdir(parents=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/yuji-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("YUJI_QA_PORT", "25589"))
processes, logs = [], []
acceptance_complete = False


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role, username="YujiTester"):
    server = role == "server"
    config = CONFIGS["runServer" if server else "runClient"]
    directory = RESULTS / role
    directory.mkdir()
    if server:
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            f"enforce-secure-profile=false\nspawn-protection=0\nview-distance={6 if VISUALS else 3}\n"
            "simulation-distance=3\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=creative\n")
    else:
        (directory / "config").mkdir()
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            f"lang:zh_cn\nrenderDistance:{6 if VISUALS else 3}\nsimulationDistance:3\nguiScale:2\n"
            f"maxFps:{CLIENT_FPS}\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n"
            "gamma:1.0\nfov:0.0\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env.update(LIBGL_ALWAYS_SOFTWARE="1", ALSOFT_DRIVERS="null")
    if not server:
        env["DISPLAY"] = display
        if RECORD_AUDIO:
            audio_config = directory / "alsoft.conf"
            audio_config.write_text(f"[general]\nchannels=stereo\nsample-type=int16\n[wave]\nfile={RESULTS / (role + '-mix.wav')}\n")
            env.update(ALSOFT_DRIVERS="wave", ALSOFT_CONF=str(audio_config))
    args = shlex.split(expand(config["args"]))
    if not server:
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", username, "--width", "960", "--height", "540",
                 "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1024M" if server else "-Xmx2048M", f"-Dyuji.qa.results={RESULTS}"]
    if BOUNDARIES:
        command += ["-Dyuji.qa.boundaries=true"]
    if ISOLATION:
        command += ["-Dyuji.qa.isolation=true"]
    if GREEN:
        command += ["-Dflare.qa.green=true"]
    if VISUALS:
        command += ["-Dflare.qa.visuals=true"]
    command += ["-Dyuji.qa.audio=" + ("OpenAL-wave" if RECORD_AUDIO else "OpenAL-null")]
    if PROFILES:
        command += ["-Dyuji.qa.profiles=" + PROFILES]
    if os.environ.get("YUJI_QA_STALL_BITE") == "1":
        command += ["-Dyuji.qa.stallBite=true"]
    command += [f"-Dyuji.qa.frameDelayMs={FRAME_DELAY_MS}"]
    command += shlex.split(expand(config["vmArgs"]))
    if not server:
        natives = directory / "lwjgl-natives"
        natives.mkdir()
        command += [f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}"]
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    log = (RESULTS / f"{role}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdin=subprocess.PIPE,
                               stdout=log, stderr=subprocess.STDOUT, text=True)
    processes.append(process)
    return process


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def wait_for(predicate, running, message, seconds):
    deadline = time.monotonic() + seconds
    while True:
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if predicate():
            return
        if any(p.poll() is not None for p in running):
            raise RuntimeError("Minecraft exited before " + message)
        if time.monotonic() >= deadline:
            raise TimeoutError(message)
        time.sleep(1)


def ready():
    try:
        with socket.create_connection(("127.0.0.1", PORT), timeout=1):
            return True
    except OSError:
        return False


def visual_evidence():
    from city_e2e import inspect_png
    phases = ("red-flight", "red-activation", "red-radius", "yellow-flight", "yellow-activation",
              "yellow-night-light", "green-flight", "green-activation", "green-descent", "green-landed")
    captures = {phase: inspect_png(RESULTS / ("visual-" + phase + ".png")) for phase in phases}
    if len({capture["sha256"] for capture in captures.values()}) != len(phases):
        raise AssertionError("Three-color evidence requires ten distinct native rendered frames")
    for color in ("red", "yellow", "green"):
        marker = (RESULTS / ("client-visual-" + color + "-flight.pass")).read_text()
        light = re.search(r"blockLight=(\d+)", marker)
        if light is None or int(light.group(1)) < 12:
            raise AssertionError("Missing real client flight block-light evidence for " + color)
    ring_marker = (RESULTS / "client-visual-red-radius.pass").read_text()
    ring = {key: int(value) for key, value in re.findall(r"(ringPixels|ringSectors|ringOutside|ringStableFrames|ringAngles|ringMaxGap|radius)=(\d+)", ring_marker)}
    if (ring.get("radius") != 24 or ring.get("ringSectors") != 32 or ring.get("ringPixels", 0) < 96
            or ring.get("ringStableFrames", 0) < 5 or ring.get("ringOutside", 10**9) > ring.get("ringPixels", 0) // 4
            or ring.get("ringAngles", 0) < 354 or ring.get("ringMaxGap", 360) > 2):
        raise AssertionError("Missing complete, correctly sized red ring in actual rendered pixels: " + ring_marker)
    rows = [line.split("\t") for line in (RESULTS / "visual-frames.tsv").read_text().splitlines()]
    if len(rows) < 20 or any(len(row) != 3 for row in rows):
        raise AssertionError("Missing actual framebuffer movie samples")
    timestamps = [int(row[1]) for row in rows]
    if any(second <= first for first, second in zip(timestamps, timestamps[1:])):
        raise AssertionError("Framebuffer timestamps must advance monotonically")
    movie_phases = {row[2] for row in rows}
    if not set(phases).issubset(movie_phases):
        raise AssertionError("Framebuffer movie does not cover every visual phase")
    # Encode only captured game frames, preserving measured intervals. No generated stills or overlays.
    concat = ["ffconcat version 1.0"]
    for index, (name, timestamp, phase) in enumerate(rows):
        if not re.fullmatch(r"frame-\d{6}\.png", name) or not (RESULTS / "visual-frames" / name).is_file():
            raise AssertionError("Invalid or missing actual framebuffer capture: " + name)
        duration = (timestamps[index + 1] - int(timestamp)) / 1_000_000_000 if index + 1 < len(rows) else .1
        concat += [f"file 'visual-frames/{name}'", f"duration {duration:.9f}"]
    concat.append(f"file 'visual-frames/{rows[-1][0]}'")
    source = RESULTS / "visual-frames.ffconcat"
    source.write_text("\n".join(concat) + "\n")
    encoded = subprocess.run(["ffmpeg", "-nostdin", "-y", "-safe", "1", "-f", "concat", "-i", str(source),
                              "-vf", "fps=10", "-c:v", "libx264", "-preset", "fast", "-crf", "20",
                              "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(RESULTS / "flare-three-colors.mp4")],
                             check=True, capture_output=True, text=True)
    (RESULTS / "visual-video.log").write_text(encoded.stderr)
    movie = RESULTS / "flare-three-colors.mp4"
    if movie.stat().st_size < 10000:
        raise AssertionError("Actual framebuffer movie is unexpectedly empty")
    (RESULTS / "visual-screenshots.json").write_text(json.dumps({"captures": captures,
        "red_ring_pixels": ring, "native_movie_frames": len(rows), "duration_seconds": (timestamps[-1] - timestamps[0]) / 1_000_000_000,
        "movie": movie.name, "source": "native Minecraft framebuffer, measured capture intervals"}, indent=2) + "\n")
    print("FLARE_VISUAL_CLIENT_PASSED actualShots=3 screenshots=10 flightBlockLight>=12 nativeFrames=" + str(len(rows)), flush=True)


try:
    print(f"YUJI_QA_RESULTS={RESULTS}", flush=True)
    print(f"YUJI_QA_CONFIG profiles={PROFILES or 'all'}, fps={CLIENT_FPS}, frameDelayMs={FRAME_DELAY_MS}, "
          f"stallBite={os.environ.get('YUJI_QA_STALL_BITE') == '1'}, recordAudio={RECORD_AUDIO}", flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    display = ":" + xvfb.stdout.readline().strip()
    server = launch("server")
    wait_for(ready, [server], "dedicated server startup", 600)
    client = launch("client", "FlareVisualQA" if VISUALS else "GreenFlareQA" if GREEN else "YujiTester")
    if VISUALS:
        wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in ["server-visuals", "client-visuals"]),
                 [server, client], "three real flare shots, bursts, red radius and measured night block-light", 900)
        visual_evidence()
    elif GREEN:
        wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in ["server-green", "client-green"]),
                 [server, client], "green shot, natural descent and real client container opening", 900)
        from city_e2e import inspect_png
        captures = {phase: inspect_png(RESULTS / ("green-" + phase + ".png"))
                    for phase in ("flight", "descent", "landed", "open")}
        if len({capture["sha256"] for capture in captures.values()}) != 4:
            raise AssertionError("Green evidence must show four distinct rendered frames")
        (RESULTS / "green-screenshots.json").write_text(json.dumps(captures, indent=2) + "\n")
        print("FLARE_GREEN_CLIENT_PASSED shot=1 spawned=1 landed=1 opened=1 screenshots=4", flush=True)
    elif ISOLATION:
        # Avoid simultaneous resource/renderer bootstrap starving the other client's login on small hosts.
        wait_for(lambda: (RESULTS / "client-isolation-ready-YujiTester.pass").exists(),
                 [server, client], "first real client fully joined", 600)
        witness = launch("witness", "YujiWitness")
        wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in
                            ["server-isolation", "client-isolation-YujiTester", "client-isolation-YujiWitness"]),
                 [server, client, witness], "two-client audio/camera isolation and real terrain/Panda scenarios", 1800)
        stop(witness)
    elif BOUNDARIES:
        witness = launch("witness", "YujiWitness")
        wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in
                            ["server-boundaries", "client-boundaries-YujiTester", "client-boundaries-YujiWitness"]),
                 [server, client, witness], "two real players, grudge, buffed slashes and body-contact boundaries", 900)
        stop(witness)
    else:
        wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in ["server-gallery", "client-gallery"]),
                 [server, client], "eleven rendered profiles, real-player AI, collisions, audio and projectile", 900)
    stop(client)
    server.stdin.write("stop\n")
    server.stdin.flush()
    if server.wait(timeout=180) != 0:
        raise RuntimeError("Dedicated server did not shut down cleanly")
    if list(RESULTS.glob("*.failed")):
        raise AssertionError("late failure markers after shutdown")
    acceptance_complete = True
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    if RECORD_AUDIO:
        audio_error = None
        for wav in sorted(RESULTS.glob("*-mix.wav")):
            try:
                ogg = wav.with_suffix(".ogg")
                converted = subprocess.run(["ffmpeg", "-nostdin", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", str(ogg)],
                                           check=True, capture_output=True, text=True)
                probe = subprocess.run(["ffmpeg", "-nostdin", "-i", str(ogg), "-af", "volumedetect", "-f", "null", "-"],
                                       check=True, capture_output=True, text=True)
                (RESULTS / (ogg.stem + "-audio.log")).write_text(converted.stderr + probe.stderr)
                peak = re.search(r"max_volume: (-?[\d.]+) dB", probe.stderr)
                if not peak or float(peak.group(1)) < -80:
                    raise AssertionError("recorded real-client mix is silent: " + str(ogg))
            except Exception as error:
                audio_error = error
                (RESULTS / (wav.stem + "-audio.failed")).write_text(str(error) + "\n" + getattr(error, "stderr", ""))
        expected = 2 if ISOLATION or BOUNDARIES else 1
        if acceptance_complete and audio_error is not None:
            raise audio_error
        if acceptance_complete and len(list(RESULTS.glob("*-mix.ogg"))) != expected:
            raise AssertionError("missing a real client audio recording")
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name} ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-20:]))
    for path in sorted(RESULTS.glob("*.pass")):
        print(f"{path.name}: {path.read_text().strip()}")

print("FLARE_THREE_COLOR_REAL_CLIENT_ACCEPTANCE_PASSED" if VISUALS else
      "FLARE_GREEN_REAL_CLIENT_ACCEPTANCE_PASSED" if GREEN else
      "YUJI_TWO_REAL_CLIENT_ISOLATION_AND_TERRAIN_PASSED" if ISOLATION else
      "YUJI_TWO_REAL_CLIENT_BOUNDARIES_PASSED" if BOUNDARIES else
      "YUJI_FOCUSED_REAL_CLIENT_PASSED profiles=" + PROFILES if PROFILES else
      "YUJI_REAL_CLIENT_AI_COLLISION_AND_AUDIO_ACCEPTANCE_PASSED", flush=True)
