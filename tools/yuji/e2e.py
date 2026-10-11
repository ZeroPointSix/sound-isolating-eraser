"""Opt-in real Forge client and dedicated-server rendering/audio smoke test."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
BOUNDARIES = os.environ.get("YUJI_QA_BOUNDARIES") == "1"
RESULTS = ROOT / "build" / (("yuji-boundaries-" if BOUNDARIES else "yuji-e2e-") + str(time.time_ns()))
RESULTS.mkdir(parents=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/yuji-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("YUJI_QA_PORT", "25589"))
processes, logs = [], []


def expand(value):
    return value.replace("${workspaceFolder}", str(ROOT))


def launch(role, username="YujiTester"):
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
            "generate-structures=false\nspawn-monsters=false\ngamemode=creative\n")
    else:
        (directory / "config").mkdir()
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "lang:zh_cn\nrenderDistance:3\nsimulationDistance:3\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n"
            "gamma:1.0\nfov:0.0\n")
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
        args += ["--username", username, "--width", "960", "--height", "540",
                 "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1024M" if server else "-Xmx2048M", f"-Dyuji.qa.results={RESULTS}"]
    if BOUNDARIES:
        command += ["-Dyuji.qa.boundaries=true"]
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


def wait_for(predicate, running, message, seconds):
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
    print(f"YUJI_QA_RESULTS={RESULTS}", flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    display = ":" + xvfb.stdout.readline().strip()
    server = launch("server")
    wait_for(ready, [server], "dedicated server startup", 600)
    client = launch("client")
    if BOUNDARIES:
        witness = launch("witness", "YujiWitness")
        wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in
                            ["server-boundaries", "client-boundaries-YujiTester", "client-boundaries-YujiWitness"]),
                 [server, client, witness], "two real players, grudge, buffed slashes and body-contact boundaries", 900)
        stop(witness)
    else:
        wait_for(lambda: all((RESULTS / (name + ".pass")).exists() for name in ["server-gallery", "client-gallery"]),
                 [server, client], "eleven rendered profiles, real-player AI, collisions, audio and projectile", 900)
    stop(client)
    server.stdin.write("stop\n")
    server.stdin.flush()
    if server.wait(timeout=180) != 0:
        raise RuntimeError("Dedicated server did not shut down cleanly")
    print("YUJI_TWO_REAL_CLIENT_BOUNDARIES_PASSED" if BOUNDARIES else "YUJI_REAL_CLIENT_AI_COLLISION_AND_AUDIO_ACCEPTANCE_PASSED", flush=True)
finally:
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    for path in RESULTS.glob("*.log"):
        print(f"--- {path.name} ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-20:]))
    for path in sorted(RESULTS.glob("*.pass")):
        print(f"{path.name}: {path.read_text().strip()}")
