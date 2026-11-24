#!/usr/bin/env python3
"""
Bộ sinh Model (1.150–1.550 cubes) + Texture 512x512 (nearest, alpha-cutout vây màng lửa liền mạch)
+ 12 Animation mượt (catmullrom / ease-in-out) đồng bộ 0-tick tuyệt đối với LeviathanAttackTiming.java
+ Preview 3D Z-buffer Alpha-Cutout đa góc (Front, Left Side, Right Side, Top-Down, Bottom-Up, Phase 2 Enraged)
cho Boss Leviathan (abyssalrpg:leviathan) — Thủy Quái Biển Sâu 4 Tay Vây Lửa v2.

Thiết kế:
- 70–80% giữ đúng phom dáng & bảng màu mẫu 5 góc:
  + Dáng thân thủy quái vai trước gù vạm vỡ -> thuôn dài mượt về 8 đốt đuôi roi nhọn (`body_chest`, `body_mid`,
    `body_rear`, `tail_1..tail_8`), có khớp lồng thu nhẹ đầu-cuối (overlapping joint cap) nối khít 100% khi uốn chữ S.
  + ĐÚNG 2 CẶP TAY VUỐT DÀI (4 TAY):
    - Cặp tay chính ngoài (`arm_front_l/r`: `upper`, `forearm`, `hand`): to khỏe, khuỷu cong lực, 3 móng vuốt đen sắc,
      dải vây màng cam-đỏ có gai xám mảnh sau cẳng tay.
    - Cặp tay phụ sau/trong (`arm_rear_l/r`: `upper`, `forearm`, `hand`): ~78% tay chính, khuỷu gập vồ mồi, 3 móng đen + vây màng lửa.
  + Đầu thủy quái biển sâu sắc sảo (phóng nhẹ cục bộ ~1.18x): gờ mày giáp xiên, 2 sừng/râu trán vát ngược,
    2 CHIẾC RĂNG NANH NGÀ LỚN (#F4E8CC) nhô lộ rõ hai bên mõm khi khép hàm + hàng răng trắng đều,
    nướu & khoang miệng tím hồng sẫm (#7E3F6B -> #5A274A) + lưỡi chi tiết, mắt xiên vàng-cam phát sáng (#FFAA18/#FFF0A8),
    2 dải vây mang má (`frill_cheek_l/r`) & chùm vây cằm (`frill_chin`) alpha-cutout mỏng có nan gai mảnh.
  + Vây đuôi lá lửa LIỀN MẠCH hai bên (`tail_fin_1..7_l/r`): 12 cặp nan xương vây xám lam rất mảnh nhô vượt mép
    nối kín bởi màng alpha mỏng chuyển đỏ thẫm #8F1912 -> đỏ cam #DB3B14 -> cam cháy #FF7818 -> viền vàng #FFC838;
    + vây sống lưng vai (`dorsal_fin`) 5 nan gai + màng lửa mỏng.
  + Vảy lưng xanh lam đá phiến/biển sâu (#1B2936..#7FA8B0) sọc vằn ngang đậm-nhạt; bụng xếp vảy chữ V kem ngà ánh hồng
    (#E8D0D0 -> #CCAAB0 -> #A8848C); glowmask fullbright cho mắt vàng-cam, dải đốm sáng xanh ngọc dọc sống lưng
    (#8AF8EC -> #4FD6CF), khe mang xanh lam sâu sau vai (#2452FF -> #5E8BFF) và viền ấm màng vây lửa.
- 20–30% biến tấu bản sắc Abyssal Spire RPG:
  + Màng vây đuôi họa tiết gân vây phân nhánh cá rồng vực thẳm + mép cắt răng cưa mảnh sắc nét.
  + Tấm giáp vai vát góc cạnh xếp tầng phía trước vây lưng + cặp gai má phụ + mũi chóp đuôi lưỡi vây xanh ngọc phát sáng.
  + Phase 2 Enraged: đốm lưng & khe mang bùng trắng-xanh điện cực chói, vây lửa rực nóng hơn, vết nứt năng lượng mảnh trên giáp vai & hàm.

Chạy: python tools/generate_leviathan_model.py
"""

import json
import math
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
BACKUP_DIR = ROOT / "tools" / "backup_leviathan_v1"
PREVIEW_DIR = ROOT / "tools" / "leviathan_previews"

TEX_W = 512
TEX_H = 512

# Vùng vật liệu trên atlas 512x512 (x0, y0, w, h)
MAT = {
    "SCALE_BANDED":   (0,   0,   128, 128),  # Vảy biển sâu xanh đá phiến (#1B2936..#7FA8B0) sọc vằn ngang đậm-nhạt
    "SCALE_ARMOR":    (128, 0,   128, 128),  # Giáp vai vát tầng, đầu, gờ mày & cẳng tay tối sắc cạnh (+ nứt sáng P2)
    "BELLY_CHEVRON":  (256, 0,   128, 128),  # Bụng vảy xếp chữ V kem ngà ánh hồng (#E8D0D0 -> #CCAAB0 -> #A8848C)
    "TAIL_FIN_MEM":   (384, 0,   128, 256),  # Màng vây lá lửa alpha-cutout (đỏ thẫm -> cam cháy -> viền vàng, gân phân nhánh + mép răng cưa!)
    "DORSAL_GLOW":    (0,   128, 128, 128),  # Sống lưng xanh đá phiến có DẢI ĐỐM/VỆT PHÁT QUANG XANH NGỌC (#8AF8EC -> #4FD6CF)!
    "FLANK_GILL":     (128, 128, 128, 128),  # Sườn sau vai có KHE MANG PHÁT SÁNG XANH LAM SÂU (#2452FF -> #5E8BFF) uốn cong!
    "SMALL_FIN_MEM":  (256, 128, 128, 128),  # Màng vây lửa mảnh cho lưng vai, 4 cẳng tay, vây má & chùm vây cằm (alpha-cutout!)
    "FIN_RAY":        (0,   256, 128, 128),  # Nan xương vây mảnh màu xám lam lạnh (#3E4B5A -> #7A8B9C)
    "GUM_MOUTH":      (128, 256, 128, 128),  # Nướu & khoang miệng tím hồng sẫm (#7E3F6B -> #5A274A) có vân cơ hàm
    "TONGUE_PLUM":    (256, 256, 64,  64),   # Lưỡi thủy quái tím hồng đậm có rãnh giữa
    "EYE_AMBER":      (320, 256, 64,  64),   # Mắt xiên phát sáng vàng-cam (#FFAA18, lõi #FFF0A8)
    "TUSK_IVORY":     (256, 320, 64,  64),   # 2 chiếc răng nanh ngà lớn (#F4E8CC) bóng mịn sắc nhọn
    "TOOTH_WHITE":    (320, 320, 64,  64),   # Hàng răng trắng đều sắc nhọn dọc 2 hàm
    "CLAW_BLACK":     (0,   384, 128, 128),  # Móng vuốt đen obsidian cong sắc ở 4 bàn tay (#12161E -> #343E4F)
    "TIP_BLADE_GLOW": (128, 384, 128, 128),  # Lưỡi vây tinh thể nhỏ ở chóp đuôi phát sáng xanh ngọc (biến tấu bản sắc!)
    "DARK_JOINT":     (256, 384, 128, 128),  # Khớp lồng tối sâu nối liền giữa các đốt thân–đuôi–khuỷu tay
    "DORSAL_SEAM":    (384, 256, 128, 128),  # Sống lưng gù trên vai có rãnh ánh cam ấm tinh tế giữa 2 mảnh giáp
    "ARM_BICEP":      (384, 384, 128, 128),  # Cơ bắp cánh tay sọc vằn xanh đá phiến có gân nổi khỏe
}


def backup_existing_files() -> None:
    if BACKUP_DIR.exists() and any(BACKUP_DIR.rglob("*.json")):
        print(f"BACKUP_SKIP: Đã có bản sao lưu Leviathan gốc tại {BACKUP_DIR}")
        return
    files = [
        ASSETS / "geo" / "entity" / "leviathan.geo.json",
        ASSETS / "animations" / "entity" / "leviathan.animation.json",
        ASSETS / "textures" / "entity" / "leviathan.png",
        ASSETS / "textures" / "entity" / "leviathan_glowmask.png",
        ASSETS / "textures" / "entity" / "leviathan_enraged.png",
        ASSETS / "textures" / "entity" / "leviathan_enraged_glowmask.png",
    ]
    copied = 0
    for f in files:
        if f.exists():
            dst = BACKUP_DIR / f.relative_to(ASSETS)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dst)
            copied += 1
    print(f"BACKUP_OK: Đã sao lưu {copied} file Leviathan cũ vào {BACKUP_DIR}")


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
                  segments: int, mat: str, overlap: float = 0.25, sub_v_span=(0.0, 1.0),
                  face_overrides: dict | None = None, aspect_z: float = 1.0) -> None:
    """Dựng thanh cơ bắp / nan vây / móng vuốt / sừng liền khối 100% dọc p_start -> p_end theo R_true."""
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


def add_horizontal_fin_sheet(out_list: list, root_a, root_b, tip_a, tip_b,
                             mat: str = "TAIL_FIN_MEM", sub_u=(0.0, 1.0), sub_v=(0.0, 1.0),
                             thick_y: float = 0.28, strips: int = 3) -> None:
    """
    Căng dải màng vây lá lửa nằm NGANG cực mỏng (0.28 px, alpha-cutout 2 mặt!) nối kín 100% giữa 2 nan xương vây
    từ đoạn gốc `(root_a -> root_b)` tỏa ra mép ngoài `(tip_a -> tip_b)` với góc nghiêng ngang chính xác `rotY`!
    """
    ra = np.array(root_a, dtype=np.float64)
    rb = np.array(root_b, dtype=np.float64)
    ta = np.array(tip_a, dtype=np.float64)
    tb = np.array(tip_b, dtype=np.float64)
    for i in range(strips):
        f0 = i / float(strips)
        f1 = (i + 1) / float(strips)
        fm = 0.5 * (f0 + f1)
        p_in = ra + (rb - ra) * fm
        p_out = ta + (tb - ta) * fm
        span_vec = p_out - p_in
        span_len = float(np.linalg.norm(span_vec))
        if span_len < 0.8:
            continue
        # Bề rộng theo trục dọc Z của mỗi dải màng phủ chồng nhẹ 1.24x đảm bảo KÍN 100% không hở khe!
        z_span = max(float(np.linalg.norm(rb - ra)), float(np.linalg.norm(tb - ta))) / strips
        strip_z = max(1.4, z_span * 1.26)
        # Đặt trục cục bộ +X (hoặc -X) dọc theo hướng tỏa ngang `span_vec`:
        # Với R_true(0, ry, 0): Ry(+ry) @ [side, 0, 0] = [side*cos(ry), 0, -side*sin(ry)]
        # Muốn khớp [span_vec.x, 0, span_vec.z]:
        side = 1.0 if span_vec[0] >= 0 else -1.0
        horiz_len = max(1e-4, math.hypot(span_vec[0], span_vec[2]))
        ry_deg = math.degrees(math.atan2(-side * span_vec[2], side * span_vec[0]))
        # Nghiêng nhẹ theo độ dốc Y nếu mép ngoài hơi cao/thấp hơn gốc:
        # Rz(-rz) @ [side, 0, 0] = [side*cos(rz), -side*sin(rz), 0]
        rz_deg = math.degrees(math.atan2(-side * span_vec[1], horiz_len))
        rot_deg = [0.0, ry_deg, rz_deg]

        # Kiểm định trục cục bộ [side, 0, 0] trùng hướng span_vec
        chk = bedrock_rot_matrix(rot_deg) @ np.array([side, 0.0, 0.0])
        assert np.allclose(chk, span_vec / span_len, atol=2e-2), f"Fin sheet mismatch: {chk} vs {span_vec / span_len}"

        ox = p_in[0] if side > 0 else (p_in[0] - span_len)
        oy = p_in[1] - thick_y * 0.5
        oz = p_in[2] - strip_z * 0.5
        su0 = sub_u[0] + (sub_u[1] - sub_u[0]) * f0
        su1 = sub_u[0] + (sub_u[1] - sub_u[0]) * f1
        # Mặt UP và DOWN hiển thị màng vây lửa sắc nét!
        # Lưu ý UV của mặt UP/DOWN trong Bedrock map X->U, Z->V: nên ta truyền sub_u theo Z và sub_v theo X
        # (ở texture TAIL_FIN_MEM: U=0 gốc thân -> U=1 mép ngoài vàng rực khi side=+1, ngược lại khi side=-1!)
        # Để cả hai bên TRÁI (+X) và PHẢI (-X) đều có đỏ thẫm ở gốc thân và vàng rực ở mép ngoài:
        # Ta chia texture TAIL_FIN_MEM thành nửa trên (v: 0..0.5, gốc=trái U=0 -> mép vàng=phải U=1 cho bên TRÁI +X)
        # và nửa dưới (v: 0.5..1.0, mép vàng=trái U=0 -> gốc=phải U=1 cho bên PHẢI -X)!
        if side > 0:
            uv_u = (0.01, 0.99)
            uv_v = (0.01 + su0 * 0.46, 0.01 + su1 * 0.46)
        else:
            uv_u = (0.01, 0.99)
            uv_v = (0.52 + su0 * 0.46, 0.52 + su1 * 0.46)

        out_list.append(make_cube(
            [ox, oy, oz],
            [span_len, thick_y, strip_z],
            mat,
            rot=rot_deg,
            pivot=p_in.tolist(),
            pad=0,
            sub_u=uv_u,
            sub_v=uv_v,
            transparent_edges=("north", "south", "east", "west"),
        ))


def add_vertical_fin_strip(out_list: list, base_a, base_b, tip_a, tip_b,
                           mat: str = "SMALL_FIN_MEM", thick: float = 0.26, strips: int = 4,
                           flip_v: bool = False) -> None:
    """Dựng dải màng vây lửa phẳng mỏng đứng dọc (cho vây lưng vai, vây sau 4 cẳng tay, vây má & chùm vây cằm)."""
    ba = np.array(base_a, dtype=np.float64)
    bb = np.array(base_b, dtype=np.float64)
    ta = np.array(tip_a, dtype=np.float64)
    tb = np.array(tip_b, dtype=np.float64)
    for i in range(strips):
        f0 = i / float(strips)
        f1 = (i + 1) / float(strips)
        fm = 0.5 * (f0 + f1)
        p0 = ba + (bb - ba) * fm
        p1 = ta + (tb - ta) * fm
        vec = p1 - p0
        L = float(np.linalg.norm(vec))
        if L < 0.6:
            continue
        u = vec / L
        horiz = math.hypot(u[0], u[2])
        a_r = math.atan2(horiz, u[1])
        ry_r = math.atan2(u[0], u[2]) if horiz > 1e-5 else 0.0
        rot_s = [-math.degrees(a_r), +math.degrees(ry_r), 0.0]

        # Xác định mặt phẳng dải vây từ (bb - ba) x vec
        chord_vec = (bb - ba)
        chord_len = float(np.linalg.norm(chord_vec)) / strips * 1.24
        n_plane = np.cross(chord_vec, vec)
        nl = float(np.linalg.norm(n_plane))
        if nl < 1e-4:
            continue
        n_plane /= nl
        R_s = bedrock_rot_matrix(rot_s)
        ex_w = R_s @ np.array([1.0, 0.0, 0.0])
        ez_w = R_s @ np.array([0.0, 0.0, 1.0])
        if abs(float(np.dot(ex_w, n_plane))) >= abs(float(np.dot(ez_w, n_plane))):
            sx, sz = thick, max(1.0, chord_len)
            tedges = ("north", "south", "up", "down")
        else:
            sx, sz = max(1.0, chord_len), thick
            tedges = ("east", "west", "up", "down")

        su = (0.02 + f0 * 0.90, 0.02 + f1 * 0.90)
        sv = (0.02, 0.96) if not flip_v else (0.96, 0.02)
        out_list.append(make_cube(
            [p0[0] - sx * 0.5, p0[1], p0[2] - sz * 0.5],
            [sx, L, sz],
            mat,
            rot=rot_s,
            pivot=p0.tolist(),
            pad=0,
            sub_u=su,
            sub_v=sv,
            transparent_edges=tedges,
        ))


