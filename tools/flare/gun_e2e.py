"""Real dedicated server + software-rendered client, screenshots, pose trace and video.

Prepare with: ./gradlew -PflareGunE2E writeFlareGunTestClasspath
The probe is an opt-in source set and is never included in the player jar.
"""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "build" / ("flare-gun-e2e-" + str(time.time_ns()))
RESULTS.mkdir(parents=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/flare-gun-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("FLARE_GUN_QA_PORT", "25594"))
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role, display):
    server = role == "server"
    config = CONFIGS["runServer" if server else "runClient"]
    directory = RESULTS / role
    directory.mkdir()
    if server:
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=3\n"
            "simulation-distance=3\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n")
    else:
        (directory / "config").mkdir()
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:en_us\nrenderDistance:3\nsimulationDistance:5\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n"
            "gamma:1.0\nfov:0.0\nmusic:0.0\n"
            # Cheaper chunk re-meshing on software GL: flight-light block updates from the merged T5 trail
            # otherwise stall frames across the short recoil window. Assertions are unchanged.
            "ao:false\ngraphicsMode:0\nentityShadows:false\nbiomeBlendRadius:0\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env.update(LIBGL_ALWAYS_SOFTWARE="1", ALSOFT_DRIVERS="null", DISPLAY=display)
    if not server:
        audio_config = directory / "alsoft.conf"
        audio_config.write_text(f"[general]\nchannels=stereo\nsample-type=int16\n[wave]\nfile={RESULTS / (role + '-mix.wav')}\n")
        env.update(ALSOFT_DRIVERS="wave", ALSOFT_CONF=str(audio_config))
    args = shlex.split(expand(config["args"]))
    if not server:
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "FlareObserver" if role == "observer" else "FlareTester", "--width", "960", "--height", "540",
                 "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1024M" if server else "-Xmx1536M", f"-Dflare.gun.qa.results={RESULTS}",
               f"-Dflare.gun.qa.role={role}", f"-Dflare.gun.qa.port={PORT}"]
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


def wait_for(predicate, running, message, seconds=600):
    deadline = time.monotonic() + seconds
    while not predicate():
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
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
    print(f"FLARE_GUN_QA_RESULTS={RESULTS}", flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    displays = []
    for _ in range(2):
        xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "960x540x24", "-nolisten", "tcp"],
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        processes.append(xvfb)
        displays.append(":" + xvfb.stdout.readline().strip())
    display = displays[0]
    server = launch("server", display)
    wait_for(ready, [server], "dedicated server startup")
    client = launch("client", display)
    wait_for(lambda: (RESULTS / "client.ready").exists(), [server, client], "real client joined", 180)
    observer = launch("observer", displays[1])
    wait_for(lambda: (RESULTS / "observer.ready").exists(), [server, client, observer], "observer joined", 180)
    videos = []
    for name, screen in zip(("first-third-person", "observer"), displays):
        video_log = (RESULTS / (name + "-video.log")).open("w")
        logs.append(video_log)
        video = subprocess.Popen(["ffmpeg", "-y", "-f", "x11grab", "-framerate", "20", "-video_size", "960x540",
                                  "-i", screen, "-c:v", "libx264", "-threads", "1", "-preset", "ultrafast", "-crf", "24",
                                  "-pix_fmt", "yuv420p", str(RESULTS / (name + ".mp4"))],
                                 stdin=subprocess.PIPE, stdout=video_log, stderr=subprocess.STDOUT, text=True)
        processes.append(video)
        videos.append(video)
    (RESULTS / "recording.ready").touch()
    wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in ("server", "client", "observer")),
             [server, client, observer, *videos], "dual-client shots, rejected attempts and rendered recoil/loading", 300)
    for video in videos:
        video.stdin.write("q\n")
        video.stdin.flush()
        if video.wait(timeout=30) != 0:
            raise AssertionError("Video encoder failed")
    stop(observer)
    stop(client)
    server.stdin.write("stop\n")
    server.stdin.flush()
    if server.wait(timeout=180) != 0:
        raise AssertionError("Dedicated server shutdown failed")
    print("FLARE_GUN_QA_PASS dedicated server, dual-client rendered motion, handed muzzle packets, rejected shots, both vanilla sounds, screenshots/video", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
