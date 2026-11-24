#!/usr/bin/env python3
"""
Bộ sinh Model (~1.200 cubes) + Texture 512x512 (nearest, alpha-cutout mang quạt & tua năng lượng)
+ 16 Animation mượt (catmullrom / ease-in-out) đồng bộ 0-tick tuyệt đối với HydraAttackTiming.java
+ Preview 3D Z-buffer Alpha-Cutout đa góc (Front, 3/4, Side, Top, Bottom, Phase 2 Enraged)
cho Boss Spirit Hydra (abyssalrpg:spirit_hydra) — Phiên bản Tứ Thủ Oan Hồn 4 đầu v2.

Thiết kế:
- 70–80% giữ đúng phom dáng & linh hồn mẫu Spirit Hydra 4 đầu:
  + 4 cổ dài mảnh uốn chữ S tách quạt rộng (2 cổ giữa vươn cao, 2 cổ ngoài mở thấp hai bên, 0% chồng lấp)
  + Mỗi cổ 6 đốt có khớp lồng thu đầu-cuối (overlapping joint cap), dải bụng cổ phát sáng xanh băng & tua năng lượng rũ
  + 4 đầu rồng mõm dài thuôn góc cạnh, hàm dưới tách rời (`jaw_1..4`) có hàng răng trắng sắc, khoang miệng/lưỡi phát sáng mạnh,
    mắt xiên trắng-lam, sừng trán nghiêng ra sau phát sáng (`horn_1..4`), mang quạt sau gáy (`frill_1..4_l/r`) gồm 5 nan gai
    nhô vượt mép + màng alpha phát sáng xanh băng–trắng
  + Thân thằn lằn bò sát thuôn dài (`chest`, `torso`, `pelvis`), 4 chân bò sát khuỵu thấp mở ngang (`leg_fl/fr/bl/br` gồm
    `thigh`, `shin`, `foot`) có vòng năng lượng xanh băng cổ chân & 4 ngón móng vuốt phát sáng, đuôi dài 7 đốt (`tail_1..7`)
    uốn cong vút lên cao + dải bụng sáng liên tục xuyên suốt từ mặt dưới 4 cổ -> ngực -> bụng -> mặt dưới đuôi
  + Vảy đá xám than–tro đen loang lổ (#1D2026, #2F3540, #475060, #647082) + glowmask fullbright (#E8FFFF -> #4DF4FF -> #0EA5E9)
- 20–30% biến tấu bản sắc Abyssal Spire RPG + phóng to cục bộ đầu/mang quạt (~1.20x) & độ dày cổ cho sắc nét:
  + Sống mũi giáp góc cạnh, gờ mày sắc, 2 gai góc hàm/cằm mỗi đầu
  + Hàng gai sống lưng đá nhỏ, sắc, thấp dọc thân & đuôi (khe sáng mảnh ở gốc) + chóp đuôi tinh thể linh hồn chẻ đôi
  + Màng mang quạt 2 tầng có đường gân sáng tia sét & mép khuyết sắc
  + Phase 2 Enraged: khe nứt sáng trắng-xanh điện/tím lạnh lan lên lưng, vai, sườn; mắt & mang quạt rực sáng cực gắt

Chạy: python tools/generate_spirit_hydra_model.py
"""

import json
import math
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
BACKUP_DIR = ROOT / "tools" / "backup_hydra_v1"
PREVIEW_DIR = ROOT / "tools" / "hydra_previews"

TEX_W = 512
TEX_H = 512

# Vùng vật liệu trên atlas 512x512 (x0, y0, w, h)
MAT = {
    "STONE_SCALE":    (0,   0,   128, 128),  # Vảy đá xám than–tro đen loang lổ (#1D2026..#647082) + khe AO
    "STONE_DORSAL":   (128, 0,   128, 128),  # Giáp sống lưng/đầu/gờ mày sẫm hơn, viền sắc + nứt sáng ở Phase 2
    "BELLY_GLOW":     (256, 0,   128, 128),  # Dải bụng phát sáng liên tục (#E8FFFF -> #4DF4FF -> #0EA5E9) có rãnh vảy bụng
    "FRILL_MEMBRANE": (384, 0,   128, 192),  # Màng mang quạt alpha-cutout có gân sáng tia sét & mép khuyết sắc!
    "NECK_FRINGE":    (0,   128, 128, 128),  # Tua năng lượng xanh băng rũ dọc mặt dưới & sườn cổ (alpha-cutout sắc nét!)
    "MOUTH_GLOW":     (128, 128, 128, 128),  # Khoang miệng, lưỡi & mặt dưới hàm có sọc năng lượng xanh hồn rực rỡ
    "JAW_RIBBED":     (256, 128, 128, 128),  # Mặt dưới hàm dưới tối có đốt sọc phát sáng cyan (đúng ảnh góc nhìn dưới!)
    "FRILL_RAY":      (0,   256, 128, 128),  # Nan gai mang quạt xám đen sắc lạnh nhô vượt mép màng
    "HORN_GLOW":      (128, 256, 128, 128),  # Sừng trán tinh thể xanh băng phát sáng (gốc xanh lam -> lõi trắng-lam)
    "EYE_GLOW":       (256, 256, 64,  64),   # Mắt xiên phát sáng trắng-lam rực rỡ
    "TOOTH_WHITE":    (320, 256, 64,  64),   # Răng trắng ngà sắc nhọn ánh lam nhẹ
    "ANKLE_BAND":     (384, 192, 128, 128),  # Vòng năng lượng xanh băng phát sáng quanh cổ chân / khớp gối
    "CLAW_GLOW":      (256, 320, 128, 64),   # Móng vuốt phát sáng trắng-lam chói ở đầu ngón chân
    "STUMP_CORE":     (0,   384, 128, 128),  # Mỏm cổ bị chặt: rìa đá rách + lõi linh hồn xanh cháy sục sôi
    "TAIL_CRYSTAL":   (128, 384, 128, 128),  # Tinh thể chóp đuôi chẻ đôi phát sáng xanh điện–trắng
    "DORSAL_SPINE":   (256, 384, 128, 128),  # Gai sống lưng đá tối sắc có khe sáng mảnh ở gốc
    "DARK_JOINT":     (384, 320, 128, 128),  # Khớp lồng cổ/đuôi tối sâu tạo chiều sâu liền mạch không khe hở
}


def backup_existing_files() -> None:
    if BACKUP_DIR.exists() and any(BACKUP_DIR.rglob("*.json")):
        print(f"BACKUP_SKIP: Đã có bản sao lưu gốc tại {BACKUP_DIR}")
        return
    files = [
        ASSETS / "geo" / "entity" / "spirit_hydra.geo.json",
        ASSETS / "animations" / "entity" / "spirit_hydra.animation.json",
        ASSETS / "textures" / "entity" / "spirit_hydra.png",
        ASSETS / "textures" / "entity" / "spirit_hydra_glowmask.png",
        ASSETS / "textures" / "entity" / "spirit_hydra_enraged.png",
        ASSETS / "textures" / "entity" / "spirit_hydra_enraged_glowmask.png",
    ]
    copied = 0
    for f in files:
        if f.exists():
            dst = BACKUP_DIR / f.relative_to(ASSETS)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dst)
            copied += 1
    print(f"BACKUP_OK: Đã sao lưu {copied} file Spirit Hydra cũ vào {BACKUP_DIR}")


def bedrock_rot_matrix(rot_deg):
    """
    Ma trận xoay CHÍNH XÁC 100% theo mã nguồn GeckoLib 4.8.3 (`BakedModelFactory.Builtin` + `RenderUtil`):
      R_true(rx, ry, rz) = Rz(-rz) @ Ry(+ry) @ Rx(-rx)
    """
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
                  segments: int, mat: str, overlap: float = 0.25, sub_v_span=(0.0, 1.0),
                  face_overrides: dict | None = None) -> None:
    """Dựng thanh/gai/sừng/nan quạt liền khối 100% dọc đoạn p_start -> p_end theo R_true GeckoLib 4.8.3."""
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

    rotated_y = bedrock_rot_matrix(rot_deg) @ np.array([0.0, 1.0, 0.0], dtype=np.float64)
    assert np.allclose(rotated_y, u_dir, atol=1e-3), f"Rod vector mismatch: {rotated_y} vs {u_dir}"

    for i in range(segments):
        t0 = i / float(segments)
        t1 = (i + 1) / float(segments)
        tm = 0.5 * (t0 + t1)
        thick = thickness_a + (thickness_b - thickness_a) * tm
        p0 = a + vec * t0
        seg_len = (total_len / segments) + overlap
        v0 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * t0
        v1 = sub_v_span[0] + (sub_v_span[1] - sub_v_span[0]) * t1
        out_list.append(make_cube(
            [p0[0] - thick * 0.5, p0[1] - overlap * 0.35, p0[2] - thick * 0.5],
            [thick, seg_len, thick],
            mat,
            rot=rot_deg,
            pivot=p0.tolist(),
            sub_v=(v0, v1),
            face_overrides=face_overrides,
        ))


def add_angled_box_along(out_list: list, p_start, p_end, width_x: float, height_y: float,
                         mat: str, pad_start: float = 0.0, pad_end: float = 0.0,
                         face_overrides: dict | None = None,
                         sub_u=(0.0, 1.0), sub_v=(0.0, 1.0),
                         transparent_edges: tuple = ()) -> None:
    """
    Dựng hộp có trục dài (-Z cục bộ hoặc +Z cục bộ) nằm gọn trong mặt phẳng YZ (dx ~ 0)
    hoặc bất kỳ hướng p_start -> p_end trong mặt phẳng YZ, dùng đúng R_true(rotX, rotY, 0).
    Ở đây hộp cục bộ chạy theo +Y (từ 0 tới L), rộng width_x theo X và dày height_y theo Z —
    nhưng với trục đốt cổ/đuôi nằm trong mặt phẳng YZ (x gần như không đổi), dùng trục -Z là tự nhiên nhất:
    Với R_true(rx, 0, 0) = Rx(-rx):
      Rx(-rx) @ [0, 0, -1] = [0, -sin(rx), -cos(rx)]
      Rx(-rx) @ [0, 1, 0]  = [0,  cos(rx), -sin(rx)]
    """
    a = np.array(p_start, dtype=np.float64)
    b = np.array(p_end, dtype=np.float64)
    vec = b - a
    L = float(np.linalg.norm(vec))
    if L < 1e-4:
        return
    u = vec / L
    # Chỉ dùng khi nằm trong mặt phẳng YZ (|u[0]| < 1e-3): chọn trục local -Z hướng theo u
    assert abs(u[0]) < 1e-3, f"add_angled_box_along expects YZ-plane vector, got {u}"
    # Muốn [-sin(rx), -cos(rx)] == [u_y, u_z] => sin(rx) = -u_y, cos(rx) = -u_z
    rx_deg = math.degrees(math.atan2(-u[1], -u[2]))
    rot_deg = [rx_deg, 0.0, 0.0]
    chk = bedrock_rot_matrix(rot_deg) @ np.array([0.0, 0.0, -1.0])
    assert np.allclose(chk, u, atol=1e-3), f"YZ box mismatch: {chk} vs {u}"

    full_L = L + pad_start + pad_end
    # Đặt origin theo -Z từ a + pad_start*Z về a - (L + pad_end)*Z, xoay quanh a
    ox = a[0] - width_x * 0.5
    oy = a[1] - height_y * 0.5
    oz = a[2] - (L + pad_end)
    out_list.append(make_cube(
        [ox, oy, oz],
        [width_x, height_y, full_L],
        mat,
        rot=rot_deg,
        pivot=a.tolist(),
        sub_u=sub_u,
        sub_v=sub_v,
        face_overrides=face_overrides,
        transparent_edges=transparent_edges,
    ))


def add_angled_box_along_pos_z(out_list: list, p_start, p_end, width_x: float, height_y: float,
                               mat: str, pad_start: float = 0.0, pad_end: float = 0.0,
                               face_overrides: dict | None = None,
                               sub_u=(0.0, 1.0), sub_v=(0.0, 1.0),
                               transparent_edges: tuple = ()) -> None:
    """
    Dựng hộp có trục dài (+Z cục bộ) hướng theo p_start -> p_end trong mặt phẳng YZ (dành cho đuôi vươn ra +Z và cong lên +Y!):
    Với R_true(rx, 0, 0) = Rx(-rx):
      Rx(-rx) @ [0, 0, +1] = [0, +sin(rx), +cos(rx)]
      Rx(-rx) @ [0, +1, 0] = [0, +cos(rx), -sin(rx)] (luôn hướng LÊN trên sống đuôi, KHÔNG BAO GIỜ lật ngược 180°!)
    """
    a = np.array(p_start, dtype=np.float64)
    b = np.array(p_end, dtype=np.float64)
    vec = b - a
    L = float(np.linalg.norm(vec))
    if L < 1e-4:
        return
    u = vec / L
    assert abs(u[0]) < 1e-3, f"add_angled_box_along_pos_z expects YZ-plane vector, got {u}"
    rx_deg = math.degrees(math.atan2(u[1], u[2]))
    rot_deg = [rx_deg, 0.0, 0.0]
    chk = bedrock_rot_matrix(rot_deg) @ np.array([0.0, 0.0, 1.0])
    assert np.allclose(chk, u, atol=1e-3), f"YZ +Z box mismatch: {chk} vs {u}"

    full_L = L + pad_start + pad_end
    ox = a[0] - width_x * 0.5
    oy = a[1] - height_y * 0.5
    oz = a[2] - pad_start
    out_list.append(make_cube(
        [ox, oy, oz],
        [width_x, height_y, full_L],
        mat,
        rot=rot_deg,
        pivot=a.tolist(),
        sub_u=sub_u,
        sub_v=sub_v,
        face_overrides=face_overrides,
        transparent_edges=transparent_edges,
    ))


# =============================================================================
# 1. CẤU HÌNH HÌNH HỌC 4 CỔ & ĐẦU (FAN RỘNG, 0% CHỒNG LẤP, ĐẦU PHÓNG TO CỤC BỘ ~1.20x)
# =============================================================================
NECK_SPECS = [
    {
        "idx": 1,
        "role": "outer_l",
        "root": [9.4, 14.5, -10.5],
        "fan_yaw": -54.0,           # Mở quạt rộng mạnh sang TRÁI (+X) — 0% chồng lấp!
        "head_counter_yaw": +20.0,  # Hơi xoay đầu hướng về trước (+34° trái)
        "head_pitch_rest": +6.0,
        "curve_yz": [
            (0.0,  0.0),
            (2.2, -7.8),
            (7.5, -14.8),
            (13.2, -21.2),
            (14.6, -28.6),
            (11.6, -36.0),
            (10.0, -42.8),
        ],
    },
    {
        "idx": 2,
        "role": "inner_l",
        "root": [4.8, 17.0, -13.0],
        "fan_yaw": -21.5,           # Tách rõ 2 đầu giữa để 2 mang quạt trong KHÔNG BAO GIỜ chạm nhau!
        "head_counter_yaw": +8.5,   # Hướng gần thẳng trước (+13° trái)
        "head_pitch_rest": +9.0,
        "curve_yz": [
            (0.0,  0.0),
            (7.0, -6.2),
            (16.0, -10.4),
            (24.8, -15.2),
            (30.2, -22.2),
            (29.8, -30.4),
            (26.2, -38.4),
        ],
    },
    {
        "idx": 3,
        "role": "inner_r",
        "root": [-4.8, 17.0, -13.0],
        "fan_yaw": +21.5,
        "head_counter_yaw": -8.5,
        "head_pitch_rest": +9.0,
        "curve_yz": [
            (0.0,  0.0),
            (7.0, -6.2),
            (16.0, -10.4),
            (24.8, -15.2),
            (30.2, -22.2),
            (29.8, -30.4),
            (26.2, -38.4),
        ],
    },
    {
        "idx": 4,
        "role": "outer_r",
        "root": [-9.4, 14.5, -10.5],
        "fan_yaw": +54.0,
        "head_counter_yaw": -20.0,
        "head_pitch_rest": +6.0,
        "curve_yz": [
            (0.0,  0.0),
            (2.2, -7.8),
            (7.5, -14.8),
            (13.2, -21.2),
            (14.6, -28.6),
            (11.6, -36.0),
            (10.0, -42.8),
        ],
    },
]


