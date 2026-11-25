package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.model.SpiritHydraArmorModel;
import com.abyssalrpg.item.SpiritHydraArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib 4.8.3 `GeoArmorRenderer` cho Bộ Giáp Spirit Hydra:
 * - `entityCutoutNoCull`: hiển thị 2 mặt sắc nét cho mang quạt 5 nan phát sáng xanh băng của cả 4 đầu Hồn Xà.
 * - `HydraArmorEmissiveLayer` (`RenderType.eyes` + `LightTexture.FULL_BRIGHT`): phát sáng thuần không bị tối
 *   do hướng nắng (`-Z` north shading), đồng bộ chớp sáng theo nhịp tim đập `lub-dub` & hơi lửa xanh!
 * - `renderFinal`: phát hạt lửa linh hồn xanh lam KHÔNG GÂY SÁT THƯƠNG (`SOUL_FIRE_FLAME` + tia lửa cyan/trắng băng)
 *   ra đúng từ miệng 3 đầu Hồn Xà theo động học thuận (FK) khi chúng chơi đùa hoặc phun lửa xanh!
 */
public final class SpiritHydraArmorRenderer extends GeoArmorRenderer<SpiritHydraArmorItem> {
    private static final ResourceLocation GLOW_TEXTURE =
            AbyssalRpgMod.id("textures/armor/spirit_hydra_armor_glowmask.png");
    private static final DustParticleOptions CYAN_FLAME_DUST =
            new DustParticleOptions(new Vector3f(0.30F, 0.96F, 1.00F), 0.72F);
    private static final DustParticleOptions ICE_CORE_DUST =
            new DustParticleOptions(new Vector3f(0.88F, 1.00F, 1.00F), 0.56F);

    private final SpiritHydraArmorModel hydraModel;

    public SpiritHydraArmorRenderer() {
        this(new SpiritHydraArmorModel());
    }

