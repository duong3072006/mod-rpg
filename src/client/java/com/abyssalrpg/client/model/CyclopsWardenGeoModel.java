package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.CyclopsWardenBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel V2 cho Boss Cyclops Warden (55 khớp nối mượt mà):
 * - 100% phép gán góc/tọa độ TUYỆT ĐỐI (KHÔNG bao giờ đọc cộng dồn lên `getRot*`/`getPos*` -> 0 lỗi quay vòng!).
 * - Chuỗi con lắc đa khớp trễ pha cho 3 đốt xích (`chain_1..3`) -> lồng sắt (`cage_body`) + cánh cửa bật mở (`cage_door`).
 * - Người tù trong lồng (`prisoner` -> `prisoner_head`) quay đầu nhìn xung quanh lo âu & tự ẩn ngay khi được giải thoát!
 * - 6 vạt khố da tua rua (`kilt_front_c/l/r`, `kilt_side_l/r`, `kilt_back`) phất phơ mềm mại theo bước chân & gió.
 * - Head-tracking 2 khớp mềm (`neck` 35% + `head` 65%) + chớp mở mắt Cyclops tự nhiên trên `eyelid_up/lo`.
 */
public class CyclopsWardenGeoModel extends GeoModel<CyclopsWardenBossEntity> {
    private static final ResourceLocation MODEL = AbyssalRpgMod.id("geo/entity/cyclops_warden.geo.json");
    private static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/entity/cyclops_warden.animation.json");
    private static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/entity/cyclops_warden.png");

    private record KiltSpec(String bone, float phase, float axisXSign, float axisZSign) {}

    private static final KiltSpec[] KILT_SPECS = {
            new KiltSpec(CyclopsWardenBossEntity.KILT_FRONT_C_BONE, 0.20f,  1.0f,  0.25f),
            new KiltSpec(CyclopsWardenBossEntity.KILT_FRONT_L_BONE, 0.85f,  1.0f, -0.55f),
            new KiltSpec(CyclopsWardenBossEntity.KILT_FRONT_R_BONE, 1.50f,  1.0f,  0.55f),
            new KiltSpec(CyclopsWardenBossEntity.KILT_SIDE_L_BONE,  2.15f,  0.35f, -1.0f),
            new KiltSpec(CyclopsWardenBossEntity.KILT_SIDE_R_BONE,  2.80f,  0.35f,  1.0f),
            new KiltSpec(CyclopsWardenBossEntity.KILT_BACK_BONE,    3.45f, -1.0f,  0.25f),
    };

