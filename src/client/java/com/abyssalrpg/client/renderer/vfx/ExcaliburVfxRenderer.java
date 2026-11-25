package com.abyssalrpg.client.renderer.vfx;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hệ thống VFX 3D riêng cho **Excalibur (Pure Radiant Gold & White)** & **Excalibur Morgan (Pitch-Black & Dark-Violet)**:
 * - Tách tuyệt đối từng pass `BODY` -> `GLOW` -> `SPARK` (1 `VertexConsumer` mỗi pass, 100% an toàn cho Minecraft 1.21.1).
 * - **Crescent Slash Trails:** Vệt chém lưỡi liềm vàng thánh khiết / đen–tím sẫm neo chuẩn `1.8` block trước mặt.
 * - **Ultimate Energy Beams:** Luồng chùm tia thánh quang Vàng Rực (`14` block) & Hắc Quang Đen–Tím (`18` block) đa tầng 3D
 *   (lõi trắng/đỏ thẫm + thân xoắn năng lượng + vòng xung kích lan dọc đường tia!).
 * - **Skyward Light Pillar & Dark Mana Vortex:** Cột ánh sáng vàng dâng trào khi gồng RMB & vòng xoáy ma lực đen–tím khi gồng `[R]`.
 * - **Crisp 4-Point Sparkles:** Hạt sao 4 cánh rực rỡ dọc thân kiếm (đồng bộ mật độ hào quang Togetsu!).
 */
public final class ExcaliburVfxRenderer {
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

    private static final class SlashArc {
        final int attackerId;
        final double x, y, z;
        final float yawDeg, pitchDeg, rollDeg;
        final float baseR, tipR, startDeg, endDeg;
        final float delayFrac;
        final boolean morgan;
        final long startTick;
        final float durationTicks;

        SlashArc(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg, float rollDeg,
                 float baseR, float tipR, float startDeg, float endDeg, float delayFrac,
                 boolean morgan, long startTick, float durationTicks) {
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
            this.morgan = morgan;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class EnergyBeam {
        final double x, y, z;
        final float yawDeg, pitchDeg;
        final float rangeBlocks;
        final boolean morgan;
        final long startTick;
        final float durationTicks;

        EnergyBeam(Vec3 origin, float yawDeg, float pitchDeg, float rangeBlocks,
                   boolean morgan, long startTick, float durationTicks) {
            this.x = origin.x;
            this.y = origin.y;
            this.z = origin.z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rangeBlocks = rangeBlocks;
            this.morgan = morgan;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class ChargePillar {
        final int playerId;
        final boolean morgan;
        final boolean darkVortex;
        final long startTick;
        final float durationTicks;

        ChargePillar(int playerId, boolean morgan, boolean darkVortex, long startTick, float durationTicks) {
            this.playerId = playerId;
            this.morgan = morgan;
            this.darkVortex = darkVortex;
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
    private static final List<EnergyBeam> BEAMS = new ArrayList<>();
    private static final List<ChargePillar> PILLARS = new ArrayList<>();
    private static final List<StarSparkle> SPARKLES = new ArrayList<>();

    private ExcaliburVfxRenderer() {}

    public static void clear() {
        SLASHES.clear();
        BEAMS.clear();
        PILLARS.clear();
        SPARKLES.clear();
    }

    public static void spawnSlashArc(int attackerId, Vec3 anchor, float yawDeg, float pitchDeg,
                                     ExcaliburMoveType move, boolean morgan, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        // Chống lặp mượt khi gói server về ngay sau dự đoán cục bộ (0 restart vệt chém!)
        for (SlashArc ex : SLASHES) {
            if (ex.attackerId == attackerId && (clientTick - ex.startTick) <= 3L) {
                return;
            }
        }
        switch (move) {
            // Neo đúng bán kính chuôi -> mũi kiếm gọn 2/3 (0.58..2.02 block), khớp 1:1 hướng vung của tay!
            case COMBO_1 -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, -34.0F,
                    0.56F, 1.98F, -78.0F, 82.0F, 0.14F, morgan, clientTick, 10.0F));
            case COMBO_2 -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, 16.0F,
                    0.58F, 2.04F, 84.0F, -84.0F, 0.10F, morgan, clientTick, 10.0F));
            case COMBO_3 -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, 90.0F,
                    0.60F, 2.22F, -86.0F, 80.0F, 0.24F, morgan, clientTick, 12.0F));
            case ULT_EXCALIBUR, ULT_MORGAN -> SLASHES.add(new SlashArc(attackerId, anchor, yawDeg, pitchDeg, 90.0F,
                    0.74F, morgan ? 3.55F : 3.15F, -90.0F, 88.0F, 0.0F, morgan, clientTick, 12.0F));
            default -> {
            }
        }
    }

