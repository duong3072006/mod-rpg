import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Rebuilds the 3D GeckoLib Bedrock geometry (.geo.json) and 256x256 cel-shaded
 * textures + glowmasks
 * for Togetsu Moon Blades (Waxing = White, Waning = Black) matching all 5
 * reference angles:
 *
 * v2 Precision Polish:
 * - Exact Bedrock Euler decomposition for R_target = Rx(-rx) * Ry(±bevelYaw) so
 * angled bevel cheek slabs
 * lie 100% flush along the curved blade with zero twist and zero
 * stair-stepping!
 * - True curved moon-crescent silhouette + deeper U-notch + sharp heel talon +
 * proud outward-curving silver claw guard.
 * - Clean fusiform top-down spine showing full metallic silver/charcoal beveled
 * faces with crisp thin cel-shaded
 * hairline outlines (zero chunky black blocks over the spine or side faces!).
 * - Smooth stepped 3D armor plates (raised Base Plate vs stepped-down Upper/Tip
 * Plate) separated by a crisp
 * continuous 3D recessed dark S-shaped groove following the exact reference
 * S-curve.
 * - Full 3D chamfered/octagonal hilt, red leather wraps, hollow pommel ring
 * with open eyelet hole,
 * faceted spherical red bead, and 3D ridged S-curved leaf tassel.
 * - Preserves exact item IDs, grip center (0,0,0), +Y blade axis, -Z cutting
 * edge, and bone hierarchy
 * (root -> blade_root -> blade_body -> blade_edge) so First-Person &
 * Third-Person alignment stay 100% intact.
 */
public class RebuildTogetsuDual3D {
    static final Path MOD_ROOT = Path.of("D:\\cremod\\mod-rpg");
    static final Path ASSETS = MOD_ROOT.resolve("src\\main\\resources\\assets\\abyssalrpg");
    static final Path OUT_DIR = Path
            .of("C:\\Users\\lucka\\.gemini\\antigravity\\brain\\6b270670-349b-4122-a74d-bf2d4d46e806\\scratch");
    static final Path PREVIEW_OUT = MOD_ROOT.resolve("tools\\togetsu_previews\\togetsu_dual_3d_rebuild_preview.png");

    static final int TEX_W = 256;
    static final int TEX_H = 256;

    static final Map<String, int[]> MAT = new LinkedHashMap<>();
    static {
        MAT.put("BASE_PLATE", new int[] { 0, 0, 32, 32 });
        MAT.put("UPPER_PLATE", new int[] { 32, 0, 32, 32 });
        MAT.put("BEVEL_EDGE", new int[] { 64, 0, 32, 32 });
        MAT.put("SPINE_FACE", new int[] { 96, 0, 32, 32 });
        MAT.put("CEL_OUTLINE", new int[] { 128, 0, 32, 32 });
        MAT.put("GUARD_SILVER", new int[] { 160, 0, 32, 32 });
        MAT.put("GUARD_SHADE", new int[] { 192, 0, 32, 32 });
        MAT.put("GRIP_LEATHER", new int[] { 224, 0, 32, 32 });
        MAT.put("GRIP_WRAP", new int[] { 0, 32, 32, 32 });
        MAT.put("POMMEL_DARK", new int[] { 32, 32, 32, 32 });
        MAT.put("BEAD_RED", new int[] { 64, 32, 32, 32 });
        MAT.put("TASSEL_MAIN", new int[] { 96, 32, 32, 32 });
        MAT.put("TASSEL_RIDGE", new int[] { 128, 32, 32, 32 });
        MAT.put("RIDGE_CREST", new int[] { 160, 32, 32, 32 });
        // Row 2 (y=64..95) — Dedicated 3D Luminous Accent Channels (runtime-tinted via multi-channel glowmasks!)
        MAT.put("GLOW_VEIN",    new int[]{0,  64, 32, 32}); // S-groove energy vein & claw ridge accent
        MAT.put("GLOW_NODE",    new int[]{32, 64, 32, 32}); // Raised 3D faceted diamond crystal nodes along ridge
        MAT.put("GLOW_EDGE",    new int[]{64, 64, 32, 32}); // Luminous razor cutting edge filament & tip/heel
        MAT.put("GLOW_GUARD",   new int[]{96, 64, 32, 32}); // Silver bolster inlaid crystal stud
        MAT.put("GLOW_BEAD",    new int[]{128,64, 32, 32}); // Luminous faceted crimson ruby pommel bead
    }

    static class Cube {
        double ox, oy, oz;
        double sx, sy, sz;
        double px, py, pz;
        double rx, ry, rz;
        boolean hasRot;
        String mat;
        String matSide;

        static Cube centered(double cx, double cy, double cz, double sx, double sy, double sz,
                String mat, double rx, double ry, double rz) {
            return centeredDual(cx, cy, cz, sx, sy, sz, mat, null, rx, ry, rz);
        }

        static Cube centeredDual(double cx, double cy, double cz, double sx, double sy, double sz,
                String mat, String matSide, double rx, double ry, double rz) {
            Cube c = new Cube();
            c.ox = cx - sx * 0.5;
            c.oy = cy - sy * 0.5;
            c.oz = cz - sz * 0.5;
            c.sx = Math.max(0.04, sx);
            c.sy = Math.max(0.04, sy);
            c.sz = Math.max(0.04, sz);
            c.px = cx;
            c.py = cy;
            c.pz = cz;
            c.rx = rx;
            c.ry = ry;
            c.rz = rz;
            c.hasRot = (Math.abs(rx) > 1e-3 || Math.abs(ry) > 1e-3 || Math.abs(rz) > 1e-3);
            c.mat = mat;
            c.matSide = matSide;
            return c;
        }
    }

    // --- Matrix helpers & Exact Bedrock Euler Decomposition ---
    // Bedrock applies: R_bedrock(rx, ry, rz) = Rz(-rz) * Ry(+ry) * Rx(-rx)
    // When we want a bevel cheek tilted by `localBevelYDeg` around the slice's OWN
    // Y axis
    // AND THEN pitched along the blade curve by `pitchXDeg` around world X:
    // M_target = Rx(-pitchXDeg) * Ry(localBevelYDeg)
    // Solve Rz(-rz) * Ry(+ry) * Rx(-rx) = M_target exactly!
    static double[] id() {
        double[] m = new double[16];
        m[0] = m[5] = m[10] = m[15] = 1;
        return m;
    }

