package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.CerberusArmorClientState;
import com.abyssalrpg.client.anim.GeoArmorElbowSync;
import com.abyssalrpg.item.CerberusArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GeoModel 3D cho Bộ Giáp Cerberus (`geo/armor/cerberus_armor.geo.json`):
 * - Nguồn điều khiển animation duy nhất phía Client (tích phân pha mượt đơn điệu, 0 giật):
 *   1. Tai sói & hàm dưới mũ (`cerberus_ear_r/l`, `cerberus_helm_jaw`): nhúc nhích cảnh giác & thở gầm.
 *   2. Lõi magma ngực (`cerberus_magma_core`): nhịp đập lửa địa ngục rực cháy (đồng bộ độ sáng emissive!).
 *   3. Đồng bộ ánh nhìn 3 đầu (`armorHead` + `cerberus_shoulder_head_r/l`): tính chính xác điểm 3D nhân vật
 *      đang nhìn (raycast mục tiêu / hướng nhìn `baseModel.head`), hội tụ cả 3 đầu vào cùng điểm đó và tự động
 *      khử góc xoay vung tay của `armorRightArm`/`armorLeftArm` bằng đại số ma trận `R_local = R_arm^T * R_des`!
 *   4. Ngọn lửa pixel gót giày (`cerberus_boot_flame_r/l`): bập bùng phập phồng và nghiêng theo tốc độ chạy.
 */
public final class CerberusArmorModel extends GeoModel<CerberusArmorItem> {
    public static final ResourceLocation GEO = AbyssalRpgMod.id("geo/armor/cerberus_armor.geo.json");
    public static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/armor/cerberus_armor.png");
    public static final ResourceLocation ANIM = AbyssalRpgMod.id("animations/armor/cerberus_armor.animation.json");

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;
    private static final float TWO_PI = Mth.TWO_PI;

    // Tọa độ pivot trong không gian GL của GeckoLib (x_gl = -x_geo, y_gl = +y_geo, z_gl = +z_geo)
    private static final float HEAD_PIV_Y = 24.0F;
    private static final float HEAD_EYE_OFFSET_Y = 4.5F;
    private static final float ARM_PIV_X = 5.0F;
    private static final float ARM_PIV_Y = 22.0F;
    private static final float SH_PIV_X = 8.95F;
    private static final float SH_PIV_Y = 24.75F;
    private static final float SH_PIV_Z = -0.35F;

    private static final float MIN_CONVERGENCE_BLOCKS = 2.2F;
    private static final float MAX_CONVERGENCE_BLOCKS = 32.0F;
    private static final float DEFAULT_CONVERGENCE_BLOCKS = 12.0F;
    private static final float MAX_SH_YAW_RAD = 75.0F * DEG_TO_RAD;
    private static final float MAX_SH_PITCH_RAD = 68.0F * DEG_TO_RAD;
    private static final float BLADE_TRANSITION_SEC = 0.42F;

    private final Map<Entity, AnimState> states = new WeakHashMap<>();
    private boolean firstPersonArmPass = false;

    private static final class AnimState {
        double lastTimeSec = Double.NaN;
        double phase = 0.0;
        double flamePhase = 0.0;
        float smoothMove = 0.0F;
        float magmaPulse = 0.5F;
        double lastParticleSec = Double.NEGATIVE_INFINITY;

        // Trạng thái trượt co/bung 2 lưỡi đao cánh tay (0.0 = ẩn hoàn toàn trong tay, 1.0 = bung đầy đủ!)
        boolean bladeInit = false;
        float bladeDeploy = 0.0F;

        // Trạng thái ánh nhìn mới nhất từ baseModel (cập nhật trong applyBaseTransformations để chuẩn cho cả base & emissive!)
        boolean hasLookSnapshot = false;
        float headRotX, headRotY, headRotZ;
        float headPosX, headPosY, headPosZ;
        float rArmRotX, rArmRotY, rArmRotZ;
        float rArmPosX, rArmPosY, rArmPosZ;
        float lArmRotX, lArmRotY, lArmRotZ;
        float lArmPosX, lArmPosY, lArmPosZ;
        float smoothLookDistBlocks = DEFAULT_CONVERGENCE_BLOCKS;

