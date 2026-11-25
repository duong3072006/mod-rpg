package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.GeoArmorElbowSync;
import com.abyssalrpg.item.NightmareArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GeoModel 3D cho Bộ Giáp Nightmare Knight v4 (`geo/armor/nightmare_armor.geo.json`):
 * Điều khiển thống nhất hoàn toàn phía Client (0 packet, 0 server tick, 0 chồng chéo với `.animation.json`):
 * 1. Đôi cánh dơi ác quỷ 3 khớp (`wing_l/r_upper/mid/tip`) đối xứng gương 100% với tích phân pha đơn điệu.
 * 2. Áo choàng hoàng gia đỏ rách toàn lưng 3 đoạn (`cape_upper/mid/lower`) bay lượn mượt, chống xuyên chân.
 * 3. Tấm khố đỏ phía trước (`tabard_front`) đung đưa nhẹ theo bước chạy để không xuyên vào đùi.
 */
public final class NightmareArmorModel extends GeoModel<NightmareArmorItem> {
    public static final ResourceLocation GEO = AbyssalRpgMod.id("geo/armor/nightmare_armor.geo.json");
    public static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/armor/nightmare_armor.png");
    public static final ResourceLocation ANIM = AbyssalRpgMod.id("animations/armor/nightmare_armor.animation.json");

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;
    private static final float TWO_PI = Mth.TWO_PI;
    private static final float WING_MID_LAG = 0.28F;
    private static final float WING_TIP_LAG = 0.54F;

    private final Map<Entity, EntityAnimState> entityStates = new WeakHashMap<>();

    private static final class EntityAnimState {
        double lastTimeSec = Double.NaN;
        double wingPhase = 0.0;
        float smoothMove = 0.0F;
        float smoothAir = 0.0F;
        float smoothCrouch = 0.0F;
    }

