package com.abyssalrpg.client.anim;

import com.abyssalrpg.combat.DualBladeMoveType;
import net.minecraft.util.Mth;

/**
 * Trạng thái Rig Animation Toàn Thân kiểu EpicFight-Nightfall (v2 — 5 nhịp rõ nét + Pose Hold 40%–80%):
 * - Mỗi đòn kéo dài `11–14` tick (kể cả khi cooldown là 8 tick ở speed 2.5):
 *   + `0% -> 40%`: Bùng nổ cực nhanh tới biên độ cực đại.
 *   + `40% -> 80%`: Giữ dáng sắc nét (`Pose Hold` ~4–5 tick) để mắt thấy cực rõ dáng kiếm sĩ!
 *   + `80% -> 100%`: Trả mượt về thế thủ (hoặc tự ngắt cross-fade mượt sang đòn tiếp theo nếu chém tiếp ở tick 8).
 * - Tái sử dụng struct `FullBodyPose` (0 cấp phát bộ nhớ ở 120–240 FPS).
 */
public final class DualBladeAnimState {
    public static final class FullBodyPose {
        public boolean isAttacking;

        public float rootYawDeg;
        public float rootPitchDeg;
        public float rootRollDeg;
        public float rootTranslateY;

        public float bodyX;
        public float bodyY;
        public float bodyZ;

        public float headAddX;
        public float headAddY;
        public float headAddZ;

        public float rightArmX;
        public float rightArmY;
        public float rightArmZ;
        public float rightArmPosX;
        public float rightArmPosZ;

        public float leftArmX;
        public float leftArmY;
        public float leftArmZ;
        public float leftArmPosX;
        public float leftArmPosZ;

        public float rightElbowX;
        public float rightElbowY;
        public float rightElbowZ;

        public float leftElbowX;
        public float leftElbowY;
        public float leftElbowZ;

        public float elbowWeight;

        public float rightLegX;
        public float rightLegY;
        public float rightLegZ;

        public float leftLegX;
        public float leftLegY;
        public float leftLegZ;

        public float armWeight;
        public float legWeight;

        public float rightBladePitchDeg;
        public float rightBladeRollDeg;
        public float rightBladeYawDeg;
        public float rightBladeOffX;
        public float rightBladeOffY;
        public float rightBladeOffZ;
        public float leftBladePitchDeg;
        public float leftBladeRollDeg;
        public float leftBladeYawDeg;
        public float leftBladeOffX;
        public float leftBladeOffY;
        public float leftBladeOffZ;

        public float firstPersonCameraRollDeg;
        public float fovOffsetDeg;

        public void reset() {
            isAttacking = false;
            rootYawDeg = rootPitchDeg = rootRollDeg = rootTranslateY = 0.0F;
            bodyX = bodyY = bodyZ = 0.0F;
            headAddX = headAddY = headAddZ = 0.0F;
            rightArmX = rightArmY = rightArmZ = rightArmPosX = rightArmPosZ = 0.0F;
            leftArmX = leftArmY = leftArmZ = leftArmPosX = leftArmPosZ = 0.0F;
            rightElbowX = rightElbowY = rightElbowZ = 0.0F;
            leftElbowX = leftElbowY = leftElbowZ = 0.0F;
            elbowWeight = 0.0F;
            rightLegX = rightLegY = rightLegZ = 0.0F;
            leftLegX = leftLegY = leftLegZ = 0.0F;
            armWeight = legWeight = 0.0F;
            rightBladePitchDeg = rightBladeRollDeg = rightBladeYawDeg = 0.0F;
            rightBladeOffX = rightBladeOffY = rightBladeOffZ = 0.0F;
            leftBladePitchDeg = leftBladeRollDeg = leftBladeYawDeg = 0.0F;
            leftBladeOffX = leftBladeOffY = leftBladeOffZ = 0.0F;
            firstPersonCameraRollDeg = 0.0F;
            fovOffsetDeg = 0.0F;
        }

