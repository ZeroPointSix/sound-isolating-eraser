"""Require all 49 flare GameTests in one complete, successful server run."""
from pathlib import Path
import re
import sys

EXPECTED_BATCHES = {
    "flare": 15, "flare_regression": 10,
    "flare_full_lighting": 1, "flare_full_airdrop": 1,
    **{f"flare_edge_{name}": 1 for name in (
        "wall", "floor", "ceiling", "entity", "flight", "yellow", "slow", "reload", "lowceiling",
        "ai_vanilla", "ai_yuji")},
    **{f"flare_permission_{name}": 1 for name in ("build", "event", "allowed")},
    **{f"flare_acceptance_{name}": 1 for name in ("vanilla", "yuji", "shadow", "unload")},
    **{f"flare_green_{name}": 1 for name in ("repeat", "sparse", "collision", "protection")},
}


def verify_log(log):
    matches = list(re.finditer(r"Running test batch '(flare[a-z_]*):\d+' \((\d+) tests\)", log))
    batches = {m.group(1): int(m.group(2)) for m in matches}
    if batches != EXPECTED_BATCHES or len(matches) != len(batches):
        raise SystemExit(f"FAIL: expected each of the 49 flare tests once; batches={batches}")
    completed = list(re.finditer(r"All (\d+) required tests passed", log))
    if (len(completed) != 1 or int(completed[0].group(1)) < 49
            or completed[0].start() < matches[-1].end()):
        raise SystemExit("FAIL: expected one successful run after all flare batches")
    if re.search(r"\brequired tests failed\b|\bBUILD FAILED\b|flare[a-z]+ failed!", log, re.I):
        raise SystemExit("FAIL: test log contains flare or overall failures")


def main():
    root = Path(__file__).resolve().parents[2] / "src/main/java/com/zeropointsix/eraser/gametest"
    names = ["FlareGameTests", "FlareRegressionGameTests", "FlareIntegrationGameTests", "FlareEdgeCaseGameTests", "FlarePermissionGameTests", "FlareAcceptanceGameTests", "FlareGreenGameTests"]
    counts = [len(re.findall(r"@GameTest\(", (root / f"{name}.java").read_text())) for name in names]
    if sum(counts[:3]) != 27 or counts[3:] != [11, 3, 4, 4]:
        raise SystemExit(f"FAIL: expected original 27 plus 11 edge, 3 permission, 4 acceptance and 4 green GameTests in source, found {counts}")
    verify_log(Path(sys.argv[1]).read_text(errors="replace"))
    print("PASS: all 49 flare GameTests (27 original + 11 edge + 3 permission + 4 acceptance + 4 green) completed in one successful GameTest run")


if __name__ == "__main__":
    main()
