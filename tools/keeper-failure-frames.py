"""Convert retained raw failure pixels after the real E2E has stopped."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import struct
import zlib


def chunk(kind, payload):
    return (struct.pack(">I", len(payload)) + kind + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff))


def rgb_png(raw, width, height):
    if not 0 < width <= 16384 or not 0 < height <= 16384:
        raise ValueError("Invalid capture dimensions")
    if len(raw) != width * height * 4:
        raise ValueError("Raw capture length differs from recorded dimensions")
    # Java ByteBuffer writes big-endian ARGB; TYPE_INT_RGB has no alpha channel.
    rgb = bytearray(width * height * 3)
    rgb[0::3], rgb[1::3], rgb[2::3] = raw[1::4], raw[2::4], raw[3::4]
    rows = bytearray()
    for row in range(height):
        rows.append(0)
        rows.extend(rgb[row * width * 3:(row + 1) * width * 3])
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def convert(results, output):
    with (results / "sampler-failure-captures.csv").open(newline="") as stream:
        reader = csv.DictReader(stream)
        required = {"frame", "start_ns", "end_ns", "width", "height"}
        if set(reader.fieldnames or ()) != required:
            raise ValueError("Expected raw capture CSV with dimensions")
        fields = reader.fieldnames
        captures = list(reader)
    output.mkdir(parents=True, exist_ok=False)
    converted, hashes = [], {}
    for capture in captures:
        name = capture["frame"]
        if Path(name).name != name or not name.endswith(".argb"):
            raise ValueError("Expected a bare raw capture filename")
        raw = (results / "sampler-failure-frames" / name).read_bytes()
        png = rgb_png(raw, int(capture["width"]), int(capture["height"]))
        png_name = Path(name).with_suffix(".png").name
        with (output / png_name).open("xb") as stream:
            stream.write(png)
        converted.append({**capture, "frame": png_name})
        hashes[png_name] = {"source": name, "raw_sha256": hashlib.sha256(raw).hexdigest(),
                            "png_sha256": hashlib.sha256(png).hexdigest()}
    with (output / "captures.csv").open("x", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(converted)
    with (output / "manifest.json").open("x") as stream:
        json.dump({"source": str(results.resolve()), "frames": hashes,
                   "scope": "Lossless post-run conversion; raw pixels and timestamps unchanged"},
                  stream, indent=2)
        stream.write("\n")
    return len(converted)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or args.results / "sampler-failure-png"
    count = convert(args.results, output)
    print(json.dumps({"converted_frames": count, "output": str(output)}))


if __name__ == "__main__":
    main()
