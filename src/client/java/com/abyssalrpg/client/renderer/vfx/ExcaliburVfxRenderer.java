package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.ExcaliburCombatHandler;
import com.abyssalrpg.combat.ExcaliburMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Hệ thống VFX Tối Giản & **Vòng Phép Thuật 2D Phong Cách Fairy Tail** cho **Excalibur**:
 * - **Vệt Chém Vàng Mảnh (Golden Slash Trail):** Sắc lẹm kéo dài 2–3 frames giữa mỗi đòn thường, 360° Golden Crescent, Air 1/2 & Royal Counter!
 * - **Hit 3 Thrust:** Duy nhất 1 vòng sóng xung kích vàng mỏng quanh mũi kiếm (không khói bụi).
 * - **Strike Air Thrust:** Duy nhất 1 vòng tròn xung kích màu vàng nhạt/trắng bay thẳng 5 blocks.
 * - **Golden Crescent Wave:** Duy nhất 1 lưỡi liềm ánh sáng vàng mảnh trượt dọc mặt đất 7 blocks.
 * - **Aerial Judgment Air 3:** 1 vòng phép thuật 2D nhỏ màu vàng xoay nhẹ dưới mũi kiếm (`Y + 0.02`) trong `0.3s` rồi tan biến.
 * - **Royal Parry Beat 1:** 1 vòng phép thuật 2D nhỏ màu vàng lóe lên tại điểm va chạm trên lưỡi kiếm.
 * - **Ultimate Excalibur (Fairy Tail Magic Circles + Chùm Tia 20 blocks):**
 *   - Mặt đất (`Y + 0.02`): 1 vòng phép 2D vàng 2 tầng (vòng cổ ngữ & hình vuông/tam giác đan xen) xoay ngược chiều (`age * 4.0°` & `-age * 5.2°`), mở rộng `+20%` ở GĐ 2, bỏ hoàn toàn cột lửa & khói!
 *   - Trước mũi kiếm: 3 vòng phép thuật 2D dựng thẳng đứng dọc trục bắn (nhỏ → vừa → lớn) xoay cực nhanh + chùm tia sáng vàng lõi trắng `20 blocks` thu nhỏ gọn gàng ở `2.30–2.60s`!
 */
public final class ExcaliburVfxRenderer {
    public static final ResourceLocation MAGIC_OUTER_TEX = AbyssalRpgMod.id("textures/vfx/excalibur_magic_outer.png");
    public static final ResourceLocation MAGIC_INNER_TEX = AbyssalRpgMod.id("textures/vfx/excalibur_magic_inner.png");

