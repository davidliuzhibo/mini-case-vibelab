"""The four miniature crime scenes, at 1:20, one GLB each.

blender -b --factory-startup --python tools/make_cases.py -- <out_dir> [wutong|inn|train ...] [--preview]

Every prop the player handles is a node named by its id in core/Cases.kt (origin at its hinge or
its middle; render/Layouts.kt says how the hinged and sliding ones move), every person a node
"fig_<id>" standing at its origin. The rest is one static backdrop per scene. Figurines and a few
props are AI models prepared by tools/blender/prep_ai.py into assets_lab/minicase/ai/; until one
exists, a plain stand-in takes its place. With --preview, also renders <out_dir>/case_<id>.png.
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(__file__))
import casekit as k  # noqa: E402
from casekit import B, box, cyl, plane, sphere, text, table, prop, node, attach  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
OUT = args[0]
PREVIEW = "--preview" in args
# Preview with the roofs off (the scene once lifted).
OPEN = "--open" in args
WHICH = [a for a in args[1:] if not a.startswith("--")] or ["wutong", "inn", "train", "opera"]
AI = os.path.join(k.LAB, "minicase", "ai")
TEX = os.path.join(k.LAB, "minicase", "tex")

# Split level, so everything is seen from where the player sits (about 35 degrees down): the
# ground-floor rooms fill the front half (z 0..0.17), the upstairs rooms the back half on a
# platform U high. Walls the eye looks over are kept low.
U = 0.07
FRONT_WALL = 0.07
BACK_WALL = 0.2
LOW = 0.055


def ai_or(name, at, yaw, fallback):
    """The AI model [name] standing at [at], or [fallback]() if it is not prepared yet."""
    path = os.path.join(AI, f"{name}.glb")
    if os.path.exists(path):
        # Not [name] itself: the prop node holding it often has that name.
        return [k.ai(path, f"{name}_mesh", at, yaw)]
    return fallback()


def figure(pid, at, color, yaw=0.0):
    """A figurine node fig_<pid> standing at [at] (AI model, or a painted peg as a stand-in)."""
    x, y, z = at

    def peg():
        m = k.flat(f"peg_{pid}", color, 0.5)
        skin = k.flat("skin", (0.92, 0.78, 0.66), 0.5)
        base = k.flat("base_wood", (0.35, 0.18, 0.1), 0.4)
        return [cyl(f"{pid}_base", 0.016, 0.005, (x, y + 0.0025, z), base),
                cyl(f"{pid}_body", 0.011, 0.058, (x, y + 0.034, z), m, radius2=0.008),
                sphere(f"{pid}_head", 0.009, (x, y + 0.072, z), skin)]
    return prop(f"fig_{pid}", at, ai_or(f"fig_{pid}", at, yaw, peg))


def base(plaque):
    """The display board under the scene, with a brass plaque at the front."""
    board = k.tex("lacquered_cherry_wood", uv=6)
    brass = k.flat("brass", (0.78, 0.6, 0.3), 0.3, 1.0)
    ink = k.flat("ink", (0.12, 0.08, 0.05), 0.5)
    return [box("display_board", (0.6, 0.022, 0.4), (0, -0.011, 0), board, bevel=0.003),
            box("plaque", (0.12, 0.016, 0.002), (0, -0.011, 0.2015), brass, bevel=0.0005),
            text("plaque_text", plaque, 0.008, (0, -0.011, 0.2028), ink)]


def walls_with_door(name, x, z0, z1, y0, h, door_z0, door_z1, door_h, mat, t=0.008):
    """A partition across the depth at [x], from z0 to z1, with a doorway between door_z0 and door_z1."""
    out = []
    if door_z0 > z0:
        out.append(box(f"{name}_a", (t, h, door_z0 - z0), (x, y0 + h / 2, (z0 + door_z0) / 2), mat, bevel=0))
    if z1 > door_z1:
        out.append(box(f"{name}_b", (t, h, z1 - door_z1), (x, y0 + h / 2, (door_z1 + z1) / 2), mat, bevel=0))
    out.append(box(f"{name}_lintel", (t, h - door_h, door_z1 - door_z0), (x, y0 + door_h + (h - door_h) / 2, (door_z0 + door_z1) / 2), mat, bevel=0))
    return out


def window(name, center, size, frame_mat, facing="+z", glass=True):
    """A small window frame with lattice, flat on a wall facing [facing]."""
    x, y, z = center
    w, h = size
    d = 0.003
    out = []
    if facing in ("+z", "-z"):
        out += [box(f"{name}_t", (w, 0.003, d), (x, y + h / 2, z), frame_mat, bevel=0),
                box(f"{name}_b", (w, 0.003, d), (x, y - h / 2, z), frame_mat, bevel=0),
                box(f"{name}_l", (0.003, h, d), (x - w / 2, y, z), frame_mat, bevel=0),
                box(f"{name}_r", (0.003, h, d), (x + w / 2, y, z), frame_mat, bevel=0),
                box(f"{name}_m", (0.0015, h, d), (x, y, z), frame_mat, bevel=0),
                box(f"{name}_c", (w, 0.0015, d), (x, y, z), frame_mat, bevel=0)]
        if glass:
            out.append(plane(f"{name}_sky", (w, h), (x, y, z - 0.001 if facing == "+z" else z + 0.001), k.flat("night_glass", (0.08, 0.12, 0.2), 0.2), facing))
    return out


def bed(name, at, size, wood, quilt, pillow):
    x, y, z = at
    w, h, d = size
    return [box(f"{name}_frame", (w, h, d), (x, y + h / 2, z), wood),
            box(f"{name}_quilt", (w * 0.94, 0.004, d * 0.72), (x, y + h + 0.002, z + d * 0.12), quilt, bevel=0.001),
            box(f"{name}_pillow", (w * 0.6, 0.006, d * 0.14), (x, y + h + 0.003, z - d * 0.38), pillow, bevel=0.0015)]


def book(name, center, size, cover, page):
    x, y, z = center
    w, h, d = size
    return [box(f"{name}_cover", (w, h, d), (x, y, z), cover, bevel=0.0003),
            box(f"{name}_pages", (w * 0.96, h * 0.7, d * 0.95), (x + w * 0.03, y, z), page, bevel=0)]


def finish(cid, static, props):
    scene = node(f"scene_{cid}", (0, 0, 0))
    attach(scene, static)
    attach(scene, props)
    path = os.path.join(OUT, f"case_{cid}.glb")
    if PREVIEW:
        roofs = [o for o in bpy.data.objects if o.name == "roof"]
        if OPEN:
            for r in roofs:
                for d in k.descendants(r):
                    d.hide_render = True
        preview(os.path.join(OUT, f"case_{cid}{'_open' if OPEN else ''}.png"))
        for r in roofs:
            for d in k.descendants(r):
                d.hide_render = False
    k.export(path, [scene])


def preview(path):
    sc = bpy.context.scene
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    sc.collection.objects.link(cam)
    # About where the player's eyes are: 60 cm in front, 45 cm above the table.
    cam.location = B((0.0, 0.45, 0.62))
    cam.rotation_euler = (math.radians(55), 0, 0)
    cam.data.lens = 26
    sc.camera = cam
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3.5
    sun.rotation_euler = (math.radians(40), math.radians(-20), math.radians(25))
    sc.collection.objects.link(sun)
    w = bpy.data.worlds.new("w")
    sc.world = w
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs["Color"].default_value = (0.35, 0.35, 0.38, 1)
    sc.render.engine = "BLENDER_EEVEE_NEXT"
    sc.render.resolution_x, sc.render.resolution_y = 960, 640
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    bpy.data.objects.remove(cam)
    bpy.data.objects.remove(sun)


def shell(plaster, side, floor_l, floor_r, floor_up, xp):
    """Floors, the raised back platform, back and side walls, low partitions at x = [xp]."""
    return [box("floor_l", (0.275 + xp, 0.002, 0.17), ((-0.275 + xp) / 2, 0.001, 0.085), floor_l, bevel=0),
            box("floor_r", (0.275 - xp, 0.002, 0.17), ((0.275 + xp) / 2, 0.001, 0.085), floor_r, bevel=0),
            box("platform", (0.55, U, 0.17), (0, U / 2, -0.085), plaster, bevel=0),
            box("floor_up", (0.55, 0.0015, 0.17), (0, U + 0.0008, -0.085), floor_up, bevel=0),
            box("wall_back", (0.56, BACK_WALL, 0.01), (0, BACK_WALL / 2, -0.175), plaster, bevel=0),
            box("wall_l_back", (0.01, BACK_WALL, 0.18), (-0.28, BACK_WALL / 2, -0.085), side, bevel=0),
            box("wall_r_back", (0.01, BACK_WALL, 0.18), (0.28, BACK_WALL / 2, -0.085), side, bevel=0),
            box("wall_l_front", (0.01, FRONT_WALL, 0.17), (-0.28, FRONT_WALL / 2, 0.085), side, bevel=0),
            box("wall_r_front", (0.01, FRONT_WALL, 0.17), (0.28, FRONT_WALL / 2, 0.085), side, bevel=0),
            box("part_front", (0.006, LOW, 0.11), (xp, LOW / 2, 0.115), plaster, bevel=0),
            box("part_back", (0.006, LOW, 0.12), (xp, U + LOW / 2, -0.11), plaster, bevel=0)]


def stairs(name, x, w, z_from, mat, n=5):
    """Steps from the ground at z_from up to the platform edge (z = 0)."""
    out = []
    run = z_from / n
    for i in range(n):
        top = (i + 1) * U / n
        out.append(box(f"{name}{i}", (w, top, run), (x, top / 2, z_from - (i + 0.5) * run), mat, bevel=0))
    return out


# ---- 1. 梧桐里：a lane house cut open; upstairs at the back -----------------------------------------

def wutong():
    k.reset()
    plaster = k.tex("plastered_wall_02", uv=8)
    brick = k.tex("brick_wall_02", uv=16)
    floor = k.tex("old_wooden_floor_02", uv=12)
    kfloor = k.tex("brick_floor_02", uv=16)
    roof = k.tex("grey_roof_tiles", uv=14)
    wood = k.tex("dark_wood", uv=24)
    lwood = k.tex("fine_grained_wood", uv=24)
    paper = k.flat("paper", (0.92, 0.88, 0.78), 0.8)
    cloth_red = k.flat("quilt_red", (0.55, 0.16, 0.14), 0.8)
    cloth_blue = k.flat("quilt_blue", (0.25, 0.33, 0.5), 0.8)
    white = k.flat("pillow", (0.9, 0.9, 0.86), 0.8)
    iron = k.flat("iron", (0.12, 0.12, 0.13), 0.5, 0.6)
    brass = k.flat("brass", (0.78, 0.6, 0.3), 0.3, 1.0)
    s = base("梧桐里七号")
    s += shell(plaster, brick, floor, kfloor, floor, 0.03)
    s += [box("roof_back", (0.6, 0.008, 0.08), (0, BACK_WALL + 0.012, -0.16), roof, bevel=0, pitch=-25)]
    s += window("win_qianlou", (-0.13, U + 0.085, -0.169), (0.06, 0.05), wood)
    s += window("win_tingzi", (0.16, U + 0.085, -0.169), (0.05, 0.045), wood)
    s += stairs("stair", 0.0, 0.04, 0.07, lwood)

    p = []
    # 客堂 (front left): the desk against the platform, its drawer, the rent book; a square table,
    # the phonograph, a poster and a clock on the platform's face.
    dx, dz = -0.19, 0.03
    s += [box("desk_top", (0.1, 0.004, 0.05), (dx, 0.04, dz), wood),
          box("desk_ped_l", (0.022, 0.038, 0.048), (dx - 0.039, 0.019, dz), wood),
          box("desk_ped_r", (0.022, 0.038, 0.048), (dx + 0.039, 0.019, dz), wood),
          box("desk_back", (0.056, 0.02, 0.003), (dx, 0.028, dz - 0.0225), wood, bevel=0)]
    dy = 0.032
    drawer = [box("drawer_front", (0.054, 0.011, 0.003), (dx, dy, dz + 0.0235), wood),
              box("drawer_floor", (0.05, 0.002, 0.044), (dx, dy - 0.005, dz), lwood, bevel=0),
              box("drawer_l", (0.002, 0.009, 0.044), (dx - 0.025, dy - 0.001, dz), lwood, bevel=0),
              box("drawer_r", (0.002, 0.009, 0.044), (dx + 0.025, dy - 0.001, dz), lwood, bevel=0),
              box("drawer_b", (0.05, 0.009, 0.002), (dx, dy - 0.001, dz - 0.021), lwood, bevel=0),
              sphere("drawer_knob", 0.0022, (dx, dy, dz + 0.026), brass)]
    dnode = prop("drawer", (dx, dy, dz), drawer)
    leather = k.flat("box_leather", (0.4, 0.08, 0.07), 0.5)
    wb = (dx - 0.006, dy - 0.0005, dz + 0.004)
    watchbox = [box("watchbox_body", (0.02, 0.008, 0.016), wb, leather, bevel=0.001),
                cyl("watchbox_mark", 0.0035, 0.0006, (wb[0] + 0.003, wb[1] - 0.0043, wb[2] - 0.002), k.flat("cobalt", (0.05, 0.22, 0.75), 0.3), verts=16)]
    attach(dnode, [prop("watchbox", wb, watchbox)])
    p.append(dnode)
    rb = (dx + 0.02, 0.0445, dz + 0.004)
    p.append(prop("rentbook", rb, book("rentbook", rb, (0.024, 0.005, 0.032), k.flat("book_blue", (0.14, 0.2, 0.36), 0.7), paper)
                  + [text("rentbook_title", "房租", 0.006, (rb[0], rb[1] + 0.0027, rb[2]), paper, facing="+y")]))
    s += table("table8", (-0.12, 0, 0.115), (0.05, 0.04, 0.05), wood)
    for i, cx in enumerate((-0.155, -0.085)):
        s += table(f"chair{i}", (cx, 0, 0.115), (0.02, 0.022, 0.02), wood, leg=0.003)
        s.append(box(f"chair{i}_back", (0.003, 0.026, 0.02), (cx + (-0.01 if i == 0 else 0.01), 0.035, 0.115), wood))
    s += [box("side_cab", (0.032, 0.035, 0.045), (-0.255, 0.0175, 0.11), wood)]
    s += ai_or("phonograph", (-0.255, 0.035, 0.11), 60, lambda: [box("phono", (0.025, 0.02, 0.03), (-0.255, 0.045, 0.11), lwood)])
    s.append(plane("poster", (0.035, 0.048), (-0.09, 0.038, 0.0012), k.picture(os.path.join(TEX, "calendar.jpeg"), "calendar")))
    s.append(cyl("clock", 0.008, 0.003, (-0.05, 0.05, 0.0015), k.flat("clockface", (0.95, 0.93, 0.85), 0.4), axis="z"))
    p.append(figure("zhou", (-0.05, 0.0, 0.09), (0.3, 0.3, 0.32)))

    # 灶披间 (front right): the stove with the herb packet, the water vat, 阿福's cot and his shoes.
    stove_b = k.tex("brick_floor_02", uv=30, tint=(0.95, 0.9, 0.85))
    s += [box("stove", (0.1, 0.044, 0.055), (0.17, 0.022, 0.03), stove_b, bevel=0.001),
          cyl("pot1", 0.012, 0.004, (0.15, 0.045, 0.03), iron), cyl("pot2", 0.01, 0.004, (0.19, 0.045, 0.03), iron),
          box("chimney", (0.018, 0.1, 0.018), (0.212, 0.09, 0.012), stove_b, bevel=0),
          cyl("vat", 0.016, 0.032, (0.25, 0.016, 0.03), k.flat("glaze", (0.25, 0.17, 0.1), 0.25), radius2=0.018),
          box("cot", (0.04, 0.014, 0.09), (0.25, 0.007, 0.11), lwood),
          box("cot_mat", (0.036, 0.003, 0.085), (0.25, 0.0155, 0.11), k.flat("straw", (0.75, 0.62, 0.38), 0.9), bevel=0)]
    hb = (0.178, 0.0484, 0.04)
    p.append(prop("herbs", hb, [box("herbs_paper", (0.016, 0.008, 0.02), hb, k.flat("kraft", (0.7, 0.55, 0.35), 0.9), bevel=0.0015),
                                box("herbs_string", (0.017, 0.0085, 0.0015), hb, k.flat("string", (0.85, 0.2, 0.15), 0.8), bevel=0),
                                plane("herbs_label", (0.008, 0.012), (hb[0], hb[1] + 0.0043, hb[2]), paper, facing="+y")]))
    sh = (0.08, 0.0045, 0.14)
    cloth_black = k.flat("shoe_cloth", (0.08, 0.08, 0.09), 0.85)
    mud = k.flat("mud", (0.36, 0.25, 0.13), 1.0)
    shoes = []
    for i, ox in enumerate((-0.008, 0.008)):
        shoes += [box(f"shoe{i}_upper", (0.011, 0.006, 0.026), (sh[0] + ox, sh[1] + 0.0005, sh[2]), cloth_black, bevel=0.002),
                  box(f"shoe{i}_sole", (0.0115, 0.0015, 0.0265), (sh[0] + ox, sh[1] - 0.0032, sh[2]), mud, bevel=0.0004)]
    p.append(prop("shoes", sh, shoes))
    p.append(figure("afu", (0.15, 0.0, 0.11), (0.45, 0.45, 0.45)))

    # 前楼 (back left, upstairs, 陈先生): bed, the easel with the blue painting, the palette knife,
    # the coat on its stand.
    s += bed("bed_chen", (-0.225, U, -0.115), (0.07, 0.016, 0.1), wood, cloth_blue, white)
    ex, ez = -0.13, -0.04
    s += [box("easel_l", (0.003, 0.07, 0.003), (ex - 0.015, U + 0.035, ez), lwood, bevel=0),
          box("easel_r", (0.003, 0.07, 0.003), (ex + 0.015, U + 0.035, ez), lwood, bevel=0),
          box("easel_back", (0.003, 0.07, 0.003), (ex, U + 0.033, ez - 0.02), lwood, bevel=0),
          box("easel_ledge", (0.04, 0.003, 0.006), (ex, U + 0.03, ez + 0.003), lwood, bevel=0)]
    ec = (ex, U + 0.052, ez + 0.002)
    p.append(prop("easel", ec, [box("canvas", (0.042, 0.032, 0.002), ec, k.flat("canvas", (0.9, 0.88, 0.82), 0.9), bevel=0),
                                plane("canvas_paint", (0.04, 0.03), (ec[0], ec[1], ec[2] + 0.0011), k.picture(os.path.join(TEX, "painting.jpeg"), "painting"))]))
    s += table("ptable", (-0.11, U, -0.14), (0.05, 0.03, 0.035), wood)
    s += [cyl("palette", 0.012, 0.001, (-0.095, U + 0.0305, -0.14), lwood, verts=20),
          cyl("paint_blue", 0.002, 0.0012, (-0.09, U + 0.0315, -0.137), k.flat("cobalt", (0.05, 0.22, 0.75), 0.3), verts=10),
          cyl("paint_white", 0.002, 0.0012, (-0.1, U + 0.0315, -0.142), white, verts=10)]
    kn = (-0.122, U + 0.0312, -0.138)
    steel = k.flat("steel", (0.75, 0.76, 0.78), 0.25, 1.0)
    p.append(prop("knife", kn, [box("knife_handle", (0.012, 0.0022, 0.003), (kn[0] - 0.008, kn[1], kn[2]), lwood, bevel=0.0005),
                                box("knife_blade", (0.014, 0.0006, 0.004), (kn[0] + 0.005, kn[1], kn[2]), steel, bevel=0),
                                box("knife_chip", (0.002, 0.0008, 0.0015), (kn[0] + 0.0115, kn[1] + 0.0004, kn[2] + 0.0005), k.flat("woodchip", (0.25, 0.12, 0.06), 0.8), bevel=0)]))
    s += [cyl("coatstand", 0.0015, 0.09, (-0.03, U + 0.045, -0.14), wood), cyl("coatstand_foot", 0.008, 0.002, (-0.03, U + 0.001, -0.14), wood)]
    cc = (-0.03, U + 0.07, -0.133)
    tweed = k.flat("tweed", (0.38, 0.27, 0.18), 0.9)
    p.append(prop("coat", cc, [box("coat_body", (0.03, 0.04, 0.008), cc, tweed, bevel=0.002),
                               box("coat_sleeve_l", (0.006, 0.035, 0.006), (cc[0] - 0.017, cc[1] - 0.002, cc[2]), tweed, bevel=0.002),
                               box("coat_sleeve_r", (0.006, 0.035, 0.006), (cc[0] + 0.017, cc[1] - 0.002, cc[2]), tweed, bevel=0.002),
                               box("coat_pocket", (0.01, 0.007, 0.001), (cc[0] + 0.006, cc[1] - 0.01, cc[2] - 0.0045), k.flat("tweed_dark", (0.28, 0.2, 0.13), 0.9), bevel=0),
                               box("coat_ticket", (0.007, 0.006, 0.0005), (cc[0] + 0.006, cc[1] - 0.005, cc[2] - 0.0052), paper, bevel=0)]))
    p.append(figure("chen", (-0.07, U, -0.05), (0.35, 0.25, 0.15)))

    # 亭子间 (back right, upstairs, 林太太): the sewing machine with the order slip, the oil lamp on a
    # small table, a dress form, a bed.
    sw = (0.2, U, -0.135)
    p.append(prop("sewing", (sw[0], U + 0.02, sw[2]), ai_or("sewing", sw, 0, lambda: [box("sewing_box", (0.05, 0.035, 0.025), (sw[0], U + 0.0175, sw[2]), wood)])
                  + [plane("order_slip", (0.012, 0.016), (sw[0] - 0.015, U + 0.0405, sw[2] + 0.005), paper, facing="+y")]))
    s += table("lamp_table", (0.09, U, -0.145), (0.03, 0.03, 0.025), wood)
    p.append(prop("lamp", (0.09, U + 0.042, -0.145), ai_or("kerolamp", (0.09, U + 0.03, -0.145), 0, lambda: [cyl("lamp_glass", 0.005, 0.02, (0.09, U + 0.042, -0.145), k.flat("lampglass", (0.9, 0.9, 0.8), 0.1))])))
    s += [cyl("dressform", 0.008, 0.04, (0.255, U + 0.05, -0.06), cloth_blue, radius2=0.006),
          cyl("dressform_pole", 0.0012, 0.03, (0.255, U + 0.015, -0.06), wood)]
    s += bed("bed_lin", (0.2, U, -0.04), (0.07, 0.016, 0.06), wood, cloth_red, white)
    p.append(figure("lin", (0.1, U, -0.06), (0.55, 0.7, 0.85)))
    finish("wutong", s, p)


# ---- 2. 听雨客栈：hall and kitchen in front; the guest rooms upstairs at the back under a lid ---------

def inn():
    k.reset()
    plaster = k.tex("plastered_wall_04", uv=8)
    beam = k.tex("dark_wooden_planks", uv=20)
    floor = k.tex("brick_floor_02", uv=16)
    floor2 = k.tex("old_planks_02", uv=14)
    roof = k.tex("grey_roof_tiles_02", uv=14)
    wood = k.tex("dark_wood", uv=24)
    lwood = k.tex("fine_grained_wood", uv=24)
    camphor = k.tex("walnut_veneer", uv=30, tint=(1.1, 0.9, 0.7))
    paper = k.flat("paper", (0.92, 0.88, 0.78), 0.8)
    red = k.flat("lacquer_red", (0.5, 0.08, 0.06), 0.4)
    quilt = k.flat("quilt_green", (0.2, 0.38, 0.3), 0.8)
    white = k.flat("pillow", (0.9, 0.9, 0.86), 0.8)
    brass = k.flat("brass", (0.78, 0.6, 0.3), 0.3, 1.0)
    ink = k.flat("ink", (0.08, 0.06, 0.05), 0.5)
    straw = k.flat("straw", (0.75, 0.62, 0.38), 0.9)
    s = base("听雨客栈")
    s += shell(plaster, plaster, floor, floor, floor2, 0.07)
    s += [box("post_l", (0.01, FRONT_WALL + 0.01, 0.01), (-0.275, (FRONT_WALL + 0.01) / 2, 0.165), beam),
          box("post_r", (0.01, FRONT_WALL + 0.01, 0.01), (0.275, (FRONT_WALL + 0.01) / 2, 0.165), beam)]

    # The lid over the guest rooms: roof slopes, gables and the front wall with lattice windows,
    # all lifted off as one.
    top = U + 0.12
    rf = [box("lid_front", (0.55, 0.12, 0.006), (0, U + 0.06, 0.002), plaster, bevel=0),
          box("roof_f", (0.6, 0.007, 0.11), (0, top + 0.022, -0.035), roof, bevel=0, pitch=22),
          box("roof_b", (0.6, 0.007, 0.11), (0, top + 0.022, -0.135), roof, bevel=0, pitch=-22),
          box("roof_ridge", (0.6, 0.012, 0.012), (0, top + 0.045, -0.085), k.flat("ridge", (0.3, 0.3, 0.32), 0.7), bevel=0),
          box("gable_l", (0.006, 0.04, 0.17), (-0.278, top + 0.015, -0.085), plaster, bevel=0),
          box("gable_r", (0.006, 0.04, 0.17), (0.278, top + 0.015, -0.085), plaster, bevel=0)]
    for i, wx in enumerate((-0.2, -0.08, 0.07, 0.19)):
        rf += window(f"lidwin{i}", (wx, U + 0.065, 0.0055), (0.05, 0.05), wood, facing="+z", glass=False)
    rf.append(box("lid_sign", (0.07, 0.016, 0.003), (0, U + 0.105, 0.0065), k.flat("sign_black", (0.08, 0.06, 0.05), 0.4), bevel=0))
    rf.append(text("lid_sign_t", "听雨客栈", 0.009, (0, U + 0.105, 0.0085), k.flat("gold", (0.85, 0.65, 0.25), 0.3, 1.0)))
    p = [prop("roof", (0, top, -0.085), rf)]

    # 大堂 (front left): the counter against the platform (drawer, wine book, abacus), the key board
    # beside it, wine jars, the storyteller's stage and its board, two tables.
    cx, cz = -0.2, 0.025
    s += [box("counter_top", (0.11, 0.004, 0.036), (cx, 0.048, cz), wood),
          box("counter_l", (0.03, 0.046, 0.034), (cx - 0.04, 0.023, cz), wood),
          box("counter_r", (0.03, 0.046, 0.034), (cx + 0.04, 0.023, cz), wood),
          box("counter_mid_back", (0.05, 0.046, 0.003), (cx, 0.023, cz - 0.0155), wood, bevel=0),
          box("counter_mid_low", (0.05, 0.028, 0.034), (cx, 0.014, cz), wood)]
    dy = 0.037
    cd = [box("cdrawer_front", (0.048, 0.016, 0.003), (cx, dy, cz + 0.0165), red),
          box("cdrawer_floor", (0.044, 0.002, 0.03), (cx, dy - 0.007, cz), lwood, bevel=0),
          box("cdrawer_l", (0.002, 0.012, 0.03), (cx - 0.022, dy - 0.002, cz), lwood, bevel=0),
          box("cdrawer_r", (0.002, 0.012, 0.03), (cx + 0.022, dy - 0.002, cz), lwood, bevel=0),
          sphere("cdrawer_knob", 0.0022, (cx, dy, cz + 0.019), brass)]
    dn = prop("counterdrawer", (cx, dy, cz), cd)
    ln = (cx + 0.005, dy - 0.0055, cz + 0.002)
    attach(dn, [prop("loan", ln, [box("loan_paper", (0.018, 0.0008, 0.022), ln, paper, bevel=0),
                                  text("loan_text", "借据", 0.005, (ln[0], ln[1] + 0.0006, ln[2]), ink, facing="+y")])])
    p.append(dn)
    wl = (cx - 0.03, 0.0512, cz)
    p.append(prop("winelog", wl, book("winelog", wl, (0.022, 0.0045, 0.028), k.flat("book_indigo", (0.12, 0.14, 0.3), 0.7), paper)
                  + [text("winelog_t", "酒簿", 0.0055, (wl[0], wl[1] + 0.0025, wl[2]), paper, facing="+y")]))
    s += [box("abacus", (0.03, 0.003, 0.014), (cx + 0.03, 0.0515, cz), wood)]
    for i in range(7):
        s.append(cyl(f"abacus_rod{i}", 0.0018, 0.012, (cx + 0.018 + i * 0.004, 0.0535, cz), k.flat("bead", (0.2, 0.1, 0.06), 0.5), axis="z", verts=8))
    kb = (-0.115, 0.045, 0.0035)
    keys = [box("kboard", (0.05, 0.034, 0.003), kb, lwood, bevel=0.0005)]
    for i, lab in enumerate(["天一", "天二", "天三"]):
        hx = kb[0] - 0.016 + i * 0.016
        keys += [cyl(f"khook{i}", 0.0006, 0.004, (hx, kb[1] + 0.006, kb[2] + 0.003), brass, axis="z", verts=8),
                 text(f"klabel{i}", lab, 0.0035, (hx, kb[1] + 0.012, kb[2] + 0.0018), ink)]
    # Room 2's key hangs on room 3's hook: room 3 has two keys, room 2 none.
    for i, hx in enumerate((kb[0] - 0.016, kb[0] + 0.016, kb[0] + 0.019)):
        keys += [box(f"key{i}", (0.0018, 0.009, 0.0008), (hx, kb[1] - 0.0005, kb[2] + 0.0035), brass, bevel=0),
                 cyl(f"keytag{i}", 0.0025, 0.0008, (hx, kb[1] - 0.007, kb[2] + 0.0035), red, axis="z", verts=12)]
    p.append(prop("keyboard", kb, keys))
    for i, (jx, jz) in enumerate([(-0.258, 0.08), (-0.258, 0.11), (-0.243, 0.095)]):
        s.append(cyl(f"jar{i}", 0.009, 0.022, (jx, 0.011, jz), k.flat("jar", (0.3, 0.2, 0.12), 0.3), radius2=0.006))
    s += [box("stage", (0.07, 0.016, 0.045), (-0.02, 0.008, 0.03), wood),
          box("stage_table", (0.025, 0.018, 0.015), (-0.02, 0.025, 0.02), red)]
    bd = (0.028, 0.04, 0.035)
    p.append(prop("board", bd, [box("board_plank", (0.016, 0.042, 0.002), bd, k.flat("board_white", (0.92, 0.9, 0.84), 0.6), bevel=0),
                                text("board_text", "三侠五义", 0.0055, (bd[0], bd[1], bd[2] + 0.0012), ink, vertical=True),
                                box("board_foot", (0.012, 0.002, 0.008), (bd[0], 0.0185, bd[2]), wood, bevel=0)]))
    s += table("htable1", (-0.13, 0, 0.12), (0.045, 0.035, 0.045), wood)
    s += table("htable2", (-0.04, 0, 0.13), (0.04, 0.035, 0.04), wood)
    p += [figure("qian", (-0.255, 0, 0.03), (0.15, 0.2, 0.35)),
          figure("wu", (-0.03, 0.016, 0.04), (0.5, 0.5, 0.5)),
          figure("xiaoliu", (0.035, 0, 0.11), (0.85, 0.85, 0.8)),
          figure("zhao", (-0.185, 0, 0.12), (0.4, 0.15, 0.25))]

    # 灶房 (front right): the stove against the platform (its door swings open; the tin box in the
    # ashes), the back stairs up to the guest rooms with the sandal, 小六's bedding with the pillow.
    stove_b = k.tex("brick_floor_02", uv=30, tint=(0.95, 0.9, 0.85))
    sx, sz = 0.15, 0.03
    s += [box("stove_l", (0.02, 0.05, 0.055), (sx - 0.035, 0.025, sz), stove_b, bevel=0.001),
          box("stove_r", (0.02, 0.05, 0.055), (sx + 0.035, 0.025, sz), stove_b, bevel=0.001),
          box("stove_top", (0.09, 0.012, 0.055), (sx, 0.044, sz), stove_b, bevel=0.001),
          box("stove_back", (0.05, 0.038, 0.01), (sx, 0.019, sz - 0.0225), stove_b, bevel=0),
          box("stove_bottom", (0.05, 0.004, 0.045), (sx, 0.002, sz), stove_b, bevel=0),
          plane("ash", (0.048, 0.04), (sx, 0.0045, sz), k.flat("ash", (0.35, 0.33, 0.31), 1.0), facing="+y"),
          cyl("wok", 0.018, 0.006, (sx, 0.051, sz), k.flat("iron", (0.12, 0.12, 0.13), 0.5, 0.6)),
          box("chimney", (0.018, 0.08, 0.018), (sx + 0.03, 0.09, sz - 0.015), stove_b, bevel=0)]
    hx, hz = sx - 0.025, sz + 0.0285
    door = [box("stove_door", (0.05, 0.036, 0.003), (hx + 0.025, 0.02, hz), k.flat("iron_door", (0.18, 0.17, 0.16), 0.5, 0.7), bevel=0.0005),
            sphere("stove_door_knob", 0.002, (hx + 0.045, 0.02, hz + 0.003), brass)]
    p.append(prop("stove", (hx, 0.02, hz), door))
    tb = (sx + 0.004, 0.0105, sz + 0.004)
    p.append(prop("tinbox", tb, [box("tin", (0.018, 0.01, 0.013), tb, k.flat("tin", (0.55, 0.55, 0.52), 0.35, 0.8), bevel=0.0006),
                                 plane("tin_ash", (0.016, 0.011), (tb[0], tb[1] + 0.0052, tb[2]), k.flat("ash", (0.35, 0.33, 0.31), 1.0), facing="+y")]))
    s += stairs("bstair", 0.245, 0.04, 0.11, lwood)
    mud = k.flat("mud_yellow", (0.55, 0.4, 0.18), 1.0)
    sd = (0.245, 3 * U / 5 + 0.003, 0.11 - 2.5 * 0.022)
    p.append(prop("sandal", sd, [box("sandal_sole", (0.01, 0.0025, 0.022), sd, straw, bevel=0.0008),
                                 box("sandal_mud", (0.0095, 0.001, 0.0215), (sd[0], sd[1] - 0.0017, sd[2]), mud, bevel=0),
                                 box("sandal_strap", (0.011, 0.002, 0.003), (sd[0], sd[1] + 0.002, sd[2] - 0.005), straw, bevel=0)]))
    for i in range(2):
        s.append(box(f"mudprint{i}", (0.008, 0.0005, 0.012), (0.245, (i + 4) * U / 5 + 0.0003, 0.11 - (i + 3.5) * 0.022), mud, bevel=0))
    s += [box("bedding", (0.05, 0.006, 0.07), (0.12, 0.003, 0.125), straw, bevel=0.001),
          box("bedding_quilt", (0.045, 0.004, 0.045), (0.12, 0.008, 0.135), k.flat("quilt_grey", (0.4, 0.38, 0.35), 0.9), bevel=0.001)]
    pl = (0.12, 0.012, 0.1)
    p.append(prop("pillow", pl, [box("pillow_body", (0.022, 0.006, 0.01), pl, k.flat("pillow_blue", (0.3, 0.4, 0.55), 0.8), bevel=0.002),
                                 box("iou", (0.012, 0.0005, 0.009), (pl[0] + 0.002, pl[1] - 0.0033, pl[2]), paper, bevel=0)]))

    # Upstairs at the back, under the lid: 天字二号 (赵老板: bed and the camphor trunk) and
    # 天字三号 (郑小姐: desk with the letter, bed).
    s += bed("bed_zhao", (-0.225, U, -0.11), (0.07, 0.016, 0.1), wood, quilt, white)
    tx, tz = -0.12, -0.13
    s += [box("trunk_body", (0.05, 0.026, 0.03), (tx, U + 0.013, tz), camphor, bevel=0.0008),
          box("trunk_inside", (0.046, 0.001, 0.026), (tx, U + 0.0262, tz), k.flat("trunk_lining", (0.45, 0.1, 0.08), 0.8), bevel=0)]
    lid = [box("trunk_lid", (0.051, 0.006, 0.031), (tx, U + 0.029, tz), camphor, bevel=0.0008),
           box("trunk_lock", (0.006, 0.006, 0.0015), (tx, U + 0.025, tz + 0.0157), brass, bevel=0)]
    p.append(prop("trunk", (tx, U + 0.026, tz - 0.015), lid))
    s += table("desk3", (0.06, U, -0.14), (0.05, 0.03, 0.03), wood)
    lt = (0.06, U + 0.0305, -0.138)
    p.append(prop("letter", lt, [box("letter_paper", (0.014, 0.0006, 0.018), lt, paper, bevel=0),
                                 text("letter_t", "柳下", 0.0045, (lt[0], lt[1] + 0.0005, lt[2]), ink, facing="+y")]))
    s += bed("bed_zheng", (0.21, U, -0.11), (0.07, 0.016, 0.1), wood, k.flat("quilt_rose", (0.7, 0.4, 0.45), 0.8), white)
    p.append(figure("zheng", (0.12, U, -0.06), (0.55, 0.7, 0.85)))
    finish("inn", s, p)


# ---- 3. 夜车：a sleeping car on its rails; the roof and the upper car side lift off as one ----------

def train():
    k.reset()
    panel = k.tex("dark_paneled_wood", uv=14)
    lwood = k.tex("lacquered_cherry_wood", uv=18)
    seat = k.tex("quatrefoil_jacquard_fabric", uv=40, tint=(0.75, 0.35, 0.3))
    carpet = k.tex("dirty_carpet", uv=20, tint=(0.6, 0.25, 0.2))
    green = k.flat("car_green", (0.12, 0.25, 0.18), 0.45, 0.3)
    roofm = k.tex("metal_plate", uv=14, tint=(0.35, 0.37, 0.38))
    gravel = k.tex("gravel_floor_02", uv=14)
    steel = k.flat("rail", (0.45, 0.45, 0.47), 0.3, 1.0)
    black = k.flat("underframe", (0.08, 0.08, 0.08), 0.6, 0.5)
    brass = k.flat("brass", (0.78, 0.6, 0.3), 0.3, 1.0)
    paper = k.flat("paper", (0.92, 0.88, 0.78), 0.8)
    ink = k.flat("ink", (0.08, 0.06, 0.05), 0.5)
    white = k.flat("pillow", (0.9, 0.9, 0.86), 0.8)
    s = base("沪宁夜车")

    s += [box("ballast", (0.58, 0.006, 0.16), (0, 0.003, 0.0), gravel, bevel=0)]
    for i in range(12):
        s.append(box(f"sleeper{i}", (0.012, 0.004, 0.14), (-0.27 + i * 0.049, 0.007, 0.0), k.flat("sleeper", (0.25, 0.18, 0.12), 0.9), bevel=0))
    for z in (-0.04, 0.04):
        s.append(box(f"rail{z}", (0.58, 0.004, 0.003), (0, 0.011, z), steel, bevel=0))
    s.append(box("underframe", (0.52, 0.012, 0.12), (0, 0.025, 0.0), black, bevel=0))
    for bx in (-0.19, 0.19):
        for wx in (-0.02, 0.02):
            for z in (-0.045, 0.045):
                s.append(cyl(f"wheel{bx}{wx}{z}", 0.011, 0.004, (bx + wx, 0.022, z), black, axis="z"))
    fy = 0.032
    W, D, H = 0.54, 0.17, 0.11
    sill = 0.028
    s += [box("car_floor", (W, 0.004, D), (0, fy - 0.002, 0), carpet, bevel=0),
          box("car_end_l", (0.008, H, D), (-W / 2, fy + H / 2, 0), green, bevel=0),
          box("car_end_r", (0.008, H, D), (W / 2, fy + H / 2, 0), green, bevel=0),
          box("car_back", (W, H, 0.006), (0, fy + H / 2, -D / 2), green, bevel=0),
          box("car_back_in", (W - 0.01, H - 0.01, 0.002), (0, fy + H / 2, -D / 2 + 0.004), panel, bevel=0),
          box("car_front_low", (W, sill, 0.006), (0, fy + sill / 2, D / 2), green, bevel=0),
          text("car_number", "头等卧车", 0.008, (0.0, fy + sill / 2, D / 2 + 0.0032), brass)]
    # Inside: a corridor along the back, four compartments and the attendant's corner in front.
    cz0, cz1 = -0.04, D / 2 - 0.004
    walls_x = [-0.155, -0.045, 0.065, 0.175]
    for i, x in enumerate(walls_x):
        s.append(box(f"cwall{i}", (0.004, H * 0.8, cz1 - cz0), (x, fy + H * 0.4, (cz0 + cz1) / 2), panel, bevel=0))
    for i, (x0, x1) in enumerate(zip([-W / 2] + walls_x, walls_x + [W / 2])):
        mid = (x0 + x1) / 2
        # A low partition to the corridor, so the eye sees over it into the corridor.
        s.append(box(f"cpart{i}", ((x1 - x0) * 0.55, 0.035, 0.003), (x0 + (x1 - x0) * 0.275, fy + 0.0175, cz0), panel, bevel=0))
        if i < 4:
            s.append(text(f"cnum{i}", f"{i + 1}号", 0.006, (x0 + (x1 - x0) * 0.275, fy + 0.0175, cz0 + 0.002), brass))
            for side, bx in (("l", x0 + 0.017), ("r", x1 - 0.017)):
                s.append(box(f"berth{i}{side}", (0.03, 0.022, cz1 - cz0 - 0.004), (bx, fy + 0.011, (cz0 + cz1) / 2), seat, bevel=0.001))
                s.append(box(f"pil{i}{side}", (0.022, 0.005, 0.01), (bx, fy + 0.0245, cz0 + 0.014), white, bevel=0.0015))
            s.append(box(f"ctable{i}", (0.04, 0.003, 0.016), (mid, fy + 0.03, cz1 - 0.009), lwood))
            s.append(box(f"ctable_leg{i}", (0.003, 0.03, 0.003), (mid, fy + 0.015, cz1 - 0.009), lwood, bevel=0))
    # The lid: roof and the car side above the sills (windows), lifted off as one.
    rf = [box("lid_front_top", (W, 0.025, 0.006), (0, fy + H - 0.0125, D / 2), green, bevel=0)]
    for i in range(11):
        x = -W / 2 + i * W / 10
        rf.append(box(f"pier{i}", (0.012, H - sill - 0.025, 0.006), (x, fy + sill + (H - sill - 0.025) / 2, D / 2), green, bevel=0))
    rt = fy + H
    rf += [box("roof_top", (W + 0.012, 0.006, 0.11), (0, rt + 0.016, 0), roofm, bevel=0),
           box("roof_front", (W + 0.012, 0.006, 0.05), (0, rt + 0.006, 0.072), roofm, bevel=0, pitch=24),
           box("roof_back", (W + 0.012, 0.006, 0.05), (0, rt + 0.006, -0.072), roofm, bevel=0, pitch=-24)]
    rf += [cyl(f"vent{i}", 0.006, 0.006, (-0.2 + i * 0.1, fy + H + 0.022, 0), roofm) for i in range(5)]
    p = [prop("roof", (0, fy + H + 0.02, 0), rf)]

    ty = fy + 0.0315
    # 1号 (沈老板): the jewel box with the glass pendant (its maker's mark underneath), the tea cup.
    x1 = (-W / 2 + walls_x[0]) / 2
    jb = (x1 - 0.008, ty + 0.006, cz1 - 0.01)
    p.append(prop("jewelbox", jb, ai_or("jade", (jb[0], ty, jb[2]), 0, lambda: [box("jbox", (0.016, 0.01, 0.014), jb, k.flat("velvet", (0.6, 0.05, 0.08), 0.9), bevel=0.001)])
                  + [text("jade_mark", "瑞华玻璃", 0.0022, (jb[0], ty - 0.0004, jb[2]), brass, facing="-y")]))
    tc = (x1 + 0.012, ty + 0.005, cz1 - 0.008)
    cup = k.flat("porcelain", (0.92, 0.92, 0.9), 0.2)
    p.append(prop("teacup", tc, [cyl("cup", 0.0045, 0.009, tc, cup, radius2=0.0055),
                                 cyl("cup_tea", 0.0048, 0.0006, (tc[0], tc[1] + 0.003, tc[2]), k.flat("powder", (0.95, 0.95, 0.93), 0.9))]))
    p.append(figure("shen", (x1, fy, 0.02), (0.2, 0.2, 0.25)))

    # 2号 (许医生): the doctor's bag (its lid opens) with the vial rack, the trench coat, the bank letter.
    x2 = (walls_x[0] + walls_x[1]) / 2
    mc = (x2 + 0.012, fy, 0.03)
    bag = k.tex("brown_leather", uv=40)
    s += [box("bag_body", (0.034, 0.018, 0.02), (mc[0], fy + 0.009, mc[2]), bag, bevel=0.0015)]
    p.append(prop("medcase", (mc[0], fy + 0.018, mc[2] - 0.01), [box("bag_lid", (0.035, 0.004, 0.021), (mc[0], fy + 0.02, mc[2]), bag, bevel=0.0012),
                                                               box("bag_handle", (0.012, 0.004, 0.002), (mc[0], fy + 0.024, mc[2]), bag, bevel=0)]))
    vr = (mc[0], fy + 0.011, mc[2])
    glass = k.flat("vial_glass", (0.55, 0.35, 0.2), 0.15)
    vials = [box("rack", (0.028, 0.004, 0.012), (vr[0], vr[1] - 0.004, vr[2]), k.flat("rack_wood", (0.7, 0.6, 0.45), 0.6), bevel=0)]
    for i in range(5):
        if i == 2:
            continue  # the empty slot (安眠散)
        vials.append(cyl(f"vial{i}", 0.002, 0.009, (vr[0] - 0.01 + i * 0.005, vr[1] + 0.001, vr[2]), glass, verts=10))
    p.append(prop("vials", vr, vials))
    tcoat = (walls_x[1] - 0.006, fy + 0.06, 0.0)
    beige = k.flat("beige", (0.78, 0.68, 0.5), 0.85)
    p.append(prop("trenchcoat", tcoat, [box("tcoat_body", (0.007, 0.045, 0.024), tcoat, beige, bevel=0.002),
                                        box("tcoat_pocket", (0.001, 0.008, 0.01), (tcoat[0] - 0.0038, tcoat[1] - 0.012, tcoat[2]), k.flat("beige_dark", (0.62, 0.52, 0.36), 0.85), bevel=0),
                                        box("tcoat_receipt", (0.0006, 0.006, 0.007), (tcoat[0] - 0.0043, tcoat[1] - 0.007, tcoat[2]), paper, bevel=0)]))
    bl = (walls_x[0] + 0.017, fy + 0.0235, cz0 + 0.035)
    p.append(prop("bankletter", bl, [box("envelope", (0.014, 0.0008, 0.02), bl, k.flat("envelope", (0.85, 0.78, 0.6), 0.8), bevel=0),
                                     text("bank_t", "银行", 0.004, (bl[0], bl[1] + 0.0006, bl[2]), k.flat("seal_red", (0.7, 0.1, 0.08), 0.6), facing="+y")]))
    p.append(figure("xu", (x2 - 0.01, fy, -0.06), (0.78, 0.68, 0.5)))

    # 3号 (苏小姐): the telegram on the table.
    x3 = (walls_x[1] + walls_x[2]) / 2
    tg = (x3, ty + 0.0005, cz1 - 0.009)
    p.append(prop("telegram", tg, [box("telegram_paper", (0.016, 0.0006, 0.012), tg, k.flat("telegram", (0.85, 0.8, 0.62), 0.8), bevel=0),
                                   text("telegram_t", "电报", 0.0045, (tg[0], tg[1] + 0.0005, tg[2]), ink, facing="+y")]))
    p.append(figure("su", (x3 + 0.01, fy, -0.06), (0.15, 0.35, 0.25)))

    # 4号 (白先生): the prop box (its lid opens) with glass gems; a top hat.
    x4 = (walls_x[2] + walls_x[3]) / 2
    pb = (x4, fy, 0.03)
    lacq = k.flat("lacquer_black", (0.05, 0.05, 0.06), 0.25)
    s += [box("pbox_body", (0.034, 0.016, 0.022), (pb[0], fy + 0.008, pb[2]), lacq, bevel=0.001)]
    for i, col in enumerate([(0.8, 0.1, 0.1), (0.1, 0.2, 0.8), (0.9, 0.75, 0.1), (0.8, 0.1, 0.4), (0.15, 0.4, 0.9)]):
        s.append(sphere(f"gem{i}", 0.0022, (pb[0] - 0.009 + i * 0.0045, fy + 0.0165, pb[2] + (0.003 if i % 2 else -0.003)), k.flat(f"gem{i}", col, 0.05)))
    p.append(prop("propbox", (pb[0], fy + 0.016, pb[2] - 0.011), [box("pbox_lid", (0.035, 0.004, 0.023), (pb[0], fy + 0.018, pb[2]), lacq, bevel=0.0008),
                                                                 box("pbox_star", (0.008, 0.0005, 0.008), (pb[0], fy + 0.0202, pb[2]), brass, bevel=0)]))
    s += [cyl("tophat", 0.006, 0.012, (walls_x[3] - 0.017, fy + 0.033, cz0 + 0.014), lacq), cyl("tophat_brim", 0.009, 0.001, (walls_x[3] - 0.017, fy + 0.0275, cz0 + 0.014), lacq)]
    p.append(figure("bai", (x4, fy, -0.06), (0.05, 0.05, 0.06)))

    # The attendant's corner: a desk with the patrol book, a lamp. The timetable in the corridor.
    x5 = (walls_x[3] + W / 2) / 2
    s += table("adesk", (x5 + 0.02, fy, cz1 - 0.02), (0.04, 0.03, 0.025), lwood)
    pt = (x5 + 0.015, fy + 0.0325, cz1 - 0.02)
    p.append(prop("patrol", pt, book("patrol", pt, (0.016, 0.003, 0.02), k.flat("book_black", (0.1, 0.1, 0.1), 0.6), paper)
                  + [text("patrol_t", "巡查", 0.0042, (pt[0], pt[1] + 0.0017, pt[2]), paper, facing="+y")]))
    s += [cyl("alamp", 0.004, 0.01, (x5 + 0.032, fy + 0.035, cz1 - 0.02), k.flat("lamp_glow", (1, 0.85, 0.5), 0.3, emission=(1, 0.75, 0.4)))]
    p.append(figure("meng", (x5 - 0.012, fy, 0.02), (0.1, 0.15, 0.35)))
    tt = (-0.02, fy + 0.06, -D / 2 + 0.007)
    p.append(prop("timetable", tt, [box("tt_frame", (0.04, 0.03, 0.002), tt, lwood, bevel=0.0004),
                                    plane("tt_paper", (0.036, 0.026), (tt[0], tt[1], tt[2] + 0.0011), paper),
                                    text("tt_title", "沪宁夜车时刻", 0.0035, (tt[0], tt[1] + 0.009, tt[2] + 0.0014), ink),
                                    text("tt_row", "镇江 23:35 到 23:45 开", 0.0028, (tt[0], tt[1] + 0.001, tt[2] + 0.0014), ink),
                                    text("tt_row2", "南京 01:20 到", 0.0028, (tt[0], tt[1] - 0.006, tt[2] + 0.0014), ink)]))
    finish("train", s, p)


# ---- 4. 春和戏园：backstage rooms in front; the stage on the platform at the back -------------------

def opera():
    k.reset()
    plaster = k.tex("painted_plaster_wall", uv=8, tint=(0.95, 0.9, 0.82))
    planks = k.tex("old_planks_02", uv=14)
    floor = k.tex("brick_floor_02", uv=16)
    stage_wood = k.tex("dark_wooden_planks", uv=14)
    carpet = k.tex("quatrefoil_jacquard_fabric", uv=30, tint=(0.75, 0.2, 0.15))
    wood = k.tex("dark_wood", uv=24)
    lwood = k.tex("fine_grained_wood", uv=24)
    rosewood = k.tex("rosewood_veneer1", uv=30)
    red = k.flat("lacquer_red", (0.55, 0.07, 0.05), 0.35)
    gold = k.flat("gold", (0.85, 0.65, 0.25), 0.3, 1.0)
    brass = k.flat("brass", (0.78, 0.6, 0.3), 0.3, 1.0)
    paper = k.flat("paper", (0.92, 0.88, 0.78), 0.8)
    redpaper = k.flat("red_paper", (0.75, 0.12, 0.08), 0.8)
    ink = k.flat("ink", (0.08, 0.06, 0.05), 0.5)
    black = k.flat("cloth_black", (0.06, 0.06, 0.07), 0.8)
    blue = k.flat("cloth_blue", (0.2, 0.3, 0.5), 0.85)
    rosin = k.flat("rosin", (0.9, 0.72, 0.3), 0.6)
    white = k.flat("horsehair", (0.95, 0.94, 0.9), 0.6)
    kingfisher = k.flat("kingfisher", (0.05, 0.45, 0.65), 0.3, 0.2)
    s = base("春和戏园")
    s += shell(plaster, plaster, floor, planks, stage_wood, 0.0)
    s += [box("part_front2", (0.006, LOW, 0.11), (-0.095, LOW / 2, 0.115), plaster, bevel=0),
          box("part_front3", (0.006, LOW, 0.11), (0.095, LOW / 2, 0.115), plaster, bevel=0)]
    # shell() puts partitions at x = 0 (front and upstairs); this scene has three rooms below and an open stage above.
    for o in [o for o in s if o.name in ("part_front", "part_back")]:
        s.remove(o)
        bpy.data.objects.remove(o)
    s.append(box("rug", (0.15, 0.0012, 0.15), (0.0, 0.0026, 0.09), k.flat("rug", (0.45, 0.18, 0.12), 0.9), bevel=0))

    # The stage (back, on the platform): red carpet, two red pillars, the embroidered backdrop with the
    # two doors (上场门 left, 下场门 right) and a carved beam; the percussion in the left wing.
    s += [box("stage_carpet", (0.3, 0.0012, 0.15), (0, U + 0.0022, -0.085), carpet, bevel=0)]
    for x in (-0.15, 0.15):
        s += [cyl(f"pillar{x}", 0.0055, BACK_WALL - U + 0.01, (x, U + (BACK_WALL - U + 0.01) / 2, -0.012), red),
              cyl(f"pillar_base{x}", 0.008, 0.004, (x, U + 0.002, -0.012), k.flat("stone", (0.6, 0.58, 0.55), 0.8))]
    s += [box("stage_beam", (0.32, 0.012, 0.008), (0, BACK_WALL + 0.006, -0.012), red, bevel=0.0005),
          box("stage_beam_gold", (0.3, 0.004, 0.001), (0, BACK_WALL + 0.006, -0.0075), gold, bevel=0)]
    backdrop = os.path.join(TEX, "shoujiu.jpeg")
    bmat = k.picture(backdrop, "shoujiu") if os.path.exists(backdrop) else k.flat("backdrop_red", (0.6, 0.1, 0.08), 0.7)
    s.append(plane("backdrop", (0.24, BACK_WALL - U - 0.01), (0, U + (BACK_WALL - U - 0.01) / 2, -0.169), bmat))
    for x, label in ((-0.11, "出将"), (0.11, "入相")):
        s += [box(f"door{label}", (0.026, 0.06, 0.002), (x, U + 0.03, -0.1685), k.flat("door_curtain", (0.65, 0.5, 0.15), 0.6), bevel=0),
              box(f"lintel{label}", (0.034, 0.012, 0.003), (x, U + 0.068, -0.168), red, bevel=0),
              text(f"doorsign{label}", label, 0.006, (x, U + 0.068, -0.1665), gold)]
    # The musicians' corner (right wing): drums and a gong on a stand. 班主's desk in the left wing.
    s += [cyl("drum_big", 0.012, 0.014, (0.215, U + 0.02, -0.12), red), cyl("drum_skin", 0.0115, 0.0008, (0.215, U + 0.0272, -0.12), paper),
          cyl("drum_small", 0.007, 0.006, (0.24, U + 0.03, -0.08), k.flat("drum_wood", (0.3, 0.15, 0.08), 0.5)),
          box("gong_stand", (0.026, 0.035, 0.003), (0.245, U + 0.0175, -0.145), wood, bevel=0),
          cyl("gong", 0.01, 0.0015, (0.245, U + 0.025, -0.143), brass, axis="z")]
    s += table("bandesk", (-0.22, U, -0.11), (0.05, 0.032, 0.03), rosewood)
    s += table("banchair", (-0.22, U, -0.075), (0.02, 0.02, 0.02), rosewood, leg=0.003)
    p = []
    lg = (-0.21, U + 0.0345, -0.11)
    p.append(prop("ledger", lg, book("ledger", lg, (0.018, 0.004, 0.024), k.flat("book_indigo", (0.12, 0.15, 0.3), 0.7), paper)
                  + [plane("ledger_label", (0.006, 0.016), (lg[0] + 0.004, lg[1] + 0.0021, lg[2]), paper, facing="+y"),
                     text("ledger_t", "戏份", 0.0035, (lg[0] + 0.004, lg[1] + 0.0023, lg[2]), ink, facing="+y")]))
    s += [cyl("teapot", 0.005, 0.007, (-0.235, U + 0.0355, -0.115), k.flat("zisha", (0.45, 0.2, 0.12), 0.5))]
    p.append(figure("huo", (-0.2, U, -0.05), (0.15, 0.2, 0.35)))
    p.append(figure("ma", (0.02, U, -0.07), (0.06, 0.06, 0.07)))
    # Steps up from the backstage to each door.
    s += stairs("stairl", -0.13, 0.03, 0.06, lwood)
    s += stairs("stairr", 0.13, 0.03, 0.06, lwood)

    # 管箱房 (front left): the red headgear trunk (its lid opens; the empty brocade box inside), other trunks,
    # a weapons rack, 老葛's coat on a hook on the wall.
    tx, tz = -0.2, 0.085
    tw, th, td = 0.05, 0.026, 0.03
    s += [box("trunk_body", (tw, th, td), (tx, th / 2, tz), red, bevel=0.001),
          box("trunk_band", (tw + 0.001, 0.003, td + 0.001), (tx, th * 0.3, tz), brass, bevel=0),
          box("trunk_lock", (0.006, 0.007, 0.002), (tx, th - 0.004, tz + td / 2 + 0.001), brass, bevel=0.0004),
          box("trunk_in", (tw - 0.004, 0.001, td - 0.004), (tx, th - 0.008, tz), k.flat("trunk_lining", (0.85, 0.75, 0.4), 0.8), bevel=0)]
    p.append(prop("kuitou", (tx, th, tz - td / 2), [box("trunk_lid", (tw + 0.002, 0.005, td + 0.002), (tx, th + 0.0025, tz), red, bevel=0.0012),
                                                   box("trunk_lid_band", (tw + 0.003, 0.0012, 0.004), (tx, th + 0.0035, tz), brass, bevel=0),
                                                   text("trunk_word", "盔头", 0.006, (tx, th + 0.0052, tz + 0.004), gold, facing="+y")]))
    cb = (tx - 0.006, th - 0.004, tz)
    brocade = k.flat("brocade", (0.75, 0.55, 0.15), 0.5, 0.3)
    p.append(prop("crownbox", cb, [box("cbox_base", (0.022, 0.006, 0.018), cb, brocade, bevel=0.0008),
                                   box("cbox_well", (0.018, 0.0008, 0.014), (cb[0], cb[1] + 0.0028, cb[2]), k.flat("velvet_yellow", (0.9, 0.75, 0.2), 0.9), bevel=0),
                                   box("cbox_clasp", (0.004, 0.003, 0.001), (cb[0], cb[1], cb[2] - 0.0095), brass, bevel=0),
                                   box("cbox_hair", (0.0003, 0.006, 0.0003), (cb[0] + 0.001, cb[1] - 0.001, cb[2] - 0.0102), white, bevel=0),
                                   box("cbox_feather", (0.004, 0.0004, 0.0012), (cb[0] + 0.012, cb[1] - 0.0025, cb[2] + 0.004), kingfisher, bevel=0)]))
    s += [box("trunk2", (0.045, 0.024, 0.028), (-0.25, 0.012, 0.15), k.flat("trunk_black", (0.1, 0.08, 0.07), 0.4), bevel=0.001),
          box("trunk3", (0.04, 0.02, 0.026), (-0.25, 0.034, 0.15), red, bevel=0.001),
          box("weapon_rack", (0.004, 0.05, 0.05), (-0.268, 0.025, 0.05), wood, bevel=0)]
    for i in range(4):
        s += [cyl(f"spear{i}", 0.0008, 0.09, (-0.264, 0.045, 0.03 + i * 0.012), lwood),
              cyl(f"spear_tip{i}", 0.0016, 0.007, (-0.264, 0.093, 0.03 + i * 0.012), k.flat("steel", (0.75, 0.76, 0.78), 0.25, 1.0), radius2=0.0001)]
    s += [cyl("coat_hook", 0.0008, 0.006, (-0.16, 0.06, 0.004), brass, axis="z")]
    cc = (-0.16, 0.042, 0.007)
    p.append(prop("guazi", cc, [box("gua_body", (0.028, 0.036, 0.006), cc, blue, bevel=0.002),
                               box("gua_sleeve_l", (0.006, 0.03, 0.005), (cc[0] - 0.016, cc[1] - 0.003, cc[2]), blue, bevel=0.002),
                               box("gua_sleeve_r", (0.006, 0.03, 0.005), (cc[0] + 0.016, cc[1] - 0.003, cc[2]), blue, bevel=0.002),
                               box("gua_pocket", (0.001, 0.008, 0.008), (cc[0] - 0.0145, cc[1] - 0.008, cc[2]), k.flat("cloth_blue_dark", (0.14, 0.2, 0.35), 0.85), bevel=0),
                               box("gua_keys", (0.0012, 0.004, 0.004), (cc[0] - 0.0155, cc[1] - 0.004, cc[2]), brass, bevel=0),
                               box("gua_rosin", (0.0004, 0.0015, 0.0015), (cc[0] - 0.0163, cc[1] - 0.004, cc[2] + 0.001), rosin, bevel=0)]))
    p.append(figure("ge", (-0.14, 0.0, 0.12), (0.2, 0.3, 0.5)))

    # 化妆间 (front middle): the dressing table against the stage front with 芸姐's comb box (its lid opens),
    # a stool with 小马's boots beside it, the costume rack, the playbill on the wall.
    mx = 0.05
    s += ai_or("mirror", (mx, 0.0, 0.022), 0, lambda: [box("dtable", (0.05, 0.035, 0.028), (mx, 0.0175, 0.022), rosewood),
                                                       box("dmirror", (0.03, 0.035, 0.002), (mx, 0.055, 0.01), k.flat("mirror", (0.8, 0.85, 0.9), 0.05, 1.0))])
    ch = (mx + 0.012, 0.0395, 0.03)
    cbw, cbh, cbd = 0.018, 0.008, 0.012
    s += [box("comb_body", (cbw, cbh, cbd), (ch[0], ch[1] + cbh / 2, ch[2]), k.flat("lacquer_black", (0.05, 0.05, 0.06), 0.25), bevel=0.0006)]
    for i in range(5):
        s.append(box(f"feather{i}", (0.003, 0.0006, 0.0015), (ch[0] - 0.005 + i * 0.0025, ch[1] + cbh - 0.002, ch[2] + (0.002 if i % 2 else -0.002)), kingfisher, bevel=0))
    s.append(cyl("glue", 0.0015, 0.004, (ch[0] + 0.006, ch[1] + cbh - 0.001, ch[2]), k.flat("glue_glass", (0.6, 0.5, 0.3), 0.1)))
    p.append(prop("combbox", (ch[0], ch[1] + cbh, ch[2] - cbd / 2), [box("comb_lid", (cbw + 0.001, 0.002, cbd + 0.001), (ch[0], ch[1] + cbh + 0.001, ch[2]), k.flat("lacquer_black", (0.05, 0.05, 0.06), 0.25), bevel=0.0005),
                                                                       cyl("comb_inlay", 0.003, 0.0004, (ch[0], ch[1] + cbh + 0.0021, ch[2]), k.flat("mother_of_pearl", (0.9, 0.9, 0.95), 0.2), verts=16)]))
    s += [cyl("stool", 0.008, 0.022, (0.0, 0.011, 0.07), rosewood)]
    bt = (0.025, 0.006, 0.085)
    sole = k.flat("sole_white", (0.92, 0.9, 0.85), 0.7)
    boots = []
    for i, ox in enumerate((-0.006, 0.006)):
        boots += [box(f"boot{i}_upper", (0.008, 0.012, 0.02), (bt[0] + ox, bt[1] + 0.004, bt[2]), black, bevel=0.0015),
                  box(f"boot{i}_sole", (0.0085, 0.006, 0.021), (bt[0] + ox, bt[1] - 0.003, bt[2]), sole, bevel=0.0005),
                  box(f"boot{i}_rosin", (0.007, 0.0004, 0.018), (bt[0] + ox, bt[1] - 0.0062, bt[2]), rosin, bevel=0)]
    p.append(prop("boots", bt, boots))
    s += ai_or("robe", (-0.04, 0.0, 0.02), 0, lambda: [box("rack", (0.05, 0.08, 0.004), (-0.04, 0.04, 0.02), wood)])
    # On the back wall of the music room, over the table.
    pb = (0.215, 0.064, 0.0012)
    p.append(prop("playbill", pb, [plane("bill_paper", (0.03, 0.04), pb, redpaper),
                                   text("bill_t", "今晚戏单", 0.0042, (pb[0], pb[1] + 0.014, pb[2] + 0.0004), gold),
                                   text("bill_1", "九点 三岔口", 0.0028, (pb[0], pb[1] + 0.005, pb[2] + 0.0004), gold),
                                   text("bill_2", "九点四十 拾玉镯", 0.0028, (pb[0], pb[1] - 0.003, pb[2] + 0.0004), gold),
                                   text("bill_3", "十点一刻 贵妃醉酒", 0.0028, (pb[0], pb[1] - 0.011, pb[2] + 0.0004), gold)]))
    p.append(figure("yun", (-0.035, 0.0, 0.11), (0.6, 0.55, 0.5)))

    # 琴房 (front right): a table with the jinghu (its bow's broken hair on the right), the instrument case on a
    # bench (its lid opens; the letter inside), a music stand.
    s += table("qtable", (0.2, 0, 0.04), (0.06, 0.032, 0.03), rosewood)
    jh = (0.19, 0.0335, 0.04)
    p.append(prop("jinghu", (jh[0], jh[1] + 0.006, jh[2]), ai_or("jinghu", jh, 70, lambda: [cyl("jh_pole", 0.0008, 0.04, (jh[0], jh[1] + 0.003, jh[2]), lwood, axis="x"),
                                                                                         cyl("jh_drum", 0.0025, 0.005, (jh[0] - 0.018, jh[1] + 0.003, jh[2]), k.flat("bamboo", (0.75, 0.6, 0.35), 0.5), axis="x")])
                  + [box("jh_bow", (0.034, 0.0006, 0.0006), (jh[0], jh[1] + 0.0015, jh[2] + 0.006), lwood, bevel=0),
                     box("jh_broken", (0.0003, 0.0003, 0.008), (jh[0] + 0.006, jh[1] + 0.0012, jh[2] + 0.009), white, bevel=0),
                     box("jh_rosin", (0.006, 0.003, 0.004), (jh[0] + 0.02, jh[1] + 0.0015, jh[2] - 0.008), rosin, bevel=0.0006)]))
    s += table("bench", (0.235, 0, 0.12), (0.06, 0.018, 0.02), wood, leg=0.003)
    qb = (0.235, 0.0195, 0.12)
    qw, qh, qd = 0.05, 0.008, 0.014
    s += [box("qbox_body", (qw, qh, qd), (qb[0], qb[1] + qh / 2, qb[2]), k.tex("brown_leather", uv=40), bevel=0.001),
          box("qbox_letter", (0.016, 0.0006, 0.009), (qb[0] - 0.008, qb[1] + qh - 0.002, qb[2]), k.flat("envelope", (0.85, 0.78, 0.6), 0.8), bevel=0)]
    p.append(prop("qinbox", (qb[0], qb[1] + qh, qb[2] - qd / 2), [box("qbox_lid", (qw + 0.001, 0.003, qd + 0.001), (qb[0], qb[1] + qh + 0.0015, qb[2]), k.tex("brown_leather", uv=40), bevel=0.001),
                                                                  box("qbox_latch", (0.004, 0.002, 0.001), (qb[0], qb[1] + qh + 0.0005, qb[2] + qd / 2 + 0.001), brass, bevel=0)]))
    lt = (qb[0] - 0.008, qb[1] + qh - 0.0015, qb[2])
    p.append(prop("huaigu", lt, [box("letter_env", (0.016, 0.0007, 0.009), lt, k.flat("envelope", (0.85, 0.78, 0.6), 0.8), bevel=0),
                                 plane("letter_stripe", (0.004, 0.008), (lt[0], lt[1] + 0.0005, lt[2]), redpaper, facing="+y")]))
    s += [cyl("music_stand", 0.0008, 0.045, (0.16, 0.0225, 0.1), k.flat("iron", (0.12, 0.12, 0.13), 0.5, 0.6)),
          box("music_sheet", (0.016, 0.012, 0.001), (0.16, 0.047, 0.1), paper, bevel=0, pitch=-20)]
    p.append(figure("zhong", (0.165, 0.0, 0.135), (0.06, 0.06, 0.07)))
    finish("opera", s, p)


for c in WHICH:
    {"wutong": wutong, "inn": inn, "train": train, "opera": opera}[c]()
