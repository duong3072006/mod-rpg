package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.AbyssalRpgMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shared 3D geometry builder for glowing Boss Spell VFX Entities (double-sided quads,
 * volumetric plasma cylinders/pillars, glowing crystal octahedrons, and RGB color blending).
 */
public final class VfxRenderHelper {
    public static final ResourceLocation MAGIC_CIRCLE_TEX = AbyssalRpgMod.id("textures/vfx/magic_circle.png");
    public static final ResourceLocation ENERGY_SLASH_TEX = AbyssalRpgMod.id("textures/vfx/energy_slash.png");
    public static final ResourceLocation BEAM_TEX = AbyssalRpgMod.id("textures/vfx/beam.png");
    public static final ResourceLocation SHOCKWAVE_TEX = AbyssalRpgMod.id("textures/vfx/shockwave.png");
    public static final ResourceLocation AURA_SWIRL_TEX = AbyssalRpgMod.id("textures/vfx/aura_swirl.png");

    private VfxRenderHelper() {}

    public static int r(int rgb) { return (rgb >> 16) & 0xFF; }
    public static int g(int rgb) { return (rgb >> 8) & 0xFF; }
    public static int b(int rgb) { return rgb & 0xFF; }

    public static int mixWhite(int ch, float amount) {
        return Mth.clamp((int) (ch + (255 - ch) * amount), 0, 255);
    }

    public static void addVertex(VertexConsumer vc, PoseStack.Pose pose,
                                 float x, float y, float z,
                                 int r, int g, int b, int a,
                                 float u, float v,
                                 float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Emits a double-sided quad so spell geometry never disappears due to backface culling.
     */
    public static void addDoubleQuad(VertexConsumer vc, PoseStack.Pose pose,
                                     float x0, float y0, float z0, float u0, float v0,
                                     float x1, float y1, float z1, float u1, float v1,
                                     float x2, float y2, float z2, float u2, float v2,
                                     float x3, float y3, float z3, float u3, float v3,
                                     int r, int g, int b, int a) {
        // Front winding
        addVertex(vc, pose, x0, y0, z0, r, g, b, a, u0, v0, 0f, 1f, 0f);
        addVertex(vc, pose, x1, y1, z1, r, g, b, a, u1, v1, 0f, 1f, 0f);
        addVertex(vc, pose, x2, y2, z2, r, g, b, a, u2, v2, 0f, 1f, 0f);
        addVertex(vc, pose, x3, y3, z3, r, g, b, a, u3, v3, 0f, 1f, 0f);
        // Back winding
        addVertex(vc, pose, x3, y3, z3, r, g, b, a, u3, v3, 0f, -1f, 0f);
        addVertex(vc, pose, x2, y2, z2, r, g, b, a, u2, v2, 0f, -1f, 0f);
        addVertex(vc, pose, x1, y1, z1, r, g, b, a, u1, v1, 0f, -1f, 0f);
        addVertex(vc, pose, x0, y0, z0, r, g, b, a, u0, v0, 0f, -1f, 0f);
    }

    /**
     * Horizontal flat disc quad centered at (0, y, 0) with half-extent radius.
     */
    public static void drawHorizontalQuad(VertexConsumer vc, PoseStack.Pose pose, float y, float radius,
                                          int r, int g, int b, int a) {
        addDoubleQuad(vc, pose,
                -radius, y, -radius, 0f, 0f,
                -radius, y,  radius, 0f, 1f,
                 radius, y,  radius, 1f, 1f,
                 radius, y, -radius, 1f, 0f,
                r, g, b, a);
    }

    /**
     * Vertical tapered 3D energy cylinder/cone from y0 (radius r0) to y1 (radius r1) with scrolling V UV!
     */
    public static void drawCylinder(VertexConsumer vc, PoseStack.Pose pose, int segments,
                                    float y0, float r0, float y1, float r1,
                                    float vStart, float vEnd,
                                    int r, int g, int b, int a) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = (float) i / segments;
            float u1 = (float) (i + 1) / segments;
            addDoubleQuad(vc, pose,
                    c0 * r0, y0, s0 * r0, u0, vStart,
                    c1 * r0, y0, s1 * r0, u1, vStart,
                    c1 * r1, y1, s1 * r1, u1, vEnd,
                    c0 * r1, y1, s0 * r1, u0, vEnd,
                    r, g, b, a);
        }
    }

    /**
     * 3D Crystalline Octahedron / Plasma Sphere built from 8 glowing diamond facets!
     */
    public static void drawOctahedron(VertexConsumer vc, PoseStack.Pose pose, float radius,
                                      int r, int g, int b, int a) {
        float[][] ring = {
                { radius, 0f, 0f },
                { 0f, 0f, radius },
                { -radius, 0f, 0f },
                { 0f, 0f, -radius }
        };
        for (int i = 0; i < 4; i++) {
            float[] p0 = ring[i];
            float[] p1 = ring[(i + 1) % 4];
            // Upper pyramid facet (degenerate quad to apex)
            addDoubleQuad(vc, pose,
                    p0[0], p0[1], p0[2], 0.15f, 0.9f,
                    p1[0], p1[1], p1[2], 0.85f, 0.9f,
                    0f, radius * 1.15f, 0f, 0.55f, 0.1f,
                    0f, radius * 1.15f, 0f, 0.45f, 0.1f,
                    r, g, b, a);
            // Lower pyramid facet
            addDoubleQuad(vc, pose,
                    p1[0], p1[1], p1[2], 0.85f, 0.1f,
                    p0[0], p0[1], p0[2], 0.15f, 0.1f,
                    0f, -radius * 1.15f, 0f, 0.45f, 0.9f,
                    0f, -radius * 1.15f, 0f, 0.55f, 0.9f,
                    r, g, b, a);
        }
    }
}