        // Góc thở & há hàm đã tính sẵn để pass emissive reRender giữ khớp 100% với base pass
        float breathPitchR, breathYawR;
        float breathPitchL, breathYawL;
        float jawOpenHelm, jawOpenR, jawOpenL;
    }

    public float getMagmaPulse(Entity entity) {
        if (entity == null) {
            return 0.55F;
        }
        AnimState st = this.states.get(entity);
        return st != null ? st.magmaPulse : 0.55F;
    }

    public boolean shouldEmitFootParticles(Entity entity, double nowSec, double minIntervalSec) {
        if (entity == null) {
            return false;
        }
        AnimState st = this.states.computeIfAbsent(entity, k -> new AnimState());
        if (nowSec - st.lastParticleSec < minIntervalSec && nowSec >= st.lastParticleSec) {
            return false;
        }
        st.lastParticleSec = nowSec;
        return true;
    }

    public void setFirstPersonArmPass(boolean active) {
        this.firstPersonArmPass = active;
    }

    /**
     * Đồng bộ trạng thái lưỡi đao co rút và giữ cẳng tay thẳng `(0, 0, 0)` cho pass render cánh tay góc nhìn thứ nhất
     * (áp dụng trên cả lớp base lẫn lớp `Emissive` để 0 lệch glowmask!).
     */
    public void syncFirstPersonArm(@Nullable Entity entity) {
        if (entity == null) {
            return;
        }
        AnimState st = this.states.computeIfAbsent(entity, k -> new AnimState());
        if (!st.bladeInit) {
            st.bladeDeploy = CerberusArmorClientState.isBladesActive(entity) ? 1.0F : 0.0F;
            st.bladeInit = true;
        }
        resetForearmStraight();
        applyRetractableArmBlades(st);
    }

    private void resetForearmStraight() {
        GeoBone rFore = getAnimationProcessor().getBone(GeoArmorElbowSync.RIGHT_FOREARM_BONE);
        GeoBone lFore = getAnimationProcessor().getBone(GeoArmorElbowSync.LEFT_FOREARM_BONE);
        if (rFore != null) {
            rFore.setRotX(0.0F);
            rFore.setRotY(0.0F);
            rFore.setRotZ(0.0F);
        }
        if (lFore != null) {
            lFore.setRotX(0.0F);
            lFore.setRotY(0.0F);
            lFore.setRotZ(0.0F);
        }
    }

    /**
     * Được gọi từ {@code CerberusArmorRenderer.applyBaseTransformations} trước cả base pass và emissive pass:
     * ghi lại chính xác góc/vị trí hiện tại của {@code head}, {@code rightArm}, {@code leftArm} trên {@code baseModel}
     * và áp dụng ngay hướng nhìn hội tụ 3 đầu (bảo đảm đồng bộ 100% kể cả khi game tạm dừng hoặc đang mở kho đồ!).
     */
    public void syncLookFromBaseModel(@Nullable Entity entity, @Nullable HumanoidModel<?> baseModel, float partialTick) {
        if (entity == null || baseModel == null) {
            return;
        }
        AnimState st = this.states.computeIfAbsent(entity, k -> new AnimState());

        ModelPart h = baseModel.head;
        ModelPart ra = baseModel.rightArm;
        ModelPart la = baseModel.leftArm;

        // Quy ước GeoArmorRenderer.applyBaseTransformations + RenderUtil.matchModelPartRot:
        // rotX = -part.xRot, rotY = -part.yRot, rotZ = +part.zRot
        // translateMatrixToBone dùng (-posX, +posY, +posZ)
        st.headRotX = -h.xRot;
        st.headRotY = -h.yRot;
        st.headRotZ = h.zRot;
        st.headPosX = h.x;
        st.headPosY = -h.y;
        st.headPosZ = h.z;

        st.rArmRotX = -ra.xRot;
        st.rArmRotY = -ra.yRot;
        st.rArmRotZ = ra.zRot;
        st.rArmPosX = ra.x + ARM_PIV_X;
        st.rArmPosY = 2.0F - ra.y;
        st.rArmPosZ = ra.z;

        st.lArmRotX = -la.xRot;
        st.lArmRotY = -la.yRot;
        st.lArmRotZ = la.zRot;
        st.lArmPosX = la.x - ARM_PIV_X;
        st.lArmPosY = 2.0F - la.y;
        st.lArmPosZ = la.z;

        float targetDist = resolveLookDistanceBlocks(entity, partialTick);
        st.smoothLookDistBlocks = Mth.lerp(0.28F, st.smoothLookDistBlocks, targetDist);
        st.hasLookSnapshot = true;

        if (!st.bladeInit) {
            st.bladeDeploy = CerberusArmorClientState.isBladesActive(entity) ? 1.0F : 0.0F;
            st.bladeInit = true;
        }

        GeoArmorElbowSync.applyBentElbows(getAnimationProcessor(), entity);
        applySyncedThreeHeadPose(st);
        applyRetractableArmBlades(st);
    }

