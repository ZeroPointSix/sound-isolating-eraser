#!/usr/bin/env python3
"""Generate the mod's textures procedurally.

Run: python3 tools/generate_textures.py
Outputs into src/main/resources/assets/sound_isolating_eraser/textures/.

Design targets from the Notion one-shot spec:
- eraser_wall.png: an almost-invisible pale film (85-92% transparent), faint
  white-blue tint, edges slightly more visible than the body, weak vertical
  streaks. No glow, no magic particles.
- eraser_mark.png: a grey-white chalk-like scratch, hugging the surface, grainy
  edges, no glow.
- sound_isolating_eraser.png (32x32): small white eraser, cold white body, a
  very light blue paper sleeve around the middle, slightly worn tip.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/sound_isolating_eraser/textures"

random.seed(20261005)


def noise(a, b):
    return random.randint(a, b)


def make_wall():
    """16x16 translucent film."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            # body: barely-there pale blue white
            a = noise(14, 24)
            r, g, b = 232, 242, 252
            # edges slightly more visible
            if x in (0, 15) or y in (0, 15):
                a = noise(28, 44)
            # faint vertical streaks
            if x in (4, 9, 13) and random.random() < 0.7:
                a = min(255, a + noise(8, 18))
            # a few soft speckles
            if random.random() < 0.05:
                a = min(255, a + noise(10, 22))
            px[x, y] = (r, g, b, a)
    return img


def make_mark():
    """16x16 chalk scratch on transparent background."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    chalk = (250, 246, 248)

    # two irregular diagonal strokes, chalk-line style
    stroke1 = [(2, 12), (5, 9), (8, 8), (11, 6), (14, 4)]
    stroke2 = [(3, 5), (6, 8), (9, 9), (12, 12), (14, 13)]

    def chalk_line(points, width_alpha):
        for i in range(len(points) - 1):
            x0, y0 = points[i]
            x1, y1 = points[i + 1]
            steps = max(abs(x1 - x0), abs(y1 - y0)) * 3
            for s in range(steps + 1):
                t = s / steps
                cx = x0 + (x1 - x0) * t
                cy = y0 + (y1 - y0) * t
                # jitter the chalk edge
                jx = cx + random.uniform(-0.9, 0.9)
                jy = cy + random.uniform(-0.9, 0.9)
                for ox in (-0.5, 0.5):
                    for oy in (-0.5, 0.5):
                        px_ = int(jx + ox)
                        py_ = int(jy + oy)
                        if 0 <= px_ < 16 and 0 <= py_ < 16:
                            a = img.getpixel((px_, py_))[3]
                            img.putpixel(
                                (px_, py_),
                                (chalk[0], chalk[1], chalk[2],
                                 min(255, a + noise(*width_alpha))))

    chalk_line(stroke1, (150, 235))
    chalk_line(stroke2, (120, 210))
    # grainy speckles around the strokes
    for _ in range(45):
        x, y = random.randint(1, 14), random.randint(1, 14)
        if img.getpixel((x, y))[3] == 0 and random.random() < 0.6:
            img.putpixel((x, y), (chalk[0], chalk[1], chalk[2], noise(50, 140)))
    return img


def make_eraser():
    """32x32 item icon: small white eraser with a pale blue paper sleeve."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)

    body = (250, 251, 252)
    shade = (229, 233, 238)
    worn = (216, 221, 228)
    sleeve = (203, 222, 240)
    sleeve_edge = (176, 203, 230)

    # rotated rounded body, drawn on a larger layer then rotated
    layer = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.rounded_rectangle([8, 14, 40, 30], radius=5, fill=body)
    # bottom shading
    ld.rounded_rectangle([8, 26, 40, 30], radius=5, fill=shade)
    ld.rectangle([8, 14, 40, 26], fill=(0, 0, 0, 0))
    ld.rounded_rectangle([8, 14, 40, 30], radius=5, fill=body)
    for x in range(8, 41):
        for y in range(26, 31):
            if layer.getpixel((x, y))[3]:
                ld.point((x, y), fill=shade)
    # worn tip (right end, slightly darker and rounded)
    for x in range(35, 41):
        for y in range(14, 31):
            if layer.getpixel((x, y))[3]:
                ld.point((x, y), fill=worn)
    # paper sleeve band across the middle
    ld.rectangle([20, 13, 27, 31], fill=sleeve)
    ld.rectangle([20, 13, 21, 31], fill=sleeve_edge)
    ld.rectangle([26, 13, 27, 31], fill=sleeve_edge)
    # a couple of crumb pixels near the worn tip
    ld.point((41, 20), fill=worn)
    ld.point((42, 24), fill=worn)
    ld.point((41, 27), fill=shade)

    layer = layer.rotate(-22, resample=Image.BICUBIC, expand=False)
    img.alpha_composite(layer, (-8, -6))

    # sharpen to 32x32 pixel feel: quantize alpha
    px = img.load()
    for y in range(32):
        for x in range(32):
            r, g, b, a = px[x, y]
            if a < 30:
                px[x, y] = (0, 0, 0, 0)
            else:
                px[x, y] = (r, g, b, 255 if a > 200 else a)
    return img


def main():
    (TEX / "block").mkdir(parents=True, exist_ok=True)
    (TEX / "item").mkdir(parents=True, exist_ok=True)
    make_wall().save(TEX / "block/eraser_wall.png")
    make_mark().save(TEX / "block/eraser_mark.png")
    make_eraser().save(TEX / "item/sound_isolating_eraser.png")
    for p in sorted(TEX.rglob("*.png")):
        print(p.relative_to(ROOT))


if __name__ == "__main__":
    main()
