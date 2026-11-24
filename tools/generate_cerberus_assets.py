#!/usr/bin/env python3
"""
Sinh trọn bộ tài nguyên GeckoLib 4.x & texture cho Boss Cerberus (abyssalrpg:cerberus):
- assets/abyssalrpg/geo/entity/cerberus.geo.json        (Dạng 3 đầu Phase 1)
- assets/abyssalrpg/geo/entity/cerberus_single.geo.json (Dạng 1 đầu Phase 2)
- assets/abyssalrpg/animations/entity/cerberus.animation.json (17 animation chuẩn, kiểm tra mọi góc <= 95°)
- 10 texture 256x256 (cerberus, cerberus_enraged, cerberus_alpha, cerberus_fire, cerberus_shadow + 5 *_glowmask.png)
- assets/abyssalrpg/textures/item/cerberus_hellfang.png (16x16)
"""

import json
import math
import os
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"


def write_png(path: Path, width: int, height: int, rgba_bytes: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)  # filter type 0
        start = y * stride
        raw.extend(rgba_bytes[start:start + stride])

    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b"")
    path.write_bytes(png)


def make_uv_box(origin, size, region_uv, north_uv=None):
    """Per-face Bedrock UV mapping cleanly referencing regions on our 256x256 texture atlas."""
    u, v, uw, vh = region_uv
    nu, nv, nuw, nvh = north_uv if north_uv else region_uv
    return {
        "origin": origin,
        "size": size,
        "uv": {
            "north": {"uv": [nu, nv], "uv_size": [nuw, nvh]},
            "south": {"uv": [u, v], "uv_size": [uw, vh]},
            "east": {"uv": [u, v], "uv_size": [uw, vh]},
            "west": {"uv": [u, v], "uv_size": [uw, vh]},
            "up": {"uv": [u, v], "uv_size": [uw, vh]},
            "down": {"uv": [u, v], "uv_size": [uw, vh]},
        },
    }


