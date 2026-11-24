package com.abyssalrpg.client.sculpt;

import com.abyssalrpg.client.sculpt.MeshBuilder.Ring;
import com.abyssalrpg.entity.mythic.ApepBossEntity;
import com.abyssalrpg.entity.mythic.FenrirBossEntity;
import com.abyssalrpg.entity.mythic.QuetzalcoatlBossEntity;
import com.abyssalrpg.entity.mythic.TiamatBossEntity;
import net.minecraft.util.Mth;

/**
 * 30% Realistic / 70% Minecraft-Boxy Rigs for ALL 7 Mythological Titan Bosses:
 * - Bulletproof telescoping ball-in-socket joints (ZERO broken joints or detached arms/legs/coils when moving!)
 * - TRUE LIVING SERPENTS (Apep, Jormungandr, Quetzalcoatl, Leviathan):
 *     * SLEEPING / RESTING (sleepBlend -> 1): Curls tightly into a continuous sleeping spiral coil on the ground
 *       with head tucked calmly onto its coils!
 *     * MOVING / CHASING (moveBlend -> 1): Uncoils smoothly and SLITHERS / CRAWLS across the ground in a fluid
 *       traveling sinusoidal serpent wave from head to tail tip!
 *     * COMBAT ALERT IDLE (sleepBlend = 0, moveBlend = 0): Coils rear body as anchor and rears head/hood high!
 */
public final class SculptedMythicRigs {
    private SculptedMythicRigs() {}

    /**
     * Telescoping ball-in-socket spine/neck/tail tube along Z — mathematically gap-free even at 75 deg bend!
     */
    private static MeshBuilder addTelescopingTubeZ(MeshBuilder mb, float stepLen, float rStart, float rEnd,
                                                   UvRect topUv, UvRect bellyUv, boolean isTip) {
        float sign = (stepLen >= 0f) ? 1f : -1f;
        float backOv = rStart * 0.72f;
        float fwdOv = isTip ? 0f : (rEnd * 0.46f);
        mb.addChamferedBox(0f, 0f, 0f, rStart * 1.08f, rStart * 0.98f, rStart * 1.08f, topUv, bellyUv);
        Ring[] rings = {
                new Ring(0, 0, -sign * backOv,         rStart * 0.96f, rStart * 0.88f, 0f, 0.00f),
                new Ring(0, 0, 0f,                     rStart,         rStart * 0.90f, 0f, 0.18f),
                new Ring(0, 0, stepLen * 0.52f,        Mth.lerp(0.52f, rStart, rEnd), Mth.lerp(0.52f, rStart, rEnd) * 0.90f, 0f, 0.58f),
                new Ring(0, 0, stepLen + sign * fwdOv, rEnd,           rEnd * 0.90f,   0f, 1.00f)
        };
        mb.addLoft(rings, 4, topUv, bellyUv, true, true);
        return mb;
    }

    /**
     * Realistic anatomical sculpted werewolf/titan limb helper + articulated 4-toe clawed paw builder.
     */
    private static SculptedBone[][] buildArticulatedWolfToes(SculptedBone paw, String prefix, float sc, UvRect skinUv) {
        MeshBuilder pm = new MeshBuilder();
        pm.addChamferedBox(0, 0.02f * sc, 0, 0.14f * sc, 0.12f * sc, 0.14f * sc, skinUv);
        pm.addLoft(new Ring[] {
                new Ring(0, -0.02f * sc, -0.04f * sc, 0.13f * sc, 0.09f * sc, 0, 0.00f),
                new Ring(0, -0.06f * sc,  0.12f * sc, 0.17f * sc, 0.08f * sc, 0, 0.60f),
                new Ring(0, -0.075f * sc, 0.22f * sc, 0.16f * sc, 0.07f * sc, 0, 1.00f)
        }, 4, skinUv, null, true, true);
        paw.baseMesh = pm.build();

        SculptedBone[][] toes = new SculptedBone[4][2];
        float[] tx   = { -0.11f, 0.00f,  0.11f };
        float[] tz   = {  0.18f, 0.21f,  0.18f };
        float[] ty   = { -0.25f, 0.00f,  0.25f };
        float[] pLen = {  0.15f, 0.18f,  0.15f };
        float[] cLen = {  0.17f, 0.20f,  0.17f };
        float[] rad  = {  0.040f, 0.044f, 0.040f };

        for (int i = 0; i < 3; i++) {
            float l1 = pLen[i] * sc, l2 = cLen[i] * sc, r = rad[i] * sc;
            SculptedBone prox = paw.addChild(new SculptedBone(prefix + "_t" + i + "p",
                    tx[i] * sc, -0.075f * sc, tz[i] * sc, 0.08f, ty[i], 0f));
            MeshBuilder b1 = new MeshBuilder();
            b1.addChamferedBox(0, 0, 0, r * 1.15f, r * 1.08f, r * 1.15f, skinUv);
            b1.addLoft(new Ring[] {
                    new Ring(0, 0, -r * 0.65f, r, r * 0.90f, 0, 0f),
                    new Ring(0, -0.01f * sc, l1 + r * 0.35f, r * 0.78f, r * 0.74f, 0, 1f)
            }, 4, skinUv, null, true, true);
            prox.baseMesh = b1.build();

            SculptedBone dist = prox.addChild(new SculptedBone(prefix + "_t" + i + "d", 0, -0.01f * sc, l1, 0.14f, 0, 0));
            MeshBuilder b2 = new MeshBuilder();
            b2.addChamferedBox(0, 0, 0, r * 0.85f, r * 0.80f, r * 0.85f, skinUv);
            b2.addCurvedSpike(0, 0, -r * 0.35f, 0, -0.02f * sc, l2 * 0.55f, 0, -0.09f * sc, l2, r * 0.82f, 3, 4, UvRect.HORN_BONE);
            dist.baseMesh = b2.build();
            toes[i][0] = prox; toes[i][1] = dist;
        }
        // Rear dewclaw talon
        float rl1 = 0.11f * sc, rl2 = 0.13f * sc, rr = 0.034f * sc;
        SculptedBone rProx = paw.addChild(new SculptedBone(prefix + "_t3p", 0f, -0.05f * sc, -0.08f * sc, 0.16f, Mth.PI, 0f));
        MeshBuilder rb1 = new MeshBuilder();
        rb1.addChamferedBox(0, 0, 0, rr * 1.15f, rr * 1.05f, rr * 1.15f, skinUv);
        rb1.addLoft(new Ring[] {
                new Ring(0, 0, -rr * 0.6f, rr, rr * 0.90f, 0, 0f),
                new Ring(0, -0.01f * sc, rl1 + rr * 0.3f, rr * 0.76f, rr * 0.72f, 0, 1f)
        }, 4, skinUv, null, true, true);
        rProx.baseMesh = rb1.build();
        SculptedBone rDist = rProx.addChild(new SculptedBone(prefix + "_t3d", 0, -0.01f * sc, rl1, 0.22f, 0, 0));
        rDist.baseMesh = new MeshBuilder().addCurvedSpike(0, 0, -rr * 0.3f, 0, -0.02f * sc, rl2 * 0.55f, 0, -0.08f * sc, rl2, rr * 0.80f, 3, 4, UvRect.HORN_BONE).build();
        toes[3][0] = rProx; toes[3][1] = rDist;
        return toes;
    }

