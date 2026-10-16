"""Regression checks for animation timing, independent of binary asset availability."""
import unittest

from validate_assets import ASSETS, read, validate_keyframes


class KeyframeTimingTests(unittest.TestCase):
    def test_static_vectors_and_exact_end_are_valid(self):
        validate_keyframes("valid", {"animation_length": 2.0, "bones": {
            "head": {"rotation": {"0.0": [0, 0, 0], "2.0": [1, 0, 0]}, "scale": [1, 1, 1]}
        }})

    def test_negative_keyframe_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Keyframe timing"):
            validate_keyframes("negative", {"animation_length": 2.0, "bones": {
                "head": {"rotation": {"-0.01": [0, 0, 0]}}
            }})

    def test_after_end_keyframe_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Keyframe timing"):
            validate_keyframes("late", {"animation_length": 2.0, "bones": {
                "head": {"rotation": {"2.333": [0, 0, 0]}}
            }})

    def test_checked_in_animation_keyframes(self):
        paths = list((ASSETS / "animations").glob("*.animation.json"))
        self.assertTrue(paths, "real animation assets must be present")
        for path in paths:
            for name, animation in read(path)["animations"].items():
                with self.subTest(animation=name):
                    validate_keyframes(name, animation)


if __name__ == "__main__":
    unittest.main()
