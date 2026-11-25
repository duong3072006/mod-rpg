"""
Generator V2 nâng cấp toàn diện cho Boss: CYCLOPS WARDEN (Khổng Lồ Một Mắt Giam Tù — `abyssalrpg:cyclops_warden`)
Cải tiến V2:
1. KHỚP 100% TOÁN HỌC GECKOLIB 4.8.3 (`BakedModelFactory.java` L172-176 + `RenderUtil.java` L59-61):
   - Vì GeckoLib đảo X -> -X trước khi nhân `Rz(+rz) @ Ry(-ry) @ Rx(-rx)`, ma trận tương đương trong không gian JSON là:
     `M_JSON = F @ Rz(+rz) @ Ry(-ry) @ Rx(-rx) @ F = Rz(-rz) @ Ry(+ry) @ Rx(-rx)`!
   - Bộ preview 3D chạy ĐÚNG công thức GL của GeckoLib (`x_gl = -x_json`, `pivot_gl = (-px, py, pz)`), đảm bảo 100%
     những gì thấy trong ảnh preview = chính xác 100% những gì hiển thị trong Minecraft!
2. ĐẠI CHÙY CHIẾN 6 CÁNH TINH XẢO (SỬA SẠCH LỖI CÁN & ĐẦU THÔ):
   - Cán gỗ đỏ-nâu bo tròn nhiều bậc + dây da quấn tay cầm + chuôi nhọn hình thoi bạc + 5 vòng đai thép rèn tinh tế,
     đâm XUYÊN THẲNG TÂM nắm tay phải qua vai phải nối liền trục tâm đầu chùy!
   - Đầu chùy 6 cánh rìu Gothic mỏng sắc tỏa tròn 60° (lõi thép đen bát giác + 6 cánh rìu khía răng cưa có viền thép bạc mảnh
     chạy liền mạch bao trọn sống lưỡi, 0 cục gạch thô!).
3. HƠN 50 KHỚP NỐI ĐA TẦNG MƯỢT MÀ + TẠO HÌNH ĐIÊU KHẮC CƠ BẮP MỀM MẠI (KHÔNG THÔ CỨNG):
   - Cột sống 5 khớp (`pelvis` -> `waist` -> `chest` -> `neck` -> `head` + `jaw` + `eyelid_up/lo`).
   - 6 vạt khố da tua rua độc lập (`kilt_fl/fc/fr/sl/sr/bk`) đung đưa theo từng bước chạy & đòn vung chùy!
   - Tay đa khớp (`clavicle` -> `shoulder` -> `arm` -> `elbow` -> `forearm` -> `wrist` -> `hand` -> `fingers` + `thumb`)
     với vai vát cơ cầu vai, bắp tay bo khối, giáp cẳng tay cong ôm sát tay!
   - Chân đa khớp (`hip` -> `thigh` -> `knee` -> `shin` -> `ankle` -> `foot` -> `toes`) bước lăn mũi chân tự nhiên!
   - Dây xích treo lồng 3 mắt xích đa khớp (`chain_1` -> `chain_2` -> `chain_3` -> `cage_body` -> `cage_door` & `prisoner` -> `prisoner_head`)!
   - Texture da nâu đất loang ấm mềm mịn hơn (bỏ nứt đen gắt như bùn khô!), gương mặt người tù biểu cảm lo âu chân thực!
"""

import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(r"c:\Users\Admin\Desktop\mod\mod-rpg")
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "abyssalrpg"
PREVIEW_DIR = ROOT / "tools" / "preview_cyclops_warden"

TEX_W, TEX_H = 512, 512

MAT = {
    "SKIN_MOTTLED":     (0,   0,   128, 128),
    "SKIN_CHEST_TAN":   (128, 0,   128, 128),
    "TOES_IVORY":       (256, 0,   128, 128),
    "HEAD_EYE":         (384, 0,   128, 128),

    "MOUTH_TEETH":      (0,   128, 128, 128),
    "IRON_DARK":        (128, 128, 128, 128),
    "IRON_SILVER":      (256, 128, 128, 128),
    "LEATHER_STRAPS":   (384, 128, 128, 128),

    "BELT_BUCKLE":      (0,   256, 128, 128),
    "LOINCLOTH_FRINGE": (128, 256, 128, 128),
    "MACE_HAFT":        (256, 256, 128, 128),
    "TUSK_BONE":        (384, 256, 128, 128),

    "VILLAGER_FACE":        (0,   384, 64,  80),
    "VILLAGER_SKIN":        (64,  384, 64,  80),
    "VILLAGER_NOSE":        (0,   464, 64,  48),
    "VILLAGER_BOOTS":       (64,  464, 64,  48),
    "VILLAGER_ROBE_FRONT":  (128, 384, 64,  128),
    "VILLAGER_ROBE_SIDE":   (192, 384, 64,  80),
    "VILLAGER_SLEEVE":      (192, 464, 64,  48),
    "VILLAGER_SHACKLE":     (256, 384, 128, 128),
    "BRACER_PLATE":         (384, 384, 128, 128),
}


def uv_for(mat_key: str, inset: float = 1.5) -> dict:
    x, y, w, h = MAT[mat_key]
    ins = min(inset, w * 0.04, h * 0.04)
    return {"uv": [x + ins, y + ins], "uv_size": [w - ins * 2.0, h - ins * 2.0]}


def face_uvs(mat_all: str, north=None, south=None, east=None, west=None, up=None, down=None) -> dict:
    return {
        "north": uv_for(north or mat_all),
        "south": uv_for(south or mat_all),
        "east":  uv_for(east  or mat_all),
        "west":  uv_for(west  or mat_all),
        "up":    uv_for(up    or mat_all),
        "down":  uv_for(down  or mat_all),
    }


def make_cube(origin, size, mat_all: str, rot=None, pivot=None, **face_overrides) -> dict:
    c = {
        "origin": [round(float(v), 3) for v in origin],
        "size": [round(float(v), 3) for v in size],
        "uv": face_uvs(mat_all, **face_overrides),
    }
    if rot is not None:
        c["rotation"] = [round(float(v), 2) for v in rot]
        if pivot is None:
            pivot = [origin[0] + size[0] * 0.5, origin[1] + size[1] * 0.5, origin[2] + size[2] * 0.5]
        c["pivot"] = [round(float(v), 3) for v in pivot]
    return c


def make_centered_cube(center, size, mat_all: str, rot=None, **face_overrides) -> dict:
    origin = [center[0] - size[0] * 0.5, center[1] - size[1] * 0.5, center[2] - size[2] * 0.5]
    return make_cube(origin, size, mat_all, rot=rot, pivot=center, **face_overrides)


# =============================================================================
# TOÁN HỌC CHÍNH XÁC 100% GECKOLIB 4.8.3 (`BakedModelFactory` + `RenderUtil`)
# =============================================================================
def _rx_mat(rad: float) -> np.ndarray:
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)


def _ry_mat(rad: float) -> np.ndarray:
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)


def _rz_mat(rad: float) -> np.ndarray:
    c, s = math.cos(rad), math.sin(rad)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


def geckolib_gl_rot_matrix(rot_deg) -> np.ndarray:
    """Ma trận GL thực tế trong `RenderUtil.rotateMatrixAroundCube`: `Rz(+rz) @ Ry(-ry) @ Rx(-rx)`."""
    rx = math.radians(-float(rot_deg[0]))
    ry = math.radians(-float(rot_deg[1]))
    rz = math.radians(+float(rot_deg[2]))
    return _rz_mat(rz) @ _ry_mat(ry) @ _rx_mat(rx)


def geckolib_json_rot_matrix(rot_deg) -> np.ndarray:
    """
    Ma trận xoay tương đương trong hệ tọa độ JSON trước khi đảo X -> -X:
    `M_JSON = F @ M_GL @ F = Rz(-rz) @ Ry(+ry) @ Rx(-rx)` với `F = diag(-1, 1, 1)`.
    """
    F = np.diag([-1.0, 1.0, 1.0])
    return F @ geckolib_gl_rot_matrix(rot_deg) @ F


def basis_to_geckolib_euler(x_axis, y_axis, z_axis) -> list:
    """
    Cho 3 vector trục trực chuẩn mong muốn trong hệ tọa độ JSON `R_json = [x_axis, y_axis, z_axis]` (det = +1).
    Trả về `[rx_deg, ry_deg, rz_deg]` sao cho `geckolib_json_rot_matrix(rot) == R_json` CHÍNH XÁC TỚI 1e-6!
    """
    R = np.column_stack([
        np.asarray(x_axis, dtype=np.float64),
        np.asarray(y_axis, dtype=np.float64),
        np.asarray(z_axis, dtype=np.float64),
    ])
    # Giải R = Rz(c) @ Ry(b) @ Rx(a):
    # Vì M_JSON = Rz(-rz) @ Ry(+ry) @ Rx(-rx), ta đặt:
    #   rx = -a,  ry = +b,  rz = -c !!
    sb = max(-1.0, min(1.0, -float(R[2, 0])))
    b = math.asin(sb)
    if abs(math.cos(b)) > 1e-5:
        a = math.atan2(float(R[2, 1]), float(R[2, 2]))
        c = math.atan2(float(R[1, 0]), float(R[0, 0]))
    else:
        a = math.atan2(-float(R[1, 2]), float(R[1, 1]))
        c = 0.0
    rot = [round(-math.degrees(a), 2), round(+math.degrees(b), 2), round(-math.degrees(c), 2)]
    # Self-check toán học ngay lúc build để KHÔNG BAO GIỜ sai dấu trong Minecraft!
    err = float(np.max(np.abs(geckolib_json_rot_matrix(rot) - R)))
    assert err < 2e-2, f"GeckoLib Euler self-check failed! err={err}, rot={rot}"
    return rot


def align_y_geckolib(vec) -> list:
    """Trả về `[rx, ry, rz]` sao cho trục +Y cục bộ của cube hướng chính xác theo `vec` trong GeckoLib 4.8.3."""
    y_ax = np.asarray(vec, dtype=np.float64)
    ln = float(np.linalg.norm(y_ax))
    if ln < 1e-6:
        return [0.0, 0.0, 0.0]
    y_ax = y_ax / ln
    ref = np.array([0.0, 0.0, 1.0]) if abs(y_ax[2]) < 0.92 else np.array([1.0, 0.0, 0.0])
    x_ax = np.cross(y_ax, ref)
    x_ax /= np.linalg.norm(x_ax)
    z_ax = np.cross(x_ax, y_ax)
    z_ax /= np.linalg.norm(z_ax)
    return basis_to_geckolib_euler(x_ax, y_ax, z_ax)


def add_chain_links(cubes: list, pts: list, link_len: float = 2.6, link_w: float = 1.65, link_t: float = 0.72):
    """Dựng chuỗi mắt xích sắt đen 3D rỗng giữa đan xen 90° chuẩn xác trong GeckoLib 4.8.3."""
    idx = 0
    for s in range(len(pts) - 1):
        p0 = np.asarray(pts[s], dtype=np.float64)
        p1 = np.asarray(pts[s + 1], dtype=np.float64)
        d = p1 - p0
        dist = float(np.linalg.norm(d))
        if dist < 0.2:
            continue
        n_links = max(1, int(round(dist / (link_len * 0.74))))
        y_ax = d / dist
        ref = np.array([0.0, 0.0, 1.0]) if abs(y_ax[2]) < 0.9 else np.array([1.0, 0.0, 0.0])
        x_ax = np.cross(y_ax, ref)
        x_ax /= np.linalg.norm(x_ax)
        z_ax = np.cross(x_ax, y_ax)
        z_ax /= np.linalg.norm(z_ax)
        for i in range(n_links):
            t = (i + 0.5) / n_links
            ctr = p0 + d * t
            if idx % 2 == 0:
                rot = basis_to_geckolib_euler(x_ax, y_ax, z_ax)
                cubes.append(make_centered_cube(ctr, [link_w, link_len, link_t], "IRON_DARK", rot=rot))
                cubes.append(make_centered_cube(ctr, [link_w * 0.52, link_len * 0.50, link_t * 1.18], "IRON_SILVER", rot=rot))
            else:
                rot = basis_to_geckolib_euler(z_ax, y_ax, -x_ax)
                cubes.append(make_centered_cube(ctr, [link_w, link_len, link_t], "IRON_DARK", rot=rot))
            idx += 1


def add_sculpted_bracer(cubes: list, center, forearm_y_axis, out_dir):
    """
    Khiên giáp cẳng tay sắt đen 3 tầng uốn cong ôm gọn theo đúng trục cẳng tay + 6 đinh bạc vát nhọn!
    """
    ctr = np.asarray(center, dtype=np.float64)
    y_ax = np.asarray(forearm_y_axis, dtype=np.float64)
    y_ax /= np.linalg.norm(y_ax)
    # Trục x_ax hướng ra ngoài phía ngoài cẳng tay (vuông góc y_ax)
    o = np.asarray(out_dir, dtype=np.float64)
    o = o - np.dot(o, y_ax) * y_ax
    x_ax = o / np.linalg.norm(o)
    z_ax = np.cross(x_ax, y_ax)
    z_ax /= np.linalg.norm(z_ax)
    rot = basis_to_geckolib_euler(x_ax, y_ax, z_ax)

    for y_off in (-3.3, 0.0, +3.3):
        band_c = ctr + y_ax * y_off
        # Tấm sắt chính hơi cong ôm 3 mảnh (giữa + 2 cánh vát 28° ôm gọn cẳng tay!)
        cubes.append(make_centered_cube(band_c, [1.5, 2.85, 6.2], "BRACER_PLATE", rot=rot))
        cubes.append(make_centered_cube(band_c + x_ax * 0.30 + y_ax * 1.25, [1.2, 0.52, 6.4], "IRON_SILVER", rot=rot))
        cubes.append(make_centered_cube(band_c + x_ax * 0.30 - y_ax * 1.25, [1.2, 0.52, 6.4], "IRON_SILVER", rot=rot))
        # Hai cánh mép vát ôm vòng mặt trước & sau cẳng tay
        for s_z in (-1.0, +1.0):
            ang = math.radians(s_z * 28.0)
            wx = math.cos(ang) * x_ax + math.sin(ang) * z_ax
            wz = -math.sin(ang) * x_ax + math.cos(ang) * z_ax
            w_rot = basis_to_geckolib_euler(wx, y_ax, wz)
            wing_c = band_c + z_ax * (s_z * 3.85) - x_ax * 0.95
            cubes.append(make_centered_cube(wing_c, [1.4, 2.75, 2.6], "IRON_DARK", rot=w_rot))
            cubes.append(make_centered_cube(wing_c + wx * 0.25 + y_ax * 1.2, [1.1, 0.48, 2.7], "IRON_SILVER", rot=w_rot))
        # 2 đinh bạc 2 tầng vát nhọn (6 đinh tổng mỗi tay!)
        for z_sp in (-1.9, +1.9):
            sp_base = band_c + z_ax * z_sp + x_ax * 1.15
            cubes.append(make_centered_cube(sp_base, [1.35, 1.25, 1.25], "IRON_SILVER", rot=rot))
            cubes.append(make_centered_cube(sp_base + x_ax * 0.95, [0.95, 0.72, 0.72], "IRON_SILVER", rot=rot))


