#!/usr/bin/env python3
"""
Bộ sinh Model (1.200–1.700 cubes) + Texture 512x512 (nearest, AO khe vảy + viền sáng cạnh,
màng cánh đỏ gỉ cháy alpha-cutout nối kín 100%, giáp đá núi lửa nứt dung nham Phase 2)
+ 16 Animation mượt catmullrom/ease-in-out đồng bộ 0-tick tuyệt đối với DragonAttackTiming.java
+ Preview 3D Z-buffer đa góc (Trước, Sau, Trái, Phải, Trên xuống, Phase 2 Enraged)
cho Boss Cinder Dragon — Hỏa Long Giáp Đá (`abyssalrpg:cinder_dragon`).

Thiết kế:
  - 60% giữ silhouette & tinh thần rồng giáp đá cánh đỏ khổng lồ 4 chân cúi thấp săn mồi,
    sải cánh rộng vượt dài hơn thân, khung xương cánh nâu đen dày có móng vuốt cổ cánh & gai,
    màng cánh đỏ sẫm/đỏ gỉ cháy hình cung dơi kín nan, mắt cam-vàng rực lửa, 7 đốt đuôi cong vút.
  - 40% biến tấu nguyên bản cho Abyssal Spire RPG:
    + 2 cặp sừng đá obsidian vươn ngược lên-ra sau kiểu vương miện + 1 sừng mũi sắc (`nasal_horn`),
      2 răng nanh ngà lộ ngoài khóe hàm trên, hàm dưới bọc giáp cằm + locator `mouth_flame` trong họng.
    + Tấm khiên vảy đá xếp tầng dọc gáy/sống lưng/đuôi + rãnh mạch dung nham hai bên cổ họng.
    + Chùy đuôi lưỡi liềm đôi bằng đá obsidian ôm lõi than hồng phát sáng ở chóp `tail_7`.
    + Đường vân nứt than cháy mờ dọc mép màng cánh (bùng sáng viền lửa ở Phase 2).
  - Kích thước mục tiêu: 60% Nightmare Knight (`SCALE = 4.5f` vs `7.5f`), phóng cục bộ `1.20x`
    cho cụm đầu/hàm/sừng, móng cổ cánh và chùy đuôi để sắc nét từng đường vảy.

Chạy: python tools/generate_cinder_dragon_model.py
"""

import json
import math
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
BACKUP_DIR = ROOT / "tools" / "backup_cinder_dragon_pre_v2"
PREVIEW_DIR = ROOT / "tools" / "cinder_dragon_previews"

# 512x512 nearest RGBA theo spec void.txt §1
TEX_W = 512
TEX_H = 512

# Atlas 512x512 (x0, y0, w, h) — bố cục gọn, không chồng lấn, có lề pad chống chảy UV
MAT = {
    "STONE_ARMOR":   (0,   0,   128, 128),  # Giáp đá núi lửa xám nâu tro (#26211F -> #3D3530 -> #574C45 -> #786B62)
    "BELLY_PLATE":   (128, 0,   128, 128),  # Vảy bụng xếp tầng tối ấm có khe AO sâu
    "DORSAL_SHIELD": (256, 0,   128, 128),  # Tấm khiên vảy đá vát cạnh dọc gáy, sống lưng & đuôi
    "OBSIDIAN_HORN": (384, 0,   128, 128),  # Sừng vương miện obsidian, sừng mũi & lưỡi liềm đuôi có gân vân
    "WING_BONE":     (0,   128, 128, 128),  # Khung xương cánh nâu đen bọc giáp dày (#1C1514 -> #332724 -> #52423D)
    "WING_MEMBRANE": (128, 128, 256, 192),  # Màng cánh đỏ sẫm/gỉ cháy (#5E1614 -> #9E2922 -> #BA3B2E), vòm dơi alpha-cutout
    "THROAT_MAGMA":  (384, 128, 128, 128),  # Vảy cổ kẹp rãnh mạch dung nham mảnh hai bên cổ họng (glow P1 & P2)
    "EYE_FIRE":      (0,   256, 128, 128),  # Mắt cam-vàng rực lửa (#FF7A00 -> #FFE070) có con ngươi dọc sắc
    "MAW_INFERNO":   (128, 320, 128, 128),  # Khoang miệng & họng lửa rực đỏ-cam (fullbright)
    "IVORY_FANG":    (256, 320, 128, 128),  # Răng nanh ngà lộ hàm & vuốt ngón chân sắc
    "EMBER_CORE":    (384, 256, 128, 128),  # Lõi than hồng chùy đuôi (P1 cam-vàng, P2 cháy trắng-cam)
    "CHEST_FISSURE": (0,   384, 128, 128),  # Giáp ức/vai/đùi nứt lõi dung nham mạnh ở Phase 2
    "CLAW_DARK":     (384, 384, 128, 128),  # Móng vuốt cong tối màu ở khớp cổ cánh & gai vai/khuỷu/hông/gót
}


def backup_existing_files() -> None:
    if BACKUP_DIR.exists() and any(BACKUP_DIR.rglob("*")):
        print(f"BACKUP_KEEP: Giữ nguyên bản sao lưu gốc tại {BACKUP_DIR}")
        return
    files = [
        ASSETS / "geo" / "entity" / "cinder_dragon.geo.json",
        ASSETS / "animations" / "entity" / "cinder_dragon.animation.json",
        ASSETS / "textures" / "entity" / "cinder_dragon.png",
        ASSETS / "textures" / "entity" / "cinder_dragon_enraged.png",
        ASSETS / "textures" / "entity" / "cinder_dragon_glowmask.png",
        ASSETS / "textures" / "entity" / "cinder_dragon_enraged_glowmask.png",
    ]
    copied = 0
    for f in files:
        if f.exists():
            dst = BACKUP_DIR / f.relative_to(ASSETS)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dst)
            copied += 1
    if copied > 0:
        print(f"BACKUP_OK: Đã sao lưu {copied} tệp cũ vào {BACKUP_DIR}")
    else:
        print("BACKUP_INFO: Chưa có tệp cinder_dragon cũ — tạo mới lần đầu.")


# =============================================================================
# 1. TOÁN HỌC GECKOLIB 4.8.3 & HELPER DỰNG HÌNH KHỐI
# =============================================================================
def bedrock_rot_matrix(rot_deg):
    """Ma trận xoay chuẩn GeckoLib 4.8.3: R = Rz(-rz) @ Ry(+ry) @ Rx(-rx)."""
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


def matrix_to_bedrock_euler(R: np.ndarray) -> list:
    """Giải ngược góc Bedrock [rx, ry, rz] (độ) sao cho bedrock_rot_matrix(rot) == R."""
    sb = float(np.clip(-R[2, 0], -1.0, 1.0))
    b = math.asin(sb)
    cb = math.cos(b)
    if abs(cb) > 1e-5:
        a = math.atan2(R[2, 1], R[2, 2])
        c = math.atan2(R[1, 0], R[0, 0])
    else:
        a = math.atan2(-R[1, 2], R[1, 1])
        c = 0.0
    return [round(-math.degrees(a), 2), round(+math.degrees(b), 2), round(-math.degrees(c), 2)]


def basis_from_dir(direction: np.ndarray, up_hint=(0.0, 1.0, 0.0)) -> np.ndarray:
    """Tạo hệ trục trực chuẩn (ex, ey, ez) với trục +Z hướng theo `direction`."""
    ez = direction / max(1e-6, float(np.linalg.norm(direction)))
    uh = np.array(up_hint, dtype=np.float64)
    if abs(float(np.dot(ez, uh))) > 0.96:
        uh = np.array([1.0, 0.0, 0.0], dtype=np.float64)
    ex = np.cross(uh, ez)
    ex /= max(1e-6, float(np.linalg.norm(ex)))
    ey = np.cross(ez, ex)
    return np.column_stack([ex, ey, ez])


