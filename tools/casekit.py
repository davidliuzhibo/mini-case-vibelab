"""Blender helpers for the miniature crime scenes: boxes, textured materials, nodes, AI models, export.

Imported by make_cases.py (run inside Blender 4.2). Game frame everywhere: meters, +x right, +y up,
+z toward the player; Blender is z-up, so a game point (x, y, z) is Blender (x, -z, y), and the
glTF exporter (export_yup) turns it back. A prop is an empty named by its id, placed where the
prop turns (a hinge) or at its middle, with the prop's meshes parented under it; the game finds it
by name and moves it.
"""
import math
import os

import bmesh
import bpy

HERE = os.path.dirname(os.path.abspath(__file__))
LAB = os.path.join(HERE, "..", "..", "..", "assets_lab")
TEX_DIRS = [os.path.join(LAB, "polyhaven"), os.path.join(LAB, "luban", "tex")]
FONT = os.path.join(LAB, "luban", "fonts", "NotoSerifSC-Bold.otf")
TILE_PX = 512
DETAIL_MAPS = False

_materials = {}
_font = None


def reset():
    global _font
    bpy.ops.wm.read_factory_settings(use_empty=True)
    _materials.clear()
    _font = None


def B(p):
    """Game point -> Blender point."""
    x, y, z = p
    return (x, -z, y)


# ---- Materials ------------------------------------------------------------------------------------

def tex(name, uv=12.0, tint=(1.0, 1.0, 1.0), roughness=None, metallic=0.0):
    """A Principled material from a texture set (diff, nor, rough) found in TEX_DIRS/<name>/.
    [uv]: repeats per meter of model (the scenes are 1:20, so real textures repeat often)."""
    key = ("tex", name, uv, tint, roughness, metallic)
    if key in _materials:
        return _materials[key]
    folder = next(os.path.join(d, name) for d in TEX_DIRS if os.path.isdir(os.path.join(d, name)))
    mat = bpy.data.materials.new(f"{name}")
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    bsdf.inputs["Metallic"].default_value = metallic
    coord = nt.nodes.new("ShaderNodeTexCoord")
    mapping = nt.nodes.new("ShaderNodeMapping")
    mapping.inputs["Scale"].default_value = (uv / 4.0, uv / 4.0, 1.0)
    nt.links.new(coord.outputs["UV"], mapping.inputs["Vector"])

    def image(file, non_color=False):
        path = os.path.join(folder, file)
        if not os.path.exists(path):
            return None
        node = nt.nodes.new("ShaderNodeTexImage")
        node.image = bpy.data.images.load(path, check_existing=True)
        # At 1:20 a 512 px tile is already finer than the headset can show; it keeps the APK small.
        if node.image.size[0] > TILE_PX:
            node.image.scale(TILE_PX, TILE_PX)
        if non_color:
            node.image.colorspace_settings.name = "Non-Color"
        nt.links.new(mapping.outputs["Vector"], node.inputs["Vector"])
        return node

    diff = image("diff.jpg")
    if diff:
        if tint != (1.0, 1.0, 1.0):
            mix = nt.nodes.new("ShaderNodeMix")
            mix.data_type = "RGBA"
            mix.blend_type = "MULTIPLY"
            mix.inputs["Factor"].default_value = 1.0
            mix.inputs[7].default_value = (*tint, 1.0)
            nt.links.new(diff.outputs["Color"], mix.inputs[6])
            nt.links.new(mix.outputs[2], bsdf.inputs["Base Color"])
        else:
            nt.links.new(diff.outputs["Color"], bsdf.inputs["Base Color"])
    # Only the color: at this scale a normal or roughness map is too fine to see, and each one is
    # a megabyte or more in the APK.
    rough = image("rough.jpg", non_color=True) if DETAIL_MAPS else None
    if rough and roughness is None:
        nt.links.new(rough.outputs["Color"], bsdf.inputs["Roughness"])
    else:
        bsdf.inputs["Roughness"].default_value = roughness if roughness is not None else 0.6
    nor = image("nor.jpg", non_color=True) if DETAIL_MAPS else None
    if nor:
        nm = nt.nodes.new("ShaderNodeNormalMap")
        nm.inputs["Strength"].default_value = 0.8
        nt.links.new(nor.outputs["Color"], nm.inputs["Color"])
        nt.links.new(nm.outputs["Normal"], bsdf.inputs["Normal"])
    _materials[key] = mat
    return mat


