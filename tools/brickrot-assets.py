"""Validate supplied art without generating or substituting any model or texture."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "src/main/resources/assets/sound_isolating_eraser"
MODELS = ["head", "neck", "neck_breached", "segment_grey", "segment_red", "segment_tail"]
ANIMATIONS = ["head_idle", "head_charge", "head_bite", "head_scan", "head_stagger", "head_emerge",
              "head_death", "segment_crawl", "red_sweep", "tail_pulse", "segment_death"]
FILES = [f"geo/brickrot_{name}.geo.json" for name in MODELS] + [
    "animations/brickrot.animation.json", "textures/entity/brickrot.png",
    "textures/entity/brickrot_glowmask.png", "textures/item/brickrot_spawn_egg.png",
    "models/item/brickrot_spawn_egg.json"]


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("Invalid PNG header")
    return struct.unpack(">II", data[16:24])


def validate(files):
    missing = [name for name in FILES if name not in files]
    if missing:
        raise ValueError("Supplied Brickrot art is missing: " + ", ".join(missing))
    for name in MODELS:
        geo = json.loads(files[f"geo/brickrot_{name}.geo.json"])
        geometries = geo["minecraft:geometry"]
        if not geometries or not geometries[0].get("bones"):
            raise ValueError("Empty geometry: " + name)
        if any(g["description"].get("texture_width") != 256
               or g["description"].get("texture_height") != 256 for g in geometries):
            raise ValueError("Unexpected atlas size: " + name)
    animation = json.loads(files["animations/brickrot.animation.json"])["animations"]
    for name in ANIMATIONS:
        if "animation.brickrot." + name not in animation:
            raise ValueError("Animation missing: " + name)
    for name in ["brickrot.png", "brickrot_glowmask.png"]:
        if png_size(files["textures/entity/" + name]) != (256, 256):
            raise ValueError("Unexpected texture dimensions: " + name)
    if png_size(files["textures/item/brickrot_spawn_egg.png"]) != (16, 16):
        raise ValueError("Unexpected spawn egg texture dimensions")
    json.loads(files["models/item/brickrot_spawn_egg.json"])


def remap_namespace(value):
    if isinstance(value, dict):
        return {key: remap_namespace(item) for key, item in value.items()}
    if isinstance(value, list):
        return [remap_namespace(item) for item in value]
    if isinstance(value, str) and value.startswith("qihai:"):
        return "sound_isolating_eraser:" + value[len("qihai:"):]
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["verify", "import"])
    parser.add_argument("archive", nargs="?")
    args = parser.parse_args()
    if args.command == "import":
        if not args.archive:
            parser.error("import requires the original resource archive")
        with zipfile.ZipFile(args.archive) as archive:
            contents = {}
            for name in FILES:
                matches = [i for i in archive.infolist() if i.filename.endswith("/assets/qihai/" + name)
                           or i.filename == "assets/qihai/" + name]
                if len(matches) != 1 or matches[0].file_size > 10_000_000:
                    raise ValueError("Missing, duplicate, or oversized source: " + name)
                contents[name] = archive.read(matches[0])
        validate(contents)
        for name in contents:
            if name.startswith("models/"):
                contents[name] = (json.dumps(remap_namespace(json.loads(contents[name])),
                                            ensure_ascii=False, indent=2) + "\n").encode()
        # Only the allowlisted asset paths are written; no archive paths are extracted.
        for name, data in contents.items():
            target = DEST / name
            if target.exists() and target.read_bytes() != data:
                raise ValueError("Refusing to overwrite different existing art: " + name)
        for name, data in contents.items():
            target = DEST / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    contents = {name: (DEST / name).read_bytes() for name in FILES if (DEST / name).is_file()}
    validate(contents)
    for name, data in contents.items():
        print(hashlib.sha256(data).hexdigest(), name)
    print("Brickrot art verified: 6 geometries, 11 animations, 3 textures, spawn egg model")


if __name__ == "__main__":
    main()
