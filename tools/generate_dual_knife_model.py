#!/usr/bin/env python3
"""
Bộ sinh Model 3D Voxel GeckoLib + Texture 128x128 Cặp Song Dao Độc Bản cho Dual Knife (`abyssalrpg:dual_knife` — v4):
Khớp sát 100% ảnh mẫu song dao mới (giữ đúng tâm nắm tay `(0,0,0)`, trục lưỡi `+Y`, mép cắt `-Z` và `KNIFE_RENDER_SCALE = 0.92`
để 100% giữ nguyên pose F5 2 tầng vắt chéo + góc nhìn Thứ 1 kiểu kiếm Vanilla!):

1. Thanh Phải (`MAINHAND` — `dual_knife.geo.json`, `dual_knife_model.png`, `_glowmask.png` — theo thanh bên TRÁI trong ảnh mẫu):
   - Đuôi chuôi móc cong nhỏ màu Vàng Kim (`#D9A520` -> `#F7D754`).
   - Cán cầm thon dài bọc dây da Nâu Sẫm (`#3D2B22` -> `#593E30`).
   - Chắn tay chữ V góc cạnh màu Vàng Kim + 2 đinh tán vàng nhỏ ngay gốc lưỡi.
   - Thân lưỡi cong mượt Đen Nhám (`#19181D` -> `#27252E`), hoa văn đỏ mảnh gốc lưỡi + 1 ngạnh sống lưng đỏ,
     cùng **6 móc răng sóng ngược viền Đỏ Huyết rực (`#B80F24` -> `#E8192E` -> `#FF4A58`)** sắc lẹm!

2. Thanh Trái (`OFFHAND` — `dual_knife_left.geo.json`, `dual_knife_left_model.png`, `_glowmask.png` — theo thanh bên PHẢI trong ảnh mẫu):
   - Đuôi chuôi mũi giáo hình thoi Đen–Bạc cực sắc (`#181A20` + `#B8C2CC` -> `#E4ECF2`).
   - Cán cầm quấn vải Xanh Dương Lam Thép (`#2C5172` -> `#5788B2`).
   - Vòng cung bảo vệ tay Đen Tuyền 3D (`knuckle guard`) cong ôm song song cán xanh có khoảng hở rõ & đầu mỏ nhọn!
   - Chắn tay **Móng Vuốt Đồng Cổ 3D (`#7E4C24` -> `#B8783E` -> `#DCA062`)** gồm 2–3 móng vuốt sắc cong quặp về cán
     nối liền lên **dải hoa văn vảy/mạch đồng rỗng uốn cong** trên nửa dưới thân lưỡi đen!
   - Thân lưỡi Đen Nhám (`#181A20`) cong sắc, sống lưng 3 răng khía nhỏ + 2 bậc góc cạnh vát thép,
     mép cắt **Bạc Thép Sáng mài gương (`#9AA6B2` -> `#D2DCE6` -> `#F4F9FF`)** chia 4 bậc sóng sắc lẹm!

3. Icon kho đồ `32x32` (`dual_knife.png`) bắt chéo đúng cặp song dao mới, viền tối 1px sắc nét.

Chạy: python tools/generate_dual_knife_model.py
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "dual_knife_previews"

TEX_W = 128
TEX_H = 128
KNIFE_RENDER_SCALE = 0.92
DISPLAY_SCALE = 0.95

# Atlas 128x128:
# - Hàng trên (y=0..15): 8 ô 16x16 cho các chất liệu 3D (thân đen, vát thép, cán, chắn tay/móng đồng, lõi sáng, trong suốt)
# - Hàng 2 phải (x=64..127, y=16..31): thêm ô chất liệu phụ (vòng đen bóng, bạc sáng mũi giáo, vàng kim sáng, nâu đậm)
# - Vùng lớn (x=0..55, y=24..127 -> 56x104 px): bản vẽ mặt nghiêng lưỡi dao độ phân giải siêu cao!
MAT = {
    "BLADE_DARK":  (0,   0, 16, 16),
    "BLADE_STEEL": (16,  0, 16, 16),
    "STEEL_BEVEL": (32,  0, 16, 16),
    "GRIP_WRAP":   (48,  0, 16, 16),
    "GUARD_MAIN":  (64,  0, 16, 16),
    "GUARD_HI":    (80,  0, 16, 16),
    "EDGE_GLOW":   (96,  0, 16, 16),
    "TRANSPARENT": (112, 0, 16, 16),
    "POMMEL_CORE": (64, 16, 16, 16),
    "POMMEL_EDGE": (80, 16, 16, 16),
    "CLAW_DARK":   (96, 16, 16, 16),
    "ARC_BLACK":   (112,16, 16, 16),
}

PROFILE_UV = (0, 24, 56, 104)  # (u0, v0, uw, vh): u0=-Z (mép cắt), u0+uw=+Z (sống), v0=+Y (mũi), v0+vh=-Y (chuôi)


def uv_box(mat_key: str, pad: int = 1) -> dict:
    x0, y0, w, h = MAT[mat_key]
    p = 0 if mat_key == "TRANSPARENT" else pad
    f = {"uv": [x0 + p, y0 + p], "uv_size": [w - 2 * p, h - 2 * p]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def make_centered_cube(center, size, mat: str, rot=None, face_mats=None) -> dict:
    cx, cy, cz = [float(v) for v in center]
    sx, sy, sz = [float(v) for v in size]
    uvs = uv_box(mat)
    if face_mats:
        for fn, mk in face_mats.items():
            uvs[fn] = uv_box(mk)[fn]
    c = {
        "origin": [round(cx - sx * 0.5, 3), round(cy - sy * 0.5, 3), round(cz - sz * 0.5, 3)],
        "size": [round(sx, 3), round(sy, 3), round(sz, 3)],
        "uv": uvs,
    }
    if rot is not None and any(abs(v) > 1e-4 for v in rot):
        c["pivot"] = [round(cx, 3), round(cy, 3), round(cz, 3)]
        c["rotation"] = [round(float(rot[0]), 2), round(float(rot[1]), 2), round(float(rot[2]), 2)]
    return c


def make_profile_fin_cube(y_min: float, y_max: float, z_min: float, z_max: float, thickness_x: float = 0.16) -> dict:
    """Lá cắt 2 mặt độ phân giải cao 56x104 khớp tuyệt đối GeoQuad GeckoLib 4.8.3 (0 viền hộp thừa!)."""
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


def make_tube_segments(points_yz, width_x_list, depth_z_list, mat: str, alt_mat: str = None):
    """Dựng đường cong 3D mượt từ danh sách điểm (y, z) cho vòng bảo vệ tay đen & các móng vuốt đồng cổ!"""
    cubes = []
    for i in range(len(points_yz) - 1):
        ya, za = points_yz[i]
        yb, zb = points_yz[i + 1]
        ym, zm = 0.5 * (ya + yb), 0.5 * (za + zb)
        dy, dz = yb - ya, zb - za
        seg_len = math.hypot(dy, dz) * 1.14
        rx_deg = -math.degrees(math.atan2(dz, dy))
        wx = 0.5 * (width_x_list[i] + width_x_list[i + 1])
        dz_w = 0.5 * (depth_z_list[i] + depth_z_list[i + 1])
        mk = alt_mat if (alt_mat is not None and i >= len(points_yz) - 2) else mat
        eps = 0.01 if (i % 2 == 1) else 0.0
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
# 1. ĐƯỜNG CONG HÌNH HỌC CHUẨN 100% THEO ẢNH MẪU MỚI
# ==============================================================================

# --- A. THANH PHẢI (`MAINHAND` — Đỏ–Đen Răng Sóng, thanh BÊN TRÁI trong ảnh mẫu!) ---
RIGHT_Y_MIN, RIGHT_Y_MAX = 2.75, 16.05
RIGHT_Z_MIN, RIGHT_Z_MAX = -2.45, +2.05

# 6 móc răng sóng nhọn hướng ngược về phía chuôi (`barbed wave serrations`) dọc mép -Z!
RIGHT_BARB_INTERVALS = (
    (0.03, 0.16, 0.42),  # răng 1 gần chắn tay
    (0.16, 0.30, 0.50),  # răng 2
    (0.30, 0.45, 0.58),  # răng 3
    (0.45, 0.62, 0.68),  # răng 4 (bụng sóng rộng hơn)
    (0.62, 0.80, 0.72),  # răng 5 (răng móc sâu mạnh mẽ)
    (0.80, 0.94, 0.52),  # răng 6 sát đoạn vút lên mũi nhọn
)


def right_spine_z(u: float) -> float:
    """
    Sống lưng thanh Đỏ–Đen (cạnh phải trong ảnh mẫu):
    - Gốc lưỡi rộng có 1 NGẠNH NHỌN (`back-thorn`) nhô sắc ở u=0.26 (sau đó khuyết gọn vào tại u=0.33),
    - Rồi uốn cong hình lưỡi liềm duyên dáng vươn lên mũi nhọn sắc lẹm tại z=+1.35!
    """
    u = max(0.0, min(1.0, u))
    # Đường sống chính cong mềm chữ S / lưỡi liềm
    base = 0.86 - 1.70 * u * (1.0 - u) + 0.54 * (u ** 1.85)
    # Ngạnh nhọn (back-thorn) nhô ra phía sống ở gốc lưỡi (u = 0.08 .. 0.34, đỉnh sắc tại u = 0.25)
    thorn = 0.0
    if 0.06 <= u <= 0.35:
        if u <= 0.25:
            f = (u - 0.06) / 0.19
            thorn = 0.92 * (f ** 1.15)
        else:
            f = (u - 0.25) / 0.10
            # Khuyết lõm gắt phía trên ngạnh đúng như thanh trái trong ảnh mẫu!
            thorn = 0.92 * ((1.0 - f) ** 2.6)
    return base + thorn


def right_outer_edge_z(u: float) -> float:
    """Đường bao ngoài của các đỉnh răng sóng (mép -Z) uốn cong đồng điệu với sống lưng tới mũi nhọn."""
    u = max(0.0, min(1.0, u))
    # Mũi nhọn hội tụ đúng tại right_spine_z(1.0) = +1.40
    sp_no_thorn = 0.86 - 1.70 * u * (1.0 - u) + 0.54 * (u ** 1.85)
    # Độ rộng từ sống chính ra đỉnh răng: dày khoẻ từ gốc tới u=0.72 rồi vuốt nhọn nhanh lên mũi!
    width = 2.48 * ((1.0 - (u ** 2.45)) ** 0.56) * (0.86 + 0.24 * math.sin(math.pi * (u ** 0.85)))
    return sp_no_thorn - max(0.0, width)


def right_edge_z(u: float) -> float:
    """6 móc răng sóng nhọn hướng ngược về phía chuôi (`barbed wave serrations`)!"""
    u = max(0.0, min(1.0, u))
    z_out = right_outer_edge_z(u)
    if u < RIGHT_BARB_INTERVALS[0][0] or u > RIGHT_BARB_INTERVALS[-1][1]:
        return z_out
    cut_in = 0.0
    for a, b, depth in RIGHT_BARB_INTERVALS:
        if a <= u <= b:
            f = (u - a) / max(1e-6, b - a)
            # Tạo móc hướng NGƯỢC về phía chuôi (a):
            # - Tại f=0..0.18: nhô gắt ra đỉnh răng nhọn hướng ngược về phía chuôi!
            # - Tại f=0.18..1.00: uốn lõm cong tròn đều rồi gom về gốc răng kế tiếp!
            if f < 0.20:
                t = f / 0.20
                cut_in = depth * (1.0 - t) ** 0.65
            else:
                t = (f - 0.20) / 0.80
                # Cung lõm sắc hình trăng lưỡi liềm
                cut_in = depth * (math.sin(math.pi * 0.5 * t) ** 1.35)
            break
    # Đảm bảo mép cắt không bao giờ cắt vượt quá sống lưng
    return min(right_spine_z(u) - 0.14, z_out + cut_in)


def right_solid_core_inner_z(u: float) -> float:
    """Biên trong của lõi thép 3D đặc (chừa sạch vùng hoa văn đỏ gốc lưỡi & khe răng sóng lõm để hiện sắc nét 100%!)."""
    sp = 0.86 - 1.70 * u * (1.0 - u) + 0.54 * (u ** 1.85)
    z_out = right_outer_edge_z(u)
    w = max(0.0, sp - z_out)
    # Giữ lõi 3D gọn dọc sát sống lưng để hoa văn đỏ gốc lưỡi & 6 răng sóng lộ rõ 100% ở cả 2 mặt!
    frac = 0.26 if u < 0.36 else 0.34
    safe_w = max(0.0, min(w * frac, w - 0.96))
    return sp - safe_w


# --- B. THANH TRÁI (`OFFHAND` — Đen–Bạc Móng Vuốt Đồng & Vòng Đen, thanh BÊN PHẢI trong ảnh mẫu!) ---
LEFT_Y_MIN, LEFT_Y_MAX = 2.65, 16.35
LEFT_Z_MIN, LEFT_Z_MAX = -2.35, +1.75


def left_spine_z(u: float) -> float:
    """
    Sống lưng thanh Đen–Bạc (cạnh trên/phải trong ảnh mẫu):
    - Gốc nối liền vòng bảo vệ tay đen, có 3 răng khía nhỏ sát gốc (u=0.08..0.24),
    - Giữa lưỡi có 2 bậc góc cạnh sắc sảo (u=0.36 & u=0.66) rồi vuốt cong mượt tới mũi nhọn dài!
    """
    u = max(0.0, min(1.0, u))
    # Dáng cong thanh thoát mạnh mẽ hơi ngả về phía trước (-Z) ở mũi
    base = 0.96 + 0.36 * math.sin(math.pi * (u ** 0.82)) - 1.62 * (u ** 1.62)

    # 3 răng khía nhỏ sắc gần gốc lưỡi (u = 0.08 .. 0.25)
    notch = 0.0
    for ca in (0.10, 0.155, 0.21):
        d = abs(u - ca)
        if d < 0.024:
            notch += 0.22 * (1.0 - d / 0.024)

    # 2 bậc góc cạnh vát sắc trên sống lưng (tại u=0.35 và u=0.64 giống ảnh mẫu!)
    step_out = 0.0
    if 0.28 <= u <= 0.42:
        f = (u - 0.28) / 0.14
        step_out += 0.32 * (f if f < 0.72 else (1.0 - f) / 0.28)
    if 0.54 <= u <= 0.72:
        f = (u - 0.54) / 0.18
        step_out += 0.28 * (f if f < 0.72 else (1.0 - f) / 0.28)

    return base + notch + step_out


def left_edge_z(u: float) -> float:
    """
    Mép cắt Bạc Thép Sáng 4 bậc sóng sắc lẹm (cạnh dưới/trái trong ảnh mẫu):
    - Hơi thắt eo thanh lịch ngay trên móng vuốt đồng (u=0.05..0.14),
    - Bùng rộng 3–4 tầng sóng bạc vát sắc nối tiếp nhau (đỉnh bậc tại u=0.22, u=0.52, u=0.78) rồi vuốt nhọn tới mũi!
    """
    u = max(0.0, min(1.0, u))
    sp_base = 0.96 + 0.36 * math.sin(math.pi * (u ** 0.82)) - 1.62 * (u ** 1.62)
    # Độ rộng cơ sở khỏe khoắn, thon dần về mũi nhọn dài
    w_base = 2.42 * ((1.0 - (u ** 2.15)) ** 0.58) * (0.92 + 0.16 * math.sin(math.pi * u))

    # 3 bậc sóng sắc trên lưỡi cắt bạc (tạo các điểm gờ bậc rõ rệt sắc lẹm giống ảnh mẫu!)
    tiers = (
        (0.02, 0.25, 0.62),  # cung bạc sâu trên móng vuốt đồng -> bậc nhọn 1 tại u=0.25
        (0.25, 0.56, 0.54),  # cung sóng bạc giữa rộng -> bậc nhọn 2 tại u=0.56
        (0.56, 0.80, 0.44),  # cung sóng bạc trên -> bậc nhọn 3 tại u=0.80
        (0.80, 1.00, 0.16),  # đoạn vút nhọn lên mũi giáo
    )
    scallop = 0.0
    for a, b, depth in tiers:
        if a <= u <= b:
            f = (u - a) / max(1e-6, b - a)
            scallop = depth * (math.sin(math.pi * (f ** 0.78)) ** 0.78)
            break

    w_eff = max(0.0, w_base - scallop)
    return sp_base - w_eff


def left_solid_core_inner_z(u: float) -> float:
    """Biên trong của lõi thép đen 3D đặc: ôm gọn dọc sống lưng để lộ trọn 100% dải hoa văn vảy đồng cổ & cạnh mài bạc!"""
    sp_base = 0.96 + 0.36 * math.sin(math.pi * (u ** 0.82)) - 1.62 * (u ** 1.62)
    ed = left_edge_z(u)
    # Giữ khối 3D dày gọn ở 32% sát sống lưng -> toàn bộ dải vảy đồng & 4 bậc sóng bạc trên bản 56x104 hiển thị sắc nét không bị che!
    return sp_base * 0.68 + ed * 0.32


def _build_smooth_spine_slices(y_min, y_max, spine_fn, inner_z_fn, tx_base, tx_tip, mat_body="BLADE_DARK",
                               mat_bevel="STEEL_BEVEL", n_slices=14):
    """Sinh 14 lát cắt 3D tiếp tuyến đồng nhất (0 bậc vuông, 0 sọc xen kẽ!)."""
    cubes = []
    span = y_max - y_min
    du = 1.0 / n_slices
    for i in range(n_slices):
        ua, ub = i * du, (i + 1) * du
        um = 0.5 * (ua + ub)
        if um > 0.94:
            continue
        ya, yb = y_min + ua * span, y_min + ub * span
        ym = 0.5 * (ya + yb)
        za_sp, zb_sp = spine_fn(ua), spine_fn(ub)
        za_in, zb_in = inner_z_fn(ua), inner_z_fn(ub)
        zm_sp = 0.5 * (za_sp + zb_sp) - 0.05
        zm_in = 0.5 * (za_in + zb_in) + 0.05
        depth_z = zm_sp - zm_in
        if depth_z < 0.22:
            continue
        zm_ctr = 0.5 * (zm_sp + zm_in)
        seg_len = math.hypot(yb - ya, zb_sp - za_sp) * 1.08
        rx_deg = -math.degrees(math.atan2(zb_sp - za_sp, yb - ya))
        tx = tx_base * (1.0 - um) + tx_tip * um
        eps = 0.012 if (i % 2 == 1) else 0.0
        cubes.append(make_centered_cube([0.0, ym, zm_ctr], [tx + eps, seg_len, depth_z], mat_body, rot=[rx_deg, 0.0, 0.0]))
        bevel_d = min(0.24, depth_z * 0.30)
        cubes.append(make_centered_cube([0.0, ym, zm_sp - bevel_d * 0.45], [tx * 0.52 + eps, seg_len * 0.95, bevel_d],
                                        mat_bevel, rot=[rx_deg, 0.0, 0.0]))
    return cubes


# ==============================================================================
# 2. XÂY DỰNG MODEL 3D CHO CẢ 2 THANH (ĐÚNG ẢNH MẪU MỚI!)
# ==============================================================================

def build_right_crimson_kasaka_geo():
    """
    THANH PHẢI (`MAINHAND` — `dual_knife.geo.json`, theo thanh BÊN TRÁI ảnh mẫu):
    - Đuôi chuôi móc cong nhỏ màu Vàng Kim (`#D9A520` -> `#F7D754`).
    - Cán bọc dây da Nâu Sẫm (`#3D2B22` -> `#593E30`) quanh tâm `(0, 0, 0)`.
    - Chắn tay chữ V góc cạnh màu Vàng Kim + 2 đinh tán vàng nhỏ.
    - Thân lưỡi cong Đen Nhám + 6 móc răng sóng ngược viền Đỏ Huyết + ngạnh sống lưng đỏ!
    """
    hilt_cubes = []
    # 1. Cán da nâu sẫm quấn dây đều quanh tâm `(0, 0, 0)`
    hilt_cubes.append(make_centered_cube([0.0, 0.0, 0.0], [0.82, 4.20, 0.98], "GRIP_WRAP"))
    for wy in (-1.55, -0.92, -0.30, 0.30, 0.92, 1.55):
        hilt_cubes.append(make_centered_cube([0.0, wy, 0.0], [0.89, 0.16, 1.05], "CLAW_DARK", rot=[7.0, 0.0, 0.0]))

    # 2. Đuôi chuôi móc cong nhỏ màu Vàng Kim (`y = -2.15 .. -3.55`) giống hệt đầu móc vàng ở ảnh mẫu!
    hilt_cubes.append(make_centered_cube([0.0, -2.28, 0.0], [0.90, 0.52, 1.08], "GUARD_MAIN"))
    hilt_cubes.extend(make_tube_segments(
        [(-2.45, 0.05), (-2.95, -0.18), (-3.32, -0.52), (-3.50, -0.88)],
        [0.72, 0.54, 0.38, 0.22],
        [0.86, 0.68, 0.50, 0.32],
        "GUARD_MAIN", alt_mat="GUARD_HI"
    ))

    # 3. Chắn tay chữ V / cánh cung ngắn góc cạnh màu Vàng Kim (`y = +2.08 .. +3.18`)
    hilt_cubes.append(make_centered_cube([0.0, 2.42, 0.0], [1.12, 0.78, 1.65], "GUARD_MAIN"))
    # Cánh chắn tay bên -Z (hơi vát góc cạnh sang bên lưỡi cắt)
    hilt_cubes.append(make_centered_cube([0.0, 2.34, -1.25], [0.92, 0.64, 1.25], "GUARD_MAIN", rot=[-16.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 2.26, -1.96], [0.68, 0.50, 0.52], "GUARD_HI", rot=[-24.0, 0.0, 0.0]))
    # Cánh chắn tay bên +Z (hơi vát gọn sang bên sống lưng)
    hilt_cubes.append(make_centered_cube([0.0, 2.48, +1.18], [0.92, 0.62, 1.15], "GUARD_MAIN", rot=[18.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 2.58, +1.80], [0.68, 0.48, 0.48], "GUARD_HI", rot=[26.0, 0.0, 0.0]))
    # 2 đinh tán vàng kim nhỏ nổi 3D ngay sát gốc lưỡi đen giống hệt ảnh mẫu!
    hilt_cubes.append(make_centered_cube([0.0, 3.16, +0.34], [0.56, 0.24, 0.24], "GUARD_HI"))
    hilt_cubes.append(make_centered_cube([0.0, 3.16, -0.34], [0.56, 0.24, 0.24], "GUARD_HI"))

    # 4. Thân lưỡi 3D Đen Nhám mượt (14 lát spline nằm gọn bên trong lõi đen)
    def right_base_spine(u: float) -> float:
        return 0.86 - 1.70 * u * (1.0 - u) + 0.54 * (u ** 1.85)

    blade_cubes = _build_smooth_spine_slices(
        RIGHT_Y_MIN, RIGHT_Y_MAX, right_base_spine, right_solid_core_inner_z,
        tx_base=0.64, tx_tip=0.18, mat_body="BLADE_DARK", mat_bevel="STEEL_BEVEL", n_slices=14
    )

    # 5. Lõi cạnh Đỏ Huyết 3D mảnh chạy dọc rìa trong + Lá cắt mặt nghiêng 56x104 sắc nét (6 răng sóng ngược + ngạnh sống + hoa văn đỏ!)
    edge_cubes = []
    for i in range(9):
        ua = 0.05 + i * 0.095
        ub = ua + 0.095
        um = 0.5 * (ua + ub)
        ya = RIGHT_Y_MIN + ua * (RIGHT_Y_MAX - RIGHT_Y_MIN)
        yb = RIGHT_Y_MIN + ub * (RIGHT_Y_MAX - RIGHT_Y_MIN)
        za = right_solid_core_inner_z(ua) - 0.05
        zb = right_solid_core_inner_z(ub) - 0.05
        rx_deg = -math.degrees(math.atan2(zb - za, yb - ya))
        edge_cubes.append(make_centered_cube(
            [0.0, 0.5 * (ya + yb), 0.5 * (za + zb)],
            [0.16 * (1.0 - um * 0.45), math.hypot(yb - ya, zb - za) * 1.05, 0.22],
            "EDGE_GLOW", rot=[rx_deg, 0.0, 0.0],
        ))
    edge_cubes.append(make_profile_fin_cube(RIGHT_Y_MIN, RIGHT_Y_MAX, RIGHT_Z_MIN, RIGHT_Z_MAX, thickness_x=0.16))

    bones = [
        {"name": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0]},
        {"name": "blade_root", "parent": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0], "cubes": hilt_cubes},
        {"name": "blade_body", "parent": "blade_root", "pivot": [0, 3.0, 0], "rotation": [0, 0, 0], "cubes": blade_cubes},
        {"name": "blade_edge", "parent": "blade_body", "pivot": [0, 9.0, -0.6], "rotation": [0, 0, 0], "cubes": edge_cubes},
    ]
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.dual_knife",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 2.0,
                "visible_bounds_height": 2.4,
                "visible_bounds_offset": [0.0, 0.5, 0.0],
            },
            "bones": bones,
        }],
    }
    out = ASSETS / "geo" / "item" / "dual_knife.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    n_cubes = sum(len(b.get("cubes", [])) for b in bones)
    print(f"GEO_RIGHT_OK: {out} ({n_cubes} cubes, Crimson-Black Barbed Dagger + Brown Grip + Gold V-Guard)")
    return geo, n_cubes


def build_left_bronze_claw_silver_geo():
    """
    THANH TRÁI (`OFFHAND` — `dual_knife_left.geo.json`, theo thanh BÊN PHẢI ảnh mẫu):
    - Đuôi chuôi mũi giáo hình thoi Đen–Bạc cực sắc (`y = -2.15 .. -4.15`).
    - Cán quấn vải Xanh Dương Lam Thép (`#2C5172` -> `#5788B2`) quanh tâm `(0, 0, 0)`.
    - **Vòng cung bảo vệ tay Đen Tuyền 3D (`knuckle guard`)** ôm song song toàn bộ cán xanh với khoảng hở rõ & đầu mỏ nhọn!
    - **Chắn tay Móng Vuốt Đồng Cổ 3D (`bronze claw guard`)** với 2–3 móng vuốt sắc cong quặp về cán!
    - Thân lưỡi Đen Nhám + hoa văn vảy đồng cổ + 4 bậc sóng Bạc Thép Sáng mài gương!
    """
    hilt_cubes = []
    # 1. Cán quấn dây vải Xanh Dương Lam Thép quanh `(0, 0, 0)`
    hilt_cubes.append(make_centered_cube([0.0, 0.0, 0.0], [0.80, 4.18, 0.96], "GRIP_WRAP"))
    for wy in (-1.45, -0.72, 0.0, 0.72, 1.45):
        hilt_cubes.append(make_centered_cube([0.0, wy, 0.0], [0.86, 0.16, 1.02], "GRIP_WRAP", rot=[12.0, 0.0, 0.0]))

    # 2. Đuôi chuôi mũi giáo hình thoi Đen–Bạc (`diamond spear pommel`, `y = -2.15 .. -4.15`)
    hilt_cubes.append(make_centered_cube([0.0, -2.35, 0.0], [0.76, 0.45, 0.96], "POMMEL_EDGE"))
    hilt_cubes.append(make_centered_cube([0.0, -2.92, 0.0], [0.46, 0.92, 1.18], "POMMEL_EDGE", rot=[0.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, -2.95, 0.0], [0.54, 0.76, 0.62], "POMMEL_CORE"))
    hilt_cubes.append(make_centered_cube([0.0, -3.52, 0.0], [0.30, 0.72, 0.74], "POMMEL_EDGE"))
    hilt_cubes.append(make_centered_cube([0.0, -3.96, 0.0], [0.16, 0.50, 0.34], "POMMEL_EDGE"))

    # 3. Vòng cung bảo vệ tay Đen Tuyền 3D (`knuckle guard`, nối liền sống gốc lưỡi, ôm dọc cán xanh với mỏ nhọn sắc!)
    knuckle_pts = [
        ( 4.10, +0.92),
        ( 3.45, +1.68),
        ( 2.40, +2.26),
        ( 1.10, +2.52),
        (-0.25, +2.54),
        (-1.55, +2.34),
        (-2.58, +1.94),
        (-3.26, +1.42),
        (-3.68, +0.98),
    ]
    knuckle_wx = [0.66, 0.58, 0.50, 0.46, 0.44, 0.42, 0.36, 0.26, 0.14]
    knuckle_dz = [0.96, 0.80, 0.68, 0.62, 0.58, 0.54, 0.48, 0.38, 0.22]
    hilt_cubes.extend(make_tube_segments(knuckle_pts, knuckle_wx, knuckle_dz, "ARC_BLACK", alt_mat="STEEL_BEVEL"))
    # Ngạnh nhỏ trang trí trên khuỷu vòng bảo vệ tay sát đuôi (như ảnh mẫu!)
    hilt_cubes.append(make_centered_cube([0.0, -2.50, +2.28], [0.28, 0.56, 0.48], "ARC_BLACK", rot=[34.0, 0.0, 0.0]))

    # 4. Chắn tay Móng Vuốt Đồng Cổ Điêu Khắc 3D (`bronze claw guard` — cụm khớp ôm gốc lưỡi + 3 móng vuốt cong quặp về cán!)
    # Cụm giáp đồng cổ ôm đỉnh cán & cổ lưỡi
    hilt_cubes.append(make_centered_cube([0.0, 2.38, -0.18], [1.14, 0.96, 1.48], "GUARD_MAIN", rot=[-10.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 2.96, -0.58], [1.06, 0.92, 1.38], "GUARD_HI", rot=[-18.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 2.52, +0.58], [0.98, 0.78, 0.88], "CLAW_DARK", rot=[12.0, 0.0, 0.0]))
    # Móng vuốt 1 (móng ngoài lớn nhất, cong quặp dài ra phía -Z và hướng xuống về phía cán!)
    claw1_pts = [(3.15, -0.88), (2.72, -1.68), (1.96, -2.26), (1.05, -2.44), (0.28, -2.28)]
    hilt_cubes.extend(make_tube_segments(claw1_pts, [0.84, 0.72, 0.56, 0.40, 0.22], [0.76, 0.66, 0.54, 0.40, 0.24],
                                         "GUARD_MAIN", alt_mat="GUARD_HI"))
    # Móng vuốt 2 (móng giữa sắc cong quặp song song bên trong!)
    claw2_pts = [(2.58, -0.68), (1.95, -1.28), (1.10, -1.58), (0.22, -1.48)]
    hilt_cubes.extend(make_tube_segments(claw2_pts, [0.76, 0.60, 0.44, 0.22], [0.68, 0.56, 0.42, 0.24],
                                         "GUARD_HI", alt_mat="GUARD_MAIN"))
    # Móng vuốt 3 (móng phụ ngắn gập sát thân cán tạo cảm giác bàn tay vuốt rồng 3D!)
    claw3_pts = [(2.28, -0.45), (1.68, -0.86), (1.05, -0.96)]
    hilt_cubes.extend(make_tube_segments(claw3_pts, [0.66, 0.48, 0.26], [0.56, 0.42, 0.24],
                                         "CLAW_DARK", alt_mat="GUARD_MAIN"))

    # 5. Thân lưỡi Đen Nhám 3D mượt (14 lát spline)
    def left_base_spine(u: float) -> float:
        return 0.96 + 0.36 * math.sin(math.pi * (u ** 0.82)) - 1.62 * (u ** 1.62)

    blade_cubes = _build_smooth_spine_slices(
        LEFT_Y_MIN, LEFT_Y_MAX, left_base_spine, left_solid_core_inner_z,
        tx_base=0.66, tx_tip=0.18, mat_body="BLADE_DARK", mat_bevel="STEEL_BEVEL", n_slices=14
    )

    # 6. Lõi vát Bạc Thép 3D mảnh chạy dọc cạnh cắt + Lá cắt mặt nghiêng 56x104 sắc nét (vảy đồng + 4 bậc sóng bạc + răng khía sống!)
    edge_cubes = []
    for i in range(10):
        ua = 0.06 + i * 0.088
        ub = ua + 0.088
        um = 0.5 * (ua + ub)
        ya = LEFT_Y_MIN + ua * (LEFT_Y_MAX - LEFT_Y_MIN)
        yb = LEFT_Y_MIN + ub * (LEFT_Y_MAX - LEFT_Y_MIN)
        za = left_edge_z(ua) + 0.18
        zb = left_edge_z(ub) + 0.18
        rx_deg = -math.degrees(math.atan2(zb - za, yb - ya))
        edge_cubes.append(make_centered_cube(
            [0.0, 0.5 * (ya + yb), 0.5 * (za + zb)],
            [0.15 * (1.0 - um * 0.45), math.hypot(yb - ya, zb - za) * 1.05, 0.20],
            "EDGE_GLOW", rot=[rx_deg, 0.0, 0.0],
        ))
    edge_cubes.append(make_profile_fin_cube(LEFT_Y_MIN, LEFT_Y_MAX, LEFT_Z_MIN, LEFT_Z_MAX, thickness_x=0.16))

    bones = [
        {"name": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0]},
        {"name": "blade_root", "parent": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0], "cubes": hilt_cubes},
        {"name": "blade_body", "parent": "blade_root", "pivot": [0, 3.0, 0], "rotation": [0, 0, 0], "cubes": blade_cubes},
        {"name": "blade_edge", "parent": "blade_body", "pivot": [0, 9.0, -0.6], "rotation": [0, 0, 0], "cubes": edge_cubes},
    ]
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.dual_knife_left",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 2.2,
                "visible_bounds_height": 2.4,
                "visible_bounds_offset": [0.0, 0.5, 0.0],
            },
            "bones": bones,
        }],
    }
    out = ASSETS / "geo" / "item" / "dual_knife_left.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    n_cubes = sum(len(b.get("cubes", [])) for b in bones)
    print(f"GEO_LEFT_OK: {out} ({n_cubes} cubes, Black-Silver Dagger + Bronze Claw Guard + Black Knuckle Arc + Blue Grip)")
    return geo, n_cubes


def build_dual_knife_animations():
    anims = {
        "animation.dual_knife.idle": {
            "loop": True,
            "animation_length": 2.4,
            "bones": {
                "blade_edge": {
                    "scale": {
                        "0.0": [1.0, 1.0, 1.0],
                        "1.2": [1.012, 1.003, 1.005],
                        "2.4": [1.0, 1.0, 1.0],
                    }
                }
            },
        },
        "animation.dual_knife.right_slash": {
            "animation_length": 0.30,
            "bones": {"blade_root": {"rotation": {"0.0": [-12, 18, -10], "0.12": [42, -32, -28], "0.30": [0, 0, 0]}}},
        },
        "animation.dual_knife.left_slash": {
            "animation_length": 0.30,
            "bones": {"blade_root": {"rotation": {"0.0": [-12, -18, 10], "0.12": [42, 32, 28], "0.30": [0, 0, 0]}}},
        },
        "animation.dual_knife.cross_slash": {
            "animation_length": 0.34,
            "bones": {
                "blade_root": {
                    "rotation": {"0.0": [-24, 0, 0], "0.14": [58, 0, -24], "0.34": [0, 0, 0]},
                    "scale": {"0.0": [1.0, 1.0, 1.0], "0.14": [1.05, 1.03, 1.05], "0.34": [1.0, 1.0, 1.0]},
                }
            },
        },
    }
    out = ASSETS / "animations" / "item" / "dual_knife.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out} ({len(anims)} animations)")


# ==============================================================================
# 3. SINH TEXTURE 128x128 RGBA + SELECTIVE GLOWMASK CHO CẢ 2 THANH
# ==============================================================================

def _put_tile(diff, glow, key, rgb, g_rgba=None):
    x0, y0, w, h = MAT[key]
    diff[y0:y0 + h, x0:x0 + w, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    diff[y0:y0 + h, x0:x0 + w, 3] = 255
    if g_rgba is not None:
        glow[y0:y0 + h, x0:x0 + w] = np.clip(g_rgba, 0, 255).astype(np.uint8)


def build_right_crimson_kasaka_texture():
    """Texture 128x128 + Selective Glowmask cho Thanh Phải (Đỏ–Đen Răng Sóng, cán nâu, chắn tay vàng)."""
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    rng = np.random.default_rng(20281)

    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32)
    noise = rng.random((16, 16)).astype(np.float32)
    grain = 0.5 + 0.5 * np.sin((xx * 0.7 + yy * 0.45) + 1.1 * np.sin(yy * 0.55 - xx * 0.3))

    # Màu chuẩn thanh bên TRÁI ảnh mẫu:
    B_DARK   = np.array([ 25,  24,  29], dtype=np.float32)  # #19181D đen nhám lì
    B_MID    = np.array([ 39,  37,  46], dtype=np.float32)  # #27252E
    B_BEVEL  = np.array([ 72,  66,  78], dtype=np.float32)
    BR_DARK  = np.array([ 54,  38,  29], dtype=np.float32)  # #36261D dây da nâu sẫm
    BR_MID   = np.array([ 86,  60,  46], dtype=np.float32)  # #563C2E
    BR_HI    = np.array([112,  80,  62], dtype=np.float32)
    GD_DARK  = np.array([182, 134,  24], dtype=np.float32)  # #B68618 vàng kim đậm
    GD_MID   = np.array([224, 174,  36], dtype=np.float32)  # #E0AE24 vàng kim sáng
    GD_HI    = np.array([250, 220,  94], dtype=np.float32)  # #FADC5E
    R_DEEP   = np.array([184,  15,  36], dtype=np.float32)  # #B80F24 đỏ huyết đậm
    R_VIVID  = np.array([232,  25,  46], dtype=np.float32)  # #E8192E đỏ huyết rực
    R_HI     = np.array([255,  74,  88], dtype=np.float32)  # #FF4A58 highlight mép đỏ
    R_ROSE   = np.array([204,  24,  62], dtype=np.float32)  # #CC183E hoa văn đỏ hồng gốc lưỡi

    # Các ô 16x16 cho chi tiết 3D
    t = np.clip(grain * 0.65 + noise * 0.35, 0.0, 1.0)[:, :, None]
    _put_tile(diff, glow, "BLADE_DARK", B_DARK * (1.0 - t) + B_MID * t)
    _put_tile(diff, glow, "BLADE_STEEL", B_MID * (1.0 - t) + B_BEVEL * t * 0.6)
    _put_tile(diff, glow, "STEEL_BEVEL", B_MID * 0.5 + B_BEVEL * (0.5 + 0.3 * t))

    # Cán da nâu sẫm quấn dây ngang/chéo sắc nét (0 glow)
    strap = ((yy * 1.2 + xx * 0.35) % 4.0) < 1.35
    c_grip = np.where(strap[:, :, None], BR_DARK * 0.85, BR_MID * (0.88 + 0.24 * noise[:, :, None]))
    strap_hi = (((yy * 1.2 + xx * 0.35) % 4.0) >= 1.35) & (((yy * 1.2 + xx * 0.35) % 4.0) < 2.1)
    c_grip = np.where(strap_hi[:, :, None], BR_HI * 0.92, c_grip)
    _put_tile(diff, glow, "GRIP_WRAP", c_grip)
    _put_tile(diff, glow, "CLAW_DARK", BR_DARK * (0.82 + 0.20 * noise[:, :, None]))

    # Chắn tay & móc đuôi màu Vàng Kim sáng (0 glow — ăn sáng kim loại thật!)
    tg = np.clip((15.0 - xx) / 15.0 * 0.55 + grain * 0.30 + noise * 0.15, 0.0, 1.0)[:, :, None]
    c_gd = GD_DARK * (1.0 - tg) + GD_MID * tg
    c_gd = np.where(((xx <= 2) | (yy <= 1))[:, :, None], GD_HI, c_gd)
    _put_tile(diff, glow, "GUARD_MAIN", c_gd)
    _put_tile(diff, glow, "GUARD_HI", GD_MID * (1.0 - tg) + GD_HI * tg)

    # Lõi vát Đỏ Huyết (glow nhẹ chọn lọc)
    te = np.clip(0.35 + 0.55 * (xx / 15.0) + 0.10 * noise, 0.0, 1.0)[:, :, None]
    c_ed = R_DEEP * (1.0 - te) + R_VIVID * te
    g_ed = np.dstack([c_ed * 0.90, np.full((16, 16), 190.0, dtype=np.float32)])
    _put_tile(diff, glow, "EDGE_GLOW", c_ed, g_ed)

    # Các ô không dùng / trong suốt
    _put_tile(diff, glow, "POMMEL_CORE", B_DARK * np.ones((16, 16, 1)))
    _put_tile(diff, glow, "POMMEL_EDGE", GD_MID * np.ones((16, 16, 1)))
    _put_tile(diff, glow, "ARC_BLACK", B_DARK * np.ones((16, 16, 1)))
    tx0, ty0, tw, th = MAT["TRANSPARENT"]
    diff[ty0:ty0 + th, tx0:tx0 + tw] = 0
    glow[ty0:ty0 + th, tx0:tx0 + tw] = 0

    # --- VẼ BẢN NGHIÊNG LƯỠI DAO 56x104 SIÊU SẮC NÉT ---
    u0, v0, uw, vh = PROFILE_UV
    noise_p = rng.random((vh, uw)).astype(np.float32)

    for py in range(vh):
        u = 1.0 - (py + 0.5) / vh
        sp = right_spine_z(u)
        ed = right_edge_z(u)
        w_blade = sp - ed
        if w_blade <= 0.02:
            continue

        # Phát hiện đỉnh ngạnh sống lưng (u ≈ 0.25) và mũi nhọn (u > 0.88)
        in_thorn_zone = (0.04 <= u <= 0.34)
        for px in range(uw):
            z = RIGHT_Z_MIN + ((px + 0.5) / uw) * (RIGHT_Z_MAX - RIGHT_Z_MIN)
            if z < ed or z > sp:
                continue

            d_edge = z - ed
            d_spine = sp - z
            n_v = noise_p[py, px]

            # 1. Nền lõi lưỡi Đen Nhám lì cực sạch (#19181D -> #27252E)
            dam = 0.5 + 0.5 * math.sin(px * 0.35 + py * 0.22)
            col = B_DARK * (0.72 + 0.14 * n_v) + B_MID * (0.28 * dam)
            g_col = np.zeros(3, dtype=np.float32)
            g_alpha = 0.0

            # 2. Hai chấm đinh tán vàng kim nhỏ ngay gốc lưỡi (sát chắn tay vàng đúng ảnh mẫu!)
            if 0.015 <= u <= 0.070:
                d_r1 = math.hypot((u - 0.042) * 14.0, (z - 0.36) * 2.2)
                d_r2 = math.hypot((u - 0.042) * 14.0, (z + 0.32) * 2.2)
                if min(d_r1, d_r2) < 0.28:
                    col = GD_HI if min(d_r1, d_r2) < 0.16 else GD_MID

            # 3. Hoa văn đường nét mảnh Đỏ Hồng/Đỏ Huyết uốn lượn rõ nét ở gốc lưỡi (u = 0.04 .. 0.38)
            if 0.04 <= u <= 0.38:
                f = (u - 0.04) / 0.34
                c_a = 0.56 * (1.0 - f * 0.68) + 0.28 * math.sin(math.pi * f)
                c_b = -0.34 * (1.0 - f * 0.58) + 0.36 * (f ** 1.15)
                if abs(z - c_a) < 0.095 or abs(z - c_b) < 0.095:
                    col = R_ROSE * 0.65 + R_VIVID * 0.35
                    g_col = R_VIVID * 0.80
                    g_alpha = 115.0

            # 4. Viền Đỏ Huyết rực dọc toàn bộ 6 răng sóng (-Z) + mũi nhọn + ngạnh sống lưng (+Z)!
            red_edge_w = 0.34 if u < 0.84 else 0.44
            # Mép cắt răng sóng (-Z)
            if d_edge <= red_edge_w:
                et = 1.0 - d_edge / red_edge_w
                if et > 0.66:
                    k = (et - 0.66) / 0.34
                    col = R_VIVID * (1.0 - k) + R_HI * k
                    g_col = col
                    g_alpha = 215.0
                elif et > 0.24:
                    k = (et - 0.24) / 0.42
                    col = R_DEEP * (1.0 - k) + R_VIVID * k
                    g_col = col * 0.85
                    g_alpha = 150.0
                else:
                    k = et / 0.24
                    col = B_DARK * (1.0 - k * 0.90) + R_DEEP * (k * 0.90)
                    g_col = R_DEEP * k * 0.55
                    g_alpha = 65.0 * k

            # Viền Đỏ Hồng/Đỏ Huyết dọc cạnh sống lưng (+Z): đậm rõ ở ngạnh gốc & suốt đường cong lên mũi!
            spine_red_w = 0.34 if in_thorn_zone else (0.28 if u > 0.75 else 0.22)
            if d_spine <= spine_red_w:
                st = 1.0 - d_spine / spine_red_w
                s_base = R_ROSE if in_thorn_zone else R_DEEP
                if st > 0.60:
                    k = (st - 0.60) / 0.40
                    col = R_VIVID * (1.0 - k * 0.55) + R_HI * (k * 0.55)
                    if 195.0 > g_alpha:
                        g_col = col * 0.90
                        g_alpha = 195.0
                else:
                    k = st / 0.60
                    col = B_DARK * (1.0 - k * 0.88) + s_base * (k * 0.88)
                    if 125.0 * k > g_alpha:
                        g_col = s_base * k * 0.75
                        g_alpha = 125.0 * k

            diff[v0 + py, u0 + px, :3] = np.clip(col, 0, 255).astype(np.uint8)
            diff[v0 + py, u0 + px, 3] = 255
            if g_alpha > 4.0:
                glow[v0 + py, u0 + px, :3] = np.clip(g_col, 0, 255).astype(np.uint8)
                glow[v0 + py, u0 + px, 3] = int(min(255, g_alpha))

    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(tex_dir / "dual_knife_model.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "dual_knife_model_glowmask.png")
    print("TEX_RIGHT_OK: dual_knife_model.png + selective glowmask (128x128 Crimson-Black Barbed + Gold Guard)")
    return diff.astype(np.float32), glow.astype(np.float32)


def build_left_bronze_claw_silver_texture():
    """Texture 128x128 + Selective Glowmask cho Thanh Trái (Đen–Bạc Móng Vuốt Đồng, Vòng Đen, Cán Xanh Dương)."""
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    rng = np.random.default_rng(20282)

    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32)
    noise = rng.random((16, 16)).astype(np.float32)
    grain = 0.5 + 0.5 * np.sin((xx * 0.72 + yy * 0.48) + 1.15 * np.sin(yy * 0.55 - xx * 0.3))

    # Màu chuẩn thanh bên PHẢI ảnh mẫu:
    K_DARK   = np.array([ 24,  26,  32], dtype=np.float32)  # #181A20 đen nhám ánh thép
    K_MID    = np.array([ 39,  42,  51], dtype=np.float32)  # #272A33
    K_STEEL  = np.array([ 76,  84,  96], dtype=np.float32)
    S_MID    = np.array([154, 166, 178], dtype=np.float32)  # #9AA6B2 bạc thép
    S_LIGHT  = np.array([210, 220, 230], dtype=np.float32)  # #D2DCE6 bạc sáng
    S_MIRROR = np.array([244, 249, 255], dtype=np.float32)  # #F4F9FF mài gương
    S_ICE    = np.array([216, 244, 255], dtype=np.float32)  # #D8F4FF ánh sáng lạnh glowmask tinh tế
    BL_DARK  = np.array([ 36,  68,  96], dtype=np.float32)  # #244460 cán vải xanh dương lam thép
    BL_MID   = np.array([ 62, 108, 148], dtype=np.float32)  # #3E6C94
    BL_HI    = np.array([ 87, 136, 178], dtype=np.float32)  # #5788B2
    BZ_DARK  = np.array([108,  64,  28], dtype=np.float32)  # #6C401C móng vuốt đồng nâu cổ
    BZ_MID   = np.array([184, 120,  62], dtype=np.float32)  # #B8783E
    BZ_HI    = np.array([224, 166, 104], dtype=np.float32)  # #E0A668

    t = np.clip(grain * 0.62 + noise * 0.38, 0.0, 1.0)[:, :, None]
    _put_tile(diff, glow, "BLADE_DARK", K_DARK * (1.0 - t) + K_MID * t)
    _put_tile(diff, glow, "BLADE_STEEL", K_MID * (1.0 - t) + K_STEEL * t)
    _put_tile(diff, glow, "STEEL_BEVEL", K_STEEL * (1.0 - t) + S_MID * t)
    _put_tile(diff, glow, "ARC_BLACK", K_DARK * (0.90 + 0.16 * t) + K_MID * (0.20 * (xx < 3)[:, :, None]))

    # Cán vải Xanh Dương Lam Thép quấn chéo rõ ràng (0 glow)
    wrap_a = ((xx + yy * 1.4) % 5.0) < 1.6
    wrap_hi = (((xx + yy * 1.4) % 5.0) >= 1.6) & (((xx + yy * 1.4) % 5.0) < 2.6)
    c_blue = np.where(wrap_a[:, :, None], BL_DARK, BL_MID)
    c_blue = np.where(wrap_hi[:, :, None], BL_HI, c_blue) * (0.90 + 0.16 * noise[:, :, None])
    _put_tile(diff, glow, "GRIP_WRAP", c_blue)

    # Móng vuốt đồng cổ điêu khắc có gân sọc tự nhiên (0 glow — giữ khối kim loại đồng thật!)
    rib = 0.5 + 0.5 * np.sin(xx * 1.15 + 0.6 * np.sin(yy * 0.45))
    tb = np.clip(rib * 0.58 + (15.0 - yy) / 15.0 * 0.25 + noise * 0.17, 0.0, 1.0)[:, :, None]
    c_bz = BZ_DARK * (1.0 - tb) + BZ_MID * tb
    c_bz = np.where((rib > 0.80)[:, :, None], c_bz * 0.45 + BZ_HI * 0.60, c_bz)
    _put_tile(diff, glow, "GUARD_MAIN", c_bz)
    _put_tile(diff, glow, "GUARD_HI", BZ_MID * (1.0 - tb) + BZ_HI * tb)
    _put_tile(diff, glow, "CLAW_DARK", BZ_DARK * (0.78 + 0.30 * tb))

    # Đuôi mũi giáo hình thoi Đen–Bạc (cạnh bạc có ánh sáng lạnh rất nhẹ trên glowmask!)
    _put_tile(diff, glow, "POMMEL_CORE", K_DARK * (1.0 - t) + K_MID * t)
    ts = np.clip(0.35 + 0.55 * (xx / 15.0) + 0.10 * noise, 0.0, 1.0)[:, :, None]
    c_sil = S_MID * (1.0 - ts) + S_MIRROR * ts
    g_pom = np.dstack([np.broadcast_to(S_ICE * 0.55, (16, 16, 3)), np.full((16, 16), 95.0, dtype=np.float32)])
    _put_tile(diff, glow, "POMMEL_EDGE", c_sil, g_pom)

    # Lõi vát cạnh cắt Bạc Thép Sáng (glow lạnh tinh tế!)
    g_ed = np.dstack([np.broadcast_to(S_ICE * 0.72, (16, 16, 3)), np.full((16, 16), 135.0, dtype=np.float32)])
    _put_tile(diff, glow, "EDGE_GLOW", c_sil, g_ed)

    tx0, ty0, tw, th = MAT["TRANSPARENT"]
    diff[ty0:ty0 + th, tx0:tx0 + tw] = 0
    glow[ty0:ty0 + th, tx0:tx0 + tw] = 0

    # --- VẼ BẢN NGHIÊNG LƯỠI DAO 56x104 SIÊU SẮC NÉT ---
    u0, v0, uw, vh = PROFILE_UV
    noise_p = rng.random((vh, uw)).astype(np.float32)

    for py in range(vh):
        u = 1.0 - (py + 0.5) / vh
        sp = left_spine_z(u)
        ed = left_edge_z(u)
        w_blade = sp - ed
        if w_blade <= 0.02:
            continue

        # Đường tâm của dải hoa văn vảy/mạch đồng cổ uốn cong nổi bật trên nửa dưới thân lưỡi (u = 0.00 .. 0.62)
        scale_band_z = (sp * 0.44 + ed * 0.56) + 0.14 * math.sin(math.pi * (u / 0.62)) if u <= 0.62 else 99.0

        for px in range(uw):
            z = LEFT_Z_MIN + ((px + 0.5) / uw) * (LEFT_Z_MAX - LEFT_Z_MIN)
            if z < ed or z > sp:
                continue

            d_edge = z - ed
            d_spine = sp - z
            n_v = noise_p[py, px]

            # 1. Thân lưỡi Đen Nhám ánh thép (#181A20 -> #272A33)
            dam = 0.5 + 0.5 * math.sin(px * 0.36 + py * 0.24)
            col = K_DARK * (0.72 + 0.14 * n_v) + K_MID * (0.28 * dam)
            g_col = np.zeros(3, dtype=np.float32)
            g_alpha = 0.0

            # 2. Dải hoa văn vảy/mạch đồng rỗng uốn cong (u = 0.00 .. 0.62) nối liền từ chắn tay móng vuốt đồng!
            if u <= 0.62:
                f = u / 0.62
                band_half_w = 0.54 * ((1.0 - f ** 1.30) ** 0.68)
                dz_b = z - scale_band_z
                if abs(dz_b) <= band_half_w:
                    # Khung viền đồng vàng-nâu rõ nét + các gân vảy đan chéo tạo mắt lưới đen bên trong giống hệt ảnh mẫu!
                    on_border = abs(dz_b) >= band_half_w - 0.135
                    cell_phase = (u * 22.0 + dz_b * 1.9) % 1.0
                    cell_phase2 = (u * 22.0 - dz_b * 1.9) % 1.0
                    on_rib = (cell_phase < 0.25) or (cell_phase2 < 0.25)
                    if on_border or on_rib:
                        col = BZ_HI if (on_border or dz_b < 0) else BZ_MID
                        g_col = BZ_HI * 0.45
                        g_alpha = 58.0
                    else:
                        col = K_DARK * 0.72

            # 3. Mép cắt Bạc Thép Sáng mài gương (#9AA6B2 -> #D2DCE6 -> #F4F9FF) rộng & sắc lẹm!
            silver_w = (0.56 * (1.0 - 0.28 * u)) if u < 0.85 else 0.46
            if d_edge <= silver_w:
                et = 1.0 - d_edge / silver_w
                if et > 0.72:
                    k = (et - 0.72) / 0.28
                    col = S_LIGHT * (1.0 - k) + S_MIRROR * k
                    g_col = S_ICE * (0.75 + 0.25 * k)
                    g_alpha = 185.0
                elif et > 0.28:
                    k = (et - 0.28) / 0.44
                    col = S_MID * (1.0 - k) + S_LIGHT * k
                    g_col = S_ICE * 0.50
                    g_alpha = 105.0
                else:
                    k = et / 0.28
                    # Ranh giới vát sắc gọn giữa thân đen nhám và cạnh mài bạc gương!
                    col = K_STEEL * (1.0 - k) + S_MID * k
                    g_col = S_ICE * 0.22 * k
                    g_alpha = 40.0 * k

            # 4. Viền vát xám-bạc mảnh dọc sống lưng góc cạnh (+Z)
            if d_spine <= 0.18:
                st = 1.0 - d_spine / 0.18
                col = K_STEEL * (1.0 - st * 0.65) + S_LIGHT * (st * 0.65)
                if u > 0.84 and 130.0 * st > g_alpha:
                    g_col = S_ICE * 0.65
                    g_alpha = 130.0 * st

            diff[v0 + py, u0 + px, :3] = np.clip(col, 0, 255).astype(np.uint8)
            diff[v0 + py, u0 + px, 3] = 255
            if g_alpha > 4.0:
                glow[v0 + py, u0 + px, :3] = np.clip(g_col, 0, 255).astype(np.uint8)
                glow[v0 + py, u0 + px, 3] = int(min(255, g_alpha))

    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(tex_dir / "dual_knife_left_model.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "dual_knife_left_model_glowmask.png")
    print("TEX_LEFT_OK: dual_knife_left_model.png + selective glowmask (128x128 Black-Silver + Bronze Claw + Blue Grip)")
    return diff.astype(np.float32), glow.astype(np.float32)


# ==============================================================================
# 4. ICON KHO ĐỒ 32x32 BẮT CHÉO ĐÚNG CẶP SONG DAO MỚI
# ==============================================================================

def build_inventory_icon_32x32():
    """
    Icon 32x32 cặp song dao mới bắt chéo chữ X (viền tối 1px sắc nét):
    - Lớp sau (trên-trái -> dưới-phải): Dao Đen–Bạc Móng Vuốt Đồng (đuôi mũi giáo bạc, cán xanh dương, vòng bảo vệ tay đen, móng vuốt đồng cổ, lưỡi đen cạnh bạc!).
    - Lớp trước (dưới-trái -> trên-phải): Dao Đỏ–Đen Răng Sóng (đuôi móc vàng, cán nâu, chắn tay vàng chữ V, lưỡi đen răng sóng đỏ huyết!).
    """
    C_OUTLINE = (9, 8, 12, 255)
    C_BLK     = (24, 24, 30, 255)
    C_BLK_MID = (42, 44, 52, 255)
    C_STL     = (98, 108, 120, 255)
    C_SIL     = (208, 220, 232, 255)
    C_SIL_HI  = (244, 250, 255, 255)

    C_BLUE_D  = (42, 76, 108, 255)
    C_BLUE_L  = (82, 132, 174, 255)
    C_BRZ_D   = (122, 72, 34, 255)
    C_BRZ_L   = (216, 152, 86, 255)

    C_BRN_D   = (62, 42, 32, 255)
    C_BRN_L   = (104, 72, 54, 255)
    C_GOLD    = (224, 174, 36, 255)
    C_GOLD_HI = (250, 224, 102, 255)
    C_RED_D   = (176, 16, 36, 255)
    C_RED     = (232, 26, 46, 255)
    C_RED_HI  = (255, 84, 96, 255)

    def add_outline(layer: Image.Image) -> Image.Image:
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
                            opx[x, y] = C_OUTLINE
                            break
        return out

    # --- Lớp 1 (Sau): Thanh Đen–Bạc Móng Vuốt Đồng (trên-trái (3,3) -> mũi bạc dưới-phải (28,28)) ---
    l_sil = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    spx = l_sil.load()

    def put_s(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            spx[x, y] = c

    # Đuôi mũi giáo bạc + cán xanh dương lam thép
    for x, y, c in [
        (3, 3, C_SIL_HI), (4, 4, C_SIL), (5, 4, C_BLK), (4, 5, C_SIL),
        (5, 5, C_BLUE_D), (6, 6, C_BLUE_L), (7, 7, C_BLUE_D), (8, 8, C_BLUE_L), (9, 9, C_BLUE_D),
        # Vòng cung bảo vệ tay Đen Tuyền ôm trên cán xanh (có khoảng hở rõ!)
        (6, 3, C_BLK), (7, 3, C_BLK_MID), (8, 4, C_BLK), (9, 5, C_BLK_MID),
        (10, 6, C_BLK), (11, 7, C_BLK_MID), (12, 8, C_BLK), (12, 9, C_BLK),
        # Chắn tay Móng Vuốt Đồng Cổ quặp xuống phía dưới-trái
        (10, 10, C_BRZ_L), (11, 10, C_BRZ_D), (10, 11, C_BRZ_L),
        (9, 12, C_BRZ_L), (8, 13, C_BRZ_D), (7, 13, C_BRZ_L),
        (10, 13, C_BRZ_D), (9, 14, C_BRZ_L),
    ]:
        put_s(x, y, c)

    # Thân lưỡi Đen Nhám + vảy đồng gốc + mép cắt Bạc Thép Sáng
    for step in range(17):
        t = step / 16.0
        curve = int(round(math.sin(math.pi * t) * 0.9))
        x = 12 + step
        y = 11 + step - curve
        put_s(x, y, C_BLK_MID if step % 3 == 0 else C_BLK)
        if step < 15:
            put_s(x, y + 1, C_BRZ_L if (step < 6 and step % 2 == 0) else C_BLK)
        # Mép cắt Bạc Thép Sáng bậc sóng dọc cạnh dưới-trái
        ex = x - (1 if step < 14 else 0)
        ey = y + (2 if step < 13 else 1)
        put_s(ex, ey, C_SIL_HI if (step >= 12 or step in (3, 8)) else C_SIL)
        if step in (3, 8, 12):
            put_s(ex - 1, ey, C_STL)
    put_s(29, 27, C_SIL_HI)

    # --- Lớp 2 (Trước): Thanh Đỏ–Đen Răng Sóng (dưới-trái (4,27) -> mũi đỏ trên-phải (28,3)) ---
    l_red = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    rpx = l_red.load()

    def put_r(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            rpx[x, y] = c

    # Đuôi móc vàng + cán da nâu sẫm + chắn tay chữ V vàng kim
    for x, y, c in [
        (3, 27, C_GOLD_HI), (4, 27, C_GOLD), (5, 26, C_GOLD),
        (6, 25, C_BRN_D), (7, 24, C_BRN_L), (8, 23, C_BRN_D), (9, 22, C_BRN_L), (10, 21, C_BRN_D),
        # Chắn tay chữ V Vàng Kim nổi bật
        (8, 19, C_GOLD_HI), (9, 20, C_GOLD), (10, 20, C_GOLD_HI), (11, 21, C_GOLD),
        (12, 22, C_GOLD), (13, 22, C_GOLD_HI),
    ]:
        put_r(x, y, c)

    # Thân lưỡi Đen Nhám + ngạnh sống đỏ + 5 răng sóng Đỏ Huyết rực
    barbs = {2, 5, 9, 13}
    for step in range(18):
        t = step / 17.0
        dip = int(round(math.sin(math.pi * t) * 0.9))
        x = 11 + step
        y = 19 - step + dip
        # Viền sống đỏ + ngạnh đỏ ở gốc
        put_r(x, y, C_RED if step in (2, 3, 15, 16, 17) else C_RED_D)
        if step == 3:
            put_r(x - 1, y - 1, C_RED_HI)
        if step < 16:
            put_r(x + 1, y, C_BLK)
        if step < 14:
            put_r(x + 1, y + 1, C_BLK_MID if step % 3 == 0 else C_BLK)
        # Răng sóng Đỏ Huyết dọc cạnh cắt
        if step in barbs:
            put_r(x + 2, y + 1, C_RED)
            put_r(x + 2, y + 2, C_RED_HI)
        elif step < 15:
            put_r(x + 2, y + 1, C_RED if step % 2 == 0 else C_RED_D)
        else:
            put_r(x + 1, y, C_RED_HI)
    put_r(28, 3, C_RED_HI)
    put_r(29, 2, C_RED_HI)

    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    im.alpha_composite(add_outline(l_sil))
    im.alpha_composite(add_outline(l_red))

    out = ASSETS / "textures" / "item" / "dual_knife.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)
    print(f"ICON_OK: {out} (32x32 Dual-Knife New Icon: Red-Black Gold Guard + Black-Silver Bronze Claw)")
    return im


# ==============================================================================
# 5. BỘ DỰNG ẢNH PREVIEW 1:1 (F5 + FIRST PERSON + CẬN CẢNH NGÀY/ĐÊM + ICON)
# ==============================================================================

def extract_geckolib_faces(geo: dict, diff_img, glow_img):
    faces = []
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            x0, y0, z0 = ox, oy, oz
            x1, y1, z1 = ox + sx, oy + sy, oz + sz
            bLB = np.array([x1, y0, z0], dtype=np.float64)
            bRB = np.array([x1, y0, z1], dtype=np.float64)
            tLB = np.array([x1, y1, z0], dtype=np.float64)
            tRB = np.array([x1, y1, z1], dtype=np.float64)
            tLF = np.array([x0, y1, z0], dtype=np.float64)
            tRF = np.array([x0, y1, z1], dtype=np.float64)
            bLF = np.array([x0, y0, z0], dtype=np.float64)
            bRF = np.array([x0, y0, z1], dtype=np.float64)
            quad_defs = (
                ("west",  [tRB, tLB, bLB, bRB]),
                ("east",  [tLF, tRF, bRF, bLF]),
                ("north", [tLB, tLF, bLF, bLB]),
                ("south", [tRF, tRB, bRB, bRF]),
                ("up",    [tRB, tRF, tLF, tLB]),
                ("down",  [bLB, bLF, bRF, bRB]),
            )
            rot = c.get("rotation")
            if rot is not None:
                piv = np.array(c["pivot"], dtype=np.float64)
                R_bed = rot_z(-math.radians(rot[2])) @ rot_y(+math.radians(rot[1])) @ rot_x(-math.radians(rot[0]))
            else:
                piv, R_bed = None, None

            uv_map = c["uv"]
            for fname, verts in quad_defs:
                if fname not in uv_map:
                    continue
                fuv = uv_map[fname]
                u0, v0 = float(fuv["uv"][0]), float(fuv["uv"][1])
                uw, vh = float(fuv["uv_size"][0]), float(fuv["uv_size"][1])
                uv_corners = [(u0 + uw, v0), (u0, v0), (u0, v0 + vh), (u0 + uw, v0 + vh)]
                q = np.array(verts, dtype=np.float64)
                if R_bed is not None:
                    q = ((q - piv) @ R_bed.T) + piv
                faces.append((q, uv_corners, None, diff_img, glow_img))
    return faces


def build_box_faces_local(bmin, bmax, col_rgb):
    x0, y0, z0 = bmin
    x1, y1, z1 = bmax
    corners = np.array([
        [x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
        [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1],
    ], dtype=np.float64)
    return [(corners[idxs], None, col_rgb, None, None)
            for idxs in ([0, 1, 2, 3], [5, 4, 7, 6], [4, 0, 3, 7], [1, 5, 6, 2], [3, 2, 6, 7], [4, 5, 1, 0])]


def transform_mc_part(faces, pivot, rx, ry, rz):
    R = rot_z(rz) @ rot_y(ry) @ rot_x(rx)
    piv = np.array(pivot, dtype=np.float64)
    return [((q @ R.T) + piv, uv, col, d, g) for q, uv, col, d, g in faces]


def mc_ydown_to_yup(faces):
    out = []
    for q, uv, col, d, g in faces:
        qq = q.copy()
        qq[:, 1] = 24.0 - qq[:, 1]
        out.append((qq, uv, col, d, g))
    return out


STANCE_HEAD = (0.32, -0.18, 0.0)
STANCE_BODY = (0.20, +0.22, 0.0)
STANCE_LARM = (-1.54, +0.92, -0.15)
STANCE_RARM = (-0.78, -0.96, +0.26)
STANCE_RLEG = (+0.30, -0.22, +0.14)
STANCE_LLEG = (-0.26, +0.22, -0.14)
STANCE_ROOT_PITCH_DEG = -6.0

LEFT_GEO_REV_R = rot_x(math.radians(112.0)) @ rot_y(math.radians(180.0)) @ rot_z(math.radians(-8.0))
LEFT_GEO_REV_T = np.array([-0.025 * 16.0, 0.0, 0.010 * 16.0], dtype=np.float64)
RIGHT_GEO_REV_R = np.eye(3, dtype=np.float64)
RIGHT_GEO_REV_T = np.zeros(3, dtype=np.float64)


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


def solve_stance_blade_angles(arm_rot, R_rev, target_tip_yup, target_edge_yup):
    F_y = np.diag([1.0, -1.0, 1.0])
    M_bedrock = np.diag([-1.0, 1.0, 1.0])
    ax, ay, az = arm_rot
    R_arm = rot_z(az) @ rot_y(ay) @ rot_x(ax)
    R_hand = rot_x(math.radians(-90.0)) @ rot_y(math.radians(180.0))
    u_tip = np.array(target_tip_yup, dtype=np.float64)
    u_tip /= np.linalg.norm(u_tip)
    e_raw = np.array(target_edge_yup, dtype=np.float64)
    e_proj = e_raw - np.dot(e_raw, u_tip) * u_tip
    u_edge = e_proj / np.linalg.norm(e_proj)
    u_z = -u_edge
    u_x = np.cross(u_tip, u_z)
    u_x /= np.linalg.norm(u_x)
    A = F_y @ R_arm @ R_hand
    B = R_rev @ M_bedrock
    R_align = A.T @ np.column_stack([u_x, u_tip, u_z]) @ B.T
    p, r, y = decompose_rx_rz_ry(R_align)
    return round(p, 2), round(r, 2), round(y, 2)


LEFT_BLADE_ANGLES = solve_stance_blade_angles(
    STANCE_LARM, LEFT_GEO_REV_R,
    [+math.cos(math.radians(38.0)), -math.sin(math.radians(38.0)), -0.16],
    [-math.sin(math.radians(38.0)), -math.cos(math.radians(38.0)), -0.18],
)
RIGHT_BLADE_ANGLES = solve_stance_blade_angles(
    STANCE_RARM, RIGHT_GEO_REV_R,
    [-math.cos(math.radians(13.0)), +math.sin(math.radians(13.0)), -0.16],
    [-math.sin(math.radians(13.0)), -math.cos(math.radians(13.0)), -0.15],
)


def build_f5_character_scene(left_faces, right_faces):
    scene = []
    head = build_box_faces_local((-4, -8, -4), (4, 0, 4), (178, 144, 120))
    head += build_box_faces_local((-4.3, -8.3, -4.3), (4.3, -5.2, 4.3), (26, 24, 34))
    head += build_box_faces_local((-2.8, -4.5, -4.15), (-0.9, -3.5, -3.95), (90, 232, 255))
    head += build_box_faces_local((+0.9, -4.5, -4.15), (+2.8, -3.5, -3.95), (90, 232, 255))
    scene.extend(transform_mc_part(head, (0, 0, 0), *STANCE_HEAD))

    body = build_box_faces_local((-4, 0, -2), (4, 12, 2), (38, 36, 46))
    body += build_box_faces_local((-4.3, 0.4, -2.35), (4.3, 8.6, 2.35), (26, 25, 33))
    scene.extend(transform_mc_part(body, (0, 0, 0), *STANCE_BODY))

    rleg = build_box_faces_local((-2, 0, -2), (2, 12, 2), (34, 34, 44))
    lleg = build_box_faces_local((-2, 0, -2), (2, 12, 2), (34, 34, 44))
    scene.extend(transform_mc_part(rleg, (-1.9, 12, 0), *STANCE_RLEG))
    scene.extend(transform_mc_part(lleg, (+1.9, 12, 0), *STANCE_LLEG))

    rarm = build_box_faces_local((-3, -2, -2), (1, 10, 2), (54, 52, 64))
    rarm += build_box_faces_local((-3.05, 7.2, -2.05), (1.05, 10.05, 2.05), (176, 140, 114))
    larm = build_box_faces_local((-1, -2, -2), (3, 10, 2), (54, 52, 64))
    larm += build_box_faces_local((-1.05, 7.2, -2.05), (3.05, 10.05, 2.05), (176, 140, 114))
    scene.extend(transform_mc_part(rarm, (-5, 2, 0), *STANCE_RARM))
    scene.extend(transform_mc_part(larm, (+5, 2, 0), *STANCE_LARM))

    M_bedrock = np.diag([-1.0, 1.0, 1.0])

    def attach(knife_faces, is_left, arm_pivot, arm_rot, blade_angles, R_rev, t_rev):
        ax, ay, az = arm_rot
        bp, br, by = blade_angles
        R_arm = rot_z(az) @ rot_y(ay) @ rot_x(ax)
        R_hand = rot_x(math.radians(-90.0)) @ rot_y(math.radians(180.0))
        t_hand = np.array([-1.0 if is_left else 1.0, 2.0, -10.0], dtype=np.float64)
        R_align = rot_x(math.radians(bp)) @ rot_z(math.radians(br)) @ rot_y(math.radians(by))
        t_disp = np.array([0.0, 0.5, 0.0], dtype=np.float64)
        s_total = DISPLAY_SCALE * KNIFE_RENDER_SCALE
        piv = np.array(arm_pivot, dtype=np.float64)
        for q, uv, col, d, g in knife_faces:
            p = (q @ M_bedrock.T) * s_total
            p = (p @ R_rev.T) + t_rev * DISPLAY_SCALE
            p = p + t_disp
            p = p @ R_align.T
            p = (p + t_hand) @ R_hand.T
            p = (p @ R_arm.T) + piv
            scene.append((p, uv, col, d, g))

    attach(left_faces, True, (+5, 2, 0), STANCE_LARM, LEFT_BLADE_ANGLES, LEFT_GEO_REV_R, LEFT_GEO_REV_T)
    attach(right_faces, False, (-5, 2, 0), STANCE_RARM, RIGHT_BLADE_ANGLES, RIGHT_GEO_REV_R, RIGHT_GEO_REV_T)

    yup = mc_ydown_to_yup(scene)
    R_root = rot_x(math.radians(STANCE_ROOT_PITCH_DEG))
    return [(q @ R_root.T, uv, col, d, g) for q, uv, col, d, g in yup]


def raster_ortho(faces, view_mat, w, h, sc, center, ambient=0.48, bg=(18, 22, 28)):
    fb = np.zeros((h, w, 3), dtype=np.float32)
    fb[:, :] = bg
    zbuf = np.full((h, w), -1e9, dtype=np.float32)
    ld = np.array([0.35, 0.72, -0.60], dtype=np.float64)
    ld /= np.linalg.norm(ld)
    cx, cy = w * 0.5, h * 0.52

    for quad, uvs, flat_rgb, diff_img, glow_img in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = ambient + (1.0 - ambient) * abs(float(np.dot(n, ld)))
        scr = np.stack([cx - cam[:, 0] * sc, cy - cam[:, 1] * sc, cam[:, 2]], axis=1)
        _raster_quad(fb, zbuf, scr, uvs, flat_rgb, diff_img, glow_img, shade, w, h)
    return Image.fromarray(fb.astype(np.uint8), "RGB")


def raster_first_person(left_faces, right_faces, w=540, h=400, fov_deg=70.0):
    disp = json.loads((ASSETS / "models" / "item" / "dual_knife.json").read_text(encoding="utf-8"))["display"]
    M_bedrock = np.diag([-1.0, 1.0, 1.0])
    fp_faces = []

    def add_hand(knife_faces, key: str, is_left: bool):
        tf = disp[key]
        flip = -1.0 if is_left else 1.0
        arm_t = np.array([flip * 0.56 * 16.0, -0.52 * 16.0, -0.72 * 16.0], dtype=np.float64)
        tx, ty, tz = tf["translation"]
        rx, ry, rz = tf["rotation"]
        sx, sy, sz = tf["scale"]
        t_json = np.array([flip * tx, ty, tz], dtype=np.float64)
        R_json = rot_x(math.radians(rx)) @ rot_y(math.radians(flip * ry)) @ rot_z(math.radians(flip * rz))
        s_vec = np.array([sx, sy, sz], dtype=np.float64) * KNIFE_RENDER_SCALE
        for q, uvs, col, d, g in knife_faces:
            p = (q @ M_bedrock.T)
            p[:, 1] += 0.16
            p = p * s_vec[None, :]
            p = (p @ R_json.T) + t_json + arm_t
            fp_faces.append((p, uvs, col, d, g))

    add_hand(right_faces, "firstperson_righthand", False)
    add_hand(left_faces, "firstperson_lefthand", True)

    fb = np.zeros((h, w, 3), dtype=np.float32)
    hor = int(h * 0.50)
    fb[:hor, :] = (102, 128, 178)
    fb[hor:, :] = (76, 114, 52)
    zbuf = np.full((h, w), -1e9, dtype=np.float32)

    fy = (h * 0.5) / math.tan(math.radians(fov_deg * 0.5))
    fx = fy
    cx, cy = w * 0.5, h * 0.5
    ld = np.array([0.30, 0.78, 0.55], dtype=np.float64)
    ld /= np.linalg.norm(ld)

    for quad, uvs, flat_rgb, diff_img, glow_img in fp_faces:
        if np.any(quad[:, 2] >= -0.4):
            continue
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = 0.56 + 0.44 * abs(float(np.dot(n, ld)))
        depth = -quad[:, 2]
        scr = np.stack([cx + (quad[:, 0] / depth) * fx, cy - (quad[:, 1] / depth) * fy, -depth], axis=1)
        _raster_quad(fb, zbuf, scr, uvs, flat_rgb, diff_img, glow_img, shade, w, h)

    im = Image.fromarray(fb.astype(np.uint8), "RGB")
    dr = ImageDraw.Draw(im)
    ccx, ccy = w // 2, h // 2
    dr.line([(ccx - 8, ccy), (ccx + 8, ccy)], fill=(245, 245, 245), width=2)
    dr.line([(ccx, ccy - 8), (ccx, ccy + 8)], fill=(245, 245, 245), width=2)
    return im


def _raster_quad(fb, zbuf, scr, uvs, flat_rgb, diff_img, glow_img, shade, w, h):
    if uvs is None:
        uvs = [(0.0, 0.0)] * 4
    for tri in ((0, 1, 2), (0, 2, 3)):
        p0, p1, p2 = scr[tri[0]], scr[tri[1]], scr[tri[2]]
        t0, t1, t2 = uvs[tri[0]], uvs[tri[1]], uvs[tri[2]]
        x0 = max(0, int(math.floor(min(p0[0], p1[0], p2[0]))))
        x1 = min(w - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
        y0 = max(0, int(math.floor(min(p0[1], p1[1], p2[1]))))
        y1 = min(h - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
        if x0 > x1 or y0 > y1:
            continue
        area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
        if abs(area) < 1e-4:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1, dtype=np.float32) + 0.5,
                             np.arange(y0, y1 + 1, dtype=np.float32) + 0.5)
        w0 = ((p1[0] - gx) * (p2[1] - gy) - (p2[0] - gx) * (p1[1] - gy)) / area
        w1 = ((p2[0] - gx) * (p0[1] - gy) - (p0[0] - gx) * (p2[1] - gy)) / area
        w2 = 1.0 - w0 - w1
        ins = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
        if not np.any(ins):
            continue
        z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
        sub_z = zbuf[y0:y1 + 1, x0:x1 + 1]
        cand = ins & (z > sub_z)
        if not np.any(cand):
            continue
        cy_i, cx_i = np.where(cand)
        if flat_rgb is not None:
            sub_z[cy_i, cx_i] = z[cy_i, cx_i]
            fb[y0 + cy_i, x0 + cx_i] = np.clip(np.array(flat_rgb, dtype=np.float32) * shade, 0, 255)
        else:
            u = np.clip(np.floor(w0[cand] * t0[0] + w1[cand] * t1[0] + w2[cand] * t2[0]).astype(np.int32), 0, TEX_W - 1)
            v = np.clip(np.floor(w0[cand] * t0[1] + w1[cand] * t1[1] + w2[cand] * t2[1]).astype(np.int32), 0, TEX_H - 1)
            d_a = diff_img[v, u, 3]
            vis = d_a >= 26.0
            if not np.any(vis):
                continue
            cy_v, cx_v = cy_i[vis], cx_i[vis]
            u_v, v_v = u[vis], v[vis]
            sub_z[cy_v, cx_v] = z[cy_v, cx_v]
            d_c = diff_img[v_v, u_v, :3]
            g_c = glow_img[v_v, u_v, :3]
            g_a = glow_img[v_v, u_v, 3:4] / 255.0
            lit = d_c * shade * (1.0 - g_a * 0.55) + g_c * (g_a * 0.95)
            fb[y0 + cy_v, x0 + cx_v] = np.clip(lit, 0, 255)


def cam_mat(yw, pt):
    y, p = math.radians(yw), math.radians(pt)
    cy, sy = math.cos(y), math.sin(y)
    cp, sp = math.cos(p), math.sin(p)
    return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])


def render_previews(left_geo, right_geo, left_diff, left_glow, right_diff, right_glow, icon_im):
    left_faces = extract_geckolib_faces(left_geo, left_diff, left_glow)
    right_faces = extract_geckolib_faces(right_geo, right_diff, right_glow)

    f5_scene = build_f5_character_scene(left_faces, right_faces)
    char_ctr = np.array([-0.6, 15.8, -2.8], dtype=np.float64)

    # Cận cảnh 2 thanh song song (trái = Đỏ–Đen Răng Sóng, phải = Đen–Bạc Móng Vuốt Đồng đúng thứ tự ảnh mẫu!)
    M_bedrock = np.diag([-1.0, 1.0, 1.0])
    pair_faces = []
    for q, uvs, col, d, g in right_faces:
        pair_faces.append(((q @ M_bedrock.T) + np.array([0.0, 0.0, -4.2]), uvs, col, d, g))
    for q, uvs, col, d, g in left_faces:
        pair_faces.append(((q @ M_bedrock.T) + np.array([0.0, 0.0, +4.2]), uvs, col, d, g))
    pair_ctr = np.array([0.0, 6.2, 0.0], dtype=np.float64)

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 540, 400

    im1 = raster_ortho(f5_scene, cam_mat(180, 0), pw, ph, 10.6, char_ctr, ambient=0.58, bg=(20, 24, 32))
    im2 = raster_ortho(f5_scene, cam_mat(142, 8), pw, ph, 10.4, char_ctr, ambient=0.55, bg=(18, 22, 30))
    im3 = raster_first_person(left_faces, right_faces, w=pw, h=ph, fov_deg=70.0)
    # Nền sáng xám nhạt ở Panel 4 giống nền ảnh mẫu để nhìn cực rõ vòng đen, cán nâu/xanh, móng đồng & lưỡi bạc!
    im4 = raster_ortho(pair_faces, cam_mat(-86, 3), pw, ph, 16.4, pair_ctr, ambient=0.72, bg=(208, 214, 220))
    im5 = raster_ortho(pair_faces, cam_mat(-86, 3), pw, ph, 16.4, pair_ctr, ambient=0.14, bg=(7, 9, 14))

    im6 = Image.new("RGB", (pw, ph), (20, 22, 28))
    dr6 = ImageDraw.Draw(im6)
    dr6.rectangle([36, 84, 248, 296], fill=(32, 32, 40), outline=(85, 98, 114))
    dr6.rectangle([292, 84, 504, 296], fill=(156, 158, 166), outline=(85, 95, 108))
    big_item = icon_im.resize((192, 192), Image.NEAREST)
    im6.paste(big_item, (46, 94), big_item)
    im6.paste(big_item, (302, 94), big_item)
    dr6.text((64, 312), "DARK SLOT (HOTBAR)", fill=(180, 235, 255))
    dr6.text((314, 312), "LIGHT SLOT (CHEST/INV)", fill=(255, 160, 170))

    sheet = Image.new("RGB", (pw * 3, ph * 2), (10, 12, 16))
    sheet.paste(im1, (0, 0))
    sheet.paste(im2, (pw, 0))
    sheet.paste(im3, (pw * 2, 0))
    sheet.paste(im4, (0, ph))
    sheet.paste(im5, (pw, ph))
    sheet.paste(im6, (pw * 2, ph))

    dr = ImageDraw.Draw(sheet)
    dr.text((10, 8), "1. F5 FRONT (SCALE 0.92 - SAME 2-TIER STANCE!)", fill=(160, 235, 255))
    dr.text((pw + 10, 8), "2. F5 3/4 VIEW (SILVER CLAW TOP + RED BARB BOTTOM)", fill=(255, 145, 155))
    dr.text((pw * 2 + 10, 8), "3. FIRST-PERSON 16:9 FOV 70 (VANILLA DUAL-SWORD POSE)", fill=(255, 245, 170))
    dr.text((10, ph + 8), "4. CLOSE-UP DAY (LEFT: RED-BLACK GOLD | RIGHT: SILVER-CLAW BLUE)", fill=(24, 28, 36))
    dr.text((pw + 10, ph + 8), "5. CLOSE-UP NIGHT GLOWMASK (SELECTIVE EDGE GLOW ONLY)", fill=(160, 235, 255))
    dr.text((pw * 2 + 10, ph + 8), "6. 32x32 NEW DUAL-KNIFE CROSSED-X ICON", fill=(255, 165, 175))

    out_preview = PREVIEW_DIR / "dual_knife_stance_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")
    return out_preview


if __name__ == "__main__":
    right_geo, c_r = build_right_crimson_kasaka_geo()
    left_geo, c_l = build_left_bronze_claw_silver_geo()
    build_dual_knife_animations()
    right_diff, right_glow = build_right_crimson_kasaka_texture()
    left_diff, left_glow = build_left_bronze_claw_silver_texture()
    icon_im = build_inventory_icon_32x32()
    render_previews(left_geo, right_geo, left_diff, left_glow, right_diff, right_glow, icon_im)
    print(f"ALL_DONE: Photo-Faithful Dual Knife v4 generated (right={c_r}c, left={c_l}c, scale={KNIFE_RENDER_SCALE})")
