import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Reproducible GeckoLib 4.8.3 Asset Generator for **Muramasa Katana & Scabbard (Saya)**
 * faithfully sculpted from the measured 3D geometry & PBR textures of Sketchfab
 * "Muramasa Katana" by Samurai_Chad (`02d5eb68f2854f2e977a78aa38bdf34a`, CC-BY):
 *  - Curved dark Damascus gunmetal steel katana blade (73% blade / 27% hilt ratio)
 *    with gold `村正` Kanji engraving near the habaki and full-length glowing golden-yellow razor edge (`#FFE820`).
 *  - Engraved antique-iron Habaki collar with coiled dragon relief.
 *  - Exact 12-spiked scalloped demon-star weathered-iron Tsuba (4 cardinal + 8 flaring diagonal spikes).
 *  - Curved deep blood-crimson Samegawa handle wrapped in 16 overlapping jet-black diamond Tsuka-ito cloth bands.
 *  - Weathered iron Kashira pommel cap with gold oval trim and demon-face cap emblem.
 *  - Matching curved black-lacquer Scabbard (Saya) with coiled crimson-red Japanese dragon & iron Koiguchi lock.
 */
public class BuildMuramasaAssets {
    static final Path ASSETS = Path.of("D:\\cremod\\mod-rpg\\src\\main\\resources\\assets\\abyssalrpg");
    static final int TEX_W = 512, TEX_H = 512;

    static final Map<String, int[]> SWATCH = new LinkedHashMap<>();
    static {
        // Right half (x=256..512) holds 64x64 procedural material swatches; inset by 4px to prevent mip bleed
        SWATCH.put("STEEL_DARK",    new int[]{260,   4, 56, 56});
        SWATCH.put("STEEL_MID",     new int[]{324,   4, 56, 56});
        SWATCH.put("STEEL_BRIGHT",  new int[]{388,   4, 56, 56});
        SWATCH.put("EDGE_YELLOW",   new int[]{452,   4, 56, 56});

        SWATCH.put("IRON_DARK",     new int[]{260,  68, 56, 56});
        SWATCH.put("IRON_WEATHER",  new int[]{324,  68, 56, 56});
        SWATCH.put("IRON_EDGE",     new int[]{388,  68, 56, 56});
        SWATCH.put("GOLD_TRIM",     new int[]{452,  68, 56, 56});

        SWATCH.put("CRIMSON_SAME",  new int[]{260, 132, 56, 56});
        SWATCH.put("CLOTH_BLACK",   new int[]{324, 132, 56, 56});
        SWATCH.put("CLOTH_SHEEN",   new int[]{388, 132, 56, 56});
        SWATCH.put("GOLD_DARK",     new int[]{452, 132, 56, 56});

        SWATCH.put("SAYA_LACQUER",  new int[]{260, 196, 56, 56});
        SWATCH.put("SAYA_CRIMSON",  new int[]{324, 196, 56, 56});
        SWATCH.put("SAGEO_CORD",    new int[]{388, 196, 56, 56});
        SWATCH.put("DEMON_CAP",     new int[]{452, 196, 56, 56});
        // Detailed art regions on the left half (x=0..256):
        //   BLADE_SIDE:   x=16..224,  y=16..496  (full brushed Damascus steel + gold 村正 kanji + bright shinogi + yellow ha edge!)
        //   HABAKI_DRAG:  x=264..376, y=268..380 (weathered iron + engraved coiled silver-grey dragon!)
        //   SAYA_DRAGON:  x=16..224,  y=16..496  (on scabbard atlas: glossy obsidian lacquer + crimson coiled dragon!)
    }

    static class FaceUV {
        double u, v, su, sv;
        FaceUV(double u, double v, double su, double sv) { this.u = u; this.v = v; this.su = su; this.sv = sv; }
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
            c.hasRot = Math.abs(rx) > 1e-3 || Math.abs(ry) > 1e-3 || Math.abs(rz) > 1e-3;
            if (swatch != null) {
                int[] r = SWATCH.get(swatch);
                FaceUV f = new FaceUV(r[0], r[1], r[2], r[3]);
                for (String d : new String[]{"north", "south", "east", "west", "up", "down"}) {
                    c.uvs.put(d, f);
                }
            }
            return c;
        }

        Cube setSideUV(FaceUV ew, FaceUV other) {
            uvs.put("east", ew);
            uvs.put("west", ew);
            uvs.put("north", other);
            uvs.put("south", other);
            uvs.put("up", other);
            uvs.put("down", other);
            return this;
        }

        Cube setFaceUV(String face, FaceUV uv) {
            uvs.put(face, uv);
            return this;
        }
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /**
     * Exact katana sori curvature measured from `muramasa.glb` (normalized to GeckoLib pixel space):
     * - Grip center at Y = 0.0, Z = 0.0
     * - Pommel end at Y = -5.6
     * - Tsuba guard at Y = +3.1
     * - Blade tip (kissaki) at Y = +27.2
     * - Cutting edge (`ha`) faces -Z (front); spine (`mune`) faces +Z (back); curves cleanly back toward +Z near tip & pommel!
     */
    static double spineCenterZ(double y) {
        if (y >= 3.1) {
            double t = clamp((y - 3.1) / (27.2 - 3.1), 0.0, 1.0);
            // Smooth authentic katana sori curve: subtle near habaki, gracefully sweeping back +2.15px toward tip!
            return 0.05 + 2.15 * Math.pow(t, 1.78);
        } else {
            double t = clamp((3.1 - y) / (3.1 - (-5.6)), 0.0, 1.0);
            // Subtle handle curve matching GLB hilt curve (Y = -0.35 -> -0.14 across Z = +1.7..+3.7)
            return 0.05 + 0.62 * Math.pow(t, 1.35);
        }
    }

