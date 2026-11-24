package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.ApepBossEntity;
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

public class ApepModel extends HierarchicalModel<ApepBossEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(AbyssalRpgMod.id("apep_boss"), "main");

    private final ModelPart root, body, neck1, neck2, neck3, neck4, head, jaw, hoodL, hoodR, tail1, tail2, tail3, tail4;

    public ApepModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck1 = this.body.getChild("neck1");
        this.neck2 = this.neck1.getChild("neck2");
        this.neck3 = this.neck2.getChild("neck3");
        this.neck4 = this.neck3.getChild("neck4");
        this.head = this.neck4.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.hoodL = this.neck4.getChild("hood_l");
        this.hoodR = this.neck4.getChild("hood_r");
        this.tail1 = this.body.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
        this.tail4 = this.tail3.getChild("tail4");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body_2_1 = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(2, 2).addBox(-9.5f, -4.5f, -7.5f, 19f, 9f, 15f),
                PartPose.offsetAndRotation(0f, 19.5f, 0f, 0f, 0f, 0f));
        PartDefinition p_tail1_2_1 = p_body_2_1.addOrReplaceChild("tail1",
                CubeListBuilder.create()
                        .texOffs(72, 2).addBox(-4f, -4f, 0f, 8f, 8f, 12f),
                PartPose.offsetAndRotation(7.5f, 0.5f, 5.5f, 0f, -0.65f, 0f));
        PartDefinition p_tail2_2_1 = p_tail1_2_1.addOrReplaceChild("tail2",
                CubeListBuilder.create()
                        .texOffs(114, 2).addBox(-3.5f, -3.5f, 0f, 7f, 7f, 12f),
                PartPose.offsetAndRotation(0f, 0.3f, 11f, 0f, -0.7f, 0f));
        PartDefinition p_tail3_2_1 = p_tail2_2_1.addOrReplaceChild("tail3",
                CubeListBuilder.create()
                        .texOffs(154, 2).addBox(-2.8f, -2.8f, 0f, 5.6f, 5.6f, 11f),
                PartPose.offsetAndRotation(0f, 0.3f, 11f, 0f, 0.85f, 0f));
        p_tail3_2_1.addOrReplaceChild("tail4",
                CubeListBuilder.create()
                        .texOffs(190, 2).addBox(-2f, -2f, 0f, 4f, 4f, 10f),
                PartPose.offsetAndRotation(0f, 0.3f, 10f, 0f, 0.65f, 0f));
        PartDefinition p_neck1_2_2 = p_body_2_1.addOrReplaceChild("neck1",
                CubeListBuilder.create()
                        .texOffs(220, 2).addBox(-4.6f, -4.6f, -10.5f, 9.2f, 9.2f, 11f),
                PartPose.offsetAndRotation(0f, -3.5f, -3.5f, -1.15f, 0f, 0f));
        PartDefinition p_neck2_2_1 = p_neck1_2_2.addOrReplaceChild("neck2",
                CubeListBuilder.create()
                        .texOffs(264, 2).addBox(-4.2f, -4.2f, -10f, 8.4f, 8.4f, 10.5f),
                PartPose.offsetAndRotation(0f, 0f, -9.8f, -0.18f, 0f, 0f));
        PartDefinition p_neck3_2_1 = p_neck2_2_1.addOrReplaceChild("neck3",
                CubeListBuilder.create()
                        .texOffs(306, 2).addBox(-3.8f, -3.8f, -9.5f, 7.6f, 7.6f, 10f),
                PartPose.offsetAndRotation(0f, 0f, -9.5f, 0.52f, 0f, 0f));
        PartDefinition p_neck4_2_1 = p_neck3_2_1.addOrReplaceChild("neck4",
                CubeListBuilder.create()
                        .texOffs(344, 2).addBox(-3.5f, -3.5f, -8f, 7f, 7f, 8.5f),
                PartPose.offsetAndRotation(0f, 0f, -8.8f, 0.65f, 0f, 0f));
        p_neck4_2_1.addOrReplaceChild("hood_r",
                CubeListBuilder.create()
                        .texOffs(378, 2).addBox(-8.5f, 0f, 0f, 17f, 0.0f, 17f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(-2.5f, -1.5f, -3.5f, 1.25f, 0f, -1.05f));
        p_neck4_2_1.addOrReplaceChild("hood_low_r",
                CubeListBuilder.create()
                        .texOffs(414, 2).addBox(-7.5f, 0f, 0f, 15f, 0.0f, 15f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(-2.5f, 0.5f, -1.5f, 1.18f, 0f, -1.52f));
        p_neck4_2_1.addOrReplaceChild("hood_l",
                CubeListBuilder.create()
                        .texOffs(446, 2).addBox(-8.5f, 0f, 0f, 17f, 0.0f, 17f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(2.5f, -1.5f, -3.5f, 1.25f, 0f, 1.05f));
        p_neck4_2_1.addOrReplaceChild("hood_low_l",
                CubeListBuilder.create()
                        .texOffs(2, 28).addBox(-7.5f, 0f, 0f, 15f, 0.0f, 15f, EnumSet.of(Direction.DOWN)),
                PartPose.offsetAndRotation(2.5f, 0.5f, -1.5f, 1.18f, 0f, 1.52f));
        PartDefinition p_head_2_5 = p_neck4_2_1.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(34, 28).addBox(-4.8f, -4.2f, -7.5f, 9.6f, 7.2f, 8.4f)
                        .texOffs(74, 28).addBox(-3.2f, -2f, -13.8f, 6.4f, 4f, 6.8f)
                        .texOffs(104, 28).addBox(-2.6f, 1.8f, -13f, 1f, 2.6f, 1f)
                        .texOffs(110, 28).addBox(1.6f, 1.8f, -13f, 1f, 2.6f, 1f),
                PartPose.offsetAndRotation(0f, -0.5f, -7.5f, 0.25f, 0f, 0f));
        p_head_2_5.addOrReplaceChild("jaw",
                CubeListBuilder.create()
                        .texOffs(116, 28).addBox(-3f, 0f, -8.5f, 6f, 2.2f, 9f),
                PartPose.offsetAndRotation(0f, 2.2f, -5f, 0.42f, 0f, 0f));
        p_head_2_5.addOrReplaceChild("ch_l",
                CubeListBuilder.create()
                        .texOffs(148, 28).addBox(-1f, -1f, 0f, 2f, 2f, 10.5f),
                PartPose.offsetAndRotation(4.2f, -3.6f, -3f, -0.35f, 0.35f, 0f));
        p_head_2_5.addOrReplaceChild("ch_r",
                CubeListBuilder.create()
                        .texOffs(176, 28).addBox(-1f, -1f, 0f, 2f, 2f, 10.5f),
                PartPose.offsetAndRotation(-4.2f, -3.6f, -3f, -0.35f, -0.35f, 0f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public ModelPart root() { return this.root; }

    @Override
    public void setupAnim(ApepBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        int cast = entity.getCastState();
        float t = ageInTicks * 0.11f;
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        this.neck1.yRot = yaw * 0.25f + Mth.sin(t) * 0.16f;
        this.neck2.yRot = yaw * 0.25f + Mth.sin(t - 0.7f) * 0.18f;
        this.neck3.yRot = yaw * 0.25f + Mth.sin(t - 1.4f) * 0.16f;
        this.neck4.yRot = yaw * 0.25f;
        this.neck1.xRot = (cast > 0 ? -1.32f : -1.15f) + Mth.cos(t) * 0.06f;
        this.head.xRot = 0.25f + headPitch * Mth.DEG_TO_RAD * 0.5f;
        this.jaw.xRot = (cast > 0) ? (0.85f + Mth.sin(ageInTicks * 0.5f) * 0.14f) : (0.34f + Math.abs(Mth.sin(t)) * 0.18f);
        float flare = (cast > 0 ? 0.22f : 0f) + Mth.sin(t * 1.4f) * 0.08f;
        this.hoodL.zRot = 1.05f + flare;
        this.hoodR.zRot = -1.05f - flare;
        this.tail1.yRot = -0.65f + Mth.sin(t + limbSwing * 0.5f) * 0.20f;
        this.tail2.yRot = -0.70f + Mth.sin(t - 0.8f + limbSwing * 0.5f) * 0.25f;
        this.tail3.yRot = 0.85f + Mth.sin(t - 1.6f) * 0.28f;
        this.tail4.yRot = 0.65f + Mth.sin(t - 2.4f) * 0.34f;
    }
}
