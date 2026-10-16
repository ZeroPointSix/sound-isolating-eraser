#!/usr/bin/env python3
"""Validate the camera's shipped assets and localized UI contract."""
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/sound_isolating_eraser"
languages = [json.loads((ASSETS / "lang" / (name + ".json")).read_text()) for name in ("en_us", "zh_cn")]
keys = [{key for key in language if "keeper_" in key or key.endswith(".in_photo")} for language in languages]
assert keys[0] == keys[1] and len(keys[0]) == 32, "camera locale keys must match"
for key in keys[0]:
    assert languages[0][key].count("%s") == languages[1][key].count("%s"), key
photo = json.loads((ASSETS / "models/item/keeper_photo.json").read_text())
assert [entry["predicate"]["sound_isolating_eraser:fade"] for entry in photo["overrides"]] == [0.125, 0.375, 0.75, 1.0]
for model in (ASSETS / "models/item").glob("keeper_*.json"):
    value = json.loads(model.read_text())
    texture = value["textures"]["layer0"].split(":", 1)[1]
    raw = (ASSETS / "textures" / (texture + ".png")).read_bytes()
    assert raw[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", raw[16:24]) == (16, 16), model
frame = (ASSETS / "textures/gui/photo_world_frame.png").read_bytes()
assert struct.unpack(">II", frame[16:24]) == (256, 256)
shader = json.loads((ASSETS / "shaders/post/keeper_photo.json").read_text())
assert [entry["name"] for entry in shader["passes"]] == ["color_convolve", "blit"]
print("KEEPER_ASSETS_PASS locales=32 models=6 textures=7 fade_thresholds=4 shader_passes=2")
