import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Generates optimized 3D GeckoLib 4.8.3 models (.geo.json), animations (.animation.json),
 * 512x512 cel-shaded diffuse + selective emissive glowmask textures, 32x32 GUI icons,
 * and item display JSON for Excalibur (Golden) and Excalibur Morgan (Saber Alter),
 * faithfully sculpted from the measured proportions & carved runic geometry of the uploaded GLBs.
 */
public class BuildExcaliburAssets {
    static final Path ASSETS = Path.of("D:\\cremod\\mod-rpg\\src\\main\\resources\\assets\\abyssalrpg");
    static final Path GLB_OUT = Path.of("C:\\Users\\lucka\\.gemini\\antigravity\\brain\\6b270670-349b-4122-a74d-bf2d4d46e806\\scratch\\glb_out");

    static final int TEX_W = 512;
    static final int TEX_H = 512;

    static final Map<String, int[]> SWATCH = new LinkedHashMap<>();
    static {
        SWATCH.put("GRIP_MAIN",   new int[]{212, 244, 56, 56});
        SWATCH.put("GRIP_WRAP",   new int[]{276, 244, 56, 56});
        SWATCH.put("METAL_MAIN",  new int[]{340, 244, 56, 56});
        SWATCH.put("METAL_LIGHT", new int[]{404, 244, 56, 56});
        SWATCH.put("METAL_DARK",  new int[]{212, 308, 56, 56});
        SWATCH.put("ACCENT_BLUE", new int[]{276, 308, 56, 56});
        SWATCH.put("CEL_OUTLINE", new int[]{340, 308, 56, 56});
        SWATCH.put("RUNE_GLOW",   new int[]{404, 308, 56, 56});
        SWATCH.put("POMMEL_CAP",  new int[]{212, 372, 56, 56});
        SWATCH.put("EDGE_SHEEN",  new int[]{276, 372, 56, 56});
    }

    static class FaceUV {
        double u, v, su, sv;
        FaceUV(double u, double v, double su, double sv) { this.u=u; this.v=v; this.su=su; this.sv=sv; }
    }

    static class Cube {
        double ox, oy, oz, sx, sy, sz;
        double px, py, pz, rx, ry, rz;
        boolean hasRot;
        Map<String, FaceUV> uvs = new LinkedHashMap<>();

        static Cube centered(double cx, double cy, double cz, double sx, double sy, double sz,
                             double rx, double ry, double rz, String swatch) {
            Cube c = new Cube();
            c.sx = Math.max(0.04, sx);
            c.sy = Math.max(0.04, sy);
            c.sz = Math.max(0.04, sz);
            c.ox = cx - c.sx * 0.5;
            c.oy = cy - c.sy * 0.5;
            c.oz = cz - c.sz * 0.5;
            c.px = cx; c.py = cy; c.pz = cz;
            c.rx = rx; c.ry = ry; c.rz = rz;
            c.hasRot = (Math.abs(rx) > 1e-3 || Math.abs(ry) > 1e-3 || Math.abs(rz) > 1e-3);
            if (swatch != null) {
                int[] r = SWATCH.get(swatch);
                FaceUV f = new FaceUV(r[0], r[1], r[2], r[3]);
                for (String dir : new String[]{"north", "south", "east", "west", "up", "down"}) {
                    c.uvs.put(dir, f);
                }
            }
            return c;
        }

        Cube setSideUV(FaceUV eastWest, FaceUV other) {
            uvs.put("east", eastWest);
            uvs.put("west", eastWest);
            uvs.put("north", other);
            uvs.put("south", other);
            uvs.put("up", other);
            uvs.put("down", other);
            return this;
        }
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static int clampI(int v) { return Math.max(0, Math.min(255, v)); }

    // Blade profile: Y = +4.4 (guard) to +27.4 (lance tip) -> blade = 23.0 px, total = 33.0 px!
    // At 0.85x scale in thirdperson_righthand: total = 1.75 blocks, blade from grip = 1.45 blocks.
    static double bladeHalfWidthZ(double y) {
        if (y <= 22.8) {
            double t = clamp((y - 4.4) / (22.8 - 4.4), 0.0, 1.0);
            return lerp(1.22, 0.98, t);
        } else {
            double t = clamp((y - 22.8) / (27.4 - 22.8), 0.0, 1.0);
            return Math.max(0.05, 0.98 * Math.pow(1.0 - t * t, 0.82));
        }
    }

    static double bladeCoreHalfX(double y) {
        double t = clamp((y - 4.4) / (27.4 - 4.4), 0.0, 1.0);
        return lerp(0.42, 0.16, Math.pow(t, 1.25));
    }

