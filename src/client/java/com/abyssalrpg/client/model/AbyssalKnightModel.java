package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.AbyssalKnightBossEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class AbyssalKnightModel extends HierarchicalModel<AbyssalKnightBossEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(AbyssalRpgMod.id("abyssal_knight_boss"), "main");

    private final ModelPart root, body, neck1, neck2, head, jaw, earL, earR, tail1, tail2, tail3, tail4;
    private final ModelPart legFL, legFLLow, legFR, legFRLow, legBL, legBLLow, legBR, legBRLow;

    public AbyssalKnightModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck1 = this.body.getChild("neck1");
        this.neck2 = this.neck1.getChild("neck2");
        this.head = this.neck2.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.earL = this.head.getChild("ear_l");
        this.earR = this.head.getChild("ear_r");
        this.tail1 = this.body.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
        this.tail4 = this.tail3.getChild("tail4");
        this.legFL = root.getChild("leg_fl"); this.legFLLow = this.legFL.getChild("leg_fl_low");
        this.legFR = root.getChild("leg_fr"); this.legFRLow = this.legFR.getChild("leg_fr_low");
        this.legBL = root.getChild("leg_bl"); this.legBLLow = this.legBL.getChild("leg_bl_low");
        this.legBR = root.getChild("leg_br"); this.legBRLow = this.legBR.getChild("leg_br_low");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body_2_1 = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(2, 2).addBox(-5.5f, -5.5f, -12f, 11f, 10.5f, 12f)
                        .texOffs(50, 2).addBox(-6.5f, -6.5f, -13.2f, 13f, 11.8f, 8f)
                        .texOffs(94, 2).addBox(-4.2f, -4.2f, 0f, 8.4f, 8.2f, 11.5f)
                        .texOffs(138, 2).addBox(-5.8f, -5.8f, -13.6f, 11.6f, 11f, 2f),
                PartPose.offsetAndRotation(0f, 9.5f, 0f, 0f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("ruff_l",
                CubeListBuilder.create()
                        .texOffs(168, 2).addBox(0f, 0f, 0f, 0.8f, 6.5f, 9f),
                PartPose.offsetAndRotation(6.5f, -5.5f, -12f, 0f, 0f, -0.35f));
        p_body_2_1.addOrReplaceChild("ruff_r",
                CubeListBuilder.create()
                        .texOffs(190, 2).addBox(-0.8f, 0f, 0f, 0.8f, 6.5f, 9f),
                PartPose.offsetAndRotation(-6.5f, -5.5f, -12f, 0f, 0f, 0.35f));
        p_body_2_1.addOrReplaceChild("chain_chest",
                CubeListBuilder.create()
                        .texOffs(212, 2).addBox(-1.2f, 0f, -1f, 2.4f, 5.2f, 2f),
                PartPose.offsetAndRotation(0f, 5.2f, -12.8f, 0.25f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("sp0",
                CubeListBuilder.create()
                        .texOffs(224, 2).addBox(-0.8f, -4.2f, -1.1f, 1.6f, 4.4f, 2.2f),
                PartPose.offsetAndRotation(0f, -6.2f, -10.5f, -0.52f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("sp1",
                CubeListBuilder.create()
                        .texOffs(236, 2).addBox(-0.8f, -4.2f, -1.1f, 1.6f, 4.4f, 2.2f),
                PartPose.offsetAndRotation(0f, -6.2f, -6.9f, -0.52f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("sp2",
                CubeListBuilder.create()
                        .texOffs(248, 2).addBox(-0.8f, -4.2f, -1.1f, 1.6f, 4.4f, 2.2f),
                PartPose.offsetAndRotation(0f, -6.2f, -3.3f, -0.52f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("sp3",
                CubeListBuilder.create()
                        .texOffs(260, 2).addBox(-0.8f, -4.2f, -1.1f, 1.6f, 4.4f, 2.2f),
                PartPose.offsetAndRotation(0f, -6.2f, 0.3f, -0.52f, 0f, 0f));
        PartDefinition p_neck1_2_8 = p_body_2_1.addOrReplaceChild("neck1",
                CubeListBuilder.create()
                        .texOffs(272, 2).addBox(-3.6f, -3.6f, -5.5f, 7.2f, 7.2f, 6.5f),
                PartPose.offsetAndRotation(0f, -2.5f, -11.5f, -0.22f, 0f, 0f));
        PartDefinition p_neck2_2_1 = p_neck1_2_8.addOrReplaceChild("neck2",
                CubeListBuilder.create()
                        .texOffs(304, 2).addBox(-3.2f, -3.2f, -4.5f, 6.4f, 6.4f, 5.5f),
                PartPose.offsetAndRotation(0f, -0.5f, -5f, 0.12f, 0f, 0f));
        PartDefinition p_head_2_1 = p_neck2_2_1.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(332, 2).addBox(-4f, -4f, -6.5f, 8f, 7f, 7.2f)
                        .texOffs(366, 2).addBox(-2.4f, -1.8f, -13f, 4.8f, 3.6f, 7f)
                        .texOffs(392, 2).addBox(-2.1f, 1.5f, -12f, 0.8f, 1.9f, 0.8f)
                        .texOffs(398, 2).addBox(1.3f, 1.5f, -12f, 0.8f, 1.9f, 0.8f),
                PartPose.offsetAndRotation(0f, -0.4f, -4.5f, 0.12f, 0f, 0f));
        p_head_2_1.addOrReplaceChild("cheek_l",
                CubeListBuilder.create()
                        .texOffs(404, 2).addBox(0f, 0f, 0f, 0.7f, 4.2f, 5f),
                PartPose.offsetAndRotation(4f, -1f, -5f, 0f, -0.35f, -0.25f));
        p_head_2_1.addOrReplaceChild("cheek_r",
                CubeListBuilder.create()
                        .texOffs(418, 2).addBox(-0.7f, 0f, 0f, 0.7f, 4.2f, 5f),
                PartPose.offsetAndRotation(-4f, -1f, -5f, 0f, 0.35f, 0.25f));
        p_head_2_1.addOrReplaceChild("jaw",
                CubeListBuilder.create()
                        .texOffs(432, 2).addBox(-2.2f, 0f, -7.5f, 4.4f, 2f, 8f),
                PartPose.offsetAndRotation(0f, 1.7f, -5f, 0.18f, 0f, 0f));
        p_head_2_1.addOrReplaceChild("ear_l",
                CubeListBuilder.create()
                        .texOffs(460, 2).addBox(-1f, -4.8f, -1f, 2f, 4.8f, 2f),
                PartPose.offsetAndRotation(3f, -3.8f, -2f, -0.25f, 0f, 0.22f));
        p_head_2_1.addOrReplaceChild("ear_r",
                CubeListBuilder.create()
                        .texOffs(470, 2).addBox(-1f, -4.8f, -1f, 2f, 4.8f, 2f),
                PartPose.offsetAndRotation(-3f, -3.8f, -2f, -0.25f, 0f, -0.22f));
        PartDefinition p_hl1_2_6 = p_head_2_1.addOrReplaceChild("hl1",
                CubeListBuilder.create()
                        .texOffs(480, 2).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(3.5f, -3.2f, -3f, -0.65f, 0.35f, 0.18f));
        p_hl1_2_6.addOrReplaceChild("hl2",
                CubeListBuilder.create()
                        .texOffs(2, 27).addBox(-0.6f, -0.6f, 0f, 1.2f, 1.2f, 5.8f),
                PartPose.offsetAndRotation(0f, 0f, 6.8f, 0.32f, 0.15f, 0f));
        PartDefinition p_hr1_2_7 = p_head_2_1.addOrReplaceChild("hr1",
                CubeListBuilder.create()
                        .texOffs(20, 27).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(-3.5f, -3.2f, -3f, -0.65f, -0.35f, -0.18f));
        p_hr1_2_7.addOrReplaceChild("hr2",
                CubeListBuilder.create()
                        .texOffs(42, 27).addBox(-0.6f, -0.6f, 0f, 1.2f, 1.2f, 5.8f),
                PartPose.offsetAndRotation(0f, 0f, 6.8f, 0.32f, -0.15f, 0f));
        PartDefinition p_tail1_2_9 = p_body_2_1.addOrReplaceChild("tail1",
                CubeListBuilder.create()
                        .texOffs(60, 27).addBox(-2f, -2f, 0f, 4f, 4f, 6.5f),
                PartPose.offsetAndRotation(0f, -2f, 11f, -0.35f, 0f, 0f));
        PartDefinition p_tail2_2_1 = p_tail1_2_9.addOrReplaceChild("tail2",
                CubeListBuilder.create()
                        .texOffs(84, 27).addBox(-1.8f, -1.8f, 0f, 3.6f, 3.6f, 6.5f),
                PartPose.offsetAndRotation(0f, 0f, 6f, 0.25f, 0f, 0f));
        PartDefinition p_tail3_2_1 = p_tail2_2_1.addOrReplaceChild("tail3",
                CubeListBuilder.create()
                        .texOffs(108, 27).addBox(-1.3f, -1.3f, 0f, 2.6f, 2.6f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 6f, 0.22f, 0f, 0f));
        p_tail3_2_1.addOrReplaceChild("tail4",
                CubeListBuilder.create()
                        .texOffs(128, 27).addBox(-0.9f, -0.9f, 0f, 1.8f, 1.8f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 5.5f, 0.15f, 0f, 0f));
        PartDefinition p_leg_fl_2_2 = root.addOrReplaceChild("leg_fl",
                CubeListBuilder.create()
                        .texOffs(144, 27).addBox(-2.2f, -1f, -2.5f, 4.4f, 8.2f, 5f),
                PartPose.offsetAndRotation(5.4f, 8.5f, -8.5f, -0.18f, 0f, 0f));
        PartDefinition p_leg_fl_low_2_1 = p_leg_fl_2_2.addOrReplaceChild("leg_fl_low",
                CubeListBuilder.create()
                        .texOffs(166, 27).addBox(-1.7f, 0f, -1.8f, 3.4f, 7.5f, 3.6f)
                        .texOffs(184, 27).addBox(-2.1f, 3.5f, -2.2f, 4.2f, 2.2f, 4.4f),
                PartPose.offsetAndRotation(0f, 7f, 0.5f, 0.24f, 0f, 0f));
        p_leg_fl_low_2_1.addOrReplaceChild("leg_fl_paw",
                CubeListBuilder.create()
                        .texOffs(206, 27).addBox(-2f, -0.5f, -3.5f, 4f, 2f, 5f)
                        .texOffs(226, 27).addBox(-1.6f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f)
                        .texOffs(234, 27).addBox(-0.35f, 0.2f, -5.2f, 0.7f, 1.1f, 2f)
                        .texOffs(242, 27).addBox(0.9f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f),
                PartPose.offsetAndRotation(0f, 7f, 0f, -0.06f, 0f, 0f));
        PartDefinition p_leg_fr_2_3 = root.addOrReplaceChild("leg_fr",
                CubeListBuilder.create()
                        .texOffs(250, 27).addBox(-2.2f, -1f, -2.5f, 4.4f, 8.2f, 5f),
                PartPose.offsetAndRotation(-5.4f, 8.5f, -8.5f, -0.18f, 0f, 0f));
        PartDefinition p_leg_fr_low_2_1 = p_leg_fr_2_3.addOrReplaceChild("leg_fr_low",
                CubeListBuilder.create()
                        .texOffs(272, 27).addBox(-1.7f, 0f, -1.8f, 3.4f, 7.5f, 3.6f)
                        .texOffs(290, 27).addBox(-2.1f, 3.5f, -2.2f, 4.2f, 2.2f, 4.4f),
                PartPose.offsetAndRotation(0f, 7f, 0.5f, 0.24f, 0f, 0f));
        p_leg_fr_low_2_1.addOrReplaceChild("leg_fr_paw",
                CubeListBuilder.create()
                        .texOffs(312, 27).addBox(-2f, -0.5f, -3.5f, 4f, 2f, 5f)
                        .texOffs(332, 27).addBox(-1.6f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f)
                        .texOffs(340, 27).addBox(-0.35f, 0.2f, -5.2f, 0.7f, 1.1f, 2f)
                        .texOffs(348, 27).addBox(0.9f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f),
                PartPose.offsetAndRotation(0f, 7f, 0f, -0.06f, 0f, 0f));
        PartDefinition p_leg_bl_2_4 = root.addOrReplaceChild("leg_bl",
                CubeListBuilder.create()
                        .texOffs(356, 27).addBox(-2.2f, -1f, -2.5f, 4.4f, 8.2f, 5f),
                PartPose.offsetAndRotation(4.6f, 8.5f, 8f, 0.26f, 0f, 0f));
        PartDefinition p_leg_bl_low_2_1 = p_leg_bl_2_4.addOrReplaceChild("leg_bl_low",
                CubeListBuilder.create()
                        .texOffs(378, 27).addBox(-1.7f, 0f, -1.8f, 3.4f, 7.5f, 3.6f),
                PartPose.offsetAndRotation(0f, 7f, 0.5f, -0.4f, 0f, 0f));
        p_leg_bl_low_2_1.addOrReplaceChild("leg_bl_paw",
                CubeListBuilder.create()
                        .texOffs(396, 27).addBox(-2f, -0.5f, -3.5f, 4f, 2f, 5f)
                        .texOffs(416, 27).addBox(-1.6f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f)
                        .texOffs(424, 27).addBox(-0.35f, 0.2f, -5.2f, 0.7f, 1.1f, 2f)
                        .texOffs(432, 27).addBox(0.9f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f),
                PartPose.offsetAndRotation(0f, 7f, 0f, 0.14f, 0f, 0f));
        PartDefinition p_leg_br_2_5 = root.addOrReplaceChild("leg_br",
                CubeListBuilder.create()
                        .texOffs(440, 27).addBox(-2.2f, -1f, -2.5f, 4.4f, 8.2f, 5f),
                PartPose.offsetAndRotation(-4.6f, 8.5f, 8f, 0.26f, 0f, 0f));
        PartDefinition p_leg_br_low_2_1 = p_leg_br_2_5.addOrReplaceChild("leg_br_low",
                CubeListBuilder.create()
                        .texOffs(462, 27).addBox(-1.7f, 0f, -1.8f, 3.4f, 7.5f, 3.6f),
                PartPose.offsetAndRotation(0f, 7f, 0.5f, -0.4f, 0f, 0f));
        p_leg_br_low_2_1.addOrReplaceChild("leg_br_paw",
                CubeListBuilder.create()
                        .texOffs(480, 27).addBox(-2f, -0.5f, -3.5f, 4f, 2f, 5f)
                        .texOffs(500, 27).addBox(-1.6f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f)
                        .texOffs(2, 43).addBox(-0.35f, 0.2f, -5.2f, 0.7f, 1.1f, 2f)
                        .texOffs(10, 43).addBox(0.9f, 0.2f, -4.8f, 0.7f, 1.1f, 1.6f),
                PartPose.offsetAndRotation(0f, 7f, 0f, 0.14f, 0f, 0f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public ModelPart root() { return this.root; }

    @Override
    public void setupAnim(AbyssalKnightBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float move = Math.min(1.0f, limbSwingAmount * 1.6f);
        float walkCycle = limbSwing * 0.75f;
        float breathe = Mth.sin(ageInTicks * 0.11f);
        float idleShift = Mth.sin(ageInTicks * 0.08f) * 0.06f * (1.0f - move);

        this.body.y = 9.5f + breathe * 0.35f - Math.abs(Mth.sin(walkCycle)) * 1.4f * move;
        this.body.xRot = breathe * 0.03f + Mth.cos(walkCycle) * 0.08f * move;

        float fl = Mth.cos(walkCycle) * 0.95f * move + idleShift;
        float fr = Mth.cos(walkCycle + Mth.PI) * 0.95f * move - idleShift;
        float bl = Mth.cos(walkCycle + Mth.PI) * 0.95f * move - idleShift;
        float br = Mth.cos(walkCycle) * 0.95f * move + idleShift;
        this.legFL.xRot = -0.18f + fl; this.legFLLow.xRot = 0.24f + Math.max(0f, -fl * 0.9f);
        this.legFR.xRot = -0.18f + fr; this.legFRLow.xRot = 0.24f + Math.max(0f, -fr * 0.9f);
        this.legBL.xRot = 0.26f + bl;  this.legBLLow.xRot = -0.40f - Math.max(0f, bl * 0.85f);
        this.legBR.xRot = 0.26f + br;  this.legBRLow.xRot = -0.40f - Math.max(0f, br * 0.85f);

        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        this.neck1.yRot = yaw * 0.35f + Mth.sin(ageInTicks * 0.07f) * 0.06f;
        this.neck2.yRot = yaw * 0.30f;
        this.head.yRot = yaw * 0.35f;
        this.head.xRot = 0.12f + pitch * 0.6f;
        this.earL.zRot = 0.22f + Mth.sin(ageInTicks * 0.19f) * 0.08f;
        this.earR.zRot = -0.22f - Mth.cos(ageInTicks * 0.17f) * 0.08f;

        float ts = 0.15f + move * 0.12f;
        this.tail1.yRot = Mth.sin(ageInTicks * ts) * 0.26f;
        this.tail2.yRot = Mth.sin(ageInTicks * ts - 0.7f) * 0.32f;
        this.tail3.yRot = Mth.sin(ageInTicks * ts - 1.4f) * 0.38f;
        this.tail4.yRot = Mth.sin(ageInTicks * ts - 2.1f) * 0.42f;

        int cast = entity.getCastState();
        float swing = entity.getAttackAnim(ageInTicks % 1.0f);
        if (cast > 0) {
            this.jaw.xRot = 0.78f + Mth.sin(ageInTicks * 0.5f) * 0.15f;
            this.neck1.xRot = -0.50f;
            if (cast == 2) {
                this.body.xRot = -0.42f;
                this.legFL.xRot = -1.25f; this.legFR.xRot = -1.25f;
            }
            this.body.yRot = (cast == 3) ? ((ageInTicks * 0.85f) % Mth.TWO_PI) : 0f;
        } else {
            this.body.yRot = 0f;
            this.neck1.xRot = -0.22f + breathe * 0.05f - Mth.sin(swing * Mth.PI) * 0.35f;
            this.jaw.xRot = 0.14f + Math.abs(breathe) * 0.12f + Mth.sin(swing * Mth.PI) * 0.68f;
            if (swing > 0.01f) this.legFL.xRot -= Mth.sin(swing * Mth.PI) * 1.1f;
        }
    }
}
