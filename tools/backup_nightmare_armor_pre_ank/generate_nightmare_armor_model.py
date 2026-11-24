#!/usr/bin/env python3
"""
Bộ sinh bộ giáp endgame 4 món **Nightmare Knight Armor** (GeckoLib 4.8.3 GeoArmorRenderer):
  - `assets/abyssalrpg/geo/armor/nightmare_armor.geo.json`
    + Đúng 8 xương chuẩn GeckoLib (`armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`,
      `armorRightLeg`, `armorLeftLeg`, `armorRightBoot`, `armorLeftBoot`) + 3 đốt áo choàng động
      (`cape_upper`, `cape_mid`, `cape_lower`) gắn dưới `armorBody` (bên vai phải!).
    + Khớp 100% silhouette 6 góc ảnh mẫu (mũ hộp mào giữa + 2 sừng cong vòng sau chẻ gai, mặt nạ sọ vát nhọn,
      vai phải giáp nhiều tầng khổng lồ có gai, vai trái gọn sắc, áo choàng rách 1 bên vai phải xuống gót,
      khung xương sườn bụng + lõi năng lượng ngực, hở bắp tay + găng cẳng tay gai, vạt hông tassets + gối xương + giày dày).
  - `assets/abyssalrpg/animations/armor/nightmare_armor.animation.json`
  - `assets/abyssalrpg/textures/armor/nightmare_armor.png` & `nightmare_armor_glowmask.png` (`128x128` RGBA nearest):
    + Đổi màu 100% theo boss Nightmare Knight:
      * Giáp chính: Thép đen obsidian ánh kim (`#14161C`, `#222630`, `#373D4C`, viền `#6B758A`, vân xước & AO khe giáp).
      * Sừng mũ: Đỏ huyết sẫm có khía vòng ngang (`#4D0F16 -> #8E1E27 -> #BA363D`).
      * Áo choàng rách 1 bên & vạt vải hông: Đỏ máu / đỏ rượu sẫm (`#580D15 -> #8F1B24 -> #B52933`).
      * Mặt nạ mũi/hàm, xương sườn bụng & gờ giáp gối: Xám xương ngà phong hóa (`#7D766F -> #AEA69C`, rãnh `#3B3632`).
      * Đùi lót: Nâu-xám than tối (`#241D1C -> #382D2A`) có dải băng đỏ sẫm.
      * Glowmask chọn lọc: CHỈ mắt đỏ khe mũ, lõi năng lượng giữa ngực, khóa rune vai/ngực & khe đỏ mảnh găng/gối phát sáng!
  - 6 Icon pixel-art `16x16` RGBA nearest + 6 Item model JSON (`nightmare_core`, `nightmare_armor_shard`,
    `nightmare_helmet`, `nightmare_chestplate`, `nightmare_leggings`, `nightmare_boots`).
  - Bảng ảnh preview 3D đa góc (Trước, Sau, Trái, Phải, Trên, Bản Đêm Glowmask & 6 Icon):
    `tools/nightmare_armor_previews/nightmare_armor_preview.png`.

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

# Atlas 128x128 RGBA nearest (8x4 ô vật liệu 16x16, mỗi ô có vân xước, viền sáng kim loại & AO rãnh sâu!)
MAT = {
    "STEEL_DARK":    (0,   0, 16, 16),  # #14161C -> #222630 thép đen obsidian sâu (0 glow)
    "STEEL_PLATE":   (16,  0, 16, 16),  # #222630 -> #373D4C tấm giáp rèn có vân xước & viền #6B758A (0 glow)
    "STEEL_TRIM":    (32,  0, 16, 16),  # #373D4C -> #6B758A gờ gai / cạnh vát thép sáng sắc (0 glow)
    "STEEL_RUNE":    (48,  0, 16, 16),  # Thép đen có khe rune đỏ máu siêu mảnh ở giữa (chỉ khe đỏ glow!)
    "HORN_CRIMSON":  (64,  0, 16, 16),  # Sừng đỏ huyết có khía vòng ngang (#4D0F16 -> #8E1E27 -> #BA363D, 0 glow)
    "HORN_TIP":      (80,  0, 16, 16),  # Chóp sừng đỏ sẫm chuyển sáng (#8E1E27 -> #BA363D, 0 glow)
    "BONE_IVORY":    (96,  0, 16, 16),  # Xám xương ngà phong hóa (#7D766F -> #AEA69C, rãnh #3B3632, 0 glow)
    "BONE_RIB":      (112, 0, 16, 16),  # Xương sườn bụng sọc ngang sắc nét trên nền tối (0 glow)

    "CAPE_CLOTH":    (0,  16, 16, 16),  # Vải áo choàng đỏ máu / đỏ rượu sẫm (#580D15 -> #8F1B24 -> #B52933, 0 glow)
    "CAPE_TORN":     (16, 16, 16, 16),  # Mép rách dưới áo choàng đỏ sẫm có khía tơi trong suốt (alpha=0 ở khía rách!)
    "THIGH_UNDER":   (32, 16, 16, 16),  # Quần lót đùi nâu-xám than (#241D1C -> #382D2A) + dải băng đỏ sẫm (0 glow)
    "CHEST_PLATE":   (48, 16, 16, 16),  # Mặt giáp ngực chữ V xếp lớp thép đen + gờ ánh kim (0 glow)
    "HELM_VISOR":    (64, 16, 16, 16),  # Mặt trước mũ thép đen + KHE MẮT ĐỎ RỰC phát sáng fullbright!
    "CORE_CRIMSON":  (80, 16, 16, 16),  # Lõi năng lượng đỏ máu giữa ngực (phát sáng rực fullbright!)
    "CLASP_RUNE":    (96, 16, 16, 16),  # Khóa tròn rune đỏ máu vai/ngực bọc viền thép đen (tâm đỏ glow!)
    "KNEE_BONE":     (112,16, 16, 16),  # Tấm giáp đầu gối thép đen viền xám xương ngà + chấm khe đỏ mảnh!
}


def uv_box(mat_key: str, pad: int = 1) -> dict:
    x0, y0, w, h = MAT[mat_key]
    f = {"uv": [x0 + pad, y0 + pad], "uv_size": [w - 2 * pad, h - 2 * pad]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def make_cube(origin, size, mat: str, rot=None, pivot=None, face_mats=None) -> dict:
    ox, oy, oz = [round(float(v), 3) for v in origin]
    sx, sy, sz = [round(float(v), 3) for v in size]
    uvs = uv_box(mat)
    if face_mats:
        for fn, mk in face_mats.items():
            uvs[fn] = uv_box(mk)[fn]
    c = {"origin": [ox, oy, oz], "size": [sx, sy, sz], "uv": uvs}
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
    Hệ tọa độ Bedrock chuẩn GeckoLib `GeoArmorRenderer`:
      - Y=0: gót chân | Y=12: hông/khớp đùi | Y=22: khớp vai | Y=24: cổ/đầu | Y=32: đỉnh đầu.
      - -Z = MẶT TRƯỚC (mặt, ngực, đầu gối) | +Z = MẶT SAU (gáy, cột sống, áo choàng).
      - -X = BÊN PHẢI người chơi (`armorRightArm` [-5,22,0], vai phải giáp gai lớn & áo choàng phải!).
      - +X = BÊN TRÁI người chơi (`armorLeftArm` [+5,22,0], vai trái gọn sắc).
    Mọi mảnh giáp nở rộng 0.55–0.85px so với hộp Steve gốc -> 0 z-fighting 100%!
    """

    # ==================== 1. MŨ (`armorHead`, pivot [0, 24, 0]) ====================
    # Hộp đầu Steve: x=[-4..4], y=[24..32], z=[-4..4]
    head_cubes = []
    # Khung mũ thép đen obsidian bọc kín (9.2 x 9.2 x 9.2, mặt trước có khe mắt đỏ rực HELM_VISOR!)
    head_cubes.append(make_centered_cube(
        [0.0, 28.1, 0.0], [9.2, 9.0, 9.2], "STEEL_PLATE",
        face_mats={"north": "HELM_VISOR", "down": "STEEL_DARK"}
    ))
    # Gờ đỉnh mũ & tấm vát trán sắc cạnh
    head_cubes.append(make_centered_cube([0.0, 32.8, -0.3], [8.2, 0.9, 8.8], "STEEL_DARK"))
    head_cubes.append(make_centered_cube([0.0, 31.8, -4.65], [7.6, 1.5, 0.9], "STEEL_TRIM", rot=[-10.0, 0.0, 0.0]))
    # Mào sống giữa chạy từ đỉnh trán vòng ra sau gáy (giống góc trên & góc sau ảnh mẫu!)
    head_cubes.append(make_centered_cube([0.0, 33.4, -2.2], [1.4, 1.5, 4.2], "STEEL_TRIM", rot=[-14.0, 0.0, 0.0]))
    head_cubes.append(make_centered_cube([0.0, 33.4, +1.8], [1.4, 1.6, 4.4], "STEEL_TRIM", rot=[+10.0, 0.0, 0.0]))
    head_cubes.append(make_centered_cube([0.0, 30.8, +4.95], [1.5, 3.8, 1.2], "STEEL_TRIM", rot=[+14.0, 0.0, 0.0]))
    # Tấm che gáy xếp lớp phía sau cổ (khớp góc sau ảnh mẫu)
    head_cubes.append(make_centered_cube([0.0, 25.4, +4.65], [9.4, 3.0, 1.1], "STEEL_DARK", rot=[+10.0, 0.0, 0.0]))
    head_cubes.append(make_centered_cube([0.0, 26.8, +5.15], [2.4, 2.8, 1.1], "STEEL_TRIM", rot=[+6.0, 0.0, 0.0]))
    # Mặt nạ mũi / hàm sọ xương ngà vát nhọn dữ tợn phía trước (khớp mặt nạ xám sáng ở ảnh mẫu -> đổi màu xương ngà!)
    head_cubes.append(make_centered_cube([0.0, 26.5, -4.85], [3.6, 2.7, 1.0], "BONE_IVORY", rot=[+8.0, 0.0, 0.0]))
    head_cubes.append(make_centered_cube([0.0, 24.8, -4.85], [2.2, 1.8, 1.1], "BONE_IVORY", rot=[+14.0, 0.0, 0.0]))
    head_cubes.append(make_centered_cube([-2.3, 25.4, -4.75], [2.2, 2.2, 0.85], "BONE_IVORY", rot=[0.0, +16.0, -8.0]))
    head_cubes.append(make_centered_cube([+2.3, 25.4, -4.75], [2.2, 2.2, 0.85], "BONE_IVORY", rot=[0.0, -16.0, +8.0]))
    # 2 vệt gờ xương ngà chạy dọc 2 bên sống mũi lên trán (tạo khe mắt dữ tợn đúng ảnh mẫu!)
    head_cubes.append(make_centered_cube([-1.65, 29.8, -4.72], [0.85, 3.2, 0.55], "BONE_IVORY", rot=[0.0, 0.0, -6.0]))
    head_cubes.append(make_centered_cube([+1.65, 29.8, -4.72], [0.85, 3.2, 0.55], "BONE_IVORY", rot=[0.0, 0.0, +6.0]))
    # 2 gai gáy nhỏ hai bên thái dương dưới (nhìn cực rõ ở góc nghiêng trái/phải mẫu!)
    for s in (-1.0, 1.0):
        head_cubes.append(make_centered_cube([s * 4.9, 26.6, +2.6], [1.2, 1.2, 2.6], "STEEL_TRIM", rot=[-16.0, s * 14.0, 0.0]))

    # CẶP SỪNG CONG ĐỎ HUYẾT DÀY MỌC TỪ 2 BÊN THÁI DƯƠNG UỐN LÊN RỒI VÒNG RA SAU CÓ CHẺ GAI NHỎ Ở CHÓP!
    for s in (-1.0, 1.0):
        # Đế sừng bọc vòng thép đen ngay thái dương
        head_cubes.append(make_centered_cube([s * 5.2, 29.6, -1.0], [1.9, 2.5, 2.5], "STEEL_DARK", rot=[0.0, 0.0, s * -10.0]))
        # Đốt 1: phóng ngang ra ngoài + vểnh lên
        head_cubes.append(make_centered_cube([s * 6.4, 30.8, -1.5], [2.4, 2.5, 2.4], "HORN_CRIMSON", rot=[-14.0, s * -8.0, s * -24.0]))
        # Đốt 2: uốn cong vút lên cao về phía trước-trên (khớp kín đốt 1 & 3)
        head_cubes.append(make_centered_cube([s * 6.8, 32.8, -1.1], [2.2, 3.0, 2.4], "HORN_CRIMSON", rot=[+18.0, 0.0, s * -8.0]))
        # Đốt 3: vòng cong ngược ra phía sau đỉnh đầu
        head_cubes.append(make_centered_cube([s * 6.5, 34.4, +0.6], [1.95, 2.25, 3.2], "HORN_CRIMSON", rot=[+20.0, s * 6.0, s * 6.0]))
        # Đốt 4: kéo dài vòng về phía sau gáy thuôn nhọn
        head_cubes.append(make_centered_cube([s * 6.0, 34.4, +2.9], [1.55, 1.75, 2.9], "HORN_TIP", rot=[-14.0, s * 12.0, s * 5.0]))
        # Chóp sừng chính + chẻ 2 gai nhỏ ở cuối (khớp 100% ảnh nghiêng trái & phải!)
        head_cubes.append(make_centered_cube([s * 5.6, 33.7, +4.9], [1.05, 1.2, 2.2], "HORN_TIP", rot=[-26.0, s * 16.0, 0.0]))
        head_cubes.append(make_centered_cube([s * 6.2, 35.3, +4.1], [0.9, 1.0, 2.0], "HORN_TIP", rot=[+18.0, s * 10.0, 0.0]))
        # Gờ nanh phụ uốn nhẹ dưới gốc sừng
        head_cubes.append(make_centered_cube([s * 5.7, 28.0, +0.9], [1.15, 1.15, 2.5], "HORN_CRIMSON", rot=[-22.0, s * 16.0, 0.0]))

    # ==================== 2. GIÁP NGỰC & LƯNG (`armorBody`, pivot [0, 24, 0]) ====================
    # Hộp thân Steve: x=[-4..4], y=[12..24], z=[-2..2]
    body_cubes = []
    # Lõi thân giáp thép đen ôm trọn thân (8.9 x 12.4 x 5.0 -> 0 z-fighting!)
    body_cubes.append(make_centered_cube(
        [0.0, 18.0, 0.0], [8.9, 12.2, 5.0], "STEEL_DARK",
        face_mats={"north": "CHEST_PLATE", "south": "STEEL_PLATE"}
    ))
    # Cổ giáp cao bọc quanh cổ
    body_cubes.append(make_centered_cube([0.0, 23.6, +0.4], [8.6, 1.8, 5.4], "STEEL_PLATE"))
    # Tấm giáp ngực chữ V nhiều lớp + gờ kim loại sắc (trước ngực y=18.5..23.2)
    body_cubes.append(make_centered_cube([0.0, 21.3, -2.75], [8.4, 3.6, 1.0], "STEEL_PLATE"))
    body_cubes.append(make_centered_cube([-2.3, 21.8, -3.15], [3.2, 2.2, 0.85], "STEEL_TRIM", rot=[0.0, +10.0, +16.0]))
    body_cubes.append(make_centered_cube([+2.3, 21.8, -3.15], [3.2, 2.2, 0.85], "STEEL_TRIM", rot=[0.0, -10.0, -16.0]))
    # 2 Khóa tròn rune đỏ máu trên ngực/vai giống hệt boss Nightmare Knight!
    body_cubes.append(make_centered_cube([-3.1, 22.3, -3.10], [1.65, 1.65, 0.85], "CLASP_RUNE"))
    body_cubes.append(make_centered_cube([+3.1, 22.3, -3.10], [1.65, 1.65, 0.85], "CLASP_RUNE"))
    # LÕI NĂNG LƯỢNG ĐỎ RỰC TRUNG TÂM NGỰC (nổi khối 3D fullbright!)
    body_cubes.append(make_centered_cube([0.0, 19.6, -3.15], [2.4, 2.6, 0.95], "CORE_CRIMSON", rot=[0.0, 0.0, 45.0]))
    body_cubes.append(make_centered_cube([0.0, 19.6, -3.35], [1.4, 1.6, 0.65], "CORE_CRIMSON"))
    # KHUNG XƯƠNG SƯỜN BỤNG/EO MÀU XÁM XƯƠNG NGÀ (giống ảnh mẫu & boss!)
    body_cubes.append(make_centered_cube([0.0, 15.6, -2.78], [6.4, 3.8, 0.85], "BONE_RIB"))
    for ry in (14.4, 15.7, 17.0):
        body_cubes.append(make_centered_cube([-2.3, ry, -3.05], [2.6, 0.68, 0.55], "BONE_IVORY", rot=[0.0, +12.0, -12.0]))
        body_cubes.append(make_centered_cube([+2.3, ry, -3.05], [2.6, 0.68, 0.55], "BONE_IVORY", rot=[0.0, -12.0, +12.0]))
    # Khóa đai thắt lưng thép đen sắc cạnh + vạt khố trước vải đỏ máu gọn
    body_cubes.append(make_centered_cube([0.0, 12.6, 0.0], [9.3, 1.7, 5.5], "STEEL_PLATE"))
    body_cubes.append(make_centered_cube([0.0, 12.5, -2.95], [2.4, 1.9, 0.9], "STEEL_TRIM", rot=[0.0, 0.0, 45.0]))
    body_cubes.append(make_centered_cube([0.0, 9.6, -2.85], [3.2, 4.4, 0.55], "CAPE_CLOTH", rot=[-6.0, 0.0, 0.0]))
    body_cubes.append(make_centered_cube([0.0, 6.7, -3.05], [2.6, 1.8, 0.45], "CAPE_TORN", rot=[-8.0, 0.0, 0.0]))
    # Tấm giáp cột sống lưng sau (xám xương + đốt thép đen sắc nét, giống góc sau ảnh mẫu!)
    body_cubes.append(make_centered_cube([0.0, 18.2, +2.78], [6.8, 8.6, 0.9], "STEEL_PLATE"))
    for sy in (14.6, 16.5, 18.4, 20.3, 22.0):
        body_cubes.append(make_centered_cube([0.0, sy, +3.25], [2.1, 1.35, 0.95], "BONE_IVORY"))
    # Đai chốt cổ áo choàng bên vai phải sau lưng
    body_cubes.append(make_centered_cube([-2.8, 22.8, +2.85], [4.2, 1.8, 1.1], "STEEL_TRIM", rot=[0.0, 0.0, +12.0]))

    # ==================== 2b. ÁO CHOÀNG RÁCH 1 BÊN VAI PHẢI (`cape_upper/mid/lower` dưới `armorBody`) ====================
    # Rủ chéo từ sau giáp vai phải (-X = -5.6..-0.4) xuống sát gót chân (y=22.5 -> 2.0), vút cong nhẹ ra sau z=+3.4..+6.8 -> 0 xuyên chân!
    cape_upper_cubes = [
        make_centered_cube([-3.0, 19.0, +3.45], [5.4, 7.2, 0.65], "CAPE_CLOTH", rot=[+9.0, +5.0, +4.0]),
        make_centered_cube([-5.4, 19.6, +2.90], [1.2, 5.8, 0.65], "CAPE_CLOTH", rot=[+6.0, +24.0, +8.0]),
    ]
    cape_mid_cubes = [
        make_centered_cube([-3.3, 12.2, +4.55], [5.6, 7.2, 0.60], "CAPE_CLOTH", rot=[+14.0, +4.0, +3.0]),
    ]
    cape_lower_cubes = [
        make_centered_cube([-3.5, 5.7, +6.20], [5.8, 6.8, 0.55], "CAPE_TORN", rot=[+20.0, +3.0, +2.0]),
    ]

    # ==================== 3. TAY PHẢI BẤT ĐỐI XỨNG — VAI GAI KHỔNG LỒ + GĂNG CẲNG TAY (`armorRightArm`, [-5, 22, 0]) ====================
    # Hộp tay phải Steve: x=[-8..-4], y=[12..24], z=[-2..2]
    rarm_cubes = []
    # Giáp vai phải nhiều tầng khổng lồ (3 tầng chồng lớp hầm hố đúng ảnh mẫu!)
    rarm_cubes.append(make_centered_cube([-6.5, 22.7, 0.0], [5.9, 4.8, 5.8], "STEEL_PLATE"))
    rarm_cubes.append(make_centered_cube([-7.6, 24.7, 0.0], [5.8, 2.6, 6.4], "STEEL_TRIM", rot=[0.0, 0.0, -20.0]))
    rarm_cubes.append(make_centered_cube([-8.6, 21.8, 0.0], [2.5, 4.0, 6.0], "STEEL_PLATE", rot=[0.0, 0.0, +14.0]))
    # Đỉnh gai sừng thép nhọn & khóa rune đỏ trên giáp vai phải
    rarm_cubes.append(make_centered_cube([-7.8, 26.2, -1.3], [1.6, 2.8, 1.6], "STEEL_TRIM", rot=[-14.0, 0.0, -28.0]))
    rarm_cubes.append(make_centered_cube([-7.8, 26.2, +1.3], [1.6, 2.8, 1.6], "STEEL_TRIM", rot=[+14.0, 0.0, -28.0]))
    rarm_cubes.append(make_centered_cube([-9.2, 23.6, 0.0], [2.2, 1.6, 2.2], "STEEL_TRIM", rot=[0.0, 0.0, -35.0]))
    rarm_cubes.append(make_centered_cube([-6.8, 22.8, -2.95], [1.8, 1.8, 0.7], "CLASP_RUNE"))
    # Gờ viền xương ngà dưới chân giáp vai phải
    rarm_cubes.append(make_centered_cube([-6.5, 19.9, 0.0], [5.4, 0.9, 5.4], "BONE_IVORY"))
    # KHOẢNG HỞ BẮP TAY (y=17.0..19.4 để hở bắp tay skin/tay áo — khớp ảnh giáp mẫu & boss!)
    # GĂNG GIÁP CẲNG TAY HẦM HỐ CÓ VIỀN GAI (`y=11.6..16.8`)
    rarm_cubes.append(make_centered_cube([-6.0, 14.1, 0.0], [5.1, 5.0, 5.1], "STEEL_PLATE", face_mats={"west": "STEEL_RUNE"}))
    rarm_cubes.append(make_centered_cube([-6.0, 16.5, 0.0], [5.5, 1.1, 5.5], "STEEL_TRIM"))
    rarm_cubes.append(make_centered_cube([-8.8, 15.2, -0.8], [1.3, 2.2, 1.2], "STEEL_TRIM", rot=[0.0, 0.0, -24.0]))
    rarm_cubes.append(make_centered_cube([-8.8, 13.4, +0.9], [1.1, 1.8, 1.1], "BONE_IVORY", rot=[0.0, 0.0, -20.0]))
    rarm_cubes.append(make_centered_cube([-6.0, 12.1, -2.65], [4.6, 1.3, 0.75], "STEEL_TRIM"))

    # ==================== 4. TAY TRÁI — VAI GỌN SẮC CẠNH + GĂNG CẲNG TAY (`armorLeftArm`, [+5, 22, 0]) ====================
    # Hộp tay trái Steve: x=[+4..+8], y=[12..24], z=[-2..2]
    larm_cubes = []
    # Tấm giáp vai trái gọn sắc xếp 2 lớp (nhỏ gọn hơn hẳn vai phải để tạo dáng bất đối xứng cực ngầu!)
    larm_cubes.append(make_centered_cube([+6.2, 22.3, 0.0], [5.1, 3.8, 5.1], "STEEL_PLATE"))
    larm_cubes.append(make_centered_cube([+6.9, 23.7, 0.0], [4.4, 1.5, 5.4], "STEEL_TRIM", rot=[0.0, 0.0, +16.0]))
    larm_cubes.append(make_centered_cube([+8.4, 22.6, 0.0], [1.4, 1.8, 2.0], "STEEL_TRIM", rot=[0.0, 0.0, +30.0]))
    larm_cubes.append(make_centered_cube([+6.2, 20.1, 0.0], [4.9, 0.8, 4.9], "BONE_IVORY"))
    # Khoảng hở bắp tay (y=17.0..19.6)
    # Găng giáp cẳng tay trái hầm hố có viền gai
    larm_cubes.append(make_centered_cube([+6.0, 14.1, 0.0], [5.1, 5.0, 5.1], "STEEL_PLATE", face_mats={"east": "STEEL_RUNE"}))
    larm_cubes.append(make_centered_cube([+6.0, 16.5, 0.0], [5.5, 1.1, 5.5], "STEEL_TRIM"))
    larm_cubes.append(make_centered_cube([+8.8, 15.2, -0.8], [1.3, 2.2, 1.2], "STEEL_TRIM", rot=[0.0, 0.0, +24.0]))
    larm_cubes.append(make_centered_cube([+8.8, 13.4, +0.9], [1.1, 1.8, 1.1], "BONE_IVORY", rot=[0.0, 0.0, +20.0]))
    larm_cubes.append(make_centered_cube([+6.0, 12.1, -2.65], [4.6, 1.3, 0.75], "STEEL_TRIM"))

    # ==================== 5. QUẦN (`armorRightLeg` [-2,12,0] & `armorLeftLeg` [+2,12,0]) ====================
    # Hộp đùi phải: x=[-4..0], y=[4..12], z=[-2..2] | Hộp đùi trái: x=[0..4], y=[4..12], z=[-2..2]
    def build_leg_cubes(side_sign: float):
        # side_sign = -1 (Right Leg), +1 (Left Leg)
        cx = side_sign * 2.0
        cubes = []
        # Lớp đùi lót nâu-xám than tối có dải băng đỏ sẫm (y=4.8..12.2)
        cubes.append(make_centered_cube([cx, 8.6, 0.0], [4.7, 7.6, 4.7], "THIGH_UNDER"))
        # Vạt giáp hông (tassets) xếp lớp thép đen + lót đỏ sẫm ôm ngoài đùi (y=8.2..12.5)
        cubes.append(make_centered_cube([cx + side_sign * 2.35, 10.6, 0.0], [1.0, 3.8, 5.0], "STEEL_PLATE", rot=[0.0, 0.0, side_sign * -10.0]))
        cubes.append(make_centered_cube([cx + side_sign * 2.65, 8.4, 0.0], [0.85, 2.4, 4.4], "STEEL_TRIM", rot=[0.0, 0.0, side_sign * -14.0]))
        cubes.append(make_centered_cube([cx, 11.1, -2.55], [4.4, 2.6, 0.75], "STEEL_PLATE", rot=[-8.0, 0.0, 0.0]))
        cubes.append(make_centered_cube([cx, 11.1, +2.55], [4.4, 2.6, 0.75], "STEEL_DARK", rot=[+8.0, 0.0, 0.0]))
        # Tấm giáp đầu gối nổi góc cạnh bằng xám xương ngà + thép đen & khe đỏ mảnh (`y=5.6..8.2`)!
        cubes.append(make_centered_cube([cx, 6.7, -2.65], [3.6, 2.8, 1.05], "KNEE_BONE"))
        cubes.append(make_centered_cube([cx, 7.8, -2.95], [2.4, 1.2, 0.75], "BONE_IVORY", rot=[-14.0, 0.0, 0.0]))
        cubes.append(make_centered_cube([cx + side_sign * 2.35, 6.6, -0.4], [0.85, 2.2, 2.4], "STEEL_TRIM"))
        return cubes

    rleg_cubes = build_leg_cubes(-1.0)
    lleg_cubes = build_leg_cubes(+1.0)

    # ==================== 6. GIÀY GIÁP DÀY (`armorRightBoot` [-2,12,0] & `armorLeftBoot` [+2,12,0]) ====================
    # Vùng cẳng chân/bàn chân: y=[0..5.2]
    def build_boot_cubes(side_sign: float):
        cx = side_sign * 2.0
        cubes = []
        # Thân giày giáp thép đen bọc cổ chân & ống đồng (y=0.0..5.1)
        cubes.append(make_centered_cube([cx, 2.55, 0.0], [5.2, 5.1, 5.2], "STEEL_PLATE", face_mats={"down": "STEEL_DARK"}))
        # Cổ giày viền kim loại & xương ngà sắc cạnh
        cubes.append(make_centered_cube([cx, 4.95, 0.0], [5.6, 1.05, 5.6], "BONE_IVORY"))
        # Mũi giày giáp dày vát nhọn nhô ra trước + gờ cổ chân thép sáng
        cubes.append(make_centered_cube([cx, 1.25, -2.85], [4.6, 2.4, 1.3], "STEEL_TRIM"))
        cubes.append(make_centered_cube([cx, 3.10, -2.72], [3.0, 1.6, 0.75], "STEEL_RUNE"))
        # Gót giày bọc thép đen + gai gót nhỏ
        cubes.append(make_centered_cube([cx, 1.15, +2.75], [4.6, 2.2, 0.85], "STEEL_DARK"))
        cubes.append(make_centered_cube([cx + side_sign * 2.65, 2.4, 0.0], [0.75, 2.6, 2.2], "STEEL_TRIM"))
        return cubes

    rboot_cubes = build_boot_cubes(-1.0)
    lboot_cubes = build_boot_cubes(+1.0)

    bones = [
        {"name": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},

        {"name": "bipedBody", "pivot": [0, 24, 0]},
        {"name": "armorBody", "parent": "bipedBody", "pivot": [0, 24, 0], "cubes": body_cubes},
        {"name": "cape_upper", "parent": "armorBody", "pivot": [-3.0, 22.6, 3.0], "cubes": cape_upper_cubes},
        {"name": "cape_mid",   "parent": "cape_upper", "pivot": [-3.2, 15.6, 3.9], "cubes": cape_mid_cubes},
        {"name": "cape_lower", "parent": "cape_mid",   "pivot": [-3.4, 8.8,  5.0], "cubes": cape_lower_cubes},

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
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, 128x128 UV)")
    return geo, total_cubes


