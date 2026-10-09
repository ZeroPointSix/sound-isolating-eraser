"""Native-input, two-client Forge shaxia cycle followed by a real server restart."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "build/shaxia-e2e"
RESULTS.mkdir(parents=True, exist_ok=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/shaxia-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("SHAXIA_QA_PORT", "25579"))
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role, restart=False):
    config = CONFIGS["runServer" if role == "server" else "runClient"]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if role == "server":
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=3\n"
            "simulation-distance=3\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n")
    else:
        (directory / "config").mkdir(exist_ok=True)
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:zh_cn\nrenderDistance:3\nsimulationDistance:5\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    if role != "server":
        env["DISPLAY"] = displays[0 if role == "user" else 1]
    args = shlex.split(expand(config["args"]))
    if role != "server":
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "ShaxiaUser" if role == "user" else "ShaxiaObserver", "--width", "960",
                 "--height", "540", "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx768M", f"-Dshaxia.qa.results={RESULTS}", f"-Dshaxia.qa.role={role}",
               f"-Dshaxia.qa.restart={str(restart).lower()}"]
    command += shlex.split(expand(config["vmArgs"]))
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    log = (RESULTS / f"{role}{'-restart' if restart else ''}.log").open("w")
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
    raise RuntimeError("Shaxia dedicated server did not become ready")


def wait_evidence(required, running):
    deadline = time.monotonic() + int(os.environ.get("SHAXIA_QA_TIMEOUT_SECONDS", "600"))
    while not all((RESULTS / (name + ".pass")).exists() for name in required):
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(p.poll() is not None for p in running):
            raise RuntimeError("Minecraft exited before shaxia evidence completed")
        if time.monotonic() >= deadline:
            raise TimeoutError("Shaxia real-client validation timed out")
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
    # Each invocation gets a fresh world, without deleting any prior evidence.
    for path in list(RESULTS.glob("*.pass")) + list(RESULTS.glob("*.failed")):
        path.rename(path.with_suffix(path.suffix + ".previous-" + str(time.time_ns())))
    world = RESULTS / "server/world"
    if world.exists():
        world.rename(world.with_name("world-previous-" + str(time.time_ns())))
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    displays = []
    for index in range(2):
        xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        processes.append(xvfb)
        displays.append(":" + xvfb.stdout.readline().strip())
    server = launch("server")
    wait_ready(server)
    clients = [launch("user"), launch("observer")]
    wait_evidence(["server-cycle", "user-saved", "observer-saved"], [server, *clients])
    server.stdin.write("stop\n")
    server.stdin.flush()
    server.wait(timeout=45)
    for client in clients:
        stop(client)
    server = launch("server", restart=True)
    wait_ready(server)
    clients = [launch("user", restart=True), launch("observer", restart=True)]
    wait_evidence(["server-restart", "user-restarted", "observer-restarted"], [server, *clients])
    print("SHAXIA_REAL_TWO_CLIENT_AND_SERVER_RESTART_E2E_PASSED", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name} ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-20:]))
