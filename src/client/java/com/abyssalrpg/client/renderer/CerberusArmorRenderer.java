package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.model.CerberusArmorModel;
import com.abyssalrpg.item.CerberusArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib 4.8.3 `GeoArmorRenderer` cho Bộ Giáp Cerberus:
 * - `entityCutoutNoCull`: hiển thị 2 mặt sắc nét cho các tấm lửa pixel 3D bốc lên từ gót giày.
 * - `CerberusArmorEmissiveLayer` (`RenderType.eyes` + `LightTexture.FULL_BRIGHT`): phát sáng rực rỡ
 *   trên đôi mắt đỏ cả 3 đầu sói, lõi magma ngực, khe nứt magma quần/giày & ngọn lửa pixel gót chân.
 * - `renderFinal` (trên slot `FEET`): phát hạt lửa pixel địa ngục (0 sát thương!) bốc lên từ gót chân
 *   và vòng tàn lửa đỏ-cam quanh đôi giày (tắt trong GUI kho đồ, không che tầm nhìn góc nhìn thứ nhất).
 */
public final class CerberusArmorRenderer extends GeoArmorRenderer<CerberusArmorItem> {
    private static final ResourceLocation GLOW_TEXTURE =
            AbyssalRpgMod.id("textures/armor/cerberus_armor_glowmask.png");
    private static final DustParticleOptions HELLFIRE_ORANGE =
            new DustParticleOptions(new Vector3f(1.00F, 0.38F, 0.06F), 0.72F);
    private static final DustParticleOptions HELLFIRE_CRIMSON =
            new DustParticleOptions(new Vector3f(0.92F, 0.14F, 0.06F), 0.68F);

    private final CerberusArmorModel cerberusModel;
    /** 0 = render bình thường (góc nhìn thứ 3 / kho đồ), +1 = tay phải góc nhìn thứ nhất, -1 = tay trái góc nhìn thứ nhất. */
    private int firstPersonArmMode = 0;

    public CerberusArmorRenderer() {
        this(new CerberusArmorModel());
    }

    private CerberusArmorRenderer(CerberusArmorModel model) {
        super(model);
        this.cerberusModel = model;
        this.addRenderLayer(new CerberusArmorEmissiveLayer(this));
    }

