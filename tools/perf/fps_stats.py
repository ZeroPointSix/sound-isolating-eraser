"""Recompute loop-cadence FPS from timestamp endpoints, never the configured cap."""

import csv
import hashlib
import math
from pathlib import Path


def percentile(sorted_values, quantile):
    position = (len(sorted_values) - 1) * quantile
    low = math.floor(position)
    high = math.ceil(position)
    return sorted_values[low] + (sorted_values[high] - sorted_values[low]) * (position - low)


def summarize(path, minimum_seconds=300):
    path = Path(path)
    with path.open(newline="") as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) < 2:
        raise ValueError("Need at least two timestamp endpoints")
    timestamps = [int(row["elapsedNanos"]) for row in rows]
    intervals = [b - a for a, b in zip(timestamps, timestamps[1:])]
    if timestamps[0] != 0 or any(value <= 0 for value in intervals):
        raise ValueError("Timestamps must start at zero and strictly increase")
    seconds = (timestamps[-1] - timestamps[0]) / 1e9
    if seconds < minimum_seconds:
        raise ValueError("Window shorter than required")
    ms = sorted(value / 1e6 for value in intervals)
    slow = ms[-max(1, math.ceil(len(ms) * .01)):]
    pose = {name: [float(row[name]) for row in rows] for name in ("x", "y", "z", "yaw", "pitch")}
    if not all(math.isfinite(value) for values in pose.values() for value in values):
        raise ValueError("Non-finite player pose")
    distances = [math.sqrt(sum((pose[axis][i] - pose[axis][0]) ** 2 for axis in ("x", "y", "z")))
                 for i in range(len(rows))]
    return {"frames": len(rows), "intervals": len(intervals), "seconds": seconds,
            "meanFps": len(intervals) / seconds, "meanFrameMs": sum(ms) / len(ms),
            "medianFrameMs": percentile(ms, .5), "p95FrameMs": percentile(ms, .95),
            "p99FrameMs": percentile(ms, .99), "maxFrameMs": ms[-1],
            "onePercentLowFps": 1000 / (sum(slow) / len(slow)),
            "framesOver50Ms": sum(value > 50 for value in ms),
            "framesOver100Ms": sum(value > 100 for value in ms),
            "startPose": {name: values[0] for name, values in pose.items()},
            "endPose": {name: values[-1] for name, values in pose.items()},
            "maxDisplacementFromFirstFrame": max(distances),
            "yawRange": max(pose["yaw"]) - min(pose["yaw"]),
            "pitchRange": max(pose["pitch"]) - min(pose["pitch"]),
            "csvSha256": hashlib.sha256(path.read_bytes()).hexdigest()}
