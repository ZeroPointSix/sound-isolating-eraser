"""Reject precise Goat, delayed-summon and full-wall production mutations."""
import argparse
import json
from pathlib import Path
import subprocess
import time

from habitat_negative_control import stop_process_group, validate_negative_log


ROOT = Path(__file__).resolve().parents[2]
BASE = Path("src/main/java/com/zeropointsix/eraser/yuji")
MUTATIONS = {
    BASE / "YujiMonster.java": [
        ("if (tick == 18 && canSummon()) summonAllies();", "if (tick == 18) summonAllies();"),
    ],
    BASE / "YujiAfflictions.java": [
        ("&& (victim == null || candidate == victim)", "&& (victim == null || candidate != victim)"),
        ("state.isCollisionShapeFullBlock(level, pos) && above.isCollisionShapeFullBlock(level, pos.above())",
         "!state.getCollisionShape(level, pos).isEmpty() && !above.getCollisionShape(level, pos.above()).isEmpty()"),
    ],
}
DIAGNOSTICS = {
    "yujibetrayalgoatusesnativeramnotmelee": ("native preparation must select the victim, not the nearer cow or witness",),
    "betrayedgoatcancelledpreparationrestorescooldown": ("native preparation must select the victim, not the nearer cow or witness",),
    "betrayedgoatcancelledreadychargerestorescooldown": ("native preparation must select the victim, not the nearer cow or witness",),
    "betrayedgoattimedoutpreparationcleansonlyownedtargets": ("native preparation must select the victim, not the nearer cow or witness",),
    "betrayedgoatlegacynbtrestoreswithoutrecruitment": ("native preparation must select the victim, not the nearer cow or witness",),
    "yujiqiyurechecksaudiencewhencastlands": ("lost audience cancels delayed summon",),
    "yujicrushrejectsnonfullcollisionshapes": ("iron bars are not solid squeeze walls",),
}


def validate(log, code):
    return validate_negative_log(log, code, set(DIAGNOSTICS), set(DIAGNOSTICS), DIAGNOSTICS)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    originals = {path: subprocess.check_output(["git", "show", f"{revision}:{path.as_posix()}"], cwd=ROOT)
                 for path in MUTATIONS}
    for path, original in originals.items():
        if (ROOT / path).read_bytes() != original:
            raise AssertionError(f"require clean committed source: {path}")
    results = ROOT / "build" / f"yuji-mechanism-negative-{time.time_ns()}"
    results.mkdir(parents=True)
    worktree = results / "worktree"
    report = {"source_sha": revision, "status": "running", "mutations": [str(p) for p in MUTATIONS]}
    subprocess.run(["git", "worktree", "add", "--detach", str(worktree), revision], cwd=ROOT, check=True)
    try:
        for path, replacements in MUTATIONS.items():
            source = originals[path].decode()
            for before, after in replacements:
                if source.count(before) != 1:
                    raise AssertionError(f"require exactly one recognized mutation site: {path}: {before}")
                source = source.replace(before, after)
            (worktree / path).write_text(source)
        command = ["bash", "./gradlew", "-PyujiOnly", "runGameTestServer", "--no-daemon", "--max-workers=1", "--console=plain"]
        if args.offline:
            command.append("--offline")
        report["command"] = command
        with (results / "negative.log").open("w") as output:
            process = subprocess.Popen(command, cwd=worktree, stdout=output, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            try:
                process.wait(timeout=1200)
            finally:
                stop_process_group(process)
                report["actual_exit"] = process.returncode
        report.update(validate((results / "negative.log").read_text(errors="replace"), process.returncode))
        report["status"] = "expected_behavior_failures_observed"
    except BaseException as error:
        report.update(status="failed", error=str(error))
        raise
    finally:
        for path, original in originals.items():
            (worktree / path).write_bytes(original)
        report["primary_source_unchanged"] = all((ROOT / p).read_bytes() == v for p, v in originals.items())
        (results / "result.json").write_text(json.dumps(report, indent=2) + "\n")
        subprocess.run(["git", "worktree", "remove", str(worktree)], cwd=ROOT, check=True)
        if not report["primary_source_unchanged"]:
            raise AssertionError("primary production source changed during isolated control")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
