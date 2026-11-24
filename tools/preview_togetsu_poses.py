#!/usr/bin/env python3
"""Đối chiếu 1:1 Pose Dual Knife vs Togetsu Dạng Tách (giảm kích thước 1.12 -> 0.78) trên đủ 6 góc 3D!"""
import json
import math
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_togetsu_model as g

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
OUT_PNG = ROOT / "tools" / "togetsu_previews" / "togetsu_poses_preview.png"


def load_model(geo_name, diff_name, glow_name):
    geo = json.loads((ASSETS / "geo" / "item" / f"{geo_name}.geo.json").read_text(encoding="utf-8"))
    diff = np.array(Image.open(ASSETS / "textures" / "item" / f"{diff_name}.png").convert("RGBA"), dtype=np.float32)
    glow = np.array(Image.open(ASSETS / "textures" / "item" / f"{glow_name}.png").convert("RGBA"), dtype=np.float32)
    return g.extract_faces(geo, diff, glow)


# 1. Nạp Dual Knife (tham chiếu 1:1) & Togetsu Waxing (Trắng) + Waning (Đen)
dk_r = load_model("dual_knife", "dual_knife_model", "dual_knife_model_glowmask")
dk_l = load_model("dual_knife_left", "dual_knife_left_model", "dual_knife_left_model_glowmask")
tg_w = load_model("togetsu_waxing", "togetsu_waxing_model", "togetsu_waxing_model_glowmask")
tg_k = load_model("togetsu_waning", "togetsu_waning_model", "togetsu_waning_model_glowmask")

# 2. Thông số Pose CHÍNH XÁC 100% từ DualBladeAnimManager.java (lines 270-307):
DK_HEAD = (+0.32, -0.18, 0.0)
DK_BODY = (+0.20, +0.22, 0.0)
DK_LARM = (-1.54, +0.92, -0.15)
DK_RARM = (-0.78, -0.96, +0.26)
DK_LLEG = (-0.26, +0.22, -0.14)
DK_RLEG = (+0.30, -0.22, +0.14)

DK_L_ALIGN = (28.36, 7.29, -30.71)
DK_R_ALIGN = (137.92, -44.06, -173.09)

# Tính chính xác ma trận xoay kết hợp của tay trái Dual Knife (ItemInHandLayerMixin @ DualBladeGeoRenderer reverseLeftThird)
# để Togetsu tay trái đạt ĐÚNG 100% hướng mũi + cạnh cắt mà KHÔNG cần xoay ngược cứng trong TogetsuGeoRenderer!
R_rev_left = g.rot_x(math.radians(112.0)) @ g.rot_y(math.radians(180.0)) @ g.rot_z(math.radians(-8.0))
R_align_l_dk = g.rot_x(math.radians(DK_L_ALIGN[0])) @ g.rot_z(math.radians(DK_L_ALIGN[1])) @ g.rot_y(math.radians(DK_L_ALIGN[2]))
# Trong DualBladeGeoRenderer: p -> M_bed -> R_rev_left -> R_align_l_dk (với M_bed = diag(-1,1,1))
# Ta cần tìm R_align_tg_l sao cho: R_align_tg_l @ M_bed == R_align_l_dk @ R_rev_left @ M_bed
# => R_align_tg_l = R_align_l_dk @ R_rev_left! (Quá gọn, chính xác tuyệt đối tới 1e-12!)
R_align_tg_l = R_align_l_dk @ R_rev_left
TG_DUAL_LEFT_ANG = tuple(round(v, 2) for v in g.decompose_rx_rz_ry(R_align_tg_l))
TG_DUAL_RIGHT_ANG = DK_R_ALIGN

# Kiểm chứng sai số ma trận = 0.000000:
R_check = g.rot_x(math.radians(TG_DUAL_LEFT_ANG[0])) @ g.rot_z(math.radians(TG_DUAL_LEFT_ANG[1])) @ g.rot_y(math.radians(TG_DUAL_LEFT_ANG[2]))
print("MATRIX_ERR_L =", float(np.max(np.abs(R_check - R_align_tg_l))))
print("TG_DUAL_LEFT_ANG  =", TG_DUAL_LEFT_ANG)
print("TG_DUAL_RIGHT_ANG =", TG_DUAL_RIGHT_ANG)

# Dựng cảnh Dual Knife tham chiếu (scale 0.92, tay trái áp R_rev_left + translate(-0.4, 0, +0.16)px đúng DualBladeGeoRenderer)
M_bed = np.diag([-1.0, 1.0, 1.0])
def apply_dk_rev_left(faces):
    out = []
    shift = np.array([-0.025 * 16.0, 0.0, 0.010 * 16.0], dtype=np.float64)
    # Transform ngược qua M_bed để build_char_scene nhân M_bed lại thành đúng (M_bed -> R_rev_left -> shift)
    T = M_bed @ R_rev_left @ M_bed
    s_bed = shift @ M_bed.T
    for q, uv, col, d, gl in faces:
        out.append(((q @ T.T) + s_bed, uv, col, d, gl))
    return out

