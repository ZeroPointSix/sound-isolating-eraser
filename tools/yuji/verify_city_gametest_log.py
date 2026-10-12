"""Require discovery and completion, even when Forge exits zero after registry errors."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
source = (root / "src/main/java/com/zeropointsix/eraser/gametest/RainCityGameTests.java").read_text()
expected = len(re.findall(r"@GameTest\(", source))
log = Path(sys.argv[1]).read_text(errors="replace")
batches = re.findall(r"Running test batch 'yuji_city:\d+' \((\d+) tests\)", log)
completed = re.findall(r"All (\d+) required tests passed", log)
if not expected or sum(map(int, batches)) != expected or not completed or int(completed[-1]) < expected:
    raise SystemExit(f"FAIL: expected {expected} city tests; batches={batches}, completed={completed}")
if re.search(r"required tests failed|BUILD FAILED|Failed to load datapacks|Registry loading errors", log):
    raise SystemExit("FAIL: city test log contains failures")
print(f"RAIN_CITY_GAMETEST_PASS discovered={expected} completed={completed[-1]}")
