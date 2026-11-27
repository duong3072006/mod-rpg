#!/usr/bin/env python3
"""
Bộ sinh 1:1 **Cerberus Sleek Blackstone & Obsidian Armor — 4-Angle Turnaround Edition** (`abyssalrpg`):
Bám sát ảnh Turnaround 4 góc (0% chi tiết màu nâu, 0% màu vàng/gold):
  1. Xóa triệt để toàn bộ chi tiết màu nâu:
     - Bỏ hẳn đai bụng nâu và toàn bộ sừng nâu trên mũ/vai/ủng; kéo dài các tấm giáp đá đen Blackstone liền mạch xuống hết gấu áo.
  2. Thu gọn Mũ giáp (`armorHead` + `cerberus_ear_r/l`, chuẩn Netherite `8x8x8` inflate `1.0F`):
     - Khung mũ gọn gàng, má giáp ôm gọn sát mặt, mõm gọn có đúng 2 răng nanh trắng ngắn, mắt đỏ phát sáng (emissive),
       sống vảy đá chạy dọc sau gáy, và **DUY NHẤT 1 cặp sừng/tai đá đen Obsidian** vuốt gọn sắc ra sau (viền highlight `1px` ánh tím).
  3. Áo giáp & Vai (`armorBody`, `cerberus_magma_core`, `armorRightArm/LeftArm`, `cerberus_shoulder_head_r/l` — `1.0F`):
     - Mặt trước gồm **4 tấm giáp đá đen Blackstone ốp nổi `+0.6px`** quanh lõi magma đỏ cam (kéo liền mạch xuống gấu áo),
       dây xích sắt 3D bắt chéo chữ X nổi tách lớp trên cùng.
     - Vai giáp ngắn `1.0F` hở hoàn toàn cẳng tay, gắn 2 đầu Cerberus đá đen nhỏ gọn trên cầu vai:
       **1 bên mắt tím phát sáng, 1 bên mắt đỏ cam phát sáng** (tai/sừng nhỏ đá đen Obsidian vuốt gọn, không sừng nâu).
     - Mặt lưng có sống vảy đá dọc ở giữa kèm khe nứt magma hẹp và xích chữ X.
  4. Quần (`0.5F`) & Ủng (`1.0F`):
     - Quần ôm lớp trong `0.5F` đá đen Blackstone nứt mạch magma đỏ cam + xích hông + ốp đầu gối nhọn.
     - Cổ ủng phồng `1.0F` đồng bộ đá đen Blackstone, mũi ủng gắn móng vuốt đen Obsidian, gót ủng có lửa pixel phát sáng (emissive).

Chạy: python tools/generate_cerberus_armor_model.py
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "cerberus_armor_previews"

TEX_W = 256
TEX_H = 256

REG = {
    # Hàng 0 (y=0..18): Toàn bộ vật liệu đá đen Blackstone, Obsidian ánh tím, xích sắt tối, mắt đỏ/tím, nanh, magma (0% NÂU, 0% VÀNG!)
    "NETH_DARK":        (0,    0,  16, 16),
    "NETH_MID":         (18,   0,  16, 16),
    "NETH_PLATE":       (36,   0,  16, 16),
    "OBSIDIAN_HORN":    (54,   0,  16, 16),
    "OBSIDIAN_TIP":     (72,   0,  14, 14),
    "IRON_CHAIN":       (88,   0,  14, 14),
    "IRON_CLASP":       (104,  0,  14, 14),
    "CLAW_OBSIDIAN":    (120,  0,  14, 14),
    "DORSAL_SPIKE":     (136,  0,  14, 14),
    "EYE_RED":          (152,  0,  10,  6),
    "EYE_SMALL_RED":    (164,  0,   8,  5),
    "EYE_SMALL_PURPLE": (164,  7,   8,  5),
    "FANG_IVORY":       (174,  0,   6, 10),
    "NOSE_DARK":        (182,  0,  10,  6),
    "MAGMA_CORE":       (194,  0,  16, 16),
    "MAGMA_CRACK":      (212,  0,  14, 16),
    "BOOT_SOLE":        (228,  0,  12, 12),
    "WAIST_BELT":       (242,  0,  12, 14),

    # Hàng 1 (y=20..52): Các tấm giáp chính độ phân giải cao
    "CHEST_FRONT":      (0,   20,  22, 26),
    "CHEST_BACK":       (24,  20,  22, 26),
    "CHEST_SIDE":       (48,  20,  14, 26),
    "HELM_TOP":         (64,  20,  18, 18),
    "HELM_SIDE":        (84,  20,  18, 18),
    "HELM_BACK":        (104, 20,  18, 18),
    "HELM_SNOUT_TOP":   (124, 20,  16, 14),
    "HELM_SNOUT_SIDE":  (142, 20,  16, 12),
    "PAULDRON_PLATE":   (160, 20,  18, 18),
    "BOOT_ANKLE":       (180, 20,  14, 16),
    "LEG_FRONT":        (196, 20,  16, 22),
    "LEG_SIDE":         (214, 20,  16, 22),
    "LEG_BACK":         (232, 20,  16, 22),

    # Hàng 2 (y=52..78): Cổ ủng 1.0F Blackstone, ốp gối nhọn & lửa pixel gót ủng
    "BOOT_FRONT":       (0,   52,  18, 18),
    "BOOT_SIDE":        (20,  52,  18, 18),
    "BOOT_HEEL":        (40,  52,  18, 18),
    "KNEE_PLATE":       (60,  52,  14, 16),
    "FLAME_HEEL":       (106, 54,  12, 16),
    "FLAME_SIDE":       (128, 54,  12, 14),

    # Hàng 3 (y=80..100): 4 tấm giáp ngực Blackstone ốp nổi +0.6px có viền highlight 1px hướng vào lõi magma
    "CHEST_PLATE_R":    (0,   80,  16, 18),
    "CHEST_PLATE_L":    (18,  80,  16, 18),
    "CHEST_PLATE_BEVEL":(36,  80,  10, 18),

    # Hàng 4 (y=104..134): Bắp tay giáp phân tầng, găng cẳng tay lưỡi khuỷu & bàn tay móng vuốt (phong cách ảnh mẫu!)
    "ARM_BICEP":        (0,  104,  16, 16),
    "GAUNTLET_FRONT":   (18, 104,  16, 22),
    "GAUNTLET_OUTER":   (36, 104,  16, 22),
    "GAUNTLET_HAND":    (54, 104,  16, 16),
    "BLADE_FIN_SIDE":   (72, 104,  18, 28),
    "BONE_TRIM":        (92, 104,  12, 16),
    "EMPTY_TRANS":      (250, 250,  2,  2),
}


def verify_uv_layout():
    occupied = {}
    for k, (x0, y0, w, h) in REG.items():
        assert x0 >= 0 and y0 >= 0 and x0 + w <= TEX_W and y0 + h <= TEX_H, f"OOB: {k}"
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                assert (x, y) not in occupied, f"UV overlap at ({x},{y}): {occupied[(x, y)]} vs {k}"
                occupied[(x, y)] = k
    for k in ("FLAME_HEEL", "FLAME_SIDE"):
        x0, y0, w, h = REG[k]
        for y in range(max(0, y0 - 4), min(TEX_H, y0 + h + 4)):
            for x in range(max(0, x0 - 4), min(TEX_W, x0 + w + 4)):
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    continue
                assert (x, y) not in occupied, f"Flame padding violated near {k} at ({x},{y})"


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


def matrix_to_bedrock_euler(M):
    ry_rad = math.asin(max(-1.0, min(1.0, -float(M[2, 0]))))
    if abs(math.cos(ry_rad)) > 1e-5:
        rx_rad = -math.atan2(float(M[2, 1]), float(M[2, 2]))
        rz_rad = -math.atan2(float(M[1, 0]), float(M[0, 0]))
    else:
        rx_rad = 0.0
        rz_rad = -math.atan2(-float(M[0, 1]), float(M[1, 1]))
    rot = [math.degrees(rx_rad), math.degrees(ry_rad), math.degrees(rz_rad)]
    assert np.allclose(bedrock_rot_matrix(rot), M, atol=1e-4), f"Euler mismatch for {rot}"
    return rot


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


def place_in_frame(frame_origin, frame_R, local_center, size, mat: str, local_rot=None, face_mats=None) -> dict:
    p0 = np.array(frame_origin, dtype=np.float64)
    lc = np.array(local_center, dtype=np.float64)
    wc = p0 + frame_R @ lc
    R_loc = bedrock_rot_matrix(local_rot) if local_rot is not None else np.eye(3, dtype=np.float64)
    R_world = frame_R @ R_loc
    w_rot = matrix_to_bedrock_euler(R_world)
    return make_centered_cube(tuple(wc), size, mat, rot=w_rot, face_mats=face_mats)


def make_segment_cube(p0, p1, w: float, h: float, mat: str, pad: float = 0.0, face_mats=None) -> dict:
    a = np.array(p0, dtype=np.float64)
    b = np.array(p1, dtype=np.float64)
    d = b - a
    length = float(np.linalg.norm(d))
    if length < 1e-5:
        return make_centered_cube(tuple(a), (w, h, 0.2), mat, face_mats=face_mats)
    ez = d / length
    up = np.array([0.0, 1.0, 0.0], dtype=np.float64)
    if abs(float(np.dot(ez, up))) > 0.96:
        up = np.array([1.0, 0.0, 0.0], dtype=np.float64)
    ex = np.cross(up, ez)
    ex /= np.linalg.norm(ex)
    ey = np.cross(ez, ex)
    ey /= np.linalg.norm(ey)
    M = np.column_stack([ex, ey, ez])
    rot = matrix_to_bedrock_euler(M)
    mid = (a + b) * 0.5
    return make_centered_cube(tuple(mid), (w, h, length + pad), mat, rot=rot, face_mats=face_mats)


def make_flame_plane(center, w: float, h: float, mat: str, rot=None) -> dict:
    return make_centered_cube(
        center, [w, h, 0.04], mat, rot=rot,
        face_mats={
            "north": mat, "south": mat,
            "east": "EMPTY_TRANS", "west": "EMPTY_TRANS",
            "up": "EMPTY_TRANS", "down": "EMPTY_TRANS",
        },
    )


def build_chain_run(p0, p1, link_count: int = 9, link_w: float = 0.78, link_t: float = 0.68) -> list:
    a = np.array(p0, dtype=np.float64)
    b = np.array(p1, dtype=np.float64)
    cubes = []
    for i in range(link_count):
        t0 = i / float(link_count)
        t1 = (i + 1.0) / float(link_count)
        q0 = a + (b - a) * t0
        q1 = a + (b - a) * t1
        if i % 2 == 0:
            cubes.append(make_segment_cube(tuple(q0), tuple(q1), link_w, link_t, "IRON_CHAIN", pad=0.16))
        else:
            cubes.append(make_segment_cube(tuple(q0), tuple(q1), link_t, link_w, "IRON_CLASP", pad=0.16))
    return cubes


def build_cerberus_armor_geo():
    """
    Cấu trúc gọn gàng, sắc sảo, chuẩn bậc phồng Netherite `1.0F / 0.5F`, 0% chi tiết nâu:
      - Helmet (`1.0F`): Khung chuẩn gọn `8x8x8 + 1.0F`, DUY NHẤT 1 cặp sừng/tai Obsidian vuốt gọn ra sau,
        mõm gọn + 2 răng nanh trắng ngắn + mắt đỏ emissive + sống vảy đá dọc sau gáy.
      - Chestplate (`1.0F`): 4 tấm đá đen Blackstone ốp nổi +0.6px kéo dài liền mạch hết gấu áo, xích 3D chữ X,
        vai ngắn hở cẳng tay + 2 đầu Cerberus đá đen nhỏ gọn (1 mắt tím / 1 mắt đỏ cam), lưng có sống vảy đá & khe magma hẹp.
      - Leggings (`0.5F`): Ôm gọn lớp trong, đá đen nứt mạch magma đỏ cam + xích hông + ốp đầu gối nhọn.
      - Boots (`1.0F`): Cổ ủng phồng đá đen Blackstone đồng bộ + móng vuốt Obsidian + lửa gót pixel emissive.
    """
    back_dorsal_rot = matrix_to_bedrock_euler(bedrock_rot_matrix([+14.0, 0.0, 0.0]) @ bedrock_rot_matrix([0.0, 0.0, 45.0]))

    # ==================== 1. MŨ GIÁP ĐẦU SÓI CERBERUS BAO TRỌN 100% ĐẦU (`armorHead` + `cerberus_ear_r/l` + `cerberus_helm_jaw`) ====================
    # Thiết kế full-enclosed beast mask (0% hở mặt người chơi) lấy cảm hứng đầu sói sắc lạnh trong ảnh mẫu:
    # - Bao kín đỉnh đầu, thái dương, má giáp, cằm & cổ; mặt trước là hộp sọ sói + mõm dài vát sắc + hàm dưới há gầm
    #   lộ 2 hàng răng nanh răng cưa sắc nhọn và vòm họng dung nham phát sáng bên trong!
    # - Đôi mắt xếch dữ tợn + vệt mạch magma chéo góc chạy dọc thái dương + mào vây nhọn giữa đỉnh đầu + cặp tai/sừng Obsidian
    #   + bờm gai đá đen vuốt mạnh ra sau (giữ |X| <= 4.8 để tuyệt đối không chạm 2 đầu vai lớn!).
    head_cubes = [
        # 1. Vỏ sọ kín 100% bao trọn đầu Steve 8x8x8 (trên, sau, hai bên, mặt trước & đáy cằm -> 0% hở da!)
        make_centered_cube([0.0, 32.16, -0.15], [8.86, 0.78, 8.80], "NETH_PLATE",
                           face_mats={"up": "HELM_TOP", "north": "NETH_PLATE", "south": "NETH_DARK",
                                      "east": "NETH_PLATE", "west": "NETH_PLATE"}),
        make_centered_cube([0.0, 28.00, +4.12], [8.56, 8.15, 0.62], "NETH_DARK"),
        make_centered_cube([-4.28, 28.05, -0.20], [0.76, 8.10, 8.55], "NETH_DARK",
                           face_mats={"east": "HELM_SIDE", "west": "NETH_DARK"}),
        make_centered_cube([+4.28, 28.05, -0.20], [0.76, 8.10, 8.55], "NETH_DARK",
                           face_mats={"west": "HELM_SIDE", "east": "NETH_DARK"}),
        # Tấm che mặt trong kín khít & giáp cằm/cổ kín hoàn toàn phía dưới (đảm bảo khi hàm há gầm chỉ lộ ánh magma, không lộ mặt Steve!)
        make_centered_cube([0.0, 27.95, -4.18], [8.48, 7.95, 0.52], "NETH_DARK"),
        make_centered_cube([0.0, 23.95, -0.25], [8.38, 0.58, 8.32], "NETH_DARK"),
        make_centered_cube([0.0, 24.55, -4.38], [6.60, 1.85, 1.25], "NETH_DARK"),

        # Vòm họng dung nham đỏ rực sâu sau khe hàm răng cưa (dùng MAGMA_CRACK tương phản sâu để răng nanh ngà nổi bật sắc nét!)
        make_centered_cube([0.0, 26.68, -4.48], [4.65, 2.10, 0.30], "MAGMA_CRACK"),
        make_centered_cube([0.0, 26.58, -4.60], [2.40, 1.05, 0.18], "MAGMA_CORE"),

        # 2. Đỉnh sọ vát 2 bậc + MÀO VÂY GIỮA ĐỈNH ĐẦU thuôn nhọn liền mạch vuốt ngược (0 bậc khối rời!)
        make_centered_cube([0.0, 32.66, -0.58], [4.48, 0.56, 7.80], "NETH_PLATE", rot=[+4.0, 0.0, 0.0]),
        make_centered_cube([0.0, 33.04, -0.92], [2.18, 0.44, 6.40], "NETH_PLATE", rot=[+6.0, 0.0, 0.0]),
        make_segment_cube((0.0, 32.55, -1.55), (0.0, 34.15, +0.20), 0.96, 1.55, "OBSIDIAN_HORN", pad=0.30),
        make_segment_cube((0.0, 34.15, +0.20), (0.0, 35.65, +1.55), 0.68, 1.10, "OBSIDIAN_HORN", pad=0.26),
        make_segment_cube((0.0, 35.65, +1.55), (0.0, 36.85, +2.55), 0.36, 0.58, "OBSIDIAN_TIP",  pad=0.14),
        make_segment_cube((0.0, 32.85, -1.90), (0.0, 35.95, +1.40), 0.22, 0.28, "MAGMA_CRACK",   pad=0.10),

        # 3. Trán giáp vát chữ V + sống trán lao xuống giữa 2 mắt
        make_centered_cube([0.0, 30.88, -4.46], [8.48, 2.38, 0.86], "NETH_PLATE", rot=[+8.0, 0.0, 0.0]),
        make_centered_cube([0.0, 30.18, -4.92], [2.62, 1.80, 0.92], "NETH_PLATE", rot=[+16.0, 0.0, 0.0]),

        # 4. Đôi mắt đỏ xếch dữ tợn EMISSIVE + gờ chân mày vát sắc góc cạnh
        make_centered_cube([-2.52, 30.52, -4.88], [2.72, 0.56, 0.96], "NETH_PLATE", rot=[+10.0, +14.0, +18.0]),
        make_centered_cube([+2.52, 30.52, -4.88], [2.72, 0.56, 0.96], "NETH_PLATE", rot=[+10.0, -14.0, -18.0]),
        make_centered_cube([-2.46, 29.78, -4.82], [2.14, 0.82, 0.24], "EYE_RED",    rot=[0.0, +15.0, +16.0]),
        make_centered_cube([+2.46, 29.78, -4.82], [2.14, 0.82, 0.24], "EYE_RED",    rot=[0.0, -15.0, -16.0]),

        # 5. Mõm sói địa ngục dài vát nhọn + sống mũi cao + chóp mũi Obsidian sắc lạnh (không chấm đỏ mũi!)
        make_centered_cube([0.0, 28.68, -5.62], [4.68, 2.02, 2.75], "HELM_SNOUT_TOP",
                           rot=[+7.0, 0.0, 0.0],
                           face_mats={"up": "HELM_SNOUT_TOP", "north": "NETH_DARK",
                                      "east": "HELM_SNOUT_SIDE", "west": "HELM_SNOUT_SIDE", "down": "NETH_DARK"}),
        make_centered_cube([0.0, 28.34, -7.12], [3.38, 1.50, 2.15], "HELM_SNOUT_TOP",
                           rot=[+9.0, 0.0, 0.0],
                           face_mats={"up": "HELM_SNOUT_TOP", "north": "NETH_DARK",
                                      "east": "HELM_SNOUT_SIDE", "west": "HELM_SNOUT_SIDE", "down": "NETH_DARK"}),
        make_centered_cube([0.0, 29.36, -6.12], [1.92, 0.62, 3.65], "NETH_PLATE", rot=[+11.0, 0.0, 0.0]),
        make_centered_cube([0.0, 28.36, -8.32], [1.62, 0.86, 0.78], "NOSE_DARK",  rot=[+15.0, 0.0, 0.0]),
        make_centered_cube([0.0, 28.44, -8.68], [0.96, 0.52, 0.44], "OBSIDIAN_HORN", rot=[+18.0, 0.0, 0.0]),
        make_centered_cube([0.0, 27.54, -6.05], [2.58, 0.26, 2.95], "MAGMA_CRACK", rot=[+6.0, 0.0, 0.0]),

        # 6. 6 răng nanh hàm trên đan răng cưa dữ tợn
        make_centered_cube([-1.32, 27.14, -7.52], [0.46, 1.24, 0.48], "FANG_IVORY", rot=[+8.0, 0.0, -5.0]),
        make_centered_cube([+1.32, 27.14, -7.52], [0.46, 1.24, 0.48], "FANG_IVORY", rot=[+8.0, 0.0, +5.0]),
        make_centered_cube([-1.82, 27.32, -6.26], [0.44, 1.06, 0.56], "FANG_IVORY", rot=[+12.0, +10.0, 0.0]),
        make_centered_cube([+1.82, 27.32, -6.26], [0.44, 1.06, 0.56], "FANG_IVORY", rot=[+12.0, -10.0, 0.0]),
        make_centered_cube([-2.10, 27.46, -5.14], [0.40, 0.88, 0.50], "FANG_IVORY", rot=[+10.0, +12.0, 0.0]),
        make_centered_cube([+2.10, 27.46, -5.14], [0.40, 0.88, 0.50], "FANG_IVORY", rot=[+10.0, -12.0, 0.0]),

        # 7. Sống gai đá dày sắc chạy dọc giữa bờm sau gáy xuống cổ
        make_centered_cube([0.0, 31.40, +5.25], [1.45, 1.45, 0.82], "DORSAL_SPIKE", rot=back_dorsal_rot),
        make_centered_cube([0.0, 29.10, +5.48], [1.38, 1.38, 0.78], "DORSAL_SPIKE", rot=back_dorsal_rot),
        make_centered_cube([0.0, 26.85, +5.20], [1.22, 1.22, 0.70], "DORSAL_SPIKE", rot=back_dorsal_rot),
        make_centered_cube([0.0, 24.90, +4.78], [1.04, 1.04, 0.62], "DORSAL_SPIKE", rot=back_dorsal_rot),
    ]

    for s in (-1.0, +1.0):
        head_cubes.extend([
            # Vệt năng lượng magma sắc xéo lộ rõ trên thái dương (nổi bên ngoài vách mũ |X|=4.74, quét ngược lên gốc tai!)
            make_segment_cube((s * 3.55, 29.96, -4.68), (s * 4.74, 31.38, -1.85), 0.24, 0.30, "MAGMA_CORE"),
            make_segment_cube((s * 4.74, 31.38, -1.85), (s * 4.68, 30.25, +0.95), 0.22, 0.26, "MAGMA_CRACK"),
            make_segment_cube((s * 4.25, 27.85, -4.55), (s * 4.76, 26.85, -2.05), 0.22, 0.26, "MAGMA_CRACK"),
            # Viền sáng magma mảnh chạy dọc mép môi răng cưa hàm trên
            make_centered_cube([s * 1.72, 27.72, -6.28], [0.22, 0.20, 3.05], "MAGMA_CRACK",
                               rot=[+8.0, s * -10.0, 0.0]),

            # Má giáp vát góc cạnh bọc kín hai bên hàm + sống hàm nối mượt sọ với khớp hàm dưới!
            make_centered_cube([s * 4.02, 27.55, -3.55], [1.14, 3.65, 2.85], "NETH_PLATE",
                               rot=[+5.0, s * -12.0, s * -7.0]),
            make_centered_cube([s * 3.32, 25.45, -4.18], [1.75, 2.75, 1.25], "NETH_MID",
                               rot=[+4.0, s * -14.0, s * +10.0]),
            make_centered_cube([s * 4.46, 26.95, -0.82], [0.80, 2.25, 3.55], "NETH_MID",
                               rot=[+14.0, s * -12.0, s * -8.0]),
            make_segment_cube((s * 4.18, 26.15, -1.45), (s * 3.05, 24.95, -4.75), 0.92, 1.38, "NETH_PLATE", pad=0.18),

            # BỜM GAI SÓI VUỐT MẠNH RA SAU (3 tầng vảy bờm ôm sát + 3 cặp gai bờm 2 đốt vuốt nhọn sắc như lưỡi dao!)
            make_centered_cube([s * 4.18, 31.15, +3.85], [0.84, 2.05, 2.65], "NETH_PLATE",
                               rot=[+18.0, s * +10.0, s * -8.0]),
            make_centered_cube([s * 3.92, 28.80, +4.05], [0.78, 2.00, 2.45], "NETH_PLATE",
                               rot=[+18.0, s * +10.0, s * -6.0]),
            make_centered_cube([s * 3.56, 26.55, +4.02], [0.70, 1.90, 2.05], "NETH_MID",
                               rot=[+16.0, s * +10.0, s * -4.0]),

            make_centered_cube([s * 2.15, 31.10, +4.85], [4.35, 2.10, 0.96], "NETH_PLATE",
                               rot=[+18.0, s * +9.0, s * +8.0]),
            make_centered_cube([s * 1.84, 28.80, +4.92], [3.85, 2.00, 0.92], "NETH_PLATE",
                               rot=[+18.0, s * +9.0, s * +11.0]),
            make_centered_cube([s * 1.44, 26.55, +4.66], [3.10, 1.88, 0.82], "NETH_MID",
                               rot=[+16.0, s * +9.0, s * +14.0]),

            # 3 cặp gai bờm 2 đốt vuốt nhọn sắc (gốc dày -> mũi kim Obsidian, hết hẳn đầu vuông cụt!)
            make_segment_cube((s * 3.15, 31.45, +3.65), (s * 3.65, 32.80, +5.65), 1.04, 1.18, "OBSIDIAN_HORN", pad=0.22),
            make_segment_cube((s * 3.65, 32.80, +5.65), (s * 4.02, 33.85, +7.25), 0.44, 0.52, "OBSIDIAN_TIP",  pad=0.14),

            make_segment_cube((s * 3.35, 29.25, +3.95), (s * 3.82, 30.15, +5.95), 0.94, 1.06, "NETH_PLATE",    pad=0.20),
            make_segment_cube((s * 3.82, 30.15, +5.95), (s * 4.15, 30.85, +7.45), 0.38, 0.44, "OBSIDIAN_TIP",  pad=0.12),

            make_segment_cube((s * 3.00, 27.05, +3.95), (s * 3.45, 27.50, +5.65), 0.80, 0.90, "NETH_MID",      pad=0.18),
            make_segment_cube((s * 3.45, 27.50, +5.65), (s * 3.72, 27.85, +6.90), 0.34, 0.38, "OBSIDIAN_TIP",  pad=0.10),

            # Tia sáng magma mảnh ẩn dọc khe bờm trên
            make_segment_cube((s * 2.55, 31.40, +4.25), (s * 3.15, 32.75, +6.25), 0.20, 0.24, "MAGMA_CRACK"),
        ])

    # HÀM DƯỚI MŨ GIÁP CỬ ĐỘNG (`cerberus_helm_jaw` — hé gầm sắc +9.5° để 2 hàng răng nanh đan khít quanh ánh magma!)
    helm_jaw_piv = [0.0, 26.05, -4.18]
    helm_jaw_R = bedrock_rot_matrix([+9.5, 0.0, 0.0])
    helm_jaw_cubes = [
        place_in_frame(helm_jaw_piv, helm_jaw_R, [0.0, -0.44, -1.88], [3.96, 1.20, 3.66], "NETH_DARK",
                       face_mats={"north": "NETH_PLATE", "east": "NETH_PLATE", "west": "NETH_PLATE", "down": "NETH_DARK"}),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [0.0, -0.76, -3.48], [2.52, 0.90, 1.50], "NETH_PLATE",
                       local_rot=[-12.0, 0.0, 0.0]),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [0.0, +0.16, -1.72], [2.46, 0.28, 2.72], "MAGMA_CRACK"),
        # Viền sáng magma mảnh hai bên hàm dưới (tạo nét phát sáng rực lửa đúng phong cách ảnh mẫu!)
        place_in_frame(helm_jaw_piv, helm_jaw_R, [-1.92, +0.14, -1.90], [0.22, 0.20, 3.05], "MAGMA_CRACK"),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [+1.92, +0.14, -1.90], [0.22, 0.20, 3.05], "MAGMA_CRACK"),
        # 6 răng nanh hàm dưới đan răng cưa sắc bén với hàm trên!
        place_in_frame(helm_jaw_piv, helm_jaw_R, [-0.98, +0.58, -3.18], [0.42, 1.06, 0.44], "FANG_IVORY", local_rot=[-10.0, 0.0, -6.0]),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [+0.98, +0.58, -3.18], [0.42, 1.06, 0.44], "FANG_IVORY", local_rot=[-10.0, 0.0, +6.0]),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [-1.54, +0.50, -2.08], [0.40, 0.92, 0.50], "FANG_IVORY", local_rot=[-8.0, +8.0, 0.0]),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [+1.54, +0.50, -2.08], [0.40, 0.92, 0.50], "FANG_IVORY", local_rot=[-8.0, -8.0, 0.0]),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [-1.84, +0.42, -1.08], [0.36, 0.76, 0.46], "FANG_IVORY", local_rot=[-6.0, +10.0, 0.0]),
        place_in_frame(helm_jaw_piv, helm_jaw_R, [+1.84, +0.42, -1.08], [0.36, 0.76, 0.46], "FANG_IVORY", local_rot=[-6.0, -10.0, 0.0]),
    ]

    # CẶP TAI / SỪNG SÓI OBSIDIAN 4 ĐỐT VUỐT CONG SẮC KÈM GÂN SÁNG (`cerberus_ear_r/l`)
    def build_obsidian_ear_horn(s: float):
        return [
            make_segment_cube(
                (s * 3.45, 32.15, -0.50),
                (s * 3.92, 33.55, +0.98),
                1.34, 0.94, "OBSIDIAN_HORN", pad=0.26,
            ),
            make_segment_cube(
                (s * 3.92, 33.55, +0.98),
                (s * 4.24, 34.98, +2.25),
                1.04, 0.74, "OBSIDIAN_HORN", pad=0.24,
            ),
            make_segment_cube(
                (s * 4.24, 34.98, +2.25),
                (s * 4.44, 36.28, +3.22),
                0.74, 0.54, "OBSIDIAN_HORN", pad=0.20,
            ),
            make_segment_cube(
                (s * 4.44, 36.28, +3.22),
                (s * 4.54, 37.25, +3.82),
                0.42, 0.34, "OBSIDIAN_TIP", pad=0.12,
            ),
            # Viền sáng magma mảnh dọc sống trong tai sói
            make_segment_cube(
                (s * 3.22, 32.45, -0.58),
                (s * 4.18, 35.85, +2.65),
                0.22, 0.24, "MAGMA_CRACK", pad=0.10,
            ),
        ]

    ear_r_cubes = build_obsidian_ear_horn(-1.0)
    ear_l_cubes = build_obsidian_ear_horn(+1.0)

    # ==================== 2. GIÁP NGỰC ÔM GỌN THANH THOÁT — OUTER LAYER 1.0F (`armorBody`) ====================
    body_cubes = [
        # Khung thân áo đá đen Blackstone ôm gọn sát form chuẩn Netherite 1.0F (hết cảm giác ngực thùng bè!)
        make_centered_cube(
            [0.0, 18.05, 0.0], [9.50, 11.95, 5.48], "NETH_MID",
            face_mats={
                "north": "CHEST_FRONT",
                "south": "CHEST_BACK",
                "east":  "CHEST_SIDE",
                "west":  "CHEST_SIDE",
                "up":    "NETH_DARK",
                "down":  "NETH_DARK",
            }
        ),
        make_centered_cube([0.0, 23.75, +0.05], [9.62, 0.66, 5.58], "NETH_DARK", face_mats={"up": "NETH_PLATE"}),
        make_centered_cube([0.0, 12.42, 0.00],  [9.62, 0.66, 5.58], "WAIST_BELT",
                           face_mats={"up": "NETH_DARK", "down": "NETH_DARK"}),

        # Sống vảy đá gọn đầu/cuối khe magma mặt lưng
        make_centered_cube([0.0, 22.85, +3.04], [1.12, 1.12, 0.54], "DORSAL_SPIKE", rot=back_dorsal_rot),
        make_centered_cube([0.0, 13.38, +3.02], [1.06, 1.06, 0.52], "DORSAL_SPIKE", rot=back_dorsal_rot),
    ]
    for s in (-1.0, 1.0):
        # 4 TẤM GIÁP NGỰC ĐÁ ĐEN BLACKSTONE ÔM GỌN NỔI VỪA (`0.58–0.62px`, front -3.44 vs lõi magma -2.90!)
        # ĐÚNG CHIỀU UV GECKOLIB: cạnh trong hướng về x=0 trên bên phải (-X) là `"west"`, bên trái (+X) là `"east"`!
        p_mat = "CHEST_PLATE_R" if s < 0 else "CHEST_PLATE_L"
        in_face = "west" if s < 0 else "east"
        body_cubes.append(make_centered_cube(
            [s * 2.98, 20.52, -3.13], [3.14, 4.32, 0.62], "NETH_DARK",
            rot=[+2.0, s * -4.0, s * -5.5],
            face_mats={"north": p_mat, in_face: "CHEST_PLATE_BEVEL", "up": "NETH_PLATE", "down": "NETH_DARK"}
        ))
        body_cubes.append(make_centered_cube(
            [s * 3.04, 15.42, -3.11], [2.94, 4.52, 0.58], "NETH_DARK",
            rot=[-1.5, s * -3.5, s * +6.0],
            face_mats={"north": p_mat, in_face: "CHEST_PLATE_BEVEL", "up": "NETH_PLATE", "down": "NETH_DARK"}
        ))
        # 4 chốt tán đầu xích gọn sát mép ngực (nằm trọn trong bề rộng thân áo |x| <= 4.38!)
        body_cubes.append(make_centered_cube([s * 4.15, 22.92, -3.42], [0.46, 0.46, 0.72], "IRON_CLASP"))
        body_cubes.append(make_centered_cube([s * 4.10, 13.38, -3.41], [0.46, 0.46, 0.70], "IRON_CLASP"))

        # Mặt lưng: 2 mảng giáp đá đen ôm sát + hàng vảy gai đá mảnh dọc 2 mép khe magma
        body_cubes.append(make_centered_cube(
            [s * 2.92, 18.08, +2.92], [3.42, 9.60, 0.36], "NETH_MID",
            rot=[0.0, s * +3.5, 0.0]
        ))
        for sy in (21.10, 18.80, 16.50, 14.30):
            body_cubes.append(make_centered_cube([s * 1.18, sy, +3.04], [0.58, 0.96, 0.44], "DORSAL_SPIKE",
                                                 rot=[+10.0, s * -12.0, s * +14.0]))

    # Dây xích sắt tối 3D MẢNH GỌN ÔM SÁT GIÁP NGỰC & LƯNG (hết hẳn cảnh xích to lơ lửng hay chĩa ra ngoài hông!)
    body_cubes.extend(build_chain_run((-4.15, 22.92, -3.76), (+4.10, 13.38, -3.74), link_count=11, link_w=0.44, link_t=0.34))
    body_cubes.extend(build_chain_run((+4.15, 22.92, -3.76), (-4.10, 13.38, -3.74), link_count=11, link_w=0.44, link_t=0.34))
    body_cubes.append(make_centered_cube([0.0, 18.15, -4.02], [0.84, 0.84, 0.34], "IRON_CLASP", rot=[0.0, 0.0, 45.0]))

    body_cubes.extend(build_chain_run((-4.10, 22.85, +3.28), (+4.05, 13.45, +3.28), link_count=11, link_w=0.38, link_t=0.28))
    body_cubes.extend(build_chain_run((+4.10, 22.85, +3.28), (-4.05, 13.45, +3.28), link_count=11, link_w=0.38, link_t=0.28))
    body_cubes.append(make_centered_cube([0.0, 18.15, +3.44], [0.68, 0.68, 0.28], "IRON_CLASP", rot=[0.0, 0.0, 45.0]))

    # LÕI MAGMA ĐỎ CAM NGỰC TRƯỚC (`z_front = -2.90`) & KHE MAGMA MẶT LƯNG (`z_back = +2.90`)
    magma_core_cubes = [
        make_centered_cube([0.0, 17.85, -2.83], [2.95, 2.95, 0.14], "MAGMA_CORE", rot=[0.0, 0.0, 45.0]),
        make_centered_cube([0.0, 18.10, -2.82], [1.65, 9.55, 0.14], "MAGMA_CRACK"),
        make_centered_cube([0.0, 23.08, -2.82], [2.15, 0.58, 0.14], "MAGMA_CRACK"),
        make_centered_cube([0.0, 18.10, +2.83], [1.32, 9.50, 0.14], "MAGMA_CRACK"),
    ]

    # ==================== 3. CẦU VAI THANH GỌN + 2 ĐẦU CERBERUS CỠ LỚN (TAM THỦ CÙNG MŨ GIÁP!) ====================
    def place_segment_in_frame(frame_origin, frame_R, p0_loc, p1_loc, w: float, h: float, mat: str, pad: float = 0.0) -> dict:
        p0_arr = np.array(frame_origin, dtype=np.float64)
        a_w = p0_arr + frame_R @ np.array(p0_loc, dtype=np.float64)
        b_w = p0_arr + frame_R @ np.array(p1_loc, dtype=np.float64)
        return make_segment_cube(tuple(a_w), tuple(b_w), w, h, mat, pad=pad)

    def build_arm_and_shoulder_head(s: float):
        # 3 đầu Cerberus: Giữa (Mũ - Alpha mắt đỏ), Vai Phải (-X - Shadow mắt tím), Vai Trái (+X - Fire mắt đỏ cam)!
        eye_mat = "EYE_SMALL_PURPLE" if s < 0 else "EYE_SMALL_RED"
        outer_face = "east" if s < 0 else "west"
        inner_face = "west" if s < 0 else "east"
        cx = s * 6.0
        v_tip_rot = matrix_to_bedrock_euler(
            bedrock_rot_matrix([0.0, 0.0, s * +7.5]) @ bedrock_rot_matrix([45.0, 0.0, 0.0])
        )

        # --- PHẦN TRÊN CÁNH TAY (`armorRightArm` / `armorLeftArm`, Y = 18.0..24.0): Cầu vai + Bắp tay bọc giáp phân tầng ---
        arm_cubes = [
            make_centered_cube([s * 4.72, 22.25, 0.0], [1.30, 3.72, 5.10], "NETH_DARK"),
            # Nắp cầu vai vát nghiêng chữ V (`zRot = ±7.5°`) làm bệ vững cho đầu Cerberus cỡ lớn
            make_centered_cube([s * 7.25, 23.72, 0.0], [4.66, 0.70, 5.95], "NETH_PLATE", rot=[0.0, 0.0, s * +7.5]),
            make_centered_cube(
                [s * 7.16, 22.14, 0.0], [4.46, 3.06, 5.68], "PAULDRON_PLATE",
                rot=[0.0, 0.0, s * +7.5],
                face_mats={"up": "NETH_PLATE", "north": "PAULDRON_PLATE", "south": "PAULDRON_PLATE",
                           "east": "PAULDRON_PLATE", "west": "PAULDRON_PLATE", "down": "NETH_DARK"},
            ),
            # Mũi vát nhọn chữ V dưới cầu vai
            make_centered_cube(
                [s * 7.66, 20.26, 0.0], [2.52, 2.95, 2.95], "PAULDRON_PLATE",
                rot=v_tip_rot,
            ),

            # Ống giáp bắp tay bọc kín 360° (Y = 18.0..22.3 -> 0% hở da bắp tay!)
            make_centered_cube(
                [cx, 20.15, 0.0], [4.68, 4.30, 4.68], "ARM_BICEP",
                face_mats={"north": "ARM_BICEP", "south": "ARM_BICEP",
                           outer_face: "ARM_BICEP", inner_face: "NETH_DARK",
                           "up": "NETH_DARK", "down": "NETH_DARK"},
            ),
            # 2 tầng vảy giáp chéo mặt ngoài bắp tay ôm xuống khuỷu (đồng bộ tầng giáp vai trong ảnh mẫu!)
            make_centered_cube([cx + s * 2.38, 19.75, 0.0], [0.64, 1.50, 4.46], "NETH_PLATE", rot=[0.0, 0.0, s * +12.0]),
            make_centered_cube([cx + s * 2.34, 18.55, 0.0], [0.58, 1.32, 4.24], "NETH_MID",   rot=[0.0, 0.0, s * +9.0]),
        ]

        # --- PHẦN CẲNG TAY & GĂNG LƯỠI ĐAO LƯỠI LIỀM MÓNG VUỐT (`armorRightForearm` / `armorLeftForearm`, pivot = [±5, 18, 0]) ---
        # Dựng lưỡi cánh khuỷu cong trực tiếp trên mặt phẳng XY (chỉ xoay quanh Z -> bản lưỡi rộng sắc nét 100% giống ảnh mẫu, 0 xoắn trục!):
        forearm_cubes = [
            # 1. Khối đệm khuỷu chìm (Y = 17.1..18.9) chống hở khe khi gập khuỷu 90° & không z-fighting khi duỗi thẳng!
            make_centered_cube([cx, 18.00, 0.0], [4.46, 1.80, 4.46], "NETH_DARK"),

            # 2. Giáp cẳng tay chính (`Y = 13.7..18.1`) dày góc cạnh, bọc kín 360°
            make_centered_cube(
                [cx + s * 0.14, 15.88, 0.0], [4.98, 4.42, 4.98], "GAUNTLET_FRONT",
                face_mats={"north": "GAUNTLET_FRONT", "south": "GAUNTLET_FRONT",
                           outer_face: "GAUNTLET_OUTER", inner_face: "NETH_MID",
                           "up": "NETH_DARK", "down": "NETH_DARK"},
            ),
            # Vòng giáp vát cổ tay & khuỷu tay tạo phân khối tầng sắc cạnh (thay thế hoàn toàn chữ X!)
            make_centered_cube([cx + s * 0.18, 17.72, 0.0], [5.20, 0.68, 5.20], "NETH_PLATE"),
            make_centered_cube([cx + s * 0.18, 13.86, 0.0], [5.16, 0.72, 5.16], "NETH_PLATE"),
            # Gờ chữ V tối + chỉ đỏ cam mảnh ở mặt trước cẳng tay
            make_centered_cube([cx + s * 0.25, 16.05, -2.56], [3.25, 2.65, 0.36], "NETH_PLATE", rot=[0.0, 0.0, s * -6.0]),
            make_centered_cube([cx + s * 0.25, 16.05, +2.56], [3.25, 2.65, 0.36], "NETH_PLATE", rot=[0.0, 0.0, s * -6.0]),
            make_centered_cube([cx + s * 0.20, 14.55, -2.62], [2.40, 0.30, 0.24], "BONE_TRIM",  rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx + s * 0.20, 14.55, +2.62], [2.40, 0.30, 0.24], "BONE_TRIM",  rot=[0.0, 0.0, s * -14.0]),

            # 3. KHIÊN NGOÀI CẲNG TAY BẢN RỘNG + ĐINH TÁN BẠC-XƯƠNG (khối nền ôm lưỡi đao cong!)
            make_centered_cube(
                [cx + s * 2.86, 15.95, 0.0], [1.05, 4.75, 3.95], "GAUNTLET_OUTER",
                rot=[0.0, 0.0, s * -9.0],
                face_mats={outer_face: "GAUNTLET_OUTER", inner_face: "NETH_DARK",
                           "north": "NETH_PLATE", "south": "NETH_PLATE", "up": "NETH_PLATE", "down": "NETH_DARK"},
            ),
            # Đinh tán hình thoi bạc đặc trưng trên mặt ngoài giáp khuỷu
            make_centered_cube([cx + s * 3.38, 16.65, -0.10], [0.38, 0.88, 0.88], "BONE_TRIM", rot=[45.0, 0.0, 0.0]),

            # 5. GĂNG TAY MÓNG VUỐT BỌC KÍN BÀN TAY (`Y = 11.9..13.9`, 0% hở da bàn tay!)
            make_centered_cube(
                [cx + s * 0.04, 12.88, 0.0], [4.68, 2.02, 4.68], "GAUNTLET_HAND",
                face_mats={"north": "GAUNTLET_HAND", "south": "GAUNTLET_HAND",
                           outer_face: "GAUNTLET_HAND", inner_face: "NETH_DARK",
                           "up": "NETH_DARK", "down": "NETH_DARK"},
            ),
            # Tấm giáp mu bàn tay sắc cạnh + thanh khớp ngón bạc mảnh
            make_centered_cube([cx + s * 2.46, 12.92, -0.05], [0.46, 1.48, 3.85], "NETH_PLATE", rot=[0.0, 0.0, s * +6.0]),
            make_centered_cube([cx + s * 2.66, 12.36, -0.05], [0.26, 0.34, 3.45], "BONE_TRIM"),
        ]

        # 4. LƯỠI ĐAO LƯỠI LIỀM CÁNH KHUỶU CO RÚT ĐỘC LẬP (`cerberus_arm_blade_r/l`, pivot = [±8.55, 16.0, +0.20])!
        # Mặc định co rút ẩn hoàn toàn trong cẳng tay; khi kích hoạt Strength II (1 phút) sẽ trượt mở mượt ra từ khe giáp ngoài!
        blade_piv = [round(cx + s * 2.55, 3), 16.00, +0.20]
        blade_cubes = [
            # Quy ước Bedrock: rot_z = s * +deg nghiêng đỉnh (+Y) RA NGOÀI (hướng s), s * -deg uốn đỉnh VÀO TRONG!
            # Tầng 1 (gốc lưỡi ôm sát cẳng tay, bản rộng 1.85px, vát nghiêng ra ngoài 22°)
            make_centered_cube([cx + s * 3.62, 16.45, +0.18], [1.85, 3.10, 1.15], "BLADE_FIN_SIDE",
                               rot=[0.0, 0.0, s * +22.0],
                               face_mats={"north": "BLADE_FIN_SIDE", "south": "BLADE_FIN_SIDE",
                                          outer_face: "NETH_PLATE", inner_face: "BONE_TRIM",
                                          "up": "NETH_PLATE", "down": "NETH_DARK"}),
            # Tầng 2 (thân giữa lưỡi cong vồng ngang khuỷu tay, vươn ra |X|=10.5, góc nghiêng +10°)
            make_centered_cube([cx + s * 4.42, 18.85, +0.26], [1.72, 2.75, 0.96], "BLADE_FIN_SIDE",
                               rot=[0.0, 0.0, s * +10.0],
                               face_mats={"north": "BLADE_FIN_SIDE", "south": "BLADE_FIN_SIDE",
                                          outer_face: "NETH_PLATE", inner_face: "BONE_TRIM",
                                          "up": "NETH_PLATE", "down": "NETH_DARK"}),
            # Tầng 3 (đầu lưỡi uốn ngược vào trong thuôn nhọn thành mũi đao lưỡi liềm sắc bén ở Y=21.8!)
            make_centered_cube([cx + s * 4.46, 20.68, +0.32], [1.14, 1.95, 0.72], "OBSIDIAN_HORN",
                               rot=[0.0, 0.0, s * -14.0]),
            make_centered_cube([cx + s * 4.10, 21.85, +0.36], [0.56, 1.10, 0.48], "OBSIDIAN_TIP",
                               rot=[0.0, 0.0, s * -26.0]),

            # Viền lửa magma đỏ cam liền mạch chạy đúng dọc mép ngoài cong của lưỡi đao!
            make_centered_cube([cx + s * 4.46, 16.20, +0.22], [0.24, 2.95, 0.72], "MAGMA_CRACK", rot=[0.0, 0.0, s * +22.0]),
            make_centered_cube([cx + s * 5.26, 18.75, +0.28], [0.24, 2.65, 0.62], "MAGMA_CORE",  rot=[0.0, 0.0, s * +10.0]),
            make_centered_cube([cx + s * 5.00, 20.72, +0.32], [0.22, 1.85, 0.48], "MAGMA_CRACK", rot=[0.0, 0.0, s * -14.0]),

            # Sống gân bạc-xương mảnh sắc uốn cong theo mép trong của lưỡi đao
            make_centered_cube([cx + s * 2.86, 16.75, +0.16], [0.26, 2.85, 0.86], "BONE_TRIM", rot=[0.0, 0.0, s * +20.0]),
            make_centered_cube([cx + s * 3.60, 19.00, +0.24], [0.24, 2.40, 0.70], "BONE_TRIM", rot=[0.0, 0.0, s * +10.0]),

            # Mũi đao thứ 2 (ngạnh dưới) vát nhọn chỉa xuống bảo vệ cổ tay & mu bàn tay
            make_centered_cube([cx + s * 3.44, 14.05, +0.18], [1.28, 2.25, 0.92], "OBSIDIAN_HORN", rot=[0.0, 0.0, s * +18.0]),
            make_centered_cube([cx + s * 3.06, 12.60, +0.18], [0.64, 1.38, 0.60], "OBSIDIAN_TIP",  rot=[0.0, 0.0, s * +26.0]),
            make_centered_cube([cx + s * 4.02, 13.85, +0.20], [0.22, 2.15, 0.56], "MAGMA_CRACK",   rot=[0.0, 0.0, s * +18.0]),
        ]

        # 4 móng vuốt quỷ sắc cong gập nhẹ ở 4 đầu ngón tay + 1 vuốt ngón cái mặt trước!
        for fz in (-1.52, -0.52, +0.52, +1.52):
            forearm_cubes.extend([
                make_centered_cube([cx + s * 2.16, 11.88, fz], [0.56, 0.68, 0.56], "CLAW_OBSIDIAN", rot=[0.0, 0.0, s * -10.0]),
                make_centered_cube([cx + s * 1.98, 11.35, fz], [0.36, 0.72, 0.38], "BONE_TRIM",     rot=[0.0, 0.0, s * -22.0]),
            ])
        # Vuốt ngón cái ôm phía trước bàn tay
        forearm_cubes.extend([
            make_centered_cube([cx - s * 1.25, 11.98, -2.18], [0.54, 0.62, 0.54], "CLAW_OBSIDIAN", rot=[-14.0, 0.0, s * +12.0]),
            make_centered_cube([cx - s * 1.05, 11.48, -2.02], [0.34, 0.64, 0.36], "BONE_TRIM",     rot=[-22.0, 0.0, s * +18.0]),
        ])

        # Đầu Cerberus cỡ lớn hoàn chỉnh trên cầu vai (tư thế gốc hướng thẳng 0° đồng trục với mũ để CerberusArmorModel.java
        # xoay đồng bộ cả 3 đầu nhìn chính xác vào cùng 1 điểm mục tiêu và há hàm đúng trục ở mọi góc nhìn!)
        hp = [s * 8.95, 24.75, -0.35]
        frame_R = np.eye(3, dtype=np.float64)
        sh_dorsal_rot = matrix_to_bedrock_euler(bedrock_rot_matrix([+16.0, 0.0, 0.0]) @ bedrock_rot_matrix([0.0, 0.0, 45.0]))

        sh_head = [
            # 1. Cổ giáp & bờm đá đen 3 tầng nối liền khối xuống cầu vai + sống gai đá sau gáy
            place_in_frame(hp, frame_R, [0.0, -1.25, +0.96], [4.48, 2.20, 3.45], "NETH_DARK"),
            place_in_frame(hp, frame_R, [0.0, +1.02, +2.50], [4.16, 1.48, 1.18], "NETH_PLATE", local_rot=[+14.0, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [0.0, -0.10, +2.66], [3.72, 1.40, 1.10], "NETH_MID",   local_rot=[+16.0, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [0.0, -1.16, +2.60], [3.16, 1.24, 1.00], "NETH_DARK",  local_rot=[+18.0, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [0.0, +2.02, +1.66], [1.02, 1.02, 0.60], "DORSAL_SPIKE", local_rot=sh_dorsal_rot),
            place_in_frame(hp, frame_R, [0.0, +0.98, +2.92], [0.94, 0.94, 0.56], "DORSAL_SPIKE", local_rot=sh_dorsal_rot),
            place_in_frame(hp, frame_R, [0.0, -0.24, +3.02], [0.86, 0.86, 0.52], "DORSAL_SPIKE", local_rot=sh_dorsal_rot),

            # 2. Hộp sọ đá đen Blackstone cỡ lớn + sống vát đỉnh đầu 2 bậc + trán vát sắc
            place_in_frame(hp, frame_R, [0.0, +0.40, +0.36], [5.02, 3.52, 3.92], "NETH_PLATE",
                           face_mats={"up": "HELM_TOP", "east": "HELM_SIDE", "west": "HELM_SIDE", "south": "NETH_DARK"}),
            place_in_frame(hp, frame_R, [0.0, +2.24, +0.12], [3.38, 0.50, 3.62], "NETH_PLATE"),
            place_in_frame(hp, frame_R, [0.0, +2.52, -0.05], [1.72, 0.36, 3.04], "NETH_PLATE"),
            place_in_frame(hp, frame_R, [0.0, +1.48, -1.52], [4.86, 1.36, 0.76], "NETH_PLATE", local_rot=[+8.0, 0.0, 0.0]),

            # 3. Mõm sói địa ngục dài sắc + sống mũi vát + chóp mũi tối + vòm họng magma rực sáng
            place_in_frame(hp, frame_R, [0.0, +0.12, -2.74], [3.24, 1.62, 3.08], "HELM_SNOUT_TOP",
                           local_rot=[+4.5, 0.0, 0.0],
                           face_mats={"up": "HELM_SNOUT_TOP", "north": "NETH_DARK",
                                      "east": "HELM_SNOUT_SIDE", "west": "HELM_SNOUT_SIDE", "down": "NETH_DARK"}),
            place_in_frame(hp, frame_R, [0.0, +0.98, -2.54], [1.68, 0.52, 2.86], "NETH_PLATE", local_rot=[+8.0, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [0.0, +0.28, -4.28], [1.46, 0.72, 0.44], "NOSE_DARK",  local_rot=[+4.5, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [0.0, -0.58, -2.35], [2.26, 0.28, 2.18], "MAGMA_CORE"),

            # 4. Đôi mắt phát sáng cỡ lớn thoáng sáng rõ + gờ chân mày sắc dữ tợn không đè mắt
            place_in_frame(hp, frame_R, [-1.46, +0.82, -1.96], [1.62, 0.78, 0.26], eye_mat,
                           local_rot=[0.0, +13.0, +13.0]),
            place_in_frame(hp, frame_R, [+1.46, +0.82, -1.96], [1.62, 0.78, 0.26], eye_mat,
                           local_rot=[0.0, -13.0, -13.0]),
            place_in_frame(hp, frame_R, [-1.52, +1.48, -1.86], [1.90, 0.46, 0.94], "NETH_PLATE",
                           local_rot=[+10.0, +12.0, +15.0]),
            place_in_frame(hp, frame_R, [+1.52, +1.48, -1.86], [1.90, 0.46, 0.94], "NETH_PLATE",
                           local_rot=[+10.0, -12.0, -15.0]),

            # 5. 4 răng nanh hàm trên sắc bén nổi bật trong khe hàm há gầm
            place_in_frame(hp, frame_R, [-1.18, -0.88, -3.62], [0.42, 0.92, 0.42], "FANG_IVORY", local_rot=[+6.0, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [+1.18, -0.88, -3.62], [0.42, 0.92, 0.42], "FANG_IVORY", local_rot=[+6.0, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [-1.28, -0.78, -2.62], [0.34, 0.64, 0.34], "FANG_IVORY", local_rot=[+6.0, 0.0, 0.0]),
            place_in_frame(hp, frame_R, [+1.28, -0.78, -2.62], [0.34, 0.64, 0.34], "FANG_IVORY", local_rot=[+6.0, 0.0, 0.0]),
        ]

        # Má giáp vát góc cạnh + cặp sừng/tai sói Obsidian 3 đốt cong mượt đồng bộ 100% với mũ!
        for cs in (-1.0, +1.0):
            sh_head.extend([
                place_in_frame(hp, frame_R, [cs * 2.42, -0.26, -0.46], [0.84, 1.88, 2.72], "NETH_PLATE",
                               local_rot=[+4.0, cs * -8.0, cs * -10.0]),
                place_in_frame(hp, frame_R, [cs * 2.68, -0.38, +1.04], [0.62, 1.24, 1.94], "NETH_MID",
                               local_rot=[+10.0, cs * -16.0, cs * -12.0]),
                place_segment_in_frame(hp, frame_R, (cs * 1.74, +2.00, +0.36), (cs * 2.18, +3.26, +1.52), 1.08, 0.78, "OBSIDIAN_HORN", pad=0.22),
                place_segment_in_frame(hp, frame_R, (cs * 2.18, +3.26, +1.52), (cs * 2.46, +4.48, +2.52), 0.80, 0.58, "OBSIDIAN_HORN", pad=0.18),
                place_segment_in_frame(hp, frame_R, (cs * 2.46, +4.48, +2.52), (cs * 2.58, +5.36, +3.12), 0.46, 0.36, "OBSIDIAN_TIP",  pad=0.10),
            ])

        # Hàm dưới hé gầm nhẹ (+13° theo quy ước Bedrock -> mở xuống dưới) lộ rõ 2 hàng răng nanh & họng dung nham phát sáng!
        hp_arr = np.array(hp, dtype=np.float64)
        jaw_local_piv = np.array([0.0, -0.96, -0.86], dtype=np.float64)
        jp = (hp_arr + frame_R @ jaw_local_piv).tolist()
        jaw_R = frame_R @ bedrock_rot_matrix([+13.0, 0.0, 0.0])
        sh_jaw = [
            place_in_frame(jp, jaw_R, [0.0, -0.38, -1.56], [2.76, 0.84, 2.94], "NETH_DARK",
                           face_mats={"north": "NETH_PLATE", "down": "NETH_DARK"}),
            place_in_frame(jp, jaw_R, [0.0, -0.56, -2.74], [2.26, 0.60, 0.96], "NETH_PLATE", local_rot=[-10.0, 0.0, 0.0]),
            place_in_frame(jp, jaw_R, [0.0, +0.08, -1.36], [1.86, 0.26, 2.02], "MAGMA_CORE"),
            place_in_frame(jp, jaw_R, [-0.94, +0.26, -2.54], [0.36, 0.66, 0.36], "FANG_IVORY", local_rot=[-8.0, 0.0, 0.0]),
            place_in_frame(jp, jaw_R, [+0.94, +0.26, -2.54], [0.36, 0.66, 0.36], "FANG_IVORY", local_rot=[-8.0, 0.0, 0.0]),
            place_in_frame(jp, jaw_R, [-1.04, +0.22, -1.68], [0.28, 0.46, 0.28], "FANG_IVORY", local_rot=[-6.0, 0.0, 0.0]),
            place_in_frame(jp, jaw_R, [+1.04, +0.22, -1.68], [0.28, 0.46, 0.28], "FANG_IVORY", local_rot=[-6.0, 0.0, 0.0]),
        ]
        return arm_cubes, forearm_cubes, blade_piv, blade_cubes, hp, sh_head, jp, sh_jaw

    rarm_cubes, rfore_cubes, rblade_piv, rblade_cubes, sh_r_piv, sh_r_head, jaw_r_piv, jaw_r_cubes = build_arm_and_shoulder_head(-1.0)
    larm_cubes, lfore_cubes, lblade_piv, lblade_cubes, sh_l_piv, sh_l_head, jaw_l_piv, jaw_l_cubes = build_arm_and_shoulder_head(+1.0)

    # ==================== 4. QUẦN GIÁP ÔM GỌN — INNER LAYER 0.5F (`armorRightLeg/LeftLeg`) ====================
    knee_rot = matrix_to_bedrock_euler(bedrock_rot_matrix([-10.0, 0.0, 0.0]) @ bedrock_rot_matrix([0.0, 0.0, 45.0]))

    def build_leg_cubes(s: float):
        lx = s * 2.26
        cubes = [
            make_centered_cube(
                [lx, 8.70, 0.0], [4.46, 6.96, 4.92], "LEG_FRONT",
                face_mats={
                    "north": "LEG_FRONT",
                    "south": "LEG_BACK",
                    "east":  "LEG_SIDE",
                    "west":  "LEG_SIDE",
                    "up":    "NETH_DARK",
                    "down":  "NETH_DARK",
                }
            ),
            make_centered_cube([lx, 6.72, -2.56], [1.80, 1.80, 0.40], "KNEE_PLATE", rot=knee_rot),
            make_centered_cube([lx, 6.76, -2.68], [1.02, 1.02, 0.24], "NETH_PLATE", rot=knee_rot),
        ]
        cubes.extend(build_chain_run((s * 0.42, 11.42, -2.56), (s * 4.38, 9.65, -2.44), link_count=6, link_w=0.34, link_t=0.24))
        return cubes

    rleg_cubes = build_leg_cubes(-1.0)
    lleg_cubes = build_leg_cubes(+1.0)

    # ==================== 5. ỦNG GIÁP GỌN SẮC — OUTER LAYER 1.0F (`armorRightBoot/LeftBoot`) ====================
    # Bỏ cục gờ cổ chân thò ra mặt bên, thu gọn móng vuốt thành 3 vuốt thú Obsidian chắc gọn ôm sát mũi ủng + lửa gót emissive!
    def build_boot_and_flames(s: float):
        bx = s * 2.50
        boot_cubes = [
            make_centered_cube(
                [bx, 3.32, 0.0], [4.90, 5.05, 5.82], "BOOT_SIDE",
                face_mats={
                    "north": "BOOT_FRONT",
                    "south": "BOOT_HEEL",
                    "east":  "BOOT_SIDE",
                    "west":  "BOOT_SIDE",
                    "up":    "NETH_DARK",
                    "down":  "BOOT_SOLE",
                }
            ),
            make_centered_cube([bx, 5.60, 0.0], [5.02, 0.56, 5.94], "NETH_PLATE",
                               face_mats={"up": "NETH_PLATE", "down": "NETH_DARK"}),
            # Gờ cổ chân vát phẳng đồng bề rộng ủng (xóa hẳn cục thò ngang bên hông ủng!)
            make_centered_cube([bx, 2.58, -1.48], [4.92, 0.88, 2.72], "BOOT_ANKLE",
                               face_mats={"up": "NETH_PLATE", "down": "NETH_DARK"}),
            make_centered_cube(
                [bx, 1.12, -2.02], [4.86, 1.96, 2.48], "BOOT_FRONT",
                face_mats={"down": "BOOT_SOLE", "up": "NETH_PLATE", "east": "BOOT_SIDE", "west": "BOOT_SIDE"}
            ),
            make_centered_cube([bx, 0.28, -0.35], [4.96, 0.66, 6.26], "BOOT_SOLE"),
            make_centered_cube([bx, 1.36, +2.68], [4.56, 2.08, 0.68], "BOOT_HEEL"),
        ]
        # 3 móng vuốt thú Obsidian chắc gọn ôm sát mũi ủng (hết hẳn cảnh 8 thanh nĩa dài thò ra trước!)
        for off in (-1.28, 0.0, +1.28):
            cx = bx + off
            boot_cubes.append(make_centered_cube([cx, 0.82, -3.40], [0.76, 1.06, 0.72], "CLAW_OBSIDIAN", rot=[+14.0, 0.0, 0.0]))
            boot_cubes.append(make_centered_cube([cx, 0.48, -3.76], [0.54, 0.64, 0.44], "CLAW_OBSIDIAN", rot=[+24.0, 0.0, 0.0]))

        fp = [bx, 1.00, +2.98]
        flame_cubes = [
            make_flame_plane([bx, 2.08, +3.14], 4.15, 3.65, "FLAME_HEEL", rot=[+5.0, 0.0, 0.0]),
            make_flame_plane([bx + s * 2.52, 1.80, +1.85], 2.85, 3.15, "FLAME_SIDE", rot=[0.0, s * +86.0, s * -4.0]),
            make_flame_plane([bx - s * 2.34, 1.52, +2.05], 2.00, 2.40, "FLAME_SIDE", rot=[0.0, s * -86.0, s * +3.0]),
        ]
        return boot_cubes, fp, flame_cubes

    rboot_cubes, fl_r_piv, fl_r_cubes = build_boot_and_flames(-1.0)
    lboot_cubes, fl_l_piv, fl_l_cubes = build_boot_and_flames(+1.0)

    bones = [
        {"name": "bipedHead",         "pivot": [0, 24, 0]},
        {"name": "armorHead",         "parent": "bipedHead", "pivot": [0, 24, 0], "cubes": head_cubes},
        {"name": "cerberus_helm_jaw", "parent": "armorHead", "pivot": helm_jaw_piv, "cubes": helm_jaw_cubes},
        {"name": "cerberus_ear_r",    "parent": "armorHead", "pivot": [-3.45, 32.15, -0.50], "cubes": ear_r_cubes},
        {"name": "cerberus_ear_l",    "parent": "armorHead", "pivot": [+3.45, 32.15, -0.50], "cubes": ear_l_cubes},

        {"name": "bipedBody",           "pivot": [0, 24, 0]},
        {"name": "armorBody",           "parent": "bipedBody", "pivot": [0, 24, 0], "cubes": body_cubes},
        {"name": "cerberus_magma_core", "parent": "armorBody", "pivot": [0.0, 18.10, 0.0], "cubes": magma_core_cubes},

        {"name": "bipedRightArm",            "pivot": [-5, 22, 0]},
        {"name": "armorRightArm",            "parent": "bipedRightArm",           "pivot": [-5, 22, 0], "cubes": rarm_cubes},
        {"name": "armorRightForearm",        "parent": "armorRightArm",           "pivot": [-5, 18, 0], "cubes": rfore_cubes},
        {"name": "cerberus_arm_blade_r",     "parent": "armorRightForearm",       "pivot": rblade_piv,  "cubes": rblade_cubes},
        {"name": "cerberus_shoulder_head_r", "parent": "armorRightArm",           "pivot": sh_r_piv,    "cubes": sh_r_head},
        {"name": "cerberus_shoulder_jaw_r",  "parent": "cerberus_shoulder_head_r","pivot": jaw_r_piv,   "cubes": jaw_r_cubes},

        {"name": "bipedLeftArm",             "pivot": [5, 22, 0]},
        {"name": "armorLeftArm",             "parent": "bipedLeftArm",            "pivot": [5, 22, 0],  "cubes": larm_cubes},
        {"name": "armorLeftForearm",         "parent": "armorLeftArm",            "pivot": [5, 18, 0],  "cubes": lfore_cubes},
        {"name": "cerberus_arm_blade_l",     "parent": "armorLeftForearm",        "pivot": lblade_piv,  "cubes": lblade_cubes},
        {"name": "cerberus_shoulder_head_l", "parent": "armorLeftArm",            "pivot": sh_l_piv,    "cubes": sh_l_head},
        {"name": "cerberus_shoulder_jaw_l",  "parent": "cerberus_shoulder_head_l","pivot": jaw_l_piv,   "cubes": jaw_l_cubes},

        {"name": "bipedRightLeg",         "pivot": [-2, 12, 0]},
        {"name": "armorRightLeg",         "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rleg_cubes},
        {"name": "armorRightBoot",        "parent": "bipedRightLeg", "pivot": [-2, 12, 0], "cubes": rboot_cubes},
        {"name": "cerberus_boot_flame_r", "parent": "armorRightBoot","pivot": fl_r_piv,    "cubes": fl_r_cubes},

        {"name": "bipedLeftLeg",          "pivot": [2, 12, 0]},
        {"name": "armorLeftLeg",          "parent": "bipedLeftLeg",  "pivot": [2, 12, 0],  "cubes": lleg_cubes},
        {"name": "armorLeftBoot",         "parent": "bipedLeftLeg",  "pivot": [2, 12, 0],  "cubes": lboot_cubes},
        {"name": "cerberus_boot_flame_l", "parent": "armorLeftBoot", "pivot": fl_l_piv,    "cubes": fl_l_cubes},
    ]

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.cerberus_armor",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 3.4,
                    "visible_bounds_height": 3.6,
                    "visible_bounds_offset": [0.0, 1.34, 0.0],
                },
                "bones": bones,
            }
        ],
    }
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out = ASSETS / "geo" / "armor" / "cerberus_armor.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes, {TEX_W}x{TEX_H})")
    return geo, total_cubes


def build_cerberus_armor_animations():
    anims = {
        "animation.cerberus_armor.idle": {
            "loop": True,
            "animation_length": 3.0,
            "bones": {},
        }
    }
    out = ASSETS / "animations" / "armor" / "cerberus_armor.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out}")


def build_cerberus_armor_textures():
    """
    Bảng màu thuần Đá đen Blackstone + Obsidian ánh tím + Thép tối + Magma đỏ cam + Mắt tím/đỏ:
      - TUYỆT ĐỐI 0% màu nâu gỗ/đất VÀ 0% màu vàng tươi (gold)!
      - Edge highlight `1px` xám-bạc ánh tím sắc nét ở các gờ cạnh.
      - Emissive (`cerberus_armor_glowmask.png`) sạch `non_glow_rgb_max == 0`.
    """
    verify_uv_layout()
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    NETH = [
        (12,  10,  18, 255),  # 0 AO sâu
        (20,  17,  29, 255),  # 1 bóng tối rãnh đá
        (31,  27,  44, 255),  # 2 nền đá đen Blackstone
        (44,  38,  61, 255),  # 3 mặt đá chính
        (59,  52,  81, 255),  # 4 mặt bắt sáng
        (78,  70, 104, 255),  # 5 gờ đá sáng dịu tự nhiên (không đóng khung trắng gắt!)
        (102, 93, 132, 255),  # 6 cạnh vát sáng tinh tế cho gờ chính
        (126, 116, 156, 255), # 7 điểm nhấn góc (dùng tiết chế)
    ]
    OBSIDIAN = [
        (10,   8,  16, 255),  # 0 khe đen sâu
        (19,  15,  31, 255),  # 1 bóng obsidian tím-đen
        (31,  25,  50, 255),  # 2 nền obsidian ánh tím trầm
        (48,  40,  74, 255),  # 3 mặt vát obsidian
        (74,  65, 108, 255),  # 4 sống sáng tím bạc dịu
        (104, 94, 144, 255),  # 5 highlight gờ sắc lạnh
    ]
    IRON = [
        (13,  14,  20, 255),
        (24,  26,  36, 255),
        (39,  43,  56, 255),
        (60,  65,  82, 255),
        (86,  92, 114, 255),
        (116, 122, 148, 255),
    ]
    # Hắt sáng đỏ thẫm sát mép khe magma (thuần đỏ thẫm r >> g ~ b, không tạo sắc nâu đất!)
    BOUNCE_RED = np.array([128, 20, 24], dtype=np.float32)

    FANG = [
        (120, 116, 112, 255),
        (178, 174, 168, 255),
        (222, 220, 214, 255),
        (246, 244, 238, 255),
    ]
    MAGMA = {
        "e": (132,  22,  10, 255),
        "d": (162,  22,   8, 255),
        "r": (232,  40,  10, 255),
        "o": (254, 104,  14, 255),
        "h": (255, 176,  28, 255),
        "c": (255, 228,  72, 255),
    }
    EYE_R = {
        "0": (14,   6,  10, 255),
        "d": (172, 12,  10, 255),
        "r": (244, 26,  14, 255),
        "o": (255, 96,  20, 255),
        "b": (255, 180, 48, 255),
    }
    EYE_P = {
        "0": (14,   8,  20, 255),
        "d": (98,  26, 162, 255),
        "r": (158, 56, 238, 255),
        "o": (208, 118, 255, 255),
        "b": (244, 196, 255, 255),
    }

    def set_glow_px(gx: int, gy: int, col):
        diff[gy, gx] = col
        glow[gy, gx] = col

    def blend_rgb(gx: int, gy: int, target_rgb, weight: float):
        if glow[gy, gx, 3] > 0:
            return
        cur = diff[gy, gx, :3].astype(np.float32)
        diff[gy, gx, :3] = np.clip(cur * (1.0 - weight) + target_rgb * weight, 0, 255).astype(np.uint8)

    def darken_ao(gx: int, gy: int, factor: float):
        if glow[gy, gx, 3] > 0:
            return
        cur = diff[gy, gx, :3].astype(np.float32)
        diff[gy, gx, :3] = np.clip(cur * factor, 8, 255).astype(np.uint8)

    def paint_blackstone(key: str, base_lvl: int = 2, sharp_edge: bool = True, layered: bool = True, top_ao_rows: int = 0):
        x0, y0, w, h = REG[key]
        for y in range(h):
            for x in range(w):
                h_fine = (((x + 7) * 73 ^ (y + 11) * 151 ^ (x0 + 3) * 37 ^ (y0 + 5) * 97) * 2654435761) >> 24 & 31
                cx2, cy2 = (x + (y & 1)) // 2, (y + (x & 1)) // 2
                h_coarse = (((cx2 + 5) * 47 ^ (cy2 + 9) * 89 ^ (x0 * 19 + y0 * 23)) * 1103515245) >> 24 & 15
                diag = (x / max(1.0, w - 1.0)) * 0.40 + (y / max(1.0, h - 1.0)) * 0.60
                lvl = base_lvl
                if diag < 0.25:
                    lvl = min(4, base_lvl + 1)
                elif diag > 0.78:
                    lvl = max(1, base_lvl - 1)
                if h_coarse <= 2 or h_fine <= 3:
                    lvl = max(1, lvl - 1)
                elif h_fine >= 28:
                    lvl = min(4, lvl + 1)

                if layered and 2 <= x <= w - 3 and 2 <= y <= h - 3:
                    brick_row = y // 5
                    bx = (x + (brick_row % 2) * 4) % 8
                    by = y % 5
                    if by == 0 and bx not in (0, 7) and h_coarse < 11:
                        lvl = max(0, lvl - 1)
                    elif bx == 0 and by in (1, 2, 3) and h_coarse < 10:
                        lvl = max(0, lvl - 1)
                    elif by == 1 and bx in (2, 3, 4) and h_fine > 20:
                        lvl = min(5, lvl + 1)

                if sharp_edge:
                    # Viền đá tự nhiên dịu mắt, phá đứt nhẹ theo vân đá — TUYỆT ĐỐI không đóng khung hộp trắng gắt quanh mọi cube!
                    if y == 0 and 1 <= x <= w - 2:
                        lvl = 5 if (h_fine >= 12) else 4
                    elif x == 0 and 1 <= y <= h - 2:
                        lvl = 4 if (y <= h // 2 and h_fine >= 10) else 3
                    elif y == h - 1 or x == w - 1:
                        lvl = 1 if h_coarse >= 4 else 0
                diff[y0 + y, x0 + x] = NETH[max(0, min(7, lvl))]
            if top_ao_rows > 0 and 1 <= y < 1 + top_ao_rows:
                for x in range(1, w - 1):
                    darken_ao(x0 + x, y0 + y, 0.78)

    def paint_overlapping_tasset_scales(key: str, base_lvl: int = 2, y_end_ratio: float = 0.50):
        x0, y0, w, h = REG[key]
        paint_blackstone(key, base_lvl=base_lvl, sharp_edge=True, layered=True, top_ao_rows=1)
        y_lim = max(4, min(h - 2, int(round(h * y_end_ratio))))
        for y in range(2, y_lim):
            for x in range(2, w - 2):
                u = (x + y) % 6
                v = (x - y + 60) % 6
                if u == 0 or v == 0:
                    lvl = 0
                elif u == 1 and v == 1:
                    lvl = 6
                elif (u == 1 and v in (2, 3, 4)) or (v == 1 and u in (2, 3, 4)):
                    lvl = 5
                elif u == 5 or v == 5:
                    lvl = 1
                else:
                    lvl = 3 if (u == 2 and v == 2) else 2
                diff[y0 + y, x0 + x] = NETH[lvl]

    def apply_crimson_rim(key: str, radius: float = 1.8):
        x0, y0, w, h = REG[key]
        glow_pts = [(x, y) for y in range(h) for x in range(w) if glow[y0 + y, x0 + x, 3] > 0]
        if not glow_pts:
            return
        for y in range(h):
            for x in range(w):
                if glow[y0 + y, x0 + x, 3] > 0:
                    continue
                min_d = min(math.hypot(x - gx, y - gy) for gx, gy in glow_pts)
                if min_d <= radius:
                    blend_rgb(x0 + x, y0 + y, BOUNCE_RED, 0.36 * (1.0 - (min_d - 1.0) / max(0.5, radius)))

    # 1. Đá đen Blackstone cơ sở (tắt viền hộp trên vật liệu khối phụ để các mảnh giáp hòa liền mạch thành 1 form!)
    for k, bl, edge, lay in (
        ("NETH_DARK",       1, False, False),
        ("NETH_MID",        2, False, True),
        ("NETH_PLATE",      3, True,  True),
        ("HELM_TOP",        3, False, True),
        ("HELM_SIDE",       2, False, True),
        ("HELM_BACK",       2, False, True),
        ("HELM_SNOUT_TOP",  3, True,  False),
        ("HELM_SNOUT_SIDE", 2, False, False),
        ("BOOT_SOLE",       1, False, False),
        ("WAIST_BELT",      2, False, True),
        ("BOOT_ANKLE",      2, True,  True),
    ):
        paint_blackstone(k, base_lvl=bl, sharp_edge=edge, layered=lay)

    # Mạch nứt magma mảnh 1px tinh tế hai bên thái dương mũ (`HELM_SIDE`)
    x0, y0, w, h = REG["HELM_SIDE"]
    for cx, cy, ch in ((5, 4, "d"), (6, 5, "r"), (7, 6, "o"), (8, 6, "r"), (9, 7, "d"), (8, 5, "d")):
        set_glow_px(x0 + cx, y0 + cy, MAGMA[ch])
    apply_crimson_rim("HELM_SIDE", radius=1.5)

    x0, y0, w, h = REG["HELM_SNOUT_TOP"]
    for y in range(1, h - 2):
        diff[y0 + y, x0 + w // 2 - 1] = NETH[5]
        diff[y0 + y, x0 + w // 2] = NETH[6 if y < h // 2 else 5]

    # Sống vảy gai đá (`DORSAL_SPIKE`): đá đen Blackstone + sống giữa sáng dịu
    paint_blackstone("DORSAL_SPIKE", base_lvl=2, sharp_edge=False, layered=False)
    x0, y0, w, h = REG["DORSAL_SPIKE"]
    for y in range(1, h - 1):
        diff[y0 + y, x0 + w // 2] = NETH[5]
        diff[y0 + y, x0 + w // 2 - 1] = NETH[4]

    # 2. SỪNG / TAI ĐÁ ĐEN OBSIDIAN (`OBSIDIAN_HORN`, `OBSIDIAN_TIP`) & MÓNG VUỐT OBSIDIAN (`CLAW_OBSIDIAN`):
    #    Vân sống dọc liền mạch KHÔNG vẽ khung hộp ngang giữa các đốt sừng để 4 đốt hòa làm 1 chiếc sừng cong mượt!
    for key in ("OBSIDIAN_HORN", "OBSIDIAN_TIP"):
        x0, y0, w, h = REG[key]
        for y in range(h):
            for x in range(w):
                grain = (((x + 5) * 41 ^ (y + 9) * 73) >> 2) & 3
                dist = abs(x - (w - 1) * 0.5)
                if dist <= 0.7:
                    lvl = 5 if (grain >= 1) else 4
                elif dist <= 2.2:
                    lvl = 3 if (grain >= 1) else 2
                elif x == 0 or x == w - 1:
                    lvl = 1
                else:
                    lvl = 2 if grain > 0 else 1
                diff[y0 + y, x0 + x] = OBSIDIAN[lvl]

    x0, y0, w, h = REG["CLAW_OBSIDIAN"]
    for y in range(h):
        for x in range(w):
            t = y / max(1.0, h - 1.0)
            dist = abs(x - (w - 1) * 0.5)
            if dist <= 0.65:
                lvl = 5
            elif dist <= 1.7:
                lvl = 4 if t > 0.35 else 3
            elif x == 0 or x == w - 1:
                lvl = 1
            else:
                lvl = 2 if ((x + y) % 3 != 0) else 1
            diff[y0 + y, x0 + x] = OBSIDIAN[lvl]

    # 3. Mũi tối, răng nanh trắng, xích sắt tối & ốp gối nhọn đá đen
    x0, y0, w, h = REG["NOSE_DARK"]
    for y in range(h):
        for x in range(w):
            if y == 0 and 1 <= x <= w - 2:
                diff[y0 + y, x0 + x] = NETH[5]
            elif y in (2, 3) and x in (2, 3, w - 4, w - 3):
                diff[y0 + y, x0 + x] = NETH[0]
            else:
                diff[y0 + y, x0 + x] = NETH[2 if y == 1 else 1]

    for key, bright in (("IRON_CHAIN", False), ("IRON_CLASP", True)):
        x0, y0, w, h = REG[key]
        for y in range(h):
            for x in range(w):
                edge = (x == 0 or x == w - 1 or y == 0 or y == h - 1)
                hole = (4 <= x <= w - 5 and 4 <= y <= h - 5)
                if edge:
                    col = IRON[1]
                elif hole:
                    col = IRON[1] if bright else IRON[0]
                elif y == 1 or x == 1:
                    col = IRON[5 if bright else 4]
                elif y == 2 or x == 2:
                    col = IRON[3 if bright else 2]
                else:
                    col = IRON[2]
                diff[y0 + y, x0 + x] = col

    paint_blackstone("KNEE_PLATE", base_lvl=2, sharp_edge=False, layered=False)
    x0, y0, w, h = REG["KNEE_PLATE"]
    for y in range(2, h - 2):
        half_w = max(1, int(round((1.0 - abs((y - 7.5) / 6.5)) * 4.5)))
        for dx in range(-half_w, half_w + 1):
            px = x0 + w // 2 + dx
            if x0 + 1 <= px <= x0 + w - 2:
                lvl = 5 if dx == 0 else (4 if abs(dx) == 1 else (3 if abs(dx) == 2 else 2))
                diff[y0 + y, px] = NETH[lvl]

    # 4. Mắt đỏ EMISSIVE (mũ + vai trái), mắt tím EMISSIVE (vai phải), răng nanh & lõi magma
    x0, y0, w, h = REG["EYE_RED"]
    for y, row in enumerate([
        "00dddddd00", "0drrrrrrd0", "droobboord",
        "0drobbord0", "00drrrrd00", "000dddd000",
    ]):
        for x, ch in enumerate(row):
            if ch == "0":
                diff[y0 + y, x0 + x] = EYE_R["0"]
            else:
                set_glow_px(x0 + x, y0 + y, EYE_R[ch])

    for key, epal in (("EYE_SMALL_RED", EYE_R), ("EYE_SMALL_PURPLE", EYE_P)):
        x0, y0, w, h = REG[key]
        for y, row in enumerate([
            "0dddddd0", "drroorrd", "drobbord", "0drrrrd0", "00dddd00",
        ]):
            for x, ch in enumerate(row):
                if ch == "0":
                    diff[y0 + y, x0 + x] = epal["0"]
                else:
                    set_glow_px(x0 + x, y0 + y, epal[ch])

    x0, y0, w, h = REG["FANG_IVORY"]
    for y in range(h):
        for x in range(w):
            lvl = 0 if (x == 0 or y == 0) else (3 if y >= h - 3 or x == w - 2 else (2 if x >= 2 else 1))
            diff[y0 + y, x0 + x] = FANG[lvl]

    for key, hot in (("MAGMA_CORE", True), ("MAGMA_CRACK", False)):
        x0, y0, w, h = REG[key]
        cx, cy = (w - 1) * 0.5, (h - 1) * 0.5
        for y in range(h):
            for x in range(w):
                d = math.hypot((x - cx) / max(1.0, cx), (y - cy) / max(1.0, cy))
                if hot:
                    ch = "c" if d < 0.44 else ("h" if d < 0.74 else ("o" if d < 0.96 else "r"))
                else:
                    ch = "h" if d < 0.46 else ("o" if d < 0.80 else ("r" if d < 1.15 else "d"))
                set_glow_px(x0 + x, y0 + y, MAGMA[ch])

    # 5. Giáp Ngực TRƯỚC & SAU (Blackstone liền mạch xuống tận gấu áo + lõi magma đỏ cam!)
    paint_blackstone("CHEST_FRONT", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=2)
    x0, y0, w, h = REG["CHEST_FRONT"]
    for y in range(3, h - 3):
        set_glow_px(x0 + 10, y0 + y, MAGMA["c"] if (8 <= y <= 16) else (MAGMA["h"] if (5 <= y <= 20) else MAGMA["o"]))
        set_glow_px(x0 + 11, y0 + y, MAGMA["c"] if (9 <= y <= 15) else MAGMA["h"])
        set_glow_px(x0 + 9,  y0 + y, MAGMA["o"] if (7 <= y <= 18) else MAGMA["r"])
        set_glow_px(x0 + 12, y0 + y, MAGMA["o"] if (7 <= y <= 18) else MAGMA["r"])
        set_glow_px(x0 + 8,  y0 + y, MAGMA["d"])
        set_glow_px(x0 + 13, y0 + y, MAGMA["d"])
    apply_crimson_rim("CHEST_FRONT", radius=2.2)

    paint_blackstone("CHEST_BACK", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=2)
    x0, y0, w, h = REG["CHEST_BACK"]
    for y in range(3, h - 3):
        set_glow_px(x0 + 10, y0 + y, MAGMA["c"] if (9 <= y <= 16) else MAGMA["h"])
        set_glow_px(x0 + 11, y0 + y, MAGMA["h"] if (7 <= y <= 18) else MAGMA["o"])
        set_glow_px(x0 + 9,  y0 + y, MAGMA["r"])
        set_glow_px(x0 + 12, y0 + y, MAGMA["r"])
    apply_crimson_rim("CHEST_BACK", radius=2.0)

    paint_blackstone("CHEST_SIDE", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=2)

    # 4 tấm giáp ngực ốp nổi +0.6px (`CHEST_PLATE_R`, `CHEST_PLATE_L`, `CHEST_PLATE_BEVEL`)
    for key, inner_right in (("CHEST_PLATE_R", True), ("CHEST_PLATE_L", False)):
        paint_blackstone(key, base_lvl=3, sharp_edge=True, layered=True, top_ao_rows=1)
        x0, y0, w, h = REG[key]
        in_x = (w - 2) if inner_right else 1
        in_x2 = (w - 3) if inner_right else 2
        for y in range(1, h - 1):
            diff[y0 + y, x0 + in_x] = NETH[7 if (2 <= y <= h - 4) else 6]
            blend_rgb(x0 + in_x2, y0 + y, BOUNCE_RED, 0.20)
    paint_blackstone("CHEST_PLATE_BEVEL", base_lvl=2, sharp_edge=False, layered=False)
    x0, y0, w, h = REG["CHEST_PLATE_BEVEL"]
    for y in range(h):
        for x in range(w):
            if y == 0 or y == h - 1:
                diff[y0 + y, x0 + x] = NETH[0]
            elif x == 1 or x == w - 2:
                diff[y0 + y, x0 + x] = NETH[6]
            else:
                blend_rgb(x0 + x, y0 + y, BOUNCE_RED, 0.28)

    # 6. Vai giáp (`PAULDRON_PLATE`) + Bắp tay, Găng cẳng tay lưỡi khuỷu & Bàn tay móng vuốt (phong cách ảnh mẫu!)
    paint_blackstone("PAULDRON_PLATE", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=1)

    BONE = [
        (78,  80,  90, 255),
        (134, 138, 148, 255),
        (184, 188, 198, 255),
        (224, 228, 236, 255),
    ]
    # BONE_TRIM: thanh gân bạc-xương sáng lạnh nổi bật trên cẳng tay, lưỡi đao & vuốt tay
    x0, y0, w, h = REG["BONE_TRIM"]
    for y in range(h):
        for x in range(w):
            if x == 0 or x == w - 1 or y == 0 or y == h - 1:
                diff[y0 + y, x0 + x] = BONE[0]
            elif x == 1 or y == 1:
                diff[y0 + y, x0 + x] = BONE[3]
            elif x <= w // 2:
                diff[y0 + y, x0 + x] = BONE[2]
            else:
                diff[y0 + y, x0 + x] = BONE[1]

    # ARM_BICEP: giáp bắp tay phân tầng vảy đá đen góc cạnh có khe đỏ thẫm mảnh tinh tế
    paint_blackstone("ARM_BICEP", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=1)
    x0, y0, w, h = REG["ARM_BICEP"]
    for row_y in (5, 10):
        for x in range(1, w - 1):
            diff[y0 + row_y, x0 + x] = NETH[0]
            if 3 <= x <= w - 4 and row_y == 10:
                set_glow_px(x0 + x, y0 + row_y + 1, MAGMA["d"] if (x % 2 == 0) else MAGMA["r"])
    apply_crimson_rim("ARM_BICEP", radius=1.4)

    # GAUNTLET_FRONT: mặt trước/sau cẳng tay giáp đen tối giản + đường viền đỏ mảnh dọc mép ngoài (0 chữ X!)
    paint_blackstone("GAUNTLET_FRONT", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=1)
    x0, y0, w, h = REG["GAUNTLET_FRONT"]
    for y in range(3, h - 3):
        set_glow_px(x0 + 1, y0 + y, MAGMA["r"] if (5 <= y <= h - 6) else MAGMA["d"])
        set_glow_px(x0 + w - 2, y0 + y, MAGMA["r"] if (5 <= y <= h - 6) else MAGMA["d"])
        if y in (6, 13):
            for x in range(3, w - 3):
                diff[y0 + y, x0 + x] = NETH[0]
                diff[y0 + y + 1, x0 + x] = NETH[4]
    apply_crimson_rim("GAUNTLET_FRONT", radius=1.5)

    # GAUNTLET_OUTER: mặt ngoài cẳng tay giáp Obsidian tối + chỉ đỏ mảnh hai mép & gờ đá sáng giữa
    paint_blackstone("GAUNTLET_OUTER", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=1)
    x0, y0, w, h = REG["GAUNTLET_OUTER"]
    for y in range(2, h - 2):
        set_glow_px(x0 + 1, y0 + y, MAGMA["r"] if (4 <= y <= h - 5) else MAGMA["d"])
        set_glow_px(x0 + w - 2, y0 + y, MAGMA["r"] if (4 <= y <= h - 5) else MAGMA["d"])
        diff[y0 + y, x0 + w // 2] = NETH[5]
        diff[y0 + y, x0 + w // 2 - 1] = NETH[4]
    apply_crimson_rim("GAUNTLET_OUTER", radius=1.6)

    # BLADE_FIN_SIDE: mặt trước/sau lưỡi đao cong khuỷu tay (Obsidian tối liền khối có gân nổi & sắc đỏ mảnh)
    paint_blackstone("BLADE_FIN_SIDE", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=1)
    x0, y0, w, h = REG["BLADE_FIN_SIDE"]
    for y in range(2, h - 2):
        diff[y0 + y, x0 + w // 2] = NETH[5]
        diff[y0 + y, x0 + w // 2 - 1] = NETH[4]
        if y % 7 == 3:
            for x in range(3, w - 3):
                diff[y0 + y, x0 + x] = NETH[0]
    apply_crimson_rim("BLADE_FIN_SIDE", radius=1.4)

    # GAUNTLET_HAND: găng bàn tay tối có các rãnh khớp ngón tối & mạch lửa đỏ mảnh 1px tinh tế!
    paint_blackstone("GAUNTLET_HAND", base_lvl=1, sharp_edge=True, layered=False, top_ao_rows=1)
    x0, y0, w, h = REG["GAUNTLET_HAND"]
    for fx in (3, 7, 11):
        for y in range(4, h - 2):
            diff[y0 + y, x0 + fx - 1] = NETH[0]
            if y in (6, 7, 9, 10, 11):
                set_glow_px(x0 + fx, y0 + y, MAGMA["r"] if y <= 9 else MAGMA["d"])
        diff[y0 + 3, x0 + fx] = BONE[2]
    apply_crimson_rim("GAUNTLET_HAND", radius=1.4)

    # 7. Quần 0.5F (`LEG_FRONT`, `LEG_SIDE`, `LEG_BACK`): 100% đá đen Blackstone + mạch nứt dung nham đỏ cam (0% mảng nâu!)
    paint_overlapping_tasset_scales("LEG_FRONT", base_lvl=2, y_end_ratio=0.45)
    x0, y0, w, h = REG["LEG_FRONT"]
    for y in range(int(h * 0.42), h - 2):
        cx = 7 + (1 if (y // 4) % 2 == 0 else 0)
        set_glow_px(x0 + cx, y0 + y, MAGMA["h"] if (10 <= y <= 16) else MAGMA["o"])
        set_glow_px(x0 + cx - 1, y0 + y, MAGMA["r"])
    apply_crimson_rim("LEG_FRONT", radius=2.0)

    paint_overlapping_tasset_scales("LEG_SIDE", base_lvl=2, y_end_ratio=0.40)
    x0, y0, w, h = REG["LEG_SIDE"]
    for y in range(int(h * 0.36), h - 2):
        cx = int(round(7.5 + math.sin(y * 0.65) * 1.6))
        cx = max(3, min(w - 4, cx))
        set_glow_px(x0 + cx, y0 + y, MAGMA["o"] if (y % 2 == 0) else MAGMA["r"])
        if y % 3 == 0 and cx + 1 < w - 2:
            set_glow_px(x0 + cx + 1, y0 + y, MAGMA["d"])
    apply_crimson_rim("LEG_SIDE", radius=1.8)

    paint_overlapping_tasset_scales("LEG_BACK", base_lvl=2, y_end_ratio=0.58)

    # 8. Cổ ủng 1.0F (`BOOT_FRONT`, `BOOT_SIDE`, `BOOT_HEEL`): 100% đá đen Blackstone đồng bộ + tàn lửa gót ủng!
    paint_blackstone("BOOT_FRONT", base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=1)
    for key in ("BOOT_SIDE", "BOOT_HEEL"):
        paint_blackstone(key, base_lvl=2, sharp_edge=True, layered=True, top_ao_rows=1)
        x0, y0, w, h = REG[key]
        for y in range(h - 6, h - 1):
            dist_b = (h - 1) - y
            for x in range(2, w - 2):
                wave = ((x * 3 + y * 5) % 5)
                if dist_b <= 2 or (dist_b <= 4 and wave in (0, 2, 3)):
                    ch = "c" if dist_b == 1 else ("h" if dist_b == 2 else "o")
                    set_glow_px(x0 + x, y0 + y, MAGMA[ch])
        apply_crimson_rim(key, radius=2.0)

    # 9. Lửa pixel gót ủng EMISSIVE
    x0, y0, w, h = REG["FLAME_HEEL"]
    for y, row in enumerate([
        ".....r......", ".....o......", "....ro......", "....oh...r..",
        "...roh...o..", "..rohh..ro..", "..ohhh..oh..", ".rohhh.roh..",
        ".ohhchhohh..", ".ohhcchhho..", "rohhccchhor.", "rohhccchhor.",
        "ohhhccchhho.", "ohhhccchhho.", "rrhhccchhrr.", ".drrooorrd..",
    ]):
        for x, ch in enumerate(row):
            if ch != ".":
                set_glow_px(x0 + x, y0 + y, MAGMA[ch])

    x0, y0, w, h = REG["FLAME_SIDE"]
    for y, row in enumerate([
        "......r.....", "......o.....", ".....ro.....", "....roh.....",
        "....ohh..r..", "...rohh..o..", "..rohhh.ro..", "..ohhcchho..",
        ".rohhcccho..", ".ohhhccchor.", ".ohhhccchor.", "rohhhccchhr.",
        ".drohchhord.", "..ddrroodd..",
    ]):
        for x, ch in enumerate(row):
            if ch != ".":
                set_glow_px(x0 + x, y0 + y, MAGMA[ch])

    non_glow_mask = glow[:, :, 3] == 0
    glow[non_glow_mask] = (0, 0, 0, 0)
    assert int(glow[non_glow_mask, :3].max()) == 0, "Non-glow RGB leak!"
    glow_count = int((glow[:, :, 3] > 0).sum())

    tex_dir = ASSETS / "textures" / "armor"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(tex_dir / "cerberus_armor.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "cerberus_armor_glowmask.png")
    print(f"TEX_OK: {tex_dir / 'cerberus_armor.png'} & glowmask (glow_px={glow_count}, non_glow_rgb_max=0)")
    return diff, glow


def build_cerberus_item_icons():
    """
    6 icon đồng bộ 100% chất liệu Đá đen Blackstone + Obsidian ánh tím + Magma đỏ cam (0% màu nâu, 0% màu vàng!):
    - `cerberus_helmet.png` & `cerberus_chestplate.png` nâng cấp chuẩn pixel-art `32x32` sắc nét (đồng chuẩn với
      vũ khí 32x32 trong mod) để khắc họa 1:1 model 3D: mũ đầu sói bọc kín có cặp sừng viền magma + mào đỉnh +
      mắt xếch dữ tợn + hàm lửa răng nanh, và giáp thân với 2 đầu Cerberus cỡ lớn trên cầu vai (trái mắt tím /
      phải mắt đỏ cam) + cánh tay bọc giáp có găng móng vuốt + lõi magma kim cương chéo xích sắt X!
    """
    pal16 = {
        ".": (0, 0, 0, 0),
        "0": (13, 10, 19, 255),
        "1": (24, 20, 35, 255),
        "2": (42, 36, 58, 255),
        "3": (66, 58, 88, 255),
        "4": (138, 128, 166, 255),
        "p": (182, 86, 246, 255),
        "s": (48, 52, 66, 255),
        "m": (150, 154, 178, 255),
        "b": (122, 22, 24, 255),
        "i": (244, 240, 232, 255),
        "d": (158, 20, 8, 255),
        "r": (232, 38, 12, 255),
        "o": (254, 104, 16, 255),
        "h": (255, 214, 56, 255),
    }
    pal32 = {
        ".": (0, 0, 0, 0),
        "0": (12, 9, 18, 255),       # Viền tối Blackstone sâu
        "1": (23, 18, 35, 255),      # Đá đen tối
        "2": (39, 33, 56, 255),      # Đá đen nền
        "3": (62, 54, 86, 255),      # Đá đen trung tính
        "4": (96, 86, 128, 255),     # Cạnh vát sáng Blackstone
        "5": (144, 134, 178, 255),   # Điểm sáng cạnh Obsidian
        "c": (20, 22, 30, 255),      # Lõi xích sắt tối
        "s": (78, 86, 108, 255),     # Ánh kim xích sắt
        "m": (182, 188, 210, 255),   # Viền bạc-xương & móng vuốt
        "i": (248, 244, 234, 255),   # Răng nanh ngà trắng sáng
        "g": (188, 182, 174, 255),   # Bóng răng nanh ngà
        "d": (166, 20, 10, 255),     # Magma đỏ thẫm
        "r": (240, 42, 14, 255),     # Magma đỏ tươi
        "o": (255, 118, 18, 255),    # Magma cam rực
        "h": (255, 222, 66, 255),    # Tâm lửa vàng chanh
        "w": (255, 248, 188, 255),   # Tâm lửa trắng nóng
        "v": (134, 40, 214, 255),    # Ánh tím tối (đầu vai phải)
        "p": (204, 102, 255, 255),   # Mắt tím phát sáng đầu vai phải
        "q": (248, 214, 255, 255),   # Tâm sáng mắt tím
    }

    def mirror_32(half_rows, eye_left_map=None, eye_right_map=None):
        assert len(half_rows) == 32, f"Expected 32 rows, got {len(half_rows)}"
        full = []
        for idx, hr in enumerate(half_rows):
            assert len(hr) == 16, f"Bad half row {idx}: len={len(hr)}"
            left = hr
            right = hr[::-1]
            if eye_left_map:
                for k, v in eye_left_map.items():
                    left = left.replace(k, v)
            if eye_right_map:
                for k, v in eye_right_map.items():
                    right = right.replace(k, v)
            full.append(left + right)
        return full

    # Mũ đầu sói Cerberus 32x32 khớp 1:1 model 3D: sừng cao viền lửa magma + mào đỉnh + mắt xếch dữ + mõm sói + hàm lửa răng nanh!
    helmet_32 = mirror_32([
        "....0...........",
        "....05..........",
        "....040........0",
        "....03o0......04",
        "....023o0.....03",
        ".....023h0....0o",
        ".....023ro0...0r",
        ".....0123ro0..03",
        ".....01234o0..02",
        ".....00344400003",
        ".....04543334444",
        ".....03332223332",
        "....0o2223332233",
        "....0r1234433222",
        "....0d0120023332",
        ".....0120ro01233",
        ".....01210oh0232",
        "....0021100dr022",
        "....032122100132",
        "....023212332211",
        "....023211221100",
        "....023100i1i0ro",
        "....02310iioiohw",
        "....02210igoohww",
        "....02210droihhh",
        "....012101diiroo",
        "....0120001gi0dr",
        ".....02211222111",
        ".....01223332232",
        ".....00112221121",
        "......0000000000",
        "................",
    ])

    # Giáp thân Cerberus 32x32 khớp 1:1 model 3D: 2 đầu sói cỡ lớn trên vai (trái mắt tím / phải mắt đỏ cam) +
    # full 2 cánh tay bọc giáp găng móng vuốt + lõi magma dọc kim cương chéo xích sắt X + cổ chữ V!
    chestplate_32 = mirror_32([
        "................",
        ".0......0.......",
        ".05....05.......",
        ".040...040......",
        ".0340..030......",
        ".023400320......",
        "0234433320......",
        "0123322320......",
        "01fEe22eE0......",
        "00123432100.....",
        ".00i1oo1i0000000",
        "..00122100s430ro",
        "..034433202cs0oh",
        ".03432232022csro",
        ".023211210221cso",
        ".02droor202320ch",
        ".0121112102330ow",
        ".00233320012rohw",
        "032234321012rhw0",
        "02o223211002rh0s",
        "02o222211012rhw0",
        "02o223211012rohw",
        "02o2222110221cso",
        "01r223211022csro",
        "01222mm2102cs0oh",
        ".012mm210.0s40ro",
        ".01211210.0330dr",
        ".012r1r20.022111",
        ".00122210.000000",
        "..0m010m0.......",
        "..0i0.0i0.......",
        "...0...0........",
    ], eye_left_map={"f": "v", "E": "p", "e": "q"}, eye_right_map={"f": "d", "E": "r", "e": "o"})

    icons = {
        "cerberus_heart.png": [
            "................",
            "....00....00....",
            "..00340..04300..",
            ".03421000012430.",
            ".0421bdrrdb1240.",
            ".031drohhord130.",
            ".021rohhhhor120.",
            ".01drhhoohhrd10.",
            "..01drroorrd10..",
            "..021bdrddb120..",
            "...021drod120...",
            "....021dd120....",
            ".....012210.....",
            "......0110......",
            ".......00.......",
            "................",
        ],
        "cerberus_hide.png": [
            "................",
            "...000....000...",
            "..034300003430..",
            ".03432211223430.",
            ".0421bdrr211240.",
            "..021droor1420..",
            "..0321rodr1230..",
            "..024drddb1120..",
            "..032ro2142320..",
            ".0342d123212430.",
            ".02314234321320.",
            ".01221232212210.",
            "..001210012100..",
            "....000..000....",
            "................",
            "................",
        ],
        "cerberus_helmet.png": helmet_32,
        "cerberus_chestplate.png": chestplate_32,
        "cerberus_leggings.png": [
            "................",
            "..000000000000..",
            "..03432ss23430..",
            "..0232s22s2320..",
            "..0m1m2112m1m0..",
            "..04sr1001rs40..",
            "..02bro00orb20..",
            "..02boh00hob20..",
            "..02bro00orb20..",
            "..0434d00d4340..",
            "..013420024310..",
            "..012310013210..",
            "..012210012210..",
            "..01110..01110..",
            "...000....000...",
            "................",
        ],
        # Ủng cổ phồng 1.0F đồng bộ đá đen Blackstone + móng vuốt Obsidian + lửa gót (0% đai nâu!)
        "cerberus_boots.png": [
            "................",
            "................",
            "..00000..00000..",
            "..03430..03430..",
            "..02320..02320..",
            "..04210..01240..",
            ".r02310..01320r.",
            ".or3432..2343ro.",
            ".ho1210..0121oh.",
            ".ro0243003420or.",
            ".oh0231001320ho.",
            ".oh1204004021ho.",
            ".ro2103003012or.",
            ".dr1001001001rd.",
            "..00000..00000..",
            "................",
        ],
    }

    item_dir = ASSETS / "textures" / "item"
    item_dir.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    strip = Image.new("RGBA", (16 * 8 * 6 + 40, 16 * 8 + 20), (22, 19, 28, 255))
    for idx, (fname, rows) in enumerate(icons.items()):
        sz = len(rows)
        assert sz in (16, 32) and all(len(r) == sz for r in rows), f"Bad icon shape: {fname} ({sz})"
        cur_pal = pal32 if sz == 32 else pal16
        arr = np.zeros((sz, sz, 4), dtype=np.uint8)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                arr[y, x] = cur_pal[ch]
        im = Image.fromarray(arr, "RGBA")
        im.save(item_dir / fname)
        up = im.resize((128, 128), Image.Resampling.NEAREST)
        strip.paste(up, (10 + idx * 132, 10), up)
    out_strip = PREVIEW_DIR / "cerberus_icons_preview.png"
    strip.save(out_strip)
    print(f"ICONS_OK: 6 icons -> {item_dir} (preview: {out_strip})")


def render_cerberus_previews(geo_data, diff_img, glow_img):
    all_faces = []
    helm_faces = []
    piece_faces = {"helmet": [], "chest": [], "legs": [], "boots": []}

    def add_dummy(center, size, rgb):
        cx, cy, cz = center
        sx, sy, sz = size
        x0, x1 = cx - sx * 0.5, cx + sx * 0.5
        y0, y1 = cy - sy * 0.5, cy + sy * 0.5
        z0, z1 = cz - sz * 0.5, cz + sz * 0.5
        pts = np.array([
            [x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
            [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1],
        ], dtype=np.float64)
        for idxs in ([0, 1, 2, 3], [5, 4, 7, 6], [1, 5, 6, 2], [4, 0, 3, 7], [3, 2, 6, 7], [4, 5, 1, 0]):
            all_faces.append((pts[idxs], None, rgb))

    add_dummy([0.0, 28.0, 0.0], [7.9, 7.9, 7.9], (196, 174, 160))
    add_dummy([0.0, 27.6, -3.98], [1.1, 0.9, 0.08], (42, 36, 46))
    add_dummy([-2.0, 28.1, -3.98], [1.3, 0.9, 0.08], (42, 36, 46))
    add_dummy([+2.0, 28.1, -3.98], [1.3, 0.9, 0.08], (42, 36, 46))
    add_dummy([0.0, 18.0, 0.0], [7.9, 11.9, 3.9], (38, 34, 48))
    add_dummy([-6.0, 18.0, 0.0], [3.9, 11.9, 3.9], (196, 174, 160))
    add_dummy([+6.0, 18.0, 0.0], [3.9, 11.9, 3.9], (196, 174, 160))
    add_dummy([-2.0, 6.0, 0.0], [3.9, 11.9, 3.9], (38, 34, 48))
    add_dummy([+2.0, 6.0, 0.0], [3.9, 11.9, 3.9], (38, 34, 48))

    for quad, uv, rgb in all_faces[:24]:
        helm_faces.append((quad, uv, rgb))

    def bone_piece_key(bname: str):
        if bname in ("armorHead", "cerberus_helm_jaw", "cerberus_ear_r", "cerberus_ear_l"):
            return "helmet"
        if bname in ("armorBody", "cerberus_magma_core", "armorRightArm", "armorLeftArm",
                     "armorRightForearm", "armorLeftForearm",
                     "cerberus_arm_blade_r", "cerberus_arm_blade_l",
                     "cerberus_shoulder_head_r", "cerberus_shoulder_jaw_r",
                     "cerberus_shoulder_head_l", "cerberus_shoulder_jaw_l"):
            return "chest"
        if bname in ("armorRightLeg", "armorLeftLeg"):
            return "legs"
        if bname in ("armorRightBoot", "armorLeftBoot", "cerberus_boot_flame_r", "cerberus_boot_flame_l"):
            return "boots"
        return None

    for bone in geo_data["minecraft:geometry"][0]["bones"]:
        pk = bone_piece_key(bone["name"])
        for c in bone.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            corners = np.array([
                [ox, oy, oz], [ox + sx, oy, oz], [ox + sx, oy + sy, oz], [ox, oy + sy, oz],
                [ox, oy, oz + sz], [ox + sx, oy, oz + sz], [ox + sx, oy + sy, oz + sz], [ox, oy + sy, oz + sz],
            ], dtype=np.float64)
            if "rotation" in c:
                piv = np.array(c["pivot"], dtype=np.float64)
                R = bedrock_rot_matrix(c["rotation"])
                corners = (R @ (corners - piv).T).T + piv
            uv_map = c["uv"]
            for fname, idxs in (
                ("north", [0, 1, 2, 3]), ("south", [5, 4, 7, 6]),
                # Khớp 100% BakedModelFactory của GeckoLib 4.8.3 (đảo dấu X nên +X trong geo là "west", -X trong geo là "east"!)
                ("west",  [1, 5, 6, 2]), ("east",  [4, 0, 3, 7]),
                ("up",    [3, 2, 6, 7]), ("down",  [4, 5, 1, 0]),
            ):
                entry = (corners[idxs], uv_map[fname], None)
                all_faces.append(entry)
                if pk == "helmet":
                    helm_faces.append(entry)
                if pk is not None:
                    piece_faces[pk].append(entry)

    def raster(view_mat, w, h, sc, center, ambient=0.60, bg=(34, 34, 36), face_list=None):
        use_faces = all_faces if face_list is None else face_list
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.32, 0.72, -0.61], dtype=np.float64)
        ld /= np.linalg.norm(ld)
        cx, cy = w * 0.5, h * 0.52

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
                    lit = np.where(g_a > 0.5, glow_img[v[vis], u[vis], :3] * 1.08, lit)
                    fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    ctr_full = np.array([0.0, 18.2, -0.2], dtype=np.float64)

    tw, th = 370, 580
    t_strip = Image.new("RGB", (tw * 4, th), (34, 34, 36))
    tdr = ImageDraw.Draw(t_strip)
    for idx, (yw, pt, label) in enumerate((
        (180, 4, "1. FRONT"),
        (+94, 4, "2. SIDE (RIGHT)"),
        (0,   4, "3. BACK"),
        (-94, 4, "4. SIDE (LEFT)"),
    )):
        panel = raster(cam_mat(yw, pt), tw, th, 13.4, ctr_full)
        t_strip.paste(panel, (idx * tw, 0))
        tdr.text((idx * tw + 14, 12), label, fill=(232, 212, 250))

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out_turn = PREVIEW_DIR / "cerberus_turnaround_4angle_preview.png"
    t_strip.save(out_turn)
    print(f"TURNAROUND_OK: {out_turn}")

    pw, ph = 520, 520
    sheet = Image.new("RGB", (pw * 2, ph * 2), (24, 24, 28))
    dr = ImageDraw.Draw(sheet)
    for im, gx, gy, title in (
        (raster(cam_mat(180,  4), pw, ph, 12.6, ctr_full), 0, 0, "1. FRONT - ALL-BLACKSTONE + 4 RAISED PLATES + 0% BROWN"),
        (raster(cam_mat(  0,  4), pw, ph, 12.6, ctr_full), 1, 0, "2. BACK - DORSAL STONE RIDGE + NARROW MAGMA + HEEL FIRE"),
        (raster(cam_mat(+90,  4), pw, ph, 12.6, ctr_full), 0, 1, "3. SIDE - SLEEK OBSIDIAN HORNS + BARE FOREARMS + CLAWS"),
        (raster(cam_mat(150, 10), pw, ph, 12.8, ctr_full), 1, 1, "4. 3/4 HERO - CERBERUS BLACKSTONE & OBSIDIAN ARMOR"),
    ):
        sheet.paste(im, (gx * pw, gy * ph))
        dr.text((gx * pw + 10, gy * ph + 10), title, fill=(214, 198, 242))
    out_4 = PREVIEW_DIR / "cerberus_armor_4angle_preview.png"
    sheet.save(out_4)
    print(f"PREVIEW_OK: {out_4}")

    ipw, iph = 440, 440
    isheet = Image.new("RGB", (ipw * 2, iph * 2), (24, 24, 28))
    idr = ImageDraw.Draw(isheet)
    for im, gx, gy, title in (
        (raster(cam_mat(208, 12), ipw, iph, 17.5, np.array([0.0, 29.8, -1.0]), face_list=piece_faces["helmet"]), 0, 0, "CERBERUS (HELMET) - FULL-ENCLOSED BEAST MASK"),
        (raster(cam_mat(150, 12), ipw, iph, 13.8, np.array([0.0, 20.0, -0.4]), face_list=piece_faces["chest"]),  1, 0, "CERBERUS (CHESTPLATE) - 1.0F BLACKSTONE + LARGE 3-HEAD PAULDRONS"),
        (raster(cam_mat(150, 14), ipw, iph, 23.0, np.array([0.0,  8.6, -0.4]), face_list=piece_faces["legs"]),   0, 1, "CERBERUS (LEGGINGS) - 0.5F BLACKSTONE + LAVA VEINS"),
        (raster(cam_mat(145, 16), ipw, iph, 23.0, np.array([0.0,  2.8, -0.4]), face_list=piece_faces["boots"]),  1, 1, "CERBERUS (BOOTS) - 1.0F BLACKSTONE + OBSIDIAN CLAWS"),
    ):
        isheet.paste(im, (gx * ipw, gy * iph))
        idr.text((gx * ipw + 10, gy * iph + 10), title, fill=(255, 172, 114))
    out_iso = PREVIEW_DIR / "cerberus_isolated_pieces_preview.png"
    isheet.save(out_iso)
    print(f"ISOLATED_PREVIEW_OK: {out_iso}")

    ctr_head = np.array([0.0, 29.8, -0.8], dtype=np.float64)
    hpw, hph = 440, 440
    hsheet = Image.new("RGB", (hpw * 2, hph * 2), (24, 24, 28))
    hdr = ImageDraw.Draw(hsheet)
    for im, gx, gy, title in (
        (raster(cam_mat(180,  4), hpw, hph, 17.8, ctr_head, face_list=helm_faces), 0, 0, "HELM 1. FRONT - FULL ENCLOSED + FIERY MAW & FANGS"),
        (raster(cam_mat(  0,  4), hpw, hph, 17.8, ctr_head, face_list=helm_faces), 1, 0, "HELM 2. BACK - SPIKY MANE + CROWN CREST + HORNS"),
        (raster(cam_mat(+90,  0), hpw, hph, 17.8, ctr_head, face_list=helm_faces), 0, 1, "HELM 3. SIDE - WOLF SNOUT + JAGGED JAW + MANE"),
        (raster(cam_mat(146, 10), hpw, hph, 17.8, ctr_head, face_list=helm_faces), 1, 1, "HELM 4. 3/4 - FULL-HEAD CERBERUS BEAST HELMET"),
    ):
        hsheet.paste(im, (gx * hpw, gy * hph))
        hdr.text((gx * hpw + 10, gy * hph + 10), title, fill=(214, 198, 242))
    out_helm = PREVIEW_DIR / "cerberus_helmet_4angle_preview.png"
    hsheet.save(out_helm)
    print(f"HELM_PREVIEW_OK: {out_helm}")


if __name__ == "__main__":
    geo_data, cubes = build_cerberus_armor_geo()
    build_cerberus_armor_animations()
    diff_img, glow_img = build_cerberus_armor_textures()
    build_cerberus_item_icons()
    render_cerberus_previews(geo_data, diff_img, glow_img)
    print(f"ALL_DONE: Cerberus Sleek Blackstone & Obsidian Edition ({TEX_W}x{TEX_H}, {cubes} cubes)")
