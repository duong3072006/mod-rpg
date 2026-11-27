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
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Hệ thống khớp nối 2 khúc toàn thân (Khuỷu tay: Upper Arm 6px + Bent Forearm 6px; Đầu gối: Thigh 6px + Bent Shin 6px)
 * dùng chung cho **Togetsu** và **Katana**!
 * - Hỗ trợ đầy đủ cả skin Classic (4px) lẫn Slim Alex (3px), lớp áo tay/quần ngoài (`rightSleeve`/`leftSleeve`/`rightPants`/`leftPants`)
 *   và giáp vanilla (`HumanoidArmorLayer`) với UV chính xác 100% không tràn pixel!
 * - Có khớp đệm trong khuỷu/gối (`plug`) nên khi gập sâu hoàn toàn liền khối, không hở khe!
 */
public final class TogetsuBentArmRig {
    private static final Set<Direction> NO_DOWN = EnumSet.of(
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP);
    private static final Set<Direction> ONLY_DOWN = EnumSet.of(Direction.DOWN);

    private static ArmorBentArms cachedArmorArms;
    private static ArmorBentLegs cachedArmorLeggings;
    private static ArmorBentLegs cachedArmorBoots;
    private static boolean renderingFirstPersonHand;

    private TogetsuBentArmRig() {
    }

    public static boolean isRenderingFirstPersonHand() {
        return renderingFirstPersonHand;
    }

    public static void setRenderingFirstPersonHand(boolean active) {
        renderingFirstPersonHand = active;
    }

    /** Lấy dáng khớp nối đang hoạt động theo đúng vũ khí người chơi đang cầm (Togetsu / SeaWeapons / Excalibur / Katana). */
    @Nullable
    public static FullBodyPose getActiveArticulatedPose(Player player) {
        int playerId = player.getId();
        if (TogetsuAnimManager.isHoldingTogetsuReady(player)) {
            FullBodyPose pose = TogetsuAnimManager.getLastComputedPose(playerId);
            if (pose != null && (pose.elbowWeight > 0.003F || pose.kneeWeight > 0.003F)) {
                return pose;
            }
        }
        if (SeaWeaponsAnimManager.isHoldingSeaWeapons(player)) {
            FullBodyPose seaPose = SeaWeaponsAnimManager.getLastComputedPose(playerId);
            if (seaPose != null && (seaPose.elbowWeight > 0.003F || seaPose.kneeWeight > 0.003F)) {
                return seaPose;
            }
        }
        if (ExcaliburAnimManager.isHoldingExcalibur(player)) {
            FullBodyPose excaliburPose = ExcaliburAnimManager.getLastComputedPose(playerId);
            if (excaliburPose != null && (excaliburPose.elbowWeight > 0.003F || excaliburPose.kneeWeight > 0.003F)) {
                return excaliburPose;
            }
        }
        if (KatanaAnimManager.isHoldingKatana(player)) {
            FullBodyPose katanaPose = KatanaAnimManager.getLastComputedPose(playerId);
            if (katanaPose != null && (katanaPose.elbowWeight > 0.003F || katanaPose.kneeWeight > 0.003F)) {
                return katanaPose;
            }
        }
        return getActiveArticulatedPose(playerId);
    }