# =============================================================================
# 1. XÂY DỰNG 4 CÁNH TAY VUỐT DÀI (2 CẶP TAY: FRONT PLUNGING + REAR WIDE-FLARED)
# =============================================================================
def build_arm_assembly(bones: list, prefix: str, parent: str, side: int,
                       shoulder: list, elbow: list, wrist: list,
                       scale_f: float = 1.0,
                       fin_dir: tuple = (0.28, 0.14, 0.95),
                       hand_inward: float = 1.0) -> None:
    """
    Dựng 1 cánh tay thủy quái vạm vỡ nhiều tầng giáp cơ (`prefix` = upper -> `prefix_forearm` -> `prefix_hand`):
    - Vai giáp xếp tầng (layered deltoid pauldrons), bắp tay gân cơ vằn xanh đá phiến + gờ giáp ngoài.
    - Khuỷu cong lực bọc khớp lồng tối + gai khuỷu vát ngược sắc cạnh.
    - Cẳng tay giáp vát + 5 nan xương vây xám lam rõ nét (`1.15 -> 0.55` px) nhô vượt mép màng lửa rực!
    - Bàn tay lớn có 3 NGÓN XÒE RÕ RỆT nhiều đốt + 3 MÓNG VUỐT ĐEN OBSIDIAN CONG SẮC (+ ngón cái trong).
    """
    sh = np.array(shoulder, dtype=np.float64)
    el = np.array(elbow, dtype=np.float64)
    wr = np.array(wrist, dtype=np.float64)
    u_dir = el - sh
    u_norm = u_dir / max(1e-4, float(np.linalg.norm(u_dir)))
    f_dir = wr - el
    f_u = f_dir / max(1e-4, float(np.linalg.norm(f_dir)))

    # 1. Cánh tay trên (`arm_front_l/r`, `arm_rear_l/r`) — cơ vai & bắp tay lực lưỡng
    uc = []
    r_sh = 4.5 * scale_f
    # Ổ khớp vai + 3 tấm giáp vai vát tầng (deltoid pauldrons)
    uc.append(make_cube([sh[0] - r_sh, sh[1] - r_sh * 0.85, sh[2] - r_sh * 1.1],
                        [r_sh * 2.0, r_sh * 1.75, r_sh * 2.2], "SCALE_ARMOR"))
    for k in range(3):
        pt = sh + u_dir * (0.08 + k * 0.16) + np.array([side * (1.8 + k * 0.4) * scale_f, (2.2 - k * 1.1) * scale_f, -0.5 * scale_f])
        pw = (4.6 - k * 0.5) * scale_f
        ph = (2.6 - k * 0.25) * scale_f
        pz = (7.2 - k * 0.8) * scale_f
        uc.append(make_cube([pt[0] - pw * 0.5, pt[1] - ph * 0.5, pt[2] - pz * 0.5],
                            [pw, ph, pz], "SCALE_ARMOR",
                            rot=[8.0 - k * 4.0, side * -12.0, side * (22.0 + k * 6.0)],
                            pivot=pt.tolist()))
    # Lõi bắp tay 5 đốt thuôn + 2 dải cơ giáp trước/sau tạo tiết diện 8 cạnh khỏe khoắn
    add_chain_rod(uc, sh, el, 6.8 * scale_f, 5.4 * scale_f, 5, "ARM_BICEP", overlap=0.75, aspect_z=1.18)
    add_chain_rod(uc, sh + np.array([side * 1.5 * scale_f, 0.9 * scale_f, -0.8 * scale_f]),
                  el + np.array([side * 1.2 * scale_f, 0.4 * scale_f, -0.5 * scale_f]),
                  3.8 * scale_f, 3.0 * scale_f, 4, "SCALE_ARMOR", overlap=0.55)
    add_chain_rod(uc, sh + np.array([-side * 1.2 * scale_f, -0.6 * scale_f, 1.0 * scale_f]),
                  el + np.array([-side * 0.9 * scale_f, -0.4 * scale_f, 0.8 * scale_f]),
                  3.4 * scale_f, 2.7 * scale_f, 3, "SCALE_BANDED", overlap=0.50)
    # Khớp khuỷu lồng tối + giáp bảo vệ khuỷu + 2 gai khuỷu xám lam sắc
    uc.append(make_cube([el[0] - 2.9 * scale_f, el[1] - 2.9 * scale_f, el[2] - 3.1 * scale_f],
                        [5.8 * scale_f, 5.8 * scale_f, 6.2 * scale_f], "DARK_JOINT"))
    uc.append(make_cube([el[0] - 2.4 * scale_f + side * 1.4 * scale_f, el[1] - 2.2 * scale_f, el[2] - 2.2 * scale_f + 1.2 * scale_f],
                        [4.2 * scale_f, 4.4 * scale_f, 4.6 * scale_f], "SCALE_ARMOR"))
    add_chain_rod(uc, el + np.array([side * 1.4 * scale_f, 0.6 * scale_f, 1.5 * scale_f]),
                  el + np.array([side * 4.2 * scale_f, 2.4 * scale_f, 5.8 * scale_f]),
                  1.7 * scale_f, 0.45 * scale_f, 3, "FIN_RAY")
    add_chain_rod(uc, el + np.array([side * 1.1 * scale_f, -1.0 * scale_f, 1.8 * scale_f]),
                  el + np.array([side * 3.0 * scale_f, 0.2 * scale_f, 4.6 * scale_f]),
                  1.25 * scale_f, 0.35 * scale_f, 2, "FIN_RAY")

    bones.append({
        "name": prefix,
        "parent": parent,
        "pivot": [round(float(sh[0]), 3), round(float(sh[1]), 3), round(float(sh[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": uc,
    })

    # 2. Cẳng tay (`prefix_forearm`) + giáp vát + 5 nan vây xám lam rõ + màng lửa rực!
    fc = []
    add_chain_rod(fc, el, wr, 5.6 * scale_f, 4.6 * scale_f, 5, "SCALE_BANDED", overlap=0.75, aspect_z=1.18)
    # Tấm giáp bọc mặt trước & mặt ngoài cẳng tay
    add_chain_rod(fc, el + np.array([side * 1.4 * scale_f, -0.3 * scale_f, -1.2 * scale_f]),
                  wr + np.array([side * 1.1 * scale_f, 0.2 * scale_f, -1.0 * scale_f]),
                  3.3 * scale_f, 2.6 * scale_f, 4, "SCALE_ARMOR", overlap=0.50)
    add_chain_rod(fc, el + np.array([-side * 1.0 * scale_f, 0.0, -1.1 * scale_f]),
                  wr + np.array([-side * 0.8 * scale_f, 0.0, -0.9 * scale_f]),
                  2.6 * scale_f, 2.2 * scale_f, 3, "ARM_BICEP", overlap=0.45)
    # Cổ tay bọc giáp
    fc.append(make_cube([wr[0] - 2.6 * scale_f, wr[1] - 2.4 * scale_f, wr[2] - 2.8 * scale_f],
                        [5.2 * scale_f, 4.8 * scale_f, 5.6 * scale_f], "DARK_JOINT"))

    fin_out = np.array([side * fin_dir[0], fin_dir[1], fin_dir[2]], dtype=np.float64)
    fin_out /= np.linalg.norm(fin_out)

    # 5 nan xương vây xám lam dày vừa rõ nét dọc sau cẳng tay nhô vượt ngoài màng lửa!
    ray_count = 5
    ray_roots = []
    ray_mem_tips = []
    for k in range(ray_count):
        ft = 0.12 + (k / float(ray_count - 1)) * 0.78
        bell = math.sin(((k + 0.55) / (ray_count + 0.1)) * math.pi)
        ray_len = (6.8 + 4.2 * bell) * scale_f
        mem_len = ray_len * 0.72
        r0 = el + f_dir * ft + fin_out * (2.2 * scale_f)
        r_tip = r0 + fin_out * ray_len - f_u * (0.9 * scale_f)
        m_tip = r0 + fin_out * mem_len - f_u * (0.5 * scale_f)
        ray_roots.append(r0)
        ray_mem_tips.append(m_tip)
        add_chain_rod(fc, r0, r_tip, 1.20 * scale_f, 0.55 * scale_f, 3, "FIN_RAY", overlap=0.22)

    for k in range(ray_count - 1):
        add_vertical_fin_strip(fc, ray_roots[k], ray_roots[k + 1],
                               ray_mem_tips[k], ray_mem_tips[k + 1],
                               mat="SMALL_FIN_MEM", thick=0.28, strips=3)

    forearm_name = f"{prefix}_forearm"
    bones.append({
        "name": forearm_name,
        "parent": prefix,
        "pivot": [round(float(el[0]), 3), round(float(el[1]), 3), round(float(el[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": fc,
    })

    # 3. Bàn tay cơ bắp & 3 NGÓN VUỐT XÒE RÕ RỆT MÓNG ĐEN OBSIDIAN (`prefix_hand`)
    hc = []
    palm_vec = f_u * (5.2 * scale_f) + np.array([-side * 1.4 * hand_inward * scale_f, -1.0 * scale_f, -0.8 * scale_f])
    palm_end = wr + palm_vec
    add_chain_rod(hc, wr, palm_end, 4.8 * scale_f, 4.4 * scale_f, 3, "SCALE_ARMOR", overlap=0.60, aspect_z=1.22)
    # Tấm mu bàn tay gồ giáp
    hc.append(make_cube([palm_end[0] - 2.3 * scale_f, palm_end[1] - 2.1 * scale_f, palm_end[2] - 2.8 * scale_f],
                        [4.6 * scale_f, 4.2 * scale_f, 5.6 * scale_f], "SCALE_ARMOR"))

    # 3 ngón tay xòe rộng dễ phân biệt từ chính diện, ngang, trên và dưới + móng vuốt đen obsidian dày cong sắc!
    finger_specs = [
        # (offset gốc khớp ngón, hướng đốt giữa, hướng mũi móng vuốt, hệ số chiều dài)
        (np.array([+side * 1.2, 0.3, -2.5]),
         np.array([-side * 1.4 * hand_inward, -2.4, -2.8]),
         np.array([-side * 3.8 * hand_inward, -2.6, -2.6]), 1.05),
        (np.array([+side * 0.2, 0.0,  0.0]),
         np.array([-side * 2.2 * hand_inward, -3.4, -0.6]),
         np.array([-side * 4.6 * hand_inward, -3.0, -0.4]), 1.22),
        (np.array([-side * 0.9, -0.2, +2.5]),
         np.array([-side * 1.6 * hand_inward, -2.4, +2.0]),
         np.array([-side * 3.8 * hand_inward, -2.4, +1.8]), 1.00),
    ]
    for f_off, mid_d, claw_d, cm in finger_specs:
        knuckle = palm_end + f_off * scale_f
        mid_f = knuckle + (f_u * 2.2 + mid_d) * (scale_f * cm)
        claw_tip = mid_f + claw_d * (scale_f * cm)
        add_chain_rod(hc, knuckle, mid_f, 2.25 * scale_f, 1.70 * scale_f, 3, "SCALE_ARMOR", overlap=0.30)
        add_chain_rod(hc, mid_f, claw_tip, 1.65 * scale_f, 0.42 * scale_f, 3, "CLAW_BLACK", overlap=0.25)

    # Ngón cái bám cạnh trong vồ giữ mồi
    thumb_k = wr + palm_vec * 0.62 + np.array([-side * 2.2 * scale_f, -0.2 * scale_f, -2.2 * scale_f])
    thumb_m = thumb_k + np.array([-side * 2.6 * scale_f, -1.6 * scale_f, -2.2 * scale_f])
    thumb_t = thumb_m + np.array([-side * 2.8 * scale_f, -1.4 * scale_f, -1.4 * scale_f])
    add_chain_rod(hc, thumb_k, thumb_m, 1.75 * scale_f, 1.35 * scale_f, 2, "SCALE_ARMOR", overlap=0.22)
    add_chain_rod(hc, thumb_m, thumb_t, 1.30 * scale_f, 0.38 * scale_f, 2, "CLAW_BLACK", overlap=0.22)

    bones.append({
        "name": f"{prefix}_hand",
        "parent": forearm_name,
        "pivot": [round(float(wr[0]), 3), round(float(wr[1]), 3), round(float(wr[2]), 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": hc,
    })


# =============================================================================
# 2. XÂY DỰNG ĐẦU THỦY QUÁI BIỂN SÂU (`neck`, `head`, `jaw`, `frill_cheek_l/r`, `frill_chin`)
#    Phóng cục bộ ~1.20x: 2 răng nanh ngà lớn lộ rõ, mắt xiên sắc, vây má bùng rộng, nướu tím hồng!
# =============================================================================
def build_head_assembly(bones: list, neck_root: list) -> list:
    nx, ny, nz = neck_root
    # 1. Cổ ngắn khỏe hạ đầu thấp về phía trước (`neck`) + vảy xếp lớp 8 cạnh + bụng cổ kem ngà nổi khối!
    nc = [
        make_cube([nx - 8.2, ny - 6.8, nz - 3.2], [16.4, 13.4, 7.6], "DARK_JOINT",
                  face_overrides={"down": "BELLY_CHEVRON"}),
        make_cube([nx - 7.6, ny - 6.8, nz - 11.4], [15.2, 12.6, 11.0], "SCALE_BANDED",
                  face_overrides={"up": "SCALE_ARMOR", "down": "BELLY_CHEVRON"}),
        make_cube([nx - 6.4, ny + 4.4, nz - 10.8], [12.8, 3.0, 10.2], "SCALE_ARMOR",
                  rot=[13.0, 0.0, 0.0], pivot=[nx, ny + 5.6, nz - 1.8]),
    ]
    # Tấm giáp sườn cổ vát xiên + 3 bậc vảy bụng chữ V kem ngà hồng sáng rực ngay dưới họng!
    for side in (-1, 1):
        nc.append(make_cube([nx + (6.6 if side > 0 else -8.8), ny - 4.4, nz - 10.8], [2.2, 8.8, 9.6], "SCALE_ARMOR",
                            rot=[0.0, side * -6.0, side * 12.0], pivot=[nx + side * 7.6, ny, nz - 6.0]))
        nc.append(make_cube([nx + (5.2 if side > 0 else -7.4), ny + 3.2, nz - 10.2], [2.2, 3.2, 9.4], "SCALE_BANDED",
                            rot=[8.0, 0.0, side * 28.0], pivot=[nx + side * 6.2, ny + 4.8, nz - 5.5]))
    for k in range(4):
        sz_p = nz - 11.4 + k * 2.8
        sw_p = 12.2 + k * 0.4
        nc.append(make_cube([nx - sw_p * 0.5, ny - 7.8 - (k % 2) * 0.25, sz_p], [sw_p, 2.2, 3.3], "BELLY_CHEVRON"))

    bones.append({
        "name": "neck",
        "parent": "body_chest",
        "pivot": [round(nx, 3), round(ny, 3), round(nz, 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": nc,
    })

    # 2. Hộp sọ & mõm thủy quái sắc sảo (`head`, phóng nhẹ cục bộ ~1.20x!)
    hx, hy, hz = nx, ny - 0.8, nz - 10.6
    hc = []
    # Sọ sau vát góc cạnh + má giáp nhiều lớp
    hc.append(make_cube([hx - 6.8, hy - 3.0, hz - 8.2], [13.6, 10.4, 9.6], "SCALE_ARMOR",
                        face_overrides={"down": "GUM_MOUTH"}))
    hc.append(make_cube([hx - 5.4, hy + 6.4, hz - 7.6], [10.8, 2.2, 8.4], "SCALE_ARMOR"))
    for side in (-1, 1):
        cx = hx + (5.6 if side > 0 else -8.2)
        hc.append(make_cube([cx, hy - 2.6, hz - 10.0], [2.6, 6.6, 9.4], "SCALE_BANDED",
                            rot=[0.0, side * -9.0, side * 12.0],
                            pivot=[hx + side * 6.8, hy + 0.6, hz - 5.2]))
        # Lớp vảy giáp xương gò má + cặp gai má phụ (biến tấu bản sắc Abyssal Spire!)
        hc.append(make_cube([hx + (4.8 if side > 0 else -7.0), hy - 2.2, hz - 15.8], [2.2, 4.4, 6.8], "SCALE_ARMOR",
                            rot=[4.0, side * -14.0, side * 8.0],
                            pivot=[hx + side * 5.8, hy, hz - 12.4]))
        add_chain_rod(hc, [hx + side * 6.4, hy - 1.2, hz - 2.8],
                      [hx + side * 9.8, hy - 2.6, hz + 3.4], 1.85, 0.45, 3, "SCALE_ARMOR")
        add_chain_rod(hc, [hx + side * 5.8, hy - 2.6, hz - 5.4],
                      [hx + side * 8.6, hy - 4.0, hz - 0.2], 1.35, 0.35, 2, "FIN_RAY")

    # Vòm trán giáp vát mạnh xuống mũi + sống trán sắc + 2 gờ mày giáp xiên dữ tợn che trên mắt!
    hc.append(make_cube([hx - 5.5, hy + 5.2, hz - 14.4], [11.0, 3.0, 13.0], "SCALE_ARMOR",
                        rot=[12.0, 0.0, 0.0], pivot=[hx, hy + 6.5, hz - 2.6]))
    hc.append(make_cube([hx - 3.2, hy + 7.2, hz - 13.6], [6.4, 1.8, 11.4], "SCALE_BANDED",
                        rot=[13.0, 0.0, 0.0], pivot=[hx, hy + 7.8, hz - 2.8]))
    for side in (-1, 1):
        bx = hx + (3.6 if side > 0 else -7.0)
        hc.append(make_cube([bx, hy + 4.4, hz - 15.8], [3.4, 2.3, 8.6], "SCALE_ARMOR",
                            rot=[16.0, side * -12.0, side * -18.0],
                            pivot=[hx + side * 5.3, hy + 5.3, hz - 11.5]))
        # Viền lông mày dưới tạo khe mắt xiên sắc (tránh cảm giác mắt hình hộp to thô!)
        hc.append(make_cube([hx + (4.9 if side > 0 else -6.4), hy + 3.6, hz - 14.8], [1.5, 1.1, 5.2], "DARK_JOINT",
                            rot=[16.0, side * -10.0, 0.0], pivot=[hx + side * 5.6, hy + 4.0, hz - 12.2]))
        # 2 sừng/râu trán mảnh vát ngược sắc sảo (đúng ảnh chính diện mẫu!)
        add_chain_rod(hc, [hx + side * 4.4, hy + 6.0, hz - 14.2],
                      [hx + side * 9.8, hy + 10.6, hz - 2.8], 1.55, 0.36, 4, "FIN_RAY", overlap=0.22)

    # ĐÔI MẮT XIÊN SẮC PHÁT SÁNG VÀNG-CAM (#FFAA18, lõi #FFF0A8) ẩn dưới gờ mày dữ tợn!
    for side in (-1, 1):
        ex = hx + (5.25 if side > 0 else -6.65)
        hc.append(make_cube([ex, hy + 2.1, hz - 14.4], [1.4, 1.85, 4.2], "EYE_AMBER",
                            rot=[16.0, side * -11.0, 0.0],
                            pivot=[hx + side * 5.9, hy + 3.0, hz - 12.3]))
        hc.append(make_cube([ex + side * 0.12, hy + 1.25, hz - 14.0], [1.2, 0.95, 4.0], "DARK_JOINT",
                            rot=[14.0, side * -10.0, 0.0], pivot=[hx + side * 5.9, hy + 1.7, hz - 12.0]))

    # Mõm trên thuôn gọn 3 bậc (tạo mũi thủy quái sắc cạnh như ảnh chính diện & ngang!)
    hc.append(make_cube([hx - 5.2, hy - 1.2, hz - 16.8], [10.4, 6.2, 10.6], "SCALE_BANDED",
                        face_overrides={"up": "SCALE_ARMOR", "down": "GUM_MOUTH"}))
    hc.append(make_cube([hx - 4.1, hy - 0.8, hz - 21.8], [8.2, 5.2, 5.8], "SCALE_ARMOR",
                        face_overrides={"down": "GUM_MOUTH"}))
    hc.append(make_cube([hx - 3.0, hy - 0.4, hz - 24.0], [6.0, 4.2, 3.0], "SCALE_ARMOR",
                        face_overrides={"down": "GUM_MOUTH"}))
    # Sống mũi gồ giáp + 2 lỗ mũi + gờ viền hàm trên
    hc.append(make_cube([hx - 2.4, hy + 4.1, hz - 22.2], [4.8, 1.9, 10.6], "SCALE_ARMOR",
                        rot=[10.0, 0.0, 0.0], pivot=[hx, hy + 4.9, hz - 13.0]))
    hc.append(make_cube([hx - 1.4, hy + 5.1, hz - 20.6], [2.8, 1.2, 8.6], "SCALE_BANDED",
                        rot=[10.0, 0.0, 0.0], pivot=[hx, hy + 5.6, hz - 13.0]))
    for side in (-1, 1):
        hc.append(make_cube([hx + (1.7 if side > 0 else -3.3), hy + 3.4, hz - 22.8], [1.6, 1.3, 2.6], "DARK_JOINT"))
        hc.append(make_cube([hx + (4.2 if side > 0 else -5.4), hy - 1.1, hz - 20.4], [1.2, 1.5, 13.2], "SCALE_ARMOR"))

    # NƯỚU VÀ VÒM MIỆNG TÍM HỒNG SẪM (#7E3F6B -> #5A274A)
    hc.append(make_cube([hx - 4.4, hy - 2.1, hz - 22.2], [8.8, 1.5, 17.0], "GUM_MOUTH"))

    # 2 CHIẾC RĂNG NANH NGÀ LỚN (#F4E8CC) vát cong rõ nét LỘ RÕ RA NGOÀI hai bên mõm kể cả khi khép hàm!
    for side in (-1, 1):
        tusk_base = [hx + side * 3.85, hy - 0.1, hz - 21.6]
        tusk_mid  = [hx + side * 4.35, hy - 4.5, hz - 22.3]
        tusk_tip  = [hx + side * 3.60, hy - 8.4, hz - 21.4]
        add_chain_rod(hc, tusk_base, tusk_mid, 2.20, 1.68, 3, "TUSK_IVORY", overlap=0.30)
        add_chain_rod(hc, tusk_mid, tusk_tip, 1.62, 0.48, 3, "TUSK_IVORY", overlap=0.26)

    # Hàng răng trắng nhỏ đều sắc nhọn dọc viền hàm trên (7 răng mỗi bên + 4 răng cửa giữa 2 nanh!)
    for side in (-1, 1):
        for k, tz in enumerate((-7.4, -9.4, -11.4, -13.4, -15.4, -17.4, -19.4)):
            tx = hx + side * (4.7 - k * 0.14) - 0.42
            th = 1.50 if k % 2 == 0 else 1.15
            hc.append(make_cube([tx, hy - 1.1 - th, hz + tz - 0.42], [0.84, th, 0.84], "TOOTH_WHITE"))
    for tx_off in (-2.0, -0.68, +0.68, +2.0):
        hc.append(make_cube([hx + tx_off - 0.42, hy - 2.4, hz - 23.2], [0.84, 1.55, 0.84], "TOOTH_WHITE"))

    # Dải vây đỉnh đầu cam-đỏ chạy dọc đỉnh sọ (6 nan gai xám rõ + màng lửa rực giống ảnh profile!)
    crest_roots = [[hx, hy + 6.5, hz - 16.5 + k * 2.8] for k in range(6)]
    crest_tips  = [[hx, hy + 11.4 + math.sin(k * 0.65) * 1.6, hz - 14.8 + k * 3.1] for k in range(6)]
    crest_mems  = [[hx, hy + 9.9 + math.sin(k * 0.65) * 1.2, hz - 15.2 + k * 3.0] for k in range(6)]
    for k in range(6):
        add_chain_rod(hc, crest_roots[k], crest_tips[k], 1.05, 0.45, 3, "FIN_RAY", overlap=0.18)
    for k in range(5):
        add_vertical_fin_strip(hc, crest_roots[k], crest_roots[k + 1], crest_mems[k], crest_mems[k + 1],
                               mat="SMALL_FIN_MEM", thick=0.26, strips=2)

    bones.append({
        "name": "head",
        "parent": "neck",
        "pivot": [round(hx, 3), round(hy, 3), round(hz, 3)],
        "rotation": [0.0, 0.0, 0.0],
        "locators": {
            "mouth_locator": [round(hx, 3), round(hy - 2.4, 3), round(hz - 19.5, 3)],
        },
        "cubes": hc,
    })

    # 3. 2 DẢI VÂY MANG MÁ BÙNG RỘNG (`frill_cheek_l`, `frill_cheek_r`) — nổi bật từ cả trên xuống, dưới lên & ngang!
    for side, cname in ((+1, "frill_cheek_l"), (-1, "frill_cheek_r")):
        cpiv = [hx + side * 6.0, hy + 0.8, hz - 6.0]
        fc = []
        c_roots = []
        c_mems = []
        for k in range(6):
            z_off = -14.2 + k * 2.7
            y_off = 3.2 - k * 0.95
            bell = math.sin(((k + 0.55) / 6.1) * math.pi)
            rlen = 5.8 + 4.4 * bell
            mlen = rlen * 0.74
            p0 = np.array([hx + side * 5.6, hy + y_off, hz + z_off], dtype=np.float64)
            out_v = np.array([side * 0.88, 0.24 - k * 0.08, 0.40], dtype=np.float64)
            out_v /= np.linalg.norm(out_v)
            p_tip = p0 + out_v * rlen
            p_mem = p0 + out_v * mlen
            c_roots.append(p0)
            c_mems.append(p_mem)
            add_chain_rod(fc, p0, p_tip, 1.05, 0.45, 3, "FIN_RAY", overlap=0.18)
        for k in range(5):
            add_vertical_fin_strip(fc, c_roots[k], c_roots[k + 1], c_mems[k], c_mems[k + 1],
                                   mat="SMALL_FIN_MEM", thick=0.25, strips=3)
        bones.append({
            "name": cname,
            "parent": "head",
            "pivot": [round(cpiv[0], 3), round(cpiv[1], 3), round(cpiv[2], 3)],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": fc,
        })

    # 4. HÀM DƯỚI TÁCH RỜI (`jaw`) — Nướu & lưỡi tím hồng sẫm, răng trắng đều + cằm giáp vát!
    jpiv = [hx, hy - 2.6, hz - 3.6]
    jc = [
        make_cube([hx - 5.0, hy - 5.8, hz - 16.8], [10.0, 3.3, 13.8], "SCALE_ARMOR",
                  face_overrides={"up": "GUM_MOUTH", "down": "SCALE_ARMOR"}),
        make_cube([hx - 3.7, hy - 5.5, hz - 22.2], [7.4, 2.9, 6.2], "SCALE_ARMOR",
                  face_overrides={"up": "GUM_MOUTH", "down": "SCALE_ARMOR"}),
        make_cube([hx - 2.7, hy - 5.0, hz - 23.6], [5.4, 2.3, 2.4], "DARK_JOINT"),
        # Sống cằm giáp tối ở đáy hàm (khớp chuẩn góc nhìn dưới lên mẫu!)
        make_cube([hx - 2.0, hy - 6.3, hz - 21.8], [4.0, 1.1, 14.5], "DARK_JOINT"),
        # Khoang miệng tím hồng sẫm & lưỡi rồng biển nổi khối chi tiết 3 đốt
        make_cube([hx - 3.9, hy - 3.2, hz - 21.4], [7.8, 1.3, 15.8], "GUM_MOUTH"),
        make_cube([hx - 2.2, hy - 2.4, hz - 19.6], [4.4, 1.0, 12.2], "TONGUE_PLUM"),
        make_cube([hx - 1.4, hy - 1.9, hz - 17.8], [2.8, 0.8, 9.2], "TONGUE_PLUM"),
        make_cube([hx - 0.8, hy - 1.7, hz - 20.6], [1.6, 0.6, 3.6], "TONGUE_PLUM"),
    ]
    for side in (-1, 1):
        jc.append(make_cube([hx + (4.2 if side > 0 else -5.4), hy - 5.2, hz - 17.0], [1.2, 2.4, 12.0], "SCALE_BANDED",
                            rot=[0.0, side * -6.0, side * -12.0], pivot=[hx + side * 4.8, hy - 4.0, hz - 11.0]))
        for k, tz in enumerate((-8.4, -10.6, -12.8, -15.0, -17.2, -19.4)):
            tx = hx + side * (4.0 - k * 0.14) - 0.42
            th = 1.30 if k % 2 == 0 else 1.00
            jc.append(make_cube([tx, hy - 2.6, hz + tz - 0.42], [0.84, th, 0.84], "TOOTH_WHITE"))
    for tx_off in (-1.6, 0.0, +1.6):
        jc.append(make_cube([hx + tx_off - 0.40, hy - 2.6, hz - 22.0], [0.80, 1.30, 0.80], "TOOTH_WHITE"))

    bones.append({
        "name": "jaw",
        "parent": "head",
        "pivot": [round(jpiv[0], 3), round(jpiv[1], 3), round(jpiv[2], 3)],
        "rotation": [6.0, 0.0, 0.0],
        "cubes": jc,
    })

    # 5. CHÙM VÂY CẰM XÒE RỘNG DƯỚI HÀM DƯỚI (`frill_chin`) — 5 nan gai dày xòe quạt + màng lửa sắc!
    chin_piv = [hx, hy - 5.6, hz - 14.6]
    chc = []
    chin_angles = [-62.0, -30.0, 0.0, +30.0, +62.0]
    ch_roots = []
    ch_mems = []
    for k, ang_d in enumerate(chin_angles):
        rad = math.radians(ang_d)
        rlen = 7.0 + (2.0 if k in (1, 2, 3) else 0.0)
        mlen = rlen * 0.75
        p0 = np.array([hx, hy - 5.5, hz - 14.6 + (k - 2) * 1.8], dtype=np.float64)
        dir_v = np.array([0.0, -math.cos(rad), math.sin(rad)], dtype=np.float64)
        p_tip = p0 + dir_v * rlen
        p_mem = p0 + dir_v * mlen
        ch_roots.append(p0)
        ch_mems.append(p_mem)
        add_chain_rod(chc, p0, p_tip, 1.10, 0.48, 3, "FIN_RAY", overlap=0.18)
    for k in range(len(chin_angles) - 1):
        add_vertical_fin_strip(chc, ch_roots[k], ch_roots[k + 1], ch_mems[k], ch_mems[k + 1],
                               mat="SMALL_FIN_MEM", thick=0.28, strips=3)
    for side in (-1, 1):
        sr0 = np.array([hx + side * 3.1, hy - 5.4, hz - 19.8])
        sr1 = np.array([hx + side * 4.5, hy - 5.4, hz - 8.8])
        st0 = sr0 + np.array([side * 2.2, -4.2, 0.4])
        st1 = sr1 + np.array([side * 2.6, -4.6, 0.8])
        add_vertical_fin_strip(chc, sr0, sr1, st0, st1, mat="SMALL_FIN_MEM", thick=0.24, strips=4)

    bones.append({
        "name": "frill_chin",
        "parent": "jaw",
        "pivot": [round(chin_piv[0], 3), round(chin_piv[1], 3), round(chin_piv[2], 3)],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": chc,
    })

    return [round(hx, 2), round(hy - 2.4, 2), round(hz - 19.5, 2)]


def add_sculpted_trunk_segment(out_list: list, base_y: float, z0: float, seg_l: float,
                               w_a: float, h_a: float, w_b: float, h_b: float,
                               has_rear_collar: bool = True, scute_count: int = 3) -> None:
    """
    Điêu khắc 1 đốt thân/đuôi tiết diện 8 cạnh hữu cơ mượt (KHÔNG hộp gạch vuông!):
    - Khớp lồng trước/sau thu nhẹ (overlapping joint cap) nối khít 100% khi uốn chữ S.
    - Lõi thân vằn xanh đá phiến + tấm sống lưng nổi chứa đốm phát sáng xanh ngọc.
    - 2 cặp má giáp vát xiên trên-dưới + dải vảy sườn nổi xếp lớp theo chiều dọc.
    - `scute_count` bậc vảy bụng chữ V kem ngà ánh hồng xếp tầng tạo độ nổi sắc nét ở góc nhìn dưới lên!
    """
    w = 0.5 * (w_a + w_b)
    h = 0.5 * (h_a + h_b)
    # 1. Khớp lồng trước
    out_list.append(make_cube([-(w_a * 0.45), base_y - h_a * 0.44, z0 - 2.2],
                              [w_a * 0.90, h_a * 0.88, 4.2], "DARK_JOINT",
                              face_overrides={"down": "BELLY_CHEVRON"}))
    # 2. Lõi thân 2 bậc thuôn mượt từ (w_a, h_a) -> (w_b, h_b)
    half_l = seg_l * 0.52
    out_list.append(make_cube([-w_a * 0.49, base_y - h_a * 0.49, z0 - 0.5],
                              [w_a * 0.98, h_a * 0.98, half_l + 1.2], "SCALE_BANDED",
                              face_overrides={"up": "DORSAL_GLOW", "down": "BELLY_CHEVRON"}))
    out_list.append(make_cube([-w_b * 0.49, base_y - h_b * 0.49, z0 + seg_l * 0.48],
                              [w_b * 0.98, h_b * 0.98, half_l + 1.1], "SCALE_BANDED",
                              face_overrides={"up": "DORSAL_GLOW", "down": "BELLY_CHEVRON"}))
    # 3. Sống lưng nổi 2 tấm liên tiếp giữ mạch đốm sáng xanh ngọc liên tục
    for half_i, (zw, z_start, sw_f, sh_f) in enumerate(((w_a, z0 + 0.2, 0.68, h_a), (w_b, z0 + seg_l * 0.50, 0.66, h_b))):
        dh = max(0.85, sh_f * 0.18)
        out_list.append(make_cube([-zw * sw_f * 0.5, base_y + sh_f * 0.45, z_start],
                                  [zw * sw_f, dh, seg_l * 0.50 + 0.3], "DORSAL_GLOW"))
    # 4. Vát cạnh 8 giác (upper bevel + flank scale ribs + dorsal ridge plates + lower bevel) hai bên trái/phải
    if w >= 3.0:
        # 2 tấm gờ vảy sống lưng vát tầng tạo độ sắc nổi 3D dọc sống lưng (chứa đốm phát quang xanh ngọc!)
        for k in range(2):
            dz = z0 + 0.6 + k * (seg_l * 0.48)
            dw = max(1.4, w * 0.46)
            out_list.append(make_cube([-dw * 0.5, base_y + h * 0.56, dz],
                                      [dw, max(0.7, h * 0.12), seg_l * 0.42], "DORSAL_GLOW"))
        for side in (-1, 1):
            # 2 tầng vát vai trên (upper chamfer plates xếp lớp vảy cá biển sâu)
            uw = max(1.0, w * 0.20)
            uh = max(1.0, h * 0.26)
            ux = (w * 0.36) if side > 0 else -(w * 0.36 + uw)
            for k in range(2):
                out_list.append(make_cube([ux, base_y + h * 0.28, z0 + 0.3 + k * (seg_l * 0.49)],
                                          [uw, uh, seg_l * 0.46], "SCALE_ARMOR",
                                          rot=[0.0, 0.0, side * 26.0],
                                          pivot=[side * w * 0.42, base_y + h * 0.40, z0 + seg_l * (0.25 + k * 0.50)]))
            # 3 tấm vảy sườn nổi xếp lớp ngang tạo khối bắp cơ khỏe
            for k in range(3):
                fw = max(0.75, w * 0.11)
                fh = max(1.4, h * 0.52)
                fx = (w * 0.46) if side > 0 else -(w * 0.46 + fw)
                out_list.append(make_cube([fx, base_y - fh * 0.46, z0 + 0.35 + k * (seg_l * 0.32)],
                                          [fw, fh, seg_l * 0.30], "SCALE_BANDED"))
            # 2 tầng vát sườn dưới bo vào bụng kem ngà
            lw = max(0.9, w * 0.16)
            lh = max(0.9, h * 0.22)
            lx = (w * 0.34) if side > 0 else -(w * 0.34 + lw)
            for k in range(2):
                out_list.append(make_cube([lx, base_y - h * 0.52, z0 + 0.3 + k * (seg_l * 0.49)],
                                          [lw, lh, seg_l * 0.46], "SCALE_ARMOR",
                                          rot=[0.0, 0.0, side * -24.0],
                                          pivot=[side * w * 0.40, base_y - h * 0.42, z0 + seg_l * (0.25 + k * 0.50)]))

    # 5. Bụng vảy xếp chữ V kem ngà ánh hồng chia nhiều bậc nổi rõ
    sc_len = seg_l / float(scute_count)
    for k in range(scute_count):
        ft = (k + 0.5) / float(scute_count)
        wk = (w_a + (w_b - w_a) * ft) * 0.84
        hk = (h_a + (h_b - h_a) * ft)
        th = max(0.95, 1.45 - (1.0 - min(1.0, w / 16.0)) * 0.45)
        out_list.append(make_cube([-wk * 0.5, base_y - hk * 0.5 - th * 0.78 - (k % 2) * 0.12, z0 + k * sc_len - 0.35],
                                  [wk, th, sc_len + 0.85], "BELLY_CHEVRON"))

    # 6. Khớp lồng sau
    if has_rear_collar:
        out_list.append(make_cube([-(w_b * 0.42), base_y - h_b * 0.42, z0 + seg_l - 1.6],
                                  [w_b * 0.84, h_b * 0.84, 3.6], "DARK_JOINT",
                                  face_overrides={"down": "BELLY_CHEVRON"}))


# =============================================================================
# 3. XÂY DỰNG TOÀN BỘ MODEL LEVIATHAN 4 TAY VÂY LỬA (1.200–1.450 CUBES)
# =============================================================================
def build_leviathan_geo() -> tuple[dict, int, dict]:
    bones = []
    base_y = 26.5

    bones.append({"name": "root", "pivot": [0.0, 0.0, 0.0], "cubes": []})
    bones.append({"name": "body", "parent": "root", "pivot": [0.0, base_y, 0.0], "cubes": []})

    # -------------------------------------------------------------------------
    # A. PHẦN VAI NGỰC GÙ VẠM VỠ (`body_chest`) + VÂY LƯNG VAI (`dorsal_fin`) + KHE MANG XANH + 4 TAY!
    # -------------------------------------------------------------------------
    chest_c = [
        # Lõi ngực vạm vỡ đỡ 2 cặp tay
        make_cube([-11.8, base_y - 7.8, -21.5], [23.6, 15.6, 27.2], "SCALE_BANDED",
                  face_overrides={"up": "DORSAL_SEAM", "down": "BELLY_CHEVRON"}),
        # Gù lưng vai 3 bậc vạm vỡ dâng cao ngay sau gáy (khớp chuẩn silhouette mẫu!)
        make_cube([-10.4, base_y + 6.4, -20.6], [20.8, 5.4, 24.0], "SCALE_ARMOR",
                  face_overrides={"up": "DORSAL_SEAM"}),
        make_cube([-8.4, base_y + 10.6, -18.2], [16.8, 3.4, 19.6], "SCALE_ARMOR",
                  rot=[-9.0, 0.0, 0.0], pivot=[0.0, base_y + 11.6, -8.5],
                  face_overrides={"up": "DORSAL_SEAM"}),
        make_cube([-6.0, base_y + 12.4, -15.6], [12.0, 2.4, 15.2], "SCALE_BANDED",
                  rot=[-11.0, 0.0, 0.0], pivot=[0.0, base_y + 13.0, -8.0],
                  face_overrides={"up": "DORSAL_SEAM"}),
        # Khớp lồng sau ngực nối sang `body_mid`
        make_cube([-10.4, base_y - 7.4, 4.4], [20.8, 14.0, 4.8], "DARK_JOINT",
                  face_overrides={"down": "BELLY_CHEVRON"}),
    ]
    # 6 bậc vảy bụng chữ V kem ngà hồng nổi sắc dưới ngực
    for k in range(6):
        zk = -21.2 + k * 4.5
        wk = 18.8 + math.sin((k / 5.0) * math.pi) * 2.0
        chest_c.append(make_cube([-wk * 0.5, base_y - 9.4 - (k % 2) * 0.18, zk], [wk, 2.3, 5.1], "BELLY_CHEVRON"))

    # Khối cơ vai phồng rộng hai bên + giáp vai xếp tầng vát góc cạnh + hốc mang xanh lam phát sáng uốn cong!
    for side in (-1, 1):
        # Bả vai phồng rộng tạo chữ V lực lưỡng từ chính diện
        bx = 10.6 if side > 0 else -15.6
        chest_c.append(make_cube([bx, base_y - 3.6, -20.8], [5.0, 14.4, 21.6], "SCALE_ARMOR"))
        # 4 tầng tấm giáp vai vát xiên trước vây lưng (biến tấu bản sắc Abyssal Spire!)
        for k in range(4):
            px = (7.8 + k * 0.55) if side > 0 else -(14.2 + k * 0.55)
            chest_c.append(make_cube([px, base_y + 8.0 - k * 1.1, -21.6 + k * 6.2],
                                     [6.4, 4.2, 7.6], "SCALE_ARMOR",
                                     rot=[14.0 - k * 3.0, side * -11.0, side * (22.0 - k * 2.0)],
                                     pivot=[side * 11.4, base_y + 9.4 - k * 1.0, -18.0 + k * 6.2]))
        # 3 sườn vảy cơ lồng ngực hai bên dưới vai
        for k in range(3):
            rx = 11.4 if side > 0 else -13.2
            chest_c.append(make_cube([rx, base_y - 6.8, -19.0 + k * 8.2], [1.8, 5.6, 7.2], "SCALE_BANDED",
                                     rot=[0.0, 0.0, side * -14.0], pivot=[side * 12.2, base_y - 4.0, -15.4 + k * 8.2]))
        # KHE MANG PHÁT SÁNG XANH LAM SÂU (#2452FF -> #5E8BFF) uốn cong sau vai — đặt thoáng cao phía sau tay phụ để lộ cực rõ từ góc ngang!
        gx = 11.6 if side > 0 else -13.2
        chest_c.append(make_cube([gx, base_y - 1.2, +0.4], [1.6, 11.4, 10.8], "FLANK_GILL",
                                 rot=[0.0, side * -6.0, 0.0], pivot=[side * 12.4, base_y + 4.4, 5.6]))
        # Khung xương vòng bảo vệ quanh khe mang (5 cung giáp vát ôm khít hốc mang)
        for k, (gy_o, gz_o, gr_x) in enumerate(((5.6, 1.4, 22.0), (2.2, -0.2, 10.0), (-1.0, -0.4, 0.0), (-3.8, 1.6, -20.0))):
            fx = 12.0 if side > 0 else -13.8
            chest_c.append(make_cube([fx, base_y + gy_o, gz_o], [1.8, 2.0, 8.2], "SCALE_ARMOR",
                                     rot=[gr_x, side * -7.0, 0.0], pivot=[side * 12.8, base_y + gy_o + 1.0, gz_o + 4.0]))

    bones.append({
        "name": "body_chest",
        "parent": "body",
        "pivot": [0.0, base_y, -8.0],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": chest_c,
    })

    # Vây sống lưng vai (`dorsal_fin`): 5 nan xương vây dày vừa sắc nét + ổ gốc + màng lửa rực!
    dfc = []
    df_z = [-17.4, -12.2, -6.8, -1.4, +4.0]
    df_h = [9.2, 12.8, 14.0, 11.6, 8.2]
    df_roots = []
    df_mems = []
    for k in range(5):
        by = base_y + 13.0 - abs(k - 1.8) * 1.0
        p0 = np.array([0.0, by, df_z[k]], dtype=np.float64)
        p_tip = p0 + np.array([0.0, df_h[k], 1.4 + k * 0.35], dtype=np.float64)
        p_mem = p0 + np.array([0.0, df_h[k] * 0.74, 1.0 + k * 0.25], dtype=np.float64)
        df_roots.append(p0)
        df_mems.append(p_mem)
        # Ổ gốc gai sống lưng + nan xương dày vừa 4 đốt thuôn
        dfc.append(make_cube([-1.2, by - 0.8, df_z[k] - 1.2], [2.4, 2.2, 2.6], "SCALE_ARMOR"))
        add_chain_rod(dfc, p0, p_tip, 1.65, 0.65, 4, "FIN_RAY", overlap=0.24)
    for k in range(4):
        add_vertical_fin_strip(dfc, df_roots[k], df_roots[k + 1], df_mems[k], df_mems[k + 1],
                               mat="SMALL_FIN_MEM", thick=0.32, strips=4)
    bones.append({
        "name": "dorsal_fin",
        "parent": "body_chest",
        "pivot": [0.0, base_y + 12.6, -7.0],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": dfc,
    })

    # Cổ & Đầu thủy quái gắn trước ngực
    mouth_model_pos = build_head_assembly(bones, [0.0, base_y + 0.6, -21.2])

    # -------------------------------------------------------------------------
    # B. ĐÚNG 2 CẶP TAY VUỐT DÀI (4 TAY — TÁCH RÕ PHOM 5 GÓC MẪU!)
    #    - Cặp tay trước/dưới (`arm_front_l/r`): vạm vỡ dài sải sâu xuống sát đất, vây cẳng tay rực sau lưng khuỷu!
    #    - Cặp tay sau/ngoài (`arm_rear_l/r`): dang RỘNG ra hai bên (X = ±26..28) và chếch chéo xuống-sau, cực dễ thấy từ cả 4 góc!
    # -------------------------------------------------------------------------
    build_arm_assembly(
        bones, "arm_front_l", "body_chest", +1,
        shoulder=[+12.8, base_y - 0.6, -15.8],
        elbow=[+19.4, base_y - 10.6, -11.0],
        wrist=[+16.6, base_y - 25.4, -14.2],
        scale_f=1.08,
        fin_dir=(0.24, 0.14, 0.96),
        hand_inward=1.12,
    )
    build_arm_assembly(
        bones, "arm_front_r", "body_chest", -1,
        shoulder=[-12.8, base_y - 0.6, -15.8],
        elbow=[-19.4, base_y - 10.6, -11.0],
        wrist=[-16.6, base_y - 25.4, -14.2],
        scale_f=1.08,
        fin_dir=(0.24, 0.14, 0.96),
        hand_inward=1.12,
    )
    build_arm_assembly(
        bones, "arm_rear_l", "body_chest", +1,
        shoulder=[+13.8, base_y + 4.4, -7.6],
        elbow=[+26.8, base_y - 3.6, +0.8],
        wrist=[+23.8, base_y - 15.8, +9.8],
        scale_f=0.90,
        fin_dir=(0.35, 0.68, 0.64),
        hand_inward=0.92,
    )
    build_arm_assembly(
        bones, "arm_rear_r", "body_chest", -1,
        shoulder=[-13.8, base_y + 4.4, -7.6],
        elbow=[-26.8, base_y - 3.6, +0.8],
        wrist=[-23.8, base_y - 15.8, +9.8],
        scale_f=0.90,
        fin_dir=(0.35, 0.68, 0.64),
        hand_inward=0.92,
    )

    # -------------------------------------------------------------------------
    # C. THÂN GIỮA (`body_mid`) & THÂN SAU (`body_rear`) — Điêu khắc 8 cạnh mượt + vảy bụng xếp lớp!
    # -------------------------------------------------------------------------
    mid_c = []
    add_sculpted_trunk_segment(mid_c, base_y, 5.5, 18.0, 19.8, 14.0, 17.8, 12.6, has_rear_collar=True, scute_count=4)
    bones.append({
        "name": "body_mid",
        "parent": "body",
        "pivot": [0.0, base_y, 6.0],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": mid_c,
    })

    rear_c = []
    add_sculpted_trunk_segment(rear_c, base_y, 23.2, 16.2, 17.6, 12.4, 15.2, 10.8, has_rear_collar=True, scute_count=4)
    bones.append({
        "name": "body_rear",
        "parent": "body_mid",
        "pivot": [0.0, base_y, 23.5],
        "rotation": [0.0, 0.0, 0.0],
        "cubes": rear_c,
    })

    # -------------------------------------------------------------------------
    # D. 8 ĐỐT ĐUÔI THON MƯỢT (`tail_1..tail_8`) + VÂY ĐUÔI LÁ LỬA MỎNG LIỀN MẠCH (`tail_fin_1..7_l/r`)
    #    12 cặp nan xương vây xám lam rõ nét + màng alpha mỏng chuyển đỏ thẫm -> cam cháy -> viền vàng!
    # -------------------------------------------------------------------------
    # (length_z, w_a, h_a, w_b, h_b, fin_span_a, fin_span_b, rays_in_seg)
    tail_specs = [
        (13.0, 14.8, 10.6, 13.0,  9.4, 12.0, 19.5, 2),  # tail_1: mở rộng lá lửa sớm sau hông
        (13.0, 12.8,  9.3, 11.2,  8.1, 19.5, 25.0, 2),  # tail_2: phình rộng mạnh
        (13.0, 11.0,  8.0,  9.4,  6.9, 25.0, 26.2, 2),  # tail_3: rộng cực đại lá lửa!
        (12.5,  9.2,  6.8,  7.8,  5.8, 26.2, 22.8, 2),  # tail_4: thuôn đầy
        (12.0,  7.6,  5.7,  6.2,  4.8, 22.8, 17.4, 2),  # tail_5: thuôn đều mượt
        (11.5,  6.0,  4.7,  4.8,  3.9, 17.4, 11.6, 1),  # tail_6: thuôn gọn sắc
        (11.0,  4.6,  3.8,  3.2,  2.8, 11.6,  5.6, 1),  # tail_7: khép cánh lá lửa vào chóp đuôi (tổng 12 cặp nan!)
        (16.0,  3.0,  2.6,  1.6,  1.6,  0.0,  0.0, 0),  # tail_8: chóp đuôi roi dài nhọn + lưỡi vây xanh ngọc phát sáng!
    ]

    cur_z = 39.2
    prev_tail = "body_rear"
    for ti, (seg_l, wa, ha, wb, hb, f_a, f_b, n_rays) in enumerate(tail_specs):
        idx = ti + 1
        tc = []
        add_sculpted_trunk_segment(tc, base_y, cur_z, seg_l, wa, ha, wb, hb,
                                   has_rear_collar=(ti < 7), scute_count=(3 if ti < 6 else 2))

        locators = None
        if ti == 7:
            # 3 đoạn thuôn nhọn thành roi mảnh dài đặc trưng của mẫu
            tc.append(make_cube([-0.98, base_y - 0.98, cur_z + seg_l - 0.4],
                                [1.96, 1.96, 9.0], "SCALE_ARMOR",
                                face_overrides={"up": "DORSAL_GLOW", "down": "BELLY_CHEVRON"}))
            tc.append(make_cube([-0.68, base_y - 0.68, cur_z + seg_l + 8.0],
                                [1.36, 1.36, 7.6], "SCALE_ARMOR",
                                face_overrides={"up": "DORSAL_GLOW"}))
            tc.append(make_cube([-0.45, base_y - 0.45, cur_z + seg_l + 15.0],
                                [0.90, 0.90, 6.2], "DARK_JOINT"))
            # Lưỡi vây tinh thể mảnh hai bên chóp roi phát sáng xanh ngọc (biến tấu bản sắc Abyssal Spire!)
            for side in (-1, 1):
                ox = 0.55 if side > 0 else -4.45
                tc.append(make_cube([ox, base_y - 0.18, cur_z + seg_l + 4.8],
                                    [3.9, 0.36, 14.0], "TIP_BLADE_GLOW",
                                    rot=[0.0, side * -8.0, 0.0],
                                    pivot=[side * 0.6, base_y, cur_z + seg_l + 11.5],
                                    pad=0, transparent_edges=("north", "south", "east", "west")))
            locators = {"tail_tip_locator": [0.0, round(base_y, 3), round(cur_z + seg_l + 20.5, 3)]}

        tname = f"tail_{idx}"
        tbone = {
            "name": tname,
            "parent": prev_tail,
            "pivot": [0.0, round(base_y, 3), round(cur_z, 3)],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": tc,
        }
        if locators:
            tbone["locators"] = locators
        bones.append(tbone)

        # 7. VÂY ĐUÔI LÁ LỬA MỎNG LIỀN MẠCH (`tail_fin_i_l`, `tail_fin_i_r`) gắn riêng từng đốt `tail_1..7`!
        if n_rays > 0:
            w_avg = 0.5 * (wa + wb)
            for side, sfx in ((+1, "l"), (-1, "r")):
                fc = []
                ra = [side * (wa * 0.44), base_y + 0.25, cur_z - 1.2]
                rb = [side * (wb * 0.44), base_y + 0.25, cur_z + seg_l + 1.2]
                ta = [side * (wa * 0.44 + f_a * 0.84), base_y + 0.05, cur_z + 1.8]
                tb = [side * (wb * 0.44 + f_b * 0.84), base_y + 0.05, cur_z + seg_l + 4.2]
                add_horizontal_fin_sheet(fc, ra, rb, ta, tb, mat="TAIL_FIN_MEM", thick_y=0.28, strips=4)

                # Các nan xương vây XÁM LAM RÕ NÉT (1.45 -> 0.82 px, 5 đốt thuôn + ổ gốc 2 lớp!) nhô vượt mép màng ~18-22%!
                for rk in range(n_rays):
                    frac = (rk + 0.5) / float(n_rays)
                    span_r = f_a + (f_b - f_a) * frac
                    w_loc = wa + (wb - wa) * frac
                    z_r = cur_z + seg_l * frac
                    r_start = [side * (w_loc * 0.40), base_y + 0.35, z_r]
                    r_end   = [side * (w_loc * 0.40 + span_r * 1.05), base_y + 0.05, z_r + 3.4]
                    fc.append(make_cube([r_start[0] - 0.9, r_start[1] - 0.7, r_start[2] - 1.1],
                                        [1.8, 1.4, 2.2], "SCALE_ARMOR"))
                    fc.append(make_cube([r_start[0] + side * 0.8 - 0.7, r_start[1] - 0.5, r_start[2] - 0.9],
                                        [1.4, 1.0, 1.8], "DARK_JOINT"))
                    add_chain_rod(fc, r_start, r_end, 1.48, 0.84, 5, "FIN_RAY", overlap=0.22, aspect_z=0.58)

                bones.append({
                    "name": f"tail_fin_{idx}_{sfx}",
                    "parent": tname,
                    "pivot": [round(side * w_avg * 0.44, 3), round(base_y + 0.25, 3), round(cur_z + seg_l * 0.5, 3)],
                    "rotation": [0.0, 0.0, 0.0],
                    "cubes": fc,
                })

        cur_z += seg_l
        prev_tail = tname

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.leviathan",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 24,
                    "visible_bounds_height": 12,
                    "visible_bounds_offset": [0, 3.0, 2.0],
                },
                "bones": bones,
            }
        ],
    }

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    out_path = ASSETS / "geo" / "entity" / "leviathan.geo.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)

    meta = {
        "bones": len(bones),
        "cubes": total_cubes,
        "mouth_model_pos": mouth_model_pos,
        "tail_tip_model_pos": [0.0, round(base_y, 2), round(cur_z + 18.0, 2)],
    }
    print(f"GEO_OK: {out_path} -> {len(bones)} bones, {total_cubes} cubes")
    print(f"MOUTH_LOCATOR (model space): {mouth_model_pos} | TAIL_TIP: {meta['tail_tip_model_pos']}")
    return geo, total_cubes, meta


# =============================================================================
# 4. SINH TEXTURE 512x512 (NEAREST PIXEL-ART BIỂN SÂU + VÂY LỬA ALPHA + GLOWMASK FULLBRIGHT P1/P2)
# =============================================================================
def _hash2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def paint_scale_banded(diff, glow, rect, enraged: bool, armor_mode: bool = False):
    """Vảy lưng/đầu/tay xanh lam đá phiến–biển sâu (#1B2936..#7FA8B0) sọc vằn ngang tương phản sắc nét như mẫu."""
    x0, y0, w, h = rect
    pal = [
        (26, 40, 54),    # #1A2836 — rãnh tối biển sâu
        (44, 67, 84),    # #2C4354 — xanh đá phiến tối
        (72, 106, 120),  # #486A78 — xanh biển trung tính
        (104, 146, 158), # #68929E — dải vằn sáng nổi bật
        (134, 178, 186), # #86B2BA — gờ vảy bắt sáng
    ]
    if armor_mode:
        pal = [(max(18, r - 10), max(26, g - 12), max(36, b - 12)) for (r, g, b) in pal]

    cell = 4
    for gy in range(h // cell):
        # Dải sọc vằn ngang tương phản rõ ràng (xen kẽ vệt sáng xanh thép & rãnh xanh đá phiến tối!)
        band_phase = math.sin(gy * 0.48)
        for gx in range(w // cell):
            wave_local = 0.5 + 0.5 * math.sin((gy + math.sin(gx * 0.28) * 1.1) * 0.52)
            noise = _hash2(gx, gy, 19 if not armor_mode else 43)
            v = 0.62 * wave_local + 0.24 * (0.5 + 0.5 * band_phase) + 0.14 * noise

            if v < 0.24:
                c = pal[0]
            elif v < 0.45:
                c = pal[1]
            elif v < 0.64:
                c = pal[2]
            elif v < 0.85:
                c = pal[3]
            else:
                c = pal[4]

            px0, py0 = x0 + gx * cell, y0 + gy * cell
            for dy in range(cell):
                for dx in range(cell):
                    px, py = px0 + dx, py0 + dy
                    ao = 1.0
                    if (px - x0) < 3 or (x0 + w - 1 - px) < 3 or (py - y0) < 3 or (y0 + h - 1 - py) < 3:
                        ao = 0.82
                    elif dy == 0 and (gy % 2 == 0):
                        ao = 0.92
                    diff[py, px] = (
                        int(np.clip(c[0] * ao, 14, 210)),
                        int(np.clip(c[1] * ao, 20, 224)),
                        int(np.clip(c[2] * ao, 26, 232)),
                        255,
                    )

    # Phase 2 Enraged: vết nứt năng lượng mảnh lan nhẹ trên giáp vai & hàm!
    if enraged and armor_mode:
        cracks = [(0.14, 0.16, 0.84, 0.42), (0.20, 0.72, 0.88, 0.56)]
        for fx0, fy0, fx1, fy1 in cracks:
            for s in range(36):
                t = s / 35.0
                cx = int(np.clip((fx0 + (fx1 - fx0) * t + math.sin(t * 14.0) * 0.04) * (w - 4) + 2, 2, w - 3))
                cy = int(np.clip((fy0 + (fy1 - fy0) * t + math.cos(t * 11.0) * 0.04) * (h - 4) + 2, 2, h - 3))
                diff[y0 + cy, x0 + cx] = (232, 255, 255, 255)
                glow[y0 + cy, x0 + cx] = (232, 255, 255, 255)
                diff[y0 + cy + 1, x0 + cx] = (92, 232, 255, 255)
                glow[y0 + cy + 1, x0 + cx] = (92, 232, 255, 255)


def paint_dorsal_glow(diff, glow, rect, enraged: bool):
    """Sống lưng xanh đá phiến có DẢI ĐỐM/VỆT PHÁT QUANG XANH NGỌC NHẠT (#9CFBF0 -> #5AE0D8) sắc nét dọc giữa lưng!"""
    paint_scale_banded(diff, glow, rect, enraged, armor_mode=False)
    x0, y0, w, h = rect
    cell = 4
    for gy in range(h // cell):
        pulse = 0.5 + 0.5 * math.sin(gy * 0.82)
        for gx in range(w // cell):
            nx = abs((gx + 0.5) / (w / cell) * 2.0 - 1.0)
            thresh = 0.30 + 0.18 * pulse + (_hash2(gx, gy, 131) - 0.5) * 0.12
            if nx < thresh and (gy % 5 != 4):
                if not enraged:
                    c = (156, 251, 240) if nx < thresh * 0.62 else (90, 224, 216)
                    g_mult = 0.95 if nx < thresh * 0.62 else 0.76
                else:
                    c = (238, 255, 255) if nx < thresh * 0.68 else (108, 246, 255)
                    g_mult = 1.0
                px0, py0 = x0 + gx * cell, y0 + gy * cell
                for dy in range(cell):
                    for dx in range(cell):
                        diff[py0 + dy, px0 + dx] = (*c, 255)
                        glow[py0 + dy, px0 + dx] = (int(c[0] * g_mult), int(c[1] * g_mult), int(c[2] * g_mult), 255)


def paint_flank_gill(diff, glow, rect, enraged: bool):
    """Sườn sau vai có KHE MANG PHÁT SÁNG XANH LAM SÂU (#2054FF -> #68A4FF) uốn cong bán nguyệt nổi bật!"""
    paint_scale_banded(diff, glow, rect, enraged, armor_mode=True)
    x0, y0, w, h = rect
    cx, cy = w * 0.56, h * 0.50
    for y in range(h):
        for x in range(w):
            dx = (x - cx) / (w * 0.38)
            dy = (y - cy) / (h * 0.40)
            r = math.hypot(dx, dy)
            in_arc = (dx < 0.38) and (abs(r - 0.76) < 0.16 or abs(r - 0.38) < 0.12)
            if in_arc:
                core = abs(r - 0.76) < 0.07 or abs(r - 0.38) < 0.06
                if not enraged:
                    c = (104, 164, 255) if core else (32, 84, 255)
                else:
                    c = (224, 250, 255) if core else (76, 174, 255)
                diff[y0 + y, x0 + x] = (*c, 255)
                glow[y0 + y, x0 + x] = (*c, 255)


def paint_belly_chevron(diff, glow, rect):
    """Bụng vảy xếp chữ V màu kem ngà ánh hồng sáng (#F4DFDF -> #DFC2C8 -> #BC98A2) sắc nét như góc nhìn dưới lên mẫu!"""
    x0, y0, w, h = rect
    cell = 4
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            nx = abs((x + 0.5) / w * 2.0 - 1.0)
            chev_y = (y + int(nx * 26.0)) % 18
            if chev_y < 3:
                c = (156, 126, 136)  # Khe vảy chữ V tối thanh mảnh
            elif nx < 0.42:
                c = (244, 223, 223)  # #F4DFDF kem ngà ánh hồng sáng rõ
            elif nx < 0.76:
                c = (223, 194, 200)  # #DFC2C8 hồng ngà trung tính
            else:
                c = (188, 152, 162)  # #BC98A2 bo bóng mép sườn bụng
            for dy in range(cell):
                for dx in range(cell):
                    if y + dy < h and x + dx < w:
                        diff[y0 + y + dy, x0 + x + dx] = (*c, 255)


def paint_tail_fin_membrane(diff, glow, rect, enraged: bool):
    """
    Màng vây đuôi lá lửa alpha-cutout (nửa trên cho TRÁI +X, nửa dưới cho PHẢI -X):
    - Đỏ thẫm rực #B81E14 -> Đỏ cam xoáy #E64614 -> Cam cháy #FF7C18 -> Mép răng cưa vàng rực #FFD23C!
    - 20–30% biến tấu + vân xoáy lửa đỏ thẫm giống màng vây rực trong ảnh top-down & bottom-up mẫu!
    """
    x0, y0, w, h = rect
    diff[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    glow[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    half_h = h // 2
    cell = 4

    for half_idx, flip_x in ((0, False), (1, True)):
        hy0 = y0 + half_idx * half_h
        for y in range(0, half_h, cell):
            v_along = y / float(half_h)
            for x in range(0, w, cell):
                if half_idx == 1 and x >= w - 8 and y >= half_h - 8:
                    continue
                u_out = (1.0 - (x + cell * 0.5) / float(w)) if flip_x else ((x + cell * 0.5) / float(w))

                # Mép ngoài cắt răng cưa lửa mảnh sắc nét
                jag_a = abs(((v_along * 7.0) % 1.0) - 0.5) * 2.0
                jag_b = abs(((v_along * 15.0 + 0.3) % 1.0) - 0.5) * 2.0
                notch = 0.80 + 0.14 * jag_a + 0.05 * jag_b
                if u_out > notch:
                    continue

                # Vân xoáy lửa & gân phân nhánh vực thẳm (spiral abyssal flame eddies!)
                swirl_u = (u_out - 0.44) * 3.8
                swirl_v = (((v_along * 4.0) % 1.0) - 0.5) * 3.2
                sr = math.hypot(swirl_u, swirl_v)
                sang = math.atan2(swirl_v, swirl_u)
                is_swirl = (0.28 < sr < 1.35) and (abs(math.sin(sr * 5.2 - sang)) > 0.86) and (0.16 < u_out < 0.72)
                branch = abs(((v_along * 5.0 + u_out * 1.5) % 1.0) - 0.5)
                is_vein = (branch < 0.07) and u_out > 0.18

                if u_out < 0.24:
                    c = (176, 28, 20)    # #B01C14 đỏ thẫm rực sát thân
                    g_s = 0.24 if not enraged else 0.52
                elif u_out < 0.52:
                    c = (232, 68, 22)    # #E84416 đỏ cam cháy rực
                    g_s = 0.50 if not enraged else 0.78
                elif u_out < notch - 0.13:
                    c = (255, 128, 26)   # #FF801A cam rực
                    g_s = 0.74 if not enraged else 0.94
                else:
                    c = (255, 214, 64)   # #FFD640 viền ngoài vàng sáng rực!
                    g_s = 0.95 if not enraged else 1.00

                if is_swirl:
                    # Gờ xoáy đỏ thẫm tương phản sắc giống ảnh mẫu!
                    c = (max(95, int(c[0] * 0.62)), max(14, int(c[1] * 0.40)), max(12, int(c[2] * 0.45)))
                    g_s = max(0.10, g_s * 0.55)
                elif is_vein:
                    c = (min(255, int(c[0] * 1.12 + 16)), min(255, int(c[1] * 1.18 + 20)), min(255, int(c[2] * 1.12 + 10)))
                    g_s = min(1.0, g_s + 0.15)

                for dy in range(cell):
                    for dx in range(cell):
                        if y + dy < half_h and x + dx < w:
                            diff[hy0 + y + dy, x0 + x + dx] = (*c, 255)
                            if g_s > 0.05:
                                glow[hy0 + y + dy, x0 + x + dx] = (
                                    int(c[0] * g_s), int(c[1] * g_s), int(c[2] * g_s), 255
                                )


def paint_small_fin_membrane(diff, glow, rect, enraged: bool):
    """Màng vây lửa mỏng alpha-cutout cho vây lưng vai, 4 cẳng tay, vây má & vây cằm (v=0 mép vàng -> v=1 gốc đỏ thẫm)."""
    x0, y0, w, h = rect
    diff[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    glow[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    cell = 4
    for y in range(0, h, cell):
        v_norm = y / float(h)
        for x in range(0, w, cell):
            if x >= w - 8 and y >= h - 8:
                continue
            u_norm = x / float(w)
            jag = 0.20 * (1.0 - abs(((u_norm * 6.0) % 1.0) - 0.5) * 2.0)
            if v_norm < jag:
                continue
            u_out = 1.0 - v_norm
            if u_out < 0.25:
                c = (176, 28, 20)
                g_s = 0.26 if not enraged else 0.52
            elif u_out < 0.54:
                c = (232, 68, 22)
                g_s = 0.52 if not enraged else 0.78
            elif u_out < 0.78:
                c = (255, 130, 26)
                g_s = 0.76 if not enraged else 0.94
            else:
                c = (255, 214, 64)
                g_s = 0.95 if not enraged else 1.0
            for dy in range(cell):
                for dx in range(cell):
                    if y + dy < h and x + dx < w:
                        diff[y0 + y + dy, x0 + x + dx] = (*c, 255)
                        glow[y0 + y + dy, x0 + x + dx] = (int(c[0] * g_s), int(c[1] * g_s), int(c[2] * g_s), 255)


def paint_tip_blade_glow(diff, glow, rect, enraged: bool):
    """Lưỡi vây chóp đuôi phát sáng xanh ngọc (alpha-cutout hình mũi giáo sắc gọn)."""
    x0, y0, w, h = rect
    diff[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    glow[y0:y0 + h, x0:x0 + w] = (0, 0, 0, 0)
    cell = 4
    for y in range(0, h, cell):
        v = y / float(h)
        profile = math.sin(v * math.pi)
        for x in range(0, w, cell):
            if x >= w - 8 and y >= h - 8:
                continue
            u = x / float(w)
            if u > 0.15 + 0.82 * profile:
                continue
            c = (225, 255, 255) if (enraged or u < 0.35) else (112, 246, 236)
            for dy in range(cell):
                for dx in range(cell):
                    if y + dy < h and x + dx < w:
                        diff[y0 + y + dy, x0 + x + dx] = (*c, 255)
                        glow[y0 + y + dy, x0 + x + dx] = (*c, 255)


def paint_solid_grad(diff, glow, rect, col_a, col_b, glow_strength=0.0, vertical=True):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            t = (y / max(1.0, h - 1.0)) if vertical else (x / max(1.0, w - 1.0))
            j = (_hash2(x // 4, y // 4, 601) - 0.5) * 0.10
            f = np.clip(t + j, 0.0, 1.0)
            r = int(np.clip(col_a[0] * (1 - f) + col_b[0] * f, 0, 255))
            g = int(np.clip(col_a[1] * (1 - f) + col_b[1] * f, 0, 255))
            b = int(np.clip(col_a[2] * (1 - f) + col_b[2] * f, 0, 255))
            diff[y0 + y, x0 + x] = (r, g, b, 255)
            if glow_strength > 0.01:
                glow[y0 + y, x0 + x] = (int(r * glow_strength), int(g * glow_strength), int(b * glow_strength), 255)


def paint_dorsal_seam(diff, glow, rect, enraged: bool):
    """Lưng vai gù xanh đá phiến có rãnh giữa ánh cam ấm mảnh tinh tế (như ảnh góc trên mẫu!)."""
    paint_scale_banded(diff, glow, rect, enraged, armor_mode=True)
    x0, y0, w, h = rect
    mid = w // 2
    for y in range(h):
        for dx in range(-3, 4):
            c = (255, 138, 42) if abs(dx) <= 1 else (180, 62, 22)
            diff[y0 + y, x0 + mid + dx] = (*c, 255)
            g_s = 0.72 if not enraged else 1.0
            glow[y0 + y, x0 + mid + dx] = (int(c[0] * g_s), int(c[1] * g_s), int(c[2] * g_s), 255)


def build_textures() -> None:
    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)

    for enraged, base_name in ((False, "leviathan"), (True, "leviathan_enraged")):
        diff = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
        glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

        paint_scale_banded(diff, glow, MAT["SCALE_BANDED"], enraged, armor_mode=False)
        paint_scale_banded(diff, glow, MAT["SCALE_ARMOR"], enraged, armor_mode=True)
        paint_scale_banded(diff, glow, MAT["ARM_BICEP"], enraged, armor_mode=False)
        paint_belly_chevron(diff, glow, MAT["BELLY_CHEVRON"])
        paint_tail_fin_membrane(diff, glow, MAT["TAIL_FIN_MEM"], enraged)
        paint_dorsal_glow(diff, glow, MAT["DORSAL_GLOW"], enraged)
        paint_flank_gill(diff, glow, MAT["FLANK_GILL"], enraged)
        paint_small_fin_membrane(diff, glow, MAT["SMALL_FIN_MEM"], enraged)
        paint_tip_blade_glow(diff, glow, MAT["TIP_BLADE_GLOW"], enraged)
        paint_dorsal_seam(diff, glow, MAT["DORSAL_SEAM"], enraged)

        # Nan xương vây mảnh xám lam (#7A8B9C -> #3E4B5A)
        paint_solid_grad(diff, glow, MAT["FIN_RAY"], (122, 139, 156), (62, 75, 90), glow_strength=0.0)
        # Nướu & khoang miệng tím hồng sẫm (#7E3F6B -> #5A274A)
        paint_solid_grad(diff, glow, MAT["GUM_MOUTH"], (126, 63, 107), (90, 39, 74), glow_strength=0.0)
        paint_solid_grad(diff, glow, MAT["TONGUE_PLUM"], (152, 74, 118), (98, 42, 78), glow_strength=0.0)
        # Mắt xiên phát sáng vàng-cam (#FFAA18, lõi #FFF0A8)
        paint_solid_grad(diff, glow, MAT["EYE_AMBER"],
                         (255, 244, 185) if enraged else (255, 240, 168),
                         (255, 145, 20) if enraged else (255, 170, 24),
                         glow_strength=1.0, vertical=False)
        # 2 răng nanh ngà lớn (#F4E8CC -> #D8C7A2)
        paint_solid_grad(diff, glow, MAT["TUSK_IVORY"], (244, 232, 204), (212, 195, 158), glow_strength=0.0)
        # Hàng răng trắng đều
        paint_solid_grad(diff, glow, MAT["TOOTH_WHITE"], (246, 244, 238), (212, 208, 198), glow_strength=0.0)
        # Móng vuốt đen obsidian bóng sắc
        paint_solid_grad(diff, glow, MAT["CLAW_BLACK"], (48, 56, 70), (16, 20, 28), glow_strength=0.0)
        # Khớp lồng tối sâu
        paint_solid_grad(diff, glow, MAT["DARK_JOINT"], (32, 46, 58), (18, 26, 34), glow_strength=0.0)

        Image.fromarray(diff, "RGBA").save(tex_dir / f"{base_name}.png")
        Image.fromarray(glow, "RGBA").save(tex_dir / f"{base_name}_glowmask.png")

    print(f"TEX_OK: 4 textures ({TEX_W}x{TEX_H} RGBA + Glowmask P1/P2) saved to {tex_dir}")


# =============================================================================
# 5. SINH 12 ANIMATION MƯỢT (CATMULLROM / EASE-IN-OUT) ĐỒNG BỘ 1:1 VỚI LeviathanAttackTiming.java
#    Sóng uốn thân truyền pha + phối hợp 4 tay tự nhiên + 0 góc > 110° chống xoay đầu tuyệt đối!
# =============================================================================
def kf(vec, easing="easeInOutSine"):
    return {"vector": [round(float(v), 2) for v in vec], "easing": easing}


def tkey(ticks: int) -> str:
    return f"{ticks / 20.0:.2f}"


def add_rot(ba: dict, bone: str, tick: int, vec, easing="easeInOutSine"):
    ba.setdefault(bone, {}).setdefault("rotation", {})[tkey(tick)] = kf(vec, easing)


def add_pos(ba: dict, bone: str, tick: int, vec, easing="easeInOutSine"):
    ba.setdefault(bone, {}).setdefault("position", {})[tkey(tick)] = kf(vec, easing)


def frills_flare(ba: dict, tick: int, cheek_deg: float, chin_deg: float, easing="easeInOutSine"):
    add_rot(ba, "frill_cheek_l", tick, [0.0, +cheek_deg, 0.0], easing)
    add_rot(ba, "frill_cheek_r", tick, [0.0, -cheek_deg, 0.0], easing)
    add_rot(ba, "frill_chin", tick, [chin_deg, 0.0, 0.0], easing)


def four_arms_pose(ba: dict, tick: int, front_l, front_r, rear_l, rear_r,
                   fore_fl=None, fore_fr=None, fore_rl=None, fore_rr=None, easing="easeInOutSine"):
    add_rot(ba, "arm_front_l", tick, front_l, easing)
    add_rot(ba, "arm_front_r", tick, front_r, easing)
    add_rot(ba, "arm_rear_l", tick, rear_l, easing)
    add_rot(ba, "arm_rear_r", tick, rear_r, easing)
    if fore_fl is not None: add_rot(ba, "arm_front_l_forearm", tick, fore_fl, easing)
    if fore_fr is not None: add_rot(ba, "arm_front_r_forearm", tick, fore_fr, easing)
    if fore_rl is not None: add_rot(ba, "arm_rear_l_forearm", tick, fore_rl, easing)
    if fore_rr is not None: add_rot(ba, "arm_rear_r_forearm", tick, fore_rr, easing)


def build_animations() -> dict:
    anims = {}

    # -------------------------------------------------------------------------
    # 1. IDLE (3.20s = 64 ticks, loop) — Sóng uốn thân truyền pha mượt + 4 tay quạt chậm nặng + thở mở nhẹ hàm & vây mang
    # -------------------------------------------------------------------------
    b = {}
    for t in (0, 8, 16, 24, 32, 40, 48, 56, 64):
        ph = (t / 64.0) * math.tau
        br = math.sin(ph)
        add_pos(b, "body", t, [0.0, br * 1.4, 0.0], "catmullrom")
        add_rot(b, "body_chest", t, [br * 2.5, math.sin(ph) * 4.0, math.cos(ph) * 2.0], "catmullrom")
        add_rot(b, "body_mid", t, [-br * 1.8, math.sin(ph - 0.55) * 6.5, 0.0], "catmullrom")
        add_rot(b, "body_rear", t, [-br * 1.4, math.sin(ph - 1.10) * 8.0, 0.0], "catmullrom")
        add_rot(b, "jaw", t, [3.0 + br * 5.0, 0.0, 0.0], "catmullrom")
        frills_flare(b, t, 4.0 + br * 6.0, br * 5.0, "catmullrom")

        # 2 tay chính quạt chậm nặng, 2 tay phụ co duỗi lệch pha mềm!
        four_arms_pose(
            b, t,
            [br * 8.0, math.cos(ph) * 6.0, -4.0 - br * 5.0],
            [br * 8.0, -math.cos(ph) * 6.0, 4.0 + br * 5.0],
            [math.sin(ph - 1.0) * 10.0, math.cos(ph - 1.0) * 5.0, -3.0 - math.sin(ph - 1.0) * 4.0],
            [math.sin(ph - 1.0) * 10.0, -math.cos(ph - 1.0) * 5.0, 3.0 + math.sin(ph - 1.0) * 4.0],
            [-6.0 + br * 6.0, 0.0, 0.0],
            [-6.0 + br * 6.0, 0.0, 0.0],
            [-8.0 + math.cos(ph) * 6.0, 0.0, 0.0],
            [-8.0 + math.cos(ph) * 6.0, 0.0, 0.0],
            "catmullrom",
        )
        # Sóng truyền pha mượt dọc 8 đốt đuôi + vây lá lửa vẫy mềm mại!
        for ti in range(1, 9):
            w_y = math.sin(ph - 1.4 - ti * 0.52) * (6.0 + ti * 1.2)
            w_x = math.cos(ph - 1.0 - ti * 0.45) * (2.5 + ti * 0.45)
            add_rot(b, f"tail_{ti}", t, [w_x, w_y, 0.0], "catmullrom")
            if ti <= 7:
                f_roll = math.sin(ph - 1.8 - ti * 0.58) * 10.0
                add_rot(b, f"tail_fin_{ti}_l", t, [0.0, 0.0, +f_roll], "catmullrom")
                add_rot(b, f"tail_fin_{ti}_r", t, [0.0, 0.0, -f_roll], "catmullrom")
    anims["animation.leviathan.idle"] = {"loop": True, "animation_length": 3.2, "bones": b}

    # -------------------------------------------------------------------------
    # 2. MOVE / SWIM (1.60s = 32 ticks, loop) — Sóng chữ S mạnh uyển chuyển như rồng biển đang bơi lượn!
    # -------------------------------------------------------------------------
    b = {}
    for t in (0, 4, 8, 12, 16, 20, 24, 28, 32):
        ph = (t / 32.0) * math.tau
        s = math.sin(ph)
        c = math.cos(ph)
        add_pos(b, "body", t, [s * 1.2, c * 1.6, 0.0], "catmullrom")
        add_rot(b, "body_chest", t, [4.0 + c * 4.0, s * 10.0, -s * 5.0], "catmullrom")
        add_rot(b, "body_mid", t, [c * 3.0, math.sin(ph - 0.65) * 13.0, 0.0], "catmullrom")
        add_rot(b, "body_rear", t, [c * 2.5, math.sin(ph - 1.30) * 15.0, 0.0], "catmullrom")
        add_rot(b, "jaw", t, [6.0 + abs(s) * 4.0, 0.0, 0.0], "catmullrom")
        frills_flare(b, t, 8.0 + c * 6.0, 4.0 + s * 4.0, "catmullrom")

        # 4 tay quạt bơi có quán tính (cặp trước ép xuôi lướt nước, cặp sau đẩy lệch pha)
        four_arms_pose(
            b, t,
            [18.0 + s * 16.0, 8.0 + c * 8.0, -10.0 - s * 8.0],
            [18.0 - s * 16.0, -8.0 - c * 8.0, 10.0 - s * 8.0],
            [22.0 + math.sin(ph - 1.2) * 14.0, 6.0, -8.0],
            [22.0 - math.sin(ph - 1.2) * 14.0, -6.0, 8.0],
            [-14.0 + c * 10.0, 0.0, 0.0],
            [-14.0 - c * 10.0, 0.0, 0.0],
            [-16.0 + s * 8.0, 0.0, 0.0],
            [-16.0 - s * 8.0, 0.0, 0.0],
            "catmullrom",
        )
        for ti in range(1, 9):
            w_y = math.sin(ph - 1.6 - ti * 0.56) * (10.5 + ti * 1.4)
            w_x = math.cos(ph - 1.2 - ti * 0.42) * (3.5 + ti * 0.5)
            add_rot(b, f"tail_{ti}", t, [w_x, w_y, 0.0], "catmullrom")
            if ti <= 7:
                f_roll = math.sin(ph - 2.0 - ti * 0.62) * 16.0
                add_rot(b, f"tail_fin_{ti}_l", t, [0.0, 0.0, +f_roll], "catmullrom")
                add_rot(b, f"tail_fin_{ti}_r", t, [0.0, 0.0, -f_roll], "catmullrom")
    anims["animation.leviathan.move"] = {"loop": True, "animation_length": 1.6, "bones": b}

    # -------------------------------------------------------------------------
    # 3. TAIL_SLAM (1.80s = 36 ticks) — Vặn hông uốn đuôi vây lửa lấy đà (0..14) -> Quất ngang cực mạnh (15..19, impact 16) -> Follow-through (20..36)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 14, [-8, -24, 6], "easeInCubic")
    add_rot(b, "body_chest", 17, [8, +26, -8], "easeOutExpo")
    add_rot(b, "body_chest", 24, [4, +14, -3], "easeOutSine")
    add_rot(b, "body_chest", 36, [0, 0, 0], "easeInOutSine")

    add_rot(b, "body_mid", 0,  [0, 0, 0])
    add_rot(b, "body_mid", 14, [0, +28, 0], "easeInCubic")
    add_rot(b, "body_mid", 17, [0, -34, 0], "easeOutExpo")
    add_rot(b, "body_mid", 25, [0, -18, 0], "easeOutSine")
    add_rot(b, "body_mid", 36, [0, 0, 0], "easeInOutSine")

    add_rot(b, "body_rear", 0,  [0, 0, 0])
    add_rot(b, "body_rear", 14, [0, +26, 0], "easeInCubic")
    add_rot(b, "body_rear", 18, [0, -38, 0], "easeOutExpo")
    add_rot(b, "body_rear", 26, [0, -20, 0], "easeOutSine")
    add_rot(b, "body_rear", 36, [0, 0, 0], "easeInOutSine")

    for ti in range(1, 9):
        add_rot(b, f"tail_{ti}", 0,  [0, 0, 0])
        add_rot(b, f"tail_{ti}", 14, [4, 18 + ti * 2.5, 0], "easeInCubic")
        add_rot(b, f"tail_{ti}", 18, [-6, -24 - ti * 3.0, 0], "easeOutExpo")
        add_rot(b, f"tail_{ti}", 26, [-2, -12 - ti * 1.5, 0], "easeOutSine")
        add_rot(b, f"tail_{ti}", 36, [0, 0, 0], "easeInOutSine")
    add_rot(b, "jaw", 0, [0, 0, 0])
    add_rot(b, "jaw", 14, [32, 0, 0], "easeInCubic")
    add_rot(b, "jaw", 20, [14, 0, 0], "easeOutCubic")
    add_rot(b, "jaw", 36, [0, 0, 0])
    frills_flare(b, 0, 0, 0)
    frills_flare(b, 14, 30, 22, "easeInCubic")
    frills_flare(b, 36, 0, 0)
    four_arms_pose(b, 0, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 14, [-12, -18, -22], [18, 12, 16], [10, 0, -14], [10, 0, 14], easing="easeInCubic")
    four_arms_pose(b, 18, [16, 14, -14], [-18, -14, 24], [-8, 0, -10], [-8, 0, 10], easing="easeOutExpo")
    four_arms_pose(b, 36, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    anims["animation.leviathan.tail_slam"] = {"loop": False, "animation_length": 1.8, "bones": b}

    # -------------------------------------------------------------------------
    # 4. BITE_CHARGE (2.10s = 42 ticks) — Gồng 4 tay ép sát sườn + xòe căng vây má/cằm + há to hàm lộ 2 nanh ngà (0..15) -> Phóng táp (16..32)!
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 14, [0, 2.5, 9.0], "easeInCubic")
    add_pos(b, "body", 18, [0, -1.5, -11.0], "easeOutExpo")
    add_pos(b, "body", 32, [0, -0.8, -6.0], "easeOutSine")
    add_pos(b, "body", 42, [0, 0, 0], "easeInOutSine")

    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 14, [-14, 0, 0], "easeInCubic")
    add_rot(b, "body_chest", 18, [12, 0, 0], "easeOutExpo")
    add_rot(b, "body_chest", 42, [0, 0, 0])

    add_rot(b, "neck", 0,  [0, 0, 0])
    add_rot(b, "neck", 14, [-16, 0, 0], "easeInCubic")
    add_rot(b, "neck", 18, [14, 0, 0], "easeOutExpo")
    add_rot(b, "neck", 42, [0, 0, 0])

    add_rot(b, "jaw", 0,  [0, 0, 0])
    add_rot(b, "jaw", 14, [48, 0, 0], "easeInCubic")  # Há rộng lộ 2 răng nanh ngà & khoang miệng tím hồng!
    add_rot(b, "jaw", 18, [-6, 0, 0], "easeOutExpo")  # Táp mạnh dứt khoát!
    add_rot(b, "jaw", 25, [40, 0, 0], "easeInCubic")
    add_rot(b, "jaw", 30, [-4, 0, 0], "easeOutExpo")
    add_rot(b, "jaw", 42, [0, 0, 0])

    frills_flare(b, 0, 0, 0)
    frills_flare(b, 14, 38, 30, "easeInCubic")
    frills_flare(b, 28, 28, 20, "easeOutSine")
    frills_flare(b, 42, 0, 0)

    # 4 tay thu ép sát sườn khi lao phóng rồi vồ mạnh về trước!
    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 14, [38, 22, 16], [38, -22, -16], [34, 16, 12], [34, -16, -12],
                   [-28, 0, 0], [-28, 0, 0], [-22, 0, 0], [-22, 0, 0], "easeInCubic")
    four_arms_pose(b, 19, [-34, -16, -18], [-34, 16, 18], [-22, -12, -12], [-22, 12, 12],
                   [12, 0, 0], [12, 0, 0], [8, 0, 0], [8, 0, 0], "easeOutExpo")
    four_arms_pose(b, 42, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    anims["animation.leviathan.bite_charge"] = {"loop": False, "animation_length": 2.1, "bones": b}

    # -------------------------------------------------------------------------
    # 5. ENERGY_BEAM / WATER_BEAM (2.80s = 56 ticks) — Ngửa cổ nạp (0..17), 4 tay dang thăng bằng, quét tia trái->phải (18..44)
    # -------------------------------------------------------------------------
    b = {}
    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 16, [-14, +14, 0], "easeInCubic")
    add_rot(b, "body_chest", 20, [6, +18, 0], "easeOutCubic")
    add_rot(b, "body_chest", 32, [8, 0, 0], "catmullrom")
    add_rot(b, "body_chest", 44, [6, -18, 0], "easeOutSine")
    add_rot(b, "body_chest", 56, [0, 0, 0], "easeInOutSine")

    add_rot(b, "neck", 0,  [0, 0, 0])
    add_rot(b, "neck", 16, [-16, +10, 0], "easeInCubic")
    add_rot(b, "neck", 20, [10, +14, 0], "easeOutCubic")
    add_rot(b, "neck", 44, [10, -14, 0], "easeInOutSine")
    add_rot(b, "neck", 56, [0, 0, 0])

    add_rot(b, "jaw", 0,  [0, 0, 0])
    add_rot(b, "jaw", 16, [34, 0, 0], "easeInCubic")
    add_rot(b, "jaw", 20, [50, 0, 0], "easeOutExpo")
    add_rot(b, "jaw", 44, [48, 0, 0], "linear")
    add_rot(b, "jaw", 56, [0, 0, 0])
    frills_flare(b, 0, 0, 0)
    frills_flare(b, 18, 40, 32, "easeOutExpo")
    frills_flare(b, 44, 36, 28, "linear")
    frills_flare(b, 56, 0, 0)

    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 18, [-14, -22, -34], [-14, 22, 34], [-8, -14, -24], [-8, 14, 24], easing="easeOutCubic")
    four_arms_pose(b, 44, [-10, -18, -30], [-10, 18, 30], [-6, -12, -20], [-6, 12, 20], easing="easeInOutSine")
    four_arms_pose(b, 56, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    anims["animation.leviathan.energy_beam"] = {"loop": False, "animation_length": 2.8, "bones": b}

    # -------------------------------------------------------------------------
    # 6. SUMMON_MINIONS (1.80s = 36 ticks) — Ngẩng cao nửa thân trước, 2 cặp tay dang mở triệu hồi (impact 16)
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 16, [0, 6.0, 0], "easeOutCubic")
    add_pos(b, "body", 36, [0, 0, 0], "easeInOutSine")
    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 16, [-24, 0, 0], "easeOutCubic")
    add_rot(b, "body_chest", 36, [0, 0, 0])
    add_rot(b, "jaw", 0,  [0, 0, 0])
    add_rot(b, "jaw", 16, [44, 0, 0], "easeOutBack")
    add_rot(b, "jaw", 36, [0, 0, 0])
    frills_flare(b, 0, 0, 0)
    frills_flare(b, 16, 36, 26, "easeOutBack")
    frills_flare(b, 36, 0, 0)
    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 16, [-26, -24, -40], [-26, 24, 40], [-18, -16, -32], [-18, 16, 32], easing="easeOutBack")
    four_arms_pose(b, 36, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    anims["animation.leviathan.summon_minions"] = {"loop": False, "animation_length": 1.8, "bones": b}

    # -------------------------------------------------------------------------
    # 7. WHIRLPOOL (Phase 2, 4.20s = 84 ticks) — Cuộn cong thân & đuôi xoáy khuấy nước liên tục (16..76)
    # -------------------------------------------------------------------------
    b = {}
    for t in (0, 14, 28, 42, 56, 70, 84):
        if t in (0, 84):
            add_rot(b, "body_chest", t, [0, 0, 0])
            add_rot(b, "body_mid", t, [0, 0, 0])
            add_rot(b, "body_rear", t, [0, 0, 0])
            four_arms_pose(b, t, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
            for ti in range(1, 9): add_rot(b, f"tail_{ti}", t, [0, 0, 0])
            frills_flare(b, t, 0, 0)
        else:
            ph = ((t - 14) / 28.0) * math.tau
            s = math.sin(ph)
            c = math.cos(ph)
            add_rot(b, "body_chest", t, [6, s * 18, c * 10], "catmullrom")
            add_rot(b, "body_mid", t, [0, 22 + c * 10, 0], "catmullrom")
            add_rot(b, "body_rear", t, [0, 24 + s * 10, 0], "catmullrom")
            frills_flare(b, t, 30, 20, "catmullrom")
            four_arms_pose(b, t,
                           [s * 24, -18, -28 + c * 12], [-s * 24, 18, 28 + c * 12],
                           [c * 20, -14, -20], [-c * 20, 14, 20], easing="catmullrom")
            for ti in range(1, 9):
                add_rot(b, f"tail_{ti}", t, [c * 6, 18 + ti * 2.2 + s * 8, 0], "catmullrom")
    anims["animation.leviathan.whirlpool"] = {"loop": False, "animation_length": 4.2, "bones": b}

    # -------------------------------------------------------------------------
    # 8. ROAR_SHOCKWAVE (Phase 2, 2.50s = 50 ticks) — Co gù vai lấy hơi (0..16) -> Bật ngửa cổ gầm lớn, 4 tay dang rộng, toàn bộ vây mở tối đa (18..42)!
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 15, [0, -3.5, 0], "easeInCubic")
    add_pos(b, "body", 20, [0, +5.0, 0], "easeOutExpo")
    add_pos(b, "body", 40, [0, +3.0, 0], "easeInOutSine")
    add_pos(b, "body", 50, [0, 0, 0])

    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 15, [18, 0, 0], "easeInCubic")
    add_rot(b, "body_chest", 20, [-26, 0, 0], "easeOutExpo")
    add_rot(b, "body_chest", 40, [-18, 0, 0], "easeInOutSine")
    add_rot(b, "body_chest", 50, [0, 0, 0])

    add_rot(b, "neck", 0,  [0, 0, 0])
    add_rot(b, "neck", 15, [14, 0, 0], "easeInCubic")
    add_rot(b, "neck", 20, [-22, 0, 0], "easeOutExpo")
    add_rot(b, "neck", 50, [0, 0, 0])

    add_rot(b, "jaw", 0,  [0, 0, 0])
    add_rot(b, "jaw", 15, [12, 0, 0], "easeInCubic")
    add_rot(b, "jaw", 20, [52, 0, 0], "easeOutExpo")
    add_rot(b, "jaw", 40, [46, 0, 0], "easeInOutSine")
    add_rot(b, "jaw", 50, [0, 0, 0])

    frills_flare(b, 0, 0, 0)
    frills_flare(b, 15, 6, 4, "easeInCubic")
    frills_flare(b, 20, 44, 36, "easeOutExpo")
    frills_flare(b, 40, 40, 32, "easeInOutSine")
    frills_flare(b, 50, 0, 0)

    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 15, [22, 24, 18], [22, -24, -18], [18, 16, 12], [18, -16, -12], easing="easeInCubic")
    four_arms_pose(b, 20, [-28, -32, -48], [-28, 32, 48], [-22, -24, -38], [-22, 24, 38], easing="easeOutExpo")
    four_arms_pose(b, 40, [-20, -24, -36], [-20, 24, 36], [-16, -18, -28], [-16, 18, 28], easing="easeInOutSine")
    four_arms_pose(b, 50, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])

    for ti in range(1, 8):
        add_rot(b, f"tail_fin_{ti}_l", 0,  [0, 0, 0])
        add_rot(b, f"tail_fin_{ti}_l", 20, [0, 0, -24], "easeOutExpo")
        add_rot(b, f"tail_fin_{ti}_l", 30, [0, 0, +20], "easeInOutSine")
        add_rot(b, f"tail_fin_{ti}_l", 50, [0, 0, 0])
        add_rot(b, f"tail_fin_{ti}_r", 0,  [0, 0, 0])
        add_rot(b, f"tail_fin_{ti}_r", 20, [0, 0, +24], "easeOutExpo")
        add_rot(b, f"tail_fin_{ti}_r", 30, [0, 0, -20], "easeInOutSine")
        add_rot(b, f"tail_fin_{ti}_r", 50, [0, 0, 0])
    anims["animation.leviathan.roar_shockwave"] = {"loop": False, "animation_length": 2.5, "bones": b}

    # -------------------------------------------------------------------------
    # 9. STORM_BARRAGE (Phase 2, 2.40s = 48 ticks) — Vút cao triệu gọi mưa bão 3 đợt (impacts: 14, 24, 34)
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 14, [0, 6.5, 0], "easeOutCubic")
    add_pos(b, "body", 36, [0, 5.5, 0], "easeInOutSine")
    add_pos(b, "body", 48, [0, 0, 0])
    for imp in (14, 24, 34):
        add_rot(b, "body_chest", imp - 4, [-12, 0, 0], "easeInCubic")
        add_rot(b, "body_chest", imp,     [-22, 0, 0], "easeOutBack")
        add_rot(b, "jaw", imp - 4, [18, 0, 0])
        add_rot(b, "jaw", imp,     [46, 0, 0], "easeOutExpo")
    add_rot(b, "body_chest", 0, [0, 0, 0])
    add_rot(b, "body_chest", 48, [0, 0, 0])
    add_rot(b, "jaw", 0, [0, 0, 0])
    add_rot(b, "jaw", 48, [0, 0, 0])
    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 14, [-30, -20, -36], [-30, 20, 36], [-20, -14, -26], [-20, 14, 26], easing="easeOutCubic")
    four_arms_pose(b, 34, [-24, -18, -32], [-24, 18, 32], [-18, -12, -22], [-18, 12, 22], easing="easeInOutSine")
    four_arms_pose(b, 48, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    anims["animation.leviathan.storm_barrage"] = {"loop": False, "animation_length": 2.4, "bones": b}

    # -------------------------------------------------------------------------
    # 10. TIDAL_WRATH (Ultimate Phase 2, 2.60s = 52 ticks) — Vút thân bay vòng lên cao (0..22) -> Bổ nhào đập thân xuống đất (24..36, impact 36) -> Recovery
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 20, [0, 10.0, 4.0], "easeOutCubic")
    add_pos(b, "body", 34, [0, -2.0, -8.0], "easeInCubic")
    add_pos(b, "body", 37, [0, -6.5, -4.0], "easeOutExpo")
    add_pos(b, "body", 52, [0, 0, 0], "easeInOutSine")

    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 20, [-32, 0, 0], "easeOutCubic")
    add_rot(b, "body_chest", 34, [+34, 0, 0], "easeInCubic")
    add_rot(b, "body_chest", 38, [+14, 0, 0], "easeOutExpo")
    add_rot(b, "body_chest", 52, [0, 0, 0])

    add_rot(b, "jaw", 0,  [0, 0, 0])
    add_rot(b, "jaw", 20, [44, 0, 0])
    add_rot(b, "jaw", 36, [52, 0, 0])
    add_rot(b, "jaw", 52, [0, 0, 0])
    frills_flare(b, 0, 0, 0)
    frills_flare(b, 20, 40, 32)
    frills_flare(b, 36, 46, 36, "easeOutExpo")
    frills_flare(b, 52, 0, 0)

    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 20, [-36, -26, -42], [-36, 26, 42], [-24, -18, -32], [-24, 18, 32], easing="easeOutCubic")
    four_arms_pose(b, 36, [28, 12, -16], [28, -12, 16], [22, 8, -12], [22, -8, 12], easing="easeOutExpo")
    four_arms_pose(b, 52, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    anims["animation.leviathan.tidal_wrath"] = {"loop": False, "animation_length": 2.6, "bones": b}

    # -------------------------------------------------------------------------
    # 11. PHASE_CHANGE (4.50s = 90 ticks) — Gầm chuyển dạng cuồng bạo (0° xoay đầu ngang, không bao giờ tái phát head-spin!)
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 26, [0, -4.0, 0], "easeInCubic")
    add_pos(b, "body", 42, [0, +6.5, 0], "easeOutExpo")
    add_pos(b, "body", 72, [0, +4.5, 0], "easeInOutSine")
    add_pos(b, "body", 90, [0, 0, 0], "easeInOutSine")

    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 26, [18, 0, 0], "easeInCubic")
    add_rot(b, "body_chest", 42, [-26, 0, 0], "easeOutExpo")
    add_rot(b, "body_chest", 72, [-20, 0, 0], "easeInOutSine")
    add_rot(b, "body_chest", 90, [0, 0, 0])

    # Đầu CHỈ ngẩng pitch nhẹ (-22°), rotY = 0.0° tuyệt đối!
    add_rot(b, "neck", 0,  [0, 0, 0])
    add_rot(b, "neck", 26, [12, 0, 0], "easeInCubic")
    add_rot(b, "neck", 42, [-20, 0, 0], "easeOutExpo")
    add_rot(b, "neck", 72, [-14, 0, 0])
    add_rot(b, "neck", 90, [0, 0, 0])
    add_rot(b, "head", 0,  [0, 0, 0])
    add_rot(b, "head", 42, [-12, 0, 0], "easeOutExpo")
    add_rot(b, "head", 90, [0, 0, 0])

    add_rot(b, "jaw", 0,  [0, 0, 0])
    add_rot(b, "jaw", 26, [14, 0, 0])
    add_rot(b, "jaw", 42, [52, 0, 0], "easeOutExpo")
    add_rot(b, "jaw", 72, [46, 0, 0])
    add_rot(b, "jaw", 90, [0, 0, 0])
    frills_flare(b, 0, 0, 0)
    frills_flare(b, 26, 8, 6)
    frills_flare(b, 42, 46, 38, "easeOutExpo")
    frills_flare(b, 72, 42, 34)
    frills_flare(b, 90, 0, 0)

    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 26, [24, 24, 18], [24, -24, -18], [20, 18, 14], [20, -18, -14], easing="easeInCubic")
    four_arms_pose(b, 42, [-32, -30, -50], [-32, 30, 50], [-24, -22, -40], [-24, 22, 40], easing="easeOutExpo")
    four_arms_pose(b, 72, [-22, -22, -38], [-22, 22, 38], [-18, -16, -30], [-18, 16, 30], easing="easeInOutSine")
    four_arms_pose(b, 90, [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    for ti in range(1, 9):
        add_rot(b, f"tail_{ti}", 0,  [0, 0, 0])
        add_rot(b, f"tail_{ti}", 26, [-6, math.sin(ti) * 8, 0])
        add_rot(b, f"tail_{ti}", 46, [10, -math.sin(ti) * 10, 0], "easeOutBack")
        add_rot(b, f"tail_{ti}", 90, [0, 0, 0])
    anims["animation.leviathan.phase_change"] = {"loop": False, "animation_length": 4.5, "bones": b}

    # -------------------------------------------------------------------------
    # 12. DEATH (3.00s = 60 ticks, hold) — 4 tay buông dần, thân uốn nặng đổ nghiêng xuống mặt đất!
    # -------------------------------------------------------------------------
    b = {}
    add_pos(b, "body", 0,  [0, 0, 0])
    add_pos(b, "body", 20, [0, -6.0, 0], "easeInCubic")
    add_pos(b, "body", 44, [0, -18.0, 0], "easeOutBounce")
    add_pos(b, "body", 60, [0, -18.5, 0], "linear")

    add_rot(b, "body_chest", 0,  [0, 0, 0])
    add_rot(b, "body_chest", 18, [-14, 0, 12], "easeOutCubic")
    add_rot(b, "body_chest", 44, [12, 0, 38], "easeInCubic")
    add_rot(b, "body_chest", 60, [14, 0, 42], "linear")
    add_rot(b, "jaw", 0,  [0, 0, 0])
    add_rot(b, "jaw", 18, [36, 0, 0])
    add_rot(b, "jaw", 60, [24, 0, 0], "linear")
    frills_flare(b, 0, 0, 0)
    frills_flare(b, 60, -16, -12, "easeOutCubic")
    four_arms_pose(b, 0,  [0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
    four_arms_pose(b, 46, [24, 10, -28], [24, -10, 28], [28, 8, -22], [28, -8, 22], easing="easeOutCubic")
    four_arms_pose(b, 60, [28, 12, -32], [28, -12, 32], [32, 10, -26], [32, -10, 26], easing="linear")
    for ti in range(1, 9):
        add_rot(b, f"tail_{ti}", 0,  [0, 0, 0])
        add_rot(b, f"tail_{ti}", 48, [-8, 12 + ti * 1.5, 6], "easeOutCubic")
        add_rot(b, f"tail_{ti}", 60, [-10, 14 + ti * 1.5, 8], "linear")
    anims["animation.leviathan.death"] = {"loop": "hold_on_last_frame", "animation_length": 3.0, "bones": b}

    # Kiểm định an toàn chống xoay đầu: KHÔNG có keyframe nào vượt quá ±110°!
    for aname, adata in anims.items():
        for bname, bdata in adata["bones"].items():
            for tk, kv in bdata.get("rotation", {}).items():
                for comp in kv["vector"]:
                    assert abs(comp) <= 110.0, f"Rotation exceeds 110° in {aname}.{bname}@{tk}: {kv}"

    out = {"format_version": "1.8.0", "animations": anims}
    out_path = ASSETS / "animations" / "entity" / "leviathan.animation.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
    print(f"ANIM_OK: {out_path} -> {len(anims)} animations verified (0 angles > 110°)")
    return out


# =============================================================================
# 6. BỘ RENDER PREVIEW 3D Z-BUFFER ALPHA-CUTOUT CHUẨN GECKOLIB 4.8.3 (6 GÓC ĐỐI CHIẾU!)
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


def rasterize_alpha_cutout(faces, diff_img, glow_img, view_mat, width, height, scale, center,
                           bg=(224, 228, 232), light_dir=None):
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
        lit = d_col * shade * (1.0 - g_a * 0.85) + g_col * (g_a * 1.15)
        fb[min_y + cy_idx, min_x + cx_idx] = np.clip(lit, 0, 255)

    for quad, uv in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = 0.45 + 0.55 * abs(float(np.dot(n, light_dir)))
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
    d_norm = np.array(Image.open(tex_dir / "leviathan.png").convert("RGBA"), dtype=np.float32)
    g_norm = np.array(Image.open(tex_dir / "leviathan_glowmask.png").convert("RGBA"), dtype=np.float32)
    d_enr = np.array(Image.open(tex_dir / "leviathan_enraged.png").convert("RGBA"), dtype=np.float32)
    g_enr = np.array(Image.open(tex_dir / "leviathan_enraged_glowmask.png").convert("RGBA"), dtype=np.float32)

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
    pw, ph = 640, 460
    sc = (min(pw, ph) * 0.88) / max(1.0, span)
    # Tâm tập trung cho góc chính diện đầu + 4 tay
    front_center = np.array([0.0, 22.0, -14.0], dtype=np.float64)

    # 1. Chính diện (Front — xác nhận 4 TAY VUỐT DÀI, 2 RĂNG NANH NGÀ LỘ RÕ, MẮT VÀNG-CAM, VÂY CẰM!)
    p_front = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(180, 6), pw, ph, sc * 2.45, front_center, bg=(218, 222, 226))
    ImageDraw.Draw(p_front).text((12, 10), "FRONT VIEW (4 CLAWED ARMS, 2 IVORY TUSKS, PURPLE GUMS, CHIN FRILL)", fill=(24, 34, 46))
    p_front.save(PREVIEW_DIR / "preview_front.png")

    # 2. Bên trái (Left Side — xác nhận vai gù, vây lưng lửa, khe mang xanh lam, 2 cặp tay, đuôi roi dài!)
    p_left = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(-90, 2), pw, ph, sc * 1.15, center, bg=(218, 222, 226))
    ImageDraw.Draw(p_left).text((12, 10), "LEFT PROFILE (HUNCHED SHOULDER, 4 ARMS, BLUE GILLS, WHIP TAIL)", fill=(24, 34, 46))
    p_left.save(PREVIEW_DIR / "preview_side_left.png")

    # 3. Bên phải (Right Side)
    p_right = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(90, 2), pw, ph, sc * 1.15, center, bg=(218, 222, 226))
    ImageDraw.Draw(p_right).text((12, 10), "RIGHT PROFILE (DORSAL FIN, ARM FINS, TAPERED HYDRODYNAMIC BODY)", fill=(24, 34, 46))
    p_right.save(PREVIEW_DIR / "preview_side_right.png")

    # 4. Trên xuống (Top-Down — xác nhận vây đuôi lá lửa alpha liền mạch + 12 cặp nan + đốm lưng xanh ngọc!)
    p_top = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(90, 88), pw, ph, sc * 1.12, center, bg=(218, 222, 226))
    ImageDraw.Draw(p_top).text((12, 10), "TOP-DOWN (LEAF FLAME TAIL FINS + 12 RAY PAIRS + AQUA DORSAL SPOTS)", fill=(24, 34, 46))
    p_top.save(PREVIEW_DIR / "preview_top.png")

    # 5. Dưới lên (Bottom-Up — xác nhận bụng vảy chữ V kem ngà hồng liên tục + 4 tay + màng vây lửa!)
    p_bot = rasterize_alpha_cutout(faces, d_norm, g_norm, cam_mat(180, -88), pw, ph, sc * 0.92, center,
                                   bg=(218, 222, 226), light_dir=np.array([0.0, -1.0, 0.0]))
    ImageDraw.Draw(p_bot).text((12, 10), "BOTTOM-UP (IVORY-PINK CHEVRON BELLY + 4 ARMS + CONTINUOUS TAIL FINS)", fill=(24, 34, 46))
    p_bot.save(PREVIEW_DIR / "preview_bottom.png")

    # 6. Phase 2 Enraged (3/4 Oblique tối màu tôn phát sáng glowmask fullbright!)
    p_p2 = rasterize_alpha_cutout(faces, d_enr, g_enr, cam_mat(138, 18), pw, ph, sc * 1.38,
                                  np.array([0.0, 24.0, 12.0]), bg=(12, 18, 28))
    ImageDraw.Draw(p_p2).text((12, 10), "PHASE 2 ENRAGED (ELECTRIC AQUA SPOTS/GILLS, HOTTER FLAME FINS)", fill=(160, 248, 255))
    p_p2.save(PREVIEW_DIR / "preview_phase2_enraged.png")

    sheet = Image.new("RGB", (pw * 3, ph * 2), (24, 30, 38))
    sheet.paste(p_front, (0,      0))
    sheet.paste(p_left,  (pw,     0))
    sheet.paste(p_top,   (pw * 2, 0))
    sheet.paste(p_right, (0,      ph))
    sheet.paste(p_bot,   (pw,     ph))
    sheet.paste(p_p2,    (pw * 2, ph))
    sheet.save(PREVIEW_DIR / "leviathan_preview_sheet.png")

    print(
        f"PREVIEW_OK: Saved 6 angles + sheet to {PREVIEW_DIR} | "
        f"bbox X=[{bmin[0]:.1f}..{bmax[0]:.1f}] Y=[{bmin[1]:.1f}..{bmax[1]:.1f}] Z=[{bmin[2]:.1f}..{bmax[2]:.1f}]"
    )


if __name__ == "__main__":
    backup_existing_files()
    geo_data, total_cubes, meta = build_leviathan_geo()
    build_textures()
    build_animations()
    render_previews(geo_data)
    print(f"ALL_DONE: Leviathan v2 rebuilt ({meta['bones']} bones, {total_cubes} cubes, {TEX_W}x{TEX_H})")
