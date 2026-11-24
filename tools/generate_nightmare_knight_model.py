#!/usr/bin/env python3
"""
Bộ sinh Model (1.200–1.700 cubes, 50 bones) + Texture 1024x1024 (nearest, ánh kim thép đen đánh bóng,
sọ xương ngà sắc nét, màng cánh & áo choàng đỏ alpha-cutout 2 mặt) + 17 Animation mượt phong cách
Pacific Rim đồng bộ 0-tick tuyệt đối với NightmareAttackTiming.java + Preview 3D Z-buffer đủ 6 góc
cho Boss Nightmare Knight (abyssalrpg:nightmare_knight) — Phiên bản v3.

5 nâng cấp cấu trúc trọng tâm so với v2 (đạt 70–80% dáng & thần thái mẫu + 20–30% bản sắc Abyssal Spire):
  1. Chân thú gập chữ Z (`digitigrade` 4 đốt: `thigh` + `knee_spur` nhọn cong trước -> `shin` vát sâu về sau
     -> `ankle_hock` cổ chân thú cao có cựa sau -> `foot` 4 ngón vuốt cong xòe ±15° + cựa gót).
  2. Đôi cánh quỷ xòe rộng sang hai bên (±X) chỉ hơi vát sau ~22–26°, xương cánh thanh mảnh có khớp lồi,
     móng móc đỉnh cánh đỏ sẫm vượt cao hơn đầu, màng cánh đỏ rượu chuyển sắc mượt khít 100% (0 bậc thang).
  3. Áo choàng đỏ dáng chữ A siêu rộng (~1.95x eo) kéo dài sát gót, chia 3 tầng (`cape_upper`, `cape_mid`,
     `cape_lower`) + 2 vạt hông (`cape_l`, `cape_r`) xếp nếp rách tơi mềm mại; khố `tabard` gọn giữa 2 đùi chữ Z.
  4. Thanh đại kiếm quỷ răng cưa cân đối (~1.08x chiều cao thân, cầm chéo tự nhiên xuống trước–phải, không chạm đất),
     sống thép trắng bạc ánh kim sắc nét tương phản mạnh mép răng cưa đỏ máu & rãnh rune phát sáng.
  5. Cụm đầu phóng nhẹ ~1.15x, mặt sọ xương xám ngà sáng nổi bật trong bán mặt nạ thép đen có mào nhọn,
     đôi mắt đỏ fullbright, sừng đỏ thẫm dày có gân vòng cong vòng oai vệ, ngực thép đánh bóng + 2 khóa khiên
     rune đỏ, bắp tay cơ bắp tối quấn băng đỏ nối găng giáp nhiều gai.

Chạy: python tools/generate_nightmare_knight_model.py
"""

import json
import math
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
BACKUP_V3_PRE = ROOT / "tools" / "backup_nightmare_v2_pre_v3"
PREVIEW_DIR = ROOT / "tools" / "nightmare_previews"

# 1024x1024 nearest RGBA cho độ sắc nét tối đa trên thân khổng lồ ~19.5 block (7.5x scale!)
TEX_W = 1024
TEX_H = 1024

# Atlas 1024x1024 (tọa độ px): (x0, y0, w, h)
MAT = {
    "ARMOR_STEEL":   (0,   0,   256, 256),  # Thép đen đánh bóng ánh kim (#1E2128 -> #343945 -> #565E70 -> viền #8E97A8)
    "ARMOR_SPIKE":   (256, 0,   256, 256),  # Giáp vát cạnh sắc, khóa khiên gai & mũi giáp đầu gối ánh bạc lạnh
    "WING_MEMBRANE": (512, 0,   512, 384),  # Màng cánh đỏ rượu chuyển sắc mượt, vòm dơi & lỗ rách nhỏ alpha-cutout
    "BONE_RIB":      (0,   256, 256, 256),  # Lồng xương sườn cong xám ngà nổi khối 3D
    "SKULL_BONE":    (256, 256, 256, 256),  # Sọ xương mặt & hàm răng xám ngà sáng (#9E9892 -> #C7C0B8 -> #DFD9D0)
    "HORN_CRIMSON":  (0,   512, 256, 256),  # Sừng quỷ dày đỏ thẫm có gân vòng ngang sắc nét
    "FLESH_DARK":    (256, 512, 256, 256),  # Bắp tay cơ bắp nâu-xám tối săn chắc + dải băng đỏ sẫm
    "CAPE_TATTER":   (512, 384, 256, 384),  # Áo choàng đỏ thẫm xếp nếp dọc sâu + mép dưới rách tơi alpha-cutout
    "CLOTH_TATTER":  (768, 384, 256, 384),  # Khố vải đỏ giữa 2 đùi + băng tay đỏ sẫm
    "WING_BONE":     (0,   768, 256, 256),  # Khung xương cánh đen obsidian thanh mảnh có ánh cạnh thép
    "BLADE_STEEL":   (256, 768, 256, 256),  # Sống đại kiếm thép trắng bạc ánh kim sắc nét (#9AA2AE -> #D8DCE2 -> #F0F4FA)
    "BLADE_EDGE":    (512, 768, 256, 256),  # Mép lưỡi răng cưa & mũi kiếm đỏ máu (#8E141C -> #D9262E, P2 nóng rực)
    "RUNE_GLOW":     (768, 768, 128, 128),  # Rãnh rune máu thân kiếm, khóa khiên ngực & tinh thể chuôi (fullbright)
    "EYE_GLOW":      (896, 768, 128, 128),  # Đôi mắt đỏ rực fullbright trong hốc mắt sâu
    "CLAW_BLOOD":    (768, 896, 256, 128),  # Móng vuốt chân thú 4 ngón & móc đỉnh cánh đen-đỏ máu bóng
}


def backup_existing_files() -> None:
    if BACKUP_V3_PRE.exists() and any(BACKUP_V3_PRE.rglob("*.json")):
        print(f"BACKUP_SKIP: Đã có bản sao lưu trước v3 tại {BACKUP_V3_PRE}")
        return
    files = [
        ASSETS / "geo" / "entity" / "nightmare_knight.geo.json",
        ASSETS / "animations" / "entity" / "nightmare_knight.animation.json",
        ASSETS / "textures" / "entity" / "nightmare_knight.png",
        ASSETS / "textures" / "entity" / "nightmare_knight_glowmask.png",
        ASSETS / "textures" / "entity" / "nightmare_knight_enraged.png",
        ASSETS / "textures" / "entity" / "nightmare_knight_enraged_glowmask.png",
    ]
    copied = 0
    for f in files:
        if f.exists():
            dst = BACKUP_V3_PRE / f.relative_to(ASSETS)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dst)
            copied += 1
    if copied > 0:
        print(f"BACKUP_OK: Đã sao lưu {copied} file v2 vào {BACKUP_V3_PRE}")


def bedrock_rot_matrix(rot_deg):
    """
    Ma trận xoay CHÍNH XÁC 100% theo mã nguồn GeckoLib 4.8.3:
    R_true(rx, ry, rz) = Rz(-rz) @ Ry(+ry) @ Rx(-rx).
    """
    rx = -math.radians(rot_deg[0])
    ry = +math.radians(rot_deg[1])
    rz = -math.radians(rot_deg[2])
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]], dtype=np.float64)
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], dtype=np.float64)
    return Rz @ Ry @ Rx


def matrix_to_bedrock_euler(R: np.ndarray) -> list:
    """Giải ngược chính xác góc [rx, ry, rz] (độ) sao cho bedrock_rot_matrix([rx, ry, rz]) == R."""
    # R = Rz(c) @ Ry(b) @ Rx(a) với a = -rx, b = +ry, c = -rz
    sb = float(np.clip(-R[2, 0], -1.0, 1.0))
    b = math.asin(sb)
    cb = math.cos(b)
    if abs(cb) > 1e-5:
        a = math.atan2(R[2, 1], R[2, 2])
        c = math.atan2(R[1, 0], R[0, 0])
    else:
        a = math.atan2(-R[1, 2], R[1, 1])
        c = 0.0
    rot = [-math.degrees(a), +math.degrees(b), -math.degrees(c)]
    return [round(v, 2) for v in rot]


