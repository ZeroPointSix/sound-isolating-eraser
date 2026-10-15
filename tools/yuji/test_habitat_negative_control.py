"""The negative-control verifier must not turn arbitrary failures into green evidence."""
import unittest
from unittest.mock import Mock, patch
import signal
import subprocess

from habitat_negative_control import stop_process_group, validate_negative_log


LOG = """[Server thread/INFO] [minecraft/GameTestBatchRunner]: Running test batch 'yuji:1' (58 tests)...
[Server thread/INFO] [minecraft/GameTestBatchRunner]: Running test batch 'yuji_city:1' (22 tests)...
[Server thread/INFO] [minecraft/GameTestBatchRunner]: Running test batch 'yuji_city_iter:1' (1 tests)...
[Server thread/INFO] [minecraft/GameTestBatchRunner]: Running test batch 'yuji_habitat:1' (5 tests)...
[Server thread/INFO] [minecraft/GameTestBatchRunner]: Running test batch 'yuji_betrayal_species:1' (5 tests)...
[Server thread/ERROR] [minecraft/LogTestReporter]: liangshewalksinsideandstaysunderroof failed! whole body must be inside room 5: (6, 1, 2) -> (7, 1, 3)
[Server thread/ERROR] [minecraft/LogTestReporter]: xieyinwalksintobodywidthcorridor failed! Xieyin must navigate into narrow corridor: (7, 1, 2)
[Server thread/INFO] [minecraft/GameTestServer]: ========= 91 GAME TESTS COMPLETE ======================
[Server thread/INFO] [minecraft/GameTestServer]: 2 required tests failed :(
[Server thread/INFO] [minecraft/GameTestServer]:    - liangshewalksinsideandstaysunderroof
[Server thread/INFO] [minecraft/GameTestServer]:    - xieyinwalksintobodywidthcorridor
BUILD FAILED
"""


class HabitatNegativeControlTests(unittest.TestCase):
    def test_accepts_only_the_intended_rejected_behavior_run(self):
        self.assertEqual(validate_negative_log(LOG, 1)["completed"], 91)

    def test_rejects_successful_or_killed_process(self):
        for code in (0, 124, 137, 143):
            with self.subTest(code=code), self.assertRaises(AssertionError):
                validate_negative_log(LOG, code)

    def test_rejects_compile_failure_without_gametests(self):
        with self.assertRaises(AssertionError):
            validate_negative_log("compileJava FAILED\nBUILD FAILED\n", 1)

    def test_rejects_missing_required_behavior_failure(self):
        log = LOG.replace("xieyinwalksintobodywidthcorridor", "liangsheleavesdestroyedshelterandfindsanother")
        with self.assertRaises(AssertionError):
            validate_negative_log(log, 1)

    def test_rejects_unrelated_failure(self):
        log = LOG.replace("2 required", "3 required").replace("BUILD FAILED", "[Server thread/INFO] [minecraft/GameTestServer]:    - unrelatedtest\nBUILD FAILED")
        with self.assertRaises(AssertionError):
            validate_negative_log(log, 1)

    def test_rejects_partial_or_miscounted_run(self):
        for log in (LOG.replace("91 GAME TESTS COMPLETE", "90 GAME TESTS COMPLETE"),
                    LOG.replace("2 required tests failed", "3 required tests failed"),
                    LOG.replace("(5 tests)", "(4 tests)")):
            with self.subTest(log=log), self.assertRaises(AssertionError):
                validate_negative_log(log, 1)

    def test_rejects_self_consistent_habitat_only_run(self):
        log = "\n".join(line for line in LOG.splitlines()
                        if "Running test batch" not in line or "yuji_habitat:" in line)
        with self.assertRaises(AssertionError):
            validate_negative_log(log.replace("91 GAME TESTS COMPLETE", "5 GAME TESTS COMPLETE"), 1)

    def test_rejects_correct_names_with_timeout_or_fixture_failure(self):
        for reason in ("Didn't succeed or fail within 500 ticks", "habitat fixture changed at BlockPos(1, 1, 1)"):
            log = LOG.replace("whole body must be inside room 5: (6, 1, 2) -> (7, 1, 3)", reason)
            with self.subTest(reason=reason), self.assertRaises(AssertionError):
                validate_negative_log(log, 1)

    def test_rejects_missing_failure_diagnostic(self):
        log = "\n".join(line for line in LOG.splitlines() if "LogTestReporter" not in line)
        with self.assertRaises(AssertionError):
            validate_negative_log(log, 1)

    @patch("habitat_negative_control.os.killpg")
    def test_cleanup_kills_whole_group_after_wrapper_timeout(self, killpg):
        process = Mock(pid=12345)
        process.wait.side_effect = [subprocess.TimeoutExpired("gradle", 10), 143]
        stop_process_group(process)
        self.assertEqual(killpg.call_args_list, [unittest.mock.call(12345, signal.SIGTERM),
                                               unittest.mock.call(12345, signal.SIGKILL)])
        self.assertEqual(process.wait.call_args_list, [unittest.mock.call(timeout=10), unittest.mock.call()])


if __name__ == "__main__":
    unittest.main()