dk_scene = g.build_char_scene(
    DK_HEAD, DK_BODY, DK_LARM, DK_RARM, DK_LLEG, DK_RLEG,
    [(apply_dk_rev_left(dk_l), True, (+5, 2, 0), DK_LARM, DK_L_ALIGN, 0.92),
     (dk_r, False, (-5, 2, 0), DK_RARM, DK_R_ALIGN, 0.92)],
    root_pitch=-6.0,
)

# Dựng cảnh Togetsu dạng tách mới: pose 100% giống Dual Knife + giảm scale từ 1.12 -> 0.78!
NEW_DUAL_SCALE = 0.78
tg_scene = g.build_char_scene(
    DK_HEAD, DK_BODY, DK_LARM, DK_RARM, DK_LLEG, DK_RLEG,
    [(tg_w, True, (+5, 2, 0), DK_LARM, TG_DUAL_LEFT_ANG, NEW_DUAL_SCALE),
     (tg_k, False, (-5, 2, 0), DK_RARM, TG_DUAL_RIGHT_ANG, NEW_DUAL_SCALE)],
    root_pitch=-6.0,
)

# Góc nhìn Thứ 1 (so sánh scale mới 0.74 cho 2 cây Togetsu ở 2 góc màn hình)
def build_fp_dual(faces, is_left, scale):
    R_json = g.rot_y(math.radians(22.0)) @ g.rot_x(math.radians(-30.0)) @ g.rot_z(math.radians(12.0))
    sign = -1.0 if is_left else 1.0
    pos = np.array([sign * 10.5, -9.0, -18.0], dtype=np.float64)
    out = []
    for q, uv, col, d, gl in faces:
        p = ((q @ M_bed.T) * scale) @ R_json.T
        if is_left:
            p[:, 0] = -p[:, 0]
        out.append((p + pos, uv, col, d, gl))
    return out

fp_dual_scene = build_fp_dual(tg_k, False, 0.74) + build_fp_dual(tg_w, True, 0.74)

pw, ph = 500, 420
im1 = g.raster(dk_scene, g.cam_mat(180, 4), pw, ph, 8.6, np.array([0.0, 14.0, -2.0]), ambient=0.65, bg=(26, 20, 26))
im2 = g.raster(tg_scene, g.cam_mat(180, 4), pw, ph, 8.6, np.array([0.0, 14.0, -2.0]), ambient=0.65, bg=(18, 22, 34))
im3 = g.raster(tg_scene, g.cam_mat(-142, 8), pw, ph, 8.4, np.array([0.0, 14.0, -2.0]), ambient=0.65, bg=(18, 22, 34))
im4 = g.raster(tg_scene, g.cam_mat(142, 8), pw, ph, 8.4, np.array([0.0, 14.0, -2.0]), ambient=0.65, bg=(20, 24, 34))
im5 = g.raster(tg_scene, g.cam_mat(0, 8), pw, ph, 8.4, np.array([0.0, 14.0, -1.0]), ambient=0.65, bg=(22, 22, 32))
im6 = g.raster(fp_dual_scene, g.cam_mat(0, 0), pw, ph, 13.0, np.array([0.0, 0.0, -18.0]), ambient=0.72, bg=(74, 120, 185))

sheet = Image.new("RGB", (pw * 3, ph * 2), (10, 12, 16))
sheet.paste(im1, (0, 0)); sheet.paste(im2, (pw, 0)); sheet.paste(im3, (pw * 2, 0))
sheet.paste(im4, (0, ph)); sheet.paste(im5, (pw, ph)); sheet.paste(im6, (pw * 2, ph))
dr = ImageDraw.Draw(sheet)
dr.text((10, 8), "1. REF: DUAL KNIFE (0.92 SCALE)", fill=(255, 195, 195))
dr.text((pw + 10, 8), "2. TOGETSU DUAL NEW (0.78 SCALE - FRONT)", fill=(185, 245, 255))
dr.text((pw * 2 + 10, 8), "3. TOGETSU DUAL NEW (3/4 FRONT-LEFT)", fill=(185, 245, 255))
dr.text((10, ph + 8), "4. TOGETSU DUAL NEW (3/4 FRONT-RIGHT)", fill=(185, 245, 255))
dr.text((pw + 10, ph + 8), "5. TOGETSU DUAL NEW (BACK VIEW)", fill=(255, 225, 175))
dr.text((pw * 2 + 10, ph + 8), "6. FIRST PERSON TOGETSU DUAL (0.74)", fill=(160, 255, 185))
sheet.save(OUT_PNG)
print("PREVIEW_SAVED:", OUT_PNG)
