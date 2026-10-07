"""Record a ~2min real-client showcase video for the wind-thunder wings.

Mirrors wings-e2e.py (real dedicated server + two real Forge clients on Xvfb)
but launches the QA driver in -Dwings.qa.mode=showcase: a choreographed flight
performance instead of assertions. ffmpeg records both displays; the two
recordings are concatenated into build/wings-showcase/wings-showcase.mp4.

Usage: python3 tools/wings-showcase.py
"""
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "build/wings-showcase"
if RESULTS.exists():
    for stale in RESULTS.iterdir():
        if stale.is_dir():
            shutil.rmtree(stale)
        else:
            stale.unlink()
RESULTS.mkdir(parents=True, exist_ok=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/gravity-test-classpath.txt").read_text().strip()
processes = []
logs = []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(name, role, display=None):
    config = CONFIGS[name]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if role == "server":
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            "online-mode=false\nserver-ip=127.0.0.1\nserver-port=25566\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=4\n"
            "simulation-distance=4\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "allow-flight=true\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n"
        )
    else:
        (directory / "options.txt").write_text(
            "renderDistance:4\nsimulationDistance:5\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:true\nnarrator:0\n"
            "gamma:1.0\nbobView:false\n"
            "tutorialStep:none\n"
        )
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    if display:
        env["DISPLAY"] = display
    args = shlex.split(expand(config["args"]))
    if role != "server":
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "WingsWearer" if role == "wearer" else "WingsObserver",
                 "--width", "1280", "--height", "720",
                 "--quickPlayMultiplayer", "127.0.0.1:25566"]
    wings_role = {"server": "server", "wearer": "wingswearer", "observer": "wingsobserver"}[role]
    command = ["java", "-Xmx1G",
               f"-Dwings.qa.results={RESULTS}", f"-Dwings.qa.role={wings_role}",
               "-Dwings.qa.mode=showcase",
               f"-Dgravity.qa.results={RESULTS}", f"-Dgravity.qa.role={role}"]
    command += shlex.split(expand(config["vmArgs"]))
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    log = (RESULTS / f"{role}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT)
    processes.append(process)
    return process


def record(display, name):
    env = os.environ.copy()
    env["DISPLAY"] = display
    out = RESULTS / f"showcase-{name}.mp4"
    cmd = ["ffmpeg", "-y", "-f", "x11grab", "-draw_mouse", "0",
           "-video_size", "1280x720", "-framerate", "20", "-i", display,
           "-c:v", "libx264", "-preset", "veryfast", "-crf", "22",
           "-pix_fmt", "yuv420p", str(out)]
    proc = subprocess.Popen(cmd, env=env,
                            stdin=subprocess.PIPE,
                            stdout=(RESULTS / f"ffmpeg-{name}.log").open("w"),
                            stderr=subprocess.STDOUT)
    processes.append(proc)
    return proc


recorders = []
try:
    for display in (":93", ":94"):
        processes.append(subprocess.Popen(["Xvfb", display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
    server = launch("runServer", "server")
    deadline = time.monotonic() + 180
    while True:
        if server.poll() is not None or time.monotonic() >= deadline:
            raise RuntimeError("Dedicated test server did not start")
        try:
            with socket.create_connection(("127.0.0.1", 25566), timeout=1):
                break
        except OSError:
            time.sleep(1)
    recorders.append(record(":93", "wearer"))
    recorders.append(record(":94", "observer"))
    wearer = launch("runClient", "wearer", ":93")
    observer = launch("runClient", "observer", ":94")
    deadline = time.monotonic() + 480
    required = ("wings-wearer", "wings-observer")
    while not all((RESULTS / f"{role}.pass").exists() for role in required):
        failures = list(RESULTS.glob("wings-*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(process.poll() is not None for process in (server, wearer, observer)):
            raise RuntimeError("A Minecraft process exited before the showcase completed")
        if time.monotonic() >= deadline:
            raise TimeoutError("Wings showcase recording timed out")
        time.sleep(1)
    print("WINGS_SHOWCASE_DONE", flush=True)
finally:
    # finalize mp4s before tearing down clients
    for rec in recorders:
        if rec.poll() is None:
            rec.send_signal(signal.SIGINT)
    for rec in recorders:
        try:
            rec.wait(timeout=15)
        except subprocess.TimeoutExpired:
            rec.kill()
            rec.wait()
    for process in reversed(processes):
        if process.poll() is None:
            process.terminate()
    for process in reversed(processes):
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    for log in logs:
        log.close()

# concat wearer view + observer view into one file
parts = [RESULTS / "showcase-wearer.mp4", RESULTS / "showcase-observer.mp4"]
if all(p.exists() and p.stat().st_size > 10000 for p in parts):
    listfile = RESULTS / "concat.txt"
    listfile.write_text("".join(f"file '{p}'\n" for p in parts))
    subprocess.run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(listfile),
                    "-c", "copy", str(RESULTS / "wings-showcase.mp4")], check=True)
    print("WINGS_SHOWCASE_VIDEO", RESULTS / "wings-showcase.mp4", flush=True)
