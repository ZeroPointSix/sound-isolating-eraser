"""Check the evidence parser, not the Minecraft behavior itself."""
from pathlib import Path
import tempfile
import unittest

from voice_negative_control import FAILURE, validate_failure


class VoiceNegativeControlTests(unittest.TestCase):
    def fixture(self, root):
        (root / "isolation-server.failed").write_text(FAILURE)
        for name in ("server-voice-0", "client-voice-0-YujiTester", "client-voice-0-YujiWitness",
                     "client-voice-found-0-YujiTester"):
            (root / (name + ".pass")).write_text("parser fixture, not functional evidence")

    def test_accepts_only_expected_behavior_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            validate_failure(root, 1)
            for code in (0, 124, 137, 143):
                with self.subTest(code=code), self.assertRaises(AssertionError):
                    validate_failure(root, code)

    def test_rejects_unrelated_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            (root / "isolation-server.failed").write_text("startup timeout")
            with self.assertRaises(AssertionError):
                validate_failure(root, 1)
            self.fixture(root)
            (root / "client.failed").write_text("disconnected")
            with self.assertRaises(AssertionError):
                validate_failure(root, 1)

    def test_rejects_missing_real_client_preconditions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            for path in list(root.glob("*.pass")):
                path.unlink()
                with self.subTest(missing=path.name), self.assertRaises(AssertionError):
                    validate_failure(root, 1)
                self.fixture(root)


if __name__ == "__main__":
    unittest.main()
