"""Opt-in real Forge client and dedicated-server rendering/audio smoke test."""
import json
import os
import re
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
BOUNDARIES = os.environ.get("YUJI_QA_BOUNDARIES") == "1"
ISOLATION = os.environ.get("YUJI_QA_ISOLATION") == "1"
CAMERA_TURN = os.environ.get("YUJI_QA_CAMERA_TURN", "true")
QIYU_SUMMONING = os.environ.get("YUJI_QA_QIYU_SUMMONING", "false")
if QIYU_SUMMONING not in ("true", "false"):
    raise ValueError("YUJI_QA_QIYU_SUMMONING must be true/false")
if CAMERA_TURN not in ("true", "false") or (CAMERA_TURN == "false" and not ISOLATION):
    raise ValueError("YUJI_QA_CAMERA_TURN must be true/false; false requires isolation acceptance")
RECORD_AUDIO = os.environ.get("YUJI_QA_RECORD_AUDIO") == "1"
PROFILES = os.environ.get("YUJI_QA_PROFILES", "")
CLIENT_FPS = int(os.environ.get("YUJI_QA_CLIENT_FPS", "30"))
FRAME_DELAY_MS = int(os.environ.get("YUJI_QA_FRAME_DELAY_MS", "0"))
if not 10 <= CLIENT_FPS <= 260 or not 0 <= FRAME_DELAY_MS <= 2000:
    raise ValueError("Client FPS must be 10..260; gallery frame delay must be 0..2000 ms")
if PROFILES and (BOUNDARIES or ISOLATION):
    raise ValueError("Focused profiles cannot replace multiplayer acceptance")
RESULTS = ROOT / "build" / (("yuji-isolation-" if ISOLATION else "yuji-boundaries-" if BOUNDARIES else "yuji-e2e-") + str(time.time_ns()))
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
        (directory / "defaultconfigs").mkdir()
        (directory / "defaultconfigs/qihai-yuji-server.toml").write_text(
            f"[enthralled]\ncameraTurn = {CAMERA_TURN}\n[qiyu]\nsummoning = {QIYU_SUMMONING}\n")
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=3\n"
            "simulation-distance=3\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=creative\n")
    else:
        (directory / "config").mkdir()
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:zh_cn\nrenderDistance:3\nsimulationDistance:3\nguiScale:2\n"
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
    command += ["-Dyuji.qa.qiyuSummoning=" + QIYU_SUMMONING]
    if BOUNDARIES:
        command += ["-Dyuji.qa.boundaries=true"]
    if ISOLATION:
        command += ["-Dyuji.qa.isolation=true"]
        command += ["-Dyuji.qa.cameraTurn=" + CAMERA_TURN]
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


try:
    print(f"YUJI_QA_RESULTS={RESULTS}", flush=True)
    print(f"YUJI_QA_CONFIG profiles={PROFILES or 'all'}, fps={CLIENT_FPS}, frameDelayMs={FRAME_DELAY_MS}, "
          f"stallBite={os.environ.get('YUJI_QA_STALL_BITE') == '1'}, recordAudio={RECORD_AUDIO}, cameraTurn={CAMERA_TURN}, "
          f"qiyuSummoning={QIYU_SUMMONING}", flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    display = ":" + xvfb.stdout.readline().strip()
    server = launch("server")
    wait_for(ready, [server], "dedicated server startup", 600)
    client = launch("client")
    if ISOLATION:
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

print("YUJI_TWO_REAL_CLIENT_ISOLATION_AND_TERRAIN_PASSED" if ISOLATION else
      "YUJI_TWO_REAL_CLIENT_BOUNDARIES_PASSED" if BOUNDARIES else
      "YUJI_FOCUSED_REAL_CLIENT_PASSED profiles=" + PROFILES if PROFILES else
      "YUJI_REAL_CLIENT_AI_COLLISION_AND_AUDIO_ACCEPTANCE_PASSED", flush=True)
