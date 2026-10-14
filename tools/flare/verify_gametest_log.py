"""Require all 36 flare GameTests in one complete, successful server run."""
from pathlib import Path
import re
import sys

EXPECTED_BATCHES = {
    "flare": 15, "flare_regression": 10,
    "flare_full_lighting": 1, "flare_full_airdrop": 1,
    **{f"flare_edge_{name}": 1 for name in (
        "wall", "floor", "ceiling", "entity", "flight", "yellow", "slow", "reload", "lowceiling")},
}


def verify_log(log):
    matches = list(re.finditer(r"Running test batch '(flare[a-z_]*):\d+' \((\d+) tests\)", log))
    batches = {m.group(1): int(m.group(2)) for m in matches}
    if batches != EXPECTED_BATCHES or len(matches) != len(batches):
        raise SystemExit(f"FAIL: expected each of the 36 flare tests once; batches={batches}")
    completed = list(re.finditer(r"All (\d+) required tests passed", log))
    if (len(completed) != 1 or int(completed[0].group(1)) < 36
            or completed[0].start() < matches[-1].end()):
        raise SystemExit("FAIL: expected one successful run after all flare batches")
    if re.search(r"\brequired tests failed\b|\bBUILD FAILED\b|flare[a-z]+ failed!", log, re.I):
        raise SystemExit("FAIL: test log contains flare or overall failures")


def main():
    root = Path(__file__).resolve().parents[2] / "src/main/java/com/zeropointsix/eraser/gametest"
    names = ["FlareGameTests", "FlareRegressionGameTests", "FlareIntegrationGameTests", "FlareEdgeCaseGameTests"]
    counts = [len(re.findall(r"@GameTest\(", (root / f"{name}.java").read_text())) for name in names]
    if sum(counts[:3]) != 27 or counts[3] != 9:
        raise SystemExit(f"FAIL: expected original 27 plus 9 edge GameTests in source, found {counts}")
    verify_log(Path(sys.argv[1]).read_text(errors="replace"))
    print("PASS: all 36 flare GameTests (27 original + 9 edge) completed in one successful GameTest run")


if __name__ == "__main__":
    main()
