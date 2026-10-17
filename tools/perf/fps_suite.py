"""Restore the existing PR1 snapshots and record four real client FPS windows."""

import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import time
import zipfile

import full_suite as suite
import production_probe as p
from fps_stats import summarize


ARTIFACT_SHA256 = "e0a5505f00f9fd0aeccf85dedf7c4c9990a0a201c2f33bf14bb6755ad718de88"
SCENES = [("A1", "A", "minecraft:overworld"), ("B1", "B1", "qihai:yuji_fodu"),
          ("B2", "B2", "sound_isolating_eraser:eternal_night"), ("C", "C", "minecraft:overworld")]


def read_when_ready(path, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        failure = p.OUT / "fps-failure.json"
        if failure.is_file():
            raise RuntimeError(failure.read_text())
        if suite.client.process.poll() is not None:
            raise RuntimeError("Client stopped before FPS window completed")
        if path.is_file():
            try:
                return json.loads(path.read_text())
            except json.JSONDecodeError:
                pass
        suite.server.delay(.1)
    raise TimeoutError("No complete evidence file " + path.name)


def restore(archive, label, source_label):
    world = p.SERVER / "world"
    if world.exists():
        world.rename(p.OUT / ("previous-world-" + label))
    name = next(name for name in archive.namelist() if name.endswith("/" + source_label + "-before.tar.gz"))
    destination = suite.FIXTURES / (label + "-reused-before.tar.gz")
    destination.write_bytes(archive.read(name))
    with tarfile.open(destination) as fixture:
        fixture.extractall(p.SERVER, filter="data")
    return {"sourceArtifactId": 12077285827, "sourceMember": name,
            "snapshotSha256": hashlib.sha256(destination.read_bytes()).hexdigest()}


def measure(label, dimension):
    suite.join(label)
    suite.dimension(dimension)
    suite.server.delay(p.WARMUP)
    suite.client.screenshot()
    suite.server.delay(5)
    for field in ("Dimension", "Pos", "Rotation"):
        suite.server.send(f"data get entity {suite.PLAYER} {field}")
    p.save_json(p.OUT / "fps-request.json", {"label": label, "dimension": dimension, "seconds": p.WINDOW})
    started = read_when_ready(p.OUT / (label + "-fps-started.json"), 45)
    print("T2_FPS_STARTED", json.dumps(started), flush=True)
    began = time.monotonic()
    complete_path = p.OUT / (label + "-fps-complete.json")
    rows = []
    with (p.OUT / (label + "-fps-entities.csv")).open("w", newline="") as stream:
        fields = ["utc", "elapsedSeconds", "load1", *suite.DIMENSIONS, "parents", "ownedChildren"]
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        second = 0
        while not complete_path.exists():
            if time.monotonic() - began > p.WINDOW + 30:
                raise TimeoutError("FPS measurement exceeded deadline")
            read_failure = p.OUT / "fps-failure.json"
            if read_failure.exists():
                raise RuntimeError(read_failure.read_text())
            if suite.client.process.poll() is not None:
                raise RuntimeError("Client exited during FPS measurement")
            suite.server.delay(max(0, began + second - time.monotonic()))
            row = {"utc": p.utc(), "elapsedSeconds": time.monotonic() - began, "load1": os.getloadavg()[0]}
            for name, dimension_id in suite.DIMENSIONS.items():
                row[name] = suite.count(name, "", dimension_id)
            row["parents"] = suite.count("parents", "type=qihai:qiyu,tag=t2_parent", dimension)
            suite.server.send("scoreboard players set ownedChildren t2_perf 0")
            suite.server.send(f"execute in {dimension} as @e[distance=0..] if data entity @s YujiSummoner run scoreboard players add ownedChildren t2_perf 1")
            mark = suite.server.send("scoreboard players get ownedChildren t2_perf")
            row["ownedChildren"] = int(suite.server.wait(r"ownedChildren has (\d+) \[t2_perf\]", mark, 5).group(1))
            writer.writerow(row)
            stream.flush()
            rows.append(row)
            second += 1
    complete = read_when_ready(complete_path, 10)
    result = summarize(p.OUT / (label + "-frames.csv"))
    if result["frames"] != complete["frames"] or complete["validWorldFrames"] != result["frames"]:
        raise RuntimeError("Client evidence frame count mismatch")
    totals = [sum(row[name] for name in suite.DIMENSIONS) for row in rows]
    result.update(label=label, metadata=complete, entityRows=len(rows),
                  entityRange=[min(totals), max(totals)],
                  dimensionEntityRanges={name: [min(row[name] for row in rows), max(row[name] for row in rows)]
                                         for name in suite.DIMENSIONS},
                  parentsRange=[min(row["parents"] for row in rows), max(row["parents"] for row in rows)],
                  ownedChildrenRange=[min(row["ownedChildren"] for row in rows), max(row["ownedChildren"] for row in rows)])
    if label == "C" and min(row["ownedChildren"] for row in rows) == 0:
        raise RuntimeError("Summon window did not continuously contain real owned summons")
    suite.client.screenshot()
    suite.server.send("spark health")
    suite.server.send("spark tps")
    suite.server.delay(3)
    p.save_json(p.OUT / (label + "-fps-summary.json"), result)
    (p.OUT / "fps-request.json").unlink()
    print("T2_FPS_RESULT", json.dumps(result), flush=True)
    return result


def main():
    summary = {"status": "incomplete", "windows": [], "scope": "software-rendered diagnostic client FPS, no hardware GPU or optimization claim"}
    try:
        topology = subprocess.check_output(["lscpu", "--parse=CPU,CORE,SOCKET"], text=True)
        groups = {}
        for row in topology.splitlines():
            if row and not row.startswith("#"):
                cpu, core, socket = map(int, row.split(","))
                groups.setdefault((socket, core), []).append(cpu)
        cores = list(groups.values())
        if len(cores) < 2 or (os.cpu_count() or 0) < 4:
            raise RuntimeError("Need separate server/client physical cores")
        os.environ["T2_SERVER_CPUS"] = ",".join(map(str, cores[0]))
        os.environ["T2_CLIENT_CPUS"] = ",".join(map(str, sum(cores[1:], [])))
        os.environ["T2_FPS_PROBE"] = "1"
        environment = p.prepare()
        environment.update(cpuTopology=topology, serverCpus=cores[0], clientCpus=sum(cores[1:], []),
                           scope=summary["scope"], clientFpsCap=260, clientVsync=False,
                           fpsReason="Recorded by separate read-only client probe; see per-scene FPS summaries")
        p.save_json(p.OUT / "environment.json", environment)
        suite.FIXTURES.mkdir(exist_ok=True)
        original = Path(os.environ["T2_SNAPSHOT_ZIP"])
        digest = hashlib.sha256(original.read_bytes()).hexdigest()
        if digest != ARTIFACT_SHA256:
            raise RuntimeError("Reused artifact hash mismatch")
        summary["sourceArtifactSha256"] = digest
        with zipfile.ZipFile(original) as archive:
            for label, source_label, dimension in SCENES:
                snapshot = restore(archive, label, source_label)
                suite.boot()
                # Retain console evidence without rendering per-entity command feedback in chat.
                suite.server.send("gamerule sendCommandFeedback false")
                measured = measure(label, dimension)
                suite.shutdown(label)
                measured["snapshot"] = snapshot
                summary["windows"].append(measured)
                p.save_json(p.OUT / "fps-suite-summary.json", summary)
        summary["status"] = "complete"
    except BaseException as error:
        summary["error"] = str(error)
        raise
    finally:
        suite.shutdown("cleanup")
        p.OUT.mkdir(parents=True, exist_ok=True)
        p.save_json(p.OUT / "fps-suite-summary.json", summary)


if __name__ == "__main__":
    main()
