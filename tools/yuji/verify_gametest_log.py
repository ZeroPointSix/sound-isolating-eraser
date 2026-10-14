"""Reject empty, partial, or failed Yuji GameTest runs."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
log = Path(sys.argv[1]).read_text(errors="replace")
completed = re.findall(r"All (\d+) required tests passed", log)
expected = 0
for filename, batch in (("YujiGameTests.java", "yuji"), ("YujiHabitatGameTests.java", "yuji_habitat")):
    source = (root / "src/main/java/com/zeropointsix/eraser/gametest" / filename).read_text()
    count = len(re.findall(r"@GameTest\(", source))
    batches = re.findall(r"Running test batch '" + batch + r":\d+' \((\d+) tests\)", log)
    if not count or sum(map(int, batches)) != count:
        raise SystemExit(f"FAIL: expected {count} {batch} tests; observed batches={batches}")
    expected += count
if not completed or int(completed[-1]) < expected:
    raise SystemExit(f"FAIL: expected at least {expected} Yuji tests; completed={completed}")
if re.search(r"\brequired tests failed\b|\bBUILD FAILED\b", log):
    raise SystemExit("FAIL: test log contains failures")
print(f"PASS: all {expected} Yuji tests were discovered and completed in a successful GameTest run")