def build_cerberus_geo(single: bool) -> dict:
    UV_HIDE = (4, 4, 120, 120)       # Vùng 1: Da thú hắc diện thạch + vân nứt dung nham
    UV_CORE = (132, 4, 120, 120)     # Vùng 2: Lồng ngực / khe lửa phát sáng mạnh
    UV_FACE = (4, 132, 120, 120)     # Vùng 3: Mặt sói có 2 mắt phát sáng (cho mặt north của đầu)
    UV_FANG = (132, 132, 120, 120)   # Vùng 4: Nanh, móng, bờm gai và chóp đuôi lửa

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {
            "name": "body",
            "parent": "root",
            "pivot": [0, 14, 0],
            "cubes": [
                make_uv_box([-9, 10, -8] if single else [-10, 10, -8],
                            [18, 12, 22] if single else [20, 13, 22], UV_HIDE),
                make_uv_box([-6, 11, -5], [12, 10, 16], UV_CORE),
            ],
        },
        {
            "name": "chest",
            "parent": "body",
            "pivot": [0, 18, -8],
            "cubes": [
                make_uv_box([-10, 11, -18] if single else [-12, 11, -19],
                            [20, 14, 13] if single else [24, 15, 14], UV_HIDE),
                make_uv_box([-8, 12, -19.5] if single else [-10, 12, -20.5],
                            [16, 11, 3] if single else [20, 12, 3], UV_CORE),
                # Bờm gai dọc sống lưng
                make_uv_box([-2, 24, -17], [4, 5, 11], UV_FANG),
            ],
        },
    ]

    # 3 Cổ + Đầu + Hàm (dạng single chỉ dựng neck_2/head_2/jaw_2 giữa ngực cân đối)
    head_specs = [
        # (idx, neck_pivot, head_pivot, jaw_pivot, x_off, y_lift, z_off, yaw_base)
        (1, [-9.0, 20.0, -14.0], [-10.5, 26.5, -22.0], [-10.5, 24.0, -23.0], -10.5, 26.5, -22.0),
        (2, [0.0, 22.0, -16.0],  [0.0, 30.5, -24.0],   [0.0, 27.5, -25.0],   0.0,   30.5, -24.0),
        (3, [9.0, 20.0, -14.0],  [10.5, 26.5, -22.0],  [10.5, 24.0, -23.0],  10.5,  26.5, -22.0),
    ]

    for idx, n_piv, h_piv, j_piv, hx, hy, hz in head_specs:
        if single and idx != 2:
            continue
        # Dạng 1 đầu phóng nhẹ đầu giữa thêm ~12% cho cân xứng bờ vai
        hw = 9.0 if (single and idx == 2) else 8.0
        hh = 8.0 if (single and idx == 2) else 7.5
        bones.append({
            "name": f"neck_{idx}",
            "parent": "chest",
            "pivot": n_piv,
            "cubes": [
                make_uv_box([n_piv[0] - 3.5, n_piv[1] - 2.0, n_piv[2] - 8.5], [7, 8, 10], UV_HIDE),
                make_uv_box([n_piv[0] - 1.5, n_piv[1] + 5.0, n_piv[2] - 7.0], [3, 4, 8], UV_FANG),
            ],
        })
        bones.append({
            "name": f"head_{idx}",
            "parent": f"neck_{idx}",
            "pivot": h_piv,
            "cubes": [
                # Hộp sọ chính (mặt north có đôi mắt phát sáng)
                make_uv_box([hx - hw / 2.0, hy - 2.5, hz - 6.0], [hw, hh, 9], UV_HIDE, north_uv=UV_FACE),
                # Mõm trên
                make_uv_box([hx - hw * 0.36, hy - 1.5, hz - 12.5], [hw * 0.72, 4.2, 7], UV_HIDE),
                # Hai tai nhọn + sừng lửa
                make_uv_box([hx - hw * 0.45, hy + hh - 2.5, hz - 2.0], [2.2, 5.0, 2.5], UV_FANG),
                make_uv_box([hx + hw * 0.45 - 2.2, hy + hh - 2.5, hz - 2.0], [2.2, 5.0, 2.5], UV_FANG),
                # Răng nanh trên
                make_uv_box([hx - hw * 0.30, hy - 3.0, hz - 12.0], [1.4, 2.2, 1.4], UV_FANG),
                make_uv_box([hx + hw * 0.30 - 1.4, hy - 3.0, hz - 12.0], [1.4, 2.2, 1.4], UV_FANG),
            ],
        })
        bones.append({
            "name": f"jaw_{idx}",
            "parent": f"head_{idx}",
            "pivot": j_piv,
            "cubes": [
                make_uv_box([hx - hw * 0.32, j_piv[1] - 2.2, hz - 11.8], [hw * 0.64, 2.6, 8.5], UV_CORE),
                make_uv_box([hx - hw * 0.25, j_piv[1] + 0.1, hz - 11.2], [1.2, 1.6, 1.2], UV_FANG),
                make_uv_box([hx + hw * 0.25 - 1.2, j_piv[1] + 0.1, hz - 11.2], [1.2, 1.6, 1.2], UV_FANG),
            ],
        })

    # 4 Chân vạm vỡ + móng lửa
    leg_specs = [
        ("leg_fl", [-9.5 if not single else -8.5, 14.0, -11.0]),
        ("leg_fr", [9.5 if not single else 8.5, 14.0, -11.0]),
        ("leg_bl", [-8.5 if not single else -7.5, 14.0, 10.5]),
        ("leg_br", [8.5 if not single else 7.5, 14.0, 10.5]),
    ]
    for name, piv in leg_specs:
        px, py, pz = piv
        bones.append({
            "name": name,
            "parent": "body",
            "pivot": piv,
            "cubes": [
                make_uv_box([px - 2.8, 4.0, pz - 3.0], [5.6, 12.0, 6.0], UV_HIDE),
                make_uv_box([px - 3.2, 0.0, pz - 4.5], [6.4, 4.2, 7.8], UV_FANG),
            ],
        })

    # Đuôi 3 đốt rực lửa
    bones.extend([
        {
            "name": "tail_1",
            "parent": "body",
            "pivot": [0, 16, 14],
            "cubes": [make_uv_box([-2.5, 14, 13], [5, 5, 9], UV_HIDE)],
        },
        {
            "name": "tail_2",
            "parent": "tail_1",
            "pivot": [0, 17, 22],
            "cubes": [make_uv_box([-2.0, 15, 21], [4, 4, 9], UV_CORE)],
        },
        {
            "name": "tail_3",
            "parent": "tail_2",
            "pivot": [0, 18, 30],
            "cubes": [make_uv_box([-2.8, 16, 29], [5.6, 5.6, 8], UV_FANG)],
        },
    ])

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.cerberus_single" if single else "geometry.cerberus",
                    "texture_width": 256,
                    "texture_height": 256,
                    "visible_bounds_width": 10,
                    "visible_bounds_height": 8,
                    "visible_bounds_offset": [0, 3.0, 0],
                },
                "bones": bones,
            }
        ],
    }


