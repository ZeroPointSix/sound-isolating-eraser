#!/usr/bin/env python3
"""Release gate: byte-exact approved assets and loot constrained to the approved categories."""
import hashlib
import json
from pathlib import Path
from import_city_art import SOURCE_SHA256, RESOURCES


def main():
    manifest_path = RESOURCES / "data/sound_isolating_eraser/eternal_night_art_manifest.json"
    if not manifest_path.is_file():
        raise SystemExit("CITY_ART_MISSING: import the authorized Notion ZIP with tools/import_city_art.py; do not generate substitutes")
    manifest = json.loads(manifest_path.read_text())
    assert manifest["source_sha256"] == SOURCE_SHA256, "Incorrect source kit"
    for relative, expected in manifest["files"].items():
        assert hashlib.sha256((RESOURCES / relative).read_bytes()).hexdigest() == expected, relative
    allowed = {"arrow", "bow", "crossbow", "shield", "shears", "iron_ingot"}
    for material in ("wooden", "stone", "iron", "diamond"):
        allowed.update(material + "_" + tool for tool in ("sword", "axe", "pickaxe", "shovel", "hoe"))
    for material in ("leather", "chainmail", "iron", "diamond"):
        allowed.update(material + "_" + part for part in ("helmet", "chestplate", "leggings", "boots"))
    tables = sorted((RESOURCES / "data/sound_isolating_eraser/loot_tables/chests/eternal_night").glob("*.json"))
    assert len(tables) == 6, "Expected six loot tables"
    for table in tables:
        for pool in json.loads(table.read_text())["pools"]:
            for entry in pool["entries"]:
                assert entry["type"] == "minecraft:item" and entry["name"].removeprefix("minecraft:") in allowed, table
    print(f"CITY_ART_VERIFIED files={len(manifest['files'])} loot_tables={len(tables)} source_sha256={SOURCE_SHA256}")


if __name__ == "__main__":
    main()
