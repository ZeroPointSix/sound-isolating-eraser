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
# Archived 32d83a5 world: res_tenement at (-752, 88, -752) reached only one floor.
REGRESSION_SEED = 5376539267146632372
SEED = int(os.environ.get("NIGHT_QA_SEED", str(REGRESSION_SEED)))
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role, display=None, session="initial"):
    config = CONFIGS["runServer" if role == "server" else "runClient"]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if role == "server":
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            f"level-seed={SEED}\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=2\n"
            "simulation-distance=2\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":63},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
            "generate-structures=false\nspawn-monsters=false\ngamemode=creative\n")
        # Pin the server config before boot: in-memory ModConfig.set() calls race
        # the Forge file watcher, which can reload the just-written defaults and
        # silently revert values (observed: entryBase reverted to 0.02 so the
        # second sleeper lost the entry roll). Writing the file removes the race.
        server_config = directory / "world/serverconfig"
        server_config.mkdir(parents=True, exist_ok=True)
        (server_config / "eternal-night-server.toml").write_text(
            "[eternalNight]\n"
            "enabled = true\n"
            'entryDimensions = ["minecraft:overworld"]\n'
            "entryBase = 1.0\n"
            "entryPerSleeplessDay = 0.08\n"
            "entryCap = 1.0\n"
            "newDoorExitChance = 0.01\n"
            "doorGuarantee = 100\n"
            "sharedExitSeconds = 300\n"
            "spawnCityMonsters = false\n"
            "nearbyShadowCap = 10\n"
            "nearbyNeonCap = 12\n"
            "returnAndResetOnDeath = true\n"
            "disablePlantGrowth = true\n")
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
    env["LP_NUM_THREADS"] = "2"
    args = shlex.split(expand(config["args"]))
    if display:
        env["DISPLAY"] = display
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "NightOwner" if role == "owner" else "NightObserver",
                 "--width", "960", "--height", "540", "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1536M" if role == "server" else "-Xmx1280M",
               f"-Dnight.qa.results={RESULTS}", f"-Dnight.qa.role={role}",
               f"-Dnight.qa.session={session}"]
    command += shlex.split(expand(config["vmArgs"]))
    if display:
        natives = directory / "lwjgl-natives"
        natives.mkdir(exist_ok=True)
        command += [f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}"]
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    suffix = "" if session == "initial" else "-" + session
    log = (RESULTS / f"{role}{suffix}.log").open("w")
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


def wait_server(server, session="initial"):
    deadline = time.monotonic() + 600
    suffix = "" if session == "initial" else "-" + session
    logfile = RESULTS / f"server{suffix}.log"
    while True:
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Isolated Forge server did not become ready; see role logs")
        # The listener opens before spawn generation finishes. Booting both native
        # renderers at that point can stall login/keepalive on a four-core host.
        text = logfile.read_text(errors="replace") if logfile.exists() else ""
        if 'For help, type "help"' not in text or "Done (" not in text:
            time.sleep(1)
            continue
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=1):
                return
        except OSError:
            time.sleep(1)


def wait_evidence(server, clients, required, suffix=".pass"):
    deadline = time.monotonic() + int(os.environ.get("NIGHT_QA_TIMEOUT_SECONDS", "1200"))
    while True:
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(process.poll() is not None for process in [server, *clients]):
            raise RuntimeError("Minecraft exited before evidence completed; see role logs")
        if all((RESULTS / (name + suffix)).exists()
               and (RESULTS / (name + suffix)).read_text().strip() for name in required):
            return
        if time.monotonic() > deadline:
            raise TimeoutError("Native-client scenario timed out; see role logs and screenshots")
        time.sleep(1)


def save_and_stop(server):
    server.stdin.write("save-all flush\nstop\n")
    server.stdin.flush()
    if server.wait(timeout=90) != 0:
        raise RuntimeError("Minecraft server failed during normal save/shutdown")


try:
    stamp = str(time.time_ns())
    for path in list(RESULTS.glob("*.pass")) + list(RESULTS.glob("*.failed")) + list(RESULTS.glob("*.complete")):
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
    wait_server(server)
    clients = [launch("owner", displays[0]), launch("observer", displays[1])]
    checkpoint = ["server-checkpoint", "owner-entry-caption", "observer-entry-caption", "owner-white", "observer-white",
                  "owner-restart-ready", "observer-restart-ready"]
    wait_evidence(server, clients, checkpoint)
    original_pid = server.pid
    save_and_stop(server)
    for client in clients:
        stop(client)
    if not (world / "level.dat").is_file():
        raise AssertionError("Original world was not saved before restart")
    server = launch("server", session="reload")
    if server.pid == original_pid:
        raise AssertionError("Restart did not create a new server process")
    print(f"NIGHT_REAL_RESTART old_pid={original_pid} new_pid={server.pid} world={world}", flush=True)
    wait_server(server, session="reload")
    clients = [launch("owner", displays[0], "reload"), launch("observer", displays[1], "reload")]
    required = checkpoint + ["server-reload", "owner-reload-white", "observer-reload-white",
                             "server-cycle", "owner-return", "observer-return",
                             "server-death", "owner-death"]
    wait_evidence(server, clients, required)
    wait_evidence(server, clients, ["server-survey", "owner-survey"], ".complete")
    print((RESULTS / "server-survey.complete").read_text().strip(), flush=True)
    survey = json.loads((RESULTS / "city-survey.json").read_text())
    if int(survey["worldSeed"]) != SEED:
        raise AssertionError(f"NIGHT_BUILDING_SEED_MISMATCH expected={SEED} actual={survey['worldSeed']}")
    if survey["templatesLocated"] != 12 or survey["nativeEntrancesPassed"] != 12 or survey["floorsReached"] != 12:
        failed = [row for row in survey["observations"] if not row.get("floorsReachedExpected", False)]
        raise AssertionError(f"NIGHT_BUILDING_ACCESS_FAILED {json.dumps(failed, ensure_ascii=False)}")
    if SEED == REGRESSION_SEED:
        counterexample = next((row for row in survey["observations"]
                               if row["template"] == "res_tenement" and row["origin"] == "-752, 88, -752"), None)
        if counterexample is None or counterexample.get("floorsReached", 0) < 4:
            raise AssertionError(f"NIGHT_FLOOR_COUNTEREXAMPLE_NOT_COVERED {counterexample}")
        print(f"NIGHT_FLOOR_COUNTEREXAMPLE_PASS {json.dumps(counterexample, ensure_ascii=False)}", flush=True)
    print(f"NIGHT_BUILDING_ACCESS_PASS seed={SEED} nativeEntrances=12/12 reachableDoorFloors=12/12", flush=True)
    for name in required:
        print((RESULTS / (name + ".pass")).read_text().strip(), flush=True)
    save_and_stop(server)
    failures = list(RESULTS.glob("*.failed"))
    if failures:
        raise AssertionError("; ".join(path.read_text() for path in failures))
    if not all((RESULTS / (name + ".pass")).read_text().strip() for name in required):
        raise AssertionError("Empty evidence marker after shutdown")
    print("NIGHT_TWO_CLIENT_E2E_PASS", flush=True)
    print("NIGHT_RESTART_AND_REAL_CLIENT_DEATH_PASS", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
