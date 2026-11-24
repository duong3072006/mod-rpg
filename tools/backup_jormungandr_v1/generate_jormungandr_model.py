#!/usr/bin/env python3
"""
Bộ sinh Model + Texture (1024x1024, ~3 texel/px) + Animation JSON NGUYÊN BẢN
cho Boss Jörmungandr (abyssalrpg:jormungandr) — Rắn Hổ Mang Thần Thoại Khổng Lồ.

- KHÔNG dùng, KHÔNG tham chiếu bất kỳ model, ảnh hay texture nào bên ngoài.
- Kiến trúc bone PHẲNG bắt buộc cho SerpentPath:
    head (con: jaw, tongue, horn_l, horn_r, hood_l, hood_r)
    segment_1 .. segment_42 (mỗi đốt là bone top-level phẳng, mặt cắt gần bát giác gồm 3 cube xoay + sống vảy + bụng)
    tail_tip (gai sừng nhọn + chuỗi 4 đốt xương rung)
- Tổng cube: ~258 (< 900), tránh z-fighting bằng độ lệch bán kính vi mô.
- Tự động sao lưu asset Jörmungandr cũ vào tools/backup_jormungandr/ trước khi ghi đè.

Chạy: python tools/generate_jormungandr_model.py
"""

import json
import math
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
BACKUP_DIR = ROOT / "tools" / "backup_jormungandr"

TEX_W = 1024
TEX_H = 1024
N_MAX = 42  # Đủ cho Phase 1 (28 đốt ~32 blk) lẫn Phase 2 (39 đốt ~44.8 blk) + biên cấu hình

# Vùng vật liệu trên atlas 1024x1024 (tọa độ px): (x0, y0, w, h)
MAT = {
    "SCALE_BACK":  (0,   0,   256, 256),  # Lưng vảy tròn chồng lớp (#17171C / #2C2C36 / #0C0C10)
    "BELLY":       (256, 0,   256, 256),  # Bụng xanh tím chuyển sắc (#4F5FC8 -> #7F8CE6)
    "HOOD":        (512, 0,   256, 256),  # Mang cổ bành tím nhạt (#B7A6EE, viền #8A74D9, lỗ nhỏ)
    "HEAD_SCALE":  (768, 0,   256, 256),  # Vảy đầu đen bóng + khe mắt xiên đỏ (#E01818)
    "EYE_SLIT":    (0,   256, 128, 128),  # Mắt đỏ khe xiên phát sáng (Phase 2 trắng-đỏ)
    "FANG_BONE":   (128, 256, 128, 128),  # Răng nanh trắng (#F2EEE6) & đốt xương đuôi
    "MOUTH_RED":   (256, 256, 192, 192),  # Trong miệng & lưỡi chẻ đôi đỏ (#C81E1E)
    "HORN_DARK":   (448, 256, 128, 128),  # Sừng đen cong & gai đuôi
    "TAIL_BONE":   (576, 256, 192, 192),  # Đốt xương đuôi & khớp sừng
}


def backup_existing_files() -> None:
    files = [
        ASSETS / "geo" / "entity" / "jormungandr.geo.json",
        ASSETS / "animations" / "entity" / "jormungandr.animation.json",
        ASSETS / "textures" / "entity" / "jormungandr.png",
        ASSETS / "textures" / "entity" / "jormungandr_glowmask.png",
        ASSETS / "textures" / "entity" / "jormungandr_enraged.png",
        ASSETS / "textures" / "entity" / "jormungandr_enraged_glowmask.png",
    ]
    copied = 0
    for f in files:
        if f.exists():
            dst = BACKUP_DIR / f.relative_to(ASSETS)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dst)
            copied += 1
    if copied > 0:
        print(f"BACKUP_OK: Đã sao lưu {copied} file cũ vào {BACKUP_DIR}")


