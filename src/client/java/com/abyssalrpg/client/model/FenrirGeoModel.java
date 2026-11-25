package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.FenrirBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * GeckoLib 4.8.3 GeoModel cho Boss Fenrir — Fel V8 (Thần Sói Bão Tố):
 * - Model 222 bone zero-bind-rotation (`geo/entity/fenrir_boss.geo.json`) chuẩn tạo hình Fel trắng xanh băng tuyết:
 *   TOÀN BỘ 70 sợi lông chỉa ra (đuôi 14 chùm x 2–3 khớp, 3 dải lưng x 5 khớp, bờm má x 4 khớp, bờm thân x 3 khớp,
 *   28 sợi lông phủ dọc 4 chân x 2 khớp) = 164 khớp lông riêng + 2 chóp tai (`ear_l_tip`, `ear_r_tip`) + 4 khớp mí mắt.
 * - QUY TẮC AN TOÀN TUYỆT ĐỐI GECKOLIB 4.8.3 (CHỐNG 100% LỖI QUAY VÒNG / TRÔI KHỚP):
 *   + MỌI biến đổi tùy chỉnh (`applyRealtimeFurWindSway`, `applyRealtimeEyeBlink`, `applyNeckTrack`) ĐỀU đặt giá trị
 *     TUYỆT ĐỐI (`setRotX/Y/Z`, `setPosX/Y/Z`), TUYỆT ĐỐI KHÔNG BAO GIỜ đọc cộng dồn (`getRot*() + d`, `getPosY() + d`)!
 *     (Vì trong GeckoLib 4.8.3, `GeoBone.setRot*` bật cờ `rotationChanged = true` sau khi `AnimationProcessor` đã reset cờ,
 *     nên khung hình sau bone không có keyframe sẽ không được reset về 0 — nếu cộng dồn sẽ quay vòng 360° vô hạn!)
 *   + Tách quyền điều khiển rạch ròi 0 trùng lặp:
 *     - `fenrir_boss.animation.json`: sở hữu thân, cột sống, 4 chân, hàm, gốc tai (`ear_l/r`), đuôi chính (`tail_1..8`),
 *       và cổ-đầu khi khóa animation.
 *     - `FenrirGeoModel`: sở hữu độc quyền 164 khớp lông đa khớp + 2 chóp tai (`ear_l_tip/r_tip`), 4 mí mắt (`eyelid_*`),
 *       và head-tracking tuyệt đối 5 khớp (`neck_1..4`, `head`) khi không bị khóa animation.
 */
public class FenrirGeoModel extends GeoModel<FenrirBossEntity> {
    private static final ResourceLocation MODEL = AbyssalRpgMod.id("geo/entity/fenrir_boss.geo.json");
    private static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/entity/fenrir_boss.animation.json");
    private static final ResourceLocation TEX_NORMAL = AbyssalRpgMod.id("textures/entity/fenrir_boss.png");
    private static final ResourceLocation TEX_ENRAGED = AbyssalRpgMod.id("textures/entity/fenrir_boss_enraged.png");

    private record FurSwaySpec(
            String bone,
            float phase,
            float freq,
            float ampXDeg,
            float ampYDeg,
            float ampZDeg,
            float runDriftXDeg,
            float deathDrapeXDeg,
            float maxAngleDeg
    ) {}

    private static final FurSwaySpec[] FUR_SWAY_SPECS = buildFurSwaySpecs();

    private static void addChain(List<FurSwaySpec> out, String prefix, int segments, float basePhase, float baseFreq,
                                 float ampXDeg, float ampYDeg, float ampZDeg, float runDriftDeg) {
        for (int i = 0; i < segments; i++) {
            String name = (i == 0) ? prefix : (prefix + "_" + (i + 1));
            float prog = (segments > 1) ? ((float) i / (float) (segments - 1)) : 0.0f;
            float gain = 0.56f + prog * 0.88f;
            float phase = basePhase + i * 0.64f;
            float freq = baseFreq + i * 0.14f;
            float drift = runDriftDeg * (0.45f + prog * 0.55f);
            float drape = 5.5f + i * 3.0f;
            out.add(new FurSwaySpec(name, phase, freq, ampXDeg * gain, ampYDeg * gain, ampZDeg * gain, drift, drape, 18.0f));
        }
    }

