package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.combat.SeaWeaponsMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Hệ thống VFX 3D chuyên biệt cho **Hải Thần Đinh Ba & Đèn Lồng Biển**:
 * - Tách tuyệt đối từng pass `BODY` -> `GLOW` -> `SPARK` (1 `VertexConsumer` mỗi pass, 100% an toàn Minecraft 1.21.1).
 * - **1. Water Slash Arcs:** Vệt đâm/quét sóng lam-trắng bám sát động tác Đinh Ba 3 nhịp.
 * - **2. 3D Rolling Tidal Wave Wall (Sóng Triều — Đèn Lồng tay trái):**
 *   Tường sóng cong uốn cuộn 3D rộng 6.5 block × cao 3.5 block với đỉnh bọt biển trắng phát sáng lao đi 18 block!
 * - **3. 3D Abyssal Whirlpool Vortex (Xoáy Nước — Đinh Ba tay phải):**
 *   Phễu xoáy nước 3D khổng lồ (bán kính 6.0 block, cao 6.2 block) gồm tường phễu xoay ngược + 5 dải bọt xoắn ốc phát sáng
 *   + vòng ma trận nước dưới đáy tồn tại 4.0s!
 * - **4. Maelstrom Geyser Burst:** Cột nước phun cao & sóng xung kích xanh ngọc khi Xoáy Nước phát nổ hoặc cộng hưởng Sóng Triều!
 */
