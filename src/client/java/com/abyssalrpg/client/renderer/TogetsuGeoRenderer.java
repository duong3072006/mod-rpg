package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.gui.TogetsuColorScreen;
import com.abyssalrpg.client.model.TogetsuGeoModel;
import com.abyssalrpg.client.model.TogetsuGeoModel.Variant;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.TogetsuGlowStyle;
import com.abyssalrpg.item.TogetsuItem;
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
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib 4.8.3 GeoItemRenderer song hình thái cho **Togetsu
 * (`abyssalrpg:togetsu`)**:
 * - `GUI`: tự đổi icon 32×32 sắc nét giữa cặp song đao (`togetsu.png`) và Đại
 * Đao Song Nguyệt (`togetsu_fused.png`).
 * - `GROUND` / `FIXED` / `HEAD` / `NONE`:
 * + Khi ở dạng tách rời: hiển thị ĐỦ CẢ 2 CÂY Trăng Non (Trắng) & Trăng Khuyết
 * (Đen) bắt chéo chữ X 3D!
 * + Khi ở dạng Hợp Nhất: hiển thị 1 thanh Đại Đao Song Nguyệt khổng lồ!
 * - **Cầm tay (F5 & First Person):**
 * + Đồng bộ thời điểm chuyển đổi model ngay giữa pha animation Hợp Nhất
 * (`FUSE_TRANSFORM` ở mốc 50%) và Tách (`UNFUSE_SPLIT` ở mốc 40%)!
 * + Dạng tách: Tay Trái = **Trăng Non (Trắng)**, Tay Phải = **Trăng Khuyết
 * (Đen)** (không đảo ngược gốc vì ma trận Sunraku giải trực tiếp!).
 * + Dạng Hợp Nhất: Tay Phải = **Đại Đao Song Nguyệt (`Sōgengetsu`)** khổng lồ,
 * Tay Trái ẩn bản sao để cầm bằng 2 tay!
 */
public final class TogetsuGeoRenderer extends GeoItemRenderer<TogetsuItem> {
    private static final ResourceLocation GUI_ICON_DUAL = AbyssalRpgMod.id("textures/item/togetsu.png");
    private static final ResourceLocation GUI_ICON_FUSED = AbyssalRpgMod.id("textures/item/togetsu_fused.png");

    private static int currentRenderEntityId = -1;

    private Variant activeVariant = Variant.WANING;
    private boolean currentEmpowered = false;
    private boolean renderingFirstPersonFused = false;
    private TogetsuGlowStyle activeGlowStyle = TogetsuGlowStyle.YIN_YANG;
    private int activeCustomPrimaryRgb = TogetsuGlowStyle.YIN_YANG.defaultPrimaryRgb;
    private int activeCustomSecondaryRgb = TogetsuGlowStyle.YIN_YANG.defaultSecondaryRgb;

    public TogetsuGeoRenderer() {
        super(new TogetsuGeoModel());
        this.addRenderLayer(new TogetsuEmissiveLayer(this));
    }

    public static void setCurrentRenderEntityId(int entityId) {
        currentRenderEntityId = entityId;
    }

    public Variant getActiveVariant() {
        return this.activeVariant;
    }

    public boolean isCurrentEmpowered() {
        return this.currentEmpowered;
    }

    public TogetsuGlowStyle getActiveGlowStyle() {
        return this.activeGlowStyle;
    }

    public int getActiveCustomPrimaryRgb() {
        return this.activeCustomPrimaryRgb;
    }

    public int getActiveCustomSecondaryRgb() {
        return this.activeCustomSecondaryRgb;
    }