    private static void animateWolfToes(SculptedBone[][] toes, float phase, float mv, float age, float seed) {
        float lift = Math.max(0f, Mth.sin(phase));
        float liftE = lift * lift * (3f - 2f * lift);
        float grip = Math.max(0f, -Mth.sin(phase)) * mv;
        float[] baseY = { -0.25f, 0.0f, 0.25f, Mth.PI };
        for (int i = 0; i < 4; i++) {
            float wave = Mth.sin(age * 0.14f + seed + i * 0.65f) * 0.06f;
            float curl = 0.04f + liftE * 0.54f * mv - grip * 0.08f + wave;
            toes[i][0].xRot = 0.06f + curl * 0.52f;
            toes[i][1].xRot = 0.12f + curl * 0.72f;
            if (i < 3) {
                toes[i][0].yRot = baseY[i] * (1.0f + grip * 0.26f - liftE * 0.22f * mv);
            }
        }
    }

    /**
     * Builds 5 individually articulated dual-joint predatory fingers (1 opposable thumb + 4 splayed clawed digits)
     * mounted onto a sculpted palm bone. Returns [5][2]: [i][0]=proximal knuckle bone, [i][1]=distal claw bone!
     */
    private static SculptedBone[][] buildArticulatedFingers(SculptedBone hand, String prefix, int side, float sc, UvRect skinUv) {
        SculptedBone[][] f = new SculptedBone[5][2];

        // Finger 0: Opposable inner thumb (angled inward & forward)
        SculptedBone tProx = hand.addChild(new SculptedBone(prefix + "_f0p",
                -side * 0.14f * sc, -0.09f * sc, 0.11f * sc, 0.22f, -side * 0.68f, -side * 0.18f));
        MeshBuilder tpm = new MeshBuilder();
        tpm.addChamferedBox(0, 0, 0, 0.056f * sc, 0.052f * sc, 0.056f * sc, skinUv);
        tpm.addLoft(new Ring[] {
                new Ring(0, 0, -0.03f * sc, 0.048f * sc, 0.044f * sc, 0, 0f),
                new Ring(0, -0.02f * sc, 0.16f * sc, 0.036f * sc, 0.034f * sc, 0, 1f)
        }, 4, skinUv, null, true, true);
        tProx.baseMesh = tpm.build();
        SculptedBone tDist = tProx.addChild(new SculptedBone(prefix + "_f0d", 0, -0.02f * sc, 0.15f * sc, 0.34f, 0, 0));
        MeshBuilder tdm = new MeshBuilder();
        tdm.addChamferedBox(0, 0, 0, 0.040f * sc, 0.038f * sc, 0.040f * sc, skinUv);
        tdm.addCurvedSpike(0, 0, -0.02f * sc, 0, -0.04f * sc, 0.12f * sc, 0, -0.14f * sc, 0.21f * sc, 0.036f * sc, 3, 4, UvRect.HORN_BONE);
        tDist.baseMesh = tdm.build();
        f[0][0] = tProx; f[0][1] = tDist;

        // Fingers 1..4: Index, Middle, Ring, Pinky — naturally arched knuckle bridge & splayed predatory fan!
        float[] kx  = { -0.115f, -0.038f,  0.042f,  0.118f };
        float[] kz  = {  0.22f,   0.25f,   0.24f,   0.20f  };
        float[] ky  = { -0.22f,  -0.06f,   0.10f,   0.26f  };
        float[] pLen = { 0.21f,   0.25f,   0.23f,   0.18f  };
        float[] cLen = { 0.25f,   0.30f,   0.27f,   0.21f  };
        float[] rad  = { 0.042f,  0.045f,  0.042f,  0.036f };

        for (int i = 0; i < 4; i++) {
            float l1 = pLen[i] * sc, l2 = cLen[i] * sc, r = rad[i] * sc;
            SculptedBone prox = hand.addChild(new SculptedBone(prefix + "_f" + (i + 1) + "p",
                    side * kx[i] * sc, -0.08f * sc, kz[i] * sc, 0.24f, side * ky[i], 0f));
            MeshBuilder pm = new MeshBuilder();
            pm.addChamferedBox(0, 0, 0, r * 1.18f, r * 1.12f, r * 1.18f, skinUv);
            pm.addLoft(new Ring[] {
                    new Ring(0, 0, -r * 0.7f, r, r * 0.92f, 0, 0f),
                    new Ring(0, -0.02f * sc, l1 * 0.55f, r * 0.88f, r * 0.82f, 0, 0.55f),
                    new Ring(0, -0.05f * sc, l1 + r * 0.4f, r * 0.76f, r * 0.72f, 0, 1f)
            }, 4, skinUv, null, true, true);
            prox.baseMesh = pm.build();

            SculptedBone dist = prox.addChild(new SculptedBone(prefix + "_f" + (i + 1) + "d",
                    0, -0.05f * sc, l1, 0.36f, 0, 0));
            MeshBuilder dm = new MeshBuilder();
            dm.addChamferedBox(0, 0, 0, r * 0.86f, r * 0.82f, r * 0.86f, skinUv);
            dm.addCurvedSpike(0, 0, -r * 0.4f, 0, -0.04f * sc, l2 * 0.55f, 0, -0.15f * sc, l2, r * 0.80f, 3, 4, UvRect.HORN_BONE);
            dist.baseMesh = dm.build();

            f[i + 1][0] = prox;
            f[i + 1][1] = dist;
        }
        return f;
    }

    /**
     * Animates all 5 dual-joint fingers with organic staggered flex waves, dynamic splay, slash clench & sleep curl!
     */
    private static void animateArticulatedFingers(SculptedBone[][] f, int side, float age, float handPhase,
                                                  float curl, float splay) {
        float[] baseY = { -0.68f, -0.22f, -0.06f, 0.10f, 0.26f };
        for (int i = 0; i < 5; i++) {
            float wave = Mth.sin(age * 0.15f + handPhase + i * 0.58f) * 0.12f;
            float flex = Mth.clamp(curl + wave * (1f - Math.abs(curl) * 0.4f), -0.35f, 1.15f);
            // Positive xRot curls +Z fingers downward/inward into a tight predatory fist/rake!
            f[i][0].xRot = 0.22f + flex * 0.58f;
            f[i][1].xRot = 0.34f + flex * 0.76f;
            float spreadScale = 1.0f + splay * 0.45f;
            f[i][0].yRot = side * baseY[i] * spreadScale;
        }
    }

