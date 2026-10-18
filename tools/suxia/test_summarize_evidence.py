"""Unit tests for the evidence parser, not substitutes for Forge/client runs."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from summarize_evidence import MARKERS, PNG_COUNTS, REQUIRED, summarize


class EvidenceSummaryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        subprocess.run(["git", "-c", "user.name=Evidence test", "-c", "user.email=test@example.invalid",
                        "commit", "-q", "--allow-empty", "-m", "Fixture"], cwd=self.root, check=True)
        (self.root / "build").mkdir()
        for mode, required in REQUIRED.items():
            results = self.root / "build" / mode
            results.mkdir()
            for checkpoint in required:
                (results / (checkpoint + ".pass")).write_text("Parser fixture only\n")
            for index in range(PNG_COUNTS[mode]):
                (results / f"fixture-{index}.png").write_bytes(b"Parser fixture, not a screenshot")
            (self.root / "build" / f"suxia-{mode}.log").write_text(
                f"SUXIA_QA_RESULTS={results}\n{MARKERS[mode]}\n")

    def test_complete_marker_sets(self):
        result = summarize(self.root)
        self.assertEqual([], result["errors"])
        self.assertEqual(22, len(result["modes"]["full"]["checkpoints"]))
        self.assertEqual(17, len(result["modes"]["cooldown"]["checkpoints"]))
        self.assertEqual(18, len(result["modes"]["death-cooldown"]["checkpoints"]))
        self.assertEqual("Parser fixture only\n", result["modes"]["cooldown"]["boundary_measurements"]["server-ledger-prelogin"])
        self.assertNotIn("not a screenshot", json.dumps(result))

    def test_missing_teleport_is_not_success(self):
        (self.root / "build/cooldown/server-teleport-12-64.pass").unlink()
        self.assertTrue(summarize(self.root)["errors"])

    def test_missing_prelogin_ledger_is_not_success(self):
        (self.root / "build/death-cooldown/server-ledger-prelogin.pass").unlink()
        self.assertTrue(summarize(self.root)["errors"])

    def test_normal_cooldown_cannot_substitute_for_death(self):
        (self.root / "build/death-cooldown/server-death-stimulus.pass").unlink()
        self.assertTrue(summarize(self.root)["errors"])

    def test_missing_checkpoint_is_not_success(self):
        (self.root / "build/full/server-sleep.pass").unlink()
        result = summarize(self.root)
        self.assertTrue(result["errors"])
        self.assertEqual(["server-sleep"], result["modes"]["full"]["missing"])

    def test_failed_marker_overrides_passes(self):
        (self.root / "build/idle/client.failed").write_text("Parser failure fixture")
        self.assertTrue(summarize(self.root)["errors"])

    def test_missing_image_is_not_success(self):
        (self.root / "build/cooldown/fixture-0.png").unlink()
        self.assertTrue(summarize(self.root)["errors"])

    def test_missing_driver_success_is_not_success(self):
        path = self.root / "build/suxia-idle.log"
        path.write_text(path.read_text().replace(MARKERS["idle"], "driver failed"))
        self.assertTrue(summarize(self.root)["errors"])

    def test_rejects_evidence_from_another_checkout(self):
        (self.root / "build/suxia-idle.log").write_text("SUXIA_QA_RESULTS=/tmp/other-run\n")
        self.assertIn("idle: results outside this checkout", summarize(self.root)["errors"])


if __name__ == "__main__":
    unittest.main()
