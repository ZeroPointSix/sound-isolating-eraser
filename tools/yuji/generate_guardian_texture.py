"""Derive the city guardian's white robe from the existing Yuchi texture."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
source = ROOT / "src/main/resources/assets/qihai/textures/entity/yuchi.png"
target = source.with_name("yuchi_white_robe.png")
image = Image.open(source).convert("RGBA")
if image.size != (128, 128):
    raise ValueError("Unexpected Yuchi texture dimensions")
# Torso, sleeves/legs, and waist UV islands. The face occupies x=0..31 and is unchanged.
for left, top, right, bottom in ((32, 0, 56, 16), (56, 0, 72, 16), (72, 0, 100, 11)):
    for y in range(top, bottom):
        for x in range(left, right):
            r, g, b, alpha = image.getpixel((x, y))
            value = min(245, 185 + (r + g + b) // 12)
            image.putpixel((x, y), (value, value, min(255, value + 3), alpha))
image.save(target)
print("Generated white-robe guardian texture; original texture unchanged")
