package com.abyssalrpg.client;

public final class ClientRpgState {
    // Boss spell camera shake state (damped client-side impulse)
    public static float shakeIntensity = 0f;
    public static int shakeTicks = 0;
    public static int shakeMaxTicks = 1;

    private ClientRpgState() {}

    public static void triggerShake(float intensity, int durationTicks) {
        if (durationTicks <= 0 || intensity <= 0f) return;
        if (intensity >= shakeIntensity || shakeTicks <= 0) {
            shakeIntensity = Math.min(3.5f, intensity);
            shakeTicks = Math.min(40, durationTicks);
            shakeMaxTicks = shakeTicks;
        }
    }
}
