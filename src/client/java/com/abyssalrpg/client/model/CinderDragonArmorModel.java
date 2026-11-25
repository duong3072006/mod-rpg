package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.GeoArmorElbowSync;
import com.abyssalrpg.item.CinderDragonArmorItem;
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
 * GeoModel 3D cho Bộ Giáp Cinder Dragon (`geo/armor/cinder_dragon_armor.geo.json`).
 *
 * Nguồn điều khiển animation DUY NHẤT (0 áp chồng với `.animation.json`, 0 tick phía server):
 * 1. Đôi cánh 3 khớp (`wing_l/r_upper/mid/tip` gắn trực tiếp lên `armorBody`):
 *    - Khóa bất biến đối xứng gương tuyệt đối: `rotY_L == -rotY_R`, `rotZ_L == -rotZ_R`, `rotX_L == +rotX_R`.
 *    - Dáng nghỉ tự nhiên gập ra sau ~32°–35° (20° hình học + 12° gốc + 3° giữa) & vồng lên ~12°–13°,
 *      khi chạy khép thêm ~10° lướt gió, khi nhảy/rơi dang rộng & vỗ mạnh ±20°.
 *    - Tích phân pha đơn điệu (`wingPhase += dt * omega`) loại bỏ hoàn toàn lỗi nổ pha `sin(t * freq(t))`!
 * 2. Đuôi rồng 5 đốt (`tail_1..tail_4` -> `tail_tip`):
 *    - Sóng truyền pha mượt (`phaseLag = 0.55 rad` mỗi đốt) với tích phân pha đơn điệu (`idle ~3.2s`, `run ~1.5s`).
 *    - Giới hạn biên độ từng đốt (`idle ±3°–5°`, `run ±6°–8.5°`, `tail_tip ±3.5°–6°`) -> không gập móc câu chữ J!
 *    - Pitch âm (`rotX < 0`) nâng nhẹ đuôi lên khi chạy/nhảy/ngồi để không chạm đất hay xuyên chân.
 */
public final class CinderDragonArmorModel extends GeoModel<CinderDragonArmorItem> {
    public static final ResourceLocation GEO = AbyssalRpgMod.id("geo/armor/cinder_dragon_armor.geo.json");
    public static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/armor/cinder_dragon_armor.png");
    public static final ResourceLocation ANIM = AbyssalRpgMod.id("animations/armor/cinder_dragon_armor.animation.json");

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;
    private static final float TWO_PI = Mth.TWO_PI;
    private static final float WING_MID_LAG = 0.25F;
    private static final float WING_TIP_LAG = 0.50F;
    private static final float TAIL_PHASE_LAG = 0.55F;

    /** Trạng thái tích phân pha mượt phía Client cho từng entity (WeakHashMap tự giải phóng, 0 rò rỉ bộ nhớ). */
    private final Map<Entity, EntityAnimState> entityStates = new WeakHashMap<>();

    private static final class EntityAnimState {
        double lastTimeSec = Double.NaN;
        double wingPhase = 0.0;
        double tailPhase = 0.0;
        float smoothMove = 0.0F;
        float smoothAir = 0.0F;
        float smoothCrouch = 0.0F;
        float smoothYawLag = 0.0F;
    }