    static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                for (int k = 0; k < 4; k++)
                    r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j];
        return r;
    }

    static double[] trans(double x, double y, double z) {
        double[] m = id();
        m[3] = x;
        m[7] = y;
        m[11] = z;
        return m;
    }

    static double[] rxM(double d) {
        double r = Math.toRadians(d), c = Math.cos(r), s = Math.sin(r), m[] = id();
        m[5] = c;
        m[6] = -s;
        m[9] = s;
        m[10] = c;
        return m;
    }

    static double[] ryM(double d) {
        double r = Math.toRadians(d), c = Math.cos(r), s = Math.sin(r), m[] = id();
        m[0] = c;
        m[2] = s;
        m[8] = -s;
        m[10] = c;
        return m;
    }

    static double[] rzM(double d) {
        double r = Math.toRadians(d), c = Math.cos(r), s = Math.sin(r), m[] = id();
        m[0] = c;
        m[1] = -s;
        m[4] = s;
        m[5] = c;
        return m;
    }

    static double[] app(double[] m, double[] p) {
        return new double[] { m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
                m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7], m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11] };
    }

    /**
     * Exact Bedrock Euler angles [rx, ry, rz] (degrees) whose Bedrock rotation
     * Rz(-rz)*Ry(ry)*Rx(-rx)
     * equals M = Rx(-pitchXDeg) * Ry(localBevelYDeg) to machine precision!
     */
    static double[] bedrockEulerForLocalBevel(double pitchXDeg, double localBevelYDeg) {
        double[] M = mul(rxM(-pitchXDeg), ryM(localBevelYDeg));
        // For R = Rz(a) * Ry(b) * Rx(c):
        // R[8] = -sin(b)
        // R[9] = cos(b)*sin(c), R[10] = cos(b)*cos(c) => c = atan2(R[9], R[10])
        // R[4] = sin(a)*cos(b), R[0] = cos(a)*cos(b) => a = atan2(R[4], R[0])
        double sb = clamp(-M[8], -1.0, 1.0);
        double b = Math.asin(sb);
        double c = Math.atan2(M[9], M[10]);
        double a = Math.atan2(M[4], M[0]);
        // Map (a, b, c) back to Bedrock (-rz, +ry, -rx):
        return new double[] { -Math.toDegrees(c), Math.toDegrees(b), -Math.toDegrees(a) };
    }

    // =========================================================================
    // 1. EXACT MOON-CRESCENT SILHOUETTE & CROSS-SECTION (MATCHES IMAGES 1-5)
    // =========================================================================
    static final double Y_MIN = 2.00;
    static final double Y_MAX = 21.60;

    /**
     * Outer convex moon-crescent cutting edge (-Z side). Sweeps fuller & curvier
     * like Images 1 & 3!
     */
    static double edgeZ(double u) {
        u = clamp(u, 0.0, 1.0);
        // Full sweeping crescent belly:
        // u=0.00 -> -4.75 (sharp backward heel spike)
        // u=0.22 -> -5.30 (bold full crescent belly!)
        // u=0.55 -> -3.45
        // u=0.82 -> -0.35
        // u=1.00 -> +2.95 (graceful hooked crescent tip!)
        return -4.75 * Math.pow(1.0 - u, 1.15)
                - 1.85 * Math.sin(Math.PI * u) * Math.pow(1.0 - u, 0.45)
                + 2.95 * Math.pow(u, 1.62);
    }

    /**
     * Back spine (+Z side) curving gracefully concave-up to match the moon-crescent
     * in Images 1 & 3!
     */
    static double backZ(double u) {
        u = clamp(u, 0.0, 1.0);
        if (u < 0.18) {
            // Deep U-shaped crescent cutout at heel (Images 1, 3, 4, 5)
            double t = u / 0.18;
            return -4.75 + 3.45 * Math.pow(t, 0.58);
        }
        // Main spine: sweeps smoothly upward from bolster shoulder (z=+0.82) curving
        // out to the crescent tip (z=+2.95)
        double t = (u - 0.18) / 0.82;
        return 0.82 + 0.14 * Math.sin(Math.PI * t) + 2.13 * Math.pow(t, 1.55);
    }

    /**
     * Exact S-shaped groove boundary u_seam(f): f=0 at cutting edge (-Z), f=1 at
     * back spine (+Z).
     */
    static double sGrooveU(double f) {
        f = clamp(f, 0.0, 1.0);
        // Matches Images 1 & 3: starts ~0.34 at cutting edge, crisp S-wave across
        // center, exits ~0.62 at spine
        return 0.480 + 0.122 * Math.tanh((f - 0.48) * 7.2) + 0.028 * (f - 0.5);
    }

    /**
     * Fusiform central ridge half-thickness (X) matching Image 2 top-down spine
     * view!
     */
    static double ridgeHalfThick(double u) {
        u = clamp(u, 0.0, 1.0);
        if (u < 0.18) {
            double t = u / 0.18;
            return 0.13 + 0.90 * Math.pow(t, 0.70);
        }
        double t = (u - 0.18) / 0.82;
        // Image 2 profile: thick sculpted mid-body (~2.20 px total width!) tapering
        // gracefully to a needle tip (0.14 px)!
        double swell = 1.00 + 0.16 * Math.sin(Math.PI * Math.min(1.0, t * 1.40));
        double taper = Math.pow(Math.max(0.0, 1.0 - Math.pow(t, 1.42)), 0.78);
        return Math.max(0.07, 1.04 * swell * taper);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Rounded/chamfered octagonal tube along YZ points. */
    static void addOctagonalTube(List<Cube> out, double[][] ptsYZ, double[] wx, double[] dz,
            String mat, String rimMat, boolean addThinRim) {
        for (int i = 0; i < ptsYZ.length - 1; i++) {
            double ya = ptsYZ[i][0], za = ptsYZ[i][1];
            double yb = ptsYZ[i + 1][0], zb = ptsYZ[i + 1][1];
            double ym = 0.5 * (ya + yb), zm = 0.5 * (za + zb);
            double len = Math.hypot(yb - ya, zb - za) * 1.15;
            double rx = -Math.toDegrees(Math.atan2(zb - za, yb - ya));
            double w = 0.5 * (wx[i] + wx[i + 1]);
            double d = 0.5 * (dz[i] + dz[i + 1]);
            double eps = (i % 2 == 1) ? 0.013 : 0.0;
            out.add(Cube.centered(0.0, ym, zm, (w + eps) * 1.00, len, d * 0.72, mat, rx, 0.0, 0.0));
            out.add(Cube.centered(0.0, ym, zm, (w + eps) * 0.72, len * 0.992, d * 1.00, mat, rx, 0.0, 0.0));
            if (addThinRim && rimMat != null) {
                // Razor-thin cel-shaded outer ridge accent along claw curve
                out.add(Cube.centered(0.0, ym, zm + d * 0.51, Math.max(0.10, w * 0.24), len * 0.97, 0.09, rimMat, rx,
                        0.0, 0.0));
            }
        }
    }

    /** 3D Faceted Spherical Bead (3 intersecting orthogonal chamfered boxes). */
    static void addFacetedSphere(List<Cube> out, double cx, double cy, double cz, double r, String mat) {
        double a = r * 1.92, b = r * 1.38;
        out.add(Cube.centered(cx, cy, cz, a, b, b, mat, 0, 0, 0));
        out.add(Cube.centered(cx, cy, cz, b, a, b, mat, 0, 0, 0));
        out.add(Cube.centered(cx, cy, cz, b, b, a, mat, 0, 0, 0));
    }

    /** Raised 3D faceted glowing diamond crystal stud with dark bezel protruding cleanly on BOTH +-X faces! */
    static void addRaisedGlowDiamond(List<Cube> out, double cy, double cz, double halfX,
                                     double sizeY, double sizeZ, double rxPitch, String glowMat) {
        out.add(Cube.centered(0.0, cy, cz, (halfX + 0.06) * 2.0, sizeY * 1.18, sizeZ * 1.18, "CEL_OUTLINE", rxPitch + 45.0, 0, 0));
        out.add(Cube.centered(0.0, cy, cz, (halfX + 0.14) * 2.0, sizeY, sizeZ, glowMat, rxPitch + 45.0, 0, 0));
        out.add(Cube.centered(0.0, cy, cz, (halfX + 0.19) * 2.0, sizeY * 0.58, sizeZ * 0.58, glowMat, rxPitch + 45.0, 0, 0));
    }

    static Map<String, List<Cube>> buildDualBladeBones() {
        List<Cube> rootCubes = new ArrayList<>();
        List<Cube> bodyCubes = new ArrayList<>();
        List<Cube> edgeCubes = new ArrayList<>();

        // =====================================================================
        // A. 3D CHAMFERED HILT, RED WRAPS, SILVER CLAW GUARD, HOLLOW POMMEL, BEAD,
        // TASSEL
        // =====================================================================

        // 1. S-curved ergonomic red-wrapped handle centered at (0, 0, 0)
        double[][] gripPts = {
                { -2.85, +0.44 }, { -1.85, +0.16 }, { -0.85, -0.04 }, { +0.15, -0.02 }, { +1.15, +0.12 },
                { +2.15, +0.34 }
        };
        double[] gripW = { 1.20, 1.30, 1.40, 1.38, 1.30, 1.24 };
        double[] gripD = { 1.24, 1.36, 1.46, 1.44, 1.36, 1.30 };
        addOctagonalTube(rootCubes, gripPts, gripW, gripD, "GRIP_LEATHER", null, false);

        // Raised diagonal crimson leather wraps with crisp thin dark creases
        double[][] wrapPos = {
                { -2.25, +0.27, -14.0 }, { -1.45, +0.06, -12.0 }, { -0.65, -0.04, -10.0 },
                { +0.15, -0.01, -11.0 }, { +0.95, +0.09, -13.0 }, { +1.65, +0.23, -15.0 }
        };
        for (int i = 0; i < wrapPos.length; i++) {
            double wy = wrapPos[i][0], wz = wrapPos[i][1], rx = wrapPos[i][2];
            double eps = (i % 2 == 1) ? 0.015 : 0.0;
            rootCubes.add(Cube.centered(0.0, wy, wz, 1.48 + eps, 0.44, 1.42, "GRIP_WRAP", rx, 0.0,
                    (i % 2 == 0) ? 14.0 : -14.0));
            rootCubes.add(Cube.centered(0.0, wy - 0.24, wz + 0.04, 1.36 + eps, 0.11, 1.32, "CEL_OUTLINE", rx, 0.0,
                    (i % 2 == 0) ? 12.0 : -12.0));
        }

        // 2. 3D Chamfered Black Pommel Ring with OPEN THROUGH-HOLE EYELET (Images 1, 3,
        // 4, 5)
        double pcY = -3.52, pcZ = +0.62, pcRx = -14.0;
        rootCubes.add(Cube.centered(0.0, -2.92, +0.49, 1.42, 0.36, 1.56, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, -2.74, +0.44, 1.44, 0.12, 1.50, "CEL_OUTLINE", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, -4.12, +0.76, 1.36, 0.38, 1.50, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ - 0.54, 1.38, 0.96, 0.42, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ + 0.54, 1.38, 0.96, 0.42, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ - 0.74, 0.96, 0.84, 0.10, "CEL_OUTLINE", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ + 0.74, 0.96, 0.84, 0.10, "CEL_OUTLINE", pcRx, 0, 0));

        // 3. 3D Faceted Spherical Luminous Ruby Bead
        rootCubes.add(Cube.centered(0.0, -4.32, +0.80, 0.40, 0.24, 0.40, "CEL_OUTLINE", pcRx, 0, 0));
        addFacetedSphere(rootCubes, 0.0, -4.68, +0.86, 0.56, "GLOW_BEAD");
        rootCubes.add(Cube.centered(0.0, -5.18, +0.92, 0.30, 0.24, 0.30, "CEL_OUTLINE", 0, 0, 0));

        // 4. 3D Sculpted S-Curved Leaf Tassel (raised midrib + beveled leaf margins +
        // crisp dark rim)
        double[][] tasselPts = {
                { -5.22, +0.92 }, { -5.85, +1.22 }, { -6.68, +1.18 }, { -7.52, +0.82 }, { -8.32, +0.52 },
                { -9.02, +0.58 }, { -9.62, +0.86 }
        };
        double[] tasZ = { 0.26, 0.92, 1.34, 1.24, 0.82, 0.44, 0.14 };
        double[] tasX = { 0.28, 0.44, 0.50, 0.46, 0.36, 0.24, 0.14 };
        for (int i = 0; i < tasselPts.length - 1; i++) {
            double ya = tasselPts[i][0], za = tasselPts[i][1];
            double yb = tasselPts[i + 1][0], zb = tasselPts[i + 1][1];
            double ym = 0.5 * (ya + yb), zm = 0.5 * (za + zb);
            double len = Math.hypot(yb - ya, zb - za) * 1.18;
            double rx = -Math.toDegrees(Math.atan2(zb - za, yb - ya));
            double wZ = 0.5 * (tasZ[i] + tasZ[i + 1]);
            double tX = 0.5 * (tasX[i] + tasX[i + 1]);
            double eps = (i % 2 == 1) ? 0.012 : 0.0;
            rootCubes.add(
                    Cube.centered(0.0, ym, zm, tX + eps, len, Math.max(0.16, wZ * 0.38), "TASSEL_RIDGE", rx, 0, 0));
            rootCubes.add(Cube.centered(0.0, ym, zm, tX * 0.54 + eps, len * 0.99, wZ, "TASSEL_MAIN", rx, 0, 0));
            rootCubes.add(Cube.centered(0.0, ym, zm - wZ * 0.51, tX * 0.38, len * 0.95, 0.09, "CEL_OUTLINE", rx, 0, 0));
            rootCubes.add(Cube.centered(0.0, ym, zm + wZ * 0.51, tX * 0.38, len * 0.95, 0.09, "CEL_OUTLINE", rx, 0, 0));
        }

        // 5. 3D Chamfered Silver Bolster Collar & Proud Outward-Curving Silver Claw
        // Guard (Images 1, 3, 4, 5!)
        rootCubes.add(Cube.centered(0.0, 2.18, +0.36, 1.50, 0.20, 1.50, "CEL_OUTLINE", -14.0, 0, 0));
        // Rounded octagonal silver collar bolster + raised glowing guard crystal stud on BOTH faces!
        rootCubes.add(Cube.centered(0.0, 2.68, +0.52, 1.70, 0.96, 1.46, "GUARD_SILVER", -14.0, 0, 0));
        rootCubes.add(Cube.centered(0.0, 2.68, +0.52, 1.36, 0.94, 1.78, "GUARD_SILVER", -14.0, 0, 0));
        addRaisedGlowDiamond(rootCubes, 2.74, +0.52, 0.84, 0.44, 0.44, -14.0, "GLOW_GUARD");
        // Recessed silver throat cushion visible inside the hollow U-arch (Image 4 &
        // 5!)
        rootCubes.add(Cube.centered(0.0, 3.45, +0.46, 1.48, 1.15, 1.20, "GUARD_SHADE", -10.0, 0, 0));
        rootCubes.add(Cube.centered(0.0, 3.22, -0.18, 1.16, 0.85, 0.88, "GUARD_SHADE", -8.0, 0, 0));

        // Curved Silver Claw Talon hugging gracefully along +Z spine with crisp luminous outer ridge accent!
        double[][] clawPts = {
                { 2.50, +0.88 }, { 3.20, +1.52 }, { 4.15, +1.98 }, { 5.20, +2.08 }, { 6.25, +1.90 }, { 7.25, +1.56 },
                { 8.15, +1.22 }
        };
        double[] clawW = { 1.44, 1.24, 1.00, 0.76, 0.54, 0.34, 0.16 };
        double[] clawD = { 1.10, 0.98, 0.84, 0.66, 0.48, 0.30, 0.15 };
        addOctagonalTube(rootCubes, clawPts, clawW, clawD, "GUARD_SILVER", "GLOW_VEIN", true);

        // =====================================================================
        // B. SMOOTH SCULPTED 3D BEVELED BLADE (EXACT BEDROCK BEVEL ALIGNMENT!)
        // =====================================================================

        // B1. Root bridge connecting bolster collar smoothly into main blade belly
        // (creates hollow U-throat arch of Image 4!)
        double[][] neckSlices = {
                { 3.20, -0.05, +0.96, 1.66 },
                { 4.00, -0.58, +0.94, 1.88 },
                { 4.80, -1.12, +0.92, 2.02 },
                { 5.55, -1.58, +0.92, 2.10 }
        };
        for (int i = 0; i < neckSlices.length; i++) {
            double ny = neckSlices[i][0], z0 = neckSlices[i][1], z1 = neckSlices[i][2], tx = neckSlices[i][3];
            double zm = 0.5 * (z0 + z1), dz = z1 - z0;
            double eps = (i % 2 == 1) ? 0.013 : 0.0;
            bodyCubes.add(Cube.centered(0.0, ny, zm, tx + eps, 1.05, dz * 0.80, "BASE_PLATE", -5.0, 0, 0));
            bodyCubes.add(
                    Cube.centered(0.0, ny, z1 - dz * 0.20, tx * 0.78 + eps, 1.02, dz * 0.42, "SPINE_FACE", -5.0, 0, 0));
            // Crisp dark U-throat inner rim
            bodyCubes.add(Cube.centered(0.0, ny, z0 + 0.05, tx * 0.60, 0.98, 0.14, "CEL_OUTLINE", -12.0, 0, 0));
        }

        // B2. 32 Smooth Sculpted Diamond-Beveled Slices from heel (u=0) to needle tip
        // (u=1)
        // ZERO chunky black blocks on slices: step transitions smoothly across
        // sGrooveU(f), and B3 draws the crisp continuous black S-groove!
        final int N_SLICES = 32;
        final double spanY = Y_MAX - Y_MIN;

        for (int i = 0; i < N_SLICES; i++) {
            double ua = i / (double) N_SLICES;
            double ub = (i + 1) / (double) N_SLICES;
            double um = 0.5 * (ua + ub);

            double ya = Y_MIN + ua * spanY;
            double yb = Y_MIN + ub * spanY;
            double ym = 0.5 * (ya + yb);

            double zeA = edgeZ(ua), zeB = edgeZ(ub);
            double zbA = backZ(ua), zbB = backZ(ub);
            double zeM = 0.5 * (zeA + zeB);
            double zbM = 0.5 * (zbA + zbB);

            double width = zbM - zeM;
            if (width < 0.10)
                continue;

            double zMidA = 0.5 * (zeA + zbA), zMidB = 0.5 * (zeB + zbB);
            double rxMid = -Math.toDegrees(Math.atan2(zMidB - zMidA, yb - ya));
            double rxEdge = -Math.toDegrees(Math.atan2(zeB - zeA, yb - ya));
            double rxBack = -Math.toDegrees(Math.atan2(zbB - zbA, yb - ya));
            double segLenMid = Math.hypot(yb - ya, zMidB - zMidA) * 1.18;
            double segLenEdge = Math.hypot(yb - ya, zeB - zeA) * 1.20;
            double segLenBack = Math.hypot(yb - ya, zbB - zbA) * 1.20;

            double rHalfBase = ridgeHalfThick(um);
            double eps = (i % 2 == 1) ? 0.011 : 0.0;

            // Clean two-layer stepped armor plate factor (NO blocky black slice
            // replacements!)
            // Lower/Base plate (u < sGrooveU): full thickness 1.00, mat = BASE_PLATE
            // Upper/Tip plate (u >= sGrooveU): stepped down 0.84, mat = UPPER_PLATE
            double[] fSample = { 0.08, 0.34, 0.66, 0.92 };
            double[] stepMul = new double[4];
            String[] bandMat = new String[4];
            for (int b = 0; b < 4; b++) {
                boolean isUpper = (um >= sGrooveU(fSample[b]));
                stepMul[b] = isUpper ? 0.84 : 1.00;
                if (b == 0)
                    bandMat[b] = "BEVEL_EDGE";
                else if (b == 3)
                    bandMat[b] = "SPINE_FACE";
                else
                    bandMat[b] = isUpper ? "UPPER_PLATE" : "BASE_PLATE";
            }

            // Cross-section stations (Z):
            // zeM (0%) -> zEdgeEnd (18%) -> zRidge (56%) -> zSpineStart (84%) -> zbM (100%)
            double zEdgeEnd = zeM + width * 0.18;
            double zRidge = zeM + width * 0.56;
            double zSpineStart = zeM + width * 0.84;

            double edgeHalf = Math.max(0.06, Math.min(0.16, rHalfBase * 0.14));
            double ridgeHalfF = rHalfBase * stepMul[1];
            double ridgeHalfB = rHalfBase * stepMul[2];
            double spineHalf = Math.max(0.07, rHalfBase * 0.60 * stepMul[3]);

            // --- BAND 1: Razor Cutting Edge (`blade_edge`) + crisp luminous razor filament ---
            double dEdge = Math.max(0.14, zEdgeEnd - zeM);
            edgeCubes.add(Cube.centered(
                    0.0, ym, zeM + dEdge * 0.50,
                    edgeHalf * 2.0 + eps, segLenEdge, dEdge,
                    bandMat[0], rxEdge, 0.0, 0.0));
            // Luminous razor cutting-edge highlight filament along outer moon crescent
            if (um > 0.03 && um < 0.99) {
                edgeCubes.add(Cube.centered(
                    0.0, ym, zeM - 0.05,
                    Math.max(0.10, edgeHalf * 1.12), segLenEdge * 0.99, 0.14,
                    "GLOW_EDGE", rxEdge, 0.0, 0.0
                ));
            }

            // --- BAND 2: FLUSH-ALIGNED 3D ANGLED FRONT BEVEL CHEEKS (EXACT BEDROCK EULER!)
            // ---
            double dFront = Math.max(0.12, zRidge - (zeM + dEdge * 0.65));
            double zFrontMid = (zeM + dEdge * 0.65) + dFront * 0.50;
            // Solid interior fill so cross-section is completely watertight
            bodyCubes.add(Cube.centered(
                    0.0, ym, zFrontMid + dFront * 0.10,
                    Math.max(0.10, (edgeHalf + ridgeHalfF) * 0.72) + eps, segLenMid * 0.99, dFront * 0.82,
                    bandMat[1], rxMid, 0.0, 0.0));
            if (dFront > 0.24 && (ridgeHalfF - edgeHalf) > 0.09) {
                double rise = ridgeHalfF - edgeHalf;
                double slopeLen = Math.hypot(dFront, rise) * 1.03;
                double bevelYaw = Math.toDegrees(Math.atan2(rise, dFront));
                double slabThick = Math.max(0.15, Math.min(0.28, ridgeHalfF * 0.30));
                double xOff = 0.5 * (edgeHalf + ridgeHalfF) - slabThick * 0.42;
                // Exact Bedrock Euler for Rx(-rxMid) * Ry(±bevelYaw) -> ZERO twist between
                // curved slices!
                double[] ePos = bedrockEulerForLocalBevel(rxMid, +bevelYaw);
                double[] eNeg = bedrockEulerForLocalBevel(rxMid, -bevelYaw);
                bodyCubes.add(Cube.centered(+xOff, ym, zFrontMid, slabThick + eps * 0.5, segLenMid, slopeLen,
                        bandMat[1], ePos[0], ePos[1], ePos[2]));
                bodyCubes.add(Cube.centered(-xOff, ym, zFrontMid, slabThick + eps * 0.5, segLenMid, slopeLen,
                        bandMat[1], eNeg[0], eNeg[1], eNeg[2]));
            }

            // --- BAND 3: DISTINCT CENTRAL RIDGE & FLUSH-ALIGNED BACK BEVEL CHEEKS ---
            double dBack = Math.max(0.12, zSpineStart - zRidge);
            double zBackMid = zRidge + dBack * 0.50;
            double ridgeCapZ = Math.max(0.18, width * 0.15);
            // Crisp central ridge crest!
            bodyCubes.add(Cube.centered(
                    0.0, ym, zRidge,
                    ridgeHalfB * 2.02 + eps, segLenMid * 1.02, ridgeCapZ,
                    "RIDGE_CREST", rxMid, 0.0, 0.0));
            // Solid back fill
            bodyCubes.add(Cube.centered(
                    0.0, ym, zBackMid,
                    Math.max(0.12, (ridgeHalfB + spineHalf) * 0.94) + eps, segLenMid, dBack * 1.04,
                    bandMat[2], rxBack, 0.0, 0.0));
            // Flush-aligned back bevel cheek plates sloping cleanly from central ridge down
            // to chamfered spine!
            if (dBack > 0.22 && (ridgeHalfB - spineHalf) > 0.06) {
                double riseB = ridgeHalfB - spineHalf;
                double slopeLenB = Math.hypot(dBack, riseB) * 1.04;
                double bevelYawB = Math.toDegrees(Math.atan2(riseB, dBack));
                double slabThickB = Math.max(0.15, Math.min(0.28, ridgeHalfB * 0.30));
                double xOffB = 0.5 * (ridgeHalfB + spineHalf) - slabThickB * 0.42;
                double[] ePosB = bedrockEulerForLocalBevel(rxBack, -bevelYawB);
                double[] eNegB = bedrockEulerForLocalBevel(rxBack, +bevelYawB);
                bodyCubes.add(Cube.centered(+xOffB, ym, zBackMid, slabThickB + eps * 0.5, segLenBack, slopeLenB,
                        bandMat[2], ePosB[0], ePosB[1], ePosB[2]));
                bodyCubes.add(Cube.centered(-xOffB, ym, zBackMid, slabThickB + eps * 0.5, segLenBack, slopeLenB,
                        bandMat[2], eNegB[0], eNegB[1], eNegB[2]));
            }

            // --- BAND 4: METALLIC CHAMFERED SPINE CREST (Image 2!) + RAZOR-THIN
            // CEL-OUTLINE RIMS ---
            double dSpine = Math.max(0.16, zbM - zSpineStart);
            // Bright metallic spine top (matches Image 2's sleek silver-grey fusiform top
            // view!)
            bodyCubes.add(Cube.centered(
                    0.0, ym, zbM - dSpine * 0.50,
                    spineHalf * 2.0 + eps, segLenBack, dSpine * 1.08,
                    bandMat[3], rxBack, 0.0, 0.0));
            // Only thin 0.08 px dark cel-shaded hairline edges along spine margins (NEVER
            // covers the silver spine!)
            if (um < 0.18) {
                // Crisp dark hairline inside concave U-notch
                bodyCubes.add(
                        Cube.centered(0.0, ym, zbM + 0.04, Math.max(0.10, spineHalf * 1.10), segLenBack * 0.98, 0.09,
                                "CEL_OUTLINE", rxBack, 0.0, 0.0));
            } else if (um <= 0.96) {
                // Thin central spine ink hairline (like Image 2 center seam!) + thin left/right
                // shoulder hairlines
                bodyCubes.add(Cube.centered(0.0, ym, zbM + 0.04, 0.09, segLenBack * 0.99, 0.09,
                        "CEL_OUTLINE", rxBack, 0.0, 0.0));
                if (spineHalf > 0.22) {
                    bodyCubes.add(
                            Cube.centered(+(spineHalf + 0.02), ym, zbM - dSpine * 0.30, 0.08, segLenBack * 0.98, 0.10,
                                    "CEL_OUTLINE", rxBack, 0.0, 0.0));
                    bodyCubes.add(
                            Cube.centered(-(spineHalf + 0.02), ym, zbM - dSpine * 0.30, 0.08, segLenBack * 0.98, 0.10,
                                    "CEL_OUTLINE", rxBack, 0.0, 0.0));
                }
            }
        }

        // B3. Crisp Continuous 3D Dark S-Shaped Recessed Groove Ribbon following
        // sGrooveU(f) on BOTH faces & across spine!
        final int N_SEAM = 18;
        for (int s = 0; s < N_SEAM; s++) {
            double fa = 0.01 + (s / (double) N_SEAM) * 0.98;
            double fb = 0.01 + ((s + 1) / (double) N_SEAM) * 0.98;
            double fm = 0.5 * (fa + fb);
            double ua = sGrooveU(fa), ub = sGrooveU(fb), um = 0.5 * (ua + ub);

            double ya = Y_MIN + ua * spanY, yb = Y_MIN + ub * spanY, ym = 0.5 * (ya + yb);
            double za = lerp(edgeZ(ua), backZ(ua), fa);
            double zb = lerp(edgeZ(ub), backZ(ub), fb);
            double zm = 0.5 * (za + zb);

            double segLen = Math.hypot(yb - ya, zb - za) * 1.24;
            double rx = -Math.toDegrees(Math.atan2(zb - za, yb - ya));
            double rH = ridgeHalfThick(um);
            double edgeH = Math.max(0.06, Math.min(0.16, rH * 0.14));
            double profileX;
            if (fm < 0.56) {
                profileX = lerp(edgeH, rH, fm / 0.56);
            } else {
                profileX = lerp(rH, rH * 0.60, (fm - 0.56) / 0.44);
            }
            // Sits razor-crisp right across the stepped boundary: dark bezel + proud glowing energy vein!
            double seamHalfX = profileX * 1.04 + 0.02;
            double eps = (s % 2 == 1) ? 0.010 : 0.0;
            bodyCubes.add(Cube.centered(
                0.0, ym, zm,
                seamHalfX * 2.0 + eps, segLen, 0.30,
                "CEL_OUTLINE", rx, 0.0, 0.0
            ));
            bodyCubes.add(Cube.centered(
                0.0, ym, zm,
                (seamHalfX + 0.045) * 2.0 + eps, segLen * 1.01, 0.15,
                "GLOW_VEIN", rx, 0.0, 0.0
            ));
        }

        // B3b. 3 Raised 3D Glowing Diamond Crystal Nodes along the central blade ridge!
        double[][] dualNodes = {
            {0.25, 0.56, 0.72, 0.72}, // Lower crescent belly focus crystal
            {0.37, 0.54, 0.50, 0.50}, // Mid-lower stepping node
            {0.69, 0.54, 0.52, 0.52}  // Upper blade spear-focus crystal
        };
        for (double[] dn : dualNodes) {
            double u = dn[0], f = dn[1];
            double ny = Y_MIN + u * spanY;
            double nz = lerp(edgeZ(u), backZ(u), f);
            double dy = 0.02 * spanY;
            double dz = lerp(edgeZ(u + 0.02), backZ(u + 0.02), f) - nz;
            double rx = -Math.toDegrees(Math.atan2(dz, dy));
            double rH = ridgeHalfThick(u) * (u >= sGrooveU(f) ? 0.84 : 1.00);
            addRaisedGlowDiamond(bodyCubes, ny, nz, rH, dn[2], dn[3], rx, "GLOW_NODE");
        }

        // B4. Razor-sharp solid tapered luminous moon tip (`y = 21.4 .. 22.3`)
        edgeCubes.add(Cube.centered(0.0, 21.55, +2.85, 0.18, 0.72, 0.34, "GLOW_EDGE", -34.0, 0, 0));
        edgeCubes.add(Cube.centered(0.0, 21.98, +3.14, 0.13, 0.54, 0.20, "GLOW_EDGE", -36.0, 0, 0));
        edgeCubes.add(Cube.centered(0.0, 21.92, +3.25, 0.08, 0.58, 0.08, "CEL_OUTLINE", -36.0, 0, 0));

        // B5. Sharp backward-pointing luminous heel talon (`y = 1.6 .. 2.3, z = -4.75`)
        edgeCubes.add(Cube.centered(0.0, 1.98, -4.76, 0.22, 0.68, 0.36, "GLOW_EDGE", +32.0, 0, 0));
        edgeCubes.add(Cube.centered(0.0, 1.62, -4.96, 0.12, 0.48, 0.18, "CEL_OUTLINE", +36.0, 0, 0));

        Map<String, List<Cube>> res = new LinkedHashMap<>();
        res.put("blade_root", rootCubes);
        res.put("blade_body", bodyCubes);
        res.put("blade_edge", edgeCubes);
        return res;
    }

    // =========================================================================
    // 2. JSON SERIALIZER & 256x256 CEL-SHADED TEXTURES
    // =========================================================================
    static String fmt(double v) {
        if (Math.abs(v) < 5e-4)
            v = 0.0;
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("(\\.\\d*?)0+$", "$1").replaceAll("\\.$", ".0");
    }

    static String uvEntry(String matKey) {
        int[] r = MAT.get(matKey);
        int pad = 2;
        return String.format(Locale.ROOT, "{\"uv\": [%d, %d], \"uv_size\": [%d, %d]}",
                r[0] + pad, r[1] + pad, r[2] - 2 * pad, r[3] - 2 * pad);
    }

    static void writeGeoJson(String variant, Map<String, List<Cube>> boneCubes) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format_version\": \"1.12.0\",\n");
        sb.append("  \"minecraft:geometry\": [\n");
        sb.append("    {\n");
        sb.append("      \"description\": {\n");
        sb.append("        \"identifier\": \"geometry.togetsu_").append(variant).append("\",\n");
        sb.append("        \"texture_width\": ").append(TEX_W).append(",\n");
        sb.append("        \"texture_height\": ").append(TEX_H).append(",\n");
        sb.append("        \"visible_bounds_width\": 2.6,\n");
        sb.append("        \"visible_bounds_height\": 3.0,\n");
        sb.append("        \"visible_bounds_offset\": [0.0, 0.4, 0.0]\n");
        sb.append("      },\n");
        sb.append("      \"bones\": [\n");
        sb.append("        {\"name\": \"root\", \"pivot\": [0, 0, 0], \"rotation\": [0, 0, 0]},\n");

        String[][] meta = {
                { "blade_root", "root", "[0, 0, 0]" },
                { "blade_body", "blade_root", "[0, 3.0, 0]" },
                { "blade_edge", "blade_body", "[0, 9.0, -1.0]" }
        };
        for (int bi = 0; bi < meta.length; bi++) {
            String bname = meta[bi][0], parent = meta[bi][1], piv = meta[bi][2];
            List<Cube> cubes = boneCubes.get(bname);
            sb.append("        {\n");
            sb.append("          \"name\": \"").append(bname).append("\",\n");
            sb.append("          \"parent\": \"").append(parent).append("\",\n");
            sb.append("          \"pivot\": ").append(piv).append(",\n");
            sb.append("          \"rotation\": [0, 0, 0],\n");
            sb.append("          \"cubes\": [\n");
            for (int ci = 0; ci < cubes.size(); ci++) {
                Cube c = cubes.get(ci);
                String mainUv = uvEntry(c.mat);
                String sideUv = c.matSide != null ? uvEntry(c.matSide) : mainUv;
                sb.append("            {");
                sb.append("\"origin\": [").append(fmt(c.ox)).append(", ").append(fmt(c.oy)).append(", ")
                        .append(fmt(c.oz)).append("], ");
                sb.append("\"size\": [").append(fmt(c.sx)).append(", ").append(fmt(c.sy)).append(", ").append(fmt(c.sz))
                        .append("], ");
                if (c.hasRot) {
                    sb.append("\"pivot\": [").append(fmt(c.px)).append(", ").append(fmt(c.py)).append(", ")
                            .append(fmt(c.pz)).append("], ");
                    sb.append("\"rotation\": [").append(fmt(c.rx)).append(", ").append(fmt(c.ry)).append(", ")
                            .append(fmt(c.rz)).append("], ");
                }
                sb.append("\"uv\": {")
                        .append("\"north\": ").append(mainUv).append(", ")
                        .append("\"south\": ").append(mainUv).append(", ")
                        .append("\"east\": ").append(sideUv).append(", ")
                        .append("\"west\": ").append(sideUv).append(", ")
                        .append("\"up\": ").append(mainUv).append(", ")
                        .append("\"down\": ").append(mainUv)
                        .append("}}");
                if (ci < cubes.size() - 1)
                    sb.append(",");
                sb.append("\n");
            }
            sb.append("          ]\n");
            sb.append("        }").append(bi < meta.length - 1 ? "," : "").append("\n");
        }
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        Path out = ASSETS.resolve("geo\\item\\togetsu_" + variant + ".geo.json");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        int total = boneCubes.values().stream().mapToInt(List::size).sum();
        System.out.println("GEO_" + variant.toUpperCase() + "_OK: " + out + " (" + total + " cubes)");
    }

    static int rgba(int r, int g, int b, int a) {
        return ((clampI(a) & 255) << 24) | ((clampI(r) & 255) << 16) | ((clampI(g) & 255) << 8) | (clampI(b) & 255);
    }

    static int clampI(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static void fillTile(BufferedImage diff, BufferedImage glowMain, BufferedImage glowAccent, String key,
                         int[] cDark, int[] cMid, int[] cHi,
                         int mainMaskLevel, int accentMaskLevel, String style, Random rng) {
        int[] r = MAT.get(key);
        int x0 = r[0], y0 = r[1], w = r[2], h = r[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double fx = x / (double) (w - 1);
                double fy = y / (double) (h - 1);
                double n = (rng.nextDouble() - 0.5) * 0.08;
                double t;
                if ("leather".equals(style)) {
                    double diag = ((x + y * 1.4) % 8.0) / 8.0;
                    t = clamp(diag < 0.22 ? 0.14 : (diag < 0.50 ? 0.84 : 0.52) + n, 0, 1);
                } else if ("wrap".equals(style)) {
                    double band = ((x * 0.7 + y * 1.5) % 9.0) / 9.0;
                    t = clamp(band < 0.18 ? 0.12 : (band < 0.48 ? 0.90 : 0.58) + n * 0.8, 0, 1);
                } else if ("bead".equals(style)) {
                    double dist = Math.hypot(fx - 0.34, fy - 0.30);
                    t = clamp(1.0 - dist * 1.35 + n * 0.3, 0, 1);
                } else if ("crystal".equals(style)) {
                    double dist = Math.hypot(fx - 0.5, fy - 0.5) * 2.0;
                    t = clamp(1.0 - dist * 0.72 + n * 0.2, 0, 1);
                } else if ("vein".equals(style)) {
                    double core = 1.0 - Math.abs(fy - 0.5) * 1.35;
                    t = clamp(0.48 + 0.52 * core + n * 0.2, 0, 1);
                } else if ("cel".equals(style)) {
                    t = clamp(0.32 + 0.20 * fy + n * 0.3, 0, 1);
                } else if ("metal_bevel".equals(style)) {
                    double sheen = 0.58 + 0.32 * (1.0 - fy) + 0.14 * Math.sin((fx * 3.0 + fy * 2.0) * Math.PI);
                    t = clamp(sheen + n * 0.5, 0, 1);
                } else {
                    // Clean brushed anime-cel metallic shading (NO dark cell borders that create
                    // grid lines across slices!)
                    double grad = 0.36 + 0.48 * (1.0 - fx * 0.42 - fy * 0.48);
                    double grain = 0.06 * Math.sin(x * 0.5 + y * 0.3);
                    t = clamp(grad + grain + n * 0.55, 0, 1);
                }

                int rr, gg, bb;
                if (t < 0.5) {
                    double k = t * 2.0;
                    rr = (int) lerp(cDark[0], cMid[0], k);
                    gg = (int) lerp(cDark[1], cMid[1], k);
                    bb = (int) lerp(cDark[2], cMid[2], k);
                } else {
                    double k = (t - 0.5) * 2.0;
                    rr = (int) lerp(cMid[0], cHi[0], k);
                    gg = (int) lerp(cMid[1], cHi[1], k);
                    bb = (int) lerp(cMid[2], cHi[2], k);
                }
                diff.setRGB(x0 + x, y0 + y, rgba(rr, gg, bb, 255));
                // White-grayscale additive intensity masks (runtime vertex-tinted by TogetsuEmissiveLayer!)
                if (mainMaskLevel > 0) {
                    int mv = clampI((int) Math.round(mainMaskLevel * (0.62 + 0.38 * t)));
                    glowMain.setRGB(x0 + x, y0 + y, mv == 0 ? 0 : rgba(mv, mv, mv, 255));
                } else {
                    glowMain.setRGB(x0 + x, y0 + y, 0);
                }
                if (accentMaskLevel > 0) {
                    int av = clampI((int) Math.round(accentMaskLevel * (0.58 + 0.42 * t)));
                    glowAccent.setRGB(x0 + x, y0 + y, av == 0 ? 0 : rgba(av, av, av, 255));
                } else {
                    glowAccent.setRGB(x0 + x, y0 + y, 0);
                }
            }
        }
    }

    static BufferedImage[] buildTextures(String variant) throws Exception {
        boolean isWhite = "waxing".equals(variant);
        BufferedImage diff = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glowMain = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glowAccent = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Random rng = new Random(isWhite ? 44203L : 44204L);

        if (isWhite) {
            fillTile(diff, glowMain, glowAccent, "BASE_PLATE",   new int[]{152,160,170}, new int[]{208,216,226}, new int[]{244,248,252}, 0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "UPPER_PLATE",  new int[]{126,134,146}, new int[]{178,186,198}, new int[]{222,230,240}, 0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "BEVEL_EDGE",   new int[]{182,190,202}, new int[]{228,236,244}, new int[]{252,254,255}, 18,  0, "metal_bevel", rng);
            fillTile(diff, glowMain, glowAccent, "RIDGE_CREST",  new int[]{164,172,184}, new int[]{214,222,232}, new int[]{246,250,255}, 0,   0, "metal_bevel", rng);
            fillTile(diff, glowMain, glowAccent, "SPINE_FACE",   new int[]{118,126,138}, new int[]{166,174,186}, new int[]{208,216,226}, 0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "TASSEL_MAIN",  new int[]{28,31,37},    new int[]{48,52,60},    new int[]{74,79,88},    0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "TASSEL_RIDGE", new int[]{42,46,54},    new int[]{68,74,84},    new int[]{98,104,116},  155, 0, "vein", rng);
            // Dark crystal socket diffuse so EVERY player-chosen glow color stays rich & vivid even in bright daylight!
            fillTile(diff, glowMain, glowAccent, "GLOW_VEIN",    new int[]{22,26,34},    new int[]{34,40,52},    new int[]{54,62,76},    240, 0, "vein", rng);
            fillTile(diff, glowMain, glowAccent, "GLOW_NODE",    new int[]{24,28,36},    new int[]{42,48,60},    new int[]{74,82,96},    255, 0, "crystal", rng);
            fillTile(diff, glowMain, glowAccent, "GLOW_EDGE",    new int[]{92,100,114},  new int[]{126,136,152}, new int[]{166,176,192}, 155, 0, "vein", rng);
            fillTile(diff, glowMain, glowAccent, "GLOW_GUARD",   new int[]{22,26,34},    new int[]{38,44,56},    new int[]{68,76,90},    245, 0, "crystal", rng);
        } else {
            fillTile(diff, glowMain, glowAccent, "BASE_PLATE",   new int[]{34,37,43},    new int[]{54,58,66},    new int[]{76,81,90},    0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "UPPER_PLATE",  new int[]{26,29,34},    new int[]{44,48,56},    new int[]{64,69,78},    0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "BEVEL_EDGE",   new int[]{50,54,62},    new int[]{74,80,90},    new int[]{104,110,122}, 0,   0, "metal_bevel", rng);
            fillTile(diff, glowMain, glowAccent, "RIDGE_CREST",  new int[]{42,46,54},    new int[]{64,69,78},    new int[]{90,96,108},   0,   0, "metal_bevel", rng);
            fillTile(diff, glowMain, glowAccent, "SPINE_FACE",   new int[]{24,27,32},    new int[]{40,44,52},    new int[]{60,65,74},    0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "TASSEL_MAIN",  new int[]{164,170,180}, new int[]{218,224,232}, new int[]{248,250,255}, 0,   0, "metal", rng);
            fillTile(diff, glowMain, glowAccent, "TASSEL_RIDGE", new int[]{192,198,208}, new int[]{236,242,248}, new int[]{254,255,255}, 155, 0, "vein", rng);
            fillTile(diff, glowMain, glowAccent, "GLOW_VEIN",    new int[]{18,20,28},    new int[]{30,34,46},    new int[]{48,54,68},    245, 0, "vein", rng);
            fillTile(diff, glowMain, glowAccent, "GLOW_NODE",    new int[]{20,22,30},    new int[]{36,40,54},    new int[]{66,72,88},    255, 0, "crystal", rng);
            fillTile(diff, glowMain, glowAccent, "GLOW_EDGE",    new int[]{42,46,56},    new int[]{66,72,86},    new int[]{98,106,122},  205, 0, "vein", rng);
            fillTile(diff, glowMain, glowAccent, "GLOW_GUARD",   new int[]{20,22,30},    new int[]{34,38,50},    new int[]{62,68,84},    245, 0, "crystal", rng);
        }

        fillTile(diff, glowMain, glowAccent, "CEL_OUTLINE",  new int[]{12,13,17},    new int[]{18,20,25},    new int[]{26,28,34},    0,   0, "cel", rng);
        fillTile(diff, glowMain, glowAccent, "GUARD_SILVER", new int[]{134,142,154}, new int[]{186,194,206}, new int[]{232,238,246}, 0,   0, "metal_bevel", rng);
        fillTile(diff, glowMain, glowAccent, "GUARD_SHADE",  new int[]{88,96,108},   new int[]{130,138,150}, new int[]{172,180,192}, 0,   0, "metal", rng);
        fillTile(diff, glowMain, glowAccent, "GRIP_LEATHER", new int[]{70,26,22},    new int[]{106,42,35},   new int[]{134,56,46},   0,   0, "leather", rng);
        fillTile(diff, glowMain, glowAccent, "GRIP_WRAP",    new int[]{84,32,27},    new int[]{124,50,41},   new int[]{156,66,54},   0,   0, "wrap", rng);
        fillTile(diff, glowMain, glowAccent, "POMMEL_DARK",  new int[]{32,36,44},    new int[]{52,58,68},    new int[]{76,84,96},    0,   0, "metal", rng);
        fillTile(diff, glowMain, glowAccent, "BEAD_RED",     new int[]{114,14,20},   new int[]{182,26,32},   new int[]{228,60,64},   0,   0, "bead", rng);
        fillTile(diff, glowMain, glowAccent, "GLOW_BEAD",    new int[]{110,14,22},   new int[]{176,24,34},   new int[]{225,58,68},   0, 245, "crystal", rng);

        Path texDir = ASSETS.resolve("textures\\item");
        ImageIO.write(diff, "png", texDir.resolve("togetsu_" + variant + "_model.png").toFile());
        ImageIO.write(glowMain, "png", texDir.resolve("togetsu_" + variant + "_model_glowmask.png").toFile());
        ImageIO.write(glowAccent, "png", texDir.resolve("togetsu_" + variant + "_model_glowmask_accent.png").toFile());
        System.out.println("TEX_" + variant.toUpperCase() + "_OK: 256x256 diffuse + primary/secondary & accent glowmasks");
        return new BufferedImage[]{diff, glowMain};
    }

    // =========================================================================
    // 3. MULTI-ANGLE 3D PREVIEW RENDERER (ALL 5 REF ANGLES + FP DUAL CHECK!)
    // =========================================================================
    static class TriQuad {
        double[][] v = new double[4][3];
        double[][] uv = new double[4][2];
        BufferedImage diff, glow;
    }

    static List<TriQuad> toQuads(Map<String, List<Cube>> boneCubes, BufferedImage diff, BufferedImage glow,
            double[] extraXform) {
        List<TriQuad> out = new ArrayList<>();
        for (List<Cube> list : boneCubes.values()) {
            for (Cube c : list) {
                double x0 = c.ox, y0 = c.oy, z0 = c.oz, x1 = x0 + c.sx, y1 = y0 + c.sy, z1 = z0 + c.sz;
                double[] bLB = { x1, y0, z0 }, bRB = { x1, y0, z1 }, tLB = { x1, y1, z0 }, tRB = { x1, y1, z1 };
                double[] tLF = { x0, y1, z0 }, tRF = { x0, y1, z1 }, bLF = { x0, y0, z0 }, bRF = { x0, y0, z1 };
                double[] R = c.hasRot ? mul(rzM(-c.rz), mul(ryM(c.ry), rxM(-c.rx))) : null;
                double[][][] fverts = {
                        { tRB, tLB, bLB, bRB }, { tLF, tRF, bRF, bLF },
                        { tLB, tLF, bLF, bLB }, { tRF, tRB, bRB, bRF },
                        { tRB, tRF, tLF, tLB }, { bLB, bLF, bRF, bRB }
                };
                int[] rMain = MAT.get(c.mat);
                int[] rSide = c.matSide != null ? MAT.get(c.matSide) : rMain;
                for (int fi = 0; fi < 6; fi++) {
                    int[] ur = (fi == 0 || fi == 1) ? rSide : rMain;
                    TriQuad q = new TriQuad();
                    for (int vi = 0; vi < 4; vi++) {
                        double[] p = fverts[fi][vi].clone();
                        if (R != null) {
                            double[] rel = { p[0] - c.px, p[1] - c.py, p[2] - c.pz };
                            double[] rr = app(R, rel);
                            p = new double[] { rr[0] + c.px, rr[1] + c.py, rr[2] + c.pz };
                        }
                        p = new double[] { -p[0], p[1], p[2] };
                        if (extraXform != null)
                            p = app(extraXform, p);
                        q.v[vi] = p;
                    }
                    double u0 = ur[0] + 2, v0 = ur[1] + 2, uw = ur[2] - 4, vh = ur[3] - 4;
                    q.uv = new double[][] { { u0 + uw, v0 }, { u0, v0 }, { u0, v0 + vh }, { u0 + uw, v0 + vh } };
                    q.diff = diff;
                    q.glow = glow;
                    out.add(q);
                }
            }
        }
        return out;
    }

    static BufferedImage rasterOrtho(List<TriQuad> quads, double[] camM, int w, int h, double pxScale, double[] center,
            String title) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, 0x565A62);
        double[] zbuf = new double[w * h];
        Arrays.fill(zbuf, -1e9);
        double[] ld = { 0.38, 0.72, 0.58 };
        double ldl = Math.sqrt(ld[0] * ld[0] + ld[1] * ld[1] + ld[2] * ld[2]);
        ld[0] /= ldl;
        ld[1] /= ldl;
        ld[2] /= ldl;

        for (TriQuad q : quads) {
            double[][] c = new double[4][3];
            for (int i = 0; i < 4; i++) {
                double[] rel = { q.v[i][0] - center[0], q.v[i][1] - center[1], q.v[i][2] - center[2] };
                c[i] = app(camM, rel);
            }
            double[] e1 = { c[1][0] - c[0][0], c[1][1] - c[0][1], c[1][2] - c[0][2] };
            double[] e2 = { c[3][0] - c[0][0], c[3][1] - c[0][1], c[3][2] - c[0][2] };
            double nx = e1[1] * e2[2] - e1[2] * e2[1], ny = e1[2] * e2[0] - e1[0] * e2[2],
                    nz = e1[0] * e2[1] - e1[1] * e2[0];
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-7)
                continue;
            double shade = 0.56 + 0.44 * Math.abs((nx * ld[0] + ny * ld[1] + nz * ld[2]) / nl);

            double[][] scr = new double[4][3];
            for (int i = 0; i < 4; i++) {
                scr[i][0] = w * 0.5 + c[i][0] * pxScale;
                scr[i][1] = h * 0.52 - c[i][1] * pxScale;
                scr[i][2] = c[i][2];
            }
            for (int[] tri : new int[][] { { 0, 1, 2 }, { 0, 2, 3 } }) {
                double[] p0 = scr[tri[0]], p1 = scr[tri[1]], p2 = scr[tri[2]];
                int x0 = Math.max(0, (int) Math.floor(Math.min(p0[0], Math.min(p1[0], p2[0]))));
                int x1 = Math.min(w - 1, (int) Math.ceil(Math.max(p0[0], Math.max(p1[0], p2[0]))));
                int y0 = Math.max(0, (int) Math.floor(Math.min(p0[1], Math.min(p1[1], p2[1]))));
                int y1 = Math.min(h - 1, (int) Math.ceil(Math.max(p0[1], Math.max(p1[1], p2[1]))));
                if (x0 > x1 || y0 > y1)
                    continue;
                double area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1]);
                if (Math.abs(area) < 1e-4)
                    continue;
                double invA = 1.0 / area;
                for (int py = y0; py <= y1; py++) {
                    double gy = py + 0.5;
                    for (int px = x0; px <= x1; px++) {
                        double gx = px + 0.5;
                        double w0 = ((p1[0] - gx) * (p2[1] - gy) - (p2[0] - gx) * (p1[1] - gy)) * invA;
                        double w1 = ((p2[0] - gx) * (p0[1] - gy) - (p0[0] - gx) * (p2[1] - gy)) * invA;
                        double w2 = 1.0 - w0 - w1;
                        if (w0 < -1e-4 || w1 < -1e-4 || w2 < -1e-4)
                            continue;
                        double z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2];
                        int bi = py * w + px;
                        if (z <= zbuf[bi])
                            continue;
                        double[] t0 = q.uv[tri[0]], t1 = q.uv[tri[1]], t2 = q.uv[tri[2]];
                        int u = clampI((int) (w0 * t0[0] + w1 * t1[0] + w2 * t2[0]));
                        int v = clampI((int) (w0 * t0[1] + w1 * t1[1] + w2 * t2[1]));
                        int argb = q.diff.getRGB(u, v);
                        if (((argb >>> 24) & 255) < 26)
                            continue;
                        int dr = (argb >> 16) & 255, dg = (argb >> 8) & 255, db = argb & 255;
                        int gargb = q.glow.getRGB(u, v);
                        double ga = ((gargb >>> 24) & 255) / 255.0;
                        int gr = (gargb >> 16) & 255, gg = (gargb >> 8) & 255, gb = gargb & 255;
                        int rr = clampI((int) (dr * shade * (1 - ga * 0.45) + gr * ga * 0.75));
                        int ggg = clampI((int) (dg * shade * (1 - ga * 0.45) + gg * ga * 0.75));
                        int bb = clampI((int) (db * shade * (1 - ga * 0.45) + gb * ga * 0.75));
                        zbuf[bi] = z;
                        img.setRGB(px, py, (rr << 16) | (ggg << 8) | bb);
                    }
                }
            }
        }
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRect(0, 0, w, 22);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.drawString(title, 8, 15);
        g2.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception {
        Map<String, List<Cube>> bones = buildDualBladeBones();
        writeGeoJson("waxing", bones);
        writeGeoJson("waning", bones);
        BufferedImage[] wTex = buildTextures("waxing");
        BufferedImage[] kTex = buildTextures("waning");

        List<TriQuad> qW = toQuads(bones, wTex[0], wTex[1], null);
        List<TriQuad> qK = toQuads(bones, kTex[0], kTex[1], null);

        List<TriQuad> pairSide = new ArrayList<>();
        pairSide.addAll(toQuads(bones, kTex[0], kTex[1], mul(trans(0, 12.0, -4.8), rxM(180))));
        pairSide.addAll(toQuads(bones, wTex[0], wTex[1], trans(0, 0.0, +4.8)));

        List<TriQuad> pairTilt = new ArrayList<>();
        pairTilt.addAll(toQuads(bones, kTex[0], kTex[1], mul(trans(-2.0, 12.0, -5.2), rxM(180))));
        pairTilt.addAll(toQuads(bones, wTex[0], wTex[1], trans(+2.0, 0.0, +5.2)));

        int pw = 560, ph = 380;
        BufferedImage[] panels = {
                rasterOrtho(pairSide, mul(rzM(45), ryM(90)), pw, ph, 11.8, new double[] { 0, 6.0, 0 },
                        "1. SIDE PROFILE PAIR (Ref Image 1)"),
                rasterOrtho(qW, mul(rzM(90), ryM(0)), pw, ph, 15.2, new double[] { 0, 6.0, 0 },
                        "2. TOP-DOWN SPINE VIEW (Ref Image 2 - Thickness & Taper)"),
                rasterOrtho(pairTilt, mul(rxM(24), mul(rzM(88), ryM(72))), pw, ph, 12.0, new double[] { 0, 6.0, 0 },
                        "3. TILTED 3/4 SIDE-SPINE (Ref Image 3 - Stepped S-Groove)"),
                rasterOrtho(qW, mul(rxM(20), mul(ryM(46), rzM(74))), pw, ph, 16.5, new double[] { 0, 5.5, -1.0 },
                        "4. FRONT-HEEL 3/4 BEVEL & U-THROAT (Ref Image 4)"),
                rasterOrtho(qK, mul(rxM(22), mul(ryM(-138), rzM(72))), pw, ph, 16.0, new double[] { 0, 4.5, 0.0 },
                        "5. REAR-SPINE 3/4 POMMEL/BEAD/GUARD (Ref Image 5)"),
                rasterOrtho(qW, mul(rxM(66), ryM(22)), pw, ph, 21.0, new double[] { 0, 9.0, -1.0 },
                        "6. DIAMOND BEVEL & STEPPED PLATE CLOSE-UP")
        };

        BufferedImage sheet = new BufferedImage(pw * 3, ph * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = sheet.createGraphics();
        for (int i = 0; i < 6; i++) {
            g2.drawImage(panels[i], (i % 3) * pw, (i / 3) * ph, null);
        }
        g2.dispose();
        Files.createDirectories(PREVIEW_OUT.getParent());
        ImageIO.write(sheet, "png", PREVIEW_OUT.toFile());
        ImageIO.write(sheet, "png", OUT_DIR.resolve("togetsu_dual_3d_preview_v2.png").toFile());
        System.out.println("PREVIEW_V2_OK: " + PREVIEW_OUT);
    }
}