        public void copyFrom(FullBodyPose src) {
            this.isAttacking = src.isAttacking;
            this.rootYawDeg = src.rootYawDeg;
            this.rootPitchDeg = src.rootPitchDeg;
            this.rootRollDeg = src.rootRollDeg;
            this.rootTranslateY = src.rootTranslateY;
            this.bodyX = src.bodyX;
            this.bodyY = src.bodyY;
            this.bodyZ = src.bodyZ;
            this.headAddX = src.headAddX;
            this.headAddY = src.headAddY;
            this.headAddZ = src.headAddZ;
            this.rightArmX = src.rightArmX;
            this.rightArmY = src.rightArmY;
            this.rightArmZ = src.rightArmZ;
            this.rightArmPosX = src.rightArmPosX;
            this.rightArmPosZ = src.rightArmPosZ;
            this.leftArmX = src.leftArmX;
            this.leftArmY = src.leftArmY;
            this.leftArmZ = src.leftArmZ;
            this.leftArmPosX = src.leftArmPosX;
            this.leftArmPosZ = src.leftArmPosZ;
            this.rightElbowX = src.rightElbowX;
            this.rightElbowY = src.rightElbowY;
            this.rightElbowZ = src.rightElbowZ;
            this.leftElbowX = src.leftElbowX;
            this.leftElbowY = src.leftElbowY;
            this.leftElbowZ = src.leftElbowZ;
            this.elbowWeight = src.elbowWeight;
            this.rightLegX = src.rightLegX;
            this.rightLegY = src.rightLegY;
            this.rightLegZ = src.rightLegZ;
            this.leftLegX = src.leftLegX;
            this.leftLegY = src.leftLegY;
            this.leftLegZ = src.leftLegZ;
            this.armWeight = src.armWeight;
            this.legWeight = src.legWeight;
            this.rightBladePitchDeg = src.rightBladePitchDeg;
            this.rightBladeRollDeg = src.rightBladeRollDeg;
            this.rightBladeYawDeg = src.rightBladeYawDeg;
            this.rightBladeOffX = src.rightBladeOffX;
            this.rightBladeOffY = src.rightBladeOffY;
            this.rightBladeOffZ = src.rightBladeOffZ;
            this.leftBladePitchDeg = src.leftBladePitchDeg;
            this.leftBladeRollDeg = src.leftBladeRollDeg;
            this.leftBladeYawDeg = src.leftBladeYawDeg;
            this.leftBladeOffX = src.leftBladeOffX;
            this.leftBladeOffY = src.leftBladeOffY;
            this.leftBladeOffZ = src.leftBladeOffZ;
            this.firstPersonCameraRollDeg = src.firstPersonCameraRollDeg;
            this.fovOffsetDeg = src.fovOffsetDeg;
        }

