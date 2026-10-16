"""Paired production-jar comparison, fixed world and observed stationary client.

Reuses the verified T2 server/client lifecycle. This auxiliary harness never ships
in either production mod and does not instrument or replace monster logic.
"""

import csv
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import time

import production_probe as p
import full_suite as s


BASE_SHA = "bb325dc9ccf3d99565d8a1eedb7efee372fdcd45"
CANDIDATE_SHA = "8c49ff6a1dd98eb97c45e4222b7918f8b0191d69"
CANDIDATE = Path(os.environ.get("T2_CANDIDATE_DIR", "candidate")).resolve()
STANDING = (0.5, 118.0, 0.5)
MOD_ID = "sound_isolating_eraser"
WINDOWS = (("baseline-1", False, 512), ("cap32", True, 32),
           ("default", True, 512), ("baseline-2", False, 512))
results = []


def fingerprint(directory):
    digest = hashlib.sha256()
    for path in sorted(directory.rglob("*")):
        if path.is_file():
            digest.update(path.relative_to(directory).as_posix().encode() + b"\0")
            digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def config_text(cap):
    return ("[spawnCaps]\n" + f"dimensionCap = {cap}\n" + "chunkCap = 64\n"
            "entitiesPerLoadedChunk = 2\nminimumScaledCap = 128\n")


def vector(raw):
    values = raw.strip("[]").split(",")
    return tuple(float(re.sub(r"[dDfF]$", "", value.strip())) for value in values)


def check_position(position):
    if len(position) != 3 or any(not math.isfinite(value) for value in position):
        raise RuntimeError("Invalid position response")
    if math.dist(position, STANDING) > .01:
        raise RuntimeError(f"Stationary player drifted: {position} != {STANDING}")


def check_metadata(data):
    meta = data["metadata"]
    duration = (meta["endTime"] - meta["startTime"]) / 1000
    if not 299 <= duration <= 305 or meta["numberOfTicks"] < 5900:
        raise RuntimeError("spark window/tick count does not satisfy the 300s gate")
    mspt = meta["platformStatistics"]["mspt"]["last5m"]
    if not all(math.isfinite(mspt[key]) and mspt[key] >= 0 for key in ("mean", "median", "percentile95", "max")):
        raise RuntimeError("spark last5m timing summary is missing/invalid")
    return {"durationSeconds": duration, "ticks": meta["numberOfTicks"], "msptLast5m": mspt,
            "tpsLast5m": meta["platformStatistics"]["tps"]["last5m"]}


def select_jar(candidate):
    source = CANDIDATE if candidate else p.ROOT
    version = "0.14.3" if candidate else "0.14.2"
    jar = source / "build/libs" / f"{MOD_ID}-{version}.jar"
    if not jar.is_file():
        raise RuntimeError(f"Missing exact production jar: {jar}")
    installed_base = p.SERVER / "mods" / f"{MOD_ID}-0.14.2.jar"
    if installed_base.exists():
        installed_base.unlink()
    shutil.copy2(jar, p.SERVER / "mods/mod-under-test.jar")
    return hashlib.sha256(jar.read_bytes()).hexdigest()


def prepare_fixture():
    s.boot()
    s.join("C-fixed-prepare")
    for command in ["gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set noon",
                    "fill -24 99 -24 24 99 24 minecraft:stone",
                    "fill -24 100 -24 24 111 24 minecraft:air",
                    "fill -24 112 -24 24 121 24 minecraft:air",
                    "fill -1 117 -1 1 120 1 minecraft:barrier",
                    "fill 0 118 0 0 119 0 minecraft:air",
                    f"tp {s.PLAYER} 0.5 118 0.5 180 8"]:
        s.server.send(command)
    for x in (-12, -4, 4, 12):
        for z in (-12, -4, 4, 12):
            s.server.send(f'summon qihai:qiyu {x} 100 {z} {{Tags:["t2_parent"],PersistenceRequired:1b,NoAI:1b}}')
    s.server.delay(3)
    if s.count("parents", "type=qihai:qiyu,tag=t2_parent", "minecraft:overworld") != 16:
        raise RuntimeError("Fixture does not have exactly 16 parents")
    s.shutdown("C-fixed-prepare")
    s.FIXTURES.mkdir(exist_ok=True)
    fixture = s.FIXTURES / "fixed-world"
    shutil.copytree(p.SERVER / "world", fixture)
    archive = s.archive_world("C-fixed-before")
    return {"treeSha256": fingerprint(fixture), "archiveSha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
            "playerPosition": STANDING, "parents": 16, "parentAiDuringPreparation": False,
            "parentAiDuringWarmupAndSampling": True, "barrierCage": "1x2 inner space at y118; prevents collision displacement"}