def uv_box(mat_key: str, pad: int = 4, sub_u=(0.0, 1.0), sub_v=(0.0, 1.0)) -> dict:
    x0, y0, w, h = MAT[mat_key]
    iw, ih = w - 2 * pad, h - 2 * pad
    ux = int(round(x0 + pad + sub_u[0] * iw))
    uy = int(round(y0 + pad + sub_v[0] * ih))
    uw = max(2, int(round((sub_u[1] - sub_u[0]) * iw)))
    uh = max(2, int(round((sub_v[1] - sub_v[0]) * ih)))
    f = {"uv": [ux, uy], "uv_size": [uw, uh]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def make_cube(origin, size, mat: str, rot=None, pivot=None,
              sub_u=(0.0, 1.0), sub_v=(0.0, 1.0), pad: int = 4,
              front_mat=None, edge_transparent: bool = False) -> dict:
    ox, oy, oz = float(origin[0]), float(origin[1]), float(origin[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    uvs = uv_box(mat, pad=pad, sub_u=sub_u, sub_v=sub_v)
    if front_mat:
        uvs["north"] = uv_box(front_mat, pad=pad)["north"]
    if edge_transparent:
        x0, y0, w, h = MAT[mat]
        trans = {"uv": [x0 + w - 6, y0 + h - 6], "uv_size": [4, 4]}
        for k in ("east", "west", "up", "down"):
            uvs[k] = trans

    c = {
        "origin": [round(ox, 3), round(oy, 3), round(oz, 3)],
        "size": [round(sx, 3), round(sy, 3), round(sz, 3)],
        "uv": uvs,
    }
    if rot is not None and any(abs(v) > 1e-4 for v in rot):
        if pivot is None:
            pivot = [ox + sx * 0.5, oy + sy * 0.5, oz + sz * 0.5]
        c["pivot"] = [round(float(pivot[0]), 3), round(float(pivot[1]), 3), round(float(pivot[2]), 3)]
        c["rotation"] = [round(float(rot[0]), 2), round(float(rot[1]), 2), round(float(rot[2]), 2)]
    return c


def mirror_x_cube(c: dict) -> dict:
    ox, oy, oz = c["origin"]
    sx, sy, sz = c["size"]
    m = {
        "origin": [round(-(ox + sx), 3), oy, oz],
        "size": [sx, sy, sz],
        "uv": c["uv"],
    }
    if "rotation" in c:
        rx, ry, rz = c["rotation"]
        px, py, pz = c["pivot"]
        m["pivot"] = [round(-px, 3), py, pz]
        m["rotation"] = [rx, round(-ry, 2), round(-rz, 2)]
    return m


def add_chain_rod(out_list: list, p_start, p_end, thickness_a: float, thickness_b: float,
                  segments: int, mat: str, overlap: float = 0.30, sub_v_span=(0.0, 1.0),
                  aspect_z: float = 1.0) -> None:
    """Dựng 1 thanh xương/sừng/gai/đốt chân liền khối 100% dọc đúng vector p_start -> p_end trong GeckoLib 4.8.3."""
    a = np.array(p_start, dtype=np.float64)
    b = np.array(p_end, dtype=np.float64)
    vec = b - a
    total_len = float(np.linalg.norm(vec))
    if total_len < 1e-4:
        return
    u_dir = vec / total_len
    horiz = math.hypot(u_dir[0], u_dir[2])
    a_rad = math.atan2(horiz, u_dir[1])
    ry_rad = math.atan2(u_dir[0], u_dir[2]) if horiz > 1e-5 else 0.0
    rot_deg = [-math.degrees(a_rad), +math.degrees(ry_rad), 0.0]

    rotated_y = bedrock_rot_matrix(rot_deg) @ np.array([0.0, 1.0, 0.0], dtype=np.float64)
    assert np.allclose(rotated_y, u_dir, atol=1e-3), f"Chain rod mismatch: {rotated_y} vs {u_dir}"

    for i in range(segments):
        t0 = i / float(segments)
        t1 = (i + 1) / float(segments)
        tm = 0.5 * (t0 + t1)
        thick = thickness_a + (thickness_b - thickness_a) * tm
        tz = thick * aspect_z
        p0 = a + vec * t0
        seg_len = (total_len / segments) + overlap
        v0 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * t0
        v1 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * t1
        out_list.append(make_cube(
            [p0[0] - thick * 0.5, p0[1] - overlap * 0.45, p0[2] - tz * 0.5],
            [thick, seg_len, tz],
            mat,
            rot=rot_deg,
            pivot=p0.tolist(),
            sub_v=(v0, v1),
        ))


def add_claw_spike(out_list: list, base_pt, tip_pt, w_base: float, mat: str = "ARMOR_SPIKE", segs: int = 3) -> None:
    add_chain_rod(out_list, base_pt, tip_pt, w_base, max(0.18, w_base * 0.22), segs, mat, overlap=0.20)


def add_curved_rod(out_list: list, pts: list, r_start: float, r_end: float,
                   mat: str, segs_per_span: int = 3, rib_rings: bool = False,
                   ring_mat: str = "HORN_CRIMSON") -> None:
    n = len(pts) - 1
    for i in range(n):
        t0, t1 = i / float(n), (i + 1) / float(n)
        ra = r_start + (r_end - r_start) * t0
        rb = r_start + (r_end - r_start) * t1
        add_chain_rod(out_list, pts[i], pts[i + 1], ra, rb, segs_per_span, mat, overlap=0.26, sub_v_span=(t0, t1))
        if rib_rings and i < n:
            for frac in (0.35, 0.72):
                pa = np.array(pts[i], dtype=np.float64)
                pb = np.array(pts[i + 1], dtype=np.float64)
                pm = pa * (1.0 - frac) + pb * frac
                du = pb - pa
                du = du / max(1e-4, float(np.linalg.norm(du)))
                rr = (ra * (1.0 - frac) + rb * frac) * 1.15
                add_chain_rod(out_list, pm - du * 0.20, pm + du * 0.20, rr, rr * 0.96, 1, ring_mat, overlap=0.05, sub_v_span=(t0, t1))


def add_membrane_bay_3d(out_list: list, top_a, top_b, bot_a, bot_b, strips: int,
                        mat: str, u_span=(0.01, 0.99), v_span=(0.01, 0.99),
                        thick: float = 0.24, pleat_amp: float = 0.0,
                        pleat_top_amp: float | None = None, pleat_freq: float = 5.0) -> None:
    """
    Xếp dải màng cánh dơi / vải áo choàng 3D PHẲNG LIỀN MẠCH 100% theo ma trận trực chuẩn chính xác
    (căn đúng cả yaw xoay ngang lẫn pitch/roll nghiêng 3D -> 0 bậc thang, 0 hở khe ở mọi góc!).
    """
    ta = np.array(top_a, dtype=np.float64)
    tb = np.array(top_b, dtype=np.float64)
    ba = np.array(bot_a, dtype=np.float64)
    bb = np.array(bot_b, dtype=np.float64)
    top_amp = (pleat_amp * 0.30) if pleat_top_amp is None else pleat_top_amp

    for i in range(strips):
        f0 = i / float(strips)
        f1 = (i + 1) / float(strips)
        fm = 0.5 * (f0 + f1)
        p_t0 = ta + (tb - ta) * f0
        p_t1 = ta + (tb - ta) * f1
        p_b0 = ba + (bb - ba) * f0
        p_b1 = ba + (bb - ba) * f1

        pt_top = 0.5 * (p_t0 + p_t1)
        pt_bot = 0.5 * (p_b0 + p_b1)
        if pleat_amp > 1e-4 or top_amp > 1e-4:
            # Gợn nếp dọc 3D đồng pha liên tục giữa 3 tầng áo choàng (không tạo bậc ngang!)
            wave = math.sin(fm * math.pi * pleat_freq)
            pt_bot = pt_bot + np.array([0.0, 0.0, wave * pleat_amp])
            pt_top = pt_top + np.array([0.0, 0.0, wave * top_amp])

        vec_down = pt_bot - pt_top
        h_strip = float(np.linalg.norm(vec_down))
        if h_strip < 0.4:
            continue
        ey = -vec_down / h_strip  # Trục +Y cục bộ hướng từ dưới lên trên

        # Trục +X cục bộ chạy dọc song song chính xác với đường nối 2 nan cánh / mép áo choàng!
        chord = 0.5 * ((p_t1 - p_t0) + (p_b1 - p_b0))
        ex = chord - np.dot(chord, ey) * ey
        ex_len = float(np.linalg.norm(ex))
        if ex_len < 1e-4:
            continue
        ex = ex / ex_len
        ez = np.cross(ex, ey)
        ez_len = float(np.linalg.norm(ez))
        if ez_len < 1e-4:
            continue
        ez = ez / ez_len
        ex = np.cross(ey, ez)  # Khóa trực chuẩn thuận tay phải

        R = np.column_stack([ex, ey, ez])
        rot = matrix_to_bedrock_euler(R)

        span_top = float(np.linalg.norm(p_t1 - p_t0))
        span_bot = float(np.linalg.norm(p_b1 - p_b0))
        w_strip = max(span_top, span_bot) * 1.18
        mid = 0.5 * (pt_top + pt_bot)
        su0 = u_span[0] + (u_span[1] - u_span[0]) * f0
        su1 = u_span[0] + (u_span[1] - u_span[0]) * f1
        out_list.append(make_cube(
            [mid[0] - w_strip * 0.5, mid[1] - (h_strip + 0.45) * 0.5, mid[2] - thick * 0.5],
            [w_strip, h_strip + 0.45, thick],
            mat,
            rot=rot,
            pivot=mid.tolist(),
            pad=0,
            sub_u=(su0, su1),
            sub_v=v_span,
            edge_transparent=True,
        ))


# =============================================================================
# 1. XÂY DỰNG TOÀN BỘ HÌNH HỌC NIGHTMARE KNIGHT v3 (1.200–1.700 CUBES, 50 BONES)
#    MỌI BONE QUAN TRỌNG ĐỀU CÓ BIND ROTATION = [0, 0, 0] -> 0% LỖI CỘNG DỒN GÓC!
# =============================================================================
def build_nightmare_geo() -> tuple[dict, int, dict]:
    bones = []

    bones.append({"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})
    bones.append({"name": "body", "parent": "root", "pivot": [0.0, 21.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})

    # -------------------------------------------------------------------------
    # A. HÔNG / KHUNG CHẬU (`pelvis`, `tail`, `tabard`) — EO CHỮ V THON + GIÁP VÁY GOTHIC
    # -------------------------------------------------------------------------
    pelvis_cubes = [
        make_cube([-6.0, 19.2, -2.5], [12.0, 3.6, 5.0], "ARMOR_STEEL"),
        make_cube([-4.2, 18.0, -2.1], [8.4, 1.8, 4.2], "ARMOR_STEEL"),
        make_cube([-2.4, 17.0, -1.7], [4.8, 1.4, 3.4], "ARMOR_SPIKE"),
        # Khóa đai lưng trung tâm chữ V nhiều tầng sắc cạnh
        make_cube([-3.5, 21.0, -3.20], [7.0, 1.5, 1.35], "ARMOR_SPIKE"),
        make_cube([-2.4, 19.3, -3.45], [4.8, 1.9, 1.35], "ARMOR_SPIKE", rot=[10, 0, 0]),
        make_cube([-1.4, 17.7, -3.60], [2.8, 1.8, 1.20], "ARMOR_SPIKE", rot=[15, 0, 0]),
        make_cube([-0.7, 16.5, -3.60], [1.4, 1.4, 0.95], "ARMOR_SPIKE", rot=[18, 0, 0]),
        make_cube([-0.18, 19.2, -3.78], [0.36, 1.9, 0.34], "RUNE_GLOW"),
        # Tấm giáp lưng dưới
        make_cube([-3.5, 19.4, 2.1], [7.0, 3.0, 1.25], "ARMOR_STEEL", rot=[-8, 0, 0]),
        make_cube([-2.3, 17.6, 2.2], [4.6, 2.0, 1.15], "ARMOR_SPIKE", rot=[-12, 0, 0]),
    ]
    pelvis_side = [
        make_cube([2.2, 20.8, -3.25], [3.1, 1.35, 1.65], "ARMOR_SPIKE", rot=[4, -18, 24]),
        make_cube([3.3, 19.5, -2.85], [2.8, 2.5, 5.6], "ARMOR_STEEL", rot=[0, -6, -16]),
        make_cube([4.4, 17.6, -2.55], [2.5, 2.5, 5.0], "ARMOR_STEEL", rot=[0, -6, -24]),
        make_cube([5.2, 15.8, -2.15], [2.2, 2.4, 4.2], "ARMOR_SPIKE", rot=[0, -6, -30]),
        make_cube([5.7, 14.2, -1.70], [1.7, 2.0, 3.2], "ARMOR_SPIKE", rot=[0, -6, -34]),
        make_cube([1.6, 19.2, -3.40], [2.7, 1.4, 1.0], "ARMOR_SPIKE", rot=[8, -22, 28]),
        make_cube([2.4, 17.6, -3.30], [2.4, 1.4, 1.0], "ARMOR_STEEL", rot=[10, -24, 24]),
        make_cube([3.5, 19.6, 1.85], [2.4, 2.5, 1.7], "ARMOR_STEEL", rot=[14, 18, -18]),
    ]
    for c in pelvis_side:
        pelvis_cubes.append(c)
        pelvis_cubes.append(mirror_x_cube(c))
    for sx in (1.0, -1.0):
        add_claw_spike(pelvis_cubes, [sx * 5.0, 21.4, -1.6], [sx * 7.4, 23.2, -2.2], 1.12, "ARMOR_SPIKE", 3)
        add_claw_spike(pelvis_cubes, [sx * 5.7, 18.8, -0.2], [sx * 8.0, 19.8, 0.2], 1.02, "ARMOR_SPIKE", 3)
        add_claw_spike(pelvis_cubes, [sx * 6.2, 16.2, 0.8], [sx * 8.0, 16.4, 1.4], 0.86, "ARMOR_SPIKE", 2)

    bones.append({"name": "pelvis", "parent": "body", "pivot": [0.0, 21.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": pelvis_cubes})

    tail_cubes = []
    add_chain_rod(tail_cubes, [0.0, 20.2, 2.2], [0.0, 16.8, 6.2], 1.70, 0.62, 5, "ARMOR_STEEL")
    add_claw_spike(tail_cubes, [0.0, 17.0, 5.8], [0.0, 15.2, 8.4], 0.82, "ARMOR_SPIKE", 3)
    bones.append({"name": "tail", "parent": "pelvis", "pivot": [0.0, 20.2, 2.4], "rotation": [0.0, 0.0, 0.0], "cubes": tail_cubes})

    # KHỐ VẢI ĐỎ RÁCH GIỮA HAI ĐÙI (`tabard` — rủ mềm giữa khe hở rộng của 2 đùi chữ Z, không xuyên đùi!)
    tabard_cubes = []
    add_membrane_bay_3d(tabard_cubes, [-2.2, 19.5, -3.55], [2.2, 19.5, -3.55],
                        [-2.5, 6.2, -4.45], [2.5, 6.2, -4.45], 6, "CLOTH_TATTER", (0.04, 0.96), (0.02, 0.98), 0.26, pleat_amp=0.35)
    add_membrane_bay_3d(tabard_cubes, [-2.3, 19.7, -3.80], [2.3, 19.7, -3.80],
                        [-2.4, 13.8, -4.15], [2.4, 13.8, -4.15], 4, "CLOTH_TATTER", (0.10, 0.90), (0.02, 0.48), 0.24, pleat_amp=0.20)
    bones.append({"name": "tabard", "parent": "pelvis", "pivot": [0.0, 19.6, -3.5], "rotation": [0.0, 0.0, 0.0], "cubes": tabard_cubes})

    # -------------------------------------------------------------------------
    # B. EO XƯƠNG SƯỜN QUỶ (`torso` — EO THON CHỮ V LỘ 4 CẶP XƯƠNG SƯỜN XÁM NGÀ + CỘT SỐNG ĐỐT RỜI)
    # -------------------------------------------------------------------------
    torso_cubes = [
        make_cube([-2.2, 22.0, -1.7], [4.4, 6.6, 3.6], "FLESH_DARK"),
        make_cube([-1.6, 26.2, -2.78], [3.2, 2.0, 1.3], "ARMOR_STEEL", rot=[10, 0, 0]),
        make_cube([-1.3, 24.4, -2.88], [2.6, 2.0, 1.2], "ARMOR_SPIKE", rot=[8, 0, 0]),
        make_cube([-1.05, 22.6, -2.82], [2.1, 2.0, 1.1], "ARMOR_STEEL", rot=[6, 0, 0]),
        make_cube([-0.15, 23.2, -3.05], [0.30, 4.4, 0.30], "RUNE_GLOW"),
    ]
    for vi in range(6):
        vy = 22.4 + vi * 1.05
        torso_cubes.append(make_cube([-1.15, vy, 1.35], [2.3, 0.85, 1.65], "ARMOR_STEEL"))
        add_claw_spike(torso_cubes, [0.0, vy + 0.35, 2.5], [0.0, vy + 1.1, 4.3], 0.78, "ARMOR_SPIKE", 2)

    rib_rows = [
        (27.3, 26.6, 5.15, 0.86),
        (25.9, 25.2, 4.65, 0.82),
        (24.5, 23.9, 4.05, 0.76),
        (23.1, 22.6, 3.40, 0.70),
    ]
    for yb, yf, wout, rth in rib_rows:
        for sx in (1.0, -1.0):
            p0 = [sx * 1.10, yb, 1.55]
            p1 = [sx * wout, (yb + yf) * 0.5 + 0.15, 0.25]
            p2 = [sx * (wout * 0.86), yf + 0.10, -1.85]
            p3 = [sx * 1.08, yf - 0.18, -2.55]
            add_chain_rod(torso_cubes, p0, p1, rth, rth * 0.96, 3, "BONE_RIB", overlap=0.20)
            add_chain_rod(torso_cubes, p1, p2, rth * 0.96, rth * 0.88, 3, "BONE_RIB", overlap=0.20)
            add_chain_rod(torso_cubes, p2, p3, rth * 0.88, rth * 0.72, 3, "BONE_RIB", overlap=0.18)
            torso_cubes.append(make_cube([sx * (wout * 0.84) - 0.4, yf - 0.35, -2.2], [0.8, 0.75, 0.8], "BONE_RIB"))

    bones.append({"name": "torso", "parent": "pelvis", "pivot": [0.0, 22.5, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": torso_cubes})

    # -------------------------------------------------------------------------
    # C. NGỰC GIÁP THÉP ĐÁNH BÓNG CHỮ V NHIỀU TẦNG, 2 KHÓA KHIÊN RUNE ĐỎ (`chest`, `spikes_back`)
    # -------------------------------------------------------------------------
    chest_cubes = [
        make_cube([-5.2, 27.8, -2.8], [10.4, 3.2, 5.6], "ARMOR_STEEL"),
        make_cube([-6.8, 30.6, -3.1], [13.6, 4.0, 6.2], "ARMOR_STEEL"),
        make_cube([-5.0, 34.0, -2.5], [10.0, 1.5, 5.0], "ARMOR_STEEL"),
        make_cube([-1.18, 27.4, -4.40], [2.36, 6.9, 1.25], "ARMOR_SPIKE"),
        make_cube([-0.16, 28.2, -4.70], [0.32, 5.2, 0.30], "RUNE_GLOW"),
        make_cube([-2.3, 34.4, -1.9], [4.6, 2.5, 4.0], "FLESH_DARK"),
        make_cube([-3.3, 34.2, -2.95], [6.6, 1.55, 5.7], "ARMOR_SPIKE", rot=[8, 0, 0]),
    ]
    for tier_i, (ty, tw, tz, tilt_z) in enumerate((
        (27.5, 3.6, -3.65, -20),
        (28.9, 4.7, -3.95, -18),
        (30.3, 5.6, -4.20, -16),
        (31.7, 6.3, -4.30, -13),
        (33.1, 6.5, -4.10, -8),
    )):
        mat_t = "ARMOR_SPIKE" if tier_i % 2 == 0 else "ARMOR_STEEL"
        pl = make_cube([0.5, ty, tz], [tw, 1.55, 1.38], mat_t, rot=[8, -12, tilt_z], pivot=[0.5, ty, tz])
        chest_cubes.append(pl)
        chest_cubes.append(mirror_x_cube(pl))

    chest_side = [
        make_cube([4.4, 29.4, -3.4], [3.8, 4.8, 6.8], "ARMOR_STEEL", rot=[0, -10, -18]),
        make_cube([5.8, 32.8, -3.2], [3.6, 2.4, 6.2], "ARMOR_SPIKE", rot=[0, -8, -22]),
        make_cube([2.4, 29.4, 2.4], [4.5, 5.2, 1.5], "ARMOR_STEEL", rot=[-8, 12, -14]),
        # Cổ giáp gothic cao 2 tầng ôm gáy
        make_cube([2.6, 34.4, -1.4], [2.1, 3.4, 4.0], "ARMOR_SPIKE", rot=[-14, -18, 22]),
        make_cube([3.3, 35.2, 0.2], [1.6, 3.1, 2.8], "ARMOR_STEEL", rot=[-20, -24, 26]),
        # 20–30% TWIST: Khóa giáp hình KHIÊN GAI THÉP có khe chữ thập rune đỏ sắc nét!
        make_cube([3.25, 31.9, -5.30], [2.65, 2.65, 1.15], "ARMOR_SPIKE", rot=[4, -14, 45]),
        make_cube([3.65, 32.3, -5.62], [1.85, 1.85, 0.65], "ARMOR_STEEL", rot=[4, -14, 45]),
        make_cube([4.38, 32.15, -5.90], [0.40, 2.15, 0.34], "RUNE_GLOW", rot=[4, -14, 0]),
        make_cube([3.68, 33.02, -5.90], [1.82, 0.38, 0.34], "RUNE_GLOW", rot=[4, -14, 0]),
        make_cube([1.5, 33.6, -4.40], [3.5, 0.90, 0.95], "ARMOR_SPIKE", rot=[0, -10, -24]),
        make_cube([1.5, 34.5, -2.35], [1.1, 2.7, 1.0], "FLESH_DARK", rot=[10, -16, -18]),
    ]
    for c in chest_side:
        chest_cubes.append(c)
        chest_cubes.append(mirror_x_cube(c))

    for sx in (1.0, -1.0):
        cx_s, cy_s, cz_s = sx * 4.55, 33.2, -5.45
        add_claw_spike(chest_cubes, [cx_s, cy_s + 0.9, cz_s], [cx_s + sx * 1.1, cy_s + 2.3, cz_s - 0.6], 0.62, "ARMOR_SPIKE", 2)
        add_claw_spike(chest_cubes, [cx_s + sx * 1.0, cy_s, cz_s], [cx_s + sx * 2.3, cy_s + 0.5, cz_s - 0.5], 0.58, "ARMOR_SPIKE", 2)
        add_claw_spike(chest_cubes, [cx_s, cy_s - 0.9, cz_s], [cx_s + sx * 0.7, cy_s - 2.1, cz_s - 0.5], 0.52, "ARMOR_SPIKE", 2)
        add_claw_spike(chest_cubes, [sx * 3.8, 36.2, 1.2], [sx * 5.8, 39.2, 2.4], 0.95, "ARMOR_SPIKE", 3)
        add_claw_spike(chest_cubes, [sx * 4.4, 34.8, -0.6], [sx * 6.4, 37.2, -1.2], 0.85, "ARMOR_SPIKE", 2)

    bones.append({"name": "chest", "parent": "torso", "pivot": [0.0, 28.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": chest_cubes})

    spikes_back_cubes = []
    for y_sp, z_sp, len_sp in ((34.5, 3.0, 3.2), (32.4, 3.2, 3.6), (30.2, 3.0, 3.0), (28.2, 2.8, 2.5)):
        add_claw_spike(spikes_back_cubes, [0.0, y_sp, z_sp], [0.0, y_sp + 1.4, z_sp + len_sp], 1.10, "ARMOR_SPIKE", 3)
        for sx in (1.0, -1.0):
            add_claw_spike(spikes_back_cubes, [sx * 2.2, y_sp, z_sp], [sx * 3.8, y_sp + 1.0, z_sp + len_sp * 0.80], 0.82, "ARMOR_SPIKE", 2)
    bones.append({"name": "spikes_back", "parent": "chest", "pivot": [0.0, 31.5, 3.2], "rotation": [0.0, 0.0, 0.0], "cubes": spikes_back_cubes})

    # -------------------------------------------------------------------------
    # C2. ÁO CHOÀNG ĐỎ CHỮ A SIÊU RỘNG & DÀI 3 TẦNG + 2 VẠT HÔNG (`cape` -> `cape_upper` -> `cape_mid` -> `cape_lower`, `cape_l`, `cape_r`)
    #     Mép dưới xòe rộng ~28.8 px (~1.95x hông!), kéo dài sát gót (y = 2.0 px), buông chéo sau lưng đúng góc mẫu!
    # -------------------------------------------------------------------------
    bones.append({"name": "cape", "parent": "chest", "pivot": [0.0, 34.2, 3.5], "rotation": [0.0, 0.0, 0.0], "cubes": []})

    cape_up_c = []
    # Cổ choàng thép ôm sau gáy giữ áo choàng + 2 khóa vai sau
    cape_up_c.append(make_cube([-4.8, 33.4, 2.8], [9.6, 1.8, 1.6], "ARMOR_SPIKE", rot=[-10, 0, 0]))
    for sx in (1.0, -1.0):
        cape_up_c.append(make_cube([sx * 4.2 - 1.0, 32.8, 3.0], [2.0, 2.4, 1.2], "ARMOR_SPIKE", rot=[-12, sx * 14, sx * -16]))
    add_membrane_bay_3d(cape_up_c, [-5.2, 34.0, 3.6], [5.2, 34.0, 3.6],
                        [-8.6, 23.6, 7.0], [8.6, 23.6, 7.0], 14, "CAPE_TATTER", (0.14, 0.86), (0.02, 0.34), 0.28,
                        pleat_amp=0.52, pleat_top_amp=0.14, pleat_freq=5.0)
    bones.append({"name": "cape_upper", "parent": "cape", "pivot": [0.0, 34.0, 3.6], "rotation": [0.0, 0.0, 0.0], "cubes": cape_up_c})

    cape_mid_c = []
    add_membrane_bay_3d(cape_mid_c, [-8.6, 23.8, 6.95], [8.6, 23.8, 6.95],
                        [-12.2, 12.8, 11.6], [12.2, 12.8, 11.6], 14, "CAPE_TATTER", (0.08, 0.92), (0.32, 0.66), 0.28,
                        pleat_amp=0.72, pleat_top_amp=0.52, pleat_freq=5.0)
    bones.append({"name": "cape_mid", "parent": "cape_upper", "pivot": [0.0, 23.8, 6.95], "rotation": [0.0, 0.0, 0.0], "cubes": cape_mid_c})

    cape_lo_c = []
    add_membrane_bay_3d(cape_lo_c, [-12.2, 13.0, 11.55], [12.2, 13.0, 11.55],
                        [-15.2, 2.0, 16.4], [15.2, 2.0, 16.4], 14, "CAPE_TATTER", (0.02, 0.98), (0.64, 0.99), 0.28,
                        pleat_amp=0.90, pleat_top_amp=0.72, pleat_freq=5.0)
    bones.append({"name": "cape_lower", "parent": "cape_mid", "pivot": [0.0, 13.0, 11.55], "rotation": [0.0, 0.0, 0.0], "cubes": cape_lo_c})

    # 2 vạt trái/phải xòe rộng lấp ló hai bên đùi chữ Z & phất phơ độc lập (`cape_l`, `cape_r`)
    cape_l_c = []
    add_membrane_bay_3d(cape_l_c, [4.8, 33.4, 3.55], [7.8, 31.6, 3.25],
                        [10.8, 14.8, 10.2], [14.4, 16.0, 7.8], 7, "CAPE_TATTER", (0.60, 0.98), (0.04, 0.64), 0.26,
                        pleat_amp=0.45, pleat_top_amp=0.14, pleat_freq=3.0)
    add_membrane_bay_3d(cape_l_c, [10.8, 15.0, 10.15], [14.4, 16.2, 7.75],
                        [13.8, 3.2, 15.4], [17.6, 5.2, 11.6], 7, "CAPE_TATTER", (0.58, 0.99), (0.62, 0.99), 0.26,
                        pleat_amp=0.60, pleat_top_amp=0.45, pleat_freq=3.0)
    cape_r_c = [mirror_x_cube(c) for c in cape_l_c]
    bones.append({"name": "cape_l", "parent": "cape_upper", "pivot": [5.4, 33.0, 3.4], "rotation": [0.0, 0.0, 0.0], "cubes": cape_l_c})
    bones.append({"name": "cape_r", "parent": "cape_upper", "pivot": [-5.4, 33.0, 3.4], "rotation": [0.0, 0.0, 0.0], "cubes": cape_r_c})

    # -------------------------------------------------------------------------
    # D. ĐẦU SỌ XƯƠNG XÁM NGÀ NỔI BẬT PHÓNG ~1.15x + BÁN MẶT NẠ THÉP + SỪNG ĐỎ DÀY CONG VÒNG!
    #    (`head`, `jaw`, `mandible_l/r`, `horn_l/r`) — Đỉnh mào ~42.4 px giữ chuẩn chiều cao ~19.8 block!
    # -------------------------------------------------------------------------
    hs = 1.14  # Tăng kích cỡ cục bộ cụm đầu/sừng ~1.14x giúp nhìn rõ khuôn mặt sọ & mắt đỏ từ xa!
    hy0 = 35.6

    def hbox(ox, oy, oz, sx, sy, sz, mat, rot=None):
        return make_cube([ox * hs, hy0 + oy * hs, oz * hs], [sx * hs, sy * hs, sz * hs], mat, rot=rot)

    head_cubes = [
        # Lõi sọ trong tối
        hbox(-2.4, 0.6, -2.4, 4.8, 4.1, 4.8, "ARMOR_STEEL"),
        # Mũ bán mặt nạ thép đen đánh bóng + vương miện góc cạnh + mào sống trán nhọn (20–30% twist!)
        hbox(-2.90, 2.75, -3.15, 5.8, 1.95, 5.5, "ARMOR_STEEL"),
        hbox(-2.05, 4.05, -3.05, 4.1, 1.30, 5.2, "ARMOR_SPIKE", rot=[-6, 0, 0]),
        hbox(-0.70, 2.95, -3.80, 1.40, 2.95, 2.1, "ARMOR_SPIKE", rot=[-14, 0, 0]),
        hbox(-0.55, 4.10, -2.05, 1.10, 2.00, 4.0, "ARMOR_SPIKE", rot=[-18, 0, 0]),
        hbox(-0.36, 4.65, -0.45, 0.72, 1.50, 3.1, "ARMOR_SPIKE", rot=[-24, 0, 0]),
        hbox(-0.28, 3.25, -4.02, 0.56, 1.80, 0.36, "RUNE_GLOW", rot=[-14, 0, 0]),
        # Mũi giáp thép nhọn che sống trán giữa 2 hốc mắt
        hbox(-0.62, 2.15, -3.72, 1.24, 1.25, 0.85, "ARMOR_SPIKE", rot=[14, 0, 0]),
        # KHUÔN MẶT SỌ XƯƠNG XÁM NGÀ SÁNG NỔI BẬT BÊN DƯỚI BÁN MẶT NẠ!
        hbox(-1.65, 0.65, -3.38, 3.30, 1.70, 1.35, "SKULL_BONE"),
        hbox(-0.68, 1.50, -3.55, 1.36, 1.25, 0.90, "SKULL_BONE"),
        hbox(-0.46, 1.22, -3.72, 0.92, 0.78, 0.32, "WING_BONE"),  # Hốc mũi tam giác sâu tối
        # 6 răng nanh xương ngà hàm trên sắc nét
        hbox(-1.46, 0.05, -3.40, 0.42, 0.96, 0.42, "SKULL_BONE"),
        hbox(-0.92, 0.22, -3.48, 0.38, 0.72, 0.40, "SKULL_BONE"),
        hbox(-0.42, 0.28, -3.50, 0.38, 0.66, 0.40, "SKULL_BONE"),
        hbox( 0.04, 0.28, -3.50, 0.38, 0.66, 0.40, "SKULL_BONE"),
        hbox( 0.54, 0.22, -3.48, 0.38, 0.72, 0.40, "SKULL_BONE"),
        hbox( 1.04, 0.05, -3.40, 0.42, 0.96, 0.42, "SKULL_BONE"),
        # Nắp gáy mũ giáp 2 tầng
        hbox(-2.45, 0.4, 1.55, 4.9, 4.1, 1.75, "ARMOR_STEEL", rot=[-10, 0, 0]),
        hbox(-1.85, -0.7, 2.05, 3.7, 2.2, 1.35, "ARMOR_SPIKE", rot=[-18, 0, 0]),
    ]
    head_side = [
        # Cung mày giáp thép vát xiên sắc lạnh
        hbox(0.62, 2.68, -3.78, 2.55, 0.85, 1.70, "ARMOR_SPIKE", rot=[6, -15, 18]),
        # Gò má xương sọ ngà sáng nổi khối + viền xương dưới hốc mắt!
        hbox(1.30, 1.05, -3.48, 1.65, 1.28, 1.65, "SKULL_BONE", rot=[0, -18, -10]),
        hbox(0.68, 1.25, -3.52, 1.45, 0.52, 0.65, "SKULL_BONE", rot=[0, -12, 8]),
        hbox(2.05, 1.25, -2.75, 1.45, 1.85, 2.65, "ARMOR_STEEL", rot=[0, -16, -12]),
        hbox(2.50, 0.95, -1.50, 1.15, 3.40, 3.90, "ARMOR_STEEL", rot=[0, -10, 8]),
        # Hốc mắt đen sâu + ĐÔI MẮT ĐỎ RỰC FULLBRIGHT NỔI RÕ!
        hbox(0.72, 1.82, -3.40, 1.75, 1.12, 0.68, "WING_BONE", rot=[0, -10, 14]),
        hbox(0.84, 1.94, -3.62, 1.50, 0.82, 0.48, "EYE_GLOW", rot=[0, -10, 14]),
        # Vây giáp tai nhọn kép
        hbox(2.85, 2.65, 0.15, 1.05, 2.30, 2.50, "ARMOR_SPIKE", rot=[-24, -22, 18]),
        hbox(2.95, 1.55, 0.60, 0.90, 1.75, 2.00, "ARMOR_STEEL", rot=[-18, -26, 24]),
    ]
    for c in head_side:
        head_cubes.append(c)
        head_cubes.append(mirror_x_cube(c))
    for sx in (1.0, -1.0):
        add_claw_spike(head_cubes, [sx * 2.5 * hs, hy0 + 1.4 * hs, -2.6 * hs],
                       [sx * 4.0 * hs, hy0 + 1.6 * hs, -3.0 * hs], 0.72 * hs, "SKULL_BONE", 2)
        add_claw_spike(head_cubes, [sx * 2.9 * hs, hy0 + 3.5 * hs, 0.6 * hs],
                       [sx * 4.4 * hs, hy0 + 5.2 * hs, 2.0 * hs], 0.78 * hs, "ARMOR_SPIKE", 2)

    bones.append({"name": "head", "parent": "chest", "pivot": [0.0, hy0 + 0.2, -0.4], "rotation": [0.0, 0.0, 0.0], "cubes": head_cubes})

    # Hàm dưới xương ngà há gầm rõ nét (`jaw`)
    jaw_cubes = [
        hbox(-1.68, -0.95, -3.35, 3.36, 1.18, 2.95, "SKULL_BONE"),
        hbox(-1.18, -1.48, -3.46, 2.36, 0.80, 1.95, "SKULL_BONE"),
        hbox(-0.70, -1.82, -3.38, 1.40, 0.56, 1.25, "SKULL_BONE"),
        hbox( 1.45, -0.58, -2.25, 0.82, 1.75, 2.30, "SKULL_BONE", rot=[-14, 0, 0]),
        hbox(-2.27, -0.58, -2.25, 0.82, 1.75, 2.30, "SKULL_BONE", rot=[-14, 0, 0]),
        # Răng hàm dưới ngà sáng
        hbox(-1.32, -0.10, -3.28, 0.42, 0.76, 0.42, "SKULL_BONE"),
        hbox(-0.70, -0.08, -3.34, 0.38, 0.60, 0.40, "SKULL_BONE"),
        hbox(-0.18, -0.06, -3.36, 0.38, 0.56, 0.40, "SKULL_BONE"),
        hbox( 0.34, -0.08, -3.34, 0.38, 0.60, 0.40, "SKULL_BONE"),
        hbox( 0.90, -0.10, -3.28, 0.42, 0.76, 0.42, "SKULL_BONE"),
    ]
    bones.append({"name": "jaw", "parent": "head", "pivot": [0.0, hy0 + 0.2, -0.5], "rotation": [0.0, 0.0, 0.0], "cubes": jaw_cubes})

    mand_l = []
    add_claw_spike(mand_l, [2.4 * hs, hy0 + 0.5 * hs, -1.8 * hs], [3.8 * hs, hy0 - 0.9 * hs, -3.8 * hs], 0.88 * hs, "ARMOR_SPIKE", 3)
    mand_r = [mirror_x_cube(c) for c in mand_l]
    bones.append({"name": "mandible_l", "parent": "head", "pivot": [2.4 * hs, hy0 + 0.5 * hs, -1.8 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": mand_l})
    bones.append({"name": "mandible_r", "parent": "head", "pivot": [-2.4 * hs, hy0 + 0.5 * hs, -1.8 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": mand_r})

    # 2 SỪNG QUỶ ĐỎ THẪM DÀY CÓ GÂN VÒNG NGANG UỐN CONG TO RA HAI BÊN RỒI CỤP VÒNG XUỐNG-SAU (`horn_l/r`)
    horn_l_pts = [
        [2.2 * hs, hy0 + 3.6 * hs, -0.8 * hs],
        [4.8 * hs, hy0 + 5.9 * hs, -0.9 * hs],
        [8.0 * hs, hy0 + 6.4 * hs, +0.2 * hs],
        [10.4 * hs, hy0 + 4.5 * hs, +1.5 * hs],
        [10.6 * hs, hy0 + 1.8 * hs, +1.4 * hs],
        [9.0 * hs, hy0 + 0.1 * hs, +0.1 * hs],
        [7.4 * hs, hy0 - 0.3 * hs, -1.4 * hs],
    ]
    horn_l_cubes = []
    add_curved_rod(horn_l_cubes, horn_l_pts, 2.75 * hs, 0.42 * hs, "HORN_CRIMSON", segs_per_span=4, rib_rings=True, ring_mat="HORN_CRIMSON")
    horn_r_cubes = [mirror_x_cube(c) for c in horn_l_cubes]
    bones.append({"name": "horn_l", "parent": "head", "pivot": [2.2 * hs, hy0 + 3.6 * hs, -0.8 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": horn_l_cubes})
    bones.append({"name": "horn_r", "parent": "head", "pivot": [-2.2 * hs, hy0 + 3.6 * hs, -0.8 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": horn_r_cubes})

    # -------------------------------------------------------------------------
    # E. ĐÔI CÁNH QUỶ XÒE NGANG RỘNG (±X) CHỈ HƠI VÁT SAU ~24° (`wing_l/r` -> `wing_*_upper/mid/tip`)
    #    - Xương cánh thanh mảnh sắc sảo có khớp lồi tự nhiên (bỏ cảm giác ống vuông dày!)
    #    - Đỉnh cánh vươn cao vượt đỉnh đầu kèm móng móc cong đỏ sẫm!
    #    - Bind rotation = [0, 0, 0], góc vát sau ~24° đúc thẳng vào tọa độ 3D -> nhìn ngang cực gọn thanh thoát!
    # -------------------------------------------------------------------------
    def build_wing_chain(side: float):
        tag = "l" if side > 0 else "r"
        s = side

        # Tọa độ thực 3D xòe mạnh theo ±X và chỉ vát sau ~24° (Z tăng nhẹ 3.2 -> 12.8 thay vì chĩa 90° ra sau!)
        p_root      = [s * 3.8,  32.6,  3.2]
        p_elbow     = [s * 10.6, 37.8,  6.2]
        p_apex      = [s * 18.2, 46.2,  8.8]    # Đỉnh cánh vươn cao hơn đỉnh đầu (~42.4) kèm móc vuốt đỏ!
        p_wrist     = [s * 26.8, 41.4, 11.0]    # Khớp cổ cánh tỏa 4 nan xương thanh mảnh
        p_outer_mid = [s * 33.4, 34.0, 12.2]
        p_outer_tip = [s * 37.2, 23.6, 13.0]    # Mũi cánh nhọn sắc

        # 4 nan xương cánh (phalanges) thanh mảnh với khớp đốt lồi tự nhiên
        p_rib0_top = [s * 16.4, 44.0,  8.2]
        p_rib0_end = [s * 12.8, 17.6,  6.8]
        p_rib1_top = [s * 19.6, 45.2,  9.2]
        p_rib1_end = [s * 19.2, 13.8,  8.6]
        p_rib2_top = [s * 23.2, 43.3, 10.1]
        p_rib2_end = [s * 25.8, 14.0, 10.4]
        p_rib3_top = p_wrist
        p_rib3_end = [s * 31.8, 17.0, 11.8]

        def lerp_pt(pa, pb, f):
            return [pa[0] + (pb[0] - pa[0]) * f, pa[1] + (pb[1] - pa[1]) * f, pa[2] + (pb[2] - pa[2]) * f]

        def add_knuckle(lst, pt, r):
            lst.append(make_cube([pt[0] - r, pt[1] - r, pt[2] - r], [r * 2.0, r * 2.0, r * 2.0], "WING_BONE"))
            lst.append(make_cube([pt[0] - r * 0.72, pt[1] - r * 0.72, pt[2] - r * 1.15], [r * 1.44, r * 1.44, r * 2.3], "ARMOR_SPIKE"))

        # 1. `wing_l/r` (khớp bả vai gốc — rotation = [0, 0, 0]!)
        w_root_cubes = [
            make_cube([p_root[0] - 1.2, p_root[1] - 1.2, p_root[2] - 1.1], [2.4, 2.4, 2.2], "WING_BONE"),
            make_cube([p_root[0] - 0.9, p_root[1] - 0.9, p_root[2] - 1.4], [1.8, 1.8, 0.9], "ARMOR_SPIKE"),
        ]

        # 2. `wing_*_upper`
        w_up_cubes = []
        add_chain_rod(w_up_cubes, p_root, p_elbow, 1.45, 1.22, 5, "WING_BONE", overlap=0.35)
        add_chain_rod(w_up_cubes, p_elbow, p_apex, 1.26, 1.08, 6, "WING_BONE", overlap=0.35)
        # Thanh xương giằng thanh mảnh nối bả vai vào nan 0
        add_chain_rod(w_up_cubes, [s * 4.6, 30.2, 3.6], lerp_pt(p_rib0_top, p_rib0_end, 0.42), 0.78, 0.56, 5, "WING_BONE", overlap=0.28)
        add_knuckle(w_up_cubes, p_elbow, 0.85)
        add_knuckle(w_up_cubes, p_apex, 0.98)
        # Móng móc cong đỏ sẫm vươn cao ở đỉnh cánh (đúng góc trước & sau mẫu!)
        apex_k1 = [p_apex[0] - s * 1.1, p_apex[1] + 2.6, p_apex[2] - 0.6]
        apex_k2 = [p_apex[0] - s * 3.4, p_apex[1] + 4.1, p_apex[2] - 1.6]
        add_chain_rod(w_up_cubes, [p_apex[0], p_apex[1] + 0.4, p_apex[2]], apex_k1, 0.95, 0.62, 3, "WING_BONE", overlap=0.24)
        add_claw_spike(w_up_cubes, apex_k1, apex_k2, 0.68, "CLAW_BLOOD", 3)
        # Nan xương 0 thanh mảnh có 2 khớp đốt lồi + móng vuốt đỏ
        add_chain_rod(w_up_cubes, p_rib0_top, p_rib0_end, 0.84, 0.34, 8, "WING_BONE", overlap=0.32)
        for frac in (0.34, 0.68):
            add_knuckle(w_up_cubes, lerp_pt(p_rib0_top, p_rib0_end, frac), 0.46)
        add_claw_spike(w_up_cubes, p_rib0_end, [p_rib0_end[0] - s * 0.4, p_rib0_end[1] - 2.3, p_rib0_end[2] - 0.5], 0.42, "CLAW_BLOOD", 2)

        # Khoang màng cánh 1 (căn phẳng 3D 100% dọc trục nghiêng cánh!)
        add_membrane_bay_3d(w_up_cubes,
                            [p_root[0] + s * 0.2, p_root[1] - 0.2, p_root[2] + 0.1],
                            [p_rib0_top[0], p_rib0_top[1] - 0.2, p_rib0_top[2]],
                            [s * 4.8, 21.0, 4.4],
                            lerp_pt(p_rib0_top, p_rib0_end, 0.95),
                            9, "WING_MEMBRANE", (0.01, 0.99), (0.01, 0.99), 0.22)

        # 3. `wing_*_mid`
        w_mid_cubes = []
        add_chain_rod(w_mid_cubes, p_apex, p_wrist, 1.15, 0.92, 6, "WING_BONE", overlap=0.35)
        add_knuckle(w_mid_cubes, p_wrist, 0.80)

        for r_top, r_end, th_a in (
            (p_rib1_top, p_rib1_end, 0.80),
            (p_rib2_top, p_rib2_end, 0.76),
            (p_rib3_top, p_rib3_end, 0.72),
        ):
            add_chain_rod(w_mid_cubes, r_top, r_end, th_a, 0.32, 8, "WING_BONE", overlap=0.32)
            for frac in (0.33, 0.67):
                add_knuckle(w_mid_cubes, lerp_pt(r_top, r_end, frac), 0.42)
            add_claw_spike(w_mid_cubes, r_end, [r_end[0] + s * 0.4, r_end[1] - 2.3, r_end[2] - 0.4], 0.40, "CLAW_BLOOD", 2)

        for (ta, ba, tb, bb, st) in (
            (p_rib0_top, p_rib0_end, p_rib1_top, p_rib1_end, 8),
            (p_rib1_top, p_rib1_end, p_rib2_top, p_rib2_end, 9),
            (p_rib2_top, p_rib2_end, p_rib3_top, p_rib3_end, 8),
        ):
            add_membrane_bay_3d(w_mid_cubes,
                                [ta[0], ta[1] - 0.2, ta[2]],
                                [tb[0], tb[1] - 0.2, tb[2]],
                                lerp_pt(ta, ba, 0.95),
                                lerp_pt(tb, bb, 0.95),
                                st, "WING_MEMBRANE", (0.01, 0.99), (0.01, 0.99), 0.22)

        # 4. `wing_*_tip`
        w_tip_cubes = []
        add_chain_rod(w_tip_cubes, p_wrist, p_outer_mid, 0.92, 0.68, 5, "WING_BONE", overlap=0.35)
        add_chain_rod(w_tip_cubes, p_outer_mid, p_outer_tip, 0.68, 0.34, 5, "WING_BONE", overlap=0.32)
        add_knuckle(w_tip_cubes, p_outer_mid, 0.56)
        add_claw_spike(w_tip_cubes, p_outer_tip, [p_outer_tip[0] + s * 1.8, p_outer_tip[1] - 3.0, p_outer_tip[2] - 0.3], 0.44, "CLAW_BLOOD", 2)

        add_membrane_bay_3d(w_tip_cubes,
                            [p_wrist[0], p_wrist[1] - 0.25, p_wrist[2]],
                            [p_outer_mid[0], p_outer_mid[1] - 0.25, p_outer_mid[2]],
                            lerp_pt(p_rib3_top, p_rib3_end, 0.95),
                            lerp_pt(p_rib3_end, p_outer_tip, 0.54),
                            7, "WING_MEMBRANE", (0.01, 0.56), (0.01, 0.99), 0.22)
        add_membrane_bay_3d(w_tip_cubes,
                            [p_outer_mid[0], p_outer_mid[1] - 0.25, p_outer_mid[2]],
                            [p_outer_tip[0] - s * 0.15, p_outer_tip[1] - 0.2, p_outer_tip[2]],
                            lerp_pt(p_rib3_end, p_outer_tip, 0.52),
                            [p_outer_tip[0] - s * 0.2, p_outer_tip[1] - 1.8, p_outer_tip[2]],
                            6, "WING_MEMBRANE", (0.54, 0.99), (0.01, 0.99), 0.22)

        bones.append({"name": f"wing_{tag}", "parent": "chest", "pivot": p_root, "rotation": [0.0, 0.0, 0.0], "cubes": w_root_cubes})
        bones.append({"name": f"wing_{tag}_upper", "parent": f"wing_{tag}", "pivot": p_root, "rotation": [0.0, 0.0, 0.0], "cubes": w_up_cubes})
        bones.append({"name": f"wing_{tag}_mid", "parent": f"wing_{tag}_upper", "pivot": p_apex, "rotation": [0.0, 0.0, 0.0], "cubes": w_mid_cubes})
        bones.append({"name": f"wing_{tag}_tip", "parent": f"wing_{tag}_mid", "pivot": p_wrist, "rotation": [0.0, 0.0, 0.0], "cubes": w_tip_cubes})

    build_wing_chain(+1.0)
    build_wing_chain(-1.0)

    # -------------------------------------------------------------------------
    # F. HAI CÁNH TAY — GIÁP VAI NHIỀU TẦNG PHÓNG NHẸ, LỘ BẮP TAY CƠ BẮP QUẤN BĂNG ĐỎ, GĂNG GAI SẮC & ĐẠI KIẾM CÂN ĐỐI!
    # -------------------------------------------------------------------------
    sword_tip_world = [0.0, 0.0, 0.0]

    def build_arm_chain(side: float):
        nonlocal sword_tip_world
        tag = "l" if side > 0 else "r"
        s = side
        sh_x = s * 8.6
        sh_y = 33.0
        el_x = sh_x + s * 2.2
        el_y = sh_y - 8.8
        ha_x = el_x + s * 1.1
        ha_y = el_y - 7.4

        bones.append({"name": f"arm_{tag}", "parent": "chest", "pivot": [sh_x, sh_y, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})

        # `arm_*_upper`: Giáp vai 5 tầng gothic ánh kim phóng nhẹ + lộ rõ bắp tay cơ bắp tối màu quấn băng đỏ!
        up_cubes = [
            make_cube([sh_x - 2.3 + s * 1.0, sh_y + 1.2, -3.0], [4.9, 1.7, 6.0], "ARMOR_SPIKE", rot=[0, 0, s * -8]),
            make_cube([sh_x - 2.7 + s * 0.8, sh_y - 0.5, -3.5], [5.7, 2.2, 7.0], "ARMOR_STEEL", rot=[0, 0, s * -16]),
            make_cube([sh_x - 2.4 + s * 1.4, sh_y - 2.1, -3.3], [5.4, 2.1, 6.6], "ARMOR_STEEL", rot=[0, 0, s * -24]),
            make_cube([sh_x - 2.0 + s * 1.9, sh_y - 3.5, -2.9], [4.8, 1.8, 5.8], "ARMOR_SPIKE", rot=[0, 0, s * -30]),
            make_cube([sh_x - 1.0 + s * 1.7, sh_y - 3.4, -0.8], [2.2, 6.0, 1.6], "ARMOR_SPIKE", rot=[0, 0, s * -24]),
        ]
        # Lộ rõ bắp tay cơ bắp nâu-xám tối (độ tương phản mạnh với thép đen!) + 2 vòng băng vải đỏ sẫm
        add_chain_rod(up_cubes, [sh_x + s * 0.9, sh_y - 2.6, 0.0], [el_x, el_y + 0.4, 0.0], 3.35, 3.05, 4, "FLESH_DARK", overlap=0.35)
        for frac, bw in ((0.46, 0.95), (0.66, 0.85)):
            px = (sh_x + s * 0.9) * (1.0 - frac) + el_x * frac
            py = (sh_y - 2.6) * (1.0 - frac) + (el_y + 0.4) * frac
            up_cubes.append(make_cube([px - 1.82, py - bw * 0.5, -1.85], [3.64, bw, 3.70], "CLOTH_TATTER",
                                      rot=[0, 0, s * -13], sub_v=(0.18, 0.48)))
        # 5 gai giáp vai hướng nhọn vát lên trên đầy uy lực + 3 lớp sống vảy vai thép nổi khối!
        for k in range(3):
            up_cubes.append(make_cube([sh_x - 1.2 + s * (1.2 + k * 0.6), sh_y + 0.6 - k * 1.9, -3.5 + k * 0.2],
                                      [2.6, 1.3, 1.4], "ARMOR_SPIKE", rot=[10, s * -16, s * (-14 - k * 7)]))
            up_cubes.append(make_cube([sh_x - 1.2 + s * (1.2 + k * 0.6), sh_y + 0.6 - k * 1.9, +2.1 - k * 0.2],
                                      [2.6, 1.3, 1.4], "ARMOR_SPIKE", rot=[-10, s * 16, s * (-14 - k * 7)]))
        add_claw_spike(up_cubes, [sh_x + s * 1.6, sh_y + 2.3, -1.0], [sh_x + s * 4.5, sh_y + 6.0, -1.8], 1.22, "ARMOR_SPIKE", 3)
        add_claw_spike(up_cubes, [sh_x + s * 2.5, sh_y + 1.4, 0.8],  [sh_x + s * 6.0, sh_y + 4.2, 1.6],  1.12, "ARMOR_SPIKE", 3)
        add_claw_spike(up_cubes, [sh_x + s * 3.0, sh_y - 0.5, -1.6], [sh_x + s * 6.2, sh_y + 1.2, -2.4], 0.98, "ARMOR_SPIKE", 3)
        add_claw_spike(up_cubes, [sh_x + s * 3.2, sh_y - 2.2, 0.2],  [sh_x + s * 6.0, sh_y - 1.0, 0.6],  0.90, "ARMOR_SPIKE", 2)
        add_claw_spike(up_cubes, [sh_x + s * 3.0, sh_y - 3.8, 1.2],  [sh_x + s * 5.4, sh_y - 3.2, 2.0],  0.80, "ARMOR_SPIKE", 2)

        bones.append({"name": f"arm_{tag}_upper", "parent": f"arm_{tag}", "pivot": [sh_x, sh_y, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": up_cubes})

        # `arm_*_lower`: Găng giáp thép nhiều tầng vát nhọn + 6 gai sắc dọc cẳng tay!
        lo_cubes = []
        add_chain_rod(lo_cubes, [el_x, el_y + 0.4, 0.0], [ha_x, ha_y, 0.0], 3.6, 3.3, 4, "ARMOR_STEEL", overlap=0.35)
        for k, (f_pos, rw, mat_g) in enumerate(((0.18, 4.3, "ARMOR_SPIKE"), (0.48, 4.1, "ARMOR_STEEL"), (0.78, 3.8, "ARMOR_SPIKE"))):
            gx = el_x * (1.0 - f_pos) + ha_x * f_pos
            gy = el_y * (1.0 - f_pos) + ha_y * f_pos
            lo_cubes.append(make_cube([gx - rw * 0.5 + s * 0.25, gy - 1.2, -rw * 0.52], [rw, 2.4, rw * 1.04], mat_g,
                                      rot=[0, 0, s * (-14 + k * 3)]))
        lo_cubes.append(make_cube([el_x + s * 1.45, el_y - 1.0, -1.2], [1.1, 3.6, 2.4], "ARMOR_SPIKE", rot=[0, 0, s * 18]))
        lo_cubes.append(make_cube([ha_x + s * 1.75, ha_y + 1.0, -0.4], [0.35, 2.6, 0.72], "RUNE_GLOW"))
        add_claw_spike(lo_cubes, [el_x + s * 1.6, el_y - 0.2, 0.8],  [el_x + s * 4.8, el_y + 2.4, 2.2],  1.02, "ARMOR_SPIKE", 3)
        add_claw_spike(lo_cubes, [el_x + s * 1.8, el_y - 1.9, -0.8], [el_x + s * 4.2, el_y - 0.6, -1.5], 0.84, "ARMOR_SPIKE", 2)
        add_claw_spike(lo_cubes, [el_x + s * 1.9, el_y - 3.3, 0.6],  [el_x + s * 4.3, el_y - 2.0, 1.4],  0.82, "ARMOR_SPIKE", 2)
        add_claw_spike(lo_cubes, [el_x + s * 2.1, el_y - 4.8, -0.6], [el_x + s * 4.1, el_y - 3.8, -1.0], 0.76, "ARMOR_SPIKE", 2)
        add_claw_spike(lo_cubes, [el_x + s * 2.2, el_y - 6.1, 0.5],  [el_x + s * 3.9, el_y - 5.2, 1.0],  0.68, "ARMOR_SPIKE", 2)

        bones.append({"name": f"arm_{tag}_lower", "parent": f"arm_{tag}_upper", "pivot": [el_x, el_y, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": lo_cubes})

        # `hand_l/r`: Bàn tay bọc giáp tăng nhẹ kích cỡ (~1.12x) + 4 ngón vuốt sắc + ngón cái!
        ha_cubes = [
            make_cube([ha_x - 1.62, ha_y - 2.4, -1.72], [3.24, 2.6, 3.44], "ARMOR_STEEL"),
            make_cube([ha_x - 1.38, ha_y - 2.7, -1.48], [2.76, 1.5, 2.96], "FLESH_DARK"),
            make_cube([ha_x + s * 1.32, ha_y - 2.3, -1.38], [0.60, 1.8, 2.76], "ARMOR_SPIKE"),
        ]
        for fz in (-1.22, -0.40, 0.40, 1.18):
            curl = 0.62 if s < 0 else 0.25
            p0 = [ha_x + s * 0.60, ha_y - 2.3, fz]
            p1 = [ha_x + s * (0.32 - curl * 0.72), ha_y - 3.6, fz - 0.08]
            p2 = [ha_x + s * (0.05 - curl * 1.15), ha_y - 4.6, fz - 0.14]
            p3 = [ha_x - s * (0.50 + curl * 0.62), ha_y - 5.5, fz - 0.20]
            add_chain_rod(ha_cubes, p0, p1, 0.68, 0.56, 2, "FLESH_DARK", overlap=0.14)
            add_chain_rod(ha_cubes, p1, p2, 0.56, 0.46, 2, "ARMOR_SPIKE", overlap=0.14)
            add_claw_spike(ha_cubes, p2, p3, 0.46, "CLAW_BLOOD", 2)
        add_chain_rod(ha_cubes, [ha_x - s * 1.05, ha_y - 1.5, -1.4], [ha_x - s * 1.75, ha_y - 3.3, -2.1], 0.68, 0.36, 3, "CLAW_BLOOD", overlap=0.14)

        bones.append({"name": f"hand_{tag}", "parent": f"arm_{tag}_lower", "pivot": [ha_x, ha_y, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": ha_cubes})

        # ---------------------------------------------------------------------
        # ĐẠI KIẾM QUỶ RĂNG CƯA CÂN ĐỐI (~1.08x chiều cao thân = 45.2 px tổng chuôi->mũi!)
        # Góc cầm chéo tự nhiên xuống trước–phải đúc trong hệ cục bộ, mũi kiếm lơ lửng an toàn ~2.2 px trên đất!
        # ---------------------------------------------------------------------
        if s < 0:
            wp_x, wp_y, wp_z = ha_x - 0.2, ha_y - 2.8, -0.6
            # Định nghĩa kiếm trong hệ cục bộ rồi đặt thẳng vào hướng cầm chéo tự nhiên (pitch=+19.5° xuống, yaw=+12° ra ngoài, roll=-8°!)
            # => mũi kiếm lơ lửng an toàn ~2.0 px trên mặt đất (KHÔNG cắm xuyên nền!), tổng dài 45.2 px (= 1.08x chiều cao thân!)
            R_sw = bedrock_rot_matrix([19.5, 12.0, -8.0])
            sw_o = np.array([wp_x, wp_y, wp_z], dtype=np.float64)

            def sw_pt(lx, ly, lz):
                return (sw_o + R_sw @ np.array([lx, ly, lz], dtype=np.float64)).tolist()

            def sw_box(lx, ly, lz, sx, sy, sz, mat, extra_rot=(0.0, 0.0, 0.0)):
                lc = np.array([lx + sx * 0.5, ly + sy * 0.5, lz + sz * 0.5], dtype=np.float64)
                wc = sw_o + R_sw @ lc
                R_tot = R_sw @ bedrock_rot_matrix(extra_rot)
                rot = matrix_to_bedrock_euler(R_tot)
                return make_cube([wc[0] - sx * 0.5, wc[1] - sy * 0.5, wc[2] - sz * 0.5], [sx, sy, sz], mat, rot=rot, pivot=wc.tolist())

            w_cubes = [
                # Cán kiếm đen quấn gân + chuôi gai + tinh thể đỏ phát sáng
                sw_box(-0.68, -0.68, -2.6, 1.36, 1.36, 10.6, "WING_BONE"),
                sw_box(-0.88, -0.88, +1.0, 1.76, 1.76, 1.0, "ARMOR_SPIKE"),
                sw_box(-0.88, -0.88, +4.0, 1.76, 1.76, 1.0, "ARMOR_SPIKE"),
                sw_box(-1.12, -1.12, +7.2, 2.24, 2.24, 1.9, "ARMOR_SPIKE"),
                sw_box(-0.78, -0.78, +8.8, 1.56, 1.56, 2.2, "RUNE_GLOW", extra_rot=(0, 0, 45)),
                # Thanh chắn kiếm (crossguard) quỷ cong gai đỏ-đen sắc sảo
                sw_box(-1.35, -3.9, -4.0, 2.70, 7.8, 2.2, "ARMOR_SPIKE"),
                sw_box(-1.05, -2.5, -4.9, 2.10, 5.0, 1.5, "ARMOR_STEEL"),
                sw_box(-0.95, -1.2, -4.7, 1.90, 2.4, 1.7, "RUNE_GLOW"),
            ]
            # 3 cặp ngạnh vuốt bảo vệ tay đỏ-đen cong vút trên crossguard & chuôi
            add_claw_spike(w_cubes, sw_pt(0, +3.2, -3.1), sw_pt(0, +6.4, -7.2), 1.22, "CLAW_BLOOD", 4)
            add_claw_spike(w_cubes, sw_pt(0, -3.2, -3.1), sw_pt(0, -6.4, -7.2), 1.22, "CLAW_BLOOD", 4)
            add_claw_spike(w_cubes, sw_pt(0, +2.2, -2.0), sw_pt(0, +4.5, +1.2), 0.90, "ARMOR_SPIKE", 3)
            add_claw_spike(w_cubes, sw_pt(0, -2.2, -2.0), sw_pt(0, -4.5, +1.2), 0.90, "ARMOR_SPIKE", 3)
            add_claw_spike(w_cubes, sw_pt(0, +1.2, +7.8), sw_pt(0, +2.6, +10.0), 0.68, "CLAW_BLOOD", 2)
            add_claw_spike(w_cubes, sw_pt(0, -1.2, +7.8), sw_pt(0, -2.6, +10.0), 0.68, "CLAW_BLOOD", 2)

            # BẢN LƯỠI BẤT ĐỐI XỨNG RĂNG CƯA SẮC SẢO:
            # Sống thép trắng bạc ánh kim (`BLADE_STEEL`) sắc mảnh thanh thoát tương phản mép lưỡi & mũi đỏ máu (`BLADE_EDGE`) + rãnh rune!
            blade_segs = [
                (-3.9,  4.8, 2.05, 1.55, 1.06),
                (-8.5,  4.8, 2.40, 1.80, 0.98),
                (-13.1, 4.8, 2.15, 1.65, 0.90),
                (-17.7, 4.8, 1.80, 1.38, 0.82),
                (-22.3, 4.6, 1.38, 1.08, 0.70),
                (-26.7, 4.0, 0.92, 0.74, 0.54),
            ]
            for idx_b, (zs, blen, hu, hd, bth) in enumerate(blade_segs):
                # Bản thép trắng bạc đánh bóng sắc nét ở sống & thân lưỡi
                w_cubes.append(sw_box(-bth * 0.5, -hd * 0.50, zs - blen, bth, hu * 0.78 + hd * 0.50, blen + 0.25, "BLADE_STEEL"))
                # Gờ sống thép sáng nổi giữa thân lưỡi
                w_cubes.append(sw_box(-bth * 0.58, -0.42, zs - blen, bth * 1.16, 0.84, blen + 0.20, "BLADE_STEEL"))
                # Mép lưỡi sắc đỏ máu thanh gọn bất đối xứng (nhấn mạnh mép sắc & răng cưa thay vì quá dày!)
                w_cubes.append(sw_box(-bth * 0.28, +hu * 0.58, zs - blen, bth * 0.56, hu * 0.48, blen + 0.20, "BLADE_EDGE"))
                w_cubes.append(sw_box(-bth * 0.26, -hd * 1.02, zs - blen, bth * 0.52, hd * 0.56, blen + 0.20, "BLADE_EDGE"))
                # Rãnh rune máu mảnh chạy dọc lõi sống thép (phát sáng fullbright!)
                if idx_b < 5:
                    w_cubes.append(sw_box(-bth * 0.64, -0.18, zs - blen + 0.50, bth * 1.28, 0.36, blen - 1.0, "RUNE_GLOW"))
                # Răng cưa quỷ nhô vát ngược sắc bén dọc hai mép lưỡi
                if idx_b in (0, 1, 2, 3):
                    add_claw_spike(w_cubes, sw_pt(0, +hu * 0.78, zs - blen * 0.52),
                                   sw_pt(0, +hu * 1.62, zs - blen * 0.12), 0.56, "BLADE_EDGE", 2)
                    add_claw_spike(w_cubes, sw_pt(0, -hd * 0.78, zs - blen * 0.72),
                                   sw_pt(0, -hd * 1.54, zs - blen * 0.28), 0.52, "BLADE_EDGE", 2)
                if idx_b in (0, 2):
                    add_claw_spike(w_cubes, sw_pt(0, +hu * 0.65, zs - blen * 0.88),
                                   sw_pt(0, +hu * 1.32, zs - blen * 0.55), 0.46, "BLADE_STEEL", 2)

            # Mũi kiếm đỏ máu vuốt dài nhọn hoắt (tới local z = -34.7 -> tổng dài pommel->tip = 45.2 px = 1.08x chiều cao!)
            add_claw_spike(w_cubes, sw_pt(0, 0, -30.5), sw_pt(0, 0, -34.7), 0.88, "BLADE_EDGE", 3)
            tip_pos = sw_pt(0, 0, -34.7)
            sword_tip_world = [round(v, 2) for v in tip_pos]

            bones.append({
                "name": "weapon",
                "parent": "hand_r",
                "pivot": [round(wp_x, 3), round(wp_y, 3), round(wp_z, 3)],
                "rotation": [0.0, 0.0, 0.0],
                "locators": {
                    "sword_tip": sword_tip_world,
                },
                "cubes": w_cubes,
            })
            bones.append({"name": "sword_tip", "parent": "weapon", "pivot": sword_tip_world, "rotation": [0.0, 0.0, 0.0], "cubes": []})

    build_arm_chain(+1.0)
    build_arm_chain(-1.0)

    # -------------------------------------------------------------------------
    # G. CHÂN THÚ GẬP CHỮ Z DIGITIGRADE 4 ĐỐT THẬT SỰ (`leg_l/r` -> `thigh_l/r` (+ `knee_spur_l/r`) -> `shin_l/r` -> `ankle_hock_l/r` -> `foot_l/r`)
    #    - Đùi nghiêng mạnh về trước tới gối (`z = 0.0 -> -5.8, y = 20.4 -> 13.0`) + mũi giáp đầu gối cong vút về trước!
    #    - Ống chân vát ngược mạnh về sau tạo khớp cổ chân thú cao (`hock` tại `z = +4.6, y = 6.8`) có cựa sau!
    #    - Cổ chân thú (`ankle_hock`) chúc chéo về trước xuống bàn chân (`z = +4.6 -> -0.6, y = 6.8 -> 1.8`)!
    #    - Bàn chân thú xòe rộng thế chữ A (`x = ±7.2`), mũi chân xoay ngoài ±15°, 4 ngón vuốt cong + cựa gót!
    # -------------------------------------------------------------------------
    def build_leg_chain(side: float):
        tag = "l" if side > 0 else "r"
        s = side

        p_hip   = np.array([s * 5.8, 20.4,  0.0], dtype=np.float64)
        p_knee  = np.array([s * 6.8, 12.8, -5.8], dtype=np.float64)  # Đùi nghiêng mạnh ra trước!
        p_hock  = np.array([s * 7.2,  6.8, +4.4], dtype=np.float64)  # Ống chân vát sâu về sau tạo khớp cổ chân thú cao chữ Z!
        p_ankle = np.array([s * 7.4,  2.0, -0.8], dtype=np.float64)  # Chúc mạnh xuống cổ bàn chân

        bones.append({"name": f"leg_{tag}", "parent": "pelvis", "pivot": p_hip.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": []})

        # 1. `thigh_l/r`: Đùi thú bọc KÍN 100% giáp thép đen đánh bóng xếp tầng nghiêng về trước (chỉ bắp tay mới lộ da cơ bắp!)
        th_cubes = []
        add_chain_rod(th_cubes, p_hip, p_knee, 4.3, 3.6, 5, "ARMOR_STEEL", overlap=0.48, aspect_z=1.08)
        for k in range(5):
            ft = 0.08 + k * 0.21
            pm = p_hip * (1.0 - ft) + p_knee * ft
            w_p = 5.1 - k * 0.16
            d_p = 5.4 - k * 0.18
            th_cubes.append(make_cube([pm[0] - w_p * 0.5 + s * 0.20, pm[1] - 1.25, pm[2] - d_p * 0.5],
                                      [w_p, 2.5, d_p], "ARMOR_SPIKE" if k % 2 == 0 else "ARMOR_STEEL",
                                      rot=[34.0, s * -10.0, s * -8.0], pivot=pm.tolist()))
            # Sống giáp cạnh sắc mặt trước + tấm sườn ngoài đùi nhiều bậc
            th_cubes.append(make_cube([pm[0] - 0.85, pm[1] - 0.9, pm[2] - d_p * 0.56],
                                      [1.7, 1.8, 1.1], "ARMOR_SPIKE",
                                      rot=[36.0, s * -10.0, 0.0], pivot=pm.tolist()))
            th_cubes.append(make_cube([pm[0] + (w_p * 0.42 if s > 0 else -(w_p * 0.42 + 1.1)), pm[1] - 1.0, pm[2] - 1.6],
                                      [1.1, 2.0, 3.2], "ARMOR_SPIKE" if k % 2 == 1 else "ARMOR_STEEL",
                                      rot=[32.0, 0.0, s * -16.0], pivot=pm.tolist()))
        # 3 gai đùi vát ngang ra ngoài + 2 gai mặt sau đùi
        for ft, gl in ((0.22, 2.4), (0.52, 2.2), (0.80, 1.9)):
            pm = p_hip * (1.0 - ft) + p_knee * ft
            add_claw_spike(th_cubes, pm + np.array([s * 2.2, 0.4, 0.0]),
                           pm + np.array([s * (2.2 + gl), 1.6, 0.6]), 0.92, "ARMOR_SPIKE", 3)
        for ft in (0.35, 0.68):
            pm = p_hip * (1.0 - ft) + p_knee * ft
            add_claw_spike(th_cubes, pm + np.array([0.0, 0.2, 2.2]),
                           pm + np.array([0.0, 1.2, 4.4]), 0.82, "ARMOR_SPIKE", 2)

        bones.append({"name": f"thigh_{tag}", "parent": f"leg_{tag}", "pivot": p_hip.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": th_cubes})

        # 1b. `knee_spur_l/r`: Mũi giáp đầu gối nhọn cong vút mạnh về trước (cực kỳ sắc nét ở góc nhìn ngang & dưới lên!)
        ks_cubes = [
            make_cube([p_knee[0] - 1.95, p_knee[1] - 1.6, p_knee[2] - 2.4], [3.9, 3.2, 3.2], "ARMOR_SPIKE", rot=[18.0, s * -12.0, 0.0]),
            make_cube([p_knee[0] + s * 1.45, p_knee[1] - 1.3, p_knee[2] - 1.5], [1.2, 2.6, 2.6], "ARMOR_STEEL", rot=[12.0, s * -18.0, s * -14.0]),
        ]
        # Mũi giáp cong vút lên-trước đặc trưng đúng 6 ảnh mẫu!
        spur_pts = [
            (p_knee + np.array([0.0, -0.4, -1.6])).tolist(),
            (p_knee + np.array([s * 0.2, +0.6, -4.4])).tolist(),
            (p_knee + np.array([s * 0.4, +2.6, -6.8])).tolist(),
            (p_knee + np.array([s * 0.5, +4.4, -8.0])).tolist(),
        ]
        add_curved_rod(ks_cubes, spur_pts, 2.15, 0.30, "ARMOR_SPIKE", segs_per_span=3, rib_rings=False)
        add_claw_spike(ks_cubes, p_knee + np.array([0.0, -1.2, -1.8]),
                       p_knee + np.array([0.0, -3.2, -3.4]), 1.10, "ARMOR_SPIKE", 2)
        bones.append({"name": f"knee_spur_{tag}", "parent": f"thigh_{tag}", "pivot": p_knee.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": ks_cubes})

        # 2. `shin_l/r`: Ống chân vát ngược MẠNH về sau (`z = -5.8 -> +4.4`, tạo góc gập chữ Z cực rõ ở góc bên!)
        sh_cubes = []
        add_chain_rod(sh_cubes, p_knee, p_hock, 3.45, 2.55, 6, "ARMOR_STEEL", overlap=0.40, aspect_z=1.15)
        for k in range(5):
            ft = 0.14 + k * 0.18
            pm = p_knee * (1.0 - ft) + p_hock * ft
            w_s = 3.55 - k * 0.18
            sh_cubes.append(make_cube([pm[0] - w_s * 0.5, pm[1] - 1.0, pm[2] - 1.85],
                                      [w_s, 2.2, 3.6], "ARMOR_SPIKE" if k % 2 == 0 else "ARMOR_STEEL",
                                      rot=[-55.0, 0.0, s * -6.0], pivot=pm.tolist()))
            sh_cubes.append(make_cube([pm[0] - 0.65, pm[1] - 0.7, pm[2] - 2.2],
                                      [1.3, 1.5, 1.1], "ARMOR_SPIKE",
                                      rot=[-55.0, 0.0, 0.0], pivot=pm.tolist()))
        # Gai bắp chân hướng ngược sau & ngoài (3 tầng gai dọc ống chân!)
        for ft, sc_g in ((0.25, 1.0), (0.52, 0.9), (0.78, 0.8)):
            pm = p_knee * (1.0 - ft) + p_hock * ft
            add_claw_spike(sh_cubes, pm + np.array([s * 1.4, 0.4, 0.8]),
                           pm + np.array([s * (1.4 + 2.2 * sc_g), 1.6, 2.6]), 0.88 * sc_g, "ARMOR_SPIKE", 3)
            add_claw_spike(sh_cubes, pm + np.array([0.0, 0.7, 1.3]),
                           pm + np.array([0.0, 2.0, 3.8 * sc_g]), 0.84 * sc_g, "ARMOR_SPIKE", 2)

        bones.append({"name": f"shin_{tag}", "parent": f"thigh_{tag}", "pivot": p_knee.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": sh_cubes})

        # 3. `ankle_hock_l/r` (NEW BONE): Khớp cổ chân thú cao (`hock`) có gai sau + đốt cổ chân chúc xuống bàn chân!
        hk_cubes = [
            make_cube([p_hock[0] - 1.45, p_hock[1] - 1.45, p_hock[2] - 1.55], [2.9, 2.9, 3.1], "ARMOR_SPIKE"),
        ]
        # Cựa gai sau cổ chân thú sắc nhọn chĩa sau
        add_claw_spike(hk_cubes, p_hock + np.array([0.0, 0.2, 1.1]), p_hock + np.array([0.0, 1.5, 4.4]), 1.15, "ARMOR_SPIKE", 3)
        add_claw_spike(hk_cubes, p_hock + np.array([s * 1.1, -0.4, 0.8]), p_hock + np.array([s * 2.8, 0.4, 2.6]), 0.78, "ARMOR_SPIKE", 2)
        # Đốt cổ chân thú nghiêng về trước nối xuống cổ bàn chân
        add_chain_rod(hk_cubes, p_hock, p_ankle, 2.55, 2.75, 5, "ARMOR_STEEL", overlap=0.38, aspect_z=1.18)
        for k in range(4):
            ft = 0.18 + k * 0.22
            pm = p_hock * (1.0 - ft) + p_ankle * ft
            hk_cubes.append(make_cube([pm[0] - 1.48, pm[1] - 0.9, pm[2] - 1.65], [2.96, 1.9, 3.2],
                                      "ARMOR_SPIKE" if k % 2 == 0 else "ARMOR_STEEL",
                                      rot=[42.0, 0.0, 0.0], pivot=pm.tolist()))
            hk_cubes.append(make_cube([pm[0] - 0.55, pm[1] - 0.6, pm[2] - 1.95], [1.10, 1.4, 0.9],
                                      "ARMOR_SPIKE", rot=[44.0, 0.0, 0.0], pivot=pm.tolist()))
        bones.append({"name": f"ankle_hock_{tag}", "parent": f"shin_{tag}", "pivot": p_hock.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": hk_cubes})

        # 4. `foot_l/r`: Bàn chân thú rộng bám đất (`y = 0.0`), 4 ngón vuốt cong xòe rõ xoay ngoài ±15° + cựa sau gót!
        yaw_out = math.radians(s * -15.0)  # Xoay mũi chân (-Z) chếch ra ngoài ±15°!
        R_ft = bedrock_rot_matrix([0.0, s * -15.0, 0.0])

        def ft_pt(lx, ly, lz):
            return (np.array([p_ankle[0], 0.0, p_ankle[2]]) + R_ft @ np.array([lx, ly, lz], dtype=np.float64)).tolist()

        def ft_box(lx, ly, lz, sx, sy, sz, mat, extra_rot=(0.0, 0.0, 0.0)):
            lc = np.array([lx + sx * 0.5, ly + sy * 0.5, lz + sz * 0.5], dtype=np.float64)
            wc = np.array([p_ankle[0], 0.0, p_ankle[2]]) + R_ft @ lc
            rot = matrix_to_bedrock_euler(R_ft @ bedrock_rot_matrix(extra_rot))
            return make_cube([wc[0] - sx * 0.5, wc[1] - sy * 0.5, wc[2] - sz * 0.5], [sx, sy, sz], mat, rot=rot, pivot=wc.tolist())

        ft_cubes = [
            ft_box(-2.55, 1.15, -2.6, 5.1, 2.1, 5.0, "ARMOR_STEEL"),
            ft_box(-2.05, 2.05, -3.1, 4.1, 1.4, 3.8, "ARMOR_SPIKE", extra_rot=(18, 0, 0)),
            ft_box(-2.35, 0.0,  -2.2, 4.7, 1.35, 4.4, "FLESH_DARK"),
        ]
        # 4 NGÓN CHÂN THÚ XÒE RÕ RÀNG (đúng ảnh Bottom-Up mẫu!) + móng vuốt cong đỏ-đen chạm đất y=0.05
        toe_specs = [(-2.15, -26.0, 5.4), (-0.75, -9.0, 6.4), (+0.75, +9.0, 6.4), (+2.15, +26.0, 5.4)]
        for tx_b, tang_deg, tlen in toe_specs:
            rad = math.radians(tang_deg)
            dx, dz = math.sin(rad), -math.cos(rad)
            p0 = ft_pt(tx_b, 1.65, -1.6)
            p1 = ft_pt(tx_b + dx * tlen * 0.36, 1.35, -1.6 + dz * tlen * 0.36)
            p2 = ft_pt(tx_b + dx * tlen * 0.68, 0.92, -1.6 + dz * tlen * 0.68)
            p3 = ft_pt(tx_b + dx * tlen * 1.05, 0.08, -1.6 + dz * tlen * 1.05)
            add_chain_rod(ft_cubes, p0, p1, 1.24, 1.05, 2, "ARMOR_STEEL", overlap=0.18)
            add_chain_rod(ft_cubes, p1, p2, 1.04, 0.86, 2, "ARMOR_SPIKE", overlap=0.18)
            add_claw_spike(ft_cubes, p2, p3, 0.84, "CLAW_BLOOD", 3)
        # Cựa sau gót chân sắc bám đất
        add_chain_rod(ft_cubes, ft_pt(0.0, 1.45, +1.9), ft_pt(0.0, 0.92, +3.9), 1.15, 0.78, 3, "ARMOR_STEEL", overlap=0.18)
        add_claw_spike(ft_cubes, ft_pt(0.0, 0.92, +3.8), ft_pt(0.0, 0.08, +5.6), 0.76, "CLAW_BLOOD", 3)

        bones.append({"name": f"foot_{tag}", "parent": f"ankle_hock_{tag}", "pivot": p_ankle.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": ft_cubes})

    build_leg_chain(+1.0)
    build_leg_chain(-1.0)

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    assert 1200 <= total_cubes <= 1700, f"Cube count {total_cubes} ngoài khoảng 1200-1700!"

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.nightmare_knight",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 18.0,
                    "visible_bounds_height": 14.0,
                    "visible_bounds_offset": [0.0, 6.0, 0.0],
                },
                "bones": bones,
            }
        ],
    }
    out = ASSETS / "geo" / "entity" / "nightmare_knight.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
    meta = {"bones": len(bones), "cubes": total_cubes, "sword_tip": sword_tip_world}
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, sword_tip={sword_tip_world})")
    return geo, total_cubes, meta


# =============================================================================
# 2. SINH 4 TEXTURE 1024x1024 NEAREST RGBA (THÉP ĐEN ĐÁNH BÓNG, SỌ SÁNG, CÁNH & CHOÀNG ĐỎ, KIẾM BẠC-MÁU)
# =============================================================================
def hex_arr(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], dtype=np.float32)


def draw_cracks_mask(w: int, h: int, seed: int, count: int = 3) -> np.ndarray:
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    for k in range(count):
        x = float(w * (0.18 + 0.64 * (k / max(1, count - 1))) + rng.uniform(-w * 0.05, w * 0.05))
        y = 0.0
        pts = [(x, y)]
        for _ in range(11):
            y += h / 10.0
            x += float(rng.uniform(-w * 0.07, w * 0.07))
            pts.append((x, y))
        d.line(pts, fill=115, width=7)
        d.line(pts, fill=215, width=4)
        d.line(pts, fill=255, width=2)
        for bi in (2, 5, 8):
            bx, by = pts[bi]
            dx = 1.0 if (k + bi) % 2 == 0 else -1.0
            d.line([(bx, by), (bx + dx * w * 0.09, by + h * 0.08)], fill=175, width=3)
    return np.array(img, dtype=np.float32) / 255.0


def build_textures() -> None:
    base = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    base[:, :, :3] = [24, 27, 34]
    base[:, :, 3] = 255

    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    enraged = base.copy()
    enraged_glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    # Thép đen đánh bóng ánh kim nổi khối sắc nét (đúng bảng màu #1E2128 -> #343945 -> #565E70 -> #8E97A8!)
    c_st0 = hex_arr("#12141A")
    c_st1 = hex_arr("#1E2128")
    c_st2 = hex_arr("#343945")
    c_st3 = hex_arr("#565E70")
    c_st4 = hex_arr("#8E97A8")

    c_fire_out = hex_arr("#B81214")
    c_fire_mid = hex_arr("#FF3818")
    c_fire_hot = hex_arr("#FFE89C")

    # 1. ARMOR_STEEL & ARMOR_SPIKE (256x256 mỗi ô): Phase 1 sạch ánh kim thép đánh bóng (0 vết nứt cam!), Phase 2 nứt đỏ mảnh!
    for key, seed, hi_boost in (("ARMOR_STEEL", 11, 1.05), ("ARMOR_SPIKE", 22, 1.35)):
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        nx, ny = xx / w, yy / h
        edge_x = np.minimum(nx, 1.0 - nx) * 2.0
        ao_seam = np.clip((1.0 - np.exp(-((1.0 - ny) * 10.0))) * np.clip(edge_x * 5.2, 0.38, 1.0), 0.26, 1.0)
        # Vát ánh kim sắc dọc cạnh trên, gờ giữa và 2 mép ngoài giúp tách khối từng tầng giáp rõ mồn một!
        bevel_hi = (
            np.exp(-(((ny - 0.07) * 15.0) ** 2)) * 1.00
            + np.exp(-((edge_x * 12.0) ** 2)) * 0.72
            + np.exp(-(((nx - 0.50) * 16.0) ** 2)) * (0.45 if key == "ARMOR_SPIKE" else 0.18)
        )
        rng = np.random.default_rng(seed)
        brushed = 0.93 + 0.07 * np.sin(nx * 24.0 + ny * 8.0) * np.cos(ny * 16.0 - nx * 5.0)
        s_img = Image.new("L", (w, h), 0)
        sd = ImageDraw.Draw(s_img)
        for _ in range(14):
            sx0, sy0 = int(rng.integers(14, w - 14)), int(rng.integers(14, h - 14))
            sd.line([(sx0, sy0), (sx0 + int(rng.integers(-48, 48)), sy0 + int(rng.integers(-28, 28)))], fill=225, width=2)
        scratch = np.array(s_img, dtype=np.float32) / 255.0

        grad = np.clip(0.36 + 0.54 * (1.0 - ny) + 0.24 * (1.0 - np.abs(nx - 0.5) * 2.0), 0.0, 1.0)
        col = c_st0 * (1.0 - ao_seam[:, :, None]) + (c_st1 * (1.0 - grad[:, :, None]) + c_st2 * grad[:, :, None]) * ao_seam[:, :, None]
        col = col * brushed[:, :, None]
        col += (c_st3 - c_st2) * np.clip(bevel_hi * 0.95 * hi_boost, 0.0, 1.0)[:, :, None]
        col += (c_st4 - c_st3) * np.clip(scratch * 0.65 + (bevel_hi > 0.68) * 0.68 * hi_boost, 0.0, 1.0)[:, :, None]
        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(col, 0, 255).astype(np.uint8)

        # Phase 2 Enraged: các vết nứt đỏ nóng mảnh trên giáp ngực/vai/đùi
        cr = draw_cracks_mask(w, h, seed=seed + 100, count=2) * (0.95 if key == "ARMOR_STEEL" else 0.55)
        fire = (
            c_fire_out * np.clip(cr * 2.0, 0, 1)[:, :, None] * (1.0 - np.clip((cr - 0.45) * 2.2, 0, 1))[:, :, None]
            + c_fire_mid * np.clip((cr - 0.35) * 2.2, 0, 1)[:, :, None] * (1.0 - np.clip((cr - 0.75) * 4.0, 0, 1))[:, :, None]
            + c_fire_hot * np.clip((cr - 0.72) * 3.8, 0, 1)[:, :, None]
        )
        mix = np.clip(cr * 1.25, 0.0, 1.0)[:, :, None]
        e_col = col * (1.0 - mix) + np.clip(fire, 0, 255) * mix
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(e_col, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(fire, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(cr * 235.0, 0, 255).astype(np.uint8)

    # 2. WING_MEMBRANE (512x384): Chuyển sắc mượt sẫm tối dọc nan xương -> đỏ rượu/đỏ hồng thẫm tâm khoang + vòm dơi + lỗ nhỏ tự nhiên!
    x0, y0, w, h = MAT["WING_MEMBRANE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h

    arch = 0.98 - 0.19 * (np.sin(nx * math.pi) ** 0.90)
    nick1 = 0.032 * np.exp(-(((nx - 0.34) * 26.0) ** 2))
    nick2 = 0.028 * np.exp(-(((nx - 0.67) * 30.0) ** 2))
    bottom_edge = arch - nick1 - nick2
    alpha = (ny < bottom_edge).astype(np.float32)

    holes = [
        (0.28, 0.44, 0.025, 0.034),
        (0.55, 0.52, 0.022, 0.030),
        (0.75, 0.37, 0.019, 0.026),
        (0.42, 0.68, 0.016, 0.022),
    ]
    hole_rim = np.zeros_like(alpha)
    for hx, hy, hr_x, hr_y in holes:
        dist = np.sqrt(((nx - hx) / hr_x) ** 2 + ((ny - hy) / hr_y) ** 2)
        alpha = np.where(dist < 1.0, 0.0, alpha)
        hole_rim = np.maximum(hole_rim, np.exp(-(((dist - 1.16) * 4.5) ** 2)))

    side_dark = np.clip(np.minimum(nx, 1.0 - nx) * 7.5, 0.22, 1.0)
    edge_dark = np.clip((bottom_edge - ny) * 10.0, 0.0, 1.0) * np.clip(ny * 7.5, 0.22, 1.0) * side_dark
    veins = np.exp(-((np.sin((nx * 6.0 + ny * 2.4) * math.pi) * 6.5) ** 2)) * 0.22
    center_glow = np.clip(1.0 - np.sqrt(((nx - 0.5) * 1.35) ** 2 + ((ny - 0.42) * 1.65) ** 2), 0.0, 1.0) * edge_dark
    c_w_dark = hex_arr("#1A060B")
    c_w_deep = hex_arr("#721824")
    c_w_rose = hex_arr("#C23A46")  # Ánh đỏ rượu/đỏ hồng thẫm xuyên sáng ở tâm khoang đúng 6 ảnh mẫu!
    w_col = (
        c_w_dark * (1.0 - edge_dark[:, :, None])
        + (c_w_deep * (1.0 - center_glow[:, :, None]) + c_w_rose * center_glow[:, :, None]) * edge_dark[:, :, None]
    )
    w_col = w_col * (1.0 - veins[:, :, None]) + c_w_dark * veins[:, :, None]
    w_col = w_col * (1.0 - hole_rim[:, :, None] * 0.65) + c_w_dark * (hole_rim[:, :, None] * 0.65)

    alpha[h - 10:, w - 10:] = 0.0
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(w_col, 0, 255).astype(np.uint8)
    base[y0:y0 + h, x0:x0 + w, 3] = (alpha * 255).astype(np.uint8)

    rim_heat = np.clip(1.0 - (bottom_edge - ny) * 10.0, 0.0, 1.0) * alpha
    e_w = w_col * 1.18 + c_fire_mid * (rim_heat * 0.85 + hole_rim * 0.55)[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(e_w, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, 3] = (alpha * 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_fire_mid * 0.7 + c_fire_hot * 0.3, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((rim_heat * 215.0 + hole_rim * 140.0) * alpha, 0, 255).astype(np.uint8)

    # 3. CAPE_TATTER & CLOTH_TATTER (256x384 mỗi ô): Nếp gấp đỏ nhung sâu + mép dưới rách tơi tự nhiên!
    for key, seed, emb_col in (("CAPE_TATTER", 33, hex_arr("#340A10")), ("CLOTH_TATTER", 44, hex_arr("#C89B54"))):
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        nx, ny = xx / w, yy / h
        fringe = 0.80 + 0.13 * np.sin(nx * math.pi * 11.0 + seed) + 0.06 * np.cos(nx * math.pi * 27.0)
        c_alpha = (ny < fringe).astype(np.float32)
        if key == "CAPE_TATTER":
            # Vài vết thủng nhỏ ở gần chân choàng (y > 0.72) đúng ảnh sau lưng mẫu!
            for hx, hy, rx, ry in ((0.16, 0.74, 0.025, 0.028), (0.82, 0.72, 0.028, 0.030), (0.48, 0.78, 0.020, 0.024)):
                d = np.sqrt(((nx - hx) / rx) ** 2 + ((ny - hy) / ry) ** 2)
                c_alpha = np.where(d < 1.0, 0.0, c_alpha)
        c_alpha[h - 10:, w - 10:] = 0.0
        fold = 0.5 + 0.5 * np.sin(nx * math.pi * 9.0 + ny * 1.4)
        c_dark = hex_arr("#460812")
        c_mid = hex_arr("#921422")
        c_hi = hex_arr("#C62838")
        c_col = c_dark * (1.0 - fold[:, :, None]) + c_mid * fold[:, :, None] + (c_hi - c_mid) * (fold ** 2.4)[:, :, None]
        trim = ((np.abs(nx - 0.10) < 0.025) | (np.abs(nx - 0.90) < 0.025)).astype(np.float32) * (ny < fringe - 0.06)
        c_col = c_col * (1.0 - trim[:, :, None] * 0.50) + emb_col * (trim[:, :, None] * 0.50)
        char = np.clip(1.0 - (fringe - ny) * 12.0, 0.0, 1.0)
        c_col = c_col * (1.0 - char[:, :, None] * 0.55) + hex_arr("#1A080C") * (char[:, :, None] * 0.55)
        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_col, 0, 255).astype(np.uint8)
        base[y0:y0 + h, x0:x0 + w, 3] = (c_alpha * 255).astype(np.uint8)

        ec = c_col * 1.15 + c_fire_mid * (char * 0.70)[:, :, None]
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(ec, 0, 255).astype(np.uint8)
        enraged[y0:y0 + h, x0:x0 + w, 3] = (c_alpha * 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_fire_mid, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(char * 185.0 * c_alpha, 0, 255).astype(np.uint8)

    # 4. BONE_RIB & SKULL_BONE (256x256 mỗi ô): Sọ xương xám ngà SÁNG nổi bật (#9E9892 -> #C7C0B8 -> #E2DCD4)!
    for key, c_lo, c_mid_h, c_hi_h in (
        ("BONE_RIB",   "#544E4A", "#9E968E", "#CFC8C0"),
        ("SKULL_BONE", "#6E6762", "#B2AAA2", "#E2DBD2"),
    ):
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        cyl = 1.0 - np.abs((xx / w) * 2.0 - 1.0)
        joint = 0.86 + 0.14 * np.sin((yy / h) * math.pi * 6.0)
        c_b0 = hex_arr(c_lo)
        c_b1 = hex_arr(c_mid_h)
        c_b2 = hex_arr(c_hi_h)
        b_col = c_b0 * (1.0 - cyl[:, :, None]) + c_b1 * cyl[:, :, None] + (c_b2 - c_b1) * (cyl ** 1.9)[:, :, None]
        b_col *= joint[:, :, None]
        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(b_col, 0, 255).astype(np.uint8)
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(b_col * np.array([1.08, 0.94, 0.90]), 0, 255).astype(np.uint8)

    # 5. HORN_CRIMSON (256x256): Sừng đỏ thẫm dày có gân vòng ngang sắc nét
    x0, y0, w, h = MAT["HORN_CRIMSON"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ny = yy / h
    cyl_h = 1.0 - np.abs((xx / w) * 2.0 - 1.0)
    ring = (yy % 24.0) / 24.0
    ridge = np.exp(-(((ring - 0.30) * 6.5) ** 2))
    ho_base = hex_arr("#42141C") * (1.0 - ny[:, :, None]) + hex_arr("#B22634") * (ny ** 0.65)[:, :, None]
    ho_col = ho_base * (0.72 + 0.34 * cyl_h[:, :, None]) + hex_arr("#2A0C12") * (ridge * 0.60)[:, :, None] + hex_arr("#D84A54") * (ridge * cyl_h)[:, :, None] * 0.48
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(ho_col, 0, 255).astype(np.uint8)
    hoe_col = ho_col + c_fire_mid * (ny ** 1.4 * 0.65)[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(hoe_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_fire_mid, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((ny ** 1.5) * 190.0, 0, 255).astype(np.uint8)

    # 6. FLESH_DARK (256x256): Cơ bắp nâu-xám săn chắc nổi tương phản rõ với thép đen!
    x0, y0, w, h = MAT["FLESH_DARK"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cyl_f = 1.0 - np.abs((xx / w) * 2.0 - 1.0)
    fiber = 0.88 + 0.12 * np.sin(xx * 0.30 + np.sin(yy * 0.08) * 2.2)
    fl_col = (hex_arr("#42302C") * (1.0 - cyl_f[:, :, None]) + hex_arr("#7E5B50") * cyl_f[:, :, None]) * fiber[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(fl_col, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(fl_col * 1.08, 0, 255).astype(np.uint8)

    # 7. WING_BONE (256x256): Xương cánh đen obsidian thanh mảnh có ánh thép lạnh
    x0, y0, w, h = MAT["WING_BONE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cyl = 1.0 - np.abs((xx / w) * 2.0 - 1.0)
    knot = np.exp(-((((yy / h) - 0.5) * 4.0) ** 2)) * 0.35
    wb = hex_arr("#101218") + hex_arr("#3E4658") * (cyl * 0.85 + knot * 0.25)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(wb, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(wb + hex_arr("#4A1418") * knot[:, :, None], 0, 255).astype(np.uint8)

    # 8. BLADE_STEEL (256x256): Sống lưỡi thép TRẮNG BẠC ÁNH KIM SẮC NÉT (#9AA2AE -> #D8DCE2 -> #F4F7FC)!
    x0, y0, w, h = MAT["BLADE_STEEL"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    sheen = 1.0 - np.abs((xx / w) * 2.0 - 1.0)
    bs = hex_arr("#9AA2AE") + (hex_arr("#ECEFF5") - hex_arr("#9AA2AE")) * (sheen ** 0.65)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(bs, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(bs * np.array([1.02, 0.94, 0.94]), 0, 255).astype(np.uint8)

    # 9. BLADE_EDGE (256x256): Mép răng cưa & mũi kiếm ĐỎ MÁU RỰC (#8E141C -> #D9262E, P2 lõi nóng sáng rực!)
    x0, y0, w, h = MAT["BLADE_EDGE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    edge_v = 1.0 - np.abs((xx / w) * 2.0 - 1.0)
    be_p1 = hex_arr("#8E141C") + (hex_arr("#E62832") - hex_arr("#8E141C")) * (edge_v ** 0.65)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(be_p1, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(hex_arr("#FF2430"), 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((edge_v ** 0.85) * 185.0, 0, 255).astype(np.uint8)

    be_p2 = hex_arr("#E21C1C") + (c_fire_hot - hex_arr("#E21C1C")) * (edge_v ** 1.1)[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(be_p2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(be_p2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = 255

    # 10. RUNE_GLOW & EYE_GLOW (128x128 mỗi ô, 100% fullbright!)
    for key, c_p1, c_p2 in (
        ("RUNE_GLOW", hex_arr("#FF1A2A"), hex_arr("#FF9C38")),
        ("EYE_GLOW",  hex_arr("#FF2424"), hex_arr("#FFF6E8")),
    ):
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        rr = np.sqrt(((xx / w) * 2.0 - 1.0) ** 2 + ((yy / h) * 2.0 - 1.0) ** 2)
        core = np.clip(1.0 - rr * 0.82, 0.0, 1.0)
        p1 = hex_arr("#A80814") * (1.0 - core[:, :, None]) + c_p1 * core[:, :, None] + hex_arr("#FFA880") * (core ** 2.2)[:, :, None]
        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(p1, 0, 255).astype(np.uint8)
        glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(p1 * 1.18, 0, 255).astype(np.uint8)
        glow[y0:y0 + h, x0:x0 + w, 3] = 255
        p2 = hex_arr("#FF1E1E") * (1.0 - core[:, :, None]) + c_p2 * (core ** 0.7)[:, :, None]
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(p2, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(p2, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, 3] = 255

    # 11. CLAW_BLOOD (256x128)
    x0, y0, w, h = MAT["CLAW_BLOOD"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ny = yy / h
    cl = hex_arr("#1E1216") * (1.0 - ny[:, :, None]) + hex_arr("#A81A28") * ny[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(cl, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(cl + c_fire_mid * (ny ** 1.5 * 0.55)[:, :, None], 0, 255).astype(np.uint8)

    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(base, "RGBA").save(tex_dir / "nightmare_knight.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "nightmare_knight_glowmask.png")
    Image.fromarray(enraged, "RGBA").save(tex_dir / "nightmare_knight_enraged.png")
    Image.fromarray(enraged_glow, "RGBA").save(tex_dir / "nightmare_knight_enraged_glowmask.png")
    print(f"TEX_OK: 4 textures ({TEX_W}x{TEX_H}, RGBA cutout, nearest)")


# =============================================================================
# 3. 17 ANIMATION MƯỢT MÀ NẶNG KHỐI PACIFIC RIM — KHỚP 100% 0-TICK NightmareAttackTiming.java!
#    Bổ sung khớp cổ chân chữ Z (`ankle_hock_l/r`), mũi gối (`knee_spur_l/r`), áo choàng 3 tầng + 2 vạt (`cape_*`)!
# =============================================================================
def kf(vec, easing="catmullrom"):
    return {"vector": vec, "easing": easing}


def build_animations() -> dict:
    anims = {
        # IDLE (4.00s): Thở chậm sâu 4s, hàm xương khẽ nhấp, gối & cổ chân chữ Z nhún đàn hồi, cánh trễ nhịp 0.2s, choàng 3 tầng lay gió!
        "animation.nightmare_knight.idle": {
            "loop": True,
            "animation_length": 4.00,
            "bones": {
                "pelvis": {
                    "position": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([0, -0.55, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")},
                    "rotation": {"0.0": kf([0, -1, 0], "easeInOutSine"), "2.0": kf([1.5, 1, 0], "easeInOutSine"), "4.0": kf([0, -1, 0], "easeInOutSine")},
                },
                "chest": {"rotation": {"0.0": kf([1.5, 0, 0], "easeInOutSine"), "1.95": kf([5.5, 0, 0], "easeInOutSine"), "4.0": kf([1.5, 0, 0], "easeInOutSine")}},
                "head": {"rotation": {"0.0": kf([1, -4, 0], "catmullrom"), "1.6": kf([3.5, 4.5, 1], "catmullrom"), "3.0": kf([2, -3, -1], "catmullrom"), "4.0": kf([1, -4, 0], "catmullrom")}},
                "jaw": {"rotation": {"0.0": kf([4, 0, 0], "easeInOutSine"), "2.1": kf([12, 0, 0], "easeInOutSine"), "4.0": kf([4, 0, 0], "easeInOutSine")}},
                # Nhún đàn hồi nhẹ khớp chân thú chữ Z giữ bàn chân bám đất phẳng
                "thigh_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([5.0, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "thigh_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([5.0, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([3, 0, 1], "easeInOutSine"), "2.1": kf([7, 0, 3], "easeInOutSine"), "4.0": kf([3, 0, 1], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([-2, 0, -1], "easeInOutSine"), "2.1": kf([2, 0, -3], "easeInOutSine"), "4.0": kf([-2, 0, -1], "easeInOutSine")}},
                "weapon": {"rotation": {"0.0": kf([1, 0, 0], "easeInOutSine"), "2.25": kf([5, 0, -1.5], "easeInOutSine"), "4.0": kf([1, 0, 0], "easeInOutSine")}},
                # Cánh xòe ngang phập phồng trễ pha 0.2s sau nhịp ngực
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.15": kf([2.5, -6, 3], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.15": kf([2.5, 6, -3], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l_mid": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.35": kf([0, -5, 2.5], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r_mid": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.35": kf([0, 5, -2.5], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                # Áo choàng 3 tầng + 2 vạt hông lượn sóng gió ngầm trễ pha từng tầng!
                "cape_upper": {"rotation": {"0.0": kf([1.5, 0, 0], "easeInOutSine"), "2.1": kf([5.5, 0, 0], "easeInOutSine"), "4.0": kf([1.5, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([2.0, 0, 0], "easeInOutSine"), "2.35": kf([7.5, 0, 0], "easeInOutSine"), "4.0": kf([2.0, 0, 0], "easeInOutSine")}},
                "cape_lower": {"rotation": {"0.0": kf([2.5, 0, 0], "easeInOutSine"), "2.60": kf([9.5, 0, 0], "easeInOutSine"), "4.0": kf([2.5, 0, 0], "easeInOutSine")}},
                "cape_l": {"rotation": {"0.0": kf([1.5, 0, 1], "easeInOutSine"), "2.3": kf([5.5, -3, 4], "easeInOutSine"), "4.0": kf([1.5, 0, 1], "easeInOutSine")}},
                "cape_r": {"rotation": {"0.0": kf([1.5, 0, -1], "easeInOutSine"), "2.3": kf([5.5, 3, -4], "easeInOutSine"), "4.0": kf([1.5, 0, -1], "easeInOutSine")}},
                "tabard": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.25": kf([4, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # WALK (1.80s = 36t): Bước thú chữ Z đàn hồi cực tự nhiên (`thigh` -> `shin` -> `ankle_hock` -> `foot`) + dồn trọng tâm hông + sóng áo choàng 3 tầng!
        "animation.nightmare_knight.walk": {
            "loop": True,
            "animation_length": 1.80,
            "bones": {
                "pelvis": {
                    "position": {
                        "0.0":  kf([0.0, 0.3, 0.0], "catmullrom"),
                        "0.45": kf([1.5, -1.6, 0.0], "easeOutCubic"),
                        "0.90": kf([0.0, 0.3, 0.0], "catmullrom"),
                        "1.35": kf([-1.5, -1.6, 0.0], "easeOutCubic"),
                        "1.80": kf([0.0, 0.3, 0.0], "catmullrom"),
                    },
                    "rotation": {
                        "0.0":  kf([2, 6, 0], "catmullrom"),
                        "0.45": kf([5, 8, -3.5], "easeOutCubic"),
                        "0.90": kf([2, -6, 0], "catmullrom"),
                        "1.35": kf([5, -8, 3.5], "easeOutCubic"),
                        "1.80": kf([2, 6, 0], "catmullrom"),
                    },
                },
                "chest": {
                    "rotation": {
                        "0.0":  kf([5, -8, 0], "catmullrom"),
                        "0.45": kf([9, -11, 3], "easeOutCubic"),
                        "0.90": kf([5, 8, 0], "catmullrom"),
                        "1.35": kf([9, 11, -3], "easeOutCubic"),
                        "1.80": kf([5, -8, 0], "catmullrom"),
                    },
                },
                "head": {
                    "rotation": {
                        "0.0":  kf([-2, 4, 0], "easeInOutSine"),
                        "0.55": kf([2, 6, -1], "easeInOutSine"),
                        "1.45": kf([2, -6, 1], "easeInOutSine"),
                        "1.80": kf([-2, 4, 0], "easeInOutSine"),
                    },
                },
                # Chân trái chữ Z: bước tới (0.0 -> 0.45 đặt nặng) -> đẩy sau duỗi khớp cổ chân hock (0.90) -> nhấc gập chữ Z (1.35)
                "leg_l": {"rotation": {"0.0": kf([-18, 0, 0], "catmullrom"), "0.45": kf([-10, 0, 0], "easeOutCubic"), "0.90": kf([18, 0, 0], "catmullrom"), "1.35": kf([10, 0, 0], "catmullrom"), "1.80": kf([-18, 0, 0], "catmullrom")}},
                "shin_l": {"rotation": {"0.0": kf([14, 0, 0], "catmullrom"), "0.45": kf([10, 0, 0], "easeOutCubic"), "0.90": kf([-6, 0, 0], "catmullrom"), "1.35": kf([26, 0, 0], "catmullrom"), "1.80": kf([14, 0, 0], "catmullrom")}},
                "ankle_hock_l": {"rotation": {"0.0": kf([-10, 0, 0], "catmullrom"), "0.45": kf([0, 0, 0], "easeOutCubic"), "0.90": kf([16, 0, 0], "catmullrom"), "1.35": kf([-24, 0, 0], "catmullrom"), "1.80": kf([-10, 0, 0], "catmullrom")}},
                "foot_l": {"rotation": {"0.0": kf([12, 0, 0], "catmullrom"), "0.45": kf([0, 0, 0], "easeOutCubic"), "0.90": kf([-20, 0, 0], "catmullrom"), "1.35": kf([14, 0, 0], "catmullrom"), "1.80": kf([12, 0, 0], "catmullrom")}},
                # Chân phải chữ Z đối pha hoàn hảo
                "leg_r": {"rotation": {"0.0": kf([18, 0, 0], "catmullrom"), "0.45": kf([10, 0, 0], "catmullrom"), "0.90": kf([-18, 0, 0], "catmullrom"), "1.35": kf([-10, 0, 0], "easeOutCubic"), "1.80": kf([18, 0, 0], "catmullrom")}},
                "shin_r": {"rotation": {"0.0": kf([-6, 0, 0], "catmullrom"), "0.45": kf([26, 0, 0], "catmullrom"), "0.90": kf([14, 0, 0], "catmullrom"), "1.35": kf([10, 0, 0], "easeOutCubic"), "1.80": kf([-6, 0, 0], "catmullrom")}},
                "ankle_hock_r": {"rotation": {"0.0": kf([16, 0, 0], "catmullrom"), "0.45": kf([-24, 0, 0], "catmullrom"), "0.90": kf([-10, 0, 0], "catmullrom"), "1.35": kf([0, 0, 0], "easeOutCubic"), "1.80": kf([16, 0, 0], "catmullrom")}},
                "foot_r": {"rotation": {"0.0": kf([-20, 0, 0], "catmullrom"), "0.45": kf([14, 0, 0], "catmullrom"), "0.90": kf([12, 0, 0], "catmullrom"), "1.35": kf([0, 0, 0], "easeOutCubic"), "1.80": kf([-20, 0, 0], "catmullrom")}},
                "arm_l": {"rotation": {"0.0": kf([14, 0, -2], "catmullrom"), "0.90": kf([-16, 0, -4], "catmullrom"), "1.80": kf([14, 0, -2], "catmullrom")}},
                "arm_r": {"rotation": {"0.0": kf([-4, 0, 3], "catmullrom"), "0.90": kf([12, 0, 5], "catmullrom"), "1.80": kf([-4, 0, 3], "catmullrom")}},
                "wing_l": {"rotation": {"0.0": kf([4, -8, 3], "easeInOutSine"), "0.60": kf([8, -14, 5], "easeInOutSine"), "1.50": kf([8, -10, 4], "easeInOutSine"), "1.80": kf([4, -8, 3], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([4, 8, -3], "easeInOutSine"), "0.60": kf([8, 10, -4], "easeInOutSine"), "1.50": kf([8, 14, -5], "easeInOutSine"), "1.80": kf([4, 8, -3], "easeInOutSine")}},
                # Áo choàng rộng 3 tầng lướt sóng mềm về sau theo nhịp bước nặng
                "cape_upper": {"rotation": {"0.0": kf([10, 0, 0], "easeInOutSine"), "0.55": kf([16, 0, 0], "easeInOutSine"), "1.45": kf([16, 0, 0], "easeInOutSine"), "1.80": kf([10, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([8, 0, 0], "easeInOutSine"), "0.70": kf([14, 0, 0], "easeInOutSine"), "1.60": kf([14, 0, 0], "easeInOutSine"), "1.80": kf([8, 0, 0], "easeInOutSine")}},
                "cape_lower": {"rotation": {"0.0": kf([6, 0, 0], "easeInOutSine"), "0.85": kf([12, 0, 0], "easeInOutSine"), "1.75": kf([12, 0, 0], "easeInOutSine"), "1.80": kf([6, 0, 0], "easeInOutSine")}},
                "cape_l": {"rotation": {"0.0": kf([10, -4, 4], "easeInOutSine"), "0.90": kf([16, -8, 7], "easeInOutSine"), "1.80": kf([10, -4, 4], "easeInOutSine")}},
                "cape_r": {"rotation": {"0.0": kf([10, 4, -4], "easeInOutSine"), "0.90": kf([16, 8, -7], "easeInOutSine"), "1.80": kf([10, 4, -4], "easeInOutSine")}},
                "tabard": {"rotation": {"0.0": kf([9, 0, 0], "easeInOutSine"), "0.90": kf([-5, 0, 0], "easeInOutSine"), "1.80": kf([9, 0, 0], "easeInOutSine")}},
            },
        },
        # 1. GREATSWORD_SLASH: total=32t (1.60s) | HitWindow=[18..21] (0.90..1.05s)
        "animation.nightmare_knight.slash": {
            "animation_length": 1.60,
            "bones": {
                "pelvis": {
                    "position": {"0.0": kf([0, 0, 0]), "0.65": kf([0, -1.4, 0.8], "easeInCubic"), "0.95": kf([0, -0.5, -1.5], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeOutCubic")},
                    "rotation": {"0.0": kf([0, 0, 0]), "0.65": kf([4, 24, 0], "easeInCubic"), "0.90": kf([8, -22, 0], "easeOutExpo"), "1.12": kf([8, -28, 0], "easeOutCubic"), "1.60": kf([0, 0, 0], "easeInOutSine")},
                },
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.65": kf([10, 0, 0], "easeInCubic"), "0.95": kf([-4, 0, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.65": kf([-8, 0, 0], "easeInCubic"), "0.95": kf([6, 0, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.72": kf([8, 46, -4], "easeInCubic"), "0.90": kf([16, -24, 4], "easeOutExpo"), "1.08": kf([18, -56, 6], "easeOutBack"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-42, 34, -48], "easeInCubic"), "0.90": kf([-72, -22, -8], "easeOutExpo"), "1.08": kf([-58, -64, 24], "easeOutBack"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "weapon": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-18, 0, 22], "easeInCubic"), "0.96": kf([18, 0, -26], "easeOutExpo"), "1.18": kf([24, 0, -30], "easeOutCubic"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.72": kf([4, -18, 6], "easeInCubic"), "0.96": kf([-6, 16, -8], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.72": kf([4, 18, -6], "easeInCubic"), "0.96": kf([-6, -16, 8], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([8, -14, 0], "easeInCubic"), "1.05": kf([18, 22, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([0, 0, 0]), "0.82": kf([10, -18, 0]), "1.15": kf([20, 26, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "cape_lower": {"rotation": {"0.0": kf([0, 0, 0]), "0.90": kf([12, -20, 0]), "1.25": kf([22, 28, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 2. OVERHEAD_SMASH: total=40t (2.00s) | HitWindow=[24..27] (1.20..1.35s), Wave=[27..29] (1.35..1.45s)
        "animation.nightmare_knight.overhead_smash": {
            "animation_length": 2.00,
            "bones": {
                "pelvis": {"position": {"0.0": kf([0, 0, 0]), "1.00": kf([0, 0.8, 1.2], "easeInCubic"), "1.20": kf([0, -2.8, -1.5], "easeOutExpo"), "1.52": kf([0, -2.2, -1.2], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.00": kf([-8, 0, 0], "easeInCubic"), "1.20": kf([18, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.00": kf([-8, 0, 0], "easeInCubic"), "1.20": kf([18, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([-14, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([-14, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "1.02": kf([-28, 0, 0], "easeInCubic"), "1.20": kf([36, 0, 0], "easeOutExpo"), "1.48": kf([38, 0, 0], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-154, 0, -12], "easeInCubic"), "1.20": kf([28, 0, 0], "easeOutExpo"), "1.48": kf([32, 0, 0], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-126, 0, 12], "easeInCubic"), "1.20": kf([24, 0, 6], "easeOutExpo"), "1.48": kf([26, 0, 6], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "head": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-22, 0, 0], "easeInCubic"), "1.22": kf([22, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([38, 0, 0], "easeInCubic"), "1.42": kf([28, 0, 0], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.02": kf([-14, 22, -16], "easeInCubic"), "1.22": kf([16, -16, 10], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.02": kf([-14, -22, 16], "easeInCubic"), "1.22": kf([16, 16, -10], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-10, 0, 0]), "1.28": kf([28, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([0, 0, 0]), "1.35": kf([24, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 3. THRUST: total=34t (1.70s) | HitWindow=[19..22] (0.95..1.10s)
        "animation.nightmare_knight.thrust": {
            "animation_length": 1.70,
            "bones": {
                "pelvis": {
                    "position": {"0.0": kf([0, 0, 0]), "0.78": kf([0, -1.4, 2.2], "easeInCubic"), "0.95": kf([0, -1.8, -3.2], "easeOutExpo"), "1.18": kf([0, -1.4, -2.8], "easeOutCubic"), "1.70": kf([0, 0, 0], "easeInOutSine")},
                    "rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([6, 28, 0], "easeInCubic"), "0.95": kf([12, -22, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")},
                },
                "ankle_hock_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([-12, 0, 0], "easeInCubic"), "0.95": kf([14, 0, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([8, 44, 0], "easeInCubic"), "0.95": kf([22, -30, 0], "easeOutExpo"), "1.15": kf([22, -32, 0], "easeOutCubic"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([34, 20, -14], "easeInCubic"), "0.95": kf([-88, -12, 0], "easeOutExpo"), "1.15": kf([-88, -12, 0], "easeOutCubic"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "weapon": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([-24, -14, 0], "easeInCubic"), "0.95": kf([-34, -16, 8], "easeOutExpo"), "1.15": kf([-34, -16, 8], "easeOutCubic"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([6, -22, 10], "easeInCubic"), "0.96": kf([12, -30, 12], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([6, 22, -10], "easeInCubic"), "0.96": kf([12, 30, -12], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.98": kf([26, 0, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 4. CLAW_SWIPE: total=34t (1.70s) | W1=[14..16] (0.70..0.80s), W2=[24..26] (1.20..1.30s)
        "animation.nightmare_knight.claw_swipe": {
            "animation_length": 1.70,
            "bones": {
                "pelvis": {"rotation": {"0.0": kf([0, 0, 0]), "0.52": kf([4, -22, 0], "easeInCubic"), "0.75": kf([8, 22, 0], "easeOutExpo"), "1.02": kf([6, 24, 0], "easeInCubic"), "1.25": kf([10, -24, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.55": kf([10, -34, 0], "easeInCubic"), "0.75": kf([16, 38, 0], "easeOutExpo"), "1.05": kf([12, 40, 0], "easeInCubic"), "1.25": kf([18, -42, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.55": kf([-82, 28, 38], "easeInCubic"), "0.75": kf([-56, -50, -14], "easeOutExpo"), "1.05": kf([-74, -46, -12], "easeInCubic"), "1.25": kf([-84, 36, 36], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([4, -18, 8], "easeOutExpo"), "1.25": kf([-4, 14, -6], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([4, 18, -8], "easeOutExpo"), "1.25": kf([-4, -14, 6], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([14, -16, 0]), "1.28": kf([16, 18, 0]), "1.70": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 5. LEAP_SMASH (Winged Dive): total=48t (2.40s) | Crouch 0.70s -> Wing Flap Lift 1.25s -> Land Impact=[34..37] (1.70..1.85s)
        "animation.nightmare_knight.leap": {
            "animation_length": 2.40,
            "bones": {
                "body": {"position": {"0.0": kf([0, 0, 0]), "0.70": kf([0, -4.2, 0], "easeInCubic"), "1.25": kf([0, 5.4, 0], "easeOutCubic"), "1.70": kf([0, -3.6, 0], "easeOutExpo"), "1.95": kf([0, -2.4, 0], "easeOutCubic"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([26, 0, 0], "easeInCubic"), "1.25": kf([-22, 0, 0], "easeOutCubic"), "1.70": kf([36, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.25": kf([-152, 0, -14], "easeOutCubic"), "1.70": kf([28, 0, -4], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                # Vỗ mạnh đôi cánh xòe ngang bốc người lên không!
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-18, 22, -24], "easeInCubic"), "1.10": kf([22, -26, 26], "easeOutExpo"), "1.45": kf([-16, 20, -20], "easeInOutSine"), "1.70": kf([16, -16, 12], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-18, -22, 24], "easeInCubic"), "1.10": kf([22, 26, -26], "easeOutExpo"), "1.45": kf([-16, -20, 20], "easeInOutSine"), "1.70": kf([16, 16, -12], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                # Chân thú chữ Z nén cực sâu -> duỗi tung trên không -> tiếp đất đàn hồi!
                "leg_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-24, 0, 0], "easeInCubic"), "1.25": kf([24, 0, 0], "easeOutCubic"), "1.70": kf([-18, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([32, 0, 0], "easeInCubic"), "1.25": kf([-14, 0, 0], "easeOutCubic"), "1.70": kf([24, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-26, 0, 0], "easeInCubic"), "1.25": kf([22, 0, 0], "easeOutCubic"), "1.70": kf([-20, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "leg_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-24, 0, 0], "easeInCubic"), "1.25": kf([24, 0, 0], "easeOutCubic"), "1.70": kf([-18, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([32, 0, 0], "easeInCubic"), "1.25": kf([-14, 0, 0], "easeOutCubic"), "1.70": kf([24, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-26, 0, 0], "easeInCubic"), "1.25": kf([22, 0, 0], "easeOutCubic"), "1.70": kf([-20, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "1.25": kf([26, 0, 0], "easeOutCubic"), "1.78": kf([36, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([0, 0, 0]), "1.35": kf([22, 0, 0]), "1.85": kf([26, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 6. BONE_SPIKE_ERUPTION: total=34t (1.70s) | Impact=[19..20] (0.95s)
        "animation.nightmare_knight.spike_eruption": {
            "animation_length": 1.70,
            "bones": {
                "pelvis": {"position": {"0.0": kf([0, 0, 0]), "0.78": kf([0, 0.5, 0], "easeInCubic"), "0.95": kf([0, -2.2, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([-20, 0, 0], "easeInCubic"), "0.95": kf([26, 0, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([-110, 0, -12], "easeInCubic"), "0.95": kf([22, 0, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([-74, 0, 24], "easeInCubic"), "0.95": kf([18, 0, 10], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([-10, 18, -14], "easeInCubic"), "0.95": kf([10, -12, 8], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.78": kf([-10, -18, 14], "easeInCubic"), "0.95": kf([10, 12, -8], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.98": kf([22, 0, 0], "easeOutExpo"), "1.70": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 7. SWORD_WAVE & FLAME_SWORD_WAVE: total=32t (1.60s) | Impact=[18..19] (0.90s)
        "animation.nightmare_knight.sword_wave": {
            "animation_length": 1.60,
            "bones": {
                "pelvis": {"rotation": {"0.0": kf([0, 0, 0]), "0.72": kf([4, 24, 0], "easeInCubic"), "0.90": kf([8, -24, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.74": kf([6, 44, 0], "easeInCubic"), "0.90": kf([14, -48, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.74": kf([-46, 36, -46], "easeInCubic"), "0.90": kf([-64, -52, 20], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.74": kf([4, -18, 6], "easeInCubic"), "0.90": kf([-8, 16, -10], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.74": kf([4, 18, -6], "easeInCubic"), "0.90": kf([-8, -16, 10], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.95": kf([18, 20, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 8. BONE_CAGE: total=36t (1.80s) | Impact=[20..21] (1.00s)
        "animation.nightmare_knight.cage": {
            "animation_length": 1.80,
            "bones": {
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.82": kf([-16, 0, 0], "easeInCubic"), "1.00": kf([14, 0, 0], "easeOutExpo"), "1.80": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.82": kf([-122, 0, 26], "easeInCubic"), "1.00": kf([-32, 0, 42], "easeOutExpo"), "1.80": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "0.95": kf([38, 0, 0], "easeOutCubic"), "1.80": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.82": kf([-12, 20, -14], "easeInCubic"), "1.80": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.82": kf([-12, -20, 14], "easeInCubic"), "1.80": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 9. NIGHTMARE_COMBO: total=76t (3.80s) | W1=[16..18](0.80s), W2=[30..32](1.50s), W3=[44..46](2.20s), W4=[60..63](3.00s)
        "animation.nightmare_knight.combo": {
            "animation_length": 3.80,
            "bones": {
                "pelvis": {
                    "rotation": {
                        "0.0": kf([0, 0, 0]),
                        "0.62": kf([4, 22, 0], "easeInCubic"), "0.82": kf([8, -24, 0], "easeOutExpo"),
                        "1.32": kf([6, -24, 0], "easeInCubic"), "1.52": kf([8, 24, 0], "easeOutExpo"),
                        "2.02": kf([6, 26, 0], "easeInCubic"), "2.22": kf([10, -28, 0], "easeOutExpo"),
                        "2.78": kf([-8, 0, 0], "easeInCubic"), "3.02": kf([14, 0, 0], "easeOutExpo"),
                        "3.80": kf([0, 0, 0], "easeInOutSine"),
                    },
                },
                "chest": {
                    "rotation": {
                        "0.0": kf([0, 0, 0]),
                        "0.62": kf([8, 48, 0], "easeInCubic"), "0.82": kf([14, -52, 0], "easeOutExpo"),
                        "1.32": kf([10, -48, 0], "easeInCubic"), "1.52": kf([14, 52, 0], "easeOutExpo"),
                        "2.02": kf([10, 52, 0], "easeInCubic"), "2.22": kf([16, -56, 0], "easeOutExpo"),
                        "2.78": kf([-28, 0, 0], "easeInCubic"), "3.02": kf([36, 12, 0], "easeOutExpo"),
                        "3.80": kf([0, 0, 0], "easeInOutSine"),
                    },
                },
                "arm_r": {
                    "rotation": {
                        "0.0": kf([0, 0, 0]),
                        "0.62": kf([-38, 34, -46], "easeInCubic"), "0.82": kf([-64, -56, 20], "easeOutExpo"),
                        "1.32": kf([-64, -54, 22], "easeInCubic"), "1.52": kf([-66, 48, -42], "easeOutExpo"),
                        "2.02": kf([-54, 44, -46], "easeInCubic"), "2.22": kf([-72, -58, 24], "easeOutExpo"),
                        "2.78": kf([-154, 0, -14], "easeInCubic"), "3.02": kf([28, 0, 0], "easeOutExpo"),
                        "3.80": kf([0, 0, 0], "easeInOutSine"),
                    },
                },
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.82": kf([-4, 14, -6], "easeOutExpo"), "2.78": kf([-16, 24, -16], "easeInCubic"), "3.02": kf([14, -16, 10], "easeOutExpo"), "3.80": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.82": kf([-4, -14, 6], "easeOutExpo"), "2.78": kf([-16, -24, 16], "easeInCubic"), "3.02": kf([14, 16, -10], "easeOutExpo"), "3.80": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.86": kf([16, 22, 0]), "1.56": kf([16, -22, 0]), "2.26": kf([18, 24, 0]), "3.08": kf([28, 0, 0]), "3.80": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 10. SPIN_SLASH: total=38t (1.90s) | W1=[16..19](0.80..0.95s), W2=[24..27](1.20..1.35s)
        "animation.nightmare_knight.spin_slash": {
            "animation_length": 1.90,
            "bones": {
                "body": {"rotation": {"0.0": kf([0, 0, 0]), "0.62": kf([0, 68, 0], "easeInCubic"), "0.95": kf([0, -155, 0], "easeOutCubic"), "1.35": kf([0, 165, 0], "linear"), "1.66": kf([0, 36, 0], "easeOutCubic"), "1.90": kf([0, 0, 0], "easeOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.68": kf([-72, 0, -72], "easeInCubic"), "1.40": kf([-72, 0, -76], "easeOutCubic"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.68": kf([-48, 0, 66], "easeInCubic"), "1.40": kf([-48, 0, 66], "easeOutCubic"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.68": kf([10, -28, 12], "easeInCubic"), "1.40": kf([10, -28, 12], "easeOutCubic"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.68": kf([10, 28, -12], "easeInCubic"), "1.40": kf([10, 28, -12], "easeOutCubic"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.85": kf([26, 24, 14]), "1.40": kf([28, 28, 16]), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([0, 0, 0]), "0.95": kf([24, 20, 12]), "1.50": kf([26, 22, 14]), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "cape_lower": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([22, 18, 10]), "1.60": kf([24, 18, 12]), "1.90": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 11. SPIKE_STORM: total=56t (2.80s) | Pulses=[16, 30, 44] (0.80s, 1.50s, 2.20s)
        "animation.nightmare_knight.storm": {
            "animation_length": 2.80,
            "bones": {
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-20, 0, 0], "easeInOutSine"), "2.20": kf([-14, 0, 0], "easeInOutSine"), "2.80": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-144, 0, -14], "easeOutCubic"), "2.20": kf([-130, 0, -18], "easeInOutSine"), "2.80": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-128, 0, 22], "easeOutCubic"), "2.20": kf([-116, 0, 26], "easeInOutSine"), "2.80": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([38, 0, 0], "easeOutCubic"), "2.20": kf([34, 0, 0], "easeInOutSine"), "2.80": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-16, 22, -16], "easeOutCubic"), "2.20": kf([-10, 18, -14], "easeInOutSine"), "2.80": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-16, -22, 16], "easeOutCubic"), "2.20": kf([-10, -18, 14], "easeInOutSine"), "2.80": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 12. NIGHTMARE_REALM: total=38t (1.90s) | Impact=[20..21] (1.00s)
        "animation.nightmare_knight.realm": {
            "animation_length": 1.90,
            "bones": {
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([18, 0, 0], "easeInCubic"), "1.00": kf([-26, 0, 0], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([-24, 0, -16], "easeInCubic"), "1.00": kf([-78, 0, 68], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([-24, 0, 16], "easeInCubic"), "1.00": kf([-78, 0, -68], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "head": {"rotation": {"0.0": kf([0, 0, 0]), "1.00": kf([-28, 0, 0], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "1.00": kf([42, 0, 0], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([10, -32, 14], "easeInCubic"), "1.00": kf([-16, 26, -20], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.80": kf([10, 32, -14], "easeInCubic"), "1.00": kf([-16, -26, 20], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([26, 0, 0], "easeOutExpo"), "1.90": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 13. ULTIMATE NIGHTMARE_DESCENT: total=84t (4.20s) | Slam=[48..51](2.40s), Waves=[58..61](2.90s), [68..71](3.40s)
        "animation.nightmare_knight.ultimate": {
            "animation_length": 4.20,
            "bones": {
                "body": {"position": {"0.0": kf([0, 0, 0]), "2.20": kf([0, -2.8, 0], "easeInCubic"), "2.40": kf([0, 0.4, 0], "easeOutExpo"), "3.40": kf([0, -1.8, 0], "easeOutCubic"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([-30, 0, 0], "easeInCubic"), "2.40": kf([38, 0, 0], "easeOutExpo"), "3.45": kf([34, 0, 0], "easeOutCubic"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([-158, 0, -14], "easeInCubic"), "2.40": kf([28, 0, 0], "easeOutExpo"), "3.45": kf([28, 0, 0], "easeOutCubic"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([-142, 0, 22], "easeInCubic"), "2.40": kf([24, 0, 14], "easeOutExpo"), "3.45": kf([24, 0, 14], "easeOutCubic"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "head": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([-26, 0, 0], "easeInCubic"), "2.40": kf([18, 0, 0], "easeOutExpo"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([42, 0, 0], "easeInCubic"), "3.40": kf([32, 0, 0], "easeOutCubic"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([-20, 28, -22], "easeInCubic"), "2.40": kf([18, -14, 10], "easeOutExpo"), "3.45": kf([-12, 20, -14], "easeOutCubic"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([-20, -28, 22], "easeInCubic"), "2.40": kf([18, 14, -10], "easeOutExpo"), "3.45": kf([-12, -20, 14], "easeOutCubic"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "2.45": kf([32, 0, 0], "easeOutExpo"), "4.20": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 14. PHASE_CHANGE: total=120t (6.00s)
        "animation.nightmare_knight.phase_change": {
            "animation_length": 6.00,
            "bones": {
                "body": {"position": {"0.0": kf([0, 0, 0]), "1.75": kf([0, -4.2, 0], "easeInCubic"), "2.20": kf([0, 0.8, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([26, 0, 0], "easeInCubic"), "2.20": kf([-4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([26, 0, 0], "easeInCubic"), "2.20": kf([-4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-22, 0, 0], "easeInCubic"), "2.20": kf([4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "ankle_hock_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-22, 0, 0], "easeInCubic"), "2.20": kf([4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([26, 0, 0], "easeInCubic"), "2.20": kf([-30, 0, 0], "easeOutExpo"), "4.8": kf([-18, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "head": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([22, 0, 0], "easeInCubic"), "2.20": kf([-28, 0, 0], "easeOutExpo"), "4.8": kf([-22, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([44, 0, 0], "easeOutExpo"), "4.8": kf([38, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-25, 0, -16], "easeInCubic"), "2.20": kf([-72, 0, 64], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-25, 0, 16], "easeInCubic"), "2.20": kf([-90, 0, -54], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([10, -34, 14], "easeInCubic"), "2.20": kf([-20, 28, -24], "easeOutExpo"), "4.8": kf([-14, 22, -18], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([10, 34, -14], "easeInCubic"), "2.20": kf([-20, -28, 24], "easeOutExpo"), "4.8": kf([-14, -22, 18], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "2.25": kf([34, 0, 0], "easeOutExpo"), "4.8": kf([24, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 15. DEATH: 2.40s — khuỵu chân chữ Z rồi đổ gục nặng nề
        "animation.nightmare_knight.death": {
            "loop": "hold_on_last_frame",
            "animation_length": 2.40,
            "bones": {
                "body": {
                    "position": {"0.0": kf([0, 0, 0]), "1.1": kf([0, -5.5, 0], "easeInCubic"), "2.4": kf([0, -13.0, 0], "easeOutBounce")},
                    "rotation": {"0.0": kf([0, 0, 0]), "1.1": kf([28, 0, 0], "easeInCubic"), "2.4": kf([86, 0, 0], "easeOutCubic")},
                },
                "head": {"rotation": {"0.0": kf([0, 0, 0]), "2.4": kf([28, 0, 0], "easeOutCubic")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "2.4": kf([-40, 0, 32], "easeOutCubic")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "2.4": kf([-25, 0, -32], "easeOutCubic")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "2.4": kf([24, -40, 22], "easeOutCubic")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "2.4": kf([24, 40, -22], "easeOutCubic")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "2.4": kf([-24, 0, 0], "easeOutCubic")}},
            },
        },
    }

    max_rot = 0.0
    for name, anim in anims.items():
        for bname, ch in anim.get("bones", {}).items():
            for t, entry in ch.get("rotation", {}).items():
                vec = entry["vector"] if isinstance(entry, dict) else entry
                for v in vec:
                    assert abs(v) <= 170.0, f"Abnormal rot {v} in {name}.{bname}@{t}"
                    max_rot = max(max_rot, abs(v))

    out = ASSETS / "animations" / "entity" / "nightmare_knight.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=2)
    print(f"ANIM_OK: {out} ({len(anims)} Pacific Rim eased animations, max |rot|={max_rot:.1f} deg)")
    return anims


# =============================================================================
# 4. BỘ RENDER PREVIEW 3D Z-BUFFER ĐỦ 6 GÓC (TRƯỚC, SAU, TRÁI, PHẢI, TRÊN XUỐNG, PHASE 2)
# =============================================================================
def transform_affine(R, t):
    M = np.eye(4, dtype=np.float64)
    M[:3, :3] = R
    M[:3, 3] = t
    return M


def rot_about_pivot(pivot, rot_deg):
    p = np.array(pivot, dtype=np.float64)
    R = bedrock_rot_matrix(rot_deg)
    return transform_affine(R, p - R @ p)


def collect_world_geometry(geo: dict):
    bones_list = geo["minecraft:geometry"][0]["bones"]
    by_name = {b["name"]: b for b in bones_list}
    world_M = {}

    def get_bone_M(name):
        if name in world_M:
            return world_M[name]
        b = by_name[name]
        local = rot_about_pivot(b.get("pivot", [0, 0, 0]), b.get("rotation", [0, 0, 0]))
        parent = b.get("parent")
        M = (get_bone_M(parent) @ local) if parent else local
        world_M[name] = M
        return M

    faces = []
    all_pts = []
    for b in bones_list:
        if not b.get("cubes"):
            continue
        bM = get_bone_M(b["name"])
        for c in b["cubes"]:
            cM = bM @ (rot_about_pivot(c["pivot"], c["rotation"]) if "rotation" in c else np.eye(4))
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            corners = np.array([
                [ox, oy, oz, 1], [ox + sx, oy, oz, 1], [ox + sx, oy + sy, oz, 1], [ox, oy + sy, oz, 1],
                [ox, oy, oz + sz, 1], [ox + sx, oy, oz + sz, 1], [ox + sx, oy + sy, oz + sz, 1], [ox, oy + sy, oz + sz, 1],
            ], dtype=np.float64)
            pts = (cM @ corners.T).T[:, :3]
            all_pts.append(pts)
            uv_map = c["uv"]
            for fname, idxs in (
                ("north", [0, 1, 2, 3]), ("south", [5, 4, 7, 6]),
                ("west", [4, 0, 3, 7]), ("east", [1, 5, 6, 2]),
                ("up", [3, 2, 6, 7]), ("down", [4, 5, 1, 0]),
            ):
                faces.append((pts[idxs], uv_map[fname]))

    stacked = np.vstack(all_pts)
    return faces, stacked.min(axis=0), stacked.max(axis=0)


def rasterize_alpha_cutout(faces, diff_img, glow_img, view_mat, width, height, scale, center, bg=(22, 23, 30)):
    fb = np.zeros((height, width, 3), dtype=np.float32)
    fb[:, :] = bg
    zbuf = np.full((height, width), -1e9, dtype=np.float32)

    light_dir = np.array([0.36, 0.72, -0.58], dtype=np.float64)
    light_dir /= np.linalg.norm(light_dir)
    cx, cy = width * 0.5, height * 0.52

    def raster_tri(p0, p1, p2, uv0, uv1, uv2, shade):
        min_x = max(0, int(math.floor(min(p0[0], p1[0], p2[0]))))
        max_x = min(width - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
        min_y = max(0, int(math.floor(min(p0[1], p1[1], p2[1]))))
        max_y = min(height - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
        if min_x > max_x or min_y > max_y:
            return
        area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
        if abs(area) < 1e-4:
            return
        inv = 1.0 / area
        gx, gy = np.meshgrid(np.arange(min_x, max_x + 1, dtype=np.float32) + 0.5,
                             np.arange(min_y, max_y + 1, dtype=np.float32) + 0.5)
        w0 = ((p1[0] - gx) * (p2[1] - gy) - (p2[0] - gx) * (p1[1] - gy)) * inv
        w1 = ((p2[0] - gx) * (p0[1] - gy) - (p0[0] - gx) * (p2[1] - gy)) * inv
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
        if not np.any(inside):
            return
        z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
        sub_z = zbuf[min_y:max_y + 1, min_x:max_x + 1]
        cand = inside & (z > sub_z)
        if not np.any(cand):
            return

        u = np.clip((w0[cand] * uv0[0] + w1[cand] * uv1[0] + w2[cand] * uv2[0]).astype(np.int32), 0, TEX_W - 1)
        v = np.clip((w0[cand] * uv0[1] + w1[cand] * uv1[1] + w2[cand] * uv2[1]).astype(np.int32), 0, TEX_H - 1)

        alpha_pass = diff_img[v, u, 3] >= 128.0
        if not np.any(alpha_pass):
            return

        cy_idx, cx_idx = np.where(cand)
        cy_idx = cy_idx[alpha_pass]
        cx_idx = cx_idx[alpha_pass]
        u = u[alpha_pass]
        v = v[alpha_pass]

        sub_z[cy_idx, cx_idx] = z[cy_idx, cx_idx]
        d_col = diff_img[v, u, :3]
        g_col = glow_img[v, u, :3]
        g_a = glow_img[v, u, 3:4] / 255.0
        lit = d_col * shade * (1.0 - g_a * 0.85) + g_col * (g_a * 1.15)
        fb[min_y + cy_idx, min_x + cx_idx] = np.clip(lit, 0, 255)

    for quad, uv in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = 0.42 + 0.58 * abs(float(np.dot(n, light_dir)))
        scr = np.stack([cx - cam[:, 0] * scale, cy - cam[:, 1] * scale, cam[:, 2]], axis=1)
        u0, v0 = uv["uv"]
        uw, uh = uv["uv_size"]
        u1, v1 = u0 + max(1, uw) - 0.01, v0 + max(1, uh) - 0.01
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        raster_tri(scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
        raster_tri(scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)

    return Image.fromarray(fb.astype(np.uint8), "RGB")


def render_previews(geo: dict) -> None:
    tex_dir = ASSETS / "textures" / "entity"
    d_norm = np.array(Image.open(tex_dir / "nightmare_knight.png").convert("RGBA"), dtype=np.float32)
    g_norm = np.array(Image.open(tex_dir / "nightmare_knight_glowmask.png").convert("RGBA"), dtype=np.float32)
    d_enr = np.array(Image.open(tex_dir / "nightmare_knight_enraged.png").convert("RGBA"), dtype=np.float32)
    g_enr = np.array(Image.open(tex_dir / "nightmare_knight_enraged_glowmask.png").convert("RGBA"), dtype=np.float32)

    faces, bmin, bmax = collect_world_geometry(geo)
    center = (bmin + bmax) * 0.5
    span = float(np.max(bmax - bmin))

    def cam_mat(yaw_deg, pitch_deg):
        y, p = math.radians(yaw_deg), math.radians(pitch_deg)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
        Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64)
        return Rx @ Ry

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 640, 520
    sc = (min(pw, ph) * 0.86) / max(1.0, span)

    specs = [
        ("preview_front_full.png",    180,  4, sc * 1.15, center, d_norm, g_norm, "FRONT (BRIGHT SKULL, STEEL PLATES, BALANCED SWORD, WIDE A-STANCE)"),
        ("preview_rear_full.png",       0,  4, sc * 1.15, center, d_norm, g_norm, "REAR (WIDE 3-TIER A-LINE RED CAPE, SWEPT BAT WINGS, CLAW HOOKS)"),
        ("preview_side_left.png",     -90,  4, sc * 1.25, np.array([0.0, 23.0, 0.0]), d_norm, g_norm, "LEFT SIDE (Z-DIGITIGRADE LEGS, KNEE SPUR, HOCK, 24-DEG WINGS)"),
        ("preview_side_right.png",     90,  4, sc * 1.25, np.array([0.0, 23.0, 0.0]), d_norm, g_norm, "RIGHT SIDE (Z-LEGS, NATURAL SWORD ANGLE, LONG SWEEPING CAPE)"),
        ("preview_top_down.png",      180, 84, sc * 1.12, center, d_norm, g_norm, "TOP-DOWN (WIDE HORIZONTAL WING SPAN ~24 DEG RAKE, SWORD & CAPE)"),
        ("preview_phase2_enraged.png", 150, 10, sc * 1.16, center, d_enr, g_enr, "PHASE 2 ENRAGED (MOLTEN ARMOR FISSURES, BLAZING BLOOD GREATSWORD)"),
    ]
    imgs = []
    for fn, yw, pt, s_c, ctr, d_i, g_i, title in specs:
        im = rasterize_alpha_cutout(faces, d_i, g_i, cam_mat(yw, pt), pw, ph, s_c, ctr)
        ImageDraw.Draw(im).text((10, 10), title, fill=(255, 210, 200))
        im.save(PREVIEW_DIR / fn)
        imgs.append(im)

    sheet = Image.new("RGB", (pw * 3, ph * 2), (14, 15, 20))
    for i, im in enumerate(imgs):
        sheet.paste(im, ((i % 3) * pw, (i // 3) * ph))
    sheet.save(PREVIEW_DIR / "nightmare_preview_sheet.png")
    print(
        f"PREVIEW_OK: 6 views + sheet saved to {PREVIEW_DIR} | "
        f"bbox X=[{bmin[0]:.1f}..{bmax[0]:.1f}] Y=[{bmin[1]:.1f}..{bmax[1]:.1f}] Z=[{bmin[2]:.1f}..{bmax[2]:.1f}]"
    )


if __name__ == "__main__":
    backup_existing_files()
    geo_data, total_cubes, meta = build_nightmare_geo()
    build_textures()
    build_animations()
    render_previews(geo_data)
    print(f"ALL_DONE: Nightmare Knight v3 rebuilt ({meta['bones']} bones, {total_cubes} cubes, {TEX_W}x{TEX_H}, sword_tip={meta['sword_tip']})")
