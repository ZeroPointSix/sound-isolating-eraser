"""Four synthetic baseline windows; real client, unchanged production server mod."""

import csv
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import tarfile
import time

import production_probe as p


PLAYER = "T2Perf"
FIXTURES = p.OUT / "fixtures"
DIMENSIONS = {"overworld": "minecraft:overworld", "nether": "minecraft:the_nether",
              "end": "minecraft:the_end", "rain": "qihai:yuji_fodu",
              "night": "sound_isolating_eraser:eternal_night"}
server = None
client = None
windows = []


def archive_world(name):
    destination = FIXTURES / (name + ".tar.gz")
    with tarfile.open(destination, "w:gz") as archive:
        archive.add(p.SERVER / "world", arcname="world")
    return destination


def new_world(previous):
    world = p.SERVER / "world"
    if world.exists():
        world.rename(p.OUT / ("world-" + previous))
    shutil.copytree(FIXTURES / "base-world", world)


def boot():
    global server
    server = p.Server()
    server.wait(r"Done \([\d.,]+s\)!", 0, 600)
    evidence = {"utc": p.utc(), "pid": server.process.pid,
                "alive": server.process.poll() is None, "cpuAffinity": os.environ.get("T2_SERVER_CPUS"),
                "ready": next(line.strip() for line in server.lines if "Done (" in line)}
    with (p.OUT / "lifecycles.jsonl").open("a") as stream:
        stream.write(json.dumps(evidence) + "\n")
    server.send("scoreboard objectives add t2_perf dummy")
    return server


def shutdown(label):
    global server, client
    if client:
        client.close()
        client = None
    if server:
        evidence = server.close()
        if not evidence["graceful"]:
            raise RuntimeError("Non-graceful server shutdown at " + label)
        for filename in ("server-console.log", "commands.jsonl"):
            (p.OUT / filename).rename(p.OUT / (label + "-" + filename))
        with (p.OUT / "lifecycles.jsonl").open("a") as stream:
            stream.write(json.dumps({"label": label, **evidence}) + "\n")
        server = None


def expand(value):
    return re.sub(r"\$\{workspaceFolder(?::[^}]+)?\}", str(p.ROOT), value)


def words(value):
    return [expand(str(x)) for x in value] if isinstance(value, list) else shlex.split(expand(value))


