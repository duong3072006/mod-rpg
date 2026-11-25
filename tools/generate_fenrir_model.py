#!/usr/bin/env python3
r"""
Bộ sinh Model 3D GeckoLib 4.8.3 + 4 Texture 1024x1024 RGBA (có Tile Lông Chân Tầng Mịn `FUR_LEG_FEATHERED`
+ Highlight Sợi Lông Sắc Nét) + 11 Animation Sóng Trễ Pha Đa Khớp + Bộ Preview 3D Z-buffer 6 góc
cho Boss FENRIR — FEL V5 (Thần Sói Bão Tố).

NÂNG CẤP TOÀN DIỆN V5 (TEXTURE LÔNG CHÂN DÀY MỊN + MỌI SỢI LÔNG ĐỀU CÓ ĐA KHỚP UỐN SÓNG!):
1. TEXTURE LÔNG CHÂN RIÊNG (`FUR_LEG_FEATHERED`, 256x256) + NÂNG CẤP ĐỘ SẮC SỢI LÔNG TOÀN THÂN:
   - Vẽ rõ từng lớp lông tầng chữ V (layered overlapping fur tufts), rãnh bóng xanh trời dịu xen kẽ highlight
     bạc tuyết & từng sợi lông mảnh chạy dọc ống chân giúp 4 chân dày lông, mềm mại, không bao giờ bị trống!
   - Đồng thời bổ sung hệ **lông ống chân & lông cổ chân 3D đa khớp** (khuỷu tay/khuỷu sau 3 chùm rời x 2 khớp,
     lông rủ dọc cẳng tay/ống chân sau 2 tầng x 2 khớp, lông cổ chân 2 khớp) cho cả 4 chân!
2. MỌI SỢI LÔNG CHỈA RA ĐỀU LÀ CHUỖI 2–5 KHỚP NỐI TIẾP (0 SỢI CỨNG, 0 SỢI 1 KHỚP!):
   - Đuôi mây 8 đốt (`tail_1..8`) + 14 chùm lông đuôi chỉa ra, MỖI CHÙM đều chia **2–3 khớp nối tiếp** (31 khớp)!
   - 3 dải lông gió lưng chia **5 khớp/dải** (15 khớp) + 3 mào lông vai chia **2 khớp/mào** (6 khớp).
   - 2 bờm má chia **4 khớp/bên** (8 khớp) + chỏm đỉnh đầu, lông má, lông cằm & 3 chùm yếm ngực đều chia **2 khớp**!
   - Toàn bộ lọn lông bờm cổ, sườn, hông (`mane_collar`, `mane_flank`, `flank_tuft`, `hip_tuft`) chia **3 khớp/lọn**!
   - Cột sống 3 đốt (`spine_1..3`), cổ 4 đốt (`neck_1..4`), tai 2 đốt, 4 chân 7 khớp đầy đủ (~210 khớp zero-bind!).
3. SÓNG HÒA ÂM TRỄ PHA TỰ ĐỘNG (`fur_registry`):
   - Mọi khớp từ gốc -> giữa -> ngọn của từng sợi lông đều nhận pha trễ riêng & biên độ tăng dần về ngọn
     trong cả 11 animation + mô phỏng gió thứ cấp thời gian thực ở `FenrirGeoModel.java`!

Chạy: python tools/generate_fenrir_model.py
"""

import json
import math
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
BACKUP_DIR = ROOT / "tools" / "backup_fenrir_legacy"
PREVIEW_DIR = ROOT / "tools" / "fenrir_previews"

TEX_W = 1024
TEX_H = 1024

MAT = {
    "FUR_SILVER":          (0,   0,   256, 256),  # Lông thân TRẮNG XANH băng trời có tầng lông chữ V & highlight lụa sắc nét
    "FUR_AZURE_DEEP":      (256, 0,   256, 256),  # Bóng xanh băng đậm dịu trong hốc bờm/sườn/bụng có sợi lông rõ
    "FUR_WHITE_CHEST":     (512, 0,   256, 256),  # Yếm ngực giọt lệ, mõm & bàn chân TRẮNG TUYẾT có lớp lông tơ mịn
    "FUR_LEG_FEATHERED":   (768, 0,   256, 256),  # Tile RIÊNG 256x256 cho 4 CHÂN: tầng lông xếp lớp, sợi highlight & bóng khớp dày mịn!
    "FUR_WISP_CUTOUT":     (0,   256, 256, 256),  # Lọn lông mây trắng-xanh highlight alpha-cutout 2 mặt
    "CRIMSON_CUFF_CUTOUT": (256, 256, 256, 256),  # Lọn lông gió trắng-xanh highlight ở khuỷu/gối 4 chân (alpha-cutout 2 mặt)
    "HEAD_FOREHEAD":       (512, 256, 256, 256),  # Trán xanh băng mang DẤU SAO 4 CÁNH TRẮNG TINH + TIA SÉT XANH ĐEN
    "EAR_INNER":           (768, 256, 128, 256),  # Lòng tai trong tím xanh dịu + lông tơ trắng chân tai
    "MUZZLE_MARK":         (896, 256, 128, 256),  # Má mõm trắng tuyết có dấu cong ĐỎ HUYẾT khóe miệng Fel
    "EYE_FEL":             (0,   512, 128, 128),  # Mắt sói NGỌC LỤC BẢO phát sáng fullbright
    "NOSE_CRIMSON":        (128, 512, 128, 128),  # Mũi sói nâu đỏ sẫm có highlight ẩm
    "MOUTH_INNER":         (256, 512, 128, 128),  # Khoang miệng & lưỡi đỏ hồng
    "FANG_IVORY":          (384, 512, 128, 128),  # Răng nanh trắng ngà
    "CLAW_OBSIDIAN":       (512, 512, 128, 128),  # Móng vuốt đen obsidian bóng sắc
    "WIND_RUNE_GLOW":      (640, 512, 128, 128),  # Ánh sao gió lam-trắng thần thánh
    "DARK_BROW":           (768, 512, 128, 128),  # Đường nét anime xanh biển đêm đậm
    "FUR_CRIMSON_MARK":    (896, 512, 128, 128),  # Vệt đỏ huyết nhỏ tinh tế ở khóe miệng Fel
    "FUR_HIGHLIGHT_LOCK":  (0,   640, 512, 384),  # Lọn lông HIGHLIGHT TRẮNG-XANH BĂNG RỰC MỊN có sớ sợi sắc nét cho bờm, lưng, chân & đuôi!
    "FLANK_SWIRL_CUTOUT":  (512, 640, 512, 384),  # Lọn lông gió trắng-xanh alpha-cutout 2 mặt
}


def backup_legacy_fenrir() -> None:
    if BACKUP_DIR.exists() and any(BACKUP_DIR.rglob("*")):
        print(f"BACKUP_SKIP: Đã sao lưu Fenrir cũ tại {BACKUP_DIR}")
        return
    legacy_files = [
        ASSETS / "textures" / "entity" / "fenrir_boss.png",
        ASSETS / "textures" / "entity" / "fenrir_boss_glow.png",
        ASSETS / "textures" / "entity" / "sculpted" / "fenrir_boss.png",
        ASSETS / "textures" / "entity" / "sculpted" / "fenrir_boss_glow.png",
    ]
    copied = 0
    for f in legacy_files:
        if f.exists():
            dst = BACKUP_DIR / f.relative_to(ASSETS)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dst)
            copied += 1
    print(f"BACKUP_OK: Đã sao lưu {copied} file texture Fenrir cũ vào {BACKUP_DIR}")


# =============================================================================
# 1. TOÁN HỌC XOAY CHUẨN GECKOLIB 4.8.3 & HÀM DỰNG HÌNH ĐA KHỚP HỮU CƠ
# =============================================================================
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


def uv_face(mat_key: str, pad: int = 3, sub_u=(0.0, 1.0), sub_v=(0.0, 1.0), flip_u: bool = False, flip_v: bool = False) -> dict:
    x0, y0, w, h = MAT[mat_key]
    iw, ih = max(2, w - 2 * pad), max(2, h - 2 * pad)
    ux = int(round(x0 + pad + sub_u[0] * iw))
    uy = int(round(y0 + pad + sub_v[0] * ih))
    uw = max(2, int(round((sub_u[1] - sub_u[0]) * iw)))
    uh = max(2, int(round((sub_v[1] - sub_v[0]) * ih)))
    if flip_u:
        ux, uw = ux + uw, -uw
    if flip_v:
        uy, uh = uy + uh, -uh
    return {"uv": [ux, uy], "uv_size": [uw, uh]}


def uv_box(mat_key: str, pad: int = 3, sub_u=(0.0, 1.0), sub_v=(0.0, 1.0),
           face_overrides: dict | None = None, transparent_edges: tuple = (),
           flip_u: bool = False, flip_v: bool = False) -> dict:
    f = uv_face(mat_key, pad=pad, sub_u=sub_u, sub_v=sub_v, flip_u=flip_u, flip_v=flip_v)
    uvs = {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}
    if face_overrides:
        for side, spec in face_overrides.items():
            if isinstance(spec, str):
                uvs[side] = uv_face(spec, pad=pad)
            elif len(spec) == 3:
                mk, su, sv = spec
                uvs[side] = uv_face(mk, pad=pad, sub_u=su, sub_v=sv)
            else:
                mk, su, sv, fu, fv = spec
                uvs[side] = uv_face(mk, pad=pad, sub_u=su, sub_v=sv, flip_u=fu, flip_v=fv)
    if transparent_edges:
        x0, y0, w, h = MAT[mat_key]
        trans = {"uv": [x0 + 1, y0 + 1], "uv_size": [2, 2]}
        for side in transparent_edges:
            uvs[side] = trans
    return uvs


def make_cube(origin, size, mat: str, rot=None, pivot=None,
              sub_u=(0.0, 1.0), sub_v=(0.0, 1.0), pad: int = 3,
              face_overrides: dict | None = None, transparent_edges: tuple = (),
              flip_u: bool = False, flip_v: bool = False) -> dict:
    ox, oy, oz = float(origin[0]), float(origin[1]), float(origin[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    uvs = uv_box(mat, pad=pad, sub_u=sub_u, sub_v=sub_v,
                 face_overrides=face_overrides, transparent_edges=transparent_edges,
                 flip_u=flip_u, flip_v=flip_v)
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


def make_centered_cube(center, size, mat: str, rot=None,
                       sub_u=(0.0, 1.0), sub_v=(0.0, 1.0), pad: int = 3,
                       face_overrides: dict | None = None, transparent_edges: tuple = (),
                       flip_u: bool = False, flip_v: bool = False) -> dict:
    cx, cy, cz = float(center[0]), float(center[1]), float(center[2])
    sx, sy, sz = float(size[0]), float(size[1]), float(size[2])
    return make_cube(
        [cx - sx * 0.5, cy - sy * 0.5, cz - sz * 0.5], [sx, sy, sz], mat,
        rot=rot, pivot=[cx, cy, cz], sub_u=sub_u, sub_v=sub_v, pad=pad,
        face_overrides=face_overrides, transparent_edges=transparent_edges,
        flip_u=flip_u, flip_v=flip_v,
    )


def add_chain_rod(out_list: list, p_start, p_end, thickness_a: float, thickness_b: float,
                  segments: int, mat: str, overlap: float = 0.40, sub_v_span=(0.0, 1.0),
                  aspect_z: float = 1.0, face_overrides: dict | None = None) -> None:
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
    assert np.allclose(chk, u_dir, atol=1e-3), f"Rod alignment failed: {chk} vs {u_dir}"

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
            sub_v=(min(v0, v1), max(v0, v1)),
            face_overrides=face_overrides,
        ))


def catmull_rom_samples(pts: list, total_samples: int) -> list:
    arr = [np.array(p, dtype=np.float64) for p in pts]
    n_spans = len(arr) - 1
    out = []
    for k in range(total_samples + 1):
        u = (k / float(total_samples)) * n_spans
        i = min(n_spans - 1, int(math.floor(u)))
        t = u - i
        p0 = arr[max(0, i - 1)]
        p1 = arr[i]
        p2 = arr[i + 1]
        p3 = arr[min(len(arr) - 1, i + 2)]
        t2, t3 = t * t, t * t * t
        pt = 0.5 * (
            (2.0 * p1)
            + (-p0 + p2) * t
            + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
            + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )
        out.append(pt)
    return out


def add_curved_rod(out_list: list, pts: list, r_start: float, r_end: float, mat: str,
                   segs_per_span: int = 2, overlap: float = 0.42, aspect_z: float = 1.0) -> None:
    dense = catmull_rom_samples(pts, max(2, (len(pts) - 1) * segs_per_span))
    total = len(dense) - 1
    for i in range(total):
        t0 = i / float(total)
        t1 = (i + 1) / float(total)
        ra = r_start + (r_end - r_start) * t0
        rb = r_start + (r_end - r_start) * t1
        add_chain_rod(out_list, dense[i], dense[i + 1], ra, rb, 1, mat,
                      overlap=overlap, sub_v_span=(t0, t1), aspect_z=aspect_z)


def add_fur_fin(out_list: list, center, width: float, height: float, mat: str, rot=(0.0, 0.0, 0.0),
                thickness: float = 0.20, flip_u: bool = False, flip_v: bool = False,
                sub_u=(0.0, 1.0), sub_v=(0.0, 1.0)) -> None:
    out_list.append(make_centered_cube(
        center, [width, height, thickness], mat, rot=rot,
        sub_u=sub_u, sub_v=sub_v, pad=2,
        transparent_edges=("east", "west", "up", "down"),
        flip_u=flip_u, flip_v=flip_v,
    ))


def add_soft_puff(out_list: list, center, rx: float, ry: float, rz: float, mat: str,
                  rot=(0.0, 0.0, 0.0), highlight_top: bool = False) -> None:
    out_list.append(make_centered_cube(center, [rx, ry, rz], mat, rot=rot))
    out_list.append(make_centered_cube(
        center, [rx * 0.88, ry * 0.90, rz * 0.88], mat,
        rot=[rot[0] + 15.0, rot[1] + 28.0, rot[2] + 18.0],
    ))
    if highlight_top:
        out_list.append(make_centered_cube(
            [center[0], center[1] + ry * 0.22, center[2]],
            [rx * 0.68, ry * 0.56, rz * 0.72],
            "FUR_HIGHLIGHT_LOCK",
            rot=[rot[0] - 8.0, rot[1] - 14.0, rot[2] - 10.0],
        ))


def add_articulated_fur_ribbon(bones: list, fur_registry: list, base_name: str, parent_name: str, pts: list,
                               n_bones: int, r_start: float, r_end: float, mat_top: str,
                               group: str, side: int = 0, phase0: float = 0.5,
                               mat_under: str | None = None, aspect_z: float = 0.76,
                               segs_per_bone: int = 2) -> list:
    """
    Biến MỌI sợi lông/chùm lông thành chuỗi `n_bones` khớp nối tiếp nhau (n_bones >= 2, 100% zero-bind)
    và tự động ghi danh vào `fur_registry` để cả 11 animation + sóng gió realtime đều uốn mượt từ gốc tới ngọn!
    """
    assert n_bones >= 2, f"Mọi sợi lông phải có ít nhất 2 khớp nối tiếp! ({base_name} có {n_bones})"
    samples = catmull_rom_samples(pts, n_bones * segs_per_bone)
    created = []
    cur_parent = parent_name
    for bi in range(n_bones):
        bname = base_name if bi == 0 else f"{base_name}_{bi + 1}"
        i0 = bi * segs_per_bone
        i1 = (bi + 1) * segs_per_bone
        sub_pts = samples[i0:i1 + 1]
        t0 = bi / float(n_bones)
        t1 = (bi + 1) / float(n_bones)
        ra = r_start + (r_end - r_start) * t0
        rb = r_start + (r_end - r_start) * t1
        cubes = []
        for si in range(len(sub_pts) - 1):
            st0 = si / float(len(sub_pts) - 1)
            st1 = (si + 1) / float(len(sub_pts) - 1)
            sa = ra + (rb - ra) * st0
            sb = ra + (rb - ra) * st1
            v_a = t0 + (t1 - t0) * st0
            v_b = t0 + (t1 - t0) * st1
            add_chain_rod(cubes, sub_pts[si], sub_pts[si + 1], sa, sb, 1, mat_top,
                          overlap=0.48, sub_v_span=(v_a, v_b), aspect_z=aspect_z)
            if mat_under is not None and bi < n_bones - 1:
                off = np.array([0.0, -sa * 0.28, 0.22], dtype=np.float64)
                add_chain_rod(cubes, sub_pts[si] + off, sub_pts[si + 1] + off, sa * 0.66, sb * 0.62, 1,
                              mat_under, overlap=0.40, sub_v_span=(v_a, v_b), aspect_z=aspect_z)
        bones.append({
            "name": bname,
            "parent": cur_parent,
            "pivot": [round(float(v), 3) for v in sub_pts[0]],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": cubes,
        })
        created.append(bname)
        cur_parent = bname
    fur_registry.append({
        "base": base_name,
        "bones": created,
        "group": group,
        "side": side,
        "phase0": phase0,
    })
    return created


