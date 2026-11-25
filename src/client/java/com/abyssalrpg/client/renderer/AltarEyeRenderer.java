package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.block.MythicSummonAltarBlock;
import com.abyssalrpg.block.entity.AltarEyeBlockEntity;
import com.abyssalrpg.client.renderer.vfx.VfxRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders a live 3D Floating Mythological Eye hovering above every Mythic Summon Altar
 * and Abyssal Altar:
 * - Smooth 180-degree sweeping horizontal gaze rotation (+/- 90 deg) + subtle vertical gaze + natural blink!
 * - True 3D spherical eyeball mesh (12x8 smooth lat/lon sphere) with bespoke 32x32 God Eye iris & slit pupil.
 * - Outer 3D effects: 2 counter-rotating tilted glowing arcane gyroscope rings, pulsing outer energy halo,
 *   rising altar-to-eye arcane beam, and 4 smoothly orbiting 3D crystalline rune shards!
 */
public class AltarEyeRenderer implements BlockEntityRenderer<AltarEyeBlockEntity> {
    private static final ResourceLocation[] GOD_EYE_TEXTURES = {
            AbyssalRpgMod.id("textures/vfx/altar_eye_fenrir.png"),
            AbyssalRpgMod.id("textures/vfx/altar_eye_hydra.png"),
            AbyssalRpgMod.id("textures/vfx/altar_eye_jormungandr.png"),
            AbyssalRpgMod.id("textures/vfx/altar_eye_leviathan.png")
    };

    private static final int[] GOD_PRIMARY_COLORS = {
            0x5AE6FF, // 0 Fenrir (Glacial Cyan)
            0x52FF68, // 1 Hydra (Venom Emerald)
            0x28D4FF, // 2 Jormungandr (Deep Frost Blue)
            0x1CF2E8  // 3 Leviathan (Abyssal Aquamarine)
    };
    private static final int[] GOD_ACCENT_COLORS = {
            0xE6F5FF, // 0 Fenrir (Ice White)
            0xD4FF58, // 1 Hydra (Acid Lime)
            0xC8F8FF, // 2 Jormungandr (Ice White)
            0x70A8FF  // 3 Leviathan (Deep Ocean Blue)
    };

