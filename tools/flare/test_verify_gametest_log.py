"""Parser tests only; these fixtures do not substitute for Forge GameTests."""
import unittest

from verify_gametest_log import EXPECTED_BATCHES, verify_log


class GameTestLogGateTests(unittest.TestCase):
    def setUp(self):
        self.batches = "\n".join(
            f"Running test batch '{name}:1' ({count} tests)"
            for name, count in EXPECTED_BATCHES.items())
        self.success = "All 362 required tests passed :)"

    def test_complete_run(self):
        verify_log(self.batches + "\n" + self.success)

    def test_missing_edge_batch(self):
        log = self.batches.replace("Running test batch 'flare_edge_wall:1' (1 tests)", "")
        with self.assertRaises(SystemExit):
            verify_log(log + "\n" + self.success)

    def test_missing_ai_batch(self):
        log = self.batches.replace("Running test batch 'flare_edge_ai_yuji:1' (1 tests)", "")
        with self.assertRaises(SystemExit):
            verify_log(log + "\n" + self.success)

    def test_missing_permission_batch(self):
        log = self.batches.replace("Running test batch 'flare_permission_event:1' (1 tests)", "")
        with self.assertRaises(SystemExit):
            verify_log(log + "\n" + self.success)

    def test_incomplete_run_after_old_success(self):
        with self.assertRaises(SystemExit):
            verify_log(self.success + "\n" + self.batches)

    def test_multiple_successful_runs(self):
        with self.assertRaises(SystemExit):
            verify_log(self.batches + "\n" + self.success + "\n" + self.success)

    def test_duplicated_batches(self):
        with self.assertRaises(SystemExit):
            verify_log(self.batches + "\n" + self.batches + "\n" + self.success)

    def test_incorrect_count(self):
        with self.assertRaises(SystemExit):
            verify_log(self.batches.replace("(15 tests)", "(14 tests)") + "\n" + self.success)

    def test_too_few_completed(self):
        with self.assertRaises(SystemExit):
            verify_log(self.batches + "\nAll 40 required tests passed :)")

    def test_failure_after_success(self):
        with self.assertRaises(SystemExit):
            verify_log(self.batches + "\n" + self.success + "\nBUILD FAILED")


if __name__ == "__main__":
    unittest.main()