    @Override
    public ResourceLocation getModelResource(CinderDragonArmorItem animatable, GeoRenderer<CinderDragonArmorItem> renderer) {
        return GEO;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(CinderDragonArmorItem animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(CinderDragonArmorItem animatable, GeoRenderer<CinderDragonArmorItem> renderer) {
        return TEXTURE;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(CinderDragonArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CinderDragonArmorItem animatable) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(CinderDragonArmorItem animatable, long instanceId,
                                    AnimationState<CinderDragonArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        Entity entity = animationState.getData(DataTickets.ENTITY);
        GeoArmorElbowSync.applyBentElbows(getAnimationProcessor(), entity);

        GeoBone wingRUpper = getAnimationProcessor().getBone("wing_r_upper");
        GeoBone wingRMid = getAnimationProcessor().getBone("wing_r_mid");
        GeoBone wingRTip = getAnimationProcessor().getBone("wing_r_tip");
        GeoBone wingLUpper = getAnimationProcessor().getBone("wing_l_upper");
        GeoBone wingLMid = getAnimationProcessor().getBone("wing_l_mid");
        GeoBone wingLTip = getAnimationProcessor().getBone("wing_l_tip");
        GeoBone tail1 = getAnimationProcessor().getBone("tail_1");
        GeoBone tail2 = getAnimationProcessor().getBone("tail_2");
        GeoBone tail3 = getAnimationProcessor().getBone("tail_3");
        GeoBone tail4 = getAnimationProcessor().getBone("tail_4");
        GeoBone tailTip = getAnimationProcessor().getBone("tail_tip");
        if (wingRUpper == null && wingLUpper == null && tail1 == null) {
            return;
        }

        float partialTick = animationState.getPartialTick();

        float move = 0.0F;
        float air = 0.0F;
        float crouch = 0.0F;
        float yawLag = 0.0F;
        float wingPhase = 0.0F;
        float tailPhase = 0.0F;

        if (entity instanceof LivingEntity living) {
            EntityAnimState st = this.entityStates.computeIfAbsent(living, e -> new EntityAnimState());
            double nowSec = (living.tickCount + partialTick) / 20.0;
            double dt;
            if (Double.isNaN(st.lastTimeSec)) {
                dt = 0.0;
                st.lastTimeSec = nowSec;
            } else {
                dt = nowSec - st.lastTimeSec;
                if (dt < -0.5 || dt > 1.0) {
                    // Dịch chuyển / đổi thế giới / tạm dừng lâu -> đồng bộ lại mốc thời gian không nhảy pha
                    dt = 0.0;
                    st.lastTimeSec = nowSec;
                } else if (dt > 0.0) {
                    dt = Math.min(dt, 0.08);
                    st.lastTimeSec = nowSec;
                } else {
                    // Cùng frame (nhiều mảnh giáp gọi liên tiếp trên 1 entity) -> giữ nguyên pha đồng nhất
                    dt = 0.0;
                }
            }

            float rawWalk = Mth.clamp(living.walkAnimation.speed(partialTick), 0.0F, 1.0F);
            float targetMove = Mth.clamp(rawWalk * (living.isSprinting() ? 1.0F : 0.78F), 0.0F, 1.0F);
            float targetAir = living.onGround() ? 0.0F : 1.0F;
            float targetCrouch = living.isCrouching() ? 1.0F : 0.0F;
            float bodyYawDelta = Mth.wrapDegrees(living.yBodyRot - living.yBodyRotO);
            float targetYawLag = Mth.clamp(bodyYawDelta * 0.0022F, -0.045F, 0.045F);

            if (dt > 0.0) {
                float alphaMove = 1.0F - (float) Math.exp(-dt / 0.22);
                float alphaAir = 1.0F - (float) Math.exp(-dt / 0.14);
                float alphaYaw = 1.0F - (float) Math.exp(-dt / 0.18);
                st.smoothMove = Mth.lerp(alphaMove, st.smoothMove, targetMove);
                st.smoothAir = Mth.lerp(alphaAir, st.smoothAir, targetAir);
                st.smoothCrouch = Mth.lerp(alphaMove, st.smoothCrouch, targetCrouch);
                st.smoothYawLag = Mth.lerp(alphaYaw, st.smoothYawLag, targetYawLag);

                // Tần số góc tích phân mượt (KHÔNG nhân thẳng time * freq(t)!):
                // - Cánh: idle T=3.2s (1.96 rad/s), walk/sprint T=1.8s (3.49 rad/s), air T=1.15s (5.46 rad/s)
                float wingGroundOmega = Mth.lerp(st.smoothMove, TWO_PI / 3.2F, TWO_PI / 1.8F);
                float wingOmega = Mth.lerp(st.smoothAir, wingGroundOmega, TWO_PI / 1.15F);
                st.wingPhase = (st.wingPhase + dt * wingOmega) % (TWO_PI * 1024.0);

                // - Đuôi: idle T=3.2s (1.96 rad/s), walk/sprint T=1.5s (4.19 rad/s) -> chậm đầm >= 2.5x bản cũ!
                float tailOmega = Mth.lerp(st.smoothMove, TWO_PI / 3.2F, TWO_PI / 1.5F);
                st.tailPhase = (st.tailPhase + dt * tailOmega) % (TWO_PI * 1024.0);
            }

            move = st.smoothMove;
            air = st.smoothAir;
            crouch = st.smoothCrouch;
            yawLag = st.smoothYawLag;
            wingPhase = (float) st.wingPhase;
            tailPhase = (float) st.tailPhase;
        }

        // ==================== 1. ĐÔI CÁNH RỒNG 3 KHỚP — ĐỐI XỨNG GƯƠNG TUYỆT ĐỐI 100% ====================
        // Quy ước trục GeckoLib 4.8.3 đã kiểm chứng toán học trên `BakedModelFactory` + `RenderUtil`:
        // - Gập ra sau (+Z): `wing_l.rotY = +fold`, `wing_r.rotY = -fold` (`rotY_L == -rotY_R`)
        // - Vồng lên trên (+Y): `wing_l.rotZ = -arch`, `wing_r.rotZ = +arch` (`rotZ_L == -rotZ_R`)
        // - Nghiêng pitch: cùng dấu cho cả 2 cánh (`rotX_L == +rotX_R`)
        float upperBaseFoldDeg = Mth.lerp(move, 12.0F, 19.0F) - air * 12.0F; // +20° geo = 32° idle, 39° sprint, 20° air-spread
        float midBaseFoldDeg   = Mth.lerp(move,  3.5F,  6.5F) - air * 4.5F;
        float tipBaseFoldDeg   = Mth.lerp(move,  1.5F,  2.5F) - air * 2.0F;

        float upperBaseArchDeg = Mth.lerp(move, 10.5F, 11.5F) + air * 5.0F;
        float midBaseArchDeg   = Mth.lerp(move,  2.5F,  2.0F) + air * 2.0F;
        float tipBaseArchDeg   = 0.5F;

        // Biên độ vẫy theo trạng thái: idle ±4.8° (tổng nhẹ nhàng), run ±10.0°, air ±20.0°
        float upperAmpDeg = Mth.lerp(air, Mth.lerp(move, 3.0F, 6.0F), 12.0F);
        float midAmpDeg   = Mth.lerp(air, Mth.lerp(move, 1.8F, 3.8F),  6.5F);
        float tipAmpDeg   = Mth.lerp(air, Mth.lerp(move, 1.6F, 4.0F),  6.0F);

        float sUpper = Mth.sin(wingPhase);
        float sMid   = Mth.sin(wingPhase - WING_MID_LAG);
        float sTip   = Mth.sin(wingPhase - WING_TIP_LAG);

        float upperFoldRad = (upperBaseFoldDeg + sUpper * upperAmpDeg) * DEG_TO_RAD;
        float midFoldRad   = (midBaseFoldDeg   + sMid   * midAmpDeg)   * DEG_TO_RAD;
        float tipFoldRad   = (tipBaseFoldDeg   + sTip   * tipAmpDeg)   * DEG_TO_RAD;

        // Vồng nhẹ đồng pha với nhịp vỗ cánh (cánh mở ra -> vồng nâng lên, cánh khép sau -> hạ nhẹ)
        float upperArchRad = (upperBaseArchDeg - sUpper * upperAmpDeg * 0.45F) * DEG_TO_RAD;
        float midArchRad   = (midBaseArchDeg   - sMid   * midAmpDeg   * 0.45F) * DEG_TO_RAD;
        float tipArchRad   = (tipBaseArchDeg   - sTip   * tipAmpDeg   * 0.50F) * DEG_TO_RAD;

        float wingPitchRad = (-2.0F - crouch * 4.0F) * DEG_TO_RAD;

        applyMirroredWing(wingLUpper, wingRUpper, upperFoldRad, upperArchRad, wingPitchRad);
        applyMirroredWing(wingLMid,   wingRMid,   midFoldRad,   midArchRad,   0.0F);
        applyMirroredWing(wingLTip,   wingRTip,   tipFoldRad,   tipArchRad,   0.0F);

        // ==================== 2. ĐUÔI RỒNG 5 ĐỐT — SÓNG TRUYỀN PHA MƯỢT, KHÔNG BẺ CHỮ J ====================
        // Biên độ giới hạn chặt từng đốt:
        // - idle: ±3.0°, ±3.6°, ±4.2°, ±4.8°, tail_tip ±3.5°
        // - walk/sprint: ±6.0°, ±7.0°, ±8.0°, ±8.5°, tail_tip ±6.0°
        // Pitch âm (`rotX < 0`) nâng đuôi nhẹ khỏi mặt đất & chân khi chạy/nhảy/ngồi!
        float amp1 = Mth.lerp(move, 3.0F, 6.0F) * DEG_TO_RAD;
        float amp2 = Mth.lerp(move, 3.6F, 7.0F) * DEG_TO_RAD;
        float amp3 = Mth.lerp(move, 4.2F, 8.0F) * DEG_TO_RAD;
        float amp4 = Mth.lerp(move, 4.8F, 8.5F) * DEG_TO_RAD;
        float amp5 = Mth.lerp(move, 3.5F, 6.0F) * DEG_TO_RAD;

        float tailLiftDeg = move * 2.2F + air * 3.0F + crouch * 3.5F;
        float breathPitch = Mth.cos(tailPhase * 0.5F);

        applyTailBone(tail1, tailPhase,                      amp1, yawLag * 0.45F, -(tailLiftDeg * 0.45F + breathPitch * 0.8F) * DEG_TO_RAD);
        applyTailBone(tail2, tailPhase - TAIL_PHASE_LAG,     amp2, yawLag * 0.55F, -(tailLiftDeg * 0.35F + breathPitch * 0.9F) * DEG_TO_RAD);
        applyTailBone(tail3, tailPhase - TAIL_PHASE_LAG * 2, amp3, yawLag * 0.65F, -(tailLiftDeg * 0.30F + breathPitch * 1.0F) * DEG_TO_RAD);
        applyTailBone(tail4, tailPhase - TAIL_PHASE_LAG * 3, amp4, yawLag * 0.70F, -(tailLiftDeg * 0.25F + breathPitch * 1.1F) * DEG_TO_RAD);
        applyTailBone(tailTip, tailPhase - TAIL_PHASE_LAG * 4, amp5, yawLag * 0.50F, -(tailLiftDeg * 0.20F + breathPitch * 1.0F) * DEG_TO_RAD);
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

    private static void applyTailBone(GeoBone bone, float phase, float yawAmpRad, float yawLagRad, float pitchRad) {
        if (bone == null) {
            return;
        }
        float yaw = Mth.clamp(Mth.sin(phase) * yawAmpRad + yawLagRad, -9.0F * DEG_TO_RAD, 9.0F * DEG_TO_RAD);
        bone.setRotY(yaw);
        bone.setRotX(pitchRad);
        bone.setRotZ(0.0F);
    }
}
