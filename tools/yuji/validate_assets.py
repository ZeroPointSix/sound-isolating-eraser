"""Validate every supplied runtime reference using only the Python standard library."""
import json
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/qihai"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_keyframes(name, animation):
    duration = animation["animation_length"]
    for bone, channels in animation.get("bones", {}).items():
        for channel, timeline in channels.items():
            if isinstance(timeline, dict):
                for timestamp in timeline:
                    require(0 <= float(timestamp) <= duration,
                            f"Keyframe timing: {name}/{bone}/{channel}@{timestamp} outside [0, {duration}]")


def texture(reference):
    namespace, name = reference.split(":", 1)
    require(namespace == "qihai", f"Unexpected texture namespace: {reference}")
    path = ASSETS / "textures" / (name + ".png")
    data = path.read_bytes()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {path}")
    return struct.unpack(">II", data[16:24])


def main():
    stats = read(ROOT / "art/yuji_fodu/data/model_stats.json")
    require(len(stats) == 12, "Expected 11 mobs and one projectile")
    sounds = read(ASSETS / "sounds.json")
    particles = {path.stem: read(path) for path in (ASSETS / "particles").glob("*.json")}
    city_particles = {"black_splash", "holy_light"}
    require(city_particles <= set(particles), "Missing city particles")
    require(len(set(particles) - city_particles) == 19, "Expected nineteen monster particle definitions")
    for particle in particles.values():
        for reference in particle["textures"]:
            namespace, sprite = reference.split(":", 1)
            texture(f"{namespace}:particle/{sprite}")
    audio_files = set()
    for key, sound in sounds.items():
        for entry in sound["sounds"]:
            name = entry if isinstance(entry, str) else entry["name"]
            namespace, resource = name.split(":", 1)
            require(namespace == "qihai", f"Unexpected audio namespace: {name}")
            path = ASSETS / "sounds" / (resource + ".ogg")
            require(path.read_bytes()[:4] == b"OggS", f"Invalid Ogg: {key}")
            audio_files.add(path)
    languages = [read(ASSETS / "lang" / name) for name in ("zh_cn.json", "en_us.json")]
    for key, sound in sounds.items():
        if "subtitle" in sound:
            for lang in languages:
                require(sound["subtitle"] in lang, f"Missing sound subtitle: {key}")
    cues = read(ASSETS / "yuji_cues.json")
    for kind, expected in stats.items():
        for lang in languages:
            require(f"entity.qihai.{kind}" in lang, f"Missing entity translation: {kind}")
        geo = read(ASSETS / "geo" / f"{kind}.geo.json")["minecraft:geometry"][0]
        bones = {bone["name"]: bone for bone in geo["bones"]}
        require(len(bones) == expected["bones"], f"Bone count: {kind}")
        require(sum(len(bone.get("cubes", [])) for bone in bones.values()) == expected["cubes"], f"Cube count: {kind}")
        for name, bone in bones.items():
            seen = {name}
            while "parent" in bone:
                parent = bone["parent"]
                require(parent in bones and parent not in seen, f"Invalid bone hierarchy: {kind}/{name}")
                seen.add(parent)
                bone = bones[parent]
        require(texture(f"qihai:entity/{kind}") == (expected["tex"], expected["tex"]), f"Texture size: {kind}")
        if expected["glow"]:
            require(texture(f"qihai:entity/{kind}_glowmask") == (expected["tex"], expected["tex"]), f"Glow size: {kind}")
        animations = read(ASSETS / "animations" / f"{kind}.animation.json")["animations"]
        require(set(animations) == {f"animation.{kind}.{action}" for action in expected["anims"]}, f"Animation set: {kind}")
        for name, animation in animations.items():
            require(set(animation.get("bones", {})) <= set(bones), f"Animation bone reference: {name}")
            validate_keyframes(name, animation)
        for action, events in cues.get(kind, {}).items():
            animation = animations[f"animation.{kind}.{action}"]
            for event in events:
                require(0 <= event["t"] <= animation["animation_length"], f"Cue timing: {kind}/{action}")
                require(event["bone"] in bones, f"Cue bone: {kind}/{action}")
                require(event["vfx"] in particles, f"Cue particle: {kind}/{action}")
                if "sound" in event:
                    require(f"entity.qihai.{kind}.{event['sound']}" in sounds, f"Cue sound: {kind}/{action}")
        if kind != "bone_spike":
            loot = read(ROOT / "src/main/resources/data/qihai/loot_tables/entities" / f"{kind}.json")
            require(loot.get("type") == "minecraft:entity" and loot.get("pools") == [], f"Explicit unspecified-loot policy: {kind}")
            model = read(ASSETS / "models/item" / f"{kind}_spawn_egg.json")
            for reference in model["textures"].values():
                texture(reference)
            for lang in languages:
                require(f"item.qihai.{kind}_spawn_egg" in lang, f"Missing egg translation: {kind}")
    for effect in ("cursed", "betrayed", "enthralled"):
        texture(f"qihai:mob_effect/{effect}")
        for lang in languages:
            require(f"effect.qihai.{effect}" in lang, f"Missing effect translation: {effect}")
    require("loot" in {bone["name"] for bone in read(ASSETS / "geo/toudao.geo.json")["minecraft:geometry"][0]["bones"]}, "Missing loot attachment")
    layers = {bone["name"] for bone in read(ASSETS / "geo/tanyu.geo.json")["minecraft:geometry"][0]["bones"]}
    require({f"layer_{i}" for i in range(1, 7)} <= layers, "Missing six facial layers")
    print(f"PASS: 12 models/animations, 11 eggs/loot tables, 3 effects, 19 particles, {len(sounds)} sounds/{len(audio_files)} Ogg files; all references resolved")


if __name__ == "__main__":
    main()