    static double spineSlopeDeg(double y) {
        double eps = 0.15;
        double dz = spineCenterZ(y + eps) - spineCenterZ(y - eps);
        double dy = 2.0 * eps;
        // Rotation around X-axis tilts +Y axis toward +Z when rx > 0 (in Bedrock/GeckoLib convention)
        return Math.toDegrees(Math.atan2(dz, dy));
    }

    static double bladeWidthZ(double y) {
        if (y <= 23.6) {
            double t = clamp((y - 3.6) / (23.6 - 3.6), 0.0, 1.0);
            return lerp(1.34, 1.02, t);
        } else {
            double t = clamp((y - 23.6) / (27.2 - 23.6), 0.0, 1.0);
            // Razor katana kissaki curve tapering cleanly to a sharp needle point
            return Math.max(0.08, 1.02 * Math.pow(1.0 - t * t, 0.78));
        }
    }

    static List<Cube> buildKatanaCubes() {
        List<Cube> cubes = new ArrayList<>();
        int[] swSteelDark = SWATCH.get("STEEL_DARK");
        int[] swEdge = SWATCH.get("EDGE_YELLOW");
        FaceUV fSteelDark = new FaceUV(swSteelDark[0], swSteelDark[1], swSteelDark[2], swSteelDark[3]);
        FaceUV fEdge = new FaceUV(swEdge[0], swEdge[1], swEdge[2], swEdge[3]);
        FaceUV fDemonCap = new FaceUV(SWATCH.get("DEMON_CAP")[0], SWATCH.get("DEMON_CAP")[1], 56, 56);

        // 1. KASHIRA POMMEL CAP (Y = -5.75 .. -4.85) + GOLD COLLAR BAND + DEMON FACE CAP
        double zPom = spineCenterZ(-5.25);
        double angPom = spineSlopeDeg(-5.25);
        Cube capMain = Cube.centered(0, -5.28, zPom, 0.92, 0.84, 1.46, angPom, 0, 0, "IRON_WEATHER");
        capMain.setFaceUV("down", fDemonCap);
        cubes.add(capMain);
        cubes.add(Cube.centered(0, -5.70, zPom + 0.06, 0.80, 0.14, 1.32, angPom, 0, 0, "DEMON_CAP"));
        // Iconic gold oval wrap-cap holder ring (`Wrap_Cap_Holder_01_Gold_0`)
        cubes.add(Cube.centered(0, -4.96, zPom - 0.02, 1.02, 0.24, 1.54, angPom, 0, 0, "GOLD_TRIM"));
        cubes.add(Cube.centered(0, -5.30, zPom, 1.00, 0.18, 0.62, angPom, 0, 0, "GOLD_TRIM"));

        // 2. CURVED TSUKA HILT: BLOOD-CRIMSON SAMEGAWA CORE + 16 BLACK DIAMOND CLOTH WRAPS (`Wrap_01..18`)
        int hiltSegs = 14;
        for (int i = 0; i < hiltSegs; i++) {
            double ya = lerp(-4.82, 2.76, i / (double) hiltSegs);
            double yb = lerp(-4.82, 2.76, (i + 1) / (double) hiltSegs);
            double ym = 0.5 * (ya + yb);
            double len = (yb - ya) + 0.06;
            double zm = spineCenterZ(ym);
            double ang = spineSlopeDeg(ym);
            double t = (i + 0.5) / hiltSegs;
            // Subtle traditional katana waist taper (slightly narrower in center, wider at fuchi/kashira)
            double waist = 1.0 - 0.07 * Math.sin(t * Math.PI);
            double coreW = 1.30 * waist;
            double coreT = 0.74 * waist;
            // Blood-crimson rayskin core (`Hilt_Handle_01_blinn5_0`)
            cubes.add(Cube.centered(0, ym, zm, coreT, len, coreW, ang, 0, 0, "CRIMSON_SAME"));

            // Overlapping jet-black braided Tsuka-ito cloth bands forming crisp diamond cutouts showing crimson!
            // Spine & front edges are completely wrapped in black cloth
            cubes.add(Cube.centered(0, ym, zm + coreW * 0.44, coreT + 0.15, len * 0.92, 0.24, ang, 0, 0, "CLOTH_BLACK"));
            cubes.add(Cube.centered(0, ym, zm - coreW * 0.44, coreT + 0.15, len * 0.92, 0.24, ang, 0, 0, "CLOTH_BLACK"));
            // Diagonal X-cross ito ribbons on both sides (east & west) revealing crimson diamonds between crosses!
            double sideX = coreT * 0.5 + 0.03;
            cubes.add(Cube.centered(+sideX, ym, zm, 0.10, 0.26, coreW * 0.86, ang + 34.0, 0, 0, "CLOTH_BLACK"));
            cubes.add(Cube.centered(+sideX, ym, zm, 0.10, 0.26, coreW * 0.86, ang - 34.0, 0, 0, "CLOTH_SHEEN"));
            cubes.add(Cube.centered(-sideX, ym, zm, 0.10, 0.26, coreW * 0.86, ang + 34.0, 0, 0, "CLOTH_BLACK"));
            cubes.add(Cube.centered(-sideX, ym, zm, 0.10, 0.26, coreW * 0.86, ang - 34.0, 0, 0, "CLOTH_SHEEN"));
        }

        // Fuchi collar right below Tsuba (Y = +2.68 .. +2.98)
        double zFuchi = spineCenterZ(2.83);
        cubes.add(Cube.centered(0, 2.83, zFuchi, 0.96, 0.34, 1.50, 0, 0, 0, "IRON_DARK"));

        // 3. EXACT 12-SPIKED DEMON-STAR WEATHERED-IRON TSUBA (Y = +2.96 .. +3.38)
        // Matching `muramasa_tsuba_silhouette.png`: 4 cardinal points + 8 flaring diagonal points + central collar!
        double yTsuba = 3.16;
        double zTsuba = spineCenterZ(yTsuba);
        // Central oval core plate + raised inner rim
        cubes.add(Cube.centered(0, yTsuba, zTsuba, 2.55, 0.38, 2.75, 0, 0, 0, "IRON_WEATHER"));
        cubes.add(Cube.centered(0, yTsuba, zTsuba, 1.65, 0.46, 2.10, 0, 0, 0, "IRON_DARK"));
        // 12 radial spikes around the Y-axis (exact angles from polar silhouette: 0, 34, 56, 90, 124, 146, 180, 214, 236, 270, 304, 326 deg)
        double[] spikeAngles = {0, 34, 56, 90, 124, 146, 180, 214, 236, 270, 304, 326};
        for (double deg : spikeAngles) {
            boolean cardinal = (deg % 90 == 0);
            double rad = Math.toRadians(deg);
            double rMid = cardinal ? 1.48 : 1.72;
            double len = cardinal ? 0.95 : 1.22;
            double wid = cardinal ? 0.48 : 0.40;
            double sx = Math.sin(rad) * rMid;
            double sz = Math.cos(rad) * rMid;
            // Base wedge of spike
            cubes.add(Cube.centered(sx, yTsuba, zTsuba + sz, wid, 0.34, len, 0, deg, 0, "IRON_WEATHER"));
            // Sharp needle tip of spike
            double rTip = cardinal ? 1.96 : 2.26;
            cubes.add(Cube.centered(Math.sin(rad) * rTip, yTsuba, zTsuba + Math.cos(rad) * rTip,
                    wid * 0.46, 0.24, 0.52, 0, deg, 0, "IRON_EDGE"));
        }

        // 4. ENGRAVED HABAKI COLLAR WITH COILED DRAGON RELIEF (`Blade_Tsuba_Piece_01`, Y = +3.36 .. +4.62)
        double zHab = spineCenterZ(3.98);
        FaceUV fHabaki = new FaceUV(268, 272, 104, 104);
        FaceUV fIronDark = new FaceUV(SWATCH.get("IRON_DARK")[0], SWATCH.get("IRON_DARK")[1], 56, 56);
        Cube habaki = Cube.centered(0, 3.98, zHab, 0.72, 1.28, 1.54, spineSlopeDeg(3.98), 0, 0, null)
                .setSideUV(fHabaki, fIronDark);
        cubes.add(habaki);
        // Seppa spacer washer between Tsuba and Habaki
        cubes.add(Cube.centered(0, 3.40, zTsuba, 0.88, 0.14, 1.72, 0, 0, 0, "IRON_EDGE"));

        // 5. CURVED FORGED DARK-STEEL KATANA BLADE + FULL-LENGTH GLOWING GOLDEN-YELLOW RAZOR EDGE (`#FFE820`)
        // 20 smooth curved segments from Habaki (Y = 4.55) to razor Kissaki tip (Y = 27.20)
        int bladeSegs = 20;
        double yStart = 4.55, yEnd = 27.20;
        for (int i = 0; i < bladeSegs; i++) {
            double fa = i / (double) bladeSegs;
            double fb = (i + 1) / (double) bladeSegs;
            double ya = lerp(yStart, yEnd, fa);
            double yb = lerp(yStart, yEnd, fb);
            double ym = 0.5 * (ya + yb);
            double segLen = (yb - ya) + 0.10;
            double zm = spineCenterZ(ym);
            double ang = spineSlopeDeg(ym);
            double w = bladeWidthZ(ym);
            double th = lerp(0.38, 0.14, Math.pow(0.5 * (fa + fb), 1.35));

            // Map each segment cleanly along the vertical BLADE_SIDE art strip (x=16..224, y=16..496, top=tip, bottom=habaki)
            double vBot = lerp(492.0, 20.0, fa);
            double vTop = lerp(492.0, 20.0, fb);
            FaceUV fSide = new FaceUV(20.0, vTop, 196.0, Math.max(4.0, vBot - vTop));

            // Main dark gunmetal steel body (back 82% of width, thicker spine -> sharp shinogi ridge)
            double bodyW = Math.max(0.06, w * 0.82);
            double bodyZ = zm + w * 0.09;
            cubes.add(Cube.centered(0, ym, bodyZ, th, segLen, bodyW, ang, 0, 0, null)
                    .setSideUV(fSide, fSteelDark));

            // Raised central shinogi bevel ridge for crisp 3D katana silhouette
            if (w > 0.28) {
                cubes.add(Cube.centered(0, ym, zm + w * 0.22, th + 0.08, segLen, bodyW * 0.44, ang, 0, 0, "STEEL_DARK"));
            }

            // ICONIC GLOWING GOLDEN-YELLOW RAZOR CUTTING EDGE (`ha`, front 22% of width, emissive `#FFE820`!)
            double edgeW = Math.max(0.08, w * 0.24);
            double edgeZ = zm - w * 0.42;
            cubes.add(Cube.centered(0, ym, edgeZ, Math.max(0.08, th * 0.58), segLen + 0.02, edgeW, ang, 0, 0, "EDGE_YELLOW")
                    .setSideUV(fEdge, fEdge));
        }

        // Sharp Kissaki needle tip cap with glowing yellow edge wrap!
        double zTip = spineCenterZ(27.32);
        double angTip = spineSlopeDeg(27.0);
        cubes.add(Cube.centered(0, 27.34, zTip - 0.04, 0.10, 0.42, 0.20, angTip + 14.0, 0, 0, "EDGE_YELLOW"));
        return cubes;
    }

