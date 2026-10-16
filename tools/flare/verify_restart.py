"""Verify saved flare cleanup across two independently launched Forge JVMs."""
from pathlib import Path
import os
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "build" / ("flare-restart-" + str(time.time_ns()))
RESULTS.mkdir(parents=True)
print(f"FLARE_RESTART_RESULTS={RESULTS}", flush=True)
seeded = None
for phase in ("seed", "reload"):
    log = RESULTS / (phase + ".log")
    command = ["bash", "./gradlew", "runGameTestServer", "--no-daemon", "--max-workers=2",
               "-Dorg.gradle.jvmargs=-Xmx2G", f"-PflareRestartPhase={phase}",
               f"-PflareRestartRunDir={RESULTS / 'world-run'}"]
    if os.environ.get("FLARE_QA_OFFLINE") == "1":
        command.append("--offline")
    with log.open("w") as output:
        result = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=900)
    text = log.read_text(errors="replace")
    for line in text.splitlines():
        if "FLARE_RESTART_" in line or "required tests" in line or "BUILD " in line:
            print(line, flush=True)
    if result.returncode or "All 1 required tests passed" not in text:
        print("\n".join(text.splitlines()[-50:]), flush=True)
        raise AssertionError(f"{phase} did not complete successfully: {log}")
    record = re.search(r"FLARE_RESTART_(?:SEEDED|LOADED) sources=(\d+) deadline=(\d+)", text)
    if record is None:
        raise AssertionError(f"Missing saved record evidence in {phase}")
    if phase == "seed":
        seeded = record.groups()
    elif record.groups() != seeded:
        raise AssertionError(f"Restart changed source count or absolute expiry: {seeded} -> {record.groups()}")
    if phase == "reload":
        for marker in ("FLARE_RESTART_PRE_EXPIRY_PASSED", "FLARE_RESTART_CLEANUP_PASSED"):
            if marker not in text:
                raise AssertionError(f"Missing real restarted-server evidence: {marker}")
print("FLARE_TWO_PROCESS_RESTART_PASSED", flush=True)