public final class SeaWeaponsVfxRenderer {
    private static final class SeaRenderTypes extends RenderType {
        private static final RenderType BODY = RenderType.create(
                "abyssalrpg_sea_body",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                2048,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(DualBladeTrailRenderer.SLASH_ARC_TEX, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private static final RenderType GLOW = RenderType.create(
                "abyssalrpg_sea_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                2048,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(DualBladeTrailRenderer.SLASH_ARC_TEX, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private static final RenderType SPARK = RenderType.create(
                "abyssalrpg_sea_spark",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                768,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(DualBladeTrailRenderer.SLASH_ARC_TEX, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private SeaRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int buf,
                               boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, fmt, mode, buf, crumble, sort, setup, clear);
        }
    }

    private static final class WaterSlash {
        final int attackerId;
        final double x, y, z;
        final float yawDeg, rollDeg, baseR, tipR, startDeg, endDeg, delayFrac;
        final long startTick;
        final float durationTicks;

        WaterSlash(int attackerId, Vec3 anchor, float yawDeg, float rollDeg,
                   float baseR, float tipR, float startDeg, float endDeg, float delayFrac,
                   long startTick, float durationTicks) {
            this.attackerId = attackerId;
            this.x = anchor.x; this.y = anchor.y; this.z = anchor.z;
            this.yawDeg = yawDeg; this.rollDeg = rollDeg;
            this.baseR = baseR; this.tipR = tipR;
            this.startDeg = startDeg; this.endDeg = endDeg;
            this.delayFrac = delayFrac;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class TidalWaveVfx {
        final double ox, oy, oz;
        final float yawDeg, rangeBlocks, widthBlocks;
        final long startTick;
        final float durationTicks;

        TidalWaveVfx(Vec3 origin, float yawDeg, float rangeBlocks, float widthBlocks, long startTick, float durationTicks) {
            this.ox = origin.x; this.oy = origin.y; this.oz = origin.z;
            this.yawDeg = yawDeg; this.rangeBlocks = rangeBlocks; this.widthBlocks = widthBlocks;
            this.startTick = startTick; this.durationTicks = durationTicks;
        }
    }

    private static final class WhirlpoolVfx {
        final double cx, cy, cz;
        final float radius, height;
        final long startTick;
        final float durationTicks;
        boolean supercharged = false;

        WhirlpoolVfx(Vec3 center, float radius, float height, long startTick, float durationTicks) {
            this.cx = center.x; this.cy = center.y; this.cz = center.z;
            this.radius = radius; this.height = height;
            this.startTick = startTick; this.durationTicks = durationTicks;
        }
    }

    private static final class MaelstromBurstVfx {
        final double cx, cy, cz;
        final float maxRadius;
        final boolean supercharged;
        final long startTick;
        final float durationTicks;

        MaelstromBurstVfx(Vec3 center, float maxRadius, boolean supercharged, long startTick, float durationTicks) {
            this.cx = center.x; this.cy = center.y; this.cz = center.z;
            this.maxRadius = maxRadius; this.supercharged = supercharged;
            this.startTick = startTick; this.durationTicks = durationTicks;
        }
    }

    /**
     * 5 Hoạt Ảnh Đinh Ba 3D Phóng Liên Hoàn Vào Mục Tiêu (sau khi người chơi giơ Đinh Ba thẳng lên trời!):
     * - Đối xứng ngoài -> trong -> mũi Trung Tâm Vương Miện kết liễu (t = 8, 10, 13, 15, 18 tick).
     * - Mỗi Đinh Ba có đầy đủ thân, vương miện ngọc sapphire hình thoi, 3 mũi nhọn, vây sóng & vệt sao chổi!
     */
    private static final class TridentVolleyVfx {
        static final int COUNT = 5;
        static final float[] HALO_RIGHT  = {-2.15F, +2.15F, -1.15F, +1.15F, 0.00F};
        static final float[] HALO_UP     = { 2.35F,  2.35F,  2.98F,  2.98F, 3.48F};
        static final float[] HALO_BACK   = {-0.30F, -0.30F, -0.55F, -0.55F, -0.75F};
        static final float[] HALO_SCALE  = { 0.96F,  0.96F,  1.04F,  1.04F, 1.30F};
        static final float[] SPAWN_DELAY = { 0.0F,   0.8F,   1.6F,   2.4F,  3.2F};
        static final float[] LAUNCH_TICK = { 8.0F,  10.0F,  13.0F,  15.0F, 18.0F};

        final int casterId;
        final int targetEntityId;
        double tx, ty, tz;
        final float castYawDeg;
        final float flightTicks;
        final long startTick;
        final float totalDurationTicks;
        final double[] launchX = new double[COUNT];
        final double[] launchY = new double[COUNT];
        final double[] launchZ = new double[COUNT];
        final boolean[] launchLocked = new boolean[COUNT];

        TridentVolleyVfx(int casterId, Vec3 targetPos, int targetEntityId, float castYawDeg, float flightTicks, long startTick) {
            this.casterId = casterId;
            this.targetEntityId = targetEntityId;
            this.tx = targetPos.x; this.ty = targetPos.y; this.tz = targetPos.z;
            this.castYawDeg = castYawDeg;
            this.flightTicks = Mth.clamp(flightTicks, 5.5F, 12.0F);
            this.startTick = startTick;
            this.totalDurationTicks = 18.0F + this.flightTicks + 7.0F;
        }
    }

    private static final class Sparkle {
        final double sx, sy, sz;
        final float vx, vy, vz, size;
        final int r, g, b;
        final long startTick;
        final float lifeTicks;

        Sparkle(double x, double y, double z, float vx, float vy, float vz, float size,
                int r, int g, int b, long startTick, float lifeTicks) {
            this.sx = x; this.sy = y; this.sz = z;
            this.vx = vx; this.vy = vy; this.vz = vz; this.size = size;
            this.r = r; this.g = g; this.b = b;
            this.startTick = startTick; this.lifeTicks = lifeTicks;
        }
    }

    private static final List<WaterSlash> SLASHES = new ArrayList<>();
    private static final List<TidalWaveVfx> WAVES = new ArrayList<>();
    private static final List<WhirlpoolVfx> WHIRLPOOLS = new ArrayList<>();
    private static final List<MaelstromBurstVfx> BURSTS = new ArrayList<>();
    private static final List<TridentVolleyVfx> VOLLEYS = new ArrayList<>();
    private static final List<Sparkle> SPARKLES = new ArrayList<>();

    private SeaWeaponsVfxRenderer() {}

    public static void clear() {
        SLASHES.clear();
        WAVES.clear();
        WHIRLPOOLS.clear();
        BURSTS.clear();
        VOLLEYS.clear();
        SPARKLES.clear();
    }

    public static void spawnSlash(int attackerId, Vec3 anchor, float yawDeg, SeaWeaponsMoveType move, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        if (SLASHES.size() >= 24) SLASHES.remove(0);
        switch (move) {
            case THRUST_1 -> SLASHES.add(new WaterSlash(attackerId, anchor, yawDeg, -14.0F, 0.55F, 2.35F, -32.0F, 36.0F, 0.14F, tick, 9.0F));
            case SWEEP_2 -> SLASHES.add(new WaterSlash(attackerId, anchor, yawDeg, 8.0F, 0.60F, 2.45F, -92.0F, 92.0F, 0.10F, tick, 10.5F));
            default -> SLASHES.add(new WaterSlash(attackerId, anchor, yawDeg, 88.0F, 0.55F, 2.55F, -82.0F, 78.0F, 0.22F, tick, 12.5F));
        }
    }

    public static void spawnTidalWave(Vec3 origin, float yawDeg, float rangeBlocks, float widthBlocks, long tick, float durationTicks) {
        if (WAVES.size() >= 8) WAVES.remove(0);
        WAVES.add(new TidalWaveVfx(origin, yawDeg, rangeBlocks, widthBlocks, tick, durationTicks));
    }

    public static void spawnWhirlpool(Vec3 center, float radius, float height, long tick, float durationTicks) {
        if (WHIRLPOOLS.size() >= 6) WHIRLPOOLS.remove(0);
        WHIRLPOOLS.add(new WhirlpoolVfx(center, radius, height, tick, durationTicks));
    }

    public static void spawnMaelstromBurst(Vec3 center, float maxRadius, boolean supercharged, long tick, float durationTicks) {
        if (supercharged) {
            for (WhirlpoolVfx wp : WHIRLPOOLS) {
                double dx = wp.cx - center.x, dz = wp.cz - center.z;
                if (dx * dx + dz * dz <= (wp.radius + 3.0) * (wp.radius + 3.0)) {
                    wp.supercharged = true;
                }
            }
        }
        if (BURSTS.size() >= 10) BURSTS.remove(0);
        BURSTS.add(new MaelstromBurstVfx(center, maxRadius, supercharged, tick, durationTicks));
    }

    public static void spawnTridentVolley(int casterId, Vec3 targetPos, int targetEntityId,
                                          float castYawDeg, float flightTicks, long tick) {
        if (VOLLEYS.size() >= 8) VOLLEYS.remove(0);
        VOLLEYS.add(new TridentVolleyVfx(casterId, targetPos, targetEntityId, castYawDeg, flightTicks, tick));
    }

    public static void spawnSparkle(double x, double y, double z, float vx, float vy, float vz,
                                    float size, int r, int g, int b, long tick, float life) {
        if (SPARKLES.size() >= 96) SPARKLES.remove(0);
        SPARKLES.add(new Sparkle(x, y, z, vx, vy, vz, size, r, g, b, tick, life));
    }

    public static void renderWorld(WorldRenderContext context, long clientTick) {
        if (SLASHES.isEmpty() && WAVES.isEmpty() && WHIRLPOOLS.isEmpty()
                && BURSTS.isEmpty() && VOLLEYS.isEmpty() && SPARKLES.isEmpty()) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        PoseStack poseStack = context.matrixStack();
        if (consumers == null || poseStack == null) return;

        float pt = context.tickCounter().getGameTimeDeltaPartialTick(true);
        double now = clientTick + pt;
        Camera cam = context.camera();
        Vec3 camPos = cam.getPosition();

        SLASHES.removeIf(s -> (now - s.startTick) >= s.durationTicks);
        WAVES.removeIf(w -> (now - w.startTick) >= w.durationTicks);
        WHIRLPOOLS.removeIf(wp -> (now - wp.startTick) >= wp.durationTicks);
        BURSTS.removeIf(b -> (now - b.startTick) >= b.durationTicks);
        VOLLEYS.removeIf(v -> (now - v.startTick) >= v.totalDurationTicks);
        SPARKLES.removeIf(sp -> (now - sp.startTick) >= sp.lifeTicks);
        if (SLASHES.isEmpty() && WAVES.isEmpty() && WHIRLPOOLS.isEmpty()
                && BURSTS.isEmpty() && VOLLEYS.isEmpty() && SPARKLES.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = poseStack.last().pose();

        // PASS 1: BODY (Translucent deep-ocean tsunami walls, 5-Trident solid cores, whirlpools & slash bodies)
        VertexConsumer bodyVc = consumers.getBuffer(SeaRenderTypes.BODY);
        for (WaterSlash s : SLASHES) drawWaterSlash(bodyVc, mat, s, now, pt, false);
        for (TidalWaveVfx w : WAVES) drawTidalWave(bodyVc, mat, w, now, false);
        for (WhirlpoolVfx wp : WHIRLPOOLS) drawWhirlpool(bodyVc, mat, wp, now, false);
        for (MaelstromBurstVfx b : BURSTS) drawBurst(bodyVc, mat, b, now, false);
        for (TridentVolleyVfx v : VOLLEYS) drawTridentVolley(bodyVc, mat, v, now, pt, false);

        // PASS 2: GLOW (Additive electric-cyan & glowing white-ice tsunami foam crests, 5-Trident spectral edges & trails!)
        VertexConsumer glowVc = consumers.getBuffer(SeaRenderTypes.GLOW);
        for (WaterSlash s : SLASHES) drawWaterSlash(glowVc, mat, s, now, pt, true);
        for (TidalWaveVfx w : WAVES) drawTidalWave(glowVc, mat, w, now, true);
        for (WhirlpoolVfx wp : WHIRLPOOLS) drawWhirlpool(glowVc, mat, wp, now, true);
        for (MaelstromBurstVfx b : BURSTS) drawBurst(glowVc, mat, b, now, true);
        for (TridentVolleyVfx v : VOLLEYS) drawTridentVolley(glowVc, mat, v, now, pt, true);

        // PASS 3: SPARK (4-point bioluminescent ocean sparkles!)
        if (!SPARKLES.isEmpty()) {
            VertexConsumer sparkVc = consumers.getBuffer(SeaRenderTypes.SPARK);
            Quaternionf camRot = cam.rotation();
            for (Sparkle sp : SPARKLES) {
                float age = (float) (now - sp.startTick);
                float u = Mth.clamp(age / sp.lifeTicks, 0.0F, 1.0F);
                float alpha = Mth.sin(u * Mth.PI);
                if (alpha <= 0.02F) continue;
                double px = sp.sx + sp.vx * age;
                double py = sp.sy + sp.vy * age;
                double pz = sp.sz + sp.vz * age;
                drawSparkQuad(sparkVc, mat, camRot, px, py, pz, sp.size * (0.65F + 0.50F * alpha),
                        sp.r, sp.g, sp.b, (int) (alpha * 235));
            }
        }

        poseStack.popPose();
    }

    private static void drawWaterSlash(VertexConsumer vc, Matrix4f mat, WaterSlash s, double now, float pt, boolean glow) {
        float age = (float) (now - s.startTick);
        float t = Mth.clamp(age / s.durationTicks, 0.0F, 1.0F);
        float p = Mth.clamp((t - s.delayFrac) / Math.max(0.15F, 0.62F - s.delayFrac), 0.0F, 1.0F);
        if (p <= 0.005F) return;

        double ax = s.x, ay = s.y, az = s.z;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity e = mc.level.getEntity(s.attackerId);
            if (e != null && e.isAlive()) {
                ax = Mth.lerp(pt, e.xo, e.getX());
                ay = Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.60;
                az = Mth.lerp(pt, e.zo, e.getZ());
            }
        }

        float head = 1.0F - (float) Math.pow(1.0 - p, 4.0);
        float tail = Mth.clamp((t - 0.34F) / 0.66F, 0.0F, 1.0F);
        tail = tail * tail;
        if (head - tail < 0.02F) return;
        float fade = (t < 0.72F) ? 1.0F : (1.0F - (t - 0.72F) / 0.28F);

        float yawRad = s.yawDeg * Mth.DEG_TO_RAD;
        double fwdX = -Mth.sin(yawRad), fwdZ = Mth.cos(yawRad);
        double rightX = -Mth.cos(yawRad), rightZ = -Mth.sin(yawRad);
        float rollRad = s.rollDeg * Mth.DEG_TO_RAD;
        double cr = Math.cos(rollRad), sr = Math.sin(rollRad);

        int segs = 14;
        for (int i = 0; i < segs; i++) {
            float f0 = i / (float) segs;
            float f1 = (i + 1) / (float) segs;
            float u0 = Mth.lerp(f0, tail, head);
            float u1 = Mth.lerp(f1, tail, head);
            float a0 = Mth.lerp(u0, s.startDeg, s.endDeg) * Mth.DEG_TO_RAD;
            float a1 = Mth.lerp(u1, s.startDeg, s.endDeg) * Mth.DEG_TO_RAD;

            float w0 = Mth.sin(f0 * Mth.PI);
            float w1 = Mth.sin(f1 * Mth.PI);
            float rIn0 = Mth.lerp(w0, s.tipR, glow ? Mth.lerp(0.52F, s.baseR, s.tipR) : s.baseR);
            float rIn1 = Mth.lerp(w1, s.tipR, glow ? Mth.lerp(0.52F, s.baseR, s.tipR) : s.baseR);

            double[] i0 = slashPt(ax, ay, az, fwdX, fwdZ, rightX, rightZ, cr, sr, a0, rIn0);
            double[] o0 = slashPt(ax, ay, az, fwdX, fwdZ, rightX, rightZ, cr, sr, a0, s.tipR);
            double[] o1 = slashPt(ax, ay, az, fwdX, fwdZ, rightX, rightZ, cr, sr, a1, s.tipR);
            double[] i1 = slashPt(ax, ay, az, fwdX, fwdZ, rightX, rightZ, cr, sr, a1, rIn1);

            int alpha = (int) (fade * (glow ? 225 : 175) * (0.35F + 0.65F * Math.max(w0, w1)));
            int r = glow ? 186 : 34;
            int g = glow ? 248 : 138;
            int b = 255;
            emitQuad(vc, mat, i0, o0, o1, i1, f0, f1, r, g, b, alpha);
        }
    }

    private static double[] slashPt(double ax, double ay, double az, double fx, double fz,
                                    double rx, double rz, double cr, double sr, float ang, float rad) {
        double lx = Mth.sin(ang) * rad;
        double lz = Mth.cos(ang) * rad;
        double wx = rx * (lx * cr) + fx * lz;
        double wy = lx * sr;
        double wz = rz * (lx * cr) + fz * lz;
        return new double[]{ax + wx, ay + wy, az + wz};
    }

    /**
     * Sóng Thần 3D Khổng Lồ (Lantern Tsunami RMB — rộng 9.6 × cao 5.85 × tầm 22 block):
     * - Tường nước đại dương cuộn ống khổng lồ đa tầng có cả mặt trước uốn vòm lẫn sườn nước phía sau dày khối!
     * - Đỉnh bọt biển trắng-lam cuồn cuộn phát sáng + thảm bọt nước tràn sát mặt đất phía trước chân sóng!
     */
    private static void drawTidalWave(VertexConsumer vc, Matrix4f mat, TidalWaveVfx w, double now, boolean glow) {
        float age = (float) (now - w.startTick);
        float t = Mth.clamp(age / w.durationTicks, 0.0F, 1.0F);
        float rise = Mth.clamp(t / 0.14F, 0.0F, 1.0F);
        float fade = (t > 0.80F) ? (1.0F - (t - 0.80F) / 0.20F) : 1.0F;
        float dist = t * w.rangeBlocks;

        float yawRad = w.yawDeg * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad);
        double rx = -Mth.cos(yawRad), rz = -Mth.sin(yawRad);

        double cx = w.ox + fx * dist;
        double cy = w.oy;
        double cz = w.oz + fz * dist;

        float halfW = w.widthBlocks * 0.5F * (0.68F + 0.32F * rise);
        float waveH = 5.85F * (0.30F + 0.70F * rise) * (0.76F + 0.24F * fade);

        int latSegs = 16;
        int profSegs = glow ? 5 : 9;
        for (int ix = 0; ix < latSegs; ix++) {
            float u0 = ix / (float) latSegs;
            float u1 = (ix + 1) / (float) latSegs;
            float s0 = (u0 * 2.0F - 1.0F);
            float s1 = (u1 * 2.0F - 1.0F);
            float arch0 = 1.0F - 0.38F * s0 * s0;
            float arch1 = 1.0F - 0.38F * s1 * s1;
            float bow0 = (1.0F - s0 * s0) * 1.65F;
            float bow1 = (1.0F - s1 * s1) * 1.65F;

            // 1. Mặt sóng thần uốn ống khổng lồ phía trước
            for (int iy = 0; iy < profSegs; iy++) {
                float vStart = glow ? 0.56F : 0.0F;
                float v0 = Mth.lerp(iy / (float) profSegs, vStart, 1.0F);
                float v1 = Mth.lerp((iy + 1) / (float) profSegs, vStart, 1.0F);

                double[] p00 = wavePoint(cx, cy, cz, fx, fz, rx, rz, s0 * halfW, bow0, waveH * arch0, v0, age);
                double[] p10 = wavePoint(cx, cy, cz, fx, fz, rx, rz, s1 * halfW, bow1, waveH * arch1, v0, age);
                double[] p11 = wavePoint(cx, cy, cz, fx, fz, rx, rz, s1 * halfW, bow1, waveH * arch1, v1, age);
                double[] p01 = wavePoint(cx, cy, cz, fx, fz, rx, rz, s0 * halfW, bow0, waveH * arch0, v1, age);

                float edgeFade = Math.min(arch0, arch1);
                if (glow) {
                    int a = (int) (fade * edgeFade * (145 + 105 * v1));
                    int r = (int) Mth.lerp(v1, 96.0F, 246.0F);
                    int g = (int) Mth.lerp(v1, 224.0F, 255.0F);
                    emitQuad(vc, mat, p00, p10, p11, p01, u0, u1, r, g, 255, a);
                } else {
                    int a = (int) (fade * edgeFade * (195 + 45 * v1));
                    int r = (int) Mth.lerp(v1, 8.0F, 62.0F);
                    int g = (int) Mth.lerp(v1, 56.0F, 198.0F);
                    int b = (int) Mth.lerp(v1, 152.0F, 255.0F);
                    emitQuad(vc, mat, p00, p10, p11, p01, u0, u1, r, g, b, a);
                }
            }

            float edgeFade = Math.min(arch0, arch1);
            if (!glow) {
                // 2. Sườn nước phía sau dày khối (để nhìn từ F5 hay bên hông thấy trọn ngọn núi nước Sóng Thần!)
                int backSegs = 4;
                for (int ib = 0; ib < backSegs; ib++) {
                    float b0 = ib / (float) backSegs;
                    float b1 = (ib + 1) / (float) backSegs;
                    double[] q00 = waveBackPoint(cx, cy, cz, fx, fz, rx, rz, s0 * halfW, bow0, waveH * arch0, b0);
                    double[] q10 = waveBackPoint(cx, cy, cz, fx, fz, rx, rz, s1 * halfW, bow1, waveH * arch1, b0);
                    double[] q11 = waveBackPoint(cx, cy, cz, fx, fz, rx, rz, s1 * halfW, bow1, waveH * arch1, b1);
                    double[] q01 = waveBackPoint(cx, cy, cz, fx, fz, rx, rz, s0 * halfW, bow0, waveH * arch0, b1);
                    int a = (int) (fade * edgeFade * (170 - 45 * b1));
                    int r = (int) Mth.lerp(b1, 24.0F, 10.0F);
                    int g = (int) Mth.lerp(b1, 128.0F, 62.0F);
                    int bl = (int) Mth.lerp(b1, 224.0F, 150.0F);
                    emitQuad(vc, mat, q00, q10, q11, q01, u0, u1, r, g, bl, a);
                }
            } else {
                // 3. Thảm bọt nước trắng phát sáng tràn xiết sát mặt đất phía trước chân Sóng Thần!
                double surfFront = 2.65;
                double surfBack = -1.25;
                double[] f0 = {cx + rx * (s0 * halfW) + fx * (bow0 + surfBack), cy + 0.16, cz + rz * (s0 * halfW) + fz * (bow0 + surfBack)};
                double[] f1 = {cx + rx * (s1 * halfW) + fx * (bow1 + surfBack), cy + 0.16, cz + rz * (s1 * halfW) + fz * (bow1 + surfBack)};
                double[] f2 = {cx + rx * (s1 * halfW * 1.06F) + fx * (bow1 + surfFront), cy + 0.08, cz + rz * (s1 * halfW * 1.06F) + fz * (bow1 + surfFront)};
                double[] f3 = {cx + rx * (s0 * halfW * 1.06F) + fx * (bow0 + surfFront), cy + 0.08, cz + rz * (s0 * halfW * 1.06F) + fz * (bow0 + surfFront)};
                int surfA = (int) (fade * edgeFade * 175);
                emitQuad(vc, mat, f0, f1, f2, f3, u0, u1, 178, 246, 255, surfA);
            }
        }
    }

    private static double[] wavePoint(double cx, double cy, double cz, double fx, double fz,
                                      double rx, double rz, float lat, float bow, float h, float v, float age) {
        // Mặt Sóng Thần dựng vách cong sâu rồi đổ ập về phía trước thành ống sóng cuộn bọt!
        float ripple = Mth.sin(lat * 1.15F + age * 0.72F) * 0.22F * v;
        float curlFwd;
        float y;
        if (v < 0.68F) {
            float k = v / 0.68F;
            curlFwd = -2.25F * (1.0F - k) * (1.0F - k) + 0.65F * k;
            y = h * Mth.sin(k * Mth.HALF_PI);
        } else {
            float k = (v - 0.68F) / 0.32F;
            curlFwd = 0.65F + 2.05F * Mth.sin(k * Mth.HALF_PI);
            y = h * (1.0F - 0.32F * k * k);
        }
        double along = bow + curlFwd + ripple;
        return new double[]{cx + rx * lat + fx * along, cy + y + 0.06, cz + rz * lat + fz * along};
    }

    private static double[] waveBackPoint(double cx, double cy, double cz, double fx, double fz,
                                          double rx, double rz, float lat, float bow, float h, float b) {
        // Sườn dốc phía sau từ đỉnh sóng (b=0) trượt dài về sau -4.8 block xuống đáy biển (b=1)
        double along = bow + Mth.lerp(b, 0.55F, -4.85F);
        double y = h * (1.0 - b * b * 0.96);
        return new double[]{cx + rx * lat + fx * along, cy + y + 0.05, cz + rz * lat + fz * along};
    }

    /**
     * Vẽ 5 hoạt ảnh Đinh Ba 3D hình thành vòng cung trên bầu trời rồi phóng liên hoàn khóa vào mục tiêu!
     */
    private static void drawTridentVolley(VertexConsumer vc, Matrix4f mat, TridentVolleyVfx v,
                                          double now, float pt, boolean glow) {
        float age = (float) (now - v.startTick);
        if (age < 0.0F || age >= v.totalDurationTicks) return;

        Minecraft mc = Minecraft.getInstance();
        // 1. Live-track mục tiêu di chuyển (nếu là mob/boss!)
        if (mc.level != null && v.targetEntityId >= 0) {
            Entity te = mc.level.getEntity(v.targetEntityId);
            if (te != null && te.isAlive()) {
                v.tx = Mth.lerp(pt, te.xo, te.getX());
                v.ty = Mth.lerp(pt, te.yo, te.getY()) + te.getBbHeight() * 0.55;
                v.tz = Mth.lerp(pt, te.zo, te.getZ());
            }
        }

        // 2. Lấy vị trí người chơi hiện tại để giữ vòng cung 5 Đinh Ba nổi mượt phía trên đỉnh đầu khi giơ Đinh Ba lên trời
        double cx = v.tx, cy = v.ty + 2.0, cz = v.tz;
        float casterYawRad = v.castYawDeg * Mth.DEG_TO_RAD;
        if (mc.level != null) {
            Entity ce = mc.level.getEntity(v.casterId);
            if (ce != null && ce.isAlive()) {
                cx = Mth.lerp(pt, ce.xo, ce.getX());
                cy = Mth.lerp(pt, ce.yo, ce.getY());
                cz = Mth.lerp(pt, ce.zo, ce.getZ());
            }
        }
        double fwdX = -Mth.sin(casterYawRad), fwdZ = Mth.cos(casterYawRad);
        double rightX = -Mth.cos(casterYawRad), rightZ = -Mth.sin(casterYawRad);

        // 3. Quầng ánh sáng thiên đỉnh quanh mũi Đinh Ba giơ lên trời (ticks 0..19)
        if (glow && age < 19.0F) {
            float haloEnv = Math.min(Mth.clamp(age / 4.0F, 0.0F, 1.0F), Mth.clamp((19.0F - age) / 5.0F, 0.0F, 1.0F));
            float rOut = 0.65F + 1.45F * Mth.sin(haloEnv * Mth.HALF_PI);
            float rIn = rOut * 0.72F;
            int segs = 22;
            for (int is = 0; is < segs; is++) {
                float a0 = age * 0.24F + (is / (float) segs) * Mth.TWO_PI;
                float a1 = age * 0.24F + ((is + 1) / (float) segs) * Mth.TWO_PI;
                double[] p0 = {cx + Math.cos(a0) * rIn, cy + 2.55, cz + Math.sin(a0) * rIn};
                double[] p1 = {cx + Math.cos(a0) * rOut, cy + 2.55, cz + Math.sin(a0) * rOut};
                double[] p2 = {cx + Math.cos(a1) * rOut, cy + 2.55, cz + Math.sin(a1) * rOut};
                double[] p3 = {cx + Math.cos(a1) * rIn, cy + 2.55, cz + Math.sin(a1) * rIn};
                emitQuad(vc, mat, p0, p1, p2, p3, 0.35F, 0.65F, 130, 240, 255, (int) (haloEnv * 190));
            }
        }

        // 4. Vẽ từng cây trong 5 Đinh Ba 3D (triệu hồi -> lơ lửng ngắm mục tiêu -> lao cong khóa đích -> nổ sóng!)
        for (int i = 0; i < TridentVolleyVfx.COUNT; i++) {
            float spawnT = TridentVolleyVfx.SPAWN_DELAY[i];
            float launchT = TridentVolleyVfx.LAUNCH_TICK[i];
            float impactT = launchT + v.flightTicks;
            if (age < spawnT || age > impactT + 5.5F) continue;

            float hr = TridentVolleyVfx.HALO_RIGHT[i];
            float hu = TridentVolleyVfx.HALO_UP[i];
            float hb = TridentVolleyVfx.HALO_BACK[i];
            float sc = TridentVolleyVfx.HALO_SCALE[i];
            boolean isCrownFinisher = (i == TridentVolleyVfx.COUNT - 1);

            if (!v.launchLocked[i]) {
                v.launchX[i] = cx + rightX * hr + fwdX * hb;
                v.launchY[i] = cy + hu + Mth.sin(age * 0.45F + i * 1.1F) * 0.10;
                v.launchZ[i] = cz + rightZ * hr + fwdZ * hb;
                if (age >= launchT) {
                    v.launchLocked[i] = true;
                }
            }
            double sx = v.launchX[i], sy = v.launchY[i], sz = v.launchZ[i];
            // Điểm cong giữa đường bay giúp 5 mũi đinh ba tạo quỹ đạo cánh cung cực điện ảnh rồi hội tụ vào mục tiêu!
            double midX = (sx + v.tx) * 0.5 + rightX * (hr * 0.42);
            double midY = Math.max(sy, v.ty) + (isCrownFinisher ? 1.55 : 0.75);
            double midZ = (sz + v.tz) * 0.5 + rightZ * (hr * 0.42);

            if (age < launchT) {
                // Pha 1: Hiện hình & ngắm khóa thẳng vào mục tiêu + vòng ma pháp nước xoay phía đuôi!
                float appear = Mth.clamp((age - spawnT) / 3.8F, 0.0F, 1.0F);
                float inv = 1.0F - appear;
                float pop = 1.0F - inv * inv * inv;
                double dx = v.tx - sx, dy = v.ty - sy, dz = v.tz - sz;
                double len = Math.max(1e-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
                double[] dir = {dx / len, dy / len, dz / len};
                float spin = age * 0.16F + i * 0.7F;
                drawSpectralTrident3D(vc, mat, sx, sy, sz, dir, sc * pop, spin, pop, isCrownFinisher, glow);
                if (glow) {
                    drawAxisRing(vc, mat, sx - dir[0] * 0.85 * sc, sy - dir[1] * 0.85 * sc, sz - dir[2] * 0.85 * sc,
                            dir, 0.42F * sc * pop, 0.58F * sc * pop, age * 0.35F, (int) (pop * 210));
                }
            } else if (age < impactT) {
                // Pha 2: Phóng tốc độ cao dọc đường cong Bezier + kéo vệt sao chổi nước phát sáng!
                float raw = Mth.clamp((age - launchT) / v.flightTicks, 0.0F, 1.0F);
                float u = raw * raw * (1.75F - 0.75F * raw);
                double[] pos = quadBezier(sx, sy, sz, midX, midY, midZ, v.tx, v.ty, v.tz, u);
                double[] tan = quadBezierTangent(sx, sy, sz, midX, midY, midZ, v.tx, v.ty, v.tz, u);
                drawSpectralTrident3D(vc, mat, pos[0], pos[1], pos[2], tan, sc * 1.06F, age * 0.55F, 1.0F, isCrownFinisher, glow);
                // Vệt sao chổi nước xoáy phía sau mũi đinh ba!
                drawVolleyCometTrail(vc, mat, sx, sy, sz, midX, midY, midZ, v.tx, v.ty, v.tz, u, sc, isCrownFinisher, glow);
            } else {
                // Pha 3: Vòng xung kích nổ nước khi mũi đinh ba xuyên trúng mục tiêu!
                float imp = Mth.clamp((age - impactT) / 5.5F, 0.0F, 1.0F);
                float fade = 1.0F - imp;
                if (glow && fade > 0.02F) {
                    double[] tan = quadBezierTangent(sx, sy, sz, midX, midY, midZ, v.tx, v.ty, v.tz, 1.0F);
                    float rMax = isCrownFinisher ? 2.85F : 1.55F;
                    float rOut = Mth.lerp(1.0F - fade * fade, 0.28F, rMax);
                    float rIn = Math.max(0.05F, rOut * 0.62F);
                    drawAxisRing(vc, mat, v.tx, v.ty, v.tz, tan, rIn, rOut, age * 0.4F, (int) (fade * 240));
                }
            }
        }
    }

    private static double[] quadBezier(double x0, double y0, double z0,
                                       double x1, double y1, double z1,
                                       double x2, double y2, double z2, float u) {
        double inv = 1.0 - u;
        double a = inv * inv, b = 2.0 * inv * u, c = u * u;
        return new double[]{a * x0 + b * x1 + c * x2, a * y0 + b * y1 + c * y2, a * z0 + b * z1 + c * z2};
    }

    private static double[] quadBezierTangent(double x0, double y0, double z0,
                                              double x1, double y1, double z1,
                                              double x2, double y2, double z2, float u) {
        double dx = 2.0 * (1.0 - u) * (x1 - x0) + 2.0 * u * (x2 - x1);
        double dy = 2.0 * (1.0 - u) * (y1 - y0) + 2.0 * u * (y2 - y1);
        double dz = 2.0 * (1.0 - u) * (z1 - z0) + 2.0 * u * (z2 - z1);
        double len = Math.max(1e-5, Math.sqrt(dx * dx + dy * dy + dz * dz));
        return new double[]{dx / len, dy / len, dz / len};
    }

    private static void buildBasis(double[] dir, float rollRad, double[] outRight, double[] outUp) {
        double refX = (Math.abs(dir[1]) > 0.92) ? 1.0 : 0.0;
        double refY = (Math.abs(dir[1]) > 0.92) ? 0.0 : 1.0;
        double refZ = 0.0;
        double rx = dir[1] * refZ - dir[2] * refY;
        double ry = dir[2] * refX - dir[0] * refZ;
        double rz = dir[0] * refY - dir[1] * refX;
        double rl = Math.max(1e-5, Math.sqrt(rx * rx + ry * ry + rz * rz));
        rx /= rl; ry /= rl; rz /= rl;
        double ux = ry * dir[2] - rz * dir[1];
        double uy = rz * dir[0] - rx * dir[2];
        double uz = rx * dir[1] - ry * dir[0];
        double c = Math.cos(rollRad), s = Math.sin(rollRad);
        outRight[0] = rx * c + ux * s; outRight[1] = ry * c + uy * s; outRight[2] = rz * c + uz * s;
        outUp[0]    = ux * c - rx * s; outUp[1]    = uy * c - ry * s; outUp[2]    = uz * c - rz * s;
    }

    private static double[] ptLocal(double ox, double oy, double oz, double[] fwd, double[] right, double[] up,
                                    double lf, double lr, double lu) {
        return new double[]{
                ox + fwd[0] * lf + right[0] * lr + up[0] * lu,
                oy + fwd[1] * lf + right[1] * lr + up[1] * lu,
                oz + fwd[2] * lf + right[2] * lr + up[2] * lu
        };
    }

    /** Dựng hình Đinh Ba Biển 3D phát sáng (cán, vương miện hình thoi sapphire, 3 mũi nhọn sắc nét & 2 vây sóng)! */
    private static void drawSpectralTrident3D(VertexConsumer vc, Matrix4f mat,
                                              double ox, double oy, double oz, double[] dir,
                                              float scale, float rollRad, float alphaMul,
                                              boolean crownFinisher, boolean glow) {
        if (scale <= 0.02F || alphaMul <= 0.02F) return;
        double[] right = new double[3], up = new double[3];
        buildBasis(dir, rollRad, right, up);

        int r = glow ? (crownFinisher ? 215 : 148) : (crownFinisher ? 44 : 20);
        int g = glow ? 250 : (crownFinisher ? 168 : 112);
        int b = 255;
        int a = (int) (alphaMul * (glow ? 235 : 210));
        float wShaft = (glow ? 0.048F : 0.068F) * scale;

        // 1. Thân cán Đinh Ba (2 mặt phẳng chữ thập 3D tạo khối đặc từ mọi góc camera)
        emitCrossBeam(vc, mat, ox, oy, oz, dir, right, up, -1.12 * scale, 0.36 * scale, 0.0, wShaft, r, g, b, a);

        // 2. Vương miện hình thoi + Ngọc Sapphire trung tâm (+0.20 .. +0.62)
        double[] cBot = ptLocal(ox, oy, oz, dir, right, up, 0.16 * scale, 0.0, 0.0);
        double[] cLft = ptLocal(ox, oy, oz, dir, right, up, 0.38 * scale, -0.32 * scale, 0.0);
        double[] cTop = ptLocal(ox, oy, oz, dir, right, up, 0.60 * scale, 0.0, 0.0);
        double[] cRgt = ptLocal(ox, oy, oz, dir, right, up, 0.38 * scale, +0.32 * scale, 0.0);
        emitQuad(vc, mat, cBot, cLft, cTop, cRgt, 0.35F, 0.65F, glow ? 230 : 62, glow ? 252 : 178, 255, a);

        // 3. Mũi Đinh Ba trung tâm dài & sắc nhọn (+0.48 .. +1.28)
        double[] mBaseL = ptLocal(ox, oy, oz, dir, right, up, 0.48 * scale, -0.075 * scale, 0.0);
        double[] mBaseR = ptLocal(ox, oy, oz, dir, right, up, 0.48 * scale, +0.075 * scale, 0.0);
        double[] mTipL  = ptLocal(ox, oy, oz, dir, right, up, 1.28 * scale, -0.008 * scale, 0.0);
        double[] mTipR  = ptLocal(ox, oy, oz, dir, right, up, 1.28 * scale, +0.008 * scale, 0.0);
        emitQuad(vc, mat, mBaseL, mBaseR, mTipR, mTipL, 0.35F, 0.65F, glow ? 235 : 88, 252, 255, a);
        emitCrossBeam(vc, mat, ox, oy, oz, dir, right, up, 0.48 * scale, 1.24 * scale, 0.0, 0.042F * scale,
                glow ? 220 : 66, 248, 255, a);

        // 4. Hai mũi phụ trái & phải (+0.34 .. +1.00, mở rộng ±0.26*scale)
        for (int sgn : new int[]{-1, +1}) {
            double off = sgn * 0.25 * scale;
            double[] sBaseIn  = ptLocal(ox, oy, oz, dir, right, up, 0.34 * scale, off - sgn * 0.045 * scale, 0.0);
            double[] sBaseOut = ptLocal(ox, oy, oz, dir, right, up, 0.34 * scale, off + sgn * 0.055 * scale, 0.0);
            double[] sTipOut  = ptLocal(ox, oy, oz, dir, right, up, 1.00 * scale, off + sgn * 0.012 * scale, 0.0);
            double[] sTipIn   = ptLocal(ox, oy, oz, dir, right, up, 1.00 * scale, off - sgn * 0.006 * scale, 0.0);
            emitQuad(vc, mat, sBaseIn, sBaseOut, sTipOut, sTipIn, 0.35F, 0.65F, glow ? 210 : 54, 244, 255, a);

            // Vây sóng nước uốn lùi hai bên vương miện!
            double[] f0 = ptLocal(ox, oy, oz, dir, right, up, 0.38 * scale, sgn * 0.26 * scale, 0.0);
            double[] f1 = ptLocal(ox, oy, oz, dir, right, up, 0.14 * scale, sgn * 0.48 * scale, 0.05 * scale);
            double[] f2 = ptLocal(ox, oy, oz, dir, right, up, -0.34 * scale, sgn * 0.34 * scale, -0.05 * scale);
            double[] f3 = ptLocal(ox, oy, oz, dir, right, up, -0.08 * scale, sgn * 0.12 * scale, 0.0);
            emitQuad(vc, mat, f0, f1, f2, f3, 0.35F, 0.65F, glow ? 175 : 32, glow ? 245 : 150, 255, (int) (a * 0.85F));
        }
    }

    private static void emitCrossBeam(VertexConsumer vc, Matrix4f mat,
                                      double ox, double oy, double oz, double[] fwd, double[] right, double[] up,
                                      double fStart, double fEnd, double rOff, float halfW,
                                      int r, int g, int b, int a) {
        double[] a0 = ptLocal(ox, oy, oz, fwd, right, up, fStart, rOff - halfW, 0.0);
        double[] a1 = ptLocal(ox, oy, oz, fwd, right, up, fStart, rOff + halfW, 0.0);
        double[] a2 = ptLocal(ox, oy, oz, fwd, right, up, fEnd,   rOff + halfW * 0.65, 0.0);
        double[] a3 = ptLocal(ox, oy, oz, fwd, right, up, fEnd,   rOff - halfW * 0.65, 0.0);
        emitQuad(vc, mat, a0, a1, a2, a3, 0.35F, 0.65F, r, g, b, a);

        double[] b0 = ptLocal(ox, oy, oz, fwd, right, up, fStart, rOff, -halfW);
        double[] b1 = ptLocal(ox, oy, oz, fwd, right, up, fStart, rOff, +halfW);
        double[] b2 = ptLocal(ox, oy, oz, fwd, right, up, fEnd,   rOff, +halfW * 0.65);
        double[] b3 = ptLocal(ox, oy, oz, fwd, right, up, fEnd,   rOff, -halfW * 0.65);
        emitQuad(vc, mat, b0, b1, b2, b3, 0.35F, 0.65F, r, g, b, a);
    }

    private static void drawVolleyCometTrail(VertexConsumer vc, Matrix4f mat,
                                             double sx, double sy, double sz,
                                             double mx, double my, double mz,
                                             double tx, double ty, double tz,
                                             float headU, float scale, boolean crownFinisher, boolean glow) {
        float tailU = Math.max(0.0F, headU - 0.44F);
        int segs = 10;
        double[] r0 = new double[3], u0 = new double[3];
        double[] r1 = new double[3], u1 = new double[3];
        float maxW = (glow ? 0.24F : 0.18F) * scale * (crownFinisher ? 1.25F : 1.0F);
        for (int i = 0; i < segs; i++) {
            float f0 = i / (float) segs;
            float f1 = (i + 1) / (float) segs;
            float p0u = Mth.lerp(f0, tailU, headU);
            float p1u = Mth.lerp(f1, tailU, headU);
            double[] p0 = quadBezier(sx, sy, sz, mx, my, mz, tx, ty, tz, p0u);
            double[] p1 = quadBezier(sx, sy, sz, mx, my, mz, tx, ty, tz, p1u);
            double[] t0 = quadBezierTangent(sx, sy, sz, mx, my, mz, tx, ty, tz, p0u);
            double[] t1 = quadBezierTangent(sx, sy, sz, mx, my, mz, tx, ty, tz, p1u);
            buildBasis(t0, 0.0F, r0, u0);
            buildBasis(t1, 0.0F, r1, u1);
            float w0 = maxW * f0;
            float w1 = maxW * f1;
            int alpha = (int) ((glow ? 215 : 145) * f1);
            int cr = glow ? (crownFinisher ? 220 : 155) : 34;
            int cg = glow ? 250 : 156;
            double[] q0 = {p0[0] - r0[0] * w0, p0[1] - r0[1] * w0, p0[2] - r0[2] * w0};
            double[] q1 = {p0[0] + r0[0] * w0, p0[1] + r0[1] * w0, p0[2] + r0[2] * w0};
            double[] q2 = {p1[0] + r1[0] * w1, p1[1] + r1[1] * w1, p1[2] + r1[2] * w1};
            double[] q3 = {p1[0] - r1[0] * w1, p1[1] - r1[1] * w1, p1[2] - r1[2] * w1};
            emitQuad(vc, mat, q0, q1, q2, q3, f0, f1, cr, cg, 255, alpha);

            double[] v0 = {p0[0] - u0[0] * w0, p0[1] - u0[1] * w0, p0[2] - u0[2] * w0};
            double[] v1 = {p0[0] + u0[0] * w0, p0[1] + u0[1] * w0, p0[2] + u0[2] * w0};
            double[] v2 = {p1[0] + u1[0] * w1, p1[1] + u1[1] * w1, p1[2] + u1[2] * w1};
            double[] v3 = {p1[0] - u1[0] * w1, p1[1] - u1[1] * w1, p1[2] - u1[2] * w1};
            emitQuad(vc, mat, v0, v1, v2, v3, f0, f1, cr, cg, 255, alpha);
        }
    }

    private static void drawAxisRing(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                                     double[] axis, float rIn, float rOut, float spin, int alpha) {
        if (alpha <= 2) return;
        double[] right = new double[3], up = new double[3];
        buildBasis(axis, spin, right, up);
        int segs = 16;
        for (int i = 0; i < segs; i++) {
            float a0 = (i / (float) segs) * Mth.TWO_PI;
            float a1 = ((i + 1) / (float) segs) * Mth.TWO_PI;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            double[] p0 = {cx + (right[0] * c0 + up[0] * s0) * rIn,  cy + (right[1] * c0 + up[1] * s0) * rIn,  cz + (right[2] * c0 + up[2] * s0) * rIn};
            double[] p1 = {cx + (right[0] * c0 + up[0] * s0) * rOut, cy + (right[1] * c0 + up[1] * s0) * rOut, cz + (right[2] * c0 + up[2] * s0) * rOut};
            double[] p2 = {cx + (right[0] * c1 + up[0] * s1) * rOut, cy + (right[1] * c1 + up[1] * s1) * rOut, cz + (right[2] * c1 + up[2] * s1) * rOut};
            double[] p3 = {cx + (right[0] * c1 + up[0] * s1) * rIn,  cy + (right[1] * c1 + up[1] * s1) * rIn,  cz + (right[2] * c1 + up[2] * s1) * rIn};
            emitQuad(vc, mat, p0, p1, p2, p3, 0.35F, 0.65F, 155, 246, 255, alpha);
        }
    }

    /**
     * Phễu Xoáy Nước Đại Dương 3D khổng lồ (Trident `[R]` / `Shift+RMB` — 4.0s):
     * - BODY: Tường phễu xoắn ốc xoay ngược chiều kim đồng hồ mở rộng lên cao.
     * - GLOW: 5 dải bọt sóng xoắn ốc tốc độ cao + vòng xoáy ma pháp phát sáng dưới đáy!
     */
    private static void drawWhirlpool(VertexConsumer vc, Matrix4f mat, WhirlpoolVfx wp, double now, boolean glow) {
        float age = (float) (now - wp.startTick);
        float t = Mth.clamp(age / wp.durationTicks, 0.0F, 1.0F);
        float env = Math.min(Mth.clamp(t / 0.10F, 0.0F, 1.0F), Mth.clamp((1.0F - t) / 0.14F, 0.0F, 1.0F));
        if (env <= 0.01F) return;

        float spinSpeed = wp.supercharged ? 0.42F : 0.26F;
        float baseSpin = age * spinSpeed;
        float curH = wp.height * (0.45F + 0.55F * env);

        if (!glow) {
            // 1. Tường phễu nước bán trong suốt nhiều tầng
            int vLayers = 6;
            int ringSegs = 20;
            for (int iy = 0; iy < vLayers; iy++) {
                float v0 = iy / (float) vLayers;
                float v1 = (iy + 1) / (float) vLayers;
                float r0 = Mth.lerp(v0 * v0, 1.25F, wp.radius * 0.92F) * env;
                float r1 = Mth.lerp(v1 * v1, 1.25F, wp.radius * 0.92F) * env;
                float y0 = v0 * curH;
                float y1 = v1 * curH;
                float twist0 = baseSpin + v0 * 2.8F;
                float twist1 = baseSpin + v1 * 2.8F;

                int a = (int) (env * (155 - 65 * v1));
                int red = wp.supercharged ? 45 : 18;
                int grn = (int) Mth.lerp(v1, 92.0F, wp.supercharged ? 225.0F : 178.0F);
                int blu = 255;

                for (int is = 0; is < ringSegs; is++) {
                    float a00 = twist0 + (is / (float) ringSegs) * Mth.TWO_PI;
                    float a10 = twist0 + ((is + 1) / (float) ringSegs) * Mth.TWO_PI;
                    float a01 = twist1 + (is / (float) ringSegs) * Mth.TWO_PI;
                    float a11 = twist1 + ((is + 1) / (float) ringSegs) * Mth.TWO_PI;

                    double[] p00 = {wp.cx + Math.cos(a00) * r0, wp.cy + y0, wp.cz + Math.sin(a00) * r0};
                    double[] p10 = {wp.cx + Math.cos(a10) * r0, wp.cy + y0, wp.cz + Math.sin(a10) * r0};
                    double[] p11 = {wp.cx + Math.cos(a11) * r1, wp.cy + y1, wp.cz + Math.sin(a11) * r1};
                    double[] p01 = {wp.cx + Math.cos(a01) * r1, wp.cy + y1, wp.cz + Math.sin(a01) * r1};
                    emitQuad(vc, mat, p00, p10, p11, p01, 0.0F, 1.0F, red, grn, blu, a);
                }
            }
        } else {
            // 2. 5 Dải Bọt Sóng Xoắn Ốc Phát Sáng Bay Lên Quanh Phễu!
            int strands = wp.supercharged ? 6 : 5;
            int steps = 18;
            for (int s = 0; s < strands; s++) {
                float phase = baseSpin * 1.35F + (s * Mth.TWO_PI / strands);
                for (int i = 0; i < steps; i++) {
                    float f0 = i / (float) steps;
                    float f1 = (i + 1) / (float) steps;
                    float rad0 = Mth.lerp(f0 * f0, 1.10F, wp.radius) * env;
                    float rad1 = Mth.lerp(f1 * f1, 1.10F, wp.radius) * env;
                    float ang0 = phase + f0 * 4.8F;
                    float ang1 = phase + f1 * 4.8F;
                    float y0 = f0 * curH;
                    float y1 = f1 * curH;
                    float thick = (0.34F + 0.28F * Mth.sin(f0 * Mth.PI)) * env;

                    double[] b0 = {wp.cx + Math.cos(ang0) * rad0, wp.cy + y0, wp.cz + Math.sin(ang0) * rad0};
                    double[] t0 = {wp.cx + Math.cos(ang0) * (rad0 + thick * 0.35F), wp.cy + y0 + thick, wp.cz + Math.sin(ang0) * (rad0 + thick * 0.35F)};
                    double[] t1 = {wp.cx + Math.cos(ang1) * (rad1 + thick * 0.35F), wp.cy + y1 + thick, wp.cz + Math.sin(ang1) * (rad1 + thick * 0.35F)};
                    double[] b1 = {wp.cx + Math.cos(ang1) * rad1, wp.cy + y1, wp.cz + Math.sin(ang1) * rad1};

                    int a = (int) (env * 230 * Mth.sin(Mth.clamp(f0, 0.05F, 0.95F) * Mth.PI));
                    int r = wp.supercharged ? 210 : 145;
                    int g = wp.supercharged ? 255 : 240;
                    emitQuad(vc, mat, b0, b1, t1, t0, f0, f1, r, g, 255, a);
                }
            }

            // 3. Vòng Ma Trận Nước Xoáy Phát Sáng Dưới Đáy!
            int ringSegs = 28;
            float rOuter = wp.radius * env;
            float rInner = rOuter * 0.78F;
            for (int i = 0; i < ringSegs; i++) {
                float a0 = -baseSpin + (i / (float) ringSegs) * Mth.TWO_PI;
                float a1 = -baseSpin + ((i + 1) / (float) ringSegs) * Mth.TWO_PI;
                double[] p0 = {wp.cx + Math.cos(a0) * rInner, wp.cy + 0.14, wp.cz + Math.sin(a0) * rInner};
                double[] p1 = {wp.cx + Math.cos(a0) * rOuter, wp.cy + 0.14, wp.cz + Math.sin(a0) * rOuter};
                double[] p2 = {wp.cx + Math.cos(a1) * rOuter, wp.cy + 0.14, wp.cz + Math.sin(a1) * rOuter};
                double[] p3 = {wp.cx + Math.cos(a1) * rInner, wp.cy + 0.14, wp.cz + Math.sin(a1) * rInner};
                int a = (int) (env * (wp.supercharged ? 220 : 175));
                emitQuad(vc, mat, p0, p1, p2, p3, 0.0F, 1.0F, wp.supercharged ? 180 : 90, 238, 255, a);
            }
        }
    }

    private static void drawBurst(VertexConsumer vc, Matrix4f mat, MaelstromBurstVfx b, double now, boolean glow) {
        float age = (float) (now - b.startTick);
        float t = Mth.clamp(age / b.durationTicks, 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        float fade = 1.0F - t;
        if (fade <= 0.02F) return;

        // Expanding horizontal tidal shockwave ring + towering central water geyser column!
        int segs = 28;
        float rOut = Mth.lerp(ease, 1.2F, b.maxRadius);
        float rIn = Math.max(0.2F, rOut - (glow ? 0.95F : 1.55F));
        for (int i = 0; i < segs; i++) {
            float a0 = (i / (float) segs) * Mth.TWO_PI;
            float a1 = ((i + 1) / (float) segs) * Mth.TWO_PI;
            double[] p0 = {b.cx + Math.cos(a0) * rIn, b.cy + 0.22, b.cz + Math.sin(a0) * rIn};
            double[] p1 = {b.cx + Math.cos(a0) * rOut, b.cy + 0.22, b.cz + Math.sin(a0) * rOut};
            double[] p2 = {b.cx + Math.cos(a1) * rOut, b.cy + 0.22, b.cz + Math.sin(a1) * rOut};
            double[] p3 = {b.cx + Math.cos(a1) * rIn, b.cy + 0.22, b.cz + Math.sin(a1) * rIn};
            int alpha = (int) (fade * (glow ? 235 : 165));
            int red = glow ? (b.supercharged ? 230 : 165) : 30;
            int grn = glow ? 250 : 156;
            emitQuad(vc, mat, p0, p1, p2, p3, 0.0F, 1.0F, red, grn, 255, alpha);
        }

        // Geyser column bursting skyward
        float colR = (glow ? 1.05F : 1.75F) * (0.45F + 0.55F * fade);
        float colH = (b.supercharged ? 11.5F : 8.0F) * ease;
        int colSegs = 12;
        for (int i = 0; i < colSegs; i++) {
            float a0 = age * 0.28F + (i / (float) colSegs) * Mth.TWO_PI;
            float a1 = age * 0.28F + ((i + 1) / (float) colSegs) * Mth.TWO_PI;
            double[] b0 = {b.cx + Math.cos(a0) * colR, b.cy, b.cz + Math.sin(a0) * colR};
            double[] b1 = {b.cx + Math.cos(a1) * colR, b.cy, b.cz + Math.sin(a1) * colR};
            double[] t1 = {b.cx + Math.cos(a1) * (colR * 1.45F), b.cy + colH, b.cz + Math.sin(a1) * (colR * 1.45F)};
            double[] t0 = {b.cx + Math.cos(a0) * (colR * 1.45F), b.cy + colH, b.cz + Math.sin(a0) * (colR * 1.45F)};
            int alpha = (int) (fade * (glow ? 215 : 145));
            emitQuad(vc, mat, b0, b1, t1, t0, 0.0F, 1.0F, glow ? 200 : 38, glow ? 252 : 162, 255, alpha);
        }
    }

    private static void emitQuad(VertexConsumer vc, Matrix4f mat,
                                 double[] p0, double[] p1, double[] p2, double[] p3,
                                 float u0, float u1, int r, int g, int b, int a) {
        if (a <= 2) return;
        vc.addVertex(mat, (float) p0[0], (float) p0[1], (float) p0[2]).setColor(r, g, b, a).setUv(u0, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        vc.addVertex(mat, (float) p1[0], (float) p1[1], (float) p1[2]).setColor(r, g, b, a).setUv(u1, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        vc.addVertex(mat, (float) p2[0], (float) p2[1], (float) p2[2]).setColor(r, g, b, a).setUv(u1, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        vc.addVertex(mat, (float) p3[0], (float) p3[1], (float) p3[2]).setColor(r, g, b, a).setUv(u0, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    private static void drawSparkQuad(VertexConsumer vc, Matrix4f mat, Quaternionf camRot,
                                      double cx, double cy, double cz, float halfSize,
                                      int r, int g, int b, int a) {
        if (a <= 2) return;
        // Crisp 4-point ocean starlight (tapered vertical diamond + horizontal diamond + bright white heart, zero blurry dome!)
        float w = halfSize * 0.24F;
        float c = halfSize * 0.34F;
        emitBillboardDiamond(vc, mat, camRot, cx, cy, cz, w, halfSize, r, g, b, a);
        emitBillboardDiamond(vc, mat, camRot, cx, cy, cz, halfSize, w, r, g, b, a);
        emitBillboardDiamond(vc, mat, camRot, cx, cy, cz, c, c, 255, 255, 255, a);
    }

    private static void emitBillboardDiamond(VertexConsumer vc, Matrix4f mat, Quaternionf camRot,
                                             double cx, double cy, double cz, float rx, float ry,
                                             int r, int g, int b, int a) {
        org.joml.Vector3f[] pts = {
                new org.joml.Vector3f(0.0F, -ry, 0.0F),
                new org.joml.Vector3f(rx, 0.0F, 0.0F),
                new org.joml.Vector3f(0.0F, ry, 0.0F),
                new org.joml.Vector3f(-rx, 0.0F, 0.0F)
        };
        for (org.joml.Vector3f p : pts) p.rotate(camRot);
        for (int i = 0; i < 4; i++) {
            vc.addVertex(mat, (float) cx + pts[i].x, (float) cy + pts[i].y, (float) cz + pts[i].z)
                    .setColor(r, g, b, a).setUv(0.5F, 0.5F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        }
    }
}