    private static FurSwaySpec[] buildFurSwaySpecs() {
        List<FurSwaySpec> list = new ArrayList<>(170);

        // 1. 14 chùm lông đuôi chỉa ra — MỖI CHÙM 2–3 KHỚP (31 khớp) truyền sóng uốn chữ S!
        //    (Thân đuôi chính `tail_1..8` do `fenrir_boss.animation.json` điều khiển độc quyền, không đụng nhau!)
        addChain(list, "tail_plume_1l", 2, 0.55f, 2.50f, 5.2f,  6.0f,  3.2f, 5.5f);
        addChain(list, "tail_plume_1r", 2, 0.90f, 2.50f, 5.2f, -6.0f, -3.2f, 5.5f);
        addChain(list, "tail_plume_2b", 3, 1.00f, 2.40f, 5.8f,  4.4f,  2.2f, 6.0f);
        addChain(list, "tail_plume_2l", 2, 1.20f, 2.60f, 5.6f,  6.4f,  3.4f, 5.5f);
        addChain(list, "tail_plume_2r", 2, 1.55f, 2.60f, 5.6f, -6.4f, -3.4f, 5.5f);
        addChain(list, "tail_plume_3b", 3, 1.65f, 2.45f, 6.2f,  4.8f,  2.4f, 6.0f);
        addChain(list, "tail_plume_3l", 2, 1.85f, 2.70f, 6.0f,  6.8f,  3.6f, 5.5f);
        addChain(list, "tail_plume_3r", 2, 2.20f, 2.70f, 6.0f, -6.8f, -3.6f, 5.5f);
        addChain(list, "tail_plume_4b", 3, 2.25f, 2.55f, 6.4f,  5.0f,  2.6f, 6.0f);
        addChain(list, "tail_plume_5l", 2, 2.55f, 2.80f, 6.2f,  7.0f,  3.6f, 5.5f);
        addChain(list, "tail_plume_5r", 2, 2.90f, 2.80f, 6.2f, -7.0f, -3.6f, 5.5f);
        addChain(list, "tail_plume_6b", 2, 2.95f, 2.90f, 6.8f,  5.6f,  2.8f, 6.0f);
        addChain(list, "tail_plume_8a", 2, 3.35f, 3.00f, 7.2f,  6.2f,  3.2f, 6.5f);
        addChain(list, "tail_plume_8b", 2, 3.65f, 3.05f, 6.8f, -6.0f, -3.0f, 6.5f);

        // 2. 3 dải lông gió lưng 5 khớp (15 khớp) + 3 mào lông vai 2 khớp (6 khớp)
        addChain(list, "mane_streamer_c", 5, 0.30f, 2.20f, 5.6f,  3.8f,  2.2f, 6.5f);
        addChain(list, "mane_streamer_l", 5, 0.60f, 2.25f, 5.4f,  4.2f,  2.6f, 6.5f);
        addChain(list, "mane_streamer_r", 5, 0.95f, 2.25f, 5.4f, -4.2f, -2.6f, 6.5f);
        addChain(list, "mane_crest_c",    2, 0.20f, 2.25f, 3.8f,  2.4f,  1.4f, 4.5f);
        addChain(list, "mane_crest_l",    2, 0.40f, 2.30f, 3.8f,  3.2f,  2.0f, 4.5f);
        addChain(list, "mane_crest_r",    2, 0.70f, 2.30f, 3.8f, -3.2f, -2.0f, 4.5f);

        // 3. Bờm má 4 khớp + chỏm đỉnh gáy, lọn má, yếm ngực & lông cằm 2 khớp
        addChain(list, "cheek_plume_l", 4, 0.25f, 2.50f, 4.2f,  5.6f,  3.0f, 5.0f);
        addChain(list, "cheek_plume_r", 4, 0.55f, 2.50f, 4.2f, -5.6f, -3.0f, 5.0f);
        addChain(list, "crown_tuft_l",  2, 0.40f, 2.65f, 4.0f,  4.6f,  2.4f, 4.0f);
        addChain(list, "crown_tuft_r",  2, 0.70f, 2.65f, 4.0f, -4.6f, -2.4f, 4.0f);
        addChain(list, "head_tuft_l",   2, 0.55f, 2.70f, 3.8f,  4.4f,  2.2f, 4.0f);
        addChain(list, "head_tuft_r",   2, 0.85f, 2.70f, 3.8f, -4.4f, -2.2f, 4.0f);
        addChain(list, "jaw_beard",     2, 0.55f, 2.65f, 4.6f,  2.6f,  1.4f, 4.0f);
        addChain(list, "bib_tuft_c",    2, 0.35f, 2.45f, 4.8f,  2.4f,  1.4f, 4.5f);
        addChain(list, "bib_tuft_l",    2, 0.65f, 2.50f, 4.6f,  3.2f,  2.0f, 4.5f);
        addChain(list, "bib_tuft_r",    2, 0.95f, 2.50f, 4.6f, -3.2f, -2.0f, 4.5f);

        // 4. Lọn lông bờm cổ, sườn, hông 3 khớp + lọn ngực & lưng hông 2 khớp
        addChain(list, "mane_collar_l", 3, 0.50f, 2.35f, 4.4f,  5.0f,  2.6f, 5.0f);
        addChain(list, "mane_collar_r", 3, 0.85f, 2.35f, 4.4f, -5.0f, -2.6f, 5.0f);
        addChain(list, "mane_flank_l",  3, 0.90f, 2.40f, 4.6f,  5.2f,  2.6f, 5.5f);
        addChain(list, "mane_flank_r",  3, 1.25f, 2.40f, 4.6f, -5.2f, -2.6f, 5.5f);
        addChain(list, "flank_tuft_l",  3, 1.30f, 2.45f, 4.8f,  5.4f,  2.8f, 5.5f);
        addChain(list, "flank_tuft_r",  3, 1.65f, 2.45f, 4.8f, -5.4f, -2.8f, 5.5f);
        addChain(list, "hip_tuft_l",    3, 1.80f, 2.50f, 5.0f,  5.6f,  2.8f, 5.5f);
        addChain(list, "hip_tuft_r",    3, 2.20f, 2.50f, 5.0f, -5.6f, -2.8f, 5.5f);
        addChain(list, "chest_wisp_l",  2, 0.45f, 2.55f, 4.2f,  4.4f,  2.2f, 4.5f);
        addChain(list, "chest_wisp_r",  2, 0.75f, 2.55f, 4.2f, -4.4f, -2.2f, 4.5f);
        addChain(list, "pelvis_wisp_l", 2, 1.60f, 2.55f, 4.4f,  4.8f,  2.4f, 4.5f);
        addChain(list, "pelvis_wisp_r", 2, 1.95f, 2.55f, 4.4f, -4.8f, -2.4f, 4.5f);

        // 5. 28 sợi lông phủ 4 chân — MỖI SỢI 2 KHỚP (56 khớp lông chân đung đưa mềm mại từng bước chạy!)
        addChain(list, "upper_fl_tuft", 2, 0.70f, 2.70f, 4.4f,  3.8f,  2.2f, 5.0f);
        addChain(list, "cuff_fl_a",     2, 0.95f, 2.80f, 5.2f,  4.4f,  2.6f, 5.5f);
        addChain(list, "cuff_fl_b",     2, 1.20f, 2.80f, 4.8f,  4.6f,  2.6f, 5.5f);
        addChain(list, "cuff_fl_c",     2, 1.40f, 2.80f, 4.6f,  4.2f,  2.4f, 5.5f);
        addChain(list, "forearm_fl_hi", 2, 1.35f, 2.90f, 5.2f,  4.2f,  2.4f, 5.5f);
        addChain(list, "forearm_fl_lo", 2, 1.65f, 2.95f, 5.4f,  4.4f,  2.4f, 5.5f);
        addChain(list, "ankle_fl_tuft", 2, 1.90f, 3.00f, 5.2f,  4.2f,  2.4f, 5.5f);

        addChain(list, "upper_fr_tuft", 2, 1.15f, 2.70f, 4.4f, -3.8f, -2.2f, 5.0f);
        addChain(list, "cuff_fr_a",     2, 1.45f, 2.80f, 5.2f, -4.4f, -2.6f, 5.5f);
        addChain(list, "cuff_fr_b",     2, 1.70f, 2.80f, 4.8f, -4.6f, -2.6f, 5.5f);
        addChain(list, "cuff_fr_c",     2, 1.90f, 2.80f, 4.6f, -4.2f, -2.4f, 5.5f);
        addChain(list, "forearm_fr_hi", 2, 1.85f, 2.90f, 5.2f, -4.2f, -2.4f, 5.5f);
        addChain(list, "forearm_fr_lo", 2, 2.15f, 2.95f, 5.4f, -4.4f, -2.4f, 5.5f);
        addChain(list, "ankle_fr_tuft", 2, 2.40f, 3.00f, 5.2f, -4.2f, -2.4f, 5.5f);

        addChain(list, "thigh_bl_tuft", 2, 1.55f, 2.70f, 4.4f,  3.8f,  2.2f, 5.0f);
        addChain(list, "cuff_bl_a",     2, 1.65f, 2.80f, 5.0f,  4.4f,  2.6f, 5.5f);
        addChain(list, "cuff_bl_b",     2, 1.85f, 2.85f, 5.2f,  4.4f,  2.6f, 5.5f);
        addChain(list, "cuff_bl_c",     2, 2.05f, 2.85f, 4.8f,  4.2f,  2.4f, 5.5f);
        addChain(list, "shin_bl_hi",    2, 2.15f, 2.90f, 5.2f,  4.2f,  2.4f, 5.5f);
        addChain(list, "shin_bl_lo",    2, 2.40f, 2.95f, 5.4f,  4.4f,  2.4f, 5.5f);
        addChain(list, "ankle_bl_tuft", 2, 2.60f, 3.00f, 5.2f,  4.2f,  2.4f, 5.5f);

        addChain(list, "thigh_br_tuft", 2, 2.00f, 2.70f, 4.4f, -3.8f, -2.2f, 5.0f);
        addChain(list, "cuff_br_a",     2, 2.15f, 2.80f, 5.0f, -4.4f, -2.6f, 5.5f);
        addChain(list, "cuff_br_b",     2, 2.35f, 2.85f, 5.2f, -4.4f, -2.6f, 5.5f);
        addChain(list, "cuff_br_c",     2, 2.55f, 2.85f, 4.8f, -4.2f, -2.4f, 5.5f);
        addChain(list, "shin_br_hi",    2, 2.65f, 2.90f, 5.2f, -4.2f, -2.4f, 5.5f);
        addChain(list, "shin_br_lo",    2, 2.90f, 2.95f, 5.4f, -4.4f, -2.4f, 5.5f);
        addChain(list, "ankle_br_tuft", 2, 3.10f, 3.00f, 5.2f, -4.2f, -2.4f, 5.5f);

        // 6. Chóp tai vẫy nhẹ tự nhiên theo gió (kẹp cứng ±5.5° tuyệt đối, không bao giờ quay vòng!)
        list.add(new FurSwaySpec("ear_l_tip", 0.60f, 2.40f, 2.4f,  1.2f,  2.2f, 0.0f, 0.0f, 5.5f));
        list.add(new FurSwaySpec("ear_r_tip", 1.35f, 2.40f, 2.4f, -1.2f, -2.2f, 0.0f, 0.0f, 5.5f));

        return list.toArray(new FurSwaySpec[0]);
    }

