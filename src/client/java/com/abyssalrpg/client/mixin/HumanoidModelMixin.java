package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.anim.KatanaAnimManager;
import com.abyssalrpg.client.anim.MuramasaAnimManager;
import com.abyssalrpg.client.anim.SeaWeaponsAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.anim.TogetsuBentArmRig;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    @Shadow
    public boolean crouching;

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
            this.body.x = 0.0F;
            this.body.z = 0.0F;
            this.body.xRot = 0.0F;
            this.body.yRot = 0.0F;
            this.body.zRot = 0.0F;
            this.head.x = 0.0F;
            this.head.z = 0.0F;
            this.head.zRot = 0.0F;
            this.rightArm.x = -5.0F;
            this.rightArm.z = 0.0F;
            this.leftArm.x = 5.0F;
            this.leftArm.z = 0.0F;
            this.rightLeg.zRot = 0.0F;
            this.leftLeg.zRot = 0.0F;

            // Tắt hoàn toàn độ vẩy tay mặc định của vanilla (attackTime / setupAttackAnimation) mọi lúc khi cầm Togetsu hoặc Dual Blade
            // để animation vanilla không bao giờ ghi đè rightArm/leftArm ở khung đầu hoặc khung chuyển tiếp!
            if (DualBladeAnimManager.isDualBladeAttacking(player)
                    || TogetsuAnimManager.isHoldingTogetsuReady(player)
                    || ExcaliburAnimManager.isHoldingExcalibur(player)
                    || KatanaAnimManager.isHoldingKatana(player)
                    || SeaWeaponsAnimManager.isHoldingSeaWeapons(player)
                    || MuramasaAnimManager.isHoldingMuramasa(player)) {
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
        if (TogetsuBentArmRig.isRenderingFirstPersonHand()) {
            return;
        }
        if (!(entity instanceof Player player)) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        // Ưu tiên tuyệt đối vũ khí ĐANG CẦM trên tay phải -> chuyển giữa các vũ khí hotbar tức thì, 0 lọt dáng cũ!
        ItemStack heldMain = player.getMainHandItem();
        boolean isExcaliburPose = false;
        FullBodyPose pose = null;
        if (heldMain.is(ModItems.MURAMASA)) {
            pose = MuramasaAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        } else if (heldMain.is(ModItems.SEA_TRIDENT)) {
            pose = SeaWeaponsAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        } else if (heldMain.is(ModItems.EXCALIBUR)) {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
            isExcaliburPose = (pose != null);
        } else if (heldMain.is(ModItems.KATANA)) {
            pose = KatanaAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        } else if (heldMain.is(ModItems.TOGETSU)) {
            pose = TogetsuAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        } else if (heldMain.is(ModItems.DUAL_KNIFE)) {
            pose = DualBladeAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        }
        if (pose == null) {
            pose = DualBladeAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        }
        if (pose == null) {
            pose = TogetsuAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        }
        if (pose == null) {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
            isExcaliburPose = (pose != null);
        }
        if (pose == null) {
            pose = KatanaAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        }
        if (pose == null) {
            pose = SeaWeaponsAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        }
        if (pose == null) {
            pose = MuramasaAnimManager.evaluatePose(player, partialTick, limbSwingAmount, ageInTicks);
        }
        if (pose == null) {
            return;
        }

        // 1. Thân (body): Bảo toàn độ cúi Shift (`crouching`) của vanilla để thân không bị dựng thẳng tách khỏi cặp chân lùi sau (+4px)!
        float vanillaBodyX = this.body.xRot;
        if (this.crouching) {
            this.body.xRot = pose.isAttacking
                    ? Mth.clamp(vanillaBodyX * 0.70F + pose.bodyX * 0.65F, 0.20F, 0.85F)
                    : (vanillaBodyX + pose.bodyX * 0.35F);
            this.body.yRot = pose.bodyY;
            this.body.zRot = pose.bodyZ;
        } else if (pose.isAttacking || pose.overrideVanilla) {
            this.body.xRot = pose.bodyX;
            this.body.yRot = pose.bodyY;
            this.body.zRot = pose.bodyZ;
        } else {
            this.body.xRot += pose.bodyX;
            this.body.yRot += pose.bodyY;
            this.body.zRot += pose.bodyZ;
        }

        // Bù trục xoay thắt lưng (Waist Pivot tại Y = 12px) theo đúng điểm nối hông gốc của vanilla (đứng: [0,12,0]; ngồi Shift: [0, 12cos(0.5), 12sin(0.5)]):
        // Giữ mép dưới thân luôn dính khít vào đỉnh 2 đùi ở mọi góc nghiêng Pitch/Yaw/Roll kể cả khi đang ngồi Shift!
        float waistDx = 0.0F;
        float waistDy = 0.0F;
        float waistDz = 0.0F;
        if (isExcaliburPose || this.crouching) {
            float targetY = 12.0F * Mth.cos(vanillaBodyX);
            float targetZ = 12.0F * Mth.sin(vanillaBodyX);
            float bx = this.body.xRot;
            float by = this.body.yRot;
            float bz = this.body.zRot;
            float y1 = 12.0F * Mth.cos(bx);
            float z1 = 12.0F * Mth.sin(bx);
            float x2 = z1 * Mth.sin(by);
            float z2 = z1 * Mth.cos(by);
            float x3 = x2 * Mth.cos(bz) - y1 * Mth.sin(bz);
            float y3 = x2 * Mth.sin(bz) + y1 * Mth.cos(bz);
            waistDx = -x3;
            waistDy = targetY - y3;
            waistDz = targetZ - z2;
            this.body.x += waistDx;
            this.body.y += waistDy;
            this.body.z += waistDz;
        }

        // 2. Đầu: khóa thẳng đứng nhìn thẳng mục tiêu khi tấn công (Head Pitch ~ 0°, không bao giờ cúi cắm xuống cỏ!)
        if (pose.isAttacking) {
            this.head.xRot = Mth.clamp(headPitch * Mth.DEG_TO_RAD * 0.45F + pose.headAddX, -0.65F, 0.22F);
            this.head.yRot = Mth.clamp(netHeadYaw * Mth.DEG_TO_RAD + pose.headAddY, -1.25F, 1.25F);
        } else {
            this.head.xRot = Mth.clamp(this.head.xRot + pose.headAddX, -1.25F, 1.25F);
            this.head.yRot = Mth.clamp(this.head.yRot + pose.headAddY, -1.35F, 1.35F);
        }
        this.head.zRot = pose.headAddZ;
        if (isExcaliburPose || this.crouching) {
            this.head.x += waistDx;
            this.head.y += waistDy;
            this.head.z += waistDz;
        }
        this.hat.copyFrom(this.head);

        // 3. Cả 2 tay: GÁN TUYỆT ĐỐI ở ưu tiên cao nhất (OVERRIDE) khi đang chém, giữ đỡ hoặc stance ổn định -> tắt 100% Vanilla Arm Bobbing!
        if (pose.isAttacking || pose.overrideVanilla || pose.armWeight >= 0.999F) {
            this.rightArm.xRot = pose.rightArmX;
            this.rightArm.yRot = pose.rightArmY;
            this.rightArm.zRot = pose.rightArmZ;

            this.leftArm.xRot = pose.leftArmX;
            this.leftArm.yRot = pose.leftArmY;
            this.leftArm.zRot = pose.leftArmZ;
        } else {
            float aw = pose.armWeight;
            if (aw >= 0.99F) {
                this.rightArm.xRot = pose.rightArmX;
                this.rightArm.yRot = pose.rightArmY;
                this.rightArm.zRot = pose.rightArmZ;

                this.leftArm.xRot = pose.leftArmX;
                this.leftArm.yRot = pose.leftArmY;
                this.leftArm.zRot = pose.leftArmZ;
            } else if (aw > 0.001F) {
                this.rightArm.xRot = Mth.lerp(aw, this.rightArm.xRot, pose.rightArmX);
                this.rightArm.yRot = Mth.lerp(aw, this.rightArm.yRot, pose.rightArmY);
                this.rightArm.zRot = Mth.lerp(aw, this.rightArm.zRot, pose.rightArmZ);

                this.leftArm.xRot = Mth.lerp(aw, this.leftArm.xRot, pose.leftArmX);
                this.leftArm.yRot = Mth.lerp(aw, this.leftArm.yRot, pose.leftArmY);
                this.leftArm.zRot = Mth.lerp(aw, this.leftArm.zRot, pose.leftArmZ);
            }
        }
        this.rightArm.x += pose.rightArmPosX + waistDx;
        this.rightArm.y += pose.rightArmPosY + waistDy;
        this.rightArm.z += pose.rightArmPosZ + waistDz;
        this.leftArm.x += pose.leftArmPosX + waistDx;
        this.leftArm.y += pose.leftArmPosY + waistDy;
        this.leftArm.z += pose.leftArmPosZ + waistDz;

        // 4. Cả 2 chân: khóa cứng chuẩn mặt đất khi giữ thế đỡ OVERRIDE đứng yên, hòa bước chạy mượt khi di chuyển
        float lw = pose.legWeight;
        if (lw >= 0.99F || (pose.overrideVanilla && lw >= 0.95F)) {
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
        float legPosScale = (isExcaliburPose ? lw : 1.0F) * (this.crouching ? 0.35F : 1.0F);
        this.rightLeg.y += pose.rightLegPosY * legPosScale;
        this.rightLeg.z += pose.rightLegPosZ * legPosScale;
        this.leftLeg.y += pose.leftLegPosY * legPosScale;
        this.leftLeg.z += pose.leftLegPosZ * legPosScale;

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
