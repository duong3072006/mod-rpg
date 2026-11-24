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


# =============================================================================
# 2. DỰNG HÌNH CINDER DRAGON (1.200–1.700 CUBES, 60% SILHOUETTE + 40% BIẾN TẤU)
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

    # Gốc — Tỉ lệ Rồng Cổ Đại (SCALE = 5.5f -> vai/đầu đứng 8.2–8.8 block, dài ~25.5 block, sải cánh ~31.2 block)
    add_bone("root", None, [0, 0, 0])
    add_bone("body_root", "root", [0, 17.0, 0.0])

    # -------------------------------------------------------------------------
    # A. THÂN VẠM VỠ OAI VỆ ĐỨNG CAO HƠN (`body_chest`, `body_mid`, `pelvis`)
    # -------------------------------------------------------------------------
    chest_cubes = []
    # Lồng ngực vạm vỡ nâng cao trên 4 chân trụ khỏe, bọc 4 vòng vảy giáp đá 3D
    chest_rings = [
        ((0.0, 18.2, -3.4),  6.6, 5.5, 4.0, -5.0),
        ((0.0, 18.8, -6.5),  7.2, 6.0, 4.2, -3.0),
        ((0.0, 19.2, -9.6),  6.6, 5.6, 3.8,  3.0),
        ((0.0, 19.4, -12.3), 5.3, 4.8, 3.4,  8.0),
    ]
    for idx, (ctr, rx, ry, dp, px) in enumerate(chest_rings):
        add_armor_ring(chest_cubes, ctr, rx, ry, dp,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="CHEST_FISSURE", pitch_x=px)
    # Ức đá hình khiên mũi tàu nhô trước ngực (nứt rực dung nham ở Phase 2!)
    chest_cubes.append(make_centered_cube([0.0, 15.2, -10.8], [6.0, 3.9, 4.8], "CHEST_FISSURE", rot=[20.0, 0, 0]))
    chest_cubes.append(make_centered_cube([0.0, 14.0, -7.2], [7.2, 2.8, 5.8], "BELLY_PLATE", rot=[6.0, 0, 0]))
    # 2 giáp cầu vai đá góc cạnh gồ lên ở khớp vai trước + 2 gai vai đá nhọn
    for side in (-1, 1):
        chest_cubes.append(make_centered_cube([side * 6.8, 21.0, -7.8], [3.8, 3.8, 5.8],
                                              "DORSAL_SHIELD", rot=[-8.0, side * 12.0, side * 18.0]))
        chest_cubes.append(make_centered_cube([side * 7.8, 18.6, -8.0], [3.0, 4.2, 5.0],
                                              "CHEST_FISSURE", rot=[6.0, side * 8.0, side * 10.0]))
        add_curved_horn(chest_cubes, [
            (side * 7.6, 22.4, -7.0),
            (side * 9.4, 24.6, -5.2),
            (side * 10.0, 26.2, -3.2),
        ], [1.05, 0.65, 0.24], "CLAW_DARK", steps_per_seg=2)
    # 3 tấm khiên vảy đá xếp tầng dọc sống lưng ngực
    for zpos, w, h in ((-4.2, 3.9, 3.6), (-7.2, 4.4, 4.2), (-10.3, 4.0, 3.9)):
        add_dorsal_shield(chest_cubes, (0.0, 24.4, zpos), w, h, 3.0, tilt_x=-20.0)
    add_bone("body_chest", "body_root", [0.0, 18.5, -6.8], chest_cubes)

    mid_cubes = []
    mid_rings = [
        ((0.0, 17.0, +2.6), 5.8, 4.7, 3.8, -6.0),
        ((0.0, 17.6, -0.6), 6.2, 5.1, 4.0, -4.0),
    ]
    for ctr, rx, ry, dp, px in mid_rings:
        add_armor_ring(mid_cubes, ctr, rx, ry, dp,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="CHEST_FISSURE", pitch_x=px)
    for zpos, w, h in ((+2.0, 3.6, 3.3), (-1.0, 3.8, 3.5)):
        add_dorsal_shield(mid_cubes, (0.0, 22.3, zpos), w, h, 2.9, tilt_x=-18.0)
    add_bone("body_mid", "body_root", [0.0, 17.2, +0.8], mid_cubes)

    pelvis_cubes = []
    pelvis_rings = [
        ((0.0, 16.2, +5.8), 6.1, 4.8, 4.2, -4.0),
        ((0.0, 15.6, +9.0), 5.3, 4.3, 4.0,  3.0),
    ]
    for ctr, rx, ry, dp, px in pelvis_rings:
        add_armor_ring(pelvis_cubes, ctr, rx, ry, dp,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="STONE_ARMOR", pitch_x=px)
    for side in (-1, 1):
        pelvis_cubes.append(make_centered_cube([side * 6.3, 17.5, +7.2], [3.4, 3.6, 5.0],
                                               "DORSAL_SHIELD", rot=[6.0, -side * 10.0, side * 16.0]))
        add_curved_horn(pelvis_cubes, [
            (side * 7.0, 19.0, +8.2),
            (side * 8.4, 20.6, +9.8),
            (side * 8.9, 21.6, +11.4),
        ], [0.90, 0.56, 0.22], "CLAW_DARK", steps_per_seg=2)
    for zpos, w, h in ((+5.2, 3.3, 3.1), (+8.4, 3.1, 2.9)):
        add_dorsal_shield(pelvis_cubes, (0.0, 20.8, zpos), w, h, 2.8, tilt_x=-16.0)
    add_bone("pelvis", "body_root", [0.0, 15.8, +7.4], pelvis_cubes)

    # -------------------------------------------------------------------------
    # B. CỔ 3 ĐỐT VƯƠN CAO OAI VỆ + KHIÊN GÁY + RÃNH MẠCH DUNG NHAM (`neck_1..3`)
    # -------------------------------------------------------------------------
    def build_neck_segment(z0, y0, z1, y1, rx, ry, shield_w, shield_h):
        c = []
        ym, zm = 0.5 * (y0 + y1), 0.5 * (z0 + z1)
        length = math.hypot(y1 - y0, z1 - z0)
        pitch = math.degrees(math.atan2(y1 - y0, -(z1 - z0)))
        add_armor_ring(c, (0.0, ym, zm), rx, ry, length * 1.24,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="THROAT_MAGMA", pitch_x=pitch)
        # Rãnh mạch dung nham nổi 2 bên cổ họng (sáng bừng khi nạp hơi khè lửa!)
        for side in (-1, 1):
            c.append(make_centered_cube([side * (rx * 0.92), ym - ry * 0.14, zm],
                                        [0.58, 1.25, length * 0.98],
                                        "THROAT_MAGMA", rot=[pitch, 0, side * 12.0]))
            # Vảy bảo vệ kẹp 2 bên rãnh cổ
            c.append(make_centered_cube([side * (rx * 0.86), ym + ry * 0.36, zm],
                                        [0.95, 1.20, length * 0.90],
                                        "DORSAL_SHIELD", rot=[pitch, 0, side * 18.0]))
        add_dorsal_shield(c, (0.0, ym + ry * 0.92, zm + 0.3), shield_w, shield_h, length * 0.70, tilt_x=-24.0 + pitch * 0.5)
        return c

    n1_cubes = build_neck_segment(-12.0, 19.6, -16.8, 20.6, 4.1, 3.7, 3.5, 3.4)
    add_bone("neck_1", "body_chest", [0.0, 19.6, -12.2], n1_cubes)

    n2_cubes = build_neck_segment(-16.4, 20.6, -21.2, 22.0, 3.6, 3.3, 3.2, 3.1)
    add_bone("neck_2", "neck_1", [0.0, 20.6, -16.6], n2_cubes)

    n3_cubes = build_neck_segment(-20.8, 22.0, -25.4, 23.6, 3.3, 3.0, 2.9, 2.9)
    add_bone("neck_3", "neck_2", [0.0, 22.0, -21.0], n3_cubes)

    # -------------------------------------------------------------------------
    # C. CỤM ĐẦU + HÀM + 2 BONE SỪNG ĐỐI XỨNG TUYỆT ĐỐI (`horn_l` / `horn_r`)
    # -------------------------------------------------------------------------
    head_cubes = []
    # Hộp sọ đá lớn, gờ mày nhô đậm che mắt cam-vàng rực lửa
    head_cubes.append(make_centered_cube([0.0, 24.4, -27.6], [8.0, 5.2, 6.8], "STONE_ARMOR", rot=[4.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, 26.8, -27.4], [6.6, 2.0, 6.2], "DORSAL_SHIELD", rot=[-5.0, 0, 0]))
    # Gò má giáp đá vát 2 bên sọ + gai gò má đối xứng
    for side in (-1, 1):
        head_cubes.append(make_centered_cube([side * 4.2, 23.4, -27.2], [2.0, 3.6, 5.8],
                                             "DORSAL_SHIELD", rot=[6.0, side * 14.0, side * 10.0]))
        add_curved_horn(head_cubes, [
            (side * 4.5, 22.8, -25.8),
            (side * 5.8, 22.3, -23.4),
            (side * 6.3, 22.0, -21.0),
        ], [0.78, 0.48, 0.18], "OBSIDIAN_HORN", steps_per_seg=2)

    # Sống mõm giáp đá dài thuôn mạnh + sống mũi sắc
    head_cubes.append(make_centered_cube([0.0, 24.0, -32.6], [6.0, 3.8, 6.0], "STONE_ARMOR", rot=[5.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, 23.4, -36.5], [4.5, 3.0, 3.8], "STONE_ARMOR", rot=[8.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, 25.8, -32.8], [3.5, 1.5, 6.6], "DORSAL_SHIELD", rot=[8.0, 0, 0]))
    # Viền môi đá trên ôm khung miệng tạo chiều sâu 3D cho khoang họng lửa (không còn mảng cam phẳng!)
    head_cubes.append(make_centered_cube([0.0, 22.0, -37.2], [4.4, 1.1, 1.6], "STONE_ARMOR", rot=[6.0, 0, 0]))
    for side in (-1, 1):
        head_cubes.append(make_centered_cube([side * 2.5, 22.1, -33.0], [1.0, 1.2, 7.6],
                                             "STONE_ARMOR", rot=[5.0, side * 4.0, 0]))
    # Khoang vòm miệng & họng lửa giật sâu vào trong tạo hiệu ứng lò lửa 3D có chiều sâu
    head_cubes.append(make_centered_cube([0.0, 22.3, -31.8], [4.4, 1.0, 6.2], "MAW_INFERNO", rot=[5.0, 0, 0]))
    head_cubes.append(make_centered_cube([0.0, 22.0, -28.0], [4.2, 2.4, 3.6], "MAW_INFERNO"))

    # Sừng mũi sắc (`nasal_horn`)
    add_curved_horn(head_cubes, [
        (0.0, 24.9, -36.0),
        (0.0, 27.3, -36.8),
        (0.0, 29.4, -35.7),
    ], [0.95, 0.56, 0.18], "OBSIDIAN_HORN", steps_per_seg=2)

    # 2 ĐÔI MẮT CAM-VÀNG RỰC LỬA (chỉ ánh xạ EYE_FIRE đúng mặt trước-ngoài để mỗi mắt ĐÚNG 1 con ngươi dọc sắc nét!)
    eye_dark_sides = {"up": "CLAW_DARK", "down": "CLAW_DARK", "south": "CLAW_DARK", "east": "CLAW_DARK", "west": "CLAW_DARK"}
    for side in (-1, 1):
        # Gờ mày đá nhô đậm phía trên mắt
        head_cubes.append(make_centered_cube([side * 3.7, 26.2, -30.2], [2.3, 1.4, 4.2],
                                             "DORSAL_SHIELD", rot=[12.0, side * 18.0, side * 18.0]))
        # Hốc mắt sâu tối
        head_cubes.append(make_centered_cube([side * 3.55, 24.7, -30.4], [1.8, 1.9, 3.0],
                                             "CLAW_DARK", rot=[8.0, side * 32.0, 0.0]))
        # Nhãn cầu cam-vàng fullbright: mặt `north` hướng chếch 36° trước-ngoài, 5 mặt còn lại tối hốc mắt -> 1 con ngươi cực sắc!
        head_cubes.append(make_centered_cube([side * 3.85, 24.65, -31.05], [2.2, 1.65, 0.85],
                                             "EYE_FIRE", rot=[8.0, side * 36.0, 0.0], face_mats=eye_dark_sides))
        # Sống mũi phụ + tấm giáp má xếp tầng
        head_cubes.append(make_centered_cube([side * 1.8, 25.0, -35.4], [1.3, 1.2, 2.3],
                                             "DORSAL_SHIELD", rot=[10.0, side * 14.0, 0]))
        head_cubes.append(make_centered_cube([side * 3.2, 23.2, -31.8], [1.4, 2.0, 4.2],
                                             "STONE_ARMOR", rot=[6.0, side * 12.0, side * 8.0]))
        head_cubes.append(make_centered_cube([side * 4.1, 25.0, -26.2], [1.6, 2.0, 3.6],
                                             "STONE_ARMOR", rot=[-5.0, side * 14.0, side * 12.0]))

    # 2 RĂNG NANH NGÀ LỚN LỘ NGOÀI KHÓE HÀM TRÊN + hàng răng sắc hàm trên
    for side in (-1, 1):
        add_curved_horn(head_cubes, [
            (side * 2.75, 22.3, -34.6),
            (side * 2.90, 20.7, -35.0),
            (side * 2.82, 19.4, -34.8),
        ], [0.64, 0.40, 0.15], "IVORY_FANG", steps_per_seg=2)
        for tz, th in ((-37.0, 1.20), (-32.4, 1.12), (-30.4, 1.00)):
            head_cubes.append(make_centered_cube([side * 2.08, 21.5, tz], [0.56, th, 0.60],
                                                 "IVORY_FANG", rot=[8.0, 0, side * 6.0]))

    add_bone("head", "neck_3", [0.0, 24.0, -25.6], head_cubes)

    # 2 BONE SỪNG VƯƠNG MIỆN ĐỘC LẬP (`horn_l` bên +X và `horn_r` đối xứng gương 100% qua `mirror_x_cube`!)
    horn_l_cubes = []
    # Sừng vương miện chính trái: vươn gọn lên-ra sau-xòe chữ V oai vệ
    add_curved_horn(horn_l_cubes, [
        (2.8, 26.8, -26.2),
        (4.8, 30.2, -22.2),
        (6.4, 33.4, -17.6),
        (7.1, 36.0, -13.2),
    ], [1.45, 1.08, 0.70, 0.24], "OBSIDIAN_HORN", steps_per_seg=5)
    # Sừng vương miện phụ ngoài thái dương trái
    add_curved_horn(horn_l_cubes, [
        (4.0, 25.4, -25.8),
        (6.8, 27.4, -22.4),
        (9.0, 29.3, -18.4),
        (9.8, 30.4, -15.2),
    ], [1.06, 0.76, 0.46, 0.18], "OBSIDIAN_HORN", steps_per_seg=5)
    # Mào sừng giữa sọ trái
    horn_l_cubes.append(make_centered_cube([1.15, 27.9, -25.8], [1.0, 2.2, 2.0],
                                           "OBSIDIAN_HORN", rot=[-28.0, 10.0, 12.0]))
    add_bone("horn_l", "head", [2.8, 26.4, -26.0], horn_l_cubes)
    add_bone("horn_r", "head", [-2.8, 26.4, -26.0], [mirror_x_cube(c) for c in horn_l_cubes])

    # Hàm dưới (`jaw`): tấm giáp cằm gắn KHÍT liền khối đáy xương hàm + viền môi đá + vòm họng lửa giật sâu có chiều sâu 3D
    jaw_cubes = []
    jaw_cubes.append(make_centered_cube([0.0, 20.6, -30.5], [5.8, 2.1, 9.2], "STONE_ARMOR", rot=[4.0, 0, 0]))
    jaw_cubes.append(make_centered_cube([0.0, 19.9, -34.8], [4.5, 1.8, 4.4], "DORSAL_SHIELD", rot=[6.0, 0, 0]))
    # Tấm giáp cằm vát thuôn gắn KHÍT vào đáy xương hàm dưới (không treo lơ lửng/tách rời!)
    jaw_cubes.append(make_centered_cube([0.0, 19.3, -34.6], [3.6, 1.1, 4.2], "OBSIDIAN_HORN", rot=[7.0, 0, 0]))
    # Gờ môi đá trước hàm dưới & 2 thành bên ôm khoang miệng để che cạnh mảng lửa từ góc chính diện
    jaw_cubes.append(make_centered_cube([0.0, 21.0, -36.5], [4.2, 1.0, 1.3], "DORSAL_SHIELD", rot=[5.0, 0, 0]))
    # Lòng hàm & họng lửa 2 tầng (sàn lưỡi đỏ sẫm phía trước + lõi họng rực sâu bên trong)
    jaw_cubes.append(make_centered_cube([0.0, 21.3, -31.8], [3.6, 0.7, 6.0], "MAW_INFERNO", rot=[3.0, 0, 0]))
    jaw_cubes.append(make_centered_cube([0.0, 21.6, -28.4], [3.8, 1.4, 3.0], "EMBER_CORE", rot=[2.0, 0, 0]))
    for side in (-1, 1):
        jaw_cubes.append(make_centered_cube([side * 2.35, 20.1, -28.6], [1.3, 1.9, 5.4],
                                            "STONE_ARMOR", rot=[5.0, side * 7.0, 0]))
        jaw_cubes.append(make_centered_cube([side * 2.05, 21.2, -33.2], [0.85, 1.0, 5.8],
                                            "STONE_ARMOR", rot=[4.0, side * 5.0, 0]))
        for tz, th in ((-35.8, 1.10), (-33.4, 1.00), (-31.2, 0.92)):
            jaw_cubes.append(make_centered_cube([side * 1.72, 21.8, tz], [0.48, th, 0.52],
                                                "IVORY_FANG", rot=[-6.0, 0, -side * 5.0]))
    add_bone("jaw", "head", [0.0, 21.2, -26.2], jaw_cubes)

    # Locator `mouth_flame` đặt ngay giữa khoang họng lửa (khớp 100% với CinderDragonBossEntity.getMouthFlameWorldPos!)
    add_bone("mouth_flame", "head", [0.0, 21.8, -32.2],
             locators={"mouth_flame": [0.0, 21.8, -32.2]})

    # -------------------------------------------------------------------------
    # D. 4 CHÂN TRỤ RỒNG CỔ ĐẠI VẠM VỠ (Đứng cao oai vệ — người chơi 1.8b ngang cổ chân/đầu gối!)
    # -------------------------------------------------------------------------
    def build_front_leg(side: int):
        sx = float(side)
        suffix = "l" if side > 0 else "r"
        p_sh = (sx * 7.2, 17.6, -7.8)
        p_el = (sx * 9.2, 10.0, -5.4)
        p_wr = (sx * 8.6, 3.0, -9.0)

        upper_c = []
        add_segment_bar(upper_c, p_sh, p_el, 4.0, 4.2, 3.3, 3.4, steps=6, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="CHEST_FISSURE")
        upper_c.append(make_centered_cube(p_el, [3.7, 3.5, 3.7], "DORSAL_SHIELD"))
        add_curved_horn(upper_c, [
            (sx * 9.5, 10.4, -4.2),
            (sx * 10.3, 11.2, -2.0),
            (sx * 10.5, 11.9, -0.2),
        ], [0.82, 0.50, 0.20], "CLAW_DARK", steps_per_seg=3)

        lower_c = []
        add_segment_bar(lower_c, p_el, p_wr, 3.2, 3.3, 2.7, 2.8, steps=6, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        for t in (0.38, 0.70):
            gp = np.array(p_el) * (1 - t) + np.array(p_wr) * t
            lower_c.append(make_centered_cube([gp[0] + sx * 1.35, gp[1] + 0.35, gp[2] + 0.65],
                                              [1.05, 1.30, 1.75], "CLAW_DARK", rot=[-24.0, sx * 16.0, sx * 22.0]))

        foot_c = []
        foot_c.append(make_centered_cube([sx * 8.6, 1.65, -9.7], [4.2, 2.5, 4.1], "STONE_ARMOR"))
        toe_specs = [
            (-26.0 * sx, 4.0, -1.0),
            (  0.0,      4.5, -1.0),
            (+26.0 * sx, 4.0, -1.0),
            (+150.0 * sx, 2.9, -1.0),
        ]
        for ang_deg, tlen, _ in toe_specs:
            rad = math.radians(ang_deg)
            dx = math.sin(rad)
            dz = -math.cos(rad)
            p_base = (sx * 8.6 + dx * 1.2, 1.15, -9.7 + dz * 1.2)
            p_mid = (sx * 8.6 + dx * (1.2 + tlen * 0.58), 0.92, -9.7 + dz * (1.2 + tlen * 0.58))
            p_tip = (sx * 8.6 + dx * (1.2 + tlen), 0.28, -9.7 + dz * (1.2 + tlen))
            add_segment_bar(foot_c, p_base, p_mid, 1.30, 1.25, 1.05, 1.05, steps=2, mat="STONE_ARMOR")
            add_curved_horn(foot_c, [p_mid, (p_tip[0], 0.68, p_tip[2]), p_tip], [0.60, 0.40, 0.16], "IVORY_FANG", steps_per_seg=2)

        add_bone(f"leg_f{suffix}", "body_chest", p_sh, upper_c)
        add_bone(f"leg_f{suffix}_lower", f"leg_f{suffix}", p_el, lower_c)
        add_bone(f"leg_f{suffix}_foot", f"leg_f{suffix}_lower", p_wr, foot_c)

    def build_back_leg(side: int):
        sx = float(side)
        suffix = "l" if side > 0 else "r"
        p_hip = (sx * 6.8, 16.0, +7.2)
        p_kn = (sx * 8.8, 9.8, +4.4)
        p_hk = (sx * 8.5, 5.4, +9.2)
        p_ft = (sx * 8.3, 2.4, +6.8)

        upper_c = []
        add_segment_bar(upper_c, p_hip, p_kn, 4.5, 4.8, 3.6, 3.7, steps=6, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="CHEST_FISSURE")
        upper_c.append(make_centered_cube(p_kn, [3.7, 3.6, 3.8], "DORSAL_SHIELD"))
        add_curved_horn(upper_c, [
            (sx * 9.0, 10.2, +3.2),
            (sx * 9.6, 10.8, +1.4),
        ], [0.82, 0.22], "CLAW_DARK", steps_per_seg=3)

        lower_c = []
        add_segment_bar(lower_c, p_kn, p_hk, 3.4, 3.4, 2.8, 2.9, steps=5, mat="STONE_ARMOR",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        add_segment_bar(lower_c, p_hk, p_ft, 2.7, 2.7, 2.5, 2.5, steps=4, mat="STONE_ARMOR")
        add_curved_horn(lower_c, [
            (sx * 8.6, 5.6, +10.0),
            (sx * 9.0, 6.4, +12.2),
            (sx * 9.0, 7.0, +13.6),
        ], [0.78, 0.48, 0.20], "CLAW_DARK", steps_per_seg=2)

        foot_c = []
        foot_c.append(make_centered_cube([sx * 8.3, 1.55, +6.1], [4.2, 2.3, 4.1], "STONE_ARMOR"))
        toe_specs = [
            (-24.0 * sx, 3.9),
            (  0.0,      4.4),
            (+24.0 * sx, 3.9),
            (+155.0 * sx, 2.8),
        ]
        for ang_deg, tlen in toe_specs:
            rad = math.radians(ang_deg)
            dx = math.sin(rad)
            dz = -math.cos(rad)
            p_base = (sx * 8.3 + dx * 1.2, 1.1, +6.1 + dz * 1.2)
            p_mid = (sx * 8.3 + dx * (1.2 + tlen * 0.58), 0.88, +6.1 + dz * (1.2 + tlen * 0.58))
            p_tip = (sx * 8.3 + dx * (1.2 + tlen), 0.28, +6.1 + dz * (1.2 + tlen))
            add_segment_bar(foot_c, p_base, p_mid, 1.28, 1.24, 1.02, 1.02, steps=2, mat="STONE_ARMOR")
            add_curved_horn(foot_c, [p_mid, (p_tip[0], 0.65, p_tip[2]), p_tip], [0.58, 0.38, 0.16], "IVORY_FANG", steps_per_seg=2)

        add_bone(f"leg_b{suffix}", "pelvis", p_hip, upper_c)
        add_bone(f"leg_b{suffix}_lower", f"leg_b{suffix}", p_kn, lower_c)
        add_bone(f"leg_b{suffix}_foot", f"leg_b{suffix}_lower", p_ft, foot_c)

    for s in (1, -1):
        build_front_leg(s)
        build_back_leg(s)

    # -------------------------------------------------------------------------
    # E. ĐÔI CÁNH ĐỎ SẪM VỒNG CAO OAI VỆ (sải ~90.5 px = 31.1 block tại SCALE 5.5f, màng đỏ găm kín 100% nan xương!)
    # -------------------------------------------------------------------------
    def build_wing_left():
        p_sh  = np.array([ 6.2, 22.0, -7.2])   # Khớp vai cánh nâng cao
        p_el  = np.array([16.4, 27.6, -3.4])   # Khuỷu cánh vồng cao
        p_wr  = np.array([28.8, 33.4, -8.8])   # Cổ cánh vươn đỉnh cao + móng vuốt cong sắc!

        # 5 nan xương cánh xòe quạt rộng — nâng cao mép sau để không bao giờ quét sát mặt đất
        p_tip = np.array([44.2, 30.8, -4.4])   # Nan đầu mép trước (sải ~90.6 px = 31.1 block!)
        p_f1  = np.array([39.6, 24.4, +4.4])   # Nan 1
        p_f2  = np.array([31.2, 19.6, +11.4])  # Nan 2
        p_f3  = np.array([21.8, 17.6, +14.2])  # Nan 3
        p_f4  = np.array([13.2, 18.2, +11.8])  # Nan 4 nối kín về hông/sườn!

        p_flank_front = np.array([5.6, 19.8, -2.8])
        p_flank_rear  = np.array([5.2, 16.8, +8.2])

        arm_c = []
        arm_c.append(make_centered_cube(p_sh, [3.8, 3.6, 4.0], "WING_BONE"))
        add_segment_bar(arm_c, p_sh, p_el, 2.9, 3.1, 2.5, 2.7, steps=5, mat="WING_BONE",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        for t in (0.28, 0.56, 0.82):
            gp = p_sh * (1 - t) + p_el * t
            arm_c.append(make_centered_cube(gp + np.array([0, 1.7, -0.35]), [0.95, 1.65, 1.25],
                                            "CLAW_DARK", rot=[-18.0, 0, -14.0]))
        add_quad_membrane(arm_c, p_sh, p_el, p_f4, p_flank_rear, rows=6, cols=5, scallop_depth=0.13)
        add_quad_membrane(arm_c, p_sh, p_flank_front, p_flank_rear, p_sh + np.array([-0.5, -2.2, 4.0]),
                          rows=3, cols=3, scallop_depth=0.04)

        mid_c = []
        mid_c.append(make_centered_cube(p_el, [3.3, 3.3, 3.5], "WING_BONE"))
        add_segment_bar(mid_c, p_el, p_wr, 2.5, 2.7, 2.2, 2.4, steps=7, mat="WING_BONE",
                        armor_ridge=True, ridge_mat="DORSAL_SHIELD")
        for t in (0.22, 0.46, 0.70, 0.90):
            gp = p_el * (1 - t) + p_wr * t
            mid_c.append(make_centered_cube(gp + np.array([0.15, 1.60, -0.40]), [0.88, 1.60, 1.20],
                                            "CLAW_DARK", rot=[-14.0, 12.0, -12.0]))
        mid_c.append(make_centered_cube(p_wr, [3.6, 3.4, 3.8], "WING_BONE"))
        add_curved_horn(mid_c, [
            p_wr + np.array([ 0.2, 0.85, -1.0]),
            p_wr + np.array([ 1.0, 2.2,  -4.2]),
            p_wr + np.array([ 1.5, 1.5,  -7.0]),
            p_wr + np.array([ 1.1, -0.2, -8.2]),
        ], [1.12, 0.80, 0.48, 0.18], "CLAW_DARK", steps_per_seg=4)
        add_curved_horn(mid_c, [
            p_wr + np.array([-0.8, 0.5, -0.8]),
            p_wr + np.array([-1.3, 1.5, -3.4]),
            p_wr + np.array([-1.1, 0.6, -5.0]),
        ], [0.72, 0.44, 0.16], "CLAW_DARK", steps_per_seg=3)

        add_membrane_panel(mid_c, p_el, p_wr, p_f4, rows=7, cols=6, scallop_depth=0.13)

        tip_c = []
        add_segment_bar(tip_c, p_wr, p_tip, 1.9, 2.0, 0.75, 0.85, steps=8, mat="WING_BONE",
                        armor_ridge=True, ridge_mat="CLAW_DARK")
        add_curved_horn(tip_c, [p_tip, p_tip + np.array([2.6, -0.3, 0.8])], [0.64, 0.16], "CLAW_DARK", steps_per_seg=2)
        add_membrane_panel(tip_c, p_wr, p_tip, p_f1, rows=8, cols=6, scallop_depth=0.14)

        f1_c = []
        add_segment_bar(f1_c, p_wr, p_f1, 1.45, 1.45, 0.60, 0.60, steps=7, mat="WING_BONE")
        add_membrane_panel(f1_c, p_wr, p_f1, p_f2, rows=8, cols=6, scallop_depth=0.15)

        f2_c = []
        add_segment_bar(f2_c, p_wr, p_f2, 1.32, 1.32, 0.56, 0.56, steps=7, mat="WING_BONE")
        add_membrane_panel(f2_c, p_wr, p_f2, p_f3, rows=7, cols=6, scallop_depth=0.15)

        f3_c = []
        add_segment_bar(f3_c, p_wr, p_f3, 1.24, 1.24, 0.52, 0.52, steps=6, mat="WING_BONE")
        add_membrane_panel(f3_c, p_wr, p_f3, p_f4, rows=7, cols=6, scallop_depth=0.14)

        f4_c = []
        add_segment_bar(f4_c, p_wr, p_f4, 1.20, 1.20, 0.52, 0.52, steps=6, mat="WING_BONE")

        return {
            "sh": p_sh, "el": p_el, "wr": p_wr,
            "arm": arm_c, "mid": mid_c, "tip": tip_c,
            "f1": f1_c, "f2": f2_c, "f3": f3_c, "f4": f4_c,
        }

    wl = build_wing_left()
    add_bone("wing_l", "body_chest", wl["sh"])
    add_bone("wing_arm_l", "wing_l", wl["sh"], wl["arm"])
    add_bone("wing_mid_l", "wing_arm_l", wl["el"], wl["mid"])
    add_bone("wing_tip_l", "wing_mid_l", wl["wr"], wl["tip"])
    add_bone("wing_finger_1_l", "wing_mid_l", wl["wr"], wl["f1"])
    add_bone("wing_finger_2_l", "wing_mid_l", wl["wr"], wl["f2"])
    add_bone("wing_finger_3_l", "wing_mid_l", wl["wr"], wl["f3"])
    add_bone("wing_finger_4_l", "wing_mid_l", wl["wr"], wl["f4"])

    def mp(pt):
        return [-float(pt[0]), float(pt[1]), float(pt[2])]

    add_bone("wing_r", "body_chest", mp(wl["sh"]))
    add_bone("wing_arm_r", "wing_r", mp(wl["sh"]), [mirror_x_cube(c) for c in wl["arm"]])
    add_bone("wing_mid_r", "wing_arm_r", mp(wl["el"]), [mirror_x_cube(c) for c in wl["mid"]])
    add_bone("wing_tip_r", "wing_mid_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["tip"]])
    add_bone("wing_finger_1_r", "wing_mid_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f1"]])
    add_bone("wing_finger_2_r", "wing_mid_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f2"]])
    add_bone("wing_finger_3_r", "wing_mid_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f3"]])
    add_bone("wing_finger_4_r", "wing_mid_r", mp(wl["wr"]), [mirror_x_cube(c) for c in wl["f4"]])

    # -------------------------------------------------------------------------
    # F. 7 ĐỐT ĐUÔI GIÁP LỒNG KHÍT GIA CỐ KHỚP TRƯỚC CHÙY + CHÙY LƯỠI LIỀM ĐÔI ÔM LÕI THAN HỒNG
    # -------------------------------------------------------------------------
    tail_nodes = [
        (0.0, 15.2, +10.2, 4.3, 3.7, 2.8, 2.8),
        (0.0, 14.3, +13.9, 3.8, 3.3, 2.5, 2.6),
        (0.0, 13.8, +17.5, 3.4, 2.9, 2.3, 2.4),
        (0.0, 13.9, +21.0, 3.0, 2.6, 2.1, 2.2),
        (0.0, 14.9, +24.4, 2.75, 2.40, 1.9, 2.0),  # Gia cố dày chắc tail_5..7 nối liền chùy lớn!
        (0.0, 16.5, +27.5, 2.55, 2.25, 1.75, 1.85),
        (0.0, 18.7, +30.2, 2.35, 2.10, 1.60, 1.70),
    ]
    prev_tail = "pelvis"
    for i, (tx, ty, tz, rx, ry, sw, sh) in enumerate(tail_nodes):
        if i < len(tail_nodes) - 1:
            nx, ny, nz = tail_nodes[i + 1][:3]
        else:
            nx, ny, nz = 0.0, 20.6, +32.4
        seg_c = []
        ym, zm = 0.5 * (ty + ny), 0.5 * (tz + nz)
        seg_len = math.hypot(ny - ty, nz - tz)
        pitch = -math.degrees(math.atan2(ny - ty, nz - tz))
        # Phủ chồng 45% (đặc biệt dày chắc ở tail_5..7) -> tuyệt đối không bị thắt cổ chai hay hở khớp!
        overlap_f = 1.46 if i >= 4 else 1.38
        add_armor_ring(seg_c, (0.0, ym, zm), rx, ry, seg_len * overlap_f,
                       mat_top="STONE_ARMOR", mat_belly="BELLY_PLATE",
                       mat_side="CHEST_FISSURE" if i < 5 else "STONE_ARMOR", pitch_x=pitch)
        add_dorsal_shield(seg_c, (0.0, ym + ry * 0.90, zm), sw, sh, seg_len * 0.78, tilt_x=-14.0 + pitch * 0.5)
        if i in (1, 3, 5):
            for side in (-1, 1):
                seg_c.append(make_centered_cube([side * (rx * 0.95), ym, zm + 0.3],
                                                [1.10, 1.00, 1.85], "CLAW_DARK", rot=[pitch, side * 24.0, 0]))

        # CHÓP ĐUÔI `tail_7`: VÒNG NGÀM GIÁP DÀY + CHÙY LƯỠI LIỀM ĐÔI ĐÁ OBSIDIAN ÔM LÕI THAN HỒNG PHÁT SÁNG
        if i == 6:
            core_c = np.array([0.0, 21.0, +33.0])
            # Cổ ngàm giáp kép nối chặt từ tail_6/tail_7 vào khối chùy (bỏ hoàn toàn đoạn thắt mảnh!)
            seg_c.append(make_centered_cube([0.0, 19.3, +30.6], [4.2, 3.6, 3.6], "DORSAL_SHIELD", rot=[-24.0, 0, 0]))
            seg_c.append(make_centered_cube([0.0, 20.2, +31.8], [3.8, 3.2, 3.4], "OBSIDIAN_HORN", rot=[-26.0, 0, 0]))
            # Lõi than hồng đa diện phát sáng fullbright ở trung tâm
            seg_c.append(make_centered_cube(core_c, [2.5, 2.5, 2.8], "EMBER_CORE", rot=[20.0, 35.0, 15.0]))
            seg_c.append(make_centered_cube(core_c, [2.0, 3.0, 2.0], "EMBER_CORE", rot=[-15.0, 10.0, 40.0]))
            # 2 lưỡi liềm obsidian cong đối xứng hai bên ôm trọn lõi than hồng!
            for side in (-1, 1):
                add_curved_horn(seg_c, [
                    (side * 1.1, 19.6, +31.4),
                    (side * 3.6, 20.4, +32.8),
                    (side * 4.5, 21.8, +35.2),
                    (side * 3.1, 23.0, +37.6),
                    (side * 1.0, 23.6, +38.8),
                ], [0.98, 1.06, 0.90, 0.62, 0.22], "OBSIDIAN_HORN", steps_per_seg=4)
                add_curved_horn(seg_c, [
                    (side * 3.9, 20.6, +33.1),
                    (side * 5.2, 22.1, +35.5),
                    (side * 3.7, 23.3, +37.9),
                ], [0.46, 0.38, 0.14], "CLAW_DARK", steps_per_seg=3)
            add_curved_horn(seg_c, [
                (0.0, 21.8, +34.4),
                (0.0, 23.4, +36.8),
            ], [0.72, 0.20], "OBSIDIAN_HORN", steps_per_seg=2)

        bname = f"tail_{i + 1}"
        add_bone(bname, prev_tail, [tx, ty, tz], seg_c)
        prev_tail = bname

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    assert 1200 <= total_cubes <= 1700, f"Cube count {total_cubes} out of target range 1200..1700!"

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.cinder_dragon",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 20.0,
                    "visible_bounds_height": 12.0,
                    "visible_bounds_offset": [0.0, 4.0, 0.0],
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
    return geo, total_cubes, {"bones": len(bones), "mouth_flame": [0.0, 21.8, -32.2]}


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

    c_dark = np.array([94, 22, 20], dtype=np.float32)     # #5E1614
    c_mid  = np.array([158, 41, 34], dtype=np.float32)    # #9E2922
    c_hi   = np.array([186, 59, 46], dtype=np.float32)    # #BA3B2E
    if enraged:
        c_dark = np.array([115, 24, 18], dtype=np.float32)
        c_mid  = np.array([194, 52, 34], dtype=np.float32)
        c_hi   = np.array([232, 86, 48], dtype=np.float32)

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

    # Tư thế cánh nghỉ vồng cao oai vệ (GeckoLib: wing_l rotZ < 0 nâng lên +Y, rotX < 0 nhấc mép sau lên, rotY < 0 quét nhẹ về sau)
    # 1. IDLE (3.20s = 64t): đứng vươn đầu oai vệ, cánh vồng cao phía trên vai/lưng (mép sau cách xa mặt đất!), thở sâu nặng
    anims["animation.cinder_dragon.idle"] = make_anim(3.20, True, {
        "body_root": {"position": track({0.0: [0, 0, 0], 1.6: [0, 0.55, 0], 3.2: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [1.0, 0, 0], 1.6: [-2.0, 0, 0], 3.2: [1.0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [-2.0, -1.5, 0], 1.6: [-5.0, 1.5, 0], 3.2: [-2.0, -1.5, 0]})},
        "neck_2": {"rotation": track({0.0: [-1.0, -1.0, 0], 1.6: [-3.0, 1.0, 0], 3.2: [-1.0, -1.0, 0]})},
        "head": {"rotation": track({0.0: [2.5, -1.5, 0], 1.6: [0.5, 1.5, 0], 3.2: [2.5, -1.5, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 1.6: [10, 0, 0], 3.2: [4, 0, 0]})},
        # Cánh vồng cao oai vệ trên vai/lưng (trễ pha 0.2s so với nhịp thở ngực), tuyệt đối không sệ chạm đất!
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.2: [-11, -13, -16], 1.8: [-7, -9, -10], 3.2: [-10, -12, -14]})},
        "wing_mid_l": {"rotation": track({0.0: [-4, -8, 12], 0.4: [-5, -9, 14], 2.0: [-2, -6, 9], 3.2: [-4, -8, 12]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.2: [-11, 13, 16], 1.8: [-7, 9, 10], 3.2: [-10, 12, 14]})},
        "wing_mid_r": {"rotation": track({0.0: [-4, 8, -12], 0.4: [-5, 9, -14], 2.0: [-2, 6, -9], 3.2: [-4, 8, -12]})},
        "tail_1": {"rotation": track({0.0: [0, -4, 0], 1.6: [0, 4, 0], 3.2: [0, -4, 0]})},
        "tail_3": {"rotation": track({0.0: [1.5, -6, 0], 1.6: [-1.5, 6, 0], 3.2: [1.5, -6, 0]})},
        "tail_5": {"rotation": track({0.0: [2.5, -8, 0], 1.6: [-2.0, 8, 0], 3.2: [2.5, -8, 0]})},
        "tail_7": {"rotation": track({0.0: [3.0, -9, 0], 1.6: [-3.0, 9, 0], 3.2: [3.0, -9, 0]})},
    })

    # 2. WALK (1.60s = 32t): bước chéo 4 chân đầm nặng, đôi cánh vồng cao giữ thăng bằng (mép sau cao rõ khỏi mặt đất!)
    anims["animation.cinder_dragon.walk"] = make_anim(1.60, True, {
        "body_root": {
            "position": track({0.0: [0, 0.2, 0], 0.4: [0, -0.65, 0], 0.8: [0, 0.2, 0], 1.2: [0, -0.65, 0], 1.6: [0, 0.2, 0]}),
            "rotation": track({0.0: [1.5, -3.5, -2.5], 0.8: [1.5, 3.5, 2.5], 1.6: [1.5, -3.5, -2.5]}),
        },
        "body_chest": {"rotation": track({0.0: [2, 4, 2.5], 0.8: [2, -4, -2.5], 1.6: [2, 4, 2.5]})},
        "neck_1": {"rotation": track({0.0: [2, -4, 0], 0.8: [2, 4, 0], 1.6: [2, -4, 0]})},
        "head": {"rotation": track({0.0: [-2, 3, 0], 0.8: [-2, -3, 0], 1.6: [-2, 3, 0]})},
        "leg_fl": {"rotation": track({0.0: [-22, 0, 0], 0.4: [-5, 0, 0], 0.8: [20, 0, 0], 1.2: [5, 0, 0], 1.6: [-22, 0, 0]})},
        "leg_fl_lower": {"rotation": track({0.0: [15, 0, 0], 0.4: [4, 0, 0], 0.8: [-9, 0, 0], 1.2: [20, 0, 0], 1.6: [15, 0, 0]})},
        "leg_br": {"rotation": track({0.0: [-20, 0, 0], 0.4: [-4, 0, 0], 0.8: [18, 0, 0], 1.2: [5, 0, 0], 1.6: [-20, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [20, 0, 0], 0.4: [5, 0, 0], 0.8: [-22, 0, 0], 1.2: [-5, 0, 0], 1.6: [20, 0, 0]})},
        "leg_fr_lower": {"rotation": track({0.0: [-9, 0, 0], 0.4: [20, 0, 0], 0.8: [15, 0, 0], 1.2: [4, 0, 0], 1.6: [-9, 0, 0]})},
        "leg_bl": {"rotation": track({0.0: [18, 0, 0], 0.4: [5, 0, 0], 0.8: [-20, 0, 0], 1.2: [-4, 0, 0], 1.6: [18, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-12, -16, -18], 0.4: [-10, -18, -14], 0.8: [-12, -14, -18], 1.2: [-10, -16, -14], 1.6: [-12, -16, -18]})},
        "wing_mid_l": {"rotation": track({0.0: [-5, -10, 16], 0.8: [-4, -8, 13], 1.6: [-5, -10, 16]})},
        "wing_r": {"rotation": track({0.0: [-12, 16, 18], 0.4: [-10, 18, 14], 0.8: [-12, 14, 18], 1.2: [-10, 16, 14], 1.6: [-12, 16, 18]})},
        "wing_mid_r": {"rotation": track({0.0: [-5, 10, -16], 0.8: [-4, 8, -13], 1.6: [-5, 10, -16]})},
        "tail_1": {"rotation": track({0.0: [0, 9, 0], 0.8: [0, -9, 0], 1.6: [0, 9, 0]})},
        "tail_3": {"rotation": track({0.0: [0, 11, 0], 0.8: [0, -11, 0], 1.6: [0, 11, 0]})},
        "tail_5": {"rotation": track({0.0: [0, 13, 0], 0.8: [0, -13, 0], 1.6: [0, 13, 0]})},
    })

    # 3. FLY (1.20s = 24t): đập cánh có lực nâng rõ (vút lên rotZ âm -> đập xuống rotZ dương dứt khoát), 4 chân thu gọn
    anims["animation.cinder_dragon.fly"] = make_anim(1.20, True, {
        "body_root": {
            "position": track({0.0: [0, 2.0, 0], 0.55: [0, -1.6, 0], 0.90: [0, 3.6, 0], 1.20: [0, 2.0, 0]}),
            "rotation": track({0.0: [-6, 0, 0], 0.55: [-11, 0, 0], 0.90: [-2, 0, 0], 1.20: [-6, 0, 0]}),
        },
        "wing_l": {"rotation": track({0.0: [-6, -4, -18], 0.55: [-12, -10, -44], 0.90: [12, 6, 30], 1.20: [-6, -4, -18]})},
        "wing_mid_l": {"rotation": track({0.0: [0, -4, 10], 0.55: [0, -14, 28], 0.90: [0, 8, -16], 1.20: [0, -4, 10]})},
        "wing_tip_l": {"rotation": track({0.0: [0, 0, 12], 0.60: [0, -8, 30], 0.95: [0, 4, -12], 1.20: [0, 0, 12]})},
        "wing_r": {"rotation": track({0.0: [-6, 4, 18], 0.55: [-12, 10, 44], 0.90: [12, -6, -30], 1.20: [-6, 4, 18]})},
        "wing_mid_r": {"rotation": track({0.0: [0, 4, -10], 0.55: [0, 14, -28], 0.90: [0, -8, 16], 1.20: [0, 4, -10]})},
        "wing_tip_r": {"rotation": track({0.0: [0, 0, -12], 0.60: [0, 8, -30], 0.95: [0, -4, 12], 1.20: [0, 0, -12]})},
        "leg_fl": {"rotation": track({0.0: [48, 0, 0], 0.60: [54, 0, 0], 1.20: [48, 0, 0]})},
        "leg_fl_lower": {"rotation": track({0.0: [34, 0, 0], 1.20: [34, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [48, 0, 0], 0.60: [54, 0, 0], 1.20: [48, 0, 0]})},
        "leg_fr_lower": {"rotation": track({0.0: [34, 0, 0], 1.20: [34, 0, 0]})},
        "leg_bl": {"rotation": track({0.0: [58, 0, 0], 0.60: [64, 0, 0], 1.20: [58, 0, 0]})},
        "leg_br": {"rotation": track({0.0: [58, 0, 0], 0.60: [64, 0, 0], 1.20: [58, 0, 0]})},
        "tail_1": {"rotation": track({0.0: [-6, 0, 0], 0.60: [6, 0, 0], 1.20: [-6, 0, 0]})},
        "tail_4": {"rotation": track({0.0: [-8, 0, 0], 0.65: [8, 0, 0], 1.20: [-8, 0, 0]})},
    })

    # 4. HOVER (1.00s = 20t): lơ lửng vỗ cánh dứt khoát, thân dựng nhẹ chĩa đầu xuống mục tiêu
    anims["animation.cinder_dragon.hover"] = make_anim(1.00, True, {
        "body_root": {
            "position": track({0.0: [0, 1.5, 0], 0.45: [0, -1.2, 0], 0.80: [0, 2.8, 0], 1.00: [0, 1.5, 0]}),
            "rotation": track({0.0: [-22, 0, 0], 0.50: [-26, 0, 0], 1.00: [-22, 0, 0]}),
        },
        "neck_1": {"rotation": track({0.0: [16, 0, 0], 1.00: [16, 0, 0]})},
        "head": {"rotation": track({0.0: [12, 0, 0], 1.00: [12, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-8, -6, -20], 0.45: [-14, -10, -46], 0.78: [14, 12, 28], 1.00: [-8, -6, -20]})},
        "wing_mid_l": {"rotation": track({0.0: [0, -4, 12], 0.48: [0, -14, 28], 0.80: [0, 10, -14], 1.00: [0, -4, 12]})},
        "wing_r": {"rotation": track({0.0: [-8, 6, 20], 0.45: [-14, 10, 46], 0.78: [14, -12, -28], 1.00: [-8, 6, 20]})},
        "wing_mid_r": {"rotation": track({0.0: [0, 4, -12], 0.48: [0, 14, -28], 0.80: [0, -10, 14], 1.00: [0, 4, -12]})},
        "leg_fl": {"rotation": track({0.0: [38, 0, 0], 1.00: [38, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [38, 0, 0], 1.00: [38, 0, 0]})},
        "leg_bl": {"rotation": track({0.0: [42, 0, 0], 1.00: [42, 0, 0]})},
        "leg_br": {"rotation": track({0.0: [42, 0, 0], 1.00: [42, 0, 0]})},
        "tail_1": {"rotation": track({0.0: [18, 0, 0], 0.50: [26, 0, 0], 1.00: [18, 0, 0]})},
    })

    # 5. BREATH — Cinder Breath (2.80s = 56t, telegraph 0..20t [1.0s], quét trái->phải 20..46t, settle 46..56t)
    anims["animation.cinder_dragon.breath"] = make_anim(2.80, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 0.75: [0, -2.4, 2.0], 1.00: [0, -1.8, -1.0], 2.30: [0, -1.6, -1.0], 2.80: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.80: [-10, 10, 0], 1.05: [12, 18, 0], 1.65: [10, 0, 0], 2.25: [12, -18, 0], 2.80: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.80: [-22, 18, 0], 1.05: [14, 24, 0], 1.65: [12, 0, 0], 2.25: [14, -24, 0], 2.80: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 0.80: [-14, 16, 0], 1.05: [8, 22, 0], 1.65: [6, 0, 0], 2.25: [8, -22, 0], 2.80: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.80: [-12, 12, 0], 1.05: [8, 16, 0], 1.65: [8, 0, 0], 2.25: [8, -16, 0], 2.80: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 0.80: [24, 0, 0], 1.00: [44, 0, 0], 2.25: [42, 0, 0], 2.80: [4, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.80: [-16, -20, -30], 1.10: [-6, 4, -8], 2.30: [-6, 2, -10], 2.80: [-10, -12, -14]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.80: [-16, 20, 30], 1.10: [-6, -4, 8], 2.30: [-6, -2, 10], 2.80: [-10, 12, 14]})},
    })

    # 6. FIREBALL — Magma Fireball Barrage (2.20s = 44t, shots at 16t [0.80s], 24t [1.20s], 32t [1.60s])
    anims["animation.cinder_dragon.fireball"] = make_anim(2.20, False, {
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.65: [-24, 0, 0], 0.80: [12, 0, 0], 1.05: [-18, 0, 0], 1.20: [12, 0, 0], 1.45: [-18, 0, 0], 1.60: [14, 0, 0], 2.20: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 0.65: [-16, 0, 0], 0.80: [10, 0, 0], 1.05: [-14, 0, 0], 1.20: [10, 0, 0], 1.45: [-14, 0, 0], 1.60: [12, 0, 0], 2.20: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.65: [-14, 0, 0], 0.80: [12, 0, 0], 1.05: [-12, 0, 0], 1.20: [12, 0, 0], 1.45: [-12, 0, 0], 1.60: [12, 0, 0], 2.20: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 0.65: [36, 0, 0], 0.85: [12, 0, 0], 1.05: [36, 0, 0], 1.25: [12, 0, 0], 1.45: [38, 0, 0], 1.70: [12, 0, 0], 2.20: [4, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.70: [-14, -18, -26], 1.60: [-6, -6, -12], 2.20: [-10, -12, -14]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.70: [-14, 18, 26], 1.60: [-6, 6, 12], 2.20: [-10, 12, 14]})},
    })

    # 7. BITE — Savage Bite & Horn Gore (1.80s = 36t, telegraph 0..18t [0.90s], impact 19t [0.95s])
    anims["animation.cinder_dragon.bite"] = make_anim(1.80, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 0.75: [0, -1.5, 4.8], 0.95: [0, -0.8, -6.5], 1.25: [0, -0.4, -3.5], 1.80: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.75: [-14, 0, 0], 0.95: [16, 0, 0], 1.25: [8, 0, 0], 1.80: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.75: [-30, 0, 0], 0.95: [24, 0, 0], 1.20: [14, 0, 0], 1.80: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 0.75: [-22, 0, 0], 0.95: [18, 0, 0], 1.80: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.75: [-16, 0, 0], 0.95: [22, 0, 0], 1.25: [-8, 0, 0], 1.80: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 0.78: [48, 0, 0], 0.95: [2, 0, 0], 1.25: [10, 0, 0], 1.80: [4, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.75: [-16, -24, -32], 0.98: [-6, -8, -10], 1.80: [-10, -12, -14]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.75: [-16, 24, 32], 0.98: [-6, 8, 10], 1.80: [-10, 12, 14]})},
    })

    # 8. WING_SWEEP — Wing Claw Sweep (1.80s = 36t, telegraph 0..16t [0.80s], impact 18t [0.90s])
    anims["animation.cinder_dragon.wing_sweep"] = make_anim(1.80, False, {
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 0.70: [4, -28, -8], 0.90: [10, 32, 10], 1.25: [6, 18, 4], 1.80: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.70: [-6, 18, 0], 0.90: [8, -22, 0], 1.80: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.70: [-18, -48, -42], 0.90: [14, 42, 12], 1.25: [6, 20, 4], 1.80: [-10, -12, -14]})},
        "wing_mid_l": {"rotation": track({0.0: [-4, -8, 12], 0.70: [0, -24, 14], 0.90: [0, 22, -12], 1.80: [-4, -8, 12]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.70: [-12, 22, 20], 0.95: [-10, 18, 18], 1.80: [-10, 12, 14]})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 0.70: [0, 16, 0], 0.90: [0, -20, 0], 1.80: [0, 0, 0]})},
    })

    # 9. TAIL_SLAM — Tail Crescent Slam (2.00s = 40t, telegraph 0..18t [0.90s], impact 20t [1.00s])
    anims["animation.cinder_dragon.tail_slam"] = make_anim(2.00, False, {
        "body_root": {"rotation": track({0.0: [0, 0, 0], 0.80: [4, -24, 0], 1.00: [-4, 28, 0], 1.35: [0, 14, 0], 2.00: [0, 0, 0]})},
        "pelvis": {"rotation": track({0.0: [0, 0, 0], 0.80: [-12, -28, 8], 1.00: [10, 36, -8], 1.40: [4, 18, 0], 2.00: [0, 0, 0]})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 0.80: [24, -36, 0], 1.00: [-12, 42, 0], 1.35: [-6, 26, 0], 2.00: [0, 0, 0]})},
        "tail_2": {"rotation": track({0.0: [0, 0, 0], 0.80: [18, -32, 0], 1.00: [-8, 38, 0], 2.00: [0, 0, 0]})},
        "tail_4": {"rotation": track({0.0: [0, 0, 0], 0.80: [16, -28, 0], 1.00: [-10, 34, 0], 2.00: [0, 0, 0]})},
        "tail_6": {"rotation": track({0.0: [0, 0, 0], 0.80: [14, -24, 0], 1.00: [-10, 28, 0], 2.00: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.85: [-14, -6, -34], 1.05: [-6, -16, -12], 2.00: [-10, -12, -14]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.85: [-14, 6, 34], 1.05: [-6, 16, 12], 2.00: [-10, 12, 14]})},
    })

    # 10. STOMP — Winged Stomp (2.10s = 42t, hop 0..18t [0.90s], slam impact 21t [1.05s])
    anims["animation.cinder_dragon.stomp"] = make_anim(2.10, False, {
        "body_root": {
            "position": track({0.0: [0, 0, 0], 0.80: [0, 8.5, 0], 1.05: [0, -2.6, 0], 1.35: [0, -1.0, 0], 2.10: [0, 0, 0]}),
            "rotation": track({0.0: [0, 0, 0], 0.80: [-26, 0, 0], 1.05: [12, 0, 0], 1.40: [4, 0, 0], 2.10: [0, 0, 0]}),
        },
        "leg_fl": {"rotation": track({0.0: [0, 0, 0], 0.80: [-42, 0, -10], 1.05: [18, 0, 4], 2.10: [0, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [0, 0, 0], 0.80: [-42, 0, 10], 1.05: [18, 0, -4], 2.10: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.45: [-14, -10, -48], 0.85: [14, 8, 24], 1.08: [-6, -4, -18], 2.10: [-10, -12, -14]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.45: [-14, 10, 48], 0.85: [14, -8, -24], 1.08: [-6, 4, 18], 2.10: [-10, 12, 14]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.80: [-18, 0, 0], 1.05: [18, 0, 0], 2.10: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 0.80: [38, 0, 0], 1.15: [24, 0, 0], 2.10: [4, 0, 0]})},
    })

    # 11. SKYFALL_INFERNO (Phase 2 exclusive — 4.60s = 92t: takeoff 0..20t, aerial fire strafe 20..72t, heavy landing 72..92t)
    anims["animation.cinder_dragon.skyfall_inferno"] = make_anim(4.60, False, {
        "body_root": {"rotation": track({0.0: [0, 0, 0], 0.60: [-28, 0, 0], 1.20: [8, 0, 0], 3.40: [10, 0, 0], 4.00: [16, 0, 0], 4.60: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.20: [24, -12, 0], 2.30: [28, 12, 0], 3.40: [24, -8, 0], 4.60: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 1.20: [22, -10, 0], 2.30: [24, 10, 0], 3.40: [20, -6, 0], 4.60: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 1.20: [46, 0, 0], 3.40: [46, 0, 0], 4.00: [18, 0, 0], 4.60: [4, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.50: [-14, -8, -48], 1.00: [14, 8, 28], 1.60: [-12, -10, -44], 2.20: [14, 8, 28], 2.80: [-12, -10, -44], 3.40: [14, 8, 28], 4.00: [-8, -10, -22], 4.60: [-10, -12, -14]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.50: [-14, 8, 48], 1.00: [14, -8, -28], 1.60: [-12, 10, 44], 2.20: [14, -8, -28], 2.80: [-12, 10, 44], 3.40: [14, -8, -28], 4.00: [-8, 10, 22], 4.60: [-10, 12, 14]})},
        "leg_fl": {"rotation": track({0.0: [0, 0, 0], 0.80: [48, 0, 0], 3.60: [42, 0, 0], 4.05: [-12, 0, 0], 4.60: [0, 0, 0]})},
        "leg_fr": {"rotation": track({0.0: [0, 0, 0], 0.80: [48, 0, 0], 3.60: [42, 0, 0], 4.05: [-12, 0, 0], 4.60: [0, 0, 0]})},
        "leg_bl": {"rotation": track({0.0: [0, 0, 0], 0.80: [54, 0, 0], 3.60: [48, 0, 0], 4.05: [0, 0, 0], 4.60: [0, 0, 0]})},
        "leg_br": {"rotation": track({0.0: [0, 0, 0], 0.80: [54, 0, 0], 3.60: [48, 0, 0], 4.05: [0, 0, 0], 4.60: [0, 0, 0]})},
    })

    # 12. BEAM — Scorched Earth Beam (Phase 2 exclusive — 3.20s = 64t, charge 0..26t [1.30s], laser fire 26..48t, cool 48..64t)
    anims["animation.cinder_dragon.beam"] = make_anim(3.20, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 1.15: [0, -2.8, 3.6], 1.35: [0, -1.6, 5.2], 2.40: [0, -1.8, 4.6], 3.20: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 1.15: [-18, 0, 0], 1.35: [8, 0, 0], 2.40: [6, 0, 0], 3.20: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.15: [-32, 0, 0], 1.35: [14, 0, 0], 2.40: [12, 0, 0], 3.20: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 1.15: [-24, 0, 0], 1.35: [8, 0, 0], 2.40: [8, 0, 0], 3.20: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 1.15: [-16, 0, 0], 1.35: [4, 0, 0], 2.40: [4, 0, 0], 3.20: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 1.15: [32, 0, 0], 1.30: [52, 0, 0], 2.45: [50, 0, 0], 3.20: [4, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 1.15: [-16, -28, -36], 1.35: [-6, 10, -8], 2.45: [-6, 8, -10], 3.20: [-10, -12, -14]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 1.15: [-16, 28, 36], 1.35: [-6, -10, 8], 2.45: [-6, -8, 10], 3.20: [-10, 12, 14]})},
    })

    # 13. DIVE — Cataclysm Wing Dive (Phase 2 exclusive — 3.80s = 76t: leap 0..22t, hover-tuck 22..46t [1.20s lock], dive impact 52t [2.60s])
    anims["animation.cinder_dragon.dive"] = make_anim(3.80, False, {
        "body_root": {"rotation": track({0.0: [0, 0, 0], 0.90: [-34, 0, 0], 1.80: [24, 0, 0], 2.45: [46, 0, 0], 2.65: [14, 0, 0], 3.80: [0, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 0.55: [-14, -10, -48], 1.10: [16, 10, 28], 1.90: [-10, -46, -42], 2.55: [-10, -50, -44], 2.80: [-12, 12, -14], 3.80: [-10, -12, -14]})},
        "wing_mid_l": {"rotation": track({0.0: [-4, -8, 12], 1.90: [0, -36, 40], 2.55: [0, -38, 42], 2.85: [0, 8, -8], 3.80: [-4, -8, 12]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 0.55: [-14, 10, 48], 1.10: [16, -10, -28], 1.90: [-10, 46, 42], 2.55: [-10, 50, 44], 2.80: [-12, -12, 14], 3.80: [-10, 12, 14]})},
        "wing_mid_r": {"rotation": track({0.0: [-4, 8, -12], 1.90: [0, 36, -40], 2.55: [0, 38, -42], 2.85: [0, -8, 8], 3.80: [-4, 8, -12]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.90: [16, 0, 0], 2.55: [22, 0, 0], 2.85: [-18, 0, 0], 3.80: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 2.00: [38, 0, 0], 2.85: [46, 0, 0], 3.80: [4, 0, 0]})},
    })

    # 14. SUPERNOVA — Ultimate "Ashen Supernova" (Phase 2 <25% HP — 4.40s = 88t: charge 0..44t [2.20s], burst 46t [2.30s])
    anims["animation.cinder_dragon.supernova"] = make_anim(4.40, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 1.20: [0, -4.2, 0], 2.15: [0, -5.0, 0], 2.35: [0, 2.2, 0], 3.10: [0, 0.8, 0], 4.40: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 1.20: [18, 0, 0], 2.15: [22, 0, 0], 2.35: [-26, 0, 0], 3.20: [-14, 0, 0], 4.40: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.20: [24, 0, 0], 2.15: [28, 0, 0], 2.35: [-36, 0, 0], 3.20: [-22, 0, 0], 4.40: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 2.15: [22, 0, 0], 2.35: [-28, 0, 0], 4.40: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 2.15: [16, 0, 0], 2.35: [-24, 0, 0], 4.40: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 1.50: [14, 0, 0], 2.30: [56, 0, 0], 3.30: [48, 0, 0], 4.40: [4, 0, 0]})},
        # Gập cánh ôm ngực nén năng lượng 2.2s -> dang bùng toàn sải 360°!
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 1.10: [18, 44, 22], 2.15: [22, 50, 26], 2.35: [-16, -22, -46], 3.25: [-12, -16, -34], 4.40: [-10, -12, -14]})},
        "wing_mid_l": {"rotation": track({0.0: [-4, -8, 12], 1.10: [0, 32, 40], 2.15: [0, 38, 44], 2.35: [0, -8, -10], 4.40: [-4, -8, 12]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 1.10: [18, -44, -22], 2.15: [22, -50, -26], 2.35: [-16, 22, 46], 3.25: [-12, 16, 34], 4.40: [-10, 12, 14]})},
        "wing_mid_r": {"rotation": track({0.0: [-4, 8, -12], 1.10: [0, -32, -40], 2.15: [0, -38, -44], 2.35: [0, 8, 10], 4.40: [-4, 8, -12]})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 2.15: [16, 0, 0], 2.40: [-18, 0, 0], 4.40: [0, 0, 0]})},
    })

    # 15. PHASE_CHANGE — "Vô Form" (5.50s = 110t: bám đất co cánh nén -> bật ngửa cổ dang cánh gầm vang trời)
    anims["animation.cinder_dragon.phase_change"] = make_anim(5.50, False, {
        "body_root": {"position": track({0.0: [0, 0, 0], 1.40: [0, -4.6, 0], 2.70: [0, -5.2, 0], 3.10: [0, 1.8, 0], 4.40: [0, 1.0, 0], 5.50: [0, 0, 0]})},
        "body_chest": {"rotation": track({0.0: [0, 0, 0], 1.40: [20, 0, 0], 2.70: [24, 0, 0], 3.10: [-28, 0, 0], 4.40: [-20, 0, 0], 5.50: [0, 0, 0]})},
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 1.40: [22, 0, 0], 2.70: [26, 0, 0], 3.10: [-38, 0, 0], 4.40: [-30, 0, 0], 5.50: [0, 0, 0]})},
        "neck_2": {"rotation": track({0.0: [0, 0, 0], 2.70: [18, 0, 0], 3.10: [-28, 0, 0], 4.40: [-20, 0, 0], 5.50: [0, 0, 0]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 2.70: [14, 0, 0], 3.10: [-26, 0, 0], 4.40: [-18, 0, 0], 5.50: [0, 0, 0]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 2.60: [12, 0, 0], 3.05: [58, 0, 0], 4.50: [52, 0, 0], 5.50: [8, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 1.40: [18, 44, 20], 2.70: [22, 50, 24], 3.15: [-18, -24, -48], 4.45: [-12, -18, -36], 5.50: [-10, -12, -14]})},
        "wing_mid_l": {"rotation": track({0.0: [-4, -8, 12], 2.70: [0, 36, 42], 3.15: [0, -10, -12], 5.50: [-4, -8, 12]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 1.40: [18, -44, -20], 2.70: [22, -50, -24], 3.15: [-18, 24, 48], 4.45: [-12, 18, 36], 5.50: [-10, 12, 14]})},
        "wing_mid_r": {"rotation": track({0.0: [-4, 8, -12], 2.70: [0, -36, -42], 3.15: [0, 10, 12], 5.50: [-4, 8, -12]})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 2.70: [14, 0, 0], 3.15: [-22, 0, 0], 5.50: [0, 0, 0]})},
    })

    # 16. DEATH (3.20s = 64t, hold_on_last_frame)
    anims["animation.cinder_dragon.death"] = make_anim(3.20, "hold", {
        "body_root": {
            "position": track({0.0: [0, 0, 0], 0.80: [0, 1.5, 2.0], 1.90: [0, -9.5, 4.0], 3.20: [0, -11.5, 4.5]}),
            "rotation": track({0.0: [0, 0, 0], 0.80: [-14, 0, 8], 1.90: [6, 0, 22], 3.20: [8, 0, 26]}),
        },
        "neck_1": {"rotation": track({0.0: [0, 0, 0], 0.80: [-22, 0, 0], 2.20: [26, 12, 10], 3.20: [30, 16, 12]})},
        "head": {"rotation": track({0.0: [0, 0, 0], 0.80: [-18, 0, 0], 2.40: [24, 14, 14], 3.20: [28, 16, 16]})},
        "jaw": {"rotation": track({0.0: [4, 0, 0], 0.80: [42, 0, 0], 3.20: [28, 0, 0]})},
        "wing_l": {"rotation": track({0.0: [-10, -12, -14], 1.20: [-14, -18, -32], 2.40: [20, -24, 18], 3.20: [24, -26, 22]})},
        "wing_r": {"rotation": track({0.0: [-10, 12, 14], 1.20: [-14, 18, 32], 2.40: [20, 24, -18], 3.20: [24, 26, -22]})},
        "tail_1": {"rotation": track({0.0: [0, 0, 0], 2.50: [-14, -16, 0], 3.20: [-16, -20, 0]})},
    })

    # Kiểm tra an toàn: không có góc xoay vượt 170° (chống lỗi xoay đầu / gimbal flip)
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