        /** Nội suy mượt từ `from` sang `this` theo hệ số `t` [0..1] khi nối đà giữa 2 hit (không khựng về dáng đứng!). */
        public void blendFrom(FullBodyPose from, float t) {
            float k = Mth.clamp(t, 0.0F, 1.0F);
            this.rootYawDeg = Mth.lerp(k, Mth.wrapDegrees(from.rootYawDeg), this.rootYawDeg);
            this.rootPitchDeg = Mth.lerp(k, from.rootPitchDeg, this.rootPitchDeg);
            this.rootRollDeg = Mth.lerp(k, from.rootRollDeg, this.rootRollDeg);
            this.rootTranslateY = Mth.lerp(k, from.rootTranslateY, this.rootTranslateY);

            this.bodyX = Mth.lerp(k, from.bodyX, this.bodyX);
            this.bodyY = Mth.lerp(k, from.bodyY, this.bodyY);
            this.bodyZ = Mth.lerp(k, from.bodyZ, this.bodyZ);

            this.headAddX = Mth.lerp(k, from.headAddX, this.headAddX);
            this.headAddY = Mth.lerp(k, from.headAddY, this.headAddY);
            this.headAddZ = Mth.lerp(k, from.headAddZ, this.headAddZ);

            this.rightArmX = Mth.lerp(k, from.rightArmX, this.rightArmX);
            this.rightArmY = Mth.lerp(k, from.rightArmY, this.rightArmY);
            this.rightArmZ = Mth.lerp(k, from.rightArmZ, this.rightArmZ);
            this.rightArmPosX = Mth.lerp(k, from.rightArmPosX, this.rightArmPosX);
            this.rightArmPosZ = Mth.lerp(k, from.rightArmPosZ, this.rightArmPosZ);

            this.leftArmX = Mth.lerp(k, from.leftArmX, this.leftArmX);
            this.leftArmY = Mth.lerp(k, from.leftArmY, this.leftArmY);
            this.leftArmZ = Mth.lerp(k, from.leftArmZ, this.leftArmZ);
            this.leftArmPosX = Mth.lerp(k, from.leftArmPosX, this.leftArmPosX);
            this.leftArmPosZ = Mth.lerp(k, from.leftArmPosZ, this.leftArmPosZ);

            this.rightElbowX = Mth.lerp(k, from.rightElbowX, this.rightElbowX);
            this.rightElbowY = Mth.lerp(k, from.rightElbowY, this.rightElbowY);
            this.rightElbowZ = Mth.lerp(k, from.rightElbowZ, this.rightElbowZ);
            this.leftElbowX = Mth.lerp(k, from.leftElbowX, this.leftElbowX);
            this.leftElbowY = Mth.lerp(k, from.leftElbowY, this.leftElbowY);
            this.leftElbowZ = Mth.lerp(k, from.leftElbowZ, this.leftElbowZ);
            this.elbowWeight = Mth.lerp(k, from.elbowWeight, this.elbowWeight);

            this.rightLegX = Mth.lerp(k, from.rightLegX, this.rightLegX);
            this.rightLegY = Mth.lerp(k, from.rightLegY, this.rightLegY);
            this.rightLegZ = Mth.lerp(k, from.rightLegZ, this.rightLegZ);

            this.leftLegX = Mth.lerp(k, from.leftLegX, this.leftLegX);
            this.leftLegY = Mth.lerp(k, from.leftLegY, this.leftLegY);
            this.leftLegZ = Mth.lerp(k, from.leftLegZ, this.leftLegZ);

            this.armWeight = Mth.lerp(k, from.armWeight, this.armWeight);
            this.legWeight = Mth.lerp(k, from.legWeight, this.legWeight);

            this.rightBladePitchDeg = Mth.rotLerp(k, from.rightBladePitchDeg, this.rightBladePitchDeg);
            this.rightBladeRollDeg = Mth.rotLerp(k, from.rightBladeRollDeg, this.rightBladeRollDeg);
            this.rightBladeYawDeg = Mth.rotLerp(k, from.rightBladeYawDeg, this.rightBladeYawDeg);
            this.rightBladeOffX = Mth.lerp(k, from.rightBladeOffX, this.rightBladeOffX);
            this.rightBladeOffY = Mth.lerp(k, from.rightBladeOffY, this.rightBladeOffY);
            this.rightBladeOffZ = Mth.lerp(k, from.rightBladeOffZ, this.rightBladeOffZ);
            this.leftBladePitchDeg = Mth.rotLerp(k, from.leftBladePitchDeg, this.leftBladePitchDeg);
            this.leftBladeRollDeg = Mth.rotLerp(k, from.leftBladeRollDeg, this.leftBladeRollDeg);
            this.leftBladeYawDeg = Mth.rotLerp(k, from.leftBladeYawDeg, this.leftBladeYawDeg);
            this.leftBladeOffX = Mth.lerp(k, from.leftBladeOffX, this.leftBladeOffX);
            this.leftBladeOffY = Mth.lerp(k, from.leftBladeOffY, this.leftBladeOffY);
            this.leftBladeOffZ = Mth.lerp(k, from.leftBladeOffZ, this.leftBladeOffZ);
            this.firstPersonCameraRollDeg = Mth.lerp(k, from.firstPersonCameraRollDeg, this.firstPersonCameraRollDeg);
            this.fovOffsetDeg = Mth.lerp(k, from.fovOffsetDeg, this.fovOffsetDeg);
        }
    }

    public float prevStanceBlend = 0.0F;
    public float stanceBlend = 0.0F;

    /** Pha chuyển mượt sang tư thế chạy Naruto Shinobi (`0.0` = đứng/đi bộ thế thủ 2 tầng, `1.0` = Naruto Run). */
    public float prevSprintBlend = 0.0F;
    public float sprintBlend = 0.0F;
    /** Cửa sổ giữ đà chạy ngắn (5 tick) để khi chém trúng quái lúc đang chạy không bị giật thoát khỏi Naruto Run! */
    public long sprintGraceUntilTick = -1000L;

    public boolean attacking = false;
    public DualBladeMoveType moveType = DualBladeMoveType.COMBO_1;
    public long startTick = -1000L;
    public float durationTicks = 11.5F;
    public float animSpeedMult = 1.0F;
    public float attackYaw = 0.0F;
    public int powerTier = 0;

