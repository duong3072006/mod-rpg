#!/usr/bin/env python3
"""
Sinh biểu tượng `assets/abyssalrpg/icon.png` (128×128 RGBA, vẽ pixel-art trên lưới 32×32
rồi phóng to 4× nearest để hiển thị cực kỳ sắc nét ở cả 128×128 Modrinth/CurseForge lẫn 32×32 Mod Menu).

Chủ đề: Abyssal Spire RPG — Tháp/Cổng vực thẳm obsidian, lõi pha lê xanh lam-tím phát sáng
và tia lửa vàng cam rực rỡ.

An toàn bảo vệ Custom Art:
- Lưu MD5 của icon gốc (placeholder) vào `tools/backup_placeholders/icon.png` trước lần ghi đầu tiên.
- Lưu MD5 của icon v2 vào `tools/icon_manifest.json`. Nếu người dùng tự vẽ thay `icon.png`,
  hash sẽ khác manifest và script tự động bỏ qua không ghi đè.

Cách dùng:
  python tools/generate_mod_icon.py
  python tools/generate_mod_icon.py --dry-run
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
from pathlib import Path

import numpy as np
from PIL import Image

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
ICON_PATH = REPO_ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg" / "icon.png"
BACKUP_PATH = TOOLS_DIR / "backup_placeholders" / "icon.png"
MANIFEST_PATH = TOOLS_DIR / "icon_manifest.json"
# Hash placeholder cũ 441 bytes (tháp tím đơn giản)
OLD_PLACEHOLDER_HASH = None


def md5_bytes(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()


def render_abyssal_icon_128() -> Image.Image:
    """Vẽ pixel-art 32×32 tỉ mỉ rồi upscale 4× nearest -> 128×128 RGBA."""
    N = 32
    px = np.zeros((N, N, 4), dtype=np.uint8)

    def put(x: int, y: int, rgb: tuple[int, int, int]) -> None:
        if 0 <= x < N and 0 <= y < N:
            px[y, x] = (*rgb, 255)

    def lerp(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
        t = max(0.0, min(1.0, t))
        return (
            int(round(a[0] + (b[0] - a[0]) * t)),
            int(round(a[1] + (b[1] - a[1]) * t)),
            int(round(a[2] + (b[2] - a[2]) * t)),
        )

    # 1. Bầu trời vực thẳm đêm (gradient chàm -> tím sẫm ở chân tháp) + hào quang tâm
    cx, cy = 15.5, 16.0
    for y in range(N):
        for x in range(N):
            bg = lerp((10, 12, 26), (26, 16, 46), y / 31.0)
            # Hào quang lam-tím lan tỏa nhẹ quanh tâm cổng tháp
            d = math.hypot((x - cx) * 0.9, (y - 16.5) * 0.85)
            if d < 12.0:
                glow_t = (1.0 - d / 12.0) ** 2.0
                glow_col = lerp((68, 42, 138), (38, 178, 224), max(0.0, 1.0 - d / 8.0))
                bg = lerp(bg, glow_col, glow_t * 0.52)
            put(x, y, bg)

    # Vài ngôi sao lấp lánh ở nền trên
    for sx, sy, col in (
        (5, 5, (150, 215, 255)),
        (26, 6, (205, 175, 255)),
        (7, 10, (125, 165, 220)),
        (24, 11, (160, 235, 255)),
        (4, 16, (110, 130, 185)),
        (27, 15, (140, 115, 195)),
    ):
        put(sx, sy, col)

    # 2. Nền đá vực thẳm ở đáy tháp (y=26..29)
    obs_dark = (18, 16, 28)
    obs_mid = (36, 32, 54)
    obs_hi = (66, 58, 92)
    obs_rim = (96, 86, 132)
    for y in range(25, 30):
        for x in range(2, 30):
            if y == 25 and (x < 6 or x > 25):
                continue
            tone = obs_hi if y == 25 else (obs_mid if (x + y) % 3 != 0 else obs_dark)
            put(x, y, tone)

    # 3. Tháp chính Obsidian (Abyssal Spire) với 2 tháp phụ nhọn hai bên + vòm cổng ở giữa
    # Tháp phụ trái (x=6..9, y=10..26) & phải (x=22..25, y=10..26)
    for base_x, peak_x in ((6, 7), (22, 24)):
        # Đỉnh nhọn tháp phụ
        put(peak_x, 8, obs_rim)
        put(peak_x, 9, obs_hi)
        put(peak_x + (1 if base_x == 6 else -1), 9, obs_mid)
        for y in range(10, 27):
            for x in range(base_x, base_x + 4):
                if x == base_x:
                    put(x, y, obs_hi if y % 3 != 0 else obs_rim)  # bắt sáng cạnh trái
                elif x == base_x + 3:
                    put(x, y, obs_dark)
                else:
                    put(x, y, obs_mid if (x + y) % 2 == 0 else obs_dark)

    # Thân tháp trung tâm uy nghi (x=9..22, chóp nhọn vươn tới y=3)
    spire_profile = {
        3: (15, 16),
        4: (15, 16),
        5: (14, 17),
        6: (13, 18),
        7: (12, 19),
        8: (11, 20),
        9: (10, 21),
    }
    for y in range(3, 27):
        x0, x1 = spire_profile.get(y, (9, 22))
        for x in range(x0, x1 + 1):
            if x == x0:
                put(x, y, obs_rim if y <= 10 else obs_hi)  # cạnh trái đón sáng
            elif x == x1:
                put(x, y, obs_dark)
            elif x == x0 + 1 and y >= 8:
                put(x, y, obs_hi if y % 2 == 0 else obs_mid)
            else:
                # Khối gạch đá obsidian xen kẽ
                brick = obs_mid if ((x + (y // 2)) % 3 != 0) else obs_dark
                put(x, y, brick)

    # Hai gai vương miện obsidian ở vai tháp (y=11..12)
    for gx, gy in ((8, 12), (23, 12), (10, 8), (21, 8)):
        put(gx, gy, obs_rim)

    # 4. Vòm cổng vực thẳm giữa tháp (x=12..19, y=12..25) — ánh năng lượng tím-lam xoáy
    for y in range(11, 26):
        for x in range(11, 21):
            dx = (x - 15.5) / 4.2
            dy = (y - 18.0) / 7.2
            in_arch = (y >= 16 and 12 <= x <= 19) or (y < 16 and (dx * dx + ((y - 16.0) / 4.6) ** 2) <= 1.0)
            if in_arch:
                # Viền trong cổng sáng tím
                dist_c = math.hypot((x - 15.5) * 1.0, (y - 17.5) * 0.78)
                if dist_c > 3.6:
                    put(x, y, (46, 26, 92))
                elif dist_c > 2.3:
                    put(x, y, (98, 56, 196))
                else:
                    put(x, y, (56, 196, 240))

    # 5. Lõi pha lê xanh lam-tím sắc cạnh (hình thoi lơ lửng ngay giữa cổng, y=12..22)
    crystal_cy = 17.0
    for y in range(12, 23):
        half_w = max(0.0, 3.2 - abs(y - crystal_cy) * 0.62)
        for x in range(12, 20):
            dx = x - 15.5
            if abs(dx) <= half_w:
                if abs(dx) <= 0.7 and abs(y - crystal_cy) <= 1.6:
                    put(x, y, (238, 255, 255))  # lõi trắng-lam chói sáng
                elif dx < 0 and y <= crystal_cy:
                    put(x, y, (124, 246, 255))  # mặt trên-trái lam sáng
                elif dx >= 0 and y <= crystal_cy:
                    put(x, y, (82, 188, 255))   # mặt trên-phải xanh biển
                elif dx < 0:
                    put(x, y, (148, 108, 255))  # mặt dưới-trái tím sáng
                else:
                    put(x, y, (108, 58, 214))   # mặt dưới-phải tím sâu

    # Ngọn tinh thể nhỏ phát sáng trên chóp đỉnh tháp (y=4..6)
    put(15, 5, (145, 248, 255))
    put(16, 5, (105, 208, 255))
    put(15, 6, (170, 128, 255))
    put(16, 6, (128, 78, 224))

    # 6. Tia lửa & rãnh dung nham vàng cam quanh chân tháp + bay lên hai bên (dấu ấn 5 Boss & Tro Phượng)
    ember_hot = (255, 232, 118)
    ember_mid = (255, 148, 42)
    ember_deep = (212, 68, 24)
    # Rãnh lửa đối xứng ở bệ đá đáy
    for ex, ey, col in (
        (10, 26, ember_deep), (11, 26, ember_mid), (12, 25, ember_hot),
        (21, 26, ember_deep), (20, 26, ember_mid), (19, 25, ember_hot),
        (14, 26, ember_mid), (15, 26, ember_hot), (16, 26, ember_hot), (17, 26, ember_mid),
        (8, 27, ember_deep), (23, 27, ember_deep),
        # Tia lửa vàng cam bay lượn 2 bên tháp
        (5, 22, ember_mid), (4, 19, ember_hot), (6, 15, ember_mid),
        (26, 21, ember_mid), (27, 18, ember_hot), (25, 13, ember_mid),
        (9, 6, ember_hot), (22, 7, ember_mid),
    ):
        put(ex, ey, col)

    # 7. Khung viền ngoài 2px obsidian-vàng đồng vát sáng góc trên-trái (rất nổi trên Mod Menu 32×32!)
    frame_hi = (242, 186, 72)
    frame_mid = (188, 122, 36)
    frame_dark = (104, 62, 20)
    frame_outer = (16, 12, 24)
    for i in range(N):
        # Đường viền trong kim loại vàng đồng (index 1 và 30)
        if 1 <= i <= 30:
            put(i, 1, frame_hi if i < 28 else frame_mid)
            put(1, i, frame_hi if i < 28 else frame_mid)
            put(i, 30, frame_dark if i > 3 else frame_mid)
            put(30, i, frame_dark if i > 3 else frame_mid)
        # Viền ngoài tối 1px giúp tách khỏi mọi nền sáng/tối
        put(i, 0, frame_outer)
        put(i, 31, frame_outer)
        put(0, i, frame_outer)
        put(31, i, frame_outer)

    # Đinh tán sáng 4 góc khung
    for gx, gy in ((2, 2), (29, 2), (2, 29), (29, 29)):
        put(gx, gy, (255, 228, 138))

    base = Image.fromarray(px, mode="RGBA")
    return base.resize((128, 128), Image.Resampling.NEAREST)


def main() -> None:
    parser = argparse.ArgumentParser(description="Sinh icon pixel-art 128x128 RGBA cho Abyssal Spire RPG")
    parser.add_argument("--dry-run", action="store_true", help="Chỉ kiểm tra hash, không ghi file")
    args = parser.parse_args()

    manifest: dict[str, str] = {}
    if MANIFEST_PATH.exists():
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    if ICON_PATH.exists():
        cur_bytes = ICON_PATH.read_bytes()
        cur_hash = md5_bytes(cur_bytes)
        prev_v2 = manifest.get("v2_icon_md5")
        orig_ph = manifest.get("original_placeholder_md5")

        # Nếu đã có manifest mà file trên đĩa khác cả v2 lẫn bản gốc -> Custom Art do người dùng vẽ -> BỎ QUA!
        if prev_v2 is not None and cur_hash != prev_v2 and cur_hash != orig_ph:
            print(f"[SKIP:CUSTOM_ART] Phát hiện {ICON_PATH.relative_to(REPO_ROOT).as_posix()} đã được thay bằng art vẽ tay (md5={cur_hash}), không ghi đè.")
            return

        if not args.dry_run and not BACKUP_PATH.exists():
            BACKUP_PATH.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ICON_PATH, BACKUP_PATH)
            manifest["original_placeholder_md5"] = cur_hash
            print(f"[Backup] Đã sao lưu icon cũ -> {BACKUP_PATH.relative_to(REPO_ROOT).as_posix()} (md5={cur_hash})")

    img = render_abyssal_icon_128()
    if args.dry_run:
        print(f"[DRY-RUN] Sẵn sàng sinh icon {img.size} {img.mode} -> {ICON_PATH.relative_to(REPO_ROOT).as_posix()}")
        return

    ICON_PATH.parent.mkdir(parents=True, exist_ok=True)
    img.save(ICON_PATH, format="PNG", optimize=True)
    new_hash = md5_bytes(ICON_PATH.read_bytes())
    manifest["v2_icon_md5"] = new_hash
    manifest["size"] = "128x128"
    manifest["mode"] = "RGBA"
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"[IconGen] Đã sinh {ICON_PATH.relative_to(REPO_ROOT).as_posix()} ({img.size[0]}x{img.size[1]} RGBA, {ICON_PATH.stat().st_size} bytes, md5={new_hash})")


if __name__ == "__main__":
    main()

