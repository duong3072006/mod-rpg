#!/usr/bin/env python3
"""
Bộ sinh Model 3D Voxel GeckoLib (`dual_knife.geo.json`), Animation (`dual_knife.animation.json`),
Texture `64x64` RGBA nearest **Netherite Đen Rèn Damascus pha Mép Cắt Xanh Dương Kim Cương**,
Icon `32x32` chữ X (`textures/item/dual_knife.png`), và bảng ảnh preview 4 góc trên nhân vật Minecraft
(`tools/dual_knife_previews/dual_knife_stance_preview.png`):
  1. Chính diện nhân vật Minecraft trong thế thủ sát thủ vắt chéo 2 tầng (`0.72x` scale Dual Knife).
  2. Góc nghiêng 3/4 nhân vật Minecraft kiểm chứng 0 xuyên ngực/mặt/mũ giáp & tách lớp rõ 2 tầng.
  3. Cận cảnh Dual Knife (`0.72x` gọn sắc) & Glowmask chọn lọc ban đêm (chỉ sáng mép cắt xanh kim cương).
  4. Icon 32x32 chữ X trên ô Hotbar tối & ô Kho đồ sáng.

Chạy: python tools/generate_dual_knife_model.py
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "dual_knife_previews"

TEX_W = 64
TEX_H = 64
KNIFE_RENDER_SCALE = 0.72

MAT = {
    "NETH_DEEP":      (0,  0,  16, 16),
    "NETH_FORGED":    (16, 0,  16, 16),
    "NETH_BEVEL":     (32, 0,  16, 16),
    "GRIP_WRAP":      (48, 0,  16, 16),
    "NETH_CLAD_SEAM": (0,  16, 16, 16),
    "DIAMOND_BEVEL":  (16, 16, 16, 16),
    "DIAMOND_RAZOR":  (32, 16, 16, 16),
    "CRYSTAL_GROOVE": (48, 16, 16, 16),
}


def uv_box(mat_key: str, pad: int = 1) -> dict:
    x0, y0, w, h = MAT[mat_key]
    f = {"uv": [x0 + pad, y0 + pad], "uv_size": [w - 2 * pad, h - 2 * pad]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def make_centered_cube(center, size, mat: str, rot=None, face_mats=None) -> dict:
    cx, cy, cz = [float(v) for v in center]
    sx, sy, sz = [float(v) for v in size]
    uvs = uv_box(mat)
    if face_mats:
        for fn, mk in face_mats.items():
            uvs[fn] = uv_box(mk)[fn]
    c = {
        "origin": [round(cx - sx * 0.5, 3), round(cy - sy * 0.5, 3), round(cz - sz * 0.5, 3)],
        "size": [round(sx, 3), round(sy, 3), round(sz, 3)],
        "uv": uvs,
    }
    if rot is not None and any(abs(v) > 1e-4 for v in rot):
        c["pivot"] = [round(cx, 3), round(cy, 3), round(cz, 3)]
        c["rotation"] = [round(float(rot[0]), 2), round(float(rot[1]), 2), round(float(rot[2]), 2)]
    return c


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


def rot_x(rad):
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)


def rot_y(rad):
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)


def rot_z(rad):
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


def build_dual_knife_geo():
    """Khung hình học 81 khối thanh thoát sắc nét chuẩn song dao găm Dual Knife (~68% Netherite Damascus + mép cắt Xanh Kim Cương)."""
    hilt_cubes = []
    hilt_cubes.append(make_centered_cube([0.0, 0.0, 0.0], [1.02, 5.0, 1.32], "GRIP_WRAP"))
    for wy in (-1.8, -0.7, 0.4, 1.5):
        hilt_cubes.append(make_centered_cube([0.0, wy, 0.0], [1.16, 0.28, 1.46], "NETH_DEEP", rot=[8.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 0.0, 0.0], [1.08, 4.0, 0.22], "CRYSTAL_GROOVE"))

    hilt_cubes.append(make_centered_cube([0.0, 2.95, -0.45], [1.46, 1.12, 4.0], "NETH_FORGED", rot=[-12.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 3.45, -2.45], [1.06, 0.92, 2.2], "NETH_BEVEL", rot=[-28.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 2.35, +1.68], [1.10, 0.88, 1.9], "NETH_DEEP", rot=[22.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 3.00, -0.40], [1.70, 0.74, 0.74], "CRYSTAL_GROOVE", rot=[45.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, 0.35, -2.02], [0.52, 3.8, 0.50], "NETH_BEVEL", rot=[11.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, -1.65, -1.20], [0.64, 0.68, 2.0], "NETH_DEEP", rot=[-20.0, 0.0, 0.0]))

    pommel_pts = [
        ([0.0, -2.95, +0.55], [1.06, 1.20, 1.78], -20.0, "NETH_FORGED"),
        ([0.0, -4.15, +1.95], [0.86, 1.00, 1.88], -38.0, "NETH_DEEP"),
        ([0.0, -4.85, +3.55], [0.64, 0.82, 1.72], -58.0, "NETH_BEVEL"),
        ([0.0, -4.55, +4.95], [0.44, 0.70, 1.48], -82.0, "NETH_CLAD_SEAM"),
        ([0.0, -3.55, +5.65], [0.26, 0.60, 1.24], -112.0, "DIAMOND_BEVEL"),
        ([0.0, -2.65, +5.35], [0.16, 0.48, 0.90], -134.0, "DIAMOND_RAZOR"),
    ]
    for pos, sz, rx, mat in pommel_pts:
        hilt_cubes.append(make_centered_cube(pos, sz, mat, rot=[rx, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, -4.25, -0.55], [0.48, 1.60, 0.74], "NETH_BEVEL", rot=[-26.0, 0.0, 0.0]))
    hilt_cubes.append(make_centered_cube([0.0, -5.40, -1.08], [0.22, 1.20, 0.48], "DIAMOND_RAZOR", rot=[-32.0, 0.0, 0.0]))

    blade_cubes = []
    blade_cubes.append(make_centered_cube([0.0, 5.05, -0.55], [1.06, 2.95, 3.60], "NETH_FORGED", rot=[-8.0, 0.0, 0.0]))
    blade_cubes.append(make_centered_cube([0.0, 5.25, -2.55], [0.70, 2.35, 1.78], "NETH_BEVEL", rot=[-14.0, 0.0, 0.0]))

    spine_segs = [
        ([0.0,  7.6, +1.40], [0.94, 3.6, 1.32],  +8.0, "NETH_DEEP"),
        ([0.0, 10.8, +1.88], [0.88, 3.6, 1.34],  +4.0, "NETH_FORGED"),
        ([0.0, 14.0, +1.92], [0.82, 3.5, 1.30],  -5.0, "NETH_DEEP"),
        ([0.0, 17.1, +1.30], [0.76, 3.4, 1.26], -15.0, "NETH_FORGED"),
        ([0.0, 20.0, +0.18], [0.68, 3.2, 1.20], -25.0, "NETH_BEVEL"),
        ([0.0, 22.6, -1.32], [0.58, 3.0, 1.12], -35.0, "NETH_FORGED"),
        ([0.0, 24.9, -3.10], [0.44, 2.7, 1.02], -44.0, "NETH_BEVEL"),
        ([0.0, 26.8, -4.85], [0.32, 2.2, 0.88], -52.0, "NETH_BEVEL"),
    ]
    for pos, sz, rx, mat in spine_segs:
        blade_cubes.append(make_centered_cube(pos, sz, mat, rot=[rx, 0.0, 0.0]))

    vein_segs = [
        ([0.0,  9.2, +1.30], [0.96, 2.8, 0.22],  +6.0),
        ([0.0, 15.5, +1.30], [0.84, 2.8, 0.20], -10.0),
        ([0.0, 21.3, -0.72], [0.68, 2.6, 0.18], -30.0),
    ]
    for pos, sz, rx in vein_segs:
        blade_cubes.append(make_centered_cube(pos, sz, "CRYSTAL_GROOVE", rot=[rx, 0.0, 0.0]))

    blade_cubes.append(make_centered_cube([0.0, 12.9, -0.35], [0.80, 1.20, 4.15], "NETH_FORGED", rot=[-22.0, 0.0, 0.0]))
    blade_cubes.append(make_centered_cube([0.0, 12.9, -0.35], [0.88, 0.22, 3.15], "CRYSTAL_GROOVE", rot=[-22.0, 0.0, 0.0]))
    blade_cubes.append(make_centered_cube([0.0, 20.1, -1.70], [0.66, 1.08, 3.40], "NETH_BEVEL", rot=[-30.0, 0.0, 0.0]))
    blade_cubes.append(make_centered_cube([0.0, 20.1, -1.70], [0.74, 0.20, 2.48], "CRYSTAL_GROOVE", rot=[-30.0, 0.0, 0.0]))
    blade_cubes.append(make_centered_cube([0.0, 9.7, -0.45], [0.42, 0.45, 3.20], "NETH_DEEP", rot=[28.0, 0.0, 0.0]))

    dorsal_teeth = [
        ([0.0,  9.5, +2.75], [0.44, 1.50, 1.00], -34.0, "NETH_BEVEL"),
        ([0.0, 10.5, +3.42], [0.20, 1.10, 0.68], -46.0, "DIAMOND_BEVEL"),
        ([0.0, 14.8, +2.85], [0.40, 1.50, 0.96], -38.0, "NETH_BEVEL"),
        ([0.0, 15.8, +3.52], [0.18, 1.08, 0.64], -50.0, "DIAMOND_RAZOR"),
        ([0.0, 19.6, +1.48], [0.36, 1.35, 0.88], -44.0, "NETH_BEVEL"),
        ([0.0, 20.5, +2.05], [0.15, 0.94, 0.56], -56.0, "DIAMOND_RAZOR"),
    ]
    for pos, sz, rx, mat in dorsal_teeth:
        blade_cubes.append(make_centered_cube(pos, sz, mat, rot=[rx, 0.0, 0.0]))

    blade_cubes.append(make_centered_cube([0.0, 3.70, -4.10], [0.52, 1.20, 2.15], "NETH_CLAD_SEAM", rot=[18.0, 0.0, 0.0]))
    blade_cubes.append(make_centered_cube([0.0, 2.25, -5.15], [0.32, 1.88, 1.28], "DIAMOND_BEVEL", rot=[-16.0, 0.0, 0.0]))
    blade_cubes.append(make_centered_cube([0.0, 0.85, -4.85], [0.15, 1.30, 0.78], "DIAMOND_RAZOR", rot=[-38.0, 0.0, 0.0]))

    glow_cubes = []
    edge_curve = [
        ( 6.6, -2.45, -3.80, -4.60, 3.0, -10.0),
        ( 9.4, -2.92, -4.42, -5.32, 3.2,  -4.0),
        (12.4, -3.20, -4.80, -5.78, 3.3,  +2.0),
        (15.4, -3.20, -4.88, -5.86, 3.3,  -4.0),
        (18.3, -3.02, -4.60, -5.58, 3.2, -12.0),
        (21.1, -2.65, -4.12, -5.06, 3.1, -22.0),
        (23.7, -2.26, -3.60, -4.52, 2.9, -33.0),
        (26.0, -2.18, -3.46, -4.32, 2.6, -44.0),
    ]
    for i, (ey, zi, zm, zr, sh, rx) in enumerate(edge_curve):
        eps = 0.025 if (i % 2 == 1) else 0.0
        glow_cubes.append(make_centered_cube([0.0, ey, zi], [0.52 + eps, sh, 1.82], "NETH_CLAD_SEAM", rot=[rx, 0.0, 0.0]))
        glow_cubes.append(make_centered_cube([0.0, ey, zm], [0.25 + eps, sh * 0.96, 1.00], "DIAMOND_BEVEL", rot=[rx, 0.0, 0.0]))
        glow_cubes.append(make_centered_cube([0.0, ey, zr], [0.11 + eps, sh * 0.94, 0.68], "DIAMOND_RAZOR", rot=[rx, 0.0, 0.0]))

    glow_cubes.append(make_centered_cube([0.0, 27.4, -5.00], [0.32, 3.0, 1.76], "NETH_CLAD_SEAM", rot=[-49.0, 0.0, 0.0]))
    glow_cubes.append(make_centered_cube([0.0, 28.7, -6.16], [0.20, 2.7, 1.40], "DIAMOND_BEVEL", rot=[-55.0, 0.0, 0.0]))
    glow_cubes.append(make_centered_cube([0.0, 29.9, -7.22], [0.13, 2.3, 1.08], "DIAMOND_RAZOR", rot=[-60.0, 0.0, 0.0]))
    glow_cubes.append(make_centered_cube([0.0, 30.9, -8.04], [0.08, 1.6, 0.72], "DIAMOND_RAZOR", rot=[-64.0, 0.0, 0.0]))

    for sy, sz in ((8.2, -5.46), (11.2, -5.98), (14.2, -6.08), (17.2, -5.88), (20.2, -5.40), (23.0, -4.78)):
        glow_cubes.append(make_centered_cube([0.0, sy, sz], [0.10, 0.54, 0.54], "DIAMOND_RAZOR", rot=[45.0, 0.0, 0.0]))

    bones = [
        {"name": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0]},
        {"name": "blade_root", "parent": "root", "pivot": [0, 0, 0], "rotation": [0, 0, 0], "cubes": hilt_cubes},
        {"name": "blade_body", "parent": "blade_root", "pivot": [0, 4.0, 0], "rotation": [0, 0, 0], "cubes": blade_cubes},
        {"name": "blade_edge", "parent": "blade_body", "pivot": [0, 14.0, -4.0], "rotation": [0, 0, 0], "cubes": glow_cubes},
    ]

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.dual_knife",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 2.4,
                    "visible_bounds_height": 3.0,
                    "visible_bounds_offset": [0.0, 0.8, 0.0],
                },
                "bones": bones,
            }
        ],
    }
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out = ASSETS / "geo" / "item" / "dual_knife.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(geo, indent=2), encoding="utf-8")
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} sleek cubes, 64x64 UV)")
    return geo, total_cubes


def build_dual_knife_animations():
    anims = {
        "animation.dual_knife.idle": {
            "loop": True,
            "animation_length": 2.4,
            "bones": {
                "blade_edge": {
                    "scale": {
                        "0.0": [1.0, 1.0, 1.0],
                        "1.2": [1.02, 1.005, 1.01],
                        "2.4": [1.0, 1.0, 1.0],
                    }
                }
            },
        },
        "animation.dual_knife.right_slash": {
            "animation_length": 0.30,
            "bones": {
                "blade_root": {
                    "rotation": {
                        "0.0": [-12, 18, -10],
                        "0.12": [42, -32, -28],
                        "0.30": [0, 0, 0],
                    }
                }
            },
        },
        "animation.dual_knife.left_slash": {
            "animation_length": 0.30,
            "bones": {
                "blade_root": {
                    "rotation": {
                        "0.0": [-12, -18, 10],
                        "0.12": [42, 32, 28],
                        "0.30": [0, 0, 0],
                    }
                }
            },
        },
        "animation.dual_knife.cross_slash": {
            "animation_length": 0.34,
            "bones": {
                "blade_root": {
                    "rotation": {
                        "0.0": [-24, 0, 0],
                        "0.14": [58, 0, -24],
                        "0.34": [0, 0, 0],
                    },
                    "scale": {
                        "0.0": [1.0, 1.0, 1.0],
                        "0.14": [1.06, 1.04, 1.06],
                        "0.34": [1.0, 1.0, 1.0],
                    },
                }
            },
        },
    }
    out = ASSETS / "animations" / "item" / "dual_knife.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=2), encoding="utf-8")
    print(f"ANIM_OK: {out} ({len(anims)} animations)")


def build_netherite_diamond_texture_64x64():
    diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    rng = np.random.default_rng(20269)

    def put(key, rgb, g_rgba=None):
        x0, y0, w, h = MAT[key]
        diff[y0:y0 + h, x0:x0 + w, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        diff[y0:y0 + h, x0:x0 + w, 3] = 255
        if g_rgba is not None:
            glow[y0:y0 + h, x0:x0 + w] = np.clip(g_rgba, 0, 255).astype(np.uint8)

    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32)
    noise = rng.random((16, 16)).astype(np.float32)
    damascus = 0.5 + 0.5 * np.sin((xx * 0.85 + yy * 0.55) + 1.35 * np.sin(yy * 0.65 - xx * 0.35))
    scratch = (((xx * 2 + yy) % 7) == 0).astype(np.float32) * 0.16

    N_DARK  = np.array([23, 21, 26], dtype=np.float32)
    N_MID   = np.array([38, 34, 43], dtype=np.float32)
    N_LIGHT = np.array([59, 53, 66], dtype=np.float32)
    N_SPEC  = np.array([90, 82, 99], dtype=np.float32)
    D_DEEP  = np.array([24, 154, 176], dtype=np.float32)
    D_CYAN  = np.array([60, 230, 245], dtype=np.float32)
    D_ICE   = np.array([194, 255, 255], dtype=np.float32)

    t1 = np.clip(damascus * 0.65 + noise * 0.35, 0.0, 1.0)[:, :, None]
    c1 = N_DARK * (1.0 - t1) + N_MID * t1
    border = ((xx == 0) | (xx == 15) | (yy == 0) | (yy == 15))[:, :, None]
    c1 = np.where(border, c1 * 0.68, c1 + scratch[:, :, None] * 18.0)
    put("NETH_DEEP", c1)

    t2 = np.clip(damascus * 0.72 + noise * 0.28, 0.0, 1.0)[:, :, None]
    c2 = N_MID * (1.0 - t2) + N_LIGHT * t2 + scratch[:, :, None] * 26.0
    ridge = (np.abs(xx - 7.5) < 1.0)[:, :, None]
    c2 = np.where(ridge, c2 * 0.55 + N_SPEC * 0.55, c2)
    groove = (np.abs(xx - 9.2) < 0.8)[:, :, None]
    c2 = np.where(groove, c2 * 0.62, c2)
    put("NETH_FORGED", c2)

    t3 = np.clip((15.0 - xx) / 15.0 * 0.55 + damascus * 0.30 + noise * 0.15, 0.0, 1.0)[:, :, None]
    c3 = N_LIGHT * (1.0 - t3) + N_SPEC * t3
    edge_hi = ((xx <= 1) | (yy <= 1))[:, :, None]
    c3 = np.where(edge_hi, c3 * 1.24 + 12.0, c3)
    put("NETH_BEVEL", c3)

    band = ((xx + yy) % 4.0) < 1.5
    c4 = np.where(band[:, :, None], N_LIGHT * 0.85, N_DARK * 0.88) * (0.90 + 0.18 * noise[:, :, None])
    put("GRIP_WRAP", c4)

    jagged_seam = 12.2 + 1.4 * np.sin(yy * 1.55) + 0.6 * np.cos(yy * 3.1)
    is_cyan_lip = (xx >= jagged_seam)[:, :, None]
    is_metal_rim = ((xx >= jagged_seam - 1.3) & (xx < jagged_seam))[:, :, None]
    t5 = np.clip(damascus * 0.70 + noise * 0.30, 0.0, 1.0)[:, :, None]
    c5_base = N_MID * (1.0 - t5) + N_LIGHT * t5 + scratch[:, :, None] * 24.0
    c5 = np.where(is_metal_rim, N_SPEC * 1.10, c5_base)
    c5 = np.where(is_cyan_lip, D_DEEP * 0.55 + D_CYAN * 0.45, c5)
    g5 = np.zeros((16, 16, 4), dtype=np.float32)
    g5[:, :, :3] = np.where(is_cyan_lip, D_CYAN, 0.0)
    g5[:, :, 3] = np.where(is_cyan_lip[:, :, 0], 195.0, 0.0)
    put("NETH_CLAD_SEAM", c5, g5)

    inner_tooth = (xx < (2.5 + 1.1 * np.sin(yy * 1.8)))[:, :, None]
    t6 = np.clip((xx / 15.0) * 0.72 + noise * 0.28, 0.0, 1.0)[:, :, None]
    c6_dia = D_DEEP * (1.0 - t6) + D_CYAN * t6
    facet = (((xx // 2) + (yy // 3)) % 2 == 0)[:, :, None]
    c6_dia = np.where(facet, c6_dia * 1.08, c6_dia * 0.90)
    c6 = np.where(inner_tooth, N_LIGHT * 0.85, c6_dia)
    g6 = np.zeros((16, 16, 4), dtype=np.float32)
    g6[:, :, :3] = np.where(inner_tooth, 0.0, c6_dia)
    g6[:, :, 3] = np.where(inner_tooth[:, :, 0], 0.0, 235.0)
    put("DIAMOND_BEVEL", c6, g6)

    t7 = np.clip(0.38 + 0.50 * (xx / 15.0) + 0.12 * noise, 0.0, 1.0)[:, :, None]
    c7 = D_CYAN * (1.0 - t7) + D_ICE * t7
    g7 = np.dstack([c7, np.full((16, 16), 255.0, dtype=np.float32)])
    put("DIAMOND_RAZOR", c7, g7)

    in_vein = (np.abs(xx - 7.5) <= 2.2)[:, :, None]
    in_core = (np.abs(xx - 7.5) <= 0.9)[:, :, None]
    c8 = N_DARK * (0.88 + 0.24 * noise[:, :, None])
    c8 = np.where(in_vein, D_DEEP * 0.45 + D_CYAN * 0.55, c8)
    c8 = np.where(in_core, D_ICE, c8)
    g8 = np.zeros((16, 16, 4), dtype=np.float32)
    g8[:, :, :3] = np.where(in_core, D_ICE, np.where(in_vein, D_CYAN, 0.0))
    g8[:, :, 3] = np.where(in_core[:, :, 0], 255.0, np.where(in_vein[:, :, 0], 210.0, 0.0))
    put("CRYSTAL_GROOVE", c8, g8)

    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(diff, "RGBA").save(tex_dir / "dual_knife_model.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "dual_knife_model_glowmask.png")
    print("TEX_MODEL_OK: dual_knife_model.png + selective edge glowmask (64x64 RGBA nearest)")
    return diff.astype(np.float32), glow.astype(np.float32)


def build_inventory_icon_32x32():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    C_OUTLINE = (9, 8, 12, 255)
    C_N_DARK  = (23, 21, 26, 255)
    C_N_MID   = (38, 34, 43, 255)
    C_N_LIGHT = (59, 53, 66, 255)
    C_N_SPEC  = (90, 82, 99, 255)
    C_D_DEEP  = (24, 154, 176, 255)
    C_D_CYAN  = (60, 230, 245, 255)
    C_D_ICE   = (194, 255, 255, 255)

    def render_knife_layer(reverse_orientation: bool):
        layer = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        lpx = layer.load()

        def put_p(x, y, col):
            tx, ty = (31 - x, 31 - y) if reverse_orientation else (x, y)
            if 0 <= tx < 32 and 0 <= ty < 32:
                lpx[tx, ty] = col

        for x, y, c in [
            (5, 26, C_N_DARK), (6, 25, C_N_MID), (7, 24, C_N_DARK), (8, 23, C_N_LIGHT),
            (9, 22, C_N_SPEC), (10, 21, C_D_ICE), (9, 21, C_N_LIGHT), (10, 22, C_N_DARK),
            (4, 27, C_N_LIGHT), (3, 26, C_N_SPEC), (3, 25, C_D_DEEP), (4, 24, C_D_CYAN),
            (11, 23, C_N_LIGHT), (12, 24, C_D_DEEP), (13, 24, C_D_ICE),
        ]:
            put_p(x, y, c)

        curve_rows = [
            (20, 10, 13, 14, 15, False),
            (19, 11, 14, 15, 16, True),
            (18, 11, 15, 16, 17, True),
            (17, 12, 16, 17, 18, False),
            (16, 13, 17, 18, 19, True),
            (15, 14, 18, 19, 20, True),
            (14, 15, 19, 20, 21, False),
            (13, 16, 20, 21, 22, True),
            (12, 17, 21, 22, 23, False),
            (11, 18, 22, 23, 24, False),
            (10, 19, 23, 24, 25, False),
            ( 9, 20, 24, 25, 26, False),
            ( 8, 21, 24, 25, 26, False),
            ( 7, 22, 25, 26, 27, False),
            ( 6, 23, 26, 27, 28, False),
            ( 5, 24, 26, 27, 28, False),
            ( 4, 25, 27, 28, 28, False),
            ( 3, 26, 27, 28, 28, False),
        ]
        for y, xs, xne, xc, xi, hollow in curve_rows:
            put_p(xs, y, C_N_DARK)
            put_p(xs + 1, y, C_N_MID)
            if not hollow:
                for x in range(xs + 2, xne):
                    put_p(x, y, C_N_SPEC if (x + y) % 3 == 0 else C_N_LIGHT)
            put_p(xne, y, C_D_DEEP if y % 2 == 0 else C_N_SPEC)
            put_p(xc, y, C_D_CYAN)
            put_p(xi, y, C_D_ICE)

        put_p(10, 15, C_N_SPEC); put_p(9, 14, C_D_CYAN)
        put_p(14, 11, C_N_SPEC); put_p(13, 10, C_D_ICE)

        outlined = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        opx = outlined.load()
        for y in range(32):
            for x in range(32):
                if lpx[x, y][3] > 0:
                    opx[x, y] = lpx[x, y]
                else:
                    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < 32 and 0 <= ny < 32 and lpx[nx, ny][3] > 0:
                            opx[x, y] = C_OUTLINE
                            break
        return outlined

    rev_layer = render_knife_layer(reverse_orientation=True).transpose(Image.FLIP_LEFT_RIGHT)
    std_layer = render_knife_layer(reverse_orientation=False)
    im.alpha_composite(rev_layer)
    im.alpha_composite(std_layer)

    out = ASSETS / "textures" / "item" / "dual_knife.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)
    print(f"ICON_OK: {out} (32x32 Dual Knife Standard+Reverse X-Icon)")
    return im


def build_box_faces_local(bmin, bmax, col_rgb):
    x0, y0, z0 = bmin
    x1, y1, z1 = bmax
    corners = np.array([
        [x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
        [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1],
    ], dtype=np.float64)
    faces = []
    for idxs in ([0, 1, 2, 3], [5, 4, 7, 6], [4, 0, 3, 7], [1, 5, 6, 2], [3, 2, 6, 7], [4, 5, 1, 0]):
        faces.append((corners[idxs], None, col_rgb))
    return faces


def transform_mc_part(faces, pivot, rx, ry, rz):
    """Exact Minecraft ModelPart.translateAndRotate: Translate(pivot) * Rz(rz) * Ry(ry) * Rx(rx)."""
    R = rot_z(rz) @ rot_y(ry) @ rot_x(rx)
    piv = np.array(pivot, dtype=np.float64)
    out = []
    for q, uv, col in faces:
        out.append(((q @ R.T) + piv, uv, col))
    return out


def mc_ydown_to_yup(faces):
    """Chuyển từ tọa độ Y-down của HumanoidModel (y=0 cổ, y=24 chân, -Z trước) sang Y-up để rasterizer vẽ chuẩn."""
    out = []
    for q, uv, col in faces:
        qq = q.copy()
        qq[:, 1] = 24.0 - qq[:, 1]
        out.append((qq, uv, col))
    return out


def build_character_stance_faces(base_knife_faces):
    """
    Dựng 1:1 toàn bộ nhân vật Minecraft (đầu, giáp thân, 2 tay, 2 chân) + cặp Dual Knife 0.72x
    bằng ĐÚNG ma trận HumanoidModelMixin + ItemInHandLayerMixin + DualBladeGeoRenderer!
    """
    scene = []

    # 1. Đầu (+11.5° cúi nhẹ, giữ ánh mắt nhìn thẳng!) + mũ/visor đơn giản
    head_faces = build_box_faces_local((-4, -8, -4), (4, 0, 4), (178, 140, 114))
    head_faces += build_box_faces_local((-4.3, -8.3, -4.3), (4.3, -5.6, 4.3), (42, 38, 48))  # Tóc / mũ giáp tối
    head_faces += build_box_faces_local((-2.8, -4.6, -4.15), (-0.9, -3.5, -3.95), (240, 250, 255))  # Mắt phải
    head_faces += build_box_faces_local((+0.9, -4.6, -4.15), (+2.8, -3.5, -3.95), (240, 250, 255))  # Mắt trái
    scene.extend(transform_mc_part(head_faces, (0, 0, 0), 0.20, -0.16, 0.0))

    # 2. Thân (body + áo giáp Netherite tối gọn)
    body_faces = build_box_faces_local((-4, 0, -2), (4, 12, 2), (50, 48, 60))
    body_faces += build_box_faces_local((-4.3, 0.5, -2.3), (4.3, 8.2, 2.3), (36, 33, 44))  # Giáp ngực
    body_faces += build_box_faces_local((-3.2, 2.2, -2.42), (3.2, 3.0, -2.2), (56, 204, 224))  # Đường chỉ giáp mảnh
    scene.extend(transform_mc_part(body_faces, (0, 0, 0), 0.16, 0.24, 0.0))

    # 3. 2 Chân tấn sát thủ
    rleg = build_box_faces_local((-2, 0, -2), (2, 12, 2), (44, 42, 54))
    lleg = build_box_faces_local((-2, 0, -2), (2, 12, 2), (44, 42, 54))
    scene.extend(transform_mc_part(rleg, (-1.9, 12, 0), -0.26, -0.22, 0.16))
    scene.extend(transform_mc_part(lleg, (+1.9, 12, 0), 0.32, 0.24, -0.16))

    # 4. 2 Cánh tay (Upper Tier Left Arm + Lower Tier Right Arm)
    rarm_box = build_box_faces_local((-3, -2, -2), (1, 10, 2), (62, 58, 72))
    rarm_box += build_box_faces_local((-3.05, 7.2, -2.05), (1.05, 10.05, 2.05), (170, 134, 108))  # Bàn tay phải
    larm_box = build_box_faces_local((-1, -2, -2), (3, 10, 2), (62, 58, 72))
    larm_box += build_box_faces_local((-1.05, 7.2, -2.05), (3.05, 10.05, 2.05), (170, 134, 108))  # Bàn tay trái

    r_ax, r_ay, r_az = -0.82, -0.84, -0.10
    l_ax, l_ay, l_az = -1.24,  0.76, -0.08
    scene.extend(transform_mc_part(rarm_box, (-5, 2, 0), r_ax, r_ay, r_az))
    scene.extend(transform_mc_part(larm_box, (+5, 2, 0), l_ax, l_ay, l_az))

    # 5. Đính 2 thanh Dual Knife (0.72x) theo ĐÚNG pipeline ItemInHandLayer + ItemInHandLayerMixin + DualBladeGeoRenderer!
    def attach_knife(is_left: bool, arm_pivot, ax, ay, az, b_pitch_deg, b_roll_deg, b_yaw_deg):
        R_arm = rot_z(az) @ rot_y(ay) @ rot_x(ax)
        R_hand = rot_x(math.radians(-90.0)) @ rot_y(math.radians(180.0))
        t_hand = np.array([-1.0 if is_left else 1.0, 2.0, -10.0], dtype=np.float64)
        R_align = rot_x(math.radians(b_pitch_deg)) @ rot_z(math.radians(b_roll_deg)) @ rot_y(math.radians(b_yaw_deg))
        t_disp = np.array([0.0, 2.0, 1.0], dtype=np.float64)
        s_total = 0.95 * KNIFE_RENDER_SCALE  # dual_knife.json (0.95) * knifeRenderScale (0.72) = 0.684!

        if is_left:
            # DualBladeGeoRenderer Reverse Grip: translate(-0.060*16, 0, +0.015*16) * Rx(112°) * Ry(180°) * Rz(-8°)
            R_rev = rot_x(math.radians(112.0)) @ rot_y(math.radians(180.0)) @ rot_z(math.radians(-8.0))
            t_rev = np.array([-0.060 * 16.0, 0.0, 0.015 * 16.0], dtype=np.float64)
        else:
            R_rev = np.eye(3, dtype=np.float64)
            t_rev = np.zeros(3, dtype=np.float64)

        piv = np.array(arm_pivot, dtype=np.float64)
        for q, uv, col in base_knife_faces:
            # q is in Bedrock model pixels centered at hilt (0,0,0)
            p = q * s_total
            p = (p @ R_rev.T) + t_rev
            p = p + t_disp
            p = p @ R_align.T
            p = ((p + t_hand) @ R_hand.T)
            p = (p @ R_arm.T) + piv
            scene.append((p, uv, col))

    # Tầng trên — Tay trái cầm ngược
    attach_knife(True,  (+5, 2, 0), l_ax, l_ay, l_az, +22.0, -10.0,  -8.0)
    # Tầng dưới — Tay phải cầm thẳng vươn ngang ra phải hông
    attach_knife(False, (-5, 2, 0), r_ax, r_ay, r_az, -22.0, -84.0, +24.0)

    # Áp nhẹ rootPitchDeg (-9.5° đổ chúi về trước quanh tâm chân y=24)
    yup = mc_ydown_to_yup(scene)
    R_root = rot_x(math.radians(-9.5))
    final_faces = []
    for q, uv, col in yup:
        final_faces.append((q @ R_root.T, uv, col))
    return final_faces


def render_previews(geo: dict, diff_img, glow_img, icon_im: Image.Image):
    base_faces = []
    all_pts = []
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
            all_pts.append(corners)
            uv_map = c["uv"]
            for fname, idxs in (
                ("north", [0, 1, 2, 3]), ("south", [5, 4, 7, 6]),
                ("west", [4, 0, 3, 7]), ("east", [1, 5, 6, 2]),
                ("up", [3, 2, 6, 7]), ("down", [4, 5, 1, 0]),
            ):
                base_faces.append((corners[idxs], uv_map[fname], None))

    def raster(faces, view_mat, w, h, sc, center, ambient=0.48, bg=(18, 22, 28)):
        fb = np.zeros((h, w, 3), dtype=np.float32)
        fb[:, :] = bg
        zbuf = np.full((h, w), -1e9, dtype=np.float32)
        ld = np.array([0.38, 0.72, -0.58], dtype=np.float64)
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
                cy_i, cx_i = np.where(cand)
                sub_z[cy_i, cx_i] = z[cy_i, cx_i]
                if flat_rgb is not None:
                    lit = np.array(flat_rgb, dtype=np.float32) * shade
                else:
                    u = np.clip((w0[cand] * t0[0] + w1[cand] * t1[0] + w2[cand] * t2[0]).astype(np.int32), 0, TEX_W - 1)
                    v = np.clip((w0[cand] * t0[1] + w1[cand] * t1[1] + w2[cand] * t2[1]).astype(np.int32), 0, TEX_H - 1)
                    d_c = diff_img[v, u, :3]
                    g_c = glow_img[v, u, :3]
                    g_a = glow_img[v, u, 3:4] / 255.0
                    lit = d_c * shade * (1.0 - g_a * 0.90) + g_c * (g_a * 1.15)
                fb[y0 + cy_i, x0 + cx_i] = np.clip(lit, 0, 255)
        return Image.fromarray(fb.astype(np.uint8), "RGB")

    def cam_mat(yw, pt):
        y, p = math.radians(yw), math.radians(pt)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    # Nhân vật Minecraft full-body cầm cặp Dual Knife (0.72x) trong tư thế vắt chéo 2 tầng!
    char_faces = build_character_stance_faces(base_faces)
    char_ctr = np.array([-1.0, 16.5, -2.5], dtype=np.float64)

    # Panel 3: Cận cảnh Dual Knife (1 thuận + 1 ngược) trong bóng đêm (Selective Glowmask!)
    night_faces = []
    R_nr = bedrock_rot_matrix([-8.0, 0.0, 0.0])
    R_nl = bedrock_rot_matrix([172.0, 180.0, 0.0])
    for q, uv, _ in base_faces:
        night_faces.append(((q @ R_nr.T) + np.array([0.0, 0.0, -10.0]), uv, None))
        night_faces.append(((q @ R_nl.T) + np.array([0.0, 22.0, +10.0]), uv, None))
    night_ctr = np.array([0.0, 11.0, 0.0], dtype=np.float64)

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 520, 440
    # Cam yaw=180 nhìn thẳng chính diện về phía -Z của nhân vật!
    im1 = raster(char_faces, cam_mat(180, 6), pw, ph, 10.6, char_ctr, ambient=0.52, bg=(22, 26, 34))
    im2 = raster(char_faces, cam_mat(140, 10), pw, ph, 10.4, char_ctr, ambient=0.50, bg=(20, 24, 32))
    im3 = raster(night_faces, cam_mat(-82, 6), pw, ph, 8.2, night_ctr, ambient=0.16, bg=(7, 9, 14))

    im4 = Image.new("RGB", (pw, ph), (22, 24, 30))
    dr4 = ImageDraw.Draw(im4)
    dr4.rectangle([26, 94, 250, 318], fill=(34, 34, 42), outline=(80, 96, 110))
    dr4.rectangle([274, 94, 498, 318], fill=(156, 158, 166), outline=(85, 95, 108))
    big_item = icon_im.resize((192, 192), Image.NEAREST)
    im4.paste(big_item, (42, 110), big_item)
    im4.paste(big_item, (290, 110), big_item)
    dr4.text((62, 332), "DARK SLOT (HOTBAR)", fill=(180, 240, 255))
    dr4.text((310, 332), "LIGHT SLOT (CHEST/INV)", fill=(195, 245, 255))

    sheet = Image.new("RGB", (pw * 2, ph * 2), (10, 12, 16))
    sheet.paste(im1, (0, 0))
    sheet.paste(im2, (pw, 0))
    sheet.paste(im3, (0, ph))
    sheet.paste(im4, (pw, ph))

    dr = ImageDraw.Draw(sheet)
    dr.text((10, 10), "FRONT VIEW: 2-TIER CROSSED ASSASSIN STANCE (DUAL KNIFE 0.72x)", fill=(130, 242, 255))
    dr.text((pw + 10, 10), "3/4 VIEW: CLEAR CHEST/HEAD GAP - ZERO ARMOR/FACE CLIPPING", fill=(130, 242, 255))
    dr.text((10, ph + 10), "NIGHT SELECTIVE GLOWMASK (DAMASCUS NETHERITE + CYAN EDGE)", fill=(130, 242, 255))
    dr.text((pw + 10, ph + 10), "32x32 DUAL KNIFE ICON (STANDARD + REVERSE GRIP, 1PX BORDER)", fill=(130, 242, 255))
    out_preview = PREVIEW_DIR / "dual_knife_stance_preview.png"
    sheet.save(out_preview)
    print(f"PREVIEW_OK: {out_preview}")


if __name__ == "__main__":
    geo_data, cubes = build_dual_knife_geo()
    build_dual_knife_animations()
    diff_img, glow_img = build_netherite_diamond_texture_64x64()
    icon_im = build_inventory_icon_32x32()
    render_previews(geo_data, diff_img, glow_img, icon_im)
    print(f"ALL_DONE: Dual Knife generated ({cubes} cubes, scale=0.72, 64x64 selective glowmask, 32x32 X-icon)")
