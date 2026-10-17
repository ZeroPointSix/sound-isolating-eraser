"""Isolated Forge dedicated server + real OpenGL client, actual item-use packets."""
import json
import os
from pathlib import Path
import shlex
import shutil
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
for source_set in ("main", "suxiaTest"):
    source_root = ROOT / "src" / source_set / "java"
    for source in source_root.rglob("Suxia*.java"):
        compiled = ROOT / "build/classes/java" / source_set / source.relative_to(source_root).with_suffix(".class")
        if not compiled.is_file() or compiled.stat().st_mtime_ns < source.stat().st_mtime_ns:
            raise RuntimeError("Stale Suxia bytecode: run ./gradlew -PsuxiaE2E writeSuxiaTestClasspath before E2E: " + str(source))
RESULTS = ROOT / "build" / ("suxia-e2e-" + str(time.time_ns()))
RESULTS.mkdir(parents=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/suxia-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("SUXIA_QA_PORT", "25589"))
TIMEOUT = int(os.environ.get("SUXIA_QA_TIMEOUT_SECONDS", "600"))
IDLE_ONLY = os.environ.get("SUXIA_QA_IDLE_ONLY") == "1"
COOLDOWN_ONLY = os.environ.get("SUXIA_QA_COOLDOWN_ONLY") == "1"
if COOLDOWN_ONLY and IDLE_ONLY:
    raise ValueError("Choose one targeted QA mode")
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role):
    server = role == "server"
    config = CONFIGS["runServer" if server else "runClient"]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if server:
        source = os.environ.get("SUXIA_QA_WORLD_SOURCE")
        if source and not (directory / "world").exists():
            if not (Path(source) / "level.dat").is_file():
                raise ValueError("SUXIA_QA_WORLD_SOURCE must be an existing disposable QA world")
            shutil.copytree(source, directory / "world", ignore=shutil.ignore_patterns("session.lock"))
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            # Server-driven QA teleports must not trigger the unrelated floating kick.
            "allow-flight=true\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=3\n"
            "simulation-distance=3\nlevel-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n")
    else:
        (directory / "config").mkdir()
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:zh_cn\nrenderDistance:3\nsimulationDistance:3\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\ngamma:1.0\nfov:0.0\n")
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
                i = args.index(option)
                del args[i:i + 2]
        args += ["--username", "SuxiaTester", "--width", "960", "--height", "540",
                 "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1024M" if server else "-Xmx1536M", f"-Dsuxia.qa.results={RESULTS}"]
    if IDLE_ONLY:
        command += ["-Dsuxia.qa.idleOnly=true"]
    if COOLDOWN_ONLY:
        command += ["-Dsuxia.qa.cooldownOnly=true", f"-Dsuxia.qa.port={PORT}"]
    command += shlex.split(expand(config["vmArgs"]))
    if not server:
        natives = directory / "lwjgl-natives"
        natives.mkdir()
        command += [f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}"]
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    log_name = "server-restart" if server and (RESULTS / "server.log").exists() else role
    log = (RESULTS / f"{log_name}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdin=subprocess.PIPE,
                               stdout=log, stderr=subprocess.STDOUT, text=True)
    processes.append(process)
    return process


def wait_for(required, running):
    deadline = time.monotonic() + TIMEOUT
    while time.monotonic() < deadline:
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(p.read_text() for p in failures))
        if all((RESULTS / (name + ".pass")).exists() for name in required):
            return
        if any(p.poll() is not None for p in running):
            raise RuntimeError("Minecraft exited before evidence completed")
        time.sleep(1)
    raise TimeoutError("Missing evidence: " + str([n for n in required if not (RESULTS / (n + ".pass")).exists()]))


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


try:
    print(f"SUXIA_QA_RESULTS={RESULTS}", flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    display = ":" + xvfb.stdout.readline().strip()
    server = launch("server")
    deadline = time.monotonic() + TIMEOUT
    while True:
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Dedicated server did not become ready")
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=1):
                break
        except OSError:
            time.sleep(1)
    client = launch("client")
    if COOLDOWN_ONLY:
        print(f"COOLDOWN_PROCESSES server={server.pid} client={client.pid}", flush=True)
        wait_for(["server-ready-restart", "client-ready-restart"], [server, client])
        server.stdin.write("stop\n")
        server.stdin.flush()
        if server.wait(timeout=180) != 0:
            raise RuntimeError("Cooldown QA server did not save cleanly")
        print(f"COOLDOWN_SERVER_STOPPED pid={server.pid} exit=0", flush=True)
        server = launch("server")
        print(f"COOLDOWN_SERVER_RESTARTED pid={server.pid}", flush=True)
        deadline = time.monotonic() + TIMEOUT
        while True:
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Cooldown QA restarted server not ready")
            failures = list(RESULTS.glob("*.failed"))
            if failures:
                raise AssertionError("; ".join(p.read_text() for p in failures))
            # The listening socket opens before Forge finishes loading the saved world.
            if "SUXIA_COOLDOWN_SERVER_READY stage=20" in (RESULTS / "server-restart.log").read_text(errors="replace"):
                break
            time.sleep(1)
        (RESULTS / "restart-server-ready.signal").write_text(f"pid={server.pid}\n")
        wait_for(["server-cool-complete", "client-cool-restarted"], [server, client])
        stop(client)
        server.stdin.write("stop\n")
        server.stdin.flush()
        if server.wait(timeout=180) != 0:
            raise RuntimeError("Cooldown QA final server save failed")
        print("SUXIA_COOLDOWN_BOUNDARIES_REAL_CLIENT_PASSED", flush=True)
        sys.exit(0)
    if IDLE_ONLY:
        wait_for(["server-idle-stationary", "client-idle-stationary",
                  "server-idle-turned", "client-idle-turned", "server-idle-complete"], [server, client])
        stop(client)
        server.stdin.write("stop\n")
        server.stdin.flush()
        if server.wait(timeout=180) != 0:
            raise RuntimeError("Idle QA server failed to shut down cleanly")
        print("SUXIA_IDLE_REAL_CLIENT_PASSED", flush=True)
        sys.exit(0)
    wait_for(["client-gallery", "client-summoned", "client-recalled", "client-second-summoned",
              "client-second-recalled", "client-cooldown-rejected", "client-logout",
              "server-lifecycle", "server-multi-watch", "server-cooldown-rejected"], [server, client])
    stop(client)
    rejoined = launch("client-rejoin")
    wait_for(["server-rejoin", "client-rejoined", "server-persistence", "server-wake",
              "client-hostile-sleep", "client-hostile-awake", "server-ready-restart"], [server, rejoined])
    stop(rejoined)
    server.stdin.write("stop\n")
    server.stdin.flush()
    if server.wait(timeout=180) != 0:
        raise RuntimeError("Dedicated server failed to shut down cleanly")
    server = launch("server")
    deadline = time.monotonic() + TIMEOUT
    while True:
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Restarted dedicated server did not become ready")
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=1):
                break
        except OSError:
            time.sleep(1)
    restored = launch("client-restored")
    wait_for(["server-restart", "client-restored", "server-restored-wake", "client-restored-awake"], [server, restored])
    stop(restored)
    server.stdin.write("stop\n")
    server.stdin.flush()
    if server.wait(timeout=180) != 0:
        raise RuntimeError("Restarted dedicated server failed to shut down cleanly")
    print("SUXIA_REAL_CLIENT_AND_DEDICATED_SERVER_E2E_PASSED", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name} ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-25:]))
