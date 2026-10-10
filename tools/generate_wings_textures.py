"""Generate every game-facing texture for the Fenglei Wings mod.

Deterministic pixel-art authoring with PIL. Re-runnable:
  python3 art/source/gen_textures.py --out src/main/resources --art art

Outputs (game assets == C package):
  assets/sound_isolating_eraser/textures/item/wind_thunder_wings.png      16x16 inventory icon (folded wings + gold bolt)
  assets/sound_isolating_eraser/textures/item/wind_thunder_wings_32.png   32x32 hi-res icon (docs / large slots)
  assets/sound_isolating_eraser/textures/item/thunder_feather.png         16x16 雷鹏骨羽 material icon
  assets/sound_isolating_eraser/textures/entity/wings.png                 64x64 worn-wing model texture
  assets/sound_isolating_eraser/textures/gui/wings_hud.png                64x64 HUD atlas (4 tiers + cooldown ring + plate)
  assets/sound_isolating_eraser/textures/particle/{wind_ribbon,thunder_arc,impact_ring,trail_dot}.png
  art/A_concept/hud_tiers_mockup.png                             4-state HUD design mockup sheet
Also mirrors the PNG set into art/C_game_assets/ so the three packages stay visibly separate.
"""

import argparse
import math
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------------------------------------------------------------- palette
INK = (43, 47, 54, 255)            # graphite outline
SILVER_HI = (246, 249, 253, 255)
SILVER = (224, 233, 241, 255)
SILVER_SH = (184, 199, 214, 255)
SILVER_DEEP = (150, 170, 190, 255)
GOLD_HI = (240, 206, 122, 255)
GOLD = (217, 168, 62, 255)
GOLD_DK = (138, 100, 40, 255)
CYAN_HI = (182, 243, 250, 255)
CYAN = (127, 220, 232, 255)
CYAN_DK = (46, 127, 148, 255)
WHITE = (255, 255, 255, 255)
PLATE_BG = (20, 24, 30, 200)
PLATE_EDGE = (90, 100, 112, 255)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def line_px(img, x0, y0, x1, y1, c):
    ImageDraw.Draw(img).line([(x0, y0), (x1, y1)], fill=c)


def feather_poly(img, ax, ay, tipx, tipy, w, fill, outline=INK, vein=None):
    """Draw a tapered feather quad from anchor to tip; optional vein line."""
    dx, dy = tipx - ax, tipy - ay
    ln = math.hypot(dx, dy) or 1
    nx, ny = -dy / ln, dx / ln            # normal
    # taper: wide at 35%, pointed at tip
    bx, by = ax + dx * 0.35, ay + dy * 0.35
    pts = [
        (ax + nx * w * 0.55, ay + ny * w * 0.55),
        (bx + nx * w * 0.5, by + ny * w * 0.5),
        (tipx, tipy),
        (bx - nx * w * 0.5, by - ny * w * 0.5),
        (ax - nx * w * 0.55, ay - ny * w * 0.55),
    ]
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=fill, outline=outline)
    if vein:
        vx, vy = ax + dx * 0.18, ay + dy * 0.18
        d.line([(vx, vy), (tipx - dx * 0.06, tipy - dy * 0.06)], fill=vein)


def bolt(img, pts, w_core, w_edge, core=WHITE, edge=GOLD):
    d = ImageDraw.Draw(img)
    for a, b in zip(pts, pts[1:]):
        d.line([a, b], fill=edge, width=w_edge)
    for a, b in zip(pts, pts[1:]):
        d.line([a, b], fill=core, width=w_core)


