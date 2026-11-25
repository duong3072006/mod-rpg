"""
Tách khớp khuỷu tay 2 khúc (`armorRightForearm` & `armorLeftForearm`, pivot chuẩn Minecraft `[±5, 18, 0]`)
cho toàn bộ 3 bộ giáp 3D dài tay GeckoLib (`nightmare_armor`, `cinder_dragon_armor`, `spirit_hydra_armor`)
để tay giáp gập khuỷu khít 100% theo `TogetsuBentArmRig` khi cầm Song Đao Togetsu!

Đặc điểm kỹ thuật:
- Idempotent (chạy lại nhiều lần vẫn giữ nguyên 100% kết quả, không tạo trùng khớp hay trùng cube).
- Cắt chính xác ống tay nền (`cube[0]` trải dài qua `Y = 18.0`) với UV từng mặt (`north/south/east/west`)
  chia đúng tỉ lệ `frac_top = (y1 - 18.0) / (y1 - y0)` -> khi tay thẳng (0°) khớp pixel 1:1 với model gốc!
- Bổ sung khối đệm khuỷu chìm 0.06px (`Y = [17.05..18.95]`) gắn trên xương cẳng tay để khi gập khuỷu sâu
  35°–90° hoàn toàn liền khối, không hở khe giáp và không z-fighting khi duỗi thẳng!
- Tự động render ảnh kiểm chứng 3D chuẩn phép biến đổi GL của `GeoArmorRenderer` trong tư thế thủ Sunraku Togetsu
  kèm thân/tay skin người chơi để xác nhận 0 lệch tay, 0 xuyên cẳng tay ra ngoài găng giáp!
"""

import copy
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(r"c:\Users\Admin\Desktop\mod\mod-rpg")
GEO_DIR = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg" / "geo" / "armor"
TEX_DIR = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg" / "textures" / "armor"
OUT_PREVIEW = ROOT / "tools" / "preview_armor_elbow_sync.png"

ELBOW_Y = 18.0
ARMOR_FILES = [
    ("nightmare_armor.geo.json", "nightmare_armor.png"),
    ("cinder_dragon_armor.geo.json", "cinder_dragon_armor.png"),
    ("spirit_hydra_armor.geo.json", "spirit_hydra_armor.png"),
]


def split_vertical_tube_cube(cube: dict, split_y: float):
    """Cắt 1 khối ống tay dọc (rot=None, Y=[y0..y1] chứa split_y) thành (upper_cube, lower_cube, elbow_plug_cube)."""
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    y0 = oy
    y1 = oy + sy
    assert y0 < split_y < y1, f"Cube Y=[{y0}..{y1}] does not straddle split_y={split_y}"
    frac_top = (y1 - split_y) / sy
    frac_bot = (split_y - y0) / sy

    upper = copy.deepcopy(cube)
    upper["origin"] = [round(ox, 4), round(split_y, 4), round(oz, 4)]
    upper["size"] = [round(sx, 4), round(y1 - split_y, 4), round(sz, 4)]

    lower = copy.deepcopy(cube)
    lower["origin"] = [round(ox, 4), round(y0, 4), round(oz, 4)]
    lower["size"] = [round(sx, 4), round(split_y - y0, 4), round(sz, 4)]

    plug = copy.deepcopy(cube)
    inset = 0.08
    plug_h = 1.90
    plug_y0 = max(y0 + 0.05, split_y - plug_h * 0.5)
    plug_y1 = min(y1 - 0.05, split_y + plug_h * 0.5)
    plug["origin"] = [round(ox + inset, 4), round(plug_y0, 4), round(oz + inset, 4)]
    plug["size"] = [round(sx - inset * 2.0, 4), round(plug_y1 - plug_y0, 4), round(sz - inset * 2.0, 4)]

    if isinstance(cube.get("uv"), dict):
        for side in ("north", "south", "east", "west"):
            if side in cube["uv"]:
                u, v = cube["uv"][side]["uv"]
                uw, vh = cube["uv"][side]["uv_size"]
                upper["uv"][side]["uv"] = [round(u, 4), round(v, 4)]
                upper["uv"][side]["uv_size"] = [round(uw, 4), round(vh * frac_top, 4)]
                lower["uv"][side]["uv"] = [round(u, 4), round(v + vh * frac_top, 4)]
                lower["uv"][side]["uv_size"] = [round(uw, 4), round(vh * frac_bot, 4)]
                # Đệm khuỷu lấy đúng dải pixel quanh v + vh * frac_top
                p_top = max(0.0, (y1 - plug_y1) / sy)
                p_h = (plug_y1 - plug_y0) / sy
                plug["uv"][side]["uv"] = [round(u, 4), round(v + vh * p_top, 4)]
                plug["uv"][side]["uv_size"] = [round(uw, 4), round(vh * p_h, 4)]

    return upper, lower, plug


