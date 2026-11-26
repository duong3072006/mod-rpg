package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.MuramasaAnimManager;
import com.abyssalrpg.client.model.MuramasaGeoModel;
import com.abyssalrpg.client.model.MuramasaGeoModel.Variant;
import com.abyssalrpg.item.MuramasaItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib 4.8.3 GeoItemRenderer cho **Yêu Đao Muramasa Katana & Vỏ Kiếm Rồng Đỏ (Saya)**:
 * - `GUI`: Icon 32×32 sắc nét hiển thị Katana lưỡi vàng phát sáng + vỏ kiếm rồng đỏ.
 * - `GROUND` / `FIXED`: Hiển thị bộ đôi Katana + Vỏ kiếm đặt song song đúng chuẩn bố cục Sketchfab!
 * - `RIGHT_HAND`: Katana Muramasa với cạnh lưỡi phát sáng vàng rực (`RenderType.eyes`) & bùng cháy đỏ-vàng khi bật Huyết Thệ.
 * - `LEFT_HAND` (khi tay phụ trống): Vỏ kiếm sơn mài đen vẽ rồng đỏ chuẩn Samurai Iaijutsu!
 */
public final class MuramasaGeoRenderer extends GeoItemRenderer<MuramasaItem> {
    private static final ResourceLocation GUI_ICON = AbyssalRpgMod.id("textures/item/muramasa.png");
    private static int currentRenderEntityId = -1;

    private Variant activeVariant = Variant.KATANA;
    private boolean awakenedActive = false;
    private float glowBoost = 0.0F;

    public MuramasaGeoRenderer() {
        super(new MuramasaGeoModel());
        this.addRenderLayer(new MuramasaEmissiveLayer(this));
    }

    public static void setCurrentRenderEntityId(int entityId) {
        currentRenderEntityId = entityId;
    }

    public Variant getActiveVariant() {
        return this.activeVariant;
    }

    public boolean isAwakenedActive() {
        return this.awakenedActive;
    }

    public float getGlowBoost() {
        return this.glowBoost;
    }

    @Override
    public RenderType getRenderType(MuramasaItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (transformType == ItemDisplayContext.GUI) {
            renderCrispGuiIcon(stack, poseStack, bufferSource);
            return;
        }
        boolean awakened = MuramasaItem.isAwakened(stack);

        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            // Hiển thị đồng thời Katana + Vỏ kiếm đặt song song đúng bố cục Sketchfab!
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.0F, 0.08F, -0.10F);
            poseStack.scale(0.92F, 0.92F, 0.92F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.KATANA, awakened, 0.0F);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.0F, -0.06F, 0.14F);
            poseStack.scale(0.92F, 0.92F, 0.92F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.SCABBARD, awakened, 0.0F);
            poseStack.popPose();
            return;
        }

        boolean isLeftHand = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        boolean isFirstPerson = (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);

        int entityId = currentRenderEntityId;
        if (entityId == -1) {
            var lp = Minecraft.getInstance().player;
            if (lp != null && lp.getMainHandItem() == stack) {
                entityId = lp.getId();
            }
        }
        float boost = (entityId != -1) ? MuramasaAnimManager.getGlowBoost(entityId) : 0.0F;
        Variant variant = isLeftHand ? Variant.SCABBARD : Variant.KATANA;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (isFirstPerson) {
            if (isLeftHand) {
                // Vỏ kiếm rồng đỏ (đã lật đúng đầu 180° & hạ thấp ~30% góc dưới-trái!):
                // Miệng sắt cổ Koiguchi + dây thắt đỏ-đen Sageo hướng lên trên-về trước, thân rồng đỏ xuôi gọn xuống dưới-trái!
                poseStack.translate(-0.12F, 0.06F, -0.24F);
                poseStack.mulPose(Axis.YP.rotationDegrees(-42.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-24.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(152.0F));
                poseStack.scale(0.82F, 0.82F, 0.82F);
            } else {
                // Katana góc nhìn thứ 1 (khớp 1:1 vùng viền đỏ người chơi khoanh!):
                // Chuôi & tsuba neo gọn góc dưới-phải, lưỡi sắc phát sáng vàng vươn chéo lên bầu trời trên-phải!
                poseStack.translate(0.12F, -0.04F, -0.12F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(29.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(44.0F));
                poseStack.scale(0.96F, 0.90F, 0.96F);
            }
        }
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, variant, awakened, boost);
        poseStack.popPose();
    }

    private void renderVariant(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                               MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                               Variant variant, boolean awakened, float boost) {
        Variant prevVar = this.activeVariant;
        boolean prevAwk = this.awakenedActive;
        float prevBoost = this.glowBoost;
        this.activeVariant = variant;
        this.awakenedActive = awakened;
        this.glowBoost = boost;
        try {
            super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        } finally {
            this.activeVariant = prevVar;
            this.awakenedActive = prevAwk;
            this.glowBoost = prevBoost;
        }
    }

    private static void renderCrispGuiIcon(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource) {
        poseStack.pushPose();
        RenderType type = RenderType.entityTranslucent(GUI_ICON);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(bufferSource, type, true, stack.hasFoil());
        PoseStack.Pose pose = poseStack.last();
        float z = 0.5F;
        vc.addVertex(pose, 0.0F, 0.0F, z).setColor(255, 255, 255, 255).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, 1.0F, 0.0F, z).setColor(255, 255, 255, 255).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, 1.0F, 1.0F, z).setColor(255, 255, 255, 255).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, 0.0F, 1.0F, z).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        poseStack.popPose();
    }

    private static final class MuramasaEmissiveLayer extends GeoRenderLayer<MuramasaItem> {
        MuramasaEmissiveLayer(MuramasaGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MuramasaItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!(getRenderer() instanceof MuramasaGeoRenderer mr)) {
                return;
            }
            Variant var = mr.getActiveVariant();
            boolean awakened = mr.isAwakenedActive();
            float boost = mr.getGlowBoost();
            Minecraft mc = Minecraft.getInstance();
            float timeSec = (mc.level != null ? ((mc.level.getGameTime() % 240000L) + partialTick) : partialTick) / 20.0F;

            float pulse = Mth.lerp(0.5F - 0.5F * Mth.cos(timeSec * (awakened ? 5.0F : 2.6F)), 0.86F, 1.00F);
            pulse = Mth.clamp(pulse + boost * 0.25F, 0.0F, 1.0F);
            int r = Mth.clamp(Math.round(255.0F * pulse), 0, 255);
            int g = Mth.clamp(Math.round((awakened && var == Variant.KATANA ? 172.0F : 255.0F) * pulse), 0, 255);
            int b = Mth.clamp(Math.round((awakened && var == Variant.KATANA ? 76.0F : 255.0F) * pulse), 0, 255);
            int col = FastColor.ARGB32.color(255, r, g, b);

            ResourceLocation glowTex = MuramasaGeoModel.glowFor(var);
            RenderType glowType = RenderType.eyes(glowTex);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, col);
        }
    }
}
