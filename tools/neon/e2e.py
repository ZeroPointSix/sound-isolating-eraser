"""Real Forge client, dedicated server, rendered assets and engulf lifecycle."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "build" / ("neon-e2e-" + str(time.time_ns()))
RESULTS.mkdir(parents=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/neon-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("NEON_QA_PORT", "25587"))
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role):
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
        distance = 2 if role == "client-rejoin" else 5
        (directory / "options.txt").write_text(
            f"lang:zh_cn\nrenderDistance:{distance}\nsimulationDistance:{distance}\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n"
            "gamma:1.0\nfov:0.0\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    env["ALSOFT_DRIVERS"] = "null"
    if not server:
        env["DISPLAY"] = display
    args = shlex.split(expand(config["args"]))
    if not server:
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        width, height = ("640", "360") if role == "client-rejoin" else ("960", "540")
        args += ["--username", "NeonTester", "--width", width, "--height", height,
                 "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1024M" if server else "-Xmx2048M", f"-Dneon.qa.results={RESULTS}"]
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


def wait_ready(server):
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline and server.poll() is None:
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=1):
                return
        except OSError:
            time.sleep(1)
    raise RuntimeError("Neon dedicated server did not become ready")


def wait_evidence(required, running):
    deadline = time.monotonic() + int(os.environ.get("NEON_QA_TIMEOUT_SECONDS", "600"))
    while not all((RESULTS / (name + ".pass")).exists() for name in required):
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(p.poll() is not None for p in running):
            raise RuntimeError("Minecraft exited before neon evidence completed")
        if time.monotonic() >= deadline:
            missing = [name for name in required if not (RESULTS / (name + ".pass")).exists()]
            raise TimeoutError(f"Neon real-client validation timed out: {missing}")
        time.sleep(1)


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


try:
    print(f"NEON_QA_RESULTS={RESULTS}", flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    display = ":" + xvfb.stdout.readline().strip()
    server = launch("server")
    wait_ready(server)
    client = launch("client")
    wait_evidence(["client-gallery", "client-escape", "server-escape", "server-timeout",
                   "client-released-death", "client-released-timeout",
                   "client-logout", "server-logout"], [server, client])
    stop(client)
    rejoining = launch("client-rejoin")
    wait_evidence(["server-rejoin", "client-rejoined", "server-dimension", "client-dimension"], [server, rejoining])
    server.stdin.write("stop\n")
    server.stdin.flush()
    server.wait(timeout=180)
    print("NEON_REAL_CLIENT_AND_DEDICATED_SERVER_E2E_PASSED", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name} ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-20:]))