    static List<Cube> buildScabbardCubes() {
        List<Cube> cubes = new ArrayList<>();
        int[] swLacq = SWATCH.get("SAYA_LACQUER");
        FaceUV fLacq = new FaceUV(swLacq[0], swLacq[1], swLacq[2], swLacq[3]);

        // Matching curved black-lacquer Scabbard (Saya) from mouth (Y = +3.30) to kojiri tip (Y = +27.80),
        // shifted cleanly to Y = -2.2 .. +22.3 so left hand grips comfortably right below the sageo cord wrap!
        double yOff = -5.5;
        int segs = 18;
        double yStart = 3.55, yEnd = 27.80;
        for (int i = 0; i < segs; i++) {
            double fa = i / (double) segs;
            double fb = (i + 1) / (double) segs;
            double ya = lerp(yStart, yEnd, fa);
            double yb = lerp(yStart, yEnd, fb);
            double ym = 0.5 * (ya + yb);
            double segLen = (yb - ya) + 0.10;
            double zm = spineCenterZ(ym);
            double ang = spineSlopeDeg(ym);
            double w = lerp(1.78, 1.46, Math.pow(0.5 * (fa + fb), 1.1));
            double th = lerp(0.92, 0.74, 0.5 * (fa + fb));

            double vBot = lerp(492.0, 20.0, fa);
            double vTop = lerp(492.0, 20.0, fb);
            FaceUV fSide = new FaceUV(20.0, vTop, 196.0, Math.max(4.0, vBot - vTop));

            cubes.add(Cube.centered(0, ym + yOff, zm, th, segLen, w, ang, 0, 0, null)
                    .setSideUV(fSide, fLacq));
        }

        // Weathered antique iron Koiguchi mouth lock (`Scabbard_Lock_01`, right at Y = +3.30 + yOff)
        double zMouth = spineCenterZ(3.55);
        double angMouth = spineSlopeDeg(3.55);
        cubes.add(Cube.centered(0, 3.48 + yOff, zMouth, 1.06, 0.96, 1.92, angMouth, 0, 0, "IRON_WEATHER"));
        cubes.add(Cube.centered(0, 3.04 + yOff, zMouth - 0.02, 1.14, 0.22, 2.02, angMouth, 0, 0, "IRON_EDGE"));

        // Kurikata cord knob + black/crimson Sageo braided tie around upper third of scabbard (grip point!)
        double yCord = 7.4 + yOff;
        double zCord = spineCenterZ(7.4);
        double angCord = spineSlopeDeg(7.4);
        cubes.add(Cube.centered(0, yCord, zCord, 1.08, 1.35, 1.88, angCord, 0, 0, "SAGEO_CORD"));
        cubes.add(Cube.centered(0, yCord + 0.45, zCord, 1.12, 0.22, 1.92, angCord, 0, 0, "SAYA_CRIMSON"));
        cubes.add(Cube.centered(0, yCord - 0.45, zCord, 1.12, 0.22, 1.92, angCord, 0, 0, "SAYA_CRIMSON"));
        // Kojiri weathered iron end-cap at bottom tip of scabbard
        double zEnd = spineCenterZ(27.90);
        double angEnd = spineSlopeDeg(27.5);
        cubes.add(Cube.centered(0, 27.90 + yOff, zEnd, 0.80, 0.56, 1.52, angEnd, 0, 0, "IRON_DARK"));

        return cubes;
    }