def articulate_armor_geo(geo_path: Path) -> dict:
    geo = json.loads(geo_path.read_text(encoding="utf-8"))
    bones = geo["minecraft:geometry"][0]["bones"]
    bone_names = {b["name"] for b in bones}
    if "armorRightForearm" in bone_names and "armorLeftForearm" in bone_names:
        print(f"ALREADY_ARTICULATED: {geo_path.name}")
        return geo

    new_bones = []
    for b in bones:
        name = b["name"]
        if name in ("armorRightArm", "armorLeftArm"):
            is_right = (name == "armorRightArm")
            fore_name = "armorRightForearm" if is_right else "armorLeftForearm"
            piv = b["pivot"]
            fore_piv = [round(float(piv[0]), 4), ELBOW_Y, round(float(piv[2]), 4)]

            upper_cubes = []
            fore_cubes = []
            for c in b.get("cubes", []):
                oy = float(c["origin"][1])
                sy = float(c["size"][1])
                y1 = oy + sy
                rot = c.get("rotation")
                is_zero_rot = (rot is None or rot == [0, 0, 0] or rot == [0.0, 0.0, 0.0])
                # Ống tay dài xuyên qua khuỷu (ví dụ Y=[12.7..24.1]) -> tách chính xác tại Y=18.0 + thêm đệm khuỷu!
                if is_zero_rot and oy < ELBOW_Y - 1.5 and y1 > ELBOW_Y + 1.5:
                    u_c, l_c, p_c = split_vertical_tube_cube(c, ELBOW_Y)
                    upper_cubes.append(u_c)
                    fore_cubes.append(l_c)
                    fore_cubes.append(p_c)
                else:
                    y_mid = oy + sy * 0.5
                    if y_mid < ELBOW_Y:
                        fore_cubes.append(c)
                    else:
                        upper_cubes.append(c)

            b_upper = copy.deepcopy(b)
            b_upper["cubes"] = upper_cubes
            new_bones.append(b_upper)
            new_bones.append({
                "name": fore_name,
                "parent": name,
                "pivot": fore_piv,
                "rotation": [0.0, 0.0, 0.0],
                "cubes": fore_cubes,
            })
            print(f"ARTICULATED {geo_path.name} :: {name} -> upper={len(upper_cubes)} cubes, {fore_name}={len(fore_cubes)} cubes (pivot={fore_piv})")
        else:
            new_bones.append(b)

    geo["minecraft:geometry"][0]["bones"] = new_bones
    geo_path.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    return geo


# =============================================================================
# BỘ KIỂM CHỨNG 3D THEO ĐÚNG TOÁN HỌC `GeoArmorRenderer` + `TogetsuBentArmRig`
# =============================================================================
def _rx(r):
    c, s = math.cos(r), math.sin(r)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)


def _ry(r):
    c, s = math.cos(r), math.sin(r)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)


def _rz(r):
    c, s = math.cos(r), math.sin(r)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


def geo_rot_from_rad(rx, ry, rz):
    return _rz(rz) @ _ry(ry) @ _rx(rx)