    @Override
    public RenderType getRenderType(TogetsuItem animatable, ResourceLocation texture,
            @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        boolean stackFused = TogetsuItem.isFused(stack);
        if (transformType == ItemDisplayContext.GUI) {
            renderCrispGuiIcon(stack, stackFused ? GUI_ICON_FUSED : GUI_ICON_DUAL, poseStack, bufferSource);
            return;
        }

        boolean empowered = TogetsuItem.isWaxingEmpowered(stack);

        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            if (stackFused) {
                poseStack.pushPose();
                poseStack.translate(0.5F, 0.5F, 0.5F);
                poseStack.scale(0.84F, 0.84F, 0.84F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-22.0F));
                poseStack.translate(-0.5F, -0.5F, -0.5F);
                renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.FUSED,
                        empowered);
                poseStack.popPose();
            } else {
                renderDualDisplayPair(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay,
                        empowered);
            }
            return;
        }

        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        int entityId = currentRenderEntityId;
        if (entityId == -1) {
            var localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null && localPlayer.getMainHandItem() == stack) {
                entityId = localPlayer.getId();
            }
        }
        boolean renderFused = (entityId != -1)
                ? TogetsuAnimManager.shouldRenderAsFused(entityId, stack, partialTick)
                : stackFused;

        boolean isLeftHand = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        boolean isFirstPerson = (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);

        if (renderFused) {
            // Ở trạng thái Đại Đao Song Nguyệt chỉ hiển thị 1 thanh Đại Đao duy nhất ở tay
            // phải!
            if (isLeftHand) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            if (isFirstPerson) {
                // Bố cục Góc nhìn Thứ 1 dựng thẳng / hơi nghiêng nhẹ giống dáng trường kiếm
                // Netherite (ZP = 16°):
                // - Lưỡi sắc cong ngoài hướng Trái/Trước về phía tâm ngắm & mục tiêu (không bị
                // ngược!).
                // - Ngạnh bạc + chuôi đôi bọc da đỏ nằm gọn góc dưới-phải, mũi trăng lưỡi liềm
                // vươn cao sắc nét bên phải màn hình.
                // - Kết hợp kéo dài riêng bone `blade_body` trong `renderRecursively` để cán
                // đôi & cánh móc sau không bị méo!
                float fpScale = WeaponsConfig.get().togetsuFpFusedScale;
                poseStack.translate(0.255F, -0.095F, -0.340F);
                poseStack.mulPose(Axis.YP.rotationDegrees(200.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(1.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(16.0F));
                poseStack.scale(fpScale, fpScale, fpScale);
            } else {
                // Giảm xuống 0.75F để Song Nguyệt Hợp Nhất thanh thoát chuẩn kiếm cong 1 tay,
                // khoảng hở mặt đất rộng rãi (yMin=0.47 Idle / 0.67 Sprint) và 0 chạm đất!
                poseStack.scale(0.75F, 0.75F, 0.75F);
            }
            // Dịch phần tay cầm vô sát lòng bàn tay nhân vật hơn (`y -= 1.8px, z -=
            // 0.55px`) để cầm cực chắc chắn!
            poseStack.translate(0.0F, -0.1125F, -0.0344F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            boolean prevFpFused = this.renderingFirstPersonFused;
            this.renderingFirstPersonFused = isFirstPerson;
            try {
                renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.FUSED,
                        empowered);
            } finally {
                this.renderingFirstPersonFused = prevFpFused;
                poseStack.popPose();
            }
            return;
        }

        // Dạng Song Đao (khớp 1:1 ma trận Reverse Grip tay trái F5 của
        // DualBladeGeoRenderer để 0 gimbal-twist & mượt tuyệt đối!)
        Variant variant = isLeftHand ? Variant.WAXING : Variant.WANING;
        float scale = isFirstPerson ? 0.74F : 0.78F;
        boolean reverseLeftThird = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (reverseLeftThird) {
            poseStack.translate(-0.025F, 0.0F, 0.010F);
            poseStack.mulPose(Axis.XP.rotationDegrees(112.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-8.0F));
        }
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, variant, empowered);
        poseStack.popPose();
    }

    private void renderDualDisplayPair(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay,
            boolean empowered) {
        // Thanh 1: Trăng Khuyết (Waning Đen) nghiêng sang trái
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.78F, 0.78F, 0.78F);
        poseStack.translate(0.055F, 0.24F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-36.0F));
        poseStack.translate(0.0F, -0.24F, 0.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.WANING,
                empowered);
        poseStack.popPose();

        // Thanh 2: Trăng Non (Waxing Trắng) đối xứng nghiêng sang phải tạo chữ X
        // Âm–Dương!
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.78F, 0.78F, 0.78F);
        poseStack.translate(-0.055F, 0.24F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-36.0F));
        poseStack.translate(0.0F, -0.24F, 0.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.WAXING,
                empowered);
        poseStack.popPose();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, TogetsuItem animatable, GeoBone bone, RenderType renderType,
            MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
            float partialTick, int packedLight, int packedOverlay, int colour) {
        if (this.activeVariant == Variant.FUSED && this.renderingFirstPersonFused
                && "blade_body".equals(bone.getName())) {
            float lenScale = WeaponsConfig.get().togetsuFpFusedBladeLength;
            float oldSy = bone.getScaleY();
            bone.setScaleY(oldSy * lenScale);
            try {
                super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                        isReRender, partialTick, packedLight, packedOverlay, colour);
            } finally {
                bone.setScaleY(oldSy);
            }
            return;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private void renderVariant(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay,
            Variant variant, boolean empowered) {
        Variant prevVar = this.activeVariant;
        boolean prevEmp = this.currentEmpowered;
        TogetsuGlowStyle prevStyle = this.activeGlowStyle;
        int prevPri = this.activeCustomPrimaryRgb;
        int prevSec = this.activeCustomSecondaryRgb;

        this.activeVariant = variant;
        this.currentEmpowered = empowered;

        // Nếu người chơi đang mở bảng pha màu TogetsuColorScreen và đây là thanh kiếm
        // trên tay họ -> xem trước trực tiếp 100% realtime!
        Minecraft mc = Minecraft.getInstance();
        boolean useLivePreview = TogetsuColorScreen.hasLivePreview()
                && mc.player != null
                && mc.player.getMainHandItem() == stack;
        if (useLivePreview) {
            this.activeGlowStyle = TogetsuColorScreen.getLivePreviewStyle();
            this.activeCustomPrimaryRgb = TogetsuColorScreen.getLivePreviewPrimaryRgb();
            this.activeCustomSecondaryRgb = TogetsuColorScreen.getLivePreviewSecondaryRgb();
        } else {
            this.activeGlowStyle = TogetsuItem.getGlowStyle(stack);
            this.activeCustomPrimaryRgb = TogetsuItem.getCustomPrimaryRgb(stack);
            this.activeCustomSecondaryRgb = TogetsuItem.getCustomSecondaryRgb(stack);
        }
        // Giữ lưỡi Trăng Non (Trắng Bạc) luôn sáng bạc rực rỡ ngay cả trong đêm tối để tương phản Âm–Dương sắc nét với lưỡi Đen!
        int effectiveLight = packedLight;
        if (variant == Variant.WAXING) {
            int blk = Math.max(LightTexture.block(packedLight), 12);
            int sky = Math.max(LightTexture.sky(packedLight), 14);
            effectiveLight = LightTexture.pack(blk, sky);
        }
        try {
            super.renderByItem(stack, transformType, poseStack, bufferSource, effectiveLight, packedOverlay);
        } finally {
            this.activeVariant = prevVar;
            this.currentEmpowered = prevEmp;
            this.activeGlowStyle = prevStyle;
            this.activeCustomPrimaryRgb = prevPri;
            this.activeCustomSecondaryRgb = prevSec;
        }
    }

    private static void renderCrispGuiIcon(ItemStack stack, ResourceLocation tex, PoseStack poseStack,
            MultiBufferSource bufferSource) {
        poseStack.pushPose();
        RenderType type = RenderType.entityTranslucent(tex);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(bufferSource, type, true, stack.hasFoil());
        PoseStack.Pose pose = poseStack.last();
        float z = 0.5F;

        vc.addVertex(pose, 0.0F, 0.0F, z)
                .setColor(255, 255, 255, 255).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 0.0F, z)
                .setColor(255, 255, 255, 255).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 1.0F, z)
                .setColor(255, 255, 255, 255).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 0.0F, 1.0F, z)
                .setColor(255, 255, 255, 255).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);

        poseStack.popPose();
    }

    private static final class TogetsuEmissiveLayer extends GeoRenderLayer<TogetsuItem> {
        TogetsuEmissiveLayer(TogetsuGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, TogetsuItem animatable, BakedGeoModel bakedModel,
                @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                float partialTick, int packedLight, int packedOverlay) {
            if (!(getRenderer() instanceof TogetsuGeoRenderer tr)) {
                return;
            }
            Variant var = tr.getActiveVariant();
            TogetsuGlowStyle style = tr.getActiveGlowStyle();

            Minecraft mc = Minecraft.getInstance();
            float timeSec = (mc.level != null ? ((mc.level.getGameTime() % 240000L) + partialTick) : partialTick)
                    / 20.0F;
            boolean empowered = tr.isCurrentEmpowered();
            float speed = empowered ? 6.5F : (var == Variant.FUSED ? 3.4F : 2.6F);
            float pulseLo = empowered ? 0.88F : (var == Variant.FUSED ? 0.86F : 0.78F);
            float priPulse = Mth.lerp(0.5F - 0.5F * Mth.cos(timeSec * speed), pulseLo, 1.00F);
            // Nhịp thở lệch pha nhẹ cho kênh Phụ tạo chiều sâu ánh sáng Âm–Dương sống động
            // trên Song Nguyệt!
            float secPulse = Mth.lerp(0.5F - 0.5F * Mth.cos(timeSec * speed + 0.9F), pulseLo, 1.00F);
            float beadPulse = Mth.lerp(0.5F - 0.5F * Mth.cos(timeSec * 2.2F + 1.6F), 0.82F, 1.00F);

            int priRgb = style.resolvePrimaryRgb(tr.getActiveCustomPrimaryRgb(), timeSec);
            int secRgb = style.resolveSecondaryRgb(tr.getActiveCustomSecondaryRgb(), timeSec);
            int beadRgb = style.resolveBeadRgb(tr.getActiveCustomPrimaryRgb(), timeSec);

            ResourceLocation glowPrimary = TogetsuGeoModel.glowPrimaryFor(var);
            if (glowPrimary != null) {
                renderEmissiveChannel(bakedModel, poseStack, bufferSource, animatable,
                        glowPrimary, partialTick, packedOverlay, packTintedColor(priRgb, priPulse));
            }

            ResourceLocation glowSecondary = TogetsuGeoModel.glowSecondaryFor(var);
            if (glowSecondary != null) {
                renderEmissiveChannel(bakedModel, poseStack, bufferSource, animatable,
                        glowSecondary, partialTick, packedOverlay, packTintedColor(secRgb, secPulse));
            }

            ResourceLocation glowAccent = TogetsuGeoModel.glowAccentFor(var);
            if (glowAccent != null) {
                renderEmissiveChannel(bakedModel, poseStack, bufferSource, animatable,
                        glowAccent, partialTick, packedOverlay, packTintedColor(beadRgb, beadPulse));
            }
        }

        private void renderEmissiveChannel(BakedGeoModel bakedModel, PoseStack poseStack,
                MultiBufferSource bufferSource, TogetsuItem animatable,
                ResourceLocation glowTex, float partialTick,
                int packedOverlay, int packedColor) {
            RenderType glowType = RenderType.eyes(glowTex);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
                    packedColor);
        }

        private static int packTintedColor(int rgb, float pulse) {
            int r = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * pulse), 0, 255);
            int g = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * pulse), 0, 255);
            int b = Mth.clamp(Math.round((rgb & 0xFF) * pulse), 0, 255);
            return FastColor.ARGB32.color(255, r, g, b);
        }
    }
}