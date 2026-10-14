"""Require the 35 default flare GameTests to be discovered and completed."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
sources = [
    root / "src/main/java/com/zeropointsix/eraser/gametest/FlareGameTests.java",
    root / "src/main/java/com/zeropointsix/eraser/gametest/FlareRegressionGameTests.java",
    root / "src/main/java/com/zeropointsix/eraser/gametest/FlareIntegrationGameTests.java",
    root / "src/main/java/com/zeropointsix/eraser/gametest/FlareEdgeCaseGameTests.java",
]
expected = sum(len(re.findall(r"@GameTest\(", path.read_text())) for path in sources)
log = Path(sys.argv[1]).read_text(errors="replace")
batches = {}
for name, count in re.findall(
        r"Running test batch '(flare(?:_regression|_full_lighting|_full_airdrop|_edge_[a-z]+)?):\d+' \((\d+) tests\)",
        log):
    batches[name] = max(batches.get(name, 0), int(count))
completed = [int(n) for n in re.findall(r"All (\d+) required tests passed", log)]
discovered = sum(batches.values())
if expected != 35:
    raise SystemExit(f"FAIL: expected 35 flare GameTests in source, found {expected}")
if discovered != expected:
    raise SystemExit(f"FAIL: expected {expected} flare tests; batches={batches}")
if not completed or max(completed) < expected:
    raise SystemExit(f"FAIL: flare tests were not part of a successful run; completed={completed}")
if re.search(r"\brequired tests failed\b|\bBUILD FAILED\b|flare[a-z]+ failed!", log, re.I):
    raise SystemExit("FAIL: test log contains flare or overall failures")
print(f"PASS: all {expected} flare GameTests were discovered and completed in a successful GameTest run")