    private static float resolveLookDistanceBlocks(Entity entity, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> || entity instanceof ArmorStand) {
            return DEFAULT_CONVERGENCE_BLOCKS;
        }
        if (!(entity instanceof LivingEntity living)) {
            return DEFAULT_CONVERGENCE_BLOCKS;
        }
        Vec3 eyePos = living.getEyePosition(partialTick);
        if (living == mc.player && mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            float d = (float) mc.hitResult.getLocation().distanceTo(eyePos);
            return Mth.clamp(d, MIN_CONVERGENCE_BLOCKS, MAX_CONVERGENCE_BLOCKS);
        }
        HitResult picked = living.pick(24.0, partialTick, false);
        if (picked != null && picked.getType() != HitResult.Type.MISS) {
            float d = (float) picked.getLocation().distanceTo(eyePos);
            return Mth.clamp(d, MIN_CONVERGENCE_BLOCKS, MAX_CONVERGENCE_BLOCKS);
        }
        return 20.0F;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(CerberusArmorItem animatable) {
        return GEO;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(CerberusArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CerberusArmorItem animatable) {
        return ANIM;
    }

    @Override
    public ResourceLocation getModelResource(CerberusArmorItem animatable, GeoRenderer<CerberusArmorItem> renderer) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(CerberusArmorItem animatable, GeoRenderer<CerberusArmorItem> renderer) {
        return TEXTURE;
    }

    @Override
    public void setCustomAnimations(CerberusArmorItem animatable, long instanceId,
            AnimationState<CerberusArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        Entity entity = animationState.getData(DataTickets.ENTITY);
        if (entity == null) {
            return;
        }
        // Ở góc nhìn thứ nhất giữ cẳng tay thẳng theo PlayerRenderer.renderHand; ở góc nhìn thứ 3 đồng bộ khuỷu Togetsu/SeaWeapons!
        if (this.firstPersonArmPass) {
            resetForearmStraight();
        } else {
            GeoArmorElbowSync.applyBentElbows(getAnimationProcessor(), entity);
        }

        AnimState st = this.states.computeIfAbsent(entity, k -> new AnimState());
        double timeSec = (entity.tickCount + animationState.getPartialTick()) / 20.0;

        float dt = 0.0F;
        if (Double.isNaN(st.lastTimeSec) || timeSec < st.lastTimeSec || (timeSec - st.lastTimeSec) > 0.5) {
            st.lastTimeSec = timeSec;
        } else {
            dt = (float) (timeSec - st.lastTimeSec);
            st.lastTimeSec = timeSec;
        }

        float targetMove = 0.0F;
        boolean combatActive = false;
        if (entity instanceof LivingEntity living) {
            double hSpeed = Math.hypot(living.getDeltaMovement().x, living.getDeltaMovement().z);
            targetMove = Mth.clamp((float) (hSpeed * 4.5), 0.0F, 1.0F);
            if (living.isSprinting()) {
                targetMove = Math.max(targetMove, 0.90F);
            }
            combatActive = living.swinging || living.hurtTime > 0;
        }

        float lerpAlpha = dt > 0.0F ? (1.0F - (float) Math.exp(-dt * 8.0)) : 0.0F;
        st.smoothMove = Mth.lerp(lerpAlpha, st.smoothMove, targetMove);
        st.phase = (st.phase + dt * (1.85 + st.smoothMove * 1.45)) % 10000.0;
        st.flamePhase = (st.flamePhase + dt * (7.5 + st.smoothMove * 4.0)) % 10000.0;

        // Trạng thái co rút / bung ra mượt mà của 2 thanh đao cánh tay (mặc định 0.0 = ẩn hoàn toàn trong tay!)
        float targetDeploy = CerberusArmorClientState.isBladesActive(entity) ? 1.0F : 0.0F;
        if (!st.bladeInit) {
            st.bladeDeploy = targetDeploy;
            st.bladeInit = true;
        } else if (dt > 0.0F) {
            float step = dt / BLADE_TRANSITION_SEC;
            if (st.bladeDeploy < targetDeploy) {
                st.bladeDeploy = Math.min(targetDeploy, st.bladeDeploy + step);
            } else if (st.bladeDeploy > targetDeploy) {
                st.bladeDeploy = Math.max(targetDeploy, st.bladeDeploy - step);
            }
        }

        // 1. Nhịp đập dung nham nóng bỏng (`cerberus_magma_core`)
        float wave = 0.5F + 0.5F * Mth.sin((float) (st.phase * 1.35));
        float pulse = wave * wave;
        if (combatActive) {
            pulse = Mth.clamp(pulse + 0.25F, 0.0F, 1.0F);
        }
        st.magmaPulse = pulse;

        GeoBone magmaCore = getAnimationProcessor().getBone("cerberus_magma_core");
        if (magmaCore != null) {
            float sc = 0.96F + pulse * 0.10F;
            magmaCore.setScaleX(sc);
            magmaCore.setScaleY(sc);
            magmaCore.setScaleZ(0.96F + pulse * 0.14F);
        }

        // 2. Cặp sừng cong phân đốt trên mũ rung nhẹ đầy uy lực theo nhịp thở & chạy
        float hornSway = Mth.sin((float) (st.phase * 0.90)) * 1.1F
                + Mth.sin((float) (st.phase * 2.8)) * (st.smoothMove * 1.6F);
        GeoBone earR = getAnimationProcessor().getBone("cerberus_ear_r");
        GeoBone earL = getAnimationProcessor().getBone("cerberus_ear_l");
        if (earR != null) {
            earR.setRotX((-st.smoothMove * 3.5F + hornSway * 0.4F) * DEG_TO_RAD);
            earR.setRotZ((hornSway) * DEG_TO_RAD);
        }
        if (earL != null) {
            earL.setRotX((-st.smoothMove * 3.5F - hornSway * 0.4F) * DEG_TO_RAD);
            earL.setRotZ((-hornSway) * DEG_TO_RAD);
        }

        // 3. Nhịp thở nhẹ & há hàm luân phiên cho cả 3 đầu Cerberus (áp chồng lên hướng nhìn hội tụ 3D!)
        float snarlBoost = combatActive ? 1.0F : 0.0F;
        st.jawOpenHelm = Math.max(0.0F, Mth.sin((float) (st.phase * 0.72 + 1.05))) * (6.0F + snarlBoost * 9.5F);
        st.jawOpenR = Math.max(0.0F, Mth.sin((float) (st.phase * 0.72))) * (7.5F + snarlBoost * 10.5F);
        st.jawOpenL = Math.max(0.0F, Mth.sin((float) (st.phase * 0.72 + Math.PI))) * (7.5F + snarlBoost * 10.5F);

        // Nhịp thở rất nhẹ (±1.4° pitch, ±1.2° yaw) để 2 đầu vai vẫn sống động nhưng khóa sát điểm mục tiêu!
        st.breathPitchR = (Mth.sin((float) st.phase) * 1.4F + snarlBoost * 2.0F) * DEG_TO_RAD;
        st.breathYawR = (Mth.cos((float) (st.phase * 0.6)) * 1.2F) * DEG_TO_RAD;
        st.breathPitchL = (Mth.sin((float) (st.phase + 2.15)) * 1.4F + snarlBoost * 2.0F) * DEG_TO_RAD;
        st.breathYawL = (-Mth.cos((float) (st.phase * 0.6 + 1.5)) * 1.2F) * DEG_TO_RAD;

        applySyncedThreeHeadPose(st);
        applyRetractableArmBlades(st);

        // 4. Ngọn lửa pixel gót giày bập bùng phập phồng & nghiêng nhẹ khi chạy!
        float flR = Mth.sin((float) st.flamePhase);
        float flL = Mth.sin((float) (st.flamePhase + TWO_PI * 0.37F));
        GeoBone flameR = getAnimationProcessor().getBone("cerberus_boot_flame_r");
        GeoBone flameL = getAnimationProcessor().getBone("cerberus_boot_flame_l");
        if (flameR != null) {
            flameR.setScaleY(0.92F + 0.16F * (0.5F + 0.5F * flR) + st.smoothMove * 0.12F);
            flameR.setScaleX(0.95F + 0.08F * Mth.cos((float) st.flamePhase));
            flameR.setRotX((-st.smoothMove * 14.0F + flR * 4.5F) * DEG_TO_RAD);
        }
        if (flameL != null) {
            flameL.setScaleY(0.92F + 0.16F * (0.5F + 0.5F * flL) + st.smoothMove * 0.12F);
            flameL.setScaleX(0.95F + 0.08F * Mth.cos((float) (st.flamePhase + 1.8)));
            flameL.setRotX((-st.smoothMove * 14.0F + flL * 4.5F) * DEG_TO_RAD);
        }
    }

    /**
     * Điều khiển co rút / bung ra của 2 thanh đao cánh tay (`cerberus_arm_blade_r/l`):
     * - Khi `bladeDeploy <= 0.002`: ẩn hoàn toàn (`setHidden(true)`) -> mặc định không có 2 lưỡi đao bên ngoài tay!
     * - Khi đang trượt mở/thu hồi (`0 < bladeDeploy < 1`): trượt ngang từ trong cẳng tay ra ngoài + duỗi dài + mở góc sắc nét.
     */
    private void applyRetractableArmBlades(AnimState st) {
        GeoBone bladeR = getAnimationProcessor().getBone("cerberus_arm_blade_r");
        GeoBone bladeL = getAnimationProcessor().getBone("cerberus_arm_blade_l");
        if (bladeR == null && bladeL == null) {
            return;
        }

        float t = Mth.clamp(st.bladeDeploy, 0.0F, 1.0F);
        // Đường cong ease-out cubic cho cảm giác bật lưỡi đao sắc bén và thu gọn mượt mà
        float invLinear = 1.0F - t;
        float ease = 1.0F - invLinear * invLinear * invLinear;
        boolean hidden = ease <= 0.002F;

        float inv = 1.0F - ease;
        float slidePx = 2.75F * inv;
        float sx = Mth.lerp(ease, 0.15F, 1.0F);
        float sy = Mth.lerp(ease, 0.18F, 1.0F);
        float sz = Mth.lerp(ease, 0.45F, 1.0F);
        float foldZ = inv * 18.0F * DEG_TO_RAD;

        if (bladeR != null) {
            bladeR.setHidden(hidden);
            if (!hidden) {
                bladeR.setPosX(+slidePx);
                bladeR.setPosY(0.0F);
                bladeR.setPosZ(0.0F);
                bladeR.setScaleX(sx);
                bladeR.setScaleY(sy);
                bladeR.setScaleZ(sz);
                bladeR.setRotX(0.0F);
                bladeR.setRotY(0.0F);
                bladeR.setRotZ(+foldZ);
            }
        }
        if (bladeL != null) {
            bladeL.setHidden(hidden);
            if (!hidden) {
                bladeL.setPosX(-slidePx);
                bladeL.setPosY(0.0F);
                bladeL.setPosZ(0.0F);
                bladeL.setScaleX(sx);
                bladeL.setScaleY(sy);
                bladeL.setScaleZ(sz);
                bladeL.setRotX(0.0F);
                bladeL.setRotY(0.0F);
                bladeL.setRotZ(-foldZ);
            }
        }
    }

    private void applySyncedThreeHeadPose(AnimState st) {
        GeoBone helmJaw = getAnimationProcessor().getBone("cerberus_helm_jaw");
        if (helmJaw != null) {
            helmJaw.setRotX(-st.jawOpenHelm * DEG_TO_RAD);
        }

        GeoBone shHeadR = getAnimationProcessor().getBone("cerberus_shoulder_head_r");
        GeoBone shJawR = getAnimationProcessor().getBone("cerberus_shoulder_jaw_r");
        GeoBone shHeadL = getAnimationProcessor().getBone("cerberus_shoulder_head_l");
        GeoBone shJawL = getAnimationProcessor().getBone("cerberus_shoulder_jaw_l");

        // Vì 2 đầu vai đã có tư thế gốc hướng thẳng 0° nên hàm mở thẳng theo trục X cục bộ ở mọi góc nhìn!
        if (shJawR != null) {
            shJawR.setRotX(-st.jawOpenR * DEG_TO_RAD);
            shJawR.setRotY(0.0F);
            shJawR.setRotZ(0.0F);
        }
        if (shJawL != null) {
            shJawL.setRotX(-st.jawOpenL * DEG_TO_RAD);
            shJawL.setRotY(0.0F);
            shJawL.setRotZ(0.0F);
        }

        if (!st.hasLookSnapshot || (shHeadR == null && shHeadL == null)) {
            return;
        }

        // Tính điểm mục tiêu 3D T trong không gian GL Model Space (đơn vị pixel = 1/16 block)
        float[] rHead = buildZyxMatrix(st.headRotX, st.headRotY, st.headRotZ);
        // Hướng nhìn của đầu chính: R_head * (0, 0, -1) = (-m02, -m12, -m22)
        float lookDirX = -rHead[2];
        float lookDirY = -rHead[5];
        float lookDirZ = -rHead[8];
        // Vị trí tâm mắt của đầu chính: T_head + piv_head + R_head * (0, HEAD_EYE_OFFSET_Y, 0)
        float eyeX = -st.headPosX + rHead[1] * HEAD_EYE_OFFSET_Y;
        float eyeY = st.headPosY + HEAD_PIV_Y + rHead[4] * HEAD_EYE_OFFSET_Y;
        float eyeZ = st.headPosZ + rHead[7] * HEAD_EYE_OFFSET_Y;

        float distPx = st.smoothLookDistBlocks * 16.0F;
        float targetX = eyeX + lookDirX * distPx;
        float targetY = eyeY + lookDirY * distPx;
        float targetZ = eyeZ + lookDirZ * distPx;

        if (shHeadR != null) {
            orientShoulderHeadToTarget(
                    shHeadR,
                    +ARM_PIV_X, ARM_PIV_Y, 0.0F,
                    +SH_PIV_X, SH_PIV_Y, SH_PIV_Z,
                    st.rArmPosX, st.rArmPosY, st.rArmPosZ,
                    st.rArmRotX, st.rArmRotY, st.rArmRotZ,
                    targetX, targetY, targetZ,
                    st.breathPitchR, st.breathYawR
            );
        }
        if (shHeadL != null) {
            orientShoulderHeadToTarget(
                    shHeadL,
                    -ARM_PIV_X, ARM_PIV_Y, 0.0F,
                    -SH_PIV_X, SH_PIV_Y, SH_PIV_Z,
                    st.lArmPosX, st.lArmPosY, st.lArmPosZ,
                    st.lArmRotX, st.lArmRotY, st.lArmRotZ,
                    targetX, targetY, targetZ,
                    st.breathPitchL, st.breathYawL
            );
        }
    }

    private static void orientShoulderHeadToTarget(
            GeoBone shHead,
            float armPivX, float armPivY, float armPivZ,
            float shPivX, float shPivY, float shPivZ,
            float armPosX, float armPosY, float armPosZ,
            float armRotX, float armRotY, float armRotZ,
            float targetX, float targetY, float targetZ,
            float breathPitchRad, float breathYawRad) {
        // 1. Vị trí thực của khớp cổ đầu vai sau khi armorRightArm/LeftArm đã xoay & dịch chuyển
        float[] rArm = buildZyxMatrix(armRotX, armRotY, armRotZ);
        float relX = shPivX - armPivX;
        float relY = shPivY - armPivY;
        float relZ = shPivZ - armPivZ;

        float curShX = -armPosX + armPivX + rArm[0] * relX + rArm[1] * relY + rArm[2] * relZ;
        float curShY = armPosY + armPivY + rArm[3] * relX + rArm[4] * relY + rArm[5] * relZ;
        float curShZ = armPosZ + armPivZ + rArm[6] * relX + rArm[7] * relY + rArm[8] * relZ;

        // 2. Vector từ khớp đầu vai tới đúng điểm mục tiêu T -> góc yaw/pitch mong muốn trong model space
        float dx = targetX - curShX;
        float dy = targetY - curShY;
        float dz = targetZ - curShZ;
        float horiz = Mth.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-4F && Math.abs(dy) < 1.0e-4F) {
            return;
        }

        float desYaw = Mth.clamp((float) Math.atan2(-dx, -dz), -MAX_SH_YAW_RAD, MAX_SH_YAW_RAD);
        float desPitch = Mth.clamp((float) Math.atan2(dy, horiz), -MAX_SH_PITCH_RAD, MAX_SH_PITCH_RAD);

        // 3. Kết hợp góc nhìn mục tiêu + nhịp thở nhẹ, rồi nhân nghịch đảo R_arm^T để khử hoàn toàn vung tay!
        float[] rDes = buildZyxMatrix(desPitch + breathPitchRad, desYaw + breathYawRad, 0.0F);
        float[] rLocal = mulTransposeLeft(rArm, rDes);

        // 4. Tách ngược chính xác góc Euler Z-Y-X khớp 100% RenderUtil.rotateMatrixAroundBone: Rz * Ry * Rx
        float sinY = Mth.clamp(-rLocal[6], -1.0F, 1.0F);
        float rotY = (float) Math.asin(sinY);
        float cosY = Mth.cos(rotY);
        float rotX;
        float rotZ;
        if (Math.abs(cosY) > 1.0e-4F) {
            rotX = (float) Math.atan2(rLocal[7], rLocal[8]);
            rotZ = (float) Math.atan2(rLocal[3], rLocal[0]);
        } else {
            rotX = (float) Math.atan2(-rLocal[5], rLocal[4]);
            rotZ = 0.0F;
        }

        shHead.setRotX(rotX);
        shHead.setRotY(rotY);
        shHead.setRotZ(rotZ);
    }

