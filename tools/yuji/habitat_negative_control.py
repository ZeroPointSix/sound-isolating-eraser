"""Prove real habitat GameTests reject removal of the production navigation goal."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time


ROOT = Path(__file__).resolve().parents[2]
MONSTER = Path("src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java")
REGISTRATION = "goalSelector.addGoal(1, new YujiHabitatGoal(this));"
REQUIRED_FAILURES = {
    "liangshewalksinsideandstaysunderroof",
    "xieyinwalksintobodywidthcorridor",
}
HABITAT_CASES = REQUIRED_FAILURES | {
    "liangsherejectssealedroomandusesreachableroom",
    "xieyinrejectstoolowcorridorandchoosestallone",
    "liangsheleavesdestroyedshelterandfindsanother",
}
# -PyujiOnly also includes Flare's shared namespace after syncing main.
EXPECTED_BATCHES = {"yuji": 61, "yuji_city": 22, "yuji_city_iter": 1, "yuji_habitat": 5,
                    "yuji_betrayal_species": 5, "flare": 15, "flare_regression": 10,
                    "flare_edge_ceiling": 1, "flare_permission_allowed": 1, "flare_edge_wall": 1,
                    "flare_edge_slow": 1, "flare_full_lighting": 1, "flare_edge_floor": 1,
                    "flare_acceptance_shadow": 1, "flare_edge_lowceiling": 1, "flare_acceptance_vanilla": 1,
                    "flare_permission_event": 1, "flare_edge_entity": 1, "flare_full_airdrop": 1,
                    "flare_edge_ai_vanilla": 1, "flare_edge_ai_yuji": 1, "flare_edge_flight": 1,
                    "flare_acceptance_unload": 1, "flare_edge_reload": 1, "flare_permission_build": 1,
                    "flare_edge_yellow": 1, "flare_acceptance_yuji": 1}
BEHAVIOR_DIAGNOSTICS = {
    "liangshewalksinsideandstaysunderroof": (
        "whole body must be inside room", "body must be below the roof", "must reach the room interior",
        "Liangshe must walk into the room", "not a stationary shelter predicate", "stays indoors after arriving",
    ),
    "xieyinwalksintobodywidthcorridor": (
        "Xieyin must navigate into narrow corridor", "real displacement into shelter",
        "whole body must stay on corridor floor between walls", "stays in corridor",
    ),
    "liangsherejectssealedroomandusesreachableroom": (
        "must find the farther reachable room, not phase into sealed room",
    ),
    "xieyinrejectstoolowcorridorandchoosestallone": (
        "must reject head collision and use tall corridor", "whole body must stay on corridor floor between walls",
    ),
    "liangsheleavesdestroyedshelterandfindsanother": (
        "whole body must be inside room", "body must be below the roof", "must reach the room interior",
        "roof removal invalidates cached shelter and triggers new navigation",
    ),
}


def validate_negative_log(log, exit_code, required_failures=REQUIRED_FAILURES,
                          allowed_failures=HABITAT_CASES, behavior_diagnostics=BEHAVIOR_DIAGNOSTICS):
    """A build failure alone is not evidence: both intended behavioral tests must fail."""
    if exit_code != 1:
        raise AssertionError(f"expected Gradle exit 1 for rejected mutant, got {exit_code}")
    batches = re.findall(r"Running test batch '([^']+)' \((\d+) tests\)", log)
    discovered = {}
    for name, count in batches:
        batch = name.split(":", 1)[0]
        discovered[batch] = discovered.get(batch, 0) + int(count)
    if discovered != EXPECTED_BATCHES:
        raise AssertionError(f"all {sum(EXPECTED_BATCHES.values())} current Yuji tests must run: {discovered}")
    complete = re.findall(r"(\d+) GAME TESTS COMPLETE", log)
    if not complete or int(complete[-1]) != sum(int(n) for _, n in batches):
        raise AssertionError("missing or partial GameTest completion summary")
    counts = re.findall(r"(\d+) required tests failed", log)
    failures = set(re.findall(r"\]:\s+-\s+([a-z0-9_.]+)\s*$", log, re.MULTILINE))
    failures = {name.rsplit(".", 1)[-1] for name in failures}
    if not counts or int(counts[-1]) != len(failures):
        raise AssertionError("failed-test count and named failures must agree")
    if not required_failures <= failures:
        raise AssertionError(f"mutant escaped a required behavior assertion: {sorted(failures)}")
    if not failures <= allowed_failures:
        raise AssertionError(f"unrelated failure cannot count as negative-control proof: {sorted(failures)}")
    diagnostics = {}
    for name, reason in re.findall(r"\[minecraft/LogTestReporter\]:\s+([a-z0-9_.]+) failed! ([^\r\n]+)", log):
        diagnostics.setdefault(name.rsplit(".", 1)[-1], []).append(reason)
    if set(diagnostics) != failures:
        raise AssertionError("every named failure must have its original GameTest diagnostic")
    for name, reasons in diagnostics.items():
        if len(reasons) != 1 or not reasons[0].startswith(behavior_diagnostics[name]):
            raise AssertionError(f"{name} failed for an unexpected reason: {reasons}")
    if "BUILD FAILED" not in log or re.search(r"All \d+ required tests passed", log):
        raise AssertionError("expected an unambiguously rejected GameTest run")
    return {"completed": int(complete[-1]), "expected_failures": sorted(failures),
            "behavior_diagnostics": diagnostics, "mutant_exit": exit_code}


def stop_process_group(process):
    """Stop the wrapper and its single-use daemon before removing their worktree."""
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        pass
    finally:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="use the existing Gradle dependency cache")
    args = parser.parse_args()
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    original_tree_source = (ROOT / MONSTER).read_bytes()
    committed_source = subprocess.check_output(["git", "show", f"{revision}:{MONSTER.as_posix()}"], cwd=ROOT)
    if original_tree_source != committed_source:
        raise AssertionError("refuse to test a dirty production source against a different commit")
    results = ROOT / "build" / f"yuji-habitat-negative-{time.time_ns()}"
    results.mkdir(parents=True)
    worktree = results / "worktree"
    report = {"source_sha": revision, "mutation": "omit exactly the YujiHabitatGoal registration", "status": "running"}
    subprocess.run(["git", "worktree", "add", "--detach", str(worktree), revision], cwd=ROOT, check=True)
    target = worktree / MONSTER
    original = target.read_bytes()
    try:
        source = original.decode("utf-8")
        if source.count(REGISTRATION) != 1:
            raise AssertionError("production goal registration changed; do not guess a replacement")
        target.write_text(source.replace(REGISTRATION, "// Negative-control mutant: habitat goal registration omitted."), encoding="utf-8")
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
        report.update(validate_negative_log((results / "negative.log").read_text(errors="replace"), process.returncode))
        report["status"] = "expected_behavior_failures_observed"
    except BaseException as error:
        report["status"] = "failed"
        report["error"] = str(error)
        raise
    finally:
        target.write_bytes(original)
        unchanged = (ROOT / MONSTER).read_bytes() == original_tree_source
        report["primary_source_unchanged"] = unchanged
        report["primary_source_sha256"] = hashlib.sha256(original_tree_source).hexdigest()
        (results / "result.json").write_text(json.dumps(report, indent=2) + "\n")
        subprocess.run(["git", "worktree", "remove", str(worktree)], cwd=ROOT, check=True)
        if not unchanged:
            raise AssertionError("primary production source changed during the isolated control")
    print("PASS: removing the production habitat goal is rejected by both real movement tests")
    print(json.dumps(report, indent=2))
    print(f"Evidence: {results}")


if __name__ == "__main__":
    main()