def sample(label, candidate, cap, jar_hash, fixture):
    server = s.server
    record = {"label": label, "sourceSha": CANDIDATE_SHA if candidate else BASE_SHA,
              "modSha256": jar_hash, "dimensionCap": cap if candidate else None,
              "fixtureTreeSha256": fixture["treeSha256"], "status": "incomplete",
              "fps": None, "fpsReason": "software-rendered observer capped at20; no measured GPU FPS"}
    results.append(record)
    server.send("execute in minecraft:overworld as @e[distance=0..,type=qihai:qiyu,tag=t2_parent] run data merge entity @s {NoAI:0b}")
    server.delay(p.WARMUP)
    s.dimension("minecraft:overworld", 10)
    server.send(f"tp {s.PLAYER} 0.5 118 0.5 180 8")
    s.client.screenshot()
    server.send("spark health")
    server.send("spark profiler cancel")
    server.delay(2)
    mark = server.send(f"spark profiler start --timeout {p.WINDOW}")
    server.wait(r"profiler is now running|profiling.*started", mark, 20)
    start = time.monotonic()
    rows = []
    with (p.OUT / (label + "-entities.csv")).open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["utc", "elapsedSeconds", "load1", "overworld", "parents", "summons", "x", "y", "z"])
        writer.writeheader()
        for second in range(p.WINDOW):
            server.delay(max(0, start + second - time.monotonic()))
            elapsed = time.monotonic() - start
            if elapsed >= p.WINDOW or s.client.process.poll() is not None:
                raise RuntimeError("Polling overran window or the real client exited")
            pos_mark = server.send(f"data get entity {s.PLAYER} Pos")
            position = vector(server.wait(r"following entity data: (\[[^\]]+\])", pos_mark, 5).group(1))
            check_position(position)
            if second % 10 == 0:
                health_mark = server.send(f"data get entity {s.PLAYER} Health")
                health = float(server.wait(r"following entity data: ([\d.]+)f", health_mark, 5).group(1))
                if health <= 0:
                    raise RuntimeError("Player died inside the sampling window")
            row = {"utc": p.utc(), "elapsedSeconds": round(elapsed, 3), "load1": os.getloadavg()[0],
                   "x": position[0], "y": position[1], "z": position[2],
                   "overworld": s.count("overworld", "", "minecraft:overworld"),
                   "parents": s.count("parents", "type=qihai:qiyu,tag=t2_parent", "minecraft:overworld")}
            server.send("scoreboard players set summons t2_perf 0")
            server.send("execute in minecraft:overworld as @e[distance=0..,type=!minecraft:player] if data entity @s YujiSummoner run scoreboard players add summons t2_perf 1")
            count_mark = server.send("scoreboard players get summons t2_perf")
            row["summons"] = int(server.wait(r"summons has (\d+) \[t2_perf\]", count_mark, 5).group(1))
            if row["parents"] != 16 or row["summons"] < 1:
                raise RuntimeError("Actual parent/summon workload vanished")
            if candidate and cap == 32 and row["parents"] + row["summons"] > 32:
                raise RuntimeError("Configured shared cap was exceeded by fixture monsters")
            writer.writerow(row); stream.flush(); rows.append(row)
    server.delay(max(0, p.WINDOW - (time.monotonic() - start)))
    url = server.wait(r"https://spark\.lucko\.me/[A-Za-z0-9]+", mark, 120).group(0)
    s.dimension("minecraft:overworld", 10)
    end_mark = server.send(f"data get entity {s.PLAYER} Pos")
    end_position = vector(server.wait(r"following entity data: (\[[^\]]+\])", end_mark, 5).group(1))
    check_position(end_position)
    if any(re.search(s.PLAYER + r" (?:died|was slain|fell|left the game|lost connection)", line) for line in server.lines[mark:]):
        raise RuntimeError("Player death/disconnect invalidates the scene")
    server.send("spark health"); server.send("spark tps"); server.delay(3)
    metadata_file = p.OUT / (label + "-spark-metadata.json")
    p.download(url + "?raw=1", metadata_file)
    p.download("https://spark-usercontent.lucko.me/" + url.rsplit("/", 1)[1], p.OUT / (label + ".sparkprofile"))
    record.update(check_metadata(json.loads(metadata_file.read_text())))
    record.update(status="sampled", sparkUrl=url, entityRows=len(rows), endPosition=end_position,
                  counts={key: {"min": min(row[key] for row in rows), "max": max(row[key] for row in rows)}
                          for key in ("overworld", "parents", "summons")})
    p.save_json(p.OUT / (label + "-summary.json"), record)
    print("T2_CAP_SAMPLE", json.dumps(record), flush=True)


