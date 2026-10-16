"""Offline guards; actual profiling remains a required independent step."""
import unittest
import cap_comparison as c


class ComparisonGuards(unittest.TestCase):
    def test_real_nbt_vector(self):
        self.assertEqual(c.vector("[0.5d, 118.0d, 0.5d]"), c.STANDING)

    def test_drift_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "drifted"):
            c.check_position((.6, 118, .5))

    def test_nonfinite_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "Invalid"):
            c.check_position((float("nan"), 118, .5))

    def test_valid_metadata(self):
        data = {"metadata": {"startTime": 1000, "endTime": 301050, "numberOfTicks": 6000,
                "platformStatistics": {"tps": {"last5m": 20}, "mspt": {"last5m":
                {"mean": 5, "median": 4, "percentile95": 6, "max": 20}}}}}
        self.assertEqual(c.check_metadata(data)["durationSeconds"], 300.05)
        data["metadata"]["numberOfTicks"] = 4000
        with self.assertRaisesRegex(RuntimeError, "gate"):
            c.check_metadata(data)

    def test_too_short_profile_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "gate"):
            c.check_metadata({"metadata": {"startTime": 0, "endTime": 10000, "numberOfTicks": 6000}})

    def test_policy_and_baseline_order(self):
        self.assertEqual([r[1] for r in c.WINDOWS], [False, True, True, False])
        self.assertIn("dimensionCap = 32", c.config_text(32))
        self.assertIn("chunkCap = 64", c.config_text(512))


if __name__ == "__main__":
    unittest.main()
