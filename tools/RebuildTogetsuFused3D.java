import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Rebuilds the combined greatsword / crescent twin-blade **Song Nguyệt (Sōgengetsu — togetsu_fused.geo.json)**
 * and its 256x256 cel-shaded texture + glowmask into TRUE sculpted 3D geometry matching all 7 multi-angle
 * reference renders (v2 — refined crescent curve, exact S-stepped overlapping white armor shells,
 * zero Z-fighting, seamless rear dark reverse-hook wing locked in `blade_root` for zero First-Person gap,
 * clean beveled diamond spine, and subtle cel-shaded outlines).
 */
public class RebuildTogetsuFused3D {
    static final Path MOD_ROOT = Path.of("D:\\cremod\\mod-rpg");
    static final Path ASSETS = MOD_ROOT.resolve("src\\main\\resources\\assets\\abyssalrpg");
    static final Path OUT_DIR = Path.of("C:\\Users\\lucka\\.gemini\\antigravity\\brain\\6b270670-349b-4122-a74d-bf2d4d46e806\\scratch");
    static final Path PREVIEW_OUT = MOD_ROOT.resolve("tools\\togetsu_previews\\togetsu_fused_3d_rebuild_preview.png");

    static final int TEX_W = 256;
    static final int TEX_H = 256;

    static final Map<String, int[]> MAT = new LinkedHashMap<>();
    static {
        // Row 0 (y=0..31)
        MAT.put("WHITE_PLATE",  new int[]{0,   0, 32, 32});
        MAT.put("WHITE_BEVEL",  new int[]{32,  0, 32, 32});
        MAT.put("WHITE_SPINE",  new int[]{64,  0, 32, 32});
        MAT.put("DARK_CORE",    new int[]{96,  0, 32, 32});
        MAT.put("DARK_BEVEL",   new int[]{128, 0, 32, 32});
        MAT.put("DARK_SPINE",   new int[]{160, 0, 32, 32});
        MAT.put("CEL_OUTLINE",  new int[]{192, 0, 32, 32});
        MAT.put("GUARD_SILVER", new int[]{224, 0, 32, 32});
        // Row 1 (y=32..63)
        MAT.put("GUARD_SHADE",  new int[]{0,  32, 32, 32});
        MAT.put("GRIP_LEATHER", new int[]{32, 32, 32, 32});
        MAT.put("GRIP_WRAP",    new int[]{64, 32, 32, 32});
        MAT.put("POMMEL_DARK",  new int[]{96, 32, 32, 32});
        MAT.put("BEAD_RED",     new int[]{128,32, 32, 32});
        MAT.put("TASSEL_W",     new int[]{160,32, 32, 32});
        MAT.put("TASSEL_WR",    new int[]{192,32, 32, 32});
        MAT.put("TASSEL_K",     new int[]{224,32, 32, 32});
        // Row 2 (y=64..95)
        MAT.put("TASSEL_KR",    new int[]{0,  64, 32, 32});
        MAT.put("WHITE_STEP",   new int[]{32, 64, 32, 32});
        // Row 3 (y=96..127) — Dedicated 3D Multi-Channel Luminous Accent Tiles (runtime-tinted!)
        MAT.put("GLOW_VEIN_A",  new int[]{0,  96, 32, 32}); // Primary channel seams & talon ridge
        MAT.put("GLOW_VEIN_B",  new int[]{32, 96, 32, 32}); // Secondary channel seams (tip cap & dark core side)
        MAT.put("GLOW_NODE_A",  new int[]{64, 96, 32, 32}); // Primary raised 3D diamond jewels (white plates)
        MAT.put("GLOW_NODE_B",  new int[]{96, 96, 32, 32}); // Secondary raised 3D crystals (Hero Eclipse Core + stars + rear wing)
        MAT.put("GLOW_EDGE_A",  new int[]{128,96, 32, 32}); // Primary luminous crescent cutting edge
        MAT.put("GLOW_EDGE_B",  new int[]{160,96, 32, 32}); // Secondary luminous dark-window & rear hook edge
        MAT.put("GLOW_GUARD",   new int[]{192,96, 32, 32}); // Silver bolster inlaid crystal stud (Primary)
        MAT.put("GLOW_BEAD",    new int[]{224,96, 32, 32}); // Twin faceted glowing ruby pommel beads (Accent)
    }

    static class Cube {
        double ox, oy, oz;
        double sx, sy, sz;
        double px, py, pz;
        double rx, ry, rz;
        boolean hasRot;
        String mat;

        static Cube centered(double cx, double cy, double cz, double sx, double sy, double sz,
                             String mat, double rx, double ry, double rz) {
            Cube c = new Cube();
            c.ox = cx - sx * 0.5;
            c.oy = cy - sy * 0.5;
            c.oz = cz - sz * 0.5;
            c.sx = Math.max(0.04, sx);
            c.sy = Math.max(0.04, sy);
            c.sz = Math.max(0.04, sz);
            c.px = cx; c.py = cy; c.pz = cz;
            c.rx = rx; c.ry = ry; c.rz = rz;
            c.hasRot = (Math.abs(rx) > 1e-3 || Math.abs(ry) > 1e-3 || Math.abs(rz) > 1e-3);
            c.mat = mat;
            return c;
        }
    }

