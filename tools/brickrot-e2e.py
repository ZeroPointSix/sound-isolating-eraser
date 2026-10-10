"""Real native-input, two-client Brickrot test with a dedicated-server restart."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "build/brickrot-e2e"
RESULTS.mkdir(parents=True, exist_ok=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/brickrot-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("BRICKROT_QA_PORT", "25581"))
processes, logs, displays = [], [], []


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
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=4\n"
            "simulation-distance=3\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=creative\n")
    else:
        (directory / "config").mkdir(exist_ok=True)
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:zh_cn\nrenderDistance:4\nsimulationDistance:5\nguiScale:2\nmaxFps:20\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    if os.environ.get("BRICKROT_QA_PACKAGED") == "1":
        # ForgeGradle maps the already-built production jar into the dev runtime.
        # Remove main's loose outputs so a missing class/resource in that jar cannot be masked.
        assert str(ROOT / "build/classes/java/main") not in CLASSPATH
        assert str(ROOT / "build/resources/main") not in CLASSPATH
        entries = env["MOD_CLASSES"].split(os.pathsep)
        env["MOD_CLASSES"] = os.pathsep.join(entry for entry in entries
                                             if not entry.startswith("sound_isolating_eraser%%"))
        assert "sound_isolating_eraser%%" not in env["MOD_CLASSES"]
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    args = shlex.split(expand(config["args"]))
    if role != "server":
        env["DISPLAY"] = displays[0 if role == "user" else 1]
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "BrickrotUser" if role == "user" else "BrickrotObserver",
                 "--width", "960", "--height", "540", "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx768M", f"-Dbrickrot.qa.results={RESULTS}", f"-Dbrickrot.qa.role={role}",
               f"-Dbrickrot.qa.restart={str(restart).lower()}"]
    command += shlex.split(expand(config["vmArgs"]))
    if role != "server":
        natives = directory / ("lwjgl-natives-restart" if restart else "lwjgl-natives")
        natives.mkdir(exist_ok=True)
        command += [f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}"]
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
    raise RuntimeError("Brickrot dedicated server did not become ready")


def wait_evidence(required, running):
    deadline = time.monotonic() + int(os.environ.get("BRICKROT_QA_TIMEOUT_SECONDS", "600"))
    while not all((RESULTS / (name + ".pass")).exists() for name in required):
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(process.poll() is not None for process in running):
            raise RuntimeError("Minecraft exited before Brickrot evidence completed")
        if time.monotonic() >= deadline:
            raise TimeoutError("Brickrot native-client validation timed out")
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
    # Preserve previous worlds and evidence; a new invocation starts with a fresh fixture.
    for path in list(RESULTS.glob("*.pass")) + list(RESULTS.glob("*.failed")):
        path.rename(path.with_suffix(path.suffix + ".previous-" + str(time.time_ns())))
    world = RESULTS / "server/world"
    if world.exists():
        world.rename(world.with_name("world-previous-" + str(time.time_ns())))
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    for index in range(2):
        xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        processes.append(xvfb)
        displays.append(":" + xvfb.stdout.readline().strip())
    server = launch("server")
    wait_ready(server)
    clients = [launch("user"), launch("observer")]
    wait_evidence(["server-cycle", "native-interactions", "user-breached", "observer-breached"], [server, *clients])
    server.stdin.write("stop\n")
    server.stdin.flush()
    server.wait(timeout=45)
    for client in clients:
        stop(client)
    server = launch("server", restart=True)
    wait_ready(server)
    clients = [launch("user", restart=True), launch("observer", restart=True)]
    wait_evidence(["server-restart", "user-restarted", "observer-restarted", "server-turn", "user-turned", "observer-turned"], [server, *clients])
    print("BRICKROT_REAL_TWO_CLIENT_AND_SERVER_RESTART_E2E_PASSED", flush=True)
    print("PRODUCTION_JAR_MODE=" + os.environ.get("BRICKROT_QA_PACKAGED", "0"), flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name} ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-20:]))
