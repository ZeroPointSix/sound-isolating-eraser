#!/usr/bin/env python3
"""Import only the exact, user-supplied city kit. No generated replacements."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

SOURCE_SHA256 = "9dab6be385742ebc8a1e7c2d371db5f8b5ef6b40a1062ee376ae99f915455d24"
SOURCE_SIZE = 2759557
ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"


def plan_import(archive):
    payload = Path(archive).read_bytes()
    if len(payload) != SOURCE_SIZE or hashlib.sha256(payload).hexdigest() != SOURCE_SHA256:
        raise ValueError("Source ZIP does not match the Notion kit's size and SHA256")
    planned = {}
    with zipfile.ZipFile(archive) as kit:
        for entry in kit.infolist():
            if entry.is_dir():
                continue
            parts = PurePosixPath(entry.filename).parts
            if ".." in parts or entry.filename.startswith("/"):
                raise ValueError("Unsafe ZIP path")
            if entry.file_size > 16 * 1024 * 1024:
                raise ValueError("Unexpected oversized member")
            target = None
            if "assets" in parts:
                relative = PurePosixPath(*parts[parts.index("assets"):])
                if str(relative).startswith("assets/qihai/"):
                    target = relative
            elif "structures" in parts and entry.filename.endswith(".nbt"):
                relative = PurePosixPath(*parts[parts.index("structures") + 1:])
                target = PurePosixPath("data/qihai/structures") / relative
            elif parts[-1] == "LICENSE-LostCities-MIT.txt":
                target = PurePosixPath("META-INF/licenses/LICENSE-LostCities-MIT.txt")
            if target is not None:
                if str(target) in planned:
                    raise ValueError("Duplicate destination in kit")
                planned[str(target)] = kit.read(entry)
    if sum(path.endswith(".nbt") for path in planned) != 35:
        raise ValueError("Expected exactly 35 original structures")
    if sum(path.endswith(".png") for path in planned) != 22:
        raise ValueError("Expected exactly 22 original textures")
    if "META-INF/licenses/LICENSE-LostCities-MIT.txt" not in planned:
        raise ValueError("Required MIT notice is absent")
    return planned


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    planned = plan_import(args.archive)
    # Validate the entire import before touching the checkout, and never silently replace another asset.
    for relative, content in planned.items():
        target = RESOURCES / relative
        if target.exists() and target.read_bytes() != content:
            raise ValueError(f"Existing asset differs: {relative}")
    manifest = {"source": "Notion eternal_night_city_kit.zip", "source_size": SOURCE_SIZE,
                "source_sha256": SOURCE_SHA256,
                "files": {name: hashlib.sha256(data).hexdigest() for name, data in sorted(planned.items())}}
    for relative, content in planned.items():
        target = RESOURCES / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
    target = RESOURCES / "data/sound_isolating_eraser/eternal_night_art_manifest.json"
    target.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"CITY_ART_IMPORTED source_sha256={SOURCE_SHA256} files={len(planned)}")


if __name__ == "__main__":
    main()