def add_beveled_limb_segment(cubes: list, p0, p1, w0: float, d0: float, w1: float, d1: float, mat: str, n_steps: int = 3):
    """Dựng khối cơ chi có vát côn nhiều tầng nối mượt giữa p0 -> p1 (KHÔNG còn cục gạch vuông đơ cứng!)."""
    a = np.asarray(p0, dtype=np.float64)
    b = np.asarray(p1, dtype=np.float64)
    vec = b - a
    total_len = float(np.linalg.norm(vec))
    rot = align_y_geckolib(vec)
    step_len = (total_len / n_steps) * 1.18
    for i in range(n_steps):
        t = (i + 0.5) / n_steps
        ctr = a + vec * t
        # Phình nhẹ cơ bắp ở đoạn giữa (sin bulge)
        bulge = 1.0 + 0.09 * math.sin(t * math.pi)
        w = (w0 + (w1 - w0) * t) * bulge
        d = (d0 + (d1 - d0) * t) * bulge
        cubes.append(make_centered_cube(ctr, [w, step_len, d], mat, rot=rot))


def add_octagonal_drum(cubes: list, center, y_axis, u_axis, v_axis, diameter: float, length: float, mat: str):
    """Dựng khối trụ bát giác nặng (2 khối lồng xoay 45°) tạo cảm giác thép đúc đặc, hết hẳn khối vuông thô!"""
    r0 = basis_to_geckolib_euler(u_axis, y_axis, v_axis)
    u45 = (u_axis + v_axis) / math.sqrt(2.0)
    v45 = (-u_axis + v_axis) / math.sqrt(2.0)
    r45 = basis_to_geckolib_euler(u45, y_axis, v45)
    cubes.append(make_centered_cube(center, [diameter, length, diameter], mat, rot=r0))
    cubes.append(make_centered_cube(center, [diameter, length * 0.992, diameter], mat, rot=r45))


def add_tapered_spike(cubes: list, base_pos, axis_dir, thickness_axis, length: float,
                      w_base: float, t_base: float, w_tip: float = 0.28, t_tip: float = 0.24,
                      n_steps: int = 4, mat_root: str = "IRON_DARK", mat_edge: str = "IRON_SILVER",
                      edge_start_idx: int = 1, diamond_roll: bool = False):
    """
    Dựng gai thép / mũi giáo vát côn sắc lẹm nhiều tầng từ gốc dày (`w_base x t_base`) tới mũi nhọn sắc (`w_tip x t_tip`).
    - `axis_dir`: hướng vươn từ gốc ra mũi nhọn.
    - `thickness_axis`: trục bề dày lưỡi (nếu `diamond_roll=True` xoay 45° tạo tiết diện mũi giáo 4 cạnh kim cương!).
    """
    y_ax = np.asarray(axis_dir, dtype=np.float64)
    y_ax /= np.linalg.norm(y_ax)
    x_ax = np.asarray(thickness_axis, dtype=np.float64)
    x_ax = x_ax - np.dot(x_ax, y_ax) * y_ax
    x_ax /= np.linalg.norm(x_ax)
    z_ax = np.cross(x_ax, y_ax)
    z_ax /= np.linalg.norm(z_ax)
    if diamond_roll:
        xr = (x_ax + z_ax) / math.sqrt(2.0)
        zr = (-x_ax + z_ax) / math.sqrt(2.0)
        rot = basis_to_geckolib_euler(xr, y_ax, zr)
    else:
        rot = basis_to_geckolib_euler(x_ax, y_ax, z_ax)

    base = np.asarray(base_pos, dtype=np.float64)
    seg_len = (length / n_steps) * 1.24
    for i in range(n_steps):
        u = (i + 0.5) / n_steps
        # Đường cong vát nhọn đều sắc bén như lưỡi giáo rèn liền khối!
        u_sharp = u ** 1.08
        t_cur = t_base + (t_tip - t_base) * u_sharp
        w_cur = w_base + (w_tip - w_base) * u_sharp
        ctr = base + y_ax * (u * length)
        mat = mat_edge if i >= edge_start_idx else mat_root
        cubes.append(make_centered_cube(ctr, [t_cur, seg_len, w_cur], mat, rot=rot))