def face_uv_box(mat_key: str, u_pad: int = 8) -> dict:
    x0, y0, w, h = MAT[mat_key]
    ux, uy, uw, uh = x0 + u_pad, y0 + u_pad, w - 2 * u_pad, h - 2 * u_pad
    f = {"uv": [ux, uy], "uv_size": [uw, uh]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def segment_uv_box(belly_bottom: bool = True) -> dict:
    sb = MAT["SCALE_BACK"]
    bl = MAT["BELLY"]
    top = {"uv": [sb[0] + 8, sb[1] + 8], "uv_size": [sb[2] - 16, sb[3] - 16]}
    bot = {"uv": [bl[0] + 8, bl[1] + 8], "uv_size": [bl[2] - 16, bl[3] - 16]} if belly_bottom else top
    return {"north": top, "south": top, "east": top, "west": top, "up": top, "down": bot}


def build_geo() -> int:
    bones = []

    # -------------------------------------------------------------------------
    # 1. ĐẦU RẮN HỔ MANG (hình tam giác dẹt, răng nanh dài, mắt xiên, sừng cong,
    #    hàm dưới riêng, lưỡi chẻ đôi, hai cánh mang cổ hood_l / hood_r)
    #    Tất cả ở tọa độ gốc [0, 0, 0], hướng nhìn về -Z (quy ước Bedrock/GeckoLib).
    # -------------------------------------------------------------------------
    head_cubes = [
        # Hộp sọ sau dẹt rộng (đáy tam giác)
        {"origin": [-14.0, -5.0, -4.0], "size": [28.0, 12.0, 16.0], "uv": face_uv_box("HEAD_SCALE")},
        # Nắp trán thon dần về mũi (tam giác dẹt)
        {"origin": [-11.5, -2.5, -18.0], "size": [23.0, 9.5, 14.5], "uv": face_uv_box("HEAD_SCALE")},
        # Mõm trước nhọn
        {"origin": [-8.0, -2.0, -28.0], "size": [16.0, 7.5, 10.5], "uv": face_uv_box("HEAD_SCALE")},
        # Cung mày nhô tạo khe mắt xiên hung dữ
        {"origin": [8.5, 4.2, -16.0], "size": [5.8, 3.2, 11.0], "rotation": [6, -12, 16], "uv": face_uv_box("HEAD_SCALE")},
        {"origin": [-14.3, 4.2, -16.0], "size": [5.8, 3.2, 11.0], "rotation": [6, 12, -16], "uv": face_uv_box("HEAD_SCALE")},
        # Hai mắt đỏ khe xiên phát sáng
        {"origin": [10.6, 1.8, -14.8], "size": [2.2, 2.8, 6.8], "rotation": [12, -16, 10], "uv": face_uv_box("EYE_SLIT")},
        {"origin": [-12.8, 1.8, -14.8], "size": [2.2, 2.8, 6.8], "rotation": [12, 16, -10], "uv": face_uv_box("EYE_SLIT")},
        # Vòm miệng trên đỏ thẫm (phát sáng nhẹ trong glowmask)
        {"origin": [-7.2, -3.2, -25.0], "size": [14.4, 2.2, 20.0], "uv": face_uv_box("MOUTH_RED")},
        # Hai răng nanh trắng dài cong nhẹ
        {"origin": [5.2, -10.8, -24.5], "size": [2.4, 8.6, 2.6], "rotation": [10, 0, -4], "uv": face_uv_box("FANG_BONE")},
        {"origin": [-7.6, -10.8, -24.5], "size": [2.4, 8.6, 2.6], "rotation": [10, 0, 4], "uv": face_uv_box("FANG_BONE")},
        # Răng phụ nhỏ hai bên hàm trên
        {"origin": [6.5, -5.8, -16.0], "size": [1.8, 3.6, 2.0], "uv": face_uv_box("FANG_BONE")},
        {"origin": [-8.3, -5.8, -16.0], "size": [1.8, 3.6, 2.0], "uv": face_uv_box("FANG_BONE")},
    ]
    bones.append({"name": "head", "pivot": [0.0, 0.0, 0.0], "cubes": head_cubes})

    # Hàm dưới (bone riêng để há miệng khi gầm / cắn / nhổ độc)
    bones.append({
        "name": "jaw", "parent": "head", "pivot": [0.0, -3.5, 4.0],
        "cubes": [
            {"origin": [-9.5, -7.8, -16.0], "size": [19.0, 4.8, 20.0], "uv": face_uv_box("BELLY")},
            {"origin": [-6.5, -7.2, -25.5], "size": [13.0, 4.0, 10.0], "uv": face_uv_box("BELLY")},
            {"origin": [-5.8, -4.4, -23.5], "size": [11.6, 1.8, 18.0], "uv": face_uv_box("MOUTH_RED")},
            {"origin": [4.2, -4.2, -22.0], "size": [1.8, 3.4, 1.8], "uv": face_uv_box("FANG_BONE")},
            {"origin": [-6.0, -4.2, -22.0], "size": [1.8, 3.4, 1.8], "uv": face_uv_box("FANG_BONE")},
        ]
    })

    # Lưỡi chẻ đôi đỏ (thè ra rung nhẹ khi idle / hiss / flare)
    bones.append({
        "name": "tongue", "parent": "jaw", "pivot": [0.0, -3.4, -14.0],
        "cubes": [
            {"origin": [-1.4, -3.8, -28.0], "size": [2.8, 1.0, 14.0], "uv": face_uv_box("MOUTH_RED")},
            {"origin": [0.6, -3.8, -35.5], "size": [1.4, 0.9, 8.2], "rotation": [0, -18, 0], "uv": face_uv_box("MOUTH_RED")},
            {"origin": [-2.0, -3.8, -35.5], "size": [1.4, 0.9, 8.2], "rotation": [0, 18, 0], "uv": face_uv_box("MOUTH_RED")},
        ]
    })

    # Hai sừng đen nhỏ cong ra sau
    bones.append({
        "name": "horn_l", "parent": "head", "pivot": [9.5, 6.5, 2.0],
        "cubes": [
            {"origin": [8.0, 6.2, 1.0], "size": [3.6, 3.6, 10.5], "rotation": [24, 18, 8], "uv": face_uv_box("HORN_DARK")},
            {"origin": [9.6, 9.2, 9.8], "size": [2.4, 2.4, 8.5], "rotation": [12, 24, 6], "uv": face_uv_box("HORN_DARK")},
        ]
    })
    bones.append({
        "name": "horn_r", "parent": "head", "pivot": [-9.5, 6.5, 2.0],
        "cubes": [
            {"origin": [-11.6, 6.2, 1.0], "size": [3.6, 3.6, 10.5], "rotation": [24, -18, -8], "uv": face_uv_box("HORN_DARK")},
            {"origin": [-12.0, 9.2, 9.8], "size": [2.4, 2.4, 8.5], "rotation": [12, -24, -6], "uv": face_uv_box("HORN_DARK")},
        ]
    })

    # Hai cánh mang cổ bành (hood_l / hood_r) có thể xòe & xẹp qua animation
    bones.append({
        "name": "hood_l", "parent": "head", "pivot": [12.0, 0.0, 10.0],
        "cubes": [
            {"origin": [12.0, -4.0, 8.0], "size": [19.0, 3.2, 28.0], "rotation": [0, 8, -10], "uv": face_uv_box("HOOD")},
            {"origin": [27.5, -5.8, 12.0], "size": [7.5, 2.6, 20.0], "rotation": [0, 18, -18], "uv": face_uv_box("HOOD")},
            {"origin": [14.0, -2.0, 34.0], "size": [12.0, 2.6, 10.0], "rotation": [0, -14, -8], "uv": face_uv_box("HOOD")},
        ]
    })
    bones.append({
        "name": "hood_r", "parent": "head", "pivot": [-12.0, 0.0, 10.0],
        "cubes": [
            {"origin": [-31.0, -4.0, 8.0], "size": [19.0, 3.2, 28.0], "rotation": [0, -8, 10], "uv": face_uv_box("HOOD")},
            {"origin": [-35.0, -5.8, 12.0], "size": [7.5, 2.6, 20.0], "rotation": [0, -18, 18], "uv": face_uv_box("HOOD")},
            {"origin": [-26.0, -2.0, 34.0], "size": [12.0, 2.6, 10.0], "rotation": [0, 14, 8], "uv": face_uv_box("HOOD")},
        ]
    })

    # -------------------------------------------------------------------------
    # 2. CÁC ĐỐT THÂN PHẲNG (segment_1 .. segment_42):
    #    KHÔNG có parent -> vị trí/góc/scale đặt tuyệt đối từng frame từ SerpentPath!
    #    Mặt cắt gần bát giác: ghép 3 cube xoay (0°, +45°, -45°) + tấm bụng riêng + gờ sống lưng.
    #    Đường kính chuẩn ~36.5 px (= 2.28 block = 35% chiều cao Leviathan 6.5 block).
    # -------------------------------------------------------------------------
    seg_len = 22.0   # ~1.375 block -> overlap nhẹ với segmentSpacing=1.15 blk để khúc cua liền mạch
    half_l = seg_len * 0.5
    rad = 18.2       # đường kính ~36.4 px (~2.275 block)

    for i in range(1, N_MAX + 1):
        # Độ lệch vi mô theo i % 2 để tuyệt đối không z-fighting giữa đốt kề nhau
        eps = 0.04 if (i % 2 == 0) else -0.04
        r0 = rad + eps
        # Lõi bát giác tạo từ 3 hình hộp chồng lệch góc 0°, +45°, -45° quanh trục Z
        seg_cubes = [
            # Lõi chính ngang (0°)
            {
                "origin": [-r0, -r0 * 0.84, -half_l],
                "size": [r0 * 2.0, r0 * 1.68, seg_len],
                "uv": segment_uv_box(True)
            },
            # Lõi vát chéo +45° (bo tròn mặt cắt thành bát giác)
            {
                "origin": [-r0 * 0.88, -r0 * 0.88, -half_l + 0.12],
                "size": [r0 * 1.76, r0 * 1.76, seg_len - 0.24],
                "pivot": [0.0, 0.0, 0.0],
                "rotation": [0.0, 0.0, 45.0],
                "uv": segment_uv_box(False)
            },
            # Sống vảy lưng nhô nhẹ phía trên
            {
                "origin": [-r0 * 0.42, r0 * 0.72, -half_l + 0.4],
                "size": [r0 * 0.84, r0 * 0.34, seg_len - 0.8],
                "uv": face_uv_box("SCALE_BACK")
            },
            # Tấm bụng xanh tím rộng rõ phía dưới
            {
                "origin": [-r0 * 0.78, -r0 * 0.96, -half_l + 0.25],
                "size": [r0 * 1.56, r0 * 0.30, seg_len - 0.5],
                "uv": face_uv_box("BELLY")
            },
        ]
        bones.append({
            "name": f"segment_{i}",
            "pivot": [0.0, 0.0, 0.0],
            "cubes": seg_cubes
        })

    # -------------------------------------------------------------------------
    # 3. ĐẦU ĐUÔI PHẲNG (tail_tip): gai sừng nhọn + chuỗi 4 đốt xương nhỏ có thể rung
    # -------------------------------------------------------------------------
    tail_cubes = [
        {"origin": [-5.2, -4.6, -4.0], "size": [10.4, 9.2, 9.0], "uv": face_uv_box("TAIL_BONE")},
        {"origin": [-4.4, -3.8, 5.2], "size": [8.8, 7.6, 6.2], "uv": face_uv_box("FANG_BONE")},
        {"origin": [-3.6, -3.2, 11.8], "size": [7.2, 6.4, 5.8], "uv": face_uv_box("TAIL_BONE")},
        {"origin": [-2.8, -2.5, 18.0], "size": [5.6, 5.0, 5.4], "uv": face_uv_box("FANG_BONE")},
        {"origin": [-2.0, -1.9, 23.8], "size": [4.0, 3.8, 5.0], "uv": face_uv_box("TAIL_BONE")},
        # Mũi gai sừng nhọn chót đuôi
        {"origin": [-1.6, -1.6, 29.0], "size": [3.2, 3.2, 11.0], "uv": face_uv_box("HORN_DARK")},
        {"origin": [-0.9, -0.9, 39.8], "size": [1.8, 1.8, 8.5], "uv": face_uv_box("HORN_DARK")},
    ]
    bones.append({"name": "tail_tip", "pivot": [0.0, 0.0, 0.0], "cubes": tail_cubes})

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    assert total_cubes < 900, f"Too many cubes: {total_cubes}"

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.jormungandr",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 64.0,
                    "visible_bounds_height": 20.0,
                    "visible_bounds_offset": [0.0, 4.0, 0.0]
                },
                "bones": bones
            }
        ]
    }
    out = ASSETS / "geo" / "entity" / "jormungandr.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes < 900)")
    return total_cubes


