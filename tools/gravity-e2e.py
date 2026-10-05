"""Run real Forge clients and a loopback-only server from ForgeGradle launch metadata."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "build/gravity-e2e"
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
            "online-mode=false\nserver-ip=127.0.0.1\nserver-port=25565\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=4\n"
            "simulation-distance=4\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=creative\n"
        )
    else:
        (directory / "options.txt").write_text(
            "renderDistance:4\nsimulationDistance:4\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\n"
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
        args += ["--username", "GravityWearer" if role == "wearer" else "GravityObserver",
                 "--width", "960", "--height", "540", "--quickPlayMultiplayer", "127.0.0.1:25565"]
    command = ["java", "-Xmx1G", "-Djava.awt.headless=false", f"-Dgravity.qa.results={RESULTS}",
               f"-Dgravity.qa.role={role}"]
    command += shlex.split(expand(config["vmArgs"]))
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    log = (RESULTS / f"{role}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT)
    processes.append(process)
    return process


try:
    for display in (":91", ":92"):
        processes.append(subprocess.Popen(["Xvfb", display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
    server = launch("runServer", "server")
    deadline = time.monotonic() + 180
    while True:
        if server.poll() is not None or time.monotonic() >= deadline:
            raise RuntimeError("Dedicated test server did not start")
        try:
            with socket.create_connection(("127.0.0.1", 25565), timeout=1):
                break
        except OSError:
            time.sleep(1)
    wearer = launch("runClient", "wearer", ":91")
    observer = launch("runClient", "observer", ":92")
    deadline = time.monotonic() + 600
    while not all((RESULTS / f"{role}.pass").exists() for role in ("wearer", "observer")):
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(process.poll() is not None for process in (server, wearer, observer)):
            raise RuntimeError("A Minecraft process exited before validation completed")
        if time.monotonic() >= deadline:
            raise TimeoutError("Two-client gameplay validation timed out")
        time.sleep(1)
    print("REAL_TWO_CLIENT_E2E_PASSED", flush=True)
finally:
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
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name}: final log lines ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-35:]))
