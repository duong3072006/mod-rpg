#!/usr/bin/env python3
"""
Bộ sinh 1:1 Model 3D & Texture **Nightmare Knight Armor** (`ank.txt` — bám sát 100% 6 ảnh mẫu, CHỈ đổi bảng màu sang Nightmare Knight):
  - `assets/abyssalrpg/geo/armor/nightmare_armor.geo.json`:
    + Đúng 8 xương chuẩn GeckoLib (`armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`,
      `armorRightLeg`, `armorLeftLeg`, `armorRightBoot`, `armorLeftBoot`) + 3 đốt áo choàng mỏng
      (`cape_upper`, `cape_mid`, `cape_lower`) dưới `armorBody`.
    + XÓA SẠCH mọi chi tiết thừa không có trong 6 ảnh mẫu (0 mắt đỏ to, 0 chấm đèn tròn vai/ngực,
      0 khối kim cương lồi ngực, 0 3 thanh xương sườn hộp lồi, 0 khố vải dài giữa đùi, 0 khe sáng dọc chân).
    + Đúng 100% hình khối 6 ảnh mẫu: mũ hộp kín có 2 nẹp dọc trán + tấm mũi tam giác gọn + mào sống ngắn + chốt gáy + 2 tai gáy nhỏ
      + cặp sừng cong vươn lên rồi uốn mượt dài về sau chẻ 3 nhánh gai; vai phải vát xiên nhiều mảnh nối gốc áo choàng;
      vai trái gọn vát chéo; hở bắp tay + găng tay vát chéo lên cao mép ngoài / hạ thấp mép trong;
      thân giáp ôm gọn họa tiết chữ V + lõi thoi phẳng + bụng giáp đối xứng; áo choàng 1 bên siêu mỏng gập 3 đoạn mép rách bậc;
      quần đùi tối có vạt hông ngắn & tấm giáp gối nổi; giày giáp dày có cổ giày nổi.
  - `assets/abyssalrpg/textures/armor/nightmare_armor.png` & `nightmare_armor_glowmask.png` (`128x128` RGBA nearest):
    + Ánh xạ màu 1:1 theo bảng `ank.txt` §3:
      * Mảng ĐỎ mẫu -> Thép đen obsidian ánh kim (`#12141A -> #20242E -> #343A4A`, highlight `#586278`).
      * Cặp sừng cong -> Đỏ huyết sẫm (`#4A0E16 -> #821B24 -> #B52D36`, 0 glow).
      * Áo choàng mỏng rách -> Đỏ máu / đỏ rượu sẫm (`#480A12 -> #7C1620 -> #A8242E`, 0 glow).
      * Toàn bộ mảng XÁM BẠC mẫu -> Xám thép / xương tro ánh kim (`#555B66 -> #848B96 -> #B8BFC9`, 0 glow).
      * Đùi tối -> Đen than sẫm (`#141316 -> #242228`).
      * Glowmask chọn lọc: CHỈ phát sáng rất nhẹ đúng lõi thoi phẳng giữa ngực + 2 chấm cực mảnh ngay dưới 2 nẹp trán (`#A81822 -> #E02832`).
  - Xuất ảnh preview 3D đủ 6 góc trùng khớp 6 ảnh mẫu (Trước, Sau, Phải, Trái, Trên, Dưới) + Bản Đêm Glowmask + 6 Icon.

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

# Bản đồ UV 128x128 theo từng mặt riêng biệt để giữ 100% bố cục hoa văn pixel của 6 ảnh mẫu!
REG = {
    # Hàng 0 (y=0..16): 4 mặt mũ + sừng + xám thép + đen obsidian + đùi tối
    "HELM_FRONT":    (0,   0, 16, 16),
    "HELM_BACK":     (16,  0, 16, 16),
    "HELM_SIDE":     (32,  0, 16, 16),
    "HELM_TOP":      (48,  0, 16, 16),
    "HORN_BASE":     (64,  0, 16, 16),
    "HORN_TIP":      (80,  0, 16, 16),
    "ASH_STEEL":     (96,  0, 16, 16),
    "OBS_DARK":      (112, 0, 16, 16),

    # Hàng 1..2 (y=16..40): Mặt thân giáp (16x24 chuẩn tỉ lệ 8x12!) + vai + găng + áo choàng
    "CHEST_FRONT":   (0,  16, 16, 24),
    "CHEST_BACK":    (16, 16, 16, 24),
    "CHEST_SIDE":    (32, 16,  8, 24),
    "OBS_PLATE":     (40, 16, 16, 16),
    "PAULDRON_L":    (56, 16, 16, 16),
    "PAULDRON_R":    (72, 16, 16, 16),
    "GAUNTLET_OUT":  (88, 16, 16, 16),
    "GAUNTLET_SIDE": (104,16, 16, 16),

    # Hàng 3 (y=40..56): Áo choàng 3 đoạn (rách bậc ở đoạn cuối!) + đùi + gối + giày
    "CAPE_UPPER":    (0,  40, 16, 16),
    "CAPE_MID":      (16, 40, 16, 16),
    "CAPE_LOWER":    (32, 40, 16, 16),
    "THIGH_DARK":    (48, 40, 16, 16),
    "THIGH_SIDE":    (64, 40, 16, 16),
    "KNEE_PLATE":    (80, 40, 16, 16),
    "TASSET_PLATE":  (96, 40, 16, 16),
    "BOOT_COLLAR":   (112,40, 16, 16),

    # Hàng 4 (y=56..72): 4 mặt giày + đế giày + mặt dưới mũ + cổ vai + khóa gáy
    "BOOT_FRONT":    (0,  56, 16, 16),
    "BOOT_SIDE":     (16, 56, 16, 16),
    "BOOT_BACK":     (32, 56, 16, 16),
    "BOOT_SOLE":     (48, 56, 16, 16),
    "ASH_DARK":      (64, 56, 16, 16),
    "ASH_BRIGHT":    (80, 56, 16, 16),
    "NOSE_GUARD":    (96, 56, 16, 16),
    "GAUNTLET_WRIST":(112,56, 16, 16),
}


def uv_rect(key: str, pad: float = 0.0) -> dict:
    x0, y0, w, h = REG[key]
    return {"uv": [x0 + pad, y0 + pad], "uv_size": [w - 2 * pad, h - 2 * pad]}


def uv_box(mat_key: str, face_mats=None, pad: float = 0.0) -> dict:
    base = uv_rect(mat_key, pad)
    uvs = {"north": base, "south": base, "east": base, "west": base, "up": base, "down": base}
    if face_mats:
        for fn, mk in face_mats.items():
            uvs[fn] = uv_rect(mk, pad)
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
    return make_cube([cx - sx * 0.5, cy - sy * 0.5, cz - sz * 0.5], [sx, sy, sz], mat, rot=rot, pivot=[cx, cy, cz], face_mats=face_mats)


def build_nightmare_armor_geo():
    """
    Dựng 1:1 hình khối 3D của 6 ảnh mẫu (không thêm chi tiết tự bịa!):
      - Y=0: đế giày | Y=12: hông | Y=22: vai | Y=24: cổ | Y=32: đỉnh đầu.
      - -Z = TRƯỚC | +Z = SAU | -X = PHẢI người chơi (vai phải lớn + áo choàng) | +X = TRÁI người chơi.
    """
    # ==================== 1. MŨ (`armorHead`, pivot [0, 24, 0]) ====================
    head_cubes = []
    # Khối mũ hộp kín hoàn toàn mặt trước (9.0 x 9.0 x 9.0, 0 hốc mặt hở, 0 mắt đỏ to!)
    head_cubes.append(make_centered_cube(
        [0.0, 28.4, 0.0], [9.0, 9.0, 9.0], "OBS_PLATE",
        face_mats={
            "north": "HELM_FRONT",
            "south": "HELM_BACK",
            "east":  "HELM_SIDE",
            "west":  "HELM_SIDE",
            "up":    "HELM_TOP",
            "down":  "OBS_DARK",
        }
    ))
    # 2 nẹp dọc mảnh song song phía trên trán (kéo từ mép đỉnh mũ xuống 3.2px, nhô nhẹ 0.30px giống mẫu)
    head_cubes.append(make_centered_cube([-1.65, 31.1, -4.58], [0.65, 3.2, 0.36], "ASH_BRIGHT"))
    head_cubes.append(make_centered_cube([+1.65, 31.1, -4.58], [0.65, 3.2, 0.36], "ASH_BRIGHT"))
    # Tấm bảo vệ mũi/cằm hình tam giác nhọn gọn chính giữa dưới (ôm sát 0.40px, tỉ lệ đúng ảnh mẫu)
    head_cubes.append(make_centered_cube([0.0, 26.8, -4.64], [2.9, 1.45, 0.45], "NOSE_GUARD"))
    head_cubes.append(make_centered_cube([0.0, 25.65, -4.68], [1.85, 1.05, 0.48], "NOSE_GUARD"))
    head_cubes.append(make_centered_cube([0.0, 24.85, -4.62], [0.90, 0.65, 0.40], "ASH_DARK"))
    # 1 mào gai thẳng hẹp chạy ở phần sau-đỉnh mũ (khớp 100% góc trên & góc sau mẫu!)
    head_cubes.append(make_centered_cube([0.0, 33.4, +2.65], [1.25, 1.45, 3.2], "ASH_STEEL", rot=[+10.0, 0.0, 0.0]))
    # 1 chốt giáp dọc nhỏ phía sau gáy + viền mép cổ dưới (khớp góc sau mẫu!)
    head_cubes.append(make_centered_cube([0.0, 26.7, +4.75], [1.45, 2.6, 0.85], "ASH_STEEL", rot=[+8.0, 0.0, 0.0]))
    head_cubes.append(make_centered_cube([0.0, 24.3, +4.45], [9.2, 1.1, 0.85], "OBS_DARK"))
    # 2 chốt tai gáy nhỏ cong ngược ở góc sau dưới hai bên mũ (nhìn cực rõ ở góc nghiêng trái/phải & trên mẫu!)
    for s in (-1.0, 1.0):
        head_cubes.append(make_centered_cube([s * 4.55, 27.1, +4.75], [0.95, 1.0, 1.85], "ASH_DARK", rot=[-16.0, s * 10.0, 0.0]))
        head_cubes.append(make_centered_cube([s * 4.65, 26.3, +5.55], [0.85, 1.15, 0.95], "ASH_STEEL", rot=[-45.0, s * 10.0, 0.0]))

    # CẶP SỪNG CONG ĐỎ HUYẾT (`horn_l`, `horn_r`): vòm liền mạch mượt mà từ thái dương trước vươn chéo lên-sau
    # song song 2 bên mép mũ (dày chắc ~2.1px giống góc trên & trước mẫu!), đuôi phía sau chẻ 3 nhánh gai nhỏ!
    def build_horn_cubes(s: float):
        hc = []
        # Gốc sừng bám sát hai bên thái dương trước (`x = ±5.1`, `y = 29.3`, `z = -3.0`)
        hc.append(make_centered_cube([s * 5.20, 29.4, -2.9], [2.05, 2.25, 2.35], "HORN_BASE", rot=[+32.0, 0.0, s * -6.0]))
        # Đốt 1: vươn mượt lên cao & lùi về phía trên-trước (khớp 2 khối sừng dày vuông góc trên mẫu!)
        hc.append(make_centered_cube([s * 5.65, 31.4, -1.7], [2.15, 2.35, 3.00], "HORN_BASE", rot=[+36.0, 0.0, s * -4.0]))
        # Đốt 2: đỉnh vòm mượt chạy song song 2 bên đỉnh mũ về phía sau
        hc.append(make_centered_cube([s * 5.60, 33.1, +0.4], [1.95, 2.15, 3.25], "HORN_BASE", rot=[+14.0, 0.0, 0.0]))
        # Đốt 3: vòng cong mượt xuống nhẹ phía sau đầu
        hc.append(make_centered_cube([s * 5.40, 33.1, +2.8], [1.65, 1.85, 2.95], "HORN_TIP", rot=[-16.0, s * 4.0, 0.0]))
        # Đốt 4 + 3 nhánh gai chẻ phía sau gáy (giống hệt góc nghiêng trái & phải mẫu!)
        hc.append(make_centered_cube([s * 5.25, 32.3, +4.6], [1.25, 1.35, 2.20], "HORN_TIP", rot=[-28.0, s * 6.0, 0.0]))
        # Nhánh gai 1 (trên-sau)
        hc.append(make_centered_cube([s * 5.20, 33.2, +5.8], [0.85, 0.85, 1.95], "HORN_TIP", rot=[+8.0, s * 8.0, 0.0]))
        # Nhánh gai 2 (giữa-sau uốn xuống)
        hc.append(make_centered_cube([s * 5.15, 31.7, +6.0], [0.85, 0.90, 2.00], "HORN_TIP", rot=[-34.0, s * 8.0, 0.0]))
        # Nhánh gai 3 (nhánh dưới phía sau gốc sừng)
        hc.append(make_centered_cube([s * 5.35, 30.8, +4.1], [0.95, 0.95, 2.15], "HORN_BASE", rot=[-38.0, s * 10.0, 0.0]))
        return hc

    horn_r_cubes = build_horn_cubes(-1.0)
    horn_l_cubes = build_horn_cubes(+1.0)

    # ==================== 2. THÂN GIÁP (`armorBody`, pivot [0, 24, 0]) ====================
    # Hộp giáp thân ôm vừa vặn kiểu Minecraft armor (8.8 x 12.2 x 4.9 -> 0 đắp khối lồi thô, 0 chấm đèn, 0 khố trước!)
    body_cubes = [
        make_centered_cube(
            [0.0, 18.0, 0.0], [8.8, 12.2, 4.9], "OBS_PLATE",
            face_mats={
                "north": "CHEST_FRONT",
                "south": "CHEST_BACK",
                "east":  "CHEST_SIDE",
                "west":  "CHEST_SIDE",
                "up":    "OBS_DARK",
                "down":  "OBS_DARK",
            }
        ),
        # Gờ mảnh chữ V cổ áo ôm cực sát (nổi tinh tế 0.25px giống góc trước mẫu)
        make_centered_cube([-2.3, 22.8, -2.52], [2.6, 1.1, 0.30], "ASH_STEEL", rot=[0.0, 0.0, +20.0]),
        make_centered_cube([+2.3, 22.8, -2.52], [2.6, 1.1, 0.30], "ASH_STEEL", rot=[0.0, 0.0, -20.0]),
        # Gờ chốt nối nhẹ giữa vai phải & gốc áo choàng phía sau lưng
        make_centered_cube([-3.1, 22.6, +2.55], [3.6, 1.4, 0.45], "ASH_DARK", rot=[0.0, 0.0, +10.0]),
    ]

    # ==================== 2b. ÁO CHOÀNG ĐỎ MỘT BÊN SIÊU MỎNG (`cape_upper/mid/lower` dưới `armorBody`) ====================
    # Dải vải mỏng (dày 0.22px kiểu plane), nối khít tại các khớp gập (0 khe hở!), gập 3 đoạn uốn ra sau lưng giống hệt mẫu!
    cape_upper_cubes = [
        make_centered_cube([-3.3, 18.1, +3.75], [4.6, 8.5, 0.22], "CAPE_UPPER", rot=[+17.0, +3.0, +2.0]),
    ]
    cape_mid_cubes = [
        make_centered_cube([-3.4, 10.4, +5.20], [4.8, 8.1, 0.22], "CAPE_MID", rot=[+4.0, +2.0, +2.0]),
    ]
    cape_lower_cubes = [
        make_centered_cube([-3.5, 4.1, +7.50], [5.0, 6.9, 0.22], "CAPE_LOWER", rot=[+39.0, +2.0, +1.0]),
    ]

    # ==================== 3. VAI PHẢI LỚN VÁT XIÊN & GĂNG TAY VÁT CHÉO (`armorRightArm`, [-5, 22, 0]) ====================
    rarm_cubes = []
    # Lót vai phải ôm bắp tay trên (thép đen + viền cung xám thép phía dưới giống góc nghiêng phải!)
    rarm_cubes.append(make_centered_cube(
        [-6.0, 21.8, 0.0], [4.8, 4.2, 4.8], "OBS_PLATE",
        face_mats={"west": "PAULDRON_L", "north": "OBS_PLATE", "south": "OBS_PLATE"}
    ))
    # Cụm giáp vai phải lớn nhiều mảnh vát xiên ôm gọn sắc cạnh màu XÁM THÉP (KHÔNG chấm đèn đỏ, KHÔNG thừa rộng!)
    rarm_cubes.append(make_centered_cube([-6.3, 24.35, 0.0], [4.9, 1.50, 5.3], "PAULDRON_R", rot=[0.0, 0.0, -22.0]))
    rarm_cubes.append(make_centered_cube([-7.25, 22.85, 0.0], [3.3, 1.60, 5.7], "PAULDRON_R", rot=[0.0, 0.0, -32.0]))
    rarm_cubes.append(make_centered_cube([-6.8, 22.7, -2.48], [3.5, 2.2, 1.05], "ASH_STEEL", rot=[-18.0, +16.0, -22.0]))
    rarm_cubes.append(make_centered_cube([-6.8, 22.7, +2.48], [3.5, 2.2, 1.05], "ASH_STEEL", rot=[+18.0, -16.0, -22.0]))
    rarm_cubes.append(make_centered_cube([-8.10, 21.65, 0.0], [1.65, 2.15, 3.0], "ASH_BRIGHT", rot=[0.0, 0.0, +22.0]))
    rarm_cubes.append(make_centered_cube([-6.0, 23.0, +2.65], [4.2, 2.3, 0.95], "ASH_DARK", rot=[+14.0, 0.0, -10.0]))

    # KHOẢNG HỞ BẮP TAY RÕ RÀNG (y = 16.9 .. 19.7)
    # GĂNG GIÁP CẲNG TAY PHẢI VÁT CHÉO (CAO Ở MÉP NGOÀI -X, HẠ THẤP Ở MÉP TRONG -X GẦN THÂN -> DÁNG CHỮ V CHÉO CHUẨN MẪU!)
    def build_slanted_gauntlet(side_sign: float):
        cx = side_sign * 6.0
        outer_face = "west" if side_sign < 0 else "east"
        inner_face = "east" if side_sign < 0 else "west"
        gc = []
        gc.append(make_centered_cube(
            [cx, 13.0, 0.0], [4.9, 2.4, 4.9], "GAUNTLET_WRIST",
            face_mats={outer_face: "GAUNTLET_OUT", inner_face: "OBS_DARK", "down": "OBS_DARK"}
        ))
        gc.append(make_centered_cube(
            [cx + side_sign * 0.80, 14.9, 0.0], [3.35, 1.5, 4.92], "GAUNTLET_SIDE",
            face_mats={outer_face: "GAUNTLET_OUT"}
        ))
        gc.append(make_centered_cube(
            [cx + side_sign * 1.65, 16.25, 0.0], [1.65, 1.35, 4.94], "OBS_PLATE",
            face_mats={outer_face: "GAUNTLET_OUT", "up": "ASH_DARK"}
        ))
        gc.append(make_centered_cube(
            [cx + side_sign * 2.52, 14.4, 0.0], [0.22, 4.9, 4.5], "GAUNTLET_OUT"
        ))
        return gc

    rarm_cubes.extend(build_slanted_gauntlet(-1.0))

    # ==================== 4. VAI TRÁI GỌN SẮC & GĂNG TAY VÁT CHÉO (`armorLeftArm`, [+5, 22, 0]) ====================
    larm_cubes = []
    larm_cubes.append(make_centered_cube(
        [+6.05, 22.1, 0.0], [4.9, 3.8, 4.9], "OBS_PLATE",
        face_mats={"east": "PAULDRON_L", "north": "PAULDRON_L", "south": "PAULDRON_L", "up": "OBS_DARK"}
    ))
    larm_cubes.append(make_centered_cube([+6.4, 24.05, 0.0], [4.6, 0.85, 5.1], "OBS_PLATE", rot=[0.0, 0.0, +16.0]))
    larm_cubes.extend(build_slanted_gauntlet(+1.0))

    # ==================== 5. QUẦN (`armorRightLeg` [-2,12,0] & `armorLeftLeg` [+2,12,0]) ====================
    def build_leg_cubes(side_sign: float):
        cx = side_sign * 2.0
        outer_face = "west" if side_sign < 0 else "east"
        lc = []
        lc.append(make_centered_cube(
            [cx, 8.4, 0.0], [4.55, 7.5, 4.55], "THIGH_DARK",
            face_mats={outer_face: "THIGH_SIDE"}
        ))
        lc.append(make_centered_cube([cx, 11.1, 0.0], [4.85, 2.45, 4.85], "TASSET_PLATE"))
        lc.append(make_centered_cube([cx + side_sign * 2.42, 10.6, 0.0], [0.55, 2.6, 4.7], "TASSET_PLATE", rot=[0.0, 0.0, side_sign * -10.0]))
        # Tấm giáp đầu gối nổi sắc gọn màu XÁM THÉP ở chính giữa đầu gối (`y = 4.9 .. 8.1`, 0 đèn đỏ!)
        lc.append(make_centered_cube([cx, 6.50, -2.42], [3.9, 3.15, 0.52], "KNEE_PLATE"))
        return lc

    rleg_cubes = build_leg_cubes(-1.0)
    lleg_cubes = build_leg_cubes(+1.0)

    # ==================== 6. GIÀY GIÁP DÀY (`armorRightBoot` [-2,12,0] & `armorLeftBoot` [+2,12,0]) ====================
    def build_boot_cubes(side_sign: float):
        cx = side_sign * 2.0
        outer_face = "west" if side_sign < 0 else "east"
        inner_face = "east" if side_sign < 0 else "west"
        bc = []
        # Thân giày giáp dày chắc (5.05 x 4.6 x 5.05, họa tiết tấm giáp mặt trước-mặt bên, 0 khe sáng dọc!)
        bc.append(make_centered_cube(
            [cx, 2.30, 0.0], [5.05, 4.6, 5.05], "BOOT_SIDE",
            face_mats={
                "north": "BOOT_FRONT",
                "south": "BOOT_BACK",
                outer_face: "BOOT_SIDE",
                inner_face: "BOOT_BACK",
                "down": "BOOT_SOLE",
                "up": "OBS_DARK",
            }
        ))
        # Viền cổ giày nổi màu XÁM THÉP bọc quanh cổ chân (`y = 4.35 .. 5.25`)
        bc.append(make_centered_cube([cx, 4.75, 0.0], [5.30, 0.95, 5.30], "BOOT_COLLAR"))
        return bc

    rboot_cubes = build_boot_cubes(-1.0)
    lboot_cubes = build_boot_cubes(+1.0)

    bones = [
        {"name": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},
        {"name": "horn_r",    "parent": "armorHead", "pivot": [-4.8, 30.0, -2.0], "cubes": horn_r_cubes},
        {"name": "horn_l",    "parent": "armorHead", "pivot": [+4.8, 30.0, -2.0], "cubes": horn_l_cubes},

        {"name": "bipedBody", "pivot": [0, 24, 0]},
        {"name": "armorBody", "parent": "bipedBody", "pivot": [0, 24, 0], "cubes": body_cubes},
        {"name": "cape_upper", "parent": "armorBody", "pivot": [-3.3, 22.0, 2.55], "cubes": cape_upper_cubes},
        {"name": "cape_mid",   "parent": "cape_upper", "pivot": [-3.4, 14.2, 5.00], "cubes": cape_mid_cubes},
        {"name": "cape_lower", "parent": "cape_mid",   "pivot": [-3.5, 6.6,  5.35], "cubes": cape_lower_cubes},

        {"name": "bipedRightArm", "pivot": [-5, 22, 0]},
        {"name": "armorRightArm", "parent": "bipedRightArm", "pivot": [-5, 22, 0], "cubes": rarm_cubes},

        {"name": "bipedLeftArm", "pivot": [5, 22, 0]},
        {"name": "armorLeftArm", "parent": "bipedLeftArm", "pivot": [5, 22, 0], "cubes": larm_cubes},

        {"name": "bipedRightLeg", "pivot": [-2, 12, 0]},
        {"name": "armorRightLeg", "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rleg_cubes},
        {"name": "armorRightBoot", "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rboot_cubes},

        {"name": "bipedLeftLeg", "pivot": [2, 12, 0]},
        {"name": "armorLeftLeg", "parent": "bipedLeftLeg", "pivot": [2, 12, 0], "cubes": lleg_cubes},
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
                    "visible_bounds_width": 3.0,
                    "visible_bounds_height": 3.5,
                    "visible_bounds_offset": [0.0, 1.25, 0.0],
                },
                "bones": bones,
            }
        ],
    }
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out = ASSETS / "geo" / "armor" / "nightmare_armor.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, 128x128 per-face UV)")
    return geo, total_cubes


def build_nightmare_armor_animations():
    anims = {
        "animation.nightmare_armor.idle": {
            "loop": True,
            "animation_length": 3.2,
            "bones": {
                "cape_upper": {
                    "rotation": {
                        "0.0": [0.0, 0.0, 0.0],
                        "1.6": [2.0, 0.8, 0.5],
                        "3.2": [0.0, 0.0, 0.0],
                    }
                },
                "cape_mid": {
                    "rotation": {
                        "0.0": [0.0, 0.0, 0.0],
                        "1.6": [3.0, -0.8, 0.8],
                        "3.2": [0.0, 0.0, 0.0],
                    }
                },
                "cape_lower": {
                    "rotation": {
                        "0.0": [0.0, 0.0, 0.0],
                        "1.6": [4.5, 1.0, -0.8],
                        "3.2": [0.0, 0.0, 0.0],
                    }
                },
            },
        }
    }
    out = ASSETS / "animations" / "armor" / "nightmare_armor.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out}")


def build_nightmare_armor_textures_128x128():
    """
    Vẽ từng vùng mặt 1:1 theo đúng bố cục hoa văn pixel của 6 ảnh mẫu,
    CHỈ ánh xạ bảng màu sang Nightmare Knight (`ank.txt` §3):
      - Thép đen obsidian ánh kim: #12141A -> #20242E -> #343A4A -> #586278
      - Sừng đỏ huyết sẫm:         #4A0E16 -> #821B24 -> #B52D36
      - Áo choàng đỏ máu sẫm:      #480A12 -> #7C1620 -> #A8242E
      - Xám thép / xương tro:      #555B66 -> #848B96 -> #B8BFC9
      - Đùi đen than sẫm:          #141316 -> #242228
      - Glowmask tinh tế:          CHỈ lõi thoi phẳng giữa ngực + 2 chấm cực mảnh dưới 2 nẹp trán (#A81822 -> #E02832)!
    """
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    # Bảng màu chuẩn pixel-art 1:1 theo `ank.txt` §3
    P = {
        ".": (0, 0, 0, 0),            # Trong suốt (mép áo choàng rách bậc!)
        "0": (14, 16, 22, 255),       # Khe rãnh đen sâu
        "1": (18, 20, 26, 255),       # #12141A obsidian sâu
        "2": (32, 36, 46, 255),       # #20242E obsidian trung
        "3": (52, 58, 74, 255),       # #343A4A obsidian sáng
        "4": (88, 98, 120, 255),      # #586278 điểm nhấn cạnh thép đen

        "a": (62, 67, 76, 255),       # xám thép rãnh
        "s": (85, 91, 102, 255),      # #555B66 xám thép tối
        "S": (132, 139, 150, 255),    # #848B96 xám thép trung
        "W": (184, 191, 201, 255),    # #B8BFC9 xám thép sáng

        "h": (74, 14, 22, 255),       # #4A0E16 sừng đỏ huyết sâu
        "H": (130, 27, 36, 255),      # #821B24 sừng đỏ huyết trung
        "R": (181, 45, 54, 255),      # #B52D36 sừng đỏ huyết sáng

        "c": (72, 10, 18, 255),       # #480A12 áo choàng đỏ máu tối
        "C": (124, 22, 32, 255),      # #7C1620 áo choàng đỏ máu trung
        "M": (168, 36, 46, 255),      # #A8242E áo choàng đỏ máu sáng

        "u": (20, 19, 22, 255),       # #141316 đùi đen than sâu
        "U": (36, 34, 40, 255),       # #242228 đùi đen than sáng

        "g": (168, 24, 34, 255),      # #A81822 đỏ thẫm phát sáng rất nhẹ (chữ X đỉnh / rìa lõi)
        "G": (224, 40, 50, 255),      # #E02832 tâm lõi thoi & 2 chấm mảnh dưới nẹp trán
    }

    def paint_ascii(key: str, rows, glow_chars=""):
        x0, y0, w, h = REG[key]
        assert len(rows) == h, f"{key}: expected {h} rows, got {len(rows)}"
        for y, row in enumerate(rows):
            assert len(row) == w, f"{key} row {y}: expected {w} chars, got {len(row)}"
            for x, ch in enumerate(row):
                rgba = P[ch]
                diff[y0 + y, x0 + x] = rgba
                if ch in glow_chars:
                    # Phát sáng tinh tế vừa phải (không chói neon!)
                    ga = 215 if ch == "G" else 140
                    glow[y0 + y, x0 + x] = (rgba[0], rgba[1], rgba[2], ga)

    # 1. HELM_FRONT (16x16) — Mũ kín mặt trước: 2 nẹp dọc mảnh + 2 chấm đỏ cực mảnh dưới nẹp + tấm mũi tam giác gọn!
    paint_ascii("HELM_FRONT", [
        "1233321111233321",
        "2343222332223432",
        "233S32233223S332",
        "232W22233222W232",
        "232W22344322W232",
        "222S22333322S222",
        "222G22233222G222",
        "1111122222211111",
        "122211sSSs112221",
        "12321sSWWSs12321",
        "12321sSWWSs12321",
        "122211sWWs112221",
        "1122111SS1112211",
        "0112211ss1122110",
        "0011111001111100",
        "0001111001111000",
    ], glow_chars="G")

    # 2. HELM_TOP (16x16) — Đỉnh mũ obsidian + họa tiết chữ X đỏ thẫm mờ + đầu 2 nẹp trán phía trước (đáy = north)!
    paint_ascii("HELM_TOP", [
        "1122222112222211",
        "1233322112233321",
        "123g32222223g321",
        "2332g322223g2332",
        "23222g3223g22232",
        "232222g33g222232",
        "2221222gg2221222",
        "221112g22g211122",
        "22212g2112g21222",
        "2322g221122g2232",
        "232g32222223g232",
        "2332233223322332",
        "232S23322332S232",
        "232W22333322W232",
        "232W22233222W232",
        "122S22222222S221",
    ], glow_chars="")

    # 3. HELM_BACK (16x16) — Mặt sau mũ thép đen phân mảng + 2 rãnh dọc tối + chốt gáy gọn
    paint_ascii("HELM_BACK", [
        "1122332222332211",
        "1233332112333321",
        "2343222222223432",
        "2332222332222332",
        "2322212332122232",
        "2322212332122232",
        "2222212222122222",
        "2221222222221222",
        "122122sSSs221221",
        "122222sWWs222221",
        "123222sSSs222321",
        "1222211ss1122221",
        "1122211001122211",
        "0112221111222110",
        "0011222222221100",
        "0001111111111000",
    ])

    # 4. HELM_SIDE (16x16) — Mặt bên mũ phân lớp chéo sắc cạnh (khớp góc trái/phải mẫu!)
    paint_ascii("HELM_SIDE", [
        "1122233333222111",
        "1233334433332211",
        "2332223332223321",
        "2322112332112321",
        "2221122332211221",
        "2211223333221121",
        "1212233223322121",
        "1122332112332211",
        "1223321111233221",
        "1233221221223321",
        "1232212332122321",
        "1222112332112221",
        "0122111221112210",
        "0112211111122110",
        "0011222222221100",
        "0001111111111000",
    ])

    # 5. CHEST_FRONT (16x24) — Giáp ngực chữ V xám thép + LÕI THOI PHẲNG ĐỎ THẪM + mảng bụng xám thép đối xứng!
    paint_ascii("CHEST_FRONT", [
        "1123sS2112Ss3211",
        "123sSWs22sWSs321",
        "1223sSWs2sWSs321",
        "12322sSWsWSs2321",
        "234322sSWSs23432",
        "2333222sSs223332",
        "2332221gGg122332",
        "232221gGGGg12232",
        "232211gGGGg11232",
        "222221gGGGg12222",
        "2223221gGg123222",
        "12332221g1223321",
        "12322sSSsSSs2321",
        "1222sSWWSSWWs221",
        "1221sS1SWWS1Ss21",
        "1221sS1WWWW1Ss21",
        "1222sS1SWWS1Ss21",
        "1221sS1WWWW1Ss21",
        "1221sS1SWWS1Ss21",
        "12222sSSWWSSs221",
        "1123221sSSs12321",
        "1123322111123321",
        "0112333222333210",
        "0011222222221100",
    ], glow_chars="gG")

    # 6. CHEST_BACK (16x24) — Giáp lưng obsidian + dải vai V + tấm khiên lưng xám thép ở giữa (khớp ảnh góc sau!)
    paint_ascii("CHEST_BACK", [
        "1123sS2112Ss3211",
        "1233sSs22sSs3321",
        "23432sSs2sSs3432",
        "233222sSWSs22332",
        "2332221sSs122332",
        "2322212sSs212232",
        "2222112sSs211222",
        "222112sSWWs21122",
        "12212sSWWWWs2121",
        "12222sSWWWWs2221",
        "12321sSWWWWs1231",
        "12321sSWWWWs1231",
        "12321sSWWWWs1231",
        "12221sSWWWWs1221",
        "12222sSWWWWs2221",
        "122221sSWWs12221",
        "122221sSWWs12221",
        "1122211sSSs12211",
        "11232211ss122311",
        "1123322111123311",
        "1123322222233211",
        "0112333222332110",
        "0011222222221100",
        "0001111111111000",
    ])

    # 7. CHEST_SIDE (8x24) — Hông sườn giáp phân lớp ngang sắc nét
    paint_ascii("CHEST_SIDE", [
        "12333321",
        "12344321",
        "12233221",
        "01122110",
        "12233221",
        "12344321",
        "12233221",
        "01122110",
        "12233221",
        "12344321",
        "12233221",
        "01122110",
        "12233221",
        "12333321",
        "12233221",
        "01122110",
        "12233221",
        "12333321",
        "12233221",
        "01122110",
        "12233221",
        "12333321",
        "01222210",
        "00111100",
    ])

    # 8. PAULDRON_L (16x16) — Giáp vai trái gọn sắc có viền cung chữ V màu xám thép (khớp góc trái mẫu!)
    paint_ascii("PAULDRON_L", [
        "1122334444332211",
        "1233344334433321",
        "2343222332223432",
        "2332212332122332",
        "2322112332112232",
        "1221122332211221",
        "1211223333221121",
        "11s1233223321s11",
        "1sSs12211221sSs1",
        "1sSWs111111sWSs1",
        "01sSWs1111sWSs10",
        "001sSWssSsWSs100",
        "0001sSWWWWSs1000",
        "00001sSSSSs10000",
        "000001ssss100000",
        "0000001111000000",
    ])

    # 9. GAUNTLET_OUT (16x16) — Mặt ngoài găng tay: trên thép đen có 2 tai mép + dưới cổ tay xám thép có 2 rãnh dọc (khớp góc trái/phải!)
    paint_ascii("GAUNTLET_OUT", [
        "1232100000012321",
        "1233211111123321",
        "1234322332234321",
        "1233322332233321",
        "1223222222223221",
        "1122211221122211",
        "0111111111111110",
        "01ssssSSSSssss10",
        "1sSWWWSssSWWWSs1",
        "1sSWWWSaaSWWWSs1",
        "1sSWWWSaaSWWWSs1",
        "1sSWWWSssSWWWSs1",
        "1sSSWWSaaSWWSSs1",
        "11sSSWSaaSWSSs11",
        "011sssSssSsss110",
        "0011111111111100",
    ])

    # 10. GAUNTLET_SIDE / WRIST (16x16)
    paint_ascii("GAUNTLET_SIDE", [
        "1122333333332211",
        "1234433333344321",
        "1233322222233321",
        "1233222332223321",
        "1222212332122221",
        "1222112222112221",
        "1122111221112211",
        "0111111111111110",
        "1223322222233221",
        "1233322332233321",
        "1232222332222321",
        "1222212222122221",
        "1122111221112211",
        "0112211111122110",
        "0011222222221100",
        "0001111111111000",
    ])
    paint_ascii("GAUNTLET_WRIST", [
        "1122333333332211",
        "1233322222233321",
        "1222211221122221",
        "0111111111111110",
        "1sSSSSWWWWSSSSs1",
        "1sSWWWsaaSWWWSs1",
        "1sSWWWsaaSWWWSs1",
        "1sSWWWsaaSWWWSs1",
        "1sSWWWsaaSWWWSs1",
        "1sSSWWsaaSWWSSs1",
        "1sSSWWsaaSWWSSs1",
        "1sSSSSsaaSSSSSs1",
        "11sssssaaassss11",
        "0111111111111110",
        "0011222222221100",
        "0001111111111000",
    ])

    # 11. KNEE_PLATE (16x16) — Tấm giáp gối nổi màu XÁM THÉP sắc cạnh, KHÔNG đèn đỏ!
    paint_ascii("KNEE_PLATE", [
        "0011ssssssss1100",
        "01sSSWWWWWWSSs10",
        "1sSWWWWWWWWWWSs1",
        "1sSWWSSWWSSWWSs1",
        "1sSWWSaWWaSWWSs1",
        "1sSWWSSWWSSWWSs1",
        "1sSWWWWWWWWWWSs1",
        "1sSSWWWWWWWWSSs1",
        "1sSSWWSaaSWWSSs1",
        "1sSSWWSaaSWWSSs1",
        "1sSSWWWWWWWWSSs1",
        "1sSSSSWWWWSSSSs1",
        "11sSSSSSSSSSSs11",
        "011ssssssssss110",
        "0011111111111100",
        "0000111111110000",
    ])

    # 12. TASSET_PLATE (16x16) — Đai & vạt giáp hông ngắn ôm sát (thép đen ánh kim)
    paint_ascii("TASSET_PLATE", [
        "0111111111111110",
        "1234433333344321",
        "1233322332233321",
        "1223222332223221",
        "1122211221122211",
        "0111111001111110",
        "1233321111233321",
        "1234322222234321",
        "1233222332223321",
        "1222212332122221",
        "1122112222112211",
        "0122111221112210",
        "0112211111122110",
        "0011222222221100",
        "0001112222111000",
        "0000011111100000",
    ])

    # 13. THIGH_DARK & THIGH_SIDE (16x16) — Đùi đen than sẫm + 1 điểm nhấn xám thép nhỏ bên hông đùi giống góc trái mẫu!
    paint_ascii("THIGH_DARK", [
        "uuuuUUUUUUUUuuuu",
        "uuuUUUUUUUUUUuuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUUUUUUUUUUuu",
        "uuuUUUUUUUUUUuuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUUUUUUUUUUuu",
        "uuUUUUUUUUUUUUuu",
        "uuuUUUUUUUUUUuuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUUUUUUUUUUuu",
        "uuuUUUUUUUUUUuuu",
        "uuuuUUUUUUUUuuuu",
    ])
    paint_ascii("THIGH_SIDE", [
        "uuuuUUUUUUUUuuuu",
        "uuuUUUUUUUUUUuuu",
        "uuUUUUUUUUUUUUuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUuuSSuuUUUuu",
        "uuUUUuSWWSuUUUuu",
        "uuUUUuSWWSuUUUuu",
        "uuUUUuuSSuuUUUuu",
        "uuUUUUUUUUUUUUuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUuuuuuuUUUuu",
        "uuUUUUUUUUUUUUuu",
        "uuUUUUUUUUUUUUuu",
        "uuuUUUUUUUUUUuuu",
        "uuuUUUUUUUUUUuuu",
        "uuuuUUUUUUUUuuuu",
    ])

    # 14. BOOT_COLLAR, BOOT_FRONT, BOOT_SIDE, BOOT_BACK, BOOT_SOLE (16x16) — Giày giáp dày chuẩn 6 ảnh mẫu (0 khe sáng dọc!)
    paint_ascii("BOOT_COLLAR", [
        "0111111111111110",
        "1sSSWWWWWWWWSSs1",
        "1sSWWWWWWWWWWSs1",
        "1sSWWWWWWWWWWSs1",
        "1sSSWWWWWWWWSSs1",
        "1sSSSSWWWWSSSSs1",
        "11sSSSSSSSSSSs11",
        "011ssssssssss110",
        "0111111111111110",
        "1sSSWWWWWWWWSSs1",
        "1sSWWWWWWWWWWSs1",
        "1sSWWWWWWWWWWSs1",
        "1sSSWWWWWWWWSSs1",
        "1sSSSSWWWWSSSSs1",
        "11ssssssssssss11",
        "0011111111111100",
    ])
    paint_ascii("BOOT_FRONT", [
        "1122334444332211",
        "1233333333333321",
        "1232211221122321",
        "12221sSSSSs12221",
        "1221sSWWWWSs1221",
        "1221sSWWWWSs1221",
        "12211sSSSSs11221",
        "1232111001112321",
        "1233211221123321",
        "1234322332234321",
        "1233322332233321",
        "1223221221223221",
        "1122211111122211",
        "0112222222222110",
        "0011222222221100",
        "0001111111111000",
    ])
    paint_ascii("BOOT_SIDE", [
        "1122334444332211",
        "1233333333333321",
        "1232211111122321",
        "1222100110012221",
        "1221012332101221",
        "1221012332101221",
        "1221012222101221",
        "1221001sS1001221",
        "122101sSWs101221",
        "122101sSWs101221",
        "1222101sS1012221",
        "1232210000122321",
        "1233221111223321",
        "1123332222333211",
        "0112222222222110",
        "0011111111111100",
    ])
    paint_ascii("BOOT_BACK", [
        "1122333333332211",
        "1233322222233321",
        "1232211221122321",
        "1222112332112221",
        "1221122332211221",
        "1221123443211221",
        "1221123333211221",
        "1221122332211221",
        "1222112222112221",
        "1232211111122321",
        "1233221221223321",
        "1233322332233321",
        "1122322222232211",
        "0112222222222110",
        "0011222222221100",
        "0001111111111000",
    ])
    paint_ascii("BOOT_SOLE", [
        "1111111111111111",
        "1222222222222221",
        "1211111111111121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1210000000000121",
        "1211111111111121",
        "1222222222222221",
        "1111111111111111",
    ])

    # 15. CAPE_UPPER, CAPE_MID, CAPE_LOWER (16x16) — Áo choàng đỏ máu sẫm mỏng rách bậc ở mép dưới (0 glow!)
    paint_ascii("CAPE_UPPER", [
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
    ])
    paint_ascii("CAPE_MID", [
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
    ])
    paint_ascii("CAPE_LOWER", [
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCMMCCccCCMMCCc",
        "cCCCMCCccCCCMCCc",
        "cCCCMCCccCCCMCCc",
        "cCCCcCCccCCCcCCc",
        "cCCc.CCccCCc.CCc",
        "cCCc.CCc.CCc.cCc",
        "cCc..cCc.cC...cc",
        "cCc...c...c...cc",
        ".c....c........c",
    ])

    # 16. Các ô vật liệu còn lại (Sừng đỏ huyết, Xám thép, Obsidian, Mũi tam giác, Giáp vai phải)
    rng = np.random.default_rng(77421)
    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32)
    noise = rng.random((16, 16)).astype(np.float32)
    grain = 0.5 + 0.5 * np.sin((xx * 0.75 + yy * 0.45) + 1.0 * np.sin(yy * 0.55 - xx * 0.4))
    border = ((xx == 0) | (xx == 15) | (yy == 0) | (yy == 15))[:, :, None]
    bevel = ((xx == 1) | (yy == 1))[:, :, None]

    def put_proc(key: str, rgb):
        x0, y0, w, h = REG[key]
        diff[y0:y0 + h, x0:x0 + w, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        diff[y0:y0 + h, x0:x0 + w, 3] = 255

    # Obsidian dark & plate (#12141A -> #20242E -> #343A4A -> #586278)
    O1 = np.array([18, 20, 26], dtype=np.float32)
    O2 = np.array([32, 36, 46], dtype=np.float32)
    O3 = np.array([52, 58, 74], dtype=np.float32)
    O4 = np.array([88, 98, 120], dtype=np.float32)
    t = np.clip(grain * 0.65 + noise * 0.35, 0, 1)[:, :, None]
    put_proc("OBS_DARK", np.where(border, O1 * 0.72, O1 * (1 - t) + O2 * t))
    c_op = O2 * (1 - t) + O3 * t
    c_op = np.where(bevel, c_op * 0.5 + O4 * 0.5, c_op)
    c_op = np.where(border, O1 * 0.75, c_op)
    put_proc("OBS_PLATE", c_op)

    # Sừng đỏ huyết sẫm (#4A0E16 -> #821B24 -> #B52D36, 0 glow!)
    H1 = np.array([74, 14, 22], dtype=np.float32)
    H2 = np.array([130, 27, 36], dtype=np.float32)
    H3 = np.array([181, 45, 54], dtype=np.float32)
    ring = 0.5 + 0.5 * np.sin(yy * 1.45)
    t_h = np.clip(ring * 0.62 + (15.0 - xx) / 15.0 * 0.20 + noise * 0.18, 0, 1)[:, :, None]
    c_hb = H1 * (1 - t_h) + H2 * t_h
    c_hb = np.where(bevel, H2 * 0.45 + H3 * 0.55, c_hb)
    c_hb = np.where(border, H1 * 0.72, c_hb)
    put_proc("HORN_BASE", c_hb)
    c_ht = H2 * (1 - t_h) + H3 * t_h
    c_ht = np.where(border, H1 * 0.80, c_ht)
    put_proc("HORN_TIP", c_ht)

    # Xám thép / xương tro ánh kim (#555B66 -> #848B96 -> #B8BFC9, 0 glow!)
    A1 = np.array([85, 91, 102], dtype=np.float32)
    A2 = np.array([132, 139, 150], dtype=np.float32)
    A3 = np.array([184, 191, 201], dtype=np.float32)
    c_as = A1 * (1 - t) + A2 * t
    c_as = np.where(bevel, A2 * 0.35 + A3 * 0.65, c_as)
    c_as = np.where(border, A1 * 0.68, c_as)
    put_proc("ASH_STEEL", c_as)

    c_ad = A1 * 0.78 * (1 - t) + A1 * 1.05 * t
    c_ad = np.where(border, A1 * 0.58, c_ad)
    put_proc("ASH_DARK", c_ad)

    c_ab = A2 * (1 - t) + A3 * t
    c_ab = np.where(bevel, A3 * 1.08, c_ab)
    c_ab = np.where(border, A1 * 0.78, c_ab)
    put_proc("ASH_BRIGHT", c_ab)
    put_proc("NOSE_GUARD", c_ab)

    # Giáp vai phải lớn vát xiên nhiều mảnh xám thép (phân tầng tương phản sáng-tối rõ nét giống mẫu!)
    diag_ridge = (np.abs((xx - yy * 0.6) - 4.0) < 1.8)[:, :, None]
    c_pr = A1 * 0.35 + A2 * 0.65
    c_pr = np.where(diag_ridge, A3 * 0.96, c_pr)
    c_pr = np.where(bevel, A3 * 1.04, c_pr)
    c_pr = np.where(border, A1 * 0.62, c_pr)
    put_proc("PAULDRON_R", c_pr)

    out_dir = ASSETS / "textures" / "armor"
    out_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(out_dir / "nightmare_armor.png")
    Image.fromarray(glow, "RGBA").save(out_dir / "nightmare_armor_glowmask.png")
    print("TEX_ARMOR_OK: nightmare_armor.png (1:1 per-face recolor) + subtle selective glowmask (chest diamond + 2 tiny brow glints ONLY)")
    return diff.astype(np.float32), glow.astype(np.float32)


def build_six_item_icons_and_models():
    """Cập nhật 6 icon 16x16 RGBA sắc nét khớp 100% thiết kế giáp mới (không mắt đỏ to, không chấm vai, không khố trước, không vạch đỏ chân)."""
    PAL = {
        ".": (0, 0, 0, 0),
        "O": (12, 12, 16, 255),
        "d": (18, 20, 26, 255),     # #12141A obsidian sâu
        "s": (34, 38, 48, 255),     # #222630 obsidian trung
        "m": (58, 64, 80, 255),     # #3A4050 obsidian sáng
        "a": (85, 91, 102, 255),    # #555B66 xám thép tối
        "A": (132, 139, 150, 255),  # #848B96 xám thép trung
        "W": (184, 191, 201, 255),  # #B8BFC9 xám thép sáng
        "r": (74, 14, 22, 255),     # #4A0E16 đỏ huyết sẫm
        "R": (130, 27, 36, 255),    # #821B24 đỏ huyết trung
        "k": (181, 45, 54, 255),    # #B52D36 đỏ huyết sáng
        "g": (224, 40, 50, 255),    # #E02832 lõi đỏ thẫm phát sáng nhẹ
        "c": (255, 162, 138, 255),  # tâm lõi Nightmare Core
    }

    SPRITES = {
        "nightmare_core": [
            "......O..O......",
            ".....OW..WO.....",
            "....OsmOOmsO....",
            "...OdsRkkRsdO...",
            "..OAsRgggRsAO...",
            ".OsmRgcccgRmsO..",
            ".OdsRgcccgRsdO..",
            "..OdRgcccgRdO...",
            "..OdsRgggRsdO...",
            ".OsmRRgggRRmsO..",
            ".OAsRkgggKRsdO..".replace("K", "k"),
            "..OdsRkkkRsdO...",
            "...OdsRRRsdO....",
            "....OsmOOmsO....",
            ".....OW..WO.....",
            "......O..O......",
        ],
        "nightmare_armor_shard": [
            ".........OO.....",
            "........OAWO....",
            ".......OAsmO....",
            "......OAssdO....",
            ".....OAsrRsdO...",
            "....OAssRrsdO...",
            "...OAsmRrsddO...",
            "...OmsmrssddO...",
            "..OAsmmsddO.....",
            "..OmsRrsdO......",
            "..OdrrsddO......",
            "...OdsddO.......",
            "...OdsdO........",
            "....OdO.........",
            ".....O..........",
            "................",
        ],
        "nightmare_helmet": [
            "..OO........OO..",
            ".OkRO..OO..ORkO.",
            ".ORrO.OAWO.OrRO.",
            ".ORrOOmssmOOrRO.",
            "..OrOdsmmsdOrO..",
            "..OOdsWddWsdOO..",
            "...OdsWddWsdO...",
            "...OdsgddgsdO...",
            "...OdsdWWdsdO...",
            "...OdsAWWAsdO...",
            "...OdsdWWdsdO...",
            "....OdsAAdsO....",
            "....OdsddsdO....",
            ".....OOOOOO.....",
            "................",
            "................",
        ],
        "nightmare_chestplate": [
            ".OOOO.......OO..",
            "OAWWAO.....OmsO.",
            "OAWAWAO...OsmsO.",
            "OAAWAOOOOOOsmsO.",
            "OdAAmWddddWmsdO.",
            ".OOdsAWddWAsdOO.",
            "..OdsddggddsdO..",
            "..ORsdggggdsdO..",
            "..ORrOdggdOsdO..",
            "..ORrOAAAAOsdO..",
            "..ORrOAWWAOsdO..",
            "..ORrOAAAAOsdO..",
            "..ORROsmmsOsdO..",
            "..ORrOdsddOsdO..",
            "..OrO.OOOO.OO...",
            "..OO............",
        ],
        "nightmare_leggings": [
            "................",
            "...OOOOOOOOOO...",
            "..OmsmmddmmsmO..",
            "..OdsddddddsdO..",
            "..OmsddddddsmO..",
            "..OdsdOOOOdsdO..",
            "..OdddO..OdddO..",
            "..OdddO..OdddO..",
            "..OAWWO..OWWAO..",
            "..OAAAO..OAAAO..",
            "..OdsdO..OdsdO..",
            "..OdsdO..OdsdO..",
            "..OmsdO..OdsmO..",
            "..OdsdO..OdsdO..",
            "...OOO....OOO...",
            "................",
        ],
        "nightmare_boots": [
            "................",
            "................",
            "................",
            "..OOOO....OOOO..",
            ".OAWWAO..OAWWAO.",
            ".OdsmdO..OdmsdO.",
            ".OdsAdO..OdAsdO.",
            ".OdsmdO..OdmsdO.",
            ".OdsmdO..OdmsdO.",
            "OAsmmdO..OdmmsAO",
            "OmssddO..OddssmO",
            "OdssddO..OddssdO",
            "OddddOO..OOddddO",
            ".OOOO......OOOO.",
            "................",
            "................",
        ],
    }

    tex_dir = ASSETS / "textures" / "item"
    model_dir = ASSETS / "models" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)

    icons = {}
    for name, rows in SPRITES.items():
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = im.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                px[x, y] = PAL[ch]
        im.save(tex_dir / f"{name}.png")
        icons[name] = im
        model_json = {"parent": "minecraft:item/generated", "textures": {"layer0": f"abyssalrpg:item/{name}"}}
        (model_dir / f"{name}.json").write_text(json.dumps(model_json, indent=2), encoding="utf-8")

    print(f"ICONS_OK: Updated {len(icons)} 16x16 icons + item model JSONs")
    return icons


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


def build_base_steve_faces():
    """Chỉ dựng 2 bắp tay skin nhỏ để thấy khoảng hở bắp tay rõ nét như trong game."""
    faces = []
    for box_min, box_max, col in [
        ((-8, 12, -2), (-4, 24, 2), (146, 118, 96)),
        ((+4, 12, -2), (+8, 24, 2), (146, 118, 96)),
    ]:
        x0, y0, z0 = box_min
        x1, y1, z1 = box_max
        corners = np.array([
            [x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
            [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1],
        ], dtype=np.float64)
        for idxs in ([0, 1, 2, 3], [5, 4, 7, 6], [4, 0, 3, 7], [1, 5, 6, 2], [3, 2, 6, 7], [4, 5, 1, 0]):
            faces.append((corners[idxs], None, col))
    return faces


def render_6_angle_preview(geo: dict, diff_img, glow_img, icons: dict):
    """Xuất bảng preview đủ ĐÚNG 6 góc trùng 6 ảnh mẫu (Trước, Sau, Phải, Trái, Trên, Dưới) + Bản Đêm Glowmask + 6 Icon!"""
    faces = build_base_steve_faces()
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

    def raster(view_mat, w, h, sc, center, ambient=0.52, bg=(18, 20, 25), night=False):
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.30, 0.72, -0.62], dtype=np.float64)
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
            shade = (0.16 if night else ambient) + (1.0 - (0.16 if night else ambient)) * abs(float(np.dot(n, ld)))
            scr = np.stack([cx - cam[:, 0] * sc, cy - cam[:, 1] * sc, cam[:, 2]], axis=1)
            if uv is not None:
                u0, v0 = uv["uv"]
                uw, uh = uv["uv_size"]
                u1, v1 = u0 + max(1.0, uw) - 0.01, v0 + max(1.0, uh) - 0.01
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
                    u = np.clip((w0[cand] * t0[0] + w1[cand] * t1[0] + w2[cand] * t2[0]).astype(np.int32), 0, TEX_W - 1)
                    v = np.clip((w0[cand] * t0[1] + w1[cand] * t1[1] + w2[cand] * t2[1]).astype(np.int32), 0, TEX_H - 1)
                    alpha = diff_img[v, u, 3]
                    vis = alpha > 128
                    if not np.any(vis):
                        continue
                    cy_all, cx_all = np.where(cand)
                    cy_i, cx_i = cy_all[vis], cx_all[vis]
                    sub_z[cy_i, cx_i] = z[cand][vis]
                    d_c = diff_img[v[vis], u[vis], :3]
                    g_c = glow_img[v[vis], u[vis], :3]
                    g_a = glow_img[v[vis], u[vis], 3:4] / 255.0
                    lit = d_c * shade * (1.0 - g_a * 0.85) + g_c * (g_a * 1.15)
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    ctr = np.array([-0.4, 17.5, 0.5], dtype=np.float64)
    pw, ph = 380, 420
    # Đủ ĐÚNG 6 góc trùng 6 ảnh mẫu + Bản Đêm Glowmask!
    im_front  = raster(cam_mat(180,  4), pw, ph, 10.2, ctr, bg=(20, 22, 27))
    im_back   = raster(cam_mat(0,    4), pw, ph, 10.2, ctr, bg=(20, 22, 27))
    im_right  = raster(cam_mat(90,   4), pw, ph, 10.2, ctr, bg=(20, 22, 27))
    im_left   = raster(cam_mat(-90,  4), pw, ph, 10.2, ctr, bg=(20, 22, 27))
    im_top    = raster(cam_mat(180, 86), pw, ph, 13.5, np.array([-0.5, 29.0, 0.0]), bg=(20, 22, 27))
    im_bottom = raster(cam_mat(180,-84), pw, ph, 12.2, np.array([-0.5, 8.0, 0.8]), bg=(20, 22, 27))
    im_night  = raster(cam_mat(158,  8), pw, ph, 10.0, ctr, bg=(8, 9, 13), night=True)

    # Panel 8: 6 Icon 16x16
    im_icons = Image.new("RGB", (pw, ph), (16, 18, 23))
    dr_i = ImageDraw.Draw(im_icons)
    order = [
        ("nightmare_core", "CORE (5%)"),
        ("nightmare_armor_shard", "SHARD (6-10)"),
        ("nightmare_helmet", "HELMET"),
        ("nightmare_chestplate", "CHESTPLATE"),
        ("nightmare_leggings", "LEGGINGS"),
        ("nightmare_boots", "BOOTS"),
    ]
    for idx, (k, label) in enumerate(order):
        col = idx % 2
        row = idx // 2
        bx = 34 + col * 172
        by = 34 + row * 124
        dr_i.rectangle([bx, by, bx + 92, by + 92], fill=(28, 30, 38), outline=(92, 54, 62))
        big = icons[k].resize((80, 80), Image.NEAREST)
        im_icons.paste(big, (bx + 6, by + 6), big)
        dr_i.text((bx + 2, by + 96), label, fill=(215, 210, 218))

    sheet = Image.new("RGB", (pw * 4, ph * 2), (10, 11, 15))
    panels = [
        (im_front,  0, 0, "1. FRONT (CLOSED HELM, NO BIG EYES/LOINCLOTH)"),
        (im_back,   1, 0, "2. BACK (ASH SPINE SHIELD + THIN CRIMSON CAPE)"),
        (im_right,  2, 0, "3. RIGHT SIDE (SLANTED PAULDRON + 3-FOLD CAPE)"),
        (im_left,   3, 0, "4. LEFT SIDE (HORNS + SLEEK PAULDRON + V-GAUNTLET)"),
        (im_top,    0, 1, "5. TOP (PARALLEL SWEPT HORNS + CENTER FIN + X)"),
        (im_bottom, 1, 1, "6. BOTTOM (BOOT SOLES + THIN TORN CAPE)"),
        (im_night,  2, 1, "7. NIGHT GLOWMASK (SUBTLE CHEST CORE + 2 BROW DOTS)"),
        (im_icons,  3, 1, "8. 6 PIXEL-ART ICONS (16x16)"),
    ]
    dr = ImageDraw.Draw(sheet)
    for im, gx, gy, title in panels:
        sheet.paste(im, (gx * pw, gy * ph))
        dr.text((gx * pw + 8, gy * ph + 8), title, fill=(235, 118, 128))

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out_preview = PREVIEW_DIR / "nightmare_armor_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")


if __name__ == "__main__":
    geo_data, cubes = build_nightmare_armor_geo()
    build_nightmare_armor_animations()
    diff_img, glow_img = build_nightmare_armor_textures_128x128()
    icons = build_six_item_icons_and_models()
    render_6_angle_preview(geo_data, diff_img, glow_img, icons)
    print(f"ALL_DONE: 1:1 Recolored Nightmare Knight Armor generated ({cubes} cubes, 6-angle preview)")
