"""Generate flat, connected chalk strokes; masks mirror EraserStroke.java."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/sound_isolating_eraser"
MODELS = ROOT / "models/block"
MASKS = {"point": 0, "x": 68, "z": 17, "nw_se": 136, "ne_sw": 34,
         "e_sw": 36, "w_se": 72, "nw_s": 144, "n_sw": 33,
         "ne_w": 66, "e_nw": 132, "se_n": 9, "s_ne": 18}
PORTS = [(0,-1), (1,-1), (1,0), (1,1), (0,1), (-1,1), (-1,0), (-1,-1)]
film = json.loads((MODELS / "eraser_barrier.json").read_text())
variants = {}
for name, mask in MASKS.items():
    if mask:
        model = {"parent": "minecraft:block/block", "ambientocclusion": False,
                 "render_type": "minecraft:translucent",
                 "textures": {"film": "sound_isolating_eraser:block/eraser_wall",
                              "particle": "sound_isolating_eraser:block/eraser_wall",
                              "chalk": "minecraft:block/white_concrete"},
                 "elements": list(film["elements"])}
        for port, (dx, dz) in enumerate(PORTS):
            if mask & (1 << port):
                if dx and dz:
                    start, end = [7, 0.015, -3.3137085 if dz < 0 else 8], [9, 0.025, 8 if dz < 0 else 19.3137085]
                elif dx:
                    start, end = [0 if dx < 0 else 8, 0.015, 7], [8 if dx < 0 else 16, 0.025, 9]
                else:
                    start, end = [7, 0.015, 0 if dz < 0 else 8], [9, 0.025, 8 if dz < 0 else 16]
                element = {"from": start, "to": end, "shade": False,
                           "faces": {face: {"texture": "#chalk", "uv": [0,0,16,16]}
                                     for face in ("up", "down")}}
                if dx and dz:
                    element["rotation"] = {"origin": [8,0,8], "axis": "y", "angle": 45 * dx * dz}
                model["elements"].append(element)
        (MODELS / f"eraser_trace_{name}.json").write_text(json.dumps(model, indent=2) + "\n")
    for facing in ("down", "up", "north", "east", "south", "west"):
        if facing == "down":
            suffix = "eraser_anchor_mark_down" if not mask else f"eraser_trace_{name}"
        elif facing == "up":
            suffix = "eraser_anchor_mark_up"
        else:
            suffix = "eraser_anchor_mark_side" if not mask else "eraser_trace_side"
        variant = {"model": "sound_isolating_eraser:block/" + suffix}
        if facing in ("east", "south", "west"):
            variant["y"] = {"east": 90, "south": 180, "west": 270}[facing]
        variants[f"facing={facing},stroke={name}"] = variant

side = json.loads((MODELS / "eraser_anchor_mark_side.json").read_text())
side["textures"]["mark"] = "minecraft:block/white_concrete"
side["elements"][-1]["from"] = [0, 7, 0.015]
side["elements"][-1]["to"] = [16, 9, 0.025]
(MODELS / "eraser_trace_side.json").write_text(json.dumps(side, indent=2) + "\n")
(ROOT / "blockstates/eraser_anchor.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n")