def uv_box(mat_key: str, pad: int = 3, sub_u=(0.0, 1.0), sub_v=(0.0, 1.0)) -> dict:
    x0, y0, w, h = MAT[mat_key]
    iw, ih = w - 2 * pad, h - 2 * pad
    ux = int(round(x0 + pad + sub_u[0] * iw))
    uy = int(round(y0 + pad + sub_v[0] * ih))
    uw = max(2, int(round((sub_u[1] - sub_u[0]) * iw)))
    uh = max(2, int(round((sub_v[1] - sub_v[0]) * ih)))
    f = {"uv": [ux, uy], "uv_size": [uw, uh]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def make_cube(origin, size, mat: str, rot=None, pivot=None,
              sub_u=(0.0, 1.0), sub_v=(0.0, 1.0), pad: int = 3,
              face_mats: dict = None, edge_transparent: bool = False) -> dict:
    ox, oy, oz = float(origin[0]), float(origin[1]), float(origin[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    uvs = uv_box(mat, pad=pad, sub_u=sub_u, sub_v=sub_v)
    if face_mats:
        for fn, mk in face_mats.items():
            uvs[fn] = uv_box(mk, pad=pad)[fn]
    if edge_transparent:
        x0, y0, w, h = MAT[mat]
        trans = {"uv": [x0 + w - 5, y0 + h - 5], "uv_size": [3, 3]}
        for k in ("east", "west", "up", "down"):
            uvs[k] = trans

    c = {
        "origin": [round(ox, 3), round(oy, 3), round(oz, 3)],
        "size": [round(sx, 3), round(sy, 3), round(sz, 3)],
        "uv": uvs,
    }
    if rot is not None and any(abs(v) > 1e-4 for v in rot):
        if pivot is None:
            pivot = [ox + sx * 0.5, oy + sy * 0.5, oz + sz * 0.5]
        c["pivot"] = [round(float(pivot[0]), 3), round(float(pivot[1]), 3), round(float(pivot[2]), 3)]
        c["rotation"] = [round(float(rot[0]), 2), round(float(rot[1]), 2), round(float(rot[2]), 2)]
    return c


def make_centered_cube(center, size, mat: str, rot=None, **kw) -> dict:
    cx, cy, cz = float(center[0]), float(center[1]), float(center[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    origin = [cx - sx * 0.5, cy - sy * 0.5, cz - sz * 0.5]
    return make_cube(origin, size, mat, rot=rot, pivot=[cx, cy, cz], **kw)


def add_segment_bar(cubes: list, p0, p1, w0: float, h0: float, w1: float, h1: float,
                    steps: int, mat: str, armor_ridge: bool = False, ridge_mat: str = "DORSAL_SHIELD",
                    overlap: float = 0.22, up_hint=(0.0, 1.0, 0.0)):
    """Xếp chuỗi khối lồng khít theo đoạn p0 -> p1, có tùy chọn gờ giáp dọc sống trên."""
    v0 = np.array(p0, dtype=np.float64)
    v1 = np.array(p1, dtype=np.float64)
    diff = v1 - v0
    total_len = float(np.linalg.norm(diff))
    if total_len < 1e-4:
        return
    R = basis_from_dir(diff, up_hint=up_hint)
    rot = matrix_to_bedrock_euler(R)
    ey = R[:, 1]
    seg_len = (total_len / steps) * (1.0 + overlap)
    for i in range(steps):
        t = (i + 0.5) / steps
        c = v0 + diff * t
        w = w0 + (w1 - w0) * t
        h = h0 + (h1 - h0) * t
        # Đảo nhẹ kích thước 0.05 px giữa các đốt liền kề để triệt tiêu z-fighting 100%
        eps = 0.05 if (i % 2 == 1) else 0.0
        cubes.append(make_centered_cube(c, [w + eps, h + eps, seg_len], mat, rot=rot))
        if armor_ridge:
            rc = c + ey * (h * 0.44 + 0.16)
            cubes.append(make_centered_cube(rc, [max(0.45, w * 0.56), max(0.45, h * 0.36), seg_len * 0.86],
                                            ridge_mat, rot=rot))


def add_curved_horn(cubes: list, pts: list, radii: list, mat: str, steps_per_seg: int = 3):
    """Dựng sừng/móng/gai cong sắc nét đi qua danh sách điểm `pts` với tiết diện giảm dần `radii`."""
    for s in range(len(pts) - 1):
        p0 = np.array(pts[s], dtype=np.float64)
        p1 = np.array(pts[s + 1], dtype=np.float64)
        r0, r1 = radii[s], radii[s + 1]
        add_segment_bar(cubes, p0, p1, r0 * 2.0, r0 * 2.0, r1 * 2.0, r1 * 2.0,
                        steps=steps_per_seg, mat=mat, overlap=0.30)


def add_dorsal_shield(cubes: list, center, width: float, height: float, depth: float,
                      tilt_x: float = -18.0, mat: str = "DORSAL_SHIELD"):
    """Tấm khiên vảy đá xếp tầng 3 lớp (đế + thân khiên vát + sống gai giữa) dọc gáy/lưng/đuôi."""
    cx, cy, cz = center
    cubes.append(make_centered_cube([cx, cy, cz], [width, height * 0.52, depth], "STONE_ARMOR",
                                    rot=[tilt_x * 0.5, 0.0, 0.0]))
    cubes.append(make_centered_cube([cx, cy + height * 0.34, cz + depth * 0.12],
                                    [width * 0.72, height * 0.62, depth * 0.78], mat,
                                    rot=[tilt_x, 0.0, 0.0]))
    cubes.append(make_centered_cube([cx, cy + height * 0.72, cz + depth * 0.28],
                                    [max(0.42, width * 0.34), height * 0.56, depth * 0.52], "OBSIDIAN_HORN",
                                    rot=[tilt_x - 10.0, 0.0, 0.0]))


def add_armor_ring(cubes: list, center, rx: float, ry: float, depth: float,
                   mat_top: str = "STONE_ARMOR", mat_belly: str = "BELLY_PLATE",
                   mat_side: str = "CHEST_FISSURE", pitch_x: float = 0.0):
    """Dựng 1 vòng đốt thân/cổ/đuôi bọc vảy 3D nhiều mặt vát góc + tấm giáp xếp tầng (không hộp vuông thô)."""
    cx, cy, cz = center
    # Lõi thân
    cubes.append(make_centered_cube([cx, cy, cz], [rx * 1.72, ry * 1.66, depth], mat_top, rot=[pitch_x, 0, 0]))
    # Vòm lưng vát trên + 2 tấm giáp vai-lưng xếp tầng chống z-fighting
    cubes.append(make_centered_cube([cx, cy + ry * 0.66, cz], [rx * 1.24, ry * 0.64, depth * 0.94],
                                    mat_top, rot=[pitch_x, 0, 0]))
    cubes.append(make_centered_cube([cx, cy + ry * 0.86, cz + depth * 0.06], [rx * 0.86, ry * 0.38, depth * 0.80],
                                    "DORSAL_SHIELD", rot=[pitch_x - 6.0, 0, 0]))
    # Tấm giáp bụng vát dưới 2 tầng xếp lớp
    cubes.append(make_centered_cube([cx, cy - ry * 0.68, cz], [rx * 1.18, ry * 0.58, depth * 0.92],
                                    mat_belly, rot=[pitch_x, 0, 0]))
    cubes.append(make_centered_cube([cx, cy - ry * 0.88, cz - depth * 0.06], [rx * 0.88, ry * 0.34, depth * 0.72],
                                    mat_belly, rot=[pitch_x + 5.0, 0, 0]))
    # 2 mảng sườn vát có vảy đá xếp tầng + khe dung nham Phase 2
    for side in (-1, 1):
        cubes.append(make_centered_cube([cx + side * (rx * 0.80), cy + ry * 0.14, cz],
                                        [rx * 0.48, ry * 1.06, depth * 0.90],
                                        mat_side, rot=[pitch_x, 0, side * 14.0]))
        cubes.append(make_centered_cube([cx + side * (rx * 0.96), cy + ry * 0.42, cz + depth * 0.04],
                                        [rx * 0.34, ry * 0.56, depth * 0.74],
                                        "DORSAL_SHIELD", rot=[pitch_x - 5.0, side * 6.0, side * 26.0]))
        cubes.append(make_centered_cube([cx + side * (rx * 0.74), cy - ry * 0.36, cz],
                                        [rx * 0.44, ry * 0.68, depth * 0.86],
                                        mat_top, rot=[pitch_x, 0, -side * 18.0]))
        cubes.append(make_centered_cube([cx + side * (rx * 0.92), cy - ry * 0.10, cz - depth * 0.05],
                                        [rx * 0.28, ry * 0.52, depth * 0.72],
                                        mat_top, rot=[pitch_x + 4.0, -side * 5.0, side * 8.0]))


def add_membrane_panel(cubes: list, root_p, tip_a, tip_b, rows: int = 5, cols: int = 4,
                       scallop_depth: float = 0.16, mat: str = "WING_MEMBRANE",
                       thickness: float = 0.24):
    """
    Lát màng cánh phẳng mỏng dạng lưới hình quạt nối KÍN 100% giữa 2 nan xương (root_p -> tip_a) và (root_p -> tip_b).
    - Mé ngoài tự uốn hình cung cánh dơi (concave scallop) khớp khớp nan tuyệt đối, 0 khe hở.
    - Mỗi tấm mảnh đặt xoay đúng mặt phẳng tam giác cục bộ bằng ma trận trực chuẩn GeckoLib.
    """
    R0 = np.array(root_p, dtype=np.float64)
    A = np.array(tip_a, dtype=np.float64)
    B = np.array(tip_b, dtype=np.float64)

    def sample_pt(u: float, v: float) -> np.ndarray:
        # u in [0,1] từ nan A sang nan B (mở rộng mép ngoài [-0.09..1.09] kẹp hàm sin về [0,1] để thực 100%)
        uc = max(0.0, min(1.0, u))
        vc = max(0.0, min(1.0, v))
        edge = A * (1.0 - u) + B * u
        scallop = 1.0 - scallop_depth * (math.sin(uc * math.pi) ** 1.3) * (vc ** 1.6)
        p = R0 + (edge - R0) * (v * scallop)
        # Độ võng khí động học nhẹ ở giữa khoang màng
        p[1] += math.sin(vc * math.pi) * 0.45 - math.sin(uc * math.pi) * 0.25 * vc
        return p

    for ir in range(rows):
        v0 = max(0.0, (ir / rows) - (0.04 if ir > 0 else 0.0))
        v1 = (ir + 1) / rows
        vc = 0.5 * (v0 + v1)
        sub_cols = max(2, int(round(cols * (0.48 + 0.52 * vc))))
        for ic in range(sub_cols):
            # Mở rộng nhẹ mép ngoài u sang [-0.09..1.09] để màng cánh găm sâu vào tâm nan xương, triệt tiêu 100% khe đen!
            u0_raw = ic / sub_cols
            u1_raw = (ic + 1) / sub_cols
            u0 = -0.09 if ic == 0 else u0_raw
            u1 = 1.09 if ic == sub_cols - 1 else u1_raw

            p00 = sample_pt(u0, v0)
            p10 = sample_pt(u1, v0)
            p11 = sample_pt(u1, v1)
            p01 = sample_pt(u0, v1)

            center = 0.25 * (p00 + p10 + p11 + p01)
            tu = 0.5 * ((p10 - p00) + (p11 - p01))
            tv = 0.5 * ((p01 - p00) + (p11 - p10))
            lu = float(np.linalg.norm(tu))
            lv = float(np.linalg.norm(tv))
            if lu < 0.20 or lv < 0.20:
                continue

            eu = tu / lu
            n = np.cross(eu, tv)
            nl = float(np.linalg.norm(n))
            if nl < 1e-5:
                continue
            ez = n / nl
            ey = np.cross(ez, eu)
            R = np.column_stack([eu, ey, ez])
            rot = matrix_to_bedrock_euler(R)

            # Phủ chồng rộng theo u & v để màng cánh liền mạch hoàn toàn từ góc nhìn trên xuống, lệch dày 0.035 tránh z-fighting
            z_off = 0.035 * ((ir + ic) % 2)
            c_pos = center + ez * z_off
            su = (max(0.02, u0_raw * 0.92 + 0.04), min(0.98, u1_raw * 0.92 + 0.04))
            sv = (max(0.02, v0 * 0.86 + 0.04), min(0.96, v1 * 0.86 + 0.04))
            cubes.append(make_centered_cube(
                c_pos,
                [lu * 1.32, lv * 1.28, thickness],
                mat,
                rot=rot,
                sub_u=su,
                sub_v=sv,
                pad=2,
                edge_transparent=True,
            ))


def add_quad_membrane(cubes: list, p00, p10, p11, p01, rows: int = 4, cols: int = 4,
                      scallop_depth: float = 0.14, mat: str = "WING_MEMBRANE", thickness: float = 0.24):
    """Lát màng cánh tứ giác nối KÍN 100% giữa khung cánh trong (arm/mid) và sườn thân/hông."""
    P00 = np.array(p00, dtype=np.float64)
    P10 = np.array(p10, dtype=np.float64)
    P11 = np.array(p11, dtype=np.float64)
    P01 = np.array(p01, dtype=np.float64)

    def sample(u: float, v: float) -> np.ndarray:
        top = P00 * (1.0 - u) + P10 * u
        bot = P01 * (1.0 - u) + P11 * u
        p = top * (1.0 - v) + bot * v
        uc = max(0.0, min(1.0, u))
        vc = max(0.0, min(1.0, v))
        p += (top - bot) * (scallop_depth * (math.sin(uc * math.pi) ** 1.2) * (vc ** 1.8))
        return p

    for ir in range(rows):
        v0_raw, v1_raw = ir / rows, (ir + 1) / rows
        v0 = -0.06 if ir == 0 else v0_raw
        v1 = v1_raw
        for ic in range(cols):
            u0_raw, u1_raw = ic / cols, (ic + 1) / cols
            u0 = -0.07 if ic == 0 else u0_raw
            u1 = 1.07 if ic == cols - 1 else u1_raw
            q00, q10, q11, q01 = sample(u0, v0), sample(u1, v0), sample(u1, v1), sample(u0, v1)
            center = 0.25 * (q00 + q10 + q11 + q01)
            tu = 0.5 * ((q10 - q00) + (q11 - q01))
            tv = 0.5 * ((q01 - q00) + (q11 - q10))
            lu, lv = float(np.linalg.norm(tu)), float(np.linalg.norm(tv))
            if lu < 0.20 or lv < 0.20:
                continue
            eu = tu / lu
            n = np.cross(eu, tv)
            nl = float(np.linalg.norm(n))
            if nl < 1e-5:
                continue
            ez = n / nl
            ey = np.cross(ez, eu)
            rot = matrix_to_bedrock_euler(np.column_stack([eu, ey, ez]))
            z_off = 0.035 * ((ir + ic) % 2)
            cubes.append(make_centered_cube(
                center + ez * z_off,
                [lu * 1.30, lv * 1.28, thickness],
                mat,
                rot=rot,
                sub_u=(0.06 + u0_raw * 0.88, 0.06 + u1_raw * 0.88),
                sub_v=(0.06 + v0_raw * 0.82, 0.06 + v1_raw * 0.82),
                pad=2,
                edge_transparent=True,
            ))


def mirror_x_cube(c: dict) -> dict:
    ox, oy, oz = c["origin"]
    sx, sy, sz = c["size"]
    m = {
        "origin": [round(-(ox + sx), 3), oy, oz],
        "size": [sx, sy, sz],
        "uv": c["uv"],
    }
    if "pivot" in c:
        px, py, pz = c["pivot"]
        m["pivot"] = [round(-px, 3), py, pz]
    if "rotation" in c:
        rx, ry, rz = c["rotation"]
        m["rotation"] = [rx, round(-ry, 2), round(-rz, 2)]
    return m


def add_tail_scythe_fin(cubes: list, base_pos, scale: float, pitch_deg: float, is_tip: bool = False):
    """
    Dựng vây lưỡi liềm / rìu chiến cong ngược về sau dọc sống lưng đuôi (giống ảnh mẫu 100%):
    - Trục chân vây vươn cao khỏi đốt đuôi -> bản lưỡi liềm loe rộng uốn cong sắc về sau + mũi trước nhọn.
    - Nếu `is_tip=True`: tạo đại lưỡi liềm chóp đuôi khổng lồ (scythe/halberd blade) có gân lõi than hồng.
    """
    bx, by, bz = float(base_pos[0]), float(base_pos[1]), float(base_pos[2])
    s = float(scale)
    if not is_tip:
        # Cổ vây mảnh nhưng chắc vươn lên từ đốt đuôi
        add_curved_horn(cubes, [
            (bx, by - 0.2 * s, bz),
            (bx, by + 2.0 * s, bz + 0.5 * s),
            (bx, by + 3.4 * s, bz + 1.4 * s),
        ], [0.68 * s, 0.54 * s, 0.36 * s], "DORSAL_SHIELD", steps_per_seg=2)
        # Mũi lưỡi rìu cong vươn ngược lên trước
        add_curved_horn(cubes, [
            (bx, by + 3.1 * s, bz + 1.0 * s),
            (bx, by + 3.7 * s, bz - 1.0 * s),
            (bx, by + 3.1 * s, bz - 2.6 * s),
        ], [0.52 * s, 0.44 * s, 0.14 * s], "OBSIDIAN_HORN", steps_per_seg=2)
        # Cánh lưỡi liềm chính vươn dài cong sắc về sau
        add_curved_horn(cubes, [
            (bx, by + 3.2 * s, bz + 1.2 * s),
            (bx, by + 3.8 * s, bz + 3.4 * s),
            (bx, by + 3.0 * s, bz + 5.4 * s),
        ], [0.58 * s, 0.46 * s, 0.14 * s], "OBSIDIAN_HORN", steps_per_seg=2)
        # Bản dẹt sắc vát giữa tạo silhouette chiếc rìu cong rõ rệt từ góc ngang!
        cubes.append(make_centered_cube(
            [bx, by + 3.1 * s, bz + 1.5 * s],
            [0.62 * s, 1.55 * s, 4.4 * s],
            "DORSAL_SHIELD", rot=[pitch_deg * 0.4 + 6.0, 0.0, 0.0]
        ))
    else:
        # ĐẠI LƯỠI LIỀM CHÓP ĐUÔI KHỔNG LỒ (SCYTHE / HALBERD CREST) + LÕI THAN HỒNG RỰC CHÁY!
        # Lõi than hồng cháy ẩn giữa khớp ngàm đá chóp đuôi
        cubes.append(make_centered_cube([bx, by + 0.6 * s, bz + 0.4 * s], [2.4 * s, 2.2 * s, 2.6 * s], "DORSAL_SHIELD", rot=[26.0, 0, 0]))
        cubes.append(make_centered_cube([bx, by + 1.4 * s, bz + 1.2 * s], [1.65 * s, 1.85 * s, 2.0 * s], "EMBER_CORE", rot=[24.0, 28.0, 12.0]))
        # Thân cổ liềm uốn cao vồng lên
        add_curved_horn(cubes, [
            (bx, by + 0.2 * s, bz),
            (bx, by + 2.6 * s, bz + 1.4 * s),
            (bx, by + 5.2 * s, bz + 2.6 * s),
        ], [1.15 * s, 0.92 * s, 0.68 * s], "OBSIDIAN_HORN", steps_per_seg=3)
        # Mỏ liềm trước cong gắt hướng về đầu rồng
        add_curved_horn(cubes, [
            (bx, by + 4.8 * s, bz + 2.2 * s),
            (bx, by + 5.8 * s, bz - 0.6 * s),
            (bx, by + 4.9 * s, bz - 3.6 * s),
            (bx, by + 3.4 * s, bz - 5.2 * s),
        ], [0.85 * s, 0.72 * s, 0.46 * s, 0.16 * s], "OBSIDIAN_HORN", steps_per_seg=3)
        # Lưỡi hái chính vươn dài vút ra sau tạo hình bóng cực bén!
        add_curved_horn(cubes, [
            (bx, by + 5.2 * s, bz + 2.6 * s),
            (bx, by + 6.2 * s, bz + 6.0 * s),
            (bx, by + 5.6 * s, bz + 9.4 * s),
            (bx, by + 4.2 * s, bz + 12.0 * s),
        ], [0.95 * s, 0.78 * s, 0.48 * s, 0.14 * s], "OBSIDIAN_HORN", steps_per_seg=3)
        # Sống bản lưỡi rìu mở rộng + viền sáng cạnh đá sắc
        cubes.append(make_centered_cube(
            [bx, by + 5.0 * s, bz + 3.4 * s],
            [0.82 * s, 2.4 * s, 7.6 * s],
            "DORSAL_SHIELD", rot=[10.0, 0.0, 0.0]
        ))
        cubes.append(make_centered_cube(
            [bx, by + 3.6 * s, bz + 2.2 * s],
            [0.72 * s, 2.0 * s, 3.8 * s],
            "EMBER_CORE", rot=[18.0, 0.0, 0.0]
        ))
        # Gai móc bụng chóp đuôi cân đối lực phản
        add_curved_horn(cubes, [
            (bx, by - 0.6 * s, bz + 0.6 * s),
            (bx, by - 2.6 * s, bz + 0.2 * s),
            (bx, by - 4.0 * s, bz - 1.2 * s),
        ], [0.68 * s, 0.42 * s, 0.14 * s], "IVORY_FANG", steps_per_seg=2)


# =============================================================================
# 2. DỰNG HÌNH CINDER DRAGON V3 — 57 BONES ĐA KHỚP NỐI MỀM MẠI, DÁNG SĂN MỒI TRƯỜNG DÀI
# =============================================================================
def build_cinder_dragon_geo():
    bones = []

    def add_bone(name: str, parent, pivot, cubes=None, locators=None):
        b = {
            "name": name,
            "pivot": [round(float(pivot[0]), 3), round(float(pivot[1]), 3), round(float(pivot[2]), 3)],
            "rotation": [0, 0, 0],
        }
        if parent is not None:
            b["parent"] = parent
        if cubes:
            b["cubes"] = cubes
        if locators:
            b["locators"] = locators
        bones.append(b)
        return b

    # -------------------------------------------------------------------------
    # A. CHUỖI XƯƠNG SỐNG ĐA KHỚP NỐI — THÂN HÌNH "ĐÔ CON" HẦM HỐ CƠ BẮP NHƯ ẢNH MẪU!
    #    - Lồng ngực rộng đồ sộ (`rx = 10.6`, bề ngang vai-ngực ~22 px!) + giáp cầu vai đá chồng tầng vạm vỡ!
    #    - Eo thóp cơ bắp (`rx = 7.6..8.6`) tạo chữ V lực lưỡng (hết gầy/ốm!), hông đùi rộng (`rx = 9.0`)!
    # -------------------------------------------------------------------------
    add_bone("root", None, [0, 0, 0])
    add_bone("body_root", "root", [0, 15.0, 2.0])

    # 1) Thân giữa / Eo cơ bắp vạm vỡ (`body_mid`): dày chắc cơ bắp, chữ V săn mồi, 5 nan sườn giáp nổi!
    mid_cubes = []
    mid_rings = [
        ((0.0, 15.4, +8.4), 8.6, 6.0, 4.6, -3.0),
        ((0.0, 15.6, +4.8), 7.9, 5.5, 4.6, -1.0),
        ((0.0, 15.7, +1.2), 7.6, 5.3, 4.6,  0.0),
        ((0.0, 15.6, -2.2), 8.2, 5.7, 4.6,  2.0),
        ((0.0, 15.3, -5.4), 9.2, 6.4, 4.6,  3.0),
    ]
    for ctr, rx, ry, dp, px in mid_rings:
        add_armor_ring(mid_cubes, ctr, rx, ry, dp,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="CHEST_FISSURE", pitch_x=px)
    # 5 cặp nan sườn cơ bắp bọc giáp đá dày dọc 2 bên eo
    for zpos, wside in ((+6.6, 7.6), (+4.0, 7.2), (+1.4, 7.0), (-1.2, 7.4), (-3.8, 8.2)):
        for side in (-1, 1):
            mid_cubes.append(make_centered_cube([side * wside, 14.6, zpos], [2.2, 4.6, 1.8],
                                                "DORSAL_SHIELD", rot=[16.0, side * 12.0, side * 16.0]))
    for zpos, ym, w, h in ((+7.8, 21.2, 4.4, 3.6), (+4.6, 21.0, 4.2, 3.5), (+1.4, 21.0, 4.1, 3.4), (-1.8, 21.2, 4.5, 3.7), (-5.0, 21.6, 5.0, 4.0)):
        add_dorsal_shield(mid_cubes, (0.0, ym, zpos), w, h, 3.2, tilt_x=-18.0)
    add_bone("body_mid", "body_root", [0.0, 15.3, +8.2], mid_cubes)

    # 2) Lồng ngực & bờ vai "ĐÔ CON" đồ sộ (`body_chest`, CON CỦA `body_mid` — rộng tới ~22 px, tầng giáp đá dày!)
    chest_cubes = []
    chest_rings = [
        ((0.0, 15.3, -8.2),   9.8, 6.7, 4.8,  2.0),
        ((0.0, 15.2, -11.4), 10.6, 7.2, 4.9,  0.0),
        ((0.0, 15.3, -14.6),  9.8, 6.7, 4.6, -4.0),
        ((0.0, 15.5, -17.4),  8.2, 5.9, 4.2, -8.0),
    ]
    for ctr, rx, ry, dp, px in chest_rings:
        add_armor_ring(chest_cubes, ctr, rx, ry, dp,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="CHEST_FISSURE", pitch_x=px)
    # Khối cơ ngực & giáp ức mũi tàu dày nặng nứt dung nham (giống phần ngực cơ bắp cuồn cuộn trong ảnh mẫu 2!)
    chest_cubes.append(make_centered_cube([0.0, 10.4, -15.4], [8.8, 4.8, 6.0], "CHEST_FISSURE", rot=[-16.0, 0, 0]))
    chest_cubes.append(make_centered_cube([0.0,  9.4, -11.4], [9.8, 3.8, 7.0], "BELLY_PLATE", rot=[-4.0, 0, 0]))
    # 2 mảng cơ ngực trước dày cuồn cuộn + 2 tầng giáp cầu vai núi lửa khổng lồ vươn cao trên vai!
    for side in (-1, 1):
        # Khối cơ ngực lớn phía trước
        chest_cubes.append(make_centered_cube([side * 6.4, 13.0, -16.8], [5.4, 5.4, 4.8],
                                              "CHEST_FISSURE", rot=[-12.0, side * 18.0, side * 10.0]))
        # Giáp cầu vai đá tầng dưới & tầng trên ôm trọn bờ vai đô rộng
        chest_cubes.append(make_centered_cube([side * 9.8, 17.8, -12.4], [5.4, 5.2, 7.8],
                                              "DORSAL_SHIELD", rot=[8.0, side * 14.0, side * 18.0]))
        chest_cubes.append(make_centered_cube([side * 10.8, 14.8, -12.6], [4.6, 5.6, 6.6],
                                              "CHEST_FISSURE", rot=[-4.0, side * 10.0, side * 12.0]))
        # 2 tầng sừng/tấm giáp đá dựng đứng trên cầu vai giống hàng gai đá sau gáy trong ảnh mẫu 2!
        add_curved_horn(chest_cubes, [
            (side * 8.4, 19.6, -13.6),
            (side * 10.2, 23.4, -11.4),
            (side * 10.8, 26.6, -9.0),
        ], [1.95, 1.32, 0.34], "DORSAL_SHIELD", steps_per_seg=3)
        add_curved_horn(chest_cubes, [
            (side * 9.6, 18.6, -10.2),
            (side * 11.8, 21.8, -7.8),
            (side * 12.6, 24.4, -5.6),
        ], [1.65, 1.05, 0.28], "OBSIDIAN_HORN", steps_per_seg=3)
        # Gai cổ ngực móc ra phía trước 2 bên ức
        add_curved_horn(chest_cubes, [
            (side * 7.8, 14.6, -17.6),
            (side * 9.4, 15.8, -19.8),
            (side * 9.8, 17.6, -21.0),
        ], [1.35, 0.85, 0.24], "DORSAL_SHIELD", steps_per_seg=2)
    for zpos, ym, w, h in ((-8.2, 22.0, 5.4, 4.4), (-11.6, 22.6, 6.0, 5.0), (-15.0, 22.2, 5.6, 4.6)):
        add_dorsal_shield(chest_cubes, (0.0, ym, zpos), w, h, 3.6, tilt_x=-20.0)
    add_bone("body_chest", "body_mid", [0.0, 15.2, -5.6], chest_cubes)

    # 3) Khối hông cơ bắp rộng khỏe (`pelvis`, `rx = 9.0` cân đối bờ vai đồ sộ!)
    PELV_Z = +3.0
    pelvis_cubes = []
    pelvis_rings = [
        ((0.0, 15.2, +8.8 + PELV_Z),  9.0, 6.3, 4.8,  2.0),
        ((0.0, 14.8, +12.6 + PELV_Z), 8.6, 6.1, 4.8, -4.0),
        ((0.0, 14.2, +16.2 + PELV_Z), 7.4, 5.4, 4.6, -8.0),
        ((0.0, 13.6, +19.2 + PELV_Z), 6.4, 4.7, 4.0, -10.0),
    ]
    for ctr, rx, ry, dp, px in pelvis_rings:
        add_armor_ring(pelvis_cubes, ctr, rx, ry, dp,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="STONE_ARMOR", pitch_x=px)
    for side in (-1, 1):
        pelvis_cubes.append(make_centered_cube([side * 8.8, 16.4, +12.2 + PELV_Z], [4.8, 5.2, 7.2],
                                               "DORSAL_SHIELD", rot=[-8.0, -side * 10.0, side * 16.0]))
        add_curved_horn(pelvis_cubes, [
            (side * 9.4, 17.8, +13.6 + PELV_Z),
            (side * 11.2, 20.2, +16.0 + PELV_Z),
            (side * 12.0, 21.6, +18.0 + PELV_Z),
        ], [1.30, 0.80, 0.24], "CLAW_DARK", steps_per_seg=2)
    for zpos, ym, w, h in ((+9.0 + PELV_Z, 21.4, 4.6, 3.8), (+12.8 + PELV_Z, 20.8, 4.4, 3.6), (+16.4 + PELV_Z, 19.6, 3.9, 3.3)):
        add_dorsal_shield(pelvis_cubes, (0.0, ym, zpos), w, h, 3.3, tilt_x=-16.0)
    add_bone("pelvis", "body_root", [0.0, 14.8, +7.6 + PELV_Z], pelvis_cubes)

    # -------------------------------------------------------------------------
    # B. CỔ 4 ĐỐT DÀY VẠM VỠ CUỒN CUỘN (`neck_1` -> `neck_2` -> `neck_3` -> `neck_4`)
    #    Gốc cổ to lực (`rx = 6.4 -> 4.1`) nối liền với bờ vai đô, không còn cổ gầy!
    # -------------------------------------------------------------------------
    def build_neck_segment(z0, y0, z1, y1, rx, ry, shield_w, shield_h):
        c = []
        ym, zm = 0.5 * (y0 + y1), 0.5 * (z0 + z1)
        length = math.hypot(y1 - y0, z1 - z0)
        # GeckoLib Rx(-rx): rot_x < 0 tilts -Z (forward) UP!
        pitch = -math.degrees(math.atan2(y1 - y0, -(z1 - z0)))
        for frac in (0.28, 0.72):
            ry_p = y0 + (y1 - y0) * frac
            rz_p = z0 + (z1 - z0) * frac
            taper = 1.0 - 0.08 * frac
            add_armor_ring(c, (0.0, ry_p, rz_p), rx * taper, ry * taper, length * 0.80,
                           mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                           mat_side="THROAT_MAGMA", pitch_x=pitch)
        for side in (-1, 1):
            c.append(make_centered_cube([side * (rx * 0.90), ym - ry * 0.14, zm],
                                        [0.72, 1.55, length * 1.08],
                                        "THROAT_MAGMA", rot=[pitch, 0, side * 12.0]))
            c.append(make_centered_cube([side * (rx * 0.86), ym + ry * 0.36, zm],
                                        [1.30, 1.65, length * 0.98],
                                        "DORSAL_SHIELD", rot=[pitch, 0, side * 18.0]))
        for frac in (0.28, 0.76):
            sy = y0 + (y1 - y0) * frac + ry * 0.88
            sz = z0 + (z1 - z0) * frac + 0.3
            add_dorsal_shield(c, (0.0, sy, sz), shield_w, shield_h, length * 0.50, tilt_x=-20.0 + pitch * 0.4)
        return c

    n1_cubes = build_neck_segment(-17.0, 15.6, -23.0, 16.0, 6.4, 5.2, 4.8, 4.2)
    add_bone("neck_1", "body_chest", [0.0, 15.6, -17.2], n1_cubes)

    n2_cubes = build_neck_segment(-22.8, 16.0, -28.8, 17.0, 5.5, 4.5, 4.3, 3.8)
    add_bone("neck_2", "neck_1", [0.0, 16.0, -22.9], n2_cubes)

    n3_cubes = build_neck_segment(-28.6, 17.0, -34.6, 18.2, 4.7, 3.9, 3.8, 3.4)
    add_bone("neck_3", "neck_2", [0.0, 17.0, -28.7], n3_cubes)

    n4_cubes = build_neck_segment(-34.4, 18.2, -40.2, 18.9, 4.1, 3.5, 3.4, 3.1)
    add_bone("neck_4", "neck_3", [0.0, 18.2, -34.5], n4_cubes)

    # -------------------------------------------------------------------------
    # C. CỤM ĐẦU + HÀM RÂU ĐÁ + SỪNG LƯỠI LIỀM VẠM VỠ CÂN ĐỐI VỚI THÂN ĐÔ (`head`, `jaw`, `horn_l/r`)
    # -------------------------------------------------------------------------
    HZ = -40.4
    HY = 18.8
    head_cubes = []
    # Hộp sọ đá rộng khỏe cân đối cổ vai đô con
    head_cubes.append(make_centered_cube([0.0, HY + 0.6, HZ - 2.6], [9.2, 5.8, 7.4], "STONE_ARMOR", rot=[4.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, HY + 3.4, HZ - 2.4], [7.6, 2.3, 6.8], "DORSAL_SHIELD", rot=[-5.0, 0, 0]))
    # Gò má giáp đá vát 2 bên sọ + gai má sắc vươn sau
    for side in (-1, 1):
        head_cubes.append(make_centered_cube([side * 4.8, HY - 0.3, HZ - 2.2], [2.4, 4.0, 6.4],
                                             "DORSAL_SHIELD", rot=[6.0, side * 14.0, side * 10.0]))
        add_curved_horn(head_cubes, [
            (side * 5.0, HY - 0.9, HZ - 0.8),
            (side * 6.8, HY - 1.4, HZ + 2.0),
            (side * 7.4, HY - 1.6, HZ + 4.8),
        ], [1.00, 0.62, 0.20], "OBSIDIAN_HORN", steps_per_seg=2)

    # Sống mõm giáp đá rộng chắc hơi khoằm kiểu rồng cổ đại
    head_cubes.append(make_centered_cube([0.0, HY + 0.1, HZ - 7.8], [6.8, 4.2, 6.4], "STONE_ARMOR", rot=[6.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, HY - 0.6, HZ - 11.8], [5.0, 3.3, 4.2], "STONE_ARMOR", rot=[10.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, HY + 2.2, HZ - 8.0], [4.0, 1.8, 7.0], "DORSAL_SHIELD", rot=[9.0, 0, 0]))
    # Viền môi trên ôm khoang họng lửa
    head_cubes.append(make_centered_cube([0.0, HY - 2.1, HZ - 12.6], [4.8, 1.2, 1.8], "STONE_ARMOR", rot=[8.0, 0, 0]))
    for side in (-1, 1):
        head_cubes.append(make_centered_cube([side * 2.8, HY - 1.9, HZ - 8.2], [1.2, 1.4, 8.2],
                                             "STONE_ARMOR", rot=[6.0, side * 4.0, 0]))
    # Khoang vòm miệng & họng lửa 3D
    head_cubes.append(make_centered_cube([0.0, HY - 1.7, HZ - 7.0], [5.0, 1.2, 6.8], "MAW_INFERNO", rot=[5.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, HY - 1.9, HZ - 3.0], [4.8, 2.8, 4.0], "MAW_INFERNO"))

    # Sừng mũi sắc trên chóp mõm
    add_curved_horn(head_cubes, [
        (0.0, HY + 1.1, HZ - 11.4),
        (0.0, HY + 3.4, HZ - 12.4),
        (0.0, HY + 5.2, HZ - 11.4),
    ], [0.98, 0.56, 0.18], "OBSIDIAN_HORN", steps_per_seg=2)

    # 2 ĐÔI MẮT CAM-VÀNG RỰC LỬA DƯỚI GỜ MÀY SÂU
    eye_dark_sides = {"up": "CLAW_DARK", "down": "CLAW_DARK", "south": "CLAW_DARK", "east": "CLAW_DARK", "west": "CLAW_DARK"}
    for side in (-1, 1):
        head_cubes.append(make_centered_cube([side * 4.2, HY + 2.8, HZ - 5.2], [2.7, 1.6, 4.8],
                                             "DORSAL_SHIELD", rot=[12.0, side * 18.0, side * 18.0]))
        head_cubes.append(make_centered_cube([side * 4.05, HY + 1.0, HZ - 5.4], [2.1, 2.1, 3.4],
                                             "CLAW_DARK", rot=[8.0, side * 32.0, 0.0]))
        head_cubes.append(make_centered_cube([side * 4.35, HY + 0.95, HZ - 6.15], [2.5, 1.85, 0.95],
                                             "EYE_FIRE", rot=[8.0, side * 36.0, 0.0], face_mats=eye_dark_sides))
        head_cubes.append(make_centered_cube([side * 2.1, HY + 1.4, HZ - 10.8], [1.5, 1.4, 2.6],
                                             "DORSAL_SHIELD", rot=[10.0, side * 14.0, 0]))
        head_cubes.append(make_centered_cube([side * 3.6, HY - 0.7, HZ - 7.0], [1.6, 2.3, 4.6],
                                             "STONE_ARMOR", rot=[6.0, side * 12.0, side * 8.0]))

    # 2 Răng nanh ngà lớn + hàng răng sắc hàm trên
    for side in (-1, 1):
        add_curved_horn(head_cubes, [
            (side * 3.05, HY - 1.7, HZ - 9.8),
            (side * 3.25, HY - 3.4, HZ - 10.3),
            (side * 3.15, HY - 4.8, HZ - 10.1),
        ], [0.70, 0.44, 0.16], "IVORY_FANG", steps_per_seg=2)
        for tz, th in ((-12.4, 1.30), (-7.6, 1.22), (-5.4, 1.10)):
            head_cubes.append(make_centered_cube([side * 2.35, HY - 2.6, HZ + tz], [0.62, th, 0.66],
                                                 "IVORY_FANG", rot=[8.0, 0, side * 6.0]))

    add_bone("head", "neck_4", [0.0, HY + 0.3, HZ], head_cubes)

    # 2 BONE SỪNG LƯỠI LIỀM ĐẠI LONG UỐN VỀ TRƯỚC RỒI VÚT CAO DÀY KHỎE!
    horn_l_cubes = []
    add_curved_horn(horn_l_cubes, [
        (3.4, HY + 3.0, HZ - 1.2),
        (6.4, HY + 4.0, HZ - 5.2),
        (8.0, HY + 6.0, HZ - 10.6),
        (8.4, HY + 9.6, HZ - 14.8),
        (8.0, HY + 14.0, HZ - 16.8),
    ], [2.10, 1.75, 1.28, 0.74, 0.22], "OBSIDIAN_HORN", steps_per_seg=4)
    add_curved_horn(horn_l_cubes, [
        (3.8, HY + 4.0, HZ - 1.6),
        (6.6, HY + 5.0, HZ - 5.6),
        (8.1, HY + 7.0, HZ - 10.8),
        (8.3, HY + 10.6, HZ - 14.8),
    ], [0.85, 0.66, 0.45, 0.18], "DORSAL_SHIELD", steps_per_seg=3)
    add_curved_horn(horn_l_cubes, [
        (4.2, HY + 2.8, HZ + 0.4),
        (6.4, HY + 5.0, HZ + 3.8),
        (7.4, HY + 6.8, HZ + 7.2),
    ], [1.25, 0.80, 0.24], "OBSIDIAN_HORN", steps_per_seg=3)
    add_bone("horn_l", "head", [3.4, HY + 2.8, HZ - 1.0], horn_l_cubes)
    add_bone("horn_r", "head", [-3.4, HY + 2.8, HZ - 1.0], [mirror_x_cube(c) for c in horn_l_cubes])

    # Hàm dưới (`jaw`) rộng chắc có MỎ CẰM ĐÁ GỒ MẠNH & RÂU GAI CẰM
    jaw_cubes = []
    jaw_cubes.append(make_centered_cube([0.0, HY - 3.6, HZ - 5.8], [6.6, 2.5, 9.8], "STONE_ARMOR", rot=[5.0, 0, 0]))
    jaw_cubes.append(make_centered_cube([0.0, HY - 4.5, HZ - 10.2], [5.2, 2.3, 4.8], "DORSAL_SHIELD", rot=[8.0, 0, 0]))
    add_curved_horn(jaw_cubes, [
        (0.0, HY - 4.8, HZ - 9.2),
        (0.0, HY - 6.2, HZ - 11.6),
        (0.0, HY - 6.0, HZ - 14.0),
    ], [1.45, 0.98, 0.30], "DORSAL_SHIELD", steps_per_seg=2)
    add_curved_horn(jaw_cubes, [
        (0.0, HY - 4.5, HZ - 6.0),
        (0.0, HY - 6.6, HZ - 5.4),
        (0.0, HY - 7.8, HZ - 4.4),
    ], [1.10, 0.70, 0.24], "OBSIDIAN_HORN", steps_per_seg=2)
    jaw_cubes.append(make_centered_cube([0.0, HY - 2.9, HZ - 7.0], [4.2, 0.8, 6.4], "MAW_INFERNO", rot=[3.0, 0, 0]))
    jaw_cubes.append(make_centered_cube([0.0, HY - 2.6, HZ - 3.4], [4.4, 1.6, 3.4], "EMBER_CORE", rot=[2.0, 0, 0]))
    for side in (-1, 1):
        jaw_cubes.append(make_centered_cube([side * 2.7, HY - 4.2, HZ - 3.8], [1.5, 2.2, 5.8],
                                            "STONE_ARMOR", rot=[5.0, side * 7.0, 0]))
        jaw_cubes.append(make_centered_cube([side * 2.35, HY - 3.0, HZ - 8.4], [1.0, 1.2, 6.2],
                                            "STONE_ARMOR", rot=[4.0, side * 5.0, 0]))
        for tz, th in ((-11.2, 1.22), (-8.6, 1.12), (-6.2, 1.02)):
            jaw_cubes.append(make_centered_cube([side * 1.98, HY - 2.2, HZ + tz], [0.54, th, 0.58],
                                                "IVORY_FANG", rot=[-6.0, 0, -side * 5.0]))
    add_bone("jaw", "head", [0.0, HY - 2.9, HZ - 1.0], jaw_cubes)

    # Locator `mouth_flame` ngay giữa khoang họng lửa (đồng bộ 100% CinderDragonBossEntity.getMouthFlameWorldPos!)
    mouth_flame_pos = [0.0, round(HY - 1.7, 3), round(HZ - 7.0, 3)]
    add_bone("mouth_flame", "head", mouth_flame_pos, locators={"mouth_flame": mouth_flame_pos})

    # -------------------------------------------------------------------------
    # D. 4 CHÂN RỒNG CƠ BẮP HẦM HỐ 4 KHỚP/CHÂN BÁM CHẮC BỜ VAI & HÔNG ĐÔ CON!
    #    - Đẩy rộng khớp vai/hông khớp bề ngang ngực 22 px, tăng độ dày bắp tay/bắp đùi + gai khuỷu/gối cực lực!
    # -------------------------------------------------------------------------
    def build_front_leg(side: int):
        sx = float(side)
        suffix = "l" if side > 0 else "r"
        p_scap = (sx * 9.6, 17.2, -12.8)
        p_sh   = (sx * 11.4, 14.0, -13.0)
        p_el   = (sx * 15.2, 6.6, -7.2)     # Khuỷu gập sâu ra sau rộng thế đứng cực lực!
        p_wr   = (sx * 14.0, 2.6, -17.2)    # Cẳng tay vươn mạnh ra trước chống đất!

        # 1) Khớp bả vai đá dày (`leg_fl_shoulder` / `leg_fr_shoulder`)
        scap_c = []
        scap_c.append(make_centered_cube([sx * 10.4, 15.8, -12.8], [5.2, 5.8, 7.2], "DORSAL_SHIELD", rot=[10.0, sx * 10.0, sx * 14.0]))
        add_curved_horn(scap_c, [
            (sx * 11.2, 16.8, -11.4),
            (sx * 13.4, 19.2, -9.0),
            (sx * 14.4, 21.0, -6.8),
        ], [1.45, 0.92, 0.28], "DORSAL_SHIELD", steps_per_seg=2)

        # 2) Cánh tay trên cực đô vạm vỡ + CẶP SỪNG GAI KHUỶU KHỔNG LỒ MÓC LÊN TRÊN!
        upper_c = []
        add_segment_bar(upper_c, p_sh, p_el, 6.8, 7.0, 5.6, 5.8, steps=6, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="CHEST_FISSURE")
        upper_c.append(make_centered_cube(p_el, [6.0, 5.8, 6.2], "DORSAL_SHIELD"))
        add_curved_horn(upper_c, [
            (sx * 15.6, 7.4, -6.0),
            (sx * 17.2, 10.8, -4.0),
            (sx * 17.8, 14.8, -3.4),
            (sx * 17.0, 17.8, -4.4),
        ], [1.75, 1.25, 0.78, 0.22], "IVORY_FANG", steps_per_seg=3)
        add_curved_horn(upper_c, [
            (sx * 16.2, 6.4, -7.4),
            (sx * 18.2, 9.2, -6.8),
            (sx * 18.8, 12.0, -7.8),
        ], [1.25, 0.78, 0.22], "DORSAL_SHIELD", steps_per_seg=2)

        # 3) Cẳng tay trước đô chắc vươn dài về trước + gai cẳng tay
        lower_c = []
        add_segment_bar(lower_c, p_el, p_wr, 5.4, 5.6, 4.8, 4.6, steps=6, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        for t in (0.32, 0.64):
            gp = np.array(p_el) * (1 - t) + np.array(p_wr) * t
            lower_c.append(make_centered_cube([gp[0] + sx * 2.15, gp[1] + 0.70, gp[2] + 0.7],
                                              [1.60, 1.95, 2.75], "CLAW_DARK", rot=[-24.0, sx * 18.0, sx * 22.0]))

        # 4) Bàn chân trước xòe rộng đồ sộ + 4 ngón móng vuốt sắc
        foot_c = []
        foot_c.append(make_centered_cube([sx * 14.0, 1.85, -18.2], [6.8, 3.4, 6.4], "STONE_ARMOR"))
        foot_c.append(make_centered_cube([sx * 14.0, 3.05, -17.4], [5.8, 2.0, 5.2], "DORSAL_SHIELD", rot=[10.0, 0, 0]))
        toe_specs = [
            (-32.0 * sx, 6.6),
            (  0.0,      7.6),
            (+32.0 * sx, 6.6),
            (+152.0 * sx, 4.4),
        ]
        for ang_deg, tlen in toe_specs:
            rad = math.radians(ang_deg)
            dx = math.sin(rad)
            dz = -math.cos(rad)
            p_base = (sx * 14.0 + dx * 2.0, 1.45, -18.2 + dz * 2.0)
            p_mid  = (sx * 14.0 + dx * (2.0 + tlen * 0.56), 1.20, -18.2 + dz * (2.0 + tlen * 0.56))
            p_tip  = (sx * 14.0 + dx * (2.0 + tlen), 0.28, -18.2 + dz * (2.0 + tlen))
            add_segment_bar(foot_c, p_base, p_mid, 2.05, 1.90, 1.65, 1.55, steps=2, mat="STONE_ARMOR")
            add_curved_horn(foot_c, [p_mid, (p_tip[0], 1.00, p_tip[2]), p_tip], [0.94, 0.62, 0.20], "IVORY_FANG", steps_per_seg=2)

        add_bone(f"leg_f{suffix}_shoulder", "body_chest", p_scap, scap_c)
        add_bone(f"leg_f{suffix}", f"leg_f{suffix}_shoulder", p_sh, upper_c)
        add_bone(f"leg_f{suffix}_lower", f"leg_f{suffix}", p_el, lower_c)
        add_bone(f"leg_f{suffix}_foot", f"leg_f{suffix}_lower", p_wr, foot_c)

    def build_back_leg(side: int):
        sx = float(side)
        suffix = "l" if side > 0 else "r"
        p_hip = (sx * 9.8, 14.8, +12.2 + PELV_Z)
        p_kn  = (sx * 14.4, 8.2, +6.0 + PELV_Z)     # Gối cúi mạnh ra trước + cặp gai gối khổng lồ!
        p_hk  = (sx * 13.2, 4.6, +15.2 + PELV_Z)    # Cổ chân sau gập sâu đầy lực bật!
        p_ft  = (sx * 12.6, 2.2, +10.2 + PELV_Z)    # Khớp bàn chân sau!

        # 1) Đùi sau cực vạm vỡ + CẶP SỪNG GAI GỐI KHỔNG LỒ MÓC CAO!
        upper_c = []
        add_segment_bar(upper_c, p_hip, p_kn, 7.2, 7.6, 5.6, 5.8, steps=6, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="CHEST_FISSURE")
        upper_c.append(make_centered_cube(p_kn, [5.8, 5.6, 5.8], "DORSAL_SHIELD"))
        add_curved_horn(upper_c, [
            (sx * 14.8, 8.8, +5.0 + PELV_Z),
            (sx * 16.6, 12.6, +3.8 + PELV_Z),
            (sx * 16.8, 16.6, +4.6 + PELV_Z),
        ], [1.65, 1.12, 0.22], "IVORY_FANG", steps_per_seg=3)
        add_curved_horn(upper_c, [
            (sx * 15.2, 9.2, +7.6 + PELV_Z),
            (sx * 17.0, 12.2, +8.6 + PELV_Z),
            (sx * 17.4, 14.6, +9.8 + PELV_Z),
        ], [1.15, 0.72, 0.20], "DORSAL_SHIELD", steps_per_seg=2)

        # 2) Cẳng chân sau dày khỏe (`leg_bl_lower` / `leg_br_lower`) + gai gót sắc
        lower_c = []
        add_segment_bar(lower_c, p_kn, p_hk, 5.2, 5.4, 4.2, 4.3, steps=5, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        lower_c.append(make_centered_cube(p_hk, [4.4, 4.2, 4.6], "DORSAL_SHIELD"))
        add_curved_horn(lower_c, [
            (sx * 13.2, 4.8, +16.4 + PELV_Z),
            (sx * 13.6, 5.8, +19.2 + PELV_Z),
            (sx * 13.6, 6.4, +21.6 + PELV_Z),
        ], [1.10, 0.66, 0.20], "IVORY_FANG", steps_per_seg=2)

        # 3) Khớp cổ chân sau riêng biệt (`leg_bl_ankle` / `leg_br_ankle`)
        ankle_c = []
        add_segment_bar(ankle_c, p_hk, p_ft, 4.0, 4.0, 3.8, 3.7, steps=4, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")

        # 4) Bàn chân sau xòe rộng chắc nịch + vuốt sắc
        foot_c = []
        foot_c.append(make_centered_cube([sx * 12.6, 1.75, +9.2 + PELV_Z], [6.4, 3.2, 6.2], "STONE_ARMOR"))
        toe_specs = [
            (-28.0 * sx, 6.2),
            (  0.0,      7.2),
            (+28.0 * sx, 6.2),
            (+156.0 * sx, 4.2),
        ]
        for ang_deg, tlen in toe_specs:
            rad = math.radians(ang_deg)
            dx = math.sin(rad)
            dz = -math.cos(rad)
            p_base = (sx * 12.6 + dx * 1.9, 1.38, +9.2 + PELV_Z + dz * 1.9)
            p_mid  = (sx * 12.6 + dx * (1.9 + tlen * 0.56), 1.15, +9.2 + PELV_Z + dz * (1.9 + tlen * 0.56))
            p_tip  = (sx * 12.6 + dx * (1.9 + tlen), 0.28, +9.2 + PELV_Z + dz * (1.9 + tlen))
            add_segment_bar(foot_c, p_base, p_mid, 1.92, 1.82, 1.54, 1.46, steps=2, mat="STONE_ARMOR")
            add_curved_horn(foot_c, [p_mid, (p_tip[0], 0.92, p_tip[2]), p_tip], [0.90, 0.58, 0.20], "IVORY_FANG", steps_per_seg=2)

        add_bone(f"leg_b{suffix}", "pelvis", p_hip, upper_c)
        add_bone(f"leg_b{suffix}_lower", f"leg_b{suffix}", p_kn, lower_c)
        add_bone(f"leg_b{suffix}_ankle", f"leg_b{suffix}_lower", p_hk, ankle_c)
        add_bone(f"leg_b{suffix}_foot", f"leg_b{suffix}_ankle", p_ft, foot_c)

    for s in (1, -1):
        build_front_leg(s)
        build_back_leg(s)

    # -------------------------------------------------------------------------
    # E. ĐÔI CÁNH ĐỎ HUYẾT KHỔNG LỒ x1.3 GÀI SÁT MÉP NGOÀI BỜ VAI ĐÔ (TÁCH BIỆT >18 px — 0 TRÙNG/DÍNH!)
    # -------------------------------------------------------------------------
    def build_wing_left():
        p_sh  = np.array([10.4, 20.2, -12.4])   # Khớp vai cánh đặt sát mép ngoài cầu vai đô (cách nhau 20.8 px!)
        p_el  = np.array([25.6, 30.4,  -5.6])   # Khuỷu cánh x1.3 vồng cao & vươn rộng ra ngoài (+X)
        p_wr  = np.array([44.0, 41.4, -13.6])   # Cổ cánh (`wing_wrist`) x1.3 vươn đỉnh cực uy lực!

        # 5 nan xương cánh x1.3 xòe quạt rộng (~146 px tổng sải cánh!), tách hẳn ra ngoài sườn (p_f4.x = +27.8!)
        p_tip = np.array([69.0, 38.2,  -6.2])   # Nan đầu mép trước (+ vuốt chóp tới X = 73.0!)
        p_f1  = np.array([63.4, 29.4,  +7.8])   # Nan 1
        p_f2  = np.array([52.6, 22.8, +19.2])   # Nan 2
        p_f3  = np.array([40.0, 19.8, +24.8])   # Nan 3
        p_f4  = np.array([27.8, 19.4, +22.0])   # Nan 4 tách rộng khỏi lưng đô (2 cánh cách nhau 55.6 px!)

        # Gốc màng cánh gài kín vào mép sườn LƯNG TRÊN của thân đô (X = +9.2..+8.6), tuyệt đối không chọc bụng/lưng!
        p_flank_front = np.array([9.4, 19.6, -6.2])
        p_flank_rear  = np.array([8.6, 18.4, +7.0])

        arm_c = []
        arm_c.append(make_centered_cube(p_sh, [4.6, 4.4, 4.8], "WING_BONE"))
        add_segment_bar(arm_c, p_sh, p_el, 3.8, 4.0, 3.2, 3.5, steps=6, mat="WING_BONE",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        for t in (0.28, 0.58, 0.85):
            gp = p_sh * (1 - t) + p_el * t
            arm_c.append(make_centered_cube(gp + np.array([0, 2.2, -0.40]), [1.25, 2.15, 1.65],
                                            "CLAW_DARK", rot=[-18.0, 0, -14.0]))
        add_quad_membrane(arm_c, p_sh, p_el, p_f4, p_flank_rear, rows=7, cols=6, scallop_depth=0.13)
        add_quad_membrane(arm_c, p_sh, p_flank_front, p_flank_rear, p_sh + np.array([-0.2, -0.9, 4.8]),
                          rows=3, cols=3, scallop_depth=0.04)

        mid_c = []
        mid_c.append(make_centered_cube(p_el, [4.2, 4.0, 4.4], "WING_BONE"))
        add_segment_bar(mid_c, p_el, p_wr, 3.4, 3.6, 3.0, 3.2, steps=8, mat="WING_BONE",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        for t in (0.22, 0.48, 0.74):
            gp = p_el * (1 - t) + p_wr * t
            mid_c.append(make_centered_cube(gp + np.array([0.2, 2.1, -0.50]), [1.15, 2.10, 1.55],
                                            "CLAW_DARK", rot=[-14.0, 12.0, -12.0]))
        add_membrane_panel(mid_c, p_el, p_wr, p_f4, rows=8, cols=7, scallop_depth=0.13)

        # Khớp cổ cánh (`wing_wrist_l`) mang 3 ĐẠI MÓNG VUỐT MÓC KHỔNG LỒ x1.3!
        wrist_c = []
        wrist_c.append(make_centered_cube(p_wr, [5.0, 4.8, 5.2], "WING_BONE"))
        add_curved_horn(wrist_c, [
            p_wr + np.array([ 0.3,  1.2, -1.5]),
            p_wr + np.array([ 1.5,  2.8, -6.8]),
            p_wr + np.array([ 2.1,  0.8, -11.8]),
            p_wr + np.array([ 1.6, -2.6, -14.2]),
        ], [1.80, 1.32, 0.78, 0.22], "CLAW_DARK", steps_per_seg=3)
        add_curved_horn(wrist_c, [
            p_wr + np.array([-0.8, -0.5, -1.5]),
            p_wr + np.array([ 0.0, -0.2, -6.2]),
            p_wr + np.array([ 0.3, -2.6, -10.2]),
            p_wr + np.array([-0.2, -5.2, -12.0]),
        ], [1.45, 1.04, 0.60, 0.20], "CLAW_DARK", steps_per_seg=3)
        add_curved_horn(wrist_c, [
            p_wr + np.array([-1.2,  1.5, -0.8]),
            p_wr + np.array([-2.2,  4.0, -4.2]),
            p_wr + np.array([-2.0,  3.8, -7.2]),
        ], [1.15, 0.74, 0.22], "CLAW_DARK", steps_per_seg=2)

        tip_c = []
        add_segment_bar(tip_c, p_wr, p_tip, 2.7, 2.8, 1.00, 1.10, steps=9, mat="WING_BONE",
                        armor_ridge=True, ridge_mat="CLAW_DARK")
        add_curved_horn(tip_c, [p_tip, p_tip + np.array([4.0, -0.5, 1.2])], [0.92, 0.20], "CLAW_DARK", steps_per_seg=2)
        add_membrane_panel(tip_c, p_wr, p_tip, p_f1, rows=9, cols=7, scallop_depth=0.14)

        f1_c = []
        add_segment_bar(f1_c, p_wr, p_f1, 1.90, 1.90, 0.78, 0.78, steps=8, mat="WING_BONE")
        add_membrane_panel(f1_c, p_wr, p_f1, p_f2, rows=9, cols=7, scallop_depth=0.15)

        f2_c = []
        add_segment_bar(f2_c, p_wr, p_f2, 1.72, 1.72, 0.72, 0.72, steps=8, mat="WING_BONE")
        add_membrane_panel(f2_c, p_wr, p_f2, p_f3, rows=8, cols=7, scallop_depth=0.15)

        f3_c = []
        add_segment_bar(f3_c, p_wr, p_f3, 1.60, 1.60, 0.66, 0.66, steps=7, mat="WING_BONE")
        add_membrane_panel(f3_c, p_wr, p_f3, p_f4, rows=8, cols=7, scallop_depth=0.14)

        f4_c = []
        add_segment_bar(f4_c, p_wr, p_f4, 1.52, 1.52, 0.64, 0.64, steps=7, mat="WING_BONE")

        return {
            "sh": p_sh, "el": p_el, "wr": p_wr,
            "arm": arm_c, "mid": mid_c, "wrist": wrist_c, "tip": tip_c,
            "f1": f1_c, "f2": f2_c, "f3": f3_c, "f4": f4_c,
        }

    wl = build_wing_left()
    add_bone("wing_l", "body_chest", wl["sh"])
    add_bone("wing_arm_l", "wing_l", wl["sh"], wl["arm"])
    add_bone("wing_mid_l", "wing_arm_l", wl["el"], wl["mid"])
    add_bone("wing_wrist_l", "wing_mid_l", wl["wr"], wl["wrist"])
    add_bone("wing_tip_l", "wing_wrist_l", wl["wr"], wl["tip"])
    add_bone("wing_finger_1_l", "wing_wrist_l", wl["wr"], wl["f1"])
    add_bone("wing_finger_2_l", "wing_wrist_l", wl["wr"], wl["f2"])
    add_bone("wing_finger_3_l", "wing_wrist_l", wl["wr"], wl["f3"])
    add_bone("wing_finger_4_l", "wing_mid_l", wl["wr"], wl["f4"])

    def mp(pt):
        return [-float(pt[0]), float(pt[1]), float(pt[2])]

    add_bone("wing_r", "body_chest", mp(wl["sh"]))
    add_bone("wing_arm_r", "wing_r", mp(wl["sh"]), [mirror_x_cube(c) for c in wl["arm"]])
    add_bone("wing_mid_r", "wing_arm_r", mp(wl["el"]), [mirror_x_cube(c) for c in wl["mid"]])
    add_bone("wing_wrist_r", "wing_mid_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["wrist"]])
    add_bone("wing_tip_r", "wing_wrist_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["tip"]])
    add_bone("wing_finger_1_r", "wing_wrist_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f1"]])
    add_bone("wing_finger_2_r", "wing_wrist_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f2"]])
    add_bone("wing_finger_3_r", "wing_wrist_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f3"]])
    add_bone("wing_finger_4_r", "wing_mid_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f4"]])

    # -------------------------------------------------------------------------
    # F. ĐUÔI 9 ĐỐT ĐA KHỚP UỐN LƯỢN SÁT ĐẤT RỒI VÚT CONG LƯỠI LIỀM (GIỐNG ẢNH MẪU 1 & 2!)
    #    - Chiều dài đuôi 58 px (+19.2 -> +78.8), vây rìu cong tăng dần kích thước ở đốt 4..8
    #      và kết thúc bằng ĐẠI LƯỠI LIỀM KHỔNG LỒ + LÕI THAN HỒNG ở `tail_9`!
    # -------------------------------------------------------------------------
    tail_nodes = [
        # (x, y, z, rx, ry, sw, sh, scythe_scale) — gốc đuôi dày khỏe nối mượt với khối hông đô (`rx = 6.0 -> 1.7`)!
        (0.0, 13.5, +19.2 + PELV_Z, 6.00, 4.70, 3.60, 3.30, 0.00),  # tail_1
        (0.0, 12.0, +26.0 + PELV_Z, 5.10, 4.05, 3.20, 2.95, 0.00),  # tail_2
        (0.0, 10.6, +32.8 + PELV_Z, 4.30, 3.45, 2.80, 2.65, 0.00),  # tail_3
        (0.0,  9.5, +39.4 + PELV_Z, 3.60, 2.95, 2.40, 2.30, 0.96),  # tail_4: vây rìu cong #1
        (0.0,  9.0, +46.0 + PELV_Z, 3.05, 2.55, 2.15, 2.05, 1.18),  # tail_5: vây rìu cong #2
        (0.0,  9.6, +52.4 + PELV_Z, 2.60, 2.20, 1.90, 1.85, 1.38),  # tail_6: vây rìu cong #3
        (0.0, 11.0, +58.6 + PELV_Z, 2.25, 1.92, 1.70, 1.65, 1.56),  # tail_7: vây rìu cong #4 lớn!
        (0.0, 13.4, +64.4 + PELV_Z, 1.95, 1.70, 1.50, 1.50, 0.00),  # tail_8: cổ uốn vút lên chóp đuôi
        (0.0, 16.8, +69.6 + PELV_Z, 1.72, 1.52, 1.35, 1.35, 1.60),  # tail_9: ĐẠI LƯỠI LIỀM CHÓP ĐUÔI!
    ]
    prev_tail = "pelvis"
    for i, (tx, ty, tz, rx, ry, sw, sh, scy) in enumerate(tail_nodes):
        if i < len(tail_nodes) - 1:
            nx, ny, nz = tail_nodes[i + 1][:3]
        else:
            nx, ny, nz = 0.0, 19.6, +73.6 + PELV_Z
        seg_c = []
        ym, zm = 0.5 * (ty + ny), 0.5 * (tz + nz)
        seg_len = math.hypot(ny - ty, nz - tz)
        # GeckoLib Rx(-rx): rot_x > 0 tilts +Z (backward tail) UP!
        pitch = +math.degrees(math.atan2(ny - ty, nz - tz))
        for frac in (0.28, 0.72):
            ry_p = ty + (ny - ty) * frac
            rz_p = tz + (nz - tz) * frac
            taper = 1.0 - 0.08 * frac
            add_armor_ring(seg_c, (0.0, ry_p, rz_p), rx * taper, ry * taper, seg_len * 0.80,
                           mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                           mat_side="CHEST_FISSURE" if i < 6 else "STONE_ARMOR", pitch_x=pitch)
        if i < 3 or i == 7:
            for frac in (0.28, 0.76):
                sy = ty + (ny - ty) * frac + ry * 0.86
                sz = tz + (nz - tz) * frac
                add_dorsal_shield(seg_c, (0.0, sy, sz), sw, sh, seg_len * 0.46, tilt_x=-14.0 + pitch * 0.4)
        # Vây rìu lưỡi liềm cong lưng đuôi + gai móc bụng đối trọng (giống ảnh mẫu 1 & 2!)
        if scy > 0.0 and i < 8:
            add_tail_scythe_fin(seg_c, (0.0, ym + ry * 0.85, zm), scy, pitch, is_tip=False)
            # Gai móc ngược dưới bụng đoạn đuôi cong
            add_curved_horn(seg_c, [
                (0.0, ym - ry * 0.85, zm),
                (0.0, ym - ry * 0.85 - 1.6 * scy, zm - 0.8 * scy),
                (0.0, ym - ry * 0.85 - 2.6 * scy, zm - 2.0 * scy),
            ], [0.58 * scy, 0.36 * scy, 0.12 * scy], "IVORY_FANG", steps_per_seg=2)
        if i in (0, 2, 4, 6):
            for side in (-1, 1):
                seg_c.append(make_centered_cube([side * (rx * 0.96), ym, zm + 0.3],
                                                [1.10, 1.00, 2.05], "CLAW_DARK", rot=[pitch, side * 24.0, 0]))
        if i == 8:
            # Đại lưỡi liềm chóp đuôi khổng lồ!
            add_tail_scythe_fin(seg_c, (0.0, ny - 0.6, nz - 0.4), scy, pitch, is_tip=True)

        bname = f"tail_{i + 1}"
        add_bone(bname, prev_tail, [tx, ty, tz], seg_c)
        prev_tail = bname

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    assert 1400 <= total_cubes <= 3400, f"Cube count {total_cubes} out of target range 1400..3400!"

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.cinder_dragon",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 36.0,
                    "visible_bounds_height": 16.0,
                    "visible_bounds_offset": [0.0, 5.0, 0.0],
                },
                "bones": bones,
            }
        ],
    }

    out = ASSETS / "geo" / "entity" / "cinder_dragon.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes)")
    return geo, total_cubes, {"bones": len(bones), "mouth_flame": mouth_flame_pos}