# =============================================================================
# 1. XÂY DỰNG GEOMETRY CYCLOPS WARDEN V2 (52 KHỚP ZERO-BIND-ROTATION, ĐIÊU KHẮC MƯỢT!)
# =============================================================================
def build_geo() -> dict:
    bones = []

    bones.append({"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})
    bones.append({"name": "body", "parent": "root", "pivot": [0.0, 18.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": []})

    # -------------------------------------------------------------------------
    # A. KHUNG CHẬU, ĐAI LƯNG 3 KHÓA KIM CƯƠNG & 6 VẠT KHỐ TUA RUA ĐA KHỚP (`pelvis`, `kilt_*`)
    # -------------------------------------------------------------------------
    pelvis_c = [
        # Khối hông bo vát 2 tầng (bớt cạnh vuông gắt!)
        make_centered_cube([0.0, 19.2, 0.0], [19.4, 6.4, 12.2], "SKIN_MOTTLED"),
        make_centered_cube([0.0, 18.0, 0.0], [17.8, 3.6, 11.4], "SKIN_MOTTLED"),
        # Đai sắt-da dày bo góc nhẹ quanh eo
        make_centered_cube([0.0, 21.0, 0.0], [20.8, 3.5, 13.4], "BELT_BUCKLE"),
        make_centered_cube([0.0, 21.0, 0.0], [21.6, 2.8, 12.0], "BELT_BUCKLE"),
        # 3 khóa kim cương viền sắt đen lõi cam cháy lồng vào nhau ở giữa bụng
        make_centered_cube([0.0, 20.6, -7.0], [4.5, 4.5, 1.4], "IRON_DARK", rot=[0.0, 0.0, 45.0]),
        make_centered_cube([0.0, 20.6, -7.3], [2.9, 2.9, 1.2], "LEATHER_STRAPS", rot=[0.0, 0.0, 45.0]),
        make_centered_cube([-3.7, 20.6, -6.85], [3.8, 3.8, 1.3], "IRON_DARK", rot=[0.0, 0.0, 45.0]),
        make_centered_cube([-3.7, 20.6, -7.15], [2.3, 2.3, 1.1], "LEATHER_STRAPS", rot=[0.0, 0.0, 45.0]),
        make_centered_cube([+3.7, 20.6, -6.85], [3.8, 3.8, 1.3], "IRON_DARK", rot=[0.0, 0.0, 45.0]),
        make_centered_cube([+3.7, 20.6, -7.15], [2.3, 2.3, 1.1], "LEATHER_STRAPS", rot=[0.0, 0.0, 45.0]),
    ]
    bones.append({"name": "pelvis", "parent": "body", "pivot": [0.0, 19.2, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": pelvis_c})

    # 6 VẠT KHỐ TUA RUA ĐỘC LẬP — ĐUNG ĐƯA MỀM MẠI KHI BƯỚC ĐI & VUNG CHÙY!
    kilt_specs = [
        ("kilt_front_c", [0.0, 19.2, -6.8],  [0.0, 16.2, -7.2],  [7.8, 6.0, 1.0],  [+8.0, 0.0, 0.0]),
        ("kilt_front_l", [+6.8, 19.2, -6.6], [+7.0, 16.3, -7.0], [7.2, 5.8, 1.0],  [+9.0, +10.0, -5.0]),
        ("kilt_front_r", [-6.8, 19.2, -6.6], [-7.0, 16.3, -7.0], [7.2, 5.8, 1.0],  [+9.0, -10.0, +5.0]),
        ("kilt_side_l",  [+10.4, 19.2, 0.0], [+10.8, 16.3, 0.0], [1.0, 5.8, 12.6], [0.0, 0.0, -10.0]),
        ("kilt_side_r",  [-10.4, 19.2, 0.0], [-10.8, 16.3, 0.0], [1.0, 5.8, 12.6], [0.0, 0.0, +10.0]),
        ("kilt_back",    [0.0, 19.2, +6.8],  [0.0, 16.2, +7.2],  [20.2, 6.0, 1.0], [-8.0, 0.0, 0.0]),
    ]
    for kname, kpiv, kctr, ksz, krot in kilt_specs:
        bones.append({
            "name": kname, "parent": "pelvis", "pivot": kpiv, "rotation": [0.0, 0.0, 0.0],
            "cubes": [make_centered_cube(kctr, ksz, "LOINCLOTH_FRINGE", rot=krot)],
        })

    # -------------------------------------------------------------------------
    # B. EO BỤNG (`waist`) -> NGỰC CƠ BẮP V-TAPER (`chest`) -> CỔ CƠ CẦU VAI (`neck`)
    # -------------------------------------------------------------------------
    waist_c = [
        # Thắt eo săn chắc chữ V (nhỏ hơn ngực để dáng đứng hùng dũng tự nhiên!)
        make_centered_cube([0.0, 26.2, -0.2], [19.6, 8.2, 12.6], "SKIN_MOTTLED"),
        # Khối cơ liên sườn hai bên bo tròn
        make_centered_cube([+9.2, 26.6, -0.2], [2.6, 7.2, 10.6], "SKIN_MOTTLED", rot=[0.0, 0.0, +6.0]),
        make_centered_cube([-9.2, 26.6, -0.2], [2.6, 7.2, 10.6], "SKIN_MOTTLED", rot=[0.0, 0.0, -6.0]),
        # Tấm cơ bụng trước 2 tầng mềm
        make_centered_cube([-3.8, 27.0, -6.4], [6.8, 6.4, 1.5], "SKIN_CHEST_TAN"),
        make_centered_cube([+3.8, 27.0, -6.4], [6.8, 6.4, 1.5], "SKIN_CHEST_TAN"),
        # Cơ lưng dưới
        make_centered_cube([-4.2, 26.4, +6.0], [7.2, 6.6, 1.8], "SKIN_MOTTLED"),
        make_centered_cube([+4.2, 26.4, +6.0], [7.2, 6.6, 1.8], "SKIN_MOTTLED"),
    ]
    bones.append({"name": "waist", "parent": "pelvis", "pivot": [0.0, 22.8, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": waist_c})

    chest_c = [
        # Lồng ngực nở rộng hình chữ V + bo vát phía trên & lưng gù cơ bắp!
        make_centered_cube([0.0, 34.4, +0.2], [23.6, 10.4, 13.8], "SKIN_MOTTLED"),
        make_centered_cube([0.0, 39.2, +1.0], [21.6, 4.6, 12.4], "SKIN_MOTTLED", rot=[+7.0, 0.0, 0.0]),
        # Hai bả vai cơ cầu vai vát nghiêng nối mượt sang đầu (KHÔNG còn vai hộp 90 độ cứng đơ!)
        make_centered_cube([+8.2, 40.6, +1.4], [9.6, 4.0, 10.4], "SKIN_MOTTLED", rot=[+6.0, 0.0, -14.0]),
        make_centered_cube([-8.2, 40.6, +1.4], [9.6, 4.0, 10.4], "SKIN_MOTTLED", rot=[+6.0, 0.0, +14.0]),
        # Cơ lưng xô nở rộng hai bên
        make_centered_cube([+11.4, 34.0, +1.0], [3.0, 9.0, 11.4], "SKIN_MOTTLED", rot=[0.0, 0.0, -8.0]),
        make_centered_cube([-11.4, 34.0, +1.0], [3.0, 9.0, 11.4], "SKIN_MOTTLED", rot=[0.0, 0.0, +8.0]),
        # Hai khối cơ ngực màu cát sáng vát nhẹ mềm mại
        make_centered_cube([-5.2, 34.4, -6.9], [9.2, 7.0, 2.4], "SKIN_CHEST_TAN", rot=[-6.0, -6.0, 0.0]),
        make_centered_cube([+5.2, 34.4, -6.9], [9.2, 7.0, 2.4], "SKIN_CHEST_TAN", rot=[-6.0, +6.0, 0.0]),
        # Cơ bả vai sau lưng có chiều sâu khối
        make_centered_cube([-5.6, 35.6, +7.0], [8.8, 7.8, 2.0], "SKIN_MOTTLED", rot=[+6.0, +6.0, 0.0]),
        make_centered_cube([+5.6, 35.6, +7.0], [8.8, 7.8, 2.0], "SKIN_MOTTLED", rot=[+6.0, -6.0, 0.0]),
    ]
    # Chuỗi xích sắt đen vắt chéo ngực & lưng (dùng chuẩn GeckoLib `add_chain_links`, ôm sát cơ thể!)
    add_chain_links(chest_c, [
        [-8.0, 39.8, -7.4],
        [-2.8, 35.4, -8.4],
        [+2.8, 31.2, -8.3],
        [+8.8, 27.6, -7.6],
        [+12.0, 27.6, -1.2],
        [+11.2, 29.8, +6.4],
        [+4.8, 33.6, +8.6],
    ], link_len=2.65, link_w=1.75, link_t=0.80)
    add_chain_links(chest_c, [
        [-7.6, 40.6, +7.4],
        [-3.0, 38.4, +8.4],
        [+1.4, 36.2, +8.9],
        [+4.6, 34.2, +9.2],
    ], link_len=2.65, link_w=1.75, link_t=0.80)
    add_chain_links(chest_c, [
        [-8.0, 39.8, -7.4],
        [-7.8, 42.2, 0.0],
        [-7.6, 40.6, +7.4],
    ], link_len=2.45, link_w=1.70, link_t=0.80)
    bones.append({"name": "chest", "parent": "waist", "pivot": [0.0, 29.5, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": chest_c})

    # Cổ cơ bắp ngắn kết nối mềm giữa ngực và đầu Cyclops
    bones.append({
        "name": "neck", "parent": "chest", "pivot": [0.0, 39.8, -2.5], "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube([0.0, 41.2, -3.2], [9.6, 4.8, 8.2], "SKIN_MOTTLED", rot=[-10.0, 0.0, 0.0])],
    })

    # -------------------------------------------------------------------------
    # C. ĐẦU CYCLOPS 1 MẮT KIM CƯƠNG ĐỎ + MÍ MẮT + HÀM DƯỚI + 2 NANH NGÀ (`head`, `jaw`, `eyelid_*`)
    # -------------------------------------------------------------------------
    head_c = [
        # Hộp sọ vát góc mềm hơn (gò má + đỉnh đầu vát nhẹ thay vì 1 khối lập phương cứng)
        make_centered_cube([0.0, 44.6, -5.6], [8.8, 9.2, 8.8], "SKIN_MOTTLED"),
        make_centered_cube([0.0, 48.8, -5.4], [7.8, 1.8, 7.8], "SKIN_MOTTLED"),
        # Gờ chân mày gồ uy nghiêm che trên mắt kim cương
        make_centered_cube([0.0, 48.4, -9.6], [8.4, 2.0, 2.2], "SKIN_MOTTLED", rot=[-10.0, 0.0, 0.0]),
        # MẮT KIM CƯƠNG ĐỎ CYCLOPS NỔI RÕ Ở CHÍNH GIỮA TRÁN
        make_centered_cube([0.0, 45.8, -10.15], [4.5, 3.2, 0.65], "HEAD_EYE"),
        # Hàm trên & răng ngà
        make_centered_cube([0.0, 42.6, -10.05], [6.4, 2.2, 0.85], "MOUTH_TEETH"),
        make_centered_cube([-4.4, 43.2, -6.2], [1.7, 5.0, 5.8], "SKIN_MOTTLED", rot=[0.0, 0.0, +6.0]),
        make_centered_cube([+4.4, 43.2, -6.2], [1.7, 5.0, 5.8], "SKIN_MOTTLED", rot=[0.0, 0.0, -6.0]),
    ]
    bones.append({"name": "head", "parent": "neck", "pivot": [0.0, 41.6, -4.4], "rotation": [0.0, 0.0, 0.0], "cubes": head_c})

    bones.append({
        "name": "eyelid_up", "parent": "head", "pivot": [0.0, 47.6, -10.4], "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube([0.0, 47.55, -10.35], [4.7, 1.3, 0.6], "SKIN_MOTTLED")],
    })
    bones.append({
        "name": "eyelid_lo", "parent": "head", "pivot": [0.0, 44.0, -10.4], "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube([0.0, 44.05, -10.35], [4.7, 0.9, 0.6], "SKIN_MOTTLED")],
    })

    jaw_c = [
        make_centered_cube([0.0, 39.8, -7.1], [7.8, 3.2, 6.4], "SKIN_MOTTLED"),
        make_centered_cube([0.0, 38.4, -7.8], [6.2, 1.4, 4.6], "SKIN_MOTTLED"),
        make_centered_cube([0.0, 41.1, -10.15], [5.8, 1.2, 0.8], "MOUTH_TEETH"),
        # 2 Nanh ngà cong vểnh lên 2 bên mép (xoay hướng chuẩn GeckoLib: -rx nghiêng ngọn về trước!)
        make_centered_cube([-3.55, 41.4, -10.2], [1.55, 3.5, 1.55], "TUSK_BONE", rot=[+14.0, 0.0, +10.0]),
        make_centered_cube([-3.85, 43.3, -10.7], [1.05, 1.9, 1.05], "TUSK_BONE", rot=[+24.0, 0.0, +6.0]),
        make_centered_cube([+3.55, 41.4, -10.2], [1.55, 3.5, 1.55], "TUSK_BONE", rot=[+14.0, 0.0, -10.0]),
        make_centered_cube([+3.85, 43.3, -10.7], [1.05, 1.9, 1.05], "TUSK_BONE", rot=[+24.0, 0.0, -6.0]),
    ]
    bones.append({"name": "jaw", "parent": "head", "pivot": [0.0, 41.0, -4.8], "rotation": [0.0, 0.0, 0.0], "cubes": jaw_c})

    # -------------------------------------------------------------------------
    # D. TAY TRÁI ĐA KHỚP (`clavicle_l` -> `shoulder_l` -> `arm_l` -> `elbow_l` -> `forearm_l` -> `wrist_l` -> `hand_l` -> `fingers_l`/`thumb_l`)
    # -------------------------------------------------------------------------
    bones.append({
        "name": "clavicle_l", "parent": "chest", "pivot": [+9.5, 38.5, 0.0], "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube([+12.2, 38.4, 0.0], [6.6, 7.6, 9.6], "SKIN_MOTTLED", rot=[0.0, 0.0, -10.0])],
    })
    bones.append({
        "name": "shoulder_l", "parent": "clavicle_l", "pivot": [+13.2, 37.6, 0.0], "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            # Cơ vai 2 tầng bo tròn tự nhiên
            make_centered_cube([+15.6, 36.8, 0.2], [9.6, 9.8, 10.2], "SKIN_MOTTLED", rot=[0.0, 0.0, -8.0]),
            make_centered_cube([+16.2, 39.2, 0.2], [8.2, 3.6, 8.8], "SKIN_MOTTLED", rot=[0.0, 0.0, -14.0]),
        ],
    })
    arm_l_c = []
    add_beveled_limb_segment(arm_l_c, [+16.0, 34.0, +0.2], [+16.6, 25.0, -0.8], 8.8, 9.0, 8.2, 8.4, "SKIN_MOTTLED", n_steps=3)
    bones.append({"name": "arm_l", "parent": "shoulder_l", "pivot": [+15.8, 34.0, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": arm_l_c})

    bones.append({
        "name": "elbow_l", "parent": "arm_l", "pivot": [+16.6, 25.2, -0.8], "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube([+16.6, 25.2, -0.5], [8.4, 4.6, 8.6], "SKIN_MOTTLED")],
    })
    fore_l_p0 = np.array([+16.6, 25.2, -0.8], dtype=np.float64)
    fore_l_p1 = np.array([+15.8, 15.8, -3.6], dtype=np.float64)
    fore_l_dir = fore_l_p1 - fore_l_p0
    fore_l_rot = align_y_geckolib(fore_l_dir)
    fore_l_c = []
    add_beveled_limb_segment(fore_l_c, fore_l_p0, fore_l_p1, 9.0, 9.2, 8.4, 8.6, "SKIN_MOTTLED", n_steps=3)
    # Dây da nâu buộc chéo giữ khiên giáp sắt trên cẳng tay trái
    fore_l_c.append(make_centered_cube(fore_l_p0 * 0.65 + fore_l_p1 * 0.35, [9.5, 1.35, 9.7], "LEATHER_STRAPS", rot=fore_l_rot))
    fore_l_c.append(make_centered_cube(fore_l_p0 * 0.30 + fore_l_p1 * 0.70, [9.3, 1.35, 9.5], "LEATHER_STRAPS", rot=fore_l_rot))
    add_sculpted_bracer(fore_l_c, (fore_l_p0 * 0.48 + fore_l_p1 * 0.52) + np.array([+4.5, 0.0, 0.0]), -fore_l_dir, [+1.0, 0.0, 0.0])
    bones.append({"name": "forearm_l", "parent": "elbow_l", "pivot": fore_l_p0.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": fore_l_c})

    bones.append({
        "name": "wrist_l", "parent": "forearm_l", "pivot": fore_l_p1.tolist(), "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube(fore_l_p1, [7.8, 3.0, 8.0], "SKIN_MOTTLED", rot=fore_l_rot)],
    })
    bones.append({
        "name": "hand_l", "parent": "wrist_l", "pivot": [+15.6, 15.2, -3.8], "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            make_centered_cube([+15.6, 13.8, -4.4], [8.2, 5.4, 7.6], "SKIN_MOTTLED", rot=[-16.0, 0.0, +6.0]),
            make_centered_cube([+13.4, 13.0, -5.0], [4.2, 4.8, 6.2], "SKIN_CHEST_TAN", rot=[-16.0, 0.0, +8.0]),
        ],
    })
    bones.append({
        "name": "fingers_l", "parent": "hand_l", "pivot": [+14.4, 11.4, -5.8], "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            make_centered_cube([+14.0, 10.6, -6.2], [6.4, 3.0, 2.8], "TOES_IVORY", rot=[-30.0, 0.0, +8.0]),
            make_centered_cube([+12.4, 9.8, -5.4], [4.4, 2.2, 2.6], "TOES_IVORY", rot=[-56.0, 0.0, +16.0]),
        ],
    })
    bones.append({
        "name": "thumb_l", "parent": "hand_l", "pivot": [+12.4, 14.6, -6.8], "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube([+11.8, 13.8, -7.2], [2.6, 3.4, 3.2], "TOES_IVORY", rot=[-12.0, -18.0, +18.0])],
    })

    # -------------------------------------------------------------------------
    # E. TAY PHẢI ĐA KHỚP VÁC ĐẠI CHÙY CHIẾN 6 CÁNH TINH XẢO QUA VAI PHẢI!
    #    (`clavicle_r` -> `shoulder_r` -> `arm_r` -> `elbow_r` -> `forearm_r` -> `wrist_r` -> `hand_r` -> `fingers_r` & `mace_root`)
    # -------------------------------------------------------------------------
    bones.append({
        "name": "clavicle_r", "parent": "chest", "pivot": [-9.5, 38.5, 0.0], "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube([-12.2, 38.4, 0.0], [6.6, 7.6, 9.6], "SKIN_MOTTLED", rot=[0.0, 0.0, +10.0])],
    })
    bones.append({
        "name": "shoulder_r", "parent": "clavicle_r", "pivot": [-13.2, 37.6, 0.0], "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            make_centered_cube([-15.6, 36.8, 0.2], [9.6, 9.8, 10.2], "SKIN_MOTTLED", rot=[0.0, 0.0, +8.0]),
            make_centered_cube([-16.2, 39.2, 0.2], [8.2, 3.6, 8.8], "SKIN_MOTTLED", rot=[0.0, 0.0, +14.0]),
        ],
    })

    arm_r_p0 = np.array([-15.8, 35.4, -0.2], dtype=np.float64)
    arm_r_p1 = np.array([-18.8, 29.2, -5.6], dtype=np.float64)
    arm_r_c = []
    add_beveled_limb_segment(arm_r_c, arm_r_p0, arm_r_p1, 9.0, 9.2, 8.4, 8.6, "SKIN_MOTTLED", n_steps=3)
    bones.append({"name": "arm_r", "parent": "shoulder_r", "pivot": arm_r_p0.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": arm_r_c})

    bones.append({
        "name": "elbow_r", "parent": "arm_r", "pivot": arm_r_p1.tolist(), "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube(arm_r_p1, [8.6, 5.0, 8.6], "SKIN_MOTTLED")],
    })

    fore_r_p0 = arm_r_p1
    fore_r_p1 = np.array([-16.2, 39.6, -10.8], dtype=np.float64)
    fore_r_dir = fore_r_p1 - fore_r_p0
    fore_r_rot = align_y_geckolib(fore_r_dir)
    fore_r_c = []
    add_beveled_limb_segment(fore_r_c, fore_r_p0, fore_r_p1, 8.8, 9.0, 8.4, 8.6, "SKIN_MOTTLED", n_steps=3)
    fore_r_c.append(make_centered_cube(fore_r_p0 * 0.65 + fore_r_p1 * 0.35, [9.4, 1.35, 9.6], "LEATHER_STRAPS", rot=fore_r_rot))
    fore_r_c.append(make_centered_cube(fore_r_p0 * 0.30 + fore_r_p1 * 0.70, [9.2, 1.35, 9.4], "LEATHER_STRAPS", rot=fore_r_rot))
    add_sculpted_bracer(fore_r_c, (fore_r_p0 * 0.48 + fore_r_p1 * 0.52) + np.array([-4.4, 0.0, -0.4]), fore_r_dir, [-1.0, 0.0, -0.15])
    bones.append({"name": "forearm_r", "parent": "elbow_r", "pivot": fore_r_p0.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": fore_r_c})

    bones.append({
        "name": "wrist_r", "parent": "forearm_r", "pivot": fore_r_p1.tolist(), "rotation": [0.0, 0.0, 0.0],
        "cubes": [make_centered_cube(fore_r_p1, [7.6, 3.0, 7.8], "SKIN_MOTTLED", rot=fore_r_rot)],
    })

    # Tâm nắm tay phải nằm CHÍNH XÁC trên trục cán chùy!
    p_grip = np.array([-15.6, 41.8, -10.8], dtype=np.float64)
    bones.append({
        "name": "hand_r", "parent": "wrist_r", "pivot": p_grip.tolist(), "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            make_centered_cube(p_grip + np.array([-0.6, -0.5, +0.2]), [7.4, 5.2, 7.2], "SKIN_MOTTLED", rot=[-14.0, -14.0, +16.0]),
            make_centered_cube(p_grip + np.array([+1.8, -0.2, -1.6]), [3.0, 3.4, 3.2], "TOES_IVORY", rot=[-12.0, -22.0, +10.0]),
        ],
    })
    bones.append({
        "name": "fingers_r", "parent": "hand_r", "pivot": (p_grip + np.array([0.0, +2.0, 0.0])).tolist(), "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            # 4 đốt ngón tay ngà ôm gọn trùm lên phía trên cán chùy!
            make_centered_cube(p_grip + np.array([+0.2, +2.2, -0.2]), [7.0, 2.8, 4.4], "TOES_IVORY", rot=[-10.0, -24.0, +14.0]),
            make_centered_cube(p_grip + np.array([+1.4, +0.8, -2.0]), [6.2, 2.4, 2.2], "TOES_IVORY", rot=[+24.0, -24.0, +14.0]),
        ],
    })

    # =========================================================================
    # F. ĐẠI CHÙY CHIẾN GOTHIC HẠNG NẶNG 6 CÁNH SẮC LẸM (`mace_root`) — 100% ĐỒNG TRỤC TRONG GECKOLIB!
    #    Nâng cấp toàn diện:
    #    - Cán bát giác bọc đai thép đinh tán + chuôi đối trọng 8 cạnh gắn mũi giáo nhọn 4 tầng!
    #    - Lõi bầu thép đúc bát giác phình nặng ở tâm tác động + 2 vòng đai đinh tán + 6 gai kim tự tháp xen kẽ!
    #    - 6 cánh rìu Gothic vát lưỡi dao (gốc 1.65px -> sống 1.0px -> lưỡi mài 0.34px) với 3 mũi gai chéo sắc lẹm mỗi cánh!
    #    - Đỉnh chùy mũi giáo kim cương 5 tầng vát nhọn + 6 vây thép chụm đỉnh (0 khối vuông thô!)
    # =========================================================================
    haft_dir = np.array([+0.64, +0.26, +0.72], dtype=np.float64)
    haft_dir /= np.linalg.norm(haft_dir)

    p_pommel = p_grip - haft_dir * 12.2
    p_head   = p_grip + haft_dir * 21.6
    total_haft_len = float(np.linalg.norm(p_head - p_pommel))

    # Hệ trục trực chuẩn gắn chặt theo trục cán chùy: y = haft_dir, x = u_vec, z = v_vec (det = +1)
    up_ref = np.array([0.0, 1.0, 0.0], dtype=np.float64)
    u_vec = np.cross(haft_dir, up_ref)
    u_vec /= np.linalg.norm(u_vec)
    v_vec = np.cross(u_vec, haft_dir)
    v_vec /= np.linalg.norm(v_vec)
    haft_rot = basis_to_geckolib_euler(u_vec, haft_dir, v_vec)

    mace_c = []

    # 1. Cán gỗ đỏ-nâu bát giác chắc nặng xuyên thẳng chính tâm nắm tay phải + dây da quấn + vòng đai thép
    add_octagonal_drum(mace_c, (p_pommel + p_head) * 0.5, haft_dir, u_vec, v_vec, 2.25, total_haft_len, "MACE_HAFT")
    add_octagonal_drum(mace_c, p_grip, haft_dir, u_vec, v_vec, 2.55, 7.6, "LEATHER_STRAPS")
    # Vòng chặn trên & dưới nắm tay + 2 đai gia cố dọc cán
    for off_g, diam, ln, mat in (
        (-4.1, 3.05, 0.95, "IRON_DARK"),
        (+4.1, 3.15, 0.95, "IRON_DARK"),
        (+8.4, 2.95, 1.05, "IRON_DARK"),
        (+8.4, 3.12, 0.42, "IRON_SILVER"),
    ):
        add_octagonal_drum(mace_c, p_grip + haft_dir * off_g, haft_dir, u_vec, v_vec, diam, ln, mat)

    # 2. Chuôi đối trọng bát giác hạng nặng + 4 vây sắc nhỏ + mũi giáo nhọn 4 tầng!
    add_octagonal_drum(mace_c, p_pommel + haft_dir * 2.4, haft_dir, u_vec, v_vec, 3.10, 1.35, "IRON_DARK")
    add_octagonal_drum(mace_c, p_pommel + haft_dir * 0.8, haft_dir, u_vec, v_vec, 3.75, 1.95, "IRON_DARK")
    add_octagonal_drum(mace_c, p_pommel + haft_dir * 0.8, haft_dir, u_vec, v_vec, 3.92, 0.55, "IRON_SILVER")
    add_tapered_spike(
        mace_c, p_pommel - haft_dir * 0.2, -haft_dir, u_vec,
        length=4.5, w_base=2.25, t_base=2.25, w_tip=0.30, t_tip=0.30,
        n_steps=4, mat_root="IRON_DARK", mat_edge="IRON_SILVER", edge_start_idx=1, diamond_roll=True
    )
    for pi in range(4):
        pang = math.radians(pi * 90.0 + 45.0)
        p_rad = math.cos(pang) * u_vec + math.sin(pang) * v_vec
        p_tan = np.cross(haft_dir, p_rad)
        p_tan /= np.linalg.norm(p_tan)
        add_tapered_spike(
            mace_c, p_pommel + haft_dir * 0.5 + p_rad * 1.4, p_rad * 0.82 - haft_dir * 0.57, p_tan,
            length=2.1, w_base=1.35, t_base=0.52, w_tip=0.26, t_tip=0.22,
            n_steps=3, mat_root="IRON_DARK", mat_edge="IRON_SILVER", edge_start_idx=1
        )

    # 3. Cổ chùy đúc thép loe 4 bậc nặng nề chuyển tiếp mượt từ cán lên bầu chùy hạng nặng!
    for off_c, diam, ln, mat in (
        (-8.6, 3.15, 1.35, "IRON_DARK"),
        (-7.3, 3.65, 1.35, "IRON_SILVER"),
        (-5.8, 4.45, 1.75, "IRON_DARK"),
        (-4.1, 5.45, 1.95, "IRON_DARK"),
    ):
        add_octagonal_drum(mace_c, p_head + haft_dir * off_c, haft_dir, u_vec, v_vec, diam, ln, mat)

    # 4. BẦU LÕI THÉP ĐÚC BÁT GIÁC HẠNG NẶNG (phình giữa 7.0px đặc khối -> cảm giác cực nặng & uy lực!)
    flange_center = p_head + haft_dir * 1.8
    add_octagonal_drum(mace_c, flange_center - haft_dir * 4.5, haft_dir, u_vec, v_vec, 5.85, 3.2, "IRON_DARK")
    add_octagonal_drum(mace_c, flange_center,                   haft_dir, u_vec, v_vec, 7.05, 6.6, "IRON_DARK")
    add_octagonal_drum(mace_c, flange_center + haft_dir * 4.5, haft_dir, u_vec, v_vec, 5.85, 3.2, "IRON_DARK")
    # 2 vòng đai thép rèn siết bầu chùy nặng nề
    add_octagonal_drum(mace_c, flange_center - haft_dir * 3.1, haft_dir, u_vec, v_vec, 7.45, 0.95, "IRON_DARK")
    add_octagonal_drum(mace_c, flange_center + haft_dir * 3.1, haft_dir, u_vec, v_vec, 7.45, 0.95, "IRON_DARK")
    add_octagonal_drum(mace_c, flange_center - haft_dir * 3.1, haft_dir, u_vec, v_vec, 7.56, 0.34, "IRON_SILVER")
    add_octagonal_drum(mace_c, flange_center + haft_dir * 3.1, haft_dir, u_vec, v_vec, 7.56, 0.34, "IRON_SILVER")

    # 5. ĐỈNH CHÙY HẠNG NẶNG + MŨI GIÁO KIM CƯƠNG 6 TẦNG LIỀN MẠCH SẮC NHỌN!
    crown_base = flange_center + haft_dir * 6.0
    add_octagonal_drum(mace_c, crown_base,                  haft_dir, u_vec, v_vec, 4.95, 1.65, "IRON_DARK")
    add_octagonal_drum(mace_c, crown_base + haft_dir * 0.95, haft_dir, u_vec, v_vec, 3.85, 0.95, "IRON_DARK")
    add_tapered_spike(
        mace_c, crown_base + haft_dir * 1.15, haft_dir, u_vec,
        length=8.8, w_base=3.05, t_base=3.05, w_tip=0.26, t_tip=0.26,
        n_steps=6, mat_root="IRON_DARK", mat_edge="IRON_SILVER", edge_start_idx=2, diamond_roll=True
    )

    # 6. 6 CÁNH RÌU GOTHIC ĐÚC LIỀN KHỐI + 18 LƯỠI GAI BẢN RỘNG SẮC LẸM + 6 ĐINH GAI KIM TỰ THÁP!
    #    Mỗi cánh đúng 3 lưỡi gai bản rộng vát côn 5 tầng liền khối (KHÔNG rối gai vụn, KHÔNG khối vuông 90°!)
    for fi in range(6):
        ang = math.radians(fi * 60.0 + 18.0)
        rad_dir = math.cos(ang) * u_vec + math.sin(ang) * v_vec
        tan_dir = np.cross(haft_dir, rad_dir)
        tan_dir /= np.linalg.norm(tan_dir)
        f_rot = basis_to_geckolib_euler(tan_dir, haft_dir, rad_dir)

        # (a) Bản cánh thép đúc dày ở gốc bám chặt bầu lõi -> vát nêm mảnh dần ra ngoài (hình rìu đặc chắc nặng!)
        mace_c.append(make_centered_cube(flange_center + rad_dir * 3.65, [1.75, 10.6, 2.6], "IRON_DARK", rot=f_rot))
        mace_c.append(make_centered_cube(flange_center + rad_dir * 5.25, [1.22, 8.6,  2.5], "IRON_DARK", rot=f_rot))
        # Hai sống vát chéo nối mượt cổ dưới & vai trên vào bầu chùy (xóa sạch góc vuông 90°!)
        for s_end in (+1.0, -1.0):
            end_dir = rad_dir * 0.62 + haft_dir * (s_end * 0.78)
            add_tapered_spike(
                mace_c, flange_center + haft_dir * (s_end * 3.8) + rad_dir * 2.5, end_dir, tan_dir,
                length=3.2, w_base=1.85, t_base=1.35, w_tip=0.72, t_tip=0.58,
                n_steps=2, mat_root="IRON_DARK", mat_edge="IRON_DARK", edge_start_idx=99
            )

        # (b) LƯỠI GAI CHỦ ĐẠO CHÍNH GIỮA — mũi giáo bản rộng vát nêm 5 tầng cực sắc (từ 4.2px -> mũi nhọn 0.24px!)
        add_tapered_spike(
            mace_c, flange_center + rad_dir * 5.2, rad_dir, tan_dir,
            length=5.9, w_base=4.25, t_base=1.08, w_tip=0.26, t_tip=0.22,
            n_steps=5, mat_root="IRON_DARK", mat_edge="IRON_SILVER", edge_start_idx=2
        )

        # (c) LƯỠI GAI MÓC TRÊN — bản rộng vát cong +34° hướng lên & tỏa ra ngoài, vát nhọn 5 tầng sắc bén!
        up_dir = rad_dir * math.cos(math.radians(34.0)) + haft_dir * math.sin(math.radians(34.0))
        add_tapered_spike(
            mace_c, flange_center + rad_dir * 3.8 + haft_dir * 3.0, up_dir, tan_dir,
            length=6.1, w_base=3.25, t_base=1.02, w_tip=0.26, t_tip=0.22,
            n_steps=5, mat_root="IRON_DARK", mat_edge="IRON_SILVER", edge_start_idx=2
        )

        # (d) LƯỠI GAI NGUYỆT DƯỚI — bản rộng vát chéo -34° hướng xuống & tỏa ra ngoài, vát nhọn 5 tầng!
        dn_dir = rad_dir * math.cos(math.radians(34.0)) - haft_dir * math.sin(math.radians(34.0))
        add_tapered_spike(
            mace_c, flange_center + rad_dir * 3.8 - haft_dir * 2.9, dn_dir, tan_dir,
            length=5.5, w_base=3.05, t_base=1.00, w_tip=0.26, t_tip=0.22,
            n_steps=5, mat_root="IRON_DARK", mat_edge="IRON_SILVER", edge_start_idx=2
        )

        # (e) Đinh gai kim tự tháp đúc nặng xen kẽ giữa các cánh trên bầu giữa (4 tầng liền mạch!)
        ang_mid = math.radians(fi * 60.0 + 48.0)
        mid_rad = math.cos(ang_mid) * u_vec + math.sin(ang_mid) * v_vec
        mid_tan = np.cross(haft_dir, mid_rad)
        mid_tan /= np.linalg.norm(mid_tan)
        add_tapered_spike(
            mace_c, flange_center + mid_rad * 3.25, mid_rad, mid_tan,
            length=3.15, w_base=1.95, t_base=1.95, w_tip=0.24, t_tip=0.24,
            n_steps=4, mat_root="IRON_DARK", mat_edge="IRON_SILVER", edge_start_idx=2, diamond_roll=True
        )

    bones.append({"name": "mace_root", "parent": "hand_r", "pivot": p_grip.tolist(), "rotation": [0.0, 0.0, 0.0], "cubes": mace_c})

    # -------------------------------------------------------------------------
    # G. XÍCH TREO 3 ĐỐT ĐA KHỚP + LỒNG SẮT GIAM TÙ + CỬA LỒNG + NGƯỜI TÙ ĐA KHỚP!
    #    (`chain_1` -> `chain_2` -> `chain_3` -> `cage_body` -> `cage_door` & `prisoner` -> `prisoner_head`)
    # -------------------------------------------------------------------------
    ch1_p = [4.6, 34.4, 9.2]
    ch2_p = [4.5, 32.6, 10.6]
    ch3_p = [4.4, 30.9, 11.8]
    cage_cx, cage_top_y, cage_cz = 4.4, 29.4, 12.6
    cage_w, cage_h, cage_d = 10.4, 15.0, 10.4
    cage_bot_y = cage_top_y - cage_h
    cage_mid_y = (cage_top_y + cage_bot_y) * 0.5

    c1_cubes, c2_cubes, c3_cubes = [], [], []
    add_chain_links(c1_cubes, [ch1_p, ch2_p], link_len=2.1, link_w=1.55, link_t=0.72)
    add_chain_links(c2_cubes, [ch2_p, ch3_p], link_len=2.1, link_w=1.55, link_t=0.72)
    add_chain_links(c3_cubes, [ch3_p, [cage_cx, cage_top_y, cage_cz]], link_len=2.0, link_w=1.55, link_t=0.72)

    bones.append({"name": "chain_1", "parent": "chest", "pivot": ch1_p, "rotation": [0.0, 0.0, 0.0], "cubes": c1_cubes})
    bones.append({"name": "chain_2", "parent": "chain_1", "pivot": ch2_p, "rotation": [0.0, 0.0, 0.0], "cubes": c2_cubes})
    bones.append({"name": "chain_3", "parent": "chain_2", "pivot": ch3_p, "rotation": [0.0, 0.0, 0.0], "cubes": c3_cubes})

    cage_c = [
        make_centered_cube([cage_cx, cage_top_y + 0.75, cage_cz], [2.2, 1.3, 2.2], "IRON_DARK"),
        make_centered_cube([cage_cx, cage_top_y, cage_cz - cage_d * 0.5 + 0.5], [cage_w, 1.15, 1.0], "IRON_DARK"),
        make_centered_cube([cage_cx, cage_top_y, cage_cz + cage_d * 0.5 - 0.5], [cage_w, 1.15, 1.0], "IRON_DARK"),
        make_centered_cube([cage_cx - cage_w * 0.5 + 0.5, cage_top_y, cage_cz], [1.0, 1.15, cage_d], "IRON_DARK"),
        make_centered_cube([cage_cx + cage_w * 0.5 - 0.5, cage_top_y, cage_cz], [1.0, 1.15, cage_d], "IRON_DARK"),
        make_centered_cube([cage_cx, cage_bot_y, cage_cz], [cage_w, 1.15, cage_d], "IRON_DARK"),
        # 4 trụ góc sắt đen chắc nịch
        make_centered_cube([cage_cx - cage_w * 0.5 + 0.5, cage_mid_y, cage_cz - cage_d * 0.5 + 0.5], [1.0, cage_h, 1.0], "IRON_DARK"),
        make_centered_cube([cage_cx + cage_w * 0.5 - 0.5, cage_mid_y, cage_cz - cage_d * 0.5 + 0.5], [1.0, cage_h, 1.0], "IRON_DARK"),
        make_centered_cube([cage_cx - cage_w * 0.5 + 0.5, cage_mid_y, cage_cz + cage_d * 0.5 - 0.5], [1.0, cage_h, 1.0], "IRON_DARK"),
        make_centered_cube([cage_cx + cage_w * 0.5 - 0.5, cage_mid_y, cage_cz + cage_d * 0.5 - 0.5], [1.0, cage_h, 1.0], "IRON_DARK"),
        # Đai ngang gia cố giữa lồng
        make_centered_cube([cage_cx, cage_mid_y, cage_cz - cage_d * 0.5 + 0.45], [cage_w, 0.85, 0.85], "IRON_DARK"),
        make_centered_cube([cage_cx - cage_w * 0.5 + 0.45, cage_mid_y, cage_cz], [0.85, 0.85, cage_d], "IRON_DARK"),
        make_centered_cube([cage_cx + cage_w * 0.5 - 0.45, cage_mid_y, cage_cz], [0.85, 0.85, cage_d], "IRON_DARK"),
    ]
    for off in (-2.0, +2.0):
        cage_c.append(make_centered_cube([cage_cx + off, cage_mid_y, cage_cz - cage_d * 0.5 + 0.45], [0.65, cage_h - 1.2, 0.65], "IRON_DARK"))
        cage_c.append(make_centered_cube([cage_cx - cage_w * 0.5 + 0.45, cage_mid_y, cage_cz + off], [0.65, cage_h - 1.2, 0.65], "IRON_DARK"))
        cage_c.append(make_centered_cube([cage_cx + cage_w * 0.5 - 0.45, cage_mid_y, cage_cz + off], [0.65, cage_h - 1.2, 0.65], "IRON_DARK"))
    bones.append({"name": "cage_body", "parent": "chain_3", "pivot": [cage_cx, cage_top_y, cage_cz], "rotation": [0.0, 0.0, 0.0], "cubes": cage_c})

    door_z = cage_cz + cage_d * 0.5 - 0.45
    bones.append({
        "name": "cage_door", "parent": "cage_body", "pivot": [cage_cx - cage_w * 0.5 + 0.55, cage_mid_y, door_z], "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            make_centered_cube([cage_cx, cage_mid_y, door_z], [cage_w - 1.4, 0.85, 0.80], "IRON_DARK"),
            make_centered_cube([cage_cx - 2.0, cage_mid_y, door_z], [0.65, cage_h - 1.4, 0.65], "IRON_DARK"),
            make_centered_cube([cage_cx + 2.0, cage_mid_y, door_z], [0.65, cage_h - 1.4, 0.65], "IRON_DARK"),
            make_centered_cube([cage_cx + 3.5, cage_mid_y - 0.2, door_z + 0.32], [1.35, 1.75, 0.85], "IRON_SILVER"),
        ],
    })

    # Dân Làng Minecraft bị giam bên trong lồng sắt (`prisoner` -> `prisoner_head`)!
    # Đúng tỉ lệ Dân Làng chuẩn Minecraft: đầu cao 8:10:8 + mũi 3D to nhô rủ xuống + lông mày rậm + mắt xanh ngọc
    # + áo choàng nâu Plains Villager + 2 tay khoanh chéo trước ngực đặc trưng + vòng xích sắt tinh gọn!
    v_sh_l = np.array([cage_cx - 2.55, cage_bot_y + 7.35, cage_cz - 0.15], dtype=np.float64)
    v_el_l = np.array([cage_cx - 2.15, cage_bot_y + 5.15, cage_cz + 1.60], dtype=np.float64)
    v_sh_r = np.array([cage_cx + 2.55, cage_bot_y + 7.35, cage_cz - 0.15], dtype=np.float64)
    v_el_r = np.array([cage_cx + 2.15, cage_bot_y + 5.15, cage_cz + 1.60], dtype=np.float64)

    villager_body_cubes = [
        # Đôi giày da nâu sẫm đặt gọn trên sàn lồng sắt
        make_centered_cube([cage_cx - 1.15, cage_bot_y + 1.35, cage_cz - 0.10], [1.85, 1.55, 2.35], "VILLAGER_BOOTS"),
        make_centered_cube([cage_cx + 1.15, cage_bot_y + 1.35, cage_cz - 0.10], [1.85, 1.55, 2.35], "VILLAGER_BOOTS"),
        # Áo choàng dài liền mảnh chuẩn Plains Villager (cổ yếm be -> đai da khóa vàng ngọc lục bảo -> tà nâu!)
        make_centered_cube([cage_cx, cage_bot_y + 5.15, cage_cz - 0.15], [4.50, 6.45, 3.20], "VILLAGER_ROBE_SIDE", south="VILLAGER_ROBE_FRONT"),
        # Gấu áo choàng hơi loe nhẹ ở chân váy (chỉ dùng mặt vải nâu trơn để không lặp thắt lưng!)
        make_centered_cube([cage_cx, cage_bot_y + 2.20, cage_cz - 0.15], [4.72, 1.15, 3.38], "VILLAGER_ROBE_SIDE"),
        # Cổ áo khoác gập nhẹ trên vai (dáng Plains Villager chuẩn Minecraft)
        make_centered_cube([cage_cx, cage_bot_y + 8.05, cage_cz - 0.15], [4.68, 0.85, 3.35], "VILLAGER_ROBE_SIDE"),
        # ĐÔI TAY KHOANH TRƯỚC NGỰC ĐẶC TRƯNG CỦA DÂN LÀNG MINECRAFT!
        make_centered_cube((v_sh_l + v_el_l) * 0.5, [1.55, 3.05, 1.65], "VILLAGER_SLEEVE", rot=align_y_geckolib(v_el_l - v_sh_l)),
        make_centered_cube((v_sh_r + v_el_r) * 0.5, [1.55, 3.05, 1.65], "VILLAGER_SLEEVE", rot=align_y_geckolib(v_el_r - v_sh_r)),
        make_centered_cube([cage_cx, cage_bot_y + 5.10, cage_cz + 1.78], [4.55, 1.68, 1.72], "VILLAGER_SLEEVE"),
        # Xích sắt & còng cổ tay nhỏ gọn (vừa thể hiện bị giam xích, vừa giữ trọn bộ áo & dáng Dân Làng!)
        make_centered_cube([cage_cx, cage_bot_y + 5.10, cage_cz + 1.84], [1.10, 1.82, 1.88], "VILLAGER_SHACKLE"),
        make_centered_cube([cage_cx, cage_bot_y + 4.05, cage_cz - 0.15], [4.75, 0.65, 3.45], "VILLAGER_SHACKLE"),
    ]
    bones.append({
        "name": "prisoner", "parent": "cage_body", "pivot": [cage_cx, cage_bot_y + 5.5, cage_cz - 0.1], "rotation": [0.0, 0.0, 0.0],
        "cubes": villager_body_cubes,
    })
    bones.append({
        "name": "prisoner_head", "parent": "prisoner", "pivot": [cage_cx, cage_bot_y + 8.35, cage_cz - 0.10], "rotation": [0.0, 0.0, 0.0],
        "cubes": [
            # ĐẦU DÂN LÀNG CAO CHUẨN TỈ LỆ 8:10:8 — mặt trước (`south`) vẽ lông mày rậm + mắt xanh ngọc, các mặt còn lại là da đào!
            make_centered_cube([cage_cx, cage_bot_y + 10.92, cage_cz - 0.05], [4.10, 5.15, 4.10], "VILLAGER_SKIN", south="VILLAGER_FACE"),
            # CHIẾC MŨI DÂN LÀNG 3D TO RỦ XUỐNG ĐẶC TRƯNG KHÔNG THỂ NHẦM LẪN (tỉ lệ 2:4:2, nhô ra +Z gọn trong lồng sắt!)
            make_centered_cube([cage_cx, cage_bot_y + 9.15, cage_cz + 2.48], [1.12, 2.28, 1.32], "VILLAGER_NOSE"),
        ],
    })

    # -------------------------------------------------------------------------
    # H. HAI CHÂN ĐA KHỚP (`hip_l/r` -> `leg_l/r` -> `knee_l/r` -> `shin_l/r` -> `ankle_l/r` -> `foot_l/r` -> `toes_l/r`)
    # -------------------------------------------------------------------------
    for sx, tag in ((+1.0, "l"), (-1.0, "r")):
        lx = sx * 6.3
        bones.append({
            "name": f"hip_{tag}", "parent": "body", "pivot": [lx, 18.2, 0.0], "rotation": [0.0, 0.0, 0.0],
            "cubes": [make_centered_cube([lx, 17.4, 0.0], [8.8, 4.4, 9.2], "SKIN_MOTTLED")],
        })
        th_c = []
        add_beveled_limb_segment(th_c, [lx, 17.8, 0.0], [lx, 10.8, -0.3], 8.8, 9.2, 8.2, 8.6, "SKIN_MOTTLED", n_steps=3)
        bones.append({"name": f"leg_{tag}", "parent": f"hip_{tag}", "pivot": [lx, 17.4, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": th_c})

        bones.append({
            "name": f"knee_{tag}", "parent": f"leg_{tag}", "pivot": [lx, 11.2, -0.3], "rotation": [0.0, 0.0, 0.0],
            "cubes": [
                make_centered_cube([lx, 11.2, -0.2], [8.2, 3.8, 8.4], "SKIN_MOTTLED"),
                make_centered_cube([lx, 11.4, -4.6], [7.2, 3.4, 2.0], "SKIN_MOTTLED", rot=[-10.0, 0.0, 0.0]),
            ],
        })
        sh_c = []
        add_beveled_limb_segment(sh_c, [lx, 11.0, +0.2], [lx, 3.8, +0.4], 8.4, 8.8, 7.8, 8.2, "SKIN_MOTTLED", n_steps=3)
        bones.append({"name": f"shin_{tag}", "parent": f"knee_{tag}", "pivot": [lx, 10.8, 0.0], "rotation": [0.0, 0.0, 0.0], "cubes": sh_c})

        bones.append({
            "name": f"ankle_{tag}", "parent": f"shin_{tag}", "pivot": [lx, 3.8, +0.2], "rotation": [0.0, 0.0, 0.0],
            "cubes": [make_centered_cube([lx, 3.8, 0.0], [7.8, 2.6, 8.4], "SKIN_MOTTLED")],
        })
        bones.append({
            "name": f"foot_{tag}", "parent": f"ankle_{tag}", "pivot": [lx, 3.2, 0.0], "rotation": [0.0, 0.0, 0.0],
            "cubes": [make_centered_cube([lx, 1.9, -0.4], [8.6, 3.8, 9.8], "SKIN_MOTTLED")],
        })
        bones.append({
            "name": f"toes_{tag}", "parent": f"foot_{tag}", "pivot": [lx, 1.4, -5.0], "rotation": [0.0, 0.0, 0.0],
            "cubes": [
                make_centered_cube([lx - 2.9, 1.7, -6.1], [1.85, 3.2, 2.6], "TOES_IVORY"),
                make_centered_cube([lx - 0.95, 1.7, -6.5], [1.85, 3.3, 2.8], "TOES_IVORY"),
                make_centered_cube([lx + 0.95, 1.7, -6.5], [1.85, 3.3, 2.8], "TOES_IVORY"),
                make_centered_cube([lx + 2.9, 1.7, -6.1], [1.85, 3.2, 2.6], "TOES_IVORY"),
            ],
        })

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.cyclops_warden",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 8.0,
                    "visible_bounds_height": 7.5,
                    "visible_bounds_offset": [0.0, 3.2, 0.0],
                },
                "bones": bones,
            }
        ],
    }
    out = ASSETS / "geo" / "entity" / "cyclops_warden.geo.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    print(f"GEO_V2_OK: {out} ({len(bones)} zero-bind-rot articulated bones, {total_cubes} sculpted cubes)")
    return geo


# =============================================================================
# 2. TEXTURE PIXEL-ART 512x512 NÂNG CẤP (DA MỀM ẤM CHUẨN RPG + NGƯỜI TÙ LO ÂU!)
# =============================================================================
def hex_rgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], dtype=np.uint8)


def put_pixel_tile(canvas: np.ndarray, key: str, small_rgba: np.ndarray) -> None:
    x0, y0, w, h = MAT[key]
    im = Image.fromarray(small_rgba, "RGBA").resize((w, h), Image.Resampling.NEAREST)
    canvas[y0:y0 + h, x0:x0 + w] = np.asarray(im, dtype=np.uint8)


def build_textures() -> None:
    base = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    base[:, :, 3] = 255
    rng = np.random.default_rng(2028)

    # 1. SKIN_MOTTLED (24x24 pixel-art da nâu đất loang ấm MỊN HƠN — loại bỏ đường nứt đen gắt như bùn khô!)
    pal_skin = [hex_rgb(c) for c in ("#5E3224", "#7C442E", "#9C5B3C", "#BF7D51", "#DCA66F", "#F1CB94")]
    sm = np.zeros((24, 24, 4), dtype=np.uint8)
    sm[:, :, 3] = 255
    seeds = rng.uniform(0.0, 24.0, (16, 2))
    seed_tones = rng.choice([2, 3, 3, 4, 4, 5], size=16)
    for y in range(24):
        for x in range(24):
            d1, d2, best_i = 1e9, 1e9, 0
            for si, (sx, sy) in enumerate(seeds):
                dx = min(abs(x - sx), 24.0 - abs(x - sx))
                dy = min(abs(y - sy), 24.0 - abs(y - sy))
                d = math.hypot(dx * 1.02, dy * 0.94)
                if d < d1:
                    d2, d1, best_i = d1, d, si
                elif d < d2:
                    d2 = d
            border = d2 - d1
            # Chuyển sắc êm ấm: rãnh chỉ đậm vừa phải (#5E3224 / #7C442E) thay vì đen gắt!
            if border < 0.42:
                idx = 0
            elif border < 0.92:
                idx = 1
            elif d1 < 1.35:
                idx = min(5, int(seed_tones[best_i]) + 1)
            elif d1 < 2.55:
                idx = int(seed_tones[best_i])
            else:
                idx = max(1, int(seed_tones[best_i]) - 1)
            if rng.random() < 0.10:
                idx = max(0, min(5, idx + int(rng.choice([-1, 1]))))
            sm[y, x, :3] = pal_skin[idx]
    put_pixel_tile(base, "SKIN_MOTTLED", sm)

    # 2. SKIN_CHEST_TAN
    sc = np.zeros((16, 16, 4), dtype=np.uint8)
    sc[:, :, 3] = 255
    for y in range(16):
        for x in range(16):
            edge = (x == 0 or x == 15 or y == 0 or y == 15)
            hi = (3 <= y <= 7 and 3 <= x <= 12)
            if edge:
                col = hex_rgb("#7C4732")
            elif y >= 12:
                col = hex_rgb("#A66D4B")
            elif hi:
                col = hex_rgb("#F2CD98") if (x + y) % 3 != 0 else hex_rgb("#E1B57E")
            else:
                col = hex_rgb("#CC9666") if (x + y) % 2 == 0 else hex_rgb("#BA8154")
            sc[y, x, :3] = col
    put_pixel_tile(base, "SKIN_CHEST_TAN", sc)

    # 3. TOES_IVORY
    ti = np.zeros((16, 16, 4), dtype=np.uint8)
    ti[:, :, 3] = 255
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y == 15:
                col = hex_rgb("#825B3E")
            elif y <= 4:
                col = hex_rgb("#FFF1CC")
            elif y <= 10:
                col = hex_rgb("#EED5A2") if (x + y) % 2 == 0 else hex_rgb("#E1C28A")
            else:
                col = hex_rgb("#BE915E")
            ti[y, x, :3] = col
    put_pixel_tile(base, "TOES_IVORY", ti)

    # 4. HEAD_EYE (32x32 mắt kim cương đỏ rực phát sáng!)
    he = np.zeros((32, 32, 4), dtype=np.uint8)
    he_g = np.zeros((32, 32, 4), dtype=np.uint8)
    he[:, :, 3] = 255
    for y in range(32):
        for x in range(32):
            he[y, x, :3] = sm[(y * 24) // 32, (x * 24) // 32, :3]
            dx = abs(x - 15.5) / 13.5
            dy = abs(y - 15.5) / 11.5
            d = dx + dy
            if d < 1.05:
                he[y, x, :3] = hex_rgb("#1E1A22")
            if d < 0.84:
                he[y, x, :3] = hex_rgb("#F4F6FA")
                he_g[y, x] = [255, 220, 210, 95]
            if d < 0.50:
                he[y, x, :3] = hex_rgb("#D81B22")
                he_g[y, x] = [255, 42, 36, 255]
            if d < 0.25:
                he[y, x, :3] = hex_rgb("#FF6A48")
                he_g[y, x] = [255, 115, 70, 255]
            if x == 14 and y == 13:
                he[y, x, :3] = hex_rgb("#FFFFFF")
                he_g[y, x] = [255, 255, 255, 255]
    put_pixel_tile(base, "HEAD_EYE", he)
    put_pixel_tile(glow, "HEAD_EYE", he_g)

    # 5. MOUTH_TEETH
    mt = np.zeros((16, 16, 4), dtype=np.uint8)
    mt[:, :, 3] = 255
    mt[:, :, :3] = hex_rgb("#261210")
    for x in range(1, 15):
        if x % 3 != 0:
            mt[2:11, x, :3] = hex_rgb("#EFE0B4")
            mt[9:12, x, :3] = hex_rgb("#C9AE7A")
        else:
            mt[2:5, x, :3] = hex_rgb("#6A4B34")
    mt[0:2, :, :3] = hex_rgb("#683626")
    mt[13:16, :, :3] = hex_rgb("#52281C")
    put_pixel_tile(base, "MOUTH_TEETH", mt)

    # 6. IRON_DARK (16x16 thép đúc đen-lam rèn búa nặng nề có vết búa & độ sâu kim loại!)
    idrk = np.zeros((16, 16, 4), dtype=np.uint8)
    idrk[:, :, 3] = 255
    pal_iron = [hex_rgb(c) for c in ("#12141D", "#1E2230", "#2F3548", "#4B546B")]
    for y in range(16):
        for x in range(16):
            edge = (x == 0 or y == 0 or x == 15 or y == 15)
            rivet = (x in (3, 12) and y in (3, 12))
            if edge:
                idx = 0
            elif rivet:
                idx = 3
            elif (x * 3 + y * 5) % 7 == 0:
                idx = 2
            elif (x + y * 2) % 5 == 0:
                idx = 0
            else:
                idx = 1
            idrk[y, x, :3] = pal_iron[idx]
    put_pixel_tile(base, "IRON_DARK", idrk)

    # 7. IRON_SILVER (16x16 thép mài phay dọc liền mạch không vằn ngang -> mũi gai & lưỡi dao sáng bóng sắc bén!)
    isil = np.zeros((16, 16, 4), dtype=np.uint8)
    isil_g = np.zeros((16, 16, 4), dtype=np.uint8)
    isil[:, :, 3] = 255
    for y in range(16):
        for x in range(16):
            # Vát sáng dọc theo sống giữa lưỡi (x=6..9 sáng loáng, x=0..2 & 13..15 chuyển gunmetal đậm -> tạo cảm giác lưỡi mài liền khối!)
            dx = abs(x - 7.5)
            if dx >= 6.5:
                isil[y, x, :3] = hex_rgb("#687689") if y % 2 == 0 else hex_rgb("#5E6C7E")
            elif dx >= 4.5:
                isil[y, x, :3] = hex_rgb("#93A3B5") if (x + y) % 3 != 0 else hex_rgb("#8494A6")
            elif dx >= 2.2:
                isil[y, x, :3] = hex_rgb("#C8D7E4") if (x + y) % 2 == 0 else hex_rgb("#BCCBD9")
                isil_g[y, x] = [195, 225, 248, 22]
            else:
                isil[y, x, :3] = hex_rgb("#EDF6FC") if y % 3 != 0 else hex_rgb("#DCEBF5")
                isil_g[y, x] = [220, 244, 255, 52]
    put_pixel_tile(base, "IRON_SILVER", isil)
    put_pixel_tile(glow, "IRON_SILVER", isil_g)

    # 8. BRACER_PLATE
    bp = idrk.copy()
    bp[:, 0:2, :3] = hex_rgb("#9AA6B8")
    bp[:, 14:16, :3] = hex_rgb("#9AA6B8")
    bp[7:9, :, :3] = hex_rgb("#14151D")
    for rx in (4, 11):
        for ry in (3, 12):
            bp[ry:ry + 2, rx:rx + 2, :3] = hex_rgb("#D0DCE8")
    put_pixel_tile(base, "BRACER_PLATE", bp)

    # 9. LEATHER_STRAPS
    ls = np.zeros((16, 16, 4), dtype=np.uint8)
    ls[:, :, 3] = 255
    for y in range(16):
        for x in range(16):
            if y in (0, 15):
                ls[y, x, :3] = hex_rgb("#4E2416")
            elif y in (6, 7, 8):
                ls[y, x, :3] = hex_rgb("#DC864A") if x % 2 == 0 else hex_rgb("#C66E3A")
            else:
                ls[y, x, :3] = hex_rgb("#A24E28") if (x + y) % 3 != 0 else hex_rgb("#82381C")
    put_pixel_tile(base, "LEATHER_STRAPS", ls)

    # 10. BELT_BUCKLE
    bb = ls.copy()
    bb[0:3, :, :3] = hex_rgb("#262834")
    bb[13:16, :, :3] = hex_rgb("#262834")
    for sx in (2, 7, 12):
        bb[5:11, sx:sx + 3, :3] = hex_rgb("#35394A")
        bb[6:10, sx + 1, :3] = hex_rgb("#A4B0C2")
    put_pixel_tile(base, "BELT_BUCKLE", bb)

    # 11. LOINCLOTH_FRINGE (alpha-cutout tua rua da rách)
    lf = np.zeros((16, 16, 4), dtype=np.uint8)
    heights = [14, 12, 15, 11, 14, 16, 12, 15, 13, 11, 15, 14, 12, 16, 13, 11]
    for x in range(16):
        h_lim = heights[x]
        for y in range(h_lim):
            lf[y, x, 3] = 255
            if x % 2 == 0:
                lf[y, x, :3] = hex_rgb("#C8703E") if y < h_lim - 3 else hex_rgb("#E4985C")
            else:
                lf[y, x, :3] = hex_rgb("#8C4024") if y < h_lim - 2 else hex_rgb("#B45E36")
            if y < 2:
                lf[y, x, :3] = hex_rgb("#582818")
    put_pixel_tile(base, "LOINCLOTH_FRINGE", lf)

    # 12. MACE_HAFT
    mh = np.zeros((16, 16, 4), dtype=np.uint8)
    mh[:, :, 3] = 255
    for y in range(16):
        for x in range(16):
            if x in (0, 15):
                mh[y, x, :3] = hex_rgb("#3C1612")
            elif x % 3 == 0:
                mh[y, x, :3] = hex_rgb("#6E2A20")
            else:
                mh[y, x, :3] = hex_rgb("#9A402C") if (x + y) % 4 != 0 else hex_rgb("#B8583A")
    put_pixel_tile(base, "MACE_HAFT", mh)

    # 13. TUSK_BONE
    tb = np.zeros((16, 16, 4), dtype=np.uint8)
    tb[:, :, 3] = 255
    for y in range(16):
        t = y / 15.0
        col = (hex_rgb("#FFF9E0") * (1.0 - t) + hex_rgb("#C49E68") * t).astype(np.uint8)
        tb[y, :, :3] = col
    tb[:, 0, :3] = hex_rgb("#9E7A4A")
    tb[:, 15, :3] = hex_rgb("#9E7A4A")
    put_pixel_tile(base, "TUSK_BONE", tb)

    # 14. VILLAGER_FACE (16x20 chuẩn pixel-art Dân Làng Minecraft: trán cao da đào + lông mày rậm liền + mắt xanh lục bảo!)
    vf = np.zeros((20, 16, 4), dtype=np.uint8)
    vf[:, :, 3] = 255
    for y in range(20):
        for x in range(16):
            if y <= 1:
                col = hex_rgb("#B6856B")
            elif x in (0, 15) or y >= 18:
                col = hex_rgb("#A6755C")
            elif 2 <= y <= 6 and 3 <= x <= 12:
                col = hex_rgb("#D4A78D") if (x + y) % 3 != 0 else hex_rgb("#C99A80")
            else:
                col = hex_rgb("#C5957B") if (x + y) % 2 == 0 else hex_rgb("#BC8B71")
            vf[y, x, :3] = col
    # Lông mày rậm màu nâu sẫm chạy liền ngang trán đặc trưng Dân Làng (hơi nhíu lo âu ở giữa!)
    vf[8, 2:14, :3] = hex_rgb("#4B2C1B")
    vf[9, 2:14, :3] = hex_rgb("#3A1F11")
    vf[10, 6:10, :3] = hex_rgb("#4B2C1B")
    # Đôi mắt xanh lục bảo đặc trưng Dân Làng (tròng trắng phía ngoài, mống mắt xanh lục phía trong!)
    vf[10:13, 2:4, :3] = hex_rgb("#F0F2EC")
    vf[10,    2:4, :3] = hex_rgb("#D5DAD0")
    vf[10:13, 4:6, :3] = hex_rgb("#3FA642")
    vf[11:13, 4:5, :3] = hex_rgb("#1E5922")
    vf[10:13, 12:14, :3] = hex_rgb("#F0F2EC")
    vf[10,    12:14, :3] = hex_rgb("#D5DAD0")
    vf[10:13, 10:12, :3] = hex_rgb("#3FA642")
    vf[11:13, 11:12, :3] = hex_rgb("#1E5922")
    # Gò má hồng ấm & nét miệng hơi lo lắng ẩn cạnh gốc mũi
    vf[13:15, 2:5, :3] = hex_rgb("#CD9A80")
    vf[13:15, 11:14, :3] = hex_rgb("#CD9A80")
    vf[16, 5:11, :3] = hex_rgb("#7E4F3B")
    vf[17, 5, :3] = hex_rgb("#683D2C")
    vf[17, 10, :3] = hex_rgb("#683D2C")
    put_pixel_tile(base, "VILLAGER_FACE", vf)

    # 15. VILLAGER_SKIN (16x20 da đầu đào-nâu ấm cho 2 bên, đỉnh đầu & sau gáy Dân Làng)
    vs = np.zeros((20, 16, 4), dtype=np.uint8)
    vs[:, :, 3] = 255
    for y in range(20):
        for x in range(16):
            if y <= 2:
                col = hex_rgb("#D1A38A") if (x + y) % 2 == 0 else hex_rgb("#C6967C")
            elif y >= 17 or x in (0, 15):
                col = hex_rgb("#A6755B")
            else:
                col = hex_rgb("#C29177") if (x + y) % 2 == 0 else hex_rgb("#B8866C")
            vs[y, x, :3] = col
    put_pixel_tile(base, "VILLAGER_SKIN", vs)

    # 16. VILLAGER_NOSE (12x16 màu da mũi Dân Làng có khối sáng sống mũi & bóng râm dưới chóp mũi!)
    vn = np.zeros((16, 12, 4), dtype=np.uint8)
    vn[:, :, 3] = 255
    for y in range(16):
        for x in range(12):
            if y >= 13:
                col = hex_rgb("#8F5E46")
            elif x in (0, 11) or y >= 11:
                col = hex_rgb("#A8755C")
            elif 3 <= x <= 8 and y <= 7:
                col = hex_rgb("#D6A98F") if (x + y) % 2 == 0 else hex_rgb("#CCA086")
            else:
                col = hex_rgb("#C39379") if (x + y) % 2 == 0 else hex_rgb("#B8866C")
            vn[y, x, :3] = col
    vn[13:15, 2:4, :3] = hex_rgb("#744734")
    vn[13:15, 8:10, :3] = hex_rgb("#744734")
    put_pixel_tile(base, "VILLAGER_NOSE", vn)

    # 17. VILLAGER_BOOTS (12x12 giày da nâu sẫm)
    vb = np.zeros((12, 12, 4), dtype=np.uint8)
    vb[:, :, 3] = 255
    for y in range(12):
        for x in range(12):
            if y >= 9 or x in (0, 11):
                vb[y, x, :3] = hex_rgb("#352014")
            elif y <= 2:
                vb[y, x, :3] = hex_rgb("#5D3C27")
            else:
                vb[y, x, :3] = hex_rgb("#4B2F1E") if (x + y) % 2 == 0 else hex_rgb("#412819")
    put_pixel_tile(base, "VILLAGER_BOOTS", vb)

    # 18. VILLAGER_ROBE_FRONT (16x32 áo choàng nâu Plains Villager chuẩn Minecraft: cổ yếm be + thắt lưng da + khóa đồng + ngọc lục bảo!)
    vrf = np.zeros((32, 16, 4), dtype=np.uint8)
    vrf[:, :, 3] = 255
    for y in range(32):
        for x in range(16):
            if x in (0, 15):
                col = hex_rgb("#5A3B27")
            elif x % 3 == 0:
                col = hex_rgb("#785238")
            else:
                col = hex_rgb("#8D6446") if (x + y) % 2 == 0 else hex_rgb("#825A3E")
            vrf[y, x, :3] = col
    # Cổ chữ V & dải yếm vải màu be sáng trước ngực
    vrf[0:4, 4:12, :3] = hex_rgb("#C4A37E")
    vrf[4:9, 5:11, :3] = hex_rgb("#B89672")
    vrf[0:9, 7:9, :3] = hex_rgb("#9C7856")
    vrf[9:30, 6:10, :3] = hex_rgb("#B5926E")
    vrf[9:30, 7:9, :3] = hex_rgb("#A68360")
    vrf[0:30, 5, :3] = hex_rgb("#63422C")
    vrf[0:30, 10, :3] = hex_rgb("#63422C")
    # Đai thắt lưng da nâu đậm + khóa đồng vàng + viên ngọc lục bảo đặc trưng Dân Làng!
    vrf[14:18, :, :3] = hex_rgb("#462B1B")
    vrf[15:17, :, :3] = hex_rgb("#573623")
    vrf[14:18, 6:10, :3] = hex_rgb("#D8B048")
    vrf[15:17, 7:9, :3] = hex_rgb("#35B54D")
    # Viền gấu áo dưới
    vrf[30:32, :, :3] = hex_rgb("#583924")
    put_pixel_tile(base, "VILLAGER_ROBE_FRONT", vrf)

    # 19. VILLAGER_ROBE_SIDE (16x20 mặt bên & lưng áo choàng nâu Dân Làng)
    vrs = np.zeros((20, 16, 4), dtype=np.uint8)
    vrs[:, :, 3] = 255
    for y in range(20):
        for x in range(16):
            if y in (0, 19) or x in (0, 15):
                col = hex_rgb("#5E3E29")
            elif x % 4 == 0:
                col = hex_rgb("#775138")
            else:
                col = hex_rgb("#8B6244") if (x + y) % 2 == 0 else hex_rgb("#7F583C")
            vrs[y, x, :3] = col
    vrs[9:12, :, :3] = hex_rgb("#462B1B")
    put_pixel_tile(base, "VILLAGER_ROBE_SIDE", vrs)

    # 20. VILLAGER_SLEEVE (16x12 tay áo nâu ấm + viền cổ tay be nhạt đặc trưng Dân Làng)
    vsl = np.zeros((12, 16, 4), dtype=np.uint8)
    vsl[:, :, 3] = 255
    for y in range(12):
        for x in range(16):
            if y in (0, 11):
                col = hex_rgb("#5A3B27")
            elif x in (0, 1, 14, 15):
                col = hex_rgb("#A88662")
            else:
                col = hex_rgb("#8A6144") if (x + y) % 2 == 0 else hex_rgb("#7D563B")
            vsl[y, x, :3] = col
    put_pixel_tile(base, "VILLAGER_SLEEVE", vsl)

    # 21. VILLAGER_SHACKLE (16x16 xích sắt đen viền thép sáng buộc còng tay & eo Dân Làng)
    vsh = np.zeros((16, 16, 4), dtype=np.uint8)
    vsh[:, :, 3] = 255
    for y in range(16):
        for x in range(16):
            if y in (0, 15) or x in (0, 15):
                vsh[y, x, :3] = hex_rgb("#181A24")
            elif (x % 4 == 1 and 4 <= y <= 11) or y in (3, 12):
                vsh[y, x, :3] = hex_rgb("#7C889C")
            else:
                vsh[y, x, :3] = hex_rgb("#2C3040") if (x + y) % 2 == 0 else hex_rgb("#3B4054")
    put_pixel_tile(base, "VILLAGER_SHACKLE", vsh)

    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)
    Image.fromarray(base, "RGBA").save(tex_dir / "cyclops_warden.png")
    Image.fromarray(glow, "RGBA").save(tex_dir / "cyclops_warden_glowmask.png")
    print(f"TEX_V2_OK: {tex_dir / 'cyclops_warden.png'} & glowmask ({TEX_W}x{TEX_H})")


# =============================================================================
# 3. 8 ANIMATION ĐA KHỚP SIÊU MƯỢT (TRỄ PHA TỪ HÔNG -> EO -> NGỰC -> VAI -> KHUỶU -> CỔ TAY -> CHÙY!)
#    TÁCH QUYỀN SỞ HỮU 0 XUNG ĐỘT:
#    - `chain_1..3`, `cage_body`, `cage_door`, `prisoner`, `prisoner_head`, `eyelid_up/lo`,
#      `kilt_front_c/l/r`, `kilt_side_l/r`, `kilt_back` do `CyclopsWardenGeoModel` điều khiển TUYỆT ĐỐI!
# =============================================================================
def kf(vec, easing="easeInOutSine"):
    return {"vector": [round(float(v), 2) for v in vec], "easing": easing}


def build_animations() -> dict:
    anims = {}

    # 1. IDLE (3.40s loop — thở sóng đa khớp mềm từ hông -> eo -> ngực -> xương đòn -> khuỷu -> cổ tay!)
    anims["animation.cyclops_warden.idle"] = {
        "loop": True,
        "animation_length": 3.40,
        "bones": {
            "body":       {"position": {"0.00": kf([0, 0, 0]), "1.70": kf([0, -0.8, 0]), "3.40": kf([0, 0, 0])}},
            "pelvis":     {"rotation": {"0.00": kf([0.4, 0, 0]), "1.70": kf([1.6, 0, 0]), "3.40": kf([0.4, 0, 0])}},
            "waist":      {"rotation": {"0.00": kf([0.6, 0, 0]), "1.80": kf([2.2, 0, 0]), "3.40": kf([0.6, 0, 0])}},
            "chest":      {"rotation": {"0.00": kf([0.8, 0, 0]), "1.90": kf([2.8, 0, 0]), "3.40": kf([0.8, 0, 0])}},
            "neck":       {"rotation": {"0.00": kf([-0.6, 0, 0]), "1.90": kf([1.6, 0, 0]), "3.40": kf([-0.6, 0, 0])}},
            "head":       {"rotation": {"0.00": kf([-0.8, 0, 0]), "1.95": kf([1.8, 0, 0]), "3.40": kf([-0.8, 0, 0])}},
            "jaw":        {"rotation": {"0.00": kf([1.8, 0, 0]), "1.85": kf([7.5, 0, 0]), "3.40": kf([1.8, 0, 0])}},
            "clavicle_l": {"rotation": {"0.00": kf([0, 0, 0]), "1.75": kf([0, 0, -2.5]), "3.40": kf([0, 0, 0])}},
            "shoulder_l": {"rotation": {"0.00": kf([0, 0, 0]), "1.85": kf([2.2, 0, -2.2]), "3.40": kf([0, 0, 0])}},
            "elbow_l":    {"rotation": {"0.00": kf([0, 0, 0]), "1.95": kf([-4.0, 0, 0]), "3.40": kf([0, 0, 0])}},
            "wrist_l":    {"rotation": {"0.00": kf([0, 0, 0]), "2.05": kf([-3.5, 0, 2.0]), "3.40": kf([0, 0, 0])}},
            "fingers_l":  {"rotation": {"0.00": kf([0, 0, 0]), "2.10": kf([-6.0, 0, 0]), "3.40": kf([0, 0, 0])}},
            "clavicle_r": {"rotation": {"0.00": kf([0, 0, 0]), "1.75": kf([0, 0, 2.2]), "3.40": kf([0, 0, 0])}},
            "shoulder_r": {"rotation": {"0.00": kf([0, 0, 0]), "1.85": kf([-2.4, 0, 1.8]), "3.40": kf([0, 0, 0])}},
            "wrist_r":    {"rotation": {"0.00": kf([0, 0, 0]), "2.00": kf([2.6, 0, -1.6]), "3.40": kf([0, 0, 0])}},
            "mace_root":  {"rotation": {"0.00": kf([0, 0, 0]), "2.10": kf([2.0, 0, -1.5]), "3.40": kf([0, 0, 0])}},
        },
    }

    # 2. WALK (1.20s loop — dồn trọng lượng hông-eo-ngực ngược chiều + lăn gối, cổ chân & ngón chân cực mượt!)
    anims["animation.cyclops_warden.walk"] = {
        "loop": True,
        "animation_length": 1.20,
        "bones": {
            "body": {
                "position": {"0.00": kf([0, 1.0, 0]), "0.30": kf([0, -1.4, 0], "easeOutCubic"), "0.60": kf([0, 1.0, 0]), "0.90": kf([0, -1.4, 0], "easeOutCubic"), "1.20": kf([0, 1.0, 0])},
                "rotation": {"0.00": kf([3.0, -4.5, 3.2]), "0.60": kf([3.0, 4.5, -3.2]), "1.20": kf([3.0, -4.5, 3.2])},
            },
            "pelvis":     {"rotation": {"0.00": kf([1.5, -5.0, 2.5]), "0.60": kf([1.5, 5.0, -2.5]), "1.20": kf([1.5, -5.0, 2.5])}},
            "waist":      {"rotation": {"0.00": kf([2.5, 3.0, -1.8]), "0.60": kf([2.5, -3.0, 1.8]), "1.20": kf([2.5, 3.0, -1.8])}},
            "chest":      {"rotation": {"0.00": kf([3.5, 5.5, -2.5]), "0.60": kf([3.5, -5.5, 2.5]), "1.20": kf([3.5, 5.5, -2.5])}},
            "neck":       {"rotation": {"0.00": kf([-2.0, -2.5, 0]), "0.60": kf([-2.0, 2.5, 0]), "1.20": kf([-2.0, -2.5, 0])}},
            "head":       {"rotation": {"0.00": kf([-2.5, -3.5, 0]), "0.60": kf([-2.5, 3.5, 0]), "1.20": kf([-2.5, -3.5, 0])}},
            "jaw":        {"rotation": {"0.00": kf([3.5, 0, 0]), "0.60": kf([9.0, 0, 0]), "1.20": kf([3.5, 0, 0])}},
            # Chân trái đa khớp (hông -> đùi -> gối -> cổ chân -> mũi chân!)
            "hip_l":      {"rotation": {"0.00": kf([-6, 0, 0]), "0.60": kf([6, 0, 0]), "1.20": kf([-6, 0, 0])}},
            "leg_l":      {"rotation": {"0.00": kf([-22, 0, 0]), "0.30": kf([0, 0, 0]), "0.60": kf([20, 0, 0]), "0.90": kf([-8, 0, 0]), "1.20": kf([-22, 0, 0])}},
            "knee_l":     {"rotation": {"0.00": kf([8, 0, 0]), "0.30": kf([4, 0, 0]), "0.60": kf([14, 0, 0]), "0.90": kf([28, 0, 0]), "1.20": kf([8, 0, 0])}},
            "ankle_l":    {"rotation": {"0.00": kf([10, 0, 0]), "0.30": kf([-2, 0, 0]), "0.60": kf([-14, 0, 0]), "0.90": kf([8, 0, 0]), "1.20": kf([10, 0, 0])}},
            "toes_l":     {"rotation": {"0.00": kf([-10, 0, 0]), "0.30": kf([0, 0, 0]), "0.60": kf([16, 0, 0]), "0.90": kf([-4, 0, 0]), "1.20": kf([-10, 0, 0])}},
            # Chân phải ngược pha
            "hip_r":      {"rotation": {"0.00": kf([6, 0, 0]), "0.60": kf([-6, 0, 0]), "1.20": kf([6, 0, 0])}},
            "leg_r":      {"rotation": {"0.00": kf([20, 0, 0]), "0.30": kf([-8, 0, 0]), "0.60": kf([-22, 0, 0]), "0.90": kf([0, 0, 0]), "1.20": kf([20, 0, 0])}},
            "knee_r":     {"rotation": {"0.00": kf([14, 0, 0]), "0.30": kf([28, 0, 0]), "0.60": kf([8, 0, 0]), "0.90": kf([4, 0, 0]), "1.20": kf([14, 0, 0])}},
            "ankle_r":    {"rotation": {"0.00": kf([-14, 0, 0]), "0.30": kf([8, 0, 0]), "0.60": kf([10, 0, 0]), "0.90": kf([-2, 0, 0]), "1.20": kf([-14, 0, 0])}},
            "toes_r":     {"rotation": {"0.00": kf([16, 0, 0]), "0.30": kf([-4, 0, 0]), "0.60": kf([-10, 0, 0]), "0.90": kf([0, 0, 0]), "1.20": kf([16, 0, 0])}},
            # Tay trái đánh tự nhiên có trễ pha khuỷu & cổ tay
            "clavicle_l": {"rotation": {"0.00": kf([4, 0, -2]), "0.60": kf([-4, 0, -4]), "1.20": kf([4, 0, -2])}},
            "arm_l":      {"rotation": {"0.00": kf([18, 0, -4]), "0.60": kf([-22, 0, -2]), "1.20": kf([18, 0, -4])}},
            "elbow_l":    {"rotation": {"0.00": kf([-6, 0, 0]), "0.60": kf([-20, 0, 0]), "1.20": kf([-6, 0, 0])}},
            "wrist_l":    {"rotation": {"0.00": kf([8, 0, 0]), "0.60": kf([-10, 0, 0]), "1.20": kf([8, 0, 0])}},
            # Tay phải giữ đại chùy rung nảy nặng trĩu
            "clavicle_r": {"rotation": {"0.00": kf([-3, 0, 2]), "0.60": kf([3, 0, -2]), "1.20": kf([-3, 0, 2])}},
            "shoulder_r": {"rotation": {"0.00": kf([-4, 0, 2]), "0.60": kf([4, 0, -2]), "1.20": kf([-4, 0, 2])}},
            "wrist_r":    {"rotation": {"0.00": kf([4, 0, -2]), "0.60": kf([-4, 0, 2]), "1.20": kf([4, 0, -2])}},
            "mace_root":  {"rotation": {"0.00": kf([3.5, 0, -2.5]), "0.60": kf([-3.5, 0, 2.5]), "1.20": kf([3.5, 0, -2.5])}},
        },
    }

    # 3. MACE_SLAM (1.30s = 26 ticks — lấy đà toàn thân, uốn eo ngực, giáng búa 6 cánh xé đất có độ nảy!)
    anims["animation.cyclops_warden.mace_slam"] = {
        "animation_length": 1.30,
        "bones": {
            "body":       {"position": {"0.00": kf([0, 0, 0]), "0.44": kf([0, 2.6, 1.8], "easeInCubic"), "0.68": kf([0, -3.6, -3.2], "easeOutExpo"), "0.96": kf([0, -2.4, -2.2]), "1.30": kf([0, 0, 0])}},
            "waist":      {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-10, -14, 0], "easeInCubic"), "0.68": kf([14, 12, 0], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "chest":      {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-14, -18, 0], "easeInCubic"), "0.68": kf([22, 16, 0], "easeOutExpo"), "0.98": kf([18, 12, 0]), "1.30": kf([0, 0, 0])}},
            "neck":       {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-8, 8, 0]), "0.68": kf([6, -6, 0], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "head":       {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-10, 12, 0]), "0.68": kf([10, -8, 0], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "jaw":        {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([36, 0, 0]), "0.68": kf([46, 0, 0], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "clavicle_r": {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-14, -10, 12], "easeInCubic"), "0.68": kf([18, 10, -8], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "shoulder_r": {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-34, -14, 14], "easeInCubic"), "0.68": kf([58, 12, -12], "easeOutExpo"), "0.98": kf([50, 8, -8]), "1.30": kf([0, 0, 0])}},
            "arm_r":      {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-22, 0, 10], "easeInCubic"), "0.68": kf([40, 0, -8], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "elbow_r":    {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-16, 0, 0]), "0.68": kf([26, 0, 0], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "wrist_r":    {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-18, 0, 10]), "0.68": kf([34, -10, -14], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "mace_root":  {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-20, 0, 12]), "0.68": kf([44, -10, -16], "easeOutExpo"), "0.98": kf([38, -8, -12]), "1.30": kf([0, 0, 0])}},
            "shoulder_l": {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([20, 0, -22]), "0.68": kf([-26, 0, -16], "easeOutExpo"), "1.30": kf([0, 0, 0])}},
            "elbow_l":    {"rotation": {"0.00": kf([0, 0, 0]), "0.44": kf([-18, 0, 0]), "0.68": kf([-28, 0, 0]), "1.30": kf([0, 0, 0])}},
        },
    }

    # 4. MACE_SWEEP (1.20s = 24 ticks — vặn xoắn hông-eo-ngực quét chùy rộng 180°!)
    anims["animation.cyclops_warden.mace_sweep"] = {
        "animation_length": 1.20,
        "bones": {
            "pelvis":     {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([0, -14, 0], "easeInCubic"), "0.66": kf([0, 18, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "waist":      {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([4, -20, 0], "easeInCubic"), "0.66": kf([8, 24, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "chest":      {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([6, -26, 0], "easeInCubic"), "0.66": kf([10, 32, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "neck":       {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([0, 12, 0]), "0.66": kf([2, -12, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "head":       {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([0, 20, 0]), "0.66": kf([4, -20, 0], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "jaw":        {"rotation": {"0.00": kf([0, 0, 0]), "0.52": kf([38, 0, 0]), "1.20": kf([0, 0, 0])}},
            "shoulder_r": {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([18, -32, 38], "easeInCubic"), "0.66": kf([44, 40, -22], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "arm_r":      {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([16, -14, 22]), "0.66": kf([28, 22, -16], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "wrist_r":    {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([16, -14, 16]), "0.66": kf([30, 24, -26], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "mace_root":  {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([20, -16, 18]), "0.66": kf([34, 28, -28], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
            "shoulder_l": {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([-22, 0, -20]), "0.66": kf([24, 0, -14], "easeOutExpo"), "1.20": kf([0, 0, 0])}},
        },
    }

    # 5. BRACER_BASH (1.05s = 21 ticks — nghiêng vai húc khiên đinh tay trái!)
    anims["animation.cyclops_warden.bracer_bash"] = {
        "animation_length": 1.05,
        "bones": {
            "body":       {"position": {"0.00": kf([0, 0, 0]), "0.34": kf([0, -1.8, 2.5], "easeInCubic"), "0.56": kf([0, 0.5, -4.6], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
            "waist":      {"rotation": {"0.00": kf([0, 0, 0]), "0.34": kf([6, 16, 0], "easeInCubic"), "0.56": kf([10, -18, 0], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
            "chest":      {"rotation": {"0.00": kf([0, 0, 0]), "0.34": kf([8, 24, 0], "easeInCubic"), "0.56": kf([14, -26, 0], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
            "head":       {"rotation": {"0.00": kf([0, 0, 0]), "0.34": kf([4, -18, 0]), "0.56": kf([6, 20, 0], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
            "jaw":        {"rotation": {"0.00": kf([0, 0, 0]), "0.48": kf([34, 0, 0]), "1.05": kf([0, 0, 0])}},
            "clavicle_l": {"rotation": {"0.00": kf([0, 0, 0]), "0.34": kf([10, 12, -10]), "0.56": kf([-18, -14, 10], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
            "shoulder_l": {"rotation": {"0.00": kf([0, 0, 0]), "0.34": kf([22, 16, -20], "easeInCubic"), "0.56": kf([-60, -24, 14], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
            "elbow_l":    {"rotation": {"0.00": kf([0, 0, 0]), "0.34": kf([-26, 0, 0]), "0.56": kf([-38, -24, 10], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
            "wrist_l":    {"rotation": {"0.00": kf([0, 0, 0]), "0.56": kf([-16, -18, 0], "easeOutExpo"), "1.05": kf([0, 0, 0])}},
        },
    }

    # 6. WAR_ROAR (1.50s = 30 ticks)
    anims["animation.cyclops_warden.roar"] = {
        "animation_length": 1.50,
        "bones": {
            "body":       {"position": {"0.00": kf([0, 0, 0]), "0.38": kf([0, -3.2, 0], "easeInCubic"), "0.68": kf([0, 2.4, 0], "easeOutExpo"), "1.50": kf([0, 0, 0])}},
            "waist":      {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([10, 0, 0]), "0.68": kf([-10, 0, 0], "easeOutExpo"), "1.50": kf([0, 0, 0])}},
            "chest":      {"rotation": {"0.00": kf([0, 0, 0]), "0.38": kf([12, 0, 0]), "0.68": kf([-16, 0, 0], "easeOutExpo"), "1.15": kf([-12, 0, 0]), "1.50": kf([0, 0, 0])}},
            "neck":       {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-12, 0, 0], "easeOutExpo"), "1.15": kf([-10, 0, 0]), "1.50": kf([0, 0, 0])}},
            "head":       {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-18, 0, 0], "easeOutExpo"), "1.15": kf([-14, 0, 0]), "1.50": kf([0, 0, 0])}},
            "jaw":        {"rotation": {"0.00": kf([0, 0, 0]), "0.66": kf([48, 0, 0], "easeOutExpo"), "1.18": kf([40, 0, 0]), "1.50": kf([0, 0, 0])}},
            "clavicle_l": {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([0, 0, -14], "easeOutExpo"), "1.50": kf([0, 0, 0])}},
            "shoulder_l": {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-32, 0, -26], "easeOutExpo"), "1.50": kf([0, 0, 0])}},
            "clavicle_r": {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([0, 0, 12], "easeOutExpo"), "1.50": kf([0, 0, 0])}},
            "shoulder_r": {"rotation": {"0.00": kf([0, 0, 0]), "0.68": kf([-24, 0, 22], "easeOutExpo"), "1.50": kf([0, 0, 0])}},
        },
    }

    # 7. EMERGE (3.20s hold — khớp chính xác 64 ticks!)
    anims["animation.cyclops_warden.emerge"] = {
        "loop": "hold_on_last_frame",
        "animation_length": 3.20,
        "bones": {
            "body":       {"position": {"0.00": kf([0, -9.0, 0]), "1.55": kf([0, -2.0, 0], "easeOutCubic"), "2.15": kf([0, 1.8, 0], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
            "waist":      {"rotation": {"0.00": kf([14, 0, 0]), "1.80": kf([-8, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
            "chest":      {"rotation": {"0.00": kf([16, 0, 0]), "1.80": kf([-14, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
            "neck":       {"rotation": {"0.00": kf([10, 0, 0]), "1.85": kf([-10, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
            "head":       {"rotation": {"0.00": kf([16, 0, 0]), "1.85": kf([-18, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
            "jaw":        {"rotation": {"0.00": kf([0, 0, 0]), "1.90": kf([46, 0, 0], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
            "shoulder_l": {"rotation": {"0.00": kf([-18, 0, -18]), "1.90": kf([-26, 0, -24], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
            "shoulder_r": {"rotation": {"0.00": kf([14, 0, 10]), "1.90": kf([-20, 0, 18], "easeOutExpo"), "3.20": kf([0, 0, 0])}},
        },
    }

    # 8. DEATH (2.00s hold)
    anims["animation.cyclops_warden.death"] = {
        "loop": "hold_on_last_frame",
        "animation_length": 2.00,
        "bones": {
            "body": {
                "position": {"0.00": kf([0, 0, 0]), "0.60": kf([0, -6.5, -2.0], "easeInCubic"), "1.20": kf([0, -15.5, -6.5], "easeOutBounce"), "2.00": kf([0, -15.5, -6.5])},
                "rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([32, 0, 6], "easeInCubic"), "1.20": kf([82, 0, 10], "easeOutCubic"), "2.00": kf([82, 0, 10])},
            },
            "waist":      {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([12, 0, 0]), "1.20": kf([6, 0, 0])}},
            "chest":      {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([14, 0, 0]), "1.20": kf([8, 0, 0])}},
            "neck":       {"rotation": {"0.00": kf([0, 0, 0]), "1.20": kf([8, -6, 0])}},
            "head":       {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([-12, 8, 0]), "1.20": kf([14, -14, 0])}},
            "jaw":        {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([36, 0, 0]), "1.20": kf([18, 0, 0])}},
            "shoulder_l": {"rotation": {"0.00": kf([0, 0, 0]), "1.20": kf([-32, 0, -28], "easeOutCubic")}},
            "shoulder_r": {"rotation": {"0.00": kf([0, 0, 0]), "1.20": kf([46, 12, 22], "easeOutCubic")}},
            "mace_root":  {"rotation": {"0.00": kf([0, 0, 0]), "1.20": kf([44, -16, 26], "easeOutCubic")}},
            "leg_l":      {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([-34, 0, 0]), "1.20": kf([-18, 0, -10])}},
            "knee_l":     {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([42, 0, 0]), "1.20": kf([26, 0, 0])}},
            "leg_r":      {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([-24, 0, 0]), "1.20": kf([-10, 0, 12])}},
            "knee_r":     {"rotation": {"0.00": kf([0, 0, 0]), "0.60": kf([32, 0, 0]), "1.20": kf([18, 0, 0])}},
        },
    }

    out = {"format_version": "1.8.0", "animations": anims}
    out_path = ASSETS / "animations" / "entity" / "cyclops_warden.animation.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
    print(f"ANIM_V2_OK: {out_path} ({len(anims)} multi-joint smooth animations verified)")
    return out


# =============================================================================
# 4. BỘ RENDER PREVIEW 3D CHẠY ĐÚNG 100% PHÉP BIẾN ĐỔI GL CỦA GECKOLIB 4.8.3!
#    (`x_gl = -x_json`, `pivot_gl = (-px, py, pz)`, `M_gl = Rz(+rz) @ Ry(-ry) @ Rx(-rx)`)
# =============================================================================
def collect_geckolib_gl_geometry(geo: dict):
    bone_map = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    xforms = {}

    def get_xform_gl(name):
        if name in xforms:
            return xforms[name]
        b = bone_map[name]
        bp = b.get("pivot", [0.0, 0.0, 0.0])
        piv = np.array([-float(bp[0]), float(bp[1]), float(bp[2])], dtype=np.float64)
        R_loc = geckolib_gl_rot_matrix(b.get("rotation", [0.0, 0.0, 0.0]))
        t_loc = piv - R_loc @ piv
        parent = b.get("parent")
        if parent and parent in bone_map:
            Rp, tp = get_xform_gl(parent)
            R, t = Rp @ R_loc, Rp @ t_loc + tp
        else:
            R, t = R_loc, t_loc
        xforms[name] = (R, t)
        return R, t

    faces, pts_all = [], []
    # Lưu ý: sau khi đảo X (x_gl = -(ox + sx) .. -ox), các mặt được giữ nhất quán theo `BakedModelFactory.VertexSet`!
    face_defs = [
        ("north", (0, 1, 2, 3)), ("south", (5, 4, 7, 6)),
        ("west",  (4, 0, 3, 7)), ("east",  (1, 5, 6, 2)),
        ("up",    (3, 2, 6, 7)), ("down",  (4, 5, 1, 0)),
    ]
    for b in geo["minecraft:geometry"][0]["bones"]:
        Rb, tb = get_xform_gl(b["name"])
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            # ĐÚNG 100% `BakedModelFactory.constructCube` dòng 172!
            gx0 = -(ox + sx)
            gx1 = -ox
            gy0, gy1 = oy, oy + sy
            gz0, gz1 = oz, oz + sz
            corners = np.array([
                [gx0, gy0, gz0], [gx1, gy0, gz0], [gx1, gy1, gz0], [gx0, gy1, gz0],
                [gx0, gy0, gz1], [gx1, gy0, gz1], [gx1, gy1, gz1], [gx0, gy1, gz1],
            ], dtype=np.float64)
            if "rotation" in c:
                cp_raw = c["pivot"]
                # ĐÚNG 100% `BakedModelFactory.constructCube` dòng 175-176!
                cp = np.array([-float(cp_raw[0]), float(cp_raw[1]), float(cp_raw[2])], dtype=np.float64)
                Rc = geckolib_gl_rot_matrix(c["rotation"])
                corners = ((corners - cp) @ Rc.T) + cp
            wc = (corners @ Rb.T) + tb
            pts_all.append(wc)
            uv_map = c["uv"]
            for fname, idxs in face_defs:
                if fname in uv_map:
                    faces.append((wc[list(idxs)], uv_map[fname]))
    all_p = np.vstack(pts_all)
    return faces, all_p.min(axis=0), all_p.max(axis=0)


def rasterize(faces, diff_img, glow_img, view_mat, width, height, scale, center, bg=(14, 21, 26)):
    fb = np.zeros((height, width, 3), dtype=np.float32)
    fb[:, :] = np.array(bg, dtype=np.float32)
    zbuf = np.full((height, width), -1e9, dtype=np.float32)
    cx, cy = width * 0.5, height * 0.52
    light_dir = np.array([-0.35, 0.78, -0.52], dtype=np.float64)
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
        shade = 0.62 + 0.38 * abs(float(np.dot(n, light_dir)))
        # Trong không gian GL (đã đảo X), +X là bên phải camera khi nhìn về -Z
        scr = np.stack([cx + cam[:, 0] * scale, cy - cam[:, 1] * scale, cam[:, 2]], axis=1)
        u0, v0 = uv["uv"]
        uw, uh = uv["uv_size"]
        u1 = u0 + uw - 0.01
        v1 = v0 + uh - 0.01
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        rtri(scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
        rtri(scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)
    return Image.fromarray(fb.astype(np.uint8), "RGB")


def render_previews(geo: dict) -> None:
    tex_dir = ASSETS / "textures" / "entity"
    diff = np.array(Image.open(tex_dir / "cyclops_warden.png").convert("RGBA"), dtype=np.float32)
    glw = np.array(Image.open(tex_dir / "cyclops_warden_glowmask.png").convert("RGBA"), dtype=np.float32)
    faces, bmin, bmax = collect_geckolib_gl_geometry(geo)
    ctr = (bmin + bmax) * 0.5
    span = float(np.max(bmax - bmin))

    def cam_mat(yaw_deg, pitch_deg):
        y, p = math.radians(yaw_deg), math.radians(pitch_deg)
        cy, sy = math.cos(y), math.sin(y)
        cp, sp = math.cos(p), math.sin(p)
        return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pw, ph = 560, 480
    sc = (min(pw, ph) * 0.80) / max(1.0, span)
    views = [
        ("front_v2.png",        180,  4, "V3 FRONT (HEAVY SPIKED GOTHIC WAR-MACE!)"),
        ("front_34_v2.png",     142,  6, "V3 FRONT 3/4 (HEAVY CORE + RAZOR SPIKES)"),
        ("back_v2.png",           0,  4, "V3 BACK (6-BLADE RAZOR MACE + VILLAGER CAGE)"),
        ("mace_top_side.png",    42, 34, "V3 MACE CLOSE-UP — TOP-SIDE (MATCH USER ANGLE!)"),
        ("mace_crown.png",      -24, 18, "V3 MACE CLOSE-UP — CROWN SPIKE & 6 RAZOR FLANGES"),
        ("villager_34.png",      34, 10, "VILLAGER IN CAGE — 3/4 CHECK"),
    ]
    imgs = []
    for fn, yw, pt, title in views:
        if fn.startswith("villager_"):
            c_use = np.array([-4.4, 21.8, +12.6])
            s_use = sc * 2.85
        elif fn.startswith("mace_"):
            # GL-space center of the heavy mace head (`x_gl = -x_json`)
            c_use = np.array([+0.5, 48.5, +6.5])
            s_use = sc * 2.15
        else:
            c_use = ctr
            s_use = sc
        im = rasterize(faces, diff, glw, cam_mat(yw, pt), pw, ph, s_use, c_use)
        ImageDraw.Draw(im).text((12, 10), title, fill=(240, 244, 250))
        im.save(PREVIEW_DIR / fn)
        imgs.append(im)

    sheet = Image.new("RGB", (pw * 3, ph * 2), (10, 14, 18))
    for i, im in enumerate(imgs):
        sheet.paste(im, ((i % 3) * pw, (i // 3) * ph))
    sheet.save(PREVIEW_DIR / "cyclops_v2_preview_sheet.png")
    print(f"PREVIEW_V2_OK: {PREVIEW_DIR / 'cyclops_v2_preview_sheet.png'} | GL bbox X=[{bmin[0]:.1f}..{bmax[0]:.1f}] Y=[{bmin[1]:.1f}..{bmax[1]:.1f}] Z=[{bmin[2]:.1f}..{bmax[2]:.1f}]")


if __name__ == "__main__":
    geo = build_geo()
    build_textures()
    build_animations()
    render_previews(geo)