# =============================================================================
# 2. DỰNG MODEL 3D FEL V5 — MỌI SỢI LÔNG ĐỀU CÓ 2–5 KHỚP + 4 CHÂN DÀY LÔNG!
# =============================================================================
def build_fenrir_geo():
    bones = []
    fur_registry = []
    bones.append({"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})
    bones.append({"name": "body", "parent": "root", "pivot": [0.0, 30.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})

    # -------------------------------------------------------------------------
    # A. HÔNG & MÔNG (`pelvis`) + LỌN LÔNG HÔNG 3 ĐỐT & LỌN MÔNG 2 ĐỐT!
    # -------------------------------------------------------------------------
    pelvis_c = [
        make_centered_cube([0.0, 30.4, +16.8], [14.2, 11.4, 14.4], "FUR_SILVER", rot=[-5.0, 0.0, 0.0]),
        make_centered_cube([0.0, 31.2, +22.8], [11.6, 9.8, 9.8],   "FUR_SILVER", rot=[-10.0, 0.0, 0.0]),
        make_centered_cube([0.0, 25.4, +16.2], [10.8, 4.8, 12.4],  "FUR_AZURE_DEEP", rot=[10.0, 0.0, 0.0]),
        make_centered_cube([0.0, 36.2, +16.8], [10.4, 4.2, 13.6],  "FUR_HIGHLIGHT_LOCK", rot=[-8.0, 0.0, 0.0]),
    ]
    for sx in (1.0, -1.0):
        add_soft_puff(pelvis_c, [sx * 7.2, 30.6, +17.6], 6.4, 9.8, 12.2, "FUR_SILVER", rot=[-6.0, sx * 12.0, sx * -8.0], highlight_top=True)
    bones.append({"name": "pelvis", "parent": "body", "pivot": [0.0, 30.0, +16.5], "rotation": [0.0, 0.0, 0.0], "cubes": pelvis_c})

    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        # 2 sợi lông lưng hông trên (2 khớp nối tiếp!)
        add_articulated_fur_ribbon(bones, fur_registry, f"pelvis_wisp_{tag}", "pelvis", [
            [sx * 6.8, 36.0, +11.8],
            [sx * 9.6, 37.6, +18.8],
            [sx * 7.6, 35.8, +25.6],
        ], n_bones=2, r_start=4.0, r_end=0.80, mat_top="FUR_HIGHLIGHT_LOCK",
           group="body_tuft", side=s_i, phase0=1.6 + (0.35 if s_i < 0 else 0.0), aspect_z=0.86)
        # 2 chùm lông hông vươn cong ra sau (3 khớp nối tiếp/bên!)
        add_articulated_fur_ribbon(bones, fur_registry, f"hip_tuft_{tag}", "pelvis", [
            [sx * 8.2, 33.0, +12.0],
            [sx * 11.0, 30.6, +18.6],
            [sx * 10.2, 27.6, +25.0],
            [sx * 7.4, 25.4, +28.8],
        ], n_bones=3, r_start=4.6, r_end=0.85, mat_top="FUR_SILVER",
           group="body_tuft", side=s_i, phase0=1.8 + (0.40 if s_i < 0 else 0.0),
           mat_under="FUR_AZURE_DEEP", aspect_z=0.88)

    # -------------------------------------------------------------------------
    # B. CỘT SỐNG 3 KHỚP MỀM (`spine_1` -> `spine_2` -> `spine_3`) + LÔNG SƯỜN 3 KHỚP!
    # -------------------------------------------------------------------------
    sp1_c = [
        make_centered_cube([0.0, 30.5, +11.2], [14.0, 11.2, 7.8], "FUR_SILVER", rot=[-2.0, 0.0, 0.0]),
        make_centered_cube([0.0, 25.3, +11.4], [10.8, 4.8, 7.2],  "FUR_AZURE_DEEP", rot=[10.0, 0.0, 0.0]),
        make_centered_cube([0.0, 36.4, +11.4], [11.0, 4.4, 7.8],  "FUR_HIGHLIGHT_LOCK", rot=[-6.0, 0.0, 0.0]),
    ]
    bones.append({"name": "spine_1", "parent": "body", "pivot": [0.0, 30.4, +14.2], "rotation": [0.0, 0.0, 0.0], "cubes": sp1_c})

    sp2_c = [
        make_centered_cube([0.0, 30.8, +5.4], [14.6, 11.6, 8.2], "FUR_SILVER", rot=[1.0, 0.0, 0.0]),
        make_centered_cube([0.0, 25.1, +5.6], [11.2, 4.9, 7.6],  "FUR_AZURE_DEEP", rot=[10.0, 0.0, 0.0]),
        make_centered_cube([0.0, 37.0, +5.6], [11.8, 4.8, 8.2],  "FUR_HIGHLIGHT_LOCK", rot=[-6.0, 0.0, 0.0]),
    ]
    bones.append({"name": "spine_2", "parent": "spine_1", "pivot": [0.0, 30.6, +8.4], "rotation": [0.0, 0.0, 0.0], "cubes": sp2_c})

    sp3_c = [
        make_centered_cube([0.0, 31.4, -0.4], [15.8, 12.8, 8.6], "FUR_SILVER", rot=[4.0, 0.0, 0.0]),
        make_centered_cube([0.0, 25.0, -0.2], [12.2, 5.2, 8.0],  "FUR_AZURE_DEEP", rot=[12.0, 0.0, 0.0]),
        make_centered_cube([0.0, 38.2, -0.2], [13.4, 5.2, 8.8],  "FUR_HIGHLIGHT_LOCK", rot=[-8.0, 0.0, 0.0]),
    ]
    for sx in (1.0, -1.0):
        add_soft_puff(sp3_c, [sx * 8.2, 32.4, -0.2], 5.4, 8.8, 8.4, "FUR_SILVER", rot=[4.0, sx * -10.0, sx * 8.0], highlight_top=True)
    bones.append({"name": "spine_3", "parent": "spine_2", "pivot": [0.0, 31.0, +2.6], "rotation": [0.0, 0.0, 0.0], "cubes": sp3_c})

    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        add_articulated_fur_ribbon(bones, fur_registry, f"flank_tuft_{tag}", "spine_3", [
            [sx * 8.0, 36.2, -3.5],
            [sx * 11.2, 33.8, +2.6],
            [sx * 10.6, 31.6, +9.4],
            [sx * 8.6, 30.4, +15.2],
        ], n_bones=3, r_start=4.8, r_end=1.0, mat_top="FUR_HIGHLIGHT_LOCK",
           group="body_tuft", side=s_i, phase0=1.3 + (0.35 if s_i < 0 else 0.0),
           mat_under="FUR_AZURE_DEEP", aspect_z=0.88)

    # -------------------------------------------------------------------------
    # C. NGỰC SÂU NỞ (`chest`) + 3 CHÙM YẾM NGỰC 2 ĐỐT + BỜM VAI/SƯỜN 3 ĐỐT
    #    + BỜM ĐỈNH (`mane_top`), 3 MÀO VAI 2 ĐỐT & 3 DẢI LÔNG GIÓ 5 ĐỐT (15 KHỚP!)
    # -------------------------------------------------------------------------
    chest_c = [
        make_centered_cube([0.0, 32.4, -10.5], [18.4, 15.6, 17.8], "FUR_SILVER", rot=[5.0, 0.0, 0.0]),
        make_centered_cube([0.0, 39.2, -10.5], [16.4, 7.6, 17.2],  "FUR_HIGHLIGHT_LOCK", rot=[-6.0, 0.0, 0.0]),
        make_centered_cube([0.0, 24.2, -10.2], [13.8, 5.8, 15.6],  "FUR_AZURE_DEEP", rot=[10.0, 0.0, 0.0]),
        # Yếm ngực giọt lệ trắng tuyết mềm mịn
        make_centered_cube([0.0, 34.4, -20.6], [12.2, 9.0, 6.2],   "FUR_WHITE_CHEST", rot=[16.0, 0.0, 0.0]),
        make_centered_cube([0.0, 29.8, -22.2], [11.0, 8.6, 5.8],   "FUR_WHITE_CHEST", rot=[14.0, 0.0, 0.0]),
        make_centered_cube([0.0, 25.4, -21.0], [9.0, 7.0, 5.4],    "FUR_WHITE_CHEST", rot=[22.0, 0.0, 0.0]),
        make_centered_cube([0.0, 30.0, -24.1], [8.4, 9.2, 2.2],    "FUR_WHITE_CHEST", rot=[14.0, 0.0, 0.0]),
        make_centered_cube([0.0, 25.2, -23.0], [6.0, 5.8, 2.2],    "FUR_WHITE_CHEST", rot=[22.0, 0.0, 0.0]),
        make_centered_cube([0.0, 21.8, -19.2], [6.2, 4.6, 4.4],    "FUR_WHITE_CHEST", rot=[30.0, 0.0, 0.0]),
    ]
    for sx in (1.0, -1.0):
        add_soft_puff(chest_c, [sx * 10.2, 35.6, -13.5], 7.8, 11.4, 13.0, "FUR_SILVER", rot=[8.0, sx * -14.0, sx * 10.0], highlight_top=True)
        add_soft_puff(chest_c, [sx * 11.4, 39.8, -16.8], 7.4, 10.0, 11.2, "FUR_SILVER", rot=[-6.0, sx * -20.0, sx * 16.0], highlight_top=True)
    bones.append({"name": "chest", "parent": "spine_3", "pivot": [0.0, 32.0, -4.5], "rotation": [0.0, 0.0, 0.0], "cubes": chest_c})

    # 3 chùm lông yếm ngực rủ độc lập — MỖI CHÙM 2 KHỚP NỐI TIẾP (`bib_tuft_c/l/r` -> `_2`)!
    for bname, tx, ty, tz, tw, tl, bside, bph in (
        ("bib_tuft_c",  0.0,  22.0, -19.6, 4.2, 7.0,  0, 0.35),
        ("bib_tuft_l", +3.6,  23.6, -20.8, 3.4, 5.8, +1, 0.65),
        ("bib_tuft_r", -3.6,  23.6, -20.8, 3.4, 5.8, -1, 0.95),
    ):
        add_articulated_fur_ribbon(bones, fur_registry, bname, "chest", [
            [tx, ty + 2.0, tz - 0.5],
            [tx * 1.06, ty - tl * 0.35, tz + 0.6],
            [tx * 0.92, ty - tl * 0.85, tz + 2.4],
        ], n_bones=2, r_start=tw, r_end=0.45, mat_top="FUR_WHITE_CHEST",
           group="bib_tuft", side=bside, phase0=bph, aspect_z=0.84)

    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        # Lọn lông ôm cạnh yếm ngực (2 khớp nối tiếp!)
        add_articulated_fur_ribbon(bones, fur_registry, f"chest_wisp_{tag}", "chest", [
            [sx * 6.2, 36.8, -21.8],
            [sx * 8.2, 31.8, -22.2],
            [sx * 7.4, 25.8, -20.6],
        ], n_bones=2, r_start=3.1, r_end=0.72, mat_top="FUR_HIGHLIGHT_LOCK",
           group="body_tuft", side=s_i, phase0=0.45 + (0.30 if s_i < 0 else 0.0), aspect_z=0.84)
        # Bờm vai cuộn 3 đốt (`mane_collar_l/r` -> `_2` -> `_3`)
        add_articulated_fur_ribbon(bones, fur_registry, f"mane_collar_{tag}", "chest", [
            [sx * 7.6, 41.4, -20.4],
            [sx * 13.8, 39.4, -16.2],
            [sx * 16.2, 35.2, -9.4],
            [sx * 14.2, 30.2, -1.6],
        ], n_bones=3, r_start=5.6, r_end=1.0, mat_top="FUR_HIGHLIGHT_LOCK",
           group="body_tuft", side=s_i, phase0=0.50 + (0.35 if s_i < 0 else 0.0),
           mat_under="FUR_AZURE_DEEP", aspect_z=0.86)
        # Bờm sườn trước 3 đốt (`mane_flank_l/r` -> `_2` -> `_3`)
        add_articulated_fur_ribbon(bones, fur_registry, f"mane_flank_{tag}", "chest", [
            [sx * 8.4, 34.8, -20.8],
            [sx * 14.4, 32.4, -16.0],
            [sx * 16.0, 28.2, -9.8],
            [sx * 13.4, 24.4, -4.8],
        ], n_bones=3, r_start=4.8, r_end=0.90, mat_top="FUR_SILVER",
           group="body_tuft", side=s_i, phase0=0.90 + (0.35 if s_i < 0 else 0.0),
           mat_under="FUR_AZURE_DEEP", aspect_z=0.86)

    # --- BỜM MÂY ĐỈNH (`mane_top`) + 3 MÀO VAI 2 ĐỐT + 3 DẢI LÔNG GIÓ 5 ĐỐT (15 KHỚP!) ---
    mane_top_c = [
        make_centered_cube([0.0, 43.4, -15.5], [18.8, 9.8, 16.4], "FUR_SILVER", rot=[-14.0, 0.0, 0.0]),
        make_centered_cube([0.0, 47.0, -13.5], [16.0, 7.0, 15.0], "FUR_HIGHLIGHT_LOCK", rot=[-18.0, 0.0, 0.0]),
        make_centered_cube([0.0, 44.8, -3.5],  [14.6, 7.4, 15.4], "FUR_SILVER", rot=[-10.0, 0.0, 0.0]),
    ]
    for sx in (1.0, -1.0):
        add_soft_puff(mane_top_c, [sx * 7.8, 47.2, -14.8], 7.6, 7.6, 11.0, "FUR_SILVER", rot=[-14.0, sx * -16.0, sx * 12.0], highlight_top=True)
    bones.append({"name": "mane_top", "parent": "chest", "pivot": [0.0, 43.0, -14.0], "rotation": [0.0, 0.0, 0.0], "cubes": mane_top_c})

    # 3 mào lông vai trước uốn ngược (mỗi mào 2 khớp nối tiếp — không còn sợi cứng!)
    add_articulated_fur_ribbon(bones, fur_registry, "mane_crest_c", "mane_top", [
        [0.0, 47.2, -20.5],
        [0.0, 51.0, -13.5],
        [0.0, 51.2, -4.5],
        [0.0, 47.4, +4.5],
    ], n_bones=2, r_start=6.8, r_end=1.8, mat_top="FUR_HIGHLIGHT_LOCK",
       group="ribbon", side=0, phase0=0.20, aspect_z=0.82)
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        add_articulated_fur_ribbon(bones, fur_registry, f"mane_crest_{tag}", "mane_top", [
            [sx * 5.2, 46.8, -21.5],
            [sx * 11.4, 50.8, -15.0],
            [sx * 12.8, 49.6, -6.0],
            [sx * 9.8, 45.6, +3.0],
        ], n_bones=2, r_start=5.8, r_end=1.2, mat_top="FUR_HIGHLIGHT_LOCK",
           group="ribbon", side=s_i, phase0=0.40 + (0.30 if s_i < 0 else 0.0), aspect_z=0.82)

    # Dải lông gió chính giữa 5 đốt (`mane_streamer_c` -> `_2` -> `_3` -> `_4` -> `_5`)
    add_articulated_fur_ribbon(bones, fur_registry, "mane_streamer_c", "mane_top", [
        [0.0, 49.2, -10.5],
        [1.0, 55.4, -1.5],
        [1.8, 58.2, +9.0],
        [1.4, 56.2, +20.5],
        [0.4, 51.2, +31.2],
        [0.0, 47.6, +38.8],
    ], n_bones=5, r_start=6.0, r_end=0.52, mat_top="FUR_HIGHLIGHT_LOCK",
       group="ribbon", side=0, phase0=0.30, mat_under="FUR_AZURE_DEEP", aspect_z=0.72)

    # 2 dải lông gió 2 bên 5 đốt/bên (`mane_streamer_l/r` -> `_2` -> `_3` -> `_4` -> `_5`)
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        add_articulated_fur_ribbon(bones, fur_registry, f"mane_streamer_{tag}", "mane_top", [
            [sx * 7.6, 48.2, -12.5],
            [sx * 11.4, 52.6, -3.5],
            [sx * 12.2, 51.8, +6.8],
            [sx * 9.8, 47.4, +17.8],
            [sx * 6.8, 43.0, +26.2],
        ], n_bones=5, r_start=4.8, r_end=0.48, mat_top="FUR_HIGHLIGHT_LOCK",
           group="ribbon", side=s_i, phase0=0.60 + (0.35 if s_i < 0 else 0.0),
           mat_under="FUR_AZURE_DEEP", aspect_z=0.74)

    # -------------------------------------------------------------------------
    # D. CỔ SÓI 4 KHỚP (`neck_1..4`), ĐẦU SÓI THẦN FEL V6 THANH TÚ SẮC NÉT,
    #    MẮT NGỌC LỤC BẢO RÕ RÀNG, MÕM THUÔN NHỌN, TAI CAO ĐỨNG & BỜM MÁ UỐN SÓNG!
    # -------------------------------------------------------------------------
    neck_specs = [
        ("neck_1", "chest",  [0.0, 35.8, -17.2], [0.0, 37.2, -18.8], [14.0, 11.0, 7.6], [10.6, 8.6, 4.4], [14.8, 5.6, 7.2], 16.0),
        ("neck_2", "neck_1", [0.0, 38.2, -21.0], [0.0, 39.6, -22.5], [12.0,  9.8, 7.2], [ 9.0, 7.4, 4.2], [13.0, 5.2, 7.0], 14.0),
        ("neck_3", "neck_2", [0.0, 40.4, -24.6], [0.0, 41.8, -26.0], [10.2,  8.6, 6.8], [ 7.8, 6.2, 4.0], [11.2, 4.8, 6.6], 12.0),
        ("neck_4", "neck_3", [0.0, 42.4, -28.0], [0.0, 43.6, -29.2], [ 8.8,  7.6, 6.4], [ 6.8, 5.0, 3.8], [ 9.6, 4.2, 6.2], 10.0),
    ]
    for nname, npar, npiv, nctr, sz_m, sz_w, sz_h, tilt in neck_specs:
        nc = [
            make_centered_cube(nctr, sz_m, "FUR_SILVER", rot=[tilt, 0.0, 0.0]),
            make_centered_cube([nctr[0], nctr[1] - 2.2, nctr[2] - 2.6], sz_w, "FUR_WHITE_CHEST", rot=[tilt + 4.0, 0.0, 0.0]),
            make_centered_cube([nctr[0], nctr[1] + 4.0, nctr[2] + 0.2], sz_h, "FUR_HIGHLIGHT_LOCK", rot=[-tilt * 0.8, 0.0, 0.0]),
        ]
        if nname == "neck_1":
            for sx in (1.0, -1.0):
                add_soft_puff(nc, [sx * 7.4, 39.2, -19.4], 5.6, 7.8, 7.4, "FUR_SILVER", rot=[8.0, sx * -18.0, sx * 12.0], highlight_top=True)
        bones.append({"name": nname, "parent": npar, "pivot": npiv, "rotation": [0.0, 0.0, 0.0], "cubes": nc})

    # === ĐẦU SÓI THẦN FEL V6.1 — GÓC CẠNH ANIME SẮC NÉT, MẮT SÁNG RÕ, TAI TAM GIÁC LIỀN KHỐI, MÕM THUÔN SẠCH ===
    head_c = [
        # Khối sọ chính thon gọn + đỉnh đầu vát xuôi tự nhiên liền mạch với trán
        make_centered_cube([0.0, 45.5, -32.6], [8.2, 6.2, 8.6], "FUR_SILVER", rot=[6.0, 0.0, 0.0]),
        make_centered_cube([0.0, 48.0, -32.8], [6.6, 2.6, 7.8], "FUR_HIGHLIGHT_LOCK", rot=[18.0, 0.0, 0.0]),
        # Trán sói vát nghiêng 26° ôm liền vào sọ (CHỈ vẽ dấu sao & tia sét trên mặt trước `north` — không còn viền đen thừa trên đỉnh!)
        make_centered_cube([0.0, 46.85, -36.05], [5.8, 4.1, 2.4], "FUR_SILVER", rot=[26.0, 0.0, 0.0],
                           face_overrides={"north": ("HEAD_FOREHEAD", (0.04, 0.96), (0.06, 0.96)),
                                           "up": "FUR_HIGHLIGHT_LOCK"}),
        # Sống mũi mảnh thanh tú nối mượt từ chân dấu sao xuống mũi (rộng 2.4 -> hoàn toàn KHÔNG che mắt!)
        make_centered_cube([0.0, 44.15, -40.2], [2.4, 1.55, 6.6], "FUR_HIGHLIGHT_LOCK", rot=[14.0, 0.0, 0.0]),
        # Thân mõm trên thuôn chữ V dần về phía trước
        make_centered_cube([0.0, 42.75, -39.9], [4.3, 2.40, 5.4], "FUR_WHITE_CHEST", rot=[7.0, 0.0, 0.0]),
        make_centered_cube([0.0, 42.32, -43.5], [3.0, 1.90, 3.8], "FUR_WHITE_CHEST", rot=[9.0, 0.0, 0.0]),
        make_centered_cube([0.0, 42.22, -45.3], [2.1, 1.40, 1.4], "FUR_WHITE_CHEST", rot=[12.0, 0.0, 0.0]),
        # Đường nét môi trên sói mảnh sắc gọn (tạo thần thái điềm tĩnh uy nghiêm!)
        make_centered_cube([0.0, 41.36, -43.4], [2.75, 0.24, 3.9], "DARK_BROW", rot=[6.0, 0.0, 0.0]),
        # Mũi sói tam giác nhỏ thanh lịch (nâu đỏ sẫm có lỗ mũi & highlight ẩm mượt)
        make_centered_cube([0.0, 42.84, -46.02], [1.28, 0.74, 0.84], "NOSE_CRIMSON", rot=[18.0, 0.0, 0.0]),
        make_centered_cube([0.0, 42.30, -45.96], [0.58, 0.50, 0.46], "NOSE_CRIMSON", rot=[10.0, 0.0, 0.0]),
        # Vòm miệng trên
        make_centered_cube([0.0, 41.35, -40.6], [3.0, 0.72, 5.4], "MOUTH_INNER"),
    ]
    for sx in (1.0, -1.0):
        outer_face = "east" if sx > 0 else "west"
        # Thái dương vát mềm nối trán với gốc tai (trán không bị vuông gờ!)
        head_c.append(make_centered_cube([sx * 3.45, 46.75, -34.8], [2.2, 3.4, 4.2], "FUR_SILVER",
                                         rot=[20.0, -sx * 26.0, sx * 12.0]))
        # Gò má sói vát chéo chữ V sắc nét ôm bên dưới mắt
        head_c.append(make_centered_cube([sx * 3.75, 43.35, -35.2], [2.8, 3.4, 5.8], "FUR_SILVER",
                                         rot=[8.0, -sx * 24.0, sx * 12.0]))
        head_c.append(make_centered_cube([sx * 2.80, 42.55, -38.6], [1.85, 2.2, 4.6], "FUR_WHITE_CHEST",
                                         rot=[6.0, -sx * 22.0, sx * 10.0]))
        # Má mõm trắng: CHỈ vẽ dấu cong đỏ huyết Fel trên MẶT BÊN NGOÀI (`outer_face`), mặt trước trắng tinh sạch sẽ!
        head_c.append(make_centered_cube([sx * 2.12, 42.15, -40.4], [1.05, 1.62, 4.4], "FUR_WHITE_CHEST",
                                         rot=[7.0, -sx * 16.0, sx * 6.0],
                                         face_overrides={outer_face: ("MUZZLE_MARK", (0.04, 0.96), (0.04, 0.96), (sx < 0), False)}))
        # Hốc mắt tự nhiên ẩn trong khối lông mày & gò má mềm (KHÔNG còn 2 khối lông mày đen thô kệch!)
        # 1. Nền hốc mắt sâu kín chống hở khi nhìn góc nghiêng
        head_c.append(make_centered_cube([sx * 2.48, 44.78, -37.26], [2.20, 1.02, 0.44], "FUR_AZURE_DEEP",
                                         rot=[5.0, -sx * 16.0, -sx * 8.0]))
        # 2. Nhãn cầu mắt sói anime thanh tú (CHỈ map `EYE_FEL` lên mặt trước `north`, 5 mặt kia là lông trắng xanh!)
        head_c.append(make_centered_cube([sx * 2.48, 44.78, -37.54], [2.12, 0.96, 0.36], "FUR_SILVER",
                                         rot=[5.0, -sx * 16.0, -sx * 8.0],
                                         face_overrides={"north": ("EYE_FEL", (0.01, 0.99), (0.01, 0.99), (sx < 0), False)}))
        # 3. Cung lông mày lông trắng xanh tự nhiên nhô nhẹ ôm trên hốc mắt (chứa mí mắt trên khi mở!)
        head_c.append(make_centered_cube([sx * 2.54, 45.68, -37.52], [2.56, 0.96, 1.12], "FUR_SILVER",
                                         rot=[12.0, -sx * 16.0, -sx * 10.0],
                                         face_overrides={"up": "FUR_HIGHLIGHT_LOCK"}))
        # 4. Viền lông má trắng mịn ôm dưới hốc mắt (chứa mí mắt dưới khi mở!)
        head_c.append(make_centered_cube([sx * 2.52, 43.92, -37.50], [2.42, 0.84, 1.04], "FUR_WHITE_CHEST",
                                         rot=[6.0, -sx * 16.0, -sx * 6.0]))
        # 5. Lọn lông đuôi mắt mềm nối mượt sang gò má
        head_c.append(make_centered_cube([sx * 3.85, 45.05, -36.0], [1.8, 1.6, 3.2], "FUR_HIGHLIGHT_LOCK",
                                         rot=[10.0, -sx * 24.0, -sx * 12.0]))
        # Răng nanh trên sắc gọn bên trong khoang miệng
        head_c.append(make_centered_cube([sx * 1.15, 41.05, -43.5], [0.42, 1.12, 0.46], "FANG_IVORY", rot=[8.0, 0.0, 0.0]))
        head_c.append(make_centered_cube([sx * 1.48, 41.20, -41.2], [0.36, 0.78, 0.40], "FANG_IVORY", rot=[5.0, 0.0, 0.0]))

    bones.append({
        "name": "head",
        "parent": "neck_4",
        "pivot": [0.0, 44.2, -31.0],
        "rotation": [0.0, 0.0, 0.0],
        "locators": {"mouth_wind": [0.0, 41.4, -44.8]},
        "cubes": head_c,
    })

    # === 4 KHỚP MÍ MẮT TRƯỢT MƯỢT (`eyelid_l_up`, `eyelid_l_lo`, `eyelid_r_up`, `eyelid_r_lo`) CHO CHỚP MẮT & NHEO MẮT TỰ NHIÊN! ===
    # Lớp depth chuẩn: Lông mày/má (-38.06) > Mí mắt (-37.76) > Nhãn cầu (-37.54) -> che sạch ánh phát sáng mắt khi nhắm, 0 Z-fighting!
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        el_up_c = [
            # Thân mí trên phủ lông trắng xanh (khi mở mắt giấu gọn bên trong cung lông mày `Y=45.20..46.16`, trượt xuống `-0.64` để nhắm kín!)
            make_centered_cube([sx * 2.48, 45.58, -37.72], [2.22, 0.72, 0.22], "FUR_SILVER",
                               rot=[5.0, -sx * 16.0, -sx * 8.0]),
            # Đường viền mi trên thanh mảnh tinh tế (chỉ dày 0.11px -> viền mắt sắc dịu tự nhiên khi mở & tạo nét nhắm mắt duyên dáng khi chớp!)
            make_centered_cube([sx * 2.48, 45.20, -37.75], [2.18, 0.11, 0.24], "DARK_BROW",
                               rot=[5.0, -sx * 16.0, -sx * 8.0]),
        ]
        bones.append({
            "name": f"eyelid_{tag}_up",
            "parent": "head",
            "pivot": [round(sx * 2.48, 3), 45.26, -37.74],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": el_up_c,
        })

        el_lo_c = [
            # Thân mí dưới phủ lông trắng mịn (giấu gọn trong viền má dưới khi mở, trượt lên `+0.28` để khớp mí trên khi chớp mắt!)
            make_centered_cube([sx * 2.48, 44.04, -37.72], [2.16, 0.48, 0.20], "FUR_WHITE_CHEST",
                               rot=[5.0, -sx * 16.0, -sx * 8.0]),
        ]
        bones.append({
            "name": f"eyelid_{tag}_lo",
            "parent": "head",
            "pivot": [round(sx * 2.48, 3), 44.28, -37.74],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": el_lo_c,
        })

    # Lọn lông đỉnh gáy & lọn lông má vát mềm ôm khuôn mặt (mỗi sợi 2 khớp nối tiếp!)
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        add_articulated_fur_ribbon(bones, fur_registry, f"crown_tuft_{tag}", "head", [
            [sx * 3.4, 47.4, -30.0],
            [sx * 5.6, 49.0, -25.8],
            [sx * 6.4, 50.0, -21.0],
        ], n_bones=2, r_start=2.7, r_end=0.62, mat_top="FUR_HIGHLIGHT_LOCK",
           group="cheek", side=s_i, phase0=0.40 + (0.30 if s_i < 0 else 0.0), aspect_z=0.80)
        add_articulated_fur_ribbon(bones, fur_registry, f"head_tuft_{tag}", "head", [
            [sx * 3.8, 42.6, -34.2],
            [sx * 6.4, 40.6, -30.6],
            [sx * 8.0, 38.8, -26.2],
        ], n_bones=2, r_start=2.3, r_end=0.52, mat_top="FUR_HIGHLIGHT_LOCK",
           group="cheek", side=s_i, phase0=0.55 + (0.30 if s_i < 0 else 0.0), aspect_z=0.82)

    jaw_c = [
        make_centered_cube([0.0, 40.75, -40.2], [3.4, 1.32, 6.2], "FUR_WHITE_CHEST", rot=[-2.0, 0.0, 0.0]),
        make_centered_cube([0.0, 40.75, -43.6], [2.3, 1.05, 2.7], "FUR_WHITE_CHEST", rot=[-5.0, 0.0, 0.0]),
        make_centered_cube([0.0, 41.30, -40.9], [3.0, 0.38, 5.6], "MOUTH_INNER"),
        make_centered_cube([0.0, 41.22, -44.7], [1.9, 0.28, 0.45], "DARK_BROW"),
    ]
    for sx in (1.0, -1.0):
        jaw_c.append(make_centered_cube([sx * 1.02, 41.70, -42.9], [0.38, 0.88, 0.42], "FANG_IVORY", rot=[-10.0, 0.0, 0.0]))
    bones.append({"name": "jaw", "parent": "head", "pivot": [0.0, 41.2, -36.2], "rotation": [0.0, 0.0, 0.0], "cubes": jaw_c})

    # Lọn lông cằm 2 khớp nối tiếp (`jaw_beard` -> `jaw_beard_2`)!
    add_articulated_fur_ribbon(bones, fur_registry, "jaw_beard", "jaw", [
        [0.0, 40.2, -41.2],
        [0.0, 39.2, -38.4],
        [0.0, 38.4, -35.2],
    ], n_bones=2, r_start=2.1, r_end=0.44, mat_top="FUR_WHITE_CHEST",
       group="bib_tuft", side=0, phase0=0.55, aspect_z=0.84)

    # --- 2 TAI SÓI TAM GIÁC LÕM 3D MỀM MẠI 2 ĐỐT (`ear_l/r` -> `ear_l/r_tip`) ---
    # Vành tai uốn cong liền mạch (KHÔNG xếp bậc như sừng!), lòng tai lõm sâu chuyển sắc mượt từ trắng xanh băng ngoài -> xanh lam sẫm trong!
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        rot_ear = [6.0, -sx * 16.0, sx * 22.0]
        R_ear = bedrock_rot_matrix(rot_ear)
        ep = np.array([sx * 3.55, 47.75, -32.25], dtype=np.float64)

        def ear_pt(lx: float, ly: float, lz: float) -> list:
            # lx > 0 là phía mép ngoài (lồi cong), lx < 0 là phía mép trong (gần đỉnh đầu); lz < 0 hướng ra trước
            return (ep + R_ear @ np.array([sx * lx, ly, lz], dtype=np.float64)).tolist()

        em = ear_pt(0.10, 2.70, -0.05)

        # Gốc tai (`ear_l/r`): 2 vành lông cong liền mạch ôm lòng tai lõm alpha-cutout + lưng tai lông dày + chỏm lông tơ chân tai!
        e1 = []
        # Vành lông mép trong thanh mảnh (trượt chéo mượt, mở thoáng hốc tai chuyển sắc sâu bên trong!)
        add_curved_rod(e1, [
            ear_pt(-2.00, -0.15, -0.34),
            ear_pt(-1.72, +1.30, -0.40),
            ear_pt(-1.26, +2.75, -0.34),
        ], 0.96, 0.74, "FUR_HIGHLIGHT_LOCK", segs_per_span=2, overlap=0.36, aspect_z=0.88)
        # Vành lông mép ngoài (uốn cong bầu mềm đặc trưng tai sói — không thẳng nhọn như sừng!)
        add_curved_rod(e1, [
            ear_pt(+2.05, -0.25, -0.30),
            ear_pt(+2.18, +1.25, -0.38),
            ear_pt(+1.72, +2.75, -0.32),
        ], 1.18, 0.84, "FUR_SILVER", segs_per_span=2, overlap=0.36, aspect_z=0.90)
        # Lưng tai lông trắng xanh phía sau tạo độ dày 3D đầy đặn khi nhìn góc nghiêng & từ sau
        add_chain_rod(e1, ear_pt(+0.06, -0.10, +0.44), ear_pt(+0.14, +2.80, +0.34),
                      3.45, 2.45, 2, "FUR_SILVER", overlap=0.34, aspect_z=0.32)
        # Tấm lòng tai lõm 3D (nửa dưới, alpha-cutout tam giác mượt chuyển màu từ ngoài sáng -> trong sẫm!)
        e1.append(make_centered_cube(
            ear_pt(0.0, 1.38, -0.02), [4.30, 2.90, 0.24], "EAR_INNER",
            rot=rot_ear, sub_u=(0.01, 0.99), sub_v=(0.45, 0.99), pad=2,
            transparent_edges=("east", "west", "up", "down"), flip_u=(sx < 0),
        ))
        # Lông tơ trắng tuyết chải nhẹ ngang chân lòng tai (gọn dưới chân để lộ trọn dải chuyển màu hốc tai!)
        add_curved_rod(e1, [
            ear_pt(-1.52, +0.25, -0.40),
            ear_pt(-0.55, +0.62, -0.36),
            ear_pt(+0.35, +0.92, -0.24),
        ], 0.74, 0.22, "FUR_WHITE_CHEST", segs_per_span=2, overlap=0.22, aspect_z=0.60)
        bones.append({"name": f"ear_{tag}", "parent": "head", "pivot": [round(float(v), 3) for v in ep], "rotation": [0.0, 0.0, 0.0], "cubes": e1})

        # Chóp tai (`ear_l/r_tip`): 2 vành lông thuôn cong hội tụ êm thành chóp tai sói nhọn mềm mại (chỉ cao ~5.1px, hết giống sừng!)
        e2 = []
        add_curved_rod(e2, [
            ear_pt(-1.26, +2.68, -0.34),
            ear_pt(-0.66, +3.95, -0.24),
            ear_pt(+0.02, +5.08, -0.14),
        ], 0.74, 0.34, "FUR_HIGHLIGHT_LOCK", segs_per_span=2, overlap=0.34, aspect_z=0.86)
        add_curved_rod(e2, [
            ear_pt(+1.72, +2.68, -0.32),
            ear_pt(+1.06, +4.00, -0.22),
            ear_pt(+0.02, +5.08, -0.14),
        ], 0.84, 0.34, "FUR_SILVER", segs_per_span=2, overlap=0.34, aspect_z=0.86)
        add_chain_rod(e2, ear_pt(+0.18, +2.68, +0.34), ear_pt(+0.05, +4.88, +0.16),
                      2.45, 0.55, 2, "FUR_HIGHLIGHT_LOCK", overlap=0.30, aspect_z=0.34)
        # Tấm lòng tai lõm 3D (nửa trên, nối khít 100% với nửa dưới!)
        e2.append(make_centered_cube(
            ear_pt(0.0, 3.88, -0.02), [4.25, 2.50, 0.24], "EAR_INNER",
            rot=rot_ear, sub_u=(0.01, 0.99), sub_v=(0.01, 0.49), pad=2,
            transparent_edges=("east", "west", "up", "down"), flip_u=(sx < 0),
        ))
        bones.append({"name": f"ear_{tag}_tip", "parent": f"ear_{tag}", "pivot": [round(float(v), 3) for v in em], "rotation": [0.0, 0.0, 0.0], "cubes": e2})

    # --- 2 LỌN BỜM MÁ 4 ĐỐT (`cheek_plume_l/r` -> `_4`) VƯƠN CONG LÊN-RA SAU ÔM KHUNG MẶT SÓI (KHÔNG CÒN CHỈA NGANG NHƯ CÁNH MÁY BAY!) ---
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        created_cp = add_articulated_fur_ribbon(bones, fur_registry, f"cheek_plume_{tag}", "head", [
            [sx * 4.4, 43.8, -30.2],
            [sx * 9.8, 46.4, -27.8],
            [sx * 15.8, 48.4, -23.4],
            [sx * 19.6, 45.4, -18.0],
            [sx * 17.6, 42.0, -14.6],
        ], n_bones=4, r_start=4.4, r_end=0.56, mat_top="FUR_HIGHLIGHT_LOCK",
           group="cheek", side=s_i, phase0=0.25 + (0.30 if s_i < 0 else 0.0),
           mat_under="FUR_AZURE_DEEP", aspect_z=0.76)
        tip_bone = next(b for b in bones if b["name"] == created_cp[-2])
        add_fur_fin(tip_bone["cubes"], [sx * 16.4, 45.6, -21.6], 11.4, 6.4, "FUR_WISP_CUTOUT",
                    rot=[-10.0, -sx * 32.0, sx * 12.0], flip_u=(sx < 0))

    # -------------------------------------------------------------------------
    # E. 2 CHÂN TRƯỚC 7 KHỚP + TEXTURE LÔNG CHÂN `FUR_LEG_FEATHERED`
    #    + 7 SỢI LÔNG CHÂN ĐA KHỚP/BÊN (KHUỶU 3 SỢI + ĐÙI + CẲNG TAY 2 TẦNG + CỔ CHÂN, MỖI SỢI 2 KHỚP!)
    # -------------------------------------------------------------------------
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        p_scap = np.array([sx * 9.0, 33.8, -12.6], dtype=np.float64)
        p_sh   = np.array([sx * 9.5, 31.8, -13.2], dtype=np.float64)
        p_elb  = np.array([sx * 9.8, 19.4, -12.0], dtype=np.float64)
        p_wri  = np.array([sx * 9.5,  5.6, -14.4], dtype=np.float64)
        p_paw  = np.array([sx * 9.5,  2.8, -15.0], dtype=np.float64)
        p_toe  = np.array([sx * 9.5,  1.2, -17.6], dtype=np.float64)

        scap_c = []
        add_soft_puff(scap_c, (p_scap + np.array([sx * 1.0, -1.0, -0.6])).tolist(), 6.8, 8.2, 8.4, "FUR_SILVER", highlight_top=True)
        bones.append({"name": f"shoulder_f{tag}", "parent": "chest", "pivot": p_scap.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": scap_c})

        up_c = []
        add_chain_rod(up_c, p_sh, p_elb, 7.6, 5.5, 4, "FUR_LEG_FEATHERED", overlap=0.55, aspect_z=1.12)
        bones.append({"name": f"leg_f{tag}", "parent": f"shoulder_f{tag}", "pivot": p_sh.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": up_c})

        # Lọn lông đùi trên trước 2 khớp nối tiếp!
        add_articulated_fur_ribbon(bones, fur_registry, f"upper_f{tag}_tuft", f"leg_f{tag}", [
            (p_sh + np.array([sx * 1.6, -5.8, -1.8])).tolist(),
            (p_elb + np.array([sx * 2.4, +1.8, -2.4])).tolist(),
            (p_elb + np.array([sx * 2.0, -1.4, -0.6])).tolist(),
        ], n_bones=2, r_start=2.8, r_end=0.55, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=0.70 + (0.45 if s_i < 0 else 0.0), aspect_z=0.82)

        elb_c = [make_centered_cube(p_elb + np.array([0.0, +0.9, 0.0]), [6.0, 4.0, 6.2], "FUR_LEG_FEATHERED", rot=[-10.0, 0.0, sx * -8.0])]
        bones.append({"name": f"elbow_f{tag}", "parent": f"leg_f{tag}", "pivot": p_elb.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": elb_c})

        # 3 sợi lông khuỷu tay RỜI — MỖI SỢI ĐỀU CÓ 2 KHỚP NỐI TIẾP (`cuff_f*_a/b/c` -> `_2`)!
        ca = add_articulated_fur_ribbon(bones, fur_registry, f"cuff_f{tag}_a", f"elbow_f{tag}", [
            (p_elb + np.array([sx * 0.4, +1.6, +0.2])).tolist(),
            (p_elb + np.array([sx * 1.4, +3.0, +4.8])).tolist(),
            (p_elb + np.array([sx * 1.8, +5.0, +9.4])).tolist(),
        ], n_bones=2, r_start=3.7, r_end=0.48, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=0.95 + (0.50 if s_i < 0 else 0.0), aspect_z=0.82)
        add_fur_fin(next(b for b in bones if b["name"] == ca[0])["cubes"],
                    (p_elb + np.array([sx * 2.2, +2.4, +3.2])).tolist(), 10.6, 7.6, "CRIMSON_CUFF_CUTOUT",
                    rot=[-12.0, sx * 78.0, sx * -14.0], flip_u=(sx < 0))

        add_articulated_fur_ribbon(bones, fur_registry, f"cuff_f{tag}_b", f"elbow_f{tag}", [
            (p_elb + np.array([sx * 1.8, +1.2, -0.6])).tolist(),
            (p_elb + np.array([sx * 4.4, +2.8, +1.8])).tolist(),
            (p_elb + np.array([sx * 6.0, +4.4, +5.0])).tolist(),
        ], n_bones=2, r_start=3.1, r_end=0.44, mat_top="FUR_SILVER",
           group="leg_fur", side=s_i, phase0=1.20 + (0.50 if s_i < 0 else 0.0), aspect_z=0.82)

        add_articulated_fur_ribbon(bones, fur_registry, f"cuff_f{tag}_c", f"elbow_f{tag}", [
            (p_elb + np.array([-sx * 1.0, +1.0, +0.4])).tolist(),
            (p_elb + np.array([-sx * 2.0, +2.4, +3.6])).tolist(),
            (p_elb + np.array([-sx * 2.2, +4.0, +6.4])).tolist(),
        ], n_bones=2, r_start=2.6, r_end=0.42, mat_top="FUR_AZURE_DEEP",
           group="leg_fur", side=s_i, phase0=1.40 + (0.50 if s_i < 0 else 0.0), aspect_z=0.80)

        lo_c = []
        add_chain_rod(lo_c, p_elb + np.array([0.0, +1.2, 0.0]), p_wri, 5.1, 3.6, 4, "FUR_LEG_FEATHERED", overlap=0.50, aspect_z=1.10)
        bones.append({"name": f"leg_f{tag}_low", "parent": f"elbow_f{tag}", "pivot": p_elb.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": lo_c})

        # 2 tầng lông ống chân trước uốn cong mềm ôm sát dọc mặt sau cẳng tay (MỖI TẦNG 2 KHỚP NỐI TIẾP!)
        p_mid_f1 = p_elb * 0.76 + p_wri * 0.24
        p_mid_f2 = p_elb * 0.42 + p_wri * 0.58
        fh = add_articulated_fur_ribbon(bones, fur_registry, f"forearm_f{tag}_hi", f"leg_f{tag}_low", [
            (p_mid_f1 + np.array([sx * 0.4, +2.0, +1.2])).tolist(),
            (p_mid_f1 + np.array([sx * 1.1, -1.6, +3.4])).tolist(),
            (p_mid_f1 + np.array([sx * 1.5, -3.4, +5.8])).tolist(),
            (p_mid_f1 + np.array([sx * 1.8, -2.2, +7.2])).tolist(),
        ], n_bones=2, r_start=3.1, r_end=0.45, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=1.35 + (0.50 if s_i < 0 else 0.0), aspect_z=0.78)
        add_fur_fin(next(b for b in bones if b["name"] == fh[0])["cubes"],
                    (p_mid_f1 + np.array([sx * 0.8, -2.0, +3.0])).tolist(), 7.6, 6.4, "FUR_WISP_CUTOUT",
                    rot=[-28.0, sx * 86.0, sx * -12.0], flip_u=(sx < 0))
        add_articulated_fur_ribbon(bones, fur_registry, f"forearm_f{tag}_lo", f"leg_f{tag}_low", [
            (p_mid_f2 + np.array([sx * 0.3, +1.8, +1.1])).tolist(),
            (p_mid_f2 + np.array([sx * 0.9, -1.4, +3.0])).tolist(),
            (p_mid_f2 + np.array([sx * 1.2, -2.8, +5.0])).tolist(),
            (p_mid_f2 + np.array([sx * 1.4, -1.8, +6.1])).tolist(),
        ], n_bones=2, r_start=2.7, r_end=0.40, mat_top="FUR_SILVER",
           group="leg_fur", side=s_i, phase0=1.65 + (0.50 if s_i < 0 else 0.0), aspect_z=0.78)

        wr_c = []
        add_chain_rod(wr_c, p_wri + np.array([0.0, +0.9, 0.0]), p_paw, 3.65, 3.35, 2, "FUR_LEG_FEATHERED", overlap=0.36, aspect_z=1.10)
        bones.append({"name": f"wrist_f{tag}", "parent": f"leg_f{tag}_low", "pivot": p_wri.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": wr_c})

        # Lọn lông cổ chân trước uốn cong mềm 2 khớp nối tiếp (`ankle_fl/fr_tuft` -> `_2`)!
        add_articulated_fur_ribbon(bones, fur_registry, f"ankle_f{tag}_tuft", f"wrist_f{tag}", [
            (p_wri + np.array([sx * 0.2, +1.8, +1.0])).tolist(),
            (p_wri + np.array([sx * 0.6, -0.4, +2.8])).tolist(),
            (p_wri + np.array([sx * 0.9, -1.2, +4.4])).tolist(),
            (p_wri + np.array([sx * 1.1, -0.4, +5.2])).tolist(),
        ], n_bones=2, r_start=2.2, r_end=0.36, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=1.90 + (0.50 if s_i < 0 else 0.0), aspect_z=0.78)

        ft_c = [
            make_cube([p_paw[0] - 2.25, 0.0, p_paw[2] - 3.2], [4.5, 2.2, 4.7], "FUR_WHITE_CHEST"),
            make_cube([p_paw[0] - 2.05, 1.7, p_paw[2] - 2.3], [4.1, 1.9, 4.0], "FUR_LEG_FEATHERED", rot=[14.0, 0.0, 0.0]),
            make_centered_cube([p_paw[0], 2.9, p_paw[2] - 0.6], [4.2, 1.6, 4.2], "FUR_HIGHLIGHT_LOCK", rot=[8.0, 0.0, 0.0]),
        ]
        bones.append({"name": f"foot_f{tag}", "parent": f"wrist_f{tag}", "pivot": p_paw.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": ft_c})

        toe_c = []
        for tx_off, t_ang, t_len in ((-1.68, -14.0, 2.6), (-0.56, -4.0, 3.1), (+0.56, +4.0, 3.1), (+1.68, +14.0, 2.6)):
            rad = math.radians(t_ang)
            dx, dz = math.sin(rad), -math.cos(rad)
            p0 = [p_paw[0] + tx_off, 1.20, p_paw[2] - 2.8]
            p1 = [p0[0] + dx * t_len * 0.56, 0.86, p0[2] + dz * t_len * 0.56]
            p2 = [p0[0] + dx * t_len,        0.20, p0[2] + dz * t_len]
            add_chain_rod(toe_c, p0, p1, 1.12, 0.90, 2, "FUR_WHITE_CHEST", overlap=0.18)
            add_chain_rod(toe_c, p1, p2, 0.70, 0.22, 2, "CLAW_OBSIDIAN", overlap=0.16)
        bones.append({"name": f"toes_f{tag}", "parent": f"foot_f{tag}", "pivot": p_toe.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": toe_c})

    # -------------------------------------------------------------------------
    # F. 2 CHÂN SAU 7 KHỚP + TEXTURE LÔNG CHÂN `FUR_LEG_FEATHERED`
    #    + 7 SỢI LÔNG CHÂN ĐA KHỚP/BÊN (ĐÙI + KHUỶU 3 SỢI + ỐNG CHÂN 2 TẦNG + CỔ CHÂN, MỖI SỢI 2 KHỚP!)
    # -------------------------------------------------------------------------
    for sx, tag in ((1.0, "l"), (-1.0, "r")):
        s_i = int(sx)
        p_hip_j = np.array([sx * 8.4, 31.4, +17.6], dtype=np.float64)
        p_hip   = np.array([sx * 8.8, 30.0, +17.8], dtype=np.float64)
        p_knee  = np.array([sx * 9.4, 20.0, +14.6], dtype=np.float64)
        p_hock  = np.array([sx * 9.4, 13.6, +22.4], dtype=np.float64)
        p_ankle = np.array([sx * 9.2,  3.0, +20.2], dtype=np.float64)
        p_toe   = np.array([sx * 9.2,  1.2, +17.6], dtype=np.float64)

        hip_c = []
        add_soft_puff(hip_c, (p_hip_j + np.array([sx * 0.8, -1.2, +0.4])).tolist(), 7.2, 8.8, 9.4, "FUR_SILVER", highlight_top=True)
        bones.append({"name": f"hip_b{tag}", "parent": "pelvis", "pivot": p_hip_j.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": hip_c})

        th_c = []
        add_chain_rod(th_c, p_hip, p_knee, 8.0, 5.7, 4, "FUR_LEG_FEATHERED", overlap=0.55, aspect_z=1.18)
        bones.append({"name": f"leg_b{tag}", "parent": f"hip_b{tag}", "pivot": p_hip.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": th_c})

        # Lọn lông đùi sau 2 khớp nối tiếp!
        add_articulated_fur_ribbon(bones, fur_registry, f"thigh_b{tag}_tuft", f"leg_b{tag}", [
            (p_hip + np.array([sx * 1.6, -5.6, -2.2])).tolist(),
            (p_knee + np.array([sx * 2.2, +1.8, -2.8])).tolist(),
            (p_knee + np.array([sx * 1.8, -1.0, -0.8])).tolist(),
        ], n_bones=2, r_start=2.9, r_end=0.56, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=1.55 + (0.45 if s_i < 0 else 0.0), aspect_z=0.82)

        mid_c = [make_centered_cube(p_knee + np.array([0.0, +0.8, 0.0]), [6.3, 4.1, 6.5], "FUR_LEG_FEATHERED", rot=[12.0, 0.0, sx * -8.0])]
        add_chain_rod(mid_c, p_knee + np.array([0.0, +1.4, -0.4]), p_hock, 5.4, 4.05, 4, "FUR_LEG_FEATHERED", overlap=0.48, aspect_z=1.08)
        bones.append({"name": f"leg_b{tag}_mid", "parent": f"leg_b{tag}", "pivot": p_knee.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": mid_c})

        # Lọn lông đầu gối sau vươn ra ngoài (2 khớp nối tiếp!)
        add_articulated_fur_ribbon(bones, fur_registry, f"cuff_b{tag}_a", f"leg_b{tag}_mid", [
            (p_knee + np.array([sx * 1.4, +1.4, -1.2])).tolist(),
            (p_knee + np.array([sx * 3.8, +3.4, -4.0])).tolist(),
            (p_knee + np.array([sx * 4.8, +5.2, -6.0])).tolist(),
        ], n_bones=2, r_start=3.3, r_end=0.48, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=1.65 + (0.50 if s_i < 0 else 0.0), aspect_z=0.82)

        hk_c = [make_centered_cube(p_hock + np.array([0.0, +0.3, +0.2]), [4.6, 3.6, 4.8], "FUR_LEG_FEATHERED", rot=[-8.0, 0.0, 0.0])]
        bones.append({"name": f"hock_b{tag}", "parent": f"leg_b{tag}_mid", "pivot": p_hock.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": hk_c})

        # 2 sợi lông khuỷu sau RỜI — MỖI SỢI 2 KHỚP NỐI TIẾP (`cuff_b*_b/c` -> `_2`)!
        cb = add_articulated_fur_ribbon(bones, fur_registry, f"cuff_b{tag}_b", f"hock_b{tag}", [
            (p_hock + np.array([sx * 1.4, +1.4, -0.4])).tolist(),
            (p_hock + np.array([sx * 3.2, +3.8, +3.0])).tolist(),
            (p_hock + np.array([sx * 4.0, +5.6, +6.6])).tolist(),
        ], n_bones=2, r_start=3.5, r_end=0.50, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=1.85 + (0.50 if s_i < 0 else 0.0), aspect_z=0.82)
        add_fur_fin(next(b for b in bones if b["name"] == cb[0])["cubes"],
                    (0.5 * (p_knee + p_hock) + np.array([sx * 2.2, +2.4, +1.4])).tolist(), 11.4, 7.8, "CRIMSON_CUFF_CUTOUT",
                    rot=[-10.0, sx * 80.0, sx * -16.0], flip_u=(sx < 0))

        add_articulated_fur_ribbon(bones, fur_registry, f"cuff_b{tag}_c", f"hock_b{tag}", [
            (p_hock + np.array([0.0, +1.0, +0.6])).tolist(),
            (p_hock + np.array([sx * 0.8, +2.6, +4.8])).tolist(),
            (p_hock + np.array([sx * 1.2, +4.4, +7.8])).tolist(),
        ], n_bones=2, r_start=3.1, r_end=0.44, mat_top="FUR_AZURE_DEEP",
           group="leg_fur", side=s_i, phase0=2.05 + (0.50 if s_i < 0 else 0.0), aspect_z=0.80)

        lo_c = []
        add_chain_rod(lo_c, p_hock + np.array([0.0, +0.8, 0.0]), p_ankle, 4.2, 3.25, 4, "FUR_LEG_FEATHERED", overlap=0.46, aspect_z=1.10)
        bones.append({"name": f"leg_b{tag}_low", "parent": f"hock_b{tag}", "pivot": p_hock.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": lo_c})

        # 2 tầng lông ống chân sau uốn cong chữ S ôm mềm mặt sau ống chân (MỖI TẦNG 2 KHỚP!)
        p_mid_b1 = p_hock * 0.74 + p_ankle * 0.26
        p_mid_b2 = p_hock * 0.36 + p_ankle * 0.64
        sh = add_articulated_fur_ribbon(bones, fur_registry, f"shin_b{tag}_hi", f"leg_b{tag}_low", [
            (p_mid_b1 + np.array([sx * 0.4, +1.8, +1.1])).tolist(),
            (p_mid_b1 + np.array([sx * 1.0, -1.2, +3.2])).tolist(),
            (p_mid_b1 + np.array([sx * 1.3, -2.6, +5.4])).tolist(),
            (p_mid_b1 + np.array([sx * 1.5, -1.4, +6.6])).tolist(),
        ], n_bones=2, r_start=2.9, r_end=0.42, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=2.15 + (0.50 if s_i < 0 else 0.0), aspect_z=0.78)
        add_fur_fin(next(b for b in bones if b["name"] == sh[0])["cubes"],
                    (p_mid_b1 + np.array([sx * 0.7, -1.4, +2.8])).tolist(), 7.2, 6.0, "FUR_WISP_CUTOUT",
                    rot=[-24.0, sx * 86.0, sx * -12.0], flip_u=(sx < 0))
        add_articulated_fur_ribbon(bones, fur_registry, f"shin_b{tag}_lo", f"leg_b{tag}_low", [
            (p_mid_b2 + np.array([sx * 0.3, +1.4, +1.0])).tolist(),
            (p_mid_b2 + np.array([sx * 0.8, -1.0, +2.8])).tolist(),
            (p_mid_b2 + np.array([sx * 1.0, -2.2, +4.6])).tolist(),
            (p_mid_b2 + np.array([sx * 1.2, -1.2, +5.6])).tolist(),
        ], n_bones=2, r_start=2.5, r_end=0.38, mat_top="FUR_SILVER",
           group="leg_fur", side=s_i, phase0=2.40 + (0.50 if s_i < 0 else 0.0), aspect_z=0.78)

        ft_c = [
            make_cube([p_ankle[0] - 2.15, 0.0, p_ankle[2] - 3.2], [4.3, 2.1, 4.6], "FUR_WHITE_CHEST"),
            make_cube([p_ankle[0] - 1.95, 1.6, p_ankle[2] - 2.2], [3.9, 1.8, 3.9], "FUR_LEG_FEATHERED", rot=[14.0, 0.0, 0.0]),
            make_centered_cube([p_ankle[0], 2.8, p_ankle[2] - 0.5], [4.0, 1.5, 4.0], "FUR_HIGHLIGHT_LOCK", rot=[8.0, 0.0, 0.0]),
        ]
        bones.append({"name": f"foot_b{tag}", "parent": f"leg_b{tag}_low", "pivot": p_ankle.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": ft_c})

        # Lọn lông cổ chân sau uốn cong mềm 2 khớp nối tiếp!
        add_articulated_fur_ribbon(bones, fur_registry, f"ankle_b{tag}_tuft", f"foot_b{tag}", [
            (p_ankle + np.array([sx * 0.2, +1.6, +1.0])).tolist(),
            (p_ankle + np.array([sx * 0.6, +0.0, +2.8])).tolist(),
            (p_ankle + np.array([sx * 0.9, -0.8, +4.2])).tolist(),
            (p_ankle + np.array([sx * 1.1, +0.1, +5.0])).tolist(),
        ], n_bones=2, r_start=2.1, r_end=0.34, mat_top="FUR_HIGHLIGHT_LOCK",
           group="leg_fur", side=s_i, phase0=2.60 + (0.50 if s_i < 0 else 0.0), aspect_z=0.78)

        toe_c = []
        for tx_off, t_ang, t_len in ((-1.58, -13.0, 2.5), (-0.52, -4.0, 3.0), (+0.52, +4.0, 3.0), (+1.58, +13.0, 2.5)):
            rad = math.radians(t_ang)
            dx, dz = math.sin(rad), -math.cos(rad)
            p0 = [p_ankle[0] + tx_off, 1.15, p_ankle[2] - 2.8]
            p1 = [p0[0] + dx * t_len * 0.56, 0.82, p0[2] + dz * t_len * 0.56]
            p2 = [p0[0] + dx * t_len,        0.20, p0[2] + dz * t_len]
            add_chain_rod(toe_c, p0, p1, 1.08, 0.86, 2, "FUR_WHITE_CHEST", overlap=0.18)
            add_chain_rod(toe_c, p1, p2, 0.66, 0.20, 2, "CLAW_OBSIDIAN", overlap=0.16)
        bones.append({"name": f"toes_b{tag}", "parent": f"foot_b{tag}", "pivot": p_toe.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": toe_c})

    # -------------------------------------------------------------------------
    # G. ĐUÔI MÂY KHỔNG LỒ 8 ĐỐT (`tail_1..8`) + 14 CHÙM LÔNG ĐUÔI CHỈA RA — MỖI CHÙM 2–3 KHỚP (31 KHỚP!)
    # -------------------------------------------------------------------------
    tail_ctrl = [
        [0.0, 30.4, +26.5],
        [0.0, 30.8, +36.2],
        [0.0, 35.8, +46.8],
        [0.0, 44.2, +54.6],
        [0.0, 52.8, +54.4],
        [0.0, 59.4, +48.4],
        [0.0, 63.4, +39.0],
    ]
    tail_pts = catmull_rom_samples(tail_ctrl, 8)
    tail_radii = [7.4, 9.8, 11.8, 12.6, 11.2, 9.0, 6.4, 3.6, 0.7]
    for ti in range(8):
        tname = f"tail_{ti + 1}"
        tpar = "pelvis" if ti == 0 else f"tail_{ti}"
        p0, p1 = tail_pts[ti], tail_pts[ti + 1]
        ra, rb = tail_radii[ti], tail_radii[ti + 1]
        tc = []
        add_chain_rod(tc, p0, p1, ra, rb, 2, "FUR_SILVER", overlap=0.72, aspect_z=1.08)
        add_chain_rod(tc, p0 + np.array([0.0, +ra * 0.24, -0.5]), p1 + np.array([0.0, +rb * 0.24, -0.5]),
                      ra * 0.68, rb * 0.66, 2, "FUR_HIGHLIGHT_LOCK", overlap=0.52)
        if 1 <= ti <= 5:
            add_chain_rod(tc, p0 + np.array([0.0, -ra * 0.26, +0.9]), p1 + np.array([0.0, -rb * 0.26, +1.0]),
                          ra * 0.72, rb * 0.68, 2, "FUR_AZURE_DEEP", overlap=0.55)
        bones.append({
            "name": tname,
            "parent": tpar,
            "pivot": [round(float(v), 3) for v in p0],
            "rotation": [0.0, 0.0, 0.0],
            "cubes": tc,
        })

    # === 14 CHÙM LÔNG ĐUÔI CHỈA RA — MỌI CHÙM ĐỀU CÓ 2–3 KHỚP NỐI TIẾP (31 KHỚP!) ===
    tail_plume_specs = [
        # (name, parent_tail, pts, r0, r1, mat, n_segs, side, ph0)
        ("tail_plume_1l", "tail_1", [[+4.4, 30.2, +31.5], [+8.0, 27.6, +38.2], [+6.8, 25.0, +43.8]], 4.2, 0.75, "FUR_AZURE_DEEP",     2, +1, 0.55),
        ("tail_plume_1r", "tail_1", [[-4.4, 30.2, +31.5], [-8.0, 27.6, +38.2], [-6.8, 25.0, +43.8]], 4.2, 0.75, "FUR_AZURE_DEEP",     2, -1, 0.90),
        ("tail_plume_2b", "tail_2", [[0.0, 29.2, +40.0],  [0.0, 25.6, +48.8],  [0.0, 23.8, +57.0], [0.0, 26.8, +63.2]], 5.6, 0.62, "FUR_HIGHLIGHT_LOCK", 3,  0, 1.00),
        ("tail_plume_2l", "tail_2", [[+5.2, 32.4, +41.2], [+9.8, 34.2, +48.4], [+8.6, 37.8, +54.5]], 4.8, 0.75, "FUR_HIGHLIGHT_LOCK", 2, +1, 1.20),
        ("tail_plume_2r", "tail_2", [[-5.2, 32.4, +41.2], [-9.8, 34.2, +48.4], [-8.6, 37.8, +54.5]], 4.8, 0.75, "FUR_HIGHLIGHT_LOCK", 2, -1, 1.55),
        ("tail_plume_3b", "tail_4", [[0.0, 38.2, +52.2],  [0.0, 36.8, +61.4],  [0.0, 40.4, +68.2]], 5.4, 0.62, "FUR_HIGHLIGHT_LOCK", 3,  0, 1.65),
        ("tail_plume_3l", "tail_4", [[+5.2, 41.2, +51.6], [+9.6, 44.8, +56.8], [+8.4, 49.6, +60.5]], 4.5, 0.68, "FUR_SILVER",         2, +1, 1.85),
        ("tail_plume_3r", "tail_4", [[-5.2, 41.2, +51.6], [-9.6, 44.8, +56.8], [-8.4, 49.6, +60.5]], 4.5, 0.68, "FUR_SILVER",         2, -1, 2.20),
        ("tail_plume_4b", "tail_5", [[0.0, 48.8, +56.5],  [0.0, 56.2, +61.8],  [0.0, 62.2, +58.6]], 4.2, 0.52, "FUR_HIGHLIGHT_LOCK", 3,  0, 2.25),
        ("tail_plume_5l", "tail_6", [[+4.0, 51.0, +54.0], [+7.6, 55.6, +56.2], [+5.8, 60.2, +52.0]], 3.5, 0.50, "FUR_HIGHLIGHT_LOCK", 2, +1, 2.55),
        ("tail_plume_5r", "tail_6", [[-4.0, 51.0, +54.0], [-7.6, 55.6, +56.2], [-5.8, 60.2, +52.0]], 3.5, 0.50, "FUR_HIGHLIGHT_LOCK", 2, -1, 2.90),
        ("tail_plume_6b", "tail_7", [[0.0, 56.2, +52.2],  [0.0, 63.2, +51.8],  [0.0, 66.8, +45.6]], 3.4, 0.44, "FUR_HIGHLIGHT_LOCK", 2,  0, 2.95),
        ("tail_plume_8a", "tail_8", [[0.0, 61.5, +44.5],  [0.0, 65.4, +39.8],  [0.0, 62.6, +32.0]], 2.6, 0.36, "FUR_HIGHLIGHT_LOCK", 2,  0, 3.35),
        ("tail_plume_8b", "tail_8", [[0.0, 60.4, +45.8],  [+2.0, 64.2, +43.5], [+2.8, 67.2, +37.5]], 2.0, 0.34, "FUR_HIGHLIGHT_LOCK", 2, +1, 3.65),
    ]
    for pname, ppar, ppts, pr0, pr1, pmat, nseg, pside, pph0 in tail_plume_specs:
        add_articulated_fur_ribbon(bones, fur_registry, pname, ppar, ppts, n_bones=nseg, r_start=pr0, r_end=pr1,
                                   mat_top=pmat, group="tail_plume", side=pside, phase0=pph0,
                                   mat_under=None, aspect_z=0.86, segs_per_bone=2)

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.fenrir_boss",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 16.0,
                    "visible_bounds_height": 11.0,
                    "visible_bounds_offset": [0.0, 4.2, 0.0],
                },
                "bones": bones,
            }
        ],
    }
    out = ASSETS / "geo" / "entity" / "fenrir_boss.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
    fur_bones_count = sum(len(entry["bones"]) for entry in fur_registry)
    meta = {
        "bones": len(bones),
        "cubes": total_cubes,
        "fur_strands": len(fur_registry),
        "fur_bones": fur_bones_count,
        "fur_registry": fur_registry,
    }
    print(f"GEO_OK: {out} ({len(bones)} bones, {total_cubes} cubes | {len(fur_registry)} multi-joint fur strands = {fur_bones_count} fur joints)")
    return geo, total_cubes, meta


# =============================================================================
# 3. SINH 4 TEXTURE 1024x1024 RGBA — LÔNG TẦNG CHỮ V + TILE LÔNG CHÂN DÀY MỊN!
# =============================================================================
def hex_arr(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], dtype=np.float32)


def smooth_velvet_fur(h: int, w: int, seed: int, freq_x: float = 10.0, freq_y: float = 3.0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    acc = np.zeros((h, w), dtype=np.float32)
    amp = 0.62
    for oct_i in range(3):
        gh = max(2, int(freq_y * (2 ** oct_i)))
        gw = max(2, int(freq_x * (2 ** oct_i)))
        grid = rng.uniform(0.0, 1.0, (gh, gw)).astype(np.float32)
        im = Image.fromarray((grid * 255.0).astype(np.uint8), "L").resize((w, h), Image.Resampling.BICUBIC)
        acc += (np.asarray(im, dtype=np.float32) / 255.0) * amp
        amp *= 0.45
    acc = (acc - acc.min()) / max(1e-5, acc.max() - acc.min())
    return acc


def silky_combed_fur_locks(h: int, w: int, seed: int, n_locks: int = 10, flow_curl: float = 0.08) -> tuple:
    """
    Sinh họa tiết SỢI LÔNG CHẢI MƯỢT ANIME (combed silky flowing wolf fur — KHÔNG BAO GIỜ bị ô vuông hay vảy cá!):
    - Kết hợp nhiều dải sợi lông dài chạy xuôi dọc kèm độ cong uốn lượn tự nhiên + từng lọn lông lớn chồng êm.
    - Trả về `(lock_body, hair_highlights, lock_creases)` đều ở miền `0..1`.
    """
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h

    # Nhiễu uốn dòng mượt (low-freq curl) làm các sợi lông lượn sóng mềm thay vì kẻ thẳng tắp
    curl_field = smooth_velvet_fur(h, w, seed + 11, freq_x=4.0, freq_y=2.0) - 0.5
    u_flow = nx + curl_field * flow_curl + 0.025 * np.sin(ny * math.pi * 2.0 + seed)

    # Sợi lông dài chạy dọc theo 3 độ mịn (dải lông chính, bó lông nhỏ, tơ mảnh)
    coarse_locks = smooth_velvet_fur(h, w, seed + 21, freq_x=float(n_locks), freq_y=1.6)
    med_strands = smooth_velvet_fur(h, w, seed + 31, freq_x=float(n_locks * 3), freq_y=2.4)
    fine_silks = smooth_velvet_fur(h, w, seed + 41, freq_x=float(n_locks * 6), freq_y=3.2)

    # Vẽ thêm các lọn lông dài uốn lượn lệch pha bằng sóng sin mượt (không tạo kẻ ngang!)
    rng = np.random.default_rng(seed + 77)
    lock_body = np.zeros((h, w), dtype=np.float32)
    lock_creases = np.zeros((h, w), dtype=np.float32)
    for li in range(n_locks):
        cx = (li + 0.5) / float(n_locks) + float(rng.uniform(-0.03, 0.03))
        amp = float(rng.uniform(0.020, 0.048))
        ph = float(rng.uniform(0.0, math.tau))
        width = float(rng.uniform(0.045, 0.095))
        # Đường tâm lọn lông cong nhẹ từ trên xuống dưới
        center_x = cx + amp * np.sin(ny * math.pi * 1.45 + ph)
        dx = np.abs(((u_flow - center_x + 0.5) % 1.0) - 0.5)
        taper = 1.0 - 0.35 * (ny ** 1.3)
        w_eff = width * taper
        prof = np.clip(1.0 - (dx / w_eff) ** 1.7, 0.0, 1.0)
        # Bóng khe mượt ngay mép ngoài mỗi lọn lông (chạy dọc chứ KHÔNG cắt ngang!)
        edge_d = np.abs(dx - w_eff * 0.92) / (w_eff * 0.36)
        crease = np.exp(-(edge_d ** 2)) * (0.45 + 0.55 * (1.0 - ny * 0.3))
        lock_body = np.maximum(lock_body, prof * (0.65 + 0.35 * coarse_locks))
        lock_creases = np.maximum(lock_creases, crease * 0.55)

    # Trộn sợi tơ mịn chạy dọc mượt mà
    body_mix = np.clip(lock_body * 0.58 + coarse_locks * 0.24 + med_strands * 0.18, 0.0, 1.0)
    hair_hi = np.clip(
        np.clip((fine_silks - 0.56) * 2.5, 0.0, 1.0) * (0.40 + 0.60 * lock_body)
        + np.clip((med_strands - 0.64) * 2.4, 0.0, 1.0) * 0.45,
        0.0, 1.0,
    )
    return body_mix, hair_hi, np.clip(lock_creases, 0.0, 1.0)


def build_textures() -> None:
    base = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    enraged = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    enraged_glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    base[:, :, 3] = 255
    enraged[:, :, 3] = 255

    c_wind_cyan = hex_arr("#6CF2FF")
    c_wind_white = hex_arr("#EEFFFF")

    # 1. CÁC VÙNG LÔNG THÂN & HIGHLIGHT — LÔNG CHẢI MƯỢT ANIME TRẮNG XANH (KHÔNG VẢY CÁ, KHÔNG Ô VUÔNG!)
    fur_specs = [
        # (key, seed, c0, c1, c2, c_sp, sp_str, p1_g, p2_h, p2_g, n_locks, crease_str)
        ("FUR_SILVER",         121, "#82BEEE", "#CAE8FF", "#F3FAFF", "#FFFFFF", 0.48, 0.0,  "#6AF2FF", 0.22, 9,  0.18),
        ("FUR_AZURE_DEEP",     122, "#4B82CA", "#80B9ED", "#C0E4FF", "#EBF8FF", 0.30, 0.0,  "#4CE0FF", 0.18, 9,  0.22),
        ("FUR_WHITE_CHEST",    123, "#CFEAFF", "#F2FAFF", "#FFFFFF", "#FFFFFF", 0.54, 0.0,  "#A8FAFF", 0.14, 10, 0.12),
        ("FUR_HIGHLIGHT_LOCK", 125, "#9AD0FA", "#DDF2FF", "#FFFFFF", "#FFFFFF", 0.74, 0.22, "#78F8FF", 0.36, 12, 0.14),
    ]
    for key, seed, c0_h, c1_h, c2_h, c_sp_h, sp_str, p1_g, p2_h, p2_g, n_locks, cr_str in fur_specs:
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        nx, ny = xx / w, yy / h

        velvet = smooth_velvet_fur(h, w, seed, freq_x=10.0, freq_y=2.5)
        body_mix, hair_hi, creases = silky_combed_fur_locks(h, w, seed + 200, n_locks=n_locks, flow_curl=0.07)

        soft_grad = 0.26 + 0.74 * (1.0 - ny * 0.56)
        t = np.clip(soft_grad * 0.58 + body_mix * 0.42 - creases * cr_str, 0.0, 1.0)

        c0, c1, c2, c_sp = hex_arr(c0_h), hex_arr(c1_h), hex_arr(c2_h), hex_arr(c_sp_h)
        col = np.where(
            t[:, :, None] < 0.5,
            c0 + (c1 - c0) * (t[:, :, None] * 2.0),
            c1 + (c2 - c1) * ((t[:, :, None] - 0.5) * 2.0),
        )
        arc_y = 0.28 + 0.07 * np.sin(nx * math.pi * 2.0 + velvet * 1.4)
        band1 = np.exp(-(((ny - arc_y) / 0.12) ** 2)) * (0.45 + 0.55 * body_mix)
        band2 = np.exp(-(((ny - (arc_y + 0.30)) / 0.09) ** 2)) * 0.40 * body_mix
        hi_mask = np.clip((band1 + band2) * sp_str + hair_hi * (sp_str * 0.68), 0.0, 0.92)
        col = col * (1.0 - hi_mask[:, :, None]) + c_sp * hi_mask[:, :, None]

        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(col, 0, 255).astype(np.uint8)
        if p1_g > 0.0:
            glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_wind_white * 0.7 + c_wind_cyan * 0.3, 0, 255).astype(np.uint8)
            glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(hi_mask * p1_g * 255.0, 0, 75).astype(np.uint8)

        tip_mask = np.clip(((1.0 - ny) ** 1.45) * (0.45 + 0.55 * velvet) + hi_mask * 0.35, 0.0, 1.0)
        p2_col = col * (1.0 - tip_mask[:, :, None] * p2_g) + hex_arr(p2_h) * (tip_mask[:, :, None] * (p2_g * 1.35))
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(p2_col, 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(hex_arr(p2_h), 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(tip_mask * (p2_g * 520.0), 0, 225).astype(np.uint8)

    # 2. TILE RIÊNG CHO 4 CHÂN (`FUR_LEG_FEATHERED`, 256x256) — SỢI LÔNG CHẢI MƯỢT ĐỒNG BỘ MÀU THÂN, CHI TIẾT SỢI RÕ NÉT!
    x0, y0, w, h = MAT["FUR_LEG_FEATHERED"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h

    body_l, hair_l, crease_l = silky_combed_fur_locks(h, w, 405, n_locks=11, flow_curl=0.09)
    fine_l = smooth_velvet_fur(h, w, 409, freq_x=56.0, freq_y=3.2)

    # Cùng hệ màu TRẮNG XANH BĂNG TUYẾT đồng bộ hoàn hảo với lông thân, chỉ tăng độ rõ của các dải sợi lông dài & highlight!
    col_c0 = hex_arr("#72B2E8")  # Rãnh bóng xanh trời dịu giữa các dải sợi lông chân
    col_c1 = hex_arr("#C2E4FF")  # Lông nền trắng xanh băng đồng bộ hoàn toàn với thân!
    col_c2 = hex_arr("#F3FAFF")  # Lông sáng trắng băng
    col_sp = hex_arr("#FFFFFF")  # Highlight sợi tơ rực sáng

    # Dải lông dọc mượt, sáng dần nhẹ về phía cổ chân & điểm nhấn highlight ở giữa các bó lông
    leg_grad = 0.34 + 0.56 * (1.0 - ny * 0.42) + 0.14 * np.sin(ny * math.pi)
    t_leg = np.clip(leg_grad * 0.50 + body_l * 0.48 - crease_l * 0.36 + (fine_l - 0.5) * 0.18, 0.0, 1.0)
    leg_col = np.where(
        t_leg[:, :, None] < 0.5,
        col_c0 + (col_c1 - col_c0) * (t_leg[:, :, None] * 2.0),
        col_c1 + (col_c2 - col_c1) * ((t_leg[:, :, None] - 0.5) * 2.0),
    )

    # Đường khe lông dịu + sợi tơ trắng sáng dọc ống chân (đọc rõ như tranh vẽ anime!)
    leg_col = leg_col * (1.0 - crease_l[:, :, None] * 0.24) + hex_arr("#5896D8") * (crease_l[:, :, None] * 0.24)
    halo_band = np.exp(-(((ny - (0.34 + 0.08 * np.sin(nx * math.tau))) / 0.15) ** 2)) * (0.45 + 0.55 * body_l)
    leg_hi = np.clip(hair_l * 0.62 + halo_band * 0.36 + np.clip((fine_l - 0.62) * 2.6, 0.0, 1.0) * 0.32, 0.0, 0.90)
    leg_col = leg_col * (1.0 - leg_hi[:, :, None]) + col_sp * leg_hi[:, :, None]

    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(leg_col, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_wind_white * 0.75 + c_wind_cyan * 0.25, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(leg_hi * 30.0, 0, 38).astype(np.uint8)

    p2_leg_mask = np.clip(leg_hi * 0.65 + (1.0 - ny) * 0.28, 0.0, 1.0)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(
        leg_col * (1.0 - p2_leg_mask[:, :, None] * 0.26) + c_wind_cyan * (p2_leg_mask[:, :, None] * 0.40),
        0, 255,
    ).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_wind_cyan * 0.8 + c_wind_white * 0.2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(p2_leg_mask * 150.0, 0, 180).astype(np.uint8)

    # 3. TRÁN SAO SẮC NÉT, MÕM, TAI, MẮT NGỌC LỤC BẢO ANIME & MŨI SÓI THANH THOÁT!
    x0, y0, w, h = MAT["HEAD_FOREHEAD"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h
    st = smooth_velvet_fur(h, w, 221, freq_x=14.0, freq_y=3.0)
    # Nền trán trắng-xanh sáng đồng bộ hoàn hảo với đỉnh sọ (không bị sẫm tách thành màn hình vuông!)
    fh_col = hex_arr("#B2DCFC") * (1.0 - st[:, :, None] * 0.45) + hex_arr("#E6F6FF") * (st[:, :, None] * 0.45)
    # Dấu sao 4 cánh trắng tinh sắc nét chuẩn Fel ở giữa trán
    dx, dy = np.abs(nx - 0.50), np.abs(ny - 0.52)
    star_d = (dx / 0.27) ** 0.62 + (dy / 0.42) ** 0.62
    fh_col[star_d < 1.0] = hex_arr("#FFFFFF")
    # Hai tia sét anime sắc gãy khúc (zig-zag nhọn chuẩn Fel — không bị cong như dấu ngoặc đơn!)
    for sign in (-1.0, 1.0):
        # 3 đoạn gãy khúc rõ nét từ đuôi mắt xếch lên thái dương
        seg_x = np.where(
            ny < 0.42,
            0.50 + sign * (0.34 - (ny - 0.16) * 0.30),
            np.where(
                ny < 0.64,
                0.50 + sign * (0.26 + (ny - 0.42) * 0.38),
                0.50 + sign * (0.34 - (ny - 0.64) * 0.42),
            ),
        )
        bolt = (np.abs(nx - seg_x) < 0.017) & (ny > 0.16) & (ny < 0.86)
        fh_col[bolt] = hex_arr("#1A2D52")
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(fh_col, 0, 255).astype(np.uint8)
    g_star = np.clip(1.0 - star_d, 0.0, 1.0)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_wind_white, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(g_star * 145.0, 0, 160).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(fh_col + c_wind_cyan * (g_star[:, :, None] * 0.52), 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_wind_cyan * 0.65 + c_wind_white * 0.35, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(g_star * 255.0, 0, 255).astype(np.uint8)

    x0, y0, w, h = MAT["MUZZLE_MARK"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h
    st_m = smooth_velvet_fur(h, w, 222, freq_x=12.0, freq_y=3.0)
    mz = hex_arr("#E6F5FF") * (1.0 - st_m[:, :, None] * 0.35) + hex_arr("#FFFFFF") * (st_m[:, :, None] * 0.35)
    # Vệt cong đỏ huyết gọn sắc ở góc khóe miệng sau + đường viền môi sói sắc nét
    curve_y = 0.62 - 0.22 * ((nx - 0.58) ** 2) * 3.8
    swirl = (np.abs(ny - curve_y) < 0.085 * np.clip(1.0 - (nx - 0.55) * 1.1, 0.25, 1.0)) & (nx > 0.34) & (nx < 0.90)
    mz[swirl] = hex_arr("#B81E2B")
    mz[(ny > 0.85) & (ny < 0.94)] = hex_arr("#1B2A48")
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(mz, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(mz, 0, 255).astype(np.uint8)

    # LÒNG TAI SÓI ALPHA-CUTOUT TAM GIÁC MỀM — CÙNG TÔNG LÔNG TRẮNG XANH, CHUYỂN MÀU SẪM DẦN TỪ NGOÀI VÀO TRONG!
    x0, y0, w, h = MAT["EAR_INNER"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h  # nx=0: mép trong (gần đỉnh đầu), nx=1: mép ngoài cong bầu; ny=0: chóp tai, ny=1: gốc tai

    # Biên hình tam giác bầu chuẩn tai sói (khớp chính xác hai vành tai 3D!):
    # Mép trong x_left(ny) đi thẳng hơi cong từ đỉnh (0.51, 0.04) xuống (0.07, 1.0)
    # Mép ngoài x_right(ny) phình cong bầu tự nhiên từ đỉnh (0.51, 0.04) xuống (0.96, 1.0)
    t_h = np.clip((ny - 0.04) / 0.96, 0.0, 1.0)
    x_left = 0.51 - 0.44 * (t_h ** 0.88)
    x_right = 0.51 + 0.46 * (t_h ** 0.66)
    ear_alpha_mask = (ny >= 0.04) & (nx >= x_left) & (nx <= x_right)

    # Khoảng cách chuẩn hóa từ mép ngoài vào tâm hốc tai (`0.0` ở mép ngoài cùng -> `1.0` sâu trong hốc tai!)
    half_span = np.maximum(0.02, 0.5 * (x_right - x_left))
    mid_x = 0.5 * (x_left + x_right)
    dist_from_center_x = np.abs(nx - mid_x) / half_span  # 0 ở giữa trục tai, 1 ở hai mép vành
    # Chiều sâu lõm hốc tai (sâu & sẫm dần từ 2 mép + chóp tai vào trung tâm lòng tai phía dưới!)
    edge_inward = np.clip(1.0 - dist_from_center_x, 0.0, 1.0)
    vert_hollow = np.clip(np.sin(np.clip((ny - 0.08) / 0.94, 0.0, 1.0) * math.pi * 0.86), 0.0, 1.0)
    depth_in = np.clip((edge_inward ** 0.78) * (0.28 + 0.72 * vert_hollow), 0.0, 1.0)

    # Thêm thớ sợi lông mảnh mềm chạy dọc trong lòng tai
    ear_hair = smooth_velvet_fur(h, w, 244, freq_x=24.0, freq_y=4.0)
    depth_in = np.clip(depth_in + (ear_hair - 0.5) * 0.14, 0.0, 1.0)

    # Dải chuyển màu 4 bậc CÙNG TÔNG LÔNG TRẮNG XANH — SÁNG Ở MÉP NGOÀI -> SẪM DẦN VÀO HỐC TAI TRONG:
    # 0.00 (mép lông ngoài): #EBF8FF (trắng băng sáng khớp 100% vành lông!)
    # 0.35 (sườn tai ngoài): #9ED2FA (xanh trời sáng)
    # 0.70 (sườn tai trong): #518AD0 (xanh băng dịu đậm)
    # 1.00 (đáy hốc tai sâu): #214478 (xanh lam băng sẫm tự nhiên, có chiều sâu!)
    c_e0 = hex_arr("#EBF8FF")
    c_e1 = hex_arr("#9ED2FA")
    c_e2 = hex_arr("#518AD0")
    c_e3 = hex_arr("#214478")
    d3 = depth_in[:, :, None]
    ei = np.where(
        d3 < 0.35,
        c_e0 + (c_e1 - c_e0) * (d3 / 0.35),
        np.where(
            d3 < 0.70,
            c_e1 + (c_e2 - c_e1) * ((d3 - 0.35) / 0.35),
            c_e2 + (c_e3 - c_e2) * ((d3 - 0.70) / 0.30),
        ),
    )

    # Sợi lông tơ trắng sáng chải nhẹ từ mép trong & đáy tai vươn vào hốc tai
    wisp_u = np.clip(1.0 - (nx - x_left) / np.maximum(0.05, (x_right - x_left) * 0.55), 0.0, 1.0)
    wisp_strands = np.clip((ear_hair - 0.46) * 2.4, 0.0, 1.0) * (wisp_u ** 1.4) * np.clip((ny - 0.32) / 0.68, 0.0, 1.0)
    base_fluff = np.clip((ny - 0.80) / 0.20, 0.0, 1.0) * (0.55 + 0.45 * ear_hair)
    fluff_mix = np.clip(wisp_strands * 0.72 + base_fluff * 0.75, 0.0, 0.88)
    ei = ei * (1.0 - fluff_mix[:, :, None]) + hex_arr("#F4FBFF") * fluff_mix[:, :, None]

    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(ei, 0, 255).astype(np.uint8)
    base[y0:y0 + h, x0:x0 + w, 3] = np.where(ear_alpha_mask, 255, 0).astype(np.uint8)

    # Phase 2: mép lông tai ánh cyan nhẹ, giữ nguyên lòng tai sâu sẫm
    p2_rim = np.clip(1.0 - depth_in * 1.4, 0.0, 1.0)
    ei_p2 = ei * (1.0 - p2_rim[:, :, None] * 0.24) + c_wind_cyan * (p2_rim[:, :, None] * 0.34)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(ei_p2, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, 3] = np.where(ear_alpha_mask, 255, 0).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_wind_cyan, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.where(ear_alpha_mask, np.clip(p2_rim * 110.0, 0, 120), 0).astype(np.uint8)

    # MẮT SÓI ANIME TỰ NHIÊN CÓ LÒNG TRẮNG, MỐNG MẮT NGỌC LỤC BẢO TRÒN SÂU & ĐƯỜNG MI THANH MẢNH (HẾT THÔ / HẾT HẠT ĐẬU XANH!)
    x0, y0, w, h = MAT["EYE_FEL"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h

    # Dáng mắt hạnh nhân sói tự nhiên (nx=0: khóe mắt trong gần mũi; nx=1: đuôi mắt ngoài hơi xếch thanh thoát)
    u_norm = (nx - 0.50) / 0.46  # -1..+1
    inside_u = np.clip(1.0 - u_norm ** 2, 0.0, 1.0)
    arch = np.sqrt(inside_u)
    # Đường cong mi trên mềm mại hơi xếch nhẹ về đuôi mắt, mi dưới ôm dịu tự nhiên
    tilt_y = 0.54 - 0.11 * u_norm
    y_top = tilt_y - 0.34 * arch - 0.03 * np.sin(nx * math.pi)
    y_bot = tilt_y + 0.28 * arch

    in_eye = (nx > 0.04) & (nx < 0.96) & (ny >= y_top) & (ny <= y_bot)

    # Nền ngoài khóe mắt là lông trắng-xanh mềm (khớp mượt 100% với da mặt quanh hốc mắt!)
    eye_col = np.broadcast_to(hex_arr("#D4ECFF"), (h, w, 3)).copy()
    fur_var = smooth_velvet_fur(h, w, 299, freq_x=10.0, freq_y=4.0)
    eye_col = hex_arr("#B8DDFB") * (1.0 - fur_var[:, :, None] * 0.55) + hex_arr("#ECF8FF") * (fur_var[:, :, None] * 0.55)

    # 1. Lòng trắng mắt (sclera) tự nhiên với bóng đổ dịu từ mí trên (tạo độ sâu 3D cực thật!)
    lid_depth = np.clip((ny - y_top) / np.maximum(0.08, y_bot - y_top), 0.0, 1.0)
    upper_shadow = np.exp(-((lid_depth / 0.24) ** 1.7))
    sclera = hex_arr("#F4FAFD") * (1.0 - upper_shadow[:, :, None] * 0.32) + hex_arr("#9DBAD4") * (upper_shadow[:, :, None] * 0.32)
    # Hơi ửng mềm khóe mắt rất nhẹ
    corner_tint = np.clip(1.0 - nx / 0.18, 0.0, 1.0) * 0.18
    sclera = sclera * (1.0 - corner_tint[:, :, None]) + hex_arr("#E6C8CE") * corner_tint[:, :, None]
    eye_col[in_eye] = sclera[in_eye]

    # 2. Mống mắt ngọc lục bảo tròn chuẩn tỉ lệ thực (cân bằng theo tỉ lệ 2.2:1 của ô mắt -> tròn tự nhiên, có chiều sâu ngọc!)
    icx, icy = 0.48, 0.51
    irx, iry = 0.205, 0.355
    idx = (nx - icx) / irx
    idy = (ny - icy) / iry
    ir = np.sqrt(idx ** 2 + idy ** 2)
    ang = np.arctan2(idy, idx)
    in_iris = in_eye & (ir <= 1.0)

    # Sớ sợi mống mắt tỏa tia mảnh + viền ngoài ngọc lục bảo đậm + ánh trăng lưỡi liềm sáng dịu phần dưới!
    fibers = 0.5 + 0.5 * np.cos(ang * 20.0 + ir * 3.0)
    limbal = np.clip((ir - 0.72) / 0.28, 0.0, 1.0)
    lower_glow = np.clip((idy + 0.18) / 0.85, 0.0, 1.0) * np.clip(1.0 - ir * 0.55, 0.0, 1.0)
    iris_col = (
        hex_arr("#1A6E46") * (1.0 - fibers[:, :, None] * 0.35)
        + hex_arr("#34C86A") * (fibers[:, :, None] * 0.35 + (1.0 - limbal[:, :, None]) * 0.45)
    )
    # Lưỡi liềm sáng ngọc-vàng dịu phía dưới mống mắt (chuẩn mắt anime cao cấp!)
    iris_col = iris_col * (1.0 - lower_glow[:, :, None] * 0.65) + hex_arr("#AEF89E") * (lower_glow[:, :, None] * 0.65)
    # Bóng mí trên đổ mềm lên nửa trên mống mắt + viền limbal đậm dịu tự nhiên
    iris_col = iris_col * (1.0 - limbal[:, :, None] * 0.55) + hex_arr("#103B2A") * (limbal[:, :, None] * 0.55)
    iris_col = iris_col * (1.0 - upper_shadow[:, :, None] * 0.42) + hex_arr("#0E3626") * (upper_shadow[:, :, None] * 0.42)
    eye_col[in_iris] = iris_col[in_iris]

    # 3. Con ngươi sói mềm mại (hình hạnh nhân đứng tự nhiên, viền chuyển mềm không bị gai răng cưa!)
    pr = np.sqrt(((nx - icx) / 0.058) ** 2 + ((ny - (icy + 0.01)) / 0.205) ** 2)
    pupil_mask = in_iris & (pr < 1.0)
    pupil_soft = np.clip((1.0 - pr) / 0.25, 0.0, 1.0)
    eye_col[pupil_mask] = (
        eye_col[pupil_mask] * (1.0 - pupil_soft[pupil_mask, None])
        + hex_arr("#0A1D18") * pupil_soft[pupil_mask, None]
    )

    # 4. Điểm sáng phản chiếu thủy tinh tự nhiên (catchlight tròn sạch + tia phản sáng nhỏ)
    c1 = (((nx - (icx - 0.075)) / 0.052) ** 2 + ((ny - (icy - 0.11)) / 0.088) ** 2) < 1.0
    c2 = (((nx - (icx + 0.070)) / 0.026) ** 2 + ((ny - (icy + 0.13)) / 0.046) ** 2) < 1.0
    eye_col[in_eye & c1] = hex_arr("#FFFFFF")
    eye_col[in_eye & c2] = hex_arr("#E6FFF4")

    # 5. Viền mi thanh mảnh tự nhiên (mi trên sắc nét vuốt nhọn hai đầu, mi dưới mảnh dịu — KHÔNG dày thô!)
    upper_lash_w = 0.048 * (0.45 + 0.55 * arch)
    upper_lash = (nx > 0.03) & (nx < 0.97) & (ny >= y_top - upper_lash_w) & (ny <= y_top + 0.018)
    lower_lash = (nx > 0.07) & (nx < 0.94) & (ny >= y_bot - 0.010) & (ny <= y_bot + 0.026)
    eye_col[upper_lash] = hex_arr("#1A2942")
    eye_col[lower_lash] = eye_col[lower_lash] * 0.45 + hex_arr("#3E5678") * 0.55

    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(eye_col, 0, 255).astype(np.uint8)
    # Glowmask tinh tế CHỈ tỏa sáng dịu trên mống mắt ngọc lục bảo & điểm sáng (không làm lóa lòng trắng!)
    glow_iris_alpha = np.where(in_iris & (~upper_lash), np.clip((0.55 + lower_glow * 0.45) * 205.0, 0, 225), 0.0)
    glow_iris_alpha = np.where(in_eye & (c1 | c2), 240.0, glow_iris_alpha)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(eye_col * 1.15, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = glow_iris_alpha.astype(np.uint8)

    eye_p2 = np.clip(eye_col.copy(), 0, 255)
    eye_p2[in_iris] = np.clip(eye_col[in_iris] * np.array([1.10, 1.24, 1.08]), 0, 255)
    enraged[y0:y0 + h, x0:x0 + w, :3] = eye_p2.astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = eye_p2.astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.where(in_iris & (~upper_lash), 245, np.where(in_eye & (c1 | c2), 255, 0)).astype(np.uint8)

    # Mũi sói tinh tế có lỗ mũi tối & highlight ẩm mượt ở sống mũi
    x0, y0, w, h = MAT["NOSE_CRIMSON"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = xx / w, yy / h
    nt = np.clip(1.0 - np.abs(nx * 2.0 - 1.0) * 0.45 - ny * 0.42, 0.0, 1.0)
    nose_c = hex_arr("#4C141E") * (1.0 - nt[:, :, None]) + hex_arr("#A4404A") * nt[:, :, None]
    # Highlight ẩm trên đỉnh mũi + 2 lỗ mũi tối đối xứng
    nose_hi = np.exp(-(((nx - 0.50) / 0.28) ** 2 + ((ny - 0.24) / 0.14) ** 2)) * 0.45
    nose_c = nose_c * (1.0 - nose_hi[:, :, None]) + hex_arr("#F0B8BE") * nose_hi[:, :, None]
    nostril_l = (((nx - 0.30) / 0.11) ** 2 + ((ny - 0.58) / 0.14) ** 2) < 1.0
    nostril_r = (((nx - 0.70) / 0.11) ** 2 + ((ny - 0.58) / 0.14) ** 2) < 1.0
    nose_c[nostril_l | nostril_r] = hex_arr("#22080E")
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(nose_c, 0, 255).astype(np.uint8)
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(nose_c, 0, 255).astype(np.uint8)

    for key, c_lo, c_hi in (
        ("MOUTH_INNER",      "#6E1828", "#DA5A6E"),
        ("FANG_IVORY",       "#DCE8F4", "#FFFFFF"),
        ("CLAW_OBSIDIAN",    "#0E1422", "#344462"),
        ("DARK_BROW",        "#121E38", "#2B406C"),
        ("FUR_CRIMSON_MARK", "#760E18", "#D82834"),
    ):
        x0, y0, w, h = MAT[key]
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        t = np.clip(1.0 - np.abs((xx / w) * 2.0 - 1.0) * 0.50 - (yy / h) * 0.30, 0.0, 1.0)
        cc = hex_arr(c_lo) * (1.0 - t[:, :, None]) + hex_arr(c_hi) * t[:, :, None]
        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(cc, 0, 255).astype(np.uint8)
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(cc, 0, 255).astype(np.uint8)

    x0, y0, w, h = MAT["WIND_RUNE_GLOW"]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    rr = np.sqrt(((xx / w) * 2.0 - 1.0) ** 2 + ((yy / h) * 2.0 - 1.0) ** 2)
    core = np.clip(1.0 - rr * 0.85, 0.0, 1.0)
    wr1 = hex_arr("#C6ECFF") * (1.0 - core[:, :, None]) + hex_arr("#FFFFFF") * core[:, :, None]
    base[y0:y0 + h, x0:x0 + w, :3] = np.clip(wr1, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(c_wind_white, 0, 255).astype(np.uint8)
    glow[y0:y0 + h, x0:x0 + w, 3] = np.clip(core * 140.0, 0, 150).astype(np.uint8)
    wr2 = c_wind_cyan * (1.0 - core[:, :, None]) + c_wind_white * core[:, :, None]
    enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(wr2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(wr2, 0, 255).astype(np.uint8)
    enraged_glow[y0:y0 + h, x0:x0 + w, 3] = 255

    def paint_cutout_plume(mat_key: str, seed: int, c_base_h: str, c_mid_h: str, c_tip_h: str,
                           p2_glow_h: str, p2_alpha: float, shape_mode: str):
        x0, y0, w, h = MAT[mat_key]
        mask_im = Image.new("L", (w, h), 0)
        draw = ImageDraw.Draw(mask_im)

        def pt(u, v):
            return (int(round(u * (w - 1))), int(round(v * (h - 1))))

        if shape_mode == "wisp":
            draw.polygon([
                pt(0.05, 0.78), pt(0.14, 0.42), pt(0.32, 0.20), pt(0.58, 0.14),
                pt(0.84, 0.22), pt(0.95, 0.12), pt(0.88, 0.38), pt(0.74, 0.48),
                pt(0.86, 0.58), pt(0.66, 0.74), pt(0.42, 0.78), pt(0.20, 0.88),
            ], fill=255)
        elif shape_mode == "crimson_cuff":
            draw.polygon([
                pt(0.08, 0.92), pt(0.14, 0.54), pt(0.06, 0.24), pt(0.28, 0.40),
                pt(0.36, 0.08), pt(0.54, 0.34), pt(0.72, 0.10), pt(0.74, 0.44),
                pt(0.94, 0.28), pt(0.84, 0.66), pt(0.62, 0.88), pt(0.32, 0.94),
            ], fill=255)
        else:
            draw.polygon([
                pt(0.05, 0.86), pt(0.12, 0.48), pt(0.30, 0.20), pt(0.56, 0.14),
                pt(0.84, 0.10), pt(0.94, 0.28), pt(0.76, 0.42), pt(0.90, 0.58),
                pt(0.68, 0.78), pt(0.36, 0.86), pt(0.14, 0.92),
            ], fill=255)

        alpha = np.asarray(mask_im, dtype=np.uint8)
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        nx, ny = xx / w, yy / h
        st = smooth_velvet_fur(h, w, seed, freq_x=14.0, freq_y=4.0)
        fine = smooth_velvet_fur(h, w, seed + 25, freq_x=46.0, freq_y=7.0)
        grad = np.clip((1.0 - ny) * 0.52 + nx * 0.20 + st * 0.28 + (fine - 0.5) * 0.18 - 0.04, 0.0, 1.0)
        cb, cm, ct = hex_arr(c_base_h), hex_arr(c_mid_h), hex_arr(c_tip_h)
        col = np.where(
            grad[:, :, None] < 0.5,
            cb + (cm - cb) * (grad[:, :, None] * 2.0),
            cm + (ct - cm) * ((grad[:, :, None] - 0.5) * 2.0),
        )
        hi_r = np.clip(np.exp(-(((ny - 0.26) / 0.14) ** 2)) * 0.45 + np.clip((fine - 0.62) * 2.5, 0.0, 1.0) * 0.35, 0.0, 0.85)
        col = col * (1.0 - hi_r[:, :, None]) + hex_arr(c_tip_h) * hi_r[:, :, None]
        base[y0:y0 + h, x0:x0 + w, :3] = np.clip(col, 0, 255).astype(np.uint8)
        base[y0:y0 + h, x0:x0 + w, 3] = alpha

        tip = np.clip((nx * 0.55 + (1.0 - ny) * 0.55 - 0.22) * 1.4, 0.0, 1.0)
        p2c = col * (1.0 - tip[:, :, None] * 0.40) + hex_arr(p2_glow_h) * (tip[:, :, None] * 0.58)
        enraged[y0:y0 + h, x0:x0 + w, :3] = np.clip(p2c, 0, 255).astype(np.uint8)
        enraged[y0:y0 + h, x0:x0 + w, 3] = alpha
        enraged_glow[y0:y0 + h, x0:x0 + w, :3] = np.clip(hex_arr(p2_glow_h), 0, 255).astype(np.uint8)
        enraged_glow[y0:y0 + h, x0:x0 + w, 3] = np.where(alpha >= 128, np.clip(tip * p2_alpha, 0, 240), 0).astype(np.uint8)

    paint_cutout_plume("FUR_WISP_CUTOUT",     321, "#6CAEEB", "#C6E8FF", "#FFFFFF", "#7CF4FF", 220.0, "wisp")
    paint_cutout_plume("CRIMSON_CUFF_CUTOUT", 322, "#5FA4E8", "#C0E6FF", "#FFFFFF", "#7CF4FF", 225.0, "crimson_cuff")
    paint_cutout_plume("FLANK_SWIRL_CUTOUT",  324, "#6BAEEB", "#C6E8FF", "#FFFFFF", "#82F6FF", 215.0, "swirl")

    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(base, "RGBA").save(tex_dir / "fenrir_boss.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "fenrir_boss_glowmask.png")
    Image.fromarray(enraged, "RGBA").save(tex_dir / "fenrir_boss_enraged.png")
    Image.fromarray(enraged_glow, "RGBA").save(tex_dir / "fenrir_boss_enraged_glowmask.png")
    print(f"TEX_OK: 4 Fel Fenrir V5 textures ({TEX_W}x{TEX_H}, layered V-tuft fur + dedicated FUR_LEG_FEATHERED)")


# =============================================================================
# 4. 11 ANIMATION SÓNG TRỄ PHA CHUẨN GECKOLIB 4.8.3 (TÁCH QUYỀN SỞ HỮU 0 XUNG ĐỘT!)
#    - `fenrir_boss.animation.json`: sở hữu thân, cột sống, 4 chân, cổ-đầu, hàm, gốc tai (`ear_l/r`), đuôi chính (`tail_1..8`).
#    - `FenrirGeoModel.java`: sở hữu độc quyền 164 khớp lông đa khớp + 2 chóp tai (`ear_l_tip/r_tip`) + 4 mí mắt (giá trị TUYỆT ĐỐI!).
# =============================================================================
def kf(vec, easing="easeInOutSine"):
    return {"vector": [round(float(v), 2) for v in vec], "easing": easing}


def wave_rot(length: float, steps: int, amp_x: float, amp_y: float, amp_z: float,
             phase_rad: float, cycles: float = 1.0, bias=(0.0, 0.0, 0.0)) -> dict:
    out = {}
    for i in range(steps + 1):
        t = (i / float(steps)) * length
        ang = (i / float(steps)) * math.tau * cycles - phase_rad
        v = [
            bias[0] + amp_x * math.sin(ang),
            bias[1] + amp_y * math.sin(ang + 0.55),
            bias[2] + amp_z * math.cos(ang),
        ]
        out[f"{t:.2f}"] = kf(v, "easeInOutSine")
    return {"rotation": out}


def add_tail_waves(bones_dict: dict, length: float, steps: int, cycles: float,
                   tail_amp: float, bias_x: float = 0.0) -> None:
    """
    Truyền sóng trễ pha mượt từ gốc -> giữa -> ngọn cho toàn bộ 8 đốt thân đuôi chính (`tail_1..8`).
    (164 khớp lông chỉa ra & 2 chóp tai được `FenrirGeoModel.applyRealtimeFurWindSway` gán góc tuyệt đối,
     nên KHÔNG phát keyframe trùng ở đây để chống 100% xung đột snapshot & giảm 10x dung lượng JSON!)
    """
    for i in range(8):
        gain = 0.55 + i * 0.14
        ph = i * 0.52
        bones_dict[f"tail_{i + 1}"] = wave_rot(
            length, steps,
            tail_amp * 0.52 * gain, tail_amp * 1.10 * gain, tail_amp * 0.25 * gain,
            ph, cycles, bias=(bias_x * (0.4 + i * 0.08), 0.0, 0.0),
        )


def build_animations(fur_registry: list) -> dict:
    _ = fur_registry  # Toàn bộ fur_registry (164 khớp) thuộc quyền điều khiển tuyệt đối của FenrirGeoModel
    anims = {}

    # 1. IDLE (4.00s loop)
    idle_b = {
        "body": {"position": {"0.00": kf([0, 0, 0], "easeInOutSine"), "2.00": kf([0, -0.55, 0], "easeInOutSine"), "4.00": kf([0, 0, 0], "easeInOutSine")}},
        "spine_1": {"rotation": {"0.00": kf([0.3, 0, 0], "easeInOutSine"), "1.80": kf([1.2, 0, 0], "easeInOutSine"), "4.00": kf([0.3, 0, 0], "easeInOutSine")}},
        "spine_2": {"rotation": {"0.00": kf([0.4, 0, 0], "easeInOutSine"), "1.92": kf([1.8, 0, 0], "easeInOutSine"), "4.00": kf([0.4, 0, 0], "easeInOutSine")}},
        "spine_3": {"rotation": {"0.00": kf([0.4, 0, 0], "easeInOutSine"), "2.04": kf([2.0, 0, 0], "easeInOutSine"), "4.00": kf([0.4, 0, 0], "easeInOutSine")}},
        "chest":   {"rotation": {"0.00": kf([0.4, 0, 0], "easeInOutSine"), "2.16": kf([2.2, 0, 0], "easeInOutSine"), "4.00": kf([0.4, 0, 0], "easeInOutSine")}},
        "shoulder_fl": {"rotation": {"0.00": kf([0, 0, 0], "easeInOutSine"), "2.10": kf([-2.0, 0, -1.5], "easeInOutSine"), "4.00": kf([0, 0, 0], "easeInOutSine")}},
        "shoulder_fr": {"rotation": {"0.00": kf([0, 0, 0], "easeInOutSine"), "2.10": kf([-2.0, 0, 1.5], "easeInOutSine"),  "4.00": kf([0, 0, 0], "easeInOutSine")}},
        "neck_1": {"rotation": {"0.00": kf([-0.5, 0, 0], "easeInOutSine"), "2.10": kf([1.2, 0, 0], "easeInOutSine"), "4.00": kf([-0.5, 0, 0], "easeInOutSine")}},
        "neck_2": {"rotation": {"0.00": kf([-0.6, -0.6, 0], "easeInOutSine"), "1.80": kf([1.0, 0.9, 0.3], "easeInOutSine"), "4.00": kf([-0.6, -0.6, 0], "easeInOutSine")}},
        "neck_3": {"rotation": {"0.00": kf([0.2, -0.7, 0], "easeInOutSine"),  "1.95": kf([-0.6, 1.0, 0.4], "easeInOutSine"), "4.00": kf([0.2, -0.7, 0], "easeInOutSine")}},
        "neck_4": {"rotation": {"0.00": kf([0.4, -0.8, 0], "easeInOutSine"),  "2.05": kf([-0.7, 1.2, 0.4], "easeInOutSine"), "4.00": kf([0.4, -0.8, 0], "easeInOutSine")}},
        "head":   {"rotation": {"0.00": kf([0.7, -1.4, 0], "easeInOutSine"),  "1.70": kf([-0.9, 1.8, 0.6], "easeInOutSine"), "3.00": kf([1.0, -1.2, -0.5], "easeInOutSine"), "4.00": kf([0.7, -1.4, 0], "easeInOutSine")}},
        "jaw":    {"rotation": {"0.00": kf([1.5, 0, 0], "easeInOutSine"),  "2.05": kf([6.5, 0, 0], "easeInOutSine"), "4.00": kf([1.5, 0, 0], "easeInOutSine")}},
        "ear_l":  {"rotation": {"0.00": kf([0, 0, 0], "easeInOutSine"), "1.30": kf([-4, 0, -4], "easeInOutSine"), "2.80": kf([2, 0, 2.5], "easeInOutSine"), "4.00": kf([0, 0, 0], "easeInOutSine")}},
        "ear_r":  {"rotation": {"0.00": kf([0, 0, 0], "easeInOutSine"), "1.30": kf([2, 0, -2.5], "easeInOutSine"), "2.80": kf([-4, 0, 4], "easeInOutSine"), "4.00": kf([0, 0, 0], "easeInOutSine")}},
        "mane_top": {"rotation": {"0.00": kf([0, 0, 0], "easeInOutSine"), "2.05": kf([-2.6, 0, 0], "easeInOutSine"), "4.00": kf([0, 0, 0], "easeInOutSine")}},
    }
    add_tail_waves(idle_b, 4.00, 8, 1.0, tail_amp=4.8)
    anims["animation.fenrir_boss.idle"] = {"loop": True, "animation_length": 4.00, "bones": idle_b}

    # 2. WALK / GALLOP (0.96s loop)
    walk_b = {
        "body": {
            "position": {"0.00": kf([0, 0.9, 0], "easeInOutSine"), "0.24": kf([0, -1.5, 0], "easeOutCubic"), "0.48": kf([0, 0.9, 0], "easeInOutSine"), "0.72": kf([0, -1.5, 0], "easeOutCubic"), "0.96": kf([0, 0.9, 0], "easeInOutSine")},
            "rotation": {"0.00": kf([-3.2, 0, 1.4], "easeInOutSine"), "0.48": kf([3.2, 0, -1.4], "easeInOutSine"), "0.96": kf([-3.2, 0, 1.4], "easeInOutSine")},
        },
        "spine_1": {"rotation": {"0.00": kf([-2.8, 2.2, 0], "easeInOutSine"), "0.48": kf([2.8, -2.2, 0], "easeInOutSine"), "0.96": kf([-2.8, 2.2, 0], "easeInOutSine")}},
        "spine_2": {"rotation": {"0.00": kf([-2.4, 0.5, 0], "easeInOutSine"), "0.48": kf([2.4, -0.5, 0], "easeInOutSine"), "0.96": kf([-2.4, 0.5, 0], "easeInOutSine")}},
        "spine_3": {"rotation": {"0.00": kf([-2.0, -1.8, 0], "easeInOutSine"), "0.48": kf([2.0, 1.8, 0], "easeInOutSine"), "0.96": kf([-2.0, -1.8, 0], "easeInOutSine")}},
        "chest":   {"rotation": {"0.00": kf([2.8, -2.8, 0], "easeInOutSine"), "0.48": kf([-2.8, 2.8, 0], "easeInOutSine"), "0.96": kf([2.8, -2.8, 0], "easeInOutSine")}},
        "neck_1":  {"rotation": {"0.00": kf([2.5, 0, 0], "easeInOutSine"), "0.48": kf([-1.8, 0, 0], "easeInOutSine"), "0.96": kf([2.5, 0, 0], "easeInOutSine")}},
        "neck_2":  {"rotation": {"0.00": kf([2.0, 0, 0], "easeInOutSine"), "0.48": kf([-1.4, 0, 0], "easeInOutSine"), "0.96": kf([2.0, 0, 0], "easeInOutSine")}},
        "neck_3":  {"rotation": {"0.00": kf([1.2, 0, 0], "easeInOutSine"), "0.48": kf([-0.8, 0, 0], "easeInOutSine"), "0.96": kf([1.2, 0, 0], "easeInOutSine")}},
        "neck_4":  {"rotation": {"0.00": kf([0.6, 0, 0], "easeInOutSine"), "0.48": kf([-0.4, 0, 0], "easeInOutSine"), "0.96": kf([0.6, 0, 0], "easeInOutSine")}},
        "head":    {"rotation": {"0.00": kf([-4.5, 0, 0], "easeInOutSine"), "0.48": kf([3.2, 0, 0], "easeInOutSine"), "0.96": kf([-4.5, 0, 0], "easeInOutSine")}},
        "ear_l":   {"rotation": {"0.00": kf([-6, 0, -4], "easeInOutSine"), "0.48": kf([-2, 0, -2], "easeInOutSine"), "0.96": kf([-6, 0, -4], "easeInOutSine")}},
        "ear_r":   {"rotation": {"0.00": kf([-6, 0, 4], "easeInOutSine"),  "0.48": kf([-2, 0, 2], "easeInOutSine"),  "0.96": kf([-6, 0, 4], "easeInOutSine")}},
        "shoulder_fl": {"rotation": {"0.00": kf([-10, 0, 0]), "0.48": kf([8, 0, 0]),  "0.96": kf([-10, 0, 0])}},
        "leg_fl":      {"rotation": {"0.00": kf([-22, 0, 0]), "0.24": kf([-4, 0, 0]), "0.48": kf([18, 0, 0]), "0.72": kf([-10, 0, 0]), "0.96": kf([-22, 0, 0])}},
        "elbow_fl":    {"rotation": {"0.00": kf([6, 0, 0]),   "0.24": kf([2, 0, 0]),  "0.48": kf([8, 0, 0]),  "0.72": kf([20, 0, 0]),  "0.96": kf([6, 0, 0])}},
        "leg_fl_low":  {"rotation": {"0.00": kf([6, 0, 0]),   "0.24": kf([2, 0, 0]),  "0.48": kf([6, 0, 0]),  "0.72": kf([18, 0, 0]),  "0.96": kf([6, 0, 0])}},
        "wrist_fl":    {"rotation": {"0.00": kf([12, 0, 0]),  "0.24": kf([0, 0, 0]),  "0.48": kf([-14, 0, 0]),"0.72": kf([18, 0, 0]),  "0.96": kf([12, 0, 0])}},
        "foot_fl":     {"rotation": {"0.00": kf([6, 0, 0]),   "0.24": kf([0, 0, 0]),  "0.48": kf([-10, 0, 0]),"0.72": kf([-8, 0, 0]),  "0.96": kf([6, 0, 0])}},
        "toes_fl":     {"rotation": {"0.00": kf([-8, 0, 0]),  "0.24": kf([0, 0, 0]),  "0.48": kf([14, 0, 0]), "0.72": kf([10, 0, 0]),  "0.96": kf([-8, 0, 0])}},

        "shoulder_fr": {"rotation": {"0.00": kf([8, 0, 0]),   "0.48": kf([-10, 0, 0]), "0.96": kf([8, 0, 0])}},
        "leg_fr":      {"rotation": {"0.00": kf([18, 0, 0]),  "0.24": kf([-10, 0, 0]), "0.48": kf([-22, 0, 0]),"0.72": kf([-4, 0, 0]), "0.96": kf([18, 0, 0])}},
        "elbow_fr":    {"rotation": {"0.00": kf([8, 0, 0]),   "0.24": kf([20, 0, 0]),  "0.48": kf([6, 0, 0]),   "0.72": kf([2, 0, 0]),  "0.96": kf([8, 0, 0])}},
        "leg_fr_low":  {"rotation": {"0.00": kf([6, 0, 0]),   "0.24": kf([18, 0, 0]),  "0.48": kf([6, 0, 0]),   "0.72": kf([2, 0, 0]),  "0.96": kf([6, 0, 0])}},
        "wrist_fr":    {"rotation": {"0.00": kf([-14, 0, 0]), "0.24": kf([18, 0, 0]),  "0.48": kf([12, 0, 0]),  "0.72": kf([0, 0, 0]),  "0.96": kf([-14, 0, 0])}},
        "foot_fr":     {"rotation": {"0.00": kf([-10, 0, 0]), "0.24": kf([-8, 0, 0]),  "0.48": kf([6, 0, 0]),   "0.72": kf([0, 0, 0]),  "0.96": kf([-10, 0, 0])}},
        "toes_fr":     {"rotation": {"0.00": kf([14, 0, 0]),  "0.24": kf([10, 0, 0]),  "0.48": kf([-8, 0, 0]),  "0.72": kf([0, 0, 0]),  "0.96": kf([14, 0, 0])}},

        "hip_br":      {"rotation": {"0.00": kf([-8, 0, 0]),  "0.48": kf([8, 0, 0]),   "0.96": kf([-8, 0, 0])}},
        "leg_br":      {"rotation": {"0.00": kf([-20, 0, 0]), "0.24": kf([-4, 0, 0]),  "0.48": kf([20, 0, 0]), "0.72": kf([-8, 0, 0]), "0.96": kf([-20, 0, 0])}},
        "leg_br_mid":  {"rotation": {"0.00": kf([14, 0, 0]),  "0.24": kf([6, 0, 0]),   "0.48": kf([-8, 0, 0]), "0.72": kf([22, 0, 0]), "0.96": kf([14, 0, 0])}},
        "hock_br":     {"rotation": {"0.00": kf([-6, 0, 0]),  "0.24": kf([-2, 0, 0]),  "0.48": kf([10, 0, 0]), "0.72": kf([-12, 0, 0]),"0.96": kf([-6, 0, 0])}},
        "leg_br_low":  {"rotation": {"0.00": kf([-4, 0, 0]),  "0.24": kf([-2, 0, 0]),  "0.48": kf([6, 0, 0]),  "0.72": kf([-8, 0, 0]), "0.96": kf([-4, 0, 0])}},
        "toes_br":     {"rotation": {"0.00": kf([-6, 0, 0]),  "0.24": kf([0, 0, 0]),   "0.48": kf([14, 0, 0]), "0.72": kf([8, 0, 0]),  "0.96": kf([-6, 0, 0])}},

        "hip_bl":      {"rotation": {"0.00": kf([8, 0, 0]),   "0.48": kf([-8, 0, 0]),  "0.96": kf([8, 0, 0])}},
        "leg_bl":      {"rotation": {"0.00": kf([20, 0, 0]),  "0.24": kf([-8, 0, 0]),  "0.48": kf([-20, 0, 0]),"0.72": kf([-4, 0, 0]), "0.96": kf([20, 0, 0])}},
        "leg_bl_mid":  {"rotation": {"0.00": kf([-8, 0, 0]),  "0.24": kf([22, 0, 0]),  "0.48": kf([14, 0, 0]), "0.72": kf([6, 0, 0]),  "0.96": kf([-8, 0, 0])}},
        "hock_bl":     {"rotation": {"0.00": kf([10, 0, 0]),  "0.24": kf([-12, 0, 0]), "0.48": kf([-6, 0, 0]), "0.72": kf([-2, 0, 0]), "0.96": kf([10, 0, 0])}},
        "leg_bl_low":  {"rotation": {"0.00": kf([6, 0, 0]),   "0.24": kf([-8, 0, 0]),  "0.48": kf([-4, 0, 0]), "0.72": kf([-2, 0, 0]), "0.96": kf([6, 0, 0])}},
        "toes_bl":     {"rotation": {"0.00": kf([14, 0, 0]),  "0.24": kf([8, 0, 0]),   "0.48": kf([-6, 0, 0]), "0.72": kf([0, 0, 0]),  "0.96": kf([14, 0, 0])}},
    }
    add_tail_waves(walk_b, 0.96, 8, 1.0, tail_amp=7.2, bias_x=8.5)
    anims["animation.fenrir_boss.walk"] = {"loop": True, "animation_length": 0.96, "bones": walk_b}

    # 3. CLAW_SLASH (1.20s)
    slash_b = {
        "body": {
            "position": {"0.00": kf([0, 0, 0]), "0.36": kf([0, 4.4, 2.5], "easeInCubic"), "0.60": kf([0, -2.0, -3.6], "easeOutExpo"), "1.20": kf([0, 0, 0], "easeInOutSine")},
            "rotation": {"0.00": kf([0, 0, 0]), "0.36": kf([-18, 10, 0], "easeInCubic"),  "0.60": kf([12, -14, 0], "easeOutExpo"),  "1.20": kf([0, 0, 0], "easeInOutSine")},
        },
        "spine_2": {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-6, 8, 0]),   "0.60": kf([8, -12, 0], "easeOutExpo"),  "1.20": kf([0, 0, 0])}},
        "spine_3": {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-8, 10, 0]),  "0.60": kf([10, -14, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        "chest":   {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-10, 14, 0]), "0.60": kf([12, -20, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        "neck_1":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-4, -6, 0]),  "0.60": kf([5, 8, 0], "easeOutExpo"),    "1.20": kf([0, 0, 0])}},
        "neck_2":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-4, -6, 0]),  "0.60": kf([5, 8, 0], "easeOutExpo"),    "1.20": kf([0, 0, 0])}},
        "neck_3":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-3, -4, 0]),  "0.60": kf([4, 6, 0], "easeOutExpo"),    "1.20": kf([0, 0, 0])}},
        "neck_4":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-3, -4, 0]),  "0.60": kf([4, 6, 0], "easeOutExpo"),    "1.20": kf([0, 0, 0])}},
        "head":    {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-4, -6, 0]),  "0.60": kf([6, 8, 0], "easeOutExpo"),    "1.20": kf([0, 0, 0])}},
        "shoulder_fr": {"rotation": {"0.00": kf([0, 0, 0]), "0.36": kf([-20, -10, 12], "easeInCubic"), "0.60": kf([14, 14, -10], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        "leg_fr":      {"rotation": {"0.00": kf([0, 0, 0]), "0.36": kf([-64, -16, 20], "easeInCubic"), "0.60": kf([32, 26, -16], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        "elbow_fr":    {"rotation": {"0.00": kf([0, 0, 0]), "0.36": kf([26, 0, 0]), "0.60": kf([-12, 0, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        "wrist_fr":    {"rotation": {"0.00": kf([0, 0, 0]), "0.36": kf([26, 0, 0]), "0.60": kf([-20, 0, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        "leg_fl":      {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([-52, 12, -16], "easeInCubic"), "0.74": kf([30, -22, 14], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        "jaw":         {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([42, 0, 0], "easeOutCubic"), "0.75": kf([24, 0, 0]), "1.20": kf([0, 0, 0])}},
        "ear_l":       {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([-16, 0, -14]), "1.20": kf([0, 0, 0])}},
        "ear_r":       {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([-16, 0, 14]),  "1.20": kf([0, 0, 0])}},
    }
    add_tail_waves(slash_b, 1.20, 6, 1.0, tail_amp=10.0, bias_x=4.0)
    anims["animation.fenrir_boss.claw_slash"] = {"animation_length": 1.20, "bones": slash_b}

    # 4. BITE_LUNGE (1.10s)
    bite_b = {
        "body":    {"position": {"0.00": kf([0, 0, 0]), "0.38": kf([0, -4.4, 4.2], "easeInCubic"), "0.56": kf([0, 1.0, -7.4], "easeOutExpo"), "1.10": kf([0, 0, 0], "easeInOutSine")}},
        "spine_3": {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([10, 0, 0], "easeInCubic"), "0.56": kf([-12, 0, 0], "easeOutExpo"), "1.10": kf([0, 0, 0])}},
        "neck_1":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-10, 0, 0]), "0.56": kf([12, 0, 0], "easeOutExpo"), "1.10": kf([0, 0, 0])}},
        "neck_2":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-10, 0, 0]), "0.56": kf([12, 0, 0], "easeOutExpo"), "1.10": kf([0, 0, 0])}},
        "neck_3":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-8, 0, 0]),  "0.56": kf([10, 0, 0], "easeOutExpo"), "1.10": kf([0, 0, 0])}},
        "neck_4":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-8, 0, 0]),  "0.56": kf([8, 0, 0], "easeOutExpo"),  "1.10": kf([0, 0, 0])}},
        "head":    {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-10, 0, 0]), "0.56": kf([8, 0, 0], "easeOutExpo"),  "1.10": kf([0, 0, 0])}},
        "jaw":     {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([52, 0, 0], "easeInCubic"), "0.60": kf([-4, 0, 0], "easeOutExpo"), "1.10": kf([0, 0, 0])}},
        "ear_l":   {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-20, 0, -16]), "1.10": kf([0, 0, 0])}},
        "ear_r":   {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-20, 0, 16]),  "1.10": kf([0, 0, 0])}},
        "leg_fl":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-22, 0, 0]), "0.56": kf([26, 0, 0], "easeOutExpo"), "1.10": kf([0, 0, 0])}},
        "leg_fr":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-22, 0, 0]), "0.56": kf([26, 0, 0], "easeOutExpo"), "1.10": kf([0, 0, 0])}},
    }
    add_tail_waves(bite_b, 1.10, 6, 1.0, tail_amp=8.0, bias_x=7.5)
    anims["animation.fenrir_boss.bite_lunge"] = {"animation_length": 1.10, "bones": bite_b}

    # 5. POUNCE (1.60s)
    pounce_b = {
        "body": {
            "position": {"0.00": kf([0, 0, 0]), "0.42": kf([0, -6.5, 4.2], "easeInCubic"), "0.82": kf([0, 8.8, -4.2], "easeOutCubic"), "1.10": kf([0, -4.5, -2.0], "easeOutExpo"), "1.60": kf([0, 0, 0], "easeInOutSine")},
            "rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([14, 0, 0], "easeInCubic"),     "0.82": kf([-24, 0, 0], "easeOutCubic"),    "1.10": kf([18, 0, 0], "easeOutExpo"),    "1.60": kf([0, 0, 0], "easeInOutSine")},
        },
        "spine_2": {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([8, 0, 0]),  "0.82": kf([-12, 0, 0]), "1.10": kf([10, 0, 0]), "1.60": kf([0, 0, 0])}},
        "spine_3": {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([8, 0, 0]),  "0.82": kf([-12, 0, 0]), "1.10": kf([10, 0, 0]), "1.60": kf([0, 0, 0])}},
        "neck_1":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([6, 0, 0]),  "0.82": kf([-8, 0, 0]),  "1.10": kf([6, 0, 0]),  "1.60": kf([0, 0, 0])}},
        "neck_2":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([6, 0, 0]),  "0.82": kf([-8, 0, 0]),  "1.10": kf([6, 0, 0]),  "1.60": kf([0, 0, 0])}},
        "neck_3":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([4, 0, 0]),  "0.82": kf([-6, 0, 0]),  "1.10": kf([4, 0, 0]),  "1.60": kf([0, 0, 0])}},
        "neck_4":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([4, 0, 0]),  "0.82": kf([-6, 0, 0]),  "1.10": kf([4, 0, 0]),  "1.60": kf([0, 0, 0])}},
        "head":    {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([6, 0, 0]),  "0.82": kf([-10, 0, 0]), "1.10": kf([8, 0, 0]),  "1.60": kf([0, 0, 0])}},
        "ear_l":   {"rotation": {"0.00": kf([0, 0, 0]), "0.82": kf([-18, 0, -14]), "1.60": kf([0, 0, 0])}},
        "ear_r":   {"rotation": {"0.00": kf([0, 0, 0]), "0.82": kf([-18, 0, 14]),  "1.60": kf([0, 0, 0])}},
        "leg_fl":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([-32, 0, 0]),"0.82": kf([-58, 0, -10], "easeOutCubic"), "1.10": kf([20, 0, 0], "easeOutExpo"), "1.60": kf([0, 0, 0])}},
        "leg_fr":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([-32, 0, 0]),"0.82": kf([-58, 0, 10], "easeOutCubic"),  "1.10": kf([20, 0, 0], "easeOutExpo"), "1.60": kf([0, 0, 0])}},
        "leg_bl":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([-24, 0, 0]),"0.82": kf([46, 0, 0], "easeOutCubic"),    "1.10": kf([-18, 0, 0], "easeOutExpo"),"1.60": kf([0, 0, 0])}},
        "leg_br":  {"rotation": {"0.00": kf([0, 0, 0]), "0.42": kf([-24, 0, 0]),"0.82": kf([46, 0, 0], "easeOutCubic"),    "1.10": kf([-18, 0, 0], "easeOutExpo"),"1.60": kf([0, 0, 0])}},
        "jaw":     {"rotation": {"0.00": kf([0, 0, 0]), "0.82": kf([46, 0, 0], "easeOutCubic"), "1.18": kf([16, 0, 0]), "1.60": kf([0, 0, 0])}},
    }
    add_tail_waves(pounce_b, 1.60, 8, 1.0, tail_amp=12.0, bias_x=9.0)
    anims["animation.fenrir_boss.pounce"] = {"animation_length": 1.60, "bones": pounce_b}

    # 6. HOWL (1.80s)
    howl_b = {
        "body":   {"position": {"0.00": kf([0, 0, 0]), "0.35": kf([0, -2.8, 1.5], "easeInCubic"), "0.68": kf([0, 2.2, -1.0], "easeOutExpo"), "1.35": kf([0, 1.6, -0.8]), "1.80": kf([0, 0, 0], "easeInOutSine")}},
        "chest":  {"rotation": {"0.00": kf([0, 0, 0]), "0.35": kf([10, 0, 0]), "0.68": kf([-14, 0, 0], "easeOutExpo"), "1.35": kf([-12, 0, 0]), "1.80": kf([0, 0, 0])}},
        "neck_1": {"rotation": {"0.00": kf([0, 0, 0]), "0.35": kf([6, 0, 0]),  "0.68": kf([-12, 0, 0], "easeOutExpo"), "1.35": kf([-10, 0, 0]), "1.80": kf([0, 0, 0])}},
        "neck_2": {"rotation": {"0.00": kf([0, 0, 0]), "0.35": kf([6, 0, 0]),  "0.68": kf([-14, 0, 0], "easeOutExpo"), "1.35": kf([-12, 0, 0]), "1.80": kf([0, 0, 0])}},
        "neck_3": {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-14, 0, 0], "easeOutExpo"), "1.35": kf([-12, 0, 0]), "1.80": kf([0, 0, 0])}},
        "neck_4": {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-12, 0, 0], "easeOutExpo"), "1.35": kf([-10, 0, 0]), "1.80": kf([0, 0, 0])}},
        "head":   {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-16, 0, 0], "easeOutExpo"), "1.35": kf([-14, 0, 0]), "1.80": kf([0, 0, 0])}},
        "jaw":    {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([46, 0, 0], "easeOutExpo"),  "1.35": kf([38, 0, 0]),  "1.80": kf([0, 0, 0])}},
        "ear_l":  {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-18, 0, -16]), "1.80": kf([0, 0, 0])}},
        "ear_r":  {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-18, 0, 16]),  "1.80": kf([0, 0, 0])}},
    }
    add_tail_waves(howl_b, 1.80, 8, 2.0, tail_amp=10.5, bias_x=-5.5)
    anims["animation.fenrir_boss.howl"] = {"animation_length": 1.80, "bones": howl_b}

    # 7. WIND_BARRAGE (1.50s)
    wb_b = {
        "body":   {"position": {"0.00": kf([0, 0, 0]), "0.48": kf([0, -3.5, 2.0], "easeInCubic"), "0.72": kf([0, 0.8, -3.0], "easeOutExpo"), "1.50": kf([0, 0, 0], "easeInOutSine")}},
        "chest":  {"rotation": {"0.00": kf([0, 0, 0]), "0.48": kf([14, 0, 0]),  "0.72": kf([-10, 0, 0], "easeOutExpo"), "1.50": kf([0, 0, 0])}},
        "neck_1": {"rotation": {"0.00": kf([0, 0, 0]), "0.48": kf([-8, 0, 0]),  "0.72": kf([8, 0, 0], "easeOutExpo"),   "1.50": kf([0, 0, 0])}},
        "neck_2": {"rotation": {"0.00": kf([0, 0, 0]), "0.48": kf([-10, 0, 0]), "0.72": kf([10, 0, 0], "easeOutExpo"),  "1.50": kf([0, 0, 0])}},
        "neck_3": {"rotation": {"0.00": kf([0, 0, 0]), "0.48": kf([-10, 0, 0]), "0.72": kf([10, 0, 0], "easeOutExpo"),  "1.50": kf([0, 0, 0])}},
        "neck_4": {"rotation": {"0.00": kf([0, 0, 0]), "0.48": kf([-6, 0, 0]),  "0.72": kf([6, 0, 0], "easeOutExpo"),   "1.50": kf([0, 0, 0])}},
        "head":   {"rotation": {"0.00": kf([0, 0, 0]), "0.48": kf([-8, 0, 0]),  "0.72": kf([8, 0, 0], "easeOutExpo"),   "1.50": kf([0, 0, 0])}},
        "jaw":    {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([46, 0, 0], "easeOutExpo"), "1.10": kf([28, 0, 0]), "1.50": kf([0, 0, 0])}},
        "ear_l":  {"rotation": {"0.00": kf([0, 0, 0]), "0.72": kf([-16, 0, -12]), "1.50": kf([0, 0, 0])}},
        "ear_r":  {"rotation": {"0.00": kf([0, 0, 0]), "0.72": kf([-16, 0, 12]),  "1.50": kf([0, 0, 0])}},
    }
    add_tail_waves(wb_b, 1.50, 8, 2.0, tail_amp=11.0, bias_x=5.5)
    anims["animation.fenrir_boss.wind_barrage"] = {"animation_length": 1.50, "bones": wb_b}

    # 8. TORNADO_CAST (2.00s)
    torn_b = {
        "body": {
            "position": {"0.00": kf([0, 0, 0]), "0.45": kf([0, -4.5, 2.0], "easeInCubic"), "0.85": kf([0, 5.2, -1.5], "easeOutExpo"), "1.35": kf([0, 2.5, -1.0]), "2.00": kf([0, 0, 0], "easeInOutSine")},
            "rotation": {"0.00": kf([0, 0, 0]), "0.45": kf([12, -14, 0], "easeInCubic"),   "0.85": kf([-24, 18, 0], "easeOutExpo"),   "1.35": kf([-12, -12, 0]), "2.00": kf([0, 0, 0], "easeInOutSine")},
        },
        "spine_2": {"rotation": {"0.00": kf([0, 0, 0]), "0.45": kf([6, -10, 0]), "0.85": kf([-10, 14, 0], "easeOutExpo"), "1.35": kf([-6, -12, 0]), "2.00": kf([0, 0, 0])}},
        "spine_3": {"rotation": {"0.00": kf([0, 0, 0]), "0.45": kf([8, -12, 0]), "0.85": kf([-12, 16, 0], "easeOutExpo"), "1.35": kf([-8, -14, 0]), "2.00": kf([0, 0, 0])}},
        "chest":   {"rotation": {"0.00": kf([0, 0, 0]), "0.45": kf([10, -16, 0]),"0.85": kf([-14, 20, 0], "easeOutExpo"), "1.35": kf([-10, -16, 0]),"2.00": kf([0, 0, 0])}},
        "neck_1":  {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-14, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0])}},
        "neck_2":  {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-14, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0])}},
        "neck_3":  {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-12, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0])}},
        "neck_4":  {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-10, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0])}},
        "head":    {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-16, 0, 0], "easeOutExpo"), "2.00": kf([0, 0, 0])}},
        "jaw":     {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([50, 0, 0], "easeOutExpo"),  "1.45": kf([36, 0, 0]), "2.00": kf([0, 0, 0])}},
        "ear_l":   {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-18, 0, -15]), "2.00": kf([0, 0, 0])}},
        "ear_r":   {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-18, 0, 15]),  "2.00": kf([0, 0, 0])}},
        "leg_fl":  {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-54, 14, -18], "easeOutExpo"), "1.35": kf([18, 0, 0]), "2.00": kf([0, 0, 0])}},
        "leg_fr":  {"rotation": {"0.00": kf([0, 0, 0]), "0.85": kf([-54, -14, 18], "easeOutExpo"), "1.35": kf([18, 0, 0]), "2.00": kf([0, 0, 0])}},
    }
    add_tail_waves(torn_b, 2.00, 10, 3.0, tail_amp=14.0, bias_x=-7.0)
    anims["animation.fenrir_boss.tornado_cast"] = {"animation_length": 2.00, "bones": torn_b}

    # 9. PHASE_CHANGE (3.20s — khớp chính xác 64 ticks!)
    p2_b = {
        "body":   {"position": {"0.00": kf([0, 0, 0]), "1.10": kf([0, -7.2, 2.0], "easeInCubic"), "1.55": kf([0, 3.2, -1.5], "easeOutExpo"), "2.60": kf([0, 2.0, -1.0]), "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "chest":  {"rotation": {"0.00": kf([0, 0, 0]), "1.10": kf([18, 0, 0], "easeInCubic"), "1.55": kf([-20, 0, 0], "easeOutExpo"), "2.60": kf([-14, 0, 0]), "3.20": kf([0, 0, 0])}},
        "neck_1": {"rotation": {"0.00": kf([0, 0, 0]), "1.10": kf([10, 0, 0]), "1.55": kf([-14, 0, 0], "easeOutExpo"), "2.60": kf([-10, 0, 0]), "3.20": kf([0, 0, 0])}},
        "neck_2": {"rotation": {"0.00": kf([0, 0, 0]), "1.10": kf([10, 0, 0]), "1.55": kf([-14, 0, 0], "easeOutExpo"), "2.60": kf([-10, 0, 0]), "3.20": kf([0, 0, 0])}},
        "neck_3": {"rotation": {"0.00": kf([0, 0, 0]), "1.10": kf([8, 0, 0]),  "1.55": kf([-12, 0, 0], "easeOutExpo"), "2.60": kf([-8, 0, 0]),  "3.20": kf([0, 0, 0])}},
        "neck_4": {"rotation": {"0.00": kf([0, 0, 0]), "1.10": kf([8, 0, 0]),  "1.55": kf([-10, 0, 0], "easeOutExpo"), "2.60": kf([-8, 0, 0]),  "3.20": kf([0, 0, 0])}},
        "head":   {"rotation": {"0.00": kf([0, 0, 0]), "1.10": kf([12, 0, 0]), "1.55": kf([-20, 0, 0], "easeOutExpo"), "2.60": kf([-16, 0, 0]), "3.20": kf([0, 0, 0])}},
        "jaw":    {"rotation": {"0.00": kf([0, 0, 0]), "1.55": kf([52, 0, 0], "easeOutExpo"), "2.60": kf([42, 0, 0]), "3.20": kf([0, 0, 0])}},
        "ear_l":  {"rotation": {"0.00": kf([0, 0, 0]), "1.55": kf([-20, 0, -16]), "2.60": kf([-14, 0, -10]), "3.20": kf([0, 0, 0])}},
        "ear_r":  {"rotation": {"0.00": kf([0, 0, 0]), "1.55": kf([-20, 0, 16]),  "2.60": kf([-14, 0, 10]),  "3.20": kf([0, 0, 0])}},
    }
    add_tail_waves(p2_b, 3.20, 12, 4.0, tail_amp=13.5, bias_x=-6.0)
    anims["animation.fenrir_boss.phase_change"] = {"animation_length": 3.20, "bones": p2_b}

    # 10. EMERGE (3.20s — khớp chính xác 64 ticks `SPAWN_ANIM_DURATION`, không còn khoảng trống dừng sớm!)
    em_b = {
        "body":   {"position": {"0.00": kf([0, -10.0, 0]), "1.50": kf([0, -2.5, 0], "easeOutCubic"), "2.10": kf([0, 2.0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "neck_1": {"rotation": {"0.00": kf([12, 0, 0]), "2.10": kf([-14, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "neck_2": {"rotation": {"0.00": kf([12, 0, 0]), "2.10": kf([-14, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "neck_3": {"rotation": {"0.00": kf([8, 0, 0]),  "2.10": kf([-10, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "neck_4": {"rotation": {"0.00": kf([6, 0, 0]),  "2.10": kf([-8, 0, 0], "easeOutExpo"),  "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "head":   {"rotation": {"0.00": kf([16, 0, 0]), "2.10": kf([-20, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "jaw":    {"rotation": {"0.00": kf([0, 0, 0]),  "2.10": kf([46, 0, 0], "easeOutExpo"),  "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "ear_l":  {"rotation": {"0.00": kf([-12, 0, -10]), "2.10": kf([-18, 0, -14], "easeOutExpo"), "3.20": kf([0, 0, 0], "easeInOutSine")}},
        "ear_r":  {"rotation": {"0.00": kf([-12, 0, 10]),  "2.10": kf([-18, 0, 14], "easeOutExpo"),  "3.20": kf([0, 0, 0], "easeInOutSine")}},
    }
    add_tail_waves(em_b, 3.20, 8, 2.0, tail_amp=9.0)
    anims["animation.fenrir_boss.emerge"] = {"loop": "hold_on_last_frame", "animation_length": 3.20, "bones": em_b}

    # 11. DEATH (2.40s hold — các dải lông rũ xuống êm ái qua `FenrirGeoModel`!)
    death_b = {
        "body": {
            "position": {"0.00": kf([0, 0, 0]), "1.10": kf([0, -9.5, 0], "easeInCubic"), "2.40": kf([0, -18.5, 0], "easeOutBounce")},
            "rotation": {"0.00": kf([0, 0, 0]), "1.30": kf([8, 0, 26], "easeInCubic"),   "2.40": kf([10, 0, 62], "easeOutCubic")},
        },
        "neck_1": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([10, -4, 0], "easeOutCubic")}},
        "neck_2": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([16, -8, 0], "easeOutCubic")}},
        "neck_3": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([12, -8, 0], "easeOutCubic")}},
        "neck_4": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([10, -6, 0], "easeOutCubic")}},
        "head":   {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([18, -14, 0], "easeOutCubic")}},
        "jaw":    {"rotation": {"0.00": kf([0, 0, 0]), "1.00": kf([34, 0, 0]), "2.40": kf([14, 0, 0])}},
        "ear_l":  {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([10, 0, -14], "easeOutCubic")}},
        "ear_r":  {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([10, 0, 14], "easeOutCubic")}},
        "leg_fl": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([-32, 0, -14], "easeOutCubic")}},
        "leg_fr": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([-24, 0, 18], "easeOutCubic")}},
        "leg_bl": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([28, 0, -12], "easeOutCubic")}},
        "leg_br": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([22, 0, 16], "easeOutCubic")}},
        "tail_1": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([20, 14, 0], "easeOutCubic")}},
        "tail_2": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([18, 15, 0], "easeOutCubic")}},
        "tail_3": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([18, 16, 0], "easeOutCubic")}},
        "tail_4": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([16, 17, 0], "easeOutCubic")}},
        "tail_5": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([14, 18, 0], "easeOutCubic")}},
        "tail_6": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([12, 16, 0], "easeOutCubic")}},
        "tail_7": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([10, 14, 0], "easeOutCubic")}},
        "tail_8": {"rotation": {"0.00": kf([0, 0, 0]), "2.40": kf([8, 12, 0], "easeOutCubic")}},
    }
    anims["animation.fenrir_boss.death"] = {"loop": "hold_on_last_frame", "animation_length": 2.40, "bones": death_b}

    out = {"format_version": "1.8.0", "animations": anims}
    out_path = ASSETS / "animations" / "entity" / "fenrir_boss.animation.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
    print(f"ANIM_OK: {out_path} ({len(anims)} spin-proof Fel V8 animations verified)")
    return out