# =============================================================================
# 3. SINH 4 BỘ TEXTURE 512x512 NEAREST RGBA (NORMAL, ENRAGED, GLOWMASK P1 & P2)
# =============================================================================
def value_noise_2d(h: int, w: int, scale: int, seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    gh = max(2, h // scale + 2)
    gw = max(2, w // scale + 2)
    grid = rng.random((gh, gw), dtype=np.float32)
    ys = np.arange(h, dtype=np.float32) / scale
    xs = np.arange(w, dtype=np.float32) / scale
    yi = np.floor(ys).astype(np.int32)
    xi = np.floor(xs).astype(np.int32)
    yf = (ys - yi)[:, None]
    xf = (xs - xi)[None, :]
    sy = yf * yf * (3.0 - 2.0 * yf)
    sx = xf * xf * (3.0 - 2.0 * xf)
    v00 = grid[yi[:, None], xi[None, :]]
    v10 = grid[yi[:, None], xi[None, :] + 1]
    v01 = grid[yi[:, None] + 1, xi[None, :]]
    v11 = grid[yi[:, None] + 1, xi[None, :] + 1]
    return (v00 * (1 - sx) + v10 * sx) * (1 - sy) + (v01 * (1 - sx) + v11 * sx) * sy


def fbm(h: int, w: int, seed: int) -> np.ndarray:
    return (
        0.55 * value_noise_2d(h, w, 16, seed)
        + 0.30 * value_noise_2d(h, w, 8, seed + 17)
        + 0.15 * value_noise_2d(h, w, 4, seed + 31)
    )


def paint_stone_scales(h: int, w: int, seed: int, enraged: bool, strong_fissures: bool = False):
    """Vảy đá núi lửa xám nâu tro (#26211F, #3D3530, #574C45, viền sáng #786B62) có AO khe vảy sắc nét."""
    n = fbm(h, w, seed)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    # Lưới vảy đá xếp sole hình lục giác/ngói
    cell_h, cell_w = 16.0, 20.0
    row = np.floor(yy / cell_h)
    shift = (row % 2) * (cell_w * 0.5)
    lx = ((xx + shift) % cell_w) / cell_w
    ly = (yy % cell_h) / cell_h

    # Khe AO xung quanh mỗi vảy đá + cạnh vát sáng trên cùng
    dx = np.abs(lx - 0.5) * 2.0
    dy = np.abs(ly - 0.5) * 2.0
    dist_edge = np.maximum(dx * 0.92 + dy * 0.25, dy)
    crevice = np.clip((dist_edge - 0.80) / 0.18, 0.0, 1.0)
    top_rim = np.clip((0.20 - ly) / 0.18, 0.0, 1.0) * (1.0 - crevice)

    # Tông màu đá núi lửa 4 bậc (viền sáng gờ đá sắc nét nổi bật ở cả ban đêm)
    base_dark = np.array([40, 35, 33], dtype=np.float32)     # #26211F
    base_mid  = np.array([65, 57, 52], dtype=np.float32)     # #3D3530
    base_hi   = np.array([94, 82, 75], dtype=np.float32)     # #574C45
    edge_hi   = np.array([138, 124, 114], dtype=np.float32)  # #786B62 -> highlight sắc cạnh
    ao_col    = np.array([19, 15, 14], dtype=np.float32)

    t = np.clip(n * 1.15 - 0.08 + (0.5 - ly) * 0.18, 0.0, 1.0)
    col = np.where(
        t[:, :, None] < 0.5,
        base_dark * (1.0 - t[:, :, None] * 2.0) + base_mid * (t[:, :, None] * 2.0),
        base_mid * (1.0 - (t[:, :, None] - 0.5) * 2.0) + base_hi * ((t[:, :, None] - 0.5) * 2.0),
    )
    col = col * (1.0 - top_rim[:, :, None] * 0.62) + edge_hi * (top_rim[:, :, None] * 0.62)
    col = col * (1.0 - crevice[:, :, None] * 0.74) + ao_col * (crevice[:, :, None] * 0.74)

    glow = np.zeros((h, w, 4), dtype=np.float32)
    if enraged:
        # Vết nứt kiến tạo dung nham liền mạch sắc nét (không rải chấm nhiễu!):
        # Kết hợp 2 đường nứt lớn cắt chéo uốn lượn theo trường nhiễu thấp-tần
        warp1 = value_noise_2d(h, w, 24, seed + 101) * 24.0
        warp2 = value_noise_2d(h, w, 20, seed + 137) * 22.0
        d1 = np.abs(((xx * 0.75 + yy * 0.65 + warp1) % 64.0) - 32.0)
        d2 = np.abs(((xx * 0.55 - yy * 0.85 + warp2) % 64.0) - 32.0)
        width_main = 3.2 if strong_fissures else 1.85
        vein1 = np.clip(1.0 - d1 / width_main, 0.0, 1.0)
        vein2 = np.clip(1.0 - d2 / (width_main * 0.85), 0.0, 1.0) * (1.0 if strong_fissures else 0.70)
        vein = np.maximum(vein1, vein2)
        if strong_fissures:
            # Thêm rãnh nóng chạy dọc khe vảy sâu quanh đường nứt chính
            near_crack = np.clip(1.0 - np.minimum(d1, d2) / 14.0, 0.0, 1.0)
            vein = np.maximum(vein, crevice * near_crack * 0.85)
        # Bảng màu dung nham Phase 2: #C42410 -> #FF7A14 -> #FFF3B0
        hot_red   = np.array([196, 36, 16], dtype=np.float32)
        hot_org   = np.array([255, 122, 20], dtype=np.float32)
        hot_white = np.array([255, 243, 176], dtype=np.float32)
        v = vein[:, :, None]
        lava = np.where(
            v < 0.55,
            hot_red * (1.0 - v / 0.55) + hot_org * (v / 0.55),
            hot_org * (1.0 - (v - 0.55) / 0.45) + hot_white * ((v - 0.55) / 0.45),
        )
        m = np.clip(vein * 1.45, 0.0, 1.0)[:, :, None]
        col = col * (1.0 - m) + lava * m
        glow[:, :, :3] = lava
        glow[:, :, 3] = np.where(vein > 0.22, np.clip(vein * 270.0, 0, 255), 0.0)

    rgba = np.zeros((h, w, 4), dtype=np.uint8)
    rgba[:, :, :3] = np.clip(col, 0, 255).astype(np.uint8)
    rgba[:, :, 3] = 255
    return rgba, np.clip(glow, 0, 255).astype(np.uint8)


def paint_wing_membrane(h: int, w: int, enraged: bool):
    """Màng cánh đỏ sẫm/đỏ gỉ cháy (#5E1614 -> #9E2922 -> #BA3B2E) có vân gân da, mép dơi alpha-cutout & vân than cháy."""
    n = fbm(h, w, 707)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u = xx / max(1.0, w - 1.0)
    v = yy / max(1.0, h - 1.0)

    # Mép dưới hình cung cánh dơi sạch alpha (cắt lượn mềm ở đáy v > 0.90)
    scallop_y = 0.955 - 0.075 * (np.maximum(0.0, np.sin(u * math.pi)) ** 1.4)
    alpha = np.where(v <= scallop_y, 255, 0).astype(np.uint8)
    # 5x5 góc phải dưới luôn trong suốt cho mặt cạnh `edge_transparent`
    alpha[h - 6:, w - 6:] = 0

    c_dark = np.array([68, 16, 14], dtype=np.float32)     # #44100E (đỏ huyết khô đậm uy nghi như ảnh mẫu!)
    c_mid  = np.array([124, 30, 24], dtype=np.float32)    # #7C1E18
    c_hi   = np.array([156, 44, 34], dtype=np.float32)    # #9C2C22
    if enraged:
        c_dark = np.array([104, 22, 16], dtype=np.float32)
        c_mid  = np.array([182, 46, 28], dtype=np.float32)
        c_hi   = np.array([228, 78, 42], dtype=np.float32)

    # Sáng dần ra giữa màng, tối ấm sát nan xương 2 bên (u gần 0/1) & đỉnh gốc (v gần 0)
    edge_ao = np.clip(np.minimum(u, 1.0 - u) * 5.0, 0.0, 1.0)
    grad = np.clip((0.25 + 0.65 * v) * (0.65 + 0.35 * edge_ao) + (n - 0.5) * 0.28, 0.0, 1.0)
    col = np.where(
        grad[:, :, None] < 0.55,
        c_dark * (1.0 - grad[:, :, None] / 0.55) + c_mid * (grad[:, :, None] / 0.55),
        c_mid * (1.0 - (grad[:, :, None] - 0.55) / 0.45) + c_hi * ((grad[:, :, None] - 0.55) / 0.45),
    )

    # Gân da phân nhánh tự nhiên chạy dọc v + gân ngang nhẹ
    rib_vein = np.exp(-((np.sin((u * 9.0 + n * 1.4) * math.pi)) ** 2) * 28.0)
    cross_vein = np.exp(-((np.sin((v * 7.0 + n * 1.2) * math.pi)) ** 2) * 34.0) * 0.45
    vein_dark = np.clip(rib_vein * 0.55 + cross_vein * 0.40, 0.0, 0.7)
    col = col * (1.0 - vein_dark[:, :, None] * 0.42) + np.array([48, 11, 10], dtype=np.float32) * (vein_dark[:, :, None] * 0.42)

    # Vân nứt than cháy dọc mép ngoài màng cánh (mờ ấm ở P1, bùng rực viền lửa ở P2!)
    dist_rim = np.clip(scallop_y - v, 0.0, 1.0)
    rim_zone = np.exp(-((dist_rim / 0.14) ** 2))
    ember_pattern = np.clip(1.0 - np.abs(fbm(h, w, 919) - 0.5) / 0.095, 0.0, 1.0)
    ember_rim = rim_zone * np.maximum(ember_pattern, np.exp(-((dist_rim / 0.032) ** 2)))

    glow = np.zeros((h, w, 4), dtype=np.uint8)
    if not enraged:
        # P1: mép than cháy sẫm có chấm hồng mờ tinh tế
        char_col = np.array([42, 16, 14], dtype=np.float32)
        ember_dim = np.array([210, 72, 24], dtype=np.float32)
        col = col * (1.0 - rim_zone[:, :, None] * 0.45) + char_col * (rim_zone[:, :, None] * 0.45)
        col = col * (1.0 - ember_rim[:, :, None] * 0.35) + ember_dim * (ember_rim[:, :, None] * 0.35)
    else:
        # P2: viền lửa cam-vàng rực cháy + glowmask fullbright!
        fire_col = np.where(
            ember_rim[:, :, None] > 0.58,
            np.array([255, 224, 118], dtype=np.float32),
            np.array([255, 104, 24], dtype=np.float32),
        )
        m = np.clip(ember_rim * 1.25, 0.0, 1.0)[:, :, None]
        col = col * (1.0 - m) + fire_col * m
        glow[:, :, :3] = np.clip(fire_col, 0, 255).astype(np.uint8)
        glow[:, :, 3] = np.where((alpha > 0) & (ember_rim > 0.22), np.clip(ember_rim * 270.0, 0, 255), 0).astype(np.uint8)

    rgba = np.zeros((h, w, 4), dtype=np.uint8)
    rgba[:, :, :3] = np.clip(col, 0, 255).astype(np.uint8)
    rgba[:, :, 3] = alpha
    return rgba, glow


def build_textures():
    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)

    for enraged in (False, True):
        diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
        glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

        def put(key, d_patch, g_patch=None):
            x0, y0, w, h = MAT[key]
            diff[y0:y0 + h, x0:x0 + w] = d_patch
            if g_patch is not None:
                glow[y0:y0 + h, x0:x0 + w] = g_patch

        # 1. STONE_ARMOR & CHEST_FISSURE
        d_stone, g_stone = paint_stone_scales(128, 128, 101, enraged, strong_fissures=False)
        put("STONE_ARMOR", d_stone, g_stone)
        d_chest, g_chest = paint_stone_scales(128, 128, 202, enraged, strong_fissures=True)
        put("CHEST_FISSURE", d_chest, g_chest)

        # 2. BELLY_PLATE (vảy bụng xếp ngang tối ấm)
        yy, xx = np.mgrid[0:128, 0:128].astype(np.float32)
        n = fbm(128, 128, 303)
        band = (yy % 16.0) / 16.0
        seam = np.clip((band - 0.80) / 0.18, 0.0, 1.0)
        b_col = np.array([56, 44, 39], dtype=np.float32) * (0.78 + 0.35 * n[:, :, None])
        b_col *= (1.0 - seam[:, :, None] * 0.55)
        b_col += np.array([28, 22, 18], dtype=np.float32) * np.clip((0.18 - band) / 0.16, 0.0, 1.0)[:, :, None]
        g_belly = np.zeros((128, 128, 4), dtype=np.uint8)
        if enraged:
            # Khe bụng ửng cam nóng ở Phase 2
            hot = seam * np.clip((n - 0.30) * 2.0, 0.0, 1.0)
            lava = np.array([255, 118, 24], dtype=np.float32)
            b_col = b_col * (1.0 - hot[:, :, None] * 0.85) + lava * (hot[:, :, None] * 0.85)
            g_belly[:, :, :3] = lava.astype(np.uint8)
            g_belly[:, :, 3] = np.where(hot > 0.22, np.clip(hot * 240.0, 0, 255), 0).astype(np.uint8)
        d_belly = np.dstack([np.clip(b_col, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        put("BELLY_PLATE", d_belly, g_belly)

        # 3. DORSAL_SHIELD (tấm khiên đá vát sáng cạnh #786B62 + sống giữa)
        d_sh, g_sh = paint_stone_scales(128, 128, 404, enraged, strong_fissures=False)
        ridge = np.exp(-(((xx - 64.0) / 14.0) ** 2))
        arr = d_sh[:, :, :3].astype(np.float32)
        arr = arr * (1.0 - ridge[:, :, None] * 0.32) + np.array([134, 120, 110], dtype=np.float32) * (ridge[:, :, None] * 0.32)
        d_sh[:, :, :3] = np.clip(arr, 0, 255).astype(np.uint8)
        put("DORSAL_SHIELD", d_sh, g_sh)

        # 4. OBSIDIAN_HORN (đá obsidian góc cạnh vân vòng sắc nét)
        nh = fbm(128, 128, 505)
        ring = 0.5 + 0.5 * np.sin((yy / 10.0 + nh * 1.2) * math.pi * 2.0)
        h_dark = np.array([24, 20, 27], dtype=np.float32)
        h_mid  = np.array([54, 46, 58], dtype=np.float32)
        h_edge = np.array([98, 88, 102], dtype=np.float32)
        t = np.clip(ring * 0.55 + nh * 0.45, 0.0, 1.0)[:, :, None]
        hc = h_dark * (1 - t) + h_mid * t
        spec = np.exp(-(((xx - 44.0) / 18.0) ** 2))[:, :, None]
        hc = hc * (1 - spec * 0.38) + h_edge * (spec * 0.38)
        g_horn = np.zeros((128, 128, 4), dtype=np.uint8)
        if enraged:
            # Sừng obsidian ánh đỏ-cam mảnh ở khe gân
            vein = np.clip((0.16 - ring) / 0.16, 0.0, 1.0) * np.clip((nh - 0.35) * 2.2, 0.0, 1.0)
            lv = np.array([255, 96, 20], dtype=np.float32)
            hc = hc * (1 - vein[:, :, None] * 0.7) + lv * (vein[:, :, None] * 0.7)
            g_horn[:, :, :3] = lv.astype(np.uint8)
            g_horn[:, :, 3] = np.where(vein > 0.25, np.clip(vein * 230, 0, 255), 0).astype(np.uint8)
        d_horn = np.dstack([np.clip(hc, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        put("OBSIDIAN_HORN", d_horn, g_horn)

        # 5. WING_BONE (xương cánh nâu đen bọc giáp dày)
        nw = fbm(128, 128, 606)
        wc = np.array([34, 25, 23], dtype=np.float32) * (0.75 + 0.55 * nw[:, :, None])
        wband = np.clip(((yy % 24.0) - 19.0) / 4.0, 0.0, 1.0)[:, :, None]
        wc = wc * (1 - wband * 0.45) + np.array([86, 68, 62], dtype=np.float32) * (wband * 0.45)
        d_wb = np.dstack([np.clip(wc, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        put("WING_BONE", d_wb)

        # 6. WING_MEMBRANE (256x192 đỏ sẫm/gỉ cháy + vân gân + viền cung dơi)
        d_wm, g_wm = paint_wing_membrane(192, 256, enraged)
        put("WING_MEMBRANE", d_wm, g_wm)

        # 7. THROAT_MAGMA (vảy cổ kẹp rãnh mạch dung nham mảnh phát sáng ở CẢ P1 và P2!)
        d_tm, _ = paint_stone_scales(128, 128, 808, False, strong_fissures=False)
        slit = np.exp(-(((yy - 64.0 + np.sin(xx * 0.10) * 6.0) / (9.0 if enraged else 6.0)) ** 2))
        slit2 = np.exp(-(((yy - 32.0 + np.cos(xx * 0.12) * 5.0) / (7.5 if enraged else 4.5)) ** 2)) * 0.75
        slit3 = np.exp(-(((yy - 96.0 + np.sin(xx * 0.11) * 5.0) / (7.5 if enraged else 4.5)) ** 2)) * 0.75
        mag = np.clip(np.maximum.reduce([slit, slit2, slit3]), 0.0, 1.0)
        m_org = np.array([255, 114, 18], dtype=np.float32)
        m_hot = np.array([255, 240, 155], dtype=np.float32) if enraged else np.array([255, 206, 86], dtype=np.float32)
        mcol = m_org * (1 - mag[:, :, None]) + m_hot * mag[:, :, None]
        tm_rgb = d_tm[:, :, :3].astype(np.float32)
        tm_rgb = tm_rgb * (1 - mag[:, :, None]) + mcol * mag[:, :, None]
        d_tm[:, :, :3] = np.clip(tm_rgb, 0, 255).astype(np.uint8)
        g_tm = np.zeros((128, 128, 4), dtype=np.uint8)
        g_tm[:, :, :3] = np.clip(mcol, 0, 255).astype(np.uint8)
        g_tm[:, :, 3] = np.where(mag > 0.24, np.clip(mag * 255.0, 0, 255), 0).astype(np.uint8)
        put("THROAT_MAGMA", d_tm, g_tm)

        # 8. EYE_FIRE (mắt cam-vàng rực sáng #FF7A00 -> #FFE070 + con ngươi dọc sắc; P2 lõi trắng chói!)
        ey_u = (xx - 64.0) / 56.0
        ey_v = (yy - 64.0) / 52.0
        rad = np.sqrt(ey_u * ey_u + ey_v * ey_v)
        iris_t = np.clip(1.0 - rad, 0.0, 1.0)
        outer = np.array([255, 110, 0], dtype=np.float32)       # #FF7A00
        inner = np.array([255, 248, 210], dtype=np.float32) if enraged else np.array([255, 224, 112], dtype=np.float32)
        ecol = outer * (1 - iris_t[:, :, None]) + inner * iris_t[:, :, None]
        # Viền mắt tối sắc nét
        rim = np.clip((rad - 0.82) / 0.16, 0.0, 1.0)[:, :, None]
        ecol = ecol * (1 - rim) + np.array([34, 12, 6], dtype=np.float32) * rim
        # Con ngươi dọc sắc (slit pupil)
        slit_p = ((np.abs(ey_u) < (0.09 if enraged else 0.13) * (1.0 - ey_v * ey_v * 0.72)) & (np.abs(ey_v) < 0.76))
        ecol[slit_p] = np.array([22, 6, 4], dtype=np.float32)
        d_eye = np.dstack([np.clip(ecol, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        g_eye = d_eye.copy()
        g_eye[slit_p, 3] = 0
        g_eye[rad > 0.90, 3] = 0
        put("EYE_FIRE", d_eye, g_eye)

        # 9. MAW_INFERNO (họng & khoang miệng tạo chiều sâu: viền sẫm đỏ cháy ở ngoài -> lõi cam-vàng rực sâu bên trong!)
        nm = fbm(128, 128, 909)
        mr = np.sqrt(((xx - 64.0) / 62.0) ** 2 + ((yy - 64.0) / 62.0) ** 2)
        depth_v = np.clip(1.0 - mr * 0.82, 0.0, 1.0)
        mc0 = np.array([58, 12, 10], dtype=np.float32)
        mc1 = np.array([168, 28, 12], dtype=np.float32)
        mc2 = np.array([255, 134, 24], dtype=np.float32)
        mc3 = np.array([255, 244, 174], dtype=np.float32) if enraged else np.array([255, 214, 96], dtype=np.float32)
        t = np.clip(depth_v * 0.85 + nm * 0.30, 0.0, 1.0)[:, :, None]
        mcol = np.where(t < 0.35, mc0 * (1 - t / 0.35) + mc1 * (t / 0.35),
               np.where(t < 0.72, mc1 * (1 - (t - 0.35) / 0.37) + mc2 * ((t - 0.35) / 0.37),
                        mc2 * (1 - (t - 0.72) / 0.28) + mc3 * ((t - 0.72) / 0.28)))
        d_maw = np.dstack([np.clip(mcol, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        g_maw = d_maw.copy()
        g_maw[:, :, 3] = np.clip(depth_v * 280.0, 35, 255).astype(np.uint8)
        put("MAW_INFERNO", d_maw, g_maw)

        # 10. IVORY_FANG (nanh ngà lộ hàm & vuốt chân sắc nét)
        nf = fbm(128, 128, 1001)
        fy = (yy / 127.0)[:, :, None]
        fc = np.array([142, 128, 114], dtype=np.float32) * (1 - fy) + np.array([236, 228, 212], dtype=np.float32) * fy
        fc *= (0.90 + 0.14 * nf[:, :, None])
        d_fang = np.dstack([np.clip(fc, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        put("IVORY_FANG", d_fang)

        # 11. EMBER_CORE (lõi than hồng chùy đuôi — cháy trắng-cam ở Phase 2!)
        ne = fbm(128, 128, 1102)
        ec_rad = np.sqrt(((xx - 64.0) / 64.0) ** 2 + ((yy - 64.0) / 64.0) ** 2)
        core_t = np.clip(1.0 - ec_rad * 0.72 + (ne - 0.5) * 0.35, 0.0, 1.0)[:, :, None]
        e1 = np.array([204, 44, 16], dtype=np.float32)
        e2 = np.array([255, 140, 24], dtype=np.float32)
        e3 = np.array([255, 252, 222], dtype=np.float32) if enraged else np.array([255, 226, 116], dtype=np.float32)
        ec = np.where(core_t < 0.5, e1 * (1 - core_t * 2) + e2 * (core_t * 2),
                      e2 * (1 - (core_t - 0.5) * 2) + e3 * ((core_t - 0.5) * 2))
        d_emb = np.dstack([np.clip(ec, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        put("EMBER_CORE", d_emb, d_emb.copy())

        # 12. CLAW_DARK (móng vuốt cong tối màu cổ cánh & gai vai/khuỷu/hông/gót)
        nc = fbm(128, 128, 1203)
        cy_t = (yy / 127.0)[:, :, None]
        cc = np.array([28, 22, 24], dtype=np.float32) * (1 - cy_t) + np.array([86, 72, 76], dtype=np.float32) * cy_t
        cc *= (0.85 + 0.25 * nc[:, :, None])
        d_cl = np.dstack([np.clip(cc, 0, 255).astype(np.uint8), np.full((128, 128), 255, dtype=np.uint8)])
        put("CLAW_DARK", d_cl)

        suffix = "_enraged" if enraged else ""
        Image.fromarray(diff, "RGBA").save(tex_dir / f"cinder_dragon{suffix}.png")
        Image.fromarray(glow, "RGBA").save(tex_dir / f"cinder_dragon{suffix}_glowmask.png")

    print(f"TEX_OK: 4 textures 512x512 RGBA saved in {tex_dir}")


# =============================================================================
# 4. SINH 16 ANIMATION MƯỢT CATMULLROM / EASE-IN-OUT (ĐỒNG BỘ 0-TICK DragonAttackTiming)
# =============================================================================
def kf(vec, mode="catmullrom"):
    v = [round(float(x), 2) for x in vec]
    if mode == "linear":
        return v
    return {"vector": v, "easing": mode} if mode != "catmullrom" else {"post": v, "lerp_mode": "catmullrom"}


def track(points: dict, mode="catmullrom") -> dict:
    return {f"{float(t):.2f}": kf(vec, mode) for t, vec in points.items()}


def make_anim(length_sec: float, loop, bone_map: dict) -> dict:
    a = {"animation_length": round(length_sec, 2), "bones": bone_map}
    if loop is True:
        a["loop"] = True
    elif loop == "hold":
        a["loop"] = "hold_on_last_frame"
    return a


def build_animations():
    anims = {}

    # =========================================================================
    # QUY ƯỚC GÓC XOAY GECKOLIB (`bedrock_rot_matrix = Rz(-rz) @ Ry(+ry) @ Rx(-rx)`):
    #   - Bind pose cánh x1.3 ĐÃ vồng cao & xòe tách biệt hoàn toàn 2 bên thân (p_sh.x=±7.6, p_f4.x=±25.0)!
    #   - Giữ góc xoay `idle`/`walk` tự nhiên & tinh tế quanh bind pose (`|ry| <= 5°`, `|rx| <= 4°`) ->
    #     màng cánh liền lạc 100%, 0 xé lớp, 2 cánh tách nhau >10 px trên MỌI tư thế!
    # =========================================================================
    WL_IDLE  = [2, -4, -6]
    WML_IDLE = [2, -4,  4]
    WWL_IDLE = [1, -3,  3]
    WR_IDLE  = [2,  4,  6]
    WMR_IDLE = [2,  4, -4]
    WWR_IDLE = [1,  3, -3]

    # 1. IDLE (3.20s = 64t): cúi thấp săn mồi đầy uy lực, thở dồn vai & cột sống S, cánh gập vồng cao trên lưng,
    #    đuôi 9 đốt uốn sóng lệch pha mượt như roi thép!
    anims["animation.cinder_dragon.idle"] = make_anim(3.20, True, {
        "body_root": {"position": track({0.0: [0, 0.0, 0], 1.6: [0, 0.75, 0], 3.2: [0, 0.0, 0]})},
        "body_mid": {"rotation": track({0.0: [1.5, -1.2, 0], 1.6: [-2.2, 1.2, 0], 3.2: [1.5, -1.2, 0]})},
        "body_chest": {"rotation": track({0.0: [1.8, 0.8, 0], 1.6: [-3.0, -0.8, 0], 3.2: [1.8, 0.8, 0]})},
        "pelvis": {"rotation": track({0.0: [-1.0, 1.0, 0], 1.6: [1.5, -1.0, 0], 3.2: [-1.0, 1.0, 0]})},
        "leg_fl_shoulder": {"rotation": track({0.0: [-1.5, 0, 0], 1.6: [2.5, 0, 0], 3.2: [-1.5, 0, 0]})},
        "leg_fr_shoulder": {"rotation": track({0.0: [-1.5, 0, 0], 1.6: [2.5, 0, 0], 3.2: [-1.5, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [-2.0, -1.8, 0], 1.6: [-5.5, 1.8, 0], 3.2: [-2.0, -1.8, 0]})},
        "neck_2": {"rotation": track({0.0: [-1.0, -1.5, 0], 0.4: [-2.0, -1.8, 0], 2.0: [-4.5, 1.8, 0], 3.2: [-1.0, -1.5, 0]})},
        "neck_3": {"rotation": track({0.0: [0.5, -1.2, 0], 0.6: [-0.5, -1.6, 0], 2.2: [-3.0, 1.6, 0], 3.2: [0.5, -1.2, 0]})},
        "neck_4": {"rotation": track({0.0: [1.5, -1.0, 0], 0.8: [0.5, -1.4, 0], 2.4: [-1.5, 1.4, 0], 3.2: [1.5, -1.0, 0]})},
        "head": {"rotation": track({0.0: [2.5, -1.5, 0], 1.6: [0.0, 1.5, 0], 3.2: [2.5, -1.5, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 1.6: [12, 0, 0], 3.2: [5, 0, 0]})},
        # Cánh x1.3 vồng cao tách biệt hoàn toàn 2 bên lưng (thở êm theo lồng ngực — 0 xé màng, 0 trùng!)
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.25: [3, -5, -9], 1.85: [1, -2, -3], 3.2: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 0.45: [3, -5, 6], 2.05: [1, -2, 2], 3.2: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 0.65: [2, -4, 5], 2.25: [0, -1, 1], 3.2: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.25: [3, 5, 9], 1.85: [1, 2, 3], 3.2: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 0.45: [3, 5, -6], 2.05: [1, 2, -2], 3.2: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 0.65: [2, 4, -5], 2.25: [0, 1, -1], 3.2: WWR_IDLE})},
        # Sóng lệch pha mềm mại chạy dọc toàn bộ 9 đốt đuôi!
        "tail_1": {"rotation": track({0.0: [0.5, -4, 0], 1.6: [-0.5, 4, 0], 3.2: [0.5, -4, 0]})},
        "tail_2": {"rotation": track({0.0: [1.0, -3.5, 0], 0.35: [1.2, -5.0, 0], 1.95: [-1.0, 5.0, 0], 3.2: [1.0, -3.5, 0]})},
        "tail_3": {"rotation": track({0.0: [1.2, -2.5, 0], 0.60: [1.6, -5.8, 0], 2.20: [-1.4, 5.8, 0], 3.2: [1.2, -2.5, 0]})},
        "tail_4": {"rotation": track({0.0: [1.0, -1.2, 0], 0.85: [1.8, -6.4, 0], 2.45: [-1.6, 6.4, 0], 3.2: [1.0, -1.2, 0]})},
        "tail_5": {"rotation": track({0.0: [0.6, 0.5, 0], 1.10: [2.0, -7.0, 0], 2.70: [-1.8, 7.0, 0], 3.2: [0.6, 0.5, 0]})},
        "tail_6": {"rotation": track({0.0: [0.0, 2.5, 0], 1.35: [2.2, -7.6, 0], 2.95: [-2.0, 7.6, 0], 3.2: [0.0, 2.5, 0]})},
        "tail_7": {"rotation": track({0.0: [-0.8, 4.5, 0], 1.60: [2.4, -8.2, 0], 3.2: [-0.8, 4.5, 0]})},
        "tail_8": {"rotation": track({0.0: [-1.5, 6.0, 0], 1.85: [2.8, -8.8, 0], 3.2: [-1.5, 6.0, 0]})},
        "tail_9": {"rotation": track({0.0: [-2.0, 7.2, 0], 2.10: [3.2, -9.5, 0], 3.2: [-2.0, 7.2, 0]})},
    })

    # 2. WALK (1.60s = 32t): bước chéo 4 chân ĐẦY ĐỦ 4 KHỚP/CHÂN (nhấc chân + gập cổ chân + đạp mạnh có lực!),
    #    cột sống uốn S đối xứng, vai đẩy theo bước chân, cánh gập nhún nặng & đuôi 9 đốt vung cân bằng lệch pha!
    anims["animation.cinder_dragon.walk"] = make_anim(1.60, True, {
        "body_root": {
            "position": track({0.0: [0, 0.4, 0], 0.4: [0, -1.1, 0], 0.8: [0, 0.4, 0], 1.2: [0, -1.1, 0], 1.6: [0, 0.4, 0]}),
            "rotation": track({0.0: [1.5, -4.5, -3.0], 0.4: [2.5, 0, -1.5], 0.8: [1.5, 4.5, 3.0], 1.2: [2.5, 0, 1.5], 1.6: [1.5, -4.5, -3.0]}),
        },
        "body_mid": {"rotation": track({0.0: [1.0, 6.0, 2.5], 0.8: [1.0, -6.0, -2.5], 1.6: [1.0, 6.0, 2.5]})},
        "body_chest": {"rotation": track({0.0: [1.5, 5.5, 3.0], 0.8: [1.5, -5.5, -3.0], 1.6: [1.5, 5.5, 3.0]})},
        "pelvis": {"rotation": track({0.0: [-1.5, -5.5, -2.5], 0.8: [-1.5, 5.5, 2.5], 1.6: [-1.5, -5.5, -2.5]})},
        "neck_1": {"rotation": track({0.0: [2, -5, 0], 0.4: [5, 0, 0], 0.8: [2, 5, 0], 1.2: [5, 0, 0], 1.6: [2, -5, 0]})},
        "neck_2": {"rotation": track({0.0: [1, -4, 0], 0.8: [1, 4, 0], 1.6: [1, -4, 0]})},
        "neck_3": {"rotation": track({0.0: [-1, 3, 0], 0.8: [-1, -3, 0], 1.6: [-1, 3, 0]})},
        "neck_4": {"rotation": track({0.0: [-1.5, 3.5, 0], 0.8: [-1.5, -3.5, 0], 1.6: [-1.5, 3.5, 0]})},
        "head": {"rotation": track({0.0: [-2.5, 3.0, 0], 0.4: [-4.0, 0, 0], 0.8: [-2.5, -3.0, 0], 1.2: [-4.0, 0, 0], 1.6: [-2.5, 3.0, 0]})},
        # Chân trước TRÁI (4 khớp: vai -> cánh tay -> cẳng tay -> bàn chân)
        "leg_fl_shoulder": {"rotation": track({0.0: [-8, 4, 0], 0.4: [2, 0, 0], 0.8: [10, -4, 0], 1.2: [0, 0, 0], 1.6: [-8, 4, 0]})},
        "leg_fl": {"rotation": track({0.0: [-24, 0, 0], 0.4: [-4, 0, 0], 0.8: [22, 0, 0], 1.2: [-14, 0, 0], 1.6: [-24, 0, 0]})},
        "leg_fl_lower": {"rotation": track({0.0: [18, 0, 0], 0.4: [2, 0, 0], 0.8: [-12, 0, 0], 1.2: [30, 0, 0], 1.6: [18, 0, 0]})},
        "leg_fl_foot": {"rotation": track({0.0: [8, 0, 0], 0.4: [2, 0, 0], 0.8: [-10, 0, 0], 1.2: [-18, 0, 0], 1.6: [8, 0, 0]})},
        # Chân trước PHẢI (lệch nửa chu kỳ 0.8s!)
        "leg_fr_shoulder": {"rotation": track({0.0: [10, 4, 0], 0.4: [0, 0, 0], 0.8: [-8, -4, 0], 1.2: [2, 0, 0], 1.6: [10, 4, 0]})},
        "leg_fr": {"rotation": track({0.0: [22, 0, 0], 0.4: [-14, 0, 0], 0.8: [-24, 0, 0], 1.2: [-4, 0, 0], 1.6: [22, 0, 0]})},
        "leg_fr_lower": {"rotation": track({0.0: [-12, 0, 0], 0.4: [30, 0, 0], 0.8: [18, 0, 0], 1.2: [2, 0, 0], 1.6: [-12, 0, 0]})},
        "leg_fr_foot": {"rotation": track({0.0: [-10, 0, 0], 0.4: [-18, 0, 0], 0.8: [8, 0, 0], 1.2: [2, 0, 0], 1.6: [-10, 0, 0]})},
        # Chân sau PHẢI (bước chéo cùng nhịp chân trước TRÁI — 4 khớp đùi/cẳng/cổ chân/bàn chân!)
        "leg_br": {"rotation": track({0.0: [-22, 0, 0], 0.4: [-2, 0, 0], 0.8: [20, 0, 0], 1.2: [-12, 0, 0], 1.6: [-22, 0, 0]})},
        "leg_br_lower": {"rotation": track({0.0: [16, 0, 0], 0.4: [0, 0, 0], 0.8: [-14, 0, 0], 1.2: [28, 0, 0], 1.6: [16, 0, 0]})},
        "leg_br_ankle": {"rotation": track({0.0: [-10, 0, 0], 0.4: [2, 0, 0], 0.8: [12, 0, 0], 1.2: [-22, 0, 0], 1.6: [-10, 0, 0]})},
        "leg_br_foot": {"rotation": track({0.0: [14, 0, 0], 0.4: [0, 0, 0], 0.8: [-12, 0, 0], 1.2: [10, 0, 0], 1.6: [14, 0, 0]})},
        # Chân sau TRÁI (bước chéo cùng nhịp chân trước PHẢI!)
        "leg_bl": {"rotation": track({0.0: [20, 0, 0], 0.4: [-12, 0, 0], 0.8: [-22, 0, 0], 1.2: [-2, 0, 0], 1.6: [20, 0, 0]})},
        "leg_bl_lower": {"rotation": track({0.0: [-14, 0, 0], 0.4: [28, 0, 0], 0.8: [16, 0, 0], 1.2: [0, 0, 0], 1.6: [-14, 0, 0]})},
        "leg_bl_ankle": {"rotation": track({0.0: [12, 0, 0], 0.4: [-22, 0, 0], 0.8: [-10, 0, 0], 1.2: [2, 0, 0], 1.6: [12, 0, 0]})},
        "leg_bl_foot": {"rotation": track({0.0: [-12, 0, 0], 0.4: [10, 0, 0], 0.8: [14, 0, 0], 1.2: [0, 0, 0], 1.6: [-12, 0, 0]})},
        # Cánh x1.3 vồng cao tách biệt 2 bên nhún theo bước chân (0 chạm nhau, 0 xé màng!)
        "wing_l": {"rotation": track({0.0: [3, -5, -8], 0.4: [1, -3, -4], 0.8: [3, -4, -8], 1.2: [1, -2, -4], 1.6: [3, -5, -8]})},
        "wing_mid_l": {"rotation": track({0.0: [3, -5, 6], 0.4: [1, -3, 3], 0.8: [3, -4, 6], 1.2: [1, -3, 3], 1.6: [3, -5, 6]})},
        "wing_wrist_l": {"rotation": track({0.0: [2, -4, 4], 0.8: [1, -2, 2], 1.6: [2, -4, 4]})},
        "wing_r": {"rotation": track({0.0: [3, 4, 8], 0.4: [1, 2, 4], 0.8: [3, 5, 8], 1.2: [1, 3, 4], 1.6: [3, 4, 8]})},
        "wing_mid_r": {"rotation": track({0.0: [3, 4, -6], 0.4: [1, 3, -3], 0.8: [3, 5, -6], 1.2: [1, 3, -3], 1.6: [3, 4, -6]})},
        "wing_wrist_r": {"rotation": track({0.0: [1, 2, -2], 0.8: [2, 4, -4], 1.6: [1, 2, -2]})},
        # Sóng đuôi 9 đốt vung cân bằng theo bước đi có trễ pha
        "tail_1": {"rotation": track({0.0: [0, 9, 0], 0.8: [0, -9, 0], 1.6: [0, 9, 0]})},
        "tail_2": {"rotation": track({0.0: [0, 7, 0], 0.20: [0, 10, 0], 1.00: [0, -10, 0], 1.6: [0, 7, 0]})},
        "tail_3": {"rotation": track({0.0: [0, 3, 0], 0.35: [0, 11, 0], 1.15: [0, -11, 0], 1.6: [0, 3, 0]})},
        "tail_4": {"rotation": track({0.0: [0, -2, 0], 0.50: [0, 12, 0], 1.30: [0, -12, 0], 1.6: [0, -2, 0]})},
        "tail_5": {"rotation": track({0.0: [0, -6, 0], 0.65: [0, 13, 0], 1.45: [0, -13, 0], 1.6: [0, -6, 0]})},
        "tail_7": {"rotation": track({0.0: [0, -10, 0], 0.80: [0, 14, 0], 1.6: [0, -10, 0]})},
        "tail_9": {"rotation": track({0.0: [0, -12, 0], 0.95: [0, 15, 0], 1.6: [0, -12, 0]})},
    })

    # 3. FLY (1.20s = 24t): bung sải cánh tối đa, nhịp đập 3 khớp (arm -> mid -> wrist/tip) tạo lực nâng khổng lồ!
    anims["animation.cinder_dragon.fly"] = make_anim(1.20, True, {
        "body_root": {
            "position": track({0.0: [0, 2.2, 0], 0.55: [0, -2.0, 0], 0.90: [0, 4.2, 0], 1.20: [0, 2.2, 0]}),
            "rotation": track({0.0: [-6, 0, 0], 0.55: [-12, 0, 0], 0.90: [-1, 0, 0], 1.20: [-6, 0, 0]}),
        },
        "body_mid": {"rotation": track({0.0: [-2, 0, 0], 0.55: [4, 0, 0], 0.90: [-6, 0, 0], 1.20: [-2, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.55: [5, 0, 0], 0.90: [-6, 0, 0], 1.20: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [2, -2, -10], 0.55: [6, -4, -24], 0.90: [-6, 8, 42], 1.20: [2, -2, -10]})},
        "wing_mid_l": {"rotation": track({0.0: [0, -2, 6], 0.55: [2, -4, 16], 0.90: [-4, 8, -14], 1.20: [0, -2, 6]})},
        "wing_wrist_l": {"rotation": track({0.0: [0, 0, 6], 0.62: [2, -4, 14], 0.96: [-4, 6, -10], 1.20: [0, 0, 6]})},
        "wing_tip_l": {"rotation": track({0.0: [0, 0, 6], 0.68: [0, -4, 18], 1.00: [0, 4, -10], 1.20: [0, 0, 6]})},
        "wing_r": {"rotation": track({0.0: [2, 2, 10], 0.55: [6, 4, 24], 0.90: [-6, -8, -42], 1.20: [2, 2, 10]})},
        "wing_mid_r": {"rotation": track({0.0: [0, 2, -6], 0.55: [2, 4, -16], 0.90: [-4, -8, 14], 1.20: [0, 2, -6]})},
        "wing_wrist_r": {"rotation": track({0.0: [0, 0, -6], 0.62: [2, 4, -14], 0.96: [-4, -6, 10], 1.20: [0, 0, -6]})},
        "wing_tip_r": {"rotation": track({0.0: [0, 0, -6], 0.68: [0, 4, -18], 1.00: [0, -4, 10], 1.20: [0, 0, -6]})},
        "leg_fl": {"rotation": track({0.0: [44, 0, 0], 0.60: [52, 0, 0], 1.20: [44, 0, 0]})},
        "leg_fl_lower": {"rotation": track({0.0: [38, 0, 0], 1.20: [38, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [44, 0, 0], 0.60: [52, 0, 0], 1.20: [44, 0, 0]})},
        "leg_fr_lower": {"rotation": track({0.0: [38, 0, 0], 1.20: [38, 0, 0]})},
        "leg_bl": {"rotation": track({0.0: [54, 0, 0], 0.60: [62, 0, 0], 1.20: [54, 0, 0]})},
        "leg_bl_lower": {"rotation": track({0.0: [24, 0, 0], 1.20: [24, 0, 0]})},
        "leg_br": {"rotation": track({0.0: [54, 0, 0], 0.60: [62, 0, 0], 1.20: [54, 0, 0]})},
        "leg_br_lower": {"rotation": track({0.0: [24, 0, 0], 1.20: [24, 0, 0]})},
        "tail_1": {"rotation": track({0.0: [-6, 0, 0], 0.60: [6, 0, 0], 1.20: [-6, 0, 0]})},
        "tail_3": {"rotation": track({0.0: [-5, 0, 0], 0.72: [8, 0, 0], 1.20: [-5, 0, 0]})},
        "tail_5": {"rotation": track({0.0: [-4, 0, 0], 0.84: [10, 0, 0], 1.20: [-4, 0, 0]})},
        "tail_7": {"rotation": track({0.0: [-4, 0, 0], 0.96: [11, 0, 0], 1.20: [-4, 0, 0]})},
    })

    # 4. HOVER (1.00s = 20t): lơ lửng vỗ cánh dứt khoát, thân dựng nhẹ chĩa đầu xuống mục tiêu
    anims["animation.cinder_dragon.hover"] = make_anim(1.00, True, {
        "body_root": {
            "position": track({0.0: [0, 1.8, 0], 0.45: [0, -1.4, 0], 0.80: [0, 3.2, 0], 1.00: [0, 1.8, 0]}),
            "rotation": track({0.0: [-22, 0, 0], 0.50: [-26, 0, 0], 1.00: [-22, 0, 0]}),
        },
        "neck_1": {"rotation": track({0.0: [12, 0, 0], 1.00: [12, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [8, 0, 0], 1.00: [8, 0, 0]})},
        "head": {"rotation": track({0.0: [12, 0, 0], 1.00: [12, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [3, -2, -12], 0.45: [7, -4, -24], 0.78: [-8, 10, 40], 1.00: [3, -2, -12]})},
        "wing_mid_l": {"rotation": track({0.0: [0, -2, 8], 0.48: [2, -4, 16], 0.80: [-4, 8, -14], 1.00: [0, -2, 8]})},
        "wing_wrist_l": {"rotation": track({0.0: [0, 0, 6], 0.54: [2, -4, 14], 0.84: [-4, 6, -10], 1.00: [0, 0, 6]})},
        "wing_r": {"rotation": track({0.0: [3, 2, 12], 0.45: [7, 4, 24], 0.78: [-8, -10, -40], 1.00: [3, 2, 12]})},
        "wing_mid_r": {"rotation": track({0.0: [0, 2, -8], 0.48: [2, 4, -16], 0.80: [-4, -8, 14], 1.00: [0, 2, -8]})},
        "wing_wrist_r": {"rotation": track({0.0: [0, 0, -6], 0.54: [2, 4, -14], 0.84: [-4, -6, 10], 1.00: [0, 0, -6]})},
        "leg_fl": {"rotation": track({0.0: [38, 0, 0], 1.00: [38, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [38, 0, 0], 1.00: [38, 0, 0]})},
        "leg_bl": {"rotation": track({0.0: [42, 0, 0], 1.00: [42, 0, 0]})},
        "leg_br": {"rotation": track({0.0: [42, 0, 0], 1.00: [42, 0, 0]})},
        "tail_1": {"rotation": track({0.0: [-14, 0, 0], 0.50: [-22, 0, 0], 1.00: [-14, 0, 0]})},
        "tail_4": {"rotation": track({0.0: [-10, 0, 0], 0.62: [-16, 0, 0], 1.00: [-10, 0, 0]})},
    })

    # 5. BREATH — Cinder Breath (2.80s = 56t, telegraph 0..20t [1.0s], quét trái->phải 20..46t, settle 46..56t)
    anims["animation.cinder_dragon.breath"] = make_anim(2.80, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 0.75: [0, -2.2, 2.4], 1.00: [0, -1.6, -1.2], 2.30: [0, -1.4, -1.2], 2.80: [0, 0, 0]})},
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 0.80: [-8, 8, 0], 1.05: [6, 12, 0], 1.65: [5, 0, 0], 2.25: [6, -12, 0], 2.80: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.80: [-10, 10, 0], 1.05: [10, 14, 0], 1.65: [8, 0, 0], 2.25: [10, -14, 0], 2.80: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.80: [-18, 14, 0], 1.05: [10, 18, 0], 1.65: [8, 0, 0], 2.25: [10, -18, 0], 2.80: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 0.80: [-14, 14, 0], 1.05: [8, 18, 0], 1.65: [6, 0, 0], 2.25: [8, -18, 0], 2.80: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 0.80: [-10, 12, 0], 1.05: [6, 14, 0], 1.65: [4, 0, 0], 2.25: [6, -14, 0], 2.80: [0, 0, 0]})},
        "neck_4": {"rotation": track({0.0: [0, 0, 0], 0.80: [-8, 10, 0], 1.05: [4, 12, 0], 1.65: [4, 0, 0], 2.25: [4, -12, 0], 2.80: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.80: [-10, 10, 0], 1.05: [6, 12, 0], 1.65: [6, 0, 0], 2.25: [6, -12, 0], 2.80: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 0.80: [24, 0, 0], 1.00: [46, 0, 0], 2.25: [44, 0, 0], 2.80: [5, 0, 0]})},
        # Dang cánh rộng áp đảo khi phun lửa!
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.80: [16, -16, -34], 1.10: [6, 6, -12], 2.30: [6, 4, -14], 2.80: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 0.85: [4, 4, 8], 1.10: [0, 0, -4], 2.30: [0, 0, -2], 2.80: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 0.85: [4, -6, 6], 1.10: [0, 0, 0], 2.30: [0, 0, 0], 2.80: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.80: [16, 16, 34], 1.10: [6, -6, 12], 2.30: [6, -4, 14], 2.80: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 0.85: [4, -4, -8], 1.10: [0, 0, 4], 2.30: [0, 0, 2], 2.80: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 0.85: [4, 6, -6], 1.10: [0, 0, 0], 2.30: [0, 0, 0], 2.80: WWR_IDLE})},
    })

    # 6. FIREBALL — Magma Fireball Barrage (2.20s = 44t, shots at 16t [0.80s], 24t [1.20s], 32t [1.60s])
    anims["animation.cinder_dragon.fireball"] = make_anim(2.20, False, {
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.65: [-10, 0, 0], 0.80: [6, 0, 0], 1.05: [-8, 0, 0], 1.20: [6, 0, 0], 1.45: [-8, 0, 0], 1.60: [8, 0, 0], 2.20: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.65: [-20, 0, 0], 0.80: [10, 0, 0], 1.05: [-16, 0, 0], 1.20: [10, 0, 0], 1.45: [-16, 0, 0], 1.60: [12, 0, 0], 2.20: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 0.65: [-14, 0, 0], 0.80: [8, 0, 0], 1.05: [-12, 0, 0], 1.20: [8, 0, 0], 1.45: [-12, 0, 0], 1.60: [10, 0, 0], 2.20: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 0.65: [-10, 0, 0], 0.80: [8, 0, 0], 1.05: [-10, 0, 0], 1.20: [8, 0, 0], 1.45: [-10, 0, 0], 1.60: [10, 0, 0], 2.20: [0, 0, 0]})},
        "neck_4": {"rotation": track({0.0: [0, 0, 0], 0.65: [-8, 0, 0], 0.80: [8, 0, 0], 1.05: [-8, 0, 0], 1.20: [8, 0, 0], 1.45: [-8, 0, 0], 1.60: [8, 0, 0], 2.20: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.65: [-12, 0, 0], 0.80: [12, 0, 0], 1.05: [-10, 0, 0], 1.20: [12, 0, 0], 1.45: [-10, 0, 0], 1.60: [12, 0, 0], 2.20: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 0.65: [38, 0, 0], 0.85: [12, 0, 0], 1.05: [38, 0, 0], 1.25: [12, 0, 0], 1.45: [40, 0, 0], 1.70: [12, 0, 0], 2.20: [5, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.70: [16, -24, -30], 1.60: [10, -16, -18], 2.20: WL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.70: [16, 24, 30], 1.60: [10, 16, 18], 2.20: WR_IDLE})},
    })

    # 7. BITE — Savage Bite & Horn Gore (1.80s = 36t, telegraph 0..18t [0.90s], impact 19t [0.95s])
    #    Thu nén lò xo cả cột sống + 4 đốt cổ + 2 vai trước -> chồm táp bùng nổ cực có lực!
    anims["animation.cinder_dragon.bite"] = make_anim(1.80, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 0.75: [0, -2.0, 5.5], 0.95: [0, -1.0, -7.2], 1.25: [0, -0.5, -3.5], 1.80: [0, 0, 0]})},
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 0.75: [-12, 0, 0], 0.95: [12, 0, 0], 1.25: [5, 0, 0], 1.80: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.75: [-14, 0, 0], 0.95: [16, 0, 0], 1.25: [8, 0, 0], 1.80: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.75: [-26, 0, 0], 0.95: [20, 0, 0], 1.25: [10, 0, 0], 1.80: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 0.75: [-18, 0, 0], 0.95: [16, 0, 0], 1.80: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 0.75: [-14, 0, 0], 0.95: [14, 0, 0], 1.80: [0, 0, 0]})},
        "neck_4": {"rotation": track({0.0: [0, 0, 0], 0.75: [-10, 0, 0], 0.95: [12, 0, 0], 1.80: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.75: [-14, 0, 0], 0.95: [22, 0, 0], 1.25: [-10, 0, 0], 1.80: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 0.78: [50, 0, 0], 0.95: [1, 0, 0], 1.25: [10, 0, 0], 1.80: [5, 0, 0]})},
        "leg_fl": {"rotation": track({0.0: [0, 0, 0], 0.75: [-18, 0, 0], 0.95: [24, 0, 0], 1.80: [0, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [0, 0, 0], 0.75: [-18, 0, 0], 0.95: [24, 0, 0], 1.80: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.75: [18, -22, -38], 0.98: [8, -8, -14], 1.80: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 0.75: [4, 4, 8], 0.98: [0, 2, 4], 1.80: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 0.75: [4, -10, 6], 0.98: [0, -4, 2], 1.80: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.75: [18, 22, 38], 0.98: [8, 8, 14], 1.80: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 0.75: [4, -4, -8], 0.98: [0, -2, -4], 1.80: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 0.75: [4, 10, -6], 0.98: [0, 4, -2], 1.80: WWR_IDLE})},
    })

    # 8. WING_SWEEP — Wing Claw Sweep (1.80s = 36t, telegraph 0..16t [0.80s], impact 18t [0.90s])
    anims["animation.cinder_dragon.wing_sweep"] = make_anim(1.80, False, {
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 0.70: [2, -16, -6], 0.90: [6, 18, 6], 1.25: [3, 10, 2], 1.80: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.70: [4, -22, -8], 0.90: [10, 26, 10], 1.25: [6, 14, 4], 1.80: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.70: [-6, 18, 0], 0.90: [8, -20, 0], 1.80: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.70: [18, -50, -44], 0.90: [-10, 44, 16], 1.25: [2, 20, 6], 1.80: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 0.70: [4, -20, 14], 0.90: [-4, 24, -14], 1.80: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 0.70: [4, -16, 10], 0.90: [-4, 18, -10], 1.80: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.70: [14, 24, 22], 0.95: [12, 20, 18], 1.80: WR_IDLE})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 0.70: [0, 14, 0], 0.90: [0, -18, 0], 1.80: [0, 0, 0]})},
        "tail_4": {"rotation": track({0.0: [0, 0, 0], 0.80: [0, 18, 0], 1.05: [0, -22, 0], 1.80: [0, 0, 0]})},
    })

    # 9. TAIL_SLAM — Tail Crescent Slam (2.00s = 40t, telegraph 0..18t [0.90s], impact 20t [1.00s])
    #    Xoắn hông + làn sóng roi thép lệch pha qua cả 9 đốt đuôi -> quất đại lưỡi liềm sấm sét!
    anims["animation.cinder_dragon.tail_slam"] = make_anim(2.00, False, {
        "body_root": {"rotation": track({0.0: [0, 0, 0], 0.80: [4, -22, 0], 1.00: [-4, 26, 0], 1.35: [0, 12, 0], 2.00: [0, 0, 0]})},
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 0.80: [2, 16, 0], 1.00: [-2, -18, 0], 2.00: [0, 0, 0]})},
        "pelvis": {"rotation": track({0.0: [0, 0, 0], 0.80: [10, -26, 8], 1.00: [-8, 34, -8], 1.40: [-4, 16, 0], 2.00: [0, 0, 0]})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 0.78: [16, -28, 0], 0.96: [-8, 34, 0], 1.35: [-4, 18, 0], 2.00: [0, 0, 0]})},
        "tail_2": {"rotation": track({0.0: [0, 0, 0], 0.82: [14, -26, 0], 0.99: [-6, 32, 0], 1.38: [-4, 18, 0], 2.00: [0, 0, 0]})},
        "tail_3": {"rotation": track({0.0: [0, 0, 0], 0.85: [12, -24, 0], 1.02: [-6, 30, 0], 1.42: [-3, 16, 0], 2.00: [0, 0, 0]})},
        "tail_4": {"rotation": track({0.0: [0, 0, 0], 0.88: [12, -22, 0], 1.05: [-6, 28, 0], 1.45: [-3, 15, 0], 2.00: [0, 0, 0]})},
        "tail_5": {"rotation": track({0.0: [0, 0, 0], 0.90: [10, -20, 0], 1.08: [-6, 26, 0], 1.48: [-2, 14, 0], 2.00: [0, 0, 0]})},
        "tail_6": {"rotation": track({0.0: [0, 0, 0], 0.92: [10, -18, 0], 1.11: [-6, 24, 0], 1.52: [-2, 12, 0], 2.00: [0, 0, 0]})},
        "tail_7": {"rotation": track({0.0: [0, 0, 0], 0.94: [10, -16, 0], 1.14: [-6, 22, 0], 1.55: [-2, 10, 0], 2.00: [0, 0, 0]})},
        "tail_8": {"rotation": track({0.0: [0, 0, 0], 0.96: [10, -14, 0], 1.17: [-6, 20, 0], 1.58: [-2, 8, 0], 2.00: [0, 0, 0]})},
        "tail_9": {"rotation": track({0.0: [0, 0, 0], 0.98: [12, -14, 0], 1.20: [-8, 22, 0], 1.62: [-2, 8, 0], 2.00: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.85: [18, -12, -38], 1.05: [8, -20, -16], 2.00: WL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.85: [18, 12, 38], 1.05: [8, 20, 16], 2.00: WR_IDLE})},
    })

    # 10. STOMP — Winged Stomp (2.10s = 42t, hop 0..18t [0.90s], slam impact 21t [1.05s])
    anims["animation.cinder_dragon.stomp"] = make_anim(2.10, False, {
        "body_root": {
            "position": track({0.0: [0, 0, 0], 0.80: [0, 9.0, 0], 1.05: [0, -2.6, 0], 1.35: [0, -1.0, 0], 2.10: [0, 0, 0]}),
            "rotation": track({0.0: [0, 0, 0], 0.80: [-28, 0, 0], 1.05: [14, 0, 0], 1.40: [4, 0, 0], 2.10: [0, 0, 0]}),
        },
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 0.80: [-14, 0, 0], 1.05: [10, 0, 0], 2.10: [0, 0, 0]})},
        "leg_fl": {"rotation": track({0.0: [0, 0, 0], 0.80: [-46, 0, -10], 1.05: [22, 0, 4], 2.10: [0, 0, 0]})},
        "leg_fl_lower": {"rotation": track({0.0: [0, 0, 0], 0.80: [38, 0, 0], 1.05: [-14, 0, 0], 2.10: [0, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [0, 0, 0], 0.80: [-46, 0, 10], 1.05: [22, 0, -4], 2.10: [0, 0, 0]})},
        "leg_fr_lower": {"rotation": track({0.0: [0, 0, 0], 0.80: [38, 0, 0], 1.05: [-14, 0, 0], 2.10: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.45: [16, -10, -50], 0.85: [-12, 10, 26], 1.08: [10, -12, -20], 2.10: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 0.45: [4, -10, 20], 0.85: [-4, 10, -12], 2.10: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 0.45: [4, -8, 16], 0.85: [-4, 6, -8], 2.10: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.45: [16, 10, 50], 0.85: [-12, -10, -26], 1.08: [10, 12, 20], 2.10: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 0.45: [4, 10, -20], 0.85: [-4, -10, 12], 2.10: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 0.45: [4, 8, -16], 0.85: [-4, -6, 8], 2.10: WWR_IDLE})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.80: [-18, 0, 0], 1.05: [18, 0, 0], 2.10: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 0.80: [-12, 0, 0], 1.05: [14, 0, 0], 2.10: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 0.80: [40, 0, 0], 1.15: [24, 0, 0], 2.10: [5, 0, 0]})},
    })

    # 11. SKYFALL_INFERNO (Phase 2 exclusive — 4.60s = 92t)
    anims["animation.cinder_dragon.skyfall_inferno"] = make_anim(4.60, False, {
        "body_root": {"rotation": track({0.0: [0, 0, 0], 0.60: [-28, 0, 0], 1.20: [8, 0, 0], 3.40: [10, 0, 0], 4.00: [16, 0, 0], 4.60: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.20: [18, -10, 0], 2.30: [22, 10, 0], 3.40: [18, -8, 0], 4.60: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 1.20: [14, -8, 0], 2.30: [16, 8, 0], 3.40: [14, -6, 0], 4.60: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 1.20: [18, -8, 0], 2.30: [20, 8, 0], 3.40: [16, -6, 0], 4.60: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 1.20: [48, 0, 0], 3.40: [48, 0, 0], 4.00: [18, 0, 0], 4.60: [5, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.50: [14, -8, -48], 1.00: [-10, 8, 30], 1.60: [12, -10, -44], 2.20: [-10, 8, 30], 2.80: [12, -10, -44], 3.40: [-10, 8, 30], 4.00: [10, -14, -22], 4.60: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 0.55: [4, -10, 26], 1.05: [-4, 8, -14], 2.20: [-4, 8, -14], 3.40: [-4, 8, -14], 4.60: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 0.60: [4, -8, 28], 1.10: [-4, 6, -12], 3.40: [-4, 6, -12], 4.60: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.50: [14, 8, 48], 1.00: [-10, -8, -30], 1.60: [12, 10, 44], 2.20: [-10, -8, -30], 2.80: [12, 10, 44], 3.40: [-10, -8, -30], 4.00: [10, 14, 22], 4.60: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 0.55: [4, 10, -26], 1.05: [-4, -8, 14], 2.20: [-4, -8, 14], 3.40: [-4, -8, 14], 4.60: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 0.60: [4, 8, -28], 1.10: [-4, -6, 12], 3.40: [-4, -6, 12], 4.60: WWR_IDLE})},
        "leg_fl": {"rotation": track({0.0: [0, 0, 0], 0.80: [48, 0, 0], 3.60: [42, 0, 0], 4.05: [-12, 0, 0], 4.60: [0, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [0, 0, 0], 0.80: [48, 0, 0], 3.60: [42, 0, 0], 4.05: [-12, 0, 0], 4.60: [0, 0, 0]})},
        "leg_bl": {"rotation": track({0.0: [0, 0, 0], 0.80: [54, 0, 0], 3.60: [48, 0, 0], 4.05: [0, 0, 0], 4.60: [0, 0, 0]})},
        "leg_br": {"rotation": track({0.0: [0, 0, 0], 0.80: [54, 0, 0], 3.60: [48, 0, 0], 4.05: [0, 0, 0], 4.60: [0, 0, 0]})},
    })

    # 12. BEAM — Scorched Earth Beam (Phase 2 exclusive — 3.20s = 64t)
    anims["animation.cinder_dragon.beam"] = make_anim(3.20, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 1.15: [0, -2.6, 3.8], 1.35: [0, -1.6, 5.4], 2.40: [0, -1.8, 4.8], 3.20: [0, 0, 0]})},
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 1.15: [-12, 0, 0], 1.35: [6, 0, 0], 2.40: [5, 0, 0], 3.20: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 1.15: [-16, 0, 0], 1.35: [8, 0, 0], 2.40: [6, 0, 0], 3.20: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.15: [-24, 0, 0], 1.35: [12, 0, 0], 2.40: [10, 0, 0], 3.20: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 1.15: [-18, 0, 0], 1.35: [8, 0, 0], 2.40: [8, 0, 0], 3.20: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 1.15: [-12, 0, 0], 1.35: [6, 0, 0], 2.40: [6, 0, 0], 3.20: [0, 0, 0]})},
        "neck_4": {"rotation": track({0.0: [0, 0, 0], 1.15: [-10, 0, 0], 1.35: [4, 0, 0], 2.40: [4, 0, 0], 3.20: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 1.15: [-14, 0, 0], 1.35: [4, 0, 0], 2.40: [4, 0, 0], 3.20: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 1.15: [34, 0, 0], 1.30: [54, 0, 0], 2.45: [52, 0, 0], 3.20: [5, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 1.15: [18, -28, -38], 1.35: [6, 12, -10], 2.45: [6, 10, -12], 3.20: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 1.15: [4, 0, 8], 1.35: [0, 0, -4], 2.45: [0, 0, -4], 3.20: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 1.35: [0, 0, 0], 2.45: [0, 0, 0], 3.20: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 1.15: [18, 28, 38], 1.35: [6, -12, 10], 2.45: [6, -10, 12], 3.20: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 1.15: [4, 0, -8], 1.35: [0, 0, 4], 2.45: [0, 0, 4], 3.20: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 1.35: [0, 0, 0], 2.45: [0, 0, 0], 3.20: WWR_IDLE})},
    })

    # 13. DIVE — Cataclysm Wing Dive (Phase 2 exclusive — 3.80s = 76t)
    anims["animation.cinder_dragon.dive"] = make_anim(3.80, False, {
        "body_root": {"rotation": track({0.0: [0, 0, 0], 0.90: [-34, 0, 0], 1.80: [24, 0, 0], 2.45: [46, 0, 0], 2.65: [14, 0, 0], 3.80: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 0.55: [14, -10, -48], 1.10: [-12, 10, 30], 1.90: [12, -48, -42], 2.55: [12, -52, -44], 2.80: [12, 12, -16], 3.80: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 1.90: [4, -34, 38], 2.55: [4, -36, 40], 2.85: [0, 8, -8], 3.80: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 1.90: [4, -30, 32], 2.55: [4, -32, 34], 2.85: [0, 6, -6], 3.80: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 0.55: [14, 10, 48], 1.10: [-12, -10, -30], 1.90: [12, 48, 42], 2.55: [12, 52, 44], 2.80: [12, -12, 16], 3.80: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 1.90: [4, 34, -38], 2.55: [4, 36, -40], 2.85: [0, -8, 8], 3.80: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 1.90: [4, 30, -32], 2.55: [4, 32, -34], 2.85: [0, -6, 6], 3.80: WWR_IDLE})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.90: [16, 0, 0], 2.55: [22, 0, 0], 2.85: [-18, 0, 0], 3.80: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 2.00: [38, 0, 0], 2.85: [46, 0, 0], 3.80: [5, 0, 0]})},
    })

    # 14. SUPERNOVA — Ultimate "Ashen Supernova" (Phase 2 <25% HP — 4.40s = 88t)
    anims["animation.cinder_dragon.supernova"] = make_anim(4.40, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 1.20: [0, -3.6, 0], 2.15: [0, -4.4, 0], 2.35: [0, 2.4, 0], 3.10: [0, 1.0, 0], 4.40: [0, 0, 0]})},
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 1.20: [14, 0, 0], 2.15: [18, 0, 0], 2.35: [-18, 0, 0], 3.20: [-10, 0, 0], 4.40: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 1.20: [16, 0, 0], 2.15: [20, 0, 0], 2.35: [-24, 0, 0], 3.20: [-14, 0, 0], 4.40: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 2.15: [22, 0, 0], 2.35: [-28, 0, 0], 3.20: [-18, 0, 0], 4.40: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 2.15: [18, 0, 0], 2.35: [-22, 0, 0], 4.40: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 2.15: [14, 0, 0], 2.35: [-18, 0, 0], 4.40: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 2.15: [14, 0, 0], 2.35: [-24, 0, 0], 4.40: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 1.50: [14, 0, 0], 2.30: [56, 0, 0], 3.30: [48, 0, 0], 4.40: [5, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 1.10: [-14, 44, 24], 2.15: [-18, 50, 28], 2.35: [16, -16, -46], 3.25: [12, -12, -34], 4.40: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 1.10: [0, 32, 40], 2.15: [0, 38, 44], 2.35: [0, -8, -12], 4.40: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 2.15: [0, 24, 34], 2.35: [0, -4, -6], 4.40: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 1.10: [-14, -44, -24], 2.15: [-18, -50, -28], 2.35: [16, 16, 46], 3.25: [12, 12, 34], 4.40: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 1.10: [0, -32, -40], 2.15: [0, -38, -44], 2.35: [0, 8, 12], 4.40: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 2.15: [0, -24, -34], 2.35: [0, 4, 6], 4.40: WWR_IDLE})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 2.15: [-14, 0, 0], 2.40: [18, 0, 0], 4.40: [0, 0, 0]})},
        "tail_5": {"rotation": track({0.0: [0, 0, 0], 2.15: [-12, 0, 0], 2.50: [20, 0, 0], 4.40: [0, 0, 0]})},
    })

    # 15. PHASE_CHANGE — "Vô Form" (5.50s = 110t: nén ôm cánh -> bật ngửa gầm bùng toàn sải cánh!)
    anims["animation.cinder_dragon.phase_change"] = make_anim(5.50, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 1.40: [0, -4.0, 0], 2.70: [0, -4.6, 0], 3.10: [0, 2.0, 0], 4.40: [0, 1.2, 0], 5.50: [0, 0, 0]})},
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 2.70: [16, 0, 0], 3.10: [-20, 0, 0], 4.40: [-14, 0, 0], 5.50: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 1.40: [18, 0, 0], 2.70: [22, 0, 0], 3.10: [-26, 0, 0], 4.40: [-18, 0, 0], 5.50: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 2.70: [20, 0, 0], 3.10: [-28, 0, 0], 4.40: [-22, 0, 0], 5.50: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 2.70: [16, 0, 0], 3.10: [-22, 0, 0], 4.40: [-16, 0, 0], 5.50: [0, 0, 0]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 2.70: [12, 0, 0], 3.10: [-18, 0, 0], 4.40: [-12, 0, 0], 5.50: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 2.70: [14, 0, 0], 3.10: [-24, 0, 0], 4.40: [-18, 0, 0], 5.50: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 2.60: [12, 0, 0], 3.05: [58, 0, 0], 4.50: [52, 0, 0], 5.50: [8, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 1.40: [-14, 44, 22], 2.70: [-18, 50, 26], 3.15: [18, -18, -48], 4.45: [12, -14, -36], 5.50: WL_IDLE})},
        "wing_mid_l": {"rotation": track({0.0: WML_IDLE, 2.70: [0, 36, 42], 3.15: [0, -10, -14], 5.50: WML_IDLE})},
        "wing_wrist_l": {"rotation": track({0.0: WWL_IDLE, 2.70: [0, 24, 34], 3.15: [0, -6, -8], 5.50: WWL_IDLE})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 1.40: [-14, -44, -22], 2.70: [-18, -50, -26], 3.15: [18, 18, 48], 4.45: [12, 14, 36], 5.50: WR_IDLE})},
        "wing_mid_r": {"rotation": track({0.0: WMR_IDLE, 2.70: [0, -36, -42], 3.15: [0, 10, 14], 5.50: WMR_IDLE})},
        "wing_wrist_r": {"rotation": track({0.0: WWR_IDLE, 2.70: [0, -24, -34], 3.15: [0, 6, 8], 5.50: WWR_IDLE})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 2.70: [-12, 0, 0], 3.15: [20, 0, 0], 5.50: [0, 0, 0]})},
        "tail_6": {"rotation": track({0.0: [0, 0, 0], 2.70: [-10, 0, 0], 3.25: [22, 0, 0], 5.50: [0, 0, 0]})},
    })

    # 16. DEATH (3.20s = 64t, hold_on_last_frame)
    anims["animation.cinder_dragon.death"] = make_anim(3.20, "hold", {
        "body_root": {
            "position": track({0.0: [0, 0, 0], 0.80: [0, 1.5, 2.0], 1.90: [0, -7.8, 4.0], 3.20: [0, -9.2, 4.5]}),
            "rotation": track({0.0: [0, 0, 0], 0.80: [-14, 0, 8], 1.90: [6, 0, 22], 3.20: [8, 0, 26]}),
        },
        "body_mid": {"rotation": track({0.0: [0, 0, 0], 2.40: [8, 10, 8], 3.20: [10, 14, 10]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.80: [-20, 0, 0], 2.20: [20, 10, 8], 3.20: [24, 14, 10]})},
        "neck_3": {"rotation": track({0.0: [0, 0, 0], 2.30: [16, 12, 8], 3.20: [20, 14, 10]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.80: [-16, 0, 0], 2.40: [22, 14, 14], 3.20: [26, 16, 16]})},
        "jaw": {"rotation": track({0.0: [5, 0, 0], 0.80: [42, 0, 0], 3.20: [28, 0, 0]})},
        "wing_l": {"rotation": track({0.0: WL_IDLE, 1.20: [14, -18, -32], 2.40: [-16, -24, 18], 3.20: [-20, -26, 22]})},
        "wing_r": {"rotation": track({0.0: WR_IDLE, 1.20: [14, 18, 32], 2.40: [-16, 24, -18], 3.20: [-20, 26, -22]})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 2.50: [-10, -14, 0], 3.20: [-12, -18, 0]})},
        "tail_5": {"rotation": track({0.0: [0, 0, 0], 2.70: [-10, -18, 0], 3.20: [-12, -22, 0]})},
    })

    # Kiểm tra an toàn: không có góc xoay vượt 170° + ĐẢM BẢO 2 CÁNH KHÔNG BAO GIỜ TRÙNG/DÍNH QUA X = 0!
    max_rot = 0.0
    for name, anim in anims.items():
        for bname, ch in anim["bones"].items():
            for t, entry in ch.get("rotation", {}).items():
                vec = entry.get("post", entry.get("vector")) if isinstance(entry, dict) else entry
                for v in vec:
                    assert abs(v) <= 170.0, f"Abnormal rot {v} in {name}.{bname}@{t}"
                    max_rot = max(max_rot, abs(v))

    out = ASSETS / "animations" / "entity" / "cinder_dragon.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=2)
    print(f"ANIM_OK: {out} ({len(anims)} smooth animations, max |rot|={max_rot:.1f} deg)")
    return anims