class Client:
    def __init__(self, label):
        global client
        client = self
        self.label = label
        self.directory = p.OUT / "client"
        self.directory.mkdir(exist_ok=True)
        (self.directory / "config").mkdir(exist_ok=True)
        (self.directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
        (self.directory / "options.txt").write_text(
            "lang:en_us\nrenderDistance:8\nsimulationDistance:6\nguiScale:2\nmaxFps:20\n"
            "enableVsync:false\npauseOnLostFocus:false\nonboardAccessibility:false\n"
            "skipMultiplayerWarning:true\nsoundCategory_master:0.0\nrenderClouds:false\n"
            "narrator:0\ntutorialStep:none\n")
        if os.environ.get("T2_FPS_PROBE"):
            options = self.directory / "options.txt"
            options.write_text(options.read_text().replace("maxFps:20", "maxFps:260"))
            shutil.copy2(options, p.OUT / (label + "-options.txt"))
        self.xvfb = subprocess.Popen(["Xvfb", "-displayfd", "1", "-screen", "0", "1280x720x24",
                                      "-nolisten", "tcp", "-noreset"],
                                     stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        self.display = ":" + self.xvfb.stdout.readline().strip()
        configs = json.loads((p.ROOT / ".vscode/launch.json").read_text())["configurations"]
        config = next(c for c in configs if re.search(r"(?:^|[\s:/])runClient(?:$|[\s(])", c["name"]))
        env = os.environ.copy()
        env.update({key: expand(value) for key, value in config.get("env", {}).items() if value is not None})
        env.update(DISPLAY=self.display, LIBGL_ALWAYS_SOFTWARE="1", ALSOFT_DRIVERS="null", LP_NUM_THREADS="1")
        if os.environ.get("T2_FPS_PROBE"):
            probe_classes = (p.OUT / "probe-mod-classes.txt").read_text().strip()
            env["MOD_CLASSES"] = env.get("MOD_CLASSES", "") + os.pathsep + probe_classes
        self.env = env
        arguments = words(config.get("args", ""))
        for key, value in {"--gameDir": str(self.directory), "--username": PLAYER,
                           "--width": "1280", "--height": "720",
                           "--quickPlayMultiplayer": f"127.0.0.1:{p.PORT}"}.items():
            while key in arguments:
                offset = arguments.index(key)
                del arguments[offset:offset + 2]
            arguments.extend([key, value])
        vm = [v for v in words(config.get("vmArgs", "")) if not v.startswith(("-Xmx", "-Xms"))]
        if os.environ.get("T2_FPS_PROBE"):
            vm.append("-Dt2.fps.out=" + str(p.OUT))
        cp = (p.OUT / "client-classpath.txt").read_text().strip()
        command = ["java", "-Xms256M", "-Xmx1536M", "-XX:ActiveProcessorCount=1", *vm,
                   "-cp", cp, config["mainClass"], *arguments]
        cpus = os.environ.get("T2_CLIENT_CPUS")
        if cpus:
            command = ["taskset", "--cpu-list", cpus, *command]
        self.log = (p.OUT / (label + "-client.log")).open("w")
        index = len(server.lines)
        self.process = subprocess.Popen(command, cwd=self.directory, env=env, stdout=self.log,
                                        stderr=subprocess.STDOUT, start_new_session=True)
        if os.environ.get("T2_FPS_PROBE"):
            deadline = time.monotonic() + 300
            while True:
                failure = p.OUT / "fps-failure.json"
                if failure.exists():
                    raise RuntimeError(failure.read_text())
                if time.monotonic() >= deadline:
                    state = p.OUT / "fps-client-state.json"
                    raise TimeoutError("Client join timeout: " + (state.read_text() if state.exists() else "no probe state"))
                try:
                    server.wait(PLAYER + r" joined the game", index, 2)
                    break
                except TimeoutError:
                    if self.process.poll() is not None:
                        raise RuntimeError("Client exited before joining")
        else:
            server.wait(PLAYER + r" joined the game", index, 300)
        server.send("op " + PLAYER)
        server.send("gamemode survival " + PLAYER)
        server.send(f"effect give {PLAYER} minecraft:resistance 1000000 4 true")
        server.send(f"effect give {PLAYER} minecraft:saturation 1000000 1 true")
        server.send(f"attribute {PLAYER} minecraft:generic.knockback_resistance base set 1")

    def input(self, *arguments):
        subprocess.run(["xdotool", *arguments], env=self.env, check=True, timeout=15)

    def screenshot(self):
        self.input("key", "F2")
        server.delay(2)

    def close(self):
        if hasattr(self, "process") and self.process.poll() is None:
            os.killpg(self.process.pid, signal.SIGTERM)
            try:
                self.process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(self.process.pid, signal.SIGKILL)
                self.process.wait(timeout=10)
        if hasattr(self, "xvfb"):
            self.xvfb.terminate()
            try:
                self.xvfb.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.xvfb.kill()
                self.xvfb.wait(timeout=10)
        if hasattr(self, "log"):
            self.log.close()


def join(label):
    global client
    client = Client(label)


def dimension(expected, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        mark = server.send(f"data get entity {PLAYER} Dimension")
        try:
            server.wait(re.escape(expected), mark, 2)
            return
        except TimeoutError:
            if client.process.poll() is not None:
                raise RuntimeError("Real client exited before dimension transition")
    raise TimeoutError("Player did not reach " + expected)


def count(score, filters, dimension_id):
    selector = "@e[distance=0.." + ("," + filters if filters else "") + "]"
    server.send(f"execute in {dimension_id} store result score {score} t2_perf if entity {selector}")
    mark = server.send(f"scoreboard players get {score} t2_perf")
    return int(server.wait(rf"{score} has (\d+) \[t2_perf\]", mark, 5).group(1))


def sample(label, expected_dimension=None):
    summary = {"scenario": label, "sourceSha": p.SOURCE_SHA, "status": "incomplete",
               "fps": None, "fpsReason": "A has no client; other scenes use capped software rendering, not GPU acceptance"}
    windows.append(summary)
    server.delay(p.WARMUP)
    if expected_dimension:
        dimension(expected_dimension, 10)
        server.send(f"data get entity {PLAYER} Pos")
        server.send(f"data get entity {PLAYER} Rotation")
        client.screenshot()
    server.send("list")
    server.send("spark health")
    server.send("spark profiler cancel")
    server.delay(2)
    index = server.send(f"spark profiler start --timeout {p.WINDOW}")
    server.wait(r"profiler is now running|profiling.*started", index, 20)
    start = time.monotonic()
    summary["startUtc"] = p.utc()
    rows = []
    columns = ["utc", "elapsedSeconds", "load1", *DIMENSIONS, "parents", "summons"]
    with (p.OUT / (label + "-entities.csv")).open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        for second in range(p.WINDOW):
            server.delay(max(0, start + second - time.monotonic()))
            elapsed = time.monotonic() - start
            if elapsed >= p.WINDOW:
                raise RuntimeError("Entity polling exceeded the sampling window")
            if client and client.process.poll() is not None:
                raise RuntimeError("Real client exited inside sampling window")
            if expected_dimension and second % 10 == 0:
                mark = server.send(f"data get entity {PLAYER} Health")
                health = float(server.wait(r"following entity data: ([\d.]+)f", mark, 5).group(1))
                if health <= 0:
                    raise RuntimeError("Player died inside sampling window")
            row = {"utc": p.utc(), "elapsedSeconds": round(elapsed, 3), "load1": os.getloadavg()[0]}
            for score, location in DIMENSIONS.items():
                row[score] = count(score, "", location)
            if label == "C":
                row["parents"] = count("parents", 'type=qihai:qiyu,tag=t2_parent', "minecraft:overworld")
                server.send("scoreboard players set summons t2_perf 0")
                server.send("execute in minecraft:overworld as @e[distance=0..,type=!minecraft:player] if data entity @s YujiSummoner run scoreboard players add summons t2_perf 1")
                mark = server.send("scoreboard players get summons t2_perf")
                row["summons"] = int(server.wait(r"summons has (\d+) \[t2_perf\]", mark, 5).group(1))
            writer.writerow(row)
            stream.flush()
            rows.append(row)
    server.delay(max(0, p.WINDOW - (time.monotonic() - start)))
    url = server.wait(r"https://spark\.lucko\.me/[A-Za-z0-9]+", index, 120).group(0)
    summary.update(status="collected_unvalidated", sparkUrl=url, endUtc=p.utc(), entityRows=len(rows),
                   observedWallSeconds=round(time.monotonic() - start, 3),
                   alive=server.process.poll() is None,
                   counts={key: {"min": min(row[key] for row in rows), "max": max(row[key] for row in rows)}
                           for key in DIMENSIONS})
    if label == "C" and not any(row["summons"] > 0 for row in rows):
        raise RuntimeError("Qiyu did not produce real summons; C is not accepted")
    if expected_dimension:
        dimension(expected_dimension, 10)
        if any(re.search(PLAYER + r" (?:died|was slain|fell|left the game|lost connection)", line)
               for line in server.lines[index:]):
            raise RuntimeError("Player death or disconnect invalidated the scene")
    server.send("spark health")
    server.send("spark tps")
    server.delay(3)
    p.download(url + "?raw=1", p.OUT / (label + "-spark-metadata.json"))
    p.download("https://spark-usercontent.lucko.me/" + url.rsplit("/", 1)[1],
               p.OUT / (label + ".sparkprofile"))
    summary["status"] = "sampled"
    p.save_json(p.OUT / (label + "-summary.json"), summary)
    print("T2_SAMPLE", json.dumps(summary), flush=True)


def main():
    global server, client
    try:
        affinity = sorted(os.sched_getaffinity(0))
        if len(affinity) < 4:
            raise RuntimeError("Four isolated runner CPUs required for server/client CPU separation")
        topology = subprocess.check_output(["lscpu", "-p=CPU,CORE,SOCKET"], text=True)
        cores = {}
        for line in topology.splitlines():
            if not line.startswith("#"):
                cpu, core, socket = map(int, line.split(","))
                if cpu in affinity:
                    cores.setdefault((socket, core), []).append(cpu)
        groups = list(cores.values())
        if len(groups) < 2:
            raise RuntimeError("At least two physical CPU cores are required")
        server_cpus, client_cpus = groups[0], [cpu for group in groups[1:] for cpu in group]
        os.environ["T2_SERVER_CPUS"] = ",".join(map(str, server_cpus))
        os.environ["T2_CLIENT_CPUS"] = ",".join(map(str, client_cpus))
        environment = p.prepare()
        environment.update(scope="A/B1/B2/C synthetic CI server baseline; no optimization or hardware-GPU FPS claim",
                           serverCpus=server_cpus, clientCpus=client_cpus, cpuTopology=topology,
                           clientRendering="llvmpipe, capped 20 FPS, 1280x720; not a GPU benchmark")
        p.save_json(p.OUT / "environment.json", environment)
        FIXTURES.mkdir(exist_ok=True)
        boot()
        shutdown("initial-world")
        shutil.copytree(p.SERVER / "world", FIXTURES / "base-world")
        archive_world("A-before")
        boot()
        sample("A")
        shutdown("A")
        archive_world("A-after")

        new_world("A")
        boot()
        join("B1-prepare")
        server.send(f"execute as {PLAYER} run yuji enter")
        dimension(DIMENSIONS["rain"], 300)
        server.send(f"execute as {PLAYER} run yuji status")
        server.send(f"execute at {PLAYER} run tp {PLAYER} ~ ~ ~ 180 8")
        shutdown("B1-prepare")
        archive_world("B1-before")
        boot()
        join("B1")
        sample("B1", DIMENSIONS["rain"])
        shutdown("B1")
        archive_world("B1-after")

        new_world("B1")
        night_config = p.SERVER / "world/serverconfig/eternal-night-server.toml"
        original = night_config.read_text()
        temporary = re.sub(r"(?m)^(\s*entryBase\s*=).*", r"\g<1> 1.0", original)
        temporary = re.sub(r"(?m)^(\s*entryCap\s*=).*", r"\g<1> 1.0", temporary)
        if temporary == original:
            raise RuntimeError("Night entry fixture configuration was not updated")
        night_config.write_text(temporary)
        boot()
        join("B2-prepare")
        for command in ["gamerule playersSleepingPercentage 101", "gamerule doDaylightCycle false",
                        "time set midnight", f"tp {PLAYER} 1.5 100 3.5 180 30",
                        "fill -8 99 -8 8 99 8 minecraft:stone",
                        "fill -8 100 -8 8 108 8 minecraft:air",
                        "setblock 1 100 1 minecraft:red_bed[facing=north,part=foot]",
                        "setblock 1 100 0 minecraft:red_bed[facing=north,part=head]"]:
            server.send(command)
        server.delay(5)
        client.input("click", "3")
        dimension(DIMENSIONS["night"], 300)
        server.delay(5)
        server.send(f"data get entity {PLAYER}")
        shutdown("B2-prepare")
        night_config.write_text(original)
        archive_world("B2-before")
        boot()
        join("B2")
        sample("B2", DIMENSIONS["night"])
        shutdown("B2")
        archive_world("B2-after")

        new_world("B2")
        boot()
        join("C-prepare")
        for command in ["gamerule doMobSpawning false", f"tp {PLAYER} 0.5 100 0.5 180 8",
                        "fill -24 99 -24 24 99 24 minecraft:stone",
                        "fill -24 100 -24 24 111 24 minecraft:air"]:
            server.send(command)
        for x in (-12, -4, 4, 12):
            for z in (-12, -4, 4, 12):
                server.send(f'summon qihai:qiyu {x} 100 {z} {{Tags:["t2_parent"],PersistenceRequired:1b}}')
        server.delay(30)
        shutdown("C-prepare")
        archive_world("C-before")
        boot()
        join("C")
        sample("C", DIMENSIONS["overworld"])
        shutdown("C")
        archive_world("C-after")
    finally:
        try:
            shutdown("cleanup")
        finally:
            p.OUT.mkdir(parents=True, exist_ok=True)
            p.save_json(p.OUT / "suite-summary.json", {"windows": windows,
                        "allFourSampled": len(windows) == 4 and all(w.get("status") == "sampled" for w in windows)})


if __name__ == "__main__":
    main()
