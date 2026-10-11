"""Two real native-input clients against an isolated loopback-only Forge server.

Reuses the repository's Brickrot/Gravity launcher contract and Gradle launch metadata.
Requires: ./gradlew -PnightE2E writeNightTestClasspath, Xvfb, xdotool, Java 17.
"""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "build/night-e2e"
RESULTS.mkdir(parents=True, exist_ok=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/night-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("NIGHT_QA_PORT", "25587"))
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role, display=None):
    config = CONFIGS["runServer" if role == "server" else "runClient"]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if role == "server":
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=2\n"
            "simulation-distance=2\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":63},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
            "generate-structures=false\nspawn-monsters=false\ngamemode=creative\n")
    else:
        (directory / "config").mkdir(exist_ok=True)
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:zh_cn\nrenderDistance:3\nsimulationDistance:5\nguiScale:2\nmaxFps:20\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    env["ALSOFT_DRIVERS"] = "null"
    args = shlex.split(expand(config["args"]))
    if display:
        env["DISPLAY"] = display
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "NightOwner" if role == "owner" else "NightObserver",
                 "--width", "960", "--height", "540", "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1536M" if role == "server" else "-Xmx768M",
               f"-Dnight.qa.results={RESULTS}", f"-Dnight.qa.role={role}"]
    command += shlex.split(expand(config["vmArgs"]))
    if display:
        natives = directory / "lwjgl-natives"
        natives.mkdir(exist_ok=True)
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
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


try:
    stamp = str(time.time_ns())
    for path in list(RESULTS.glob("*.pass")) + list(RESULTS.glob("*.failed")):
        path.rename(path.with_suffix(path.suffix + ".previous-" + stamp))
    world = RESULTS / "server/world"
    if world.exists():
        world.rename(world.with_name("world-previous-" + stamp))
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    displays = []
    for _ in range(2):
        xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        processes.append(xvfb)
        displays.append(":" + xvfb.stdout.readline().strip())
    server = launch("server")
    deadline = time.monotonic() + 600
    while True:
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Isolated Forge server did not become ready; see server.log")
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=1):
                break
        except OSError:
            time.sleep(1)
    clients = [launch("owner", displays[0]), launch("observer", displays[1])]
    required = ["server-cycle", "owner-white", "observer-white", "owner-return", "observer-return"]
    deadline = time.monotonic() + int(os.environ.get("NIGHT_QA_TIMEOUT_SECONDS", "1200"))
    while True:
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(process.poll() is not None for process in [server, *clients]):
            raise RuntimeError("Minecraft exited before evidence completed; see role logs")
        if all((RESULTS / (name + ".pass")).exists()
               and (RESULTS / (name + ".pass")).read_text().strip() for name in required):
            break
        if time.monotonic() > deadline:
            raise TimeoutError("Native-client scenario timed out; see role logs and screenshots")
        time.sleep(1)
    for name in required:
        print((RESULTS / (name + ".pass")).read_text().strip(), flush=True)
    server.stdin.write("stop\n")
    server.stdin.flush()
    if server.wait(timeout=90) != 0:
        raise RuntimeError("Minecraft server failed during shutdown; see server.log")
    failures = list(RESULTS.glob("*.failed"))
    if failures:
        raise AssertionError("; ".join(path.read_text() for path in failures))
    if not all((RESULTS / (name + ".pass")).read_text().strip() for name in required):
        raise AssertionError("Empty evidence marker after shutdown")
    print("NIGHT_TWO_CLIENT_E2E_PASS", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