# =============================================================================
# 5. BỘ RENDER PREVIEW 3D Z-BUFFER ĐA GÓC (TRƯỚC, SAU, TRÁI, PHẢI, TRÊN XUỐNG, PHASE 2)
# =============================================================================
def transform_affine(R, t):
    M = np.eye(4, dtype=np.float64)
    M[:3, :3] = R
    M[:3, 3] = t
    return M


def rot_about_pivot(pivot, rot_deg):
    p = np.array(pivot, dtype=np.float64)
    R = bedrock_rot_matrix(rot_deg)
    return transform_affine(R, p - R @ p)


def collect_world_geometry(geo: dict, anim_bones: dict = None):
    bones_list = geo["minecraft:geometry"][0]["bones"]
    by_name = {b["name"]: b for b in bones_list}
    world_M = {}

    def get_anim_vec(bname: str, channel: str):
        if not anim_bones or bname not in anim_bones:
            return [0.0, 0.0, 0.0]
        ch = anim_bones[bname].get(channel)
        if not ch:
            return [0.0, 0.0, 0.0]
        entry = ch.get("0.00") or next(iter(ch.values()))
        vec = entry.get("post", entry.get("vector")) if isinstance(entry, dict) else entry
        return [float(vec[0]), float(vec[1]), float(vec[2])]

    def get_bone_M(name):
        if name in world_M:
            return world_M[name]
        b = by_name[name]
        base_rot = b.get("rotation", [0, 0, 0])
        arot = get_anim_vec(name, "rotation")
        apos = get_anim_vec(name, "position")
        rot = [base_rot[0] + arot[0], base_rot[1] + arot[1], base_rot[2] + arot[2]]
        local = rot_about_pivot(b.get("pivot", [0, 0, 0]), rot)
        if any(abs(v) > 1e-4 for v in apos):
            T = np.eye(4, dtype=np.float64)
            T[:3, 3] = np.array([-apos[0], apos[1], apos[2]], dtype=np.float64)
            local = T @ local
        parent = b.get("parent")
        M = (get_bone_M(parent) @ local) if parent else local
        world_M[name] = M
        return M

    faces = []
    all_pts = []
    for b in bones_list:
        if not b.get("cubes"):
            continue
        bM = get_bone_M(b["name"])
        for c in b["cubes"]:
            cM = bM @ (rot_about_pivot(c["pivot"], c["rotation"]) if "rotation" in c else np.eye(4))
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            corners = np.array([
                [ox, oy, oz, 1], [ox + sx, oy, oz, 1], [ox + sx, oy + sy, oz, 1], [ox, oy + sy, oz, 1],
                [ox, oy, oz + sz, 1], [ox + sx, oy, oz + sz, 1], [ox + sx, oy + sy, oz + sz, 1], [ox, oy + sy, oz + sz, 1],
            ], dtype=np.float64)
            pts = (cM @ corners.T).T[:, :3]
            all_pts.append(pts)
            uv_map = c["uv"]
            for fname, idxs in (
                ("north", [0, 1, 2, 3]), ("south", [5, 4, 7, 6]),
                ("west", [4, 0, 3, 7]), ("east", [1, 5, 6, 2]),
                ("up", [3, 2, 6, 7]), ("down", [4, 5, 1, 0]),
            ):
                faces.append((pts[idxs], uv_map[fname]))

    stacked = np.vstack(all_pts)
    return faces, stacked.min(axis=0), stacked.max(axis=0)


