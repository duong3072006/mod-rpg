#!/usr/bin/env python3
"""
Bộ sinh 1:1 cho **Spirit Hydra Armor** (`abyssalrpg:spirit_hydra_helmet/chestplate/leggings/boots`)
theo đúng đặc tả `uhd1.txt` + đối chiếu trực tiếp Boss Spirit Hydra & ảnh thiết kế 6 góc:
  - GIỮ NGUYÊN: Mũ đầu rồng hở mặt, 3 đầu rồng phụ tách biệt sau lưng, đuôi rồng 6 đốt uốn cong & hệ bone chuẩn.
  - SỬA ÁO NGỰC (`armorBody`): xóa sạch 6 cục hộp vuông tối thô ở 2 bên sườn; thay bằng giáp vảy đá granite
    ôm dáng ngực chữ V gọn sắc + lõi/vết nứt tinh thể dọc giữa ngực (phình sáng hình thoi ở tâm, thuôn nhọn
    lên cổ & xuống bụng) phát sáng rực `FULL_BRIGHT` (`#E8FFFF` → `#66F7FF`).
  - SỬA VAI & TAY (`armorRightArm/LeftArm`): bọc kín 100% không hở da, thu gọn phom tay đỡ phồng vuông thô,
    gắn 3 sừng/gai đá nhọn chĩa chếch lên-ngoài trên 2 vai (gọn, không đâm vào cổ rồng), 2 vòng đai năng lượng
    ngang (bắp tay & cổ tay) phát sáng xanh điện rực `FULL_BRIGHT` + 3 mấu vuốt trắng gọn mép ngoài cổ tay.
  - SỬA QUẦN (`armorRightLeg/LeftLeg`): giáp vảy đá granite nổi bậc ôm đùi & cẳng chân có rãnh tối giữa 2 đùi
    + đai gối 3D nổi phát sáng rực `FULL_BRIGHT` (`#E8FFFF` → `#66F7FF`).
  - SỬA GIÀY (`armorRightBoot/LeftBoot`): xóa hàng 8 thanh mỏng sát nhau; dựng ĐÚNG 3 móng vuốt rồng 3D to khỏe
    tách khe rõ mỗi chân (tổng 6 móng) nhô trước ~1.8 px, chóp móng trắng ngà ánh băng phát sáng nhẹ trên glowmask,
    kèm đúng 1 cựa móng sau mỗi gót (đế hạ -0.14 px chống z-fighting).
  - TEXTURE: Vảy đá granite xám than-tro đen lốm đốm 1:1 lấy mẫu từ Boss Spirit Hydra (`#1B1F27`, `#2D3440`, `#465062`, `#647287`).

Chạy: python tools/generate_spirit_hydra_armor_model.py
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "spirit_hydra_armor_previews"

TEX_W = 256
TEX_H = 256

REG = {
    # Hàng 0 (y=0..16): Vảy đá granite Boss Spirit Hydra, năng lượng FULL_BRIGHT cyan, mắt, răng, móng phát sáng chóp
    "SCALE_DARK":       (0,    0,  16, 16),
    "SCALE_MID":        (16,   0,  16, 16),
    "SCALE_BRIGHT":     (32,   0,  16, 16),
    "SCALE_RIDGE":      (48,   0,  16, 16),
    "NECK_RING":        (64,   0,  16, 16),
    "TAIL_RING":        (80,   0,  16, 16),
    "CYAN_GLOW":        (96,   0,  16, 16),  # FULL_BRIGHT #66F7FF -> #E8FFFF (alpha 255 glowmask)
    "CYAN_HORN":        (112,  0,  16, 16),
    "CYAN_SPINE":       (128,  0,  16, 16),
    "CYAN_BLADE":       (144,  0,  16, 16),
    "HYDRA_EYE":        (160,  0,   8,  6),
    "IVORY_TOOTH":      (170,  0,   6,  6),
    "CLAW_HAND":        (178,  0,   8,  8),  # Mấu vuốt trắng gọn mép ngoài cổ tay
    "CLAW_BOOT_GLOW":   (188,  0,   8,  8),  # Móng vuốt giày trắng ngà ánh băng — chóp phát sáng trên glowmask!
    "NAVY_INNER":       (198,  0,   8,  8),  # Rãnh giáp tối tinh tế mặt trong đùi
    "BOOT_SOLE":        (208,  0,  10, 10),
    "EMPTY_TRANS":      (250, 250,  2,  2),

    # Hàng 1 (y=20..56): Bề mặt giáp chuyên biệt 1:1
    "CHEST_FRONT":      (0,   20,  18, 26),  # Ngực vảy granite chữ V + vết nứt lõi tinh thể dọc FULL_BRIGHT!
    "CHEST_BACK":       (20,  20,  18, 26),
    "CHEST_SIDE":       (40,  20,  10, 26),
    "HELM_TOP":         (52,  20,  16, 16),
    "HELM_SNOUT_TOP":   (70,  20,  14, 16),
    "HELM_SNOUT_SIDE":  (86,  20,  16, 12),
    "ARM_SLEEVE":       (104, 20,  12, 24),
    "PAULDRON_PLATE":   (118, 20,  14, 14),
    "LEG_THIGH":        (134, 20,  12, 18),
    "BOOT_SIDE":        (148, 20,  14, 14),
    "BOOT_TOP":         (164, 20,  12, 16),
    "BOOT_CUFF":        (178, 20,  14,  6),
    # Vùng chuyên biệt cho Mũ Đầu Rồng kiểu dáng Cinder Dragon (chất liệu đá granite + mắt xanh điện Spirit Hydra!)
    "HELM_BACK":        (194, 20,  16, 16),
    "HELM_SIDE":        (212, 20,  16, 16),
    "HELM_NOSE":        (230, 20,  12,  6),
    "HELM_EYE":         (244, 20,   6,  4),
    "HELM_HORN_BASE":   (194, 38,   8,  8),
    "HELM_HORN_MID":    (204, 38,   8,  8),
    "HELM_HORN_DARK":   (214, 38,   8,  8),
    "HELM_HORN_TIP":    (224, 38,   8,  8),
    "FANG_IVORY":       (234, 38,   4,  8),
    "HYDRA_SCALE_3D":   (240, 38,   8,  8),  # Vảy rồng đá granite vát cạnh 3D nổi sắc (mép trên sáng #8694AA -> mặt đá -> rãnh bóng tối!)

    # Hàng 2 (y=64..104): VÂY MANG QUẠT 5 NAN PHÁT SÁNG CYAN (>= 10 px viền alpha=0 xung quanh!)
    "FIN_LARGE_R":      (10,  64,  24, 34),
    "FIN_LARGE_L":      (48,  64,  24, 34),
    "FIN_MINI_R":       (88,  64,  20, 28),
    "FIN_MINI_L":       (122, 64,  20, 28),

    # Hàng 3 (y=114..134): VẬT LIỆU VẢY & MAI RỒNG HỮU CƠ TỰ NHIÊN BOSS SPIRIT HYDRA (0 sắt thép, 0 đinh tán, 0 sọc đồ ngủ!)
    "SCALE_SHINGLE":    (4,  114,  16, 16),  # Tấm vảy đá granite rồng xếp chồng tự nhiên (giống chất vảy Cinder Dragon trên tông granite Boss!)
    "SCALE_RING":       (24, 114,  12, 12),  # Tầng vảy da rồng xếp lớp tự nhiên quấn quanh cánh tay & ống chân
    "HYDRA_RIM":        (40, 114,  12, 12),  # Gờ mai hắc thạch tối điểm mạch băng xanh sâu cực mảnh 1px tự nhiên!
    "HYDRA_CLAW_TIP":   (56, 114,  12, 12),  # Khớp sừng/móng rồng hắc thạch ánh băng thẫm cho 6 móng kẹp tim & khớp ngón chân
    "WAIST_CRYSTAL":    (72, 114,  12, 12),  # Ổ đá tự nhiên khảm mảnh tinh thể Spirit Hydra rỉ sáng ngay trung tâm đai eo
    "KNEE_SCUTE":       (88, 114,  12, 12),  # Mấu vảy xương gối rồng tự nhiên vát nhọn (không phải khiên sắt!)
    "HIDE_DARK":        (104, 114, 12, 12),  # Lớp da vảy rồng tối mịn lót khớp tay/đùi tự nhiên (0 ca-rô, 0 xích sắt!)
    "BOOT_CARAPACE":    (120, 114, 12, 12),  # Mai chân rồng đá granite gân nổi tự nhiên cho đôi ủng 6 móng vuốt!
}


def verify_uv_and_fin_padding():
    occupied = {}
    for k, (x0, y0, w, h) in REG.items():
        assert x0 >= 0 and y0 >= 0 and x0 + w <= TEX_W and y0 + h <= TEX_H, f"OOB: {k}"
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                assert (x, y) not in occupied, f"UV overlap at ({x},{y}): {occupied[(x,y)]} vs {k}"
                occupied[(x, y)] = k
    for k, (x0, y0, w, h) in REG.items():
        if not k.startswith("FIN_"):
            continue
        for y in range(max(0, y0 - 6), min(TEX_H, y0 + h + 6)):
            for x in range(max(0, x0 - 6), min(TEX_W, x0 + w + 6)):
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    continue
                assert (x, y) not in occupied, f"Fin padding violated near {k} at ({x},{y}) by {occupied[(x,y)]}"


def bedrock_rot_matrix(rot_deg):
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


def uv_rect(key: str) -> dict:
    x0, y0, w, h = REG[key]
    return {"uv": [x0, y0], "uv_size": [w, h]}


def uv_box(mat_key: str, face_mats=None) -> dict:
    base = uv_rect(mat_key)
    uvs = {"north": base, "south": base, "east": base, "west": base, "up": base, "down": base}
    if face_mats:
        for fn, mk in face_mats.items():
            uvs[fn] = uv_rect(mk)
    return uvs


def make_cube(origin, size, mat: str, rot=None, pivot=None, face_mats=None) -> dict:
    ox, oy, oz = [round(float(v), 3) for v in origin]
    sx, sy, sz = [round(float(v), 3) for v in size]
    c = {"origin": [ox, oy, oz], "size": [sx, sy, sz], "uv": uv_box(mat, face_mats)}
    if rot is not None and any(abs(v) > 1e-4 for v in rot):
        if pivot is None:
            pivot = [ox + sx * 0.5, oy + sy * 0.5, oz + sz * 0.5]
        c["pivot"] = [round(float(v), 3) for v in pivot]
        c["rotation"] = [round(float(v), 2) for v in rot]
    return c


def make_centered_cube(center, size, mat: str, rot=None, face_mats=None) -> dict:
    cx, cy, cz = [float(v) for v in center]
    sx, sy, sz = [float(v) for v in size]
    return make_cube(
        [cx - sx * 0.5, cy - sy * 0.5, cz - sz * 0.5],
        [sx, sy, sz],
        mat,
        rot=rot,
        pivot=[cx, cy, cz],
        face_mats=face_mats,
    )


def make_segment_cube(p0, p1, sx: float, sy: float, mat: str, pad: float = 0.45, face_mats=None) -> dict:
    dx = float(p1[0] - p0[0])
    dy = float(p1[1] - p0[1])
    dz = float(p1[2] - p0[2])
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    assert length > 1e-5, "Zero-length segment"
    cx = (p0[0] + p1[0]) * 0.5
    cy = (p0[1] + p1[1]) * 0.5
    cz = (p0[2] + p1[2]) * 0.5
    uy = max(-1.0, min(1.0, dy / length))
    rot_x = math.degrees(math.asin(uy))
    rot_y = math.degrees(math.atan2(dx, dz))
    return make_centered_cube(
        [cx, cy, cz], [sx, sy, length + pad], mat,
        rot=[rot_x, rot_y, 0.0],
        face_mats=face_mats,
    )


def make_fin_plane(center, width, height, front_key: str, back_key: str, rot=None) -> dict:
    return make_centered_cube(
        center, [width, height, 0.06], "EMPTY_TRANS",
        rot=rot,
        face_mats={
            "north": front_key,
            "south": back_key,
            "up":    "EMPTY_TRANS",
            "down":  "EMPTY_TRANS",
            "east":  "EMPTY_TRANS",
            "west":  "EMPTY_TRANS",
        }
    )


def make_boss_5ray_gill_fin(to_world, side: float, root_lp, width: float, height: float,
                            fin_prefix: str, fan_sweep_back: float = 2.0, fan_lift_y: float = 1.2,
                            scale: float = 1.0) -> list:
    rx0, ry0, rz0 = root_lp
    f_tag = "R" if side < 0 else "L"
    b_tag = "L" if side < 0 else "R"

    w_root = np.array(to_world((rx0, ry0, rz0)), float)
    w_mid_tip = np.array(to_world((rx0 + side * width, ry0 + fan_lift_y, rz0 + fan_sweep_back)), float)
    w_up_ref = np.array(to_world((rx0, ry0 + 1.0, rz0)), float) - w_root

    out_dir = w_mid_tip - w_root
    out_len = float(np.linalg.norm(out_dir))
    out_hat = out_dir / max(1e-5, out_len)
    up_vec = w_up_ref - float(np.dot(w_up_ref, out_hat)) * out_hat
    up_hat = up_vec / max(1e-5, float(np.linalg.norm(up_vec)))

    rib_specs = [
        (-0.64, 1.04, 0.36),
        (-0.32, 1.08, 0.38),
        ( 0.00, 1.10, 0.38),
        (+0.32, 1.05, 0.36),
        (+0.62, 0.96, 0.34),
    ]
    out = []
    for ang, r_frac, thick in rib_specs:
        p_a = w_root + out_hat * (0.04 * out_len)
        p_b = w_root + out_hat * (out_len * r_frac * math.cos(ang)) - up_hat * ((height * scale * 0.50) * r_frac * math.sin(ang) * 1.04)
        out.append(make_segment_cube(tuple(p_a), tuple(p_b), thick * scale, thick * scale, "SCALE_DARK", pad=0.08))

    ex = out_hat if side > 0 else -out_hat
    ey = up_hat
    ez = np.cross(ex, ey)
    ez /= max(1e-5, float(np.linalg.norm(ez)))
    M = np.column_stack([ex, ey, ez])
    if np.linalg.det(M) < 0:
        ez = -ez
        M = np.column_stack([ex, ey, ez])

    sy = max(-1.0, min(1.0, -M[2, 0]))
    ry_rad = math.asin(sy)
    if abs(math.cos(ry_rad)) > 1e-4:
        rx_rad = -math.atan2(M[2, 1], M[2, 2])
        rz_rad = -math.atan2(M[1, 0], M[0, 0])
    else:
        rx_rad = 0.0
        rz_rad = -math.atan2(-M[0, 1], M[1, 1])

    rot_deg = [math.degrees(rx_rad), math.degrees(ry_rad), math.degrees(rz_rad)]
    R_chk = bedrock_rot_matrix(rot_deg)
    assert np.linalg.norm(R_chk[:, 0] - ex) < 1e-3 and np.linalg.norm(R_chk[:, 1] - ey) < 1e-3, f"Fin basis mismatch: {rot_deg}"

    fin_center = w_root + out_hat * (out_len * 0.50)
    out.append(make_fin_plane(
        tuple(fin_center),
        out_len,
        height * scale,
        f"{fin_prefix}_{f_tag}",
        f"{fin_prefix}_{b_tag}",
        rot=rot_deg,
    ))
    return out


def build_boss_matched_aux_head(center, yaw_deg: float, pitch_deg: float, scale: float = 1.0, is_crown: bool = False):
    cx, cy, cz = center
    ry = math.radians(yaw_deg)
    rx = math.radians(pitch_deg)
    cy_, sy_ = math.cos(ry), math.sin(ry)
    cx_, sx_ = math.cos(rx), math.sin(rx)
    R_yaw = np.array([[cy_, 0, -sy_], [0, 1, 0], [sy_, 0, cy_]], float)
    R_pit = np.array([[1, 0, 0], [0, cx_, -sx_], [0, sx_, cx_]], float)
    R = R_yaw @ R_pit

    def to_world(lp):
        v = R @ (np.array(lp, float) * scale)
        return (cx + v[0], cy + v[1], cz + v[2])

    cubes = []
    cubes.append(make_segment_cube(to_world((0, 0.55, +1.9)), to_world((0, 0.55, -1.5)), 3.55 * scale, 2.75 * scale, "SCALE_MID", pad=0.15))
    cubes.append(make_segment_cube(to_world((0, 1.55, +1.5)), to_world((0, 1.45, -1.6)), 2.55 * scale, 0.90 * scale, "SCALE_BRIGHT", pad=0.12))
    cubes.append(make_segment_cube(to_world((0, 0.45, -1.1)), to_world((0, 0.35, -3.7)), 2.75 * scale, 1.75 * scale, "SCALE_MID", pad=0.14))
    cubes.append(make_segment_cube(to_world((0, 0.35, -3.3)), to_world((0, 0.25, -5.4)), 2.05 * scale, 1.32 * scale, "SCALE_BRIGHT", pad=0.10))
    cubes.append(make_segment_cube(to_world((0, 1.55, +1.4)), to_world((0, 1.05, -4.5)), 0.72 * scale, 0.45 * scale, "CYAN_SPINE", pad=0.10))

    if is_crown:
        cubes.append(make_segment_cube(to_world((0, 1.65, +0.1)), to_world((0, 5.20, +0.9)), 0.92 * scale, 0.92 * scale, "CYAN_HORN", pad=0.22))
        cubes.append(make_segment_cube(to_world((0, 4.95, +0.85)), to_world((0, 6.45, +1.25)), 0.52 * scale, 0.52 * scale, "CYAN_GLOW", pad=0.12))

    cubes.append(make_segment_cube(to_world((0, -0.85, +0.9)), to_world((0, -2.10, -2.5)), 2.35 * scale, 0.95 * scale, "SCALE_DARK", pad=0.15))
    cubes.append(make_segment_cube(to_world((0, -2.00, -2.2)), to_world((0, -2.95, -4.7)), 1.75 * scale, 0.78 * scale, "SCALE_MID", pad=0.10))
    glow_w = 1.95 if is_crown else 1.58
    cubes.append(make_segment_cube(to_world((0, -0.35, -0.2)), to_world((0, -1.20, -3.5)), glow_w * scale, 1.15 * scale, "CYAN_GLOW", pad=0.06))

    for side in (-1.0, 1.0):
        cubes.append(make_segment_cube(
            to_world((side * 1.62, 0.95, -0.45)), to_world((side * 1.48, 0.85, -1.95)),
            0.56 * scale, 0.72 * scale, "HYDRA_EYE", pad=0.04
        ))
        cubes.append(make_segment_cube(
            to_world((side * 1.55, 1.48, -0.2)), to_world((side * 1.35, 1.32, -2.3)),
            0.68 * scale, 0.45 * scale, "SCALE_BRIGHT", pad=0.08
        ))
        for idx, zt in enumerate((-1.8, -2.75, -3.70, -4.65)):
            t_len = 0.72 if idx in (0, 3) else 0.56
            cubes.append(make_segment_cube(
                to_world((side * (1.10 - idx * 0.08), -0.25, zt)),
                to_world((side * (1.10 - idx * 0.08), -0.25 - t_len, zt - 0.05)),
                0.34 * scale, 0.34 * scale, "IVORY_TOOTH", pad=0.04
            ))
            frac = (idx + 1) / 4.2
            ly = -0.95 - frac * 1.72
            cubes.append(make_segment_cube(
                to_world((side * (0.94 - idx * 0.07), ly, zt + 0.25)),
                to_world((side * (0.94 - idx * 0.07), ly + 0.52, zt + 0.18)),
                0.30 * scale, 0.30 * scale, "IVORY_TOOTH", pad=0.04
            ))

        if not is_crown:
            cubes.append(make_segment_cube(
                to_world((side * 1.20, 1.45, +0.3)), to_world((side * 2.15, 3.65, +2.6)),
                0.85 * scale, 0.85 * scale, "CYAN_HORN", pad=0.22
            ))
            cubes.append(make_segment_cube(
                to_world((side * 2.15, 3.65, +2.6)), to_world((side * 2.75, 5.25, +4.4)),
                0.52 * scale, 0.52 * scale, "CYAN_GLOW", pad=0.14
            ))
        else:
            cubes.append(make_segment_cube(
                to_world((side * 1.45, 1.45, +0.4)), to_world((side * 2.65, 3.35, +2.1)),
                0.76 * scale, 0.76 * scale, "CYAN_HORN", pad=0.18
            ))

        fin_w = 4.45 if is_crown else 4.10
        fin_h = 6.65 if is_crown else 5.85
        cubes.extend(make_boss_5ray_gill_fin(
            to_world, side, (side * 1.48, 0.45, +0.95),
            fin_w, fin_h, "FIN_MINI",
            fan_sweep_back=(1.40 if is_crown else 1.85),
            fan_lift_y=(1.25 if is_crown else 0.95),
            scale=scale,
        ))
    return cubes


def build_spirit_hydra_armor_geo():
    """
    Hệ tọa độ Bedrock/GeckoLib 1.12.0:
      - Y=0: đế giày | Y=12: hông | Y=22: vai | Y=24: cổ | Y=32: đỉnh đầu
      - -Z = TRƯỚC | +Z = SAU | -X = PHẢI người chơi | +X = TRÁI người chơi
    """
    # ==================== 1. MŨ ĐẦU BOSS SPIRIT HYDRA (MÕM GÓC CẠNH + 3 SỪNG TINH THỂ TRÊN + MANG QUẠT 5 NAN 2 BÊN) ====================
    # Giữ phom mũ đội gọn kín gáy & hở sáng hoàn toàn khuôn mặt người chơi (0 hàm dưới vướng mặt, 0 z-fighting),
    # kết hợp 100% đặc trưng đầu Boss Spirit Hydra (`spirit_hydra.geo.json`):
    #   - Mõm bò sát góc cạnh 2 bậc + sống mũi nâng + 2 gờ lỗ mũi + ánh sáng xanh nhẹ vòm hàm trên
    #   - Gò má đá vuông làm ổ khớp mang + gờ mày đá vát dữ tợn + đôi mắt xiên xanh băng phát sáng FULL_BRIGHT
    #   - Hàng răng nanh trắng ngà sắc nhọn chạy dọc mép hàm trên (thoáng hoàn toàn trên mắt người chơi)
    #   - CỤM 3 SỪNG TINH THỂ XANH BĂNG PHÁT SÁNG TRÊN ĐỈNH ĐẦU: 1 sừng trán lớn 4 đốt nghiêng ngược mạnh ra sau + 2 sừng nhánh 3 đốt 2 bên!
    #   - MANG QUẠT 5 NAN BOSS (`hydra_crest_r/l`): 5 nan gai gốc sáng xanh băng -> mũi đen granite vươn dài VƯỢT hẳn mép màng quạt xanh lam răng cưa!
    head_cubes = [
        # 1a. Hộp sọ đá granite bọc kín đầu (nóc, gờ vòm, thành sau gáy kín, 2 thành bên vát lùi dưới mắt — khớp mép khít 0 z-fighting!)
        make_cube(
            [-4.72, 32.15, -4.90], [9.44, 1.05, 9.75], "SCALE_DARK",
            face_mats={"up": "HELM_TOP", "north": "SCALE_MID", "south": "SCALE_MID", "east": "SCALE_MID", "west": "SCALE_MID"}
        ),
        make_centered_cube([0.0, 33.40, +0.10], [6.60, 0.85, 8.20], "SCALE_MID"),
        make_cube([-4.72, 24.10, +4.10], [9.44, 8.05, 0.75], "SCALE_DARK", face_mats={"south": "HELM_BACK"}),
        make_cube([-4.72, 28.55, -4.90], [0.68, 3.60, 9.00], "SCALE_DARK", face_mats={"west": "HELM_SIDE", "north": "SCALE_MID"}),
        make_cube([-4.72, 24.10, -3.25], [0.68, 4.45, 7.35], "SCALE_DARK", face_mats={"west": "HELM_SIDE", "north": "SCALE_DARK"}),
        make_cube([+4.04, 28.55, -4.90], [0.68, 3.60, 9.00], "SCALE_DARK", face_mats={"east": "HELM_SIDE", "north": "SCALE_MID"}),
        make_cube([+4.04, 24.10, -3.25], [0.68, 4.45, 7.35], "SCALE_DARK", face_mats={"east": "HELM_SIDE", "north": "SCALE_DARK"}),

        # 1b. MÕM TRÊN SPIRIT HYDRA LÙI SÁT MẶT NHÂN VẬT (`z = -4.20 .. -7.65`, `y = 29.50 .. 32.40` — gọn sát, 0 che mắt!)
        make_cube(
            [-3.75, 29.55, -6.35], [7.50, 2.85, 2.15], "SCALE_MID",
            face_mats={"up": "HELM_SNOUT_TOP", "north": "HELM_SNOUT_TOP", "east": "HELM_SNOUT_SIDE", "west": "HELM_SNOUT_SIDE", "down": "CYAN_GLOW"}
        ),
        make_cube(
            [-3.15, 29.50, -7.65], [6.30, 2.45, 1.55], "SCALE_MID",
            face_mats={"north": "HELM_NOSE", "up": "HELM_SNOUT_TOP", "east": "HELM_SNOUT_SIDE", "west": "HELM_SNOUT_SIDE", "down": "CYAN_GLOW"}
        ),
        # Sống mũi giáp đá nổi góc cạnh chạy dọc giữa mõm sát trán
        make_centered_cube([0.0, 32.20, -5.85], [3.00, 0.85, 3.60], "SCALE_BRIGHT", rot=[-5.5, 0.0, 0.0]),

        # 1b-2. HÀM DƯỚI BOSS SPIRIT HYDRA ÔM SÁT KHÍT CẰM & MÁ NHÂN VẬT (`z = -4.08 .. -4.92`, `y = 23.95 .. 24.91` — 0 khoảng hở!)
        make_cube(
            [-4.35, 23.95, -4.92], [8.70, 0.96, 0.84], "SCALE_MID",
            face_mats={"up": "CYAN_GLOW", "down": "SCALE_DARK", "north": "SCALE_BRIGHT"}
        ),
        make_cube([-4.72, 23.95, -4.92], [0.68, 1.45, 1.72], "SCALE_DARK", face_mats={"west": "HELM_SIDE", "north": "SCALE_MID"}),
        make_cube([+4.04, 23.95, -4.92], [0.68, 1.45, 1.72], "SCALE_DARK", face_mats={"east": "HELM_SIDE", "north": "SCALE_MID"}),

        # 1c. Ổ ĐÁ & SỪNG TRÁN TINH THỂ XANH BĂNG CHÍNH (`horn_center`) — 4 ĐỐT PHÁT SÁNG NGHIÊNG NGƯỢC RA SAU Y HỆT BOSS!
        make_centered_cube([0.0, 32.78, -5.45], [2.45, 1.35, 2.55], "SCALE_DARK", rot=[-14.0, 0.0, 0.0]),
        make_segment_cube((0.0, 32.98, -5.35), (0.0, 34.42, -3.95), 1.60, 1.60, "CYAN_GLOW",  pad=0.35),
        make_segment_cube((0.0, 34.42, -3.95), (0.0, 35.75, -2.55), 1.26, 1.26, "CYAN_HORN",  pad=0.32),
        make_segment_cube((0.0, 35.75, -2.55), (0.0, 36.95, -1.18), 0.92, 0.92, "CYAN_HORN",  pad=0.28),
        make_segment_cube((0.0, 36.95, -1.18), (0.0, 37.98, +0.08), 0.58, 0.58, "CYAN_BLADE", pad=0.20),
    ]

    # 1d. 2 GỜ LỖ MŨI + GÒ MÁ VUÔNG + GỜ MÀY + ĐÔI MẮT XIÊN XANH BĂNG BOSS + 2 SỪNG NHÁNH + HÀM DƯỚI & 2 HÀNG RĂNG SÁT MẶT!
    for s in (-1.0, 1.0):
        outer_f = "west" if s < 0 else "east"
        # Gờ lỗ mũi giáp nổi sát đầu mõm
        head_cubes.append(make_centered_cube([s * 1.65, 31.72, -6.95], [1.10, 0.65, 1.25], "SCALE_DARK"))
        # Khối gò má đá granite vuông đặc trưng ngay sau mắt (ổ khớp nối của mang quạt 5 nan!)
        head_cubes.append(make_centered_cube([s * 4.42, 29.75, -3.15], [1.45, 2.55, 2.55], "SCALE_MID"))
        # Gờ mày đá vát xiên sắc dữ tợn che trên mắt
        head_cubes.append(make_centered_cube(
            [s * 3.95, 31.95, -5.00], [1.55, 0.90, 2.85], "SCALE_DARK",
            rot=[+10.0, s * -10.0, s * -14.0]
        ))
        # Đôi mắt xiên dài xanh băng phát sáng rực FULL_BRIGHT đặc trưng của Boss Spirit Hydra!
        head_cubes.append(make_centered_cube(
            [s * 3.90, 30.82, -5.05], [0.96, 1.18, 2.35], "SCALE_DARK",
            rot=[+11.0, s * -8.0, 0.0],
            face_mats={"north": "HYDRA_EYE", outer_f: "HYDRA_EYE", "up": "HYDRA_EYE"}
        ))
        # Mí mắt dưới tối mảnh tạo ánh nhìn xiên sắc
        head_cubes.append(make_centered_cube(
            [s * 3.94, 30.20, -4.98], [0.82, 0.40, 2.20], "SCALE_DARK",
            rot=[+10.0, s * -8.0, 0.0]
        ))
        # 2 SỪNG TINH THỂ NHÁNH PHỤ TRÊN ĐỈNH SỌ (kẹp 2 bên sau sừng trán chính, nghiêng lên-ngoài-sau giống hệt ảnh Boss 5 & 6!)
        head_cubes.append(make_segment_cube((s * 2.45, 32.65, -2.55), (s * 3.05, 34.25, -0.90), 1.00, 1.00, "CYAN_GLOW",  pad=0.28))
        head_cubes.append(make_segment_cube((s * 3.05, 34.25, -0.90), (s * 3.65, 35.75, +0.75), 0.72, 0.72, "CYAN_HORN",  pad=0.24))
        head_cubes.append(make_segment_cube((s * 3.65, 35.75, +0.75), (s * 4.20, 37.10, +2.30), 0.44, 0.44, "CYAN_BLADE", pad=0.18))
        # Hàng răng nanh trắng ngà HÀM TRÊN (kéo cao trên tầm mắt `y >= 28.6`, lùi sát mặt!)
        head_cubes.append(make_centered_cube([s * 1.15, 29.08, -7.30], [0.54, 0.88, 0.54], "FANG_IVORY"))
        for rx, ry, rz, rh in (
            (2.65, 29.05, -6.45, 0.94),
            (3.45, 29.10, -5.25, 0.92),
        ):
            head_cubes.append(make_centered_cube([s * rx, ry, rz], [0.56, rh, 0.56], "FANG_IVORY"))
        # Khớp vát má nối liền hàm dưới ôm sát cạnh mặt + 3 cặp răng nanh trắng ngà sát mặt cằm (`z = -4.55`, 0 hở!)
        head_cubes.append(make_centered_cube([s * 4.38, 25.55, -3.95], [0.68, 0.96, 1.30], "SCALE_DARK", rot=[+22.0, 0.0, 0.0]))
        head_cubes.append(make_centered_cube([s * 1.05, 25.20, -4.55], [0.46, 0.64, 0.42], "FANG_IVORY"))
        head_cubes.append(make_centered_cube([s * 2.35, 25.22, -4.55], [0.48, 0.68, 0.42], "FANG_IVORY"))
        head_cubes.append(make_centered_cube([s * 3.65, 25.32, -4.45], [0.54, 0.86, 0.48], "FANG_IVORY"))
        # Vảy rồng 3D nổi chồng lớp dọc sau gáy & thái dương mũ (xóa bề mặt phẳng trơn, 0 z-fighting!)
        head_cubes.append(make_centered_cube([s * 2.55, 30.85, +4.96], [2.45, 1.85, 0.34], "HYDRA_SCALE_3D", rot=[+11.0, s * +5.0, 0.0]))
        head_cubes.append(make_centered_cube([s * 2.75, 28.65, +4.96], [2.55, 1.85, 0.34], "HYDRA_SCALE_3D", rot=[+11.0, s * +5.0, 0.0]))
        head_cubes.append(make_centered_cube([s * 2.45, 26.40, +4.96], [2.45, 1.85, 0.34], "HYDRA_SCALE_3D", rot=[+11.0, s * +5.0, 0.0]))
        head_cubes.append(make_centered_cube([s * 4.80, 27.60, +1.55], [0.30, 1.85, 2.35], "HYDRA_SCALE_3D", rot=[0.0, 0.0, s * -10.0]))
        head_cubes.append(make_centered_cube([s * 4.80, 25.60, +1.85], [0.30, 1.75, 2.25], "HYDRA_SCALE_3D", rot=[0.0, 0.0, s * -10.0]))
    # 4 tấm vảy sống gáy chính giữa mũ (xếp tầng dọc mặt sau)
    for hy in (31.35, 29.25, 27.15, 25.15):
        head_cubes.append(make_centered_cube([0.0, hy, +5.00], [2.15, 1.65, 0.36], "HYDRA_SCALE_3D", rot=[+12.0, 0.0, 0.0]))

    # 1e. MANG QUẠT 5 NAN ĐẶC TRƯNG BOSS SPIRIT HYDRA 2 BÊN MŨ (`hydra_crest_r/l` gắn trục ngay ổ gò má `[±4.55, 29.85, -1.85]`)!
    # 5 nan gai vươn dài vượt hẳn mép màng quạt ~2.2 px (gốc sáng xanh băng -> thân/mũi đen granite sắc lạnh) + màng quạt xanh lam răng cưa!
    def build_main_head_crest(s: float):
        piv = np.array([s * 4.55, 29.85, -1.85], dtype=np.float64)
        sweep_back = 0.42  # Nghiêng sau ~24° giống hệt mang quạt Boss Spirit Hydra (ảnh 6, 7, 8)!
        ray_angles_deg = [+68.0, +34.0, 0.0, -34.0, -64.0]
        ray_lengths    = [7.80,  8.65,  8.35, 7.35,  5.90]
        cc = []
        for ang_d, rlen in zip(ray_angles_deg, ray_lengths):
            rad = math.radians(ang_d)
            u = np.array([s * math.cos(rad) * math.cos(sweep_back), math.sin(rad), math.cos(rad) * math.sin(sweep_back)], dtype=np.float64)
            u /= np.linalg.norm(u)
            p_a = piv + u * 0.15
            p_b = piv + u * (rlen * 0.30)  # Gốc nan sáng xanh băng tỏa từ ổ mang (ảnh 7 & 8!)
            p_c = piv + u * (rlen * 0.64)  # Thân nan đá xám tối
            p_d = piv + u * rlen           # Mũi nan gai đen granite nhô VƯỢT mép màng quạt ~2.2 px!
            cc.append(make_segment_cube(tuple(p_a), tuple(p_b), 0.56, 0.52, "CYAN_GLOW",  pad=0.16))
            cc.append(make_segment_cube(tuple(p_b), tuple(p_c), 0.42, 0.40, "SCALE_MID",  pad=0.16))
            cc.append(make_segment_cube(tuple(p_c), tuple(p_d), 0.30, 0.28, "SCALE_DARK", pad=0.14))

        # Căng màng quạt xanh băng răng cưa alpha-cutout (`FIN_LARGE_R/L`) nằm KHÍT trong mặt phẳng 5 nan quạt (mép màng dừng trước mũi nan!)
        out_hat = np.array([s * math.cos(sweep_back), 0.0, math.sin(sweep_back)], dtype=np.float64)
        up_hat  = np.array([0.0, 1.0, 0.0], dtype=np.float64)
        ex = out_hat if s > 0 else -out_hat
        ey = up_hat
        ez = np.cross(ex, ey)
        ez /= max(1e-5, float(np.linalg.norm(ez)))
        M = np.column_stack([ex, ey, ez])
        if np.linalg.det(M) < 0:
            ez = -ez
            M = np.column_stack([ex, ey, ez])
        ry_rad = math.asin(max(-1.0, min(1.0, -M[2, 0])))
        rx_rad = -math.atan2(M[2, 1], M[2, 2]) if abs(math.cos(ry_rad)) > 1e-4 else 0.0
        rz_rad = -math.atan2(M[1, 0], M[0, 0]) if abs(math.cos(ry_rad)) > 1e-4 else -math.atan2(-M[0, 1], M[1, 1])
        rot_deg = [math.degrees(rx_rad), math.degrees(ry_rad), math.degrees(rz_rad)]

        mem_w = 6.35
        mem_h = 10.85
        f_tag = "R" if s < 0 else "L"
        b_tag = "L" if s < 0 else "R"
        fin_center = piv + out_hat * (mem_w * 0.50) + np.array([0.0, 0.20, 0.0])
        cc.append(make_fin_plane(
            tuple(fin_center), mem_w, mem_h,
            f"FIN_LARGE_{f_tag}", f"FIN_LARGE_{b_tag}",
            rot=rot_deg,
        ))
        return cc

    hydra_crest_r = build_main_head_crest(-1.0)
    hydra_crest_l = build_main_head_crest(+1.0)

    # ==================== 2. GIÁP NGỰC MAI RỒNG HỮU CƠ TỰ NHIÊN + 6 MÓNG SỪNG KẸP TIM + VẢY XẾP CHỒNG 3D (`armorBody`) ====================
    # Phong cách giáp rồng hữu cơ tự nhiên 100% (tương đồng Cinder Dragon trên chất liệu granite Boss Spirit Hydra):
    #   - Bỏ hoàn toàn sắt thép/đinh tán/khung vuông cơ khí! Dùng mảng mai đá rồng phân tầng + hàng vảy rồng 3D nổi chồng lớp so le!
    #   - 3 cặp móng sừng rồng 3D uốn cong mọc tự nhiên từ sườn ngực kẹp ôm lõi tim đập `hydra_heart_core`!
    #   - Đai eo mai xương rồng tự nhiên khảm mảnh tinh thể xanh băng + mạch băng xanh thẫm cực mảnh (`HYDRA_RIM`)!
    horn_claw_faces = {
        "north": "HYDRA_CLAW_TIP", "south": "HYDRA_RIM",
        "up": "HYDRA_CLAW_TIP", "down": "SCALE_DARK",
        "east": "HYDRA_CLAW_TIP", "west": "HYDRA_CLAW_TIP",
    }
    body_cubes = [
        # Lõi thân mai rồng bọc kín (mặt trước rãnh năng lượng Spirit Hydra phát sáng `CHEST_FRONT`, sườn da rồng granite!)
        make_centered_cube(
            [0.0, 18.10, 0.0], [9.10, 12.10, 5.20], "SCALE_MID",
            face_mats={
                "north": "CHEST_FRONT",
                "south": "CHEST_BACK",
                "east":  "CHEST_SIDE",
                "west":  "CHEST_SIDE",
                "up":    "HIDE_DARK",
                "down":  "HIDE_DARK",
            }
        ),
        # Vòng cổ mai rồng đá tối ôm sát chân cổ (không còn cổ thép vuông!)
        make_centered_cube([0.0, 23.90, +0.25], [9.35, 1.05, 5.15], "SCALE_DARK",
                           face_mats={"up": "SCALE_SHINGLE", "north": "HYDRA_RIM"}),
        # Đai eo mai xương rồng tối dày (`9.62 x 1.24 x 5.56`) + ổ tinh thể băng tự nhiên giữa eo + viền gân băng `HYDRA_RIM`!
        make_centered_cube([0.0, 12.38, 0.0], [9.62, 1.24, 5.56], "SCALE_DARK",
                           face_mats={"up": "HIDE_DARK", "down": "HYDRA_RIM", "north": "SCALE_RING"}),
        make_centered_cube([0.0, 12.44, -2.86], [2.00, 1.08, 0.34], "SCALE_DARK",
                           face_mats={"north": "WAIST_CRYSTAL"}),
        # Viền gân mai hắc thạch ánh băng mảnh chạy ôm đáy hốc tim & vòng dưới đai eo (chừa gọn gốc đuôi sau!)
        make_centered_cube([0.0, 13.42, -2.74], [8.80, 0.50, 0.36], "HYDRA_RIM"),
        make_centered_cube([-3.05, 11.62, -2.82], [3.55, 0.36, 0.38], "HYDRA_RIM"),
        make_centered_cube([+3.05, 11.62, -2.82], [3.55, 0.36, 0.38], "HYDRA_RIM"),
        make_centered_cube([-4.82, 11.62,  0.00], [0.36, 0.36, 5.66], "HYDRA_RIM"),
        make_centered_cube([+4.82, 11.62,  0.00], [0.36, 0.36, 5.66], "HYDRA_RIM"),
        make_centered_cube([-3.15, 11.62, +2.82], [3.20, 0.36, 0.38], "HYDRA_RIM"),
        make_centered_cube([+3.15, 11.62, +2.82], [3.20, 0.36, 0.38], "HYDRA_RIM"),

        # Khe pha lê sống lưng phát sáng xanh băng (gắn khít mặt lưng `z = +2.78`, ăn khớp 11 gai sống lưng!)
        make_centered_cube([0.0, 17.65, +2.78], [1.45, 7.90, 0.44], "CYAN_SPINE"),
    ]
    for s in (-1.0, 1.0):
        # --- 1. Mảng mai sườn đá granite phân 3 tầng ôm tự nhiên quanh hốc tim phát sáng (0 khung sắt thép!) ---
        body_cubes.append(make_centered_cube([s * 3.05, 21.85, -2.72], [3.05, 4.10, 0.32], "SCALE_SHINGLE"))
        body_cubes.append(make_centered_cube([s * 1.48, 21.75, -2.76], [0.44, 4.15, 0.36], "HYDRA_RIM"))
        body_cubes.append(make_centered_cube([s * 2.90, 19.52, -2.78], [3.35, 0.52, 0.38], "HYDRA_RIM"))
        body_cubes.append(make_centered_cube([s * 3.65, 17.95, -2.72], [1.85, 2.65, 0.32], "SCALE_SHINGLE"))
        body_cubes.append(make_centered_cube([s * 3.45, 16.58, -2.78], [2.25, 0.50, 0.38], "HYDRA_RIM"))
        body_cubes.append(make_centered_cube([s * 3.80, 14.95, -2.72], [1.55, 2.75, 0.32], "SCALE_SHINGLE"))

        # --- 2. 3 CẶP MÓNG SỪNG RỒNG 3D HỮU CƠ UỐN CONG KẸP ÔM LÕI TIM ĐẬP (`hydra_heart_core`)! ---
        claw_tiers = [
            ((s * 2.45, 21.05, -2.58), (s * 1.92, 20.88, -3.28), (s * 0.92, 20.62, -3.58), 0.94, 0.80),
            ((s * 3.05, 18.50, -2.58), (s * 2.48, 18.48, -3.34), (s * 1.38, 18.45, -3.62), 0.98, 0.84),
            ((s * 3.25, 15.65, -2.58), (s * 2.68, 15.78, -3.30), (s * 1.32, 15.98, -3.58), 0.96, 0.82),
        ]
        for p_root, p_knuckle, p_tip, w_root, w_tip in claw_tiers:
            body_cubes.append(make_segment_cube(p_root, p_knuckle, w_root, w_root, "HELM_HORN_BASE", pad=0.24))
            body_cubes.append(make_segment_cube(p_knuckle, p_tip, w_tip, w_tip, "HYDRA_CLAW_TIP", pad=0.20, face_mats=horn_claw_faces))

        # --- 3. HÀNG VẢY RỒNG 3D XẾP CHỒNG SO LE TỰ NHIÊN (nghiêng vát `-11°` xuống ngoài, 0 dáng gối bông!) ---
        # Mặt trước ngực (so le 2 bên hốc tim, tránh sạch 6 móng vuốt & lõi tim đập)
        body_cubes.append(make_centered_cube([s * 2.40, 22.90, -2.96], [1.38, 1.34, 0.44], "SCALE_SHINGLE", rot=[-11.0, s * -6.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.78, 22.58, -2.98], [1.38, 1.38, 0.46], "SCALE_SHINGLE", rot=[-11.0, s * -10.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.74, 20.62, -2.96], [1.32, 1.38, 0.44], "SCALE_SHINGLE", rot=[-11.0, s * -8.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.88, 18.00, -2.96], [1.18, 1.40, 0.42], "SCALE_SHINGLE", rot=[-11.0, s * -8.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.96, 15.05, -2.96], [1.12, 1.36, 0.42], "SCALE_SHINGLE", rot=[-11.0, s * -8.0, 0.0]))
        # Vảy rồng nổi tự nhiên xếp tầng cạnh ổ tinh thể đai eo
        body_cubes.append(make_centered_cube([s * 2.22, 12.36, -2.88], [1.62, 1.06, 0.36], "SCALE_SHINGLE", rot=[-9.0, 0.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.92, 12.32, -2.86], [1.40, 1.06, 0.36], "SCALE_SHINGLE", rot=[-9.0, s * -10.0, 0.0]))
        # Vảy sườn ngực xếp bậc tự nhiên (gọn sát, không xuyên tay Dual Knife!)
        for sy in (20.45, 17.85, 15.25):
            body_cubes.append(make_centered_cube([s * 4.68, sy, 0.0], [0.38, 1.68, 2.70], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -9.0]))
        # 3 tầng vảy rồng lớn xếp chồng tự nhiên sau lưng ôm 2 bên cột sống & 11 gai lưng!
        for sy, sw in ((21.55, 2.15), (18.65, 2.25), (15.75, 2.05)):
            body_cubes.append(make_centered_cube([s * 3.30, sy, +2.78], [sw, 1.95, 0.50], "SCALE_SHINGLE", rot=[+12.0, s * +6.0, 0.0]))

    # BONE RIÊNG LÕI TIM TINH THỂ GIỮA NGỰC (`hydra_heart_core`, pivot `[0.0, 18.50, -3.04]`) CHO ANIMATION NHỊP TIM ĐẬP LUB-DUB!
    heart_core_cubes = [
        make_centered_cube([0.0, 18.30, -2.92], [0.95, 7.80, 0.24], "CYAN_HORN"),
        make_centered_cube([0.0, 18.50, -3.02], [1.90, 3.65, 0.26], "CYAN_HORN"),
        make_centered_cube([0.0, 18.50, -3.12], [2.10, 2.10, 0.24], "CYAN_HORN", rot=[0.0, 0.0, 45.0]),
        # Nhân tim pha lê trắng-xanh trung tâm nhô nổi 3D đập phồng theo từng nhịp lub-dub giữa 3 cặp xương sườn!
        make_centered_cube([0.0, 18.50, -3.24], [1.25, 1.25, 0.28], "CYAN_GLOW", rot=[0.0, 0.0, 45.0]),
    ]

    # BONE RIÊNG CỤM 11 GAI RỒNG 3D SAU LƯNG (`hydra_back_spines`, pivot `[0.0, 16.50, +3.05]`) — SẮC NHỌN, PHẬP PHỒNG NHẸ THEO NHỊP TIM!
    # 3 gai sống lưng trung tâm dọc khe sáng (`y=18.75, 16.45, 14.30`) + 4 cặp gai sườn lưng đối xứng (`s=±1`), tách sạch 3 cổ & đuôi!
    back_spines_cubes = [
        make_segment_cube((0.0, 18.75, +3.05), (0.0, 19.65, +4.95), 1.24, 1.18, "HELM_HORN_BASE", pad=0.30),
        make_segment_cube((0.0, 19.65, +4.95), (0.0, 20.25, +6.30), 0.58, 0.56, "CYAN_BLADE",     pad=0.20),
        make_segment_cube((0.0, 16.45, +3.05), (0.0, 17.05, +5.25), 1.32, 1.24, "HELM_HORN_BASE", pad=0.30),
        make_segment_cube((0.0, 17.05, +5.25), (0.0, 17.48, +6.85), 0.62, 0.58, "CYAN_BLADE",     pad=0.20),
        make_segment_cube((0.0, 14.30, +3.05), (0.0, 14.65, +4.85), 1.14, 1.08, "HELM_HORN_MID",  pad=0.26),
        make_segment_cube((0.0, 14.65, +4.85), (0.0, 14.92, +6.10), 0.52, 0.50, "CYAN_HORN",      pad=0.18),
    ]
    for s in (-1.0, 1.0):
        back_spines_cubes.extend([
            make_segment_cube((s * 3.15, 19.35, +3.05), (s * 4.20, 20.15, +4.55), 0.96, 0.90, "HELM_HORN_BASE", pad=0.24),
            make_segment_cube((s * 4.20, 20.15, +4.55), (s * 4.95, 20.75, +5.65), 0.46, 0.44, "CYAN_HORN",      pad=0.16),
            make_segment_cube((s * 3.30, 17.45, +3.05), (s * 4.55, 17.95, +4.70), 0.98, 0.90, "HELM_HORN_BASE", pad=0.24),
            make_segment_cube((s * 4.55, 17.95, +4.70), (s * 5.38, 18.30, +5.88), 0.48, 0.44, "CYAN_BLADE",     pad=0.16),
            make_segment_cube((s * 3.15, 15.60, +3.05), (s * 4.25, 15.90, +4.55), 0.88, 0.84, "HELM_HORN_MID",  pad=0.22),
            make_segment_cube((s * 4.25, 15.90, +4.55), (s * 4.98, 16.15, +5.58), 0.44, 0.42, "CYAN_HORN",      pad=0.16),
            make_segment_cube((s * 2.85, 13.85, +3.05), (s * 3.85, 14.05, +4.35), 0.80, 0.76, "HELM_HORN_DARK", pad=0.20),
            make_segment_cube((s * 3.85, 14.05, +4.35), (s * 4.48, 14.20, +5.20), 0.40, 0.38, "CYAN_HORN",      pad=0.14),
        ])

    # ==================== 2b. 3 ĐẦU HỒN XÀ PHỤ & ĐUÔI 6 ĐỐT (GIỮ NGUYÊN 100%, BÁM KHÍT MẶT LƯNG MỚI!) ====================
    def build_side_hydra_neck_and_head(s: float):
        p0 = (s * 2.55, 20.30, +3.10)
        p1 = (s * 6.35, 23.45, +4.95)
        p2 = (s * 9.60, 27.15, +4.45)
        p3 = (s * 12.70, 28.70, +2.35)
        p4 = (s * 14.95, 27.80, -0.20)

        n1 = [
            make_segment_cube(p0, p1, 2.85, 2.75, "NECK_RING", pad=0.90),
            make_segment_cube((p0[0], p0[1]-1.05, p0[2]+0.85), (p1[0], p1[1]-1.05, p1[2]+0.85), 1.15, 0.90, "CYAN_GLOW",  pad=0.50),
            make_segment_cube((p0[0], p0[1]+1.15, p0[2]+0.45), (p1[0], p1[1]+1.15, p1[2]+0.45), 0.95, 0.72, "CYAN_SPINE", pad=0.45),
            make_segment_cube(p1, p2, 2.60, 2.52, "NECK_RING", pad=0.90),
            make_segment_cube((p1[0], p1[1]-0.98, p1[2]+0.80), (p2[0], p2[1]-0.98, p2[2]+0.80), 1.08, 0.85, "CYAN_GLOW",  pad=0.50),
            make_segment_cube((p1[0], p1[1]+1.08, p1[2]+0.40), (p2[0], p2[1]+1.08, p2[2]+0.40), 0.88, 0.68, "CYAN_SPINE", pad=0.45),
        ]
        n2 = [
            make_segment_cube((p2[0] - s*0.55, p2[1] - 0.35, p2[2] + 0.15), p3, 2.38, 2.32, "NECK_RING", pad=0.90),
            make_segment_cube((p2[0], p2[1]-0.90, p2[2]+0.75), (p3[0], p3[1]-0.90, p3[2]+0.75), 1.00, 0.78, "CYAN_GLOW",  pad=0.48),
            make_segment_cube((p2[0], p2[1]+1.00, p2[2]+0.35), (p3[0], p3[1]+1.00, p3[2]+0.35), 0.82, 0.64, "CYAN_SPINE", pad=0.45),
            make_segment_cube(p3, p4, 2.15, 2.10, "NECK_RING", pad=0.85),
            make_segment_cube((p3[0], p3[1]+0.92, p3[2]+0.30), (p4[0], p4[1]+0.92, p4[2]+0.30), 0.78, 0.60, "CYAN_SPINE", pad=0.40),
        ]
        hd = build_boss_matched_aux_head((s * 15.20, 27.45, -0.95), yaw_deg=s * 58.0, pitch_deg=14.0, scale=1.04, is_crown=False)
        return n1, n2, hd

    neck_r1, neck_r2, head_r = build_side_hydra_neck_and_head(-1.0)
    neck_l1, neck_l2, head_l = build_side_hydra_neck_and_head(+1.0)

    c0  = (0.0, 20.80, +2.85)
    c0b = (0.0, 24.10, +6.55)
    c1  = (0.0, 28.20, +9.05)
    c2  = (0.0, 32.75, +9.85)
    c2b = (0.0, 36.85, +8.65)
    c3  = (0.0, 39.85, +5.75)
    c4  = (0.0, 40.95, +2.00)

    def add_c_seg(lst, pa, pb, w, h, pad=0.85):
        v = np.array(pb, float) - np.array(pa, float)
        u = v / np.linalg.norm(v)
        out_n = np.array([0.0, -u[2], u[1]], float)
        out_n /= max(1e-4, np.linalg.norm(out_n))
        lst.append(make_segment_cube(pa, pb, w, h, "NECK_RING", pad=pad))
        lst.append(make_segment_cube(tuple(np.array(pa) + out_n * (h * 0.50)),
                                     tuple(np.array(pb) + out_n * (h * 0.50)), w * 0.44, h * 0.34, "CYAN_SPINE", pad=0.50))
        lst.append(make_segment_cube(tuple(np.array(pa) - out_n * (h * 0.46)),
                                     tuple(np.array(pb) - out_n * (h * 0.46)), w * 0.44, h * 0.30, "CYAN_GLOW", pad=0.45))

    neck_c1 = []
    add_c_seg(neck_c1, c0,  c0b, 3.20, 2.95)
    add_c_seg(neck_c1, c0b, c1,  3.02, 2.80)
    add_c_seg(neck_c1, c1,  c2,  2.85, 2.65)

    neck_c2 = []
    add_c_seg(neck_c2, (0.0, c2[1] - 0.55, c2[2] + 0.08), c2b, 2.70, 2.52)
    add_c_seg(neck_c2, c2b, c3,  2.52, 2.38)
    add_c_seg(neck_c2, c3,  c4,  2.35, 2.22, pad=0.75)

    head_c = build_boss_matched_aux_head((0.0, 40.75, +1.10), yaw_deg=0.0, pitch_deg=5.0, scale=1.08, is_crown=True)

    tp = [
        (  0.00, 12.25,  +2.65),
        ( -1.75,  8.45,  +6.60),
        ( -4.55,  5.65, +10.55),
        ( -7.55,  5.45, +13.45),
        ( -9.85,  8.05, +14.85),
        (-10.65, 12.40, +14.75),
        (-10.55, 17.05, +13.50),
    ]

    def build_tail_seg(i: int, w: float, h: float, overlap_back: float = 0.92, is_tip: bool = False):
        p_a, p_b = np.array(tp[i], float), np.array(tp[i + 1], float)
        d = p_b - p_a
        u = d / np.linalg.norm(d)
        p_start = p_a - u * overlap_back
        # Thân đốt đuôi đá granite lốm đốm 1:1 giống Boss Spirit Hydra (xóa hẳn sọc ống xếp `TAIL_RING`!) + khớp nối tối tự nhiên
        segs = [
            make_segment_cube(p_start, p_b, w, h, "SCALE_MID", pad=0.38),
            make_segment_cube(p_a - u * 0.35, p_a + u * 0.75, w * 0.92, h * 0.90, "SCALE_DARK", pad=0.22),
        ]
        ref_down = np.array([-0.32, -0.82, +0.48], float)
        out_n = ref_down - np.dot(ref_down, u) * u
        nl = np.linalg.norm(out_n)
        out_n = (out_n / nl) if nl > 1e-4 else np.array([0.0, 0.0, 1.0], float)
        # Dải bụng năng lượng xanh băng liên tục dọc mặt dưới đuôi (chuẩn Boss Spirit Hydra!)
        segs.append(make_segment_cube(p_a + out_n * (h * 0.46), p_b + out_n * (h * 0.46), w * 0.62, h * 0.36, "CYAN_GLOW", pad=0.42))

        # Gai sống lưng nhỏ 2 đốt trên mỗi đốt đuôi (gốc đá tối -> chóp xanh điện giống hệt ảnh Boss `tail_5..tail_7`!)
        dorsal_n = -out_n
        sp_root = p_a + d * 0.56 + dorsal_n * (h * 0.44)
        sp_dir = dorsal_n * 0.86 - u * 0.48
        sp_dir /= max(1e-4, float(np.linalg.norm(sp_dir)))
        sp_w = max(0.38, 0.62 - i * 0.04)
        sp_l1 = max(0.62, 0.96 - i * 0.06)
        sp_l2 = max(0.46, 0.72 - i * 0.05)
        sp_mid = sp_root + sp_dir * sp_l1
        sp_tip = sp_mid + sp_dir * sp_l2
        segs.append(make_segment_cube(sp_root, sp_mid, sp_w, sp_w, "HELM_HORN_BASE", pad=0.14))
        segs.append(make_segment_cube(sp_mid, sp_tip, sp_w * 0.58, sp_w * 0.58, "CYAN_HORN", pad=0.10))

        if is_tip:
            # CỔ ĐÁ GRANITE LOE RỘNG CHÓP ĐUÔI + CỤM ĐUÔI CHẺ CHỮ V 2 SỪNG TINH THỂ 5 ĐỐT + 1 GAI GIỮA (1:1 BOSS SPIRIT HYDRA!)
            p_collar_a = p_b - u * 0.30
            p_collar_b = p_b + u * 1.95
            segs.append(make_segment_cube(p_collar_a, p_collar_b, 2.56, 2.34, "SCALE_MID", pad=0.22))
            segs.append(make_segment_cube(p_collar_a - u * 0.15, p_collar_a + u * 0.65, 2.38, 2.18, "SCALE_DARK", pad=0.15))

            side_v = np.cross(u, out_n)
            sv_len = float(np.linalg.norm(side_v))
            side_v = (side_v / sv_len) if sv_len > 1e-4 else np.array([1.0, 0.0, 0.0], float)

            p_base = p_collar_b - u * 0.18
            # 2 SỪNG TINH THỂ CHẺ CHỮ V ĐỐI XỨNG 5 ĐỐT THUÔN NHỌN (xanh băng <-> trắng băng chuyển lớp giống ảnh Boss!)
            fork_pts = [
                (0.00, 0.22),
                (1.30, 0.96),
                (2.75, 1.62),
                (4.20, 2.08),
                (5.55, 2.48),
                (6.85, 2.86),
            ]
            fork_spec = [
                (1.00, "CYAN_GLOW"),
                (0.86, "CYAN_HORN"),
                (0.68, "CYAN_GLOW"),
                (0.48, "CYAN_HORN"),
                (0.28, "CYAN_BLADE"),
            ]
            for side in (-1.0, 1.0):
                pts = [p_base + u * du + side_v * (side * ds) for du, ds in fork_pts]
                for idx, (fw, fmat) in enumerate(fork_spec):
                    segs.append(make_segment_cube(pts[idx], pts[idx + 1], fw, fw, fmat, pad=0.18))

            # 1 GAI TINH THỂ TRUNG TÂM MẢNH ĐỨNG GIỮA CHỮ V (~50% chiều cao 2 sừng bên, giống hệt ảnh Boss!)
            m0 = p_base - u * 0.10
            m1 = p_base + u * 1.80
            m2 = p_base + u * 3.45
            segs.append(make_segment_cube(m0, m1, 0.58, 0.58, "CYAN_HORN", pad=0.16))
            segs.append(make_segment_cube(m1, m2, 0.34, 0.34, "CYAN_GLOW", pad=0.12))
        return segs

    tail_1 = build_tail_seg(0, 4.10, 3.65, overlap_back=0.85)
    tail_2 = build_tail_seg(1, 3.62, 3.22, overlap_back=0.92)
    tail_3 = build_tail_seg(2, 3.16, 2.82, overlap_back=0.92)
    tail_4 = build_tail_seg(3, 2.72, 2.42, overlap_back=0.92)
    tail_5 = build_tail_seg(4, 2.28, 2.05, overlap_back=0.92)
    tail_6 = build_tail_seg(5, 1.85, 1.68, overlap_back=0.92, is_tip=True)

    # ==================== 3. VAI & CÁNH TAY VẢY RỒNG 3D XẾP CHỒNG TỰ NHIÊN + SỪNG VAI + GĂNG 4 MÓNG VUỐT (`armorRightArm/LeftArm`) ====================
    # Bỏ hoàn toàn giáp sắt thép, đinh tán & khe đèn ngang! Thay bằng tầng vảy đá granite rồng chồng lớp tự nhiên như da rồng thật!
    def build_hydra_arm(s: float):
        cx = s * 6.0
        outer_f = "west" if s < 0 else "east"
        inner_f = "east" if s < 0 else "west"
        cubes = [
            # Lớp da mai rồng tối bọc kín 100% cả cánh tay VÀ toàn bộ 5 mặt bàn tay xuống tới `y = 11.66` (0 hở da ở lòng bàn tay & cổ tay!)
            make_centered_cube([cx, 17.93, 0.0], [4.78, 12.54, 4.78], "HIDE_DARK",
                               face_mats={"up": "SCALE_DARK", "down": "BOOT_CARAPACE", outer_f: "SCALE_DARK", inner_f: "HIDE_DARK"}),

            # --- A. MAI VAI RỒNG HỮU CƠ VÁT GÓC & HÀNG VẢY XẾP CHỒNG TỰ NHIÊN (bậc rộng kiểu Netherite, 0 kim loại!) ---
            make_centered_cube(
                [cx + s * 0.42, 22.35, 0.0], [5.72, 3.40, 5.68], "PAULDRON_PLATE",
                rot=[0.0, 0.0, s * -7.0],
                face_mats={"up": "SCALE_SHINGLE", "down": "HYDRA_RIM", outer_f: "SCALE_SHINGLE"}
            ),
            # Mào vảy đá đỉnh vai vát nghiêng tự nhiên (`-17°`)
            make_centered_cube(
                [cx + s * 1.18, 23.95, 0.0], [4.55, 1.20, 5.25], "SCALE_SHINGLE",
                rot=[0.0, 0.0, s * -17.0],
                face_mats={"up": "SCALE_SHINGLE", outer_f: "HYDRA_RIM"}
            ),
            # Hàng vảy rồng 3D nổi xòe nhẹ chồng lớp dưới bờ vai (`-13°`, 0 thanh đèn sáng!)
            make_centered_cube([cx + s * 3.00, 20.65, -1.25], [0.54, 1.58, 1.88], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx + s * 3.00, 20.65, +1.25], [0.54, 1.58, 1.88], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx + s * 0.25, 20.55, -2.74], [4.10, 1.44, 0.46], "SCALE_SHINGLE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 0.25, 20.55, +2.74], [4.10, 1.44, 0.46], "SCALE_SHINGLE", rot=[+11.0, 0.0, 0.0]),

            # SỪNG VAI RỒNG HỮU CƠ 2 TẦNG MỌC TỪ ĐỈNH MAI VAI (gốc đá granite sần -> mũi tinh thể băng xanh, tách sạch 3 cổ phụ!)
            make_segment_cube((cx + s * 2.15, 23.55, -0.55), (cx + s * 3.90, 24.55, -0.25), 1.65, 1.58, "HELM_HORN_BASE", pad=0.45),
            make_segment_cube((cx + s * 3.90, 24.55, -0.25), (cx + s * 5.20, 26.00, +0.05), 1.12, 1.08, "HELM_HORN_MID",  pad=0.38),
            make_segment_cube((cx + s * 5.20, 26.00, +0.05), (cx + s * 5.75, 27.45, +0.30), 0.62, 0.58, "CYAN_BLADE",     pad=0.22),
            # Gai sừng phụ ngắn mọc chếch dưới gốc sừng chính
            make_segment_cube((cx + s * 2.65, 21.60, +0.25), (cx + s * 4.25, 22.25, +0.55), 1.12, 1.06, "HELM_HORN_BASE", pad=0.34),
            make_segment_cube((cx + s * 4.25, 22.25, +0.55), (cx + s * 5.15, 23.05, +0.85), 0.58, 0.54, "CYAN_HORN",      pad=0.20),

            # --- B. CÁC TẦNG VẢY RỒNG 3D XẾP CHỒNG SO LE DỌC BẮP TAY & CẲNG TAY (0 sọc đồ ngủ, 0 vỏ hộp sắt!) ---
            # Tầng vảy bắp tay
            make_centered_cube([cx + s * 0.08, 19.05, 0.0], [5.16, 1.38, 5.14], "SCALE_RING",
                               rot=[0.0, 0.0, s * -5.0], face_mats={"up": "SCALE_SHINGLE", "down": "HYDRA_RIM"}),
            make_centered_cube([cx + s * 2.74, 18.80,  0.0], [0.50, 1.52, 2.25], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -13.0]),
            make_centered_cube([cx,            18.75, -2.58], [3.45, 1.32, 0.44], "SCALE_SHINGLE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx,            18.75, +2.58], [3.45, 1.32, 0.44], "SCALE_SHINGLE", rot=[+11.0, 0.0, 0.0]),
            # Tầng vảy cẳng tay (so le với bắp tay!)
            make_centered_cube([cx + s * 0.08, 17.15, 0.0], [5.12, 1.36, 5.10], "SCALE_RING",
                               rot=[0.0, 0.0, s * -5.0], face_mats={"up": "SCALE_SHINGLE", "down": "HYDRA_RIM"}),
            make_centered_cube([cx + s * 2.72, 16.95, -1.10], [0.48, 1.46, 1.75], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -13.0]),
            make_centered_cube([cx + s * 2.72, 16.95, +1.10], [0.48, 1.46, 1.75], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -13.0]),
            # Ống vảy cổ tay tự nhiên (`y = 13.6..16.2`)
            make_centered_cube([cx, 14.90, 0.0], [5.24, 2.55, 5.24], "SCALE_RING",
                               face_mats={"up": "HYDRA_RIM", "down": "SCALE_DARK", outer_f: "SCALE_SHINGLE"}),

            # 2 CỰA SỪNG KHUỶU TAY HỮU CƠ CONG SẮC (mọc tự nhiên từ sống cẳng tay ngoài-sau, chĩa lên-sau!)
            make_segment_cube((cx + s * 2.55, 18.45, +0.95), (cx + s * 3.55, 19.30, +1.40), 0.78, 0.74, "HELM_HORN_BASE", pad=0.28),
            make_segment_cube((cx + s * 3.55, 19.30, +1.40), (cx + s * 4.05, 20.15, +1.65), 0.44, 0.42, "HELM_HORN_TIP",  pad=0.18),
            make_segment_cube((cx + s * 2.55, 16.55, +1.05), (cx + s * 3.45, 17.25, +1.45), 0.70, 0.66, "HELM_HORN_BASE", pad=0.24),
            make_segment_cube((cx + s * 3.45, 17.25, +1.45), (cx + s * 3.85, 17.95, +1.65), 0.40, 0.38, "HELM_HORN_TIP",  pad=0.16),

            # --- C. GĂNG VẢY RỒNG BỌC TRỌN 100% CẢ BÀN TAY (TRƯỚC, SAU, NGOÀI, TRONG & LÒNG BÀN TAY) + 3 MÓNG VUỐT HÌNH HỘP ÔM KHÍT ---
            make_centered_cube([cx + s * 2.34, 12.92,  0.00], [0.54, 2.52, 4.56], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -3.0]),
            make_centered_cube([cx - s * 2.28, 12.92,  0.00], [0.42, 2.46, 4.52], "SCALE_SHINGLE"),
            make_centered_cube([cx,            12.98, -2.26], [4.58, 2.38, 0.44], "SCALE_SHINGLE"),
            make_centered_cube([cx,            12.92, +2.26], [4.56, 2.46, 0.44], "SCALE_SHINGLE"),
            # Tấm mai lòng bàn tay bọc kín hoàn toàn đáy nắm đấm (`y = 11.56..11.92`, xóa 100% mảng da lộ khi nhìn từ dưới lên!)
            make_centered_cube([cx,            11.74,  0.00], [4.62, 0.36, 4.62], "BOOT_CARAPACE",
                               face_mats={"down": "BOOT_CARAPACE", "up": "HIDE_DARK", "north": "HYDRA_RIM", "south": "HYDRA_RIM", outer_f: "HYDRA_RIM", inner_f: "HYDRA_RIM"}),
            make_centered_cube([cx + s * 2.42, 12.18,  0.00], [0.46, 0.46, 4.46], "HYDRA_RIM"),
            make_centered_cube([cx,            12.38, -2.36], [4.54, 0.44, 0.42], "HYDRA_RIM"),
        ]
        # ĐÚNG 3 MÓNG VUỐT HÌNH HỘP ÔM KHÍT SÁT BÀN TAY ĐÃ BỌC KÍN & QUẶP SÂU DỌC DƯỚI LÒNG BÀN TAY!
        for ox, is_mid in ((-1.32, False), (0.0, True), (+1.32, False)):
            w_root = 0.90 if is_mid else 0.82
            w_mid  = 0.80 if is_mid else 0.72
            w_tip  = 0.66 if is_mid else 0.58
            h_tip  = 0.64 if is_mid else 0.56
            y_tip  = 11.42 if is_mid else 11.48
            # Bậc 1: Khớp gốc hình hộp áp khít mặt trước găng tay (`z = -2.38`)
            cubes.append(make_centered_cube(
                [cx + ox, 12.92, -2.38], [w_root, 0.88, 0.52], "HELM_HORN_BASE"
            ))
            # Bậc 2: Thân móng hình hộp bọc khít xuống hết mép dưới nắm đấm (`y = 11.68..12.60`, `z = -2.36`)
            cubes.append(make_centered_cube(
                [cx + ox, 12.14, -2.36], [w_mid, 0.92, 0.54], "HYDRA_CLAW_TIP",
                face_mats=horn_claw_faces
            ))
            # Bậc 3: Chóp móng hình hộp trắng băng quặp ôm khít dưới tấm mai lòng bàn tay (`z = -2.42 -> -0.78`, 0 khe hở!)
            cubes.append(make_centered_cube(
                [cx + ox, y_tip, -1.60], [w_tip, h_tip, 1.64], "CLAW_BOOT_GLOW"
            ))
        return cubes

    rarm_cubes = build_hydra_arm(-1.0)
    larm_cubes = build_hydra_arm(+1.0)

    # ==================== 4. QUẦN GIÁP VẢY RỒNG 3D XẾP CHỒNG SO LE + ĐAI GÂN CHÉO + 3 MẤU VẢY GỐI + VÂY HÔNG (`armorRightLeg/LeftLeg`) ====================
    # Bỏ sạch khiên sắt gối & sọc đèn! Giáp đùi rồng vảy đá xếp tầng tự nhiên, gọn chuẩn Layer-2 bên trên đôi ủng chân rồng loe rộng!
    def build_hydra_leg(s: float):
        cx = s * 2.08
        outer_f = "west" if s < 0 else "east"
        inner_f = "east" if s < 0 else "west"
        return [
            # Thân ống quần da vảy đá granite tự nhiên chuẩn Layer-2 (`4.42 x 6.95 x 4.50`)
            make_centered_cube(
                [cx, 8.20, 0.0], [4.42, 6.95, 4.50], "LEG_THIGH",
                face_mats={"up": "SCALE_DARK", "down": "HIDE_DARK", inner_f: "HIDE_DARK", outer_f: "SCALE_SHINGLE", "south": "LEG_THIGH"}
            ),
            # Vây hông thủy long vát chéo ngoài đùi trên (đồng bộ bộ vây mang Spirit Hydra!)
            make_centered_cube(
                [cx + s * 2.44, 9.75, +0.10], [0.24, 2.55, 0.66], "SCALE_DARK",
                rot=[0.0, 0.0, s * +12.0], face_mats={"north": "HYDRA_RIM", outer_f: "HYDRA_RIM"}
            ),
            # HÀNG VẢY RỒNG 3D NỔI CHỒNG TẦNG SO LE TRÊN ĐÙI TRƯỚC, NGOÀI & SAU (`-11°`)
            make_centered_cube([cx - 0.94, 10.05, -2.38], [1.62, 1.38, 0.42], "SCALE_SHINGLE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx + 0.94, 10.05, -2.38], [1.62, 1.38, 0.42], "SCALE_SHINGLE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx,         8.45, -2.42], [2.10, 1.46, 0.46], "SCALE_SHINGLE", rot=[-12.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 1.54, 8.38, -2.36], [1.08, 1.34, 0.40], "SCALE_SHINGLE", rot=[-11.0, s * -10.0, 0.0]),
            # Mặt ngoài đùi (so le tự nhiên!)
            make_centered_cube([cx + s * 2.38, 9.80, -0.92], [0.42, 1.48, 1.56], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -12.0]),
            make_centered_cube([cx + s * 2.38, 8.15, +0.42], [0.44, 1.50, 2.10], "SCALE_SHINGLE", rot=[0.0, 0.0, s * -12.0]),
            # Mặt sau đùi (2 tầng vảy rồng so le!)
            make_centered_cube([cx - 0.90, 9.90, +2.38], [1.60, 1.42, 0.42], "SCALE_SHINGLE", rot=[+11.0, 0.0, 0.0]),
            make_centered_cube([cx + 0.90, 9.90, +2.38], [1.60, 1.42, 0.42], "SCALE_SHINGLE", rot=[+11.0, 0.0, 0.0]),
            make_centered_cube([cx,        8.30, +2.40], [2.15, 1.42, 0.44], "SCALE_SHINGLE", rot=[+12.0, 0.0, 0.0]),

            # Đai gân mai hắc thạch quấn chéo 3D quanh đùi–gối (`+11.5°`, ánh băng sâu mảnh tự nhiên, 0 sọc trắng!)
            make_centered_cube([cx, 6.45, 0.0], [4.90, 1.00, 5.04], "HYDRA_RIM", rot=[+11.5, 0.0, 0.0]),
            make_centered_cube([cx, 7.10, -0.04], [4.82, 0.48, 4.94], "SCALE_DARK", rot=[+11.5, 0.0, 0.0]),

            # 3 MẤU VẢY XƯƠNG GỐI RỒNG 3D TỰ NHIÊN (mấu giữa dài & nhô hơn, 2 mấu bên ôm gọn — 0 khiên sắt vuông!)
            make_centered_cube([cx + s * 1.40, 5.52, -2.52], [1.06, 1.02, 0.44], "KNEE_SCUTE", rot=[-10.0, 0.0, 0.0]),
            make_centered_cube([cx,            5.32, -2.62], [1.26, 1.34, 0.54], "KNEE_SCUTE", rot=[-12.0, 0.0, 0.0]),
            make_centered_cube([cx - s * 1.40, 5.52, -2.52], [1.06, 1.02, 0.44], "KNEE_SCUTE", rot=[-10.0, 0.0, 0.0]),
        ]

    rleg_cubes = build_hydra_leg(-1.0)
    lleg_cubes = build_hydra_leg(+1.0)

    # ==================== 5. ỦNG CHÂN RỒNG HỮU CƠ LOE RỘNG CHUẨN NETHERITE (`|x|=5.30`) + VẢY ỐNG ĐỒNG + 6 MÓNG VUỐT + 2 CỰA GÓT ====================
    def build_hydra_boot(s: float):
        cx = s * 2.52
        outer_f = "west" if s < 0 else "east"
        cubes = [
            # Thân ủng mai chân rồng đá granite dày chắc khỏe (`5.00 x 4.96 x 5.50`, đáy hạ -0.14 px chống z-fighting, 0 đè chân trong!)
            make_centered_cube(
                [cx, 2.34, 0.0], [5.00, 4.96, 5.50], "BOOT_CARAPACE",
                face_mats={"down": "BOOT_SOLE", "up": "SCALE_DARK", "north": "BOOT_CARAPACE", outer_f: "BOOT_SIDE", "south": "BOOT_SIDE"}
            ),
            # Cổ ủng mai vảy rồng nổi bậc vượt hẳn ống quần (`5.22 x 1.08 x 5.76`, `|x|=0.01..5.23` -> 0 đè nhau!)
            make_centered_cube(
                [s * 2.62, 4.62, 0.0], [5.22, 1.08, 5.76], "SCALE_RING",
                face_mats={"up": "HYDRA_RIM", "down": "HYDRA_RIM", "north": "SCALE_SHINGLE", outer_f: "SCALE_SHINGLE"}
            ),
            # 2 tầng vảy ống đồng tự nhiên ôm trước cổ chân (thay khiên kim loại!)
            make_centered_cube([cx, 3.65, -2.86], [2.85, 1.48, 0.46], "SCALE_SHINGLE", rot=[-10.0, 0.0, 0.0]),
            make_centered_cube([cx, 2.25, -2.88], [3.65, 1.28, 0.44], "SCALE_SHINGLE", rot=[-12.0, 0.0, 0.0]),
            # Cặp vây mắt cá thủy long nhỏ vát xiên ngoài cổ chân (đồng bộ vây mang Spirit Hydra!)
            make_centered_cube(
                [s * 5.26, 3.35, +0.35], [0.30, 2.15, 1.85], "SCALE_DARK",
                rot=[0.0, 0.0, s * -13.0], face_mats={"north": "HYDRA_RIM", "up": "HYDRA_RIM"}
            ),
            # ĐÚNG 1 CỰA XƯƠNG GÓT SẮC GỌN PHÍA SAU GÓT MỖI ỦNG (gốc sừng đá -> chóp trắng băng sáng!)
            make_segment_cube((cx, 1.25, +2.65), (cx, 1.56, +4.18), 0.78, 0.74, "CLAW_BOOT_GLOW", pad=0.14),
        ]
        # ĐÚNG 3 NGÓN MÓNG VUỐT RỒNG 3D TO KHỎE, LIỀN KHỐI TỪ MŨI BÀN CHÂN RA CHÓP MÓNG (TỔNG 6 MÓNG CHO 2 CHÂN!)
        for ox in (-1.52, 0.0, +1.52):
            # Lõi khớp ngón liền sàn (`y = -0.14`, xóa sạch khe hở tam giác gầm chân!)
            cubes.append(make_centered_cube(
                [cx + ox, 0.56, -3.10], [0.92, 1.40, 1.15], "HYDRA_CLAW_TIP",
                face_mats={**horn_claw_faces, "down": "BOOT_SOLE"}
            ))
            # Sống ngón chân rồng dốc mượt từ cổ chân ra móng vuốt
            cubes.append(make_segment_cube(
                (cx + ox, 2.22, -2.55), (cx + ox, 0.96, -3.88),
                0.92, 0.88, "HYDRA_CLAW_TIP", pad=0.18, face_mats=horn_claw_faces
            ))
            # Chóp móng vuốt rồng trắng ngà ánh băng vuông khỏe nhô ra trước (`y_min = -0.14`)
            cubes.append(make_centered_cube(
                [cx + ox, 0.48, -4.08], [0.90, 1.24, 1.38], "CLAW_BOOT_GLOW"
            ))
        return cubes

    rboot_cubes = build_hydra_boot(-1.0)
    lboot_cubes = build_hydra_boot(+1.0)

    bones = [
        {"name": "bipedHead",     "pivot": [0, 24, 0]},
        {"name": "armorHead",     "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},
        {"name": "hydra_crest_r", "parent": "armorHead", "pivot": [-4.55, 29.85, -1.85], "cubes": hydra_crest_r},
        {"name": "hydra_crest_l", "parent": "armorHead", "pivot": [+4.55, 29.85, -1.85], "cubes": hydra_crest_l},

        {"name": "bipedBody",        "pivot": [0, 24, 0]},
        {"name": "armorBody",        "parent": "bipedBody", "pivot": [0, 24, 0], "cubes": body_cubes},
        {"name": "hydra_heart_core", "parent": "armorBody", "pivot": [0.0, 18.50, -3.04], "cubes": heart_core_cubes},
        {"name": "hydra_back_spines","parent": "armorBody", "pivot": [0.0, 16.50, +3.05], "cubes": back_spines_cubes},

        {"name": "hydra_neck_r1", "parent": "armorBody",     "pivot": [-2.55, 20.30, +3.10], "cubes": neck_r1},
        {"name": "hydra_neck_r2", "parent": "hydra_neck_r1", "pivot": [-9.60, 27.15, +4.45], "cubes": neck_r2},
        {"name": "hydra_head_r",  "parent": "hydra_neck_r2", "pivot": [-14.95, 27.80, -0.20],"cubes": head_r},

        {"name": "hydra_neck_l1", "parent": "armorBody",     "pivot": [+2.55, 20.30, +3.10], "cubes": neck_l1},
        {"name": "hydra_neck_l2", "parent": "hydra_neck_l1", "pivot": [+9.60, 27.15, +4.45], "cubes": neck_l2},
        {"name": "hydra_head_l",  "parent": "hydra_neck_l2", "pivot": [+14.95, 27.80, -0.20],"cubes": head_l},

        {"name": "hydra_neck_c1", "parent": "armorBody",     "pivot": [0.0, 20.80, +2.85],   "cubes": neck_c1},
        {"name": "hydra_neck_c2", "parent": "hydra_neck_c1", "pivot": [0.0, 32.75, +9.85],   "cubes": neck_c2},
        {"name": "hydra_head_c",  "parent": "hydra_neck_c2", "pivot": [0.0, 40.95, +2.00],   "cubes": head_c},

        {"name": "tail_1", "parent": "armorBody", "pivot": list(tp[0]), "cubes": tail_1},
        {"name": "tail_2", "parent": "tail_1",    "pivot": list(tp[1]), "cubes": tail_2},
        {"name": "tail_3", "parent": "tail_2",    "pivot": list(tp[2]), "cubes": tail_3},
        {"name": "tail_4", "parent": "tail_3",    "pivot": list(tp[3]), "cubes": tail_4},
        {"name": "tail_5", "parent": "tail_4",    "pivot": list(tp[4]), "cubes": tail_5},
        {"name": "tail_6", "parent": "tail_5",    "pivot": list(tp[5]), "cubes": tail_6},

        {"name": "bipedRightArm", "pivot": [-5, 22, 0]},
        {"name": "armorRightArm", "parent": "bipedRightArm", "pivot": [-5, 22, 0], "cubes": rarm_cubes},

        {"name": "bipedLeftArm",  "pivot": [5, 22, 0]},
        {"name": "armorLeftArm",  "parent": "bipedLeftArm",  "pivot": [5, 22, 0], "cubes": larm_cubes},

        {"name": "bipedRightLeg",  "pivot": [-2, 12, 0]},
        {"name": "armorRightLeg",  "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rleg_cubes},
        {"name": "armorRightBoot", "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rboot_cubes},

        {"name": "bipedLeftLeg",   "pivot": [2, 12, 0]},
        {"name": "armorLeftLeg",   "parent": "bipedLeftLeg",  "pivot": [2, 12, 0], "cubes": lleg_cubes},
        {"name": "armorLeftBoot",  "parent": "bipedLeftLeg",  "pivot": [2, 12, 0], "cubes": lboot_cubes},
    ]

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.spirit_hydra_armor",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 4.8,
                    "visible_bounds_height": 4.8,
                    "visible_bounds_offset": [0.0, 1.6, 0.35],
                },
                "bones": bones,
            }
        ],
    }
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out = ASSETS / "geo" / "armor" / "spirit_hydra_armor.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, {TEX_W}x{TEX_H})")
    return geo, total_cubes


def build_spirit_hydra_armor_animations():
    anims = {
        "animation.spirit_hydra_armor.idle": {
            "loop": True,
            "animation_length": 3.2,
            "bones": {},
        }
    }
    out = ASSETS / "animations" / "armor" / "spirit_hydra_armor.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out}")


def build_spirit_hydra_armor_textures():
    """
    Texture đá granite xám than-tro đen lốm đốm 1:1 lấy mẫu từ Boss Spirit Hydra (`#1B1F27`, `#2D3440`, `#465062`, `#647287`)
    + Glowmask FULL_BRIGHT rực rỡ (`alpha = 255`, `#66F7FF -> #E8FFFF`) trên lõi ngực, 2 vòng tay, đai gối,
    chóp 6 móng vuốt giày, cựa gót và toàn bộ phần đầu/cổ/đuôi!
    """
    verify_uv_and_fin_padding()
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    # Bảng màu đá granite & vảy rồng tinh xảo 7 bậc chuyển sắc lam băng (chi tiết sắc mịn 1x1 px, độ chuyển mềm tự nhiên, hết cảm giác thô cứng!)
    STONE = [
        (11, 15, 24, 255),    # 0 khe rãnh vảy & vân nứt sâu
        (22, 30, 44, 255),    # 1 #161E2C nền đá tối biển sâu
        (38, 52, 74, 255),    # 2 #26344A thân vảy đá granite trung
        (62, 86, 116, 255),   # 3 #3E5674 mặt vảy chuyển sáng mượt
        (96, 134, 172, 255),  # 4 #6086AC sống gân vảy & mép sáng lam
        (144, 192, 224, 255), # 5 #90C0E0 highlight cong đỉnh vảy sắc mịn
        (206, 238, 255, 255), # 6 #CEEEFF điểm chấm bắt sáng chóp vảy (1px glint!)
    ]
    FROST_BOUNCE = (40, 126, 166, 255)  # phản quang xanh băng hắt lên vảy quanh lõi tim
    FROST_RIM    = (84, 202, 240, 255)  # viền phản quang sát khe phát sáng
    CYAN = {
        "d": (14, 138, 196, 255),
        "c": (44, 212, 252, 255),
        "C": (77, 244, 255, 255),
        "Q": (102, 247, 255, 255),
        "W": (232, 255, 255, 255),
    }
    IVORY = [
        (178, 198, 218, 255),
        (216, 232, 248, 255),
        (238, 255, 255, 255),
    ]

    def paint_dragon_scale_mesh(key: str, base_lvl: int = 2, scale_w: int = 6, scale_h: int = 5,
                                add_veins: bool = True, highlight_top: bool = False):
        """
        Đánh vân & vảy rồng hữu cơ độ phân giải 1x1 pixel êm mượt, sang trọng (KHÔNG lốm đốm chấm trắng ríu mắt!):
        - Độ tương phản nội khối dịu (±1 bậc quanh `base_lvl`) để giữ bề mặt da rồng liền lạc, sang và sắc nét
        - Vảy bo tròn-nhọn hình lá xếp so le kèm vân sớ mảnh & đường vân rạn đá hữu cơ chảy chéo
        - Chỉ đẩy highlight sáng (`STONE[4..5]`) ở mép gờ trên của khối chính!
        """
        x0, y0, w, h = REG[key]
        for y in range(h):
            row_id = y // scale_h
            ly = y % scale_h
            half_w = scale_w * 0.5
            x_shift = (scale_w // 2) if (row_id % 2 == 1) else 0
            for x in range(w):
                sx = (x + x_shift) % scale_w
                col_id = (x + x_shift) // scale_w
                dx = abs(sx - (scale_w - 1) * 0.5) / max(1.0, half_w)
                dy = ly / max(1.0, scale_h - 1.0)
                # Dáng vảy lá bo cong mềm mại (không gắt!)
                edge_curve = (dx * dx) * 0.65 + (dy ** 1.35) * 0.85
                noise = ((x * 53 + y * 97 + col_id * 17 + row_id * 31 + x0 * 7) ^ (x * 13 - y * 29)) & 15
                # Đường vân đá rồng chảy chéo mềm mại (mảnh 1px, liên tục tự nhiên)
                vein_dist = abs(((x * 2 + y * 3 + int(math.sin((x * 0.45 + y * 0.30 + x0) ) * 1.6)) % 13) - 6)

                if edge_curve > 1.18 or (ly == scale_h - 1 and dx > 0.40):
                    lvl = max(0, base_lvl - 2)  # khe rãnh cong giữa vảy sâu êm
                elif edge_curve > 0.96:
                    lvl = max(0, base_lvl - 1)  # viền đổ bóng mịn quanh vảy
                elif dx <= 0.18 and 1 <= ly <= scale_h - 2:
                    # Sống gân giữa mảnh êm dịu (chỉ +1 bậc -> hết chấm trắng lốm đốm!)
                    lvl = min(4, base_lvl + 1)
                elif ly == 0 and dx <= 0.65:
                    lvl = min(4, base_lvl + 1)
                else:
                    # Chuyển sắc mượt trên lòng vảy kèm vân hạt siêu nhẹ
                    lvl = base_lvl + (1 if (dy < 0.35 and noise < 5) else (-1 if (dy > 0.72 and noise < 6) else 0))

                # Vân rạn đá hữu cơ mảnh (tối 1 bậc + gờ sáng nhẹ kế bên tạo chiều sâu điêu khắc!)
                if add_veins and vein_dist == 0 and noise < 9:
                    lvl = max(0, lvl - 1)
                elif add_veins and vein_dist == 1 and noise < 5 and lvl < 4:
                    lvl = min(4, lvl + 1)

                # Highlight mềm tập trung ở gờ trên-trái của khối lớn (không chấm rải rác!)
                if highlight_top:
                    if y == 0 and 1 <= x <= w - 2:
                        lvl = min(5, max(lvl, base_lvl + 2))
                    elif y == 1 and 1 <= x <= w - 2 and lvl >= base_lvl:
                        lvl = min(4, max(lvl, base_lvl + 1))
                    elif y == h - 1:
                        lvl = 0

                diff[y0 + y, x0 + x] = STONE[max(0, min(6, lvl))]

    # Phủ hệ vảy lá & vân đá mượt 1x1 cho toàn bộ vùng cơ sở (mũ, vai, lưng, đùi, ủng, đuôi!)
    for k, bl, sw, sh, ht in (
        ("SCALE_DARK",     1, 6, 5, False),
        ("SCALE_MID",      2, 6, 5, True),
        ("SCALE_BRIGHT",   3, 6, 5, True),
        ("SCALE_RIDGE",    4, 5, 4, True),
        ("HELM_TOP",       2, 6, 5, True),
        ("HELM_BACK",      2, 6, 5, True),
        ("HELM_SIDE",      2, 6, 5, True),
        ("HELM_SNOUT_TOP", 3, 5, 5, True),
        ("HELM_SNOUT_SIDE",2, 6, 5, True),
        ("PAULDRON_PLATE", 3, 6, 5, True),
        ("ARM_SLEEVE",     2, 5, 5, False),
        ("LEG_THIGH",      2, 6, 5, True),
        ("BOOT_SIDE",      2, 6, 5, True),
        ("BOOT_TOP",       3, 6, 5, True),
        ("BOOT_SOLE",      1, 5, 4, False),
        ("CHEST_SIDE",     2, 6, 5, False),
    ):
        paint_dragon_scale_mesh(k, base_lvl=bl, scale_w=sw, scale_h=sh, add_veins=True, highlight_top=ht)

    # Đánh vân & highlight cong mềm trên vòm mai vai (`PAULDRON_PLATE` 14x14): chuyển khối mịn từ vòm trên sáng lam xuống gầm tối + mạch băng mảnh!
    x0, y0, w, h = REG["PAULDRON_PLATE"]
    for y in range(h):
        for x in range(w):
            # Tăng nhẹ độ sáng mượt về phía đỉnh vòm vai (`y = 0..4`) mà vẫn giữ trọn đường vân vảy bên dưới!
            if y == h - 3 and 2 <= x <= w - 3:
                wy = (h - 3) + (0 if x % 4 in (1, 2) else 1)
                if y == wy:
                    col = (84, 226, 255, 255) if (4 <= x <= w - 5) else (44, 174, 218, 255)
                    diff[y0 + y, x0 + x] = col
                    glow[y0 + y, x0 + x] = (62, 204, 240, 255) if (4 <= x <= w - 5) else (28, 138, 182, 255)
            elif y == 0 and 1 <= x <= w - 2:
                diff[y0 + y, x0 + x] = STONE[6] if (3 <= x <= 9) else STONE[5]
            elif y == 1 and 1 <= x <= w - 2:
                diff[y0 + y, x0 + x] = STONE[5] if (2 <= x <= 10) else STONE[4]
            elif y == h - 1 or x == w - 1:
                diff[y0 + y, x0 + x] = STONE[0]

    # Mặt trước mũi mũ rồng (`HELM_NOSE` 12x6): vảy mõm mịn + sống mũi highlight mượt + 2 lỗ mũi sâu đối xứng!
    paint_dragon_scale_mesh("HELM_NOSE", base_lvl=3, scale_w=5, scale_h=4, add_veins=True, highlight_top=True)
    x0, y0, w, h = REG["HELM_NOSE"]
    for ny in (2, 3):
        for nx in (2, 3, 8, 9):
            diff[y0 + ny, x0 + nx] = STONE[0]
    for nx in range(w):
        diff[y0 + h - 1, x0 + nx] = STONE[0]
    for nx in (4, 5, 6, 7):
        diff[y0, x0 + nx] = STONE[6] if nx in (5, 6) else STONE[5]

    # Mắt rồng trên mũ (`HELM_EYE` 6x4): phát sáng xanh điện băng FULL_BRIGHT với con ngươi dọc đen sắc dữ tợn!
    x0, y0, w, h = REG["HELM_EYE"]
    for y, row in enumerate(["QQ00QQ", "QW00WQ", "CC00WW", "CC00QQ"]):
        for x, ch in enumerate(row):
            if ch == "0":
                diff[y0 + y, x0 + x] = STONE[0]
            else:
                diff[y0 + y, x0 + x] = CYAN[ch]
                glow[y0 + y, x0 + x] = CYAN[ch]

    # Răng nanh trắng ngà mũ rồng (`FANG_IVORY` 4x8): vân men ngà mịn sắc
    x0, y0, w, h = REG["FANG_IVORY"]
    for y in range(h):
        for x in range(w):
            if x == 0 and y < 5:
                diff[y0 + y, x0 + x] = IVORY[0]
            elif y < 2:
                diff[y0 + y, x0 + x] = IVORY[0]
            elif y < 5:
                diff[y0 + y, x0 + x] = IVORY[1]
            else:
                diff[y0 + y, x0 + x] = (244, 250, 255, 255)

    # Sừng vai, gai lưng & khớp 6 móng kẹp tim — VÂN VÒNG SỪNG RỒNG 1x1 MỊN SẮC + GÂN SÁNG DỌC SỐNG SỪNG!
    horn_pal = {
        "0": (11, 15, 24, 255),
        "1": (24, 34, 50, 255),
        "2": (46, 66, 92, 255),
        "3": (78, 114, 150, 255),
        "4": (126, 176, 212, 255),
        "5": (196, 232, 252, 255),
        "C": CYAN["C"],
        "Q": CYAN["Q"],
        "W": CYAN["W"],
    }
    def paint_horn_8x8(key: str, rows8, emit_cyan: bool = False):
        x0, y0, w, h = REG[key]
        for y in range(h):
            r = rows8[y]
            for x in range(w):
                col = horn_pal[r[x]]
                diff[y0 + y, x0 + x] = col
                if emit_cyan and r[x] in ("C", "Q", "W"):
                    glow[y0 + y, x0 + x] = col

    paint_horn_8x8("HELM_HORN_BASE", [
        "12355421",
        "01234310",
        "12454321",
        "12343210",
        "01243210",
        "12343221",
        "01232110",
        "00121100",
    ])
    paint_horn_8x8("HELM_HORN_MID", [
        "23555431",
        "12344320",
        "23455421",
        "12344321",
        "12454320",
        "12343210",
        "01233210",
        "01122100",
    ])
    paint_horn_8x8("HELM_HORN_DARK", [
        "12344310",
        "01233210",
        "12343210",
        "01232100",
        "01232110",
        "01122100",
        "00121000",
        "00011000",
    ])
    paint_horn_8x8("HELM_HORN_TIP", [
        "12455421",
        "12344321",
        "12CQWC21",
        "1CQWWQC1",
        "CCWWWWCC",
        "QQWWWWQQ",
        "QQQWWQQQ",
        "CQQQQQQC",
    ], emit_cyan=True)

    # Ô vảy giáp hình khiên nhọn tinh xảo 1x1 (`HYDRA_SCALE_3D` 8x8)
    x0, y0, w, h = REG["HYDRA_SCALE_3D"]
    scale_rows = [
        "03566530",
        "24555542",
        "13455431",
        "12344321",
        "01244210",
        "01233210",
        "00122100",
        "00011000",
    ]
    for y, row in enumerate(scale_rows):
        for x, ch in enumerate(row):
            diff[y0 + y, x0 + x] = STONE[int(ch)]

    # === BỘ VẬT LIỆU VẢY & VÂN RỒNG TINH XẢO MƯỢT MẮT 1x1 PIXEL (Row 3, y=114..134 — KHÔNG RÍU MẮT, KHÔNG THÔ!) ===
    # 1. SCALE_SHINGLE (16x16): MỖI KHỐI VẢY 3D LÀ 1 TẤM VẢY RỒNG HÌNH KHIÊN MŨI NHỌN ĐIÊU KHẮC TINH XẢO + VÂN TIA RẺ QUẠT CHẢY DỌC THÂN VẢY!
    x0, y0, w, h = REG["SCALE_SHINGLE"]
    for y in range(h):
        for x in range(w):
            dx = abs(x - 7.5) / 7.5   # 0 ở trục sống vảy -> 1 ở 2 mép bên
            dy = y / 15.0             # 0 ở gốc trên -> 1 ở mũi vảy nhọn đáy
            # Đường viền cong hình khiên rồng tự nhiên bo nhọn ở đáy
            outline = (dx ** 1.65) * 0.92 + (dy ** 1.45) * 0.85
            # Vân tia rẻ quạt / vân tăng trưởng chữ V mảnh chảy mượt 2 bên sống vảy (`organic fan striations`!)
            fan_vein = abs((y - int(dx * 5.5)) % 4)
            micro = ((x * 29 + y * 47) ^ (x * 11 - y * 17)) & 7

            if outline > 1.24 or y == h - 1 or x in (0, w - 1):
                lvl = 0  # viền bóng cong ôm lấy tấm vảy
            elif outline > 1.06:
                lvl = 1  # đổ bóng mép vảy mềm
            elif dx <= 0.14 and 1 <= y <= 12:
                # Sống gân chính mượt chạy dọc trục giữa tấm vảy (bắt sáng dịu dần từ đỉnh xuống mũi!)
                lvl = 6 if y <= 2 else (5 if y <= 6 else (4 if y <= 10 else 3))
            elif y <= 1 and dx <= 0.72:
                # Mép gờ vòm trên bắt sáng mềm mại
                lvl = 5 if dx <= 0.42 else 4
            elif y == 2 and dx <= 0.78:
                lvl = 4 if dx <= 0.48 else 3
            elif fan_vein == 0 and 3 <= y <= 12 and dx >= 0.20:
                # Rãnh vân chữ V mảnh điêu khắc tinh tế dọc hai cánh vảy!
                lvl = 1 if dy > 0.55 else 2
            elif fan_vein == 1 and 3 <= y <= 10 and dx >= 0.22:
                # Gờ sáng mảnh kế bên vân chữ V tạo cảm giác nổi 3D mịn!
                lvl = 4 if dy < 0.42 else 3
            else:
                # Lòng vảy chuyển mượt từ sáng trên xuống tối dưới (0 chấm trắng lốm đốm!)
                lvl = 3 if dy < 0.48 else (2 if dy < 0.80 else 1)
                if micro == 0 and lvl > 1:
                    lvl -= 1
            diff[y0 + y, x0 + x] = STONE[max(0, min(6, lvl))]

    # 2. SCALE_RING (12x12): 2 tầng vảy lá bo cong chuyển sắc êm mượt cho cánh tay & cổ ủng (0 răng cưa trắng lốm đốm!)
    x0, y0, w, h = REG["SCALE_RING"]
    for y in range(h):
        for x in range(w):
            if y < 6:
                lx = x % 6
                ly = y
            else:
                lx = (x + 3) % 6
                ly = y - 6
            dx = abs(lx - 2.5) / 2.5
            dy = ly / 5.0
            curve = (dx ** 1.5) * 0.80 + (dy ** 1.3) * 0.85
            if curve > 1.22 or ly == 5:
                lvl = 0
            elif curve > 1.02:
                lvl = 1
            elif dx <= 0.22 and 1 <= ly <= 4:
                lvl = 5 if ly == 1 else (4 if ly <= 3 else 3)
            elif ly == 0 and dx <= 0.65:
                lvl = 4 if dx <= 0.40 else 3
            else:
                lvl = 3 if dy < 0.45 else 2
            diff[y0 + y, x0 + x] = STONE[max(0, min(6, lvl))]

    # 3. HYDRA_RIM (12x12): Gờ mai vân đá có khe mạch năng lượng băng uốn nhịp hữu cơ (tinh tế hơn thanh ngang cứng!)
    x0, y0, w, h = REG["HYDRA_RIM"]
    for y in range(h):
        for x in range(w):
            speck = (x * 13 + y * 31) & 7
            cy_vein = (h // 2) + (0 if (x % 4 in (1, 2)) else -1)
            if y == cy_vein and 1 <= x <= w - 2:
                is_hot = (x % 3 != 0)
                col = CYAN["W"] if (x in (4, 5, 7)) else (CYAN["Q"] if is_hot else CYAN["C"])
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = (152, 248, 255, 255) if is_hot else (76, 220, 252, 255)
            elif abs(y - cy_vein) == 1:
                diff[y0 + y, x0 + x] = STONE[0]
            elif y == 0:
                diff[y0 + y, x0 + x] = STONE[5] if (x % 3 != 0) else STONE[4]
            elif y == 1:
                diff[y0 + y, x0 + x] = STONE[3]
            else:
                diff[y0 + y, x0 + x] = STONE[2] if speck < 4 else STONE[1]

    # 4. HYDRA_CLAW_TIP (12x12): Móng sừng có vân sớ dọc tinh xảo + sống sáng cong trên + chóp phản quang băng từ lõi tim!
    x0, y0, w, h = REG["HYDRA_CLAW_TIP"]
    for y in range(h):
        for x in range(w):
            dx = abs(x - 5.5)
            flute = (x % 3 == 0)
            if y <= 1 and dx <= 4.5:
                diff[y0 + y, x0 + x] = STONE[6] if (y == 0 and dx <= 2.5) else STONE[5]
            elif y >= h - 4:
                is_tip = (y >= h - 2 and 1 <= x <= w - 2)
                col = CYAN["W"] if (y == h - 1 and dx <= 2.0) else (CYAN["Q"] if is_tip else CYAN["C"])
                diff[y0 + y, x0 + x] = col
                if y >= h - 3 and 1 <= x <= w - 2:
                    glow[y0 + y, x0 + x] = (176, 252, 255, 255) if is_tip else (78, 222, 252, 255)
            elif y >= h - 6:
                diff[y0 + y, x0 + x] = FROST_BOUNCE if flute else FROST_RIM
            else:
                diff[y0 + y, x0 + x] = STONE[2] if flute else (STONE[4] if dx <= 2.2 else STONE[3])

    # 5. WAIST_CRYSTAL (12x12): Ổ đá vảy nhuyễn ôm viên tinh thể lục giác Spirit Hydra rực sáng giữa đai eo!
    paint_dragon_scale_mesh("WAIST_CRYSTAL", base_lvl=2, scale_w=5, scale_h=4, add_veins=True, highlight_top=True)
    x0, y0, w, h = REG["WAIST_CRYSTAL"]
    for y in range(h):
        for x in range(w):
            d = abs(x - 5.5) * 0.88 + abs(y - 5.5) * 1.05
            if d <= 2.7:
                col = CYAN["W"] if d <= 1.55 else CYAN["Q"]
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col
            elif d <= 3.7:
                diff[y0 + y, x0 + x] = FROST_RIM if d <= 3.2 else STONE[0]
            elif y == h - 1 or x == w - 1:
                diff[y0 + y, x0 + x] = STONE[0]

    # 6. KNEE_SCUTE (12x12): Mấu vảy xương gối hình khiên nhọn khắc vân chữ V tinh xảo + lõi tinh thể băng nhỏ ở sống gối!
    x0, y0, w, h = REG["KNEE_SCUTE"]
    for y in range(h):
        for x in range(w):
            dx = abs(x - 5.5) / 5.5
            dy = y / 11.0
            shield = (dx ** 1.55) * 0.90 + (dy ** 1.35) * 0.82
            chevron = abs((y - int(dx * 4.0)) % 4)
            if shield > 1.24 or y == h - 1 or x in (0, w - 1):
                diff[y0 + y, x0 + x] = STONE[0]
            elif dx <= 0.22 and 2 <= y <= 6:
                col = CYAN["W"] if (dx <= 0.12 and 3 <= y <= 5) else CYAN["Q"]
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = (146, 248, 255, 255) if (dx <= 0.12 and 3 <= y <= 5) else (76, 218, 250, 255)
            elif dx <= 0.34 and y <= 8:
                diff[y0 + y, x0 + x] = STONE[6] if y <= 2 else STONE[5]
            elif chevron == 0 and y <= 9:
                diff[y0 + y, x0 + x] = STONE[4]
            elif y <= 1:
                diff[y0 + y, x0 + x] = STONE[5]
            else:
                diff[y0 + y, x0 + x] = STONE[2] if shield > 0.90 else STONE[3]

    # 7. HIDE_DARK (12x12): Da vảy rồng lót nhuyễn mịn 1x1
    paint_dragon_scale_mesh("HIDE_DARK", base_lvl=1, scale_w=5, scale_h=4, add_veins=False, highlight_top=False)

    # 8. BOOT_CARAPACE (12x12) & BOOT_CUFF: Vảy lá & vân gân chân rồng tinh xảo êm mượt!
    paint_dragon_scale_mesh("BOOT_CARAPACE", base_lvl=2, scale_w=6, scale_h=5, add_veins=True, highlight_top=True)
    x0, y0, w, h = REG["BOOT_CARAPACE"]
    for x in range(1, w - 1):
        diff[y0 + 5, x0 + x] = STONE[0]
        diff[y0 + 6, x0 + x] = STONE[5] if (3 <= x <= 8) else STONE[4]

    paint_dragon_scale_mesh("BOOT_CUFF", base_lvl=3, scale_w=5, scale_h=4, add_veins=True, highlight_top=True)

    # 3 Cổ phụ & vòng cổ (`NECK_RING` / `TAIL_RING`): vảy lá xếp mượt 1x1 có gân sống dịu mắt!
    paint_dragon_scale_mesh("NECK_RING", base_lvl=2, scale_w=6, scale_h=5, add_veins=True, highlight_top=True)
    paint_dragon_scale_mesh("TAIL_RING", base_lvl=2, scale_w=6, scale_h=5, add_veins=True, highlight_top=True)

    # Vòng đai năng lượng, lõi tinh thể, sừng, sống đuôi: PHÁT SÁNG RỰC FULL_BRIGHT (#E8FFFF lõi -> #66F7FF/#4DF4FF viền, alpha=255!)
    def fill_cyan_band(key: str, horizontal: bool = True, segmented: bool = False):
        x0, y0, w, h = REG[key]
        for y in range(h):
            for x in range(w):
                if segmented and (y % 4 == 3):
                    diff[y0 + y, x0 + x] = STONE[1]
                    continue
                t = abs((y - (h - 1) * 0.5) / max(1.0, h * 0.5)) if horizontal else abs((x - (w - 1) * 0.5) / max(1.0, w * 0.5))
                if t < 0.42:
                    col = CYAN["W"]   # #E8FFFF lõi sáng trắng băng rực!
                elif t < 0.78:
                    col = CYAN["Q"]   # #66F7FF xanh điện rực!
                else:
                    col = CYAN["C"]   # #4DF4FF viền sáng!
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col

    fill_cyan_band("CYAN_GLOW",  horizontal=True,  segmented=False)
    fill_cyan_band("CYAN_HORN",  horizontal=False, segmented=False)
    fill_cyan_band("CYAN_SPINE", horizontal=False, segmented=True)
    fill_cyan_band("CYAN_BLADE", horizontal=False, segmented=False)

    x0, y0, w, h = REG["HYDRA_EYE"]
    for y, r in enumerate(["00000000", "0CQQWWW0", "0QQWWWW0", "00QQQQ00", "000CC000", "00000000"]):
        for x, ch in enumerate(r):
            if ch == "0":
                diff[y0 + y, x0 + x] = STONE[0]
            else:
                diff[y0 + y, x0 + x] = CYAN[ch]
                glow[y0 + y, x0 + x] = CYAN[ch]

    # Răng hàm & mấu vuốt tay trắng ngà
    for key in ("IVORY_TOOTH", "CLAW_HAND"):
        x0, y0, w, h = REG[key]
        for y in range(h):
            for x in range(w):
                if y == h - 1 or x == 0:
                    diff[y0 + y, x0 + x] = IVORY[0]
                elif y <= h // 2:
                    diff[y0 + y, x0 + x] = IVORY[2]
                else:
                    diff[y0 + y, x0 + x] = IVORY[1]

    # MÓNG VUỐT GIÀY & CỰA GÓT (`CLAW_BOOT_GLOW`): gốc trắng ngà `#CBD3E3` -> chóp móng `#E8FFFF` ánh băng PHÁT SÁNG trên glowmask!
    x0, y0, w, h = REG["CLAW_BOOT_GLOW"]
    for y in range(h):
        for x in range(w):
            if y >= h // 2:
                # Phần chóp/mũi móng vuốt trắng băng phát sáng FULL_BRIGHT (`#E8FFFF` -> `#9EF9FF`)!
                col = (232, 255, 255, 255) if (1 <= x <= w - 2) else (158, 249, 255, 255)
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col
            elif y >= 2:
                diff[y0 + y, x0 + x] = IVORY[1]  # #CBD3E3
                glow[y0 + y, x0 + x] = (128, 224, 240, 255)
            else:
                diff[y0 + y, x0 + x] = IVORY[0]

    # Rãnh giáp tối tinh tế ở mặt trong đùi (`NAVY_INNER`)
    x0, y0, w, h = REG["NAVY_INNER"]
    for y in range(h):
        for x in range(w):
            v = (x + y) % 2
            diff[y0 + y, x0 + x] = (18 + v * 5, 24 + v * 6, 46 + v * 8, 255)

    # NGỰC GIÁP (`CHEST_FRONT` 18x26): vảy rồng khiên 1x1 + VẾT NỨT LÕI TINH THỂ DỌC PHÁT SÁNG RỰC FULL_BRIGHT!
    paint_dragon_scale_mesh("CHEST_FRONT", base_lvl=2, scale_w=5, scale_h=4, add_veins=True, highlight_top=False)
    x0, y0, w, h = REG["CHEST_FRONT"]
    cx_f, cy_f = (w - 1) * 0.5, 11.5
    for y in range(h):
        for x in range(w):
            dx = abs(x - cx_f)
            dy = abs(y - cy_f)
            taper_w = 2.85 * (max(0.0, 1.0 - dy / 11.5) ** 1.35)
            if dy <= 10.5 and dx <= max(0.55, taper_w * 0.52):
                col = CYAN["W"]
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col
            elif dy <= 11.2 and dx <= max(1.05, taper_w * 0.92):
                col = CYAN["Q"]
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col
            elif dy <= 11.8 and dx <= taper_w + 0.95:
                col = CYAN["C"]
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col
            elif dy <= 12.2 and dx <= taper_w + 2.45:
                diff[y0 + y, x0 + x] = FROST_RIM if (dx <= taper_w + 1.55) else FROST_BOUNCE

    paint_dragon_scale_mesh("CHEST_BACK", base_lvl=2, scale_w=5, scale_h=4, add_veins=True, highlight_top=True)
    x0, y0, w, h = REG["CHEST_BACK"]
    cx_b = (w - 1) * 0.5
    for y in range(2, h - 3):
        for x in range(w):
            dx = abs(x - cx_b)
            if dx <= 0.85:
                col = CYAN["W"] if (y % 4 != 0) else CYAN["Q"]
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col
            elif dx <= 1.75:
                col = CYAN["C"]
                diff[y0 + y, x0 + x] = col
                glow[y0 + y, x0 + x] = col
            elif dx <= 3.10:
                diff[y0 + y, x0 + x] = FROST_BOUNCE

    def paint_5ray_gill_fin(key_r: str, key_l: str, boss_jagged: bool = False):
        x0r, y0r, w, h = REG[key_r]
        x0l, y0l, _, _ = REG[key_l]
        rib_angles = [-1.18, -0.59, 0.00, +0.59, +1.12] if boss_jagged else [-0.64, -0.32, 0.00, +0.32, +0.62]
        for y in range(h):
            v = y / max(1, h - 1)
            for x in range(w):
                u = x / max(1, w - 1)
                du = 1.02 - u
                dv = (v - 0.50) * (2.05 if boss_jagged else 1.15)
                ang = math.atan2(dv, max(1e-4, du))
                rad = math.sqrt(du * du + dv * dv)
                if boss_jagged:
                    if ang < -1.24 or ang > 1.22 or rad < 0.05:
                        continue
                    min_rib_d = min(abs(ang - ra) for ra in rib_angles)
                    # Mép khuyết răng cưa sắc hình tia sét giống hệt Boss Spirit Hydra (`spirit_hydra.png` ảnh 5–8)!
                    saw = abs(((ang * 7.5 + 3.0) % 1.0) - 0.5) * 2.0
                    jag = 0.14 * (1.0 - saw) + 0.06 * (((x * 13 + y * 7) % 5) / 4.0)
                    max_rad = 0.96 - 0.14 * min(1.0, min_rib_d / 0.28) - jag - 0.06 * abs(ang)
                    if rad > max_rad:
                        continue
                    # Gân tia sét sáng chói & chuyển màu chuẩn Boss: lõi trắng-lam -> xanh điện -> viền lam đậm sắc!
                    vein = abs(((ang * 6.0 + 0.18 * math.sin(rad * 12.0)) % 1.0) - 0.5)
                    if rad < 0.34 or vein < 0.085:
                        col = CYAN["W"] if rad < 0.46 else CYAN["Q"]
                    elif rad < 0.68:
                        col = CYAN["C"]
                    elif rad < max_rad - 0.09:
                        col = CYAN["c"]
                    else:
                        col = CYAN["d"]
                    is_emit = True
                else:
                    if ang < -0.74 or ang > 0.72:
                        continue
                    min_rib_d = min(abs(ang - ra) for ra in rib_angles)
                    max_rad = 0.98 - 0.30 * min(1.0, min_rib_d / 0.17) - 0.10 * abs(ang)
                    if rad > max_rad or rad < 0.06:
                        continue
                    if min_rib_d < 0.032 and rad > 0.22:
                        col = STONE[1] if rad > 0.42 else CYAN["d"]
                        is_emit = False
                    else:
                        if rad < 0.44:
                            col = CYAN["W"]
                        elif rad < 0.72:
                            col = CYAN["Q"]
                        elif rad < 0.88:
                            col = CYAN["C"]
                        else:
                            col = CYAN["c"]
                        is_emit = True
                diff[y0r + y, x0r + x] = col
                diff[y0l + y, x0l + (w - 1 - x)] = col
                if is_emit:
                    glow[y0r + y, x0r + x] = col
                    glow[y0l + y, x0l + (w - 1 - x)] = col

    paint_5ray_gill_fin("FIN_LARGE_R", "FIN_LARGE_L", boss_jagged=True)
    paint_5ray_gill_fin("FIN_MINI_R",  "FIN_MINI_L",  boss_jagged=False)

    for k in ("FIN_LARGE_R", "FIN_LARGE_L", "FIN_MINI_R", "FIN_MINI_L"):
        x0, y0, w, h = REG[k]
        sub_a = diff[y0:y0+h, x0:x0+w, 3]
        assert np.all((sub_a == 0) | (sub_a == 255))
        pad = diff[y0-4:y0+h+4, x0-4:x0+w+4, 3].copy()
        pad[4:4+h, 4:4+w] = 0
        assert not np.any(pad != 0), f"Non-zero fin padding near {k}"

    out_dir = ASSETS / "textures" / "armor"
    out_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(out_dir / "spirit_hydra_armor.png")
    Image.fromarray(glow, "RGBA").save(out_dir / "spirit_hydra_armor_glowmask.png")
    glow_px = int(np.sum(glow[:, :, 3] > 0))
    print(f"TEX_ARMOR_OK: {TEX_W}x{TEX_H} Boss Granite + FULL_BRIGHT Glowmask (glowPixels={glow_px}, 0 fin-edge bleed)")
    return diff.astype(np.float32), glow.astype(np.float32)


def build_steve_body_faces():
    faces = []

    def add_box(bmin, bmax, rgb):
        x0, y0, z0 = bmin
        x1, y1, z1 = bmax
        corners = np.array([
            [x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
            [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1],
        ], dtype=np.float64)
        for idxs in ([0, 1, 2, 3], [5, 4, 7, 6], [4, 0, 3, 7], [1, 5, 6, 2], [3, 2, 6, 7], [4, 5, 1, 0]):
            faces.append((corners[idxs], None, rgb))

    add_box((-4.0, 24.0, -4.0), (4.0, 32.0, 4.0), (196, 148, 114))
    add_box((-4.02, 29.8, -4.02), (4.02, 32.02, 4.02), (66, 44, 28))
    add_box((-4.02, 24.5, +1.50), (4.02, 32.02, 4.02), (66, 44, 28))
    add_box((-3.0, 27.0, -4.05), (-2.0, 28.0, -3.98), (244, 246, 250))
    add_box((-2.0, 27.0, -4.05), (-1.0, 28.0, -3.98), (74, 72, 168))
    add_box((+2.0, 27.0, -4.05), (+3.0, 28.0, -3.98), (244, 246, 250))
    add_box((+1.0, 27.0, -4.05), (+2.0, 28.0, -3.98), (74, 72, 168))
    add_box((-1.0, 26.0, -4.04), (+1.0, 27.0, -3.98), (168, 122, 92))
    add_box((-1.5, 25.0, -4.04), (+1.5, 25.8, -3.98), (122, 74, 54))
    add_box((-4.0, 12.0, -2.0), (+4.0, 24.0, +2.0), (36, 128, 136))
    add_box((-4.0,  0.0, -2.0), ( 0.0, 12.0, +2.0), (46, 52, 112))
    add_box(( 0.0,  0.0, -2.0), (+4.0, 12.0, +2.0), (46, 52, 112))
    add_box((-8.0, 12.0, -2.0), (-4.0, 24.0, +2.0), (196, 148, 114))
    add_box((+4.0, 12.0, -2.0), (+8.0, 24.0, +2.0), (196, 148, 114))
    return faces


def render_4angle_preview(geo: dict, diff_img, glow_img):
    """Xuất bảng 4 góc sắc nét (FRONT, BACK, SIDE, 3/4 PERSPECTIVE) CÓ GHÉP NHÂN VẬT STEVE BÊN TRONG + bản riêng MŨ ĐẦU HYDRA!"""
    steve_faces = build_steve_body_faces()
    faces = list(steve_faces)
    # Chỉ lấy đầu Steve (y >= 23.9) + các bone mũ cho ảnh cận cảnh riêng mũ đầu Spirit Hydra!
    helm_faces = [f for f in steve_faces if float(np.min(f[0][:, 1])) >= 23.9]
    helm_bone_names = {"armorHead", "hydra_crest_r", "hydra_crest_l"}
    for b in geo["minecraft:geometry"][0]["bones"]:
        is_helm = b["name"] in helm_bone_names
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            corners = np.array([
                [ox, oy, oz], [ox + sx, oy, oz], [ox + sx, oy + sy, oz], [ox, oy + sy, oz],
                [ox, oy, oz + sz], [ox + sx, oy, oz + sz], [ox + sx, oy + sy, oz + sz], [ox, oy + sy, oz + sz],
            ], dtype=np.float64)
            if "rotation" in c:
                piv = np.array(c["pivot"], dtype=np.float64)
                R = bedrock_rot_matrix(c["rotation"])
                corners = ((corners - piv) @ R.T) + piv
            uv_map = c["uv"]
            for fname, idxs in (
                ("north", [0, 1, 2, 3]), ("south", [5, 4, 7, 6]),
                ("west",  [4, 0, 3, 7]), ("east",  [1, 5, 6, 2]),
                ("up",    [3, 2, 6, 7]), ("down",  [4, 5, 1, 0]),
            ):
                entry = (corners[idxs], uv_map[fname], None)
                faces.append(entry)
                if is_helm:
                    helm_faces.append(entry)

    def raster(view_mat, w, h, sc, center, ambient=0.56, bg=(22, 26, 34), face_list=None):
        use_faces = faces if face_list is None else face_list
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.28, 0.74, -0.60], dtype=np.float64)
        ld /= np.linalg.norm(ld)
        cx, cy = w * 0.5, h * 0.54

        for quad, uv, flat_rgb in use_faces:
            rel = quad - center[None, :]
            cam = (view_mat @ rel.T).T
            n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
            nl = np.linalg.norm(n)
            if nl < 1e-6:
                continue
            n /= nl
            shade = ambient + (1.0 - ambient) * abs(float(np.dot(n, ld)))
            scr = np.stack([cx - cam[:, 0] * sc, cy - cam[:, 1] * sc, cam[:, 2]], axis=1)
            if uv is not None:
                u0, v0 = uv["uv"]
                uw, uh = uv["uv_size"]
                u1, v1 = u0 + uw - 0.001, v0 + uh - 0.001
                uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
            else:
                uvs = [(0, 0)] * 4
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
                if flat_rgb is not None:
                    cy_i, cx_i = np.where(cand)
                    sub_z[cy_i, cx_i] = z[cy_i, cx_i]
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(np.array(flat_rgb, dtype=np.float32) * shade, 0, 255)
                else:
                    u = np.clip(np.floor(w0[cand] * t0[0] + w1[cand] * t1[0] + w2[cand] * t2[0]).astype(np.int32), 0, TEX_W - 1)
                    v = np.clip(np.floor(w0[cand] * t0[1] + w1[cand] * t1[1] + w2[cand] * t2[1]).astype(np.int32), 0, TEX_H - 1)
                    vis = diff_img[v, u, 3] > 128
                    if not np.any(vis):
                        continue
                    cy_all, cx_all = np.where(cand)
                    cy_i, cx_i = cy_all[vis], cx_all[vis]
                    sub_z[cy_i, cx_i] = z[cand][vis]
                    lit = diff_img[v[vis], u[vis], :3] * shade
                    g_a = glow_img[v[vis], u[vis], 3:4] / 255.0
                    lit = np.where(g_a > 0.5, glow_img[v[vis], u[vis], :3] * 1.05, lit)
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    ctr_full = np.array([0.0, 21.6, 2.0], dtype=np.float64)
    ctr_side = np.array([0.0, 21.6, 4.0], dtype=np.float64)
    pw, ph = 540, 540
    im_front = raster(cam_mat(180,  4), pw, ph, 10.2, ctr_full)
    im_back  = raster(cam_mat(  0,  4), pw, ph, 10.2, ctr_full)
    im_side  = raster(cam_mat(+90,  4), pw, ph, 10.2, ctr_side)
    im_pers  = raster(cam_mat(146, 12), pw, ph, 10.4, ctr_full)

    sheet = Image.new("RGB", (pw * 2, ph * 2), (14, 15, 22))
    panels = [
        (im_front, 0, 0, "1. FRONT - V-CHEST CORE + 3 SHOULDER SPIKES + 6 SEPARATED CLAWS"),
        (im_back,  1, 0, "2. BACK - 3 HEADS + CURVED TAIL + 1 HEEL SPUR EACH"),
        (im_side,  0, 1, "3. SIDE - GRANITE SCALES + FULL_BRIGHT BANDS & CLAW TIPS"),
        (im_pers,  1, 1, "4. 3/4 PERSPECTIVE - STEVE INSIDE + 0 SKIN LEAKS"),
    ]
    dr = ImageDraw.Draw(sheet)
    for im, gx, gy, title in panels:
        sheet.paste(im, (gx * pw, gy * ph))
        dr.text((gx * pw + 10, gy * ph + 10), title, fill=(215, 248, 255))

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out_preview = PREVIEW_DIR / "spirit_hydra_armor_4angle_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")

    # Ảnh cận cảnh 4 góc RIÊNG MŨ ĐẦU SPIRIT HYDRA (Front / Back / Side / 3/4) — 0 che bởi đầu phụ sau lưng!
    ctr_head = np.array([0.0, 30.8, -1.8], dtype=np.float64)
    hpw, hph = 480, 480
    hm_front = raster(cam_mat(180,  4), hpw, hph, 17.5, ctr_head, face_list=helm_faces)
    hm_back  = raster(cam_mat(  0,  4), hpw, hph, 17.5, ctr_head, face_list=helm_faces)
    hm_side  = raster(cam_mat(+90,  4), hpw, hph, 17.5, ctr_head, face_list=helm_faces)
    hm_pers  = raster(cam_mat(142, 12), hpw, hph, 17.5, ctr_head, face_list=helm_faces)
    hsheet = Image.new("RGB", (hpw * 2, hph * 2), (14, 15, 22))
    hdr = ImageDraw.Draw(hsheet)
    for im, gx, gy, title in (
        (hm_front, 0, 0, "HYDRA HELMET 1. FRONT - 3 CRYSTAL HORNS + 5-RAY GILL FRILLS"),
        (hm_back,  1, 0, "HYDRA HELMET 2. BACK - SOLID GRANITE + 5-RAY GILLS + HORNS"),
        (hm_side,  0, 1, "HYDRA HELMET 3. SIDE - BOSS SNOUT + SLIT EYE + GILL FAN"),
        (hm_pers,  1, 1, "HYDRA HELMET 4. 3/4 - BOSS HYDRA HEAD + OPEN FACE"),
    ):
        hsheet.paste(im, (gx * hpw, gy * hph))
        hdr.text((gx * hpw + 10, gy * hph + 10), title, fill=(215, 248, 255))
    out_helm_preview = PREVIEW_DIR / "spirit_hydra_helmet_4angle_preview.png"
    hsheet.save(out_helm_preview)
    print(f"HELM_PREVIEW_OK: {out_helm_preview}")

    # Ảnh cận cảnh 4 góc ÁO + VAI + QUẦN + GIÀY (Front / Back / Side / 3/4) để soi kỹ từng mảnh giáp chiến binh rèn!
    ctr_body = np.array([0.0, 12.8, 0.0], dtype=np.float64)
    bpw, bph = 480, 480
    bm_front = raster(cam_mat(180,  4), bpw, bph, 16.5, ctr_body)
    bm_back  = raster(cam_mat(  0,  4), bpw, bph, 16.5, ctr_body)
    bm_side  = raster(cam_mat(+90,  4), bpw, bph, 16.5, ctr_body)
    bm_pers  = raster(cam_mat(144, 10), bpw, bph, 16.5, ctr_body)
    bsheet = Image.new("RGB", (bpw * 2, bph * 2), (14, 15, 22))
    bdr = ImageDraw.Draw(bsheet)
    for im, gx, gy, title in (
        (bm_front, 0, 0, "ARMOR 1. FRONT - RIB-CLAW CUIRASS + TASSETS + KNEE GEMS"),
        (bm_back,  1, 0, "ARMOR 2. BACK - 11 BACK SPINES + FORGED PLATES + HEEL SPURS"),
        (bm_side,  0, 1, "ARMOR 3. SIDE - SWEPT PAULDRONS + ELBOW SPURS + SABATONS"),
        (bm_pers,  1, 1, "ARMOR 4. 3/4 - WARLORD DRAGON PLATE SILHOUETTE"),
    ):
        bsheet.paste(im, (gx * bpw, gy * bph))
        bdr.text((gx * bpw + 10, gy * bph + 10), title, fill=(215, 248, 255))
    out_body_preview = PREVIEW_DIR / "spirit_hydra_armor_body_closeup.png"
    bsheet.save(out_body_preview)
    print(f"BODY_PREVIEW_OK: {out_body_preview}")


def build_spirit_hydra_item_icons():
    """
    Vẽ lại trọn bộ 4 icon kho đồ 16x16 (`helmet`, `chestplate`, `leggings`, `boots`) đồng bộ 100% với model 3D mới
    và đánh highlight sắc khối lạnh (`#7488A8 -> #A2BAD8 -> #D8ECFF`) + ánh băng tinh thể rực rỡ!
    """
    pal = {
        ".": (0, 0, 0, 0),
        "0": (12, 16, 26, 255),     # viền ngoài obsidian sâu
        "1": (24, 32, 48, 255),     # đá than-lam tối
        "2": (44, 60, 84, 255),     # đá granite lam trung tính
        "3": (76, 108, 142, 255),   # mặt vảy bắt sáng lam
        "4": (118, 166, 204, 255),  # mảng highlight lam băng sáng!
        "5": (172, 220, 244, 255),  # highlight viền sắc cạnh!
        "6": (224, 246, 255, 255),  # điểm bắt sáng specular băng!
        "b": (28, 142, 188, 255),   # phản quang xanh băng thẫm
        "c": (52, 214, 252, 255),   # xanh điện cyan
        "q": (122, 246, 255, 255),  # xanh băng rực
        "w": (236, 255, 255, 255),  # lõi trắng băng phát sáng
        "i": (220, 236, 250, 255),  # trắng ngà răng/móng
    }
    icons = {
        # 1. Mũ Đầu Boss Spirit Hydra: 3 sừng tinh thể trên + 2 mang quạt cyan + sống mõm highlight + đôi mắt xiên + hàm răng!
        "spirit_hydra_helmet.png": [
            ".......qw.......",
            "..0c...cq...c0..",
            ".0qc0.0560.0cq0.",
            ".cqw00455400wqc.",
            "0cqb04566540bqc0",
            "0cq0343443430qc0",
            ".0b0q234432q0b0.",
            "..00w023320w00..",
            "..010012210010..",
            "..020i.00.i020..",
            "..030......030..",
            "..040......040..",
            "..020i....i020..",
            "...0103443010...",
            "....00122100....",
            "................",
        ],
        # 2. Áo Giáp Mai Rồng: vai sừng highlight sắc + 6 móng sừng kẹp tim tinh thể kim cương + ngọc eo (0 sọc đồ ngủ!)
        "spirit_hydra_chestplate.png": [
            ".0c..........c0.",
            "0q40........04q0",
            "056500....005650",
            "0454300ww0034540",
            "0343210qq0123430",
            "02345bqqqqb54320",
            "012310qwwq013210",
            ".0245bqwwqb5420.",
            "..0210bqqb0120..",
            "..03450qq05430..",
            "..02320cc02320..",
            "..0122bwwb2210..",
            "..02340qq04320..",
            "..012321123210..",
            "...0012222100...",
            "................",
        ],
        # 3. Quần Giáp Vảy Rồng: vảy đùi highlight + ngọc eo + đai gân chéo băng + 3 mấu vảy xương gối sáng khối!
        "spirit_hydra_leggings.png": [
            "................",
            "..000000000000..",
            "..04554qq45540..",
            "..03443bb34430..",
            "..023432234320..",
            "..034320023430..",
            "..023210012320..",
            "..045320023540..",
            "..0bqc2002cqb0..",
            "..02bqc00cqb20..",
            "..035630036530..",
            "..024520025420..",
            "..012310013210..",
            "..012210012210..",
            "...0000..0000...",
            "................",
        ],
        # 4. Ủng Chân Rồng: cổ ủng highlight + vảy ống đồng + khe băng mắt cá + móng vuốt trắng băng sắc khối!
        "spirit_hydra_boots.png": [
            "................",
            "................",
            "..00000..00000..",
            "..05650..05650..",
            "..03430..03430..",
            "..02320..02320..",
            "..04530..03540..",
            "..0bqc0..0cqb0..",
            "..03420..02430..",
            ".002320..023200.",
            "0542320..0232450",
            "0qw3420..0243wq0",
            "0iw2310..0132wi0",
            "0ii1210..0121ii0",
            ".000000..000000.",
            "................",
        ],
    }
    item_dir = ASSETS / "textures" / "item"
    item_dir.mkdir(parents=True, exist_ok=True)
    strip = Image.new("RGBA", (16 * 10 * 4 + 30, 16 * 10 + 20), (20, 24, 34, 255))
    for idx, (fname, rows) in enumerate(icons.items()):
        arr = np.zeros((16, 16, 4), dtype=np.uint8)
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), f"Bad icon shape: {fname}"
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                arr[y, x] = pal[ch]
        im = Image.fromarray(arr, "RGBA")
        im.save(item_dir / fname)
        up = im.resize((160, 160), Image.Resampling.NEAREST)
        strip.paste(up, (10 + idx * 165, 10), up)
    preview_out = PREVIEW_DIR / "spirit_hydra_icons_preview.png"
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    strip.save(preview_out)
    print(f"ICONS_OK: 4 highlighted 16x16 icons -> {item_dir} (preview: {preview_out})")


if __name__ == "__main__":
    geo_data, cubes = build_spirit_hydra_armor_geo()
    build_spirit_hydra_armor_animations()
    diff_img, glow_img = build_spirit_hydra_armor_textures()
    build_spirit_hydra_item_icons()
    render_4angle_preview(geo_data, diff_img, glow_img)
    print(f"ALL_DONE: Spirit Hydra Armor UHD1 ({TEX_W}x{TEX_H}, {cubes} cubes)")