    @Override
    public ResourceLocation getModelResource(NightmareArmorItem animatable, GeoRenderer<NightmareArmorItem> renderer) {
        return GEO;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(NightmareArmorItem animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(NightmareArmorItem animatable, GeoRenderer<NightmareArmorItem> renderer) {
        return TEXTURE;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(NightmareArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(NightmareArmorItem animatable) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(NightmareArmorItem animatable, long instanceId,
                                    AnimationState<NightmareArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        Entity entity = animationState.getData(DataTickets.ENTITY);
        // Đồng bộ khớp khuỷu găng tay giáp 1:1 với TogetsuBentArmRig (luôn gán tuyệt đối -> 0 lệch tay!)
        GeoArmorElbowSync.applyBentElbows(getAnimationProcessor(), entity);

        GeoBone capeUpper = getAnimationProcessor().getBone("cape_upper");
        GeoBone capeMid = getAnimationProcessor().getBone("cape_mid");
        GeoBone capeLower = getAnimationProcessor().getBone("cape_lower");
        GeoBone tabardFront = getAnimationProcessor().getBone("tabard_front");
        GeoBone chestCoreHeart = getAnimationProcessor().getBone("chest_core_heart");
        GeoBone wingRUpper = getAnimationProcessor().getBone("wing_r_upper");
        GeoBone wingRMid = getAnimationProcessor().getBone("wing_r_mid");
        GeoBone wingRTip = getAnimationProcessor().getBone("wing_r_tip");
        GeoBone wingLUpper = getAnimationProcessor().getBone("wing_l_upper");
        GeoBone wingLMid = getAnimationProcessor().getBone("wing_l_mid");
        GeoBone wingLTip = getAnimationProcessor().getBone("wing_l_tip");

        if (capeUpper == null && wingRUpper == null && wingLUpper == null && tabardFront == null && chestCoreHeart == null) {
            return;
        }

        float partialTick = animationState.getPartialTick();

        float walkSpeed = 0.0F;
        float sprintBoost = 0.0F;
        float fallLift = 0.0F;
        float crouchClearance = 0.0F;
        float lateralSway = 0.0F;
        float timeSec = partialTick / 20.0F;

        float move = 0.0F;
        float air = 0.0F;
        float crouch = 0.0F;
        float wingPhase = 0.0F;

        if (entity instanceof LivingEntity living) {
            timeSec = (living.tickCount + partialTick) / 20.0F;
            walkSpeed = Mth.clamp(living.walkAnimation.speed(partialTick), 0.0F, 1.0F);
            if (living.isSprinting()) {
                sprintBoost = 0.28F;
            }
            double vy = living.getDeltaMovement().y;
            if (!living.onGround() && vy < -0.05) {
                fallLift = (float) Mth.clamp(-vy * 0.42, 0.0, 0.42);
            }
            if (living.isCrouching()) {
                crouchClearance = 0.24F;
            }
            float bodyYawDelta = Mth.wrapDegrees(living.yBodyRot - living.yBodyRotO);
            lateralSway = Mth.clamp(bodyYawDelta * 0.012F, -0.22F, 0.22F);

            EntityAnimState st = this.entityStates.computeIfAbsent(living, e -> new EntityAnimState());
            double nowSec = timeSec;
            double dt;
            if (Double.isNaN(st.lastTimeSec)) {
                dt = 0.0;
                st.lastTimeSec = nowSec;
            } else {
                dt = nowSec - st.lastTimeSec;
                if (dt < -0.5 || dt > 1.0) {
                    dt = 0.0;
                    st.lastTimeSec = nowSec;
                } else if (dt > 0.0) {
                    dt = Math.min(dt, 0.08);
                    st.lastTimeSec = nowSec;
                } else {
                    dt = 0.0;
                }
            }

            float targetMove = Mth.clamp(walkSpeed * (living.isSprinting() ? 1.0F : 0.78F), 0.0F, 1.0F);
            float targetAir = living.onGround() ? 0.0F : 1.0F;
            float targetCrouch = living.isCrouching() ? 1.0F : 0.0F;
            if (dt > 0.0) {
                float alphaMove = 1.0F - (float) Math.exp(-dt / 0.22);
                float alphaAir = 1.0F - (float) Math.exp(-dt / 0.14);
                st.smoothMove = Mth.lerp(alphaMove, st.smoothMove, targetMove);
                st.smoothAir = Mth.lerp(alphaAir, st.smoothAir, targetAir);
                st.smoothCrouch = Mth.lerp(alphaMove, st.smoothCrouch, targetCrouch);

                float wingGroundOmega = Mth.lerp(st.smoothMove, TWO_PI / 3.4F, TWO_PI / 1.9F);
                float wingOmega = Mth.lerp(st.smoothAir, wingGroundOmega, TWO_PI / 1.2F);
                st.wingPhase = (st.wingPhase + dt * wingOmega) % (TWO_PI * 1024.0);
            }
            move = st.smoothMove;
            air = st.smoothAir;
            crouch = st.smoothCrouch;
            wingPhase = (float) st.wingPhase;
        }

        // 1. ĐÔI CÁNH DƠI ÁC QUỶ 3 KHỚP ĐỐI XỨNG GƯƠNG 100%
        float upperBaseFoldDeg = Mth.lerp(move, 11.0F, 18.0F) - air * 11.0F;
        float midBaseFoldDeg   = Mth.lerp(move,  3.5F,  6.5F) - air * 4.5F;
        float tipBaseFoldDeg   = Mth.lerp(move,  1.5F,  2.5F) - air * 2.0F;

        float upperBaseArchDeg = Mth.lerp(move, 9.5F, 11.0F) + air * 5.0F;
        float midBaseArchDeg   = Mth.lerp(move, 2.2F,  2.0F) + air * 2.0F;
        float tipBaseArchDeg   = 0.5F;

        float upperAmpDeg = Mth.lerp(air, Mth.lerp(move, 2.8F, 5.5F), 11.5F);
        float midAmpDeg   = Mth.lerp(air, Mth.lerp(move, 1.8F, 3.6F),  6.2F);
        float tipAmpDeg   = Mth.lerp(air, Mth.lerp(move, 1.5F, 3.8F),  5.8F);

        float sUpper = Mth.sin(wingPhase);
        float sMid   = Mth.sin(wingPhase - WING_MID_LAG);
        float sTip   = Mth.sin(wingPhase - WING_TIP_LAG);

        float upperFoldRad = (upperBaseFoldDeg + sUpper * upperAmpDeg) * DEG_TO_RAD;
        float midFoldRad   = (midBaseFoldDeg   + sMid   * midAmpDeg)   * DEG_TO_RAD;
        float tipFoldRad   = (tipBaseFoldDeg   + sTip   * tipAmpDeg)   * DEG_TO_RAD;

        float upperArchRad = (upperBaseArchDeg - sUpper * upperAmpDeg * 0.45F) * DEG_TO_RAD;
        float midArchRad   = (midBaseArchDeg   - sMid   * midAmpDeg   * 0.45F) * DEG_TO_RAD;
        float tipArchRad   = (tipBaseArchDeg   - sTip   * tipAmpDeg   * 0.50F) * DEG_TO_RAD;
        float wingPitchRad = (-2.0F - crouch * 4.0F) * DEG_TO_RAD;

        applyMirroredWing(wingLUpper, wingRUpper, upperFoldRad, upperArchRad, wingPitchRad);
        applyMirroredWing(wingLMid,   wingRMid,   midFoldRad,   midArchRad,   0.0F);
        applyMirroredWing(wingLTip,   wingRTip,   tipFoldRad,   tipArchRad,   0.0F);

        // 2. ÁO CHOÀNG ĐỎ HOÀNG GIA TOÀN LƯNG 3 ĐOẠN
        float windFreq = 2.8F + walkSpeed * 3.8F;
        float wave1 = Mth.sin(timeSec * windFreq) * (0.028F + walkSpeed * 0.045F);
        float wave2 = Mth.sin(timeSec * windFreq - 0.85F) * (0.042F + walkSpeed * 0.072F);
        float wave3 = Mth.sin(timeSec * windFreq - 1.70F) * (0.060F + walkSpeed * 0.100F);

        float baseLift = 0.05F + walkSpeed * 0.34F + sprintBoost + fallLift + crouchClearance;
        if (capeUpper != null) {
            capeUpper.setRotX(-(baseLift * 0.55F + Math.max(0.0F, wave1)));
            capeUpper.setRotZ(lateralSway * 0.40F + Mth.cos(timeSec * 2.0F) * 0.018F);
        }
        if (capeMid != null) {
            capeMid.setRotX(-(baseLift * 0.32F + wave2));
            capeMid.setRotZ(lateralSway * 0.50F + Mth.cos(timeSec * 2.0F - 0.7F) * 0.025F);
        }
        if (capeLower != null) {
            capeLower.setRotX(-(baseLift * 0.25F + wave3));
            capeLower.setRotZ(lateralSway * 0.60F + Mth.cos(timeSec * 2.0F - 1.4F) * 0.032F);
        }

        // 3. KHỐ ĐỎ MẶT TRƯỚC (đưa nhẹ về phía trước khi chạy/cúi để không xuyên vào đùi!)
        if (tabardFront != null) {
            float frontLift = 0.04F + walkSpeed * 0.26F + sprintBoost * 0.45F + crouchClearance * 0.55F
                    + Mth.sin(timeSec * windFreq * 1.1F) * (0.02F + walkSpeed * 0.06F);
            tabardFront.setRotX(frontLift);
            tabardFront.setRotZ(-lateralSway * 0.35F);
        }

        // 4. TRÁI TIM NIGHTMARE CORE TRONG LỒNG NGỰC MỞ — NHỊP ĐẬP "LUB-DUB" SỐNG ĐỘNG!
        if (chestCoreHeart != null) {
            float beatPeriod = Mth.lerp(move, 1.35F, 0.95F);
            float cycle = (timeSec % beatPeriod) / beatPeriod;
            float lub = (float) Math.exp(-Math.pow((cycle - 0.14F) / 0.055F, 2.0));
            float dub = (float) Math.exp(-Math.pow((cycle - 0.32F) / 0.065F, 2.0));
            float heartScale = 1.00F + lub * 0.11F + dub * 0.065F;
            chestCoreHeart.setScaleX(heartScale);
            chestCoreHeart.setScaleY(heartScale);
            chestCoreHeart.setScaleZ(1.00F + (heartScale - 1.00F) * 1.25F);
        }
    }

    private static void applyMirroredWing(GeoBone left, GeoBone right, float foldRad, float archRad, float pitchRad) {
        if (left != null) {
            left.setRotX(pitchRad);
            left.setRotY(foldRad);
            left.setRotZ(-archRad);
        }
        if (right != null) {
            right.setRotX(pitchRad);
            right.setRotY(-foldRad);
            right.setRotZ(archRad);
        }
    }
}