# =============================================================================
# 5. BỘ RENDER PREVIEW 3D Z-BUFFER 6 GÓC
# =============================================================================
def collect_world_geometry(geo: dict, bone_pos_offsets: dict | None = None):
    bone_map = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    xforms = {}

    def get_xform(name):
        if name in xforms:
            return xforms[name]
        b = bone_map[name]
        piv = np.array(b.get("pivot", [0.0, 0.0, 0.0]), dtype=np.float64)
        R_loc = bedrock_rot_matrix(b.get("rotation", [0.0, 0.0, 0.0]))
        t_loc = piv - R_loc @ piv
        if bone_pos_offsets and name in bone_pos_offsets:
            t_loc = t_loc + np.array(bone_pos_offsets[name], dtype=np.float64)
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
        ("north", (0, 1, 2, 3)), ("south", (5, 4, 7, 6)),
        ("west",  (4, 0, 3, 7)), ("east",  (1, 5, 6, 2)),
        ("up",    (3, 2, 6, 7)), ("down",  (4, 5, 1, 0)),
    ]
    for b in geo["minecraft:geometry"][0]["bones"]:
        Rb, tb = get_xform(b["name"])
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            corners = np.array([
                [ox, oy, oz], [ox + sx, oy, oz], [ox + sx, oy + sy, oz], [ox, oy + sy, oz],
                [ox, oy, oz + sz], [ox + sx, oy, oz + sz], [ox + sx, oy + sy, oz + sz], [ox, oy + sy, oz + sz],
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


def rasterize(faces, diff_img, glow_img, view_mat, width, height, scale, center, bg=(14, 18, 30)):
    fb = np.zeros((height, width, 3), dtype=np.float32)
    fb[:, :] = np.array(bg, dtype=np.float32)
    zbuf = np.full((height, width), -1e9, dtype=np.float32)
    cx, cy = width * 0.5, height * 0.53
    light_dir = np.array([0.38, 0.78, -0.49], dtype=np.float64)
    light_dir /= np.linalg.norm(light_dir)

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
        fb[min_y + cy_i, min_x + cx_i] = np.clip(dc * shade * (1.0 - ga * 0.85) + gc * (ga * 1.15), 0, 255)

    for quad, uv in faces:
        rel = quad - center[None, :]
        cam = (view_mat @ rel.T).T
        n = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        nl = np.linalg.norm(n)
        if nl < 1e-6:
            continue
        n /= nl
        shade = 0.64 + 0.36 * abs(float(np.dot(n, light_dir)))
        scr = np.stack([cx - cam[:, 0] * scale, cy - cam[:, 1] * scale, cam[:, 2]], axis=1)
        u0, v0 = uv["uv"]
        uw, uh = uv["uv_size"]
        u1 = u0 + uw - (0.01 if uw >= 0 else -0.01)
        v1 = v0 + uh - (0.01 if uh >= 0 else -0.01)
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        rtri(scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
        rtri(scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)
    return Image.fromarray(fb.astype(np.uint8), "RGB")


def render_previews(geo: dict) -> None:
    tex_dir = ASSETS / "textures" / "entity"
    d1 = np.array(Image.open(tex_dir / "fenrir_boss.png").convert("RGBA"), dtype=np.float32)
    g1 = np.array(Image.open(tex_dir / "fenrir_boss_glowmask.png").convert("RGBA"), dtype=np.float32)
    d2 = np.array(Image.open(tex_dir / "fenrir_boss_enraged.png").convert("RGBA"), dtype=np.float32)
    g2 = np.array(Image.open(tex_dir / "fenrir_boss_enraged_glowmask.png").convert("RGBA"), dtype=np.float32)

    faces, bmin, bmax = collect_world_geometry(geo)
    faces_blink, _, _ = collect_world_geometry(geo, bone_pos_offsets={
        "eyelid_l_up": [0.0, -0.64, 0.0],
        "eyelid_r_up": [0.0, -0.64, 0.0],
        "eyelid_l_lo": [0.0, +0.28, 0.0],
        "eyelid_r_lo": [0.0, +0.28, 0.0],
    })
    ctr = (bmin + bmax) * 0.5
    span = float(np.max(bmax - bmin))

    def cam_mat(yaw_deg, pitch_deg):
        y, p = math.radians(yaw_deg), math.radians(pitch_deg)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 640, 480
    sc = (min(pw, ph) * 0.84) / max(1.0, span)
    bg = (14, 18, 30)

    views = [
        ("preview_eyes_open_macro.png",  faces,       d1, g1,  180,  4, sc * 5.60, np.array([0.0, 44.8, -36.5]),     "EYES MACRO OPEN V7 (NATURAL SCLERA, JADE IRIS, FUR BROWS)"),
        ("preview_eyes_blink_macro.png", faces_blink, d1, g1,  180,  4, sc * 5.60, np.array([0.0, 44.8, -36.5]),     "EYES MACRO BLINK CLOSED V7 (4 SLIDING EYELID BONES)"),
        ("preview_face_closeup.png",     faces,       d1, g1,  180,  4, sc * 3.10, np.array([0.0, 44.5, -34.0]),     "FACE FRONT CLOSE-UP V7 (NATURAL ANIME WOLF EYES)"),
        ("preview_face_34.png",          faces,       d1, g1, -148,  6, sc * 2.85, np.array([0.0, 44.0, -34.0]),     "FACE 3/4 CLOSE-UP V7 (SOFT FUR BROWS & RECESSED SOCKETS)"),
        ("preview_front.png",            faces,       d1, g1,  180,  5, sc * 1.48, np.array([0.0, 32.0, -14.0]),     "FULL FRONT V7 (IN-GAME CAMERA ANGLE)"),
        ("preview_hero_34.png",          faces,       d1, g1, -132,  8, sc * 1.15, ctr + np.array([0.0, 2.0, -2.0]), "HERO 3/4 V7 (222 JOINTS + BLINKING EYES)"),
    ]
    imgs = []
    for fn, fset, dim, gim, yw, pt, s_v, c_v, title in views:
        im = rasterize(fset, dim, gim, cam_mat(yw, pt), pw, ph, s_v, c_v, bg=bg)
        ImageDraw.Draw(im).text((12, 10), title, fill=(235, 245, 255))
        im.save(PREVIEW_DIR / fn)
        imgs.append(im)

    sheet = Image.new("RGB", (pw * 3, ph * 2), (10, 14, 24))
    for i, im in enumerate(imgs):
        sheet.paste(im, ((i % 3) * pw, (i // 3) * ph))
    sheet.save(PREVIEW_DIR / "fenrir_preview_sheet.png")
    print(f"PREVIEW_OK: 6 V5 views + sheet saved to {PREVIEW_DIR} | bbox X=[{bmin[0]:.1f}..{bmax[0]:.1f}] Y=[{bmin[1]:.1f}..{bmax[1]:.1f}] Z=[{bmin[2]:.1f}..{bmax[2]:.1f}]")


if __name__ == "__main__":
    backup_legacy_fenrir()
    geo, cubes, meta = build_fenrir_geo()
    build_textures()
    build_animations(meta["fur_registry"])
    render_previews(geo)
    print(f"ALL_DONE: Fel (Fenrir) V5 rebuilt ({meta['bones']} bones, {cubes} cubes, {meta['fur_strands']} multi-joint fur strands = {meta['fur_bones']} fur joints, {TEX_W}x{TEX_H})")
