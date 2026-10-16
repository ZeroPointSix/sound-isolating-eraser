"""One bounded, loopback-only production Forge/spark experiment.

This harness is not shipped in the mod and does not change any game logic.
The checkout, downloaded dependencies, process lifecycle and raw measurements
are recorded separately from any claim about performance improvement.
"""

from __future__ import annotations

import csv
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import socket
import subprocess
import tarfile
import threading
import time


SOURCE_SHA = "bb325dc9ccf3d99565d8a1eedb7efee372fdcd45"
SPARK_URL = "https://cdn.modrinth.com/data/l6YH9Als/versions/4FXHDE9F/spark-1.10.53-forge.jar"
SPARK_SHA512 = (
    "15c6a3753dcba380624643a1e19c856240e8f6bed280f908e1be12c6154a86284ac"
    "86405c806273842975e0b7270af93ea9b1db5cf575a3853e54e1088cf7343"
)
ROOT = Path(os.environ.get("T2_SOURCE_DIR", ".")).resolve()
OUT = ROOT / "build/t2-perf"
SERVER = OUT / "server"
PORT = 25592
WINDOW = 300
WARMUP = 120
manifest: list[dict] = []


def utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def save_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def command_log(command: list[str], path: Path, cwd: Path, timeout: int) -> None:
    print("RUN", json.dumps(command), flush=True)
    with path.open("w", encoding="utf-8") as log:
        result = subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT,
                                timeout=timeout, check=False)
    if result.returncode:
        print(path.read_text(errors="replace")[-12000:], flush=True)
        raise RuntimeError(f"Command exited {result.returncode}: {command[0]}; see {path.name}")


def download(url: str, target: Path, expected: str | None = None,
             algorithm: str = "sha512") -> None:
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error",
                    "--retry", "2", "--connect-timeout", "15", "--max-time", "180",
                    "--output", str(target), url], check=True, timeout=560)
    data = target.read_bytes()
    if expected and hashlib.new(algorithm, data).hexdigest() != expected:
        raise RuntimeError(f"Published {algorithm} mismatch for {target.name}")
    manifest.append({"url": url, "file": target.name, "bytes": len(data),
                     "sha256": hashlib.sha256(data).hexdigest(),
                     "publishedHashVerified": {algorithm: expected} if expected else None})
    save_json(OUT / "downloads.json", manifest)


def prepare() -> dict:
    OUT.mkdir(parents=True, exist_ok=True)
    SERVER.mkdir(exist_ok=True)
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    if sha != SOURCE_SHA:
        raise RuntimeError(f"Wrong source checkout: {sha}")
    with socket.socket() as test:
        test.bind(("127.0.0.1", PORT))
    config = {line.split("=", 1)[0]: line.split("=", 1)[1]
              for line in (ROOT / "gradle.properties").read_text().splitlines()
              if "=" in line and not line.startswith("#")}
    version = config["minecraft_version"] + "-" + config["forge_version"]
    installer = SERVER / "forge-installer.jar"
    url = f"https://maven.minecraftforge.net/net/minecraftforge/forge/{version}/forge-{version}-installer.jar"
    checksum = subprocess.check_output(["curl", "--fail", "--silent", "--show-error",
                                       "--max-time", "30", url + ".sha1"], text=True).strip().split()[0]
    if not re.fullmatch(r"[0-9a-fA-F]{40}", checksum):
        raise RuntimeError("Invalid Forge installer checksum response")
    download(url, installer, checksum.lower(), "sha1")
    command_log(["java", "-Xmx1G", "-jar", str(installer), "--installServer"],
                OUT / "forge-install.log", SERVER, 600)
    mods = SERVER / "mods"
    mods.mkdir(exist_ok=True)
    mod = ROOT / "build/libs" / f"{config['mod_id']}-{config['mod_version']}.jar"
    if not mod.is_file():
        raise RuntimeError(f"Missing production mod: {mod.name}")
    shutil.copy2(mod, mods / mod.name)
    curios = config["curios_version"]
    gecko = config["geckolib_version"]
    download(f"https://maven.theillusivec4.top/top/theillusivec4/curios/curios-forge/{curios}/curios-forge-{curios}.jar",
             mods / f"curios-forge-{curios}.jar")
    download(f"https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-forge-1.20.1/{gecko}/geckolib-forge-1.20.1-{gecko}.jar",
             mods / f"geckolib-forge-1.20.1-{gecko}.jar")
    download(SPARK_URL, mods / "spark-1.10.53-forge.jar", SPARK_SHA512)
    (SERVER / "eula.txt").write_text("eula=true\n")
    (SERVER / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25592\nonline-mode=false\n"
        "enforce-secure-profile=false\nenable-rcon=false\nenable-query=false\n"
        "level-seed=20261016\nlevel-name=world\nlevel-type=minecraft:normal\n"
        "view-distance=8\nsimulation-distance=6\ndifficulty=normal\n"
        "max-players=2\nspawn-protection=0\nmax-tick-time=120000\n"
    )
    hardware = subprocess.check_output(["lscpu", "--json"], text=True)
    result = {"sourceSha": sha, "harnessSha": os.environ.get("T2_HARNESS_SHA"),
              "modVersion": config["mod_version"], "forge": version, "spark": "1.10.53-forge",
              "platform": platform.platform(), "cpuCount": os.cpu_count(),
              "hardware": json.loads(hardware), "startUtc": utc(),
              "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr,
              "modSha256": hashlib.sha256(mod.read_bytes()).hexdigest(),
              "windowSeconds": WINDOW, "warmupSeconds": WARMUP,
              "fps": None, "fpsReason": "Dedicated server, zero connected clients; not applicable",
              "scope": "A idle only; B1/B2/C and before-after comparison are not complete"}
    save_json(OUT / "environment.json", result)
    return result