def picture(path, name):
    """A matte material showing one image across the face (a painting, a poster)."""
    key = ("pic", path)
    if key in _materials:
        return _materials[key]
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    img = nt.nodes.new("ShaderNodeTexImage")
    img.image = bpy.data.images.load(path, check_existing=True)
    if img.image.size[0] > 512:
        img.image.scale(512, 512)
    nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.8
    _materials[key] = mat
    return mat


def flat(name, color, roughness=0.6, metallic=0.0, emission=None):
    key = ("flat", name)
    if key in _materials:
        return _materials[key]
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1.0)
        bsdf.inputs["Emission Strength"].default_value = 1.0
    _materials[key] = mat
    return mat


def assign(ob, mat):
    ob.data.materials.clear()
    ob.data.materials.append(mat)


# ---- Shapes ---------------------------------------------------------------------------------------

def _finish(ob, name, mat, smooth=False, bevel=0.0):
    ob.name = name
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if bevel > 0:
        m = ob.modifiers.new("bevel", "BEVEL")
        m.width = bevel
        m.segments = 2
        m.limit_method = "ANGLE"
        bpy.ops.object.modifier_apply(modifier="bevel")
    for p in ob.data.polygons:
        p.use_smooth = smooth
    cube_uv(ob)
    assign(ob, mat)
    return ob


def cube_uv(ob):
    """Box-projected UVs, one unit per meter (materials scale them)."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    uv = bm.loops.layers.uv.verify()
    for f in bm.faces:
        n = f.normal
        ax = max(range(3), key=lambda i: abs(n[i]))
        for loop in f.loops:
            co = loop.vert.co
            if ax == 0:
                loop[uv].uv = (co.y, co.z)
            elif ax == 1:
                loop[uv].uv = (co.x, co.z)
            else:
                loop[uv].uv = (co.x, co.y)
    bm.to_mesh(ob.data)
    bm.free()


def box(name, size, center, mat, bevel=0.0006, yaw=0.0, pitch=0.0):
    """A box: [size] (game x, y, z, meters) centered at game [center], turned [yaw] degrees about y;
    [pitch] tilts it about x (negative lowers its far edge, as a back roof slope)."""
    bpy.ops.mesh.primitive_cube_add(size=1)
    ob = bpy.context.object
    sx, sy, sz = size
    ob.scale = (sx, sz, sy)
    ob.rotation_euler = (math.radians(pitch), 0, math.radians(yaw))
    ob.location = B(center)
    return _finish(ob, name, mat, bevel=bevel if min(size) > bevel * 3 else 0.0)


def cyl(name, radius, height, center, mat, axis="y", verts=24, smooth=True, radius2=None):
    """A cylinder (or a cone frustum with [radius2] at the top) along game [axis], centered at [center]."""
    if radius2 is None:
        bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=height, vertices=verts)
    else:
        bpy.ops.mesh.primitive_cone_add(radius1=radius, radius2=radius2, depth=height, vertices=verts)
    ob = bpy.context.object
    ob.rotation_euler = {"y": (0, 0, 0), "x": (0, math.pi / 2, 0), "z": (math.pi / 2, 0, 0)}[axis]
    ob.location = B(center)
    return _finish(ob, name, mat, smooth=smooth)


def sphere(name, radius, center, mat, scale=(1, 1, 1), segments=16):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=radius, segments=segments, ring_count=segments // 2)
    ob = bpy.context.object
    sx, sy, sz = scale
    ob.scale = (sx, sz, sy)
    ob.location = B(center)
    return _finish(ob, name, mat, smooth=True)


def plane(name, size, center, mat, facing="+z", fit=True):
    """A flat rectangle [size] (w, h) centered at [center], facing game +z, -z, +x, -x or +y.
    [fit]: the material's image spans the face once (a picture); otherwise it tiles like a box."""
    w, h = size
    bpy.ops.mesh.primitive_plane_add(size=1)
    ob = bpy.context.object
    ob.scale = (w, h, 1)
    rot = {"+z": (math.pi / 2, 0, 0), "-z": (math.pi / 2, 0, math.pi), "+y": (0, 0, 0),
           "+x": (math.pi / 2, 0, math.pi / 2), "-x": (math.pi / 2, 0, -math.pi / 2), "-y": (math.pi, 0, 0)}[facing]
    ob.rotation_euler = rot
    ob.location = B(center)
    ob.name = name
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if not fit:
        cube_uv(ob)
    assign(ob, mat)
    return ob


