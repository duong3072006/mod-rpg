package com.abyssalrpg.client.sculpt;

import com.abyssalrpg.client.sculpt.MeshBuilder.Ring;
import com.abyssalrpg.entity.AbyssalKnightBossEntity;
import com.abyssalrpg.entity.CryptLichBossEntity;
import com.abyssalrpg.entity.MoltenTitanBossEntity;
import net.minecraft.util.Mth;

/**
 * 30% Realistic / 70% Minecraft-Boxy Rigs for the 4 Core Abyssal Spire Bosses:
 * - Bulletproof telescoping ball-in-socket joints (parent overshoots child pivot + child extends behind pivot
 *   + oversize beveled joint ball at pivot + every hip/shoulder locked DEEPLY inside torso/pelvis!)
 * - Anatomically safe joint bend angles -> ZERO cracked, broken, or detached limbs during movement!
 */
public final class SculptedCoreRigs {
    private SculptedCoreRigs() {}

    /**
     * Telescoping ball-in-socket tube along Z (for necks & tails) — mathematically gap-free up to 75 deg bend!
     */
    private static MeshBuilder addTelescopingTubeZ(MeshBuilder mb, float stepLen, float r0, float r1,
                                                   UvRect topUv, UvRect bellyUv, boolean isTip) {
        float sign = (stepLen >= 0f) ? 1f : -1f;
        float backOv = r0 * 0.72f;
        float fwdOv = isTip ? 0f : (r1 * 0.44f);
        mb.addChamferedBox(0f, 0f, 0f, r0 * 1.06f, r0 * 0.98f, r0 * 1.06f, topUv, bellyUv);
        Ring[] rings = {
                new Ring(0, 0, -sign * backOv,          r0 * 0.96f, r0 * 0.88f, 0f, 0.00f),
                new Ring(0, 0, 0f,                      r0,         r0 * 0.90f, 0f, 0.18f),
                new Ring(0, 0, stepLen * 0.52f,         Mth.lerp(0.52f, r0, r1), Mth.lerp(0.52f, r0, r1) * 0.90f, 0f, 0.58f),
                new Ring(0, 0, stepLen + sign * fwdOv,  r1,         r1 * 0.90f, 0f, 1.00f)
        };
        mb.addLoft(rings, 4, topUv, bellyUv, true, true);
        return mb;
    }

    /**
     * Realistic anatomical telescoping upper limb along -Y (rounded shoulder/haunch cap -> bulging muscle belly
     * -> sculpted elbow/knee taper -> optional rear elbow/hock spur) — ZERO joint cracking!
     */
    private static MeshBuilder addAnatomicalUpperLimbY(MeshBuilder mb, float stepDown, float rTop, float rMid, float rBot,
                                                       UvRect uv, boolean hasRearSpur) {
        float topOv = rTop * 0.78f;
        float botOv = rBot * 0.46f;
        mb.addChamferedBox(0f, 0f, 0f, rTop * 1.12f, rTop * 1.12f, rTop * 1.12f, uv);
        Ring[] rings = {
                new Ring(0,  topOv,             0.00f,          rTop * 0.96f, rTop * 0.96f, 0f, 0.00f),
                new Ring(0, -stepDown * 0.14f,  0.02f * rTop,   rMid,         rMid * 1.04f, 0f, 0.24f),
                new Ring(0, -stepDown * 0.56f,  0.03f * rTop,   Mth.lerp(0.45f, rMid, rBot), Mth.lerp(0.45f, rMid, rBot) * 1.02f, 0f, 0.62f),
                new Ring(0, -stepDown,          0.00f,          rBot,         rBot,         0f, 0.90f),
                new Ring(0, -stepDown - botOv,  0.00f,          rBot * 0.92f, rBot * 0.92f, 0f, 1.00f)
        };
        mb.addLoft(rings, 4, uv, null, true, true);
        if (hasRearSpur) {
            mb.addCurvedSpike(0f, -stepDown * 0.84f, -rBot * 0.65f,
                    0f, -stepDown * 0.94f, -rBot * 1.45f,
                    0f, -stepDown * 1.04f, -rBot * 1.95f, rBot * 0.36f, 3, 4, uv);
        }
        return mb;
    }

    /**
     * Realistic anatomical telescoping lower limb along -Y (muscular upper calf/forearm -> slender tendon ankle/wrist!).
     */
    private static MeshBuilder addAnatomicalLowerLimbY(MeshBuilder mb, float stepDown, float rTop, float rMid, float rBot,
                                                       UvRect uv) {
        float topOv = rTop * 0.78f;
        float botOv = rBot * 0.46f;
        mb.addChamferedBox(0f, 0f, 0f, rTop * 1.10f, rTop * 1.10f, rTop * 1.10f, uv);
        Ring[] rings = {
                new Ring(0,  topOv,             0.00f,         rTop * 0.96f, rTop * 0.96f, 0f, 0.00f),
                new Ring(0, -stepDown * 0.18f, -0.02f * rTop,  rMid,         rMid * 1.05f, 0f, 0.28f),
                new Ring(0, -stepDown * 0.62f,  0.01f * rTop,  Mth.lerp(0.60f, rMid, rBot), Mth.lerp(0.60f, rMid, rBot), 0f, 0.68f),
                new Ring(0, -stepDown,          0.01f * rTop,  rBot,         rBot,         0f, 0.90f),
                new Ring(0, -stepDown - botOv,  0.01f * rTop,  rBot * 0.92f, rBot * 0.92f, 0f, 1.00f)
        };
        mb.addLoft(rings, 4, uv, null, true, true);
        return mb;
    }

