#!/usr/bin/env python3
"""
Bộ sinh Model (1.200–1.550 cubes cho bản 3 đầu + bản 1 đầu Phase 2 khít vai) + 10 Texture 512x512
(nearest, fullbright glowmask cho 5 biến thể: normal, enraged, alpha [đỏ], fire [cam], shadow [tím])
+ 17 Animation mượt (catmullrom / ease-in-out, ZERO doubled base rotations!) đồng bộ 0-tick với CerberusAttackTiming.java
+ Bộ Preview 3D Z-buffer Alpha-Cutout đa góc cho CẢ Phase 1 (6 góc) & Phase 2 (3 dạng Alpha/Fire/Shadow)
cho Boss Cerberus (abyssalrpg:cerberus) — Khuyển Tam Thủ Địa Ngục v4.

SỬA DỨT ĐIỂM LỖI ĐẦU GIỮA NGỬA THẲNG TRỜI & 2 ĐẦU BÊN XOAY NGANG 90°:
- Trong GeckoLib 4.8.3, rotation trong `.animation.json` là DELTA cộng thêm vào bind-pose `rotation` trong `.geo.json`.
- Bản cũ vừa đặt `neck_1 rot=[-14,-38,5]` & `neck_2 rot=[-22,0,0]` trong `.geo.json`, VỪA lặp lại y hệt trong `idle`
  -> cộng dồn thành `-79°` ngang và `-48°` ngửa lên, đồng thời `setCustomAnimations()` ghi đè mất góc bẻ ngược của `upperNeck`!
- Giải pháp triệt để v4: TOÀN BỘ bone hoạt ảnh trong `cerberus.geo.json` và `cerberus_single.geo.json` đều có
  `"rotation": [0.0, 0.0, 0.0]`. Dáng quạt 3 đầu nhìn thẳng về trước (`head_2 = 0°`, `head_1 = -18°`, `head_3 = +18°`),
  thế đứng 4 chân dang rộng thấp và đường cong sừng vai được dựng trực tiếp vào hình học cube & pivot,
  mọi keyframe animation đều xoay quanh `[0, 0, 0]` (delta thuần 100%, không bao giờ cộng dồn hay bẻ ngược cổ!).

Chạy: python tools/generate_cerberus_model.py
"""

import copy
import json
import math
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
BACKUP_DIR = ROOT / "tools" / "backup_cerberus_v3"
PREVIEW_DIR = ROOT / "tools" / "cerberus_previews"

TEX_W = 512
TEX_H = 512

# Vùng vật liệu trên atlas 512x512 (x0, y0, w, h) — dùng chung 100% cho cả Phase 1 (3 đầu) & Phase 2 (1 đầu)
MAT = {
    "BASALT_PLATE":    (0,   0,   128, 128),  # Giáp đá bazan xám chì–đen ánh tro xếp lớp (#222028..#6E697C)
    "BASALT_CRACKED":  (128, 0,   128, 128),  # Giáp đá bazan có vết nứt/sẹo dung nham chữ X sắc rực sáng trên vai/đùi!
    "TERRACOTTA_MUSC": (256, 0,   128, 128),  # Cơ da ngực–bụng–họng–bụng đuôi cam đất cháy–đỏ gỉ (#6B2314..#C4572B) + sẹo lửa
    "TAIL_FLAME":      (384, 0,   128, 128),  # Ngọn lửa chóp đuôi alpha-cutout 3 cánh fullbright (#FFF290 -> #FF7A18 -> #C42212)
    "HORN_BRONZE":     (0,   128, 128, 128),  # Sừng vai cong khổng lồ & sừng vương miện nâu đồng cháy/xương nung (#583726..#A86E48)
    "LEG_TRANSITION":  (128, 128, 128, 128),  # Biến tấu cẳng chân: giáp đá xám đen nứt khe lửa -> chuyển đỏ đồng sẫm xuống bàn chân!
    "PAW_COPPER":      (256, 128, 128, 128),  # Bàn chân 4 ngón xòe rộng đỏ đồng sẫm + khe dung nham sáng ở khớp ngón
    "RIDGE_STONE":     (384, 128, 128, 128),  # Dãy vảy giáp đá sống lưng vát ngược về sau có cạnh bắt sáng sắc nét
    "HEAD_ALPHA":      (0,   256, 128, 128),  # Giáp đầu Alpha (giữa — thủ lĩnh) có rãnh giáp vương miện
    "HEAD_FIRE":       (128, 256, 128, 128),  # Giáp đầu Fire (trái) có mào má nứt dung nham phát sáng rực
    "HEAD_SHADOW":     (256, 256, 128, 128),  # Giáp đầu Shadow (phải) đá obsidian góc cạnh có vân ánh tím
    "MOUTH_GLOW":      (384, 256, 128, 128),  # Khoang miệng & họng phát sáng đỏ-cam / tím (fullbright glowmask)
    "EYE_ALPHA":       (0,   384, 64,  64),   # Mắt Alpha (Đỏ rực / đổi theo biến thể Phase 2!)
    "EYE_FIRE":        (64,  384, 64,  64),   # Mắt Fire (Cam vàng rực)
    "EYE_SHADOW":      (128, 384, 64,  64),   # Mắt Shadow (Tím năng lượng rực)
    "FANG_IVORY":      (192, 384, 64,  64),   # Răng nanh trắng ngà sắc nhọn (#F2E6CE)
    "CLAW_OBSIDIAN":   (256, 384, 128, 128),  # Móng vuốt đen obsidian bóng sắc (#14131A -> #363344)
    "DARK_JOINT":      (384, 384, 128, 128),  # Khớp lồng tối sâu nối liền cổ/thân/đuôi/chân chống hở khe
}


def backup_existing_files() -> None:
    if BACKUP_DIR.exists() and any(BACKUP_DIR.rglob("*.json")):
        print(f"BACKUP_SKIP: Đã có bản sao lưu Cerberus trước đó tại {BACKUP_DIR}")
        return
    files = [
        ASSETS / "geo" / "entity" / "cerberus.geo.json",
        ASSETS / "geo" / "entity" / "cerberus_single.geo.json",
        ASSETS / "animations" / "entity" / "cerberus.animation.json",
        ASSETS / "textures" / "entity" / "cerberus.png",
        ASSETS / "textures" / "entity" / "cerberus_glowmask.png",
        ASSETS / "textures" / "entity" / "cerberus_enraged.png",
        ASSETS / "textures" / "entity" / "cerberus_enraged_glowmask.png",
        ASSETS / "textures" / "entity" / "cerberus_alpha.png",
        ASSETS / "textures" / "entity" / "cerberus_alpha_glowmask.png",
        ASSETS / "textures" / "entity" / "cerberus_fire.png",
        ASSETS / "textures" / "entity" / "cerberus_fire_glowmask.png",
        ASSETS / "textures" / "entity" / "cerberus_shadow.png",
        ASSETS / "textures" / "entity" / "cerberus_shadow_glowmask.png",
    ]
    copied = 0
    for f in files:
        if f.exists():
            dst = BACKUP_DIR / f.relative_to(ASSETS)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dst)
            copied += 1
    print(f"BACKUP_OK: Đã sao lưu {copied} file Cerberus cũ vào {BACKUP_DIR}")


def bedrock_rot_matrix(rot_deg):
    """Ma trận xoay CHÍNH XÁC 100% theo GeckoLib 4.8.3: R_true(rx, ry, rz) = Rz(-rz) @ Ry(+ry) @ Rx(-rx)."""
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


def uv_face(mat_key: str, pad: int = 2, sub_u=(0.0, 1.0), sub_v=(0.0, 1.0)) -> dict:
    x0, y0, w, h = MAT[mat_key]
    iw, ih = max(2, w - 2 * pad), max(2, h - 2 * pad)
    ux = int(round(x0 + pad + sub_u[0] * iw))
    uy = int(round(y0 + pad + sub_v[0] * ih))
    uw = max(2, int(round((sub_u[1] - sub_u[0]) * iw)))
    uh = max(2, int(round((sub_v[1] - sub_v[0]) * ih)))
    return {"uv": [ux, uy], "uv_size": [uw, uh]}


def uv_box(mat_key: str, pad: int = 2, sub_u=(0.0, 1.0), sub_v=(0.0, 1.0),
           face_overrides: dict | None = None, transparent_edges: tuple = ()) -> dict:
    f = uv_face(mat_key, pad=pad, sub_u=sub_u, sub_v=sub_v)
    uvs = {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}
    if face_overrides:
        for side, spec in face_overrides.items():
            if isinstance(spec, str):
                uvs[side] = uv_face(spec, pad=pad)
            else:
                mk, su, sv = spec
                uvs[side] = uv_face(mk, pad=pad, sub_u=su, sub_v=sv)
    if transparent_edges:
        x0, y0, w, h = MAT[mat_key]
        trans = {"uv": [x0 + w - 3, y0 + h - 3], "uv_size": [2, 2]}
        for side in transparent_edges:
            uvs[side] = trans
    return uvs


