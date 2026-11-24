package com.abyssalrpg.client.sculpt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hierarchical skeletal bone carrying smooth high-poly SculptedMesh geometry
 * (separate base & emissive glow meshes) and live animated transforms.
 * Coordinate convention: +Y UP, +Z FORWARD, +X BOSS LEFT, Y=0 GROUND (real meters!).
 */
public final class SculptedBone {
    public final String name;
    public final float ox, oy, oz;
    public final float baseRx, baseRy, baseRz;

    public float x, y, z;
    public float xRot, yRot, zRot;
    public float xScale = 1f, yScale = 1f, zScale = 1f;

    public SculptedMesh baseMesh = SculptedMesh.EMPTY;
    public SculptedMesh glowMesh = SculptedMesh.EMPTY;
    public final List<SculptedBone> children = new ArrayList<>();
    private final Map<String, SculptedBone> byName = new HashMap<>();

    public SculptedBone(String name, float ox, float oy, float oz, float rx, float ry, float rz) {
        this.name = name;
        this.ox = ox; this.oy = oy; this.oz = oz;
        this.baseRx = rx; this.baseRy = ry; this.baseRz = rz;
        resetPose();
    }

    public SculptedBone(String name, float ox, float oy, float oz) {
        this(name, ox, oy, oz, 0f, 0f, 0f);
    }

    public SculptedBone addChild(SculptedBone child) {
        this.children.add(child);
        this.byName.put(child.name, child);
        return child;
    }

    public SculptedBone getChild(String childName) {
        SculptedBone b = byName.get(childName);
        if (b == null) throw new IllegalArgumentException("Missing SculptedBone child: " + childName + " in " + name);
        return b;
    }

    public void resetPose() {
        this.x = ox; this.y = oy; this.z = oz;
        this.xRot = baseRx; this.yRot = baseRy; this.zRot = baseRz;
        this.xScale = 1f; this.yScale = 1f; this.zScale = 1f;
    }

    public void resetPoseTree() {
        resetPose();
        for (SculptedBone c : children) c.resetPoseTree();
    }

    public void render(PoseStack poseStack, VertexConsumer vc, int light, int overlay,
                       int r, int g, int b, int a, boolean glowPass) {
        poseStack.pushPose();
        if (x != 0f || y != 0f || z != 0f) {
            poseStack.translate(x, y, z);
        }
        if (zRot != 0f) poseStack.mulPose(Axis.ZP.rotation(zRot));
        if (yRot != 0f) poseStack.mulPose(Axis.YP.rotation(yRot));
        if (xRot != 0f) poseStack.mulPose(Axis.XP.rotation(xRot));
        if (xScale != 1f || yScale != 1f || zScale != 1f) {
            poseStack.scale(xScale, yScale, zScale);
        }

        SculptedMesh mesh = glowPass ? glowMesh : baseMesh;
        if (!mesh.isEmpty()) {
            mesh.render(poseStack.last(), vc, light, overlay, r, g, b, a);
        }
        for (SculptedBone c : children) {
            c.render(poseStack, vc, light, overlay, r, g, b, a, glowPass);
        }
        poseStack.popPose();
    }
}

