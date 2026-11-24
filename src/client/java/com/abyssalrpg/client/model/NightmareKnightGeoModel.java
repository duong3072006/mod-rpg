package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel cho Boss Nightmare Knight:
 * - Đường dẫn chuẩn GeckoLib:
 *   + Geo: `assets/abyssalrpg/geo/entity/nightmare_knight.geo.json`
 *   + Animation: `assets/abyssalrpg/animations/entity/nightmare_knight.animation.json`
 *   + Texture Phase 1: `textures/entity/nightmare_knight.png` (+ `nightmare_knight_glowmask.png`)
 *   + Texture Phase 2: `textures/entity/nightmare_knight_enraged.png` (+ `nightmare_knight_enraged_glowmask.png`)
 * - Chống lỗi đầu quay nhiều vòng trong `setCustomAnimations`:
 *   + `netHeadYaw` & `headPitch` qua `Mth.wrapDegrees`, kẹp cứng `±45°` yaw, `±30°` pitch.
 *   + Đặt rotation tuyệt đối lên bone `head` (không `+=`), tắt hẳn khi `isHeadLockedByAnimation()`.
 */
public class NightmareKnightGeoModel extends GeoModel<NightmareKnightBossEntity> {
    private static final ResourceLocation MODEL = AbyssalRpgMod.id("geo/entity/nightmare_knight.geo.json");
    private static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/entity/nightmare_knight.animation.json");
    private static final ResourceLocation TEX_NORMAL = AbyssalRpgMod.id("textures/entity/nightmare_knight.png");
    private static final ResourceLocation TEX_ENRAGED = AbyssalRpgMod.id("textures/entity/nightmare_knight_enraged.png");

    @Override
    public ResourceLocation getModelResource(NightmareKnightBossEntity animatable, GeoRenderer<NightmareKnightBossEntity> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(NightmareKnightBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(NightmareKnightBossEntity animatable, GeoRenderer<NightmareKnightBossEntity> renderer) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(NightmareKnightBossEntity animatable) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    public ResourceLocation getAnimationResource(NightmareKnightBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(NightmareKnightBossEntity animatable, long instanceId,
                                    AnimationState<NightmareKnightBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone swordTip = getAnimationProcessor().getBone(NightmareKnightBossEntity.SWORD_TIP_BONE);
        if (swordTip != null) {
            swordTip.setTrackingMatrices(true);
        }

        GeoBone head = getAnimationProcessor().getBone(NightmareKnightBossEntity.HEAD_BONE);
        if (head == null || animatable.isHeadLockedByAnimation()) {
            animatable.stepClientHeadTracking(0.0f, 0.0f);
            return;
        }

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (modelData == null) {
            return;
        }

        float safeYawDeg = Mth.clamp(Mth.wrapDegrees(modelData.netHeadYaw()), -42.0f, 42.0f);
        float safePitchDeg = Mth.clamp(Mth.wrapDegrees(modelData.headPitch()), -28.0f, 28.0f);

        // Chống xoay đầu mượt bằng approachDegrees (stepAngleTowards) + đặt góc tuyệt đối kẹp cứng
        animatable.stepClientHeadTracking(safeYawDeg, safePitchDeg);
        head.setRotY(animatable.getClientTrackedHeadYawDeg() * Mth.DEG_TO_RAD);
        head.setRotX(animatable.getClientTrackedHeadPitchDeg() * Mth.DEG_TO_RAD);
    }
}

