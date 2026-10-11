"""Regression tests for Brickrot asset integrity checks."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/brickrot-assets.py"
ASSETS = ROOT / "src/main/resources/assets/sound_isolating_eraser"

spec = spec_from_file_location("brickrot_assets", SCRIPT)
brickrot_assets = module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(brickrot_assets)


class BrickrotAssetIntegrityTest(unittest.TestCase):
    def files(self):
        return {
            name: (ASSETS / name).read_bytes()
            for name in brickrot_assets.FILES
        }

    def test_current_assets_match_pinned_hashes(self):
        brickrot_assets.validate(self.files())

    def test_one_byte_asset_tampering_is_rejected(self):
        files = self.files()
        target = "textures/entity/brickrot.png"
        tampered = bytearray(files[target])
        tampered[-1] ^= 1
        files[target] = bytes(tampered)

        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            brickrot_assets.validate(files)

    def test_wrong_archive_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "tampered.zip"
            archive.write_bytes(b"not the approved Brickrot archive")
            with self.assertRaisesRegex(ValueError, "archive SHA-256 mismatch"):
                brickrot_assets.verify_archive_hash(archive)


if __name__ == "__main__":
    unittest.main()
