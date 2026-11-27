package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.ExcaliburClientState;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.model.ExcaliburGeoModel;
import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib 4.8.3 GeoItemRenderer cho **Excalibur — Thánh Kiếm Vàng Kim**:
 * - Góc nhìn thứ nhất dùng **Camera-Space Quaternion Slerp** (giữ chuôi kiếm gọn góc dưới phải, tuyệt đối không
 *   vẩy kiếm văng lên giữa bầu trời khi chém/đỡ; đỡ trên không xoay phẳng **mặt ngang** chắn trên đỉnh tầm nhìn!).
 * - Hiển thị trực tiếp **cánh tay mang đúng skin người chơi** (`PlayerRenderer#renderRightHand / renderLeftHand`,
 *   tự động hỗ trợ Steve 4px, Alex Slim 3px & lớp tay áo ngoài) nắm chặt chuôi kiếm xanh-vàng đồng bộ 100%!
 */
public final class ExcaliburGeoRenderer extends GeoItemRenderer<ExcaliburItem> {
    private static final ResourceLocation GUI_ICON_GOLDEN = AbyssalRpgMod.id("textures/item/excalibur.png");
    private static int currentRenderEntityId = -1;
    private static @Nullable ExcaliburGeoRenderer instance;

    // Khớp 1:1 display.firstperson_righthand trong excalibur.json để khử sạch về đúng hệ Camera-Space (giữ nguyên bobView!)
    private static final float FP_JSON_TX = 3.5F / 16.0F;
    private static final float FP_JSON_TY = -5.0F / 16.0F;
    private static final float FP_JSON_TZ = -3.5F / 16.0F;
    private static final float FP_JSON_RX = -10.0F * Mth.DEG_TO_RAD;
    private static final float FP_JSON_RY = -16.0F * Mth.DEG_TO_RAD;
    private static final float FP_JSON_RZ = 15.0F * Mth.DEG_TO_RAD;
    private static final float FP_JSON_SCALE = 0.68F;

    // Quaternion mặc định Camera-Space dùng khi cầm Excalibur ở tay phụ (tay chính do ExcaliburFirstPersonRigRenderer xử lý)
    private static final Quaternionf Q_FP_IDLE = new Quaternionf(-0.13355F, +0.85229F, -0.02678F, +0.50502F);

    private float chargeBoost = 0.0F;
    private float energyRatio = 0.0F;

    public ExcaliburGeoRenderer() {
        super(new ExcaliburGeoModel());
        this.addRenderLayer(new ExcaliburEmissiveLayer(this));
        instance = this;
    }

    public static void setCurrentRenderEntityId(int entityId) {
        currentRenderEntityId = entityId;
    }

    private static ExcaliburGeoRenderer getOrCreateInstance() {
        if (instance == null) {
            instance = new ExcaliburGeoRenderer();
        }
        return instance;
    }

    /** Vẽ trực tiếp mô hình 3D Excalibur + Emissive Glow ở tư thế Third-Person (dùng chung cho F5 & First-Person 3rd-Rig!). */
    public static void renderThirdPersonDirect(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
                                               int packedLight, int packedOverlay, int entityId) {
        ExcaliburGeoRenderer renderer = getOrCreateInstance();
        float boost = (entityId != -1) ? ExcaliburAnimManager.getGlowBoost(entityId) : 0.0F;
        float energyFrac = (entityId != -1)
                ? Mth.clamp(ExcaliburClientState.getPassive(entityId, stack).energy / ExcaliburItem.MAX_ENERGY, 0.0F, 1.0F)
                : Mth.clamp(ExcaliburItem.getEnergy(stack) / ExcaliburItem.MAX_ENERGY, 0.0F, 1.0F);
        renderer.renderExcalibur(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, poseStack, bufferSource,
                packedLight, packedOverlay, boost, energyFrac);
    }

    public float getChargeBoost() {
        return this.chargeBoost;
    }

    public float getEnergyRatio() {
        return this.energyRatio;
    }