def build_head_assembly(bones: list, idx: int, head_pivot: list, counter_yaw: float, pitch_rest: float, parent_seg: str):
    """
    Dựng trọn bộ 1 đầu rồng Spirit Hydra (phóng to cục bộ ~1.20x cho nét từng chiếc răng, mắt, sừng, mang quạt):
    - `head_orient_i` (giữ góc bù rest) -> `head_i` (rotation [0,0,0] để Java tracking/animation không bị lệch!)
    - `jaw_i` (hàm dưới tách rời, có răng sắc + khoang miệng/lưỡi sáng + gân sáng mặt dưới hàm)
    - `horn_i` (sừng trán nghiêng ra sau phát sáng xanh băng trong hốc đá)
    - `frill_i_l` / `frill_i_r` (mỗi bên 5 nan gai dài nhô vượt mép + 2 tầng màng quạt alpha-cutout phát sáng tia sét!)
    """
    px, py, pz = head_pivot
    orient_name = f"head_orient_{idx}"
    head_name = f"head_{idx}"
    jaw_name = f"jaw_{idx}"
    horn_name = f"horn_{idx}"
    frill_l_name = f"frill_{idx}_l"
    frill_r_name = f"frill_{idx}_r"

    bones.append({
        "name": orient_name,
        "parent": parent_seg,
        "pivot": [round(px, 3), round(py, 3), round(pz, 3)],
        "rotation": [round(pitch_rest, 2), round(counter_yaw, 2), 0.0],
        "cubes": [],
    })

    # === 1. HỘP SỌ & MÕM TRÊN (`head_i`) — Mõm thuôn dài góc cạnh (17.5 px dài!), không hộp vuông ===
    hc = []
    # Khớp cổ-sọ & sọ sau
    hc.append(make_cube([px - 4.3, py - 2.4, pz - 5.2], [8.6, 6.2, 6.6], "STONE_SCALE",
                        face_overrides={"up": "STONE_DORSAL", "down": "MOUTH_GLOW"}))
    # Gò má giáp góc cạnh hai bên
    for side in (-1, 1):
        cx = px + (3.6 if side > 0 else -5.2)
        hc.append(make_cube([cx, py - 1.8, pz - 7.4], [1.6, 4.4, 7.2], "STONE_DORSAL",
                            rot=[0.0, side * -6.0, side * 8.0], pivot=[px + side * 4.2, py + 0.4, pz - 4.0]))
        # 2 gai phụ ở góc hàm/gáy (biến tấu bản sắc Abyssal Spire!)
        add_chain_rod(hc, [px + side * 4.0, py + 1.8, pz - 1.2], [px + side * 5.8, py + 3.6, pz + 3.4],
                      1.35, 0.35, 2, "DORSAL_SPINE")
        add_chain_rod(hc, [px + side * 4.1, py - 0.8, pz - 1.6], [px + side * 5.6, py - 1.6, pz + 2.4],
                      1.15, 0.30, 2, "DORSAL_SPINE")

    # Vòm trán & đỉnh sọ thuôn dần về trước
    hc.append(make_cube([px - 3.6, py + 2.6, pz - 9.4], [7.2, 2.2, 8.6], "STONE_DORSAL",
                        rot=[7.0, 0.0, 0.0], pivot=[px, py + 3.4, pz - 3.0]))
    # Gờ mày sắc nhô che trên mắt (biến tấu dữ tợn)
    for side in (-1, 1):
        bx = px + (2.3 if side > 0 else -4.4)
        hc.append(make_cube([bx, py + 2.3, pz - 10.6], [2.1, 1.35, 5.4], "STONE_DORSAL",
                            rot=[10.0, side * -12.0, side * -14.0],
                            pivot=[px + side * 3.3, py + 2.8, pz - 7.8]))

    # Mắt xiên dài phát sáng trắng-lam rực rỡ (nhìn cực rõ cả chính diện lẫn nghiêng!)
    for side in (-1, 1):
        ex = px + (3.25 if side > 0 else -4.35)
        hc.append(make_cube([ex, py + 0.85, pz - 10.2], [1.1, 1.55, 3.8], "EYE_GLOW",
                            rot=[12.0, side * -8.0, 0.0],
                            pivot=[px + side * 3.8, py + 1.5, pz - 8.4]))
        # Mí mắt tối mảnh tạo độ xiên dữ tợn
        hc.append(make_cube([ex + side * 0.12, py + 0.35, pz - 10.0], [0.9, 0.55, 3.5], "DARK_JOINT"))

    # Thân mõm giữa thuôn dài góc cạnh
    hc.append(make_cube([px - 3.25, py - 0.5, pz - 13.8], [6.5, 4.1, 9.2], "STONE_SCALE",
                        face_overrides={"up": "STONE_DORSAL", "down": "MOUTH_GLOW"}))
    # Sống mũi giáp góc cạnh nâng dọc sống mõm (biến tấu!)
    hc.append(make_cube([px - 1.7, py + 3.1, pz - 16.4], [3.4, 1.4, 8.4], "STONE_DORSAL",
                        rot=[5.5, 0.0, 0.0], pivot=[px, py + 3.5, pz - 11.0]))
    # Chóp mõm trước thuôn gọn + lỗ mũi giáp nổi
    hc.append(make_cube([px - 2.65, py - 0.35, pz - 18.2], [5.3, 3.45, 5.2], "STONE_SCALE",
                        face_overrides={"up": "STONE_DORSAL", "down": "MOUTH_GLOW"}))
    for side in (-1, 1):
        nx = px + (1.3 if side > 0 else -2.5)
        hc.append(make_cube([nx, py + 2.45, pz - 17.6], [1.2, 1.0, 2.2], "STONE_DORSAL"))

    # Vòm họng phát sáng rực bên trong hàm trên (nhìn rõ khi há miệng!)
    hc.append(make_cube([px - 2.55, py - 0.95, pz - 16.8], [5.1, 1.15, 13.4], "MOUTH_GLOW"))

    # Hàng răng hàm trên sắc nhọn rõ ràng (6 răng mỗi bên + 2 răng nanh dài phía trước!)
    upper_z_list = [-6.4, -8.5, -10.6, -12.7, -14.8, -16.9]
    for side in (-1, 1):
        for k, tz in enumerate(upper_z_list):
            taper_x = 3.05 - k * 0.13
            tx = px + side * taper_x - 0.38
            th = 1.65 if k == 5 else (1.35 if k % 2 == 0 else 1.05)
            hc.append(make_cube([tx, py - 0.35 - th, pz + tz - 0.38], [0.76, th, 0.76], "TOOTH_WHITE"))
    # 2 răng cửa trước mõm trên
    for side in (-1, 1):
        hc.append(make_cube([px + side * 0.95 - 0.36, py - 1.55, pz - 18.0], [0.72, 1.35, 0.72], "TOOTH_WHITE"))

    bones.append({
        "name": head_name,
        "parent": orient_name,
        "pivot": [round(px, 3), round(py, 3), round(pz, 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": hc,
    })

    # === 2. SỪNG TRÁN PHÁT SÁNG XANH BĂNG (`horn_i`) — Nghiêng mạnh ra sau cực sắc nét ===
    hornc = []
    # Ổ đá gốc sừng trên sống trán
    hornc.append(make_cube([px - 1.45, py + 3.2, pz - 13.6], [2.9, 1.9, 3.6], "STONE_DORSAL",
                           rot=[-14.0, 0.0, 0.0], pivot=[px, py + 3.8, pz - 12.2]))
    # Thân sừng chính phát sáng nghiêng ra sau (như ảnh mẫu góc trước & ngang!)
    add_chain_rod(hornc, [px, py + 3.9, pz - 12.4], [px, py + 11.2, pz - 3.6],
                  1.95, 0.48, 4, "HORN_GLOW", overlap=0.35)
    # 2 sừng nhánh phụ mảnh hơn kẹp sát sau trán (biến tấu tăng độ ngầu!)
    for side in (-1, 1):
        add_chain_rod(hornc, [px + side * 2.4, py + 3.4, pz - 5.2], [px + side * 3.8, py + 8.6, pz + 1.8],
                      1.15, 0.28, 3, "HORN_GLOW", overlap=0.28)
    bones.append({
        "name": horn_name,
        "parent": head_name,
        "pivot": [round(px, 3), round(py + 3.8, 3), round(pz - 11.5, 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": hornc,
    })

    # === 3. HÀM DƯỚI TÁCH RỜI (`jaw_i`) — Há rộng mượt, phát sáng khoang miệng + sọc sáng mặt dưới ===
    jaw_pivot = [px, py - 1.45, pz - 3.4]
    jc = []
    # Khung hàm dưới kéo dài (dài 14.5 px khớp mõm trên) — mặt dưới dùng JAW_RIBBED sáng sọc ngang giống ảnh đáy mẫu!
    jc.append(make_cube([px - 3.05, py - 3.55, pz - 13.2], [6.1, 2.25, 10.6], "STONE_SCALE",
                        face_overrides={"up": "MOUTH_GLOW", "down": "JAW_RIBBED"}))
    jc.append(make_cube([px - 2.35, py - 3.35, pz - 17.6], [4.7, 2.05, 5.0], "STONE_SCALE",
                        face_overrides={"up": "MOUTH_GLOW", "down": "JAW_RIBBED"}))
    # Lõi năng lượng sáng rực khoang hàm dưới & lưỡi linh hồn
    jc.append(make_cube([px - 2.2, py - 1.85, pz - 16.6], [4.4, 1.05, 12.4], "MOUTH_GLOW"))
    jc.append(make_cube([px - 1.1, py - 1.25, pz - 14.8], [2.2, 0.65, 9.2], "EYE_GLOW"))
    # 2 gai cằm nhỏ & viền tua sáng dưới cằm
    for side in (-1, 1):
        add_chain_rod(jc, [px + side * 2.5, py - 3.3, pz - 5.2], [px + side * 3.4, py - 4.9, pz - 2.4],
                      0.95, 0.28, 2, "DORSAL_SPINE")
    # Tua sáng nhỏ rũ dưới cằm (giống ảnh nghiêng mẫu!)
    for kz in (-7.2, -10.2, -13.2, -15.8):
        jc.append(make_cube([px - 1.6, py - 4.65, pz + kz - 0.35], [3.2, 1.25, 0.7], "NECK_FRINGE",
                            transparent_edges=("up", "down", "east", "west")))
    # Hàng răng hàm dưới sắc nhọn hướng lên (5 răng mỗi bên + 2 nanh trước!)
    lower_z_list = [-7.2, -9.4, -11.6, -13.8, -16.2]
    for side in (-1, 1):
        for k, tz in enumerate(lower_z_list):
            taper_x = 2.65 - k * 0.14
            tx = px + side * taper_x - 0.35
            th = 1.45 if k == 4 else (1.15 if k % 2 == 0 else 0.92)
            jc.append(make_cube([tx, py - 1.35, pz + tz - 0.35], [0.70, th, 0.70], "TOOTH_WHITE"))
    for side in (-1, 1):
        jc.append(make_cube([px + side * 0.85 - 0.32, py - 1.35, pz - 17.2], [0.64, 1.25, 0.64], "TOOTH_WHITE"))

    bones.append({
        "name": jaw_name,
        "parent": head_name,
        "pivot": [round(jaw_pivot[0], 3), round(jaw_pivot[1], 3), round(jaw_pivot[2], 3)],
        "rotation": [18.0, 0.0, 0.0],  # Há mõm rõ nét khoang miệng sáng + hàng răng trắng sắc!
        "cubes": jc,
    })

    # === 4. MANG QUẠT SAU GÁY (`frill_i_l`, `frill_i_r`) — 5 NAN GAI XÁM ĐEN NHÔ VƯỢT MÉP + MÀNG QUẠT SÁNG 2 TẦNG ===
    for side, fname in ((+1, frill_l_name), (-1, frill_r_name)):
        fpiv = [px + side * 3.85, py + 0.35, pz - 2.3]
        fc = []
        # 5 nan gai quạt tỏa đều góc từ trên (+70°) xuống dưới (-66°), xòe ngang và nghiêng sau (+Z)
        ray_angles_deg = [70.0, 35.0, 0.0, -34.0, -66.0]
        ray_lengths    = [14.6, 16.0, 15.4, 13.8, 11.2]
        mem_lengths    = [11.2, 12.6, 12.2, 10.8,  8.6]  # Nan gai nhô vượt mép màng ~3.2 px cực sắc nét!

        sweep_back = 0.42  # Mặt phẳng quạt nghiêng sau ~24° ôm gọn cổ không va chạm giữa 2 đầu trong
        ray_tips = []
        mem_tips = []
        for ang_d, rlen, mlen in zip(ray_angles_deg, ray_lengths, mem_lengths):
            rad = math.radians(ang_d)
            ux = side * math.cos(rad) * math.cos(sweep_back)
            uy = math.sin(rad)
            uz = math.cos(rad) * math.sin(sweep_back)
            u = np.array([ux, uy, uz], dtype=np.float64)
            u /= np.linalg.norm(u)
            tip = np.array(fpiv) + u * rlen
            mtip = np.array(fpiv) + u * mlen
            ray_tips.append(tip)
            mem_tips.append(mtip)
            add_chain_rod(fc, fpiv, tip, 1.02, 0.36, 3, "FRILL_RAY", overlap=0.24)

        # Căng màng quạt mỏng alpha-cutout fullbright giữa từng cặp nan liền kề (4 ô quạt + 1 tầng gân sáng lồng trong!)
        # Dùng đúng R_true(0, ry_frill, rz_sector) để tấm màng nằm KHÍT 100% giữa hai nan gai!
        # Mặt phẳng quạt có góc xoay quanh Y: ry_plane sao cho trục +X (hoặc -X) nằm dọc mặt phẳng quạt:
        #   Ry(+ry) @ [side, 0, 0] = [side*cos(ry), 0, -side*sin(ry)]
        #   Muốn Z component = +sin(sweep_back) => -side*sin(ry) = +sin(sweep_back) => ry = -side * sweep_back
        ry_plane_deg = -side * math.degrees(sweep_back)
        for k in range(len(ray_angles_deg) - 1):
            a0 = ray_angles_deg[k]
            a1 = ray_angles_deg[k + 1]
            amid = 0.5 * (a0 + a1)
            mlen = 0.5 * (mem_lengths[k] + mem_lengths[k + 1])
            # Chiều cao dây cung (spread) tại mép ngoài của ô quạt
            d_rad = math.radians(abs(a0 - a1))
            chord = 2.0 * mlen * math.sin(d_rad * 0.5) * 1.12
            # Xoay cục bộ trong mặt phẳng quạt bằng Rz(-rz) (trong R_true, Rz(-rz) quay [1,0,0] thành [cos(rz), -sin(rz), 0]):
            # Để +X (hoặc -X) hướng lên góc amid:
            #   side=+1: Rz(-rz)@[1,0,0] = [cos(rz), -sin(rz), 0] => muốn y = +sin(amid) => rz = -amid!
            #   side=-1: Rz(-rz)@[-1,0,0] = [-cos(rz), +sin(rz), 0] => muốn y = +sin(amid) => rz = +amid!
            rz_sec = -side * amid
            # Kiểm định toán học 100% hướng giữa ô quạt trùng khít trục giữa hai nan!
            # Lưu ý thứ tự R_true = Rz(-rz) @ Ry(+ry) @ Rx(-rx) — nếu có cả ry và rz, Rz chạy SAU Ry!
            # Vì vậy để quay trong mặt phẳng đã xoay Y, tính 2 thanh dải mảnh chạy theo trục Y bằng add_chain_rod-style
            # hoặc đặt hộp dọc trục nan rồi xoay đúng ma trận R_true!
            # Kiểm tra bằng hướng trung bình u_mid:
            u0 = mem_tips[k] - np.array(fpiv)
            u1 = mem_tips[k + 1] - np.array(fpiv)
            u_mid = 0.5 * (u0 + u1)
            u_len = float(np.linalg.norm(u_mid))
            u_dir = u_mid / max(1e-5, u_len)
            # Dùng cùng công thức chuẩn xác như add_chain_rod: trục cục bộ +Y hướng theo u_dir!
            #   R_true(rx, ry, 0) @ [0, 1, 0] == u_dir!
            #   Còn trục ngang của tấm màng nằm vuông góc với u_dir -> chọn độ dày mỏng theo Z và rộng theo X (hoặc ngược lại)
            #   kiểm tra xem trục X sau R_true(rx, ry, 0) có vuông góc với mặt quạt không:
            #   R_true(rx, ry, 0) @ [1, 0, 0] = [cos(ry), 0, -sin(ry)] (luôn nằm ngang!)
            #   Nên trục Z cục bộ = R_true @ [0, 0, 1] nằm trong mặt thẳng đứng qua u_dir!
            #   => Để tấm màng rộng theo mặt phẳng quạt (giữa u0 và u1), đặt 3 nan mảnh tỏa đều bên trong mỗi ô quạt
            #      (tổng 3 dải chồng khít không kẽ hở mỗi ô, dày 0.24 theo pháp tuyến quạt, rộng bám kín từ gốc ra mép)!
            p_base = np.array(fpiv, dtype=np.float64)
            # 3 dải tỏa đều từ gốc tới mép (ở tỷ lệ 0.22, 0.50, 0.78 giữa 2 nan) -> phủ kín 100% hình quạt tròn mượt!
            for sub_i, frac in enumerate((0.20, 0.50, 0.80)):
                v_ray = u0 * (1.0 - frac) + u1 * frac
                r_dist = float(np.linalg.norm(v_ray))
                v_u = v_ray / max(1e-5, r_dist)
                horiz = math.hypot(v_u[0], v_u[2])
                a_r = math.atan2(horiz, v_u[1])
                ry_r = math.atan2(v_u[0], v_u[2]) if horiz > 1e-5 else 0.0
                rot_strip = [-math.degrees(a_r), +math.degrees(ry_r), 0.0]

                # Kiểm tra pháp tuyến n_plane = cross(u0, u1) với trục cục bộ X và Z sau rot_strip
                R_s = bedrock_rot_matrix(rot_strip)
                ex_w = R_s @ np.array([1.0, 0.0, 0.0])
                ez_w = R_s @ np.array([0.0, 0.0, 1.0])
                n_fan = np.cross(u0, u1)
                n_fan /= max(1e-5, float(np.linalg.norm(n_fan)))
                # Nếu ex_w song song pháp tuyến quạt hơn ez_w -> làm mỏng theo X, rộng theo Z; ngược lại mỏng theo Z, rộng theo X!
                strip_w = max(1.6, chord * 0.44)
                if abs(float(np.dot(ex_w, n_fan))) >= abs(float(np.dot(ez_w, n_fan))):
                    sx, sz = 0.24, strip_w
                    tedges = ("north", "south", "up", "down")
                else:
                    sx, sz = strip_w, 0.24
                    tedges = ("east", "west", "up", "down")

                # Chia 2 đoạn (gốc sáng trắng-lam + ngoài tia sét khuyết mép) cho độ nét texture cực cao!
                p_in = p_base + v_u * 1.1
                len_main = r_dist - 1.1
                su0 = (k + sub_i * 0.32) / 4.0
                su1 = min(0.99, su0 + 0.26)
                fc.append(make_cube(
                    [p_in[0] - sx * 0.5, p_in[1], p_in[2] - sz * 0.5],
                    [sx, len_main, sz],
                    "FRILL_MEMBRANE",
                    rot=rot_strip,
                    pivot=p_in.tolist(),
                    pad=0,
                    sub_u=(su0, su1),
                    sub_v=(0.02, 0.98),
                    transparent_edges=tedges,
                ))
            # Tầng 2 nhẹ (lõi năng lượng sát gốc mang quạt tăng chiều sâu phát sáng!)
            p_in2 = p_base + u_dir * 0.8
            horiz2 = math.hypot(u_dir[0], u_dir[2])
            rot_in2 = [-math.degrees(math.atan2(horiz2, u_dir[1])),
                       +math.degrees(math.atan2(u_dir[0], u_dir[2])) if horiz2 > 1e-5 else 0.0, 0.0]
            fc.append(make_cube(
                [p_in2[0] - 0.55, p_in2[1], p_in2[2] - 0.55],
                [1.1, u_len * 0.42, 1.1],
                "EYE_GLOW",
                rot=rot_in2,
                pivot=p_in2.tolist(),
            ))

        bones.append({
            "name": fname,
            "parent": head_name,
            "pivot": [round(fpiv[0], 3), round(fpiv[1], 3), round(fpiv[2], 3)],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": fc,
        })


def build_leg_assembly(bones: list, name: str, parent: str, side: int, is_front: bool, hip_pivot: list):
    """
    Dựng 1 chân bò sát khuỵu thấp mở ngang kiểu kỳ đà/rồng đất (khớp gối gập rõ từ góc ngang & trước):
    `leg_xx` (thigh) -> `leg_xx_shin` -> `leg_xx_foot` (chạm đất chính xác Y=0.0, 4 ngón + móng vuốt phát sáng + vòng năng lượng cổ chân!)
    """
    hx, hy, hz = hip_pivot
    # Chân trước gối hơi chếch ra sau-ngang rồi cẳng chân lao về trước; chân sau gối chếch trước-ngang rồi cẳng chân đạp lùi-xuống!
    knee = [hx + side * (7.2 if is_front else 7.0), 9.2, hz + (+4.2 if is_front else -4.6)]
    ankle = [knee[0] + side * 1.4, 2.5, knee[2] + (-4.8 if is_front else +4.4)]

    # 1. Đùi (`leg_fl/fr/bl/br`)
    thigh_c = []
    thigh_c.append(make_cube([hx - 2.7, hy - 2.7, hz - 2.9], [5.4, 5.4, 5.8], "STONE_SCALE",
                             face_overrides={"down": "BELLY_GLOW"}))
    add_chain_rod(thigh_c, [hx, hy, hz], knee, 5.2, 4.4, 3, "STONE_SCALE", overlap=0.65)
    mid_th = 0.5 * (np.array([hx, hy, hz]) + np.array(knee))
    thigh_c.append(make_cube([mid_th[0] - 2.4, mid_th[1] - 2.1, mid_th[2] - 2.5], [4.8, 4.2, 5.0], "STONE_DORSAL"))
    thigh_c.append(make_cube([knee[0] - 2.5, knee[1] - 2.2, knee[2] - 2.5], [5.0, 4.4, 5.0], "STONE_SCALE"))
    # Đai năng lượng xanh băng phát sáng quanh khớp gối/đùi
    add_chain_rod(thigh_c,
                  np.array([hx, hy, hz]) * 0.32 + np.array(knee) * 0.68,
                  np.array([hx, hy, hz]) * 0.10 + np.array(knee) * 0.90,
                  5.05, 4.95, 1, "ANKLE_BAND", overlap=0.25)

    bones.append({
        "name": name,
        "parent": parent,
        "pivot": [round(hx, 3), round(hy, 3), round(hz, 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": thigh_c,
    })

    # 2. Ống chân (`leg_xx_shin`) — vòng năng lượng xanh băng sáng rực quanh cổ chân!
    shin_c = []
    add_chain_rod(shin_c, knee, ankle, 4.2, 3.7, 3, "STONE_SCALE", overlap=0.55)
    band_a = np.array(knee) * 0.45 + np.array(ankle) * 0.55
    band_b = np.array(knee) * 0.20 + np.array(ankle) * 0.80
    add_chain_rod(shin_c, band_a, band_b, 4.55, 4.45, 1, "ANKLE_BAND", overlap=0.25)
    add_chain_rod(shin_c, [knee[0], knee[1] + 0.5, knee[2] + (1.4 if is_front else -1.4)],
                  [knee[0] + side * 1.6, knee[1] + 2.4, knee[2] + (4.0 if is_front else -3.6)], 1.15, 0.3, 2, "DORSAL_SPINE")

    shin_name = f"{name}_shin"
    bones.append({
        "name": shin_name,
        "parent": name,
        "pivot": [round(knee[0], 3), round(knee[1], 3), round(knee[2], 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": shin_c,
    })

    # 3. Bàn chân 4 ngón chạm đất chính xác Y = 0.0 + móng vuốt trắng-lam phát sáng!
    fx, fy, fz = ankle[0], 0.0, ankle[2] - 1.2
    foot_c = []
    foot_c.append(make_cube([fx - 2.7, 0.0, fz - 2.3], [5.4, 2.7, 5.6], "STONE_DORSAL",
                            face_overrides={"down": "DARK_JOINT"}))
    toe_offsets_x = [-1.95, -0.65, +0.65, +1.95]
    toe_yaws = [+18.0, +6.0, -6.0, -18.0]
    for tx_off, tyaw in zip(toe_offsets_x, toe_yaws):
        tpx = fx + tx_off
        tpz = fz - 2.1
        foot_c.append(make_cube([tpx - 0.60, 0.0, tpz - 2.6], [1.20, 1.60, 2.8], "STONE_SCALE",
                                rot=[0.0, tyaw, 0.0], pivot=[tpx, 0.75, tpz]))
        rad = math.radians(tyaw)
        tip_x = tpx - math.sin(rad) * 2.55
        tip_z = tpz - math.cos(rad) * 2.55
        foot_c.append(make_cube([tip_x - 0.54, 0.0, tip_z - 1.75], [1.08, 1.40, 1.95], "CLAW_GLOW",
                                rot=[6.0, tyaw, 0.0], pivot=[tip_x, 0.65, tip_z]))
    foot_c.append(make_cube([fx - 0.55, 0.15, fz + 3.0], [1.1, 1.25, 1.7], "CLAW_GLOW"))

    bones.append({
        "name": f"{name}_foot",
        "parent": shin_name,
        "pivot": [round(ankle[0], 3), round(ankle[1], 3), round(ankle[2], 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": foot_c,
    })


# =============================================================================
# 2. XÂY DỰNG TOÀN BỘ MODEL SPIRIT HYDRA 4 ĐẦU (~1.200 CUBES)
# =============================================================================
def build_hydra_geo() -> tuple[dict, int, dict]:
    bones = []
    bones.append({"name": "root", "pivot": [0.0, 0.0, 0.0], "cubes": []})
    bones.append({"name": "body", "parent": "root", "pivot": [0.0, 14.0, 8.0], "cubes": []})

    # -------------------------------------------------------------------------
    # A. THÂN THẰN LẰN THUÔN DÀI (`chest`, `torso`, `pelvis`) — Dáng bò sát thấp, bụng phát sáng liên tục!
    # -------------------------------------------------------------------------
    # 1. `chest` (Y: 8.6 .. 20.8 — hở gầm tự nhiên cho 4 chân bò sát khuỵu rõ ràng!)
    chest_c = [
        make_cube([-9.6, 9.8, -13.5], [19.2, 9.6, 19.5], "STONE_SCALE",
                  face_overrides={"up": "STONE_DORSAL", "down": "BELLY_GLOW"}),
        make_cube([-8.4, 8.6, -13.2], [16.8, 2.2, 18.8], "BELLY_GLOW"),
        make_cube([-7.8, 10.0, -14.2], [15.6, 5.4, 1.6], "BELLY_GLOW"),
        make_cube([-8.6, 18.8, -12.4], [17.2, 2.0, 17.6], "STONE_DORSAL"),
        make_cube([-6.4, 20.2, -10.8], [12.8, 1.4, 14.4], "STONE_DORSAL"),
        make_cube([9.0, 11.6, -11.5], [1.8, 6.4, 15.2], "STONE_DORSAL"),
        make_cube([-10.8, 11.6, -11.5], [1.8, 6.4, 15.2], "STONE_DORSAL"),
        make_cube([-8.2, 9.8, 4.8], [16.4, 8.8, 3.6], "DARK_JOINT",
                  face_overrides={"down": "BELLY_GLOW"}),
    ]
    for sz in (-9.6, -5.2, -0.8, 3.6):
        add_chain_rod(chest_c, [0.0, 20.8, sz], [0.0, 24.0, sz + 2.2], 1.55, 0.35, 2, "DORSAL_SPINE")
        for side in (-1, 1):
            chest_c.append(make_cube([side * 3.8 - 0.55, 20.2, sz - 0.8], [1.1, 1.5, 2.4], "DORSAL_SPINE"))
    for side in (-1, 1):
        for sz in (-8.5, -3.5, 1.5):
            chest_c.append(make_cube([side * 8.9 - 0.5, 9.2, sz - 1.4], [1.0, 2.4, 2.8], "BELLY_GLOW"))

    bones.append({
        "name": "chest",
        "parent": "body",
        "pivot": [0.0, 14.5, -4.0],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": chest_c,
    })

    # 2. `torso` (Eo thằn lằn thuôn gọn, Y: 8.8 .. 19.8)
    torso_c = [
        make_cube([-7.4, 9.8, 5.5], [14.8, 8.4, 18.0], "STONE_SCALE",
                  face_overrides={"up": "STONE_DORSAL", "down": "BELLY_GLOW"}),
        make_cube([-6.4, 8.7, 5.8], [12.8, 2.0, 17.4], "BELLY_GLOW"),
        make_cube([-6.2, 17.6, 6.4], [12.4, 1.8, 16.2], "STONE_DORSAL"),
        make_cube([-4.8, 18.8, 7.5], [9.6, 1.2, 14.0], "STONE_DORSAL"),
        make_cube([-7.0, 9.8, 22.0], [14.0, 8.2, 3.6], "DARK_JOINT",
                  face_overrides={"down": "BELLY_GLOW"}),
    ]
    for sz in (8.2, 12.8, 17.4, 21.8):
        add_chain_rod(torso_c, [0.0, 19.4, sz], [0.0, 22.4, sz + 2.0], 1.4, 0.32, 2, "DORSAL_SPINE")
        for side in (-1, 1):
            torso_c.append(make_cube([side * 7.2 - 0.45, 9.4, sz - 1.3], [0.9, 2.2, 2.6], "BELLY_GLOW"))

    bones.append({
        "name": "torso",
        "parent": "body",
        "pivot": [0.0, 14.0, 14.0],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": torso_c,
    })

    # 3. `pelvis` (Hông khỏe đỡ 2 chân sau & gốc đuôi vút cao, Y: 9.2 .. 20.4)
    pelvis_c = [
        make_cube([-8.2, 10.2, 22.8], [16.4, 8.8, 16.0], "STONE_SCALE",
                  face_overrides={"up": "STONE_DORSAL", "down": "BELLY_GLOW"}),
        make_cube([-6.8, 9.1, 23.2], [13.6, 2.0, 15.2], "BELLY_GLOW"),
        make_cube([-7.0, 18.4, 23.8], [14.0, 1.8, 14.2], "STONE_DORSAL"),
        make_cube([-6.2, 11.0, 37.2], [12.4, 7.4, 3.6], "DARK_JOINT",
                  face_overrides={"down": "BELLY_GLOW"}),
    ]
    for sz in (26.0, 30.8, 35.4):
        add_chain_rod(pelvis_c, [0.0, 19.6, sz], [0.0, 22.6, sz + 2.0], 1.35, 0.3, 2, "DORSAL_SPINE")
        for side in (-1, 1):
            pelvis_c.append(make_cube([side * 8.0 - 0.45, 9.9, sz - 1.3], [0.9, 2.1, 2.6], "BELLY_GLOW"))

    bones.append({
        "name": "pelvis",
        "parent": "body",
        "pivot": [0.0, 14.5, 30.5],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": pelvis_c,
    })

    # -------------------------------------------------------------------------
    # B. 4 CHÂN BÒ SÁT MỞ NGANG (`leg_fl`, `leg_fr`, `leg_bl`, `leg_br`)
    # -------------------------------------------------------------------------
    build_leg_assembly(bones, "leg_fl", "chest",  +1, True,  [+9.2, 13.4, -5.2])
    build_leg_assembly(bones, "leg_fr", "chest",  -1, True,  [-9.2, 13.4, -5.2])
    build_leg_assembly(bones, "leg_bl", "pelvis", +1, False, [+8.2, 13.8, 31.0])
    build_leg_assembly(bones, "leg_br", "pelvis", -1, False, [-8.2, 13.8, 31.0])

    # -------------------------------------------------------------------------
    # C. ĐUÔI DÀI 7 ĐỐT (`tail_1..7`) — DÙNG add_angled_box_along_pos_z ĐỂ LƯNG ĐÁ LUÔN Ở TRÊN & BỤNG SÁNG LUÔN Ở DƯỚI!
    # -------------------------------------------------------------------------
    tail_pts = [
        (14.5, 38.5),
        (15.2, 48.2),
        (17.8, 57.6),
        (22.5, 66.0),
        (29.2, 72.8),
        (37.2, 77.6),
        (45.8, 80.0),
        (54.2, 80.2),
    ]
    tail_widths  = [11.0, 9.6, 8.2, 7.0, 5.8, 4.6, 3.6]
    tail_heights = [ 7.6, 6.8, 6.0, 5.2, 4.5, 3.8, 3.1]

    prev_tail = "pelvis"
    for ti in range(7):
        y0, z0 = tail_pts[ti]
        y1, z1 = tail_pts[ti + 1]
        p0 = [0.0, y0, z0]
        p1 = [0.0, y1, z1]
        w = tail_widths[ti]
        h = tail_heights[ti]
        tc = []

        # Thân đốt đuôi chính (+Z dọc đuôi -> mặt UP luôn là giáp lưng đá, mặt DOWN luôn là bụng sáng!)
        add_angled_box_along_pos_z(tc, p0, p1, w, h, "STONE_SCALE", pad_start=1.0, pad_end=1.0,
                                   face_overrides={"up": "STONE_DORSAL", "down": "BELLY_GLOW"})
        vec = np.array(p1) - np.array(p0)
        u = vec / np.linalg.norm(vec)
        # Với u = [0, uy, uz] hướng ra +Z & cong lên +Y:
        # u_down (mặt dưới/ngoài cung đuôi) = [0, -uz, +uy]; u_up (sống lưng trên đuôi) = [0, +uz, -uy]!
        u_down = np.array([0.0, -u[2], u[1]])
        u_up = -u_down
        # Dải bụng phát sáng dày nổi chạy dọc MẶT DƯỚI đốt đuôi (cực rõ từ góc ngang & góc dưới, lưng trên tối hoàn toàn!)
        add_angled_box_along_pos_z(tc,
                                   (np.array(p0) + u_down * (h * 0.44)).tolist(),
                                   (np.array(p1) + u_down * (h * 0.44)).tolist(),
                                   w * 0.84, max(1.6, h * 0.34), "BELLY_GLOW",
                                   pad_start=0.7, pad_end=0.7)
        # Khớp lồng thu nhẹ đầu đốt (overlapping joint cap)
        add_angled_box_along_pos_z(tc,
                                   (np.array(p0) - u * 0.9).tolist(),
                                   (np.array(p0) + u * 2.2).tolist(),
                                   w * 0.90, h * 0.88, "DARK_JOINT",
                                   face_overrides={"down": "BELLY_GLOW"})
        # Gai sống lưng đá nhỏ, sắc, thấp trên sống đuôi trên!
        mid_t = 0.5 * (np.array(p0) + np.array(p1))
        spine_base = mid_t + u_up * (h * 0.45)
        spine_tip = spine_base + u_up * (2.8 - ti * 0.18) + u * 1.6
        add_chain_rod(tc, spine_base, spine_tip, max(0.70, 1.40 - ti * 0.1), 0.28, 2, "DORSAL_SPINE")

        if ti == 6:
            end_p = np.array(p1)
            tc.append(make_cube([end_p[0] - 2.1, end_p[1] - 1.6, end_p[2] - 1.8], [4.2, 3.2, 3.6], "STONE_DORSAL"))
            for side in (-1, 1):
                prong_mid = end_p + u * 4.5 + np.array([side * 2.4, 0.0, 0.0]) + u_up * 0.4
                prong_tip = end_p + u * 10.2 + np.array([side * 3.8, 0.0, 0.0]) + u_up * 1.2
                add_chain_rod(tc, end_p, prong_mid, 1.75, 1.35, 2, "TAIL_CRYSTAL", overlap=0.35)
                add_chain_rod(tc, prong_mid, prong_tip, 1.30, 0.30, 3, "TAIL_CRYSTAL", overlap=0.3)
            add_chain_rod(tc, end_p, end_p + u * 5.2, 1.45, 0.35, 2, "EYE_GLOW", overlap=0.25)

        tname = f"tail_{ti + 1}"
        bones.append({
            "name": tname,
            "parent": prev_tail,
            "pivot": [round(p0[0], 3), round(p0[1], 3), round(p0[2], 3)],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": tc,
        })
        prev_tail = tname

    # -------------------------------------------------------------------------
    # D. 4 CỔ DÀI MẢNH UỐN CHỮ S (`neck_1..4`, mỗi cổ 6 đốt `neck_i_seg_1..6`) + 4 MỎM CỤT (`stump_1..4`) + 4 ĐẦU
    # -------------------------------------------------------------------------
    computed_head_world_pivots = []
    computed_neck_root_pivots = []

    for spec in NECK_SPECS:
        idx = spec["idx"]
        rx, ry, rz = spec["root"]
        fan_yaw = spec["fan_yaw"]
        cyaw = spec["head_counter_yaw"]
        crest = spec["head_pitch_rest"]
        curve = spec["curve_yz"]

        computed_neck_root_pivots.append([round(rx, 2), round(ry, 2), round(rz, 2)])

        # 1. Mỏm cổ cụt `stump_i` (hiện lên khi đầu bị chặt, ẩn khi đầu còn sống!)
        sc = []
        # Vòng cổ đá rách nham nhở tại gốc ngực
        sc.append(make_cube([rx - 3.6, ry - 3.4, rz - 4.0], [7.2, 6.8, 5.2], "STONE_DORSAL",
                            face_overrides={"north": "STUMP_CORE", "down": "BELLY_GLOW"}))
        # Lõi năng lượng linh hồn rỉ ra ở vết chặt
        sc.append(make_cube([rx - 2.4, ry - 2.2, rz - 5.2], [4.8, 4.4, 2.2], "STUMP_CORE"))
        # 4 mảnh vảy đá rách & 3 tia hồn nhô ra quanh miệng vết chặt
        for ang_deg in (35, 125, 215, 305):
            ar = math.radians(ang_deg)
            p_b = [rx + math.cos(ar) * 2.8, ry + math.sin(ar) * 2.6, rz - 3.6]
            p_t = [rx + math.cos(ar) * 3.8, ry + math.sin(ar) * 3.5, rz - 6.2]
            add_chain_rod(sc, p_b, p_t, 1.1, 0.25, 2, "STONE_DORSAL")
        for ang_deg in (0, 120, 240):
            ar = math.radians(ang_deg)
            p_b = [rx + math.cos(ar) * 1.1, ry + math.sin(ar) * 1.1, rz - 4.6]
            p_t = [rx + math.cos(ar) * 1.8, ry + math.sin(ar) * 1.8, rz - 7.8]
            add_chain_rod(sc, p_b, p_t, 0.9, 0.2, 2, "HORN_GLOW")

        bones.append({
            "name": f"stump_{idx}",
            "parent": "chest",
            "pivot": [round(rx, 3), round(ry, 3), round(rz, 3)],
            "rotation": [0.0, round(fan_yaw, 2), 0.0],
            "cubes": sc,
        })

        # 2. Bone gốc cổ `neck_i` (giữ góc quạt `fan_yaw` -> ẩn `neck_i` sẽ ẩn trọn cả 6 đốt + đầu + mang quạt!)
        bones.append({
            "name": f"neck_{idx}",
            "parent": "chest",
            "pivot": [round(rx, 3), round(ry, 3), round(rz, 3)],
            "rotation": [0.0, round(fan_yaw, 2), 0.0],
            "cubes": [],
        })

        # 3. 6 đốt cổ `neck_i_seg_1..6` nối khớp lồng khít nhau trong mặt phẳng cục bộ YZ của `neck_i`!
        seg_widths  = [7.0, 6.7, 6.4, 6.1, 5.8, 5.5]  # Tăng nhẹ độ dày đốt cổ cho sắc nét!
        seg_heights = [6.6, 6.3, 6.0, 5.7, 5.5, 5.2]
        prev_parent = f"neck_{idx}"

        for k in range(6):
            dy0, dz0 = curve[k]
            dy1, dz1 = curve[k + 1]
            p0 = np.array([rx, ry + dy0, rz + dz0], dtype=np.float64)
            p1 = np.array([rx, ry + dy1, rz + dz1], dtype=np.float64)
            vec = p1 - p0
            L = float(np.linalg.norm(vec))
            u = vec / L
            # Pháp tuyến mặt dưới cổ trong mặt phẳng YZ (vì u hướng -Z: u = [0, uy, uz] với uz < 0):
            # Khi u = [0, 0, -1], u_down = [0, -1, 0] => u_down = [0, +uz, -uy]!
            u_down = np.array([0.0, u[2], -u[1]])
            u_up = -u_down

            w = seg_widths[k]
            h = seg_heights[k]
            nc = []

            # Lõi đốt cổ chính (lưng & sườn vảy đá xám than loang lổ, mặt dưới sáng xanh băng!)
            add_angled_box_along(nc, p0.tolist(), p1.tolist(), w, h, "STONE_SCALE",
                                 pad_start=0.85, pad_end=0.85,
                                 face_overrides={"up": "STONE_DORSAL", "down": "BELLY_GLOW"})
            # Khớp lồng thu nhẹ đầu-cuối (overlapping joint cap) -> xoay cổ cực gắt cũng liền khối!
            add_angled_box_along(nc, (p0 - u * 1.15).tolist(), (p0 + u * 1.85).tolist(),
                                 w * 0.90, h * 0.88, "DARK_JOINT",
                                 face_overrides={"down": "BELLY_GLOW"})
            add_angled_box_along(nc, (p1 - u * 1.55).tolist(), (p1 + u * 1.15).tolist(),
                                 w * 0.88, h * 0.86, "DARK_JOINT",
                                 face_overrides={"down": "BELLY_GLOW"})
            # Tấm vảy lưng giáp nổi trên sống cổ
            add_angled_box_along(nc,
                                 (p0 + u_up * (h * 0.42)).tolist(),
                                 (p1 + u_up * (h * 0.42)).tolist(),
                                 w * 0.78, 1.35, "STONE_DORSAL",
                                 pad_start=0.25, pad_end=0.25)
            # Dải bụng cổ phát sáng xanh hồn rực rỡ chạy liên tục suốt chiều dài đốt!
            add_angled_box_along(nc,
                                 (p0 + u_down * (h * 0.44)).tolist(),
                                 (p1 + u_down * (h * 0.44)).tolist(),
                                 w * 0.84, 1.65, "BELLY_GLOW",
                                 pad_start=0.75, pad_end=0.75)

            # 2 dải tua năng lượng xanh băng rũ dọc hai viền sườn dưới cổ (đúng chất ảnh mẫu!)
            for side in (-1, 1):
                fringe_p0 = p0 + u_down * (h * 0.56) + np.array([side * (w * 0.44), 0.0, 0.0])
                fringe_p1 = p1 + u_down * (h * 0.56) + np.array([side * (w * 0.44), 0.0, 0.0])
                add_angled_box_along(nc, fringe_p0.tolist(), fringe_p1.tolist(),
                                     0.32, 3.2, "NECK_FRINGE",
                                     pad_start=0.2, pad_end=0.2,
                                     transparent_edges=("north", "south", "up", "down"))
            # 2 gai năng lượng băng nhỏ rũ ở chính giữa bụng đốt cổ tạo chiều sâu 3D
            for f_t in (0.32, 0.72):
                gp = p0 + vec * f_t + u_down * (h * 0.50)
                gtip = gp + u_down * 2.5 - u * 0.8
                add_chain_rod(nc, gp, gtip, 0.82, 0.22, 2, "HORN_GLOW", overlap=0.18)

            # Gai sống cổ đá nhỏ thấp trên đỉnh đốt
            sp_b = p0 + vec * 0.5 + u_up * (h * 0.50)
            sp_t = sp_b + u_up * 1.9 - u * 1.2
            add_chain_rod(nc, sp_b, sp_t, 0.95, 0.26, 2, "DORSAL_SPINE")

            seg_name = f"neck_{idx}_seg_{k + 1}"
            bones.append({
                "name": seg_name,
                "parent": prev_parent,
                "pivot": [round(float(p0[0]), 3), round(float(p0[1]), 3), round(float(p0[2]), 3)],
                "rotation": [0.0, 0.0, 0.0],
                "cubes": nc,
            })
            prev_parent = seg_name

        # 4. Đầu rồng gắn vào đầu mút `neck_i_seg_6`
        end_dy, end_dz = curve[-1]
        head_local_piv = [rx, ry + end_dy, rz + end_dz]
        build_head_assembly(bones, idx, head_local_piv, cyaw, crest, prev_parent)

        # Tính tọa độ thế giới (model space) chính xác của tâm miệng đầu `head_i` để đồng bộ với Java!
        R_fan = bedrock_rot_matrix([0.0, fan_yaw, 0.0])
        R_ori = bedrock_rot_matrix([crest, cyaw, 0.0])
        root_v = np.array([rx, ry, rz], dtype=np.float64)
        hp_v = np.array(head_local_piv, dtype=np.float64)
        mouth_local_offset = np.array([0.0, -1.2, -13.0], dtype=np.float64)  # Tâm hàm trước
        mouth_in_neck = hp_v + R_ori @ mouth_local_offset
        mouth_world = root_v + R_fan @ (mouth_in_neck - root_v)
        computed_head_world_pivots.append([round(float(mouth_world[0]), 2),
                                           round(float(mouth_world[1]), 2),
                                           round(float(mouth_world[2]), 2)])

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.spirit_hydra",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 22,
                    "visible_bounds_height": 14,
                    "visible_bounds_offset": [0, 4.5, 0],
                },
                "bones": bones,
            }
        ],
    }

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out_path = ASSETS / "geo" / "entity" / "spirit_hydra.geo.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)

    meta = {
        "bones": len(bones),
        "cubes": total_cubes,
        "head_mouth_pivots": computed_head_world_pivots,
        "neck_root_pivots": computed_neck_root_pivots,
    }
    print(f"GEO_OK: {out_path} -> {len(bones)} bones, {total_cubes} cubes")
    print(f"HEAD_MOUTH_PIVOTS (model space): {computed_head_world_pivots}")
    print(f"NECK_ROOT_PIVOTS  (model space): {computed_neck_root_pivots}")
    return geo, total_cubes, meta


# =============================================================================
# 3. SINH TEXTURE 512x512 (NEAREST PIXEL-ART ĐÁ LOANG LỔ + GLOWMASK FULLBRIGHT + ENRAGED PHASE 2)
# =============================================================================
def _hash2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def paint_region_stone_scales(diff, glow, rect, enraged: bool, dark_mode: bool = False):
    """Vẽ kết cấu đá/vảy xám than–tro đen loang lổ (#1D2026, #2F3540, #475060, #647082) mảng khối sắc nét kiểu Minecraft voxel."""
    x0, y0, w, h = rect
    # Bảng màu đá xám than–tro đen có độ tương phản loang lổ rõ nét như mẫu (bỏ sạch vân lưới caro xanh đen cũ!)
    palette = [
        (29, 32, 38),    # #1D2026 — khe vảy đá tối
        (47, 53, 64),    # #2F3540 — đá than nền
        (76, 86, 102),   # #4C5666 — vảy xám tro trung tính
        (114, 126, 146), # #727E92 — mảng vảy đá sáng cạnh (tạo độ loang rõ ràng!)
    ]
    if dark_mode:
        palette = [(max(18, r - 8), max(20, g - 8), max(24, b - 6)) for (r, g, b) in palette]

    # Ô voxel đá 8x8 sắc nét (đậm chất Minecraft, KHÔNG hạt nhiễu li ti)
    cell = 8
    gx_n = w // cell
    gy_n = h // cell
    for gy in range(gy_n):
        for gx in range(gx_n):
            r1 = _hash2(gx, gy, 17 if not dark_mode else 37)
            r2 = _hash2(gx // 2, gy // 2, 79)
            val = 0.62 * r1 + 0.38 * r2

            if val < 0.24:
                col = palette[0]
            elif val < 0.52:
                col = palette[1]
            elif val < 0.78:
                col = palette[2]
            else:
                col = palette[3]

            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    px, py = px0 + dx, py0 + dy
                    # Đổ bóng AO nhẹ ở rìa từng viên đá & viền khối
                    ao = 1.0
                    if dx == 0 or dy == 0:
                        ao *= 0.88
                    elif dx == cell - 1 and dy == cell - 1:
                        ao *= 1.08
                    if (px - x0) < 4 or (x0 + w - 1 - px) < 4 or (py - y0) < 4 or (y0 + h - 1 - py) < 4:
                        ao *= 0.80
                    rr = int(np.clip(col[0] * ao, 16, 168))
                    gg = int(np.clip(col[1] * ao, 18, 178))
                    bb = int(np.clip(col[2] * ao, 22, 198))
                    diff[py, px] = (rr, gg, bb, 255)

    # Phase 2 Enraged: vẽ các ĐƯỜNG NỨT SÁNG TIA SÉT rõ nét (trắng-xanh điện & tím lạnh) lan qua vảy lưng/vai/sườn!
    if enraged:
        cracks = [
            (0.15, 0.08, 0.82, 0.52, False),
            (0.75, 0.14, 0.22, 0.86, True),
            (0.10, 0.62, 0.90, 0.78, False),
            (0.45, 0.06, 0.58, 0.94, True),
        ]
        for (fx0, fy0, fx1, fy1, is_violet) in cracks:
            steps = 36
            c_core = (242, 255, 255, 255)
            c_edge = (182, 108, 255, 255) if is_violet else (72, 240, 255, 255)
            for s in range(steps):
                t = s / float(steps - 1)
                jag_x = (math.sin(t * 14.0 + fx0 * 10.0) * 0.06)
                jag_y = (math.cos(t * 12.0 + fy0 * 10.0) * 0.05)
                cx = int(np.clip((fx0 + (fx1 - fx0) * t + jag_x) * (w - 6) + 3, 3, w - 4))
                cy = int(np.clip((fy0 + (fy1 - fy0) * t + jag_y) * (h - 6) + 3, 3, h - 4))
                # Nét nứt 3px rõ nét (lõi trắng-lam + viền xanh điện / tím lạnh)
                for oy in range(-1, 2):
                    for ox in range(-1, 2):
                        cc = c_core if (ox == 0 and oy == 0) else c_edge
                        diff[y0 + cy + oy, x0 + cx + ox] = cc
                        glow[y0 + cy + oy, x0 + cx + ox] = cc


def paint_region_belly_glow(diff, glow, rect, enraged: bool):
    """Dải bụng phát sáng liên tục: lõi trắng-lam #E8FFFF -> xanh điện #4DF4FF -> viền xanh lam #0EA5E9."""
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            nx = abs((x + 0.5) / w * 2.0 - 1.0)  # 0 ở giữa, 1 ở mép
            # Nhịp đốt vảy bụng ngang mảnh (cảm giác giáp bụng rồng phát sáng)
            rib = 1.0 if ((y // 4) % 4 != 0) else 0.78
            noise = (_hash2(x // 4, y // 4, 211) - 0.5) * 0.14
            t = np.clip(nx + noise, 0.0, 1.0)

            if not enraged:
                if t < 0.36:
                    c = (232, 255, 255)  # #E8FFFF lõi trắng-lam chói
                elif t < 0.72:
                    c = (77, 244, 255)   # #4DF4FF xanh điện
                else:
                    c = (14, 165, 233)   # #0EA5E9 viền xanh lam
            else:
                if t < 0.42:
                    c = (242, 255, 255)  # Trắng-xanh điện cực gắt
                elif t < 0.74:
                    c = (96, 235, 255)
                else:
                    c = (164, 92, 255)   # Viền tím lạnh cuồng bạo!

            rr = int(np.clip(c[0] * rib, 0, 255))
            gg = int(np.clip(c[1] * rib, 0, 255))
            bb = int(np.clip(c[2] * rib, 0, 255))
            diff[y0 + y, x0 + x] = (rr, gg, bb, 255)
            glow[y0 + y, x0 + x] = (rr, gg, bb, 255)


def paint_region_frill_membrane(diff, glow, rect, enraged: bool):
    """
    Màng mang quạt alpha-cutout sắc nét (20-30% biến tấu):
    - Đường gân sáng tia sét chạy dọc từ gốc (v=1) ra mép ngoài (v=0)
    - Khuyết mép sắc hình tia sét/vảy linh hồn ở rìa ngoài (alpha=0 ở góc khuyết!)
    - Vùng cuối dưới góc phải (x0+w-3..x0+w-1, y0+h-3..y0+h-1) luôn trong suốt alpha=0 cho cạnh mỏng!
    """
    x0, y0, w, h = rect
    # Xóa sạch nền trong suốt trước
    diff[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    glow[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)

    cell = 4
    for y in range(0, h, cell):
        v_norm = y / float(h)  # 0 = mép ngoài đỉnh quạt, 1 = gốc sát đầu
        for x in range(0, w, cell):
            u_norm = x / float(w)
            # Khuyết mép ngoài sắc hình răng cưa tia sét (3 đỉnh nhọn trên mỗi ô quạt)
            saw = abs(((u_norm * 4.0) % 1.0) - 0.5) * 2.0  # 0 ở giữa rãnh, 1 ở đỉnh
            jag = 0.22 * (1.0 - saw) + 0.07 * _hash2(x // cell, 0, 311)
            # Đảm bảo góc dưới phải hoàn toàn trong suốt cho transparent_edges
            if x >= w - 8 and y >= h - 8:
                continue
            if v_norm < jag:
                continue  # Phần khuyết mép trong suốt (alpha=0)!

            # Lỗ tia năng lượng nhỏ tinh tế gần mép ngoài (cảm giác màng linh hồn sắc bén)
            if 0.22 < v_norm < 0.45 and _hash2(x // cell, y // cell, 389) > 0.91:
                continue

            # Đường gân tia sét sáng chói dọc & zic-zac
            vein_dist = abs(((u_norm * 4.0 + 0.12 * math.sin(v_norm * 14.0)) % 1.0) - 0.5)
            is_vein = vein_dist < 0.11 or v_norm > 0.72
            is_rim = v_norm < jag + 0.08

            if not enraged:
                if is_vein:
                    col = (232, 255, 255)  # Trắng-lam rực rỡ
                elif is_rim:
                    col = (22, 168, 236)   # Viền xanh lam sắc
                else:
                    # Chuyển dần từ trắng-xanh ở gốc -> xanh điện ở giữa -> lam ở ngoài
                    col = (95, 246, 255) if v_norm > 0.45 else (44, 212, 248)
            else:
                if is_vein:
                    col = (246, 255, 255)
                elif is_rim:
                    col = (182, 102, 255)  # Viền tím lạnh sắc chói!
                else:
                    col = (130, 245, 255) if v_norm > 0.42 else (95, 195, 255)

            for dy in range(cell):
                for dx in range(cell):
                    if y + dy < h and x + dx < w:
                        diff[y0 + y + dy, x0 + x + dx] = (col[0], col[1], col[2], 255)
                        glow[y0 + y + dy, x0 + x + dx] = (col[0], col[1], col[2], 255)


def paint_region_neck_fringe(diff, glow, rect, enraged: bool):
    """Tua/gai năng lượng xanh băng rũ dọc mặt dưới & sườn cổ (alpha-cutout hình rặng gai tinh thể sắc nét)."""
    x0, y0, w, h = rect
    diff[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    glow[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    cell = 4
    for x in range(0, w, cell):
        col_idx = x // cell
        # Mỗi cột có độ dài gai rũ khác nhau (dài xen ngắn kiểu gai băng năng lượng mẫu!)
        spike_len = 0.25 + 0.68 * (0.5 + 0.5 * math.sin(col_idx * 1.45)) * (0.7 + 0.3 * _hash2(col_idx, 5, 419))
        for y in range(0, h, cell):
            if x >= w - 8 and y >= h - 8:
                continue
            v_norm = y / float(h)  # 0 = gốc gắn cổ (trên), 1 = chóp tua rũ (dưới)
            if v_norm > spike_len:
                continue
            if v_norm < 0.26:
                col = (232, 255, 255) if not enraged else (245, 255, 255)
            elif v_norm < 0.60:
                col = (77, 244, 255) if not enraged else (110, 238, 255)
            else:
                col = (18, 175, 238) if not enraged else (175, 100, 255)
            for dy in range(cell):
                for dx in range(cell):
                    if y + dy < h and x + dx < w:
                        diff[y0 + y + dy, x0 + x + dx] = (col[0], col[1], col[2], 255)
                        glow[y0 + y + dy, x0 + x + dx] = (col[0], col[1], col[2], 255)


def paint_region_jaw_ribbed(diff, glow, rect, enraged: bool):
    """Mặt dưới hàm dưới: vỏ đá đen xen kẽ các khe năng lượng ngang phát sáng xanh băng (khớp ảnh đáy mẫu!)."""
    paint_region_stone_scales(diff, glow, rect, enraged, dark_mode=True)
    x0, y0, w, h = rect
    for y in range(h):
        # 5 thanh ngang sáng phát sáng dọc chiều dài hàm dưới
        band = (y // 12) % 2 == 0 and (y % 12) >= 3 and (y % 12) <= 9
        for x in range(w):
            nx = abs((x + 0.5) / w * 2.0 - 1.0)
            if band and nx < 0.72:
                c = (232, 255, 255) if nx < 0.35 else ((77, 244, 255) if not enraged else (140, 230, 255))
                diff[y0 + y, x0 + x] = (c[0], c[1], c[2], 255)
                glow[y0 + y, x0 + x] = (c[0], c[1], c[2], 255)


def paint_region_solid_glow(diff, glow, rect, core_rgb, edge_rgb, glow_strength=1.0, vertical_grad=False):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            if vertical_grad:
                t = y / max(1.0, h - 1.0)
            else:
                dx = abs((x + 0.5) / w * 2.0 - 1.0)
                dy = abs((y + 0.5) / h * 2.0 - 1.0)
                t = np.clip(max(dx, dy) * 0.9, 0.0, 1.0)
            jitter = (_hash2(x // 4, y // 4, 701) - 0.5) * 0.12
            f = np.clip(t + jitter, 0.0, 1.0)
            r = int(np.clip(core_rgb[0] * (1 - f) + edge_rgb[0] * f, 0, 255))
            g = int(np.clip(core_rgb[1] * (1 - f) + edge_rgb[1] * f, 0, 255))
            b = int(np.clip(core_rgb[2] * (1 - f) + edge_rgb[2] * f, 0, 255))
            diff[y0 + y, x0 + x] = (r, g, b, 255)
            if glow_strength > 0.01:
                glow[y0 + y, x0 + x] = (
                    int(r * glow_strength), int(g * glow_strength), int(b * glow_strength), 255
                )


def paint_region_dorsal_spine(diff, glow, rect, enraged: bool):
    """Gai sống lưng đá sắc tối màu, chỉ có khe sáng mảnh ở gốc (v > 0.76)."""
    paint_region_stone_scales(diff, glow, rect, enraged, dark_mode=True)
    x0, y0, w, h = rect
    for y in range(h):
        v = y / max(1.0, h - 1.0)
        if v > 0.74:
            for x in range(w):
                if abs((x / w) - 0.5) < 0.34:
                    c = (85, 240, 255) if not enraged else (175, 115, 255)
                    diff[y0 + y, x0 + x] = (c[0], c[1], c[2], 255)
                    glow[y0 + y, x0 + x] = (c[0], c[1], c[2], 255)


def paint_region_stump(diff, glow, rect, enraged: bool):
    """Vết chặt cổ: viền đá than rách ngoài + lõi linh hồn xanh/tím cháy rực ở tâm."""
    paint_region_stone_scales(diff, glow, rect, enraged, dark_mode=True)
    x0, y0, w, h = rect
    cx, cy = w * 0.5, h * 0.5
    for y in range(h):
        for x in range(w):
            dist = math.hypot((x - cx) / (w * 0.42), (y - cy) / (h * 0.42))
            noise = (_hash2(x // 4, y // 4, 823) - 0.5) * 0.25
            d = dist + noise
            if d < 0.48:
                c = (232, 255, 255) if not enraged else (245, 225, 255)
                diff[y0 + y, x0 + x] = (*c, 255)
                glow[y0 + y, x0 + x] = (*c, 255)
            elif d < 0.80:
                c = (52, 238, 255) if not enraged else (195, 90, 255)
                diff[y0 + y, x0 + x] = (*c, 255)
                glow[y0 + y, x0 + x] = (*c, 255)


def build_textures() -> None:
    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)

    for enraged, base_name in ((False, "spirit_hydra"), (True, "spirit_hydra_enraged")):
        diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
        glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

        paint_region_stone_scales(diff, glow, MAT["STONE_SCALE"], enraged, dark_mode=False)
        paint_region_stone_scales(diff, glow, MAT["STONE_DORSAL"], enraged, dark_mode=True)
        paint_region_belly_glow(diff, glow, MAT["BELLY_GLOW"], enraged)
        paint_region_frill_membrane(diff, glow, MAT["FRILL_MEMBRANE"], enraged)
        paint_region_neck_fringe(diff, glow, MAT["NECK_FRINGE"], enraged)
        paint_region_jaw_ribbed(diff, glow, MAT["JAW_RIBBED"], enraged)
        paint_region_dorsal_spine(diff, glow, MAT["DORSAL_SPINE"], enraged)
        paint_region_stump(diff, glow, MAT["STUMP_CORE"], enraged)

        # Khoang miệng phát sáng rực
        paint_region_solid_glow(
            diff, glow, MAT["MOUTH_GLOW"],
            (238, 255, 255) if not enraged else (248, 255, 255),
            (32, 225, 255) if not enraged else (160, 105, 255),
            glow_strength=1.0,
        )
        # Nan gai mang quạt xám đen lạnh sắc cạnh
        paint_region_solid_glow(
            diff, glow, MAT["FRILL_RAY"],
            (92, 104, 118), (32, 36, 44), glow_strength=0.0, vertical_grad=True
        )
        # Sừng trán tinh thể phát sáng (gốc xanh lam -> mũi trắng-lam chói)
        paint_region_solid_glow(
            diff, glow, MAT["HORN_GLOW"],
            (236, 255, 255) if not enraged else (250, 255, 255),
            (20, 180, 245) if not enraged else (165, 92, 255),
            glow_strength=1.0, vertical_grad=True
        )
        # Mắt xiên trắng-lam chói lóa
        paint_region_solid_glow(
            diff, glow, MAT["EYE_GLOW"],
            (250, 255, 255), (96, 246, 255) if not enraged else (195, 130, 255),
            glow_strength=1.0
        )
        # Răng trắng ngà sắc nét (ánh lam rất nhẹ, không tự glow lóa mất hình chiếc răng)
        paint_region_solid_glow(
            diff, glow, MAT["TOOTH_WHITE"],
            (248, 252, 255), (195, 220, 232), glow_strength=0.32, vertical_grad=True
        )
        # Vòng năng lượng cổ chân / khớp gối
        paint_region_solid_glow(
            diff, glow, MAT["ANKLE_BAND"],
            (235, 255, 255) if not enraged else (246, 255, 255),
            (30, 210, 250) if not enraged else (160, 96, 255),
            glow_strength=1.0
        )
        # Móng vuốt phát sáng trắng-lam
        paint_region_solid_glow(
            diff, glow, MAT["CLAW_GLOW"],
            (245, 255, 255), (64, 232, 255) if not enraged else (175, 110, 255),
            glow_strength=0.95, vertical_grad=True
        )
        # Tinh thể chóp đuôi chẻ đôi
        paint_region_solid_glow(
            diff, glow, MAT["TAIL_CRYSTAL"],
            (240, 255, 255), (24, 196, 248) if not enraged else (172, 95, 255),
            glow_strength=1.0, vertical_grad=True
        )
        # Khớp lồng tối sâu
        paint_region_solid_glow(
            diff, glow, MAT["DARK_JOINT"],
            (38, 44, 54), (20, 23, 28), glow_strength=0.0
        )

        Image.fromarray(diff, "RGBA").save(tex_dir / f"{base_name}.png")
        Image.fromarray(glow, "RGBA").save(tex_dir / f"{base_name}_glowmask.png")
    print(f"TEX_OK: 4 textures ({TEX_W}x{TEX_H} RGBA + Glowmask P1/P2) saved to {tex_dir}")


# =============================================================================
# 4. SINH 16 ANIMATION MƯỢT (CATMULLROM / EASE-IN-OUT) ĐỒNG BỘ 1:1 VỚI HydraAttackTiming.java
# =============================================================================
def kf(vec, easing="easeInOutSine"):
    return {"vector": [round(float(v), 2) for v in vec], "easing": easing}


def tkey(ticks: int) -> str:
    return f"{ticks / 20.0:.2f}"


def add_rot(bones_anim: dict, bone: str, tick: int, vec, easing="easeInOutSine"):
    bones_anim.setdefault(bone, {}).setdefault("rotation", {})[tkey(tick)] = kf(vec, easing)


def add_pos(bones_anim: dict, bone: str, tick: int, vec, easing="easeInOutSine"):
    bones_anim.setdefault(bone, {}).setdefault("position", {})[tkey(tick)] = kf(vec, easing)


def add_scale(bones_anim: dict, bone: str, tick: int, vec, easing="easeInOutSine"):
    bones_anim.setdefault(bone, {}).setdefault("scale", {})[tkey(tick)] = kf(vec, easing)


def neck_pose(bones_anim: dict, idx: int, tick: int, seg_pitches, seg_yaws=None,
              jaw_open=0.0, frill_flare=0.0, easing="easeInOutSine"):
    """Đặt tư thế đồng bộ mượt cho 6 đốt cổ + đầu + hàm + 2 mang quạt của đầu `idx` (1..4)."""
    if seg_yaws is None:
        seg_yaws = [0.0] * 6
    for k in range(6):
        add_rot(bones_anim, f"neck_{idx}_seg_{k + 1}", tick, [seg_pitches[k], seg_yaws[k], 0.0], easing)
    if abs(jaw_open) > 1e-4 or tick == 0:
        add_rot(bones_anim, f"jaw_{idx}", tick, [jaw_open, 0.0, 0.0], easing)
    # Trong R_true: frill_l (+X) xòe rộng về trước khi rotY > 0; frill_r (-X) xòe rộng về trước khi rotY < 0!
    add_rot(bones_anim, f"frill_{idx}_l", tick, [0.0, +frill_flare, 0.0], easing)
    add_rot(bones_anim, f"frill_{idx}_r", tick, [0.0, -frill_flare, 0.0], easing)


def build_animations() -> dict:
    anims = {}

    # -------------------------------------------------------------------------
    # 1. IDLE (4.00s = 80 ticks, loop) — 4 cổ uốn sóng sin lệch pha độc lập + thở + mang quạt mở-khép + đuôi đung đưa
    # -------------------------------------------------------------------------
    b = {}
    steps = [0, 10, 20, 30, 40, 50, 60, 70, 80]
    for t in steps:
        ph = (t / 80.0) * math.tau
        breath = math.sin(ph)
        add_pos(b, "body", t, [0.0, breath * 0.45, 0.0], "catmullrom")
        add_rot(b, "chest", t, [breath * 2.2, 0.0, 0.0], "catmullrom")
        add_rot(b, "torso", t, [-breath * 1.4, math.sin(ph) * 2.5, 0.0], "catmullrom")
        add_rot(b, "pelvis", t, [-breath * 1.2, -math.sin(ph) * 2.5, 0.0], "catmullrom")
        # Đuôi cong đung đưa mềm dạng sóng chạy
        for ti in range(7):
            wave_y = math.sin(ph - ti * 0.55) * (4.5 + ti * 1.4)
            wave_x = math.cos(ph - ti * 0.45) * (2.2 + ti * 0.6)
            add_rot(b, f"tail_{ti + 1}", t, [wave_x, wave_y, 0.0], "catmullrom")
        # 4 cổ uốn lượn mềm mại theo sóng sin LỆCH PHA ĐỘC LẬP NHAU!
        for i in range(1, 5):
            h_ph = ph + (i - 1) * 1.57
            pitches = [math.sin(h_ph - k * 0.55) * (3.2 + k * 0.6) for k in range(6)]
            yaws    = [math.cos(h_ph - k * 0.60) * (3.5 + k * 0.8) for k in range(6)]
            jaw_b   = 3.0 + math.sin(h_ph * 1.0) * 4.5
            frill_b = 5.0 + math.sin(ph + i * 0.7) * 8.0
            neck_pose(b, i, t, pitches, yaws, jaw_open=jaw_b, frill_flare=frill_b, easing="catmullrom")
    anims["animation.spirit_hydra.idle"] = {"loop": True, "animation_length": 4.0, "bones": b}

    # -------------------------------------------------------------------------
    # 2. WALK (1.40s = 28 ticks, loop) — Bò sát 4 chân bước chéo (FL+BR xen FR+BL), hông lắc ngang, 4 cổ giữ thăng bằng
    # -------------------------------------------------------------------------
    b = {}
    for t in (0, 7, 14, 21, 28):
        ph = (t / 28.0) * math.tau
        s = math.sin(ph)
        c = math.cos(ph)
        # Thân lắc ngang mềm kiểu kỳ đà & nhún nhẹ theo nhịp bước
        add_pos(b, "body", t, [0.0, abs(s) * 0.7 - 0.3, 0.0], "catmullrom")
        add_rot(b, "chest", t, [4.0, -s * 7.5, s * 2.5], "catmullrom")
        add_rot(b, "torso", t, [0.0, s * 9.0, 0.0], "catmullrom")
        add_rot(b, "pelvis", t, [0.0, s * 8.5, -s * 2.5], "catmullrom")

        # Cặp chân chéo 1: FL + BR; Cặp chân chéo 2: FR + BL (lệch pha PI)
        leg_defs = [
            ("leg_fl", +1, ph),
            ("leg_br", -1, ph),
            ("leg_fr", -1, ph + math.pi),
            ("leg_bl", +1, ph + math.pi),
        ]
        for lname, side, lph in leg_defs:
            sw = math.sin(lph)       # Đưa chân trước/sau
            lift = max(0.0, math.cos(lph))  # Nhấc chân khi đưa về trước
            add_rot(b, lname, t, [sw * 18.0, side * sw * 14.0, -side * lift * 14.0], "catmullrom")
            add_rot(b, f"{lname}_shin", t, [-sw * 14.0 - lift * 18.0, 0.0, 0.0], "catmullrom")
            add_rot(b, f"{lname}_foot", t, [lift * 16.0, 0.0, 0.0], "catmullrom")

        # Đuôi quét đối trọng ngược pha với hông
        for ti in range(7):
            add_rot(b, f"tail_{ti + 1}", t, [math.cos(ph) * 2.5, -math.sin(ph - ti * 0.45) * (7.0 + ti * 1.5), 0.0], "catmullrom")

        # 4 cổ hơi chúi về trước giữ thăng bằng săn mồi
        for i in range(1, 5):
            h_ph = ph + i * 1.1
            add_rot(b, f"neck_{i}_seg_1", t, [5.0 + math.sin(h_ph) * 3.0, math.sin(ph) * 4.0, 0.0], "catmullrom")
            add_rot(b, f"neck_{i}_seg_3", t, [-3.0 + math.cos(h_ph) * 3.0, -math.sin(ph) * 3.0, 0.0], "catmullrom")
            add_rot(b, f"neck_{i}_seg_5", t, [4.0 + math.sin(h_ph) * 2.5, 0.0, 0.0], "catmullrom")
            add_rot(b, f"frill_{i}_l", t, [0.0, 6.0 + lift * 4.0, 0.0], "catmullrom")
            add_rot(b, f"frill_{i}_r", t, [0.0, -(6.0 + lift * 4.0), 0.0], "catmullrom")
    anims["animation.spirit_hydra.walk"] = {"loop": True, "animation_length": 1.4, "bones": b}

    # -------------------------------------------------------------------------
    # 3. BITE_1 .. BITE_4 (1.10s = 22 ticks) — Telegraph rụt S + xòe mang quạt (0..10) -> Phóng cắn (11..14, impact 12) -> Recovery (15..22)
    #    Các đầu không cắn vẫn cử động độc lập nhẹ nhàng!
    # -------------------------------------------------------------------------
    for target_i in range(1, 5):
        b = {}
        side_sign = +1 if target_i <= 2 else -1
        # Thân nhún lấy đà nhẹ
        add_rot(b, "chest", 0, [0, 0, 0])
        add_rot(b, "chest", 9, [-7.0, side_sign * 8.0, 0.0], "easeInCubic")
        add_rot(b, "chest", 12, [9.0, -side_sign * 6.0, 0.0], "easeOutExpo")
        add_rot(b, "chest", 22, [0, 0, 0], "easeInOutSine")

        # Đầu ra đòn: rụt cổ cong chữ S + há to hàm + xòe căng mang quạt (t=9) -> phóng thẳng cắn ngập (t=12) -> thu về (t=22)
        neck_pose(b, target_i, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, target_i, 9,  [-18, -16, -8, +14, +18, +10], jaw_open=44.0, frill_flare=34.0, easing="easeInCubic")
        neck_pose(b, target_i, 12, [+16, +14, +10, +8, +4, -10],  jaw_open=-6.0, frill_flare=28.0, easing="easeOutExpo")
        neck_pose(b, target_i, 15, [+12, +10, +6, +4, +2, -6],    jaw_open=4.0,  frill_flare=14.0, easing="easeOutSine")
        neck_pose(b, target_i, 22, [0, 0, 0, 0, 0, 0],            jaw_open=0.0,  frill_flare=0.0,  easing="easeInOutSine")

        # Các đầu còn lại vẫn cử động sống động, không bị đóng băng đơ
        for other in range(1, 5):
            if other == target_i:
                continue
            d = +1 if other <= 2 else -1
            neck_pose(b, other, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
            neck_pose(b, other, 10, [-4, -3, 2, 3, -2, 0], [d * 5, d * 4, 0, -d * 3, 0, 0], jaw_open=10.0, frill_flare=10.0)
            neck_pose(b, other, 22, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)

        anims[f"animation.spirit_hydra.bite_{target_i}"] = {"loop": False, "animation_length": 1.1, "bones": b}

    # -------------------------------------------------------------------------
    # 4. TRIPLE_BITE (2.60s = 52 ticks) — Liên hoàn 3 đầu phóng cắn kế tiếp (impacts: 12, 24, 36)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 9, [-8, 10, 0], "easeInCubic")
    add_rot(b, "chest", 12, [10, -8, 0], "easeOutExpo")
    add_rot(b, "chest", 21, [-8, -10, 0], "easeInCubic")
    add_rot(b, "chest", 24, [10, 8, 0], "easeOutExpo")
    add_rot(b, "chest", 33, [-10, 0, 0], "easeInCubic")
    add_rot(b, "chest", 36, [12, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 52, [0, 0, 0], "easeInOutSine")

    strike_seq = [(1, 12), (4, 24), (2, 36)]
    for h_i, imp in strike_seq:
        w0 = max(0, imp - 11)
        coil = imp - 3
        rec = min(52, imp + 12)
        neck_pose(b, h_i, w0,   [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, h_i, coil, [-18, -15, -6, +14, +16, +10], jaw_open=46.0, frill_flare=36.0, easing="easeInCubic")
        neck_pose(b, h_i, imp,  [+16, +14, +10, +8, +2, -10],  jaw_open=-6.0, frill_flare=30.0, easing="easeOutExpo")
        neck_pose(b, h_i, rec,  [0, 0, 0, 0, 0, 0],            jaw_open=0.0,  frill_flare=0.0,  easing="easeInOutSine")
    # Đầu 3 gầm hăm dọa hỗ trợ
    neck_pose(b, 3, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    neck_pose(b, 3, 24, [-10, -8, 4, 8, 4, -4], jaw_open=26.0, frill_flare=22.0)
    neck_pose(b, 3, 52, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.triple_bite"] = {"loop": False, "animation_length": 2.6, "bones": b}

    # -------------------------------------------------------------------------
    # 5. BREATH — SPIRIT BREATH (2.80s = 56 ticks) — Ngẩng hít tụ lực (0..16), quét ngang trái->phải phun hơi hồn (16..44), thu thế (44..56)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 14, [-12, 16, 0], "easeInCubic")
    add_rot(b, "chest", 18, [6, 22, 0], "easeOutCubic")
    add_rot(b, "chest", 30, [8, 0, 0], "easeInOutSine")
    add_rot(b, "chest", 44, [6, -22, 0], "easeInOutSine")
    add_rot(b, "chest", 56, [0, 0, 0], "easeInOutSine")

    # 2 đầu giữa phun quét ngang đồng bộ, 2 đầu ngoài mở quạt gầm trợ uy
    for hi in (2, 3):
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 14, [-16, -14, -8, 6, 8, -4], [8, 6, 6, 4, 4, 2], jaw_open=36.0, frill_flare=40.0, easing="easeInCubic")
        neck_pose(b, hi, 18, [8, 10, 8, 6, 4, -6],     [10, 10, 8, 8, 6, 4], jaw_open=48.0, frill_flare=42.0, easing="easeOutCubic")
        neck_pose(b, hi, 31, [10, 10, 8, 6, 4, -6],    [0, 0, 0, 0, 0, 0],   jaw_open=48.0, frill_flare=42.0, easing="catmullrom")
        neck_pose(b, hi, 44, [8, 10, 8, 6, 4, -6],     [-10, -10, -8, -8, -6, -4], jaw_open=44.0, frill_flare=38.0, easing="easeOutSine")
        neck_pose(b, hi, 56, [0, 0, 0, 0, 0, 0],       jaw_open=0.0, frill_flare=0.0, easing="easeInOutSine")
    for hi in (1, 4):
        s = +1 if hi == 1 else -1
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 16, [-10, -8, -4, 4, 4, 0], [s * -8, s * -6, 0, 0, 0, 0], jaw_open=22.0, frill_flare=28.0)
        neck_pose(b, hi, 44, [-8, -6, -2, 4, 4, 0],  [s * -6, s * -4, 0, 0, 0, 0], jaw_open=18.0, frill_flare=24.0)
        neck_pose(b, hi, 56, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.breath"] = {"loop": False, "animation_length": 2.8, "bones": b}

    # -------------------------------------------------------------------------
    # 6. SPIT — SOUL SPIT (1.60s = 32 ticks) — Từng đầu giật cổ lấy đà rồi vung lên nhả cầu hồn lệch nhịp (13, 16, 19, 22)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 10, [6, 0, 0], "easeInCubic")
    add_rot(b, "chest", 20, [-8, 0, 0], "easeOutCubic")
    add_rot(b, "chest", 32, [0, 0, 0], "easeInOutSine")
    spit_ticks = [(1, 13), (2, 16), (3, 19), (4, 22)]
    for hi, st in spit_ticks:
        neck_pose(b, hi, 0,      [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, st - 5, [10, 8, 4, -8, -10, -8], jaw_open=16.0, frill_flare=28.0, easing="easeInCubic")
        neck_pose(b, hi, st,     [-14, -12, -8, -4, -2, -12], jaw_open=46.0, frill_flare=38.0, easing="easeOutBack")
        neck_pose(b, hi, 32,     [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0, easing="easeInOutSine")
    anims["animation.spirit_hydra.spit"] = {"loop": False, "animation_length": 1.6, "bones": b}

    # -------------------------------------------------------------------------
    # 7. SLAM — TAIL / BODY SLAM (1.80s = 36 ticks) — Vặn hông lấy đà (0..15) -> Quất đuôi vòng cực mạnh & đập thân (16..20, impact 17) -> Thu thế
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0, [0, 0, 0])
    add_pos(b, "body", 14, [0, 4.5, 0], "easeInCubic")
    add_pos(b, "body", 17, [0, -1.5, 0], "easeOutExpo")
    add_pos(b, "body", 24, [0, -0.5, 0], "easeOutSine")
    add_pos(b, "body", 36, [0, 0, 0], "easeInOutSine")

    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 14, [-16, -24, 0], "easeInCubic")
    add_rot(b, "chest", 17, [12, 22, 0], "easeOutExpo")
    add_rot(b, "chest", 36, [0, 0, 0], "easeInOutSine")

    add_rot(b, "pelvis", 0, [0, 0, 0])
    add_rot(b, "pelvis", 14, [8, 34, 0], "easeInCubic")
    add_rot(b, "pelvis", 18, [-6, -42, 0], "easeOutExpo")
    add_rot(b, "pelvis", 25, [-2, -26, 0], "easeOutSine")
    add_rot(b, "pelvis", 36, [0, 0, 0], "easeInOutSine")

    for ti in range(7):
        add_rot(b, f"tail_{ti + 1}", 0,  [0, 0, 0])
        add_rot(b, f"tail_{ti + 1}", 14, [-8 - ti * 1.5, 16 + ti * 3.0, 0], "easeInCubic")
        add_rot(b, f"tail_{ti + 1}", 18, [-14 - ti * 2.0, -24 - ti * 4.5, 0], "easeOutExpo")
        add_rot(b, f"tail_{ti + 1}", 26, [-6, -12 - ti * 2.0, 0], "easeOutSine")
        add_rot(b, f"tail_{ti + 1}", 36, [0, 0, 0], "easeInOutSine")

    for hi in range(1, 5):
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 14, [-12, -10, -4, 6, 8, 0], jaw_open=36.0, frill_flare=34.0, easing="easeInCubic")
        neck_pose(b, hi, 18, [12, 10, 6, 4, 2, -6],   jaw_open=12.0, frill_flare=26.0, easing="easeOutExpo")
        neck_pose(b, hi, 36, [0, 0, 0, 0, 0, 0],      jaw_open=0.0,  frill_flare=0.0)
    anims["animation.spirit_hydra.slam"] = {"loop": False, "animation_length": 1.8, "bones": b}

    # -------------------------------------------------------------------------
    # 8. SUMMON_WISPS (1.80s = 36 ticks) — 4 đầu vươn trời tru gọi oan hồn (impact 18)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 16, [-14, 0, 0], "easeInOutSine")
    add_rot(b, "chest", 22, [-16, 0, 0], "easeOutCubic")
    add_rot(b, "chest", 36, [0, 0, 0], "easeInOutSine")
    for hi in range(1, 5):
        s = +1 if hi <= 2 else -1
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 14, [-14, -12, -10, -8, -6, -8], [s * -6, s * -4, 0, 0, 0, 0], jaw_open=42.0, frill_flare=38.0)
        neck_pose(b, hi, 22, [-16, -14, -10, -6, -4, -10], jaw_open=46.0, frill_flare=42.0)
        neck_pose(b, hi, 36, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.summon_wisps"] = {"loop": False, "animation_length": 1.8, "bones": b}

    # -------------------------------------------------------------------------
    # 9. HEAD_CUT (1.25s = 25 ticks) & STAGGER (1.00s = 20 ticks) — Giật lùi đau đớn, các đầu sống ngửa trời rống, cổ cụt rung giật!
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0, [0, 0, 0])
    add_pos(b, "body", 4, [0, -1.8, 4.5], "easeOutExpo")
    add_pos(b, "body", 14, [0, -1.2, 3.2], "easeInOutSine")
    add_pos(b, "body", 25, [0, 0, 0], "easeInOutSine")
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 5, [-18, 0, 6], "easeOutExpo")
    add_rot(b, "chest", 11, [-15, 0, -6], "easeInOutSine")
    add_rot(b, "chest", 17, [-10, 0, 4], "easeInOutSine")
    add_rot(b, "chest", 25, [0, 0, 0], "easeInOutSine")
    for hi in range(1, 5):
        s = +1 if hi <= 2 else -1
        # Cổ cụt rung giật tóe hồn!
        add_rot(b, f"stump_{hi}", 0, [0, 0, 0])
        add_rot(b, f"stump_{hi}", 4, [-12, s * 14, s * 10], "easeOutExpo")
        add_rot(b, f"stump_{hi}", 9, [10, -s * 12, -s * 8], "easeInOutSine")
        add_rot(b, f"stump_{hi}", 15, [-8, s * 8, s * 6], "easeInOutSine")
        add_rot(b, f"stump_{hi}", 25, [0, 0, 0], "easeInOutSine")
        # Các đầu sống ngửa lên rống đau
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 5,  [-18, -16, -12, -8, -4, -14], [s * -10, s * -8, 0, 0, 0, 0], jaw_open=48.0, frill_flare=40.0, easing="easeOutExpo")
        neck_pose(b, hi, 14, [-14, -12, -8, -4, 0, -10],   [s * 8, s * 6, 0, 0, 0, 0],   jaw_open=44.0, frill_flare=34.0)
        neck_pose(b, hi, 25, [0, 0, 0, 0, 0, 0],           jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.head_cut"] = {"loop": False, "animation_length": 1.25, "bones": b}

    b_stag = {}
    add_pos(b_stag, "body", 0, [0, 0, 0])
    add_pos(b_stag, "body", 5, [0, -1.4, 3.0], "easeOutCubic")
    add_pos(b_stag, "body", 20, [0, 0, 0], "easeInOutSine")
    add_rot(b_stag, "chest", 0, [0, 0, 0])
    add_rot(b_stag, "chest", 6, [-12, 0, 5], "easeOutCubic")
    add_rot(b_stag, "chest", 13, [-8, 0, -4], "easeInOutSine")
    add_rot(b_stag, "chest", 20, [0, 0, 0], "easeInOutSine")
    for hi in range(1, 5):
        neck_pose(b_stag, hi, 0, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b_stag, hi, 6, [-12, -10, -6, 0, 4, -8], jaw_open=36.0, frill_flare=30.0)
        neck_pose(b_stag, hi, 20, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.stagger"] = {"loop": False, "animation_length": 1.0, "bones": b_stag}

    # -------------------------------------------------------------------------
    # 10. HEAD_REGROW (0.80s = 16 ticks) — Cổ duỗi bung mọc lại + xòe căng mang quạt
    # -------------------------------------------------------------------------
    b = {}
    for hi in range(1, 5):
        add_scale(b, f"neck_{hi}", 0, [0.35, 0.35, 0.35], "easeOutBack")
        add_scale(b, f"neck_{hi}", 8, [1.10, 1.10, 1.10], "easeOutBack")
        add_scale(b, f"neck_{hi}", 16, [1.0, 1.0, 1.0], "easeInOutSine")
        neck_pose(b, hi, 0,  [14, 12, 8, 6, 4, 0], jaw_open=12.0, frill_flare=0.0)
        neck_pose(b, hi, 9,  [-14, -12, -8, -4, 0, -10], jaw_open=46.0, frill_flare=44.0, easing="easeOutBack")
        neck_pose(b, hi, 16, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.head_regrow"] = {"loop": False, "animation_length": 0.8, "bones": b}

    # -------------------------------------------------------------------------
    # 11. PHASE_CHANGE (5.00s = 100 ticks) — Hạ thân tụ lực (0..24) -> Gầm bùng nổ (24..38) ->
    #     Từng đầu 1..4 duỗi bung mọc lại cách nhau ĐÚNG 0.35s (7 ticks: 38, 45, 52, 59) -> 4 đầu xòe quạt gầm đồng thanh (72..100)!
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,   [0, 0, 0])
    add_pos(b, "body", 20,  [0, -3.2, 0], "easeInCubic")
    add_pos(b, "body", 32,  [0, 3.6, 0],  "easeOutExpo")
    add_pos(b, "body", 74,  [0, 2.8, 0],  "easeInOutSine")
    add_pos(b, "body", 100, [0, 0, 0],    "easeInOutSine")

    add_rot(b, "chest", 0,   [0, 0, 0])
    add_rot(b, "chest", 20,  [14, 0, 0],  "easeInCubic")
    add_rot(b, "chest", 32,  [-18, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 76,  [-20, 0, 0], "easeInOutSine")
    add_rot(b, "chest", 100, [0, 0, 0],   "easeInOutSine")

    # Đuôi vút dựng cao uy nghiêm
    for ti in range(7):
        add_rot(b, f"tail_{ti + 1}", 0,   [0, 0, 0])
        add_rot(b, f"tail_{ti + 1}", 22,  [-6, 0, 0])
        add_rot(b, f"tail_{ti + 1}", 36,  [10 + ti * 1.2, 0, 0], "easeOutBack")
        add_rot(b, f"tail_{ti + 1}", 80,  [8 + ti * 1.0, math.sin(ti) * 6.0, 0])
        add_rot(b, f"tail_{ti + 1}", 100, [0, 0, 0])

    # 4 đầu mọc bung lần lượt ở tick 38, 45, 52, 59 (cách nhau đúng 7 tick = 0.35s!), rồi gầm đồng thanh ở 74..90!
    regrow_starts = [38, 45, 52, 59]
    for idx_0, rg_t in enumerate(regrow_starts):
        hi = idx_0 + 1
        s = +1 if hi <= 2 else -1
        # Co rút tụ lực trước khi mọc bung
        neck_pose(b, hi, 0,        [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 20,       [16, 14, 10, 6, 4, 8], jaw_open=10.0, frill_flare=0.0, easing="easeInCubic")
        neck_pose(b, hi, rg_t - 2, [14, 12, 8, 6, 4, 6],  jaw_open=16.0, frill_flare=0.0)
        # Bung mạnh mọc lại & xòe căng mang quạt!
        add_scale(b, f"neck_{hi}", rg_t - 1, [0.45, 0.45, 0.45])
        add_scale(b, f"neck_{hi}", rg_t + 6, [1.08, 1.08, 1.08], "easeOutBack")
        add_scale(b, f"neck_{hi}", rg_t + 12, [1.0, 1.0, 1.0], "easeInOutSine")
        neck_pose(b, hi, rg_t + 6, [-16, -14, -10, -4, 0, -10], [s * -8, s * -6, 0, 0, 0, 0],
                  jaw_open=46.0, frill_flare=44.0, easing="easeOutBack")
        # Đồng thanh 4 đầu gầm trời!
        neck_pose(b, hi, 78, [-18, -14, -10, -6, -4, -12], [s * -10, s * -6, 0, 0, 0, 0],
                  jaw_open=50.0, frill_flare=46.0, easing="easeOutCubic")
        neck_pose(b, hi, 90, [-14, -10, -6, -4, -2, -8],
                  jaw_open=42.0, frill_flare=40.0, easing="easeInOutSine")
        neck_pose(b, hi, 100, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0, easing="easeInOutSine")
    anims["animation.spirit_hydra.phase_change"] = {"loop": False, "animation_length": 5.0, "bones": b}

    # -------------------------------------------------------------------------
    # 12. ALL_HEAD_BARRAGE (Phase 2, 2.20s = 44 ticks) — 4 đầu tỏa quạt đồng loạt bắn 2 loạt mưa cầu hồn (impacts: 16, 28)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 14, [8, 0, 0], "easeInCubic")
    add_rot(b, "chest", 17, [-10, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 26, [6, 0, 0], "easeInCubic")
    add_rot(b, "chest", 29, [-12, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 44, [0, 0, 0], "easeInOutSine")
    for hi in range(1, 5):
        s = +1 if hi <= 2 else -1
        wide = -10.0 if hi in (1, 4) else -4.0
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 13, [12, 10, 6, -6, -8, -6], [s * wide, s * wide, 0, 0, 0, 0], jaw_open=20.0, frill_flare=36.0, easing="easeInCubic")
        neck_pose(b, hi, 17, [-14, -12, -8, -4, -2, -12], [s * wide, s * wide, 0, 0, 0, 0], jaw_open=48.0, frill_flare=44.0, easing="easeOutExpo")
        neck_pose(b, hi, 25, [10, 8, 4, -6, -8, -6], [s * wide, s * wide, 0, 0, 0, 0], jaw_open=22.0, frill_flare=38.0, easing="easeInCubic")
        neck_pose(b, hi, 29, [-16, -14, -10, -4, -2, -12], [s * wide, s * wide, 0, 0, 0, 0], jaw_open=50.0, frill_flare=46.0, easing="easeOutExpo")
        neck_pose(b, hi, 44, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0, easing="easeInOutSine")
    anims["animation.spirit_hydra.all_head_barrage"] = {"loop": False, "animation_length": 2.2, "bones": b}

    # -------------------------------------------------------------------------
    # 13. SPIRIT_NOVA (Phase 2, 2.40s = 48 ticks) — 4 đầu thu tụ tâm (0..17) rồi nổ bung tỏa vòng 360° (18..42)!
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0, [0, 0, 0])
    add_pos(b, "body", 16, [0, -3.5, 0], "easeInCubic")
    add_pos(b, "body", 20, [0, 3.0, 0], "easeOutExpo")
    add_pos(b, "body", 48, [0, 0, 0], "easeInOutSine")
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 16, [14, 0, 0], "easeInCubic")
    add_rot(b, "chest", 20, [-18, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 48, [0, 0, 0], "easeInOutSine")
    for hi in range(1, 5):
        s = +1 if hi <= 2 else -1
        # Hội tụ vào giữa rồi bung tỏa mạnh ra ngoài!
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 16, [14, 12, 10, 8, 6, 8], [s * 12, s * 10, s * 6, 0, 0, 0], jaw_open=14.0, frill_flare=8.0, easing="easeInCubic")
        neck_pose(b, hi, 20, [-16, -14, -8, -4, 0, -10], [s * -14, s * -12, s * -8, 0, 0, 0], jaw_open=50.0, frill_flare=46.0, easing="easeOutExpo")
        neck_pose(b, hi, 38, [-10, -8, -4, -2, 0, -6], [s * -10, s * -8, s * -4, 0, 0, 0], jaw_open=38.0, frill_flare=36.0)
        neck_pose(b, hi, 48, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.spirit_nova"] = {"loop": False, "animation_length": 2.4, "bones": b}

    # -------------------------------------------------------------------------
    # 14. SOUL_CHAIN (Phase 2, 2.00s = 40 ticks) — Phóng xích kéo (impact 14), giật ngược (16..24), cắn bồi cực mạnh (impact 26)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 12, [-10, 0, 0], "easeInCubic")
    add_rot(b, "chest", 15, [8, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 23, [-14, 0, 0], "easeInOutSine")
    add_rot(b, "chest", 27, [14, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 40, [0, 0, 0], "easeInOutSine")
    # 2 đầu giữa phối hợp xích & cắn bồi
    for hi in (2, 3):
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 11, [-14, -12, -6, 10, 12, 6], jaw_open=36.0, frill_flare=34.0, easing="easeInCubic")
        neck_pose(b, hi, 15, [12, 10, 8, 6, 2, -6],     jaw_open=42.0, frill_flare=38.0, easing="easeOutExpo")
        neck_pose(b, hi, 23, [-20, -16, -10, 14, 16, 10], jaw_open=48.0, frill_flare=42.0, easing="easeInCubic")
        neck_pose(b, hi, 27, [18, 16, 12, 8, 4, -10],   jaw_open=-6.0, frill_flare=30.0, easing="easeOutExpo")
        neck_pose(b, hi, 40, [0, 0, 0, 0, 0, 0],        jaw_open=0.0,  frill_flare=0.0)
    for hi in (1, 4):
        s = +1 if hi == 1 else -1
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 24, [-8, -6, 2, 6, 4, 0], [s * -8, s * -6, 0, 0, 0, 0], jaw_open=28.0, frill_flare=28.0)
        neck_pose(b, hi, 40, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.soul_chain"] = {"loop": False, "animation_length": 2.0, "bones": b}

    # -------------------------------------------------------------------------
    # 15. WRATH_OF_MANY_HEADS (Ultimate Phase 2, 3.20s = 64 ticks) — 4 đầu hội tụ gồng xoáy (0..36) -> quét cắn xé 360° (38..44)!
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0, [0, 0, 0])
    add_pos(b, "body", 20, [0, -3.5, 0], "easeInCubic")
    add_pos(b, "body", 36, [0, -4.2, 0], "easeInOutSine")
    add_pos(b, "body", 40, [0, 4.2, 0], "easeOutExpo")
    add_pos(b, "body", 64, [0, 0, 0], "easeInOutSine")

    add_rot(b, "chest", 0, [0, 0, 0])
    add_rot(b, "chest", 18, [12, 16, 0], "easeInOutSine")
    add_rot(b, "chest", 36, [16, -16, 0], "easeInOutSine")
    add_rot(b, "chest", 40, [-14, 0, 0], "easeOutExpo")
    add_rot(b, "chest", 64, [0, 0, 0], "easeInOutSine")

    for ti in range(7):
        add_rot(b, f"tail_{ti + 1}", 0,  [0, 0, 0])
        add_rot(b, f"tail_{ti + 1}", 36, [10, 18 + ti * 3.0, 0])
        add_rot(b, f"tail_{ti + 1}", 42, [-8, -22 - ti * 3.5, 0], "easeOutExpo")
        add_rot(b, f"tail_{ti + 1}", 64, [0, 0, 0])

    for hi in range(1, 5):
        s = +1 if hi <= 2 else -1
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        # Gồng hội tụ tạo vòng năng lượng
        neck_pose(b, hi, 18, [-14, -10, 8, 14, 16, 8], [s * 14, s * 10, s * 6, 0, 0, 0], jaw_open=42.0, frill_flare=42.0, easing="easeInCubic")
        neck_pose(b, hi, 36, [-20, -16, -6, 18, 20, 12], [s * 16, s * 12, s * 6, 0, 0, 0], jaw_open=50.0, frill_flare=46.0, easing="easeInOutSine")
        # Phóng bung cắn xé 360° cực gắt!
        neck_pose(b, hi, 40, [16, 14, 10, 8, 4, -8], [s * -18, s * -16, s * -12, s * -8, 0, 0], jaw_open=-6.0, frill_flare=46.0, easing="easeOutExpo")
        neck_pose(b, hi, 48, [10, 8, 6, 4, 2, -4], [s * -12, s * -10, s * -6, 0, 0, 0], jaw_open=16.0, frill_flare=30.0)
        neck_pose(b, hi, 64, [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
    anims["animation.spirit_hydra.wrath_of_many_heads"] = {"loop": False, "animation_length": 3.2, "bones": b}

    # -------------------------------------------------------------------------
    # 16. DEATH (3.00s = 60 ticks, hold) — Gục thân bò sát xuống đất, 4 cổ rũ mềm tản ra, hàm thả lỏng
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 14, [0, -2.5, 0], "easeInCubic")
    add_pos(b, "body", 34, [0, -7.2, 0], "easeOutBounce")
    add_pos(b, "body", 60, [0, -7.5, 0], "linear")

    add_rot(b, "chest", 0,  [0, 0, 0])
    add_rot(b, "chest", 14, [-12, 0, 0], "easeOutCubic")
    add_rot(b, "chest", 36, [10, 0, 8],  "easeInCubic")
    add_rot(b, "chest", 60, [12, 0, 10], "linear")

    for lname, s in (("leg_fl", 1), ("leg_fr", -1), ("leg_bl", 1), ("leg_br", -1)):
        add_rot(b, lname, 0,  [0, 0, 0])
        add_rot(b, lname, 34, [12, 0, s * 28], "easeOutCubic")
        add_rot(b, lname, 60, [14, 0, s * 32], "linear")

    for ti in range(7):
        add_rot(b, f"tail_{ti + 1}", 0,  [0, 0, 0])
        add_rot(b, f"tail_{ti + 1}", 42, [-14 - ti * 1.5, 8, 0], "easeOutCubic")
        add_rot(b, f"tail_{ti + 1}", 60, [-16 - ti * 1.5, 10, 0], "linear")

    for hi in range(1, 5):
        s = +1 if hi <= 2 else -1
        neck_pose(b, hi, 0,  [0, 0, 0, 0, 0, 0], jaw_open=0.0, frill_flare=0.0)
        neck_pose(b, hi, 12, [-14, -12, -8, -4, 0, -10], jaw_open=42.0, frill_flare=34.0, easing="easeOutCubic")
        neck_pose(b, hi, 40, [14, 12, 10, 8, 8, 6], [s * -6, s * -6, s * -4, 0, 0, 0], jaw_open=26.0, frill_flare=-18.0, easing="easeInCubic")
        neck_pose(b, hi, 60, [16, 14, 12, 10, 10, 8], [s * -8, s * -6, s * -4, 0, 0, 0], jaw_open=28.0, frill_flare=-22.0, easing="linear")
    anims["animation.spirit_hydra.death"] = {"loop": "hold_on_last_frame", "animation_length": 3.0, "bones": b}

    # Kiểm định an toàn tuyệt đối: KHÔNG có bất kỳ góc xoay nào vượt quá ±120° (chống spin 360°)
    for aname, adata in anims.items():
        for bname, bdata in adata["bones"].items():
            for tk, kv in bdata.get("rotation", {}).items():
                for comp in kv["vector"]:
                    assert abs(comp) <= 120.0, f"Rotation exceeds 120° in {aname}.{bname}@{tk}: {kv}"

    out = {"format_version": "1.8.0", "animations": anims}
    out_path = ASSETS / "animations" / "entity" / "spirit_hydra.animation.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
    print(f"ANIM_OK: {out_path} -> {len(anims)} animations verified (0 angles > 120°)")
    return out


# =============================================================================
# 5. BỘ RENDER PREVIEW 3D Z-BUFFER ALPHA-CUTOUT CHUẨN GECKOLIB 4.8.3 (ĐA GÓC ĐỐI CHIẾU!)
# =============================================================================
def collect_world_geometry(geo: dict, hidden_bones: set | None = None):
    if hidden_bones is None:
        hidden_bones = {"stump_1", "stump_2", "stump_3", "stump_4"}
    bone_map = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    xforms = {}

    def is_hidden(name):
        cur = name
        while cur:
            if cur in hidden_bones:
                return True
            cur = bone_map[cur].get("parent")
        return False

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
    # 6 mặt hộp theo chuẩn Bedrock
    face_defs = [
        ("north", (0, 1, 2, 3)),
        ("south", (5, 4, 7, 6)),
        ("west",  (4, 0, 3, 7)),
        ("east",  (1, 5, 6, 2)),
        ("up",    (3, 2, 6, 7)),
        ("down",  (4, 5, 1, 0)),
    ]

    for b in geo["minecraft:geometry"][0]["bones"]:
        if is_hidden(b["name"]):
            continue
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


def rasterize_alpha_cutout(faces, diff_img, glow_img, view_mat, width, height, scale, center,
                           bg=(10, 22, 28), light_dir=None):
    fb = np.zeros((height, width, 3), dtype=np.float32)
    fb[:, :] = np.array(bg, dtype=np.float32)
    zbuf = np.full((height, width), -1e9, dtype=np.float32)
    cx, cy = width * 0.5, height * 0.52
    if light_dir is None:
        light_dir = np.array([0.42, 0.78, -0.46], dtype=np.float64)
    light_dir = light_dir / np.linalg.norm(light_dir)

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
        lit = d_col * shade * (1.0 - g_a * 0.88) + g_col * (g_a * 1.18)
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
    d_norm = np.array(Image.open(tex_dir / "spirit_hydra.png").convert("RGBA"), dtype=np.float32)
    g_norm = np.array(Image.open(tex_dir / "spirit_hydra_glowmask.png").convert("RGBA"), dtype=np.float32)
    d_enr = np.array(Image.open(tex_dir / "spirit_hydra_enraged.png").convert("RGBA"), dtype=np.float32)
    g_enr = np.array(Image.open(tex_dir / "spirit_hydra_enraged_glowmask.png").convert("RGBA"), dtype=np.float32)

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

    # 1. Trước 3/4 (Front 3/4 Oblique — kiểm tra 4 đầu tỏa quạt + mang quạt sáng + răng + thân bò sát)
    p_3q = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(145, 16), pw, ph, sc * 1.18, center)
    ImageDraw.Draw(p_3q).text((12, 10), "FRONT 3/4 OBLIQUE (4 HEADS FANNED, FRILLS, GLOWING UNDERBELLY)", fill=(165, 248, 255))
    p_3q.save(PREVIEW_DIR / "preview_three_quarter.png")

    # 2. Ngang (Side Profile — kiểm tra cổ chữ S, thân thấp, chân bò sát, đai sáng cổ chân & đuôi vút lên!)
    p_side = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(90, 6), pw, ph, sc * 1.12, center)
    ImageDraw.Draw(p_side).text((12, 10), "SIDE PROFILE (S-CURVED NECKS, CROUCHED LEGS, HIGH CURVED TAIL)", fill=(165, 248, 255))
    p_side.save(PREVIEW_DIR / "preview_side.png")

    # 3. Trên xuống (Top-Down — xác nhận 4 cổ tách quạt rộng 0% chồng lấp!)
    p_top = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(180, 86), pw, ph, sc * 1.05, center)
    ImageDraw.Draw(p_top).text((12, 10), "TOP-DOWN VIEW (WIDE 4-NECK FAN SPREAD — ZERO OVERLAP)", fill=(165, 248, 255))
    p_top.save(PREVIEW_DIR / "preview_top.png")

    # 4. Dưới lên (Bottom-Up — xác nhận dải bụng sáng liên tục xuyên suốt 4 cổ -> ngực -> bụng -> đuôi & sọc hàm dưới!)
    p_bot = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(180, -86), pw, ph, sc * 1.05, center,
                                   light_dir=np.array([0.0, -1.0, 0.0]))
    ImageDraw.Draw(p_bot).text((12, 10), "BOTTOM-UP VIEW (CONTINUOUS GLOWING VENTRAL STRIP & RIBBED JAWS)", fill=(165, 248, 255))
    p_bot.save(PREVIEW_DIR / "preview_bottom.png")

    # 5. Chính diện (Front Heroic — kiểm tra bố cục 4 đầu, răng, mắt, sừng, mang quạt & 4 chân!)
    p_front = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(180, 10), pw, ph, sc * 1.25, center)
    ImageDraw.Draw(p_front).text((12, 10), "FRONT HEROIC (4 DRAGON HEADS, TEETH, EYES, HORNS, FRILLS)", fill=(165, 248, 255))
    p_front.save(PREVIEW_DIR / "preview_front.png")

    # 6. Phase 2 Enraged (3/4 Oblique — kiểm tra khe nứt sáng trắng-xanh điện/tím lạnh trên lưng/sườn!)
    p_p2 = rasterize_alpha_cutout(faces, d_enr, g_enr, cam_mat(145, 16), pw, ph, sc * 1.18, center)
    ImageDraw.Draw(p_p2).text((12, 10), "PHASE 2 ENRAGED (ELECTRIC WHITE-CYAN & COLD VIOLET CRACKS)", fill=(220, 175, 255))
    p_p2.save(PREVIEW_DIR / "preview_phase2_enraged.png")

    # Bảng tổng hợp 3x2 đa góc
    sheet = Image.new("RGB", (pw * 3, ph * 2), (10, 22, 28))
    sheet.paste(p_3q,    (0,      0))
    sheet.paste(p_side,  (pw,     0))
    sheet.paste(p_top,   (pw * 2, 0))
    sheet.paste(p_front, (0,      ph))
    sheet.paste(p_bot,   (pw,     ph))
    sheet.paste(p_p2,    (pw * 2, ph))
    sheet.save(PREVIEW_DIR / "hydra_preview_sheet.png")

    print(
        f"PREVIEW_OK: Saved 6 angles + sheet to {PREVIEW_DIR} | "
        f"bbox X=[{bmin[0]:.1f}..{bmax[0]:.1f}] Y=[{bmin[1]:.1f}..{bmax[1]:.1f}] Z=[{bmin[2]:.1f}..{bmax[2]:.1f}]"
    )


if __name__ == "__main__":
    backup_existing_files()
    geo_data, total_cubes, meta = build_hydra_geo()
    build_textures()
    build_animations()
    render_previews(geo_data)
    print(f"ALL_DONE: Spirit Hydra v2 rebuilt ({meta['bones']} bones, {total_cubes} cubes, {TEX_W}x{TEX_H})")
