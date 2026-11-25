package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.client.anim.DualBladeAnimState;
import com.abyssalrpg.combat.TogetsuMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Hệ thống VFX 3D Âm–Dương Song Nguyệt & Tuyệt Kỹ **Crescent Vorpal (`Chí Mệnh
 * Tam Nhật Nguyệt`)**:
 * - Tách tuyệt đối 3 pass tuần tự (`BODY` -> `GLOW` -> `SPARK`, chỉ giữ 1
 * `VertexConsumer` mỗi pass) -> 100% an toàn cho Minecraft 1.21.1
 * `MultiBufferSource`!
 * - **Dạng Song Đao:** Vệt chém Trắng Ngọc (Trăng Non) & Đen Obsidian viền Bạc
 * (Trăng Khuyết).
 * - **Dạng Đại Đao Song Nguyệt:** Vòng cung Đại Đao 2 tầng Âm–Dương khổng lồ
 * bán kính `3.2` block!
 * - **Hợp Nhất `[R]`:** Sóng xung kích Song Nguyệt Trắng–Đen xoáy bung quanh
 * người chơi.
 * - **Tuyệt kỹ `Crescent Vorpal`:** Luồng Năng Lượng Lưỡi Liềm Trăng Khuyết 3D
 * rộng `4.6` block lao xuyên thẳng `22` block về phía trước!
 */
public final class TogetsuVfxRenderer {
    public enum LunarPalette {
        WAXING_WHITE(110, 128, 162, 224, 240, 255, 255, 255, 255),
        WANING_BLACK(10, 12, 20, 54, 68, 96, 210, 232, 255),
        VORPAL_LUNAR(24, 34, 62, 132, 222, 255, 255, 255, 255);

        final int tailR, tailG, tailB;
        final int midR, midG, midB;
        final int coreR, coreG, coreB;

        LunarPalette(int tr, int tg, int tb, int mr, int mg, int mb, int cr, int cg, int cb) {
            this.tailR = tr;
            this.tailG = tg;
            this.tailB = tb;
            this.midR = mr;
            this.midG = mg;
            this.midB = mb;
            this.coreR = cr;
            this.coreG = cg;
            this.coreB = cb;
        }
    }

    private static final class LunarRenderTypes extends RenderType {
        private static final RenderType BODY = RenderType.create(
                "abyssalrpg_togetsu_body",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(DualBladeTrailRenderer.SLASH_ARC_TEX,
                                false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private static final RenderType GLOW = RenderType.create(
                "abyssalrpg_togetsu_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(DualBladeTrailRenderer.SLASH_ARC_TEX,
                                false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private static final RenderType SPARK = RenderType.create(
                "abyssalrpg_togetsu_spark",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                768,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(
                                new RenderStateShard.TextureStateShard(VfxRenderHelper.ENERGY_SLASH_TEX, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private LunarRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int buf,
                boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, fmt, mode, buf, crumble, sort, setup, clear);
        }
    }

    private static final class ArcStrip {
        final float[] bx, by, bz, tx, ty, tz;
        final int count;
        final LunarPalette palette;
        final float delayFrac, sweepFrac;

        ArcStrip(int count, float startDeg, float endDeg, float baseR, float tipR,
                float startY, float endY, float archY, float delayFrac, float sweepFrac, LunarPalette palette) {
            this.count = count;
            this.bx = new float[count];
            this.by = new float[count];
            this.bz = new float[count];
            this.tx = new float[count];
            this.ty = new float[count];
            this.tz = new float[count];
            this.delayFrac = delayFrac;
            this.sweepFrac = sweepFrac;
            this.palette = palette;
            for (int i = 0; i < count; i++) {
                float u = i / (float) (count - 1);
                float ang = Mth.lerp(u, startDeg, endDeg) * Mth.DEG_TO_RAD;
                float s = Mth.sin(ang);
                float c = Mth.cos(ang);
                float yt = Mth.lerp(u, startY, endY) + Mth.sin(u * Mth.PI) * archY;
                float yb = yt * 0.42F;
                this.bx[i] = s * baseR;
                this.by[i] = yb;
                this.bz[i] = c * baseR;
                this.tx[i] = s * tipR;
                this.ty[i] = yt;
                this.tz[i] = c * tipR;
            }
        }
    }

    private static final class SlashEffect {
        final int attackerId;
        final TogetsuMoveType move;
        final double ax, ay, az;
        final float yawDeg, pitchDeg;
        final long startTick;
        final float durationTicks;
        final List<ArcStrip> strips;

        SlashEffect(int attackerId, TogetsuMoveType move, Vec3 anchor, float yawDeg, float pitchDeg,
                long startTick, float durationTicks, List<ArcStrip> strips) {
            this.attackerId = attackerId;
            this.move = move;
            this.ax = anchor.x;
            this.ay = anchor.y;
            this.az = anchor.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.strips = strips;
        }
    }

    private static final class FusionShockwave {
        final double x, y, z;
        final boolean fusing;
        final boolean imploding;
        final long startTick;
        final float durationTicks;

        FusionShockwave(Vec3 pos, boolean fusing, boolean imploding, long startTick) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.fusing = fusing;
            this.imploding = imploding;
            this.startTick = startTick;
            this.durationTicks = imploding ? 10.0F : (fusing ? 13.0F : 9.0F);
        }
    }

    /**
     * Luồng năng lượng Lưỡi Liềm Trăng Khuyết khổng lồ của tuyệt kỹ Crescent Vorpal
     * lao xuyên 22 block!
     */
    private static final class CrescentVorpalWave {
        final Vec3 origin;
        final float yawDeg, pitchDeg;
        final float travelDist;
        final long startTick;
        final float durationTicks;

        CrescentVorpalWave(Vec3 origin, float yawDeg, float pitchDeg, float travelDist, long startTick) {
            this.origin = origin;
            this.yawDeg = yawDeg;
            this.pitchDeg = Mth.clamp(pitchDeg, -45.0F, 45.0F);
            this.travelDist = Mth.clamp(travelDist, 4.0F, 26.0F);
            this.startTick = startTick;
            this.durationTicks = 14.5F;
        }
    }

    private static final class LunarSpark {
        final double ox, oy, oz;
        final float vx, vy, vz, size;
        final int r, g, b;
        final long startTick;
        final float durationTicks;
        final boolean ambientStar;

        LunarSpark(double ox, double oy, double oz, float vx, float vy, float vz, float size,
                int r, int g, int b, long startTick, float durationTicks) {
            this(ox, oy, oz, vx, vy, vz, size, r, g, b, startTick, durationTicks, false);
        }

        LunarSpark(double ox, double oy, double oz, float vx, float vy, float vz, float size,
                int r, int g, int b, long startTick, float durationTicks, boolean ambientStar) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.r = r;
            this.g = g;
            this.b = b;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.ambientStar = ambientStar;
        }
    }

    /**
     * Vệt đâm xuyên Trắng–Đen sắc lẹm trên thân mục tiêu cho 12 nhát đâm vô địch!
     */
    private static final class PinThrustFlash {
        final double cx, cy, cz;
        final float yawDeg, pitchDeg, rollDeg;
        final float halfLen, halfWidth;
        final LunarPalette palette;
        final long startTick;
        final float durationTicks;

        PinThrustFlash(double cx, double cy, double cz, float yawDeg, float pitchDeg, float rollDeg,
                float halfLen, float halfWidth, LunarPalette palette, long startTick) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.halfLen = halfLen;
            this.halfWidth = halfWidth;
            this.palette = palette;
            this.startTick = startTick;
            this.durationTicks = 6.5F;
        }
    }

