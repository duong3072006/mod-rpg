package com.abyssalrpg.client.sculpt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Compact flat-array baked high-poly 3D quad mesh with smooth per-vertex normals.
 * Layout per vertex (8 floats): x, y, z, u, v, nx, ny, nz. 4 vertices (32 floats) per quad.
 */
public final class SculptedMesh {
    public static final SculptedMesh EMPTY = new SculptedMesh(new float[0]);

    private final float[] data;
    private final int vertexCount;

    public SculptedMesh(float[] data) {
        this.data = data;
        this.vertexCount = data.length / 8;
    }

    public boolean isEmpty() {
        return vertexCount == 0;
    }

    public int getQuadCount() {
        return vertexCount / 4;
    }

    public float[] rawData() {
        return data;
    }

    public void render(PoseStack.Pose pose, VertexConsumer vc, int packedLight, int packedOverlay,
                       int r, int g, int b, int a) {
        final float[] d = this.data;
        final int n = this.vertexCount;
        for (int i = 0; i < n; i++) {
            int o = i * 8;
            vc.addVertex(pose, d[o], d[o + 1], d[o + 2])
                    .setColor(r, g, b, a)
                    .setUv(d[o + 3], d[o + 4])
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, d[o + 5], d[o + 6], d[o + 7]);
        }
    }
}

