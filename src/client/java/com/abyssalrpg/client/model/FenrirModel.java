package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.FenrirBossEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import java.util.EnumSet;

public class FenrirModel extends HierarchicalModel<FenrirBossEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(AbyssalRpgMod.id("fenrir_boss"), "main");

    private final ModelPart root, body, head, jaw, armL, armLLow, armR, armRLow, legL, legLLow, legR, legRLow, tail1, tail2, tail3;

    public FenrirModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.armL = this.body.getChild("arm_l"); this.armLLow = this.armL.getChild("arm_l_low");
        this.armR = this.body.getChild("arm_r"); this.armRLow = this.armR.getChild("arm_r_low");
        this.legL = root.getChild("leg_l"); this.legLLow = this.legL.getChild("leg_l_low");
        this.legR = root.getChild("leg_r"); this.legRLow = this.legR.getChild("leg_r_low");
        this.tail1 = this.body.getChild("tail1"); this.tail2 = this.tail1.getChild("tail2"); this.tail3 = this.tail2.getChild("tail3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body_2_1 = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(2, 2).addBox(-8.5f, -8f, -6.5f, 17f, 13f, 12f)
                        .texOffs(62, 2).addBox(-9.5f, -9.5f, -7.2f, 19f, 9f, 13f)
                        .texOffs(128, 2).addBox(-6f, 4.5f, -4.5f, 12f, 7.5f, 9f),
                PartPose.offsetAndRotation(0f, 4f, 0f, 0.28f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("mane0",
                CubeListBuilder.create()
                        .texOffs(172, 2).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 7.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(-7.6f, -9.2f, -3f, 1.15f, 0f, -0.44f));
        p_body_2_1.addOrReplaceChild("mane1",
                CubeListBuilder.create()
                        .texOffs(189, 2).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 7.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(-3.8f, -9.2f, -3f, 1.15f, 0f, -0.22f));
        p_body_2_1.addOrReplaceChild("mane2",
                CubeListBuilder.create()
                        .texOffs(206, 2).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 7.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -9.2f, -3f, 1.15f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("mane3",
                CubeListBuilder.create()
                        .texOffs(223, 2).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 7.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(3.8f, -9.2f, -3f, 1.15f, 0f, 0.22f));
        p_body_2_1.addOrReplaceChild("mane4",
                CubeListBuilder.create()
                        .texOffs(240, 2).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 7.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(7.6f, -9.2f, -3f, 1.15f, 0f, 0.44f));
        PartDefinition p_head_2_6 = p_body_2_1.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(257, 2).addBox(-4.6f, -4.5f, -7.5f, 9.2f, 8.2f, 8.2f)
                        .texOffs(297, 2).addBox(-2.8f, -1.6f, -15.2f, 5.6f, 3.8f, 8.2f)
                        .texOffs(329, 2).addBox(-2.4f, 2f, -14.2f, 0.95f, 2.4f, 0.95f)
                        .texOffs(335, 2).addBox(1.45f, 2f, -14.2f, 0.95f, 2.4f, 0.95f),
                PartPose.offsetAndRotation(0f, -7.8f, -6.8f, -0.22f, 0f, 0f));
        p_head_2_6.addOrReplaceChild("jaw",
                CubeListBuilder.create()
                        .texOffs(341, 2).addBox(-2.6f, 0f, -9.6f, 5.2f, 2.2f, 10f),
                PartPose.offsetAndRotation(0f, 2.4f, -5f, 0.48f, 0f, 0f));
        p_head_2_6.addOrReplaceChild("ear_l",
                CubeListBuilder.create()
                        .texOffs(375, 2).addBox(-1.2f, -6.8f, -1.2f, 2.4f, 7f, 2.4f),
                PartPose.offsetAndRotation(3.6f, -4.4f, -2.5f, -0.1f, 0f, 0.24f));
        p_head_2_6.addOrReplaceChild("ear_r",
                CubeListBuilder.create()
                        .texOffs(389, 2).addBox(-1.2f, -6.8f, -1.2f, 2.4f, 7f, 2.4f),
                PartPose.offsetAndRotation(-3.6f, -4.4f, -2.5f, -0.1f, 0f, -0.24f));
        p_head_2_6.addOrReplaceChild("ruff_l",
                CubeListBuilder.create()
                        .texOffs(403, 2).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 7.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(4.4f, -0.5f, -4.5f, 1.35f, 0f, 1.15f));
        p_head_2_6.addOrReplaceChild("ruff_r",
                CubeListBuilder.create()
                        .texOffs(420, 2).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 7.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(-4.4f, -0.5f, -4.5f, 1.35f, 0f, -1.15f));
        PartDefinition p_arm_r_2_7 = p_body_2_1.addOrReplaceChild("arm_r",
                CubeListBuilder.create()
                        .texOffs(437, 2).addBox(-3.6f, -2.5f, -3.6f, 7.2f, 13.8f, 7.2f),
                PartPose.offsetAndRotation(-11.4f, -5.5f, -2.5f, -0.35f, 0f, 0.42f));
        PartDefinition p_arm_r_low_2_1 = p_arm_r_2_7.addOrReplaceChild("arm_r_low",
                CubeListBuilder.create()
                        .texOffs(471, 2).addBox(-3.2f, 0f, -3.2f, 6.4f, 13.2f, 6.4f)
                        .texOffs(2, 29).addBox(-3.5f, 12f, -4.2f, 7f, 3.2f, 7.4f)
                        .texOffs(34, 29).addBox(-2.8f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f)
                        .texOffs(48, 29).addBox(-0.95f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f)
                        .texOffs(62, 29).addBox(0.9f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f)
                        .texOffs(76, 29).addBox(2.75f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f),
                PartPose.offsetAndRotation(0f, 10.8f, 0f, -0.25f, 0f, -0.34f));
        p_arm_r_low_2_1.addOrReplaceChild("ft_r",
                CubeListBuilder.create()
                        .texOffs(90, 29).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 6.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(-3f, 3f, 2f, 1.57f, 0f, -1.4f));
        PartDefinition p_arm_l_2_8 = p_body_2_1.addOrReplaceChild("arm_l",
                CubeListBuilder.create()
                        .texOffs(106, 29).addBox(-3.6f, -2.5f, -3.6f, 7.2f, 13.8f, 7.2f),
                PartPose.offsetAndRotation(11.4f, -5.5f, -2.5f, -0.35f, 0f, -0.42f));
        PartDefinition p_arm_l_low_2_1 = p_arm_l_2_8.addOrReplaceChild("arm_l_low",
                CubeListBuilder.create()
                        .texOffs(140, 29).addBox(-3.2f, 0f, -3.2f, 6.4f, 13.2f, 6.4f)
                        .texOffs(170, 29).addBox(-3.5f, 12f, -4.2f, 7f, 3.2f, 7.4f)
                        .texOffs(202, 29).addBox(-2.8f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f)
                        .texOffs(216, 29).addBox(-0.95f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f)
                        .texOffs(230, 29).addBox(0.9f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f)
                        .texOffs(244, 29).addBox(2.75f, 13.5f, -7.8f, 0.9f, 2.2f, 4.2f),
                PartPose.offsetAndRotation(0f, 10.8f, 0f, -0.25f, 0f, 0.34f));
        p_arm_l_low_2_1.addOrReplaceChild("ft_l",
                CubeListBuilder.create()
                        .texOffs(258, 29).addBox(-3.5f, 0f, 0f, 7f, 0.0f, 6.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(3f, 3f, 2f, 1.57f, 0f, 1.4f));
        PartDefinition p_tail1_2_9 = p_body_2_1.addOrReplaceChild("tail1",
                CubeListBuilder.create()
                        .texOffs(274, 29).addBox(-2.6f, -2.6f, 0f, 5.2f, 5.2f, 8f),
                PartPose.offsetAndRotation(0f, 9f, 4.5f, -0.45f, 0f, 0f));
        PartDefinition p_tail2_2_1 = p_tail1_2_9.addOrReplaceChild("tail2",
                CubeListBuilder.create()
                        .texOffs(304, 29).addBox(-2.2f, -2.2f, 0f, 4.4f, 4.4f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 7.5f, 0.25f, 0f, 0f));
        p_tail2_2_1.addOrReplaceChild("tail3",
                CubeListBuilder.create()
                        .texOffs(332, 29).addBox(-1.5f, -1.5f, 0f, 3f, 3f, 7f),
                PartPose.offsetAndRotation(0f, 0f, 7.5f, 0.2f, 0f, 0f));
        PartDefinition p_leg_r_2_2 = root.addOrReplaceChild("leg_r",
                CubeListBuilder.create()
                        .texOffs(354, 29).addBox(-3f, -1.5f, -3.5f, 6f, 8.5f, 7f),
                PartPose.offsetAndRotation(-5.8f, 11.5f, 2f, -0.42f, 0f, 0f));
        p_leg_r_2_2.addOrReplaceChild("leg_r_low",
                CubeListBuilder.create()
                        .texOffs(382, 29).addBox(-2.4f, 0f, -2.6f, 4.8f, 7.2f, 5.2f)
                        .texOffs(406, 29).addBox(-3f, 5.8f, -5.2f, 6f, 2.5f, 7.5f)
                        .texOffs(436, 29).addBox(-2.4f, 6.6f, -7.2f, 0.9f, 1.5f, 2.5f)
                        .texOffs(446, 29).addBox(-0.45f, 6.6f, -7.6f, 0.9f, 1.5f, 2.8f)
                        .texOffs(456, 29).addBox(1.5f, 6.6f, -7.2f, 0.9f, 1.5f, 2.5f),
                PartPose.offsetAndRotation(0f, 6.5f, 0.5f, 0.58f, 0f, 0f));
        PartDefinition p_leg_l_2_3 = root.addOrReplaceChild("leg_l",
                CubeListBuilder.create()
                        .texOffs(466, 29).addBox(-3f, -1.5f, -3.5f, 6f, 8.5f, 7f),
                PartPose.offsetAndRotation(5.8f, 11.5f, 2f, -0.42f, 0f, 0f));
        p_leg_l_2_3.addOrReplaceChild("leg_l_low",
                CubeListBuilder.create()
                        .texOffs(2, 53).addBox(-2.4f, 0f, -2.6f, 4.8f, 7.2f, 5.2f)
                        .texOffs(26, 53).addBox(-3f, 5.8f, -5.2f, 6f, 2.5f, 7.5f)
                        .texOffs(56, 53).addBox(-2.4f, 6.6f, -7.2f, 0.9f, 1.5f, 2.5f)
                        .texOffs(66, 53).addBox(-0.45f, 6.6f, -7.6f, 0.9f, 1.5f, 2.8f)
                        .texOffs(76, 53).addBox(1.5f, 6.6f, -7.2f, 0.9f, 1.5f, 2.5f),
                PartPose.offsetAndRotation(0f, 6.5f, 0.5f, 0.58f, 0f, 0f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public ModelPart root() { return this.root; }

    @Override
    public void setupAnim(FenrirBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        int cast = entity.getCastState();
        float move = Math.min(1.0f, limbSwingAmount * 1.7f);
        float wc = limbSwing * 0.7f;
        float br = Mth.sin(ageInTicks * 0.12f);
        float swing = entity.getAttackAnim(ageInTicks % 1.0f);

        this.body.y = 4.0f + br * 0.45f - Math.abs(Mth.sin(wc)) * 1.4f * move;
        this.body.xRot = 0.28f + br * 0.04f - (cast == 2 ? 0.35f : 0f);
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.7f;
        this.head.xRot = -0.25f + headPitch * Mth.DEG_TO_RAD * 0.5f;
        this.jaw.xRot = (cast > 0) ? (0.86f + Mth.sin(ageInTicks * 0.5f) * 0.14f) : (0.36f + Math.abs(br) * 0.14f + Mth.sin(swing * Mth.PI) * 0.55f);

        // Powerful bipedal / knuckle-charging wolf-titan legs & smashing clawed arms!
        float lgL = Mth.cos(wc) * 0.85f * move, lgR = Mth.cos(wc + Mth.PI) * 0.85f * move;
        this.legL.xRot = -0.42f + lgL; this.legLLow.xRot = 0.58f + Math.max(0f, -lgL * 0.7f);
        this.legR.xRot = -0.42f + lgR; this.legRLow.xRot = 0.58f + Math.max(0f, -lgR * 0.7f);

        if (cast > 0) {
            float smash = Mth.sin(ageInTicks * 0.35f) * 0.35f;
            this.armL.xRot = -1.65f + smash; this.armR.xRot = -1.65f - smash;
            this.armLLow.xRot = -0.45f; this.armRLow.xRot = -0.45f;
        } else {
            float atk = Mth.sin(swing * Mth.PI) * 1.45f;
            this.armL.xRot = -0.35f + lgR * 0.9f - atk + br * 0.06f;
            this.armR.xRot = -0.35f + lgL * 0.9f - atk * 0.7f - br * 0.06f;
            this.armLLow.xRot = -0.28f - Math.max(0f, -lgR * 0.5f);
            this.armRLow.xRot = -0.28f - Math.max(0f, -lgL * 0.5f);
        }
        this.tail1.yRot = Mth.sin(ageInTicks * 0.16f) * 0.28f;
        this.tail2.yRot = Mth.sin(ageInTicks * 0.16f - 0.8f) * 0.34f;
        this.tail3.yRot = Mth.sin(ageInTicks * 0.16f - 1.6f) * 0.40f;
    }
}
