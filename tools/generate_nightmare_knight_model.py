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
    "CLAW_BLOOD":    (768, 896, 128, 128),  # Móng vuốt & móc đỉnh cánh đen-đỏ máu bóng
    "HEART_OUTER":   (896, 896,  64, 128),  # Vỏ cơ tim đỏ huyết thẩm gân mạch của Nightmare Core
    "HEART_CORE":    (960, 896,  64, 128),  # Lõi trái tim Nightmare Core hồng san hô rực sáng fullbright
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


def make_centered_cube(center, size, mat: str, rot=None, **kw) -> dict:
    cx, cy, cz = float(center[0]), float(center[1]), float(center[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    return make_cube([cx - sx * 0.5, cy - sy * 0.5, cz - sz * 0.5], size, mat, rot=rot, pivot=[cx, cy, cz], **kw)


def add_beveled_armor_block(out_list: list, center, w: float, h: float, d: float,
                            mat_main: str = "ARMOR_STEEL", mat_trim: str = "ARMOR_SPIKE",
                            rot_x: float = 0.0) -> None:
    """Dựng 1 đốt giáp ngực/đùi/ống chân bát giác vát cạnh nhiều tầng (loại bỏ cảm giác hộp vuông thô cứng!)."""
    cx, cy, cz = float(center[0]), float(center[1]), float(center[2])
    out_list.append(make_centered_cube([cx, cy, cz], [w * 0.86, h, d * 0.88], mat_main, rot=[rot_x, 0.0, 0.0]))
    out_list.append(make_centered_cube([cx, cy, cz - d * 0.36], [w * 0.62, h * 0.94, d * 0.34], mat_trim, rot=[rot_x, 0.0, 0.0]))
    out_list.append(make_centered_cube([cx, cy, cz + d * 0.36], [w * 0.66, h * 0.92, d * 0.32], mat_main, rot=[rot_x, 0.0, 0.0]))
    for sx in (-1.0, 1.0):
        out_list.append(make_centered_cube([cx + sx * (w * 0.40), cy, cz], [w * 0.28, h * 0.92, d * 0.68], mat_main,
                                           rot=[rot_x, sx * 12.0, 0.0]))
        out_list.append(make_centered_cube([cx + sx * (w * 0.35), cy, cz - d * 0.28], [w * 0.26, h * 0.88, d * 0.34], mat_trim,
                                           rot=[rot_x, -sx * 24.0, 0.0]))


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
# 1. XÂY DỰNG TOÀN BỘ HÌNH HỌC NIGHTMARE KNIGHT v5 (HỮU CƠ MỀM MẠI, LỒNG NGỰC MỞ BỌC TRÁI TIM, CHÂN GỌN LỰC LƯỠNG!)
#    - Bỏ hoàn toàn dáng xếp tầng hộp cứng kiểu robot/nhựa -> dùng mảng cong hữu cơ, cơ bắp gân guốc & giáp rèn gothic mềm mại
#    - Giảm chiều dài chân (~22.8 px thay vì 27.2 px) cho tỷ lệ chiến thần cân đối uy nghi (đỉnh sọ ~52.8 px, đỉnh cánh ~60.8 px)
#    - Lồng ngực mở toang lộ khung xương sườn cong ôm bọc Trái Tim Nightmare Core 3D phát sáng đập nhịp (`heart` bone!)
# =============================================================================
def build_nightmare_geo() -> tuple[dict, int, dict]:
    bones = []

    bones.append({"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})
    bones.append({"name": "body", "parent": "root", "pivot": [0.0, 23.2, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})

    # -------------------------------------------------------------------------
    # A. HÔNG / ĐAI GOTHIC HỮU CƠ & DẢI VẢI HÔNG MỀM (`pelvis`, `tail`, `tabard` -> `tabard_lower`)
    # -------------------------------------------------------------------------
    pelvis_cubes = [
        # Lõi hông hữu cơ bọc giáp thép rèn ôm gọn
        make_centered_cube([0.0, 23.6, 0.3], [11.2, 3.6, 5.8], "ARMOR_STEEL"),
        make_centered_cube([0.0, 21.8, 0.2], [8.4, 2.2, 4.8], "FLESH_DARK"),
        make_centered_cube([0.0, 20.8, -0.1], [4.6, 2.0, 3.8], "ARMOR_STEEL"),
        # Khóa thắt lưng đầu lâu/khiên gothic chạm khắc mềm ở chính giữa
        make_centered_cube([0.0, 24.6, -2.95], [7.2, 1.5, 1.35], "ARMOR_SPIKE"),
        make_centered_cube([0.0, 23.0, -3.35], [4.4, 2.1, 1.35], "ARMOR_SPIKE", rot=[10, 0, 0]),
        make_centered_cube([0.0, 21.3, -3.55], [2.6, 2.0, 1.20], "ARMOR_SPIKE", rot=[16, 0, 0]),
        make_centered_cube([0.0, 23.2, -4.02], [1.45, 1.45, 0.55], "HORN_CRIMSON", rot=[10, 0, 45]),
        make_centered_cube([0.0, 23.2, -4.22], [0.68, 0.68, 0.35], "RUNE_GLOW", rot=[10, 0, 45]),
        # Giáp lưng dưới cong mềm
        make_centered_cube([0.0, 23.8, 3.45], [7.4, 3.2, 1.45], "ARMOR_STEEL", rot=[-10, 0, 0]),
        make_centered_cube([0.0, 21.6, 3.55], [4.8, 2.2, 1.20], "ARMOR_SPIKE", rot=[-14, 0, 0]),
    ]
    # Đai hông vát cong & tấm giáp váy hông (faulds) vát mềm lượn theo đường cong đùi (không hộp ngang!)
    for sx in (1.0, -1.0):
        # Gờ đai xương-thép cong ôm ngang hông
        add_curved_rod(pelvis_cubes, [
            [sx * 1.8, 24.8, -3.15],
            [sx * 4.4, 24.2, -2.55],
            [sx * 6.0, 24.6, +0.10],
            [sx * 4.4, 24.8, +2.85],
        ], 1.35, 1.05, "ARMOR_SPIKE", segs_per_span=2)
        # 3 lớp vảy giáp váy đùi cong vát dọc rủ mềm (giống váy giáp hiệp sĩ bóng đêm)
        pelvis_cubes.append(make_centered_cube([sx * 5.2, 22.0, -0.5], [1.8, 4.4, 5.8], "ARMOR_STEEL", rot=[4, -sx * 10, -sx * 16]))
        pelvis_cubes.append(make_centered_cube([sx * 6.0, 19.0, -0.6], [1.5, 4.2, 4.8], "ARMOR_STEEL", rot=[6, -sx * 12, -sx * 20]))
        pelvis_cubes.append(make_centered_cube([sx * 6.6, 16.4, -0.5], [1.2, 3.4, 3.4], "ARMOR_SPIKE", rot=[8, -sx * 14, -sx * 24]))
        # 2 vạt vải đỏ rách phủ mềm hai bên hông (làm dáng mềm mại & giàu chi tiết!)
        add_membrane_bay_3d(pelvis_cubes,
                            [sx * 4.6, 23.8, -2.4], [sx * 5.6, 23.6, +2.2],
                            [sx * 7.2, 13.8, -3.0], [sx * 8.2, 14.2, +3.0],
                            4, "CLOTH_TATTER", (0.12, 0.88), (0.18, 0.96), 0.22, pleat_amp=0.28)
        # Gai xương cong tự nhiên mọc bên đai hông
        add_curved_rod(pelvis_cubes, [
            [sx * 5.4, 24.6, -1.6],
            [sx * 7.0, 25.8, -2.1],
            [sx * 7.8, 27.2, -2.2],
        ], 0.95, 0.24, "BONE_RIB", segs_per_span=2)

    bones.append({"name": "pelvis", "parent": "body", "pivot": [0.0, 23.2, 0.3], "rotation": [0.0, 0.0, 0.0], "cubes": pelvis_cubes})

    tail_cubes = []
    add_curved_rod(tail_cubes, [
        [0.0, 22.6, 3.0],
        [0.0, 20.6, 6.2],
        [0.0, 18.8, 8.8],
        [0.0, 17.6, 10.6],
    ], 1.70, 0.38, "ARMOR_STEEL", segs_per_span=2)
    bones.append({"name": "tail", "parent": "pelvis", "pivot": [0.0, 22.6, 3.0], "rotation": [0.0, 0.0, 0.0], "cubes": tail_cubes})

    # KHỐ VẢI ĐỎ RÁCH 2 ĐỐT (`tabard` -> `tabard_lower`) mềm mại giữa 2 đùi!
    tabard_cubes = []
    add_membrane_bay_3d(tabard_cubes, [-2.5, 22.8, -3.55], [2.5, 22.8, -3.55],
                        [-2.9, 14.8, -4.05], [2.9, 14.8, -4.05], 6, "CLOTH_TATTER", (0.04, 0.96), (0.02, 0.54), 0.24, pleat_amp=0.34)
    add_membrane_bay_3d(tabard_cubes, [-2.3, 23.0, -3.80], [2.3, 23.0, -3.80],
                        [-2.5, 17.8, -4.15], [2.5, 17.8, -4.15], 4, "CLOTH_TATTER", (0.10, 0.90), (0.02, 0.36), 0.22, pleat_amp=0.20)
    bones.append({"name": "tabard", "parent": "pelvis", "pivot": [0.0, 22.8, -3.5], "rotation": [0.0, 0.0, 0.0], "cubes": tabard_cubes})

    tabard_lo_c = []
    add_membrane_bay_3d(tabard_lo_c, [-2.9, 15.0, -4.05], [2.9, 15.0, -4.05],
                        [-3.4, 6.8, -4.60], [3.4, 6.8, -4.60], 6, "CLOTH_TATTER", (0.04, 0.96), (0.50, 0.99), 0.24, pleat_amp=0.46, pleat_top_amp=0.34)
    bones.append({"name": "tabard_lower", "parent": "tabard", "pivot": [0.0, 15.0, -4.05], "rotation": [0.0, 0.0, 0.0], "cubes": tabard_lo_c})

    # -------------------------------------------------------------------------
    # B. EO XƯƠNG SƯỜN QUỶ HỮU CƠ (`torso` -> `torso_upper`) — EO GÂN GUỐC LỘ XƯƠNG SƯỜN & MỞ RỘNG MỀM LÊN HỐC TIM!
    # -------------------------------------------------------------------------
    torso_cubes = [
        # Lõi eo cơ bắp tối săn chắc + cột sống xương cổ điển phía sau
        make_centered_cube([0.0, 28.2, 0.3], [5.8, 7.4, 4.8], "FLESH_DARK"),
        # Dải gân cơ bụng tối & sống xương bụng (không còn tấm thép vuông cứng!)
        make_centered_cube([0.0, 29.8, -2.55], [3.2, 2.4, 1.3], "FLESH_DARK", rot=[8, 0, 0]),
        make_centered_cube([0.0, 27.6, -2.65], [2.8, 2.4, 1.2], "ARMOR_STEEL", rot=[6, 0, 0]),
        make_centered_cube([0.0, 25.6, -2.55], [2.4, 2.2, 1.2], "ARMOR_STEEL", rot=[4, 0, 0]),
    ]
    # Đốt sống lưng xương-sắt nhô mềm dọc sống lưng
    for vi in range(5):
        vy = 25.4 + vi * 1.25
        torso_cubes.append(make_centered_cube([0.0, vy, 2.65], [2.4, 0.95, 1.85], "BONE_RIB"))
        add_claw_spike(torso_cubes, [0.0, vy + 0.3, 3.2], [0.0, vy + 1.0, 5.1], 0.78, "ARMOR_SPIKE", 2)

    # 3 cặp xương sườn eo cong 3D tự nhiên bọc quanh eo chữ V
    for yb, yf, wout, rth in (
        (30.8, 29.6, 5.85, 0.94),
        (29.0, 28.0, 5.15, 0.88),
        (27.2, 26.3, 4.45, 0.80),
    ):
        for sx in (1.0, -1.0):
            add_curved_rod(torso_cubes, [
                [sx * 1.35, yb, 2.35],
                [sx * wout, (yb + yf) * 0.5 + 0.10, 0.35],
                [sx * (wout * 0.86), yf, -2.10],
                [sx * 1.45, yf - 0.35, -3.10],
            ], rth, rth * 0.62, "BONE_RIB", segs_per_span=2)
            # Sợi gân máu mảnh chạy dọc giữa xương sườn eo
            add_curved_rod(torso_cubes, [
                [sx * (wout * 0.72), yf + 0.35, -1.80],
                [sx * 1.65, yf + 0.05, -2.85],
            ], 0.36, 0.24, "HORN_CRIMSON", segs_per_span=2)

    bones.append({"name": "torso", "parent": "pelvis", "pivot": [0.0, 25.0, 0.2], "rotation": [0.0, 0.0, 0.0], "cubes": torso_cubes})

    # Đốt lồng ngực dưới (`torso_upper`) — TẠO ĐÁY HỐC TIM MỞ & XƯƠNG SƯỜN DƯỚI BỌC LÊN TRÁI TIM!
    tu_cubes = [
        # Thành lưng sau hốc ngực (khoét rỗng phía trước để hở hẳn quả tim!)
        make_centered_cube([0.0, 33.4, 2.4], [11.8, 5.4, 3.2], "ARMOR_STEEL"),
        make_centered_cube([0.0, 33.4, 0.2], [7.2, 5.2, 2.8], "WING_BONE"),  # Bóng tối sâu bên trong hốc lồng ngực!
    ]
    for sx in (1.0, -1.0):
        # Mạn sườn giáp cong 2 bên mở rộng chữ V (hở trống chính giữa!)
        tu_cubes.append(make_centered_cube([sx * 5.8, 33.4, 0.2], [3.6, 5.6, 6.2], "ARMOR_STEEL", rot=[8, -sx * 16, -sx * 24]))
        tu_cubes.append(make_centered_cube([sx * 6.4, 34.4, -2.0], [2.8, 4.4, 3.4], "ARMOR_STEEL", rot=[12, -sx * 28, -sx * 18]))
        # Cặp xương sườn đáy hốc ngực vươn cong ôm vòng đỡ dưới Trái Tim!
        add_curved_rod(tu_cubes, [
            [sx * 6.6, 32.8, 0.8],
            [sx * 6.4, 32.6, -2.4],
            [sx * 4.5, 32.7, -4.8],
            [sx * 2.1, 33.4, -5.4],
        ], 1.10, 0.56, "BONE_RIB", segs_per_span=2)
        add_curved_rod(tu_cubes, [
            [sx * 5.8, 31.2, 1.0],
            [sx * 5.4, 31.0, -2.0],
            [sx * 3.6, 31.2, -4.2],
            [sx * 1.4, 32.0, -4.6],
        ], 0.96, 0.48, "BONE_RIB", segs_per_span=2)

    bones.append({"name": "torso_upper", "parent": "torso", "pivot": [0.0, 31.0, 0.2], "rotation": [0.0, 0.0, 0.0], "cubes": tu_cubes})

    # -------------------------------------------------------------------------
    # C. LỒNG NGỰC MỞ TOANG CÓ KHUNG XƯƠNG SƯỜN BAO BỌC TRÁI TIM NIGHTMARE CORE (`chest`, `heart`, `spikes_back`)
    #    - Bỏ hoàn toàn khối ngực hộp vuông kiểu robot!
    #    - Hai nửa giáp ngực gothic xé mở sang 2 bên để lộ khoang lồng ngực sâu tối ở chính giữa!
    #    - 5 cặp XƯƠNG SƯỜN CONG HỮU CƠ (`BONE_RIB` -> mũi `SKULL_BONE`) vươn quặp ôm bao bọc TRÁI TIM 3D (`heart` bone)!
    #    - Giữ trọn 2 huy hiệu ngọc đỏ xương quai xanh + mạch máu gân đỏ tỏa ra từ Trái Tim!
    # -------------------------------------------------------------------------
    chest_cubes = [
        # Lưng giáp & thành sau hốc ngực (nền đen sâu phía sau làm Trái Tim phát sáng bật nổi rực rỡ!)
        make_centered_cube([0.0, 39.0, 2.8], [15.4, 8.2, 3.6], "ARMOR_STEEL"),
        make_centered_cube([0.0, 38.4, 0.2], [10.4, 7.6, 3.2], "WING_BONE"),  # Hốc tối sâu ôm lưng Trái Tim!
        # Đỉnh cổ vòm gothic chữ V mềm mại phía trên hốc tim (thay khối thanh ngang thẳng!)
        make_centered_cube([0.0, 42.3, -2.2], [5.4, 1.9, 4.6], "ARMOR_STEEL", rot=[10, 0, 0]),
        make_centered_cube([0.0, 41.9, -4.55], [2.4, 2.0, 1.25], "ARMOR_SPIKE", rot=[14, 0, 45]),
    ]

    for sx in (1.0, -1.0):
        # 1. NỬA GIÁP NGỰC XÉ MỞ DẠNG VỎ GOTHIC CONG THUÔN MỀM (taper từ vai xuống eo, KHÔNG còn tấm hộp chữ nhật!)
        chest_cubes.append(make_centered_cube([sx * 6.4, 40.7, -1.4], [4.3, 3.4, 6.2], "ARMOR_STEEL", rot=[8, -sx * 26, -sx * 24]))
        chest_cubes.append(make_centered_cube([sx * 6.0, 37.9, -1.3], [3.8, 3.6, 5.6], "ARMOR_STEEL", rot=[10, -sx * 24, -sx * 18]))
        chest_cubes.append(make_centered_cube([sx * 5.4, 35.4, -1.2], [3.1, 3.0, 4.8], "ARMOR_STEEL", rot=[12, -sx * 22, -sx * 12]))
        # Vòm xương đòn cong uốn mượt nối cổ ra hai vai
        add_curved_rod(chest_cubes, [
            [sx * 1.4, 42.1, -4.2],
            [sx * 4.4, 42.8, -3.6],
            [sx * 7.8, 42.6, -1.8],
        ], 1.25, 0.95, "ARMOR_SPIKE", segs_per_span=2)
        # Đường viền thép rèn chữ C uốn cong ôm trọn mép xé mở của lồng ngực (xóa sạch nét cạnh thẳng!)
        add_curved_rod(chest_cubes, [
            [sx * 4.2, 42.2, -4.5],
            [sx * 5.6, 40.0, -4.9],
            [sx * 5.5, 37.5, -4.7],
            [sx * 4.7, 35.1, -4.3],
            [sx * 3.2, 33.4, -3.8],
        ], 1.08, 0.68, "ARMOR_SPIKE", segs_per_span=2)
        # 2 vây giáp ngực cong vát mảnh chồng lên sườn giáp (tăng độ mềm & chi tiết thủ công!)
        for k, (py, pz, rotz) in enumerate(((40.2, -4.3, -22), (37.4, -4.4, -14))):
            chest_cubes.append(make_centered_cube([sx * (6.2 - k * 0.45), py, pz], [3.4, 1.55, 1.35],
                                                  "ARMOR_SPIKE" if k == 0 else "ARMOR_STEEL",
                                                  rot=[12, -sx * 30, sx * rotz]))

        # 2. 4 CẶP XƯƠNG SƯỜN CONG HỮU CƠ BAO BỌC KHUNG LỒNG NGỰC MỞ QUANH TRÁI TIM!
        #    (Mọc cong từ nửa giáp ngực -> uốn nhô ra trước `Z = -6.2` -> quặp vuốt xương ngà sáng ôm lấy Trái Tim nhưng CHỪA HỞ MẶT TIM!)
        cage_ribs = [
            # (y_root, y_mid, y_tip, z_mid, z_tip, x_tip, r_start, r_end)
            (41.2, 40.9, 40.2, -5.6, -6.15, 2.35, 1.05, 0.44),
            (39.4, 39.2, 38.8, -6.0, -6.50, 2.75, 1.14, 0.48),
            (37.6, 37.5, 37.4, -5.9, -6.40, 2.65, 1.10, 0.46),
            (35.8, 35.8, 36.1, -5.4, -5.90, 2.15, 1.02, 0.42),
        ]
        for yr, ym, yt, zm, zt, xt, rs, re in cage_ribs:
            p_root = [sx * 7.2, yr, -1.8]
            p_bend = [sx * 5.4, ym, zm]
            p_tip  = [sx * xt,  yt, zt]
            add_curved_rod(chest_cubes, [p_root, p_bend], rs, rs * 0.80, "BONE_RIB", segs_per_span=2)
            add_curved_rod(chest_cubes, [p_bend, p_tip],  rs * 0.78, re, "SKULL_BONE", segs_per_span=2)

        # Vòm xương ức cong trên & dưới viền khung hốc tim (kết nối khung xương như lồng ngực thật!)
        add_curved_rod(chest_cubes, [
            [sx * 5.8, 41.8, -4.4],
            [sx * 3.0, 41.9, -5.3],
            [sx * 1.1, 41.4, -5.4],
        ], 0.92, 0.50, "BONE_RIB", segs_per_span=2)

        # 3. 2 HUY HIỆU NGỌC ĐỎ TRÒN TRÊN XƯƠNG QUAI XANH (gọn đẹp hài hòa phía trên khung tim!)
        chest_cubes.append(make_centered_cube([sx * 6.0, 41.6, -5.45], [2.75, 2.75, 1.05], "ARMOR_SPIKE", rot=[6, -sx * 20, 45]))
        chest_cubes.append(make_centered_cube([sx * 6.0, 41.6, -5.70], [2.25, 2.25, 0.95], "HORN_CRIMSON", rot=[6, -sx * 20, 0]))
        chest_cubes.append(make_centered_cube([sx * 6.0, 41.6, -5.78], [1.95, 1.95, 0.90], "HORN_CRIMSON", rot=[6, -sx * 20, 45]))
        chest_cubes.append(make_centered_cube([sx * 6.0, 41.6, -6.10], [0.95, 0.95, 0.45], "RUNE_GLOW", rot=[6, -sx * 20, 45]))

        # 4. CỔ GIÁP GOTHIC GAI CONG MỀM MẠI ÔM 2 BÊN CỔ (không còn khối gạch thẳng góc!)
        add_curved_rod(chest_cubes, [
            [sx * 2.8, 42.4, -3.4],
            [sx * 4.4, 43.4, -1.6],
            [sx * 4.8, 44.8, +1.2],
            [sx * 2.8, 45.2, +3.0],
        ], 1.45, 1.10, "ARMOR_SPIKE", segs_per_span=2)
        add_curved_rod(chest_cubes, [
            [sx * 3.8, 43.2, -1.8],
            [sx * 5.0, 45.2, -2.0],
            [sx * 5.4, 47.0, -1.6],
        ], 0.85, 0.22, "ARMOR_SPIKE", segs_per_span=2)
        add_curved_rod(chest_cubes, [
            [sx * 4.6, 43.8, +0.2],
            [sx * 6.2, 46.2, +0.8],
            [sx * 6.8, 48.0, +1.4],
        ], 0.95, 0.24, "ARMOR_SPIKE", segs_per_span=2)

    bones.append({"name": "chest", "parent": "torso_upper", "pivot": [0.0, 35.0, -0.2], "rotation": [0.0, 0.0, 0.0], "cubes": chest_cubes})

    # -------------------------------------------------------------------------
    # C1. BONE TRÁI TIM NIGHTMARE CORE ĐẬP NHỊP 3D (`heart`, con của `chest`) — LƠ LỬNG GIỮA KHUNG XƯƠNG SƯỜN MỞ!
    #     Khớp chuẩn phong cách Nightmare Core (lõi hồng san hô phát sáng + vỏ đỏ huyết + 4 chấu bạc + 6 mạch máu rễ tim!)
    # -------------------------------------------------------------------------
    hx, hy, hz = 0.0, 37.8, -3.55
    heart_cubes = [
        # Khối cơ tim bầu dục–nhọn đáy hữu cơ gồm 2 bầu tâm nhĩ trên vát tròn + đỉnh tâm thất thuôn nhọn (KHÔNG còn hộp chữ nhật!)
        make_centered_cube([hx,        hy - 0.15, hz],        [3.60, 4.40, 2.85], "HEART_OUTER"),
        make_centered_cube([hx + 0.92, hy + 1.05, hz - 0.10], [2.45, 2.70, 2.45], "HEART_OUTER", rot=[4, 0, -24]),
        make_centered_cube([hx - 0.96, hy + 0.90, hz - 0.08], [2.25, 2.55, 2.35], "HEART_OUTER", rot=[4, 0, +22]),
        make_centered_cube([hx,        hy - 1.35, hz - 0.12], [2.55, 2.40, 2.30], "HEART_OUTER", rot=[10, 0, 45]),
        make_centered_cube([hx,        hy - 2.35, hz - 0.24], [1.35, 1.65, 1.55], "HEART_OUTER", rot=[14, 0, 45]),
        # Lõi Nightmare Core bầu dục–kim cương hữu cơ phát sáng mượt (taper nhiều lớp xoay chéo -> KHÔNG viền hình chữ nhật!)
        make_centered_cube([hx + 0.45, hy + 0.65, hz - 0.68], [1.45, 1.95, 1.45], "HEART_CORE", rot=[4, 0, -22]),
        make_centered_cube([hx - 0.48, hy + 0.55, hz - 0.66], [1.35, 1.85, 1.40], "HEART_CORE", rot=[4, 0, +20]),
        make_centered_cube([hx,        hy - 0.05, hz - 0.85], [1.85, 2.95, 1.65], "HEART_CORE", rot=[4, 0, 0]),
        make_centered_cube([hx,        hy - 0.05, hz - 1.15], [1.85, 1.85, 1.30], "HEART_CORE", rot=[4, 0, 45]),
        make_centered_cube([hx,        hy + 0.05, hz - 1.42], [1.05, 1.05, 0.92], "HEART_CORE", rot=[4, 0, 45]),
        # 4 chấu kẹp xương-bạc mảnh ôm cực trên & dưới của Trái Tim Nightmare Core (y hệt icon Nightmare Core!)
        make_centered_cube([hx - 0.95, hy + 2.85, hz - 0.55], [0.54, 1.40, 0.72], "SKULL_BONE", rot=[14, 0, -18]),
        make_centered_cube([hx + 0.95, hy + 2.85, hz - 0.55], [0.54, 1.40, 0.72], "SKULL_BONE", rot=[14, 0, +18]),
        make_centered_cube([hx - 0.80, hy - 2.75, hz - 0.55], [0.50, 1.30, 0.68], "SKULL_BONE", rot=[-14, 0, +18]),
        make_centered_cube([hx + 0.80, hy - 2.75, hz - 0.55], [0.50, 1.30, 0.68], "SKULL_BONE", rot=[-14, 0, -18]),
    ]
    # 2 mạch vành sẫm nổi ôm cong mặt trước cơ tim tạo cảm giác trái tim sống hữu cơ 100%!
    for sx in (1.0, -1.0):
        add_curved_rod(heart_cubes, [
            [hx + sx * 1.45, hy + 1.55, hz - 1.05],
            [hx + sx * 1.15, hy + 0.05, hz - 1.50],
            [hx + sx * 0.45, hy - 1.65, hz - 1.25],
        ], 0.30, 0.14, "CLAW_BLOOD", segs_per_span=2)
    # 6 mạch máu & động mạch chủ hữu cơ mọc từ đỉnh và 2 bên Trái Tim cắm vào xương sườn & 2 huy hiệu đỏ!
    for sx in (1.0, -1.0):
        # Động mạch trên vươn cong nối từ Trái Tim lên 2 huy hiệu ngọc đỏ & cổ giáp!
        add_curved_rod(heart_cubes, [
            [sx * 0.85, hy + 2.1, hz - 0.15],
            [sx * 2.50, hy + 3.4, hz - 0.95],
            [sx * 4.80, hy + 3.8, hz - 1.75],
        ], 0.76, 0.42, "HORN_CRIMSON", segs_per_span=2)
        # Động mạch giữa bám sang vách lồng ngực
        add_curved_rod(heart_cubes, [
            [sx * 1.60, hy + 0.4, hz + 0.10],
            [sx * 3.80, hy + 0.8, hz + 0.45],
            [sx * 5.60, hy + 0.4, hz + 1.25],
        ], 0.68, 0.36, "CLAW_BLOOD", segs_per_span=2)
        # Động mạch dưới bám vào xương sườn đáy
        add_curved_rod(heart_cubes, [
            [sx * 1.25, hy - 1.5, hz + 0.10],
            [sx * 3.20, hy - 2.6, hz + 0.40],
            [sx * 4.80, hy - 3.6, hz + 1.20],
        ], 0.62, 0.32, "CLAW_BLOOD", segs_per_span=2)

    bones.append({"name": "heart", "parent": "chest", "pivot": [hx, hy, hz], "rotation": [0.0, 0.0, 0.0], "cubes": heart_cubes})

    spikes_back_cubes = []
    for y_sp, z_sp, len_sp in ((42.0, 4.0, 3.6), (39.4, 4.3, 4.2), (36.8, 4.1, 3.5), (34.4, 3.8, 2.8)):
        add_curved_rod(spikes_back_cubes, [
            [0.0, y_sp, z_sp],
            [0.0, y_sp + 0.9, z_sp + len_sp * 0.55],
            [0.0, y_sp + 2.1, z_sp + len_sp],
        ], 1.15, 0.24, "ARMOR_SPIKE", segs_per_span=2)
        for sx in (1.0, -1.0):
            add_curved_rod(spikes_back_cubes, [
                [sx * 2.6, y_sp, z_sp],
                [sx * 4.4, y_sp + 1.3, z_sp + len_sp * 0.78],
            ], 0.85, 0.22, "ARMOR_SPIKE", segs_per_span=2)
    bones.append({"name": "spikes_back", "parent": "chest", "pivot": [0.0, 38.4, 4.2], "rotation": [0.0, 0.0, 0.0], "cubes": spikes_back_cubes})

    # -------------------------------------------------------------------------
    # C2. ÁO CHOÀNG ĐỎ CHỮ A RỘNG MỀM 3 TẦNG + 2 VẠT HÔNG (`cape` -> `cape_upper` -> `cape_mid` -> `cape_lower`, `cape_l/r`)
    # -------------------------------------------------------------------------
    bones.append({"name": "cape", "parent": "chest", "pivot": [0.0, 41.6, 4.4], "rotation": [0.0, 0.0, 0.0], "cubes": []})

    cape_up_c = []
    cape_up_c.append(make_centered_cube([0.0, 41.2, 4.2], [11.2, 2.0, 1.8], "ARMOR_SPIKE", rot=[-10, 0, 0]))
    for sx in (1.0, -1.0):
        cape_up_c.append(make_centered_cube([sx * 5.0, 40.4, 4.4], [2.2, 2.6, 1.3], "ARMOR_SPIKE", rot=[-12, sx * 14, sx * -16]))
    add_membrane_bay_3d(cape_up_c, [-6.2, 41.4, 4.5], [6.2, 41.4, 4.5],
                        [-9.8, 28.4, 8.2], [9.8, 28.4, 8.2], 14, "CAPE_TATTER", (0.14, 0.86), (0.02, 0.34), 0.28,
                        pleat_amp=0.58, pleat_top_amp=0.16, pleat_freq=5.0)
    bones.append({"name": "cape_upper", "parent": "cape", "pivot": [0.0, 41.4, 4.5], "rotation": [0.0, 0.0, 0.0], "cubes": cape_up_c})

    cape_mid_c = []
    add_membrane_bay_3d(cape_mid_c, [-9.8, 28.6, 8.15], [9.8, 28.6, 8.15],
                        [-13.8, 15.2, 13.0], [13.8, 15.2, 13.0], 14, "CAPE_TATTER", (0.08, 0.92), (0.32, 0.66), 0.28,
                        pleat_amp=0.80, pleat_top_amp=0.58, pleat_freq=5.0)
    bones.append({"name": "cape_mid", "parent": "cape_upper", "pivot": [0.0, 28.6, 8.15], "rotation": [0.0, 0.0, 0.0], "cubes": cape_mid_c})

    cape_lo_c = []
    add_membrane_bay_3d(cape_lo_c, [-13.8, 15.4, 12.95], [13.8, 15.4, 12.95],
                        [-17.6, 2.2, 18.0], [17.6, 2.2, 18.0], 14, "CAPE_TATTER", (0.02, 0.98), (0.64, 0.99), 0.28,
                        pleat_amp=1.00, pleat_top_amp=0.80, pleat_freq=5.0)
    bones.append({"name": "cape_lower", "parent": "cape_mid", "pivot": [0.0, 15.4, 12.95], "rotation": [0.0, 0.0, 0.0], "cubes": cape_lo_c})

    cape_l_c = []
    add_membrane_bay_3d(cape_l_c, [5.6, 40.6, 4.45], [9.0, 38.6, 4.05],
                        [12.2, 18.2, 11.2], [16.2, 19.6, 8.6], 7, "CAPE_TATTER", (0.60, 0.98), (0.04, 0.64), 0.26,
                        pleat_amp=0.50, pleat_top_amp=0.16, pleat_freq=3.0)
    add_membrane_bay_3d(cape_l_c, [12.2, 18.4, 11.15], [16.2, 19.8, 8.55],
                        [15.6, 3.6, 16.8], [19.8, 6.0, 12.8], 7, "CAPE_TATTER", (0.58, 0.99), (0.62, 0.99), 0.26,
                        pleat_amp=0.66, pleat_top_amp=0.50, pleat_freq=3.0)
    cape_r_c = [mirror_x_cube(c) for c in cape_l_c]
    bones.append({"name": "cape_l", "parent": "cape_upper", "pivot": [6.2, 40.2, 4.3], "rotation": [0.0, 0.0, 0.0], "cubes": cape_l_c})
    bones.append({"name": "cape_r", "parent": "cape_upper", "pivot": [-6.2, 40.2, 4.3], "rotation": [0.0, 0.0, 0.0], "cubes": cape_r_c})

    # -------------------------------------------------------------------------
    # D. CỔ GÂN GUỐC (`neck`) & ĐẦU SỌ QUỶ HỮU CƠ MỀM MẠI (`head`, `jaw`, `mandible_l/r`, `horn_l/r`)
    #    - Vuốt mềm vương miện mũ thành đường cong hữu cơ (bỏ góc cạnh hộp!), giữ mắt đỏ xếch hung dữ `\ /`,
    #      mũi trán chữ V, hốc mũi tam giác, gò má xương cao gai nhỏ, cằm nhọn chữ V & cặp sừng cừu đỏ móc ngược lên!
    # -------------------------------------------------------------------------
    neck_cubes = [
        make_centered_cube([0.0, 43.8, -0.5], [4.1, 3.4, 3.8], "FLESH_DARK"),
        make_centered_cube([0.0, 43.8, 1.3], [3.0, 3.2, 1.3], "ARMOR_STEEL"),
    ]
    for sx in (1.0, -1.0):
        # Gân cổ cơ bắp & mạch máu đỏ mảnh kéo từ cổ ngực lên sọ
        add_chain_rod(neck_cubes, [sx * 1.75, 42.0, -2.0], [sx * 1.05, 45.2, -1.2], 0.92, 0.68, 2, "FLESH_DARK", overlap=0.18)
        add_chain_rod(neck_cubes, [sx * 1.25, 42.2, -2.3], [sx * 0.75, 44.8, -1.8], 0.42, 0.28, 2, "HORN_CRIMSON", overlap=0.14)
    bones.append({"name": "neck", "parent": "chest", "pivot": [0.0, 42.6, -0.4], "rotation": [0.0, 0.0, 0.0], "cubes": neck_cubes})

    hs = 1.22
    hy0 = 45.8  # Đỉnh sọ ~52.8 px, đỉnh sừng ~55.0 px -> tỷ lệ siêu cân đối!

    def hcen(cx, cy, cz, sx, sy, sz, mat, rot=None):
        return make_centered_cube([cx * hs, hy0 + cy * hs, cz * hs], [sx * hs, sy * hs, sz * hs], mat, rot=rot)

    head_cubes = [
        # Lõi sọ sau bọc mũ thép gothic vòm cong mềm
        hcen(0.0, 2.55, 0.15, 3.9, 4.0, 4.6, "ARMOR_STEEL"),
        hcen(0.0, 4.05, 0.15, 3.4, 1.5, 4.7, "ARMOR_STEEL", rot=[-10, 0, 0]),
        # Mũi trán chữ V (Widow's peak) thép đen cắm sâu sắc lạnh giữa 2 mắt
        hcen(0.0, 3.35, -3.26, 1.50, 1.38, 0.92, "ARMOR_SPIKE", rot=[10, 0, 0]),
        hcen(0.0, 2.50, -3.48, 0.90, 1.22, 0.82, "ARMOR_SPIKE", rot=[14, 0, 0]),
        hcen(0.0, 1.85, -3.56, 0.42, 0.70, 0.62, "ARMOR_SPIKE", rot=[18, 0, 0]),

        # KHUÔN MẶT SỌ XƯƠNG XÁM NGÀ THON GẦY (`SKULL_BONE`)
        hcen(0.0, 2.35, -2.75, 3.45, 1.70, 1.58, "SKULL_BONE"),
        hcen(0.0, 1.28, -3.10, 2.36, 1.48, 1.44, "SKULL_BONE"),
        hcen(0.0, 1.28, -3.82, 0.70, 1.04, 0.44, "WING_BONE"),
        hcen(0.0, 1.58, -3.84, 0.40, 0.52, 0.42, "WING_BONE"),
        hcen(0.0, 0.36, -3.14, 2.00, 0.86, 1.35, "SKULL_BONE"),
        hcen(0.0, -0.45, -2.88, 1.65, 1.38, 1.42, "WING_BONE"),
        hcen(-0.54, -0.06, -3.38, 0.25, 0.45, 0.25, "SKULL_BONE"),
        hcen( 0.00, -0.02, -3.42, 0.26, 0.35, 0.25, "SKULL_BONE"),
        hcen(+0.54, -0.06, -3.38, 0.25, 0.45, 0.25, "SKULL_BONE"),
        # Nắp gáy cong thuôn sau mũ
        hcen(0.0, 2.20, 2.30, 4.1, 3.8, 1.55, "ARMOR_STEEL", rot=[-12, 0, 0]),
        hcen(0.0, 0.40, 2.50, 3.2, 2.0, 1.25, "ARMOR_SPIKE", rot=[-20, 0, 0]),
    ]
    # Sống vương miện cong hữu cơ uốn lượn dọc đỉnh sọ (thay thế hẳn 3 khối hộp đỉnh đầu!)
    add_curved_rod(head_cubes, [
        [0.0, hy0 + 3.6 * hs, -3.1 * hs],
        [0.0, hy0 + 5.6 * hs, -1.6 * hs],
        [0.0, hy0 + 6.0 * hs, +0.4 * hs],
        [0.0, hy0 + 4.4 * hs, +2.6 * hs],
    ], 1.15 * hs, 0.55 * hs, "ARMOR_SPIKE", segs_per_span=2)

    head_side = [
        # Cung mày cong chữ V hung dữ (`rotZ = -22°` trên +X => mắt xếch dữ `\ /`!)
        hcen(1.38, 3.16, -3.16, 2.15, 0.68, 1.48, "ARMOR_SPIKE", rot=[8, -18, -22]),
        hcen(2.02, 2.75, -1.90, 1.15, 1.85, 3.00, "ARMOR_STEEL", rot=[0, -14, -14]),
        # Hốc mắt sâu xếch hung dữ `\ /` & mắt đỏ mảnh sắc lẹm fullbright
        hcen(1.16, 2.20, -3.52, 1.52, 0.84, 0.56, "WING_BONE", rot=[0, -14, -18]),
        hcen(1.16, 2.20, -3.76, 1.20, 0.36, 0.34, "EYE_GLOW",  rot=[0, -14, -18]),
        # Gò má xương sọ gầy cao vát sắc + hõm má sâu ma quái
        hcen(1.50, 1.48, -3.18, 1.58, 0.72, 1.42, "SKULL_BONE", rot=[4, -22, -16]),
        hcen(1.90, 1.82, -2.65, 1.16, 0.64, 1.22, "SKULL_BONE", rot=[0, -24, -22]),
        hcen(1.34, 0.50, -2.85, 1.02, 1.35, 1.28, "WING_BONE", rot=[0, -16, 12]),
        hcen(2.34, 2.22, -2.55, 0.94, 0.36, 0.60, "SKULL_BONE", rot=[0, -20, -26]),
        hcen(2.26, 1.56, -2.75, 0.86, 0.34, 0.56, "SKULL_BONE", rot=[0, -22, -14]),
    ]
    for c in head_side:
        head_cubes.append(c)
        head_cubes.append(mirror_x_cube(c))
    # Vây thái dương cong sắc uốn ngược ra sau mũ
    for sx in (1.0, -1.0):
        add_curved_rod(head_cubes, [
            [sx * 2.1 * hs, hy0 + 2.8 * hs, -1.2 * hs],
            [sx * 2.8 * hs, hy0 + 3.4 * hs, +0.8 * hs],
            [sx * 2.6 * hs, hy0 + 4.4 * hs, +2.6 * hs],
        ], 0.85 * hs, 0.24 * hs, "ARMOR_SPIKE", segs_per_span=2)

    bones.append({"name": "head", "parent": "neck", "pivot": [0.0, hy0 + 0.2, -0.5], "rotation": [0.0, 0.0, 0.0], "cubes": head_cubes})

    jaw_cubes = [
        hcen( 0.94, -0.35, -2.82, 0.58, 2.05, 1.58, "SKULL_BONE", rot=[-10, -14, +18]),
        hcen(-0.94, -0.35, -2.82, 0.58, 2.05, 1.58, "SKULL_BONE", rot=[-10, +14, -18]),
        hcen(0.0, -1.32, -3.12, 1.66, 0.76, 1.72, "SKULL_BONE"),
        hcen(0.0, -1.88, -3.20, 1.08, 0.66, 1.38, "SKULL_BONE"),
        hcen(0.0, -2.38, -3.16, 0.62, 0.56, 1.02, "SKULL_BONE"),
        hcen(0.0, -2.76, -3.08, 0.32, 0.40, 0.70, "SKULL_BONE"),
        hcen(0.0, -0.78, -2.74, 1.30, 0.92, 1.35, "WING_BONE"),
    ]
    bones.append({"name": "jaw", "parent": "head", "pivot": [0.0, hy0 + 0.5, -0.8], "rotation": [0.0, 0.0, 0.0], "cubes": jaw_cubes})

    mand_l = []
    add_claw_spike(mand_l, [2.0 * hs, hy0 + 0.6 * hs, -1.0 * hs], [3.0 * hs, hy0 - 0.5 * hs, -2.4 * hs], 0.56 * hs, "ARMOR_SPIKE", 2)
    mand_r = [mirror_x_cube(c) for c in mand_l]
    bones.append({"name": "mandible_l", "parent": "head", "pivot": [2.0 * hs, hy0 + 0.6 * hs, -1.0 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": mand_l})
    bones.append({"name": "mandible_r", "parent": "head", "pivot": [-2.0 * hs, hy0 + 0.6 * hs, -1.0 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": mand_r})

    # 2 SỪNG CỪU QUỶ ĐỎ THẪM CONG MÓC LÊN-TRƯỚC HỮU CƠ
    horn_l_pts = [
        [1.65 * hs, hy0 + 4.25 * hs, -0.75 * hs],
        [4.30 * hs, hy0 + 6.75 * hs, -0.70 * hs],
        [7.40 * hs, hy0 + 6.85 * hs, +0.25 * hs],
        [9.70 * hs, hy0 + 4.45 * hs, +0.75 * hs],
        [9.80 * hs, hy0 + 1.85 * hs, -0.25 * hs],
        [8.65 * hs, hy0 + 1.45 * hs, -1.85 * hs],
        [8.05 * hs, hy0 + 3.45 * hs, -2.85 * hs],
        [8.25 * hs, hy0 + 5.10 * hs, -3.15 * hs],
    ]
    horn_l_cubes = []
    add_curved_rod(horn_l_cubes, horn_l_pts, 2.38 * hs, 0.38 * hs, "HORN_CRIMSON", segs_per_span=4, rib_rings=True, ring_mat="HORN_CRIMSON")
    horn_r_cubes = [mirror_x_cube(c) for c in horn_l_cubes]
    bones.append({"name": "horn_l", "parent": "head", "pivot": [1.65 * hs, hy0 + 4.25 * hs, -0.75 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": horn_l_cubes})
    bones.append({"name": "horn_r", "parent": "head", "pivot": [-1.65 * hs, hy0 + 4.25 * hs, -0.75 * hs], "rotation": [0.0, 0.0, 0.0], "cubes": horn_r_cubes})

    # -------------------------------------------------------------------------
    # E. ĐÔI CÁNH QUỶ KHỔNG LỒ XÒE NGANG RỘNG (`wing_l/r` -> `wing_*_upper/mid/tip`)
    # -------------------------------------------------------------------------
    def build_wing_chain(side: float):
        tag = "l" if side > 0 else "r"
        s = side

        p_root      = [s * 4.4,  39.8,  4.2]
        p_elbow     = [s * 12.2, 46.2,  7.4]
        p_apex      = [s * 20.8, 56.0, 10.4]
        p_wrist     = [s * 30.6, 50.4, 12.8]
        p_outer_mid = [s * 38.2, 41.8, 14.2]
        p_outer_tip = [s * 42.6, 29.8, 15.0]

        p_rib0_top = [s * 18.6, 53.5,  9.6]
        p_rib0_end = [s * 14.6, 22.8,  8.0]
        p_rib1_top = [s * 22.4, 54.8, 10.8]
        p_rib1_end = [s * 22.0, 18.4, 10.0]
        p_rib2_top = [s * 26.5, 52.7, 11.8]
        p_rib2_end = [s * 29.5, 18.8, 12.0]
        p_rib3_top = p_wrist
        p_rib3_end = [s * 36.2, 22.0, 13.6]

        def lerp_pt(pa, pb, f):
            return [pa[0] + (pb[0] - pa[0]) * f, pa[1] + (pb[1] - pa[1]) * f, pa[2] + (pb[2] - pa[2]) * f]

        def add_knuckle(lst, pt, r):
            lst.append(make_centered_cube(pt, [r * 2.0, r * 2.0, r * 2.0], "WING_BONE"))
            lst.append(make_centered_cube(pt, [r * 1.44, r * 1.44, r * 2.3], "ARMOR_SPIKE"))

        w_root_cubes = [
            make_centered_cube(p_root, [2.7, 2.7, 2.5], "WING_BONE"),
            make_centered_cube([p_root[0], p_root[1], p_root[2] - 0.8], [2.0, 2.0, 1.0], "ARMOR_SPIKE"),
        ]

        w_up_cubes = []
        add_chain_rod(w_up_cubes, p_root, p_elbow, 1.60, 1.34, 5, "WING_BONE", overlap=0.38)
        add_chain_rod(w_up_cubes, p_elbow, p_apex, 1.38, 1.18, 6, "WING_BONE", overlap=0.38)
        add_chain_rod(w_up_cubes, [s * 5.4, 37.2, 4.6], lerp_pt(p_rib0_top, p_rib0_end, 0.42), 0.86, 0.60, 5, "WING_BONE", overlap=0.30)
        add_knuckle(w_up_cubes, p_elbow, 0.92)
        add_knuckle(w_up_cubes, p_apex, 1.06)
        apex_k1 = [p_apex[0] - s * 1.3, p_apex[1] + 2.9, p_apex[2] - 0.7]
        apex_k2 = [p_apex[0] - s * 3.9, p_apex[1] + 4.7, p_apex[2] - 1.8]
        add_chain_rod(w_up_cubes, [p_apex[0], p_apex[1] + 0.5, p_apex[2]], apex_k1, 1.04, 0.68, 3, "WING_BONE", overlap=0.26)
        add_claw_spike(w_up_cubes, apex_k1, apex_k2, 0.76, "CLAW_BLOOD", 3)
        add_chain_rod(w_up_cubes, p_rib0_top, p_rib0_end, 0.92, 0.36, 8, "WING_BONE", overlap=0.34)
        for frac in (0.34, 0.68):
            add_knuckle(w_up_cubes, lerp_pt(p_rib0_top, p_rib0_end, frac), 0.50)
        add_claw_spike(w_up_cubes, p_rib0_end, [p_rib0_end[0] - s * 0.5, p_rib0_end[1] - 2.6, p_rib0_end[2] - 0.6], 0.46, "CLAW_BLOOD", 2)

        add_membrane_bay_3d(w_up_cubes,
                            [p_root[0] + s * 0.2, p_root[1] - 0.2, p_root[2] + 0.1],
                            [p_rib0_top[0], p_rib0_top[1] - 0.2, p_rib0_top[2]],
                            [s * 5.6, 26.6, 5.2],
                            lerp_pt(p_rib0_top, p_rib0_end, 0.95),
                            9, "WING_MEMBRANE", (0.01, 0.99), (0.01, 0.99), 0.24)

        w_mid_cubes = []
        add_chain_rod(w_mid_cubes, p_apex, p_wrist, 1.26, 1.00, 6, "WING_BONE", overlap=0.38)
        add_knuckle(w_mid_cubes, p_wrist, 0.88)

        for r_top, r_end, th_a in (
            (p_rib1_top, p_rib1_end, 0.88),
            (p_rib2_top, p_rib2_end, 0.84),
            (p_rib3_top, p_rib3_end, 0.78),
        ):
            add_chain_rod(w_mid_cubes, r_top, r_end, th_a, 0.34, 8, "WING_BONE", overlap=0.34)
            for frac in (0.33, 0.67):
                add_knuckle(w_mid_cubes, lerp_pt(r_top, r_end, frac), 0.46)
            add_claw_spike(w_mid_cubes, r_end, [r_end[0] + s * 0.5, r_end[1] - 2.6, r_end[2] - 0.5], 0.44, "CLAW_BLOOD", 2)

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
                                st, "WING_MEMBRANE", (0.01, 0.99), (0.01, 0.99), 0.24)

        w_tip_cubes = []
        add_chain_rod(w_tip_cubes, p_wrist, p_outer_mid, 1.00, 0.74, 5, "WING_BONE", overlap=0.36)
        add_chain_rod(w_tip_cubes, p_outer_mid, p_outer_tip, 0.74, 0.36, 5, "WING_BONE", overlap=0.34)
        add_knuckle(w_tip_cubes, p_outer_mid, 0.62)
        add_claw_spike(w_tip_cubes, p_outer_tip, [p_outer_tip[0] + s * 2.0, p_outer_tip[1] - 3.4, p_outer_tip[2] - 0.4], 0.48, "CLAW_BLOOD", 2)

        add_membrane_bay_3d(w_tip_cubes,
                            [p_wrist[0], p_wrist[1] - 0.25, p_wrist[2]],
                            [p_outer_mid[0], p_outer_mid[1] - 0.25, p_outer_mid[2]],
                            lerp_pt(p_rib3_top, p_rib3_end, 0.95),
                            lerp_pt(p_rib3_end, p_outer_tip, 0.54),
                            7, "WING_MEMBRANE", (0.01, 0.56), (0.01, 0.99), 0.24)
        add_membrane_bay_3d(w_tip_cubes,
                            [p_outer_mid[0], p_outer_mid[1] - 0.25, p_outer_mid[2]],
                            [p_outer_tip[0] - s * 0.15, p_outer_tip[1] - 0.2, p_outer_tip[2]],
                            lerp_pt(p_rib3_end, p_outer_tip, 0.52),
                            [p_outer_tip[0] - s * 0.2, p_outer_tip[1] - 2.0, p_outer_tip[2]],
                            6, "WING_MEMBRANE", (0.54, 0.99), (0.01, 0.99), 0.24)

        bones.append({"name": f"wing_{tag}", "parent": "chest", "pivot": p_root, "rotation": [0.0, 0.0, 0.0], "cubes": w_root_cubes})
        bones.append({"name": f"wing_{tag}_upper", "parent": f"wing_{tag}", "pivot": p_root, "rotation": [0.0, 0.0, 0.0], "cubes": w_up_cubes})
        bones.append({"name": f"wing_{tag}_mid", "parent": f"wing_{tag}_upper", "pivot": p_apex, "rotation": [0.0, 0.0, 0.0], "cubes": w_mid_cubes})
        bones.append({"name": f"wing_{tag}_tip", "parent": f"wing_{tag}_mid", "pivot": p_wrist, "rotation": [0.0, 0.0, 0.0], "cubes": w_tip_cubes})

    build_wing_chain(+1.0)
    build_wing_chain(-1.0)

    # -------------------------------------------------------------------------
    # F. GIÁP VAI VỎ SÒ CONG HỮU CƠ (`shoulder_l/r`) + CÁNH TAY CƠ BẮP HƠI CO TỰ NHIÊN (`arm_l/r`) + ĐẠI KIẾM!
    #    - Bỏ sạch các thanh vai vuông chồng gạch kiểu robot! Dùng gân sườn giáp cong ôm vòm vai + vây cong hữu cơ!
    #    - Khuỷu tay hơi gập tự nhiên về trước (`Z = 0.0 -> +1.0 -> -1.4`), cẳng tay vuốt thon hữu cơ nhiều chi tiết!
    # -------------------------------------------------------------------------
    sword_tip_world = [0.0, 0.0, 0.0]

    def build_arm_chain(side: float):
        nonlocal sword_tip_world
        tag = "l" if side > 0 else "r"
        s = side
        sh_piv = [s * 9.0, 41.0, 0.0]
        arm_x, arm_y, arm_z = s * 10.8, 39.4, +0.2
        el_x,  el_y,  el_z  = arm_x + s * 2.1, arm_y - 9.4, +1.0   # Hơi ngả sau ở khuỷu
        ha_x,  ha_y,  ha_z  = el_x  + s * 0.4, el_y  - 9.2, -1.6   # Cẳng tay co nhẹ tự nhiên về trước — KHÔNG đơ thẳng đứng!

        # 1. KHỚP VAI HỮU CƠ CONG VÒM (`shoulder_l/r`) — Giáp bả vai vỏ sò gothic uốn mềm thay vì hộp vuông!
        sh_cubes = [
            make_centered_cube([sh_piv[0] + s * 1.4, sh_piv[1] + 0.4, 0.0], [5.4, 4.6, 7.0], "ARMOR_STEEL", rot=[0, 0, s * -16]),
            make_centered_cube([sh_piv[0] + s * 2.5, sh_piv[1] - 1.6, 0.0], [4.6, 3.8, 6.2], "ARMOR_STEEL", rot=[0, 0, s * -28]),
            # Mái vòm cổ vai cong vát cao khung 2 bên đầu giống ảnh mẫu 1
            make_centered_cube([sh_piv[0] + s * 0.2, sh_piv[1] + 3.0, -0.5], [1.6, 4.2, 4.8], "ARMOR_SPIKE", rot=[0, -s * 16, s * 22]),
        ]
        # 5 thanh gân giáp vỏ sò cong ôm vòm vai từ trước ra sau (chi tiết sống động đúng ảnh mẫu 2!)
        for k, (dy, dx_out, dz_span) in enumerate(((2.0, 1.8, 3.6), (0.8, 3.2, 4.2), (-0.6, 4.2, 4.0), (-2.0, 4.8, 3.5), (-3.3, 4.9, 2.8))):
            add_curved_rod(sh_cubes, [
                [sh_piv[0] + s * (dx_out - 0.6), sh_piv[1] + dy - 0.5, -dz_span],
                [sh_piv[0] + s * (dx_out + 0.6), sh_piv[1] + dy + 0.3,  0.0],
                [sh_piv[0] + s * (dx_out - 0.5), sh_piv[1] + dy - 0.5, +dz_span],
            ], 1.15, 0.95, "ARMOR_SPIKE" if k % 2 == 0 else "ARMOR_STEEL", segs_per_span=2)
        # 3 sừng gai cong hữu cơ uốn lượn mềm trên bả vai (thay que thẳng robot!)
        add_curved_rod(sh_cubes, [
            [sh_piv[0] + s * 1.0, sh_piv[1] + 2.8, -1.0],
            [sh_piv[0] + s * 2.8, sh_piv[1] + 5.2, -1.4],
            [sh_piv[0] + s * 4.2, sh_piv[1] + 7.2, -1.0],
        ], 1.15, 0.24, "ARMOR_SPIKE", segs_per_span=2)
        add_curved_rod(sh_cubes, [
            [sh_piv[0] + s * 2.8, sh_piv[1] + 1.6, +0.8],
            [sh_piv[0] + s * 5.0, sh_piv[1] + 3.4, +1.4],
            [sh_piv[0] + s * 6.6, sh_piv[1] + 4.8, +2.0],
        ], 1.05, 0.22, "ARMOR_SPIKE", segs_per_span=2)
        add_curved_rod(sh_cubes, [
            [sh_piv[0] + s * 3.8, sh_piv[1] - 0.8, -1.2],
            [sh_piv[0] + s * 6.0, sh_piv[1] - 0.2, -1.8],
            [sh_piv[0] + s * 7.2, sh_piv[1] + 0.8, -1.6],
        ], 0.92, 0.20, "BONE_RIB", segs_per_span=2)

        bones.append({"name": f"shoulder_{tag}", "parent": "chest", "pivot": sh_piv, "rotation": [0.0, 0.0, 0.0], "cubes": sh_cubes})
        bones.append({"name": f"arm_{tag}", "parent": f"shoulder_{tag}", "pivot": [arm_x, arm_y, arm_z], "rotation": [0.0, 0.0, 0.0], "cubes": []})

        # 2. `arm_*_upper`: Bắp tay cơ bắp nổi khối gân guốc + dây vải đỏ quấn chéo hữu cơ!
        up_cubes = []
        add_chain_rod(up_cubes, [arm_x, arm_y - 0.6, arm_z], [el_x, el_y + 0.5, el_z], 4.05, 3.35, 4, "FLESH_DARK", overlap=0.45)
        mid_u = [0.5 * (arm_x + el_x), 0.5 * (arm_y + el_y) - 0.4, 0.5 * (arm_z + el_z)]
        # Khối cơ nhị đầu (bicep) & tam đầu (tricep) vuốt mềm
        up_cubes.append(make_centered_cube([mid_u[0] - s * 0.2, mid_u[1], mid_u[2] - 1.15], [3.0, 5.2, 1.85], "FLESH_DARK", rot=[6, 0, s * -10]))
        up_cubes.append(make_centered_cube([mid_u[0] + s * 0.2, mid_u[1] + 0.3, mid_u[2] + 1.10], [2.9, 4.8, 1.75], "FLESH_DARK", rot=[6, 0, s * -10]))
        # 2 vòng dây băng vải đỏ quấn chéo tự nhiên + 1 dải vải thừa phất nhẹ
        for frac, tilt in ((0.44, 10), (0.66, -8)):
            px = arm_x * (1.0 - frac) + el_x * frac
            py = (arm_y - 0.6) * (1.0 - frac) + (el_y + 0.5) * frac
            pz = arm_z * (1.0 - frac) + el_z * frac
            up_cubes.append(make_centered_cube([px, py, pz], [4.05, 0.95, 4.15], "CLOTH_TATTER",
                                               rot=[tilt, 0, s * -12], sub_v=(0.18, 0.48)))

        bones.append({"name": f"arm_{tag}_upper", "parent": f"arm_{tag}", "pivot": [arm_x, arm_y, arm_z], "rotation": [0.0, 0.0, 0.0], "cubes": up_cubes})

        # 3. `arm_*_lower`: Găng giáp cẳng tay vát dọc mềm mại (KHÔNG còn 3 khối ống vuông xếp chồng!) + vây khuỷu cong!
        lo_cubes = []
        add_chain_rod(lo_cubes, [el_x, el_y + 0.6, el_z], [ha_x, ha_y, ha_z], 3.75, 2.95, 5, "ARMOR_STEEL", overlap=0.45)
        mid_l = [0.5 * (el_x + ha_x), 0.5 * (el_y + ha_y), 0.5 * (el_z + ha_z)]
        # Tấm ốp găng giáp dọc vát thuôn dần theo cơ cẳng tay (mềm & sắc sảo!)
        lo_cubes.append(make_centered_cube([mid_l[0] + s * 1.45, mid_l[1] + 0.2, mid_l[2]], [1.55, 7.6, 3.2], "ARMOR_STEEL", rot=[16, 0, s * -6]))
        lo_cubes.append(make_centered_cube([mid_l[0] + s * 1.90, mid_l[1] + 0.4, mid_l[2] + 0.2], [0.95, 6.2, 2.0], "ARMOR_SPIKE", rot=[16, 0, s * -8]))
        # Vòng cổ tay gothic vát nhọn
        lo_cubes.append(make_centered_cube([ha_x, ha_y + 1.2, ha_z + 0.2], [3.55, 1.8, 3.65], "ARMOR_SPIKE", rot=[16, 0, 0]))
        # Lưỡi vây khuỷu tay cong mềm & 2 gai cẳng tay cong tự nhiên
        add_curved_rod(lo_cubes, [
            [el_x + s * 1.2, el_y - 0.6, el_z + 1.0],
            [el_x + s * 3.0, el_y + 1.2, el_z + 2.6],
            [el_x + s * 4.4, el_y + 3.4, el_z + 3.6],
        ], 1.15, 0.24, "ARMOR_SPIKE", segs_per_span=2)
        add_curved_rod(lo_cubes, [
            [mid_l[0] + s * 1.6, mid_l[1] + 0.8, mid_l[2] + 0.8],
            [mid_l[0] + s * 3.8, mid_l[1] + 2.4, mid_l[2] + 1.8],
        ], 0.82, 0.20, "ARMOR_SPIKE", segs_per_span=2)
        add_curved_rod(lo_cubes, [
            [mid_l[0] + s * 1.5, mid_l[1] - 1.8, mid_l[2] + 0.4],
            [mid_l[0] + s * 3.4, mid_l[1] - 0.6, mid_l[2] + 1.2],
        ], 0.74, 0.18, "ARMOR_SPIKE", segs_per_span=2)

        bones.append({"name": f"arm_{tag}_lower", "parent": f"arm_{tag}_upper", "pivot": [el_x, el_y, el_z], "rotation": [0.0, 0.0, 0.0], "cubes": lo_cubes})

        # 4. `hand_l/r`: Bàn tay gân guốc bọc mu giáp & 4 ngón vuốt cong hữu cơ
        ha_cubes = [
            make_centered_cube([ha_x, ha_y - 1.2, ha_z], [3.20, 2.6, 3.40], "FLESH_DARK", rot=[10, 0, 0]),
            make_centered_cube([ha_x + s * 1.20, ha_y - 1.1, ha_z], [1.10, 2.2, 3.10], "ARMOR_SPIKE", rot=[10, 0, 0]),
        ]
        for fz_off in (-1.20, -0.40, 0.40, 1.18):
            fz = ha_z + fz_off
            curl = 0.68 if s < 0 else 0.34
            p0 = [ha_x + s * 0.55, ha_y - 2.3, fz]
            p1 = [ha_x + s * (0.28 - curl * 0.76), ha_y - 3.6, fz - 0.14]
            p2 = [ha_x + s * (0.02 - curl * 1.20), ha_y - 4.7, fz - 0.24]
            p3 = [ha_x - s * (0.50 + curl * 0.64), ha_y - 5.6, fz - 0.32]
            add_chain_rod(ha_cubes, p0, p1, 0.68, 0.56, 2, "FLESH_DARK", overlap=0.16)
            add_chain_rod(ha_cubes, p1, p2, 0.56, 0.45, 2, "ARMOR_SPIKE", overlap=0.16)
            add_claw_spike(ha_cubes, p2, p3, 0.45, "CLAW_BLOOD", 2)
        add_chain_rod(ha_cubes, [ha_x - s * 1.05, ha_y - 1.5, ha_z - 1.4], [ha_x - s * 1.80, ha_y - 3.4, ha_z - 2.2], 0.68, 0.36, 3, "CLAW_BLOOD", overlap=0.16)

        bones.append({"name": f"hand_{tag}", "parent": f"arm_{tag}_lower", "pivot": [ha_x, ha_y, ha_z], "rotation": [0.0, 0.0, 0.0], "cubes": ha_cubes})

        # ---------------------------------------------------------------------
        # ĐẠI KIẾM QUỶ RĂNG CƯA CÂN ĐỐI (~50.5 px tổng chuôi->mũi, lơ lửng an toàn ~2.8 px trên đất!)
        # ---------------------------------------------------------------------
        if s < 0:
            wp_x, wp_y, wp_z = ha_x - 0.2, ha_y - 2.8, ha_z - 0.6
            R_sw = bedrock_rot_matrix([20.5, 11.0, -7.0])
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
                sw_box(-0.72, -0.72, -2.8, 1.44, 1.44, 11.8, "WING_BONE"),
                sw_box(-0.92, -0.92, +1.1, 1.84, 1.84, 1.0, "ARMOR_SPIKE"),
                sw_box(-0.92, -0.92, +4.4, 1.84, 1.84, 1.0, "ARMOR_SPIKE"),
                sw_box(-1.18, -1.18, +8.0, 2.36, 2.36, 2.1, "ARMOR_SPIKE"),
                sw_box(-0.82, -0.82, +9.7, 1.64, 1.64, 2.4, "RUNE_GLOW", extra_rot=(0, 0, 45)),
                sw_box(-1.42, -4.2, -4.3, 2.84, 8.4, 2.3, "ARMOR_SPIKE"),
                sw_box(-1.12, -2.7, -5.2, 2.24, 5.4, 1.6, "ARMOR_STEEL"),
                sw_box(-1.00, -1.3, -5.0, 2.00, 2.6, 1.8, "RUNE_GLOW"),
            ]
            add_claw_spike(w_cubes, sw_pt(0, +3.5, -3.4), sw_pt(0, +7.0, -7.8), 1.30, "CLAW_BLOOD", 4)
            add_claw_spike(w_cubes, sw_pt(0, -3.5, -3.4), sw_pt(0, -7.0, -7.8), 1.30, "CLAW_BLOOD", 4)
            add_claw_spike(w_cubes, sw_pt(0, +2.4, -2.2), sw_pt(0, +4.9, +1.3), 0.96, "ARMOR_SPIKE", 3)
            add_claw_spike(w_cubes, sw_pt(0, -2.4, -2.2), sw_pt(0, -4.9, +1.3), 0.96, "ARMOR_SPIKE", 3)
            add_claw_spike(w_cubes, sw_pt(0, +1.3, +8.6), sw_pt(0, +2.8, +11.0), 0.74, "CLAW_BLOOD", 2)
            add_claw_spike(w_cubes, sw_pt(0, -1.3, +8.6), sw_pt(0, -2.8, +11.0), 0.74, "CLAW_BLOOD", 2)

            blade_segs = [
                (-4.2,  5.3, 2.22, 1.68, 1.12),
                (-9.3,  5.3, 2.58, 1.94, 1.04),
                (-14.4, 5.3, 2.32, 1.78, 0.95),
                (-19.5, 5.3, 1.94, 1.48, 0.86),
                (-24.6, 5.1, 1.48, 1.16, 0.74),
                (-29.5, 4.4, 0.98, 0.80, 0.58),
            ]
            for idx_b, (zs, blen, hu, hd, bth) in enumerate(blade_segs):
                w_cubes.append(sw_box(-bth * 0.5, -hd * 0.50, zs - blen, bth, hu * 0.78 + hd * 0.50, blen + 0.26, "BLADE_STEEL"))
                w_cubes.append(sw_box(-bth * 0.58, -0.45, zs - blen, bth * 1.16, 0.90, blen + 0.20, "BLADE_STEEL"))
                w_cubes.append(sw_box(-bth * 0.28, +hu * 0.58, zs - blen, bth * 0.56, hu * 0.48, blen + 0.20, "BLADE_EDGE"))
                w_cubes.append(sw_box(-bth * 0.26, -hd * 1.02, zs - blen, bth * 0.52, hd * 0.56, blen + 0.20, "BLADE_EDGE"))
                if idx_b < 5:
                    w_cubes.append(sw_box(-bth * 0.64, -0.19, zs - blen + 0.52, bth * 1.28, 0.38, blen - 1.04, "RUNE_GLOW"))
                if idx_b in (0, 1, 2, 3):
                    add_claw_spike(w_cubes, sw_pt(0, +hu * 0.78, zs - blen * 0.52),
                                   sw_pt(0, +hu * 1.62, zs - blen * 0.12), 0.60, "BLADE_EDGE", 2)
                    add_claw_spike(w_cubes, sw_pt(0, -hd * 0.78, zs - blen * 0.72),
                                   sw_pt(0, -hd * 1.54, zs - blen * 0.28), 0.56, "BLADE_EDGE", 2)
                if idx_b in (0, 2):
                    add_claw_spike(w_cubes, sw_pt(0, +hu * 0.65, zs - blen * 0.88),
                                   sw_pt(0, +hu * 1.32, zs - blen * 0.55), 0.48, "BLADE_STEEL", 2)

            add_claw_spike(w_cubes, sw_pt(0, 0, -33.6), sw_pt(0, 0, -38.4), 0.92, "BLADE_EDGE", 3)
            tip_pos = sw_pt(0, 0, -38.4)
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
    # G. CHÂN HIỆP SĨ QUỶ THẲNG GỌN HƠN (~22.8 px) & MỀM MẠI HỮU CƠ (KHÔNG CÒN ỐNG XẾP VÒNG ROBOT!)
    #    - Đùi & ống chân bọc tấm giáp dọc uốn cong ôm cơ đùi/bắp chân, lộ gân cơ mặt trong, giáp gối hình lá gothic cong mềm!
    # -------------------------------------------------------------------------
    def build_leg_chain(side: float):
        tag = "l" if side > 0 else "r"
        s = side

        # Giảm chiều dài chân xuống 22.8 px (tỷ lệ chiến thần cân đối, thế đứng chữ A vững chắc tự nhiên!)
        p_hip   = np.array([s * 4.5, 22.8, +0.3], dtype=np.float64)
        p_knee  = np.array([s * 5.7, 12.6, -1.2], dtype=np.float64)
        p_ankle = np.array([s * 6.3,  3.0, +0.1], dtype=np.float64)

        bones.append({"name": f"leg_{tag}", "parent": "pelvis", "pivot": p_hip.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": []})

        # 1. `thigh_l/r`: Cơ đùi bọc giáp dọc lượn sóng mềm mại (thay thế hoàn toàn 5 đốt hộp ngang robot!)
        th_cubes = []
        # Lõi cơ đùi săn chắc mặt trong (`FLESH_DARK`) tạo độ tương phản hữu cơ cực đẹp
        add_chain_rod(th_cubes, p_hip, p_knee, 5.1, 3.8, 4, "FLESH_DARK", overlap=0.55, aspect_z=1.08)
        mid_th = 0.5 * (p_hip + p_knee)
        # 3 tấm giáp đùi dọc uốn lượn ôm mặt trước, mặt ngoài & sống đùi (trơn mượt, KHÔNG bậc ngang robot!)
        th_cubes.append(make_centered_cube(mid_th + np.array([0.0, 0.3, -1.95]), [3.8, 9.6, 1.85], "ARMOR_STEEL", rot=[8.0, 0.0, s * -6.0]))
        th_cubes.append(make_centered_cube(mid_th + np.array([0.0, 0.4, -2.60]), [1.8, 8.8, 1.15], "ARMOR_SPIKE", rot=[8.0, 0.0, s * -6.0]))
        th_cubes.append(make_centered_cube(mid_th + np.array([s * 2.15, 0.2, -0.35]), [1.8, 9.2, 4.4], "ARMOR_STEEL", rot=[8.0, -s * 12.0, s * -8.0]))
        th_cubes.append(make_centered_cube(mid_th + np.array([0.0, 0.3, +1.90]), [3.6, 8.8, 1.75], "ARMOR_STEEL", rot=[8.0, 0.0, s * -6.0]))
        # 2 đai da-đỏ sẫm thắt ngang mặt sau đùi + gân giáp cong ngoài đùi
        for frac in (0.36, 0.68):
            pm = p_hip * (1.0 - frac) + p_knee * frac
            th_cubes.append(make_centered_cube(pm + np.array([-s * 0.4, 0.0, 0.3]), [4.4, 0.85, 4.6], "CLOTH_TATTER",
                                               rot=[8.0, 0.0, s * -6.0], sub_v=(0.20, 0.46)))
        add_curved_rod(th_cubes, [
            (p_hip * 0.72 + p_knee * 0.28 + np.array([s * 2.4, 0.2, 0.0])).tolist(),
            (p_hip * 0.48 + p_knee * 0.52 + np.array([s * 3.8, 1.2, 0.5])).tolist(),
            (p_hip * 0.30 + p_knee * 0.70 + np.array([s * 4.5, 2.2, 0.9])).tolist(),
        ], 0.92, 0.22, "ARMOR_SPIKE", segs_per_span=2)

        bones.append({"name": f"thigh_{tag}", "parent": f"leg_{tag}", "pivot": p_hip.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": th_cubes})

        # 1b. `knee_spur_l/r`: Giáp gối hình lá gothic uốn cong tinh xảo (nhiều chi tiết mềm mại, KHÔNG khối vuông!)
        ks_cubes = [
            make_centered_cube(p_knee + np.array([0.0, 0.1, -1.75]), [3.8, 3.2, 2.0], "ARMOR_STEEL", rot=[6.0, -s * 8.0, 0.0]),
            make_centered_cube(p_knee + np.array([0.0, 0.2, -2.40]), [2.8, 2.8, 1.3], "ARMOR_SPIKE", rot=[6.0, -s * 8.0, 45.0]),
            make_centered_cube(p_knee + np.array([s * 1.95, 0.0, -0.4]), [1.2, 2.8, 2.9], "ARMOR_SPIKE", rot=[0.0, -s * 16.0, -s * 12.0]),
        ]
        # Sống mũi gối cong uốn mượt lên trên & xuống dưới như cánh hoa thép gothic
        add_curved_rod(ks_cubes, [
            (p_knee + np.array([0.0, -1.6, -2.2])).tolist(),
            (p_knee + np.array([0.0, +0.4, -3.1])).tolist(),
            (p_knee + np.array([s * 0.2, +2.6, -3.3])).tolist(),
        ], 1.05, 0.24, "ARMOR_SPIKE", segs_per_span=2)
        bones.append({"name": f"knee_spur_{tag}", "parent": f"thigh_{tag}", "pivot": p_knee.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": ks_cubes})

        # 2. `shin_l/r`: Ống chân giáp dọc thuôn mềm ôm bắp chân (taper mượt xuống cổ chân thanh thoát!)
        sh_cubes = []
        add_chain_rod(sh_cubes, p_knee, p_ankle, 3.9, 2.9, 4, "FLESH_DARK", overlap=0.50, aspect_z=1.10)
        mid_sh = 0.5 * (p_knee + p_ankle)
        # Tấm giáp ống quyển dọc vuốt nhọn & bắp chân cong mềm mại
        sh_cubes.append(make_centered_cube(mid_sh + np.array([0.0, 0.2, -1.65]), [3.2, 8.6, 1.65], "ARMOR_STEEL", rot=[-7.0, 0.0, s * -3.0]))
        sh_cubes.append(make_centered_cube(mid_sh + np.array([0.0, 0.2, -2.25]), [1.4, 8.2, 1.05], "ARMOR_SPIKE", rot=[-7.0, 0.0, s * -3.0]))
        sh_cubes.append(make_centered_cube(mid_sh + np.array([s * 1.65, 0.1, -0.15]), [1.45, 8.2, 3.6], "ARMOR_STEEL", rot=[-7.0, -s * 10.0, s * -5.0]))
        sh_cubes.append(make_centered_cube(p_knee * 0.62 + p_ankle * 0.38 + np.array([0.0, 0.0, +1.75]), [3.3, 4.6, 1.85], "ARMOR_STEEL", rot=[8.0, 0.0, 0.0]))
        # 2 vây gai cong hữu cơ bên bắp chân
        add_curved_rod(sh_cubes, [
            (p_knee * 0.65 + p_ankle * 0.35 + np.array([s * 1.6, 0.0, 0.5])).tolist(),
            (p_knee * 0.52 + p_ankle * 0.48 + np.array([s * 3.2, 1.2, 1.4])).tolist(),
        ], 0.80, 0.20, "ARMOR_SPIKE", segs_per_span=2)
        add_curved_rod(sh_cubes, [
            (p_knee * 0.32 + p_ankle * 0.68 + np.array([s * 1.4, 0.0, 0.4])).tolist(),
            (p_knee * 0.22 + p_ankle * 0.78 + np.array([s * 2.8, 0.9, 1.1])).tolist(),
        ], 0.70, 0.18, "ARMOR_SPIKE", segs_per_span=2)

        bones.append({"name": f"shin_{tag}", "parent": f"thigh_{tag}", "pivot": p_knee.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": sh_cubes})

        # 3. `ankle_hock_l/r`: Cổ chân giáp gọn thanh thoát
        hk_cubes = [
            make_centered_cube(p_ankle + np.array([0.0, 0.4, 0.0]), [3.5, 2.0, 3.9], "ARMOR_STEEL"),
            make_centered_cube(p_ankle + np.array([0.0, 0.5, -1.85]), [2.1, 1.7, 1.0], "ARMOR_SPIKE", rot=[16.0, 0.0, 0.0]),
        ]
        add_curved_rod(hk_cubes, [
            (p_ankle + np.array([0.0, 0.4, 1.6])).tolist(),
            (p_ankle + np.array([0.0, 1.3, 3.8])).tolist(),
        ], 0.82, 0.20, "ARMOR_SPIKE", segs_per_span=2)
        bones.append({"name": f"ankle_hock_{tag}", "parent": f"shin_{tag}", "pivot": p_ankle.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": hk_cubes})

        # 4. `foot_l/r`: Ủng chiến giáp gothic vảy cong đặt phẳng Y = 0.0 + 3 vuốt cong mềm sắc bén!
        R_ft = bedrock_rot_matrix([0.0, s * -10.0, 0.0])

        def ft_pt(lx, ly, lz):
            return (np.array([p_ankle[0], 0.0, p_ankle[2]]) + R_ft @ np.array([lx, ly, lz], dtype=np.float64)).tolist()

        def ft_box(lx, ly, lz, sx, sy, sz, mat, extra_rot=(0.0, 0.0, 0.0)):
            lc = np.array([lx + sx * 0.5, ly + sy * 0.5, lz + sz * 0.5], dtype=np.float64)
            wc = np.array([p_ankle[0], 0.0, p_ankle[2]]) + R_ft @ lc
            rot = matrix_to_bedrock_euler(R_ft @ bedrock_rot_matrix(extra_rot))
            return make_cube([wc[0] - sx * 0.5, wc[1] - sy * 0.5, wc[2] - sz * 0.5], [sx, sy, sz], mat, rot=rot, pivot=wc.tolist())

        ft_cubes = [
            ft_box(-2.15, 0.0,  -3.4, 4.3, 1.50, 6.0, "ARMOR_STEEL"),
            ft_box(-1.90, 1.25, -3.0, 3.8, 1.70, 5.0, "ARMOR_STEEL"),
            ft_box(-1.50, 1.65, -4.1, 3.0, 1.35, 3.8, "ARMOR_SPIKE", extra_rot=(18, 0, 0)),
            ft_box(-1.00, 0.40, -4.9, 2.0, 1.15, 2.0, "ARMOR_SPIKE", extra_rot=(10, 0, 0)),
        ]
        for tx_b, tang_deg, tlen in ((-1.30, -14.0, 2.7), (0.0, 0.0, 3.3), (+1.30, +14.0, 2.7)):
            rad = math.radians(tang_deg)
            dx, dz = math.sin(rad), -math.cos(rad)
            p0 = ft_pt(tx_b, 1.15, -3.6)
            p1 = ft_pt(tx_b + dx * tlen * 0.48, 0.82, -3.6 + dz * tlen * 0.48)
            p2 = ft_pt(tx_b + dx * tlen,        0.16, -3.6 + dz * tlen)
            add_chain_rod(ft_cubes, p0, p1, 0.98, 0.76, 2, "ARMOR_SPIKE", overlap=0.16)
            add_claw_spike(ft_cubes, p1, p2, 0.72, "CLAW_BLOOD", 2)
        add_claw_spike(ft_cubes, ft_pt(0.0, 1.05, +2.2), ft_pt(0.0, 0.30, +4.1), 0.76, "ARMOR_SPIKE", 2)

        bones.append({"name": f"foot_{tag}", "parent": f"ankle_hock_{tag}", "pivot": p_ankle.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": ft_cubes})

    build_leg_chain(+1.0)
    build_leg_chain(-1.0)

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    assert 1000 <= total_cubes <= 2800, f"Cube count {total_cubes} ngoài khoảng 1000-2800!"

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.nightmare_knight",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 22.0,
                    "visible_bounds_height": 18.0,
                    "visible_bounds_offset": [0.0, 7.5, 0.0],
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

    # Thép rèn gothic tối cổ điển (BỎ hẳn viền sáng bóng nhựa kiểu đồ chơi robot -> rèn búa nhám, vệt chiến trận, ánh thép lạnh tự nhiên!)
    c_st0 = hex_arr("#0E0F14")
    c_st1 = hex_arr("#1A1C23")
    c_st2 = hex_arr("#2B2F3A")
    c_st3 = hex_arr("#464D5E")
    c_st4 = hex_arr("#768094")

    c_fire_out = hex_arr("#B81214")
    c_fire_mid = hex_arr("#FF3818")
    c_fire_hot = hex_arr("#FFE89C")

    # 1. ARMOR_STEEL & ARMOR_SPIKE (256x256 mỗi ô): Sắt rèn cổ đại có hạt búa đập, rỉ máu mờ ở khe sâu & xước kiếm chiến trận!
    for key, seed, hi_boost in (("ARMOR_STEEL", 11, 0.42), ("ARMOR_SPIKE", 22, 0.88)):
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        nx, ny = xx / w, yy / h
        rng = np.random.default_rng(seed)

        # Kết cấu rèn búa đa tầng (hammered dark iron — triệt tiêu 100% cảm giác mặt nhựa láng!)
        grid_coarse = rng.normal(0.0, 1.0, (h // 16, w // 16)).astype(np.float32)
        grid_med    = rng.normal(0.0, 1.0, (h // 8,  w // 8)).astype(np.float32)
        grid_fine   = rng.normal(0.0, 1.0, (h // 4,  w // 4)).astype(np.float32)
        n_c = np.repeat(np.repeat(grid_coarse, 16, axis=0), 16, axis=1)
        n_m = np.repeat(np.repeat(grid_med,     8, axis=0),  8, axis=1)
        n_f = np.repeat(np.repeat(grid_fine,    4, axis=0),  4, axis=1)
        forged_noise = np.clip(0.50 + 0.16 * n_c + 0.14 * n_m + 0.08 * n_f, 0.08, 0.96)
        damascus = 0.5 + 0.5 * np.sin((nx * 14.0 + ny * 5.0 + n_c * 0.8) * math.pi)

        # Bóng đổ AO mềm tự nhiên ở mép khe (KHÔNG vẽ viền xám trắng sáng chạy quanh từng ô cube nữa!)
        edge_x = np.minimum(nx, 1.0 - nx) * 2.0
        ao_seam = np.clip((1.0 - np.exp(-((1.0 - ny) * 8.0))) * np.clip(edge_x * 4.5, 0.45, 1.0), 0.30, 1.0)
        # Chỉ nhấn ánh thép mảnh trên gờ sống giữa & mép rèn nhọn của ARMOR_SPIKE
        ridge_hi = (
            np.exp(-(((nx - 0.50) * 14.0) ** 2)) * (0.62 if key == "ARMOR_SPIKE" else 0.14)
            + np.exp(-(((ny - 0.08) * 18.0) ** 2)) * (0.48 if key == "ARMOR_SPIKE" else 0.16)
        )

        # Vết chém xước chiến trận & vết rỉ máu tối mờ ở rãnh sâu
        s_img = Image.new("L", (w, h), 0)
        sd = ImageDraw.Draw(s_img)
        for _ in range(22):
            sx0, sy0 = int(rng.integers(10, w - 10)), int(rng.integers(10, h - 10))
            sd.line([(sx0, sy0), (sx0 + int(rng.integers(-54, 54)), sy0 + int(rng.integers(-34, 34)))], fill=int(rng.integers(130, 235)), width=int(rng.integers(1, 3)))
        scratch = np.array(s_img, dtype=np.float32) / 255.0

        grad = np.clip(0.28 + 0.44 * (1.0 - ny) * 0.8 + 0.28 * forged_noise + 0.10 * damascus, 0.0, 1.0)
        col = c_st0 * (1.0 - ao_seam[:, :, None]) + (c_st1 * (1.0 - grad[:, :, None]) + c_st2 * grad[:, :, None]) * ao_seam[:, :, None]
        # Rỉ đỏ huyết cổ rất nhẹ trong hốc tối cho chiều sâu dark-fantasy
        patina = np.clip((1.0 - ao_seam) * 0.6 + (1.0 - forged_noise) * 0.25, 0.0, 0.42)
        col = col * (1.0 - patina[:, :, None] * 0.35) + hex_arr("#341016") * (patina[:, :, None] * 0.35)
        col += (c_st3 - c_st2) * np.clip(ridge_hi * hi_boost + (forged_noise > 0.72) * 0.28, 0.0, 1.0)[:, :, None]
        col += (c_st4 - c_st3) * np.clip(scratch * 0.52 + ridge_hi * 0.32 * hi_boost, 0.0, 1.0)[:, :, None]
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

    # 4. BONE_RIB & SKULL_BONE (256x256 mỗi ô): Xương cổ hữu cơ có vân thớ xương, vết ố & khe nứt mảnh tự nhiên!
    for key, seed_b, c_lo, c_mid_h, c_hi_h in (
        ("BONE_RIB",   71, "#4A403A", "#988B7F", "#CFC3B6"),
        ("SKULL_BONE", 72, "#625852", "#B2A69A", "#E4DBD0"),
    ):
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        rng_b = np.random.default_rng(seed_b)
        mottle = np.repeat(np.repeat(rng_b.normal(0.0, 1.0, (h // 8, w // 8)).astype(np.float32), 8, axis=0), 8, axis=1)
        striation = 0.90 + 0.10 * np.sin((xx / w) * math.pi * 22.0 + mottle * 1.2)
        cyl = np.clip(1.0 - np.abs((xx / w) * 2.0 - 1.0) + 0.12 * mottle, 0.0, 1.0)
        joint = 0.88 + 0.12 * np.sin((yy / h) * math.pi * 5.0)
        c_b0 = hex_arr(c_lo)
        c_b1 = hex_arr(c_mid_h)
        c_b2 = hex_arr(c_hi_h)
        b_col = (c_b0 * (1.0 - cyl[:, :, None]) + c_b1 * cyl[:, :, None] + (c_b2 - c_b1) * (cyl ** 2.0)[:, :, None]) * joint[:, :, None] * striation[:, :, None]
        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(b_col, 0, 255).astype(np.uint8)
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(b_col * np.array([1.08, 0.93, 0.89]), 0, 255).astype(np.uint8)

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

    # 11. CLAW_BLOOD (128x128) + HEART_OUTER (64x128) + HEART_CORE (64x128 — Trái Tim Nightmare Core phát sáng fullbright!)
    x0, y0, w, h = MAT["CLAW_BLOOD"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ny = yy / h
    cl = hex_arr("#1E1216") * (1.0 - ny[:, :, None]) + hex_arr("#A81A28") * ny[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(cl, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(cl + c_fire_mid * (ny ** 1.5 * 0.55)[:, :, None], 0, 255).astype(np.uint8)

    # HEART_OUTER: Vỏ cơ tim đỏ huyết thẩm có gân mạch hữu cơ đập sáng nhẹ
    x0, y0, w, h = MAT["HEART_OUTER"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h
    rr = np.sqrt(((nx - 0.5) * 1.9) ** 2 + ((ny - 0.5) * 1.7) ** 2)
    vein = np.exp(-((np.sin((nx * 5.0 + ny * 3.5) * math.pi) * 5.5) ** 2))
    pulse_c = np.clip(1.0 - rr * 0.85, 0.0, 1.0)
    ho = hex_arr("#520818") * (1.0 - pulse_c[:, :, None]) + hex_arr("#C8183E") * pulse_c[:, :, None] + hex_arr("#FF5678") * (pulse_c ** 2.2)[:, :, None]
    ho = ho * (1.0 - vein[:, :, None] * 0.35) + hex_arr("#2A040C") * (vein[:, :, None] * 0.35)
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(ho, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(ho * 1.15, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(pulse_c * 210.0, 60, 230).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(ho * 1.25 + c_fire_mid * 0.25, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(ho * 1.2 + c_fire_mid * 0.4, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = 245

    # HEART_CORE: Lõi Nightmare Core hồng san hô–trắng hồng mờ viền hữu cơ (chuyển êm về đỏ huyết ở mép -> KHÔNG viền vuông!)
    x0, y0, w, h = MAT["HEART_CORE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h
    rr = np.sqrt(((nx - 0.5) * 2.15) ** 2 + ((ny - 0.5) * 2.05) ** 2)
    hc_t = np.clip(1.0 - rr, 0.0, 1.0) ** 1.35
    hc = hex_arr("#981030") * (1.0 - hc_t[:, :, None]) + hex_arr("#FF7A84") * hc_t[:, :, None] + hex_arr("#FFF2E8") * (hc_t ** 2.1)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(hc, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(hc * 1.12, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(145.0 + hc_t * 110.0, 0, 255).astype(np.uint8)
    hc_p2 = hex_arr("#C81818") * (1.0 - hc_t[:, :, None]) + hex_arr("#FFECC8") * (hc_t ** 0.90)[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(hc_p2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(hc_p2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = 255

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
        # IDLE (4.00s): Thở sâu lan qua 3 đốt cột sống (`torso` -> `torso_upper` -> `chest`) + nhún vai + cổ/đầu + cánh + choàng 3 tầng!
        "animation.nightmare_knight.idle": {
            "loop": True,
            "animation_length": 4.00,
            "bones": {
                "pelvis": {
                    "position": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([0, -0.55, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")},
                    "rotation": {"0.0": kf([0, -1, 0], "easeInOutSine"), "2.0": kf([1.2, 1, 0], "easeInOutSine"), "4.0": kf([0, -1, 0], "easeInOutSine")},
                },
                "torso": {"rotation": {"0.0": kf([0.5, 0, 0], "easeInOutSine"), "1.90": kf([2.2, 0, 0], "easeInOutSine"), "4.0": kf([0.5, 0, 0], "easeInOutSine")}},
                "torso_upper": {"rotation": {"0.0": kf([0.5, 0, 0], "easeInOutSine"), "2.00": kf([2.4, 0, 0], "easeInOutSine"), "4.0": kf([0.5, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0.8, 0, 0], "easeInOutSine"), "2.10": kf([2.8, 0, 0], "easeInOutSine"), "4.0": kf([0.8, 0, 0], "easeInOutSine")}},
                # Nhịp tim đập đôi (*lub-dub*) sống động cho Trái Tim Nightmare Core trong lồng ngực mở!
                "heart": {
                    "scale": {
                        "0.0":  kf([1.00, 1.00, 1.00], "easeInOutSine"),
                        "0.22": kf([1.12, 1.10, 1.14], "easeOutCubic"),
                        "0.44": kf([0.96, 0.97, 0.96], "easeInOutSine"),
                        "0.68": kf([1.08, 1.07, 1.10], "easeOutCubic"),
                        "1.00": kf([1.00, 1.00, 1.00], "easeInOutSine"),
                        "2.00": kf([1.00, 1.00, 1.00], "easeInOutSine"),
                        "2.22": kf([1.12, 1.10, 1.14], "easeOutCubic"),
                        "2.44": kf([0.96, 0.97, 0.96], "easeInOutSine"),
                        "2.68": kf([1.08, 1.07, 1.10], "easeOutCubic"),
                        "3.00": kf([1.00, 1.00, 1.00], "easeInOutSine"),
                        "4.00": kf([1.00, 1.00, 1.00], "easeInOutSine"),
                    },
                },
                "shoulder_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.05": kf([1.5, 0, -3.0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "shoulder_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.05": kf([1.5, 0, 3.0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "neck": {"rotation": {"0.0": kf([-0.5, -1.5, 0], "catmullrom"), "1.7": kf([1.2, 1.8, 0.5], "catmullrom"), "3.0": kf([0.5, -1.2, -0.5], "catmullrom"), "4.0": kf([-0.5, -1.5, 0], "catmullrom")}},
                "head": {"rotation": {"0.0": kf([0.5, -2.5, 0], "catmullrom"), "1.7": kf([2.0, 2.8, 0.8], "catmullrom"), "3.0": kf([1.2, -2.0, -0.8], "catmullrom"), "4.0": kf([0.5, -2.5, 0], "catmullrom")}},
                "jaw": {"rotation": {"0.0": kf([3, 0, 0], "easeInOutSine"), "2.1": kf([10, 0, 0], "easeInOutSine"), "4.0": kf([3, 0, 0], "easeInOutSine")}},
                # Nhún nhẹ khớp gối chân thẳng giữ đế ủng phẳng mặt đất
                "thigh_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([5.0, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "foot_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "thigh_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([5.0, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "foot_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.0": kf([-2.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([2, 0, 1], "easeInOutSine"), "2.1": kf([6, 0, 3], "easeInOutSine"), "4.0": kf([2, 0, 1], "easeInOutSine")}},
                "arm_r_lower": {"rotation": {"0.0": kf([-8, 0, 0], "easeInOutSine"), "2.15": kf([-13, 0, 0], "easeInOutSine"), "4.0": kf([-8, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([-2, 0, -1], "easeInOutSine"), "2.1": kf([2, 0, -3], "easeInOutSine"), "4.0": kf([-2, 0, -1], "easeInOutSine")}},
                "arm_l_lower": {"rotation": {"0.0": kf([-10, 0, 0], "easeInOutSine"), "2.15": kf([-15, 0, 0], "easeInOutSine"), "4.0": kf([-10, 0, 0], "easeInOutSine")}},
                "weapon": {"rotation": {"0.0": kf([1, 0, 0], "easeInOutSine"), "2.25": kf([4, 0, -1.5], "easeInOutSine"), "4.0": kf([1, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.15": kf([2.5, -6, 3], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.15": kf([2.5, 6, -3], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l_mid": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.35": kf([0, -5, 2.5], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r_mid": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.35": kf([0, 5, -2.5], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([1.5, 0, 0], "easeInOutSine"), "2.1": kf([5.5, 0, 0], "easeInOutSine"), "4.0": kf([1.5, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([2.0, 0, 0], "easeInOutSine"), "2.35": kf([7.5, 0, 0], "easeInOutSine"), "4.0": kf([2.0, 0, 0], "easeInOutSine")}},
                "cape_lower": {"rotation": {"0.0": kf([2.5, 0, 0], "easeInOutSine"), "2.60": kf([9.5, 0, 0], "easeInOutSine"), "4.0": kf([2.5, 0, 0], "easeInOutSine")}},
                "cape_l": {"rotation": {"0.0": kf([1.5, 0, 1], "easeInOutSine"), "2.3": kf([5.5, -3, 4], "easeInOutSine"), "4.0": kf([1.5, 0, 1], "easeInOutSine")}},
                "cape_r": {"rotation": {"0.0": kf([1.5, 0, -1], "easeInOutSine"), "2.3": kf([5.5, 3, -4], "easeInOutSine"), "4.0": kf([1.5, 0, -1], "easeInOutSine")}},
                "tabard": {"rotation": {"0.0": kf([0, 0, 0], "easeInOutSine"), "2.20": kf([3.5, 0, 0], "easeInOutSine"), "4.0": kf([0, 0, 0], "easeInOutSine")}},
                "tabard_lower": {"rotation": {"0.0": kf([1, 0, 0], "easeInOutSine"), "2.45": kf([6.0, 0, 0], "easeInOutSine"), "4.0": kf([1, 0, 0], "easeInOutSine")}},
            },
        },
        # WALK (1.80s = 36t): Bước chân hiệp sĩ thẳng đầy uy lực (gối gập ngược tự nhiên + vặn 3 đốt cột sống + xoay vai + choàng/khố bay)!
        "animation.nightmare_knight.walk": {
            "loop": True,
            "animation_length": 1.80,
            "bones": {
                "pelvis": {
                    "position": {
                        "0.0":  kf([0.0, 0.4, 0.0], "catmullrom"),
                        "0.45": kf([1.4, -1.6, 0.0], "easeOutCubic"),
                        "0.90": kf([0.0, 0.4, 0.0], "catmullrom"),
                        "1.35": kf([-1.4, -1.6, 0.0], "easeOutCubic"),
                        "1.80": kf([0.0, 0.4, 0.0], "catmullrom"),
                    },
                    "rotation": {
                        "0.0":  kf([2, 6, 0], "catmullrom"),
                        "0.45": kf([4, 8, -3.0], "easeOutCubic"),
                        "0.90": kf([2, -6, 0], "catmullrom"),
                        "1.35": kf([4, -8, 3.0], "easeOutCubic"),
                        "1.80": kf([2, 6, 0], "catmullrom"),
                    },
                },
                "torso": {
                    "rotation": {
                        "0.0":  kf([1.5, -2.5, 0], "catmullrom"),
                        "0.45": kf([2.5, -3.5, 1.2], "easeOutCubic"),
                        "0.90": kf([1.5, 2.5, 0], "catmullrom"),
                        "1.35": kf([2.5, 3.5, -1.2], "easeOutCubic"),
                        "1.80": kf([1.5, -2.5, 0], "catmullrom"),
                    },
                },
                "torso_upper": {
                    "rotation": {
                        "0.0":  kf([1.5, -3.0, 0], "catmullrom"),
                        "0.45": kf([3.0, -4.0, 1.2], "easeOutCubic"),
                        "0.90": kf([1.5, 3.0, 0], "catmullrom"),
                        "1.35": kf([3.0, 4.0, -1.2], "easeOutCubic"),
                        "1.80": kf([1.5, -3.0, 0], "catmullrom"),
                    },
                },
                "chest": {
                    "rotation": {
                        "0.0":  kf([2.0, -3.5, 0], "catmullrom"),
                        "0.45": kf([3.5, -4.5, 1.2], "easeOutCubic"),
                        "0.90": kf([2.0, 3.5, 0], "catmullrom"),
                        "1.35": kf([3.5, 4.5, -1.2], "easeOutCubic"),
                        "1.80": kf([2.0, -3.5, 0], "catmullrom"),
                    },
                },
                "heart": {
                    "scale": {
                        "0.0":  kf([1.00, 1.00, 1.00], "easeInOutSine"),
                        "0.25": kf([1.14, 1.12, 1.16], "easeOutCubic"),
                        "0.55": kf([0.97, 0.97, 0.97], "easeInOutSine"),
                        "0.90": kf([1.00, 1.00, 1.00], "easeInOutSine"),
                        "1.15": kf([1.14, 1.12, 1.16], "easeOutCubic"),
                        "1.45": kf([0.97, 0.97, 0.97], "easeInOutSine"),
                        "1.80": kf([1.00, 1.00, 1.00], "easeInOutSine"),
                    },
                },
                "shoulder_l": {"rotation": {"0.0": kf([3, 0, -2], "catmullrom"), "0.90": kf([-4, 0, -4], "catmullrom"), "1.80": kf([3, 0, -2], "catmullrom")}},
                "shoulder_r": {"rotation": {"0.0": kf([-3, 0, 2], "catmullrom"), "0.90": kf([4, 0, 4], "catmullrom"), "1.80": kf([-3, 0, 2], "catmullrom")}},
                "neck": {"rotation": {"0.0": kf([-1, 2, 0], "easeInOutSine"), "0.90": kf([-1, -2, 0], "easeInOutSine"), "1.80": kf([-1, 2, 0], "easeInOutSine")}},
                "head": {
                    "rotation": {
                        "0.0":  kf([-1.5, 3, 0], "easeInOutSine"),
                        "0.55": kf([1.5, 4.5, -0.8], "easeInOutSine"),
                        "1.45": kf([1.5, -4.5, 0.8], "easeInOutSine"),
                        "1.80": kf([-1.5, 3, 0], "easeInOutSine"),
                    },
                },
                # Chân trái thẳng chuẩn giải phẫu: bước tới (-22°) -> đặt gót trụ (-8°, gối gập 14°) -> đẩy sau (+20°) -> nhấc gối gập mượt (+34°)!
                "leg_l":        {"rotation": {"0.0": kf([-22, 0, 0], "catmullrom"), "0.45": kf([-8, 0, 0], "easeOutCubic"), "0.90": kf([20, 0, 0], "catmullrom"), "1.35": kf([-4, 0, 0], "catmullrom"), "1.80": kf([-22, 0, 0], "catmullrom")}},
                "shin_l":       {"rotation": {"0.0": kf([10, 0, 0], "catmullrom"),  "0.45": kf([14, 0, 0], "easeOutCubic"), "0.90": kf([8, 0, 0], "catmullrom"),  "1.35": kf([34, 0, 0], "catmullrom"), "1.80": kf([10, 0, 0], "catmullrom")}},
                "ankle_hock_l": {"rotation": {"0.0": kf([6, 0, 0], "catmullrom"),   "0.45": kf([-4, 0, 0], "easeOutCubic"), "0.90": kf([-14, 0, 0], "catmullrom"),"1.35": kf([-16, 0, 0], "catmullrom"),"1.80": kf([6, 0, 0], "catmullrom")}},
                "foot_l":       {"rotation": {"0.0": kf([6, 0, 0], "catmullrom"),   "0.45": kf([-2, 0, 0], "easeOutCubic"), "0.90": kf([-14, 0, 0], "catmullrom"),"1.35": kf([4, 0, 0], "catmullrom"),  "1.80": kf([6, 0, 0], "catmullrom")}},
                # Chân phải đối pha hoàn hảo
                "leg_r":        {"rotation": {"0.0": kf([20, 0, 0], "catmullrom"),  "0.45": kf([-4, 0, 0], "catmullrom"),   "0.90": kf([-22, 0, 0], "catmullrom"),"1.35": kf([-8, 0, 0], "easeOutCubic"),"1.80": kf([20, 0, 0], "catmullrom")}},
                "shin_r":       {"rotation": {"0.0": kf([8, 0, 0], "catmullrom"),   "0.45": kf([34, 0, 0], "catmullrom"),   "0.90": kf([10, 0, 0], "catmullrom"), "1.35": kf([14, 0, 0], "easeOutCubic"),"1.80": kf([8, 0, 0], "catmullrom")}},
                "ankle_hock_r": {"rotation": {"0.0": kf([-14, 0, 0], "catmullrom"), "0.45": kf([-16, 0, 0], "catmullrom"),  "0.90": kf([6, 0, 0], "catmullrom"),  "1.35": kf([-4, 0, 0], "easeOutCubic"),"1.80": kf([-14, 0, 0], "catmullrom")}},
                "foot_r":       {"rotation": {"0.0": kf([-14, 0, 0], "catmullrom"), "0.45": kf([4, 0, 0], "catmullrom"),    "0.90": kf([6, 0, 0], "catmullrom"),  "1.35": kf([-2, 0, 0], "easeOutCubic"),"1.80": kf([-14, 0, 0], "catmullrom")}},
                "arm_l":       {"rotation": {"0.0": kf([16, 0, -2], "catmullrom"), "0.90": kf([-18, 0, -4], "catmullrom"), "1.80": kf([16, 0, -2], "catmullrom")}},
                "arm_l_lower": {"rotation": {"0.0": kf([-10, 0, 0], "catmullrom"), "0.90": kf([-24, 0, 0], "catmullrom"), "1.80": kf([-10, 0, 0], "catmullrom")}},
                "arm_r":       {"rotation": {"0.0": kf([-6, 0, 3], "catmullrom"), "0.90": kf([12, 0, 5], "catmullrom"), "1.80": kf([-6, 0, 3], "catmullrom")}},
                "arm_r_lower": {"rotation": {"0.0": kf([-14, 0, 0], "catmullrom"), "0.90": kf([-8, 0, 0], "catmullrom"), "1.80": kf([-14, 0, 0], "catmullrom")}},
                "wing_l": {"rotation": {"0.0": kf([4, -8, 3], "easeInOutSine"), "0.60": kf([8, -14, 5], "easeInOutSine"), "1.50": kf([8, -10, 4], "easeInOutSine"), "1.80": kf([4, -8, 3], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([4, 8, -3], "easeInOutSine"), "0.60": kf([8, 10, -4], "easeInOutSine"), "1.50": kf([8, 14, -5], "easeInOutSine"), "1.80": kf([4, 8, -3], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([10, 0, 0], "easeInOutSine"), "0.55": kf([16, 0, 0], "easeInOutSine"), "1.45": kf([16, 0, 0], "easeInOutSine"), "1.80": kf([10, 0, 0], "easeInOutSine")}},
                "cape_mid": {"rotation": {"0.0": kf([8, 0, 0], "easeInOutSine"), "0.70": kf([14, 0, 0], "easeInOutSine"), "1.60": kf([14, 0, 0], "easeInOutSine"), "1.80": kf([8, 0, 0], "easeInOutSine")}},
                "cape_lower": {"rotation": {"0.0": kf([6, 0, 0], "easeInOutSine"), "0.85": kf([12, 0, 0], "easeInOutSine"), "1.75": kf([12, 0, 0], "easeInOutSine"), "1.80": kf([6, 0, 0], "easeInOutSine")}},
                "cape_l": {"rotation": {"0.0": kf([10, -4, 4], "easeInOutSine"), "0.90": kf([16, -8, 7], "easeInOutSine"), "1.80": kf([10, -4, 4], "easeInOutSine")}},
                "cape_r": {"rotation": {"0.0": kf([10, 4, -4], "easeInOutSine"), "0.90": kf([16, 8, -7], "easeInOutSine"), "1.80": kf([10, 4, -4], "easeInOutSine")}},
                "tabard": {"rotation": {"0.0": kf([10, 0, 0], "easeInOutSine"), "0.90": kf([-5, 0, 0], "easeInOutSine"), "1.80": kf([10, 0, 0], "easeInOutSine")}},
                "tabard_lower": {"rotation": {"0.0": kf([14, 0, 0], "easeInOutSine"), "0.90": kf([4, 0, 0], "easeInOutSine"), "1.80": kf([14, 0, 0], "easeInOutSine")}},
            },
        },
        # 1. GREATSWORD_SLASH: total=32t (1.60s) | HitWindow=[18..21] (0.90..1.05s)
        "animation.nightmare_knight.slash": {
            "animation_length": 1.60,
            "bones": {
                "pelvis": {
                    "position": {"0.0": kf([0, 0, 0]), "0.65": kf([0, -1.5, 0.8], "easeInCubic"), "0.95": kf([0, -0.6, -1.6], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeOutCubic")},
                    "rotation": {"0.0": kf([0, 0, 0]), "0.65": kf([4, 24, 0], "easeInCubic"), "0.90": kf([8, -22, 0], "easeOutExpo"), "1.12": kf([8, -28, 0], "easeOutCubic"), "1.60": kf([0, 0, 0], "easeInOutSine")},
                },
                "torso_upper": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([4, 20, -2], "easeInCubic"), "0.90": kf([7, -12, 2], "easeOutExpo"), "1.08": kf([8, -24, 3], "easeOutBack"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "0.72": kf([4, 28, -2], "easeInCubic"), "0.90": kf([9, -14, 2], "easeOutExpo"), "1.08": kf([10, -34, 3], "easeOutBack"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "shoulder_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.74": kf([-10, 12, -14], "easeInCubic"), "0.92": kf([8, -14, 10], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.65": kf([12, 0, 0], "easeInCubic"), "0.95": kf([6, 0, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-40, 32, -46], "easeInCubic"), "0.90": kf([-72, -22, -8], "easeOutExpo"), "1.08": kf([-58, -64, 24], "easeOutBack"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r_lower": {"rotation": {"0.0": kf([0, 0, 0]), "0.75": kf([-24, 0, 0], "easeInCubic"), "0.96": kf([-6, 0, 0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")}},
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
                "pelvis": {"position": {"0.0": kf([0, 0, 0]), "1.00": kf([0, 0.8, 1.2], "easeInCubic"), "1.20": kf([0, -3.2, -1.6], "easeOutExpo"), "1.52": kf([0, -2.4, -1.2], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "thigh_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([-14, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([26, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "foot_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([-12, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "thigh_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([-14, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([26, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "foot_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.20": kf([-12, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "torso_upper": {"rotation": {"0.0": kf([0, 0, 0]), "1.02": kf([-12, 0, 0], "easeInCubic"), "1.20": kf([16, 0, 0], "easeOutExpo"), "1.48": kf([17, 0, 0], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "1.02": kf([-16, 0, 0], "easeInCubic"), "1.20": kf([20, 0, 0], "easeOutExpo"), "1.48": kf([21, 0, 0], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "shoulder_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-16, 0, -14], "easeInCubic"), "1.20": kf([12, 0, 6], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "shoulder_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-14, 0, 14], "easeInCubic"), "1.20": kf([10, 0, -6], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-144, 0, -10], "easeInCubic"), "1.20": kf([24, 0, 0], "easeOutExpo"), "1.48": kf([28, 0, 0], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-116, 0, 10], "easeInCubic"), "1.20": kf([20, 0, 6], "easeOutExpo"), "1.48": kf([22, 0, 6], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "neck": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-10, 0, 0], "easeInCubic"), "1.22": kf([10, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "head": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([-14, 0, 0], "easeInCubic"), "1.22": kf([14, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "1.05": kf([36, 0, 0], "easeInCubic"), "1.42": kf([26, 0, 0], "easeOutCubic"), "2.00": kf([0, 0, 0], "easeInOutSine")}},
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
                # Chân thẳng gập nhún bật nhảy -> duỗi trên không -> tiếp đất đàn hồi!
                "leg_l":  {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-24, 0, 0], "easeInCubic"), "1.25": kf([16, 0, 0], "easeOutCubic"), "1.70": kf([-20, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([42, 0, 0], "easeInCubic"),  "1.25": kf([14, 0, 0], "easeOutCubic"), "1.70": kf([36, 0, 0], "easeOutExpo"),  "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "foot_l": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-18, 0, 0], "easeInCubic"), "1.25": kf([20, 0, 0], "easeOutCubic"), "1.70": kf([-16, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "leg_r":  {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-24, 0, 0], "easeInCubic"), "1.25": kf([16, 0, 0], "easeOutCubic"), "1.70": kf([-20, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([42, 0, 0], "easeInCubic"),  "1.25": kf([14, 0, 0], "easeOutCubic"), "1.70": kf([36, 0, 0], "easeOutExpo"),  "2.40": kf([0, 0, 0], "easeInOutSine")}},
                "foot_r": {"rotation": {"0.0": kf([0, 0, 0]), "0.70": kf([-18, 0, 0], "easeInCubic"), "1.25": kf([20, 0, 0], "easeOutCubic"), "1.70": kf([-16, 0, 0], "easeOutExpo"), "2.40": kf([0, 0, 0], "easeInOutSine")}},
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
                "heart": {"scale": {"0.0": kf([1.0, 1.0, 1.0]), "0.80": kf([0.92, 0.92, 0.92], "easeInCubic"), "1.00": kf([1.26, 1.24, 1.28], "easeOutExpo"), "1.90": kf([1.0, 1.0, 1.0], "easeInOutSine")}},
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
                "heart": {"scale": {"0.0": kf([1.0, 1.0, 1.0]), "1.10": kf([1.18, 1.16, 1.20], "easeInOutSine"), "2.20": kf([0.90, 0.90, 0.90], "easeInCubic"), "2.40": kf([1.28, 1.26, 1.32], "easeOutExpo"), "3.45": kf([1.18, 1.16, 1.20], "easeOutCubic"), "4.20": kf([1.0, 1.0, 1.0], "easeInOutSine")}},
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
                "body": {"position": {"0.0": kf([0, 0, 0]), "1.75": kf([0, -4.6, 0], "easeInCubic"), "2.20": kf([0, 1.0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "thigh_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-20, 0, 0], "easeInCubic"), "2.20": kf([0, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_l":  {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([36, 0, 0], "easeInCubic"),  "2.20": kf([4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "foot_l":  {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-16, 0, 0], "easeInCubic"), "2.20": kf([-4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "thigh_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-20, 0, 0], "easeInCubic"), "2.20": kf([0, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "shin_r":  {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([36, 0, 0], "easeInCubic"),  "2.20": kf([4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "foot_r":  {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-16, 0, 0], "easeInCubic"), "2.20": kf([-4, 0, 0], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "torso_upper": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([12, 0, 0], "easeInCubic"), "2.20": kf([-14, 0, 0], "easeOutExpo"), "4.8": kf([-8, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "chest": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([16, 0, 0], "easeInCubic"), "2.20": kf([-18, 0, 0], "easeOutExpo"), "4.8": kf([-12, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "heart": {
                    "scale": {
                        "0.0":  kf([1.00, 1.00, 1.00]),
                        "0.60": kf([1.16, 1.14, 1.18], "easeOutCubic"),
                        "1.10": kf([0.94, 0.94, 0.94], "easeInOutSine"),
                        "1.65": kf([1.22, 1.20, 1.24], "easeInCubic"),
                        "2.20": kf([1.34, 1.30, 1.36], "easeOutExpo"),
                        "3.40": kf([1.18, 1.16, 1.20], "easeInOutSine"),
                        "4.80": kf([1.12, 1.10, 1.14], "easeInOutSine"),
                        "6.0":  kf([1.00, 1.00, 1.00], "easeInOutSine"),
                    },
                },
                "neck": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([10, 0, 0], "easeInCubic"), "2.20": kf([-12, 0, 0], "easeOutExpo"), "4.8": kf([-10, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "head": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([14, 0, 0], "easeInCubic"), "2.20": kf([-18, 0, 0], "easeOutExpo"), "4.8": kf([-14, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "jaw": {"rotation": {"0.0": kf([0, 0, 0]), "2.20": kf([42, 0, 0], "easeOutExpo"), "4.8": kf([36, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "shoulder_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([8, 0, -10], "easeInCubic"), "2.20": kf([-10, 0, 14], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "shoulder_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([8, 0, 10], "easeInCubic"),  "2.20": kf([-10, 0, -14], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "arm_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-25, 0, -16], "easeInCubic"), "2.20": kf([-72, 0, 64], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "arm_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([-25, 0, 16], "easeInCubic"), "2.20": kf([-90, 0, -54], "easeOutExpo"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([10, -34, 14], "easeInCubic"), "2.20": kf([-20, 28, -24], "easeOutExpo"), "4.8": kf([-14, 22, -18], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "wing_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.75": kf([10, 34, -14], "easeInCubic"), "2.20": kf([-20, -28, 24], "easeOutExpo"), "4.8": kf([-14, -22, 18], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
                "cape_upper": {"rotation": {"0.0": kf([0, 0, 0]), "2.25": kf([34, 0, 0], "easeOutExpo"), "4.8": kf([24, 0, 0], "easeInOutSine"), "6.0": kf([0, 0, 0], "easeInOutSine")}},
            },
        },
        # 15. DEATH: 2.40s — khuỵu gối rồi đổ gục nặng nề
        "animation.nightmare_knight.death": {
            "loop": "hold_on_last_frame",
            "animation_length": 2.40,
            "bones": {
                "body": {
                    "position": {"0.0": kf([0, 0, 0]), "1.1": kf([0, -7.5, 0], "easeInCubic"), "2.4": kf([0, -17.5, 0], "easeOutBounce")},
                    "rotation": {"0.0": kf([0, 0, 0]), "1.1": kf([28, 0, 0], "easeInCubic"), "2.4": kf([86, 0, 0], "easeOutCubic")},
                },
                "shin_l": {"rotation": {"0.0": kf([0, 0, 0]), "1.1": kf([46, 0, 0], "easeInCubic"), "2.4": kf([24, 0, 0], "easeOutCubic")}},
                "shin_r": {"rotation": {"0.0": kf([0, 0, 0]), "1.1": kf([46, 0, 0], "easeInCubic"), "2.4": kf([24, 0, 0], "easeOutCubic")}},
                "torso_upper": {"rotation": {"0.0": kf([0, 0, 0]), "2.4": kf([14, 0, 0], "easeOutCubic")}},
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
        ("preview_front_full.png",     180,  4, sc * 1.18, center, d_norm, g_norm, "FRONT V5 (ORGANIC FORGED ARMOR, OPEN RIBCAGE HEART, BALANCED LEGS)"),
        ("preview_face_closeup.png",   180,  2, sc * 3.15, np.array([0.0, 42.5, -2.5]), d_norm, g_norm, "FACE & OPEN RIBCAGE HEART CLOSE-UP (PULSING NIGHTMARE CORE)"),
        ("preview_side_left.png",      -90,  4, sc * 1.25, np.array([0.0, 26.5, 0.0]), d_norm, g_norm, "LEFT SIDE (ORGANIC CURVED ARMOR, NATURAL BENT ARMS, SHORTER LEGS)"),
        ("preview_side_right.png",      90,  4, sc * 1.25, np.array([0.0, 26.5, 0.0]), d_norm, g_norm, "RIGHT SIDE (GREATSWORD, OPEN CHEST ARCH, FLOWING CAPE)"),
        ("preview_rear_full.png",        0,  4, sc * 1.18, center, d_norm, g_norm, "REAR (WIDE 3-TIER A-LINE RED CAPE, SWEPT BAT WINGS)"),
        ("preview_phase2_enraged.png", 152,  8, sc * 1.20, center, d_enr, g_enr, "PHASE 2 ENRAGED (BLAZING NIGHTMARE HEART & MOLTEN ARMOR FISSURES)"),
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