    static List<Cube> buildSwordCubes(boolean morgan) {
        List<Cube> cubes = new ArrayList<>();

        // 1. OCTAGONAL POMMEL & WAISTED GRIP (Y = -5.6 .. +3.6, hand center = (0,0,0))
        cubes.add(Cube.centered(0, -5.25, 0, 1.38, 0.72, 1.06, 0, 0, 0, "POMMEL_CAP"));
        cubes.add(Cube.centered(0, -5.25, 0, 1.06, 0.72, 1.38, 0, 0, 0, "POMMEL_CAP"));
        cubes.add(Cube.centered(0, -5.60, 0, 0.96, 0.24, 0.96, 0, 0, 0, "METAL_LIGHT"));
        cubes.add(Cube.centered(0, -4.78, 0, 1.44, 0.22, 1.44, 0, 0, 0, "METAL_DARK"));
        cubes.add(Cube.centered(0, -4.55, 0, 1.18, 0.34, 1.18, 0, 0, 0, "METAL_MAIN"));

        int gripSegs = 8;
        for (int i = 0; i < gripSegs; i++) {
            double ya = lerp(-4.35, 2.75, i / (double) gripSegs);
            double yb = lerp(-4.35, 2.75, (i + 1) / (double) gripSegs);
            double ym = 0.5 * (ya + yb);
            double t = (i + 0.5) / gripSegs;
            double rad = 0.44 + 0.16 * Math.pow(Math.abs(t - 0.45) * 2.0, 1.45);
            double eps = (i % 2 == 1) ? 0.008 : 0.0;
            cubes.add(Cube.centered(0, ym, 0, rad * 2.0 + eps, (yb - ya) * 1.06, rad * 1.46, 0, 0, 0, "GRIP_MAIN"));
            cubes.add(Cube.centered(0, ym, 0, rad * 1.46 + eps, (yb - ya) * 1.06, rad * 2.0, 0, 0, 0, "GRIP_MAIN"));
            if (i > 0 && i < gripSegs) {
                cubes.add(Cube.centered(0, ya, 0, rad * 2.06, 0.12, rad * 1.54, 0, 0, 0, "GRIP_WRAP"));
                cubes.add(Cube.centered(0, ya, 0, rad * 1.54, 0.12, rad * 2.06, 0, 0, 0, "GRIP_WRAP"));
            }
        }
        cubes.add(Cube.centered(0, 3.05, 0, 1.38, 0.78, 1.42, 0, 0, 0, "METAL_MAIN"));
        cubes.add(Cube.centered(0, 3.05, 0, 1.12, 0.78, 1.66, 0, 0, 0, "METAL_LIGHT"));
        cubes.add(Cube.centered(0, 2.66, 0, 1.42, 0.14, 1.48, 0, 0, 0, "CEL_OUTLINE"));

        // 2. ICONIC CURVED V-SHAPED CROSSGUARD (Y = +3.4 .. +6.4, Z = -4.05 .. +4.05)
        int guardSegs = 9;
        double gSpanZ = 4.05;
        FaceUV guardSideEdge = new FaceUV(SWATCH.get("METAL_DARK")[0], SWATCH.get("METAL_DARK")[1], 56, 56);
        FaceUV guardLightEdge = new FaceUV(SWATCH.get("METAL_LIGHT")[0], SWATCH.get("METAL_LIGHT")[1], 56, 56);

        for (int i = 0; i < guardSegs; i++) {
            double u0 = i / (double) guardSegs;
            double u1 = (i + 1) / (double) guardSegs;
            double z0 = lerp(-gSpanZ, gSpanZ, u0);
            double z1 = lerp(-gSpanZ, gSpanZ, u1);
            double zm = 0.5 * (z0 + z1);
            double dz = (z1 - z0) * 1.12;

            double s0 = Math.abs(z0) / gSpanZ;
            double s1 = Math.abs(z1) / gSpanZ;
            double sm = Math.abs(zm) / gSpanZ;
            double y0 = 4.05 + 1.48 * s0 * s0;
            double y1 = 4.05 + 1.48 * s1 * s1;
            double ym = 0.5 * (y0 + y1);

            double slope = (y1 - y0) / (z1 - z0);
            double rxDeg = Math.toDegrees(Math.atan(slope));

            double hY = lerp(1.52, 1.72, sm * sm);
            double thickX = lerp(1.36, 1.14, sm);
            double eps = (i % 2 == 1) ? 0.012 : 0.0;

            double texU = 208.0 + u0 * 256.0;
            double texSu = (u1 - u0) * 256.0;
            FaceUV faceUV = new FaceUV(texU, 18.0, texSu, 92.0);

            Cube gc = Cube.centered(0, ym, zm, thickX + eps, hY, dz, rxDeg, 0, 0, null);
            gc.setSideUV(faceUV, guardSideEdge);
            cubes.add(gc);

            double rad = Math.toRadians(rxDeg);
            double ny = Math.cos(rad), nz = -Math.sin(rad);
            double halfH = hY * 0.50;
            Cube topRim = Cube.centered(0, ym + ny * halfH, zm + nz * halfH,
                    thickX * 0.72 + eps, 0.22, dz * 0.99, rxDeg, 0, 0, null);
            topRim.setSideUV(guardLightEdge, guardLightEdge);
            cubes.add(topRim);

            Cube botRim = Cube.centered(0, ym - ny * halfH, zm - nz * halfH,
                    thickX * 0.72 + eps, 0.22, dz * 0.99, rxDeg, 0, 0, null);
            botRim.setSideUV(guardSideEdge, guardSideEdge);
            cubes.add(botRim);

            cubes.add(Cube.centered(0, ym + ny * (halfH + 0.11), zm + nz * (halfH + 0.11),
                    thickX * 0.36, 0.09, dz * 0.97, rxDeg, 0, 0, "CEL_OUTLINE"));
            cubes.add(Cube.centered(0, ym - ny * (halfH + 0.11), zm - nz * (halfH + 0.11),
                    thickX * 0.36, 0.09, dz * 0.97, rxDeg, 0, 0, "CEL_OUTLINE"));
        }
        for (int sgn : new int[]{-1, +1}) {
            double rxTip = sgn * 34.0;
            double tipY = 4.05 + 1.48;
            cubes.add(Cube.centered(0, tipY, sgn * (gSpanZ - 0.04), 1.12, 1.66, 0.24, rxTip, 0, 0, "METAL_LIGHT"));
            cubes.add(Cube.centered(0, tipY + 0.04, sgn * (gSpanZ + 0.08), 0.56, 1.54, 0.10, rxTip, 0, 0, "CEL_OUTLINE"));
        }
        FaceUV centerGuardUV = new FaceUV(208.0 + 0.41 * 256.0, 18.0, 0.18 * 256.0, 92.0);
        Cube boss = Cube.centered(0, 4.12, 0, 1.52, 1.62, 1.38, 0, 0, 0, null);
        boss.setSideUV(centerGuardUV, guardLightEdge);
        cubes.add(boss);

        // 3. RICASSO COLLAR (Y = +4.8 .. +7.6)
        FaceUV ricassoUV = new FaceUV(210.0, 130.0, 92.0, 92.0);
        if (!morgan) {
            Cube collar = Cube.centered(0, 6.05, 0, 0.98, 2.60, 1.68, 0, 0, 0, null);
            collar.setSideUV(ricassoUV, guardLightEdge);
            cubes.add(collar);
            cubes.add(Cube.centered(0, 7.45, 0, 0.90, 0.72, 0.92, 0, 0, 0, "METAL_MAIN"));
            cubes.add(Cube.centered(0, 7.82, 0, 0.84, 0.42, 0.46, 0, 0, 0, "METAL_LIGHT"));
        } else {
            Cube mCollar = Cube.centered(0, 5.42, 0, 0.96, 1.24, 1.54, 0, 0, 0, null);
            mCollar.setSideUV(ricassoUV, guardSideEdge);
            cubes.add(mCollar);
        }

        // 4. SCULPTED 3D BEVELED BROADSWORD BLADE (Y = +4.6 .. +27.4)
        int bladeSegs = 22;
        double yStart = 4.6, yEnd = 27.4;
        FaceUV edgeTint = new FaceUV(SWATCH.get("EDGE_SHEEN")[0], SWATCH.get("EDGE_SHEEN")[1], 56, 56);
        FaceUV outlineUV = new FaceUV(SWATCH.get("CEL_OUTLINE")[0], SWATCH.get("CEL_OUTLINE")[1], 56, 56);

        for (int i = 0; i < bladeSegs; i++) {
            double f0 = i / (double) bladeSegs;
            double f1 = (i + 1) / (double) bladeSegs;
            double ya = lerp(yStart, yEnd, f0);
            double yb = lerp(yStart, yEnd, f1);
            double ym = 0.5 * (ya + yb);
            double segH = (yb - ya) * 1.04;

            double hw0 = bladeHalfWidthZ(ya);
            double hw1 = bladeHalfWidthZ(yb);
            double hwm = 0.5 * (hw0 + hw1);
            double coreX = bladeCoreHalfX(ym);
            double eps = (i % 2 == 1) ? 0.008 : 0.0;

            double vTop = 16.0 + (1.0 - f1) * 448.0;
            double vH   = (f1 - f0) * 448.0;

            double innerHalfZ = hwm * 0.68;
            double uPad = (1.0 - clamp(innerHalfZ / 1.22, 0.05, 1.0)) * 48.0;
            FaceUV coreFaceUV = new FaceUV(16.0 + uPad, vTop, Math.max(2.0, 96.0 - 2.0 * uPad), Math.max(2.0, vH));

            Cube core = Cube.centered(0, ym, 0, coreX * 2.0 + eps, segH, Math.max(0.08, innerHalfZ * 2.0), 0, 0, 0, null);
            core.setSideUV(coreFaceUV, edgeTint);
            cubes.add(core);

            double bevelSpanZ = hwm - innerHalfZ * 0.85;
            if (bevelSpanZ > 0.06) {
                double slopeDeg = Math.toDegrees(Math.atan2(hw1 - hw0, yb - ya));
                double bevelThickX = Math.max(0.14, coreX * 1.18);
                FaceUV bevelUV = new FaceUV(130.0, vTop, 60.0, Math.max(2.0, vH));
                for (int sgn : new int[]{-1, +1}) {
                    double cz = sgn * (innerHalfZ * 0.82 + bevelSpanZ * 0.50);
                    Cube bev = Cube.centered(0, ym, cz, bevelThickX + eps, segH * 1.02, bevelSpanZ * 1.05,
                            -sgn * slopeDeg, 0, 0, null);
                    bev.setSideUV(bevelUV, edgeTint);
                    cubes.add(bev);

                    double ez = sgn * (hwm - 0.04);
                    Cube sharpEdge = Cube.centered(0, ym, ez, Math.max(0.08, coreX * 0.42), segH * 1.03, 0.14,
                            -sgn * slopeDeg, 0, 0, null);
                    sharpEdge.setSideUV(edgeTint, edgeTint);
                    cubes.add(sharpEdge);

                    Cube rim = Cube.centered(0, ym, sgn * (hwm + 0.04), Math.max(0.05, coreX * 0.24), segH * 1.02, 0.06,
                            -sgn * slopeDeg, 0, 0, null);
                    rim.setSideUV(outlineUV, outlineUV);
                    cubes.add(rim);
                }
            }
        }
        cubes.add(Cube.centered(0, 27.38, 0, 0.16, 0.36, 0.14, 0, 0, 0, "EDGE_SHEEN"));
        cubes.add(Cube.centered(0, 27.54, 0, 0.08, 0.14, 0.08, 0, 0, 0, "CEL_OUTLINE"));

        // 5. SLEEK 3D CENTRAL FULLER RIB & GUARD CREST (Zero blocky artifacts over Morgan's crisp painted runic rings!)
        if (morgan) {
            // Sleek thin glowing central fuller rib strictly above the 4 circular runic rings (Y = 14.2 .. 23.4)
            cubes.add(Cube.centered(0, 18.80, 0, 0.64, 9.2, 0.10, 0, 0, 0, "RUNE_GLOW"));
            // Raised central obsidian-violet boss gem on the V-guard
            cubes.add(Cube.centered(0, 4.12, 0, 1.58, 0.34, 0.34, 45, 0, 0, "RUNE_GLOW"));
        } else {
            // Golden Excalibur: gleaming slim central white-gold fuller spine (Y = 8.4 .. 24.4)
            cubes.add(Cube.centered(0, 16.40, 0, 0.72, 16.0, 0.14, 0, 0, 0, "RUNE_GLOW"));
            cubes.add(Cube.centered(0, 4.12, 0, 1.62, 0.52, 0.52, 45, 0, 0, "METAL_LIGHT"));
        }

        return cubes;
    }