    // --- Matrix helpers & Exact Bedrock Euler Decomposition ---
    static double[] id() { double[] m = new double[16]; m[0]=m[5]=m[10]=m[15]=1; return m; }
    static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i=0;i<4;i++) for (int j=0;j<4;j++) for (int k=0;k<4;k++) r[i*4+j]+=a[i*4+k]*b[k*4+j];
        return r;
    }
    static double[] trans(double x, double y, double z) { double[] m=id(); m[3]=x; m[7]=y; m[11]=z; return m; }
    static double[] scaleM(double x, double y, double z) { double[] m=id(); m[0]=x; m[5]=y; m[10]=z; return m; }
    static double[] rxM(double d) { double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r),m[]=id(); m[5]=c;m[6]=-s;m[9]=s;m[10]=c; return m; }
    static double[] ryM(double d) { double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r),m[]=id(); m[0]=c;m[2]=s;m[8]=-s;m[10]=c; return m; }
    static double[] rzM(double d) { double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r),m[]=id(); m[0]=c;m[1]=-s;m[4]=s;m[5]=c; return m; }
    static double[] app(double[] m, double[] p) {
        return new double[]{m[0]*p[0]+m[1]*p[1]+m[2]*p[2]+m[3], m[4]*p[0]+m[5]*p[1]+m[6]*p[2]+m[7], m[8]*p[0]+m[9]*p[1]+m[10]*p[2]+m[11]};
    }

    /** Exact Bedrock Euler [rx, ry, rz] (deg) giving Rz(-rz)*Ry(ry)*Rx(-rx) = Rx(-pitchX) * Ry(localBevelY). */
    static double[] bedrockEulerForLocalBevel(double pitchXDeg, double localBevelYDeg) {
        double[] M = mul(rxM(-pitchXDeg), ryM(localBevelYDeg));
        double sb = clamp(-M[8], -1.0, 1.0);
        double b = Math.asin(sb);
        double c = Math.atan2(M[9], M[10]);
        double a = Math.atan2(M[4], M[0]);
        return new double[]{-Math.toDegrees(c), Math.toDegrees(b), -Math.toDegrees(a)};
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double[] bezier3(double[][] P, double t) {
        double u = 1.0 - t;
        double b0 = u*u*u, b1 = 3*u*u*t, b2 = 3*u*t*t, b3 = t*t*t;
        return new double[]{
            b0*P[0][0] + b1*P[1][0] + b2*P[2][0] + b3*P[3][0],
            b0*P[0][1] + b1*P[1][1] + b2*P[2][1] + b3*P[3][1]
        };
    }

    /** Octagonal chamfered 3D tube along YZ points. */
    static void addOctagonalTube(List<Cube> out, double[][] ptsYZ, double[] wx, double[] dz,
                                 String mat, String rimMat, boolean addThinRim) {
        for (int i = 0; i < ptsYZ.length - 1; i++) {
            double ya = ptsYZ[i][0], za = ptsYZ[i][1];
            double yb = ptsYZ[i + 1][0], zb = ptsYZ[i + 1][1];
            double ym = 0.5 * (ya + yb), zm = 0.5 * (za + zb);
            double len = Math.hypot(yb - ya, zb - za) * 1.18;
            double rx = -Math.toDegrees(Math.atan2(zb - za, yb - ya));
            double w = 0.5 * (wx[i] + wx[i + 1]);
            double d = 0.5 * (dz[i] + dz[i + 1]);
            double eps = (i % 2 == 1) ? 0.013 : 0.0;
            out.add(Cube.centered(0.0, ym, zm, (w + eps) * 1.00, len, d * 0.72, mat, rx, 0, 0));
            out.add(Cube.centered(0.0, ym, zm, (w + eps) * 0.72, len * 0.992, d * 1.00, mat, rx, 0, 0));
            if (addThinRim && rimMat != null) {
                double rxRad = Math.toRadians(rx);
                // Offset perpendicular to segment toward -Z (concave outer rim of talon)
                double ny = -Math.sin(rxRad), nz = Math.cos(rxRad);
                out.add(Cube.centered(0.0, ym - ny * (d * 0.51), zm - nz * (d * 0.51),
                        Math.max(0.09, w * 0.22), len * 0.96, 0.08, rimMat, rx, 0, 0));
            }
        }
    }

    /** 3D Faceted Spherical Bead (3 orthogonal chamfered boxes). */
    static void addFacetedSphere(List<Cube> out, double cx, double cy, double cz, double r, String mat) {
        double a = r * 1.92, b = r * 1.38;
        out.add(Cube.centered(cx, cy, cz, a, b, b, mat, 0, 0, 0));
        out.add(Cube.centered(cx, cy, cz, b, a, b, mat, 0, 0, 0));
        out.add(Cube.centered(cx, cy, cz, b, b, a, mat, 0, 0, 0));
    }

    /** Raised 3D faceted glowing diamond crystal stud with dark bezel protruding cleanly on BOTH +-X blade faces! */
    static void addRaisedGlowDiamond(List<Cube> out, double cy, double cz, double halfX,
                                     double sizeY, double sizeZ, double rxPitch, String glowMat) {
        out.add(Cube.centered(0.0, cy, cz, (halfX + 0.06) * 2.0, sizeY * 1.18, sizeZ * 1.18, "CEL_OUTLINE", rxPitch + 45.0, 0, 0));
        out.add(Cube.centered(0.0, cy, cz, (halfX + 0.14) * 2.0, sizeY, sizeZ, glowMat, rxPitch + 45.0, 0, 0));
        out.add(Cube.centered(0.0, cy, cz, (halfX + 0.19) * 2.0, sizeY * 0.58, sizeZ * 0.58, glowMat, rxPitch + 45.0, 0, 0));
    }

    /** Sculpt a 3D S-curved leaf tassel with raised midrib + beveled margins + crisp cel outline. */
    static void addLeafTassel(List<Cube> out, double[][] ptsYZ, double[] wZ, double[] tX, String mainMat, String ridgeMat) {
        for (int i = 0; i < ptsYZ.length - 1; i++) {
            double ya = ptsYZ[i][0], za = ptsYZ[i][1];
            double yb = ptsYZ[i + 1][0], zb = ptsYZ[i + 1][1];
            double ym = 0.5 * (ya + yb), zm = 0.5 * (za + zb);
            double len = Math.hypot(yb - ya, zb - za) * 1.20;
            double rx = -Math.toDegrees(Math.atan2(zb - za, yb - ya));
            double wz = 0.5 * (wZ[i] + wZ[i + 1]);
            double tx = 0.5 * (tX[i] + tX[i + 1]);
            double eps = (i % 2 == 1) ? 0.011 : 0.0;
            double rxRad = Math.toRadians(rx);
            double ny = -Math.sin(rxRad), nz = Math.cos(rxRad);
            out.add(Cube.centered(0.0, ym, zm, tx + eps, len, Math.max(0.16, wz * 0.36), ridgeMat, rx, 0, 0));
            out.add(Cube.centered(0.0, ym, zm, tx * 0.52 + eps, len * 0.99, wz, mainMat, rx, 0, 0));
            out.add(Cube.centered(0.0, ym - ny * (wz * 0.51), zm - nz * (wz * 0.51), Math.max(0.08, tx * 0.28), len * 0.95, 0.08, "CEL_OUTLINE", rx, 0, 0));
            out.add(Cube.centered(0.0, ym + ny * (wz * 0.51), zm + nz * (wz * 0.51), Math.max(0.08, tx * 0.28), len * 0.95, 0.08, "CEL_OUTLINE", rx, 0, 0));
        }
    }

    // =========================================================================
    // 1. EXACT PARAMETRIC CRESCENT CURVES FOR FUSED SŌGENGETSU (REF IMAGES 1-7 + FP!)
    // =========================================================================
    // Primary grip centered at (0, 0, 0), running y = -3.5 .. +4.3 along z ~ 0.
    // Secondary grip curves out to +Z (z ~ +3.4), plugging into the rounded dark charcoal lobe.
    // Main Crescent Blade arcs boldly forward toward -Z (matching BOTH Ref Images 1, 3, 5, 7 AND
    // the original togetsu_fused First-Person framing!).

    static double[] mainOuterYZ(double s) {
        s = clamp(s, 0.0, 1.0);
        // Deep sweeping outer convex moon edge (matches Ref Images 1, 3, 5, 7 AND frames beautifully in First Person!)
        double[][] P = {
            { 3.60, +6.65},
            {12.80, +6.65},
            {21.20, -2.55},
            {24.20, -12.40}
        };
        return bezier3(P, s);
    }

    static double[] mainInnerYZ(double s) {
        s = clamp(s, 0.0, 1.0);
        // Deep concave inner crescent edge hugging primary bolster & silver talon -> dual-layered tip
        double[][] P = {
            { 4.45, +0.20},
            {11.00, -1.45},
            {18.00, -5.20},
            {24.20, -12.40}
        };
        return bezier3(P, s);
    }

    /** Core half-thickness (X) of the sculpted charcoal inner blade along s in [0, 1] (Ref Images 2, 4, 6). */
    static double coreHalfThick(double s) {
        s = clamp(s, 0.0, 1.0);
        double swell = 1.00 + 0.14 * Math.sin(Math.PI * Math.min(1.0, s * 1.25));
        double taper = Math.pow(Math.max(0.0, 1.0 - Math.pow(s, 1.50)), 0.72);
        return Math.max(0.08, 0.92 * swell * taper);
    }

    /** Build a sculpted 3D beveled slice between quad corners (inA, outA) -> (inB, outB) with exact Bedrock Euler bevels! */
    static void addBeveledSlice(List<Cube> bodyOut, List<Cube> edgeOut,
                                double[] inA, double[] outA, double[] inB, double[] outB,
                                double ridgeHalf, double innerHalf, double outerHalf,
                                double ridgeFrac,
                                String bodyMat, String bevelOuterMat, String bevelInnerMat, String spineMat,
                                boolean addOuterRim, boolean addInnerRim, double eps) {
        addBeveledSliceWithRim(bodyOut, edgeOut, inA, outA, inB, outB,
                ridgeHalf, innerHalf, outerHalf, ridgeFrac,
                bodyMat, bevelOuterMat, bevelInnerMat, spineMat,
                addOuterRim ? "CEL_OUTLINE" : null, addInnerRim ? "CEL_OUTLINE" : null, eps);
    }

    static void addBeveledSliceWithRim(List<Cube> bodyOut, List<Cube> edgeOut,
                                       double[] inA, double[] outA, double[] inB, double[] outB,
                                       double ridgeHalf, double innerHalf, double outerHalf,
                                       double ridgeFrac,
                                       String bodyMat, String bevelOuterMat, String bevelInnerMat, String spineMat,
                                       String outerRimMat, String innerRimMat, double eps) {
        double[] midIn  = {0.5*(inA[0]+inB[0]),   0.5*(inA[1]+inB[1])};
        double[] midOut = {0.5*(outA[0]+outB[0]), 0.5*(outA[1]+outB[1])};
        double wy = midOut[0] - midIn[0], wz = midOut[1] - midIn[1];
        double width = Math.hypot(wy, wz);
        if (width < 0.16) return;

        double[] cA = {lerp(inA[0], outA[0], ridgeFrac), lerp(inA[1], outA[1], ridgeFrac)};
        double[] cB = {lerp(inB[0], outB[0], ridgeFrac), lerp(inB[1], outB[1], ridgeFrac)};
        double ty = cB[0] - cA[0], tz = cB[1] - cA[1];
        double segLen = Math.hypot(ty, tz) * 1.20;
        if (segLen < 0.07) return;
        double rxMid = -Math.toDegrees(Math.atan2(tz, ty));

        double uy = wy / width, uz = wz / width;
        double oty = outB[0] - outA[0], otz = outB[1] - outA[1];
        double segLenOut = Math.hypot(oty, otz) * 1.22;
        double rxOut = -Math.toDegrees(Math.atan2(otz, oty));
        double ity = inB[0] - inA[0], itz = inB[1] - inA[1];
        double segLenIn = Math.hypot(ity, itz) * 1.22;
        double rxIn = -Math.toDegrees(Math.atan2(itz, ity));

        double fInEnd  = Math.min(0.20, ridgeFrac * 0.44);
        double fOutBeg = Math.max(0.78, ridgeFrac + (1.0 - ridgeFrac) * 0.56);

        double[] pRidge  = {midIn[0] + uy * (width * ridgeFrac), midIn[1] + uz * (width * ridgeFrac)};
        double[] pInMid  = {midIn[0] + uy * (width * (0.5 * fInEnd)), midIn[1] + uz * (width * (0.5 * fInEnd))};
        double[] pOutMid = {midIn[0] + uy * (width * (0.5 * (fOutBeg + 1.0))), midIn[1] + uz * (width * (0.5 * (fOutBeg + 1.0)))};

        double dInEdge   = Math.max(0.14, width * fInEnd);
        double dOutEdge  = Math.max(0.14, width * (1.0 - fOutBeg));
        double dSlopeIn  = Math.max(0.14, width * (ridgeFrac - fInEnd * 0.72));
        double dSlopeOut = Math.max(0.14, width * (fOutBeg + (1.0 - fOutBeg) * 0.28 - ridgeFrac));

        double[] pSlopeIn  = {midIn[0] + uy * (width * (fInEnd * 0.72) + dSlopeIn * 0.5),
                              midIn[1] + uz * (width * (fInEnd * 0.72) + dSlopeIn * 0.5)};
        double[] pSlopeOut = {midIn[0] + uy * (width * ridgeFrac + dSlopeOut * 0.5),
                              midIn[1] + uz * (width * ridgeFrac + dSlopeOut * 0.5)};

        // Local +Z direction under Rx(-rxMid) is (-sin(rxMid), cos(rxMid))
        double rxRad = Math.toRadians(rxMid);
        double locZy = -Math.sin(rxRad), locZz = Math.cos(rxRad);
        double zSign = (uy * locZy + uz * locZz >= 0.0) ? +1.0 : -1.0;

        // 1. Raised Central Ridge Crest (clean bright bevel diamond spine!)
        bodyOut.add(Cube.centered(0.0, pRidge[0], pRidge[1],
                ridgeHalf * 2.02 + eps, segLen, Math.max(0.22, width * 0.20),
                spineMat, rxMid, 0, 0));

        // 2. Inner Bevel Slope (inner edge -> central ridge) + watertight solid core
        bodyOut.add(Cube.centered(0.0, pSlopeIn[0], pSlopeIn[1],
                Math.max(0.12, (innerHalf + ridgeHalf) * 0.90) + eps, segLen * 0.99, dSlopeIn * 1.02,
                bodyMat, rxMid, 0, 0));
        if (dSlopeIn > 0.22 && (ridgeHalf - innerHalf) > 0.07) {
            double rise = ridgeHalf - innerHalf;
            double slLen = Math.hypot(dSlopeIn, rise) * 1.05;
            double bYaw = Math.toDegrees(Math.atan2(rise, dSlopeIn)) * zSign;
            double slabT = Math.max(0.16, Math.min(0.32, ridgeHalf * 0.30));
            double xOff = 0.5 * (innerHalf + ridgeHalf) - slabT * 0.40;
            double[] eP = bedrockEulerForLocalBevel(rxMid, +bYaw);
            double[] eN = bedrockEulerForLocalBevel(rxMid, -bYaw);
            bodyOut.add(Cube.centered(+xOff, pSlopeIn[0], pSlopeIn[1], slabT + eps*0.5, segLen, slLen, bodyMat, eP[0], eP[1], eP[2]));
            bodyOut.add(Cube.centered(-xOff, pSlopeIn[0], pSlopeIn[1], slabT + eps*0.5, segLen, slLen, bodyMat, eN[0], eN[1], eN[2]));
        }

        // 3. Outer Bevel Slope (central ridge -> razor outer cutting edge) + watertight solid core
        bodyOut.add(Cube.centered(0.0, pSlopeOut[0], pSlopeOut[1],
                Math.max(0.12, (outerHalf + ridgeHalf) * 0.86) + eps, segLen * 0.99, dSlopeOut * 1.02,
                bodyMat, rxMid, 0, 0));
        if (dSlopeOut > 0.22 && (ridgeHalf - outerHalf) > 0.07) {
            double rise = ridgeHalf - outerHalf;
            double slLen = Math.hypot(dSlopeOut, rise) * 1.05;
            double bYaw = Math.toDegrees(Math.atan2(rise, dSlopeOut)) * (-zSign);
            double slabT = Math.max(0.16, Math.min(0.32, ridgeHalf * 0.30));
            double xOff = 0.5 * (outerHalf + ridgeHalf) - slabT * 0.40;
            double[] eP = bedrockEulerForLocalBevel(rxMid, +bYaw);
            double[] eN = bedrockEulerForLocalBevel(rxMid, -bYaw);
            bodyOut.add(Cube.centered(+xOff, pSlopeOut[0], pSlopeOut[1], slabT + eps*0.5, segLen, slLen, bevelOuterMat, eP[0], eP[1], eP[2]));
            bodyOut.add(Cube.centered(-xOff, pSlopeOut[0], pSlopeOut[1], slabT + eps*0.5, segLen, slLen, bevelOuterMat, eN[0], eN[1], eN[2]));
        }

        // 4. Inner Edge Strip + Subtle Rim
        bodyOut.add(Cube.centered(0.0, pInMid[0], pInMid[1],
                innerHalf * 2.0 + eps, segLenIn, dInEdge * 1.06,
                bevelInnerMat, rxIn, 0, 0));
        if (innerRimMat != null) {
            bodyOut.add(Cube.centered(0.0, midIn[0] - uy * 0.04, midIn[1] - uz * 0.04,
                    Math.max(0.09, Math.min(0.18, innerHalf * 0.72)), segLenIn * 0.98, 0.11,
                    innerRimMat, rxIn, 0, 0));
        }

        // 5. Razor Outer Cutting Edge Strip + Crisp Luminous or Cel Rim
        edgeOut.add(Cube.centered(0.0, pOutMid[0], pOutMid[1],
                outerHalf * 2.0 + eps, segLenOut, dOutEdge * 1.08,
                bevelOuterMat, rxOut, 0, 0));
        if (outerRimMat != null) {
            boolean glowRim = outerRimMat.startsWith("GLOW_");
            edgeOut.add(Cube.centered(0.0, midOut[0] + uy * (glowRim ? 0.06 : 0.05), midOut[1] + uz * (glowRim ? 0.06 : 0.05),
                    Math.max(glowRim ? 0.10 : 0.07, Math.min(glowRim ? 0.18 : 0.14, outerHalf * (glowRim ? 0.95 : 0.72))),
                    segLenOut * 0.99, glowRim ? 0.15 : 0.08,
                    outerRimMat, rxOut, 0, 0));
        }
    }

    /** Crisp tapered 3D recessed panel seam with inlaid glowing energy vein! */
    static void addTaperedGlowingSeam(List<Cube> out, double[] pA, double[] pB, double halfXA, double halfXB, String glowMat) {
        final int SUB = 4;
        for (int i = 0; i < SUB; i++) {
            double ta = i / (double) SUB, tb = (i + 1) / (double) SUB, tm = 0.5 * (ta + tb);
            double ya = lerp(pA[0], pB[0], ta), za = lerp(pA[1], pB[1], ta);
            double yb = lerp(pA[0], pB[0], tb), zb = lerp(pA[1], pB[1], tb);
            double len = Math.hypot(yb - ya, zb - za) * 1.05;
            if (len < 0.06) continue;
            double rx = -Math.toDegrees(Math.atan2(zb - za, yb - ya));
            double hx = lerp(halfXA, halfXB, tm);
            out.add(Cube.centered(0.0, 0.5 * (ya + yb), 0.5 * (za + zb), Math.max(0.12, hx * 2.0 + 0.03), len, 0.24, "CEL_OUTLINE", rx, 0, 0));
            out.add(Cube.centered(0.0, 0.5 * (ya + yb), 0.5 * (za + zb), Math.max(0.14, (hx + 0.055) * 2.0), len * 1.01, 0.14, glowMat, rx, 0, 0));
        }
    }

    /** Thin glowing seam ribbon framed by dark cel bezel along a longitudinal overlapping plate edge. */
    static void addGlowingSeamRibbon(List<Cube> out, double[] pA, double[] pB, double halfX, String glowMat) {
        addTaperedGlowingSeam(out, pA, pB, halfX, halfX, glowMat);
    }

    static Map<String, List<Cube>> buildFusedSogengetsuBones() {
        List<Cube> rootCubes = new ArrayList<>();
        List<Cube> bodyCubes = new ArrayList<>();
        List<Cube> edgeCubes = new ArrayList<>();

        // =====================================================================
        // A. DOUBLE-HILT & DUAL-GRIP ASSEMBLY (`blade_root` — Ref Images 1, 3, 5, 7!)
        // =====================================================================

        // A1. Primary Curved Red-Wrapped Handle (grip center (0,0,0) -> silver bolster y=+4.4!)
        double[][] priGrip = {
            {-3.50, +0.34}, {-2.05, +0.10}, {-0.60, -0.04}, {+0.85, +0.00}, {+2.40, +0.14}, {+4.20, +0.28}
        };
        double[] priW = {1.12, 1.24, 1.32, 1.30, 1.24, 1.18};
        double[] priD = {1.18, 1.30, 1.38, 1.36, 1.30, 1.24};
        addOctagonalTube(rootCubes, priGrip, priW, priD, "GRIP_LEATHER", null, false);
        double[][] priWraps = {
            {-2.60, +0.20, -8}, {-1.60, +0.05, -6}, {-0.60, -0.04, -3},
            {+0.45, -0.01, -4}, {+1.50, +0.06, -6}, {+2.55, +0.16, -7}, {+3.50, +0.24, -8}
        };
        for (int i = 0; i < priWraps.length; i++) {
            double wy = priWraps[i][0], wz = priWraps[i][1], rx = priWraps[i][2];
            double eps = (i % 2 == 1) ? 0.015 : 0.0;
            rootCubes.add(Cube.centered(0.0, wy, wz, 1.40 + eps, 0.42, 1.38, "GRIP_WRAP", rx, 0, (i % 2 == 0) ? 14 : -14));
            rootCubes.add(Cube.centered(0.0, wy - 0.23, wz + 0.02, 1.28 + eps, 0.10, 1.28, "CEL_OUTLINE", rx, 0, (i % 2 == 0) ? 12 : -12));
        }

        // A2. Secondary Diverging Curved Red-Wrapped Handle (from shared hollow pommel -> dark rear socket lobe!)
        double[][] secGrip = {
            {-3.55, +0.62}, {-3.05, +1.98}, {-1.90, +3.08}, {-0.30, +3.52}, {+1.35, +3.54}, {+2.85, +3.32}, {+3.95, +2.98}
        };
        double[] secW = {1.06, 1.12, 1.18, 1.20, 1.18, 1.16, 1.18};
        double[] secD = {1.12, 1.18, 1.24, 1.26, 1.24, 1.22, 1.26};
        addOctagonalTube(rootCubes, secGrip, secW, secD, "GRIP_LEATHER", null, false);
        for (int i = 1; i < secGrip.length - 1; i++) {
            double ya = secGrip[i - 1][0], za = secGrip[i - 1][1];
            double yb = secGrip[i + 1][0], zb = secGrip[i + 1][1];
            double rx = -Math.toDegrees(Math.atan2(zb - za, yb - ya));
            double eps = (i % 2 == 1) ? 0.014 : 0.0;
            rootCubes.add(Cube.centered(0.0, secGrip[i][0], secGrip[i][1], 1.28 + eps, 0.42, 1.28, "GRIP_WRAP", rx, 0, (i % 2 == 0) ? 14 : -14));
            rootCubes.add(Cube.centered(0.0, secGrip[i][0] - 0.16, secGrip[i][1] + 0.05, 1.18 + eps, 0.10, 1.18, "CEL_OUTLINE", rx, 0, 0));
        }
        // Small silver accent recess & dark collar right where secondary grip enters dark rear lobe (Ref 1, 3, 5!)
        rootCubes.add(Cube.centered(0.0, 3.68, +3.10, 1.34, 0.54, 1.28, "POMMEL_DARK", 16, 0, 0));
        rootCubes.add(Cube.centered(0.0, 3.85, +3.62, 0.96, 0.42, 0.56, "GUARD_SHADE", 12, 0, 0));

        // A3. Shared Chamfered Black Pommel Block WITH REAL OPEN CENTER EYELET HOLE (Ref Images 1, 3, 5, 7!)
        double pcY = -4.10, pcZ = +0.52, pcRx = -10.0;
        rootCubes.add(Cube.centered(0.0, -3.50, +0.44, 1.38, 0.42, 1.72, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, -4.70, +0.62, 1.32, 0.42, 1.60, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ - 0.58, 1.34, 0.94, 0.46, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ + 0.58, 1.34, 0.94, 0.46, "POMMEL_DARK", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ - 0.80, 0.96, 0.86, 0.10, "CEL_OUTLINE", pcRx, 0, 0));
        rootCubes.add(Cube.centered(0.0, pcY, pcZ + 0.80, 0.96, 0.86, 0.10, "CEL_OUTLINE", pcRx, 0, 0));

        // A4. TWO Faceted 3D Spherical Luminous Ruby Beads in series!
        rootCubes.add(Cube.centered(0.0, -4.90, +0.65, 0.36, 0.20, 0.36, "CEL_OUTLINE", pcRx, 0, 0));
        addFacetedSphere(rootCubes, 0.0, -5.28, +0.70, 0.46, "GLOW_BEAD");
        addFacetedSphere(rootCubes, 0.0, -6.02, +0.80, 0.43, "GLOW_BEAD");
        rootCubes.add(Cube.centered(0.0, -6.46, +0.86, 0.30, 0.20, 0.30, "CEL_OUTLINE", 0, 0, 0));

        // A5. DUAL Diverging S-Curved 3D Ridged Leaf Tassels (Pearl-White on -Z, Charcoal-Black on +Z!)
        double[][] whiteTasselPts = {
            {-6.50, +0.80}, {-7.22, +0.10}, {-8.18, -0.55}, {-9.22, -0.96}, {-10.20, -1.10}, {-11.05, -0.98}
        };
        double[][] blackTasselPts = {
            {-6.50, +0.94}, {-7.22, +1.80}, {-8.16, +2.74}, {-9.15, +3.52}, {-10.12, +4.02}, {-10.95, +4.26}
        };
        double[] tWZ = {0.26, 0.96, 1.38, 1.24, 0.72, 0.16};
        double[] tTX = {0.26, 0.42, 0.48, 0.42, 0.28, 0.14};
        addLeafTassel(rootCubes, whiteTasselPts, tWZ, tTX, "TASSEL_W", "TASSEL_WR");
        addLeafTassel(rootCubes, blackTasselPts, tWZ, tTX, "TASSEL_K", "TASSEL_KR");

        // A6. Rounded Silver Bolster Collar + Raised Bolster Gem & Luminous-Rimmed Curved Silver Talon!
        rootCubes.add(Cube.centered(0.0, 4.18, +0.28, 1.44, 0.22, 1.44, "CEL_OUTLINE", -8, 0, 0));
        rootCubes.add(Cube.centered(0.0, 4.65, +0.30, 1.80, 0.98, 1.48, "GUARD_SILVER", -10, 0, 0));
        rootCubes.add(Cube.centered(0.0, 4.65, +0.30, 1.44, 0.96, 1.82, "GUARD_SILVER", -10, 0, 0));
        addRaisedGlowDiamond(rootCubes, 4.65, +0.30, 0.88, 0.50, 0.50, -10.0, "GLOW_GUARD");
        // Sleek Curved Silver Talon arcing cleanly ahead of the concave throat (-Z) with luminous outer vein!
        double[][] talonPts = {
            {4.65, -0.14}, {5.55, -1.04}, {6.70, -1.72}, {7.95, -2.02}, {9.15, -1.96}, {10.20, -1.60}
        };
        double[] talW = {1.38, 1.12, 0.86, 0.62, 0.38, 0.14};
        double[] talD = {1.00, 0.86, 0.70, 0.52, 0.34, 0.13};
        addOctagonalTube(rootCubes, talonPts, talW, talD, "GUARD_SILVER", "GLOW_VEIN_A", true);

        // =====================================================================
        // B. SCULPTED 3D REAR DARK REVERSE-HOOK WING + WHITE LOWER OVERHANG FIN
        //    Placed in `blade_root` so it stays 100% locked to the secondary grip
        //    even during First-Person `blade_body` Y-stretch around [0, 4.5, 0]!
        // =====================================================================

        // B1. Rounded Dark Charcoal Socket Lobe hugging Secondary Handle top — strictly tucked behind white saddle (ZERO poke-through!)
        rootCubes.add(Cube.centered(0.0, 4.10, +3.18, 1.36, 1.55, 1.95, "DARK_CORE", -4, 0, 0));
        rootCubes.add(Cube.centered(0.0, 3.45, +3.45, 1.34, 1.28, 1.48, "DARK_CORE", 14, 0, 0));
        rootCubes.add(Cube.centered(0.0, 2.85, +3.92, 1.18, 0.92, 1.06, "DARK_SPINE", 26, 0, 0));

        // B2. Sweeping BOLD Dark Rear Reverse-Hook Wing (Ref Images 1, 3, 5, 6, 7!)
        // Sweeps wide and deep backward to a razor dark reverse-scythe point at (-5.4, +9.1) with deep inner U-hook bay!
        double[][] rearOuterP = {
            { 5.10, +6.60}, { 1.80, +7.65}, {-1.85, +8.35}, {-5.40, +9.10}
        };
        double[][] rearInnerP = {
            { 4.35, +2.85}, { 1.25, +5.40}, {-1.70, +6.15}, {-5.40, +9.10}
        };
        final int N_REAR = 16;
        for (int i = 0; i < N_REAR; i++) {
            double ta = i / (double) N_REAR;
            double tb = (i + 1) / (double) N_REAR;
            double tm = 0.5 * (ta + tb);
            double[] inA = bezier3(rearInnerP, ta), inB = bezier3(rearInnerP, tb);
            double[] outA = bezier3(rearOuterP, ta), outB = bezier3(rearOuterP, tb);
            double rHalf = Math.max(0.08, 0.92 * Math.pow(Math.max(0.0, 1.0 - Math.pow(tm, 1.32)), 0.72));
            double inH = Math.max(0.07, rHalf * 0.42);
            double outH = Math.max(0.06, rHalf * 0.16);
            double eps = (i % 2 == 1) ? 0.012 : 0.0;
            addBeveledSliceWithRim(rootCubes, rootCubes, inA, outA, inB, outB,
                    rHalf, inH, outH, 0.48,
                    "DARK_CORE", "DARK_BEVEL", "DARK_SPINE", "DARK_CORE",
                    (tm > 0.42 ? "GLOW_EDGE_B" : "CEL_OUTLINE"), "CEL_OUTLINE", eps);
        }
        // Crisp luminous dark rear hook tip needle + raised Rear Wing Crystal Node!
        rootCubes.add(Cube.centered(0.0, -5.58, +9.22, 0.16, 0.74, 0.30, "GLOW_EDGE_B", -12, 0, 0));
        rootCubes.add(Cube.centered(0.0, -5.94, +9.38, 0.09, 0.50, 0.14, "CEL_OUTLINE", -14, 0, 0));
        double[] rwIn = bezier3(rearInnerP, 0.60), rwOut = bezier3(rearOuterP, 0.60);
        double rwR = Math.max(0.08, 0.92 * Math.pow(Math.max(0.0, 1.0 - Math.pow(0.60, 1.32)), 0.72));
        addRaisedGlowDiamond(rootCubes, lerp(rwIn[0], rwOut[0], 0.48), lerp(rwIn[1], rwOut[1], 0.48), rwR, 0.56, 0.56, -15.0, "GLOW_NODE_B");

        // B3. Raised Pointed WHITE Lower Overhang Fin overlapping proudly (+0.38 px) partway over Rear Dark Wing!
        // Terminates sharply at (-0.65, +8.15) so the bold dark reverse-hook tail juts dramatically past it (Ref 1, 3, 5, 7!)
        double[][] finOuterP = {
            { 4.50, +6.95}, { 2.80, +7.40}, { 1.05, +7.82}, {-0.65, +8.15}
        };
        double[][] finInnerP = {
            { 4.50, +2.75}, { 3.05, +4.75}, { 1.30, +6.55}, {-0.65, +8.15}
        };
        final int N_FIN = 10;
        for (int i = 0; i < N_FIN; i++) {
            double ta = i / (double) N_FIN;
            double tb = (i + 1) / (double) N_FIN;
            double tm = 0.5 * (ta + tb);
            double[] inA = bezier3(finInnerP, ta), inB = bezier3(finInnerP, tb);
            double[] outA = bezier3(finOuterP, ta), outB = bezier3(finOuterP, tb);
            double underlyingDarkH = 0.92 * Math.pow(Math.max(0.0, 1.0 - Math.pow(tm * 0.55, 1.32)), 0.72);
            double stepBoost = lerp(0.38, 0.06, Math.pow(tm, 1.15));
            double rHalf = Math.max(0.10, underlyingDarkH * (1.0 - 0.78 * tm) + stepBoost);
            double inH   = Math.max(0.10, rHalf * 0.62);
            double outH  = Math.max(0.06, rHalf * 0.16);
            double eps = (i % 2 == 1) ? 0.014 : 0.0;
            addBeveledSliceWithRim(rootCubes, rootCubes, inA, outA, inB, outB,
                    rHalf, inH, outH, 0.42,
                    "WHITE_PLATE", "WHITE_BEVEL", "WHITE_SPINE", "WHITE_PLATE",
                    "GLOW_EDGE_A", null, eps);
            if (tm < 0.92) {
                addGlowingSeamRibbon(rootCubes, inA, inB, inH + 0.02, "GLOW_VEIN_A");
            }
        }
        // Sharp gleaming luminous white overhang spike tip
        rootCubes.add(Cube.centered(0.0, -0.85, +8.20, 0.14, 0.54, 0.22, "GLOW_EDGE_A", -11, 0, 0));

        // =====================================================================
        // C. SCULPTED 3D MAIN CRESCENT CHARCOAL INNER CORE (`blade_body` + `blade_edge`)
        // =====================================================================
        final int N_MAIN = 36;
        for (int i = 0; i < N_MAIN; i++) {
            double sa = (i / (double) N_MAIN) * 0.955;
            double sb = ((i + 1) / (double) N_MAIN) * 0.955;
            double sm = 0.5 * (sa + sb);

            double[] inA = mainInnerYZ(sa), inB = mainInnerYZ(sb);
            double[] outA = mainOuterYZ(sa), outB = mainOuterYZ(sb);

            double outerInset = (sm < 0.39 || sm > 0.61) ? 0.93 : 1.00;
            double[] cOutA = {lerp(inA[0], outA[0], outerInset), lerp(inA[1], outA[1], outerInset)};
            double[] cOutB = {lerp(inB[0], outB[0], outerInset), lerp(inB[1], outB[1], outerInset)};

            double baseR = coreHalfThick(sm);
            // Safely recess dark core slightly underneath White Saddle Plate #1 so ZERO dark vertices poke through in FP/F5!
            double rHalf = (sm < 0.39) ? baseR * 0.84 : baseR;
            double inH   = Math.max(0.07, rHalf * 0.34);
            double outH  = Math.max(0.06, rHalf * 0.16);
            double eps = (i % 2 == 1) ? 0.012 : 0.0;

            boolean exposedOuter = (sm >= 0.39 && sm <= 0.62);
            boolean exposedInner = (sm >= 0.47);
            addBeveledSliceWithRim(bodyCubes, edgeCubes, inA, cOutA, inB, cOutB,
                    rHalf, inH, outH, 0.50,
                    "DARK_CORE", "DARK_BEVEL", "DARK_SPINE", "DARK_CORE",
                    exposedOuter ? "GLOW_EDGE_B" : null, exposedInner ? "CEL_OUTLINE" : null, eps);
        }
        // Crisp dark charcoal secondary under-tip fang (visible right inside the gleaming white tip in Ref 1, 3, 5!)
        double[] dTipA = mainInnerYZ(0.91);
        double[] dTipB = {lerp(mainInnerYZ(0.968)[0], mainOuterYZ(0.968)[0], 0.22),
                          lerp(mainInnerYZ(0.968)[1], mainOuterYZ(0.968)[1], 0.22)};
        double dtx = -Math.toDegrees(Math.atan2(dTipB[1] - dTipA[1], dTipB[0] - dTipA[0]));
        bodyCubes.add(Cube.centered(0.0, 0.5*(dTipA[0]+dTipB[0]), 0.5*(dTipA[1]+dTipB[1]),
                0.24, Math.hypot(dTipB[0]-dTipA[0], dTipB[1]-dTipA[1])*1.25, 0.52,
                "DARK_BEVEL", dtx, 0, 0));

        // =====================================================================
        // D. OVERLAPPING RAISED 3D WHITE/SILVER ARMOR PLATE #1: BASE-MID SADDLE
        // =====================================================================
        final int N_W1A = 14;
        for (int i = 0; i < N_W1A; i++) {
            double sa = (i / (double) N_W1A) * 0.40;
            double sb = ((i + 1) / (double) N_W1A) * 0.40;
            double sm = 0.5 * (sa + sb);

            double[] mInA = mainInnerYZ(sa), mInB = mainInnerYZ(sb);
            double[] mOutA = mainOuterYZ(sa), mOutB = mainOuterYZ(sb);

            double[] wOutA = {mOutA[0] + 0.06, mOutA[1] + 0.28};
            double[] wOutB = {mOutB[0] + 0.06, mOutB[1] + 0.28};
            double[] wInA  = {mInA[0]  - 0.02, mInA[1]  - 0.08};
            double[] wInB  = {mInB[0]  - 0.02, mInB[1]  - 0.08};

            double baseCoreH   = coreHalfThick(sm);
            double plateRidgeH = baseCoreH + 0.40;
            double plateInH    = Math.max(0.32, baseCoreH * 0.62 + 0.30);
            double plateOutH   = Math.max(0.08, baseCoreH * 0.18);
            double eps = (i % 2 == 1) ? 0.014 : 0.0;

            addBeveledSliceWithRim(bodyCubes, edgeCubes, wInA, wOutA, wInB, wOutB,
                    plateRidgeH, plateInH, plateOutH, 0.46,
                    "WHITE_PLATE", "WHITE_BEVEL", "WHITE_SPINE", "WHITE_PLATE",
                    "GLOW_EDGE_A", "CEL_OUTLINE", eps);
        }

        // Tapered 3D glowing panel seam across outer half at s = 0.40
        double c40 = coreHalfThick(0.40);
        double[] s40Mid = {lerp(mainInnerYZ(0.40)[0], mainOuterYZ(0.40)[0], 0.48),
                           lerp(mainInnerYZ(0.40)[1], mainOuterYZ(0.40)[1], 0.48)};
        double[] s40Out = {mainOuterYZ(0.40)[0] + 0.04, mainOuterYZ(0.40)[1] + 0.22};
        addTaperedGlowingSeam(bodyCubes, s40Mid, s40Out, c40 + 0.36, Math.max(0.10, c40 * 0.20), "GLOW_VEIN_A");

        // Inner S-Stepped Upper Lobe of White Saddle Plate #1 (`s = 0.40 -> 0.475`)
        final int N_W1B = 6;
        for (int i = 0; i < N_W1B; i++) {
            double ta = i / (double) N_W1B;
            double tb = (i + 1) / (double) N_W1B;
            double sa = lerp(0.395, 0.475, ta);
            double sb = lerp(0.395, 0.475, tb);
            double sm = 0.5 * (sa + sb);
            double tm = 0.5 * (ta + tb);

            double[] mInA = mainInnerYZ(sa), mInB = mainInnerYZ(sb);
            double[] mOutA = mainOuterYZ(sa), mOutB = mainOuterYZ(sb);

            double fOutA = lerp(0.50, 0.10, Math.pow(ta, 1.35));
            double fOutB = lerp(0.50, 0.10, Math.pow(tb, 1.35));
            double[] wInA  = {mInA[0] - 0.02, mInA[1] - 0.08};
            double[] wInB  = {mInB[0] - 0.02, mInB[1] - 0.08};
            double[] wOutA = {lerp(mInA[0], mOutA[0], fOutA), lerp(mInA[1], mOutA[1], fOutA)};
            double[] wOutB = {lerp(mInB[0], mOutB[0], fOutB), lerp(mInB[1], mOutB[1], fOutB)};

            double baseCoreH = coreHalfThick(sm);
            double stepAdd   = lerp(0.38, 0.24, tm);
            double rHalf     = baseCoreH + stepAdd;
            double inH       = Math.max(0.26, baseCoreH * 0.58 + stepAdd * 0.78);
            double outH      = Math.max(0.24, baseCoreH * 0.85 + stepAdd * 0.72);
            double eps       = (i % 2 == 1) ? 0.013 : 0.0;

            addBeveledSliceWithRim(bodyCubes, bodyCubes, wInA, wOutA, wInB, wOutB,
                    rHalf, inH, outH, 0.52,
                    "WHITE_PLATE", "WHITE_STEP", "WHITE_SPINE", "WHITE_PLATE",
                    null, "CEL_OUTLINE", eps);
            addGlowingSeamRibbon(bodyCubes, wOutA, wOutB, outH + 0.02, "GLOW_VEIN_A");
        }

        // =====================================================================
        // E. OVERLAPPING RAISED 3D WHITE/SILVER ARMOR PLATE #2: CRESCENT TIP CAP
        //    (s = 0.61 -> 1.00 — Physically stepped +0.34 px proud over exposed charcoal mid-core!)
        // =====================================================================
        final int N_W2 = 16;
        for (int i = 0; i < N_W2; i++) {
            double sa = lerp(0.61, 1.00, i / (double) N_W2);
            double sb = lerp(0.61, 1.00, (i + 1) / (double) N_W2);
            double sm = 0.5 * (sa + sb);
            double tLocal = (sm - 0.61) / 0.39;

            double[] mInA = mainInnerYZ(sa), mInB = mainInnerYZ(sb);
            double[] mOutA = mainOuterYZ(sa), mOutB = mainOuterYZ(sb);

            double fInA = whiteTipInnerFrac(sa);
            double fInB = whiteTipInnerFrac(sb);
            double[] wInA  = {lerp(mInA[0], mOutA[0], fInA), lerp(mInA[1], mOutA[1], fInA)};
            double[] wInB  = {lerp(mInB[0], mOutB[0], fInB), lerp(mInB[1], mOutB[1], fInB)};
            double proudOut = 0.24 * (1.0 - tLocal * 0.65);
            double[] wOutA = {mOutA[0] + proudOut * 0.55, mOutA[1] + proudOut * 0.35};
            double[] wOutB = {mOutB[0] + proudOut * 0.55, mOutB[1] + proudOut * 0.35};

            double baseCoreH = coreHalfThick(sm);
            double stepAdd   = 0.34 * (1.0 - Math.pow(tLocal, 1.38));
            double rHalf     = Math.max(0.08, baseCoreH + stepAdd);
            double inH       = Math.max(0.09, baseCoreH * 0.65 + stepAdd * 0.78);
            double outH      = Math.max(0.05, rHalf * 0.17);
            double eps       = (i % 2 == 1) ? 0.013 : 0.0;

            addBeveledSliceWithRim(bodyCubes, edgeCubes, wInA, wOutA, wInB, wOutB,
                    rHalf, inH, outH, 0.42,
                    "WHITE_PLATE", "WHITE_BEVEL", "WHITE_SPINE", "WHITE_PLATE",
                    "GLOW_EDGE_A", null, eps);
            if (tLocal < 0.88) {
                addGlowingSeamRibbon(bodyCubes, wInA, wInB, inH + 0.02, "GLOW_VEIN_B");
            }
        }
        // Tapered 3D glowing shoulder seam at the base of White Tip Plate #2 (s = 0.61)
        double c61 = coreHalfThick(0.61);
        double[] s61In  = {lerp(mainInnerYZ(0.61)[0], mainOuterYZ(0.61)[0], whiteTipInnerFrac(0.61)),
                           lerp(mainInnerYZ(0.61)[1], mainOuterYZ(0.61)[1], whiteTipInnerFrac(0.61))};
        double[] s61Out = {mainOuterYZ(0.61)[0] + 0.10, mainOuterYZ(0.61)[1] + 0.06};
        addTaperedGlowingSeam(bodyCubes, s61In, s61Out, c61 + 0.31, Math.max(0.09, c61 * 0.19), "GLOW_VEIN_B");

        // =====================================================================
        // F. RAISED 3D GLOWING CRYSTAL NODES ALONG SONG NGUYỆT BLADE!
        // =====================================================================
        // 1) White Saddle #1 raised crescent jewel (Primary Channel A, s = 0.22)
        double[] n1In = mainInnerYZ(0.22), n1Out = mainOuterYZ(0.22), n1Next = mainOuterYZ(0.25);
        double n1Rx = -Math.toDegrees(Math.atan2(n1Next[1] - n1Out[1], n1Next[0] - n1Out[0]));
        addRaisedGlowDiamond(bodyCubes, lerp(n1In[0], n1Out[0], 0.48), lerp(n1In[1], n1Out[1], 0.48),
                coreHalfThick(0.22) + 0.40, 0.78, 0.78, n1Rx, "GLOW_NODE_A");
        // 2) HERO ECLIPSE CORE DIAMOND + twin flanking star crystals inside Dark Obsidian Mid-Window (Secondary Channel B!)
        double[] n2In = mainInnerYZ(0.52), n2Out = mainOuterYZ(0.52), n2Next = mainOuterYZ(0.55);
        double n2Rx = -Math.toDegrees(Math.atan2(n2Next[1] - n2Out[1], n2Next[0] - n2Out[0]));
        addRaisedGlowDiamond(bodyCubes, lerp(n2In[0], n2Out[0], 0.52), lerp(n2In[1], n2Out[1], 0.52),
                coreHalfThick(0.52), 1.05, 0.92, n2Rx, "GLOW_NODE_B");
        double[] n2aIn = mainInnerYZ(0.45), n2aOut = mainOuterYZ(0.45);
        addRaisedGlowDiamond(bodyCubes, lerp(n2aIn[0], n2aOut[0], 0.68), lerp(n2aIn[1], n2aOut[1], 0.68),
                coreHalfThick(0.45) * 0.94, 0.42, 0.42, n2Rx, "GLOW_NODE_B");
        double[] n2bIn = mainInnerYZ(0.58), n2bOut = mainOuterYZ(0.58);
        addRaisedGlowDiamond(bodyCubes, lerp(n2bIn[0], n2bOut[0], 0.36), lerp(n2bIn[1], n2bOut[1], 0.36),
                coreHalfThick(0.58) * 0.94, 0.42, 0.42, n2Rx, "GLOW_NODE_B");
        // 3) White Tip Cap #2 raised crescent jewel (Primary Channel A, s = 0.74)
        double[] n3In = mainInnerYZ(0.74), n3Out = mainOuterYZ(0.74), n3Next = mainOuterYZ(0.78);
        double n3Rx = -Math.toDegrees(Math.atan2(n3Next[1] - n3Out[1], n3Next[0] - n3Out[0]));
        double t3 = (0.74 - 0.61) / 0.39;
        addRaisedGlowDiamond(bodyCubes, lerp(n3In[0], n3Out[0], 0.58), lerp(n3In[1], n3Out[1], 0.58),
                coreHalfThick(0.74) + 0.34 * (1.0 - Math.pow(t3, 1.38)), 0.56, 0.56, n3Rx, "GLOW_NODE_A");

        // Gleaming luminous razor-sharp primary crescent tip needle (`y = 24.2 .. 24.9, z = -12.4 .. -13.6`)
        edgeCubes.add(Cube.centered(0.0, 24.42, -12.82, 0.17, 0.94, 0.38, "GLOW_EDGE_A", +74.0, 0, 0));
        edgeCubes.add(Cube.centered(0.0, 24.64, -13.45, 0.12, 0.68, 0.22, "GLOW_EDGE_A", +76.0, 0, 0));
        edgeCubes.add(Cube.centered(0.0, 24.74, -13.62, 0.07, 0.58, 0.08, "CEL_OUTLINE", +76.0, 0, 0));

        Map<String, List<Cube>> res = new LinkedHashMap<>();
        res.put("blade_root", rootCubes);
        res.put("blade_body", bodyCubes);
        res.put("blade_edge", edgeCubes);
        return res;
    }

    static double whiteTipInnerFrac(double s) {
        if (s <= 0.61) return 0.55;
        if (s <= 0.72) {
            double t = (s - 0.61) / 0.11;
            double sm = t * t * (3.0 - 2.0 * t);
            return lerp(0.55, 0.24, sm);
        }
        double t = clamp((s - 0.72) / 0.28, 0.0, 1.0);
        return lerp(0.24, 0.10, t);
    }

    // =========================================================================
    // 2. JSON SERIALIZER & 256x256 CEL-SHADED TEXTURES + GLOWMASK
    // =========================================================================
    static String fmt(double v) {
        if (Math.abs(v) < 5e-4) v = 0.0;
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("(\\.\\d*?)0+$", "$1").replaceAll("\\.$", ".0");
    }

    static String uvEntry(String matKey) {
        int[] r = MAT.get(matKey);
        int pad = 2;
        return String.format(Locale.ROOT, "{\"uv\": [%d, %d], \"uv_size\": [%d, %d]}",
                r[0] + pad, r[1] + pad, r[2] - 2 * pad, r[3] - 2 * pad);
    }

    static void writeGeoJson(Map<String, List<Cube>> boneCubes) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format_version\": \"1.12.0\",\n");
        sb.append("  \"minecraft:geometry\": [\n");
        sb.append("    {\n");
        sb.append("      \"description\": {\n");
        sb.append("        \"identifier\": \"geometry.togetsu_fused\",\n");
        sb.append("        \"texture_width\": ").append(TEX_W).append(",\n");
        sb.append("        \"texture_height\": ").append(TEX_H).append(",\n");
        sb.append("        \"visible_bounds_width\": 3.6,\n");
        sb.append("        \"visible_bounds_height\": 4.0,\n");
        sb.append("        \"visible_bounds_offset\": [0.0, 0.8, 0.0]\n");
        sb.append("      },\n");
        sb.append("      \"bones\": [\n");
        sb.append("        {\"name\": \"root\", \"pivot\": [0, 0, 0], \"rotation\": [0, 0, 0]},\n");

        String[][] meta = {
            {"blade_root", "root",       "[0, 0, 0]"},
            {"blade_body", "blade_root", "[0, 4.5, 0]"},
            {"blade_edge", "blade_body", "[0, 14.0, -1.5]"}
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
                String uv = uvEntry(c.mat);
                sb.append("            {");
                sb.append("\"origin\": [").append(fmt(c.ox)).append(", ").append(fmt(c.oy)).append(", ").append(fmt(c.oz)).append("], ");
                sb.append("\"size\": [").append(fmt(c.sx)).append(", ").append(fmt(c.sy)).append(", ").append(fmt(c.sz)).append("], ");
                if (c.hasRot) {
                    sb.append("\"pivot\": [").append(fmt(c.px)).append(", ").append(fmt(c.py)).append(", ").append(fmt(c.pz)).append("], ");
                    sb.append("\"rotation\": [").append(fmt(c.rx)).append(", ").append(fmt(c.ry)).append(", ").append(fmt(c.rz)).append("], ");
                }
                sb.append("\"uv\": {")
                  .append("\"north\": ").append(uv).append(", ")
                  .append("\"south\": ").append(uv).append(", ")
                  .append("\"east\": ").append(uv).append(", ")
                  .append("\"west\": ").append(uv).append(", ")
                  .append("\"up\": ").append(uv).append(", ")
                  .append("\"down\": ").append(uv)
                  .append("}}");
                if (ci < cubes.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("          ]\n");
            sb.append("        }").append(bi < meta.length - 1 ? "," : "").append("\n");
        }
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        Path out = ASSETS.resolve("geo\\item\\togetsu_fused.geo.json");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        int total = boneCubes.values().stream().mapToInt(List::size).sum();
        System.out.println("GEO_FUSED_OK: " + out + " (" + total + " cubes)");
    }

    static int clampI(int v) { return Math.max(0, Math.min(255, v)); }
    static int rgba(int r, int g, int b, int a) {
        return ((clampI(a) & 255) << 24) | ((clampI(r) & 255) << 16) | ((clampI(g) & 255) << 8) | (clampI(b) & 255);
    }

    static void fillTile(BufferedImage diff, BufferedImage glowPrimary, BufferedImage glowSecondary, BufferedImage glowAccent,
                         String key, int[] cDark, int[] cMid, int[] cHi,
                         int priLevel, int secLevel, int accLevel, String style, Random rng) {
        int[] r = MAT.get(key);
        int x0 = r[0], y0 = r[1], w = r[2], h = r[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double fx = x / (double) (w - 1), fy = y / (double) (h - 1);
                double n = (rng.nextDouble() - 0.5) * 0.06;
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
                    t = clamp(0.30 + 0.20 * fy + n * 0.25, 0, 1);
                } else if ("metal_bevel".equals(style)) {
                    double sheen = 0.64 + 0.28 * (1.0 - fy) + 0.10 * Math.sin((fx * 3.0 + fy * 2.0) * Math.PI);
                    t = clamp(sheen + n * 0.35, 0, 1);
                } else {
                    double grad = 0.40 + 0.46 * (1.0 - fx * 0.40 - fy * 0.44);
                    double grain = 0.04 * Math.sin(x * 0.5 + y * 0.3);
                    t = clamp(grad + grain + n * 0.4, 0, 1);
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
                // 3-channel white-grayscale additive masks for exact runtime RGB tinting in TogetsuEmissiveLayer!
                int pv = priLevel > 0 ? clampI((int) Math.round(priLevel * (0.62 + 0.38 * t))) : 0;
                int sv = secLevel > 0 ? clampI((int) Math.round(secLevel * (0.62 + 0.38 * t))) : 0;
                int av = accLevel > 0 ? clampI((int) Math.round(accLevel * (0.58 + 0.42 * t))) : 0;
                glowPrimary.setRGB(x0 + x, y0 + y, pv == 0 ? 0 : rgba(pv, pv, pv, 255));
                glowSecondary.setRGB(x0 + x, y0 + y, sv == 0 ? 0 : rgba(sv, sv, sv, 255));
                glowAccent.setRGB(x0 + x, y0 + y, av == 0 ? 0 : rgba(av, av, av, 255));
            }
        }
    }

    static BufferedImage[] buildTextures() throws Exception {
        BufferedImage diff = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glowPri = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glowSec = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glowAcc = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Random rng = new Random(55105L);

        // Crisp shaded cool metallic silver-white armor plates (ZERO blown-out glare!)
        fillTile(diff, glowPri, glowSec, glowAcc, "WHITE_PLATE",  new int[]{150,158,168}, new int[]{208,215,224}, new int[]{244,248,252}, 0,   0, 0, "metal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "WHITE_BEVEL",  new int[]{178,186,196}, new int[]{226,232,240}, new int[]{252,254,255}, 18,  0, 0, "metal_bevel", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "WHITE_SPINE",  new int[]{132,140,152}, new int[]{184,192,204}, new int[]{224,230,238}, 0,   0, 0, "metal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "WHITE_STEP",   new int[]{114,122,134}, new int[]{160,168,180}, new int[]{202,210,220}, 0,   0, 0, "metal", rng);

        // Smooth matte dark slate-charcoal inner core & rear hook wing
        fillTile(diff, glowPri, glowSec, glowAcc, "DARK_CORE",    new int[]{36,39,45},    new int[]{56,60,68},    new int[]{78,83,92},    0,   0, 0, "metal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "DARK_BEVEL",   new int[]{46,50,58},    new int[]{68,73,82},    new int[]{94,100,112},  0,   0, 0, "metal_bevel", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "DARK_SPINE",   new int[]{28,31,36},    new int[]{44,48,56},    new int[]{64,69,78},    0,   0, 0, "metal", rng);

        // Subtle cel outline & shared hardware
        fillTile(diff, glowPri, glowSec, glowAcc, "CEL_OUTLINE",  new int[]{12,13,17},    new int[]{18,20,25},    new int[]{26,28,34},    0,   0, 0, "cel", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GUARD_SILVER", new int[]{134,142,154}, new int[]{186,194,206}, new int[]{232,238,246}, 0,   0, 0, "metal_bevel", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GUARD_SHADE",  new int[]{88,96,108},   new int[]{130,138,150}, new int[]{172,180,192}, 0,   0, 0, "metal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GRIP_LEATHER", new int[]{70,26,22},    new int[]{106,42,35},   new int[]{134,56,46},   0,   0, 0, "leather", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GRIP_WRAP",    new int[]{84,32,27},    new int[]{124,50,41},   new int[]{156,66,54},   0,   0, 0, "wrap", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "POMMEL_DARK",  new int[]{32,36,44},    new int[]{52,58,68},    new int[]{76,84,96},    0,   0, 0, "metal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "BEAD_RED",     new int[]{114,14,20},   new int[]{182,26,32},   new int[]{228,60,64},   0,   0, 0, "bead", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "TASSEL_W",     new int[]{164,170,180}, new int[]{218,224,232}, new int[]{248,250,255}, 0,   0, 0, "metal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "TASSEL_WR",    new int[]{192,198,208}, new int[]{236,242,248}, new int[]{254,255,255}, 150, 0, 0, "vein", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "TASSEL_K",     new int[]{28,31,37},    new int[]{48,52,60},    new int[]{74,79,88},    0,   0, 0, "metal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "TASSEL_KR",    new int[]{42,46,54},    new int[]{68,74,84},    new int[]{98,104,116},  0, 175, 0, "vein", rng);

        // Dedicated 3-channel raised luminous crystal & energy vein tiles (dark crystal socket base so ANY RGB pops cleanly in daylight!)
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_VEIN_A",  new int[]{22,26,34},    new int[]{36,42,54},    new int[]{56,64,78},    240, 0,   0, "vein", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_VEIN_B",  new int[]{18,20,28},    new int[]{32,36,48},    new int[]{52,58,74},    0,   245, 0, "vein", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_NODE_A",  new int[]{24,28,36},    new int[]{42,48,60},    new int[]{74,82,96},    255, 0,   0, "crystal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_NODE_B",  new int[]{20,22,30},    new int[]{36,40,54},    new int[]{68,74,90},    0,   255, 0, "crystal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_EDGE_A",  new int[]{92,100,114},  new int[]{126,136,152}, new int[]{166,176,192}, 150, 0,   0, "vein", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_EDGE_B",  new int[]{42,46,56},    new int[]{66,72,86},    new int[]{98,106,122},  0,   205, 0, "vein", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_GUARD",   new int[]{22,26,34},    new int[]{38,44,56},    new int[]{68,76,90},    245, 0,   0, "crystal", rng);
        fillTile(diff, glowPri, glowSec, glowAcc, "GLOW_BEAD",    new int[]{110,14,22},   new int[]{176,24,34},   new int[]{225,58,68},   0,   0, 245, "crystal", rng);

        Path texDir = ASSETS.resolve("textures\\item");
        ImageIO.write(diff, "png", texDir.resolve("togetsu_fused_model.png").toFile());
        ImageIO.write(glowPri, "png", texDir.resolve("togetsu_fused_model_glowmask.png").toFile());
        ImageIO.write(glowSec, "png", texDir.resolve("togetsu_fused_model_glowmask_secondary.png").toFile());
        ImageIO.write(glowAcc, "png", texDir.resolve("togetsu_fused_model_glowmask_accent.png").toFile());
        System.out.println("TEX_FUSED_OK: 256x256 diffuse + 3-channel (primary/secondary/accent) glowmasks");
        return new BufferedImage[]{diff, glowPri};
    }

    // =========================================================================
    // 3. MULTI-ANGLE & TRUE 16:9 FIRST-PERSON VERIFICATION RENDERER
    // =========================================================================
    static class TriQuad {
        double[][] v = new double[4][3];
        double[][] uv = new double[4][2];
        BufferedImage diff, glow;
    }

    static List<TriQuad> toQuads(Map<String, List<Cube>> boneCubes, BufferedImage diff, BufferedImage glow,
                                 boolean toModelUnits, double bladeBodyLenScale) {
        List<TriQuad> out = new ArrayList<>();
        for (Map.Entry<String, List<Cube>> e : boneCubes.entrySet()) {
            boolean inBlade = "blade_body".equals(e.getKey()) || "blade_edge".equals(e.getKey());
            for (Cube c : e.getValue()) {
                double x0=c.ox, y0=c.oy, z0=c.oz, x1=x0+c.sx, y1=y0+c.sy, z1=z0+c.sz;
                double[] bLB={x1,y0,z0}, bRB={x1,y0,z1}, tLB={x1,y1,z0}, tRB={x1,y1,z1};
                double[] tLF={x0,y1,z0}, tRF={x0,y1,z1}, bLF={x0,y0,z0}, bRF={x0,y0,z1};
                double[] R = c.hasRot ? mul(rzM(-c.rz), mul(ryM(c.ry), rxM(-c.rx))) : null;
                double[][][] fverts = {
                    {tRB, tLB, bLB, bRB}, {tLF, tRF, bRF, bLF},
                    {tLB, tLF, bLF, bLB}, {tRF, tRB, bRB, bRF},
                    {tRB, tRF, tLF, tLB}, {bLB, bLF, bRF, bRB}
                };
                int[] ur = MAT.get(c.mat);
                for (int fi = 0; fi < 6; fi++) {
                    TriQuad q = new TriQuad();
                    for (int vi = 0; vi < 4; vi++) {
                        double[] p = fverts[fi][vi].clone();
                        if (R != null) {
                            double[] rel = {p[0]-c.px, p[1]-c.py, p[2]-c.pz};
                            double[] rr = app(R, rel);
                            p = new double[]{rr[0]+c.px, rr[1]+c.py, rr[2]+c.pz};
                        }
                        if (inBlade && Math.abs(bladeBodyLenScale - 1.0) > 1e-4) {
                            p[1] = 4.5 + (p[1] - 4.5) * bladeBodyLenScale;
                        }
                        p = toModelUnits ? new double[]{-p[0]/16.0, p[1]/16.0, p[2]/16.0} : new double[]{-p[0], p[1], p[2]};
                        q.v[vi] = p;
                    }
                    double u0 = ur[0]+2, v0 = ur[1]+2, uw = ur[2]-4, vh = ur[3]-4;
                    q.uv = new double[][]{{u0+uw, v0}, {u0, v0}, {u0, v0+vh}, {u0+uw, v0+vh}};
                    q.diff = diff; q.glow = glow;
                    out.add(q);
                }
            }
        }
        return out;
    }

    static BufferedImage rasterOrtho(List<TriQuad> quads, double[] camM, int w, int h, double pxScale, double[] center, String title) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) img.setRGB(x, y, 0x4E525A);
        double[] zbuf = new double[w * h];
        Arrays.fill(zbuf, -1e9);
        double[] ld = {0.38, 0.72, 0.58};
        double ldl = Math.sqrt(ld[0]*ld[0]+ld[1]*ld[1]+ld[2]*ld[2]);
        ld[0]/=ldl; ld[1]/=ldl; ld[2]/=ldl;

        for (TriQuad q : quads) {
            double[][] c = new double[4][3];
            for (int i=0;i<4;i++) {
                double[] rel = {q.v[i][0]-center[0], q.v[i][1]-center[1], q.v[i][2]-center[2]};
                c[i] = app(camM, rel);
            }
            double[] e1 = {c[1][0]-c[0][0], c[1][1]-c[0][1], c[1][2]-c[0][2]};
            double[] e2 = {c[3][0]-c[0][0], c[3][1]-c[0][1], c[3][2]-c[0][2]};
            double nx = e1[1]*e2[2]-e1[2]*e2[1], ny = e1[2]*e2[0]-e1[0]*e2[2], nz = e1[0]*e2[1]-e1[1]*e2[0];
            double nl = Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (nl < 1e-7) continue;
            double shade = 0.56 + 0.44 * Math.abs((nx*ld[0]+ny*ld[1]+nz*ld[2])/nl);

            double[][] scr = new double[4][3];
            for (int i=0;i<4;i++) {
                scr[i][0] = w * 0.5 + c[i][0] * pxScale;
                scr[i][1] = h * 0.52 - c[i][1] * pxScale;
                scr[i][2] = c[i][2];
            }
            for (int[] tri : new int[][]{{0,1,2},{0,2,3}}) {
                double[] p0=scr[tri[0]], p1=scr[tri[1]], p2=scr[tri[2]];
                int x0=Math.max(0,(int)Math.floor(Math.min(p0[0],Math.min(p1[0],p2[0]))));
                int x1=Math.min(w-1,(int)Math.ceil(Math.max(p0[0],Math.max(p1[0],p2[0]))));
                int y0=Math.max(0,(int)Math.floor(Math.min(p0[1],Math.min(p1[1],p2[1]))));
                int y1=Math.min(h-1,(int)Math.ceil(Math.max(p0[1],Math.max(p1[1],p2[1]))));
                if (x0>x1||y0>y1) continue;
                double area = (p1[0]-p0[0])*(p2[1]-p0[1]) - (p2[0]-p0[0])*(p1[1]-p0[1]);
                if (Math.abs(area)<1e-4) continue;
                double invA = 1.0/area;
                for (int py=y0;py<=y1;py++) {
                    double gy=py+0.5;
                    for (int px=x0;px<=x1;px++) {
                        double gx=px+0.5;
                        double w0=((p1[0]-gx)*(p2[1]-gy)-(p2[0]-gx)*(p1[1]-gy))*invA;
                        double w1=((p2[0]-gx)*(p0[1]-gy)-(p0[0]-gx)*(p2[1]-gy))*invA;
                        double w2=1.0-w0-w1;
                        if (w0<-1e-4||w1<-1e-4||w2<-1e-4) continue;
                        double z = w0*p0[2]+w1*p1[2]+w2*p2[2];
                        int bi = py*w+px;
                        if (z <= zbuf[bi]) continue;
                        double[] t0=q.uv[tri[0]], t1=q.uv[tri[1]], t2=q.uv[tri[2]];
                        int u = clampI((int)(w0*t0[0]+w1*t1[0]+w2*t2[0]));
                        int v = clampI((int)(w0*t0[1]+w1*t1[1]+w2*t2[1]));
                        int argb = q.diff.getRGB(u, v);
                        if (((argb>>>24)&255) < 26) continue;
                        int dr=(argb>>16)&255, dg=(argb>>8)&255, db=argb&255;
                        int gargb = q.glow.getRGB(u, v);
                        int gr=(gargb>>16)&255, gg=(gargb>>8)&255, gb=gargb&255;
                        // True Minecraft RenderType.eyes() additive blending (GL_ONE, GL_ONE — ignores alpha!)
                        int rr = clampI((int)(dr*shade) + gr);
                        int ggg = clampI((int)(dg*shade) + gg);
                        int bb = clampI((int)(db*shade) + gb);
                        zbuf[bi] = z;
                        img.setRGB(px, py, (rr<<16)|(ggg<<8)|bb);
                    }
                }
            }
        }
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0,0,0,165));
        g2.fillRect(0, 0, w, 22);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.drawString(title, 8, 15);
        g2.dispose();
        return img;
    }

    static BufferedImage rasterFirstPerson(List<TriQuad> quadsModel, int w, int h, String title) {
        // Exact Minecraft 1.21.1 TogetsuGeoRenderer FP transform + true 16:9 widescreen projection!
        double[] M = trans(0.56, -0.52, -0.72);
        M = mul(M, trans(1.2/16.0, 0.8/16.0, 1.0/16.0));
        M = mul(M, rxM(-30.0)); M = mul(M, ryM(22.0)); M = mul(M, rzM(12.0));
        M = mul(M, scaleM(0.68, 0.68, 0.68));
        M = mul(M, trans(0.255, -0.095, -0.340));
        M = mul(M, ryM(200.0)); M = mul(M, rxM(1.0)); M = mul(M, rzM(16.0));
        M = mul(M, scaleM(1.32, 1.32, 1.32));
        M = mul(M, trans(0.0, -0.1125 + 0.01, -0.0344));

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int hy = (int)(h * 0.52);
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) img.setRGB(x, y, y < hy ? 0x84AEF5 : 0x527230);
        double[] zbuf = new double[w * h];
        Arrays.fill(zbuf, 1e9);
        double aspect = 16.0 / 9.0, f = 1.0 / Math.tan(Math.toRadians(70.0)*0.5), near = 0.05;
        double[] ld = {0.35, 0.72, 0.60};
        double ldl = Math.sqrt(ld[0]*ld[0]+ld[1]*ld[1]+ld[2]*ld[2]);
        ld[0]/=ldl; ld[1]/=ldl; ld[2]/=ldl;
        double minZ = 999; boolean clip = false;

        for (TriQuad q : quadsModel) {
            double[][] v = new double[4][3]; double[] d = new double[4]; boolean allB = true;
            for (int i=0;i<4;i++) {
                v[i] = app(M, q.v[i]); d[i] = -v[i][2];
                if (d[i] > near) allB = false;
                if (d[i] <= near) { clip = true; d[i] = near; }
                minZ = Math.min(minZ, d[i]);
            }
            if (allB) continue;
            double[] e1={v[1][0]-v[0][0],v[1][1]-v[0][1],v[1][2]-v[0][2]};
            double[] e2={v[3][0]-v[0][0],v[3][1]-v[0][1],v[3][2]-v[0][2]};
            double nx=e1[1]*e2[2]-e1[2]*e2[1], ny=e1[2]*e2[0]-e1[0]*e2[2], nz=e1[0]*e2[1]-e1[1]*e2[0];
            double nl=Math.sqrt(nx*nx+ny*ny+nz*nz); if (nl<1e-8) continue;
            double shade = 0.60 + 0.40 * Math.abs((nx*ld[0]+ny*ld[1]+nz*ld[2])/nl);
            double[][] scr = new double[4][3];
            for (int i=0;i<4;i++) {
                scr[i][0] = ((v[i][0]*(f/aspect))/d[i]*0.5 + 0.5)*w;
                scr[i][1] = (0.5 - (v[i][1]*f)/d[i]*0.5)*h;
                scr[i][2] = d[i];
            }
            for (int[] tri : new int[][]{{0,1,2},{0,2,3}}) {
                double[] p0=scr[tri[0]], p1=scr[tri[1]], p2=scr[tri[2]];
                int x0=Math.max(0,(int)Math.floor(Math.min(p0[0],Math.min(p1[0],p2[0]))));
                int x1=Math.min(w-1,(int)Math.ceil(Math.max(p0[0],Math.max(p1[0],p2[0]))));
                int y0=Math.max(0,(int)Math.floor(Math.min(p0[1],Math.min(p1[1],p2[1]))));
                int y1=Math.min(h-1,(int)Math.ceil(Math.max(p0[1],Math.max(p1[1],p2[1]))));
                if (x0>x1||y0>y1) continue;
                double area=(p1[0]-p0[0])*(p2[1]-p0[1])-(p2[0]-p0[0])*(p1[1]-p0[1]);
                if (Math.abs(area)<1e-4) continue;
                double invA=1.0/area;
                for (int py=y0;py<=y1;py++) for (int px=x0;px<=x1;px++) {
                    double gx=px+0.5, gy=py+0.5;
                    double w0=((p1[0]-gx)*(p2[1]-gy)-(p2[0]-gx)*(p1[1]-gy))*invA;
                    double w1=((p2[0]-gx)*(p0[1]-gy)-(p0[0]-gx)*(p2[1]-gy))*invA;
                    double w2=1.0-w0-w1;
                    if (w0<-1e-4||w1<-1e-4||w2<-1e-4) continue;
                    double z = 1.0 / Math.max(w0/p0[2]+w1/p1[2]+w2/p2[2], 1e-6);
                    int bi=py*w+px; if (z>=zbuf[bi]) continue;
                    double[] t0=q.uv[tri[0]], t1=q.uv[tri[1]], t2=q.uv[tri[2]];
                    int u=clampI((int)((w0*t0[0]/p0[2]+w1*t1[0]/p1[2]+w2*t2[0]/p2[2])*z));
                    int vp=clampI((int)((w0*t0[1]/p0[2]+w1*t1[1]/p1[2]+w2*t2[1]/p2[2])*z));
                    int argb=q.diff.getRGB(u,vp); if (((argb>>>24)&255)<26) continue;
                    int dr=(argb>>16)&255, dg=(argb>>8)&255, db=argb&255;
                    int gargb=q.glow.getRGB(u,vp);
                    int gr=(gargb>>16)&255, gg=(gargb>>8)&255, gb=gargb&255;
                    int rr=clampI((int)(dr*shade) + gr);
                    int ggg=clampI((int)(dg*shade) + gg);
                    int bb=clampI((int)(db*shade) + gb);
                    zbuf[bi]=z; img.setRGB(px,py,(rr<<16)|(ggg<<8)|bb);
                }
            }
        }
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.drawLine(w/2-7, h/2, w/2+7, h/2); g2.drawLine(w/2, h/2-7, w/2, h/2+7);
        g2.setColor(new Color(0,0,0,165)); g2.fillRect(0,0,w,22);
        g2.setColor(Color.WHITE); g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.drawString(String.format(Locale.ROOT, "%s | minZ=%.2f clip=%b", title, minZ, clip), 8, 15);
        g2.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception {
        Map<String, List<Cube>> bones = buildFusedSogengetsuBones();
        writeGeoJson(bones);
        BufferedImage[] tex = buildTextures();

        List<TriQuad> qOrtho = toQuads(bones, tex[0], tex[1], false, 1.0);
        List<TriQuad> qFp = toQuads(bones, tex[0], tex[1], true, 1.38);

        int pw = 560, ph = 380;
        BufferedImage[] panels = {
            // 1. Side Profile (Ref Images 1 & 5)
            rasterOrtho(qOrtho, mul(rzM(-52), ryM(90)), pw, ph, 10.6, new double[]{0, 7.5, -1.0}, "1. SIDE PROFILE (Ref Images 1 & 5)"),
            // 2. Inner Concave & Claw Edge View (Ref Image 2 - stepped white plates clamping dark core!)
            rasterOrtho(qOrtho, mul(rzM(-90), ryM(180)), pw, ph, 11.4, new double[]{0, 8.2, 0.0}, "2. INNER EDGE / CLAW VIEW (Ref Image 2)"),
            // 3. Reverse Side Profile (Ref Image 3)
            rasterOrtho(qOrtho, mul(rzM(58), ryM(-90)), pw, ph, 10.6, new double[]{0, 7.5, -1.0}, "3. REVERSE SIDE PROFILE (Ref Image 3)"),
            // 4. Outer Convex Spine View (Ref Image 4 - thick tapered core & stepped white plates!)
            rasterOrtho(qOrtho, mul(rzM(90), ryM(0)), pw, ph, 11.4, new double[]{0, 9.8, 0.0}, "4. OUTER CONVEX SPINE VIEW (Ref Image 4)"),
            // 5. 3/4 Hero View (Ref Image 7 - dual grip, pommel hole, beads, tassels, stepped plates!)
            rasterOrtho(qOrtho, mul(rxM(24), mul(rzM(-72), ryM(128))), pw, ph, 11.0, new double[]{0, 7.0, -0.5}, "5. 3/4 HERO VIEW (Ref Image 7)"),
            // 6. Live Minecraft 16:9 First-Person Perspective Check!
            rasterFirstPerson(qFp, pw, ph, "6. FIRST-PERSON 16:9 VIEW (IN-GAME CHECK)")
        };

        BufferedImage sheet = new BufferedImage(pw * 3, ph * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = sheet.createGraphics();
        for (int i = 0; i < 6; i++) g2.drawImage(panels[i], (i % 3) * pw, (i / 3) * ph, null);
        g2.dispose();
        Files.createDirectories(PREVIEW_OUT.getParent());
        ImageIO.write(sheet, "png", PREVIEW_OUT.toFile());
        ImageIO.write(sheet, "png", OUT_DIR.resolve("togetsu_fused_3d_preview_v3.png").toFile());
        System.out.println("PREVIEW_FUSED_V3_OK: " + PREVIEW_OUT);
    }
}
