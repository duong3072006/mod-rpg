package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.TiamatBossEntity;
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

public class TiamatModel extends HierarchicalModel<TiamatBossEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(AbyssalRpgMod.id("tiamat_boss"), "main");

    private final ModelPart root, body, tcl1, tcl2, tcl3, tcr1, tcr2, tcr3;
    private final ModelPart[] n1 = new ModelPart[7], n2 = new ModelPart[7], n3 = new ModelPart[7], hd = new ModelPart[7], jaw = new ModelPart[7];
    private static final float[] BASE_RX = { -0.55f, -1.35f, -1.15f, -1.15f, -0.78f, -0.78f, -0.95f };
    private static final float[] BASE_RY = {  0.00f,  0.00f, -0.45f,  0.45f, -0.82f,  0.82f,  0.22f };

    public TiamatModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.tcl1 = this.body.getChild("tc_l1"); this.tcl2 = this.tcl1.getChild("tc_l2"); this.tcl3 = this.tcl2.getChild("tc_l3");
        this.tcr1 = this.body.getChild("tc_r1"); this.tcr2 = this.tcr1.getChild("tc_r2"); this.tcr3 = this.tcr2.getChild("tc_r3");
        for (int i = 0; i < 7; i++) {
            this.n1[i] = this.body.getChild("t" + i + "_n1");
            this.n2[i] = this.n1[i].getChild("t" + i + "_n2");
            this.n3[i] = this.n2[i].getChild("t" + i + "_n3");
            this.hd[i] = this.n3[i].getChild("t" + i + "_hd");
            this.jaw[i] = this.hd[i].getChild("t" + i + "_jaw");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body_2_1 = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(2, 2).addBox(-10f, -7.5f, -8.5f, 20f, 15f, 16f),
                PartPose.offsetAndRotation(0f, 16.5f, 0f, 0f, 0f, 0f));
        PartDefinition p_tc_r1_2_1 = p_body_2_1.addOrReplaceChild("tc_r1",
                CubeListBuilder.create()
                        .texOffs(76, 2).addBox(-4.2f, -4.2f, 0f, 8.4f, 8.4f, 11f),
                PartPose.offsetAndRotation(-8f, 2.5f, 5.5f, 0f, 0.55f, 0f));
        PartDefinition p_tc_r2_2_1 = p_tc_r1_2_1.addOrReplaceChild("tc_r2",
                CubeListBuilder.create()
                        .texOffs(118, 2).addBox(-3.2f, -3.2f, 0f, 6.4f, 6.4f, 11f),
                PartPose.offsetAndRotation(0f, 0.5f, 10.2f, 0f, 0.65f, 0f));
        p_tc_r2_2_1.addOrReplaceChild("tc_r3",
                CubeListBuilder.create()
                        .texOffs(156, 2).addBox(-2.2f, -2.2f, 0f, 4.4f, 4.4f, 10f),
                PartPose.offsetAndRotation(0f, 0.4f, 10.2f, 0f, -0.55f, 0f));
        PartDefinition p_tc_l1_2_2 = p_body_2_1.addOrReplaceChild("tc_l1",
                CubeListBuilder.create()
                        .texOffs(188, 2).addBox(-4.2f, -4.2f, 0f, 8.4f, 8.4f, 11f),
                PartPose.offsetAndRotation(8f, 2.5f, 5.5f, 0f, -0.55f, 0f));
        PartDefinition p_tc_l2_2_1 = p_tc_l1_2_2.addOrReplaceChild("tc_l2",
                CubeListBuilder.create()
                        .texOffs(230, 2).addBox(-3.2f, -3.2f, 0f, 6.4f, 6.4f, 11f),
                PartPose.offsetAndRotation(0f, 0.5f, 10.2f, 0f, -0.65f, 0f));
        p_tc_l2_2_1.addOrReplaceChild("tc_l3",
                CubeListBuilder.create()
                        .texOffs(268, 2).addBox(-2.2f, -2.2f, 0f, 4.4f, 4.4f, 10f),
                PartPose.offsetAndRotation(0f, 0.4f, 10.2f, 0f, 0.55f, 0f));
        PartDefinition p_t0_n1_2_3 = p_body_2_1.addOrReplaceChild("t0_n1",
                CubeListBuilder.create()
                        .texOffs(300, 2).addBox(-2.6f, -2.6f, -7.92f, 5.2f, 5.2f, 8.52f),
                PartPose.offsetAndRotation(0f, -3.5f, -8f, -0.55f, 0f, 0f));
        PartDefinition p_t0_n2_2_1 = p_t0_n1_2_3.addOrReplaceChild("t0_n2",
                CubeListBuilder.create()
                        .texOffs(332, 2).addBox(-2.3f, -2.3f, -7.38f, 4.6f, 4.6f, 7.88f),
                PartPose.offsetAndRotation(0f, 0f, -7.42f, 0.35f, 0f, 0f));
        PartDefinition p_t0_n3_2_1 = p_t0_n2_2_1.addOrReplaceChild("t0_n3",
                CubeListBuilder.create()
                        .texOffs(360, 2).addBox(-2f, -2f, -6.48f, 4f, 4f, 6.98f),
                PartPose.offsetAndRotation(0f, 0f, -6.88f, 0.4f, 0f, 0f));
        PartDefinition p_t0_hd_2_1 = p_t0_n3_2_1.addOrReplaceChild("t0_hd",
                CubeListBuilder.create()
                        .texOffs(384, 2).addBox(-3.4f, -3f, -5.8f, 6.8f, 5.4f, 6.4f)
                        .texOffs(414, 2).addBox(-2.2f, -1.5f, -10.8f, 4.4f, 3f, 5.6f)
                        .texOffs(438, 2).addBox(-1.8f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f)
                        .texOffs(444, 2).addBox(1.15f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f),
                PartPose.offsetAndRotation(0f, -0.2f, -6.08f, 0.32f, 0f, 0f));
        p_t0_hd_2_1.addOrReplaceChild("t0_jaw",
                CubeListBuilder.create()
                        .texOffs(450, 2).addBox(-2f, 0f, -6.2f, 4f, 1.6f, 6.6f),
                PartPose.offsetAndRotation(0f, 1.6f, -4.2f, 0.42f, 0f, 0f));
        p_t0_hd_2_1.addOrReplaceChild("t0_cr0",
                CubeListBuilder.create()
                        .texOffs(474, 2).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10.8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -2f));
        p_t0_hd_2_1.addOrReplaceChild("t0_cr1",
                CubeListBuilder.create()
                        .texOffs(491, 2).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 11.7f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -1.333f));
        p_t0_hd_2_1.addOrReplaceChild("t0_cr2",
                CubeListBuilder.create()
                        .texOffs(2, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 12.6f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -0.667f));
        p_t0_hd_2_1.addOrReplaceChild("t0_cr3",
                CubeListBuilder.create()
                        .texOffs(21, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 13.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0f));
        p_t0_hd_2_1.addOrReplaceChild("t0_cr4",
                CubeListBuilder.create()
                        .texOffs(41, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 12.6f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0.667f));
        p_t0_hd_2_1.addOrReplaceChild("t0_cr5",
                CubeListBuilder.create()
                        .texOffs(60, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 11.7f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 1.333f));
        p_t0_hd_2_1.addOrReplaceChild("t0_cr6",
                CubeListBuilder.create()
                        .texOffs(78, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10.8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 2f));
        PartDefinition p_t1_n1_2_4 = p_body_2_1.addOrReplaceChild("t1_n1",
                CubeListBuilder.create()
                        .texOffs(95, 35).addBox(-2.6f, -2.6f, -11f, 5.2f, 5.2f, 11.6f),
                PartPose.offsetAndRotation(0f, -7f, -2.5f, -1.35f, 0f, 0f));
        PartDefinition p_t1_n2_2_1 = p_t1_n1_2_4.addOrReplaceChild("t1_n2",
                CubeListBuilder.create()
                        .texOffs(133, 35).addBox(-2.3f, -2.3f, -10.25f, 4.6f, 4.6f, 10.75f),
                PartPose.offsetAndRotation(0f, 0f, -10.5f, 0.35f, 0f, 0f));
        PartDefinition p_t1_n3_2_1 = p_t1_n2_2_1.addOrReplaceChild("t1_n3",
                CubeListBuilder.create()
                        .texOffs(167, 35).addBox(-2f, -2f, -9f, 4f, 4f, 9.5f),
                PartPose.offsetAndRotation(0f, 0f, -9.75f, 0.4f, 0f, 0f));
        PartDefinition p_t1_hd_2_1 = p_t1_n3_2_1.addOrReplaceChild("t1_hd",
                CubeListBuilder.create()
                        .texOffs(197, 35).addBox(-3.4f, -3f, -5.8f, 6.8f, 5.4f, 6.4f)
                        .texOffs(227, 35).addBox(-2.2f, -1.5f, -10.8f, 4.4f, 3f, 5.6f)
                        .texOffs(251, 35).addBox(-1.8f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f)
                        .texOffs(257, 35).addBox(1.15f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f),
                PartPose.offsetAndRotation(0f, -0.2f, -8.6f, 0.32f, 0f, 0f));
        p_t1_hd_2_1.addOrReplaceChild("t1_jaw",
                CubeListBuilder.create()
                        .texOffs(263, 35).addBox(-2f, 0f, -6.2f, 4f, 1.6f, 6.6f),
                PartPose.offsetAndRotation(0f, 1.6f, -4.2f, 0.42f, 0f, 0f));
        p_t1_hd_2_1.addOrReplaceChild("t1_cr0",
                CubeListBuilder.create()
                        .texOffs(287, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10.8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -2f));
        p_t1_hd_2_1.addOrReplaceChild("t1_cr1",
                CubeListBuilder.create()
                        .texOffs(304, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 11.7f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -1.333f));
        p_t1_hd_2_1.addOrReplaceChild("t1_cr2",
                CubeListBuilder.create()
                        .texOffs(322, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 12.6f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -0.667f));
        p_t1_hd_2_1.addOrReplaceChild("t1_cr3",
                CubeListBuilder.create()
                        .texOffs(341, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 13.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0f));
        p_t1_hd_2_1.addOrReplaceChild("t1_cr4",
                CubeListBuilder.create()
                        .texOffs(361, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 12.6f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0.667f));
        p_t1_hd_2_1.addOrReplaceChild("t1_cr5",
                CubeListBuilder.create()
                        .texOffs(380, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 11.7f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 1.333f));
        p_t1_hd_2_1.addOrReplaceChild("t1_cr6",
                CubeListBuilder.create()
                        .texOffs(398, 35).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10.8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 2f));
        PartDefinition p_t2_n1_2_5 = p_body_2_1.addOrReplaceChild("t2_n1",
                CubeListBuilder.create()
                        .texOffs(415, 35).addBox(-2.6f, -2.6f, -9.504f, 5.2f, 5.2f, 10.104f),
                PartPose.offsetAndRotation(5.5f, -6.5f, -3f, -1.15f, -0.45f, 0f));
        PartDefinition p_t2_n2_2_1 = p_t2_n1_2_5.addOrReplaceChild("t2_n2",
                CubeListBuilder.create()
                        .texOffs(451, 35).addBox(-2.3f, -2.3f, -8.856f, 4.6f, 4.6f, 9.356f),
                PartPose.offsetAndRotation(0f, 0f, -9.004f, 0.35f, 0f, 0f));
        PartDefinition p_t2_n3_2_1 = p_t2_n2_2_1.addOrReplaceChild("t2_n3",
                CubeListBuilder.create()
                        .texOffs(483, 35).addBox(-2f, -2f, -7.776f, 4f, 4f, 8.276f),
                PartPose.offsetAndRotation(0f, 0f, -8.356f, 0.4f, 0f, 0f));
        PartDefinition p_t2_hd_2_1 = p_t2_n3_2_1.addOrReplaceChild("t2_hd",
                CubeListBuilder.create()
                        .texOffs(2, 55).addBox(-3.4f, -3f, -5.8f, 6.8f, 5.4f, 6.4f)
                        .texOffs(32, 55).addBox(-2.2f, -1.5f, -10.8f, 4.4f, 3f, 5.6f)
                        .texOffs(56, 55).addBox(-1.8f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f)
                        .texOffs(62, 55).addBox(1.15f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f),
                PartPose.offsetAndRotation(0f, -0.2f, -7.376f, 0.32f, 0f, 0f));
        p_t2_hd_2_1.addOrReplaceChild("t2_jaw",
                CubeListBuilder.create()
                        .texOffs(68, 55).addBox(-2f, 0f, -6.2f, 4f, 1.6f, 6.6f),
                PartPose.offsetAndRotation(0f, 1.6f, -4.2f, 0.42f, 0f, 0f));
        p_t2_hd_2_1.addOrReplaceChild("t2_cr0",
                CubeListBuilder.create()
                        .texOffs(92, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -2f));
        p_t2_hd_2_1.addOrReplaceChild("t2_cr1",
                CubeListBuilder.create()
                        .texOffs(106, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -1.333f));
        p_t2_hd_2_1.addOrReplaceChild("t2_cr2",
                CubeListBuilder.create()
                        .texOffs(121, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -0.667f));
        p_t2_hd_2_1.addOrReplaceChild("t2_cr3",
                CubeListBuilder.create()
                        .texOffs(137, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0f));
        p_t2_hd_2_1.addOrReplaceChild("t2_cr4",
                CubeListBuilder.create()
                        .texOffs(153, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0.667f));
        p_t2_hd_2_1.addOrReplaceChild("t2_cr5",
                CubeListBuilder.create()
                        .texOffs(169, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 1.333f));
        p_t2_hd_2_1.addOrReplaceChild("t2_cr6",
                CubeListBuilder.create()
                        .texOffs(184, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 2f));
        PartDefinition p_t3_n1_2_6 = p_body_2_1.addOrReplaceChild("t3_n1",
                CubeListBuilder.create()
                        .texOffs(198, 55).addBox(-2.6f, -2.6f, -9.504f, 5.2f, 5.2f, 10.104f),
                PartPose.offsetAndRotation(-5.5f, -6.5f, -3f, -1.15f, 0.45f, 0f));
        PartDefinition p_t3_n2_2_1 = p_t3_n1_2_6.addOrReplaceChild("t3_n2",
                CubeListBuilder.create()
                        .texOffs(234, 55).addBox(-2.3f, -2.3f, -8.856f, 4.6f, 4.6f, 9.356f),
                PartPose.offsetAndRotation(0f, 0f, -9.004f, 0.35f, 0f, 0f));
        PartDefinition p_t3_n3_2_1 = p_t3_n2_2_1.addOrReplaceChild("t3_n3",
                CubeListBuilder.create()
                        .texOffs(266, 55).addBox(-2f, -2f, -7.776f, 4f, 4f, 8.276f),
                PartPose.offsetAndRotation(0f, 0f, -8.356f, 0.4f, 0f, 0f));
        PartDefinition p_t3_hd_2_1 = p_t3_n3_2_1.addOrReplaceChild("t3_hd",
                CubeListBuilder.create()
                        .texOffs(294, 55).addBox(-3.4f, -3f, -5.8f, 6.8f, 5.4f, 6.4f)
                        .texOffs(324, 55).addBox(-2.2f, -1.5f, -10.8f, 4.4f, 3f, 5.6f)
                        .texOffs(348, 55).addBox(-1.8f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f)
                        .texOffs(354, 55).addBox(1.15f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f),
                PartPose.offsetAndRotation(0f, -0.2f, -7.376f, 0.32f, 0f, 0f));
        p_t3_hd_2_1.addOrReplaceChild("t3_jaw",
                CubeListBuilder.create()
                        .texOffs(360, 55).addBox(-2f, 0f, -6.2f, 4f, 1.6f, 6.6f),
                PartPose.offsetAndRotation(0f, 1.6f, -4.2f, 0.42f, 0f, 0f));
        p_t3_hd_2_1.addOrReplaceChild("t3_cr0",
                CubeListBuilder.create()
                        .texOffs(384, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -2f));
        p_t3_hd_2_1.addOrReplaceChild("t3_cr1",
                CubeListBuilder.create()
                        .texOffs(398, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -1.333f));
        p_t3_hd_2_1.addOrReplaceChild("t3_cr2",
                CubeListBuilder.create()
                        .texOffs(413, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -0.667f));
        p_t3_hd_2_1.addOrReplaceChild("t3_cr3",
                CubeListBuilder.create()
                        .texOffs(429, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0f));
        p_t3_hd_2_1.addOrReplaceChild("t3_cr4",
                CubeListBuilder.create()
                        .texOffs(445, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0.667f));
        p_t3_hd_2_1.addOrReplaceChild("t3_cr5",
                CubeListBuilder.create()
                        .texOffs(461, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 1.333f));
        p_t3_hd_2_1.addOrReplaceChild("t3_cr6",
                CubeListBuilder.create()
                        .texOffs(476, 55).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 2f));
        PartDefinition p_t4_n1_2_7 = p_body_2_1.addOrReplaceChild("t4_n1",
                CubeListBuilder.create()
                        .texOffs(2, 74).addBox(-2.6f, -2.6f, -8.448f, 5.2f, 5.2f, 9.048f),
                PartPose.offsetAndRotation(8.5f, -3.5f, -5.5f, -0.78f, -0.82f, 0f));
        PartDefinition p_t4_n2_2_1 = p_t4_n1_2_7.addOrReplaceChild("t4_n2",
                CubeListBuilder.create()
                        .texOffs(36, 74).addBox(-2.3f, -2.3f, -7.872f, 4.6f, 4.6f, 8.372f),
                PartPose.offsetAndRotation(0f, 0f, -7.948f, 0.35f, 0f, 0f));
        PartDefinition p_t4_n3_2_1 = p_t4_n2_2_1.addOrReplaceChild("t4_n3",
                CubeListBuilder.create()
                        .texOffs(66, 74).addBox(-2f, -2f, -6.912f, 4f, 4f, 7.412f),
                PartPose.offsetAndRotation(0f, 0f, -7.372f, 0.4f, 0f, 0f));
        PartDefinition p_t4_hd_2_1 = p_t4_n3_2_1.addOrReplaceChild("t4_hd",
                CubeListBuilder.create()
                        .texOffs(92, 74).addBox(-3.4f, -3f, -5.8f, 6.8f, 5.4f, 6.4f)
                        .texOffs(122, 74).addBox(-2.2f, -1.5f, -10.8f, 4.4f, 3f, 5.6f)
                        .texOffs(146, 74).addBox(-1.8f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f)
                        .texOffs(152, 74).addBox(1.15f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f),
                PartPose.offsetAndRotation(0f, -0.2f, -6.512f, 0.32f, 0f, 0f));
        p_t4_hd_2_1.addOrReplaceChild("t4_jaw",
                CubeListBuilder.create()
                        .texOffs(158, 74).addBox(-2f, 0f, -6.2f, 4f, 1.6f, 6.6f),
                PartPose.offsetAndRotation(0f, 1.6f, -4.2f, 0.42f, 0f, 0f));
        p_t4_hd_2_1.addOrReplaceChild("t4_cr0",
                CubeListBuilder.create()
                        .texOffs(182, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -2f));
        p_t4_hd_2_1.addOrReplaceChild("t4_cr1",
                CubeListBuilder.create()
                        .texOffs(196, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -1.333f));
        p_t4_hd_2_1.addOrReplaceChild("t4_cr2",
                CubeListBuilder.create()
                        .texOffs(211, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -0.667f));
        p_t4_hd_2_1.addOrReplaceChild("t4_cr3",
                CubeListBuilder.create()
                        .texOffs(227, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0f));
        p_t4_hd_2_1.addOrReplaceChild("t4_cr4",
                CubeListBuilder.create()
                        .texOffs(243, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0.667f));
        p_t4_hd_2_1.addOrReplaceChild("t4_cr5",
                CubeListBuilder.create()
                        .texOffs(259, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 1.333f));
        p_t4_hd_2_1.addOrReplaceChild("t4_cr6",
                CubeListBuilder.create()
                        .texOffs(274, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 2f));
        PartDefinition p_t5_n1_2_8 = p_body_2_1.addOrReplaceChild("t5_n1",
                CubeListBuilder.create()
                        .texOffs(288, 74).addBox(-2.6f, -2.6f, -8.448f, 5.2f, 5.2f, 9.048f),
                PartPose.offsetAndRotation(-8.5f, -3.5f, -5.5f, -0.78f, 0.82f, 0f));
        PartDefinition p_t5_n2_2_1 = p_t5_n1_2_8.addOrReplaceChild("t5_n2",
                CubeListBuilder.create()
                        .texOffs(322, 74).addBox(-2.3f, -2.3f, -7.872f, 4.6f, 4.6f, 8.372f),
                PartPose.offsetAndRotation(0f, 0f, -7.948f, 0.35f, 0f, 0f));
        PartDefinition p_t5_n3_2_1 = p_t5_n2_2_1.addOrReplaceChild("t5_n3",
                CubeListBuilder.create()
                        .texOffs(352, 74).addBox(-2f, -2f, -6.912f, 4f, 4f, 7.412f),
                PartPose.offsetAndRotation(0f, 0f, -7.372f, 0.4f, 0f, 0f));
        PartDefinition p_t5_hd_2_1 = p_t5_n3_2_1.addOrReplaceChild("t5_hd",
                CubeListBuilder.create()
                        .texOffs(378, 74).addBox(-3.4f, -3f, -5.8f, 6.8f, 5.4f, 6.4f)
                        .texOffs(408, 74).addBox(-2.2f, -1.5f, -10.8f, 4.4f, 3f, 5.6f)
                        .texOffs(432, 74).addBox(-1.8f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f)
                        .texOffs(438, 74).addBox(1.15f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f),
                PartPose.offsetAndRotation(0f, -0.2f, -6.512f, 0.32f, 0f, 0f));
        p_t5_hd_2_1.addOrReplaceChild("t5_jaw",
                CubeListBuilder.create()
                        .texOffs(444, 74).addBox(-2f, 0f, -6.2f, 4f, 1.6f, 6.6f),
                PartPose.offsetAndRotation(0f, 1.6f, -4.2f, 0.42f, 0f, 0f));
        p_t5_hd_2_1.addOrReplaceChild("t5_cr0",
                CubeListBuilder.create()
                        .texOffs(468, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -2f));
        p_t5_hd_2_1.addOrReplaceChild("t5_cr1",
                CubeListBuilder.create()
                        .texOffs(482, 74).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -1.333f));
        p_t5_hd_2_1.addOrReplaceChild("t5_cr2",
                CubeListBuilder.create()
                        .texOffs(2, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -0.667f));
        p_t5_hd_2_1.addOrReplaceChild("t5_cr3",
                CubeListBuilder.create()
                        .texOffs(18, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0f));
        p_t5_hd_2_1.addOrReplaceChild("t5_cr4",
                CubeListBuilder.create()
                        .texOffs(34, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0.667f));
        p_t5_hd_2_1.addOrReplaceChild("t5_cr5",
                CubeListBuilder.create()
                        .texOffs(50, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 1.333f));
        p_t5_hd_2_1.addOrReplaceChild("t5_cr6",
                CubeListBuilder.create()
                        .texOffs(65, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 2f));
        PartDefinition p_t6_n1_2_9 = p_body_2_1.addOrReplaceChild("t6_n1",
                CubeListBuilder.create()
                        .texOffs(79, 92).addBox(-2.6f, -2.6f, -8.096f, 5.2f, 5.2f, 8.696f),
                PartPose.offsetAndRotation(4f, -5.5f, -6f, -0.95f, 0.22f, 0f));
        PartDefinition p_t6_n2_2_1 = p_t6_n1_2_9.addOrReplaceChild("t6_n2",
                CubeListBuilder.create()
                        .texOffs(111, 92).addBox(-2.3f, -2.3f, -7.544f, 4.6f, 4.6f, 8.044f),
                PartPose.offsetAndRotation(0f, 0f, -7.596f, 0.35f, 0f, 0f));
        PartDefinition p_t6_n3_2_1 = p_t6_n2_2_1.addOrReplaceChild("t6_n3",
                CubeListBuilder.create()
                        .texOffs(141, 92).addBox(-2f, -2f, -6.624f, 4f, 4f, 7.124f),
                PartPose.offsetAndRotation(0f, 0f, -7.044f, 0.4f, 0f, 0f));
        PartDefinition p_t6_hd_2_1 = p_t6_n3_2_1.addOrReplaceChild("t6_hd",
                CubeListBuilder.create()
                        .texOffs(167, 92).addBox(-3.4f, -3f, -5.8f, 6.8f, 5.4f, 6.4f)
                        .texOffs(197, 92).addBox(-2.2f, -1.5f, -10.8f, 4.4f, 3f, 5.6f)
                        .texOffs(221, 92).addBox(-1.8f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f)
                        .texOffs(227, 92).addBox(1.15f, 1.3f, -10.2f, 0.65f, 1.6f, 0.65f),
                PartPose.offsetAndRotation(0f, -0.2f, -6.224f, 0.32f, 0f, 0f));
        p_t6_hd_2_1.addOrReplaceChild("t6_jaw",
                CubeListBuilder.create()
                        .texOffs(233, 92).addBox(-2f, 0f, -6.2f, 4f, 1.6f, 6.6f),
                PartPose.offsetAndRotation(0f, 1.6f, -4.2f, 0.42f, 0f, 0f));
        p_t6_hd_2_1.addOrReplaceChild("t6_cr0",
                CubeListBuilder.create()
                        .texOffs(257, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -2f));
        p_t6_hd_2_1.addOrReplaceChild("t6_cr1",
                CubeListBuilder.create()
                        .texOffs(271, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -1.333f));
        p_t6_hd_2_1.addOrReplaceChild("t6_cr2",
                CubeListBuilder.create()
                        .texOffs(286, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, -0.667f));
        p_t6_hd_2_1.addOrReplaceChild("t6_cr3",
                CubeListBuilder.create()
                        .texOffs(302, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 10f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0f));
        p_t6_hd_2_1.addOrReplaceChild("t6_cr4",
                CubeListBuilder.create()
                        .texOffs(318, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 9.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 0.667f));
        p_t6_hd_2_1.addOrReplaceChild("t6_cr5",
                CubeListBuilder.create()
                        .texOffs(334, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 1.333f));
        p_t6_hd_2_1.addOrReplaceChild("t6_cr6",
                CubeListBuilder.create()
                        .texOffs(349, 92).addBox(-1.8f, 0f, 1f, 3.6f, 0.0f, 8f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.2f, -1.5f, 1.28f, 0f, 2f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public ModelPart root() { return this.root; }

    @Override
    public void setupAnim(TiamatBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        int cast = entity.getCastState();
        float yaw = netHeadYaw * Mth.DEG_TO_RAD * 0.45f;
        float pitch = headPitch * Mth.DEG_TO_RAD * 0.35f;
        for (int i = 0; i < 7; i++) {
            float ph = i * 1.05f;
            float spd = 0.11f + (i % 3) * 0.015f;
            this.n1[i].yRot = BASE_RY[i] + yaw * 0.35f + Mth.sin(ageInTicks * spd + ph) * 0.15f;
            this.n2[i].yRot = Mth.sin(ageInTicks * spd + ph - 0.8f) * 0.18f;
            this.n3[i].yRot = Mth.sin(ageInTicks * spd + ph - 1.5f) * 0.16f;
            this.n1[i].xRot = BASE_RX[i] - (cast > 0 ? 0.16f : 0f) + Mth.cos(ageInTicks * spd + ph) * 0.08f;
            this.hd[i].xRot = 0.32f + pitch;
            this.jaw[i].xRot = (cast > 0 ? 0.84f : 0.36f) + Math.abs(Mth.sin(ageInTicks * 0.20f + ph)) * 0.22f;
        }
        float t = ageInTicks * 0.12f;
        this.tcl1.yRot = -0.55f + Mth.sin(t) * 0.22f; this.tcl2.yRot = -0.65f + Mth.sin(t - 0.8f) * 0.26f; this.tcl3.yRot = 0.55f + Mth.sin(t - 1.6f) * 0.32f;
        this.tcr1.yRot =  0.55f - Mth.sin(t) * 0.22f; this.tcr2.yRot =  0.65f - Mth.sin(t - 0.8f) * 0.26f; this.tcr3.yRot = -0.55f - Mth.sin(t - 1.6f) * 0.32f;
    }
}
