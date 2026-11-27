package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.combat.ExcaliburMoveType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Bộ render Góc Nhìn Thứ Nhất đồng bộ **100% khung hình theo Cây Xương Góc Nhìn Thứ Ba (F5)** cho **Excalibur + Avalon**:
 * - Hủy render Item Display JSON mặc định ở góc nhìn thứ nhất; chỉ vẽ trực tiếp thanh `Excalibur` 3D và vỏ `Avalon` 3D
 *   (đã loại bỏ hoàn toàn khối cánh tay trắng che 2 bên màn hình, giữ nguyên tay ở góc nhìn thứ ba F5!).
 * - Áp dụng Pitch Clamp `0.75f` chống xuyên camera khi ngước lên trời hay cúi xuống đất.
 */
public final class ExcaliburFirstPersonRigRenderer {
    private ExcaliburFirstPersonRigRenderer() {}

    public static void render(AbstractClientPlayer player, ItemStack mainStack,
                              float partialTicks, float pitch,
                              PoseStack poseStack, MultiBufferSource buffer, int combinedLight) {
        if (player.isInvisible()) {
            return;
        }
        int id = player.getId();
        float limbSpeed = player.walkAnimation.speed(partialTicks);
        float ageInTicks = player.tickCount + partialTicks;
        FullBodyPose pose = ExcaliburAnimManager.evaluatePose(player, partialTicks, limbSpeed, ageInTicks);
        if (pose == null) {
            return;
        }

        boolean slim = (player.getSkin().model() == PlayerSkin.Model.SLIM);
        boolean swordInAvalon = ExcaliburAnimManager.isSwordInHipAvalon(id, partialTicks);
        boolean unsheathed = ExcaliburAnimManager.isUnsheathed(id);
        float guard = ExcaliburAnimManager.getGuardBlend(id, partialTicks);
        float overhead = ExcaliburAnimManager.getGuardOverheadBlend(id, partialTicks);
        float sprint = ExcaliburAnimManager.getSprintBlend(id, partialTicks);
        float actionAlpha = ExcaliburAnimManager.getActionProgress(id, partialTicks);
        ExcaliburMoveType move = ExcaliburAnimManager.getMoveType(id);

        // 0. Trạng thái chờ đã rút kiếm (Drawn Idle / Drawn Sprint):
        //    Giữ thanh kiếm neo cố định trong khung hình camera (KHÔNG xoay bù pitch gây nhấc chuôi lơ lửng khi cúi xuống!),
        //    tăng kích cỡ thanh Excalibur lên `0.68F` cân đối uy lực với chuôi bám chắc quá mép dưới bên phải!
        if (unsheathed && !swordInAvalon && actionAlpha <= 0.001F && guard <= 0.001F) {
            float breath = Mth.sin(ageInTicks * 0.085F);

            poseStack.pushPose();
            poseStack.translate(
                    +0.62F + sprint * 0.04F,
                    -0.64F + breath * 0.005F - sprint * 0.04F,
                    -1.00F - sprint * 0.03F);
            poseStack.mulPose(Axis.YP.rotationDegrees(-16.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(+15.0F - sprint * 5.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F - sprint * 5.0F));
            // Xoay nhẹ 3/4 (+48°) để bản chắn tay vàng thanh mảnh, không choán góc dưới bên phải màn hình!
            poseStack.mulPose(Axis.YP.rotationDegrees(+48.0F));
            // Phóng kích thước thanh Excalibur lên `0.68F` quanh tâm chuôi cầm (Y = -2.2px) để lưỡi vươn dài uy lực!
            poseStack.translate(0.0F, (-2.2F / 16.0F) * 0.45F, 0.0F);
            poseStack.scale(0.68F, 0.68F, 0.68F);
            poseStack.translate(0.0F, +2.2F / 16.0F, 0.0F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            ExcaliburGeoRenderer.renderThirdPersonDirect(mainStack, poseStack, buffer, combinedLight,
                    OverlayTexture.NO_OVERLAY, id);
            poseStack.popPose();
            return;
        }

        // 1. Đẩy toàn bộ rig cánh tay + kiếm/vỏ ra xa khỏi camera & thu nhỏ tỷ lệ gọn gàng theo đúng trạng thái combat
        //    để không bị phóng to che góc màn hình và luôn thoáng hoàn toàn khu vực tâm ngắm (+)!
        float offX;
        float offY;
        float offZ;
        float rigScale;
        float rollDeg = 0.0F;

        if (actionAlpha > 0.001F) {
            if (move == ExcaliburMoveType.SHEATHE_AVALON) {
                // Cất kiếm 0.55s: thấy rõ tay trái nâng Avalon ở góc dưới-trái & tay phải trượt Excalibur vào vỏ!
                offX = +0.02F;
                offY = +0.04F;
                offZ = -0.44F;
                rigScale = 0.58F;
            } else if (move == ExcaliburMoveType.GUARD_OVERHEAD_IMPACT) {
                offX = +0.04F;
                offY = -0.28F;
                offZ = -0.54F;
                rigScale = 0.54F;
            } else if (move == ExcaliburMoveType.ULT_EXCALIBUR) {
                // Tuyệt kỹ: Dựng thẳng chính giữa màn hình (0.0–1.2s) -> bổ thẳng ngang bắn tia từ tâm ngắm (+)!
                offX = 0.00F;
                offY = -0.16F;
                offZ = -0.46F;
                rigScale = 0.58F;
            } else if (move == ExcaliburMoveType.AVALON_PARRY_COUNTER) {
                // Rút kiếm phản công Iaido (Trái -> Phải): vỏ Avalon tụt về hông, Excalibur lóe sáng chém ngang gọn dưới tâm ngắm!
                offX = 0.00F;
                offY = -0.16F;
                offZ = -0.46F;
                rigScale = 0.56F;
            } else {
                // Chuỗi 4 Hit, Strike Air, Golden Crescent, Aerial 1-3, Royal Parry Counter — thu gọn & đẩy lùi xa camera!
                offX = +0.04F;
                offY = -0.18F;
                offZ = -0.48F;
                rigScale = 0.56F;
            }
        } else {
            // Base cho Sheathed Idle/Run (đẩy xa camera gọn gàng)
            float baseX = unsheathed ? +0.08F : 0.00F;
            float baseY = unsheathed ? -0.18F : +0.10F;
            float baseZ = unsheathed ? -0.48F : -0.44F;
            float baseScale = unsheathed ? 0.56F : 0.58F;

            if (guard > 0.001F) {
                if (!unsheathed) {
                    // Avalon Parry Guard (`is_unsheathed == false`): vỏ Avalon nâng chéo 45° gọn dưới tâm ngắm (+)!
                    offX = Mth.lerp(guard, baseX, +0.02F);
                    offY = Mth.lerp(guard, baseY, -0.18F);
                    offZ = Mth.lerp(guard, baseZ, -0.48F);
                    rigScale = Mth.lerp(guard, baseScale, 0.60F);
                    rollDeg = 0.0F;
                } else {
                    // Đỡ Hoàng Gia [RMB]: giữ kiếm ở tầm thấp nghiêng chéo ~42° trước mặt, tay & chắn tay thu nhỏ đẩy xa camera!
                    // Đỡ Trên Cao [Pitch < -30°]: mái che mặt ngang gọn phía trên tâm ngắm (+)!
                    float gX = Mth.lerp(overhead, +0.04F, +0.04F);
                    float gY = Mth.lerp(overhead, -0.20F, -0.28F);
                    float gZ = Mth.lerp(overhead, -0.46F, -0.54F);
                    float gScale = Mth.lerp(overhead, 0.58F, 0.54F);
                    float gRoll = Mth.lerp(overhead, -10.0F, 0.0F);
                    offX = Mth.lerp(guard, baseX, gX);
                    offY = Mth.lerp(guard, baseY, gY);
                    offZ = Mth.lerp(guard, baseZ, gZ);
                    rigScale = Mth.lerp(guard, baseScale, gScale);
                    rollDeg = Mth.lerp(guard, 0.0F, gRoll);
                }
            } else {
                offX = baseX;
                offY = baseY;
                offZ = baseZ;
                rigScale = baseScale;
            }
        }

        boolean showSwingHands = actionAlpha > 0.001F && !swordInAvalon
                && move != ExcaliburMoveType.GUARD_OVERHEAD_IMPACT;
        Matrix4f entryInv = showSwingHands ? new Matrix4f(poseStack.last().pose()).invert() : null;
        Vector3f rGripCam = null;
        Vector3f lGripCam = null;

        poseStack.pushPose();
        // 2. Giữ khung kiếm gắn cố định theo camera (KHÔNG xoay bù pitch làm nhấc kiếm lơ lửng khi di chuột xuống!)
        if (Math.abs(rollDeg) > 0.01F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(rollDeg));
        }

        // 3. Đẩy gốc rig ra xa camera `(offX, offY, offZ)` rồi thu nhỏ đồng đều cả cánh tay & thanh kiếm (`rigScale`),
        //    dịch về trục mắt PlayerModel `(Y - 1.45, Z + 0.35)` theo đúng hệ LivingEntityRenderer (det = +1)!
        poseStack.translate(offX, offY + pose.rootTranslateY * 0.35F, offZ);
        poseStack.scale(rigScale, rigScale, rigScale);
        poseStack.translate(0.0F, -1.45F, +0.35F);
        if (Math.abs(pose.rootPitchDeg) > 0.05F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pose.rootPitchDeg * 0.35F));
        }
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        // 4. Tính vị trí cổ tay phải (KHÔNG vẽ khối cánh tay 3D màu trắng che 2 bên màn hình ở góc nhìn thứ nhất!)
        float armYBase = slim ? 2.5F : 2.0F;
        float ew = pose.elbowWeight;

