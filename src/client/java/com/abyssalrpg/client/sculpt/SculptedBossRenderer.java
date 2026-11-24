package com.abyssalrpg.client.sculpt;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.renderer.vfx.VfxRenderHelper;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * 3D Skeletal Boss Renderer with per-entity smooth exponential animation blending,
 * emissive glowing eyes/jaws/fissures, Phase 2/3 swirling aura, hurt flash & death fall.
 */
public class SculptedBossRenderer<T extends AbstractAbyssalBoss> extends EntityRenderer<T> {
    private final SculptedBossRig<T> rig;
    private final ResourceLocation baseTexture;
    private final ResourceLocation glowTexture;
    private final ResourceLocation enragedBaseTexture;
    private final ResourceLocation enragedGlowTexture;
    private final int auraRgb;
    private final float baseModelScale;
    private final Map<Integer, BossAnimState> animStates = new HashMap<>();

    public SculptedBossRenderer(EntityRendererProvider.Context ctx, SculptedBossRig<T> rig,
                                String texKey, float shadowRadius, int auraRgb, float baseModelScale) {
        this(ctx, rig, texKey, null, shadowRadius, auraRgb, baseModelScale);
    }

    public SculptedBossRenderer(EntityRendererProvider.Context ctx, SculptedBossRig<T> rig,
                                String texKey, String enragedTexKey, float shadowRadius, int auraRgb, float baseModelScale) {
        super(ctx);
        this.rig = rig;
        this.baseTexture = AbyssalRpgMod.id("textures/entity/sculpted/" + texKey + ".png");
        this.glowTexture = AbyssalRpgMod.id("textures/entity/sculpted/" + texKey + "_glow.png");
        this.enragedBaseTexture = enragedTexKey != null ? AbyssalRpgMod.id("textures/entity/sculpted/" + enragedTexKey + ".png") : null;
        this.enragedGlowTexture = enragedTexKey != null ? AbyssalRpgMod.id("textures/entity/sculpted/" + enragedTexKey + "_glow.png") : null;
        this.shadowRadius = shadowRadius;
        this.auraRgb = auraRgb;
        this.baseModelScale = baseModelScale;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.distanceToSqr(camX, camY, camZ) < 180.0 * 180.0;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float ageInTicks = entity.tickCount + partialTick;
        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = Math.min(1.0f, entity.walkAnimation.speed(partialTick));

        if (animStates.size() > 128) {
            animStates.clear();
        }
        BossAnimState state = animStates.computeIfAbsent(entity.getId(), k -> new BossAnimState());
        state.update(entity, ageInTicks, partialTick, limbSwingAmount, bodyYaw);

        // Rotate local +Z (model forward) to match Minecraft entity bodyYaw!
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        // Subtle dynamic turn-banking roll & climbing/diving pitch when moving organically
        if (!entity.isSpawningIn() && entity.deathTime <= 0) {
            float bankDeg = state.turnRate * (entity.isFlyingBoss() ? 18.0f : 7.5f) * (0.35f + 0.65f * state.moveBlend);
            poseStack.mulPose(Axis.ZP.rotationDegrees(bankDeg));
            if (entity.isFlyingBoss()) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitchVel * 16.0f));
            }
        }

        // Smooth death fall-over
        if (entity.deathTime > 0) {
            float f = ((float) entity.deathTime + partialTick - 1.0f) / 20.0f * 1.6f;
            f = Math.min(1.0f, Mth.sqrt(Math.max(0f, f)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 90.0f));
        }

        float finalScale = entity.getScale() * baseModelScale;

        // Cinematic 3D Earth-Breaking Boss Emergence Animation:
        // Boss bursts UP from deep underground at FULL colossal titan scale with tectonic heave, skyward arch & awakening roar!
        float spawnAura = 0f;
        if (entity.isSpawningIn()) {
            float p = entity.getSpawnProgress(partialTick);

            // Phase A (0..0.20): Subterranean rumble & first crest breach
            // Phase B (0.20..0.72): Heavy dramatic S-curve heave rising out of the shattered ground
            // Phase C (0.72..1.00): Skyward heroic awakening roar & powerful settle (or sky launch for winged gods!)
            float emergeT = Mth.clamp(p / 0.72f, 0f, 1f);
            // Smooth cubic-quintic ease-out for weighty colossal inertia
            float heaveEase = emergeT * emergeT * (3f - 2f * emergeT);
            float roarPulse = (p > 0.64f && p < 0.96f) ? Mth.sin(((p - 0.64f) / 0.32f) * Mth.PI) : 0f;

            // Starts deep below ground (-3.4 * finalScale) and surges cleanly up to ground level (+ heroic roar crest!)
            float buryDepth = 3.4f * finalScale;
            float yRise = -(1f - heaveEase) * buryDepth + roarPulse * (entity.isFlyingBoss() ? 1.15f : 0.24f) * finalScale;

            // Intense seismic tremor while tearing through earth + roar vibration climax
            float earthStrain = (1f - emergeT) * (0.35f + emergeT * 0.65f);
            float tremorAmp = (earthStrain * 0.16f + roarPulse * 0.14f);
            float tremorX = Mth.sin(ageInTicks * 3.9f) * tremorAmp;
            float tremorZ = Mth.cos(ageInTicks * 3.3f) * tremorAmp;
            poseStack.translate(tremorX, yRise, tremorZ);

            // Dramatic upward sky-gaze pitch while surging vertically out of the ground -> levels cleanly -> rears back to roar!
            float breachPitch = (1f - heaveEase) * -28.0f;
            float roarArchPitch = roarPulse * -16.0f;
            poseStack.mulPose(Axis.XP.rotationDegrees(breachPitch + roarArchPitch));

            // Titanic left-right shoulder heave while pulling colossal mass free from the earth
            float heaveRoll = (1f - emergeT) * Mth.sin(emergeT * Mth.PI * 3.0f) * 8.5f;
            poseStack.mulPose(Axis.ZP.rotationDegrees(heaveRoll));

            // Keep full majestic titan scale (0.90 -> 1.06 roar crest -> 1.00) — NO tiny toy-shrinking!
            float titanScale = Mth.lerp(heaveEase, 0.90f, 1.0f) + roarPulse * 0.06f;
            finalScale *= titanScale;
            spawnAura = (0.35f + roarPulse * 0.65f) * (1.0f - p * 0.35f);
        }

        poseStack.scale(finalScale, finalScale, finalScale);

        // Drive live multi-joint skeletal bone animation with smooth interpolated blend weights!
        this.rig.root().resetPoseTree();
        this.rig.animate(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, state);

        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0f), OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));

        int phase = entity.getSyncedPhase();
        boolean useEnraged = phase >= 2 && this.enragedBaseTexture != null;
        ResourceLocation activeBaseTex = useEnraged ? this.enragedBaseTexture : this.baseTexture;
        ResourceLocation activeGlowTex = (useEnraged && this.enragedGlowTexture != null) ? this.enragedGlowTexture : this.glowTexture;

        // Pass 1: Crisp 3D Boss Body (auto-swaps to Phase 2 Enraged skin when enraged!)
        VertexConsumer baseVc = buffers.getBuffer(RenderType.entityCutoutNoCull(activeBaseTex));
        this.rig.root().render(poseStack, baseVc, packedLight, overlay, 255, 255, 255, 255, false);

        // Pass 2: Full-Bright Emissive Glowing Eyes, Fins, Gill-Ports, Molten Cracks & Jaws!
        VertexConsumer glowVc = buffers.getBuffer(RenderType.eyes(activeGlowTex));
        this.rig.root().render(poseStack, glowVc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255, true);

        // Pass 3: Phase 2 / 3, Spell-Casting & Blazing Summon Entrance Swirling Energy Aura!
        float auraStrength = Math.max(Math.max(phase >= 3 ? 0.78f : (phase >= 2 ? 0.52f : 0f), state.castBlend * 0.78f), spawnAura);
        if (auraStrength > 0.04f) {
            float pulse = auraStrength * (0.80f + 0.20f * Mth.sin(ageInTicks * 0.25f));
            int ar = Mth.clamp((int) (((auraRgb >> 16) & 0xFF) * pulse), 0, 255);
            int ag = Mth.clamp((int) (((auraRgb >> 8) & 0xFF) * pulse), 0, 255);
            int ab = Mth.clamp((int) ((auraRgb & 0xFF) * pulse), 0, 255);
            float u = (ageInTicks * 0.015f) % 1.0f;
            float v = (ageInTicks * 0.011f) % 1.0f;
            VertexConsumer auraVc = buffers.getBuffer(RenderType.energySwirl(VfxRenderHelper.AURA_SWIRL_TEX, u, v));
            poseStack.pushPose();
            poseStack.scale(1.028f, 1.028f, 1.028f);
            this.rig.root().render(poseStack, auraVc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ar, ag, ab, 255, false);
            poseStack.popPose();
        }

        poseStack.popPose();

        // Render Boss Nameplate above the colossal creature
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.baseTexture;
    }
}