    static void generateTextures(boolean morgan, Path diffPath, Path glowPath) throws Exception {
        BufferedImage diff = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glow = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D dg = diff.createGraphics();
        Graphics2D gg = glow.createGraphics();
        dg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        dg.setColor(morgan ? new Color(18, 16, 26) : new Color(225, 190, 92));
        dg.fillRect(0, 0, TEX_W, TEX_H);
        gg.setColor(new Color(0, 0, 0, 255));
        gg.fillRect(0, 0, TEX_W, TEX_H);

        BufferedImage steelNoise = null;
        File nf = GLB_OUT.resolve("morgan_img0_Material.001_baseColor.jpeg.jpg").toFile();
        if (nf.exists()) steelNoise = ImageIO.read(nf);

        if (!morgan) {
            paintGoldenAtlas(dg, gg, diff, glow, steelNoise);
        } else {
            paintMorganAtlas(dg, gg, diff, glow, steelNoise);
        }

        dg.dispose();
        gg.dispose();
        Files.createDirectories(diffPath.getParent());
        ImageIO.write(diff, "png", diffPath.toFile());
        ImageIO.write(glow, "png", glowPath.toFile());
        System.out.println("Wrote textures: " + diffPath.getFileName() + " & " + glowPath.getFileName());
    }

    static void paintGoldenAtlas(Graphics2D dg, Graphics2D gg, BufferedImage diff, BufferedImage glow, BufferedImage noise) {
        fillSwatch(dg, "GRIP_MAIN", new Color(24, 46, 132), new Color(16, 30, 96), true);
        fillSwatch(dg, "GRIP_WRAP", new Color(42, 74, 178), new Color(20, 38, 112), false);
        fillSwatch(dg, "METAL_MAIN", new Color(248, 206, 80), new Color(204, 152, 34), false);
        fillSwatch(dg, "METAL_LIGHT", new Color(255, 240, 156), new Color(242, 196, 72), false);
        fillSwatch(dg, "METAL_DARK", new Color(172, 122, 26), new Color(122, 82, 16), false);
        fillSwatch(dg, "ACCENT_BLUE", new Color(26, 52, 152), new Color(16, 34, 110), false);
        fillSwatch(dg, "CEL_OUTLINE", new Color(36, 26, 18), new Color(24, 18, 12), false);
        fillSwatch(dg, "POMMEL_CAP", new Color(248, 208, 84), new Color(196, 144, 30), false);
        fillSwatch(dg, "EDGE_SHEEN", new Color(250, 252, 255), new Color(220, 228, 242), false);
        fillSwatch(dg, "RUNE_GLOW", new Color(255, 248, 202), new Color(255, 226, 122), false);
        fillSwatch(gg, "RUNE_GLOW", new Color(255, 240, 162), new Color(255, 210, 86), false);
        fillSwatch(gg, "EDGE_SHEEN", new Color(78, 66, 34), new Color(48, 40, 18), false);

        int bx = 16, by = 16, bw = 96, bh = 448;
        LinearGradientPaint bladeGrad = new LinearGradientPaint(
                bx, by, bx + bw, by,
                new float[]{0.0f, 0.16f, 0.34f, 0.50f, 0.66f, 0.84f, 1.0f},
                new Color[]{
                        new Color(224, 232, 246),
                        new Color(250, 252, 255),
                        new Color(255, 228, 130),
                        new Color(255, 248, 196),
                        new Color(255, 228, 130),
                        new Color(250, 252, 255),
                        new Color(224, 232, 246)
                });
        dg.setPaint(bladeGrad);
        dg.fillRect(bx, by, bw, bh);

        dg.setColor(new Color(148, 120, 62, 165));
        dg.setStroke(new BasicStroke(2.2f));
        dg.drawLine(bx + 18, by + 28, bx + 18, by + bh);
        dg.drawLine(bx + bw - 18, by + 28, bx + bw - 18, by + bh);

        LinearGradientPaint fullerG = new LinearGradientPaint(
                bx + 36, by, bx + 60, by,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(255, 222, 96, 150), new Color(255, 255, 238, 255), new Color(255, 222, 96, 150)});
        dg.setPaint(fullerG);
        dg.fillRect(bx + 36, by + 34, 24, bh - 68);