    public static void spawnEnergyBeam(int attackerId, Vec3 origin, float yawDeg, float pitchDeg,
                                       float rangeBlocks, boolean morgan, long clientTick) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        BEAMS.add(new EnergyBeam(origin, yawDeg, pitchDeg, rangeBlocks, morgan, clientTick, morgan ? 18.0F : 16.0F));
        spawnSlashArc(attackerId, origin, yawDeg, pitchDeg,
                morgan ? ExcaliburMoveType.ULT_MORGAN : ExcaliburMoveType.ULT_EXCALIBUR, morgan, clientTick);
    }

    public static void spawnChargePillar(int playerId, boolean morgan, boolean darkVortex,
                                         long clientTick, float durationTicks) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        PILLARS.removeIf(p -> p.playerId == playerId);
        PILLARS.add(new ChargePillar(playerId, morgan, darkVortex, clientTick, durationTicks));
    }

    public static void stopChargePillar(int playerId) {
        PILLARS.removeIf(p -> p.playerId == playerId);
    }

    public static void spawnSparkle(double x, double y, double z, float vx, float vy, float vz,
                                    float size, int r, int g, int b, long clientTick, float durTicks) {
        if (!WeaponsConfig.get().enableSlashVfx || SPARKLES.size() >= 220) {
            return;
        }
        SPARKLES.add(new StarSparkle(x, y, z, vx, vy, vz, size, r, g, b, clientTick, durTicks));
    }

    public static void renderWorld(WorldRenderContext context, long clientTick) {
        if (SLASHES.isEmpty() && BEAMS.isEmpty() && PILLARS.isEmpty() && SPARKLES.isEmpty()) {
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

        // Prune expired effects
        SLASHES.removeIf(s -> (now - s.startTick) >= s.durationTicks);
        BEAMS.removeIf(b -> (now - b.startTick) >= b.durationTicks);
        PILLARS.removeIf(p -> (now - p.startTick) >= p.durationTicks);
        SPARKLES.removeIf(sp -> (now - sp.startTick) >= sp.durationTicks);

        // PASS 1: BODY (Translucent Emissive — Dark Obsidian Sheaths & Solid Gold / Crimson Bodies)
        VertexConsumer bodyVc = consumers.getBuffer(ExcaliburRenderTypes.BODY);
        renderSlashesPass(poseStack, bodyVc, camPos, now, partialTick, false);
        renderBeamsPass(poseStack, bodyVc, camPos, now, false);
        renderPillarsPass(poseStack, bodyVc, camPos, now, partialTick, false);

        // PASS 2: GLOW (Additive Full-Bright — Blinding Golden Light & Electric Crimson/Purple Cores!)
        VertexConsumer glowVc = consumers.getBuffer(ExcaliburRenderTypes.GLOW);
        renderSlashesPass(poseStack, glowVc, camPos, now, partialTick, true);
        renderBeamsPass(poseStack, glowVc, camPos, now, true);
        renderPillarsPass(poseStack, glowVc, camPos, now, partialTick, true);

        // PASS 3: SPARK (Crisp Billboarded 4-Point Star Sparkles along Moving Blade Aura!)
        if (!SPARKLES.isEmpty()) {
            VertexConsumer sparkVc = consumers.getBuffer(ExcaliburRenderTypes.SPARK);
            renderSparklesPass(poseStack, sparkVc, camPos, camera.rotation(), now);
        }
    }

    private static void renderSlashesPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                          double now, float partialTick, boolean glowPass) {
        Minecraft mc = Minecraft.getInstance();
        for (SlashArc s : SLASHES) {
            float rawT = Mth.clamp((float) ((now - s.startTick) / s.durationTicks), 0.0F, 1.0F);
            if (rawT < s.delayFrac) {
                continue;
            }
            float t = Mth.clamp((rawT - s.delayFrac) / Math.max(0.15F, 1.0F - s.delayFrac), 0.0F, 1.0F);
            // Đường lướt mượt đồng bộ 1:1 vận tốc vung kiếm (đầu lưỡi lao nhanh easeOutQuart + đuôi vệt lướt đuổi theo mượt!)
            float headP = Mth.clamp(t / 0.44F, 0.0F, 1.0F);
            float sweepHead = 1.0F - (1.0F - headP) * (1.0F - headP) * (1.0F - headP) * (1.0F - headP);
            float sweepTail = Mth.clamp((t - 0.30F) / 0.70F, 0.0F, 0.94F);
            if (sweepHead <= sweepTail + 0.02F) {
                continue;
            }
            float fade = (t < 0.16F) ? (t / 0.16F) : (1.0F - Mth.clamp((t - 0.38F) / 0.62F, 0.0F, 1.0F));
            if (fade <= 0.01F) {
                continue;
            }

            // Bám TRỰC TIẾP tọa độ thời gian thực của người chơi -> vệt chém không bao giờ tách rời khỏi thanh kiếm!
            double ax = s.x;
            double ay = s.y;
            double az = s.z;
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
            int segs = 22;
            int r, g, b, a;
            if (s.morgan) {
                if (!glowPass) {
                    r = 16; g = 4; b = 7; a = Math.round(230 * fade);
                } else {
                    r = 255; g = 32; b = 44; a = Math.round(248 * fade);
                }
            } else {
                if (!glowPass) {
                    r = 255; g = 196; b = 46; a = Math.round(195 * fade);
                } else {
                    r = 255; g = 248; b = 198; a = Math.round(250 * fade);
                }
            }

            float innerR = glowPass ? Mth.lerp(0.38F, s.baseR, s.tipR) : s.baseR;
            float outerR = glowPass ? s.tipR * 1.05F : s.tipR;

            for (int i = 0; i < segs; i++) {
                float f0 = i / (float) segs;
                float f1 = (i + 1) / (float) segs;
                float u0 = Mth.lerp(f0, sweepTail, sweepHead);
                float u1 = Mth.lerp(f1, sweepTail, sweepHead);
                float ang0 = Mth.lerp(u0, s.startDeg, s.endDeg) * Mth.DEG_TO_RAD;
                float ang1 = Mth.lerp(u1, s.startDeg, s.endDeg) * Mth.DEG_TO_RAD;

                // Dáng vệt lưỡi liềm sắc nhọn ở đầu mũi chém và vuốt mảnh ở đuôi vệt
                float w0 = (float) Math.pow(Math.max(0.0F, Mth.sin(f0 * Mth.PI)), 0.58);
                float w1 = (float) Math.pow(Math.max(0.0F, Mth.sin(f1 * Mth.PI)), 0.58);
                float r0Out = Mth.lerp(w0, innerR, outerR);
                float r1Out = Mth.lerp(w1, innerR, outerR);

                int a0 = Math.max(0, Math.min(255, Math.round(a * w0)));
                int a1 = Math.max(0, Math.min(255, Math.round(a * w1)));

                float x0b = Mth.sin(ang0) * innerR, z0b = Mth.cos(ang0) * innerR;
                float x0t = Mth.sin(ang0) * r0Out,  z0t = Mth.cos(ang0) * r0Out;
                float x1b = Mth.sin(ang1) * innerR, z1b = Mth.cos(ang1) * innerR;
                float x1t = Mth.sin(ang1) * r1Out,  z1t = Mth.cos(ang1) * r1Out;

                addQuad(vc, mat,
                        x0b, 0, z0b, x0t, 0, z0t, x1t, 0, z1t, x1b, 0, z1b,
                        r, g, b, a0, a1);

                // Lớp viền lõi cắt sắc lẹm (Trắng–Vàng / Trắng–Đỏ) chạy sát đúng mép mũi kiếm!
                if (glowPass) {
                    float edgeIn0 = Mth.lerp(0.76F, innerR, r0Out);
                    float edgeIn1 = Mth.lerp(0.76F, innerR, r1Out);
                    int er = 255;
                    int eg = s.morgan ? 168 : 255;
                    int eb = s.morgan ? 172 : 235;
                    addQuad(vc, mat,
                            Mth.sin(ang0) * edgeIn0, 0.015F, Mth.cos(ang0) * edgeIn0,
                            x0t, 0.02F, z0t,
                            x1t, 0.02F, z1t,
                            Mth.sin(ang1) * edgeIn1, 0.015F, Mth.cos(ang1) * edgeIn1,
                            er, eg, eb, a0, a1);
                }
            }
            poseStack.popPose();
        }
    }

    private static void renderBeamsPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos, double now, boolean glowPass) {
        for (EnergyBeam beam : BEAMS) {
            float t = Mth.clamp((float) ((now - beam.startTick) / beam.durationTicks), 0.0F, 1.0F);
            // Pha 1 (0..26%): Luồng kiếm ánh sáng khổng lồ cao 28 block chém bổ thẳng TỪ TRỜI XUỐNG ĐẤT!
            // Pha 2 (26..100%): Bức tường ánh sáng dọc trời-đất + luồng xung kích kéo dài dọc toàn bộ 18-24 block!
            float descend = Mth.clamp(t / 0.24F, 0.0F, 1.0F);
            float easeDescend = 1.0F - (1.0F - descend) * (1.0F - descend) * (1.0F - descend);
            float fade = (t < 0.12F) ? (t / 0.12F) : (1.0F - Mth.clamp((t - 0.36F) / 0.64F, 0.0F, 1.0F));
            if (fade <= 0.01F) {
                continue;
            }

            float len = Math.max(beam.rangeBlocks, beam.morgan ? 22.0F : 18.0F) * Mth.clamp(t / 0.18F, 0.14F, 1.0F);
            float skyHeight = (beam.morgan ? 30.0F : 26.0F) * (0.75F + 0.25F * fade);
            float baseRad = (beam.morgan ? 1.48F : 1.08F) * (0.45F + 0.55F * fade);

            poseStack.pushPose();
            // Neo trên mặt phẳng đứng theo hướng nhìn ngang của người chơi để chém thẳng từ bầu trời xuống mặt đất!
            poseStack.translate(beam.x - camPos.x, beam.y - camPos.y, beam.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-beam.yawDeg));
            Matrix4f mat = poseStack.last().pose();

            // Dạng Thường: Luồng sáng VÀNG rực rỡ (Golden Holy Light)!
            // Dạng Kiếm Đen (Morgan): Luồng sáng TÍM (Purple/Violet) chém thẳng từ trời xuống!
            int bodyR = beam.morgan ? 24 : 255;
            int bodyG = beam.morgan ? 8 : 204;
            int bodyB = beam.morgan ? 46 : 44;

            int midR = beam.morgan ? 180 : 255;
            int midG = beam.morgan ? 46 : 228;
            int midB = beam.morgan ? 255 : 74;

            int coreR = beam.morgan ? 242 : 255;
            int coreG = beam.morgan ? 164 : 255;
            int coreB = beam.morgan ? 255 : 226;

            int sides = 8;
            float spin = (float) (now * 0.45);
            float curYTop = skyHeight;
            float curYBot = Mth.lerp(easeDescend, skyHeight * 0.72F, -1.2F);
            float wallHalfW = (beam.morgan ? 1.15F : 0.85F) * (0.50F + 0.50F * fade);

            // 1. BỨC TƯỜNG KIẾM ÁNH SÁNG KHỔNG LỒ CHÉM TỪ TRỜI XUỐNG (Vertical Sky-Slash Blade Curtain!)
            if (!glowPass) {
                int a = Math.round((beam.morgan ? 210 : 175) * fade);
                // Hai mặt bên của bức tường kiếm dọc trời -> đất dọc suốt chiều dài `len`
                addQuad(vc, mat,
                        -wallHalfW, curYBot, 0.0F,
                        -wallHalfW, curYTop, 0.0F,
                        -wallHalfW * 0.35F, curYTop * 0.88F, len,
                        -wallHalfW * 0.35F, curYBot, len,
                        bodyR, bodyG, bodyB, a, Math.round(a * 0.35F));
                addQuad(vc, mat,
                        wallHalfW, curYBot, 0.0F,
                        wallHalfW, curYTop, 0.0F,
                        wallHalfW * 0.35F, curYTop * 0.88F, len,
                        wallHalfW * 0.35F, curYBot, len,
                        bodyR, bodyG, bodyB, a, Math.round(a * 0.35F));
                // Thân luồng năng lượng mặt đất chạy dài phía trước
                drawCylinder(vc, mat, sides, baseRad, len, spin, bodyR, bodyG, bodyB, Math.round((beam.morgan ? 220 : 170) * fade));
            } else {
                int aMid = Math.round(235 * fade);
                int aCore = Math.round(255 * fade);
                // Cánh sáng rực màu VÀNG (Excalibur) / TÍM (Morgan) chém dọc bầu trời!
                float glowHalfW = wallHalfW * 0.78F;
                addQuad(vc, mat,
                        -glowHalfW, curYBot, 0.0F,
                        -glowHalfW, curYTop, 0.0F,
                        0.0F, curYTop * 0.92F, len,
                        0.0F, curYBot, len,
                        midR, midG, midB, aMid, Math.round(aMid * 0.45F));
                addQuad(vc, mat,
                        glowHalfW, curYBot, 0.0F,
                        glowHalfW, curYTop, 0.0F,
                        0.0F, curYTop * 0.92F, len,
                        0.0F, curYBot, len,
                        midR, midG, midB, aMid, Math.round(aMid * 0.45F));
                // Lõi kiếm sáng chói dọc trung tâm từ trời bổ thẳng xuống đất!
                float coreW = wallHalfW * 0.28F;
                addQuad(vc, mat,
                        -coreW, curYBot, 0.0F,
                        -coreW, curYTop, 0.0F,
                        coreW, curYTop * 0.95F, len,
                        coreW, curYBot, len,
                        coreR, coreG, coreB, aCore, Math.round(aCore * 0.55F));

                // 2. LƯỠI KIẾM ÁNH SÁNG ĐANG BỔ TỪ TRỜI XUỐNG (-82° -> 0°!) trong pha đầu
                poseStack.pushPose();
                float slashAngleDeg = Mth.lerp(easeDescend, -82.0F, 4.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(slashAngleDeg));
                Matrix4f slashMat = poseStack.last().pose();
                drawCylinder(vc, slashMat, sides, baseRad * 0.78F, skyHeight * 0.95F, -spin * 1.6F, midR, midG, midB, aMid);
                drawCylinder(vc, slashMat, sides, baseRad * 0.36F, skyHeight * 0.98F, spin * 2.2F, coreR, coreG, coreB, aCore);
                poseStack.popPose();

                // 3. Cột sáng trụ dọc thẳng đứng từ đỉnh trời giáng xuống điểm chém!
                poseStack.pushPose();
                poseStack.translate(0.0F, curYBot, len * 0.28F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                Matrix4f pillarMat = poseStack.last().pose();
                drawCylinder(vc, pillarMat, sides, baseRad * 1.05F, skyHeight, spin * 1.2F, midR, midG, midB, Math.round(180 * fade));
                drawCylinder(vc, pillarMat, sides, baseRad * 0.46F, skyHeight, -spin * 1.8F, coreR, coreG, coreB, Math.round(235 * fade));
                poseStack.popPose();

                // 4. Luồng năng lượng mặt đất + 4 vòng xung kích bùng dọc vệt chém!
                drawCylinder(vc, mat, sides, baseRad * 0.76F, len, -spin * 1.4F, midR, midG, midB, aMid);
                drawCylinder(vc, mat, sides, baseRad * 0.36F, len, spin * 2.0F, coreR, coreG, coreB, aCore);
                for (int ri = 0; ri < 4; ri++) {
                    float zRing = ((t * 1.75F + ri * 0.25F) % 1.0F) * len;
                    float ringR = baseRad * (1.20F + 0.65F * Mth.sin(ri * 1.5F + t * 6.0F));
                    drawBeamRing(vc, mat, 10, ringR, zRing, 0.65F, midR, midG, midB, Math.round(195 * fade));
                }
            }
            poseStack.popPose();
        }
    }

    private static void renderPillarsPass(PoseStack poseStack, VertexConsumer vc, Vec3 camPos,
                                          double now, float partialTick, boolean glowPass) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (ChargePillar p : PILLARS) {
            Entity ent = mc.level.getEntity(p.playerId);
            if (ent == null || !ent.isAlive()) {
                continue;
            }
            float t = Mth.clamp((float) ((now - p.startTick) / p.durationTicks), 0.0F, 1.0F);
            float pulse = 0.75F + 0.25F * Mth.sin((float) (now * 0.85));
            double px = Mth.lerp(partialTick, ent.xo, ent.getX());
            double py = Mth.lerp(partialTick, ent.yo, ent.getY());
            double pz = Mth.lerp(partialTick, ent.zo, ent.getZ());

            poseStack.pushPose();
            poseStack.translate(px - camPos.x, py - camPos.y, pz - camPos.z);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F)); // Cylinder along +Z becomes vertical +Y!
            Matrix4f mat = poseStack.last().pose();

            boolean dark = p.morgan || p.darkVortex;
            float rad = (p.darkVortex ? (1.45F - 0.55F * t) : (0.85F + 0.28F * t)) * pulse;
            float height = p.darkVortex ? (3.2F + 2.0F * t) : (8.5F + 4.5F * t);
            float spin = (float) (now * (p.darkVortex ? 0.55 : 0.35));

            if (!glowPass) {
                int r = dark ? (p.darkVortex ? 24 : 20) : 255;
                int g = dark ? 6 : 208;
                int b = dark ? (p.darkVortex ? 10 : 38) : 66;
                drawCylinder(vc, mat, 8, rad, height, spin, r, g, b, dark ? 145 : 95);
            } else {
                // Gồng biến đổi [R] = xoáy Đỏ Thẫm (Crimson); gồng chiêu chém RMB Morgan = cột ánh sáng Tím (Purple)!
                int r = dark ? (p.darkVortex ? 245 : 182) : 255;
                int g = dark ? (p.darkVortex ? 28 : 48) : 244;
                int b = dark ? (p.darkVortex ? 42 : 255) : 172;
                drawCylinder(vc, mat, 8, rad * 0.62F, height, -spin * 1.6F, r, g, b, 165);
                for (int ri = 0; ri < 3; ri++) {
                    float h = ((t * 2.5F + ri * 0.33F) % 1.0F) * height;
                    drawBeamRing(vc, mat, 10, rad * 1.18F, h, 0.24F, r, g, b, 175);
                }
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
            if (fade <= 0.01F) {
                continue;
            }
            double wx = sp.ox + sp.vx * age;
            double wy = sp.oy + sp.vy * age;
            double wz = sp.oz + sp.vz * age;

            poseStack.pushPose();
            poseStack.translate(wx - camPos.x, wy - camPos.y, wz - camPos.z);
            poseStack.mulPose(camRot);
            Matrix4f mat = poseStack.last().pose();

            float s = sp.size * (0.65F + 0.55F * fade);
            float thin = s * 0.22F;
            int a = Math.max(0, Math.min(255, Math.round(240 * fade)));
            // Crisp 4-point star (vertical spike + horizontal spike + bright center diamond!)
            addQuad(vc, mat, -thin, -s, 0, thin, -s, 0, thin, s, 0, -thin, s, 0, sp.r, sp.g, sp.b, a, a);
            addQuad(vc, mat, -s, -thin, 0, s, -thin, 0, s, thin, 0, -s, thin, 0, sp.r, sp.g, sp.b, a, a);
            float c = s * 0.30F;
            addQuad(vc, mat, -c, -c, 0, c, -c, 0, c, c, 0, -c, c, 0, 255, 255, 255, a, a);
            poseStack.popPose();
        }
    }

    private static void drawCylinder(VertexConsumer vc, Matrix4f mat, int sides, float radius, float length,
                                     float spinRad, int r, int g, int b, int alpha) {
        for (int i = 0; i < sides; i++) {
            float a0 = spinRad + (i * Mth.TWO_PI / sides);
            float a1 = spinRad + ((i + 1) * Mth.TWO_PI / sides);
            float x0 = Mth.cos(a0) * radius, y0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius, y1 = Mth.sin(a1) * radius;
            addQuad(vc, mat, x0, y0, 0.0F, x1, y1, 0.0F, x1, y1, length, x0, y0, length, r, g, b, alpha, alpha);
        }
    }

    private static void drawBeamRing(VertexConsumer vc, Matrix4f mat, int sides, float radius, float zPos,
                                     float bandLen, int r, int g, int b, int alpha) {
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides;
            float a1 = (i + 1) * Mth.TWO_PI / sides;
            float x0 = Mth.cos(a0) * radius, y0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius, y1 = Mth.sin(a1) * radius;
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
