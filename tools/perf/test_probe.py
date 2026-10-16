"""Offline guard tests; these do not substitute for the real Forge experiment."""

import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import production_probe as probe


class GuardTests(unittest.TestCase):
    def test_published_spark_hash_shape(self):
        self.assertEqual(len(probe.SPARK_SHA512), 128)
        self.assertEqual(int(probe.SPARK_SHA512, 16).bit_length() > 0, True)

    def test_download_rejects_checksum_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "spark.jar"
            def transfer(*args, **kwargs):
                target.write_bytes(b"wrong")
            with patch.object(probe.subprocess, "run", side_effect=transfer):
                with self.assertRaisesRegex(RuntimeError, "mismatch"):
                    probe.download("https://example.invalid/spark.jar", target, "0" * 128)

    def test_download_records_verified_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "spark.jar"
            data = b"unit-test-only"
            def transfer(*args, **kwargs):
                target.write_bytes(data)
            with patch.object(probe, "OUT", Path(directory)), patch.object(probe, "manifest", []), \
                    patch.object(probe.subprocess, "run", side_effect=transfer):
                probe.download("https://example.invalid/spark.jar", target, hashlib.sha512(data).hexdigest())
                self.assertEqual(probe.manifest[0]["bytes"], len(data))
                self.assertTrue(probe.manifest[0]["publishedHashVerified"])

    def test_wait_cannot_pass_for_exited_process(self):
        server = probe.Server.__new__(probe.Server)
        server.process = Mock(returncode=1)
        server.process.poll.return_value = 1
        server.lines = ["Done (0.1s)!"]
        with self.assertRaisesRegex(RuntimeError, "Server exited"):
            server.wait("Done", 0, 1)

    def test_wait_uses_only_requested_log_window(self):
        server = probe.Server.__new__(probe.Server)
        server.process = Mock()
        server.process.poll.return_value = None
        server.lines = ["https://spark.lucko.me/old", "no current result"]
        with self.assertRaises(TimeoutError):
            server.wait("https://spark", 1, 0.01)


if __name__ == "__main__":
    unittest.main()
