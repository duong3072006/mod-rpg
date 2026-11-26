package com.abyssalrpg.client.hud;

import com.abyssalrpg.client.MuramasaClientState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Lớp phủ HUD **Mù Lòa Flash Trắng Toàn Màn Hình (10 giây)** khi kích hoạt chém Chuột Phải (`RMB`)
 * trên **Yêu Đao Muramasa Katana**:
 * - Che phủ toàn bộ khung hình bằng ánh chớp trắng xóa mượt mà theo từng khung hình (120–240 FPS),
 *   kèm ánh lóe vàng-trắng chói nhẹ ở 1.2 giây đầu rồi tan dần về trong suốt sau đúng 10 giây!
 */
public final class MuramasaFlashHudRenderer {
    private MuramasaFlashHudRenderer() {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        float pt = deltaTracker.getGameTimeDeltaPartialTick(true);
        float alpha = MuramasaClientState.getWhiteFlashAlpha(pt);
        if (alpha <= 0.002F) {
            return;
        }

        int w = g.guiWidth();
        int h = g.guiHeight();
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        float glare = MuramasaClientState.getWhiteFlashGlare(pt);
        // Ánh trắng tinh khiết pha chút ấm cực nhẹ (0xFFFFFA) khi vừa bùng nổ rồi chuyển thuần trắng 0xFFFFFF
        int blueCh = Mth.clamp(Math.round(255.0F - glare * 16.0F), 235, 255);
        int argb = (a << 24) | (255 << 16) | (255 << 8) | blueCh;

        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, argb);
        RenderSystem.disableBlend();
    }
}