    @Override
    public RenderType getRenderType(ExcaliburItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutout(texture);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (transformType == ItemDisplayContext.GUI) {
            renderCrispGuiIcon(stack, GUI_ICON_GOLDEN, poseStack, bufferSource);
            return;
        }

        boolean isFirstPerson = (transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        boolean isLeftHand = (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        int entityId = currentRenderEntityId;
        if (entityId == -1) {
            var lp = Minecraft.getInstance().player;
            if (lp != null && (isFirstPerson || lp.getMainHandItem().is(ModItems.EXCALIBUR))) {
                entityId = lp.getId();
            }
        }
        float boost = (entityId != -1) ? ExcaliburAnimManager.getGlowBoost(entityId) : 0.0F;

        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(1.06F, 1.08F, 1.06F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderExcalibur(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, boost, 0.5F);
            poseStack.popPose();
            return;
        }

        float energyFrac = (entityId != -1)
                ? Mth.clamp(ExcaliburClientState.getPassive(entityId, stack).energy / ExcaliburItem.MAX_ENERGY, 0.0F, 1.0F)
                : Mth.clamp(ExcaliburItem.getEnergy(stack) / ExcaliburItem.MAX_ENERGY, 0.0F, 1.0F);

        if (isFirstPerson) {
            renderFirstPersonExcalibur(stack, transformType, entityId, isLeftHand, poseStack, bufferSource,
                    packedLight, packedOverlay, boost, energyFrac);
        } else {
            // Khi Excalibur đang tra gọn (hoặc đang trượt vào) trong vỏ Avalon bên hông trái -> không vẽ trùng ở tay phải!
            float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            if (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    && currentRenderEntityId != -1 && ExcaliburAnimManager.isSwordInHipAvalon(currentRenderEntityId, pt)) {
                return;
            }
            renderExcalibur(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, boost, energyFrac);
        }
    }

    /**
     * Render Excalibur ở góc nhìn thứ nhất cho tay phụ (`OFF_HAND`, tay chính do `ExcaliburFirstPersonRigRenderer` đảm nhiệm).
     */
    private void renderFirstPersonExcalibur(ItemStack stack, ItemDisplayContext transformType,
                                            int entityId, boolean isLeftHand,
                                            PoseStack poseStack, MultiBufferSource bufferSource,
                                            int packedLight, int packedOverlay,
                                            float boost, float energyFrac) {
        float hx = +0.58F;
        float hy = -0.54F;
        float hz = -1.04F;
        float sc = FP_JSON_SCALE;
        Quaternionf swordQ = new Quaternionf(Q_FP_IDLE);

        float side = isLeftHand ? -1.0F : 1.0F;
        if (isLeftHand) {
            hx = -hx;
            swordQ.set(swordQ.x, -swordQ.y, -swordQ.z, swordQ.w);
        }

        poseStack.pushPose();
        // Khử chính xác ItemRenderer(-0.5) + ItemTransform(excalibur.json) + vanilla(±0.56,-0.52,-0.72) về Camera-Space (giữ nguyên bobView!)
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(1.0F / FP_JSON_SCALE, 1.0F / FP_JSON_SCALE, 1.0F / FP_JSON_SCALE);
        poseStack.mulPose(new Quaternionf().rotationXYZ(FP_JSON_RX, side * FP_JSON_RY, side * FP_JSON_RZ).conjugate());
        poseStack.translate(-side * FP_JSON_TX, -FP_JSON_TY, -FP_JSON_TZ);
        poseStack.translate(-side * 0.56F, +0.52F, +0.72F);

        // 1) Vẽ riêng Thánh Kiếm Excalibur phóng lớn `0.58F` quanh đúng tâm lòng bàn tay nắm chuôi (Y = -2.5px)
        //    để lưỡi kiếm vươn dài uy lực ra phía trước mà không đâm ngược vào cánh tay!
        poseStack.pushPose();
        poseStack.translate(hx, hy, hz);
        poseStack.mulPose(swordQ);
        poseStack.translate(0.0F, (-2.5F / 16.0F) * 0.45F, 0.0F);
        poseStack.scale(sc, sc, sc);
        poseStack.translate(0.0F, +2.5F / 16.0F, 0.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderExcalibur(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, boost, energyFrac);
        poseStack.popPose();

        // 2) Giữ nguyên tuyệt đối kích thước & vị trí cánh tay người chơi (neo tại chuẩn 0.45F, không bị scale theo kiếm)!
        renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, isLeftHand, hx, hy, hz, 0.45F, swordQ, packedLight);

        poseStack.popPose();
    }

    /**
     * Render cánh tay thật (`PlayerRenderer#renderRightHand / renderLeftHand`) dùng đúng skin & lớp tay áo của
     * `AbstractClientPlayer`, tự động hỗ trợ cả model Classic (4px) và Slim Alex (3px), nắm chặt đúng chuôi xanh-vàng
     * của Excalibur và hướng khuỷu/vai mượt về góc dưới màn hình ở mọi tư thế (Idle, Đỡ, Đỡ Trên Không, Combo, Ult)!
     */
    private static void renderFirstPersonPlayerArm(PoseStack poseStack, MultiBufferSource bufferSource,
                                                   int entityId, boolean isLeftHand,
                                                   float hx, float hy, float hz, float swordScale,
                                                   Quaternionf swordQ, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer acp = null;
        if (mc.level != null && entityId != -1 && mc.level.getEntity(entityId) instanceof AbstractClientPlayer p) {
            acp = p;
        } else if (mc.player != null) {
            acp = mc.player;
        }
        if (acp == null || acp.isInvisible()) {
            return;
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(acp) instanceof PlayerRenderer playerRenderer)) {
            return;
        }

        // Tâm tay cầm nắm trên chuôi kiếm màu xanh (Y = -2.5px trong excalibur.geo.json) quy đổi sang Camera-Space!
        Vector3f gripCam = new Vector3f(0.0F, (-2.5F / 16.0F) * swordScale, 0.0F);
        swordQ.transform(gripCam).add(hx, hy, hz);

        // Neo vai cố định ngoài mép dưới cùng của màn hình -> cánh tay luôn vươn tự nhiên, không bao giờ bay ngược!
        float side = isLeftHand ? -1.0F : 1.0F;
        Vector3f shoulderCam = new Vector3f(side * 0.64F, -0.78F, +0.10F);

        // +Y_arm của PlayerModel.rightArm đi từ vai (Y=-2px) tới nắm đấm (Y=+10px) -> căn trùng hướng vai -> chuôi kiếm!
        Vector3f yArm = new Vector3f(gripCam).sub(shoulderCam);
        if (yArm.lengthSquared() < 1e-6F) {
            return;
        }
        yArm.normalize();

        Vector3f upHint = (Math.abs(yArm.y) > 0.92F) ? new Vector3f(0.0F, 0.0F, -1.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f xArm = new Vector3f(upHint).cross(yArm).normalize();
        Vector3f zArm = new Vector3f(xArm).cross(yArm).normalize();
        Quaternionf armQ = new Matrix3f()
                .setColumn(0, xArm)
                .setColumn(1, yArm)
                .setColumn(2, zArm)
                .getNormalizedRotation(new Quaternionf());

        boolean slim = (acp.getSkin().model() == PlayerSkin.Model.SLIM);
        float fistOffX = (isLeftHand ? -1.0F : 1.0F) * (slim ? (5.5F / 16.0F) : (6.0F / 16.0F));
        float fistOffY = slim ? (-11.0F / 16.0F) : (-10.5F / 16.0F);
        float armScale = 0.45F;

        poseStack.pushPose();
        poseStack.translate(gripCam.x, gripCam.y, gripCam.z);
        poseStack.mulPose(armQ);
        poseStack.scale(armScale, armScale, armScale);
        poseStack.translate(fistOffX, fistOffY, 0.0F);
        if (isLeftHand) {
            playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, acp);
        } else {
            playerRenderer.renderRightHand(poseStack, bufferSource, packedLight, acp);
        }
        poseStack.popPose();
    }

    private static final float[] HIP_AVALON_PARAMS = new float[7];

    /**
     * Vẽ Vỏ Kiếm Avalon 3D thon gọn áp sát dọc bên hông trái — đồng bộ trực tiếp theo State Machine:
     * - Đứng chờ (`idle_sheathed`): góc `36°` sau-dưới, mặt rộng áp phẳng sườn trái, không chạm cỏ.
     * - Chạy lúc tra vỏ (`run_sheathed`): khóa chặt góc `42°` sau-dưới trong 0.15s chuyển tiếp, không xuyên đùi.
     * - Cất kiếm (`SHEATHE_AVALON`, `0.55s`): nâng miệng vỏ đón kiếm ở GĐ 1, cho Excalibur trượt thẳng dọc trục vào vỏ
     *   đến khi chắn tay chạm khít cổ vỏ tại mốc `0.40s` ("Clink!"), rồi thu về hông trái!
     */
    public static void renderHipAvalon(PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, int packedOverlay,
                                       ItemStack sheathedStack, int entityId, float partialTick) {
        int blk = Math.max(LightTexture.block(packedLight), 12);
        int sky = Math.max(LightTexture.sky(packedLight), 14);
        int scabLight = LightTexture.pack(blk, sky);

        ExcaliburAnimManager.fillHipAvalonTransform(entityId, partialTick, HIP_AVALON_PARAMS);
        float tx = HIP_AVALON_PARAMS[0];
        float ty = HIP_AVALON_PARAMS[1];
        float tz = HIP_AVALON_PARAMS[2];
        float rxDeg = HIP_AVALON_PARAMS[3];
        float ryDeg = HIP_AVALON_PARAMS[4];
        float rzDeg = HIP_AVALON_PARAMS[5];
        float slidePx = HIP_AVALON_PARAMS[6];

        poseStack.pushPose();
        // Trong tọa độ hm.body: +X = hông trái, +Y = xuống dưới, +Z = về sau.
        poseStack.translate(tx, ty, tz);
        poseStack.mulPose(Axis.YP.rotationDegrees(ryDeg));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rzDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(rxDeg));

        if (!sheathedStack.isEmpty()) {
            ExcaliburGeoRenderer renderer = getOrCreateInstance();
            float boost = (entityId != -1) ? ExcaliburAnimManager.getGlowBoost(entityId) : 0.0F;
            float energyFrac = (entityId != -1)
                    ? Mth.clamp(ExcaliburClientState.getPassive(entityId, sheathedStack).energy / ExcaliburItem.MAX_ENERGY, 0.0F, 1.0F)
                    : Mth.clamp(ExcaliburItem.getEnergy(sheathedStack) / ExcaliburItem.MAX_ENERGY, 0.0F, 1.0F);
            poseStack.pushPose();
            poseStack.scale(0.58F, 0.82F, 0.64F);
            poseStack.translate(0.0F, (-4.4F + slidePx) / 16.0F, 0.0F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderer.renderExcalibur(sheathedStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    poseStack, bufferSource, packedLight, packedOverlay, boost, energyFrac);
            poseStack.popPose();
        }

        // Bóp gọn chiều ngang & độ dày vỏ Avalon về [0.68, 0.82, 0.68] để thân vỏ thanh mảnh, không chạm mặt cỏ!
        poseStack.pushPose();
        poseStack.scale(0.68F, 0.82F, 0.68F);
        poseStack.translate(0.0F, -4.4F / 16.0F, 0.0F);
        drawAvalonScabbardMesh(poseStack, bufferSource, scabLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    /** Dựng mesh 3D Vỏ Kiếm Avalon thon gọn (vàng kim + tấm xanh hoàng gia + gia huy thánh kiếm phát sáng) ôm khít lưỡi Excalibur! */
    private static void drawAvalonScabbardMesh(PoseStack poseStack, MultiBufferSource bufferSource,
                                               int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(ExcaliburGeoModel.TEX_GOLDEN));

        // 1) Thân vỏ Avalon vàng kim thon mảnh ôm gọn lưỡi kiếm từ cổ vỏ (3.6px) tới mũi kiếm (27.4px)
        emitTexturedBox(vc, pose, -0.70F, 3.6F, -1.36F, +0.70F, 27.4F, +1.36F,
                0.66F, 0.48F, 0.77F, 0.58F, packedLight, packedOverlay);
        // 2) Cổ vỏ (Locket) vàng sáng ngay sát dưới chắn tay cánh chim của Excalibur
        emitTexturedBox(vc, pose, -0.82F, 3.3F, -1.52F, +0.82F, 5.4F, +1.52F,
                0.79F, 0.48F, 0.90F, 0.58F, packedLight, packedOverlay);
        // 3) Hai tấm ốp xanh lam hoàng gia áp phẳng dọc mặt rộng của thân vỏ
        emitTexturedBox(vc, pose, -0.75F, 6.2F, -0.96F, +0.75F, 12.8F, +0.96F,
                0.54F, 0.48F, 0.64F, 0.58F, packedLight, packedOverlay);
        emitTexturedBox(vc, pose, -0.75F, 16.2F, -0.96F, +0.75F, 23.8F, +0.96F,
                0.54F, 0.48F, 0.64F, 0.58F, packedLight, packedOverlay);
        // 4) Gia huy kim cương vàng trên nền xanh lam giữa thân vỏ Avalon
        emitTexturedBox(vc, pose, -0.78F, 13.0F, -1.08F, +0.78F, 16.0F, +1.08F,
                0.415F, 0.262F, 0.578F, 0.422F, packedLight, packedOverlay);
        // 5) Đuôi bọc mũi vỏ (Chape) vàng rực thuôn nhọn gọn gàng
        emitTexturedBox(vc, pose, -0.74F, 26.8F, -1.12F, +0.74F, 28.2F, +1.12F,
                0.79F, 0.48F, 0.90F, 0.58F, packedLight, packedOverlay);
        emitTexturedBox(vc, pose, -0.48F, 28.2F, -0.68F, +0.48F, 29.1F, +0.68F,
                0.79F, 0.48F, 0.90F, 0.58F, packedLight, packedOverlay);

        // Lớp phát sáng nhẹ cho cổ vỏ & mũi vỏ Avalon
        VertexConsumer glowVc = bufferSource.getBuffer(RenderType.eyes(ExcaliburGeoModel.GLOW_GOLDEN));
        emitTexturedBox(glowVc, pose, -0.84F, 3.3F, -1.54F, +0.84F, 5.4F, +1.54F,
                0.79F, 0.60F, 0.89F, 0.70F, LightTexture.FULL_BRIGHT, packedOverlay);
        emitTexturedBox(glowVc, pose, -0.76F, 26.8F, -1.14F, +0.76F, 28.2F, +1.14F,
                0.79F, 0.60F, 0.89F, 0.70F, LightTexture.FULL_BRIGHT, packedOverlay);
    }

    private static void emitTexturedBox(VertexConsumer vc, PoseStack.Pose pose,
                                        float x0Px, float y0Px, float z0Px,
                                        float x1Px, float y1Px, float z1Px,
                                        float u0, float v0, float u1, float v1,
                                        int light, int overlay) {
        float x0 = x0Px / 16.0F, y0 = y0Px / 16.0F, z0 = z0Px / 16.0F;
        float x1 = x1Px / 16.0F, y1 = y1Px / 16.0F, z1 = z1Px / 16.0F;
        // Front (+Z)
        emitQuad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1, 0, 0, 1, light, overlay);
        // Back (-Z)
        emitQuad(vc, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, v0, u1, v1, 0, 0, -1, light, overlay);
        // Right (+X)
        emitQuad(vc, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, v0, u1, v1, 1, 0, 0, light, overlay);
        // Left (-X)
        emitQuad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v0, u1, v1, -1, 0, 0, light, overlay);
        // Top (+Y)
        emitQuad(vc, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1, 0, 1, 0, light, overlay);
        // Bottom (-Y)
        emitQuad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1, 0, -1, 0, light, overlay);
    }

    private static void emitQuad(VertexConsumer vc, PoseStack.Pose pose,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float u0, float v0, float u1, float v1,
                                 float nx, float ny, float nz, int light, int overlay) {
        vc.addVertex(pose, x0, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u1, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u0, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private void renderExcalibur(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                 float boost, float energyFrac) {
        float prevBoost = this.chargeBoost;
        float prevEnergy = this.energyRatio;
        this.chargeBoost = boost;
        this.energyRatio = energyFrac;

        int blk = Math.max(LightTexture.block(packedLight), (energyFrac >= 0.99F || boost > 0.1F) ? 15 : 13);
        int sky = Math.max(LightTexture.sky(packedLight), (energyFrac >= 0.99F || boost > 0.1F) ? 15 : 14);
        int effectiveLight = LightTexture.pack(blk, sky);
        try {
            super.renderByItem(stack, transformType, poseStack, bufferSource, effectiveLight, packedOverlay);
        } finally {
            this.chargeBoost = prevBoost;
            this.energyRatio = prevEnergy;
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

    private static final class ExcaliburEmissiveLayer extends GeoRenderLayer<ExcaliburItem> {
        ExcaliburEmissiveLayer(ExcaliburGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, ExcaliburItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!(getRenderer() instanceof ExcaliburGeoRenderer er)) {
                return;
            }
            float intensity = Mth.clamp(0.76F + er.getEnergyRatio() * 0.24F + er.getChargeBoost() * 0.14F, 0.0F, 1.0F);
            int ch = Mth.clamp(Math.round(255.0F * intensity), 0, 255);
            int packedCol = FastColor.ARGB32.color(255, ch, ch, ch);

            RenderType glowType = RenderType.eyes(ExcaliburGeoModel.GLOW_GOLDEN);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedCol);
        }
    }
}
