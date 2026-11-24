#!/usr/bin/env python3
"""
Bộ sinh 1:1 chuẩn mật độ pixel-art Minecraft (`128x128`, `1 texel = 1 model px`, ZERO noise)
cho **Cinder Dragon Armor** — bản nâng cấp chuẩn Boss Cinder Dragon (`acd1.txt`):
  1. Mũ giáp (`armorHead`, `horn_r`, `horn_l`): Đầu rồng Cinder Dragon hoàn chỉnh — mõm vảy đá núi lửa nhô về trước,
     2 lỗ mũi, sừng mũi obsidian (`nasal_horn`), 2 cặp răng nanh trắng ngà ôm 2 bên mặt (lộ rõ khuôn mặt người chơi bên dưới),
     gờ mày vát xiên + đôi mắt rồng cam-vàng con ngươi dọc phát sáng (glowmask), 2 cặp sừng vương miện obsidian cong ngược
     ra sau (0 kim trắng mảnh), và 3 gai sống đỉnh 3D khối đặc (0 tấm mặt phẳng mỏng nhiễu pixel).
  2. Đôi cánh rồng hoàn chỉnh (`wing_r_upper/mid/tip`, `wing_l_upper/mid/tip`): 3 khớp xương liền mạch, sải 2.4x bề rộng vai
     (~39 px), móng cổ cánh obsidian (`thumb claw`), gai sống cánh, 4 nan xương kéo sát mép dưới, màng cánh đỏ thẫm cắt
     cung dơi sạch tuyệt đối (4 mặt cạnh `EMPTY_TRANS` + đệm `>= 4 px` trong suốt quanh UV -> 0 đường kẻ đen lỗi UV!).
  3. Đuôi rồng dài (`tail_1..tail_4` -> `tail_tip`): 4 đốt phân đốt thon dần (~16.5 px, cong chữ S hếch nhẹ không kéo đất)
     có gai sống lưng 3D + chùy đuôi 2 lưỡi liềm obsidian cong đối xứng vào trong ôm lõi tinh thể than hồng cam-vàng
     phát sáng (glowmask) & 1 gai trục obsidian thẳng ở chính giữa y hệt đuôi Boss Cinder Dragon.
  4. Giữ nguyên 100% thân ngực/lõi magma, cánh tay bọc kín, quần giáp và giày 4 móng vuốt!

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

# Atlas 128x128 chuẩn mật độ 1:1 (`1 texel = 1 model px`) để chừa >= 4 px padding trong suốt quanh mọi màng cánh!
TEX_W = 128
TEX_H = 128

REG = {
    # Hàng 0 (y=0..8): Vỏ đầu rồng + mõm + mắt rồng + vật liệu 4x4 phẳng sạch
    "HELM_BACK":       (0,   0,  8,  8),
    "HELM_SIDE":       (8,   0,  8,  8),
    "HELM_TOP":        (16,  0,  8,  8),
    "DRAGON_SNOUT":    (24,  0,  6,  4),
    "DRAGON_NOSE":     (24,  4,  6,  3),
    "DRAGON_EYE":      (32,  0,  3,  2),
    "HORN_BASE":       (36,  0,  4,  4),
    "HORN_MID":        (40,  0,  4,  4),
    "HORN_DARK":       (44,  0,  4,  4),
    "BONE_LIGHT":      (48,  0,  4,  4),
    "BASALT_PLATE":    (52,  0,  4,  4),
    "BASALT_DARK":     (56,  0,  4,  4),
    "CRIMSON_TRIM":    (60,  0,  4,  4),
    "WING_BONE":       (64,  0,  4,  4),
    "TAIL_SEG":        (68,  0,  4,  4),
    "TAIL_CRYSTAL":    (72,  0,  4,  4),
    "ARM_CAP":         (76,  0,  4,  4),
    "BOOT_CLAW":       (80,  0,  2,  2),
    "FANG_IVORY":      (84,  0,  1,  3),
    "CHEST_GEM":       (88,  0,  4,  4),  # 3 viên ngọc tinh thể hỏa long trước ngực (fullbright glowmask)
    "CLAW_CRIMSON":    (94,  0,  4,  4),  # Đầu móng kẹp đỏ núi lửa trầm ánh thép
    "EMPTY_TRANS":     (124, 124, 1, 1),  # Ô trong suốt alpha=0 xóa triệt để mọi viền mỏng 4 cạnh màng cánh!

    # Hàng 1 (y=10..24): Thân giáp, lõi ngực magma, mai lưng, vai, tay kín, quần, giày (giữ nguyên 100% thiết kế cũ!)
    "CHEST_FRONT":     (0,  10,  8, 12),
    "CHEST_CORE":      (8,  10,  4, 10),
    "CHEST_BACK":      (12, 10,  8, 12),
    "CHEST_SIDE":      (20, 10,  4, 12),
    "BACK_SHELL":      (24, 10,  6, 10),
    "PAULDRON_FRONT":  (30, 10,  5,  5),
    "PAULDRON_OUT":    (35, 10,  5,  5),
    "ARM_SLEEVE":      (40, 10,  4, 11),
    "ARM_BAND":        (44, 10,  5,  2),
    "GAUNTLET_CUFF":   (44, 13,  5,  3),
    "LEG_THIGH":       (50, 10,  4,  8),
    "LEG_BELT":        (54, 10,  5,  3),
    "BOOT_BODY":       (50, 19,  5,  5),
    "BOOT_SOLE":       (56, 19,  5,  5),

    # Hàng 2 (y=34..54): 3 TẤM MÀNG CÁNH DƠI ĐỎ THẪM với >= 6 px padding trong suốt hoàn toàn bao quanh mỗi vùng UV!
    # (y=24..33 & y=55..123 & các dải dọc cách 6 px giữa các ô đều để alpha=0 -> 0 đường kẻ đen lỗi UV!)
    "WING_MEM_INNER_R": (4,  34, 10, 16),
    "WING_MEM_MID_R":   (20, 34, 12, 20),
    "WING_MEM_OUTER_R": (38, 34, 12, 18),
    "WING_MEM_INNER_L": (56, 34, 10, 16),
    "WING_MEM_MID_L":   (72, 34, 12, 20),
    "WING_MEM_OUTER_L": (90, 34, 12, 18),

    # Hàng 3 (y=62..74): Rãnh hốc ngực + Đai eo + Quần (trước/sau/bên) + Giày 6 móng vuốt (cách >= 8 px dưới màng cánh!)
    "CHEST_CAVITY":     (4,  62, 10, 12),
    "WAIST_BELT_FRONT": (18, 62, 10,  2),
    "WAIST_BUCKLE":     (18, 66,  4,  4),
    "LEG_FRONT_R":      (30, 62,  4,  8),
    "LEG_FRONT_L":      (36, 62,  4,  8),
    "LEG_BACK_R":       (42, 62,  4,  8),
    "LEG_BACK_L":       (48, 62,  4,  8),
    "LEG_SIDE":         (54, 62,  4,  8),
    "BOOT_FRONT":       (60, 62,  4,  5),
    "BOOT_SIDE_R":      (66, 62,  4,  5),
    "BOOT_SIDE_L":      (72, 62,  4,  5),
    "BOOT_BACK":        (78, 62,  4,  5),
    "BOOT_CLAW_IVORY":  (84, 62,  4,  4),
    "ASH_STONE":        (90, 62,  4,  4),
    "KNEE_TAB":         (96, 62,  3,  3),
    "SCALE_PLATE":      (102, 62, 4,  4),  # Vảy rồng đá núi lửa nổi 3D (viền sáng #68605A -> mặt #504944 -> khe #2A2624)
    "SCALE_RING":       (108, 62, 5,  3),  # Tầng vảy rồng xếp chồng quanh bắp tay/cẳng tay
    "OBSIDIAN_RIM":     (115, 62, 4,  4),  # Đá obsidian xám-tím than tối (#1D1A22/#2C2834/#3F394A) + viền đỏ trầm cực mảnh
    "PAULDRON_HORN_BASE": (18, 74, 4, 4),  # Gốc sừng vai obsidian viền sáng cạnh #5C556A (tách nét trên nền cánh đỏ!)
    "PAULDRON_HORN_MID":  (24, 74, 4, 4),  # Thân sừng vai obsidian ánh cạnh sắc
    "PAULDRON_HORN_TIP":  (30, 74, 4, 4),  # Chóp sừng vai ánh tro/ngà nhẹ nổi khối rõ trên nền cánh đỏ!
}


def verify_uv_and_wing_padding():
    occupied = {}
    for k, (x0, y0, w, h) in REG.items():
        assert x0 >= 0 and y0 >= 0 and x0 + w <= TEX_W and y0 + h <= TEX_H, f"Out of bounds: {k}"
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                assert (x, y) not in occupied, f"UV overlap at ({x},{y}) between {occupied[(x,y)]} and {k}"
                occupied[(x, y)] = k
    # Kiểm chứng cứng >= 2 px (thực tế >= 4 px) đệm trong suốt xung quanh mọi vùng màng cánh!
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
    """
    Tạo đốt cube định hướng chính xác theo vector 3D `p0 -> p1` (trục local +Z khớp 100% vector hướng trong GeckoLib/Bedrock).
    Phần `pad` giúp 2 đốt kế tiếp ăn khớp liền khối 100%, ZERO khe hở lơ lửng!
    """
    dx = float(p1[0] - p0[0])
    dy = float(p1[1] - p0[1])
    dz = float(p1[2] - p0[2])
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    assert length > 1e-5, "Zero-length segment"
    cx = (p0[0] + p1[0]) * 0.5
    cy = (p0[1] + p1[1]) * 0.5
    cz = (p0[2] + p1[2]) * 0.5
    uy = max(-1.0, min(1.0, dy / length))
    # Kiểm chứng ma trận Bedrock (rx=-rad(rot_x), ry=+rad(rot_y), rz=0):
    # Ry @ Rx @ [0,0,1] = [sin(ry)*cos(rot_x), sin(rot_x), cos(ry)*cos(rot_x)] = [dx, dy, dz] / L!
    rot_x = math.degrees(math.asin(uy))
    rot_y = math.degrees(math.atan2(dx, dz))
    return make_centered_cube(
        [cx, cy, cz], [sx, sy, length + pad], mat,
        rot=[rot_x, rot_y, 0.0],
        face_mats=face_mats,
    )


def make_membrane_plane(center, width, height, front_key: str, back_key: str, rot=None) -> dict:
    """
    Tạo màng cánh mỏng 2 mặt KHÔNG BAO GIỜ bị lỗi đường kẻ đen ở 4 cạnh mỏng & KHÔNG bị lật gương giữa mặt trước/sau:
    - Mặt `north` (trước) dùng `front_key`, mặt `south` (sau, UV lật ngang theo chuẩn Bedrock) dùng `back_key` đối gương!
    - Cả 4 cạnh mỏng `up/down/east/west` gán `EMPTY_TRANS` (alpha=0)!
    """
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

    # ==================== 1. MŨ TRÙM ĐẦU RỒNG CINDER DRAGON (`armorHead`, `horn_r`, `horn_l`) ====================
    # Hở hoàn toàn vùng khuôn mặt & mắt người chơi bên dưới hàm trên (`y = 24.0..28.8`) -> 0 che mắt/tầm nhìn, 0 z-fighting!
    head_cubes = [
        # 1a. Hộp sọ rồng đá núi lửa (nóc, gáy, 2 thành bên vát gọn ra sau hàm dưới)
        make_cube(
            [-4.72, 32.15, -4.90], [9.44, 1.05, 9.75], "BASALT_DARK",
            face_mats={"up": "HELM_TOP", "north": "BASALT_PLATE", "south": "BASALT_PLATE", "east": "BASALT_PLATE", "west": "BASALT_PLATE"}
        ),
        # Gờ sọ đỉnh hơi vòm nối từ trán ra sau gáy
        make_centered_cube([0.0, 33.45, +0.20], [6.60, 0.85, 8.20], "BASALT_PLATE"),
        # Thành sau gáy đá núi lửa
        make_cube([-4.72, 24.10, +4.10], [9.44, 8.20, 0.75], "BASALT_DARK", face_mats={"south": "HELM_BACK"}),
        # Hai thành bên sọ (phần trên che thái dương tới z=-4.90; phần dưới lùi ra sau z=-3.25 để lộ sáng mặt & má!)
        make_cube([-4.72, 28.55, -4.90], [0.68, 3.80, 9.75], "BASALT_DARK", face_mats={"west": "HELM_SIDE", "north": "BASALT_PLATE"}),
        make_cube([-4.72, 24.10, -3.25], [0.68, 4.55, 8.10], "BASALT_DARK", face_mats={"west": "HELM_SIDE", "north": "BASALT_DARK"}),
        make_cube([+4.04, 28.55, -4.90], [0.68, 3.80, 9.75], "BASALT_DARK", face_mats={"east": "HELM_SIDE", "north": "BASALT_PLATE"}),
        make_cube([+4.04, 24.10, -3.25], [0.68, 4.55, 8.10], "BASALT_DARK", face_mats={"east": "HELM_SIDE", "north": "BASALT_DARK"}),

        # 1b. MÕM RỒNG / HÀM TRÊN NHÔ VỀ PHÍA TRƯỚC (`z = -4.55 .. -8.90`, `y = 28.85 .. 32.55` — nằm trọn phía trên mắt Steve!)
        make_cube(
            [-3.95, 29.05, -6.95], [7.90, 3.40, 2.55], "BASALT_PLATE",
            face_mats={"up": "DRAGON_SNOUT", "north": "DRAGON_SNOUT", "east": "BASALT_PLATE", "west": "BASALT_PLATE", "down": "BASALT_DARK"}
        ),
        make_cube(
            [-3.35, 28.90, -8.90], [6.70, 2.85, 2.20], "BASALT_PLATE",
            face_mats={"north": "DRAGON_NOSE", "up": "DRAGON_SNOUT", "east": "BASALT_PLATE", "west": "BASALT_PLATE", "down": "BASALT_DARK"}
        ),
        make_centered_cube([0.0, 32.15, -6.85], [2.40, 0.85, 3.60], "BASALT_PLATE", rot=[-8.0, 0.0, 0.0]),

        # 1c. SỪNG MŨI OBSIDIAN LIỀN KHỐI (`nasal_horn`) uốn cong sắc vươn cao trên đầu sống mũi giống hệt Boss Cinder Dragon!
        make_segment_cube((0.0, 31.65, -8.05), (0.0, 33.45, -7.85), 1.50, 1.50, "HORN_BASE", pad=0.55, face_mats=horn_faces),
        make_segment_cube((0.0, 33.45, -7.85), (0.0, 35.00, -7.30), 1.12, 1.12, "HORN_MID",  pad=0.50, face_mats=horn_faces),
        make_segment_cube((0.0, 35.00, -7.30), (0.0, 36.15, -6.45), 0.72, 0.72, "HORN_DARK", pad=0.35, face_mats=horn_faces),

        # 1d. 3 GAI SỐNG ĐỈNH ĐẦU KHỐI 3D ĐẶC CHẮC CHẮN (xóa sạch tấm mặt phẳng mỏng nhiễu pixel!)
        make_centered_cube([0.0, 34.45, -2.15], [1.32, 2.00, 1.95], "HORN_BASE", rot=[-24.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 34.25, +0.95], [1.36, 2.20, 2.05], "HORN_MID",  rot=[-32.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 32.95, +4.00], [1.26, 2.00, 1.90], "HORN_DARK", rot=[-46.0, 0.0, 0.0], face_mats=horn_faces),
    ]

    # 1e. ĐÔI MẮT RỒNG CAM-VÀNG CON NGƯƠI DỌC SẮC + GỜ MÀY ĐÁ VÁT XIÊN + 2 CẶP RĂNG NANH TRẮNG NGÀ ĐẨY GỌN NGOÀI MẮT STEVE!
    for s in (-1.0, 1.0):
        outer_f = "west" if s < 0 else "east"
        # Gờ mày đá vát xiên dữ tợn 2 bên trán mõm (`y = 31.35 .. 32.45`)
        head_cubes.append(make_centered_cube(
            [s * 4.25, 31.85, -5.45], [1.70, 1.05, 3.05], "BASALT_PLATE",
            rot=[+10.0, s * -12.0, s * -18.0]
        ))
        # Hốc mắt & Đôi mắt rồng cam-vàng có con ngươi dọc sắc (phát sáng nhẹ trên glowmask, thấy rõ chính diện, 3/4 & góc bên!)
        head_cubes.append(make_centered_cube(
            [s * 4.18, 30.55, -5.65], [1.20, 1.35, 2.15], "BASALT_DARK",
            rot=[0.0, s * -12.0, 0.0],
            face_mats={"north": "DRAGON_EYE", outer_f: "DRAGON_EYE"}
        ))
        # Mảng gò má vảy đá nâng đỡ dưới hốc mắt rồng
        head_cubes.append(make_centered_cube(
            [s * 4.22, 29.45, -5.55], [1.35, 0.95, 2.50], "BASALT_PLATE",
            rot=[0.0, s * -10.0, 0.0]
        ))
        # Cặp răng nanh trắng ngà #1 (trước — đặt tại góc ngoài mép mõm x = ±3.42 -> 100% NGOÀI mắt Steve cả chính diện & 3/4!)
        head_cubes.append(make_centered_cube(
            [s * 3.42, 28.55, -8.10], [0.74, 1.28, 0.74], "FANG_IVORY",
            rot=[+4.0, 0.0, s * -5.0]
        ))
        head_cubes.append(make_centered_cube(
            [s * 3.39, 27.72, -8.06], [0.46, 0.68, 0.46], "FANG_IVORY",
            rot=[+6.0, 0.0, s * -7.0]
        ))
        # Cặp răng nanh trắng ngà #2 (sau — rủ xuống tại góc hàm ngoài x = ±4.15 ôm gọn 2 bên má, 0 che mắt!)
        head_cubes.append(make_centered_cube(
            [s * 4.15, 28.42, -5.65], [0.76, 1.48, 0.76], "FANG_IVORY",
            rot=[+4.0, 0.0, s * -6.0]
        ))
        head_cubes.append(make_centered_cube(
            [s * 4.12, 27.42, -5.62], [0.48, 0.78, 0.48], "FANG_IVORY",
            rot=[+6.0, 0.0, s * -8.0]
        ))

    # 1f. 2 CẶP SỪNG VƯƠNG MIỆN OBSIDIAN DÀY LIỀN MẠCH VƯƠN NGƯỢC LÊN TRÊN & RA SAU (dùng make_segment_cube -> 0 khe hở, 0 kim trắng!)
    def build_boss_crown_horns(s: float):
        hc = []
        # --- Cặp sừng chính lớn phía trên (4 đốt obsidian liền khối vươn lên cao & cong mượt ra sau giống hệt sừng Boss!) ---
        u_pts = [
            (s * 4.25, 31.85, -0.85),
            (s * 5.45, 33.70, +1.05),
            (s * 6.50, 35.85, +3.15),
            (s * 7.25, 38.05, +5.25),
            (s * 7.55, 39.95, +6.95),
        ]
        u_sizes = [(2.25, 2.25, "HORN_BASE"), (1.88, 1.88, "HORN_BASE"), (1.46, 1.46, "HORN_MID"), (0.98, 0.98, "HORN_DARK")]
        for idx, (w, h, mat) in enumerate(u_sizes):
            hc.append(make_segment_cube(u_pts[idx], u_pts[idx + 1], w, h, mat, pad=0.72, face_mats=horn_faces))

        # --- Cặp sừng phụ ngắn hơn phía dưới / sau má (3 đốt obsidian liền khối chĩa chếch ra ngoài & ra sau!) ---
        l_pts = [
            (s * 4.30, 28.85, +0.45),
            (s * 5.60, 29.75, +2.25),
            (s * 6.65, 30.80, +4.05),
            (s * 7.35, 31.95, +5.55),
        ]
        l_sizes = [(1.68, 1.62, "HORN_BASE"), (1.30, 1.26, "HORN_MID"), (0.86, 0.84, "HORN_DARK")]
        for idx, (w, h, mat) in enumerate(l_sizes):
            hc.append(make_segment_cube(l_pts[idx], l_pts[idx + 1], w, h, mat, pad=0.62, face_mats=horn_faces))
        return hc

    horn_r_cubes = build_boss_crown_horns(-1.0)
    horn_l_cubes = build_boss_crown_horns(+1.0)

    # ==================== 2. THÂN GIÁP, LỒNG MÓNG VUỐT KẸP 3 VIÊN NGỌC MAGMA & GAI LƯNG (`armorBody`) ====================
    claw_tip_faces = {
        "north": "CLAW_CRIMSON", "south": "CRIMSON_TRIM",
        "up": "CLAW_CRIMSON", "down": "CRIMSON_TRIM",
        "east": "CLAW_CRIMSON", "west": "CLAW_CRIMSON",
    }
    body_cubes = [
        # Hộp thân giáp chính đá núi lửa than nâu-đen (`8.8 x 12.2 x 4.9`) — rãnh hốc dung nham phát sáng ở mặt trước (`CHEST_CAVITY`)!
        make_centered_cube(
            [0.0, 18.0, 0.0], [8.8, 12.2, 4.9], "BASALT_PLATE",
            face_mats={
                "north": "CHEST_CAVITY",
                "south": "CHEST_BACK",
                "east":  "CHEST_SIDE",
                "west":  "CHEST_SIDE",
                "up":    "BASALT_DARK",
                "down":  "BASALT_DARK",
            }
        ),
        # Đai eo obsidian tối dày + ô khóa đai xám tro điểm lõi cam cháy + gờ viền mảnh đỏ núi lửa phía dưới (chừa gọn gốc đuôi sau!)
        make_centered_cube(
            [0.0, 12.38, 0.0], [9.26, 1.20, 5.28], "HORN_DARK",
            face_mats={
                "north": "WAIST_BELT_FRONT",
                "south": "HORN_DARK",
                "east":  "HORN_DARK",
                "west":  "HORN_DARK",
                "up":    "BASALT_DARK",
                "down":  "HORN_DARK",
            }
        ),
        make_centered_cube(
            [0.0, 12.44, -2.72], [1.90, 1.02, 0.30], "ASH_STONE",
            face_mats={"north": "WAIST_BUCKLE"}
        ),
        # Gờ viền obsidian có ánh đỏ núi lửa cực mảnh chạy vòng dưới đai eo (chừa thoáng dưới gốc đuôi rồng!)
        make_centered_cube([-2.95, 11.62, -2.68], [3.40, 0.36, 0.40], "OBSIDIAN_RIM"),
        make_centered_cube([+2.95, 11.62, -2.68], [3.40, 0.36, 0.40], "OBSIDIAN_RIM"),
        make_centered_cube([-4.66, 11.62,  0.00], [0.36, 0.36, 5.42], "OBSIDIAN_RIM"),
        make_centered_cube([+4.66, 11.62,  0.00], [0.36, 0.36, 5.42], "OBSIDIAN_RIM"),
        make_centered_cube([-3.10, 11.62, +2.68], [3.10, 0.36, 0.40], "OBSIDIAN_RIM"),
        make_centered_cube([+3.10, 11.62, +2.68], [3.10, 0.36, 0.40], "OBSIDIAN_RIM"),
        # Mai lưng phân đốt chạy dọc cột sống — GIỮ NGUYÊN 100%!
        make_centered_cube(
            [0.0, 17.8, +2.85], [5.40, 9.60, 1.25], "BACK_SHELL",
            face_mats={"south": "BACK_SHELL", "up": "TAIL_SEG", "down": "TAIL_SEG"}
        ),
        # 3 gai sống lưng khối 3D đặc — GIỮ NGUYÊN 100%!
        make_centered_cube([0.0, 21.8, +4.05], [1.15, 1.95, 1.75], "HORN_BASE", rot=[-32.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 18.8, +4.15], [1.15, 1.95, 1.75], "HORN_MID",  rot=[-36.0, 0.0, 0.0], face_mats=horn_faces),
        make_centered_cube([0.0, 15.8, +4.05], [1.10, 1.80, 1.65], "HORN_DARK", rot=[-40.0, 0.0, 0.0], face_mats=horn_faces),

        # --- 2a. KHUNG RÃNH NGỰC NỔI 3D & VIỀN ĐÁY HỐC CHỨA NGỌC (viền obsidian tinh tế khớp màu boss!) ---
        make_centered_cube([0.0, 13.38, -2.62], [8.60, 0.56, 0.36], "OBSIDIAN_RIM"),

        # --- 2b. CỤM 3 VIÊN NGỌC TINH THỂ HỎA LONG XOAY GÓC NỔI 3D BÊN TRONG LỒNG MÓNG (GIỮ NGUYÊN 100% HÌNH KHỐI!) ---
        make_centered_cube([+0.08, 15.95, -2.84], [1.54, 1.54, 0.88], "CHEST_GEM", rot=[+7.0, -5.0, -28.0]),
        make_centered_cube([-0.68, 17.88, -2.78], [0.92, 0.92, 0.72], "CHEST_GEM", rot=[+4.0, +6.0, +38.0]),
        make_centered_cube([+0.68, 18.02, -2.78], [0.90, 0.90, 0.72], "CHEST_GEM", rot=[-3.0, -5.0, +4.0]),
    ]

    # Hai mảng giáp sườn đá núi lửa + 3 CẶP MÓNG KẸP NGỌC (GIỮ NGUYÊN HÌNH KHỐI!) + CÁC HÀNG VẢY RỒNG 3D NỔI CHỒNG TẦNG!
    for s in (-1.0, 1.0):
        # Mảng giáp sườn ôm dọc rãnh ngực (đá vảy rồng núi lửa xám-nâu khớp boss!)
        body_cubes.append(make_centered_cube([s * 2.95, 21.85, -2.60], [2.90, 4.10, 0.30], "BASALT_PLATE"))
        body_cubes.append(make_centered_cube([s * 1.40, 21.75, -2.63], [0.46, 4.15, 0.36], "OBSIDIAN_RIM"))
        body_cubes.append(make_centered_cube([s * 2.78, 19.52, -2.65], [3.24, 0.56, 0.38], "OBSIDIAN_RIM"))
        body_cubes.append(make_centered_cube([s * 3.55, 17.95, -2.60], [1.70, 2.65, 0.30], "BASALT_PLATE"))
        body_cubes.append(make_centered_cube([s * 3.35, 16.58, -2.65], [2.10, 0.54, 0.38], "OBSIDIAN_RIM"))
        body_cubes.append(make_centered_cube([s * 3.70, 14.95, -2.60], [1.40, 2.75, 0.30], "BASALT_PLATE"))

        # --- 3 TẦNG MÓNG VUỐT 3D KẸP NGỌC (GIỮ NGUYÊN 100% TỌA ĐỘ & KÍCH THƯỚC — đổi chất liệu obsidian tối khớp boss!) ---
        claw_tiers = [
            ((s * 2.25, 20.85, -2.44), (s * 1.82, 20.72, -3.14), (s * 0.65, 20.50, -3.50), 0.94, 0.84),
            ((s * 2.86, 18.25, -2.44), (s * 2.38, 18.20, -3.20), (s * 1.38, 18.12, -3.55), 0.96, 0.86),
            ((s * 3.16, 15.10, -2.44), (s * 2.62, 15.18, -3.18), (s * 1.32, 15.36, -3.54), 0.98, 0.88),
        ]
        for p_root, p_knuckle, p_tip, w_root, w_tip in claw_tiers:
            body_cubes.append(make_segment_cube(p_root, p_knuckle, w_root, w_root, "HORN_BASE", pad=0.24, face_mats=horn_faces))
            body_cubes.append(make_segment_cube(p_knuckle, p_tip, w_tip, w_tip, "CLAW_CRIMSON", pad=0.22, face_mats=claw_tip_faces))

        # --- VẢY RỒNG 3D NỔI CHỒNG TẦNG SO LE TRÊN NGỰC, SƯỜN, ĐAI EO & LƯNG (nhô 0.40–0.65 px, nghiêng vát xuống, 0 z-fighting!) ---
        # Mặt trước ngực (ôm 2 bên hốc ngọc, cách sạch cụm 6 móng & 3 ngọc!)
        body_cubes.append(make_centered_cube([s * 2.30, 22.90, -2.84], [1.32, 1.32, 0.44], "SCALE_PLATE", rot=[-11.0, s * -6.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.62, 22.60, -2.86], [1.32, 1.36, 0.46], "SCALE_PLATE", rot=[-11.0, s * -10.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.60, 20.65, -2.84], [1.28, 1.36, 0.44], "SCALE_PLATE", rot=[-11.0, s * -8.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.74, 18.00, -2.84], [1.14, 1.38, 0.42], "SCALE_PLATE", rot=[-11.0, s * -8.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.82, 15.05, -2.84], [1.06, 1.34, 0.42], "SCALE_PLATE", rot=[-11.0, s * -8.0, 0.0]))
        # Vảy rồng nổi trên mặt trước & bên hông đai eo (2 hàng vảy đá xếp tầng cạnh khóa đai)
        body_cubes.append(make_centered_cube([s * 2.10, 12.35, -2.74], [1.55, 1.02, 0.36], "SCALE_PLATE", rot=[-9.0, 0.0, 0.0]))
        body_cubes.append(make_centered_cube([s * 3.75, 12.30, -2.72], [1.35, 1.02, 0.36], "SCALE_PLATE", rot=[-9.0, s * -10.0, 0.0]))
        # Vảy rồng nổi thấp trên 2 bên sườn ngực (nhô gọn 0.36 px, không xuyên tay Dual Knife!)
        for sy in (20.40, 17.80, 15.20):
            body_cubes.append(make_centered_cube([s * 4.52, sy, 0.0], [0.36, 1.65, 2.55], "SCALE_PLATE", rot=[0.0, 0.0, s * -9.0]))
        # Vảy rồng nổi chồng tầng sau lưng (3 tầng vảy đá lớn ôm 2 bên mai lưng & gai sống lưng!)
        for sy, sw in ((21.60, 1.95), (18.70, 2.05), (15.80, 1.85)):
            body_cubes.append(make_centered_cube([s * 3.25, sy, +2.68], [sw, 1.92, 0.50], "SCALE_PLATE", rot=[+12.0, s * +6.0, 0.0]))

    # ==================== 2b. ĐÔI CÁNH RỒNG HOÀN CHỈNH 3 KHỚP (`wing_*_upper -> mid -> tip`, SẢI ~42.5 px = 2.5x VAI!) ====================
    # Toàn bộ 3 khớp cánh + 4 nan xương + 3 tấm màng đỏ thẫm nằm trên CÙNG 1 mặt phẳng vát sweep 20° (`pt(t, y)`)
    # -> Màng cánh kín 100% sát xương sống tới tận mũi cánh, ZERO khe hở giữa 3 tấm, ZERO nan dư, ZERO đường kẻ đen lỗi UV!
    WING_YAW_DEG = 20.0
    WING_COS = math.cos(math.radians(WING_YAW_DEG))
    WING_SIN = math.sin(math.radians(WING_YAW_DEG))

    def wing_pt(s: float, t: float, y: float):
        return (s * (2.0 + t * WING_COS), y, 2.60 + t * WING_SIN)

    def build_boss_dragon_wing(s: float):
        # Lưu ý quy ước gương mặt trước (`north`) vs mặt sau (`south`) của Bedrock UV:
        # - Cánh phải (`s=-1`): north = *_R, south = *_L
        # - Cánh trái (`s=+1`): north = *_L, south = *_R
        f_tag = "R" if s < 0 else "L"
        b_tag = "L" if s < 0 else "R"
        p_sh = wing_pt(s, 0.0,  21.4)
        p_el = wing_pt(s, 6.2,  25.2)
        p_wr = wing_pt(s, 13.0, 27.8)
        p_tp = wing_pt(s, 20.6, 25.2)

        f1_tip = wing_pt(s, 5.8,  14.6)
        f2_tip = wing_pt(s, 9.6,  14.4)
        f3_tip = wing_pt(s, 13.4, 15.0)
        f4_tip = wing_pt(s, 17.4, 17.6)

        # --- KHỚP 1: Cánh trên (`wing_*_upper`) ---
        upper = [
            # Xương sống cánh trên nối vai -> khuỷu
            make_segment_cube(p_sh, p_el, 1.32, 1.32, "WING_BONE", pad=0.65, face_mats=horn_faces),
            # 2 gai sống cánh sắc trên xương trên
            make_centered_cube(wing_pt(s, 2.6, 23.65), [0.75, 1.25, 0.75], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * -12.0], face_mats=horn_faces),
            make_centered_cube(wing_pt(s, 5.0, 25.20), [0.75, 1.28, 0.75], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * -14.0], face_mats=horn_faces),
            # Nan xương #1 kéo trọn từ khuỷu xuống đúng đỉnh cung màng trong (`f1_tip`)
            make_segment_cube(p_el, f1_tip, 0.58, 0.58, "WING_BONE", pad=0.25, face_mats=horn_faces),
            # Màng cánh trong đỏ thẫm cắt cung dơi toán học chính xác (`t in [0.0, 6.3]`, `y in [14.4, 25.4]`)
            make_membrane_plane(
                wing_pt(s, 3.15, 19.90), 6.30, 11.00,
                f"WING_MEM_INNER_{f_tag}", f"WING_MEM_INNER_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]

        # --- KHỚP 2: Cánh giữa (`wing_*_mid`) + MÓNG VUỐT CỔ CÁNH + NAN #2 & #3 ---
        thumb_mid = (p_wr[0] - s * 0.25, p_wr[1] + 1.85, p_wr[2] - 0.55)
        thumb_tip = (p_wr[0] - s * 0.95, p_wr[1] + 2.75, p_wr[2] - 1.65)
        mid = [
            # Xương cẳng cánh (forearm) nối khuỷu -> cổ cánh
            make_segment_cube(p_el, p_wr, 1.24, 1.24, "WING_BONE", pad=0.65, face_mats=horn_faces),
            # Gai nhỏ sống cánh giữa
            make_centered_cube(wing_pt(s, 9.6, 27.30), [0.72, 1.22, 0.72], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * -10.0], face_mats=horn_faces),
            # MÓNG VUỐT CỔ CÁNH CONG OBSIDIAN LIỀN KHỐI (`thumb claw` — khớp 100% ảnh 1 Boss Cinder Dragon!)
            make_segment_cube(p_wr, thumb_mid, 0.92, 0.92, "HORN_BASE", pad=0.50, face_mats=horn_faces),
            make_segment_cube(thumb_mid, thumb_tip, 0.64, 0.64, "HORN_DARK", pad=0.35, face_mats=horn_faces),
            # Nan xương #2 & #3 tỏa dài từ cổ cánh kéo sát đúng đỉnh cung màng dưới (`f2_tip`, `f3_tip`)
            make_segment_cube(p_wr, f2_tip, 0.54, 0.54, "WING_BONE", pad=0.20, face_mats=horn_faces),
            make_segment_cube(p_wr, f3_tip, 0.52, 0.52, "WING_BONE", pad=0.20, face_mats=horn_faces),
            # Màng cánh giữa đỏ thẫm căng kín 100% giữa khuỷu -> cổ cánh -> nan #1/#2/#3 (`t in [5.7, 13.5]`, `y in [14.2, 28.0]`)
            make_membrane_plane(
                wing_pt(s, 9.60, 21.10), 7.80, 13.80,
                f"WING_MEM_MID_{f_tag}", f"WING_MEM_MID_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]

        # --- KHỚP 3: Mũi cánh ngoài (`wing_*_tip`) + NAN #4 + MÀNG CÁNH KÍN TỚI TẬN MŨI NHỌN ---
        p_tip_end = wing_pt(s, 21.8, 24.4)
        tip = [
            # Xương sống mép trước mũi cánh chúc nhẹ từ cổ cánh tới mũi cánh
            make_segment_cube(p_wr, p_tp, 1.05, 1.05, "WING_BONE", pad=0.55, face_mats=horn_faces),
            # Mũi cánh thuôn nhọn vươn hết sải (`x = ±22.4`)
            make_segment_cube(p_tp, p_tip_end, 0.68, 0.68, "HORN_DARK", pad=0.30, face_mats=horn_faces),
            # Gai nhỏ sống cánh ngoài
            make_centered_cube(wing_pt(s, 16.8, 27.25), [0.64, 1.05, 0.64], "HORN_MID", rot=[+6.0, s * -WING_YAW_DEG, s * +10.0], face_mats=horn_faces),
            # Nan xương #4 tỏa từ cổ cánh kéo sát đúng đỉnh cung ngoài (`f4_tip`)
            make_segment_cube(p_wr, f4_tip, 0.48, 0.48, "WING_BONE", pad=0.18, face_mats=horn_faces),
            # Màng cánh ngoài đỏ thẫm căng kín 100% ngay dưới xương mũi cánh -> nan #3/#4 -> mũi nhọn (`t in [13.0, 20.8]`, `y in [14.8, 28.0]`)
            make_membrane_plane(
                wing_pt(s, 16.90, 21.40), 7.80, 13.20,
                f"WING_MEM_OUTER_{f_tag}", f"WING_MEM_OUTER_{b_tag}",
                rot=[0.0, s * -WING_YAW_DEG, 0.0],
            ),
        ]
        return upper, mid, tip

    wing_r_upper, wing_r_mid, wing_r_tip = build_boss_dragon_wing(-1.0)
    wing_l_upper, wing_l_mid, wing_l_tip = build_boss_dragon_wing(+1.0)

    # ==================== 2c. ĐUÔI DÀI 4 ĐỐT CONG CHỮ S + CHÙY ĐUÔI LƯỠI LIỀM ĐÔI (KHỚP LỒNG CHỒNG SÂU 0.75–0.85 px -> 0 HỞ KHỚP!) ====================
    # Quy tắc khớp cầu không hở đốt & không z-fighting:
    # - Pivot của `tail_i` đặt CHÍNH XÁC tại tâm mặt tiếp xúc với `tail_{i-1}` phía trước.
    # - Mỗi đốt `tail_i` kéo dài tiến về trước vượt qua pivot 0.75–0.85 px (lọt sâu vào lòng đốt trước vì tiết diện nhỏ hơn >= 0.40 px -> 0 z-fighting, 0 khe hở khi uốn tối đa!).
    tail_1_cubes = [
        # Pivot [0, 12.60, +2.60]: lồng sâu vào mặt sau `armorBody` từ z=+1.85 -> +6.45 (dài 4.60 px)
        make_centered_cube([0.0, 12.10, +4.15], [3.40, 3.00, 4.60], "TAIL_SEG", rot=[-16.0, 0.0, 0.0]),
        make_centered_cube([0.0, 14.00, +4.40], [1.05, 1.55, 1.55], "HORN_BASE", rot=[-32.0, 0.0, 0.0], face_mats=horn_faces),
    ]
    tail_2_cubes = [
        # Pivot [0, 11.45, +5.90]: lồng sâu 0.80 px vào lòng `tail_1` (z=+5.10 -> +9.70, dài 4.60 px; nhỏ hơn tail_1 0.55 px -> 0 z-fighting!)
        make_centered_cube([0.0, 11.00, +7.40], [2.85, 2.50, 4.60], "TAIL_SEG", rot=[-8.0, 0.0, 0.0]),
        make_centered_cube([0.0, 12.60, +7.85], [0.95, 1.45, 1.45], "HORN_MID",  rot=[-24.0, 0.0, 0.0], face_mats=horn_faces),
    ]
    tail_3_cubes = [
        # Pivot [0, 10.70, +9.25]: lồng sâu 0.80 px vào lòng `tail_2` (z=+8.45 -> +13.05, dài 4.60 px; nhỏ hơn tail_2 0.50 px -> 0 z-fighting!)
        make_centered_cube([0.0, 10.68, +10.75], [2.35, 2.10, 4.60], "TAIL_SEG", rot=[+10.0, 0.0, 0.0]),
        make_centered_cube([0.0, 12.10, +11.10], [0.88, 1.35, 1.35], "HORN_MID", rot=[-8.0, 0.0, 0.0], face_mats=horn_faces),
    ]
    tail_4_cubes = [
        # Pivot [0, 10.95, +12.55]: lồng sâu 0.80 px vào lòng `tail_3` (z=+11.75 -> +16.15, dài 4.40 px; nhỏ hơn tail_3 0.40 px -> 0 z-fighting!)
        make_centered_cube([0.0, 11.25, +13.95], [1.95, 1.75, 4.40], "TAIL_SEG", rot=[+18.0, 0.0, 0.0]),
        make_centered_cube([0.0, 12.50, +14.20], [0.80, 1.25, 1.25], "HORN_DARK", rot=[+4.0, 0.0, 0.0], face_mats=horn_faces),
    ]

    # CHÙY ĐUÔI LƯỠI LIỀM ĐÔI ÔM LÕI THAN HỒNG + GAI TRỤC GIỮA (`tail_tip`, pivot `[0.0, 11.70, +15.60]` — lồng chốt khớp sâu 0.75 px vào `tail_4`!)
    tail_tip_cubes = [
        # Chốt khớp cổ chùy lồng sâu 0.75 px vào lòng `tail_4` (nhỏ hơn tail_4 0.15 px -> 0 z-fighting, 0 khe hở khi vẫy!)
        make_centered_cube([0.0, 11.55, +15.55], [1.80, 1.60, 1.55], "TAIL_SEG", rot=[+18.0, 0.0, 0.0]),
        # Khung bệ đá núi lửa nâng đỡ gốc chùy & 2 bên lõi tinh thể
        make_centered_cube([0.0, 11.75, +16.05], [2.55, 1.65, 1.75], "BASALT_PLATE", rot=[+18.0, 0.0, 0.0]),
        # LÕI TINH THỂ THAN HỒNG CAM-VÀNG PHÁT SÁNG (2 khối tinh thể vát góc cạnh lồng 45° rực sáng trên glowmask!)
        make_centered_cube([0.0, 12.25, +16.95], [2.25, 1.90, 2.25], "TAIL_CRYSTAL", rot=[+18.0, +45.0, 0.0]),
        make_centered_cube([0.0, 12.35, +17.05], [1.75, 2.10, 1.75], "TAIL_CRYSTAL", rot=[+20.0, 0.0, +45.0]),
        # GAI TRỤC OBSIDIAN THẲNG NỔI BẬT NGAY GIỮA 2 LƯỠI LIỀM (mặt trên HORN_MID sáng rõ như ảnh 4!)
        make_segment_cube((0.0, 12.35, +17.40), (0.0, 13.15, +19.85), 1.20, 1.08, "HORN_BASE", pad=0.45, face_mats=horn_faces),
        make_segment_cube((0.0, 13.15, +19.85), (0.0, 13.70, +21.65), 0.78, 0.72, "HORN_MID",  pad=0.35, face_mats=horn_faces),
    ]
    # 2 LƯỠI LIỀM OBSIDIAN ĐEN-TÍM TỐI LIỀN KHỐI (5 đốt cong mượt mỗi bên bọc gọn lấy lõi than & gai trục!)
    for s in (-1.0, 1.0):
        p_arc = [
            (s * 1.15, 11.85, +16.35),
            (s * 2.85, 12.20, +17.45),
            (s * 3.55, 12.75, +19.10),
            (s * 3.15, 13.35, +20.80),
            (s * 2.10, 13.80, +21.95),
            (s * 0.95, 14.08, +22.35),
        ]
        p_sizes = [
            (1.52, 1.32, "HORN_BASE"),
            (1.42, 1.26, "HORN_BASE"),
            (1.26, 1.14, "HORN_MID"),
            (1.04, 0.96, "HORN_MID"),
            (0.76, 0.72, "HORN_DARK"),
        ]
        for idx, (w, h, mat) in enumerate(p_sizes):
            tail_tip_cubes.append(make_segment_cube(p_arc[idx], p_arc[idx + 1], w, h, mat, pad=0.58, face_mats=horn_faces))

    # ==================== 3. VAI, CÁNH TAY BỌC KÍN VẢY RỒNG 3D XẾP CHỒNG (`armorRightArm`, `armorLeftArm`) ====================
    def build_dragon_arm(s: float):
        cx = s * 6.0
        outer_face = "west" if s < 0 else "east"
        return [
            # Lớp lót tay đá tro tối bọc kín 100% bắp tay tới cổ tay (0 hở da!)
            make_centered_cube(
                [cx, 18.925, 0.0], [4.88, 10.75, 4.82], "ARM_SLEEVE",
                face_mats={"up": "ARM_CAP", "down": "ARM_CAP"}
            ),
            # Vai vảy rồng tầng 1 (khối chính vát gọn đồng bộ đá núi lửa boss, bỏ sọc đỏ dày!)
            make_centered_cube(
                [cx + s * 0.25, 22.30, 0.0], [5.48, 3.30, 5.48], "PAULDRON_OUT",
                rot=[0.0, 0.0, s * -6.0],
                face_mats={"north": "PAULDRON_FRONT", "south": "PAULDRON_OUT", outer_face: "PAULDRON_OUT", "up": "SCALE_PLATE", "down": "OBSIDIAN_RIM"}
            ),
            # Mào vảy đá đỉnh vai vát nghiêng + viền obsidian mảnh
            make_centered_cube(
                [cx + s * 1.15, 23.85, 0.0], [4.35, 1.22, 5.18], "SCALE_PLATE",
                rot=[0.0, 0.0, s * -18.0],
                face_mats={"up": "SCALE_PLATE", "north": "SCALE_PLATE", "south": "SCALE_PLATE", outer_face: "OBSIDIAN_RIM"}
            ),
            # Hàng vảy rồng 3D nổi chồng lớp dưới vai (3 tấm vảy xòe nhẹ ~0.50 px ra ngoài + trước/sau!)
            make_centered_cube([cx + s * 2.86, 20.65, -1.25], [0.54, 1.55, 1.85], "SCALE_PLATE", rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx + s * 2.86, 20.65, +1.25], [0.54, 1.55, 1.85], "SCALE_PLATE", rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx + s * 0.15, 20.60, -2.64], [3.85, 1.42, 0.46], "SCALE_PLATE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 0.15, 20.60, +2.64], [3.85, 1.42, 0.46], "SCALE_PLATE", rot=[+11.0, 0.0, 0.0]),

            # Tầng vảy rồng xếp chồng bắp tay (thay vòng sọc đỏ phẳng cũ!)
            make_centered_cube([cx + s * 0.08, 19.15, 0.0], [5.26, 1.38, 5.24], "SCALE_RING", rot=[0.0, 0.0, s * -5.0], face_mats={"up": "SCALE_PLATE", "down": "OBSIDIAN_RIM"}),
            make_centered_cube([cx + s * 2.78, 18.85,  0.0], [0.52, 1.55, 2.20], "SCALE_PLATE", rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx,            18.80, -2.62], [3.40, 1.32, 0.44], "SCALE_PLATE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx,            18.80, +2.62], [3.40, 1.32, 0.44], "SCALE_PLATE", rot=[+11.0, 0.0, 0.0]),

            # Tầng vảy rồng xếp chồng cẳng tay (so le với tầng bắp tay!)
            make_centered_cube([cx + s * 0.08, 17.25, 0.0], [5.24, 1.36, 5.22], "SCALE_RING", rot=[0.0, 0.0, s * -5.0], face_mats={"up": "SCALE_PLATE", "down": "OBSIDIAN_RIM"}),
            make_centered_cube([cx + s * 2.76, 17.05, -1.10], [0.50, 1.48, 1.75], "SCALE_PLATE", rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx + s * 2.76, 17.05, +1.10], [0.50, 1.48, 1.75], "SCALE_PLATE", rot=[0.0, 0.0, s * -14.0]),

            # Ống giáp cổ tay vảy rồng đá + 2 gờ vảy sống obsidian nhô gọn dọc mặt ngoài cẳng tay (0 xuyên khi cầm Dual Knife!)
            make_centered_cube([cx, 14.95, 0.0], [5.40, 2.68, 5.40], "GAUNTLET_CUFF", face_mats={"up": "OBSIDIAN_RIM", "down": "BASALT_DARK"}),
            make_centered_cube([cx + s * 2.95, 16.25, +0.25], [0.72, 1.25, 1.10], "HORN_BASE", rot=[0.0, 0.0, s * +16.0], face_mats=horn_faces),
            make_centered_cube([cx + s * 2.92, 14.70, +0.25], [0.68, 1.12, 1.02], "HORN_MID",  rot=[0.0, 0.0, s * +18.0], face_mats=horn_faces),
        ]

    rarm_cubes = build_dragon_arm(-1.0)
    larm_cubes = build_dragon_arm(+1.0)

    # ==================== 3a. SỪNG VAI RỒNG (`pauldron_horn_r/l`) & GĂNG MÓNG VUỐT BÀN TAY / CỰA KHUỶU (`claw_gauntlet_r/l`) ====================
    phorn_faces_base = {"up": "PAULDRON_HORN_MID", "down": "HORN_DARK", "north": "PAULDRON_HORN_BASE", "south": "HORN_DARK", "east": "PAULDRON_HORN_BASE", "west": "PAULDRON_HORN_BASE"}
    phorn_faces_mid  = {"up": "PAULDRON_HORN_MID", "down": "PAULDRON_HORN_BASE", "north": "PAULDRON_HORN_MID", "south": "PAULDRON_HORN_BASE", "east": "PAULDRON_HORN_MID", "west": "PAULDRON_HORN_MID"}
    phorn_faces_tip  = {"up": "PAULDRON_HORN_TIP", "down": "PAULDRON_HORN_MID", "north": "PAULDRON_HORN_TIP", "south": "PAULDRON_HORN_MID", "east": "PAULDRON_HORN_TIP", "west": "PAULDRON_HORN_TIP"}

    def build_pauldron_horn(s: float):
        """
        Sừng rồng 2 tầng gắn mép trên-ngoài giáp vai (`|x| = 8.15 -> 11.85`, `y = 23.65 -> 29.15`, `z = -0.15 -> +1.65`):
        - Cách mũ & cặp sừng mũ >= 1.5–4.0 px, cách khung cánh sau lưng (`z >= +4.72`) >= 3.0 px -> TUYỆT ĐỐI 0 xuyên mũ/cánh/Dual Knife!
        - Viền cạnh sáng #5C556A + chóp ánh tro/ngà nhẹ tách nét nổi bật trên nền màng cánh đỏ thẫm!
        """
        hc = []
        # Sừng chính 4 đốt obsidian thuôn nhọn dần (gốc dày 2.15 px -> mũi nhọn 0.72 px) xòe ra ngoài rồi uốn vươn lên & hơi ngả sau
        m_pts = [
            (s * 8.10,  23.60, -0.15),
            (s * 9.75,  24.45, +0.25),
            (s * 11.05, 25.85, +0.75),
            (s * 11.75, 27.55, +1.25),
            (s * 11.85, 29.15, +1.65),
        ]
        m_spec = [
            (2.15, 2.10, "PAULDRON_HORN_BASE", phorn_faces_base),
            (1.68, 1.65, "PAULDRON_HORN_MID",  phorn_faces_mid),
            (1.20, 1.18, "PAULDRON_HORN_MID",  phorn_faces_mid),
            (0.72, 0.72, "PAULDRON_HORN_TIP",  phorn_faces_tip),
        ]
        for idx, (w, h, mat, fm) in enumerate(m_spec):
            hc.append(make_segment_cube(m_pts[idx], m_pts[idx + 1], w, h, mat, pad=0.62, face_mats=fm))

        # Gai sừng phụ ngắn hơn (~50% sừng chính) mọc chếch phía dưới/sau gốc sừng chính đồng bộ sừng 2 tầng trên đầu boss!
        s_pts = [
            (s * 8.15,  22.25, +0.90),
            (s * 9.65,  22.80, +1.40),
            (s * 10.75, 23.70, +1.90),
        ]
        hc.append(make_segment_cube(s_pts[0], s_pts[1], 1.32, 1.26, "PAULDRON_HORN_BASE", pad=0.50, face_mats=phorn_faces_base))
        hc.append(make_segment_cube(s_pts[1], s_pts[2], 0.78, 0.76, "PAULDRON_HORN_TIP",  pad=0.35, face_mats=phorn_faces_tip))
        return hc

    def build_claw_gauntlet(s: float):
        """
        Găng móng vuốt rồng bọc mu bàn tay + 4 móng vuốt trắng ngà sắc nhọn tách khe + 2 cựa khuỷu tay obsidian:
        - Chừa trống 100% lòng bàn tay (`x = cx - s*2.0 .. cx - s*0.3`, `y = 12.0..13.5`) -> cầm Dual Knife (thuận & ngược) / vũ khí 0 xuyên cán!
        - Mũi móng chỉ vượt đáy tay ~0.95 px (`y = 11.05`) & nhô trước ~0.55 px -> First-Person sạch gọn 0 che tâm ngắm!
        """
        cx = s * 6.0
        gc = [
            # 1. Tấm giáp vảy rồng bọc mu bàn tay ngoài & viền khớp ngón (nối mượt từ cổ tay xuống y = 12.45, 0 nuốt lòng bàn tay!)
            make_centered_cube([cx + s * 2.38, 13.45,  0.00], [0.54, 1.95, 4.46], "SCALE_PLATE", rot=[0.0, 0.0, s * -6.0]),
            make_centered_cube([cx + s * 0.48, 13.20, -2.28], [3.55, 1.38, 0.44], "SCALE_PLATE", rot=[-8.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 0.56, 13.25, +2.24], [3.40, 1.38, 0.42], "SCALE_PLATE", rot=[+8.0, 0.0, 0.0]),
            # Gờ obsidian khớp ngón tay (khóa nền cho cụm móng vuốt trắng ngà)
            make_centered_cube([cx + s * 2.48, 12.75,  0.00], [0.46, 0.52, 4.30], "OBSIDIAN_RIM"),
            make_centered_cube([cx + s * 0.52, 12.68, -2.36], [3.40, 0.48, 0.40], "OBSIDIAN_RIM"),
        ]

        # 2. 4 MÓNG VUỐT RỒNG 3D TRẮNG NGÀ PHONG HÓA TÁCH KHE RÕ (gốc khớp obsidian + mũi vuốt vót nhọn cong quặp ~1.75 px!)
        # Bố trí ôm mép trước-ngoài & dọc cạnh ngoài mu bàn tay -> sắc nét từ cả Chính diện, Góc bên & 3/4, lòng bàn tay trống 100%!
        claw_specs = [
            # (gốc gắn khớp, đỉnh cong ngoài, mũi vuốt cong quặp vào-xuống)
            ((cx + s * 0.25, 12.62, -2.26), (cx + s * 0.25, 11.92, -2.56), (cx + s * 0.10, 11.05, -2.18)),  # Móng trước-trong
            ((cx + s * 1.48, 12.62, -2.22), (cx + s * 1.68, 11.92, -2.50), (cx + s * 1.44, 11.02, -2.08)),  # Móng trước-ngoài
            ((cx + s * 2.42, 12.68, -0.78), (cx + s * 2.66, 11.94, -0.96), (cx + s * 2.28, 11.06, -0.78)),  # Móng cạnh ngoài giữa
            ((cx + s * 2.42, 12.68, +0.92), (cx + s * 2.66, 11.94, +0.76), (cx + s * 2.28, 11.12, +0.86)),  # Móng cạnh ngoài sau
        ]
        for p0, p1, p2 in claw_specs:
            # Gốc khớp nối obsidian
            gc.append(make_segment_cube(p0, p1, 0.56, 0.56, "PAULDRON_HORN_BASE", pad=0.22, face_mats=phorn_faces_base))
            # Mũi vuốt trắng ngà phong hóa (`#9C958B/#CAC3B8/#E6E0D4`) vót nhọn cong quặp ôm theo đốt ngón tay!
            gc.append(make_segment_cube(p1, p2, 0.44, 0.44, "BOOT_CLAW_IVORY", pad=0.14))

        # 3. 2 CỰA VUỐT KHUỶU / CẲNG TAY OBSIDIAN CONG NHỎ GỌN (dọc mặt ngoài-sau gần khuỷu, chĩa chếch ra ngoài-lên trên, 0 xuyên cánh!)
        elbow_spurs = [
            ((cx + s * 2.62, 18.65, +0.95), (cx + s * 3.62, 19.45, +1.35), (cx + s * 4.12, 20.30, +1.55), 0.84, 0.48),
            ((cx + s * 2.62, 16.75, +1.05), (cx + s * 3.52, 17.40, +1.40), (cx + s * 3.92, 18.12, +1.55), 0.74, 0.44),
        ]
        for q0, q1, q2, w1, w2 in elbow_spurs:
            gc.append(make_segment_cube(q0, q1, w1, w1, "PAULDRON_HORN_BASE", pad=0.32, face_mats=phorn_faces_base))
            gc.append(make_segment_cube(q1, q2, w2, w2, "PAULDRON_HORN_TIP",  pad=0.22, face_mats=phorn_faces_tip))
        return gc

    pauldron_horn_r_cubes = build_pauldron_horn(-1.0)
    pauldron_horn_l_cubes = build_pauldron_horn(+1.0)
    claw_gauntlet_r_cubes = build_claw_gauntlet(-1.0)
    claw_gauntlet_l_cubes = build_claw_gauntlet(+1.0)

    # ==================== 3b. QUẦN GIÁP VẢY RỒNG 3D XẾP CHỒNG + ĐAI CHÉO + 3 MẤU GỐI + 2 VÂY HÔNG MẢNH & GIÀY 6 MÓNG VUỐT ====================
    def build_dragon_leg(s: float):
        cx = s * 2.0
        tag = "R" if s < 0 else "L"
        outer_face = "west" if s < 0 else "east"
        inner_face = "east" if s < 0 else "west"
        return [
            # 1. Thân giáp đùi & ống chân vảy đá núi lửa liền mạch (xóa sạch ô caro xám, thấp hơn vảy & đai chéo -> 0 z-fighting!)
            make_centered_cube(
                [cx, 8.125, 0.0], [4.56, 7.05, 4.56], "BASALT_PLATE",
                face_mats={
                    "north":    f"LEG_FRONT_{tag}",
                    "south":    f"LEG_BACK_{tag}",
                    outer_face: "LEG_SIDE",
                    inner_face: "BASALT_DARK",
                    "up":       "HORN_DARK",
                    "down":     "BASALT_DARK",
                }
            ),
            # 2. Vây giáp hông siêu mảnh vát chéo ngoài đùi trên (`hip_fin_r/l` — GIỮ NGUYÊN!)
            make_centered_cube(
                [cx + s * 2.46, 9.65, +0.10], [0.22, 2.45, 0.62], "HORN_DARK",
                rot=[0.0, 0.0, s * +11.0],
            ),
            # 3. CÁC HÀNG VẢY RỒNG 3D NỔI CHỒNG TẦNG SO LE TRÊN ĐÙI & ỐNG CHÂN (nhô 0.42–0.56 px, nghiêng vát xuống!)
            # Mặt trước đùi trên (hàng 1: 2 vảy so le; hàng 2: vảy trung tâm lớn + vảy mép ngoài!)
            make_centered_cube([cx - 0.96, 10.00, -2.44], [1.62, 1.38, 0.44], "SCALE_PLATE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx + 0.96, 10.00, -2.44], [1.62, 1.38, 0.44], "SCALE_PLATE", rot=[-11.0, 0.0, 0.0]),
            make_centered_cube([cx,         8.38, -2.48], [2.10, 1.48, 0.48], "SCALE_PLATE", rot=[-12.0, 0.0, 0.0]),
            make_centered_cube([cx + s * 1.56, 8.32, -2.42], [1.10, 1.36, 0.40], "SCALE_PLATE", rot=[-11.0, s * -10.0, 0.0]),
            # Mặt ngoài đùi & ống chân (2 vảy trên so le + 1 vảy ống chân dưới đai chéo!)
            make_centered_cube([cx + s * 2.44, 9.75, -0.95], [0.42, 1.50, 1.58], "SCALE_PLATE", rot=[0.0, 0.0, s * -12.0]),
            make_centered_cube([cx + s * 2.44, 8.10, +0.45], [0.44, 1.52, 2.15], "SCALE_PLATE", rot=[0.0, 0.0, s * -12.0]),
            make_centered_cube([cx + s * 2.42, 4.75,  0.00], [0.40, 1.52, 2.10], "SCALE_PLATE", rot=[0.0, 0.0, s * -11.0]),
            # Mặt sau đùi (2 hàng vảy rồng 3D nổi chồng lớp so le!)
            make_centered_cube([cx - 0.92, 9.85, +2.44], [1.62, 1.44, 0.44], "SCALE_PLATE", rot=[+11.0, 0.0, 0.0]),
            make_centered_cube([cx + 0.92, 9.85, +2.44], [1.62, 1.44, 0.44], "SCALE_PLATE", rot=[+11.0, 0.0, 0.0]),
            make_centered_cube([cx,        8.25, +2.46], [2.20, 1.45, 0.46], "SCALE_PLATE", rot=[+12.0, 0.0, 0.0]),
            # 4. Đai giáp quấn chéo 3D quanh đùi–gối (giữ nguyên dáng nghiêng +11.5°, đổi sang obsidian tối viền đỏ cực mảnh khớp boss!)
            make_centered_cube(
                [cx, 6.22, 0.0], [5.14, 1.06, 5.26], "OBSIDIAN_RIM",
                rot=[+11.5, 0.0, 0.0],
            ),
            make_centered_cube(
                [cx, 6.92, -0.04], [5.04, 0.52, 5.14], "HORN_DARK",
                rot=[+11.5, 0.0, 0.0],
            ),
            # 5. 3 mấu vảy gối 3D nhô bậc nổi rõ ở mặt trước đầu gối (vảy đá rồng xếp tầng — mấu giữa nhô & dài hơn!)
            make_centered_cube([cx + s * 1.44, 5.22, -2.56], [1.08, 1.02, 0.46], "KNEE_TAB", rot=[-10.0, 0.0, 0.0]),
            make_centered_cube([cx,            5.02, -2.66], [1.26, 1.34, 0.56], "KNEE_TAB", rot=[-12.0, 0.0, 0.0]),
            make_centered_cube([cx - s * 1.44, 5.22, -2.56], [1.08, 1.02, 0.46], "KNEE_TAB", rot=[-10.0, 0.0, 0.0]),
        ]

    rleg_cubes = build_dragon_leg(-1.0)
    lleg_cubes = build_dragon_leg(+1.0)

    def build_dragon_boot(s: float):
        cx = s * 2.0
        tag = "R" if s < 0 else "L"
        outer_face = "west" if s < 0 else "east"
        inner_face = "east" if s < 0 else "west"
        cubes = [
            # Thân giày đá núi lửa bọc cổ chân & gót chân, đáy hạ -0.12 px xuống dưới đế chân Steve -> 0 z-fighting ở góc đáy!
            make_centered_cube(
                [cx, 2.35, 0.0], [4.76, 4.94, 4.76], "BASALT_DARK",
                face_mats={
                    "north":    "BOOT_FRONT",
                    "south":    "BOOT_BACK",
                    outer_face: f"BOOT_SIDE_{tag}",
                    inner_face: "BASALT_DARK",
                    "down":     "BOOT_SOLE",
                    "up":       "BASALT_DARK",
                }
            ),
        ]
        # ĐÚNG 3 NGÓN MÓNG VUỐT RỒNG 3D TÁCH KHE RÕ MỖI CHIẾC GIÀY (TỔNG 6 MÓNG TRẮNG NGÀ CHO 2 CHÂN!)
        # Mỗi ngón liền khối 100% (0 khe hở gầm): sống khớp trên đỏ núi lửa dốc xuống + lõi nối + đầu móng vuông bậc trắng ngà!
        for offset_x in (-1.38, 0.0, +1.38):
            # Lõi nối khớp dưới đỏ núi lửa liền mạch từ mũi ống chân ra đầu móng (xóa sạch khe hở tam giác ở góc bên!)
            cubes.append(make_centered_cube(
                [cx + offset_x, 0.56, -2.78], [0.90, 1.36, 1.05], "CLAW_CRIMSON",
                face_mats={**claw_tip_faces, "down": "BOOT_SOLE"},
            ))
            # Sống khớp trên đỏ núi lửa dốc mượt từ mũi ống chân xuống đầu móng
            cubes.append(make_segment_cube(
                (cx + offset_x, 2.18, -2.18),
                (cx + offset_x, 0.98, -3.46),
                0.90, 0.88, "CLAW_CRIMSON", pad=0.18, face_mats=claw_tip_faces,
            ))
            # Đầu móng vuông bậc trắng ngà phong hóa nhô thẳng ra trước ~1.82 px (`z = -2.86 .. -4.18`, phẳng đáy `y = -0.12`)
            cubes.append(make_centered_cube(
                [cx + offset_x, 0.44, -3.52], [0.92, 1.12, 1.32], "BOOT_CLAW_IVORY"
            ))
        return cubes

    rboot_cubes = build_dragon_boot(-1.0)
    lboot_cubes = build_dragon_boot(+1.0)

    bones = [
        {"name": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},
        {"name": "horn_r",    "parent": "armorHead", "pivot": [-4.6, 31.5, 0.0], "cubes": horn_r_cubes},
        {"name": "horn_l",    "parent": "armorHead", "pivot": [+4.6, 31.5, 0.0], "cubes": horn_l_cubes},

        {"name": "bipedBody",     "pivot": [0, 24, 0]},
        {"name": "armorBody",     "parent": "bipedBody",     "pivot": [0, 24, 0], "cubes": body_cubes},
        {"name": "wing_r_upper",  "parent": "armorBody",     "pivot": [-2.0, 21.4, +2.6],   "cubes": wing_r_upper},
        {"name": "wing_r_mid",    "parent": "wing_r_upper",  "pivot": [-7.83, 25.2, +4.72], "cubes": wing_r_mid},
        {"name": "wing_r_tip",    "parent": "wing_r_mid",    "pivot": [-14.22, 27.8, +7.05],"cubes": wing_r_tip},
        {"name": "wing_l_upper",  "parent": "armorBody",     "pivot": [+2.0, 21.4, +2.6],   "cubes": wing_l_upper},
        {"name": "wing_l_mid",    "parent": "wing_l_upper",  "pivot": [+7.83, 25.2, +4.72], "cubes": wing_l_mid},
        {"name": "wing_l_tip",    "parent": "wing_l_mid",    "pivot": [+14.22, 27.8, +7.05],"cubes": wing_l_tip},
        {"name": "tail_1",        "parent": "armorBody",     "pivot": [0.0, 12.60, +2.60],  "cubes": tail_1_cubes},
        {"name": "tail_2",        "parent": "tail_1",        "pivot": [0.0, 11.45, +5.90],  "cubes": tail_2_cubes},
        {"name": "tail_3",        "parent": "tail_2",        "pivot": [0.0, 10.70, +9.25],  "cubes": tail_3_cubes},
        {"name": "tail_4",        "parent": "tail_3",        "pivot": [0.0, 10.95, +12.55], "cubes": tail_4_cubes},
        {"name": "tail_tip",      "parent": "tail_4",        "pivot": [0.0, 11.70, +15.60], "cubes": tail_tip_cubes},

        {"name": "bipedRightArm",   "pivot": [-5, 22, 0]},
        {"name": "armorRightArm",   "parent": "bipedRightArm", "pivot": [-5, 22, 0], "cubes": rarm_cubes},
        {"name": "pauldron_horn_r", "parent": "armorRightArm", "pivot": [-8.10, 23.60, -0.15], "cubes": pauldron_horn_r_cubes},
        {"name": "claw_gauntlet_r", "parent": "armorRightArm", "pivot": [-6.0,  13.50,  0.00], "cubes": claw_gauntlet_r_cubes},

        {"name": "bipedLeftArm",    "pivot": [5, 22, 0]},
        {"name": "armorLeftArm",    "parent": "bipedLeftArm",  "pivot": [5, 22, 0], "cubes": larm_cubes},
        {"name": "pauldron_horn_l", "parent": "armorLeftArm",  "pivot": [+8.10, 23.60, -0.15], "cubes": pauldron_horn_l_cubes},
        {"name": "claw_gauntlet_l", "parent": "armorLeftArm",  "pivot": [+6.0,  13.50,  0.00], "cubes": claw_gauntlet_l_cubes},

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
                    "visible_bounds_width": 4.2,
                    "visible_bounds_height": 4.0,
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
    """
    Để trống `bones` trong `.animation.json` để `CinderDragonArmorModel.setCustomAnimations(...)`
    là NGUỒN ĐIỀU KHIỂN DUY NHẤT (0 áp chồng keyframe gây lệch pha bập bênh cánh hay giật đuôi!).
    """
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
    print(f"ANIM_OK: {out} (single-source-of-truth: empty JSON bones, driven strictly by CinderDragonArmorModel.setCustomAnimations)")


def build_cinder_dragon_armor_textures():
    """
    Texture pixel-art 1:1 (`128x128`, 0 noise, >= 4px transparent padding quanh mọi màng cánh)
    + Glowmask chọn lọc (`cinder_dragon_armor_glowmask.png` CHỈ phát sáng tại:
      1. Đôi mắt rồng cam-vàng có con ngươi dọc sắc trên mũ `DRAGON_EYE`
      2. Khe lõi dung nham giữa ngực `CHEST_CORE`
      3. Lõi tinh thể than hồng chùy đuôi `TAIL_CRYSTAL`!)
    """
    verify_uv_and_wing_padding()
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    P = {
        ".": (0, 0, 0, 0),
        # Vảy đá núi lửa xám than–nâu tro LẤY MẪU TRỰC TIẾP TỪ `cinder_dragon.png` (KHÔNG tối đen bệt!)
        "0": (29, 25, 24, 255),       # #1D1918 — rãnh bóng sâu khe vảy
        "1": (42, 38, 36, 255),       # #2A2624 — rãnh khe vảy boss
        "2": (60, 55, 52, 255),       # #3C3734 — nền đá núi lửa thân boss
        "3": (80, 73, 68, 255),       # #504944 — mặt vảy nổi thân boss
        "4": (104, 96, 90, 255),      # #68605A — viền sáng cạnh vảy nổi!

        # Obsidian xám-tím than tối (#1D1A22 -> #2C2834 -> #3F394A) + viền cạnh sáng sắc nét (#5C556A) cho sừng vai & cựa khuỷu!
        "o": (29, 26, 34, 255),       # #1D1A22
        "O": (44, 40, 52, 255),       # #2C2834
        "p": (63, 57, 74, 255),       # #3F394A
        "P": (92, 85, 106, 255),      # #5C556A — ánh cạnh sừng tách khối sắc trên nền cánh đỏ!

        # Màng cánh đỏ thẫm (giữ nguyên chuẩn màng cánh trong `cinder_dragon.png`!)
        "w": (92, 18, 16, 255),       # #5C1210
        "W": (143, 31, 24, 255),      # #8F1F18
        "m": (184, 46, 34, 255),      # #B82E22

        # Viền đỏ núi lửa trầm cực tinh tế (chỉ điểm khe mảnh, KHÔNG kẻ sọc đỏ đậm dày đặc!)
        "r": (74, 24, 20, 255),       # #4A1814
        "R": (108, 34, 26, 255),      # #6C221A
        "k": (138, 46, 34, 255),      # #8A2E22

        # Lớp lót cánh tay đá tro tối đồng bộ màu thân boss (bọc kín 100%, không đen bệt!)
        "a": (38, 34, 32, 255),       # #262220
        "A": (54, 49, 46, 255),       # #36312E

        # Trắng ngà xương phong hóa (răng nanh, 6 móng vuốt giày) & Xám tro đá sáng (khóa đai, ô điểm nhấn đùi)
        "b": (156, 149, 139, 255),    # #9C958B
        "B": (202, 195, 184, 255),    # #CAC3B8
        "I": (230, 224, 212, 255),    # #E6E0D4
        "g": (143, 136, 126, 255),    # #8F887E — xám tro đá sáng
        "G": (190, 183, 172, 255),    # #BEB7AC
        "E": (221, 214, 202, 255),    # #DDD6CA

        # Đá núi lửa xám-nâu (lõi ngực, mai lưng, thân đuôi)
        "d": (38, 34, 32, 255),       # #262220
        "D": (62, 55, 51, 255),       # #3E3733
        "s": (89, 80, 74, 255),       # #59504A

        # Dung nham cam-vàng cháy & lõi vàng trắng hỏa long (mắt rồng, rãnh hốc ngực, 3 viên ngọc ngực, lõi tinh thể chùy đuôi)
        "f": (214, 73, 0, 255),       # #D64900
        "F": (255, 138, 24, 255),     # #FF8A18
        "Y": (255, 224, 112, 255),    # #FFE070
        "H": (255, 246, 194, 255),    # #FFF6C2 — lõi sáng vàng trắng rực rỡ!
    }
    GLOW_CHARS = {"f", "F", "Y", "H"}

    def paint(key: str, rows, emit_glow: bool = False):
        x0, y0, w, h = REG[key]
        assert len(rows) == h, f"{key}: expected {h} rows, got {len(rows)}"
        for y, row in enumerate(rows):
            assert len(row) == w, f"{key} row {y}: expected {w} chars, got {len(row)}"
            for x, ch in enumerate(row):
                col = P[ch]
                diff[y0 + y, x0 + x] = col
                if emit_glow and ch in GLOW_CHARS:
                    glow[y0 + y, x0 + x] = col

    # 1. ĐẦU RỒNG CINDER DRAGON (vảy đá núi lửa + lỗ mũi + mắt cam-vàng con ngươi dọc sắc + răng nanh ngà)
    paint("HELM_BACK", [
        "12322321",
        "23433432",
        "12311321",
        "23211232",
        "12311321",
        "23211232",
        "12100121",
        "01100110",
    ])
    paint("HELM_SIDE", [
        "12343221",
        "23432321",
        "12321221",
        "23212321",
        "12221211",
        "23212211",
        "11211210",
        "01111110",
    ])
    paint("HELM_TOP", [
        "12344321",
        "23233232",
        "23422432",
        "12322321",
        "12344321",
        "23233232",
        "12344321",
        "12233221",
    ])
    paint("DRAGON_SNOUT", [
        "234432",
        "123321",
        "232232",
        "123321",
    ])
    paint("DRAGON_NOSE", [
        "234432",
        "203302",  # 2 lỗ mũi rồng đối xứng!
        "123321",
    ])
    # Đôi mắt rồng cam-vàng có con ngươi dọc sắc (phát sáng nhẹ trên glowmask!)
    paint("DRAGON_EYE", [
        "F0F",
        "f0Y",
    ], emit_glow=True)
    paint("FANG_IVORY", [
        "B",
        "I",
        "I",
    ])

    # 2. VẬT LIỆU PHẲNG SẠCH 4x4 (vân sống obsidian rõ nét cho sừng, khung cánh, lưỡi liềm & gai trục đuôi!)
    paint("HORN_BASE", [
        "oOpo",
        "OpoO",
        "oOpo",
        "ooOo",
    ])
    paint("HORN_MID", [
        "OpOo",
        "ppOp",
        "oOpo",
        "ooOo",
    ])
    paint("HORN_DARK", [
        "oOOo",
        "ooOo",
        "oooo",
        "0oo0",
    ])
    paint("BONE_LIGHT", [
        "IIBB",
        "IBBb",
        "BBbb",
        "bbbb",
    ])
    paint("BASALT_PLATE", [
        "2343",
        "3232",
        "1221",
        "1121",
    ])
    paint("BASALT_DARK", [
        "1221",
        "1110",
        "1010",
        "0000",
    ])
    paint("CRIMSON_TRIM", [
        "RkkR",
        "rRRr",
        "rRRr",
        "rrr0",
    ])
    paint("WING_BONE", [
        "oOpo",
        "OOpO",
        "oOOo",
        "0oo0",
    ])
    paint("TAIL_SEG", [
        "1231",
        "2342",
        "1231",
        "0120",
    ])
    # Lõi tinh thể than hồng cam-vàng rực sáng ở chùy đuôi (100% emissive trên glowmask!)
    paint("TAIL_CRYSTAL", [
        "fFFf",
        "FYYF",
        "FYYF",
        "fFFf",
    ], emit_glow=True)
    # 3 viên ngọc tinh thể hỏa long trước ngực (#D64900 -> #FF8A18 -> #FFE070 -> #FFF6C2, fullbright glowmask!)
    paint("CHEST_GEM", [
        "fFYF",
        "FYHY",
        "YHHY",
        "FFYf",
    ], emit_glow=True)
    # 6 đầu móng kẹp ngực & khớp móng giày: ĐÁ OBSIDIAN XÁM-TÍM THAN TỐI (#1D1A22/#2C2834/#3F394A) + điểm viền đỏ trầm cực mảnh!
    paint("CLAW_CRIMSON", [
        "OpOp",
        "pOOp",
        "oOOr",
        "ooOo",
    ])
    # Viền khung đai & rãnh ngực bằng obsidian tối điểm 1 tia đỏ trầm tinh tế (thay thế mảng đỏ chói!)
    paint("OBSIDIAN_RIM", [
        "oOpo",
        "OOpO",
        "oOOr",
        "0oo0",
    ])
    # Tấm vảy rồng đá núi lửa 3D nổi khối — kết cấu rạn đá + cạnh sáng #68605A đồng bộ 100% `cinder_dragon.png`!
    paint("SCALE_PLATE", [
        "3443",
        "2342",
        "2332",
        "1221",
    ])
    # Vòng vảy rồng xếp chồng cho bắp tay & cẳng tay (0 sọc đỏ đậm!)
    paint("SCALE_RING", [
        "34343",
        "23432",
        "12121",
    ])
    # Sừng vai & cựa khuỷu obsidian có cạnh sáng sắc nét (#5C556A) + chóp ánh tro/ngà nhẹ nổi bật trên nền cánh đỏ!
    paint("PAULDRON_HORN_BASE", [
        "oOpP",
        "OOpP",
        "oOpo",
        "ooOo",
    ])
    paint("PAULDRON_HORN_MID", [
        "OpPP",
        "pPPp",
        "OOpP",
        "oOpo",
    ])
    paint("PAULDRON_HORN_TIP", [
        "pPbB",
        "PbBB",
        "pPbB",
        "OpPP",
    ])
    paint("EMPTY_TRANS", ["."])

    # Rãnh hốc dung nham phát sáng ở mặt trước ngực (trên hẹp nối cổ -> giữa mở rộng ôm sau 3 viên ngọc!)
    paint("CHEST_CAVITY", [
        "122fFFf221",
        "232fFYf232",
        "122FYHY221",
        "232FYHY232",
        "12fFYHYf21",
        "1fFYHHYFf1",
        "1fYHHHHYf1",
        "1fYHHHHYf1",
        "1fFYHHYFf1",
        "12fFYYFf21",
        "122fFFf221",
        "0112222110",
    ], emit_glow=True)

    # 3. THÂN GIÁP, VAI, TAY KÍN, QUẦN, GIÀY (GIỮ NGUYÊN 100%!)
    paint("CHEST_FRONT", [
        "12322321",
        "23422432",
        "12322321",
        "232dd232",
        "122DD221",
        "232dd232",
        "122DD221",
        "232dd232",
        "12322321",
        "23233232",
        "rRkkkkRr",
        "01233210",
    ])
    paint("CHEST_CORE", [
        "dDDd",
        "DssD",
        "dfFd",
        "dFYd",
        "dYYd",
        "dFYd",
        "dfFd",
        "DssD",
        "dDDd",
        "rRRr",
    ], emit_glow=False)
    # Mặt sau lưng áo giáp đồng bộ 100% vảy đá núi lửa boss (#2A2624 -> #68605A) + sống lưng obsidian chạy thẳng xuống gốc đuôi (0 mảng đỏ/vàng!)
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
        "1232",
        "2342",
        "1221",
        "0110",
        "1232",
        "2342",
        "1221",
        "0110",
        "1232",
        "2332",
        "oOpo",
        "0110",
    ])
    # Mai sống lưng bằng vảy đá núi lửa & dải sống giữa obsidian tối nối liền mạch xuống `tail_1`!
    paint("BACK_SHELL", [
        "23op32",
        "34po43",
        "12op21",
        "34po43",
        "23op32",
        "34po43",
        "12op21",
        "34po43",
        "23op32",
        "12oo21",
    ])
    # Giáp vai, ống tay & cổ tay chuyển sang kết cấu vảy đá núi lửa xám-nâu khớp thân boss (bỏ toàn bộ sọc đỏ dày!)
    paint("PAULDRON_FRONT", [
        "oOpOo",
        "34432",
        "23432",
        "12321",
        "oOOr0",
    ])
    paint("PAULDRON_OUT", [
        "oOpOo",
        "34432",
        "23321",
        "23432",
        "12210",
    ])
    paint("ARM_SLEEVE", [
        "aAAa",
        "A23A",
        "aAAa",
        "A32A",
        "aAAa",
        "A23A",
        "aAAa",
        "A32A",
        "aAAa",
        "a22a",
        "aaaa",
    ])
    paint("ARM_CAP", [
        "aaaa",
        "aAAa",
        "aAAa",
        "aaaa",
    ])
    paint("ARM_BAND", [
        "34432",
        "12321",
    ])
    paint("GAUNTLET_CUFF", [
        "oOpOo",
        "34432",
        "23321",
    ])
    paint("LEG_THIGH", [
        "2342",
        "1231",
        "2342",
        "1221",
        "2342",
        "1231",
        "2331",
        "1221",
    ])
    paint("LEG_BELT", [
        "oOpOo",
        "23432",
        "12321",
    ])
    paint("BOOT_BODY", [
        "oOpOo",
        "23432",
        "12321",
        "23432",
        "12221",
    ])
    paint("BOOT_SOLE", [
        "0oo0o",
        "o121o",
        "o212o",
        "o121o",
        "0oo0o",
    ])
    paint("BOOT_CLAW", [
        "II",
        "bB",
    ])

    # 3b. TEXTURE 1:1 ĐAI EO, QUẦN VẢY RỒNG & GIÀY 6 MÓNG VUỐT — ĐỒNG BỘ 100% VẢY ĐÁ BOSS CINDER DRAGON (0 CARO XÁM, 0 SỌC ĐỎ DÀY!)
    paint("WAIST_BELT_FRONT", [
        "oOpo34opOo",
        "ooOo23oOoo",
    ])
    # Ô khóa giữa đai eo bằng đá tro xám, điểm lõi cam cháy mảnh (glowmask) nối mạch từ cụm ngọc ngực!
    paint("WAIST_BUCKLE", [
        "3443",
        "4Ff4",
        "4FF4",
        "2332",
    ], emit_glow=True)
    paint("ASH_STONE", [
        "3443",
        "2332",
        "2332",
        "1221",
    ])
    # 3 mấu vảy gối 3D bằng đá núi lửa vát sáng đỉnh, viền khe obsidian
    paint("KNEE_TAB", [
        "343",
        "232",
        "121",
    ])
    # Mặt trước đùi/ống chân: vảy đá núi lửa rạn so le khớp 100% thân boss (xóa sạch ô vuông caro xám lạc tông!)
    leg_front_r = [
        "o231",
        "2342",
        "1231",
        "3432",
        "2331",
        "1231",
        "2342",
        "1221",
    ]
    paint("LEG_FRONT_R", leg_front_r)
    paint("LEG_FRONT_L", [row[::-1] for row in leg_front_r])
    # Mặt sau đùi/ống chân: vảy đá núi lửa đồng nhất thân boss
    leg_back_l = [
        "o231",
        "2342",
        "1231",
        "2331",
        "1221",
        "1231",
        "2342",
        "1221",
    ]
    paint("LEG_BACK_L", leg_back_l)
    paint("LEG_BACK_R", [row[::-1] for row in leg_back_l])
    # Mặt bên ngoài đùi/ống chân: vảy đá núi lửa sần tự nhiên
    paint("LEG_SIDE", [
        "1231",
        "2342",
        "1231",
        "2342",
        "1231",
        "2332",
        "1231",
        "1221",
    ])
    # Thân giày 4 mặt bằng vảy đá núi lửa đồng bộ màu quần & mũ (bỏ viền đỏ dày, giữ viền obsidian tinh tế!)
    paint("BOOT_FRONT", [
        "2342",
        "1231",
        "2342",
        "oOpo",
        "1231",
    ])
    boot_side_r = [
        "1231",
        "2342",
        "1231",
        "123O",
        "12Op",
    ]
    paint("BOOT_SIDE_R", boot_side_r)
    paint("BOOT_SIDE_L", [row[::-1] for row in boot_side_r])
    paint("BOOT_BACK", [
        "1231",
        "2342",
        "1231",
        "1221",
        "o12o",
    ])
    paint("BOOT_CLAW_IVORY", [
        "BIIB",
        "IIBI",
        "BIIb",
        "bBBb",
    ])

    # 4. 3 TẤM MÀNG CÁNH DƠI ĐỎ THẪM CẮT CUNG TOÁN HỌC CHÍNH XÁC 100% KHỚP XƯƠNG & NAN CÁNH!
    # Quy ước `*_R` (mặt trước cánh phải -X): col 0 = mép NGOÀI (t_max), col w-1 = mép TRONG (t_min); `*_L` = gương ngang!
    # Khi lắp lên cánh: north = *_R, south = *_L (cánh phải) và đảo ngược cho cánh trái -> 2 mặt trước/sau khớp 100%!
    def build_wing_mem_rows(w: int, h: int, t0: float, t1: float, y0: float, y1: float, top_fn, bot_fn):
        rows = []
        for r in range(h):
            # row 0 = y1 (đỉnh trên), row h-1 = y0 (mép dưới)
            y = y1 - (r + 0.5) / h * (y1 - y0)
            chars = []
            for c in range(w):
                # col 0 = t1 (NGOÀI cho cánh phải), col w-1 = t0 (TRONG)
                t = t1 - (c + 0.5) / w * (t1 - t0)
                yt = top_fn(t)
                yb = bot_fn(t)
                if yb <= y <= yt:
                    # Phân sắc vân màng cánh đỏ thẫm (`w` = viền tối sát xương/mép, `W` = thân màng, `m` = gân sáng trung tâm)
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

    # Bay 1: WING_MEM_INNER (`t in [0.0, 6.3]`, `y in [14.4, 25.4]`) — khớp hoàn hảo xương vai->khuỷu & nan #1!
    mem_inner_r = build_wing_mem_rows(
        10, 16, 0.0, 6.30, 14.40, 25.40,
        top_fn=lambda t: 21.40 + (25.20 - 21.40) * min(1.0, max(0.0, t / 6.20)),
        bot_fn=lambda t: arch(t, 0.0, 14.80, 5.80, 14.60, lift=2.55) if t <= 5.80 else 14.60 + (t - 5.80) * 0.4,
    )
    # Bay 2: WING_MEM_MID (`t in [5.7, 13.5]`, `y in [14.2, 28.0]`) — khớp hoàn hảo xương khuỷu->cổ cánh & 2 cung giữa nan #1/#2/#3!
    mem_mid_r = build_wing_mem_rows(
        12, 20, 5.70, 13.50, 14.20, 28.00,
        top_fn=lambda t: 25.20 + (27.80 - 25.20) * min(1.0, max(0.0, (t - 6.20) / (13.00 - 6.20))),
        bot_fn=lambda t: (
            arch(t, 5.80, 14.60, 9.60, 14.40, lift=2.90)
            if t <= 9.60
            else arch(t, 9.60, 14.40, 13.40, 15.00, lift=3.10)
        ),
    )
    # Bay 3: WING_MEM_OUTER (`t in [13.0, 20.8]`, `y in [14.8, 28.0]`) — kín 100% sát dưới xương mũi cánh tới tận đỉnh nhọn!
    mem_outer_r = build_wing_mem_rows(
        12, 18, 13.00, 20.80, 14.80, 28.00,
        top_fn=lambda t: 27.80 + (25.20 - 27.80) * min(1.0, max(0.0, (t - 13.00) / (20.60 - 13.00))),
        bot_fn=lambda t: (
            arch(t, 13.40, 15.00, 17.40, 17.60, lift=2.80)
            if t <= 17.40
            else (17.60 + (25.20 - 17.60) * (max(0.0, min(1.0, (t - 17.40) / (20.60 - 17.40))) ** 0.72))
        ),
    )
    paint("WING_MEM_INNER_R", mem_inner_r)
    paint("WING_MEM_MID_R",   mem_mid_r)
    paint("WING_MEM_OUTER_R", mem_outer_r)
    paint("WING_MEM_INNER_L", [row[::-1] for row in mem_inner_r])
    paint("WING_MEM_MID_L",   [row[::-1] for row in mem_mid_r])
    paint("WING_MEM_OUTER_L", [row[::-1] for row in mem_outer_r])

    # Kiểm chứng nghiêm ngặt:
    # 1. Mọi pixel màng cánh đều có alpha nhị phân {0, 255} và vùng đệm 2px xung quanh hoàn toàn alpha = 0!
    for k in ("WING_MEM_INNER_R", "WING_MEM_MID_R", "WING_MEM_OUTER_R", "WING_MEM_INNER_L", "WING_MEM_MID_L", "WING_MEM_OUTER_L"):
        x0, y0, w, h = REG[k]
        sub_a = diff[y0:y0 + h, x0:x0 + w, 3]
        assert np.all((sub_a == 0) | (sub_a == 255)), f"Non-binary alpha in {k}"
        pad = diff[y0 - 2:y0 + h + 2, x0 - 2:x0 + w + 2, 3].copy()
        pad[2:2 + h, 2:2 + w] = 0
        assert not np.any(pad != 0), f"Non-zero padding around {k}"

    # 2. Glowmask CHỈ có alpha > 0 tại DRAGON_EYE, CHEST_GEM, CHEST_CAVITY, TAIL_CRYSTAL & tâm khóa đai WAIST_BUCKLE (0 sáng quần/giày)!
    g_mask = glow[:, :, 3] > 0
    allowed = np.zeros((TEX_H, TEX_W), dtype=bool)
    for k in ("DRAGON_EYE", "CHEST_GEM", "CHEST_CAVITY", "TAIL_CRYSTAL", "WAIST_BUCKLE"):
        x0, y0, w, h = REG[k]
        allowed[y0:y0 + h, x0:x0 + w] = True
    assert not np.any(g_mask & ~allowed), "Glowmask leaked outside DRAGON_EYE / CHEST_GEM / CHEST_CAVITY / TAIL_CRYSTAL / WAIST_BUCKLE!"
    glow_px = int(np.sum(g_mask))
    assert glow_px > 0

    out_dir = ASSETS / "textures" / "armor"
    out_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(out_dir / "cinder_dragon_armor.png")
    Image.fromarray(glow, "RGBA").save(out_dir / "cinder_dragon_armor_glowmask.png")
    print(f"TEX_ARMOR_OK: {TEX_W}x{TEX_H} 1:1 pure pixel-art + glowmask (glowPixels={glow_px} ONLY dragon eyes, chest gems/cavity, buckle core & tail crystal, 0 wing-edge bleed)")
    return diff.astype(np.float32), glow.astype(np.float32)


def update_helmet_item_icon():
    """Đồng bộ icon 16x16 của `cinder_dragon_helmet.png` sang dáng Đầu Rồng Cinder Dragon (sừng mũi, mắt cam-vàng, răng nanh, 2 cặp sừng vương miện)."""
    PAL = {
        ".": (0, 0, 0, 0),
        "0": (14, 12, 12, 255),
        "1": (27, 24, 23, 255),
        "2": (44, 39, 37, 255),
        "3": (64, 57, 53, 255),
        "4": (91, 82, 76, 255),
        "o": (21, 19, 26, 255),
        "O": (44, 38, 54, 255),
        "I": (230, 224, 212, 255),
        "F": (255, 138, 24, 255),
        "Y": (255, 224, 112, 255),
    }
    rows = [
        "................",
        ".0O0...00...0O0.",
        ".0oO0.0oO0.0Oo0.",
        "..0oO00oO00Oo0..",
        "..0o02344320o0..",
        ".0O0234334320O0.",
        ".0o0F023320F0o0.",
        "..00Y023320Y00..",
        "...0232002320...",
        "...0I234432I0...",
        "...0I.0000.I0...",
        "...0I......I0...",
        "....0......0....",
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

    add_box((-4.0, 24.0, -4.0), (4.0, 32.0, 4.0), (206, 160, 128))
    add_box((-4.02, 29.8, -4.02), (4.02, 32.02, 4.02), (62, 40, 26))
    add_box((-3.0, 27.0, -4.05), (-2.0, 28.0, -3.98), (244, 246, 250))
    add_box((-2.0, 27.0, -4.05), (-1.0, 28.0, -3.98), (58, 122, 74))
    add_box((+2.0, 27.0, -4.05), (+3.0, 28.0, -3.98), (244, 246, 250))
    add_box((+1.0, 27.0, -4.05), (+2.0, 28.0, -3.98), (58, 122, 74))
    add_box((-1.0, 26.0, -4.04), (+1.0, 27.0, -3.98), (180, 134, 104))
    add_box((-1.5, 25.0, -4.04), (+1.5, 25.8, -3.98), (132, 84, 66))
    add_box((-4.0, 12.0, -2.0), (+4.0, 24.0, +2.0), (46, 134, 140))
    add_box((-4.0,  0.0, -2.0), ( 0.0, 12.0, +2.0), (54, 58, 122))
    add_box(( 0.0,  0.0, -2.0), (+4.0, 12.0, +2.0), (54, 58, 122))
    add_box((-8.25, 12.0, -2.25), (-3.75, 24.25, +2.25), (226, 174, 136))
    add_box((+3.75, 12.0, -2.25), (+8.25, 24.25, +2.25), (226, 174, 136))
    return faces


def render_multi_angle_preview(geo: dict, diff_img, glow_img):
    """
    Xuất bảng preview 6 góc theo đúng yêu cầu `acd1.txt`:
      1. Chính diện toàn thân + sải cánh rộng
      2. Cận cảnh Đầu Rồng Cinder Dragon góc 3/4 (mõm, sừng mũi, mắt cam-vàng, răng nanh, 2 cặp sừng vương miện, lộ mặt Steve!)
      3. Góc sau lưng toàn thân (cánh dơi 3 khớp có móng cổ cánh, 0 đường kẻ đen lỗi UV, đuôi dài chữ S!)
      4. Góc bên phải (profile mõm + sừng mũi + 2 cặp sừng ngược + cánh dơi + đuôi cong chữ S + chùy đuôi!)
      5. Góc nhìn từ trên xuống toàn thân (đối chiếu sải cánh + 2 cặp sừng + đuôi lưỡi liềm!)
      6. Cận cảnh từ trên xuống riêng CHÙY ĐUÔI LƯỠI LIỀM ĐÔI (đối chiếu 1:1 ảnh 4: 2 lưỡi liềm obsidian + lõi than + gai trục giữa!)
    """
    faces = build_steve_body_faces()
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
                faces.append((corners[idxs], uv_map[fname], None))

    def raster(view_mat, w, h, sc, center, ambient=0.58, bg=(56, 60, 74)):
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.28, 0.74, -0.60], dtype=np.float64)
        ld /= np.linalg.norm(ld)
        cx, cy = w * 0.5, h * 0.52

        for quad, uv, flat_rgb in faces:
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
                    lit = np.where(g_a > 0.5, glow_img[v[vis], u[vis], :3] * 1.05, lit)
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    ctr_full = np.array([0.0, 19.0, 2.5], dtype=np.float64)
    ctr_head = np.array([0.0, 30.2, -2.5], dtype=np.float64)
    ctr_side = np.array([0.0, 19.5, +6.5], dtype=np.float64)
    ctr_top  = np.array([0.0, 18.0, +6.0], dtype=np.float64)
    ctr_tail = np.array([0.0, 12.6, +18.2], dtype=np.float64)

    pw, ph = 460, 460
    im_front      = raster(cam_mat(180,   4), pw, ph,  8.6, ctr_full)
    im_head_34    = raster(cam_mat(144,   8), pw, ph, 17.5, ctr_head)
    im_back       = raster(cam_mat(0,     4), pw, ph,  8.6, ctr_full)
    im_side       = raster(cam_mat(90,    4), pw, ph,  9.2, ctr_side)
    im_top_full   = raster(cam_mat(180, +86), pw, ph,  8.6, ctr_top)
    im_tail_top   = raster(cam_mat(0,   +84), pw, ph, 30.0, ctr_tail)

    sheet = Image.new("RGB", (pw * 3, ph * 2), (16, 17, 23))
    panels = [
        (im_front,    0, 0, "1. FRONT (DRAGON HEAD + WIDE BAT WINGS + CLEAR FACE)"),
        (im_head_34,  1, 0, "2. HEAD 3/4 CLOSE-UP (SNOUT, NASAL HORN, EYES, FANGS, 2x HORNS)"),
        (im_back,     2, 0, "3. BACK (3-JOINT BAT WINGS + THUMB CLAW + 0 BLACK EDGE LINE)"),
        (im_side,     0, 1, "4. RIGHT SIDE PROFILE (SNOUT + WINGS + LONG S-CURVED TAIL)"),
        (im_top_full, 1, 1, "5. TOP-DOWN FULL (2.4x WINGSPAN + SWEPT HORNS + PINCER TAIL)"),
        (im_tail_top, 2, 1, "6. TOP-DOWN TAIL CLUB CLOSE-UP (TWIN CRESCENTS + EMBER + SPIKE)"),
    ]
    dr = ImageDraw.Draw(sheet)
    for im, gx, gy, title in panels:
        sheet.paste(im, (gx * pw, gy * ph))
        dr.text((gx * pw + 8, gy * ph + 8), title, fill=(238, 234, 224))

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out_preview = PREVIEW_DIR / "cinder_dragon_armor_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")

    # Bảng cận cảnh riêng cụm NGỰC KẸP NGỌC 3D đúng 3 góc mẫu (chính diện, từ dưới lên, 3/4 trên xuống) + toàn thân!
    ctr_chest = np.array([0.0, 17.6, -2.85], dtype=np.float64)
    ch_front   = raster(cam_mat(180,   0), pw, ph, 38.0, ctr_chest, bg=(26, 24, 32))
    ch_under   = raster(cam_mat(180, -68), pw, ph, 42.0, np.array([0.0, 17.2, -2.95]), bg=(26, 24, 32))
    ch_34      = raster(cam_mat(162, +34), pw, ph, 38.0, ctr_chest, bg=(26, 24, 32))
    ch_full    = raster(cam_mat(180,   4), pw, ph,  8.8, ctr_full,  bg=(52, 56, 70))

    chest_sheet = Image.new("RGB", (pw * 2, ph * 2), (16, 17, 23))
    chest_panels = [
        (ch_front, 0, 0, "1. CHEST FRONT (MAGMA CHANNEL + 6 CURVED CLAWS + 3 GEMS)"),
        (ch_under, 1, 0, "2. CHEST BOTTOM-UP (3D CLAW ARCH + PROTRUDING GEMS)"),
        (ch_34,    0, 1, "3. CHEST 3/4 TOP-DOWN (OBSIDIAN ROOTS + CRIMSON CLAW TIPS)"),
        (ch_full,  1, 1, "4. FULL BODY (ZERO ARM/DUAL-KNIFE CLIPPING + BOSS SILHOUETTE)"),
    ]
    dr2 = ImageDraw.Draw(chest_sheet)
    for im, gx, gy, title in chest_panels:
        chest_sheet.paste(im, (gx * pw, gy * ph))
        dr2.text((gx * pw + 8, gy * ph + 8), title, fill=(242, 236, 224))
    out_chest = PREVIEW_DIR / "cinder_dragon_armor_chest_preview.png"
    chest_sheet.save(out_chest)
    print(f"CHEST_PREVIEW_OK: {out_chest}")

    # Bảng cận cảnh 5 góc QUẦN + GIÀY (Trước, Phải, Sau, Trái, Đáy) + 1 góc Toàn thân có Steve bên trong!
    ctr_legs = np.array([0.0, 6.4, -0.4], dtype=np.float64)
    ctr_sole = np.array([0.0, 0.5, -1.0], dtype=np.float64)
    lg_front = raster(cam_mat(180,   0), pw, ph, 27.5, ctr_legs, bg=(48, 50, 62))
    lg_right = raster(cam_mat(+90,   0), pw, ph, 27.5, ctr_legs, bg=(48, 50, 62))
    lg_back  = raster(cam_mat(  0,   0), pw, ph, 27.5, ctr_legs, bg=(48, 50, 62))
    lg_left  = raster(cam_mat(-90,   0), pw, ph, 27.5, ctr_legs, bg=(48, 50, 62))
    lg_sole  = raster(cam_mat(180, -86), pw, ph, 34.0, ctr_sole, bg=(48, 50, 62))
    lg_full  = raster(cam_mat(180,   4), pw, ph,  8.8, ctr_full, bg=(52, 56, 70))

    legs_sheet = Image.new("RGB", (pw * 3, ph * 2), (16, 17, 23))
    legs_panels = [
        (lg_front, 0, 0, "1. LEGS & BOOTS FRONT (HIP FINS + 3 KNEE TABS + 6 IVORY CLAWS)"),
        (lg_right, 1, 0, "2. RIGHT SIDE (SLANTED 3D KNEE BAND + SLOPED CLAW TOE)"),
        (lg_back,  2, 0, "3. BACK (OBSIDIAN BELT + HIP FINS + CALF PLATES)"),
        (lg_left,  0, 1, "4. LEFT SIDE (SYMMETRIC SLANTED BAND + CLAW PROFILE)"),
        (lg_sole,  1, 1, "5. BOTTOM SOLE (DARK SOLES + 6 DISTINCT IVORY CLAWS)"),
        (lg_full,  2, 1, "6. FULL BODY WITH STEVE (HEAD/CHEST/WINGS/TAIL/ARMS INTACT)"),
    ]
    dr3 = ImageDraw.Draw(legs_sheet)
    for im, gx, gy, title in legs_panels:
        legs_sheet.paste(im, (gx * pw, gy * ph))
        dr3.text((gx * pw + 8, gy * ph + 8), title, fill=(242, 236, 224))
    out_legs = PREVIEW_DIR / "cinder_dragon_armor_legs_boots_preview.png"
    legs_sheet.save(out_legs)
    print(f"LEGS_PREVIEW_OK: {out_legs}")

    # Bảng tổng hợp VẢY RỒNG 3D NỔI KHỐI & ĐỐI CHIẾU MÀU THÂN BOSS CINDER DRAGON!
    ctr_torso = np.array([0.0, 14.5, -1.5], dtype=np.float64)
    sc_front   = raster(cam_mat(180,   4), pw, ph,  9.2, ctr_full,  bg=(52, 56, 70))
    sc_close   = raster(cam_mat(180,   4), pw, ph, 21.5, ctr_torso, bg=(42, 44, 54))
    sc_34      = raster(cam_mat(142,   8), pw, ph, 19.5, ctr_torso, bg=(42, 44, 54))
    sc_back    = raster(cam_mat(  0,   4), pw, ph, 14.5, np.array([0.0, 15.5, +1.5]), bg=(42, 44, 54))
    sc_side    = raster(cam_mat( 90,   4), pw, ph, 16.5, np.array([0.0, 14.5,  0.0]), bg=(42, 44, 54))

    # Panel 6: Ghép crop vảy thân thật của Boss `cinder_dragon.png` (góc trên-trái) cạnh bản giáp 3/4 toàn thân để kiểm màu trực tiếp!
    sc_match = raster(cam_mat(154, 6), pw, ph, 10.2, ctr_full, bg=(42, 44, 54))
    boss_tex = ASSETS / "textures" / "entity" / "cinder_dragon.png"
    if boss_tex.exists():
        b_im = Image.open(boss_tex).convert("RGB").crop((0, 0, 128, 128)).resize((150, 150), Image.Resampling.NEAREST)
        sc_match.paste(b_im, (14, ph - 166))
        d_m = ImageDraw.Draw(sc_match)
        d_m.rectangle([13, ph - 167, 165, ph - 15], outline=(235, 190, 110), width=2)
        d_m.text((18, ph - 182), "BOSS SCALE REF (cinder_dragon.png)", fill=(255, 224, 112))

    scales_sheet = Image.new("RGB", (pw * 3, ph * 2), (16, 17, 23))
    scales_panels = [
        (sc_front, 0, 0, "1. FULL FRONT (BOSS VOLCANIC STONE PALETTE + 0 RED STRIPES)"),
        (sc_close, 1, 0, "2. TORSO/ARMS/LEGS FRONT CLOSE-UP (3D OVERLAPPING SCALES)"),
        (sc_34,    2, 0, "3. 3/4 DEPTH VIEW (RAISED LAYERED SCALES + OBSIDIAN CLAWS)"),
        (sc_back,  0, 1, "4. BACK SCALES + WINGS + TAIL (STAGGERED BACK & LEG SCALES)"),
        (sc_side,  1, 1, "5. SIDE PROFILE (ARM/LEG 3D SCALE LAYERING + SLANTED BAND)"),
        (sc_match, 2, 1, "6. DIRECT COLOR MATCH VS REAL BOSS TEXTURE (cinder_dragon.png)"),
    ]
    dr4 = ImageDraw.Draw(scales_sheet)
    for im, gx, gy, title in scales_panels:
        scales_sheet.paste(im, (gx * pw, gy * ph))
        dr4.text((gx * pw + 8, gy * ph + 8), title, fill=(242, 236, 224))
    out_scales = PREVIEW_DIR / "cinder_dragon_armor_scales_preview.png"
    scales_sheet.save(out_scales)
    print(f"SCALES_PREVIEW_OK: {out_scales}")

    # Bảng 4 góc kiểm chứng SỪNG VAI RỒNG (`pauldron_horn_r/l`) & GĂNG MÓNG VUỐT BÀN TAY / CỰA KHUỶU (`claw_gauntlet_r/l`)!
    ctr_upper = np.array([0.0, 19.5, -0.5], dtype=np.float64)
    hc_front = raster(cam_mat(180,  4), pw, ph, 16.5, ctr_upper, bg=(42, 44, 54))
    hc_side  = raster(cam_mat( 90,  4), pw, ph, 15.0, np.array([0.0, 19.5, +1.5]), bg=(42, 44, 54))
    hc_back  = raster(cam_mat(  0,  4), pw, ph, 13.5, np.array([0.0, 20.0, +2.0]), bg=(42, 44, 54))
    hc_34    = raster(cam_mat(144,  8), pw, ph, 11.2, np.array([0.0, 18.5, +1.0]), bg=(42, 44, 54))

    hc_sheet = Image.new("RGB", (pw * 2, ph * 2), (16, 17, 23))
    hc_panels = [
        (hc_front, 0, 0, "1. FRONT UPPER CLOSE-UP (PAULDRON HORNS + IVORY HAND CLAWS)"),
        (hc_side,  1, 0, "2. RIGHT SIDE PROFILE (HORN-WING CLEARANCE + ELBOW SPURS + CLAWS)"),
        (hc_back,  0, 1, "3. BACK UPPER VIEW (ZERO WING/HELMET COLLISION)"),
        (hc_34,    1, 1, "4. 3/4 HERO VIEW WITH STEVE (OPEN PALM FOR DUAL KNIFE + FULL SET)"),
    ]
    dr5 = ImageDraw.Draw(hc_sheet)
    for im, gx, gy, title in hc_panels:
        hc_sheet.paste(im, (gx * pw, gy * ph))
        dr5.text((gx * pw + 8, gy * ph + 8), title, fill=(242, 236, 224))
    out_hc = PREVIEW_DIR / "cinder_dragon_armor_horns_claws_preview.png"
    hc_sheet.save(out_hc)
    print(f"HORNS_CLAWS_PREVIEW_OK: {out_hc}")


if __name__ == "__main__":
    geo_data, cubes = build_cinder_dragon_armor_geo()
    build_cinder_dragon_armor_animations()
    diff_img, glow_img = build_cinder_dragon_armor_textures()
    update_helmet_item_icon()
    render_multi_angle_preview(geo_data, diff_img, glow_img)
    print(f"ALL_DONE: Cinder Dragon Armor v2 (Boss Dragon Head + 3-Joint Bat Wings + Crescent Pincer Tail, {cubes} cubes)")
