package com.abyssalrpg.item;

import net.minecraft.util.Mth;

/**
 * Hệ thống phong cách màu điểm sáng nhấn nổi 3D cho **Togetsu (Song Đao & Song Nguyệt)**:
 * - Đủ trọn bộ 4 Option chính (mặc định `YIN_YANG` — Option 2 Âm Dương Đối Lập)
 *   + 2 mẫu mở rộng (`EMERALD_JADE` Ngọc Lục Bảo, `PRISMATIC` Thất Sắc cầu vồng động)
 *   + chế độ `CUSTOM` cho phép người chơi tự pha bất kỳ 2 mã màu RGB (`#RRGGBB`) cho Kênh Chính & Kênh Phụ!
 */
public enum TogetsuGlowStyle {
    /** Option 2 (Mặc định): Âm Dương Đối Lập — Lưỡi Trắng Xanh Băng & Lưỡi Đen / Lõi Đen Tím Huyền Bí. */
    YIN_YANG((byte) 0, "yin_yang", 0x96E4FF, 0xC358FF, 0xF02A4E),
    /** Option 1: Nguyệt Quang Bạc – Xanh Băng chuẩn nguyên tác ánh trăng lạnh. */
    MOONLIGHT((byte) 1, "moonlight", 0x70D8FF, 0x56C8FF, 0xEB2D41),
    /** Option 3: Huyết Nguyệt — Điểm nhấn Đỏ Thẫm đồng bộ tay cầm da đỏ. */
    CRIMSON((byte) 2, "crimson", 0xFF2B46, 0xFF1F3E, 0xFF2A3A),
    /** Option 4: Thánh Nguyệt Vàng Kim & Bạc — Nổi rực rỡ cả dưới ánh nắng ban ngày. */
    CELESTIAL_GOLD((byte) 3, "celestial_gold", 0xFFCC48, 0xFFB838, 0xF5303E),
    /** Mẫu mở rộng 5: Ngọc Lục Bảo huyền ảo. */
    EMERALD_JADE((byte) 4, "emerald_jade", 0x38FFB4, 0x1CE896, 0xFFE066),
    /** Mẫu mở rộng 6: Thất Sắc Nguyệt Thực — Chuyển màu cầu vồng động 2 pha đối xứng theo thời gian thực! */
    PRISMATIC((byte) 5, "prismatic", 0x70F0FF, 0xD060FF, 0xFF4578),
    /** Chế độ Tự Pha Màu: người chơi tùy chọn 2 màu RGB bất kỳ (Màu Chính + Màu Phụ). */
    CUSTOM((byte) 6, "custom", 0x96E4FF, 0xC358FF, 0xF02A4E);

    public static final int PRESET_COUNT = 6; // 0..5 là các preset cố định/động, 6 là Custom RGB

    public final byte id;
    public final String key;
    public final int defaultPrimaryRgb;
    public final int defaultSecondaryRgb;
    public final int defaultBeadRgb;

    TogetsuGlowStyle(byte id, String key, int defaultPrimaryRgb, int defaultSecondaryRgb, int defaultBeadRgb) {
        this.id = id;
        this.key = key;
        this.defaultPrimaryRgb = defaultPrimaryRgb & 0xFFFFFF;
        this.defaultSecondaryRgb = defaultSecondaryRgb & 0xFFFFFF;
        this.defaultBeadRgb = defaultBeadRgb & 0xFFFFFF;
    }

    public String translationKey() {
        return "togetsu.glow_style.abyssalrpg." + this.key;
    }

    public static TogetsuGlowStyle byId(int id) {
        for (TogetsuGlowStyle s : values()) {
            if (s.id == id) {
                return s;
            }
        }
        return YIN_YANG;
    }

    /** Chuyển nhanh sang preset tiếp theo (0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 0). */
    public TogetsuGlowStyle nextPreset() {
        if (this == CUSTOM) {
            return YIN_YANG;
        }
        return byId((this.id + 1) % PRESET_COUNT);
    }

    public int resolvePrimaryRgb(int customPrimaryRgb, float timeSec) {
        if (this == PRISMATIC) {
            float hue = (timeSec * 0.14F) % 1.0F;
            return hsvToRgb(hue, 0.78F, 1.00F);
        }
        if (this == CUSTOM) {
            return customPrimaryRgb & 0xFFFFFF;
        }
        return this.defaultPrimaryRgb;
    }

    public int resolveSecondaryRgb(int customSecondaryRgb, float timeSec) {
        if (this == PRISMATIC) {
            float hue = (timeSec * 0.14F + 0.36F) % 1.0F;
            return hsvToRgb(hue, 0.85F, 1.00F);
        }
        if (this == CUSTOM) {
            return customSecondaryRgb & 0xFFFFFF;
        }
        return this.defaultSecondaryRgb;
    }

    public int resolveBeadRgb(int customPrimaryRgb, float timeSec) {
        if (this == PRISMATIC) {
            float hue = (timeSec * 0.14F + 0.68F) % 1.0F;
            return hsvToRgb(hue, 0.88F, 1.00F);
        }
        if (this == CUSTOM) {
            return 0xF02A4E;
        }
        return this.defaultBeadRgb;
    }

    /** Chuyển đổi HSV (0..1) -> 0xRRGGBB thuần toán học (an toàn tuyệt đối cho cả Dedicated Server không có AWT). */
    public static int hsvToRgb(float h, float s, float v) {
        float hh = ((h % 1.0F) + 1.0F) % 1.0F;
        float ss = Mth.clamp(s, 0.0F, 1.0F);
        float vv = Mth.clamp(v, 0.0F, 1.0F);
        int i = (int) Math.floor(hh * 6.0F);
        float f = hh * 6.0F - i;
        float p = vv * (1.0F - ss);
        float q = vv * (1.0F - f * ss);
        float t = vv * (1.0F - (1.0F - f) * ss);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = vv; g = t;  b = p; }
            case 1 -> { r = q;  g = vv; b = p; }
            case 2 -> { r = p;  g = vv; b = t; }
            case 3 -> { r = p;  g = q;  b = vv; }
            case 4 -> { r = t;  g = p;  b = vv; }
            default -> { r = vv; g = p; b = q; }
        }
        int ri = Mth.clamp(Math.round(r * 255.0F), 0, 255);
        int gi = Mth.clamp(Math.round(g * 255.0F), 0, 255);
        int bi = Mth.clamp(Math.round(b * 255.0F), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    /** Chuyển đổi 0xRRGGBB -> mảng {hue, sat, val} trong [0..1] cho thanh trượt màu. */
    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0.0F;
        if (d > 1e-5F) {
            if (max == r) {
                h = ((g - b) / d) % 6.0F;
            } else if (max == g) {
                h = (b - r) / d + 2.0F;
            } else {
                h = (r - g) / d + 4.0F;
            }
            h /= 6.0F;
            if (h < 0.0F) h += 1.0F;
        }
        float s = max <= 1e-5F ? 0.0F : (d / max);
        return new float[]{h, s, max};
    }
}
