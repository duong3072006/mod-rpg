#!/usr/bin/env python3
"""
Bộ sinh Model (1.100+ cubes) + Texture 1024x1024 chất lượng cao (3-4 texel/px, nearest)
+ Animation (giữ nguyên 17 tên) + Bộ render Preview 3D đa góc (Z-buffer + UV thật)
cho Boss Jörmungandr (abyssalrpg:jormungandr) — Phiên bản đại tu toàn diện v2.

Kiến trúc giữ nguyên 100% tương thích với SerpentPath + bone phẳng + proxy hitbox:
  - Đầu & phụ kiện : head (con: jaw, tongue, horn_l, horn_r, hood_l, hood_r)
  - Locator mới    : mouth_venom (head), hood_l_edge (hood_l), hood_r_edge (hood_r), tail_tip_fx (tail_tip)
  - Thân phẳng     : segment_1 .. segment_54 (18 cube/đốt, mặt cắt bát giác vát 45°,
                     khớp lồng đuôi thu nhẹ 0.55 px chống z-fighting, sống lưng vảy mũi tên,
                     vảy bụng ngang lồng gọn không nhô cục; segment_1 có cổ lồng sâu vào hộp sọ)
  - Đuôi phẳng     : tail_tip (26 cube, thuôn mượt thành lưỡi sừng cổ đại có khe phát sáng Phase 2)

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
BACKUP_DIR = ROOT / "tools" / "backup_jormungandr_v1"
BACKUP_V2_DIR = ROOT / "tools" / "backup_jormungandr_v2"
PREVIEW_DIR = ROOT / "tools" / "jormungandr_previews"

TEX_W = 1024
TEX_H = 1024
N_MAX = 54              # Đủ cho Phase 1 (34 đốt) và Phase 2 x1.4 (48 đốt) + biên config
SEG_LEN_PX = 16.0       # Khớp chính xác SerpentPath.BASE_SEGMENT_MODEL_LENGTH_PX = 16.0f
BODY_RAD_PX = 18.2      # Đường kính chuẩn 36.4 px (~2.28 blk ở scale 1.0, ~2.96 blk ở 1.30x)
HEAD_SCALE_MULT = 1.35  # Khớp mặc định JormungandrConfig.headScaleMultiplier = 1.35f

# Bảng vùng vật liệu trên atlas 1024x1024: (x0, y0, w, h)
# Các vùng thân cũ (y < 832) giữ nguyên vị trí 100%; vùng y = 832..1024 dành riêng cho đầu/mắt/sừng v3!
MAT = {
    "SCALE_BACK":     (0,   0,   384, 384),  # Vảy lưng lục giác/giọt nước 6 tông đen-xanh than + AO (GIỮ NGUYÊN 100%)
    "BELLY":          (384, 0,   256, 384),  # Vảy bụng ngang xanh-tím (GIỮ NGUYÊN 100%)
    "HOOD_MEMBRANE":  (640, 0,   384, 384),  # Màng mang cổ tím dạ quang (#6B4BC4 -> #BBA3FF) + gân mảnh phát sáng
    "HEAD_SCALE":     (0,   384, 256, 256),  # Tấm giáp vảy đầu xếp lớp có viền sáng cạnh (#555E88 / #7A66B8)
    "EYE_SLIT":       (256, 384, 192, 192),  # Dự phòng tương thích vùng mắt cũ
    "FANG_BONE":      (448, 384, 192, 192),  # Răng nanh ngà cong #F4EFE6 có ánh độc đỏ/tím mảnh ở chân nanh
    "MOUTH_RED":      (640, 384, 192, 192),  # Vòm họng đỏ sẫm có chiều sâu & lưỡi chẻ đôi
    "HORN_DARK":      (832, 384, 192, 192),  # Sừng & gai sống lưng obsidian có viền sáng cạnh
    "HOOD_RIB":       (0,   640, 256, 192),  # Nan xương sườn mang cổ phát sáng (#DDD2FF)
    "DORSAL_VEIN":    (256, 640, 256, 192),  # Mạch năng lượng mảnh dọc sống lưng (GIỮ NGUYÊN 100%)
    "TAIL_CORE":      (512, 640, 256, 192),  # Giáp sừng đuôi cổ đại (GIỮ NGUYÊN 100%)
    "LIP_ARMOR":      (768, 640, 256, 192),  # Tấm vảy môi hàm trên/dưới chia đốt có rim highlight
    # --- VÙNG ĐẦU / MẮT / SỪNG V3 (y = 832..1024) ---
    "EYE_SOCKET":     (0,   832, 128, 128),  # Nền hốc mắt đen sâu tuyệt đối (#050508) tương phản tối đa
    "EYE_SLIT_HQ":    (128, 832, 256, 192),  # Tròng mắt fullbright 256x192 (#C80018 -> #FF3322 -> #FFE699 + vertical slit)
    "HEAD_RIM_PLATE": (384, 832, 256, 192),  # Giáp sống mũi, gờ mày & má có rim highlight #555E88 / #7A66B8 cực nét
    "HORN_SCULPT":    (640, 832, 256, 192),  # Sừng chính & sừng má có gốc tím sẫm + vân gờ đốt + viền sáng cạnh
}


def backup_existing_files() -> None:
    """Sao lưu bản v2 trước khi ghi đè v3 (giữ nguyên backup_jormungandr_v1 nếu đã tồn tại)."""
    for target_dir in (BACKUP_DIR, BACKUP_V2_DIR):
        if target_dir.exists() and any(target_dir.rglob("*.json")):
            continue
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
                dst = target_dir / f.relative_to(ASSETS)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, dst)
                copied += 1
        if copied > 0:
            print(f"BACKUP_OK: Đã sao lưu {copied} file vào {target_dir}")


def uv_box(mat_key: str, pad: int = 10, sub_u=(0.0, 1.0), sub_v=(0.0, 1.0)) -> dict:
    x0, y0, w, h = MAT[mat_key]
    iw, ih = w - 2 * pad, h - 2 * pad
    ux = int(round(x0 + pad + sub_u[0] * iw))
    uy = int(round(y0 + pad + sub_v[0] * ih))
    uw = max(4, int(round((sub_u[1] - sub_u[0]) * iw)))
    uh = max(4, int(round((sub_v[1] - sub_v[0]) * ih)))
    f = {"uv": [ux, uy], "uv_size": [uw, uh]}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def make_cube(origin, size, mat: str, rot=None, pivot=None,
              top_mat=None, bot_mat=None, front_mat=None, side_mat=None,
              sub_u=(0.0, 1.0), sub_v=(0.0, 1.0)) -> dict:
    """Tạo 1 cube Bedrock 1.12.0 luôn kèm pivot tường minh khi xoay (đúng 100% giữa preview & GeckoLib)."""
    ox, oy, oz = float(origin[0]), float(origin[1]), float(origin[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    base = uv_box(mat, sub_u=sub_u, sub_v=sub_v)
    if top_mat:
        base["up"] = uv_box(top_mat)["up"]
    if bot_mat:
        base["down"] = uv_box(bot_mat)["down"]
    if front_mat:
        base["north"] = uv_box(front_mat)["north"]
    if side_mat:
        sf = uv_box(side_mat)["east"]
        base["east"] = sf
        base["west"] = sf

    c = {
        "origin": [round(ox, 3), round(oy, 3), round(oz, 3)],
        "size": [round(sx, 3), round(sy, 3), round(sz, 3)],
        "uv": base,
    }
    if rot is not None and any(abs(v) > 1e-4 for v in rot):
        if pivot is None:
            pivot = [ox + sx * 0.5, oy + sy * 0.5, oz + sz * 0.5]
        c["pivot"] = [round(float(pivot[0]), 3), round(float(pivot[1]), 3), round(float(pivot[2]), 3)]
        c["rotation"] = [round(float(rot[0]), 2), round(float(rot[1]), 2), round(float(rot[2]), 2)]
    return c


def mirror_x_cube(c: dict) -> dict:
    """Phản chiếu hoàn hảo 1 cube qua mặt phẳng X = 0 (đảo dấu origin.x, pivot.x, rotY, rotZ)."""
    ox, oy, oz = c["origin"]
    sx, sy, sz = c["size"]
    m = {
        "origin": [round(-(ox + sx), 3), oy, oz],
        "size": [sx, sy, sz],
        "uv": c["uv"],
    }
    if "rotation" in c:
        rx, ry, rz = c["rotation"]
        px, py, pz = c["pivot"]
        m["pivot"] = [round(-px, 3), py, pz]
        m["rotation"] = [rx, round(-ry, 2), round(-rz, 2)]
    return m


# =============================================================================
# 1. XÂY DỰNG HÌNH HỌC MODEL CHI TIẾT CAO (ĐẦU V3 RỘNG, MẮT SÁNG RÕ TỪ DƯỚI LÊN, GIỮ NGUYÊN THÂN!)
# =============================================================================
def add_tapered_chain(out_list: list, points: list, radii: list, mat: str,
                      overlap: float = 0.8, sub_v_span=(0.0, 1.0)) -> None:
    """Dựng 1 chuỗi đốt cong mượt thuôn nhọn (răng nanh, sừng chính, sừng má) nối khít 100% không khe hở."""
    n = len(points) - 1
    for i in range(n):
        a = np.array(points[i], dtype=np.float64)
        b = np.array(points[i + 1], dtype=np.float64)
        vec = b - a
        seg_len = float(np.linalg.norm(vec))
        if seg_len < 1e-4:
            continue
        u = vec / seg_len
        horiz = math.hypot(u[0], u[2])
        rx_gl = math.atan2(horiz, u[1])
        ry_gl = math.atan2(u[0], u[2]) if horiz > 1e-5 else 0.0
        rot_deg = [-math.degrees(rx_gl), -math.degrees(ry_gl), 0.0]
        w_mid = 0.5 * (radii[i] + radii[i + 1])
        v0 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * (i / float(n))
        v1 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * ((i + 1) / float(n))
        out_list.append(make_cube(
            [a[0] - w_mid * 0.5, a[1] - overlap * 0.45, a[2] - w_mid * 0.5],
            [w_mid, seg_len + overlap, w_mid],
            mat,
            rot=rot_deg,
            pivot=a.tolist(),
            sub_v=(v0, v1),
        ))


def build_head_and_hood_bones() -> list[dict]:
    bones = []

    # -------------------------------------------------------------------------
    # A. ĐẦU RẮN HỔ MANG V3 — RỘNG BỀ THẾ, MẮT SÁNG FULLBRIGHT NHÌN XUỐNG & VỀ TRƯỚC,
    #    NANH CONG VUỐT NHỌN, SỪNG CHÍNH & SỪNG MÁ SẮC SẢO (~115 cubes riêng `head`!)
    #    Hướng nhìn về -Z, pivot gốc [0, 0, 0] tại khớp cổ-đầu (nối mượt vào segment_1).
    # -------------------------------------------------------------------------
    head_cubes = []

    # 1. Hộp sọ rắn hổ mang mở rộng bề ngang & dày khỏe + cổ áo vảy bọc mượt khớp nối `segment_1`!
    head_cubes.append(make_cube([-10.2, -4.4, -5.0], [20.4, 8.6, 17.5], "HEAD_SCALE"))
    # Khớp nối gáy thuôn ôm kín lấy `segment_1` (z = +8.5..+17.2) chống hở/gãy bậc khi ngẩng cổ hay quay ±45°!
    head_cubes.append(make_cube([-10.6, -4.2, 8.5], [21.2, 8.4, 8.2], "SCALE_BACK", bot_mat="BELLY"))
    head_cubes.append(make_cube([-9.2, -5.8, 7.2], [18.4, 3.2, 9.2], "BELLY", rot=[10, 0, 0]))
    head_cubes.append(make_cube([-7.8, 2.8, 7.8], [15.6, 3.8, 9.4], "HEAD_SCALE", rot=[-14, 0, 0]))
    # Tấm giáp đỉnh đầu nhiều tầng xếp lớp sắc nét (vương miện vảy cobra có viền sáng cạnh)
    head_cubes.append(make_cube([-7.8, 2.8, -19.2], [15.6, 3.4, 17.0], "HEAD_RIM_PLATE", rot=[11, 0, 0]))
    head_cubes.append(make_cube([-7.6, 4.2, -5.8], [15.2, 3.2, 16.5], "HEAD_SCALE", rot=[4, 0, 0]))
    head_cubes.append(make_cube([-5.8, 6.0, -3.5], [11.6, 2.3, 14.2], "HEAD_RIM_PLATE", rot=[-6, 0, 0]))
    head_cubes.append(make_cube([-3.6, 7.2, -1.0], [7.2, 1.8, 11.8], "HEAD_RIM_PLATE", rot=[-12, 0, 0]))
    # Mạch năng lượng tím mảnh chạy dọc chính giữa đỉnh sọ xuống gáy
    head_cubes.append(make_cube([-1.3, 7.6, -12.5], [2.6, 1.3, 21.0], "DORSAL_VEIN", rot=[-4, 0, 0]))

    # 2. Mõm trên xếp lớp nhiều tầng + gờ sống mũi nổi + 2 lỗ mũi chạm gờ sắc nét!
    head_cubes.append(make_cube([-8.8, -2.6, -19.8], [17.6, 6.8, 16.0], "HEAD_SCALE"))
    head_cubes.append(make_cube([-6.8, -2.4, -27.5], [13.6, 5.8, 9.5], "HEAD_SCALE"))
    head_cubes.append(make_cube([-4.8, -2.2, -32.2], [9.6, 4.6, 6.0], "HEAD_RIM_PLATE", rot=[7, 0, 0]))
    # Gờ sống mũi nổi 3 tầng có viền sáng thép-tím chạy thẳng ra chóp mõm
    head_cubes.append(make_cube([-3.6, 4.2, -25.8], [7.2, 2.3, 16.2], "HEAD_RIM_PLATE", rot=[10, 0, 0]))
    head_cubes.append(make_cube([-2.4, 3.2, -31.2], [4.8, 2.0, 7.6], "HEAD_RIM_PLATE", rot=[16, 0, 0]))
    head_cubes.append(make_cube([-1.3, 2.4, -33.2], [2.6, 1.5, 4.2], "HEAD_RIM_PLATE", rot=[22, 0, 0]))
    # Tấm giáp đầu mõm sắc gọn
    head_cubes.append(make_cube([-4.0, -2.9, -33.4], [8.0, 3.4, 2.6], "LIP_ARMOR"))

    # 3. Vòm họng trên đỏ thẫm sâu rộng nằm kín bên trong hàm
    head_cubes.append(make_cube([-7.8, -3.9, -26.5], [15.6, 2.5, 23.0], "MOUTH_RED"))
    head_cubes.append(make_cube([-6.2, -3.4, -6.5], [12.4, 5.4, 10.0], "MOUTH_RED"))

    # 4. CÁC CẤU TRÚC ĐỐI XỨNG HAI BÊN ĐẦU (MẮT ƯU TIÊN #1, GỜ MÀY, MÁ GÓC CẠNH, NANH CONG, SỪNG MÁ!)
    side_head = [
        # --- (a) Gò má & góc hàm mở rộng bề thế (vươn ra x = ±16.8 px -> áp đảo hoàn toàn phần cổ!) ---
        make_cube([8.4, -4.4, -5.2], [8.6, 7.4, 16.0], "HEAD_SCALE", rot=[0, -20, 14]),
        make_cube([11.8, -3.2, 1.8], [5.8, 6.0, 11.4], "HEAD_RIM_PLATE", rot=[0, -28, 18]),
        make_cube([12.6, -4.6, 4.2], [4.4, 3.8, 9.2], "HEAD_RIM_PLATE", rot=[-6, -32, 12]),
        # Tấm thái dương vát xiên nhiều lớp ôm mượt từ chỏm sọ xuống góc hàm (xóa sạch dáng hộp)
        make_cube([5.8, 2.4, -7.8], [6.6, 3.0, 17.5], "HEAD_RIM_PLATE", rot=[4, -14, 36]),
        make_cube([7.6, 1.0, 0.8], [6.2, 2.8, 13.0], "HEAD_SCALE", rot=[-2, -22, 42]),
        # Má dưới TRƯỚC MẮT thu gọn nhẹ (x <= 9.6, y <= -0.8) -> TUYỆT ĐỐI KHÔNG CHE KHUẤT MẮT TỪ DƯỚI LÊN!
        make_cube([5.6, -3.6, -19.2], [4.0, 3.0, 14.0], "HEAD_SCALE", rot=[0, -16, 4]),
        # Viền tấm vảy môi hàm trên chia đốt sắc nét
        make_cube([7.2, -4.5, -18.5], [3.2, 2.6, 17.5], "LIP_ARMOR", rot=[0, -14, 0]),
        make_cube([4.4, -4.1, -28.6], [3.0, 2.4, 11.8], "LIP_ARMOR", rot=[0, -22, 0]),

        # --- (b) CỤM MẮT ƯU TIÊN SỐ 1: TO, RÕ, VÁT VỀ TRƯỚC 34° & NGHIÊNG NHÌN XUỐNG 28°! ---
        # Nền hốc mắt đen sâu (#050508) nghiêng rot=[8, -34, -28] -> pháp tuyến (+0.73 ngoài, -0.39 XUỐNG, -0.56 TRƯỚC)!
        make_cube([8.6, 0.1, -20.5], [3.4, 4.9, 11.2], "EYE_SOCKET", rot=[8, -34, -28]),
        # Gờ mày giáp sắc lẹm vát xiên cau mày ép sát MÉP TRÊN & ĐUÔI MẮT tạo ánh nhìn rắn độc dữ tợn (không che phía dưới!)
        make_cube([7.5, 3.85, -20.6], [6.5, 2.3, 13.4], "HEAD_RIM_PLATE", rot=[17, -25, 22]),
        make_cube([5.8, 4.15, -24.0], [4.9, 2.0, 7.8], "HEAD_RIM_PLATE", rot=[22, -29, 18]),
        make_cube([10.9, 2.95, -10.8], [4.3, 2.1, 7.4], "HEAD_RIM_PLATE", rot=[4, -30, 12]),

        # --- (c) Hai lỗ mũi chạm gờ nổi hai bên chóp mõm ---
        make_cube([3.2, 1.3, -29.2], [2.8, 2.2, 5.8], "HEAD_RIM_PLATE", rot=[6, -22, 12]),
        make_cube([4.8, 1.1, -28.4], [1.3, 1.5, 2.4], "EYE_SOCKET", rot=[6, -22, 12]),
    ]

    # Dựng CUBE TRÒNG MẮT TÁCH RIÊNG nổi +0.18 px trên hốc mắt đen (UV 256x192 fullbright rực rỡ!)
    # Lưu ý: mặt ngoài (`east` bên trái / `west` bên phải) hiển thị trọn vẹn tròng mắt + con ngươi khe dọc;
    # mặt trước (`north`) và mặt dưới (`down`) hiển thị tròng sáng rực #FF3322/#FFE699 để nhìn từ trực diện
    # hoặc từ dưới đất ngước lên đều thấy ánh mắt đỏ rực sắc lẹm!
    eye_l = make_cube([10.55, 0.65, -19.8], [2.6, 3.85, 9.8], "EYE_SLIT_HQ", rot=[8, -34, -28])
    glow_edge_uv = uv_box("EYE_SLIT_HQ", pad=12, sub_u=(0.14, 0.36), sub_v=(0.24, 0.76))["north"]
    eye_l["uv"]["north"] = glow_edge_uv
    eye_l["uv"]["down"] = glow_edge_uv
    eye_l["uv"]["up"] = uv_box("EYE_SOCKET")["up"]
    side_head.append(eye_l)

    # --- (d) 1 CẶP SỪNG/GAI MÁ PHỤ NGẮN (4 đốt cong vuốt sắc ôm xiên ra sau hai bên góc hàm!) ---
    cheek_horn_pts = [
        [14.2, -2.0, 5.0],
        [16.8, -1.6, 10.8],
        [18.8, -0.8, 16.5],
        [20.0,  0.4, 21.8],
        [20.4,  1.6, 25.8],
    ]
    cheek_horn_r = [3.1, 2.4, 1.75, 1.15, 0.55]
    add_tapered_chain(side_head, cheek_horn_pts, cheek_horn_r, "HORN_SCULPT", overlap=0.75, sub_v_span=(0.0, 1.0))
    # Gai góc hàm nhỏ thứ 2 ôm dưới sừng má
    add_tapered_chain(side_head,
                      [[13.0, -4.4, 6.2], [15.2, -4.8, 11.4], [16.8, -4.6, 15.8]],
                      [2.1, 1.35, 0.55], "HORN_SCULPT", overlap=0.65, sub_v_span=(0.15, 1.0))

    # --- (e) 2 RĂNG NANH ĐỘC CHÍNH DÀI, CONG MƯỢT VÀO TRONG & VUỐT NHỌN RÕ RỆT (4 đốt mượt, gọn trong khung hàm!) ---
    fang_pts = [
        [5.3, -3.5, -26.4],
        [5.1, -6.2, -25.8],
        [4.7, -8.7, -24.7],
        [4.1, -10.8, -23.4],
        [3.4, -12.4, -22.1],
    ]
    fang_r = [2.15, 1.62, 1.12, 0.68, 0.30]
    add_tapered_chain(side_head, fang_pts, fang_r, "FANG_BONE", overlap=0.42, sub_v_span=(0.0, 1.0))

    # --- (f) Hàng răng nanh phụ nhỏ sắc gọn dọc viền hàm trên (4 răng thuôn nhọn, KHÔNG còn cột vuông!) ---
    for r_idx, (rx_f, rz_f, r_len, r_w) in enumerate((
        (6.8, -21.4, 3.4, 1.15),
        (7.7, -17.4, 3.0, 1.05),
        (8.5, -13.4, 2.6, 0.95),
        (9.2,  -9.6, 2.2, 0.85),
    )):
        add_tapered_chain(side_head,
                          [[rx_f, -3.9, rz_f], [rx_f - 0.3, -3.9 - r_len * 0.55, rz_f + 0.5], [rx_f - 0.5, -3.9 - r_len, rz_f + 1.2]],
                          [r_w, r_w * 0.65, 0.28], "FANG_BONE", overlap=0.25, sub_v_span=(0.12, 0.85))

    for sc in side_head:
        head_cubes.append(sc)
        head_cubes.append(mirror_x_cube(sc))

    bones.append({
        "name": "head",
        "pivot": [0.0, 0.0, 0.0],
        "locators": {
            "mouth_venom": [0.0, -7.8, -25.8],
        },
        "cubes": head_cubes,
    })

    bones.append({"name": "mouth_venom", "parent": "head", "pivot": [0.0, -7.8, -25.8], "cubes": []})

    # -------------------------------------------------------------------------
    # B. HÀM DƯỚI (`jaw` — DÀY KHỎE HƠN, BO VÁT THON VỀ CHÓP CẰM, CHUYỂN MƯỢT VÀO VẢY BỤNG CỔ!)
    # -------------------------------------------------------------------------
    jaw_cubes = [
        # Khung hàm dưới dày khỏe mở rộng góc hàm sau, vát thon mạnh về chóp cằm
        make_cube([-9.8, -7.4, -14.5], [19.6, 4.0, 20.5], "HEAD_SCALE", bot_mat="BELLY"),
        make_cube([-7.2, -6.9, -23.8], [14.4, 3.5, 10.6], "HEAD_SCALE", bot_mat="BELLY"),
        make_cube([-4.5, -6.3, -29.6], [9.0, 3.1, 6.6], "LIP_ARMOR", bot_mat="BELLY"),
        make_cube([-2.6, -5.8, -31.8], [5.2, 2.5, 2.8], "HEAD_RIM_PLATE", bot_mat="BELLY", rot=[-10, 0, 0]),
        # Vảy cằm dưới chuyển sắc tự nhiên vào dải vảy bụng xanh-tím của cổ (`segment_1`)
        make_cube([-8.2, -8.3, -15.0], [16.4, 1.6, 21.5], "BELLY"),
        make_cube([-5.6, -7.6, -25.2], [11.2, 1.4, 11.4], "BELLY"),
        make_cube([-7.4, -7.9, 4.2], [14.8, 2.4, 7.6], "BELLY", rot=[14, 0, 0]),
        # Sàn miệng đỏ sẫm có chiều sâu nằm gọn bên trong
        make_cube([-6.6, -4.3, -25.2], [13.2, 1.6, 21.0], "MOUTH_RED"),
    ]
    jaw_side = [
        # Cánh giáp góc hàm dưới có rim highlight tạo đường nét hàm sắc khỏe
        make_cube([8.0, -6.8, -6.0], [3.6, 3.6, 12.2], "HEAD_RIM_PLATE", rot=[0, -18, 12]),
        make_cube([7.4, -6.3, -16.5], [2.6, 2.8, 17.0], "LIP_ARMOR", rot=[0, -15, 0]),
        make_cube([4.4, -6.0, -27.2], [2.6, 2.5, 12.2], "LIP_ARMOR", rot=[0, -23, 0]),
        # Hàng răng nanh nhỏ sắc gọn dọc viền hàm dưới (thuôn nhọn, cong nhẹ vào trong!)
    ]
    for rx_j, rz_j, r_len, r_w in (
        (3.8, -26.4, 3.2, 1.15),
        (5.4, -21.6, 2.6, 0.98),
        (6.6, -16.8, 2.3, 0.90),
        (7.5, -12.2, 2.0, 0.82),
    ):
        add_tapered_chain(jaw_side,
                          [[rx_j, -4.2, rz_j], [rx_j - 0.3, -4.2 + r_len * 0.55, rz_j + 0.4], [rx_j - 0.5, -4.2 + r_len, rz_j + 1.0]],
                          [r_w, r_w * 0.62, 0.26], "FANG_BONE", overlap=0.22, sub_v_span=(0.12, 0.85))
    for jc in jaw_side:
        jaw_cubes.append(jc)
        jaw_cubes.append(mirror_x_cube(jc))

    bones.append({
        "name": "jaw",
        "parent": "head",
        "pivot": [0.0, -3.8, 4.5],
        "cubes": jaw_cubes,
    })

    # -------------------------------------------------------------------------
    # C. LƯỠI CHẺ ĐÔI (`tongue` — MẢNH GỌN TRONG KHOANG MIỆNG, KHÔNG BAO GIỜ BAY RỜI!)
    # -------------------------------------------------------------------------
    tongue_cubes = [
        make_cube([-1.5, -3.8, -9.0], [3.0, 0.85, 7.0], "MOUTH_RED"),
        make_cube([-1.2, -3.7, -15.0], [2.4, 0.75, 6.5], "MOUTH_RED"),
        make_cube([-0.9, -3.6, -19.8], [1.8, 0.68, 5.2], "MOUTH_RED"),
        make_cube([0.2, -3.6, -25.4], [0.85, 0.58, 6.0], "MOUTH_RED", rot=[0, -22, 0], pivot=[0.45, -3.3, -19.6]),
        make_cube([-1.05, -3.6, -25.4], [0.85, 0.58, 6.0], "MOUTH_RED", rot=[0, 22, 0], pivot=[-0.45, -3.3, -19.6]),
    ]
    bones.append({
        "name": "tongue",
        "parent": "jaw",
        "pivot": [0.0, -3.5, -4.2],
        "cubes": tongue_cubes,
    })

    # -------------------------------------------------------------------------
    # D. 1 CẶP SỪNG CHÍNH CONG VUỐT MƯỢT RA SAU (`horn_l`, `horn_r` — 6 đốt thuôn nhọn liền khối,
    #    gốc ánh tím sẫm + vân gờ đốt + viền sáng cạnh, XÓA HOÀN TOÀN dáng thanh hộp thô!)
    # -------------------------------------------------------------------------
    horn_l_cubes = []
    horn_pts = [
        [8.4,  5.6, -1.5],
        [10.8, 9.8,  5.5],
        [13.8, 12.8, 13.5],
        [16.8, 14.2, 22.0],
        [19.2, 14.4, 30.2],
        [20.8, 13.8, 37.5],
        [21.6, 12.6, 43.4],
    ]
    horn_radii = [4.6, 3.8, 3.05, 2.35, 1.65, 1.05, 0.45]
    add_tapered_chain(horn_l_cubes, horn_pts, horn_radii, "HORN_SCULPT", overlap=1.1, sub_v_span=(0.0, 1.0))
    # 3 vòng gờ sừng nổi sắc nét + mạch ánh tím mảnh chạy dọc sống lưng sừng (nổi bật cả ngoài nắng!)
    for idx_r in (1, 2, 3):
        pm = np.array(horn_pts[idx_r], dtype=np.float64)
        rr = horn_radii[idx_r] * 1.14
        horn_l_cubes.append(make_cube([pm[0] - rr * 0.5, pm[1] - rr * 0.5, pm[2] - rr * 0.35],
                                      [rr, rr, rr * 0.7], "HEAD_RIM_PLATE", rot=[-16, -24, 10]))
    add_tapered_chain(horn_l_cubes,
                      [[8.8, 7.6, 0.2], [11.4, 11.4, 7.2], [14.4, 14.0, 15.2], [17.2, 15.1, 23.4]],
                      [1.05, 0.88, 0.72, 0.48], "DORSAL_VEIN", overlap=0.7)

    horn_r_cubes = [mirror_x_cube(c) for c in horn_l_cubes]
    bones.append({"name": "horn_l", "parent": "head", "pivot": [8.4, 5.6, -1.5], "cubes": horn_l_cubes})
    bones.append({"name": "horn_r", "parent": "head", "pivot": [-8.4, 5.6, -1.5], "cubes": horn_r_cubes})

    # -------------------------------------------------------------------------
    # E. MANG CỔ BÀNH RẮN HỔ MANG V3 (`hood_l`, `hood_r` — MỞ RỘNG ÔM ĐẦU TO 1.35x,
    #    VIỀN VẢY ĐEN CONG SẮC NÉT, MÀNG TÍM DẠ QUANG MỎNG TỰ NHIÊN + GÂN SÁNG FULLBRIGHT!)
    # -------------------------------------------------------------------------
    hood_l_cubes = []
    bands = 9
    y_top, y_bot = 3.6, -47.5
    z_plane = 9.2
    inner_x = 12.2

    def hood_width(t: float) -> float:
        # Dáng cánh mang hổ mang chúa mở rộng thanh thoát ôm lấy cụm đầu v3 to bề thế!
        return 5.4 + 27.6 * (math.sin(math.pi * (t ** 0.74)) ** 1.04)

    def hood_y(t: float) -> float:
        return y_top + (y_bot - y_top) * t

    # Dải nối gốc ôm kín lấy sườn gáy/cổ
    hood_l_cubes.append(make_cube([9.4, y_bot + 1.0, z_plane - 1.6], [5.4, abs(y_top - y_bot) - 1.5, 4.4], "SCALE_BACK"))

    for k in range(bands):
        t0 = k / float(bands)
        t1 = (k + 1) / float(bands)
        y0_b, y1_b = hood_y(t0), hood_y(t1)
        ym = 0.5 * (y0_b + y1_b)
        band_h = abs(y0_b - y1_b) + 1.0
        w0, w1 = hood_width(t0), hood_width(t1)
        wm = 0.5 * (w0 + w1)

        # 1. Tấm màng mang cổ MỎNG liền mạch (1.6 px — không có đường cắt ngang giữa các băng!)
        mem_w = max(4.0, wm - 2.4)
        z_jit = 0.018 if (k % 2 == 0) else -0.018
        mem_cube = make_cube(
            [inner_x, y1_b - 0.02, z_plane + z_jit],
            [mem_w, abs(y0_b - y1_b) + 0.04, 1.56],
            "HOOD_MEMBRANE",
            sub_u=(0.04, min(0.96, 0.14 + 0.82 * (mem_w / 30.0))),
            sub_v=(max(0.02, t0 * 0.94 + 0.02), min(0.98, t1 * 0.94 + 0.04)),
        )
        mid_violet_uv = uv_box("HOOD_MEMBRANE", sub_u=(0.14, 0.24), sub_v=(0.45, 0.55))["up"]
        mem_cube["uv"]["up"] = mid_violet_uv
        mem_cube["uv"]["down"] = mid_violet_uv
        hood_l_cubes.append(mem_cube)

        # 2. Giáp vảy đen bóng mặt sau màng
        hood_l_cubes.append(make_cube(
            [inner_x + 0.1, y1_b - 0.4, z_plane + 1.55],
            [mem_w + 0.4, band_h - 0.2, 1.4],
            "SCALE_BACK",
        ))

        # 3. Viền ngoài bằng vảy đen dày uốn cong sắc nét mượt mà
        rim_cx = inner_x + mem_w + 0.7
        dw = w1 - w0
        dy = y1_b - y0_b
        tangent_z = math.degrees(math.atan2(dw, abs(dy)))
        rim_len = math.hypot(dw, abs(dy)) + 2.6
        hood_l_cubes.append(make_cube(
            [rim_cx - 2.2, ym - rim_len * 0.5, z_plane - 0.8],
            [4.4, rim_len, 3.8],
            "HEAD_SCALE",
            rot=[0, 0, tangent_z],
            pivot=[rim_cx, ym, z_plane + 1.1],
        ))

    # 4. Năm nan xương sườn thanh mảnh phát sáng tỏa đều dọc mặt trước màng mang cổ
    for rt, r_angle in ((0.16, 12.0), (0.32, 5.0), (0.49, -2.0), (0.66, -10.0), (0.81, -18.0)):
        ry_p = hood_y(rt)
        rw_p = max(4.5, hood_width(rt) - 3.8)
        hood_l_cubes.append(make_cube(
            [inner_x + 0.2, ry_p - 0.45, z_plane - 0.42],
            [rw_p, 0.9, 0.82],
            "HOOD_RIB",
            rot=[0, 0, r_angle],
            pivot=[inner_x + 0.2, ry_p, z_plane],
        ))

    hood_r_cubes = [mirror_x_cube(c) for c in hood_l_cubes]

    bones.append({
        "name": "hood_l",
        "parent": "head",
        "pivot": [12.2, 0.8, 9.2],
        "rotation": [15.0, -18.0, 0.0],
        "locators": {
            "hood_l_edge": [41.5, -16.5, 9.2],
        },
        "cubes": hood_l_cubes,
    })
    bones.append({
        "name": "hood_r",
        "parent": "head",
        "pivot": [-12.2, 0.8, 9.2],
        "rotation": [15.0, 18.0, 0.0],
        "locators": {
            "hood_r_edge": [-41.5, -16.5, 9.2],
        },
        "cubes": hood_r_cubes,
    })
    bones.append({"name": "hood_l_edge", "parent": "hood_l", "pivot": [41.5, -16.5, 9.2], "cubes": []})
    bones.append({"name": "hood_r_edge", "parent": "hood_r", "pivot": [-41.5, -16.5, 9.2], "cubes": []})

    return bones


def build_body_segments() -> list[dict]:
    """
    Xây dựng 54 đốt thân phẳng (`segment_1 .. segment_54`), mỗi đốt 18 cube (tổng 972+2 cube):
      - Mặt cắt bát giác tròn mượt (lõi + 2 vai vát 38° + 2 hông tròn + 2 chuyển bụng vát 40° + giáp lưng)
      - Gờ sống lưng vảy mũi tên xếp lớp nghiêng về sau + mạch sống lưng phát sáng mảnh
      - 3 bản vảy bụng ngang nằm gọn mặt dưới, chuyển tiếp mượt qua vảy sườn (không nhô cục vuông)
      - 4 mảng khớp lồng phía sau (thu vào trong 0.55 px & thò nhẹ +1.2 px về +Z) chống tuyệt đối z-fighting
        khi chồng mép 34% (`segmentOverlapFactor = 1.34`)!
      - segment_1..6 hơi bè nhẹ mặt trước giống cổ rắn hổ mang; segment_1 có ống lồng sâu vào sau hộp sọ!
    """
    bones = []
    half_l = SEG_LEN_PX * 0.5  # 8.0 px

    for i in range(1, N_MAX + 1):
        # Độ lệch bán kính vi mô xen kẽ chẵn/lẻ (±0.035 px) -> tuyệt đối không đồng phẳng z-fighting
        eps = 0.035 if (i % 2 == 0) else -0.035

        # Cổ trên (segment_1..6) hơi bè nhẹ chiều ngang (+8% -> +1%) kiểu cổ hổ mang, rồi tròn mập đều
        if i <= 6:
            neck_u = 1.0 - (i - 1) / 6.0
            rx = (BODY_RAD_PX + eps) * (1.0 + 0.08 * neck_u)
            ry = (BODY_RAD_PX + eps) * (1.0 - 0.05 * neck_u)
        else:
            rx = BODY_RAD_PX + eps
            ry = BODY_RAD_PX + eps

        cubes = [
            # 1. Lõi bát giác trung tâm đặc (kín mọi khe hở giữa các tấm vát)
            make_cube([-rx * 0.80, -ry * 0.72, -half_l], [rx * 1.60, ry * 1.48, SEG_LEN_PX],
                      "SCALE_BACK", bot_mat="BELLY"),

            # 2-3. Hai tấm vai lưng vát góc ±38° (tạo vòm lưng tròn mượt kiểu rắn thật)
            make_cube([rx * 0.22, ry * 0.36, -half_l + 0.06], [rx * 0.68, ry * 0.44, SEG_LEN_PX - 0.12],
                      "SCALE_BACK", rot=[0, 0, 38], pivot=[rx * 0.56, ry * 0.58, 0.0]),
            make_cube([-rx * 0.90, ry * 0.36, -half_l + 0.06], [rx * 0.68, ry * 0.44, SEG_LEN_PX - 0.12],
                      "SCALE_BACK", rot=[0, 0, -38], pivot=[-rx * 0.56, ry * 0.58, 0.0]),

            # 4-5. Hai tấm giáp hông tròn hai bên (đường xích đạo ngoài cùng)
            make_cube([rx * 0.74, -ry * 0.40, -half_l + 0.08], [rx * 0.28, ry * 0.82, SEG_LEN_PX - 0.16],
                      "SCALE_BACK"),
            make_cube([-rx * 1.02, -ry * 0.40, -half_l + 0.08], [rx * 0.28, ry * 0.82, SEG_LEN_PX - 0.16],
                      "SCALE_BACK"),

            # 6-7. Hai tấm chuyển tiếp hông-bụng vát góc ±40° (chuyển mượt từ vảy đen sang vảy bụng xanh-tím!)
            make_cube([rx * 0.24, -ry * 0.82, -half_l + 0.10], [rx * 0.64, ry * 0.34, SEG_LEN_PX - 0.20],
                      "SCALE_BACK", bot_mat="BELLY", rot=[0, 0, -40], pivot=[rx * 0.56, -ry * 0.65, 0.0]),
            make_cube([-rx * 0.88, -ry * 0.82, -half_l + 0.10], [rx * 0.64, ry * 0.34, SEG_LEN_PX - 0.20],
                      "SCALE_BACK", bot_mat="BELLY", rot=[0, 0, 40], pivot=[-rx * 0.56, -ry * 0.65, 0.0]),

            # 8. Giáp đỉnh sống lưng phẳng bo nhẹ
            make_cube([-rx * 0.50, ry * 0.72, -half_l + 0.08], [rx * 1.00, ry * 0.24, SEG_LEN_PX - 0.16],
                      "SCALE_BACK"),

            # 9-10. Gờ sống lưng: đoạn gáy cổ (segment_1..6) vát nhọn xuôi mượt ra sau thu nhỏ dần (xóa các thanh chữ nhật đứng!);
            #       toàn bộ phần thân cuộn (segment_7..54) GIỮ NGUYÊN 100%!
            *(
                (
                    make_cube([-rx * 0.11, ry * 0.84, -half_l + 1.2], [rx * 0.22, ry * (0.26 - 0.015 * i), 9.4],
                              "HORN_SCULPT", rot=[-46, 0, 0], pivot=[0.0, ry * 0.86, -half_l + 5.0]),
                    make_cube([-rx * 0.09, ry * 0.84, 0.4], [rx * 0.18, ry * (0.22 - 0.012 * i), 9.0],
                              "HORN_SCULPT", rot=[-48, 0, 0], pivot=[0.0, ry * 0.86, 4.4]),
                ) if i <= 6 else (
                    make_cube([-rx * 0.16, ry * 0.88, -half_l + 0.8], [rx * 0.32, ry * 0.30, 8.2],
                              "HORN_DARK", rot=[-22, 0, 0], pivot=[0.0, ry * 0.90, -half_l + 4.8]),
                    make_cube([-rx * 0.14, ry * 0.88, 0.2], [rx * 0.28, ry * 0.28, 8.2],
                              "HORN_DARK", rot=[-25, 0, 0], pivot=[0.0, ry * 0.90, 4.2]),
                )
            ),

            # 11. Mạch sống lưng mảnh phát sáng (mờ ở Phase 1, rực tím-trắng ở Phase 2)
            make_cube([-rx * 0.09, ry * 0.93, -half_l + 0.3], [rx * 0.18, ry * 0.10, SEG_LEN_PX - 0.6],
                      "DORSAL_VEIN"),

            # 12-14. Ba bản vảy bụng ngang (ventral scutes) nằm gọn mặt dưới, xếp vảy nghiêng 3° cực tự nhiên!
            make_cube([-rx * 0.62, -ry * 0.92, -7.8], [rx * 1.24, ry * 0.22, 5.4],
                      "BELLY", rot=[3, 0, 0], pivot=[0.0, -ry * 0.82, -5.1], sub_v=(0.02, 0.34)),
            make_cube([-rx * 0.62, -ry * 0.92, -2.7], [rx * 1.24, ry * 0.22, 5.4],
                      "BELLY", rot=[3, 0, 0], pivot=[0.0, -ry * 0.82, 0.0], sub_v=(0.33, 0.66)),
            make_cube([-rx * 0.62, -ry * 0.92, 2.4], [rx * 1.24, ry * 0.22, 5.4],
                      "BELLY", rot=[3, 0, 0], pivot=[0.0, -ry * 0.82, 5.1], sub_v=(0.66, 0.98)),

            # 15-18. Khớp lồng phía sau thu nhỏ nhẹ vào trong (inset = 0.55 px ≈ 0.034 block)
            #        nối lồng vào lòng đốt kế tiếp -> chống z-fighting & chống hở khe ở góc cuộn gắt!
            make_cube([-rx * 0.72, ry * 0.42, 2.5], [rx * 1.44, ry * 0.44, 6.8], "SCALE_BACK"),
            make_cube([-rx * 0.56, -ry * 0.85, 2.5], [rx * 1.12, ry * 0.36, 6.8], "BELLY"),
            make_cube([rx * 0.58, -ry * 0.54, 2.5], [rx * 0.34, ry * 1.08, 6.8], "SCALE_BACK"),
            make_cube([-rx * 0.92, -ry * 0.54, 2.5], [rx * 0.34, ry * 1.08, 6.8], "SCALE_BACK"),
        ]

        # Đốt cổ đầu tiên (`segment_1`) thêm ống cổ lồng sâu vào hộp sọ của `head` (-Z tới -14.5 px)
        # đảm bảo nối liền mạch tuyệt đối trong mọi tư thế quay ±45°, ngẩng cổ hay Strike Bite!
        if i == 1:
            cubes.append(make_cube([-rx * 0.64, -ry * 0.54, -14.5], [rx * 1.28, ry * 1.10, 7.2],
                                   "SCALE_BACK", bot_mat="BELLY"))
            cubes.append(make_cube([-rx * 0.46, -ry * 0.66, -13.5], [rx * 0.92, ry * 0.28, 6.2],
                                   "BELLY"))

        bones.append({
            "name": f"segment_{i}",
            "pivot": [0.0, 0.0, 0.0],
            "cubes": cubes,
        })

    return bones


def build_tail_tip_bones() -> list[dict]:
    """
    Đầu đuôi (`tail_tip`, 26 cubes + locator `tail_tip_fx`):
      - Ống khớp nối trước (z = -5..+2) lồng khít vào đốt cuối cùng `segment_N`
      - 4 tầng thuôn bát giác liền mạch nối tiếp độ thon của thân
      - Cụm gai sừng / lưỡi dao cổ đại xếp tầng cuối đuôi kèm khe phát sáng tím nhẹ (rực ở Phase 2)
    """
    r = BODY_RAD_PX  # Chú ý: tail_tip được đặt diameterScale = ~0.23 ở Java -> dùng bán kính gốc đồng bộ!
    cubes = [
        # Khớp lồng trước ăn sâu vào đuôi đốt cuối
        make_cube([-r * 0.86, -r * 0.78, -5.0], [r * 1.72, r * 1.56, 8.0], "SCALE_BACK", bot_mat="BELLY"),
        # Tầng thuôn 1 (z = 2..12)
        make_cube([-r * 0.78, -r * 0.70, 2.0], [r * 1.56, r * 1.40, 10.5], "SCALE_BACK", bot_mat="BELLY"),
        make_cube([-r * 0.66, -r * 0.66, 2.5], [r * 1.32, r * 1.32, 9.8], "SCALE_BACK", rot=[0, 0, 45]),
        make_cube([-r * 0.52, -r * 0.78, 2.4], [r * 1.04, r * 0.24, 9.6], "BELLY"),
        make_cube([-r * 0.16, r * 0.68, 3.0], [r * 0.32, r * 0.34, 8.8], "HORN_DARK", rot=[-20, 0, 0]),

        # Tầng thuôn 2 (z = 12..22)
        make_cube([-r * 0.60, -r * 0.54, 11.8], [r * 1.20, r * 1.08, 10.2], "TAIL_CORE", bot_mat="BELLY"),
        make_cube([-r * 0.50, -r * 0.50, 12.2], [r * 1.00, r * 1.00, 9.4], "TAIL_CORE", rot=[0, 0, 45]),
        make_cube([-r * 0.14, r * 0.54, 13.0], [r * 0.28, r * 0.32, 8.2], "HORN_DARK", rot=[-24, 0, 0]),

        # Tầng thuôn 3 — vòng giáp sừng cổ đại & lõi phát sáng tím (z = 21..31)
        make_cube([-r * 0.44, -r * 0.40, 21.2], [r * 0.88, r * 0.80, 9.8], "TAIL_CORE"),
        make_cube([-r * 0.18, -r * 0.18, 21.8], [r * 0.36, r * 0.36, 9.2], "DORSAL_VEIN"),
        make_cube([-r * 0.12, r * 0.42, 22.0], [r * 0.24, r * 0.36, 8.0], "HORN_DARK", rot=[-28, 0, 0]),

        # Cụm gai lưỡi sừng xếp tầng 2 bên (3 cặp gai vát ra sau)
        make_cube([r * 0.40, -r * 0.16, 15.0], [r * 0.26, r * 0.30, 8.8], "HORN_DARK", rot=[0, -24, 0]),
        make_cube([-r * 0.66, -r * 0.16, 15.0], [r * 0.26, r * 0.30, 8.8], "HORN_DARK", rot=[0, 24, 0]),
        make_cube([r * 0.32, -r * 0.14, 23.0], [r * 0.24, r * 0.26, 9.2], "HORN_DARK", rot=[0, -20, 0]),
        make_cube([-r * 0.56, -r * 0.14, 23.0], [r * 0.24, r * 0.26, 9.2], "HORN_DARK", rot=[0, 20, 0]),
        make_cube([r * 0.22, -r * 0.12, 30.2], [r * 0.20, r * 0.22, 8.6], "HORN_DARK", rot=[0, -14, 0]),
        make_cube([-r * 0.42, -r * 0.12, 30.2], [r * 0.20, r * 0.22, 8.6], "HORN_DARK", rot=[0, 14, 0]),

        # Lưỡi kiếm sừng cổ đại chót đuôi (z = 30..50, lõi tím phát sáng ở giữa)
        make_cube([-r * 0.28, -r * 0.28, 30.2], [r * 0.56, r * 0.56, 9.5], "TAIL_CORE"),
        make_cube([-r * 0.12, -r * 0.34, 31.0], [r * 0.24, r * 0.68, 9.0], "HORN_DARK"),
        make_cube([-r * 0.08, -r * 0.14, 31.5], [r * 0.16, r * 0.28, 12.0], "DORSAL_VEIN"),
        make_cube([-r * 0.18, -r * 0.20, 39.2], [r * 0.36, r * 0.40, 7.6], "HORN_DARK"),
        make_cube([-r * 0.10, -r * 0.24, 40.0], [r * 0.20, r * 0.48, 7.2], "HORN_DARK"),
        make_cube([-r * 0.11, -r * 0.12, 46.2], [r * 0.22, r * 0.24, 5.8], "HORN_DARK"),
        # Mũi nhọn cuối cùng
        make_cube([-r * 0.06, -r * 0.08, 51.4], [r * 0.12, r * 0.16, 4.4], "HORN_DARK"),
        # Vây sống lưng & bụng nhỏ ổn định lưỡi đuôi
        make_cube([-r * 0.08, r * 0.24, 34.5], [r * 0.16, r * 0.26, 9.5], "HORN_DARK", rot=[-16, 0, 0]),
        make_cube([-r * 0.08, -r * 0.46, 34.5], [r * 0.16, r * 0.24, 9.0], "HORN_DARK", rot=[16, 0, 0]),
    ]

    return [
        {
            "name": "tail_tip",
            "pivot": [0.0, 0.0, 0.0],
            "locators": {
                "tail_tip_fx": [0.0, 0.0, 52.0],
            },
            "cubes": cubes,
        },
        {"name": "tail_tip_fx", "parent": "tail_tip", "pivot": [0.0, 0.0, 52.0], "cubes": []},
    ]


def build_geo() -> tuple[dict, int]:
    head_bones = build_head_and_hood_bones()
    body_bones = build_body_segments()
    tail_bones = build_tail_tip_bones()
    bones = head_bones + body_bones + tail_bones

    head_cubes_n = sum(len(b.get("cubes", [])) for b in head_bones)
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    assert 950 <= total_cubes <= 1650, f"Cube count {total_cubes} ngoài dải hợp lệ"

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.jormungandr",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 84.0,
                    "visible_bounds_height": 28.0,
                    "visible_bounds_offset": [0.0, 5.0, 0.0],
                },
                "bones": bones,
            }
        ],
    }
    out = ASSETS / "geo" / "entity" / "jormungandr.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} total cubes | head_cluster={head_cubes_n} cubes)")
    return geo, total_cubes


# =============================================================================
# 2. SINH 4 TEXTURE 1024x1024 CHẤT LƯỢNG CAO (NUMPY VECTORIZED — CÓ CHIỀU SÂU 3D & AO)
# =============================================================================
def hex_arr(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], dtype=np.float32)


def draw_cracks_mask(w: int, h: int, seed: int, count: int = 3) -> np.ndarray:
    """Vẽ các tia nứt năng lượng Phase 2 sắc nét, thoáng và có chiều sâu (không rối mắt)."""
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    for k in range(count):
        x = float(w * (0.16 + 0.68 * (k / max(1, count - 1))) + rng.uniform(-w * 0.05, w * 0.05))
        y = 0.0
        pts = [(x, y)]
        for s in range(11):
            y += h / 10.0
            x += float(rng.uniform(-w * 0.06, w * 0.06))
            pts.append((x, y))
        d.line(pts, fill=115, width=6)
        d.line(pts, fill=205, width=3)
        d.line(pts, fill=255, width=1)
        for b_idx in (3, 7):
            bx, by = pts[b_idx]
            dir_x = 1.0 if (k + b_idx) % 2 == 0 else -1.0
            bpts = [(bx, by), (bx + dir_x * w * 0.055, by + h * 0.05), (bx + dir_x * w * 0.10, by + h * 0.095)]
            d.line(bpts, fill=125, width=4)
            d.line(bpts, fill=235, width=2)
    return np.array(img, dtype=np.float32) / 255.0


def build_textures() -> None:
    base = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    base[:, :, :3] = [14, 15, 22]
    base[:, :, 3] = 255

    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    enraged = base.copy()
    enraged_glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    # Bảng màu chuẩn theo yêu cầu (5+ tông đen-xanh than, bụng xanh-tím, mang cổ, nứt P2)
    c_crevice = hex_arr("#0B0B10")
    c_s1 = hex_arr("#151620")
    c_s2 = hex_arr("#212333")
    c_s3 = hex_arr("#32364D")
    c_s4 = hex_arr("#4C5273")

    c_b_center = hex_arr("#6C7BE0")
    c_b_edge = hex_arr("#3A4596")
    c_b_groove = hex_arr("#1E2452")

    c_h_black = hex_arr("#0E0E16")
    c_h_deep = hex_arr("#432B84")
    c_h_mid = hex_arr("#6B4BC4")
    c_h_bright = hex_arr("#BBA3FF")
    c_h_rib = hex_arr("#E6DCFF")

    # Tông rim highlight riêng cho vùng đầu/gờ mày/sừng (xám-xanh thép #555E88 & ánh tím lạnh #7A66B8)
    c_rim_steel = hex_arr("#555E88")
    c_rim_violet = hex_arr("#7A66B8")

    c_p2_outer = hex_arr("#7C2AD1")
    c_p2_mid = hex_arr("#D685FF")
    c_p2_core = hex_arr("#FFFFFF")

    # -------------------------------------------------------------------------
    # 1. SCALE_BACK (384x384): Vảy lục giác/giọt nước xếp lớp CỠ LỚN rõ từng vảy + AO khe vảy
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["SCALE_BACK"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    # Kích thước vảy lớn (48x42 px -> ~8x9 vảy trên 1 ô texture, nhìn cực rõ từ xa!)
    sx_cell, sy_cell = 48.0, 42.0
    row = np.floor(yy / sy_cell)
    x_shift = (row % 2.0) * (sx_cell * 0.5)
    u = ((xx + x_shift) % sx_cell) / sx_cell - 0.5   # [-0.5, +0.5]
    v = (yy % sy_cell) / sy_cell                      # [0, 1] từ đầu vảy (trên) xuống mũi giọt nước (dưới)

    # Hình giọt nước / lục giác vát nhọn xuống dưới
    half_w = np.clip(0.50 - 0.16 * (v ** 1.6), 0.10, 0.52)
    dx_norm = np.abs(u) / half_w
    dy_norm = np.maximum(0.0, (v - 0.52) / 0.48)
    edge_dist = 1.0 - np.maximum(dx_norm ** 1.35, dy_norm ** 1.45)

    # Khối vồng 3D + sống gờ giữa mỗi vảy + ánh sáng cạnh trên
    dome = np.clip(edge_dist, 0.0, 1.0)
    keel = np.exp(-((u * 9.0) ** 2)) * np.clip(1.0 - v * 0.5, 0.0, 1.0) * 0.28
    top_rim = np.exp(-(((v - 0.14) * 7.5) ** 2)) * np.clip(1.0 - np.abs(u) * 1.9, 0.0, 1.0)
    ao = np.clip((edge_dist * 4.2), 0.0, 1.0) * np.clip(1.0 - np.exp(-((1.0 - v) * 8.0)) * 0.55, 0.35, 1.0)

    # Pha 5 tông màu từ khe tối #0B0B10 -> #151620 -> #212333 -> #32364D -> viền sáng #4C5273
    t_body = np.clip(dome * 0.82 + keel, 0.0, 1.0)
    col = c_crevice * (1.0 - ao[:, :, None]) + (
        c_s1 * np.clip(1.0 - t_body * 1.5, 0.0, 1.0)[:, :, None]
        + c_s2 * np.clip(1.0 - np.abs(t_body - 0.45) * 2.2, 0.0, 1.0)[:, :, None]
        + c_s3 * np.clip((t_body - 0.38) * 1.65, 0.0, 1.0)[:, :, None]
    ) * ao[:, :, None]
    # Viền sáng bóng cạnh trên mỗi vảy (#4C5273)
    col = col * (1.0 - top_rim[:, :, None] * 0.72) + c_s4 * (top_rim[:, :, None] * 0.72)
    # Khe rãnh sắc giữa vảy
    crevice_mask = (edge_dist < 0.08).astype(np.float32)
    col = col * (1.0 - crevice_mask[:, :, None] * 0.85) + c_crevice * (crevice_mask[:, :, None] * 0.85)

    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(col, 0, 255).astype(np.uint8)

    # Phase 2 Enraged: khe vảy nứt sáng tím-trắng (#7C2AD1 -> #D685FF -> #FFFFFF) thoáng, sắc nét + ánh tím lạnh viền vảy
    cracks = draw_cracks_mask(w, h, seed=42, count=3)
    e_col = col.copy()
    # Viền trên ánh tím lạnh nhẹ
    e_col = e_col * (1.0 - top_rim[:, :, None] * 0.42) + np.array([112, 86, 174], dtype=np.float32) * (top_rim[:, :, None] * 0.42)
    # 3 tầng màu nứt: #7C2AD1 -> #D685FF -> #FFFFFF
    c_layer = (
        c_p2_outer * np.clip(cracks * 2.2, 0.0, 1.0)[:, :, None] * (1.0 - np.clip((cracks - 0.45) * 2.2, 0.0, 1.0))[:, :, None]
        + c_p2_mid * np.clip((cracks - 0.35) * 2.4, 0.0, 1.0)[:, :, None] * (1.0 - np.clip((cracks - 0.75) * 4.0, 0.0, 1.0))[:, :, None]
        + c_p2_core * np.clip((cracks - 0.72) * 3.8, 0.0, 1.0)[:, :, None]
    )
    mix = np.clip(cracks * 1.35, 0.0, 1.0)[:, :, None]
    e_col = e_col * (1.0 - mix) + np.clip(c_layer, 0, 255) * mix
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(e_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_layer, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(cracks * 250.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 2. HEAD_SCALE (256x256): Tấm vảy đầu rắn hổ mang có rim highlight #555E88 / #7A66B8 nổi rõ ngoài trời nắng!
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["HEAD_SCALE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    hx, hy = 58.0, 50.0
    hr = np.floor(yy / hy)
    hu = ((xx + (hr % 2.0) * (hx * 0.5)) % hx) / hx - 0.5
    hv = (yy % hy) / hy
    h_half_w = np.clip(0.50 - 0.22 * (hv ** 1.45), 0.12, 0.52)
    h_edge = 1.0 - np.maximum((np.abs(hu) / h_half_w) ** 1.3, (np.maximum(0.0, (hv - 0.45) / 0.55)) ** 1.4)
    h_ao = np.clip(h_edge * 4.2, 0.0, 1.0)
    h_keel = np.exp(-((hu * 8.0) ** 2)) * np.clip(1.0 - hv * 0.55, 0.0, 1.0) * 0.34
    h_bevel = np.exp(-(((hv - 0.14) * 7.0) ** 2)) * np.clip(1.0 - np.abs(hu) * 1.85, 0.0, 1.0)
    ht = np.clip(np.clip(h_edge, 0, 1) * 0.8 + h_keel, 0, 1)
    h_col = c_crevice * (1.0 - h_ao[:, :, None]) + (c_s1 * 0.25 + c_s2 * 0.42 + c_s3 * (0.33 + 0.65 * ht)[:, :, None]) * h_ao[:, :, None]
    # Thêm viền sáng cạnh xám-xanh thép (#555E88) & sống vảy ánh tím lạnh (#7A66B8)
    h_col = h_col * (1.0 - h_bevel[:, :, None] * 0.78) + c_rim_steel * (h_bevel[:, :, None] * 0.78)
    h_col = h_col + c_rim_violet * (h_keel * 0.48)[:, :, None]
    h_crev = (h_edge < 0.08).astype(np.float32)
    h_col = h_col * (1.0 - h_crev[:, :, None] * 0.85) + c_crevice * (h_crev[:, :, None] * 0.85)
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(h_col, 0, 255).astype(np.uint8)

    h_cracks = draw_cracks_mask(w, h, seed=99, count=2)
    he_col = h_col * (1.0 - h_cracks[:, :, None] * 0.92) + (c_p2_mid * 0.55 + c_p2_core * 0.45) * h_cracks[:, :, None]
    he_col = he_col * (1.0 - h_bevel[:, :, None] * 0.38) + np.array([126, 98, 192], dtype=np.float32) * (h_bevel[:, :, None] * 0.38)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(he_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip((c_p2_mid * 0.55 + c_p2_core * 0.45) * (h_cracks > 0.15)[:, :, None], 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(h_cracks * 245.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 3. BELLY (256x384): Bản vảy bụng ngang rõ nét (#6C7BE0 tâm -> #3A4596 mép -> #1E2452 rãnh)
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["BELLY"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u_center = 1.0 - np.abs((xx / float(w)) * 2.0 - 1.0)  # 0 mép -> 1 tâm
    center_curve = np.clip(u_center ** 0.72, 0.0, 1.0)
    # 10 bản vảy bụng ngang rõ ràng, có độ cong vòm nhẹ ở giữa & rãnh tối sâu giữa từng bản
    band_h = h / 10.0
    arch_y = yy + 4.5 * (1.0 - center_curve)
    bv = (arch_y % band_h) / band_h  # [0..1] trong 1 bản vảy ngang

    # Rãnh tối #1E2452 tại đáy mỗi bản vảy (bv > 0.86) + bóng hắt ngay dưới rãnh (bv < 0.10)
    groove = np.clip((bv - 0.82) / 0.16, 0.0, 1.0)
    under_shadow = np.clip(1.0 - bv / 0.12, 0.0, 1.0) * 0.35
    ridge_hi = np.exp(-(((bv - 0.24) * 5.2) ** 2)) * center_curve * 0.28
    # Vân sừng dọc cực mảnh tăng cảm giác chất liệu vảy
    grain = 0.96 + 0.04 * np.sin(xx * 0.45 + np.floor(arch_y / band_h) * 2.1)

    b_base = c_b_edge * (1.0 - center_curve[:, :, None]) + c_b_center * center_curve[:, :, None]
    b_col = b_base * grain[:, :, None] * (1.0 - under_shadow[:, :, None]) + np.array([150, 166, 248], dtype=np.float32) * ridge_hi[:, :, None]
    b_col = b_col * (1.0 - groove[:, :, None] * 0.88) + c_b_groove * (groove[:, :, None] * 0.88)
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(b_col, 0, 255).astype(np.uint8)

    # Enraged belly: sáng ánh tím-xanh nhẹ ở tâm bản vảy
    eb_col = b_col * 1.15 + np.array([38, 18, 56], dtype=np.float32) * (center_curve * (1.0 - groove))[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(eb_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_p2_mid * 0.75, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(ridge_hi * 360.0 * (1.0 - groove), 0, 145).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 4. HOOD_MEMBRANE (384x384): Chuyển sắc mượt đen viền -> #3C2B73 -> #B49FF5 -> #DDD2FF
    #    + HOA VĂN MẮT RẮN / RUNE HUYỀN BÍ SẮC SẢO (XÓA SẠCH CHẤM TRÒN ĐEN THÔ!)
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["HOOD_MEMBRANE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx = (xx / float(w)) * 2.0 - 1.0  # [-1, +1]
    ny = (yy / float(h)) * 2.0 - 1.0  # [-1, +1]
    r_oval = np.sqrt((nx * 1.06) ** 2 + (ny * 0.92) ** 2)

    # Chuyển sắc 4 tầng cực mượt từ mép ngoài vào trung tâm màng mang
    t1 = np.clip(1.0 - r_oval, 0.0, 1.0)
    hm_col = (
        c_h_black * np.clip((r_oval - 0.84) * 5.5, 0.0, 1.0)[:, :, None]
        + c_h_deep * np.clip(1.0 - np.abs(r_oval - 0.70) * 3.0, 0.0, 1.0)[:, :, None]
        + c_h_mid * np.clip(1.0 - np.abs(r_oval - 0.42) * 2.4, 0.0, 1.0)[:, :, None]
        + c_h_bright * np.clip(1.0 - r_oval * 1.75, 0.0, 1.0)[:, :, None]
    )
    # Chuẩn hóa trọng số
    w_sum = (
        np.clip((r_oval - 0.84) * 5.5, 0.0, 1.0)
        + np.clip(1.0 - np.abs(r_oval - 0.70) * 3.0, 0.0, 1.0)
        + np.clip(1.0 - np.abs(r_oval - 0.42) * 2.4, 0.0, 1.0)
        + np.clip(1.0 - r_oval * 1.75, 0.0, 1.0)
    )
    hm_col = hm_col / np.maximum(0.001, w_sum)[:, :, None]

    # Gân tia nan quạt sáng thanh mảnh tỏa từ rìa trong (-X) ra mép ngoài (+X)
    fan_angle = np.arctan2(ny * 0.9, nx + 1.15)
    rib_wave = np.exp(-((np.sin(fan_angle * 6.5) * 9.0) ** 2)) * np.clip(1.0 - np.clip((r_oval - 0.88) * 8.0, 0.0, 1.0), 0.0, 1.0)

    # Hoa văn Mắt Rắn Thần Thoại / Cổ Ngữ (Spectacle Rune) sắc sảo ở vùng trung tâm
    eye_dx = (nx - 0.04) * 2.35
    eye_dy = (ny + 0.04) * 1.65
    eye_r = np.sqrt(eye_dx ** 2 + eye_dy ** 2)
    # Vòng ngoài hình thoi/mắt rắn sắc nét
    almond = np.abs(eye_dx) + 0.55 * (eye_dy ** 2)
    outer_rune = np.exp(-(((almond - 0.62) * 18.0) ** 2)) * (eye_r < 0.92)
    inner_rune = np.exp(-(((almond - 0.36) * 22.0) ** 2)) * (eye_r < 0.65)
    dark_iris = np.clip((0.35 - almond) * 14.0, 0.0, 1.0) * (eye_r < 0.55)
    slit_glow = np.exp(-(((eye_dx * 16.0) ** 2) + ((eye_dy * 3.2) ** 2)))
    # Dải hoa văn sóng cổ ngữ chạy dọc viền ngoài màng mang
    rim_rune = np.exp(-(((r_oval - 0.78) * 24.0) ** 2))

    rune_line = np.clip(outer_rune + inner_rune * 0.85 + rim_rune * 0.70 + rib_wave * 0.45, 0.0, 1.0)

    # Trộn vào màu cơ bản (Phase 1) — nổi bật rõ lớp màng mang cổ tím dạ quang (#6B4BC4 -> #BBA3FF) & gân mảnh phát sáng!
    hm_p1 = hm_col * (1.0 - dark_iris[:, :, None] * 0.72) + np.array([22, 14, 46], dtype=np.float32) * (dark_iris[:, :, None] * 0.72)
    hm_p1 = hm_p1 * (1.0 - rune_line[:, :, None] * 0.85) + c_h_rib * (rune_line[:, :, None] * 0.85)
    hm_p1 = hm_p1 * (1.0 - slit_glow[:, :, None] * 0.92) + np.array([255, 84, 156], dtype=np.float32) * (slit_glow[:, :, None] * 0.92)

    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(hm_p1, 0, 255).astype(np.uint8)
    # Glowmask Phase 1: gân màng mang cổ, hoa văn rune & nền dạ quang nhẹ phát sáng thanh thoát fullbright
    p1_g_alpha = np.clip(rune_line * 215.0 + slit_glow * 255.0 + t1 * 68.0, 0, 255)
    p1_g_rgb = c_h_bright * 0.45 + c_h_rib * 0.55
    p1_g_rgb = p1_g_rgb * (1.0 - slit_glow[:, :, None]) + np.array([255, 72, 138], dtype=np.float32) * slit_glow[:, :, None]
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(p1_g_rgb, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = p1_g_alpha.astype(np.uint8)

    # Phase 2 Enraged: mang cổ bừng sáng rực rỡ hoa văn tím-trắng
    hm_p2 = hm_p1 * 1.18 + c_p2_mid * (rune_line * 0.45 + t1 * 0.18)[:, :, None] + c_p2_core * (slit_glow * 0.75 + outer_rune * 0.45)[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(hm_p2, 0, 255).astype(np.uint8)
    p2_g_alpha = np.clip(rune_line * 255.0 + slit_glow * 255.0 + t1 * 80.0, 0, 255)
    p2_g_rgb = c_p2_mid * 0.55 + c_p2_core * (0.45 + 0.55 * np.clip(outer_rune + slit_glow, 0, 1))[:, :, None]
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(p2_g_rgb, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = p2_g_alpha.astype(np.uint8)

    # -------------------------------------------------------------------------
    # 5. EYE_SLIT (192x192) & EYE_SLIT_HQ (256x192): MẮT RẮN FULLBRIGHT CÓ CHIỀU SÂU + VERTICAL SLIT!
    #    Viền ngoài đỏ rực #C80018 -> tròng sáng đỏ-cam #FF3322 -> lõi sáng #FFE699 -> con ngươi khe dọc sắc hẹp!
    #    100% tròng mắt đưa vào glowmask (alpha=255) để LightTexture.FULL_BRIGHT rực sáng rõ cả giữa trời nắng!
    # -------------------------------------------------------------------------
    for eye_key in ("EYE_SLIT", "EYE_SLIT_HQ"):
        x0, y0, w, h = MAT[eye_key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        ex = (xx / float(w)) * 2.0 - 1.0  # [-1..+1] dọc chiều dài mắt
        ey = (yy / float(h)) * 2.0 - 1.0  # [-1..+1] chiều cao mắt
        er = np.sqrt((ex * 0.92) ** 2 + (ey * 1.08) ** 2)

        # Sợi mống mắt tia tỏa nhẹ tăng chiều sâu sinh học
        ang = np.arctan2(ey, ex)
        iris_fibers = 0.88 + 0.12 * np.sin(ang * 22.0 + er * 6.0)

        # Con ngươi khe dọc rắn hổ mang (vertical slit) sắc hẹp ở chính giữa mắt
        slit_half_w = 0.115 * np.clip(1.0 - (np.abs(ey) ** 1.35) * 0.92, 0.0, 1.0)
        pupil_mask = np.clip((slit_half_w - np.abs(ex)) * 32.0, 0.0, 1.0)
        pupil_halo = np.exp(-(((np.abs(ex) - slit_half_w - 0.04) * 14.0) ** 2)) * np.clip(1.0 - np.abs(ey) * 0.85, 0.0, 1.0)

        # Dải màu chuẩn yêu cầu: #C80018 (viền ngoài) -> #FF3322 (tròng đỏ-cam) -> #FFE699 (lõi sáng vàng-trắng)
        c_e_outer = hex_arr("#C80018")
        c_e_mid = hex_arr("#FF3322")
        c_e_core = hex_arr("#FFE699")
        c_e_slit = hex_arr("#0B0104")

        t_mid = np.clip(1.0 - er * 0.92, 0.0, 1.0)
        t_core = np.clip(1.0 - np.sqrt((ex * 1.45) ** 2 + (ey * 1.35) ** 2), 0.0, 1.0) ** 1.55
        eye_p1 = c_e_outer * (1.0 - t_mid[:, :, None]) + c_e_mid * t_mid[:, :, None]
        eye_p1 = eye_p1 * iris_fibers[:, :, None]
        eye_p1 = eye_p1 * (1.0 - np.clip(t_core * 0.88 + pupil_halo * 0.55, 0.0, 1.0)[:, :, None]) \
                 + c_e_core * np.clip(t_core * 0.88 + pupil_halo * 0.55, 0.0, 1.0)[:, :, None]
        eye_p1 = eye_p1 * (1.0 - pupil_mask[:, :, None]) + c_e_slit * pupil_mask[:, :, None]

        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(eye_p1, 0, 255).astype(np.uint8)
        # Glowmask Phase 1: 100% tròng mắt alpha=255 rực fullbright, chỉ chừa con ngươi dọc tối sắc nét!
        glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(eye_p1 * 1.12, 0, 255).astype(np.uint8)
        glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((1.0 - pupil_mask * 0.96) * 255.0, 0, 255).astype(np.uint8)

        # Phase 2 Enraged: lõi mắt rực sáng trắng-đỏ/tím chói lọi kèm con ngươi dọc đen sắc lạnh!
        c_e2_outer = hex_arr("#E0083C")
        c_e2_mid = hex_arr("#FF44AA")
        c_e2_core = hex_arr("#FFF7FF")
        eye_p2 = c_e2_outer * (1.0 - t_mid[:, :, None]) + c_e2_mid * t_mid[:, :, None]
        eye_p2 = eye_p2 * (1.0 - np.clip(t_core * 1.15 + pupil_halo * 0.70, 0.0, 1.0)[:, :, None]) \
                 + c_e2_core * np.clip(t_core * 1.15 + pupil_halo * 0.70, 0.0, 1.0)[:, :, None]
        eye_p2 = eye_p2 * (1.0 - pupil_mask[:, :, None]) + c_e_slit * pupil_mask[:, :, None]

        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(eye_p2, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(eye_p2 * 1.10, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((1.0 - pupil_mask * 0.94) * 255.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 6. FANG_BONE (192x192): Ngà trắng #F4EFE6 vuốt nhọn + ánh độc tím/đỏ mảnh ở chân nanh!
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["FANG_BONE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    fy = yy / float(h)  # 0 gốc nanh -> 1 mũi nanh nhọn
    fx = 1.0 - np.abs((xx / float(w)) * 2.0 - 1.0)
    ivory = hex_arr("#D4CEC4") * (1.0 - fx[:, :, None] * 0.55) + hex_arr("#F4EFE6") * (fx[:, :, None] * 0.55)
    # Ánh độc tím/đỏ mảnh quanh chân nanh (fy < 0.28) + mũi nanh ngà bóng sắc lạnh
    root_venom = np.clip((0.28 - fy) / 0.28, 0.0, 1.0)
    tip_sheen = np.clip((fy - 0.72) / 0.28, 0.0, 1.0)
    f_col = ivory * (1.0 - root_venom[:, :, None] * 0.48) + hex_arr("#9B2252") * (root_venom[:, :, None] * 0.48)
    f_col = f_col + np.array([12, 12, 10], dtype=np.float32) * (tip_sheen * fx)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(f_col, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = hex_arr("#C4366E")
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(root_venom * 125.0, 0, 255).astype(np.uint8)

    fe_col = ivory * (1.0 - root_venom[:, :, None] * 0.58) + hex_arr("#A44CE6") * (root_venom[:, :, None] * 0.58)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(fe_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = hex_arr("#D685FF")
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((root_venom * 195.0 + tip_sheen * 95.0), 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 7. MOUTH_RED (192x192): Vòm họng đỏ sẫm có gân cơ & ánh tối sâu
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["MOUTH_RED"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ribs = 0.5 + 0.5 * np.sin(yy * 0.22)
    m_col = np.array([98, 12, 20], dtype=np.float32) + np.array([78, 16, 22], dtype=np.float32) * ribs[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(m_col, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.array([185, 26, 40], dtype=np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(70.0 + ribs * 65.0, 0, 255).astype(np.uint8)

    me_col = np.array([145, 22, 46], dtype=np.float32) + np.array([92, 28, 52], dtype=np.float32) * ribs[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(me_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.array([255, 68, 120], dtype=np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(145.0 + ribs * 80.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 8. HORN_DARK (192x192): Sừng obsidian khía vòng cổ đại sắc cạnh
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["HORN_DARK"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ring = (yy % 18.0) / 18.0
    ridge = np.exp(-(((ring - 0.25) * 6.0) ** 2))
    ho_col = c_crevice + (c_s2 - c_crevice) * 0.85 + (c_s4 - c_s2) * ridge[:, :, None] * 0.85
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(ho_col, 0, 255).astype(np.uint8)
    hoe_col = ho_col + c_p2_outer * (ridge * 0.55)[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(hoe_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_p2_mid, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(ridge * 115.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 9. HOOD_RIB (256x192): Nan xương sườn mang cổ thanh mảnh chuyển tím -> sáng (#DDD2FF)
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["HOOD_RIB"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ry_c = 1.0 - np.abs((yy / float(h)) * 2.0 - 1.0)
    rib_col = c_h_deep * (1.0 - ry_c[:, :, None]) + c_h_bright * (ry_c ** 0.7)[:, :, None] + (c_h_rib - c_h_bright) * (ry_c ** 2.2)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(rib_col, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_h_rib, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((ry_c ** 1.4) * 165.0, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(rib_col * 1.15 + c_p2_core * (ry_c ** 1.8)[:, :, None] * 0.45, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_p2_core * 0.55 + c_p2_mid * 0.45, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((ry_c ** 0.9) * 235.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 10. DORSAL_VEIN (256x192): Mạch sáng mảnh dọc sống lưng (mờ P1, rực tím-trắng P2)
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["DORSAL_VEIN"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    vc = np.exp(-((((xx / float(w)) * 2.0 - 1.0) * 3.2) ** 2))
    pulse = 0.75 + 0.25 * np.sin(yy * 0.14)
    v_p1 = c_s1 * (1.0 - vc[:, :, None]) + np.array([106, 86, 178], dtype=np.float32) * (vc * pulse)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(v_p1, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.array([138, 112, 224], dtype=np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(vc * pulse * 105.0, 0, 255).astype(np.uint8)

    v_p2 = c_p2_outer * (1.0 - vc[:, :, None]) + (c_p2_mid * 0.4 + c_p2_core * 0.6) * vc[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(v_p2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_p2_mid * 0.35 + c_p2_core * 0.65, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(vc * 255.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 11. TAIL_CORE (256x192): Giáp sừng đuôi cổ đại & khe phát sáng tím
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["TAIL_CORE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    tx = np.abs((xx / float(w)) * 2.0 - 1.0)
    chev = ((yy + tx * 44.0) % 36.0) / 36.0
    seam = np.exp(-(((chev - 0.50) * 14.0) ** 2))
    tc_p1 = c_s1 * 0.7 + c_s3 * (0.35 + 0.55 * (1.0 - tx))[:, :, None]
    tc_p1 = tc_p1 * (1.0 - seam[:, :, None] * 0.65) + c_h_deep * (seam[:, :, None] * 0.65)
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(tc_p1, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_h_bright, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(seam * 95.0, 0, 255).astype(np.uint8)

    tc_p2 = tc_p1 * 0.85 + (c_p2_mid * 0.55 + c_p2_core * 0.45) * seam[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(tc_p2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_p2_mid * 0.45 + c_p2_core * 0.55, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(seam * 245.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 12. LIP_ARMOR (256x192): Tấm vảy môi chia đốt sắc nét + viền sáng cạnh #555E88
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["LIP_ARMOR"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cell = (xx % 32.0) / 32.0
    lip_groove = ((cell < 0.10) | (cell > 0.90)).astype(np.float32)
    lip_hi = np.exp(-((((yy / float(h)) - 0.18) * 5.2) ** 2)) * (1.0 - lip_groove)
    la_col = c_s2 * (1.0 - lip_groove[:, :, None] * 0.72) + c_crevice * (lip_groove[:, :, None] * 0.72)
    la_col = la_col * (1.0 - lip_hi[:, :, None] * 0.76) + c_rim_steel * (lip_hi[:, :, None] * 0.76)
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(la_col, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(la_col + c_rim_violet * (lip_hi * 0.42)[:, :, None], 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 13. EYE_SOCKET (128x128): Nền hốc mắt đen sâu #050508 tạo tương phản tối đa với tròng mắt đỏ-vàng!
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["EYE_SOCKET"]
    base[y0:y0 + h, x0:x0 + w, :3] = hex_arr("#050508").astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = hex_arr("#050508").astype(np.uint8)

    # -------------------------------------------------------------------------
    # 14. HEAD_RIM_PLATE (256x192): Giáp sống mũi, gờ mày & góc hàm có rim highlight #555E88 & #7A66B8 cực nét!
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["HEAD_RIM_PLATE"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / float(w), yy / float(h)
    ex_rim = np.minimum(nx, 1.0 - nx) * 2.0
    ey_rim = np.minimum(ny, 1.0 - ny) * 2.0
    border_hi = np.exp(-((ex_rim * 8.5) ** 2)) * 0.78 + np.exp(-(((ny - 0.10) * 11.0) ** 2)) * 0.95
    center_keel = np.exp(-(((nx - 0.5) * 9.0) ** 2)) * 0.45
    ao_bot = np.clip(1.0 - np.exp(-((1.0 - ny) * 10.0)), 0.25, 1.0)
    hp_col = (c_s1 * (1.0 - (1.0 - ny)[:, :, None] * 0.55) + c_s3 * ((1.0 - ny) * 0.55)[:, :, None]) * ao_bot[:, :, None]
    hp_col = hp_col * (1.0 - np.clip(border_hi * 0.80, 0, 1)[:, :, None]) + c_rim_steel * np.clip(border_hi * 0.80, 0, 1)[:, :, None]
    hp_col = hp_col + c_rim_violet * np.clip(center_keel * 0.65 + (border_hi > 0.75) * 0.25, 0, 1)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(hp_col, 0, 255).astype(np.uint8)
    hp_cr = draw_cracks_mask(w, h, seed=131, count=2)
    hpe = hp_col * (1.0 - hp_cr[:, :, None] * 0.88) + (c_p2_mid * 0.5 + c_p2_core * 0.5) * hp_cr[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(hpe, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_p2_mid * 0.45 + c_p2_core * 0.55, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(hp_cr * 225.0, 0, 255).astype(np.uint8)

    # -------------------------------------------------------------------------
    # 15. HORN_SCULPT (256x192): Sừng chính & sừng má — gốc ánh tím sẫm + vân gờ đốt + viền sáng cạnh #555E88/#7A66B8!
    # -------------------------------------------------------------------------
    x0, y0, w, h = MAT["HORN_SCULPT"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / float(w), yy / float(h)  # ny=0 gốc sừng (ánh tím sẫm) -> ny=1 chóp sừng sắc lạnh
    cyl = 1.0 - np.abs(nx * 2.0 - 1.0)
    ring = (yy % 20.0) / 20.0
    ridge = np.exp(-(((ring - 0.28) * 6.5) ** 2))
    root_violet = np.clip((0.36 - ny) / 0.36, 0.0, 1.0)
    hs_col = c_crevice * 0.6 + c_s2 * (0.4 + 0.6 * cyl)[:, :, None]
    hs_col = hs_col * (1.0 - root_violet[:, :, None] * 0.52) + hex_arr("#4B2D86") * (root_violet[:, :, None] * 0.52)
    hs_col = hs_col + c_rim_steel * (ridge * 0.65 * (0.45 + 0.55 * cyl))[:, :, None]
    hs_col = hs_col + c_rim_violet * (np.exp(-((cyl - 0.85) * 6.0) ** 2) * 0.42)[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(hs_col, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = c_rim_violet
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(root_violet * 75.0, 0, 255).astype(np.uint8)

    hse_col = hs_col + c_p2_outer * (ridge * 0.45 + root_violet * 0.40)[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(hse_col, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_p2_mid, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip((ridge * 120.0 + root_violet * 160.0), 0, 255).astype(np.uint8)

    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(base, "RGBA").save(tex_dir / "jormungandr.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "jormungandr_glowmask.png")
    Image.fromarray(enraged, "RGBA").save(tex_dir / "jormungandr_enraged.png")
    Image.fromarray(enraged_glow, "RGBA").save(tex_dir / "jormungandr_enraged_glowmask.png")

    # Giữ icon vật phẩm nanh độc sắc nét
    item_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(item_img)
    fang_pts = [(11, 2), (13, 4), (11, 8), (8, 12), (4, 14), (3, 12), (6, 9), (8, 5)]
    idraw.polygon(fang_pts, fill=(244, 240, 232, 255), outline=(112, 106, 134, 255))
    idraw.rectangle([10, 2, 13, 4], fill=(122, 96, 204, 255))
    idraw.rectangle([2, 13, 4, 15], fill=(124, 242, 92, 255))
    idraw.point((3, 14), fill=(224, 255, 206, 255))
    item_out = ASSETS / "textures" / "item" / "jormungandr_venom_fang.png"
    item_out.parent.mkdir(parents=True, exist_ok=True)
    item_img.save(item_out)
    print(f"TEX_OK: 4 entity textures ({TEX_W}x{TEX_H}, nearest) + item icon")


# =============================================================================
# 3. 17 ANIMATION (GIỮ NGUYÊN 100% TÊN CŨ — SỬA DỨT ĐIỂM LƯỠI BAY & GẬP/XÒE MANG CỔ)
# =============================================================================
def build_animations() -> dict:
    # Quy ước Bedrock đã kiểm chứng:
    #   - jaw.rotX > 0  -> mở hàm dưới xuống (-Y)
    #   - tongue.posZ < 0 -> đẩy lưỡi ra trước (-Z); lưỡi gốc nằm sâu ở z=-2..-23, mõm kết thúc ở z=-31
    #     -> chỉ dịch Z tối đa [-7..-9] để đầu lưỡi thè nhẹ ra giữa 2 hàm, gốc luôn neo sâu trong miệng!
    #   - hood_l/r.rotX > 0 (+58°..+66°) -> gập cánh mang cổ từ dáng dọc (rear-up) nằm xuôi ra sau (+Z) khi trườn!
    #   - hood_l.rotY < 0 (hood_r.rotY > 0) -> xòe cúp mang về trước đe dọa; ngược lại -> khép sát cổ!
    anims = {
        "animation.jormungandr.idle_coil": {
            "loop": True,
            "animation_length": 3.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [4, 0, 0], "1.5": [11, 0, 0], "3.0": [4, 0, 0]}},
                "tongue": {
                    "position": {"0.0": [0, 0, 2], "1.0": [0, 0, -7], "1.45": [0, 0, -8], "1.95": [0, 0, 2], "3.0": [0, 0, 2]},
                    "rotation": {"0.0": [0, 0, 0], "1.15": [6, 10, 0], "1.45": [-6, -10, 0], "3.0": [0, 0, 0]},
                },
                "hood_l": {"rotation": {"0.0": [0, -2, 2], "1.5": [-2, -8, 5], "3.0": [0, -2, 2]}},
                "hood_r": {"rotation": {"0.0": [0, 2, -2], "1.5": [-2, 8, -5], "3.0": [0, 2, -2]}},
            },
        },
        "animation.jormungandr.rear_up": {
            "loop": True,
            "animation_length": 2.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "1.2": [20, 0, 0], "2.4": [8, 0, 0]}},
                "tongue": {
                    "position": {"0.0": [0, 0, 1], "0.85": [0, 0, -8], "1.35": [0, 0, -7], "1.85": [0, 0, 1], "2.4": [0, 0, 1]},
                    "rotation": {"0.0": [0, 0, 0], "1.0": [7, 12, 0], "1.3": [-7, -12, 0], "2.4": [0, 0, 0]},
                },
                "hood_l": {"rotation": {"0.0": [0, -6, 4], "1.2": [-2, -14, 8], "2.4": [0, -6, 4]}},
                "hood_r": {"rotation": {"0.0": [0, 6, -4], "1.2": [-2, 14, -8], "2.4": [0, 6, -4]}},
            },
        },
        "animation.jormungandr.slither": {
            "loop": True,
            "animation_length": 1.6,
            "bones": {
                "jaw": {"rotation": {"0.0": [4, 0, 0], "0.8": [8, 0, 0], "1.6": [4, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 2], "0.7": [0, 0, -5], "1.1": [0, 0, 2], "1.6": [0, 0, 2]}},
                # Gập cánh mang cổ xuôi gọn về sau (+Z) ôm sát hai bên cổ khi trườn nhanh (cộng rest [16,-14,0] -> [64,30,-16])
                "hood_l": {"rotation": {"0.0": [48, 44, -16], "0.8": [52, 40, -14], "1.6": [48, 44, -16]}},
                "hood_r": {"rotation": {"0.0": [48, -44, 16], "0.8": [52, -40, 14], "1.6": [48, -44, 16]}},
            },
        },
        "animation.jormungandr.strike": {
            "animation_length": 1.8,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "0.8": [48, 0, 0], "1.15": [56, 0, 0], "1.35": [4, 0, 0], "1.8": [8, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 2], "0.8": [0, 0, -4], "1.35": [0, 0, 3], "1.8": [0, 0, 2]}},
                "hood_l": {"rotation": {"0.0": [4, -4, 2], "0.8": [-4, -18, 10], "1.3": [18, 4, 2], "1.8": [4, -4, 2]}},
                "hood_r": {"rotation": {"0.0": [4, 4, -2], "0.8": [-4, 18, -10], "1.3": [18, -4, -2], "1.8": [4, 4, -2]}},
            },
        },
        "animation.jormungandr.spit": {
            "animation_length": 1.8,
            "bones": {
                "jaw": {"rotation": {"0.0": [6, 0, 0], "0.75": [50, 0, 0], "1.25": [40, 0, 0], "1.8": [6, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 2], "1.8": [0, 0, 2]}},
                "hood_l": {"rotation": {"0.0": [2, -6, 4], "0.8": [-2, -16, 10], "1.8": [2, -6, 4]}},
                "hood_r": {"rotation": {"0.0": [2, 6, -4], "0.8": [-2, 16, -10], "1.8": [2, 6, -4]}},
            },
        },
        "animation.jormungandr.tail_slam": {
            "animation_length": 2.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "1.0": [34, 0, 0], "2.0": [8, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [2, -6, 4], "1.0": [-2, -14, 8], "2.0": [2, -6, 4]}},
                "hood_r": {"rotation": {"0.0": [2, 6, -4], "1.0": [-2, 14, -8], "2.0": [2, 6, -4]}},
            },
        },
        "animation.jormungandr.constrict": {
            "animation_length": 3.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "1.5": [34, 0, 0], "3.0": [8, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 1], "1.5": [0, 0, -6], "3.0": [0, 0, 1]}},
                "hood_l": {"rotation": {"0.0": [12, -8, 6], "1.5": [8, -16, 10], "3.0": [12, -8, 6]}},
                "hood_r": {"rotation": {"0.0": [12, 8, -6], "1.5": [8, 16, -10], "3.0": [12, 8, -6]}},
            },
        },
        "animation.jormungandr.flare": {
            "animation_length": 1.6,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "0.75": [52, 0, 0], "1.6": [10, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 1], "0.75": [0, 0, -8], "1.6": [0, 0, 1]}},
                "hood_l": {"rotation": {"0.0": [4, 10, -4], "0.65": [-6, -22, 14], "1.2": [-6, -18, 12], "1.6": [2, -6, 4]}},
                "hood_r": {"rotation": {"0.0": [4, -10, 4], "0.65": [-6, 22, -14], "1.2": [-6, 18, -12], "1.6": [2, 6, -4]}},
            },
        },
        "animation.jormungandr.burrow": {
            "animation_length": 1.4,
            "bones": {
                "hood_l": {"rotation": {"0.0": [6, 0, 0], "0.7": [54, 50, -20], "1.4": [54, 50, -20]}},
                "hood_r": {"rotation": {"0.0": [6, 0, 0], "0.7": [54, -50, 20], "1.4": [54, -50, 20]}},
                "jaw": {"rotation": {"0.0": [8, 0, 0], "1.4": [0, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 2], "1.4": [0, 0, 3]}},
            },
        },
        "animation.jormungandr.emerge": {
            "animation_length": 1.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [4, 0, 0], "0.5": [56, 0, 0], "1.4": [12, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [50, 46, -18], "0.5": [-4, -20, 12], "1.4": [2, -8, 4]}},
                "hood_r": {"rotation": {"0.0": [50, -46, 18], "0.5": [-4, 20, -12], "1.4": [2, 8, -4]}},
            },
        },
        "animation.jormungandr.rapid_strike": {
            "animation_length": 2.8,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "0.6": [52, 0, 0], "0.9": [6, 0, 0],
                                     "1.5": [54, 0, 0], "1.8": [6, 0, 0], "2.4": [58, 0, 0], "2.8": [10, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [4, -10, 6], "1.4": [-2, -20, 12], "2.8": [4, -10, 6]}},
                "hood_r": {"rotation": {"0.0": [4, 10, -6], "1.4": [-2, 20, -12], "2.8": [4, 10, -6]}},
            },
        },
        "animation.jormungandr.nova": {
            "animation_length": 2.0,
            "bones": {
                "jaw": {"rotation": {"0.0": [8, 0, 0], "0.9": [58, 0, 0], "1.5": [50, 0, 0], "2.0": [8, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [2, -6, 4], "0.9": [-6, -24, 14], "2.0": [2, -6, 4]}},
                "hood_r": {"rotation": {"0.0": [2, 6, -4], "0.9": [-6, 24, -14], "2.0": [2, 6, -4]}},
            },
        },
        "animation.jormungandr.rain": {
            "animation_length": 2.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "0.8": [54, 0, 0], "1.8": [46, 0, 0], "2.4": [10, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [2, -8, 4], "1.2": [-4, -20, 12], "2.4": [2, -8, 4]}},
                "hood_r": {"rotation": {"0.0": [2, 8, -4], "1.2": [-4, 20, -12], "2.4": [2, 8, -4]}},
            },
        },
        "animation.jormungandr.ouroboros": {
            "animation_length": 6.5,
            "bones": {
                "jaw": {"rotation": {"0.0": [12, 0, 0], "2.5": [42, 0, 0], "5.5": [58, 0, 0], "6.5": [10, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [14, -12, 8], "5.5": [-2, -24, 14], "6.5": [4, -10, 6]}},
                "hood_r": {"rotation": {"0.0": [14, 12, -8], "5.5": [-2, 24, -14], "6.5": [4, 10, -6]}},
            },
        },
        "animation.jormungandr.phase_change": {
            "animation_length": 5.5,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "1.2": [60, 0, 0], "4.2": [56, 0, 0], "5.5": [14, 0, 0]}},
                "tongue": {"position": {"0.0": [0, 0, 1], "1.5": [0, 0, -8], "4.2": [0, 0, -8], "5.5": [0, 0, 1]}},
                "hood_l": {"rotation": {"0.0": [4, 8, -4], "1.2": [-6, -24, 14], "4.5": [-6, -24, 14], "5.5": [2, -10, 6]}},
                "hood_r": {"rotation": {"0.0": [4, -8, 4], "1.2": [-6, 24, -14], "4.5": [-6, 24, -14], "5.5": [2, 10, -6]}},
            },
        },
        "animation.jormungandr.grow": {
            "animation_length": 2.4,
            "bones": {
                "jaw": {"rotation": {"0.0": [18, 0, 0], "1.2": [52, 0, 0], "2.4": [14, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [0, -14, 8], "1.2": [-6, -24, 14], "2.4": [0, -12, 8]}},
                "hood_r": {"rotation": {"0.0": [0, 14, -8], "1.2": [-6, 24, -14], "2.4": [0, 12, -8]}},
            },
        },
        "animation.jormungandr.death": {
            "loop": "hold_on_last_frame",
            "animation_length": 2.6,
            "bones": {
                "jaw": {"rotation": {"0.0": [10, 0, 0], "1.2": [46, 0, 0], "2.6": [30, 0, 0]}},
                "hood_l": {"rotation": {"0.0": [4, -6, 4], "2.6": [46, 44, -18]}},
                "hood_r": {"rotation": {"0.0": [4, 6, -4], "2.6": [46, -44, 18]}},
                "tongue": {"position": {"0.0": [0, 0, 1], "2.6": [0, 0, -8]}},
            },
        },
    }

    max_rot = 0.0
    for name, anim in anims.items():
        for bname, ch in anim.get("bones", {}).items():
            for t, vec in ch.get("rotation", {}).items():
                for v in vec:
                    assert abs(v) <= 95.0, f"Abnormal rot {v} in {name}.{bname}@{t}"
                    max_rot = max(max_rot, abs(v))

    out = ASSETS / "animations" / "entity" / "jormungandr.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=2)
    print(f"ANIM_OK: {out} ({len(anims)} animations, max |rot|={max_rot:.1f} deg)")
    return anims


# =============================================================================
# 4. BỘ RENDER PREVIEW 3D ĐA GÓC MÔ PHỎNG ĐÚNG SERPENTPATH (CUBIC BEZIER + ARC-LENGTH COIL + SCALEZ)
# =============================================================================
def bedrock_rot_matrix(rot_deg):
    rx, ry, rz = -math.radians(rot_deg[0]), -math.radians(rot_deg[1]), math.radians(rot_deg[2])
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]], dtype=np.float64)
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], dtype=np.float64)
    return Rz @ Ry @ Rx


def transform_affine(R, t):
    M = np.eye(4, dtype=np.float64)
    M[:3, :3] = R
    M[:3, 3] = t
    return M


def rot_about_pivot(pivot, rot_deg):
    p = np.array(pivot, dtype=np.float64)
    R = bedrock_rot_matrix(rot_deg)
    return transform_affine(R, p - R @ p)


def compute_taper_factor(frac: float) -> float:
    f = max(0.0, min(1.0, frac))
    neck_full = 0.06 * math.exp(-((f - 0.08) / 0.075) ** 2)
    main_taper = 1.0 - 0.78 * (f ** 1.42)
    return max(0.22, min(1.06, main_taper + neck_full))


def simulate_serpent_path_poses(active_segs: int, visual_scale: float = 1.30, overlap: float = 1.34):
    """Mô phỏng chính xác 1:1 công thức SerpentPath.java (đơn vị model px: 1 block = 16 / visual_scale px)."""
    spacing = 0.96 * visual_scale
    rear_h = 5.20 * visual_scale
    coil_rad = 4.80 * visual_scale
    body_diam = 2.28 * visual_scale
    neck_n = 9
    total_nodes = active_segs + 1

    # Hướng entity nhìn về -Z trong không gian model preview (fwd = [0, 0, -1], right = [-1, 0, 0])
    fwd = np.array([0.0, 0.0, -1.0])
    right = np.array([-1.0, 0.0, 0.0])
    up = np.array([0.0, 1.0, 0.0])

    # 1. Xoắn ốc idle coil tích phân độ dài cung
    foot_back = max(spacing * 2.2, rear_h * 0.55)
    foot_pos = -fwd * foot_back
    r0 = max(body_diam * 1.35, coil_rad * 0.72)
    b = (body_diam * 1.08) / (2.0 * math.pi)
    center = foot_pos - right * r0

    nodes = [np.zeros(3) for _ in range(total_nodes + 1)]
    for i in range(neck_n + 1):
        rad_i = (body_diam * 0.5) * compute_taper_factor(i / max(1, active_segs))
        nodes[i] = -fwd * (foot_back * i / neck_n) + up * (rad_i * 0.85)

    u = 0.0
    ds = spacing / 4.0
    for i in range(neck_n + 1, total_nodes + 1):
        for _ in range(4):
            r1 = r0 + b * u
            du1 = ds / max(0.5, math.hypot(r1, b))
            rm = r0 + b * (u + 0.5 * du1)
            u += ds / max(0.5, math.hypot(rm, b))
        theta = math.pi * 0.5 + u
        r = r0 + b * u
        rad_i = (body_diam * 0.5) * compute_taper_factor(i / max(1, active_segs))
        nodes[i] = center + right * (math.sin(theta) * r) + fwd * (math.cos(theta) * r) + up * (rad_i * 0.85)

    # 2. Cubic Bezier chữ S cho cổ ngẩng (i = 0..neck_n)
    b0 = nodes[neck_n]
    head_rad = (body_diam * 0.5) * compute_taper_factor(0.0)
    b3 = fwd * (0.24 * rear_h) + up * (rear_h + head_rad * 0.85)
    span_vec = np.array([b3[0] - b0[0], 0.0, b3[2] - b0[2]])
    span_h = float(np.linalg.norm(span_vec))
    nfwd = span_vec / span_h if span_h > 0.35 else fwd
    eff_span = max(span_h, 0.72 * rear_h)
    rise_y = max(1.5, b3[1] - b0[1])
    b1 = b0 + nfwd * (eff_span * 0.48) + up * (rise_y * 0.06)
    b2 = b3 - nfwd * (eff_span * 0.42) + up * (rise_y * 0.96 - (b3[1] - b0[1]))

    lut_pts, lut_arc = [b0], [0.0]
    for k in range(1, 33):
        t = k / 32.0
        om = 1.0 - t
        pt = (om ** 3) * b0 + 3 * (om ** 2) * t * b1 + 3 * om * (t ** 2) * b2 + (t ** 3) * b3
        lut_pts.append(pt)
        lut_arc.append(lut_arc[-1] + float(np.linalg.norm(pt - lut_pts[-2])))
    tot_arc = lut_arc[-1]
    for i in range(neck_n):
        targ = ((neck_n - i) / neck_n) * tot_arc
        for k in range(1, 33):
            if lut_arc[k] >= targ:
                f = (targ - lut_arc[k - 1]) / max(1e-6, lut_arc[k] - lut_arc[k - 1])
                nodes[i] = lut_pts[k - 1] * (1.0 - f) + lut_pts[k] * f
                break

    # 3. Sinh pose từng bone phẳng trong không gian model px (16 px = visual_scale blocks)
    px_per_blk = 16.0 / visual_scale
    world_base_len = (SEG_LEN_PX / 16.0) * visual_scale
    poses = {}

    def pose_matrix(world_pos, seg_vec, sx, sy, sz, dampen_pitch=1.0):
        # seg_vec trỏ về phía trước (-Z trong local bone)
        h = math.hypot(seg_vec[0], seg_vec[2])
        # Trong preview: forward chuẩn là (0, 0, -1), +X là trái
        yaw_rad = math.atan2(-seg_vec[0], -seg_vec[2])  # xoay quanh +Y đưa (0,0,-1) -> (seg_vec.x, 0, seg_vec.z)
        pitch_rad = math.atan2(seg_vec[1], max(1e-4, h)) * dampen_pitch
        cy, sy_ = math.cos(yaw_rad), math.sin(yaw_rad)
        cp, sp = math.cos(pitch_rad), math.sin(pitch_rad)
        Ry = np.array([[cy, 0, sy_], [0, 1, 0], [-sy_, 0, cy]], dtype=np.float64)
        Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64)
        S = np.diag([sx, sy, sz])
        M = np.eye(4, dtype=np.float64)
        M[:3, :3] = Ry @ Rx @ S
        M[:3, 3] = world_pos * px_per_blk
        return M

    # Áp chính xác `HEAD_SCALE_MULT = 1.35` cho toàn bộ cụm đầu y như JormungandrGeoModel.java!
    poses["head"] = pose_matrix(nodes[0], nodes[0] - nodes[1], HEAD_SCALE_MULT, HEAD_SCALE_MULT, HEAD_SCALE_MULT, dampen_pitch=0.22)
    for i in range(1, active_segs + 1):
        vec = nodes[i - 1] - nodes[i]
        dist = float(np.linalg.norm(vec))
        mid = (nodes[i - 1] + nodes[i]) * 0.5
        diam = compute_taper_factor((i - 0.5) / active_segs)
        lz = max(0.55, min(3.40, (dist / world_base_len) * overlap))
        poses[f"segment_{i}"] = pose_matrix(mid, vec, diam, diam, lz)

    tvec = nodes[active_segs] - nodes[total_nodes]
    tdist = float(np.linalg.norm(tvec))
    tdir = tvec / max(1e-5, tdist)
    tanchor = nodes[active_segs] + tdir * (tdist * 0.16)
    tdiam = compute_taper_factor(1.0) * 1.06
    tlz = max(0.75, min(2.25, (tdist / world_base_len) * overlap))
    poses["tail_tip"] = pose_matrix(tanchor, tvec, tdiam, tdiam, tlz)
    return poses


def collect_posed_faces(geo: dict, flat_bone_mats: dict, anim_overrides: dict = None, active_segs: int = 34):
    bones_list = geo["minecraft:geometry"][0]["bones"]
    by_name = {b["name"]: b for b in bones_list}
    world_M = {}
    anim_overrides = anim_overrides or {}

    def get_bone_M(name):
        if name in world_M:
            return world_M[name]
        b = by_name[name]
        parent = b.get("parent")
        if parent is None:
            M = flat_bone_mats.get(name, np.eye(4, dtype=np.float64))
        else:
            piv = b.get("pivot", [0, 0, 0])
            base_rot = b.get("rotation", [0, 0, 0])
            ov = anim_overrides.get(name, {})
            add_rot = ov.get("rotation", [0, 0, 0])
            rot = [base_rot[k] + add_rot[k] for k in range(3)]
            pos = ov.get("position", [0, 0, 0])
            T_pos = transform_affine(np.eye(3), np.array([pos[0], pos[1], pos[2]], dtype=np.float64))
            local = T_pos @ rot_about_pivot(piv, rot)
            M = get_bone_M(parent) @ local
        world_M[name] = M
        return M

    faces = []
    all_pts = []
    for b in bones_list:
        name = b["name"]
        if name.startswith("segment_"):
            idx = int(name.split("_")[1])
            if idx > active_segs:
                continue
        if name == "tail_tip" and name not in flat_bone_mats:
            continue
        if not b.get("cubes"):
            continue
        bM = get_bone_M(name)
        for c in b["cubes"]:
            cM = bM @ (rot_about_pivot(c["pivot"], c["rotation"]) if "rotation" in c else np.eye(4))
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            x0, y0, z0 = ox, oy, oz
            x1, y1, z1 = ox + sx, oy + sy, oz + sz
            corners = np.array([
                [x0, y0, z0, 1], [x1, y0, z0, 1], [x1, y1, z0, 1], [x0, y1, z0, 1],
                [x0, y0, z1, 1], [x1, y0, z1, 1], [x1, y1, z1, 1], [x0, y1, z1, 1],
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


def rasterize_view(faces, diff_img, glow_img, view_mat, width, height, scale, center, bg=(22, 24, 34)):
    fb = np.zeros((height, width, 3), dtype=np.float32)
    fb[:, :] = bg
    zbuf = np.full((height, width), -1e9, dtype=np.float32)

    light_dir = np.array([0.38, 0.78, -0.50], dtype=np.float64)
    light_dir /= np.linalg.norm(light_dir)
    cx, cy = width * 0.5, height * 0.5

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
        mask = inside & (z > sub_z)
        if not np.any(mask):
            return
        sub_z[mask] = z[mask]
        u = np.clip((w0[mask] * uv0[0] + w1[mask] * uv1[0] + w2[mask] * uv2[0]).astype(np.int32), 0, TEX_W - 1)
        v = np.clip((w0[mask] * uv0[1] + w1[mask] * uv1[1] + w2[mask] * uv2[1]).astype(np.int32), 0, TEX_H - 1)
        d_col = diff_img[v, u, :3]
        g_col = glow_img[v, u, :3]
        g_a = glow_img[v, u, 3:4] / 255.0
        lit = d_col * shade * (1.0 - g_a * 0.85) + g_col * (g_a * 1.15)
        fb[min_y:max_y + 1, min_x:max_x + 1][mask] = np.clip(lit, 0, 255)

    for quad, uv in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        cv1, cv2 = cam[1] - cam[0], cam[3] - cam[0]
        if (cv1[0] * cv2[1] - cv1[1] * cv2[0]) <= 0:
            continue
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = 0.42 + 0.58 * max(0.0, float(np.dot(n, light_dir)))
        scr = np.stack([cx + cam[:, 0] * scale, cy - cam[:, 1] * scale, cam[:, 2]], axis=1)
        u0, v0 = uv["uv"]
        uw, uh = uv["uv_size"]
        u1, v1 = u0 + max(1, uw) - 0.01, v0 + max(1, uh) - 0.01
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        raster_tri(scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
        raster_tri(scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)

    return Image.fromarray(fb.astype(np.uint8), "RGB")


def render_previews(geo: dict) -> None:
    tex_dir = ASSETS / "textures" / "entity"
    d_norm = np.array(Image.open(tex_dir / "jormungandr.png").convert("RGBA"), dtype=np.float32)
    g_norm = np.array(Image.open(tex_dir / "jormungandr_glowmask.png").convert("RGBA"), dtype=np.float32)
    d_enr = np.array(Image.open(tex_dir / "jormungandr_enraged.png").convert("RGBA"), dtype=np.float32)
    g_enr = np.array(Image.open(tex_dir / "jormungandr_enraged_glowmask.png").convert("RGBA"), dtype=np.float32)

    def cam_mat(yaw_deg, pitch_deg):
        y, p = math.radians(yaw_deg), math.radians(pitch_deg)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
        Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64)
        return Rx @ Ry

    rear_anim = {
        "jaw": {"rotation": [18, 0, 0]},
        "tongue": {"position": [0, 0, -7], "rotation": [4, 6, 0]},
        "hood_l": {"rotation": [0, -8, 4]},
        "hood_r": {"rotation": [0, 8, -4]},
    }

    poses_p1 = simulate_serpent_path_poses(active_segs=34, visual_scale=1.30, overlap=1.34)
    faces_p1, bmin1, bmax1 = collect_posed_faces(geo, poses_p1, anim_overrides=rear_anim, active_segs=34)

    poses_p2 = simulate_serpent_path_poses(active_segs=48, visual_scale=1.30, overlap=1.34)
    faces_p2, bmin2, bmax2 = collect_posed_faces(geo, poses_p2, anim_overrides=rear_anim, active_segs=48)

    # Cận cảnh cụm đầu + cổ trên (`segment_1..8`) ở tỉ lệ thực 1.35x để kiểm chứng khớp nối, mắt từ dưới lên, sừng & mang cổ!
    neck_head_faces, nh_min, nh_max = collect_posed_faces(geo, poses_p1, anim_overrides=rear_anim, active_segs=8)
    head_anchor = poses_p1["head"][:3, 3]
    head_focus = head_anchor + np.array([0.0, -8.0, -8.0], dtype=np.float64)

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 640, 480

    # --- BỘ 3 ẢNH CẬN CẢNH ĐẦU YÊU CẦU TRONG ujm 2.txt (+ GÓC DƯỚI LÊN PHASE 2) ---
    hsc_close = 3.25
    # 1. Trực diện (Direct Front — kiểm chứng độ rộng gò má, 2 mắt đối xứng, nanh cong, mang cổ!)
    p_h_front = rasterize_view(neck_head_faces, d_norm, g_norm, cam_mat(180, 4), pw, ph, hsc_close, head_focus)
    ImageDraw.Draw(p_h_front).text((12, 10), "HEAD FRONT (1.35x HEAD, FULLBRIGHT EYES, CURVED FANGS, HOOD)", fill=(255, 225, 225))
    p_h_front.save(PREVIEW_DIR / "preview_head_front.png")

    # 2. Nghiêng 3/4 nhìn từ dưới lên (GIỐNG HỆT GÓC CHỤP TRONG GAME — kiểm chứng nhìn rõ mắt & cổ!)
    p_h_low3q = rasterize_view(neck_head_faces, d_norm, g_norm, cam_mat(138, -24), pw, ph, hsc_close * 0.92, head_focus + np.array([0.0, -6.0, 0.0]))
    ImageDraw.Draw(p_h_low3q).text((12, 10), "HEAD 3/4 LOW-ANGLE LOOKING UP (IN-GAME VIEW: EYES & NECK JOINT)", fill=(255, 225, 225))
    p_h_low3q.save(PREVIEW_DIR / "preview_head_low_3q.png")

    # 3. Góc nhìn bên (Side Profile — kiểm chứng sừng cong, sừng má, gờ sống mũi, nanh, gai gáy xuôi!)
    p_h_side = rasterize_view(neck_head_faces, d_norm, g_norm, cam_mat(94, -4), pw, ph, hsc_close * 0.94, head_focus + np.array([0.0, 0.0, 10.0]))
    ImageDraw.Draw(p_h_side).text((12, 10), "HEAD SIDE PROFILE (HORNS, CHEEK HORN, BROW, SWEPT NAPE SPINES)", fill=(255, 225, 225))
    p_h_side.save(PREVIEW_DIR / "preview_head_side.png")

    # 4. Góc nhìn 3/4 từ dưới lên ở Phase 2 Enraged (kiểm chứng mắt trắng-đỏ/tím chói + mang cổ bừng sáng)
    p_h_p2 = rasterize_view(neck_head_faces, d_enr, g_enr, cam_mat(140, -22), pw, ph, hsc_close * 0.92, head_focus + np.array([0.0, -6.0, 0.0]))
    ImageDraw.Draw(p_h_p2).text((12, 10), "PHASE 2 ENRAGED LOW-ANGLE (BLAZING EYES & LUMINOUS HOOD)", fill=(245, 195, 255))
    p_h_p2.save(PREVIEW_DIR / "preview_head_low_3q_p2.png")

    head_sheet = Image.new("RGB", (pw * 2, ph * 2), (14, 15, 22))
    head_sheet.paste(p_h_front, (0, 0))
    head_sheet.paste(p_h_low3q, (pw, 0))
    head_sheet.paste(p_h_side, (0, ph))
    head_sheet.paste(p_h_p2, (pw, ph))
    head_sheet.save(PREVIEW_DIR / "jormungandr_head_preview_sheet.png")

    # Giữ cập nhật bộ sheet tổng thể 2x2 (bao gồm toàn thân cuộn không đổi + đầu mới 1.35x)
    c1 = (bmin1 + bmax1) * 0.5
    sp1 = float(np.max(bmax1 - bmin1))
    sc1 = (min(pw, ph) * 0.86) / max(1.0, sp1)
    p_3q = rasterize_view(faces_p1, d_norm, g_norm, cam_mat(145, 22), pw, ph, sc1 * 1.15, c1)
    ImageDraw.Draw(p_3q).text((12, 10), "3/4 OBLIQUE REAR-UP COIL (1.35x HEAD + UNTOUCHED BODY)", fill=(235, 215, 255))
    p_3q.save(PREVIEW_DIR / "preview_three_quarter_rearup.png")

    p_top = rasterize_view(faces_p1, d_norm, g_norm, cam_mat(180, 86), pw, ph, sc1 * 1.12, c1)
    ImageDraw.Draw(p_top).text((12, 10), "TOP-DOWN COIL CONTINUITY CHECK (0 GAPS)", fill=(235, 215, 255))
    p_top.save(PREVIEW_DIR / "preview_topdown_coil.png")

    c2 = (bmin2 + bmax2) * 0.5
    sp2 = float(np.max(bmax2 - bmin2))
    sc2 = (min(pw, ph) * 0.86) / max(1.0, sp2)
    p_p2 = rasterize_view(faces_p2, d_enr, g_enr, cam_mat(142, 24), pw, ph, sc2 * 1.15, c2)
    ImageDraw.Draw(p_p2).text((12, 10), "PHASE 2 ENRAGED (48 SEGS x1.4 + 1.35x HEAD)", fill=(245, 195, 255))
    p_p2.save(PREVIEW_DIR / "preview_phase2_enraged.png")

    sheet = Image.new("RGB", (pw * 2, ph * 2), (14, 15, 22))
    sheet.paste(p_h_low3q, (0, 0))
    sheet.paste(p_3q, (pw, 0))
    sheet.paste(p_top, (0, ph))
    sheet.paste(p_p2, (pw, ph))
    sheet.save(PREVIEW_DIR / "jormungandr_preview_sheet.png")
    print(f"PREVIEW_OK: 3 head closeups + head sheet + full-body sheet saved to {PREVIEW_DIR}")


if __name__ == "__main__":
    backup_existing_files()
    geo_data, cube_total = build_geo()
    build_textures()
    build_animations()
    render_previews(geo_data)
    print(f"ALL_DONE: Jormungandr v2 rebuilt ({cube_total} cubes, {TEX_W}x{TEX_H}, N_MAX={N_MAX})")