    public boolean hasTransitionFrom = false;
    public final FullBodyPose idleStancePose = new FullBodyPose();
    public final FullBodyPose blendFromPose = new FullBodyPose();
    public final FullBodyPose lastComputedPose = new FullBodyPose();
    public final FullBodyPose reusablePose = new FullBodyPose();

    public boolean wasOnGround = true;
    public long pendingAirLandingUntilTick = -1000L;
    public long lastSeenTick = 0L;

    public float getStanceBlend(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, this.prevStanceBlend, this.stanceBlend), 0.0F, 1.0F);
    }

    public float getSprintBlend(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, this.prevSprintBlend, this.sprintBlend), 0.0F, 1.0F);
    }

    public float getAttackAlpha(long clientTick, float partialTick) {
        if (!this.attacking || this.durationTicks <= 0.1F) {
            return 0.0F;
        }
        float elapsed = (clientTick - this.startTick) + partialTick;
        if (elapsed <= 0.0F) {
            return 0.0F;
        }
        if (elapsed >= this.durationTicks) {
            return 1.0F;
        }
        return Mth.clamp(elapsed / this.durationTicks, 0.0F, 1.0F);
    }

    public boolean isAttackActive(long clientTick, float partialTick) {
        if (!this.attacking) {
            return false;
        }
        float elapsed = (clientTick - this.startTick) + partialTick;
        return elapsed >= -0.25F && elapsed < this.durationTicks;
    }

    /**
     * Đường cong 3 pha chuẩn Pose Hold:
     * - `0% -> 40%`: Bùng nổ cực nhanh tới góc kết đòn (`0.0 -> 1.04`, tạo lực vung dứt khoát).
     * - `40% -> 80%`: Giữ dáng kết đòn cực rõ (~4.5–5.5 tick) chỉ rung rất nhẹ (`1.04 -> 1.00`) cho tự nhiên.
     * - `80% -> 100%`: Giữ ở `1.00` để `poseHoldEnvelope` thu mượt về thế thủ.
     */
    public static float poseHoldSweep(float alpha) {
        float x = Mth.clamp(alpha, 0.0F, 1.0F);
        if (x < 0.40F) {
            float u = x / 0.40F;
            return easeOutQuart(u) * 1.04F;
        }
        if (x < 0.80F) {
            float u = (x - 0.40F) / 0.40F;
            return Mth.lerp(u, 1.04F, 1.00F);
        }
        return 1.00F;
    }

    /**
     * Biên độ tổng thể 3 pha Pose Hold:
     * - `0% -> 14%`: lao vào góc nghiêng cực đại gần như tức thì.
     * - `14% -> 80%`: GIỮ NGUYÊN `1.00` để độ đổ chúi 35°–42°, xoay vai ±75° và tư thế chân hiện siêu rõ!
     * - `80% -> 100%`: trả mượt bằng smoothstep về `0.00` (tư thế thủ song đao).
     */
    public static float poseHoldEnvelope(float alpha) {
        float x = Mth.clamp(alpha, 0.0F, 1.0F);
        if (x < 0.14F) {
            return easeOutCubic(x / 0.14F);
        }
        if (x <= 0.80F) {
            return 1.0F;
        }
        float u = (x - 0.80F) / 0.20F;
        return 1.0F - (u * u * (3.0F - 2.0F * u));
    }

    /**
     * Đường cong riêng cho Nhịp 4 — 360° Cyclone Spin-Sweep (`14` tick):
     * - `0% -> 62%` (~8.7 tick): xoay tròn toàn thân 360° mượt sắc nét để mắt theo kịp trọn vòng!
     * - `62% -> 84%` (~3.1 tick): hãm quán tính giữ tư thế khuỵu thấp 2 đao dang ngang!
     * - `84% -> 100%`: đứng dậy trả về thế thủ.
     */
    public static float cyclone360Spin(float alpha) {
        float x = Mth.clamp(alpha, 0.0F, 1.0F);
        if (x < 0.62F) {
            return easeInOutCubic(x / 0.62F);
        }
        return 1.0F;
    }

    public static float easeInOutCubic(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0 * x + 2.0, 3.0) / 2.0F;
    }

    public static float easeOutCubic(float t) {
        float inv = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    public static float easeOutQuart(float t) {
        float inv = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv * inv;
    }

    public static float easeOutBack(float t, float overshoot) {
        float x = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
        return 1.0F + (overshoot + 1.0F) * x * x * x + overshoot * x * x;
    }
}
