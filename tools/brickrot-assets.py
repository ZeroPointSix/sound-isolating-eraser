"""Validate supplied Brickrot art without generating or substituting model or texture bytes."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "src/main/resources/assets/sound_isolating_eraser"
ORIGINAL_ARCHIVE_SHA256 = "0bc90e45a7da2ae09cc7748905b6aa3c43ac415653a3b6a5743afeaec674a964"
MODELS = ["head", "neck", "neck_breached", "segment_grey", "segment_red", "segment_tail"]
ANIMATIONS = ["head_idle", "head_charge", "head_bite", "head_scan", "head_stagger", "head_emerge",
              "head_death", "segment_crawl", "red_sweep", "tail_pulse", "segment_death"]
SPAWN_EGG_MODEL = "models/item/brickrot_spawn_egg.json"
ORIGINAL_FILE_SHA256 = {
    "geo/brickrot_head.geo.json": "cebbc944e19c4e10433ea84ef2c420da79e375df3c3b9dc1203a941589dd3fcd",
    "geo/brickrot_neck.geo.json": "56bb6b252e432d406a6e361939c38fe04d92124813ab026029c3eabfdaae08cf",
    "geo/brickrot_neck_breached.geo.json": "26c16193c0da86bc8a21e9de2fd42b91e691b2cd6c8ba080345a0c373b858638",
    "geo/brickrot_segment_grey.geo.json": "08b0d244720d07f5935cb278c8b0974cf94a775619c510b6f7a8b12833d1a22d",
    "geo/brickrot_segment_red.geo.json": "663f4ec9845a279a2cc9e5602355586cb0237897ccb557590cd82b1f8981e82f",
    "geo/brickrot_segment_tail.geo.json": "dae77423c7eb810018fbcba2c7a6e542ba5a5cedae35f3b9365108ea4d3a0191",
    "animations/brickrot.animation.json": "b965c627fa86fb5a59592f0590da75d30844c773a257a56a7a769c718aabdcc2",
    "textures/entity/brickrot.png": "549eb882af4352ed5a3ceecc1842bb734133047a7327baa1888cb58fa89c281d",
    "textures/entity/brickrot_glowmask.png": "2771b6e82581f960c1d6d89f44a8c0f99ab402ca68d1d5eb58b19616ca05f636",
    "textures/item/brickrot_spawn_egg.png": "56cd4c535ad675983cfe868386d678d14c413859a84b02ec6f63e0fd97556e4a",
}
FILES = [f"geo/brickrot_{name}.geo.json" for name in MODELS] + [
    "animations/brickrot.animation.json",
    "textures/entity/brickrot.png",
    "textures/entity/brickrot_glowmask.png",
    "textures/item/brickrot_spawn_egg.png",
    SPAWN_EGG_MODEL,
]


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_archive_hash(archive):
    actual = sha256_file(archive)
    if actual != ORIGINAL_ARCHIVE_SHA256:
        raise ValueError(
            "Original Brickrot archive SHA-256 mismatch: "
            + actual + " != " + ORIGINAL_ARCHIVE_SHA256
        )


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("Invalid PNG header")
    return struct.unpack(">II", data[16:24])


def validate_original_hashes(files):
    for name, expected in ORIGINAL_FILE_SHA256.items():
        actual = hashlib.sha256(files[name]).hexdigest()
        if actual != expected:
            raise ValueError(
                "Original Brickrot art SHA-256 mismatch for "
                + name + ": " + actual + " != " + expected
            )


def validate_spawn_egg_model(data):
    model = json.loads(data)
    if model.get("parent") != "minecraft:item/template_spawn_egg":
        raise ValueError("Brickrot spawn egg must use minecraft:item/template_spawn_egg")


def validate(files, require_spawn_egg_parent=True):
    missing = [name for name in FILES if name not in files]
    if missing:
        raise ValueError("Supplied Brickrot art is missing: " + ", ".join(missing))
    for name in MODELS:
        geo = json.loads(files[f"geo/brickrot_{name}.geo.json"])
        geometries = geo["minecraft:geometry"]
        if not geometries or not geometries[0].get("bones"):
            raise ValueError("Empty geometry: " + name)
        if any(
            geometry["description"].get("texture_width") != 256
            or geometry["description"].get("texture_height") != 256
            for geometry in geometries
        ):
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
    validate_original_hashes(files)
    if require_spawn_egg_parent:
        validate_spawn_egg_model(files[SPAWN_EGG_MODEL])
    else:
        json.loads(files[SPAWN_EGG_MODEL])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["verify", "import"])
    parser.add_argument("archive", nargs="?")
    args = parser.parse_args()
    if args.command == "import":
        if not args.archive:
            parser.error("import requires the original resource archive")
        verify_archive_hash(args.archive)
        with zipfile.ZipFile(args.archive) as archive:
            contents = {}
            for name in FILES:
                matches = [
                    item for item in archive.infolist()
                    if item.filename.endswith("/assets/qihai/" + name)
                    or item.filename == "assets/qihai/" + name
                ]
                if len(matches) != 1 or matches[0].file_size > 10_000_000:
                    raise ValueError("Missing, duplicate, or oversized source: " + name)
                contents[name] = archive.read(matches[0])
        validate(contents, require_spawn_egg_parent=False)
        # Forge 1.20.1 uses the vanilla two-layer egg, so the source item model is adapted.
        contents[SPAWN_EGG_MODEL] = (
            json.dumps({"parent": "minecraft:item/template_spawn_egg"}, indent=2) + "\n"
        ).encode()
        validate(contents)
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
