import csv
from pathlib import Path
import tempfile
import unittest

from fps_stats import percentile, summarize


class FrameStatisticsTests(unittest.TestCase):
    def fixture(self, times):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "frames.csv"
        with path.open("w", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["elapsedNanos", "x", "y", "z", "yaw", "pitch"])
            writer.writerows((value, 1, 100, 2, 180, 8) for value in times)
        return path

    def test_exact_constant_fps_uses_intervals_not_endpoint_count(self):
        result = summarize(self.fixture(range(0, 300_000_000_001, 50_000_000)))
        self.assertEqual(result["meanFps"], 20)
        self.assertEqual(result["frames"], 6001)
        self.assertEqual(result["meanFrameMs"], 50)
        self.assertEqual(result["onePercentLowFps"], 20)
        self.assertEqual(result["maxDisplacementFromFirstFrame"], 0)

    def test_slow_frames_are_not_removed(self):
        result = summarize(self.fixture([0, 10_000_000, 110_000_000]), 0)
        self.assertAlmostEqual(result["meanFps"], 2 / .11)
        self.assertEqual(result["maxFrameMs"], 100)
        self.assertEqual(result["onePercentLowFps"], 10)

    def test_reject_short_window(self):
        with self.assertRaisesRegex(ValueError, "shorter"):
            summarize(self.fixture([0, 100]))

    def test_reject_nonmonotonic(self):
        with self.assertRaisesRegex(ValueError, "increase"):
            summarize(self.fixture([0, 100, 100]), 0)

    def test_reject_missing_zero_endpoint(self):
        with self.assertRaisesRegex(ValueError, "zero"):
            summarize(self.fixture([100, 200]), 0)

    def test_percentile_definition(self):
        self.assertEqual(percentile([10, 20], .95), 19.5)


if __name__ == "__main__":
    unittest.main()
