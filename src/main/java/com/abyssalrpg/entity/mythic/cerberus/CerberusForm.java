package com.abyssalrpg.entity.mythic.cerberus;

import net.minecraft.ChatFormatting;

/**
 * Bốn hình dạng (form) của Boss Cerberus đồng bộ qua SynchedEntityData:
 * - TRIPLE:      Phase 1 — dạng 3 đầu nguyên bản.
 * - ALPHA_HEAD:  Phase 2 — cá thể thủ lĩnh cận chiến hung hãn (mắt đỏ).
 * - FIRE_HEAD:   Phase 2 — cá thể tầm xa lửa địa ngục (mắt cam).
 * - SHADOW_HEAD: Phase 2 — cá thể tốc độ & ám sát bóng tối (mắt tím).
 */
public enum CerberusForm {
    TRIPLE(0, false, "cerberus", 0xFF6A14, 0.0f, ChatFormatting.GOLD, "Hound of the Underworld"),
    ALPHA_HEAD(1, true, "cerberus_alpha", 0xFF2430, 0.0f, ChatFormatting.RED, "Alpha — Wrath of the Pack"),
    FIRE_HEAD(2, true, "cerberus_fire", 0xFF8C1A, 120.0f, ChatFormatting.GOLD, "Pyre — Hellfire Maw"),
    SHADOW_HEAD(3, true, "cerberus_shadow", 0xB832FF, -120.0f, ChatFormatting.DARK_PURPLE, "Umbra — Phantom Fang");

    private final int id;
    private final boolean splitSingle;
    private final String textureKey;
    private final int accentRgb;
    private final float flankAngleDeg;
    private final ChatFormatting chatColor;
    private final String roleSubtitle;

    CerberusForm(int id, boolean splitSingle, String textureKey, int accentRgb,
                 float flankAngleDeg, ChatFormatting chatColor, String roleSubtitle) {
        this.id = id;
        this.splitSingle = splitSingle;
        this.textureKey = textureKey;
        this.accentRgb = accentRgb;
        this.flankAngleDeg = flankAngleDeg;
        this.chatColor = chatColor;
        this.roleSubtitle = roleSubtitle;
    }

    public int id() { return id; }
    public boolean isSplitSingle() { return splitSingle; }
    public String textureKey() { return textureKey; }
    public int accentRgb() { return accentRgb; }
    public float flankAngleDeg() { return flankAngleDeg; }
    public ChatFormatting chatColor() { return chatColor; }
    public String roleSubtitle() { return roleSubtitle; }

    public static CerberusForm byId(int id) {
        for (CerberusForm f : values()) {
            if (f.id == id) return f;
        }
        return TRIPLE;
    }
}

