#!/usr/bin/env python3
"""
Bộ sinh 1:1 chuẩn mật độ pixel-art Minecraft (`64x64`, `1 texel = 1 model px`, ZERO noise, ZERO glowmask!)
cho **Nightmare Knight Armor** — khớp 100% hình khối 3D & bố cục pixel của 6 ảnh mẫu, CHỈ đổi bảng màu:
  1. Cặp sừng (`horn_l`, `horn_r`): bản to dày (`3x3..3x4 px`), mặt trơn đỏ huyết (`#4E1018 -> #7F1C26 -> #AA2B35`, 0 sọc ngang),
     vươn rộng sang 2 bên và cao vượt đỉnh mũ, uốn cong ra sau chẻ 3 nhánh gai + 2 gai phụ tách rời ở góc dưới gáy.
  2. Găng giáp cẳng tay (`armorRightArm/LeftArm`): khối 3D dày hơn thân tay (`5.6x5.6`), hở bắp tay rõ,
     cạnh trên cắt vát chéo 45° cực gắt (cao mép ngoài, thấp mép trong), cổ tay xám thép có khía.
  3. Giáp vai phải (`armorRightArm`): khối vát chéo gai góc to dày xám thép vươn cao ngang cằm/má phải & xòe rộng;
     vai trái gọn hộp vát có đường viền chữ U rõ nét mặt ngoài.
  4. Áo choàng một bên (`cape_upper/mid/lower`): siêu mỏng (`0.18 px`), rộng & đổ chéo từ dưới vai phải xuống sát gót chân,
     gấp 3 đoạn rõ rệt ra sau, 2 mép bên & gấu áo cắt răng cưa bậc pixel bất đối xứng, màu đỏ rượu sẫm (0 sọc dọc!).
  5. Mũ hộp kín 0 chấm mắt đỏ: 2 sọc dọc trán, viền ngang dưới, tấm mũi tam giác gọn, chữ X đỏ sẫm lì đỉnh mũ, mào đỉnh & chốt gáy.
  6. Thân giáp 1:1 phẳng gọn (V-ngực + 1 ô thoi phẳng đỏ sẫm lì + khung giáp bụng vuông xám thép + tấm lưng rộng),
     giáp hông tassets 3D hơi loe, giáp gối xám bản rộng TRƠN (0 chấm đen!), giày dày viền cổ sáng & mảng cong chữ U ở hông (0 chấm tròn!).

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

# Chuẩn pixel-art 1:1 Minecraft: 64x64 RGBA nearest, 1 texel = 1 model px!
TEX_W = 64
TEX_H = 64

REG = {
    # Hàng 0 (y=0..8): Mũ 8x8 + vật liệu 4x4 phẳng sạch (0 noise, 0 sọc!)
    "HELM_FRONT":          (0,   0, 8, 8),
    "HELM_BACK":           (8,   0, 8, 8),
    "HELM_SIDE":           (16,  0, 8, 8),
    "HELM_TOP":            (24,  0, 8, 8),
    "HORN_TOP":            (32,  0, 4, 4),
    "HORN_SIDE":           (36,  0, 4, 4),
    "HORN_DARK":           (40,  0, 4, 4),
    "ASH_LIGHT":           (44,  0, 4, 4),
    "ASH_MID":             (48,  0, 4, 4),
    "ASH_DARK":            (52,  0, 4, 4),
    "OBS_PLATE":           (56,  0, 4, 4),
    "OBS_DARK":            (60,  0, 4, 4),

    # Hàng 1 (y=10..22): Thân giáp 8x12 (1:1!) + vai + găng vát chéo + đùi + gối trơn
    "CHEST_FRONT":         (0,  10, 8, 12),
    "CHEST_BACK":          (8,  10, 8, 12),
    "CHEST_SIDE":          (16, 10, 4, 12),
    "PAULDRON_L":          (20, 10, 5, 5),
    "GAUNTLET_OUT":        (25, 10, 5, 6),
    "GAUNTLET_HIGH_LEFT":  (30, 10, 5, 6),
    "GAUNTLET_HIGH_RIGHT": (35, 10, 5, 6),
    "GAUNTLET_INNER":      (40, 10, 5, 6),
    "TASSET_F":            (45, 10, 5, 4),
    "THIGH_DARK":          (50, 10, 4, 7),
    "THIGH_SIDE":          (54, 10, 4, 7),
    "KNEE_PLAIN":          (58, 10, 5, 4),

    # Hàng 2 (y=24..31): Giày 5x5 + cổ giày 5x2 + vai phải vát xiên 4x4
    "BOOT_COLLAR":         (0,  24, 5, 2),
    "BOOT_FRONT":          (6,  24, 5, 5),
    "BOOT_SIDE":           (12, 24, 5, 5),
    "BOOT_BACK":           (18, 24, 5, 5),
    "BOOT_SOLE":           (24, 24, 5, 5),
    "PAULDRON_R_TOP":      (30, 24, 5, 5),
    "PAULDRON_R_SIDE":     (36, 24, 5, 5),
    "GAUNTLET_BOT":        (42, 24, 5, 5),

    # Hàng 3 (y=34..45): Áo choàng 3 đoạn (8x9 mỗi đoạn — mép 2 bên & gấu rách răng cưa bất đối xứng, 0 sọc dọc!)
    "CAPE_UPPER":          (0,  34, 8, 9),
    "CAPE_MID":            (10, 34, 8, 9),
    "CAPE_LOWER":          (20, 34, 8, 9),
}


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
    return make_cube([cx - sx * 0.5, cy - sy * 0.5, cz - sz * 0.5], [sx, sy, sz], mat, rot=rot, pivot=[cx, cy, cz], face_mats=face_mats)


def build_nightmare_armor_geo():
    """
    Hình khối 3D bám sát 100% 6 ảnh mẫu (không thêm chi tiết thừa, không glow!):
      - Y=0: đế giày | Y=12: hông | Y=22: vai | Y=24: cổ | Y=32: đỉnh đầu.
      - -Z = TRƯỚC | +Z = SAU | -X = PHẢI người chơi (vai phải gai góc + áo choàng) | +X = TRÁI người chơi.
    """
    # ==================== 1. MŨ (`armorHead`, `horn_r`, `horn_l`) ====================
    head_cubes = [
        # Hộp mũ kín mặt (9.0 x 9.0 x 9.0 — 0 mắt đỏ, 0 hốc mặt!)
        make_centered_cube(
            [0.0, 28.4, 0.0], [9.0, 9.0, 9.0], "OBS_PLATE",
            face_mats={
                "north": "HELM_FRONT",
                "south": "HELM_BACK",
                "east":  "HELM_SIDE",
                "west":  "HELM_SIDE",
                "up":    "HELM_TOP",
                "down":  "OBS_DARK",
            }
        ),
        # 2 sọc dọc mảnh trên trán nhô nhẹ 0.25px (khớp mặt trước & trên mẫu)
        make_centered_cube([-1.75, 31.3, -4.55], [0.65, 2.8, 0.28], "ASH_LIGHT"),
        make_centered_cube([+1.75, 31.3, -4.55], [0.65, 2.8, 0.28], "ASH_LIGHT"),
        # Tấm mũi/cằm tam giác nhọn gọn nhô nhẹ ở giữa dưới (khớp góc trước mẫu!)
        make_centered_cube([0.0, 26.85, -4.62], [2.8, 1.35, 0.42], "ASH_LIGHT"),
        make_centered_cube([0.0, 25.75, -4.66], [1.7, 1.00, 0.45], "ASH_MID"),
        make_centered_cube([0.0, 24.95, -4.60], [0.85, 0.65, 0.36], "ASH_DARK"),
        # 1 mào gai thẳng hẹp ở giữa phần sau-đỉnh mũ (khớp góc trên & sau mẫu!)
        make_centered_cube([0.0, 33.45, +2.75], [1.35, 1.55, 3.2], "ASH_MID", rot=[+10.0, 0.0, 0.0], face_mats={"up": "ASH_LIGHT"}),
        # 1 chốt giáp dọc nhỏ phía sau gáy + viền cổ dưới sau (khớp góc sau mẫu!)
        make_centered_cube([0.0, 26.6, +4.75], [1.55, 2.55, 0.90], "ASH_MID", rot=[+8.0, 0.0, 0.0], face_mats={"up": "ASH_LIGHT"}),
        make_centered_cube([0.0, 24.25, +4.45], [9.2, 1.05, 0.85], "OBS_DARK"),
    ]
    # 2 GAI PHỤ NHỎ TÁCH RỜI CHĨA NGƯỢC RA SAU Ở GÓC DƯỚI GÁY (thấy rất rõ ở cả 2 ảnh góc nghiêng mẫu!)
    for s in (-1.0, 1.0):
        head_cubes.append(make_centered_cube(
            [s * 4.55, 26.7, +5.35], [1.15, 1.05, 2.25], "ASH_MID",
            rot=[-24.0, s * 10.0, 0.0], face_mats={"up": "ASH_LIGHT", "down": "ASH_DARK"}
        ))
        head_cubes.append(make_centered_cube(
            [s * 4.65, 25.8, +6.35], [0.95, 1.10, 1.15], "ASH_DARK",
            rot=[-52.0, s * 10.0, 0.0]
        ))

    # CẶP SỪNG BẢN TO, DÀY, MẶT TRƠN GÓC CẠNH (`3x3` đến `3.2x3.8` px!) — MỌC TỪ 2 BÊN THÁI DƯƠNG,
    # NHÔ RỘNG RA HAI BÊN VÀ VƯƠN CAO VƯỢT HẲN ĐỈNH MŨ, UỐN CONG RA SAU RỒI CHẺ 2–3 NHÁNH GAI NHỌN Ở ĐUÔI!
    horn_faces = {"up": "HORN_TOP", "down": "HORN_DARK", "north": "HORN_TOP", "south": "HORN_DARK", "east": "HORN_SIDE", "west": "HORN_SIDE"}

    def build_thick_horn(s: float):
        hc = []
        # Gốc sừng dày bám ngang 2 bên thái dương trước, nhô rộng ra `x = ±6.3`
        hc.append(make_centered_cube([s * 5.65, 29.6, -2.5], [2.95, 2.85, 2.95], "HORN_SIDE", rot=[+26.0, 0.0, s * -18.0], face_mats=horn_faces))
        # Thân sừng dưới-trước vươn cao dày vọt lên góc trên-ngoài mũ (`y = 32.4..34.8`, `x = ±6.6` — nhìn trực diện & sau cực uy lực!)
        hc.append(make_centered_cube([s * 6.55, 32.5, -1.5], [3.15, 3.65, 3.30], "HORN_SIDE", rot=[+32.0, 0.0, s * -10.0], face_mats=horn_faces))
        # Đỉnh vòm sừng dày vươn CAO VƯỢT ĐỈNH MŨ (`y = 34.4..36.0`) vòng ra sau song song 2 bên mũ!
        hc.append(make_centered_cube([s * 6.45, 34.6, +0.9], [2.95, 2.90, 3.85], "HORN_SIDE", rot=[+10.0, 0.0, 0.0], face_mats=horn_faces))
        # Đoạn đuôi cong gập xuống phía sau đầu (`z = +3.5`)
        hc.append(make_centered_cube([s * 6.15, 34.0, +3.6], [2.35, 2.35, 3.10], "HORN_SIDE", rot=[-20.0, s * 5.0, 0.0], face_mats=horn_faces))
        # Chẻ 3 nhánh gai nhọn ở đuôi sừng phía sau (khớp góc nghiêng trái/phải mẫu!)
        hc.append(make_centered_cube([s * 5.95, 34.8, +5.4], [1.25, 1.10, 2.40], "HORN_TOP",  rot=[+12.0, s * 8.0, 0.0], face_mats=horn_faces))
        hc.append(make_centered_cube([s * 5.85, 33.1, +5.8], [1.25, 1.15, 2.55], "HORN_SIDE", rot=[-30.0, s * 8.0, 0.0], face_mats=horn_faces))
        hc.append(make_centered_cube([s * 6.10, 31.7, +4.4], [1.20, 1.15, 2.25], "HORN_DARK", rot=[-42.0, s * 10.0, 0.0], face_mats=horn_faces))
        return hc

    horn_r_cubes = build_thick_horn(-1.0)
    horn_l_cubes = build_thick_horn(+1.0)

    # ==================== 2. THÂN GIÁP (`armorBody`, pivot [0, 24, 0]) ====================
    # Hộp giáp thân phẳng gọn chuẩn Minecraft armor (8.8 x 12.2 x 4.9 — 0 cục đỏ lồi ngực, 0 thanh xương sườn hộp!)
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
        # Gờ chữ V cổ vai tinh tế ôm sát (0.25px) + chốt sau vai phải nối áo choàng
        make_centered_cube([-2.3, 22.9, -2.50], [2.4, 1.0, 0.25], "ASH_MID", rot=[0.0, 0.0, +20.0]),
        make_centered_cube([+2.3, 22.9, -2.50], [2.4, 1.0, 0.25], "ASH_MID", rot=[0.0, 0.0, -20.0]),
        make_centered_cube([-3.2, 22.7, +2.55], [3.6, 1.5, 0.45], "ASH_DARK", rot=[0.0, 0.0, +10.0]),
    ]

    # ==================== 2b. ÁO CHOÀNG ĐỔ CHÉO RÁCH BẬC 1 BÊN (`cape_upper/mid/lower` dưới `armorBody`) ====================
    # Siêu mỏng (0.18 px), rộng hơn & đổ chéo lệch sang bên từ dưới giáp vai phải (-X) xuống sát gót chân,
    # gập 3 đoạn sắc nét ra sau ở ngang gối/bắp chân (khớp y hệt góc sau, góc nghiêng & góc dưới của mẫu!)
    cape_upper_cubes = [
        make_centered_cube([-4.2, 17.8, +4.15], [5.8, 8.8, 0.18], "CAPE_UPPER", rot=[+24.0, +4.0, +6.0]),
    ]
    cape_mid_cubes = [
        make_centered_cube([-4.6, 10.2, +6.05], [6.2, 8.2, 0.18], "CAPE_MID",   rot=[+4.0,  +3.0, +4.0]),
    ]
    cape_lower_cubes = [
        make_centered_cube([-4.9, 3.8,  +8.85], [6.6, 7.6, 0.18], "CAPE_LOWER", rot=[+44.0, +3.0, +2.0]),
    ]

    # ==================== 3. GIÁP VAI PHẢI TO DÀY GAI GÓC & GĂNG 3D VÁT XÉO (`armorRightArm`, [-5, 22, 0]) ====================
    pauld_faces = {"up": "PAULDRON_R_TOP", "down": "ASH_DARK", "north": "PAULDRON_R_SIDE", "south": "PAULDRON_R_SIDE", "west": "PAULDRON_R_SIDE", "east": "ASH_DARK"}
    rarm_cubes = [
        # Khung vai lót dưới màu obsidian + viền cong chữ U xám bạc ở mặt ngoài (thấy rõ ngay dưới giáp vai phải góc nghiêng phải!)
        make_centered_cube(
            [-6.0, 21.6, 0.0], [5.2, 4.0, 5.2], "OBS_PLATE",
            face_mats={"west": "PAULDRON_L", "north": "OBS_PLATE", "south": "OBS_PLATE", "up": "OBS_DARK"}
        ),
        # KHỐI GIÁP VAI PHẢI TO, DÀY, GAI GÓC LỒNG NHAU VƯƠN CAO NGANG CẰM/MÁ PHẢI (`y = 26.2`) & XÒE RỘNG (`x = -9.6`)!
        # Khối lõi vát nghiêng chính ôm đỉnh vai
        make_centered_cube([-6.7, 24.4, 0.0],  [5.4, 2.5, 5.8], "PAULDRON_R_TOP",  rot=[0.0,   0.0, -24.0], face_mats=pauld_faces),
        # Khối gai nhọn đỉnh vươn cao lên gần má/cằm phải (silhouette góc cạnh mạnh ở góc trước & sau!)
        make_centered_cube([-6.0, 25.5, -0.4], [3.6, 2.4, 4.2], "PAULDRON_R_TOP",  rot=[-12.0, +14.0, -34.0], face_mats=pauld_faces),
        # Cánh vát xòe rộng ra ngoài vai phải (gồ ghề mạnh ở góc trước & góc phải!)
        make_centered_cube([-8.4, 23.1, 0.0],  [3.4, 2.8, 5.4], "PAULDRON_R_SIDE", rot=[0.0,   0.0, -38.0], face_mats=pauld_faces),
        # Mảnh vát chéo phía trước & sau tạo góc nhọn đa diện (khớp góc nghiêng phải mẫu!)
        make_centered_cube([-7.4, 23.2, -2.5], [3.8, 2.9, 2.0], "PAULDRON_R_SIDE", rot=[-24.0, +22.0, -26.0], face_mats=pauld_faces),
        make_centered_cube([-7.4, 23.2, +2.5], [3.8, 2.9, 2.0], "PAULDRON_R_SIDE", rot=[+24.0, -22.0, -26.0], face_mats=pauld_faces),
        # Mũi gờ góc nhọn dưới ngoài vai phải
        make_centered_cube([-8.7, 21.5, 0.0],  [2.2, 2.4, 3.4], "ASH_MID",         rot=[0.0,   0.0, +24.0], face_mats=pauld_faces),
    ]

    # KHOẢNG HỞ BẮP TAY RÕ RÀNG (`y = 17.5 .. 19.6`)
    # GĂNG GIÁP CẲNG TAY 3D DÀY RÕ RỆT (`5.6 x 5.6` — inflate lớn hơn tay Steve!) + CẮT VÁT CHÉO 45° CỰC GẮT!
    def build_thick_slanted_gauntlet(side_sign: float):
        # side_sign = -1 (Right arm, outer = -X = LEFT on Front view, RIGHT on Back view)
        # side_sign = +1 (Left  arm, outer = +X = RIGHT on Front view, LEFT on Back view)
        cx = side_sign * 6.0
        outer_face = "west" if side_sign < 0 else "east"
        inner_face = "east" if side_sign < 0 else "west"
        # Khớp chính xác hướng cắt chéo 45° trên cả MẶT TRƯỚC lẫn MẶT SAU!
        north_fb = "GAUNTLET_HIGH_LEFT"  if side_sign < 0 else "GAUNTLET_HIGH_RIGHT"
        south_fb = "GAUNTLET_HIGH_RIGHT" if side_sign < 0 else "GAUNTLET_HIGH_LEFT"
        return [
            # Hộp găng giáp 3D dày (5.6 x 5.8 x 5.6) bọc nửa dưới cẳng tay (`y = 11.6 .. 17.4`),
            # mặt trước/sau cắt chéo 45° sắc nét bằng cutout alpha 1:1 + mặt ngoài đầy đặn có cổ tay xám thép!
            make_centered_cube(
                [cx, 14.5, 0.0], [5.6, 5.8, 5.6], "GAUNTLET_OUT",
                face_mats={
                    "north":    north_fb,
                    "south":    south_fb,
                    outer_face: "GAUNTLET_OUT",
                    inner_face: "GAUNTLET_INNER",
                    "up":       "GAUNTLET_INNER",
                    "down":     "GAUNTLET_BOT",
                }
            ),
            # Khối độn 3D dày thực thụ dọc cổ tay dưới (`y = 11.65 .. 13.75`) để phần vát chéo phía trên hở sạch chữ V!
            make_centered_cube(
                [cx, 12.70, 0.0], [5.45, 2.05, 5.45], "OBS_DARK",
                face_mats={outer_face: "GAUNTLET_OUT", "down": "GAUNTLET_BOT"}
            ),
            # Cạnh vát chéo 3D nhô cao phía mép ngoài cẳng tay (`y = 13.8 .. 17.45`) tạo khối găng dày cực chắc!
            make_centered_cube(
                [cx + side_sign * 2.05, 15.65, 0.0], [1.55, 3.55, 5.62], "OBS_PLATE",
                face_mats={outer_face: "GAUNTLET_OUT", "up": "ASH_DARK"}
            ),
        ]

    rarm_cubes.extend(build_thick_slanted_gauntlet(-1.0))

    # ==================== 4. VAI TRÁI GỌN HỘP VÁT CÓ VIỀN CHỮ U + GĂNG 3D VÁT XÉO (`armorLeftArm`, [+5, 22, 0]) ====================
    larm_cubes = [
        # Tấm bọc vai trái hộp vát gọn có đường viền cong chữ U xám bạc rõ nét mặt ngoài (khớp góc trái mẫu!)
        make_centered_cube(
            [+6.15, 22.1, 0.0], [5.4, 4.1, 5.4], "OBS_PLATE",
            rot=[0.0, 0.0, +10.0],
            face_mats={"east": "PAULDRON_L", "north": "PAULDRON_L", "south": "PAULDRON_L", "up": "OBS_DARK", "down": "OBS_DARK"}
        ),
    ]
    larm_cubes.extend(build_thick_slanted_gauntlet(+1.0))

    # ==================== 5. QUẦN + GIÁP HÔNG 3D HƠI LOE + GIÁP GỐI TRƠN (`armorRightLeg`, `armorLeftLeg`) ====================
    def build_leg_cubes(side_sign: float):
        cx = side_sign * 2.0
        outer_face = "west" if side_sign < 0 else "east"
        return [
            # Đùi đen than ôm gọn (`y = 4.6 .. 12.1`)
            make_centered_cube(
                [cx, 8.4, 0.0], [4.55, 7.5, 4.55], "THIGH_DARK",
                face_mats={outer_face: "THIGH_SIDE"}
            ),
            # Lớp giáp hông tassets 3D hơi loe (inflate rõ quanh eo/đùi trên, KHÔNG có khố dài giữa đùi!)
            make_centered_cube([cx, 11.1, 0.0], [5.15, 2.85, 5.15], "TASSET_F"),
            make_centered_cube([cx + side_sign * 2.60, 10.6, 0.0], [0.65, 3.0, 5.05], "TASSET_F", rot=[0.0, 0.0, side_sign * -12.0]),
            make_centered_cube([cx, 10.8, -2.62], [4.75, 2.55, 0.55], "TASSET_F", rot=[-10.0, 0.0, 0.0]),
            make_centered_cube([cx, 10.8, +2.62], [4.75, 2.55, 0.55], "TASSET_F", rot=[+10.0, 0.0, 0.0]),
            # Tấm giáp gối bản rộng TRƠN GỌN màu XÁM THÉP phủ trọn mặt trước gối (`y = 4.8 .. 8.0`, 0 chấm đen!)
            make_centered_cube([cx, 6.45, -2.45], [4.45, 3.15, 0.58], "KNEE_PLAIN"),
        ]

    rleg_cubes = build_leg_cubes(-1.0)
    lleg_cubes = build_leg_cubes(+1.0)

    # ==================== 6. GIÀY GIÁP DÀY + CỔ GIÀY SÁNG + MẢNG CONG CHỮ U HÔNG (`armorRightBoot`, `armorLeftBoot`) ====================
    def build_boot_cubes(side_sign: float):
        cx = side_sign * 2.0
        outer_face = "west" if side_sign < 0 else "east"
        inner_face = "east" if side_sign < 0 else "west"
        return [
            # Thân giày dày chắc (5.2 x 4.55 x 5.2 — mảng cong chữ U ở mặt hông, mũi giáp chữ U mặt trước, 0 chấm tròn!)
            make_centered_cube(
                [cx, 2.28, 0.0], [5.20, 4.55, 5.20], "BOOT_SIDE",
                face_mats={
                    "north":    "BOOT_FRONT",
                    "south":    "BOOT_BACK",
                    outer_face: "BOOT_SIDE",
                    inner_face: "BOOT_BACK",
                    "down":     "BOOT_SOLE",
                    "up":       "OBS_DARK",
                }
            ),
            # Viền cổ giày sáng sắc nét bọc quanh cổ chân (`y = 4.35 .. 5.30`)
            make_centered_cube([cx, 4.80, 0.0], [5.50, 1.00, 5.50], "BOOT_COLLAR", face_mats={"up": "ASH_LIGHT"}),
        ]

    rboot_cubes = build_boot_cubes(-1.0)
    lboot_cubes = build_boot_cubes(+1.0)

    bones = [
        {"name": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},
        {"name": "horn_r",    "parent": "armorHead", "pivot": [-5.2, 30.0, -2.5], "cubes": horn_r_cubes},
        {"name": "horn_l",    "parent": "armorHead", "pivot": [+5.2, 30.0, -2.5], "cubes": horn_l_cubes},

        {"name": "bipedBody", "pivot": [0, 24, 0]},
        {"name": "armorBody", "parent": "bipedBody", "pivot": [0, 24, 0], "cubes": body_cubes},
        {"name": "cape_upper", "parent": "armorBody", "pivot": [-3.9, 22.0, 2.55], "cubes": cape_upper_cubes},
        {"name": "cape_mid",   "parent": "cape_upper", "pivot": [-4.3, 14.0, 5.80], "cubes": cape_mid_cubes},
        {"name": "cape_lower", "parent": "cape_mid",   "pivot": [-4.6, 6.4,  6.30], "cubes": cape_lower_cubes},

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
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, 64x64 1:1 UV)")
    return geo, total_cubes


def build_nightmare_armor_animations():
    anims = {
        "animation.nightmare_armor.idle": {
            "loop": True,
            "animation_length": 3.2,
            "bones": {
                "cape_upper": {"rotation": {"0.0": [0.0, 0.0, 0.0], "1.6": [2.0, 0.6, 0.4], "3.2": [0.0, 0.0, 0.0]}},
                "cape_mid":   {"rotation": {"0.0": [0.0, 0.0, 0.0], "1.6": [3.0, -0.6, 0.6], "3.2": [0.0, 0.0, 0.0]}},
                "cape_lower": {"rotation": {"0.0": [0.0, 0.0, 0.0], "1.6": [4.5, 0.8, -0.6], "3.2": [0.0, 0.0, 0.0]}},
            },
        }
    }
    out = ASSETS / "animations" / "armor" / "nightmare_armor.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out}")


def build_nightmare_armor_texture_64x64():
    """
    Texture pixel-art 1:1 THUẦN TÚY (`64x64`, `1 texel = 1 model px`, 0 noise, 0 glowmask!):
    Chỉ 3–4 tông sắc nét mỗi chất liệu, ánh xạ 1:1 trực tiếp từ 6 ảnh mẫu sang Nightmare Knight:
      - Mảng đỏ mẫu -> Thép đen obsidian: #14161C, #232732, #383E4E, cạnh #525A6E
      - Sừng mẫu    -> Đỏ huyết sẫm TRƠN: #4E1018, #7F1C26, #AA2B35 (0 sọc ngang!)
      - Áo choàng   -> Đỏ rượu sẫm:       #420A12, #6E141E, #94212C (0 sọc dọc!)
      - Mảng xám    -> Xám thép bạc:      #5A606B, #858C98, #B6BDC9
      - Đùi tối     -> Đen than:          #161519, #26242B
      - Thoi ngực & chữ X đỉnh mũ -> Đỏ sẫm lì (#8C1B24, #B82934), 0 glow!
    """
    img = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    P = {
        ".": (0, 0, 0, 0),            # Trong suốt (cắt chéo găng tay & mép răng cưa áo choàng!)
        "0": (12, 13, 18, 255),       # Rãnh sâu đen
        "1": (20, 22, 28, 255),       # #14161C obsidian tối
        "2": (35, 39, 50, 255),       # #232732 obsidian trung
        "3": (56, 62, 78, 255),       # #383E4E obsidian sáng
        "4": (82, 90, 110, 255),      # #525A6E cạnh sáng thép đen

        "s": (90, 96, 107, 255),      # #5A606B xám thép tối
        "S": (133, 140, 152, 255),    # #858C98 xám thép trung
        "W": (182, 189, 201, 255),    # #B6BDC9 xám thép sáng

        "h": (78, 16, 24, 255),       # #4E1018 sừng đỏ huyết tối
        "H": (127, 28, 38, 255),      # #7F1C26 sừng đỏ huyết trung
        "R": (170, 43, 53, 255),      # #AA2B35 sừng đỏ huyết sáng

        "c": (66, 10, 18, 255),       # #420A12 áo choàng đỏ rượu tối
        "C": (110, 20, 30, 255),      # #6E141E áo choàng đỏ rượu trung
        "M": (148, 33, 44, 255),      # #94212C áo choàng đỏ rượu sáng

        "u": (22, 21, 25, 255),       # #161519 đùi đen than tối
        "U": (38, 36, 43, 255),       # #26242B đùi đen than sáng

        "x": (140, 27, 36, 255),      # #8C1B24 đỏ sẫm lì (chữ X đỉnh / viền thoi ngực)
        "X": (184, 41, 52, 255),      # #B82934 đỏ sẫm lì sáng hơn nhẹ (tâm thoi ngực — 0 glow!)
    }

    def paint(key: str, rows):
        x0, y0, w, h = REG[key]
        assert len(rows) == h, f"{key}: expected {h} rows, got {len(rows)}"
        for y, row in enumerate(rows):
            assert len(row) == w, f"{key} row {y}: expected {w} chars, got {len(row)}"
            for x, ch in enumerate(row):
                img[y0 + y, x0 + x] = P[ch]

    # 1. MŨ (`8x8` chuẩn 1:1 — KHÔNG CÓ 2 CHẤM MẮT ĐỎ!)
    paint("HELM_FRONT", [
        "23S33S32",
        "23W33W32",
        "22S33S22",
        "12233221",
        "111SS111",
        "12sWWs21",
        "121SW121",
        "011ss110",
    ])
    paint("HELM_BACK", [
        "12333321",
        "23433432",
        "23222232",
        "22122122",
        "122SS221",
        "122SS221",
        "11211211",
        "01111110",
    ])
    paint("HELM_SIDE", [
        "12333321",
        "23443321",
        "23223221",
        "22122211",
        "12232111",
        "12332121",
        "11221121",
        "01111110",
    ])
    # Đỉnh mũ: họa tiết chữ X đỏ sẫm lì tối giản + 2 móc sáng ở mép trán (đáy = north!)
    paint("HELM_TOP", [
        "12322321",
        "2x3223x2",
        "23x33x32",
        "222xx222",
        "22x22x22",
        "2x2332x2",
        "23S22S32",
        "23W22W32",
    ])

    # 2. Vật liệu phẳng sạch 4x4 (0 noise, sừng trơn mịn!)
    paint("HORN_TOP", [
        "RRRR",
        "RRRH",
        "RHHH",
        "HHHh",
    ])
    paint("HORN_SIDE", [
        "RRHH",
        "RHHH",
        "HHhh",
        "Hhhh",
    ])
    paint("HORN_DARK", [
        "HHhh",
        "Hhhh",
        "hhhh",
        "hhhh",
    ])
    paint("ASH_LIGHT", [
        "WWWW",
        "WWWS",
        "WWSS",
        "WSSs",
    ])
    paint("ASH_MID", [
        "WWSS",
        "WSSS",
        "SSss",
        "Ssss",
    ])
    paint("ASH_DARK", [
        "SSss",
        "Ssss",
        "ssss",
        "s00s",
    ])
    paint("OBS_PLATE", [
        "3443",
        "3332",
        "2322",
        "1221",
    ])
    paint("OBS_DARK", [
        "2221",
        "2111",
        "1110",
        "1000",
    ])

    # 3. THÂN GIÁP (`8x12` 1:1 — viền V vai ngực, 1 ô thoi phẳng gọn đỏ sẫm lì, giáp bụng khung vuông xám thép, giáp lưng rộng!)
    paint("CHEST_FRONT", [
        "2S2112S2",
        "23W22W32",
        "22SWWS22",
        "232xx232",
        "23xXXx32",
        "222xx222",
        "12sSSs21",
        "1sSWWSs1",
        "1sWSSWs1",
        "1sSWWSs1",
        "12sSSs21",
        "01233210",
    ])
    paint("CHEST_BACK", [
        "2S2112S2",
        "23S22S32",
        "22sSSs22",
        "221SS122",
        "22sWWs22",
        "1sSWWSs1",
        "1sSWWSs1",
        "1sSWWSs1",
        "12sWWs21",
        "121SS121",
        "12333321",
        "01222210",
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
        "1221",
        "0110",
    ])

    # 4. VAI TRÁI (`5x5` với đường viền cong chữ U xám bạc rõ nét!) & VAI PHẢI (`5x5` xám thép góc cạnh)
    paint("PAULDRON_L", [
        "23432",
        "23332",
        "s212s",
        "sW1Ws",
        "0sWWs",
    ])
    paint("PAULDRON_R_TOP", [
        "sWWWS",
        "WWWWW",
        "WWWSS",
        "WSSss",
        "sSSss",
    ])
    paint("PAULDRON_R_SIDE", [
        "sWWWs",
        "SWWSS",
        "SSWWs",
        "sSSss",
        "0sss0",
    ])

    # 5. GĂNG GIÁP CẲNG TAY 3D VÁT CHÉO 45° (`5x6` 1:1 — mặt ngoài cổ tay xám bạc sắc nét, cạnh trên cắt chéo gắt!)
    paint("GAUNTLET_OUT", [
        "32.23",
        "34343",
        "11111",
        "sWWWs",
        "SWsWS",
        "sSsSs",
    ])
    paint("GAUNTLET_HIGH_LEFT", [
        "3....",
        "34...",
        "232..",
        "12321",
        "sWWSS",
        "sSSss",
    ])
    paint("GAUNTLET_HIGH_RIGHT", [
        "....3",
        "...43",
        "..232",
        "12321",
        "SSWWs",
        "ssSSs",
    ])
    paint("GAUNTLET_INNER", [
        ".....",
        ".....",
        ".....",
        ".....",
        "sSSSs",
        "sssss",
    ])
    paint("GAUNTLET_BOT", [
        "12221",
        "21112",
        "21012",
        "21112",
        "12221",
    ])

    # 6. GIÁP HÔNG 3D, ĐÙI ĐEN THAN & GIÁP GỐI BẢN RỘNG TRƠN (0 CHẤM ĐEN!)
    paint("TASSET_F", [
        "13431",
        "23332",
        "12321",
        "01210",
    ])
    paint("THIGH_DARK", [
        "uUUu",
        "uUUu",
        "uUuu",
        "uUUu",
        "uUUu",
        "uUuu",
        "uUUu",
    ])
    paint("THIGH_SIDE", [
        "uUUu",
        "uUUu",
        "uSWu",
        "uWSu",
        "uUUu",
        "uUUu",
        "uUUu",
    ])
    # Giáp gối xám bản rộng TRƠN GỌN (0 chấm đen!)
    paint("KNEE_PLAIN", [
        "sWWWs",
        "SWWWS",
        "SSWSS",
        "sSSSs",
    ])

    # 7. GIÀY DÀY: cổ giày sáng sắc nét, mặt trước mũi chữ U, mặt hông MẢNG VIỀN CONG CHỮ U (0 chấm tròn!)
    paint("BOOT_COLLAR", [
        "sWWWs",
        "sSSSs",
    ])
    paint("BOOT_FRONT", [
        "23432",
        "21112",
        "21S12",
        "21112",
        "12321",
    ])
    paint("BOOT_SIDE", [
        "23432",
        "20002",
        "20s02",
        "2SWW2",
        "12321",
    ])
    paint("BOOT_BACK", [
        "23332",
        "23432",
        "22322",
        "12321",
        "12221",
    ])
    paint("BOOT_SOLE", [
        "22222",
        "21112",
        "21012",
        "21112",
        "22222",
    ])

    # 8. ÁO CHOÀNG ĐỎ RƯỢU SẪM RÁCH RĂNG CƯA BẤT ĐỐI XỨNG 2 MÉP & GẤU (0 sọc dọc thẳng tắp!)
    paint("CAPE_UPPER", [
        "..cCCCMc",
        "..cCCMCc",
        ".cCCMCcc",
        ".cCMCCcc",
        ".cCMCCc.",
        "cCCMCcc.",
        "cCCMCcc.",
        "cCMCCcc.",
        "cCCMCc..",
    ])
    paint("CAPE_MID", [
        ".cCMCCc.",
        ".cCCMCcc",
        "cCCMCcc.",
        "cCMCCcc.",
        "cCCMCcc.",
        "cCMCCc..",
        "cCCMCcc.",
        "cCCMCcc.",
        ".cCMCCc.",
    ])
    paint("CAPE_LOWER", [
        "cCCMCcc.",
        "cCMCCcc.",
        "cCCMCcc.",
        "cCCMCc..",
        "cCMCCc..",
        "cCc.Cc..",
        "cC..Cc.c",
        "c...c..c",
        "....c...",
    ])

    out_dir = ASSETS / "textures" / "armor"
    out_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(out_dir / "nightmare_armor.png")
    print("TEX_ARMOR_OK: nightmare_armor.png (64x64 1:1 pure pixel-art, ZERO noise, ZERO glowmask)")
    return img.astype(np.float32)


def build_six_item_icons_and_models():
    """6 icon 16x16 RGBA sắc nét khớp 100% thiết kế không mắt đỏ / không glow của giáp mới."""
    PAL = {
        ".": (0, 0, 0, 0),
        "O": (12, 13, 18, 255),
        "d": (20, 22, 28, 255),     # #14161C
        "s": (35, 39, 50, 255),     # #232732
        "m": (56, 62, 78, 255),     # #383E4E
        "a": (90, 96, 107, 255),    # #5A606B
        "A": (133, 140, 152, 255),  # #858C98
        "W": (182, 189, 201, 255),  # #B6BDC9
        "r": (78, 16, 24, 255),     # #4E1018
        "R": (127, 28, 38, 255),    # #7F1C26
        "k": (170, 43, 53, 255),    # #AA2B35
        "g": (184, 41, 52, 255),    # #B82934
        "c": (255, 162, 138, 255),  # core center highlight (item nightmare_core only)
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
            ".OAsRkgggkRsdO..",
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
            ".OOO........OOO.",
            "OkRRO..OO..ORRkO",
            "ORRRO.OAWO.ORRRO",
            "ORRROOmssmOORRRO",
            ".OrROdsmmsdORrO.",
            "..OOdsWddWsdOO..",
            "...OdsWddWsdO...",
            "...OdsAddAsdO...",
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
            "OOOOO.......OO..",
            "OAWWAO.....OmsO.",
            "OAWWWAO...OsmsO.",
            "OAAWAAOOOOOsmsO.",
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
            "..OAWWO..OWWAO..",
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


def render_6_angle_preview(geo: dict, diff_img):
    """
    Xuất bảng ảnh preview ĐÚNG 6 góc trùng khớp 6 ảnh mẫu (Trước, Sau, Phải, Trái, Trên, Dưới)
    KHÔNG có nền skin giả che găng tay để thấy rõ 100% khối giáp tách rời (bắp tay trống) giống 6 ảnh mẫu!
    """
    faces = []
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
                faces.append((corners[idxs], uv_map[fname]))

    def raster(view_mat, w, h, sc, center, ambient=0.58, bg=(22, 23, 27)):
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.28, 0.74, -0.60], dtype=np.float64)
        ld /= np.linalg.norm(ld)
        cx, cy = w * 0.5, h * 0.52

        for quad, uv in faces:
            rel = quad - center[None, :]
            cam = (view_mat @ rel.T).T
            n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
            nl = np.linalg.norm(n)
            if nl < 1e-6:
                continue
            n /= nl
            shade = ambient + (1.0 - ambient) * abs(float(np.dot(n, ld)))
            scr = np.stack([cx - cam[:, 0] * sc, cy - cam[:, 1] * sc, cam[:, 2]], axis=1)
            u0, v0 = uv["uv"]
            uw, uh = uv["uv_size"]
            u1, v1 = u0 + uw - 0.001, v0 + uh - 0.001
            uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
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
                fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    ctr = np.array([-0.5, 18.0, 0.8], dtype=np.float64)
    pw, ph = 420, 460
    im_front  = raster(cam_mat(180,   4), pw, ph, 10.6, ctr)
    im_back   = raster(cam_mat(0,     4), pw, ph, 10.6, ctr)
    im_right  = raster(cam_mat(90,    4), pw, ph, 10.6, ctr)
    im_left   = raster(cam_mat(-90,   4), pw, ph, 10.6, ctr)
    im_top    = raster(cam_mat(180,  86), pw, ph, 14.2, np.array([-0.4, 30.0, 0.2]))
    im_bottom = raster(cam_mat(180, -84), pw, ph, 12.8, np.array([-0.4,  8.0, 1.0]))

    sheet = Image.new("RGB", (pw * 3, ph * 2), (12, 13, 16))
    panels = [
        (im_front,  0, 0, "1. FRONT (0 EYES, 0 GLOW, THICK HORNS, SLANT GAUNTLETS, PLAIN KNEES)"),
        (im_back,   1, 0, "2. BACK (ASH BACKPLATE + DIAGONAL STEPPED-TORN CRIMSON CAPE)"),
        (im_right,  2, 0, "3. RIGHT SIDE (JAGGED PAULDRON + DETACHED NAPE SPUR + 3-FOLD CAPE)"),
        (im_left,   0, 1, "4. LEFT SIDE (THICK HORNS + U-PAULDRON + V-GAUNTLET + U-BOOT)"),
        (im_top,    1, 1, "5. TOP (THICK HORNS + CENTER FIN + FLAT X + BROW HOOKS)"),
        (im_bottom, 2, 1, "6. BOTTOM (OBSIDIAN BOOT SOLES + THIN RAGGED CAPE)"),
    ]
    dr = ImageDraw.Draw(sheet)
    for im, gx, gy, title in panels:
        sheet.paste(im, (gx * pw, gy * ph))
        dr.text((gx * pw + 8, gy * ph + 8), title, fill=(215, 222, 232))

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out_preview = PREVIEW_DIR / "nightmare_armor_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")


if __name__ == "__main__":
    geo_data, cubes = build_nightmare_armor_geo()
    build_nightmare_armor_animations()
    diff_img = build_nightmare_armor_texture_64x64()
    build_six_item_icons_and_models()
    render_6_angle_preview(geo_data, diff_img)
    print(f"ALL_DONE: 1:1 64x64 Pure Pixel-Art Nightmare Knight Armor ({cubes} cubes, ZERO glowmask)")
