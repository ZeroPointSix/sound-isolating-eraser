"""Check original supplied artwork and the common/client resource contract."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
ASSETS = RESOURCES / "assets/sound_isolating_eraser"
EXPECTED = {
    "entity/suxia/suxia.png": "0258c3e7797a0873698743f27efd80a4d7597fa568421ae329be3ae7f2d4dd0f",
    "entity/suxia/suxia_angry.png": "09eb69824455a4e10edb0377aa84636b368807275bf50b427797def4f21c2b8b",
    "entity/suxia/suxia_eyes.png": "7a8faede6637c97dd616dac0fdf90bcd723ac3c2c14cfeeb01ad671643ac205f",
    "item/suxia_pocket_watch.png": "0feb387eaceb7d26974f5a86406bfa3a2087df3d55d61d2057e98bd396e7bbcc",
    "item/suxia_pocket_watch_open.png": "f23f89f05edca168f35cfc6e89d8a71ffbe5bd33a45f67797643c3d5a13e589e",
}
for path, digest in EXPECTED.items():
    assert hashlib.sha256((ASSETS / "textures" / path).read_bytes()).hexdigest() == digest, path

langs = [json.loads((ASSETS / "lang" / name).read_text()) for name in ("en_us.json", "zh_cn.json")]
keys = [{key for key in lang if "suxia" in key} for lang in langs]
assert keys[0] == keys[1], "Suxia language keys must match"
assert not any("yourmodid" in key for lang in langs for key in lang), "placeholder namespace"
model = json.loads((ASSETS / "models/item/suxia_pocket_watch.json").read_text())
assert model["overrides"][0]["predicate"] == {"sound_isolating_eraser:open": 1}, "open model predicate"
tag = json.loads((RESOURCES / "data/sound_isolating_eraser/tags/items/precious_memory.json").read_text())
assert tag["values"] == ["sound_isolating_eraser:suxia_pocket_watch"], "memory category"
java = ROOT / "src/main/java/com/zeropointsix/eraser"
assert (java / "client/SuxiaModel.java").read_text().count(".addBox(") == 31, "original 31 cuboids"
for path in (java / "suxia").glob("*.java"):
    assert "net.minecraft.client" not in path.read_text(), f"dedicated-server class references client: {path}"
print("SUXIA_ASSETS_PASSED: five original texture hashes, matching languages, model predicate, category and dedicated-server boundary")
