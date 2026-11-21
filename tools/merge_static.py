"""Merge a case model's unmoving parts by material: fewer entities and draw calls, the same picture.
blender -b -P merge_static.py -- <in.glb> <out.glb> <keep names, comma separated>
Props (opened, lifted or picked up) and figurines keep their nodes and names; everything else is joined.
"""
import bpy, sys
a = sys.argv[sys.argv.index("--") + 1:]
keep = set(a[2].split(","))
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=a[0])
keep = {k for k in keep if not k.startswith("scene_")}  # the root holds everything; it is never a reason to keep a child
def kept(o):
    while o:
        if o.name.split(".")[0] in keep: return True
        o = o.parent
    return False
static = [o for o in bpy.context.scene.objects if o.type == "MESH" and not kept(o)]
before = sum(1 for o in bpy.context.scene.objects if o.type == "MESH")
# Split by material, then join per material.
groups = {}
for o in static:
    bpy.ops.object.select_all(action="DESELECT"); o.select_set(True); bpy.context.view_layer.objects.active = o
    world = o.matrix_world.copy(); o.parent = None; o.matrix_world = world
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if len(o.data.materials) > 1:
        bpy.ops.mesh.separate(type="MATERIAL")
for o in [x for x in bpy.context.scene.objects if x.type == "MESH" and not kept(x)]:
    groups.setdefault(o.data.materials[0].name if o.data.materials else "", []).append(o)
n = 0
for name, objs in groups.items():
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs: o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    if len(objs) > 1: bpy.ops.object.join()
    bpy.context.object.name = f"static_{n}"; n += 1
root = next((o for o in bpy.context.scene.objects if o.name.startswith("scene_")), None)
if root:
    for o in bpy.context.scene.objects:
        if o.name.startswith("static_"):
            o.parent = root; o.matrix_parent_inverse = root.matrix_world.inverted()
after = sum(1 for o in bpy.context.scene.objects if o.type == "MESH")
print("MERGED", a[0].split("/")[-1], before, "->", after)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=a[1], use_selection=True, export_format="GLB", export_image_format="AUTO", export_tangents=False, export_animations=False)
