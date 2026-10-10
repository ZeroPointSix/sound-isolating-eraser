"""Build and render an original low-poly Wind / Thunder Wings art prototype.

Blender 4.x:
  blender -b --python blender_scene.py -- --out /path/to/output --render --animate
The default run saves editable .blend, animated .glb, and an asset manifest.
"""

import argparse
import json
import math
import os
import struct
import sys
from pathlib import Path

import bpy
import bmesh
from mathutils import Quaternion, Vector
from bpy_extras.object_utils import world_to_camera_view


def arguments():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--render", action="store_true")
    parser.add_argument("--animate", action="store_true")
    parser.add_argument("--render-clips", default="")
    parser.add_argument("--engine", choices=["workbench", "eevee", "cycles"], default="workbench")
    parser.add_argument("--width", type=int, default=1600)
    parser.add_argument("--height", type=int, default=900)
    parser.add_argument("--samples", type=int, default=24)
    parser.add_argument("--flash", action="store_true", help="Enable the optional white blink burst; disabled by default.")
    parser.add_argument("--export-minecraft", action="store_true", help="Also export rigid, material-colored triangle geometry for a Minecraft RenderLayer.")
    return parser.parse_args(argv)


ARGS = arguments()
OUT = Path(ARGS.out).resolve()
OUT.mkdir(parents=True, exist_ok=True)
bpy.ops.object.mode_set(mode="OBJECT") if bpy.context.object and bpy.context.object.mode != "OBJECT" else None
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
for datablocks in (bpy.data.materials, bpy.data.actions, bpy.data.armatures, bpy.data.meshes):
    for block in list(datablocks):
        if block.users == 0:
            datablocks.remove(block)

SCENE = bpy.context.scene
SCENE.name = "WindThunder_ArtPrototype"
SCENE.render.fps = 24
SCENE.render.resolution_percentage = 100
SCENE.render.image_settings.file_format = "PNG"
SCENE.render.image_settings.color_mode = "RGBA"
SCENE.render.film_transparent = False
SCENE.world.color = (0.66, 0.70, 0.72)
SCENE.view_settings.view_transform = "Standard"
SCENE.view_settings.look = "Medium High Contrast" if "Medium High Contrast" in [item.name for item in SCENE.view_settings.bl_rna.properties["look"].enum_items] else "None"
SCENE.view_settings.exposure = 0.0
SCENE.view_settings.gamma = 1.0


def collection(name):
    result = bpy.data.collections.new(name)
    SCENE.collection.children.link(result)
    return result


ASSET = collection("01_WIND_THUNDER_WINGS")
PLAYER = collection("02_ORIGINAL_NEUTRAL_MANNEQUIN")
STUDIO = collection("03_STUDIO")
CAMERAS = collection("04_ORTHOGRAPHIC_CAMERAS")
RIGCOL = collection("00_ANIMATION_RIG")


def move_to(obj, target):
    for previous in list(obj.users_collection):
        previous.objects.unlink(obj)
    target.objects.link(obj)


def material(name, color, metallic=0.0, roughness=0.55, emission=0.0):
    result = bpy.data.materials.new(name)
    result.diffuse_color = (*color, 1.0)
    result.use_nodes = True
    shader = result.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (*color, 1.0)
    shader.inputs["Metallic"].default_value = metallic
    shader.inputs["Roughness"].default_value = roughness
    if emission:
        if "Emission Color" in shader.inputs:
            shader.inputs["Emission Color"].default_value = (*color, 1.0)
            shader.inputs["Emission Strength"].default_value = emission
        else:
            shader.inputs["Emission"].default_value = (*color, 1.0)
    return result


SILVER = material("Feather | silver-white ceramic", (0.73, 0.81, 0.84), 0.35)
EDGE = material("Feather | clean cut highlight", (0.92, 0.94, 0.91), 0.30)
SHADE = material("Feather | layered cool shadow", (0.34, 0.44, 0.49), 0.25)
GOLD = material("Thunder | fine warm gold inlay", (0.85, 0.57, 0.17), 0.70, 0.32, 0.22)
CYAN = material("Wind | pale cyan accent", (0.31, 0.80, 0.84), 0.15, 0.35, 0.75)
DARK = material("Harness | graphite", (0.095, 0.14, 0.16), 0.15)
ENERGY = material("Blink | gold energy", (1.0, 0.76, 0.25), 0.15, 0.25, 3.0)
FLASH = material("Blink | optional white burst", (1.0, 1.0, 1.0), 0.0, 0.3, 6.0)
CLOTH = material("Mannequin | mineral green tunic", (0.20, 0.31, 0.31))
CLOTH_LIGHT = material("Mannequin | shoulder cloth", (0.46, 0.55, 0.53))
BOOTS = material("Mannequin | dark boots", (0.11, 0.16, 0.18))
SKIN = material("Mannequin | neutral clay", (0.66, 0.70, 0.68))
STAGE = material("Studio | light neutral", (0.80, 0.83, 0.83))

ASSET_OBJECTS = []
BINDINGS = []
FEATHER_POSES = []
BONE_SPECS = [("mount", (0, 0.25, 1.50), None)]
SIDE_GROUPS = {}


def register_asset(obj, bone):
    ASSET_OBJECTS.append(obj)
    BINDINGS.append((obj, bone))
    obj["prototype_asset"] = "wind_thunder_wings"
    return obj


def cube(name, center, dimensions, mat, target=ASSET, bone=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=center)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = dimensions
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(mat)
    move_to(obj, target)
    if bone:
        register_asset(obj, bone)
    return obj


def segment(name, start, end, width, depth, mat, bone):
    a, b = Vector(start), Vector(end)
    obj = cube(name, (a + b) / 2, (width, depth, (b - a).length), mat, bone=bone)
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = (b - a).to_track_quat("Z", "Y")
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return obj


