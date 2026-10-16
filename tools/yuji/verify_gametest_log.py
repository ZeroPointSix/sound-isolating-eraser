"""Reject empty, partial, or failed Yuji GameTest runs."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
log = Path(sys.argv[1]).read_text(errors="replace")
completed = re.findall(r"All (\d+) required tests passed", log)
expected_batches = {}
for filename in ("YujiGameTests.java", "YujiHabitatGameTests.java", "YujiBetrayalSpeciesGameTests.java"):
    source = (root / "src/main/java/com/zeropointsix/eraser/gametest" / filename).read_text()
    annotations = re.findall(r"@GameTest\(([^)]*)\)", source)
    if not annotations:
        raise SystemExit(f"FAIL: no tests in {filename}")
    for annotation in annotations:
        match = re.search(r'batch\s*=\s*"([^"]+)"', annotation)
        if not match:
            raise SystemExit(f"FAIL: missing explicit batch in {filename}")
        batch = match.group(1)
        expected_batches[batch] = expected_batches.get(batch, 0) + 1
for batch, count in expected_batches.items():
    batches = re.findall(r"Running test batch '" + batch + r":\d+' \((\d+) tests\)", log)
    if not count or sum(map(int, batches)) != count:
        raise SystemExit(f"FAIL: expected {count} {batch} tests; observed batches={batches}")
expected = sum(expected_batches.values())
if not completed or int(completed[-1]) < expected:
    raise SystemExit(f"FAIL: expected at least {expected} Yuji tests; completed={completed}")
if re.search(r"\brequired tests failed\b|\bBUILD FAILED\b", log):
    raise SystemExit("FAIL: test log contains failures")
print(f"PASS: all {expected} Yuji tests were discovered and completed in a successful GameTest run")