def main():
    try:
        if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=CANDIDATE, text=True).strip() != CANDIDATE_SHA:
            raise RuntimeError("Candidate checkout is not the pinned commit")
        topology = subprocess.check_output(["lscpu", "-p=CPU,CORE,SOCKET"], text=True)
        affinity = os.sched_getaffinity(0); cores = {}
        for line in topology.splitlines():
            if not line.startswith("#"):
                cpu, core, socket = map(int, line.split(","))
                if cpu in affinity:
                    cores.setdefault((socket, core), []).append(cpu)
        groups = list(cores.values())
        if len(groups) < 2:
            raise RuntimeError("At least two physical cores required")
        os.environ["T2_SERVER_CPUS"] = ",".join(map(str, groups[0]))
        os.environ["T2_CLIENT_CPUS"] = ",".join(str(cpu) for group in groups[1:] for cpu in group)
        environment = p.prepare()
        environment.update(scope="Paired C production-jar policy/overhead comparison, not GPU FPS acceptance",
                           candidateSha=CANDIDATE_SHA, cpuTopology=topology, order=[row[0] for row in WINDOWS],
                           serverCpus=os.environ["T2_SERVER_CPUS"], clientCpus=os.environ["T2_CLIENT_CPUS"],
                           clientSourceSha=BASE_SHA, fpsReason="One software-rendered observer, no measured GPU FPS",
                           note="Exact world bytes restored before each window; live entity RNG is not replay-locked")
        p.save_json(p.OUT / "environment.json", environment)
        fixture = prepare_fixture()
        p.save_json(p.OUT / "fixture.json", fixture)
        for label, candidate, cap in WINDOWS:
            (p.SERVER / "world").rename(p.OUT / (label + "-previous-world"))
            shutil.copytree(s.FIXTURES / "fixed-world", p.SERVER / "world")
            if fingerprint(p.SERVER / "world") != fixture["treeSha256"]:
                raise RuntimeError("World restore differs from the saved fixture")
            config = p.SERVER / "world/serverconfig" / f"{MOD_ID}-spawn-caps-server.toml"
            config.write_text(config_text(cap))
            jar_hash = select_jar(candidate)
            s.archive_world(label + "-before")
            s.boot(); s.join(label)
            sample(label, candidate, cap, jar_hash, fixture)
            s.shutdown(label)
            s.archive_world(label + "-after")
    finally:
        try:
            s.shutdown("cleanup")
        finally:
            p.OUT.mkdir(parents=True, exist_ok=True)
            p.save_json(p.OUT / "comparison-summary.json", {"windows": results,
                        "allFourSampled": len(results) == 4 and all(r["status"] == "sampled" for r in results)})


if __name__ == "__main__":
    main()
