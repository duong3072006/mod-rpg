package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.MoltenTitanBossEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class MoltenTitanModel extends HierarchicalModel<MoltenTitanBossEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(AbyssalRpgMod.id("molten_titan_boss"), "main");

    private final ModelPart root, body, tail1, tail2, tail3, tail4;
    private final ModelPart nC1, nC2, nC3, hC, jC, nL1, nL2, nL3, hL, jL, nR1, nR2, nR3, hR, jR;
    private final ModelPart legFL, legFLLow, legFR, legFRLow, legBL, legBLLow, legBR, legBRLow;

    public MoltenTitanModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.nC1 = this.body.getChild("n_c1"); this.nC2 = this.nC1.getChild("n_c2"); this.nC3 = this.nC2.getChild("n_c3"); this.hC = this.nC3.getChild("h_c"); this.jC = this.hC.getChild("j_c");
        this.nL1 = this.body.getChild("n_l1"); this.nL2 = this.nL1.getChild("n_l2"); this.nL3 = this.nL2.getChild("n_l3"); this.hL = this.nL3.getChild("h_l"); this.jL = this.hL.getChild("j_l");
        this.nR1 = this.body.getChild("n_r1"); this.nR2 = this.nR1.getChild("n_r2"); this.nR3 = this.nR2.getChild("n_r3"); this.hR = this.nR3.getChild("h_r"); this.jR = this.hR.getChild("j_r");
        this.tail1 = this.body.getChild("tail1"); this.tail2 = this.tail1.getChild("tail2"); this.tail3 = this.tail2.getChild("tail3"); this.tail4 = this.tail3.getChild("tail4");
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
                        .texOffs(2, 2).addBox(-7.5f, -5.5f, -11f, 15f, 11f, 13f)
                        .texOffs(60, 2).addBox(-6f, -4.5f, 1.5f, 12f, 9.5f, 10f),
                PartPose.offsetAndRotation(0f, 10.5f, 0f, 0f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("sh_l",
                CubeListBuilder.create()
                        .texOffs(106, 2).addBox(-1.5f, -3.5f, -3.5f, 3f, 5f, 7f),
                PartPose.offsetAndRotation(7.2f, -4.5f, -8.5f, 0f, 0f, 0.35f));
        p_body_2_1.addOrReplaceChild("sh_r",
                CubeListBuilder.create()
                        .texOffs(128, 2).addBox(-1.5f, -3.5f, -3.5f, 3f, 5f, 7f),
                PartPose.offsetAndRotation(-7.2f, -4.5f, -8.5f, 0f, 0f, -0.35f));
        p_body_2_1.addOrReplaceChild("dsp0",
                CubeListBuilder.create()
                        .texOffs(150, 2).addBox(-0.9f, -4.5f, -1.2f, 1.8f, 4.8f, 2.4f),
                PartPose.offsetAndRotation(0f, -5.2f, -8f, -0.5f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("dsp1",
                CubeListBuilder.create()
                        .texOffs(162, 2).addBox(-0.9f, -4.5f, -1.2f, 1.8f, 4.8f, 2.4f),
                PartPose.offsetAndRotation(0f, -5.2f, -2.8f, -0.5f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("dsp2",
                CubeListBuilder.create()
                        .texOffs(174, 2).addBox(-0.9f, -4.5f, -1.2f, 1.8f, 4.8f, 2.4f),
                PartPose.offsetAndRotation(0f, -5.2f, 2.4f, -0.5f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("dsp3",
                CubeListBuilder.create()
                        .texOffs(186, 2).addBox(-0.9f, -4.5f, -1.2f, 1.8f, 4.8f, 2.4f),
                PartPose.offsetAndRotation(0f, -5.2f, 7.6f, -0.5f, 0f, 0f));
        PartDefinition p_n_c1_2_7 = p_body_2_1.addOrReplaceChild("n_c1",
                CubeListBuilder.create()
                        .texOffs(198, 2).addBox(-2.6f, -2.6f, -8.5f, 5.2f, 5.2f, 9f)
                        .texOffs(230, 2).addBox(-3f, -3f, -2.4f, 6f, 6f, 1.8f),
                PartPose.offsetAndRotation(0f, -3.5f, -10.5f, -0.68f, 0f, 0f));
        PartDefinition p_n_c2_2_1 = p_n_c1_2_7.addOrReplaceChild("n_c2",
                CubeListBuilder.create()
                        .texOffs(248, 2).addBox(-2.3f, -2.3f, -8f, 4.6f, 4.6f, 8.5f),
                PartPose.offsetAndRotation(0f, 0f, -8f, 0.25f, 0f, 0f));
        PartDefinition p_n_c3_2_1 = p_n_c2_2_1.addOrReplaceChild("n_c3",
                CubeListBuilder.create()
                        .texOffs(278, 2).addBox(-2f, -2f, -7f, 4f, 4f, 7.5f),
                PartPose.offsetAndRotation(0f, 0f, -7.5f, 0.32f, 0f, 0f));
        PartDefinition p_h_c_2_1 = p_n_c3_2_1.addOrReplaceChild("h_c",
                CubeListBuilder.create()
                        .texOffs(304, 2).addBox(-3.4f, -3f, -6f, 6.8f, 5.2f, 6.8f)
                        .texOffs(334, 2).addBox(-2.2f, -1.6f, -11.5f, 4.4f, 3.2f, 6f)
                        .texOffs(358, 2).addBox(-1.9f, 1.3f, -10.8f, 0.7f, 1.6f, 0.7f)
                        .texOffs(364, 2).addBox(1.2f, 1.3f, -10.8f, 0.7f, 1.6f, 0.7f),
                PartPose.offsetAndRotation(0f, -0.2f, -6.8f, 0.22f, 0f, 0f));
        p_h_c_2_1.addOrReplaceChild("j_c",
                CubeListBuilder.create()
                        .texOffs(370, 2).addBox(-2f, 0f, -6.8f, 4f, 1.8f, 7.2f),
                PartPose.offsetAndRotation(0f, 1.5f, -4.5f, 0.22f, 0f, 0f));
        p_h_c_2_1.addOrReplaceChild("hl_c",
                CubeListBuilder.create()
                        .texOffs(396, 2).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(2.8f, -2.6f, -1.8f, -0.55f, 0.25f, 0.18f));
        p_h_c_2_1.addOrReplaceChild("hr_c",
                CubeListBuilder.create()
                        .texOffs(418, 2).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(-2.8f, -2.6f, -1.8f, -0.55f, -0.25f, -0.18f));
        PartDefinition p_n_l1_2_8 = p_body_2_1.addOrReplaceChild("n_l1",
                CubeListBuilder.create()
                        .texOffs(440, 2).addBox(-2.6f, -2.6f, -8.5f, 5.2f, 5.2f, 9f)
                        .texOffs(472, 2).addBox(-3f, -3f, -2.4f, 6f, 6f, 1.8f),
                PartPose.offsetAndRotation(5.8f, -2.5f, -10.5f, -0.68f, -0.58f, 0.25f));
        PartDefinition p_n_l2_2_1 = p_n_l1_2_8.addOrReplaceChild("n_l2",
                CubeListBuilder.create()
                        .texOffs(2, 28).addBox(-2.3f, -2.3f, -8f, 4.6f, 4.6f, 8.5f),
                PartPose.offsetAndRotation(0f, 0f, -8f, 0.25f, 0f, 0f));
        PartDefinition p_n_l3_2_1 = p_n_l2_2_1.addOrReplaceChild("n_l3",
                CubeListBuilder.create()
                        .texOffs(32, 28).addBox(-2f, -2f, -7f, 4f, 4f, 7.5f),
                PartPose.offsetAndRotation(0f, 0f, -7.5f, 0.32f, 0f, 0f));
        PartDefinition p_h_l_2_1 = p_n_l3_2_1.addOrReplaceChild("h_l",
                CubeListBuilder.create()
                        .texOffs(58, 28).addBox(-3.4f, -3f, -6f, 6.8f, 5.2f, 6.8f)
                        .texOffs(88, 28).addBox(-2.2f, -1.6f, -11.5f, 4.4f, 3.2f, 6f)
                        .texOffs(112, 28).addBox(-1.9f, 1.3f, -10.8f, 0.7f, 1.6f, 0.7f)
                        .texOffs(118, 28).addBox(1.2f, 1.3f, -10.8f, 0.7f, 1.6f, 0.7f),
                PartPose.offsetAndRotation(0f, -0.2f, -6.8f, 0.22f, 0f, 0f));
        p_h_l_2_1.addOrReplaceChild("j_l",
                CubeListBuilder.create()
                        .texOffs(124, 28).addBox(-2f, 0f, -6.8f, 4f, 1.8f, 7.2f),
                PartPose.offsetAndRotation(0f, 1.5f, -4.5f, 0.22f, 0f, 0f));
        p_h_l_2_1.addOrReplaceChild("hl_l",
                CubeListBuilder.create()
                        .texOffs(150, 28).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(2.8f, -2.6f, -1.8f, -0.55f, 0.25f, 0.18f));
        p_h_l_2_1.addOrReplaceChild("hr_l",
                CubeListBuilder.create()
                        .texOffs(172, 28).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(-2.8f, -2.6f, -1.8f, -0.55f, -0.25f, -0.18f));
        PartDefinition p_n_r1_2_9 = p_body_2_1.addOrReplaceChild("n_r1",
                CubeListBuilder.create()
                        .texOffs(194, 28).addBox(-2.6f, -2.6f, -8.5f, 5.2f, 5.2f, 9f)
                        .texOffs(226, 28).addBox(-3f, -3f, -2.4f, 6f, 6f, 1.8f),
                PartPose.offsetAndRotation(-5.8f, -2.5f, -10.5f, -0.68f, 0.58f, -0.25f));
        PartDefinition p_n_r2_2_1 = p_n_r1_2_9.addOrReplaceChild("n_r2",
                CubeListBuilder.create()
                        .texOffs(244, 28).addBox(-2.3f, -2.3f, -8f, 4.6f, 4.6f, 8.5f),
                PartPose.offsetAndRotation(0f, 0f, -8f, 0.25f, 0f, 0f));
        PartDefinition p_n_r3_2_1 = p_n_r2_2_1.addOrReplaceChild("n_r3",
                CubeListBuilder.create()
                        .texOffs(274, 28).addBox(-2f, -2f, -7f, 4f, 4f, 7.5f),
                PartPose.offsetAndRotation(0f, 0f, -7.5f, 0.32f, 0f, 0f));
        PartDefinition p_h_r_2_1 = p_n_r3_2_1.addOrReplaceChild("h_r",
                CubeListBuilder.create()
                        .texOffs(300, 28).addBox(-3.4f, -3f, -6f, 6.8f, 5.2f, 6.8f)
                        .texOffs(330, 28).addBox(-2.2f, -1.6f, -11.5f, 4.4f, 3.2f, 6f)
                        .texOffs(354, 28).addBox(-1.9f, 1.3f, -10.8f, 0.7f, 1.6f, 0.7f)
                        .texOffs(360, 28).addBox(1.2f, 1.3f, -10.8f, 0.7f, 1.6f, 0.7f),
                PartPose.offsetAndRotation(0f, -0.2f, -6.8f, 0.22f, 0f, 0f));
        p_h_r_2_1.addOrReplaceChild("j_r",
                CubeListBuilder.create()
                        .texOffs(366, 28).addBox(-2f, 0f, -6.8f, 4f, 1.8f, 7.2f),
                PartPose.offsetAndRotation(0f, 1.5f, -4.5f, 0.22f, 0f, 0f));
        p_h_r_2_1.addOrReplaceChild("hl_r",
                CubeListBuilder.create()
                        .texOffs(392, 28).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(2.8f, -2.6f, -1.8f, -0.55f, 0.25f, 0.18f));
        p_h_r_2_1.addOrReplaceChild("hr_r",
                CubeListBuilder.create()
                        .texOffs(414, 28).addBox(-0.8f, -0.8f, 0f, 1.6f, 1.6f, 7.2f),
                PartPose.offsetAndRotation(-2.8f, -2.6f, -1.8f, -0.55f, -0.25f, -0.18f));
        PartDefinition p_tail1_2_10 = p_body_2_1.addOrReplaceChild("tail1",
                CubeListBuilder.create()
                        .texOffs(436, 28).addBox(-3.8f, -3.2f, 0f, 7.6f, 6.4f, 8.5f),
                PartPose.offsetAndRotation(0f, -0.5f, 11f, -0.14f, 0f, 0f));
        PartDefinition p_tail2_2_1 = p_tail1_2_10.addOrReplaceChild("tail2",
                CubeListBuilder.create()
                        .texOffs(472, 28).addBox(-2.8f, -2.4f, 0f, 5.6f, 4.8f, 8.5f),
                PartPose.offsetAndRotation(0f, 0.3f, 8f, 0.08f, 0f, 0f));
        PartDefinition p_tail3_2_1 = p_tail2_2_1.addOrReplaceChild("tail3",
                CubeListBuilder.create()
                        .texOffs(2, 46).addBox(-1.9f, -1.7f, 0f, 3.8f, 3.4f, 8f),
                PartPose.offsetAndRotation(0f, 0.2f, 8f, 0.1f, 0f, 0f));
        p_tail3_2_1.addOrReplaceChild("tail4",
                CubeListBuilder.create()
                        .texOffs(28, 46).addBox(-1.2f, -1.1f, 0f, 2.4f, 2.2f, 7.5f),
                PartPose.offsetAndRotation(0f, 0.1f, 7.5f, 0.08f, 0f, 0f));
        PartDefinition p_leg_fl_2_2 = root.addOrReplaceChild("leg_fl",
                CubeListBuilder.create()
                        .texOffs(52, 46).addBox(-2.8f, -1.5f, -3f, 5.6f, 7.8f, 6f),
                PartPose.offsetAndRotation(7.6f, 9.5f, -7f, -0.15f, 0f, 0f));
        p_leg_fl_2_2.addOrReplaceChild("leg_fl_low",
                CubeListBuilder.create()
                        .texOffs(78, 46).addBox(-2.3f, 0f, -2.3f, 4.6f, 7.2f, 4.6f)
                        .texOffs(100, 46).addBox(-2.8f, 5.8f, -4.4f, 5.6f, 2.4f, 6.8f)
                        .texOffs(128, 46).addBox(-2.2f, 6.5f, -6f, 0.9f, 1.3f, 2f)
                        .texOffs(136, 46).addBox(-0.45f, 6.5f, -6.4f, 0.9f, 1.3f, 2.3f)
                        .texOffs(146, 46).addBox(1.3f, 6.5f, -6f, 0.9f, 1.3f, 2f),
                PartPose.offsetAndRotation(0f, 6.2f, 0.5f, 0.22f, 0f, 0f));
        PartDefinition p_leg_fr_2_3 = root.addOrReplaceChild("leg_fr",
                CubeListBuilder.create()
                        .texOffs(154, 46).addBox(-2.8f, -1.5f, -3f, 5.6f, 7.8f, 6f),
                PartPose.offsetAndRotation(-7.6f, 9.5f, -7f, -0.15f, 0f, 0f));
        p_leg_fr_2_3.addOrReplaceChild("leg_fr_low",
                CubeListBuilder.create()
                        .texOffs(180, 46).addBox(-2.3f, 0f, -2.3f, 4.6f, 7.2f, 4.6f)
                        .texOffs(202, 46).addBox(-2.8f, 5.8f, -4.4f, 5.6f, 2.4f, 6.8f)
                        .texOffs(230, 46).addBox(-2.2f, 6.5f, -6f, 0.9f, 1.3f, 2f)
                        .texOffs(238, 46).addBox(-0.45f, 6.5f, -6.4f, 0.9f, 1.3f, 2.3f)
                        .texOffs(248, 46).addBox(1.3f, 6.5f, -6f, 0.9f, 1.3f, 2f),
                PartPose.offsetAndRotation(0f, 6.2f, 0.5f, 0.22f, 0f, 0f));
        PartDefinition p_leg_bl_2_4 = root.addOrReplaceChild("leg_bl",
                CubeListBuilder.create()
                        .texOffs(256, 46).addBox(-2.8f, -1.5f, -3f, 5.6f, 7.8f, 6f),
                PartPose.offsetAndRotation(6.6f, 9.5f, 7.5f, 0.22f, 0f, 0f));
        p_leg_bl_2_4.addOrReplaceChild("leg_bl_low",
                CubeListBuilder.create()
                        .texOffs(282, 46).addBox(-2.3f, 0f, -2.3f, 4.6f, 7.2f, 4.6f)
                        .texOffs(304, 46).addBox(-2.8f, 5.8f, -4.4f, 5.6f, 2.4f, 6.8f)
                        .texOffs(332, 46).addBox(-2.2f, 6.5f, -6f, 0.9f, 1.3f, 2f)
                        .texOffs(340, 46).addBox(-0.45f, 6.5f, -6.4f, 0.9f, 1.3f, 2.3f)
                        .texOffs(350, 46).addBox(1.3f, 6.5f, -6f, 0.9f, 1.3f, 2f),
                PartPose.offsetAndRotation(0f, 6.2f, 0.5f, -0.32f, 0f, 0f));
        PartDefinition p_leg_br_2_5 = root.addOrReplaceChild("leg_br",
                CubeListBuilder.create()
                        .texOffs(358, 46).addBox(-2.8f, -1.5f, -3f, 5.6f, 7.8f, 6f),
                PartPose.offsetAndRotation(-6.6f, 9.5f, 7.5f, 0.22f, 0f, 0f));
        p_leg_br_2_5.addOrReplaceChild("leg_br_low",
                CubeListBuilder.create()
                        .texOffs(384, 46).addBox(-2.3f, 0f, -2.3f, 4.6f, 7.2f, 4.6f)
                        .texOffs(406, 46).addBox(-2.8f, 5.8f, -4.4f, 5.6f, 2.4f, 6.8f)
                        .texOffs(434, 46).addBox(-2.2f, 6.5f, -6f, 0.9f, 1.3f, 2f)
                        .texOffs(442, 46).addBox(-0.45f, 6.5f, -6.4f, 0.9f, 1.3f, 2.3f)
                        .texOffs(452, 46).addBox(1.3f, 6.5f, -6f, 0.9f, 1.3f, 2f),
                PartPose.offsetAndRotation(0f, 6.2f, 0.5f, -0.32f, 0f, 0f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public ModelPart root() { return this.root; }

    @Override
    public void setupAnim(MoltenTitanBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float move = Math.min(1.0f, limbSwingAmount * 1.65f);
        float wc = limbSwing * 0.65f;
        float breathe = Mth.sin(ageInTicks * 0.10f);
        this.body.y = 10.5f + breathe * 0.35f - Math.abs(Mth.sin(wc)) * 1.1f * move;
        this.body.zRot = Mth.cos(wc) * 0.06f * move;

        float fl = Mth.cos(wc) * 0.85f * move, fr = Mth.cos(wc + Mth.PI) * 0.85f * move;
        this.legFL.xRot = -0.15f + fl; this.legFLLow.xRot = 0.22f + Math.max(0f, -fl * 0.85f);
        this.legFR.xRot = -0.15f + fr; this.legFRLow.xRot = 0.22f + Math.max(0f, -fr * 0.85f);
        this.legBL.xRot = 0.22f + fr;  this.legBLLow.xRot = -0.32f - Math.max(0f, fr * 0.8f);
        this.legBR.xRot = 0.22f + fl;  this.legBRLow.xRot = -0.32f - Math.max(0f, fl * 0.8f);

        int cast = entity.getCastState();
        float yaw = netHeadYaw * Mth.DEG_TO_RAD * 0.45f;
        float pitch = headPitch * Mth.DEG_TO_RAD * 0.35f;
        float roar = (cast > 0) ? 0.75f : 0.16f;

        this.nC1.yRot = yaw * 0.5f + Mth.sin(ageInTicks * 0.10f) * 0.14f;
        this.nC2.yRot = Mth.sin(ageInTicks * 0.10f - 0.8f) * 0.16f;
        this.nC3.yRot = yaw * 0.5f + Mth.sin(ageInTicks * 0.10f - 1.5f) * 0.14f;
        this.nC1.xRot = (cast > 0 ? -0.92f : -0.68f) + Mth.cos(ageInTicks * 0.10f) * 0.08f;
        this.nC2.xRot = 0.25f + Mth.sin(ageInTicks * 0.10f) * 0.08f;
        this.hC.xRot = 0.22f + pitch;
        this.jC.xRot = roar + Math.abs(Mth.sin(ageInTicks * 0.18f)) * 0.18f;

        this.nL1.yRot = -0.58f + yaw * 0.35f + Mth.sin(ageInTicks * 0.12f + 2.1f) * 0.18f;
        this.nL2.yRot = Mth.sin(ageInTicks * 0.12f + 1.3f) * 0.20f;
        this.nL3.yRot = Mth.sin(ageInTicks * 0.12f + 0.5f) * 0.16f;
        this.nL1.xRot = (cast > 0 ? -0.88f : -0.64f) + Mth.sin(ageInTicks * 0.12f + 1.0f) * 0.10f;
        this.hL.xRot = 0.22f + pitch * 0.8f;
        this.jL.xRot = roar + Math.abs(Mth.sin(ageInTicks * 0.19f + 2.0f)) * 0.20f;

        this.nR1.yRot = 0.58f + yaw * 0.35f + Mth.sin(ageInTicks * 0.11f + 4.2f) * 0.18f;
        this.nR2.yRot = Mth.sin(ageInTicks * 0.11f + 3.4f) * 0.20f;
        this.nR3.yRot = Mth.sin(ageInTicks * 0.11f + 2.6f) * 0.16f;
        this.nR1.xRot = (cast > 0 ? -0.88f : -0.64f) + Mth.cos(ageInTicks * 0.11f) * 0.10f;
        this.hR.xRot = 0.22f + pitch * 0.8f;
        this.jR.xRot = roar + Math.abs(Mth.cos(ageInTicks * 0.17f)) * 0.20f;

        this.tail1.yRot = Mth.sin(ageInTicks * 0.11f) * 0.22f;
        this.tail2.yRot = Mth.sin(ageInTicks * 0.11f - 0.8f) * 0.28f;
        this.tail3.yRot = Mth.sin(ageInTicks * 0.11f - 1.6f) * 0.34f;
        this.tail4.yRot = Mth.sin(ageInTicks * 0.11f - 2.4f) * 0.40f;
    }
}
