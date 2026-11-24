package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import com.abyssalrpg.entity.mythic.cerberus.CerberusForm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel cho Boss Cerberus v4:
 * - Chọn model theo form & config:
 *   + Phase 1 (`TRIPLE`): `geo/entity/cerberus.geo.json` (3 đầu hướng thẳng theo hình quạt chặt ±20°).
 *   + Phase 2 (`ALPHA_HEAD` / `FIRE_HEAD` / `SHADOW_HEAD`):
 *     - `cerberus_single.geo.json` (mặc định) có giáp đá bịt kín hai hốc vai.
 *     - Hoặc dùng chung `cerberus.geo.json` và tự ẩn `neck_1/head_1/jaw_1` & `neck_3/head_3/jaw_3`.
 * - MỌI bone trong `.geo.json` đều có `"rotation": [0, 0, 0]` (dáng đứng & góc quạt ±20° đúc thẳng vào cube),
 *   xóa sổ vĩnh viễn lỗi cộng dồn góc quay khiến đầu giữa dựng đứng lên trời & 2 đầu bên bẻ ngang 90°!
 * - Head-tracking mượt bằng `stepAngleTowards` trên từng thực thể + kẹp cứng tuyệt đối:
 *   + Đầu giữa (`head_2`): yaw `±35°`, pitch `±25°`.
 *   + Hai đầu bên (`head_1` / `head_3`): yaw `±18°` (quanh góc quạt gốc ±20°), pitch `±22°`.
 *   + TẮT hoàn toàn head-tracking trên đúng đầu đang chạy animation skill / cắn / phun lửa / chuyển phase / chết!
 */
public class CerberusGeoModel extends GeoModel<CerberusBossEntity> {
    private static final ResourceLocation MODEL_TRIPLE = AbyssalRpgMod.id("geo/entity/cerberus.geo.json");
    private static final ResourceLocation MODEL_SINGLE = AbyssalRpgMod.id("geo/entity/cerberus_single.geo.json");
    private static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/entity/cerberus.animation.json");

    private static final ResourceLocation TEX_NORMAL = AbyssalRpgMod.id("textures/entity/cerberus.png");
    private static final ResourceLocation TEX_ENRAGED = AbyssalRpgMod.id("textures/entity/cerberus_enraged.png");
    private static final ResourceLocation TEX_ALPHA = AbyssalRpgMod.id("textures/entity/cerberus_alpha.png");
    private static final ResourceLocation TEX_FIRE = AbyssalRpgMod.id("textures/entity/cerberus_fire.png");
    private static final ResourceLocation TEX_SHADOW = AbyssalRpgMod.id("textures/entity/cerberus_shadow.png");

    @Override
    public ResourceLocation getModelResource(CerberusBossEntity animatable, GeoRenderer<CerberusBossEntity> renderer) {
        if (animatable.getCerberusForm().isSplitSingle() && CerberusConfig.get().useSeparateSingleGeo) {
            return MODEL_SINGLE;
        }
        return MODEL_TRIPLE;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(CerberusBossEntity animatable) {
        return getModelResource(animatable, null);
    }

    @Override
    public ResourceLocation getTextureResource(CerberusBossEntity animatable, GeoRenderer<CerberusBossEntity> renderer) {
        CerberusForm form = animatable.getCerberusForm();
        return switch (form) {
            case ALPHA_HEAD -> TEX_ALPHA;
            case FIRE_HEAD -> TEX_FIRE;
            case SHADOW_HEAD -> TEX_SHADOW;
            case TRIPLE -> animatable.isEnragedVisual() || animatable.isTransitioningPhase() ? TEX_ENRAGED : TEX_NORMAL;
        };
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(CerberusBossEntity animatable) {
        return getTextureResource(animatable, null);
    }

    @Override
    public ResourceLocation getAnimationResource(CerberusBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(CerberusBossEntity animatable, long instanceId,
                                    AnimationState<CerberusBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CerberusForm form = animatable.getCerberusForm();
        boolean singleMode = form.isSplitSingle();
        boolean resetNow = animatable.consumePendingClientHeadReset();

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float safeNetYawDeg = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.netHeadYaw()), -35.0f, 35.0f) : 0.0f;
        float safePitchDeg = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.headPitch()), -25.0f, 25.0f) : 0.0f;

