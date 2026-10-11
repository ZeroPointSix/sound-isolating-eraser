"""Reject Forge startup failures that return zero without running GameTests."""
from pathlib import Path
import re
import sys

log = Path(sys.argv[1] if len(sys.argv) > 1 else "run/logs/latest.log").read_text(errors="replace")
passed = re.search(r"All (\d+) required tests passed", log, re.IGNORECASE)
assert passed, "GameTest server did not report that all required tests passed"
assert "Running test batch 'neon:1'" in log, "Neon behavioral tests did not run"
assert "Running test batch 'neon-navigation:1'" in log, "Neon navigation test did not run"
assert " failed!" not in log, "At least one test reported a failure"
print(f"Verified {passed.group(1)} passing required GameTests, including both Neon batches")