# ---------------------------------------------------------------- icons
def item_icon(size):
    """Folded wing pair seen from behind: silver fan + center gold bolt."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    s = size / 16.0
    ax, ay = 8.0 * s, 14.0 * s             # anchor bottom center
    hi = size >= 24
    # fan: (angle deg from horizontal, length, width, color)
    fan = ([(25, 13.8, 2.2, SILVER_SH),
            (52, 12.4, 2.4, SILVER),
            (78, 10.8, 2.4, SILVER_HI)] if not hi else
           [(30, 13.5, 2.6, SILVER_SH),
            (55, 12.5, 2.8, SILVER),
            (80, 11.5, 2.8, SILVER_HI)])
    for side in (-1, 1):
        for ang, ln, w, col in fan:
            a = math.radians(ang)
            tipx = ax + side * math.cos(a) * ln * s
            tipy = ay - math.sin(a) * ln * s
            feather_poly(img, ax, ay, tipx, tipy, w * s, col,
                         vein=GOLD_HI if hi else None)
    # center gold bolt, bold zigzag
    pts = [(9.2 * s, 2.0 * s), (6.8 * s, 6.8 * s),
           (9.8 * s, 9.0 * s), (6.9 * s, 14.0 * s)]
    if hi:
        bolt(img, pts, max(1, round(1.2 * s)), max(1, round(2 * s)))
    else:
        d16 = ImageDraw.Draw(img)
        for a_, b_ in zip(pts, pts[1:]):
            d16.line([a_, b_], fill=GOLD_DK)
        for a_, b_ in zip(pts, pts[1:]):
            d16.line([(a_[0], a_[1] - 0.35 * s), (b_[0], b_[1] - 0.35 * s)], fill=GOLD)
    # cyan glints at outer tips
    for side in (-1, 1):
        a = math.radians(25 if not hi else 30)
        tx = round(ax + side * math.cos(a) * (13.8 if not hi else 13.5) * s)
        ty = round(ay - math.sin(a) * (13.8 if not hi else 13.5) * s)
        px(img, tx, ty, CYAN_HI)
    return img


def feather_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    s = size / 16.0
    # single diagonal feather, quill bottom-left -> tip top-right
    feather_poly(img, 3.5 * s, 13.5 * s, 13.5 * s, 2.5 * s, 5.2 * s,
                 SILVER_HI, vein=GOLD_HI)
    # vane shading on the lower side
    feather_poly(img, 3.5 * s, 13.5 * s, 12.4 * s, 3.6 * s, 3.4 * s, SILVER)
    feather_poly(img, 3.5 * s, 13.5 * s, 10.6 * s, 4.6 * s, 2.0 * s, SILVER_SH)
    # gold shaft
    line_px(img, 3.5 * s, 13.5 * s, 13.2 * s, 2.8 * s, GOLD)
    # cyan tip
    d = ImageDraw.Draw(img)
    d.line([(11.5 * s, 4.5 * s), (13.5 * s, 2.5 * s)], fill=CYAN, width=max(1, round(s)))
    px(img, round(13.5 * s), round(2.5 * s), CYAN_HI)
    return img


# ------------------------------------------------------------- entity tex
def entity_wings():
    """64x64 texture matching WingsModel v2 UV layout:
      (0,0,32,24)   covert arm: 3 stacked covert rows (8px each) + feather seams
      (32,0,64,24)  primary vane sheet: horizontal vane w/ barbs + gold shaft + cyan rim
      (0,24,24,32)  secondary vane sheet (4 columns sampled horizontally)
      (24,24,32,32) alula / small feather
      (26,24,32,32) + (32,24,64,32) leading-edge strip (gold rivets)
      (0,32,16,48)  harness plate + cyan gem
      (16,32,32,48) straps / bindings
      (32,32,64,56) folded wing bundle (retracted back state)
      (48,48,64,64) spare white flash cell (blink frame)
    """
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # --- covert band: 3 rows, progressively darker (top bright)
    for row in range(3):
        y0 = row * 8
        col = (SILVER_HI, SILVER, SILVER_SH)[row]
        d.rectangle([0, y0, 31, y0 + 7], fill=col)
        for x in range(0, 32, 3):               # small feather separations
            d.line([(x, y0 + 1), (x + 2, y0 + 7)], fill=INK)
        d.line([(0, y0 + 7), (31, y0 + 7)], fill=INK)
        d.line([(0, y0), (31, y0)], fill=GOLD_HI)  # gold hairline on band top
    # --- primary vane sheet: horizontal vane; barbs run across length
    for x in range(32, 64):
        for y in range(0, 24):
            img.putpixel((x, y), SILVER if (x + y) % 9 < 5 else SILVER_HI)
    for y in range(0, 24, 3):                   # barb separations across length
        d.line([(32, y), (63, y + 1)], fill=SILVER_DEEP)
    d.rectangle([32, 0, 63, 1], fill=CYAN_HI)   # cyan rim along leading edge
    d.line([(32, 11), (63, 11)], fill=GOLD)     # gold shaft along vane center
    d.line([(32, 12), (63, 12)], fill=GOLD_DK)
    for x in range(34, 64, 6):                  # shaft nodes
        px(img, x, 10, GOLD_HI)
    # --- secondary vane sheet (0,24)-(24,32): lighter short vanes
    for y in range(24, 32):
        for x in range(0, 24):
            img.putpixel((x, y), SILVER_SH if (x + y) % 7 < 4 else SILVER)
    for y in range(24, 32, 2):
        d.line([(0, y), (23, y + 1)], fill=SILVER_DEEP)
    d.line([(0, 30), (23, 30)], fill=GOLD)
    # --- alula (24,24)-(32,32): small dark vane with cyan tip
    d.rectangle([24, 24, 31, 31], fill=SILVER_DEEP)
    d.rectangle([28, 24, 31, 26], fill=CYAN)
    # --- leading edge strip (32,24)-(64,32): gold band with rivets
    d.rectangle([32, 24, 63, 31], fill=GOLD)
    d.rectangle([32, 24, 63, 25], fill=GOLD_HI)
    d.rectangle([32, 30, 63, 31], fill=GOLD_DK)
    for x in range(35, 64, 7):
        px(img, x, 27, GOLD_DK)
    # --- harness plate + straps
    d.rectangle([0, 32, 15, 47], fill=GOLD_DK)
    d.rectangle([1, 33, 14, 46], fill=GOLD)
    d.polygon([(8, 35), (12, 39), (8, 43), (4, 39)], fill=CYAN_HI, outline=CYAN_DK)
    d.point([(7, 38)], fill=WHITE)
    d.rectangle([16, 32, 31, 47], fill=(58, 44, 30, 255))        # leather strap
    for y in (35, 41):                                          # stitching
        d.line([(16, y), (31, y)], fill=GOLD_DK)
    d.rectangle([24, 36, 27, 43], fill=GOLD)                    # buckle
    # --- folded wing bundle (32,32)-(64,56)
    for side in (-1, 1):
        feather_poly(img, 48, 52, 48 + side * 13, 35, 4.5, SILVER_HI, vein=GOLD_HI)
        feather_poly(img, 48, 52, 48 + side * 9, 34, 3.5, SILVER, vein=GOLD)
        feather_poly(img, 48, 52, 48 + side * 5, 33, 2.5, SILVER_SH, vein=GOLD_DK)
    d.polygon([(48, 50), (51, 53), (48, 56), (45, 53)], fill=GOLD, outline=GOLD_DK)
    # --- spare white flash cell (48,48)-(64,64)
    d.ellipse([50, 50, 62, 62], fill=WHITE)
    return img


# ------------------------------------------------------------- hud atlas
def hud_atlas():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def mini_wing(x0, y0, swept=0.0, col=CYAN_HI):
        # two wing strokes anchored at slot center-bottom
        ax, ay = x0 + 8, y0 + 12
        for side in (-1, 1):
            ex = ax + side * 6
            ey = ay - 6 - swept * 2
            mx = ax + side * (3 - swept)
            my = ay - 4
            d.polygon([(ax + side, ay), (mx, my), (ex, ey), (ex - side, ey + 2), (mx + side, my + 2)], fill=col, outline=INK)

    def chevrons(x0, n):
        for i in range(n):
            bx = x0 + 11 + i * 2
            d.polygon([(bx, 4), (bx + 2, 8), (bx, 12)], outline=GOLD_HI)

    # tier icons 16x16 each at y=0
    mini_wing(0, 0, 0.0)                                  # hover: flat spread
    d.rectangle([6, 13, 10, 14], fill=CYAN_DK)            # 'hold' bar under hover
    mini_wing(16, 0, 0.4)                                 # normal: level
    chevrons(16, 1)
    mini_wing(32, 0, 1.0)                                 # boost: swept back
    chevrons(32, 2)
    mini_wing(48, 0, 1.0, col=WHITE)                      # extreme: swept + bolt
    bolt(img, [(57, 3), (54, 8), (58, 10), (55, 15)], 1, 1)
    chevrons(48, 0)

    # wing emblem (equipped/idle) at (0,16)
    mini_wing(0, 16, 0.0, col=SILVER_HI)
    # cooldown ring at (16,16): thin ring + tick marks
    d.ellipse([17, 17, 30, 30], outline=WHITE)
    for a in range(0, 360, 45):
        r1, r2 = 6, 7.5
        x1 = 23.5 + math.cos(math.radians(a)) * r1
        y1 = 23.5 + math.sin(math.radians(a)) * r1
        x2 = 23.5 + math.cos(math.radians(a)) * r2
        y2 = 23.5 + math.sin(math.radians(a)) * r2
        d.line([(x1, y1), (x2, y2)], fill=WHITE)
    # ring fill sector helper at (32,16): full dim disc (client tints/alphas it)
    d.ellipse([33, 17, 46, 30], fill=(255, 255, 255, 90))
    # charged orb at (48,16): 雷力充盈 gold-cyan burst
    d.polygon([(56, 17), (59, 23), (56, 29), (53, 23)], fill=GOLD_HI, outline=GOLD_DK)
    d.point([(56, 23)], fill=CYAN_HI)

    # widget backplate 64x16 at (0,32): dark plate + 4 slot separators + gold footer accent
    d.rounded_rectangle([0, 32, 63, 47], radius=3, fill=PLATE_BG, outline=PLATE_EDGE)
    for i in (16, 32, 48):
        d.line([(i, 34), (i, 45)], fill=(255, 255, 255, 40))
    d.line([(2, 46), (61, 46)], fill=GOLD_DK)
    return img


# ------------------------------------------------------------- particles
def particle_wind_ribbon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i, a in enumerate((70, 120, 200, 255, 200, 120, 70)):
        d.line([(i + 2, 8), (i + 5, 8)], fill=CYAN_HI[:3] + (a,))
    d.line([(3, 8), (12, 8)], fill=WHITE)
    return img.filter(ImageFilter.GaussianBlur(0.6))


def particle_thunder_arc():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    bolt(img, [(4, 1), (7, 6), (6, 9), (11, 15)], 1, 1, core=WHITE, edge=GOLD_HI)
    return img


def particle_impact_ring():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, 14, 14], outline=CYAN_HI)
    d.ellipse([2, 2, 13, 13], outline=WHITE)
    return img


def particle_trail_dot():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([5, 5, 10, 10], fill=CYAN_HI[:3] + (220,))
    d.ellipse([6, 6, 9, 9], fill=WHITE)
    return img.filter(ImageFilter.GaussianBlur(0.5))


# ------------------------------------------------------------- A-pack HUD sheet
def drumstick(d, x, y, lit):
    """Rough vanilla-hunger-drumstick glyph (10x9 area)."""
    c = (120, 72, 30, 255) if lit else (60, 50, 45, 200)
    d.rectangle([x + 1, y + 1, x + 6, y + 4], fill=c)          # meat
    d.rectangle([x + 6, y + 4, x + 8, y + 7], fill=c)          # bone
    d.rectangle([x + 2, y + 5, x + 5, y + 8], fill=c)


def hud_mockup(font_path):
    tiers = ["HOVER", "CRUISE", "BOOST", "STORM"]
    names_cn = ["悬停", "御风", "疾风", "风雷"]
    atlas = hud_atlas()
    W, H = 240, 132
    sheet = Image.new("RGBA", (W * 4 + 50, H + 46), (24, 26, 30, 255))
    f_big = ImageFont.truetype(font_path, 16)
    f_sm = ImageFont.truetype(font_path, 10)
    d = ImageDraw.Draw(sheet)
    d.text((10, 6), "风雷翅 HUD · 四档位（消耗=饥饿值，无灵力条）", font=f_big, fill=(232, 238, 244))
    d.text((10, 26), "右下角组件：档位图标×4 + 雷遁冷却环 + 充能珠；饥饿条保持原版样式，消耗时自然抖动", font=f_sm, fill=(150, 160, 170))
    for i, (en, cn) in enumerate(zip(tiers, names_cn)):
        ox = 10 + i * (W + 10)
        oy = 44
        d.rounded_rectangle([ox, oy, ox + W, oy + H], radius=4, fill=(38, 42, 48), outline=(70, 78, 88))
        # fake game strip: hearts (red) + hunger (brown) at bottom
        for h in range(10):
            d.rectangle([ox + 8 + h * 9, oy + H - 34, ox + 14 + h * 9, oy + H - 28], fill=(150, 30, 27))
        for h in range(10):
            drumstick(d, ox + W - 92 + h * 9, oy + H - 34, h < (10 - i * 2))
        # widget: backplate + 4 tier icons (2x for mockup legibility) + blink slot
        wx, wy = ox + W - 156, oy + H - 70
        plate = atlas.crop((0, 32, 64, 48)).resize((112, 28), Image.NEAREST)
        sheet.paste(plate, (wx - 8, wy - 4))
        for t in range(4):
            icon = atlas.crop((t * 16, 0, t * 16 + 16, 16)).resize((26, 26), Image.NEAREST)
            if t != i:
                icon = icon.point(lambda p: p // 3)
            sheet.paste(icon, (wx + t * 27, wy), icon)
            if t == i:
                d.rectangle([wx + t * 27 - 2, wy - 2, wx + t * 27 + 27, wy + 27], outline=GOLD_HI)
                d.rectangle([wx + t * 27, wy + 28, wx + t * 27 + 25, wy + 30], fill=GOLD)
        # blink slot + cooldown ring (demo: charged orb on tier 3)
        bx = wx + 114
        d.rounded_rectangle([bx, wy, bx + 32, wy + 32], radius=4, fill=(30, 34, 40), outline=PLATE_EDGE)
        bolt(sheet, [(bx + 19, wy + 5), (bx + 12, wy + 14), (bx + 18, wy + 17), (bx + 11, wy + 27)], 2, 3)
        ring = atlas.crop((16, 16, 32, 32)).resize((36, 36), Image.NEAREST)
        sheet.paste(ring, (bx - 2, wy - 2), ring)
        if i == 3:
            orb = atlas.crop((48, 16, 64, 32)).resize((30, 30), Image.NEAREST)
            sheet.paste(orb, (wx - 42, wy), orb)
        d.text((ox + 8, oy + 8), f"{cn} {en}", font=f_sm, fill=(240, 206, 122) if i == 3 else (180, 190, 200))
        d.text((ox + 8, oy + H - 16), f"饥饿消耗档 {i} · 剩余饥饿 {10 - i * 2}/10", font=f_sm, fill=(140, 148, 156))
    return sheet


# ------------------------------------------------------------------ main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="src/main/resources")
    ap.add_argument("--art", default="art")
    ap.add_argument("--font", default=str(Path(__file__).parent / "fonts" / "NotoSansSC.ttf"))
    args = ap.parse_args()
    out = Path(args.out)
    tex = out / "assets/sound_isolating_eraser/textures"

    files = {
        tex / "item/wind_thunder_wings.png": item_icon(16),
        tex / "item/wind_thunder_wings_32.png": item_icon(32),
        tex / "item/thunder_feather.png": feather_icon(16),
        tex / "entity/wings.png": entity_wings(),
        tex / "gui/wings_hud.png": hud_atlas(),
        tex / "particle/wind_ribbon.png": particle_wind_ribbon(),
        tex / "particle/thunder_arc.png": particle_thunder_arc(),
        tex / "particle/impact_ring.png": particle_impact_ring(),
        tex / "particle/trail_dot.png": particle_trail_dot(),
    }
    for path, img in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print("wrote", path, img.size)

    mock = hud_mockup(args.font)
    a_dir = Path(args.art) / "A_concept"
    a_dir.mkdir(parents=True, exist_ok=True)
    mock.save(a_dir / "hud_tiers_mockup.png")
    print("wrote", a_dir / "hud_tiers_mockup.png", mock.size)

    # mirror PNG assets into art/C_game_assets for the split manifest
    c_dir = Path(args.art) / "C_game_assets"
    for path in files:
        rel = path.relative_to(out / "assets/sound_isolating_eraser")
        dest = c_dir / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, dest)
    print("mirrored", len(files), "files ->", c_dir)


if __name__ == "__main__":
    main()
