package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.HydraBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel cho Boss Spirit Hydra 4 đầu:
 * - Ẩn toàn bộ nhánh `neck_i` (gồm 6 đốt cổ, `head_i`, `jaw_i`, `frill_i_l/r`, `horn_i`) và lộ `stump_i` khi đầu `i` bị chặt.
 * - Bật `setTrackingMatrices(true)` trên từng `head_1..4` và `stump_1..4` để Renderer cập nhật vị trí thế giới thật.
 * - Chống xoay đầu tuyệt đối: `wrapDegrees` + clamp riêng cho từng đầu (`±38°` yaw, `±26°` pitch), đặt góc tuyệt đối
 *   lên `head_i` (bind rotation = `[0,0,0]` dưới `head_orient_i`), và TẮT head-tracking của ĐÚNG đầu đang chạy skill!
 */
public class SpiritHydraGeoModel extends GeoModel<HydraBossEntity> {
    private static final ResourceLocation MODEL =
            AbyssalRpgMod.id("geo/entity/spirit_hydra.geo.json");
    private static final ResourceLocation ANIMATION =
            AbyssalRpgMod.id("animations/entity/spirit_hydra.animation.json");
    private static final ResourceLocation TEX_NORMAL =
            AbyssalRpgMod.id("textures/entity/spirit_hydra.png");
    private static final ResourceLocation TEX_ENRAGED =
            AbyssalRpgMod.id("textures/entity/spirit_hydra_enraged.png");

    @Override
    public ResourceLocation getModelResource(HydraBossEntity animatable, GeoRenderer<HydraBossEntity> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(HydraBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(HydraBossEntity animatable, GeoRenderer<HydraBossEntity> renderer) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(HydraBossEntity animatable) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    public ResourceLocation getAnimationResource(HydraBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(HydraBossEntity animatable, long instanceId,
                                    AnimationState<HydraBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float safeNetYawDeg = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.netHeadYaw()), -42.0f, 42.0f) : 0.0f;
        float safePitchDeg = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.headPitch()), -28.0f, 28.0f) : 0.0f;
        float time = animatable.tickCount + animationState.getPartialTick();

        for (int i = 0; i < HydraBossEntity.HEAD_COUNT; i++) {
            boolean alive = animatable.isHeadAlive(i);
            GeoBone neck = getAnimationProcessor().getBone(HydraBossEntity.NECK_BONES[i]);
            GeoBone head = getAnimationProcessor().getBone(HydraBossEntity.HEAD_BONES[i]);
            GeoBone stump = getAnimationProcessor().getBone(HydraBossEntity.STUMP_BONES[i]);

            // 1. Ẩn toàn bộ cổ-đầu bị chặt; lộ mỏm cổ cụt `stump_i` phát khói hồn
            if (neck != null) {
                neck.setHidden(!alive);
                neck.setChildrenHidden(!alive);
            }
            if (head != null) {
                head.setHidden(!alive);
                head.setTrackingMatrices(alive);
            }
            if (stump != null) {
                stump.setHidden(alive);
                stump.setTrackingMatrices(!alive);
            }

            if (!alive) {
                animatable.recordClientHeadDebugAngles(i, 0.0f, 0.0f);
                continue;
            }

            // 2. TẮT head-tracking trên ĐÚNG đầu đang chạy skill / cắn / phun / chuyển phase / stagger / chết!
            if (animatable.isHeadLockedByAnimation(i)) {
                if (head != null) {
                    animatable.recordClientHeadDebugAngles(i,
                            head.getRotY() * Mth.RAD_TO_DEG,
                            head.getRotX() * Mth.RAD_TO_DEG);
                }
                continue;
            }

            // 3. Tracking mượt + dao động tinh tế lệch pha (head_i có bind rotation = [0,0,0] nên đặt tuyệt đối an toàn 100%)
            float phase = i * 1.57f;
            float swayYawDeg = Mth.sin(time * 0.085f + phase) * 5.5f;
            float swayPitchDeg = Mth.cos(time * 0.105f + phase * 1.2f) * 3.5f;

            float headYawDeg = Mth.clamp(safeNetYawDeg * 0.65f + swayYawDeg, -38.0f, 38.0f);
            float headPitchDeg = Mth.clamp(safePitchDeg * 0.55f + swayPitchDeg, -26.0f, 26.0f);

            if (head != null) {
                head.setRotY(headYawDeg * Mth.DEG_TO_RAD);
                head.setRotX(headPitchDeg * Mth.DEG_TO_RAD);
            }
            animatable.recordClientHeadDebugAngles(i, headYawDeg, headPitchDeg);
        }
    }
}