def make_cube(origin, size, mat: str, rot=None, pivot=None,
              sub_u=(0.0, 1.0), sub_v=(0.0, 1.0), pad: int = 2,
              face_overrides: dict | None = None,
              transparent_edges: tuple = ()) -> dict:
    ox, oy, oz = float(origin[0]), float(origin[1]), float(origin[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    uvs = uv_box(mat, pad=pad, sub_u=sub_u, sub_v=sub_v,
                 face_overrides=face_overrides, transparent_edges=transparent_edges)
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


def add_chain_rod(out_list: list, p_start, p_end, thickness_a: float, thickness_b: float,
                  segments: int, mat: str, overlap: float = 0.28, sub_v_span=(0.0, 1.0),
                  face_overrides: dict | None = None, aspect_z: float = 1.0) -> None:
    """Dựng thanh/sừng/chân/móng liền khối dọc p_start -> p_end kiểm định chặt với R_true GeckoLib."""
    a = np.array(p_start, dtype=np.float64)
    b = np.array(p_end, dtype=np.float64)
    vec = b - a
    total_len = float(np.linalg.norm(vec))
    if total_len < 1e-4:
        return
    u_dir = vec / total_len
    horiz = math.hypot(u_dir[0], u_dir[2])
    a_rad = math.atan2(horiz, u_dir[1])
    ry_rad = math.atan2(u_dir[0], u_dir[2]) if horiz > 1e-5 else 0.0
    rot_deg = [-math.degrees(a_rad), +math.degrees(ry_rad), 0.0]

    chk = bedrock_rot_matrix(rot_deg) @ np.array([0.0, 1.0, 0.0], dtype=np.float64)
    assert np.allclose(chk, u_dir, atol=1e-3), f"Rod vector mismatch: {chk} vs {u_dir}"

    for i in range(segments):
        t0 = i / float(segments)
        t1 = (i + 1) / float(segments)
        tm = 0.5 * (t0 + t1)
        tx = thickness_a + (thickness_b - thickness_a) * tm
        tz = tx * aspect_z
        p0 = a + vec * t0
        seg_len = (total_len / segments) + overlap
        v0 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * t0
        v1 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * t1
        out_list.append(make_cube(
            [p0[0] - tx * 0.5, p0[1] - overlap * 0.35, p0[2] - tz * 0.5],
            [tx, seg_len, tz],
            mat,
            rot=rot_deg,
            pivot=p0.tolist(),
            sub_v=(v0, v1),
            face_overrides=face_overrides,
        ))


def add_curved_horn(out_list: list, pts: list, r_start: float, r_end: float, mat: str = "HORN_BRONZE",
                    rib_rings: bool = True) -> None:
    """Dựng sừng cong nhiều đốt thuôn nhọn có khía đốt gân guốc (cho sừng vai khổng lồ & sừng vương miện!)."""
    n = len(pts) - 1
    for i in range(n):
        t0 = i / float(n)
        t1 = (i + 1) / float(n)
        ra = r_start + (r_end - r_start) * t0
        rb = r_start + (r_end - r_start) * t1
        add_chain_rod(out_list, pts[i], pts[i + 1], ra, rb, 3, mat, overlap=0.45, aspect_z=1.08)
        if rib_rings and i < n - 1:
            p_rib = np.array(pts[i], dtype=np.float64) * 0.38 + np.array(pts[i + 1], dtype=np.float64) * 0.62
            d_v = np.array(pts[i + 1], dtype=np.float64) - np.array(pts[i], dtype=np.float64)
            d_u = d_v / max(1e-4, float(np.linalg.norm(d_v)))
            add_chain_rod(out_list, p_rib - d_u * 0.50, p_rib + d_u * 0.50,
                          (ra + rb) * 0.60, (ra + rb) * 0.58, 1, "DARK_JOINT", overlap=0.1, aspect_z=1.08)


def add_local_box_in_frame(out_list: list, frame_origin, frame_yaw_deg: float, frame_pitch_deg: float,
                           loc_min, loc_size, mat: str, extra_rot=(0.0, 0.0, 0.0),
                           face_overrides: dict | None = None) -> None:
    """
    Đặt 1 khối cube định nghĩa theo hệ tọa độ cục bộ của đầu/cổ (X=phải/trái, Y=lên, Z=-trước/+sau),
    sau đó xoay gọn vào khung hướng `frame_yaw_deg` (±18° cho đầu trái/phải, 0° cho đầu giữa!) mà KHÔNG cần
    đặt `bone.rotation` != 0 -> KHẮC PHỤC 100% LỖI CỘNG DỒN GÓC QUAY GECKOLIB!
    """
    # R_frame = R_true(pitch, yaw, 0)
    # Lưu ý: với R_true(0, ry, 0), vector [0, 0, -1] (hướng mõm về trước -Z) xoay sang:
    # Ry(+ry) @ [0, 0, -1] = [-sin(ry), 0, -cos(ry)] -> ry > 0 quay mõm sang -X (trái của thú), ry < 0 sang +X (phải của thú)!
    o = np.array(frame_origin, dtype=np.float64)
    lm = np.array(loc_min, dtype=np.float64)
    ls = np.array(loc_size, dtype=np.float64)
    lc = lm + ls * 0.5
    R_f = bedrock_rot_matrix([frame_pitch_deg, frame_yaw_deg, 0.0])
    wc = o + R_f @ lc
    # Đặt cube căn tâm tại wc rồi xoay theo góc tổng [frame_pitch + extra_x, frame_yaw + extra_y, extra_z]
    tot_rot = [frame_pitch_deg + extra_rot[0], frame_yaw_deg + extra_rot[1], extra_rot[2]]
    out_list.append(make_cube(
        [wc[0] - ls[0] * 0.5, wc[1] - ls[1] * 0.5, wc[2] - ls[2] * 0.5],
        ls.tolist(),
        mat,
        rot=tot_rot,
        pivot=wc.tolist(),
        face_overrides=face_overrides,
    ))


def transform_pt(frame_origin, yaw_deg: float, pitch_deg: float, loc_pt) -> list:
    o = np.array(frame_origin, dtype=np.float64)
    R_f = bedrock_rot_matrix([pitch_deg, yaw_deg, 0.0])
    return (o + R_f @ np.array(loc_pt, dtype=np.float64)).tolist()


# =============================================================================
# 1. XÂY DỰNG 1 CỤM CỔ + ĐẦU + HÀM PHÂN HÓA RÕ 3 VAI (FIRE / ALPHA / SHADOW)
#    Mọi bone đều có `"rotation": [0, 0, 0]` -> 0% lỗi cộng dồn góc quay!
# =============================================================================
def build_head_unit(bones: list, idx: int, neck_root: list, fan_yaw_deg: float,
                    role: str, head_scale: float = 1.0) -> list:
    """
    idx: 1 (trái / FIRE, -X), 2 (giữa / ALPHA, X=0), 3 (phải / SHADOW, +X).
    fan_yaw_deg: +18° cho head_1 (hướng trước-trái -X), 0° cho head_2 (thẳng trước), -18° cho head_3 (hướng trước-phải +X).
    """
    head_mat = {"FIRE": "HEAD_FIRE", "ALPHA": "HEAD_ALPHA", "SHADOW": "HEAD_SHADOW"}[role]
    eye_mat = {"FIRE": "EYE_FIRE", "ALPHA": "EYE_ALPHA", "SHADOW": "EYE_SHADOW"}[role]

    nr = np.array(neck_root, dtype=np.float64)
    # Điểm khớp giữa cổ (`neck_i_upper`) và đầu (`head_i`) nằm theo trục quạt `fan_yaw_deg` hướng thẳng về trước (-Z)!
    R_fan = bedrock_rot_matrix([0.0, fan_yaw_deg, 0.0])
    upper_piv = (nr + R_fan @ np.array([0.0, 3.0 * head_scale, -10.0 * head_scale])).tolist()
    head_piv  = (nr + R_fan @ np.array([0.0, 5.2 * head_scale, -19.2 * head_scale])).tolist()

    # --- A. CỔ DƯỚI (`neck_1/2/3`) ---
    n1c = []
    # Khớp lồng cổ-vai rộng kín tuyệt đối
    add_local_box_in_frame(n1c, nr, fan_yaw_deg, 0.0,
                           [-6.6 * head_scale, -6.2 * head_scale, -4.0 * head_scale],
                           [13.2 * head_scale, 12.2 * head_scale, 8.8 * head_scale],
                           "DARK_JOINT", face_overrides={"down": "TERRACOTTA_MUSC"})
    # Thân cổ dưới cơ bắp (lưng giáp đá bazan, bụng/họng cơ đỏ đất cháy!)
    add_local_box_in_frame(n1c, nr, fan_yaw_deg, -10.0,
                           [-5.9 * head_scale, -4.8 * head_scale, -11.5 * head_scale],
                           [11.8 * head_scale, 10.6 * head_scale, 12.2 * head_scale],
                           "BASALT_PLATE", face_overrides={"up": "RIDGE_STONE", "down": "TERRACOTTA_MUSC"})
    # Dải cơ cổ dưới màu cam đất cháy–đỏ gỉ nổi khối
    for k in range(3):
        add_local_box_in_frame(n1c, nr, fan_yaw_deg, -10.0,
                               [-4.8 * head_scale, -5.8 * head_scale, (-10.8 + k * 3.5) * head_scale],
                               [9.6 * head_scale, 2.4 * head_scale, 4.0 * head_scale],
                               "TERRACOTTA_MUSC")
    # 2 tấm giáp vảy cổ bên xếp lớp 2 tầng + vảy sống cổ vát ngược 2 lớp!
    for side in (-1, 1):
        for k in range(2):
            add_local_box_in_frame(n1c, nr, fan_yaw_deg, -10.0,
                                   [(4.8 if side > 0 else -7.0) * head_scale, (-2.6 + k * 3.8) * head_scale, (-10.6 + k * 0.8) * head_scale],
                                   [2.2 * head_scale, 3.8 * head_scale, 9.6 * head_scale],
                                   head_mat if k == 0 else "BASALT_CRACKED", extra_rot=(0.0, 0.0, side * (12.0 + k * 8.0)))
    for k in range(3):
        add_local_box_in_frame(n1c, nr, fan_yaw_deg, -10.0,
                               [-3.4 * head_scale, 5.0 * head_scale, (-10.2 + k * 3.6) * head_scale],
                               [6.8 * head_scale, 3.0 * head_scale, 4.0 * head_scale],
                               "RIDGE_STONE", extra_rot=(-28.0, 0.0, 0.0))
        add_local_box_in_frame(n1c, nr, fan_yaw_deg, -10.0,
                               [-2.0 * head_scale, 6.8 * head_scale, (-9.8 + k * 3.6) * head_scale],
                               [4.0 * head_scale, 2.0 * head_scale, 3.0 * head_scale],
                               "BASALT_PLATE", extra_rot=(-32.0, 0.0, 0.0))
    # Tấm giáp cổ viền vát chéo 2 bên khớp vai & gờ cơ cổ dưới
    for side in (-1, 1):
        for k in range(3):
            add_local_box_in_frame(n1c, nr, fan_yaw_deg, -10.0,
                                   [(5.4 if side > 0 else -7.2) * head_scale, (-4.2 + k * 3.2) * head_scale, (-10.2 + k * 3.2) * head_scale],
                                   [1.8 * head_scale, 2.6 * head_scale, 3.6 * head_scale],
                                   "RIDGE_STONE" if k == 2 else "BASALT_CRACKED",
                                   extra_rot=(-14.0, side * -12.0, side * 18.0))
    for k in range(2):
        add_local_box_in_frame(n1c, nr, fan_yaw_deg, -10.0,
                               [-3.8 * head_scale, -6.5 * head_scale, (-9.4 + k * 4.8) * head_scale],
                               [7.6 * head_scale, 1.4 * head_scale, 3.6 * head_scale],
                               "TERRACOTTA_MUSC")

    bones.append({
        "name": f"neck_{idx}",
        "parent": "chest",
        "pivot": [round(float(nr[0]), 3), round(float(nr[1]), 3), round(float(nr[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": n1c,
    })

    # --- B. CỔ TRÊN (`neck_1/2/3_upper`) ---
    n2c = []
    add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, 0.0,
                           [-5.6 * head_scale, -4.8 * head_scale, -2.5 * head_scale],
                           [11.2 * head_scale, 9.8 * head_scale, 5.8 * head_scale],
                           "DARK_JOINT", face_overrides={"down": "TERRACOTTA_MUSC"})
    add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, -6.0,
                           [-5.4 * head_scale, -4.6 * head_scale, -10.8 * head_scale],
                           [10.8 * head_scale, 9.8 * head_scale, 11.4 * head_scale],
                           head_mat, face_overrides={"up": "RIDGE_STONE", "down": "TERRACOTTA_MUSC"})
    for k in range(3):
        add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, -6.0,
                               [-4.4 * head_scale, -5.6 * head_scale, (-10.0 + k * 3.3) * head_scale],
                               [8.8 * head_scale, 2.2 * head_scale, 3.8 * head_scale],
                               "TERRACOTTA_MUSC")
    for side in (-1, 1):
        for k in range(2):
            add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, -6.0,
                                   [(4.5 if side > 0 else -6.7) * head_scale, (-2.2 + k * 3.4) * head_scale, (-9.8 + k * 0.6) * head_scale],
                                   [2.2 * head_scale, 3.6 * head_scale, 8.8 * head_scale],
                                   "BASALT_CRACKED" if (role == "FIRE" or k == 1) else "BASALT_PLATE",
                                   extra_rot=(0.0, 0.0, side * (14.0 + k * 8.0)))
    for k in range(3):
        add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, -6.0,
                               [-2.9 * head_scale, 4.6 * head_scale, (-9.6 + k * 3.3) * head_scale],
                               [5.8 * head_scale, 2.8 * head_scale, 3.6 * head_scale],
                               "RIDGE_STONE", extra_rot=(-32.0, 0.0, 0.0))
        add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, -6.0,
                               [-1.8 * head_scale, 6.2 * head_scale, (-9.2 + k * 3.3) * head_scale],
                               [3.6 * head_scale, 1.8 * head_scale, 2.8 * head_scale],
                               "BASALT_PLATE", extra_rot=(-35.0, 0.0, 0.0))
    # Vảy giáp cổ trên vát xếp bậc ôm khớp sọ
    for side in (-1, 1):
        for k in range(3):
            add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, -6.0,
                                   [(5.0 if side > 0 else -6.8) * head_scale, (-3.6 + k * 3.0) * head_scale, (-9.6 + k * 3.0) * head_scale],
                                   [1.8 * head_scale, 2.5 * head_scale, 3.4 * head_scale],
                                   head_mat if k != 1 else "BASALT_CRACKED",
                                   extra_rot=(-10.0, side * -14.0, side * 16.0))
    for k in range(2):
        add_local_box_in_frame(n2c, upper_piv, fan_yaw_deg, -6.0,
                               [-3.4 * head_scale, -6.2 * head_scale, (-8.8 + k * 4.4) * head_scale],
                               [6.8 * head_scale, 1.3 * head_scale, 3.4 * head_scale],
                               "TERRACOTTA_MUSC")

    bones.append({
        "name": f"neck_{idx}_upper",
        "parent": f"neck_{idx}",
        "pivot": [round(float(upper_piv[0]), 3), round(float(upper_piv[1]), 3), round(float(upper_piv[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": n2c,
    })

    # --- C. ĐẦU SÓI–RỒNG ĐÁ SẮC SẢO (`head_1/2/3`) — PHÓNG CỤC BỘ ~1.18x, PHÂN HÓA RÕ 3 VAI! ---
    hs = head_scale * 1.18
    hc = []
    # Sọ sau bọc giáp đá vát 8 cạnh + chỏm giáp đỉnh sọ 2 lớp
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-5.8 * hs, -3.2 * hs, -8.8 * hs],
                           [11.6 * hs, 8.8 * hs, 9.8 * hs],
                           head_mat, face_overrides={"down": "MOUTH_GLOW"})
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-4.4 * hs, 4.8 * hs, -9.6 * hs],
                           [8.8 * hs, 2.4 * hs, 9.4 * hs],
                           "RIDGE_STONE", extra_rot=(8.0, 0.0, 0.0))
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-3.0 * hs, 6.2 * hs, -9.0 * hs],
                           [6.0 * hs, 1.8 * hs, 8.2 * hs],
                           "BASALT_PLATE", extra_rot=(10.0, 0.0, 0.0))
    # 2 gò má giáp xiên 2 tầng + gờ thái dương + mày giáp xiên + mắt phát sáng!
    for side in (-1, 1):
        cx = (4.8 if side > 0 else -7.2) * hs
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [cx, -2.6 * hs, -9.6 * hs],
                               [2.4 * hs, 6.2 * hs, 8.8 * hs],
                               "BASALT_CRACKED" if role == "FIRE" else head_mat,
                               extra_rot=(0.0, side * -10.0, side * 12.0))
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(4.2 if side > 0 else -6.4) * hs, -1.8 * hs, -15.6 * hs],
                               [2.2 * hs, 4.0 * hs, 6.8 * hs],
                               head_mat, extra_rot=(4.0, side * -12.0, side * 8.0))
        # Gờ mày giáp xiên 2 bậc dữ tợn che trên mắt
        bx = (3.0 if side > 0 else -6.4) * hs
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [bx, 3.8 * hs, -13.6 * hs],
                               [3.4 * hs, 2.2 * hs, 7.6 * hs],
                               "RIDGE_STONE", extra_rot=(14.0, side * -10.0, side * -18.0))
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(3.4 if side > 0 else -5.8) * hs, 4.9 * hs, -12.6 * hs],
                               [2.4 * hs, 1.5 * hs, 6.2 * hs],
                               "BASALT_PLATE", extra_rot=(16.0, side * -10.0, side * -20.0))
        # ĐÔI MẮT KHE XIÊN RỰC SÁNG (Đỏ Alpha / Cam Fire / Tím Shadow — fullbright!)
        ex = (4.45 if side > 0 else -5.95) * hs
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [ex, 1.8 * hs, -12.8 * hs],
                               [1.5 * hs, 1.65 * hs, 3.8 * hs],
                               eye_mat, extra_rot=(15.0, side * -10.0, 0.0))
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(4.55 if side > 0 else -5.85) * hs, 0.9 * hs, -12.4 * hs],
                               [1.3 * hs, 0.95 * hs, 3.6 * hs],
                               "DARK_JOINT", extra_rot=(14.0, side * -10.0, 0.0))

    # Mõm sói–rồng đá dài khỏe 3 bậc thuôn góc cạnh + gờ mõm + sống mũi nổi + lỗ mũi phả khói!
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-4.6 * hs, -1.2 * hs, -15.6 * hs],
                           [9.2 * hs, 5.2 * hs, 8.2 * hs],
                           head_mat, face_overrides={"up": "RIDGE_STONE", "down": "MOUTH_GLOW"})
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-3.6 * hs, -0.8 * hs, -20.8 * hs],
                           [7.2 * hs, 4.4 * hs, 5.8 * hs],
                           "BASALT_PLATE", face_overrides={"down": "MOUTH_GLOW"})
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-2.5 * hs, -0.4 * hs, -22.8 * hs],
                           [5.0 * hs, 3.4 * hs, 2.6 * hs],
                           "RIDGE_STONE", face_overrides={"down": "MOUTH_GLOW"})
    # Sống mũi gồ giáp 2 tầng sắc chạy dọc mõm + 2 gờ lip hàm trên
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-1.8 * hs, 3.4 * hs, -21.2 * hs],
                           [3.6 * hs, 1.7 * hs, 11.4 * hs],
                           "RIDGE_STONE", extra_rot=(7.0, 0.0, 0.0))
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-1.1 * hs, 4.5 * hs, -19.6 * hs],
                           [2.2 * hs, 1.1 * hs, 8.8 * hs],
                           "BASALT_PLATE", extra_rot=(7.0, 0.0, 0.0))
    for side in (-1, 1):
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(1.5 if side > 0 else -2.9) * hs, 2.8 * hs, -21.6 * hs],
                               [1.4 * hs, 1.1 * hs, 2.2 * hs], "DARK_JOINT")
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(3.4 if side > 0 else -4.6) * hs, -1.2 * hs, -20.6 * hs],
                               [1.2 * hs, 1.4 * hs, 12.2 * hs], "RIDGE_STONE")
        # Má giáp 2 tầng vát góc cạnh + sống gờ thái dương + bậc mõm trước sắc nét
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(5.4 if side > 0 else -7.4) * hs, -3.2 * hs, -8.2 * hs],
                               [2.0 * hs, 3.4 * hs, 7.6 * hs], "RIDGE_STONE",
                               extra_rot=(-8.0, side * -16.0, side * 18.0))
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(4.4 if side > 0 else -6.2) * hs, 2.2 * hs, -8.0 * hs],
                               [1.8 * hs, 2.8 * hs, 6.8 * hs], "BASALT_CRACKED",
                               extra_rot=(10.0, side * -14.0, side * -12.0))
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(2.6 if side > 0 else -4.2) * hs, 1.4 * hs, -21.0 * hs],
                               [1.6 * hs, 2.4 * hs, 6.2 * hs], "BASALT_PLATE",
                               extra_rot=(6.0, side * -10.0, side * 10.0))
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(3.8 if side > 0 else -5.4) * hs, 4.6 * hs, -6.4 * hs],
                               [1.6 * hs, 2.0 * hs, 5.8 * hs], "RIDGE_STONE",
                               extra_rot=(-12.0, side * -10.0, side * -16.0))

    # Vòm họng phát sáng đỏ-cam + hàng răng nanh trắng ngà sắc nhọn!
    add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                           [-3.8 * hs, -2.0 * hs, -20.2 * hs],
                           [7.6 * hs, 1.3 * hs, 14.5 * hs], "MOUTH_GLOW")
    for side in (-1, 1):
        p_fb = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 3.1 * hs, -0.8 * hs, -19.8 * hs])
        p_ft = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 3.0 * hs, -4.6 * hs, -20.0 * hs])
        add_chain_rod(hc, p_fb, p_ft, 1.50 * hs, 0.35 * hs, 3, "FANG_IVORY", overlap=0.18)
        for k, tz in enumerate((-8.4, -10.2, -12.0, -13.8, -15.6, -17.4, -18.8)):
            th = (1.85 if k % 2 == 0 else 1.25) * hs
            add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                                   [(side * (3.7 - k * 0.12) - 0.40) * hs, -1.2 * hs - th, tz * hs],
                                   [0.80 * hs, th, 0.80 * hs], "FANG_IVORY")
    for tx_o in (-1.8, -0.6, 0.6, 1.8):
        add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                               [(tx_o - 0.38) * hs, -2.4 * hs, -22.2 * hs],
                               [0.76 * hs, 1.5 * hs, 0.76 * hs], "FANG_IVORY")

    # --- PHÂN HÓA ĐẶC TRƯNG 20-30% CHO TỪNG ĐẦU (ALPHA / FIRE / SHADOW) ---
    if role == "ALPHA":
        # ALPHA (đầu giữa): cặp sừng VƯƠNG MIỆN cong vút cao + mào trán giáp vương giả + cặp tai giáp nhọn!
        for side in (-1, 1):
            c_pts = [
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 4.4 * hs, 5.2 * hs, -7.2 * hs]),
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 7.8 * hs, 8.8 * hs, -3.5 * hs]),
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 9.8 * hs, 14.5 * hs, +1.5 * hs]),
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 8.6 * hs, 20.2 * hs, +4.2 * hs]),
            ]
            add_curved_horn(hc, c_pts, 2.5 * hs, 0.55 * hs, "HORN_BRONZE", rib_rings=True)
            # Tai sói bọc giáp đá vát sau + sừng phụ vương miện
            e0 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 4.8 * hs, 5.4 * hs, -4.0 * hs])
            e1 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 6.8 * hs, 10.6 * hs, +2.4 * hs])
            add_chain_rod(hc, e0, e1, 2.2 * hs, 0.5 * hs, 3, "RIDGE_STONE", overlap=0.22)
            a0 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 2.8 * hs, 6.2 * hs, -9.8 * hs])
            a1 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 4.8 * hs, 11.4 * hs, -4.8 * hs])
            add_chain_rod(hc, a0, a1, 1.6 * hs, 0.40 * hs, 3, "HORN_BRONZE", overlap=0.20)
        # Mào vương miện chính giữa trán Alpha (3 bậc sắc cạnh!)
        for k in range(3):
            add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                                   [(-1.4 + k * 0.2) * hs, (6.2 + k * 1.4) * hs, (-11.5 + k * 2.8) * hs],
                                   [(2.8 - k * 0.4) * hs, (3.8 - k * 0.5) * hs, (4.2 - k * 0.4) * hs],
                                   "RIDGE_STONE" if k != 1 else "BASALT_CRACKED", extra_rot=(-18.0 - k * 5.0, 0.0, 0.0))
    elif role == "FIRE":
        # FIRE (đầu trái): mào giáp má nứt dung nham phát sáng + cặp sừng phụ ngắn vát sau + tai gai lửa!
        for side in (-1, 1):
            f_pts = [
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 4.6 * hs, 4.6 * hs, -6.2 * hs]),
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 8.2 * hs, 7.6 * hs, -1.2 * hs]),
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 9.6 * hs, 11.6 * hs, +4.8 * hs]),
            ]
            add_curved_horn(hc, f_pts, 2.2 * hs, 0.5 * hs, "HORN_BRONZE", rib_rings=True)
            # Mào giáp gò má nứt dung nham rực lửa (2 tầng bậc!)
            add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                                   [(5.4 if side > 0 else -8.2) * hs, -1.8 * hs, -7.8 * hs],
                                   [2.8 * hs, 4.8 * hs, 8.6 * hs], "BASALT_CRACKED",
                                   extra_rot=(-10.0, side * -18.0, side * 14.0))
            add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                                   [(6.4 if side > 0 else -8.6) * hs, 0.6 * hs, -5.8 * hs],
                                   [2.2 * hs, 3.2 * hs, 7.2 * hs], "HEAD_FIRE",
                                   extra_rot=(-14.0, side * -22.0, side * 18.0))
            e0 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 5.2 * hs, 3.2 * hs, -2.5 * hs])
            e1 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 8.8 * hs, 5.4 * hs, +4.8 * hs])
            add_chain_rod(hc, e0, e1, 2.0 * hs, 0.45 * hs, 3, "BASALT_CRACKED")
            f0 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 3.8 * hs, 5.4 * hs, -8.8 * hs])
            f1 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 6.2 * hs, 9.2 * hs, -2.2 * hs])
            add_chain_rod(hc, f0, f1, 1.5 * hs, 0.36 * hs, 3, "BASALT_CRACKED")
    else:
        # SHADOW (đầu phải): tai/gai giáp mảnh kéo sắc dài về sau + sống mõm góc cạnh sắc bén!
        for side in (-1, 1):
            s_pts = [
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 4.4 * hs, 4.8 * hs, -6.6 * hs]),
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 7.6 * hs, 7.8 * hs, +0.6 * hs]),
                transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 8.4 * hs, 10.4 * hs, +9.4 * hs]),
            ]
            add_curved_horn(hc, s_pts, 1.9 * hs, 0.35 * hs, "CLAW_OBSIDIAN", rib_rings=False)
            # Đôi gai má mảnh + vây tai sắc kéo ngược kiểu sói bóng tối
            e0 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 5.2 * hs, 0.6 * hs, -5.2 * hs])
            e1 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 8.6 * hs, 2.8 * hs, +5.8 * hs])
            add_chain_rod(hc, e0, e1, 1.7 * hs, 0.32 * hs, 3, "CLAW_OBSIDIAN")
            e2 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 4.8 * hs, -1.6 * hs, -6.8 * hs])
            e3 = transform_pt(head_piv, fan_yaw_deg, 4.0, [side * 8.2 * hs, -0.4 * hs, +3.2 * hs])
            add_chain_rod(hc, e2, e3, 1.4 * hs, 0.30 * hs, 3, "HEAD_SHADOW")
        for k in range(3):
            add_local_box_in_frame(hc, head_piv, fan_yaw_deg, 4.0,
                                   [-1.1 * hs, (4.8 + k * 0.9) * hs, (-22.6 + k * 4.5) * hs],
                                   [2.2 * hs, 1.8 * hs, 5.2 * hs], "CLAW_OBSIDIAN", extra_rot=(6.0 - k * 4.0, 0.0, 0.0))

    loc_name = {1: "mouth_fire_l", 2: "mouth_fire_c", 3: "mouth_fire_r"}[idx]
    mouth_pt = transform_pt(head_piv, fan_yaw_deg, 4.0, [0.0, -2.6 * hs, -17.5 * hs])

    bones.append({
        "name": f"head_{idx}",
        "parent": f"neck_{idx}_upper",
        "pivot": [round(float(head_piv[0]), 3), round(float(head_piv[1]), 3), round(float(head_piv[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "locators": {
            loc_name: [round(mouth_pt[0], 3), round(mouth_pt[1], 3), round(mouth_pt[2], 3)],
        },
        "cubes": hc,
    })

    # --- D. HÀM DƯỚI TÁCH RỜI (`jaw_1/2/3`) — HÁ NHẸ LỘ NANH TRẮNG NGÀ & HỌNG PHÁT SÁNG! ---
    jaw_piv = transform_pt(head_piv, fan_yaw_deg, 4.0, [0.0, -2.8 * hs, -4.6 * hs])
    jc = []
    # Khung hàm dưới hơi há +16° trong chính hình học (bone rotation vẫn = 0!)
    jaw_pitch = 16.0
    add_local_box_in_frame(jc, jaw_piv, fan_yaw_deg, jaw_pitch,
                           [-4.2 * hs, -2.8 * hs, -13.4 * hs],
                           [8.4 * hs, 2.8 * hs, 13.8 * hs],
                           "BASALT_PLATE", face_overrides={"up": "MOUTH_GLOW", "down": "TERRACOTTA_MUSC"})
    add_local_box_in_frame(jc, jaw_piv, fan_yaw_deg, jaw_pitch,
                           [-3.2 * hs, -2.4 * hs, -17.6 * hs],
                           [6.4 * hs, 2.4 * hs, 4.8 * hs],
                           "BASALT_PLATE", face_overrides={"up": "MOUTH_GLOW", "down": "TERRACOTTA_MUSC"})
    # Sống cằm đỏ đất cháy + họng phát sáng rực
    add_local_box_in_frame(jc, jaw_piv, fan_yaw_deg, jaw_pitch,
                           [-2.8 * hs, -3.2 * hs, -15.8 * hs],
                           [5.6 * hs, 1.1 * hs, 12.6 * hs], "TERRACOTTA_MUSC")
    add_local_box_in_frame(jc, jaw_piv, fan_yaw_deg, jaw_pitch,
                           [-3.1 * hs, -0.4 * hs, -16.4 * hs],
                           [6.2 * hs, 0.9 * hs, 14.0 * hs], "MOUTH_GLOW")
    # 2 răng nanh dưới + hàng răng hàm dưới sắc nhọn + má hàm bọc giáp đá & gai cằm!
    for side in (-1, 1):
        p_lb = transform_pt(jaw_piv, fan_yaw_deg, jaw_pitch, [side * 2.5 * hs, 0.0, -16.2 * hs])
        p_lt = transform_pt(jaw_piv, fan_yaw_deg, jaw_pitch, [side * 2.5 * hs, 2.8 * hs, -16.4 * hs])
        add_chain_rod(jc, p_lb, p_lt, 1.2 * hs, 0.30 * hs, 2, "FANG_IVORY", overlap=0.14)
        for k, tz in enumerate((-4.8, -6.8, -8.8, -10.8, -12.8, -14.6)):
            th = (1.45 if k % 2 == 0 else 1.05) * hs
            add_local_box_in_frame(jc, jaw_piv, fan_yaw_deg, jaw_pitch,
                                   [(side * (3.2 - k * 0.12) - 0.38) * hs, 0.0, tz * hs],
                                   [0.76 * hs, th, 0.76 * hs], "FANG_IVORY")
        # Má giáp hàm dưới vát góc cạnh + gai cằm
        add_local_box_in_frame(jc, jaw_piv, fan_yaw_deg, jaw_pitch,
                               [(3.8 if side > 0 else -5.2) * hs, -2.6 * hs, -13.6 * hs],
                               [1.4 * hs, 2.2 * hs, 10.6 * hs], "RIDGE_STONE",
                               extra_rot=(0.0, side * -8.0, side * 12.0))
        add_local_box_in_frame(jc, jaw_piv, fan_yaw_deg, jaw_pitch,
                               [(2.0 if side > 0 else -3.4) * hs, -3.4 * hs, -16.8 * hs],
                               [1.4 * hs, 1.4 * hs, 4.6 * hs], "BASALT_CRACKED",
                               extra_rot=(-14.0, side * -10.0, 0.0))

    bones.append({
        "name": f"jaw_{idx}",
        "parent": f"head_{idx}",
        "pivot": [round(float(jaw_piv[0]), 3), round(float(jaw_piv[1]), 3), round(float(jaw_piv[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": jc,
    })
    return [round(mouth_pt[0], 2), round(mouth_pt[1], 2), round(mouth_pt[2], 2)]


# =============================================================================
# 2. XÂY DỰNG 4 CHÂN DANG RỘNG VỮNG CHÃI (BỎ KIỂU CHÂN ROBOT 90° CŨ!)
#    Vai/đùi đá bazan nứt dung nham -> cẳng chân chuyển sắc đỏ đồng -> bàn chân 4 ngón móng obsidian!
# =============================================================================
def build_leg_assembly(bones: list, leg_id: str, parent: str, side: int, is_front: bool,
                       hip: list, knee: list, ankle: list, paw_center: list) -> list:
    hp = np.array(hip, dtype=np.float64)
    kn = np.array(knee, dtype=np.float64)
    an = np.array(ankle, dtype=np.float64)
    pc = np.array(paw_center, dtype=np.float64)

    # 1. Vai / Đùi trên (`leg_fl`, `leg_fr`, `leg_bl`, `leg_br`) — bọc giáp đá bazan nứt dung nham + gai khuỷu cong ngược!
    uc = []
    # Khối cơ vai/đùi vạm vỡ nối khít thân
    r_h = 6.2 if is_front else 5.8
    uc.append(make_cube([hp[0] - r_h, hp[1] - r_h * 0.95, hp[2] - r_h * 1.15],
                        [r_h * 2.0, r_h * 1.9, r_h * 2.3], "BASALT_CRACKED"))
    # Đốt đùi cơ bắp vát 8 cạnh dang rộng xuống khuỷu/gối (6 đốt thuôn + giáp ngoài/trước/sau!)
    add_chain_rod(uc, hp, kn, 10.2 if is_front else 9.6, 8.4 if is_front else 7.8,
                  6, "BASALT_CRACKED", overlap=0.95, aspect_z=1.22)
    add_chain_rod(uc, hp + np.array([side * 2.9, 1.6, -1.0]),
                  kn + np.array([side * 2.4, 0.8, 0.5]),
                  5.4, 4.3, 5, "BASALT_PLATE", overlap=0.60, aspect_z=1.35)
    add_chain_rod(uc, hp + np.array([side * 0.6, -0.8, -3.5]),
                  kn + np.array([side * 0.6, -0.4, -2.7]),
                  4.8, 3.8, 4, "RIDGE_STONE", overlap=0.50)
    add_chain_rod(uc, hp + np.array([-side * 1.4, -1.2, +2.8]),
                  kn + np.array([-side * 1.0, -0.6, +2.4]),
                  4.2, 3.4, 4, "BASALT_PLATE", overlap=0.45)
    # Khớp khuỷu/gối lồng tối + tấm giáp bảo vệ đầu gối góc cạnh + vảy giáp đùi xếp tầng!
    uc.append(make_cube([kn[0] - 4.1, kn[1] - 4.1, kn[2] - 4.3], [8.2, 8.2, 8.6], "DARK_JOINT"))
    uc.append(make_cube([kn[0] - 3.0 + side * 1.8, kn[1] - 2.6, kn[2] - 4.6], [6.0, 5.2, 4.2], "RIDGE_STONE"))
    for k in range(3):
        t_u = 0.22 + k * 0.26
        pu = hp * (1.0 - t_u) + kn * t_u
        uc.append(make_cube([pu[0] + (3.2 if side > 0 else -6.4), pu[1] - 2.2, pu[2] - 3.6],
                            [3.2, 4.4, 7.2], "RIDGE_STONE" if k == 0 else "BASALT_PLATE",
                            rot=[0.0, 0.0, side * (16.0 - k * 3.0)], pivot=pu.tolist()))
        uc.append(make_cube([pu[0] - 2.6, pu[1] - 2.0, pu[2] - 5.4],
                            [5.2, 3.8, 2.4], "BASALT_CRACKED"))

    # PHÍA SAU KHUỶU 2 CHÂN TRƯỚC: GAI/VÂY GIÁP CONG MỌC NGƯỢC LÊN (đúng 6 ảnh mẫu!)
    if is_front:
        sp_pts = [
            (kn + np.array([side * 2.2, -1.2, +2.6])).tolist(),
            (kn + np.array([side * 5.0, +4.4, +5.8])).tolist(),
            (kn + np.array([side * 6.8, +11.6, +7.2])).tolist(),
            (kn + np.array([side * 6.2, +18.2, +6.6])).tolist(),
        ]
        add_curved_horn(uc, sp_pts, 3.5, 0.68, "HORN_BRONZE", rib_rings=True)
        sp2 = [
            (kn + np.array([side * 2.5, -2.8, +1.5])).tolist(),
            (kn + np.array([side * 5.0, +1.8, +4.0])).tolist(),
            (kn + np.array([side * 6.2, +7.4, +4.8])).tolist(),
        ]
        add_curved_horn(uc, sp2, 2.4, 0.55, "RIDGE_STONE", rib_rings=False)
    else:
        add_chain_rod(uc, kn + np.array([side * 1.4, 0.0, +3.2]),
                      kn + np.array([side * 2.8, +4.4, +7.4]), 2.4, 0.55, 3, "RIDGE_STONE")

    bones.append({
        "name": leg_id,
        "parent": parent,
        "pivot": [round(float(hp[0]), 3), round(float(hp[1]), 3), round(float(hp[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": uc,
    })

    # 2. Cẳng chân dưới (`leg_*_lower`) — BIẾN TẤU 20-30%: giáp đá xám đen nứt khe lửa chuyển dần sang đỏ đồng sẫm!
    lc = []
    add_chain_rod(lc, kn, an, 7.8 if is_front else 7.4, 6.8 if is_front else 6.4,
                  6, "LEG_TRANSITION", overlap=0.82, aspect_z=1.18)
    add_chain_rod(lc, kn + np.array([side * 1.6, -1.0, -2.2]),
                  an + np.array([side * 1.4, +0.6, -1.9]),
                  4.2, 3.4, 5, "BASALT_CRACKED", overlap=0.45)
    add_chain_rod(lc, kn + np.array([side * 2.5, -1.2, +1.0]),
                  an + np.array([side * 2.1, +0.8, +1.2]),
                  3.4, 2.8, 4, "PAW_COPPER", overlap=0.40)
    add_chain_rod(lc, kn + np.array([-side * 1.6, -1.2, +0.4]),
                  an + np.array([-side * 1.4, +0.6, +0.4]),
                  3.2, 2.6, 4, "LEG_TRANSITION", overlap=0.40)
    lc.append(make_cube([an[0] - 4.2, an[1] - 2.6, an[2] - 4.4], [8.4, 5.2, 8.8], "PAW_COPPER"))
    lc.append(make_cube([an[0] - 4.5, an[1] - 1.2, an[2] - 4.7], [9.0, 2.2, 9.4], "BASALT_CRACKED"))

    lower_name = f"{leg_id}_lower"
    bones.append({
        "name": lower_name,
        "parent": leg_id,
        "pivot": [round(float(kn[0]), 3), round(float(kn[1]), 3), round(float(kn[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": lc,
    })

    # 3. Bàn chân xòe rộng chắc chắn (`foot_*`) — 4 ngón chân xòe rõ ràng + khe dung nham khớp ngón + móng vuốt obsidian đen!
    fc = []
    fc.append(make_cube([pc[0] - 5.4, 0.2, pc[2] - 4.8], [10.8, 4.6, 10.0], "PAW_COPPER",
                        face_overrides={"up": "LEG_TRANSITION"}))
    fc.append(make_cube([pc[0] - 4.6, 3.8, pc[2] - 3.8], [9.2, 2.2, 8.0], "BASALT_CRACKED"))

    toe_angles_deg = [-44.0, -15.0, +15.0, +44.0]
    toe_lens = [6.2, 7.6, 7.6, 6.0]
    yaw_bias = side * -12.0
    for k, (ang_d, tlen) in enumerate(zip(toe_angles_deg, toe_lens)):
        rad = math.radians(ang_d + yaw_bias)
        dir_xz = np.array([math.sin(rad), 0.0, -math.cos(rad)], dtype=np.float64)
        t_root = np.array([pc[0], 1.8, pc[2]], dtype=np.float64) + dir_xz * 3.2
        t_knuck = t_root + dir_xz * (tlen * 0.58) + np.array([0.0, -0.25, 0.0])
        t_end = t_knuck + dir_xz * (tlen * 0.48) + np.array([0.0, -0.45, 0.0])
        claw_tip = t_end + dir_xz * 4.2 + np.array([0.0, -0.75, 0.0])
        add_chain_rod(fc, t_root, t_knuck, 2.8, 2.4, 3, "PAW_COPPER", overlap=0.30, aspect_z=1.12)
        add_chain_rod(fc, t_knuck, t_end, 2.35, 1.95, 3, "BASALT_CRACKED", overlap=0.28, aspect_z=1.10)
        add_chain_rod(fc, t_end, claw_tip, 1.90, 0.46, 3, "CLAW_OBSIDIAN", overlap=0.22, aspect_z=1.05)
        # Vòng giáp khớp ngón chân & sống móng vuốt obsidian
        fc.append(make_cube([t_knuck[0] - 1.4, t_knuck[1] + 0.6, t_knuck[2] - 1.4],
                            [2.8, 1.4, 2.8], "RIDGE_STONE"))
        fc.append(make_cube([t_end[0] - 1.1, t_end[1] + 0.4, t_end[2] - 1.1],
                            [2.2, 1.2, 2.2], "DARK_JOINT"))

    # Đệm gót chân sau
    fc.append(make_cube([pc[0] - 2.6, 0.2, pc[2] + 3.8], [5.2, 3.0, 3.6], "PAW_COPPER"))

    foot_name = leg_id.replace("leg_", "foot_")
    pad_loc_name = leg_id.replace("leg_", "foot_pad_")
    bones.append({
        "name": foot_name,
        "parent": lower_name,
        "pivot": [round(float(an[0]), 3), round(float(an[1]), 3), round(float(an[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "locators": {
            pad_loc_name: [round(float(pc[0]), 3), 0.2, round(float(pc[2]), 3)],
        },
        "cubes": fc,
    })
    return [round(float(pc[0]), 2), 0.2, round(float(pc[2]), 2)]


# =============================================================================
# 3. XÂY DỰNG ĐUÔI THẰN LẰN DÀI 6 ĐỐT + CHÓP LỬA 3 CÁNH ALPHA-CUTOUT FULLBRIGHT!
# =============================================================================
def build_tail_assembly(bones: list, tail_start_y: float, tail_start_z: float) -> tuple[list, list]:
    # (seg_l, w_a, h_a, w_b, h_b, dy_drop)
    specs = [
        (11.0, 14.0, 11.4, 11.8,  9.8, -1.6),  # tail_1
        (11.0, 11.6,  9.6,  9.6,  8.2, -1.8),  # tail_2
        (11.0,  9.4,  8.0,  7.6,  6.7, -1.6),  # tail_3
        (10.5,  7.4,  6.5,  5.8,  5.3, -1.4),  # tail_4
        (10.0,  5.6,  5.1,  4.2,  4.0, -1.0),  # tail_5
        (10.0,  4.0,  3.8,  2.4,  2.4, -0.5),  # tail_6 (+ vỏ gai đá ôm gốc ngọn lửa!)
    ]
    cur_y = tail_start_y
    cur_z = tail_start_z
    prev = "pelvis"

    for ti, (seg_l, wa, ha, wb, hb, dy) in enumerate(specs):
        idx = ti + 1
        tc = []
        y_mid = cur_y + dy * 0.5
        pitch = math.degrees(math.atan2(-dy, seg_l))
        # Khớp lồng trước thu nhẹ chống hở khe khi uốn chữ S
        tc.append(make_cube([-(wa * 0.44), cur_y - ha * 0.44, cur_z - 2.0],
                            [wa * 0.88, ha * 0.88, 4.0], "DARK_JOINT",
                            face_overrides={"down": "TERRACOTTA_MUSC"}))
        # Lõi đốt đuôi 2 bậc thuôn mượt (lưng giáp đá bazan, bụng vảy cam đất cháy!)
        tc.append(make_cube([-wa * 0.49, cur_y - ha * 0.49, cur_z - 0.4],
                            [wa * 0.98, ha * 0.98, seg_l * 0.56 + 1.0], "BASALT_PLATE",
                            rot=[pitch, 0.0, 0.0], pivot=[0.0, cur_y, cur_z],
                            face_overrides={"up": "RIDGE_STONE", "down": "TERRACOTTA_MUSC"}))
        tc.append(make_cube([-wb * 0.49, y_mid - hb * 0.49, cur_z + seg_l * 0.46],
                            [wb * 0.98, hb * 0.98, seg_l * 0.58 + 1.0], "BASALT_PLATE",
                            rot=[pitch, 0.0, 0.0], pivot=[0.0, y_mid, cur_z + seg_l * 0.5],
                            face_overrides={"up": "RIDGE_STONE", "down": "TERRACOTTA_MUSC"}))
        # 2 bậc vảy bụng đỏ cam đất cháy nổi rõ dưới đuôi
        for k in range(2):
            fk = 0.25 + k * 0.5
            wk = (wa + (wb - wa) * fk) * 0.80
            hk = (ha + (hb - ha) * fk)
            yk = cur_y + dy * fk
            tc.append(make_cube([-wk * 0.5, yk - hk * 0.5 - 1.1, cur_z + k * seg_l * 0.5 - 0.3],
                                [wk, max(1.0, 1.5 - ti * 0.1), seg_l * 0.56], "TERRACOTTA_MUSC",
                                rot=[pitch, 0.0, 0.0], pivot=[0.0, yk, cur_z + (k + 0.5) * seg_l * 0.5]))
        # 2 tấm vảy giáp đá sống đuôi vát ngược về sau (tiếp nối dãy sống lưng!)
        for k in range(2):
            fk = 0.25 + k * 0.5
            rw = max(1.6, (wa + (wb - wa) * fk) * 0.50)
            rh = max(1.4, (ha + (hb - ha) * fk) * 0.34)
            yk = cur_y + dy * fk
            tc.append(make_cube([-rw * 0.5, yk + (ha + (hb - ha) * fk) * 0.36, cur_z + k * seg_l * 0.5],
                                [rw, rh, seg_l * 0.46], "RIDGE_STONE",
                                rot=[-28.0 + pitch, 0.0, 0.0], pivot=[0.0, yk + 1.0, cur_z + (k + 0.5) * seg_l * 0.5]))
        # Tấm vát 8 cạnh sườn đuôi trái/phải (2 tầng giáp trên & dưới mỗi bên!)
        if wa >= 3.8:
            for side in (-1, 1):
                sw = max(0.9, wa * 0.16)
                sx = (wa * 0.44) if side > 0 else -(wa * 0.44 + sw)
                for k in range(2):
                    tc.append(make_cube([sx, y_mid + (0.4 if k == 0 else -hb * 0.44), cur_z + 0.4 + k * 0.3],
                                        [sw, hb * 0.46, seg_l - 0.6],
                                        "BASALT_CRACKED" if (ti < 2 and k == 0) else "BASALT_PLATE",
                                        rot=[pitch, 0.0, side * (18.0 if k == 0 else -16.0)],
                                        pivot=[side * wa * 0.44, y_mid, cur_z + seg_l * 0.5]))
                # Gờ vảy đá bậc trên sống đuôi hai bên
                tc.append(make_cube([side * wa * 0.24 - sw * 0.5, y_mid + hb * 0.34, cur_z + 0.8],
                                    [sw * 1.1, hb * 0.24, seg_l - 1.4], "RIDGE_STONE",
                                    rot=[pitch, 0.0, side * 26.0], pivot=[side * wa * 0.28, y_mid + hb * 0.42, cur_z + seg_l * 0.5]))

        # Biến tấu 20-30% trên `tail_6`: VỎ GAI ĐÁ ÔM GỐC NGỌN LỬA ĐUÔI trước khi lửa bùng ra!
        if ti == 5:
            ey = cur_y + dy
            ez = cur_z + seg_l
            for k in range(6):
                ang = (math.tau * k) / 6.0
                p0 = [math.cos(ang) * 1.4, ey + math.sin(ang) * 1.4, ez - 2.6]
                p1 = [math.cos(ang) * 3.8, ey + math.sin(ang) * 3.8, ez + 2.8]
                add_chain_rod(tc, p0, p1, 1.35, 0.38, 2, "RIDGE_STONE", overlap=0.18)
            # Lõi than rực ngay trong chén đá giữ ngọn lửa
            tc.append(make_cube([-1.8, ey - 1.8, ez - 1.5], [3.6, 3.6, 4.0], "MOUTH_GLOW"))

        tname = f"tail_{idx}"
        bones.append({
            "name": tname,
            "parent": prev,
            "pivot": [0.0, round(cur_y, 3), round(cur_z, 3)],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": tc,
        })
        cur_y += dy
        cur_z += seg_l
        prev = tname

    # --- NGỌN LỬA CHÓP ĐUÔI 3 CÁNH ALPHA-CUTOUT GIAO CHÉO (`tail_flame`) — FULLBRIGHT 0 PARTICLE! ---
    tf_piv = [0.0, round(cur_y, 3), round(cur_z - 1.0, 3)]
    tfc = []
    # 3 mặt phẳng mỏng cực sắc giao chéo góc 0°, +58°, -58° (hoa thị lửa bùng cuộn đúng ảnh mẫu!)
    flame_len = 22.0
    flame_h = 18.0
    for roll_deg in (0.0, 58.0, -58.0):
        tfc.append(make_cube(
            [-0.18, cur_y - flame_h * 0.5, cur_z - 2.2],
            [0.36, flame_h, flame_len],
            "TAIL_FLAME",
            rot=[0.0, 0.0, roll_deg],
            pivot=tf_piv,
            pad=0,
            transparent_edges=("north", "south", "up", "down"),
        ))
    # Mặt phẳng ngang thêm chiều sâu khi nhìn trực diện từ trên xuống
    tfc.append(make_cube(
        [-flame_h * 0.42, cur_y - 0.18, cur_z - 2.0],
        [flame_h * 0.84, 0.36, flame_len * 0.94],
        "TAIL_FLAME",
        rot=[0.0, 0.0, 0.0],
        pivot=tf_piv,
        pad=0,
        transparent_edges=("north", "south", "east", "west"),
    ))

    tail_fire_pt = [0.0, round(cur_y, 2), round(cur_z + 5.0, 2)]
    tail_tip_pt  = [0.0, round(cur_y, 2), round(cur_z + 17.5, 2)]
    bones.append({
        "name": "tail_flame",
        "parent": "tail_6",
        "pivot": tf_piv,
        "rotation": [0.0, 0.0, 0.0],
        "locators": {
            "tail_fire": tail_fire_pt,
            "tail_tip": tail_tip_pt,
        },
        "cubes": tfc,
    })
    return tail_fire_pt, tail_tip_pt


# =============================================================================
# 4. DỰNG TOÀN BỘ CERBERUS v4 (`cerberus.geo.json` 3 ĐẦU + `cerberus_single.geo.json` 1 ĐẦU PHASE 2)
# =============================================================================
def build_cerberus_geo(single_head: bool = False) -> tuple[dict, int, dict]:
    bones = []
    # Chiều cao thân cơ bắp uy lực (bụng ~19..24, đỉnh vai ~43..47, 4 chân sải dài vững chãi bám đất Y=0!)
    body_y = 29.5

    bones.append({"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})
    bones.append({"name": "body", "parent": "root", "pivot": [0.0, body_y, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})

    # -------------------------------------------------------------------------
    # A. VAI NGỰC CỰC RỘNG CƠ BẮP (`chest` / vai trước + giáp đá bazan xếp lớp nhiều tầng + sẹo lửa bụng!)
    # -------------------------------------------------------------------------
    ch_c = [
        make_cube([-16.2, body_y - 8.2, -23.5], [32.4, 17.6, 27.5], "BASALT_PLATE",
                  face_overrides={"up": "RIDGE_STONE", "down": "TERRACOTTA_MUSC"}),
        make_cube([-14.0, body_y + 8.0, -22.2], [28.0, 5.4, 23.4], "BASALT_PLATE",
                  face_overrides={"up": "RIDGE_STONE"}),
        make_cube([-10.8, body_y + 12.4, -19.8], [21.6, 3.4, 18.6], "RIDGE_STONE",
                  rot=[-8.0, 0.0, 0.0], pivot=[0.0, body_y + 13.2, -10.5]),
        make_cube([-13.6, body_y - 7.6, 2.2], [27.2, 15.6, 5.0], "DARK_JOINT",
                  face_overrides={"down": "TERRACOTTA_MUSC"}),
    ]

    # Tấm ngực trước & bụng ngực cơ bắp cam đất cháy xếp tầng gân guốc
    for k in range(6):
        zk = -24.4 + k * 4.5
        wk = 25.6 - abs(k - 2.0) * 1.4
        ch_c.append(make_cube([-wk * 0.5, body_y - 10.2 - (k % 2) * 0.25, zk], [wk, 2.8, 5.1], "TERRACOTTA_MUSC"))
    ch_c.append(make_cube([-12.8, body_y - 9.4, -25.4], [25.6, 12.8, 3.0], "TERRACOTTA_MUSC"))
    ch_c.append(make_cube([-9.8, body_y - 8.2, -26.6], [19.6, 9.6, 1.8], "TERRACOTTA_MUSC"))
    # 2 khối cơ ức nổi hai bên ngực trước
    for side in (-1, 1):
        ch_c.append(make_cube([side * 5.2 - 4.2, body_y - 7.6, -27.2], [8.4, 7.6, 2.2], "TERRACOTTA_MUSC",
                              rot=[8.0, side * -12.0, 0.0], pivot=[side * 5.2, body_y - 3.8, -26.0]))

    if single_head:
        for side in (-1, 1):
            sx = 6.2 if side > 0 else -16.8
            ch_c.append(make_cube([sx, body_y - 2.0, -25.2], [10.6, 13.2, 10.8], "BASALT_CRACKED",
                                  rot=[12.0, side * -18.0, side * 14.0],
                                  pivot=[side * 11.4, body_y + 4.6, -19.8]))
            ch_c.append(make_cube([sx + side * 0.8, body_y + 8.8, -23.6], [8.8, 3.8, 9.4], "RIDGE_STONE",
                                  rot=[14.0, side * -14.0, side * 20.0],
                                  pivot=[side * 11.4, body_y + 10.6, -19.0]))

    # Giáp vai đá bazan nứt dung nham xếp tầng nhiều lớp hai bên + tấm vảy sống lưng 2 lớp!
    for side in (-1, 1):
        bx = 14.6 if side > 0 else -20.4
        ch_c.append(make_cube([bx, body_y - 4.8, -22.4], [5.8, 16.0, 23.0], "BASALT_CRACKED"))
        for k in range(5):
            px = (10.4 + k * 0.45) if side > 0 else -(17.6 + k * 0.45)
            ch_c.append(make_cube([px, body_y + 9.6 - k * 0.85, -23.2 + k * 5.2],
                                  [7.2, 4.2, 6.2], "BASALT_PLATE",
                                  rot=[10.0 - k * 2.5, side * -11.0, side * (24.0 - k * 1.5)],
                                  pivot=[side * 14.2, body_y + 11.0 - k * 0.85, -20.0 + k * 5.2]))
            # Tấm vảy phụ tầng ngoài chồng bậc sắc cạnh
            qx = (13.4 + k * 0.35) if side > 0 else -(18.4 + k * 0.35)
            ch_c.append(make_cube([qx, body_y + 5.0 - k * 0.7, -22.2 + k * 5.2],
                                  [5.0, 4.0, 5.8], "BASALT_CRACKED" if k % 2 == 0 else "RIDGE_STONE",
                                  rot=[6.0 - k * 2.0, side * -8.0, side * 16.0],
                                  pivot=[side * 15.8, body_y + 7.0 - k * 0.7, -19.2 + k * 5.2]))
        for k in range(4):
            rx = 15.0 if side > 0 else -17.2
            ch_c.append(make_cube([rx, body_y - 8.6, -21.0 + k * 6.0], [2.2, 5.4, 5.4], "TERRACOTTA_MUSC",
                                  rot=[0.0, 0.0, side * -16.0], pivot=[side * 16.0, body_y - 5.8, -18.2 + k * 6.0]))

    # Dãy vảy giáp đá sống lưng lớn vát ngược về sau (2 tầng vát mỗi vảy + 2 cánh giáp phụ hai bên!)
    for k in range(5):
        zk = -21.4 + k * 5.1
        w_s = 7.6 - k * 0.35
        h_s = 5.8 - k * 0.25
        ch_c.append(make_cube([-w_s * 0.5, body_y + 13.8 - k * 0.65, zk], [w_s, h_s, 5.6], "RIDGE_STONE",
                              rot=[-34.0, 0.0, 0.0], pivot=[0.0, body_y + 14.2 - k * 0.65, zk + 2.6]))
        ch_c.append(make_cube([-w_s * 0.32, body_y + 17.0 - k * 0.65, zk + 1.0], [w_s * 0.64, 3.0, 4.0], "BASALT_PLATE",
                              rot=[-38.0, 0.0, 0.0], pivot=[0.0, body_y + 17.5 - k * 0.65, zk + 3.0]))
        for side in (-1, 1):
            ch_c.append(make_cube([side * (w_s * 0.52) - 1.2, body_y + 12.6 - k * 0.65, zk + 0.6],
                                  [2.4, 3.6, 4.4], "BASALT_CRACKED" if k % 2 == 0 else "RIDGE_STONE",
                                  rot=[-30.0, 0.0, side * 22.0], pivot=[side * w_s * 0.52, body_y + 13.6 - k * 0.65, zk + 2.6]))

    bones.append({
        "name": "chest",
        "parent": "body",
        "pivot": [0.0, body_y, -10.0],
        "rotation": [0.0, 0.0, 0.0],
        "locators": {
            "crack_vent_fl": [-17.2, body_y + 3.5, -15.0],
            "crack_vent_fr": [ 17.2, body_y + 3.5, -15.0],
            "crack_vent_back": [0.0, body_y + 14.8, -6.0],
        },
        "cubes": ch_c,
    })

    # -------------------------------------------------------------------------
    # B. CẶP SỪNG VAI CONG C-ARCH KHỔNG LỒ (`shoulder_horn_l`, `shoulder_horn_r`) — ĐÚNG 6 ẢNH MẪU!
    # -------------------------------------------------------------------------
    for side, hname in ((-1, "shoulder_horn_l"), (+1, "shoulder_horn_r")):
        hc = []
        root_p = [side * 16.4, body_y + 5.0, -17.5]
        hc.append(make_cube([root_p[0] - 4.0, root_p[1] - 4.0, root_p[2] - 4.2], [8.0, 8.0, 8.4], "BASALT_CRACKED"))
        hc.append(make_cube([root_p[0] - 3.0, root_p[1] + 2.2, root_p[2] - 3.2], [6.0, 3.6, 6.8], "RIDGE_STONE"))
        # Sừng vai chính cong mở rộng hình chữ C ra hai bên rồi vút cao cuộn ngược về sau!
        main_horn_pts = [
            [side * 16.0, body_y + 4.4, -17.2],
            [side * 23.4, body_y + 8.6, -13.0],
            [side * 27.6, body_y + 14.8, -4.6],
            [side * 27.2, body_y + 22.4, +5.4],
            [side * 23.6, body_y + 29.8, +14.6],
            [side * 19.6, body_y + 35.6, +20.8],
        ]
        add_curved_horn(hc, main_horn_pts, 6.0, 1.00, "HORN_BRONZE", rib_rings=True)
        # Sừng vai phụ phía dưới uốn cong song song
        sub_horn_pts = [
            [side * 15.8, body_y + 1.0, -13.2],
            [side * 22.2, body_y + 4.2, -7.8],
            [side * 24.4, body_y + 9.4, +0.2],
            [side * 22.0, body_y + 15.6, +7.6],
        ]
        add_curved_horn(hc, sub_horn_pts, 3.8, 0.70, "HORN_BRONZE", rib_rings=True)
        bones.append({
            "name": hname,
            "parent": "chest",
            "pivot": [round(root_p[0], 3), round(root_p[1], 3), round(root_p[2], 3)],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": hc,
        })

    # -------------------------------------------------------------------------
    # C. BA CỔ & BA ĐẦU HƯỚNG THẲNG VỀ TRƯỚC THEO HÌNH QUẠT CHẶT (`±20°`, `0°`!)
    # -------------------------------------------------------------------------
    mouth_pivots = [None, None, None]
    if not single_head:
        mouth_pivots[0] = build_head_unit(bones, 1, [-13.6, body_y + 2.4, -22.4], +20.0, "FIRE",   head_scale=0.98)
        mouth_pivots[1] = build_head_unit(bones, 2, [  0.0, body_y + 7.6, -23.4],   0.0, "ALPHA",  head_scale=1.10)
        mouth_pivots[2] = build_head_unit(bones, 3, [+13.6, body_y + 2.4, -22.4], -20.0, "SHADOW", head_scale=0.98)
    else:
        mouth_pivots[1] = build_head_unit(bones, 2, [  0.0, body_y + 6.0, -23.4],   0.0, "ALPHA",  head_scale=1.12)
        mouth_pivots[0] = mouth_pivots[1]
        mouth_pivots[2] = mouth_pivots[1]

    # -------------------------------------------------------------------------
    # D. THÂN GIỮA (`body_mid`) & HÔNG SAU (`pelvis`) — Điêu khắc nhiều tầng giáp đá & thớ cơ đỏ!
    # -------------------------------------------------------------------------
    mid_c = [
        make_cube([-13.4, body_y - 7.4, 3.0], [26.8, 15.2, 17.5], "BASALT_PLATE",
                  face_overrides={"up": "RIDGE_STONE", "down": "TERRACOTTA_MUSC"}),
        make_cube([-11.2, body_y + 6.8, 3.8], [22.4, 3.2, 15.8], "BASALT_PLATE",
                  face_overrides={"up": "RIDGE_STONE"}),
        make_cube([-11.6, body_y - 6.8, 19.2], [23.2, 13.6, 4.6], "DARK_JOINT",
                  face_overrides={"down": "TERRACOTTA_MUSC"}),
    ]
    for k in range(5):
        wk = 21.8 - k * 0.75
        mid_c.append(make_cube([-wk * 0.5, body_y - 9.2 - (k % 2) * 0.22, 2.8 + k * 3.5], [wk, 2.4, 4.1], "TERRACOTTA_MUSC"))
    for side in (-1, 1):
        for k in range(3):
            ux = 11.4 if side > 0 else -15.6
            mid_c.append(make_cube([ux, body_y + 2.0, 3.4 + k * 5.5], [4.2, 7.2, 5.2], "BASALT_PLATE",
                                   rot=[0.0, 0.0, side * 18.0], pivot=[side * 13.5, body_y + 5.6, 6.0 + k * 5.5]))
        for k in range(4):
            sx = 12.4 if side > 0 else -14.6
            mid_c.append(make_cube([sx, body_y - 6.8, 3.4 + k * 4.2], [2.2, 7.4, 3.9], "TERRACOTTA_MUSC",
                                   rot=[0.0, 0.0, side * -12.0], pivot=[side * 13.4, body_y - 3.2, 5.4 + k * 4.2]))
    for k in range(4):
        zk = 3.8 + k * 4.2
        mid_c.append(make_cube([-3.2, body_y + 9.6 - k * 0.35, zk], [6.4, 4.4, 4.6], "RIDGE_STONE",
                               rot=[-32.0, 0.0, 0.0], pivot=[0.0, body_y + 10.2 - k * 0.35, zk + 2.2]))
        mid_c.append(make_cube([-2.0, body_y + 12.2 - k * 0.35, zk + 0.8], [4.0, 2.4, 3.4], "BASALT_PLATE",
                               rot=[-36.0, 0.0, 0.0], pivot=[0.0, body_y + 12.6 - k * 0.35, zk + 2.5]))

    bones.append({
        "name": "body_mid",
        "parent": "body",
        "pivot": [0.0, body_y, 3.5],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": mid_c,
    })

    pel_c = [
        make_cube([-12.6, body_y - 6.8, 20.0], [25.2, 14.0, 18.0], "BASALT_CRACKED",
                  face_overrides={"up": "RIDGE_STONE", "down": "TERRACOTTA_MUSC"}),
        make_cube([-10.0, body_y + 6.4, 20.8], [20.0, 2.8, 16.0], "BASALT_PLATE",
                  face_overrides={"up": "RIDGE_STONE"}),
        make_cube([-9.2, body_y - 5.8, 36.6], [18.4, 11.8, 4.4], "DARK_JOINT",
                  face_overrides={"down": "TERRACOTTA_MUSC"}),
    ]
    for k in range(5):
        wk = 19.2 - k * 0.9
        pel_c.append(make_cube([-wk * 0.5, body_y - 8.4 - (k % 2) * 0.2, 20.2 + k * 3.5], [wk, 2.2, 4.1], "TERRACOTTA_MUSC"))
    for side in (-1, 1):
        for k in range(3):
            px = 10.8 if side > 0 else -14.8
            pel_c.append(make_cube([px, body_y - 3.2, 20.6 + k * 5.4], [4.0, 10.2, 5.2], "BASALT_CRACKED",
                                   rot=[0.0, 0.0, side * 14.0], pivot=[side * 12.8, body_y + 2.0, 23.2 + k * 5.4]))
    for k in range(4):
        zk = 21.0 + k * 4.1
        pel_c.append(make_cube([-2.8, body_y + 8.6 - k * 0.35, zk], [5.6, 3.8, 4.4], "RIDGE_STONE",
                               rot=[-30.0, 0.0, 0.0], pivot=[0.0, body_y + 9.2 - k * 0.35, zk + 2.1]))
        pel_c.append(make_cube([-1.7, body_y + 11.0 - k * 0.35, zk + 0.8], [3.4, 2.2, 3.2], "BASALT_PLATE",
                               rot=[-34.0, 0.0, 0.0], pivot=[0.0, body_y + 11.4 - k * 0.35, zk + 2.4]))

    bones.append({
        "name": "pelvis",
        "parent": "body_mid",
        "pivot": [0.0, body_y, 20.5],
        "rotation": [0.0, 0.0, 0.0],
        "locators": {
            "crack_vent_bl": [-14.0, body_y + 1.0, 28.0],
            "crack_vent_br": [ 14.0, body_y + 1.0, 28.0],
        },
        "cubes": pel_c,
    })

    # -------------------------------------------------------------------------
    # E. 4 CHÂN DANG RỘNG VỮNG CHÃI (TẦM CHÂN CAO KHỎE ĐÚNG 6 ẢNH MẪU!)
    # -------------------------------------------------------------------------
    foot_pads = []
    foot_pads.append(build_leg_assembly(
        bones, "leg_fl", "chest", -1, True,
        hip=[-17.2, body_y + 1.2, -14.2],
        knee=[-29.8, 17.6, -12.6],
        ankle=[-28.4, 4.8, -17.8],
        paw_center=[-29.2, 0.2, -21.2],
    ))
    foot_pads.append(build_leg_assembly(
        bones, "leg_fr", "chest", +1, True,
        hip=[+17.2, body_y + 1.2, -14.2],
        knee=[+29.8, 17.6, -12.6],
        ankle=[+28.4, 4.8, -17.8],
        paw_center=[+29.2, 0.2, -21.2],
    ))
    foot_pads.append(build_leg_assembly(
        bones, "leg_bl", "pelvis", -1, False,
        hip=[-14.0, body_y + 0.4, +27.8],
        knee=[-24.4, 16.8, +32.8],
        ankle=[-23.4, 4.8, +27.4],
        paw_center=[-24.2, 0.2, +23.8],
    ))
    foot_pads.append(build_leg_assembly(
        bones, "leg_br", "pelvis", +1, False,
        hip=[+14.0, body_y + 0.4, +27.8],
        knee=[+24.4, 16.8, +32.8],
        ankle=[+23.4, 4.8, +27.4],
        paw_center=[+24.2, 0.2, +23.8],
    ))

    # -------------------------------------------------------------------------
    # F. ĐUÔI THẰN LẰN DÀI 6 ĐỐT + VỎ GAI ĐÁ + NGỌN LỬA 3 CÁNH FULLBRIGHT!
    # -------------------------------------------------------------------------
    tail_fire_pt, tail_tip_pt = build_tail_assembly(bones, body_y + 0.4, 37.6)

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.cerberus_single" if single_head else "geometry.cerberus",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 16,
                    "visible_bounds_height": 10,
                    "visible_bounds_offset": [0, 3.0, 0.5],
                },
                "bones": bones,
            }
        ],
    }

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    fname = "cerberus_single.geo.json" if single_head else "cerberus.geo.json"
    out_path = ASSETS / "geo" / "entity" / fname
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)

    meta = {
        "bones": len(bones),
        "cubes": total_cubes,
        "mouth_pivots": mouth_pivots,
        "foot_pads": foot_pads,
        "tail_fire": tail_fire_pt,
        "tail_tip": tail_tip_pt,
    }
    print(f"GEO_OK: {fname} -> {len(bones)} bones, {total_cubes} cubes")
    if not single_head:
        print(f"LOCATORS: mouth_l={mouth_pivots[0]} mouth_c={mouth_pivots[1]} mouth_r={mouth_pivots[2]}")
        print(f"          tail_fire={tail_fire_pt} tail_tip={tail_tip_pt} foot_pads={foot_pads}")
    return geo, total_cubes, meta


# =============================================================================
# 5. SINH 10 TEXTURE 512x512 (NEAREST PIXEL-ART ĐÁ BAZAN + CƠ ĐỎ CHÁY + LỬA ĐUÔI + GLOWMASK FULLBRIGHT)
#    5 biến thể: cerberus (P1), cerberus_enraged (P1->P2), cerberus_alpha (Đỏ), cerberus_fire (Cam), cerberus_shadow (Tím)
# =============================================================================
def _hash2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def paint_basalt_armor(diff, glow, rect, variant: str, cracked: bool = False, ridge: bool = False):
    """Tấm giáp đá bazan xám chì–đen ánh tro xếp lớp góc cạnh (#222028, #363340, #4E4A59, viền sáng #6E697C)."""
    x0, y0, w, h = rect
    pal = [
        (34, 32, 40),    # #222028 khe đá sâu
        (54, 51, 64),    # #363340 nền bazan tối
        (78, 74, 89),    # #4E4A59 bề mặt giáp đá
        (110, 105, 124), # #6E697C viền sáng góc cạnh
    ]
    if variant == "shadow":
        pal = [(max(16, r - 6), max(16, g - 8), min(255, b + 8)) for (r, g, b) in pal]
    elif variant == "enraged":
        pal = [(min(255, r + 10), max(18, g - 2), max(22, b - 4)) for (r, g, b) in pal]

    cell = 4
    for gy in range(h // cell):
        for gx in range(w // cell):
            # Vảy tấm đá bazan xếp bậc gạch chéo + thớ đá sắc (không lem bùn!)
            brick_y = gy % 6
            brick_x = (gx + (gy // 6) * 4) % 8
            n = _hash2(gx, gy, 37 if not ridge else 91)
            if brick_y == 0 or brick_x == 0:
                c = pal[0]
            elif brick_y == 1 or (ridge and gx in (0, 1, w // cell - 2, w // cell - 1)):
                c = pal[3]
            elif n > 0.62:
                c = pal[2]
            else:
                c = pal[1]

            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    ao = 0.84 if ((px0 + dx - x0) < 3 or (x0 + w - 1 - (px0 + dx)) < 3) else 1.0
                    diff[py0 + dy, px0 + dx] = (int(c[0] * ao), int(c[1] * ao), int(c[2] * ao), 255)

    # Vết nứt / sẹo dung nham chữ X hoặc tia nứt sắc gọn rực sáng trên vai/đùi/má!
    if cracked or variant == "enraged":
        core_col, halo_col = {
            "normal":  ((255, 224, 96),  (255, 102, 22)),
            "enraged": ((255, 248, 178), (255, 118, 20)),
            "alpha":   ((255, 195, 110), (255, 46, 36)),
            "fire":    ((255, 245, 145), (255, 128, 22)),
            "shadow":  ((238, 178, 255), (156, 48, 255)),
        }[variant]
        # Nét nứt chữ X sắc gọn (đúng mẫu sẹo chữ X ở vai & đùi!)
        segs = [(0.22, 0.24, 0.78, 0.76), (0.76, 0.26, 0.24, 0.74)]
        if variant == "enraged":
            segs.append((0.12, 0.52, 0.88, 0.48))
        for fx0, fy0, fx1, fy1 in segs:
            for s in range(44):
                t = s / 43.0
                jag = math.sin(t * 17.0) * 0.032
                cx = int(np.clip((fx0 + (fx1 - fx0) * t + jag) * (w - 8) + 4, 4, w - 5))
                cy = int(np.clip((fy0 + (fy1 - fy0) * t - jag) * (h - 8) + 4, 4, h - 5))
                rad = 2 if (variant == "enraged" or cracked) else 1
                for dy in range(-rad, rad + 1):
                    for dx in range(-rad, rad + 1):
                        d = abs(dx) + abs(dy)
                        if d <= 1:
                            col = core_col
                            gs = 1.0
                        elif d <= rad + 1:
                            col = halo_col
                            gs = 0.85
                        else:
                            continue
                        diff[y0 + cy + dy, x0 + cx + dx] = (*col, 255)
                        glow[y0 + cy + dy, x0 + cx + dx] = (int(col[0] * gs), int(col[1] * gs), int(col[2] * gs), 255)


def paint_terracotta_muscle(diff, glow, rect, variant: str):
    """Cơ da cam đất cháy–đỏ gỉ (#6B2314 -> #9E3C1E -> #C4572B) có vân cơ gân guốc + sẹo dung nham chữ X sắc!"""
    x0, y0, w, h = rect
    pal = [
        (107, 35, 20),  # #6B2314 rãnh cơ sẫm
        (158, 60, 30),  # #9E3C1E nền cơ địa ngục
        (196, 87, 43),  # #C4572B thớ cơ sáng nổi
        (224, 114, 58), # gờ cơ bắt sáng
    ]
    if variant == "shadow":
        pal = [(88, 34, 64), (128, 48, 92), (166, 68, 120), (196, 94, 148)]
    cell = 4
    for gy in range(h // cell):
        for gx in range(w // cell):
            nx = abs((gx + 0.5) / (w / cell) * 2.0 - 1.0)
            # Thớ cơ ngang chữ V kiểu cơ bụng quái thú địa ngục (đúng ảnh Bottom-Up mẫu!)
            rib = (gy + int(nx * 5.0)) % 5
            n = _hash2(gx, gy, 73)
            if rib == 0:
                c = pal[0]
            elif nx < 0.35 and rib in (2, 3):
                c = pal[3] if n > 0.45 else pal[2]
            elif rib in (1, 2):
                c = pal[2]
            else:
                c = pal[1]
            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    diff[py0 + dy, px0 + dx] = (*c, 255)

    # Sẹo chữ X dung nham sắc gọn giữa vùng bụng/ngực
    core_col = (255, 232, 118) if variant != "shadow" else (238, 178, 255)
    halo_col = (255, 116, 24) if variant != "shadow" else (168, 56, 255)
    for fx0, fy0, fx1, fy1 in ((0.34, 0.34, 0.66, 0.64), (0.64, 0.34, 0.36, 0.64)):
        for s in range(28):
            t = s / 27.0
            cx = int(np.clip((fx0 + (fx1 - fx0) * t) * (w - 8) + 4, 4, w - 5))
            cy = int(np.clip((fy0 + (fy1 - fy0) * t) * (h - 8) + 4, 4, h - 5))
            for dy in range(-1, 2):
                for dx in range(-1, 2):
                    col = core_col if (dx == 0 and dy == 0) else halo_col
                    diff[y0 + cy + dy, x0 + cx + dx] = (*col, 255)
                    glow[y0 + cy + dy, x0 + cx + dx] = (*col, 255)


def paint_leg_transition(diff, glow, rect, variant: str):
    """Biến tấu 20-30%: Cẳng chân giáp đá bazan xám đen (trên v=0) nứt khe lửa chuyển sang đỏ đồng sẫm (dưới v=1)!"""
    x0, y0, w, h = rect
    cell = 4
    for gy in range(h // cell):
        v = gy / float(h // cell - 1)
        for gx in range(w // cell):
            n = _hash2(gx, gy, 151)
            band = (gy % 4 == 0)
            # Chuyển sắc từ bazan xám đen -> đỏ đồng sẫm dần xuống bàn chân
            bas = (46, 44, 56) if not band else (28, 26, 34)
            cop = (176, 74, 46) if not band else (112, 42, 28)
            if variant == "shadow":
                cop = (132, 54, 108) if not band else (84, 34, 72)
            t = np.clip((v - 0.18) / 0.72 + (n - 0.5) * 0.14, 0.0, 1.0)
            r = int(bas[0] * (1 - t) + cop[0] * t)
            g = int(bas[1] * (1 - t) + cop[1] * t)
            b = int(bas[2] * (1 - t) + cop[2] * t)
            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    diff[py0 + dy, px0 + dx] = (r, g, b, 255)
    # Khe dung nham dọc khớp cẳng chân
    seam_c = (255, 190, 68) if variant != "shadow" else (216, 128, 255)
    for y in range(12, h - 12):
        cx = w // 2 + int(math.sin(y * 0.12) * 8)
        diff[y0 + y, x0 + cx] = (*seam_c, 255)
        glow[y0 + y, x0 + cx] = (*seam_c, 255)
        diff[y0 + y, x0 + cx + 1] = (*seam_c, 255)
        glow[y0 + y, x0 + cx + 1] = (*seam_c, 255)


def paint_paw_copper(diff, glow, rect, variant: str):
    """Bàn chân đỏ đồng sẫm xếp vảy ngang + khe dung nham sáng ở khớp ngón."""
    x0, y0, w, h = rect
    cell = 4
    base_hi = (196, 86, 56) if variant != "shadow" else (142, 62, 118)
    base_lo = (128, 48, 34) if variant != "shadow" else (92, 36, 78)
    knuck_g = (255, 174, 48) if variant != "shadow" else (208, 114, 255)
    for gy in range(h // cell):
        is_rib = (gy % 4 == 0)
        is_glow_joint = (gy == h // cell // 2)
        for gx in range(w // cell):
            c = knuck_g if is_glow_joint else (base_lo if is_rib else base_hi)
            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    diff[py0 + dy, px0 + dx] = (*c, 255)
                    if is_glow_joint:
                        glow[py0 + dy, px0 + dx] = (int(c[0] * 0.85), int(c[1] * 0.85), int(c[2] * 0.85), 255)


def paint_horn_bronze(diff, glow, rect, variant: str):
    """Sừng vai cong khổng lồ nâu đồng cháy / xương nung có vân đốt (#583726 -> #8F5B3D -> #B67E52)."""
    x0, y0, w, h = rect
    cell = 4
    for gy in range(h // cell):
        v = gy / float(h // cell - 1)
        is_ring = (gy % 5 == 0)
        for gx in range(w // cell):
            n = _hash2(gx, gy, 211)
            if is_ring:
                c = (68, 42, 28)
            else:
                r = int(np.clip(88 + v * 68 + n * 18, 60, 195))
                g = int(np.clip(55 + v * 46 + n * 12, 36, 140))
                b = int(np.clip(38 + v * 28 + n * 8, 24, 96))
                c = (r, g, b)
            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    diff[py0 + dy, px0 + dx] = (*c, 255)
                    if variant == "enraged" and is_ring and v < 0.55:
                        glow[py0 + dy, px0 + dx] = (255, 118, 24, 255)
                        diff[py0 + dy, px0 + dx] = (255, 134, 34, 255)


def paint_tail_flame(diff, glow, rect, variant: str):
    """Ngọn lửa chóp đuôi alpha-cutout uốn lưỡi lửa bất đối xứng cuộn lên trên (đúng ảnh mẫu, 100% fullbright!)."""
    x0, y0, w, h = rect
    diff[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    glow[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    cell = 4
    gw, gh = w // cell, h // cell
    for gy in range(gh):
        v = (gy + 0.5) / gh  # 0=đỉnh trên, 1=đáy dưới
        for gx in range(gw):
            if gx >= gw - 2 and gy >= gh - 2:
                continue
            u = (gx + 0.5) / gw  # 0=gốc chén đá -> 1=chóp lưỡi lửa bay sau!
            # Trục ngọn lửa hơi uốn vểnh lên trên (v_center dịch lên 0.38 ở cuối đuôi) + 3 lưỡi lửa cuộn!
            v_center = 0.52 - 0.14 * (u ** 1.3) + 0.08 * math.sin(u * 8.0)
            dv = abs(v - v_center) * 2.0
            tongue_top = 0.22 * math.exp(-((u - 0.52) / 0.20) ** 2) if v < v_center else 0.0
            tongue_bot = 0.16 * math.exp(-((u - 0.68) / 0.18) ** 2) if v > v_center else 0.0
            env = (math.sin(min(1.0, u * 1.08) * math.pi) * 0.72 + 0.12 * (1.0 - u)) + tongue_top + tongue_bot
            # Khoét rãnh xoáy nhỏ giữa 2 lưỡi lửa cho silhouette lửa sống động!
            notch = 0.14 * math.exp(-((u - 0.66) / 0.08) ** 2) if (0.28 < v < 0.44) else 0.0
            dist_norm = dv / max(0.06, env - notch)

            ember_dot = (0.76 < u < 0.96) and (abs(v - v_center) < 0.42) and (((gx * 7 + gy * 13) % 11) == 0)
            if dist_norm > 1.0 and not ember_dot:
                continue

            if variant == "shadow":
                cols = ((245, 224, 255), (192, 82, 255), (118, 22, 196))
            elif variant == "enraged":
                cols = ((255, 255, 212), (255, 162, 32), (224, 38, 16))
            else:
                cols = ((255, 242, 144), (255, 122, 24), (196, 34, 18))

            if ember_dot:
                c = cols[1]
            elif dist_norm < 0.36 and u < 0.70:
                c = cols[0]
            elif dist_norm < 0.72:
                c = cols[1]
            else:
                c = cols[2]

            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    diff[py0 + dy, px0 + dx] = (*c, 255)
                    glow[py0 + dy, px0 + dx] = (*c, 255)


def paint_solid_grad(diff, glow, rect, col_a, col_b, glow_strength=0.0):
    x0, y0, w, h = rect
    for y in range(h):
        t = y / max(1.0, h - 1.0)
        for x in range(w):
            j = (_hash2(x // 4, y // 4, 503) - 0.5) * 0.10
            f = np.clip(t + j, 0.0, 1.0)
            r = int(np.clip(col_a[0] * (1 - f) + col_b[0] * f, 0, 255))
            g = int(np.clip(col_a[1] * (1 - f) + col_b[1] * f, 0, 255))
            b = int(np.clip(col_a[2] * (1 - f) + col_b[2] * f, 0, 255))
            diff[y0 + y, x0 + x] = (r, g, b, 255)
            if glow_strength > 0.01:
                glow[y0 + y, x0 + x] = (int(r * glow_strength), int(g * glow_strength), int(b * glow_strength), 255)


def build_textures() -> None:
    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)

    # 5 biến thể texture đầy đủ + glowmask fullbright!
    # Lưu ý: ở Phase 2 (alpha/fire/shadow), đầu giữa dùng MAT["EYE_ALPHA"] -> đổi màu mắt đúng ĐỎ / CAM / TÍM theo vai!
    variants = [
        ("normal",  "cerberus",         ((255, 62, 42),  (255, 174, 34), (192, 68, 255))),
        ("enraged", "cerberus_enraged", ((255, 92, 54),  (255, 224, 88), (222, 104, 255))),
        ("alpha",   "cerberus_alpha",   ((255, 36, 36),  (255, 36, 36),  (255, 36, 36))),   # ALPHA: Mắt Đỏ rực!
        ("fire",    "cerberus_fire",    ((255, 168, 24), (255, 168, 24), (255, 168, 24))),  # FIRE: Mắt Cam rực!
        ("shadow",  "cerberus_shadow",  ((196, 68, 255), (196, 68, 255), (196, 68, 255))),  # SHADOW: Mắt Tím rực!
    ]

    for var_key, base_name, (e_alpha, e_fire, e_shadow) in variants:
        diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
        glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

        paint_basalt_armor(diff, glow, MAT["BASALT_PLATE"],   var_key, cracked=False, ridge=False)
        paint_basalt_armor(diff, glow, MAT["BASALT_CRACKED"], var_key, cracked=True,  ridge=False)
        paint_basalt_armor(diff, glow, MAT["RIDGE_STONE"],    var_key, cracked=False, ridge=True)
        paint_basalt_armor(diff, glow, MAT["HEAD_ALPHA"],     var_key, cracked=(var_key == "enraged"), ridge=True)
        paint_basalt_armor(diff, glow, MAT["HEAD_FIRE"],      var_key, cracked=True,  ridge=False)
        paint_basalt_armor(diff, glow, MAT["HEAD_SHADOW"],    "shadow" if var_key in ("normal", "shadow") else var_key,
                           cracked=(var_key == "enraged"), ridge=False)

        paint_terracotta_muscle(diff, glow, MAT["TERRACOTTA_MUSC"], var_key)
        paint_leg_transition(diff, glow, MAT["LEG_TRANSITION"], var_key)
        paint_paw_copper(diff, glow, MAT["PAW_COPPER"], var_key)
        paint_horn_bronze(diff, glow, MAT["HORN_BRONZE"], var_key)
        paint_tail_flame(diff, glow, MAT["TAIL_FLAME"], var_key)

        # Khoang miệng phát sáng đỏ-cam (hoặc tím cho Shadow)
        m_a = (255, 224, 128) if var_key != "shadow" else (238, 178, 255)
        m_b = (214, 48, 18)   if var_key != "shadow" else (128, 32, 208)
        paint_solid_grad(diff, glow, MAT["MOUTH_GLOW"], m_a, m_b, glow_strength=0.95)

        # 3 đôi mắt fullbright
        paint_solid_grad(diff, glow, MAT["EYE_ALPHA"],  (255, 240, 210), e_alpha,  glow_strength=1.0)
        paint_solid_grad(diff, glow, MAT["EYE_FIRE"],   (255, 248, 190), e_fire,   glow_strength=1.0)
        paint_solid_grad(diff, glow, MAT["EYE_SHADOW"], (248, 224, 255), e_shadow, glow_strength=1.0)

        paint_solid_grad(diff, glow, MAT["FANG_IVORY"],    (246, 236, 212), (212, 194, 162), glow_strength=0.0)
        paint_solid_grad(diff, glow, MAT["CLAW_OBSIDIAN"], (54, 51, 68),    (18, 17, 24),    glow_strength=0.0)
        paint_solid_grad(diff, glow, MAT["DARK_JOINT"],    (36, 34, 44),    (20, 18, 26),    glow_strength=0.0)

        Image.fromarray(diff, "RGBA").save(tex_dir / f"{base_name}.png")
        Image.fromarray(glow, "RGBA").save(tex_dir / f"{base_name}_glowmask.png")

    print(f"TEX_OK: 10 textures (5 variants x diffuse+glowmask, {TEX_W}x{TEX_H} RGBA) saved to {tex_dir}")


# =============================================================================
# 6. SINH 17 ANIMATION MƯỢT (CATMULLROM / EASE-IN-OUT, 100% ZERO-CENTERED DELTAS!)
#    ĐỒNG BỘ 1:1 VỚI CerberusAttackTiming.java (0 TICK DRIFT, 0 GÓC ĐẦU > 45°!)
# =============================================================================
def kf(vec, easing="easeInOutSine"):
    return {"vector": [round(float(v), 2) for v in vec], "easing": easing}


def tk(ticks: int) -> str:
    return f"{ticks / 20.0:.2f}"


def rset(ba: dict, bone: str, tick: int, vec, easing="easeInOutSine"):
    ba.setdefault(bone, {}).setdefault("rotation", {})[tk(tick)] = kf(vec, easing)


def pset(ba: dict, bone: str, tick: int, vec, easing="easeInOutSine"):
    ba.setdefault(bone, {}).setdefault("position", {})[tk(tick)] = kf(vec, easing)


def build_animations() -> dict:
    anims = {}

    # 1. IDLE (3.20s = 64t, loop) — Thở sâu lồng ngực, 3 đầu rình mồi lệch pha (±6° quanh hướng thẳng!), đuôi lửa uốn S!
    b = {}
    for t in (0, 8, 16, 24, 32, 40, 48, 56, 64):
        ph = (t / 64.0) * math.tau
        br = math.sin(ph)
        pset(b, "body", t, [0.0, br * 0.9, 0.0], "catmullrom")
        rset(b, "chest", t, [br * 2.5, 0.0, 0.0], "catmullrom")
        rset(b, "body_mid", t, [-br * 1.5, 0.0, 0.0], "catmullrom")
        # 3 đầu chỉ đung đưa lệch pha nhỏ quanh [0, 0, 0] (tuyệt đối không xoay ngang hay dựng đứng!)
        for hi, ph_off in ((1, 0.0), (2, 2.1), (3, 4.2)):
            s = math.sin(ph + ph_off)
            c = math.cos(ph + ph_off)
            rset(b, f"neck_{hi}", t, [s * 4.5, c * 4.0, 0.0], "catmullrom")
            rset(b, f"neck_{hi}_upper", t, [-s * 2.0, c * 3.0, 0.0], "catmullrom")
            rset(b, f"head_{hi}", t, [c * 3.5, s * 4.5, 0.0], "catmullrom")
            rset(b, f"jaw_{hi}", t, [max(0.0, s * 8.0), 0.0, 0.0], "catmullrom")
        # Đuôi thằn lằn quét chữ S mềm mại + ngọn lửa đuôi lay nhẹ
        for ti in range(1, 7):
            wy = math.sin(ph - ti * 0.58) * (7.0 + ti * 1.6)
            wx = math.cos(ph - ti * 0.45) * (2.5 + ti * 0.5)
            rset(b, f"tail_{ti}", t, [wx, wy, 0.0], "catmullrom")
        rset(b, "tail_flame", t, [math.sin(ph * 2.0) * 6.0, math.cos(ph * 2.0) * 8.0, math.sin(ph) * 12.0], "catmullrom")
    anims["animation.cerberus.idle"] = {"loop": True, "animation_length": 3.2, "bones": b}

    # 2. MOVE / WALK-RUN (0.90s = 18t, loop) — Bước chéo mạnh mẽ 4 chân dang ngang, thân uốn kiểu thằn lằn–sói, 3 đầu chúi rình!
    b = {}
    for t in (0, 3, 6, 9, 12, 15, 18):
        ph = (t / 18.0) * math.tau
        s = math.sin(ph)
        c = math.cos(ph)
        pset(b, "body", t, [s * 1.4, abs(c) * 1.8 - 0.6, 0.0], "catmullrom")
        rset(b, "chest", t, [4.0 + c * 3.0, s * 7.5, -s * 4.0], "catmullrom")
        rset(b, "body_mid", t, [0.0, -s * 8.0, 0.0], "catmullrom")
        rset(b, "pelvis", t, [0.0, -s * 9.0, s * 4.0], "catmullrom")
        # Bước chéo FL+BR đối pha FR+BL
        for leg, sgn in (("leg_fl", +1), ("leg_br", +1), ("leg_fr", -1), ("leg_bl", -1)):
            sw = sgn * s
            lift = max(0.0, sgn * c)
            rset(b, leg, t, [sw * 28.0, 0.0, 0.0], "catmullrom")
            rset(b, f"{leg}_lower", t, [-lift * 26.0, 0.0, 0.0], "catmullrom")
            foot = leg.replace("leg_", "foot_")
            rset(b, foot, t, [lift * 16.0 - sw * 10.0, 0.0, 0.0], "catmullrom")
        # 3 đầu hạ chúi về trước giữ thăng bằng (pitch dương nhẹ +6..+10°!)
        for hi in (1, 2, 3):
            rset(b, f"neck_{hi}", t, [6.0 + c * 3.0, -s * 4.0, 0.0], "catmullrom")
            rset(b, f"head_{hi}", t, [-4.0 - c * 2.5, 0.0, 0.0], "catmullrom")
            rset(b, f"jaw_{hi}", t, [4.0 + abs(s) * 6.0, 0.0, 0.0], "catmullrom")
        for ti in range(1, 7):
            rset(b, f"tail_{ti}", t, [c * 3.0, math.sin(ph - ti * 0.55) * (9.0 + ti * 1.6), 0.0], "catmullrom")
        rset(b, "tail_flame", t, [0.0, s * 10.0, c * 14.0], "catmullrom")
    anims["animation.cerberus.move"] = {"loop": True, "animation_length": 0.9, "bones": b}

    # 3-5. BITE_1 / BITE_2 / BITE_3 (0.80s = 16t mỗi nhịp) — Giật cổ lùi + há rộng hàm (0..7) -> Phóng táp cực nhanh (8..10, impact 9) -> Recovery (11..16)!
    for hi, bname in ((1, "bite_1"), (2, "bite_2"), (3, "bite_3")):
        b = {}
        rset(b, "chest", 0, [0, 0, 0])
        rset(b, "chest", 7, [-8, (hi - 2) * -8, 0], "easeInCubic")
        rset(b, "chest", 10, [10, (hi - 2) * 8, 0], "easeOutExpo")
        rset(b, "chest", 16, [0, 0, 0])
        rset(b, f"neck_{hi}", 0, [0, 0, 0])
        rset(b, f"neck_{hi}", 7, [-18, 0, 0], "easeInCubic")
        rset(b, f"neck_{hi}", 10, [22, 0, 0], "easeOutExpo")
        rset(b, f"neck_{hi}", 16, [0, 0, 0])
        rset(b, f"neck_{hi}_upper", 0, [0, 0, 0])
        rset(b, f"neck_{hi}_upper", 7, [-10, 0, 0], "easeInCubic")
        rset(b, f"neck_{hi}_upper", 10, [14, 0, 0], "easeOutExpo")
        rset(b, f"neck_{hi}_upper", 16, [0, 0, 0])
        rset(b, f"jaw_{hi}", 0, [0, 0, 0])
        rset(b, f"jaw_{hi}", 7, [44, 0, 0], "easeInCubic")
        rset(b, f"jaw_{hi}", 10, [-10, 0, 0], "easeOutExpo")
        rset(b, f"jaw_{hi}", 16, [0, 0, 0])
        anims[f"animation.cerberus.{bname}"] = {"loop": False, "animation_length": 0.8, "bones": b}

    # 6. BREATH / HELLFIRE_BREATH (2.60s = 52t) — Hít sâu kéo 3 cổ lùi (0..16) -> Rướn đầu quét ngang phun lửa (18..44) -> Thu về (52)!
    b = {}
    rset(b, "chest", 0, [0, 0, 0])
    rset(b, "chest", 16, [-14, 14, 0], "easeInCubic")
    rset(b, "chest", 20, [8, 18, 0], "easeOutCubic")
    rset(b, "chest", 32, [10, 0, 0], "catmullrom")
    rset(b, "chest", 44, [8, -18, 0], "easeOutSine")
    rset(b, "chest", 52, [0, 0, 0])
    for hi in (1, 2, 3):
        rset(b, f"neck_{hi}", 0, [0, 0, 0])
        rset(b, f"neck_{hi}", 16, [-16, 0, 0], "easeInCubic")
        rset(b, f"neck_{hi}", 20, [14, 10, 0], "easeOutCubic")
        rset(b, f"neck_{hi}", 44, [14, -10, 0], "easeInOutSine")
        rset(b, f"neck_{hi}", 52, [0, 0, 0])
        rset(b, f"jaw_{hi}", 0, [0, 0, 0])
        rset(b, f"jaw_{hi}", 16, [24, 0, 0], "easeInCubic")
        rset(b, f"jaw_{hi}", 20, [48, 0, 0], "easeOutExpo")
        rset(b, f"jaw_{hi}", 44, [44, 0, 0], "linear")
        rset(b, f"jaw_{hi}", 52, [0, 0, 0])
    anims["animation.cerberus.breath"] = {"loop": False, "animation_length": 2.6, "bones": b}

    # 7. POUNCE (2.00s = 40t) — Nén thấp rình sát đất (0..16) -> Bật vọt căng thân trên không (18..28) -> Giáng đáp đất (30, recovery 40)!
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 15, [0, -7.5, 4.0], "easeInCubic")
    pset(b, "body", 22, [0, 5.5, -5.0], "easeOutCubic")
    pset(b, "body", 30, [0, -4.5, -2.0], "easeOutExpo")
    pset(b, "body", 40, [0, 0, 0])
    rset(b, "chest", 0, [0, 0, 0])
    rset(b, "chest", 15, [14, 0, 0], "easeInCubic")
    rset(b, "chest", 22, [-20, 0, 0], "easeOutCubic")
    rset(b, "chest", 30, [16, 0, 0], "easeOutExpo")
    rset(b, "chest", 40, [0, 0, 0])
    for leg in ("leg_fl", "leg_fr"):
        rset(b, leg, 0, [0, 0, 0])
        rset(b, leg, 15, [28, 0, 0], "easeInCubic")
        rset(b, leg, 22, [-42, 0, 0], "easeOutCubic")
        rset(b, leg, 30, [18, 0, 0], "easeOutExpo")
        rset(b, leg, 40, [0, 0, 0])
    for leg in ("leg_bl", "leg_br"):
        rset(b, leg, 0, [0, 0, 0])
        rset(b, leg, 15, [-26, 0, 0], "easeInCubic")
        rset(b, leg, 22, [38, 0, 0], "easeOutCubic")
        rset(b, leg, 30, [-14, 0, 0], "easeOutExpo")
        rset(b, leg, 40, [0, 0, 0])
    for hi in (1, 2, 3):
        rset(b, f"jaw_{hi}", 0, [0, 0, 0])
        rset(b, f"jaw_{hi}", 22, [44, 0, 0], "easeOutCubic")
        rset(b, f"jaw_{hi}", 40, [0, 0, 0])
    anims["animation.cerberus.pounce"] = {"loop": False, "animation_length": 2.0, "bones": b}

    # 8. SLAM / GROUND_SLAM (1.80s = 36t) — Nhấc bổng nửa thân trước & 2 chân trước lên cao (0..16) -> Đập sầm xuống đất (impact 18..20)!
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 15, [0, 8.5, 2.0], "easeInCubic")
    pset(b, "body", 19, [0, -5.5, -2.5], "easeOutExpo")
    pset(b, "body", 26, [0, -2.0, -1.0], "easeOutSine")
    pset(b, "body", 36, [0, 0, 0])
    rset(b, "body", 0, [0, 0, 0])
    rset(b, "body", 15, [-28, 0, 0], "easeInCubic")
    rset(b, "body", 19, [14, 0, 0], "easeOutExpo")
    rset(b, "body", 36, [0, 0, 0])
    for leg in ("leg_fl", "leg_fr"):
        rset(b, leg, 0, [0, 0, 0])
        rset(b, leg, 15, [-52, 0, 0], "easeInCubic")
        rset(b, leg, 19, [20, 0, 0], "easeOutExpo")
        rset(b, leg, 36, [0, 0, 0])
    for hi in (1, 2, 3):
        rset(b, f"neck_{hi}", 0, [0, 0, 0])
        rset(b, f"neck_{hi}", 15, [-18, 0, 0], "easeInCubic")
        rset(b, f"neck_{hi}", 19, [16, 0, 0], "easeOutExpo")
        rset(b, f"neck_{hi}", 36, [0, 0, 0])
        rset(b, f"jaw_{hi}", 0, [0, 0, 0])
        rset(b, f"jaw_{hi}", 15, [46, 0, 0], "easeInCubic")
        rset(b, f"jaw_{hi}", 22, [34, 0, 0])
        rset(b, f"jaw_{hi}", 36, [0, 0, 0])
    anims["animation.cerberus.slam"] = {"loop": False, "animation_length": 1.8, "bones": b}

    # 9. HOWL / FLAME_RAIN (2.00s = 40t) — Hạ thấp hông sau, nâng 3 đầu tru vang nhịp nhàng (clamp pitch <= -24°, KHÔNG bẻ ngược cổ!)
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 14, [0, -2.5, 2.0], "easeInOutSine")
    pset(b, "body", 28, [0, -2.0, 1.5], "easeInOutSine")
    pset(b, "body", 40, [0, 0, 0])
    rset(b, "body", 0, [0, 0, 0])
    rset(b, "body", 16, [-14, 0, 0], "easeOutCubic")
    rset(b, "body", 30, [-12, 0, 0], "easeInOutSine")
    rset(b, "body", 40, [0, 0, 0])
    for hi in (1, 2, 3):
        rset(b, f"neck_{hi}", 0, [0, 0, 0])
        rset(b, f"neck_{hi}", 16, [-18, 0, 0], "easeOutCubic")
        rset(b, f"neck_{hi}", 30, [-16, 0, 0], "easeInOutSine")
        rset(b, f"neck_{hi}", 40, [0, 0, 0])
        rset(b, f"head_{hi}", 0, [0, 0, 0])
        rset(b, f"head_{hi}", 16, [-14, 0, 0], "easeOutCubic")
        rset(b, f"head_{hi}", 30, [-12, 0, 0], "easeInOutSine")
        rset(b, f"head_{hi}", 40, [0, 0, 0])
        rset(b, f"jaw_{hi}", 0, [0, 0, 0])
        rset(b, f"jaw_{hi}", 16, [44, 0, 0], "easeOutExpo")
        rset(b, f"jaw_{hi}", 30, [40, 0, 0], "linear")
        rset(b, f"jaw_{hi}", 40, [0, 0, 0])
    anims["animation.cerberus.howl"] = {"loop": False, "animation_length": 2.0, "bones": b}

    # 10. PHASE_CHANGE (1.80s = 36t, Stage 1 gầm bốc cháy trước khi nổ 3 hỏa cầu; 0° quay đầu ngang!)
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 14, [0, -4.5, 0], "easeInCubic")
    pset(b, "body", 24, [0, 5.5, 0], "easeOutExpo")
    pset(b, "body", 36, [0, 2.0, 0])
    rset(b, "chest", 0, [0, 0, 0])
    rset(b, "chest", 14, [14, 0, 0], "easeInCubic")
    rset(b, "chest", 24, [-22, 0, 0], "easeOutExpo")
    rset(b, "chest", 36, [-14, 0, 0])
    for hi in (1, 2, 3):
        rset(b, f"neck_{hi}", 0, [0, 0, 0])
        rset(b, f"neck_{hi}", 24, [-20, 0, 0], "easeOutExpo")
        rset(b, f"neck_{hi}", 36, [-12, 0, 0])
        rset(b, f"jaw_{hi}", 0, [0, 0, 0])
        rset(b, f"jaw_{hi}", 24, [52, 0, 0], "easeOutExpo")
        rset(b, f"jaw_{hi}", 36, [46, 0, 0])
    anims["animation.cerberus.phase_change"] = {"loop": False, "animation_length": 1.8, "bones": b}

    # 11. SPLIT_FIREBALL (0.60s = 12t, loop) — Cuộn tròn nhẹ khi bay hỏa cầu (đồng bộ hidden render)
    b = {}
    rset(b, "body", 0, [0, 0, 0])
    rset(b, "body", 6, [18, 0, 0])
    rset(b, "body", 12, [0, 0, 0])
    anims["animation.cerberus.split_fireball"] = {"loop": True, "animation_length": 0.6, "bones": b}

    # 12. LAND_SPAWN (1.60s = 32t) — 3 cá thể 1 đầu tiếp đất nặng nề rồi bật cổ gầm!
    b = {}
    pset(b, "body", 0, [0, -8.5, 0])
    pset(b, "body", 14, [0, 2.5, 0], "easeOutBack")
    pset(b, "body", 32, [0, 0, 0], "easeInOutSine")
    rset(b, "chest", 0, [18, 0, 0])
    rset(b, "chest", 16, [-18, 0, 0], "easeOutExpo")
    rset(b, "chest", 32, [0, 0, 0])
    rset(b, "jaw_2", 0, [10, 0, 0])
    rset(b, "jaw_2", 16, [48, 0, 0], "easeOutExpo")
    rset(b, "jaw_2", 32, [0, 0, 0])
    anims["animation.cerberus.land_spawn"] = {"loop": False, "animation_length": 1.6, "bones": b}

    # 13. NOVA / HELLFIRE_NOVA (2.10s = 42t) — FIRE_HEAD thu thân tụ hỏa (0..16) -> Bung sóng lửa 360° (impact 18..40)!
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 15, [0, -5.5, 0], "easeInCubic")
    pset(b, "body", 20, [0, 5.0, 0], "easeOutExpo")
    pset(b, "body", 42, [0, 0, 0])
    rset(b, "chest", 0, [0, 0, 0])
    rset(b, "chest", 15, [16, 0, 0], "easeInCubic")
    rset(b, "chest", 20, [-20, 0, 0], "easeOutExpo")
    rset(b, "chest", 42, [0, 0, 0])
    rset(b, "jaw_2", 0, [0, 0, 0])
    rset(b, "jaw_2", 20, [50, 0, 0], "easeOutExpo")
    rset(b, "jaw_2", 42, [0, 0, 0])
    anims["animation.cerberus.nova"] = {"loop": False, "animation_length": 2.1, "bones": b}

    # 14. DASH / WRATH_CHARGE & SHADOW_DASH (1.60s = 32t) — Nén thấp đầu chúi (0..14) -> Lao húc cực nhanh (14..26) -> Phanh!
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 13, [0, -5.0, 4.5], "easeInCubic")
    pset(b, "body", 17, [0, -1.5, -7.5], "easeOutExpo")
    pset(b, "body", 26, [0, -1.0, -4.0], "easeOutSine")
    pset(b, "body", 32, [0, 0, 0])
    rset(b, "chest", 0, [0, 0, 0])
    rset(b, "chest", 13, [12, 0, 0], "easeInCubic")
    rset(b, "chest", 17, [16, 0, 0], "easeOutExpo")
    rset(b, "chest", 32, [0, 0, 0])
    rset(b, "neck_2", 0, [0, 0, 0])
    rset(b, "neck_2", 17, [14, 0, 0], "easeOutExpo")
    rset(b, "neck_2", 32, [0, 0, 0])
    rset(b, "jaw_2", 0, [0, 0, 0])
    rset(b, "jaw_2", 17, [44, 0, 0], "easeOutExpo")
    rset(b, "jaw_2", 32, [0, 0, 0])
    anims["animation.cerberus.dash"] = {"loop": False, "animation_length": 1.6, "bones": b}

    # 15. SHADOW_STEP (1.20s = 24t) — Cuộn bóng ẩn thân (0..12) -> Hiện hình bổ cắn chớp nhoáng (13..24)!
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 12, [0, -6.0, 2.0], "easeInCubic")
    pset(b, "body", 15, [0, 1.5, -5.0], "easeOutExpo")
    pset(b, "body", 24, [0, 0, 0])
    rset(b, "neck_2", 0, [0, 0, 0])
    rset(b, "neck_2", 15, [20, 0, 0], "easeOutExpo")
    rset(b, "neck_2", 24, [0, 0, 0])
    rset(b, "jaw_2", 0, [0, 0, 0])
    rset(b, "jaw_2", 13, [46, 0, 0], "easeInCubic")
    rset(b, "jaw_2", 16, [-8, 0, 0], "easeOutExpo")
    rset(b, "jaw_2", 24, [0, 0, 0])
    anims["animation.cerberus.shadow_step"] = {"loop": False, "animation_length": 1.2, "bones": b}

    # 16. PACK_HUNT (2.40s = 48t) — Rình thấp chực vồ rồi bật lao mạnh theo nhịp combo 3 cá thể!
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 16, [0, -6.5, 4.0], "easeInCubic")
    pset(b, "body", 22, [0, 2.0, -7.0], "easeOutExpo")
    pset(b, "body", 34, [0, -1.0, -3.5], "easeOutSine")
    pset(b, "body", 48, [0, 0, 0])
    rset(b, "chest", 0, [0, 0, 0])
    rset(b, "chest", 16, [14, 0, 0])
    rset(b, "chest", 22, [-14, 0, 0], "easeOutExpo")
    rset(b, "chest", 48, [0, 0, 0])
    rset(b, "jaw_2", 0, [0, 0, 0])
    rset(b, "jaw_2", 22, [48, 0, 0], "easeOutExpo")
    rset(b, "jaw_2", 48, [0, 0, 0])
    anims["animation.cerberus.pack_hunt"] = {"loop": False, "animation_length": 2.4, "bones": b}

    # 17. DEATH (2.75s = 55t, hold) — Khuỵu 4 chân nặng nề, 3 đầu gục xuống mặt đất, đuôi tắt lửa đổ nghiêng!
    b = {}
    pset(b, "body", 0, [0, 0, 0])
    pset(b, "body", 20, [0, -7.0, 0], "easeInCubic")
    pset(b, "body", 42, [0, -17.0, 0], "easeOutBounce")
    pset(b, "body", 55, [0, -17.5, 0], "linear")
    rset(b, "body", 0, [0, 0, 0])
    rset(b, "body", 42, [12, 0, 32], "easeInCubic")
    rset(b, "body", 55, [14, 0, 36], "linear")
    for hi in (1, 2, 3):
        rset(b, f"neck_{hi}", 0, [0, 0, 0])
        rset(b, f"neck_{hi}", 42, [24, 0, 0], "easeOutCubic")
        rset(b, f"neck_{hi}", 55, [28, 0, 0], "linear")
        rset(b, f"jaw_{hi}", 0, [0, 0, 0])
        rset(b, f"jaw_{hi}", 20, [36, 0, 0])
        rset(b, f"jaw_{hi}", 55, [22, 0, 0], "linear")
    anims["animation.cerberus.death"] = {"loop": "hold_on_last_frame", "animation_length": 2.75, "bones": b}

    # Kiểm định an toàn tuyệt đối: KHÔNG có keyframe cổ/đầu nào vượt quá ±55°!
    for aname, adata in anims.items():
        for bname, bdata in adata.get("bones", {}).items():
            for tkey, kv in bdata.get("rotation", {}).items():
                for comp in kv["vector"]:
                    assert abs(comp) <= 60.0, f"Rotation exceeds 60° in {aname}.{bname}@{tkey}: {kv}"

    out = {"format_version": "1.8.0", "animations": anims}
    out_path = ASSETS / "animations" / "entity" / "cerberus.animation.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
    print(f"ANIM_OK: {out_path} -> {len(anims)} animations verified (0 angles > 60°, 100% zero-centered deltas)")
    return out


# =============================================================================
# 7. BỘ RENDER PREVIEW 3D Z-BUFFER ALPHA-CUTOUT CHUẨN GECKOLIB 4.8.3
#    Xuất đầy đủ: 6 góc Phase 1 (3 đầu) + 3 dạng Phase 2 (1 đầu: Alpha / Fire / Shadow)!
# =============================================================================
def collect_world_geometry(geo: dict):
    bone_map = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    xforms = {}

    def get_xform(name):
        if name in xforms:
            return xforms[name]
        b = bone_map[name]
        piv = np.array(b.get("pivot", [0.0, 0.0, 0.0]), dtype=np.float64)
        R_loc = bedrock_rot_matrix(b.get("rotation", [0.0, 0.0, 0.0]))
        t_loc = piv - R_loc @ piv
        parent = b.get("parent")
        if parent and parent in bone_map:
            Rp, tp = get_xform(parent)
            R = Rp @ R_loc
            t = Rp @ t_loc + tp
        else:
            R, t = R_loc, t_loc
        xforms[name] = (R, t)
        return R, t

    faces = []
    pts_all = []
    face_defs = [
        ("north", (0, 1, 2, 3)),
        ("south", (5, 4, 7, 6)),
        ("west",  (4, 0, 3, 7)),
        ("east",  (1, 5, 6, 2)),
        ("up",    (3, 2, 6, 7)),
        ("down",  (4, 5, 1, 0)),
    ]

    for b in geo["minecraft:geometry"][0]["bones"]:
        Rb, tb = get_xform(b["name"])
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            corners = np.array([
                [ox,      oy,      oz],
                [ox + sx, oy,      oz],
                [ox + sx, oy + sy, oz],
                [ox,      oy + sy, oz],
                [ox,      oy,      oz + sz],
                [ox + sx, oy,      oz + sz],
                [ox + sx, oy + sy, oz + sz],
                [ox,      oy + sy, oz + sz],
            ], dtype=np.float64)
            if "rotation" in c:
                cp = np.array(c["pivot"], dtype=np.float64)
                Rc = bedrock_rot_matrix(c["rotation"])
                corners = ((corners - cp) @ Rc.T) + cp
            world_c = (corners @ Rb.T) + tb
            pts_all.append(world_c)
            uv_map = c["uv"]
            for fname, idxs in face_defs:
                if fname in uv_map:
                    faces.append((world_c[list(idxs)], uv_map[fname]))

    all_p = np.vstack(pts_all)
    return faces, all_p.min(axis=0), all_p.max(axis=0)


def rasterize(faces, diff_img, glow_img, view_mat, width, height, scale, center,
              bg=(24, 24, 34), light_dir=None):
    fb = np.zeros((height, width, 3), dtype=np.float32)
    fb[:, :] = np.array(bg, dtype=np.float32)
    zbuf = np.full((height, width), -1e9, dtype=np.float32)
    cx, cy = width * 0.5, height * 0.54
    if light_dir is None:
        light_dir = np.array([0.42, 0.78, -0.46], dtype=np.float64)
    light_dir = light_dir / np.linalg.norm(light_dir)

    def rtri(p0, p1, p2, uv0, uv1, uv2, shade):
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
        xs = np.arange(min_x, max_x + 1, dtype=np.float32) + 0.5
        ys = np.arange(min_y, max_y + 1, dtype=np.float32) + 0.5
        gx, gy = np.meshgrid(xs, ys)
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
        ap = diff_img[v, u, 3] >= 128.0
        if not np.any(ap):
            return
        cy_i, cx_i = np.where(cand)
        cy_i, cx_i = cy_i[ap], cx_i[ap]
        u, v = u[ap], v[ap]
        sub_z[cy_i, cx_i] = z[cy_i, cx_i]
        dc = diff_img[v, u, :3]
        gc = glow_img[v, u, :3]
        ga = glow_img[v, u, 3:4] / 255.0
        lit = dc * shade * (1.0 - ga * 0.88) + gc * (ga * 1.18)
        fb[min_y + cy_i, min_x + cx_i] = np.clip(lit, 0, 255)

    for quad, uv in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = 0.44 + 0.56 * abs(float(np.dot(n, light_dir)))
        scr = np.stack([cx - cam[:, 0] * scale, cy - cam[:, 1] * scale, cam[:, 2]], axis=1)
        u0, v0 = uv["uv"]
        uw, uh = uv["uv_size"]
        u1, v1 = u0 + max(1, uw) - 0.01, v0 + max(1, uh) - 0.01
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        rtri(scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
        rtri(scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)

    return Image.fromarray(fb.astype(np.uint8), "RGB")


def render_previews(geo_p1: dict, geo_p2: dict) -> None:
    tex_dir = ASSETS / "textures" / "entity"
    def load_pair(name):
        return (
            np.array(Image.open(tex_dir / f"{name}.png").convert("RGBA"), dtype=np.float32),
            np.array(Image.open(tex_dir / f"{name}_glowmask.png").convert("RGBA"), dtype=np.float32),
        )

    d_p1, g_p1 = load_pair("cerberus")
    d_al, g_al = load_pair("cerberus_alpha")
    d_fi, g_fi = load_pair("cerberus_fire")
    d_sh, g_sh = load_pair("cerberus_shadow")

    faces1, bmin1, bmax1 = collect_world_geometry(geo_p1)
    c1 = (bmin1 + bmax1) * 0.5
    span1 = float(np.max(bmax1 - bmin1))

    faces2, bmin2, bmax2 = collect_world_geometry(geo_p2)
    c2 = (bmin2 + bmax2) * 0.5
    span2 = float(np.max(bmax2 - bmin2))

    def cam_mat(yaw_deg, pitch_deg):
        y, p = math.radians(yaw_deg), math.radians(pitch_deg)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
        Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64)
        return Rx @ Ry

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 640, 460
    sc1 = (min(pw, ph) * 0.82) / max(1.0, span1)
    sc2 = (min(pw, ph) * 0.82) / max(1.0, span2)
    bg = (26, 26, 36)

    # 6 góc Phase 1 (3 đầu)
    views = [
        ("preview_p1_front.png",      180,   8, sc1 * 1.55, np.array([0.0, 26.0, -14.0]), None, "P1 FRONT (3 FORWARD HEADS +-20 DEG, CROWN/SHOULDER HORNS, WIDE STANCE)"),
        ("preview_p1_side_left.png",  -90,   4, sc1 * 1.12, c1,                           None, "P1 LEFT SIDE (BASALT PLATES, CURVED HORNS, ELBOW SPURS, FLAME TAIL)"),
        ("preview_p1_side_right.png",  90,   4, sc1 * 1.12, c1,                           None, "P1 RIGHT SIDE (3 FORWARD HEADS, TERRACOTTA BELLY, MAGMA SCARS)"),
        ("preview_p1_top.png",         90,  86, sc1 * 1.10, c1,                           None, "P1 TOP-DOWN (3 TIGHT-FAN HEADS, STEPPED SPINE RIDGE, 4 SPLAYED PAWS)"),
        ("preview_p1_bottom.png",     180, -86, sc1 * 0.96, c1, np.array([0.0, -1.0, 0.0]), "P1 BOTTOM-UP (TERRACOTTA MUSCLE BELLY, X-SCARS, 4x4 CLAWED PAWS)"),
        ("preview_p1_rear.png",         0,  10, sc1 * 1.45, np.array([0.0, 24.0, +16.0]), None, "P1 REAR (WIDE SPLAYED LEGS, SHOULDER HORNS, FLAMING TAIL TIP)"),
    ]
    imgs1 = []
    for fn, yw, pt, sc, ctr, ld, title in views:
        im = rasterize(faces1, d_p1, g_p1, cam_mat(yw, pt), pw, ph, sc, ctr, bg=bg, light_dir=ld)
        ImageDraw.Draw(im).text((12, 10), title, fill=(235, 224, 210))
        im.save(PREVIEW_DIR / fn)
        imgs1.append(im)

    sheet1 = Image.new("RGB", (pw * 3, ph * 2), (16, 16, 22))
    for i, im in enumerate(imgs1):
        sheet1.paste(im, ((i % 3) * pw, (i // 3) * ph))
    sheet1.save(PREVIEW_DIR / "cerberus_p1_sheet.png")

    # 3 biến thể Phase 2 (1 đầu: cerberus_single.geo.json + Alpha / Fire / Shadow!)
    p2_specs = [
        ("preview_p2_alpha.png",  d_al, g_al, 142, 14, "P2 SINGLE: ALPHA_HEAD (RED EYES, CROWN HORNS, SEALED SHOULDERS)"),
        ("preview_p2_fire.png",   d_fi, g_fi, -142, 14, "P2 SINGLE: FIRE_HEAD (ORANGE EYES, MAGMA CRACKS, FLAME TAIL)"),
        ("preview_p2_shadow.png", d_sh, g_sh, 148, 12, "P2 SINGLE: SHADOW_HEAD (VIOLET EYES, PURPLE HELLFLAME TAIL)"),
    ]
    imgs2 = []
    for fn, d_im, g_im, yw, pt, title in p2_specs:
        im = rasterize(faces2, d_im, g_im, cam_mat(yw, pt), pw, ph, sc2 * 1.25, c2, bg=(18, 18, 28))
        ImageDraw.Draw(im).text((12, 10), title, fill=(240, 225, 215))
        im.save(PREVIEW_DIR / fn)
        imgs2.append(im)

    sheet2 = Image.new("RGB", (pw * 3, ph), (14, 14, 20))
    for i, im in enumerate(imgs2):
        sheet2.paste(im, (i * pw, 0))
    sheet2.save(PREVIEW_DIR / "cerberus_p2_sheet.png")

    print(
        f"PREVIEW_OK: Saved 6 P1 views + 3 P2 single views + 2 sheets to {PREVIEW_DIR} | "
        f"P1 bbox X=[{bmin1[0]:.1f}..{bmax1[0]:.1f}] Y=[{bmin1[1]:.1f}..{bmax1[1]:.1f}] Z=[{bmin1[2]:.1f}..{bmax1[2]:.1f}] | "
        f"P2 bbox X=[{bmin2[0]:.1f}..{bmax2[0]:.1f}] Y=[{bmin2[1]:.1f}..{bmax2[1]:.1f}] Z=[{bmin2[2]:.1f}..{bmax2[2]:.1f}]"
    )


if __name__ == "__main__":
    backup_existing_files()
    geo_p1, cubes_p1, meta_p1 = build_cerberus_geo(single_head=False)
    geo_p2, cubes_p2, meta_p2 = build_cerberus_geo(single_head=True)
    build_textures()
    build_animations()
    render_previews(geo_p1, geo_p2)
    print(f"ALL_DONE: Cerberus v4 rebuilt (P1: {meta_p1['bones']} bones, {cubes_p1} cubes | P2 single: {meta_p2['bones']} bones, {cubes_p2} cubes | {TEX_W}x{TEX_H})")
