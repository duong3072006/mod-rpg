package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.SeaWeaponsAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.anim.TogetsuBentArmRig;
import com.abyssalrpg.client.anim.TogetsuBentArmRig.SkinBentArms;
import com.google.common.collect.Iterables;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
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
 * Articulated Bent-Elbow PlayerModel Mixin cho tư thế thủ Song Đao Togetsu (`TOGETSU_DUAL_IDLE`):
 * - Khi `pose.elbowWeight > 0.01F`, tạm ẩn cặp cánh tay thẳng cứng mặc định và vẽ cặp cánh tay 2 khúc có khớp khuỷu gập
 *   (giữ nguyên 100% skin, độ rộng Slim/Classic và lớp áo tay ngoài của người chơi).
 * - Đồng bộ ma trận khuỷu tay ngay trong `translateToHand(...)` để cả 2 thanh Togetsu tự động khóa chuẩn vào cổ tay đã gập!
 * - Khi vẽ tay góc nhìn thứ 1 (`setAllVisible`) hoặc đổi item/chém/chạy -> trả về 100% cánh tay vanilla.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
    @Shadow
    @Final
    public ModelPart leftSleeve;

    @Shadow
    @Final
    public ModelPart rightSleeve;

    @Unique
    private SkinBentArms abyssalrpg$bentArms;

    @Unique
    private boolean abyssalrpg$bentActive;

    @Unique
    private boolean abyssalrpg$hidVanillaArms;

    @Unique
    private boolean abyssalrpg$savedRightArmVis;

    @Unique
    private boolean abyssalrpg$savedLeftArmVis;

    @Unique
    private boolean abyssalrpg$savedRightSleeveVis;

    @Unique
    private boolean abyssalrpg$savedLeftSleeveVis;

    @Unique
    private float abyssalrpg$rElbX;

    @Unique
    private float abyssalrpg$rElbY;

    @Unique
    private float abyssalrpg$rElbZ;

    @Unique
    private float abyssalrpg$lElbX;

    @Unique
    private float abyssalrpg$lElbY;

    @Unique
    private float abyssalrpg$lElbZ;

    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/model/geom/ModelPart;Z)V", at = @At("TAIL"))
    private void abyssalrpg$initBentArms(ModelPart root, boolean slim, CallbackInfo ci) {
        this.abyssalrpg$bentArms = TogetsuBentArmRig.createSkinBentArms(slim);
    }

    @ModifyReturnValue(method = "bodyParts", at = @At("RETURN"))
    private Iterable<ModelPart> abyssalrpg$appendBentArms(Iterable<ModelPart> original) {
        if (this.abyssalrpg$bentArms == null) {
            return original;
        }
        return Iterables.concat(original, this.abyssalrpg$bentArms.allParts());
    }

    @Inject(method = "setAllVisible(Z)V", at = @At("TAIL"))
    private void abyssalrpg$resetBentArmsOnVisibility(boolean visible, CallbackInfo ci) {
        this.abyssalrpg$bentActive = false;
        this.abyssalrpg$hidVanillaArms = false;
        if (this.abyssalrpg$bentArms != null) {
            this.abyssalrpg$bentArms.setVisible(false);
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void abyssalrpg$applyBentElbowRig(T entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo ci) {
        if (this.abyssalrpg$hidVanillaArms) {
            this.rightArm.visible = this.abyssalrpg$savedRightArmVis;
            this.leftArm.visible = this.abyssalrpg$savedLeftArmVis;
            this.rightSleeve.visible = this.abyssalrpg$savedRightSleeveVis;
            this.leftSleeve.visible = this.abyssalrpg$savedLeftSleeveVis;
            this.abyssalrpg$hidVanillaArms = false;
        }
        this.abyssalrpg$bentActive = false;
        if (this.abyssalrpg$bentArms == null) {
            return;
        }
        this.abyssalrpg$bentArms.setVisible(false);
        if (!(entity instanceof Player player)) {
            return;
        }
        FullBodyPose pose = TogetsuAnimManager.getLastComputedPose(player.getId());
        if (pose == null || pose.elbowWeight <= 0.003F) {
            pose = SeaWeaponsAnimManager.getLastComputedPose(player.getId());
        }
        if (pose == null || pose.elbowWeight <= 0.003F) {
            return;
        }
        float ew = pose.elbowWeight;
        this.abyssalrpg$rElbX = pose.rightElbowX * ew;
        this.abyssalrpg$rElbY = pose.rightElbowY * ew;
        this.abyssalrpg$rElbZ = pose.rightElbowZ * ew;
        this.abyssalrpg$lElbX = pose.leftElbowX * ew;
        this.abyssalrpg$lElbY = pose.leftElbowY * ew;
        this.abyssalrpg$lElbZ = pose.leftElbowZ * ew;

        this.abyssalrpg$savedRightArmVis = this.rightArm.visible;
        this.abyssalrpg$savedLeftArmVis = this.leftArm.visible;
        this.abyssalrpg$savedRightSleeveVis = this.rightSleeve.visible;
        this.abyssalrpg$savedLeftSleeveVis = this.leftSleeve.visible;

        this.abyssalrpg$bentArms.syncFrom(
                this.rightArm, this.leftArm,
                this.abyssalrpg$savedRightArmVis, this.abyssalrpg$savedLeftArmVis,
                this.abyssalrpg$savedRightSleeveVis, this.abyssalrpg$savedLeftSleeveVis,
                this.abyssalrpg$rElbX, this.abyssalrpg$rElbY, this.abyssalrpg$rElbZ,
                this.abyssalrpg$lElbX, this.abyssalrpg$lElbY, this.abyssalrpg$lElbZ
        );
        this.rightArm.visible = false;
        this.leftArm.visible = false;
        this.rightSleeve.visible = false;
        this.leftSleeve.visible = false;
        this.abyssalrpg$hidVanillaArms = true;
        this.abyssalrpg$bentActive = true;
    }

    @Inject(method = "translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void abyssalrpg$applyElbowToHandMatrix(HumanoidArm side, PoseStack poseStack, CallbackInfo ci) {
        if (!this.abyssalrpg$bentActive) {
            return;
        }
        boolean left = (side == HumanoidArm.LEFT);
        float ex = left ? this.abyssalrpg$lElbX : this.abyssalrpg$rElbX;
        float ey = left ? this.abyssalrpg$lElbY : this.abyssalrpg$rElbY;
        float ez = left ? this.abyssalrpg$lElbZ : this.abyssalrpg$rElbZ;

        // T(elbow) * Rz(ez) * Ry(ey) * Rx(ex) * T(-elbow) -> khớp 1:1 đầu cổ tay cẳng tay gập!
        poseStack.translate(0.0F, 4.0F / 16.0F, 0.0F);
        if (ez != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotation(ez));
        }
        if (ey != 0.0F) {
            poseStack.mulPose(Axis.YP.rotation(ey));
        }
        if (ex != 0.0F) {
            poseStack.mulPose(Axis.XP.rotation(ex));
        }
        poseStack.translate(0.0F, -4.0F / 16.0F, 0.0F);
    }
}