def kf(points: dict) -> dict:
    return {str(t): {"vector": v, "easing": "easeInOutSine"} for t, v in points.items()}


def build_cerberus_animations() -> dict:
    anims = {
        "animation.cerberus.idle": {
            "loop": True,
            "animation_length": 3.0,
            "bones": {
                "body": {"position": kf({"0.0": [0, 0, 0], "1.5": [0, 0.7, 0], "3.0": [0, 0, 0]})},
                "chest": {"rotation": kf({"0.0": [0, 0, 0], "1.5": [2.5, 0, 0], "3.0": [0, 0, 0]})},
                "neck_1": {"rotation": kf({"0.0": [0, -8, 0], "1.5": [4, -4, 2], "3.0": [0, -8, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "1.5": [-3.5, 0, 0], "3.0": [0, 0, 0]})},
                "neck_3": {"rotation": kf({"0.0": [0, 8, 0], "1.5": [4, 4, -2], "3.0": [0, 8, 0]})},
                "jaw_1": {"rotation": kf({"0.0": [5, 0, 0], "1.5": [13, 0, 0], "3.0": [5, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [7, 0, 0], "1.5": [16, 0, 0], "3.0": [7, 0, 0]})},
                "jaw_3": {"rotation": kf({"0.0": [5, 0, 0], "1.5": [13, 0, 0], "3.0": [5, 0, 0]})},
                "tail_1": {"rotation": kf({"0.0": [10, -14, 0], "1.5": [14, 14, 0], "3.0": [10, -14, 0]})},
                "tail_2": {"rotation": kf({"0.0": [8, -18, 0], "1.5": [12, 18, 0], "3.0": [8, -18, 0]})},
                "tail_3": {"rotation": kf({"0.0": [6, -22, 0], "1.5": [10, 22, 0], "3.0": [6, -22, 0]})},
            },
        },
        "animation.cerberus.move": {
            "loop": True,
            "animation_length": 0.8,
            "bones": {
                "body": {
                    "position": kf({"0.0": [0, 0, 0], "0.2": [0, 1.8, 0], "0.4": [0, 0, 0], "0.6": [0, 1.5, 0], "0.8": [0, 0, 0]}),
                    "rotation": kf({"0.0": [-4, 0, 0], "0.4": [4, 0, 0], "0.8": [-4, 0, 0]}),
                },
                "leg_fl": {"rotation": kf({"0.0": [-34, 0, 0], "0.4": [32, 0, 0], "0.8": [-34, 0, 0]})},
                "leg_fr": {"rotation": kf({"0.0": [32, 0, 0], "0.4": [-34, 0, 0], "0.8": [32, 0, 0]})},
                "leg_bl": {"rotation": kf({"0.0": [30, 0, 0], "0.4": [-32, 0, 0], "0.8": [30, 0, 0]})},
                "leg_br": {"rotation": kf({"0.0": [-32, 0, 0], "0.4": [30, 0, 0], "0.8": [-32, 0, 0]})},
                "neck_2": {"rotation": kf({"0.0": [6, 0, 0], "0.4": [-4, 0, 0], "0.8": [6, 0, 0]})},
                "tail_1": {"rotation": kf({"0.0": [18, -12, 0], "0.4": [8, 12, 0], "0.8": [18, -12, 0]})},
            },
        },
        "animation.cerberus.bite_1": {
            "animation_length": 0.7,
            "bones": {
                "neck_1": {"rotation": kf({"0.0": [0, -8, 0], "0.2": [-18, -22, 0], "0.4": [24, 6, 0], "0.7": [0, -8, 0]})},
                "head_1": {"rotation": kf({"0.0": [0, 0, 0], "0.2": [-16, 0, 0], "0.4": [18, 0, 0], "0.7": [0, 0, 0]})},
                "jaw_1": {"rotation": kf({"0.0": [5, 0, 0], "0.22": [48, 0, 0], "0.4": [2, 0, 0], "0.7": [5, 0, 0]})},
            },
        },
        "animation.cerberus.bite_2": {
            "animation_length": 0.75,
            "bones": {
                "chest": {"rotation": kf({"0.0": [0, 0, 0], "0.25": [-10, 0, 0], "0.45": [14, 0, 0], "0.75": [0, 0, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "0.25": [-22, 0, 0], "0.45": [28, 0, 0], "0.75": [0, 0, 0]})},
                "head_2": {"rotation": kf({"0.0": [0, 0, 0], "0.25": [-18, 0, 0], "0.45": [20, 0, 0], "0.75": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.25": [54, 0, 0], "0.45": [0, 0, 0], "0.75": [6, 0, 0]})},
            },
        },
        "animation.cerberus.bite_3": {
            "animation_length": 0.7,
            "bones": {
                "neck_3": {"rotation": kf({"0.0": [0, 8, 0], "0.2": [-18, 22, 0], "0.4": [24, -6, 0], "0.7": [0, 8, 0]})},
                "head_3": {"rotation": kf({"0.0": [0, 0, 0], "0.2": [-16, 0, 0], "0.4": [18, 0, 0], "0.7": [0, 0, 0]})},
                "jaw_3": {"rotation": kf({"0.0": [5, 0, 0], "0.22": [48, 0, 0], "0.4": [2, 0, 0], "0.7": [5, 0, 0]})},
            },
        },
        "animation.cerberus.breath": {
            "animation_length": 2.4,
            "bones": {
                "chest": {"rotation": kf({"0.0": [0, 0, 0], "0.5": [-14, 0, 0], "1.0": [8, 0, 0], "2.0": [8, 0, 0], "2.4": [0, 0, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "0.5": [-18, 0, 0], "1.0": [10, -24, 0], "1.6": [10, 24, 0], "2.4": [0, 0, 0]})},
                "head_2": {"rotation": kf({"0.0": [0, 0, 0], "0.6": [-10, 0, 0], "1.3": [8, 0, 0], "2.4": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.6": [56, 0, 0], "2.0": [52, 0, 0], "2.4": [6, 0, 0]})},
            },
        },
        "animation.cerberus.pounce": {
            "animation_length": 1.6,
            "bones": {
                "body": {
                    "position": kf({"0.0": [0, 0, 0], "0.5": [0, -4.5, 2], "0.9": [0, 6.0, -3], "1.3": [0, -2.5, 0], "1.6": [0, 0, 0]}),
                    "rotation": kf({"0.0": [0, 0, 0], "0.5": [16, 0, 0], "0.9": [-22, 0, 0], "1.3": [12, 0, 0], "1.6": [0, 0, 0]}),
                },
                "leg_fl": {"rotation": kf({"0.0": [0, 0, 0], "0.5": [28, 0, 0], "0.9": [-48, 0, 0], "1.6": [0, 0, 0]})},
                "leg_fr": {"rotation": kf({"0.0": [0, 0, 0], "0.5": [28, 0, 0], "0.9": [-48, 0, 0], "1.6": [0, 0, 0]})},
                "leg_bl": {"rotation": kf({"0.0": [0, 0, 0], "0.5": [-26, 0, 0], "0.9": [46, 0, 0], "1.6": [0, 0, 0]})},
                "leg_br": {"rotation": kf({"0.0": [0, 0, 0], "0.5": [-26, 0, 0], "0.9": [46, 0, 0], "1.6": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.9": [48, 0, 0], "1.6": [6, 0, 0]})},
            },
        },
        "animation.cerberus.slam": {
            "animation_length": 1.6,
            "bones": {
                "body": {
                    "rotation": kf({"0.0": [0, 0, 0], "0.65": [-34, 0, 0], "1.0": [14, 0, 0], "1.6": [0, 0, 0]}),
                    "position": kf({"0.0": [0, 0, 0], "0.65": [0, 5.5, 2], "1.0": [0, -2.5, -2], "1.6": [0, 0, 0]}),
                },
                "leg_fl": {"rotation": kf({"0.0": [0, 0, 0], "0.65": [-58, 0, 0], "1.0": [24, 0, 0], "1.6": [0, 0, 0]})},
                "leg_fr": {"rotation": kf({"0.0": [0, 0, 0], "0.65": [-58, 0, 0], "1.0": [24, 0, 0], "1.6": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.65": [46, 0, 0], "1.6": [6, 0, 0]})},
            },
        },
        "animation.cerberus.howl": {
            "animation_length": 1.8,
            "bones": {
                "chest": {"rotation": kf({"0.0": [0, 0, 0], "0.6": [-22, 0, 0], "1.3": [-22, 0, 0], "1.8": [0, 0, 0]})},
                "neck_1": {"rotation": kf({"0.0": [0, -8, 0], "0.6": [-36, -16, 0], "1.3": [-36, -16, 0], "1.8": [0, -8, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "0.6": [-42, 0, 0], "1.3": [-42, 0, 0], "1.8": [0, 0, 0]})},
                "neck_3": {"rotation": kf({"0.0": [0, 8, 0], "0.6": [-36, 16, 0], "1.3": [-36, 16, 0], "1.8": [0, 8, 0]})},
                "jaw_1": {"rotation": kf({"0.0": [5, 0, 0], "0.6": [50, 0, 0], "1.4": [48, 0, 0], "1.8": [5, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.6": [54, 0, 0], "1.4": [50, 0, 0], "1.8": [6, 0, 0]})},
                "jaw_3": {"rotation": kf({"0.0": [5, 0, 0], "0.6": [50, 0, 0], "1.4": [48, 0, 0], "1.8": [5, 0, 0]})},
            },
        },
        "animation.cerberus.phase_change": {
            "animation_length": 2.0,
            "bones": {
                "body": {
                    "position": kf({"0.0": [0, 0, 0], "0.4": [0, -3.5, 0], "1.1": [0, 4.5, 0], "2.0": [0, 0, 0]}),
                    "rotation": kf({"0.0": [0, 0, 0], "0.4": [14, 0, 0], "1.1": [-26, 0, 0], "2.0": [0, 0, 0]}),
                },
                "neck_1": {"rotation": kf({"0.0": [0, -8, 0], "1.1": [-32, -28, 0], "2.0": [0, -8, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "1.1": [-38, 0, 0], "2.0": [0, 0, 0]})},
                "neck_3": {"rotation": kf({"0.0": [0, 8, 0], "1.1": [-32, 28, 0], "2.0": [0, 8, 0]})},
                "jaw_1": {"rotation": kf({"0.0": [5, 0, 0], "1.1": [56, 0, 0], "2.0": [10, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "1.1": [58, 0, 0], "2.0": [12, 0, 0]})},
                "jaw_3": {"rotation": kf({"0.0": [5, 0, 0], "1.1": [56, 0, 0], "2.0": [10, 0, 0]})},
            },
        },
        "animation.cerberus.split_fireball": {
            "loop": True,
            "animation_length": 0.6,
            "bones": {
                "body": {"scale": kf({"0.0": [0.45, 0.45, 0.45], "0.3": [0.55, 0.55, 0.55], "0.6": [0.45, 0.45, 0.45]})},
                "chest": {"rotation": kf({"0.0": [20, 0, 0], "0.6": [20, 0, 0]})},
            },
        },
        "animation.cerberus.land_spawn": {
            "animation_length": 1.5,
            "bones": {
                "body": {
                    "position": kf({"0.0": [0, -5.0, 0], "0.5": [0, 2.5, 0], "1.0": [0, 0.5, 0], "1.5": [0, 0, 0]}),
                    "rotation": kf({"0.0": [20, 0, 0], "0.6": [-20, 0, 0], "1.5": [0, 0, 0]}),
                },
                "neck_2": {"rotation": kf({"0.0": [22, 0, 0], "0.65": [-34, 0, 0], "1.5": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [4, 0, 0], "0.65": [56, 0, 0], "1.5": [8, 0, 0]})},
            },
        },
        "animation.cerberus.nova": {
            "animation_length": 1.8,
            "bones": {
                "body": {"position": kf({"0.0": [0, 0, 0], "0.7": [0, -4.0, 0], "1.0": [0, 3.5, 0], "1.8": [0, 0, 0]})},
                "chest": {"rotation": kf({"0.0": [0, 0, 0], "0.7": [18, 0, 0], "1.0": [-24, 0, 0], "1.8": [0, 0, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "0.7": [24, 0, 0], "1.0": [-32, 0, 0], "1.8": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "1.0": [58, 0, 0], "1.8": [6, 0, 0]})},
            },
        },
        "animation.cerberus.dash": {
            "animation_length": 1.4,
            "bones": {
                "body": {
                    "position": kf({"0.0": [0, 0, 0], "0.5": [0, -3.5, 3], "0.85": [0, 1.0, -5], "1.4": [0, 0, 0]}),
                    "rotation": kf({"0.0": [0, 0, 0], "0.5": [14, 0, 0], "0.85": [-8, 0, 0], "1.4": [0, 0, 0]}),
                },
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "0.85": [18, 0, 0], "1.4": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.85": [46, 0, 0], "1.4": [6, 0, 0]})},
            },
        },
        "animation.cerberus.shadow_step": {
            "animation_length": 1.1,
            "bones": {
                "body": {"position": kf({"0.0": [0, 0, 0], "0.35": [0, -5.0, 0], "0.7": [0, 2.0, -3], "1.1": [0, 0, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "0.7": [24, 0, 0], "1.1": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.7": [48, 0, 0], "1.1": [6, 0, 0]})},
            },
        },
        "animation.cerberus.pack_hunt": {
            "animation_length": 2.4,
            "bones": {
                "body": {
                    "position": kf({"0.0": [0, 0, 0], "0.7": [0, -4.0, 2], "1.3": [0, 2.0, -5], "2.4": [0, 0, 0]}),
                    "rotation": kf({"0.0": [0, 0, 0], "0.7": [16, 0, 0], "1.3": [-12, 0, 0], "2.4": [0, 0, 0]}),
                },
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "0.7": [-18, 0, 0], "1.3": [22, 0, 0], "2.4": [0, 0, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "0.7": [42, 0, 0], "1.3": [52, 0, 0], "2.4": [6, 0, 0]})},
            },
        },
        "animation.cerberus.death": {
            "animation_length": 2.6,
            "bones": {
                "body": {
                    "position": kf({"0.0": [0, 0, 0], "1.0": [0, -6.0, 0], "2.6": [0, -11.0, 0]}),
                    "rotation": kf({"0.0": [0, 0, 0], "1.0": [12, 0, 20], "2.6": [8, 0, 78]}),
                },
                "neck_1": {"rotation": kf({"0.0": [0, -8, 0], "2.6": [34, -18, 0]})},
                "neck_2": {"rotation": kf({"0.0": [0, 0, 0], "2.6": [38, 0, 0]})},
                "neck_3": {"rotation": kf({"0.0": [0, 8, 0], "2.6": [34, 18, 0]})},
                "jaw_2": {"rotation": kf({"0.0": [6, 0, 0], "2.6": [30, 0, 0]})},
            },
        },
    }

    # Kiểm tra cứng: không có bất kỳ keyframe xoay nào > 180° (chống lỗi quay đầu nhiều vòng)
    for anim_name, anim_data in anims.items():
        for bone_name, channels in anim_data.get("bones", {}).items():
            rot = channels.get("rotation")
            if isinstance(rot, dict):
                for ts, frame in rot.items():
                    vec = frame["vector"]
                    for axis_val in vec:
                        assert abs(axis_val) <= 95.0, f"Rotation quá lớn tại {anim_name}.{bone_name}@{ts}: {vec}"

    return {"format_version": "1.8.0", "animations": anims}


def generate_entity_texture_pair(base_name: str, hide_rgb, vein_rgb, eye_rgb, glow_strength: float) -> None:
    """Sinh cặp diffuse 256x256 + glowmask 256x256 khớp 4 vùng UV của cerberus.geo.json."""
    w, h = 256, 256
    diff = bytearray(w * h * 4)
    glow = bytearray(w * h * 4)

    def put(buf, x, y, r, g, b, a=255):
        idx = (y * w + x) * 4
        buf[idx] = max(0, min(255, int(r)))
        buf[idx + 1] = max(0, min(255, int(g)))
        buf[idx + 2] = max(0, min(255, int(b)))
        buf[idx + 3] = max(0, min(255, int(a)))

    for y in range(h):
        for x in range(w):
            # Nhiễu hữu cơ nhẹ cho lông thú đá núi lửa
            n = math.sin(x * 0.23 + y * 0.17) * math.cos(x * 0.11 - y * 0.29)
            shade = 0.86 + 0.16 * n
            if x < 128 and y < 128:
                # Vùng 1: Da thú obsidian + khe nứt dung nham phát sáng mảnh
                crack = abs(math.sin((x + y * 1.3) * 0.14) + math.cos((x * 0.9 - y) * 0.12)) < 0.14
                if crack:
                    put(diff, x, y, vein_rgb[0], vein_rgb[1], vein_rgb[2], 255)
                    put(glow, x, y, vein_rgb[0] * glow_strength, vein_rgb[1] * glow_strength, vein_rgb[2] * glow_strength, 220)
                else:
                    put(diff, x, y, hide_rgb[0] * shade, hide_rgb[1] * shade, hide_rgb[2] * shade, 255)
                    put(glow, x, y, 0, 0, 0, 0)
            elif x >= 128 and y < 128:
                # Vùng 2: Lồng ngực / hàm nóng chảy sáng rực
                lx, ly = x - 128, y
                pulse = 0.55 + 0.45 * math.sin(lx * 0.15) * math.cos(ly * 0.15)
                r = hide_rgb[0] * (1 - pulse) + vein_rgb[0] * pulse
                g = hide_rgb[1] * (1 - pulse) + vein_rgb[1] * pulse
                b = hide_rgb[2] * (1 - pulse) + vein_rgb[2] * pulse
                put(diff, x, y, r, g, b, 255)
                if pulse > 0.58:
                    put(glow, x, y, vein_rgb[0], vein_rgb[1], vein_rgb[2], int(240 * pulse))
                else:
                    put(glow, x, y, 0, 0, 0, 0)
            elif x < 128 and y >= 128:
                # Vùng 3: Mặt sói (hai mắt rực sáng cân xứng theo vai trò!)
                lx, ly = x, y - 128
                left_eye = (22 <= lx <= 48) and (36 <= ly <= 58)
                right_eye = (80 <= lx <= 106) and (36 <= ly <= 58)
                brow_rune = (56 <= lx <= 72) and (18 <= ly <= 42) and ((lx + ly) % 3 == 0)
                if left_eye or right_eye:
                    put(diff, x, y, eye_rgb[0], eye_rgb[1], eye_rgb[2], 255)
                    put(glow, x, y, eye_rgb[0], eye_rgb[1], eye_rgb[2], 255)
                elif brow_rune:
                    put(diff, x, y, vein_rgb[0], vein_rgb[1], vein_rgb[2], 255)
                    put(glow, x, y, vein_rgb[0], vein_rgb[1], vein_rgb[2], 230)
                else:
                    put(diff, x, y, hide_rgb[0] * shade * 0.92, hide_rgb[1] * shade * 0.92, hide_rgb[2] * shade * 0.92, 255)
                    put(glow, x, y, 0, 0, 0, 0)
            else:
                # Vùng 4: Nanh, móng vuốt, gai lưng & chóp đuôi lửa
                lx, ly = x - 128, y - 128
                tip = ly < 56 or (lx + ly) % 11 < 3
                if tip:
                    put(diff, x, y, eye_rgb[0], eye_rgb[1], eye_rgb[2], 255)
                    put(glow, x, y, eye_rgb[0], eye_rgb[1], eye_rgb[2], 235)
                else:
                    put(diff, x, y, hide_rgb[0] * 1.25, hide_rgb[1] * 1.15, hide_rgb[2] * 1.15, 255)
                    put(glow, x, y, 0, 0, 0, 0)

    tex_dir = ASSETS / "textures" / "entity"
    write_png(tex_dir / f"{base_name}.png", w, h, bytes(diff))
    write_png(tex_dir / f"{base_name}_glowmask.png", w, h, bytes(glow))


def generate_hellfang_icon() -> None:
    w, h = 16, 16
    buf = bytearray(w * h * 4)
    # Hình nanh cong rực lửa 16x16
    coords = {
        (11, 2): (255, 225, 160), (10, 3): (255, 200, 110), (11, 3): (255, 130, 40),
        (9, 4): (255, 180, 90),   (10, 4): (240, 100, 30),  (11, 4): (180, 45, 20),
        (8, 5): (255, 170, 80),   (9, 5): (230, 85, 25),    (10, 5): (140, 30, 18),
        (7, 6): (250, 150, 70),   (8, 6): (210, 65, 22),    (9, 6): (110, 22, 16),
        (6, 7): (245, 135, 60),   (7, 7): (185, 50, 20),    (8, 7): (85, 18, 16),
        (5, 8): (240, 120, 50),   (6, 8): (160, 40, 20),    (7, 8): (65, 14, 16),
        (4, 9): (235, 105, 45),   (5, 9): (135, 32, 18),    (6, 9): (55, 14, 16),
        (4, 10): (215, 85, 35),   (5, 10): (100, 24, 18),
        (3, 11): (195, 65, 28),   (4, 11): (80, 20, 18),
        (3, 12): (165, 45, 24),   (2, 13): (130, 30, 20),
    }
    for (x, y), (r, g, b) in coords.items():
        idx = (y * w + x) * 4
        buf[idx:idx + 4] = bytes([r, g, b, 255])
    write_png(ASSETS / "textures" / "item" / "cerberus_hellfang.png", w, h, bytes(buf))


def main() -> None:
    geo_dir = ASSETS / "geo" / "entity"
    anim_dir = ASSETS / "animations" / "entity"
    geo_dir.mkdir(parents=True, exist_ok=True)
    anim_dir.mkdir(parents=True, exist_ok=True)

    (geo_dir / "cerberus.geo.json").write_text(json.dumps(build_cerberus_geo(False), indent=2), encoding="utf-8")
    (geo_dir / "cerberus_single.geo.json").write_text(json.dumps(build_cerberus_geo(True), indent=2), encoding="utf-8")
    (anim_dir / "cerberus.animation.json").write_text(json.dumps(build_cerberus_animations(), indent=2), encoding="utf-8")

    # 5 biến thể texture + 5 glowmask tương ứng
    generate_entity_texture_pair("cerberus",         (34, 24, 28), (245, 110, 25), (255, 180, 45), 0.90)
    generate_entity_texture_pair("cerberus_enraged", (46, 16, 18), (255, 48, 28),  (255, 95, 35),  1.00)
    generate_entity_texture_pair("cerberus_alpha",   (42, 14, 16), (255, 28, 42),  (255, 25, 35),  1.00)  # Mắt đỏ (Thủ lĩnh)
    generate_entity_texture_pair("cerberus_fire",    (44, 24, 14), (255, 145, 20), (255, 165, 25), 1.00)  # Mắt cam (Lửa)
    generate_entity_texture_pair("cerberus_shadow",  (22, 14, 34), (175, 45, 255), (205, 65, 255), 1.00)  # Mắt tím (Bóng tối)
    generate_hellfang_icon()

    print("CERBERUS_ASSETS_OK: 2 geo + 1 animation (17 clips, all rot <= 95 deg) + 10 entity PNG + 1 item PNG generated.")


if __name__ == "__main__":
    main()