        // Engraved Fairy Letters near the lower blade
        dg.setColor(new Color(156, 118, 42, 220));
        dg.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gg.setColor(new Color(255, 248, 190));
        gg.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int scriptY0 = by + bh - 170;
        for (int k = 0; k < 6; k++) {
            int cy = scriptY0 + k * 21;
            Path2D glyph = new Path2D.Double();
            glyph.moveTo(bx + 34 + (k % 2) * 4, cy);
            glyph.curveTo(bx + 48, cy - 7, bx + 54, cy + 7, bx + 62 - (k % 2) * 4, cy - 2);
            glyph.moveTo(bx + 48, cy - 8);
            glyph.lineTo(bx + 46 + (k % 3 - 1) * 3, cy + 8);
            dg.draw(glyph);
            gg.draw(glyph);
        }

        // Glowmask: Selective radiant gold & pure white inner blade fuller glow + crisp bevel edge highlight!
        LinearGradientPaint glowBlade = new LinearGradientPaint(
                bx, by, bx + bw, by,
                new float[]{0.0f, 0.14f, 0.28f, 0.42f, 0.50f, 0.58f, 0.72f, 0.86f, 1.0f},
                new Color[]{
                        new Color(148, 126, 62),
                        new Color(72, 60, 26),
                        new Color(88, 70, 24),
                        new Color(214, 170, 64),
                        new Color(255, 252, 224),
                        new Color(214, 170, 64),
                        new Color(88, 70, 24),
                        new Color(72, 60, 26),
                        new Color(148, 126, 62)
                });
        gg.setPaint(glowBlade);
        gg.fillRect(bx + 4, by + 18, bw - 8, bh - 24);

        LinearGradientPaint bevelGrad = new LinearGradientPaint(
                128, by, 192, by,
                new float[]{0.0f, 0.40f, 0.72f, 1.0f},
                new Color[]{new Color(214, 228, 250), new Color(255, 255, 255), new Color(255, 242, 182), new Color(255, 226, 120)});
        dg.setPaint(bevelGrad);
        dg.fillRect(128, by, 64, bh);
        // Crisp bright golden-white bevel specular highlight on glowmask
        gg.setPaint(new GradientPaint(128, by, new Color(118, 102, 46), 192, by, new Color(196, 168, 74)));
        gg.fillRect(132, by + 12, 56, bh - 20);

