"""Import the supplied art pack, keeping only resources supported by Forge 1.20.1."""

import argparse
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2] / "src/main/resources/assets/sound_isolating_eraser"
    allowed = ["geo/neon_tumor.geo.json", "animations/neon_tumor.animation.json",
               "models/item/neon_tumor_spawn_egg.json", "textures/item/neon_tumor_spawn_egg.png"]
    for color in ("red", "blue", "purple"):
        allowed.extend([f"textures/entity/neon_tumor_{color}.png",
                        f"textures/entity/neon_tumor_{color}_glowmask.png"])
    with ZipFile(args.archive) as archive:
        for relative in allowed:
            matches = [name for name in archive.namelist() if name.endswith("/assets/qihai/" + relative)]
            if len(matches) != 1:
                raise ValueError(f"Expected exactly one {relative}, found {len(matches)}")
            data = archive.read(matches[0])
            if relative.endswith(".json"):
                data = data.replace(b"qihai:", b"sound_isolating_eraser:")
                json.loads(data)
            dest = root / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
    # Temporary corrosion badge: eroded armor with contrasting acid droplets.
    icon = Image.new("RGBA", (18, 18))
    draw = ImageDraw.Draw(icon)
    draw.polygon([(3, 3), (6, 2), (8, 4), (10, 4), (12, 2), (15, 3), (16, 7),
                  (13, 8), (13, 14), (5, 14), (5, 8), (2, 7)], fill="#B8326A", outline="#56203F")
    draw.line([(6, 5), (6, 11), (11, 11)], fill="#F28BAC", width=1)
    draw.rectangle((11, 6, 14, 8), fill=(0, 0, 0, 0))
    draw.rectangle((10, 8, 12, 10), fill=(0, 0, 0, 0))
    draw.rectangle((12, 11, 13, 13), fill="#76E8DA")
    draw.point((12, 15), fill="#76E8DA")
    path = root / "textures/mob_effect/corroded.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    icon.save(path)
    print(f"Imported {len(allowed)} original resources and one 18x18 placeholder icon")


if __name__ == "__main__":
    main()
