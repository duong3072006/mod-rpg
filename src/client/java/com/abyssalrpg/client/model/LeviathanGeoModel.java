package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.LeviathanBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib GeoModel chuẩn cho Boss Leviathan (2.5x Colossal + Chống quay đầu nhiều vòng):
 * - Model:      assets/abyssalrpg/geo/entity/leviathan.geo.json
 * - Animation:  assets/abyssalrpg/animations/entity/leviathan.animation.json
 * - Texture P1: assets/abyssalrpg/textures/entity/leviathan.png (+ leviathan_glowmask.png)
 * - Texture P2: assets/abyssalrpg/textures/entity/leviathan_enraged.png (+ leviathan_enraged_glowmask.png)
 *
 * SỬA LỖI XOAY ĐẦU NHIỀU VÒNG:
 * 1. Trước đây `netHeadYaw` không được wrapDegrees/clamp và bị cộng dồn (`neck.getRotY() + ...`),
 *    nên khi yaw tích lũy > 360° hoặc animation đang điều khiển bone đầu, đầu bị xoay liên tục nhiều vòng.
 * 2. Nay khóa hoàn toàn head-tracking khi boss đang chuyển Phase / Roar / Spawn / Death (để animation điều khiển),
 *    và khi bình thường dùng `Mth.wrapDegrees` + `Mth.clamp(±45° yaw, ±30° pitch)` đặt góc tuyệt đối (không cộng dồn).
 */
public class LeviathanGeoModel extends GeoModel<LeviathanBossEntity> {
    private static final ResourceLocation MODEL =
            AbyssalRpgMod.id("geo/entity/leviathan.geo.json");
    private static final ResourceLocation ANIMATION =
            AbyssalRpgMod.id("animations/entity/leviathan.animation.json");
    private static final ResourceLocation TEX_NORMAL =
            AbyssalRpgMod.id("textures/entity/leviathan.png");
    private static final ResourceLocation TEX_ENRAGED =
            AbyssalRpgMod.id("textures/entity/leviathan_enraged.png");

    @Override
    public ResourceLocation getModelResource(LeviathanBossEntity animatable, GeoRenderer<LeviathanBossEntity> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(LeviathanBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LeviathanBossEntity animatable, GeoRenderer<LeviathanBossEntity> renderer) {
        // Đọc cờ đồng bộ DataTracker `isEnragedVisual()` (giữ đúng cả khi reload world giữa Phase 2)
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(LeviathanBossEntity animatable) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    public ResourceLocation getAnimationResource(LeviathanBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(LeviathanBossEntity animatable, long instanceId,
                                    AnimationState<LeviathanBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone neck = getAnimationProcessor().getBone("neck");
        GeoBone head = getAnimationProcessor().getBone("head");
        GeoBone tailTip = getAnimationProcessor().getBone("tail_8");
        if (head != null) head.setTrackingMatrices(true);
        if (tailTip != null) tailTip.setTrackingMatrices(true);

        // Tắt hoàn toàn phần nhìn theo mục tiêu trên bone cổ/đầu khi animation đang chủ động điều khiển
        // (phase_change, roar_shockwave, spawn emergence, death) để tránh xung đột với keyframe animation!
        if (animatable.isHeadLockedByAnimation()) {
            return;
        }

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (modelData == null) return;

        // Chuẩn hóa góc về [-180, 180] trước khi kẹp ±45° yaw và ±30° pitch — ngăn tuyệt đối xoay nhiều vòng
        float safeNetYaw = Mth.clamp(Mth.wrapDegrees(modelData.netHeadYaw()), -45.0f, 45.0f);
        float safePitch = Mth.clamp(Mth.wrapDegrees(modelData.headPitch()), -30.0f, 30.0f);

        float headYawRad = safeNetYaw * Mth.DEG_TO_RAD;
        float headPitchRad = safePitch * Mth.DEG_TO_RAD;

        // Đặt góc tuyệt đối (KHÔNG cộng dồn `getRotY() + ...` mỗi frame)
        if (neck != null) {
            neck.setRotY(headYawRad * 0.35f);
            neck.setRotX(headPitchRad * 0.30f);
        }
        if (head != null) {
            head.setRotY(headYawRad * 0.55f);
            head.setRotX(headPitchRad * 0.45f);
        }

        // Đuôi bám trễ nhẹ theo góc cua (đã kẹp cứng để không bao giờ xoắn ngược)
        float tailLagYaw = Mth.clamp(-headYawRad * 0.14f, -0.28f, 0.28f);
        for (int i = 1; i <= 8; i++) {
            GeoBone tail = getAnimationProcessor().getBone("tail_" + i);
            if (tail != null) {
                tail.setRotY(tail.getRotY() + tailLagYaw * (0.35f + i * 0.06f));
            }
        }
    }
}