def text(name, s, size, center, mat, facing="+z", vertical=False, depth=0.0003):
    """Characters [size] tall standing out [depth], centered at [center], facing game +z (or -y for underneath)."""
    global _font
    if _font is None:
        _font = bpy.data.fonts.load(FONT)
    content = "\n".join(s) if vertical else s
    cu = bpy.data.curves.new(name, "FONT")
    cu.body = content
    cu.font = _font
    cu.size = size
    cu.align_x = "CENTER"
    cu.align_y = "CENTER"
    cu.extrude = depth / 2
    ob = bpy.data.objects.new(name, cu)
    bpy.context.scene.collection.objects.link(ob)
    rot = {"+z": (math.pi / 2, 0, 0), "-z": (math.pi / 2, 0, math.pi), "+y": (0, 0, 0), "-y": (math.pi, 0, 0),
           "+x": (math.pi / 2, 0, math.pi / 2), "-x": (math.pi / 2, 0, -math.pi / 2)}[facing]
    ob.rotation_euler = rot
    ob.location = B(center)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.convert(target="MESH")
    ob = bpy.context.object
    ob.select_set(False)
    return _finish(ob, name, mat)


def legs(name, x0, x1, z0, z1, h, w, mat, y0=0.0):
    """Four square legs [h] tall under a rectangle, inset by half their width."""
    out = []
    for i, (x, z) in enumerate([(x0, z0), (x1, z0), (x0, z1), (x1, z1)]):
        out.append(box(f"{name}_leg{i}", (w, h, w), (x + (w / 2 if x == x0 else -w / 2), y0 + h / 2, z + (w / 2 if z == z0 else -w / 2)), mat, bevel=0))
    return out


def table(name, center, size, mat, top=0.003, leg=0.004):
    """A table: top [size] (w, h to the top surface, d) whose middle is at [center] on the floor."""
    x, y, z = center
    w, h, d = size
    parts = [box(f"{name}_top", (w, top, d), (x, y + h - top / 2, z), mat)]
    parts += legs(name, x - w / 2, x + w / 2, z - d / 2, z + d / 2, h - top, leg, mat, y0=y)
    return parts


# ---- Nodes, AI models, export ---------------------------------------------------------------------

def node(name, at):
    """An empty named [name] at game point [at]: the game moves it, its children go along."""
    e = bpy.data.objects.new(name, None)
    e.empty_display_size = 0.01
    e.location = B(at)
    bpy.context.scene.collection.objects.link(e)
    return e


def attach(parent, objects):
    """Parents [objects] to [parent], keeping where they are."""
    bpy.context.view_layer.update()
    for o in objects:
        mw = o.matrix_world.copy()
        o.parent = parent
        o.matrix_parent_inverse = parent.matrix_world.inverted()
        o.matrix_world = mw
    return parent


def prop(name, at, objects):
    """A prop node at [at] holding [objects]."""
    return attach(node(name, at), objects)


def ai(path, name, at, yaw=0.0, scale=1.0):
    """Imports a prepared AI model (tools/blender/prep_ai.py: real size, standing on its origin),
    standing at game [at], turned [yaw] degrees (0 faces the player)."""
    before = set(bpy.context.scene.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.context.scene.objects if o not in before]
    meshes = [o for o in new if o.type == "MESH"]
    for o in new:
        if o.type != "MESH":
            bpy.data.objects.remove(o)
    ob = meshes[0]
    ob.parent = None
    ob.name = name
    ob.scale = (scale, scale, scale)
    ob.rotation_euler = (0, 0, math.radians(yaw))
    ob.location = B(at)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.select_all(action="DESELECT")
    ob.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    ob.select_set(False)
    return ob


def descendants(o):
    out = [o]
    for c in o.children:
        out += descendants(c)
    return out


def export(path, roots):
    """Writes [roots] and everything under them to one GLB (triangulated, with tangents)."""
    objs = [d for r in roots for d in descendants(r)]
    for ob in objs:
        if ob.type != "MESH":
            continue
        m = ob.modifiers.new("tri", "TRIANGULATE")
        bpy.context.view_layer.objects.active = ob
        bpy.ops.object.modifier_apply(modifier="tri")
    bpy.ops.object.select_all(action="DESELECT")
    for ob in objs:
        ob.select_set(True)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=path,
        export_format="GLB",
        use_selection=True,
        export_yup=True,
        export_tangents=True,
        export_image_format="JPEG",
        export_jpeg_quality=85,
    )
    tris = sum(len(o.data.polygons) for o in objs if o.type == "MESH")
    print("wrote", path, tris, "triangles", os.path.getsize(path) // 1024, "KB")