def build_nightmare_armor_animations():
    anims = {
        "animation.nightmare_armor.idle": {
            "loop": True,
            "animation_length": 3.2,
            "bones": {
                "cape_upper": {
                    "rotation": {
                        "0.0": [2.0, 0.0, 0.0],
                        "1.6": [5.0, 1.5, 1.0],
                        "3.2": [2.0, 0.0, 0.0],
                    }
                },
                "cape_mid": {
                    "rotation": {
                        "0.0": [3.0, 0.0, 0.0],
                        "1.6": [7.5, -1.5, 1.5],
                        "3.2": [3.0, 0.0, 0.0],
                    }
                },
                "cape_lower": {
                    "rotation": {
                        "0.0": [4.0, 0.0, 0.0],
                        "1.6": [10.0, 2.0, -1.5],
                        "3.2": [4.0, 0.0, 0.0],
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
    Sinh texture `128x128` RGBA nearest theo đúng bảng màu boss Nightmare Knight:
    - Thép đen obsidian ánh kim (`#14161C / #222630 / #373D4C / #6B758A`), vân xước + AO rãnh khe giáp (0 glow!).
    - Sừng đỏ huyết có khía ngang (`#4D0F16 -> #8E1E27 -> #BA363D`, 0 glow!).
    - Áo choàng & vải hông đỏ máu (`#580D15 -> #8F1B24 -> #B52933`, có mép rách tơi trong suốt, 0 glow!).
    - Mặt nạ, xương sườn & gối xám xương ngà (`#7D766F -> #AEA69C`, rãnh `#3B3632`, 0 glow!).
    - Glowmask chọn lọc: CHỈ mắt đỏ khe mũ, lõi ngực đỏ rực, khóa rune đỏ & khe đỏ mảnh găng/gối!
    """
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    rng = np.random.default_rng(77419)

    def put(key, rgba, g_rgba=None):
        x0, y0, w, h = MAT[key]
        if rgba.shape[2] == 3:
            diff[y0:y0 + h, x0:x0 + w, :3] = np.clip(rgba, 0, 255).astype(np.uint8)
            diff[y0:y0 + h, x0:x0 + w, 3] = 255
        else:
            diff[y0:y0 + h, x0:x0 + w] = np.clip(rgba, 0, 255).astype(np.uint8)
        if g_rgba is not None:
            glow[y0:y0 + h, x0:x0 + w] = np.clip(g_rgba, 0, 255).astype(np.uint8)

    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32)
    noise = rng.random((16, 16)).astype(np.float32)
    grain = 0.5 + 0.5 * np.sin((xx * 0.75 + yy * 0.45) + 1.1 * np.sin(yy * 0.6 - xx * 0.4))
    scratch = (((xx * 3 + yy * 2) % 9) == 0).astype(np.float32)

    S_DEEP  = np.array([20, 22, 28], dtype=np.float32)   # #14161C
    S_MID   = np.array([34, 38, 48], dtype=np.float32)   # #222630
    S_LIGHT = np.array([55, 61, 76], dtype=np.float32)   # #373D4C
    S_SPEC  = np.array([107, 117, 138], dtype=np.float32)# #6B758A

    H_DARK  = np.array([77, 15, 22], dtype=np.float32)   # #4D0F16
    H_MID   = np.array([142, 30, 39], dtype=np.float32)  # #8E1E27
    H_HI    = np.array([186, 54, 61], dtype=np.float32)  # #BA363D

    C_DARK  = np.array([88, 13, 21], dtype=np.float32)   # #580D15
    C_MID   = np.array([143, 27, 36], dtype=np.float32)  # #8F1B24
    C_HI    = np.array([181, 41, 51], dtype=np.float32)  # #B52933

    B_CREV  = np.array([59, 54, 50], dtype=np.float32)   # #3B3632
    B_MID   = np.array([125, 118, 111], dtype=np.float32)# #7D766F
    B_HI    = np.array([174, 166, 156], dtype=np.float32)# #AEA69C

    U_DARK  = np.array([36, 29, 28], dtype=np.float32)   # #241D1C
    U_LIGHT = np.array([56, 45, 42], dtype=np.float32)   # #382D2A

    G_RED   = np.array([255, 36, 48], dtype=np.float32)  # #FF2430 glowing crimson
    G_CORE  = np.array([255, 140, 120], dtype=np.float32)# bright core center

    border = ((xx == 0) | (xx == 15) | (yy == 0) | (yy == 15))[:, :, None]
    inner_bevel = ((xx == 1) | (yy == 1))[:, :, None]

    # 1. STEEL_DARK (#14161C -> #222630) + AO viền sâu
    t = np.clip(grain * 0.6 + noise * 0.4, 0, 1)[:, :, None]
    c = S_DEEP * (1 - t) + S_MID * t + scratch[:, :, None] * 10.0
    c = np.where(border, c * 0.62, c)
    put("STEEL_DARK", c)

    # 2. STEEL_PLATE (#222630 -> #373D4C) + vân xước kim loại + viền cạnh sáng #6B758A + đổ bóng khe giáp
    t = np.clip(grain * 0.65 + noise * 0.35, 0, 1)[:, :, None]
    c = S_MID * (1 - t) + S_LIGHT * t + scratch[:, :, None] * 18.0
    c = np.where(inner_bevel, c * 0.45 + S_SPEC * 0.55, c)
    c = np.where(border, S_DEEP * 0.72, c)
    put("STEEL_PLATE", c)

    # 3. STEEL_TRIM (#373D4C -> #6B758A) cạnh vát gai thép sắc ánh kim
    t = np.clip((15.0 - yy) / 15.0 * 0.55 + grain * 0.30 + noise * 0.15, 0, 1)[:, :, None]
    c = S_LIGHT * (1 - t) + S_SPEC * t
    c = np.where(((xx <= 1) | (yy <= 1))[:, :, None], S_SPEC * 1.18 + 10.0, c)
    c = np.where(((xx == 15) | (yy == 15))[:, :, None], S_DEEP * 0.85, c)
    put("STEEL_TRIM", c)

    # 4. STEEL_RUNE — Thép đen + 1 khe rune đỏ siêu mảnh giữa (chỉ khe đỏ glow!)
    c = S_MID * 0.7 + S_LIGHT * 0.3 + scratch[:, :, None] * 14.0
    c = np.where(border, S_DEEP * 0.72, c)
    slit = ((np.abs(xx - 7.5) <= 1.0) & (yy >= 3) & (yy <= 12))[:, :, None]
    c = np.where(slit, G_RED, c)
    g = np.zeros((16, 16, 4), dtype=np.float32)
    g[:, :, :3] = np.where(slit, G_RED, 0.0)
    g[:, :, 3] = np.where(slit[:, :, 0], 235.0, 0.0)
    put("STEEL_RUNE", c, g)

    # 5. HORN_CRIMSON — Sừng đỏ huyết có khía vòng ngang (#4D0F16 -> #8E1E27 -> #BA363D)
    ring = 0.5 + 0.5 * np.sin(yy * 1.85)
    t = np.clip(ring * 0.72 + noise * 0.28, 0, 1)[:, :, None]
    c = H_DARK * (1 - t) + H_MID * t
    ridge = (ring > 0.78)[:, :, None]
    c = np.where(ridge, H_MID * 0.4 + H_HI * 0.6, c)
    put("HORN_CRIMSON", c)

    # 6. HORN_TIP — Chóp sừng đỏ sáng sắc hơn
    ring = 0.5 + 0.5 * np.sin(yy * 2.1)
    t = np.clip(ring * 0.6 + (15 - yy) / 15.0 * 0.25 + noise * 0.15, 0, 1)[:, :, None]
    c = H_MID * (1 - t) + H_HI * t
    put("HORN_TIP", c)

    # 7. BONE_IVORY — Xám xương ngà phong hóa (#7D766F -> #AEA69C, rãnh tối #3B3632)
    t = np.clip(grain * 0.55 + noise * 0.45, 0, 1)[:, :, None]
    c = B_MID * (1 - t) + B_HI * t
    crack = (((xx + yy * 2) % 11 == 0) & (noise > 0.45))[:, :, None]
    c = np.where(crack, B_CREV, c)
    c = np.where(border, B_CREV * 0.85, c)
    put("BONE_IVORY", c)

    # 8. BONE_RIB — Khung xương sườn bụng ngang sáng xám ngà trên nền rãnh tối
    rib = ((yy % 4) <= 1.5) & (xx >= 2) & (xx <= 13)
    spine_mid = (np.abs(xx - 7.5) <= 1.2)
    c = np.where((rib | spine_mid)[:, :, None], B_HI * 0.72 + B_MID * 0.28, S_DEEP * 0.78)
    c *= (0.90 + 0.18 * noise[:, :, None])
    put("BONE_RIB", c)

    # 9. CAPE_CLOTH — Vải áo choàng đỏ máu / đỏ rượu sẫm có nếp gấp dọc + mép sậm
    fold = 0.5 + 0.5 * np.sin(xx * 1.15 + yy * 0.15)
    t = np.clip(fold * 0.68 + noise * 0.32, 0, 1)[:, :, None]
    c = C_DARK * (1 - t) + C_MID * t
    c = np.where((fold > 0.80)[:, :, None], C_MID * 0.5 + C_HI * 0.5, c)
    c = np.where(((xx == 0) | (xx == 15))[:, :, None], C_DARK * 0.65, c)
    put("CAPE_CLOTH", c)

    # 10. CAPE_TORN — Đuôi áo choàng rách tơi phía dưới (có răng rách trong suốt alpha=0!)
    rgba = np.zeros((16, 16, 4), dtype=np.float32)
    fold = 0.5 + 0.5 * np.sin(xx * 1.25)
    t = np.clip(fold * 0.65 + noise * 0.35, 0, 1)[:, :, None]
    col = C_DARK * (1 - t) + C_MID * t
    col *= np.clip(1.05 - (yy / 16.0) * 0.38, 0.55, 1.05)[:, :, None]
    torn_edge = 11.2 + 2.8 * np.sin(xx * 1.35) + 1.4 * np.cos(xx * 2.9)
    alpha = np.where(yy <= torn_edge, 255.0, 0.0)
    rgba[:, :, :3] = col
    rgba[:, :, 3] = alpha
    put("CAPE_TORN", rgba)

    # 11. THIGH_UNDER — Quần lót đùi nâu-xám than (#241D1C -> #382D2A) + dải băng đỏ sẫm
    t = np.clip(grain * 0.5 + noise * 0.5, 0, 1)[:, :, None]
    c = U_DARK * (1 - t) + U_LIGHT * t
    band = ((yy >= 6) & (yy <= 8))[:, :, None]
    c = np.where(band, C_DARK * 0.75 + C_MID * 0.25, c)
    c = np.where(border, U_DARK * 0.70, c)
    put("THIGH_UNDER", c)

    # 12. CHEST_PLATE — Giáp ngực chữ V xếp lớp thép đen ánh kim
    v_line = (np.abs(np.abs(xx - 7.5) - (13.0 - yy) * 0.55) < 1.2)[:, :, None]
    c = S_MID * 0.55 + S_LIGHT * 0.45 + scratch[:, :, None] * 16.0
    c = np.where(v_line, S_SPEC * 0.92, c)
    c = np.where(border, S_DEEP * 0.70, c)
    put("CHEST_PLATE", c)

    # 13. HELM_VISOR — Mặt trước mũ thép đen + KHE MẮT ĐỎ RỰC DỮ TỢN (chỉ 2 khe mắt glow fullbright!)
    c = S_MID * 0.6 + S_LIGHT * 0.4 + scratch[:, :, None] * 14.0
    c = np.where(border, S_DEEP * 0.65, c)
    # Hốc tối khe mũ
    visor_slit = ((yy >= 6) & (yy <= 9) & (xx >= 2) & (xx <= 13))[:, :, None]
    c = np.where(visor_slit, S_DEEP * 0.42, c)
    # 2 đôi mắt đỏ rực xếch dữ tợn
    eye_l = ((yy >= 7) & (yy <= 8) & (xx >= 3) & (xx <= 5))
    eye_r = ((yy >= 7) & (yy <= 8) & (xx >= 10) & (xx <= 12))
    eyes = (eye_l | eye_r)[:, :, None]
    eye_core = ((yy == 7) & ((xx == 4) | (xx == 11)))[:, :, None]
    c = np.where(eyes, G_RED, c)
    c = np.where(eye_core, G_CORE, c)
    g = np.zeros((16, 16, 4), dtype=np.float32)
    g[:, :, :3] = np.where(eye_core, G_CORE, np.where(eyes, G_RED, 0.0))
    g[:, :, 3] = np.where(eyes[:, :, 0], 255.0, 0.0)
    put("HELM_VISOR", c, g)

    # 14. CORE_CRIMSON — Lõi năng lượng đỏ rực trung tâm ngực (fullbright!)
    dist = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2)
    t = np.clip(1.0 - dist / 7.5, 0, 1)[:, :, None]
    c = H_DARK * (1 - t) + G_RED * t
    c = np.where((dist < 3.0)[:, :, None], G_CORE, c)
    c = np.where(border, S_DEEP, c)
    g = np.zeros((16, 16, 4), dtype=np.float32)
    mask = (dist < 6.8)
    g[:, :, :3] = np.where(mask[:, :, None], c, 0.0)
    g[:, :, 3] = np.where(mask, 255.0, 0.0)
    put("CORE_CRIMSON", c, g)

    # 15. CLASP_RUNE — Khóa tròn rune đỏ máu trên vai/ngực bọc thép đen (tâm tròn đỏ glow!)
    dist = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2)
    in_ring = (dist <= 5.5) & (dist >= 3.6)
    in_gem = (dist < 3.6)
    c = np.where(in_ring[:, :, None], S_SPEC * 0.95, S_DARK := S_DEEP * 0.9)
    c = np.where(in_gem[:, :, None], G_RED * 0.85 + C_HI * 0.15, c)
    c = np.where((dist < 1.8)[:, :, None], G_CORE, c)
    g = np.zeros((16, 16, 4), dtype=np.float32)
    g[:, :, :3] = np.where(in_gem[:, :, None], c, 0.0)
    g[:, :, 3] = np.where(in_gem, 245.0, 0.0)
    put("CLASP_RUNE", c, g)

    # 16. KNEE_BONE — Giáp gối thép đen viền xám xương ngà + khe đỏ mảnh ở giữa!
    c = S_MID * 0.55 + S_LIGHT * 0.45
    c = np.where(((xx <= 2) | (xx >= 13) | (yy <= 2))[:, :, None], B_HI * 0.7 + B_MID * 0.3, c)
    slit = ((np.abs(xx - 7.5) <= 1.0) & (yy >= 5) & (yy <= 11))[:, :, None]
    c = np.where(slit, G_RED, c)
    c = np.where(border, S_DEEP * 0.72, c)
    g = np.zeros((16, 16, 4), dtype=np.float32)
    g[:, :, :3] = np.where(slit, G_RED, 0.0)
    g[:, :, 3] = np.where(slit[:, :, 0], 225.0, 0.0)
    put("KNEE_BONE", c, g)

    out_dir = ASSETS / "textures" / "armor"
    out_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(out_dir / "nightmare_armor.png")
    Image.fromarray(glow, "RGBA").save(out_dir / "nightmare_armor_glowmask.png")
    print("TEX_ARMOR_OK: nightmare_armor.png + selective glowmask (128x128 RGBA nearest)")
    return diff.astype(np.float32), glow.astype(np.float32)


def build_six_item_icons_and_models():
    """Sinh 6 icon pixel-art `16x16` RGBA sắc nét (viền tối 1px) + 6 file `models/item/*.json`."""
    PAL = {
        ".": (0, 0, 0, 0),
        "O": (12, 11, 16, 255),     # Outline tối
        "d": (20, 22, 28, 255),     # Thép đen sâu #14161C
        "s": (42, 47, 59, 255),     # Thép obsidian #2A2F3B
        "m": (72, 80, 98, 255),     # Thép sáng #485062
        "h": (118, 128, 150, 255),  # Ánh kim #768096
        "b": (146, 138, 129, 255),  # Xương ngà #928A81
        "B": (182, 174, 164, 255),  # Xương sáng #B6AEA4
        "r": (112, 20, 28, 255),    # Đỏ huyết sẫm #70141C
        "R": (182, 36, 48, 255),    # Đỏ máu #B62430
        "g": (255, 42, 54, 255),    # Đỏ phát sáng #FF2A36
        "c": (255, 162, 138, 255),  # Tâm lõi chói #FFA28A
    }

    SPRITES = {
        "nightmare_core": [
            "......O..O......",
            ".....Oh..hO.....",
            "....OsmOOmsO....",
            "...OdsrRRrsdO...",
            "..OhsrRggRrsdO..",
            ".OsmrRgccgRrmdO.",
            ".OdrRgcccgRrsdO.",
            "..OdrgcccgrsdO..",
            "..OdsRgggRrsdO..",
            ".OsmrRgggRrmdO..",
            ".OhsrRRggRrsdO..",
            "..OdsrRRRrsdO...",
            "...OdsrRrsdO....",
            "....OsmOOmsO....",
            ".....Oh..hO.....",
            "......O..O......",
        ],
        "nightmare_armor_shard": [
            ".........OO.....",
            "........OhmO....",
            ".......OhsmO....",
            "......OhssdO....",
            ".....OhsrRsdO...",
            "....OhssRrsdO...",
            "...OhsmRrsddO...",
            "...OmsmrssddO...",
            "..OhsmmsddO.....",
            "..OmsRrsdO......",
            "..OdrrsddO......",
            "...OdsddO.......",
            "...OdsdO........",
            "....OdO.........",
            ".....O..........",
            "................",
        ],
        "nightmare_helmet": [
            ".OO..........OO.",
            "ORrO...OO...OrRO",
            "OrrO..OhsO..OrrO",
            "OrrOOOmhhmOOOrrO",
            ".OrROdsmmsdORrO.",
            "..OOdsddddsdOO..",
            "...OdsddddsdO...",
            "...OdgObbOgdO...",
            "...OdsOBBOsdO...",
            "...OdsbBBbsdO...",
            "...OdsbBBbsdO...",
            "....OdsbbdsO....",
            "....OdObbOdO....",
            ".....OO..OO.....",
            "................",
            "................",
        ],
        "nightmare_chestplate": [
            ".OOO........OO..",
            "OhmhO......OmsO.",
            "OmsmhO....OsmsO.",
            "OmsmsOOOOOOsmsO.",
            "OdrsmmRddRmsmdO.",
            ".OOdsmgddgmsdOO.",
            "..OdsdgccgdsdO..",
            "..OdRdggggdsdO..",
            "..ORrObbbbOsdO..",
            "..ORrOdBBdOsdO..",
            "..ORrObbbbOsdO..",
            "..ORrOdBBdOsdO..",
            "..ORROsmmsOsdO..",
            "..ORrOdsddOsdO..",
            "..OrO.OOOO.OO...",
            "..OO............",
        ],
        "nightmare_leggings": [
            "................",
            "...OOOOOOOOOO...",
            "..OmsmmRRmmsmO..",
            "..OdsddRRddsdO..",
            "..OmsddRRddsmO..",
            "..OdsdOrrOdsdO..",
            "..OdsdOrrOdsdO..",
            "..OmsdO..OdsmO..",
            "..ObBdO..OdBbO..",
            "..ObgdO..OdgbO..",
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
            ".ObBBbO..ObBBbO.",
            ".OdsmdO..OdmsdO.",
            ".OdsgdO..OdgsdO.",
            ".OdsmdO..OdmsdO.",
            ".OdsmdO..OdmsdO.",
            "OhsmmdO..OdmmsdO",
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

        model_json = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"abyssalrpg:item/{name}"}
        }
        (model_dir / f"{name}.json").write_text(json.dumps(model_json, indent=2), encoding="utf-8")

    print(f"ICONS_OK: Generated {len(icons)} 16x16 icons + item model JSONs")
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


def build_steve_underbody_faces():
    """Thân Steve tối giản bên trong để xác nhận 0 z-fighting & thấy rõ khoảng hở bắp tay + găng cẳng tay!"""
    boxes = [
        ((-4, 24, -4), (4, 32, 4), (165, 128, 104)),   # Đầu
        ((-4, 12, -2), (4, 24, 2), (45, 42, 48)),      # Thân
        ((-8, 12, -2), (-4, 24, 2), (162, 124, 100)),  # Tay phải (hở bắp tay rõ nét!)
        ((+4, 12, -2), (+8, 24, 2), (162, 124, 100)),  # Tay trái
        ((-4, 0, -2),  (0, 12, 2),  (32, 28, 34)),     # Chân phải
        ((0, 0, -2),   (+4, 12, 2), (32, 28, 34)),     # Chân trái
    ]
    faces = []
    for (x0, y0, z0), (x1, y1, z1), rgb in boxes:
        corners = np.array([
            [x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
            [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1],
        ], dtype=np.float64)
        for idxs in ([0, 1, 2, 3], [5, 4, 7, 6], [4, 0, 3, 7], [1, 5, 6, 2], [3, 2, 6, 7], [4, 5, 1, 0]):
            faces.append((corners[idxs], None, rgb))
    return faces


def render_multiview_preview(geo: dict, diff_img, glow_img, icons: dict):
    """Dựng bảng preview 6 ô: Chính diện, Sau lưng, Nghiêng Phải, Nghiêng Trái, Nhìn Từ Trên + Bản Đêm Glowmask & 6 Icon!"""
    armor_faces = []
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
                ("west", [4, 0, 3, 7]), ("east", [1, 5, 6, 2]),
                ("up", [3, 2, 6, 7]), ("down", [4, 5, 1, 0]),
            ):
                armor_faces.append((corners[idxs], uv_map[fname], None))

    full_faces = build_steve_underbody_faces() + armor_faces
    ctr = np.array([0.0, 17.2, 0.4], dtype=np.float64)

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    def raster(faces, view_mat, w, h, sc, center, ambient=0.50, bg=(20, 22, 28)):
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.36, 0.72, -0.58], dtype=np.float64)
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
                u1, v1 = u0 + max(1, uw) - 0.01, v0 + max(1, uh) - 0.01
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
                    a_d = diff_img[v, u, 3]
                    vis = a_d > 128
                    if not np.any(vis):
                        continue
                    cy_all, cx_all = np.where(cand)
                    cy_i, cx_i = cy_all[vis], cx_all[vis]
                    uv_u, uv_v = u[vis], v[vis]
                    sub_z[cy_i, cx_i] = z[cy_i, cx_i]
                    d_c = diff_img[uv_v, uv_u, :3]
                    g_c = glow_img[uv_v, uv_u, :3]
                    g_a = glow_img[uv_v, uv_u, 3:4] / 255.0
                    lit = d_c * shade * (1.0 - g_a * 0.90) + g_c * (g_a * 1.15)
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 380, 400
    im_front = raster(full_faces, cam_mat(180, 6),   pw, ph, 8.8, ctr, ambient=0.52, bg=(22, 24, 30))
    im_back  = raster(full_faces, cam_mat(0,   6),   pw, ph, 8.8, ctr, ambient=0.52, bg=(22, 24, 30))
    im_right = raster(full_faces, cam_mat(90,  6),   pw, ph, 8.8, ctr, ambient=0.52, bg=(22, 24, 30))
    im_left  = raster(full_faces, cam_mat(-90, 6),   pw, ph, 8.8, ctr, ambient=0.52, bg=(22, 24, 30))
    im_top   = raster(full_faces, cam_mat(180, 72),  pw, ph, 9.6, np.array([0.0, 25.5, 0.0]), ambient=0.52, bg=(22, 24, 30))
    im_night = raster(full_faces, cam_mat(162, 8),   pw, ph, 8.8, ctr, ambient=0.14, bg=(7, 8, 12))

    # Dải icon 6 vật phẩm dưới cùng (1140 x 150)
    strip_h = 150
    icon_strip = Image.new("RGB", (pw * 3, strip_h), (18, 20, 26))
    dr_i = ImageDraw.Draw(icon_strip)
    order = [
        ("nightmare_core", "CORE (5%)"),
        ("nightmare_armor_shard", "SHARD (6-10)"),
        ("nightmare_helmet", "HELMET"),
        ("nightmare_chestplate", "CHESTPLATE"),
        ("nightmare_leggings", "LEGGINGS"),
        ("nightmare_boots", "BOOTS"),
    ]
    slot_w = (pw * 3) // 6
    for idx, (key, label) in enumerate(order):
        sx = idx * slot_w + (slot_w - 88) // 2
        dr_i.rectangle([sx - 4, 24, sx + 92, 120], fill=(34, 34, 42), outline=(92, 52, 60))
        big = icons[key].resize((80, 80), Image.NEAREST)
        icon_strip.paste(big, (sx + 4, 32), big)
        dr_i.text((sx + 2, 126), label, fill=(235, 190, 195))

    sheet = Image.new("RGB", (pw * 3, ph * 2 + strip_h), (10, 11, 15))
    sheet.paste(im_front, (0, 0))
    sheet.paste(im_back,  (pw, 0))
    sheet.paste(im_right, (pw * 2, 0))
    sheet.paste(im_left,  (0, ph))
    sheet.paste(im_top,   (pw, ph))
    sheet.paste(im_night, (pw * 2, ph))
    sheet.paste(icon_strip, (0, ph * 2))

    dr = ImageDraw.Draw(sheet)
    dr.text((10, 10), "FRONT (OBSIDIAN STEEL + BONE RIBS + CORE)", fill=(255, 130, 138))
    dr.text((pw + 10, 10), "BACK (SPINE PLATES + RIGHT CRIMSON CAPE)", fill=(255, 130, 138))
    dr.text((pw * 2 + 10, 10), "RIGHT SIDE (MASSIVE PAULDRON + CAPE + HORN)", fill=(255, 130, 138))
    dr.text((10, ph + 10), "LEFT SIDE (SLEEK PAULDRON + BICEP GAP + GAUNTLET)", fill=(255, 130, 138))
    dr.text((pw + 10, ph + 10), "TOP VIEW (CREST + CURVED CRIMSON HORNS)", fill=(255, 130, 138))
    dr.text((pw * 2 + 10, ph + 10), "NIGHT SELECTIVE GLOWMASK (EYES/CORE/RUNES)", fill=(255, 130, 138))

    out_preview = PREVIEW_DIR / "nightmare_armor_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")


if __name__ == "__main__":
    geo_data, cubes = build_nightmare_armor_geo()
    build_nightmare_armor_animations()
    diff_img, glow_img = build_nightmare_armor_textures_128x128()
    icons = build_six_item_icons_and_models()
    render_multiview_preview(geo_data, diff_img, glow_img, icons)
    print(f"ALL_DONE: Nightmare Knight Armor generated ({cubes} cubes, 128x128 selective glowmask, 6 icons)")