def feather_mesh(name, root, tip, width, thickness, bone, alternate=False):
    """A sharp asymmetrical prism with a raised ridge, not a rounded leaf."""
    root, tip = Vector(root), Vector(tip)
    axis = tip - root
    normal = Vector((-axis.z, 0, axis.x)).normalized()
    fractions = ((0.0, 0.30), (0.42, 0.52), (0.82, 0.22), (1.0, 0.0), (0.72, -0.45), (0.02, -0.32))
    perimeter = [root + axis * along + normal * width * across for along, across in fractions]
    vertices = []
    for sign in (-1, 1):
        for point in perimeter:
            vertices.append(tuple(point + Vector((0, sign * thickness / 2, 0))))
        vertices.append(tuple(root + axis * 0.48 + Vector((0, sign * thickness * 1.00, 0))))
    faces = []
    indices = []
    for side in (0, 1):
        start = side * 7
        for j in range(6):
            face = (start + 6, start + j, start + (j + 1) % 6)
            faces.append(tuple(reversed(face)) if side else face)
            indices.append(1 if j < 3 else (2 if alternate else 0))
    for j in range(6):
        faces.append((j, (j + 1) % 6, 7 + (j + 1) % 6, 7 + j))
        indices.append(2)
    mesh = bpy.data.meshes.new(name + "_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.clear()
    for mat in (SILVER, EDGE, SHADE):
        mesh.materials.append(mat)
    mesh.update()
    normal_mesh = bmesh.new()
    normal_mesh.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(normal_mesh, faces=list(normal_mesh.faces))
    normal_mesh.to_mesh(mesh)
    normal_mesh.free()
    obj = bpy.data.objects.new(name, mesh)
    ASSET.objects.link(obj)
    for polygon, index in zip(mesh.polygons, indices):
        polygon.material_index = index
        polygon.use_smooth = False
    register_asset(obj, bone)
    ridge_root = root + axis * 0.08
    ridge_tip = root + axis * 0.80
    for sign in (-1, 1):
        offset = Vector((0, sign * (thickness + 0.006), 0))
        segment(name + ("_gold_front" if sign < 0 else "_gold_back"), ridge_root + offset, ridge_tip + offset, 0.015, 0.011, GOLD, bone)
    return obj


def add_feather(side, family, index, root2, tip2, width, layer, group, folded_index):
    label = "R" if side > 0 else "L"
    root = Vector((side * root2[0], layer, root2[1]))
    tip = Vector((side * tip2[0], layer, tip2[1]))
    bone = f"{label}_{family}_{index:02d}"
    BONE_SPECS.append((bone, tuple(root), group))
    feather_mesh(bone, root, tip, width, 0.038, bone, index % 3 == 0)
    axis = tip - root
    desired = Vector((side * (0.02 + 0.022 * folded_index), 0, -1)).normalized() * axis.length
    angle = math.atan2(axis.x, axis.z) - math.atan2(desired.x, desired.z)
    fold_q = Quaternion((0, 1, 0), -angle)
    if family == "primary":
        folded_root = Vector((side * (0.32 + 0.036 * folded_index), 0.41 + 0.012 * folded_index, 1.76 - 0.013 * folded_index))
    elif family == "secondary":
        folded_root = Vector((side * (0.31 + 0.027 * folded_index), 0.34 + 0.012 * folded_index, 1.61 - 0.020 * folded_index))
    else:
        folded_root = Vector((side * (0.32 + 0.030 * folded_index), 0.27 + 0.006 * folded_index, 1.70 - 0.022 * folded_index))
    FEATHER_POSES.append({"bone": bone, "delta": folded_root - root, "rotation": fold_q})
    if family == "covert" and index in (1, 4, 7):
        accent_start = root + axis * 0.18 + Vector((0, -0.050, 0))
        accent_end = root + axis * 0.42 + Vector((0, -0.050, 0))
        segment(bone + "_pale_cyan", accent_start, accent_end, 0.045, 0.018, CYAN, bone)


for side in (-1, 1):
    label = "R" if side > 0 else "L"
    main, outer, fan = label + "_main", label + "_outer", label + "_fan"
    SIDE_GROUPS[side] = (main, outer, fan)
    BONE_SPECS.extend([
        (main, (side * 0.32, 0.29, 1.60), "mount"),
        (outer, (side * 1.15, 0.30, 1.99), main),
        (fan, (side * 1.95, 0.32, 2.17), outer),
    ])
    primary_data = [
        ((2.02, 2.20), (3.20, 2.00), 0.28),
        ((1.98, 2.13), (3.18, 1.61), 0.32),
        ((1.90, 2.07), (3.02, 1.20), 0.36),
        ((1.80, 2.02), (2.78, 0.89), 0.38),
        ((1.68, 1.97), (2.48, 0.64), 0.39),
        ((1.57, 1.90), (2.17, 0.52), 0.37),
        ((1.46, 1.83), (1.91, 0.48), 0.34),
    ]
    for index, (root, tip, width) in enumerate(primary_data):
        add_feather(side, "primary", index, root, tip, width, 0.40, fan, index)
    secondary_data = [
        ((0.54, 1.70), (0.66, 1.01), 0.31),
        ((0.74, 1.84), (0.91, 0.84), 0.35),
        ((0.96, 1.96), (1.17, 0.70), 0.37),
        ((1.18, 2.04), (1.43, 0.60), 0.38),
        ((1.39, 2.10), (1.70, 0.57), 0.35),
        ((1.57, 2.15), (1.93, 0.63), 0.31),
    ]
    for index, (root, tip, width) in enumerate(secondary_data):
        add_feather(side, "secondary", index, root, tip, width, 0.31, main if index < 3 else outer, index)
    covert_data = [
        ((0.33, 1.58), (0.72, 1.38), 0.25),
        ((0.50, 1.82), (0.90, 1.39), 0.27),
        ((0.73, 1.99), (1.12, 1.43), 0.28),
        ((0.96, 2.12), (1.35, 1.53), 0.28),
        ((1.22, 2.22), (1.62, 1.65), 0.28),
        ((1.48, 2.28), (1.92, 1.78), 0.28),
        ((1.75, 2.31), (2.22, 1.90), 0.26),
        ((2.02, 2.29), (2.52, 2.08), 0.23),
    ]
    for index, (root, tip, width) in enumerate(covert_data):
        add_feather(side, "covert", index, root, tip, width, 0.22, main if index < 3 else outer, index)

# A narrow mechanical backplate keeps the silhouette recognizably wearable.
cube("Harness_backplate", (0, 0.23, 1.43), (0.38, 0.16, 0.63), DARK, bone="mount")
cube("Harness_spine_gold", (0, 0.33, 1.44), (0.063, 0.06, 0.62), GOLD, bone="mount")
for side in (-1, 1):
    cube(f"Harness_socket_{side}", (side * 0.23, 0.27, 1.61), (0.17, 0.22, 0.19), GOLD, bone="mount")
    cube(f"Harness_socket_inset_{side}", (side * 0.23, 0.40, 1.61), (0.10, 0.018, 0.09), CYAN, bone="mount")
core = cube("Wind_core_diamond", (0, 0.35, 1.47), (0.18, 0.055, 0.18), CYAN, bone="mount")
core.rotation_euler.y = math.radians(45)

# VFX are real geometry, controlled by dedicated bones and absent in idle views.
for side in (-1, 1):
    label = "R" if side > 0 else "L"
    bone = label + "_energy"
    BONE_SPECS.append((bone, (side * 0.31, 0.19, 1.62), "mount"))
    points = [(0.34, 1.66), (0.87, 2.09), (1.16, 2.04), (1.73, 2.48), (2.01, 2.32), (2.73, 2.43)]
    for index, (start, end) in enumerate(zip(points, points[1:])):
        segment(f"{label}_blink_arc_{index}", (side * start[0], 0.15, start[1]), (side * end[0], 0.15, end[1]), 0.021, 0.022, ENERGY, bone)
BONE_SPECS.append(("optional_white_flash", (0, 0.16, 1.50), "mount"))
for index, (start, end) in enumerate((
    ((-0.90, 0.16, 1.50), (0.90, 0.16, 1.50)),
    ((0, 0.16, 0.60), (0, 0.16, 2.40)),
    ((-0.45, 0.16, 1.05), (0.45, 0.16, 1.95)),
    ((0.45, 0.16, 1.05), (-0.45, 0.16, 1.95)),
)):
    segment(f"Optional_white_flash_ray_{index}", start, end, 0.055, 0.024, FLASH, "optional_white_flash")

# Self-authored neutral mannequin, using the Minecraft 32-pixel / two-block scale.
cube("Neutral_head", (0, 0, 1.75), (0.50, 0.50, 0.50), SKIN, PLAYER)
cube("Neutral_torso", (0, 0, 1.125), (0.50, 0.25, 0.75), CLOTH, PLAYER)
for side in (-1, 1):
    cube(f"Neutral_arm_{side}", (side * 0.375, 0, 1.125), (0.25, 0.25, 0.75), CLOTH_LIGHT, PLAYER)
    cube(f"Neutral_hand_{side}", (side * 0.375, -0.006, 0.81), (0.25, 0.26, 0.13), SKIN, PLAYER)
    cube(f"Neutral_leg_{side}", (side * 0.125, 0, 0.375), (0.25, 0.25, 0.75), CLOTH, PLAYER)
    cube(f"Neutral_boot_{side}", (side * 0.125, -0.025, 0.10), (0.25, 0.30, 0.20), BOOTS, PLAYER)
    cube(f"Harness_front_strap_{side}", (side * 0.17, -0.140, 1.27), (0.063, 0.025, 0.39), DARK, PLAYER)
    cube(f"Harness_front_clasp_{side}", (side * 0.17, -0.158, 1.29), (0.075, 0.026, 0.060), GOLD, PLAYER)
cube("Tunic_neckline", (0, -0.137, 1.445), (0.21, 0.026, 0.085), CLOTH_LIGHT, PLAYER)

# All feathers have independent fold pivots underneath the three wing groups.
armature = bpy.data.armatures.new("WindThunder_deformation_bones")
RIG = bpy.data.objects.new("WindThunder_RIG", armature)
RIGCOL.objects.link(RIG)
bpy.context.view_layer.objects.active = RIG
RIG.select_set(True)
bpy.ops.object.mode_set(mode="EDIT")
for name, head, parent_name in BONE_SPECS:
    bone = armature.edit_bones.new(name)
    bone.head = head
    bone.tail = Vector(head) + Vector((0, 0, 0.20))
    if parent_name:
        bone.parent = armature.edit_bones[parent_name]
    bone.use_connect = False
bpy.ops.object.mode_set(mode="OBJECT")
RIG.show_in_front = True
RIG["world_axes"] = "+X right, +Y player back, +Z up; 1 unit = 1 Minecraft block"
RIG["design"] = "Original raptor-cut silver feathers, thin gold shafts, pale cyan wind accents"
RIG["clip_note"] = "Prototype articulation: 0.4-block magical miniature expands to full wings; rigid feather pivots remain editable"
for obj, bone_name in BINDINGS:
    group = obj.vertex_groups.new(name=bone_name)
    group.add(list(range(len(obj.data.vertices))), 1.0, "REPLACE")
    modifier = obj.modifiers.new("Rigid feather skin", "ARMATURE")
    modifier.object = RIG
    obj.parent = RIG
    obj.matrix_parent_inverse = RIG.matrix_world.inverted()
bpy.context.view_layer.update()
rest_points = [obj.matrix_world @ vertex.co for obj in ASSET_OBJECTS for vertex in obj.data.vertices]
REST_MIN = Vector(tuple(min(point[axis] for point in rest_points) for axis in range(3)))
REST_MAX = Vector(tuple(max(point[axis] for point in rest_points) for axis in range(3)))
MINI_SCALE = 0.4 / (REST_MAX.x - REST_MIN.x)
MINI_CENTER = (REST_MIN + REST_MAX) / 2
MOUNT_ANCHOR = Vector((0, 0.29, 1.48))


def local_rotation(bone_name, world_q):
    basis = armature.bones[bone_name].matrix_local.to_quaternion()
    return basis.inverted() @ world_q @ basis


def local_delta(bone_name, world_vector):
    return armature.bones[bone_name].matrix_local.to_quaternion().inverted() @ world_vector


def pose(fold=0.0, flap=0.0, sweep=0.0, fan=0.0, energy=0.0, miniature=0.0, flash=0.0):
    RIG.location = (0, 0, 0)
    RIG.rotation_mode = "XYZ"
    RIG.rotation_euler = (0, 0, 0)
    RIG.scale = (1, 1, 1)
    for bone in RIG.pose.bones:
        bone.rotation_mode = "QUATERNION"
        bone.location = (0, 0, 0)
        bone.rotation_quaternion = (1, 0, 0, 0)
        bone.scale = (1, 1, 1)
    for feather in FEATHER_POSES:
        bone = RIG.pose.bones[feather["bone"]]
        bone.location = local_delta(bone.name, feather["delta"] * fold)
        bone.rotation_quaternion = local_rotation(bone.name, Quaternion().slerp(feather["rotation"], fold))
    for side, names in SIDE_GROUPS.items():
        main, outer, fan_bone = names
        RIG.pose.bones[main].rotation_quaternion = local_rotation(main, Quaternion((0, 1, 0), math.radians(side * flap)) @ Quaternion((0, 0, 1), math.radians(side * sweep)))
        RIG.pose.bones[outer].rotation_quaternion = local_rotation(outer, Quaternion((0, 1, 0), math.radians(-side * flap * 0.35)))
        RIG.pose.bones[fan_bone].rotation_quaternion = local_rotation(fan_bone, Quaternion((0, 1, 0), math.radians(side * fan)))
        RIG.pose.bones[("R" if side > 0 else "L") + "_energy"].scale = (max(energy, 0.0001),) * 3
    RIG.pose.bones["optional_white_flash"].scale = (max(flash if ARGS.flash else 0, 0.0001),) * 3
    scale = 1.0 + (MINI_SCALE - 1.0) * miniature
    RIG.scale = (scale,) * 3
    RIG.location = MOUNT_ANCHOR * (1.0 - scale)
    bpy.context.view_layer.update()


def dropped_transform(height, rotation):
    RIG.rotation_euler.z = rotation
    RIG.location = Vector((0, 0, height)) - RIG.rotation_euler.to_quaternion() @ (MINI_CENTER * MINI_SCALE)


def insert_pose(frame):
    for bone in RIG.pose.bones:
        for prop in ("location", "rotation_quaternion", "scale"):
            bone.keyframe_insert(data_path=prop, frame=frame, group=bone.name)
    for prop in ("location", "rotation_euler", "scale"):
        RIG.keyframe_insert(data_path=prop, frame=frame, group="Root motion")


CLIPS = {}


def clip(name, length, samples, description):
    action = bpy.data.actions.new(name)
    action.use_fake_user = True
    RIG.animation_data_create()
    RIG.animation_data.action = action
    for frame, values in samples:
        settings = dict(values)
        drop = settings.pop("drop", None)
        pose(**settings)
        if drop:
            dropped_transform(drop[0], drop[1])
        insert_pose(frame)
    action["description"] = description
    action["fps"] = 24
    action["duration_seconds"] = length / 24
    action["loop"] = name in ("hover", "glide", "drop_spin_bob")
    # Legacy and slotted Blender actions are both kept usable without conversion.
    try:
        for curve in action.fcurves:
            for point in curve.keyframe_points:
                point.interpolation = "LINEAR" if name == "drop_spin_bob" and curve.data_path == "rotation_euler" else "BEZIER"
    except AttributeError:
        pass
    CLIPS[name] = {"action": action, "frames": length, "seconds": length / 24, "description": description}


clip("deploy", 7, [(1, {"miniature": 1}), (3, {"miniature": 0.76}), (6, {"miniature": 0.04, "flap": -3}), (8, {})], "0.4-block miniature grows into full wingspan; requested 0.3 s quantized to 7/24 s.")
hover_samples = []
for index in range(9):
    wave = math.sin(index * math.pi / 2)
    hover_samples.append((1 + index * 7.5, {"flap": -4 * wave, "fan": -2 * wave}))
clip("hover", 60, hover_samples, "0.8 Hz wingbeat: 1.25 s per cycle; 60 rendered frames show two complete cycles.")
clip("glide", 24, [(1, {"sweep": 10, "flap": 3}), (13, {"sweep": 11, "flap": 2}), (25, {"sweep": 10, "flap": 3})], "Restrained rearward-swept gliding pose; 1 s loop.")
clip("boost", 7, [(1, {}), (4, {"sweep": 30, "flap": 3, "energy": 0.3}), (8, {"sweep": 30, "flap": 2, "energy": 0.15})], "Wind boost: precise 30-degree rearward sweep; 0.292 s.")
clip("thunder_boost", 7, [(1, {"sweep": 30}), (4, {"sweep": 60, "flap": 3, "energy": 1.0}), (8, {"sweep": 60, "flap": 2, "energy": 0.5})], "Wind-thunder boost: precise 60-degree rearward sweep; 0.292 s.")
clip("blink", 6, [(1, {"sweep": 30}), (3, {"energy": 0.05}), (4, {"energy": 1.0, "flash": 1.0}), (5, {"energy": 0.35, "flash": 0.15}), (7, {})], "Thunder blink: maximum wingspan plus optional white burst; --flash enables the burst. 0.25 s.")
clip("retract", 8, [(1, {}), (4, {"miniature": 0.40}), (7, {"miniature": 0.96}), (9, {"miniature": 1})], "Returns to the 0.4-block glowing miniature at the back; 0.333 s.")
drop_samples = []
for frame in (1, 13, 25, 37, 49, 61, 73, 85, 97):
    phase = (frame - 1) / 96 * math.tau
    drop_samples.append((frame, {"miniature": 1, "drop": (0.28 + 0.025 * math.sin(phase * 2), phase)}))
clip("drop_spin_bob", 96, drop_samples, "Miniature dropped item turns once and bobs twice in four seconds.")


def detach_action():
    if RIG.animation_data:
        RIG.animation_data.action = None


detach_action()
pose()

# Neutral floor and actual area lighting also support a Cycles CPU fallback.
cube("Studio_floor", (0, 0, -0.075), (200, 200, 0.10), STAGE, STUDIO)
for name, location, power, size in (
    ("Key_softbox", (2, -4, 7), 1100, 5.0),
    ("Fill_softbox", (-4, -1, 4), 700, 4.0),
    ("Rim_softbox", (1, 5, 5), 950, 3.0),
):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = power
    data.shape = "DISK"
    data.size = size
    obj = bpy.data.objects.new(name, data)
    STUDIO.objects.link(obj)
    obj.location = location
    obj.rotation_euler = (Vector((0, 0.25, 1.4)) - obj.location).to_track_quat("-Z", "Y").to_euler()


def camera(name, location, target, scale):
    data = bpy.data.cameras.new(name)
    data.type = "ORTHO"
    data.ortho_scale = scale
    data.lens = 50
    obj = bpy.data.objects.new(name, data)
    CAMERAS.objects.link(obj)
    obj.location = location
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()
    return obj


CAM = {
    "orthographic_front": camera("CAM_01_front_orthographic", (0, -12, 1.25), (0, 0.25, 1.25), 7.75),
    "orthographic_side": camera("CAM_02_right_side_orthographic", (12, 0.25, 1.25), (0, 0.25, 1.25), 7.75),
    "orthographic_top": camera("CAM_03_top_orthographic", (0, 0.25, 12), (0, 0.25, 0), 7.75),
    "hero_45": camera("CAM_04_three_quarter", (6.8, -10.5, 6.4), (0, 0.30, 1.18), 7.40),
    "worn_idle": camera("CAM_05_worn_folded_back", (-3.6, 6.4, 3.0), (0, 0.12, 1.05), 4.25),
    "worn_flight": camera("CAM_06_worn_flight_back", (-5.2, 11, 5.0), (0, 0.25, 1.23), 7.60),
    "dropped": camera("CAM_07_miniature_dropped", (1.5, -2.4, 1.45), (0, 0, 0.28), 0.72),
}


def configure_engine():
    engines = {"workbench": "BLENDER_WORKBENCH", "eevee": "BLENDER_EEVEE_NEXT", "cycles": "CYCLES"}
    SCENE.render.engine = engines[ARGS.engine]
    SCENE.render.resolution_x = ARGS.width
    SCENE.render.resolution_y = ARGS.height
    SCENE.render.use_file_extension = True
    if ARGS.engine == "workbench":
        shade = SCENE.display.shading
        shade.light = "STUDIO"
        shade.studio_light = "paint.sl"
        shade.color_type = "MATERIAL"
        shade.show_shadows = True
        shade.show_cavity = True
        shade.cavity_type = "BOTH"
        shade.curvature_ridge_factor = 1.50
        shade.curvature_valley_factor = 1.10
        shade.cavity_ridge_factor = 1.15
        shade.cavity_valley_factor = 1.0
        shade.show_specular_highlight = True
        shade.show_object_outline = False
        shade.background_type = "VIEWPORT"
        shade.background_color = (0.80, 0.83, 0.83)
        SCENE.display.render_aa = "16"
    if SCENE.render.engine == "CYCLES":
        SCENE.cycles.device = "CPU"
        SCENE.cycles.samples = ARGS.samples
        SCENE.cycles.use_denoising = True
        SCENE.cycles.max_bounces = 4


configure_engine()


def set_view(name):
    detach_action()
    SCENE.frame_set(1)
    PLAYER.hide_render = name == "dropped"
    PLAYER.hide_viewport = name == "dropped"
    if name == "worn_idle":
        pose(miniature=1)
    elif name == "dropped":
        pose(miniature=1)
        dropped_transform(0.28, -0.28)
    else:
        pose()
    SCENE.camera = CAM[name]
    bpy.context.view_layer.update()


def projected_bounds(name):
    set_view(name)
    graph = bpy.context.evaluated_depsgraph_get()
    points = []
    world_points = []
    asset_points = []
    objects = ASSET_OBJECTS + ([] if name == "dropped" else list(PLAYER.objects))
    for obj in objects:
        evaluated = obj.evaluated_get(graph)
        for vertex in evaluated.data.vertices:
            point = evaluated.matrix_world @ vertex.co
            world_points.append(point)
            if obj in ASSET_OBJECTS:
                asset_points.append(point)
            points.append(world_to_camera_view(SCENE, SCENE.camera, point))
    xs, ys = [point.x for point in points], [point.y for point in points]
    return {
        "screen_bounds": [min(xs), min(ys), max(xs), max(ys)],
        "world_min": [min(point[axis] for point in world_points) for axis in range(3)],
        "world_max": [max(point[axis] for point in world_points) for axis in range(3)],
        "asset_world_dimensions": [max(point[axis] for point in asset_points) - min(point[axis] for point in asset_points) for axis in range(3)],
        "orthographic_scale": SCENE.camera.data.ortho_scale,
        "fully_in_frame": min(xs) >= 0 and max(xs) <= 1 and min(ys) >= 0 and max(ys) <= 1,
    }


# Check evaluated geometry, not just planned dimensions, before rendering.
VIEW_QA = {}
orthographic_names = ("orthographic_front", "orthographic_side", "orthographic_top")
for name in CAM:
    bounds = projected_bounds(name)
    factor = max(abs(value - 0.5) * 2 for value in bounds["screen_bounds"]) * 1.10
    if factor > 1:
        if name in orthographic_names:
            for ortho_name in orthographic_names:
                CAM[ortho_name].data.ortho_scale *= factor
        else:
            CAM[name].data.ortho_scale *= factor
for name in CAM:
    VIEW_QA[name] = projected_bounds(name)
    if not VIEW_QA[name]["fully_in_frame"]:
        raise RuntimeError("Camera crops evaluated geometry: " + name)
if abs(VIEW_QA["worn_idle"]["asset_world_dimensions"][0] - 0.4) > 0.005:
    raise RuntimeError("Evaluated idle wings do not match the 0.4-block width")
if abs(VIEW_QA["dropped"]["asset_world_dimensions"][0] - 0.4) > 0.04:
    raise RuntimeError("Evaluated dropped wings do not match the approximate 0.4-block width")


def render_png(path):
    SCENE.render.filepath = str(path)
    try:
        bpy.ops.render.render(write_still=True)
    except RuntimeError as error:
        if SCENE.render.engine == "CYCLES":
            raise
        print("GPU-free fallback to Cycles CPU:", error, flush=True)
        SCENE.render.engine = "CYCLES"
        SCENE.cycles.device = "CPU"
        SCENE.cycles.samples = ARGS.samples
        SCENE.cycles.use_denoising = True
        SCENE.cycles.max_bounces = 3
        bpy.ops.render.render(write_still=True)


STATIC_VIEWS = []
if ARGS.render:
    image_dir = OUT / "renders"
    image_dir.mkdir(exist_ok=True)
    for name in ("orthographic_front", "orthographic_side", "orthographic_top", "hero_45", "dropped", "worn_idle", "worn_flight"):
        set_view(name)
        path = image_dir / (name + ".png")
        render_png(path)
        STATIC_VIEWS.append(str(path))


ANIMATION_RENDERS = []
render_clips = [entry.strip() for entry in ARGS.render_clips.split(",") if entry.strip()]
if ARGS.animate and "hover" not in render_clips:
    render_clips.insert(0, "hover")
for name in render_clips:
    if name not in CLIPS:
        raise ValueError("Unknown clip: " + name)
    directory = OUT / ("frames" if name == "hover" else "frames_" + name)
    directory.mkdir(exist_ok=True)
    PLAYER.hide_render = name == "drop_spin_bob"
    PLAYER.hide_viewport = name == "drop_spin_bob"
    RIG.animation_data.action = CLIPS[name]["action"]
    SCENE.camera = CAM["dropped" if name == "drop_spin_bob" else "worn_flight"]
    SCENE.render.resolution_x = 768
    SCENE.render.resolution_y = 432
    for frame in range(1, CLIPS[name]["frames"] + 1):
        SCENE.frame_set(frame)
        render_png(directory / f"{frame:04d}.png")
    ANIMATION_RENDERS.append({"clip": name, "directory": str(directory), "fps": 24, "frames": CLIPS[name]["frames"]})


# Leave the saved file immediately inspectable at the full-span hero pose.
PLAYER.hide_render = False
PLAYER.hide_viewport = False
detach_action()
pose()
SCENE.camera = CAM["hero_45"]
SCENE.frame_start = 1
SCENE.frame_end = 60
SCENE.frame_set(1)
SCENE.render.resolution_x = ARGS.width
SCENE.render.resolution_y = ARGS.height
RIG.animation_data.action = CLIPS["hover"]["action"]
SCENE.frame_set(1)
SCENE["asset_status"] = "ART PROTOTYPE, not yet game-integrated or collision validated"
SCENE["three_view_scale"] = "Front, right, top: all orthographic, identical 7.75-unit horizontal scale at 16:9"
SCENE["readme"] = "Eight named Actions on WindThunder_RIG; select the rig and change Action to preview. All artwork is original."
SCENE["optional_white_flash_enabled"] = ARGS.flash

blend_path = OUT / "wind_thunder_wings.blend"
bpy.ops.wm.save_as_mainfile(filepath=str(blend_path))
bpy.ops.object.select_all(action="DESELECT")
RIG.select_set(True)
for obj in ASSET_OBJECTS:
    obj.select_set(True)
bpy.context.view_layer.objects.active = RIG
glb_path = OUT / "wind_thunder_wings.glb"
gltf_options = {
    "filepath": str(glb_path),
    "export_format": "GLB",
    "use_selection": True,
    "export_animations": True,
    "export_skins": True,
    "export_yup": True,
    "export_materials": "EXPORT",
    "export_cameras": False,
    "export_lights": False,
    "export_apply": False,
    "export_animation_mode": "ACTIONS",
    "export_nla_strips": True,
    "export_frame_range": False,
    "export_force_sampling": True,
}
supported = bpy.ops.export_scene.gltf.get_rna_type().properties
gltf_options = {key: value for key, value in gltf_options.items() if key in supported}
bpy.ops.export_scene.gltf(**gltf_options)
glb_bytes = glb_path.read_bytes()
json_length, json_kind = struct.unpack_from("<II", glb_bytes, 12)
if glb_bytes[:4] != b"glTF" or json_kind != 0x4E4F534A:
    raise RuntimeError("Exporter did not produce a valid GLB container")
glb_document = json.loads(glb_bytes[20:20 + json_length])
exported_clips = [entry.get("name", "") for entry in glb_document.get("animations", [])]


def minecraft_vector(value):
    """Keep block units; map Blender X,Y,Z to front-view-right, up, back."""
    return [round(float(value[0]), 6), round(float(value[2]), 6), round(float(value[1]), 6)]


def srgb_byte(linear):
    value = min(1.0, max(0.0, float(linear)))
    encoded = value * 12.92 if value <= 0.0031308 else 1.055 * value ** (1 / 2.4) - 0.055
    return round(encoded * 255)


def validate_minecraft_mesh(payload):
    """Fail before handoff if the renderer-facing arrays cannot be consumed safely."""
    materials = payload["materials"]
    bone_names = {entry["name"] for entry in payload["bones"]}
    vertex_total, triangle_total = 0, 0
    for index, entry in enumerate(materials):
        if entry["id"] != index or len(entry["rgb"]) != 3 or any(not isinstance(value, int) or not 0 <= value <= 255 for value in entry["rgb"]):
            raise ValueError("Invalid material RGB or index")
    for mesh in payload["meshes"]:
        points, faces = mesh["vertices"], mesh["triangles"]
        if mesh["bone"] not in bone_names or mesh["side"] not in ("left", "right", "center"):
            raise ValueError("Unknown rigid mesh binding: " + mesh["name"])
        if len(faces) != len(mesh["normals"]) or len(faces) != len(mesh["material_ids"]):
            raise ValueError("Triangle attribute counts differ: " + mesh["name"])
        if any(len(point) != 3 or any(not math.isfinite(value) for value in point) for point in points):
            raise ValueError("Non-finite vertex: " + mesh["name"])
        for face, normal, material_id in zip(faces, mesh["normals"], mesh["material_ids"]):
            if len(face) != 3 or len(set(face)) != 3 or any(not isinstance(index, int) or not 0 <= index < len(points) for index in face):
                raise ValueError("Triangle index out of range or repeated: " + mesh["name"])
            if len(normal) != 3 or any(not math.isfinite(value) for value in normal) or abs(sum(value * value for value in normal) - 1) > 0.0001:
                raise ValueError("Triangle normal is not unit length: " + mesh["name"])
            if not isinstance(material_id, int) or not 0 <= material_id < len(materials):
                raise ValueError("Triangle material out of range: " + mesh["name"])
        vertex_total += len(points)
        triangle_total += len(faces)
    return {"valid": True, "mesh_count": len(payload["meshes"]), "vertex_count": vertex_total, "triangle_count": triangle_total, "material_count": len(materials), "bone_count": len(bone_names)}


def export_minecraft_mesh(path):
    """Read original rest meshes only, leaving the scene, rig, and Actions untouched."""
    source_materials = sorted({mat.name: mat for obj in ASSET_OBJECTS for mat in obj.data.materials}.values(), key=lambda entry: entry.name)
    material_lookup = {mat.name: index for index, mat in enumerate(source_materials)}
    material_records = []
    for index, mat in enumerate(source_materials):
        shader = mat.node_tree.nodes.get("Principled BSDF") if mat.use_nodes else None
        emission_color = (0, 0, 0)
        emission_strength = 0.0
        if shader:
            emission_input = shader.inputs.get("Emission Color") or shader.inputs.get("Emission")
            if emission_input:
                emission_color = tuple(emission_input.default_value[:3])
            strength_input = shader.inputs.get("Emission Strength")
            emission_strength = float(strength_input.default_value) if strength_input else 0.0
            if not any(value > 0 for value in emission_color):
                emission_strength = 0.0
        material_records.append({
            "id": index,
            "name": mat.name,
            "rgb": [srgb_byte(value) for value in mat.diffuse_color[:3]],
            "linear_rgb": [round(float(value), 6) for value in mat.diffuse_color[:3]],
            "alpha": round(float(mat.diffuse_color[3]), 6),
            "emission_rgb": [srgb_byte(value) for value in emission_color],
            "emission_strength": round(emission_strength, 6),
        })
    specifications = {name: {"head": head, "parent": parent} for name, head, parent in BONE_SPECS}
    feather_bones = {entry["bone"] for entry in FEATHER_POSES}
    group_names = {name for groups in SIDE_GROUPS.values() for name in groups}
    bones = [{"name": name, "parent": entry["parent"], "pivot": minecraft_vector(entry["head"])} for name, entry in specifications.items()]
    rig_inverse = RIG.matrix_world.inverted()
    records = []
    for obj, bone_name in BINDINGS:
        mesh = obj.data
        mesh.calc_loop_triangles()
        # Removing the root transform prevents a selected miniature/drop Action
        # from accidentally baking scale or bobbing into the exported rest asset.
        matrix = rig_inverse @ obj.matrix_world
        normal_matrix = matrix.to_3x3().inverted().transposed()
        points = [minecraft_vector(matrix @ vertex.co) for vertex in mesh.vertices]
        faces, normals, material_ids = [], [], []
        # The Y/Z exchange has determinant -1, so reverse winding to retain CCW.
        reverse_winding = matrix.to_3x3().determinant() > 0
        for triangle in mesh.loop_triangles:
            face = list(triangle.vertices)
            if reverse_winding:
                face[1], face[2] = face[2], face[1]
            normal = (normal_matrix @ triangle.normal).normalized()
            faces.append(face)
            normals.append(minecraft_vector(normal))
            slot = mesh.polygons[triangle.polygon_index].material_index
            material_ids.append(material_lookup[mesh.materials[slot].name])
        side = "left" if bone_name.startswith("L_") else "right" if bone_name.startswith("R_") else "center"
        parent_group = bone_name
        while parent_group not in group_names and specifications[parent_group]["parent"]:
            parent_group = specifications[parent_group]["parent"]
        is_feather = bone_name in feather_bones
        channel = "white_flash" if bone_name == "optional_white_flash" else "thunder_energy" if bone_name.endswith("_energy") else "body"
        records.append({
            "name": obj.name,
            "side": side,
            "bone": bone_name,
            "wing_group": parent_group,
            "pivot": minecraft_vector(specifications[bone_name]["head"]),
            "feather": bone_name if is_feather else None,
            "feather_root": minecraft_vector(specifications[bone_name]["head"]) if is_feather else None,
            "visibility_channel": channel,
            "default_visible": channel == "body",
            "vertices": points,
            "triangles": faces,
            "normals": normals,
            "material_ids": material_ids,
        })
    payload = {
        "schema": "fenglei.rigid_triangle_mesh",
        "schema_version": 1,
        "asset": "wind_thunder_wings",
        "coordinates": {
            "unit": "minecraft_block",
            "blocks_per_unit": 1,
            "x": "front_view_right",
            "y": "up",
            "z": "player_back",
            "origin": "player_feet",
            "front_face": "counter_clockwise",
            "source_axes": "Blender X,Y,Z converted to Minecraft X,Z,Y",
        },
        "attributes": {"normals": "one flat unit normal per triangle", "material_ids": "one index into materials per triangle", "rgb": "sRGB integers 0..255", "binding": "each mesh is rigidly attached to its named bone; pivots are absolute rest positions"},
        "mount": {"pivot": minecraft_vector(MOUNT_ANCHOR), "miniature_scale": MINI_SCALE, "miniature_width_blocks": 0.4},
        "animation_contract": {
            "payload": "rest geometry and pivots only; original keyframed animations remain in BLEND/GLB",
            "hover_hz": 0.8,
            "wind_sweep_degrees": 30,
            "thunder_sweep_degrees": 60,
            "right_wing_positive_sweep": "negative rotation around exported +Y",
            "right_wing_positive_source_flap": "negative rotation around exported +Z",
            "white_flash_optional": True,
        },
        "materials": material_records,
        "bones": bones,
        "meshes": records,
    }
    summary = validate_minecraft_mesh(payload)
    payload["statistics"] = summary
    path.write_text(json.dumps(payload, separators=(",", ":"), allow_nan=False), encoding="utf-8")
    if validate_minecraft_mesh(json.loads(path.read_text(encoding="utf-8"))) != summary:
        raise RuntimeError("Minecraft mesh failed its serialized round-trip check")
    return summary


vertices = sum(len(obj.data.vertices) for obj in ASSET_OBJECTS)
polygons = sum(len(obj.data.polygons) for obj in ASSET_OBJECTS)
triangles = sum(sum(len(poly.vertices) - 2 for poly in obj.data.polygons) for obj in ASSET_OBJECTS)
manifest = {
    "asset": "Wind / Thunder Wings",
    "status": "original art prototype; not a completed Minecraft mod",
    "blender_version": bpy.app.version_string,
    "axis_convention": "+X right, +Y back, +Z up; 1 Blender unit = 1 Minecraft block",
    "dimensions_intent": {"span_blocks": 6.4, "player_height_blocks": 2.0, "idle_width_blocks": 0.4, "drop_width_target_blocks": 0.4},
    "small_wings": {"normalization_scale": MINI_SCALE, "unrotated_actual_width_blocks": (REST_MAX.x - REST_MIN.x) * MINI_SCALE, "idle_anchor": list(MOUNT_ANCHOR), "white_flash_enabled": ARGS.flash},
    "design": {"layers_per_side": 3, "feathers_per_side": 21, "palette": ["silver-white", "thin warm gold", "pale cyan", "graphite"]},
    "geometry": {"mesh_objects": len(ASSET_OBJECTS), "vertices": vertices, "polygons": polygons, "triangles": triangles, "bones": len(armature.bones)},
    "render_engine": SCENE.render.engine,
    "view_validation": VIEW_QA,
    "glb_validation": {"animation_names": exported_clips, "expected_clip_count": len(CLIPS), "all_clips_present": all(name in exported_clips for name in CLIPS), "mesh_count": len(glb_document.get("meshes", [])), "node_count": len(glb_document.get("nodes", []))},
    "renders": STATIC_VIEWS,
    "animation_renders": ANIMATION_RENDERS,
    "clips": {name: {key: value for key, value in entry.items() if key != "action"} for name, entry in CLIPS.items()},
    "files": {"blend": str(blend_path), "glb": str(glb_path)},
    "limitations": ["Art prototype only: no Minecraft renderer, item registration, gameplay, or runtime UI is included.", "Layered feather folding is stylized and still needs in-game collision and silhouette validation.", "Workbench renders show color and geometry but not physically accurate metal or bloom."],
}
if ARGS.export_minecraft:
    minecraft_path = OUT / "wind_thunder_wings.mesh.json"
    manifest["minecraft_mesh_validation"] = export_minecraft_mesh(minecraft_path)
    manifest["files"]["minecraft_mesh"] = str(minecraft_path)
(OUT / "asset_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print("ART_ASSET_RESULT=" + json.dumps(manifest), flush=True)