    // =========================================================================
    // 1. QUETZALCOATL — Coils Tightly When Sleeping, Uncoils & Slithers/Glides When Moving!
    // =========================================================================
    public static SculptedBossRig<QuetzalcoatlBossEntity> createQuetzalcoatl() {
        SculptedBone root = new SculptedBone("root", 0, 0, 0);
        SculptedBone body = root.addChild(new SculptedBone("body", 0, 1.40f, 0.15f));
        MeshBuilder bodyM = new MeshBuilder();
        bodyM.addChamferedBox(0, 0.04f, 0.18f, 0.31f, 0.28f, 0.34f, UvRect.SKIN, UvRect.BELLY_ARMOR);
        bodyM.addBox(0, 0.30f, 0.18f, 0.28f, 0.10f, 0.28f, UvRect.METAL_GOLD);
        body.baseMesh = bodyM.build();

        SculptedBone neck = body.addChild(new SculptedBone("neck", 0, 0.06f, 0.28f, -0.24f, 0, 0));
        neck.baseMesh = addTelescopingTubeZ(new MeshBuilder(), 0.62f, 0.26f, 0.22f, UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();

        SculptedBone head = neck.addChild(MeshBuilder.buildAnatomicalHead(
                "head", 0, 0.04f, 0.60f, 1.04f, 0.54f, 0.34f, 0.30f,
                UvRect.SKIN, UvRect.MAW_DARK, UvRect.EYE_MAIN, UvRect.EYE_MAIN, null, false, true));
        head.xRot = 0.22f;
        SculptedBone jaw = head.getChild("head_jaw");

        MeshBuilder crownB = new MeshBuilder(), crownG = new MeshBuilder();
        crownB.addBox(0, 0.32f, 0.02f, 0.48f, 0.14f, 0.36f, UvRect.METAL_GOLD);
        crownB.addBox(0, 0.46f, 0.00f, 0.34f, 0.14f, 0.26f, UvRect.METAL_GOLD);
        crownB.addBox(0, 0.58f, -0.02f, 0.20f, 0.14f, 0.18f, UvRect.METAL_GOLD);
        crownB.addBox(0, 0.40f, 0.18f, 0.14f, 0.16f, 0.08f, UvRect.EYE_MAIN);
        crownG.addBox(0, 0.40f, 0.19f, 0.15f, 0.17f, 0.09f, UvRect.EYE_MAIN);
        crownB.addBox( 0.36f, 0.04f, -0.02f, 0.12f, 0.32f, 0.32f, UvRect.METAL_GOLD);
        crownB.addBox(-0.36f, 0.04f, -0.02f, 0.12f, 0.32f, 0.32f, UvRect.METAL_GOLD);
        SculptedBone crown = head.addChild(new SculptedBone("crown", 0, 0, 0));
        crown.baseMesh = crownB.build();
        crown.glowMesh = crownG.build();

        SculptedBone beard = jaw.addChild(new SculptedBone("beard", 0, -0.08f, 0.30f, 1.25f, 0, 0));
        beard.baseMesh = new MeshBuilder().addCurvedBlade(0.32f, 0.78f, 0.16f, 0.05f, 2, 3, UvRect.FEATHER_2).build();

        UvRect[] fUvs = { UvRect.FEATHER_0, UvRect.FEATHER_1, UvRect.FEATHER_2, UvRect.FEATHER_3 };
        SculptedBone[] plumes = new SculptedBone[13];
        for (int i = 0; i < 13; i++) {
            float t = (i / 12.0f) * 2.0f - 1.0f;
            float fanZ = t * 2.15f;
            float pLen = 1.15f + (1.0f - Math.abs(t)) * 0.45f;
            SculptedBone pl = head.addChild(new SculptedBone("plume" + i, 0, 0.06f, -0.08f, -1.82f, 0, fanZ));
            SculptedMesh fm = new MeshBuilder().addCurvedBlade(0.34f, pLen, 0.22f, 0.04f, 2, 3, fUvs[i % 4]).build();
            pl.baseMesh = fm; pl.glowMesh = fm;
            plumes[i] = pl;
        }

        // 10-Segment Unified Articulated Sky-Serpent Body (curls into sleeping coil or slithers in full S-wave!)
        SculptedBone[] segs = new SculptedBone[10];
        SculptedBone cur = body;
        float[] rad = { 0.29f, 0.27f, 0.25f, 0.23f, 0.21f, 0.18f, 0.15f, 0.12f, 0.10f, 0.08f };
        for (int i = 0; i < 10; i++) {
            SculptedBone seg = cur.addChild(new SculptedBone("seg" + i, 0, 0, i == 0 ? 0.04f : -0.54f));
            float r0 = rad[i], r1 = (i < 9) ? rad[i + 1] : 0.04f;
            MeshBuilder sm = addTelescopingTubeZ(new MeshBuilder(), -0.54f, r0, r1, UvRect.SKIN, UvRect.BELLY_ARMOR, i == 9);
            if (i % 2 == 0) sm.addBox(0, r0 * 0.98f, -0.28f, r0 * 0.94f, 0.09f, 0.22f, UvRect.METAL_GOLD);
            seg.baseMesh = sm.build();
            SculptedBone df = seg.addChild(new SculptedBone("df" + i, 0, r0 * 0.85f, -0.14f, -0.45f, 0, Mth.HALF_PI));
            df.baseMesh = new MeshBuilder().addCurvedBlade(0.34f, 0.62f, 0.14f, 0.03f, 2, 3, (i % 2 == 0) ? UvRect.FEATHER_2 : UvRect.FEATHER_3).build();
            segs[i] = seg;
            cur = seg;
        }
        SculptedBone tfan = cur.addChild(new SculptedBone("tfan", 0, 0, -0.54f, 0, Mth.PI, 0));
        tfan.baseMesh = new MeshBuilder().addCurvedBlade(0.65f, 0.90f, 0.12f, 0.04f, 2, 3, UvRect.FEATHER_1).build();

        return new SculptedBossRig<>() {
            @Override public SculptedBone root() { return root; }
            @Override public void animate(QuetzalcoatlBossEntity e, float ls, float lsa, float age, float ny, float hp, BossAnimState st) {
                float sleep = BossAnimState.ease(st.sleepBlend);
                float mv = BossAnimState.ease(st.moveBlend) * (1f - sleep);
                float cast = BossAnimState.ease(st.castBlend);
                float turn = st.turnRate * (1f - sleep);
                float swimPhase = age * 0.14f + st.gaitPhase * 1.15f;

                // Graceful 3D celestial sky-swimming: lateral S-glide, altitude wave, banking into turns & dive/climb pitch!
                float awakeY = 1.40f + Mth.sin(swimPhase) * (0.22f + mv * 0.14f) + cast * 0.24f;
                body.x = Mth.lerp(sleep, Mth.sin(swimPhase * 0.9f) * (0.12f + mv * 0.22f), 0f);
                body.y = Mth.lerp(sleep, awakeY, 0.30f + Mth.sin(age * 0.05f) * 0.02f);
                body.yRot = Mth.lerp(sleep, Mth.cos(swimPhase * 0.9f) * (0.10f + mv * 0.14f), 0f);
                body.zRot = Mth.lerp(sleep, Mth.cos(swimPhase * 0.85f) * (0.14f + mv * 0.12f) + turn * 0.22f, 0f);

                float yaw = -ny * Mth.DEG_TO_RAD * (1f - sleep);
                // Head & neck counter-steer smoothly to keep piercing gaze locked forward while body swims in S-waves!
                neck.yRot = Mth.lerp(sleep, yaw * 0.45f + turn * 0.25f - Mth.cos(swimPhase * 0.9f) * (0.08f + mv * 0.12f), -0.58f);
                neck.xRot = Mth.lerp(sleep, -0.24f - cast * 0.18f - Mth.cos(swimPhase) * (0.08f + mv * 0.06f), -0.18f);
                head.yRot = Mth.lerp(sleep, yaw * 0.45f + turn * 0.15f, -0.44f);
                head.xRot = Mth.lerp(sleep, 0.22f + hp * Mth.DEG_TO_RAD * 0.45f + Mth.cos(swimPhase) * 0.05f, 0.22f);
                jaw.xRot = Mth.lerp(sleep, Mth.lerp(cast, 0.24f + Math.abs(Mth.sin(swimPhase)) * 0.16f, 0.82f + Mth.sin(age * 0.5f) * 0.14f), 0.04f);

                for (int i = 0; i < 13; i++) {
                    float awakePl = -1.82f + cast * 0.20f - mv * 0.14f + Mth.sin(swimPhase * 1.35f + i * 0.42f) * (0.09f + mv * 0.08f + cast * 0.05f);
                    plumes[i].xRot = Mth.lerp(sleep, awakePl, -1.25f);
                }
                for (int i = 0; i < 10; i++) {
                    float amp = 0.24f + mv * 0.18f + i * 0.015f;
                    float slitherY = -turn * (0.16f + i * 0.025f) + Mth.sin(swimPhase - i * 0.62f) * amp;
                    float slitherX = Mth.cos(swimPhase * 0.92f - i * 0.56f) * (0.14f + mv * 0.10f);
                    float sleepCoilY = 0.54f + i * 0.015f;
                    segs[i].yRot = Mth.lerp(sleep, slitherY, sleepCoilY);
                    segs[i].xRot = Mth.lerp(sleep, slitherX, 0f);
                }
            }
        };
    }

    // =========================================================================
    // 2. APEP — 100% UNIFIED ARTICULATED COBRA: COILS TO SLEEP / UNCOILS & SLITHERS WHEN MOVING!
    // =========================================================================
    public static SculptedBossRig<ApepBossEntity> createApep() {
        SculptedBone root = new SculptedBone("root", 0, 0, 0);
        // Central pivot resting right on the ground (y=0.30 -> belly touches Y=0.0!)
        SculptedBone body = root.addChild(new SculptedBone("body", 0, 0.30f, 0.0f));
        body.baseMesh = new MeshBuilder().addChamferedBox(0, 0, 0, 0.34f, 0.30f, 0.34f, UvRect.SKIN, UvRect.BELLY_ARMOR).build();

        // 4-Segment Articulated Front Cobra Neck (rears high in alert/combat, lowers to slither, curls onto coils to sleep!)
        SculptedBone n1 = body.addChild(new SculptedBone("n1", 0f, 0.04f, 0.18f, -1.08f, 0, 0));
        n1.baseMesh = addTelescopingTubeZ(new MeshBuilder(), 0.72f, 0.32f, 0.29f, UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();
        SculptedBone n2 = n1.addChild(new SculptedBone("n2", 0, 0, 0.72f, -0.06f, 0, 0));
        n2.baseMesh = addTelescopingTubeZ(new MeshBuilder(), 0.68f, 0.29f, 0.27f, UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();
        SculptedBone n3 = n2.addChild(new SculptedBone("n3", 0, 0, 0.68f, 0.50f, 0, 0));
        n3.baseMesh = addTelescopingTubeZ(new MeshBuilder(), 0.64f, 0.27f, 0.25f, UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();
        SculptedBone n4 = n3.addChild(new SculptedBone("n4", 0, 0, 0.64f, 0.62f, 0, 0));
        n4.baseMesh = addTelescopingTubeZ(new MeshBuilder(), 0.52f, 0.25f, 0.24f, UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();

        SculptedBone hoodL = n4.addChild(new SculptedBone("hoodL", 0.14f, -0.02f, 0.28f, -1.70f, 0.22f, 1.38f));
        hoodL.baseMesh = new MeshBuilder().addCurvedBlade(1.18f, 1.08f, 0.22f, 0.18f, 2, 3, UvRect.MEMBRANE_FULL).build();
        SculptedBone hoodR = n4.addChild(new SculptedBone("hoodR", -0.14f, -0.02f, 0.28f, -1.70f, -0.22f, -1.38f));
        hoodR.baseMesh = new MeshBuilder().addCurvedBlade(1.18f, 1.08f, 0.22f, 0.18f, 2, 3, UvRect.MEMBRANE_FULL).build();
        SculptedBone hoodL2 = n3.addChild(new SculptedBone("hoodL2", 0.16f, 0.0f, 0.40f, -1.65f, 0.18f, 1.50f));
        hoodL2.baseMesh = new MeshBuilder().addCurvedBlade(0.95f, 0.85f, 0.18f, 0.14f, 2, 3, UvRect.MEMBRANE_FULL).build();
        SculptedBone hoodR2 = n3.addChild(new SculptedBone("hoodR2", -0.16f, 0.0f, 0.40f, -1.65f, -0.18f, -1.50f));
        hoodR2.baseMesh = new MeshBuilder().addCurvedBlade(0.95f, 0.85f, 0.18f, 0.14f, 2, 3, UvRect.MEMBRANE_FULL).build();

        SculptedBone head = n4.addChild(MeshBuilder.buildAnatomicalHead(
                "head", 0, 0.04f, 0.50f, 1.10f, 0.56f, 0.38f, 0.30f,
                UvRect.SKIN, UvRect.MAW_DARK, UvRect.EYE_ALT1, UvRect.EYE_MAIN, UvRect.EYE_ALT2, false, true));
        head.xRot = 0.20f;
        SculptedBone jaw = head.getChild("head_jaw");

        MeshBuilder hornM = new MeshBuilder();
        for (int side = -1; side <= 1; side += 2) {
            hornM.addCurvedSpike(side * 0.26f, 0.26f, 0.08f, side * 0.52f, 0.36f, -0.35f, side * 0.68f, 0.16f, -0.78f, 0.08f, 4, 4, UvRect.HORN_BONE);
        }
        head.addChild(new SculptedBone("horns", 0, 0, 0)).baseMesh = hornM.build();

        // 11-SEGMENT UNIFIED ARTICULATED SERPENT BODY & TAIL (ZERO frozen donut coil — coils & slithers live!)
        SculptedBone[] segs = new SculptedBone[11];
        float[] rad = { 0.33f, 0.32f, 0.31f, 0.29f, 0.27f, 0.25f, 0.22f, 0.19f, 0.15f, 0.11f, 0.08f };
        SculptedBone cur = body;
        for (int i = 0; i < 11; i++) {
            float step = 0.58f;
            // Default static pose = graceful defensive coil so previews look iconic too!
            float defY = (i < 6) ? 0.52f : (i < 8 ? 0.42f : -0.25f);
            SculptedBone seg = cur.addChild(new SculptedBone("seg" + i, 0f, 0f, i == 0 ? -0.18f : -step, 0, defY, 0));
            float r0 = rad[i], r1 = (i < 10) ? rad[i + 1] : 0.03f;
            seg.baseMesh = addTelescopingTubeZ(new MeshBuilder(), -step, r0, r1, UvRect.SKIN, UvRect.BELLY_ARMOR, i == 10).build();
            segs[i] = seg;
            cur = seg;
        }

        return new SculptedBossRig<>() {
            @Override public SculptedBone root() { return root; }
            @Override public void animate(ApepBossEntity e, float ls, float lsa, float age, float ny, float hp, BossAnimState st) {
                float sleep = BossAnimState.ease(st.sleepBlend);
                float mv = BossAnimState.ease(st.moveBlend) * (1f - sleep);
                float cast = BossAnimState.ease(st.castBlend);
                float sl = st.slashCurve();
                float turn = st.turnRate * (1f - sleep);
                float t = age * 0.11f;
                float yaw = -ny * Mth.DEG_TO_RAD * (1f - sleep);
                float slitherPhase = st.gaitPhase * 1.55f;

                // True serpentine lateral S-propulsion on root body while slithering + subtle body roll!
                body.x = Mth.sin(slitherPhase) * 0.28f * mv;
                body.yRot = Mth.cos(slitherPhase) * 0.24f * mv;
                body.zRot = Mth.sin(slitherPhase) * 0.08f * mv;

                float n1Alert = -1.08f - cast * 0.18f + sl * 0.28f + Mth.cos(t) * 0.06f;
                float n2Alert = -0.06f + cast * 0.06f + sl * 0.12f;
                float n3Alert =  0.50f;
                float n4Alert =  0.62f;

                // Sleek raised-hood cobra hunt crawl (glides forward with head weaving & locking onto prey!)
                float n1Crawl = -0.46f + Mth.sin(slitherPhase * 2f) * 0.05f;
                float n2Crawl =  0.14f;
                float n3Crawl =  0.18f;
                float n4Crawl =  0.14f;

                float n1Sleep = -0.44f + Mth.sin(age * 0.05f) * 0.03f;
                float n2Sleep =  0.26f;
                float n3Sleep =  0.14f;
                float n4Sleep =  0.08f;

                n1.xRot = Mth.lerp(sleep, Mth.lerp(mv, n1Alert, n1Crawl), n1Sleep);
                n2.xRot = Mth.lerp(sleep, Mth.lerp(mv, n2Alert, n2Crawl), n2Sleep);
                n3.xRot = Mth.lerp(sleep, Mth.lerp(mv, n3Alert, n3Crawl), n3Sleep);
                n4.xRot = Mth.lerp(sleep, Mth.lerp(mv, n4Alert, n4Crawl), n4Sleep);

                // Neck counter-steers against root S-yaw so cobra head tracks smoothly forward like a real king cobra!
                float counterSteer = -Mth.cos(slitherPhase) * 0.22f * mv;
                n1.yRot = Mth.lerp(sleep, yaw * 0.25f + turn * 0.22f + Mth.sin(t) * 0.14f * (1f - mv) + counterSteer * 0.55f, -0.58f);
                n2.yRot = Mth.lerp(sleep, yaw * 0.25f + turn * 0.18f + Mth.sin(t - 0.7f) * 0.16f * (1f - mv) + counterSteer * 0.45f, -0.64f);
                n3.yRot = Mth.lerp(sleep, yaw * 0.25f + turn * 0.12f + Mth.sin(t - 1.4f) * 0.14f * (1f - mv) - counterSteer * 0.25f, -0.62f);
                n4.yRot = Mth.lerp(sleep, yaw * 0.25f, -0.48f);

                head.xRot = Mth.lerp(sleep, Mth.lerp(mv, 0.20f, 0.02f) + hp * Mth.DEG_TO_RAD * 0.42f - sl * 0.16f, 0.14f);
                jaw.xRot  = Mth.lerp(sleep, Mth.lerp(cast, 0.28f + Math.abs(Mth.sin(t)) * 0.14f + Math.max(0f, sl) * 0.48f, 0.86f + Mth.sin(age * 0.5f) * 0.12f), 0.04f);

                float hoodOpen = Mth.lerp(sleep, Mth.lerp(mv, 1.38f + cast * 0.24f + Mth.sin(t * 1.4f) * 0.06f, 1.18f + Mth.sin(slitherPhase) * 0.05f), 0.82f);
                hoodL.zRot =  hoodOpen; hoodR.zRot = -hoodOpen;
                hoodL2.zRot = hoodOpen + 0.10f; hoodR2.zRot = -(hoodOpen + 0.10f);

                for (int i = 0; i < 11; i++) {
                    float alertCoil = ((i < 6) ? 0.50f : (i < 8 ? 0.40f : -0.22f)) + Mth.sin(t - i * 0.55f) * 0.045f;
                    float slitherWave = -turn * (0.14f + i * 0.02f) + Mth.sin(slitherPhase - i * 0.58f) * (0.36f + i * 0.018f);
                    float sleepCoil = 0.52f + i * 0.018f;
                    segs[i].yRot = Mth.lerp(sleep, Mth.lerp(mv, alertCoil, slitherWave), sleepCoil);
                    segs[i].xRot = (i < 4) ? (sleep * Mth.sin(age * 0.05f - i * 0.3f) * 0.018f) : 0f;
                }
            }
        };
    }

    // =========================================================================
    // 3. FENRIR — Deeply Locked Shoulders/Hips + Bulletproof 3-Joint Arms & Legs!
    // =========================================================================
    public static SculptedBossRig<FenrirBossEntity> createFenrir() {
        SculptedBone root = new SculptedBone("root", 0, 0, 0);
        SculptedBone pelvis = root.addChild(new SculptedBone("pelvis", 0, 0.88f, -0.04f));
        pelvis.baseMesh = new MeshBuilder().addChamferedBox(0, 0.06f, 0, 0.50f, 0.28f, 0.38f, UvRect.SKIN, UvRect.BELLY_ARMOR).build();

        SculptedBone body = pelvis.addChild(new SculptedBone("body", 0, 0.16f, 0.04f, 0.32f, 0, 0));
        MeshBuilder torsoB = new MeshBuilder();
        Ring[] torsoR = {
                new Ring(0, -0.34f, -0.04f, 0.44f, 0.34f, 0f, 0.0f),
                new Ring(0, -0.02f,  0.02f, 0.48f, 0.38f, 0f, 0.32f),
                new Ring(0,  0.34f,  0.10f, 0.66f, 0.46f, 0f, 0.64f),
                new Ring(0,  0.66f,  0.04f, 0.74f, 0.48f, 0f, 0.88f),
                new Ring(0,  0.82f, -0.06f, 0.44f, 0.34f, 0f, 1.0f)
        };
        torsoB.addLoft(torsoR, 4, UvRect.SKIN, UvRect.BELLY_ARMOR, true, true);
        torsoB.addBox( 0.66f, 0.54f, 0.08f, 0.58f, 0.56f, 0.60f, UvRect.SKIN);
        torsoB.addBox(-0.66f, 0.54f, 0.08f, 0.58f, 0.56f, 0.60f, UvRect.SKIN);
        torsoB.addBox(0f, 0.48f, 0.44f, 1.06f, 0.48f, 0.28f, UvRect.SKIN);
        body.baseMesh = torsoB.build();
        for (int i = -2; i <= 2; i++) {
            SculptedBone mf = body.addChild(new SculptedBone("mane" + (i + 2), i * 0.22f, 0.76f, -0.04f, -1.15f, 0, i * 0.24f));
            mf.baseMesh = new MeshBuilder().addCurvedBlade(0.38f, 0.60f, 0.16f, 0.04f, 2, 3, UvRect.MEMBRANE_FULL).build();
        }

        SculptedBone head = body.addChild(MeshBuilder.buildAnatomicalHead(
                "head", 0, 0.72f, 0.38f, 1.06f, 0.58f, 0.35f, 0.30f,
                UvRect.SKIN, UvRect.MAW_DARK, UvRect.EYE_MAIN, UvRect.EYE_MAIN, null, false, true));
        head.xRot = -0.28f;
        SculptedBone jaw = head.getChild("head_jaw");
        MeshBuilder ears = new MeshBuilder();
        ears.addCurvedSpike( 0.22f, 0.26f, -0.06f,  0.30f, 0.48f, -0.10f,  0.34f, 0.68f, -0.14f, 0.10f, 3, 4, UvRect.SKIN);
        ears.addCurvedSpike(-0.22f, 0.26f, -0.06f, -0.30f, 0.48f, -0.10f, -0.34f, 0.68f, -0.14f, 0.10f, 3, 4, UvRect.SKIN);
        head.addChild(new SculptedBone("ears", 0, 0, 0)).baseMesh = ears.build();

        // REALISTIC ANATOMICAL WEREWOLF ARMS (Deltoid -> Muscular Bicep -> Elbow Spur -> Tapered Forearm) + 5 Moving Fingers!
        SculptedBone[] arms = new SculptedBone[2], fores = new SculptedBone[2], hands = new SculptedBone[2];
        SculptedBone[][][] fenFingers = new SculptedBone[2][][];
        for (int sIdx = 0; sIdx < 2; sIdx++) {
            int side = (sIdx == 0) ? 1 : -1;
            SculptedBone arm = body.addChild(new SculptedBone("arm" + sIdx, side * 0.62f, 0.52f, 0.10f, -0.26f, side * -0.10f, side * 0.34f));
            MeshBuilder am = new MeshBuilder();
            am.addChamferedBox(0, 0.02f, 0, 0.25f, 0.25f, 0.25f, UvRect.SKIN);
            am.addLoft(new Ring[] {
                    new Ring(0,  0.18f,  0.00f, 0.23f, 0.23f, 0, 0.00f),
                    new Ring(0, -0.08f,  0.02f, 0.27f, 0.26f, 0, 0.22f), // Massive werewolf deltoid
                    new Ring(0, -0.36f,  0.04f, 0.23f, 0.24f, 0, 0.58f), // Bulging bicep belly
                    new Ring(0, -0.70f,  0.00f, 0.175f, 0.18f, 0, 0.90f), // Tapered elbow
                    new Ring(0, -0.78f,  0.00f, 0.16f, 0.165f, 0, 1.00f)
            }, 4, UvRect.SKIN, null, true, true);
            am.addCurvedSpike(side * 0.10f, -0.60f, -0.10f, side * 0.16f, -0.70f, -0.26f, side * 0.18f, -0.80f, -0.34f, 0.058f, 3, 4, UvRect.SKIN);
            arm.baseMesh = am.build();

            SculptedBone fore = arm.addChild(new SculptedBone("fore" + sIdx, 0, -0.70f, 0, -0.48f, side * 0.12f, side * -0.22f));
            MeshBuilder fm = new MeshBuilder();
            fm.addChamferedBox(0, 0, 0, 0.195f, 0.195f, 0.195f, UvRect.SKIN);
            fm.addLoft(new Ring[] {
                    new Ring(0,  0.14f,  0.00f, 0.18f, 0.18f, 0, 0.00f),
                    new Ring(0, -0.14f,  0.02f, 0.215f, 0.205f, 0, 0.26f), // Muscular forearm bulge
                    new Ring(0, -0.48f,  0.02f, 0.155f, 0.145f, 0, 0.64f), // Tapered forearm
                    new Ring(0, -0.72f,  0.01f, 0.13f, 0.12f, 0, 0.90f),   // Slim wrist
                    new Ring(0, -0.78f,  0.01f, 0.12f, 0.11f, 0, 1.00f)
            }, 4, UvRect.SKIN, null, true, true);
            fore.baseMesh = fm.build();

            SculptedBone hand = fore.addChild(new SculptedBone("hand" + sIdx, 0, -0.72f, 0.04f, 0.10f, 0, 0));
            MeshBuilder hm = new MeshBuilder();
            hm.addChamferedBox(0, 0.02f, 0, 0.14f, 0.12f, 0.14f, UvRect.SKIN);
            hm.addLoft(new Ring[] {
                    new Ring(0, -0.04f, -0.04f, 0.135f, 0.095f, 0, 0f),
                    new Ring(0, -0.09f,  0.14f, 0.195f, 0.088f, 0, 0.65f),
                    new Ring(0, -0.10f,  0.26f, 0.19f,  0.080f, 0, 1.0f)
            }, 4, UvRect.SKIN, null, true, true);
            hand.baseMesh = hm.build();
            fenFingers[sIdx] = buildArticulatedFingers(hand, "ff" + sIdx, side, 1.15f, UvRect.SKIN);
            arms[sIdx] = arm; fores[sIdx] = fore; hands[sIdx] = hand;
        }

        SculptedBone t1 = pelvis.addChild(new SculptedBone("t1", 0, 0.02f, -0.24f, -0.35f, 0, 0));
        t1.baseMesh = addTelescopingTubeZ(new MeshBuilder(), -0.55f, 0.18f, 0.22f, UvRect.SKIN, null, false).build();
        SculptedBone t2 = t1.addChild(new SculptedBone("t2", 0, 0, -0.54f, 0.22f, 0, 0));
        t2.baseMesh = addTelescopingTubeZ(new MeshBuilder(), -0.56f, 0.22f, 0.08f, UvRect.SKIN, null, true).build();

        // Anatomical Digitigrade Werewolf Hind Legs + 4 Individually Articulated Dual-Joint Moving Clawed Toes!
        SculptedBone[] legs = new SculptedBone[2], shins = new SculptedBone[2], paws = new SculptedBone[2];
        SculptedBone[][][] fenToes = new SculptedBone[2][][];
        for (int sIdx = 0; sIdx < 2; sIdx++) {
            int side = (sIdx == 0) ? 1 : -1;
            SculptedBone leg = pelvis.addChild(new SculptedBone("leg" + sIdx, side * 0.35f, 0.0f, -0.02f, -0.38f, 0, 0));
            MeshBuilder lm = new MeshBuilder();
            lm.addChamferedBox(0, 0, 0, 0.25f, 0.25f, 0.25f, UvRect.SKIN);
            lm.addLoft(new Ring[] {
                    new Ring(0,  0.17f,  0.00f, 0.23f, 0.23f, 0, 0.00f),
                    new Ring(0, -0.08f,  0.03f, 0.26f, 0.27f, 0, 0.25f), // Powerful wolf quadriceps/haunch
                    new Ring(0, -0.28f,  0.02f, 0.21f, 0.22f, 0, 0.62f),
                    new Ring(0, -0.44f,  0.00f, 0.165f, 0.17f, 0, 0.90f), // Tapered knee
                    new Ring(0, -0.52f,  0.00f, 0.155f, 0.16f, 0, 1.00f)
            }, 4, UvRect.SKIN, null, true, true);
            leg.baseMesh = lm.build();

            SculptedBone shin = leg.addChild(new SculptedBone("shin" + sIdx, 0, -0.44f, 0.02f, 0.52f, 0, 0));
            MeshBuilder sm = new MeshBuilder();
            sm.addChamferedBox(0, 0, 0, 0.18f, 0.18f, 0.18f, UvRect.SKIN);
            sm.addLoft(new Ring[] {
                    new Ring(0,  0.13f,  0.00f, 0.17f, 0.17f, 0, 0.00f),
                    new Ring(0, -0.10f, -0.03f, 0.185f, 0.19f, 0, 0.28f), // Muscular calf/gastrocnemius
                    new Ring(0, -0.32f,  0.00f, 0.135f, 0.14f, 0, 0.72f),
                    new Ring(0, -0.44f,  0.01f, 0.12f, 0.12f, 0, 0.90f),  // Slim ankle
                    new Ring(0, -0.50f,  0.01f, 0.11f, 0.11f, 0, 1.00f)
            }, 4, UvRect.SKIN, null, true, true);
            sm.addCurvedSpike(0, -0.34f, -0.10f, 0, -0.40f, -0.22f, 0, -0.44f, -0.28f, 0.045f, 2, 4, UvRect.SKIN);
            shin.baseMesh = sm.build();

            SculptedBone paw = shin.addChild(new SculptedBone("paw" + sIdx, 0, -0.44f, -0.02f, -0.14f, 0, 0));
            fenToes[sIdx] = buildArticulatedWolfToes(paw, "ft" + sIdx, 1.10f, UvRect.SKIN);
            legs[sIdx] = leg; shins[sIdx] = shin; paws[sIdx] = paw;
        }

        return new SculptedBossRig<>() {
            @Override public SculptedBone root() { return root; }
            @Override public void animate(FenrirBossEntity e, float ls, float lsa, float age, float ny, float hp, BossAnimState st) {
                float mv = BossAnimState.ease(st.moveBlend);
                float wc = st.gaitPhase * 1.38f;
                float br = Mth.sin(age * 0.12f);
                float cast = BossAnimState.ease(st.castBlend);
                float cast2 = BossAnimState.ease(st.cast2Blend);
                float sl = st.slashCurve();
                float sSide = st.attackSide;
                float turn = st.turnRate;

                // Savage athletic werewolf stalk/charge: spring-loaded hip bounce, deep predatory hunch & counter-twisting chest!
                float stepBounce = Math.abs(Mth.sin(wc)) * 0.12f * mv;
                pelvis.y = 0.88f + br * 0.025f + stepBounce - mv * 0.07f;
                pelvis.zRot = Mth.cos(wc) * 0.075f * mv;
                pelvis.yRot = -Mth.sin(wc) * 0.12f * mv;

                body.xRot = 0.32f + mv * 0.18f + br * 0.03f + Mth.cos(wc * 2f) * 0.05f * mv - cast2 * 0.34f - cast * 0.12f + sl * 0.18f;
                body.yRot = Mth.sin(wc) * 0.24f * mv + turn * 0.22f - sl * 0.34f * sSide;
                body.zRot = -Mth.cos(wc) * 0.06f * mv;

                // Fierce head stabilization (keeps glowing eyes dead-level on prey through heavy werewolf strides!)
                head.yRot = -ny * Mth.DEG_TO_RAD * 0.62f - Mth.sin(wc) * 0.12f * mv + turn * 0.16f + sl * 0.18f * sSide;
                head.xRot = -0.28f - mv * 0.14f + hp * Mth.DEG_TO_RAD * 0.42f - cast2 * 0.22f;
                jaw.xRot = Mth.lerp(cast, 0.32f + Math.abs(br) * 0.14f + mv * 0.16f + Math.max(0f, sl) * 0.58f, 0.86f + Mth.sin(age * 0.5f) * 0.12f);

                for (int sIdx = 0; sIdx < 2; sIdx++) {
                    float ph = (sIdx == 0) ? wc : (wc + Mth.PI);
                    float swing = Mth.cos(ph) + Mth.sin(ph * 2f) * 0.14f;
                    float lift = Math.max(0f, Mth.sin(ph));
                    float liftE = lift * lift * (3f - 2f * lift);
                    float hRot = -0.38f - swing * 0.58f * mv - liftE * 0.30f * mv;
                    float kRot =  0.52f + liftE * 0.66f * mv + Math.max(0f, swing) * 0.22f * mv;
                    float pRot = Mth.lerp(liftE * mv, -(hRot + kRot), 0.38f - Math.max(0f, swing) * 0.22f);
                    legs[sIdx].xRot = hRot; shins[sIdx].xRot = kRot; paws[sIdx].xRot = pRot;
                    animateWolfToes(fenToes[sIdx], ph, mv, age, sIdx * 2.1f);
                }

                for (int sIdx = 0; sIdx < 2; sIdx++) {
                    int side = (sIdx == 0) ? 1 : -1;
                    float armPh = (sIdx == 0) ? (wc + Mth.PI) : wc;
                    float swing = Mth.cos(armPh);
                    float lag   = Mth.cos(armPh - 0.72f);
                    float idle  = Mth.sin(age * 0.12f + sIdx * 0.6f);
                    float isPrimary = (side * sSide > 0f) ? 1.0f : 0.35f;

                    // Powerful athletic predatory arm swing with delayed forearm snap & flared elbows!
                    float shX = -0.26f - mv * 0.16f - swing * 0.62f * mv + idle * 0.055f;
                    float shZ = side * (0.34f + mv * 0.08f + Math.abs(swing) * 0.10f * mv + idle * 0.03f);
                    float elX = -0.48f - mv * 0.18f - Math.max(0f, lag) * 0.44f * mv - Math.abs(idle) * 0.10f;
                    float elZ = side * -0.22f;
                    float haX = 0.10f + lag * 0.26f * mv + idle * 0.08f;

                    if (Math.abs(sl) > 0.001f) {
                        shX += -sl * 1.18f * isPrimary;
                        shZ += side * (0.26f * sl * isPrimary);
                        elX += (-0.44f + Math.max(0f, sl) * 0.34f) * Math.abs(sl) * isPrimary;
                        haX += (0.42f * sl) * isPrimary;
                    }

                    float slamWave = Mth.sin(age * 0.34f + sIdx * Mth.PI) * 0.28f;
                    arms[sIdx].xRot  = Mth.lerp(cast, shX, -1.55f + slamWave);
                    arms[sIdx].zRot  = Mth.lerp(cast, shZ, side * (0.54f + slamWave * 0.20f));
                    fores[sIdx].xRot = Mth.lerp(cast, elX, -0.68f - Math.abs(slamWave) * 0.28f);
                    fores[sIdx].zRot = Mth.lerp(cast, elZ, side * -0.34f);
                    hands[sIdx].xRot = Mth.lerp(cast, haX, -0.30f + slamWave * 0.36f);

                    float fCurl = 0.22f + Math.max(0f, sl) * 0.74f * isPrimary - cast * 0.35f + Math.max(0f, -swing) * 0.32f * mv;
                    float fSplay = cast * 0.85f + Math.max(0f, swing) * 0.30f * mv + Math.max(0f, -sl) * 0.65f * isPrimary;
                    animateArticulatedFingers(fenFingers[sIdx], side, age, sIdx * 1.8f, fCurl, fSplay);
                }

                t1.yRot = -turn * 0.40f + Mth.sin(age * 0.16f + wc * 0.5f) * (0.26f + mv * 0.18f);
                t1.xRot = -0.35f - mv * 0.18f;
                t2.yRot = -turn * 0.45f + Mth.sin(age * 0.16f + wc * 0.5f - 0.85f) * (0.36f + mv * 0.24f);
            }
        };
    }

    // =========================================================================
    // (Jörmungandr đã được xây lại hoàn toàn bằng GeckoLib 4.8.3 + SerpentPath)
    // =========================================================================
    // 6. TIAMAT — Seamless Armored Sea-Dragon Queen + Telescoping 7 Necks & Twin Tails!
    // =========================================================================
    public static SculptedBossRig<TiamatBossEntity> createTiamat() {
        SculptedBone root = new SculptedBone("root", 0, 0, 0);
        SculptedBone body = root.addChild(new SculptedBone("body", 0, 0.65f, 0));
        MeshBuilder bm = new MeshBuilder();
        Ring[] torsoR = {
                new Ring(0, -0.64f, -0.15f, 0.74f, 0.56f, 0f, 0.0f),
                new Ring(0, -0.15f,  0.02f, 0.60f, 0.48f, 0f, 0.35f),
                new Ring(0,  0.34f,  0.12f, 0.82f, 0.58f, 0f, 0.75f),
                new Ring(0,  0.60f,  0.04f, 0.56f, 0.40f, 0f, 1.0f)
        };
        bm.addLoft(torsoR, 4, UvRect.SKIN, null, true, true);
        bm.addBox(0f, 0.24f, 0.60f, 0.92f, 0.58f, 0.24f, UvRect.BELLY_ARMOR);
        for (int side = -1; side <= 1; side += 2) {
            for (int r = 0; r < 3; r++) {
                float ry = -0.05f + r * 0.22f;
                bm.addCurvedSpike(side * 0.25f, ry, 0.52f, side * 0.72f, ry + 0.08f, 0.30f, side * 0.96f, ry + 0.24f, -0.12f, 0.08f, 3, 4, UvRect.BELLY_ARMOR);
            }
        }
        body.baseMesh = bm.build();

        SculptedBone[][] tails = new SculptedBone[2][4];
        for (int sIdx = 0; sIdx < 2; sIdx++) {
            int side = (sIdx == 0) ? 1 : -1;
            SculptedBone tc = body;
            for (int k = 0; k < 4; k++) {
                SculptedBone seg = tc.addChild(new SculptedBone("tc_" + sIdx + "_" + k,
                        k == 0 ? side * 0.42f : 0f,
                        k == 0 ? -0.34f : 0f,
                        k == 0 ? -0.16f : -0.74f,
                        0, side * (k >= 2 ? 0.45f : -0.52f), 0));
                seg.baseMesh = addTelescopingTubeZ(new MeshBuilder(), -0.74f, 0.32f - k * 0.06f, 0.26f - k * 0.06f, UvRect.SKIN, UvRect.BELLY_ARMOR, k == 3).build();
                tails[sIdx][k] = seg; tc = seg;
            }
        }

        float[] ox = { 0f, 0f, 0.38f, -0.38f, 0.60f, -0.60f, 0.26f };
        float[] oy = { 0.24f, 0.52f, 0.48f, 0.48f, 0.26f, 0.26f, 0.40f };
        float[] oz = { 0.50f, 0.16f, 0.20f, 0.20f, 0.34f, 0.34f, 0.38f };
        float[] bRx = { -0.52f, -1.35f, -1.14f, -1.14f, -0.76f, -0.76f, -0.94f };
        float[] bRy = { 0f, 0f, 0.46f, -0.46f, 0.82f, -0.82f, -0.22f };
        float[] sc = { 0.94f, 1.24f, 1.06f, 1.06f, 0.94f, 0.94f, 0.90f };

        SculptedBone[] n1s = new SculptedBone[7], n2s = new SculptedBone[7], n3s = new SculptedBone[7], hds = new SculptedBone[7], jaws = new SculptedBone[7];
        for (int i = 0; i < 7; i++) {
            float l = 0.64f * sc[i];
            SculptedBone n1 = body.addChild(new SculptedBone("t" + i + "_n1", ox[i], oy[i], oz[i], bRx[i], bRy[i], 0));
            n1.baseMesh = addTelescopingTubeZ(new MeshBuilder(), l, 0.21f * sc[i], 0.19f * sc[i], UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();
            SculptedBone n2 = n1.addChild(new SculptedBone("t" + i + "_n2", 0, 0, l, 0.36f, 0, 0));
            n2.baseMesh = addTelescopingTubeZ(new MeshBuilder(), l * 0.92f, 0.19f * sc[i], 0.17f * sc[i], UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();
            SculptedBone n3 = n2.addChild(new SculptedBone("t" + i + "_n3", 0, 0, l * 0.92f, 0.42f, 0, 0));
            n3.baseMesh = addTelescopingTubeZ(new MeshBuilder(), l * 0.84f, 0.17f * sc[i], 0.15f * sc[i], UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();

            SculptedBone hd = n3.addChild(MeshBuilder.buildAnatomicalHead(
                    "t" + i + "_hd", 0, 0.02f, l * 0.80f, 0.90f * sc[i], 0.56f, 0.31f, 0.26f,
                    UvRect.SKIN, UvRect.MAW_GLOW, UvRect.EYE_MAIN, UvRect.EYE_MAIN, (i == 0) ? UvRect.EYE_ALT2 : null, true, true));
            hd.xRot = 0.32f;

            MeshBuilder crown = new MeshBuilder();
            for (int sp = 0; sp < 7; sp++) {
                float ft = (sp / 6.0f) * 2.0f - 1.0f;
                float ang = ft * 1.90f;
                float spLen = (i <= 1 ? 0.72f : 0.54f) * sc[i];
                float dx = Mth.sin(ang) * spLen;
                float dy = Mth.cos(ang) * spLen;
                crown.addCurvedSpike(dx * 0.18f, dy * 0.18f + 0.04f, -0.05f, dx * 0.65f, dy * 0.65f + 0.04f, -0.22f,
                        dx, dy + 0.02f, -0.34f, 0.050f * sc[i], 3, 4, UvRect.HORN_BONE);
            }
            hd.addChild(new SculptedBone("t" + i + "_cr", 0, 0, 0)).baseMesh = crown.build();
            n1s[i] = n1; n2s[i] = n2; n3s[i] = n3; hds[i] = hd; jaws[i] = hd.getChild("t" + i + "_hd_jaw");
        }

        return new SculptedBossRig<>() {
            @Override public SculptedBone root() { return root; }
            @Override public void animate(TiamatBossEntity e, float ls, float lsa, float age, float ny, float hp, BossAnimState st) {
                float sleep = BossAnimState.ease(st.sleepBlend);
                float mv = BossAnimState.ease(st.moveBlend) * (1f - sleep);
                float cast = BossAnimState.ease(st.castBlend);
                float sl = st.slashCurve();
                float turn = st.turnRate * (1f - sleep);
                float yaw = -ny * Mth.DEG_TO_RAD * 0.45f * (1f - sleep), pitch = hp * Mth.DEG_TO_RAD * 0.35f;
                float swimPhase = age * 0.12f + st.gaitPhase * 1.25f;

                // Regal hovering/gliding Dragon-Queen torso surge (NO MORE rigid statue!): altitude wave, chase pitch & turn bank!
                body.x = Mth.sin(swimPhase * 0.85f) * 0.18f * mv;
                body.y = Mth.lerp(sleep, 0.65f + Mth.sin(swimPhase) * (0.12f + mv * 0.10f) + cast * 0.14f, 0.48f);
                body.xRot = Mth.lerp(sleep, mv * 0.18f + Mth.cos(swimPhase) * 0.06f - cast * 0.14f, 0.08f);
                body.yRot = Mth.lerp(sleep, Mth.cos(swimPhase * 0.85f) * 0.14f * mv + turn * 0.16f, 0f);
                body.zRot = Mth.lerp(sleep, Mth.sin(swimPhase * 0.85f) * (0.06f + mv * 0.08f) + turn * 0.18f, 0f);

                for (int i = 0; i < 7; i++) {
                    float ph = i * 1.05f, spd = 0.11f + (i % 3) * 0.016f;
                    float lunge = (i <= 1 ? sl : sl * 0.38f);
                    float weaveY = Mth.sin(age * spd + ph + swimPhase * 0.35f) * (0.17f + mv * 0.09f);
                    float weaveX = Mth.cos((age * spd + ph) * 1.8f) * (0.08f + mv * 0.04f);
                    n1s[i].yRot = Mth.lerp(sleep, bRy[i] + yaw * 0.38f + turn * 0.22f + weaveY, bRy[i] * 0.65f);
                    n2s[i].yRot = Mth.lerp(sleep, Mth.sin(age * spd + ph - 0.85f + swimPhase * 0.35f) * (0.19f + mv * 0.09f), 0f);
                    n3s[i].yRot = Mth.lerp(sleep, Mth.sin(age * spd + ph - 1.55f + swimPhase * 0.35f) * (0.16f + mv * 0.08f), 0f);
                    n1s[i].xRot = Mth.lerp(sleep, bRx[i] + mv * 0.10f - cast * 0.18f + lunge * 0.24f + weaveX, -0.35f);
                    hds[i].xRot = Mth.lerp(sleep, 0.32f + pitch - mv * 0.10f, 0.12f);
                    jaws[i].xRot = Mth.lerp(sleep, Mth.lerp(cast, 0.34f + Math.abs(Mth.sin(age * 0.20f + ph)) * 0.18f + Math.max(0f, lunge) * 0.42f, 0.84f + Math.abs(Mth.sin(age * 0.34f + ph)) * 0.14f), 0.04f);
                }
                for (int sIdx = 0; sIdx < 2; sIdx++) {
                    int side = (sIdx == 0) ? 1 : -1;
                    for (int k = 0; k < 4; k++) {
                        float slither = -turn * (0.18f + k * 0.04f) + Mth.sin(swimPhase * 1.15f - k * 0.68f + sIdx * Mth.PI) * (0.34f + k * 0.04f);
                        float idle = side * (k >= 2 ? 0.45f : -0.52f) + side * Mth.sin(age * 0.12f - k * 0.8f) * 0.22f;
                        tails[sIdx][k].yRot = Mth.lerp(sleep, Mth.lerp(mv, idle, slither), side * -0.48f);
                        tails[sIdx][k].xRot = Mth.lerp(sleep, Mth.cos(swimPhase - k * 0.7f) * (0.06f + mv * 0.10f), 0f);
                    }
                }
            }
        };
    }

}