    private static final class ExcaliburRenderTypes extends RenderType {
        private static final RenderType BODY = RenderType.create(
                "abyssalrpg_excalibur_body",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
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
                "abyssalrpg_excalibur_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
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

        private static final RenderType MAGIC_OUTER = RenderType.create(
                "abyssalrpg_excalibur_magic_outer",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                768,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(MAGIC_OUTER_TEX, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private static final RenderType MAGIC_INNER = RenderType.create(
                "abyssalrpg_excalibur_magic_inner",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                768,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(MAGIC_INNER_TEX, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        private static final RenderType SPARK = RenderType.create(
                "abyssalrpg_excalibur_spark",
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
                        .createCompositeState(false));

        private ExcaliburRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int buf,
                                     boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, fmt, mode, buf, crumble, sort, setup, clear);
        }
    }

    /** Vệt chém mảnh màu vàng kim (Golden Slash Trail) sắc nét trong 2–3 frames giữa mỗi đòn! */
    private static final class SlashArc {
        final int attackerId;
        final double x, y, z;
        final float yawDeg, pitchDeg, rollDeg;
        final float baseR, tipR, startDeg, endDeg;
        final float delayFrac;
        final long startTick;
        final float durationTicks;

        SlashArc(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg, float rollDeg,
                 float baseR, float tipR, float startDeg, float endDeg, float delayFrac,
                 long startTick, float durationTicks) {
            this.attackerId = attackerId;
            this.x = anchor.x;
            this.y = anchor.y;
            this.z = anchor.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.baseR = baseR;
            this.tipR = tipR;
            this.startDeg = startDeg;
            this.endDeg = endDeg;
            this.delayFrac = delayFrac;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Hit 3 Thrust: Duy nhất 1 vòng sóng xung kích vàng mỏng quanh mũi kiếm (không khói bụi)! */
    private static final class SingleThrustRing {
        final int attackerId;
        final double x, y, z;
        final float yawDeg, pitchDeg;
        final long startTick;
        final float durationTicks;

        SingleThrustRing(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg, long startTick, float durationTicks) {
            this.attackerId = attackerId;
            this.x = anchor.x;
            this.y = anchor.y;
            this.z = anchor.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Strike Air Thrust: Duy nhất 1 vòng tròn xung kích màu vàng nhạt/trắng bay thẳng 5 blocks! */
    private static final class StrikeAirRing {
        final double x, y, z;
        final float yawDeg, pitchDeg, rangeBlocks;
        final long startTick;
        final float durationTicks;

        StrikeAirRing(Vec3 origin, float yawDeg, float pitchDeg, float rangeBlocks, long startTick, float durationTicks) {
            this.x = origin.x;
            this.y = origin.y;
            this.z = origin.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rangeBlocks = rangeBlocks;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Golden Crescent Wave: Duy nhất 1 lưỡi liềm ánh sáng vàng mảnh trượt dọc mặt đất 7 blocks! */
    private static final class GoldenCrescentWave {
        final double x, y, z;
        final float yawDeg, rangeBlocks;
        final long startTick;
        final float durationTicks;

        GoldenCrescentWave(Vec3 origin, float yawDeg, float rangeBlocks, long startTick, float durationTicks) {
            this.x = origin.x;
            this.y = origin.y;
            this.z = origin.z;
            this.yawDeg = yawDeg;
            this.rangeBlocks = rangeBlocks;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Vòng phép thuật 2D phẳng nằm sát mặt đất (`Y + 0.02`): dùng cho Ultimate (2.60s) & Aerial Air 3 (0.30s)! */
    private static final class FlatMagicCircle {
        final int followPlayerId; // >= 0: theo chân người chơi (Ultimate); -1: cố định tại điểm cắm kiếm (Air 3)
        final double x, y, z;
        final float baseRadius;
        final boolean isUltimate;
        final long startTick;
        final float durationTicks;

        FlatMagicCircle(int followPlayerId, double x, double y, double z, float baseRadius,
                        boolean isUltimate, long startTick, float durationTicks) {
            this.followPlayerId = followPlayerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.baseRadius = baseRadius;
            this.isUltimate = isUltimate;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Vòng phép thuật 2D dựng thẳng đứng: dùng cho Royal Parry Beat 1 tại điểm va chạm trên lưỡi kiếm! */
    private static final class VerticalMagicCircle {
        final double x, y, z;
        final float yawDeg, pitchDeg, radius;
        final long startTick;
        final float durationTicks;

        VerticalMagicCircle(Vec3 pos, float yawDeg, float pitchDeg, float radius, long startTick, float durationTicks) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.radius = radius;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Ultimate Phase 3: 3 vòng phép thuật 2D dựng dọc trục bắn (nhỏ → vừa → lớn) + Chùm tia sáng vàng lõi trắng 20 blocks! */
    private static final class ExcaliburBeam {
        final double x, y, z;
        final float yawDeg, pitchDeg;
        final float rangeBlocks;
        final long startTick;
        final float durationTicks;

        ExcaliburBeam(Vec3 origin, float yawDeg, float pitchDeg, float rangeBlocks, long startTick, float durationTicks) {
            this.x = origin.x;
            this.y = origin.y;
            this.z = origin.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rangeBlocks = rangeBlocks;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class StarSparkle {
        final double ox, oy, oz;
        final float vx, vy, vz, size;
        final int r, g, b;
        final long startTick;
        final float durationTicks;

        StarSparkle(double ox, double oy, double oz, float vx, float vy, float vz, float size,
                    int r, int g, int b, long startTick, float durationTicks) {
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

    private static final List<SlashArc> SLASHES = new ArrayList<>();
    private static final List<SingleThrustRing> THRUST_RINGS = new ArrayList<>();
    private static final List<StrikeAirRing> STRIKE_AIR_RINGS = new ArrayList<>();
    private static final List<GoldenCrescentWave> CRESCENT_WAVES = new ArrayList<>();
    private static final List<FlatMagicCircle> FLAT_MAGIC_CIRCLES = new ArrayList<>();
    private static final List<VerticalMagicCircle> VERT_MAGIC_CIRCLES = new ArrayList<>();
    private static final List<ExcaliburBeam> BEAMS = new ArrayList<>();
    private static final List<StarSparkle> SPARKLES = new ArrayList<>();

    private ExcaliburVfxRenderer() {}

    public static void clear() {
        SLASHES.clear();
        THRUST_RINGS.clear();
        STRIKE_AIR_RINGS.clear();
        CRESCENT_WAVES.clear();
        FLAT_MAGIC_CIRCLES.clear();
        VERT_MAGIC_CIRCLES.clear();
        BEAMS.clear();
        SPARKLES.clear();
    }

    /** Bật vệt chém mảnh màu vàng kim sắc lẹm theo đúng quỹ đạo mỗi đòn (chỉ 2–3 frames giữa đòn!). */
    public static void spawnSlashArc(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg,
                                     ExcaliburMoveType move, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        if (move == ExcaliburMoveType.HIT_3_THRUST) {
            for (SingleThrustRing ex : THRUST_RINGS) {
                if (ex.attackerId == attackerId && (clientTick - ex.startTick) <= 3L) {
                    return;
                }
            }
            THRUST_RINGS.add(new SingleThrustRing(attackerId, anchor, yawDeg, pitchDeg, clientTick, 6.5F));
            return;
        }

        for (SlashArc ex : SLASHES) {
            if (ex.attackerId == attackerId && (clientTick - ex.startTick) <= 2L) {
                return;
            }
        }
        switch (move) {
            case HIT_1_SWEEP -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, 0.0F,
                    1.50F, 2.10F, -84.0F, +86.0F, 0.25F, clientTick, 5.2F));
            case HIT_2_RISING -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, -45.0F,
                    1.50F, 2.12F, +84.0F, -86.0F, 0.20F, clientTick, 5.2F));
            case HIT_4_SLAM -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, -90.0F,
                    1.55F, 2.38F, -96.0F, +82.0F, 0.34F, clientTick, 6.5F));
            case GOLDEN_CRESCENT -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, 0.0F, 0.0F,
                    2.45F, 3.20F, 0.0F, +368.0F, 0.04F, clientTick, 5.8F));
            case AIR_1_DIAG -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, -42.0F,
                    1.52F, 2.22F, -84.0F, +86.0F, 0.16F, clientTick, 4.6F));
            case AIR_2_DIAG -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, +42.0F,
                    1.52F, 2.22F, +84.0F, -86.0F, 0.10F, clientTick, 4.6F));
            case ROYAL_PARRY_COUNTER -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, 0.0F,
                    1.55F, 2.45F, -88.0F, +92.0F, 0.02F, clientTick, 4.8F));
            case AVALON_PARRY_COUNTER -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, 0.0F,
                    1.62F, 2.38F, +88.0F, -92.0F, 0.04F, clientTick, 5.2F));
            default -> {
            }
        }
    }

    /** Strike Air Thrust: Phóng duy nhất 1 vòng tròn xung kích màu vàng nhạt/trắng bay thẳng 5 blocks! */
    public static void spawnStrikeAirRing(Vec3 origin, float yawDeg, float pitchDeg, float rangeBlocks, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        STRIKE_AIR_RINGS.add(new StrikeAirRing(origin, yawDeg, pitchDeg,
                rangeBlocks > 1.0F ? rangeBlocks : 5.0F, clientTick, 6.5F));
    }

    /** Golden Crescent Wave: Phóng duy nhất 1 lưỡi liềm ánh sáng vàng mảnh trượt mặt đất 7 blocks! */
    public static void spawnGoldenCrescentWave(Vec3 origin, float yawDeg, float rangeBlocks, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        CRESCENT_WAVES.add(new GoldenCrescentWave(origin, yawDeg,
                rangeBlocks > 1.0F ? rangeBlocks : 7.0F, clientTick, 9.5F));
    }

    /** Aerial Judgment Air Hit 3 chạm đất: 1 vòng phép thuật 2D nhỏ màu vàng xoay dưới mũi kiếm trong 0.3s (6t)! */
    public static void spawnAirImpactMagicCircle(Vec3 pos, float radius, long clientTick, float durationTicks) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        FLAT_MAGIC_CIRCLES.add(new FlatMagicCircle(-1, pos.x, pos.y, pos.z,
                radius > 0.5F ? radius : 1.65F, false, clientTick, durationTicks > 1.0F ? durationTicks : 6.0F));
    }

