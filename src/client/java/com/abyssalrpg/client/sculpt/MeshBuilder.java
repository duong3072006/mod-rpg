package com.abyssalrpg.client.sculpt;

import org.joml.Vector3f;

import java.util.Arrays;

/**
 * 30% Realistic / 70% Minecraft-Boxy Blockbench 3D Mesh Builder (Cataclysm / Ice & Fire / Ender Dragon x10 style):
 * - 70% Crisp Minecraft square/boxy voxel architecture: flat-faced boxes, 4-sided axis-aligned tapered box prisms,
 *   stepped boxy armor/carapace/brow/nostril slabs & crisp 85% flat Minecraft directional shading!
 * - 30% Sculpted realistic anatomy: graceful S-curved articulated spines/necks/coils, tapered predatory proportions,
 *   curved 4-sided horns/fangs/claws, arched 3D wings/feathers/hoods, and 15% smooth depth shading!
 */
public final class MeshBuilder {
    private float[] buf = new float[2048];
    private int len = 0;

    private void ensure(int extra) {
        if (len + extra > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + extra));
        }
    }

    public void addVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        ensure(8);
        buf[len++] = x; buf[len++] = y; buf[len++] = z;
        buf[len++] = u; buf[len++] = v;
        buf[len++] = nx; buf[len++] = ny; buf[len++] = nz;
    }

    public void addQuad(float x0, float y0, float z0, float u0, float v0, float nx0, float ny0, float nz0,
                        float x1, float y1, float z1, float u1, float v1, float nx1, float ny1, float nz1,
                        float x2, float y2, float z2, float u2, float v2, float nx2, float ny2, float nz2,
                        float x3, float y3, float z3, float u3, float v3, float nx3, float ny3, float nz3) {
        addVertex(x0, y0, z0, u0, v0, nx0, ny0, nz0);
        addVertex(x1, y1, z1, u1, v1, nx1, ny1, nz1);
        addVertex(x2, y2, z2, u2, v2, nx2, ny2, nz2);
        addVertex(x3, y3, z3, u3, v3, nx3, ny3, nz3);
    }

    /**
     * Emits a 70% Minecraft-flat / 30% smooth-depth hybrid quad (default 84% crisp flat face normal + 16% smooth normal).
     */
    public void addHybridQuad(float x0, float y0, float z0, float u0, float v0, float sx0, float sy0, float sz0,
                              float x1, float y1, float z1, float u1, float v1, float sx1, float sy1, float sz1,
                              float x2, float y2, float z2, float u2, float v2, float sx2, float sy2, float sz2,
                              float x3, float y3, float z3, float u3, float v3, float sx3, float sy3, float sz3,
                              float flatWeight) {
        float ex = x1 - x0, ey = y1 - y0, ez = z1 - z0;
        float fx = x3 - x0, fy = y3 - y0, fz = z3 - z0;
        float fnx = ey * fz - ez * fy;
        float fny = ez * fx - ex * fz;
        float fnz = ex * fy - ey * fx;
        float nl = (float) Math.sqrt(fnx * fnx + fny * fny + fnz * fnz);
        if (nl < 1e-6f) {
            ex = x2 - x0; ey = y2 - y0; ez = z2 - z0;
            fx = x3 - x1; fy = y3 - y1; fz = z3 - z1;
            fnx = ey * fz - ez * fy;
            fny = ez * fx - ex * fz;
            fnz = ex * fy - ey * fx;
            nl = (float) Math.sqrt(fnx * fnx + fny * fny + fnz * fnz);
        }
        if (nl > 1e-6f) { fnx /= nl; fny /= nl; fnz /= nl; } else { fnx = 0; fny = 1; fnz = 0; }

        float sw = 1.0f - flatWeight;
        float[] n0 = blendNorm(fnx, fny, fnz, sx0, sy0, sz0, flatWeight, sw);
        float[] n1 = blendNorm(fnx, fny, fnz, sx1, sy1, sz1, flatWeight, sw);
        float[] n2 = blendNorm(fnx, fny, fnz, sx2, sy2, sz2, flatWeight, sw);
        float[] n3 = blendNorm(fnx, fny, fnz, sx3, sy3, sz3, flatWeight, sw);

        addQuad(x0, y0, z0, u0, v0, n0[0], n0[1], n0[2],
                x1, y1, z1, u1, v1, n1[0], n1[1], n1[2],
                x2, y2, z2, u2, v2, n2[0], n2[1], n2[2],
                x3, y3, z3, u3, v3, n3[0], n3[1], n3[2]);
    }

    private static float[] blendNorm(float fnx, float fny, float fnz, float snx, float sny, float snz, float fw, float sw) {
        float nx = fnx * fw + snx * sw;
        float ny = fny * fw + sny * sw;
        float nz = fnz * fw + snz * sw;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l > 1e-5f) { nx /= l; ny /= l; nz /= l; }
        return new float[] { nx, ny, nz };
    }

    public void addFlatQuad(float x0, float y0, float z0, float u0, float v0,
                            float x1, float y1, float z1, float u1, float v1,
                            float x2, float y2, float z2, float u2, float v2,
                            float x3, float y3, float z3, float u3, float v3) {
        addHybridQuad(x0, y0, z0, u0, v0, 0, 1, 0,
                      x1, y1, z1, u1, v1, 0, 1, 0,
                      x2, y2, z2, u2, v2, 0, 1, 0,
                      x3, y3, z3, u3, v3, 0, 1, 0, 1.0f);
    }

    public SculptedMesh build() {
        return new SculptedMesh(Arrays.copyOf(buf, len));
    }

    public record Ring(float cx, float cy, float cz, float rx, float ry, float dorsalKeel, float vCoord) {}

    /**
     * Pure crisp flat-shaded Minecraft voxel box — the core 70% Minecraft architectural building block!
     */
    public MeshBuilder addBox(float cx, float cy, float cz, float sx, float sy, float sz, UvRect uv) {
        return addBox(cx, cy, cz, sx, sy, sz, uv, uv, uv);
    }

    private static boolean isTilingMaterial(UvRect uv) {
        return uv == UvRect.SKIN || uv == UvRect.BELLY_ARMOR || uv == UvRect.METAL_GOLD;
    }

    private static float tileSpan(float meters) {
        return Math.max(0.28f, Math.min(1.0f, meters / 0.85f));
    }

    public MeshBuilder addBox(float cx, float cy, float cz, float sx, float sy, float sz,
                              UvRect topUv, UvRect sideUv, UvRect bottomUv) {
        float x0 = cx - sx * 0.5f, x1 = cx + sx * 0.5f;
        float y0 = cy - sy * 0.5f, y1 = cy + sy * 0.5f;
        float z0 = cz - sz * 0.5f, z1 = cz + sz * 0.5f;
        UvRect bot = (bottomUv != null) ? bottomUv : sideUv;

        float tu1 = isTilingMaterial(topUv) ? topUv.mapU(tileSpan(sx)) : topUv.u1();
        float tv1 = isTilingMaterial(topUv) ? topUv.mapV(tileSpan(sz)) : topUv.v1();
        float bu1 = isTilingMaterial(bot) ? bot.mapU(tileSpan(sx)) : bot.u1();
        float bv1 = isTilingMaterial(bot) ? bot.mapV(tileSpan(sz)) : bot.v1();
        float fsu1 = isTilingMaterial(sideUv) ? sideUv.mapU(tileSpan(sx)) : sideUv.u1();
        float fsv1 = isTilingMaterial(sideUv) ? sideUv.mapV(tileSpan(sy)) : sideUv.v1();
        float ssu1 = isTilingMaterial(sideUv) ? sideUv.mapU(tileSpan(sz)) : sideUv.u1();
        float ssv1 = isTilingMaterial(sideUv) ? sideUv.mapV(tileSpan(sy)) : sideUv.v1();

        addQuad(x0, y1, z0, topUv.u0(), topUv.v0(), 0, 1, 0,
                x0, y1, z1, topUv.u0(), tv1,        0, 1, 0,
                x1, y1, z1, tu1,        tv1,        0, 1, 0,
                x1, y1, z0, tu1,        topUv.v0(), 0, 1, 0);
        addQuad(x0, y0, z1, bot.u0(), bot.v0(), 0, -1, 0,
                x0, y0, z0, bot.u0(), bv1,      0, -1, 0,
                x1, y0, z0, bu1,      bv1,      0, -1, 0,
                x1, y0, z1, bu1,      bot.v0(), 0, -1, 0);
        addQuad(x0, y1, z1, sideUv.u0(), sideUv.v0(), 0, 0, 1,
                x0, y0, z1, sideUv.u0(), fsv1,        0, 0, 1,
                x1, y0, z1, fsu1,        fsv1,        0, 0, 1,
                x1, y1, z1, fsu1,        sideUv.v0(), 0, 0, 1);
        addQuad(x1, y1, z0, sideUv.u0(), sideUv.v0(), 0, 0, -1,
                x1, y0, z0, sideUv.u0(), fsv1,        0, 0, -1,
                x0, y0, z0, fsu1,        fsv1,        0, 0, -1,
                x0, y1, z0, fsu1,        sideUv.v0(), 0, 0, -1);
        addQuad(x1, y1, z1, sideUv.u0(), sideUv.v0(), 1, 0, 0,
                x1, y0, z1, sideUv.u0(), ssv1,        1, 0, 0,
                x1, y0, z0, ssu1,        ssv1,        1, 0, 0,
                x1, y1, z0, ssu1,        sideUv.v0(), 1, 0, 0);
        addQuad(x0, y1, z0, sideUv.u0(), sideUv.v0(), -1, 0, 0,
                x0, y0, z0, sideUv.u0(), ssv1,        -1, 0, 0,
                x0, y0, z1, ssu1,        ssv1,        -1, 0, 0,
                x0, y1, z1, ssu1,        sideUv.v0(), -1, 0, 0);
        return this;
    }

    /**
     * Crisp Stepped Minecraft Armor Plate (pure box core with clean slight bevel step on top/ends — 70% Minecraft box!).
     */
    public MeshBuilder addBeveledSlab(float cx, float cy, float cz, float sx, float sy, float sz, UvRect uv) {
        addBox(cx, cy, cz, sx, sy, sz, uv);
        return this;
    }

    /**
     * 70% Boxy Minecraft Volume with slight tapered front/back ends (4-sided crisp tapered box prism!).
     */
    public MeshBuilder addChamferedBox(float cx, float cy, float cz, float rx, float ry, float rz,
                                       UvRect topUv, UvRect bellyUv) {
        Ring[] rings = {
                new Ring(cx, cy, cz - rz,         rx * 0.72f, ry * 0.72f, 0f, 0.00f),
                new Ring(cx, cy, cz - rz * 0.65f, rx,         ry,         0f, 0.22f),
                new Ring(cx, cy, cz + rz * 0.65f, rx,         ry,         0f, 0.78f),
                new Ring(cx, cy, cz + rz,         rx * 0.72f, ry * 0.72f, 0f, 1.00f)
        };
        return addLoft(rings, 4, topUv, bellyUv, true, true);
    }

    public MeshBuilder addChamferedBox(float cx, float cy, float cz, float rx, float ry, float rz, UvRect uv) {
        return addChamferedBox(cx, cy, cz, rx, ry, rz, uv, null);
    }

    /**
     * 30% Realistic / 70% Minecraft-Boxy Multi-Ring Tapered Prism along any 3D curve!
     * - Default snaps to 4-sided AXIS-ALIGNED MINECRAFT BOX PRISM (flat top, flat bottom, flat left, flat right!)
     *   with subtle 8-sided option ONLY when sides >= 8 is explicitly passed for large armored torsos/coils!
     * - Uses 85% flat Minecraft face normal + 15% smooth depth normal for razor-crisp Minecraft readability!
     */
    public MeshBuilder addLoft(Ring[] rings, int sides, UvRect topUv, UvRect bellyUv, boolean capStart, boolean capEnd) {
        int nR = rings.length;
        if (nR < 2) return this;

        // 70% Minecraft boxy: almost everything uses crisp 4-sided square box prisms; only sides >= 8 uses crisp chamfered 8-box!
        int useSides = (sides >= 8) ? 8 : 4;
        float radBoost = (useSides == 4) ? 1.32f : 1.06f;
        float flatWeight = (useSides == 4) ? 0.90f : 0.80f;

        Vector3f[] right = new Vector3f[nR];
        Vector3f[] up = new Vector3f[nR];
        Vector3f[] tangents = new Vector3f[nR];
        Vector3f worldUp = new Vector3f(0f, 1f, 0f);
        Vector3f fallbackUp = new Vector3f(0f, 0f, 1f);

        for (int i = 0; i < nR; i++) {
            int p = Math.max(0, i - 1);
            int q = Math.min(nR - 1, i + 1);
            Vector3f tan = new Vector3f(rings[q].cx - rings[p].cx, rings[q].cy - rings[p].cy, rings[q].cz - rings[p].cz);
            if (tan.lengthSquared() < 1e-6f) tan.set(0f, 0f, 1f);
            tan.normalize();
            Vector3f ref = Math.abs(tan.dot(worldUp)) > 0.94f ? fallbackUp : worldUp;
            Vector3f rVec = new Vector3f(ref).cross(tan);
            if (rVec.lengthSquared() < 1e-6f) rVec.set(1f, 0f, 0f);
            rVec.normalize();
            Vector3f uVec = new Vector3f(tan).cross(rVec).normalize();
            tangents[i] = tan;
            right[i] = rVec;
            up[i] = uVec;
        }

        float[][][] pos = new float[nR][useSides + 1][3];
        float[][][] smoothN = new float[nR][useSides + 1][3];
        for (int i = 0; i < nR; i++) {
            Ring rg = rings[i];
            for (int j = 0; j <= useSides; j++) {
                // Phase offset by PI / useSides so TOP, BOTTOM, LEFT, RIGHT faces are FLAT Minecraft planes!
                double ang = (Math.PI * 2.0 * (j + 0.5)) / useSides;
                float ca = (float) Math.cos(ang);
                float sa = (float) Math.sin(ang);
                float keel = (useSides == 8 && sa > 0.60f && rg.dorsalKeel > 0f) ? rg.dorsalKeel * 0.55f : 0f;
                float lx = ca * rg.rx * radBoost;
                float ly = sa * rg.ry * radBoost + keel;
                pos[i][j][0] = rg.cx + right[i].x * lx + up[i].x * ly;
                pos[i][j][1] = rg.cy + right[i].y * lx + up[i].y * ly;
                pos[i][j][2] = rg.cz + right[i].z * lx + up[i].z * ly;
            }
        }

        for (int i = 0; i < nR; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(nR - 1, i + 1);
            for (int j = 0; j <= useSides; j++) {
                int jm = (j - 1 + useSides) % useSides;
                int jp = (j + 1) % useSides;
                float ax = pos[i1][j][0] - pos[i0][j][0], ay = pos[i1][j][1] - pos[i0][j][1], az = pos[i1][j][2] - pos[i0][j][2];
                float tx = pos[i][jp][0] - pos[i][jm][0], ty = pos[i][jp][1] - pos[i][jm][1], tz = pos[i][jp][2] - pos[i][jm][2];
                float nx = ty * az - tz * ay, ny = tz * ax - tx * az, nz = tx * ay - ty * ax;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-6f) {
                    nx = pos[i][j][0] - rings[i].cx;
                    ny = pos[i][j][1] - rings[i].cy;
                    nz = pos[i][j][2] - rings[i].cz;
                    nl = Math.max(1e-5f, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
                }
                smoothN[i][j][0] = nx / nl; smoothN[i][j][1] = ny / nl; smoothN[i][j][2] = nz / nl;
            }
        }

        // Measure total loft length so scales ALWAYS keep crisp 1:1 unstretched Minecraft pixel aspect ratio!
        float totalLen = 0f;
        for (int i = 0; i < nR - 1; i++) {
            float dx = rings[i + 1].cx - rings[i].cx;
            float dy = rings[i + 1].cy - rings[i].cy;
            float dz = rings[i + 1].cz - rings[i].cz;
            totalLen += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        float vScale = tileSpan(totalLen);

        for (int i = 0; i < nR - 1; i++) {
            float v0 = rings[i].vCoord;
            float v1 = rings[i + 1].vCoord;
            for (int j = 0; j < useSides; j++) {
                double midAng = (Math.PI * 2.0 * (j + 1.0)) / useSides;
                boolean isBelly = (bellyUv != null) && (Math.sin(midAng) < -0.52);
                UvRect uv = isBelly ? bellyUv : topUv;

                float u0, u1, mv0, mv1;
                if (useSides == 4 && isTilingMaterial(uv)) {
                    // Each flat face of a 4-sided Minecraft prism maps its own 1:1 unstretched square scale span!
                    float fx0 = pos[i][j + 1][0] - pos[i][j][0];
                    float fy0 = pos[i][j + 1][1] - pos[i][j][1];
                    float fz0 = pos[i][j + 1][2] - pos[i][j][2];
                    float fx1 = pos[i + 1][j + 1][0] - pos[i + 1][j][0];
                    float fy1 = pos[i + 1][j + 1][1] - pos[i + 1][j][1];
                    float fz1 = pos[i + 1][j + 1][2] - pos[i + 1][j][2];
                    float faceW = 0.5f * ((float) Math.sqrt(fx0 * fx0 + fy0 * fy0 + fz0 * fz0)
                            + (float) Math.sqrt(fx1 * fx1 + fy1 * fy1 + fz1 * fz1));
                    float uSpan = isBelly ? 1.0f : tileSpan(faceW);
                    float uPad = (1.0f - uSpan) * 0.5f;
                    u0 = uv.mapU(uPad);
                    u1 = uv.mapU(1.0f - uPad);
                    mv0 = uv.mapV(v0 * vScale);
                    mv1 = uv.mapV(v1 * vScale);
                } else {
                    u0 = uv.mapU((float) j / useSides);
                    u1 = uv.mapU((float) (j + 1) / useSides);
                    mv0 = uv.mapV(v0);
                    mv1 = uv.mapV(v1);
                }

                addHybridQuad(
                        pos[i][j][0],         pos[i][j][1],         pos[i][j][2],         u0, mv0, smoothN[i][j][0], smoothN[i][j][1], smoothN[i][j][2],
                        pos[i][j + 1][0],     pos[i][j + 1][1],     pos[i][j + 1][2],     u1, mv0, smoothN[i][j + 1][0], smoothN[i][j + 1][1], smoothN[i][j + 1][2],
                        pos[i + 1][j + 1][0], pos[i + 1][j + 1][1], pos[i + 1][j + 1][2], u1, mv1, smoothN[i + 1][j + 1][0], smoothN[i + 1][j + 1][1], smoothN[i + 1][j + 1][2],
                        pos[i + 1][j][0],     pos[i + 1][j][1],     pos[i + 1][j][2],     u0, mv1, smoothN[i + 1][j][0], smoothN[i + 1][j][1], smoothN[i + 1][j][2],
                        flatWeight
                );
            }
        }

        if (capStart) emitCap(rings[0], pos[0], useSides, topUv, tangents[0], false);
        if (capEnd) emitCap(rings[nR - 1], pos[nR - 1], useSides, topUv, tangents[nR - 1], true);
        return this;
    }

    private void emitCap(Ring rg, float[][] ringPos, int sides, UvRect uv, Vector3f tan, boolean end) {
        float nx = end ? tan.x : -tan.x;
        float ny = end ? tan.y : -tan.y;
        float nz = end ? tan.z : -tan.z;
        if (sides == 4) {
            float[] p0 = ringPos[0], p1 = ringPos[1], p2 = ringPos[2], p3 = ringPos[3];
            if (end) {
                addQuad(p0[0], p0[1], p0[2], uv.u0(), uv.v0(), nx, ny, nz,
                        p1[0], p1[1], p1[2], uv.u1(), uv.v0(), nx, ny, nz,
                        p2[0], p2[1], p2[2], uv.u1(), uv.v1(), nx, ny, nz,
                        p3[0], p3[1], p3[2], uv.u0(), uv.v1(), nx, ny, nz);
            } else {
                addQuad(p3[0], p3[1], p3[2], uv.u0(), uv.v1(), nx, ny, nz,
                        p2[0], p2[1], p2[2], uv.u1(), uv.v1(), nx, ny, nz,
                        p1[0], p1[1], p1[2], uv.u1(), uv.v0(), nx, ny, nz,
                        p0[0], p0[1], p0[2], uv.u0(), uv.v0(), nx, ny, nz);
            }
            return;
        }
        float cu = uv.mapU(0.5f), cv = uv.mapV(0.5f);
        for (int j = 0; j < sides; j++) {
            float[] p0 = ringPos[j];
            float[] p1 = ringPos[(j + 1) % sides];
            if (end) {
                addQuad(rg.cx, rg.cy, rg.cz, cu, cv, nx, ny, nz,
                        p0[0], p0[1], p0[2], uv.mapU(0.2f), uv.mapV(0.2f), nx, ny, nz,
                        p1[0], p1[1], p1[2], uv.mapU(0.8f), uv.mapV(0.2f), nx, ny, nz,
                        rg.cx, rg.cy, rg.cz, cu, cv, nx, ny, nz);
            } else {
                addQuad(rg.cx, rg.cy, rg.cz, cu, cv, nx, ny, nz,
                        p1[0], p1[1], p1[2], uv.mapU(0.8f), uv.mapV(0.2f), nx, ny, nz,
                        p0[0], p0[1], p0[2], uv.mapU(0.2f), uv.mapV(0.2f), nx, ny, nz,
                        rg.cx, rg.cy, rg.cz, cu, cv, nx, ny, nz);
            }
        }
    }

    public MeshBuilder addEllipsoid(float cx, float cy, float cz, float rx, float ry, float rz,
                                    int latSegs, int lonSegs, UvRect uv) {
        return addChamferedBox(cx, cy, cz, rx, ry, rz, uv, null);
    }

    /**
     * Crisp 4-Sided Flat-Shaded Curved Minecraft Horn / Fang / Talon / Dorsal Spine!
     */
    public MeshBuilder addCurvedSpike(float x0, float y0, float z0,
                                      float xc, float yc, float zc,
                                      float x1, float y1, float z1,
                                      float baseR, int steps, int sides, UvRect uv) {
        int useSteps = Math.max(2, Math.min(steps, 4));
        Ring[] rings = new Ring[useSteps + 1];
        for (int i = 0; i <= useSteps; i++) {
            float t = (float) i / useSteps;
            float omt = 1f - t;
            float px = omt * omt * x0 + 2f * omt * t * xc + t * t * x1;
            float py = omt * omt * y0 + 2f * omt * t * yc + t * t * y1;
            float pz = omt * omt * z0 + 2f * omt * t * zc + t * t * z1;
            float r = Math.max(0.014f, baseR * (1f - t * 0.84f));
            rings[i] = new Ring(px, py, pz, r, r, 0f, t);
        }
        return addLoft(rings, 4, uv, null, true, true);
    }

    /**
     * Crisp Flat-Shaded Angular Blockbench Wing / Feather / Cobra Hood / Gill Fin (70% Minecraft planes, 30% curved arch!).
     */
    public MeshBuilder addCurvedBlade(float width, float length, float archLift, float cupDepth,
                                      int segsU, int segsV, UvRect uv) {
        int uS = 2;
        int vS = Math.max(2, Math.min(segsV, 3));
        float[][][] p = new float[vS + 1][uS + 1][3];
        for (int iv = 0; iv <= vS; iv++) {
            float fv = (float) iv / vS;
            float archY = (float) Math.sin(fv * Math.PI * 0.85) * archLift - fv * fv * archLift * 0.35f;
            for (int iu = 0; iu <= uS; iu++) {
                float fu = (float) iu / uS;
                float cx = (fu - 0.5f) * 2f;
                float x = cx * (width * 0.5f);
                float cupY = -(cx * cx) * cupDepth * (float) Math.sin(fv * Math.PI);
                p[iv][iu][0] = x;
                p[iv][iu][1] = archY + cupY;
                p[iv][iu][2] = fv * length;
            }
        }
        for (int iv = 0; iv < vS; iv++) {
            float v0 = uv.mapV(1f - (float) iv / vS);
            float v1 = uv.mapV(1f - (float) (iv + 1) / vS);
            for (int iu = 0; iu < uS; iu++) {
                float u0 = uv.mapU((float) iu / uS);
                float u1 = uv.mapU((float) (iu + 1) / uS);
                addFlatQuad(
                        p[iv][iu][0],         p[iv][iu][1],         p[iv][iu][2],         u0, v0,
                        p[iv][iu + 1][0],     p[iv][iu + 1][1],     p[iv][iu + 1][2],     u1, v0,
                        p[iv + 1][iu + 1][0], p[iv + 1][iu + 1][1], p[iv + 1][iu + 1][2], u1, v1,
                        p[iv + 1][iu][0],     p[iv + 1][iu][1],     p[iv + 1][iu][2],     u0, v1
                );
            }
        }
        return this;
    }

    /**
     * Razor-sharp upright vertical dorsal fin standing cleanly in the YZ plane along a spine (ZERO sideways tilt!).
     */
    public MeshBuilder addVerticalDorsalFin(float xOff, float yBase, float zStart, float zLen,
                                            float height, float sweepBack, UvRect uv) {
        int segs = 3;
        for (int i = 0; i < segs; i++) {
            float u0 = (float) i / segs;
            float u1 = (float) (i + 1) / segs;
            float zb0 = zStart - u0 * zLen;
            float zb1 = zStart - u1 * zLen;
            float zt0 = zb0 - sweepBack;
            float zt1 = zb1 - sweepBack;
            float arch0 = (float) Math.sin(u0 * Math.PI) * 0.25f + 0.75f;
            float arch1 = (float) Math.sin(u1 * Math.PI) * 0.25f + 0.75f;
            float yt0 = yBase + height * arch0;
            float yt1 = yBase + height * arch1;
            float mu0 = uv.mapU(u0), mu1 = uv.mapU(u1);
            float mvBot = uv.mapV(1.0f), mvTop = uv.mapV(0.0f);
            addFlatQuad(
                    xOff, yBase, zb0, mu0, mvBot,
                    xOff, yBase, zb1, mu1, mvBot,
                    xOff, yt1,   zt1, mu1, mvTop,
                    xOff, yt0,   zt0, mu0, mvTop
            );
        }
        return this;
    }

    /**
     * 30% REALISTIC / 70% MINECRAFT BOXY MASTERPIECE SKULL (Ender Dragon x10 / Ice & Fire / Cataclysm style)!
     * - Layered crisp Minecraft square cranium block + crown crest block + cheek armor plates
     * - Strong 4-sided tapered Minecraft dragon/wolf/serpent snout with raised square nasal ridge & twin nostril blocks
     * - Heavy overhanging boxy brow slabs + recessed crisp rectangular glowing predator eyes (flush inside sockets!)
     * - Sharp 4-sided curved fangs + articulated 4-sided boxy lower jaw with chin block & forked tongue!
     */
    public static SculptedBone buildAnatomicalHead(
            String name, float ox, float oy, float oz, float scale,
            float snoutLen, float skullWidth, float skullHeight,
            UvRect skinUv, UvRect jawInsideUv, UvRect eyeRightUv, UvRect eyeLeftUv, UvRect thirdEyeUv,
            boolean glowingMaw, boolean hasForkedTongue) {

        SculptedBone head = new SculptedBone(name, ox, oy, oz);
        MeshBuilder base = new MeshBuilder();
        MeshBuilder glow = new MeshBuilder();

        float s = scale;
        float w = skullWidth * s;
        float h = skullHeight * s;
        float sl = snoutLen * s;

        // 1. Broad Square Layered Minecraft Cranium + Top Crest Block!
        base.addBox(0f, 0.16f * h, -0.14f * w, 1.72f * w, 1.24f * h, 1.18f * w, skinUv, skinUv, jawInsideUv);
        base.addBox(0f, 0.84f * h, -0.14f * w, 1.28f * w, 0.24f * h, 0.96f * w, skinUv);

        // 2. Strong 4-Sided Tapered Square Minecraft Snout (iconic Ender Dragon / Ice & Fire dragon muzzle!)
        Ring[] snoutRings = {
                new Ring(0f, 0.08f * h, 0.14f * sl, 0.72f * w, 0.50f * h, 0f, 0.00f),
                new Ring(0f, 0.04f * h, 0.58f * sl, 0.58f * w, 0.42f * h, 0f, 0.55f),
                new Ring(0f, 0.02f * h, 0.98f * sl, 0.48f * w, 0.34f * h, 0f, 1.00f)
        };
        base.addLoft(snoutRings, 4, skinUv, jawInsideUv, true, true);

        // Crisp Square Nasal Bridge + Iconic Twin Minecraft Dragon Nostril Blocks!
        base.addBox(0f, 0.44f * h, 0.52f * sl, 0.56f * w, 0.18f * h, 0.72f * sl, skinUv);
        base.addBox( 0.28f * w, 0.36f * h, 0.86f * sl, 0.24f * w, 0.22f * h, 0.24f * sl, skinUv);
        base.addBox(-0.28f * w, 0.36f * h, 0.86f * sl, 0.24f * w, 0.22f * h, 0.24f * sl, skinUv);

        // 3. Heavy Overhanging Square Brow Ridge Slabs + Cheek Armor Blocks + Swept 4-Sided Brow Horns!
        for (int side = -1; side <= 1; side += 2) {
            base.addBox(side * 0.74f * w, 0.60f * h, 0.18f * sl, 0.46f * w, 0.24f * h, 0.72f * w, skinUv);
            base.addBox(side * 0.88f * w, -0.02f * h, -0.18f * w, 0.28f * w, 0.40f * h, 0.74f * w, skinUv);
            base.addCurvedSpike(
                    side * 0.64f * w, 0.60f * h, 0.30f * sl,
                    side * 0.86f * w, 0.70f * h, -0.12f * w,
                    side * 0.94f * w, 0.60f * h, -0.64f * w,
                    0.14f * w, 3, 4, skinUv
            );
        }

        // 4. Crisp Recessed Rectangular Minecraft Glowing Eyes tucked flush under the brow blocks!
        float eyeX = 0.72f * w, eyeY = 0.30f * h, eyeZ = 0.24f * sl;
        float eW = 0.20f * w, eH = 0.24f * h, eD = 0.36f * w;
        base.addBox(-eyeX, eyeY, eyeZ, eW, eH, eD, eyeRightUv);
        glow.addBox(-eyeX * 1.03f, eyeY, eyeZ + 0.01f * sl, eW * 1.05f, eH * 1.05f, eD * 1.04f, eyeRightUv);
        base.addBox( eyeX, eyeY, eyeZ, eW, eH, eD, eyeLeftUv);
        glow.addBox( eyeX * 1.03f, eyeY, eyeZ + 0.01f * sl, eW * 1.05f, eH * 1.05f, eD * 1.04f, eyeLeftUv);

        // Optional Crisp Square 3rd Forehead Eye (Apep) or Sapphire Crown Gem (Tiamat)!
        if (thirdEyeUv != null) {
            float r3 = 0.30f * w;
            base.addBox(0f, 0.80f * h, 0.26f * sl, r3, r3 * 0.90f, r3 * 0.66f, thirdEyeUv);
            glow.addBox(0f, 0.80f * h, 0.28f * sl, r3 * 1.08f, r3 * 0.98f, r3 * 0.72f, thirdEyeUv);
        }

        // 5. 8 Sharp 4-Sided Curved Minecraft Upper Fangs!
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 4; f++) {
                float tf = f / 3.0f;
                float fz = (0.30f + tf * 0.60f) * sl;
                float fx = side * (0.58f - tf * 0.14f) * w;
                float fLen = (f == 3 ? 0.36f : (f == 2 ? 0.24f : 0.20f)) * h;
                float fRad = (f == 3 ? 0.082f : 0.058f) * w;
                base.addCurvedSpike(fx, -0.24f * h, fz, fx * 0.96f, -0.24f * h - fLen * 0.6f, fz - fLen * 0.10f,
                        fx * 0.92f, -0.24f * h - fLen, fz - fLen * 0.24f, fRad, 2, 4, UvRect.HORN_BONE);
            }
        }

        if (glowingMaw) {
            glow.addBox(0f, -0.20f * h, 0.38f * sl, 0.80f * w, 0.26f * h, 0.70f * sl, jawInsideUv);
        }

        head.baseMesh = base.build();
        head.glowMesh = glow.build();

        // 6. Articulated Crisp 4-Sided Boxy Lower Jaw + Square Chin Plate + Fangs + Forked Tongue!
        SculptedBone jaw = new SculptedBone(name + "_jaw", 0f, -0.24f * h, -0.04f * w, 0.30f, 0f, 0f);
        MeshBuilder jBase = new MeshBuilder();
        MeshBuilder jGlow = new MeshBuilder();
        Ring[] jawRings = {
                new Ring(0f, -0.06f * h, -0.06f * w, 0.64f * w, 0.24f * h, 0f, 0.00f),
                new Ring(0f, -0.09f * h,  0.42f * sl, 0.52f * w, 0.20f * h, 0f, 0.52f),
                new Ring(0f, -0.07f * h,  0.92f * sl, 0.40f * w, 0.16f * h, 0f, 1.00f)
        };
        jBase.addLoft(jawRings, 4, jawInsideUv, skinUv, true, true);
        jBase.addBox(0f, -0.22f * h, 0.72f * sl, 0.64f * w, 0.14f * h, 0.44f * sl, skinUv);

        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 3; f++) {
                float tf = f / 2.0f;
                float fz = (0.30f + tf * 0.54f) * sl;
                float fx = side * (0.46f - tf * 0.12f) * w;
                float fLen = (f == 2 ? 0.25f : 0.18f) * h;
                jBase.addCurvedSpike(fx, 0.06f * h, fz, fx, 0.06f * h + fLen * 0.6f, fz - fLen * 0.08f,
                        fx * 0.94f, 0.06f * h + fLen, fz - fLen * 0.18f, 0.055f * w, 2, 4, UvRect.HORN_BONE);
            }
        }
        if (hasForkedTongue) {
            jBase.addCurvedSpike(0f, 0.06f * h, 0.22f * sl, 0f, 0.09f * h, 0.66f * sl,
                    -0.12f * w, 0.03f * h, 0.98f * sl, 0.055f * w, 3, 4, UvRect.TONGUE);
            jBase.addCurvedSpike(0f, 0.06f * h, 0.22f * sl, 0f, 0.09f * h, 0.66f * sl,
                     0.12f * w, 0.03f * h, 0.98f * sl, 0.055f * w, 3, 4, UvRect.TONGUE);
        }
        if (glowingMaw) {
            jGlow.addBox(0f, 0.04f * h, 0.44f * sl, 0.66f * w, 0.14f * h, 0.64f * sl, jawInsideUv);
        }
        jaw.baseMesh = jBase.build();
        jaw.glowMesh = jGlow.build();
        head.addChild(jaw);
        return head;
    }
}