    /** Ma trận 3x3 row-major cho tích Rz(rz) * Ry(ry) * Rx(rx). */
    private static float[] buildZyxMatrix(float rx, float ry, float rz) {
        float cx = Mth.cos(rx), sx = Mth.sin(rx);
        float cy = Mth.cos(ry), sy = Mth.sin(ry);
        float cz = Mth.cos(rz), sz = Mth.sin(rz);
        return new float[] {
                cz * cy,  cz * sy * sx - sz * cx,  cz * sy * cx + sz * sx,
                sz * cy,  sz * sy * sx + cz * cx,  sz * sy * cx - cz * sx,
                -sy,      cy * sx,                 cy * cx
        };
    }

    /** Tính A^T * B với ma trận 3x3 row-major. */
    private static float[] mulTransposeLeft(float[] a, float[] b) {
        return new float[] {
                a[0] * b[0] + a[3] * b[3] + a[6] * b[6],
                a[0] * b[1] + a[3] * b[4] + a[6] * b[7],
                a[0] * b[2] + a[3] * b[5] + a[6] * b[8],

                a[1] * b[0] + a[4] * b[3] + a[7] * b[6],
                a[1] * b[1] + a[4] * b[4] + a[7] * b[7],
                a[1] * b[2] + a[4] * b[5] + a[7] * b[8],

                a[2] * b[0] + a[5] * b[3] + a[8] * b[6],
                a[2] * b[1] + a[5] * b[4] + a[8] * b[7],
                a[2] * b[2] + a[5] * b[5] + a[8] * b[8]
        };
    }
}

