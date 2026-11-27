package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.TogetsuBentArmRig;
import com.abyssalrpg.client.anim.TogetsuBentArmRig.SkinBentArms;
import com.abyssalrpg.client.anim.TogetsuBentArmRig.SkinBentLegs;
import com.google.common.collect.Iterables;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
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
import software.bernie.geckolib.animatable.GeoItem;

/**
 * Articulated Bent-Elbow & Bent-Knee PlayerModel Mixin cho **Togetsu** và **Katana**:
 * - Khi `pose.elbowWeight > 0.003F`: vẽ cặp cánh tay 2 khúc có khớp khuỷu gập + đồng bộ `translateToHand(...)`.
 * - Khi `pose.kneeWeight > 0.003F`: vẽ cặp chân 2 khúc có khớp đầu gối gập mượt (tự bỏ qua nếu mặc giáp chân GeckoLib cứng).
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
    @Shadow
    @Final
    public ModelPart leftSleeve;

    @Shadow
    @Final
    public ModelPart rightSleeve;

    @Shadow
    @Final
    public ModelPart leftPants;

    @Shadow
    @Final
    public ModelPart rightPants;

    @Unique
    private SkinBentArms abyssalrpg$bentArms;

    @Unique
    private SkinBentLegs abyssalrpg$bentLegs;

    @Unique
    private boolean abyssalrpg$bentActive;

    @Unique
    private boolean abyssalrpg$hidVanillaArms;

    @Unique
    private boolean abyssalrpg$hidVanillaLegs;

    @Unique
    private boolean abyssalrpg$savedRightArmVis;

    @Unique
    private boolean abyssalrpg$savedLeftArmVis;

    @Unique
    private boolean abyssalrpg$savedRightSleeveVis;

    @Unique
    private boolean abyssalrpg$savedLeftSleeveVis;

    @Unique
    private boolean abyssalrpg$savedRightLegVis;

    @Unique
    private boolean abyssalrpg$savedLeftLegVis;

    @Unique
    private boolean abyssalrpg$savedRightPantsVis;

    @Unique
    private boolean abyssalrpg$savedLeftPantsVis;

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
        this.abyssalrpg$bentLegs = TogetsuBentArmRig.createSkinBentLegs();
    }

    @ModifyReturnValue(method = "bodyParts", at = @At("RETURN"))
    private Iterable<ModelPart> abyssalrpg$appendBentArms(Iterable<ModelPart> original) {
        Iterable<ModelPart> out = original;
        if (this.abyssalrpg$bentArms != null) {
            out = Iterables.concat(out, this.abyssalrpg$bentArms.allParts());
        }
        if (this.abyssalrpg$bentLegs != null) {
            out = Iterables.concat(out, this.abyssalrpg$bentLegs.allParts());
        }
        return out;
    }

    @Inject(method = "setAllVisible(Z)V", at = @At("TAIL"))
    private void abyssalrpg$resetBentArmsOnVisibility(boolean visible, CallbackInfo ci) {
        this.abyssalrpg$bentActive = false;
        this.abyssalrpg$hidVanillaArms = false;
        this.abyssalrpg$hidVanillaLegs = false;
        if (this.abyssalrpg$bentArms != null) {
            this.abyssalrpg$bentArms.setVisible(false);
        }
        if (this.abyssalrpg$bentLegs != null) {
            this.abyssalrpg$bentLegs.setVisible(false);
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
        if (this.abyssalrpg$hidVanillaLegs) {
            this.rightLeg.visible = this.abyssalrpg$savedRightLegVis;
            this.leftLeg.visible = this.abyssalrpg$savedLeftLegVis;
            this.rightPants.visible = this.abyssalrpg$savedRightPantsVis;
            this.leftPants.visible = this.abyssalrpg$savedLeftPantsVis;
            this.abyssalrpg$hidVanillaLegs = false;
        }
        this.abyssalrpg$bentActive = false;
        if (this.abyssalrpg$bentArms != null) {
            this.abyssalrpg$bentArms.setVisible(false);
        }
        if (this.abyssalrpg$bentLegs != null) {
            this.abyssalrpg$bentLegs.setVisible(false);
        }
        if (TogetsuBentArmRig.isRenderingFirstPersonHand()) {
            return;
        }
        if (!(entity instanceof Player player)) {
            return;
        }
        FullBodyPose pose = TogetsuBentArmRig.getActiveArticulatedPose(player);
        if (pose == null) {
            return;
        }

        if (this.abyssalrpg$bentArms != null && pose.elbowWeight > 0.003F) {
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

        boolean rigidGeoLegs = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof GeoItem
                || player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof GeoItem;
        if (this.abyssalrpg$bentLegs != null && pose.kneeWeight > 0.003F && !rigidGeoLegs) {
            float kw = pose.kneeWeight;
            this.abyssalrpg$savedRightLegVis = this.rightLeg.visible;
            this.abyssalrpg$savedLeftLegVis = this.leftLeg.visible;
            this.abyssalrpg$savedRightPantsVis = this.rightPants.visible;
            this.abyssalrpg$savedLeftPantsVis = this.leftPants.visible;

            this.abyssalrpg$bentLegs.syncFrom(
                    this.rightLeg, this.leftLeg,
                    this.abyssalrpg$savedRightLegVis, this.abyssalrpg$savedLeftLegVis,
                    this.abyssalrpg$savedRightPantsVis, this.abyssalrpg$savedLeftPantsVis,
                    pose.rightKneeX * kw, pose.leftKneeX * kw
            );
            this.rightLeg.visible = false;
            this.leftLeg.visible = false;
            this.rightPants.visible = false;
            this.leftPants.visible = false;
            this.abyssalrpg$hidVanillaLegs = true;
        }
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
