"""Read-only city-kit checks for Python 3.9+, using only the standard library."""
import argparse
import gzip
import hashlib
import io
import itertools
import json
from pathlib import Path, PurePosixPath
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
SOURCE_SHA256 = "f01759a77ecf4060042c109b9ce7c4ef9a8e7c25cf33e43b179c4b2a7dddc14f"
KINDS = "yuchi shasheng toudao ekou wangyu liangshe xieyin qiyu tanyu chennu".split()
FACES = {"north", "east", "south", "west"}
BLOCK_PROPERTIES = {
    "incense_burner": {},
    "holy_light_source": {"lit": {"true", "false"}},
    "wall_corpse": {"facing": FACES, "part": {"lower", "upper"}},
    "mural": {"facing": FACES, "mural": set(KINDS) | {"soul", "golden_buddha"},
              "tile": {str(i) for i in range(9)}},
}
LOOT_NAMES = {"common", "grocery", "hardware", "hospital", "mall", "pharmacy", "supply_drop"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


class NbtReader:
    """Decode the standard Java-edition NBT tags without a third-party package."""

    def __init__(self, data):
        self.stream = io.BytesIO(data)
        self.size = len(data)

    def read(self, size):
        require(size >= 0, "Negative NBT read length")
        data = self.stream.read(size)
        require(len(data) == size, "Truncated NBT payload")
        return data

    def number(self, format_code):
        return struct.unpack(">" + format_code, self.read(struct.calcsize(">" + format_code)))[0]

    def string(self):
        return self.read(self.number("H")).decode("utf-8")

    def count(self):
        count = self.number("i")
        require(0 <= count <= self.size, "Invalid NBT collection length")
        return count

    def payload(self, tag, depth=0):
        require(depth < 64, "NBT nesting limit exceeded")
        formats = {1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}
        if tag in formats:
            return self.number(formats[tag])
        if tag == 7:
            return self.read(self.count())
        if tag == 8:
            return self.string()
        if tag == 9:
            kind, count = self.number("B"), self.count()
            require(1 <= kind <= 12 or count == 0, "Invalid NBT list type")
            return [self.payload(kind, depth + 1) for _ in range(count)]
        if tag == 10:
            result = {}
            while True:
                kind = self.number("B")
                if kind == 0:
                    return result
                name = self.string()
                require(name not in result, f"Duplicate NBT key: {name}")
                result[name] = self.payload(kind, depth + 1)
        if tag in (11, 12):
            return [self.number("i" if tag == 11 else "q") for _ in range(self.count())]
        raise ValueError(f"Unknown NBT tag: {tag}")

    def root(self):
        require(self.number("B") == 10, "Structure NBT root must be a compound")
        self.string()
        result = self.payload(10)
        require(self.stream.tell() == self.size, "Trailing data after NBT root")
        return result


def objects(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from objects(child)
    elif isinstance(value, list):
        for child in value:
            yield from objects(child)


def safe_path(base, relative):
    path = PurePosixPath(relative)
    require(not path.is_absolute() and bool(path.parts) and ".." not in path.parts and "\\" not in relative,
            f"Unsafe resource path: {relative}")
    target = base / path
    require(target.resolve().is_relative_to(base.resolve()), f"Resource escapes root: {relative}")
    return target


class CityVerifier:
    def __init__(self, root):
        self.root = root.resolve()
        self.resources = self.root / "src/main/resources"
        self.json_cache = {}
        self.models = set()
        self.loot = set()

    def load_json(self, path):
        if path not in self.json_cache:
            require(path.is_file(), f"Missing resource: {path}")
            try:
                self.json_cache[path] = json.loads(path.read_text(encoding="utf-8"))
            except (ValueError, UnicodeError) as error:
                raise ValueError(f"Invalid JSON in {path}: {error}") from error
        return self.json_cache[path]

    def reference(self, value, folder, suffix, origin, prefix=""):
        require(isinstance(value, str), f"Non-string resource reference in {origin}")
        namespace, separator, path = value.partition(":")
        if not separator:
            namespace, path = "minecraft", namespace
        require(re.fullmatch(r"[a-z0-9_.-]+", namespace) and re.fullmatch(r"[a-z0-9_./-]+", path),
                f"Invalid resource ID {value} in {origin}")
        require(".." not in PurePosixPath(path).parts and not path.startswith("/"),
                f"Unsafe resource ID {value} in {origin}")
        if namespace == "minecraft":
            return None
        kind = "data" if folder == "loot_tables" else "assets"
        target = safe_path(self.resources, f"{kind}/{namespace}/{folder}/{prefix}{path}{suffix}")
        require(target.is_file(), f"Missing {value} referenced by {origin}")
        return target

    def model(self, path, stack=()):
        require(path not in stack, f"Model parent cycle at {path}")
        if path in self.models:
            return
        model = self.load_json(path)
        require(isinstance(model, dict), f"Model must be an object: {path}")
        if "parent" in model:
            parent = self.reference(model["parent"], "models", ".json", path)
            if parent is not None:
                self.model(parent, stack + (path,))
        for texture in model.get("textures", {}).values():
            if not texture.startswith("#"):
                self.reference(texture, "textures", ".png", path)
        self.models.add(path)

    def manifest(self):
        manifest = self.load_json(self.resources / "data/qihai/yuji_city_manifest.json")
        require(manifest.get("source_sha256") == SOURCE_SHA256, "City manifest has the wrong source archive")
        require(isinstance(manifest.get("files"), dict) and manifest["files"], "Empty city resource manifest")
        for section, base in (("files", self.resources), ("documentation", self.root)):
            require(isinstance(manifest.get(section), dict) and manifest[section], f"Missing manifest section: {section}")
            for relative, expected in manifest[section].items():
                path = safe_path(base, relative)
                require(path.is_file(), f"Missing manifest file: {relative}")
                require(hashlib.sha256(path.read_bytes()).hexdigest() == expected, f"Hash mismatch: {relative}")
        for locale in ("en_us", "zh_cn"):
            relative = f"assets/qihai/lang/{locale}.json"
            expected = manifest.get("language_entries", {}).get(relative)
            require(isinstance(expected, dict) and expected, f"Missing language manifest: {locale}")
            actual = self.load_json(safe_path(self.resources, relative))
            require(all(actual.get(key) == value for key, value in expected.items()),
                    f"Imported translations were changed or lost: {locale}")
            for kind in KINDS:
                require(f"item.qihai.karma_mark_{kind}" in actual, f"Missing karma translation: {locale}/{kind}")
            for block in BLOCK_PROPERTIES:
                require(f"block.qihai.{block}" in actual, f"Missing block translation: {locale}/{block}")
        document_files = manifest["documentation"]
        require("art/yuji_city/README.md" in document_files and "art/yuji_city/data/structure_stats.json" in document_files,
                "City documentation is incomplete")
        readme = (self.root / "art/yuji_city/README.md").read_text(encoding="utf-8")
        for relative in re.findall(r"!\[[^\]]*\]\((images/[^)]+)\)", readme):
            require(f"art/yuji_city/{relative}" in document_files, f"Missing README image: {relative}")
        return manifest

    def assets(self, manifest):
        for relative in manifest["files"]:
            path = safe_path(self.resources, relative)
            if path.suffix != ".json":
                continue
            value = self.load_json(path)
            if "/models/" in relative:
                self.model(path)
            elif "/blockstates/" in relative:
                for entry in objects(value):
                    if "model" in entry:
                        model = self.reference(entry["model"], "models", ".json", path)
                        if model is not None:
                            self.model(model)
            elif "/particles/" in relative:
                for texture in value.get("textures", []):
                    self.reference(texture, "textures", ".png", path, prefix="particle/")
        for name in list(BLOCK_PROPERTIES) + [f"karma_mark_{kind}" for kind in KINDS]:
            self.model(self.resources / f"assets/qihai/models/item/{name}.json")
        mural = self.load_json(self.resources / "assets/qihai/blockstates/mural.json")
        variants = {tuple(sorted(tuple(part.split("=", 1)) for part in key.split(","))) for key in mural["variants"]}
        expected = {
            tuple(sorted((("facing", facing), ("mural", name), ("tile", str(tile)))))
            for facing, name, tile in itertools.product(FACES, BLOCK_PROPERTIES["mural"]["mural"], range(9))
        }
        require(variants == expected, "Mural blockstates must cover all 432 registered combinations")

    def structures(self, manifest):
        structures = sorted((self.resources / "data/qihai/structures/yuji").rglob("*.nbt"))
        require(len(structures) == 48, f"Expected 48 city structures, found {len(structures)}")
        imported = {safe_path(self.resources, name) for name in manifest["files"] if name.endswith(".nbt")}
        require(set(structures) == imported, "Structure files differ from the source manifest")
        for path in structures:
            try:
                nbt = NbtReader(gzip.decompress(path.read_bytes())).root()
            except (OSError, ValueError) as error:
                raise ValueError(f"Invalid structure {path}: {error}") from error
            require(nbt.get("DataVersion") == 3465, f"Wrong Minecraft data version in {path}")
            size = nbt.get("size", [])
            require(len(size) == 3 and all(isinstance(n, int) and n > 0 for n in size), f"Invalid structure size: {path}")
            palettes = nbt.get("palettes", [nbt.get("palette", [])])
            require(palettes and all(palettes), f"Empty structure palette: {path}")
            for palette in palettes:
                for state in palette:
                    name = state["Name"]
                    if not name.startswith("qihai:"):
                        continue
                    block = name.split(":", 1)[1]
                    require(block in BLOCK_PROPERTIES, f"Unregistered city block {name} in {path}")
                    for key, value in state.get("Properties", {}).items():
                        require(value in BLOCK_PROPERTIES[block].get(key, set()),
                                f"Unsupported block property {name}[{key}={value}] in {path}")
            for block in nbt.get("blocks", []):
                require(all(0 <= block["state"] < len(palette) for palette in palettes), f"Invalid palette index: {path}")
                require(len(block["pos"]) == 3 and all(0 <= value < size[i] for i, value in enumerate(block["pos"])),
                        f"Block lies outside structure bounds: {path}")
                for entry in objects(block.get("nbt", {})):
                    if "LootTable" in entry:
                        loot = self.reference(entry["LootTable"], "loot_tables", ".json", path)
                        if loot is not None:
                            self.loot.add(loot)
        expected = {self.resources / f"data/qihai/loot_tables/chests/yuji_{name}.json" for name in LOOT_NAMES}
        require(expected <= self.loot, "The structures no longer reference all seven city loot tables")
        for path in self.loot:
            table = self.load_json(path)
            require(table.get("type") == "minecraft:chest" and bool(table.get("pools")), f"Invalid chest loot table: {path}")
            for pool in table["pools"]:
                require("rolls" in pool and bool(pool.get("entries")), f"Empty loot pool: {path}")
            for entry in objects(table):
                if entry.get("type") == "minecraft:loot_table":
                    self.reference(entry["name"], "loot_tables", ".json", path)
                elif entry.get("type") == "minecraft:item":
                    self.reference(entry["name"], "models", ".json", path, prefix="item/")

    def dimension(self):
        dimension = self.load_json(self.resources / "data/qihai/dimension/yuji_fodu.json")
        require(dimension.get("type") == "qihai:yuji_fodu", "Wrong city dimension type")
        generator = dimension.get("generator", {})
        require(generator.get("type") == "qihai:yuji_fodu", "Wrong city chunk generator")
        require(generator.get("biome_source") == {"type": "minecraft:fixed", "biome": "qihai:yuji_fodu"},
                "City generator needs its fixed city biome")
        kind = self.load_json(self.resources / "data/qihai/dimension_type/yuji_fodu.json")
        for key, value in {"min_y": -64, "height": 384, "logical_height": 384, "has_skylight": True,
                           "has_ceiling": False, "has_raids": False, "natural": True, "effects": "qihai:yuji_fodu"}.items():
            require(kind.get(key) == value, f"Wrong dimension setting: {key}")
        require("fixed_time" not in kind, "City dimension must retain the normal day/night cycle")
        biome = self.load_json(self.resources / "data/qihai/worldgen/biome/yuji_fodu.json")
        require(biome.get("has_precipitation") is True and biome.get("temperature", 0) > 0.15,
                "City biome must allow rain rather than snow")
        require(not any(biome.get("spawners", {}).values()), "Vanilla natural spawns must stay disabled in the city")
        require(not biome.get("features") and not any(biome.get("carvers", {}).values()), "City biome must not alter templates")

    def verify(self):
        manifest = self.manifest()
        self.assets(manifest)
        self.structures(manifest)
        self.dimension()
        return (f"Verified {len(manifest['files'])} source resources, "
                f"{len(manifest['documentation'])} documentation files, 48 NBT structures, "
                f"{len(self.models)} models, {len(self.loot)} loot tables and the city dimension")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="Repository root to verify")
    args = parser.parse_args()
    try:
        print(CityVerifier(args.root).verify())
    except (OSError, ValueError, KeyError, TypeError, AttributeError, struct.error) as error:
        print(f"City verification failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
