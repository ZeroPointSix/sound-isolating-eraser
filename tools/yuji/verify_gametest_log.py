"""Reject empty, partial, or failed Yuji GameTest runs."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
test_sources = root / "src/main/java/com/zeropointsix/eraser/gametest"
expected = sum(len(re.findall(r"@GameTest\(", (test_sources / name).read_text()))
               for name in ("YujiGameTests.java", "YujiDeathGameTests.java"))
log = Path(sys.argv[1]).read_text(errors="replace")
batches = re.findall(r"Running test batch 'yuji:\d+' \((\d+) tests\)", log)
completed = re.findall(r"All (\d+) required tests passed", log)
if not expected or sum(map(int, batches)) != expected or not completed or int(completed[-1]) < expected:
    raise SystemExit(f"FAIL: expected {expected} Yuji tests; observed batches={batches}, completed={completed}")
if re.search(r"\brequired tests failed\b|\bBUILD FAILED\b", log):
    raise SystemExit("FAIL: test log contains failures")
print(f"PASS: all {expected} Yuji tests were discovered and completed in a successful GameTest run")
