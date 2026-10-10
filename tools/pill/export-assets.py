"""Export the approved D:\\mc pixel source without browser or third-party dependencies."""
import json
from pathlib import Path
import struct
import subprocess
import zlib

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/sound_isolating_eraser"
SCRIPT = """
const fs = require('fs'), vm = require('vm');
const scope = {};
vm.runInNewContext(fs.readFileSync(process.argv[1], 'utf8'), scope);
console.log(JSON.stringify(Object.fromEntries(scope.PillArt.names.map(name => {
  const image = scope.PillArt.get(name);
  return [name, {width:image.w, height:image.h, pixels:Array.from(image.data)}];
}))));
"""


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def export():
    images = json.loads(subprocess.check_output([
        "node", "-e", SCRIPT, str(Path(__file__).with_name("pixelart.js"))], text=True))
    textures = ASSETS / "textures/item"
    models = ASSETS / "models/item"
    textures.mkdir(parents=True, exist_ok=True)
    models.mkdir(parents=True, exist_ok=True)
    for name, image in images.items():
        width, height = image["width"], image["height"]
        pixels = bytes(image["pixels"])
        rows = b"".join(b"\0" + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
        (textures / (name + ".png")).write_bytes(png)
    for left in range(13):
        name = "empty_pill_pack" if left == 0 else "enhancement_pill_pack" if left == 12 else f"enhancement_pill_pack_{left}"
        texture = "empty_pill_pack" if left == 0 else f"enhancement_pill_pack_{left}"
        model = {"parent": "minecraft:item/generated", "textures": {
            "layer0": f"sound_isolating_eraser:item/{texture}",
            "particle": "sound_isolating_eraser:item/enhancement_pill_pack_particle"}}
        if left == 12:
            model["overrides"] = [{"predicate": {"damage": round(damage / 12 - 0.004, 6)},
                "model": f"sound_isolating_eraser:item/enhancement_pill_pack_{12 - damage}"} for damage in range(1, 12)]
        (models / (name + ".json")).write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(images)} original textures and 13 models")


if __name__ == "__main__":
    export()