    /** Royal Parry Beat 1: Lóe 1 vòng phép thuật 2D nhỏ màu vàng ngay tại điểm va chạm trên lưỡi kiếm! */
    public static void spawnParryMagicCircle(Vec3 pos, float yawDeg, float pitchDeg, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        VERT_MAGIC_CIRCLES.add(new VerticalMagicCircle(pos, yawDeg, pitchDeg, 0.88F, clientTick, 5.5F));
        for (int i = 0; i < 8; i++) {
            double ang = (Mth.TWO_PI * i) / 8.0;
            spawnSparkle(pos.x, pos.y, pos.z,
                    (float) (Math.cos(ang) * 0.085), (float) (Math.sin(ang) * 0.06), (float) (Math.sin(ang) * 0.085),
                    0.11F, 255, 232, 102, clientTick, 6.0F);
        }
    }

    public static void spawnGuardSparks(Vec3 pos, boolean overhead, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        int count = overhead ? 10 : 6;
        for (int i = 0; i < count; i++) {
            double ang = (Mth.TWO_PI * i) / count;
            double spd = overhead ? 0.09 : 0.065;
            spawnSparkle(pos.x, pos.y, pos.z,
                    (float) (Math.cos(ang) * spd), overhead ? 0.02F : 0.015F, (float) (Math.sin(ang) * spd),
                    0.095F, 255, 230, 100, clientTick, 5.5F);
        }
    }

    /** Ultimate Phase 1–3: 1 vòng phép thuật 2D Fairy Tail màu vàng kim ngay dưới chân (`Y + 0.02`)! */
    public static void spawnUltGroundMagicCircle(int playerId, long clientTick, float durationTicks) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        FLAT_MAGIC_CIRCLES.removeIf(c -> c.isUltimate && c.followPlayerId == playerId);
        FLAT_MAGIC_CIRCLES.add(new FlatMagicCircle(playerId, 0.0, 0.0, 0.0, 2.35F,
                true, clientTick, durationTicks > 1.0F ? durationTicks : ExcaliburCombatHandler.ULT_TOTAL_TICKS));
    }

