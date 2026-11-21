"""Builds the app icon from one of the game's models in front of a textured disc with a rim.

blender -b --factory-startup --python make_icon.py -- <res_dir> <model.glb> <disc_texture.jpg> <rim r,g,b> <tilt_deg> <turn_deg> <scale> [preview.png]
Writes the PICO 3D icon layers (drawable/icon_3d_layer_{0,1}.png: 0 = the disc, 1 = the model),
their SDF masks (icon_3d_sdf_*.png) and the flat icons (mipmap-xxxhdpi/ic_spatial_launcher.png,
drawable/ic_launcher_foreground.png). An SDF here is the distance inside the shape, ramping from
0 at the edge to 1 at 32 px in (1024 px canvas), in all four channels, matching the SDK template.
With a preview path, only the composite is written there.
"""
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
res, model_path, disc_tex = args[0], args[1], args[2]
rim_color = np.array([float(v) for v in args[3].split(",")])
tilt, turn, scale = float(args[4]), float(args[5]), float(args[6])
preview_path = args[7] if len(args) > 7 else None
preview = 512 if preview_path else 0
N = preview or 1024

# --- Foreground render ---------------------------------------------------------------------
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "BLENDER_EEVEE_NEXT"
sc.render.resolution_x = sc.render.resolution_y = N
sc.render.film_transparent = True
sc.view_settings.view_transform = "Standard"
world = bpy.data.worlds.new("w")
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0.5, 0.45, 0.4, 1)
world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
sc.world = world

# Camera looks along +Y; screen x = world x, screen up = world z, canvas spans -1..1.
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
cam.data.type = "ORTHO"
cam.data.ortho_scale = 2.0
cam.location = (0, -10, 0)
cam.rotation_euler = (math.radians(90), 0, 0)
sc.collection.objects.link(cam)
sc.camera = cam

sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
sun.data.energy = 3.5
sun.data.color = (1.0, 0.93, 0.82)
sun.rotation_euler = (math.radians(55), math.radians(-35), math.radians(-20))
sc.collection.objects.link(sun)


def material(name, color=None, image=None, emission=None, strength=1.0, rough=0.5):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = rough
    if image:
        tex = nt.nodes.new("ShaderNodeTexImage")
        tex.image = bpy.data.images.load(image)
        nt.links.new(tex.outputs[0], bsdf.inputs["Base Color"])
    if color:
        bsdf.inputs["Base Color"].default_value = (*color, 1)
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1)
        bsdf.inputs["Emission Strength"].default_value = strength
    return m


# The model, turned to show itself well, filling the middle of the canvas.
bpy.ops.import_scene.gltf(filepath=model_path)
lock = bpy.data.objects.new("lock", None)
sc.collection.objects.link(lock)
for o in [o for o in sc.objects if o.parent is None and o not in (cam, sun, lock)]:
    o.parent = lock
bpy.context.view_layer.update()
pts = [o.matrix_world @ Vector(c) for o in sc.objects if o.type == "MESH" for c in o.bound_box]
mid = sum(pts, Vector()) / len(pts)
for o in lock.children:
    o.location -= mid
lock.scale = Vector([scale] * 3)
lock.rotation_euler = (math.radians(tilt), 0, math.radians(turn))
lock.location = (0, 0, 0)

sc.render.filepath = os.path.join(bpy.app.tempdir, "fg.png")
bpy.ops.render.render(write_still=True)
fg = np.array(bpy.data.images.load(sc.render.filepath).pixels[:], dtype=np.float32).reshape(N, N, 4)

# --- Background: a disc of dark wood with a thin gold rim ---------------------------------
wood = bpy.data.images.load(disc_tex)
ww, wh = wood.size
wpx = np.array(wood.pixels[:], dtype=np.float32).reshape(wh, ww, 4)
idx = (np.arange(N) * min(ww, wh) / N).astype(int)
crop = wpx[idx][:, idx]
yy, xx = np.mgrid[0:N, 0:N]
rr = np.hypot(xx - (N - 1) / 2, yy - (N - 1) / 2) / (N / 2)
bg = crop.copy()
bg[..., :3] *= np.clip(1.0 - 0.45 * rr ** 2, 0, 1)[..., None] * 0.8
rim = np.clip(1 - np.abs(rr - 0.955) * N / 2 / 6, 0, 1)
gold = rim_color
bg[..., :3] = bg[..., :3] * (1 - rim[..., None]) + gold * rim[..., None]
disc_alpha = np.clip((1 - rr) * N / 2 + 0.5, 0, 1)
bg[..., 3] = disc_alpha
side = min(ww, wh)
skypx = wpx
x0 = y0 = 0


def over(top, base):
    a = top[..., 3:4]
    out = base.copy()
    out[..., 3:4] = a + base[..., 3:4] * (1 - a)
    out[..., :3] = (top[..., :3] * a + base[..., :3] * base[..., 3:4] * (1 - a)) / np.maximum(out[..., 3:4], 1e-6)
    return out


def save(px, path):
    h, w = px.shape[:2]
    im = bpy.data.images.new(os.path.basename(path), w, h, alpha=True)
    im.pixels = np.ascontiguousarray(px, dtype=np.float32).ravel()
    im.filepath_raw = path
    im.file_format = "PNG"
    im.save()
    print("wrote", path)


composite = over(fg, bg)
if preview:
    save(composite, preview_path)
    sys.exit(0)


def sdf(alpha):
    """Inside distance / 32 px by repeated erosion, alternating square and cross neighborhoods
    so the ramp is close to round."""
    m = alpha > 0.5
    total = m.astype(np.float32)
    for k in range(31):
        p = np.pad(m, 1)
        nb = [p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]]
        if k % 2:
            nb += [p[:-2, :-2], p[:-2, 2:], p[2:, :-2], p[2:, 2:]]
        for n in nb:
            m = m & n
        total += m
    v = np.clip(total / 32, 0, 1)
    return np.repeat(v[..., None], 4, axis=2)


save(bg, os.path.join(res, "drawable/icon_3d_layer_0.png"))
save(fg, os.path.join(res, "drawable/icon_3d_layer_1.png"))
save(sdf(bg[..., 3]), os.path.join(res, "drawable/icon_3d_sdf_0.png"))
save(sdf(fg[..., 3]), os.path.join(res, "drawable/icon_3d_sdf_1.png"))
save(composite, os.path.join(res, "mipmap-xxxhdpi/ic_spatial_launcher.png"))

# Adaptive-icon foreground (1424, full bleed): the wood as a square, the artwork inside the safe zone.
M = 1424
full = np.zeros((M, M, 4), dtype=np.float32)
idx = (np.arange(M) * side / M).astype(int)
full[...] = skypx[y0 + idx][:, x0 + idx]
full[..., :3] *= 0.85
full[..., 3] = 1
off = (M - N) // 2
full[off:off + N, off:off + N] = over(fg, full[off:off + N, off:off + N])
save(full, os.path.join(res, "drawable/ic_launcher_foreground.png"))
