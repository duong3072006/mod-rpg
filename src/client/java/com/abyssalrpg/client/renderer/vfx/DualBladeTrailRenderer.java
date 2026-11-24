package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.DualBladeAnimState;
import com.abyssalrpg.combat.DualBladeMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.network.DualBladeDashVfxPayload;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Vệt Kiếm Thư Pháp Song Sắc Đỏ Huyết – Bạc Lam Lạnh (`DualBladeTrailRenderer` v6 — NO_CULL, Fullbright):
 * - Đồng bộ 100% với cặp Song Dao `Dual Knife`:
 *   + Tay Trái (`Offhand` — Lưỡi Đen–Bạc Móng Vuốt Đồng, `DIAMOND_BLUE`): lõi `#FFFFFF` -> **Bạc Lam Lạnh `#9EE8FF` / `#4A98D8`** -> đuôi **Đen Thép `#161B26`**.
 *   + Tay Phải (`Mainhand` — Lưỡi Đỏ–Đen Răng Sóng, `CRIMSON_BLOOD`): lõi `#FFFFFF` -> **Đỏ Huyết `#FF2236`** -> đuôi **Đen `#2B060B`**.
 * - Chém 2 tay / Xoay 360° / Chữ X / Đường lướt **15 block** & **10 nhát chém** bùng nổ:
 *   2 dải Đỏ Huyết – Đen và Bạc Lam – Đen đan xen song song nhau nổi bật (`NO_CULL` + `FULL_BRIGHT`), đồng bộ kích thước mới (`0.92`).
 */
public final class DualBladeTrailRenderer {
    public static final ResourceLocation SLASH_ARC_TEX =
            AbyssalRpgMod.id("textures/vfx/dual_knife_slash_arc.png");

    /** Hai bảng màu chuẩn Song Dao: Bạc Lam Lạnh – Đen Thép (Tay Trái) & Đỏ Huyết – Đen (Tay Phải). */
    public enum RibbonPalette {
        DIAMOND_BLUE (22, 27, 38,  118, 206, 245,  255, 255, 255),  // #161B26 -> Bạc Lam Lạnh (#9EE8FF/#4A98D8) -> #FFFFFF
        CRIMSON_BLOOD(43,  6, 11,  255,  34,  54,  255, 255, 255);  // #2B060B -> #FF2236 -> #FFFFFF

        final int tailR, tailG, tailB;
        final int midR, midG, midB;
        final int coreR, coreG, coreB;

        RibbonPalette(int tailR, int tailG, int tailB, int midR, int midG, int midB,
                      int coreR, int coreG, int coreB) {
            this.tailR = tailR;
            this.tailG = tailG;
            this.tailB = tailB;
            this.midR = midR;
            this.midG = midG;
            this.midB = midB;
            this.coreR = coreR;
            this.coreG = coreG;
            this.coreB = coreB;
        }
    }

    private static final int MAX_ACTIVE_TRAILS = 32;
    private static final int MAX_ACTIVE_WORLD_RIBBONS = 16;
    private static final int MAX_ACTIVE_FLURRY_CUTS = 180;
    private static final int MAX_ACTIVE_SPARKS = 80;
    private static final int CATMULL_SUBDIVS = 4;

    private static final List<MoveTrail> TRAILS = new ArrayList<>();
    private static final List<WorldDashRibbon> DASH_RIBBONS = new ArrayList<>();
    private static final List<TargetSlashCut> FLURRY_CUTS = new ArrayList<>();
    private static final List<ImpactSpark> SPARKS = new ArrayList<>();

    private DualBladeTrailRenderer() {}

    /** RenderType chuyên biệt `NO_CULL` + `FULL_BRIGHT` + `COLOR_WRITE` hiển thị rực rõ cả ngày lẫn đêm. */
    private static final class RibbonRenderTypes extends RenderType {
        private static final RenderType RIBBON_BODY = RenderType.create(
                "abyssalrpg_dual_blade_ribbon_body",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(SLASH_ARC_TEX, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false)
        );

        private static final RenderType RIBBON_GLOW = RenderType.create(
                "abyssalrpg_dual_blade_ribbon_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(SLASH_ARC_TEX, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false)
        );

        private static final RenderType SPARK_GLOW = RenderType.create(
                "abyssalrpg_dual_blade_spark_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                768,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(VfxRenderHelper.ENERGY_SLASH_TEX, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false)
        );

