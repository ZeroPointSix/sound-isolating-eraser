"""Validate checkpoint coverage and retain derived results, never raw images/NBT."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = {
    "idle": {
        "server-idle-stationary", "client-idle-stationary", "server-idle-turned",
        "client-idle-turned", "server-idle-complete",
    },
    "full": {
        "client-gallery", "client-summoned", "client-recalled", "client-second-summoned",
        "client-second-recalled", "client-cooldown-rejected", "client-logout",
        "server-lifecycle", "server-multi-watch", "server-cooldown-rejected",
        "server-rejoin", "client-rejoined", "server-sleep", "server-persistence", "server-wake",
        "client-hostile-sleep", "client-hostile-awake", "server-ready-restart",
        "server-restart", "client-restored", "server-restored-wake", "client-restored-awake",
    },
    "cooldown": {
        "server-cool-start", "server-cool-before-rejected", "server-cool-dropped",
        "server-cool-picked", "server-cool-picked-rejected", "server-cool-rejoined",
        "server-cool-rejoined-rejected", "server-ready-restart", "client-ready-restart",
        "server-cool-restarted", "server-cool-complete", "client-cool-before",
        "client-cool-picked", "client-cool-rejoined", "client-cool-restarted",
        "server-teleport-12-64", "server-ledger-prelogin",
    },
}
REQUIRED["death-cooldown"] = REQUIRED["cooldown"] | {"server-death-stimulus"}
PNG_COUNTS = {"idle": 2, "full": 11, "cooldown": 8, "death-cooldown": 8}
MARKERS = {
    "idle": "SUXIA_IDLE_REAL_CLIENT_PASSED",
    "full": "SUXIA_REAL_CLIENT_AND_DEDICATED_SERVER_E2E_PASSED",
    "cooldown": "SUXIA_COOLDOWN_BOUNDARIES_REAL_CLIENT_PASSED",
    "death-cooldown": "SUXIA_COOLDOWN_BOUNDARIES_REAL_CLIENT_PASSED",
}


def summarize(root):
    summary = {"head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
               "modes": {}, "errors": []}
    for mode, required in REQUIRED.items():
        log_path = root / "build" / f"suxia-{mode}.log"
        log = log_path.read_text() if log_path.exists() else ""
        paths = [line.removeprefix("SUXIA_QA_RESULTS=") for line in log.splitlines()
                 if line.startswith("SUXIA_QA_RESULTS=")]
        if len(paths) != 1:
            summary["errors"].append(f"{mode}: expected one recorded results directory")
            continue
        directory = Path(paths[0]).resolve()
        if directory.parent != (root / "build").resolve():
            summary["errors"].append(f"{mode}: results outside this checkout")
            continue
        passes = {path.stem for path in directory.glob("*.pass")}
        images = sorted(directory.glob("*.png"))
        failures = sorted(path.name for path in directory.glob("*.failed"))
        missing = sorted(required - passes)
        if missing or failures or len(images) != PNG_COUNTS[mode] or MARKERS[mode] not in log:
            summary["errors"].append(f"{mode}: incomplete or failed native evidence")
        summary["modes"][mode] = {
            "checkpoints": sorted(passes), "missing": missing, "failures": failures,
            "png_count": len(images), "driver_success": MARKERS[mode] in log,
            "boundary_measurements": {path.stem: path.read_text() for path in sorted(directory.glob("server-*.pass"))
                                      if path.stem in {"server-teleport-12-64", "server-ledger-prelogin", "server-death-stimulus"}},
            "measurements": {path.name: path.read_text() for path in sorted(directory.glob("*.txt"))
                             if path.name.endswith("-camera.txt") or path.name.startswith("cool-")},
            "image_sha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in images},
        }
    return summary


if __name__ == "__main__":
    result = summarize(ROOT)
    output = ROOT / "build/suxia-acceptance-summary.json"
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    raise SystemExit(bool(result["errors"]))
