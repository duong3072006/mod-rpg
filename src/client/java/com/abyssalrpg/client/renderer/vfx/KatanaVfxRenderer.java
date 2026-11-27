package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.combat.KatanaMoveType;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Hệ thống VFX 3D sắc lẹm cho **Damascus Katana (`abyssalrpg:katana`)**:
 * - Chuỗi 4 đòn thường + Chiburi + Deflect.
 * - **5 Nhánh Combo Mở Rộng:**
 *   1. `THRUST`: Vòng sóng xung kích trắng nhỏ quanh mũi kiếm Kissaki ở frame `0.22s` + mũi thương ánh sáng.
 *   2. `DELAY_SWEEP_360`: Vệt quét tầm thấp (Đòn A) + **Vòng cung chém 360°** bao quanh người chơi (`0.32s–0.45s`).
 *   3. `IAIDO_BLINK`: Ngôi sao 4 cánh lóe sáng tại miệng vỏ `Koiguchi` (`0.35s`) + **3–5 vệt chém Judgment Cut đan xen trên không** (đổi đỏ/bạc khi kích nổ!).
 *   4. `AIR_1..3`: Vệt chém hất lộn vòng, chém ngang trên không & **vòng sóng đất đá** khi bổ thiên thạch chạm đất!
 *   5. `PARRY_COUNTER`: Đường phản chém **Vàng/Đỏ** chí mạng + bùng nổ tia lửa vàng/đỏ!
 */
public final class KatanaVfxRenderer {
    private static final class KatanaRenderTypes extends RenderType {
        private static final RenderType SLASH_BODY = RenderType.create(
                "abyssalrpg_katana_slash_body",
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
                "abyssalrpg_katana_glow",
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

        private static final RenderType SPARK = RenderType.create(
                "abyssalrpg_katana_spark",
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

        private KatanaRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int buf,
                                  boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, fmt, mode, buf, crumble, sort, setup, clear);
        }
    }

    private static final class SlashArc {
        final int playerId;
        final Vec3 fallbackPos;
        final float yawDeg;
        final float pitchDeg;
        final float startDeg;
        final float endDeg;
        final float rollDeg;
        final float radius;
        final float thickness;
        final float heightFrac;
        final boolean iaidoFlash;
        final boolean goldCrimson;
        final long startTick;
        final float durationTicks;