        private RibbonRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int buf,
                                  boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, fmt, mode, buf, crumble, sort, setup, clear);
        }
    }

    private static final class BladeKnot {
        final float bx, by, bz;
        final float tx, ty, tz;

        BladeKnot(float bx, float by, float bz, float tx, float ty, float tz) {
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
        }
    }

    private static final class RibbonStrip {
        final float[] bx, by, bz;
        final float[] tx, ty, tz;
        final int count;
        final float startDelayFrac;
        final float sweepSpeedFrac;
        final RibbonPalette palette;

        RibbonStrip(List<BladeKnot> controlKnots, float startDelayFrac, float sweepSpeedFrac, RibbonPalette palette) {
            int n = controlKnots.size();
            int total = Math.max(2, (n - 1) * CATMULL_SUBDIVS + 1);
            this.bx = new float[total];
            this.by = new float[total];
            this.bz = new float[total];
            this.tx = new float[total];
            this.ty = new float[total];
            this.tz = new float[total];
            this.count = total;
            this.startDelayFrac = startDelayFrac;
            this.sweepSpeedFrac = sweepSpeedFrac;
            this.palette = palette;

            int idx = 0;
            for (int i = 0; i < n - 1; i++) {
                BladeKnot p0 = controlKnots.get(Math.max(0, i - 1));
                BladeKnot p1 = controlKnots.get(i);
                BladeKnot p2 = controlKnots.get(i + 1);
                BladeKnot p3 = controlKnots.get(Math.min(n - 1, i + 2));

                int steps = (i == n - 2) ? CATMULL_SUBDIVS + 1 : CATMULL_SUBDIVS;
                for (int s = 0; s < steps; s++) {
                    float t = s / (float) CATMULL_SUBDIVS;
                    this.bx[idx] = catmullRom(p0.bx, p1.bx, p2.bx, p3.bx, t);
                    this.by[idx] = catmullRom(p0.by, p1.by, p2.by, p3.by, t);
                    this.bz[idx] = catmullRom(p0.bz, p1.bz, p2.bz, p3.bz, t);
                    this.tx[idx] = catmullRom(p0.tx, p1.tx, p2.tx, p3.tx, t);
                    this.ty[idx] = catmullRom(p0.ty, p1.ty, p2.ty, p3.ty, t);
                    this.tz[idx] = catmullRom(p0.tz, p1.tz, p2.tz, p3.tz, t);
                    idx++;
                }
            }
        }
    }

    private static final class MoveTrail {
        final int attackerId;
        final DualBladeMoveType moveType;
        final double anchorX, anchorY, anchorZ;
        final float yawDeg, pitchDeg;
        final int powerTier;
        final long startTick;
        final float durationTicks;
        final List<RibbonStrip> strips;

        MoveTrail(int attackerId, DualBladeMoveType moveType, Vec3 anchor, float yawDeg, float pitchDeg,
                  int powerTier, long startTick, float durationTicks, List<RibbonStrip> strips) {
            this.attackerId = attackerId;
            this.moveType = moveType;
            this.anchorX = anchor.x;
            this.anchorY = anchor.y;
            this.anchorZ = anchor.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.powerTier = powerTier;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.strips = strips;
        }
    }

    /** 2 dải ribbon Xanh Kim Cương & Netherite–Lam song song kéo dài trọn hành lang lướt 18 block trong không gian thế giới. */
    private static final class WorldDashRibbon {
        final double originX, originY, originZ;
        final RibbonStrip rightDiamondStrip;
        final RibbonStrip leftNetheriteStrip;
        final long startTick;
        final float durationTicks;

        WorldDashRibbon(Vec3 origin, RibbonStrip rightDiamondStrip, RibbonStrip leftNetheriteStrip, long startTick, float durationTicks) {
            this.originX = origin.x;
            this.originY = origin.y;
            this.originZ = origin.z;
            this.rightDiamondStrip = rightDiamondStrip;
            this.leftNetheriteStrip = leftNetheriteStrip;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** 1 trong 10 vệt chém chéo sắc lẹm Đỏ–Đen / Vàng–Đen cắt ngang thân mục tiêu của chiêu Lướt Chém (mờ dần 8 tick). */
    private static final class TargetSlashCut {
        final double cx, cy, cz;
        final float yawDeg, pitchDeg, rollDeg;
        final float halfLength, halfWidth;
        final RibbonPalette palette;
        final float startTickF;
        final float durationTicks;

        TargetSlashCut(double cx, double cy, double cz, float yawDeg, float pitchDeg, float rollDeg,
                       float halfLength, float halfWidth, RibbonPalette palette, float startTickF, float durationTicks) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.halfLength = halfLength;
            this.halfWidth = halfWidth;
            this.palette = palette;
            this.startTickF = startTickF;
            this.durationTicks = durationTicks;
        }
    }

    private static final class ImpactSpark {
        final double ox, oy, oz;
        final float vx, vy, vz;
        final float size;
        final int r, g, b;
        final long startTick;
        final float durationTicks;

        ImpactSpark(double ox, double oy, double oz, float vx, float vy, float vz,
                    float size, int r, int g, int b, long startTick, float durationTicks) {
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
        }
    }

    public static void clear() {
        TRAILS.clear();
        DASH_RIBBONS.clear();
        FLURRY_CUTS.clear();
        SPARKS.clear();
    }

    public static void spawnMoveTrail(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg,
                                      DualBladeMoveType moveType, float animDurationTicks,
                                      int powerTier, long clientTick) {
        WeaponsConfig cfg = WeaponsConfig.get();
        if (!cfg.enableSlashRibbon || !cfg.enableSlashVfx) {
            return;
        }
        for (int i = TRAILS.size() - 1; i >= 0; i--) {
            MoveTrail ex = TRAILS.get(i);
            if (ex.attackerId == attackerId && ex.moveType == moveType && (clientTick - ex.startTick) <= 2L) {
                return;
            }
        }

        List<RibbonStrip> strips = buildNightfallStrips(moveType);
        if (strips.isEmpty()) {
            return;
        }
        float dur = moveType.isHeavyFinisherOrCyclone()
                ? Mth.clamp(animDurationTicks * 0.66F, 8.6F, 9.2F)
                : Mth.clamp(animDurationTicks * 0.68F, 7.4F, 8.2F);

        if (TRAILS.size() >= MAX_ACTIVE_TRAILS) {
            TRAILS.remove(0);
        }
        TRAILS.add(new MoveTrail(attackerId, moveType, anchor, yawDeg, pitchDeg, powerTier, clientTick, dur, strips));
    }

    /**
     * Dựng toàn bộ hiệu ứng chiêu Chuột Phải Flash Dash từ 1 gói duy nhất:
     * 1. 2 dải lụa song song Xanh Kim Cương (tay phải thuận) & Netherite–Lam (tay trái ngược) kéo trọn 18 block `(start -> end)`.
     * 2. 10 vệt chém chéo sắc lạnh Xanh Kim Cương & Netherite Tối cắt đan xen trên thân từng mục tiêu (mờ dần trong 8 tick).
     */
    public static void spawnFlashDashTrailAndFlurry(DualBladeDashVfxPayload p, long clientTick) {
        WeaponsConfig cfg = WeaponsConfig.get();
        if (!cfg.enableSlashRibbon && !cfg.enableSlashVfx) {
            return;
        }

        Vec3 start = new Vec3(p.startX(), p.startY(), p.startZ());
        Vec3 end = new Vec3(p.endX(), p.endY(), p.endZ());
        Vec3 delta = end.subtract(start);
        double dist = delta.length();
        if (dist > 0.35) {
            Vec3 fwd = delta.scale(1.0 / dist);
            Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
            if (right.lengthSqr() < 1e-4) {
                right = new Vec3(1.0, 0.0, 0.0);
            } else {
                right = right.normalize();
            }

            // 24 control knots (93 đoạn Catmull-Rom mượt) trải đều trọn hành lang 15 block!
            int knots = Mth.clamp((int) Math.round(dist * 1.60), 16, 26);
            List<BladeKnot> rightKnots = new ArrayList<>(knots);
            List<BladeKnot> leftKnots = new ArrayList<>(knots);
            for (int i = 0; i < knots; i++) {
                float u = i / (float) (knots - 1);
                Vec3 pt = delta.scale(u);
                float swirl = Mth.sin(u * Mth.PI * 3.0F) * 0.20F;
                // Dải Đỏ Huyết – Đen (dao phải Lưỡi Sóng Đỏ Huyết — đồng bộ kích thước mới 0.92)
                double rOffX = right.x * 0.58, rOffZ = right.z * 0.58;
                rightKnots.add(new BladeKnot(
                        (float) (pt.x + rOffX * 0.34), (float) (pt.y - 0.28 + swirl), (float) (pt.z + rOffZ * 0.34),
                        (float) (pt.x + rOffX * 1.62), (float) (pt.y + 0.42 + swirl), (float) (pt.z + rOffZ * 1.62)
                ));
                // Dải Xanh Kim Cương – Đen (dao trái Nanh Xanh Kim Cương) chạy song song bên trái đối pha
                double lOffX = -right.x * 0.58, lOffZ = -right.z * 0.58;
                leftKnots.add(new BladeKnot(
                        (float) (pt.x + lOffX * 0.34), (float) (pt.y + 0.28 - swirl), (float) (pt.z + lOffZ * 0.34),
                        (float) (pt.x + lOffX * 1.62), (float) (pt.y - 0.42 - swirl), (float) (pt.z + lOffZ * 1.62)
                ));
            }

            if (DASH_RIBBONS.size() >= MAX_ACTIVE_WORLD_RIBBONS) {
                DASH_RIBBONS.remove(0);
            }
            DASH_RIBBONS.add(new WorldDashRibbon(
                    start,
                    new RibbonStrip(rightKnots, 0.0F, 0.26F, RibbonPalette.CRIMSON_BLOOD),
                    new RibbonStrip(leftKnots, 0.0F, 0.26F, RibbonPalette.DIAMOND_BLUE),
                    clientTick,
                    11.2F
            ));
        }

        // Bùng nổ 10 vệt chém chéo song sắc Đỏ Huyết – Đen & Xanh Kim Cương – Đen đan xen ngay trên thân từng mục tiêu (mờ dần trong 8 tick!)
        Minecraft mc = Minecraft.getInstance();
        RandomSource rng = mc.level != null ? mc.level.random : RandomSource.create();
        int slashCount = Math.max(1, cfg.dashHitCount);
        for (Vec3 targetPos : p.targetCenters()) {
            for (int i = 0; i < slashCount; i++) {
                RibbonPalette pal = (i % 2 == 0) ? RibbonPalette.CRIMSON_BLOOD : RibbonPalette.DIAMOND_BLUE;
                float yaw = p.yawDeg() + (i * 37.0F) + (rng.nextFloat() - 0.5F) * 28.0F;
                float pitch = -42.0F + (i % 5) * 22.0F + (rng.nextFloat() - 0.5F) * 14.0F;
                float roll = ((i % 2 == 0) ? 38.0F : -38.0F) + (rng.nextFloat() - 0.5F) * 24.0F;
                double ox = (rng.nextDouble() - 0.5) * 0.55;
                double oy = (rng.nextDouble() - 0.5) * 0.65;
                double oz = (rng.nextDouble() - 0.5) * 0.55;
                float delayTicks = (i * 0.48F); // Trải 10 nhát gọn trong ~4.8 tick cực đã mắt!
                if (FLURRY_CUTS.size() >= MAX_ACTIVE_FLURRY_CUTS) {
                    FLURRY_CUTS.remove(0);
                }
                FLURRY_CUTS.add(new TargetSlashCut(
                        targetPos.x + ox, targetPos.y + oy, targetPos.z + oz,
                        yaw, pitch, roll,
                        1.92F + rng.nextFloat() * 0.58F,
                        0.28F + rng.nextFloat() * 0.10F,
                        pal,
                        clientTick + delayTicks,
                        8.0F
                ));
            }
            spawnImpactSparks(targetPos, clientTick);
        }
    }

    /** Tia lửa va chạm song sắc Đỏ Huyết `#FF2236` & Xanh Kim Cương `#3EE8FA` tại điểm chạm. */
    public static void spawnImpactSparks(Vec3 hitPos, long clientTick) {
        WeaponsConfig cfg = WeaponsConfig.get();
        if (!cfg.enableSlashRibbon && !cfg.enableSlashVfx) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RandomSource rng = mc.level != null ? mc.level.random : RandomSource.create();
        int count = 6 + rng.nextInt(3);
        for (int i = 0; i < count; i++) {
            double theta = rng.nextDouble() * Math.PI * 2.0;
            double phi = (rng.nextDouble() - 0.35) * Math.PI * 0.75;
            float speed = 0.24F + rng.nextFloat() * 0.34F;
            float vx = (float) (Math.cos(theta) * Math.cos(phi) * speed);
            float vy = (float) (Math.sin(phi) * speed + 0.08);
            float vz = (float) (Math.sin(theta) * Math.cos(phi) * speed);
            boolean crimson = (i % 2 == 0);
            int r = crimson ? 255 : 62;
            int g = crimson ? 56 : 232;
            int b = crimson ? 72 : 250;
            float size = 0.065F + rng.nextFloat() * 0.045F;
            if (SPARKS.size() >= MAX_ACTIVE_SPARKS) {
                SPARKS.remove(0);
            }
            SPARKS.add(new ImpactSpark(hitPos.x, hitPos.y, hitPos.z, vx, vy, vz, size, r, g, b, clientTick, 5.2F));
        }
    }

    /** Tia sáng Chí Mạng song sắc Trắng Sáng `#FFFFFF`, Đỏ Huyết `#FF2236` & Xanh Kim Cương `#3EE8FA`. */
    public static void spawnCritBurst(Vec3 hitPos, long clientTick) {
        WeaponsConfig cfg = WeaponsConfig.get();
        if (!cfg.enableSlashRibbon && !cfg.enableSlashVfx) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RandomSource rng = mc.level != null ? mc.level.random : RandomSource.create();
        int count = 10;
        for (int i = 0; i < count; i++) {
            double theta = (Math.PI * 2.0 * i) / count + (rng.nextDouble() - 0.5) * 0.25;
            double phi = (rng.nextDouble() - 0.30) * Math.PI * 0.65;
            float speed = 0.30F + rng.nextFloat() * 0.34F;
            float vx = (float) (Math.cos(theta) * Math.cos(phi) * speed);
            float vy = (float) (Math.sin(phi) * speed + 0.12);
            float vz = (float) (Math.sin(theta) * Math.cos(phi) * speed);
            int lane = i % 3;
            int r = (lane == 0) ? 255 : (lane == 1 ? 255 : 62);
            int g = (lane == 0) ? 250 : (lane == 1 ? 42 : 232);
            int b = (lane == 0) ? 252 : (lane == 1 ? 64 : 250);
            float size = 0.095F + rng.nextFloat() * 0.055F;
            if (SPARKS.size() >= MAX_ACTIVE_SPARKS) {
                SPARKS.remove(0);
            }
            SPARKS.add(new ImpactSpark(hitPos.x, hitPos.y, hitPos.z, vx, vy, vz, size, r, g, b, clientTick, 6.2F));
        }
    }

    /**
     * Dựng dải lụa Đỏ Huyết – Đen (Tay Phải) & Xanh Kim Cương – Đen (Tay Trái)
     * đồng bộ chính xác theo kích thước mới của `Dual Knife` (`scale = 0.92`, ~1.02 block) và tự scale theo `WeaponsConfig.knifeRenderScale`!
     */
    private static List<RibbonStrip> buildNightfallStrips(DualBladeMoveType move) {
        List<RibbonStrip> list = new ArrayList<>(2);
        switch (move) {
            case COMBO_1 -> {
                // Nhịp 1 (Tay Phải — Lưỡi Sóng Đỏ Huyết): chém chéo rộng bám khít gốc–mũi dao mới!
                list.add(sampleArcStrip(20, -118.0F, 112.0F, 0.44F, 1.92F, 0.44F, -0.32F, 0.12F, 0.0F, 0.34F, RibbonPalette.CRIMSON_BLOOD));
            }
            case COMBO_2 -> {
                // Nhịp 2 (Tay Trái — Nanh Xanh Kim Cương): quét backhand tầng cao bám khít mũi dao!
                list.add(sampleReverseLeftArcStrip(20, -68.0F, 142.0F, 0.42F, 1.84F, 0.38F, 0.14F, 0.18F, 0.0F, 0.34F, RibbonPalette.DIAMOND_BLUE));
            }
            case COMBO_3 -> {
                // Nhịp 3 (2 tầng song sắc ngược chiều): Tay Trái Xanh Kim Cương tầng trên + Tay Phải Đỏ Huyết tầng dưới đan xen!
                list.add(sampleReverseLeftArcStrip(22, -88.0F, 138.0F, 0.43F, 1.94F, 0.44F, 0.64F, 0.20F, 0.0F, 0.36F, RibbonPalette.DIAMOND_BLUE));
                list.add(sampleArcStrip(22, 116.0F, -128.0F, 0.44F, 1.94F, 0.10F, -0.42F, -0.15F, 0.04F, 0.36F, RibbonPalette.CRIMSON_BLOOD));
            }
            case COMBO_4 -> {
                // Nhịp 4 (360° Cyclone): 2 vòng tròn 360° Đỏ Huyết (dưới) & Xanh Kim Cương (trên) quấn xoáy song song!
                float outerR = (float) Mth.clamp(WeaponsConfig.get().cycloneRadius * 0.66, 2.00, 2.85);
                list.add(sampleArcStrip(30, -180.0F, 195.0F, outerR * 0.28F, outerR, -0.14F, -0.14F, 0.05F, 0.0F, 0.48F, RibbonPalette.CRIMSON_BLOOD));
                list.add(sampleReverseLeftArcStrip(30, -15.0F, 360.0F, outerR * 0.26F, outerR * 0.92F, 0.28F, 0.22F, -0.08F, 0.02F, 0.48F, RibbonPalette.DIAMOND_BLUE));
            }
            case COMBO_5 -> {
                // Nhịp 5 Finisher (Cross-X Slam): chữ X song sắc Đỏ Huyết + Xanh Kim Cương đan chéo rực sáng!
                list.add(sampleDiagonalSlamStrip(20, -1.0F, 2.08F, 0.08F, 0.36F, RibbonPalette.CRIMSON_BLOOD));
                list.add(sampleReverseDiagonalRendStrip(20, 2.02F, 0.12F, 0.36F, RibbonPalette.DIAMOND_BLUE));
            }
            case SPRINT_THRUST -> {
                // Sprint Opening Cross-Slash: 2 vòng cung chém chéo lệch pha bám sát tay phải (trước) & tay trái (sau 0.12 pha)!
                list.add(sampleArcStrip(20, -108.0F, 102.0F, 0.42F, 1.88F, 0.32F, -0.26F, 0.10F, 0.0F, 0.34F, RibbonPalette.CRIMSON_BLOOD));
                list.add(sampleReverseLeftArcStrip(20, -64.0F, 128.0F, 0.40F, 1.84F, 0.24F, 0.04F, 0.14F, 0.12F, 0.34F, RibbonPalette.DIAMOND_BLUE));
            }
            case AIR_SLASH -> {
                list.add(sampleArcStrip(20, -108.0F, 112.0F, 0.44F, 1.90F, -0.30F, 0.50F, 0.18F, 0.0F, 0.36F, RibbonPalette.CRIMSON_BLOOD));
                list.add(sampleReverseLeftArcStrip(20, 114.0F, -108.0F, 0.42F, 1.84F, -0.16F, 0.58F, -0.14F, 0.05F, 0.36F, RibbonPalette.DIAMOND_BLUE));
            }
            case SKILL_DASH -> {
                list.add(sampleArcStrip(20, -120.0F, 120.0F, 0.44F, 1.92F, -0.14F, -0.10F, 0.04F, 0.0F, 0.34F, RibbonPalette.CRIMSON_BLOOD));
                list.add(sampleReverseLeftArcStrip(20, 126.0F, -114.0F, 0.42F, 1.84F, 0.22F, 0.18F, -0.04F, 0.03F, 0.34F, RibbonPalette.DIAMOND_BLUE));
            }
            case FLASH_DASH_END -> {
                // Cú hãm xoay chữ X song sắc Đỏ Huyết + Xanh Kim Cương kết thúc cú lướt 15 block!
                list.add(sampleDiagonalSlamStrip(18, -1.0F, 1.98F, 0.0F, 0.34F, RibbonPalette.CRIMSON_BLOOD));
                list.add(sampleReverseDiagonalRendStrip(18, 1.92F, 0.04F, 0.34F, RibbonPalette.DIAMOND_BLUE));
            }
            case FLASH_DASH_CHARGE, FLASH_DASH_LAUNCH -> {
                // Pha 1 & 2 dùng WorldDashRibbon 15 block riêng
            }
        }
        return list;
    }

    private static RibbonStrip sampleArcStrip(int knots, float startDeg, float endDeg,
                                              float baseR, float tipR,
                                              float startY, float endY, float midArchY,
                                              float delayFrac, float speedFrac, RibbonPalette palette) {
        List<BladeKnot> list = new ArrayList<>(knots);
        for (int i = 0; i < knots; i++) {
            float u = i / (float) (knots - 1);
            float ang = Mth.lerp(u, startDeg, endDeg) * Mth.DEG_TO_RAD;
            float s = Mth.sin(ang);
            float c = Mth.cos(ang);
            float yTip = Mth.lerp(u, startY, endY) + Mth.sin(u * Mth.PI) * midArchY;
            float yBase = yTip * 0.40F;
            list.add(new BladeKnot(s * baseR, yBase, c * baseR, s * tipR, yTip, c * tipR));
        }
        return new RibbonStrip(list, delayFrac, speedFrac, palette);
    }

    /**
     * Tọa độ đầu/đuôi ribbon chuyên biệt cho **Tay Trái Cầm Ngược (Reverse Grip)**:
     * - Lưỡi kéo ngược theo mặt ngoài cẳng tay trái nên tâm quét dịch về phía vai-khuỷu trái (`+0.26` X, `-0.18` Z),
     *   gốc ribbon bám sát cổ tay/sống lưỡi ngược (`baseAng` sớm pha `14°` so với mũi lưỡi kéo theo sau) để vệt sáng
     *   tuôn mượt từ đúng mép lưỡi cầm ngược chứ KHÔNG bị lệch sang đầu chuôi!
     */
    private static RibbonStrip sampleReverseLeftArcStrip(int knots, float startDeg, float endDeg,
                                                         float baseR, float tipR,
                                                         float startY, float endY, float midArchY,
                                                         float delayFrac, float speedFrac, RibbonPalette palette) {
        List<BladeKnot> list = new ArrayList<>(knots);
        float dirSign = (endDeg >= startDeg) ? 1.0F : -1.0F;
        float lagRad = -dirSign * 14.0F * Mth.DEG_TO_RAD; // Mũi lưỡi ngược quét trễ pha theo cẳng tay 14° cực tự nhiên!
        for (int i = 0; i < knots; i++) {
            float u = i / (float) (knots - 1);
            float baseAng = Mth.lerp(u, startDeg, endDeg) * Mth.DEG_TO_RAD;
            float tipAng = baseAng + lagRad;
            float yTip = Mth.lerp(u, startY, endY) + Mth.sin(u * Mth.PI) * midArchY;
            float yBase = yTip * 0.56F + 0.06F;
            float bx = Mth.sin(baseAng) * baseR + 0.22F;
            float bz = Mth.cos(baseAng) * baseR - 0.14F;
            float tx = Mth.sin(tipAng) * tipR + 0.22F;
            float tz = Mth.cos(tipAng) * tipR - 0.14F;
            list.add(new BladeKnot(bx, yBase, bz, tx, yTip, tz));
        }
        return new RibbonStrip(list, delayFrac, speedFrac, palette);
    }

    private static RibbonStrip sampleReverseDiagonalRendStrip(int knots, float reach,
                                                              float delayFrac, float speedFrac, RibbonPalette palette) {
        List<BladeKnot> list = new ArrayList<>(knots);
        for (int i = 0; i < knots; i++) {
            float u = i / (float) (knots - 1);
            float ang = Mth.lerp(u, -76.0F, 82.0F) * Mth.DEG_TO_RAD;
            float fwd = Mth.cos(ang);
            float swing = Mth.sin(ang);
            float tilt = 36.0F * Mth.DEG_TO_RAD;
            // Xé chéo ngược từ vai-trái ra ngoài-phải khớp cánh tay trái Reverse Grip!
            float lx = (-swing * Mth.cos(tilt)) + 0.12F;
            float ly = (-swing * Mth.sin(tilt)) + 0.26F;
            float lz = fwd * 0.92F + 0.36F;
            list.add(new BladeKnot(lx * (reach * 0.22F) + 0.16F, ly * (reach * 0.26F) + 0.06F, lz * (reach * 0.22F) - 0.10F,
                    lx * reach, ly * reach, lz * reach));
        }
        return new RibbonStrip(list, delayFrac, speedFrac, palette);
    }

    private static RibbonStrip sampleDiagonalSlamStrip(int knots, float sideSign, float reach,
                                                       float delayFrac, float speedFrac, RibbonPalette palette) {
        List<BladeKnot> list = new ArrayList<>(knots);
        for (int i = 0; i < knots; i++) {
            float u = i / (float) (knots - 1);
            float ang = Mth.lerp(u, -82.0F, 80.0F) * Mth.DEG_TO_RAD;
            float fwd = Mth.cos(ang);
            float swing = Mth.sin(ang);
            float tilt = 42.0F * Mth.DEG_TO_RAD;
            float lx = sideSign * (-swing * Mth.cos(tilt));
            float ly = -swing * Mth.sin(tilt) + 0.22F;
            float lz = fwd * 0.96F + 0.44F;
            list.add(new BladeKnot(lx * (reach * 0.24F), ly * (reach * 0.24F), lz * (reach * 0.24F),
                    lx * reach, ly * reach, lz * reach));
        }
        return new RibbonStrip(list, delayFrac, speedFrac, palette);
    }

    private static RibbonStrip sampleThrustBeamStrip(int knots, float sideOffsetX, float yOffset,
                                                     float length, float delayFrac, float speedFrac, RibbonPalette palette) {
        List<BladeKnot> list = new ArrayList<>(knots);
        for (int i = 0; i < knots; i++) {
            float u = i / (float) (knots - 1);
            float z = 0.30F + u * length;
            float spread = 0.36F * (1.0F - u * 0.42F);
            list.add(new BladeKnot(
                    sideOffsetX * 0.78F, yOffset - spread * 0.48F, z,
                    sideOffsetX + (sideOffsetX < 0 ? -spread : spread), yOffset + spread * 0.72F, z + 0.14F
            ));
        }
        return new RibbonStrip(list, delayFrac, speedFrac, palette);
    }

    public static void renderWorld(WorldRenderContext context, long clientTick) {
        if (TRAILS.isEmpty() && DASH_RIBBONS.isEmpty() && FLURRY_CUTS.isEmpty() && SPARKS.isEmpty()) {
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
        int localPlayerId = mc.player != null ? mc.player.getId() : -1;
        boolean firstPerson = !camera.isDetached();

        // Dọn sạch các hiệu ứng hết hạn trước khi tách pass vẽ (tránh lỗi MultiBufferSource.BufferSource "Not building!" khi giữ đồng thời 2 VertexConsumer không cố định trong Minecraft 1.21.1!)
        TRAILS.removeIf(tr -> Math.max(0.0F, nowF - tr.startTick) >= tr.durationTicks);
        DASH_RIBBONS.removeIf(dr -> Math.max(0.0F, nowF - dr.startTick) >= dr.durationTicks);
        FLURRY_CUTS.removeIf(cut -> (nowF - cut.startTickF) >= cut.durationTicks);
        SPARKS.removeIf(sp -> Math.max(0.0F, nowF - sp.startTick) >= sp.durationTicks);

        boolean hasRibbons = !TRAILS.isEmpty() || !DASH_RIBBONS.isEmpty() || !FLURRY_CUTS.isEmpty();

        // Pass 1: RIBBON_BODY (toàn bộ phần thân ribbon & 10 vệt chém)
        if (hasRibbons) {
            VertexConsumer bodyVc = consumers.getBuffer(RibbonRenderTypes.RIBBON_BODY);
            renderRibbonPass(poseStack, bodyVc, false, level, camPos, nowF, partialTick, localPlayerId, firstPerson);
        }

        // Pass 2: RIBBON_GLOW (toàn bộ lớp hào quang additive — lấy buffer mới sau khi xong hoàn toàn Pass 1)
        if (hasRibbons) {
            VertexConsumer glowVc = consumers.getBuffer(RibbonRenderTypes.RIBBON_GLOW);
            renderRibbonPass(poseStack, glowVc, true, level, camPos, nowF, partialTick, localPlayerId, firstPerson);
        }

        // Pass 3: Tia lửa va chạm sắc gọn (SPARK_GLOW)
        if (!SPARKS.isEmpty()) {
            VertexConsumer sparkVc = consumers.getBuffer(RibbonRenderTypes.SPARK_GLOW);
            for (ImpactSpark sp : SPARKS) {
                float age = Math.max(0.0F, nowF - sp.startTick);
                renderImpactSpark(poseStack, sparkVc, camera, camPos, sp, age / sp.durationTicks);
            }
        }
    }

    private static void renderRibbonPass(PoseStack poseStack, VertexConsumer vc, boolean glowPass,
                                         ClientLevel level, Vec3 camPos, float nowF, float partialTick,
                                         int localPlayerId, boolean firstPerson) {
        // 1. Dải lụa combo 5 nhịp / Sprint / Air
        for (MoveTrail tr : TRAILS) {
            float age = Math.max(0.0F, nowF - tr.startTick);
            float progress = Mth.clamp(age / tr.durationTicks, 0.0F, 1.0F);
            renderMoveTrail(poseStack, vc, glowPass, level, camPos, tr, progress, partialTick, localPlayerId, firstPerson);
        }

        // 2. 2 dải lụa song song của chiêu Flash Dash
        for (WorldDashRibbon dr : DASH_RIBBONS) {
            float age = Math.max(0.0F, nowF - dr.startTick);
            float progress = Mth.clamp(age / dr.durationTicks, 0.0F, 1.0F);
            float fade = progress < 0.10F
                    ? (progress / 0.10F)
                    : (1.0F - Mth.clamp((progress - 0.45F) / 0.55F, 0.0F, 1.0F));
            fade = fade * fade * (3.0F - 2.0F * fade);
            if (fade > 0.01F) {
                poseStack.pushPose();
                poseStack.translate(dr.originX - camPos.x, dr.originY - camPos.y, dr.originZ - camPos.z);
                drawCalligraphicStrip(poseStack.last(), vc, glowPass, dr.rightDiamondStrip, progress, fade);
                drawCalligraphicStrip(poseStack.last(), vc, glowPass, dr.leftNetheriteStrip, progress, fade);
                poseStack.popPose();
            }
        }

        // 3. 10 vệt chém chéo sắc lẹm bùng nổ trên thân mục tiêu
        for (TargetSlashCut cut : FLURRY_CUTS) {
            float age = nowF - cut.startTickF;
            if (age < 0.0F) {
                continue; // Chưa tới nhịp trễ stagger
            }
            renderTargetSlashCut(poseStack, vc, glowPass, camPos, cut, age / cut.durationTicks);
        }
    }

    private static void renderMoveTrail(PoseStack poseStack, VertexConsumer vc, boolean glowPass,
                                        ClientLevel level, Vec3 camPos, MoveTrail tr, float progress,
                                        float partialTick, int localPlayerId, boolean firstPerson) {
        double wx = tr.anchorX, wy = tr.anchorY, wz = tr.anchorZ;
        Entity ent = level.getEntity(tr.attackerId);
        if (ent != null && ent.isAlive()) {
            Vec3 ep = ent.getPosition(partialTick);
            wx = ep.x;
            wy = ep.y + ent.getBbHeight() * 0.54;
            wz = ep.z;
        }

        boolean localFp = firstPerson && tr.attackerId == localPlayerId;
        float knifeRatio = Mth.clamp(WeaponsConfig.get().knifeRenderScale / 0.92F, 0.65F, 1.55F);
        float scale = (1.0F + tr.powerTier * 0.05F) * knifeRatio * (localFp ? 0.78F : 1.00F);

        poseStack.pushPose();
        poseStack.translate(wx - camPos.x, wy - camPos.y, wz - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-tr.yawDeg));
        if (localFp) {
            poseStack.translate(0.0, -0.16, 0.52);
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.clamp(tr.pitchDeg * 0.42F, -24.0F, 24.0F)));
        } else {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.clamp(tr.pitchDeg * 0.20F, -14.0F, 14.0F)));
        }
        poseStack.scale(scale, scale, scale);

        float globalFade = progress < 0.10F
                ? (progress / 0.10F)
                : (1.0F - Mth.clamp((progress - 0.52F) / 0.48F, 0.0F, 1.0F));
        globalFade = globalFade * globalFade * (3.0F - 2.0F * globalFade);

        if (globalFade > 0.01F) {
            for (RibbonStrip strip : tr.strips) {
                drawCalligraphicStrip(poseStack.last(), vc, glowPass, strip, progress, globalFade);
            }
        }

        poseStack.popPose();
    }

    private static void renderTargetSlashCut(PoseStack poseStack, VertexConsumer vc, boolean glowPass,
                                             Vec3 camPos, TargetSlashCut cut, float progress) {
        float expand = DualBladeAnimState.easeOutQuart(Mth.clamp(progress / 0.28F, 0.0F, 1.0F));
        float fade = progress < 0.14F
                ? (progress / 0.14F)
                : (1.0F - Mth.clamp((progress - 0.34F) / 0.66F, 0.0F, 1.0F));
        if (fade <= 0.01F) {
            return;
        }
        float len = cut.halfLength * (0.35F + 0.65F * expand);
        float w = cut.halfWidth * (1.05F - progress * 0.55F);
        RibbonPalette pal = cut.palette;

        int aTail = Mth.clamp(Math.round(fade * 140.0F), 0, 255);
        int aMid = Mth.clamp(Math.round(fade * 245.0F), 0, 255);
        int aCore = Mth.clamp(Math.round(fade * 255.0F), 0, 255);

        poseStack.pushPose();
        poseStack.translate(cut.cx - camPos.x, cut.cy - camPos.y, cut.cz - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-cut.yawDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(cut.pitchDeg));
        poseStack.mulPose(Axis.ZP.rotationDegrees(cut.rollDeg));

        PoseStack.Pose pose = poseStack.last();
        if (!glowPass) {
            // Cánh chém thư pháp sắc nhọn 2 đầu (2 nửa ghép thoi đối xứng theo trục X)
            emitQuad(vc, pose,
                    -len, 0.0F, 0.0F, 0.02F, 0.50F, pal.tailR, pal.tailG, pal.tailB, aTail,
                    0.0F, -w,   0.0F, 0.50F, 0.08F, pal.tailR, pal.tailG, pal.tailB, aTail,
                    0.0F,  w,   0.0F, 0.50F, 0.92F, pal.midR,  pal.midG,  pal.midB,  aMid,
                    -len, 0.0F, 0.0F, 0.02F, 0.50F, pal.midR,  pal.midG,  pal.midB,  aMid);
            emitQuad(vc, pose,
                    0.0F, -w,   0.0F, 0.50F, 0.08F, pal.tailR, pal.tailG, pal.tailB, aTail,
                     len, 0.0F, 0.0F, 0.98F, 0.50F, pal.tailR, pal.tailG, pal.tailB, aTail,
                     len, 0.0F, 0.0F, 0.98F, 0.50F, pal.midR,  pal.midG,  pal.midB,  aMid,
                    0.0F,  w,   0.0F, 0.50F, 0.92F, pal.midR,  pal.midG,  pal.midB,  aMid);
        } else {
            // Lõi trắng-băng chói siêu sắc (`RIBBON_GLOW` additive) dọc trung tâm đường chém
            float midW = w * 0.52F;
            emitQuad(vc, pose,
                    -len, 0.0F,  0.01F, 0.02F, 0.92F, pal.coreR, pal.coreG, pal.coreB, aCore,
                    0.0F, -midW, 0.01F, 0.50F, 0.80F, pal.midR,  pal.midG,  pal.midB,  aMid,
                     len, 0.0F,  0.01F, 0.98F, 0.92F, pal.coreR, pal.coreG, pal.coreB, aCore,
                    0.0F,  midW, 0.01F, 0.50F, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore);
        }

        poseStack.popPose();
    }

    private static void drawCalligraphicStrip(PoseStack.Pose pose, VertexConsumer vc, boolean glowPass,
                                              RibbonStrip strip, float progress, float globalFade) {
        float localP = Mth.clamp((progress - strip.startDelayFrac) / Math.max(0.15F, 1.0F - strip.startDelayFrac), 0.0F, 1.0F);
        float headFrac = DualBladeAnimState.easeOutQuart(Mth.clamp(localP / strip.sweepSpeedFrac, 0.0F, 1.0F));
        float tailFrac = Mth.clamp((localP - 0.38F) / 0.62F, 0.0F, 0.92F);
        if (headFrac <= tailFrac + 0.03F) {
            return;
        }

        int iStart = Mth.clamp((int) Math.floor(tailFrac * (strip.count - 1)), 0, strip.count - 2);
        int iEnd = Mth.clamp((int) Math.ceil(headFrac * (strip.count - 1)), iStart + 1, strip.count - 1);
        int span = Math.max(1, iEnd - iStart);
        RibbonPalette pal = strip.palette;

        for (int i = iStart; i < iEnd; i++) {
            float u0 = (i - iStart) / (float) span;
            float u1 = (i + 1 - iStart) / (float) span;

            float taper0 = (float) Math.pow(Math.max(0.0F, Mth.sin(u0 * Mth.PI)), 0.62);
            float taper1 = (float) Math.pow(Math.max(0.0F, Mth.sin(u1 * Mth.PI)), 0.62);

            float bx0 = Mth.lerp(1.0F - taper0, strip.bx[i], strip.tx[i]);
            float by0 = Mth.lerp(1.0F - taper0, strip.by[i], strip.ty[i]);
            float bz0 = Mth.lerp(1.0F - taper0, strip.bz[i], strip.tz[i]);
            float tx0 = strip.tx[i], ty0 = strip.ty[i], tz0 = strip.tz[i];

            float bx1 = Mth.lerp(1.0F - taper1, strip.bx[i + 1], strip.tx[i + 1]);
            float by1 = Mth.lerp(1.0F - taper1, strip.by[i + 1], strip.ty[i + 1]);
            float bz1 = Mth.lerp(1.0F - taper1, strip.bz[i + 1], strip.tz[i + 1]);
            float tx1 = strip.tx[i + 1], ty1 = strip.ty[i + 1], tz1 = strip.tz[i + 1];

            float f0 = globalFade * (0.36F + 0.64F * taper0);
            float f1 = globalFade * (0.36F + 0.64F * taper1);

            int aMid0 = Mth.clamp(Math.round(f0 * 246.0F), 0, 255);
            int aMid1 = Mth.clamp(Math.round(f1 * 246.0F), 0, 255);
            int aCore0 = Mth.clamp(Math.round(f0 * 255.0F), 0, 255);
            int aCore1 = Mth.clamp(Math.round(f1 * 255.0F), 0, 255);

            if (!glowPass) {
                float mx0 = Mth.lerp(0.54F, bx0, tx0), my0 = Mth.lerp(0.54F, by0, ty0), mz0 = Mth.lerp(0.54F, bz0, tz0);
                float mx1 = Mth.lerp(0.54F, bx1, tx1), my1 = Mth.lerp(0.54F, by1, ty1), mz1 = Mth.lerp(0.54F, bz1, tz1);
                int aTail0 = Mth.clamp(Math.round(f0 * 160.0F), 0, 255);
                int aTail1 = Mth.clamp(Math.round(f1 * 160.0F), 0, 255);

                // Tầng 1->2 (`RIBBON_BODY` NO_CULL): đuôi tối -> dải giữa
                emitQuad(vc, pose,
                        bx0, by0, bz0, u0, 0.04F, pal.tailR, pal.tailG, pal.tailB, aTail0,
                        bx1, by1, bz1, u1, 0.04F, pal.tailR, pal.tailG, pal.tailB, aTail1,
                        mx1, my1, mz1, u1, 0.56F, pal.midR,  pal.midG,  pal.midB,  aMid1,
                        mx0, my0, mz0, u0, 0.56F, pal.midR,  pal.midG,  pal.midB,  aMid0);

                // Tầng 2->3 (`RIBBON_BODY` NO_CULL): dải giữa -> lõi cạnh cắt trắng-băng
                emitQuad(vc, pose,
                        mx0, my0, mz0, u0, 0.56F, pal.midR,  pal.midG,  pal.midB,  aMid0,
                        mx1, my1, mz1, u1, 0.56F, pal.midR,  pal.midG,  pal.midB,  aMid1,
                        tx1, ty1, tz1, u1, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore1,
                        tx0, ty0, tz0, u0, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore0);
            } else {
                float cx0 = Mth.lerp(0.74F, bx0, tx0), cy0 = Mth.lerp(0.74F, by0, ty0), cz0 = Mth.lerp(0.74F, bz0, tz0);
                float cx1 = Mth.lerp(0.74F, bx1, tx1), cy1 = Mth.lerp(0.74F, by1, ty1), cz1 = Mth.lerp(0.74F, bz1, tz1);

                // Lớp hào quang additive (`RIBBON_GLOW` NO_CULL) dọc mép cắt sáng chói
                emitQuad(vc, pose,
                        cx0, cy0 + 0.015F, cz0, u0, 0.74F, pal.midR,  pal.midG,  pal.midB,  aMid0,
                        cx1, cy1 + 0.015F, cz1, u1, 0.74F, pal.midR,  pal.midG,  pal.midB,  aMid1,
                        tx1, ty1 + 0.025F, tz1, u1, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore1,
                        tx0, ty0 + 0.025F, tz0, u0, 0.98F, pal.coreR, pal.coreG, pal.coreB, aCore0);
            }
        }
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

    private static void renderImpactSpark(PoseStack poseStack, VertexConsumer vc, Camera camera, Vec3 camPos,
                                          ImpactSpark sp, float t) {
        float ease = DualBladeAnimState.easeOutCubic(t);
        double px = sp.ox + sp.vx * ease * 3.0;
        double py = sp.oy + sp.vy * ease * 3.0 - 0.16 * t * t;
        double pz = sp.oz + sp.vz * ease * 3.0;
        float s = sp.size * (1.0F - t * 0.60F);
        int a = Mth.clamp(Math.round((1.0F - t) * 250.0F), 0, 255);
        if (s <= 0.005F || a <= 4) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(px - camPos.x, py - camPos.y, pz - camPos.z);
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F + t * 90.0F));
        emitQuad(vc, poseStack.last(),
                -s, -s, 0.0F, 0.25F, 0.25F, sp.r, sp.g, sp.b, a,
                 s, -s, 0.0F, 0.75F, 0.25F, sp.r, sp.g, sp.b, a,
                 s,  s, 0.0F, 0.75F, 0.75F, sp.r, sp.g, sp.b, a,
                -s,  s, 0.0F, 0.25F, 0.75F, sp.r, sp.g, sp.b, a);
        poseStack.popPose();
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * ((2.0F * p1)
                + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }
}