    @Override
    public ResourceLocation getModelResource(FenrirBossEntity animatable, GeoRenderer<FenrirBossEntity> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(FenrirBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FenrirBossEntity animatable, GeoRenderer<FenrirBossEntity> renderer) {
        return (animatable.isEnragedVisual() || animatable.hasEnteredPhase2() || animatable.isTransitioningPhase())
                ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(FenrirBossEntity animatable) {
        return getTextureResource(animatable, null);
    }

    @Override
    public ResourceLocation getAnimationResource(FenrirBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(FenrirBossEntity animatable, long instanceId,
                                    AnimationState<FenrirBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 1. Sóng gió thời gian thực TUYỆT ĐỐI (0 cộng dồn) cho 164 khớp lông đa khớp & 2 chóp tai
        applyRealtimeFurWindSway(animatable, animationState);

        // 2. Chớp mở mắt tự nhiên TUYỆT ĐỐI (0 cộng dồn) trên 4 khớp mí mắt (`eyelid_l/r_up/lo`)
        applyRealtimeEyeBlink(animatable, animationState);

        // 3. Head-tracking 5 khớp cổ-đầu TUYỆT ĐỐI (0 cộng dồn) — tự tắt hoàn toàn khi animation khóa đầu
        GeoBone neck1 = getAnimationProcessor().getBone(FenrirBossEntity.NECK_1_BONE);
        GeoBone neck2 = getAnimationProcessor().getBone(FenrirBossEntity.NECK_2_BONE);
        GeoBone neck3 = getAnimationProcessor().getBone(FenrirBossEntity.NECK_3_BONE);
        GeoBone neck4 = getAnimationProcessor().getBone(FenrirBossEntity.NECK_4_BONE);
        GeoBone head = getAnimationProcessor().getBone(FenrirBossEntity.HEAD_BONE);
        if (head != null) {
            head.setTrackingMatrices(true);
        }

        if (animatable.isHeadLockedByAnimation()) {
            animatable.stepClientHeadTracking(0.0f, 0.0f);
            return;
        }

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float targetYawDeg = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.netHeadYaw()), -38.0f, 38.0f) : 0.0f;
        float targetPitchDeg = (modelData != null) ? Mth.clamp(Mth.wrapDegrees(modelData.headPitch()), -24.0f, 24.0f) : 0.0f;

        animatable.stepClientHeadTracking(targetYawDeg, targetPitchDeg);
        float smYawRad = animatable.getClientTrackedHeadYawDeg() * Mth.DEG_TO_RAD;
        float smPitchRad = animatable.getClientTrackedHeadPitchDeg() * Mth.DEG_TO_RAD;

        // Nhịp thở nhẹ hòa trực tiếp vào pitch cổ-đầu (giá trị tuyệt đối 100%, không bao giờ cộng dồn!)
        float breathTime = (animatable.tickCount + animationState.getPartialTick()) * 0.05f;
        float breath = Mth.sin(breathTime * (Mth.TWO_PI / 4.0f));
        float breathPitchRad = breath * 1.1f * Mth.DEG_TO_RAD;

        applyNeckTrackAbsolute(neck1, smYawRad * 0.10f, smPitchRad * 0.10f + breathPitchRad * 0.6f, 7.0f, 6.0f);
        applyNeckTrackAbsolute(neck2, smYawRad * 0.15f, smPitchRad * 0.15f + breathPitchRad * 0.8f, 10.0f, 8.0f);
        applyNeckTrackAbsolute(neck3, smYawRad * 0.15f, smPitchRad * 0.15f - breathPitchRad * 0.4f, 12.0f, 10.0f);
        applyNeckTrackAbsolute(neck4, smYawRad * 0.20f, smPitchRad * 0.20f - breathPitchRad * 0.5f, 15.0f, 12.0f);
        applyNeckTrackAbsolute(head,  smYawRad * 0.40f, smPitchRad * 0.40f - breathPitchRad * 0.6f, 38.0f, 24.0f);
    }

    private static void applyNeckTrackAbsolute(GeoBone bone, float yawRad, float pitchRad,
                                               float maxYawDeg, float maxPitchDeg) {
        if (bone == null) return;
        bone.setRotY(Mth.clamp(yawRad, -maxYawDeg * Mth.DEG_TO_RAD, maxYawDeg * Mth.DEG_TO_RAD));
        bone.setRotX(Mth.clamp(pitchRad, -maxPitchDeg * Mth.DEG_TO_RAD, maxPitchDeg * Mth.DEG_TO_RAD));
        bone.setRotZ(0.0f);
    }

    private static float blinkPulse(float t, float start, float closeDur, float holdDur, float openDur) {
        float dt = t - start;
        if (dt <= 0.0f) return 0.0f;
        if (dt < closeDur) {
            float u = dt / closeDur;
            return u * u * (3.0f - 2.0f * u);
        }
        dt -= closeDur;
        if (dt < holdDur) {
            return 1.0f;
        }
        dt -= holdDur;
        if (dt < openDur) {
            float u = 1.0f - (dt / openDur);
            return u * u * (3.0f - 2.0f * u);
        }
        return 0.0f;
    }

    private void applyRealtimeEyeBlink(FenrirBossEntity animatable, AnimationState<FenrirBossEntity> animationState) {
        var proc = getAnimationProcessor();
        GeoBone lUp = proc.getBone(FenrirBossEntity.EYELID_L_UP_BONE);
        GeoBone lLo = proc.getBone(FenrirBossEntity.EYELID_L_LO_BONE);
        GeoBone rUp = proc.getBone(FenrirBossEntity.EYELID_R_UP_BONE);
        GeoBone rLo = proc.getBone(FenrirBossEntity.EYELID_R_LO_BONE);
        if (lUp == null && rUp == null) return;

        float partialTick = animationState.getPartialTick();
        float closeAmt;
        if (!animatable.isAlive()) {
            // Nhắm mắt êm ái trong ~0.5s khi boss ngã xuống
            closeAmt = Mth.clamp((animatable.deathTime + partialTick) / 10.0f, 0.0f, 1.0f);
        } else {
            float timeSec = (animatable.tickCount + partialTick) * 0.05f + ((animatable.getId() * 1.37f) % 4.2f);
            float cycleLen = 4.2f;
            int cycleIdx = (int) Math.floor(timeSec / cycleLen);
            float phase = timeSec - cycleIdx * cycleLen;

            // Nhịp chớp mắt sinh học tự nhiên: nhắm nhanh (0.09s) -> giữ nhẹ (0.04s) -> mở êm (0.13s), xen kẽ chớp kép!
            float blink = blinkPulse(phase, 0.12f, 0.09f, 0.04f, 0.13f);
            if (Math.floorMod(cycleIdx, 3) == 1) {
                blink = Math.max(blink, blinkPulse(phase, 0.56f, 0.08f, 0.03f, 0.12f));
            }

            // Biểu cảm nheo mắt uy nghiêm khi hú/tung chiêu & nheo phản xạ khi trúng đòn
            float squint = 0.0f;
            FenrirBossEntity.SkillState st = animatable.getSkillState();
            if (st == FenrirBossEntity.SkillState.HOWL || st == FenrirBossEntity.SkillState.PHASE_CHANGE) {
                squint = 0.24f;
                blink *= 0.25f;
            } else if (st == FenrirBossEntity.SkillState.CLAW_SLASH
                    || st == FenrirBossEntity.SkillState.BITE_LUNGE
                    || st == FenrirBossEntity.SkillState.POUNCE
                    || st == FenrirBossEntity.SkillState.TORNADO_SUMMON
                    || st == FenrirBossEntity.SkillState.WIND_BARRAGE) {
                squint = 0.16f;
            }
            float flinch = (animatable.hurtTime > 0)
                    ? Mth.clamp((animatable.hurtTime - partialTick) / 10.0f, 0.0f, 1.0f) * 0.65f
                    : 0.0f;

            closeAmt = Mth.clamp(Math.max(blink, Math.max(squint, flinch)), 0.0f, 1.0f);
        }

        // Đặt tọa độ tuyệt đối (bind posY = 0) — trượt mí trên xuống (-0.64px) & mí dưới lên (+0.28px) không bao giờ trôi!
        float upOffset = -0.64f * closeAmt;
        float loOffset = +0.28f * closeAmt;
        if (lUp != null) { lUp.setPosX(0.0f); lUp.setPosY(upOffset); lUp.setPosZ(0.0f); }
        if (rUp != null) { rUp.setPosX(0.0f); rUp.setPosY(upOffset); rUp.setPosZ(0.0f); }
        if (lLo != null) { lLo.setPosX(0.0f); lLo.setPosY(loOffset); lLo.setPosZ(0.0f); }
        if (rLo != null) { rLo.setPosX(0.0f); rLo.setPosY(loOffset); rLo.setPosZ(0.0f); }
    }

    private void applyRealtimeFurWindSway(FenrirBossEntity animatable, AnimationState<FenrirBossEntity> animationState) {
        float partialTick = animationState.getPartialTick();
        float timeSec = (animatable.tickCount + partialTick) * 0.05f
                + ((animatable.getId() * 0.73f) % Mth.TWO_PI);

        float deathBlend = animatable.isAlive()
                ? 0.0f
                : Mth.clamp((animatable.deathTime + partialTick) / 16.0f, 0.0f, 1.0f);
        float aliveFactor = 1.0f - deathBlend;

        float horizSpeed = animatable.isAlive()
                ? (float) animatable.getDeltaMovement().horizontalDistance()
                : 0.0f;
        float speedFactor = Mth.clamp(horizSpeed * 3.2f, 0.0f, 1.0f);

        float emergeFactor = animatable.isSpawningIn()
                ? (0.50f + 0.50f * animatable.getSpawnProgress(partialTick))
                : 1.0f;

        float windAmp = (0.95f + speedFactor * 0.55f) * emergeFactor * aliveFactor;
        float freqScale = 1.0f + speedFactor * 0.34f;

        if (animatable.isAlive()
                && (animatable.isEnragedVisual() || animatable.hasEnteredPhase2() || animatable.isTransitioningPhase())) {
            windAmp *= 1.24f;
            freqScale *= 1.15f;
        }
        FenrirBossEntity.SkillState st = animatable.getSkillState();
        if (animatable.isAlive() && (st == FenrirBossEntity.SkillState.TORNADO_SUMMON
                || st == FenrirBossEntity.SkillState.WIND_BARRAGE
                || st == FenrirBossEntity.SkillState.HOWL
                || st == FenrirBossEntity.SkillState.PHASE_CHANGE)) {
            windAmp *= 1.38f;
            freqScale *= 1.22f;
        }
        windAmp = Math.min(windAmp, 1.85f);

        var proc = getAnimationProcessor();
        for (FurSwaySpec spec : FUR_SWAY_SPECS) {
            GeoBone bone = proc.getBone(spec.bone());
            if (bone == null) continue;

            float w = spec.freq() * freqScale;
            float baseWave = Mth.sin(timeSec * w + spec.phase())
                    + 0.30f * Mth.sin(timeSec * (w * 1.71f) + spec.phase() * 1.42f);
            float crossWave = Mth.cos(timeSec * (w * 0.86f) + spec.phase() * 1.13f)
                    + 0.24f * Mth.sin(timeSec * (w * 1.49f) - spec.phase() * 0.68f);

            float maxRad = spec.maxAngleDeg() * Mth.DEG_TO_RAD;
            float driftDeg = spec.runDriftXDeg() * speedFactor * aliveFactor;
            float drapeDeg = spec.deathDrapeXDeg() * deathBlend;

            float rx = Mth.clamp((baseWave * spec.ampXDeg() * windAmp - driftDeg - drapeDeg) * Mth.DEG_TO_RAD, -maxRad, maxRad);
            float ry = Mth.clamp((crossWave * spec.ampYDeg() * windAmp) * Mth.DEG_TO_RAD, -maxRad, maxRad);
            float rz = Mth.clamp((crossWave * spec.ampZDeg() * windAmp + deathBlend * 3.5f) * Mth.DEG_TO_RAD, -maxRad, maxRad);

            // Gán TUYỆT ĐỐI (KHÔNG cộng dồn lên getRot*!) — toán học bất khả thi để quay vòng 360°!
            bone.setRotX(rx);
            bone.setRotY(ry);
            bone.setRotZ(rz);
        }
    }
}
