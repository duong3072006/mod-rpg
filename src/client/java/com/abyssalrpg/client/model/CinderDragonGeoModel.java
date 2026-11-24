package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel cho Boss Cinder Dragon — Hỏa Long Giáp Đá:
 * - Tự động đổi texture `cinder_dragon.png` (Phase 1) <-> `cinder_dragon_enraged.png` (Phase 2 "Vô Form").
 * - Theo dõi ma trận thực của locator `mouth_flame` phục vụ VFX khè lửa/tia lửa chuẩn vị trí họng.
 * - Head-tracking kẹp cứng tuyệt đối (`±42°` yaw, `±25°` pitch) trải đều qua `neck_1..3` + `head`,
 *   tự tắt hoàn toàn khi đang dùng skill / chuyển phase / chết để chống 100% lỗi xoay đầu nhiều vòng.
 */
public class CinderDragonGeoModel extends GeoModel<CinderDragonBossEntity> {
    private static final ResourceLocation MODEL = AbyssalRpgMod.id("geo/entity/cinder_dragon.geo.json");
    private static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/entity/cinder_dragon.animation.json");
    private static final ResourceLocation TEX_NORMAL = AbyssalRpgMod.id("textures/entity/cinder_dragon.png");
    private static final ResourceLocation TEX_ENRAGED = AbyssalRpgMod.id("textures/entity/cinder_dragon_enraged.png");

    @Override
    public ResourceLocation getModelResource(CinderDragonBossEntity animatable, GeoRenderer<CinderDragonBossEntity> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(CinderDragonBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CinderDragonBossEntity animatable, GeoRenderer<CinderDragonBossEntity> renderer) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(CinderDragonBossEntity animatable) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    public ResourceLocation getAnimationResource(CinderDragonBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(CinderDragonBossEntity animatable, long instanceId,
                                    AnimationState<CinderDragonBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone mouthFlame = getAnimationProcessor().getBone(CinderDragonBossEntity.MOUTH_FLAME_BONE);
        if (mouthFlame != null) {
            mouthFlame.setTrackingMatrices(true);
        }

        GeoBone n1 = getAnimationProcessor().getBone(CinderDragonBossEntity.NECK_1_BONE);
        GeoBone n2 = getAnimationProcessor().getBone(CinderDragonBossEntity.NECK_2_BONE);
        GeoBone n3 = getAnimationProcessor().getBone(CinderDragonBossEntity.NECK_3_BONE);
        GeoBone head = getAnimationProcessor().getBone(CinderDragonBossEntity.HEAD_BONE);
        if (head == null || animatable.isHeadLockedByAnimation()) {
            animatable.stepClientHeadTracking(0.0f, 0.0f);
            return;
        }

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        // Kẹp gọn góc nhìn đầu/cổ (±28° yaw, ±16° pitch) và tách pitch tập trung vào neck_3 + head
        // để triệt tiêu hoàn toàn hiện tượng nghiêng xoắn (parasitic roll) làm lệch 2 cặp sừng vương miện!
        float targetYaw = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.netHeadYaw()), -28.0f, 28.0f) : 0.0f;
        float targetPitch = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.headPitch()), -16.0f, 16.0f) : 0.0f;
        animatable.stepClientHeadTracking(targetYaw, targetPitch);

        float yawRad = animatable.getClientHeadYawDeg() * Mth.DEG_TO_RAD;
        float pitchRad = animatable.getClientHeadPitchDeg() * Mth.DEG_TO_RAD;

        if (n1 != null) {
            n1.setRotY(yawRad * 0.24f);
        }
        if (n2 != null) {
            n2.setRotY(yawRad * 0.24f);
        }
        if (n3 != null) {
            n3.setRotY(yawRad * 0.22f);
            n3.setRotX(pitchRad * 0.35f);
        }
        head.setRotY(yawRad * 0.30f);
        head.setRotX(pitchRad * 0.65f);
        head.setRotZ(0.0f);
    }
}