        // Crossguard Panel (x=208..464, y=16..112): Gold V-wings + royal-blue chevron inlays!
        int gx = 208, gy = 16, gw = 256, gh = 96;
        dg.setPaint(new GradientPaint(gx, gy, new Color(255, 234, 132), gx, gy + gh, new Color(196, 144, 32)));
        dg.fillRect(gx, gy, gw, gh);
        dg.setColor(new Color(122, 84, 18));
        dg.setStroke(new BasicStroke(4.0f));
        dg.drawRect(gx + 2, gy + 4, gw - 4, gh - 8);
        dg.setColor(new Color(22, 48, 154));
        dg.setStroke(new BasicStroke(13.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
        dg.drawPolyline(new int[]{gx + 18, gx + 62, gx + 106}, new int[]{gy + 66, gy + 38, gy + 56}, 3);
        dg.drawPolyline(new int[]{gx + gw - 18, gx + gw - 62, gx + gw - 106}, new int[]{gy + 66, gy + 38, gy + 56}, 3);
        dg.setColor(new Color(74, 118, 224));
        dg.setStroke(new BasicStroke(3.0f));
        dg.drawPolyline(new int[]{gx + 22, gx + 62, gx + 102}, new int[]{gy + 64, gy + 40, gy + 55}, 3);
        dg.drawPolyline(new int[]{gx + gw - 22, gx + gw - 62, gx + gw - 102}, new int[]{gy + 64, gy + 40, gy + 55}, 3);
        dg.setColor(new Color(255, 238, 152));
        dg.fillOval(gx + gw/2 - 14, gy + gh/2 - 20, 28, 40);
        dg.setColor(new Color(22, 48, 154));
        dg.fillOval(gx + gw/2 - 7, gy + gh/2 - 12, 14, 24);
        gg.setColor(new Color(122, 98, 32));
        gg.fillOval(gx + gw/2 - 12, gy + gh/2 - 18, 24, 36);

        // Ricasso Collar Panel (x=208..304, y=128..224): Gold collar + royal-blue fleur-de-lis!
        int rx = 208, ry = 128, rw = 96, rh = 96;
        dg.setPaint(new GradientPaint(rx, ry, new Color(252, 222, 114), rx + rw, ry + rh, new Color(194, 142, 30)));
        dg.fillRect(rx, ry, rw, rh);
        dg.setColor(new Color(22, 48, 154));
        dg.fillRoundRect(rx + 18, ry + 14, rw - 36, rh - 24, 16, 16);
        dg.setColor(new Color(255, 232, 132));
        dg.fillRect(rx + rw/2 - 5, ry + 18, 10, rh - 34);
        int[] dx = {rx + rw/2, rx + rw/2 + 16, rx + rw/2, rx + rw/2 - 16};
        int[] dy = {ry + 32, ry + 50, ry + 68, ry + 50};
        dg.fillPolygon(dx, dy, 4);
        dg.setColor(new Color(120, 82, 16));
        dg.setStroke(new BasicStroke(3.0f));
        dg.drawRect(rx + 3, ry + 3, rw - 6, rh - 6);
        gg.setColor(new Color(132, 108, 38));
        gg.fillPolygon(dx, dy, 4);

        if (noise != null) blendSubtleGrain(diff, noise, 0.06f);
    }

    static void paintMorganAtlas(Graphics2D dg, Graphics2D gg, BufferedImage diff, BufferedImage glow, BufferedImage noise) {
        // Excalibur Morgan (Saber Alter): Matte Pitch-Black Obsidian + Blazing Crimson-Red Runes & Metallic Highlights!
        fillSwatch(dg, "GRIP_MAIN",   new Color(22, 14, 18), new Color(10, 6, 9), true);
        fillSwatch(dg, "GRIP_WRAP",   new Color(54, 16, 24), new Color(28, 8, 14), false);
        fillSwatch(dg, "METAL_MAIN",  new Color(32, 24, 30), new Color(16, 12, 16), false);
        fillSwatch(dg, "METAL_LIGHT", new Color(82, 56, 66), new Color(46, 30, 38), false);
        fillSwatch(dg, "METAL_DARK",  new Color(15, 10, 14), new Color(7, 5, 8), false);
        fillSwatch(dg, "ACCENT_BLUE", new Color(46, 12, 18), new Color(24, 6, 10), false);
        fillSwatch(dg, "CEL_OUTLINE", new Color(8, 3, 5),    new Color(4, 2, 3), false);
        fillSwatch(dg, "POMMEL_CAP",  new Color(44, 28, 34), new Color(20, 12, 16), false);
        fillSwatch(dg, "EDGE_SHEEN",  new Color(118, 72, 82), new Color(56, 32, 40), false);
        fillSwatch(dg, "RUNE_GLOW",   new Color(255, 36, 48), new Color(210, 12, 28), false);
        fillSwatch(gg, "RUNE_GLOW",   new Color(255, 48, 56), new Color(230, 16, 32), false);
        fillSwatch(gg, "EDGE_SHEEN",  new Color(135, 18, 28), new Color(72, 8, 16), false);

        int bx = 16, by = 16, bw = 96, bh = 448;
        LinearGradientPaint obsidianBlade = new LinearGradientPaint(
                bx, by, bx + bw, by,
                new float[]{0.0f, 0.14f, 0.24f, 0.50f, 0.76f, 0.86f, 1.0f},
                new Color[]{
                        new Color(98, 66, 74),
                        new Color(48, 34, 40),
                        new Color(16, 11, 14),
                        new Color(9, 6, 9),
                        new Color(16, 11, 14),
                        new Color(48, 34, 40),
                        new Color(98, 66, 74)
                });
        dg.setPaint(obsidianBlade);
        dg.fillRect(bx, by, bw, bh);

        dg.setColor(new Color(6, 3, 5));
        dg.setStroke(new BasicStroke(2.5f));
        dg.drawLine(bx + 18, by + 26, bx + 18, by + bh);
        dg.drawLine(bx + bw - 18, by + 26, bx + bw - 18, by + bh);

        // EXACT CARVED RUNIC GEOMETRY — BLAZING CRIMSON RED!
        int cx = bx + bw / 2;
        int orbY = by + bh - 34;
        drawGlowCircle(dg, gg, cx, orbY, 13, true);
        drawGlowArc(dg, gg, cx, by + bh - 8, 22, 16, 18, 144);

        int[] ringYs = {by + bh - 78, by + bh - 118, by + bh - 158, by + bh - 198};
        int ringR = 16;
        for (int ri = 0; ri < ringYs.length; ri++) {
            int ry = ringYs[ri];
            drawGlowCircle(dg, gg, cx, ry, ringR, false);
            int yPrev = (ri == 0) ? (orbY - 11) : (ringYs[ri - 1] - ringR + 1);
            drawGlowLine(dg, gg, cx, ry + ringR - 1, cx, yPrev, 3.8f);
        }
        int lineBot = ringYs[3] - ringR + 1;
        int lineTop = by + 54;
        drawGlowLine(dg, gg, cx, lineBot, cx, lineTop, 4.0f);

        int archY = by + 114;
        drawGlowArc(dg, gg, cx, archY, 20, 26, 10, 160);
        drawGlowCircle(dg, gg, cx, archY + 6, 4, true);

        // Crisp glowing crimson highlight lines running cleanly along BOTH blade bevel rims!
        gg.setColor(new Color(196, 22, 34, 195));
        gg.setStroke(new BasicStroke(4.2f));
        gg.drawLine(bx + 8, by + 24, bx + 8, by + bh - 4);
        gg.drawLine(bx + bw - 8, by + 24, bx + bw - 8, by + bh - 4);

        LinearGradientPaint mBevel = new LinearGradientPaint(
                128, by, 192, by,
                new float[]{0.0f, 0.45f, 0.80f, 1.0f},
                new Color[]{new Color(28, 20, 24), new Color(96, 68, 76), new Color(142, 44, 52), new Color(64, 24, 30)});
        dg.setPaint(mBevel);
        dg.fillRect(128, by, 64, bh);
        // Crimson metallic rim highlight on Morgan bevels
        gg.setPaint(new GradientPaint(128, by, new Color(64, 8, 14), 192, by, new Color(188, 22, 34)));
        gg.fillRect(146, by + 14, 24, bh - 22);

        // Crossguard Panel (x=208..464, y=16..112): Obsidian V-wings + carved crimson-red runes & metallic highlight!
        int gx = 208, gy = 16, gw = 256, gh = 96;
        dg.setPaint(new GradientPaint(gx, gy, new Color(46, 30, 36), gx, gy + gh, new Color(14, 9, 12)));
        dg.fillRect(gx, gy, gw, gh);
        dg.setColor(new Color(8, 4, 6));
        dg.setStroke(new BasicStroke(4.0f));
        dg.drawRect(gx + 2, gy + 4, gw - 4, gh - 8);

        Path2D leftArc = new Path2D.Double();
        leftArc.moveTo(gx + 24, gy + 36);
        leftArc.quadTo(gx + 68, gy + 66, gx + 106, gy + 48);
        Path2D rightArc = new Path2D.Double();
        rightArc.moveTo(gx + gw - 24, gy + 36);
        rightArc.quadTo(gx + gw - 68, gy + 66, gx + gw - 106, gy + 48);
        drawGlowPath(dg, gg, leftArc, 6.8f);
        drawGlowPath(dg, gg, rightArc, 6.8f);

        int gcx = gx + gw / 2;
        drawGlowCircle(dg, gg, gcx, gy + 44, 7, true);
        drawGlowArc(dg, gg, gcx, gy + 64, 16, 14, 20, 140);
        drawGlowCircle(dg, gg, gcx, gy + 74, 3, true);

        int rx = 208, ry = 128, rw = 96, rh = 96;
        dg.setPaint(new GradientPaint(rx, ry, new Color(36, 22, 28), rx, ry + rh, new Color(12, 8, 10)));
        dg.fillRect(rx, ry, rw, rh);
        drawGlowArc(dg, gg, rx + rw / 2, ry + rh - 22, 46, 36, 12, 156);
        drawGlowCircle(dg, gg, rx + rw / 2, ry + 36, 11, true);

        if (noise != null) blendSubtleGrain(diff, noise, 0.12f);
    }

    static void fillSwatch(Graphics2D g, String key, Color top, Color bot, boolean diagStripes) {
        int[] r = SWATCH.get(key);
        g.setPaint(new GradientPaint(r[0], r[1], top, r[0], r[1] + r[3], bot));
        g.fillRect(r[0] - 4, r[1] - 4, r[2] + 8, r[3] + 8);
        if (diagStripes) {
            g.setColor(new Color(0, 0, 0, 48));
            g.setStroke(new BasicStroke(2.0f));
            for (int d = -64; d < 128; d += 12) {
                g.drawLine(r[0] + d, r[1], r[0] + d + 28, r[1] + r[3]);
            }
        }
    }

    static void drawGlowCircle(Graphics2D dg, Graphics2D gg, int cx, int cy, int r, boolean filled) {
        Color halo = new Color(186, 12, 24, 140);
        Color mid  = new Color(248, 26, 40, 255);
        Color core = new Color(255, 136, 108, 255);
        if (filled) {
            for (Graphics2D g : new Graphics2D[]{dg, gg}) {
                g.setColor(halo); g.fillOval(cx - r - 4, cy - r - 4, (r + 4) * 2, (r + 4) * 2);
                g.setColor(mid);  g.fillOval(cx - r, cy - r, r * 2, r * 2);
                g.setColor(core); g.fillOval(cx - Math.max(1, r / 2), cy - Math.max(1, r / 2), Math.max(2, r), Math.max(2, r));
            }
        } else {
            for (Graphics2D g : new Graphics2D[]{dg, gg}) {
                g.setColor(halo); g.setStroke(new BasicStroke(9.5f)); g.drawOval(cx - r, cy - r, r * 2, r * 2);
                g.setColor(mid);  g.setStroke(new BasicStroke(4.8f)); g.drawOval(cx - r, cy - r, r * 2, r * 2);
                g.setColor(core); g.setStroke(new BasicStroke(2.0f)); g.drawOval(cx - r, cy - r, r * 2, r * 2);
            }
        }
    }

    static void drawGlowLine(Graphics2D dg, Graphics2D gg, int x0, int y0, int x1, int y1, float w) {
        Color halo = new Color(186, 12, 24, 140);
        Color mid  = new Color(248, 26, 40, 255);
        Color core = new Color(255, 136, 108, 255);
        for (Graphics2D g : new Graphics2D[]{dg, gg}) {
            g.setColor(halo); g.setStroke(new BasicStroke(w * 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawLine(x0, y0, x1, y1);
            g.setColor(mid);  g.setStroke(new BasicStroke(w * 1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawLine(x0, y0, x1, y1);
            g.setColor(core); g.setStroke(new BasicStroke(Math.max(1.5f, w * 0.45f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawLine(x0, y0, x1, y1);
        }
    }

    static void drawGlowArc(Graphics2D dg, Graphics2D gg, int cx, int cy, int w, int h, int startDeg, int extDeg) {
        Color halo = new Color(186, 12, 24, 140);
        Color mid  = new Color(248, 26, 40, 255);
        Color core = new Color(255, 136, 108, 255);
        for (Graphics2D g : new Graphics2D[]{dg, gg}) {
            g.setColor(halo); g.setStroke(new BasicStroke(8.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawArc(cx - w/2, cy - h/2, w, h, startDeg, extDeg);
            g.setColor(mid);  g.setStroke(new BasicStroke(4.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawArc(cx - w/2, cy - h/2, w, h, startDeg, extDeg);
            g.setColor(core); g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawArc(cx - w/2, cy - h/2, w, h, startDeg, extDeg);
        }
    }

    static void drawGlowPath(Graphics2D dg, Graphics2D gg, Shape sh, float w) {
        Color halo = new Color(186, 12, 24, 140);
        Color mid  = new Color(248, 26, 40, 255);
        Color core = new Color(255, 136, 108, 255);
        for (Graphics2D g : new Graphics2D[]{dg, gg}) {
            g.setColor(halo); g.setStroke(new BasicStroke(w * 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(sh);
            g.setColor(mid);  g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(sh);
            g.setColor(core); g.setStroke(new BasicStroke(w * 0.42f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(sh);
        }
    }

    static void blendSubtleGrain(BufferedImage dst, BufferedImage noise, float strength) {
        int nw = noise.getWidth(), nh = noise.getHeight();
        for (int y = 0; y < TEX_H; y++) {
            for (int x = 0; x < TEX_W; x++) {
                int argb = dst.getRGB(x, y);
                int nrgb = noise.getRGB((x * 2) % nw, (y * 2) % nh);
                float nl = (((nrgb >> 16) & 0xFF) - 62) / 128.0f;
                int r = clampI(Math.round(((argb >> 16) & 0xFF) * (1.0f + nl * strength)));
                int g = clampI(Math.round(((argb >> 8) & 0xFF) * (1.0f + nl * strength)));
                int b = clampI(Math.round((argb & 0xFF) * (1.0f + nl * strength)));
                dst.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    static void generateGuiIcon(boolean morgan, Path outPath) throws Exception {
        int S = 32;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                double dx = x - 11, dy = y - 20;
                double t = clamp((dx * 16 - dy * 16) / (16*16 + 16*16), 0.0, 1.0);
                double px = 11 + t * 16, py = 20 - t * 16;
                double d = Math.hypot(x - px, y - py);
                if (d < 5.0) {
                    int a = (int) (Math.pow(1.0 - d / 5.0, 2.0) * (morgan ? 132 : 118));
                    int rgb = morgan ? 0xFF1E2D : 0xFFE066;
                    img.setRGB(x, y, (clampI(a) << 24) | rgb);
                }
            }
        }
        // Crisp 32x32 cel outline along blade (from (11,20) to pointed spear tip (28,3))
        for (int i = 0; i <= 15; i++) {
            int x = 11 + i, y = 20 - i;
            setPx(img, x - 2, y - 1, morgan ? 0xFF120608 : 0xFF33230E);
            setPx(img, x + 1, y + 2, morgan ? 0xFF120608 : 0xFF33230E);
            setPx(img, x - 1, y - 2, morgan ? 0xFF120608 : 0xFF33230E);
            setPx(img, x + 2, y + 1, morgan ? 0xFF120608 : 0xFF33230E);
        }
        // Outline around sharp spear tip at (27,4) -> (29,2)
        setPx(img, 26, 2, morgan ? 0xFF120608 : 0xFF33230E);
        setPx(img, 27, 2, morgan ? 0xFF120608 : 0xFF33230E);
        setPx(img, 28, 1, morgan ? 0xFF120608 : 0xFF33230E);
        setPx(img, 29, 1, morgan ? 0xFF120608 : 0xFF33230E);
        setPx(img, 30, 2, morgan ? 0xFF120608 : 0xFF33230E);
        setPx(img, 30, 3, morgan ? 0xFF120608 : 0xFF33230E);
        setPx(img, 29, 4, morgan ? 0xFF120608 : 0xFF33230E);
        setPx(img, 29, 5, morgan ? 0xFF120608 : 0xFF33230E);

        for (int i = 0; i <= 15; i++) {
            int x = 11 + i, y = 20 - i;
            int edgeCol = morgan ? 0xFF66464E : 0xFFF2F6FF;
            int coreCol = morgan ? ((i % 3 == 1 && i < 12) ? 0xFFFF2436 : 0xFF1A0E12) : 0xFFFFD85A;
            if (morgan && i >= 2 && i <= 13 && i % 2 == 0) coreCol = 0xFFDF1828;
            if (!morgan && i >= 3 && i <= 14) coreCol = 0xFFFFF4B2;
            setPx(img, x - 1, y - 1, edgeCol);
            setPx(img, x + 1, y + 1, edgeCol);
            setPx(img, x, y, coreCol);
            setPx(img, x, y - 1, edgeCol);
            setPx(img, x + 1, y, edgeCol);
        }
        // Sharp pointed tip
        setPx(img, 27, 4, morgan ? 0xFF66464E : 0xFFFFEC9E);
        setPx(img, 27, 3, morgan ? 0xFF8C5A64 : 0xFFF2F6FF);
        setPx(img, 28, 4, morgan ? 0xFF8C5A64 : 0xFFF2F6FF);
        setPx(img, 28, 3, morgan ? 0xFFB86C78 : 0xFFFFFFFF);
        setPx(img, 29, 2, morgan ? 0xFFFF525C : 0xFFFFFFFF);

        // Iconic curved V-crossguard around (10, 21)
        int[][] guardPts = {{6,15},{7,16},{8,18},{9,19},{10,20},{11,21},{12,22},{13,23},{15,24},{16,25}};
        for (int[] gp : guardPts) {
            setPx(img, gp[0] - 1, gp[1], 0xFF140B0E);
            setPx(img, gp[0] + 1, gp[1], 0xFF140B0E);
            setPx(img, gp[0], gp[1] + 1, 0xFF140B0E);
            setPx(img, gp[0], gp[1] - 1, 0xFF140B0E);
        }
        for (int idx = 0; idx < guardPts.length; idx++) {
            int[] gp = guardPts[idx];
            int c = morgan
                    ? ((idx == 2 || idx == 7 || idx == 5) ? 0xFFFF2436 : 0xFF2E1E24)
                    : ((idx == 2 || idx == 7) ? 0xFF1A3BA8 : 0xFFF8CA4C);
            setPx(img, gp[0], gp[1], c);
            setPx(img, gp[0] + 1, gp[1] - 1, morgan ? 0xFF4C323B : 0xFFFFE684);
        }
        for (int i = 1; i <= 5; i++) {
            int x = 10 - i, y = 21 + i;
            setPx(img, x - 1, y, 0xFF10080A);
            setPx(img, x + 1, y, 0xFF10080A);
            setPx(img, x, y - 1, 0xFF10080A);
            setPx(img, x, y + 1, 0xFF10080A);
            setPx(img, x, y, morgan ? 0xFF241418 : 0xFF1C3AA0);
            setPx(img, x + 1, y - 1, morgan ? 0xFF3E1E26 : 0xFF2E54C8);
        }
        int pCol = morgan ? 0xFF482C34 : 0xFFF5C242;
        setPx(img, 3, 28, pCol); setPx(img, 4, 28, pCol);
        setPx(img, 3, 27, pCol); setPx(img, 4, 27, morgan ? 0xFFFF2436 : 0xFFFFEC9E);

        Files.createDirectories(outPath.getParent());
        ImageIO.write(img, "png", outPath.toFile());
        System.out.println("Wrote GUI icon: " + outPath.getFileName());
    }

    static void setPx(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) img.setRGB(x, y, argb);
    }

    static void writeGeoJson(String identifier, List<Cube> cubes, Path outPath) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format_version\": \"1.12.0\",\n");
        sb.append("  \"minecraft:geometry\": [\n");
        sb.append("    {\n");
        sb.append("      \"description\": {\n");
        sb.append("        \"identifier\": \"").append(identifier).append("\",\n");
        sb.append("        \"texture_width\": ").append(TEX_W).append(",\n");
        sb.append("        \"texture_height\": ").append(TEX_H).append(",\n");
        sb.append("        \"visible_bounds_width\": 3.0,\n");
        sb.append("        \"visible_bounds_height\": 4.2,\n");
        sb.append("        \"visible_bounds_offset\": [0.0, 1.0, 0.0]\n");
        sb.append("      },\n");
        sb.append("      \"bones\": [\n");
        sb.append("        {\"name\": \"root\", \"pivot\": [0, 0, 0], \"rotation\": [0, 0, 0]},\n");
        sb.append("        {\n");
        sb.append("          \"name\": \"blade_root\",\n");
        sb.append("          \"parent\": \"root\",\n");
        sb.append("          \"pivot\": [0, 0, 0],\n");
        sb.append("          \"rotation\": [0, 0, 0],\n");
        sb.append("          \"cubes\": [\n");

        for (int i = 0; i < cubes.size(); i++) {
            Cube c = cubes.get(i);
            sb.append(String.format(Locale.ROOT,
                    "            {\"origin\": [%.3f, %.3f, %.3f], \"size\": [%.3f, %.3f, %.3f]",
                    c.ox, c.oy, c.oz, c.sx, c.sy, c.sz));
            if (c.hasRot) {
                sb.append(String.format(Locale.ROOT,
                        ", \"pivot\": [%.3f, %.3f, %.3f], \"rotation\": [%.3f, %.3f, %.3f]",
                        c.px, c.py, c.pz, c.rx, c.ry, c.rz));
            }
            sb.append(", \"uv\": {");
            int fi = 0;
            for (Map.Entry<String, FaceUV> e : c.uvs.entrySet()) {
                if (fi++ > 0) sb.append(", ");
                FaceUV f = e.getValue();
                sb.append(String.format(Locale.ROOT,
                        "\"%s\": {\"uv\": [%.2f, %.2f], \"uv_size\": [%.2f, %.2f]}",
                        e.getKey(), f.u, f.v, f.su, f.sv));
            }
            sb.append("}}").append(i + 1 < cubes.size() ? ",\n" : "\n");
        }

        sb.append("          ]\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote geo: " + outPath.getFileName() + " (" + cubes.size() + " sculpted cubes)");
    }

    static void writeAnimationAndItemModel() throws Exception {
        // Static idle bone (ZERO GeckoLib scale wobble / holding jitter!)
        String anim = """
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.excalibur.idle": {
                      "loop": true,
                      "animation_length": 2.0,
                      "bones": {}
                    }
                  }
                }
                """;
        Path animPath = ASSETS.resolve("animations\\item\\excalibur.animation.json");
        Files.createDirectories(animPath.getParent());
        Files.writeString(animPath, anim, StandardCharsets.UTF_8);

        // Enlarged heroic scale for BOTH Golden Excalibur & Excalibur Morgan!
        String itemModel = """
                {
                  "parent": "builtin/entity",
                  "gui_light": "front",
                  "textures": {
                    "particle": "abyssalrpg:item/excalibur"
                  },
                  "display": {
                    "thirdperson_righthand": {
                      "rotation": [0, 0, 0],
                      "translation": [0.0, 0.8, 0.0],
                      "scale": [1.22, 1.28, 1.22]
                    },
                    "thirdperson_lefthand": {
                      "rotation": [0, 0, 0],
                      "translation": [0.0, 0.8, 0.0],
                      "scale": [1.22, 1.28, 1.22]
                    },
                    "firstperson_righthand": {
                      "rotation": [-12, 12, 8],
                      "translation": [1.6, 0.0, -0.4],
                      "scale": [1.10, 1.22, 1.10]
                    },
                    "firstperson_lefthand": {
                      "rotation": [-12, 12, 8],
                      "translation": [1.6, 0.0, -0.4],
                      "scale": [1.10, 1.22, 1.10]
                    },
                    "ground": {
                      "rotation": [0, 0, 0],
                      "translation": [0, 3.6, 0],
                      "scale": [0.85, 0.88, 0.85]
                    },
                    "fixed": {
                      "rotation": [0, 90, 0],
                      "translation": [0, -2.4, 0],
                      "scale": [0.92, 0.94, 0.92]
                    },
                    "gui": {
                      "rotation": [0, 0, 0],
                      "translation": [0, 0, 0],
                      "scale": [1.0, 1.0, 1.0]
                    }
                  }
                }
                """;
        Path itemPath = ASSETS.resolve("models\\item\\excalibur.json");
        Files.createDirectories(itemPath.getParent());
        Files.writeString(itemPath, itemModel, StandardCharsets.UTF_8);
        System.out.println("Wrote animation & item model JSON.");
    }

    public static void main(String[] args) throws Exception {
        List<Cube> goldenCubes = buildSwordCubes(false);
        List<Cube> morganCubes = buildSwordCubes(true);

        writeGeoJson("geometry.excalibur", goldenCubes, ASSETS.resolve("geo\\item\\excalibur.geo.json"));
        writeGeoJson("geometry.excalibur_morgan", morganCubes, ASSETS.resolve("geo\\item\\excalibur_morgan.geo.json"));

        generateTextures(false,
                ASSETS.resolve("textures\\item\\excalibur_golden_model.png"),
                ASSETS.resolve("textures\\item\\excalibur_golden_model_glowmask.png"));
        generateTextures(true,
                ASSETS.resolve("textures\\item\\excalibur_morgan_model.png"),
                ASSETS.resolve("textures\\item\\excalibur_morgan_model_glowmask.png"));

        generateGuiIcon(false, ASSETS.resolve("textures\\item\\excalibur.png"));
        generateGuiIcon(true,  ASSETS.resolve("textures\\item\\excalibur_morgan.png"));

        writeAnimationAndItemModel();
        System.out.println("ALL EXCALIBUR ASSETS GENERATED SUCCESSFULLY!");
    }
}