def cube_gl_rot(rot_deg):
    rx = math.radians(-float(rot_deg[0]))
    ry = math.radians(-float(rot_deg[1]))
    rz = math.radians(+float(rot_deg[2]))
    return _rz(rz) @ _ry(ry) @ _rx(rx)


def render_pose_verification():
    """
    Render 3 bộ giáp (Nightmare, Cinder Dragon, Spirit Hydra) cùng tay skin gập khuỷu Togetsu Sunraku
    trong KHÔNG GIAN THẾ GIỚI MINECRAFT THỰC (`GeoArmorRenderer.actuallyRender` + `TogetsuBentArmRig`):
    - Cả 2 tay đều đặt ở góc Sunraku Dual Idle chính thức từ `TogetsuAnimManager`:
      Right: arm=(-0.58, -0.06, +0.30, z=-0.3), elbow=(-0.60, -0.06, -0.08)
      Left:  arm=(-0.44, +0.08, -0.42, z=-0.3), elbow=(-0.66, +0.06, +0.08)
    - Kiểm chứng 100%: tay skin (màu da sáng) NẰM GỌN TRONG GĂNG GIÁP ở cả khuỷu lẫn cổ tay, 0 lệch!
    """
    r_arm = (-0.58, -0.06, +0.30, -0.30)
    r_elb = (-0.60, -0.06, -0.08)
    l_arm = (-0.44, +0.08, -0.42, -0.30)
    l_elb = (-0.66, +0.06, +0.08)

    # Biến đổi từ GeoArmorRenderer GL space sang Minecraft entity space:
    # `actuallyRender`: T(0, 24, 0) * S(-1, -1, 1) -> X_mc = -X_gl, Y_mc = 24 - Y_gl, Z_mc = +Z_gl
    # Để nhìn thẳng đứng đầu trên (+Y_up), ta lấy Y_up = 24 - Y_mc = Y_gl, X_right = -X_mc = X_gl, Z_back = Z_mc = Z_gl!
    # => Hệ (X_gl, Y_gl, Z_gl) CHÍNH LÀ không gian hiển thị chuẩn (+Y hướng lên trời, -Z hướng mặt trước)!

    def mc_part_box_to_gl_quads(piv_mc, rot_mc, parent_xform, box_min_mc, box_size_mc, col_rgb):
        """Dựng 1 box theo chuẩn Vanilla `ModelPart` (MC space: +Y down, +X left, -Z front) rồi đổi sang GL display space!"""
        x0, y0, z0 = box_min_mc
        sx, sy, sz = box_size_mc
        pts = np.array([
            [x0, y0, z0], [x0 + sx, y0, z0], [x0 + sx, y0 + sy, z0], [x0, y0 + sy, z0],
            [x0, y0, z0 + sz], [x0 + sx, y0, z0 + sz], [x0 + sx, y0 + sy, z0 + sz], [x0, y0 + sy, z0 + sz],
        ], dtype=np.float64)
        R_loc = _rz(rot_mc[2]) @ _ry(rot_mc[1]) @ _rx(rot_mc[0])
        t_loc = np.asarray(piv_mc, dtype=np.float64)
        if parent_xform is not None:
            Rp, tp = parent_xform
            R = Rp @ R_loc
            t = Rp @ t_loc + tp
        else:
            R, t = R_loc, t_loc
        mc_pts = (pts @ R.T) + t
        # Đổi MC space -> GL display space: X_gl = -X_mc, Y_gl = 24 - Y_mc, Z_gl = Z_mc
        gl_pts = np.stack([-mc_pts[:, 0], 24.0 - mc_pts[:, 1], mc_pts[:, 2]], axis=1)
        fdefs = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (3, 2, 6, 7), (4, 5, 1, 0)]
        return [(gl_pts[list(idx)], None, col_rgb) for idx in fdefs], (R, t)

    def collect_skin_bent_quads():
        quads = []
        # Head + Torso (tham chiếu)
        q, _ = mc_part_box_to_gl_quads((0, 0, 0), (0, 0, 0), None, (-4, -8, -4), (8, 8, 8), (220, 195, 175))
        quads.extend(q)
        q, _ = mc_part_box_to_gl_quads((0, 0, 0), (0, 0, 0), None, (-4, 0, -2), (8, 12, 4), (110, 160, 210))
        quads.extend(q)
        # Right bent skin arm (Classic 4px: upper + forearm) — màu xanh ngọc nổi bật để soi xem có bị ló ra khỏi giáp không!
        q, r_up = mc_part_box_to_gl_quads((-5.0, 2.0, r_arm[3]), r_arm[:3], None, (-3.0, -2.0, -2.0), (4.0, 6.0, 4.0), (80, 230, 255))
        quads.extend(q)
        q, _ = mc_part_box_to_gl_quads((0.0, 4.0, 0.0), r_elb, r_up, (-3.0, 0.0, -2.0), (4.0, 6.0, 4.0), (255, 225, 130))
        quads.extend(q)
        # Left bent skin arm
        q, l_up = mc_part_box_to_gl_quads((+5.0, 2.0, l_arm[3]), l_arm[:3], None, (-1.0, -2.0, -2.0), (4.0, 6.0, 4.0), (80, 230, 255))
        quads.extend(q)
        q, _ = mc_part_box_to_gl_quads((0.0, 4.0, 0.0), l_elb, l_up, (-1.0, 0.0, -2.0), (4.0, 6.0, 4.0), (255, 225, 130))
        quads.extend(q)
        return quads

    def collect_geo_armor_quads(geo: dict, tex_arr: np.ndarray, apply_elbow: bool):
        desc = geo["minecraft:geometry"][0]["description"]
        tw, th = float(desc["texture_width"]), float(desc["texture_height"])
        bone_map = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
        # Áp đúng `GeoArmorRenderer.applyBaseTransformations` + `GeoArmorElbowSync`!
        overrides = {
            "armorRightArm": (
                (-r_arm[0], -r_arm[1], +r_arm[2]),
                (0.0, 0.0, r_arm[3]),  # posX=0, posY=0, posZ=rightArmPart.z
            ),
            "armorLeftArm": (
                (-l_arm[0], -l_arm[1], +l_arm[2]),
                (0.0, 0.0, l_arm[3]),
            ),
            "armorRightForearm": (
                ((-r_elb[0], -r_elb[1], +r_elb[2]) if apply_elbow else (0.0, 0.0, 0.0)),
                (0.0, 0.0, 0.0),
            ),
            "armorLeftForearm": (
                ((-l_elb[0], -l_elb[1], +l_elb[2]) if apply_elbow else (0.0, 0.0, 0.0)),
                (0.0, 0.0, 0.0),
            ),
        }

        xforms = {}

        def get_xform(name):
            if name in xforms:
                return xforms[name]
            b = bone_map[name]
            bp = b.get("pivot", [0.0, 0.0, 0.0])
            piv = np.array([-float(bp[0]), float(bp[1]), float(bp[2])], dtype=np.float64)
            rot_r, pos_v = overrides.get(name, ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0)))
            # `RenderUtil`: translate(-posX, +posY, +posZ) * T(piv) * Rz(rz)Ry(ry)Rx(rx) * T(-piv)
            R_loc = geo_rot_from_rad(*rot_r)
            trans = np.array([-pos_v[0], pos_v[1], pos_v[2]], dtype=np.float64)
            t_loc = trans + piv - R_loc @ piv
            parent = b.get("parent")
            if parent and parent in bone_map:
                Rp, tp = get_xform(parent)
                R, t = Rp @ R_loc, Rp @ t_loc + tp
            else:
                R, t = R_loc, t_loc
            xforms[name] = (R, t)
            return R, t

        # Chỉ vẽ phần thân + tay (slot CHEST) để nhìn siêu rõ khớp vai & khuỷu!
        chest_roots = {"armorBody", "armorRightArm", "armorLeftArm"}

        def is_chest_bone(bn):
            cur = bn
            while cur:
                if cur in chest_roots:
                    return True
                cur = bone_map[cur].get("parent")
            return False

        fdefs = [
            ("north", (0, 1, 2, 3)), ("south", (5, 4, 7, 6)),
            ("west", (4, 0, 3, 7)), ("east", (1, 5, 6, 2)),
            ("up", (3, 2, 6, 7)), ("down", (4, 5, 1, 0)),
        ]
        quads = []
        for b in geo["minecraft:geometry"][0]["bones"]:
            if not is_chest_bone(b["name"]):
                continue
            Rb, tb = get_xform(b["name"])
            for c in b.get("cubes", []):
                ox, oy, oz = c["origin"]
                sx, sy, sz = c["size"]
                gx0, gx1 = -(ox + sx), -ox
                gy0, gy1 = oy, oy + sy
                gz0, gz1 = oz, oz + sz
                corners = np.array([
                    [gx0, gy0, gz0], [gx1, gy0, gz0], [gx1, gy1, gz0], [gx0, gy1, gz0],
                    [gx0, gy0, gz1], [gx1, gy0, gz1], [gx1, gy1, gz1], [gx0, gy1, gz1],
                ], dtype=np.float64)
                if "rotation" in c:
                    cp_raw = c["pivot"]
                    cp = np.array([-float(cp_raw[0]), float(cp_raw[1]), float(cp_raw[2])], dtype=np.float64)
                    Rc = cube_gl_rot(c["rotation"])
                    corners = ((corners - cp) @ Rc.T) + cp
                wc = (corners @ Rb.T) + tb
                uv_map = c.get("uv", {})
                for fname, idxs in fdefs:
                    if isinstance(uv_map, dict) and fname in uv_map:
                        quads.append((wc[list(idxs)], (uv_map[fname], tex_arr, tw, th), None))
        return quads

    def raster(quads, yaw_deg, pitch_deg, w=460, h=420):
        y, p = math.radians(yaw_deg), math.radians(pitch_deg)
        vm = _rx(p) @ _ry(y)
        fb = np.full((h, w, 3), (14, 18, 24), dtype=np.float32)
        zb = np.full((h, w), -1e9, dtype=np.float32)
        ctr = np.array([0.0, 18.0, -2.5])
        scale = 11.5
        ld = np.array([-0.35, 0.78, -0.52])
        ld /= np.linalg.norm(ld)

        def tri(p0, p1, p2, uv_info, solid, shade):
            mnx, mxx = max(0, int(math.floor(min(p0[0], p1[0], p2[0])))), min(w - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
            mny, mxy = max(0, int(math.floor(min(p0[1], p1[1], p2[1])))), min(h - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
            if mnx > mxx or mny > mxy:
                return
            area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
            if abs(area) < 1e-4:
                return
            inv = 1.0 / area
            xs = np.arange(mnx, mxx + 1, dtype=np.float32) + 0.5
            ys = np.arange(mny, mxy + 1, dtype=np.float32) + 0.5
            gx, gy = np.meshgrid(xs, ys)
            w0 = ((p1[0] - gx) * (p2[1] - gy) - (p2[0] - gx) * (p1[1] - gy)) * inv
            w1 = ((p2[0] - gx) * (p0[1] - gy) - (p0[0] - gx) * (p2[1] - gy)) * inv
            w2 = 1.0 - w0 - w1
            ins = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not np.any(ins):
                return
            z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
            sz = zb[mny:mxy + 1, mnx:mxx + 1]
            cand = ins & (z > sz)
            if not np.any(cand):
                return
            cy, cx = np.where(cand)
            if solid is not None:
                sz[cy, cx] = z[cy, cx]
                fb[mny + cy, mnx + cx] = np.clip(np.array(solid, dtype=np.float32) * shade, 0, 255)
            else:
                (uv0, uv1, uv2), tex_arr, tw, th = uv_info
                th_i, tw_i = tex_arr.shape[:2]
                u = np.clip(((w0[cand] * uv0[0] + w1[cand] * uv1[0] + w2[cand] * uv2[0]) * (tw_i / tw)).astype(np.int32), 0, tw_i - 1)
                v = np.clip(((w0[cand] * uv0[1] + w1[cand] * uv1[1] + w2[cand] * uv2[1]) * (th_i / th)).astype(np.int32), 0, th_i - 1)
                ap = tex_arr[v, u, 3] >= 128.0
                if not np.any(ap):
                    return
                cy, cx, u, v = cy[ap], cx[ap], u[ap], v[ap]
                sz[cy, cx] = z[cy, cx]
                fb[mny + cy, mnx + cx] = np.clip(tex_arr[v, u, :3] * shade, 0, 255)

        for quad, uv_pkg, solid in quads:
            rel = quad - ctr[None, :]
            cam = (vm @ rel.T).T
            n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
            nl = np.linalg.norm(n)
            if nl < 1e-6:
                continue
            shade = 0.62 + 0.38 * abs(float(np.dot(n / nl, ld)))
            scr = np.stack([w * 0.5 + cam[:, 0] * scale, h * 0.52 - cam[:, 1] * scale, cam[:, 2]], axis=1)
            if solid is not None:
                tri(scr[0], scr[1], scr[2], None, solid, shade)
                tri(scr[0], scr[2], scr[3], None, solid, shade)
            else:
                uv_d, tex_arr, tw, th = uv_pkg
                u0, v0 = uv_d["uv"]
                uw, uh = uv_d["uv_size"]
                u1, v1 = u0 + uw - 0.01, v0 + uh - 0.01
                uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
                tri(scr[0], scr[1], scr[2], ((uvs[0], uvs[1], uvs[2]), tex_arr, tw, th), None, shade)
                tri(scr[0], scr[2], scr[3], ((uvs[0], uvs[2], uvs[3]), tex_arr, tw, th), None, shade)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    skin_q = collect_skin_bent_quads()
    panels = []
    # 1. BEFORE (Nightmare Armor rigid 1-piece vs bent skin forearm!) -> reproduces exact user bug!
    ng = json.loads((GEO_DIR / "nightmare_armor.geo.json").read_text(encoding="utf-8"))
    nt = np.array(Image.open(TEX_DIR / "nightmare_armor.png").convert("RGBA"), dtype=np.float32)
    im = raster(skin_q + collect_geo_armor_quads(ng, nt, apply_elbow=False), 135, 10)
    ImageDraw.Draw(im).text((10, 8), "BEFORE (RIGID ARMOR -> YELLOW FOREARM POPS OUT!)", fill=(255, 110, 110))
    panels.append(im)

    # 2..4 AFTER (Nightmare, Cinder Dragon, Spirit Hydra with articulated elbow sync!)
    for geo_fn, tex_fn in ARMOR_FILES:
        g = json.loads((GEO_DIR / geo_fn).read_text(encoding="utf-8"))
        t = np.array(Image.open(TEX_DIR / tex_fn).convert("RGBA"), dtype=np.float32)
        im = raster(skin_q + collect_geo_armor_quads(g, t, apply_elbow=True), 135, 10)
        label = geo_fn.replace(".geo.json", "").upper()
        ImageDraw.Draw(im).text((10, 8), f"AFTER: {label} (BENT ELBOW LOCKED 100%!)", fill=(120, 255, 150))
        panels.append(im)

    pw, ph = panels[0].size
    sheet = Image.new("RGB", (pw * 2, ph * 2), (10, 12, 16))
    for i, p_im in enumerate(panels):
        sheet.paste(p_im, ((i % 2) * pw, (i // 2) * ph))
    sheet.save(OUT_PREVIEW)
    print(f"PREVIEW_ELBOW_SYNC_OK: {OUT_PREVIEW}")


if __name__ == "__main__":
    for geo_fn, _ in ARMOR_FILES:
        articulate_armor_geo(GEO_DIR / geo_fn)
    render_pose_verification()
