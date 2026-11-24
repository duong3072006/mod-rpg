package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Full-Body Bone Rig Mixin (`HumanoidModel.setupAnim` — v2 triệt tiêu hoàn toàn xung đột vanilla):
 * 1. Tại `@At("HEAD")`:
 *    - Reset sạch mọi góc xoay dư trên `body` & `legs.zRot` cho Player -> 0 kẹt dáng khi đổi item hotbar.
 *    - Khi đang trong animation chém Dual Blade: TẮT HOÀN TOÀN `attackTime` mặc định của vanilla (lưu tạm và đặt = 0.0F)
 *      để cú vẩy tay mặc định của vanilla KHÔNG triệt tiêu hay giật chéo với rig 5 nhịp toàn thân!
 * 2. Tại `@At("TAIL")`:
 *    - Khôi phục `attackTime` gốc để không ảnh hưởng bước sau.
 *    - Khi đang tấn công (`pose.isAttacking`): **GÁN TUYỆT ĐỐI** góc xoay cho `body`, `rightArm`, `leftArm` (không `+=` lên vanilla!)
 *      và độ ưu tiên cao cho `rightLeg`, `leftLeg`.
 *    - Sao chép tường minh sang TOÀN BỘ lớp ngoài `PlayerModel` (`hat`, `jacket`, `leftSleeve`, `rightSleeve`, `leftPants`, `rightPants`)
 *      -> `HumanoidArmorLayer.renderArmorPiece` gọi `copyPropertiesTo(armorModel)` ngay sau đó nên giáp & skin bám liền khối 100%!
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart head;

    @Shadow
    @Final
    public ModelPart hat;

    @Shadow
    @Final
    public ModelPart body;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightLeg;

    @Shadow
    @Final
    public ModelPart leftLeg;

    @Unique
    private float abyssalrpg$savedAttackTime;

    @Unique
    private boolean abyssalrpg$suppressedVanillaSwing;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void abyssalrpg$prepareDualBladeRigBeforeVanilla(T entity, float limbSwing, float limbSwingAmount,
                                                             float ageInTicks, float netHeadYaw, float headPitch,
                                                             CallbackInfo ci) {
        this.abyssalrpg$suppressedVanillaSwing = false;
        if (entity instanceof Player player) {
            this.body.xRot = 0.0F;
            this.body.yRot = 0.0F;
            this.body.zRot = 0.0F;
            this.rightLeg.zRot = 0.0F;
            this.leftLeg.zRot = 0.0F;

            // Tắt hoàn toàn độ vẩy tay mặc định của vanilla khi đang đánh bằng Dual Blade!
            if (DualBladeAnimManager.isDualBladeAttacking(player)) {
                HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
                this.abyssalrpg$savedAttackTime = self.attackTime;
                self.attackTime = 0.0F;
                this.abyssalrpg$suppressedVanillaSwing = true;
            }
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void abyssalrpg$applyFullBodyDualBladeRig(T entity, float limbSwing, float limbSwingAmount,
                                                      float ageInTicks, float netHeadYaw, float headPitch,
                                                      CallbackInfo ci) {
        if (this.abyssalrpg$suppressedVanillaSwing) {
            HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
            self.attackTime = this.abyssalrpg$savedAttackTime;
            this.abyssalrpg$suppressedVanillaSwing = false;
        }
        if (!(entity instanceof Player player)) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        FullBodyPose pose = DualBladeAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        if (pose == null) {
            return;
        }

        // 1. Thân (body): GÁN TUYỆT ĐỐI khi đang chém (0 xung đột vanilla), hòa mượt khi đứng thủ
        if (pose.isAttacking) {
            this.body.xRot = pose.bodyX;
            this.body.yRot = pose.bodyY;
            this.body.zRot = pose.bodyZ;
        } else {
            this.body.xRot += pose.bodyX;
            this.body.yRot += pose.bodyY;
            this.body.zRot += pose.bodyZ;
        }

        // 2. Đầu (giữ hướng nhìn mục tiêu rõ nét, không gãy cổ)
        this.head.xRot = Mth.clamp(this.head.xRot + pose.headAddX, -1.25F, 1.25F);
        this.head.yRot = Mth.clamp(this.head.yRot + pose.headAddY, -1.35F, 1.35F);
        this.hat.copyFrom(this.head);

        // 3. Cả 2 tay: GÁN TUYỆT ĐỐI khi đang chém để từng đường kiếm rộng & sắc nét 100%
        if (pose.isAttacking) {
            this.rightArm.xRot = pose.rightArmX;
            this.rightArm.yRot = pose.rightArmY;
            this.rightArm.zRot = pose.rightArmZ;

            this.leftArm.xRot = pose.leftArmX;
            this.leftArm.yRot = pose.leftArmY;
            this.leftArm.zRot = pose.leftArmZ;
        } else {
            float aw = pose.armWeight;
            if (aw > 0.001F) {
                this.rightArm.xRot = Mth.lerp(aw, this.rightArm.xRot, pose.rightArmX);
                this.rightArm.yRot = Mth.lerp(aw, this.rightArm.yRot, pose.rightArmY);
                this.rightArm.zRot = Mth.lerp(aw, this.rightArm.zRot, pose.rightArmZ);

                this.leftArm.xRot = Mth.lerp(aw, this.leftArm.xRot, pose.leftArmX);
                this.leftArm.yRot = Mth.lerp(aw, this.leftArm.yRot, pose.leftArmY);
                this.leftArm.zRot = Mth.lerp(aw, this.leftArm.zRot, pose.leftArmZ);
            }
        }

        // 4. Cả 2 chân: thế tấn rộng / đá chân sau cực rõ trong lúc chém, hòa bước chạy khi di chuyển
        float lw = pose.legWeight;
        if (lw >= 0.99F) {
            this.rightLeg.xRot = pose.rightLegX;
            this.rightLeg.yRot = pose.rightLegY;
            this.rightLeg.zRot = pose.rightLegZ;

            this.leftLeg.xRot = pose.leftLegX;
            this.leftLeg.yRot = pose.leftLegY;
            this.leftLeg.zRot = pose.leftLegZ;
        } else if (lw > 0.001F) {
            this.rightLeg.xRot = Mth.lerp(lw, this.rightLeg.xRot, pose.rightLegX);
            this.rightLeg.yRot = Mth.lerp(lw, this.rightLeg.yRot, pose.rightLegY);
            this.rightLeg.zRot = Mth.lerp(lw, this.rightLeg.zRot, pose.rightLegZ);

            this.leftLeg.xRot = Mth.lerp(lw, this.leftLeg.xRot, pose.leftLegX);
            this.leftLeg.yRot = Mth.lerp(lw, this.leftLeg.yRot, pose.leftLegY);
            this.leftLeg.zRot = Mth.lerp(lw, this.leftLeg.zRot, pose.leftLegZ);
        }

        // 5. Đồng bộ tường minh 100% lớp ngoài của PlayerModel -> không bao giờ tách skin hay giáp!
        if ((Object) this instanceof PlayerModel<?> pm) {
            pm.jacket.copyFrom(this.body);
            pm.rightSleeve.copyFrom(this.rightArm);
            pm.leftSleeve.copyFrom(this.leftArm);
            pm.rightPants.copyFrom(this.rightLeg);
            pm.leftPants.copyFrom(this.leftLeg);
        }
    }
}
