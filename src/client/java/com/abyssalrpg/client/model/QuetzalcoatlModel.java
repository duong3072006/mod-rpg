package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.QuetzalcoatlBossEntity;
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

public class QuetzalcoatlModel extends HierarchicalModel<QuetzalcoatlBossEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(AbyssalRpgMod.id("quetzalcoatl_boss"), "main");

    private final ModelPart root, body, neck, head, jaw;
    private final ModelPart[] segs = new ModelPart[8];

    public QuetzalcoatlModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = this.body.getChild("neck");
        this.head = this.neck.getChild("head");
        this.jaw = this.head.getChild("jaw");
        ModelPart cur = this.body;
        for (int i = 0; i < 8; i++) {
            this.segs[i] = cur.getChild("seg" + i);
            cur = this.segs[i];
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body_2_1 = root.addOrReplaceChild("body",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 8f, -2f, 0f, 0f, 0f));
        PartDefinition p_neck_2_1 = p_body_2_1.addOrReplaceChild("neck",
                CubeListBuilder.create()
                        .texOffs(2, 2).addBox(-3.6f, -3.6f, -9f, 7.2f, 7.2f, 10f),
                PartPose.offsetAndRotation(0f, -2f, -6f, -0.35f, 0f, 0f));
        PartDefinition p_head_2_1 = p_neck_2_1.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(40, 2).addBox(-4.2f, -3.8f, -7.2f, 8.4f, 6.8f, 8f)
                        .texOffs(76, 2).addBox(-2.8f, -1.8f, -14.2f, 5.6f, 3.8f, 7.6f)
                        .texOffs(106, 2).addBox(-2.2f, 1.8f, -13.2f, 0.8f, 2.2f, 0.8f)
                        .texOffs(112, 2).addBox(1.4f, 1.8f, -13.2f, 0.8f, 2.2f, 0.8f)
                        .texOffs(118, 2).addBox(-2.6f, -7.8f, -5.5f, 5.2f, 4.2f, 4.2f)
                        .texOffs(142, 2).addBox(-1.6f, -10.5f, -4.8f, 3.2f, 3f, 3f)
                        .texOffs(158, 2).addBox(4f, -1.5f, -3.8f, 1.4f, 4.4f, 4.4f)
                        .texOffs(174, 2).addBox(-5.4f, -1.5f, -3.8f, 1.4f, 4.4f, 4.4f),
                PartPose.offsetAndRotation(0f, -0.5f, -8.8f, 0.38f, 0f, 0f));
        PartDefinition p_jaw_2_1 = p_head_2_1.addOrReplaceChild("jaw",
                CubeListBuilder.create()
                        .texOffs(190, 2).addBox(-2.6f, 0f, -8.8f, 5.2f, 2f, 9.2f),
                PartPose.offsetAndRotation(0f, 2.2f, -4.8f, 0.35f, 0f, 0f));
        p_jaw_2_1.addOrReplaceChild("beard",
                CubeListBuilder.create()
                        .texOffs(224, 2).addBox(-2.8f, 0f, 0f, 5.6f, 0.0f, 12f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, 1.6f, -5.5f, -1.35f, 0f, 0f));
        p_head_2_1.addOrReplaceChild("plume0",
                CubeListBuilder.create()
                        .texOffs(244, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 18f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, -2.1f));
        p_head_2_1.addOrReplaceChild("plume1",
                CubeListBuilder.create()
                        .texOffs(269, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 19.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, -1.75f));
        p_head_2_1.addOrReplaceChild("plume2",
                CubeListBuilder.create()
                        .texOffs(296, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 20.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, -1.4f));
        p_head_2_1.addOrReplaceChild("plume3",
                CubeListBuilder.create()
                        .texOffs(324, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 22f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, -1.05f));
        p_head_2_1.addOrReplaceChild("plume4",
                CubeListBuilder.create()
                        .texOffs(353, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 23.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, -0.7f));
        p_head_2_1.addOrReplaceChild("plume5",
                CubeListBuilder.create()
                        .texOffs(384, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 24.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, -0.35f));
        p_head_2_1.addOrReplaceChild("plume6",
                CubeListBuilder.create()
                        .texOffs(416, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 26f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, 0f));
        p_head_2_1.addOrReplaceChild("plume7",
                CubeListBuilder.create()
                        .texOffs(449, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 24.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, 0.35f));
        p_head_2_1.addOrReplaceChild("plume8",
                CubeListBuilder.create()
                        .texOffs(481, 2).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 23.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, 0.7f));
        p_head_2_1.addOrReplaceChild("plume9",
                CubeListBuilder.create()
                        .texOffs(2, 30).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 22f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, 1.05f));
        p_head_2_1.addOrReplaceChild("plume10",
                CubeListBuilder.create()
                        .texOffs(31, 30).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 20.667f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, 1.4f));
        p_head_2_1.addOrReplaceChild("plume11",
                CubeListBuilder.create()
                        .texOffs(59, 30).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 19.333f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, 1.75f));
        p_head_2_1.addOrReplaceChild("plume12",
                CubeListBuilder.create()
                        .texOffs(86, 30).addBox(-2.4f, 0f, 1.5f, 4.8f, 0.0f, 18f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.5f, -1.5f, 1.28f, 0f, 2.1f));
        PartDefinition p_seg0_2_2 = p_body_2_1.addOrReplaceChild("seg0",
                CubeListBuilder.create()
                        .texOffs(111, 30).addBox(-3.9f, -3.9f, 0f, 7.8f, 7.8f, 10f),
                PartPose.offsetAndRotation(0f, 0f, -6f, 0f, 0f, 0f));
        p_seg0_2_2.addOrReplaceChild("dfin0",
                CubeListBuilder.create()
                        .texOffs(149, 30).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -3.51f, 1f, 0.25f, 0f, 1.571f));
        PartDefinition p_seg1_2_2 = p_seg0_2_2.addOrReplaceChild("seg1",
                CubeListBuilder.create()
                        .texOffs(170, 30).addBox(-3.6f, -3.6f, 0f, 7.2f, 7.2f, 10f),
                PartPose.offsetAndRotation(0f, 0f, 9.2f, 0f, 0f, 0f));
        p_seg1_2_2.addOrReplaceChild("dfin1",
                CubeListBuilder.create()
                        .texOffs(208, 30).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -3.24f, 1f, 0.25f, 0f, 1.571f));
        PartDefinition p_seg2_2_2 = p_seg1_2_2.addOrReplaceChild("seg2",
                CubeListBuilder.create()
                        .texOffs(229, 30).addBox(-3.2f, -3.2f, 0f, 6.4f, 6.4f, 10f),
                PartPose.offsetAndRotation(0f, 0f, 9.2f, 0f, 0f, 0f));
        p_seg2_2_2.addOrReplaceChild("dfin2",
                CubeListBuilder.create()
                        .texOffs(265, 30).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -2.88f, 1f, 0.25f, 0f, 1.571f));
        PartDefinition p_seg3_2_2 = p_seg2_2_2.addOrReplaceChild("seg3",
                CubeListBuilder.create()
                        .texOffs(286, 30).addBox(-2.8f, -2.8f, 0f, 5.6f, 5.6f, 10f),
                PartPose.offsetAndRotation(0f, 0f, 9.2f, 0f, 0f, 0f));
        p_seg3_2_2.addOrReplaceChild("dfin3",
                CubeListBuilder.create()
                        .texOffs(320, 30).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -2.52f, 1f, 0.25f, 0f, 1.571f));
        PartDefinition p_seg4_2_2 = p_seg3_2_2.addOrReplaceChild("seg4",
                CubeListBuilder.create()
                        .texOffs(341, 30).addBox(-2.4f, -2.4f, 0f, 4.8f, 4.8f, 10f),
                PartPose.offsetAndRotation(0f, 0f, 9.2f, 0f, 0f, 0f));
        p_seg4_2_2.addOrReplaceChild("dfin4",
                CubeListBuilder.create()
                        .texOffs(373, 30).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -2.16f, 1f, 0.25f, 0f, 1.571f));
        PartDefinition p_seg5_2_2 = p_seg4_2_2.addOrReplaceChild("seg5",
                CubeListBuilder.create()
                        .texOffs(394, 30).addBox(-2f, -2f, 0f, 4f, 4f, 10f),
                PartPose.offsetAndRotation(0f, 0f, 9.2f, 0f, 0f, 0f));
        p_seg5_2_2.addOrReplaceChild("dfin5",
                CubeListBuilder.create()
                        .texOffs(424, 30).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.8f, 1f, 0.25f, 0f, 1.571f));
        PartDefinition p_seg6_2_2 = p_seg5_2_2.addOrReplaceChild("seg6",
                CubeListBuilder.create()
                        .texOffs(445, 30).addBox(-1.6f, -1.6f, 0f, 3.2f, 3.2f, 10f),
                PartPose.offsetAndRotation(0f, 0f, 9.2f, 0f, 0f, 0f));
        p_seg6_2_2.addOrReplaceChild("dfin6",
                CubeListBuilder.create()
                        .texOffs(475, 30).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -1.44f, 1f, 0.25f, 0f, 1.571f));
        PartDefinition p_seg7_2_2 = p_seg6_2_2.addOrReplaceChild("seg7",
                CubeListBuilder.create()
                        .texOffs(2, 54).addBox(-1.1f, -1.1f, 0f, 2.2f, 2.2f, 10f),
                PartPose.offsetAndRotation(0f, 0f, 9.2f, 0f, 0f, 0f));
        p_seg7_2_2.addOrReplaceChild("dfin7",
                CubeListBuilder.create()
                        .texOffs(30, 54).addBox(-4.5f, 0f, 0f, 9f, 0.0f, 9.5f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, -0.99f, 1f, 0.25f, 0f, 1.571f));
        p_seg7_2_2.addOrReplaceChild("tail_fan",
                CubeListBuilder.create()
                        .texOffs(51, 54).addBox(-6f, 0f, 0f, 12f, 0.0f, 14f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(0f, 0f, 9f, 0.1f, 0f, 0f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public ModelPart root() { return this.root; }

    @Override
    public void setupAnim(QuetzalcoatlBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        int cast = entity.getCastState();
        float t = ageInTicks * 0.16f;
        this.body.y = 8.0f + Mth.sin(t) * 2.4f;
        this.body.zRot = Mth.cos(t * 0.7f) * 0.14f;
        this.neck.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.45f + Mth.sin(t) * 0.14f;
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.45f;
        this.head.xRot = 0.38f + headPitch * Mth.DEG_TO_RAD * 0.5f;
        this.jaw.xRot = (cast > 0) ? (0.78f + Mth.sin(ageInTicks * 0.5f) * 0.15f) : (0.28f + Math.abs(Mth.sin(t * 0.8f)) * 0.16f);
        for (int i = 0; i < 8; i++) {
            this.segs[i].yRot = Mth.sin(t - i * 0.65f) * (0.26f + i * 0.025f);
            this.segs[i].xRot = Mth.cos(t * 0.85f - i * 0.55f) * 0.16f;
        }
    }
}