    /** Lấy dáng khớp nối đang hoạt động (ưu tiên Togetsu -> SeaWeapons -> Excalibur -> Katana) để mọi Mixin & giáp đồng bộ 1 nguồn duy nhất. */
    @Nullable
    public static FullBodyPose getActiveArticulatedPose(int playerId) {
        FullBodyPose pose = TogetsuAnimManager.getLastComputedPose(playerId);
        if (pose != null && (pose.elbowWeight > 0.003F || pose.kneeWeight > 0.003F)) {
            return pose;
        }
        FullBodyPose seaPose = SeaWeaponsAnimManager.getLastComputedPose(playerId);
        if (seaPose != null && (seaPose.elbowWeight > 0.003F || seaPose.kneeWeight > 0.003F)) {
            return seaPose;
        }
        FullBodyPose excaliburPose = ExcaliburAnimManager.getLastComputedPose(playerId);
        if (excaliburPose != null && (excaliburPose.elbowWeight > 0.003F || excaliburPose.kneeWeight > 0.003F)) {
            return excaliburPose;
        }
        FullBodyPose katanaPose = KatanaAnimManager.getLastComputedPose(playerId);
        if (katanaPose != null && (katanaPose.elbowWeight > 0.003F || katanaPose.kneeWeight > 0.003F)) {
            return katanaPose;
        }
        if (pose != null) return pose;
        if (seaPose != null) return seaPose;
        return excaliburPose != null ? excaliburPose : katanaPose;
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

    // =========================================================================
    // Articulated 2-Segment Bent-Knee Leg Rig (Thigh 6px + Knee Plug + Shin 6px)
    // =========================================================================

    public static final class SkinBentLegs {
        public final ModelPart rightThigh;
        public final ModelPart rightShin;
        public final ModelPart rightUpperPants;
        public final ModelPart rightLowerPants;
        public final ModelPart leftThigh;
        public final ModelPart leftShin;
        public final ModelPart leftUpperPants;
        public final ModelPart leftLowerPants;
        private final List<ModelPart> rootParts;

        SkinBentLegs(ModelPart root) {
            this.rightThigh = root.getChild("right_thigh");
            this.rightShin = this.rightThigh.getChild("right_shin");
            this.rightUpperPants = root.getChild("right_upper_pants");
            this.rightLowerPants = this.rightUpperPants.getChild("right_lower_pants");
            this.leftThigh = root.getChild("left_thigh");
            this.leftShin = this.leftThigh.getChild("left_shin");
            this.leftUpperPants = root.getChild("left_upper_pants");
            this.leftLowerPants = this.leftUpperPants.getChild("left_lower_pants");
            this.rootParts = ImmutableList.of(
                    this.rightThigh, this.rightUpperPants,
                    this.leftThigh, this.leftUpperPants
            );
            setVisible(false);
        }

        public List<ModelPart> allParts() {
            return this.rootParts;
        }

        public void setVisible(boolean visible) {
            this.rightThigh.visible = visible;
            this.rightUpperPants.visible = visible;
            this.leftThigh.visible = visible;
            this.leftUpperPants.visible = visible;
        }

        public void syncFrom(ModelPart vanillaRightLeg, ModelPart vanillaLeftLeg,
                             boolean showRightLeg, boolean showLeftLeg,
                             boolean showRightPants, boolean showLeftPants,
                             float rKneeX, float lKneeX) {
            this.rightThigh.copyFrom(vanillaRightLeg);
            this.rightUpperPants.copyFrom(vanillaRightLeg);
            this.leftThigh.copyFrom(vanillaLeftLeg);
            this.leftUpperPants.copyFrom(vanillaLeftLeg);

            this.rightThigh.visible = showRightLeg;
            this.rightUpperPants.visible = showRightLeg && showRightPants;
            this.leftThigh.visible = showLeftLeg;
            this.leftUpperPants.visible = showLeftLeg && showLeftPants;

            this.rightShin.xRot = rKneeX;
            this.rightShin.yRot = 0.0F;
            this.rightShin.zRot = 0.0F;
            this.rightLowerPants.xRot = rKneeX;
            this.rightLowerPants.yRot = 0.0F;
            this.rightLowerPants.zRot = 0.0F;

            this.leftShin.xRot = lKneeX;
            this.leftShin.yRot = 0.0F;
            this.leftShin.zRot = 0.0F;
            this.leftLowerPants.xRot = lKneeX;
            this.leftLowerPants.yRot = 0.0F;
            this.leftLowerPants.zRot = 0.0F;
        }
    }

    private static final class ArmorBentLegs {
        final ModelPart rightThigh;
        final ModelPart rightShin;
        final ModelPart leftThigh;
        final ModelPart leftShin;

        ArmorBentLegs(ModelPart root) {
            this.rightThigh = root.getChild("armor_right_thigh");
            this.rightShin = this.rightThigh.getChild("armor_right_shin");
            this.leftThigh = root.getChild("armor_left_thigh");
            this.leftShin = this.leftThigh.getChild("armor_left_shin");
        }
    }

    public static SkinBentLegs createSkinBentLegs() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Right Leg (u=0, v=16) & Right Pants (u=0, v=32)
        addSegmentedSkinLeg(root, "right_thigh", "right_shin",
                0, 16, -1.9F, CubeDeformation.NONE, true);
        addSegmentedSkinLeg(root, "right_upper_pants", "right_lower_pants",
                0, 32, -1.9F, new CubeDeformation(0.25F), false);

        // Left Leg (u=16, v=48) & Left Pants (u=0, v=48)
        addSegmentedSkinLeg(root, "left_thigh", "left_shin",
                16, 48, 1.9F, CubeDeformation.NONE, true);
        addSegmentedSkinLeg(root, "left_upper_pants", "left_lower_pants",
                0, 48, 1.9F, new CubeDeformation(0.25F), false);

        return new SkinBentLegs(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    private static void addSegmentedSkinLeg(PartDefinition root, String thighName, String shinName,
                                            int u, int v, float pivotX,
                                            CubeDeformation deform, boolean exactSkinCaps) {
        CubeListBuilder thighCubes = CubeListBuilder.create();
        if (exactSkinCaps) {
            thighCubes.texOffs(u, v).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, NO_DOWN);
            thighCubes.texOffs(u, v + 6).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, ONLY_DOWN);
        } else {
            thighCubes.texOffs(u, v).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, deform);
        }
        PartDefinition thigh = root.addOrReplaceChild(thighName, thighCubes, PartPose.offset(pivotX, 12.0F, 0.0F));