    private static final class MoonMarkRing {
        final double x, y, z;
        final long startTick;
        final float durationTicks;

        MoonMarkRing(Vec3 pos, long startTick) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.startTick = startTick;
            this.durationTicks = 40.0F; // 2.0s rotating Moon Mark ring on target!
        }
    }

    /**
     * Sóng lưỡi liềm xoay tròn 2 sắc Trắng–Đen của Signature RMB (Crescent Wave
     * Slash) bay AoE 16 block!
     */
    private static final class SpinningCrescentWave {
        final Vec3 origin;
        final float yawDeg, pitchDeg;
        final float travelDist;
        final long startTick;
        final float durationTicks;

        SpinningCrescentWave(Vec3 origin, float yawDeg, float pitchDeg, float travelDist, long startTick) {
            this.origin = origin;
            this.yawDeg = yawDeg;
            this.pitchDeg = Mth.clamp(pitchDeg, -42.0F, 42.0F);
            this.travelDist = Mth.clamp(travelDist, 4.0F, 20.0F);
            this.startTick = startTick;
            this.durationTicks = 12.0F;
        }
    }

    private static final List<SlashEffect> SLASHES = new ArrayList<>();
    private static final List<FusionShockwave> SHOCKWAVES = new ArrayList<>();
    private static final List<CrescentVorpalWave> VORPAL_WAVES = new ArrayList<>();
    private static final List<SpinningCrescentWave> SPINNING_WAVES = new ArrayList<>();
    private static final List<MoonMarkRing> MOON_MARKS = new ArrayList<>();
    private static final List<PinThrustFlash> PIN_STABS = new ArrayList<>();
    private static final List<LunarSpark> SPARKS = new ArrayList<>();

    private TogetsuVfxRenderer() {
    }

    public static void clear() {
        SLASHES.clear();
        SHOCKWAVES.clear();
        VORPAL_WAVES.clear();
        SPINNING_WAVES.clear();
        MOON_MARKS.clear();
        PIN_STABS.clear();
        SPARKS.clear();
    }

    public static void spawnSlashTrail(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg,
            TogetsuMoveType move, float animDurTicks, long clientTick) {
        if (!WeaponsConfig.get().enableSlashRibbon || !WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        for (int i = SLASHES.size() - 1; i >= 0; i--) {
            SlashEffect ex = SLASHES.get(i);
            if (ex.attackerId == attackerId && (clientTick - ex.startTick) <= 4L
                    && (ex.move == move || (ex.move.isFusedMove() && move.isFusedMove()))) {
                return;
            }
        }
        List<ArcStrip> strips = buildStrips(move);
        if (strips.isEmpty())
            return;
        if (SLASHES.size() >= 28)
            SLASHES.remove(0);
        float trailDur = (move == TogetsuMoveType.DUAL_5)
                ? Mth.clamp(animDurTicks * 0.96F, 9.6F, 11.5F)
                : Mth.clamp(animDurTicks * 0.78F, 6.2F, 11.5F);
        SLASHES.add(new SlashEffect(attackerId, move, anchor, yawDeg, pitchDeg, clientTick, trailDur, strips));
    }

    public static void spawnFusionShockwave(Vec3 pos, boolean fusing, long clientTick) {
        if (SHOCKWAVES.size() >= 12)
            SHOCKWAVES.remove(0);
        SHOCKWAVES.add(new FusionShockwave(pos, fusing, false, clientTick));
        spawnLunarCritBurst(pos, true, clientTick);
    }

    public static void spawnVorpalChargeImplosion(Vec3 pos, long clientTick) {
        if (SHOCKWAVES.size() >= 14)
            SHOCKWAVES.remove(0);
        SHOCKWAVES.add(new FusionShockwave(pos, true, true, clientTick));
    }

    public static void spawnCrescentVorpalWave(Vec3 start, float yawDeg, float pitchDeg, float travelDist,
            long clientTick) {
        if (VORPAL_WAVES.size() >= 10)
            VORPAL_WAVES.remove(0);
        VORPAL_WAVES.add(new CrescentVorpalWave(start, yawDeg, pitchDeg, travelDist, clientTick));
        spawnLunarCritBurst(start, true, clientTick);
    }

    public static void spawnSpinningCrescentWave(Vec3 start, float yawDeg, float pitchDeg, float travelDist,
            long clientTick) {
        if (SPINNING_WAVES.size() >= 12)
            SPINNING_WAVES.remove(0);
        SPINNING_WAVES.add(new SpinningCrescentWave(start, yawDeg, pitchDeg, travelDist, clientTick));
        spawnLunarCritBurst(start, true, clientTick);
        spawnLunarCritBurst(start, false, clientTick);
    }

    public static void spawnMoonMarkRing(Vec3 pos, long clientTick) {
        if (MOON_MARKS.size() >= 16)
            MOON_MARKS.remove(0);
        MOON_MARKS.add(new MoonMarkRing(pos, clientTick));
        spawnLunarCritBurst(pos, true, clientTick);
    }

    public static void spawnPinStabFlash(Vec3 targetPos, float yawDeg, boolean waxing, int stabIndex, long clientTick) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource rng = mc.level != null ? mc.level.random : RandomSource.create();
        LunarPalette pal = waxing ? LunarPalette.WAXING_WHITE : LunarPalette.WANING_BLACK;
        float yaw = yawDeg + (stabIndex * 31.0F) + (rng.nextFloat() - 0.5F) * 26.0F;
        float pitch = -36.0F + (stabIndex % 4) * 22.0F + (rng.nextFloat() - 0.5F) * 14.0F;
        float roll = (waxing ? 34.0F : -34.0F) + (rng.nextFloat() - 0.5F) * 20.0F;
        double ox = (rng.nextDouble() - 0.5) * 0.45;
        double oy = (rng.nextDouble() - 0.5) * 0.50;
        double oz = (rng.nextDouble() - 0.5) * 0.45;
        boolean finale = (stabIndex >= 12);
        if (PIN_STABS.size() >= 48)
            PIN_STABS.remove(0);
        PIN_STABS.add(new PinThrustFlash(
                targetPos.x + ox, targetPos.y + oy, targetPos.z + oz,
                yaw, pitch, roll,
                finale ? 2.55F : (1.55F + rng.nextFloat() * 0.45F),
                finale ? 0.34F : (0.20F + rng.nextFloat() * 0.08F),
                finale ? LunarPalette.VORPAL_LUNAR : pal,
                clientTick));
        spawnLunarCritBurst(targetPos, waxing, clientTick);
    }

