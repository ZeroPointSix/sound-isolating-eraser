"""Isolated real Forge client/server lifecycle and rendering validation."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "build" / ("red-nails-e2e-" + str(time.time_ns()))
RESULTS.mkdir(parents=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/red-nails-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("RED_NAILS_QA_PORT", "25587"))
TIMEOUT = int(os.environ.get("RED_NAILS_QA_TIMEOUT", "900"))
PURSUIT = os.environ.get("RED_NAILS_QA_PURSUIT") == "1"
ACCEPTANCE = os.environ.get("RED_NAILS_QA_ACCEPTANCE") == "1"
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role):
    server = role == "server"
    config = CONFIGS["runServer" if server else "runClient"]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if server:
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            "allow-flight=true\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=3\n"
            "simulation-distance=3\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "network-compression-threshold=-1\nplayer-idle-timeout=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n")
    else:
        (directory / "config").mkdir(exist_ok=True)
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:zh_cn\nrenderDistance:3\nsimulationDistance:3\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\ngamma:1.0\nfov:0.0\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env.update(LIBGL_ALWAYS_SOFTWARE="1", ALSOFT_DRIVERS="null")
    if not server:
        env["DISPLAY"] = display
    args = shlex.split(expand(config["args"]))
    if not server:
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "NailsTester", "--width", "960", "--height", "540",
                 "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-XX:ActiveProcessorCount=2",
               "-Xmx1024M" if server else "-Xmx1536M", f"-Drednails.qa.results={RESULTS}"]
    if PURSUIT:
        command += ["-Drednails.qa.pursuit=true"]
    if ACCEPTANCE:
        command += ["-Drednails.qa.acceptance=true"]
    command += shlex.split(expand(config["vmArgs"]))
    if server:
        # Allow registry synchronization during a software-rendered client join.
        command += ["-Dforge.readTimeout=120"]
        print("RED_NAILS_QA_TRANSPORT_READ_TIMEOUT_SECONDS=120", flush=True)
    if not server:
        natives = directory / "lwjgl-natives"
        natives.mkdir(exist_ok=True)
        command += [f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}"]
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    name = "server-restart" if server and (RESULTS / "server.log").exists() else role
    log = (RESULTS / f"{name}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdin=subprocess.PIPE,
                               stdout=log, stderr=subprocess.STDOUT, text=True)
    processes.append(process)
    print(f"RED_NAILS_QA_PROCESS role={role} pid={process.pid} cwd={directory}", flush=True)
    return process


def wait_for(markers, running):
    deadline = time.monotonic() + TIMEOUT
    while time.monotonic() < deadline:
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(p.read_text() for p in failures))
        if all((RESULTS / (name + ".pass")).exists() for name in markers):
            return
        if any(p.poll() is not None for p in running):
            raise RuntimeError("Minecraft exited before evidence completed")
        time.sleep(1)
    raise TimeoutError("Missing evidence: " + str([n for n in markers if not (RESULTS / (n + ".pass")).exists()]))


def ready(server):
    deadline = time.monotonic() + TIMEOUT
    while time.monotonic() < deadline and server.poll() is None:
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=1):
                return
        except OSError:
            time.sleep(1)
    raise RuntimeError("Dedicated server did not become ready")


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def save_stop(server):
    server.stdin.write("stop\n")
    server.stdin.flush()
    if server.wait(timeout=180) != 0:
        raise RuntimeError("Dedicated server did not save and stop cleanly")


try:
    print(f"RED_NAILS_QA_RESULTS={RESULTS}", flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    display = ":" + xvfb.stdout.readline().strip()
    server = launch("server")
    ready(server)
    client = launch("client")
    if ACCEPTANCE:
        required = ["acceptance-boat", "acceptance-bottom", "acceptance-damage",
                    "acceptance-coexist", "acceptance-recall", "acceptance-recall-watch",
                    "acceptance-unequip", "acceptance-client"]
        wait_for(required, [server, client])
        stop(client)
        save_stop(server)
        print("RED_NAILS_NATIVE_ACCEPTANCE_PASSED", flush=True)
        sys.exit(0)
    required = ["server-input", "client-guard", "server-backlash", "client-backlash", "server-recall", "client-recalled"]
    required += (["server-shore", "client-shore-complete", "server-wild-started", "server-wild", "client-wild-complete"]
                 if PURSUIT else ["server-water", "client-water"])
    wait_for(required, [server, client])
    stop(client)
    save_stop(server)
    server = launch("server")
    ready(server)
    client = launch("client-restored")
    wait_for(["server-restart", "client-restored"], [server, client])
    stop(client)
    save_stop(server)
    print("RED_NAILS_PURSUIT_REAL_CLIENT_AND_RESTART_E2E_PASSED" if PURSUIT
          else "RED_NAILS_REAL_CLIENT_AND_RESTART_E2E_PASSED", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name} ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-20:]))