        SlashArc(int playerId, Vec3 fallbackPos, float yawDeg, float pitchDeg,
                 float startDeg, float endDeg, float rollDeg, float radius, float thickness,
                 float heightFrac, boolean iaidoFlash, boolean goldCrimson, long startTick, float durationTicks) {
            this.playerId = playerId;
            this.fallbackPos = fallbackPos;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.startDeg = startDeg;
            this.endDeg = endDeg;
            this.rollDeg = rollDeg;
            this.radius = radius;
            this.thickness = thickness;
            this.heightFrac = heightFrac;
            this.iaidoFlash = iaidoFlash;
            this.goldCrimson = goldCrimson;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class DeflectBurst {
        final Vec3 pos;
        final float yawDeg;
        final float pitchDeg;
        final boolean goldCrimson;
        final long startTick;
        final float durationTicks;

        DeflectBurst(Vec3 pos, float yawDeg, float pitchDeg, boolean goldCrimson, long startTick, float durationTicks) {
            this.pos = pos;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.goldCrimson = goldCrimson;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Vòng sóng xung kích trắng quanh mũi kiếm Kissaki cho Nhánh 1 (`THRUST` ở frame 0.22s)! */
    private static final class ThrustRing {
        final int playerId;
        final Vec3 fallbackPos;
        final float yawDeg;
        final float pitchDeg;
        final long startTick;
        final float durationTicks;

        ThrustRing(int playerId, Vec3 fallbackPos, float yawDeg, float pitchDeg, long startTick, float durationTicks) {
            this.playerId = playerId;
            this.fallbackPos = fallbackPos;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Chùm 4–5 vệt chém Judgment Cut đan xen trên không tại vị trí cũ của mục tiêu (Nhánh 3)! */
    private static final class JudgmentCutCluster {
        final Vec3 pos;
        final float baseYawDeg;
        final boolean detonate;
        final long startTick;
        final float durationTicks;

        JudgmentCutCluster(Vec3 pos, float baseYawDeg, boolean detonate, long startTick, float durationTicks) {
            this.pos = pos;
            this.baseYawDeg = baseYawDeg;
            this.detonate = detonate;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    /** Vòng sóng mặt đất cho Đòn Chốt Không Trung (`AIR_3_SLAM`) khi cắm kiếm xuống đất! */
    private static final class GroundShockwave {
        final Vec3 pos;
        final long startTick;
        final float durationTicks;

        GroundShockwave(Vec3 pos, long startTick, float durationTicks) {
            this.pos = pos;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final List<SlashArc> SLASH_ARCS = new ArrayList<>();
    private static final List<DeflectBurst> DEFLECT_BURSTS = new ArrayList<>();
    private static final List<ThrustRing> THRUST_RINGS = new ArrayList<>();
    private static final List<JudgmentCutCluster> JUDGMENT_CUTS = new ArrayList<>();
    private static final List<GroundShockwave> GROUND_WAVES = new ArrayList<>();

    private KatanaVfxRenderer() {}

    public static void clearAll() {
        SLASH_ARCS.clear();
        DEFLECT_BURSTS.clear();
        THRUST_RINGS.clear();
        JUDGMENT_CUTS.clear();
        GROUND_WAVES.clear();
    }

    public static void spawnSlashArc(int playerId, Vec3 pos, float yawDeg, float pitchDeg,
                                     KatanaMoveType move, boolean iaidoFlash, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        if (move == KatanaMoveType.DELAY_SWEEP_360) {
            // Đòn A (0.00-0.25s): Quét tầm thấp sát đầu gối Phải -> Trái
            SLASH_ARCS.add(new SlashArc(playerId, pos, yawDeg, pitchDeg,
                    -88.0F, +92.0F, 0.0F, 2.85F, 0.50F, 0.30F, false, false, tick + 2L, 3.0F));
            // Đòn B (0.32s-0.45s = Tick 6..9): Vệt chém vòng tròn 360° bao quanh người chơi!
            SLASH_ARCS.add(new SlashArc(playerId, pos, yawDeg, 0.0F,
                    0.0F, +372.0F, 0.0F, 3.50F, 0.72F, 0.62F, true, false, tick + 6L, 4.5F));
            return;
        }
        float startDeg;
        float endDeg;
        float rollDeg;
        float radius;
        float thickness;
        float hFrac = 0.62F;
        long effectiveStartTick = tick;
        float duration = 2.6F;
        switch (move) {
            case HIT_1_DRAW -> {
                startDeg = +88.0F; endDeg = -90.0F; rollDeg = -10.0F;
                radius = 2.85F; thickness = 0.56F; hFrac = 0.68F;
                effectiveStartTick = tick + 3L; duration = 2.6F;
            }
            case HIT_2_DOWN_DIAG -> {
                startDeg = -88.0F; endDeg = +92.0F; rollDeg = -45.0F;
                radius = 2.62F; thickness = 0.52F;
                effectiveStartTick = tick + 2L; duration = 2.5F;
            }
            case HIT_3_UP_DIAG -> {
                startDeg = +88.0F; endDeg = -90.0F; rollDeg = -45.0F;
                radius = 2.62F; thickness = 0.52F;
                effectiveStartTick = tick + 2L; duration = 2.5F;
            }
            case HIT_4_FINISHER -> {
                startDeg = -96.0F; endDeg = +90.0F; rollDeg = -90.0F;
                radius = 2.95F; thickness = 0.66F;
                effectiveStartTick = tick + 4L; duration = 2.8F;
            }
            case AIR_1_LAUNCH -> {
                // Hất lộn vòng lớn từ dưới lên đỉnh đầu!
                startDeg = +96.0F; endDeg = -100.0F; rollDeg = -82.0F;
                radius = 2.80F; thickness = 0.58F;
                effectiveStartTick = tick + 1L; duration = 3.2F;
            }
            case AIR_2_MID_SWEEP -> {
                // Chém ngang dứt khoát Phải -> Trái trên không trung!
                startDeg = -92.0F; endDeg = +92.0F; rollDeg = 0.0F;
                radius = 2.90F; thickness = 0.56F; hFrac = 0.68F;
                effectiveStartTick = tick + 1L; duration = 3.0F;
            }
            case CHIBURI_NOTO -> {
                startDeg = -64.0F; endDeg = +58.0F; rollDeg = -48.0F;
                radius = 2.05F; thickness = 0.22F; duration = 2.4F;
            }
            default -> {
                startDeg = -72.0F; endDeg = 94.0F; rollDeg = 32.0F;
                radius = 2.35F; thickness = 0.58F; duration = 5.0F;
            }
        }
        SLASH_ARCS.add(new SlashArc(playerId, pos, yawDeg, pitchDeg,
                startDeg, endDeg, rollDeg, radius, thickness, hFrac, iaidoFlash, false, effectiveStartTick, duration));
    }

    /** Nhánh 1 (`THRUST`): Vòng sóng xung kích trắng quanh mũi kiếm Kissaki ở frame 0.22s (`tick + 4`)! */
    public static void spawnThrustShockwave(int playerId, Vec3 pos, float yawDeg, float pitchDeg, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        THRUST_RINGS.add(new ThrustRing(playerId, pos, yawDeg, pitchDeg, tick + 4L, 6.0F));
    }

    /** Nhánh 3 (`IAIDO_READY` ở 0.35s): Ngôi sao 4 cánh lóe sáng rực tại miệng vỏ Koiguchi! */
    public static void spawnKoiguchiGlint(int playerId, Vec3 fallbackPos, float yawDeg, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        Vec3 pos = fallbackPos;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity e = mc.level.getEntity(playerId);
            if (e != null) {
                float yRad = e.getYRot() * Mth.DEG_TO_RAD;
                double leftX = Mth.cos(yRad);
                double leftZ = Mth.sin(yRad);
                double fwdX = -Mth.sin(yRad);
                double fwdZ = Mth.cos(yRad);
                pos = e.position().add(leftX * 0.34 + fwdX * 0.28, 0.92, leftZ * 0.34 + fwdZ * 0.28);
            }
        }
        ExcaliburVfxRenderer.spawnSparkle(pos.x, pos.y, pos.z, 0.0F, 0.005F, 0.0F,
                0.34F, 245, 252, 255, tick, 7.0F);
        ExcaliburVfxRenderer.spawnSparkle(pos.x, pos.y, pos.z, 0.0F, 0.005F, 0.0F,
                0.22F, 120, 242, 255, tick, 8.0F);
        for (int i = 0; i < 6; i++) {
            double ang = (Mth.TWO_PI * i) / 6.0;
            ExcaliburVfxRenderer.spawnSparkle(pos.x, pos.y, pos.z,
                    (float) (Math.cos(ang) * 0.045), 0.015F, (float) (Math.sin(ang) * 0.045),
                    0.10F, 185, 248, 255, tick, 6.5F);
        }
    }

    /** Nhánh 3 (`JUDGMENT_CUT`): 3–5 vệt chém chéo đan xen trên không trung tại vị trí cũ của mục tiêu! */
    public static void spawnJudgmentCut(Vec3 pos, float yawDeg, boolean detonate, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        JUDGMENT_CUTS.add(new JudgmentCutCluster(pos, yawDeg, detonate, tick, detonate ? 7.5F : 13.0F));
        int count = detonate ? 18 : 10;
        for (int i = 0; i < count; i++) {
            double ang = (Mth.TWO_PI * i) / count;
            double spd = detonate ? 0.14 : 0.06;
            boolean crimson = detonate && ((i & 1) == 0);
            ExcaliburVfxRenderer.spawnSparkle(pos.x, pos.y, pos.z,
                    (float) (Math.cos(ang) * spd),
                    (float) ((Math.sin(ang * 2.0) * 0.08) + 0.02),
                    (float) (Math.sin(ang) * spd),
                    detonate ? 0.18F : 0.12F,
                    crimson ? 255 : 140,
                    crimson ? 68 : 244,
                    crimson ? 64 : 255,
                    tick, detonate ? 9.0F : 11.0F);
        }
    }

    /** Nhánh 4C (`AIR_3_SLAM`): Vòng sóng đất đá văng xung quanh khi cắm kiếm xuống mặt đất! */
    public static void spawnGroundShockwave(Vec3 pos, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        GROUND_WAVES.add(new GroundShockwave(pos, tick, 10.0F));
    }

    /** Nhánh 5 (`PARRY_COUNTER`): Chém phản công chớp nhoáng kèm hiệu ứng tia lửa vàng/đỏ! */
    public static void spawnCounterSlash(int playerId, Vec3 pos, float yawDeg, float pitchDeg, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        SLASH_ARCS.add(new SlashArc(playerId, pos, yawDeg, pitchDeg,
                +92.0F, -96.0F, -36.0F, 3.15F, 0.68F, 0.65F, true, true, tick, 4.2F));
        DEFLECT_BURSTS.add(new DeflectBurst(pos, yawDeg, pitchDeg, true, tick, 8.0F));
        for (int i = 0; i < 20; i++) {
            double ang = (Mth.TWO_PI * i) / 20.0;
            double spd = 0.10 + (i % 3) * 0.055;
            boolean gold = (i % 2 == 0);
            ExcaliburVfxRenderer.spawnSparkle(
                    pos.x, pos.y + 0.15, pos.z,
                    (float) (Math.cos(ang) * spd),
                    (float) ((Math.sin(ang * 3.0) * 0.07) + 0.03),
                    (float) (Math.sin(ang) * spd),
                    0.14F + (i % 2) * 0.06F,
                    255,
                    gold ? 215 : 58,
                    gold ? 48 : 42,
                    tick, 9.0F);
        }
    }

    public static void spawnDeflectBurst(Vec3 pos, float yawDeg, float pitchDeg, long tick) {
        spawnDeflectBurst(pos, yawDeg, pitchDeg, false, tick);
    }

    /** Suriage Sekiro Parry: Tia lửa tròn lớn màu vàng cam (`sekiroGoldOrange = true`) hoặc lam thép! */
    public static void spawnDeflectBurst(Vec3 pos, float yawDeg, float pitchDeg, boolean sekiroGoldOrange, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        DEFLECT_BURSTS.add(new DeflectBurst(pos, yawDeg, pitchDeg, sekiroGoldOrange, tick, sekiroGoldOrange ? 11.0F : 9.0F));
        int count = sekiroGoldOrange ? 24 : 16;
        for (int i = 0; i < count; i++) {
            double ang = (Mth.TWO_PI * i) / count;
            double spd = (sekiroGoldOrange ? 0.11 : 0.08) + (i % 3) * 0.05;
            if (sekiroGoldOrange) {
                ExcaliburVfxRenderer.spawnSparkle(
                        pos.x, pos.y, pos.z,
                        (float) (Math.cos(ang) * spd),
                        (float) ((Math.sin(ang * 2.0) * 0.07) + 0.02),
                        (float) (Math.sin(ang) * spd),
                        0.15F + (i % 2) * 0.07F,
                        255, (i & 1) == 0 ? 195 : 130, (i & 1) == 0 ? 45 : 22, tick, 10.5F);
            } else {
                ExcaliburVfxRenderer.spawnSparkle(
                        pos.x, pos.y, pos.z,
                        (float) (Math.cos(ang) * spd),
                        (float) ((Math.sin(ang * 2.0) * 0.06) + 0.03),
                        (float) (Math.sin(ang) * spd),
                        0.12F + (i % 2) * 0.06F,
                        (i & 1) == 0 ? 115 : 235,
                        (i & 1) == 0 ? 242 : 252,
                        255, tick, 9.5F);
            }
        }
    }

    /** Thế 2 (Kasa-uke): Tia lửa trắng / cam tóe ngang tại điểm tiếp xúc trên đỉnh đầu! */
    public static void spawnKasaImpactSparks(Vec3 pos, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        for (int i = 0; i < 18; i++) {
            double ang = (Mth.TWO_PI * i) / 18.0;
            double spd = 0.10 + (i % 3) * 0.04;
            boolean orange = (i & 1) == 0;
            ExcaliburVfxRenderer.spawnSparkle(
                    pos.x, pos.y, pos.z,
                    (float) (Math.cos(ang) * spd), 0.02F - (i % 2) * 0.04F, (float) (Math.sin(ang) * spd),
                    0.13F + (i % 2) * 0.05F,
                    255, orange ? 168 : 250, orange ? 42 : 242, tick, 8.0F);
        }
    }

    /** Thế 1 (Uke-nagashi) & Thế 5 (Saya-uke): Tóe tia lửa vàng cam rực rỡ ngay điểm tiếp xúc lưỡi kiếm / vỏ kiếm! */
    public static void spawnUkeSlideSparks(Vec3 pos, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            double ang = (Mth.TWO_PI * i) / 16.0;
            double spd = 0.09 + (i % 3) * 0.045;
            boolean gold = (i % 3 != 0);
            ExcaliburVfxRenderer.spawnSparkle(
                    pos.x, pos.y, pos.z,
                    (float) (Math.cos(ang) * spd), (float) (0.04 - (i % 3) * 0.03), (float) (Math.sin(ang) * spd),
                    0.12F + (i % 2) * 0.055F,
                    255, gold ? 196 : 126, gold ? 44 : 18, tick, 7.5F);
        }
    }

    /** Thế 4 (Mune-oshi): Sóng chịu lực nặng + tia lửa trắng-vàng! */
    public static void spawnMuneOshiBrace(Vec3 pos, float yawDeg, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        DEFLECT_BURSTS.add(new DeflectBurst(pos, yawDeg, 0.0F, true, tick, 8.0F));
        spawnKasaImpactSparks(pos, tick);
    }

    public static void renderWorld(WorldRenderContext context, long currentTick) {
        if (SLASH_ARCS.isEmpty() && DEFLECT_BURSTS.isEmpty() && THRUST_RINGS.isEmpty()
                && JUDGMENT_CUTS.isEmpty() && GROUND_WAVES.isEmpty()) {
            return;
        }
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        double now = currentTick + partialTick;
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        Camera camera = context.camera();
        if (poseStack == null || consumers == null || camera == null) {
            return;
        }
        Vec3 camPos = camera.getPosition();

        SLASH_ARCS.removeIf(a -> (now - a.startTick) >= a.durationTicks);
        DEFLECT_BURSTS.removeIf(b -> (now - b.startTick) >= b.durationTicks);
        THRUST_RINGS.removeIf(t -> (now - t.startTick) >= t.durationTicks);
        JUDGMENT_CUTS.removeIf(j -> (now - j.startTick) >= j.durationTicks);
        GROUND_WAVES.removeIf(g -> (now - g.startTick) >= g.durationTicks);

        if (!SLASH_ARCS.isEmpty()) {
            renderSlashArcPass(poseStack, consumers, camPos, now, partialTick, false);
            renderSlashArcPass(poseStack, consumers, camPos, now, partialTick, true);
        }
        if (!DEFLECT_BURSTS.isEmpty() || !THRUST_RINGS.isEmpty() || !JUDGMENT_CUTS.isEmpty() || !GROUND_WAVES.isEmpty()) {
            VertexConsumer sparkVc = consumers.getBuffer(KatanaRenderTypes.SPARK);
            renderDeflectBursts(poseStack, sparkVc, camPos, camera, now);
            renderThrustRings(poseStack, sparkVc, camPos, now, partialTick);
            renderJudgmentCuts(poseStack, sparkVc, camPos, now);
            renderGroundWaves(poseStack, sparkVc, camPos, now);
        }
    }

    private static void renderSlashArcPass(PoseStack poseStack, MultiBufferSource consumers,
                                           Vec3 camPos, double now, float partialTick, boolean glowPass) {
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer vc = consumers.getBuffer(glowPass ? KatanaRenderTypes.GLOW : KatanaRenderTypes.SLASH_BODY);

        for (SlashArc arc : SLASH_ARCS) {
            if (now < arc.startTick) {
                continue;
            }
            float age = Mth.clamp((float) ((now - arc.startTick) / arc.durationTicks), 0.0F, 1.0F);
            float fade = (age < 0.14F) ? (age / 0.14F)
                    : (age < 0.72F ? 1.0F : (1.0F - (age - 0.72F) / 0.28F));
            fade = Mth.clamp(fade, 0.0F, 1.0F);
            if (fade <= 0.01F) {
                continue;
            }

            Vec3 center = arc.fallbackPos;
            if (mc.level != null) {
                Entity e = mc.level.getEntity(arc.playerId);
                if (e != null) {
                    center = e.getPosition(partialTick).add(0.0, e.getBbHeight() * arc.heightFrac, 0.0);
                }
            }

            boolean fullCircle = Math.abs(arc.endDeg - arc.startDeg) > 280.0F;
            float sweep = 1.0F - (1.0F - Mth.clamp(age / (fullCircle ? 0.56F : 0.42F), 0.0F, 1.0F))
                    * (1.0F - Mth.clamp(age / (fullCircle ? 0.56F : 0.42F), 0.0F, 1.0F));
            float curEnd = Mth.lerp(sweep, arc.startDeg, arc.endDeg);
            float curStart = Mth.lerp(Mth.clamp((age - (fullCircle ? 0.28F : 0.18F)) / 0.72F, 0.0F, 1.0F), arc.startDeg, arc.endDeg);

            poseStack.pushPose();
            poseStack.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-arc.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(arc.pitchDeg * 0.45F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(arc.rollDeg));

            Matrix4f mat = poseStack.last().pose();
            int segs = fullCircle ? 32 : 16;
            float rOuter = arc.radius;
            float rInner = Math.max(0.55F, arc.radius - arc.thickness);

            for (int i = 0; i < segs; i++) {
                float u0 = i / (float) segs;
                float u1 = (i + 1) / (float) segs;
                float a0 = Mth.lerp(u0, curStart, curEnd) * Mth.DEG_TO_RAD;
                float a1 = Mth.lerp(u1, curStart, curEnd) * Mth.DEG_TO_RAD;

                float env0 = Mth.sin(u0 * Mth.PI);
                float env1 = Mth.sin(u1 * Mth.PI);
                float ri0 = Mth.lerp(env0, rOuter, rInner);
                float ri1 = Mth.lerp(env1, rOuter, rInner);

                float ox0 = Mth.sin(a0) * rOuter, oz0 = Mth.cos(a0) * rOuter;
                float ix0 = Mth.sin(a0) * ri0, iz0 = Mth.cos(a0) * ri0;
                float ox1 = Mth.sin(a1) * rOuter, oz1 = Mth.cos(a1) * rOuter;
                float ix1 = Mth.sin(a1) * ri1, iz1 = Mth.cos(a1) * ri1;

                if (arc.goldCrimson) {
                    if (glowPass) {
                        addQuad(vc, mat, ix0, 0.01F, iz0, ox0, 0.01F, oz0, ox1, 0.01F, oz1, ix1, 0.01F, iz1,
                                u0, u1, Math.round(255.0F * fade), Math.round(215.0F * fade), Math.round(55.0F * fade), 255);
                    } else {
                        int alphaBody = Math.round(215.0F * fade * (0.45F + 0.55F * Math.max(env0, env1)));
                        addQuad(vc, mat, ix0, 0, iz0, ox0, 0, oz0, ox1, 0, oz1, ix1, 0, iz1,
                                u0, u1, 235, 38, 32, alphaBody);
                    }
                } else if (glowPass) {
                    int rGlow = Math.round((arc.iaidoFlash ? 165.0F : 95.0F) * fade);
                    int gGlow = Math.round(235.0F * fade);
                    int bGlow = Math.round(255.0F * fade);
                    addQuad(vc, mat, ix0, 0.01F, iz0, ox0, 0.01F, oz0, ox1, 0.01F, oz1, ix1, 0.01F, iz1,
                            u0, u1, rGlow, gGlow, bGlow, 255);
                } else {
                    int alphaBody = Math.round(185.0F * fade * (0.45F + 0.55F * Math.max(env0, env1)));
                    addQuad(vc, mat, ix0, 0, iz0, ox0, 0, oz0, ox1, 0, oz1, ix1, 0, iz1,
                            u0, u1, 35, 165, 220, alphaBody);
                }
            }
            poseStack.popPose();
        }
    }

    private static void renderThrustRings(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                          double now, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        for (ThrustRing tr : THRUST_RINGS) {
            if (now < tr.startTick) {
                continue;
            }
            float age = Mth.clamp((float) ((now - tr.startTick) / tr.durationTicks), 0.0F, 1.0F);
            float expand = 1.0F - (1.0F - age) * (1.0F - age) * (1.0F - age);
            float fade = 1.0F - age;
            if (fade <= 0.01F) {
                continue;
            }
            Vec3 center = tr.fallbackPos;
            if (mc.level != null) {
                Entity e = mc.level.getEntity(tr.playerId);
                if (e != null) {
                    center = e.getPosition(partialTick).add(0.0, e.getBbHeight() * 0.62, 0.0);
                }
            }

            poseStack.pushPose();
            poseStack.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-tr.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(tr.pitchDeg));
            Matrix4f mat = poseStack.last().pose();

            int c = Math.round(255.0F * fade);
            // 1. Vòng sóng xung kích trắng nhỏ bung quanh mũi kiếm Kissaki (tại z ~ 2.15 blocks)
            float zRing = 1.85F + expand * 0.70F;
            float rOut = 0.22F + expand * 0.85F;
            float rIn = Math.max(0.05F, rOut - 0.24F * fade);
            int segs = 18;
            for (int i = 0; i < segs; i++) {
                float a0 = (Mth.TWO_PI * i) / segs;
                float a1 = (Mth.TWO_PI * (i + 1)) / segs;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                addQuad(vc, mat,
                        c0 * rIn, s0 * rIn, zRing,
                        c0 * rOut, s0 * rOut, zRing,
                        c1 * rOut, s1 * rOut, zRing,
                        c1 * rIn, s1 * rIn, zRing,
                        0.0F, 1.0F, c, c, c, 255);
            }
            // 2. Tia đâm xuyên trắng-băng lam phóng dọc trục kiếm (0.6 -> 3.8 blocks)
            float spearW = 0.16F * fade;
            float spearLen = 1.4F + expand * 2.8F;
            addQuad(vc, mat, -spearW, 0.0F, 0.5F, spearW, 0.0F, 0.5F, 0.02F, 0.0F, spearLen, -0.02F, 0.0F, spearLen,
                    0.0F, 1.0F, Math.round(220.0F * fade), Math.round(248.0F * fade), c, 255);
            addQuad(vc, mat, 0.0F, -spearW, 0.5F, 0.0F, spearW, 0.5F, 0.0F, 0.02F, spearLen, 0.0F, -0.02F, spearLen,
                    0.0F, 1.0F, c, c, c, 255);
            poseStack.popPose();
        }
    }

    /** 4–5 vệt chém Judgment Cut đan xen sắc lẹm lơ lửng trên không tại vị trí cũ của mục tiêu! */
    private static void renderJudgmentCuts(PoseStack poseStack, VertexConsumer vc, Vec3 camPos, double now) {
        final float[][] cuts = {
                {28.0F,  18.0F,  34.0F, 1.95F},
                {-42.0F, 72.0F, -52.0F, 2.10F},
                {64.0F, 128.0F,  18.0F, 1.85F},
                {-18.0F, -35.0F, 76.0F, 2.05F},
                {12.0F, 165.0F, -24.0F, 1.75F}
        };
        for (JudgmentCutCluster jc : JUDGMENT_CUTS) {
            float age = Mth.clamp((float) ((now - jc.startTick) / jc.durationTicks), 0.0F, 1.0F);
            float pop = 1.0F - (1.0F - Mth.clamp(age / 0.18F, 0.0F, 1.0F)) * (1.0F - Mth.clamp(age / 0.18F, 0.0F, 1.0F));
            float fade = (age < 0.70F) ? 1.0F : (1.0F - (age - 0.70F) / 0.30F);
            fade = Mth.clamp(fade, 0.0F, 1.0F);
            if (fade <= 0.01F) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(jc.pos.x - camPos.x, jc.pos.y - camPos.y, jc.pos.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-jc.baseYawDeg));
            for (int i = 0; i < cuts.length; i++) {
                float[] c = cuts[i];
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(c[1]));
                poseStack.mulPose(Axis.XP.rotationDegrees(c[0]));
                poseStack.mulPose(Axis.ZP.rotationDegrees(c[2]));
                Matrix4f mat = poseStack.last().pose();
                float halfLen = c[3] * (0.45F + 0.55F * pop) * (jc.detonate ? 1.35F : 1.0F);
                float halfW = (jc.detonate ? 0.13F : 0.065F) * fade;
                int r = Math.round((jc.detonate ? 255.0F : 155.0F) * fade);
                int g = Math.round((jc.detonate ? ((i & 1) == 0 ? 75.0F : 235.0F) : 246.0F) * fade);
                int b = Math.round((jc.detonate ? ((i & 1) == 0 ? 68.0F : 255.0F) : 255.0F) * fade);
                addQuad(vc, mat,
                        -halfLen, -halfW, 0.0F,
                        halfLen, -halfW, 0.0F,
                        halfLen, halfW, 0.0F,
                        -halfLen, halfW, 0.0F,
                        0.0F, 1.0F, r, g, b, 255);
                addQuad(vc, mat,
                        -halfLen, 0.0F, -halfW,
                        halfLen, 0.0F, -halfW,
                        halfLen, 0.0F, halfW,
                        -halfLen, 0.0F, halfW,
                        0.0F, 1.0F, r, g, b, 255);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    private static void renderGroundWaves(PoseStack poseStack, VertexConsumer vc, Vec3 camPos, double now) {
        for (GroundShockwave gw : GROUND_WAVES) {
            float age = Mth.clamp((float) ((now - gw.startTick) / gw.durationTicks), 0.0F, 1.0F);
            float expand = 1.0F - (1.0F - age) * (1.0F - age) * (1.0F - age);
            float fade = 1.0F - age;
            if (fade <= 0.01F) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(gw.pos.x - camPos.x, gw.pos.y + 0.06 - camPos.y, gw.pos.z - camPos.z);
            Matrix4f mat = poseStack.last().pose();
            float rOut = 0.45F + expand * 3.55F;
            float rIn = Math.max(0.10F, rOut - 0.65F * fade);
            int segs = 24;
            int rCol = Math.round(145.0F * fade);
            int gCol = Math.round(240.0F * fade);
            int bCol = Math.round(255.0F * fade);
            for (int i = 0; i < segs; i++) {
                float a0 = (Mth.TWO_PI * i) / segs;
                float a1 = (Mth.TWO_PI * (i + 1)) / segs;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                addQuad(vc, mat,
                        c0 * rIn, 0.0F, s0 * rIn,
                        c0 * rOut, 0.0F, s0 * rOut,
                        c1 * rOut, 0.0F, s1 * rOut,
                        c1 * rIn, 0.0F, s1 * rIn,
                        0.0F, 1.0F, rCol, gCol, bCol, 255);
            }
            poseStack.popPose();
        }
    }

    private static void renderDeflectBursts(PoseStack poseStack, VertexConsumer sparkVc,
                                            Vec3 camPos, Camera camera, double now) {
        Quaternionf camRot = camera.rotation();
        for (DeflectBurst b : DEFLECT_BURSTS) {
            float age = Mth.clamp((float) ((now - b.startTick) / b.durationTicks), 0.0F, 1.0F);
            float expand = 1.0F - (1.0F - age) * (1.0F - age) * (1.0F - age);
            float fade = (1.0F - age) * (1.0F - age);
            if (fade <= 0.01F) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(b.pos.x - camPos.x, b.pos.y - camPos.y, b.pos.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-b.yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(b.pitchDeg));
            Matrix4f ringMat = poseStack.last().pose();
            float rOut = 0.25F + expand * 1.65F;
            float rIn = Math.max(0.05F, rOut - (0.34F * (1.0F - age * 0.55F)));
            int segs = 20;
            int rCol = Math.round((b.goldCrimson ? 255.0F : 135.0F) * fade);
            int gCol = Math.round((b.goldCrimson ? 185.0F : 244.0F) * fade);
            int bCol = Math.round((b.goldCrimson ? 42.0F : 255.0F) * fade);
            for (int i = 0; i < segs; i++) {
                float a0 = (Mth.TWO_PI * i) / segs;
                float a1 = (Mth.TWO_PI * (i + 1)) / segs;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                addQuad(sparkVc, ringMat,
                        c0 * rIn, s0 * rIn, 0.0F,
                        c0 * rOut, s0 * rOut, 0.0F,
                        c1 * rOut, s1 * rOut, 0.0F,
                        c1 * rIn, s1 * rIn, 0.0F,
                        0.0F, 1.0F, rCol, gCol, bCol, 255);
            }
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(b.pos.x - camPos.x, b.pos.y - camPos.y, b.pos.z - camPos.z);
            poseStack.mulPose(camRot);
            float flashLen = (0.45F + expand * 1.45F);
            float flashW = 0.16F * (1.0F - age * 0.6F);
            int fr = Math.round((b.goldCrimson ? 255.0F : 230.0F) * fade);
            int fg = Math.round((b.goldCrimson ? 120.0F : 252.0F) * fade);
            int fb = Math.round((b.goldCrimson ? 45.0F : 255.0F) * fade);
            for (float deg : new float[]{35.0F, -55.0F}) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.ZP.rotationDegrees(deg));
                Matrix4f fm = poseStack.last().pose();
                addQuad(sparkVc, fm,
                        -flashLen, -flashW, 0.0F,
                        flashLen, -flashW, 0.0F,
                        flashLen, flashW, 0.0F,
                        -flashLen, flashW, 0.0F,
                        0.0F, 1.0F, fr, fg, fb, 255);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    private static void addQuad(VertexConsumer vc, Matrix4f mat,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float u0, float u1, int r, int g, int b, int a) {
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(u0, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(u0, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setUv(u1, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a).setUv(u1, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