    @Override
    public ResourceLocation getModelResource(CyclopsWardenBossEntity animatable, GeoRenderer<CyclopsWardenBossEntity> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(CyclopsWardenBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CyclopsWardenBossEntity animatable, GeoRenderer<CyclopsWardenBossEntity> renderer) {
        return TEXTURE;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(CyclopsWardenBossEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CyclopsWardenBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(CyclopsWardenBossEntity animatable, long instanceId,
                                    AnimationState<CyclopsWardenBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        float partialTick = animationState.getPartialTick();
        float timeSec = (animatable.tickCount + partialTick) * 0.05f + ((animatable.getId() * 0.91f) % Mth.TWO_PI);

        // 1. Con lắc xích 3 đốt + lồng sắt + cửa bật mở + người tù quay đầu lo âu (tuyệt đối 100%)
        applyChainCageAndPrisoner(animatable, partialTick, timeSec);

        // 2. 6 vạt khố da tua rua đung đưa mềm mại theo gió & nhịp bước chân (tuyệt đối 100%)
        applyLoinclothSway(animatable, partialTick, timeSec);

        // 3. Chớp mở con mắt Cyclops (tuyệt đối 100%)
        applyCyclopsEyeBlink(animatable, partialTick, timeSec);

        // 4. Head-tracking 2 khớp cổ-đầu mượt mà (`neck` 35% + `head` 65%) khi không bị khóa animation
        GeoBone neck = getAnimationProcessor().getBone(CyclopsWardenBossEntity.NECK_BONE);
        GeoBone head = getAnimationProcessor().getBone(CyclopsWardenBossEntity.HEAD_BONE);
        if (head == null || animatable.isHeadLockedByAnimation()) {
            animatable.stepClientHeadTracking(0.0f, 0.0f);
            return;
        }
        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float targetYaw = (data != null) ? Mth.clamp(Mth.wrapDegrees(data.netHeadYaw()), -34.0f, 34.0f) : 0.0f;
        float targetPitch = (data != null) ? Mth.clamp(Mth.wrapDegrees(data.headPitch()), -20.0f, 20.0f) : 0.0f;
        animatable.stepClientHeadTracking(targetYaw, targetPitch);

        float yawRad = animatable.getClientHeadYawDeg() * Mth.DEG_TO_RAD;
        float pitchRad = animatable.getClientHeadPitchDeg() * Mth.DEG_TO_RAD;
        if (neck != null) {
            neck.setRotY(yawRad * 0.35f);
            neck.setRotX(pitchRad * 0.35f);
            neck.setRotZ(0.0f);
        }
        head.setRotY(yawRad * 0.65f);
        head.setRotX(pitchRad * 0.65f);
        head.setRotZ(0.0f);
    }

    private void applyChainCageAndPrisoner(CyclopsWardenBossEntity animatable, float partialTick, float timeSec) {
        var proc = getAnimationProcessor();
        GeoBone ch1 = proc.getBone(CyclopsWardenBossEntity.CHAIN_1_BONE);
        GeoBone ch2 = proc.getBone(CyclopsWardenBossEntity.CHAIN_2_BONE);
        GeoBone ch3 = proc.getBone(CyclopsWardenBossEntity.CHAIN_3_BONE);
        GeoBone cage = proc.getBone(CyclopsWardenBossEntity.CAGE_BODY_BONE);
        GeoBone door = proc.getBone(CyclopsWardenBossEntity.CAGE_DOOR_BONE);
        GeoBone prisoner = proc.getBone(CyclopsWardenBossEntity.PRISONER_BONE);
        GeoBone prisHead = proc.getBone(CyclopsWardenBossEntity.PRISONER_HEAD_BONE);

        boolean released = animatable.isPrisonerReleased();
        if (prisoner != null) {
            prisoner.setHidden(released);
            if (!released) {
                float trembleY = Mth.sin(timeSec * 2.9f) * 3.0f * Mth.DEG_TO_RAD;
                float trembleZ = Mth.sin(timeSec * 5.2f) * 1.6f * Mth.DEG_TO_RAD;
                prisoner.setRotX(0.0f);
                prisoner.setRotY(trembleY);
                prisoner.setRotZ(trembleZ);
            }
        }
        if (prisHead != null && !released) {
            // Người tù ngó nghiêng lo âu tìm người cứu qua song sắt!
            float lookY = (Mth.sin(timeSec * 1.45f) * 14.0f + Mth.sin(timeSec * 3.1f) * 4.0f) * Mth.DEG_TO_RAD;
            float lookX = Mth.cos(timeSec * 1.9f) * 5.0f * Mth.DEG_TO_RAD;
            prisHead.setRotX(lookX);
            prisHead.setRotY(lookY);
            prisHead.setRotZ(0.0f);
        }

        if (door != null) {
            float openProg = animatable.getClientCageDoorOpenProgress(partialTick);
            door.setRotX(0.0f);
            door.setRotY(-112.0f * openProg * Mth.DEG_TO_RAD);
            door.setRotZ(0.0f);
        }

        float deathBlend = animatable.isAlive()
                ? 0.0f
                : Mth.clamp((animatable.deathTime + partialTick) / 14.0f, 0.0f, 1.0f);
        float alive = 1.0f - deathBlend;

        float speed = animatable.isAlive() ? (float) animatable.getDeltaMovement().horizontalDistance() : 0.0f;
        float speedFactor = Mth.clamp(speed * 4.0f, 0.0f, 1.0f);
        float skillBoost = (animatable.isAlive() && animatable.getSkillState() != CyclopsWardenBossEntity.SkillState.IDLE) ? 1.40f : 1.0f;
        float amp = (1.0f + speedFactor * 1.35f) * skillBoost * alive;

        // Sóng con lắc trễ pha mềm từ đốt xích 1 -> đốt 2 -> đốt 3 -> thân lồng sắt!
        GeoBone[] links = {ch1, ch2, ch3};
        for (int i = 0; i < links.length; i++) {
            GeoBone lk = links[i];
            if (lk == null) continue;
            float lag = i * 0.28f;
            float lx = Mth.clamp((Mth.sin(timeSec * 2.4f - lag) * 2.2f * amp + speedFactor * 2.4f * alive - deathBlend * 9.0f) * Mth.DEG_TO_RAD,
                    -10.0f * Mth.DEG_TO_RAD, 10.0f * Mth.DEG_TO_RAD);
            float lz = Mth.clamp((Mth.cos(timeSec * 2.1f - lag) * 2.6f * amp) * Mth.DEG_TO_RAD,
                    -10.0f * Mth.DEG_TO_RAD, 10.0f * Mth.DEG_TO_RAD);
            lk.setRotX(lx);
            lk.setRotY(0.0f);
            lk.setRotZ(lz);
        }

        if (cage != null) {
            float cageX = Mth.clamp((Mth.sin(timeSec * 2.4f - 0.90f) * 3.8f * amp - deathBlend * 34.0f) * Mth.DEG_TO_RAD,
                    -36.0f * Mth.DEG_TO_RAD, 14.0f * Mth.DEG_TO_RAD);
            float cageZ = Mth.clamp((Mth.cos(timeSec * 2.1f - 0.90f) * 4.4f * amp + deathBlend * 7.0f) * Mth.DEG_TO_RAD,
                    -14.0f * Mth.DEG_TO_RAD, 14.0f * Mth.DEG_TO_RAD);
            cage.setRotX(cageX);
            cage.setRotY(0.0f);
            cage.setRotZ(cageZ);
        }
    }

    private void applyLoinclothSway(CyclopsWardenBossEntity animatable, float partialTick, float timeSec) {
        float deathBlend = animatable.isAlive()
                ? 0.0f
                : Mth.clamp((animatable.deathTime + partialTick) / 14.0f, 0.0f, 1.0f);
        float alive = 1.0f - deathBlend;
        float speed = animatable.isAlive() ? (float) animatable.getDeltaMovement().horizontalDistance() : 0.0f;
        float speedFactor = Mth.clamp(speed * 4.2f, 0.0f, 1.0f);
        float amp = (2.6f + speedFactor * 7.8f) * alive;
        float freq = 2.6f + speedFactor * 2.8f;

        var proc = getAnimationProcessor();
        float maxRad = 16.0f * Mth.DEG_TO_RAD;
        for (KiltSpec spec : KILT_SPECS) {
            GeoBone bone = proc.getBone(spec.bone());
            if (bone == null) continue;
            float w = Mth.sin(timeSec * freq + spec.phase()) + 0.28f * Mth.cos(timeSec * (freq * 1.6f) - spec.phase());
            float flareOut = speedFactor * 5.0f * alive;
            float rx = Mth.clamp((w * amp * spec.axisXSign() + flareOut * spec.axisXSign()) * Mth.DEG_TO_RAD, -maxRad, maxRad);
            float rz = Mth.clamp((w * amp * 0.75f * spec.axisZSign() + flareOut * 0.65f * spec.axisZSign()) * Mth.DEG_TO_RAD, -maxRad, maxRad);
            bone.setRotX(rx);
            bone.setRotY(0.0f);
            bone.setRotZ(rz);
        }
    }

    private void applyCyclopsEyeBlink(CyclopsWardenBossEntity animatable, float partialTick, float timeSec) {
        var proc = getAnimationProcessor();
        GeoBone up = proc.getBone(CyclopsWardenBossEntity.EYELID_UP_BONE);
        GeoBone lo = proc.getBone(CyclopsWardenBossEntity.EYELID_LO_BONE);
        if (up == null && lo == null) return;

        float closeAmt;
        if (!animatable.isAlive()) {
            closeAmt = Mth.clamp((animatable.deathTime + partialTick) / 10.0f, 0.0f, 1.0f);
        } else {
            float cycleLen = 4.6f;
            float phase = timeSec % cycleLen;
            float blink = 0.0f;
            if (phase > 0.15f && phase < 0.42f) {
                float u = (phase - 0.15f) / 0.27f;
                blink = Mth.sin(u * Mth.PI);
            }
            float squint = (animatable.getSkillState() != CyclopsWardenBossEntity.SkillState.IDLE) ? 0.30f : 0.0f;
            float flinch = (animatable.hurtTime > 0)
                    ? Mth.clamp((animatable.hurtTime - partialTick) / 10.0f, 0.0f, 1.0f) * 0.70f
                    : 0.0f;
            closeAmt = Mth.clamp(Math.max(blink, Math.max(squint, flinch)), 0.0f, 1.0f);
        }

        if (up != null) { up.setPosX(0.0f); up.setPosY(-1.45f * closeAmt); up.setPosZ(0.0f); }
        if (lo != null) { lo.setPosX(0.0f); lo.setPosY(+0.95f * closeAmt); lo.setPosZ(0.0f); }
    }
}
