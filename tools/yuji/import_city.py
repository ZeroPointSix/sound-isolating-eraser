"""Import the supplied v0.2 city kit without replacing existing mod assets."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE_SHA256 = "f01759a77ecf4060042c109b9ce7c4ef9a8e7c25cf33e43b179c4b2a7dddc14f"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    parser.add_argument("--root", type=Path, default=ROOT, help="Repository root (also useful for isolated checks)")
    args = parser.parse_args()
    root = args.root.resolve()
    resources = root / "src/main/resources"
    require(hashlib.sha256(args.archive.read_bytes()).hexdigest() == SOURCE_SHA256, "Unexpected city archive SHA-256")
    planned, languages = {}, {}
    with zipfile.ZipFile(args.archive) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            path = PurePosixPath(item.filename)
            require(not path.is_absolute() and ".." not in path.parts and "\\" not in item.filename,
                    f"Unsafe archive path: {item.filename}")
            require(len(path.parts) > 1 and path.parts[0] == "yuji_fodu_city_kit" and item.file_size < 20_000_000,
                    f"Unexpected archive entry: {item.filename}")
            relative = PurePosixPath(*path.parts[1:])
            data = archive.read(item)
            if str(relative).startswith("structures/"):
                target = resources / "data/qihai/structures/yuji" / relative.relative_to("structures")
            elif str(relative).startswith("assets/qihai/lang/"):
                target = resources / relative
                require(target.resolve().is_relative_to(root), f"Target escapes repository: {target}")
                original = json.loads(target.read_text(encoding="utf-8")) if target.exists() else {}
                addition = json.loads(data)
                require(isinstance(original, dict) and isinstance(addition, dict), f"Invalid language object: {target}")
                require(all(k not in original or original[k] == v for k, v in addition.items()),
                        f"Refusing to replace language entries in {target}")
                require(target not in planned, f"Duplicate archive target: {target}")
                languages[str(relative)] = addition
                planned[target] = json.dumps(original | addition, ensure_ascii=False, indent=2).encode("utf-8") + b"\n"
                continue
            elif str(relative).startswith("assets/"):
                # 1.21 item definitions are not used by 1.20.1; models/item is supplied too.
                if str(relative).startswith("assets/qihai/items/"):
                    continue
                target = resources / relative
            elif str(relative) == "LICENSE-LostCities-MIT.txt":
                target = resources / "META-INF/licenses/yuji-LostCities-MIT.txt"
            elif str(relative) in ("README.md", "data/structure_stats.json") or str(relative).startswith("images/"):
                target = root / "art/yuji_city" / relative
            else:
                continue
            require(target.resolve().is_relative_to(root), f"Target escapes repository: {target}")
            if target.exists():
                require(target.read_bytes() == data, f"Refusing to replace {target}")
            require(target not in planned, f"Duplicate archive target: {target}")
            planned[target] = data
    require(sum(p.suffix == ".nbt" for p in planned) == 48, "Expected exactly 48 city structures")
    messages = json.loads(Path(__file__).with_name("city_messages.json").read_text(encoding="utf-8"))
    require(isinstance(messages, dict) and set(messages) == {"en_us", "zh_cn"}, "Expected English and Chinese city messages")
    extra_languages = {}
    for locale, addition in messages.items():
        require(isinstance(addition, dict) and all(isinstance(k, str) and isinstance(v, str) for k, v in addition.items()),
                f"Invalid city message object: {locale}")
        relative = f"assets/qihai/lang/{locale}.json"
        target = resources / relative
        require(target in planned, f"City archive lacks its {locale} translations")
        original = json.loads(planned[target])
        require(all(k not in original or original[k] == v for k, v in addition.items()),
                f"Refusing to replace city message entries in {target}")
        planned[target] = json.dumps(original | addition, ensure_ascii=False, indent=2).encode("utf-8") + b"\n"
        extra_languages[relative] = addition
    manifest = {"source_sha256": SOURCE_SHA256, "files": {}, "language_entries": languages,
                "extra_language_entries": extra_languages, "documentation": {}}
    for path, data in planned.items():
        if path.is_relative_to(resources) and "/lang/" not in str(path):
            manifest["files"][path.relative_to(resources).as_posix()] = hashlib.sha256(data).hexdigest()
        elif path.is_relative_to(root / "art/yuji_city"):
            manifest["documentation"][path.relative_to(root).as_posix()] = hashlib.sha256(data).hexdigest()
    manifest_path = resources / "data/qihai/yuji_city_manifest.json"
    require(manifest_path.resolve().is_relative_to(root), "Manifest escapes repository")
    if manifest_path.exists():
        previous = json.loads(manifest_path.read_text(encoding="utf-8"))
        require(previous.get("source_sha256") == SOURCE_SHA256, "Refusing to replace an unrelated manifest")
        for section in ("files", "language_entries", "extra_language_entries", "documentation"):
            require(all(manifest[section].get(k) == v for k, v in previous.get(section, {}).items()),
                    f"Refusing to discard changed manifest {section}")
    planned[manifest_path] = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8") + b"\n"
    for target, data in planned.items():
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print(f"Imported {len(planned) - 1} files, including 48 byte-identical structures and reference images")


if __name__ == "__main__":
    main()
