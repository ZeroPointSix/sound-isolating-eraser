"""Generate a closed, unlit GameTest arena without third-party NBT dependencies."""

import gzip
import struct
from pathlib import Path


def string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def integer(name, value):
    return tag(3, name, struct.pack(">i", value))


def list_tag(name, kind, values):
    return tag(9, name, bytes([kind]) + struct.pack(">i", len(values)) + b"".join(values))


def integers(name, values):
    return list_tag(name, 3, [struct.pack(">i", value) for value in values])


size = (25, 6, 25)
palette = [tag(8, "Name", string(name)) + b"\0" for name in ("minecraft:air", "minecraft:stone")]
blocks = []
for x in range(size[0]):
    for y in range(size[1]):
        for z in range(size[2]):
            wall = x in (0, 24) or y in (0, 5) or z in (0, 24)
            blocks.append(integers("pos", [x, y, z]) + integer("state", int(wall)) + b"\0")
root = (integer("DataVersion", 3465) + integers("size", size)
        + list_tag("palette", 10, palette) + list_tag("blocks", 10, blocks)
        + list_tag("entities", 10, []) + b"\0")
output = Path(__file__).resolve().parents[2] / "src/main/resources/data/sound_isolating_eraser/structures/shadow_arena.nbt"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(gzip.compress(tag(10, "", root), mtime=0))
print(output.name, output.stat().st_size, "bytes")
