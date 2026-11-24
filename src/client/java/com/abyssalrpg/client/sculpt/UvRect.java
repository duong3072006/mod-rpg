package com.abyssalrpg.client.sculpt;

/**
 * Normalized UV region inside a Boss's 512x512 4-Quadrant High-Def Material Atlas.
 */
public record UvRect(float u0, float v0, float u1, float v1) {
    // 4 Main 256x256 Quadrants
    public static final UvRect SKIN = new UvRect(0.0f, 0.0f, 0.5f, 0.5f);
    public static final UvRect BELLY_ARMOR = new UvRect(0.5f, 0.0f, 1.0f, 0.5f);
    public static final UvRect HORN_BONE = new UvRect(0.0f, 0.5f, 0.25f, 1.0f);
    public static final UvRect METAL_GOLD = new UvRect(0.25f, 0.5f, 0.5f, 1.0f);

    // Quadrant 3 Upper Half (v: 0.50..0.75) — Membranes, Feathers, Hoods, Gill Fins, Wings
    public static final UvRect MEMBRANE_FULL = new UvRect(0.5f, 0.5f, 1.0f, 0.75f);
    public static final UvRect FEATHER_0 = new UvRect(0.500f, 0.5f, 0.625f, 0.75f); // Magenta / Primary
    public static final UvRect FEATHER_1 = new UvRect(0.625f, 0.5f, 0.750f, 0.75f); // Cyan / Secondary
    public static final UvRect FEATHER_2 = new UvRect(0.750f, 0.5f, 0.875f, 0.75f); // Lime / Tertiary
    public static final UvRect FEATHER_3 = new UvRect(0.875f, 0.5f, 1.000f, 0.75f); // Gold / Accent

    // Quadrant 3 Lower Half (v: 0.75..1.00) — Glowing Slit-Pupil Eye Irises, Gems & Maw Interior
    public static final UvRect EYE_MAIN = new UvRect(0.500f, 0.75f, 0.625f, 0.875f);
    public static final UvRect EYE_ALT1 = new UvRect(0.625f, 0.75f, 0.750f, 0.875f); // Apep Red / Tiamat Sapphire
    public static final UvRect EYE_ALT2 = new UvRect(0.750f, 0.75f, 0.875f, 0.875f); // Apep Jade 3rd Eye
    public static final UvRect MAW_DARK = new UvRect(0.875f, 0.75f, 1.000f, 0.875f);
    public static final UvRect MAW_GLOW = new UvRect(0.500f, 0.875f, 0.750f, 1.000f); // Molten / Electric / Crimson Throat
    public static final UvRect TONGUE = new UvRect(0.750f, 0.875f, 1.000f, 1.000f);

    public float mapU(float u) { return u0 + (u1 - u0) * u; }
    public float mapV(float v) { return v0 + (v1 - v0) * v; }
}

