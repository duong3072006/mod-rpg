package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.CryptLichBossEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class CryptLichModel extends HierarchicalModel<CryptLichBossEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(AbyssalRpgMod.id("crypt_lich_boss"), "main");

    private final ModelPart root, body, head, abdomen, fangL, fangR, palpL, palpL2, palpR, palpR2;
    private final ModelPart[] femL = new ModelPart[4], tibL = new ModelPart[4], tipL = new ModelPart[4];
    private final ModelPart[] femR = new ModelPart[4], tibR = new ModelPart[4], tipR = new ModelPart[4];

    public CryptLichModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.abdomen = this.body.getChild("abdomen");
        this.fangL = this.head.getChild("fang_l");
        this.fangR = this.head.getChild("fang_r");
        this.palpL = this.head.getChild("palp_l"); this.palpL2 = this.palpL.getChild("palp_l2");
        this.palpR = this.head.getChild("palp_r"); this.palpR2 = this.palpR.getChild("palp_r2");
        for (int i = 0; i < 4; i++) {
            this.femL[i] = this.body.getChild("leg_l" + i);
            this.tibL[i] = this.femL[i].getChild("leg_l" + i + "_mid");
            this.tipL[i] = this.tibL[i].getChild("leg_l" + i + "_tip");
            this.femR[i] = this.body.getChild("leg_r" + i);
            this.tibR[i] = this.femR[i].getChild("leg_r" + i + "_mid");
            this.tipR[i] = this.tibR[i].getChild("leg_r" + i + "_tip");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body_2_1 = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(2, 2).addBox(-5.2f, -4.2f, -7.2f, 10.4f, 7.8f, 11.4f),
                PartPose.offsetAndRotation(0f, 16.5f, 0f, 0f, 0f, 0f));
        p_body_2_1.addOrReplaceChild("sp_c1",
                CubeListBuilder.create()
                        .texOffs(50, 2).addBox(-0.7f, -4f, -1f, 1.4f, 4.2f, 2f),
                PartPose.offsetAndRotation(2.8f, -4.2f, -3.5f, -0.4f, 0f, 0.35f));
        p_body_2_1.addOrReplaceChild("sp_c2",
                CubeListBuilder.create()
                        .texOffs(60, 2).addBox(-0.7f, -4f, -1f, 1.4f, 4.2f, 2f),
                PartPose.offsetAndRotation(-2.8f, -4.2f, -3.5f, -0.4f, 0f, -0.35f));
        p_body_2_1.addOrReplaceChild("abdomen",
                CubeListBuilder.create()
                        .texOffs(70, 2).addBox(-5.5f, -4.5f, 0f, 11f, 9f, 7f)
                        .texOffs(108, 2).addBox(-7.6f, -6.2f, 6f, 15.2f, 12.2f, 11.2f)
                        .texOffs(166, 2).addBox(6.8f, -2f, 8.5f, 2.4f, 3.8f, 4.5f)
                        .texOffs(184, 2).addBox(-9.2f, -2f, 8.5f, 2.4f, 3.8f, 4.5f)
                        .texOffs(202, 2).addBox(5.5f, -1.5f, 14f, 2f, 3f, 3.5f)
                        .texOffs(216, 2).addBox(-7.5f, -1.5f, 14f, 2f, 3f, 3.5f)
                        .texOffs(230, 2).addBox(-5f, -4.2f, 16.5f, 10f, 8.5f, 6f)
                        .texOffs(264, 2).addBox(-2.2f, -1.8f, 22f, 4.4f, 3.6f, 3f),
                PartPose.offsetAndRotation(0f, -1f, 4f, 0.16f, 0f, 0f));
        PartDefinition p_head_2_4 = p_body_2_1.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(282, 2).addBox(-4.6f, -3.6f, -6.5f, 9.2f, 7.2f, 7f),
                PartPose.offsetAndRotation(0f, 0f, -7f, 0f, 0f, 0f));
        p_head_2_4.addOrReplaceChild("fang_l",
                CubeListBuilder.create()
                        .texOffs(318, 2).addBox(-1.1f, 0f, -2.5f, 2.2f, 2.6f, 3f)
                        .texOffs(332, 2).addBox(-0.6f, 1.8f, -3.5f, 1.2f, 3.4f, 1.4f),
                PartPose.offsetAndRotation(2.4f, 1.5f, -6f, 0.25f, -0.18f, 0f));
        p_head_2_4.addOrReplaceChild("fang_r",
                CubeListBuilder.create()
                        .texOffs(342, 2).addBox(-1.1f, 0f, -2.5f, 2.2f, 2.6f, 3f)
                        .texOffs(356, 2).addBox(-0.6f, 1.8f, -3.5f, 1.2f, 3.4f, 1.4f),
                PartPose.offsetAndRotation(-2.4f, 1.5f, -6f, 0.25f, 0.18f, 0f));
        PartDefinition p_palp_l_2_3 = p_head_2_4.addOrReplaceChild("palp_l",
                CubeListBuilder.create()
                        .texOffs(366, 2).addBox(-0.9f, -0.9f, -5.5f, 1.8f, 1.8f, 6f),
                PartPose.offsetAndRotation(4.5f, 1.5f, -4.5f, 0.3f, -0.35f, 0f));
        p_palp_l_2_3.addOrReplaceChild("palp_l2",
                CubeListBuilder.create()
                        .texOffs(384, 2).addBox(-0.7f, -0.7f, -4.5f, 1.4f, 1.4f, 4.8f),
                PartPose.offsetAndRotation(0f, 0f, -5.2f, 0.55f, 0.2f, 0f));
        PartDefinition p_palp_r_2_4 = p_head_2_4.addOrReplaceChild("palp_r",
                CubeListBuilder.create()
                        .texOffs(400, 2).addBox(-0.9f, -0.9f, -5.5f, 1.8f, 1.8f, 6f),
                PartPose.offsetAndRotation(-4.5f, 1.5f, -4.5f, 0.3f, 0.35f, 0f));
        p_palp_r_2_4.addOrReplaceChild("palp_r2",
                CubeListBuilder.create()
                        .texOffs(418, 2).addBox(-0.7f, -0.7f, -4.5f, 1.4f, 1.4f, 4.8f),
                PartPose.offsetAndRotation(0f, 0f, -5.2f, 0.55f, -0.2f, 0f));
        PartDefinition p_leg_r0_2_5 = p_body_2_1.addOrReplaceChild("leg_r0",
                CubeListBuilder.create()
                        .texOffs(434, 2).addBox(-13f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(468, 2).addBox(-13f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(-4.8f, 0.8f, -5.2f, 0f, -0.72f, 0.78f));
        PartDefinition p_leg_r0_mid_2_1 = p_leg_r0_2_5.addOrReplaceChild("leg_r0_mid",
                CubeListBuilder.create()
                        .texOffs(478, 2).addBox(-12f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(-12.5f, 0f, 0f, 0f, 0f, -1.42f));
        p_leg_r0_mid_2_1.addOrReplaceChild("leg_r0_tip",
                CubeListBuilder.create()
                        .texOffs(2, 29).addBox(-9f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(-11.5f, 0f, 0f, 0f, 0f, -0.42f));
        PartDefinition p_leg_r1_2_6 = p_body_2_1.addOrReplaceChild("leg_r1",
                CubeListBuilder.create()
                        .texOffs(26, 29).addBox(-13f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(60, 29).addBox(-13f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(-4.8f, 0.8f, -2.2f, 0f, -0.26f, 0.78f));
        PartDefinition p_leg_r1_mid_2_1 = p_leg_r1_2_6.addOrReplaceChild("leg_r1_mid",
                CubeListBuilder.create()
                        .texOffs(70, 29).addBox(-12f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(-12.5f, 0f, 0f, 0f, 0f, -1.42f));
        p_leg_r1_mid_2_1.addOrReplaceChild("leg_r1_tip",
                CubeListBuilder.create()
                        .texOffs(100, 29).addBox(-9f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(-11.5f, 0f, 0f, 0f, 0f, -0.42f));
        PartDefinition p_leg_r2_2_7 = p_body_2_1.addOrReplaceChild("leg_r2",
                CubeListBuilder.create()
                        .texOffs(124, 29).addBox(-13f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(158, 29).addBox(-13f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(-4.8f, 0.8f, 0.8f, 0f, 0.24f, 0.78f));
        PartDefinition p_leg_r2_mid_2_1 = p_leg_r2_2_7.addOrReplaceChild("leg_r2_mid",
                CubeListBuilder.create()
                        .texOffs(168, 29).addBox(-12f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(-12.5f, 0f, 0f, 0f, 0f, -1.42f));
        p_leg_r2_mid_2_1.addOrReplaceChild("leg_r2_tip",
                CubeListBuilder.create()
                        .texOffs(198, 29).addBox(-9f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(-11.5f, 0f, 0f, 0f, 0f, -0.42f));
        PartDefinition p_leg_r3_2_8 = p_body_2_1.addOrReplaceChild("leg_r3",
                CubeListBuilder.create()
                        .texOffs(222, 29).addBox(-13f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(256, 29).addBox(-13f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(-4.8f, 0.8f, 3.6f, 0f, 0.72f, 0.78f));
        PartDefinition p_leg_r3_mid_2_1 = p_leg_r3_2_8.addOrReplaceChild("leg_r3_mid",
                CubeListBuilder.create()
                        .texOffs(266, 29).addBox(-12f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(-12.5f, 0f, 0f, 0f, 0f, -1.42f));
        p_leg_r3_mid_2_1.addOrReplaceChild("leg_r3_tip",
                CubeListBuilder.create()
                        .texOffs(296, 29).addBox(-9f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(-11.5f, 0f, 0f, 0f, 0f, -0.42f));
        PartDefinition p_leg_l0_2_9 = p_body_2_1.addOrReplaceChild("leg_l0",
                CubeListBuilder.create()
                        .texOffs(320, 29).addBox(0f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(354, 29).addBox(11.2f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(4.8f, 0.8f, -5.2f, 0f, 0.72f, -0.78f));
        PartDefinition p_leg_l0_mid_2_1 = p_leg_l0_2_9.addOrReplaceChild("leg_l0_mid",
                CubeListBuilder.create()
                        .texOffs(364, 29).addBox(0f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(12.5f, 0f, 0f, 0f, 0f, 1.42f));
        p_leg_l0_mid_2_1.addOrReplaceChild("leg_l0_tip",
                CubeListBuilder.create()
                        .texOffs(394, 29).addBox(0f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(11.5f, 0f, 0f, 0f, 0f, 0.42f));
        PartDefinition p_leg_l1_2_10 = p_body_2_1.addOrReplaceChild("leg_l1",
                CubeListBuilder.create()
                        .texOffs(418, 29).addBox(0f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(452, 29).addBox(11.2f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(4.8f, 0.8f, -2.2f, 0f, 0.26f, -0.78f));
        PartDefinition p_leg_l1_mid_2_1 = p_leg_l1_2_10.addOrReplaceChild("leg_l1_mid",
                CubeListBuilder.create()
                        .texOffs(462, 29).addBox(0f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(12.5f, 0f, 0f, 0f, 0f, 1.42f));
        p_leg_l1_mid_2_1.addOrReplaceChild("leg_l1_tip",
                CubeListBuilder.create()
                        .texOffs(2, 37).addBox(0f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(11.5f, 0f, 0f, 0f, 0f, 0.42f));
        PartDefinition p_leg_l2_2_11 = p_body_2_1.addOrReplaceChild("leg_l2",
                CubeListBuilder.create()
                        .texOffs(26, 37).addBox(0f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(60, 37).addBox(11.2f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(4.8f, 0.8f, 0.8f, 0f, -0.24f, -0.78f));
        PartDefinition p_leg_l2_mid_2_1 = p_leg_l2_2_11.addOrReplaceChild("leg_l2_mid",
                CubeListBuilder.create()
                        .texOffs(70, 37).addBox(0f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(12.5f, 0f, 0f, 0f, 0f, 1.42f));
        p_leg_l2_mid_2_1.addOrReplaceChild("leg_l2_tip",
                CubeListBuilder.create()
                        .texOffs(100, 37).addBox(0f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(11.5f, 0f, 0f, 0f, 0f, 0.42f));
        PartDefinition p_leg_l3_2_12 = p_body_2_1.addOrReplaceChild("leg_l3",
                CubeListBuilder.create()
                        .texOffs(124, 37).addBox(0f, -1.4f, -1.4f, 13f, 2.8f, 2.8f)
                        .texOffs(158, 37).addBox(11.2f, -3.4f, -0.7f, 1.8f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(4.8f, 0.8f, 3.6f, 0f, -0.72f, -0.78f));
        PartDefinition p_leg_l3_mid_2_1 = p_leg_l3_2_12.addOrReplaceChild("leg_l3_mid",
                CubeListBuilder.create()
                        .texOffs(168, 37).addBox(0f, -1f, -1f, 12f, 2f, 2f),
                PartPose.offsetAndRotation(12.5f, 0f, 0f, 0f, 0f, 1.42f));
        p_leg_l3_mid_2_1.addOrReplaceChild("leg_l3_tip",
                CubeListBuilder.create()
                        .texOffs(198, 37).addBox(0f, -0.65f, -0.65f, 9f, 1.3f, 1.3f),
                PartPose.offsetAndRotation(11.5f, 0f, 0f, 0f, 0f, 0.42f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public ModelPart root() { return this.root; }

    @Override
    public void setupAnim(CryptLichBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float move = Math.min(1.0f, limbSwingAmount * 1.8f);
        float breathe = Mth.sin(ageInTicks * 0.12f);
        this.body.y = 16.5f + breathe * 0.45f;
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6f;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD * 0.4f;
        this.abdomen.xRot = 0.16f + breathe * 0.08f + move * 0.08f;

        float[] baseY = { 0.72f, 0.26f, -0.24f, -0.72f };
        int cast = entity.getCastState();
        for (int i = 0; i < 4; i++) {
            float pL = (i % 2 == 0) ? 0f : Mth.PI;
            float pR = (i % 2 == 0) ? Mth.PI : 0f;
            float idleL = Mth.sin(ageInTicks * 0.10f + i * 1.4f) * 0.07f;
            float idleR = Mth.cos(ageInTicks * 0.10f + i * 1.4f) * 0.07f;
            float swingL = Mth.cos(limbSwing * 1.0f + pL) * 0.45f * move;
            float liftL = Math.max(0f, Mth.sin(limbSwing * 1.0f + pL)) * 0.48f * move;
            float swingR = Mth.cos(limbSwing * 1.0f + pR) * 0.45f * move;
            float liftR = Math.max(0f, Mth.sin(limbSwing * 1.0f + pR)) * 0.48f * move;
            float rearUp = (cast > 0 && i == 0) ? 0.65f : 0f;

            this.femL[i].yRot = baseY[i] + swingL + idleL * 0.5f;
            this.femL[i].zRot = -0.78f - liftL - idleL - rearUp;
            this.tibL[i].zRot = 1.42f + liftL * 0.65f + idleL * 0.8f - rearUp * 0.5f;
            this.tipL[i].zRot = 0.42f - liftL * 0.35f;

            this.femR[i].yRot = -baseY[i] - swingR - idleR * 0.5f;
            this.femR[i].zRot = 0.78f + liftR + idleR + rearUp;
            this.tibR[i].zRot = -1.42f - liftR * 0.65f - idleR * 0.8f + rearUp * 0.5f;
            this.tipR[i].zRot = -0.42f + liftR * 0.35f;
        }
        float ch = Mth.sin(ageInTicks * (cast > 0 ? 0.55f : 0.22f));
        this.fangL.yRot = -0.18f - Math.abs(ch) * (cast > 0 ? 0.45f : 0.18f);
        this.fangR.yRot = 0.18f + Math.abs(ch) * (cast > 0 ? 0.45f : 0.18f);
        this.palpL.xRot = 0.30f + Mth.sin(ageInTicks * 0.18f) * 0.18f;
        this.palpL2.xRot = 0.55f + Mth.cos(ageInTicks * 0.18f) * 0.22f;
        this.palpR.xRot = 0.30f + Mth.cos(ageInTicks * 0.18f) * 0.18f;
        this.palpR2.xRot = 0.55f + Mth.sin(ageInTicks * 0.18f) * 0.22f;
        this.body.xRot = (cast > 0) ? -0.24f : 0f;
    }
}