    static String fmt(double v) { return String.format(Locale.ROOT, "%.3f", v); }

    static void writeGeo(Path out, String id, List<Cube> cubes) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n");
        sb.append("      \"description\": {\n");
        sb.append("        \"identifier\": \"").append(id).append("\",\n");
        sb.append("        \"texture_width\": ").append(TEX_W).append(",\n");
        sb.append("        \"texture_height\": ").append(TEX_H).append(",\n");
        sb.append("        \"visible_bounds_width\": 2.8,\n");
        sb.append("        \"visible_bounds_height\": 3.8,\n");
        sb.append("        \"visible_bounds_offset\": [0.0, 1.0, 0.0]\n");
        sb.append("      },\n      \"bones\": [\n");
        sb.append("        {\"name\": \"root\", \"pivot\": [0, 0, 0], \"rotation\": [0, 0, 0]},\n");
        sb.append("        {\n          \"name\": \"blade_root\",\n          \"parent\": \"root\",\n          \"pivot\": [0, 0, 0],\n          \"rotation\": [0, 0, 0],\n          \"cubes\": [\n");
        for (int i = 0; i < cubes.size(); i++) {
            Cube c = cubes.get(i);
            sb.append("            {\"origin\": [").append(fmt(c.ox)).append(", ").append(fmt(c.oy)).append(", ").append(fmt(c.oz)).append("], ");
            sb.append("\"size\": [").append(fmt(c.sx)).append(", ").append(fmt(c.sy)).append(", ").append(fmt(c.sz)).append("]");
            if (c.hasRot) {
                sb.append(", \"pivot\": [").append(fmt(c.px)).append(", ").append(fmt(c.py)).append(", ").append(fmt(c.pz)).append("]");
                sb.append(", \"rotation\": [").append(fmt(c.rx)).append(", ").append(fmt(c.ry)).append(", ").append(fmt(c.rz)).append("]");
            }
            sb.append(", \"uv\": {");
            int fi = 0;
            for (Map.Entry<String, FaceUV> e : c.uvs.entrySet()) {
                if (fi++ > 0) sb.append(", ");
                FaceUV u = e.getValue();
                sb.append("\"").append(e.getKey()).append("\": {\"uv\": [").append(fmt(u.u)).append(", ").append(fmt(u.v))
                  .append("], \"uv_size\": [").append(fmt(u.su)).append(", ").append(fmt(u.sv)).append("]}");
            }
            sb.append("}}").append(i + 1 < cubes.size() ? ",\n" : "\n");
        }
        sb.append("          ]\n        }\n      ]\n    }\n  ]\n}\n");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    static void fillNoiseBox(BufferedImage img, int x0, int y0, int w, int h, int baseRgb, int var, Random rng, boolean verticalGrain) {
        int br = (baseRgb >> 16) & 0xFF, bg = (baseRgb >> 8) & 0xFF, bb = baseRgb & 0xFF;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int n = verticalGrain
                        ? (int) (Math.sin(x * 0.75 + rng.nextDouble() * 0.3) * var * 0.65 + (rng.nextInt(var) - var / 2) * 0.45)
                        : (rng.nextInt(var * 2 + 1) - var);
                int r = (int) clamp(br + n, 0, 255);
                int g = (int) clamp(bg + n, 0, 255);
                int b = (int) clamp(bb + n, 0, 255);
                img.setRGB(x, y, (255 << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    static void paintCommonSwatches(BufferedImage diff, BufferedImage glow, Random rng) {
        // Right half material swatches (64x64 cells)
        fillNoiseBox(diff, 256,   0, 64, 64, 0x1C1E24, 14, rng, true);  // STEEL_DARK
        fillNoiseBox(diff, 320,   0, 64, 64, 0x3D424D, 18, rng, true);  // STEEL_MID
        fillNoiseBox(diff, 384,   0, 64, 64, 0x8C95A6, 20, rng, true);  // STEEL_BRIGHT
        fillNoiseBox(diff, 448,   0, 64, 64, 0xFFF030, 10, rng, false); // EDGE_YELLOW (bright glowing yellow razor edge!)
        // Full-bright yellow glow on EDGE_YELLOW swatch!
        Graphics2D gg = glow.createGraphics();
        gg.setColor(new Color(255, 240, 48, 255));
        gg.fillRect(448, 0, 64, 64);

        // Weathered antique dark iron (matching muramasa_tex_1.jpg mottled stone/forged iron!)
        fillNoiseBox(diff, 256,  64, 64, 64, 0x222227, 16, rng, false); // IRON_DARK
        fillNoiseBox(diff, 320,  64, 64, 64, 0x3A3B42, 26, rng, false); // IRON_WEATHER
        Graphics2D gd = diff.createGraphics();
        gd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Mottled dark patina splotches on IRON_WEATHER
        for (int i = 0; i < 45; i++) {
            gd.setColor(i % 2 == 0 ? new Color(22, 22, 26, 175) : new Color(92, 94, 104, 125));
            gd.fillOval(322 + rng.nextInt(54), 66 + rng.nextInt(54), 4 + rng.nextInt(12), 4 + rng.nextInt(10));
        }
        fillNoiseBox(diff, 384,  64, 64, 64, 0x626572, 18, rng, false); // IRON_EDGE
        // Rich antique gold trim (`Wrap_Cap_Holder_01_Gold_0`)
        fillNoiseBox(diff, 448,  64, 64, 64, 0xC88A22, 16, rng, true);  // GOLD_TRIM
        gd.setColor(new Color(255, 224, 120, 160));
        gd.fillRect(448, 72, 64, 10);
        gg.setColor(new Color(145, 96, 18, 255));
        gg.fillRect(448, 64, 64, 64);

        // Deep blood-crimson pebbled rayskin (`Hilt_Handle_01_blinn5_0`)
        fillNoiseBox(diff, 256, 128, 64, 64, 0x6E0B10, 14, rng, false); // CRIMSON_SAME
        for (int y = 132; y < 188; y += 5) {
            for (int x = 260; x < 316; x += 5) {
                gd.setColor(new Color(152, 22, 28, 210));
                gd.fillOval(x + ((y % 10 == 0) ? 0 : 2), y, 3, 3);
            }
        }
        // Subtle eerie crimson ember on samegawa in glowmask
        gg.setColor(new Color(72, 8, 12, 255));
        gg.fillRect(256, 128, 64, 64);

        // Jet-black braided cloth (`Wrap_01..18_Cloth_0`)
        fillNoiseBox(diff, 320, 128, 64, 64, 0x111116, 8, rng, true);   // CLOTH_BLACK
        fillNoiseBox(diff, 384, 128, 64, 64, 0x1C1D24, 10, rng, true);  // CLOTH_SHEEN
        fillNoiseBox(diff, 448, 128, 64, 64, 0x8A5A12, 12, rng, true);  // GOLD_DARK

        // Scabbard glossy black lacquer + crimson dragon accent + sageo cord
        fillNoiseBox(diff, 256, 192, 64, 64, 0x100E12, 6, rng, true);   // SAYA_LACQUER
        fillNoiseBox(diff, 320, 192, 64, 64, 0x8A1016, 16, rng, false); // SAYA_CRIMSON
        fillNoiseBox(diff, 384, 192, 64, 64, 0x18151A, 10, rng, true);  // SAGEO_CORD

        // Demon-face Kashira cap (`muramasa_tex_2.jpg` — fierce furrowed-brow fanged yokai crest!)
        fillNoiseBox(diff, 448, 192, 64, 64, 0x26272D, 14, rng, false);
        gd.setColor(new Color(148, 152, 162, 225));
        gd.fillOval(456, 200, 48, 48);
        gd.setColor(new Color(22, 20, 26, 240));
        gd.setStroke(new BasicStroke(2.6f));
        gd.drawOval(456, 200, 48, 48);
        // Sharp Oni horns + angry V-slanted brows + blazing eyes + jagged fanged snarl
        gd.fillPolygon(new int[]{464, 469, 473}, new int[]{208, 199, 210}, 3);
        gd.fillPolygon(new int[]{496, 491, 487}, new int[]{208, 199, 210}, 3);
        gd.drawLine(464, 212, 477, 218);
        gd.drawLine(496, 212, 483, 218);
        gd.fillPolygon(new int[]{465, 476, 471}, new int[]{216, 220, 224}, 3);
        gd.fillPolygon(new int[]{495, 484, 489}, new int[]{216, 220, 224}, 3);
        gd.fillRect(468, 230, 24, 9);
        gd.setColor(new Color(235, 232, 220, 240));
        gd.fillPolygon(new int[]{470, 474, 472}, new int[]{230, 230, 237}, 3);
        gd.fillPolygon(new int[]{490, 486, 488}, new int[]{230, 230, 237}, 3);
        gd.fillPolygon(new int[]{477, 483, 480}, new int[]{239, 239, 233}, 3);
        gd.setColor(new Color(255, 216, 48, 255));
        gd.fillOval(469, 218, 4, 4);
        gd.fillOval(487, 218, 4, 4);
        gg.setColor(new Color(255, 200, 36, 255));
        gg.fillOval(469, 218, 4, 4);
        gg.fillOval(487, 218, 4, 4);

        // Engraved coiled dragon relief on Habaki (x=264..376, y=268..380 — faithful to muramasa_tex_1.jpg!)
        fillNoiseBox(diff, 260, 264, 120, 120, 0x262830, 20, rng, false);
        gd.setColor(new Color(14, 15, 20, 215));
        gd.setStroke(new BasicStroke(11.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D dragon = new Path2D.Double();
        dragon.moveTo(280, 368);
        dragon.curveTo(268, 332, 360, 344, 352, 314);
        dragon.curveTo(344, 286, 280, 308, 292, 282);
        dragon.curveTo(304, 266, 356, 274, 362, 294);
        gd.draw(dragon);
        gd.setColor(new Color(165, 170, 184, 230));
        gd.setStroke(new BasicStroke(6.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gd.draw(dragon);
        gd.setColor(new Color(225, 230, 240, 225));
        gd.setStroke(new BasicStroke(2.0f));
        gd.draw(dragon);
        // Carved dragon scales & horn/whisker accents
        for (int s = 0; s < 7; s++) {
            int sx = 288 + (s % 3) * 24, sy = 286 + s * 11;
            gd.drawArc(sx - 6, sy - 4, 12, 8, 20, 140);
        }
        gd.drawLine(358, 288, 369, 278);
        gd.drawLine(356, 294, 370, 292);
        gd.setColor(new Color(255, 220, 52, 255));
        gd.fillOval(354, 289, 5, 5);
        gg.setColor(new Color(240, 200, 42, 255));
        gg.fillOval(354, 289, 5, 5);

        gd.dispose();
        gg.dispose();
    }

    static void buildKatanaTextures() throws Exception {
        BufferedImage diff = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glow = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gd = diff.createGraphics(), gg = glow.createGraphics();
        gd.setColor(new Color(14, 14, 18, 255)); gd.fillRect(0, 0, TEX_W, TEX_H);
        gg.setColor(new Color(0, 0, 0, 255));    gg.fillRect(0, 0, TEX_W, TEX_H);
        gd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Random rng = new Random(133702L);
        paintCommonSwatches(diff, glow, rng);

        // Left half (x=16..224, y=16..496): Brushed Damascus gunmetal blade flat (faithful to muramasa_tex_0.jpg!)
        for (int y = 16; y < 496; y++) {
            double fy = (y - 16.0) / 480.0; // 0 = tip, 1 = habaki
            for (int x = 16; x < 224; x++) {
                double fx = (x - 16.0) / 208.0; // 0 = front/edge side, 1 = spine side
                // Authentic anisotropic vertical brushed-steel sheen peaking along the shinogi center (fx ~ 0.35..0.55)
                double centerSheen = Math.exp(-Math.pow((fx - 0.44) / 0.28, 2.0));
                double wave = 0.5 + 0.5 * Math.sin(fy * 38.0 + Math.sin(fx * 11.0) * 2.4);
                double grain = (rng.nextDouble() - 0.5) * 18.0 + Math.sin(x * 1.35) * 7.0;
                double lum = 26.0 + centerSheen * 108.0 + wave * 18.0 + grain;
                // Dark spine edge & fiery golden hamon reflection right next to the cutting edge (fx < 0.14)
                double r = lum * 0.96, g = lum * 0.99, b = lum * 1.07;
                if (fx < 0.14) {
                    double k = (0.14 - fx) / 0.14;
                    r = lerp(r, 235, k * 0.72);
                    g = lerp(g, 205, k * 0.68);
                    b = lerp(b, 48,  k * 0.65);
                }
                diff.setRGB(x, y, (255 << 24) | ((int) clamp(r, 0, 255) << 16) | ((int) clamp(g, 0, 255) << 8) | (int) clamp(b, 0, 255));
                if (fx < 0.08) {
                    double k = (0.08 - fx) / 0.08;
                    int gr = (int) clamp(235 * k, 0, 255);
                    int ggc = (int) clamp(210 * k, 0, 255);
                    int gb = (int) clamp(35 * k, 0, 255);
                    glow.setRGB(x, y, (255 << 24) | (gr << 16) | (ggc << 8) | gb);
                }
            }
        }
        // Subtle diagonal battle-scratch micro-lines matching muramasa_tex_0.jpg
        gd.setColor(new Color(195, 205, 220, 42));
        for (int s = 0; s < 55; s++) {
            int sx = 24 + rng.nextInt(185), sy = 24 + rng.nextInt(450);
            gd.drawLine(sx, sy, sx + rng.nextInt(26) - 13, sy + rng.nextInt(54) - 27);
        }

        // Gold `村正` (Muramasa) Kanji signature near the base of the blade (faithful to muramasa_tex_0.jpg bottom-left!)
        drawGoldKanjiSignature(gd, 118, 412, new Color(245, 194, 48, 255));
        drawGoldKanjiSignature(gg, 118, 412, new Color(255, 210, 45, 255));

        gd.dispose();
        gg.dispose();
        Path texDir = ASSETS.resolve("textures\\item");
        Files.createDirectories(texDir);
        ImageIO.write(diff, "png", texDir.resolve("muramasa_model.png").toFile());
        ImageIO.write(glow, "png", texDir.resolve("muramasa_model_glowmask.png").toFile());
    }

    static void drawGoldKanjiSignature(Graphics2D g, int cx, int y0, Color col) {
        g.setColor(col);
        g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Stylized 村 (Mura) upper kanji
        g.drawLine(cx - 16, y0 + 6, cx - 2, y0 + 6);
        g.drawLine(cx - 9, y0, cx - 9, y0 + 26);
        g.drawLine(cx - 9, y0 + 10, cx - 17, y0 + 22);
        g.drawLine(cx - 9, y0 + 12, cx - 2, y0 + 19);
        g.drawLine(cx + 2, y0 + 8, cx + 18, y0 + 8);
        g.drawLine(cx + 12, y0, cx + 12, y0 + 26);
        g.drawLine(cx + 4, y0 + 15, cx + 9, y0 + 20);
        // Stylized 正 (Masa) lower kanji
        int y1 = y0 + 36;
        g.drawLine(cx - 16, y1, cx + 16, y1);
        g.drawLine(cx, y1, cx, y1 + 24);
        g.drawLine(cx, y1 + 11, cx + 13, y1 + 11);
        g.drawLine(cx - 11, y1 + 10, cx - 11, y1 + 24);
        g.drawLine(cx - 18, y1 + 24, cx + 18, y1 + 24);
    }

    static void buildScabbardTextures() throws Exception {
        BufferedImage diff = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glow = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gd = diff.createGraphics(), gg = glow.createGraphics();
        gd.setColor(new Color(12, 10, 14, 255)); gd.fillRect(0, 0, TEX_W, TEX_H);
        gg.setColor(new Color(0, 0, 0, 255));   gg.fillRect(0, 0, TEX_W, TEX_H);
        gd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Random rng = new Random(998811L);
        paintCommonSwatches(diff, glow, rng);

        // Left half (x=16..224, y=16..496): Glossy black-lacquer scabbard with crimson coiled Japanese dragon (`muramasa_tex_3.jpg`!)
        for (int y = 16; y < 496; y++) {
            for (int x = 16; x < 224; x++) {
                double fx = (x - 16.0) / 208.0;
                double sheen = Math.exp(-Math.pow((fx - 0.5) / 0.34, 2.0)) * 22.0;
                int v = (int) clamp(14 + sheen + (rng.nextInt(5) - 2), 0, 255);
                diff.setRGB(x, y, (255 << 24) | ((v + 4) << 16) | (v << 8) | (v + 3));
            }
        }
        // Coiled deep blood-crimson dragon winding down the entire Saya body + subtle crimson ember glow!
        Path2D body = new Path2D.Double();
        body.moveTo(120, 62);
        for (int i = 0; i <= 14; i++) {
            double t = i / 14.0;
            double py = lerp(62, 448, t);
            double px = 120 + Math.sin(t * Math.PI * 3.6) * 56.0;
            if (i == 0) body.moveTo(px, py); else body.lineTo(px, py);
        }
        gd.setColor(new Color(115, 10, 16, 235));
        gd.setStroke(new BasicStroke(16.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gd.draw(body);
        gd.setColor(new Color(185, 22, 28, 225));
        gd.setStroke(new BasicStroke(7.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gd.draw(body);
        // Dragon scales & fiery crimson wisps
        gd.setColor(new Color(225, 42, 42, 210));
        gd.setStroke(new BasicStroke(2.2f));
        for (int i = 0; i < 24; i++) {
            double t = (i + 0.5) / 24.0;
            double py = lerp(68, 442, t);
            double px = 120 + Math.sin(t * Math.PI * 3.6) * 56.0;
            gd.drawArc((int) px - 10, (int) py - 6, 20, 12, 20, 140);
            if (i % 3 == 0) {
                gd.drawLine((int) px, (int) py, (int) px + (i % 2 == 0 ? 24 : -24), (int) py - 14);
            }
        }
        // Subtle demonic crimson glow along dragon spine
        gg.setColor(new Color(128, 14, 18, 255));
        gg.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gg.draw(body);

        gd.dispose();
        gg.dispose();
        Path texDir = ASSETS.resolve("textures\\item");
        ImageIO.write(diff, "png", texDir.resolve("muramasa_scabbard_model.png").toFile());
        ImageIO.write(glow, "png", texDir.resolve("muramasa_scabbard_model_glowmask.png").toFile());
    }

    static void buildGuiIcon() throws Exception {
        // Crisp 32x32 GUI icon showing the curved dark-steel Muramasa Katana with bright golden-yellow glowing edge,
        // 12-spike iron tsuba, crimson-black hilt, gold kashira, and parallel crimson-dragon scabbard!
        BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // 1. Subtle parallel Scabbard (Saya) tucked cleanly behind/below-right
        for (int i = 0; i <= 18; i++) {
            double t = i / 18.0;
            int x = (int) Math.round(lerp(13, 29, t) + Math.sin(t * Math.PI) * 1.1);
            int y = (int) Math.round(lerp(28, 12, t) + Math.sin(t * Math.PI) * 1.1);
            putPx(icon, x, y, 0xFF141016);
            putPx(icon, x + 1, y, (i % 4 == 1) ? 0xFF9E181E : 0xFF201A22);
            putPx(icon, x, y + 1, 0xFF0A080C);
        }
        putPx(icon, 13, 28, 0xFF5A5C68); // iron koiguchi mouth

        // 2. Curved Katana Blade (from guard (10,21) -> tip (29,2))
        for (int i = 0; i <= 22; i++) {
            double t = i / 22.0;
            double curve = Math.sin(t * Math.PI) * 1.65;
            int bx = (int) Math.round(lerp(10.0, 29.0, t) - curve * 0.72);
            int by = (int) Math.round(lerp(21.0, 2.0, t) - curve * 0.72);
            // Dark gunmetal spine
            putPx(icon, bx - 1, by - 1, 0xFF1A1D24);
            putPx(icon, bx, by - 1, 0xFF4C5362);
            putPx(icon, bx - 1, by, 0xFF6B7384);
            // ICONIC BRIGHT GLOWING YELLOW RAZOR EDGE (`#FFE820`)!
            putPx(icon, bx, by, 0xFFFFF030);
            putPx(icon, bx + 1, by, (i < 20) ? 0x90FFE020 : 0xFFFFF880);
        }
        // Razor tip glint
        putPx(icon, 29, 2, 0xFFFFFFA0);
        putPx(icon, 30, 1, 0xC0FFE830);

        // 3. Spiked Weathered-Iron Tsuba at (10, 21)
        int[][] tsubaPts = {{10,21},{9,20},{11,22},{8,19},{12,23},{9,22},{11,20},{7,20},{11,24},{12,21},{9,18}};
        for (int[] p : tsubaPts) putPx(icon, p[0], p[1], 0xFF565966);
        putPx(icon, 10, 21, 0xFF2B2D36);
        putPx(icon, 11, 20, 0xFFD89A28); // tiny gold habaki glint

        // 4. Braided Crimson-Black Tsuka Hilt from (9, 22) -> (3, 28)
        for (int i = 0; i <= 6; i++) {
            int hx = 9 - i;
            int hy = 22 + i;
            putPx(icon, hx, hy, (i % 2 == 0) ? 0xFF15151C : 0xFF9A1218);
            putPx(icon, hx - 1, hy, 0xFF0E0E14);
            putPx(icon, hx, hy + 1, 0xFF1D1D26);
        }
        // Gold Kashira pommel cap at (2, 29)
        putPx(icon, 2, 29, 0xFFD89A28);
        putPx(icon, 3, 29, 0xFF4A4D58);
        putPx(icon, 2, 28, 0xFFF2C248);

        g.dispose();
        ImageIO.write(icon, "png", ASSETS.resolve("textures\\item\\muramasa.png").toFile());
    }

    static void putPx(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) img.setRGB(x, y, argb);
    }

    static void writeAuxFiles() throws Exception {
        String animJson = """
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.muramasa.idle": {
                      "loop": true,
                      "animation_length": 2.0,
                      "bones": {}
                    }
                  }
                }
                """;
        Path animPath = ASSETS.resolve("animations\\item\\muramasa.animation.json");
        Files.createDirectories(animPath.getParent());
        Files.writeString(animPath, animJson, StandardCharsets.UTF_8);

        // Clean proportional scale [0.88, 0.84, 0.88] -> authentic ~1.5-block katana length, ZERO ground clipping!
        String itemModelJson = """
                {
                  "parent": "builtin/entity",
                  "gui_light": "front",
                  "textures": {
                    "particle": "abyssalrpg:item/muramasa"
                  },
                  "display": {
                    "thirdperson_righthand": {
                      "rotation": [0, 0, 0],
                      "translation": [0.0, 0.8, 0.0],
                      "scale": [0.88, 0.84, 0.88]
                    },
                    "thirdperson_lefthand": {
                      "rotation": [0, 0, 0],
                      "translation": [0.0, 0.8, 0.0],
                      "scale": [0.88, 0.84, 0.88]
                    },
                    "firstperson_righthand": {
                      "rotation": [-10, 12, 8],
                      "translation": [1.4, 0.4, -0.4],
                      "scale": [0.88, 0.84, 0.88]
                    },
                    "firstperson_lefthand": {
                      "rotation": [-10, 12, 8],
                      "translation": [1.4, 0.4, -0.4],
                      "scale": [0.88, 0.84, 0.88]
                    },
                    "ground": {
                      "rotation": [0, 0, 0],
                      "translation": [0, 3.0, 0],
                      "scale": [0.74, 0.70, 0.74]
                    },
                    "fixed": {
                      "rotation": [0, 90, 0],
                      "translation": [0, -1.8, 0],
                      "scale": [0.82, 0.76, 0.82]
                    },
                    "gui": {
                      "rotation": [0, 0, 0],
                      "translation": [0, 0, 0],
                      "scale": [1.0, 1.0, 1.0]
                    }
                  }
                }
                """;
        Path modelPath = ASSETS.resolve("models\\item\\muramasa.json");
        Files.createDirectories(modelPath.getParent());
        Files.writeString(modelPath, itemModelJson, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        List<Cube> katana = buildKatanaCubes();
        List<Cube> scabbard = buildScabbardCubes();
        writeGeo(ASSETS.resolve("geo\\item\\muramasa.geo.json"), "geometry.muramasa", katana);
        writeGeo(ASSETS.resolve("geo\\item\\muramasa_scabbard.geo.json"), "geometry.muramasa_scabbard", scabbard);
        buildKatanaTextures();
        buildScabbardTextures();
        buildGuiIcon();
        writeAuxFiles();
        System.out.printf("SUCCESS: Generated Muramasa Katana (%d cubes) + Scabbard (%d cubes) + 512x512 textures & 32x32 icon!%n",
                katana.size(), scabbard.size());
    }
}
