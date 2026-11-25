package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Hệ thống cánh tay 2 khúc có khớp khuỷu gập (Upper Arm 6px + Bent Forearm 6px) cho tư thế thủ Song Đao Togetsu!
 * - Hỗ trợ đầy đủ cả skin Classic (4px) lẫn Slim Alex (3px), lớp áo tay ngoài (`rightSleeve`/`leftSleeve`)
 *   và cả tay áo giáp ngực vanilla (`HumanoidArmorLayer`) với UV chính xác 100% không tràn pixel!
 * - Có khớp đệm trong khuỷu (`elbow_plug`) nên khi gập khuỷu sâu 90°–120° hoàn toàn liền khối, không hở khe!
 */
public final class TogetsuBentArmRig {
    private static final Set<Direction> NO_DOWN = EnumSet.of(
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP);
    private static final Set<Direction> ONLY_DOWN = EnumSet.of(Direction.DOWN);

    private static ArmorBentArms cachedArmorArms;

    private TogetsuBentArmRig() {
    }

    public static final class SkinBentArms {
        public final ModelPart rightUpperArm;
        public final ModelPart rightForearm;
        public final ModelPart rightUpperSleeve;
        public final ModelPart rightForeSleeve;
        public final ModelPart leftUpperArm;
        public final ModelPart leftForearm;
        public final ModelPart leftUpperSleeve;
        public final ModelPart leftForeSleeve;
        private final List<ModelPart> rootParts;

        SkinBentArms(ModelPart root) {
            this.rightUpperArm = root.getChild("right_upper_arm");
            this.rightForearm = this.rightUpperArm.getChild("right_forearm");
            this.rightUpperSleeve = root.getChild("right_upper_sleeve");
            this.rightForeSleeve = this.rightUpperSleeve.getChild("right_fore_sleeve");
            this.leftUpperArm = root.getChild("left_upper_arm");
            this.leftForearm = this.leftUpperArm.getChild("left_forearm");
            this.leftUpperSleeve = root.getChild("left_upper_sleeve");
            this.leftForeSleeve = this.leftUpperSleeve.getChild("left_fore_sleeve");
            this.rootParts = ImmutableList.of(
                    this.rightUpperArm, this.rightUpperSleeve,
                    this.leftUpperArm, this.leftUpperSleeve
            );
            setVisible(false);
        }

        public List<ModelPart> allParts() {
            return this.rootParts;
        }

        public void setVisible(boolean visible) {
            this.rightUpperArm.visible = visible;
            this.rightUpperSleeve.visible = visible;
            this.leftUpperArm.visible = visible;
            this.leftUpperSleeve.visible = visible;
        }

        public void syncFrom(ModelPart vanillaRightArm, ModelPart vanillaLeftArm,
                             boolean showRightArm, boolean showLeftArm,
                             boolean showRightSleeve, boolean showLeftSleeve,
                             float rElbX, float rElbY, float rElbZ,
                             float lElbX, float lElbY, float lElbZ) {
            this.rightUpperArm.copyFrom(vanillaRightArm);
            this.rightUpperSleeve.copyFrom(vanillaRightArm);
            this.leftUpperArm.copyFrom(vanillaLeftArm);
            this.leftUpperSleeve.copyFrom(vanillaLeftArm);

            this.rightUpperArm.visible = showRightArm;
            this.rightUpperSleeve.visible = showRightArm && showRightSleeve;
            this.leftUpperArm.visible = showLeftArm;
            this.leftUpperSleeve.visible = showLeftArm && showLeftSleeve;

            this.rightForearm.xRot = rElbX;
            this.rightForearm.yRot = rElbY;
            this.rightForearm.zRot = rElbZ;
            this.rightForeSleeve.xRot = rElbX;
            this.rightForeSleeve.yRot = rElbY;
            this.rightForeSleeve.zRot = rElbZ;

            this.leftForearm.xRot = lElbX;
            this.leftForearm.yRot = lElbY;
            this.leftForearm.zRot = lElbZ;
            this.leftForeSleeve.xRot = lElbX;
            this.leftForeSleeve.yRot = lElbY;
            this.leftForeSleeve.zRot = lElbZ;
        }
    }

    private static final class ArmorBentArms {
        final ModelPart rightUpper;
        final ModelPart rightForearm;
        final ModelPart leftUpper;
        final ModelPart leftForearm;

        ArmorBentArms(ModelPart root) {
            this.rightUpper = root.getChild("armor_right_upper");
            this.rightForearm = this.rightUpper.getChild("armor_right_forearm");
            this.leftUpper = root.getChild("armor_left_upper");
            this.leftForearm = this.leftUpper.getChild("armor_left_forearm");
        }
    }

