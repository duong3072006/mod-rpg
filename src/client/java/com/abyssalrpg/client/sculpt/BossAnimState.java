package com.abyssalrpg.client.sculpt;

import com.abyssalrpg.entity.AbstractAbyssalBoss;
import net.minecraft.util.Mth;

/**
 * Per-entity smooth kinematic & procedural locomotion state for ALL 11 sculpted bosses.
 * Computes TRUE world-space velocity (immune to Minecraft's Attributes.SCALE walk-animation slowdown),
 * smoothly integrated stride/slither phase, turn-rate banking, vertical pitch velocity, and blended
 * Idle / Sleeping / Moving / Attack / Spell-Cast weights so every boss moves organically with weight!
 */
public final class BossAnimState {
    public float lastAge = -1f;
    public double prevX = Double.NaN;
    public double prevY = Double.NaN;
    public double prevZ = Double.NaN;
    public float prevBodyYaw = Float.NaN;

    public float castBlend = 0f;
    public float cast2Blend = 0f;
    public float moveBlend = 0f;
    public float sleepBlend = 0f;
    public float spawnBlend = 0f;

    /** Continuously integrated stride / slither phase in radians (never stalls on 4x-5x scaled titans!). */
    public float gaitPhase = 0f;
    /** Smoothly filtered turn velocity in [-1..+1] (negative = turning left, positive = turning right). */
    public float turnRate = 0f;
    /** Smoothly filtered vertical velocity in [-1..+1] (positive = ascending, negative = diving). */
    public float pitchVel = 0f;

    public float attackPhase = 0f;
    public float attackSide = 1f;
    private boolean wasSwinging = false;

    public void update(AbstractAbyssalBoss e, float ageInTicks, float partialTick, float rawWalkSpeed, float bodyYaw) {
        float dt = (lastAge < 0f || ageInTicks < lastAge) ? 0.5f : Mth.clamp(ageInTicks - lastAge, 0.001f, 1.5f);
        lastAge = ageInTicks;

        double curX = Mth.lerp(partialTick, e.xo, e.getX());
        double curY = Mth.lerp(partialTick, e.yo, e.getY());
        double curZ = Mth.lerp(partialTick, e.zo, e.getZ());

        float horizSpeedPerTick = 0f;
        float vertSpeedPerTick = 0f;
        float yawDeltaPerTick = 0f;

        if (!Double.isNaN(prevX) && dt > 0.0005f) {
            double dx = curX - prevX;
            double dy = curY - prevY;
            double dz = curZ - prevZ;
            double distSq = dx * dx + dz * dz;
            if (distSq < 9.0) { // Ignore sudden teleports
                horizSpeedPerTick = (float) (Math.sqrt(distSq) / dt);
                vertSpeedPerTick = (float) (dy / dt);
            }
        }
        if (!Float.isNaN(prevBodyYaw) && dt > 0.0005f) {
            float dYaw = Mth.wrapDegrees(bodyYaw - prevBodyYaw);
            if (Math.abs(dYaw) < 60f) {
                yawDeltaPerTick = dYaw / dt;
            }
        }
        prevX = curX;
        prevY = curY;
        prevZ = curZ;
        prevBodyYaw = bodyYaw;

        boolean spawning = e.isSpawningIn();
        float spawnProg = spawning ? e.getSpawnProgress(partialTick) : 1.0f;
        int cast = e.getCastState();

        // Drive rearing necks, flaring wings & wide-open jaws as the titan breaks free from earth and roars
        float emergeStruggle = spawning ? Mth.sin(Mth.clamp(spawnProg / 0.65f, 0f, 1f) * Mth.PI) * 0.55f : 0f;
        float spawnRoar = spawning ? Mth.sin(Mth.clamp((spawnProg - 0.55f) / 0.42f, 0f, 1f) * Mth.PI) : 0f;
        float targetCast = Math.max((cast > 0 && !spawning) ? 1f : 0f, Math.max(emergeStruggle, spawnRoar));
        float targetCast2 = Math.max((cast == 2 && !spawning) ? 1f : 0f, spawnRoar);

        // Combine true world-space displacement speed (primary!) + Minecraft walkAnimation fallback
        float worldMove = Mth.clamp(horizSpeedPerTick * 6.2f, 0f, 1.0f);
        float targetMove = spawning ? 0f : Math.max(worldMove, Mth.clamp(rawWalkSpeed * 1.85f, 0f, 1.0f));
        float targetSleep = (!spawning && e.isResting() && targetMove < 0.08f && cast == 0 && e.hurtTime <= 0) ? 1f : 0f;

        float kCast  = 1f - (float) Math.exp(-0.24 * dt);
        float kMove  = 1f - (float) Math.exp(-0.22 * dt);
        float kSleep = 1f - (float) Math.exp(-0.10 * dt);
        float kTurn  = 1f - (float) Math.exp(-0.18 * dt);

        castBlend  = Mth.lerp(kCast,  castBlend,  targetCast);
        cast2Blend = Mth.lerp(kCast,  cast2Blend, targetCast2);
        moveBlend  = Mth.lerp(kMove,  moveBlend,  targetMove);
        sleepBlend = Mth.lerp(kSleep, sleepBlend, targetSleep);
        spawnBlend = spawning ? (1f - spawnProg) : Mth.lerp(kCast, spawnBlend, 0f);

        float targetTurn = Mth.clamp(yawDeltaPerTick * 0.14f, -1f, 1f);
        float targetPitch = Mth.clamp(vertSpeedPerTick * 3.2f, -1f, 1f);
        turnRate = Mth.lerp(kTurn, turnRate, targetTurn);
        pitchVel = Mth.lerp(kTurn, pitchVel, targetPitch);

        // Smooth velocity-proportional stride/slither phase integration (always fluid, never frozen on colossal scales!)
        if (!spawning) {
            float speedFactor = Math.max(moveBlend, Math.abs(turnRate) * 0.35f);
            gaitPhase += dt * (0.20f + speedFactor * 0.24f) * speedFactor;
            if (gaitPhase > Mth.TWO_PI * 1000f) {
                gaitPhase -= Mth.TWO_PI * 1000f;
            }
        }

        float swing = e.getAttackAnim(partialTick);
        boolean swingingNow = swing > 0.001f;
        if (swingingNow && !wasSwinging) {
            attackSide = -attackSide;
        }
        wasSwinging = swingingNow;
        if (swingingNow) {
            attackPhase = swing;
        } else {
            attackPhase = Mth.lerp(1f - (float) Math.exp(-0.40 * dt), attackPhase, 0f);
            if (attackPhase < 0.002f) attackPhase = 0f;
        }
    }

    public static float ease(float t) {
        float c = Mth.clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    public float slashCurve() {
        if (attackPhase <= 0.001f) return 0f;
        float p = attackPhase;
        if (p < 0.25f) {
            return -0.30f * Mth.sin((p / 0.25f) * Mth.PI);
        }
        float f = (p - 0.25f) / 0.75f;
        return Mth.sin(f * Mth.PI);
    }
}