def build_textures() -> None:
    base = Image.new("RGBA", (TEX_W, TEX_H), (23, 23, 28, 255))
    glow = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    enraged = Image.new("RGBA", (TEX_W, TEX_H), (23, 23, 28, 255))
    enraged_glow = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))

    db = ImageDraw.Draw(base)
    dg = ImageDraw.Draw(glow)
    de = ImageDraw.Draw(enraged)
    deg = ImageDraw.Draw(enraged_glow)

    def hex_rgb(h: str) -> tuple[int, int, int]:
        h = h.lstrip("#")
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)

    c_scale = hex_rgb("#17171C")
    c_scale_hi = hex_rgb("#2C2C36")
    c_crevice = hex_rgb("#0C0C10")
    c_belly0 = hex_rgb("#4F5FC8")
    c_belly1 = hex_rgb("#7F8CE6")
    c_hood = hex_rgb("#B7A6EE")
    c_hood_rim = hex_rgb("#8A74D9")
    c_eye = hex_rgb("#E01818")
    c_fang = hex_rgb("#F2EEE6")
    c_mouth = hex_rgb("#C81E1E")

    # 1. SCALE_BACK & HEAD_SCALE: vảy tròn chồng lớp (#17171C, #2C2C36, khe #0C0C10) + vết nứt tím trắng ở Phase 2
    for key in ("SCALE_BACK", "HEAD_SCALE"):
        x0, y0, w, h = MAT[key]
        db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(*c_crevice, 255))
        de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(*c_crevice, 255))
        step_x = 24
        step_y = 20
        for row in range(-1, h // step_y + 2):
            off = (step_x // 2) if (row % 2 == 1) else 0
            for col in range(-1, w // step_x + 2):
                cx = x0 + col * step_x + off
                cy = y0 + row * step_y
                box = [cx - 12, cy - 11, cx + 12, cy + 11]
                # Khe tối bao ngoài
                db.ellipse([box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1], fill=(*c_crevice, 255))
                de.ellipse([box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1], fill=(*c_crevice, 255))
                # Vảy đen bóng chính
                db.ellipse(box, fill=(*c_scale, 255))
                de.ellipse(box, fill=(c_scale[0] + 5, c_scale[1] + 4, c_scale[2] + 12, 255))
                # Ánh sáng tròn đỉnh vảy (#2C2C36)
                hi_box = [cx - 8, cy - 8, cx + 6, cy + 1]
                db.ellipse(hi_box, fill=(*c_scale_hi, 255))
                de.ellipse(hi_box, fill=(c_scale_hi[0] + 12, c_scale_hi[1] + 8, c_scale_hi[2] + 24, 255))

        # Vết nứt sáng tím-trắng Phase 2 trên vảy (glowmask enraged)
        for k in range(5):
            pts = []
            px = x0 + 24 + k * 46
            for s in range(10):
                py = y0 + s * 28
                px_j = px + int(math.sin(s * 1.4 + k) * 14)
                pts.append((px_j, py))
            de.line(pts, fill=(216, 176, 255, 255), width=4)
            de.line(pts, fill=(255, 246, 255, 255), width=2)
            deg.line(pts, fill=(210, 150, 255, 235), width=4)
            deg.line(pts, fill=(255, 245, 255, 255), width=2)

    # 2. BELLY: chuyển sắc xanh tím (#4F5FC8 -> #7F8CE6) chia đốt vảy bụng ngang
    x0, y0, w, h = MAT["BELLY"]
    for dy in range(h):
        for dx in range(w):
            # Chuyển sắc từ rìa (#4F5FC8) vào giữa bụng (#7F8CE6)
            edge_dist = 1.0 - abs((dx / float(w)) * 2.0 - 1.0)
            t = max(0.0, min(1.0, edge_dist ** 0.7))
            rib = 0.80 if (dy % 18 < 3) else 1.0
            r = int((c_belly0[0] + (c_belly1[0] - c_belly0[0]) * t) * rib)
            g = int((c_belly0[1] + (c_belly1[1] - c_belly0[1]) * t) * rib)
            b = int((c_belly0[2] + (c_belly1[2] - c_belly0[2]) * t) * rib)
            base.putpixel((x0 + dx, y0 + dy), (r, g, b, 255))
            # Phase 2 bụng sáng hơn & ánh tím trắng ở giữa
            er = min(255, int(r * 1.28 + 30 * t))
            eg = min(255, int(g * 1.22 + 24 * t))
            eb = min(255, int(b * 1.15 + 32 * t))
            enraged.putpixel((x0 + dx, y0 + dy), (er, eg, eb, 255))
            if t > 0.55 and dy % 18 >= 4:
                enraged_glow.putpixel((x0 + dx, y0 + dy), (175, 165, 255, 115))

    # 3. HOOD: mang cổ tím nhạt (#B7A6EE), viền sẫm (#8A74D9), vài lỗ tròn nhỏ & mép phát sáng trong glowmask
    x0, y0, w, h = MAT["HOOD"]
    db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(*c_hood, 255))
    de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(208, 192, 255, 255))
    # Viền sẫm ngoài + glow mép mang cổ
    db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=(*c_hood_rim, 255), width=22)
    de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=(168, 128, 255, 255), width=22)
    dg.rectangle([x0 + 4, y0 + 4, x0 + w - 5, y0 + h - 5], outline=(180, 145, 255, 175), width=10)
    deg.rectangle([x0 + 4, y0 + 4, x0 + w - 5, y0 + h - 5], outline=(235, 200, 255, 245), width=14)
    # Đường gân tia mang cổ
    for ry in range(36, h - 24, 38):
        db.line([(x0 + 18, y0 + ry), (x0 + w - 18, y0 + ry)], fill=(*c_hood_rim, 255), width=4)
        de.line([(x0 + 18, y0 + ry), (x0 + w - 18, y0 + ry)], fill=(230, 205, 255, 255), width=5)
        deg.line([(x0 + 18, y0 + ry), (x0 + w - 18, y0 + ry)], fill=(220, 185, 255, 165), width=3)
    # Vài lỗ tròn nhỏ trang trí có viền sẫm trên mang cổ
    for hx, hy, hr in [(84, 72, 14), (170, 96, 12), (102, 168, 15), (176, 182, 11)]:
        db.ellipse([x0 + hx - hr - 3, y0 + hy - hr - 3, x0 + hx + hr + 3, y0 + hy + hr + 3], fill=(*c_hood_rim, 255))
        db.ellipse([x0 + hx - hr, y0 + hy - hr, x0 + hx + hr, y0 + hy + hr], fill=(22, 18, 32, 255))
        de.ellipse([x0 + hx - hr - 3, y0 + hy - hr - 3, x0 + hx + hr + 3, y0 + hy + hr + 3], fill=(235, 205, 255, 255))
        de.ellipse([x0 + hx - hr, y0 + hy - hr, x0 + hx + hr, y0 + hy + hr], fill=(32, 18, 52, 255))
        deg.ellipse([x0 + hx - hr - 3, y0 + hy - hr - 3, x0 + hx + hr + 3, y0 + hy + hr + 3], outline=(245, 220, 255, 220), width=3)

    # 4. EYE_SLIT: mắt đỏ khe xiên phát sáng (#E01818), Phase 2 chuyển trắng-đỏ
    x0, y0, w, h = MAT["EYE_SLIT"]
    db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(*c_eye, 255))
    dg.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(255, 36, 36, 255))
    de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(255, 115, 115, 255))
    deg.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(255, 175, 175, 255))
    # Đồng tử dọc sắc lạnh
    db.polygon([(x0 + w // 2, y0 + 8), (x0 + w // 2 + 12, y0 + h // 2),
                (x0 + w // 2, y0 + h - 8), (x0 + w // 2 - 12, y0 + h // 2)], fill=(20, 4, 6, 255))
    de.polygon([(x0 + w // 2, y0 + 12), (x0 + w // 2 + 16, y0 + h // 2),
                (x0 + w // 2, y0 + h - 12), (x0 + w // 2 - 16, y0 + h // 2)], fill=(255, 250, 248, 255))
    deg.polygon([(x0 + w // 2, y0 + 12), (x0 + w // 2 + 16, y0 + h // 2),
                 (x0 + w // 2, y0 + h - 12), (x0 + w // 2 - 16, y0 + h // 2)], fill=(255, 255, 255, 255))

    # 5. FANG_BONE (#F2EEE6), MOUTH_RED (#C81E1E), HORN_DARK, TAIL_BONE
    x0, y0, w, h = MAT["FANG_BONE"]
    db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(*c_fang, 255))
    de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(248, 244, 240, 255))
    for gy in range(0, h, 16):
        db.line([(x0, y0 + gy), (x0 + w, y0 + gy)], fill=(214, 206, 194, 255), width=2)
        de.line([(x0, y0 + gy), (x0 + w, y0 + gy)], fill=(224, 210, 232, 255), width=2)

    x0, y0, w, h = MAT["MOUTH_RED"]
    db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(*c_mouth, 255))
    dg.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(220, 36, 48, 190))
    de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(232, 54, 78, 255))
    deg.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(255, 86, 118, 235))

    x0, y0, w, h = MAT["HORN_DARK"]
    db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(16, 15, 22, 255))
    de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(28, 20, 38, 255))
    for gy in range(0, h, 12):
        db.line([(x0, y0 + gy), (x0 + w, y0 + gy)], fill=(38, 36, 48, 255), width=2)
        de.line([(x0, y0 + gy), (x0 + w, y0 + gy)], fill=(120, 88, 165, 255), width=2)

    x0, y0, w, h = MAT["TAIL_BONE"]
    db.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(42, 40, 52, 255))
    de.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(66, 52, 92, 255))
    for gy in range(8, h, 24):
        db.rectangle([x0 + 8, y0 + gy, x0 + w - 8, y0 + gy + 10], fill=(*c_fang, 255))
        de.rectangle([x0 + 8, y0 + gy, x0 + w - 8, y0 + gy + 10], fill=(240, 232, 255, 255))

    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)
    base.save(tex_dir / "jormungandr.png")
    glow.save(tex_dir / "jormungandr_glowmask.png")
    enraged.save(tex_dir / "jormungandr_enraged.png")
    enraged_glow.save(tex_dir / "jormungandr_enraged_glowmask.png")

    # Vật phẩm hiếm: jormungandr_venom_fang.png (16x16 nanh rắn hổ mang rỉ độc tím-xanh)
    item_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(item_img)
    # Thân nanh trắng cong (#F2EEE6)
    fang_pts = [(11, 2), (13, 4), (11, 8), (8, 12), (4, 14), (3, 12), (6, 9), (8, 5)]
    idraw.polygon(fang_pts, fill=(242, 238, 230, 255), outline=(140, 134, 152, 255))
    # Gốc nanh viền tím tối
    idraw.rectangle([10, 2, 13, 4], fill=(138, 116, 217, 255))
    # Giọt độc phát sáng ở mũi nanh
    idraw.rectangle([2, 13, 4, 15], fill=(132, 245, 92, 255))
    idraw.point((3, 14), fill=(215, 255, 195, 255))
    item_out = ASSETS / "textures" / "item" / "jormungandr_venom_fang.png"
    item_out.parent.mkdir(parents=True, exist_ok=True)
    item_img.save(item_out)
    print(f"TEX_OK: 4 entity textures (1024x1024) + 1 item icon ({item_out})")


def build_animations() -> None:
    # 17 animation đúng danh sách mục 9.5; mọi góc xoay kẹp trong [-180, 180]
    anims = {
        "animation.jormungandr.idle_coil": {
            "loop": True,
            "animation_length": 3.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [4, 0, 0], "1.5": [12, 0, 0], "3.0": [4, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 6], "1.1": [0, 0, -5], "1.5": [0, 0, -5], "2.0": [0, 0, 6], "3.0": [0, 0, 6]},
                           "rotation": {"0.0": [0, 0, 0], "1.25": [8, 12, 0], "1.55": [-8, -12, 0], "3.0": [0, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, -28, 16], "1.5": [0, -22, 12], "3.0": [0, -28, 16]}},
                "hood_r": {"rotation": {"0.0": [0, 28, -16], "1.5": [0, 22, -12], "3.0": [0, 28, -16]}},
            }
        },
        "animation.jormungandr.rear_up": {
            "loop": True,
            "animation_length": 2.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "1.2": [22, 0, 0], "2.4": [10, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, -4], "1.2": [0, 0, -8], "2.4": [0, 0, -4]}},
                "hood_l": {"rotation": {"0.0": [0, 18, -12], "1.2": [0, 26, -16], "2.4": [0, 18, -12]}},
                "hood_r": {"rotation": {"0.0": [0, -18, 12], "1.2": [0, -26, 16], "2.4": [0, -18, 12]}},
            }
        },
        "animation.jormungandr.slither": {
            "loop": True,
            "animation_length": 1.6,
            "bones": {
                "jaw": {"rotation": {"0.0": [6, 0, 0], "0.8": [10, 0, 0], "1.6": [6, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, -34, 20], "0.8": [0, -28, 18], "1.6": [0, -34, 20]}},
                "hood_r": {"rotation": {"0.0": [0, 34, -20], "0.8": [0, 28, -18], "1.6": [0, 34, -20]}},
            }
        },
        "animation.jormungandr.strike": {
            "animation_length": 1.8,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "0.8": [48, 0, 0], "1.15": [58, 0, 0], "1.35": [4, 0, 0], "1.8": [8, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, -10, 0], "0.8": [0, 32, -18], "1.4": [0, 20, -10], "1.8": [0, -10, 0]}},
                "hood_r": {"rotation": {"0.0": [0, 10, 0], "0.8": [0, -32, 18], "1.4": [0, -20, 10], "1.8": [0, 10, 0]}},
            }
        },
        "animation.jormungandr.spit": {
            "animation_length": 1.8,
            "bones": {
                "jaw": {"rotation": {"0.0": [6, 0, 0], "0.75": [52, 0, 0], "1.25": [42, 0, 0], "1.8": [6, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 0, 0], "0.8": [0, 28, -14], "1.8": [0, 0, 0]}},
                "hood_r": {"rotation": {"0.0": [0, 0, 0], "0.8": [0, -28, 14], "1.8": [0, 0, 0]}},
            }
        },
        "animation.jormungandr.tail_slam": {
            "animation_length": 2.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "1.0": [34, 0, 0], "2.0": [8, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 24, -10], "2.0": [0, 0, 0]}},
                "hood_r": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, -24, 10], "2.0": [0, 0, 0]}},
            }
        },
        "animation.jormungandr.constrict": {
            "animation_length": 3.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "1.5": [36, 0, 0], "3.0": [8, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 18, -10], "1.5": [0, 30, -16], "3.0": [0, 18, -10]}},
                "hood_r": {"rotation": {"0.0": [0, -18, 10], "1.5": [0, -30, 16], "3.0": [0, -18, 10]}},
            }
        },
        "animation.jormungandr.flare": {
            "animation_length": 1.6,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "0.75": [54, 0, 0], "1.6": [10, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 0], "0.75": [0, 0, -10], "1.6": [0, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, -20, 10], "0.65": [0, 42, -24], "1.2": [0, 38, -22], "1.6": [0, -12, 6]}},
                "hood_r": {"rotation": {"0.0": [0, 20, -10], "0.65": [0, -42, 24], "1.2": [0, -38, 22], "1.6": [0, 12, -6]}},
            }
        },
        "animation.jormungandr.burrow": {
            "animation_length": 1.4,
            "bones": {
                "hood_l": {"rotation": {"0.0": [0, 0, 0], "0.7": [0, -45, 28], "1.4": [0, -45, 28]}},
                "hood_r": {"rotation": {"0.0": [0, 0, 0], "0.7": [0, 45, -28], "1.4": [0, 45, -28]}},
                "jaw": {"rotation": {"0.0": [10, 0, 0], "1.4": [0, 0, 0]}},
            }
        },
        "animation.jormungandr.emerge": {
            "animation_length": 1.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [4, 0, 0], "0.5": [58, 0, 0], "1.4": [14, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, -40, 24], "0.5": [0, 38, -20], "1.4": [0, 16, -8]}},
                "hood_r": {"rotation": {"0.0": [0, 40, -24], "0.5": [0, -38, 20], "1.4": [0, -16, 8]}},
            }
        },
        "animation.jormungandr.rapid_strike": {
            "animation_length": 2.8,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "0.6": [54, 0, 0], "0.9": [6, 0, 0],
                                     "1.5": [56, 0, 0], "1.8": [6, 0, 0], "2.4": [60, 0, 0], "2.8": [10, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 20, -12], "1.4": [0, 36, -20], "2.8": [0, 20, -12]}},
                "hood_r": {"rotation": {"0.0": [0, -20, 12], "1.4": [0, -36, 20], "2.8": [0, -20, 12]}},
            }
        },
        "animation.jormungandr.nova": {
            "animation_length": 2.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "0.9": [60, 0, 0], "1.5": [52, 0, 0], "2.0": [8, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 0, 0], "0.9": [0, 44, -24], "2.0": [0, 0, 0]}},
                "hood_r": {"rotation": {"0.0": [0, 0, 0], "0.9": [0, -44, 24], "2.0": [0, 0, 0]}},
            }
        },
        "animation.jormungandr.rain": {
            "animation_length": 2.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "0.8": [56, 0, 0], "1.8": [48, 0, 0], "2.4": [10, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 12, -8], "1.2": [0, 36, -20], "2.4": [0, 12, -8]}},
                "hood_r": {"rotation": {"0.0": [0, -12, 8], "1.2": [0, -36, 20], "2.4": [0, -12, 8]}},
            }
        },
        "animation.jormungandr.ouroboros": {
            "animation_length": 6.5,
            "bones": {
                "jaw": {"rotation": {"0.0": [12, 0, 0], "2.5": [42, 0, 0], "5.5": [60, 0, 0], "6.5": [10, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 28, -16], "5.5": [0, 44, -24], "6.5": [0, 20, -10]}},
                "hood_r": {"rotation": {"0.0": [0, -28, 16], "5.5": [0, -44, 24], "6.5": [0, -20, 10]}},
            }
        },
        "animation.jormungandr.phase_change": {
            "animation_length": 5.5,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "1.2": [62, 0, 0], "4.2": [58, 0, 0], "5.5": [14, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 0], "1.5": [0, 0, -10], "4.2": [0, 0, -10], "5.5": [0, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, -10, 6], "1.2": [0, 46, -26], "4.5": [0, 46, -26], "5.5": [0, 20, -12]}},
                "hood_r": {"rotation": {"0.0": [0, 10, -6], "1.2": [0, -46, 26], "4.5": [0, -46, 26], "5.5": [0, -20, 12]}},
            }
        },
        "animation.jormungandr.grow": {
            "animation_length": 2.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [20, 0, 0], "1.2": [54, 0, 0], "2.4": [16, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 32, -18], "1.2": [0, 44, -24], "2.4": [0, 24, -14]}},
                "hood_r": {"rotation": {"0.0": [0, -32, 18], "1.2": [0, -44, 24], "2.4": [0, -24, 14]}},
            }
        },
        "animation.jormungandr.death": {
            "loop": "hold_on_last_frame",
            "animation_length": 2.6,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "1.2": [48, 0, 0], "2.6": [32, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, 10, -6], "2.6": [0, -48, 30]}},
                "hood_r": {"rotation": {"0.0": [0, -10, 6], "2.6": [0, 48, -30]}},
                "tongue": {"position": {"0.0": [0, 0, 0], "2.6": [0, 0, -12]}},
            }
        }
    }

    max_rot = 0.0
    for name, anim in anims.items():
        for bname, ch in anim.get("bones", {}).items():
            for t, vec in ch.get("rotation", {}).items():
                for v in vec:
                    assert abs(v) <= 180.0, f"Abnormal rot {v} in {name}.{bname}@{t}"
                    max_rot = max(max_rot, abs(v))

    out = ASSETS / "animations" / "entity" / "jormungandr.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=2)
    print(f"ANIM_OK: {out} ({len(anims)} animations, max |rot|={max_rot:.1f} deg)")


if __name__ == "__main__":
    backup_existing_files()
    build_geo()
    build_textures()
    build_animations()