    /**
     * Builds a sculpted anatomical paw/foot with 4 individually articulated dual-joint moving clawed toes
     * (3 splayed weight-bearing front talons + 1 opposing rear dewclaw/hallux talon)!
     * Returns SculptedBone[4][2]: [toeIdx][0]=proximal knuckle phalanx, [toeIdx][1]=distal curved claw.
     */
    private static SculptedBone[][] buildArticulatedPawToes(SculptedBone paw, String prefix, float sc,
                                                            UvRect skinUv, boolean emitGlowOnSkin) {
        MeshBuilder pm = new MeshBuilder();
        pm.addChamferedBox(0, 0.02f * sc, 0, 0.13f * sc, 0.11f * sc, 0.13f * sc, skinUv);
        pm.addLoft(new Ring[] {
                new Ring(0, -0.02f * sc, -0.04f * sc, 0.12f * sc, 0.085f * sc, 0, 0.00f),
                new Ring(0, -0.06f * sc,  0.10f * sc, 0.155f * sc, 0.075f * sc, 0, 0.58f),
                new Ring(0, -0.075f * sc, 0.20f * sc, 0.15f * sc, 0.065f * sc, 0, 1.00f)
        }, 4, skinUv, null, true, true);
        paw.baseMesh = pm.build();
        if (emitGlowOnSkin) paw.glowMesh = paw.baseMesh;

        SculptedBone[][] toes = new SculptedBone[4][2];
        float[] tx   = { -0.105f, 0.000f,  0.105f };
        float[] tz   = {  0.17f,  0.20f,   0.17f  };
        float[] ty   = { -0.26f,  0.00f,   0.26f  };
        float[] pLen = {  0.14f,  0.17f,   0.14f  };
        float[] cLen = {  0.16f,  0.19f,   0.16f  };
        float[] rad  = {  0.038f, 0.042f,  0.038f };

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
            if (emitGlowOnSkin) prox.glowMesh = prox.baseMesh;

            SculptedBone dist = prox.addChild(new SculptedBone(prefix + "_t" + i + "d",
                    0, -0.01f * sc, l1, 0.14f, 0, 0));
            MeshBuilder b2 = new MeshBuilder();
            b2.addChamferedBox(0, 0, 0, r * 0.85f, r * 0.80f, r * 0.85f, skinUv);
            b2.addCurvedSpike(0, 0, -r * 0.35f, 0, -0.02f * sc, l2 * 0.55f, 0, -0.09f * sc, l2, r * 0.82f, 3, 4, UvRect.HORN_BONE);
            dist.baseMesh = b2.build();
            toes[i][0] = prox;
            toes[i][1] = dist;
        }

        // Toe 3: Opposing rear dewclaw / hallux talon (grips backward & curls in mid-air!)
        float rl1 = 0.11f * sc, rl2 = 0.13f * sc, rr = 0.034f * sc;
        SculptedBone rProx = paw.addChild(new SculptedBone(prefix + "_t3p",
                0f, -0.05f * sc, -0.08f * sc, 0.16f, Mth.PI, 0f));
        MeshBuilder rb1 = new MeshBuilder();
        rb1.addChamferedBox(0, 0, 0, rr * 1.15f, rr * 1.05f, rr * 1.15f, skinUv);
        rb1.addLoft(new Ring[] {
                new Ring(0, 0, -rr * 0.6f, rr, rr * 0.90f, 0, 0f),
                new Ring(0, -0.01f * sc, rl1 + rr * 0.3f, rr * 0.76f, rr * 0.72f, 0, 1f)
        }, 4, skinUv, null, true, true);
        rProx.baseMesh = rb1.build();
        if (emitGlowOnSkin) rProx.glowMesh = rProx.baseMesh;