    /**
     * Tạo 1 đốm sao lấp lánh 4 cánh bay bổng nhẹ dọc lưỡi kiếm khi ở tư thế thủ
     * Dual-Blade Idle!
     * (Lưỡi Đen phải: tím/magenta; Lưỡi Trắng trái: cyan/xanh băng — tự đồng bộ màu
     * tùy chỉnh [C]).
     */
    public static void spawnBladeSparkle(Vec3 pos, int rgb, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RandomSource rng = mc.level != null ? mc.level.random : RandomSource.create();
        Vec3 vel = new Vec3(
                (rng.nextFloat() - 0.5F) * 0.014F,
                0.018F + rng.nextFloat() * 0.018F,
                (rng.nextFloat() - 0.5F) * 0.014F);
        spawnBladeSparkle(pos, vel, rgb, clientTick);
    }

    /**
     * Phiên bản có vận tốc tùy chỉnh cho dải sao 4 cánh kéo vệt ngược về sau từ 2
     * mũi đao khi chạy nước rút (`TOGETSU_DUAL_SPRINT`).
     */
    public static void spawnBladeSparkle(Vec3 pos, Vec3 vel, int rgb, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RandomSource rng = mc.level != null ? mc.level.random : RandomSource.create();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (SPARKS.size() >= 180) {
            SPARKS.remove(0);
        }
        float sz = 0.044F + rng.nextFloat() * 0.024F;
        SPARKS.add(new LunarSpark(pos.x, pos.y, pos.z,
                (float) vel.x, (float) vel.y, (float) vel.z,
                sz, r, g, b, clientTick, 11.5F, true));
    }

