"""Verify saved flare cleanup across two independently launched Forge JVMs."""
from pathlib import Path
import os
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "build" / ("flare-restart-" + str(time.time_ns()))
RESULTS.mkdir(parents=True)
print(f"FLARE_RESTART_RESULTS={RESULTS}", flush=True)
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
    if phase == "reload" and "FLARE_RESTART_CLEANUP_PASSED" not in text:
        raise AssertionError("Missing real restarted-server cleanup evidence")
print("FLARE_TWO_PROCESS_RESTART_PASSED", flush=True)
