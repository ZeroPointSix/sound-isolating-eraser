#!/usr/bin/env python3
"""Run the pre-6516a51 fixture and its fix against 64 real Forge RNG seeds."""

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time


RESULT = re.compile(
    r"SHADOW_MELEE_RESULT mode=(before|after) seed=(\d+) status=(PASS|FAIL) "
    r"hits=(\d+) firstTarget=(-?\d+) interruption=(-?\d+) "
    r"swings=(\[[^\]]*\]) hitTicks=(\[[^\]]*\])"
)
SEEDS = set(range(64))


def read_results(path, mode):
    results = {}
    errors = []
    for match in RESULT.finditer(path.read_text() if path.exists() else ""):
        found_mode, seed, status, hits, first_target, interruption, swings, hit_ticks = match.groups()
        if found_mode != mode:
            errors.append(f"Unexpected mode {found_mode} in {path}")
            continue
        seed = int(seed)
        if seed in results:
            errors.append(f"Duplicate seed {seed} in {path}")
            continue
        results[seed] = dict(status=status, hits=int(hits), first_target=int(first_target),
                             interruption=int(interruption), swings=json.loads(swings),
                             hit_ticks=json.loads(hit_ticks))
    if set(results) != SEEDS:
        errors.append(f"Incomplete {mode} evidence; missing={sorted(SEEDS - results.keys())}, "
                      f"extra={sorted(results.keys() - SEEDS)}")
    return results, errors


def stop_tree(process):
    # Finish cleanup even if CI sends another termination signal during the grace period.
    previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM, signal.SIGINT})
    try:
        _stop_tree(process)
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)


def _stop_tree(process):
    # Gradle's single-use daemon may start a separate process group.
    parents = {int(pid): int(ppid) for pid, ppid in
               (line.split() for line in subprocess.check_output(
                   ["ps", "-eo", "pid=,ppid="], text=True).splitlines())}
    owned = {process.pid}
    while True:
        children = {pid for pid, parent in parents.items() if parent in owned}
        if children <= owned:
            break
        owned.update(children)
    handles = []
    try:
        for pid in owned:
            try:
                handles.append(os.pidfd_open(pid))
            except ProcessLookupError:
                pass
        for handle in handles:
            try:
                signal.pidfd_send_signal(handle, signal.SIGTERM)
            except ProcessLookupError:
                pass
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            live = []
            for handle in handles:
                try:
                    signal.pidfd_send_signal(handle, 0)
                    live.append(handle)
                except ProcessLookupError:
                    pass
            if not live:
                break
            process.poll()
            time.sleep(0.2)
        for handle in handles:
            try:
                signal.pidfd_send_signal(handle, signal.SIGKILL)
            except ProcessLookupError:
                pass
        process.wait()
    finally:
        for handle in handles:
            os.close(handle)