    public static void spawnLunarCritBurst(Vec3 pos, boolean waxing, long clientTick) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource rng = mc.level != null ? mc.level.random : RandomSource.create();
        for (int i = 0; i < 12; i++) {
            double th = (Math.PI * 2.0 * i) / 12.0 + (rng.nextDouble() - 0.5) * 0.25;
            double ph = (rng.nextDouble() - 0.30) * Math.PI * 0.65;
            float spd = 0.28F + rng.nextFloat() * 0.36F;
            boolean white = waxing ? (i % 3 != 0) : (i % 3 == 0);
            int r = white ? 255 : 118;
            int g = white ? 252 : 210;
            int b = 255;
            if (SPARKS.size() >= 96)
                SPARKS.remove(0);
            SPARKS.add(new LunarSpark(pos.x, pos.y, pos.z,
                    (float) (Math.cos(th) * Math.cos(ph) * spd),
                    (float) (Math.sin(ph) * spd + 0.10),
                    (float) (Math.sin(th) * Math.cos(ph) * spd),
                    0.095F + rng.nextFloat() * 0.055F, r, g, b, clientTick, 6.5F));
        }
    }

    private static List<ArcStrip> buildStrips(TogetsuMoveType move) {
        List<ArcStrip> list = new ArrayList<>(2);
        switch (move) {
            // Neo trực tiếp phía trước tầm ngực/mắt bán kính gọn 1.8 block khớp chuẩn mặt
            // phẳng vung lưỡi Eclipse Style!
            case DUAL_1 -> {
                // Nhát 1 Song Đao: 1 dải Đen duy nhất (Tay Phải: Phải-Trên -> Trái-Dưới)
                list.add(new ArcStrip(28, -82.0F, 82.0F, 0.62F, 1.85F, 0.56F, -0.42F, 0.10F, 0.10F, 0.34F,
                        LunarPalette.WANING_BLACK));
            }
            case DUAL_2 -> {
                // Nhát 2 Song Đao: 1 dải Trắng duy nhất (Tay Trái: Trái-Trên -> Phải-Dưới)
                list.add(new ArcStrip(28, 82.0F, -82.0F, 0.62F, 1.85F, 0.56F, -0.42F, 0.10F, 0.10F, 0.34F,
                        LunarPalette.WAXING_WHITE));
            }
            case DUAL_3 -> {
                // Nhát 3 Song Đao: 1 dải Đen duy nhất (Tay Phải chém hất: Trái-Dưới ->
                // Phải-Trên)
                list.add(new ArcStrip(28, 80.0F, -80.0F, 0.62F, 1.88F, -0.44F, 0.60F, 0.12F, 0.10F, 0.34F,
                        LunarPalette.WANING_BLACK));
            }
            case DUAL_4 -> {
                // Nhát 4 Song Đao: 1 dải Trắng duy nhất (Tay Trái chém ngược: Phải-Dưới ->
                // Trái-Trên + Moon Mark)
                list.add(new ArcStrip(28, -80.0F, 80.0F, 0.62F, 1.88F, -0.44F, 0.60F, 0.12F, 0.10F, 0.34F,
                        LunarPalette.WAXING_WHITE));
            }
            case DUAL_5 -> {
                // NHÁT 5 ĐÒN ĐÁNH ĐẶC BIỆT (Auto-Fuse Eclipse Morphing Finisher): vòng bổ 180°
                // Trắng–Đen siêu rộng từ đỉnh đầu xuống chéo!
                list.add(new ArcStrip(36, 86.0F, -86.0F, 0.64F, 1.98F, 1.22F, -0.58F, 0.26F, 0.28F, 0.30F,
                        LunarPalette.WAXING_WHITE));
                list.add(new ArcStrip(36, 86.0F, -86.0F, 0.74F, 2.04F, 1.16F, -0.64F, 0.22F, 0.30F, 0.32F,
                        LunarPalette.WANING_BLACK));
            }
            case DUAL_SPRINT_THRUST -> {
                list.add(new ArcStrip(26, -82.0F, 82.0F, 0.62F, 1.85F, 0.28F, -0.22F, 0.12F, 0.0F, 0.36F,
                        LunarPalette.WANING_BLACK));
                list.add(new ArcStrip(26, 82.0F, -82.0F, 0.62F, 1.85F, 0.22F, -0.18F, 0.12F, 0.06F, 0.36F,
                        LunarPalette.WAXING_WHITE));
            }
            case DUAL_AIR_SLASH -> {
                list.add(new ArcStrip(28, -86.0F, 86.0F, 0.62F, 1.88F, -0.36F, 0.52F, 0.16F, 0.0F, 0.40F,
                        LunarPalette.WAXING_WHITE));
                list.add(new ArcStrip(28, 86.0F, -86.0F, 0.62F, 1.84F, -0.32F, 0.48F, -0.12F, 0.04F, 0.40F,
                        LunarPalette.WANING_BLACK));
            }
            case DUAL_LANDING_DASH -> {
                list.add(new ArcStrip(28, -84.0F, 84.0F, 0.62F, 1.86F, 0.04F, -0.04F, 0.05F, 0.0F, 0.36F,
                        LunarPalette.WANING_BLACK));
                list.add(new ArcStrip(28, 84.0F, -84.0F, 0.62F, 1.86F, -0.02F, 0.06F, -0.05F, 0.02F, 0.36F,
                        LunarPalette.WAXING_WHITE));
            }
            case FUSED_1 -> {
                // Song Nguyệt Nhát 1 (0.3s): Dải bổ chém cắm thẳng từ Đỉnh Đầu-Phải xuống mặt
                // đất bên Trái!
                list.add(new ArcStrip(34, -46.0F, 48.0F, 0.62F, 1.96F, 1.32F, -0.94F, 0.22F, 0.12F, 0.34F,
                        LunarPalette.WAXING_WHITE));
                list.add(new ArcStrip(34, -46.0F, 48.0F, 0.72F, 2.04F, 1.26F, -0.98F, 0.18F, 0.14F, 0.36F,
                        LunarPalette.WANING_BLACK));
            }
            case FUSED_2 -> {
                // Song Nguyệt Nhát 2 (0.3s): Dải bổ chém cắm thẳng từ Đỉnh Đầu-Trái xuống mặt
                // đất bên Phải!
                list.add(new ArcStrip(34, 46.0F, -48.0F, 0.62F, 1.96F, 1.32F, -0.94F, 0.22F, 0.12F, 0.34F,
                        LunarPalette.WAXING_WHITE));
                list.add(new ArcStrip(34, 46.0F, -48.0F, 0.72F, 2.04F, 1.26F, -0.98F, 0.18F, 0.14F, 0.36F,
                        LunarPalette.WANING_BLACK));
            }
            case FUSED_3 -> {
                // Song Nguyệt Nhát 3 (0.3s): Dải chém hất vòng cung thanh thoát từ Thấp-Trái
                // lên Cao-Phải (Rising Crescent Sweep!)
                list.add(new ArcStrip(34, 82.0F, -82.0F, 0.66F, 2.08F, -0.46F, 0.72F, 0.14F, 0.08F, 0.32F,
                        LunarPalette.WAXING_WHITE));
                list.add(new ArcStrip(34, 82.0F, -82.0F, 0.76F, 2.16F, -0.52F, 0.66F, 0.10F, 0.10F, 0.34F,
                        LunarPalette.WANING_BLACK));
            }
            case CRESCENT_WAVE_SLASH -> {
                // Signature RMB Draw-Slash: dải rút kiếm ngang sắc nét Trái -> Phải trực diện
                // 2.05 block!
                list.add(new ArcStrip(32, 86.0F, -86.0F, 0.68F, 2.05F, -0.04F, 0.06F, 0.04F, 0.02F, 0.32F,
                        LunarPalette.WAXING_WHITE));
                list.add(new ArcStrip(32, 86.0F, -86.0F, 0.80F, 2.14F, -0.10F, 0.00F, 0.04F, 0.04F, 0.34F,
                        LunarPalette.WANING_BLACK));
            }
            default -> {
            }
        }
        return list;
    }

    public static void renderWorld(WorldRenderContext context, long clientTick) {
        if (SLASHES.isEmpty() && SHOCKWAVES.isEmpty() && VORPAL_WAVES.isEmpty()
                && SPINNING_WAVES.isEmpty() && MOON_MARKS.isEmpty() && PIN_STABS.isEmpty() && SPARKS.isEmpty()) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        ClientLevel level = context.world();
        if (poseStack == null || consumers == null || level == null) {
            return;
        }

        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        float nowF = clientTick + partialTick;
        Camera camera = context.camera();
        Vec3 camPos = camera.getPosition();
        Minecraft mc = Minecraft.getInstance();
        int localId = mc.player != null ? mc.player.getId() : -1;
        boolean firstPerson = !camera.isDetached();

        SLASHES.removeIf(s -> Math.max(0.0F, nowF - s.startTick) >= s.durationTicks);
        SHOCKWAVES.removeIf(sw -> Math.max(0.0F, nowF - sw.startTick) >= sw.durationTicks);
        VORPAL_WAVES.removeIf(vw -> Math.max(0.0F, nowF - vw.startTick) >= vw.durationTicks);
        SPINNING_WAVES.removeIf(spw -> Math.max(0.0F, nowF - spw.startTick) >= spw.durationTicks);
        MOON_MARKS.removeIf(mm -> Math.max(0.0F, nowF - mm.startTick) >= mm.durationTicks);
        PIN_STABS.removeIf(ps -> Math.max(0.0F, nowF - ps.startTick) >= ps.durationTicks);
        SPARKS.removeIf(sp -> Math.max(0.0F, nowF - sp.startTick) >= sp.durationTicks);

        boolean hasQuads = !SLASHES.isEmpty() || !SHOCKWAVES.isEmpty() || !VORPAL_WAVES.isEmpty()
                || !SPINNING_WAVES.isEmpty() || !MOON_MARKS.isEmpty() || !PIN_STABS.isEmpty();

        // Pass 1: BODY (duy nhất 1 VertexConsumer đang mở!)
        if (hasQuads) {
            VertexConsumer bodyVc = consumers.getBuffer(LunarRenderTypes.BODY);
            renderPass(poseStack, bodyVc, false, level, camPos, nowF, partialTick, localId, firstPerson);
        }

        // Pass 2: GLOW (additive — lấy buffer mới sau khi hoàn tất Pass 1!)
        if (hasQuads) {
            VertexConsumer glowVc = consumers.getBuffer(LunarRenderTypes.GLOW);
            renderPass(poseStack, glowVc, true, level, camPos, nowF, partialTick, localId, firstPerson);
        }

        // Pass 3: SPARK (tia sáng Chí Mạng Song Nguyệt)
        if (!SPARKS.isEmpty()) {
            VertexConsumer sparkVc = consumers.getBuffer(LunarRenderTypes.SPARK);
            for (LunarSpark sp : SPARKS) {
                float t = Mth.clamp(Math.max(0.0F, nowF - sp.startTick) / sp.durationTicks, 0.0F, 1.0F);
                renderSpark(poseStack, sparkVc, camera, camPos, sp, t);
            }
        }
    }

    private static void renderPass(PoseStack poseStack, VertexConsumer vc, boolean glowPass,
            ClientLevel level, Vec3 camPos, float nowF, float partialTick,
            int localId, boolean firstPerson) {
        // 1. Vệt chém Song Đao & Đại Đao Song Nguyệt
        for (SlashEffect tr : SLASHES) {
            float progress = Mth.clamp(Math.max(0.0F, nowF - tr.startTick) / tr.durationTicks, 0.0F, 1.0F);
            double wx = tr.ax, wy = tr.ay, wz = tr.az;
            Entity ent = level.getEntity(tr.attackerId);
            if (ent != null && ent.isAlive()) {
                Vec3 ep = ent.getPosition(partialTick);
                wx = ep.x;
                // Neo chuẩn tầm ngực `eyeY - 0.35` để lưỡi kiếm chém cắt thẳng qua tâm vệt
                // sáng!
                wy = ep.y + ent.getEyeHeight() - 0.35;
                wz = ep.z;
            }
            boolean localFp = firstPerson && tr.attackerId == localId;
            boolean spin360 = false;
            float scale = localFp ? 0.80F : 1.00F;
            float fade = progress < 0.10F ? (progress / 0.10F)
                    : (1.0F - Mth.clamp((progress - 0.50F) / 0.50F, 0.0F, 1.0F));
            fade = fade * fade * (3.0F - 2.0F * fade);
            if (fade <= 0.01F)
                continue;

            float alignedPitch = spin360 ? 0.0F : Mth.clamp(tr.pitchDeg * 0.72F, -48.0F, 48.0F);
            poseStack.pushPose();
            poseStack.translate(wx - camPos.x, wy - camPos.y, wz - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-tr.yawDeg));
            if (Math.abs(alignedPitch) > 0.05F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(alignedPitch));
            }
            // Đẩy tâm vòng cung tiến trực tiếp theo vector hướng nhìn phía trước người chơi
            // (trừ chiêu xoay 360° quanh trục thân)!
            if (!spin360) {
                poseStack.translate(0.0, localFp ? -0.10 : 0.0, localFp ? 0.44 : 0.30);
            }
            poseStack.scale(scale, scale, scale);
            for (ArcStrip strip : tr.strips) {
                drawStrip(poseStack.last(), vc, glowPass, strip, progress, fade);
            }
            poseStack.popPose();
        }

        // 2. Sóng xung kích Hợp Nhất / Tách Song Nguyệt & Vòng hút tụ lực 4s Crescent
        // Vorpal!
        for (FusionShockwave sw : SHOCKWAVES) {
            float p = Mth.clamp(Math.max(0.0F, nowF - sw.startTick) / sw.durationTicks, 0.0F, 1.0F);
            float ease = DualBladeAnimState.easeOutQuart(p);
            float fade = sw.imploding ? Mth.sin(p * Mth.PI) : ((1.0F - p) * (1.0F - p));
            if (fade <= 0.01F)
                continue;
            float rOuter = sw.imploding
                    ? Mth.lerp(ease, 3.4F, 0.45F)
                    : Mth.lerp(ease, 0.65F, sw.fusing ? 4.2F : 2.8F);
            float rInner = rOuter * 0.62F;
            poseStack.pushPose();
            poseStack.translate(sw.x - camPos.x, sw.y - camPos.y, sw.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(p * (sw.imploding ? -260.0F : (sw.fusing ? 240.0F : -180.0F))));
            drawLunarRing(poseStack.last(), vc, glowPass, rInner, rOuter, 0.0F, fade,
                    sw.imploding ? LunarPalette.VORPAL_LUNAR : LunarPalette.WAXING_WHITE);
            poseStack.mulPose(Axis.XP.rotationDegrees(26.0F));
            drawLunarRing(poseStack.last(), vc, glowPass, rInner * 0.92F, rOuter * 0.92F, 0.22F, fade,
                    LunarPalette.WANING_BLACK);
            poseStack.popPose();
        }

        // 3. LUỒNG SÓNG TRĂNG KHUYẾT KHỔNG LỒ: CRESCENT VORPAL (CHÍ MỆNH TAM NHẬT
        // NGUYỆT — 22 BLOCK!)
        for (CrescentVorpalWave vw : VORPAL_WAVES) {
            float p = Mth.clamp(Math.max(0.0F, nowF - vw.startTick) / vw.durationTicks, 0.0F, 1.0F);
            float travel = DualBladeAnimState.easeOutCubic(Mth.clamp(p / 0.85F, 0.0F, 1.0F));
            float fade = p < 0.08F ? (p / 0.08F) : (1.0F - Mth.clamp((p - 0.68F) / 0.32F, 0.0F, 1.0F));
            if (fade <= 0.01F)
                continue;

            Vec3 fwd = Vec3.directionFromRotation(vw.pitchDeg, vw.yawDeg).normalize();
            Vec3 headPos = vw.origin.add(fwd.scale(1.4 + travel * (vw.travelDist - 1.4)));
            float scale = 0.85F + 0.55F * Mth.sin(Mth.clamp(p * 1.4F, 0.0F, 1.0F) * Mth.PI * 0.5F);

            poseStack.pushPose();
            poseStack.translate(headPos.x - camPos.x, headPos.y - camPos.y, headPos.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-vw.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(vw.pitchDeg));
            // Nghiêng nhẹ lưỡi liềm 14° tạo dáng chém xiên siêu ngầu như Sunraku chém xuyên
            // Tensei!
            poseStack.mulPose(Axis.ZP.rotationDegrees(14.0F));
            poseStack.scale(scale, scale, scale);
            drawVorpalCrescentMesh(poseStack.last(), vc, glowPass, fade);
            poseStack.popPose();
        }

        // 4. 12 vệt đâm xuyên Trắng–Đen sắc lẹm trên thân mục tiêu bị đè!
        for (PinThrustFlash ps : PIN_STABS) {
            float p = Mth.clamp(Math.max(0.0F, nowF - ps.startTick) / ps.durationTicks, 0.0F, 1.0F);
            float exp = DualBladeAnimState.easeOutQuart(Mth.clamp(p / 0.28F, 0.0F, 1.0F));
            float fade = p < 0.14F ? (p / 0.14F) : (1.0F - Mth.clamp((p - 0.30F) / 0.70F, 0.0F, 1.0F));
            if (fade <= 0.01F)
                continue;
            float len = ps.halfLen * (0.30F + 0.70F * exp);
            float w = ps.halfWidth * (1.05F - p * 0.55F);
            LunarPalette pal = ps.palette;
            int aTail = Mth.clamp(Math.round(fade * 155.0F), 0, 255);
            int aMid = Mth.clamp(Math.round(fade * 245.0F), 0, 255);
            int aCore = Mth.clamp(Math.round(fade * 255.0F), 0, 255);

            poseStack.pushPose();
            poseStack.translate(ps.cx - camPos.x, ps.cy - camPos.y, ps.cz - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-ps.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(ps.pitchDeg));
            poseStack.mulPose(Axis.ZP.rotationDegrees(ps.rollDeg));
            PoseStack.Pose pose = poseStack.last();
            if (!glowPass) {
                emitQuad(vc, pose,
                        -len, 0.0F, 0.0F, 0.02F, 0.50F, pal.tailR, pal.tailG, pal.tailB, aTail,
                        0.0F, -w, 0.0F, 0.50F, 0.08F, pal.tailR, pal.tailG, pal.tailB, aTail,
                        0.0F, w, 0.0F, 0.50F, 0.92F, pal.midR, pal.midG, pal.midB, aMid,
                        -len, 0.0F, 0.0F, 0.02F, 0.50F, pal.midR, pal.midG, pal.midB, aMid);
                emitQuad(vc, pose,
                        0.0F, -w, 0.0F, 0.50F, 0.08F, pal.tailR, pal.tailG, pal.tailB, aTail,
                        len, 0.0F, 0.0F, 0.98F, 0.50F, pal.tailR, pal.tailG, pal.tailB, aTail,
                        len, 0.0F, 0.0F, 0.98F, 0.50F, pal.midR, pal.midG, pal.midB, aMid,
                        0.0F, w, 0.0F, 0.50F, 0.92F, pal.midR, pal.midG, pal.midB, aMid);
            } else {
                float mw = w * 0.52F;
                emitQuad(vc, pose,
                        -len, 0.0F, 0.01F, 0.02F, 0.92F, pal.coreR, pal.coreG, pal.coreB, aCore,
                        0.0F, -mw, 0.01F, 0.50F, 0.80F, pal.midR, pal.midG, pal.midB, aMid,
                        len, 0.0F, 0.01F, 0.98F, 0.92F, pal.coreR, pal.coreG, pal.coreB, aCore,
                        0.0F, mw, 0.01F, 0.50F, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore);
            }
            poseStack.popPose();
        }

        // 5. Spinning Dual-Colored (Black & White) Crescent Projectile Wave (Signature
        // RMB Crescent Wave Slash!)
        for (SpinningCrescentWave spw : SPINNING_WAVES) {
            float p = Mth.clamp(Math.max(0.0F, nowF - spw.startTick) / spw.durationTicks, 0.0F, 1.0F);
            float travel = DualBladeAnimState.easeOutCubic(Math.min(1.0F, p / 0.92F));
            float fade = p < 0.08F ? (p / 0.08F) : (1.0F - Mth.clamp((p - 0.72F) / 0.28F, 0.0F, 1.0F));
            if (fade <= 0.01F)
                continue;

            Vec3 fwd = Vec3.directionFromRotation(spw.pitchDeg, spw.yawDeg).normalize();
            Vec3 headPos = spw.origin.add(fwd.scale(1.2 + travel * (spw.travelDist - 1.2)));
            float spinDeg = p * 780.0F; // Fast dual-colored spinning crescent disc!
            float sc = 0.78F + 0.34F * Mth.sin(Math.min(1.0F, p * 1.5F) * Mth.PI * 0.5F);

            poseStack.pushPose();
            poseStack.translate(headPos.x - camPos.x, headPos.y - camPos.y, headPos.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-spw.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(spw.pitchDeg));
            poseStack.mulPose(Axis.ZP.rotationDegrees(10.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(spinDeg));
            poseStack.scale(sc, sc, sc);
            drawLunarRing(poseStack.last(), vc, glowPass, 0.95F, 1.95F, 0.03F, fade, LunarPalette.WAXING_WHITE);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            drawLunarRing(poseStack.last(), vc, glowPass, 0.85F, 1.85F, -0.03F, fade, LunarPalette.WANING_BLACK);
            poseStack.popPose();
        }

        // 6. Rotating Moon Mark Ring (Hit 3 Reverse-Grip Twin Thrust mark on hit
        // target!)
        for (MoonMarkRing mm : MOON_MARKS) {
            float p = Mth.clamp(Math.max(0.0F, nowF - mm.startTick) / mm.durationTicks, 0.0F, 1.0F);
            float pop = DualBladeAnimState.easeOutQuart(Math.min(1.0F, p / 0.14F));
            float fade = p < 0.08F ? (p / 0.08F) : (1.0F - Mth.clamp((p - 0.75F) / 0.25F, 0.0F, 1.0F));
            if (fade <= 0.01F)
                continue;
            float rOuter = Mth.lerp(pop, 0.35F, 1.05F);
            float rInner = rOuter * 0.68F;
            poseStack.pushPose();
            poseStack.translate(mm.x - camPos.x, mm.y - camPos.y, mm.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(p * 360.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(18.0F));
            drawLunarRing(poseStack.last(), vc, glowPass, rInner, rOuter, 0.02F, fade, LunarPalette.WAXING_WHITE);
            poseStack.mulPose(Axis.XP.rotationDegrees(-36.0F));
            drawLunarRing(poseStack.last(), vc, glowPass, rInner * 0.88F, rOuter * 0.88F, -0.02F, fade,
                    LunarPalette.WANING_BLACK);
            poseStack.popPose();
        }
    }

    /**
     * Vẽ lưới 3D Lưỡi Liềm Trăng Khuyết khổng lồ (rộng 4.6 block, đuôi kéo ngược
     * 3.2 block) phóng xuyên 22 block!
     */
    private static void drawVorpalCrescentMesh(PoseStack.Pose pose, VertexConsumer vc, boolean glowPass, float fade) {
        int segs = 22;
        LunarPalette pal = LunarPalette.VORPAL_LUNAR;
        float arcRad = 136.0F * Mth.DEG_TO_RAD;
        float rOuter = 2.65F;
        float rInner = 1.45F;

        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs;
            float u1 = (i + 1) / (float) segs;
            float a0 = Mth.lerp(u0, -arcRad * 0.5F, arcRad * 0.5F);
            float a1 = Mth.lerp(u1, -arcRad * 0.5F, arcRad * 0.5F);

            float t0 = (float) Math.pow(Math.max(0.0F, Mth.sin(u0 * Mth.PI)), 0.55);
            float t1 = (float) Math.pow(Math.max(0.0F, Mth.sin(u1 * Mth.PI)), 0.55);

            // Cạnh cắt sáng dẫn đầu (+Z) & đuôi năng lượng kéo lùi (-Z) theo hình trăng
            // khuyết sắc lẹm!
            float ox0 = Mth.sin(a0) * rOuter;
            float oz0 = (Mth.cos(a0) - 0.38F) * rOuter;
            float ox1 = Mth.sin(a1) * rOuter;
            float oz1 = (Mth.cos(a1) - 0.38F) * rOuter;

            float ix0 = Mth.sin(a0) * Mth.lerp(t0, rOuter, rInner);
            float iz0 = (Mth.cos(a0) - 0.38F) * Mth.lerp(t0, rOuter, rInner) - 2.35F * t0;
            float ix1 = Mth.sin(a1) * Mth.lerp(t1, rOuter, rInner);
            float iz1 = (Mth.cos(a1) - 0.38F) * Mth.lerp(t1, rOuter, rInner) - 2.35F * t1;

            float yArch0 = 0.22F * Mth.cos(a0);
            float yArch1 = 0.22F * Mth.cos(a1);

            int aCore0 = Mth.clamp(Math.round(fade * (0.45F + 0.55F * t0) * 255.0F), 0, 255);
            int aCore1 = Mth.clamp(Math.round(fade * (0.45F + 0.55F * t1) * 255.0F), 0, 255);
            int aTail0 = Mth.clamp(Math.round(fade * t0 * 165.0F), 0, 255);
            int aTail1 = Mth.clamp(Math.round(fade * t1 * 165.0F), 0, 255);

            if (!glowPass) {
                // Mặt chính Lưỡi Liềm (Đen-Lam Đêm -> Xanh Trăng -> Trắng Lóa)
                emitQuad(vc, pose,
                        ix0, yArch0 * 0.4F, iz0, u0, 0.05F, pal.tailR, pal.tailG, pal.tailB, aTail0,
                        ix1, yArch1 * 0.4F, iz1, u1, 0.05F, pal.tailR, pal.tailG, pal.tailB, aTail1,
                        ox1, yArch1, oz1, u1, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore1,
                        ox0, yArch0, oz0, u0, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore0);
                // Cánh dọc chữ thập giúp nhìn dày 3D dù ở góc nhìn ngang hay trên xuống!
                emitQuad(vc, pose,
                        ix0, -0.32F * t0, iz0, u0, 0.12F, pal.tailR, pal.tailG, pal.tailB, aTail0 / 2,
                        ix1, -0.32F * t1, iz1, u1, 0.12F, pal.tailR, pal.tailG, pal.tailB, aTail1 / 2,
                        ox1, 0.32F * t1, oz1, u1, 0.92F, pal.midR, pal.midG, pal.midB, aCore1,
                        ox0, 0.32F * t0, oz0, u0, 0.92F, pal.midR, pal.midG, pal.midB, aCore0);
            } else {
                // Hào quang rực chói dọc mũi cắt lưỡi liềm!
                float mx0 = Mth.lerp(0.60F, ix0, ox0), mz0 = Mth.lerp(0.60F, iz0, oz0);
                float mx1 = Mth.lerp(0.60F, ix1, ox1), mz1 = Mth.lerp(0.60F, iz1, oz1);
                emitQuad(vc, pose,
                        mx0, yArch0 + 0.04F, mz0, u0, 0.60F, pal.midR, pal.midG, pal.midB, aCore0,
                        mx1, yArch1 + 0.04F, mz1, u1, 0.60F, pal.midR, pal.midG, pal.midB, aCore1,
                        ox1, yArch1 + 0.06F, oz1, u1, 0.99F, pal.coreR, pal.coreG, pal.coreB, aCore1,
                        ox0, yArch0 + 0.06F, oz0, u0, 0.99F, pal.coreR, pal.coreG, pal.coreB, aCore0);
            }
        }
    }

    private static void drawLunarRing(PoseStack.Pose pose, VertexConsumer vc, boolean glowPass,
            float rInner, float rOuter, float yOff, float fade, LunarPalette pal) {
        int segs = 28;
        int aIn = Mth.clamp(Math.round(fade * 140.0F), 0, 255);
        int aOut = Mth.clamp(Math.round(fade * 250.0F), 0, 255);
        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs;
            float u1 = (i + 1) / (float) segs;
            float a0 = u0 * Mth.TWO_PI;
            float a1 = u1 * Mth.TWO_PI;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            if (!glowPass) {
                emitQuad(vc, pose,
                        c0 * rInner, yOff, s0 * rInner, u0, 0.10F, pal.tailR, pal.tailG, pal.tailB, aIn,
                        c1 * rInner, yOff, s1 * rInner, u1, 0.10F, pal.tailR, pal.tailG, pal.tailB, aIn,
                        c1 * rOuter, yOff, s1 * rOuter, u1, 0.96F, pal.coreR, pal.coreG, pal.coreB, aOut,
                        c0 * rOuter, yOff, s0 * rOuter, u0, 0.96F, pal.coreR, pal.coreG, pal.coreB, aOut);
            } else {
                float rMid = Mth.lerp(0.65F, rInner, rOuter);
                emitQuad(vc, pose,
                        c0 * rMid, yOff + 0.02F, s0 * rMid, u0, 0.65F, pal.midR, pal.midG, pal.midB, aOut,
                        c1 * rMid, yOff + 0.02F, s1 * rMid, u1, 0.65F, pal.midR, pal.midG, pal.midB, aOut,
                        c1 * rOuter, yOff + 0.03F, s1 * rOuter, u1, 0.98F, pal.coreR, pal.coreG, pal.coreB, aOut,
                        c0 * rOuter, yOff + 0.03F, s0 * rOuter, u0, 0.98F, pal.coreR, pal.coreG, pal.coreB, aOut);
            }
        }
    }

    private static void drawStrip(PoseStack.Pose pose, VertexConsumer vc, boolean glowPass,
            ArcStrip strip, float progress, float globalFade) {
        float localP = Mth.clamp((progress - strip.delayFrac) / Math.max(0.15F, 1.0F - strip.delayFrac), 0.0F, 1.0F);
        float head = DualBladeAnimState.easeOutQuart(Mth.clamp(localP / strip.sweepFrac, 0.0F, 1.0F));
        float tail = Mth.clamp((localP - 0.36F) / 0.64F, 0.0F, 0.92F);
        if (head <= tail + 0.03F)
            return;

        int i0 = Mth.clamp((int) Math.floor(tail * (strip.count - 1)), 0, strip.count - 2);
        int i1 = Mth.clamp((int) Math.ceil(head * (strip.count - 1)), i0 + 1, strip.count - 1);
        int span = Math.max(1, i1 - i0);
        LunarPalette pal = strip.palette;

        for (int i = i0; i < i1; i++) {
            float u0 = (i - i0) / (float) span;
            float u1 = (i + 1 - i0) / (float) span;
            float t0 = (float) Math.pow(Math.max(0.0F, Mth.sin(u0 * Mth.PI)), 0.60);
            float t1 = (float) Math.pow(Math.max(0.0F, Mth.sin(u1 * Mth.PI)), 0.60);

            float bx0 = Mth.lerp(1.0F - t0, strip.bx[i], strip.tx[i]);
            float by0 = Mth.lerp(1.0F - t0, strip.by[i], strip.ty[i]);
            float bz0 = Mth.lerp(1.0F - t0, strip.bz[i], strip.tz[i]);
            float tx0 = strip.tx[i], ty0 = strip.ty[i], tz0 = strip.tz[i];

            float bx1 = Mth.lerp(1.0F - t1, strip.bx[i + 1], strip.tx[i + 1]);
            float by1 = Mth.lerp(1.0F - t1, strip.by[i + 1], strip.ty[i + 1]);
            float bz1 = Mth.lerp(1.0F - t1, strip.bz[i + 1], strip.tz[i + 1]);
            float tx1 = strip.tx[i + 1], ty1 = strip.ty[i + 1], tz1 = strip.tz[i + 1];

            float f0 = globalFade * (0.36F + 0.64F * t0);
            float f1 = globalFade * (0.36F + 0.64F * t1);
            int aMid0 = Mth.clamp(Math.round(f0 * 246.0F), 0, 255);
            int aMid1 = Mth.clamp(Math.round(f1 * 246.0F), 0, 255);
            int aCore0 = Mth.clamp(Math.round(f0 * 255.0F), 0, 255);
            int aCore1 = Mth.clamp(Math.round(f1 * 255.0F), 0, 255);

            if (!glowPass) {
                float mx0 = Mth.lerp(0.54F, bx0, tx0), my0 = Mth.lerp(0.54F, by0, ty0), mz0 = Mth.lerp(0.54F, bz0, tz0);
                float mx1 = Mth.lerp(0.54F, bx1, tx1), my1 = Mth.lerp(0.54F, by1, ty1), mz1 = Mth.lerp(0.54F, bz1, tz1);
                int aTail0 = Mth.clamp(Math.round(f0 * 170.0F), 0, 255);
                int aTail1 = Mth.clamp(Math.round(f1 * 170.0F), 0, 255);
                emitQuad(vc, pose,
                        bx0, by0, bz0, u0, 0.04F, pal.tailR, pal.tailG, pal.tailB, aTail0,
                        bx1, by1, bz1, u1, 0.04F, pal.tailR, pal.tailG, pal.tailB, aTail1,
                        mx1, my1, mz1, u1, 0.56F, pal.midR, pal.midG, pal.midB, aMid1,
                        mx0, my0, mz0, u0, 0.56F, pal.midR, pal.midG, pal.midB, aMid0);
                emitQuad(vc, pose,
                        mx0, my0, mz0, u0, 0.56F, pal.midR, pal.midG, pal.midB, aMid0,
                        mx1, my1, mz1, u1, 0.56F, pal.midR, pal.midG, pal.midB, aMid1,
                        tx1, ty1, tz1, u1, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore1,
                        tx0, ty0, tz0, u0, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore0);
            } else {
                float cx0 = Mth.lerp(0.74F, bx0, tx0), cy0 = Mth.lerp(0.74F, by0, ty0), cz0 = Mth.lerp(0.74F, bz0, tz0);
                float cx1 = Mth.lerp(0.74F, bx1, tx1), cy1 = Mth.lerp(0.74F, by1, ty1), cz1 = Mth.lerp(0.74F, bz1, tz1);
                emitQuad(vc, pose,
                        cx0, cy0 + 0.015F, cz0, u0, 0.74F, pal.midR, pal.midG, pal.midB, aMid0,
                        cx1, cy1 + 0.015F, cz1, u1, 0.74F, pal.midR, pal.midG, pal.midB, aMid1,
                        tx1, ty1 + 0.025F, tz1, u1, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore1,
                        tx0, ty0 + 0.025F, tz0, u0, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore0);
            }
        }
    }

    private static void renderSpark(PoseStack poseStack, VertexConsumer vc, Camera camera, Vec3 camPos,
            LunarSpark sp, float t) {
        if (sp.ambientStar) {
            double px = sp.ox + sp.vx * t * 2.2;
            double py = sp.oy + sp.vy * t * 2.6;
            double pz = sp.oz + sp.vz * t * 2.2;
            float twinkle = Mth.sin(t * Mth.PI);
            float s = sp.size * (0.45F + 0.70F * twinkle);
            int a = Mth.clamp(Math.round(twinkle * 250.0F), 0, 255);
            if (s <= 0.004F || a <= 4) {
                return;
            }
            float w = s * 0.27F;
            poseStack.pushPose();
            poseStack.translate(px - camPos.x, py - camPos.y, pz - camPos.z);
            poseStack.mulPose(camera.rotation());
            PoseStack.Pose pose = poseStack.last();
            // Đốm sao 4 cánh phát sáng sắc nét (tâm trắng lóa + 4 tia sao mảnh, không có ô
            // vuông/thoi thừa) cho cả Song Đao & Song Nguyệt!
            emitQuad(vc, pose,
                    0.0F, -s, 0.0F, 0.50F, 0.15F, sp.r, sp.g, sp.b, a,
                    w, 0.0F, 0.0F, 0.85F, 0.50F, sp.r, sp.g, sp.b, a,
                    0.0F, s, 0.0F, 0.50F, 0.85F, sp.r, sp.g, sp.b, a,
                    -w, 0.0F, 0.0F, 0.15F, 0.50F, sp.r, sp.g, sp.b, a);
            emitQuad(vc, pose,
                    -s, 0.0F, 0.0F, 0.15F, 0.50F, sp.r, sp.g, sp.b, a,
                    0.0F, -w, 0.0F, 0.50F, 0.15F, sp.r, sp.g, sp.b, a,
                    s, 0.0F, 0.0F, 0.85F, 0.50F, sp.r, sp.g, sp.b, a,
                    0.0F, w, 0.0F, 0.50F, 0.85F, sp.r, sp.g, sp.b, a);
            float core = s * 0.34F;
            int coreR = Math.min(255, sp.r + 85);
            int coreG = Math.min(255, sp.g + 85);
            int coreB = Math.min(255, sp.b + 85);
            emitQuad(vc, pose,
                    -core, -core, 0.002F, 0.30F, 0.30F, coreR, coreG, coreB, a,
                    core, -core, 0.002F, 0.70F, 0.30F, coreR, coreG, coreB, a,
                    core, core, 0.002F, 0.70F, 0.70F, coreR, coreG, coreB, a,
                    -core, core, 0.002F, 0.30F, 0.70F, coreR, coreG, coreB, a);
            poseStack.popPose();
            return;
        }
        float ease = DualBladeAnimState.easeOutCubic(t);
        double px = sp.ox + sp.vx * ease * 3.0;
        double py = sp.oy + sp.vy * ease * 3.0 - 0.14 * t * t;
        double pz = sp.oz + sp.vz * ease * 3.0;
        float s = sp.size * (1.0F - t * 0.58F);
        int a = Mth.clamp(Math.round((1.0F - t) * 250.0F), 0, 255);
        if (s <= 0.005F || a <= 4)
            return;
        poseStack.pushPose();
        poseStack.translate(px - camPos.x, py - camPos.y, pz - camPos.z);
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F + t * 90.0F));
        emitQuad(vc, poseStack.last(),
                -s, -s, 0.0F, 0.25F, 0.25F, sp.r, sp.g, sp.b, a,
                s, -s, 0.0F, 0.75F, 0.25F, sp.r, sp.g, sp.b, a,
                s, s, 0.0F, 0.75F, 0.75F, sp.r, sp.g, sp.b, a,
                -s, s, 0.0F, 0.25F, 0.75F, sp.r, sp.g, sp.b, a);
        poseStack.popPose();
    }

    private static void emitQuad(VertexConsumer vc, PoseStack.Pose pose,
            float x0, float y0, float z0, float u0, float v0, int r0, int g0, int b0, int a0,
            float x1, float y1, float z1, float u1, float v1, int r1, int g1, int b1, int a1,
            float x2, float y2, float z2, float u2, float v2, int r2, int g2, int b2, int a2,
            float x3, float y3, float z3, float u3, float v3, int r3, int g3, int b3, int a3) {
        VfxRenderHelper.addVertex(vc, pose, x0, y0, z0, r0, g0, b0, a0, u0, v0, 0.0F, 1.0F, 0.0F);
        VfxRenderHelper.addVertex(vc, pose, x1, y1, z1, r1, g1, b1, a1, u1, v1, 0.0F, 1.0F, 0.0F);
        VfxRenderHelper.addVertex(vc, pose, x2, y2, z2, r2, g2, b2, a2, u2, v2, 0.0F, 1.0F, 0.0F);
        VfxRenderHelper.addVertex(vc, pose, x3, y3, z3, r3, g3, b3, a3, u3, v3, 0.0F, 1.0F, 0.0F);
    }
}