    public AltarEyeRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(AltarEyeBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        BlockPos pos = be.getBlockPos();

        float time = (level != null ? (level.getGameTime() % 24000L) : 0L) + partialTick;
        // Phase offset by block position so multiple altars don't move identically in lockstep
        float seedPhase = ((pos.getX() * 31 + pos.getZ() * 17) & 63) * 0.1f;
        float t = time + seedPhase;

        int god = state.getBlock() instanceof MythicSummonAltarBlock
                ? Math.floorMod(state.getValue(MythicSummonAltarBlock.BOSS_TYPE), MythicSummonAltarBlock.GOD_COUNT)
                : 0;
        ResourceLocation eyeTex = GOD_EYE_TEXTURES[god];
        int primaryRgb = GOD_PRIMARY_COLORS[god];
        int accentRgb = GOD_ACCENT_COLORS[god];
        float speedMult = 1.0f;

        float mt = t * speedMult;
        int pr = VfxRenderHelper.r(primaryRgb), pg = VfxRenderHelper.g(primaryRgb), pb = VfxRenderHelper.b(primaryRgb);
        int ar = VfxRenderHelper.r(accentRgb), ag = VfxRenderHelper.g(accentRgb), ab = VfxRenderHelper.b(accentRgb);

        float bob = Mth.sin(mt * 0.065f) * 0.085f;
        float eyeY = 1.54f + bob;
        float pulse = 0.85f + 0.15f * Mth.sin(mt * 0.11f);

        // Compute base facing toward nearest player (or South) + continuous smooth 180-degree (+/- 90 deg) scan sweep!
        float baseYawDeg = 0.0f;
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            double dx = player.getX() - (pos.getX() + 0.5);
            double dz = player.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz < 48.0 * 48.0) {
                baseYawDeg = (float) Math.toDegrees(Math.atan2(dx, dz));
            }
        }
        // Full 180-degree sweeping rotation (-90 deg -> +90 deg -> -90 deg smoothly!)
        float scan180Deg = Mth.sin(mt * 0.042f) * 90.0f;
        float eyeYawDeg = baseYawDeg + scan180Deg;
        float eyePitchDeg = Mth.sin(mt * 0.068f) * 14.0f;

        // Subtle periodic blink (briefly compresses vertical eye scale every ~110 ticks)
        float blinkCycle = (t % 115.0f);
        float blinkScaleY = 1.0f;
        if (blinkCycle < 6.0f) {
            float bf = Mth.sin((blinkCycle / 6.0f) * Mth.PI);
            blinkScaleY = 1.0f - bf * 0.82f;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);

        // ---------------------------------------------------------------------
        // 1. SOFT RISING ARCANE LIGHT PILLAR FROM ALTAR TOP TO FLOATING EYE
        // ---------------------------------------------------------------------
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 2.4f));
        VertexConsumer beamVc = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));
        float scrollV = -t * 0.045f;
        VfxRenderHelper.drawCylinder(beamVc, poseStack.last(), 10,
                0.81f, 0.24f, eyeY + 0.15f, 0.11f,
                scrollV, scrollV + 1.4f, pr, pg, pb, (int) (95 * pulse));
        poseStack.popPose();

        // ---------------------------------------------------------------------
        // 2. FLOATING 3D SPHERICAL MYTHOLOGICAL EYE (180-DEGREE SCANNING!)
        // ---------------------------------------------------------------------
        poseStack.pushPose();
        poseStack.translate(0.0, eyeY, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(eyeYawDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(eyePitchDeg));
        poseStack.scale(1.0f, blinkScaleY, 1.0f);

        // Solid crisp 3D textured Eyeball Sphere (front = glowing God Eye, back = armored runic shell)
        VertexConsumer eyeCutout = buffers.getBuffer(RenderType.entityCutoutNoCull(eyeTex));
        drawTexturedEyeSphere(eyeCutout, poseStack.last(), 0.295f, 255, 255, 255, 255);

        // Additive self-illuminated Eye Sheen so iris glows brightly even at midnight!
        VertexConsumer eyeGlow = buffers.getBuffer(RenderType.eyes(eyeTex));
        drawTexturedEyeSphere(eyeGlow, poseStack.last(), 0.303f,
                VfxRenderHelper.mixWhite(pr, 0.55f),
                VfxRenderHelper.mixWhite(pg, 0.55f),
                VfxRenderHelper.mixWhite(pb, 0.55f), 170);
        poseStack.popPose();

        // ---------------------------------------------------------------------
        // 3. OUTER EFFECTS AROUND THE EYE:
        //    (A) Two Counter-Rotating Tilted 3D Arcane Halo Rings (Gyroscope!)
        //    (B) Outer Translucent Energy Swirl Ring
        //    (C) Four Smoothly Orbiting 3D Glowing Crystal Shards!
        // ---------------------------------------------------------------------
        // Outer Gyroscope Arcane Ring #1 (translucent pass first)
        poseStack.pushPose();
        poseStack.translate(0.0, eyeY, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(28.0f + Mth.sin(t * 0.03f) * 8.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 3.1f));
        VertexConsumer ringTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.MAGIC_CIRCLE_TEX));
        VfxRenderHelper.drawHorizontalQuad(ringTrans, poseStack.last(), 0.0f, 0.72f * pulse, pr, pg, pb, 165);
        VertexConsumer ringGlow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.MAGIC_CIRCLE_TEX));
        VfxRenderHelper.drawHorizontalQuad(ringGlow, poseStack.last(), 0.005f, 0.70f * pulse,
                VfxRenderHelper.mixWhite(pr, 0.45f), VfxRenderHelper.mixWhite(pg, 0.45f), VfxRenderHelper.mixWhite(pb, 0.45f), 195);
        poseStack.popPose();

        // Inner Gyroscope Arcane Ring #2 (tilted -34 deg, counter-rotating faster in accent color — same ringGlow buffer!)
        poseStack.pushPose();
        poseStack.translate(0.0, eyeY, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(-34.0f + Mth.cos(t * 0.035f) * 10.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(-t * 4.5f));
        VfxRenderHelper.drawHorizontalQuad(ringGlow, poseStack.last(), 0.0f, 0.56f, ar, ag, ab, 185);
        poseStack.popPose();

        // Horizontal Outer Energy Swirl Halo underneath the Eye
        poseStack.pushPose();
        poseStack.translate(0.0, 1.02f + bob * 0.35f, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-t * 2.2f));
        VertexConsumer auraSwirl = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.AURA_SWIRL_TEX));
        VfxRenderHelper.drawHorizontalQuad(auraSwirl, poseStack.last(), 0.0f, 0.64f, pr, pg, pb, 130);
        poseStack.popPose();

        // 4 Smoothly Orbiting 3D Glowing Crystal Shards revolving outside the Eye!
        VertexConsumer shardVc = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.BEAM_TEX));
        for (int i = 0; i < 4; i++) {
            float orbitAngle = t * 0.055f + i * (Mth.PI * 0.5f);
            float orbitRad = 0.68f + Mth.sin(t * 0.08f + i * 1.5f) * 0.06f;
            float sx = Mth.cos(orbitAngle) * orbitRad;
            float sz = Mth.sin(orbitAngle) * orbitRad;
            float sy = eyeY + Mth.sin(t * 0.09f + i * 1.57f) * 0.16f;

            poseStack.pushPose();
            poseStack.translate(sx, sy, sz);
            poseStack.mulPose(Axis.YP.rotationDegrees(-t * 5.0f + i * 90.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(22.0f));
            boolean useAccent = (i % 2 == 1);
            VfxRenderHelper.drawOctahedron(shardVc, poseStack.last(), 0.075f,
                    useAccent ? ar : pr, useAccent ? ag : pg, useAccent ? ab : pb, 235);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * Builds a smooth 3D sphere centered at (0, 0, 0) where the front (+Z) hemisphere
     * cleanly projects the circular 32x32 Eye texture (u in [0..0.5]) and the rear (-Z)
     * hemisphere maps the dark runic eye-shell back (u in [0.5..1.0]).
     */
    private static void drawTexturedEyeSphere(VertexConsumer vc, PoseStack.Pose pose, float radius,
                                              int r, int g, int b, int a) {
        final int latSteps = 10;
        final int lonSteps = 16;
        for (int lat = 0; lat < latSteps; lat++) {
            float phi0 = -Mth.HALF_PI + (lat / (float) latSteps) * Mth.PI;
            float phi1 = -Mth.HALF_PI + ((lat + 1) / (float) latSteps) * Mth.PI;
            float y0 = Mth.sin(phi0), c0 = Mth.cos(phi0);
            float y1 = Mth.sin(phi1), c1 = Mth.cos(phi1);

            for (int lon = 0; lon < lonSteps; lon++) {
                float th0 = (lon / (float) lonSteps) * Mth.TWO_PI;
                float th1 = ((lon + 1) / (float) lonSteps) * Mth.TWO_PI;

                float nx00 = c0 * Mth.sin(th0), ny00 = y0, nz00 = c0 * Mth.cos(th0);
                float nx10 = c0 * Mth.sin(th1), ny10 = y0, nz10 = c0 * Mth.cos(th1);
                float nx11 = c1 * Mth.sin(th1), ny11 = y1, nz11 = c1 * Mth.cos(th1);
                float nx01 = c1 * Mth.sin(th0), ny01 = y1, nz01 = c1 * Mth.cos(th0);

                // Planar circular projection for crisp undistorted front iris (nz >= 0) vs back shell (nz < 0)
                boolean front = (nz00 + nz10 + nz11 + nz01) >= 0.0f;
                float uOff = front ? 0.0f : 0.5f;

                float u00 = uOff + Mth.clamp(0.25f + nx00 * 0.245f, 0.005f, 0.495f);
                float v00 = Mth.clamp(0.5f - ny00 * 0.49f, 0.005f, 0.995f);
                float u10 = uOff + Mth.clamp(0.25f + nx10 * 0.245f, 0.005f, 0.495f);
                float v10 = Mth.clamp(0.5f - ny10 * 0.49f, 0.005f, 0.995f);
                float u11 = uOff + Mth.clamp(0.25f + nx11 * 0.245f, 0.005f, 0.495f);
                float v11 = Mth.clamp(0.5f - ny11 * 0.49f, 0.005f, 0.995f);
                float u01 = uOff + Mth.clamp(0.25f + nx01 * 0.245f, 0.005f, 0.495f);
                float v01 = Mth.clamp(0.5f - ny01 * 0.49f, 0.005f, 0.995f);

                emit(vc, pose, nx00 * radius, ny00 * radius, nz00 * radius, u00, v00, nx00, ny00, nz00, r, g, b, a);
                emit(vc, pose, nx10 * radius, ny10 * radius, nz10 * radius, u10, v10, nx10, ny10, nz10, r, g, b, a);
                emit(vc, pose, nx11 * radius, ny11 * radius, nz11 * radius, u11, v11, nx11, ny11, nz11, r, g, b, a);
                emit(vc, pose, nx01 * radius, ny01 * radius, nz01 * radius, u01, v01, nx01, ny01, nz01, r, g, b, a);
            }
        }
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose pose,
                             float x, float y, float z, float u, float v,
                             float nx, float ny, float nz,
                             int r, int g, int b, int a) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}

