"""Small pictures for the clue cards: every prop and figurine of a case model, alone, seen from the
front and a little above, on a transparent ground.

blender -b --factory-startup --python tools/render_thumbs.py -- <out_dir> <ids.txt> <case.glb> [<case.glb> ...]

ids.txt: one node name per line (prop ids from core/Cases.kt and fig_<person>). Each found node
(with everything under it) is rendered to <out_dir>/<name>.png, 256x256, Cycles at a few samples.
"""
import math
import os
import sys

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
OUT, IDS, GLBS = args[0], args[1], args[2:]
os.makedirs(OUT, exist_ok=True)
WANT = {l.strip() for l in open(IDS) if l.strip()}


def setup_scene():
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.samples = 24
    sc.cycles.use_denoising = False
    sc.render.resolution_x = sc.render.resolution_y = 256
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.view_settings.view_transform = "Standard"
    w = bpy.data.worlds.new("w")
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs[0].default_value = (0.9, 0.88, 0.84, 1)
    w.node_tree.nodes["Background"].inputs[1].default_value = 0.9
    sc.world = w
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3.0
    sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(30))
    sc.collection.objects.link(sun)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.type = "ORTHO"
    sc.collection.objects.link(cam)
    sc.camera = cam
    return cam


def subtree(o):
    out = [o]
    for c in o.children:
        out += subtree(c)
    return out


def render(node, cam):
    keep = set(subtree(node))
    meshes = [o for o in keep if o.type == "MESH"]
    if not meshes:
        return False
    for o in bpy.context.scene.objects:
        if o.type == "MESH":
            o.hide_render = o not in keep
    pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    mid = (lo + hi) / 2
    size = max((hi - lo).length, 1e-4)
    # Blender is z-up after the glTF import: the player looks along +y (from -y), 30 degrees down.
    d = Vector((0, -math.cos(math.radians(30)), math.sin(math.radians(30))))
    cam.location = mid + d * size * 4
    cam.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    cam.data.ortho_scale = size * 1.05
    cam.data.clip_start = size * 0.1
    cam.data.clip_end = size * 10
    bpy.context.scene.render.filepath = os.path.join(OUT, node.name.split(".")[0] + ".png")
    bpy.ops.render.render(write_still=True)
    return True


done = []
for glb in GLBS:
    bpy.ops.wm.read_factory_settings(use_empty=True)
    cam = setup_scene()
    bpy.ops.import_scene.gltf(filepath=glb)
    for o in list(bpy.context.scene.objects):
        name = o.name.split(".")[0]
        if name in WANT and name not in done and render(o, cam):
            done.append(name)
print("THUMBS", len(done), sorted(done))
print("MISSING", sorted(WANT - set(done)))
