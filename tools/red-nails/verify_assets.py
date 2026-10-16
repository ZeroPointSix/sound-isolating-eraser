"""Validate original art, item predicates and common-side isolation without a client."""
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
RESOURCE = ROOT / "src/main/resources"
ASSETS = RESOURCE / "assets/sound_isolating_eraser"
EXPECTED = {
    "item/red_nails.png": "b113c3cd31e6af0225a35f0c6aa9d65fc4bed17265464c066cbc67176c2e197d",
    "item/red_nails_0.png": "467bf513d74eb6d1c582211d44c06b64d2b751118950b7d35d47e20564b79e4c",
    "item/red_nails_1.png": "30b98ef701b2e34d46eaf9b716ab6370d0771dbc79abd53303d9b30b10a26a65",
    "item/red_nails_2.png": "35b252afd98c30588e7a7105bd5614f81ab463596e786db003561021b70852fa",
    "item/red_nails_3.png": "0d416a3524b10dd3146ad4a6a559f103e88c59d8ff0f6af911630be6ac254d04",
    "entity/water_ghost/water_ghost.png": "310c90901e107223982bbfec4abc4f416a029a6faa884234f65accb66cff97cd",
    "entity/water_ghost/water_ghost_eyes.png": "5f1233dcbb6b505b9a55e438406382ed658692d3361824ef6cf5cc7b7fc11114",
}
for path, digest in EXPECTED.items():
    data = (ASSETS / "textures" / path).read_bytes()
    assert hashlib.sha256(data).hexdigest() == digest, "Original texture changed: " + path
    width, height = struct.unpack(">II", data[16:24])
    assert width == height and width in (16, 32, 64, 128), (path, width, height)
model = json.loads((ASSETS / "models/item/red_nails.json").read_text())
assert [entry["predicate"]["sound_isolating_eraser:fallen_nails"] for entry in model["overrides"]] == [.25, .5, .75, 1]
for entry in model["overrides"]:
    path = entry["model"].split(":")[1]
    item = json.loads((ASSETS / "models" / (path + ".json")).read_text())
    assert (ASSETS / "textures" / (item["textures"]["layer0"].split(":")[1] + ".png")).exists()
languages = [json.loads((ASSETS / "lang" / (lang + ".json")).read_text()) for lang in ("zh_cn", "en_us")]
keys = {key for key in languages[0] if any(part in key for part in ("red_nails", "water_ghost", "nail_torn", "finger."))}
assert keys <= languages[1].keys(), "Missing English translations"
source = ROOT / "src/main/java/com/zeropointsix/eraser"
for path in (source / "rednails").glob("*.java"):
    assert "net.minecraft.client" not in path.read_text(), "Dedicated-server class references client: " + path.name
geometry = (source / "client/WaterGhostModel.java").read_text()
assert geometry.count(".addBox(") == 33, "Original 33-cube geometry changed"
assert "RenderType::entityCutoutNoCull" in geometry, "Transparent hair must render double-sided"
for tag in ("bypasses_armor", "bypasses_cooldown"):
    assert "sound_isolating_eraser:nail_torn" in json.loads((RESOURCE / f"data/minecraft/tags/damage_type/{tag}.json").read_text())["values"]
assert "sound_isolating_eraser:red_nails" in json.loads((RESOURCE / "data/curios/tags/items/ring.json").read_text())["values"]
memories = json.loads((RESOURCE / "data/sound_isolating_eraser/tags/items/precious_memory.json").read_text())["values"]
assert {"sound_isolating_eraser:suxia_pocket_watch", "sound_isolating_eraser:red_nails"} <= set(memories), "Shared memory category retains both items"
print("RED_NAILS_ASSETS_PASSED: seven original textures, 33 cubes, four predicates, translations, damage tags and common-side isolation")
