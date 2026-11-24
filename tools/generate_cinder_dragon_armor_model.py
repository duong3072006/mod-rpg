#!/usr/bin/env python3
"""
Bộ sinh 1:1 chuẩn mật độ pixel-art Minecraft (`128x128`, `1 texel = 1 model px`, ZERO noise)
cho **Cinder Dragon Armor v3 — Volcanic Dragon-Knight Sovereign**:
  1. **Phom giáp nặng nổi khối chuẩn Netherite (HẾT ôm sát vào nhân vật!):**
     - **Vai giáp (`Pauldrons`):** Khối vai giáp rồng đa tầng xòe rộng rõ rệt (`6.75 x 3.90 x 6.50`, nhô hẳn ra ngoài bắp tay
       giống tỉ lệ vai giáp Netherite), đỉnh vai vát gờ nổi + mào sừng vai obsidian 2 tầng, tách biệt rõ với bắp tay thon hơn
       và ống găng cẳng tay bọc khớp loe mạnh (`5.75`) -> dáng chữ T uy lực, hết dạng ống thẳng!
     - **Ủng giáp (`Heavy Boots`):** Bậc ủng dày nổi hẳn ra ngoài ống quần (`5.90` thân ủng + **`6.35` cổ ủng loe** so với quần `4.78`
       — chênh lệch nổi khối rõ nét ngang ủng Netherite!), mũi ủng bọc tấm mu chân nối liền 3 móng vuốt rồng mỗi bên + gót giày bọc sắt!
     - **Áo giáp ngực (`Heavy Cuirass`):** Dày khối `9.70 x 12.2 x 5.75`, có cổ giáp (`gorget`), 2 mảng giáp ngực vát tầng nổi,
       đai eo phân đốt + tấm giáp hông (`tassets`) loe bảo vệ đùi, ôm gọn rãnh lõi dung nham + 6 móng kẹp + 3 viên ngọc tinh thể!
     - **Mũ Đầu Rồng (`Dragon Helm`):** Dày khối `9.96` ngang chuẩn mũ Netherite, bổ sung **má giáp loe (`cheek guards`)** & gờ mày dày
       khung rõ khuôn mặt (vẫn hở hoàn toàn mặt/mắt người chơi), giữ trọn mõm rồng, sừng mũi, mắt rồng cam-vàng, răng nanh ngà & 2 cặp sừng!
  2. **Vân rạn núi lửa phát sáng (~10% diện tích bề mặt — Volcanic Heat Veins):**
     - 90% bề mặt là tấm giáp **đá bazan–obsidian rèn viền sáng sắc nét** (tách rõ từng mảnh giáp, đinh tán, viền vát ngay cả ban đêm!).
     - ~10% là các **vết nứt dung nham mỏng rực nóng (`#9E2406 -> #FF7A14 -> #FFE070`)** len qua khe vai, viền cổ tay, mảng ngực,
       gối, cổ ủng, trán mũ và đuôi -> tỏa nhiệt sức nóng núi lửa cực chất nhưng tinh tế, không lòe loẹt!
  3. **Giữ nguyên 100% cấu trúc xương animation** (`wing_*_upper/mid/tip`, `tail_1..tail_4`, `tail_tip`, `pauldron_horn_*`, `claw_gauntlet_*`).

Chạy: python tools/generate_cinder_dragon_armor_model.py
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "cinder_dragon_armor_previews"

TEX_W = 128
TEX_H = 128

REG = {
    # Hàng 0 (y=0..8): Vỏ đầu rồng + mõm + mắt rồng + vật liệu 4x4 phẳng sạch
    "HELM_BACK":          (0,   0,  8,  8),
    "HELM_SIDE":          (8,   0,  8,  8),
    "HELM_TOP":           (16,  0,  8,  8),
    "DRAGON_SNOUT":       (24,  0,  6,  4),
    "DRAGON_NOSE":        (24,  4,  6,  3),
    "DRAGON_EYE":         (32,  0,  3,  2),
    "HORN_BASE":          (36,  0,  4,  4),
    "HORN_MID":           (40,  0,  4,  4),
    "HORN_DARK":          (44,  0,  4,  4),
    "BONE_LIGHT":         (48,  0,  4,  4),
    "BASALT_PLATE":       (52,  0,  4,  4),
    "BASALT_DARK":        (56,  0,  4,  4),
    "CRIMSON_TRIM":       (60,  0,  4,  4),
    "WING_BONE":          (64,  0,  4,  4),
    "TAIL_SEG":           (68,  0,  4,  4),
    "TAIL_CRYSTAL":       (72,  0,  4,  4),
    "ARM_CAP":            (76,  0,  4,  4),
    "BOOT_CLAW":          (80,  0,  2,  2),
    "FANG_IVORY":         (84,  0,  1,  3),
    "CHEST_GEM":          (88,  0,  4,  4),
    "CLAW_CRIMSON":       (94,  0,  4,  4),
    "MAGMA_SEAM":         (100, 0,  4,  4),  # Khe nứt nhiệt núi lửa mảnh phát sáng tinh tế (~10% tổng giáp!)
    "BEVEL_STEEL":        (106, 0,  4,  4),  # Viền thép-bazan rèn sáng gờ cạnh (tách khối cực nét như Netherite!)
    "CHEEK_GUARD":        (112, 0,  4,  5),  # Tấm má giáp mũ kiểu Netherite vát cạnh + 1 tia nứt nhiệt mảnh
    "EMPTY_TRANS":        (124, 124, 1, 1),

    # Hàng 1 (y=10..26): Thân giáp, ngực, lưng, vai lớn, ống tay, găng loe, quần, ủng lớn!
    "CHEST_FRONT":        (0,  10,  8, 12),
    "CHEST_CORE":         (8,  10,  4, 10),
    "CHEST_BACK":         (12, 10,  8, 12),
    "CHEST_SIDE":         (20, 10,  4, 12),
    "BACK_SHELL":         (24, 10,  6, 10),
    "PAULDRON_FRONT":     (30, 10,  6,  6),  # Vai giáp lớn 6x6 có gờ thép vát + 1 vết nứt dung nham chéo!
    "PAULDRON_OUT":       (36, 10,  6,  6),  # Mặt ngoài vai giáp 6x6 nổi gờ tầng + vết nứt núi lửa mảnh!
    "ARM_SLEEVE":         (42, 10,  4, 11),
    "ARM_BAND":           (46, 10,  5,  2),
    "GAUNTLET_CUFF":      (46, 13,  6,  4),  # Cổ tay găng giáp loe 6x4 viền vát + khe nhiệt đỏ cam!
    "LEG_THIGH":          (53, 10,  4,  8),
    "LEG_BELT":           (57, 10,  5,  3),
    "BOOT_CUFF":          (53, 18,  6,  3),  # Cổ ủng loe kiểu Netherite 6x3 có gờ thép & khe nhiệt mảnh!
    "BOOT_SOLE":          (60, 18,  5,  5),
    "PAULDRON_TOP":       (66, 10,  6,  6),  # Đỉnh vai giáp rèn viền nổi khối + khe nhiệt mảnh!
    "CHEST_PECTORAL":     (73, 10,  5,  6),  # Tấm giáp ngực nổi khối 5x6 rèn vát cạnh + vết rạn dung nham!
    "HIP_TASSET":         (79, 10,  4,  5),  # Tấm giáp hông (tasset) loe bảo vệ đùi trên!

    # Hàng 2 (y=34..54): 3 TẤM MÀNG CÁNH DƠI ĐỎ THẪM (>= 6 px padding trong suốt xung quanh -> 0 viền lỗi UV!)
    "WING_MEM_INNER_R":   (4,  34, 10, 16),
    "WING_MEM_MID_R":     (20, 34, 12, 20),
    "WING_MEM_OUTER_R":   (38, 34, 12, 18),
    "WING_MEM_INNER_L":   (56, 34, 10, 16),
    "WING_MEM_MID_L":     (72, 34, 12, 20),
    "WING_MEM_OUTER_L":   (90, 34, 12, 18),

    # Hàng 3 (y=62..78): Rãnh ngực, Đai eo, Quần, Ủng lớn, Vảy nổi, Sừng vai (cách >= 8 px dưới màng cánh!)
    "CHEST_CAVITY":       (4,  62, 10, 12),
    "WAIST_BELT_FRONT":   (18, 62, 10,  2),
    "WAIST_BUCKLE":       (18, 66,  4,  4),
    "LEG_FRONT_R":        (30, 62,  4,  8),
    "LEG_FRONT_L":        (36, 62,  4,  8),
    "LEG_BACK_R":         (42, 62,  4,  8),
    "LEG_BACK_L":         (48, 62,  4,  8),
    "LEG_SIDE":           (54, 62,  4,  8),
    "BOOT_FRONT":         (60, 62,  5,  6),  # Mặt trước ủng dày 5x6 có nẹp ống chân + tia nứt núi lửa mảnh!
    "BOOT_SIDE_R":        (66, 62,  5,  6),  # Mặt bên ủng dày 5x6 có khớp cổ chân nổi khối!
    "BOOT_SIDE_L":        (72, 62,  5,  6),
    "BOOT_BACK":          (78, 62,  5,  6),  # Gót ủng bọc thép vát khối!
    "BOOT_CLAW_IVORY":    (84, 62,  4,  4),
    "ASH_STONE":          (90, 62,  4,  4),
    "KNEE_TAB":           (96, 62,  4,  4),  # Giáp đầu gối (poleyn) 4x4 vát sắc + 1 điểm rạn nhiệt mảnh!
    "SCALE_PLATE":        (102, 62, 4,  4),
    "SCALE_RING":         (108, 62, 5,  3),
    "OBSIDIAN_RIM":       (115, 62, 4,  4),
    "PAULDRON_HORN_BASE": (18, 74,  4,  4),
    "PAULDRON_HORN_MID":  (24, 74,  4,  4),
    "PAULDRON_HORN_TIP":  (30, 74,  4,  4),
}


def verify_uv_and_wing_padding():
    occupied = {}
    for k, (x0, y0, w, h) in REG.items():
        assert x0 >= 0 and y0 >= 0 and x0 + w <= TEX_W and y0 + h <= TEX_H, f"Out of bounds: {k}"
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                assert (x, y) not in occupied, f"UV overlap at ({x},{y}) between {occupied[(x,y)]} and {k}"
                occupied[(x, y)] = k
    for k, (x0, y0, w, h) in REG.items():
        if not k.startswith("WING_MEM_"):
            continue
        for y in range(max(0, y0 - 2), min(TEX_H, y0 + h + 2)):
            for x in range(max(0, x0 - 2), min(TEX_W, x0 + w + 2)):
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    continue
                assert (x, y) not in occupied, f"Wing padding violated near {k} at ({x},{y}) by {occupied[(x,y)]}"


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


def make_segment_cube(p0, p1, sx: float, sy: float, mat: str, pad: float = 0.48, face_mats=None) -> dict:
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


def make_membrane_plane(center, width, height, front_key: str, back_key: str, rot=None) -> dict:
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


def build_cinder_dragon_armor_geo():
    """
    Hệ tọa độ Bedrock/GeckoLib 1.12.0:
      - Y=0: đế giày | Y=12: hông | Y=22: vai | Y=24: cổ | Y=32: đỉnh đầu
      - -Z = TRƯỚC | +Z = SAU | -X = PHẢI người chơi | +X = TRÁI người chơi
    """
    horn_faces = {"up": "HORN_MID", "down": "HORN_DARK", "north": "HORN_BASE", "south": "HORN_DARK", "east": "HORN_BASE", "west": "HORN_BASE"}

    # ==================== 1. MŨ ĐẦU RỒNG KHỐI DÀY CHUẨN NETHERITE (`armorHead`, `horn_r`, `horn_l`) ====================
    # Nở phom mũ lên 10.0 px (giống mũ Netherite!), bổ sung má giáp loe & gờ mày vát sắc, vẫn mở thoáng 100% mắt/mặt người chơi!
    head_cubes = [
        # 1a. Nóc mũ khối dày nổi gờ (`10.00 x 1.25 x 10.20`, ôm vượt đầu Steve +1.0 px mỗi bên giống Netherite!)
        make_cube(
            [-5.00, 32.18, -5.12], [10.00, 1.25, 10.20], "BASALT_DARK",
            face_mats={"up": "HELM_TOP", "north": "BEVEL_STEEL", "south": "BASALT_PLATE", "east": "BASALT_PLATE", "west": "BASALT_PLATE"}
        ),
        # Sống vòm đỉnh mũ rèn vát nổi có khe nhiệt mảnh
        make_centered_cube([0.0, 33.68, +0.10], [6.80, 0.92, 8.60], "BASALT_PLATE", face_mats={"up": "HELM_TOP", "north": "BEVEL_STEEL"}),
        # Thành sau gáy dày khối + gờ cổ sau (bọc chắc gáy như mũ Netherite)
        make_cube([-5.00, 23.85, +4.18], [10.00, 8.45, 0.92], "BASALT_DARK", face_mats={"south": "HELM_BACK", "down": "BEVEL_STEEL"}),
        # Hai thành bên mũ dày nổi khối (trên che thái dương tới z=-5.12; dưới nối liền má giáp loe!)
        make_cube([-5.00, 28.45, -5.12], [0.96, 3.95, 10.20], "BASALT_DARK", face_mats={"west": "HELM_SIDE", "north": "BEVEL_STEEL"}),
        make_cube([-5.00, 23.95, -3.55], [0.96, 4.65, 8.65],  "BASALT_DARK", face_mats={"west": "HELM_SIDE", "north": "CHEEK_GUARD"}),
        make_cube([+4.04, 28.45, -5.12], [0.96, 3.95, 10.20], "BASALT_DARK", face_mats={"east": "HELM_SIDE", "north": "BEVEL_STEEL"}),
        make_cube([+4.04, 23.95, -3.55], [0.96, 4.65, 8.65],  "BASALT_DARK", face_mats={"east": "HELM_SIDE", "north": "CHEEK_GUARD"}),

        # 1b. MÕM RỒNG / HÀM TRÊN NHÔ VỀ PHÍA TRƯỚC (giữ đúng chiều cao trên mắt Steve, tăng độ sắc cạnh viền rèn!)
        make_cube(
            [-4.12, 29.05, -7.15], [8.24, 3.45, 2.75], "BASALT_PLATE",
            face_mats={"up": "DRAGON_SNOUT", "north": "DRAGON_SNOUT", "east": "BASALT_PLATE", "west": "BASALT_PLATE", "down": "BASALT_DARK"}
        ),
        make_cube(
            [-3.45, 28.92, -9.05], [6.90, 2.90, 2.25], "BASALT_PLATE",
            face_mats={"north": "DRAGON_NOSE", "up": "DRAGON_SNOUT", "east": "BASALT_PLATE", "west": "BASALT_PLATE", "down": "BASALT_DARK"}
        ),
        make_centered_cube([0.0, 32.25, -6.95], [2.55, 0.90, 3.80], "BEVEL_STEEL", rot=[-8.0, 0.0, 0.0]),

        # 1c. SỪNG MŨI OBSIDIAN LIỀN KHỐI (`nasal_horn`)
        make_segment_cube((0.0, 31.70, -8.15), (0.0, 33.55, -7.95), 1.54, 1.54, "HORN_BASE", pad=0.55, face_mats=horn_faces),
        make_segment_cube((0.0, 33.55, -7.95), (0.0, 35.10, -7.35), 1.14, 1.14, "HORN_MID",  pad=0.50, face_mats=horn_faces),
        make_segment_cube((0.0, 35.10, -7.35), (0.0, 36.25, -6.48), 0.72, 0.72, "HORN_DARK", pad=0.35, face_mats=horn_faces),

        # 1d. 3 GAI SỐNG ĐỈNH ĐẦU KHỐI 3D ĐẶC
        make_centered_cube([0.0, 34.65, -2.15], [1.36, 2.05, 1.95], "HORN_BASE", rot=[-24.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 34.45, +0.95], [1.40, 2.25, 2.05], "HORN_MID",  rot=[-32.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 33.15, +4.05], [1.30, 2.05, 1.92], "HORN_DARK", rot=[-46.0, 0.0, 0.0], face_mats=horn_faces),
    ]

    # 1e. MÁ GIÁP LOE KIỂU NETHERITE (`cheek_guard`) + GỜ MÀY SẮC + MẮT RỒNG + RĂNG NANH NGÀ!
    for s in (-1.0, 1.0):
        outer_f = "west" if s < 0 else "east"
        # Má giáp rèn dày vát cạnh (khung mặt uy lực giống mũ Netherite, cách sạch mắt & miệng Steve!)
        head_cubes.append(make_centered_cube(
            [s * 4.55, 26.20, -3.95], [1.22, 4.45, 2.15], "CHEEK_GUARD",
            rot=[0.0, s * -10.0, s * -5.0],
            face_mats={"north": "CHEEK_GUARD", outer_f: "HELM_SIDE", "down": "BEVEL_STEEL"}
        ))
        # Gờ hàm dưới vát chéo ôm gọn 2 bên cằm (hở sạch cằm giữa!)
        head_cubes.append(make_centered_cube(
            [s * 3.85, 24.42, -4.55], [1.65, 1.05, 1.15], "BEVEL_STEEL",
            rot=[0.0, s * -16.0, 0.0]
        ))
        # Gờ mày đá vát xiên dữ tợn + điểm khe nhiệt mảnh ở gốc thái dương
        head_cubes.append(make_centered_cube(
            [s * 4.45, 31.95, -5.55], [1.85, 1.12, 3.25], "BEVEL_STEEL",
            rot=[+10.0, s * -12.0, s * -18.0]
        ))
        # Hốc mắt & Đôi mắt rồng cam-vàng phát sáng
        head_cubes.append(make_centered_cube(
            [s * 4.38, 30.60, -5.75], [1.22, 1.36, 2.20], "BASALT_DARK",
            rot=[0.0, s * -12.0, 0.0],
            face_mats={"north": "DRAGON_EYE", outer_f: "DRAGON_EYE"}
        ))
        # Mảng gò má vảy đá dưới hốc mắt rồng
        head_cubes.append(make_centered_cube(
            [s * 4.42, 29.48, -5.65], [1.40, 0.98, 2.55], "BASALT_PLATE",
            rot=[0.0, s * -10.0, 0.0]
        ))
        # 2 cặp răng nanh trắng ngà sắc nhọn ôm ngoài khuôn mặt
        head_cubes.append(make_centered_cube(
            [s * 3.52, 28.55, -8.25], [0.76, 1.30, 0.76], "FANG_IVORY", rot=[+4.0, 0.0, s * -5.0]
        ))
        head_cubes.append(make_centered_cube(
            [s * 3.49, 27.70, -8.20], [0.46, 0.68, 0.46], "FANG_IVORY", rot=[+6.0, 0.0, s * -7.0]
        ))
        head_cubes.append(make_centered_cube(
            [s * 4.28, 28.42, -5.75], [0.76, 1.48, 0.76], "FANG_IVORY", rot=[+4.0, 0.0, s * -6.0]
        ))
        head_cubes.append(make_centered_cube(
            [s * 4.25, 27.42, -5.70], [0.48, 0.78, 0.48], "FANG_IVORY", rot=[+6.0, 0.0, s * -8.0]
        ))

    def build_boss_crown_horns(s: float):
        hc = []
        u_pts = [
            (s * 4.50, 31.95, -0.85),
            (s * 5.70, 33.80, +1.05),
            (s * 6.75, 35.95, +3.15),
            (s * 7.50, 38.15, +5.25),
            (s * 7.80, 40.05, +6.95),
        ]
        u_sizes = [(2.28, 2.28, "HORN_BASE"), (1.90, 1.90, "HORN_BASE"), (1.48, 1.48, "HORN_MID"), (0.98, 0.98, "HORN_DARK")]
        for idx, (w, h, mat) in enumerate(u_sizes):
            hc.append(make_segment_cube(u_pts[idx], u_pts[idx + 1], w, h, mat, pad=0.72, face_mats=horn_faces))

        l_pts = [
            (s * 4.55, 28.90, +0.45),
            (s * 5.85, 29.80, +2.25),
            (s * 6.90, 30.85, +4.05),
            (s * 7.60, 32.00, +5.55),
        ]
        l_sizes = [(1.68, 1.62, "HORN_BASE"), (1.30, 1.26, "HORN_MID"), (0.86, 0.84, "HORN_DARK")]
        for idx, (w, h, mat) in enumerate(l_sizes):
            hc.append(make_segment_cube(l_pts[idx], l_pts[idx + 1], w, h, mat, pad=0.62, face_mats=horn_faces))
        return hc

    horn_r_cubes = build_boss_crown_horns(-1.0)
    horn_l_cubes = build_boss_crown_horns(+1.0)

    # ==================== 2. ÁO GIÁP NGỰC KHỐI DÀY CHUẨN NETHERITE + KHE NHIỆT NÚI LỬA ~10% (`armorBody`) ====================
    claw_tip_faces = {
        "north": "CLAW_CRIMSON", "south": "CRIMSON_TRIM",
        "up": "CLAW_CRIMSON", "down": "CRIMSON_TRIM",
        "east": "CLAW_CRIMSON", "west": "CLAW_CRIMSON",
    }
    body_cubes = [
        # Hộp thân giáp ngực dày dặn chuẩn Netherite (`9.70 x 12.20 x 5.70` — hết ôm sát vào thân Steve!)
        make_centered_cube(
            [0.0, 18.0, 0.0], [9.70, 12.20, 5.70], "BASALT_PLATE",
            face_mats={
                "north": "CHEST_CAVITY",
                "south": "CHEST_BACK",
                "east":  "CHEST_SIDE",
                "west":  "CHEST_SIDE",
                "up":    "BEVEL_STEEL",
                "down":  "BASALT_DARK",
            }
        ),
        # Cổ giáp rèn đứng (Gorget / High Collar) ôm chắc quanh cổ nối mũ <-> vai giáp!
        make_centered_cube([0.0, 24.05, +2.15], [7.80, 1.45, 1.55], "BEVEL_STEEL", rot=[+14.0, 0.0, 0.0]),
        make_centered_cube([-3.65, 23.95, +0.25], [1.65, 1.30, 4.95], "BEVEL_STEEL", rot=[0.0, 0.0, +10.0]),
        make_centered_cube([+3.65, 23.95, +0.25], [1.65, 1.30, 4.95], "BEVEL_STEEL", rot=[0.0, 0.0, -10.0]),

        # Đai eo bọc sắt-bazan dày khối nổi bậc (`10.15 x 1.55 x 6.20`) + khóa đai điểm lõi nhiệt núi lửa!
        make_centered_cube(
            [0.0, 12.35, 0.0], [10.15, 1.55, 6.20], "HORN_DARK",
            face_mats={
                "north": "WAIST_BELT_FRONT",
                "south": "HORN_DARK",
                "east":  "BEVEL_STEEL",
                "west":  "BEVEL_STEEL",
                "up":    "BEVEL_STEEL",
                "down":  "HORN_DARK",
            }
        ),
        make_centered_cube(
            [0.0, 12.42, -3.22], [2.30, 1.32, 0.42], "BEVEL_STEEL",
            face_mats={"north": "WAIST_BUCKLE"}
        ),
        # Tấm giáp bụng dưới (Codpiece / Front Tasset) vát nhọn xuống giữa 2 đùi kiểu hiệp sĩ rồng!
        make_centered_cube(
            [0.0, 10.95, -3.12], [2.65, 2.05, 0.56], "HIP_TASSET", rot=[-8.0, 0.0, 0.0],
            face_mats={"north": "HIP_TASSET", "down": "BEVEL_STEEL"}
        ),

        # Mai lưng phân đốt dày khối + 3 gai sống lưng đặc (dịch z +0.40 khớp lưng dày mới!)
        make_centered_cube(
            [0.0, 18.0, +3.32], [5.85, 9.80, 1.40], "BACK_SHELL",
            face_mats={"south": "BACK_SHELL", "up": "BEVEL_STEEL", "down": "TAIL_SEG"}
        ),
        make_centered_cube([0.0, 22.0, +4.55], [1.20, 2.05, 1.85], "HORN_BASE", rot=[-32.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 19.0, +4.65], [1.20, 2.05, 1.85], "HORN_MID",  rot=[-36.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 16.0, +4.55], [1.14, 1.90, 1.75], "HORN_DARK", rot=[-40.0, 0.0, 0.0], face_mats=horn_faces),

        # Gờ khung đáy hốc lõi nhiệt trước ngực
        make_centered_cube([0.0, 13.45, -3.08], [9.40, 0.64, 0.50], "BEVEL_STEEL"),

        # Cụm 3 viên ngọc tinh thể hỏa long nổi 3D trong khe nứt giữa ngực (gọn & sắc nét hơn, tỏa nhiệt đúng ~10%!)
        make_centered_cube([+0.06, 16.15, -3.32], [1.36, 1.36, 0.86], "CHEST_GEM", rot=[+7.0, -5.0, -28.0]),
        make_centered_cube([-0.55, 18.00, -3.26], [0.84, 0.84, 0.72], "CHEST_GEM", rot=[+4.0, +6.0, +38.0]),
        make_centered_cube([+0.55, 18.12, -3.26], [0.82, 0.82, 0.72], "CHEST_GEM", rot=[-3.0, -5.0, +4.0]),
    ]

    # Hai mảng giáp ngực rèn dày nổi khối (`CHEST_PECTORAL`) có vết nứt dung nham mảnh + 3 tầng móng kẹp lõi + giáp sườn!
    for s in (-1.0, 1.0):
        # Mảng giáp ngực trên (Pectoral Plate) dày nổi bậc hẳn ra trước `z=-3.18` (giáp chiến binh cực uy lực!)
        body_cubes.append(make_centered_cube(
            [s * 2.95, 21.15, -3.14], [3.85, 4.30, 0.72], "CHEST_PECTORAL",
            rot=[-5.0, s * -10.0, 0.0],
            face_mats={"north": "CHEST_PECTORAL", "up": "BEVEL_STEEL", "down": "OBSIDIAN_RIM"}
        ))
        # Tấm giáp sườn giữa & dưới ôm khép khe lõi dung nham tạo hình chữ V mạnh mẽ!
        body_cubes.append(make_centered_cube(
            [s * 3.25, 17.55, -3.06], [3.25, 2.95, 0.58], "BASALT_PLATE",
            rot=[0.0, s * -8.0, 0.0],
            face_mats={"north": "CHEST_PECTORAL", "down": "BEVEL_STEEL"}
        ))
        body_cubes.append(make_centered_cube(
            [s * 3.40, 14.90, -3.02], [2.95, 2.35, 0.54], "BASALT_PLATE",
            rot=[+5.0, s * -8.0, 0.0],
            face_mats={"north": "SCALE_PLATE", "down": "OBSIDIAN_RIM"}
        ))
        # Gờ xương sườn obsidian kẹp khe lõi núi lửa (3 cặp móng kẹp 3D gồ mạnh trên thân dày!)
        claw_tiers = [
            ((s * 2.40, 20.85, -2.95), (s * 1.85, 20.72, -3.62), (s * 0.78, 20.52, -3.96), 0.90, 0.76),
            ((s * 2.85, 18.25, -2.95), (s * 2.28, 18.20, -3.66), (s * 1.25, 18.12, -3.98), 0.92, 0.78),
            ((s * 3.05, 15.20, -2.95), (s * 2.48, 15.26, -3.64), (s * 1.25, 15.42, -3.96), 0.94, 0.80),
        ]
        for p_root, p_knuckle, p_tip, w_root, w_tip in claw_tiers:
            body_cubes.append(make_segment_cube(p_root, p_knuckle, w_root, w_root, "HORN_BASE", pad=0.24, face_mats=horn_faces))
            body_cubes.append(make_segment_cube(p_knuckle, p_tip, w_tip, w_tip, "CLAW_CRIMSON", pad=0.22, face_mats=claw_tip_faces))

        # Vảy giáp sườn bảo vệ mạn sườn (gọn gàng không chạm tay khi đánh Dual Knife / Togetsu)
        for sy in (20.20, 17.55, 14.95):
            body_cubes.append(make_centered_cube([s * 4.96, sy, 0.0], [0.42, 1.90, 3.15], "SCALE_PLATE", rot=[0.0, 0.0, s * -8.0]))

        # Vảy giáp lưng lớn sau bả vai
        for sy, sw in ((21.70, 2.35), (18.70, 2.40), (15.80, 2.15)):
            body_cubes.append(make_centered_cube([s * 3.45, sy, +3.12], [sw, 2.15, 0.56], "SCALE_PLATE", rot=[+11.0, s * +6.0, 0.0]))

    # ==================== 2b. ĐÔI CÁNH RỒNG 3 KHỚP ĐỐI XỨNG (dịch z +0.42 khớp lưng giáp dày mới!) ====================
    WING_YAW_DEG = 20.0
    WING_COS = math.cos(math.radians(WING_YAW_DEG))
    WING_SIN = math.sin(math.radians(WING_YAW_DEG))

    def wing_pt(s: float, t: float, y: float):
        return (s * (2.2 + t * WING_COS), y, 3.05 + t * WING_SIN)

    def build_boss_dragon_wing(s: float):
        f_tag = "R" if s < 0 else "L"
        b_tag = "L" if s < 0 else "R"
        p_sh = wing_pt(s, 0.0,  21.5)
        p_el = wing_pt(s, 6.2,  25.3)
        p_wr = wing_pt(s, 13.0, 27.9)
        p_tp = wing_pt(s, 20.6, 25.3)

        f1_tip = wing_pt(s, 5.8,  14.7)
        f2_tip = wing_pt(s, 9.6,  14.5)
        f3_tip = wing_pt(s, 13.4, 15.1)
        f4_tip = wing_pt(s, 17.4, 17.7)

        upper = [
            make_segment_cube(p_sh, p_el, 1.32, 1.32, "WING_BONE", pad=0.65, face_mats=horn_faces),
            make_centered_cube(wing_pt(s, 2.6, 23.75), [0.75, 1.25, 0.75], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * -12.0], face_mats=horn_faces),
            make_centered_cube(wing_pt(s, 5.0, 25.30), [0.75, 1.28, 0.75], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * -14.0], face_mats=horn_faces),
            make_segment_cube(p_el, f1_tip, 0.58, 0.58, "WING_BONE", pad=0.25, face_mats=horn_faces),
            make_membrane_plane(
                wing_pt(s, 3.15, 20.00), 6.30, 11.00,
                f"WING_MEM_INNER_{f_tag}", f"WING_MEM_INNER_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]

        thumb_mid = (p_wr[0] - s * 0.25, p_wr[1] + 1.85, p_wr[2] - 0.55)
        thumb_tip = (p_wr[0] - s * 0.95, p_wr[1] + 2.75, p_wr[2] - 1.65)
        mid = [
            make_segment_cube(p_el, p_wr, 1.24, 1.24, "WING_BONE", pad=0.65, face_mats=horn_faces),
            make_centered_cube(wing_pt(s, 9.6, 27.40), [0.72, 1.22, 0.72], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * -10.0], face_mats=horn_faces),
            make_segment_cube(p_wr, thumb_mid, 0.92, 0.92, "HORN_BASE", pad=0.50, face_mats=horn_faces),
            make_segment_cube(thumb_mid, thumb_tip, 0.64, 0.64, "HORN_DARK", pad=0.35, face_mats=horn_faces),
            make_segment_cube(p_wr, f2_tip, 0.54, 0.54, "WING_BONE", pad=0.20, face_mats=horn_faces),
            make_segment_cube(p_wr, f3_tip, 0.52, 0.52, "WING_BONE", pad=0.20, face_mats=horn_faces),
            make_membrane_plane(
                wing_pt(s, 9.60, 21.20), 7.80, 13.80,
                f"WING_MEM_MID_{f_tag}", f"WING_MEM_MID_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]

        p_tip_end = wing_pt(s, 21.8, 24.5)
        tip = [
            make_segment_cube(p_wr, p_tp, 1.05, 1.05, "WING_BONE", pad=0.55, face_mats=horn_faces),
            make_segment_cube(p_tp, p_tip_end, 0.68, 0.68, "HORN_DARK", pad=0.30, face_mats=horn_faces),
            make_centered_cube(wing_pt(s, 16.8, 27.35), [0.64, 1.05, 0.64], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * +10.0], face_mats=horn_faces),
            make_segment_cube(p_wr, f4_tip, 0.48, 0.48, "WING_BONE", pad=0.18, face_mats=horn_faces),
            make_membrane_plane(
                wing_pt(s, 16.90, 21.50), 7.80, 13.20,
                f"WING_MEM_OUTER_{f_tag}", f"WING_MEM_OUTER_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]
        return upper, mid, tip

    wing_r_upper, wing_r_mid, wing_r_tip = build_boss_dragon_wing(-1.0)
    wing_l_upper, wing_l_mid, wing_l_tip = build_boss_dragon_wing(+1.0)

    # ==================== 2c. ĐUÔI RỒNG 4 ĐỐT + CHÙY LƯỠI LIỀM ĐÔI (dịch z +0.42 khớp đai eo dày mới!) ====================
    tz = 0.42
    tail_1_cubes = [
        make_centered_cube([0.0, 12.10, +4.15 + tz], [3.40, 3.00, 4.60], "TAIL_SEG", rot=[-16.0, 0.0, 0.0]),
        make_centered_cube([0.0, 14.00, +4.40 + tz], [1.05, 1.55, 1.55], "HORN_BASE", rot=[-32.0, 0.0, 0.0], face_mats=horn_faces),
    ]
    tail_2_cubes = [
        make_centered_cube([0.0, 11.00, +7.40 + tz], [2.85, 2.50, 4.60], "TAIL_SEG", rot=[-8.0, 0.0, 0.0]),
        make_centered_cube([0.0, 12.60, +7.85 + tz], [0.95, 1.45, 1.45], "HORN_MID",  rot=[-24.0, 0.0, 0.0], face_mats=horn_faces),
    ]
    tail_3_cubes = [
        make_centered_cube([0.0, 10.68, +10.75 + tz], [2.35, 2.10, 4.60], "TAIL_SEG", rot=[+10.0, 0.0, 0.0]),
        make_centered_cube([0.0, 12.10, +11.10 + tz], [0.88, 1.35, 1.35], "HORN_MID", rot=[-8.0, 0.0, 0.0], face_mats=horn_faces),
    ]
    tail_4_cubes = [
        make_centered_cube([0.0, 11.25, +13.95 + tz], [1.95, 1.75, 4.40], "TAIL_SEG", rot=[+18.0, 0.0, 0.0]),
        make_centered_cube([0.0, 12.50, +14.20 + tz], [0.80, 1.25, 1.25], "HORN_DARK", rot=[+4.0, 0.0, 0.0], face_mats=horn_faces),
    ]
    tail_tip_cubes = [
        make_centered_cube([0.0, 11.55, +15.55 + tz], [1.80, 1.60, 1.55], "TAIL_SEG", rot=[+18.0, 0.0, 0.0]),
        make_centered_cube([0.0, 11.75, +16.05 + tz], [2.55, 1.65, 1.75], "BASALT_PLATE", rot=[+18.0, 0.0, 0.0]),
        make_centered_cube([0.0, 12.25, +16.95 + tz], [2.25, 1.90, 2.25], "TAIL_CRYSTAL", rot=[+18.0, +45.0, 0.0]),
        make_centered_cube([0.0, 12.35, +17.05 + tz], [1.75, 2.10, 1.75], "TAIL_CRYSTAL", rot=[+20.0, 0.0, +45.0]),
        make_segment_cube((0.0, 12.35, +17.40 + tz), (0.0, 13.15, +19.85 + tz), 1.20, 1.08, "HORN_BASE", pad=0.45, face_mats=horn_faces),
        make_segment_cube((0.0, 13.15, +19.85 + tz), (0.0, 13.70, +21.65 + tz), 0.78, 0.72, "HORN_MID",  pad=0.35, face_mats=horn_faces),
    ]
    for s in (-1.0, 1.0):
        p_arc = [
            (s * 1.15, 11.85, +16.35 + tz),
            (s * 2.85, 12.20, +17.45 + tz),
            (s * 3.55, 12.75, +19.10 + tz),
            (s * 3.15, 13.35, +20.80 + tz),
            (s * 2.10, 13.80, +21.95 + tz),
            (s * 0.95, 14.08, +22.35 + tz),
        ]
        p_sizes = [
            (1.52, 1.32, "HORN_BASE"), (1.42, 1.26, "HORN_BASE"),
            (1.26, 1.14, "HORN_MID"), (1.04, 0.96, "HORN_MID"), (0.76, 0.72, "HORN_DARK"),
        ]
        for idx, (w, h, mat) in enumerate(p_sizes):
            tail_tip_cubes.append(make_segment_cube(p_arc[idx], p_arc[idx + 1], w, h, mat, pad=0.58, face_mats=horn_faces))

    # ==================== 3. VAI GIÁP RỘNG NỔI KHỐI CHUẨN NETHERITE & GĂNG GIÁP LOE (`armorRightArm`, `armorLeftArm`) ====================
    def build_dragon_arm(s: float):
        cx = s * 6.0
        outer_face = "west" if s < 0 else "east"
        return [
            # 1. Lớp giáp lót bắp tay thon gọn (`4.56 x 10.60 x 4.56`) -> làm NỔI BẬT PHOM VAI GIÁP RỘNG & ỐNG GĂNG LOE!
            make_centered_cube(
                [cx, 18.85, 0.0], [4.56, 10.60, 4.56], "ARM_SLEEVE",
                face_mats={"up": "ARM_CAP", "down": "ARM_CAP"}
            ),

            # 2. VAI GIÁP CHÍNH KHỔNG LỒ NỔI BẬC HẲN RA NGOÀI KIỂU NETHERITE (`6.75 x 3.85 x 6.50`, lệch ngoài `s*0.72`!)
            make_centered_cube(
                [cx + s * 0.72, 22.30, 0.0], [6.75, 3.85, 6.50], "PAULDRON_OUT",
                rot=[0.0, 0.0, s * -7.0],
                face_mats={
                    "north":    "PAULDRON_FRONT",
                    "south":    "PAULDRON_FRONT",
                    outer_face: "PAULDRON_OUT",
                    "up":       "PAULDRON_TOP",
                    "down":     "BEVEL_STEEL",
                }
            ),
            # Tầng mái vai giáp trên vát cạnh nổi bậc (chồng tầng như giáp hiệp sĩ Netherite + rồng núi lửa!)
            make_centered_cube(
                [cx + s * 1.15, 24.45, 0.0], [5.55, 1.35, 6.05], "PAULDRON_TOP",
                rot=[0.0, 0.0, s * -16.0],
                face_mats={"up": "PAULDRON_TOP", "north": "BEVEL_STEEL", "south": "BEVEL_STEEL", outer_face: "BEVEL_STEEL"}
            ),
            # Tầng lá vai giáp dưới (Pauldron Lame) xòe rõ quanh bắp tay trên (thép-bazan sạch viền sắc, 0 vết nứt cam!)
            make_centered_cube(
                [cx + s * 0.52, 20.15, 0.0], [6.20, 1.28, 5.95], "BEVEL_STEEL",
                rot=[0.0, 0.0, s * -10.0],
                face_mats={"north": "BASALT_PLATE", "south": "BASALT_PLATE", outer_face: "BASALT_PLATE", "up": "BEVEL_STEEL", "down": "OBSIDIAN_RIM"}
            ),

            # 3. Đai bọc khuỷu tay thon gọn (`5.05`) để lộ rõ khoảng thắt giữa Vai Giáp (6.75) và Găng Cổ Tay (5.75)!
            make_centered_cube([cx + s * 0.10, 17.80, 0.0], [5.05, 1.25, 5.05], "OBSIDIAN_RIM", face_mats={"up": "BEVEL_STEEL"}),
            # Khiên khuỷu ngoài vát nhọn (Couter)
            make_centered_cube([cx + s * 2.72, 17.85, +0.25], [0.72, 2.05, 2.55], "SCALE_PLATE", rot=[0.0, 0.0, s * -12.0]),

            # 4. ỐNG GĂNG CỔ TAY LOE DÀY KHỐI (`5.75 x 3.25 x 5.75`) — bước nhảy khối cực sắc nét giống giáp cổ tay hiệp sĩ!
            make_centered_cube(
                [cx + s * 0.14, 15.10, 0.0], [5.75, 3.25, 5.75], "GAUNTLET_CUFF",
                face_mats={
                    "north":    "GAUNTLET_CUFF",
                    "south":    "GAUNTLET_CUFF",
                    outer_face: "GAUNTLET_CUFF",
                    "up":       "BEVEL_STEEL",
                    "down":     "BASALT_DARK",
                }
            ),
            # Vành trên loe của găng cổ tay (cuff lip `6.05 x 0.72 x 6.05`) tách rõ cổ tay khỏi khuỷu tay!
            make_centered_cube([cx + s * 0.18, 16.55, 0.0], [6.05, 0.72, 6.05], "BEVEL_STEEL"),
            # 2 gai sống cẳng tay obsidian sắc cạnh
            make_centered_cube([cx + s * 3.16, 16.10, +0.25], [0.76, 1.32, 1.15], "HORN_BASE", rot=[0.0, 0.0, s * +16.0], face_mats=horn_faces),
            make_centered_cube([cx + s * 3.12, 14.55, +0.25], [0.70, 1.15, 1.05], "HORN_MID",  rot=[0.0, 0.0, s * +18.0], face_mats=horn_faces),
        ]

    rarm_cubes = build_dragon_arm(-1.0)
    larm_cubes = build_dragon_arm(+1.0)

    # ==================== 3a. SỪNG VAI RỒNG (`pauldron_horn_r/l` đẩy ngoài theo vai lớn 6.75!) & GĂNG MÓNG VUỐT (`claw_gauntlet_r/l`) ====================
    phorn_faces_base = {"up": "PAULDRON_HORN_MID", "down": "HORN_DARK", "north": "PAULDRON_HORN_BASE", "south": "HORN_DARK", "east": "PAULDRON_HORN_BASE", "west": "PAULDRON_HORN_BASE"}
    phorn_faces_mid  = {"up": "PAULDRON_HORN_MID", "down": "PAULDRON_HORN_BASE", "north": "PAULDRON_HORN_MID", "south": "PAULDRON_HORN_BASE", "east": "PAULDRON_HORN_MID", "west": "PAULDRON_HORN_MID"}
    phorn_faces_tip  = {"up": "PAULDRON_HORN_TIP", "down": "PAULDRON_HORN_MID", "north": "PAULDRON_HORN_TIP", "south": "PAULDRON_HORN_MID", "east": "PAULDRON_HORN_TIP", "west": "PAULDRON_HORN_TIP"}

    def build_pauldron_horn(s: float):
        hc = []
        m_pts = [
            (s * 9.05,  23.90, -0.10),
            (s * 10.60, 24.85, +0.30),
            (s * 11.85, 26.25, +0.80),
            (s * 12.50, 27.95, +1.30),
            (s * 12.60, 29.45, +1.70),
        ]
        m_spec = [
            (2.15, 2.10, "PAULDRON_HORN_BASE", phorn_faces_base),
            (1.68, 1.65, "PAULDRON_HORN_MID",  phorn_faces_mid),
            (1.20, 1.18, "PAULDRON_HORN_MID",  phorn_faces_mid),
            (0.72, 0.72, "PAULDRON_HORN_TIP",  phorn_faces_tip),
        ]
        for idx, (w, h, mat, fm) in enumerate(m_spec):
            hc.append(make_segment_cube(m_pts[idx], m_pts[idx + 1], w, h, mat, pad=0.62, face_mats=fm))

        s_pts = [
            (s * 9.10,  22.35, +0.95),
            (s * 10.55, 22.90, +1.45),
            (s * 11.60, 23.80, +1.95),
        ]
        hc.append(make_segment_cube(s_pts[0], s_pts[1], 1.32, 1.26, "PAULDRON_HORN_BASE", pad=0.50, face_mats=phorn_faces_base))
        hc.append(make_segment_cube(s_pts[1], s_pts[2], 0.78, 0.76, "PAULDRON_HORN_TIP",  pad=0.35, face_mats=phorn_faces_tip))
        return hc

    def build_claw_gauntlet(s: float):
        cx = s * 6.0
        gc = [
            make_centered_cube([cx + s * 2.52, 13.35,  0.00], [0.60, 2.05, 4.75], "SCALE_PLATE", rot=[0.0, 0.0, s * -6.0]),
            make_centered_cube([cx + s * 0.55, 13.15, -2.42], [3.75, 1.45, 0.50], "BEVEL_STEEL", rot=[-8.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 0.62, 13.20, +2.38], [3.60, 1.45, 0.48], "SCALE_PLATE", rot=[+8.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 2.62, 12.68,  0.00], [0.50, 0.54, 4.50], "OBSIDIAN_RIM"),
            make_centered_cube([cx + s * 0.58, 12.62, -2.50], [3.60, 0.50, 0.44], "OBSIDIAN_RIM"),
        ]
        claw_specs = [
            ((cx + s * 0.25, 12.56, -2.38), (cx + s * 0.25, 11.88, -2.68), (cx + s * 0.10, 11.02, -2.28)),
            ((cx + s * 1.52, 12.56, -2.34), (cx + s * 1.72, 11.88, -2.62), (cx + s * 1.48, 11.00, -2.18)),
            ((cx + s * 2.56, 12.62, -0.82), (cx + s * 2.80, 11.90, -1.00), (cx + s * 2.40, 11.04, -0.82)),
            ((cx + s * 2.56, 12.62, +0.96), (cx + s * 2.80, 11.90, +0.80), (cx + s * 2.40, 11.10, +0.90)),
        ]
        for p0, p1, p2 in claw_specs:
            gc.append(make_segment_cube(p0, p1, 0.58, 0.58, "PAULDRON_HORN_BASE", pad=0.22, face_mats=phorn_faces_base))
            gc.append(make_segment_cube(p1, p2, 0.44, 0.44, "BOOT_CLAW_IVORY", pad=0.14))

        elbow_spurs = [
            ((cx + s * 2.75, 18.60, +1.00), (cx + s * 3.72, 19.40, +1.40), (cx + s * 4.22, 20.25, +1.60), 0.84, 0.48),
            ((cx + s * 2.75, 16.70, +1.10), (cx + s * 3.62, 17.35, +1.45), (cx + s * 4.02, 18.08, +1.60), 0.74, 0.44),
        ]
        for q0, q1, q2, w1, w2 in elbow_spurs:
            gc.append(make_segment_cube(q0, q1, w1, w1, "PAULDRON_HORN_BASE", pad=0.32, face_mats=phorn_faces_base))
            gc.append(make_segment_cube(q1, q2, w2, w2, "PAULDRON_HORN_TIP",  pad=0.22, face_mats=phorn_faces_tip))
        return gc

    pauldron_horn_r_cubes = build_pauldron_horn(-1.0)
    pauldron_horn_l_cubes = build_pauldron_horn(+1.0)
    claw_gauntlet_r_cubes = build_claw_gauntlet(-1.0)
    claw_gauntlet_l_cubes = build_claw_gauntlet(+1.0)

    # ==================== 3b. QUẦN GIÁP CÓ TẤM HÔNG + GIÁP GỐI NỔI KHỐI & ỦNG DÀY BẬC LỚN CHUẨN NETHERITE! ====================
    def build_dragon_leg(s: float):
        cx = s * 2.0
        tag = "R" if s < 0 else "L"
        outer_face = "west" if s < 0 else "east"
        inner_face = "east" if s < 0 else "west"
        return [
            # 1. Thân giáp đùi thon chuẩn tầng 2 Netherite (`4.76 x 7.05 x 4.76`) để làm NỔI BẬT BẬC ỦNG RỘNG BÊN DƯỚI!
            make_centered_cube(
                [cx, 8.45, 0.0], [4.76, 7.05, 4.76], "BASALT_PLATE",
                face_mats={
                    "north":    f"LEG_FRONT_{tag}",
                    "south":    f"LEG_BACK_{tag}",
                    outer_face: "LEG_SIDE",
                    inner_face: "BASALT_DARK",
                    "up":       "HORN_DARK",
                    "down":     "BASALT_DARK",
                }
            ),
            # 2. Tấm giáp hông (Hip Tasset) loe rõ bên ngoài đùi trên (`0.88 x 3.65 x 4.45` — thay vây mỏng yếu ớt cũ!)
            make_centered_cube(
                [cx + s * 2.66, 9.85, 0.0], [0.88, 3.65, 4.45], "HIP_TASSET",
                rot=[0.0, 0.0, s * -11.0],
                face_mats={outer_face: "HIP_TASSET", "north": "BEVEL_STEEL", "south": "BEVEL_STEEL", "down": "BEVEL_STEEL"}
            ),
            # Tấm giáp đùi trước vát khối
            make_centered_cube([cx + s * 0.12, 9.55, -2.56], [3.65, 2.75, 0.56], "SCALE_PLATE", rot=[-8.0, s * -6.0, 0.0]),
            # 3. Đai giáp gối vát sắc + GIÁP ĐẦU GỐI NỔI KHỐI 3D (Poleyn) có vết rạn nhiệt núi lửa tinh tế!
            make_centered_cube([cx, 6.45, 0.0], [5.28, 1.10, 5.38], "OBSIDIAN_RIM", rot=[+10.0, 0.0, 0.0]),
            # Khiên đầu gối 3D dày khối nổi bật phía trước đầu gối
            make_centered_cube(
                [cx, 6.18, -2.86], [3.10, 2.30, 0.85], "KNEE_TAB",
                rot=[-10.0, 0.0, 0.0],
                face_mats={"north": "KNEE_TAB", "up": "BEVEL_STEEL", "down": "BEVEL_STEEL"}
            ),
            # Gai chỏm gối sắc nhô nhẹ ở giữa
            make_centered_cube([cx, 6.08, -3.32], [1.15, 1.45, 0.52], "BEVEL_STEEL", rot=[-16.0, 0.0, 0.0]),
        ]

    rleg_cubes = build_dragon_leg(-1.0)
    lleg_cubes = build_dragon_leg(+1.0)

    def build_dragon_boot(s: float):
        """
        ỦNG GIÁP NẶNG NỔI BẬC CHUẨN NETHERITE (`armorRightBoot` / `armorLeftBoot`):
        - Thân ủng dày `5.78 x 5.20 x 5.88` (rộng hơn quần `4.76` tận `+1.02 px` -> bước nhảy khối cực rõ giống hệt ủng Netherite!).
        - Cổ ủng loe bảo vệ ống chân (`Boot Cuff / Greave Collar` rộng `6.24 x 1.38 x 6.24` ở y=4.60..5.98) + khe nhiệt mảnh!
        - Đế ủng bọc sắt dày (`6.02 x 1.00 x 6.20`) + gót giáp nhọn + mu chân bọc tấm giáp nối liền 3 móng vuốt rồng sắc nét!
        """
        cx = s * 2.0
        tag = "R" if s < 0 else "L"
        outer_face = "west" if s < 0 else "east"
        inner_face = "east" if s < 0 else "west"
        cubes = [
            # 1. Thân ủng dày khối mở hẳn ra ngoài ống quần (mép trong giữ |x|>=0.08 -> 0 đè nhau khi chạy, mép ngoài vươn tới 5.20!)
            make_centered_cube(
                [cx + s * 0.64, 2.52, -0.08], [5.12, 5.26, 5.88], "BASALT_DARK",
                face_mats={
                    "north":    "BOOT_FRONT",
                    "south":    "BOOT_BACK",
                    outer_face: f"BOOT_SIDE_{tag}",
                    inner_face: "BASALT_DARK",
                    "down":     "BOOT_SOLE",
                    "up":       "BEVEL_STEEL",
                }
            ),
            # 2. CỔ ỦNG LOE DÀY KHỐI KIỂU NETHERITE (mép trong |x|>=0.06, mép ngoài vươn tới 5.44 -> bước nhảy ủng cực nét!)
            make_centered_cube(
                [cx + s * 0.75, 4.92, -0.08], [5.38, 1.38, 6.26], "BOOT_CUFF",
                face_mats={
                    "north":    "BOOT_CUFF",
                    "south":    "BOOT_CUFF",
                    outer_face: "BOOT_CUFF",
                    inner_face: "OBSIDIAN_RIM",
                    "up":       "BEVEL_STEEL",
                    "down":     "OBSIDIAN_RIM",
                }
            ),
            # Nẹp ống chân trước vát nhọn (Shin Greave Ridge) — sạch gọn không vẽ vết nứt cam!
            make_centered_cube(
                [cx + s * 0.45, 3.65, -3.18], [2.45, 3.15, 0.62], "BEVEL_STEEL",
                rot=[-8.0, 0.0, 0.0],
                face_mats={"north": "BOOT_FRONT", "up": "BEVEL_STEEL"}
            ),

            # 3. ĐẾ ỦNG BỌC SẮT DÀY + BỌC GÓT CHÂN HẦM HỐ (0 đè nhau ở x=0, đáy y=-0.12 0 z-fighting!)
            make_centered_cube(
                [cx + s * 0.72, 0.42, -0.20], [5.32, 1.08, 6.26], "BEVEL_STEEL",
                face_mats={"down": "BOOT_SOLE", "up": "OBSIDIAN_RIM"}
            ),
            # Cựa gót giáp sau (Heel Guard)
            make_centered_cube([cx + s * 0.55, 1.65, +3.14], [3.20, 2.30, 0.68], "BEVEL_STEEL", rot=[+12.0, 0.0, 0.0]),
            # Cánh bảo vệ mắt cá ngoài
            make_centered_cube([cx + s * 3.38, 2.20, +0.10], [0.62, 2.30, 2.80], "SCALE_PLATE", rot=[0.0, 0.0, s * -10.0]),

            # 4. Tấm giáp mu bàn chân vát dốc (Sabaton Instep) nối liền thân ủng dày ra 3 móng vuốt rồng!
            make_centered_cube(
                [cx + s * 0.52, 1.48, -3.28], [4.50, 1.48, 1.44], "BEVEL_STEEL",
                rot=[+24.0, 0.0, 0.0],
                face_mats={"up": "BOOT_FRONT", "north": "OBSIDIAN_RIM"}
            ),
        ]

        # 3 NGÓN MÓNG VUỐT RỒNG 3D SẮC NÉT MỖI CHIẾC ỦNG
        for offset_x in (-1.38, 0.0, +1.38):
            bx = cx + s * 0.52 + offset_x
            cubes.append(make_centered_cube(
                [bx, 0.56, -3.40], [0.94, 1.36, 1.15], "CLAW_CRIMSON",
                face_mats={**claw_tip_faces, "down": "BOOT_SOLE"},
            ))
            cubes.append(make_segment_cube(
                (bx, 1.98, -2.75),
                (bx, 0.96, -4.05),
                0.92, 0.88, "CLAW_CRIMSON", pad=0.18, face_mats=claw_tip_faces,
            ))
            cubes.append(make_centered_cube(
                [bx, 0.44, -4.16], [0.92, 1.12, 1.30], "BOOT_CLAW_IVORY"
            ))
        return cubes

    rboot_cubes = build_dragon_boot(-1.0)
    lboot_cubes = build_dragon_boot(+1.0)

    bones = [
        {"name": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},
        {"name": "horn_r",    "parent": "armorHead", "pivot": [-4.85, 31.6, 0.0], "cubes": horn_r_cubes},
        {"name": "horn_l",    "parent": "armorHead", "pivot": [+4.85, 31.6, 0.0], "cubes": horn_l_cubes},

        {"name": "bipedBody",     "pivot": [0, 24, 0]},
        {"name": "armorBody",     "parent": "bipedBody",     "pivot": [0, 24, 0], "cubes": body_cubes},
        {"name": "wing_r_upper",  "parent": "armorBody",     "pivot": [-2.2, 21.5, +3.05],  "cubes": wing_r_upper},
        {"name": "wing_r_mid",    "parent": "wing_r_upper",  "pivot": [-8.03, 25.3, +5.17], "cubes": wing_r_mid},
        {"name": "wing_r_tip",    "parent": "wing_r_mid",    "pivot": [-14.42, 27.9, +7.50],"cubes": wing_r_tip},
        {"name": "wing_l_upper",  "parent": "armorBody",     "pivot": [+2.2, 21.5, +3.05],  "cubes": wing_l_upper},
        {"name": "wing_l_mid",    "parent": "wing_l_upper",  "pivot": [+8.03, 25.3, +5.17], "cubes": wing_l_mid},
        {"name": "wing_l_tip",    "parent": "wing_l_mid",    "pivot": [+14.42, 27.9, +7.50],"cubes": wing_l_tip},
        {"name": "tail_1",        "parent": "armorBody",     "pivot": [0.0, 12.60, +2.60 + tz],  "cubes": tail_1_cubes},
        {"name": "tail_2",        "parent": "tail_1",        "pivot": [0.0, 11.45, +5.90 + tz],  "cubes": tail_2_cubes},
        {"name": "tail_3",        "parent": "tail_2",        "pivot": [0.0, 10.70, +9.25 + tz],  "cubes": tail_3_cubes},
        {"name": "tail_4",        "parent": "tail_3",        "pivot": [0.0, 10.95, +12.55 + tz], "cubes": tail_4_cubes},
        {"name": "tail_tip",      "parent": "tail_4",        "pivot": [0.0, 11.70, +15.60 + tz], "cubes": tail_tip_cubes},

        {"name": "bipedRightArm",   "pivot": [-5, 22, 0]},
        {"name": "armorRightArm",   "parent": "bipedRightArm", "pivot": [-5, 22, 0], "cubes": rarm_cubes},
        {"name": "pauldron_horn_r", "parent": "armorRightArm", "pivot": [-9.05, 23.90, -0.10], "cubes": pauldron_horn_r_cubes},
        {"name": "claw_gauntlet_r", "parent": "armorRightArm", "pivot": [-6.0,  13.40,  0.00], "cubes": claw_gauntlet_r_cubes},

        {"name": "bipedLeftArm",    "pivot": [5, 22, 0]},
        {"name": "armorLeftArm",    "parent": "bipedLeftArm",  "pivot": [5, 22, 0], "cubes": larm_cubes},
        {"name": "pauldron_horn_l", "parent": "armorLeftArm",  "pivot": [+9.05, 23.90, -0.10], "cubes": pauldron_horn_l_cubes},
        {"name": "claw_gauntlet_l", "parent": "armorLeftArm",  "pivot": [+6.0,  13.40,  0.00], "cubes": claw_gauntlet_l_cubes},

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
                    "identifier": "geometry.cinder_dragon_armor",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 4.4,
                    "visible_bounds_height": 4.2,
                    "visible_bounds_offset": [0.0, 1.4, 0.25],
                },
                "bones": bones,
            }
        ],
    }
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out = ASSETS / "geo" / "armor" / "cinder_dragon_armor.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, {TEX_W}x{TEX_H} 1:1 UV)")
    return geo, total_cubes


def build_cinder_dragon_armor_animations():
    anims = {
        "animation.cinder_dragon_armor.idle": {
            "loop": True,
            "animation_length": 3.2,
            "bones": {},
        }
    }
    out = ASSETS / "animations" / "armor" / "cinder_dragon_armor.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out}")


def build_cinder_dragon_armor_textures():
    """
    Texture pixel-art 1:1 (`128x128`) nâng cấp giáp rèn núi lửa:
    - 90% mảng giáp Bazan-Obsidian rèn viền sáng sắc nét (`#1B181E -> #342F38 -> #524B58 -> #746C7C`), nổi bật rõ ngay cả trong đêm!
    - ~10% vết nứt nhiệt núi lửa (`#A82606 -> #F2640A -> #FFD24C`) len qua khe vai, mảng ngực, cổ tay, gối, cổ ủng & trán mũ
      kèm `glowmask` phát sáng fullbright tinh tế -> tạo cảm giác bộ giáp thực sự đang rực sức nóng từ bên trong!
    """
    verify_uv_and_wing_padding()
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    P = {
        ".": (0, 0, 0, 0),
        # Thép-bazan rèn núi lửa độ tương phản cao (đọc rõ từng góc vát, tầng giáp, đinh tán ngay cả ban đêm!)
        "0": (22, 19, 22, 255),       # #161316 — khe nối sâu
        "1": (36, 32, 36, 255),       # #242024 — nền giáp tối
        "2": (54, 49, 54, 255),       # #363136 — thân tấm giáp bazan
        "3": (78, 71, 76, 255),       # #4E474C — mặt giáp vát sáng
        "4": (110, 102, 108, 255),    # #6E666C — cạnh vát thép rèn cực sắc nét!
        "5": (138, 130, 136, 255),    # #8A8288 — đỉnh ánh thép trên gờ vai/ủng/mũ!

        # Obsidian xám-tím than cho sừng, khung cánh, lưỡi liềm đuôi
        "o": (26, 23, 32, 255),
        "O": (44, 39, 54, 255),
        "p": (66, 59, 80, 255),
        "P": (98, 90, 116, 255),

        # Màng cánh đỏ thẫm rồng lửa
        "w": (92, 18, 16, 255),
        "W": (143, 31, 24, 255),
        "m": (184, 46, 34, 255),

        # Viền đỏ núi lửa trầm (không phát sáng — dùng làm rìa cháy sém quanh vết nứt nhiệt!)
        "r": (88, 24, 14, 255),       # #58180E — rìa đá nung đỏ sẫm
        "R": (132, 36, 16, 255),      # #842410 — đá nung đỏ đậm
        "k": (166, 48, 18, 255),

        # Lớp lót tay đá tro tối
        "a": (30, 27, 30, 255),
        "A": (46, 42, 46, 255),

        # Trắng ngà phong hóa (răng nanh, móng vuốt tay/chân)
        "b": (156, 149, 139, 255),
        "B": (202, 195, 184, 255),
        "I": (232, 226, 214, 255),
        "g": (143, 136, 126, 255),
        "G": (190, 183, 172, 255),
        "E": (221, 214, 202, 255),

        # Đá núi lửa xám-nâu
        "d": (36, 32, 32, 255),
        "D": (58, 52, 50, 255),
        "s": (86, 78, 74, 255),

        # SỨC NÓNG NÚI LỬA (~10%): Đỏ magma sẫm -> Cam dung nham cháy -> Vàng lõi nhiệt (chỉ các ký tự v, f, F, Y, H phát sáng!)
        "v": (176, 44, 6, 255),       # #B02C06 — rìa nứt nhiệt đỏ dung nham (glow dịu)
        "f": (228, 82, 6, 255),       # #E45206 — mạch dung nham nóng chảy
        "F": (255, 142, 24, 255),     # #FF8E18 — tâm vết nứt nhiệt rực cam
        "Y": (255, 220, 96, 255),     # #FFDC60 — điểm cực nóng lõi ngọc / mắt rồng
        "H": (255, 246, 194, 255),    # #FFF6C2 — lõi tinh thể trắng-vàng
    }
    GLOW_CHARS = {"v", "f", "F", "Y", "H"}

    def paint(key: str, rows, emit_glow: bool = False):
        x0, y0, w, h = REG[key]
        assert len(rows) == h, f"{key}: expected {h} rows, got {len(rows)}"
        for y, row in enumerate(rows):
            assert len(row) == w, f"{key} row {y}: expected {w} chars, got {len(row)}"
            for x, ch in enumerate(row):
                col = P[ch]
                diff[y0 + y, x0 + x] = col
                if emit_glow and ch in GLOW_CHARS:
                    # Glow dịu hơn một chút ở viền `v` để mạch nhiệt sâu & tự nhiên, không chói gắt!
                    if ch == "v":
                        glow[y0 + y, x0 + x] = (col[0], col[1], col[2], 195)
                    else:
                        glow[y0 + y, x0 + x] = col

    # 1. MŨ ĐẦU RỒNG (sạch sắc nét bằng thép-bazan rèn gờ sáng, BỎ toàn bộ vết nứt cam xung quanh — CHỈ giữ mắt rồng phát sáng!)
    paint("HELM_BACK", [
        "13444431",
        "23211232",
        "12322321",
        "23122132",
        "12322321",
        "23211232",
        "12344321",
        "01222210",
    ])
    paint("HELM_SIDE", [
        "13454321",
        "23432321",
        "12321221",
        "23212232",
        "12222211",
        "23432211",
        "12321210",
        "01222110",
    ])
    paint("HELM_TOP", [
        "13455431",
        "23233232",
        "23422432",
        "12322321",
        "12344321",
        "23233232",
        "13455431",
        "12233221",
    ])
    paint("CHEEK_GUARD", [
        "3453",
        "2342",
        "2332",
        "1231",
        "0141",
    ])
    paint("DRAGON_SNOUT", [
        "245542",
        "123321",
        "232232",
        "123321",
    ])
    paint("DRAGON_NOSE", [
        "245542",
        "203302",
        "123321",
    ])
    paint("DRAGON_EYE", [
        "F0F",
        "f0Y",
    ], emit_glow=True)
    paint("FANG_IVORY", ["B", "I", "I"])

    # 2. VẬT LIỆU PHẲNG & GỜ RÈN SẮC NÉT
    paint("HORN_BASE", ["oOpo", "OpoO", "oOpo", "ooOo"])
    paint("HORN_MID",  ["OpOo", "ppOp", "oOpo", "ooOo"])
    paint("HORN_DARK", ["oOOo", "ooOo", "oooo", "0oo0"])
    paint("BONE_LIGHT", ["IIBB", "IBBb", "BBbb", "bbbb"])
    paint("BASALT_PLATE", [
        "2343",
        "3232",
        "1221",
        "1121",
    ])
    paint("BASALT_DARK", [
        "1221",
        "1120",
        "1010",
        "0000",
    ])
    paint("BEVEL_STEEL", [
        "4554",
        "3443",
        "2332",
        "1221",
    ])
    paint("MAGMA_SEAM", ["oOpo", "OOpO", "oOOo", "0oo0"])
    paint("CRIMSON_TRIM", ["RkkR", "rRRr", "rRRr", "rrr0"])
    paint("WING_BONE", ["oOpo", "OOpO", "oOOo", "0oo0"])
    paint("TAIL_SEG", [
        "1341",
        "2342",
        "1231",
        "0120",
    ])
    paint("TAIL_CRYSTAL", [
        "fFFf",
        "FYYF",
        "FYYF",
        "fFFf",
    ], emit_glow=True)
    paint("CHEST_GEM", [
        "fFYF",
        "FYHY",
        "YHHY",
        "FFYf",
    ], emit_glow=True)
    paint("CLAW_CRIMSON", ["OpOp", "pOOp", "oOOr", "ooOo"])
    paint("OBSIDIAN_RIM", ["oOpP", "OOpO", "oOOo", "0oo0"])
    paint("SCALE_PLATE", [
        "3453",
        "2342",
        "2332",
        "1221",
    ])
    paint("SCALE_RING", [
        "34543",
        "23432",
        "12121",
    ])
    paint("PAULDRON_HORN_BASE", ["oOpP", "OOpP", "oOpo", "ooOo"])
    paint("PAULDRON_HORN_MID",  ["OpPP", "pPPp", "OOpP", "oOpo"])
    paint("PAULDRON_HORN_TIP",  ["pPbB", "PbBB", "pPbB", "OpPP"])
    paint("EMPTY_TRANS", ["."])

    # 3. NGỰC & LƯNG (CHỈ giữ phần LÕI DUNG NHAM ở giữa ngực sáng nổi bật, xung quanh toàn bộ là giáp thép-bazan sạch sẽ!)
    paint("CHEST_CAVITY", [
        "1232rr2321",
        "2322vf2232",
        "122rvFv221",
        "232vfFf232",
        "122vFYFv21",
        "12rvFYFv21",
        "122fFYYf21",
        "122fFYYf21",
        "122vfFFv21",
        "1222vff221",
        "1232rr2321",
        "0122222210",
    ], emit_glow=True)
    # Tấm giáp ngực nổi khối 5x6: thép-bazan rèn gờ sáng sạch sẽ, KHÔNG có vết nứt xung quanh!
    paint("CHEST_PECTORAL", [
        "34554",
        "23432",
        "23321",
        "23321",
        "12232",
        "01342",
    ])
    paint("CHEST_FRONT", [
        "13422431",
        "23422432",
        "12322321",
        "232dd232",
        "122DD221",
        "232dd232",
        "122DD221",
        "232dd232",
        "12322321",
        "23233232",
        "oOp44pOo",
        "01233210",
    ])
    paint("CHEST_CORE", [
        "dDDd", "DssD", "dfFd", "dFYd", "dYYd",
        "dFYd", "dfFd", "DssD", "dDDd", "rRRr",
    ], emit_glow=False)
    paint("CHEST_BACK", [
        "234oo432",
        "123Op321",
        "234oo432",
        "123Op321",
        "234oo432",
        "123Op321",
        "234oo432",
        "123Op321",
        "234oo432",
        "123Op321",
        "12oOpo21",
        "012oo210",
    ])
    paint("CHEST_SIDE", [
        "1342", "2342", "1221", "0110",
        "1232", "2342", "1221", "0110",
        "1232", "2332", "oOpo", "0110",
    ])
    paint("BACK_SHELL", [
        "24op42", "34po43", "12op21", "34po43", "23op32",
        "34po43", "12op21", "34po43", "23op32", "12oo21",
    ])

    # 4. VAI GIÁP KHỔNG LỒ 6x6 & GĂNG CỔ TAY LOE 6x4 (sạch gọn bằng thép-bazan rèn gờ sáng, 0 vết nứt cam!)
    paint("PAULDRON_FRONT", [
        "345543",
        "234432",
        "233321",
        "123321",
        "123331",
        "014541",
    ])
    paint("PAULDRON_OUT", [
        "345543",
        "234432",
        "233321",
        "234321",
        "123321",
        "014541",
    ])
    paint("PAULDRON_TOP", [
        "345543",
        "343343",
        "232232",
        "233332",
        "233332",
        "134431",
    ])
    paint("ARM_SLEEVE", [
        "aAAa", "A23A", "aAAa", "A32A", "aAAa", "A23A",
        "aAAa", "A32A", "aAAa", "a22a", "aaaa",
    ])
    paint("ARM_CAP", ["aaaa", "aAAa", "aAAa", "aaaa"])
    paint("ARM_BAND", ["34532", "12321"])
    paint("GAUNTLET_CUFF", [
        "345543",
        "233332",
        "123321",
        "013431",
    ])

    # 5. ĐAI EO, TẤM HÔNG, QUẦN, GIÁP GỐI & ỦNG DÀY NỔI BẬC (sạch gọn thép-bazan, CHỈ giữ điểm lõi nhỏ ở khóa đai giữa!)
    paint("WAIST_BELT_FRONT", [
        "oOp4554pOo",
        "ooO2332Ooo",
    ])
    paint("WAIST_BUCKLE", [
        "3453",
        "4Ff4",
        "4FF4",
        "2342",
    ], emit_glow=True)
    paint("HIP_TASSET", [
        "3453",
        "2342",
        "2332",
        "1231",
        "0341",
    ])
    paint("ASH_STONE", ["3453", "2342", "2332", "1221"])
    paint("KNEE_TAB", [
        "3453",
        "2342",
        "2331",
        "1231",
    ])
    paint("LEG_THIGH", [
        "2342", "1231", "2342", "1221",
        "2342", "1231", "2331", "1221",
    ])
    paint("LEG_BELT", ["oOpOo", "23432", "12321"])
    leg_front_r = [
        "o342", "2342", "1231", "3432",
        "2331", "1231", "2342", "1221",
    ]
    paint("LEG_FRONT_R", leg_front_r)
    paint("LEG_FRONT_L", [row[::-1] for row in leg_front_r])
    leg_back_l = [
        "o231", "2342", "1231", "2331",
        "1221", "1231", "2342", "1221",
    ]
    paint("LEG_BACK_L", leg_back_l)
    paint("LEG_BACK_R", [row[::-1] for row in leg_back_l])
    paint("LEG_SIDE", [
        "1341", "2342", "1231", "2342",
        "1231", "2332", "1231", "1221",
    ])

    # Cổ ủng loe & Thân ủng dày: thép-bazan sạch sẽ sắc cạnh chuẩn Netherite (0 vết nứt cam!)
    paint("BOOT_CUFF", [
        "455554",
        "234321",
        "123321",
    ])
    paint("BOOT_FRONT", [
        "34543",
        "23432",
        "23421",
        "12331",
        "oOpPo",
        "12421",
    ])
    boot_side_r = [
        "34542", "23432", "12331",
        "2332O", "123Op", "01421",
    ]
    paint("BOOT_SIDE_R", boot_side_r)
    paint("BOOT_SIDE_L", [row[::-1] for row in boot_side_r])
    paint("BOOT_BACK", [
        "34543", "23432", "12321",
        "12221", "o232o", "01410",
    ])
    paint("BOOT_SOLE", [
        "0oo0o", "o121o", "o212o", "o121o", "0oo0o",
    ])
    paint("BOOT_CLAW", ["II", "bB"])
    paint("BOOT_CLAW_IVORY", [
        "BIIB", "IIBI", "BIIb", "bBBb",
    ])

    # 6. 3 TẤM MÀNG CÁNH DƠI ĐỎ THẪM (GIỮ NGUYÊN 100% — sạch tuyệt đối 0 đường viền lỗi UV!)
    def build_wing_mem_rows(w: int, h: int, t0: float, t1: float, y0: float, y1: float, top_fn, bot_fn):
        rows = []
        for r in range(h):
            y = y1 - (r + 0.5) / h * (y1 - y0)
            chars = []
            for c in range(w):
                t = t1 - (c + 0.5) / w * (t1 - t0)
                yt = top_fn(t)
                yb = bot_fn(t)
                if yb <= y <= yt:
                    d_top = yt - y
                    d_bot = y - yb
                    if d_top < 0.85 or d_bot < 0.75:
                        chars.append("w")
                    elif ((c * 3 + r) % 5 == 0) or (2.2 < d_top < 5.5 and (c + r) % 2 == 0):
                        chars.append("m")
                    else:
                        chars.append("W")
                else:
                    chars.append(".")
            rows.append("".join(chars))
        return rows

    def arch(t, ta, ya, tb, yb, lift):
        u = max(0.0, min(1.0, (t - ta) / max(1e-5, tb - ta)))
        return ya + (yb - ya) * u + lift * 4.0 * u * (1.0 - u)

    mem_inner_r = build_wing_mem_rows(
        10, 16, 0.0, 6.30, 14.50, 25.50,
        top_fn=lambda t: 21.50 + (25.30 - 21.50) * min(1.0, max(0.0, t / 6.20)),
        bot_fn=lambda t: arch(t, 0.0, 14.90, 5.80, 14.70, lift=2.55) if t <= 5.80 else 14.70 + (t - 5.80) * 0.4,
    )
    mem_mid_r = build_wing_mem_rows(
        12, 20, 5.70, 13.50, 14.30, 28.10,
        top_fn=lambda t: 25.30 + (27.90 - 25.30) * min(1.0, max(0.0, (t - 6.20) / (13.00 - 6.20))),
        bot_fn=lambda t: (
            arch(t, 5.80, 14.70, 9.60, 14.50, lift=2.90)
            if t <= 9.60
            else arch(t, 9.60, 14.50, 13.40, 15.10, lift=3.10)
        ),
    )
    mem_outer_r = build_wing_mem_rows(
        12, 18, 13.00, 20.80, 14.90, 28.10,
        top_fn=lambda t: 27.90 + (25.30 - 27.90) * min(1.0, max(0.0, (t - 13.00) / (20.60 - 13.00))),
        bot_fn=lambda t: (
            arch(t, 13.40, 15.10, 17.40, 17.70, lift=2.80)
            if t <= 17.40
            else (17.70 + (25.30 - 17.70) * (max(0.0, min(1.0, (t - 17.40) / (20.60 - 17.40))) ** 0.72))
        ),
    )
    paint("WING_MEM_INNER_R", mem_inner_r)
    paint("WING_MEM_MID_R",   mem_mid_r)
    paint("WING_MEM_OUTER_R", mem_outer_r)
    paint("WING_MEM_INNER_L", [row[::-1] for row in mem_inner_r])
    paint("WING_MEM_MID_L",   [row[::-1] for row in mem_mid_r])
    paint("WING_MEM_OUTER_L", [row[::-1] for row in mem_outer_r])

    # Kiểm chứng padding 2px quanh màng cánh hoàn toàn alpha=0 (0 viền đen lỗi UV!)
    for k in ("WING_MEM_INNER_R", "WING_MEM_MID_R", "WING_MEM_OUTER_R", "WING_MEM_INNER_L", "WING_MEM_MID_L", "WING_MEM_OUTER_L"):
        x0, y0, w, h = REG[k]
        sub_a = diff[y0:y0 + h, x0:x0 + w, 3]
        assert np.all((sub_a == 0) | (sub_a == 255)), f"Non-binary alpha in {k}"
        pad = diff[y0 - 2:y0 + h + 2, x0 - 2:x0 + w + 2, 3].copy()
        pad[2:2 + h, 2:2 + w] = 0
        assert not np.any(pad != 0), f"Non-zero padding around {k}"

    glow_px = int(np.sum(glow[:, :, 3] > 0))
    opaque_px = int(np.sum(diff[:, :, 3] > 0))
    out_dir = ASSETS / "textures" / "armor"
    out_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(out_dir / "cinder_dragon_armor.png")
    Image.fromarray(glow, "RGBA").save(out_dir / "cinder_dragon_armor_glowmask.png")
    print(f"TEX_ARMOR_OK: {TEX_W}x{TEX_H} (glowTexels={glow_px}/{opaque_px} = {100.0 * glow_px / max(1, opaque_px):.1f}%)")
    return diff.astype(np.float32), glow.astype(np.float32)


def update_helmet_item_icon():
    PAL = {
        ".": (0, 0, 0, 0),
        "0": (14, 12, 14, 255),
        "1": (27, 24, 27, 255),
        "2": (46, 42, 46, 255),
        "3": (72, 66, 72, 255),
        "4": (106, 98, 106, 255),
        "o": (22, 19, 28, 255),
        "O": (46, 40, 58, 255),
        "I": (232, 226, 214, 255),
        "f": (228, 82, 6, 255),
        "F": (255, 142, 24, 255),
        "Y": (255, 220, 96, 255),
    }
    rows = [
        "................",
        ".0O0...00...0O0.",
        ".0oO0.0oO0.0Oo0.",
        "..0oO00oO00Oo0..",
        "..0o02344320o0..",
        ".0O0233333320O0.",
        ".0o0F023320F0o0.",
        "..00Y023320Y00..",
        "..032320023230..",
        "..04I234432I40..",
        "..03I.0000.I30..",
        "..02I......I20..",
        "...00......00...",
        "................",
        "................",
        "................",
    ]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            px[x, y] = PAL[ch]
    out = ASSETS / "textures" / "item" / "cinder_dragon_helmet.png"
    im.save(out)
    print(f"HELM_ICON_OK: {out}")


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
    """Dựng phom chuẩn giáp Netherite Vanilla (+1.0 inflate mũ/ngực/vai/ủng, +0.5 inflate quần) để đối chiếu trực tiếp!"""
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
    # Helmet (+1.0)
    add_box((-5.0, 29.0, -5.0), (5.0, 33.0, 5.0), c_mid)
    add_box((-5.0, 23.8, -3.5), (-4.0, 29.0, 5.0), c_dark)
    add_box((+4.0, 23.8, -3.5), (+5.0, 29.0, 5.0), c_dark)
    add_box((-5.0, 23.8, +4.0), (+5.0, 29.0, 5.0), c_dark)
    # Chestplate (+1.0)
    add_box((-5.0, 11.8, -3.0), (5.0, 24.5, 3.0), c_mid)
    # Pauldrons (+1.0 shoulder cap, bare lower arm below y=18!)
    add_box((-9.0, 18.2, -3.0), (-3.8, 25.0, 3.0), c_mid)
    add_box((+3.8, 18.2, -3.0), (+9.0, 25.0, 3.0), c_mid)
    # Leggings (+0.5)
    add_box((-4.5, 5.2, -2.5), (-0.1, 12.2, 2.5), c_dark)
    add_box((+0.1, 5.2, -2.5), (+4.5, 12.2, 2.5), c_dark)
    # Chunky Boots (+1.0)
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

    def raster(face_list, view_mat, w, h, sc, center, ambient=0.58, bg=(50, 54, 68), track_glow=False):
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        armor_mask = np.zeros((h, w), dtype=bool)
        glow_mask = np.zeros((h, w), dtype=bool)
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
                    is_g = (g_a[:, 0] > 0.25)
                    lit = np.where(g_a > 0.25, lit * (1.0 - g_a * 0.75) + glow_img[v[vis], u[vis], :3] * (g_a * 1.05), lit)
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
                    if track_glow:
                        armor_mask[y0 + cy_i, x0 + cx_i] = True
                        glow_mask[y0 + cy_i, x0 + cx_i] = is_g
        img = Image.fromarray(fb.astype(np.uint8), "RGB")
        if track_glow:
            pct = 100.0 * float(np.sum(glow_mask)) / max(1.0, float(np.sum(armor_mask)))
            return img, pct
        return img

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    ctr_full = np.array([0.0, 18.5, 1.5], dtype=np.float64)
    ctr_body = np.array([0.0, 16.5, -0.5], dtype=np.float64)
    ctr_legs = np.array([0.0, 6.2, -0.4], dtype=np.float64)
    ctr_side = np.array([0.0, 18.5, +5.0], dtype=np.float64)

    pw, ph = 460, 460
    im_neth = raster(build_netherite_ref_faces(), cam_mat(180, 4), pw, ph, 10.2, ctr_body, ambient=0.42, bg=(18, 22, 24))
    im_night, pct_front = raster(armor_faces, cam_mat(180, 4), pw, ph, 10.2, ctr_body, ambient=0.34, bg=(14, 17, 20), track_glow=True)
    im_day, _ = raster(armor_faces, cam_mat(180, 4), pw, ph, 8.8, ctr_full, ambient=0.62, bg=(46, 52, 66), track_glow=True)
    im_34, pct_34 = raster(armor_faces, cam_mat(144, 8), pw, ph, 11.2, ctr_body, ambient=0.45, bg=(22, 24, 32), track_glow=True)
    im_back, pct_back = raster(armor_faces, cam_mat(0, 4), pw, ph, 8.8, ctr_full, ambient=0.45, bg=(22, 24, 32), track_glow=True)
    im_legs, _ = raster(armor_faces, cam_mat(162, 6), pw, ph, 25.0, ctr_legs, ambient=0.52, bg=(28, 30, 38), track_glow=True)

    avg_glow_pct = (pct_front + pct_34 + pct_back) / 3.0
    print(f"VISIBLE_VOLCANIC_GLOW_RATIO: Front={pct_front:.1f}%, 3/4={pct_34:.1f}%, Back={pct_back:.1f}% | Avg={avg_glow_pct:.1f}%")

    sheet = Image.new("RGB", (pw * 3, ph * 2), (12, 13, 18))
    panels = [
        (im_neth,  0, 0, "1. REF: VANILLA NETHERITE VOLUME (+1.0 PAULDRON/BOOTS)"),
        (im_night, 1, 0, f"2. CINDER DRAGON v3 NIGHT (VOLCANIC HEAT {pct_front:.1f}%)"),
        (im_day,   2, 0, "3. CINDER DRAGON v3 DAY FULL (WINGS + PAULDRONS + BOOTS)"),
        (im_34,    0, 1, f"4. 3/4 HERO VIEW (HEAVY PAULDRONS, CHEEKS, BOOTS - {pct_34:.1f}%)"),
        (im_back,  1, 1, f"5. BACK VIEW (WINGS + TAIL + SPINE VEINS - {pct_back:.1f}%)"),
        (im_legs,  2, 1, "6. CLOSE-UP: CHUNKY STEPPED BOOTS + KNEE POLEYN + TASSETS"),
    ]
    dr = ImageDraw.Draw(sheet)
    for im, gx, gy, title in panels:
        sheet.paste(im, (gx * pw, gy * ph))
        dr.text((gx * pw + 8, gy * ph + 8), title, fill=(246, 224, 172))

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out_preview = PREVIEW_DIR / "cinder_dragon_armor_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")

    # Bảng cận cảnh riêng CHÂN & ỦNG (4 góc) để soi rõ độ dày bậc ủng chuẩn Netherite!
    lg_front = raster(armor_faces, cam_mat(180, 0), pw, ph, 27.5, ctr_legs, ambient=0.52, bg=(28, 30, 38))
    lg_right = raster(armor_faces, cam_mat(+90, 0), pw, ph, 27.5, ctr_legs, ambient=0.52, bg=(28, 30, 38))
    lg_back  = raster(armor_faces, cam_mat(  0, 0), pw, ph, 27.5, ctr_legs, ambient=0.52, bg=(28, 30, 38))
    lg_side  = raster(armor_faces, cam_mat(+90, 4), pw, ph,  9.2, ctr_side, ambient=0.50, bg=(28, 30, 38))
    legs_sheet = Image.new("RGB", (pw * 2, ph * 2), (12, 13, 18))
    dr2 = ImageDraw.Draw(legs_sheet)
    for im, gx, gy, title in [
        (lg_front, 0, 0, "1. LEGS & CHUNKY BOOTS FRONT (CUFF + GREAVE + 6 CLAWS)"),
        (lg_right, 1, 0, "2. RIGHT PROFILE (STEPPED BOOT + HEEL GUARD + KNEE)"),
        (lg_back,  0, 1, "3. BACK VIEW (BOOT CUFF + HEEL + TASSETS)"),
        (lg_side,  1, 1, "4. FULL SIDE PROFILE (HEAD + PAULDRON + WINGS + TAIL)"),
    ]:
        legs_sheet.paste(im, (gx * pw, gy * ph))
        dr2.text((gx * pw + 8, gy * ph + 8), title, fill=(246, 224, 172))
    out_legs = PREVIEW_DIR / "cinder_dragon_armor_legs_boots_preview.png"
    legs_sheet.save(out_legs)
    print(f"LEGS_PREVIEW_OK: {out_legs}")


if __name__ == "__main__":
    geo_data, cubes = build_cinder_dragon_armor_geo()
    build_cinder_dragon_armor_animations()
    diff_img, glow_img = build_cinder_dragon_armor_textures()
    update_helmet_item_icon()
    render_multi_angle_preview(geo_data, diff_img, glow_img)
    print(f"ALL_DONE: Cinder Dragon Armor v3 (Heavy Netherite-Class Pauldrons & Boots + ~10% Volcanic Magma Veins, {cubes} cubes)")