        float rX = -5.0F + pose.rightArmPosX;
        float rY = armYBase + pose.rightArmPosY;
        float rZ = pose.rightArmPosZ;

        // 5. Vẽ vỏ kiếm Avalon 3D (kèm Excalibur bên trong khi đứng chờ/chạy tra vỏ/GĐ 2-3 cất kiếm!) bám đúng trục xương `body`!
        poseStack.pushPose();
        if (pose.bodyZ != 0.0F) poseStack.mulPose(Axis.ZP.rotation(pose.bodyZ));
        if (pose.bodyY != 0.0F) poseStack.mulPose(Axis.YP.rotation(pose.bodyY));
        if (pose.bodyX != 0.0F) poseStack.mulPose(Axis.XP.rotation(pose.bodyX));
        ExcaliburGeoRenderer.renderHipAvalon(poseStack, buffer, combinedLight, OverlayTexture.NO_OVERLAY,
                swordInAvalon ? mainStack : ItemStack.EMPTY, id, partialTicks);
        poseStack.popPose();

        // 6. Khi kiếm đã rút ra tay phải (Combat Idle, 4 Hit, Combo mở rộng, Đỡ Hoàng Gia, Phản Đòn, Tuyệt Kỹ, GĐ 1 Cất Kiếm):
        //    Vẽ Excalibur gắn 1:1 vào đúng ma trận cổ tay phải của cây xương F5!
        if (!swordInAvalon) {
            poseStack.pushPose();
            // Khớp 100% PlayerModel.translateToHand(RIGHT) + PlayerModelMixin elbow + ItemInHandLayer + ItemInHandLayerMixin!
            poseStack.translate((rX + (slim ? 1.0F : 0.0F)) / 16.0F, rY / 16.0F, rZ / 16.0F);
            if (pose.rightArmZ != 0.0F) poseStack.mulPose(Axis.ZP.rotation(pose.rightArmZ));
            if (pose.rightArmY != 0.0F) poseStack.mulPose(Axis.YP.rotation(pose.rightArmY));
            if (pose.rightArmX != 0.0F) poseStack.mulPose(Axis.XP.rotation(pose.rightArmX));

            // Khớp khuỷu tay 2 khúc (`PlayerModelMixin.abyssalrpg$applyElbowToHandMatrix`)
            if (ew > 0.003F) {
                float ex = pose.rightElbowX * ew;
                float ey = pose.rightElbowY * ew;
                float ez = pose.rightElbowZ * ew;
                poseStack.translate(0.0F, 4.0F / 16.0F, 0.0F);
                if (ez != 0.0F) poseStack.mulPose(Axis.ZP.rotation(ez));
                if (ey != 0.0F) poseStack.mulPose(Axis.YP.rotation(ey));
                if (ex != 0.0F) poseStack.mulPose(Axis.XP.rotation(ex));
                poseStack.translate(0.0F, -4.0F / 16.0F, 0.0F);
            }

            // Gắn item chuẩn vanilla `ItemInHandLayer.renderArmWithItem`
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate(1.0F / 16.0F, 0.125F, -0.625F);

            // Góc lưỡi kiếm theo đúng keyframe F5 (`ItemInHandLayerMixin`)
            if (Math.abs(pose.rightBladePitchDeg) > 0.05F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(pose.rightBladePitchDeg));
            }
            if (Math.abs(pose.rightBladeRollDeg) > 0.05F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(pose.rightBladeRollDeg));
            }
            if (Math.abs(pose.rightBladeYawDeg) > 0.05F) {
                poseStack.mulPose(Axis.YP.rotationDegrees(pose.rightBladeYawDeg));
            }
            if (Math.abs(pose.rightBladeOffX) > 1e-4F || Math.abs(pose.rightBladeOffY) > 1e-4F || Math.abs(pose.rightBladeOffZ) > 1e-4F) {
                poseStack.translate(pose.rightBladeOffX, pose.rightBladeOffY, pose.rightBladeOffZ);
            }

            // Tăng riêng kích thước thanh Excalibur (`1.38F`) quanh đúng tâm lòng bàn tay nắm chuôi (Y = -0.10F)
            // để lưỡi kiếm to cân đối và vươn dài uy lực về phía trước!
            poseStack.translate(0.0F, -0.10F, 0.0F);
            poseStack.scale(1.38F, 1.38F, 1.38F);
            poseStack.translate(0.0F, +0.10F, 0.0F);

            // Khớp 1:1 `excalibur.json` display.thirdperson_righthand: translation [0, 0.8, 0], scale [0.92, 0.86, 0.92]
            poseStack.translate(0.0F, 0.8F / 16.0F, 0.0F);
            poseStack.scale(0.92F, 0.86F, 0.92F);

            // Trích chính xác tọa độ chuôi xanh trên & dưới của thanh Excalibur trong Camera-Space
            // mà KHÔNG thay đổi bất kỳ thông số hình dạng/kích thước nào của vũ khí!
            if (entryInv != null) {
                Matrix4f relSword = new Matrix4f(entryInv).mul(poseStack.last().pose());
                rGripCam = relSword.transformPosition(new Vector3f(0.0F, -1.8F / 16.0F, 0.0F));
                lGripCam = relSword.transformPosition(new Vector3f(0.0F, -4.8F / 16.0F, 0.0F));
            }

            poseStack.translate(-0.5F, -0.5F, -0.5F);

            ExcaliburGeoRenderer.renderThirdPersonDirect(mainStack, poseStack, buffer, combinedLight,
                    OverlayTexture.NO_OVERLAY, id);
            poseStack.popPose();
        }

        poseStack.popPose();

        // 7. Vẽ tay mang đúng skin người chơi kiểu Katana CHỈ LÚC VUNG KIẾM (cân đối, không quá dài hay quá to,
        //    giữ nguyên 100% hình dạng/kích thước thanh Excalibur và không vẽ tay khi đứng chờ/đỡ tĩnh!)
        if (rGripCam != null) {
            boolean twoHandedSwing = move != ExcaliburMoveType.AVALON_PARRY_COUNTER
                    && move != ExcaliburMoveType.SHEATHE_AVALON;
            boolean returnsToIdle = move != ExcaliburMoveType.AIR_1_DIAG
                    && move != ExcaliburMoveType.AIR_2_DIAG
                    && (move != ExcaliburMoveType.AIR_3_FALLING_STAR || ExcaliburAnimManager.isFallingStarLanded(id));
            float retract = (returnsToIdle && actionAlpha > 0.86F) ? ((actionAlpha - 0.86F) / 0.14F) : 0.0F;
            renderKatanaStyleSwingArm(poseStack, buffer, player, false, rGripCam, combinedLight, retract);
            if (twoHandedSwing && lGripCam != null) {
                renderKatanaStyleSwingArm(poseStack, buffer, player, true, lGripCam, combinedLight, retract);
            }
        }
    }

    /**
     * Render cánh tay mang đúng skin & lớp áo tay ngoài (`PlayerRenderer#renderRightHand / renderLeftHand`)
     * theo phong cách Katana lúc vung Excalibur: tỷ lệ cân đối (`armScaleXZ = 0.36F`, `armScaleY <= 0.68F`)
     * để tay không bị to thô hay kéo dài quá mức!
     */
    private static void renderKatanaStyleSwingArm(PoseStack poseStack, MultiBufferSource buffer,
                                                  AbstractClientPlayer player, boolean isLeftHand,
                                                  Vector3f gripTarget, int combinedLight, float retractFrac) {
        if (retractFrac >= 0.99F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer playerRenderer)) {
            return;
        }

        Vector3f gripCam = new Vector3f(gripTarget);
        float side = isLeftHand ? -1.0F : 1.0F;
        Vector3f shoulderCam = new Vector3f(side * 0.42F, -0.60F, -0.18F);

        Vector3f delta = new Vector3f(gripCam).sub(shoulderCam);
        float dist = delta.length();
        if (dist < 1e-4F) {
            return;
        }
        Vector3f yArm = new Vector3f(delta).div(dist);

        // Thu mượt tay xuống dưới mép màn hình ở 14% cuối đòn chém khi quay về dáng chờ
        float retract = Mth.clamp(retractFrac, 0.0F, 1.0F);
        if (retract > 0.001F) {
            gripCam.fma(-retract * 0.28F, yArm);
        }

        Vector3f upHint = (Math.abs(yArm.y) > 0.92F) ? new Vector3f(0.0F, 0.0F, -1.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f xArm = new Vector3f(upHint).cross(yArm).normalize();
        Vector3f zArm = new Vector3f(xArm).cross(yArm).normalize();
        Quaternionf armQ = new Matrix3f()
                .setColumn(0, xArm)
                .setColumn(1, yArm)
                .setColumn(2, zArm)
                .getNormalizedRotation(new Quaternionf());

        boolean slim = (player.getSkin().model() == PlayerSkin.Model.SLIM);
        float fistOffX = (isLeftHand ? -1.0F : 1.0F) * (slim ? (5.5F / 16.0F) : (6.0F / 16.0F));
        float fistOffY = slim ? (-11.0F / 16.0F) : (-10.5F / 16.0F);
        float armScaleXZ = 0.36F;
        float armScaleY = Mth.clamp((dist / (10.5F / 16.0F)) * 0.92F, armScaleXZ, 0.68F);

        poseStack.pushPose();
        poseStack.translate(gripCam.x, gripCam.y, gripCam.z);
        poseStack.mulPose(armQ);
        poseStack.scale(armScaleXZ, armScaleY, armScaleXZ);
        poseStack.translate(fistOffX, fistOffY, 0.0F);
        if (isLeftHand) {
            playerRenderer.renderLeftHand(poseStack, buffer, combinedLight, player);
        } else {
            playerRenderer.renderRightHand(poseStack, buffer, combinedLight, player);
        }
        poseStack.popPose();
    }
}
