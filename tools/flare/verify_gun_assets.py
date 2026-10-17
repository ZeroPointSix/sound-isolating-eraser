"""Validate the original flare gun's geometry, UVs and finite one-shot animations."""
import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/qihai"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def vector(value, context):
    require(len(value) == 3 and all(isinstance(v, (int, float)) and math.isfinite(v) for v in value), context)


def main():
    model = json.loads((ASSETS / "models/item/flare_gun.json").read_text())
    require(model["parent"] == "builtin/entity", "GeckoLib item must use builtin/entity")
    for hand in ("firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"):
        for key in ("rotation", "translation", "scale"):
            vector(model["display"][hand][key], f"Missing/invalid {hand} {key}")
    geo = json.loads((ASSETS / "geo/flare_gun.geo.json").read_text())["minecraft:geometry"][0]
    require(geo["description"]["texture_width"] == geo["description"]["texture_height"] == 64, "Texture size")
    bones = {bone["name"]: bone for bone in geo["bones"]}
    require(set(bones) == {"root", "frame", "barrel"}, "Gun rig bones")
    count = 0
    for name, bone in bones.items():
        require("parent" not in bone or bone["parent"] in bones, f"Unknown parent {name}")
        for cube in bone.get("cubes", []):
            count += 1
            vector(cube["origin"], "Cube origin")
            vector(cube["size"], "Cube size")
            require(all(v > 0 for v in cube["size"]), "Degenerate cube")
            require(len(cube["uv"]) == 6, "All six faces need explicit UVs")
            for face in cube["uv"].values():
                require(all(0 <= u < u + size <= 64 for u, size in zip(face["uv"], face["uv_size"])), "UV outside atlas")
    require(count == 24, f"Expected original 24 parts, got {count}")
    animations = json.loads((ASSETS / "animations/flare_gun.animation.json").read_text())["animations"]
    require(set(animations) == {"animation.flare_gun." + v for v in ("idle", "fire", "load")}, "Action names")
    for name, animation in animations.items():
        if name.endswith("idle"):
            continue
        require(animation["loop"] is False and 0 < animation["animation_length"] <= .7, "Finite short actions")
        for bone, channels in animation["bones"].items():
            require(bone in bones, "Unknown animation bone")
            for channel, keyframes in channels.items():
                frames = sorted((float(t), value) for t, value in keyframes.items())
                require(frames[0][0] == 0 and frames[-1][0] <= animation["animation_length"], "Animation timing")
                require(frames[0][1] == frames[-1][1] == [0, 0, 0], "Action must return to rest")
                for _, value in frames:
                    vector(value, f"Invalid {bone} {channel}")
    data = (ASSETS / "textures/item/flare_gun.png").read_bytes()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", "PNG signature")
    offset = 8
    while offset < len(data):
        size = struct.unpack_from(">I", data, offset)[0]
        chunk = data[offset + 4:offset + 8 + size]
        require(zlib.crc32(chunk) == struct.unpack_from(">I", data, offset + 8 + size)[0], "PNG CRC")
        offset += size + 12
    require(offset == len(data), "PNG length")
    print("FLARE_GUN_ASSETS_PASS parts=24 bones=3 actions=3 texture=64x64")


if __name__ == "__main__":
    main()
