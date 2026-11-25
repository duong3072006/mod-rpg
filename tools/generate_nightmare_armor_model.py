#!/usr/bin/env python3
"""
Bộ sinh 1:1 chuẩn mật độ pixel-art Minecraft (`128x128`, ZERO noise)
cho **Nightmare Knight Armor v4 — Netherite-Frame Demon Sovereign**:
  Kết hợp phom giáp nặng nổi khối chuẩn **Netherite Armor** (+1.0 mũ/ngực/vai/ủng, +0.5 quần)
  với toàn bộ chi tiết đặc trưng của Boss **Nightmare Knight**:
  1. **Mũ Hiệp Sĩ Ác Quỷ (`armorHead`, `horn_r`, `horn_l`):**
     - Phom mũ dày kiểu Netherite (`10.0` rộng) có gờ mày nhọn chữ V, má giáp loe khung mặt (hở trọn mắt/mặt người chơi),
       sống đỉnh mũ nhọn & gáy thép kín.
     - **Cặp sừng cừu đỏ huyết uốn cong dày dặn** (6 đốt mỗi bên: vươn lên -> uốn rộng sang hai bên -> cuộn vòng ra sau–xuống dưới
       giống hệt cặp sừng cong của boss Nightmare Knight!).
  2. **Áo Giáp Ngực (`armorBody`) + 2 Huy Hiệu Ruby Đỏ + Khố Đỏ Trước (`tabard_front`):**
     - Phom ngực dày chuẩn Netherite (`9.8 x 12.2 x 5.8`), phủ 3 tầng giáp ngực chữ V thép đen sắc cạnh,
       **2 chốt tròn đá ruby đỏ rực** hai bên ngực trên, họa tiết xương sườn giáp dưới ngực & đai eo chữ V sắc nét.
     - **Tấm khố vải đỏ rách (`tabard_front`)** buông dọc chính giữa mặt trước giữa 2 đùi (cực đặc trưng của boss!).
  3. **Đôi Cánh Dơi Ác Quỷ Rách Đỏ 3 Khớp Đối Xứng (`wing_l/r_upper/mid/tip`):**
     - Xương cánh sắt đen vươn cao lên góc vuốt ngón cái đỉnh đỏ, 4 nan xương dài chĩa xuống đầu vuốt đỏ,
       màng cánh da đỏ huyết cắt rách răng cưa gấu dưới + các lỗ rách chiến trận nhỏ (padding trong suốt >= 2px, 0 lỗi viền UV!).
  4. **Áo Choàng Hoàng Gia Đỏ Rách Toàn Lưng (`cape_upper/mid/lower`):**
     - Xuất phát từ chốt chữ V thép đen sau gáy giữa 2 gốc cánh, xòe rộng phủ kín lưng & gấu dưới cắt rách tơi cực uy nghi!
  5. **Vai Giáp Gai Đối Xứng Chuẩn Netherite & Găng Tay Gai (`armorRightArm`, `armorLeftArm`):**
     - Vai giáp rộng nổi khối chuẩn Netherite (`6.8 x 3.9 x 6.5`) chồng tầng thép đen + gai vai vươn cao đối xứng 2 bên,
       bắp tay bọc giáp xích tối có đai đỏ bắp tay, găng cẳng tay loe có cánh khuỷu & 3 gai sắc dọc mép ngoài!
  6. **Quần Giáp Đùi Đa Tầng & Ủng Vuốt Quỷ Dày Chuẩn Netherite (`armorRightLeg/LeftLeg`, `armorRightBoot/LeftBoot`):**
     - Quần giáp đùi góc cạnh + khiên đầu gối vát nhọn; ủng giáp bước nhảy dày chuẩn Netherite (`5.2 -> 5.5` cổ ủng loe)
       kèm 3 móng vuốt quỷ mũi đỏ mỗi bàn chân!

Chạy: python tools/generate_nightmare_armor_model.py
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "nightmare_armor_previews"

TEX_W = 128
TEX_H = 128

REG = {
    # Hàng 0 (y=0..8): Mũ + vật liệu nền 4x4 sạch nét
    "HELM_TOP":          (0,   0,  8,  8),
    "HELM_BACK":         (8,   0,  8,  8),
    "HELM_SIDE":         (16,  0,  8,  8),
    "HELM_BROW":         (24,  0,  8,  4),
    "CHEEK_GUARD":       (24,  4,  4,  4),
    "CROWN_CREST":       (28,  4,  4,  4),

    "STEEL_DARK":        (34,  0,  4,  4),
    "STEEL_MID":         (38,  0,  4,  4),
    "STEEL_BRIGHT":      (42,  0,  4,  4),
    "STEEL_BEVEL":       (46,  0,  4,  4),
    "OBS_DEEP":          (50,  0,  4,  4),
    "HORN_BASE":         (54,  0,  4,  4),
    "HORN_MID":          (58,  0,  4,  4),
    "HORN_TIP":          (62,  0,  4,  4),
    "RUBY_CLASP":        (66,  0,  4,  4),
    "CLAW_BLOOD":        (70,  0,  4,  4),
    "WING_BONE":         (74,  0,  4,  4),
    "WING_JOINT":        (78,  0,  4,  4),
    "RIB_BONE":          (82,  0,  4,  4),
    "ARM_STRAP_RED":     (86,  0,  4,  2),
    "ARM_CAP":           (86,  2,  4,  4),
    "BACK_CLASP":        (92,  0,  6,  6),
    "BELT_BUCKLE":       (98,  0,  4,  4),
    "SPIKE_METAL":       (102, 0,  4,  4),
    "CORE_HEART_OUTER":  (108, 0,  4,  6),  # Vỏ trái tim Nightmare Core đỏ huyết phát sáng
    "CORE_HEART_INNER":  (112, 0,  4,  6),  # Lõi giữa Nightmare Core hồng san hô #FFA28A rực sáng
    "CORE_HEART_GLOW":   (116, 0,  4,  4),  # Tâm sáng trắng-san hô nhịp tim
    "RIB_CLAW_SILVER":   (116, 4,  4,  4),  # Móng xương sườn bạc sáng ôm quanh Nightmare Core
    "EMPTY_TRANS":       (124, 124, 1, 1),

    # Hàng 1 (y=10..26): Ngực, lưng, vai, tay, găng, đùi, gối, ủng
    "CHEST_FRONT":       (0,  10,  8, 12),
    "CHEST_BACK":        (8,  10,  8, 12),
    "CHEST_SIDE":        (16, 10,  4, 12),
    "CHEST_CHEVRON":     (20, 10,  6,  6),
    "PAULDRON_TOP":      (26, 10,  6,  6),
    "PAULDRON_OUT":      (32, 10,  6,  6),
    "PAULDRON_FRONT":    (38, 10,  6,  6),
    "ARM_SLEEVE":        (44, 10,  4, 11),
    "GAUNTLET_OUT":      (48, 10,  6,  6),
    "GAUNTLET_FRONT":    (54, 10,  6,  6),
    "WAIST_BELT":        (60, 10, 10,  2),
    "WAIST_RIBS":        (60, 13,  8,  4),
    "THIGH_FRONT":       (70, 10,  4,  8),
    "THIGH_SIDE":        (74, 10,  4,  8),
    "THIGH_BACK":        (78, 10,  4,  8),
    "HIP_TASSET":        (82, 10,  4,  5),
    "KNEE_GUARD":        (86, 10,  4,  4),
    "BOOT_CUFF":         (90, 10,  6,  3),
    "BOOT_FRONT":        (90, 14,  5,  6),
    "BOOT_SIDE_R":       (96, 14,  5,  6),
    "BOOT_SIDE_L":       (102, 14, 5,  6),
    "BOOT_BACK":         (108, 14, 5,  6),
    "BOOT_SOLE":         (114, 14, 5,  5),

    # Hàng 2 (y=32..54): 6 TẤM MÀNG CÁNH DƠI ĐỎ RÁCH (>= 4px padding alpha=0 xung quanh!)
    "WING_MEM_INNER_R":  (4,  32, 10, 16),
    "WING_MEM_MID_R":    (20, 32, 12, 20),
    "WING_MEM_OUTER_R":  (38, 32, 12, 18),
    "WING_MEM_INNER_L":  (56, 32, 10, 16),
    "WING_MEM_MID_L":    (72, 32, 12, 20),
    "WING_MEM_OUTER_L":  (90, 32, 12, 18),

    # Hàng 3 (y=60..84): Áo choàng toàn lưng 3 đoạn RÁCH ĐỎ + Khố trước đỏ rách (>= 4px padding alpha=0!)
    "CAPE_UPPER":        (4,  60, 12, 12),
    "CAPE_MID":          (22, 60, 14, 12),
    "CAPE_LOWER":        (42, 60, 16, 14),
    "TABARD_FRONT":      (64, 60,  8, 14),
}


def verify_uv_and_padding():
    occupied = {}
    for k, (x0, y0, w, h) in REG.items():
        assert x0 >= 0 and y0 >= 0 and x0 + w <= TEX_W and y0 + h <= TEX_H, f"Out of bounds: {k}"
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                assert (x, y) not in occupied, f"UV overlap at ({x},{y}) between {occupied[(x,y)]} and {k}"
                occupied[(x, y)] = k
    for k, (x0, y0, w, h) in REG.items():
        if not (k.startswith("WING_MEM_") or k.startswith("CAPE_") or k == "TABARD_FRONT"):
            continue
        for y in range(max(0, y0 - 2), min(TEX_H, y0 + h + 2)):
            for x in range(max(0, x0 - 2), min(TEX_W, x0 + w + 2)):
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    continue
                assert (x, y) not in occupied, f"Cutout padding violated near {k} at ({x},{y}) by {occupied[(x,y)]}"


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


def make_cutout_plane(center, width, height, front_key: str, back_key: str, rot=None, thickness: float = 0.08) -> dict:
    return make_centered_cube(
        center, [width, height, thickness], "EMPTY_TRANS",
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


def build_nightmare_armor_geo():
    """
    Hệ tọa độ Bedrock/GeckoLib 1.12.0:
      - Y=0: đế giày | Y=12: hông | Y=22: vai | Y=24: cổ | Y=32: đỉnh đầu
      - -Z = TRƯỚC | +Z = SAU | -X = PHẢI người chơi | +X = TRÁI người chơi
    """
    horn_faces = {
        "up": "HORN_TIP", "down": "HORN_BASE",
        "north": "HORN_MID", "south": "HORN_BASE",
        "east": "HORN_MID", "west": "HORN_MID",
    }
    bone_faces = {
        "up": "WING_JOINT", "down": "OBS_DEEP",
        "north": "WING_BONE", "south": "WING_BONE",
        "east": "WING_BONE", "west": "WING_BONE",
    }
    claw_faces = {
        "up": "CLAW_BLOOD", "down": "HORN_BASE",
        "north": "CLAW_BLOOD", "south": "HORN_BASE",
        "east": "CLAW_BLOOD", "west": "CLAW_BLOOD",
    }

    # ==================== 1. MŨ KHUNG NETHERITE + MÃO GOTHIC + CẶP SỪNG CỪU ĐỎ CONG (`armorHead`) ====================
    head_cubes = [
        # Khung nóc mũ dày chuẩn Netherite (10.0 x 1.20 x 10.10)
        make_cube(
            [-5.00, 32.15, -5.12], [10.00, 1.20, 10.12], "STEEL_DARK",
            face_mats={"up": "HELM_TOP", "north": "STEEL_BEVEL", "south": "STEEL_MID", "east": "STEEL_MID", "west": "STEEL_MID"}
        ),
        # Gờ vòm đỉnh mũ vát nhọn kiểu vương miện hắc hiệp sĩ (khớp góc nhìn sau & trước boss!)
        make_centered_cube([0.0, 33.65, +0.15], [5.80, 0.90, 8.80], "STEEL_MID", face_mats={"up": "HELM_TOP", "north": "CROWN_CREST"}),
        # Sống mào đỉnh nhọn chạy từ trán ra sau gáy
        make_centered_cube([0.0, 34.25, -2.65], [1.40, 1.65, 2.55], "STEEL_BRIGHT", rot=[+14.0, 0.0, 0.0], face_mats={"north": "STEEL_BEVEL", "up": "STEEL_BEVEL"}),
        make_centered_cube([0.0, 34.10, +0.95], [1.30, 1.45, 3.20], "STEEL_MID",    rot=[-12.0, 0.0, 0.0]),
        make_centered_cube([0.0, 32.45, +4.40], [1.55, 2.35, 1.50], "STEEL_MID",    rot=[-28.0, 0.0, 0.0]),

        # Thành sau gáy dày chuẩn Netherite + gờ thép nhọn sau gáy (giống góc sau ảnh 1!)
        make_cube([-5.00, 23.90, +4.12], [10.00, 8.40, 0.96], "STEEL_DARK", face_mats={"south": "HELM_BACK", "down": "STEEL_BEVEL"}),
        make_centered_cube([0.0, 26.80, +5.10], [3.60, 4.40, 0.85], "STEEL_MID", rot=[+8.0, 0.0, 0.0], face_mats={"south": "HELM_BACK"}),
        make_centered_cube([0.0, 24.40, +4.95], [7.80, 1.35, 1.05], "STEEL_BEVEL"),

        # Hai thành bên mũ dày nổi khối chuẩn Netherite (10.0 px tổng rộng, hở trọn mặt người chơi!)
        make_cube([-5.00, 28.45, -5.12], [0.96, 3.85, 10.12], "STEEL_DARK", face_mats={"west": "HELM_SIDE", "north": "STEEL_BEVEL"}),
        make_cube([-5.00, 23.95, -3.55], [0.96, 4.65, 8.55],  "STEEL_DARK", face_mats={"west": "HELM_SIDE", "north": "CHEEK_GUARD"}),
        make_cube([+4.04, 28.45, -5.12], [0.96, 3.85, 10.12], "STEEL_DARK", face_mats={"east": "HELM_SIDE", "north": "STEEL_BEVEL"}),
        make_cube([+4.04, 23.95, -3.55], [0.96, 4.65, 8.55],  "STEEL_DARK", face_mats={"east": "HELM_SIDE", "north": "CHEEK_GUARD"}),

        # Vành trán Netherite dày + chóp đỉnh trán chữ V (vẫn hở hoàn toàn đôi mắt người chơi ở y=27..28!)
        make_cube([-5.00, 29.10, -5.22], [10.00, 3.15, 0.72], "STEEL_DARK", face_mats={"north": "HELM_BROW", "up": "STEEL_BEVEL", "down": "OBS_DEEP"}),
        # Chóp nhọn sống mũi kiểu vương miện ác quỷ (ngay phía trên sống mũi y=27.6..29.6, KHÔNG che mắt ±1..±3!)
        make_centered_cube([0.0, 29.25, -5.45], [2.20, 2.35, 0.65], "CROWN_CREST", rot=[-8.0, 0.0, 0.0]),
        make_centered_cube([0.0, 27.95, -5.42], [1.15, 1.45, 0.58], "STEEL_BRIGHT", rot=[-4.0, 0.0, 0.0]),
    ]

    # Má giáp loe kiểu Netherite + 2 gai thái dương nhỏ sau gáy (giống boss ảnh 1 & 4)
    for s in (-1.0, 1.0):
        outer_f = "west" if s < 0 else "east"
        # Má giáp rèn dày vát cạnh (khung mặt chuẩn Netherite, hở hoàn toàn mắt/má/miệng!)
        head_cubes.append(make_centered_cube(
            [s * 4.55, 26.20, -3.95], [1.22, 4.45, 2.10], "CHEEK_GUARD",
            rot=[0.0, s * -10.0, s * -5.0],
            face_mats={"north": "CHEEK_GUARD", outer_f: "HELM_SIDE", "down": "STEEL_BEVEL"}
        ))
        # Gờ cằm vát chéo 2 bên hàm dưới (chừa hở sạch cằm giữa!)
        head_cubes.append(make_centered_cube(
            [s * 3.72, 24.42, -4.52], [1.85, 1.05, 1.12], "STEEL_BEVEL",
            rot=[0.0, s * -16.0, 0.0]
        ))
        # Gờ mày vát sắc góc trên ngoài
        head_cubes.append(make_centered_cube(
            [s * 4.35, 31.85, -5.28], [2.10, 0.95, 1.25], "STEEL_BEVEL",
            rot=[+8.0, s * -10.0, s * -16.0]
        ))
        # 2 gai gáy nhỏ chĩa xéo ra ngoài sau mũ (chi tiết góc sau ảnh 1!)
        head_cubes.append(make_centered_cube(
            [s * 4.80, 28.40, +4.65], [1.05, 0.95, 2.25], "SPIKE_METAL",
            rot=[+16.0, s * 22.0, 0.0]
        ))
        head_cubes.append(make_centered_cube(
            [s * 4.65, 26.20, +4.95], [0.90, 0.85, 1.95], "STEEL_DARK",
            rot=[-12.0, s * 18.0, 0.0]
        ))

    # CẶP SỪNG CỪU ĐỎ HUYẾT CONG XOẮN Y HỆT BOSS NIGHTMARE KNIGHT (Ảnh 1–4!):
    # Mọc từ hai bên trán trên -> uốn vòm cao qua đỉnh đầu -> mở rộng sang hai bên -> cuộn vòng ra sau & gập xuống móc nhẹ về trước!
    def build_boss_ram_horn(s: float):
        hc = []
        pts = [
            (s * 3.85, 31.55, -2.35),  # Gốc sừng trên thái dương
            (s * 5.65, 34.05, -1.65),  # Vươn lên & mở rộng
            (s * 7.85, 35.15, -0.10),  # Đỉnh vòm sừng vượt cao trên mũ
            (s * 9.35, 34.15, +1.95),  # Cuộn tròn ra ngoài & về sau
            (s * 9.30, 31.70, +2.65),  # Gập xuống bên má sau
            (s * 8.15, 30.20, +1.55),  # Thu gọn xoắn nhẹ
            (s * 6.95, 30.10, +0.20),  # Mũi sừng uốn móc về trước (đúng hình cừu ác quỷ ảnh 2–4!)
        ]
        specs = [
            (2.45, 2.45, "HORN_BASE"),
            (2.25, 2.25, "HORN_MID"),
            (2.00, 2.00, "HORN_TIP"),
            (1.72, 1.72, "HORN_MID"),
            (1.38, 1.38, "HORN_MID"),
            (0.95, 0.95, "HORN_TIP"),
        ]
        for idx, (w, h, mat) in enumerate(specs):
            hc.append(make_segment_cube(pts[idx], pts[idx + 1], w, h, mat, pad=0.72, face_mats=horn_faces))
        return hc

    horn_r_cubes = build_boss_ram_horn(-1.0)
    horn_l_cubes = build_boss_ram_horn(+1.0)

    # ==================== 2. ÁO GIÁP NGỰC KHUNG NETHERITE — LỒNG NGỰC MỞ TOANG ĐỂ LỘ TRÁI TIM NIGHTMARE CORE (`armorBody`) ====================
    body_cubes = [
        # Khối giáp thân chuẩn Netherite (+1.0 dày dặn: 9.75 x 12.20 x 5.75; mặt trước khoét hốc tối sâu lộ tim!)
        make_centered_cube(
            [0.0, 18.0, 0.0], [9.75, 12.20, 5.75], "STEEL_DARK",
            face_mats={
                "north": "CHEST_FRONT",
                "south": "CHEST_BACK",
                "east":  "CHEST_SIDE",
                "west":  "CHEST_SIDE",
                "up":    "STEEL_BEVEL",
                "down":  "OBS_DEEP",
            }
        ),
        # Cổ giáp cao bọc gáy & 2 bên cổ (Gorget — nối liền mũ <-> vai & tạo góc V sau lưng gắn áo choàng!)
        make_centered_cube([0.0, 24.05, +2.25], [8.00, 1.55, 1.60], "STEEL_BEVEL", rot=[+14.0, 0.0, 0.0]),
        make_centered_cube([-3.70, 23.95, +0.20], [1.65, 1.30, 5.05], "STEEL_MID", rot=[0.0, 0.0, +10.0]),
        make_centered_cube([+3.70, 23.95, +0.20], [1.65, 1.30, 5.05], "STEEL_MID", rot=[0.0, 0.0, -10.0]),

        # Thanh viền cổ ngực trên (clavicle arch) và khung đáy hốc tim giữ mở toang chính giữa ngực (y=14.8..22.0)!
        make_centered_cube([0.0, 22.85, -3.12], [7.60, 1.15, 0.64], "STEEL_MID", face_mats={"north": "STEEL_BEVEL", "up": "STEEL_BEVEL"}),
        make_centered_cube([0.0, 14.35, -3.08], [6.40, 0.92, 0.56], "STEEL_MID", face_mats={"north": "STEEL_BEVEL", "down": "OBS_DEEP"}),

        # Đai eo bọc thép đen dày chuẩn Netherite + khóa chữ V nhọn xuống bụng dưới!
        make_centered_cube(
            [0.0, 12.40, 0.0], [10.15, 1.65, 6.20], "OBS_DEEP",
            face_mats={"north": "WAIST_BELT", "south": "STEEL_DARK", "east": "STEEL_BEVEL", "west": "STEEL_BEVEL", "up": "STEEL_BEVEL"}
        ),
        make_centered_cube([0.0, 12.50, -3.26], [2.85, 1.75, 0.56], "BELT_BUCKLE", rot=[-6.0, 0.0, 0.0], face_mats={"north": "BELT_BUCKLE", "up": "STEEL_BEVEL"}),
        make_centered_cube([0.0, 11.20, -3.18], [1.95, 1.40, 0.50], "STEEL_BEVEL", rot=[-10.0, 0.0, 0.0]),

        # Chốt lưng chữ V thép đen nối cổ áo choàng hoàng gia (khớp chi tiết V sau cổ ảnh 1!)
        make_centered_cube([0.0, 22.10, +3.32], [6.80, 3.60, 0.92], "BACK_CLASP", rot=[+10.0, 0.0, 0.0], face_mats={"south": "BACK_CLASP", "up": "STEEL_BEVEL"}),
        make_centered_cube([0.0, 19.80, +3.24], [4.20, 2.40, 0.72], "STEEL_MID",  rot=[+14.0, 0.0, 0.0]),
    ]

    # HAI NỬA GIÁP NGỰC TÁCH MỞ RA HAI BÊN + 4 CẶP XƯƠNG SƯỜN BẠC UỐN MỞ ÔM LẤY TRÁI TIM NIGHTMARE CORE + 2 CHỐT RUBY!
    rib_claw_faces = {
        "north": "RIB_CLAW_SILVER", "south": "STEEL_DARK",
        "up": "RIB_CLAW_SILVER", "down": "STEEL_MID",
        "east": "RIB_CLAW_SILVER", "west": "RIB_CLAW_SILVER",
    }
    for s in (-1.0, 1.0):
        # 1. Nửa giáp ngực trên tách mở sang hai bên (xoay vát ra ngoài tạo cảm giác lồng ngực xé toang!)
        body_cubes.append(make_centered_cube(
            [s * 3.28, 20.65, -3.22], [3.35, 3.35, 0.76], "CHEST_CHEVRON",
            rot=[-5.0, s * -20.0, s * +6.0],
            face_mats={"north": "CHEST_CHEVRON", "up": "STEEL_BEVEL", "down": "OBS_DEEP"}
        ))
        # Nửa giáp ngực dưới tách mở ôm viền hốc tim
        body_cubes.append(make_centered_cube(
            [s * 3.50, 16.95, -3.12], [2.95, 3.55, 0.68], "STEEL_MID",
            rot=[+4.0, s * -18.0, s * -5.0],
            face_mats={"north": "CHEST_CHEVRON", "down": "STEEL_BEVEL"}
        ))

        # 2. 4 CẶP XƯƠNG SƯỜN LỒNG NGỰC MỞ TOANG (vươn cong ra trước rồi quặp nhẹ hướng về Trái Tim Nightmare Core!)
        open_ribs = [
            ((s * 3.65, 20.75, -2.85), (s * 2.95, 20.55, -3.85), (s * 1.72, 20.15, -4.08), 0.72, 0.56),
            ((s * 3.80, 19.15, -2.85), (s * 3.10, 19.00, -3.95), (s * 1.85, 18.85, -4.18), 0.76, 0.58),
            ((s * 3.75, 17.55, -2.85), (s * 3.05, 17.48, -3.92), (s * 1.80, 17.55, -4.14), 0.74, 0.56),
            ((s * 3.50, 16.05, -2.85), (s * 2.82, 16.10, -3.78), (s * 1.62, 16.38, -3.98), 0.68, 0.52),
        ]
        for p_root, p_mid, p_tip, w1, w2 in open_ribs:
            body_cubes.append(make_segment_cube(p_root, p_mid, w1, w1 * 0.92, "RIB_BONE", pad=0.22))
            body_cubes.append(make_segment_cube(p_mid, p_tip, w2, w2 * 0.90, "RIB_CLAW_SILVER", pad=0.18, face_mats=rib_claw_faces))

        # 3. 2 chốt tròn đá ruby đỏ rực trên xương quai xanh 2 bên (giữ chi tiết đặc trưng boss!)
        body_cubes.append(make_centered_cube(
            [s * 3.55, 22.15, -3.45], [2.05, 2.05, 0.56], "OBS_DEEP",
            rot=[-5.0, s * -12.0, 0.0], face_mats={"north": "STEEL_BEVEL"}
        ))
        body_cubes.append(make_centered_cube(
            [s * 3.55, 22.15, -3.72], [1.52, 1.52, 0.48], "RUBY_CLASP",
            rot=[-5.0, s * -12.0, +45.0]
        ))
        body_cubes.append(make_centered_cube(
            [s * 3.55, 22.15, -3.75], [1.45, 1.45, 0.44], "RUBY_CLASP",
            rot=[-5.0, s * -12.0, 0.0]
        ))

        # 4. Thanh xương sườn mạn sườn (bảo vệ hông dưới giáp ngực)
        for ry in (16.55, 15.30, 14.15):
            body_cubes.append(make_centered_cube(
                [s * 4.92, ry, -0.45], [0.46, 0.54, 3.60], "RIB_BONE",
                rot=[0.0, 0.0, s * -6.0]
            ))

    # ==================== 2-CORE. TRÁI TIM NIGHTMARE CORE PHÁT SÁNG & ĐẬP NHỊP GIỮA LỒNG NGỰC (`chest_core_heart`) ====================
    # Khớp 1:1 hình dáng & màu sắc của item `Nightmare Core` (bầu dục đỏ huyết viền ngoài + lõi hồng san hô #FFA28A rực sáng + 4 chấu bạc trên/dưới!)
    heart_y = 18.35
    heart_z = -3.12
    chest_core_heart_cubes = [
        # Khối trái tim Nightmare Core bầu dục đỏ huyết nổi 3D ngay giữa hốc lồng ngực mở
        make_centered_cube([0.0, heart_y, heart_z],        [2.45, 3.35, 1.12], "CORE_HEART_OUTER"),
        make_centered_cube([0.0, heart_y, heart_z - 0.05], [1.85, 4.10, 0.94], "CORE_HEART_OUTER"),
        make_centered_cube([0.0, heart_y, heart_z - 0.02], [1.22, 4.55, 0.76], "CORE_HEART_OUTER"),
        # Lõi phát sáng hồng san hô (#FFA28A -> #FFD8CC) nổi khối ở mặt trước trái tim (khớp 1:1 ảnh Nightmare Core!)
        make_centered_cube([0.0, heart_y + 0.05, heart_z - 0.24], [1.72, 2.75, 0.98], "CORE_HEART_INNER"),
        make_centered_cube([0.0, heart_y + 0.10, heart_z - 0.44], [1.15, 1.75, 0.80], "CORE_HEART_GLOW", rot=[0.0, 0.0, 0.0]),
        # Mũi pha lê cắt cạnh tâm lõi sáng
        make_centered_cube([0.0, heart_y + 0.12, heart_z - 0.52], [0.82, 0.82, 0.64], "CORE_HEART_GLOW", rot=[0.0, 0.0, +45.0]),
        # 4 chấu kẹp bạc mảnh ôm cực trên & dưới của Nightmare Core (giống cặp nanh bạc trên/dưới icon!)
        make_centered_cube([-0.46, heart_y + 2.18, heart_z - 0.20], [0.36, 0.72, 0.46], "RIB_CLAW_SILVER", rot=[+12.0, 0.0, -14.0]),
        make_centered_cube([+0.46, heart_y + 2.18, heart_z - 0.20], [0.36, 0.72, 0.46], "RIB_CLAW_SILVER", rot=[+12.0, 0.0, +14.0]),
        make_centered_cube([-0.46, heart_y - 2.18, heart_z - 0.20], [0.36, 0.72, 0.46], "RIB_CLAW_SILVER", rot=[-12.0, 0.0, +14.0]),
        make_centered_cube([+0.46, heart_y - 2.18, heart_z - 0.20], [0.36, 0.72, 0.46], "RIB_CLAW_SILVER", rot=[-12.0, 0.0, -14.0]),
    ]

    # ==================== 2a. KHỐ VẢI ĐỎ RÁCH CHÍNH GIỮA MẶT TRƯỚC (`tabard_front` dưới `armorBody`) ====================
    # Chi tiết chữ ký cực nổi bật ở mặt trước ảnh 2, 3, 4 — đặt z=-3.48 thoát hoàn toàn khỏi đùi & khiên gối!
    tabard_front_cubes = [
        make_cutout_plane([0.0, 7.65, -3.48], 3.30, 7.50, "TABARD_FRONT", "TABARD_FRONT", rot=[-5.0, 0.0, 0.0], thickness=0.14),
    ]

    # ==================== 2b. ÁO CHOÀNG HOÀNG GIA ĐỎ RÁCH TOÀN LƯNG 3 ĐOẠN (`cape_upper/mid/lower` dưới `armorBody`) ====================
    # Dáng chữ A xòe rộng uy nghi y hệt ảnh 1 (lưng boss!), ngả ra sau thoáng hoàn toàn khỏi gót ủng!
    cape_upper_cubes = [
        make_cutout_plane([0.0, 18.25, +4.00], 9.20, 8.60, "CAPE_UPPER", "CAPE_UPPER", rot=[+13.0, 0.0, 0.0], thickness=0.18),
    ]
    cape_mid_cubes = [
        make_cutout_plane([0.0, 10.75, +6.10], 13.20, 8.30, "CAPE_MID", "CAPE_MID", rot=[+18.0, 0.0, 0.0], thickness=0.18),
    ]
    cape_lower_cubes = [
        make_cutout_plane([0.0, 3.75, +8.85], 17.40, 8.00, "CAPE_LOWER", "CAPE_LOWER", rot=[+24.0, 0.0, 0.0], thickness=0.18),
    ]

    # ==================== 2c. ĐÔI CÁNH DƠI ÁC QUỶ 3 KHỚP ĐỐI XỨNG (`wing_l/r_upper/mid/tip` dưới `armorBody`) ====================
    # Dáng cánh dơi cao vút, khung xương sắt đen mảnh sắc có đốt khớp, 4 nan xương vươn dài xuống đầu vuốt đỏ + màng đỏ rách có lỗ!
    WING_YAW_DEG = 18.0
    WING_COS = math.cos(math.radians(WING_YAW_DEG))
    WING_SIN = math.sin(math.radians(WING_YAW_DEG))

    def wing_pt(s: float, t: float, y: float):
        return (s * (2.35 + t * WING_COS), y, 3.30 + t * WING_SIN)

    def build_demon_wing(s: float):
        f_tag = "R" if s < 0 else "L"
        b_tag = "L" if s < 0 else "R"
        # Khớp cánh vươn cao lên đỉnh vuốt cánh giống ảnh 1–4!
        p_sh = wing_pt(s, 0.0,  21.6)
        p_el = wing_pt(s, 6.4,  26.0)
        p_wr = wing_pt(s, 13.4, 29.6)
        p_tp = wing_pt(s, 21.8, 25.4)

        # 4 đầu nan xương cánh dài chĩa xuống với móng vuốt đỏ sắc nhọn
        f1_tip = wing_pt(s, 6.1,  12.4)
        f2_tip = wing_pt(s, 10.2, 11.8)
        f3_tip = wing_pt(s, 14.4, 12.6)
        f4_tip = wing_pt(s, 18.8, 15.2)

        def add_claw_tip(p_from, p_to, w):
            dx = p_to[0] - p_from[0]
            dy = p_to[1] - p_from[1]
            dz = p_to[2] - p_from[2]
            L = max(1e-5, math.sqrt(dx * dx + dy * dy + dz * dz))
            p_ext = (p_to[0] + dx / L * 1.45, p_to[1] + dy / L * 1.45, p_to[2] + dz / L * 1.45)
            return make_segment_cube(p_to, p_ext, w * 0.75, w * 0.75, "CLAW_BLOOD", pad=0.18, face_mats=claw_faces)

        upper = [
            make_segment_cube(p_sh, p_el, 1.28, 1.28, "WING_BONE", pad=0.65, face_mats=bone_faces),
            make_centered_cube(p_el, [1.55, 1.55, 1.55], "WING_JOINT"),
            make_segment_cube(p_el, f1_tip, 0.62, 0.62, "WING_BONE", pad=0.25, face_mats=bone_faces),
            add_claw_tip(p_el, f1_tip, 0.62),
            make_cutout_plane(
                wing_pt(s, 3.20, 19.30), 6.50, 13.80,
                f"WING_MEM_INNER_{f_tag}", f"WING_MEM_INNER_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]

        # Ngón cái đỉnh cánh móc ngược lên đầu đỏ rực (rất đặc trưng ở đỉnh 2 cánh trong ảnh 1–4!)
        thumb_mid = (p_wr[0] - s * 0.55, p_wr[1] + 1.95, p_wr[2] - 0.55)
        thumb_tip = (p_wr[0] - s * 2.10, p_wr[1] + 2.65, p_wr[2] - 1.25)
        mid = [
            make_segment_cube(p_el, p_wr, 1.22, 1.22, "WING_BONE", pad=0.65, face_mats=bone_faces),
            make_centered_cube(p_wr, [1.52, 1.52, 1.52], "WING_JOINT"),
            make_segment_cube(p_wr, thumb_mid, 0.90, 0.90, "WING_BONE", pad=0.45, face_mats=bone_faces),
            make_segment_cube(thumb_mid, thumb_tip, 0.64, 0.64, "CLAW_BLOOD", pad=0.30, face_mats=claw_faces),
            make_segment_cube(p_wr, f2_tip, 0.58, 0.58, "WING_BONE", pad=0.22, face_mats=bone_faces),
            add_claw_tip(p_wr, f2_tip, 0.58),
            make_segment_cube(p_wr, f3_tip, 0.56, 0.56, "WING_BONE", pad=0.22, face_mats=bone_faces),
            add_claw_tip(p_wr, f3_tip, 0.56),
            make_cutout_plane(
                wing_pt(s, 9.90, 20.80), 8.20, 18.20,
                f"WING_MEM_MID_{f_tag}", f"WING_MEM_MID_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]

        p_tip_claw = wing_pt(s, 23.2, 24.0)
        tip = [
            make_segment_cube(p_wr, p_tp, 1.05, 1.05, "WING_BONE", pad=0.55, face_mats=bone_faces),
            make_segment_cube(p_tp, p_tip_claw, 0.68, 0.68, "CLAW_BLOOD", pad=0.25, face_mats=claw_faces),
            make_segment_cube(p_wr, f4_tip, 0.52, 0.52, "WING_BONE", pad=0.20, face_mats=bone_faces),
            add_claw_tip(p_wr, f4_tip, 0.52),
            make_cutout_plane(
                wing_pt(s, 17.60, 21.60), 8.60, 16.40,
                f"WING_MEM_OUTER_{f_tag}", f"WING_MEM_OUTER_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]
        return upper, mid, tip

    wing_r_upper, wing_r_mid, wing_r_tip = build_demon_wing(-1.0)
    wing_l_upper, wing_l_mid, wing_l_tip = build_demon_wing(+1.0)

    # ==================== 3. VAI GIÁP RỘNG ĐỐI XỨNG CHUẨN NETHERITE + GAI VAI + GĂNG GAI (`armorRightArm`, `armorLeftArm`) ====================
    def build_knight_arm(s: float):
        cx = s * 6.0
        outer_face = "west" if s < 0 else "east"
        inner_face = "east" if s < 0 else "west"
        return [
            # 1. Ống tay giáp xích bọc kín 100% bắp tay & khuỷu tay (gọn 4.66 -> làm nổi bật phom Vai Giáp 6.80 & Găng 5.85!)
            make_centered_cube(
                [cx, 18.40, 0.0], [4.66, 11.40, 4.66], "ARM_SLEEVE",
                face_mats={"up": "ARM_CAP", "down": "ARM_CAP"}
            ),
            # Đai đỏ thắt bắp tay đặc trưng của boss (thấy rõ ở cả ảnh 1–4!)
            make_centered_cube([cx, 19.35, 0.0], [4.98, 1.05, 4.98], "ARM_STRAP_RED"),

            # 2. VAI GIÁP KHUNG NETHERITE ĐỐI XỨNG RỘNG DÀY (`6.80 x 3.90 x 6.50`, xòe ngoài `s*0.72`!)
            make_centered_cube(
                [cx + s * 0.72, 22.35, 0.0], [6.80, 3.90, 6.50], "PAULDRON_OUT",
                rot=[0.0, 0.0, s * -8.0],
                face_mats={
                    "north":    "PAULDRON_FRONT",
                    "south":    "PAULDRON_FRONT",
                    outer_face: "PAULDRON_OUT",
                    inner_face: "STEEL_DARK",
                    "up":       "PAULDRON_TOP",
                    "down":     "STEEL_BEVEL",
                }
            ),
            # Tầng lá vai giáp trên vát cạnh sắc (chồng tầng gothic!)
            make_centered_cube(
                [cx + s * 1.12, 24.45, 0.0], [5.50, 1.35, 6.00], "PAULDRON_TOP",
                rot=[0.0, 0.0, s * -16.0],
                face_mats={"up": "PAULDRON_TOP", "north": "STEEL_BEVEL", "south": "STEEL_BEVEL", outer_face: "STEEL_BEVEL"}
            ),
            # Tầng lá vai giáp dưới ôm xòe xuống bắp tay trên
            make_centered_cube(
                [cx + s * 0.56, 20.15, 0.0], [6.25, 1.25, 5.95], "STEEL_MID",
                rot=[0.0, 0.0, s * -11.0],
                face_mats={"north": "STEEL_MID", "south": "STEEL_MID", outer_face: "STEEL_BEVEL", "up": "STEEL_BEVEL", "down": "OBS_DEEP"}
            ),
            # GAI VAI CAO GOTHIC VƯƠN THẲNG LÊN & GAI NGOÀI CHĨA XÉO (cực giống 2 vai boss ảnh 2, 3, 4!)
            make_segment_cube(
                (cx + s * 0.25, 24.65, -0.35), (cx + s * 0.85, 28.40, -0.15),
                1.28, 1.28, "SPIKE_METAL", pad=0.35, face_mats={"up": "STEEL_BEVEL", "north": "STEEL_BRIGHT"}
            ),
            make_segment_cube(
                (cx + s * 0.85, 28.40, -0.15), (cx + s * 1.15, 29.85, +0.05),
                0.68, 0.68, "STEEL_BEVEL", pad=0.22
            ),
            make_segment_cube(
                (cx + s * 3.15, 23.15, 0.0), (cx + s * 5.25, 24.60, +0.25),
                1.18, 1.15, "SPIKE_METAL", pad=0.32
            ),
            make_segment_cube(
                (cx + s * 5.25, 24.60, +0.25), (cx + s * 6.35, 25.35, +0.40),
                0.62, 0.60, "STEEL_BEVEL", pad=0.18
            ),

            # 3. GĂNG CẲNG TAY GOTHIC LOE DÀY (`5.85 x 5.00 x 5.85`) + CÁNH KHUỶU NHỌN + 3 GAI CẲNG TAY (khớp ảnh 1–4!)
            make_centered_cube(
                [cx + s * 0.14, 15.05, 0.0], [5.85, 4.85, 5.85], "GAUNTLET_OUT",
                face_mats={
                    "north":    "GAUNTLET_FRONT",
                    "south":    "GAUNTLET_FRONT",
                    outer_face: "GAUNTLET_OUT",
                    inner_face: "STEEL_DARK",
                    "up":       "STEEL_BEVEL",
                    "down":     "OBS_DEEP",
                }
            ),
            # Vành cổ găng trên loe sắc
            make_centered_cube([cx + s * 0.18, 17.35, 0.0], [6.15, 0.78, 6.15], "STEEL_BEVEL"),
            # Cánh khuỷu nhọn vươn lên cao bên ngoài khuỷu tay (chi tiết cực chất của tay boss ảnh 2 & 3!)
            make_segment_cube(
                (cx + s * 2.75, 17.20, +0.45), (cx + s * 3.65, 20.15, +0.95),
                1.08, 1.22, "SPIKE_METAL", pad=0.30, face_mats={"up": "STEEL_BEVEL", outer_face: "STEEL_BRIGHT"}
            ),
            # 3 gai nhọn dọc mép ngoài cẳng tay (khớp ảnh 1 & 4!)
            make_centered_cube([cx + s * 3.30, 16.35, +0.35], [1.10, 0.90, 0.92], "SPIKE_METAL", rot=[0.0, 0.0, s * +22.0]),
            make_centered_cube([cx + s * 3.35, 14.95, +0.35], [1.18, 0.92, 0.92], "SPIKE_METAL", rot=[0.0, 0.0, s * +24.0]),
            make_centered_cube([cx + s * 3.25, 13.55, +0.35], [1.02, 0.85, 0.88], "CLAW_BLOOD",  rot=[0.0, 0.0, s * +22.0]),
        ]

    rarm_cubes = build_knight_arm(-1.0)
    larm_cubes = build_knight_arm(+1.0)

    # ==================== 4. QUẦN GIÁP ĐÙI ĐA TẦNG + KHIÊN GỐI NHỌN (`armorRightLeg`, `armorLeftLeg`) ====================
    def build_knight_leg(s: float):
        cx = s * 2.0
        outer_face = "west" if s < 0 else "east"
        inner_face = "east" if s < 0 else "west"
        return [
            # Khung quần Netherite (+0.5 gọn hơn ủng để làm rõ bậc ủng!)
            make_centered_cube(
                [cx, 8.45, 0.0], [4.78, 7.10, 4.78], "STEEL_DARK",
                face_mats={
                    "north":    "THIGH_FRONT",
                    "south":    "THIGH_BACK",
                    outer_face: "THIGH_SIDE",
                    inner_face: "OBS_DEEP",
                    "up":       "OBS_DEEP",
                    "down":     "OBS_DEEP",
                }
            ),
            # Tấm giáp hông góc cạnh (Hip Tasset) 2 tầng xòe bên đùi trên (khớp ảnh 2–4!)
            make_centered_cube(
                [cx + s * 2.65, 9.95, 0.0], [0.92, 3.85, 4.55], "HIP_TASSET",
                rot=[0.0, 0.0, s * -12.0],
                face_mats={outer_face: "HIP_TASSET", "north": "STEEL_BEVEL", "south": "STEEL_BEVEL", "down": "STEEL_BEVEL"}
            ),
            # Tấm giáp đùi trước vát tầng chữ V (layered cuisse plates)
            make_centered_cube([cx + s * 0.14, 9.75, -2.58], [3.85, 2.35, 0.62], "STEEL_MID", rot=[-8.0, s * -6.0, 0.0], face_mats={"north": "HIP_TASSET", "down": "STEEL_BEVEL"}),
            make_centered_cube([cx + s * 0.16, 7.85, -2.56], [3.65, 1.95, 0.56], "STEEL_MID", rot=[-6.0, s * -6.0, 0.0], face_mats={"north": "STEEL_MID",  "down": "STEEL_BEVEL"}),
            # Khiên đầu gối nhọn (Poleyn) + vây thép bên ngoài gối (giống ảnh 2–4!)
            make_centered_cube([cx, 6.30, -2.86], [3.15, 2.35, 0.86], "KNEE_GUARD", rot=[-10.0, 0.0, 0.0], face_mats={"north": "KNEE_GUARD", "up": "STEEL_BEVEL"}),
            make_centered_cube([cx, 6.20, -3.34], [1.15, 1.50, 0.52], "STEEL_BEVEL", rot=[-16.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 2.58, 6.35, -0.45], [0.76, 1.95, 2.25], "SPIKE_METAL", rot=[0.0, s * -18.0, s * -14.0]),
        ]

    rleg_cubes = build_knight_leg(-1.0)
    lleg_cubes = build_knight_leg(+1.0)

    # ==================== 5. ỦNG GIÁP NẶNG KHUNG NETHERITE + 3 MÓNG VUỐT QUỶ ĐỎ (`armorRightBoot`, `armorLeftBoot`) ====================
    def build_knight_boot(s: float):
        cx = s * 2.0
        tag = "R" if s < 0 else "L"
        outer_face = "west" if s < 0 else "east"
        inner_face = "east" if s < 0 else "west"
        cubes = [
            # Thân ủng dày nổi khối chuẩn Netherite (mép trong giữ |x|>=0.08 -> 0 chạm nhau, mép ngoài vươn tới 5.20!)
            make_centered_cube(
                [cx + s * 0.64, 2.52, -0.08], [5.12, 5.26, 5.88], "STEEL_DARK",
                face_mats={
                    "north":    "BOOT_FRONT",
                    "south":    "BOOT_BACK",
                    outer_face: f"BOOT_SIDE_{tag}",
                    inner_face: "OBS_DEEP",
                    "down":     "BOOT_SOLE",
                    "up":       "STEEL_BEVEL",
                }
            ),
            # Cổ ủng loe kiểu Netherite (bước nhảy khối cực nét trên ống chân!)
            make_centered_cube(
                [cx + s * 0.75, 4.92, -0.08], [5.40, 1.40, 6.26], "BOOT_CUFF",
                face_mats={
                    "north":    "BOOT_CUFF",
                    "south":    "BOOT_CUFF",
                    outer_face: "BOOT_CUFF",
                    inner_face: "OBS_DEEP",
                    "up":       "STEEL_BEVEL",
                    "down":     "OBS_DEEP",
                }
            ),
            # Đế ủng bọc thép dày + mũi gờ ống chân + gót gai sau
            make_centered_cube([cx + s * 0.66, 0.50, -0.15], [5.22, 1.00, 6.18], "OBS_DEEP", face_mats={"down": "BOOT_SOLE", "north": "STEEL_BEVEL", outer_face: "STEEL_BEVEL"}),
            make_centered_cube([cx + s * 0.58, 3.80, -3.14], [1.75, 2.75, 0.56], "STEEL_BEVEL", rot=[-8.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 0.66, 1.55, +3.18], [2.40, 1.65, 0.90], "STEEL_MID",   rot=[+16.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 0.62, 1.40, -3.22], [4.50, 1.90, 1.05], "STEEL_MID",   rot=[+10.0, 0.0, 0.0], face_mats={"up": "STEEL_BEVEL"}),
        ]
        # 3 móng vuốt quỷ mũi đỏ mỗi bàn chân (khớp chân vuốt ác quỷ boss ảnh 2–4!)
        toe_specs = [
            (cx + s * 0.55 - 1.38, 1.02, -3.65, -1.65, -4.95, 0.84),
            (cx + s * 0.55,        1.08, -3.72,  0.00, -5.35, 0.94),
            (cx + s * 0.55 + 1.38, 1.02, -3.65, +1.65, -4.95, 0.84),
        ]
        for tx0, ty0, tz0, dx_out, tz1, w in toe_specs:
            p0 = (tx0, ty0, tz0)
            p1 = (tx0 + (dx_out - (tx0 - (cx + s * 0.55))) * 0.20, ty0 - 0.16, tz0 - 0.68)
            p2 = (cx + s * 0.55 + dx_out, 0.24, tz1)
            cubes.append(make_segment_cube(p0, p1, w, w * 0.90, "STEEL_MID", pad=0.22))
            cubes.append(make_segment_cube(p1, p2, w * 0.68, w * 0.64, "CLAW_BLOOD", pad=0.16, face_mats=claw_faces))
        return cubes

    rboot_cubes = build_knight_boot(-1.0)
    lboot_cubes = build_knight_boot(+1.0)

    bones = [
        {"name": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},
        {"name": "horn_r",    "parent": "armorHead", "pivot": [-3.85, 31.55, -2.35], "cubes": horn_r_cubes},
        {"name": "horn_l",    "parent": "armorHead", "pivot": [+3.85, 31.55, -2.35], "cubes": horn_l_cubes},

        {"name": "bipedBody",        "pivot": [0, 24, 0]},
        {"name": "armorBody",        "parent": "bipedBody",  "pivot": [0, 24, 0],     "cubes": body_cubes},
        {"name": "chest_core_heart", "parent": "armorBody",  "pivot": [0, heart_y, heart_z], "cubes": chest_core_heart_cubes},
        {"name": "tabard_front",     "parent": "armorBody",  "pivot": [0, 11.5, -3.35], "cubes": tabard_front_cubes},
        {"name": "cape_upper",   "parent": "armorBody",  "pivot": [0, 22.3, +3.40], "cubes": cape_upper_cubes},
        {"name": "cape_mid",     "parent": "cape_upper", "pivot": [0, 14.4, +5.05], "cubes": cape_mid_cubes},
        {"name": "cape_lower",   "parent": "cape_mid",   "pivot": [0, 7.0,  +7.30], "cubes": cape_lower_cubes},

        # Đôi cánh dơi ác quỷ 3 khớp gắn trực tiếp lên armorBody (đối xứng gương 100%)
        {"name": "wing_r_upper", "parent": "armorBody",    "pivot": list(wing_pt(-1.0,  0.0, 21.6)), "cubes": wing_r_upper},
        {"name": "wing_r_mid",   "parent": "wing_r_upper", "pivot": list(wing_pt(-1.0,  6.4, 26.0)), "cubes": wing_r_mid},
        {"name": "wing_r_tip",   "parent": "wing_r_mid",   "pivot": list(wing_pt(-1.0, 13.4, 29.6)), "cubes": wing_r_tip},

        {"name": "wing_l_upper", "parent": "armorBody",    "pivot": list(wing_pt(+1.0,  0.0, 21.6)), "cubes": wing_l_upper},
        {"name": "wing_l_mid",   "parent": "wing_l_upper", "pivot": list(wing_pt(+1.0,  6.4, 26.0)), "cubes": wing_l_mid},
        {"name": "wing_l_tip",   "parent": "wing_l_mid",   "pivot": list(wing_pt(+1.0, 13.4, 29.6)), "cubes": wing_l_tip},

        {"name": "bipedRightArm", "pivot": [-5, 22, 0]},
        {"name": "armorRightArm", "parent": "bipedRightArm", "pivot": [-5, 22, 0], "cubes": rarm_cubes},

        {"name": "bipedLeftArm", "pivot": [5, 22, 0]},
        {"name": "armorLeftArm", "parent": "bipedLeftArm", "pivot": [5, 22, 0], "cubes": larm_cubes},

        {"name": "bipedRightLeg",  "pivot": [-2, 12, 0]},
        {"name": "armorRightLeg",  "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rleg_cubes},
        {"name": "armorRightBoot", "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rboot_cubes},

        {"name": "bipedLeftLeg",  "pivot": [2, 12, 0]},
        {"name": "armorLeftLeg",  "parent": "bipedLeftLeg", "pivot": [2, 12, 0], "cubes": lleg_cubes},
        {"name": "armorLeftBoot", "parent": "bipedLeftLeg", "pivot": [2, 12, 0], "cubes": lboot_cubes},
    ]

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.nightmare_armor",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 4.2,
                    "visible_bounds_height": 3.8,
                    "visible_bounds_offset": [0.0, 1.4, 0.3],
                },
                "bones": bones,
            }
        ],
    }
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out = ASSETS / "geo" / "armor" / "nightmare_armor.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, 128x128 1:1 UV)")
    return geo, total_cubes


def build_nightmare_armor_animations():
    """Giữ clip rỗng để toàn bộ chuyển động cánh/áo choàng/khố do NightmareArmorModel.setCustomAnimations điều khiển 1 nguồn duy nhất."""
    anims = {
        "animation.nightmare_armor.idle": {
            "loop": True,
            "animation_length": 2.0,
            "bones": {},
        }
    }
    out = ASSETS / "animations" / "armor" / "nightmare_armor.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out}")


def build_nightmare_armor_texture_128():
    verify_uv_and_padding()
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    # Bảng màu Nightmare Knight Boss chuẩn ảnh 1–4:
    # - Giáp thép đen gunmetal sắc nét: rãnh sâu #101218 -> bản giáp #1E222C / #2F3544 -> cạnh vát sáng #525C70 / #7F8B9F / #B0BACB
    # - Sừng cừu & cánh đỏ huyết rách: #4E0B15 -> #851828 -> #B8293D -> sáng #DA4658
    # - Áo choàng & khố đỏ thẫm hoàng gia: #3E0810 -> #6E101E -> #A01B2E -> nếp sáng #C92B40
    # - 2 chốt Ruby ngực đỏ rực: #6E0A16 -> #B81426 -> #EB263C -> phản sáng #FF7A88
    # - Xương sườn giáp màu đồng xám/xương cổ: #4A3832 -> #745B50 -> #9C8072
    P = {
        ".": (0, 0, 0, 0),
        "0": (12, 13, 17, 255),      # Rãnh sâu đen
        "1": (23, 25, 32, 255),      # Thép gunmetal obsidian tối (khớp Netherite + boss!)
        "2": (38, 41, 52, 255),      # Thép gunmetal trung
        "3": (58, 63, 77, 255),      # Thép gunmetal sáng
        "4": (88, 96, 114, 255),     # Cạnh vát thép sáng sắc
        "5": (132, 142, 160, 255),   # Ánh kim viền cạnh

        "h": (78, 12, 22, 255),      # Sừng đỏ tối
        "H": (132, 24, 38, 255),     # Sừng đỏ trung
        "R": (182, 42, 58, 255),     # Sừng đỏ sáng
        "T": (216, 72, 86, 255),     # Đỉnh sáng sừng

        "r": (118, 12, 24, 255),     # Ruby tối
        "E": (204, 24, 42, 255),     # Ruby rực
        "G": (246, 66, 84, 255),     # Ruby tâm sáng
        "P": (255, 176, 184, 255),   # Điểm phản quang ruby

        "c": (66, 9, 18, 255),       # Áo choàng đỏ tối (nếp sâu)
        "C": (118, 17, 30, 255),     # Áo choàng đỏ trung
        "M": (164, 28, 46, 255),     # Áo choàng đỏ sáng
        "L": (198, 44, 62, 255),     # Đỉnh nếp gấp áo choàng

        "w": (78, 14, 24, 255),      # Màng cánh đỏ sẫm (viền xương & mép rách)
        "W": (126, 28, 42, 255),     # Màng cánh đỏ huyết mượt
        "m": (166, 48, 64, 255),     # Tâm màng cánh đỏ xuyên sáng mịn (0 kẻ ô caro!)

        "b": (68, 50, 44, 255),      # Xương sườn tối
        "B": (112, 86, 76, 255),     # Xương sườn trung
        "I": (152, 122, 108, 255),   # Xương sườn sáng

        "a": (18, 19, 25, 255),      # Giáp xích ống tay tối
        "A": (30, 33, 42, 255),      # Giáp xích ống tay trung
        "v": (46, 50, 62, 255),      # Giáp xích ống tay sáng

        # Bảng màu chuẩn 1:1 từ item `Nightmare Core` (đỏ huyết -> đỏ rực -> san hô #FFA28A -> tâm trắng san hô)
        "k": (127, 28, 38, 255),     # #7F1C26 viền ngoài Nightmare Core
        "K": (184, 41, 52, 255),     # #B82934 thân đỏ Nightmare Core
        "q": (232, 74, 86, 255),     # Đỏ san hô sáng
        "Q": (255, 162, 138, 255),   # #FFA28A lõi sáng đặc trưng Nightmare Core!
        "F": (255, 224, 212, 255),   # Tâm sáng trắng-san hô đập nhịp
        "S": (133, 140, 152, 255),   # #858C98 xương sườn bạc trung
        "Y": (182, 189, 201, 255),   # #B6BDC9 chấu xương sườn bạc sáng
    }
    GLOW_CHARS = {
        "k": (132, 20, 34, 175),
        "K": (205, 38, 52, 225),
        "q": (245, 78, 90, 245),
        "Q": (255, 168, 144, 255),
        "F": (255, 232, 220, 255),
    }

    def paint(key: str, rows, emissive: bool = False):
        x0, y0, w, h = REG[key]
        assert len(rows) == h, f"{key}: expected {h} rows, got {len(rows)}"
        for y, row in enumerate(rows):
            assert len(row) == w, f"{key} row {y}: expected {w} chars, got {len(row)}"
            for x, ch in enumerate(row):
                diff[y0 + y, x0 + x] = P[ch]
                if emissive and ch in GLOW_CHARS:
                    glow[y0 + y, x0 + x] = GLOW_CHARS[ch]

    # 1. Mũ + vật liệu nền
    paint("HELM_TOP", [
        "12344321",
        "23433432",
        "32233223",
        "32344323",
        "32344323",
        "32233223",
        "23433432",
        "12344321",
    ])
    paint("HELM_BACK", [
        "12344321",
        "23455432",
        "23233232",
        "12233221",
        "12344321",
        "12233221",
        "11233211",
        "01222210",
    ])
    paint("HELM_SIDE", [
        "12344321",
        "23443321",
        "23322321",
        "12233211",
        "12343211",
        "12232110",
        "11222110",
        "01221100",
    ])
    paint("HELM_BROW", [
        "23455432",
        "12344321",
        "12233221",
        "01244210",
    ])
    paint("CHEEK_GUARD", [
        "2342",
        "1232",
        "1231",
        "0141",
    ])
    paint("CROWN_CREST", [
        "2452",
        "2342",
        "1341",
        "0230",
    ])

    paint("STEEL_DARK",   ["1221", "1121", "1211", "0110"])
    paint("STEEL_MID",    ["2332", "2232", "1231", "1221"])
    paint("STEEL_BRIGHT", ["3454", "3443", "2343", "2332"])
    paint("STEEL_BEVEL",  ["4554", "3443", "2332", "1221"])
    paint("OBS_DEEP",     ["0110", "1111", "0110", "0000"])
    paint("HORN_BASE",    ["HHHh", "hHHh", "hhhh", "hhHh"])
    paint("HORN_MID",     ["RRRH", "HRRH", "hHHh", "HRRH"])
    paint("HORN_TIP",     ["TTTR", "RRTR", "HRRH", "HHRH"])
    paint("RUBY_CLASP",   ["rEEr", "EGPE", "EGGE", "rEEr"])
    paint("CLAW_BLOOD",   ["TTRH", "RRHh", "HHhh", "hh00"])
    paint("WING_BONE",    ["0231", "1242", "0131", "0120"])
    paint("WING_JOINT",   ["1342", "2453", "1342", "0121"])
    paint("RIB_BONE",     ["bBBI", "BIIB", "bBBb", "0bb0"])
    paint("ARM_STRAP_RED",["cCMc", "CMMC"])
    paint("ARM_CAP",      ["aaaa", "aAAa", "aAAa", "aaaa"])
    paint("BACK_CLASP", [
        "245542",
        "134431",
        "123321",
        "013310",
        "002200",
        "001100",
    ])
    paint("BELT_BUCKLE",  ["3553", "2442", "1331", "0220"])
    paint("SPIKE_METAL",  ["3453", "2342", "1231", "0120"])
    paint("CORE_HEART_OUTER", [
        "kKKk",
        "KqqK",
        "KqQK",
        "KqQK",
        "KqqK",
        "kKKk",
    ], emissive=True)
    paint("CORE_HEART_INNER", [
        "KqqK",
        "qQQq",
        "QFFQ",
        "QFFQ",
        "qQQq",
        "KqqK",
    ], emissive=True)
    paint("CORE_HEART_GLOW", [
        "qQQq",
        "QFFQ",
        "QFFQ",
        "qQQq",
    ], emissive=True)
    paint("RIB_CLAW_SILVER", [
        "SYY4",
        "YSY3",
        "SSY2",
        "2SS1",
    ])
    paint("EMPTY_TRANS",  ["."])

    # 2. Ngực khoét hốc lồng ngực đen sâu rực ánh đỏ bên trong (làm nổi bật 100% Trái Tim Nightmare Core!)
    paint("CHEST_FRONT", [
        "12344321",
        "23000032",
        "100kk001",
        "10kKKk01",
        "10kKqK01",
        "10kKqK01",
        "10kKKk01",
        "100kk001",
        "01000010",
        "01233210",
        "12344321",
        "01222210",
    ], emissive=True)
    paint("CHEST_BACK", [
        "12344321",
        "23433432",
        "12344321",
        "12233221",
        "12233221",
        "12344321",
        "12233221",
        "12233221",
        "12344321",
        "12233221",
        "11233211",
        "01222210",
    ])
    paint("CHEST_SIDE", [
        "1231", "2342", "1231", "1231",
        "1231", "0120", "bBBI", "0120",
        "bBBI", "0120", "1231", "0120",
    ])
    paint("CHEST_CHEVRON", [
        "345543",
        "234432",
        "123321",
        "023320",
        "134431",
        "012210",
    ])
    paint("PAULDRON_TOP", [
        "234542",
        "345432",
        "234321",
        "234321",
        "123431",
        "012320",
    ])
    paint("PAULDRON_OUT", [
        "345543",
        "234432",
        "123321",
        "234432",
        "123321",
        "014410",
    ])
    paint("PAULDRON_FRONT", [
        "345543",
        "234432",
        "123321",
        "124421",
        "123321",
        "013310",
    ])
    paint("ARM_SLEEVE", [
        "aAAa", "avva", "aAAa", "avva",
        "aAAa", "a33a", "aAAa", "avva",
        "aAAa", "avva", "aaaa",
    ])
    paint("GAUNTLET_OUT", [
        "345543",
        "234432",
        "123321",
        "234432",
        "123321",
        "024420",
    ])
    paint("GAUNTLET_FRONT", [
        "345543",
        "234432",
        "123321",
        "123321",
        "124421",
        "013310",
    ])
    paint("WAIST_BELT", [
        "2345555432",
        "1234444321",
    ])
    paint("WAIST_RIBS", [
        "0bB11Bb0",
        "bBI00IBb",
        "0bB11Bb0",
        "bBI00IBb",
    ])
    paint("THIGH_FRONT", [
        "2342", "1231", "2342", "1231",
        "2342", "1231", "1231", "0120",
    ])
    paint("THIGH_SIDE", [
        "1341", "2342", "1231", "2342",
        "1231", "2332", "1231", "0120",
    ])
    paint("THIGH_BACK", [
        "1231", "1221", "1231", "1221",
        "1231", "1221", "1221", "0110",
    ])
    paint("HIP_TASSET", [
        "3453",
        "2342",
        "1231",
        "2342",
        "0230",
    ])
    paint("KNEE_GUARD", [
        "2452",
        "2342",
        "1341",
        "0230",
    ])
    paint("BOOT_CUFF", [
        "455554",
        "234432",
        "123321",
    ])
    paint("BOOT_FRONT", [
        "34543",
        "23432",
        "23432",
        "12321",
        "12421",
        "01310",
    ])
    boot_side_r = [
        "34542", "23432", "12331",
        "23421", "12321", "01420",
    ]
    paint("BOOT_SIDE_R", boot_side_r)
    paint("BOOT_SIDE_L", [row[::-1] for row in boot_side_r])
    paint("BOOT_BACK", [
        "34543", "23432", "12321",
        "12321", "12321", "01410",
    ])
    paint("BOOT_SOLE", [
        "01110", "12321", "13231", "12321", "01110",
    ])

    # 3. 6 TẤM MÀNG CÁNH DƠI ĐỎ HUYẾT RÁCH CÓ LỖ CHIẾN TRẬN (khớp y hệt ảnh 1–4!)
    def arch(t, ta, ya, tb, yb, lift):
        u = max(0.0, min(1.0, (t - ta) / max(1e-5, tb - ta)))
        return ya + (yb - ya) * u + lift * 4.0 * u * (1.0 - u)

    def build_wing_mem_rows(w: int, h: int, t0: float, t1: float, y0: float, y1: float, top_fn, bot_fn, holes):
        rows = []
        for r in range(h):
            y = y1 - (r + 0.5) / h * (y1 - y0)
            chars = []
            for c in range(w):
                t = t1 - (c + 0.5) / w * (t1 - t0)
                yt = top_fn(t)
                yb = bot_fn(t)
                # Răng cưa rách nhẹ dọc mép dưới
                tatter = 0.75 if ((c * 5 + 1) % 3 == 0) else 0.0
                if (yb + tatter) <= y <= yt:
                    # Lỗ rách chiến trận trong màng cánh (giống hệt ảnh 1–4!)
                    in_hole = any((c - hc) ** 2 + (r - hr) ** 2 <= rad2 for hc, hr, rad2 in holes)
                    if in_hole:
                        chars.append(".")
                    else:
                        d_top = yt - y
                        d_bot = y - (yb + tatter)
                        span = max(1.0, yt - (yb + tatter))
                        v_frac = d_top / span
                        edge_dist = min(c, w - 1 - c)
                        if d_top < 1.35 or d_bot < 1.05 or edge_dist == 0:
                            chars.append("w")
                        elif 0.22 <= v_frac <= 0.68 and edge_dist >= 2:
                            chars.append("m")
                        else:
                            chars.append("W")
                else:
                    chars.append(".")
            rows.append("".join(chars))
        return rows

    mem_inner_r = build_wing_mem_rows(
        10, 16, 0.0, 6.50, 12.40, 26.20,
        top_fn=lambda t: 21.60 + (26.00 - 21.60) * min(1.0, max(0.0, t / 6.40)),
        bot_fn=lambda t: arch(t, 0.0, 14.50, 6.10, 12.40, lift=2.90) if t <= 6.10 else 12.40 + (t - 6.10) * 0.5,
        holes=[(4.2, 8.8, 0.7)],
    )
    mem_mid_r = build_wing_mem_rows(
        12, 20, 5.80, 14.00, 11.70, 29.90,
        top_fn=lambda t: 26.00 + (29.60 - 26.00) * min(1.0, max(0.0, (t - 6.40) / (13.40 - 6.40))),
        bot_fn=lambda t: (
            arch(t, 6.10, 12.40, 10.20, 11.80, lift=3.40)
            if t <= 10.20
            else arch(t, 10.20, 11.80, 14.40, 12.60, lift=3.50)
        ),
        holes=[(3.5, 11.2, 0.8), (8.2, 9.4, 0.7)],
    )
    mem_outer_r = build_wing_mem_rows(
        12, 18, 13.30, 21.90, 13.40, 29.80,
        top_fn=lambda t: 29.60 + (25.40 - 29.60) * min(1.0, max(0.0, (t - 13.40) / (21.80 - 13.40))),
        bot_fn=lambda t: (
            arch(t, 14.40, 12.60, 18.80, 15.20, lift=3.10)
            if t <= 18.80
            else (15.20 + (25.40 - 15.20) * (max(0.0, min(1.0, (t - 18.80) / (21.80 - 18.80))) ** 0.75))
        ),
        holes=[(5.5, 9.2, 0.9), (8.4, 12.0, 0.6)],
    )
    paint("WING_MEM_INNER_R", mem_inner_r)
    paint("WING_MEM_MID_R",   mem_mid_r)
    paint("WING_MEM_OUTER_R", mem_outer_r)
    paint("WING_MEM_INNER_L", [row[::-1] for row in mem_inner_r])
    paint("WING_MEM_MID_L",   [row[::-1] for row in mem_mid_r])
    paint("WING_MEM_OUTER_L", [row[::-1] for row in mem_outer_r])

    # 4. ÁO CHOÀNG ĐỎ HOÀNG GIA TOÀN LƯNG 3 ĐOẠN (nếp gấp dọc giàu chiều sâu + gấu rách tơi y hệt ảnh 1!)
    paint("CAPE_UPPER", [
        "..cCMLLMMLMc..",
        "..cCMLLMMLMc..",
        ".cCMcMLcMLMCc.",
        ".cCMcMLcMLMCc.",
        ".cCMcMLcMLMCc.",
        ".cCMcMLcMLMCc.",
        "cCMcCMLcMLcCMc",
        "cCMcCMLcMLcCMc",
        "cCMcCMLcMLcCMc",
        "cCMcCMLcMLcCMc",
        "cCMcCMLcMLcCMc",
        "cCMcCMLcMLcCMc",
    ][:12] if False else [
        "..cCMLLMCMc.",
        "..cCMLLMCMc.",
        ".cCMcMLcMLMc",
        ".cCMcMLcMLMc",
        ".cCMcMLcMLMc",
        ".cCMcMLcMLMc",
        "cCMcCMLcMLMc",
        "cCMcCMLcMLMc",
        "cCMcCMLcMLMc",
        "cCMcCMLcMLMc",
        "cCMcCMLcMLMc",
        "cCMcCMLcMLMc",
    ])
    paint("CAPE_MID", [
        ".cCMcCMLcMLMc.",
        ".cCMcCMLcMLMc.",
        ".cCMcCMLcMLMc.",
        ".cCMcCMLcMLMc.",
        "cCMcCMLcCMLcMc",
        "cCMcCMLcCMLcMc",
        "cCMcCMLcCMLcMc",
        "cCMcCMLcCMLcMc",
        "cCMcCMLcCMLcMc",
        "cCMcCMLcCMLcMc",
        "cCMcCMLcCMLcMc",
        "cCMcCMLcCMLcMc",
    ])
    paint("CAPE_LOWER", [
        ".cCMcCMLcCMLcMc.",
        ".cCMcCMLcCMLcMc.",
        "cCMcCMLcCMLcCMMc",
        "cCMcCMLcCMLcCMMc",
        "cCMcCMLcCMLcCMMc",
        "cCMcCMLcCMLcCMMc",
        "cCMcCMLcCMLcCMMc",
        "cCM.CMLcCMLc.MMc",
        "cCMcCMLcCMLcCMMc",
        "cC.cCM.cCM.cCM.c",
        "cC.cC..cC..cC..c",
        "c..cC..c...cC..c",
        "c..c...c....c...",
        ".......c........",
    ])
    # Khố vải đỏ rách mặt trước giữa 2 đùi (ảnh 2, 3, 4!)
    paint("TABARD_FRONT", [
        "cCMLLMCM",
        "cCMcMLMc",
        "cCMcMLMc",
        "cCMcMLMc",
        "cCMcMLMc",
        "cCMcMLMc",
        "cCMcMLMc",
        "cCMcMLMc",
        "cCMcMLMc",
        ".CMcMLM.",
        ".CMcMLM.",
        ".C.cML.c",
        ".C.cM..c",
        "...c....",
    ])

    # Xác minh padding 2px trong suốt quanh toàn bộ vùng cutout (0 lỗi viền UV!)
    for k in (
        "WING_MEM_INNER_R", "WING_MEM_MID_R", "WING_MEM_OUTER_R",
        "WING_MEM_INNER_L", "WING_MEM_MID_L", "WING_MEM_OUTER_L",
        "CAPE_UPPER", "CAPE_MID", "CAPE_LOWER", "TABARD_FRONT",
    ):
        x0, y0, w, h = REG[k]
        sub_a = diff[y0:y0 + h, x0:x0 + w, 3]
        assert np.all((sub_a == 0) | (sub_a == 255)), f"Non-binary alpha in {k}"
        pad = diff[y0 - 2:y0 + h + 2, x0 - 2:x0 + w + 2, 3].copy()
        pad[2:2 + h, 2:2 + w] = 0
        assert not np.any(pad != 0), f"Non-zero padding around {k}"

    out_dir = ASSETS / "textures" / "armor"
    out_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(out_dir / "nightmare_armor.png")
    Image.fromarray(glow, "RGBA").save(out_dir / "nightmare_armor_glowmask.png")
    glow_px = int(np.sum(glow[:, :, 3] > 0))
    print(f"TEX_ARMOR_OK: {out_dir / 'nightmare_armor.png'} ({TEX_W}x{TEX_H} 1:1 pixel-art, heart glowTexels={glow_px})")
    return diff.astype(np.float32), glow.astype(np.float32)


def update_armor_item_icons():
    PAL = {
        ".": (0, 0, 0, 0),
        "0": (14, 15, 20, 255),
        "1": (28, 32, 42, 255),
        "2": (48, 54, 68, 255),
        "3": (76, 84, 102, 255),
        "4": (118, 128, 148, 255),
        "5": (168, 178, 196, 255),
        "h": (90, 14, 26, 255),
        "H": (152, 28, 44, 255),
        "R": (208, 48, 66, 255),
        "E": (236, 36, 56, 255),
        "c": (78, 10, 20, 255),
        "C": (132, 20, 34, 255),
        "M": (176, 32, 50, 255),
        "Q": (255, 162, 138, 255),
    }
    SPRITES = {
        "nightmare_helmet": [
            "................",
            "..0RR0....0RR0..",
            ".0RHR004500RHR0.",
            ".0H0023443200H0.",
            ".0h0234554320h0.",
            "..002322223200..",
            "...0432442340...",
            "...0320440230...",
            "...032....230...",
            "...021....120...",
            "...031....130...",
            "....00....00....",
            "................",
            "................",
            "................",
            "................",
        ],
        "nightmare_chestplate": [
            ".00..........00.",
            ".0400......0040.",
            "0345400..0045430",
            "0234320000234320",
            ".02E305RR503E20.",
            "..0245RQQ5R420..",
            "..0C25RQQ5R2C0..",
            "..0CM45RR54MC0..",
            "..0CM010010MC0..",
            "..0CM023320MC0..",
            "..0CM014410MC0..",
            "..0cC023320Cc0..",
            "..0cC00MM00Cc0..",
            "..0c.00MM00.c0..",
            "...0...00...0...",
            "................",
        ],
        "nightmare_leggings": [
            "................",
            "..000000000000..",
            ".03454344345430.",
            ".023222MM222320.",
            ".034210MM012430.",
            ".023210CM012320.",
            ".023200CM002320.",
            ".01220.CM.02210.",
            ".03430.CM.03430.",
            ".02420.cC.02420.",
            ".01210.c..01210.",
            ".02310....01320.",
            ".01210....01210.",
            "..000......000..",
            "................",
            "................",
        ],
        "nightmare_boots": [
            "................",
            "................",
            "................",
            "..0000....0000..",
            ".045540..045540.",
            ".023420..024320.",
            ".023320..023320.",
            ".012320..023210.",
            ".023320..023320.",
            "0343220..0223430",
            "0R23210..01232R0",
            "0RH2210..0122HR0",
            "0h00000..00000h0",
            "................",
            "................",
            "................",
        ],
    }
    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    for name, rows in SPRITES.items():
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = im.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                px[x, y] = PAL[ch]
        im.save(tex_dir / f"{name}.png")
    print("ICONS_OK: Updated 4 Nightmare Knight Armor 16x16 icons")


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

    add_box((-4.0, 24.0, -4.0), (4.0, 32.0, 4.0), (190, 152, 130))
    add_box((-4.02, 29.8, -4.02), (4.02, 32.02, 4.02), (62, 40, 26))
    add_box((-3.0, 27.0, -4.05), (-2.0, 28.0, -3.98), (244, 246, 250))
    add_box((-2.0, 27.0, -4.05), (-1.0, 28.0, -3.98), (58, 122, 74))
    add_box((+2.0, 27.0, -4.05), (+3.0, 28.0, -3.98), (244, 246, 250))
    add_box((+1.0, 27.0, -4.05), (+2.0, 28.0, -3.98), (58, 122, 74))
    add_box((-4.0, 12.0, -2.0), (+4.0, 24.0, +2.0), (46, 134, 140))
    add_box((-4.0,  0.0, -2.0), ( 0.0, 12.0, +2.0), (54, 58, 122))
    add_box(( 0.0,  0.0, -2.0), (+4.0, 12.0, +2.0), (54, 58, 122))
    add_box((-8.0, 12.0, -2.0), (-4.0, 24.0, +2.0), (190, 152, 130))
    add_box((+4.0, 12.0, -2.0), (+8.0, 24.0, +2.0), (190, 152, 130))
    return faces


def build_netherite_ref_faces():
    faces = build_steve_body_faces()

    def add_box(bmin, bmax, rgb):
        x0, y0, z0 = bmin
        x1, y1, z1 = bmax
        corners = np.array([
            [x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
            [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1],
        ], dtype=np.float64)
        for idxs in ([0, 1, 2, 3], [5, 4, 7, 6], [4, 0, 3, 7], [1, 5, 6, 2], [3, 2, 6, 7], [4, 5, 1, 0]):
            faces.append((corners[idxs], None, rgb))

    c_dark = (50, 44, 54)
    c_mid  = (66, 58, 70)
    add_box((-5.0, 29.0, -5.0), (5.0, 33.0, 5.0), c_mid)
    add_box((-5.0, 23.8, -3.5), (-4.0, 29.0, 5.0), c_dark)
    add_box((+4.0, 23.8, -3.5), (+5.0, 29.0, 5.0), c_dark)
    add_box((-5.0, 23.8, +4.0), (+5.0, 29.0, 5.0), c_dark)
    add_box((-5.0, 11.8, -3.0), (5.0, 24.5, 3.0), c_mid)
    add_box((-9.0, 18.2, -3.0), (-3.8, 25.0, 3.0), c_mid)
    add_box((+3.8, 18.2, -3.0), (+9.0, 25.0, 3.0), c_mid)
    add_box((-4.5, 5.2, -2.5), (-0.1, 12.2, 2.5), c_dark)
    add_box((+0.1, 5.2, -2.5), (+4.5, 12.2, 2.5), c_dark)
    add_box((-5.0, -0.1, -3.0), (-0.1, 5.8, 3.0), c_mid)
    add_box((+0.1, -0.1, -3.0), (+5.0, 5.8, 3.0), c_mid)
    return faces


def render_multi_angle_preview(geo: dict, diff_img, glow_img):
    armor_faces = build_steve_body_faces()
    for b in geo["minecraft:geometry"][0]["bones"]:
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
                armor_faces.append((corners[idxs], uv_map[fname], None))

    def raster(face_list, view_mat, w, h, sc, center, ambient=0.58, bg=(34, 38, 48)):
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.28, 0.74, -0.60], dtype=np.float64)
        ld /= np.linalg.norm(ld)
        cx, cy = w * 0.5, h * 0.52

        for quad, uv, flat_rgb in face_list:
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
                uvs = [(0, 0), (0, 0), (0, 0), (0, 0)]
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
                    alpha = diff_img[v, u, 3]
                    vis = alpha > 128
                    if not np.any(vis):
                        continue
                    cy_all, cx_all = np.where(cand)
                    cy_i, cx_i = cy_all[vis], cx_all[vis]
                    sub_z[cy_i, cx_i] = z[cand][vis]
                    lit = diff_img[v[vis], u[vis], :3] * shade
                    g_a = glow_img[v[vis], u[vis], 3:4] / 255.0
                    lit = np.where(g_a > 0.20, lit * (1.0 - g_a * 0.80) + glow_img[v[vis], u[vis], :3] * (g_a * 1.05), lit)
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    ctr_full = np.array([0.0, 18.5, 1.5], dtype=np.float64)
    ctr_body = np.array([0.0, 17.5, -0.5], dtype=np.float64)
    ctr_chest = np.array([0.0, 20.8, -1.6], dtype=np.float64)

    pw, ph = 460, 460
    im_neth  = raster(build_netherite_ref_faces(), cam_mat(180, 4), pw, ph, 10.2, ctr_body, ambient=0.45, bg=(22, 24, 30))
    im_front = raster(armor_faces, cam_mat(180, 4), pw, ph, 8.6, ctr_full, ambient=0.52, bg=(24, 27, 36))
    im_back  = raster(armor_faces, cam_mat(0,   4), pw, ph, 8.6, ctr_full, ambient=0.56, bg=(30, 34, 44))
    im_34l   = raster(armor_faces, cam_mat(145, 6), pw, ph, 9.6, ctr_full, ambient=0.52, bg=(24, 27, 36))
    im_34r   = raster(armor_faces, cam_mat(215, 6), pw, ph, 9.6, ctr_full, ambient=0.52, bg=(24, 27, 36))
    im_close = raster(armor_faces, cam_mat(180, 3), pw, ph, 19.5, ctr_chest, ambient=0.46, bg=(18, 20, 28))

    sheet = Image.new("RGB", (pw * 3, ph * 2), (14, 15, 20))
    panels = [
        (im_neth,  0, 0, "1. REF: VANILLA NETHERITE FRAME"),
        (im_front, 1, 0, "2. NIGHTMARE v4 FRONT (OPEN RIBCAGE + PULSING NIGHTMARE CORE)"),
        (im_back,  2, 0, "3. NIGHTMARE v4 BACK (FULL TATTERED CRIMSON CAPE + WINGS)"),
        (im_34l,   0, 1, "4. 3/4 RIGHT-FRONT HERO VIEW (OPEN RIBS + GLOWING HEART)"),
        (im_34r,   1, 1, "5. 3/4 LEFT-FRONT HERO VIEW (NETHERITE FRAME + CLAW BOOTS)"),
        (im_close, 2, 1, "6. CLOSE-UP: SPLIT-OPEN RIBCAGE + NIGHTMARE CORE HEART"),
    ]
    dr = ImageDraw.Draw(sheet)
    for im, gx, gy, title in panels:
        sheet.paste(im, (gx * pw, gy * ph))
        dr.text((gx * pw + 8, gy * ph + 8), title, fill=(242, 224, 200))

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out_preview = PREVIEW_DIR / "nightmare_armor_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")


if __name__ == "__main__":
    geo_data, cubes = build_nightmare_armor_geo()
    build_nightmare_armor_animations()
    diff_img, glow_img = build_nightmare_armor_texture_128()
    update_armor_item_icons()
    render_multi_angle_preview(geo_data, diff_img, glow_img)
    print(f"ALL_DONE: Nightmare Knight Armor v4.1 (Netherite Frame + Open Ribcage Nightmare Core Heart + Boss Details, {cubes} cubes)")
