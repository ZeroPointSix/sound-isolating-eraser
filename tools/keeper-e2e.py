"""Single-client Forge keeper camera E2E: flash, sepia filter, polaroid HUD."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "qa-evidence/20261016-e2e-x11"
RESULTS.mkdir(parents=True, exist_ok=True)
CONFIGS = {c["name"]: c for c in json.loads((ROOT / ".vscode/launch.json").read_text())["configurations"]}
CLASSPATH = (ROOT / "build/keeper-test-classpath.txt").read_text().strip()
PORT = int(os.environ.get("KEEPER_QA_PORT", "25578"))
processes, logs = [], []
displays = []


def expand(value):
    return value.replace("$" + "{workspaceFolder}", str(ROOT))


def launch(role):
    config = CONFIGS["runServer" if role == "server" else "runClient"]
    directory = RESULTS / role
    directory.mkdir(exist_ok=True)
    if role == "server":
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={PORT}\n"
            "enforce-secure-profile=false\nspawn-protection=0\nview-distance=2\n"
            "network-compression-threshold=-1\nsimulation-distance=2\n"
            "level-type=minecraft:flat\nmax-tick-time=0\n"
            "generate-structures=false\nspawn-monsters=false\ngamemode=survival\n")
    else:
        (directory / "config").mkdir(exist_ok=True)
        (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (directory / "options.txt").write_text(
            "renderDistance:2\nsimulationDistance:2\nguiScale:2\nmaxFps:20\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "showAutosaveIndicator:false\nrenderClouds:false\nnarrator:0\ntutorialStep:none\n"
            "skipMultiplayerWarning:true\njoinedFirstServer:true\n")
    env = os.environ.copy()
    env.update({key: expand(value) for key, value in config["env"].items()})
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    if role != "server":
        env["DISPLAY"] = displays[0]
    args = shlex.split(expand(config["args"]))
    if role != "server":
        for option in ("--username", "--uuid", "--width", "--height", "--quickPlayMultiplayer"):
            while option in args:
                index = args.index(option)
                del args[index:index + 2]
        args += ["--username", "KeeperUser", "--uuid", "11111111-1111-1111-1111-111111111111",
                 "--width", "854", "--height", "480", "--quickPlayMultiplayer", f"127.0.0.1:{PORT}"]
    command = ["java", "-Xmx1536M" if role != "server" else "-Xmx768M",
               f"-Dkeeper.qa.results={RESULTS}", f"-Dkeeper.qa.role={role}"]
    vm = expand(config["vmArgs"]).replace("forge.logging.console.level=debug", "forge.logging.console.level=info")
    command += shlex.split(vm)
    command += ["-cp", CLASSPATH, config["mainClass"], *args]
    log = (RESULTS / f"{role}.log").open("w")
    logs.append(log)
    process = subprocess.Popen(command, cwd=directory, env=env, stdin=subprocess.PIPE,
                               stdout=log, stderr=subprocess.STDOUT, text=True)
    processes.append(process)
    return process


def wait_ready(server):
    deadline = time.monotonic() + 900
    log = RESULTS / "server.log"
    while time.monotonic() < deadline and server.poll() is None:
        text = log.read_text(errors="replace") if log.exists() else ""
        if "For help, type" in text:
            return
        time.sleep(1)
    raise RuntimeError("Keeper dedicated server did not become ready")


def wait_evidence(required, running):
    deadline = time.monotonic() + int(os.environ.get("KEEPER_QA_TIMEOUT_SECONDS", "1200"))
    last_grab = 0
    rapid_until = 0
    saw_shoot = False
    saw_photo = False
    while not all((RESULTS / (name + ".pass")).exists() for name in required):
        failures = list(RESULTS.glob("*.failed"))
        if failures:
            raise AssertionError("; ".join(path.read_text() for path in failures))
        if any(p.poll() is not None for p in running):
            raise RuntimeError("Minecraft exited before keeper evidence completed")
        if time.monotonic() >= deadline:
            raise TimeoutError("Keeper real-client validation timed out")
        log = RESULTS / "server.log"
        text = log.read_text(errors="replace") if log.exists() else ""
        now = time.monotonic()
        if "KEEPER_QA_PHASE=shoot" in text and not saw_shoot:
            saw_shoot = True
            rapid_until = now + 5
        if "KEEPER_QA_PHASE=photo" in text and not saw_photo:
            saw_photo = True
            rapid_until = max(rapid_until, now + 4)
        if now < rapid_until:
            label = "flash" if not saw_photo else "photo"
            x11_grab("xvfb-" + label + "-" + str(int(now * 1000)) + ".png")
            time.sleep(0.12)
            continue
        if now - last_grab >= 8:
            x11_grab("xvfb-" + str(int(now)) + ".png")
            last_grab = now
        time.sleep(0.5)


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def x11_grab(name):
    if not displays:
        return
    subprocess.run(["import", "-display", displays[0], "-window", "root", str(RESULTS / name)],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


try:
    for path in list(RESULTS.glob("*.pass")) + list(RESULTS.glob("*.failed")):
        path.rename(path.with_suffix(path.suffix + ".previous-" + str(time.time_ns())))
    # Reuse the already generated superflat world to skip spawn prep.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", PORT))
    xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    processes.append(xvfb)
    displays.append(":" + xvfb.stdout.readline().strip())
    server = launch("server")
    wait_ready(server)
    client = launch("client")
    wait_evidence(["flash", "sepia", "hud", "server"], [server, client])
    x11_grab("xvfb-final.png")
    print("KEEPER_REAL_CLIENT_FLASH_SEPIA_HUD_E2E_PASSED", flush=True)
finally:
    try:
        x11_grab("xvfb-exit.png")
    except Exception:
        pass
    for process in reversed(processes):
        stop(process)
    for log in logs:
        log.close()
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    passed = all((RESULTS / (n + ".pass")).exists() for n in ["flash", "sepia", "hud", "server"])
    (RESULTS / "results.md").write_text(
        "# Keeper camera client E2E\n\n"
        + "commit=" + commit + "\n"
        + "jar=build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar\n"
        + "display=" + (displays[0] if displays else "none") + "\n"
        + "port=" + str(PORT) + "\n"
        + "passed=" + str(passed) + "\n"
    )
    for path in sorted(RESULTS.glob("*.log")):
        print("--- " + path.name + " ---")
        print("\n".join(path.read_text(errors="replace").splitlines()[-40:]))
