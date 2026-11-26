package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.combat.MuramasaMoveType;
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
import java.util.Iterator;
import java.util.List;

/**
 * Hệ thống VFX 3D chuyên biệt cho **Yêu Đao Muramasa Katana**:
 * - Vệt chém lưỡi liềm sắc lẹm hai lớp **Vàng Kim rực (`#FFE820`) + Huyết Đỏ/Đen (`#D61422`)** khớp hoàn hảo cạnh lưỡi phát sáng!
 * - Sóng rút kiếm Iaijutsu **Nhất Đao Huyết Nguyện** (lưỡi liềm bay xuyên 14–16 block).
 * - Đốm sao tia lửa 4 cánh dọc cạnh lưỡi vàng & hào quang Huyết Thệ.
 */
public final class MuramasaVfxRenderer {
    private static final class MuramasaRenderTypes extends RenderType {
        private static final RenderType BODY = RenderType.create(
                "abyssalrpg_muramasa_body",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
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
                "abyssalrpg_muramasa_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
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
                "abyssalrpg_muramasa_spark",
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

        private MuramasaRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int buf,
                                    boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, fmt, mode, buf, crumble, sort, setup, clear);
        }
    }

    private static final class SlashArc {
        final int attackerId;
        final double x, y, z;
        final float yawDeg, pitchDeg, rollDeg;
        final float baseR, tipR, startDeg, endDeg, delayFrac;
        final boolean awakened;
        final long startTick;
        final float durationTicks;

        SlashArc(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg, float rollDeg,
                 float baseR, float tipR, float startDeg, float endDeg, float delayFrac,
                 boolean awakened, long startTick, float durationTicks) {
            this.attackerId = attackerId;
            this.x = anchor.x; this.y = anchor.y; this.z = anchor.z;
            this.yawDeg = yawDeg; this.pitchDeg = pitchDeg; this.rollDeg = rollDeg;
            this.baseR = baseR; this.tipR = tipR;
            this.startDeg = startDeg; this.endDeg = endDeg;
            this.delayFrac = delayFrac;
            this.awakened = awakened;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class IaiWave {
        final double ox, oy, oz, dx, dz;
        final float yawDeg, range;
        final boolean awakened;
        final long startTick;
        final float durationTicks;

        IaiWave(Vec3 origin, float yawDeg, float range, boolean awakened, long startTick, float durationTicks) {
            this.ox = origin.x; this.oy = origin.y; this.oz = origin.z;
            float rad = yawDeg * Mth.DEG_TO_RAD;
            this.dx = -Mth.sin(rad);
            this.dz = Mth.cos(rad);
            this.yawDeg = yawDeg;
            this.range = range;
            this.awakened = awakened;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class Sparkle {
        final double x, y, z;
        final float vx, vy, vz, size;
        final int r, g, b;
        final long startTick;
        final float lifeTicks;

        Sparkle(double x, double y, double z, float vx, float vy, float vz, float size,
                int r, int g, int b, long startTick, float lifeTicks) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.size = size;
            this.r = r; this.g = g; this.b = b;
            this.startTick = startTick;
            this.lifeTicks = lifeTicks;
        }
    }

    private static final List<SlashArc> ARCS = new ArrayList<>();
    private static final List<IaiWave> WAVES = new ArrayList<>();
    private static final List<Sparkle> SPARKLES = new ArrayList<>();

    private MuramasaVfxRenderer() {}

    public static void clear() {
        ARCS.clear();
        WAVES.clear();
        SPARKLES.clear();
    }

    public static void spawnSlashArc(int playerId, Vec3 anchor, float yawDeg, float pitchDeg,
                                     MuramasaMoveType move, boolean awakened, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        float boost = awakened ? 1.16F : 1.0F;
        switch (move) {
            case COMBO_1 -> ARCS.add(new SlashArc(playerId, anchor, yawDeg, pitchDeg, -34.0F,
                    0.55F, 2.05F * boost, -82.0F, 84.0F, 0.10F, awakened, tick, 9.5F));
            case COMBO_2 -> ARCS.add(new SlashArc(playerId, anchor, yawDeg, pitchDeg, +30.0F,
                    0.55F, 2.10F * boost, 84.0F, -84.0F, 0.08F, awakened, tick, 9.5F));
            case COMBO_3 -> ARCS.add(new SlashArc(playerId, anchor, yawDeg, pitchDeg, -6.0F,
                    0.60F, 2.35F * boost, -108.0F, 110.0F, 0.12F, awakened, tick, 11.5F));
            default -> {}
        }
    }

    public static void spawnIaiWave(Vec3 origin, float yawDeg, float range, boolean awakened, long tick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        WAVES.add(new IaiWave(origin, yawDeg, range, awakened, tick, 14.0F));
    }

    public static void spawnSparkle(double x, double y, double z, float vx, float vy, float vz,
                                    float size, int r, int g, int b, long tick, float life) {
        if (!WeaponsConfig.get().enableSlashVfx || SPARKLES.size() > 240) {
            return;
        }
        SPARKLES.add(new Sparkle(x, y, z, vx, vy, vz, size, r, g, b, tick, life));
    }

    public static void renderWorld(WorldRenderContext ctx, long clientTick) {
        if (ARCS.isEmpty() && WAVES.isEmpty() && SPARKLES.isEmpty()) {
            return;
        }
        PoseStack ps = ctx.matrixStack();
        MultiBufferSource consumers = ctx.consumers();
        if (ps == null || consumers == null) {
            return;
        }
        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(true);
        Camera cam = ctx.camera();
        Vec3 camPos = cam.getPosition();

        // Pass 1: Dark-Crimson / Steel Slash Body
        VertexConsumer bodyVc = consumers.getBuffer(MuramasaRenderTypes.BODY);
        Iterator<SlashArc> arcIt = ARCS.iterator();
        while (arcIt.hasNext()) {
            SlashArc a = arcIt.next();
            float age = (clientTick - a.startTick) + pt;
            if (age < 0.0F || age >= a.durationTicks) {
                arcIt.remove();
                continue;
            }
            renderArc(ps, bodyVc, camPos, a, age / a.durationTicks, false);
        }
        Iterator<IaiWave> wIt = WAVES.iterator();
        while (wIt.hasNext()) {
            IaiWave w = wIt.next();
            float age = (clientTick - w.startTick) + pt;
            if (age < 0.0F || age >= w.durationTicks) {
                wIt.remove();
                continue;
            }
            renderWave(ps, bodyVc, camPos, w, age / w.durationTicks, false);
        }

        // Pass 2: Blazing Golden-Yellow (`#FFE820`) + Crimson Additive Glow
        VertexConsumer glowVc = consumers.getBuffer(MuramasaRenderTypes.GLOW);
        for (SlashArc a : ARCS) {
            float age = (clientTick - a.startTick) + pt;
            if (age >= 0.0F && age < a.durationTicks) {
                renderArc(ps, glowVc, camPos, a, age / a.durationTicks, true);
            }
        }
        for (IaiWave w : WAVES) {
            float age = (clientTick - w.startTick) + pt;
            if (age >= 0.0F && age < w.durationTicks) {
                renderWave(ps, glowVc, camPos, w, age / w.durationTicks, true);
            }
        }

        // Pass 3: 4-Point Sparkles
        if (!SPARKLES.isEmpty()) {
            VertexConsumer spVc = consumers.getBuffer(MuramasaRenderTypes.SPARK);
            Quaternionf camRot = cam.rotation();
            Iterator<Sparkle> spIt = SPARKLES.iterator();
            while (spIt.hasNext()) {
                Sparkle s = spIt.next();
                float age = (clientTick - s.startTick) + pt;
                if (age < 0.0F || age >= s.lifeTicks) {
                    spIt.remove();
                    continue;
                }
                float f = age / s.lifeTicks;
                float env = Mth.sin(f * Mth.PI);
                float sz = s.size * (0.55F + 0.55F * env);
                int al = Mth.clamp(Math.round(235.0F * env), 0, 255);

                ps.pushPose();
                ps.translate(s.x + s.vx * age - camPos.x, s.y + s.vy * age - camPos.y, s.z + s.vz * age - camPos.z);
                ps.mulPose(camRot);
                Matrix4f m = ps.last().pose();
                spVc.addVertex(m, -sz, -sz, 0).setColor(s.r, s.g, s.b, al).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
                spVc.addVertex(m, sz, -sz, 0).setColor(s.r, s.g, s.b, al).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
                spVc.addVertex(m, sz, sz, 0).setColor(s.r, s.g, s.b, al).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
                spVc.addVertex(m, -sz, sz, 0).setColor(s.r, s.g, s.b, al).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
                ps.popPose();
            }
        }
    }

    private static void renderArc(PoseStack ps, VertexConsumer vc, Vec3 camPos, SlashArc a, float alpha, boolean glow) {
        if (alpha < a.delayFrac) {
            return;
        }
        float p = Mth.clamp((alpha - a.delayFrac) / (1.0F - a.delayFrac), 0.0F, 1.0F);
        float head = 1.0F - (float) Math.pow(1.0F - Math.min(1.0F, p * 1.9F), 3.0);
        float tail = Mth.clamp((p - 0.14F) / 0.86F, 0.0F, 1.0F);
        tail = tail * tail;
        if (head - tail <= 0.02F) {
            return;
        }
        float fade = (p < 0.15F) ? (p / 0.15F) : (1.0F - Mth.clamp((p - 0.55F) / 0.45F, 0.0F, 1.0F));

        double ax = a.x, ay = a.y, az = a.z;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity ent = mc.level.getEntity(a.attackerId);
            if (ent != null) {
                float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
                ax = Mth.lerp(pt, ent.xo, ent.getX());
                ay = Mth.lerp(pt, ent.yo, ent.getY()) + ent.getBbHeight() * 0.62;
                az = Mth.lerp(pt, ent.zo, ent.getZ());
            }
        }

        ps.pushPose();
        ps.translate(ax - camPos.x, ay - camPos.y, az - camPos.z);
        ps.mulPose(Axis.YP.rotationDegrees(-a.yawDeg));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.clamp(a.pitchDeg * 0.45F, -28.0F, 28.0F)));
        ps.mulPose(Axis.ZP.rotationDegrees(a.rollDeg));

        Matrix4f m = ps.last().pose();
        int segs = 16;
        // Body: obsidian-crimson inner edge; Glow: signature Muramasa blazing yellow (`#FFE820`) outer razor edge!
        int rIn  = glow ? (a.awakened ? 255 : 225) : 135;
        int gIn  = glow ? (a.awakened ? 52  : 36)  : 14;
        int bIn  = glow ? (a.awakened ? 42  : 28)  : 20;
        int rOut = glow ? 255 : (a.awakened ? 225 : 245);
        int gOut = glow ? (a.awakened ? 210 : 242) : (a.awakened ? 48 : 212);
        int bOut = glow ? 54  : 38;

        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs;
            float u1 = (i + 1) / (float) segs;
            float f0 = Mth.lerp(u0, tail, head);
            float f1 = Mth.lerp(u1, tail, head);
            float ang0 = Mth.lerp(f0, a.startDeg, a.endDeg) * Mth.DEG_TO_RAD;
            float ang1 = Mth.lerp(f1, a.startDeg, a.endDeg) * Mth.DEG_TO_RAD;

            float w0 = Mth.sin(u0 * Mth.PI);
            float w1 = Mth.sin(u1 * Mth.PI);
            float rIn0 = Mth.lerp(w0, a.tipR * 0.86F, glow ? a.baseR * 1.18F : a.baseR);
            float rIn1 = Mth.lerp(w1, a.tipR * 0.86F, glow ? a.baseR * 1.18F : a.baseR);
            float rOut0 = glow ? a.tipR * 1.07F : a.tipR;
            float rOut1 = glow ? a.tipR * 1.07F : a.tipR;

            int al0 = Mth.clamp(Math.round((glow ? 235.0F : 195.0F) * fade * w0), 0, 255);
            int al1 = Mth.clamp(Math.round((glow ? 235.0F : 195.0F) * fade * w1), 0, 255);

            float sx0 = -Mth.sin(ang0), sz0 = Mth.cos(ang0);
            float sx1 = -Mth.sin(ang1), sz1 = Mth.cos(ang1);

            vc.addVertex(m, sx0 * rIn0, 0.0F, sz0 * rIn0).setColor(rIn, gIn, bIn, al0 / 2).setUv(u0, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(m, sx0 * rOut0, 0.0F, sz0 * rOut0).setColor(rOut, gOut, bOut, al0).setUv(u0, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(m, sx1 * rOut1, 0.0F, sz1 * rOut1).setColor(rOut, gOut, bOut, al1).setUv(u1, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(m, sx1 * rIn1, 0.0F, sz1 * rIn1).setColor(rIn, gIn, bIn, al1 / 2).setUv(u1, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        }
        ps.popPose();
    }

    private static void renderWave(PoseStack ps, VertexConsumer vc, Vec3 camPos, IaiWave w, float alpha, boolean glow) {
        float dist = (1.0F - (1.0F - alpha) * (1.0F - alpha)) * w.range;
        float fade = (alpha < 0.12F) ? (alpha / 0.12F) : (1.0F - Mth.clamp((alpha - 0.52F) / 0.48F, 0.0F, 1.0F));
        float radOut = (w.awakened ? 3.2F : 2.6F) * (0.75F + 0.35F * alpha) * (glow ? 1.08F : 1.0F);
        float radIn = radOut * (glow ? 0.66F : 0.48F);

        ps.pushPose();
        ps.translate(w.ox + w.dx * dist - camPos.x, w.oy - camPos.y, w.oz + w.dz * dist - camPos.z);
        ps.mulPose(Axis.YP.rotationDegrees(-w.yawDeg));
        ps.mulPose(Axis.ZP.rotationDegrees(glow ? 4.0F : -4.0F));

        Matrix4f m = ps.last().pose();
        int segs = 14;
        int rC = glow ? 255 : 168;
        int gC = glow ? (w.awakened ? 196 : 242) : 18;
        int bC = glow ? 44 : 26;
        int alMax = Mth.clamp(Math.round((glow ? 245.0F : 200.0F) * fade), 0, 255);

        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs;
            float u1 = (i + 1) / (float) segs;
            float ang0 = Mth.lerp(u0, -76.0F, 76.0F) * Mth.DEG_TO_RAD;
            float ang1 = Mth.lerp(u1, -76.0F, 76.0F) * Mth.DEG_TO_RAD;
            float e0 = Mth.sin(u0 * Mth.PI);
            float e1 = Mth.sin(u1 * Mth.PI);
            int a0 = Mth.clamp(Math.round(alMax * e0), 0, 255);
            int a1 = Mth.clamp(Math.round(alMax * e1), 0, 255);

            float sx0 = -Mth.sin(ang0), sz0 = Mth.cos(ang0);
            float sx1 = -Mth.sin(ang1), sz1 = Mth.cos(ang1);
            vc.addVertex(m, sx0 * radIn, 0.0F, sz0 * radIn - 1.2F).setColor(160, 16, 24, a0 / 3).setUv(u0, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(m, sx0 * radOut, 0.0F, sz0 * radOut - 1.2F).setColor(rC, gC, bC, a0).setUv(u0, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(m, sx1 * radOut, 0.0F, sz1 * radOut - 1.2F).setColor(rC, gC, bC, a1).setUv(u1, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(m, sx1 * radIn, 0.0F, sz1 * radIn - 1.2F).setColor(160, 16, 24, a1 / 3).setUv(u1, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        }
        ps.popPose();
    }
}
