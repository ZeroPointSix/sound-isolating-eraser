"""Rebuild the small, nearest-neighbour pixel-art flare textures."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/qihai/textures"


def save(image, path):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


for color, fill in {"red": "#d93832", "yellow": "#f3d750", "green": "#4ac56b"}.items():
    image = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(image)
    d.rectangle((6, 2, 10, 13), fill="#202b31")
    d.rectangle((7, 3, 9, 10), fill=fill)
    d.rectangle((7, 3, 7, 9), fill="#fff0b0")
    d.rectangle((5, 11, 11, 13), fill="#c0a367")
    d.rectangle((6, 12, 10, 12), fill="#f3d897")
    save(image, f"item/flare_{color}.png")

image = Image.new("RGBA", (16, 16))
d = ImageDraw.Draw(image)
d.polygon([(2, 3), (14, 3), (14, 7), (9, 7), (8, 14), (4, 14), (5, 8), (2, 7)], fill="#222c33")
d.rectangle((3, 4, 13, 5), fill="#df643c")
d.rectangle((3, 6, 8, 7), fill="#a13727")
d.polygon([(6, 9), (8, 9), (7, 13), (5, 13)], fill="#72564a")
d.point((12, 3), fill="#f4d58a")
save(image, "item/flare_gun.png")

image = Image.new("RGBA", (32, 32), "#885e39")
d = ImageDraw.Draw(image)
for y in range(0, 32, 8):
    d.rectangle((0, y, 31, y + 1), fill="#4b3527")
    d.line((2, y + 4, 28, y + 4), fill="#a8794a")
for a in (5, 24):
    d.rectangle((a, 0, a + 2, 31), fill="#344c42")
    d.rectangle((0, a, 31, a + 2), fill="#344c42")
    for b in (5, 24):
        d.rectangle((a, b, a + 2, b + 2), fill="#c9c8a1")
d.rectangle((12, 10, 19, 21), fill="#d9d9bd")
d.line((15, 12, 15, 19), fill="#457552", width=2)
d.line((13, 15, 18, 15), fill="#457552", width=2)
save(image, "block/supply_crate.png")

image = Image.new("RGBA", (32, 32), "#982a23")
d = ImageDraw.Draw(image)
d.rectangle((0, 0, 7, 23), fill="#d54b37")
d.rectangle((8, 0, 23, 7), fill="#ffe6a3")
d.rectangle((0, 22, 7, 31), fill="#544436")
save(image, "entity/flare_decoy.png")

image = Image.new("RGBA", (128, 128), "#e0e2c6")
d = ImageDraw.Draw(image)
for x in range(0, 120, 10):
    if x % 20 == 0:
        d.rectangle((x, 0, x + 9, 127), fill="#578d6c")
    d.line((x, 0, x, 127), fill="#a6b49b")
d.rectangle((120, 0, 127, 127), fill="#4b5246")
save(image, "entity/flare_parachute.png")
