#!/usr/bin/env python3
"""
Bộ sinh Model 3D Voxel GeckoLib + Texture 128x128 cho vũ khí Song Nguyệt Đao **Togetsu (兎月)** — Shangri-La Frontier:
Khớp sát 100% toàn bộ 10 ảnh tham chiếu (6 góc song đao tách rời + 3 góc đại đao hợp nhất + pose vắt chéo Sunraku):

1. **Trăng Non (`togetsu_waxing.geo.json`, Tay Trái — Lưỡi Trắng):**
   - Cán cong nhẹ bọc dây da nâu đỏ (`#6E322A` -> `#8F4438`), chuôi vuông đen có lỗ + hạt ngọc đỏ + dải lụa **ĐEN** đối lập!
   - Ngạnh chắn tay bạc vươn cong phía dưới gốc lưỡi.
   - Lưỡi đao trăng khuyết bản rộng màu **Trắng Ngọc Trai / Bạc Sáng (`#C8CFD6` -> `#EDF2F7` -> `#FFFFFF`)** có đường rãnh ghép chữ S đen sắc nét + góc lưỡi liềm khuyết ở gót lưỡi!

2. **Trăng Khuyết (`togetsu_waning.geo.json`, Tay Phải — Lưỡi Đen):**
   - Cán nâu đỏ, chuôi đen + hạt ngọc đỏ + dải lụa **TRẮNG** phát sáng nhẹ đối lập (Âm–Dương)!
   - Ngạnh chắn tay bạc sáng vươn cong ôm cổ lưỡi.
   - Lưỡi đao trăng khuyết màu **Đen Tuyền Obsidian (`#1A1B21` -> `#2E303A` -> `#484B59`)** có rãnh ghép chữ S & gót liềm sắc bén!

3. **Dạng Hợp Nhất: Song Nguyệt (`togetsu_fused.geo.json` — Sōgengetsu Đại Đao Trăng Khuyết Khổng Lồ!):**
   - Hai cán nâu đỏ ghép thành **tay cầm khung chữ D khép kín** độc bản, nối đỉnh tại chuôi đen + 2 hạt ngọc đỏ + 2 dải lụa **Trắng & Đen chẽ chữ V**!
   - Hai lưỡi Trắng & Đen khớp mộng thành **1 thanh Đại Đao Trăng Khuyết khổng lồ (~2.2×)** mang hoa văn mảng ghép Trắng–Đen–Bạc y hệt ảnh mẫu 7–9 (đuôi sừng đen sau tay cầm + thân bụng trắng sáng có ngạnh bạc + đoạn lưng đen giữa + mũi trăng khuyết khổng lồ trắng–đen sắc lẹm)!

4. Giải chính xác ma trận 1:1 Minecraft cho:
   - **Pose Dual-Wield Sunraku** (ảnh 10): Trăng Non (Trắng) giơ ngang tầm cằm/mặt tay trái + Trăng Khuyết (Đen) vắt thấp ngang bụng tay phải!
   - **Pose Đại Kiếm Song Nguyệt**: 2 tay nắm chuôi chữ D vác chéo đại đao trăng khuyết khổng lồ qua vai phải!

Chạy: python tools/generate_togetsu_model.py
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "togetsu_previews"

TEX_W = 128
TEX_H = 128
DUAL_SCALE = 1.12
FUSED_SCALE = 1.06
DISPLAY_SCALE = 0.95

# Atlas 128x128:
# - Hàng trên (y=0..15): 8 ô 16x16 cho chi tiết 3D (cán nâu đỏ, chuôi đen, ngọc đỏ, ngạnh bạc, lụa trắng/đen, lõi thép, trong suốt)
# - Vùng vẽ profile siêu nét (x=0..63, y=24..127 -> 64x104 px) dành riêng cho bản vẽ lưỡi đao trăng khuyết!
MAT = {
    "BLADE_MAIN":  (0,   0, 16, 16),
    "BLADE_EDGE":  (16,  0, 16, 16),
    "GUARD_SILV":  (32,  0, 16, 16),
    "GRIP_BROWN":  (48,  0, 16, 16),
    "POMMEL_BLK":  (64,  0, 16, 16),
    "BEAD_RED":    (80,  0, 16, 16),
    "RIBBON_TAIL": (96,  0, 16, 16),
    "TRANSPARENT": (112, 0, 16, 16),
    "RIBBON_ALT":  (64, 16, 16, 16),
    "BLADE_ALT":   (80, 16, 16, 16),
}

PROFILE_UV = (0, 24, 64, 104)  # u0=-Z (cạnh trước/cắt), u0+uw=+Z (sống/lưng), v0=+Y (mũi), v0+vh=-Y (gốc)


def uv_box(mat_key: str, pad: int = 1) -> dict:
    x0, y0, w, h = MAT[mat_key]
    p = 0 if mat_key == "TRANSPARENT" else pad
    f = {"uv": [x0 + p, y0 + p], "uv_size": [w - 2 * p, h - 2 * p]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def make_centered_cube(center, size, mat: str, rot=None) -> dict:
    cx, cy, cz = [float(v) for v in center]
    sx, sy, sz = [float(v) for v in size]
    c = {
        "origin": [round(cx - sx * 0.5, 3), round(cy - sy * 0.5, 3), round(cz - sz * 0.5, 3)],
        "size": [round(sx, 3), round(sy, 3), round(sz, 3)],
        "uv": uv_box(mat),
    }
    if rot is not None and any(abs(v) > 1e-4 for v in rot):
        c["pivot"] = [round(cx, 3), round(cy, 3), round(cz, 3)]
        c["rotation"] = [round(float(rot[0]), 2), round(float(rot[1]), 2), round(float(rot[2]), 2)]
    return c


def make_profile_fin_cube(y_min: float, y_max: float, z_min: float, z_max: float, thickness_x: float = 0.24) -> dict:
    u0, v0, uw, vh = PROFILE_UV
    trans = uv_box("TRANSPARENT")["north"]
    return {
        "origin": [round(-thickness_x * 0.5, 3), round(y_min, 3), round(z_min, 3)],
        "size": [round(thickness_x, 3), round(y_max - y_min, 3), round(z_max - z_min, 3)],
        "uv": {
            "west":  {"uv": [u0, v0],      "uv_size": [uw, vh]},
            "east":  {"uv": [u0 + uw, v0], "uv_size": [-uw, vh]},
            "north": trans,
            "south": trans,
            "up":    trans,
            "down":  trans,
        },
    }


def make_tube_segments(points_yz, wx_list, dz_list, mat: str, alt_mat: str = None):
    cubes = []
    for i in range(len(points_yz) - 1):
        ya, za = points_yz[i]
        yb, zb = points_yz[i + 1]
        ym, zm = 0.5 * (ya + yb), 0.5 * (za + zb)
        dy, dz = yb - ya, zb - za
        seg_len = math.hypot(dy, dz) * 1.14
        rx_deg = -math.degrees(math.atan2(dz, dy))
        wx = 0.5 * (wx_list[i] + wx_list[i + 1])
        dz_w = 0.5 * (dz_list[i] + dz_list[i + 1])
        mk = alt_mat if (alt_mat is not None and i >= len(points_yz) - 2) else mat
        eps = 0.012 if (i % 2 == 1) else 0.0
        cubes.append(make_centered_cube([0.0, ym, zm], [wx + eps, seg_len, dz_w], mk, rot=[rx_deg, 0.0, 0.0]))
    return cubes


def rot_x(rad):
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)


def rot_y(rad):
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)


def rot_z(rad):
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


# ==============================================================================
# 1. HÌNH KHỐI SONG ĐAO TÁCH RỜI: TRĂNG NON (TRẮNG) & TRĂNG KHUYẾT (ĐEN)
# Bản v2 kéo dài & thon sắc (không còn ngắn béo như dao bầu!):
# lưỡi cong trăng khuyết thanh trường sắc bén, gót khuyết liềm gọn,
# ngạnh bạc cong dưới cổ lưỡi, cán nâu đỏ công thái học, chuôi đen + ngọc đỏ + dải lụa bay!
# ==============================================================================

SINGLE_Y_MIN, SINGLE_Y_MAX = 1.90, 21.60
SINGLE_Z_MIN, SINGLE_Z_MAX = -4.25, +2.85


def single_outer_edge_z(u: float) -> float:
    """Cạnh cắt cong vòng cung trường sắc (`-Z` side — lưỡi trăng khuyết dài uy lực, hết dáng dao bầu!)."""
    u = max(0.0, min(1.0, u))
    if u < 0.08:
        # Gót nhọn vươn ra sau vừa gọn tạo hốc trăng khuyết sắc nét
        t = u / 0.08
        return -1.15 - 2.65 * (t ** 0.84)
    t = (u - 0.08) / 0.92
    # Cung cắt trường thanh thoát uốn cong dần mạnh lên mũi nhọn tại z = +2.15!
    return -3.80 * ((1.0 - t) ** 1.16) - 1.18 * math.sin(math.pi * t) * (1.0 - t * 0.42) + 2.15 * (t ** 1.42)


def single_inner_spine_z(u: float) -> float:
    """Cạnh sống lưng trong (`+Z` side): khuyết tròn ôm lấy ngạnh bạc rồi uốn cong đồng điệu lên mũi (+2.15)!"""
    u = max(0.0, min(1.0, u))
    if u < 0.14:
        t = u / 0.14
        return -1.15 + 1.95 * (t ** 0.65)
    t = (u - 0.14) / 0.86
    return 0.80 + 0.16 * math.sin(math.pi * t) + 1.35 * (t ** 1.50)


def build_single_togetsu_geo(variant: str):
    """
    variant = "waxing" (Trăng Non - Trắng, dải lụa Đen) hoặc "waning" (Trăng Khuyết - Đen, dải lụa Trắng).
    Tâm nắm tay cố định tại `(0, 0, 0)`, trục lưỡi `+Y`, lưỡi cắt cong hướng `-Z`!
    """
    hilt_cubes = []
    # 1. Cán cầm cong nhẹ công thái học bọc dây da nâu đỏ quanh tâm `(0, 0, 0)` (giống Ảnh 5–6!)
    grip_pts = [(-2.35, +0.42), (-1.20, +0.10), (0.00, 0.00), (1.15, +0.08), (2.25, +0.32)]
    hilt_cubes.extend(make_tube_segments(grip_pts, [0.88, 0.94, 0.98, 0.96, 0.90], [1.04, 1.10, 1.14, 1.10, 1.02], "GRIP_BROWN"))
    # Đai quấn da nổi nhẹ
    for gy, gz in ((-1.45, 0.18), (-0.55, 0.03), (0.45, 0.02), (1.40, 0.15)):
        hilt_cubes.append(make_centered_cube([0.0, gy, gz], [1.04, 0.20, 1.18], "POMMEL_BLK", rot=[12.0, 0.0, 0.0]))

    # 2. Khối chuôi đen có lỗ + Hạt ngọc Đỏ + Dải lụa đối lập (Trắng cho Đen, Đen cho Trắng!)
    hilt_cubes.append(make_centered_cube([0.0, -2.75, +0.58], [0.96, 0.95, 1.24], "POMMEL_BLK", rot=[-14.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, -3.48, +0.76], [0.76, 0.72, 0.76], "BEAD_RED"))
    # Dải lụa hình chiếc lá/lông vũ uốn lượn bay về phía sau chuôi (`y = -3.85 .. -6.85`)
    ribbon_pts = [(-3.82, +0.84), (-4.60, +0.98), (-5.45, +0.86), (-6.25, +0.62), (-6.95, +0.74)]
    hilt_cubes.extend(make_tube_segments(ribbon_pts, [0.20, 0.22, 0.24, 0.18, 0.10], [0.32, 0.82, 0.92, 0.62, 0.24], "RIBBON_TAIL"))

    # 3. Ngạnh chắn tay màu Bạc Sáng vươn cong phía dưới sống lưng (`+Z` ôm lấy cổ lưỡi!)
    hilt_cubes.append(make_centered_cube([0.0, 2.55, +0.46], [1.06, 0.76, 1.12], "GUARD_SILV", rot=[-12.0, 0.0, 0.0]))
    guard_pts = [(2.65, +0.78), (3.55, +1.30), (4.85, +1.46), (6.15, +1.22)]
    hilt_cubes.extend(make_tube_segments(guard_pts, [0.78, 0.58, 0.40, 0.18], [0.72, 0.62, 0.46, 0.22], "GUARD_SILV"))

    # 4. Lõi 3D thân lưỡi đao dày có vát cạnh trường sắc (14 lát)
    blade_cubes = []
    n_slices = 14
    span = SINGLE_Y_MAX - SINGLE_Y_MIN
    for i in range(n_slices):
        ua = 0.14 + (i / n_slices) * 0.80
        ub = 0.14 + ((i + 1) / n_slices) * 0.80
        um = 0.5 * (ua + ub)
        ya, yb = SINGLE_Y_MIN + ua * span, SINGLE_Y_MIN + ub * span
        ym = 0.5 * (ya + yb)
        za_sp, zb_sp = single_inner_spine_z(ua), single_inner_spine_z(ub)
        za_ed, zb_ed = single_outer_edge_z(ua), single_outer_edge_z(ub)
        zm_sp = 0.5 * (za_sp + zb_sp) - 0.10
        zm_ed = 0.5 * (za_ed + zb_ed) + 0.55  # chừa viền mỏng sắc cho profile fin 64x104
        depth = zm_sp - zm_ed
        if depth < 0.25:
            continue
        zm_ctr = 0.5 * (zm_sp + zm_ed)
        rx_deg = -math.degrees(math.atan2(zb_sp - za_sp, yb - ya))
        seg_len = math.hypot(yb - ya, zb_sp - za_sp) * 1.08
        tx = 0.62 * (1.0 - (um ** 1.15) * 0.68)
        eps = 0.012 if (i % 2 == 1) else 0.0
        # Khối sống lưng gọn sát mép trong để lộ trọn bản vẽ 64x104 & đường rãnh ghép chữ S mượt tuyệt đối!
        core_d = min(0.65, depth * 0.24)
        blade_cubes.append(make_centered_cube([0.0, ym, zm_sp - core_d * 0.52], [tx + eps, seg_len, core_d],
                                              "BLADE_MAIN", rot=[rx_deg, 0.0, 0.0]))

    # 5. Lá cắt profile độ phân giải cao 64x104 (tạo biên trăng khuyết siêu mượt, gót liềm nhọn & đường rãnh ghép chữ S!)
    edge_cubes = [
        make_profile_fin_cube(SINGLE_Y_MIN, SINGLE_Y_MAX, SINGLE_Z_MIN, SINGLE_Z_MAX, thickness_x=0.34),
    ]

    bones = [
        {"name": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0]},
        {"name": "blade_root", "parent": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0], "cubes": hilt_cubes},
        {"name": "blade_body", "parent": "blade_root", "pivot": [0, 3.0, 0], "rotation": [0, 0, 0], "cubes": blade_cubes},
        {"name": "blade_edge", "parent": "blade_body", "pivot": [0, 9.0, -1.0], "rotation": [0, 0, 0], "cubes": edge_cubes},
    ]
    ident = f"geometry.togetsu_{variant}"
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": ident,
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 2.4,
                "visible_bounds_height": 2.8,
                "visible_bounds_offset": [0.0, 0.4, 0.0],
            },
            "bones": bones,
        }],
    }
    out = ASSETS / "geo" / "item" / f"togetsu_{variant}.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    n_cubes = sum(len(b.get("cubes", [])) for b in bones)
    print(f"GEO_{variant.upper()}_OK: {out} ({n_cubes} cubes)")
    return geo


# ==============================================================================
# 2. HÌNH KHỐI ĐẠI ĐAO HỢP NHẤT: SONG NGUYỆT (`togetsu_fused.geo.json` — SŌGENGETSU!)
# Khớp chuẩn Ảnh 7, 8, 9:
# - Tay cầm khung chữ D khép kín ghép từ 2 cán nâu đỏ (`y = -3.8 .. +3.2`) + 2 hạt ngọc đỏ + 2 dải lụa Trắng & Đen chữ V!
# - Lưỡi Đại Đao Trăng Khuyết Khổng Lồ (`y = -1.2 .. +25.2`, sải cong đồ sộ uy lực chuẩn Đại Kiếm!):
#   + Đuôi sừng Đen vươn ngược ra sau tay cầm (`y = -1.2 .. +6.5, z = +2.6 .. +6.8`)
#   + Bụng Trắng Ngọc Trai ở giữa kèm ngạnh bạc sắc nhọn (`y = 4.5 .. 14.5`)
#   + Đoạn Đen Obsidian khóa mộng ở giữa (`y = 12.0 .. 20.5`)
#   + Mũi Trăng Khuyết khổng lồ Trắng Sáng + viền Đen uốn cong gắt xuống phía trước (`y = 18.0 .. 25.4, z = -7.8`)!
# ==============================================================================

FUSED_Y_MIN, FUSED_Y_MAX = -1.80, 25.40
FUSED_Z_MIN, FUSED_Z_MAX = -11.80, +7.20


def fused_outer_arc_z(u: float) -> float:
    """
    Đường cung trăng khuyết NGOÀI khổng lồ (cong lưỡi liềm sâu ~110° y hệt Ảnh 8 & 9!):
    - Từ mũi đuôi sừng Đen sau tay cầm (+6.4) uốn vòm cao (+4.8) rồi quặp cong cực mạnh ra trước (-10.8)!
    """
    u = max(0.0, min(1.0, u))
    if u < 0.22:
        t = u / 0.22
        return 6.40 - 1.10 * t + 0.55 * math.sin(math.pi * t)
    t = (u - 0.22) / 0.78
    return 5.30 * ((1.0 - t) ** 1.28) + 2.40 * math.sin(math.pi * t) * (1.0 - t * 0.48) - 10.80 * (t ** 1.72)


def fused_inner_arc_z(u: float) -> float:
    """Đường cung trong của Đại Đao Song Nguyệt: khuyết sâu ôm tay cầm chữ D và mở bụng trăng khuyết đồ sộ!"""
    u = max(0.0, min(1.0, u))
    if u < 0.14:
        t = u / 0.14
        return 6.40 - 2.90 * (t ** 0.76)
    if u < 0.28:
        t = (u - 0.14) / 0.14
        return 3.50 - 3.30 * t
    t = (u - 0.28) / 0.72
    outer = fused_outer_arc_z(u)
    width = 7.40 * ((1.0 - (t ** 1.85)) ** 0.58) * (0.85 + 0.24 * math.sin(math.pi * t))
    return outer - max(0.0, width)


def build_fused_sogengetsu_geo():
    """
    Đại Đao Hợp Nhất **Song Nguyệt (Sōgengetsu)**:
    - Cán chính (nơi tay phải nắm) đặt đúng tâm `(0, 0, 0)` để tay nhân vật nắm khít 100%!
    - Cán phụ uốn cong nối liền thành khung chữ D khép kín (`z = +0.0 -> +3.4`) kết nối trực tiếp vào gốc sừng Đen!
    - Đuôi chuôi có 2 hạt ngọc đỏ + 2 dải lụa **Trắng & Đen chẽ hình chữ V** bay phía sau giống Ảnh 8 & 9!
    """
    hilt_cubes = []
    # 1. Cán chính (bọc dây nâu đỏ, chạy thẳng qua tâm nắm tay `(0, 0, 0)` tới khớp cổ lưỡi Trắng `(y=+4.2)`)
    main_grip = [(-3.40, +0.25), (-1.80, +0.05), (0.00, 0.00), (2.10, +0.10), (4.20, +0.35)]
    hilt_cubes.extend(make_tube_segments(main_grip, [0.96, 1.02, 1.06, 1.02, 0.98], [1.12, 1.18, 1.22, 1.18, 1.14], "GRIP_BROWN"))
    for gy in (-2.2, -0.9, 0.4, 1.7, 3.0):
        hilt_cubes.append(make_centered_cube([0.0, gy, 0.08], [1.12, 0.22, 1.26], "POMMEL_BLK", rot=[10.0, 0.0, 0.0]))

    # 2. Cán phụ cong khép vòng khung chữ D (`y = -3.4 .. +3.6, z = +0.5 .. +3.4`) nối đỉnh chuôi vào gốc lưỡi Đen!
    loop_grip = [(-3.40, +0.55), (-2.60, +1.85), (-1.20, +2.85), (0.60, +3.30), (2.30, +3.25), (3.80, +2.85)]
    hilt_cubes.extend(make_tube_segments(loop_grip, [0.88, 0.92, 0.94, 0.94, 0.92, 0.96], [1.02, 1.06, 1.08, 1.08, 1.06, 1.16], "GRIP_BROWN"))

    # 3. Khối chốt chuôi đen có lỗ + 2 Hạt Ngọc Đỏ + 2 Dải Lụa Trắng & Đen chẽ chữ V (giống hệt Ảnh 8 & 9!)
    hilt_cubes.append(make_centered_cube([0.0, -3.75, +0.35], [1.14, 1.25, 1.56], "POMMEL_BLK", rot=[-10.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, -4.60, +0.52], [0.82, 0.78, 0.82], "BEAD_RED"))
    hilt_cubes.append(make_centered_cube([0.0, -5.25, +0.62], [0.76, 0.72, 0.76], "BEAD_RED"))
    # Dải lụa TRẮNG bay chéo xuống-trước
    rib_w = [(-5.60, +0.55), (-6.70, -0.05), (-7.95, -0.68), (-9.05, -0.95)]
    hilt_cubes.extend(make_tube_segments(rib_w, [0.22, 0.26, 0.24, 0.12], [0.38, 1.05, 1.00, 0.34], "RIBBON_TAIL"))
    # Dải lụa ĐEN bay chéo lên-sau (tạo hình cánh chim chữ V y hệt Ảnh 8–9!)
    rib_b = [(-5.60, +0.85), (-6.65, +1.68), (-7.85, +2.55), (-8.90, +3.10)]
    hilt_cubes.extend(make_tube_segments(rib_b, [0.22, 0.26, 0.24, 0.12], [0.38, 1.05, 1.00, 0.34], "RIBBON_ALT"))

    # 4. Ngạnh chắn tay màu Bạc Sáng vươn cong sắc dưới cổ lưỡi Trắng (`y = 4.2 .. 8.8`)
    hilt_cubes.append(make_centered_cube([0.0, 4.55, +0.30], [1.22, 0.96, 1.38], "GUARD_SILV", rot=[-14.0, 0.0, 0.0]))
    spur_pts = [(4.75, -0.25), (5.85, -1.05), (7.35, -1.60), (8.85, -1.75)]
    hilt_cubes.extend(make_tube_segments(spur_pts, [0.88, 0.66, 0.44, 0.20], [0.82, 0.68, 0.48, 0.24], "GUARD_SILV"))

    # 5. Lõi sống 3D dày cho Đại Đao Song Nguyệt (uốn dọc theo cung lưng ngoài để có khối dày 3D đồ sộ khi xoay!)
    blade_cubes = []
    n_slices = 15
    span = FUSED_Y_MAX - FUSED_Y_MIN
    for i in range(n_slices):
        ua = 0.14 + (i / n_slices) * 0.82
        ub = 0.14 + ((i + 1) / n_slices) * 0.82
        um = 0.5 * (ua + ub)
        ya, yb = FUSED_Y_MIN + ua * span, FUSED_Y_MIN + ub * span
        ym = 0.5 * (ya + yb)
        za_o, zb_o = fused_outer_arc_z(ua), fused_outer_arc_z(ub)
        za_i, zb_i = fused_inner_arc_z(ua), fused_inner_arc_z(ub)
        zm_o = 0.5 * (za_o + zb_o) - 0.18
        zm_i = 0.5 * (za_i + zb_i) + 0.35
        w_d = zm_o - zm_i
        if w_d < 0.45:
            continue
        rx_deg = -math.degrees(math.atan2(zb_o - za_o, yb - ya))
        seg_len = math.hypot(yb - ya, zb_o - za_o) * 1.10
        tx = 0.96 * (1.0 - (um ** 1.35) * 0.65)
        eps = 0.015 if (i % 2 == 1) else 0.0
        # Khối lõi dọc sát lưng ngoài cực gọn để lộ trọn 100% mảng ghép Trắng/Đen sắc nét trên lá cắt 64x104!
        mat_s = "BLADE_MAIN" if (0.32 <= um <= 0.54 or um >= 0.72) else "BLADE_ALT"
        spine_d = min(0.85, w_d * 0.18)
        blade_cubes.append(make_centered_cube([0.0, ym, zm_o - spine_d * 0.56], [tx + eps, seg_len, spine_d],
                                              mat_s, rot=[rx_deg, 0.0, 0.0]))

    # 6. Lá cắt đại đao độ phân giải cao 64x104 thể hiện trọn vẹn mảng ghép Âm–Dương Trắng/Đen của Song Nguyệt!
    edge_cubes = [
        make_profile_fin_cube(FUSED_Y_MIN, FUSED_Y_MAX, FUSED_Z_MIN, FUSED_Z_MAX, thickness_x=0.46),
    ]

    bones = [
        {"name": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0]},
        {"name": "blade_root", "parent": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0], "cubes": hilt_cubes},
        {"name": "blade_body", "parent": "blade_root", "pivot": [0, 4.5, 0], "rotation": [0, 0, 0], "cubes": blade_cubes},
        {"name": "blade_edge", "parent": "blade_body", "pivot": [0, 14.0, -1.5], "rotation": [0, 0, 0], "cubes": edge_cubes},
    ]
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.togetsu_fused",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 3.4,
                "visible_bounds_height": 3.8,
                "visible_bounds_offset": [0.0, 0.8, 0.0],
            },
            "bones": bones,
        }],
    }
    out = ASSETS / "geo" / "item" / "togetsu_fused.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    n_cubes = sum(len(b.get("cubes", [])) for b in bones)
    print(f"GEO_FUSED_OK: {out} ({n_cubes} cubes, Colossal Sogengetsu Crescent Greatsword)")
    return geo


def build_togetsu_animations():
    anims = {
        "animation.togetsu.idle": {
            "loop": True,
            "animation_length": 2.4,
            "bones": {
                "blade_edge": {
                    "scale": {"0.0": [1.0, 1.0, 1.0], "1.2": [1.01, 1.003, 1.005], "2.4": [1.0, 1.0, 1.0]}
                }
            },
        }
    }
    out = ASSETS / "animations" / "item" / "togetsu.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out}")


# ==============================================================================
# 3. SINH TEXTURE 128x128 RGBA + SELECTIVE GLOWMASK CHO CẢ 3 DẠNG
# ==============================================================================

def _put_tile(diff, glow, key, rgb, g_rgba=None):
    x0, y0, w, h = MAT[key]
    diff[y0:y0 + h, x0:x0 + w, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    diff[y0:y0 + h, x0:x0 + w, 3] = 255
    if g_rgba is not None:
        glow[y0:y0 + h, x0:x0 + w] = np.clip(g_rgba, 0, 255).astype(np.uint8)


def _fill_shared_togetsu_tiles(diff, glow, mode: str, rng):
    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32)
    noise = rng.random((16, 16)).astype(np.float32)
    grain = 0.5 + 0.5 * np.sin((xx * 0.65 + yy * 0.45) + 1.0 * np.sin(yy * 0.5 - xx * 0.3))
    t = np.clip(grain * 0.55 + noise * 0.25 + (15.0 - xx) / 15.0 * 0.20, 0.0, 1.0)[:, :, None]

    W_SHADOW = np.array([154, 162, 172], dtype=np.float32)
    W_MAIN   = np.array([218, 224, 232], dtype=np.float32)
    W_PEARL  = np.array([248, 251, 255], dtype=np.float32)

    K_DARK   = np.array([ 24,  26,  32], dtype=np.float32)
    K_MID    = np.array([ 46,  49,  58], dtype=np.float32)
    K_STEEL  = np.array([ 78,  84,  96], dtype=np.float32)

    SIL_D    = np.array([138, 146, 156], dtype=np.float32)
    SIL_L    = np.array([212, 220, 228], dtype=np.float32)

    # Cán da nâu đỏ đặc trưng của Sunraku Togetsu (#5C251F -> #8F3E34)
    BR_D     = np.array([ 78,  32,  26], dtype=np.float32)
    BR_M     = np.array([128,  54,  44], dtype=np.float32)
    BR_HI    = np.array([156,  72,  58], dtype=np.float32)

    RED_D    = np.array([172,  22,  32], dtype=np.float32)
    RED_HI   = np.array([248,  68,  74], dtype=np.float32)

    if mode in ("waxing", "fused"):
        _put_tile(diff, glow, "BLADE_MAIN", W_SHADOW * (1.0 - t) + W_PEARL * t)
        _put_tile(diff, glow, "BLADE_EDGE", W_MAIN * (1.0 - t) + W_PEARL * t)
        _put_tile(diff, glow, "BLADE_ALT", K_DARK * (1.0 - t) + K_MID * t)
    else:
        _put_tile(diff, glow, "BLADE_MAIN", K_DARK * (1.0 - t) + K_MID * t)
        _put_tile(diff, glow, "BLADE_EDGE", K_MID * (1.0 - t) + K_STEEL * t)
        _put_tile(diff, glow, "BLADE_ALT", W_SHADOW * (1.0 - t) + W_MAIN * t)

    _put_tile(diff, glow, "GUARD_SILV", SIL_D * (1.0 - t) + SIL_L * t)

    # Dây quấn da nâu đỏ chéo sắc nét
    wrap = ((xx + yy * 1.35) % 5.0) < 1.5
    wrap_hi = (((xx + yy * 1.35) % 5.0) >= 1.5) & (((xx + yy * 1.35) % 5.0) < 2.5)
    c_grip = np.where(wrap[:, :, None], BR_D, BR_M)
    c_grip = np.where(wrap_hi[:, :, None], BR_HI, c_grip) * (0.90 + 0.16 * noise[:, :, None])
    _put_tile(diff, glow, "GRIP_BROWN", c_grip)

    _put_tile(diff, glow, "POMMEL_BLK", K_DARK * (0.88 + 0.24 * t))

    # Hạt ngọc đỏ bóng nhẹ (glow đỏ rất dịu)
    tr = np.clip((15.0 - yy) / 15.0 * 0.65 + (15.0 - xx) / 15.0 * 0.35, 0.0, 1.0)[:, :, None]
    c_red = RED_D * (1.0 - tr) + RED_HI * tr
    g_red = np.dstack([c_red * 0.35, np.full((16, 16), 75.0, dtype=np.float32)])
    _put_tile(diff, glow, "BEAD_RED", c_red, g_red)

    # Dải lụa Âm-Dương:
    # - waxing (lưỡi trắng) -> lụa ĐEN; waning (lưỡi đen) -> lụa TRẮNG phát sáng dịu; fused -> RIBBON_TAIL=TRẮNG, RIBBON_ALT=ĐEN!
    if mode == "waxing":
        _put_tile(diff, glow, "RIBBON_TAIL", K_DARK * (1.0 - t) + K_MID * t)
        _put_tile(diff, glow, "RIBBON_ALT", W_MAIN * (1.0 - t) + W_PEARL * t)
    else:
        c_rw = W_MAIN * (1.0 - t) + W_PEARL * t
        g_rw = np.dstack([np.broadcast_to(W_PEARL * 0.55, (16, 16, 3)), np.full((16, 16), 110.0, dtype=np.float32)])
        _put_tile(diff, glow, "RIBBON_TAIL", c_rw, g_rw)
        _put_tile(diff, glow, "RIBBON_ALT", K_DARK * (1.0 - t) + K_MID * t)

    tx0, ty0, tw, th = MAT["TRANSPARENT"]
    diff[ty0:ty0 + th, tx0:tx0 + tw] = 0
    glow[ty0:ty0 + th, tx0:tx0 + tw] = 0


def build_single_togetsu_texture(variant: str):
    """Texture 128x128 + selective glowmask cho Trăng Non (`waxing` - Trắng) hoặc Trăng Khuyết (`waning` - Đen)."""
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    rng = np.random.default_rng(30101 if variant == "waxing" else 30102)
    _fill_shared_togetsu_tiles(diff, glow, variant, rng)

    is_white = (variant == "waxing")
    u0, v0, uw, vh = PROFILE_UV
    noise_p = rng.random((vh, uw)).astype(np.float32)

    W_EDGE   = np.array([252, 254, 255], dtype=np.float32)
    W_BODY   = np.array([218, 224, 232], dtype=np.float32)
    W_SHAD   = np.array([148, 156, 168], dtype=np.float32)
    K_EDGE   = np.array([108, 118, 134], dtype=np.float32)
    K_BODY   = np.array([ 34,  36,  44], dtype=np.float32)
    K_DEEP   = np.array([ 18,  19,  24], dtype=np.float32)
    OUTLINE  = np.array([ 14,  15,  19], dtype=np.float32)
    GLOW_W   = np.array([228, 245, 255], dtype=np.float32)
    GLOW_K   = np.array([150, 175, 220], dtype=np.float32)

    for py in range(vh):
        u = 1.0 - (py + 0.5) / vh
        z_sp = single_inner_spine_z(u)
        z_ed = single_outer_edge_z(u)
        w_b = z_sp - z_ed
        if w_b <= 0.03:
            continue

        # Đường rãnh ghép chữ S đặc trưng giữa thân lưỡi (ảnh 5–6!)
        # Chạy chéo từ (u=0.38, cạnh cắt) -> bậc chữ S ở giữa (u=0.52) -> (u=0.64, sống lưng)
        for px in range(uw):
            z = SINGLE_Z_MIN + ((px + 0.5) / uw) * (SINGLE_Z_MAX - SINGLE_Z_MIN)
            if z < z_ed or z > z_sp:
                continue

            d_ed = z - z_ed
            d_sp = z_sp - z
            frac = d_ed / max(1e-5, w_b)
            n_v = noise_p[py, px]

            # Tính khoảng cách tới rãnh ghép chữ S đặc trưng của Togetsu
            seam_u = 0.44 + 0.14 * math.tanh((frac - 0.48) * 5.2) + 0.06 * (frac - 0.5)
            on_seam = abs(u - seam_u) < 0.017

            # Đường vát sống sắc (bevel line) chạy song song cách cạnh cắt ~22%
            bevel_t = min(1.0, max(0.0, d_ed / max(0.45, w_b * 0.34)))

            g_col = np.zeros(3, dtype=np.float32)
            g_alpha = 0.0

            if is_white:
                # Trăng Non (Trắng Ngọc Trai bóng mượt, mép vát trắng tinh phát sáng dịu!)
                if bevel_t < 1.0:
                    col = W_EDGE * (1.0 - bevel_t * 0.45) + W_BODY * (bevel_t * 0.45)
                    if d_ed < 0.36:
                        et = 1.0 - d_ed / 0.36
                        g_col = GLOW_W
                        g_alpha = 185.0 * et
                else:
                    col = W_BODY * (0.78 + 0.16 * (1.0 - frac)) + W_SHAD * (0.22 * frac + 0.08 * n_v)
                if d_ed < 0.12 or d_sp < 0.12:
                    col = col * 0.58 + OUTLINE * 0.42
                if on_seam:
                    col = OUTLINE * 0.78 + W_SHAD * 0.22
                    g_alpha = 0.0
            else:
                # Trăng Khuyết (Đen Tuyền Obsidian lì sâu, mép cắt ánh thép lạnh bạc xám sắc lẹm!)
                if bevel_t < 1.0:
                    col = K_EDGE * (1.0 - bevel_t * 0.72) + K_BODY * (bevel_t * 0.72)
                    if d_ed < 0.30:
                        et = 1.0 - d_ed / 0.30
                        g_col = GLOW_K
                        g_alpha = 135.0 * et
                else:
                    col = K_BODY * (0.68 + 0.18 * (1.0 - frac)) + K_DEEP * (0.32 * frac + 0.08 * n_v)
                if d_sp < 0.14:
                    col = K_DEEP
                if on_seam:
                    col = OUTLINE
                    g_alpha = 0.0

            diff[v0 + py, u0 + px, :3] = np.clip(col, 0, 255).astype(np.uint8)
            diff[v0 + py, u0 + px, 3] = 255
            if g_alpha > 4.0:
                glow[v0 + py, u0 + px, :3] = np.clip(g_col, 0, 255).astype(np.uint8)
                glow[v0 + py, u0 + px, 3] = int(min(255, g_alpha))

    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(tex_dir / f"togetsu_{variant}_model.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / f"togetsu_{variant}_model_glowmask.png")
    print(f"TEX_{variant.upper()}_OK: togetsu_{variant}_model.png + glowmask (128x128)")
    return diff.astype(np.float32), glow.astype(np.float32)


def build_fused_sogengetsu_texture():
    """Texture 128x128 + selective glowmask cho Đại Đao Hợp Nhất Song Nguyệt (mảng ghép Âm–Dương Trắng/Đen khớp Ảnh 8–9!)."""
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    rng = np.random.default_rng(30103)
    _fill_shared_togetsu_tiles(diff, glow, "fused", rng)

    u0, v0, uw, vh = PROFILE_UV
    noise_p = rng.random((vh, uw)).astype(np.float32)

    W_EDGE  = np.array([252, 254, 255], dtype=np.float32)
    W_BODY  = np.array([218, 226, 234], dtype=np.float32)
    W_SHAD  = np.array([150, 160, 174], dtype=np.float32)
    K_EDGE  = np.array([118, 128, 146], dtype=np.float32)
    K_BODY  = np.array([ 36,  38,  46], dtype=np.float32)
    K_DEEP  = np.array([ 18,  20,  26], dtype=np.float32)
    SEAM    = np.array([ 14,  15,  20], dtype=np.float32)
    GLOW_LU = np.array([224, 244, 255], dtype=np.float32)

    for py in range(vh):
        u = 1.0 - (py + 0.5) / vh
        z_o = fused_outer_arc_z(u)
        z_i = fused_inner_arc_z(u)
        w_b = z_o - z_i
        if w_b <= 0.04:
            continue

        for px in range(uw):
            z = FUSED_Z_MIN + ((px + 0.5) / uw) * (FUSED_Z_MAX - FUSED_Z_MIN)
            if z < z_i or z > z_o:
                continue

            d_out = z_o - z     # khoảng cách từ lưng cung trăng khuyết ngoài
            d_in = z - z_i      # khoảng cách từ cung trong
            frac = d_in / max(1e-5, w_b)  # 0 = cung trong, 1 = cung ngoài
            n_v = noise_p[py, px]

            # Mảng ghép Âm–Dương Song Nguyệt (khớp hệt Ảnh 8 & Ảnh 9!):
            # - Vùng 1 (đuôi sừng sau tay cầm): ĐEN (u < bound1)
            # - Vùng 2 (yên trắng bản rộng ôm giữa thân lưỡi): TRẮNG NGỌC TRAI (bound1 <= u <= bound2)
            # - Vùng 3 (khúc trăng khuyết giữa thân): ĐEN OBSIDIAN (bound2 < u < bound3)
            # - Vùng 4 (mũi trăng khuyết khổng lồ): TRẮNG SÁNG bọc ngoài + sống viền Đen sắc (u >= bound3)
            bound1 = 0.30 + 0.06 * math.sin(frac * math.pi)
            bound2 = 0.55 - 0.07 * math.tanh((frac - 0.5) * 4.5)
            bound3 = 0.72 + 0.06 * math.tanh((frac - 0.45) * 4.2)

            on_seam = (abs(u - bound1) < 0.014) or (abs(u - bound2) < 0.014) or (abs(u - bound3) < 0.014)
            is_white_zone = (bound1 <= u <= bound2) or (u >= bound3 and frac >= 0.24)

            g_col = np.zeros(3, dtype=np.float32)
            g_alpha = 0.0

            if is_white_zone:
                col = W_BODY * (0.72 + 0.24 * frac) + W_SHAD * (0.28 * (1.0 - frac) + 0.06 * n_v)
                if d_out < 0.65:
                    et = 1.0 - d_out / 0.65
                    col = col * (1.0 - et * 0.65) + W_EDGE * (et * 0.65)
                    g_col = GLOW_LU
                    g_alpha = 215.0 * et
                elif u >= bound3:
                    # Mũi Song Nguyệt ánh sáng trăng thanh khiết
                    g_col = GLOW_LU
                    g_alpha = 95.0
            else:
                col = K_BODY * (0.68 + 0.24 * frac) + K_DEEP * (0.32 * (1.0 - frac) + 0.06 * n_v)
                if d_out < 0.45:
                    et = 1.0 - d_out / 0.45
                    col = col * (1.0 - et * 0.55) + K_EDGE * (et * 0.55)
                    g_col = GLOW_LU * 0.75
                    g_alpha = 135.0 * et

            if on_seam:
                col = SEAM
                g_alpha = 0.0

            diff[v0 + py, u0 + px, :3] = np.clip(col, 0, 255).astype(np.uint8)
            diff[v0 + py, u0 + px, 3] = 255
            if g_alpha > 4.0:
                glow[v0 + py, u0 + px, :3] = np.clip(g_col, 0, 255).astype(np.uint8)
                glow[v0 + py, u0 + px, 3] = int(min(255, g_alpha))

    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(tex_dir / "togetsu_fused_model.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "togetsu_fused_model_glowmask.png")
    print("TEX_FUSED_OK: togetsu_fused_model.png + glowmask (128x128 Sogengetsu Yin-Yang Crescent)")
    return diff.astype(np.float32), glow.astype(np.float32)


# ==============================================================================
# 4. HAI ICON KHO ĐỒ 32x32 (DẠNG SONG ĐAO + DẠNG ĐẠI ĐAO SONG NGUYỆT)
# ==============================================================================

def _add_outline_32(layer: Image.Image) -> Image.Image:
    out_c = (10, 10, 14, 255)
    lpx = layer.load()
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    opx = out.load()
    for y in range(32):
        for x in range(32):
            if lpx[x, y][3] > 0:
                opx[x, y] = lpx[x, y]
            else:
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 32 and 0 <= ny < 32 and lpx[nx, ny][3] > 0:
                        opx[x, y] = out_c
                        break
    return out


def build_togetsu_icons_32x32():
    W_HI  = (248, 251, 255, 255)
    W_MID = (206, 214, 224, 255)
    W_SH  = (146, 156, 170, 255)
    K_HI  = (76, 82, 94, 255)
    K_MID = (36, 38, 46, 255)
    K_DK  = (20, 21, 26, 255)
    BR_L  = (144, 64, 52, 255)
    BR_D  = (88, 36, 30, 255)
    RED   = (232, 42, 48, 255)

    # --- Icon 1: Song Đao Togetsu bắt chéo (1 Trắng Trăng Non + 1 Đen Trăng Khuyết, cán nâu đỏ + ngọc đỏ + lụa đối lập) ---
    l_blk = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    bpx = l_blk.load()
    def pb(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32: bpx[x, y] = c
    # Dải lụa trắng + ngọc đỏ + cán nâu đỏ từ trên-trái -> lưỡi đen cong xuống dưới-phải
    for x, y, c in [(2, 3, W_HI), (3, 4, W_MID), (4, 5, RED), (5, 5, K_DK), (6, 6, BR_L), (7, 7, BR_D), (8, 8, BR_L), (9, 9, BR_D), (10, 9, W_SH)]:
        pb(x, y, c)
    for s in range(16):
        x, y = 11 + s, 11 + s
        pb(x, y, K_HI if s % 3 == 0 else K_MID)
        if s < 14: pb(x - 1, y + 1, K_MID)
        if s < 11: pb(x - 2, y + 2, K_DK if s != 5 else K_HI)
        if s in (2, 3): pb(x - 3, y + 3, K_MID)
    pb(27, 26, K_HI)

    l_wht = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    wpx = l_wht.load()
    def pw(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32: wpx[x, y] = c
    # Dải lụa đen + ngọc đỏ + cán nâu đỏ từ dưới-trái -> lưỡi trắng cong lên trên-phải
    for x, y, c in [(2, 28, K_MID), (3, 27, K_HI), (4, 26, RED), (5, 25, K_DK), (6, 24, BR_L), (7, 23, BR_D), (8, 22, BR_L), (9, 21, BR_D), (10, 21, W_SH)]:
        pw(x, y, c)
    # Lưỡi Trăng Non (Trắng) mịn liền khối (không sọc caro!)
    for s in range(16):
        x, y = 11 + s, 19 - s
        pw(x, y, W_HI)
        if s < 14: pw(x + 1, y + 1, K_DK if s == 7 else W_MID)
        if s < 11: pw(x + 2, y + 2, W_SH if s == 6 else W_HI)
        if s in (2, 3, 4): pw(x + 3, y + 3, W_MID)
    pw(27, 4, W_HI)

    icon_dual = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    icon_dual.alpha_composite(_add_outline_32(l_blk))
    icon_dual.alpha_composite(_add_outline_32(l_wht))

    # --- Icon 2: Đại Đao Hợp Nhất Song Nguyệt (liền khối hoàn toàn giữa tay cầm chữ D nâu đỏ & vòng trăng khuyết!) ---
    l_f = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    fpx = l_f.load()
    def pf(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32: fpx[x, y] = c
    for x, y, c in [
        (2, 28, W_HI), (3, 27, W_MID), (4, 29, K_MID), (5, 28, K_HI),
        (5, 26, RED), (6, 25, RED), (7, 24, K_DK),
        (8, 23, BR_L), (9, 22, BR_D), (10, 21, BR_L), (11, 20, BR_D), (12, 19, BR_L), (13, 18, BR_D),
        (7, 22, BR_D), (7, 20, BR_L), (8, 18, BR_D), (9, 16, BR_L), (10, 15, BR_D),
        (14, 19, W_SH), (15, 19, W_HI), (16, 18, W_SH),
    ]:
        pf(x, y, c)
    # Vòng trăng khuyết khổng lồ Trắng–Đen ôm khít cổ tay cầm chữ D!
    for step in range(34):
        t = step / 33.0
        ang = math.radians(-162.0 + t * 205.0)
        cx_a, cy_a = 16.5, 16.5
        r_out = 12.6
        r_in = 5.4 + 3.2 * abs(t - 0.50)
        for rr in np.linspace(r_in, r_out, 14):
            px = int(round(cx_a + math.cos(ang) * rr))
            py = int(round(cy_a + math.sin(ang) * rr))
            if t < 0.24:
                col = K_MID if rr < r_out - 1.2 else K_HI
            elif t < 0.52:
                col = W_HI if rr > r_out - 2.4 else W_MID
            elif t < 0.72:
                col = K_DK if rr < r_out - 1.8 else K_HI
            else:
                col = W_HI if rr > r_in + 1.5 else W_MID
            pf(px, py, col)

    icon_fused = _add_outline_32(l_f)

    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    icon_dual.save(tex_dir / "togetsu.png")
    icon_fused.save(tex_dir / "togetsu_fused.png")
    print("ICONS_OK: togetsu.png (Dual) + togetsu_fused.png (Sogengetsu)")
    return icon_dual, icon_fused


def build_togetsu_item_model():
    model = {
        "parent": "builtin/entity",
        "gui_light": "front",
        "textures": {"particle": "abyssalrpg:item/togetsu"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0.0, 0.5, 0.0], "scale": [0.95, 0.95, 0.95]},
            "thirdperson_lefthand":  {"rotation": [0, 0, 0], "translation": [0.0, 0.5, 0.0], "scale": [0.95, 0.95, 0.95]},
            "firstperson_righthand": {"rotation": [-30, 22, 12], "translation": [1.2, 0.8, 1.0], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand":  {"rotation": [-30, 22, 12], "translation": [1.2, 0.8, 1.0], "scale": [0.68, 0.68, 0.68]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3.2, 0], "scale": [0.68, 0.68, 0.68]},
            "fixed":  {"rotation": [0, 90, 0], "translation": [0, -2.4, 0], "scale": [0.76, 0.76, 0.76]},
            "gui":    {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        },
    }
    out = ASSETS / "models" / "item" / "togetsu.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(model, indent=2), encoding="utf-8")
    print(f"MODEL_JSON_OK: {out}")


# ==============================================================================
# 5. GIẢI MA TRẬN POSE SUNRAKU (DUAL) & ĐẠI KIẾM SONG NGUYỆT (FUSED) + PREVIEW 6 GÓC
# ==============================================================================

def decompose_rx_rz_ry(R):
    sr = np.clip(-R[0, 1], -1.0, 1.0)
    r = math.asin(sr)
    cr = math.cos(r)
    if abs(cr) > 1e-6:
        p = math.atan2(R[2, 1], R[1, 1])
        y = math.atan2(R[0, 2], R[0, 0])
    else:
        p = math.atan2(-R[1, 2], R[2, 2])
        y = 0.0
    return math.degrees(p), math.degrees(r), math.degrees(y)


def solve_blade_angles(arm_rot, R_rev, target_tip_yup, target_edge_yup):
    F_y = np.diag([1.0, -1.0, 1.0])
    M_bedrock = np.diag([-1.0, 1.0, 1.0])
    ax, ay, az = arm_rot
    R_arm = rot_z(az) @ rot_y(ay) @ rot_x(ax)
    R_hand = rot_x(math.radians(-90.0)) @ rot_y(math.radians(180.0))
    u_tip = np.array(target_tip_yup, dtype=np.float64); u_tip /= np.linalg.norm(u_tip)
    e_raw = np.array(target_edge_yup, dtype=np.float64)
    e_proj = e_raw - np.dot(e_raw, u_tip) * u_tip
    u_edge = e_proj / np.linalg.norm(e_proj)
    u_z = -u_edge
    u_x = np.cross(u_tip, u_z); u_x /= np.linalg.norm(u_x)
    A = F_y @ R_arm @ R_hand
    B = R_rev @ M_bedrock
    R_align = A.T @ np.column_stack([u_x, u_tip, u_z]) @ B.T
    p, r, y = decompose_rx_rz_ry(R_align)
    return round(p, 2), round(r, 2), round(y, 2)


# --- POSE 1: SUNRAKU DUAL-WIELD STANCE (Ảnh 10!) ---
# Tay Trái (Trăng Non - Trắng) vắt CAO ngang cằm/mặt, mũi đao chĩa NGANG sang phải nhân vật (trái ảnh = -X), cạnh cong lên trên (+Y)!
# Tay Phải (Trăng Khuyết - Đen) vắt THẤP ngang bụng, mũi đao chĩa NGANG sang trái nhân vật (phải ảnh = +X), cạnh cong xuống dưới (-Y)!
SUNRAKU_HEAD = (0.14, -0.14, 0.0)
SUNRAKU_BODY = (0.12, +0.16, 0.0)
SUNRAKU_LARM = (-1.68, +0.62, -0.06)
SUNRAKU_RARM = (-1.04, -0.58, +0.16)
SUNRAKU_LLEG = (-0.24, +0.18, -0.14)
SUNRAKU_RLEG = (+0.28, -0.18, +0.14)

SUNRAKU_LEFT_ANGLES = solve_blade_angles(
    SUNRAKU_LARM, np.eye(3),
    target_tip_yup=[-0.98, +0.08, -0.18],   # Lưỡi Trắng vươn ngang TRƯỚC MẶT sang trái ảnh (-X) giống Ảnh 10!
    target_edge_yup=[+0.08, +0.96, -0.24],  # Lưng cong trắng hướng lên trên (+Y) đúng Ảnh 10!
)
SUNRAKU_RIGHT_ANGLES = solve_blade_angles(
    SUNRAKU_RARM, np.eye(3),
    target_tip_yup=[+0.98, -0.12, -0.18],   # Lưỡi Đen vươn ngang TRƯỚC BỤNG sang phải ảnh (+X) đúng Ảnh 10!
    target_edge_yup=[-0.10, -0.96, -0.24],  # Bụng cong đen hướng xuống dưới (-Y) đúng Ảnh 10!
)

# --- POSE 2: ĐẠI KIẾM SONG NGUYỆT — POSE NGẠO NGHỄ ÁP ĐẢO (MANGA CLIMAX BOOST / HUNGER WOLF!) ---
# - Hai chân đứng tấn rộng oai vệ, ngực mở đầy kiêu ngạo, đầu cúi nhẹ liếc thẳng sắc lạnh!
# - Tay Trái vung ngang vai hơi kéo ra sau nắm chặt tay tạo khí thế áp đảo bùng nổ!
# - Tay Phải duỗi chéo xuống trước hông cầm 1 tay chuôi Đại Đao Song Nguyệt, lưỡi trăng khuyết khổng lồ
#   uốn vòng quét chếch vút cao lên trời bên mạn trái (bên phải ảnh F5)!
FUSED_HEAD = (0.18, -0.30, 0.0)
FUSED_BODY = (0.20, +0.36, -0.06)
FUSED_RARM = (-0.84, -0.14, -0.58)
FUSED_LARM = (+0.14, -0.26, -1.40)
FUSED_LLEG = (-0.36, +0.26, -0.26)
FUSED_RLEG = (+0.40, -0.26, +0.26)

FUSED_BLADE_ANGLES = solve_blade_angles(
    FUSED_RARM, np.eye(3),
    target_tip_yup=[+0.80, +0.56, -0.26],   # Đại đao quét chếch rộng ra mạn phải ảnh (+X, 0 che mặt!) vút mũi lên trời!
    target_edge_yup=[-0.56, +0.80, -0.18],  # Cung trăng khuyết khổng lồ ôm vòng ngạo nghễ đúng góc ảnh manga!
)


def extract_faces(geo: dict, diff_img, glow_img):
    faces = []
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]; sx, sy, sz = c["size"]
            x0, y0, z0, x1, y1, z1 = ox, oy, oz, ox + sx, oy + sy, oz + sz
            bLB = np.array([x1, y0, z0], dtype=np.float64); bRB = np.array([x1, y0, z1], dtype=np.float64)
            tLB = np.array([x1, y1, z0], dtype=np.float64); tRB = np.array([x1, y1, z1], dtype=np.float64)
            tLF = np.array([x0, y1, z0], dtype=np.float64); tRF = np.array([x0, y1, z1], dtype=np.float64)
            bLF = np.array([x0, y0, z0], dtype=np.float64); bRF = np.array([x0, y0, z1], dtype=np.float64)
            rot = c.get("rotation")
            piv = np.array(c["pivot"], dtype=np.float64) if rot is not None else None
            R_bed = (rot_z(-math.radians(rot[2])) @ rot_y(+math.radians(rot[1])) @ rot_x(-math.radians(rot[0]))) if rot is not None else None
            uv_map = c["uv"]
            for fname, verts in (("west", [tRB, tLB, bLB, bRB]), ("east", [tLF, tRF, bRF, bLF]),
                                 ("north", [tLB, tLF, bLF, bLB]), ("south", [tRF, tRB, bRB, bRF]),
                                 ("up", [tRB, tRF, tLF, tLB]), ("down", [bLB, bLF, bRF, bRB])):
                if fname not in uv_map: continue
                fuv = uv_map[fname]
                u0, v0 = float(fuv["uv"][0]), float(fuv["uv"][1])
                uw, vh = float(fuv["uv_size"][0]), float(fuv["uv_size"][1])
                uv_c = [(u0 + uw, v0), (u0, v0), (u0, v0 + vh), (u0 + uw, v0 + vh)]
                q = np.array(verts, dtype=np.float64)
                if R_bed is not None: q = ((q - piv) @ R_bed.T) + piv
                faces.append((q, uv_c, None, diff_img, glow_img))
    return faces


def build_box(bmin, bmax, rgb):
    x0, y0, z0 = bmin; x1, y1, z1 = bmax
    cs = np.array([[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]], dtype=np.float64)
    return [(cs[idx], None, rgb, None, None) for idx in ([0,1,2,3],[5,4,7,6],[4,0,3,7],[1,5,6,2],[3,2,6,7],[4,5,1,0])]


def xform_part(faces, piv, rx, ry, rz):
    R = rot_z(rz) @ rot_y(ry) @ rot_x(rx)
    p = np.array(piv, dtype=np.float64)
    return [((q @ R.T) + p, uv, col, d, g) for q, uv, col, d, g in faces]


def build_char_scene(head_r, body_r, larm_r, rarm_r, lleg_r, rleg_r, attachments, root_pitch=-4.0):
    scene = []
    head = build_box((-4,-8,-4),(4,0,4),(155,172,188)) + build_box((-1.8,-3.5,-5.6),(1.8,-1.5,-3.9),(212,186,120))
    head += build_box((-2.8,-5.2,-4.15),(-1.1,-4.1,-3.95),(210,36,44)) + build_box((1.1,-5.2,-4.15),(2.8,-4.1,-3.95),(210,36,44))
    scene.extend(xform_part(head, (0,0,0), *head_r))
    scene.extend(xform_part(build_box((-4,0,-2),(4,12,2),(195,158,132)), (0,0,0), *body_r))
    scene.extend(xform_part(build_box((-2,0,-2),(2,12,2),(52,58,68)), (-1.9,12,0), *rleg_r))
    scene.extend(xform_part(build_box((-2,0,-2),(2,12,2),(52,58,68)), (+1.9,12,0), *lleg_r))
    scene.extend(xform_part(build_box((-3,-2,-2),(1,10,2),(195,158,132)), (-5,2,0), *rarm_r))
    scene.extend(xform_part(build_box((-1,-2,-2),(3,10,2),(195,158,132)), (+5,2,0), *larm_r))

    M_bed = np.diag([-1.0, 1.0, 1.0])
    for knife_faces, is_left, arm_piv, arm_rot, b_ang, sc in attachments:
        ax, ay, az = arm_rot; bp, br, by = b_ang
        R_arm = rot_z(az) @ rot_y(ay) @ rot_x(ax)
        R_hand = rot_x(math.radians(-90.0)) @ rot_y(math.radians(180.0))
        t_hand = np.array([-1.0 if is_left else 1.0, 2.0, -10.0], dtype=np.float64)
        R_align = rot_x(math.radians(bp)) @ rot_z(math.radians(br)) @ rot_y(math.radians(by))
        t_disp = np.array([0.0, 0.5, 0.0], dtype=np.float64)
        s_tot = DISPLAY_SCALE * sc
        piv = np.array(arm_piv, dtype=np.float64)
        for q, uv, col, d, g in knife_faces:
            p = (q @ M_bed.T) * s_tot + t_disp
            p = ((p @ R_align.T + t_hand) @ R_hand.T) @ R_arm.T + piv
            scene.append((p, uv, col, d, g))

    R_root = rot_x(math.radians(root_pitch))
    out = []
    for q, uv, col, d, g in scene:
        qq = q.copy(); qq[:, 1] = 24.0 - qq[:, 1]
        out.append((qq @ R_root.T, uv, col, d, g))
    return out


def raster(faces, view_mat, w, h, sc, center, ambient=0.58, bg=(22, 24, 30)):
    fb = np.zeros((h, w, 3), dtype=np.float32); fb[:, :] = bg
    zbuf = np.full((h, w), -1e9, dtype=np.float32)
    ld = np.array([0.35, 0.72, -0.60], dtype=np.float64); ld /= np.linalg.norm(ld)
    cx, cy = w * 0.5, h * 0.52
    for quad, uvs, flat_rgb, diff_img, glow_img in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6: continue
        shade = ambient + (1.0 - ambient) * abs(float(np.dot(n / nl, ld)))
        scr = np.stack([cx - cam[:, 0] * sc, cy - cam[:, 1] * sc, cam[:, 2]], axis=1)
        uvs_u = uvs if uvs is not None else [(0.0, 0.0)] * 4
        for tri in ((0, 1, 2), (0, 2, 3)):
            p0, p1, p2 = scr[tri[0]], scr[tri[1]], scr[tri[2]]
            t0, t1, t2 = uvs_u[tri[0]], uvs_u[tri[1]], uvs_u[tri[2]]
            x0, x1 = max(0, int(math.floor(min(p0[0], p1[0], p2[0])))), min(w - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
            y0, y1 = max(0, int(math.floor(min(p0[1], p1[1], p2[1])))), min(h - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
            if x0 > x1 or y0 > y1: continue
            area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
            if abs(area) < 1e-4: continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1, dtype=np.float32) + 0.5, np.arange(y0, y1 + 1, dtype=np.float32) + 0.5)
            w0 = ((p1[0] - gx) * (p2[1] - gy) - (p2[0] - gx) * (p1[1] - gy)) / area
            w1 = ((p2[0] - gx) * (p0[1] - gy) - (p0[0] - gx) * (p2[1] - gy)) / area
            w2 = 1.0 - w0 - w1
            ins = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not np.any(ins): continue
            z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
            sub_z = zbuf[y0:y1 + 1, x0:x1 + 1]
            cand = ins & (z > sub_z)
            if not np.any(cand): continue
            cy_i, cx_i = np.where(cand)
            if flat_rgb is not None:
                sub_z[cy_i, cx_i] = z[cy_i, cx_i]
                fb[y0 + cy_i, x0 + cx_i] = np.clip(np.array(flat_rgb, dtype=np.float32) * shade, 0, 255)
            else:
                u = np.clip(np.floor(w0[cand] * t0[0] + w1[cand] * t1[0] + w2[cand] * t2[0]).astype(np.int32), 0, TEX_W - 1)
                v = np.clip(np.floor(w0[cand] * t0[1] + w1[cand] * t1[1] + w2[cand] * t2[1]).astype(np.int32), 0, TEX_H - 1)
                vis = diff_img[v, u, 3] >= 26.0
                if not np.any(vis): continue
                cy_v, cx_v, u_v, v_v = cy_i[vis], cx_i[vis], u[vis], v[vis]
                sub_z[cy_v, cx_v] = z[cy_v, cx_v]
                d_c = diff_img[v_v, u_v, :3]; g_c = glow_img[v_v, u_v, :3]; g_a = glow_img[v_v, u_v, 3:4] / 255.0
                fb[y0 + cy_v, x0 + cx_v] = np.clip(d_c * shade * (1.0 - g_a * 0.55) + g_c * (g_a * 0.95), 0, 255)
    return Image.fromarray(fb.astype(np.uint8), "RGB")


def cam_mat(yw, pt):
    y, p = math.radians(yw), math.radians(pt)
    return np.array([[1,0,0],[0,math.cos(p),-math.sin(p)],[0,math.sin(p),math.cos(p)]]) @ \
           np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])


def render_togetsu_preview(w_geo, k_geo, f_geo, w_d, w_g, k_d, k_g, f_d, f_g, icon_dual, icon_fused):
    wf = extract_faces(w_geo, w_d, w_g)
    kf = extract_faces(k_geo, k_d, k_g)
    ff = extract_faces(f_geo, f_d, f_g)

    dual_scene = build_char_scene(
        SUNRAKU_HEAD, SUNRAKU_BODY, SUNRAKU_LARM, SUNRAKU_RARM, SUNRAKU_LLEG, SUNRAKU_RLEG,
        [(wf, True, (+5, 2, 0), SUNRAKU_LARM, SUNRAKU_LEFT_ANGLES, DUAL_SCALE),
         (kf, False, (-5, 2, 0), SUNRAKU_RARM, SUNRAKU_RIGHT_ANGLES, DUAL_SCALE)],
        root_pitch=-4.0,
    )
    fused_scene = build_char_scene(
        FUSED_HEAD, FUSED_BODY, FUSED_LARM, FUSED_RARM, FUSED_LLEG, FUSED_RLEG,
        [(ff, False, (-5, 2, 0), FUSED_RARM, FUSED_BLADE_ANGLES, FUSED_SCALE)],
        root_pitch=-6.0,
    )

    M_bed = np.diag([-1.0, 1.0, 1.0])
    pair_faces = [((q @ M_bed.T) + np.array([0.0, 0.0, -5.2]), uv, col, d, g) for q, uv, col, d, g in wf] + \
                 [((q @ M_bed.T) + np.array([0.0, 0.0, +5.2]), uv, col, d, g) for q, uv, col, d, g in kf]
    fused_solo = [((q @ M_bed.T), uv, col, d, g) for q, uv, col, d, g in ff]

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 520, 400
    im1 = raster(dual_scene, cam_mat(180, 0), pw, ph, 9.2, np.array([0.0, 15.0, -2.5]), ambient=0.62, bg=(148, 28, 34))
    im2 = raster(fused_scene, cam_mat(180, 4), pw, ph, 7.6, np.array([5.5, 14.5, -2.0]), ambient=0.62, bg=(22, 24, 34))
    im3 = raster(pair_faces, cam_mat(-90, 2), pw, ph, 11.4, np.array([0.0, 7.2, 0.0]), ambient=0.72, bg=(76, 78, 84))
    im4 = raster(fused_solo, cam_mat(-90, 2), pw, ph, 10.4, np.array([0.0, 10.5, -1.0]), ambient=0.72, bg=(76, 78, 84))
    im5 = raster(fused_solo, cam_mat(-90, 2), pw, ph, 10.4, np.array([0.0, 10.5, -1.0]), ambient=0.14, bg=(8, 10, 15))

    im6 = Image.new("RGB", (pw, ph), (20, 22, 28))
    dr6 = ImageDraw.Draw(im6)
    dr6.rectangle([30, 84, 242, 296], fill=(34, 34, 42), outline=(100, 110, 128))
    dr6.rectangle([278, 84, 490, 296], fill=(34, 34, 42), outline=(100, 110, 128))
    im6.paste(icon_dual.resize((192, 192), Image.NEAREST), (40, 94), icon_dual.resize((192, 192), Image.NEAREST))
    im6.paste(icon_fused.resize((192, 192), Image.NEAREST), (288, 94), icon_fused.resize((192, 192), Image.NEAREST))
    dr6.text((54, 312), "DUAL FORM ICON (32x32)", fill=(225, 235, 250))
    dr6.text((294, 312), "FUSED SOGENGETSU (32x32)", fill=(255, 220, 165))

    sheet = Image.new("RGB", (pw * 3, ph * 2), (10, 12, 16))
    sheet.paste(im1, (0, 0)); sheet.paste(im2, (pw, 0)); sheet.paste(im3, (pw * 2, 0))
    sheet.paste(im4, (0, ph)); sheet.paste(im5, (pw, ph)); sheet.paste(im6, (pw * 2, ph))
    dr = ImageDraw.Draw(sheet)
    dr.text((10, 8), "1. F5 SUNRAKU DUAL STANCE (WHITE TOP + BLACK BOTTOM)", fill=(255, 245, 245))
    dr.text((pw + 10, 8), "2. F5 FUSED SOGENGETSU GREATSWORD STANCE", fill=(215, 240, 255))
    dr.text((pw * 2 + 10, 8), "3. CLOSE-UP: WAXING (WHITE) & WANING (BLACK)", fill=(240, 245, 250))
    dr.text((10, ph + 8), "4. CLOSE-UP: FUSED SOGENGETSU CRESCENT GREATSWORD", fill=(240, 245, 250))
    dr.text((pw + 10, ph + 8), "5. NIGHT GLOWMASK (SOGENGETSU LUNAR EDGE)", fill=(185, 235, 255))
    dr.text((pw * 2 + 10, ph + 8), "6. 32x32 DUAL & FUSED INVENTORY ICONS", fill=(255, 225, 175))
    out = PREVIEW_DIR / "togetsu_preview.png"
    sheet.save(out)
    print(f"PREVIEW_OK: {out}")
    print(f"SOLVED SUNRAKU_LEFT_ANGLES  = {SUNRAKU_LEFT_ANGLES}")
    print(f"SOLVED SUNRAKU_RIGHT_ANGLES = {SUNRAKU_RIGHT_ANGLES}")
    print(f"SOLVED FUSED_BLADE_ANGLES   = {FUSED_BLADE_ANGLES}")


if __name__ == "__main__":
    w_geo = build_single_togetsu_geo("waxing")
    k_geo = build_single_togetsu_geo("waning")
    f_geo = build_fused_sogengetsu_geo()
    build_togetsu_animations()
    build_togetsu_item_model()
    w_d, w_g = build_single_togetsu_texture("waxing")
    k_d, k_g = build_single_togetsu_texture("waning")
    f_d, f_g = build_fused_sogengetsu_texture()
    i_dual, i_fused = build_togetsu_icons_32x32()
    render_togetsu_preview(w_geo, k_geo, f_geo, w_d, w_g, k_d, k_g, f_d, f_g, i_dual, i_fused)
    print("ALL_TOGETSU_ASSETS_DONE!")
