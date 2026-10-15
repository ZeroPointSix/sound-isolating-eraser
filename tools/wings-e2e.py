"""Run real Forge clients and a loopback-only server for wind-thunder wings E2E."""
import json
import hashlib
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time
import sys

ROOT = Path(__file__).resolve().parents[1]
MOTION_ONLY = "--motion-only" in sys.argv
RESULTS = ROOT / ("build/wings-motion-e2e" if MOTION_ONLY else "build/wings-e2e")
# Fresh world every run: player NBT (deployed wings, food, position) must not carry over.
if RESULTS.exists():
    RESULTS.rename(RESULTS.with_name("wings-e2e-previous-" + str(time.time_ns())))
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
            "allow-flight=false\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n"
        )
    else:
        (directory / "config").mkdir(exist_ok=True)
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "renderDistance:4\nsimulationDistance:5\nguiScale:2\nmaxFps:30\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\n"
            "tutorialStep:none\n"
            "soundCategory_master:1.0\nsoundCategory_players:1.0\n"
        )
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    # Exercise the real decoder/mixer/source lifecycle in headless CI. This is
    # an OpenAL output backend, not a claim of human listening or speaker output.
    env["ALSOFT_DRIVERS"] = "null"
    env["ALSOFT_LOGLEVEL"] = "2"
    if display:
        env["DISPLAY"] = display
    args = shlex.split(expand(config["args"]))
    if role != "server":
        for option in ("--username", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "WingsWearer" if role == "wearer" else "WingsObserver",
                 "--width", "960", "--height", "540", "--quickPlayMultiplayer", "127.0.0.1:25566"]
    wings_role = {"server": "server", "wearer": "wingswearer", "observer": "wingsobserver"}[role]
    command = ["java", "-Xmx1G",
               f"-Dwings.qa.motionOnly={str(MOTION_ONLY).lower()}",
               f"-Dwings.qa.results={RESULTS}", f"-Dwings.qa.role={wings_role}",
               f"-Dgravity.qa.results={RESULTS}", f"-Dgravity.qa.role={role}"]
    command += shlex.split(expand(config["vmArgs"]))
    if role != "server":
        natives = directory / "lwjgl-natives"
        natives.mkdir(exist_ok=True)
        command += [f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}"]
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    log = (RESULTS / f"{role}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT)
    processes.append(process)
    return process


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
    wearer = launch("runClient", "wearer", ":93")
    observer = launch("runClient", "observer", ":94")
    # Class/resource loading must not consume the gameplay acceptance budget.
    # Both waits remain bounded even when one client never joins or gets ready.
    startup_deadline = time.monotonic() + 600
    gameplay_deadline = None
    required = ("wings-wearer", "wings-observer", "wings-server", "wings-controls", "wings-key-conflicts",
                "wings-input-rebinding", "wings-animation", "wings-audio", "wings-network",
                "wings-network-server")
    if MOTION_ONLY:
        required = ("wings-motion-wingswearer", "wings-motion-wingsobserver", "wings-motion-server")
    while not all((RESULTS / f"{role}.pass").exists() for role in required):
        failures = list(RESULTS.glob("wings-*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(process.poll() is not None for process in (server, wearer, observer)):
            raise RuntimeError("A Minecraft process exited before validation completed")
        now = time.monotonic()
        if gameplay_deadline is None:
            if all((RESULTS / f"wings-{role}.ready").exists() for role in ("wearer", "observer")):
                gameplay_deadline = now + 600
                print("WINGS_CLIENTS_READY: starting bounded 600-second gameplay validation", flush=True)
            elif now >= startup_deadline:
                raise TimeoutError("Two-client wings startup did not reach ready within 600 seconds")
        elif now >= gameplay_deadline:
            raise TimeoutError("Two-client wings gameplay validation exceeded 600 seconds after readiness")
        time.sleep(1)
    if MOTION_ONLY:
        print("REAL_TWO_CLIENT_WINGS_MOTION_PASSED (actual travel, configuration speeds, release/jump, stow/redeploy, simultaneous flight)", flush=True)
        sys.exit(0)
    audio = json.loads((RESULTS / "wings-audio.json").read_text())
    assert all(count > 0 for count in audio["sourcePlaying"].values()), audio
    network = json.loads((RESULTS / "wings-network-client.json").read_text())
    assert network["forwardedMovementPackets"] >= 60 and network["multiMoveBatches"] > 0, network
    frames = json.loads((RESULTS / "wings-animation-frames.json").read_text())
    for direction in ("deploy", "stow"):
        sequence = [frame for frame in frames if frame["file"].startswith(f"animation-{direction}-")]
        hashes = {hashlib.sha256((RESULTS / frame["file"]).read_bytes()).hexdigest() for frame in sequence}
        assert len(hashes) >= 3, f"{direction}: repeated screenshots are not animation evidence"
        assert sum(frame["changedWingRegionPixels"] >= 20 for frame in sequence) >= 2
    for role in ("wearer", "observer"):
        log = (RESULTS / f"{role}.log").read_text(errors="replace")
        assert "Turning off sounds" not in log and "Error starting SoundSystem" not in log, f"{role}: audio disabled"
    print("REAL_TWO_CLIENT_WINGS_E2E_PASSED (native input, impaired TCP, OpenAL sources, distinct rendered animation)", flush=True)
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