def run_mode(root, output, mode, offline, timeout):
    env = os.environ.copy()
    # Do not inherit caller switches that could accidentally change the control.
    env["JAVA_TOOL_OPTIONS"] = ("-Dshadow.melee.seeds=64 "
                                f"-Dshadow.melee.legacyTarget={'true' if mode == 'before' else 'false'}")
    command = ["bash", "./gradlew", "--no-daemon", "--max-workers=2",
               "-Dorg.gradle.jvmargs=-Xmx2G", "-PshadowMeleeOnly",
               f"-PshadowMeleeRunDir={output / (mode + '-runtime')}", "runGameTestServer"]
    # A reused GameTest world can retain villagers from the previous JVM.
    # Separate fresh worlds keep native target discovery identical in both arms.
    (output / f"{mode}-runtime").mkdir()
    if offline:
        command.insert(2, "--offline")
    log_path = output / f"{mode}.log"
    print(f"{mode}: {' '.join(command)}; log={log_path}", flush=True)
    with log_path.open("x") as log:
        process = subprocess.Popen(command, cwd=root, env=env, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            exit_code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            stop_tree(process)
            raise RuntimeError(f"{mode} timed out; preserved {log_path}") from None
        except BaseException:
            stop_tree(process)
            raise
    (output / f"{mode}.exit").write_text(f"{exit_code}\n")
    print(f"{mode}: exit={exit_code}", flush=True)
    return exit_code


def summarize(output):
    rows, errors, exits = {}, [], {}
    for mode in ("before", "after"):
        rows[mode], issues = read_results(output / f"{mode}.log", mode)
        errors.extend(issues)
        exit_path = output / f"{mode}.exit"
        exits[mode] = int(exit_path.read_text()) if exit_path.exists() else None
        if exits[mode] is None:
            errors.append(f"Missing {mode} process exit code")
        for seed in SEEDS - rows[mode].keys():
            rows[mode][seed] = dict(status="NO_RESULT", hits=None, first_target=None,
                                   interruption=None, swings=[], hit_ticks=[])
    failures = {mode: sorted(seed for seed, result in rows[mode].items() if result["status"] != "PASS")
                for mode in rows}
    with (output / "seeds.csv").open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["seed", "test", "before", "after", "before_hits", "after_hits",
                         "before_first_target", "after_first_target", "before_hit_ticks", "after_hit_ticks"])
        for seed in sorted(SEEDS):
            before, after = rows["before"][seed], rows["after"][seed]
            writer.writerow([seed, f"shadowmelee_seed_{seed}", before["status"], after["status"],
                             before["hits"], after["hits"], before["first_target"], after["first_target"],
                             before["hit_ticks"], after["hit_ticks"]])
    report = dict(seeds=64, failures=failures, exits=exits, results=rows, errors=errors)
    (output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(dict(seeds=64, failures=failures, exits=exits)), flush=True)
    if errors:
        raise RuntimeError("; ".join(errors))
    if not failures["before"] or exits["before"] == 0:
        raise RuntimeError("Negative control did not reproduce the old failure")
    if failures["after"] or exits["after"] != 0:
        raise RuntimeError("Fixed fixture did not pass every real GameTest")
    for seed, result in rows["after"].items():
        hits = result["hit_ticks"]
        if result["hits"] < 3 or result["interruption"] < 0 or len(hits) != result["hits"]:
            raise RuntimeError(f"Invalid actual-hit evidence for seed {seed}")
        if any(b - a < 20 for a, b in zip(hits, hits[1:])):
            raise RuntimeError(f"Attack interval violated for seed {seed}")
    print("PASS: old failure reproduced; all 64 fixed seeds passed without retries", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--summarize-only", action="store_true")
    parser.add_argument("--timeout", type=int, default=1800)
    parser.add_argument("--output", type=Path, default=Path("build/reports/shadow-melee-comparison"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if not args.summarize_only:
        try:
            with (output / ".run.lock").open("x") as lock:
                lock.write(f"pid={os.getpid()}\n")
        except FileExistsError:
            parser.error("Evidence directory is reserved by another run; use a new --output directory")
        if any((output / f"{mode}.log").exists() for mode in ("before", "after")):
            parser.error("Evidence directory already contains logs; use a new --output directory")
        sources = ["src/main/java/com/zeropointsix/eraser/shadow/ShadowTanglerEntity.java",
                   "src/main/java/com/zeropointsix/eraser/gametest/ShadowTanglerGameTests.java",
                   "build.gradle"]
        metadata = dict(
            head=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
            status=subprocess.check_output(["git", "status", "--short"], cwd=root, text=True),
            sha256={path: hashlib.sha256((root / path).read_bytes()).hexdigest() for path in sources},
            seeds=sorted(SEEDS), baseline="pre-6516a51 target discovery; production code unchanged",
        )
        (output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
        def terminate(signum, _frame):
            signal.signal(signum, signal.SIG_IGN)
            raise SystemExit(128 + signum)

        signal.signal(signal.SIGTERM, terminate)
        try:
            for mode in ("before", "after"):
                run_mode(root, output, mode, args.offline, args.timeout)
        except BaseException:
            try:
                summarize(output)
            except Exception as error:
                print(f"Incomplete run report: {error}", flush=True)
            raise
    summarize(output)


if __name__ == "__main__":
    main()