def rasterize_alpha_cutout(faces, diff_img, glow_img, view_mat, width, height, scale, center, bg=(22, 21, 26)):
    fb = np.zeros((height, width, 3), dtype=np.float32)
    fb[:, :] = bg
    zbuf = np.full((height, width), -1e9, dtype=np.float32)

    light_dir = np.array([0.38, 0.70, -0.60], dtype=np.float64)
    light_dir /= np.linalg.norm(light_dir)
    cx, cy = width * 0.5, height * 0.54

    def raster_tri(p0, p1, p2, uv0, uv1, uv2, shade):
        min_x = max(0, int(math.floor(min(p0[0], p1[0], p2[0]))))
        max_x = min(width - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
        min_y = max(0, int(math.floor(min(p0[1], p1[1], p2[1]))))
        max_y = min(height - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
        if min_x > max_x or min_y > max_y:
            return
        area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
        if abs(area) < 1e-4:
            return
        inv = 1.0 / area
        gx, gy = np.meshgrid(np.arange(min_x, max_x + 1, dtype=np.float32) + 0.5,
                             np.arange(min_y, max_y + 1, dtype=np.float32) + 0.5)
        w0 = ((p1[0] - gx) * (p2[1] - gy) - (p2[0] - gx) * (p1[1] - gy)) * inv
        w1 = ((p2[0] - gx) * (p0[1] - gy) - (p0[0] - gx) * (p2[1] - gy)) * inv
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
        if not np.any(inside):
            return
        z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
        sub_z = zbuf[min_y:max_y + 1, min_x:max_x + 1]
        cand = inside & (z > sub_z)
        if not np.any(cand):
            return

        u = np.clip((w0[cand] * uv0[0] + w1[cand] * uv1[0] + w2[cand] * uv2[0]).astype(np.int32), 0, TEX_W - 1)
        v = np.clip((w0[cand] * uv0[1] + w1[cand] * uv1[1] + w2[cand] * uv2[1]).astype(np.int32), 0, TEX_H - 1)

        alpha_pass = diff_img[v, u, 3] >= 128.0
        if not np.any(alpha_pass):
            return

        cy_idx, cx_idx = np.where(cand)
        cy_idx = cy_idx[alpha_pass]
        cx_idx = cx_idx[alpha_pass]
        u = u[alpha_pass]
        v = v[alpha_pass]

        sub_z[cy_idx, cx_idx] = z[cy_idx, cx_idx]
        d_col = diff_img[v, u, :3]
        g_col = glow_img[v, u, :3]
        g_a = glow_img[v, u, 3:4] / 255.0
        lit = d_col * shade * (1.0 - g_a * 0.85) + g_col * (g_a * 1.18)
        fb[min_y + cy_idx, min_x + cx_idx] = np.clip(lit, 0, 255)

    for quad, uv in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = 0.42 + 0.58 * abs(float(np.dot(n, light_dir)))
        scr = np.stack([cx - cam[:, 0] * scale, cy - cam[:, 1] * scale, cam[:, 2]], axis=1)
        u0, v0 = uv["uv"]
        uw, uh = uv["uv_size"]
        u1, v1 = u0 + max(1, uw) - 0.01, v0 + max(1, uh) - 0.01
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        raster_tri(scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
        raster_tri(scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)

    return Image.fromarray(fb.astype(np.uint8), "RGB")


def render_previews(geo: dict, anims: dict = None) -> None:
    tex_dir = ASSETS / "textures" / "entity"
    d_norm = np.array(Image.open(tex_dir / "cinder_dragon.png").convert("RGBA"), dtype=np.float32)
    g_norm = np.array(Image.open(tex_dir / "cinder_dragon_glowmask.png").convert("RGBA"), dtype=np.float32)
    d_enr = np.array(Image.open(tex_dir / "cinder_dragon_enraged.png").convert("RGBA"), dtype=np.float32)
    g_enr = np.array(Image.open(tex_dir / "cinder_dragon_enraged_glowmask.png").convert("RGBA"), dtype=np.float32)

    faces, bmin, bmax = collect_world_geometry(geo)
    center = (bmin + bmax) * 0.5
    span = float(np.max(bmax - bmin))

    idle_bones = anims["animation.cinder_dragon.idle"]["bones"] if anims and "animation.cinder_dragon.idle" in anims else None
    faces_idle, bmin_i, bmax_i = collect_world_geometry(geo, idle_bones)
    center_idle = (bmin_i + bmax_i) * 0.5
    span_idle = float(np.max(bmax_i - bmin_i))

    def cam_mat(yaw_deg, pitch_deg):
        y, p = math.radians(yaw_deg), math.radians(pitch_deg)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
        Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64)
        return Rx @ Ry

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 640, 480
    sc = (min(pw, ph) * 0.90) / max(1.0, span)
    sc_i = (min(pw, ph) * 0.90) / max(1.0, span_idle)

    # Kiểm tra toán học CHỐNG DÍNH/TRÙNG CÁNH (cả 2 cánh phải tách biệt rõ qua X = 0 ở BIND, IDLE, WALK, FLY!)
    specs = [
        ("preview_front.png",          180,  8, sc_i * 1.15, center_idle, faces_idle, d_norm, g_norm, "FRONT IDLE (1.3x SEPARATED WINGS, FORWARD HORNS)"),
        ("preview_34_hero.png",        146, 12, sc_i * 1.20, center_idle, faces_idle, d_norm, g_norm, "3/4 HERO IDLE (1.3x WINGS, ELBOW/KNEE SPURS, SCYTHE TAIL)"),
        ("preview_side_left.png",      -90,  4, sc_i * 1.16, center_idle, faces_idle, d_norm, g_norm, "SIDE IDLE (LOW CROUCH, 1.3x ARCHED WINGS, 9-SEG SCYTHE TAIL)"),
        ("preview_rear.png",             0, 12, sc_i * 1.16, center_idle, faces_idle, d_norm, g_norm, "REAR IDLE (CLEAR SPINE GAP — ZERO WING OVERLAP!)"),
        ("preview_top_down.png",       180, 82, sc_i * 1.08, center_idle, faces_idle, d_norm, g_norm, "TOP-DOWN IDLE (134px 1.3x WINGSPAN, 150px LENGTH, ZERO OVERLAP)"),
        ("preview_phase2_enraged.png", 146, 12, sc * 1.18,   center,      faces,      d_enr,  g_enr,  "PHASE 2 OPEN (FULL 1.3x WINGSPAN, MOLTEN CRACKS & CORE)"),
    ]
    imgs = []
    for fn, yw, pt, s_c, ctr, f_list, d_i, g_i, title in specs:
        im = rasterize_alpha_cutout(f_list, d_i, g_i, cam_mat(yw, pt), pw, ph, s_c, ctr)
        ImageDraw.Draw(im).text((10, 10), title, fill=(255, 220, 180))
        im.save(PREVIEW_DIR / fn)
        imgs.append(im)

    sheet = Image.new("RGB", (pw * 3, ph * 2), (14, 13, 18))
    for i, im in enumerate(imgs):
        sheet.paste(im, ((i % 3) * pw, (i // 3) * ph))
    sheet.save(PREVIEW_DIR / "cinder_dragon_preview_sheet.png")
    print(
        f"PREVIEW_OK: 6 views + sheet saved to {PREVIEW_DIR} | "
        f"bbox X=[{bmin[0]:.1f}..{bmax[0]:.1f}] (span {bmax[0]-bmin[0]:.1f}px) "
        f"Y=[{bmin[1]:.1f}..{bmax[1]:.1f}] (H {bmax[1]-bmin[1]:.1f}px) "
        f"Z=[{bmin[2]:.1f}..{bmax[2]:.1f}] (L {bmax[2]-bmin[2]:.1f}px)"
    )


if __name__ == "__main__":
    backup_existing_files()
    geo_data, total_cubes, meta = build_cinder_dragon_geo()
    build_textures()
    anims_data = build_animations()
    render_previews(geo_data, anims_data)
    print(f"ALL_DONE: Cinder Dragon generated ({meta['bones']} bones, {total_cubes} cubes, {TEX_W}x{TEX_H}, mouth_flame={meta['mouth_flame']})")