def collect_world_geometry(geo: dict):
    bones_list = geo["minecraft:geometry"][0]["bones"]
    by_name = {b["name"]: b for b in bones_list}
    world_M = {}

    def get_bone_M(name):
        if name in world_M:
            return world_M[name]
        b = by_name[name]
        local = rot_about_pivot(b.get("pivot", [0, 0, 0]), b.get("rotation", [0, 0, 0]))
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


def render_previews(geo: dict) -> None:
    tex_dir = ASSETS / "textures" / "entity"
    d_norm = np.array(Image.open(tex_dir / "cinder_dragon.png").convert("RGBA"), dtype=np.float32)
    g_norm = np.array(Image.open(tex_dir / "cinder_dragon_glowmask.png").convert("RGBA"), dtype=np.float32)
    d_enr = np.array(Image.open(tex_dir / "cinder_dragon_enraged.png").convert("RGBA"), dtype=np.float32)
    g_enr = np.array(Image.open(tex_dir / "cinder_dragon_enraged_glowmask.png").convert("RGBA"), dtype=np.float32)

    faces, bmin, bmax = collect_world_geometry(geo)
    center = (bmin + bmax) * 0.5
    span = float(np.max(bmax - bmin))

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

    specs = [
        ("preview_front.png",          180,  8, sc * 1.12, center, d_norm, g_norm, "FRONT (FIRE EYES, CROWN HORNS, FANGS, WIDE RED WINGS)"),
        ("preview_34_hero.png",        148, 14, sc * 1.18, center, d_norm, g_norm, "3/4 HERO (STONE SCALES, WING CLAWS, THROAT MAGMA)"),
        ("preview_side_left.png",      -90,  6, sc * 1.10, center, d_norm, g_norm, "SIDE (CROUCH STANCE, 7-SEG TAIL, CRESCENT MACE)"),
        ("preview_rear.png",             0, 10, sc * 1.12, center, d_norm, g_norm, "REAR (SCALLOPED RED MEMBRANES, OBSIDIAN TAIL MACE)"),
        ("preview_top_down.png",       180, 82, sc * 1.05, center, d_norm, g_norm, "TOP-DOWN (WINGSPAN > BODY LENGTH, ZERO MEMBRANE GAPS)"),
        ("preview_phase2_enraged.png", 148, 14, sc * 1.18, center, d_enr,  g_enr,  "PHASE 2 VO FORM (MOLTEN CRACKS, BLAZING WING RIMS & CORE)"),
    ]
    imgs = []
    for fn, yw, pt, s_c, ctr, d_i, g_i, title in specs:
        im = rasterize_alpha_cutout(faces, d_i, g_i, cam_mat(yw, pt), pw, ph, s_c, ctr)
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
    build_animations()
    render_previews(geo_data)
    print(f"ALL_DONE: Cinder Dragon generated ({meta['bones']} bones, {total_cubes} cubes, {TEX_W}x{TEX_H}, mouth_flame={meta['mouth_flame']})")
