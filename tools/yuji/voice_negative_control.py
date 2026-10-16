"""Prove real TCP body discovery rejects removal of the production streak reset."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

from habitat_negative_control import stop_process_group


ROOT = Path(__file__).resolve().parents[2]
MONSTER = Path("src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java")
RESET = '''        if (voiceRecipient != null) {
            Player finder = level().getPlayerByUUID(voiceRecipient);
            if (finder instanceof ServerPlayer seen && vulnerable(seen)
                    && hasLineOfSight(seen) && lookingAt(seen, getEyePosition(), 25)) missedVoices = 0;
        }
'''
FAILURE = "java.lang.AssertionError: seeing the real body between sounds must reset consecutive misses"


def validate_failure(results, exit_code):
    failures = sorted(results.glob("*.failed"))
    if exit_code != 1 or [p.name for p in failures] != ["isolation-server.failed"]:
        raise AssertionError(f"expected only the body-discovery assertion, exit={exit_code}, failures={failures}")
    if failures[0].read_text().strip() != FAILURE:
        raise AssertionError(f"unrelated failure is not proof: {failures[0].read_text()}")
    for name in ("server-voice-0", "client-voice-0-YujiTester", "client-voice-0-YujiWitness",
                 "client-voice-found-0-YujiTester"):
        if not (results / (name + ".pass")).is_file():
            raise AssertionError(f"real sound isolation and client look input must precede rejection: {name}")


def run(command, cwd, log, env=None, timeout=1800):
    with log.open("w") as output:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            process.wait(timeout=timeout)
        finally:
            stop_process_group(process)
    return process.returncode


def main():
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    original = subprocess.check_output(["git", "show", f"{revision}:{MONSTER.as_posix()}"], cwd=ROOT)
    if (ROOT / MONSTER).read_bytes() != original or original.decode().count(RESET) != 1:
        raise AssertionError("require clean, recognized production source before creating a negative control")
    results = ROOT / "build" / f"yuji-voice-negative-{time.time_ns()}"
    results.mkdir(parents=True)
    worktree = results / "worktree"
    report = {"source_sha": revision, "mutation": "remove only between-sound body-discovery reset", "status": "running"}
    subprocess.run(["git", "worktree", "add", "--detach", str(worktree), revision], cwd=ROOT, check=True)
    try:
        (worktree / MONSTER).write_text(original.decode().replace(RESET, ""))
        prepare = ["bash", "./gradlew", "-PyujiE2E", "writeYujiTestClasspath", "--no-daemon", "--max-workers=1", "--console=plain"]
        report["prepare_command"] = prepare
        report["prepare_exit"] = run(prepare, worktree, results / "prepare.log", timeout=900)
        if report["prepare_exit"] != 0:
            raise AssertionError("negative-control compilation failed; this is not behavioral evidence")
        command = ["python3", "tools/yuji/e2e.py"]
        env = dict(os.environ, YUJI_QA_ISOLATION="1", YUJI_QA_CAMERA_TURN="false")
        report["client_command"] = "YUJI_QA_ISOLATION=1 YUJI_QA_CAMERA_TURN=false " + " ".join(command)
        report["client_exit"] = run(command, worktree, results / "negative.log", env, timeout=1500)
        client_results = sorted((worktree / "build").glob("yuji-isolation-*"))
        if len(client_results) != 1:
            raise AssertionError("expected one fresh, real-client result directory")
        validate_failure(client_results[0], report["client_exit"])
        report["status"] = "expected_body_discovery_failure_observed"
    except BaseException as error:
        report.update(status="failed", error=str(error))
        raise
    finally:
        (worktree / MONSTER).write_bytes(original)
        report["primary_source_unchanged"] = (ROOT / MONSTER).read_bytes() == original
        for client_run in (worktree / "build").glob("yuji-isolation-*"):
            evidence = results / client_run.name
            evidence.mkdir()
            for path in client_run.iterdir():
                if path.is_file() and path.suffix in {".log", ".pass", ".failed", ".png"}:
                    shutil.copy2(path, evidence / path.name)
        generated_launch = worktree / ".vscode"
        if generated_launch.is_dir():
            shutil.move(str(generated_launch), results / "generated-vscode")
        (results / "result.json").write_text(json.dumps(report, indent=2) + "\n")
        subprocess.run(["git", "worktree", "remove", str(worktree)], cwd=ROOT, check=True)
        if not report["primary_source_unchanged"]:
            raise AssertionError("primary production source changed during the isolated control")
    print(json.dumps(report, indent=2))
    print(f"PASS: real clients reject removed body-discovery reset; evidence={results}")


if __name__ == "__main__":
    main()