    /** Ultimate Phase 3: 3 vòng phép thuật 2D dựng đứng dọc trục bắn (nhỏ → vừa → lớn) + Chùm tia 20 blocks lõi trắng! */
    public static void spawnExcaliburBeamAndCircles(int attackerId, Vec3 origin, float yawDeg, float pitchDeg,
                                                    float rangeBlocks, float durationTicks, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) return;
        float range = rangeBlocks > 1.0F ? rangeBlocks : ExcaliburCombatHandler.BEAM_RANGE;
        float dur = durationTicks > 1.0F ? durationTicks : (ExcaliburCombatHandler.ULT_TOTAL_TICKS - ExcaliburCombatHandler.ULT_BEAM_FIRE_TICK);
        BEAMS.add(new ExcaliburBeam(origin, yawDeg, pitchDeg, range, clientTick, dur));
    }

    public static void spawnSparkle(double x, double y, double z, float vx, float vy, float vz,
                                    float size, int r, int g, int b, long clientTick, float durTicks) {
        if (!WeaponsConfig.get().enableSlashVfx || SPARKLES.size() >= 140) {
            return;
        }
        SPARKLES.add(new StarSparkle(x, y, z, vx, vy, vz, size, r, g, b, clientTick, durTicks));
    }

    public static void renderWorld(WorldRenderContext context, long clientTick) {
        if (SLASHES.isEmpty() && THRUST_RINGS.isEmpty() && STRIKE_AIR_RINGS.isEmpty()
                && CRESCENT_WAVES.isEmpty() && FLAT_MAGIC_CIRCLES.isEmpty() && VERT_MAGIC_CIRCLES.isEmpty()
                && BEAMS.isEmpty() && SPARKLES.isEmpty()) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }
        Camera camera = context.camera();
        Vec3 camPos = camera.getPosition();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        double now = clientTick + partialTick;

        SLASHES.removeIf(s -> (now - s.startTick) >= s.durationTicks);
        THRUST_RINGS.removeIf(th -> (now - th.startTick) >= th.durationTicks);
        STRIKE_AIR_RINGS.removeIf(r -> (now - r.startTick) >= r.durationTicks);
        CRESCENT_WAVES.removeIf(w -> (now - w.startTick) >= w.durationTicks);
        FLAT_MAGIC_CIRCLES.removeIf(c -> (now - c.startTick) >= c.durationTicks);
        VERT_MAGIC_CIRCLES.removeIf(c -> (now - c.startTick) >= c.durationTicks);
        BEAMS.removeIf(b -> (now - b.startTick) >= b.durationTicks);
        SPARKLES.removeIf(sp -> (now - sp.startTick) >= sp.durationTicks);

        // PASS 1: VÒNG PHÉP THUẬT 2D FAIRY TAIL (Outer Ring + Counter-Rotating Inner Hexagram!)
        if (!FLAT_MAGIC_CIRCLES.isEmpty() || !VERT_MAGIC_CIRCLES.isEmpty() || !BEAMS.isEmpty()) {
            VertexConsumer magicOuterVc = consumers.getBuffer(ExcaliburRenderTypes.MAGIC_OUTER);
            renderFlatMagicCirclesPass(poseStack, magicOuterVc, camPos, now, partialTick, true);
            renderVerticalMagicCirclesPass(poseStack, magicOuterVc, camPos, now, true);
            renderBeamMagicCirclesPass(poseStack, magicOuterVc, camPos, now, true);

            VertexConsumer magicInnerVc = consumers.getBuffer(ExcaliburRenderTypes.MAGIC_INNER);
            renderFlatMagicCirclesPass(poseStack, magicInnerVc, camPos, now, partialTick, false);
            renderVerticalMagicCirclesPass(poseStack, magicInnerVc, camPos, now, false);
            renderBeamMagicCirclesPass(poseStack, magicInnerVc, camPos, now, false);
        }

        // PASS 2: BODY (Vệt chém vàng mảnh, Lưỡi liềm trượt đất, Chùm tia)
        if (!SLASHES.isEmpty() || !CRESCENT_WAVES.isEmpty() || !BEAMS.isEmpty()) {
            VertexConsumer bodyVc = consumers.getBuffer(ExcaliburRenderTypes.BODY);
            renderSlashesPass(poseStack, bodyVc, camPos, now, partialTick, false);
            renderCrescentWavesPass(poseStack, bodyVc, camPos, now, false);
            renderBeamsPass(poseStack, bodyVc, camPos, now, false);
        }

        // PASS 3: ADDITIVE GLOW (Lõi trắng sắc nét, Vòng xung kích mảnh)
        if (!SLASHES.isEmpty() || !THRUST_RINGS.isEmpty() || !STRIKE_AIR_RINGS.isEmpty()
                || !CRESCENT_WAVES.isEmpty() || !BEAMS.isEmpty()) {
            VertexConsumer glowVc = consumers.getBuffer(ExcaliburRenderTypes.GLOW);
            renderSlashesPass(poseStack, glowVc, camPos, now, partialTick, true);
            renderThrustRingsPass(poseStack, glowVc, camPos, now, partialTick);
            renderStrikeAirRingsPass(poseStack, glowVc, camPos, now);
            renderCrescentWavesPass(poseStack, glowVc, camPos, now, true);
            renderBeamsPass(poseStack, glowVc, camPos, now, true);
        }

        // PASS 4: SPARKLES (Đốm sáng nhỏ tinh tế)
        if (!SPARKLES.isEmpty()) {
            VertexConsumer sparkVc = consumers.getBuffer(ExcaliburRenderTypes.SPARK);
            renderSparklesPass(poseStack, sparkVc, camPos, camera.rotation(), now);
        }
    }

    /** Vẽ Vòng Phép 2D phẳng nằm sát mặt đất (`Y + 0.02` chống Z-fighting!) xoay ngược chiều nhau chuẩn Fairy Tail! */
    private static void renderFlatMagicCirclesPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                                   double now, float partialTick, boolean outerLayer) {
        Minecraft mc = Minecraft.getInstance();
        for (FlatMagicCircle c : FLAT_MAGIC_CIRCLES) {
            float age = (float) (now - c.startTick);
            float t = Mth.clamp(age / c.durationTicks, 0.0F, 1.0F);

            double wx = c.x, wy = c.y, wz = c.z;
            if (c.followPlayerId >= 0 && mc.level != null) {
                Entity ent = mc.level.getEntity(c.followPlayerId);
                if (ent == null || !ent.isAlive()) continue;
                wx = Mth.lerp(partialTick, ent.xo, ent.getX());
                wy = Mth.lerp(partialTick, ent.yo, ent.getY());
                wz = Mth.lerp(partialTick, ent.zo, ent.getZ());
            }

            float radius;
            float alpha;
            float rotDeg;
            if (c.isUltimate) {
                // GĐ 1 (0–18t): Hiện lên & giữ 100% | GĐ 2 (18–24t): Mở rộng +20% & sáng rực | GĐ 3 (24–46t): 120% | Thu thế (46–52t): Thu nhỏ & biến mất!
                float appear = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
                float expand20 = Mth.clamp((age - ExcaliburCombatHandler.ULT_TILT_START_TICK) / 6.0F, 0.0F, 1.0F);
                float shrinkOut = Mth.clamp((age - ExcaliburCombatHandler.ULT_BEAM_END_TICK)
                        / (float) (ExcaliburCombatHandler.ULT_TOTAL_TICKS - ExcaliburCombatHandler.ULT_BEAM_END_TICK), 0.0F, 1.0F);
                float scale = Mth.lerp(appear, 0.35F, 1.0F) * (1.0F + 0.20F * expand20) * (1.0F - shrinkOut);
                radius = c.baseRadius * scale;
                alpha = appear * (1.0F - shrinkOut) * (0.88F + 0.12F * expand20);
                // Tầng ngoài xoay thuận +4.0°/tick, tầng trong xoay ngược -5.2°/tick!
                rotDeg = outerLayer ? (age * 4.0F) : (-age * 5.2F);
            } else {
                // Aerial Air 3 chạm đất: Vòng phép 2D nhỏ xoay nhẹ 0.3s rồi tan biến!
                float appear = Mth.clamp(t / 0.18F, 0.0F, 1.0F);
                float fade = 1.0F - Mth.clamp((t - 0.55F) / 0.45F, 0.0F, 1.0F);
                radius = c.baseRadius * Mth.lerp(appear, 0.55F, 1.0F) * (0.95F + 0.10F * t);
                alpha = appear * fade;
                rotDeg = outerLayer ? (age * 6.5F) : (-age * 8.5F);
            }

            if (alpha <= 0.01F || radius <= 0.05F) continue;

            poseStack.pushPose();
            // Sát mặt đất Y + 0.02 (tầng trong +0.025 tránh z-fight giữa 2 tầng!)
            poseStack.translate(wx - camPos.x, wy + (outerLayer ? 0.020F : 0.026F) - camPos.y, wz - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotDeg));
            Matrix4f mat = poseStack.last().pose();

            int a = Math.max(0, Math.min(255, Math.round(255.0F * alpha)));
            addTexturedQuadXZ(vc, mat, radius, 255, 255, 255, a);
            poseStack.popPose();
        }
    }

    /** Vẽ Vòng Phép 2D dựng thẳng đứng tại điểm va chạm trên lưỡi kiếm khi Royal Parry Beat 1! */
    private static void renderVerticalMagicCirclesPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                                       double now, boolean outerLayer) {
        for (VerticalMagicCircle c : VERT_MAGIC_CIRCLES) {
            float age = (float) (now - c.startTick);
            float t = Mth.clamp(age / c.durationTicks, 0.0F, 1.0F);
            float pop = 1.0F - (1.0F - Mth.clamp(t * 3.5F, 0.0F, 1.0F)) * (1.0F - Mth.clamp(t * 3.5F, 0.0F, 1.0F));
            float fade = 1.0F - Mth.clamp((t - 0.35F) / 0.65F, 0.0F, 1.0F);
            if (fade <= 0.01F) continue;

            float radius = c.radius * Mth.lerp(pop, 0.40F, 1.08F);
            float rotDeg = outerLayer ? (age * 14.0F) : (-age * 18.0F);

            poseStack.pushPose();
            poseStack.translate(c.x - camPos.x, c.y - camPos.y, c.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-c.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(c.pitchDeg * 0.6F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotDeg));
            Matrix4f mat = poseStack.last().pose();

            int a = Math.max(0, Math.min(255, Math.round(255.0F * fade)));
            addTexturedQuadXY(vc, mat, radius, outerLayer ? 0.0F : 0.012F, 255, 255, 255, a);
            poseStack.popPose();
        }
    }

    /** Ultimate Phase 3: 3 Vòng Phép Thuật 2D dựng thẳng đứng xếp dọc trục bắn (nhỏ → vừa → lớn), xoay cực nhanh! */
    private static void renderBeamMagicCirclesPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                                   double now, boolean outerLayer) {
        final float[] zOffsets = {1.10F, 2.55F, 4.30F};
        final float[] radii = {0.92F, 1.45F, 1.98F};
        final float[] speedMult = {1.35F, 1.00F, 0.78F};

        for (ExcaliburBeam beam : BEAMS) {
            float age = (float) (now - beam.startTick);
            float t = Mth.clamp(age / beam.durationTicks, 0.0F, 1.0F);
            // Thu thế (2.30-2.60s = last 24% of beam duration): thu nhỏ dần rồi biến mất!
            float shrink = (t > 0.76F) ? (1.0F - Mth.clamp((t - 0.76F) / 0.24F, 0.0F, 1.0F)) : 1.0F;
            if (shrink <= 0.01F) continue;

            poseStack.pushPose();
            poseStack.translate(beam.x - camPos.x, beam.y - camPos.y, beam.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-beam.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(beam.pitchDeg));

            for (int i = 0; i < 3; i++) {
                float open = Mth.clamp((age - i * 0.65F) / 2.2F, 0.0F, 1.0F);
                if (open <= 0.01F) continue;
                float r = radii[i] * Mth.lerp(open, 0.25F, 1.0F) * shrink;
                float dir = ((i & 1) == 0 ? 1.0F : -1.0F) * (outerLayer ? 1.0F : -1.3F);
                float rotDeg = age * 16.0F * speedMult[i] * dir;

                poseStack.pushPose();
                poseStack.translate(0.0F, 0.0F, zOffsets[i] + (outerLayer ? 0.0F : 0.016F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(rotDeg));
                Matrix4f mat = poseStack.last().pose();
                int a = Math.max(0, Math.min(255, Math.round(255.0F * open * shrink)));
                addTexturedQuadXY(vc, mat, r, 0.0F, 255, 255, 255, a);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    /** Vệt chém mảnh màu vàng kim (Golden Slash Trail) kéo dài sắc nét 2–3 frames giữa mỗi đòn! */
    private static void renderSlashesPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                          double now, float partialTick, boolean glowPass) {
        Minecraft mc = Minecraft.getInstance();
        for (SlashArc s : SLASHES) {
            float rawT = Mth.clamp((float) ((now - s.startTick) / s.durationTicks), 0.0F, 1.0F);
            if (rawT < s.delayFrac) {
                continue;
            }
            float t = Mth.clamp((rawT - s.delayFrac) / Math.max(0.15F, 1.0F - s.delayFrac), 0.0F, 1.0F);
            boolean fullCircle = Math.abs(s.endDeg - s.startDeg) > 280.0F;
            float headP = Mth.clamp(t / (fullCircle ? 0.54F : 0.42F), 0.0F, 1.0F);
            float sweepHead = 1.0F - (1.0F - headP) * (1.0F - headP) * (1.0F - headP) * (1.0F - headP);
            float sweepTail = Mth.clamp((t - (fullCircle ? 0.24F : 0.22F)) / 0.78F, 0.0F, 0.94F);
            if (sweepHead <= sweepTail + 0.02F) {
                continue;
            }
            float fade = (t < 0.14F) ? (t / 0.14F) : (1.0F - Mth.clamp((t - 0.34F) / 0.66F, 0.0F, 1.0F));
            if (fade <= 0.01F) {
                continue;
            }

            double ax = s.x, ay = s.y, az = s.z;
            if (mc.level != null) {
                Entity ent = mc.level.getEntity(s.attackerId);
                if (ent != null && ent.isAlive()) {
                    ax = Mth.lerp(partialTick, ent.xo, ent.getX());
                    ay = Mth.lerp(partialTick, ent.yo, ent.getY()) + ent.getBbHeight() * 0.62;
                    az = Mth.lerp(partialTick, ent.zo, ent.getZ());
                }
            }

            poseStack.pushPose();
            poseStack.translate(ax - camPos.x, ay - camPos.y, az - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-s.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(s.pitchDeg * 0.65F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(s.rollDeg));

            Matrix4f mat = poseStack.last().pose();
            int segs = fullCircle ? 32 : 20;
            int r = 255;
            int g = glowPass ? 246 : 212;
            int b = glowPass ? 180 : 48;
            int a = Math.round((glowPass ? 248 : 195) * fade);

            float innerR = glowPass ? Mth.lerp(0.55F, s.baseR, s.tipR) : s.baseR;
            float outerR = glowPass ? s.tipR * 1.03F : s.tipR;

            for (int i = 0; i < segs; i++) {
                float f0 = i / (float) segs;
                float f1 = (i + 1) / (float) segs;
                float u0 = Mth.lerp(f0, sweepTail, sweepHead);
                float u1 = Mth.lerp(f1, sweepTail, sweepHead);
                float ang0 = Mth.lerp(u0, s.startDeg, s.endDeg) * Mth.DEG_TO_RAD;
                float ang1 = Mth.lerp(u1, s.startDeg, s.endDeg) * Mth.DEG_TO_RAD;

                float w0 = (float) Math.pow(Math.max(0.0F, Mth.sin(f0 * Mth.PI)), 0.62);
                float w1 = (float) Math.pow(Math.max(0.0F, Mth.sin(f1 * Mth.PI)), 0.62);
                float r0Out = Mth.lerp(w0, innerR, outerR);
                float r1Out = Mth.lerp(w1, innerR, outerR);

                int a0 = Math.max(0, Math.min(255, Math.round(a * w0)));
                int a1 = Math.max(0, Math.min(255, Math.round(a * w1)));

                addQuad(vc, mat,
                        Mth.sin(ang0) * innerR, 0, Mth.cos(ang0) * innerR,
                        Mth.sin(ang0) * r0Out,  0, Mth.cos(ang0) * r0Out,
                        Mth.sin(ang1) * r1Out,  0, Mth.cos(ang1) * r1Out,
                        Mth.sin(ang1) * innerR, 0, Mth.cos(ang1) * innerR,
                        r, g, b, a0, a1);
            }
            poseStack.popPose();
        }
    }

    /** Hit 3 Thrust: Duy nhất 1 vòng sóng xung kích vàng mỏng quanh mũi kiếm (không khói bụi)! */
    private static void renderThrustRingsPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                              double now, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        for (SingleThrustRing th : THRUST_RINGS) {
            float rawT = Mth.clamp((float) ((now - th.startTick) / th.durationTicks), 0.0F, 1.0F);
            if (rawT < 0.28F) continue;
            float t = Mth.clamp((rawT - 0.28F) / 0.72F, 0.0F, 1.0F);
            float expand = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float fade = 1.0F - Mth.clamp((t - 0.20F) / 0.80F, 0.0F, 1.0F);
            if (fade <= 0.01F) continue;

            double ax = th.x, ay = th.y, az = th.z;
            if (mc.level != null) {
                Entity ent = mc.level.getEntity(th.attackerId);
                if (ent != null && ent.isAlive()) {
                    ax = Mth.lerp(partialTick, ent.xo, ent.getX());
                    ay = Mth.lerp(partialTick, ent.yo, ent.getY()) + ent.getBbHeight() * 0.62;
                    az = Mth.lerp(partialTick, ent.zo, ent.getZ());
                }
            }

            poseStack.pushPose();
            poseStack.translate(ax - camPos.x, ay - camPos.y, az - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-th.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(th.pitchDeg * 0.70F));
            Matrix4f mat = poseStack.last().pose();

            float zPos = Mth.lerp(expand, 1.55F, 2.35F);
            float rad = Mth.lerp(expand, 0.24F, 0.92F);
            int a = Math.round(240 * fade);
            drawThinRingXY(vc, mat, 20, rad * 0.86F, rad, zPos, 255, 234, 112, a);
            poseStack.popPose();
        }
    }

    /** Strike Air Thrust: Duy nhất 1 vòng tròn xung kích màu vàng nhạt/trắng bay thẳng về trước 5 blocks! */
    private static void renderStrikeAirRingsPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos, double now) {
        for (StrikeAirRing r : STRIKE_AIR_RINGS) {
            float t = Mth.clamp((float) ((now - r.startTick) / r.durationTicks), 0.0F, 1.0F);
            float travel = 1.0F - (1.0F - t) * (1.0F - t);
            float fade = (t < 0.12F) ? (t / 0.12F) : (1.0F - Mth.clamp((t - 0.55F) / 0.45F, 0.0F, 1.0F));
            if (fade <= 0.01F) continue;

            poseStack.pushPose();
            poseStack.translate(r.x - camPos.x, r.y - camPos.y, r.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-r.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(r.pitchDeg));
            Matrix4f mat = poseStack.last().pose();

            float zPos = 0.65F + travel * (r.rangeBlocks - 0.45F);
            float rad = Mth.lerp(travel, 0.45F, 1.38F);
            int a = Math.round(245 * fade);
            // 1 vòng xung kích mỏng sắc nét màu vàng nhạt / trắng tinh!
            drawThinRingXY(vc, mat, 24, rad * 0.84F, rad, zPos, 255, 250, 212, a);
            drawEllipticalRing(vc, mat, 24, rad, rad, zPos, 0.24F, 255, 244, 180, Math.round(160 * fade));
            poseStack.popPose();
        }
    }

    /** Golden Crescent Wave: Duy nhất 1 lưỡi liềm ánh sáng vàng mảnh trượt dọc mặt đất 7 blocks! */
    private static void renderCrescentWavesPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                                double now, boolean glowPass) {
        for (GoldenCrescentWave w : CRESCENT_WAVES) {
            float t = Mth.clamp((float) ((now - w.startTick) / w.durationTicks), 0.0F, 1.0F);
            float travel = 1.0F - (1.0F - t) * (1.0F - t);
            float fade = (t < 0.10F) ? (t / 0.10F) : (1.0F - Mth.clamp((t - 0.68F) / 0.32F, 0.0F, 1.0F));
            if (fade <= 0.01F) continue;

            float yawRad = w.yawDeg * Mth.DEG_TO_RAD;
            double fwdX = -Mth.sin(yawRad);
            double fwdZ = Mth.cos(yawRad);
            double dist = travel * w.rangeBlocks;

            poseStack.pushPose();
            poseStack.translate(w.x + fwdX * dist - camPos.x, w.y - camPos.y, w.z + fwdZ * dist - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-w.yawDeg));
            Matrix4f mat = poseStack.last().pose();

            int segs = 16;
            float rOuter = glowPass ? 1.52F : 1.46F;
            float rInner = glowPass ? 1.18F : 1.04F;
            int r = 255;
            int g = glowPass ? 248 : 212;
            int b = glowPass ? 204 : 44;
            int aMax = Math.round((glowPass ? 250 : 205) * fade);

            for (int i = 0; i < segs; i++) {
                float u0 = i / (float) segs;
                float u1 = (i + 1) / (float) segs;
                float ang0 = Mth.lerp(u0, -68.0F, +68.0F) * Mth.DEG_TO_RAD;
                float ang1 = Mth.lerp(u1, -68.0F, +68.0F) * Mth.DEG_TO_RAD;
                float env0 = Mth.sin(u0 * Mth.PI);
                float env1 = Mth.sin(u1 * Mth.PI);
                float ro0 = Mth.lerp(env0, rInner, rOuter);
                float ro1 = Mth.lerp(env1, rInner, rOuter);

                addQuad(vc, mat,
                        Mth.sin(ang0) * rInner, 0.04F, Mth.cos(ang0) * rInner - 0.7F,
                        Mth.sin(ang0) * ro0, 0.04F, Mth.cos(ang0) * ro0 - 0.7F,
                        Mth.sin(ang1) * ro1, 0.04F, Mth.cos(ang1) * ro1 - 0.7F,
                        Mth.sin(ang1) * rInner, 0.04F, Mth.cos(ang1) * rInner - 0.7F,
                        r, g, b, Math.round(aMax * env0), Math.round(aMax * env1));
            }
            poseStack.popPose();
        }
    }

    /** Ultimate Phase 3: Chùm tia sáng vàng lõi trắng sắc nét dài 20 blocks bắn từ tâm 3 vòng phép đứng (không khói mù)! */
    private static void renderBeamsPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                        double now, boolean glowPass) {
        for (ExcaliburBeam beam : BEAMS) {
            float age = (float) (now - beam.startTick);
            float t = Mth.clamp(age / beam.durationTicks, 0.0F, 1.0F);
            float extendFrac = 1.0F - (float) Math.pow(1.0F - Mth.clamp(age / 2.2F, 0.0F, 1.0F), 3.0);
            // Thu thế (2.30-2.60s = last 24%): thu nhỏ dần rồi biến mất gọn gàng!
            float narrow = (t > 0.76F) ? (1.0F - Mth.clamp((t - 0.76F) / 0.24F, 0.0F, 1.0F)) : 1.0F;
            float fade = (t < 0.06F) ? (t / 0.06F) : narrow;
            if (fade <= 0.01F) continue;

            float pulse = 1.0F + 0.08F * Mth.sin((float) (now * 2.4));
            float curLen = Math.max(1.4F, beam.rangeBlocks * extendFrac);
            float startW = ExcaliburCombatHandler.BEAM_START_HALF_W * narrow;
            float startH = ExcaliburCombatHandler.BEAM_START_HALF_H * narrow;
            float endW = Mth.lerp(extendFrac, startW, ExcaliburCombatHandler.BEAM_END_HALF_W * narrow);
            float endH = Mth.lerp(extendFrac, startH, ExcaliburCombatHandler.BEAM_END_HALF_H * narrow);

            poseStack.pushPose();
            poseStack.translate(beam.x - camPos.x, beam.y - camPos.y, beam.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-beam.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(beam.pitchDeg));
            Matrix4f mat = poseStack.last().pose();

            int sides = 18;
            float spin = (float) (now * 0.28);

            if (!glowPass) {
                drawEllipticalCone(vc, mat, sides,
                        startW * 1.04F * pulse, startH * 1.04F * pulse,
                        endW * 0.98F * pulse, endH * 0.98F * pulse,
                        curLen, spin, 255, 208, 32, Math.round(145 * fade), Math.round(52 * fade));
            } else {
                drawEllipticalCone(vc, mat, sides,
                        startW * 0.88F * pulse, startH * 0.88F * pulse,
                        endW * 0.84F * pulse, endH * 0.84F * pulse,
                        curLen, -spin * 0.9F, 255, 232, 102, Math.round(215 * fade), Math.round(88 * fade));
                // Lõi trắng tinh khiết sắc nét ở tâm chùm tia!
                drawEllipticalCone(vc, mat, sides,
                        startW * 0.46F, startH * 0.46F,
                        endW * 0.44F, endH * 0.44F,
                        curLen * 0.99F, spin * 1.4F, 255, 255, 255, Math.round(255 * fade), Math.round(205 * fade));
            }
            poseStack.popPose();
        }
    }

    private static void renderSparklesPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                           Quaternionf camRot, double now) {
        for (StarSparkle sp : SPARKLES) {
            float age = (float) (now - sp.startTick);
            float t = Mth.clamp(age / sp.durationTicks, 0.0F, 1.0F);
            float fade = (t < 0.20F) ? (t / 0.20F) : (1.0F - (t - 0.20F) / 0.80F);
            if (fade <= 0.01F) continue;
            double wx = sp.ox + sp.vx * age;
            double wy = sp.oy + sp.vy * age;
            double wz = sp.oz + sp.vz * age;

            poseStack.pushPose();
            poseStack.translate(wx - camPos.x, wy - camPos.y, wz - camPos.z);
            poseStack.mulPose(camRot);
            Matrix4f mat = poseStack.last().pose();

            float s = sp.size * (0.65F + 0.55F * fade);
            float thin = s * 0.22F;
            int a = Math.max(0, Math.min(255, Math.round(235 * fade)));
            addQuad(vc, mat, -thin, -s, 0, thin, -s, 0, thin, s, 0, -thin, s, 0, sp.r, sp.g, sp.b, a, a);
            addQuad(vc, mat, -s, -thin, 0, s, -thin, 0, s, thin, 0, -s, thin, 0, sp.r, sp.g, sp.b, a, a);
            poseStack.popPose();
        }
    }

    private static void addTexturedQuadXZ(VertexConsumer vc, Matrix4f mat, float halfSize,
                                          int r, int g, int b, int a) {
        vc.addVertex(mat, -halfSize, 0.0F, -halfSize).setColor(r, g, b, a).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, -halfSize, 0.0F, +halfSize).setColor(r, g, b, a).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, +halfSize, 0.0F, +halfSize).setColor(r, g, b, a).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, +halfSize, 0.0F, -halfSize).setColor(r, g, b, a).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    private static void addTexturedQuadXY(VertexConsumer vc, Matrix4f mat, float halfSize, float z,
                                          int r, int g, int b, int a) {
        vc.addVertex(mat, -halfSize, -halfSize, z).setColor(r, g, b, a).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
        vc.addVertex(mat, +halfSize, -halfSize, z).setColor(r, g, b, a).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
        vc.addVertex(mat, +halfSize, +halfSize, z).setColor(r, g, b, a).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
        vc.addVertex(mat, -halfSize, +halfSize, z).setColor(r, g, b, a).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
    }

    private static void drawThinRingXY(VertexConsumer vc, Matrix4f mat, int sides,
                                       float innerR, float outerR, float zPos,
                                       int r, int g, int b, int alpha) {
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides;
            float a1 = (i + 1) * Mth.TWO_PI / sides;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            addQuad(vc, mat,
                    c0 * innerR, s0 * innerR, zPos,
                    c1 * innerR, s1 * innerR, zPos,
                    c1 * outerR, s1 * outerR, zPos,
                    c0 * outerR, s0 * outerR, zPos,
                    r, g, b, alpha, alpha);
        }
    }

    private static void drawEllipticalCone(VertexConsumer vc, Matrix4f mat, int sides,
                                           float startRx, float startRy, float endRx, float endRy,
                                           float length, float spinRad,
                                           int r, int g, int b, int alphaNear, int alphaFar) {
        for (int i = 0; i < sides; i++) {
            float a0 = spinRad + (i * Mth.TWO_PI / sides);
            float a1 = spinRad + ((i + 1) * Mth.TWO_PI / sides);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            addQuad(vc, mat,
                    c0 * startRx, s0 * startRy, 0.0F,
                    c1 * startRx, s1 * startRy, 0.0F,
                    c1 * endRx, s1 * endRy, length,
                    c0 * endRx, s0 * endRy, length,
                    r, g, b, alphaNear, alphaFar);
        }
    }

    private static void drawEllipticalRing(VertexConsumer vc, Matrix4f mat, int sides,
                                           float rx, float ry, float zPos, float bandLen,
                                           int r, int g, int b, int alpha) {
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides;
            float a1 = (i + 1) * Mth.TWO_PI / sides;
            float x0 = Mth.cos(a0) * rx, y0 = Mth.sin(a0) * ry;
            float x1 = Mth.cos(a1) * rx, y1 = Mth.sin(a1) * ry;
            addQuad(vc, mat, x0, y0, zPos, x1, y1, zPos, x1, y1, zPos + bandLen, x0, y0, zPos + bandLen,
                    r, g, b, alpha, 0);
        }
    }

    private static void addQuad(VertexConsumer vc, Matrix4f mat,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                int r, int g, int b, int a0, int a1) {
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a0).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a0).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a1).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a1).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