    private SpiritHydraArmorRenderer(SpiritHydraArmorModel model) {
        super(model);
        this.hydraModel = model;
        this.addRenderLayer(new HydraArmorEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(SpiritHydraArmorItem animatable, ResourceLocation texture,
            @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderFinal(PoseStack poseStack, SpiritHydraArmorItem animatable, BakedGeoModel model,
            MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        // Chỉ phát hạt lửa xanh thuần hình ảnh (0 sát thương!) khi render đúng slot áo ngực ngoài thế giới thật!
        if (this.getCurrentSlot() != EquipmentSlot.CHEST) {
            return;
        }
        Entity entity = this.getCurrentEntity();
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        if (!(living.level() instanceof ClientLevel level)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.screen instanceof AbstractContainerScreen<?>) {
            return;
        }

        SpiritHydraArmorModel.HeadBreathSample sample = this.hydraModel.getHeadBreathSample(living);
        if (sample == null || sample.maxBreath <= 0.04F) {
            return;
        }

        double nowSec = (living.tickCount + partialTick) / 20.0;
        if (nowSec - sample.lastParticleEmitSec < 0.045 && nowSec >= sample.lastParticleEmitSec) {
            return;
        }
        sample.lastParticleEmitSec = nowSec;

        boolean localFirstPerson = (living == mc.player) && mc.options.getCameraType().isFirstPerson();
        double ex = Mth.lerp(partialTick, living.xo, living.getX());
        double ey = Mth.lerp(partialTick, living.yo, living.getY()) - (living.isCrouching() ? 0.16 : 0.0);
        double ez = Mth.lerp(partialTick, living.zo, living.getZ());
        float bodyYawRad = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD;

        double fwdX = -Mth.sin(bodyYawRad);
        double fwdZ = Mth.cos(bodyYawRad);
        double leftX = Mth.cos(bodyYawRad);
        double leftZ = Mth.sin(bodyYawRad);
        RandomSource rng = level.random;

        for (int h = 0; h < 3; h++) {
            float breath = sample.breathStrength[h];
            float spark = sample.playSparkStrength[h];
            if (breath <= 0.04F && spark <= 0.05F) {
                continue;
            }
            // Ở góc nhìn thứ nhất của chính người chơi, tránh che tâm ngắm từ đầu đỉnh giữa sát mắt
            if (localFirstPerson && h == SpiritHydraArmorModel.HEAD_CENTER) {
                continue;
            }

            Vector3f mp = sample.muzzleModelPx[h];
            Vector3f md = sample.dirModel[h];
            double mx = mp.x / 16.0;
            double my = mp.y / 16.0;
            double mz = -mp.z / 16.0;

            double wx = ex + leftX * mx + fwdX * mz;
            double wy = ey + my;
            double wz = ez + leftZ * mx + fwdZ * mz;

            double dx = leftX * md.x + fwdX * (-md.z);
            double dy = md.y;
            double dz = leftZ * md.x + fwdZ * (-md.z);
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1e-4) {
                dx /= len;
                dy /= len;
                dz /= len;
            }

            if (breath > 0.04F) {
                // Luồng lửa linh hồn xanh lam vô hại bắn ra từ miệng rồng (SOUL_FIRE_FLAME + tinh thể cyan + lõi trắng băng!)
                int flameCount = localFirstPerson ? 1 : (breath > 0.65F ? 3 : 2);
                for (int k = 0; k < flameCount; k++) {
                    double spread = 0.055;
                    double speed = 0.14 + rng.nextDouble() * 0.10 * breath;
                    double vx = dx * speed + (rng.nextDouble() - 0.5) * spread;
                    double vy = dy * speed + 0.015 + (rng.nextDouble() - 0.5) * spread;
                    double vz = dz * speed + (rng.nextDouble() - 0.5) * spread;
                    double px = wx + dx * (localFirstPerson ? 0.22 : 0.06);
                    double py = wy + dy * 0.06;
                    double pz = wz + dz * (localFirstPerson ? 0.22 : 0.06);
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, vx, vy, vz);
                }
                level.addParticle(rng.nextBoolean() ? CYAN_FLAME_DUST : ICE_CORE_DUST,
                        wx + dx * 0.12, wy + dy * 0.12, wz + dz * 0.12,
                        dx * 0.12, dy * 0.10 + 0.01, dz * 0.12);
                if (rng.nextFloat() < 0.22F * breath) {
                    level.addParticle(ParticleTypes.SOUL, wx + dx * 0.18, wy + 0.04, wz + dz * 0.18,
                            dx * 0.05, 0.035, dz * 0.05);
                }
            } else if (spark > 0.05F && rng.nextFloat() < 0.55F * spark) {
                // Tia lửa xanh tinh nghịch lách tách khi 3 đầu cọ mũi / đớp đùa nhau!
                double speed = 0.065 + rng.nextDouble() * 0.045;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz,
                        dx * speed + (rng.nextDouble() - 0.5) * 0.05,
                        dy * speed + 0.025,
                        dz * speed + (rng.nextDouble() - 0.5) * 0.05);
                level.addParticle(ICE_CORE_DUST, wx, wy, wz, 0.0, 0.02, 0.0);
            }
        }

        // Hạt ánh băng tinh nghịch rải theo chóp đuôi phát sáng khi đuôi uốn lên chơi đùa với 3 đầu Hydra (0 sát thương!)
        if (sample.tailPlaySpark > 0.06F && !localFirstPerson && rng.nextFloat() < 0.72F * sample.tailPlaySpark) {
            Vector3f tp = sample.tailTipModelPx;
            double tx = tp.x / 16.0;
            double ty = tp.y / 16.0;
            double tz = -tp.z / 16.0;
            double wx = ex + leftX * tx + fwdX * tz;
            double wy = ey + ty;
            double wz = ez + leftZ * tx + fwdZ * tz;
            level.addParticle(rng.nextBoolean() ? CYAN_FLAME_DUST : ICE_CORE_DUST,
                    wx, wy, wz,
                    (rng.nextDouble() - 0.5) * 0.04,
                    0.025 + rng.nextDouble() * 0.02,
                    (rng.nextDouble() - 0.5) * 0.04);
            if (rng.nextFloat() < 0.35F * sample.tailPlaySpark) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz,
                        (rng.nextDouble() - 0.5) * 0.045,
                        0.02 + rng.nextDouble() * 0.025,
                        (rng.nextDouble() - 0.5) * 0.045);
            }
        }
    }

    private final class HydraArmorEmissiveLayer extends GeoRenderLayer<SpiritHydraArmorItem> {
        HydraArmorEmissiveLayer(GeoRenderer<SpiritHydraArmorItem> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, SpiritHydraArmorItem animatable, BakedGeoModel bakedModel,
                @Nullable RenderType renderType, MultiBufferSource bufferSource,
                @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            Entity entity = SpiritHydraArmorRenderer.this.getCurrentEntity();
            float pulse = SpiritHydraArmorRenderer.this.hydraModel.getCurrentHeartbeatPulse(entity);
            SpiritHydraArmorModel.HeadBreathSample sample = SpiritHydraArmorRenderer.this.hydraModel.getHeadBreathSample(entity);
            float breathBoost = sample != null ? sample.maxBreath * 0.45F : 0.0F;
            float glow = Mth.clamp(Math.max(pulse, breathBoost), 0.0F, 1.0F);
            // Đồng bộ cường độ phát sáng theo nhịp tim kép `lub-dub` & pha phun lửa xanh (0.72 khi nghỉ -> 1.00 rực chói!)
            int r = Mth.clamp(Math.round(Mth.lerp(glow, 0x9A, 0xFF)), 0, 255);
            int g = Mth.clamp(Math.round(Mth.lerp(glow, 0xDC, 0xFF)), 0, 255);
            int b = Mth.clamp(Math.round(Mth.lerp(glow, 0xEB, 0xFF)), 0, 255);
            int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;

            RenderType glowType = RenderType.eyes(GLOW_TEXTURE);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, argb);
        }
    }
}
