"""Validate the Neon Tumor resource contract without starting Minecraft."""

import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources"
ASSETS = ROOT / "assets/sound_isolating_eraser"
geometry = json.loads((ASSETS / "geo/neon_tumor.geo.json").read_text())["minecraft:geometry"][0]
bones = {bone["name"] for bone in geometry["bones"]}
assert len(bones) == 19
animations = json.loads((ASSETS / "animations/neon_tumor.animation.json").read_text())["animations"]
assert set(animations) == {"animation.neon_tumor." + name for name in (
    "idle_dormant", "idle_awake", "crawl", "lunge", "slam", "hurt", "engulf", "digest", "spawn", "death")}
for animation in animations.values():
    assert set(animation.get("bones", {})) <= bones
for color in ("red", "blue", "purple"):
    for suffix in ("", "_glowmask"):
        image = Image.open(ASSETS / f"textures/entity/neon_tumor_{color}{suffix}.png")
        assert image.size == (128, 64) and image.mode == "RGBA"
        assert image.getchannel("A").getextrema()[0] < 255
assert Image.open(ASSETS / "textures/item/neon_tumor_spawn_egg.png").size == (16, 16)
assert Image.open(ASSETS / "textures/mob_effect/corroded.png").size == (18, 18)
model = json.loads((ASSETS / "models/item/neon_tumor_spawn_egg.json").read_text())
assert model["parent"] == "item/generated" or model["parent"] == "minecraft:item/generated"
assert model["textures"]["layer0"] == "sound_isolating_eraser:item/neon_tumor_spawn_egg"
for lang in ("zh_cn", "en_us"):
    data = json.loads((ASSETS / f"lang/{lang}.json").read_text())
    for key in ("entity.sound_isolating_eraser.neon_tumor", "item.sound_isolating_eraser.neon_tumor_spawn_egg",
                "effect.sound_isolating_eraser.corroded", "death.attack.sound_isolating_eraser.corrosion"):
        assert data[key]
for size, ranges in (("small", [(0, 1)]), ("medium", [(0, 2), (0, 1)]), ("large", [(1, 3), (1, 2)])):
    loot = json.loads((ROOT / f"data/sound_isolating_eraser/loot_tables/entities/neon_tumor_{size}.json").read_text())
    assert len(loot["pools"]) == len(ranges)
    for pool, (low, high) in zip(loot["pools"], ranges):
        functions = pool["entries"][0]["functions"]
        assert functions[0]["count"]["min"] == low and functions[0]["count"]["max"] == high
        assert functions[1]["function"] == "minecraft:looting_enchant"
assert not (ASSETS / "items/neon_tumor_spawn_egg.json").exists()
print("Neon Tumor: 19 bones, 10 animations, 9 textures, language/model/loot contracts passed")