        for (int i = 0; i < CerberusBossEntity.HEAD_COUNT; i++) {
            boolean visible = !singleMode || (i == CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX);
            GeoBone neck = getAnimationProcessor().getBone(CerberusBossEntity.NECK_BONES[i]);
            GeoBone upperNeck = getAnimationProcessor().getBone(CerberusBossEntity.UPPER_NECK_BONES[i]);
            GeoBone head = getAnimationProcessor().getBone(CerberusBossEntity.HEAD_BONES[i]);
            GeoBone jaw = getAnimationProcessor().getBone(CerberusBossEntity.JAW_BONES[i]);

            if (neck != null) {
                neck.setHidden(!visible);
                neck.setChildrenHidden(!visible);
            }
            if (upperNeck != null) {
                upperNeck.setHidden(!visible);
                if (resetNow) {
                    upperNeck.setRotX(0.0f);
                    upperNeck.setRotY(0.0f);
                    upperNeck.setRotZ(0.0f);
                }
            }
            if (head != null) {
                head.setHidden(!visible);
                head.setTrackingMatrices(visible);
                if (resetNow) {
                    head.setRotX(0.0f);
                    head.setRotY(0.0f);
                    head.setRotZ(0.0f);
                }
            }
            if (jaw != null) {
                jaw.setHidden(!visible);
                if (resetNow) {
                    jaw.setRotX(0.0f);
                }
            }

            if (!visible) {
                animatable.resetClientHeadTracking(i);
                continue;
            }

            // Khi đầu đang bị khóa bởi animation skill / cắn / phun lửa / chuyển phase / chết:
            // nhường 100% quyền điều khiển cho GeckoLib animation (không ghi đè góc cổ/đầu!).
            if (animatable.isHeadLockedByAnimation(i)) {
                animatable.stepClientHeadTracking(i, 0.0f, 0.0f, 35.0f, 25.0f);
                continue;
            }

            boolean isCenter = (i == CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX);
            float maxYawDeg = isCenter ? 35.0f : 18.0f;
            float maxPitchDeg = isCenter ? 25.0f : 22.0f;

            float desiredYawDeg = Mth.clamp(safeNetYawDeg * (isCenter ? 1.0f : 0.55f), -maxYawDeg, maxYawDeg);
            float desiredPitchDeg = Mth.clamp(safePitchDeg * (isCenter ? 0.90f : 0.72f), -maxPitchDeg, maxPitchDeg);

            animatable.stepClientHeadTracking(i, desiredYawDeg, desiredPitchDeg, maxYawDeg, maxPitchDeg);
            float smYawDeg = animatable.getClientTrackedHeadYawDeg(i);
            float smPitchDeg = animatable.getClientTrackedHeadPitchDeg(i);

            // Chia tỉ lệ mượt 35% lên đốt cổ trên (`neck_i_upper`) + 65% lên sọ (`head_i`), kẹp cứng tuyệt đối
            // trên nền bind rotation = [0, 0, 0] để vừa giữ nhịp thở `idle` vừa KHÔNG BAO GIỜ gập ngược hay bẻ ngang!
            float maxUpperYawRad = (maxYawDeg * 0.45f) * Mth.DEG_TO_RAD;
            float maxUpperPitchRad = (maxPitchDeg * 0.45f) * Mth.DEG_TO_RAD;
            float maxHeadYawRad = maxYawDeg * Mth.DEG_TO_RAD;
            float maxHeadPitchRad = maxPitchDeg * Mth.DEG_TO_RAD;

            if (upperNeck != null) {
                upperNeck.setRotY(Mth.clamp(upperNeck.getRotY() + smYawDeg * 0.35f * Mth.DEG_TO_RAD,
                        -maxUpperYawRad, maxUpperYawRad));
                upperNeck.setRotX(Mth.clamp(upperNeck.getRotX() + smPitchDeg * 0.35f * Mth.DEG_TO_RAD,
                        -maxUpperPitchRad, maxUpperPitchRad));
            }
            if (head != null) {
                head.setRotY(Mth.clamp(head.getRotY() + smYawDeg * 0.65f * Mth.DEG_TO_RAD,
                        -maxHeadYawRad, maxHeadYawRad));
                head.setRotX(Mth.clamp(head.getRotX() + smPitchDeg * 0.65f * Mth.DEG_TO_RAD,
                        -maxHeadPitchRad, maxHeadPitchRad));
            }
        }
    }
}
