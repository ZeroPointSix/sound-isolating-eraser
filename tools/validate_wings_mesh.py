"""Verify runtime geometry is the original orthographic mesh, not a box approximation."""
import hashlib
import json
import math
from pathlib import Path

root = Path(__file__).resolve().parents[1]
reference = root / "art/B_model_preview/model/wind_thunder_wings.mesh.json"
runtime = root / "src/main/resources/assets/sound_isolating_eraser/models/wind_thunder_wings.mesh.json"
assert reference.read_bytes() == runtime.read_bytes(), "Runtime mesh differs from three-view source"
mesh = json.loads(runtime.read_text())
assert mesh["coordinates"]["unit"] == "minecraft_block"
assert mesh["coordinates"]["y"] == "up"
bones = {b["name"]: b for b in mesh["bones"]}
for bone in bones.values():
    visited = set()
    current = bone
    while current:
        assert current["name"] not in visited, "Bone cycle"
        visited.add(current["name"])
        current = bones[current["parent"]] if current["parent"] else None
points = []
triangles = 0
for part in mesh["meshes"]:
    assert part["bone"] in bones
    assert len(part["triangles"]) == len(part["normals"]) == len(part["material_ids"])
    for point in part["vertices"]:
        assert all(math.isfinite(c) for c in point)
    for face, normal, material in zip(part["triangles"], part["normals"], part["material_ids"]):
        assert len(face) == 3 and all(0 <= i < len(part["vertices"]) for i in face)
        assert abs(sum(v*v for v in normal) - 1) < 0.001
        assert 0 <= material < len(mesh["materials"])
    if part["visibility_channel"] == "body":
        points.extend(part["vertices"])
        triangles += len(part["triangles"])
span = max(v[0] for v in points) - min(v[0] for v in points)
assert abs(span - 6.4) < 0.001, span
assert abs(span * mesh["mount"]["miniature_scale"] - 0.4) < 0.001
assert len([b for b in bones if "_primary_" in b]) == 14
assert len([b for b in bones if "_secondary_" in b]) == 12
assert len([b for b in bones if "_covert_" in b]) == 16
print(json.dumps({"status": "WINGS_REFERENCE_MESH_PASSED", "sha256": hashlib.sha256(runtime.read_bytes()).hexdigest(),
                  "span_blocks": span, "closed_blocks": span * mesh["mount"]["miniature_scale"],
                  "body_triangles": triangles, "feathers": 42}, indent=2))