class Server:
    def __init__(self) -> None:
        self.lines: list[str] = []
        self.log = (OUT / "server-console.log").open("w", encoding="utf-8")
        self.commands = (OUT / "commands.jsonl").open("w", encoding="utf-8")
        arguments = "libraries/net/minecraftforge/forge/1.20.1-47.2.0/unix_args.txt"
        self.process = subprocess.Popen(
            ["java", "-Xms512M", "-Xmx2G", "-XX:ActiveProcessorCount=2", "@" + arguments, "nogui"],
            cwd=SERVER, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, start_new_session=True, bufsize=1)
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()

    def read(self) -> None:
        for line in self.process.stdout:
            self.log.write(line)
            self.log.flush()
            self.lines.append(line)
            if "spark" in line.lower() or "Done (" in line or "ERROR" in line:
                print(line.rstrip(), flush=True)

    def send(self, text: str) -> int:
        if self.process.poll() is not None:
            raise RuntimeError(f"Server exited {self.process.returncode}")
        index = len(self.lines)
        self.commands.write(json.dumps({"utc": utc(), "monotonic": time.monotonic(),
                                        "command": text}) + "\n")
        self.commands.flush()
        self.process.stdin.write(text + "\n")
        self.process.stdin.flush()
        return index

    def wait(self, pattern: str, start: int, timeout: float) -> re.Match:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError(f"Server exited {self.process.returncode}; see server-console.log")
            found = re.search(pattern, "".join(self.lines[start:]), re.IGNORECASE)
            if found:
                return found
            time.sleep(.1)
        raise TimeoutError(f"No server response matching {pattern!r} within {timeout}s")

    def delay(self, seconds: float) -> None:
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError("Server exited during bounded wait")
            time.sleep(min(1, max(0, deadline - time.monotonic())))

    def close(self) -> dict:
        graceful = False
        if self.process.poll() is None:
            try:
                self.send("save-all flush")
                self.send("stop")
                graceful = self.process.wait(timeout=90) == 0
            except (subprocess.TimeoutExpired, BrokenPipeError):
                os.killpg(self.process.pid, signal.SIGTERM)
                try:
                    self.process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(self.process.pid, signal.SIGKILL)
                    self.process.wait(timeout=10)
        self.thread.join(timeout=5)
        self.log.close()
        self.commands.close()
        return {"utc": utc(), "pid": self.process.pid, "exitCode": self.process.returncode,
                "graceful": graceful, "aliveAfterStop": self.process.poll() is None}


def main() -> None:
    server = None
    summary = {"scenario": "A", "status": "incomplete", "sparkUrl": None}
    try:
        prepare()
        server = Server()
        server.wait(r"Done \([\d.,]+s\)!", 0, 600)
        with socket.create_connection(("127.0.0.1", PORT), timeout=5):
            pass
        save_json(OUT / "startup.json", {"utc": utc(), "pid": server.process.pid,
                  "alive": server.process.poll() is None, "listening": "127.0.0.1:25592",
                  "readyLog": next(line.strip() for line in server.lines if "Done (" in line)})
        server.send("scoreboard objectives add t2_perf dummy")
        server.send("spark profiler info")
        server.delay(WARMUP)
        server.send("list")
        server.send("spark health show")
        index = server.send(f"spark profiler start --timeout {WINDOW}")
        server.wait(r"profiler is now running|profiling.*started", index, 20)
        start = time.monotonic()
        summary["startUtc"] = utc()
        dimensions = {"overworld": "minecraft:overworld", "rain": "qihai:yuji_fodu",
                      "night": "sound_isolating_eraser:eternal_night"}
        with (OUT / "A-entities.csv").open("w", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=["utc", "elapsedSeconds", "load1", *dimensions])
            writer.writeheader()
            for second in range(WINDOW):
                server.delay(max(0, start + second - time.monotonic()))
                row = {"utc": utc(), "elapsedSeconds": round(time.monotonic() - start, 3),
                       "load1": os.getloadavg()[0]}
                for name, dimension in dimensions.items():
                    server.send(f"execute in {dimension} store result score {name} t2_perf if entity @e")
                    mark = server.send(f"scoreboard players get {name} t2_perf")
                    match = server.wait(rf"{name} has (\d+) \[t2_perf\]", mark, 5)
                    row[name] = int(match.group(1))
                writer.writerow(row)
                output.flush()
        server.delay(max(0, WINDOW - (time.monotonic() - start)))
        report = server.wait(r"https://spark\.lucko\.me/[A-Za-z0-9]+", index, 120).group(0)
        summary.update(status="sampled", sparkUrl=report, endUtc=utc(),
                       observedWallSeconds=round(time.monotonic() - start, 3),
                       processAlive=server.process.poll() is None, entityRows=WINDOW,
                       msptSource="spark profiler report; console health intervals kept separately",
                       fps=None, fpsReason="No client in idle dedicated-server scenario")
        server.send("spark health show")
        server.send("spark tps")
        server.send("list")
        server.delay(3)
        print("T2_IDLE_SAMPLE", json.dumps(summary), flush=True)
    except BaseException as error:
        summary["error"] = str(error)
        raise
    finally:
        if server:
            summary["shutdown"] = server.close()
        OUT.mkdir(parents=True, exist_ok=True)
        save_json(OUT / "A-summary.json", summary)
        world = SERVER / "world"
        if world.exists() and summary.get("shutdown", {}).get("graceful"):
            with tarfile.open(OUT / "A-world-after.tar.gz", "w:gz") as archive:
                archive.add(world, arcname="world")


if __name__ == "__main__":
    main()