        SculptedBone rDist = rProx.addChild(new SculptedBone(prefix + "_t3d", 0, -0.01f * sc, rl1, 0.22f, 0, 0));
        MeshBuilder rb2 = new MeshBuilder();
        rb2.addCurvedSpike(0, 0, -rr * 0.3f, 0, -0.02f * sc, rl2 * 0.55f, 0, -0.08f * sc, rl2, rr * 0.80f, 3, 4, UvRect.HORN_BONE);
        rDist.baseMesh = rb2.build();
        toes[3][0] = rProx;
        toes[3][1] = rDist;
        return toes;
    }

    /**
     * Animates all 4 articulated toes on a quadruped paw: flat ground grip during stance, natural downward curl
     * on lift-off, splay on touchdown, organic alive idle ripple, and eagle-talon clench when airborne!
     */
    private static void animatePawToes(SculptedBone[][] toes, float phase, float mv, float age, float legSeed, float airBlend) {
        float lift = Math.max(0f, Mth.sin(phase));
        float liftE = lift * lift * (3f - 2f * lift);
        float stanceGrip = Math.max(0f, -Mth.sin(phase)) * mv;
        float[] baseY = { -0.26f, 0.0f, 0.26f, Mth.PI };
        for (int i = 0; i < 4; i++) {
            float idleWave = Mth.sin(age * 0.14f + legSeed + i * 0.65f) * 0.06f;
            float gndCurl = 0.04f + liftE * 0.54f * mv - stanceGrip * 0.08f + idleWave;
            float airCurl = 0.68f + Mth.sin(age * 0.16f + legSeed + i * 0.55f) * 0.18f;
            float curl = Mth.lerp(airBlend, gndCurl, airCurl);
            toes[i][0].xRot = 0.06f + curl * 0.52f;
            toes[i][1].xRot = 0.12f + curl * 0.72f;
            if (i < 3) {
                float splay = 1.0f + stanceGrip * 0.28f - liftE * 0.22f * mv - airBlend * 0.25f;
                toes[i][0].yRot = baseY[i] * splay;
            }
        }
    }

    /**
     * Fluid, weighty, asymmetric predator quadruped stride (Hip -> Knee -> Flat-Planting Paw).
     * Uses snappy high-knee paw recovery during swing + planted power-push during stance so limbs
     * feel heavy, athletic, and grounded (ZERO stiff pendulum motion or broken joints!).
     */
    private static void animateQuadLeg(SculptedBone hip, SculptedBone knee, SculptedBone paw,
                                       boolean front, float phase, float mv, float br) {
        // Asymmetric stride waveform: snappy forward reach + longer grounded push-off stroke
        float s1 = Mth.cos(phase);
        float s2 = Mth.sin(phase * 2.0f) * 0.14f;
        float swing = Mth.clamp(s1 + s2, -1.12f, 1.12f);
        float liftRaw = Math.max(0f, Mth.sin(phase));
        float liftE = liftRaw * liftRaw * (3f - 2f * liftRaw);
        float pushOff = Math.max(0f, -Mth.sin(phase - 0.45f));

        if (front) {
            float hRot = 0.12f - swing * 0.46f * mv - liftE * 0.30f * mv + pushOff * 0.08f * mv + br * 0.025f;
            float kRot = -0.22f + liftE * 0.78f * mv + Math.max(0f, -swing) * 0.18f * mv - pushOff * 0.12f * mv - br * 0.03f;
            float flatPaw = -(hRot + kRot);
            // Wrist folds gracefully backward on lift, snaps forward just before touchdown, plants flat on stance!
            float swingPaw = 0.44f - Math.max(0f, swing) * 0.28f;
            paw.xRot = Mth.lerp(liftE * mv, flatPaw, swingPaw);
            hip.xRot = hRot; knee.xRot = kRot;
        } else {
            float hRot = -0.22f - swing * 0.48f * mv - liftE * 0.28f * mv + pushOff * 0.12f * mv - br * 0.025f;
            float kRot =  0.42f + liftE * 0.62f * mv + Math.max(0f, swing) * 0.18f * mv - pushOff * 0.14f * mv + br * 0.03f;
            float flatPaw = -(hRot + kRot);
            float swingPaw = 0.36f - Math.max(0f, swing) * 0.22f;
            paw.xRot = Mth.lerp(liftE * mv, flatPaw, swingPaw);
            hip.xRot = hRot; knee.xRot = kRot;
        }
    }

    // =========================================================================
    // 1. DIRE SHADOW FENRIR — Deeply Anchored 3-Joint Athletic Direwolf!
    // =========================================================================
    public static SculptedBossRig<AbyssalKnightBossEntity> createShadowFenrir() {
        SculptedBone root = new SculptedBone("root", 0, 0, 0);
        SculptedBone body = root.addChild(new SculptedBone("body", 0, 1.00f, 0));
        MeshBuilder bm = new MeshBuilder();
        Ring[] wolfR = {
                new Ring(0, -0.02f, -0.84f, 0.36f, 0.34f, 0f, 0.0f), // Broad muscular pelvis so hind legs stay 100% locked inside!
                new Ring(0, -0.04f, -0.25f, 0.38f, 0.36f, 0f, 0.35f),
                new Ring(0,  0.04f,  0.35f, 0.47f, 0.45f, 0f, 0.72f),
                new Ring(0,  0.08f,  0.78f, 0.48f, 0.46f, 0f, 1.0f)
        };
        bm.addLoft(wolfR, 4, UvRect.SKIN, UvRect.BELLY_ARMOR, true, true);
        bm.addBox(0f, 0.44f,  0.42f, 0.92f, 0.24f, 0.68f, UvRect.SKIN);
        bm.addBox(0f, 0.36f, -0.34f, 0.68f, 0.18f, 0.74f, UvRect.SKIN);
        Ring[] collar = { new Ring(0, 0.08f, 0.64f, 0.51f, 0.49f, 0, 0f), new Ring(0, 0.08f, 0.78f, 0.49f, 0.47f, 0, 1f) };
        bm.addLoft(collar, 4, UvRect.METAL_GOLD, null, false, false);
        body.baseMesh = bm.build();

        SculptedBone neck = body.addChild(new SculptedBone("neck", 0, 0.14f, 0.66f, -0.26f, 0, 0));
        neck.baseMesh = addTelescopingTubeZ(new MeshBuilder(), 0.56f, 0.30f, 0.25f, UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();
        SculptedBone head = neck.addChild(MeshBuilder.buildAnatomicalHead(
                "head", 0, 0.04f, 0.52f, 1.02f, 0.60f, 0.31f, 0.27f,
                UvRect.SKIN, UvRect.MAW_DARK, UvRect.EYE_MAIN, UvRect.EYE_MAIN, null, false, false));
        head.xRot = 0.18f;
        SculptedBone jaw = head.getChild("head_jaw");
        MeshBuilder eh = new MeshBuilder();
        eh.addCurvedSpike( 0.20f, 0.24f, -0.04f,  0.26f, 0.46f, -0.08f,  0.28f, 0.60f, -0.10f, 0.082f, 3, 4, UvRect.SKIN);
        eh.addCurvedSpike(-0.20f, 0.24f, -0.04f, -0.26f, 0.46f, -0.08f, -0.28f, 0.60f, -0.10f, 0.082f, 3, 4, UvRect.SKIN);
        eh.addCurvedSpike( 0.24f, 0.22f, -0.08f,  0.44f, 0.38f, -0.45f,  0.54f, 0.48f, -0.84f, 0.068f, 4, 4, UvRect.HORN_BONE);
        eh.addCurvedSpike(-0.24f, 0.22f, -0.08f, -0.44f, 0.38f, -0.45f, -0.54f, 0.48f, -0.84f, 0.068f, 4, 4, UvRect.HORN_BONE);
        head.addChild(new SculptedBone("eh", 0, 0, 0)).baseMesh = eh.build();

        SculptedBone t1 = body.addChild(new SculptedBone("t1", 0, 0.08f, -0.74f, -0.30f, 0, 0));
        t1.baseMesh = addTelescopingTubeZ(new MeshBuilder(), -0.58f, 0.18f, 0.20f, UvRect.SKIN, null, false).build();
        SculptedBone t2 = t1.addChild(new SculptedBone("t2", 0, 0, -0.56f, 0.22f, 0, 0));
        t2.baseMesh = addTelescopingTubeZ(new MeshBuilder(), -0.60f, 0.20f, 0.06f, UvRect.SKIN, null, true).build();

        // Anatomical 3-Joint Direwolf Legs + 4 Individually Articulated Dual-Joint Moving Clawed Toes per Paw!
        SculptedBone[] uLegs = new SculptedBone[4], lLegs = new SculptedBone[4], paws = new SculptedBone[4];
        SculptedBone[][][] legToes = new SculptedBone[4][][];
        float[][] lp = { {0.33f, -0.02f, 0.54f}, {-0.33f, -0.02f, 0.54f}, {0.28f, -0.02f, -0.54f}, {-0.28f, -0.02f, -0.54f} };
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            SculptedBone u = body.addChild(new SculptedBone("leg" + i, lp[i][0], lp[i][1], lp[i][2], front ? 0.14f : -0.24f, 0, 0));
            u.baseMesh = addAnatomicalUpperLimbY(new MeshBuilder(), 0.46f, front ? 0.19f : 0.21f, front ? 0.21f : 0.24f, 0.135f, UvRect.SKIN, true).build();

            SculptedBone l = u.addChild(new SculptedBone("low" + i, 0, -0.46f, 0, front ? -0.24f : 0.44f, 0, 0));
            MeshBuilder lm = addAnatomicalLowerLimbY(new MeshBuilder(), 0.40f, 0.135f, 0.145f, 0.105f, UvRect.SKIN);
            if (front) lm.addBox(0f, -0.25f, 0f, 0.26f, 0.13f, 0.26f, UvRect.METAL_GOLD);
            l.baseMesh = lm.build();

            SculptedBone p = l.addChild(new SculptedBone("paw" + i, 0, -0.40f, 0, front ? 0.10f : -0.20f, 0, 0));
            legToes[i] = buildArticulatedPawToes(p, "sf" + i, 0.96f, UvRect.SKIN, false);
            uLegs[i] = u; lLegs[i] = l; paws[i] = p;
        }

        return new SculptedBossRig<>() {
            @Override public SculptedBone root() { return root; }
            @Override public void animate(AbyssalKnightBossEntity e, float ls, float lsa, float age, float ny, float hp, BossAnimState st) {
                float mv = BossAnimState.ease(st.moveBlend);
                float wc = st.gaitPhase * 1.35f;
                float br = Mth.sin(age * 0.11f);
                float cast = BossAnimState.ease(st.castBlend);
                float sl = st.slashCurve();
                float turn = st.turnRate;

                // Athletic predator wolf lope: energetic chest heave, hip roll & forward pursuit crouch!
                float strideBounce = Math.abs(Mth.sin(wc)) * 0.105f * mv;
                float stridePitch = Mth.cos(wc * 2f - 0.35f) * 0.075f * mv;
                body.y = 1.00f + br * 0.022f + strideBounce - mv * 0.04f - cast * 0.05f;
                body.xRot = br * 0.025f + stridePitch + mv * 0.05f - cast * 0.18f + sl * 0.16f;
                body.yRot = Mth.sin(wc) * 0.06f * mv + turn * 0.14f;
                body.zRot = Mth.cos(wc) * 0.065f * mv;

                // Diagonal rotary wolf gait (front-L + hind-R slightly offset for natural apex predator feel)
                float[] phs = { wc, wc + Mth.PI, wc + Mth.PI - 0.55f, wc - 0.55f };
                for (int i = 0; i < 4; i++) {
                    animateQuadLeg(uLegs[i], lLegs[i], paws[i], i < 2, phs[i], mv, (i % 2 == 0) ? br : -br);
                    animatePawToes(legToes[i], phs[i], mv, age, i * 1.4f, 0f);
                }

                // Counter-stabilized predator head lock (stays fiercely level & locked onto prey while shoulders heave!)
                float yaw = -ny * Mth.DEG_TO_RAD;
                neck.yRot = yaw * 0.50f + turn * 0.24f + Mth.sin(age * 0.08f) * 0.06f - Mth.sin(wc) * 0.05f * mv;
                neck.xRot = -0.26f - stridePitch * 0.65f + mv * 0.10f - cast * 0.30f + sl * 0.22f + br * 0.035f;
                head.yRot = yaw * 0.42f + turn * 0.14f;
                head.xRot = 0.18f - stridePitch * 0.35f + hp * Mth.DEG_TO_RAD * 0.45f - cast * 0.14f;
                jaw.xRot = Mth.lerp(cast, 0.16f + Math.abs(br) * 0.10f + mv * 0.14f + Math.max(0f, sl) * 0.62f, 0.78f + Mth.sin(age * 0.45f) * 0.12f);

                // Fluid counterweight wolf tail whipping opposite to turns and pumping with the lope!
                t1.yRot = -turn * 0.38f + Mth.sin(age * 0.15f + wc * 0.4f) * (0.24f + mv * 0.18f);
                t1.xRot = -0.30f - mv * 0.18f + Mth.cos(wc * 2f) * 0.09f * mv;
                t2.yRot = -turn * 0.45f + Mth.sin(age * 0.15f + wc * 0.4f - 0.85f) * (0.34f + mv * 0.24f);
            }
        };
    }

    // =========================================================================
    // 2. COLOSSAL BROODMOTHER SPIDER — Locked Coxa Sockets + Telescoping 8 Legs!
    // =========================================================================
    public static SculptedBossRig<CryptLichBossEntity> createBroodmotherSpider() {
        SculptedBone root = new SculptedBone("root", 0, 0, 0);
        SculptedBone body = root.addChild(new SculptedBone("body", 0, 0.58f, 0));
        MeshBuilder thorax = new MeshBuilder();
        thorax.addBox(0, 0.04f, 0.08f, 0.76f, 0.48f, 0.88f, UvRect.SKIN, UvRect.SKIN, UvRect.BELLY_ARMOR);
        thorax.addBox(0, 0.30f, 0.08f, 0.60f, 0.14f, 0.72f, UvRect.SKIN);
        thorax.addChamferedBox(0, 0.10f, -0.34f, 0.30f, 0.26f, 0.28f, UvRect.SKIN, UvRect.BELLY_ARMOR);
        body.baseMesh = thorax.build();

        SculptedBone ab = body.addChild(new SculptedBone("abdomen", 0, 0.12f, -0.32f, -0.14f, 0, 0));
        MeshBuilder abB = new MeshBuilder(), abG = new MeshBuilder();
        abB.addChamferedBox(0, 0.04f, 0.04f, 0.30f, 0.26f, 0.26f, UvRect.SKIN, UvRect.BELLY_ARMOR);
        Ring[] abRings = {
                new Ring(0, 0.04f,  0.14f, 0.30f, 0.26f, 0f, 0.00f),
                new Ring(0, 0.14f, -0.34f, 0.54f, 0.44f, 0f, 0.28f),
                new Ring(0, 0.18f, -0.80f, 0.62f, 0.50f, 0f, 0.62f),
                new Ring(0, 0.12f, -1.26f, 0.46f, 0.38f, 0f, 0.86f),
                new Ring(0, 0.06f, -1.52f, 0.22f, 0.18f, 0f, 1.00f)
        };
        abB.addLoft(abRings, 4, UvRect.SKIN, UvRect.BELLY_ARMOR, true, true);
        abB.addBox(0, 0.60f, -0.44f, 0.82f, 0.16f, 0.34f, UvRect.SKIN);
        abB.addBox(0, 0.66f, -0.84f, 0.92f, 0.18f, 0.38f, UvRect.SKIN);
        abB.addBox(0, 0.54f, -1.20f, 0.66f, 0.16f, 0.32f, UvRect.SKIN);
        for (int s = 0; s < 3; s++) {
            float sz = -0.42f - s * 0.38f;
            abB.addCurvedSpike(0, 0.72f, sz, 0, 0.94f, sz - 0.14f, 0, 1.00f, sz - 0.28f, 0.06f, 3, 4, UvRect.HORN_BONE);
        }
        // Bioluminescent glowing cyan venom sacs (pure energy glow, NO eyeball pupil slits!)
        abB.addBox( 0.60f, 0.16f, -0.68f, 0.20f, 0.26f, 0.36f, UvRect.MAW_GLOW);
        abG.addBox( 0.61f, 0.16f, -0.68f, 0.22f, 0.28f, 0.38f, UvRect.MAW_GLOW);
        abB.addBox(-0.60f, 0.16f, -0.68f, 0.20f, 0.26f, 0.36f, UvRect.MAW_GLOW);
        abG.addBox(-0.61f, 0.16f, -0.68f, 0.22f, 0.28f, 0.38f, UvRect.MAW_GLOW);
        abB.addBox( 0.48f, 0.14f, -1.08f, 0.16f, 0.22f, 0.28f, UvRect.MAW_GLOW);
        abG.addBox( 0.49f, 0.14f, -1.08f, 0.18f, 0.24f, 0.30f, UvRect.MAW_GLOW);
        abB.addBox(-0.48f, 0.14f, -1.08f, 0.16f, 0.22f, 0.28f, UvRect.MAW_GLOW);
        abG.addBox(-0.49f, 0.14f, -1.08f, 0.18f, 0.24f, 0.30f, UvRect.MAW_GLOW);
        abG.addBox(0, 0.76f, -0.84f, 0.24f, 0.06f, 0.44f, UvRect.MAW_GLOW);
        ab.baseMesh = abB.build(); ab.glowMesh = abG.build();

        SculptedBone head = body.addChild(new SculptedBone("head", 0, 0.02f, 0.44f));
        MeshBuilder hB = new MeshBuilder(), hG = new MeshBuilder();
        hB.addBox(0, 0.04f, 0.16f, 0.66f, 0.46f, 0.62f, UvRect.SKIN);
        hB.addBox(0, 0.28f, 0.16f, 0.56f, 0.12f, 0.48f, UvRect.SKIN);
        float[][] eyePos = {
                {-0.12f, 0.06f, 0.48f, 0.12f}, {0.12f, 0.06f, 0.48f, 0.12f},
                {-0.25f, 0.10f, 0.46f, 0.08f}, {0.25f, 0.10f, 0.46f, 0.08f},
                {-0.08f, 0.18f, 0.47f, 0.07f}, {0.08f, 0.18f, 0.47f, 0.07f}
        };
        for (float[] ep : eyePos) {
            hB.addBox(ep[0], ep[1], ep[2], ep[3], ep[3], 0.06f, UvRect.EYE_MAIN);
            hG.addBox(ep[0], ep[1], ep[2] + 0.01f, ep[3] * 1.06f, ep[3] * 1.06f, 0.07f, UvRect.EYE_MAIN);
        }
        head.baseMesh = hB.build(); head.glowMesh = hG.build();
        SculptedBone fangL = head.addChild(new SculptedBone("fangL", 0.14f, -0.06f, 0.44f));
        fangL.baseMesh = new MeshBuilder().addCurvedSpike(0, 0, 0, 0.08f, -0.14f, 0.24f, -0.04f, -0.32f, 0.18f, 0.07f, 3, 4, UvRect.HORN_BONE).build();
        SculptedBone fangR = head.addChild(new SculptedBone("fangR", -0.14f, -0.06f, 0.44f));
        fangR.baseMesh = new MeshBuilder().addCurvedSpike(0, 0, 0, -0.08f, -0.14f, 0.24f, 0.04f, -0.32f, 0.18f, 0.07f, 3, 4, UvRect.HORN_BONE).build();

        // 8 Anatomical Arachnid Legs (Muscular Femur -> Spiked Patella Knee -> Tapered Tibia -> Articulated Metatarsus + Dual Moving Tarsal Claws!)
        float[] zOff = { 0.34f, 0.14f, -0.08f, -0.28f };
        float[] yAng = { -0.70f, -0.25f, 0.24f, 0.70f };
        SculptedBone[][] fem = new SculptedBone[2][4], tib = new SculptedBone[2][4], tip = new SculptedBone[2][4];
        SculptedBone[][][] spClaws = new SculptedBone[2][4][2];
        for (int sIdx = 0; sIdx < 2; sIdx++) {
            int side = (sIdx == 0) ? 1 : -1;
            for (int i = 0; i < 4; i++) {
                SculptedBone f = body.addChild(new SculptedBone("f" + sIdx + "_" + i, side * 0.24f, -0.02f, zOff[i], 0, side * yAng[i], side * 0.76f));
                MeshBuilder fm = new MeshBuilder();
                fm.addChamferedBox(0, 0, 0, 0.135f, 0.13f, 0.135f, UvRect.SKIN);
                fm.addLoft(new Ring[] {
                        new Ring(-side * 0.12f, 0, 0, 0.10f, 0.10f, 0, 0.00f),
                        new Ring( side * 0.28f, 0.02f, 0, 0.125f, 0.115f, 0, 0.35f), // Muscular femur bulge
                        new Ring( side * 0.86f, 0.00f, 0, 0.082f, 0.080f, 0, 0.90f),
                        new Ring( side * 0.94f, 0.00f, 0, 0.075f, 0.075f, 0, 1.00f)
                }, 4, UvRect.SKIN, null, true, true);
                fm.addBox(side * 0.86f, 0.02f, 0f, 0.21f, 0.21f, 0.21f, UvRect.SKIN);
                fm.addCurvedSpike(side * 0.82f, 0.08f, 0, side * 0.92f, 0.25f, 0, side * 1.02f, 0.34f, 0, 0.044f, 3, 4, UvRect.HORN_BONE);
                f.baseMesh = fm.build();

                SculptedBone t = f.addChild(new SculptedBone("t" + sIdx + "_" + i, side * 0.86f, 0, 0, 0, 0, side * -1.40f));
                MeshBuilder tm = new MeshBuilder();
                tm.addChamferedBox(0, 0, 0, 0.105f, 0.105f, 0.105f, UvRect.SKIN);
                tm.addLoft(new Ring[] {
                        new Ring(-side * 0.10f, 0, 0, 0.085f, 0.085f, 0, 0.00f),
                        new Ring( side * 0.24f, 0.01f, 0, 0.090f, 0.086f, 0, 0.34f), // Muscular tibia belly
                        new Ring( side * 0.76f, 0.00f, 0, 0.058f, 0.058f, 0, 0.90f),
                        new Ring( side * 0.84f, 0.00f, 0, 0.052f, 0.052f, 0, 1.00f)
                }, 4, UvRect.SKIN, null, true, true);
                tm.addCurvedSpike(side * 0.46f, -0.05f, 0, side * 0.56f, -0.14f, 0, side * 0.64f, -0.20f, 0, 0.026f, 2, 4, UvRect.HORN_BONE);
                t.baseMesh = tm.build();

                SculptedBone p = t.addChild(new SculptedBone("p" + sIdx + "_" + i, side * 0.76f, 0, 0, 0, 0, side * -0.40f));
                MeshBuilder pm = new MeshBuilder();
                pm.addChamferedBox(0, 0, 0, 0.075f, 0.075f, 0.075f, UvRect.SKIN);
                pm.addLoft(new Ring[] {
                        new Ring(-side * 0.08f, 0, 0, 0.062f, 0.062f, 0, 0.0f),
                        new Ring( side * 0.42f, -0.02f, 0, 0.044f, 0.044f, 0, 1.0f)
                }, 4, UvRect.SKIN, null, true, true);
                p.baseMesh = pm.build();

                // Dual articulated gripping arachnid tarsal claws at each leg tip!
                for (int c = 0; c < 2; c++) {
                    float cz = (c == 0) ? 0.028f : -0.028f;
                    float cyRot = (c == 0) ? 0.24f : -0.24f;
                    SculptedBone cl = p.addChild(new SculptedBone("sc" + sIdx + "_" + i + "_" + c, side * 0.40f, -0.02f, cz, 0, side * cyRot, 0));
                    cl.baseMesh = new MeshBuilder().addCurvedSpike(-side * 0.03f, 0, 0, side * 0.14f, -0.02f, cz * 0.8f, side * 0.28f, -0.08f, cz * 1.4f, 0.032f, 3, 4, UvRect.HORN_BONE).build();
                    spClaws[sIdx][i][c] = cl;
                }
                fem[sIdx][i] = f; tib[sIdx][i] = t; tip[sIdx][i] = p;
            }
        }

        return new SculptedBossRig<>() {
            @Override public SculptedBone root() { return root; }
            @Override public void animate(CryptLichBossEntity e, float ls, float lsa, float age, float ny, float hp, BossAnimState st) {
                float mv = BossAnimState.ease(st.moveBlend);
                float wc = st.gaitPhase * 1.75f;
                float br = Mth.sin(age * 0.12f);
                float cast = BossAnimState.ease(st.castBlend);
                float sl = st.slashCurve();
                float turn = st.turnRate;

                // Creepy hydraulic arachnid scuttle: low predatory stalk, subtle rapid body jitter & swaying venom abdomen!
                body.y = 0.58f + br * 0.025f - mv * 0.04f + Math.abs(Mth.sin(wc * 2f)) * 0.035f * mv + cast * 0.14f;
                body.xRot = mv * 0.06f - cast * 0.26f + sl * 0.12f;
                body.yRot = Mth.sin(wc) * 0.05f * mv + turn * 0.10f;
                body.zRot = Mth.cos(wc) * 0.055f * mv;
                head.yRot = -ny * Mth.DEG_TO_RAD * 0.52f + turn * 0.18f;
                head.xRot = hp * Mth.DEG_TO_RAD * 0.28f;
                ab.xRot = -0.14f + br * 0.07f + Mth.sin(wc * 2f - 0.6f) * 0.06f * mv + cast * 0.16f;
                ab.yRot = -turn * 0.42f + Mth.sin(age * 0.10f + wc * 0.5f) * (0.06f + mv * 0.10f);

                for (int i = 0; i < 4; i++) {
                    float wave = i * 0.72f;
                    float pL = wc + (i % 2 == 0 ? 0f : Mth.PI) + wave;
                    float pR = wc + (i % 2 == 0 ? Mth.PI : 0f) + wave;
                    float idleL = Mth.sin(age * 0.11f + i * 1.3f) * 0.04f;
                    float idleR = Mth.sin(age * 0.11f + i * 1.3f + Mth.PI) * 0.04f;

                    float liftL = Math.max(0f, Mth.sin(pL)); liftL = liftL * liftL * (3f - 2f * liftL);
                    float liftR = Math.max(0f, Mth.sin(pR)); liftR = liftR * liftR * (3f - 2f * liftR);
                    float sL = Mth.cos(pL) * 0.42f * mv, uL = liftL * 0.48f * mv;
                    float sR = Mth.cos(pR) * 0.42f * mv, uR = liftR * 0.48f * mv;
                    float rear = (i == 0) ? (cast * (0.68f + Mth.sin(age * 0.38f) * 0.16f) + Math.max(0f, sl) * 0.50f) : (i == 1 ? cast * 0.22f : 0f);
                    float rearR = (i == 0) ? (cast * (0.68f - Mth.sin(age * 0.38f) * 0.16f) + Math.max(0f, sl) * 0.38f) : (i == 1 ? cast * 0.22f : 0f);

                    fem[0][i].yRot = yAng[i] + sL - rear * 0.22f;
                    fem[0][i].zRot = 0.76f + uL + idleL + rear;
                    tib[0][i].zRot = -1.40f - uL * 0.58f - idleL * 0.7f + rear * 0.38f;
                    tip[0][i].zRot = -0.40f + uL * 0.48f - Math.max(0f, -Mth.sin(pL)) * 0.16f * mv - rear * 0.32f;

                    fem[1][i].yRot = -yAng[i] - sR + rearR * 0.22f;
                    fem[1][i].zRot = -0.76f - uR - idleR - rearR;
                    tib[1][i].zRot = 1.40f + uR * 0.58f + idleR * 0.7f - rearR * 0.38f;
                    tip[1][i].zRot = 0.40f - uR * 0.48f + Math.max(0f, -Mth.sin(pR)) * 0.16f * mv + rearR * 0.32f;

                    float gripL = 0.18f + uL * 0.72f + rear * 0.52f + Mth.sin(age * 0.18f + i * 1.1f) * 0.09f;
                    float gripR = 0.18f + uR * 0.72f + rearR * 0.52f + Mth.cos(age * 0.18f + i * 1.1f) * 0.09f;
                    spClaws[0][i][0].yRot =  gripL; spClaws[0][i][1].yRot = -gripL;
                    spClaws[0][i][0].zRot = -uL * 0.48f; spClaws[0][i][1].zRot = -uL * 0.48f;
                    spClaws[1][i][0].yRot = -gripR; spClaws[1][i][1].yRot =  gripR;
                    spClaws[1][i][0].zRot =  uR * 0.48f; spClaws[1][i][1].zRot =  uR * 0.48f;
                }
                float ch = Math.abs(Mth.sin(age * 0.26f)) * (0.24f + mv * 0.12f) + cast * 0.22f + Math.max(0f, sl) * 0.30f;
                fangL.yRot = ch; fangR.yRot = -ch;
                fangL.xRot = -ch * 0.30f; fangR.xRot = -ch * 0.30f;
            }
        };
    }

    // =========================================================================
    // 3. VOLCANIC 3-HEADED MAGMA HYDRA — Deeply Locked 3-Joint Legs & Telescoping Necks!
    // =========================================================================
    public static SculptedBossRig<MoltenTitanBossEntity> createVolcanicHydra() {
        SculptedBone root = new SculptedBone("root", 0, 0, 0);
        SculptedBone body = root.addChild(new SculptedBone("body", 0, 0.90f, 0));
        MeshBuilder bm = new MeshBuilder();
        Ring[] br = {
                new Ring(0, -0.02f, -0.84f, 0.50f, 0.42f, 0f, 0f),
                new Ring(0,  0.04f,  0.00f, 0.60f, 0.48f, 0f, 0.5f),
                new Ring(0,  0.10f,  0.78f, 0.56f, 0.46f, 0f, 1.0f)
        };
        bm.addLoft(br, 4, UvRect.SKIN, UvRect.BELLY_ARMOR, true, true);
        bm.addBox(0f, 0.56f,  0.40f, 0.92f, 0.20f, 0.58f, UvRect.SKIN);
        bm.addBox(0f, 0.54f, -0.24f, 0.84f, 0.20f, 0.60f, UvRect.SKIN);
        for (int k = 0; k < 4; k++) {
            float kz = -0.58f + k * 0.40f;
            bm.addCurvedSpike(0, 0.64f, kz, 0, 0.90f, kz - 0.14f, 0, 1.00f, kz - 0.28f, 0.08f, 3, 4, UvRect.HORN_BONE);
        }
        body.baseMesh = bm.build();
        body.glowMesh = body.baseMesh;

        SculptedBone[] n1s = new SculptedBone[3], n2s = new SculptedBone[3], hds = new SculptedBone[3], jaws = new SculptedBone[3];
        float[] ox = { 0f, 0.34f, -0.34f }, bRy = { 0f, 0.50f, -0.50f };
        for (int i = 0; i < 3; i++) {
            SculptedBone n1 = body.addChild(new SculptedBone("n1_" + i, ox[i], 0.20f, 0.60f, -0.78f, bRy[i], 0));
            MeshBuilder nm1 = addTelescopingTubeZ(new MeshBuilder(), 0.66f, 0.22f, 0.19f, UvRect.SKIN, UvRect.BELLY_ARMOR, false);
            nm1.addBox(0, 0.22f, 0.34f, 0.16f, 0.14f, 0.34f, UvRect.SKIN);
            SculptedMesh m1 = nm1.build();
            n1.baseMesh = m1; n1.glowMesh = m1;

            SculptedBone n2 = n1.addChild(new SculptedBone("n2_" + i, 0, 0, 0.64f, 0.42f, 0, 0));
            MeshBuilder nm2 = addTelescopingTubeZ(new MeshBuilder(), 0.62f, 0.19f, 0.17f, UvRect.SKIN, UvRect.BELLY_ARMOR, false);
            nm2.addBox(0, 0.19f, 0.32f, 0.14f, 0.12f, 0.30f, UvRect.SKIN);
            SculptedMesh m2 = nm2.build();
            n2.baseMesh = m2; n2.glowMesh = m2;

            SculptedBone hd = n2.addChild(MeshBuilder.buildAnatomicalHead(
                    "hd_" + i, 0, 0.02f, 0.60f, 1.02f, 0.58f, 0.31f, 0.27f,
                    UvRect.SKIN, UvRect.MAW_GLOW, UvRect.EYE_MAIN, UvRect.EYE_MAIN, null, true, true));
            hd.xRot = 0.28f;
            MeshBuilder hm = new MeshBuilder();
            hm.addCurvedSpike( 0.22f, 0.22f, -0.05f,  0.40f, 0.40f, -0.38f,  0.48f, 0.48f, -0.74f, 0.068f, 4, 4, UvRect.HORN_BONE);
            hm.addCurvedSpike(-0.22f, 0.22f, -0.05f, -0.40f, 0.40f, -0.38f, -0.48f, 0.48f, -0.74f, 0.068f, 4, 4, UvRect.HORN_BONE);
            hd.addChild(new SculptedBone("h_" + i, 0, 0, 0)).baseMesh = hm.build();
            n1s[i] = n1; n2s[i] = n2; hds[i] = hd; jaws[i] = hd.getChild("hd_" + i + "_jaw");
        }

        SculptedBone t1 = body.addChild(new SculptedBone("t1", 0, 0.0f, -0.74f, -0.14f, 0, 0));
        SculptedMesh tm1 = addTelescopingTubeZ(new MeshBuilder(), -0.76f, 0.30f, 0.18f, UvRect.SKIN, UvRect.BELLY_ARMOR, false).build();
        t1.baseMesh = tm1; t1.glowMesh = tm1;
        SculptedBone t2 = t1.addChild(new SculptedBone("t2", 0, 0, -0.74f, 0.10f, 0, 0));
        SculptedMesh tm2 = addTelescopingTubeZ(new MeshBuilder(), -0.80f, 0.18f, 0.06f, UvRect.SKIN, UvRect.BELLY_ARMOR, true).build();
        t2.baseMesh = tm2; t2.glowMesh = tm2;

        // Anatomical Draconic Magma Legs + 4 Individually Articulated Dual-Joint Moving Clawed Toes per Foot!
        SculptedBone[] uLegs = new SculptedBone[4], lLegs = new SculptedBone[4], paws = new SculptedBone[4];
        SculptedBone[][][] legToes = new SculptedBone[4][][];
        float[][] lp = { {0.42f, -0.02f, 0.48f}, {-0.42f, -0.02f, 0.48f}, {0.38f, -0.02f, -0.48f}, {-0.38f, -0.02f, -0.48f} };
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            SculptedBone u = body.addChild(new SculptedBone("l" + i, lp[i][0], lp[i][1], lp[i][2], front ? 0.14f : -0.24f, 0, 0));
            MeshBuilder umB = addAnatomicalUpperLimbY(new MeshBuilder(), 0.44f, 0.21f, 0.24f, 0.155f, UvRect.SKIN, true);
            umB.addBox(0f, 0.04f, 0f, 0.44f, 0.24f, 0.44f, UvRect.SKIN);
            SculptedMesh um = umB.build();
            u.baseMesh = um; u.glowMesh = um;

            SculptedBone l = u.addChild(new SculptedBone("lo" + i, 0, -0.44f, 0, front ? -0.24f : 0.44f, 0, 0));
            l.baseMesh = addAnatomicalLowerLimbY(new MeshBuilder(), 0.34f, 0.155f, 0.165f, 0.125f, UvRect.SKIN).build();
            l.glowMesh = l.baseMesh;

            SculptedBone p = l.addChild(new SculptedBone("fp" + i, 0, -0.34f, 0, front ? 0.10f : -0.20f, 0, 0));
            legToes[i] = buildArticulatedPawToes(p, "mh" + i, 1.12f, UvRect.SKIN, true);
            uLegs[i] = u; lLegs[i] = l; paws[i] = p;
        }

        return new SculptedBossRig<>() {
            @Override public SculptedBone root() { return root; }
            @Override public void animate(MoltenTitanBossEntity e, float ls, float lsa, float age, float ny, float hp, BossAnimState st) {
                float mv = BossAnimState.ease(st.moveBlend);
                float wc = st.gaitPhase * 1.15f;
                float br = Mth.sin(age * 0.10f);
                float cast = BossAnimState.ease(st.castBlend);
                float sl = st.slashCurve();
                float turn = st.turnRate;

                // Heavy seismic Magma-Hydra lumbering stomp with shoulder roll & hip sway!
                float stompY = Math.abs(Mth.sin(wc)) * 0.085f * mv;
                body.y = 0.90f + br * 0.025f + stompY;
                body.zRot = Mth.cos(wc) * 0.075f * mv;
                body.yRot = Mth.sin(wc) * 0.08f * mv + turn * 0.12f;
                body.xRot = br * 0.025f + Mth.cos(wc * 2f) * 0.045f * mv - cast * 0.12f;

                float[] phs = { wc, wc + Mth.PI, wc + Mth.PI - 0.45f, wc - 0.45f };
                for (int i = 0; i < 4; i++) {
                    animateQuadLeg(uLegs[i], lLegs[i], paws[i], i < 2, phs[i], mv, (i % 2 == 0) ? br : -br);
                    animatePawToes(legToes[i], phs[i], mv, age, i * 1.6f, 0f);
                }

                float yaw = -ny * Mth.DEG_TO_RAD * 0.45f;
                for (int i = 0; i < 3; i++) {
                    float ph = i * 2.1f;
                    float strike = (i == 0 ? sl : sl * 0.5f);
                    float strideWeave = Mth.sin(wc + ph) * 0.16f * mv;
                    n1s[i].yRot = bRy[i] + yaw * 0.45f + turn * 0.22f + Mth.sin(age * 0.11f + ph) * 0.18f + strideWeave;
                    n2s[i].yRot = Mth.sin(age * 0.11f + ph - 0.8f) * 0.22f - strideWeave * 0.65f;
                    n1s[i].xRot = -0.78f - cast * 0.22f + strike * 0.28f + Mth.cos(age * 0.11f + ph) * 0.08f + Mth.cos(wc + ph) * 0.08f * mv;
                    n2s[i].xRot = 0.42f + cast * 0.10f - strike * 0.14f + Mth.cos(age * 0.11f + ph - 0.6f) * 0.07f;
                    hds[i].xRot = 0.28f + hp * Mth.DEG_TO_RAD * 0.40f;
                    jaws[i].xRot = Mth.lerp(cast, 0.28f + Math.abs(Mth.sin(age * 0.18f + ph)) * 0.18f + Math.max(0f, strike) * 0.48f, 0.84f + Mth.sin(age * 0.38f + ph) * 0.12f);
                }
                t1.yRot = -turn * 0.35f + Mth.sin(age * 0.11f + wc * 0.5f) * (0.22f + mv * 0.18f);
                t2.yRot = -turn * 0.42f + Mth.sin(age * 0.11f + wc * 0.5f - 0.85f) * (0.34f + mv * 0.22f);
            }
        };
    }

}