        CubeListBuilder shinCubes = CubeListBuilder.create();
        if (exactSkinCaps) {
            shinCubes.texOffs(u, v + 6).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, NO_DOWN);
            shinCubes.texOffs(u, v).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, ONLY_DOWN);
            // Khớp đệm gối chìm 0.03px giữ đầu gối hoàn toàn kín khối ở mọi góc gập!
            shinCubes.texOffs(u, v + 4).addBox(-2.0F, -0.90F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(-0.03F));
        } else {
            shinCubes.texOffs(u, v + 6).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, deform);
        }
        thigh.addOrReplaceChild(shinName, shinCubes, PartPose.offset(0.0F, 6.0F, 0.0F));
    }

    private static ArmorBentLegs getArmorBentLegs(boolean boots) {
        if (boots && cachedArmorBoots != null) {
            return cachedArmorBoots;
        }
        if (!boots && cachedArmorLeggings != null) {
            return cachedArmorLeggings;
        }
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation legDeform = new CubeDeformation(boots ? 1.0F : 0.5F);
        CubeDeformation plugDeform = new CubeDeformation(boots ? 0.94F : 0.45F);

        PartDefinition rThigh = root.addOrReplaceChild("armor_right_thigh",
                boots ? CubeListBuilder.create()
                        : CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, legDeform),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        CubeListBuilder rShinCubes = CubeListBuilder.create()
                .texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, legDeform);
        if (!boots) {
            rShinCubes.texOffs(0, 20).addBox(-2.0F, -0.85F, -2.0F, 4.0F, 2.0F, 4.0F, plugDeform);
        }
        rThigh.addOrReplaceChild("armor_right_shin", rShinCubes, PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition lThigh = root.addOrReplaceChild("armor_left_thigh",
                boots ? CubeListBuilder.create()
                        : CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, legDeform),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        CubeListBuilder lShinCubes = CubeListBuilder.create()
                .texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, legDeform);
        if (!boots) {
            lShinCubes.texOffs(0, 20).mirror().addBox(-2.0F, -0.85F, -2.0F, 4.0F, 2.0F, 4.0F, plugDeform);
        }
        lThigh.addOrReplaceChild("armor_left_shin", lShinCubes, PartPose.offset(0.0F, 6.0F, 0.0F));

        ArmorBentLegs built = new ArmorBentLegs(LayerDefinition.create(mesh, 64, 32).bakeRoot());
        if (boots) {
            cachedArmorBoots = built;
        } else {
            cachedArmorLeggings = built;
        }
        return built;
    }

    public static void renderBentArmorLegs(PoseStack poseStack, VertexConsumer vc, int packedLight,
                                           int packedOverlay, int color,
                                           ModelPart armorRightLeg, ModelPart armorLeftLeg,
                                           FullBodyPose pose, boolean boots) {
        ArmorBentLegs legs = getArmorBentLegs(boots);
        float kw = pose.kneeWeight;

        legs.rightThigh.copyFrom(armorRightLeg);
        legs.rightThigh.visible = true;
        legs.rightShin.xRot = pose.rightKneeX * kw;
        legs.rightShin.yRot = 0.0F;
        legs.rightShin.zRot = 0.0F;
        legs.rightThigh.render(poseStack, vc, packedLight, packedOverlay, color);

        legs.leftThigh.copyFrom(armorLeftLeg);
        legs.leftThigh.visible = true;
        legs.leftShin.xRot = pose.leftKneeX * kw;
        legs.leftShin.yRot = 0.0F;
        legs.leftShin.zRot = 0.0F;
        legs.leftThigh.render(poseStack, vc, packedLight, packedOverlay, color);
    }
}