    public static SkinBentArms createSkinBentArms(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        float w = slim ? 3.0F : 4.0F;
        float rX = slim ? -2.0F : -3.0F;
        float lX = -1.0F;
        float shY = slim ? 2.5F : 2.0F;

        // 1. Right Arm (u=40, v=16) & Right Sleeve (u=40, v=32)
        addSegmentedSkinArm(root, "right_upper_arm", "right_forearm",
                40, 16, rX, w, -5.0F, shY, CubeDeformation.NONE, true);
        addSegmentedSkinArm(root, "right_upper_sleeve", "right_fore_sleeve",
                40, 32, rX, w, -5.0F, shY, new CubeDeformation(0.25F), false);

        // 2. Left Arm (u=32, v=48) & Left Sleeve (u=48, v=48)
        addSegmentedSkinArm(root, "left_upper_arm", "left_forearm",
                32, 48, lX, w, 5.0F, shY, CubeDeformation.NONE, true);
        addSegmentedSkinArm(root, "left_upper_sleeve", "left_fore_sleeve",
                48, 48, lX, w, 5.0F, shY, new CubeDeformation(0.25F), false);

        return new SkinBentArms(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    private static void addSegmentedSkinArm(PartDefinition root, String upperName, String foreName,
                                            int u, int v, float boxX, float w, float pivotX, float pivotY,
                                            CubeDeformation deform, boolean exactSkinCaps) {
        CubeListBuilder upperCubes = CubeListBuilder.create();
        if (exactSkinCaps) {
            upperCubes.texOffs(u, v).addBox(boxX, -2.0F, -2.0F, w, 6.0F, 4.0F, NO_DOWN);
            upperCubes.texOffs(u, v + 6).addBox(boxX, -2.0F, -2.0F, w, 6.0F, 4.0F, ONLY_DOWN);
        } else {
            upperCubes.texOffs(u, v).addBox(boxX, -2.0F, -2.0F, w, 6.0F, 4.0F, deform);
        }
        PartDefinition upper = root.addOrReplaceChild(upperName, upperCubes, PartPose.offset(pivotX, pivotY, 0.0F));

        CubeListBuilder foreCubes = CubeListBuilder.create();
        if (exactSkinCaps) {
            // 4 mặt bên + nắp khuỷu dùng đúng vùng cẳng tay dưới (v+6 -> v+10..v+16), lòng bàn tay dưới dùng đúng nắp gốc (v)!
            foreCubes.texOffs(u, v + 6).addBox(boxX, 0.0F, -2.0F, w, 6.0F, 4.0F, NO_DOWN);
            foreCubes.texOffs(u, v).addBox(boxX, 0.0F, -2.0F, w, 6.0F, 4.0F, ONLY_DOWN);
            // Khớp đệm khuỷu chìm 0.03px giữ khuỷu tay hoàn toàn kín khối ở mọi góc gập!
            foreCubes.texOffs(u, v + 4).addBox(boxX, -0.90F, -2.0F, w, 2.0F, 4.0F, new CubeDeformation(-0.03F));
        } else {
            foreCubes.texOffs(u, v + 6).addBox(boxX, 0.0F, -2.0F, w, 6.0F, 4.0F, deform);
        }
        upper.addOrReplaceChild(foreName, foreCubes, PartPose.offset(0.0F, 4.0F, 0.0F));
    }

    private static ArmorBentArms getArmorBentArms() {
        if (cachedArmorArms != null) {
            return cachedArmorArms;
        }
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation armorDeform = new CubeDeformation(1.0F);
        CubeDeformation plugDeform = new CubeDeformation(0.95F);

        PartDefinition rUpper = root.addOrReplaceChild("armor_right_upper",
                CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, armorDeform),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rUpper.addOrReplaceChild("armor_right_forearm",
                CubeListBuilder.create()
                        .texOffs(40, 22).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, armorDeform)
                        .texOffs(40, 20).addBox(-3.0F, -0.85F, -2.0F, 4.0F, 2.0F, 4.0F, plugDeform),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition lUpper = root.addOrReplaceChild("armor_left_upper",
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, armorDeform),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        lUpper.addOrReplaceChild("armor_left_forearm",
                CubeListBuilder.create()
                        .texOffs(40, 22).mirror().addBox(-1.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, armorDeform)
                        .texOffs(40, 20).mirror().addBox(-1.0F, -0.85F, -2.0F, 4.0F, 2.0F, 4.0F, plugDeform),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        cachedArmorArms = new ArmorBentArms(LayerDefinition.create(mesh, 64, 32).bakeRoot());
        return cachedArmorArms;
    }

    public static void renderBentArmorSleeves(PoseStack poseStack, VertexConsumer vc, int packedLight,
                                              int packedOverlay, int color,
                                              ModelPart armorRightArm, ModelPart armorLeftArm,
                                              FullBodyPose pose) {
        ArmorBentArms arms = getArmorBentArms();
        float ew = pose.elbowWeight;

        arms.rightUpper.copyFrom(armorRightArm);
        arms.rightUpper.visible = true;
        arms.rightForearm.xRot = pose.rightElbowX * ew;
        arms.rightForearm.yRot = pose.rightElbowY * ew;
        arms.rightForearm.zRot = pose.rightElbowZ * ew;
        arms.rightUpper.render(poseStack, vc, packedLight, packedOverlay, color);

        arms.leftUpper.copyFrom(armorLeftArm);
        arms.leftUpper.visible = true;
        arms.leftForearm.xRot = pose.leftElbowX * ew;
        arms.leftForearm.yRot = pose.leftElbowY * ew;
        arms.leftForearm.zRot = pose.leftElbowZ * ew;
        arms.leftUpper.render(poseStack, vc, packedLight, packedOverlay, color);
    }
}
