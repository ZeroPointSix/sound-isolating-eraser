"""Rebuild the original T4 flare pistol assets; no third-party artwork is used."""
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/qihai"
PALETTE = [(181, 48, 42), (46, 53, 56), (122, 140, 143), (198, 159, 78),
           (28, 32, 33), (225, 222, 197), (19, 22, 23), (231, 104, 61)]
# name, lower corner, upper corner, material. The open bore is four separate rails.
PARTS = [
    ("grip", [6, 0, 8.5], [10, 6.5, 12], 4),
    ("grip_panel_l", [5.8, 1, 9], [6, 5.5, 11.5], 1),
    ("grip_panel_r", [10, 1, 9], [10.2, 5.5, 11.5], 1),
    ("heel", [5.7, 0, 8.3], [10.3, 1, 12.3], 2),
    ("frame", [5.7, 6, 6], [10.3, 8.5, 12], 1),
    ("breech", [5.5, 8, 8], [10.5, 12, 11], 3),
    ("barrel_top", [5.5, 11, 0], [10.5, 12, 8], 0),
    ("barrel_bottom", [5.5, 8, 0], [10.5, 9, 8], 0),
    ("barrel_left", [5.5, 9, 0], [6.5, 11, 8], 0),
    ("barrel_right", [9.5, 9, 0], [10.5, 11, 8], 0),
    ("bore", [6.5, 9, 7.9], [9.5, 11, 8], 6),
    ("muzzle_top", [5.3, 11, -0.4], [10.7, 12.2, 0.5], 2),
    ("muzzle_bottom", [5.3, 7.8, -0.4], [10.7, 9, 0.5], 2),
    ("muzzle_left", [5.3, 9, -0.4], [6.5, 11, 0.5], 2),
    ("muzzle_right", [9.5, 9, -0.4], [10.7, 11, 0.5], 2),
    ("sight", [7.5, 12, 0.6], [8.5, 13, 2.2], 5),
    ("latch", [7.3, 12, 8.3], [8.7, 12.7, 11.5], 2),
    ("hinge_l", [5.1, 7.3, 6.3], [5.7, 8.7, 7.7], 3),
    ("hinge_r", [10.3, 7.3, 6.3], [10.9, 8.7, 7.7], 3),
    ("guard_front", [7.2, 3.8, 4.8], [8.8, 6.2, 5.5], 2),
    ("guard_bottom", [7.2, 3.5, 4.8], [8.8, 4.2, 9], 2),
    ("trigger", [7.6, 5, 7], [8.4, 6.2, 7.7], 3),
    ("id_plate_l", [5.45, 9.3, 4.3], [5.5, 10.7, 6.8], 5),
    ("id_plate_r", [10.5, 9.3, 4.3], [10.55, 10.7, 6.8], 5),
]


def tile(material):
    return (material % 4 * 16, material // 4 * 16)


def png(path):
    rows = []
    for y in range(64):
        row = bytearray([0])
        for x in range(64):
            index = min(7, x // 16 + 4 * (y // 16))
            local_x, local_y = x % 16, y % 16
            shade = 16 if local_y < 2 else -18 if local_y > 13 else 0
            shade += 5 if (local_x * 7 + local_y * 11) % 17 == 0 else 0
            if index == 4 and local_y % 4 == 0:
                shade -= 24
            if index == 5 and 4 <= local_x <= 11 and local_y in (5, 7, 10):
                shade -= 120
            row.extend(max(0, min(255, c + shade)) for c in PALETTE[index])
            row.append(255)
        rows.append(row)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 64, 64, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))


def generate():
    elements = []
    for name, lower, upper, material in PARTS:
        u, v = tile(material)
        uv = [u / 4, v / 4, (u + 16) / 4, (v + 16) / 4]
        elements.append({"name": name, "from": lower, "to": upper,
                         "faces": {face: {"texture": "#body", "uv": uv}
                                   for face in ("north", "south", "east", "west", "up", "down")}})
    model = {
        "credit": "Original T4 emergency flare pistol, ZeroPointSix",
        "ambientocclusion": False,
        "textures": {"body": "qihai:item/flare_gun", "particle": "qihai:item/flare_gun"},
        "display": {
            "gui": {"rotation": [25, 140, -8], "translation": [0, 1, 0], "scale": [0.95] * 3},
            "ground": {"rotation": [0, 0, 90], "translation": [0, 2.5, 0], "scale": [0.5] * 3},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 1, 0], "scale": [0.8] * 3},
            "firstperson_righthand": {"rotation": [0, 0, -5], "translation": [0.5, 1.4, 1.4], "scale": [0.85] * 3},
            "firstperson_lefthand": {"rotation": [0, 0, 5], "translation": [0.5, 1.4, 1.4], "scale": [0.85] * 3},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1], "scale": [0.8] * 3},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1], "scale": [0.8] * 3},
        },
        "elements": elements,
    }
    target = ASSETS / "models/item/flare_gun.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    png(ASSETS / "textures/item/flare_gun.png")


if __name__ == "__main__":
    generate()
