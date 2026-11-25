import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Reproducible GeckoLib 4.8.3 Asset Generator for **Abyssal Tidebreaker (Sea Trident & Sea Lantern)**
 * faithful to Sketchfab "Sea weapons, Lantern and Trident - Blockbench" (5a5b389d8f234f31a76e435fcd3c1115).
 *
 * Generates:
 *  - geo/item/sea_trident.geo.json & geo/item/sea_lantern.geo.json
 *  - animations/item/sea_weapons.animation.json
 *  - textures/item/sea_trident_model.png + sea_trident_model_glowmask.png
 *  - textures/item/sea_lantern_model.png + sea_lantern_model_glowmask.png
 *  - textures/item/sea_trident.png (crisp 32x32 GUI icon showing both Trident + Lantern!)
 *  - models/item/sea_trident.json
 */
public class BuildSeaWeaponsAssets {
    static final Path ASSETS = Path.of("D:\\cremod\\mod-rpg\\src\\main\\resources\\assets\\abyssalrpg");
    static final int TEX_W = 256, TEX_H = 256;
    static final int CELL = 16; // 16x16 per swatch cell -> 256 swatches available on a 256x256 atlas!

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static final class PaletteAtlas {
        final BufferedImage diff = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        final BufferedImage glow = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        final Map<Long, int[]> cache = new LinkedHashMap<>();
        int nextIdx = 0;

        PaletteAtlas() {
            Graphics2D g1 = diff.createGraphics(), g2 = glow.createGraphics();
            g1.setColor(new Color(0, 0, 0, 255)); g1.fillRect(0, 0, TEX_W, TEX_H);
            g2.setColor(new Color(0, 0, 0, 255)); g2.fillRect(0, 0, TEX_W, TEX_H);
            g1.dispose(); g2.dispose();
        }

        int[] uvFor(int rgb, float glowStrength, int glowRgb) {
            int gByte = Math.max(0, Math.min(255, Math.round(glowStrength * 255.0f)));
            long key = ((long) (rgb & 0xFFFFFF) << 32) | ((long) gByte << 24) | (glowRgb & 0xFFFFFFL);
            int[] existing = cache.get(key);
            if (existing != null) return existing;

            int idx = nextIdx++;
            int col = idx % (TEX_W / CELL);
            int row = idx / (TEX_W / CELL);
            int u = col * CELL;
            int v = row * CELL;

            // Fill 16x16 swatch with crisp subtle pixel-art shading border (no blurry bleeding)
            Color base = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
            Color hi = new Color(
                    Math.min(255, base.getRed() + 14),
                    Math.min(255, base.getGreen() + 14),
                    Math.min(255, base.getBlue() + 16), 255);
            Color lo = new Color(
                    Math.max(0, base.getRed() - 12),
                    Math.max(0, base.getGreen() - 12),
                    Math.max(0, base.getBlue() - 10), 255);

            for (int dy = 0; dy < CELL; dy++) {
                for (int dx = 0; dx < CELL; dx++) {
                    Color c = (dy < 3) ? hi : ((dy >= CELL - 3) ? lo : base);
                    diff.setRGB(u + dx, v + dy, c.getRGB());
                }
            }
            if (gByte > 2) {
                int gr = Math.min(255, Math.round(((glowRgb >> 16) & 0xFF) * glowStrength));
                int gg = Math.min(255, Math.round(((glowRgb >> 8) & 0xFF) * glowStrength));
                int gb = Math.min(255, Math.round((glowRgb & 0xFF) * glowStrength));
                int gc = (255 << 24) | (gr << 16) | (gg << 8) | gb;
                for (int dy = 0; dy < CELL; dy++) {
                    for (int dx = 0; dx < CELL; dx++) {
                        glow.setRGB(u + dx, v + dy, gc);
                    }
                }
            }
            // Inset UV by 2px so linear/mipmap never bleeds adjacent swatches
            int[] uv = {u + 2, v + 2, CELL - 4, CELL - 4};
            cache.put(key, uv);
            return uv;
        }
    }

    static final class Cube {
        double ox, oy, oz, sx, sy, sz, px, py, pz, rx, ry, rz;
        boolean hasRot;
        int rgb, topRgb = -1, botRgb = -1, frontRgb = -1;
        float glow = 0.0f;
        int glowRgb = 0x70E8FF;

        static Cube box(double cx, double cy, double cz, double sx, double sy, double sz, int rgb) {
            return rot(cx, cy, cz, sx, sy, sz, 0, 0, 0, rgb);
        }
        static Cube rot(double cx, double cy, double cz, double sx, double sy, double sz,
                        double rx, double ry, double rz, int rgb) {
            Cube c = new Cube();
            c.sx = Math.max(0.06, sx); c.sy = Math.max(0.06, sy); c.sz = Math.max(0.06, sz);
            c.ox = cx - c.sx * 0.5; c.oy = cy - c.sy * 0.5; c.oz = cz - c.sz * 0.5;
            c.px = cx; c.py = cy; c.pz = cz; c.rx = rx; c.ry = ry; c.rz = rz;
            c.hasRot = Math.abs(rx) > 1e-3 || Math.abs(ry) > 1e-3 || Math.abs(rz) > 1e-3;
            c.rgb = rgb;
            return c;
        }
        Cube faces(int top, int bot, int front) { this.topRgb = top; this.botRgb = bot; this.frontRgb = front; return this; }
        Cube emit(float g, int col) { this.glow = g; this.glowRgb = col; return this; }
    }