    /**
     * Render trực tiếp cánh tay giáp Cerberus 3D (gồm găng tay móng vuốt, lớp phát sáng Emissive & lưỡi đao co rút)
     * ngay trên cánh tay ở góc nhìn thứ nhất (`PlayerRenderer.renderHand`), ẩn đầu vai để không che tầm nhìn!
     */
    public void renderFirstPersonArm(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> playerModel,
            ItemStack chestStack, boolean isRightArm) {
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        this.firstPersonArmMode = isRightArm ? 1 : -1;
        this.cerberusModel.setFirstPersonArmPass(true);
        try {
            this.prepForRender(player, chestStack, EquipmentSlot.CHEST, playerModel, bufferSource,
                    partialTick, 0.0F, 0.0F, 0.0F, 0.0F);
            RenderType renderType = this.getRenderType(
                    this.animatable, this.getTextureLocation(this.animatable), bufferSource, partialTick);
            VertexConsumer buffer = ItemRenderer.getArmorFoilBuffer(bufferSource, renderType, chestStack.hasFoil());
            this.defaultRender(poseStack, this.animatable, bufferSource, renderType, buffer,
                    0.0F, partialTick, packedLight);
        } finally {
            this.firstPersonArmMode = 0;
            this.cerberusModel.setFirstPersonArmPass(false);
            this.animatable = null;
            GeoBone shR = this.cerberusModel.getAnimationProcessor().getBone("cerberus_shoulder_head_r");
            GeoBone shL = this.cerberusModel.getAnimationProcessor().getBone("cerberus_shoulder_head_l");
            if (shR != null) {
                shR.setHidden(false);
            }
            if (shL != null) {
                shL.setHidden(false);
            }
        }
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
        GeoBone shR = this.cerberusModel.getAnimationProcessor().getBone("cerberus_shoulder_head_r");
        GeoBone shL = this.cerberusModel.getAnimationProcessor().getBone("cerberus_shoulder_head_l");
        if (this.firstPersonArmMode != 0) {
            setAllBonesVisible(false);
            setBoneVisible(this.firstPersonArmMode > 0 ? this.rightArm : this.leftArm, true);
            if (shR != null) {
                shR.setHidden(true);
            }
            if (shL != null) {
                shL.setHidden(true);
            }
            return;
        }
        super.applyBoneVisibilityBySlot(currentSlot);
        if (shR != null) {
            shR.setHidden(false);
        }
        if (shL != null) {
            shL.setHidden(false);
        }
    }

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> baseModel) {
        super.applyBaseTransformations(baseModel);
        if (this.firstPersonArmMode != 0) {
            GeoBone activeArm = this.firstPersonArmMode > 0 ? this.rightArm : this.leftArm;
            if (activeArm != null) {
                activeArm.updateRotation(0.0F, 0.0F, 0.0F);
                activeArm.updatePosition(0.0F, 0.0F, 0.0F);
            }
            this.cerberusModel.syncFirstPersonArm(this.currentEntity);
            return;
        }
        this.cerberusModel.syncLookFromBaseModel(this.currentEntity, baseModel, this.partialTick);
    }

    @Override
    public RenderType getRenderType(CerberusArmorItem animatable, ResourceLocation texture,
            @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderFinal(PoseStack poseStack, CerberusArmorItem animatable, BakedGeoModel model,
            MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        if (this.getCurrentSlot() != EquipmentSlot.FEET) {
            return;
        }
        Entity entity = this.getCurrentEntity();
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInWater()) {
            return;
        }
        if (!(living.level() instanceof ClientLevel level)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.screen instanceof AbstractContainerScreen<?>) {
            return;
        }

        boolean localFirstPerson = (living == mc.player) && mc.options.getCameraType().isFirstPerson();
        double nowSec = (living.tickCount + partialTick) / 20.0;
        double hSpeed = Math.hypot(living.getDeltaMovement().x, living.getDeltaMovement().z);
        double interval = hSpeed > 0.08 ? 0.065 : 0.115;
        if (!this.cerberusModel.shouldEmitFootParticles(living, nowSec, interval)) {
            return;
        }

        double ex = Mth.lerp(partialTick, living.xo, living.getX());
        double ey = Mth.lerp(partialTick, living.yo, living.getY());
        double ez = Mth.lerp(partialTick, living.zo, living.getZ());
        float bodyYawRad = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD;

        double backX = Mth.sin(bodyYawRad);
        double backZ = -Mth.cos(bodyYawRad);
        double rightX = -Mth.cos(bodyYawRad);
        double rightZ = -Mth.sin(bodyYawRad);
        RandomSource rng = level.random;

        // Hạt lửa pixel bốc lên từ 2 gót giày & vòng tàn lửa đỏ-cam quanh chân (100% thuần hình ảnh, 0 sát thương!)
        int count = localFirstPerson ? 1 : (hSpeed > 0.08 ? 3 : 2);
        for (int i = 0; i < count; i++) {
            double side = (i % 2 == 0 ? 1.0 : -1.0) * (0.16 + rng.nextDouble() * 0.24);
            double backOff = (localFirstPerson ? 0.34 : 0.18) + rng.nextDouble() * 0.22;
            double px = ex + rightX * side + backX * backOff + (rng.nextDouble() - 0.5) * 0.16;
            double py = ey + 0.06 + rng.nextDouble() * 0.22;
            double pz = ez + rightZ * side + backZ * backOff + (rng.nextDouble() - 0.5) * 0.16;
            double vy = 0.032 + rng.nextDouble() * 0.036;

            if (i == 0) {
                level.addParticle(ParticleTypes.FLAME, px, py, pz,
                        backX * 0.015 + (rng.nextDouble() - 0.5) * 0.015,
                        vy,
                        backZ * 0.015 + (rng.nextDouble() - 0.5) * 0.015);
            } else {
                level.addParticle(rng.nextBoolean() ? HELLFIRE_ORANGE : HELLFIRE_CRIMSON,
                        px, py + 0.04, pz, 0.0, vy * 0.7, 0.0);
            }
        }
        // Vòng lửa pixel nhỏ nhảy múa quanh đôi giày như ảnh concept (100% thuần hình ảnh, 0 sát thương!)
        if (!localFirstPerson) {
            int haloCount = hSpeed > 0.08 ? 2 : 1;
            for (int h = 0; h < haloCount; h++) {
                double ang = rng.nextDouble() * Math.PI * 2.0;
                double rad = 0.38 + rng.nextDouble() * 0.34;
                level.addParticle(rng.nextFloat() < 0.55F ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                        ex + Math.cos(ang) * rad,
                        ey + 0.04 + rng.nextDouble() * 0.14,
                        ez + Math.sin(ang) * rad,
                        0.0, 0.022 + rng.nextDouble() * 0.018, 0.0);
            }
        }
    }

    private final class CerberusArmorEmissiveLayer extends GeoRenderLayer<CerberusArmorItem> {
        CerberusArmorEmissiveLayer(GeoRenderer<CerberusArmorItem> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, CerberusArmorItem animatable, BakedGeoModel bakedModel,
                @Nullable RenderType renderType, MultiBufferSource bufferSource,
                @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            Entity entity = CerberusArmorRenderer.this.getCurrentEntity();
            float pulse = CerberusArmorRenderer.this.cerberusModel.getMagmaPulse(entity);
            int r = Mth.clamp(Math.round(Mth.lerp(pulse, 0xCC, 0xFF)), 0, 255);
            int g = Mth.clamp(Math.round(Mth.lerp(pulse, 0xAE, 0xFF)), 0, 255);
            int b = Mth.clamp(Math.round(Mth.lerp(pulse, 0x98, 0xF0)), 0, 255);
            int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;

            RenderType glowType = RenderType.eyes(GLOW_TEXTURE);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, argb);
        }
    }
}

