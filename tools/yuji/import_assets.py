"""Import the supplied art archive without changing its namespace or existing files."""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    digest = hashlib.sha256(args.archive.read_bytes()).hexdigest()
    expected = "c034d4c9aa7685255fb182fa3ff07b12af5c52b1c813438ebd6843ee73a8bc1f"
    if digest != expected:
        raise ValueError("Unexpected archive SHA-256")
    root = Path(__file__).resolve().parents[2]
    entries = []
    with zipfile.ZipFile(args.archive) as archive:
        for info in archive.infolist():
            name = PurePosixPath(info.filename)
            if name.is_absolute() or ".." in name.parts or (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError("Unsafe archive member")
            if info.is_dir():
                continue
            relative = name.relative_to("yuji_fodu_monsters")
            if str(relative).startswith("assets/qihai/items/"):
                continue  # 1.21 item definitions are not used by this Forge 1.20.1 project.
            if relative.parts[0] == "assets":
                destination = root / "src/main/resources" / relative
            elif str(relative) == "data/cues.json":
                destination = root / "src/main/resources/assets/qihai/yuji_cues.json"
            else:
                destination = root / "art/yuji_fodu" / relative
            data = archive.read(info)
            if destination.exists() and destination.read_bytes() != data:
                raise ValueError(f"Refusing to overwrite changed asset: {destination}")
            entries.append((destination, data))
    for destination, data in entries:
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    print(f"Imported {len(entries)} verified files; archive SHA-256 {digest}")


if __name__ == "__main__":
    main()