    static String fmt(double d) {
        if (Math.abs(d) < 1e-4) return "0";
        return String.format(Locale.ROOT, "%.3f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static void appendCubesJson(StringBuilder sb, List<Cube> cubes, PaletteAtlas atlas) {
        for (int i = 0; i < cubes.size(); i++) {
            Cube c = cubes.get(i);
            int[] uvSide  = atlas.uvFor(c.rgb, c.glow, c.glowRgb);
            int[] uvTop   = atlas.uvFor(c.topRgb >= 0 ? c.topRgb : c.rgb, c.glow, c.glowRgb);
            int[] uvBot   = atlas.uvFor(c.botRgb >= 0 ? c.botRgb : c.rgb, c.glow, c.glowRgb);
            int[] uvFront = atlas.uvFor(c.frontRgb >= 0 ? c.frontRgb : c.rgb, c.glow, c.glowRgb);

            sb.append("            {\"origin\":[")
              .append(fmt(c.ox)).append(",").append(fmt(c.oy)).append(",").append(fmt(c.oz))
              .append("],\"size\":[")
              .append(fmt(c.sx)).append(",").append(fmt(c.sy)).append(",").append(fmt(c.sz)).append("]");
            if (c.hasRot) {
                sb.append(",\"pivot\":[")
                  .append(fmt(c.px)).append(",").append(fmt(c.py)).append(",").append(fmt(c.pz))
                  .append("],\"rotation\":[")
                  .append(fmt(c.rx)).append(",").append(fmt(c.ry)).append(",").append(fmt(c.rz)).append("]");
            }
            sb.append(",\"uv\":{")
              .append("\"north\":{\"uv\":[").append(uvFront[0]).append(",").append(uvFront[1]).append("],\"uv_size\":[").append(uvFront[2]).append(",").append(uvFront[3]).append("]},")
              .append("\"south\":{\"uv\":[").append(uvFront[0]).append(",").append(uvFront[1]).append("],\"uv_size\":[").append(uvFront[2]).append(",").append(uvFront[3]).append("]},")
              .append("\"east\":{\"uv\":[").append(uvSide[0]).append(",").append(uvSide[1]).append("],\"uv_size\":[").append(uvSide[2]).append(",").append(uvSide[3]).append("]},")
              .append("\"west\":{\"uv\":[").append(uvSide[0]).append(",").append(uvSide[1]).append("],\"uv_size\":[").append(uvSide[2]).append(",").append(uvSide[3]).append("]},")
              .append("\"up\":{\"uv\":[").append(uvTop[0]).append(",").append(uvTop[1]).append("],\"uv_size\":[").append(uvTop[2]).append(",").append(uvTop[3]).append("]},")
              .append("\"down\":{\"uv\":[").append(uvBot[0]).append(",").append(uvBot[1]).append("],\"uv_size\":[").append(uvBot[2]).append(",").append(uvBot[3]).append("]}")
              .append("}}").append(i + 1 < cubes.size() ? ",\n" : "\n");
        }
    }

    static void buildTridentAssets() throws Exception {
        PaletteAtlas atlas = new PaletteAtlas();
        List<Cube> mainCubes = new ArrayList<>();
        List<Cube> waveCubes = new ArrayList<>();

        // Shift Y by -5.5 so the hand grip center (dark-navy ribbed wrap between the two silver collars) sits right at Y = 0!
        double yShift = -5.5;

        // 1. Ice-silver diamond pommel cap at bottom
        mainCubes.add(Cube.rot(0, -13.6 + yShift, 0, 2.35, 1.05, 2.35, 0, 45, 0, 0x89BCE0)
                .faces(0xDCF6FF, 0x2F445C, 0x76AACF).emit(0.32f, 0x9BE8FF));
        mainCubes.add(Cube.rot(0, -13.15 + yShift, 0, 1.55, 0.35, 1.55, 0, 45, 0, 0x33475E));

        // 2. Banded dark-charcoal/slate polearm shaft
        for (int i = 0; i < 16; i++) {
            double y0 = lerp(-13.0, 2.4, i / 16.0) + yShift;
            double y1 = lerp(-13.0, 2.4, (i + 1) / 16.0) + yShift;
            mainCubes.add(Cube.box(0, 0.5 * (y0 + y1), 0, 1.24, (y1 - y0) + 0.03, 1.24, (i % 2 == 0) ? 0x262E39 : 0x45505E));
        }
        // Lower raised ice-steel collar ring
        mainCubes.add(Cube.box(0, 2.75 + yShift, 0, 1.95, 0.88, 1.95, 0x7AA6C4)
                .faces(0xCBEAFF, 0x3A4E64, 0x7AA6C4).emit(0.16f, 0x8AE4FF));
        mainCubes.add(Cube.box(0, 2.25 + yShift, 0, 1.56, 0.36, 1.56, 0x243140));
        // Ribbed dark-navy leather grip wrap (centered at Y = 0!)
        for (int i = 0; i < 6; i++) {
            double y0 = lerp(3.2, 7.8, i / 6.0) + yShift;
            double y1 = lerp(3.2, 7.8, (i + 1) / 6.0) + yShift;
            mainCubes.add(Cube.box(0, 0.5 * (y0 + y1), 0, 1.34, (y1 - y0) + 0.03, 1.34, (i % 2 == 0) ? 0x102034 : 0x1E3A58));
        }
        // Upper raised ice-steel collar ring
        mainCubes.add(Cube.box(0, 8.15 + yShift, 0, 1.95, 0.88, 1.95, 0x7AA6C4)
                .faces(0xCBEAFF, 0x3A4E64, 0x7AA6C4).emit(0.16f, 0x8AE4FF));
        // Upper neck shaft
        for (int i = 0; i < 4; i++) {
            double y0 = lerp(8.6, 11.8, i / 4.0) + yShift;
            double y1 = lerp(8.6, 11.8, (i + 1) / 4.0) + yShift;
            mainCubes.add(Cube.box(0, 0.5 * (y0 + y1), 0, 1.36, (y1 - y0) + 0.04, 1.36, (i % 2 == 0) ? 0x26313E : 0x3A4656));
        }

        // 3. Closed Diamond Crossguard Crown + Faceted Sapphire Gem
        double dCy = 14.20 + yShift, dOff = 1.48;
        mainCubes.add(Cube.rot(-dOff, dCy + dOff, 0, 3.65, 0.92, 1.44, 0, 0, -45, 0xBCE8FC)
                .faces(0xF0FCFF, 0x5A7C9A, 0xA8DAF4).emit(0.30f, 0xB0F0FF));
        mainCubes.add(Cube.rot(+dOff, dCy + dOff, 0, 3.65, 0.92, 1.44, 0, 0, +45, 0xBCE8FC)
                .faces(0xF0FCFF, 0x5A7C9A, 0xA8DAF4).emit(0.30f, 0xB0F0FF));
        mainCubes.add(Cube.rot(-dOff, dCy - dOff, 0, 3.65, 0.92, 1.40, 0, 0, +45, 0x6284A4)
                .faces(0x9BC6E2, 0x263547, 0x6284A4));
        mainCubes.add(Cube.rot(+dOff, dCy - dOff, 0, 3.65, 0.92, 1.40, 0, 0, -45, 0x6284A4)
                .faces(0x9BC6E2, 0x263547, 0x6284A4));
        mainCubes.add(Cube.rot(0, dCy + 2.86, 0, 1.05, 1.05, 1.48, 0, 0, 45, 0xE8FCFF).emit(0.50f, 0xC8F8FF));
        mainCubes.add(Cube.rot(0, dCy, 0, 2.75, 2.75, 1.18, 0, 0, 45, 0x131E30));
        mainCubes.add(Cube.rot(0, dCy, 0, 2.15, 2.15, 1.82, 0, 0, 45, 0x2462DC)
                .faces(0x84D4FF, 0x122662, 0x2B6CE6).emit(0.65f, 0x52B2FF));
        mainCubes.add(Cube.rot(0, dCy + 0.62, 0, 1.24, 0.92, 1.92, 0, 0, 45, 0xECFCFF).emit(0.90f, 0xC6F8FF));

        for (int sgn : new int[]{-1, +1}) {
            mainCubes.add(Cube.rot(sgn * 2.65, 13.55 + yShift, 0, 2.75, 1.38, 1.36, 0, 0, sgn * 26, 0x90BCD8)
                    .faces(0xCEF0FF, 0x354B62, 0x7BA6C4).emit(0.18f, 0x92EAFF));
            mainCubes.add(Cube.rot(sgn * 2.45, 12.78 + yShift, 0, 2.35, 0.72, 1.18, 0, 0, sgn * 26, 0x384E66));
        }

        // 4. Three Sharp Crystalline Trident Prongs
        for (int i = 0; i < 6; i++) {
            double ya = lerp(16.4, 22.8, i / 6.0) + yShift;
            double yb = lerp(16.4, 22.8, (i + 1) / 6.0) + yShift;
            double ym = 0.5 * (ya + yb);
            mainCubes.add(Cube.box(-0.22, ym, 0, 0.92, (yb - ya) + 0.04, 1.02, (i % 2 == 0) ? 0x1C45A4 : 0x2E7BF0)
                    .emit(0.42f, 0x3894FF));
            mainCubes.add(Cube.box(+0.44, ym, 0, 0.54, (yb - ya) + 0.04, 0.86, (i >= 3) ? 0xCCF5FF : 0x82D2FF)
                    .emit(0.68f, 0x92EEFF));
        }
        mainCubes.add(Cube.rot(0, 23.35 + yShift, 0, 1.24, 1.24, 0.94, 0, 0, 45, 0x88DAFF).emit(0.65f, 0x88EEFF));
        mainCubes.add(Cube.rot(0, 24.05 + yShift, 0, 0.88, 0.88, 0.82, 0, 0, 45, 0xECFDFF).emit(0.95f, 0xC8FAFF));

        for (int sgn : new int[]{-1, +1}) {
            double px = sgn * 2.35;
            for (int i = 0; i < 5; i++) {
                double ya = lerp(14.8, 20.1, i / 5.0) + yShift;
                double yb = lerp(14.8, 20.1, (i + 1) / 5.0) + yShift;
                double ym = 0.5 * (ya + yb);
                mainCubes.add(Cube.box(px - sgn * 0.22, ym, 0, 0.62, (yb - ya) + 0.04, 0.86, (i % 2 == 0) ? 0x183280 : 0x2E74EB)
                        .emit(0.42f, 0x3A8CFF));
                mainCubes.add(Cube.box(px + sgn * 0.24, ym, 0, 0.50, (yb - ya) + 0.04, 0.76, (i >= 2) ? 0xD2F8FF : 0x72C4FA)
                        .emit(0.72f, 0x9CEEFF));
            }
            mainCubes.add(Cube.rot(px + sgn * 0.04, 20.65 + yShift, 0, 0.90, 0.98, 0.78, 0, 0, sgn * 34, 0xE8FCFF)
                    .emit(0.95f, 0xC6FAFF));
        }

        // 5. Bioluminescent Sea-Coral / Guardian Fin Tendrils
        for (int sgn : new int[]{-1, +1}) {
            double[][] darkPts = {{3.4, 16.3}, {4.1, 16.9}, {3.9, 15.4}, {4.7, 15.8}, {4.5, 14.3}, {5.2, 14.6}};
            for (double[] pt : darkPts) {
                mainCubes.add(Cube.box(sgn * pt[0], pt[1] + yShift, -0.22, 0.76, 0.76, 0.42, 0x142254).emit(0.22f, 0x2648B8));
            }
            double[][] t1 = {{3.9, 14.8}, {4.6, 14.0}, {5.3, 13.3}, {5.9, 12.5}};
            int[] c1 = {0x2256CC, 0x429EFF, 0x98E8FF, 0xF0FFFF};
            for (int i = 0; i < t1.length; i++) {
                mainCubes.add(Cube.box(sgn * t1[i][0], t1[i][1] + yShift, 0.05, 0.80, 0.80, 0.50, c1[i]).emit(0.50f + i * 0.16f, c1[i]));
            }
            double[][] t2 = {{3.5, 13.1}, {4.1, 12.2}, {4.6, 11.2}, {5.1, 10.1}, {5.4, 9.1}};
            int[] c2 = {0x1E4CC0, 0x3892F8, 0x76D6FF, 0xC6F6FF, 0xF4FFFF};
            for (int i = 0; i < t2.length; i++) {
                mainCubes.add(Cube.box(sgn * t2[i][0], t2[i][1] + yShift, 0.14, 0.78, 0.86, 0.46, c2[i]).emit(0.46f + i * 0.13f, c2[i]));
            }
            double[][] t3 = {{2.5, 11.8}, {3.0, 10.8}, {3.4, 9.7}, {3.8, 8.6}, {4.2, 7.5}, {4.5, 6.4}, {4.7, 5.4}};
            int[] c3 = {0x1D48B8, 0x2D76E8, 0x52B8FF, 0x8CE2FF, 0xCEF6FF, 0xECFFFF, 0xFFFFFF};
            for (int i = 0; i < t3.length; i++) {
                double w = (i == t3.length - 1) ? 0.54 : 0.76;
                mainCubes.add(Cube.box(sgn * t3[i][0], t3[i][1] + yShift, 0.24, w, 0.90, 0.44, c3[i]).emit(0.42f + i * 0.09f, c3[i]));
            }
        }

        // 6. 360° 3D Spiraling Ocean Water Wave Ribbon (separate child bone `water_ribbon` for subtle GeckoLib pulse!)
        int waveSteps = 48;
        for (int i = 0; i < waveSteps; i++) {
            double t = i / (double) (waveSteps - 1);
            double y = lerp(11.6, -12.4, t) + yShift;
            double ang = Math.toRadians(lerp(-18.0, 295.0, Math.pow(t, 0.98)));
            double crestTop = Math.exp(-Math.pow((t - 0.18) / 0.15, 2.0)) * 3.45;
            double flareLow = Math.exp(-Math.pow((t - 0.74) / 0.14, 2.0)) * 2.85;
            double rad = lerp(2.1, 1.35, t) + crestTop + flareLow;
            double wx = Math.cos(ang) * rad;
            double wz = Math.sin(ang) * rad * 0.78;

            double bandThick = 0.64 + crestTop * 0.36 + flareLow * 0.18;
            if (t > 0.84) bandThick *= lerp(1.0, 0.35, (t - 0.84) / 0.16);

            int foamCol;
            float foamGlow;
            if (t < 0.30) { foamCol = 0xECFDFF; foamGlow = 0.96f; }
            else if (t < 0.60) { foamCol = 0x3EB4FF; foamGlow = 0.72f; }
            else if (t < 0.84) { foamCol = 0xD2FAFF; foamGlow = 0.94f; }
            else { foamCol = 0x1A3478; foamGlow = 0.28f; }
            waveCubes.add(Cube.box(wx, y, wz, bandThick * 0.92, 0.82, bandThick * 0.72, foamCol).emit(foamGlow, foamCol));

            if (t < 0.84) {
                double inScale = (t < 0.36) ? 0.76 : 0.84;
                int bodyCol = (t < 0.34) ? 0x3490EA : 0x1D4CA2;
                waveCubes.add(Cube.box(wx * inScale, y - 0.12, wz * inScale, bandThick * 0.86, 0.88, bandThick * 0.68, bodyCol)
                        .emit(0.42f, 0x3A98FF));
                if (t > 0.04 && t < 0.35) {
                    waveCubes.add(Cube.box(wx * 0.56, y - 0.24, wz * 0.56, 1.05, 0.96, 0.72, 0x1A3D82).emit(0.24f, 0x2662CC));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n")
          .append("      \"description\": {\n        \"identifier\": \"geometry.sea_trident\",\n")
          .append("        \"texture_width\": ").append(TEX_W).append(",\n        \"texture_height\": ").append(TEX_H).append(",\n")
          .append("        \"visible_bounds_width\": 3.0,\n        \"visible_bounds_height\": 4.0,\n        \"visible_bounds_offset\": [0, 0.5, 0]\n      },\n")
          .append("      \"bones\": [\n")
          .append("        {\n          \"name\": \"root\",\n          \"pivot\": [0, 0, 0]\n        },\n")
          .append("        {\n          \"name\": \"trident_body\",\n          \"parent\": \"root\",\n          \"pivot\": [0, 0, 0],\n          \"cubes\": [\n");
        appendCubesJson(sb, mainCubes, atlas);
        sb.append("          ]\n        },\n")
          .append("        {\n          \"name\": \"water_ribbon\",\n          \"parent\": \"trident_body\",\n          \"pivot\": [0, 0, 0],\n          \"cubes\": [\n");
        appendCubesJson(sb, waveCubes, atlas);
        sb.append("          ]\n        }\n      ]\n    }\n  ]\n}\n");

        Files.createDirectories(ASSETS.resolve("geo\\item"));
        Files.createDirectories(ASSETS.resolve("textures\\item"));
        Files.writeString(ASSETS.resolve("geo\\item\\sea_trident.geo.json"), sb.toString());
        ImageIO.write(atlas.diff, "png", ASSETS.resolve("textures\\item\\sea_trident_model.png").toFile());
        ImageIO.write(atlas.glow, "png", ASSETS.resolve("textures\\item\\sea_trident_model_glowmask.png").toFile());
        System.out.printf("WROTE sea_trident: %d main + %d wave cubes (%d swatches)%n",
                mainCubes.size(), waveCubes.size(), atlas.cache.size());
    }

    static void buildLanternAssets() throws Exception {
        PaletteAtlas atlas = new PaletteAtlas();
        List<Cube> ringCubes = new ArrayList<>();
        List<Cube> bodyCubes = new ArrayList<>();

        // Shift Y by -11.5 so the top hanging ring sits right in the player's Left Hand at Y = 0 and the lantern hangs cleanly below!
        double yShift = -11.5;

        // 1. Heavy dark-iron base pedestal
        bodyCubes.add(Cube.box(0, 1.0 + yShift, 0, 6.4, 2.0, 6.4, 0x222930).faces(0x364652, 0x151A1F, 0x222930));
        for (int sgn : new int[]{-1, +1}) {
            bodyCubes.add(Cube.box(0, 1.55 + yShift, sgn * 3.22, 2.6, 0.95, 0.14, 0x3E7B8C).emit(0.32f, 0x4EC0DC));
            bodyCubes.add(Cube.box(sgn * 3.22, 1.55 + yShift, 0, 0.14, 0.95, 2.6, 0x3E7B8C).emit(0.32f, 0x4EC0DC));
        }
        for (int sx : new int[]{-1, +1}) for (int sz : new int[]{-1, +1}) {
            bodyCubes.add(Cube.box(sx * 2.82, 1.05 + yShift, sz * 2.82, 0.98, 2.12, 0.98, 0x2A323A));
        }

        // 2. Inner Bioluminescent Deep-Sea Core (deep cobalt top -> electric cyan -> blazing aqua-white heart!)
        int[] coreCols = {0x9AF2FF, 0x4BC6FF, 0x248CE6, 0x1752A8, 0x0E2868};
        float[] coreGlows = {0.96f, 0.86f, 0.72f, 0.52f, 0.34f};
        for (int i = 0; i < coreCols.length; i++) {
            double y0 = lerp(2.05, 6.05, i / (double) coreCols.length) + yShift;
            double y1 = lerp(2.05, 6.05, (i + 1) / (double) coreCols.length) + yShift;
            bodyCubes.add(Cube.box(0, 0.5 * (y0 + y1), 0, 4.75, (y1 - y0) + 0.04, 4.75, coreCols[i])
                    .emit(coreGlows[i], coreCols[i]));
        }
        bodyCubes.add(Cube.box(0, 2.95 + yShift, 0, 4.86, 1.65, 1.55, 0xD8FCFF).emit(1.00f, 0xC8FAFF));
        bodyCubes.add(Cube.box(0, 2.95 + yShift, 0, 1.55, 1.65, 4.86, 0xD8FCFF).emit(1.00f, 0xC8FAFF));

        // 3. Wrought-iron corner pillars & vertical cage bars
        for (int sx : new int[]{-1, +1}) for (int sz : new int[]{-1, +1}) {
            bodyCubes.add(Cube.box(sx * 2.65, 4.10 + yShift, sz * 2.65, 1.02, 4.30, 1.02, 0x171D22));
        }
        for (double off : new double[]{-0.95, +0.95}) {
            bodyCubes.add(Cube.box(off, 4.10 + yShift, -2.68, 0.58, 4.25, 0.46, 0x181E24));
            bodyCubes.add(Cube.box(off, 4.10 + yShift, +2.68, 0.58, 4.25, 0.46, 0x181E24));
            bodyCubes.add(Cube.box(-2.68, 4.10 + yShift, off, 0.46, 4.25, 0.58, 0x181E24));
            bodyCubes.add(Cube.box(+2.68, 4.10 + yShift, off, 0.46, 4.25, 0.58, 0x181E24));
        }

        // 4. Stepped 2-tier weathered iron roof
        bodyCubes.add(Cube.box(0, 6.75 + yShift, 0, 7.6, 1.32, 7.6, 0x2B343B).faces(0x46535C, 0x1A2126, 0x2E3840));
        bodyCubes.add(Cube.box(0, 8.02 + yShift, 0, 5.2, 1.24, 5.2, 0x2F3940).faces(0x4C5A64, 0x1D2429, 0x333E46));

        // 5. Draped Seaweed / Kelp Moss over front-right corner
        bodyCubes.add(Cube.box(+2.25, 7.50 + yShift, -3.15, 2.85, 0.52, 1.45, 0x4EA68F).faces(0x62BEA5, 0x2C6458, 0x459680));
        bodyCubes.add(Cube.box(+3.15, 7.50 + yShift, -2.25, 1.45, 0.52, 2.85, 0x3D8A76).faces(0x52B097, 0x275B4F, 0x398270));
        bodyCubes.add(Cube.box(+3.32, 6.78 + yShift, -3.32, 1.35, 1.38, 1.35, 0x2C6458).faces(0x459680, 0x1F4840, 0x337466));
        bodyCubes.add(Cube.box(+2.28, 6.95 + yShift, -3.86, 1.65, 0.88, 0.42, 0x489D86));
        bodyCubes.add(Cube.box(+2.98, 5.55 + yShift, -3.32, 1.12, 1.95, 0.72, 0x3E8D78));
        bodyCubes.add(Cube.box(+3.32, 5.20 + yShift, -2.75, 0.62, 1.65, 1.05, 0x317262));
        bodyCubes.add(Cube.box(+2.96, 4.15 + yShift, -3.26, 0.92, 1.35, 0.62, 0x327564));
        bodyCubes.add(Cube.box(+3.08, 2.85 + yShift, -3.16, 0.52, 1.55, 0.46, 0x275D50));
        bodyCubes.add(Cube.box(-2.45, -0.18 + yShift, -2.45, 0.34, 0.48, 0.34, 0x255C50));

        // 6. Roof bail mount + clean hollow tilted iron hanging ring (pivoted cleanly at hand!)
        bodyCubes.add(Cube.box(-1.05, 9.15 + yShift, 0, 0.46, 1.15, 0.46, 0x252D34));
        bodyCubes.add(Cube.box(+1.05, 9.15 + yShift, 0, 0.46, 1.15, 0.46, 0x252D34));
        bodyCubes.add(Cube.box(0, 9.72 + yShift, 0, 2.55, 0.48, 0.48, 0x3A454E));
        bodyCubes.add(Cube.box(0, 10.35 + yShift, 0, 0.52, 0.96, 0.52, 0x2E373F));

        double rCy = 11.95 + yShift, rTilt = -16.0;
        double radT = Math.toRadians(-rTilt), ct = Math.cos(radT), st = Math.sin(radT);
        double halfW = 0.78, halfH = 0.98;
        ringCubes.add(Cube.rot(-halfW * ct, rCy - halfW * st, 0, 0.46, halfH * 2.0 + 0.44, 0.46, 0, 0, rTilt, 0x4A5660));
        ringCubes.add(Cube.rot(+halfW * ct, rCy + halfW * st, 0, 0.46, halfH * 2.0 + 0.44, 0.46, 0, 0, rTilt, 0x4A5660));
        ringCubes.add(Cube.rot(-halfH * st, rCy + halfH * ct, 0, halfW * 2.0 + 0.46, 0.48, 0.48, 0, 0, rTilt, 0x5E6D7A));
        ringCubes.add(Cube.rot(+halfH * st, rCy - halfH * ct, 0, halfW * 2.0 + 0.46, 0.48, 0.48, 0, 0, rTilt, 0x3A454F));

        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n")
          .append("      \"description\": {\n        \"identifier\": \"geometry.sea_lantern\",\n")
          .append("        \"texture_width\": ").append(TEX_W).append(",\n        \"texture_height\": ").append(TEX_H).append(",\n")
          .append("        \"visible_bounds_width\": 2.0,\n        \"visible_bounds_height\": 2.5,\n        \"visible_bounds_offset\": [0, -0.35, 0]\n      },\n")
          .append("      \"bones\": [\n")
          .append("        {\n          \"name\": \"root\",\n          \"pivot\": [0, 0, 0]\n        },\n")
          .append("        {\n          \"name\": \"lantern_ring\",\n          \"parent\": \"root\",\n          \"pivot\": [0, 0, 0],\n          \"cubes\": [\n");
        appendCubesJson(sb, ringCubes, atlas);
        sb.append("          ]\n        },\n")
          .append("        {\n          \"name\": \"lantern_body\",\n          \"parent\": \"lantern_ring\",\n          \"pivot\": [0, -1.2, 0],\n          \"cubes\": [\n");
        appendCubesJson(sb, bodyCubes, atlas);
        sb.append("          ]\n        }\n      ]\n    }\n  ]\n}\n");

        Files.writeString(ASSETS.resolve("geo\\item\\sea_lantern.geo.json"), sb.toString());
        ImageIO.write(atlas.diff, "png", ASSETS.resolve("textures\\item\\sea_lantern_model.png").toFile());
        ImageIO.write(atlas.glow, "png", ASSETS.resolve("textures\\item\\sea_lantern_model_glowmask.png").toFile());
        System.out.printf("WROTE sea_lantern: %d ring + %d body cubes (%d swatches)%n",
                ringCubes.size(), bodyCubes.size(), atlas.cache.size());
    }

    static void buildAnimationAndItemModel() throws Exception {
        // Gentle pendulum sway on lantern_body + zero bob on root (100% twitch-free!)
        String animJson = """
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.sea_weapons.idle": {
                      "loop": true,
                      "animation_length": 3.2,
                      "bones": {
                        "lantern_body": {
                          "rotation": {
                            "0.0": [2.5, 0, 3.5],
                            "0.8": [-1.5, 0, -2.8],
                            "1.6": [-2.5, 0, -3.5],
                            "2.4": [1.5, 0, 2.8],
                            "3.2": [2.5, 0, 3.5]
                          }
                        }
                      }
                    }
                  }
                }
                """;
        Files.createDirectories(ASSETS.resolve("animations\\item"));
        Files.writeString(ASSETS.resolve("animations\\item\\sea_weapons.animation.json"), animJson);

        String itemJson = """
                {
                  "parent": "builtin/entity",
                  "gui_light": "front",
                  "textures": {
                    "particle": "abyssalrpg:item/sea_trident"
                  },
                  "display": {
                    "thirdperson_righthand": {
                      "rotation": [0, 0, 0],
                      "translation": [0.0, 0.5, 0.0],
                      "scale": [0.95, 0.95, 0.95]
                    },
                    "thirdperson_lefthand": {
                      "rotation": [0, 0, 0],
                      "translation": [0.0, 0.5, 0.0],
                      "scale": [0.95, 0.95, 0.95]
                    },
                    "firstperson_righthand": {
                      "rotation": [0, 0, 0],
                      "translation": [2.8, -0.5, -2.6],
                      "scale": [0.82, 0.82, 0.82]
                    },
                    "firstperson_lefthand": {
                      "rotation": [0, 14, 0],
                      "translation": [0.0, 4.2, -1.6],
                      "scale": [0.80, 0.80, 0.80]
                    },
                    "ground": {
                      "rotation": [0, 0, 0],
                      "translation": [0, 3.5, 0],
                      "scale": [0.72, 0.72, 0.72]
                    },
                    "fixed": {
                      "rotation": [0, 0, 0],
                      "translation": [0, -1.0, 0],
                      "scale": [0.78, 0.78, 0.78]
                    },
                    "gui": {
                      "rotation": [0, 0, 0],
                      "translation": [0, 0, 0],
                      "scale": [1.0, 1.0, 1.0]
                    }
                  }
                }
                """;
        Files.createDirectories(ASSETS.resolve("models\\item"));
        Files.writeString(ASSETS.resolve("models\\item\\sea_trident.json"), itemJson);
    }

    static void buildGuiIcon32() throws Exception {
        // Hand-crafted crisp 32x32 pixel-art icon showing BOTH the diagonal Sea Trident + glowing Sea Lantern in bottom-right!
        BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setComposite(AlphaComposite.Clear); g.fillRect(0, 0, 32, 32);
        g.setComposite(AlphaComposite.SrcOver);

        // Helper for crisp single pixels
        int[][] px = new int[32][32];
        // 1. Diagonal Sea Trident shaft from bottom-left (3,28) to upper-right (17,14)
        for (int i = 0; i <= 15; i++) {
            int x = 3 + i, y = 28 - i;
            int col = (i % 2 == 0) ? 0xFF283240 : 0xFF4C5B6E;
            if (i == 0) col = 0xFFD8F6FF; // ice pommel!
            else if (i == 7 || i == 11) col = 0xFF9ACCE6; // silver collars
            px[y][x] = col;
            if (y > 0) px[y - 1][x] = (i == 0) ? 0xFF88CBE8 : 0xFF1A222D;
        }
        // 2. Spiraling cyan-white water ribbon around shaft
        int[][] wave = {{16,17},{17,18},{17,19},{16,20},{14,21},{11,21},{9,22},{7,24},{6,25},{6,26}};
        for (int i = 0; i < wave.length; i++) {
            int col = (i < 3 || i >= 7) ? 0xFFE6FCFF : 0xFF3CB8FF;
            px[wave[i][1]][wave[i][0]] = col;
        }
        // 3. Diamond crossguard crown centered around (18,12)
        int[][] crown = {{18,9},{19,10},{20,11},{21,12},{20,13},{19,14},{18,15},{17,14},{16,13},{15,12},{16,11},{17,10}};
        for (int[] p : crown) px[p[1]][p[0]] = 0xFFBCEAFF;
        // Sapphire gem core
        px[12][18] = 0xFF2B74F2; px[11][18] = 0xFF9CEEFF; px[12][19] = 0xFF1A46B0; px[13][18] = 0xFF1A46B0;
        // 4. Three glowing prongs pointing up-right!
        // Center main prong
        for (int i = 0; i <= 7; i++) {
            int x = 20 + i, y = 10 - i;
            if (x < 32 && y >= 0) {
                px[y][x] = (i >= 5) ? 0xFFE8FDFF : 0xFF3E98FF;
                if (y + 1 < 32) px[y + 1][x] = (i >= 6) ? 0xFFB0EEFF : 0xFF1C46A4;
            }
        }
        // Upper-left prong
        for (int i = 0; i <= 5; i++) {
            int x = 16 + i, y = 8 - i;
            if (x < 32 && y >= 0) px[y][x] = (i >= 3) ? 0xFFD8F9FF : 0xFF2F7FF0;
        }
        // Lower-right prong
        for (int i = 0; i <= 5; i++) {
            int x = 22 + i, y = 14 - i;
            if (x < 32 && y >= 0) px[y][x] = (i >= 3) ? 0xFFD8F9FF : 0xFF2F7FF0;
        }
        // Glowing fin tendrils under crown
        int[][] fins = {{13,12},{12,13},{11,15},{14,16},{13,17},{19,17},{20,18},{22,16},{23,17}};
        for (int[] f : fins) px[f[1]][f[0]] = 0xFFA4EEFF;

        // 5. Sea Lantern in bottom-right corner (x=20..30, y=19..30) with glowing ocean core & teal kelp!
        // Hanging ring
        px[18][25] = 0xFF566470; px[19][24] = 0xFF44515C; px[19][26] = 0xFF44515C; px[20][25] = 0xFF3A4650;
        // Roof
        for (int x = 22; x <= 28; x++) px[21][x] = 0xFF42505A;
        for (int x = 21; x <= 29; x++) px[22][x] = 0xFF2A343C;
        // Iron pillars + glowing ocean-blue core + bright white heart
        for (int y = 23; y <= 27; y++) {
            px[y][22] = 0xFF1A2228; px[y][25] = 0xFF1A2228; px[y][28] = 0xFF1A2228;
            int core = (y <= 24) ? 0xFF184EA8 : ((y == 25) ? 0xFF30A4FF : 0xFFB8F8FF);
            px[y][23] = core; px[y][24] = core; px[y][26] = core; px[y][27] = core;
        }
        // Iron pedestal base with cyan reflection
        for (int x = 21; x <= 29; x++) {
            px[28][x] = (x >= 23 && x <= 27) ? 0xFF428496 : 0xFF263038;
            px[29][x] = 0xFF1E262E;
        }
        // Signature prismarine-teal kelp drape on front-right roof corner!
        px[21][27] = 0xFF56B49B; px[21][28] = 0xFF56B49B;
        px[22][28] = 0xFF429680; px[23][28] = 0xFF3E8C78; px[24][28] = 0xFF357A68; px[25][28] = 0xFF2B6658;

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                if (px[y][x] != 0) icon.setRGB(x, y, px[y][x]);
            }
        }
        g.dispose();
        ImageIO.write(icon, "png", ASSETS.resolve("textures\\item\\sea_trident.png").toFile());
        System.out.println("WROTE 32x32 GUI icon: sea_trident.png");
    }

    public static void main(String[] args) throws Exception {
        buildTridentAssets();
        buildLanternAssets();
        buildAnimationAndItemModel();
        buildGuiIcon32();
    }
}
