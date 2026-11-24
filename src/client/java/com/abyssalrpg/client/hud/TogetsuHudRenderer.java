package com.abyssalrpg.client.hud;

import com.abyssalrpg.client.TogetsuClientState;
import com.abyssalrpg.combat.TogetsuCombatHandler;
import com.abyssalrpg.item.TogetsuItem;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * HUD Thanh Đo Hợp Nhất (Fusion Gauge 0–100%) cho **Togetsu / Song Nguyệt**:
 * - Chỉ hiển thị gọn gàng phía trên thanh hotbar khi người chơi đang cầm `abyssalrpg:togetsu`.
 * - Hiển thị trực quan trạng thái Trăng Non (Trắng) / Trăng Khuyết (Đen), mốc sẵn sàng phím `[R]` Hợp Nhất,
 *   trạng thái Cường Hóa Trăng Non (+XX%), và thời gian hồi của tuyệt kỹ **Crescent Vorpal** khi đang ở dạng Song Nguyệt!
 */
public final class TogetsuHudRenderer {
    private static float displayedGauge = 0.0F;

    private TogetsuHudRenderer() {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TOGETSU)) {
            return;
        }

        boolean fused = TogetsuItem.isFused(stack);
        long gameTime = mc.level.getGameTime();
        int fuseTicksLeft = TogetsuItem.getFusionTicksRemaining(stack, gameTime);
        float rawGauge = fused
                ? (fuseTicksLeft / (float) TogetsuCombatHandler.FUSION_DURATION_TICKS) * 100.0F
                : TogetsuItem.getFusionGauge(stack);
        displayedGauge = Mth.lerp(0.24F, displayedGauge, rawGauge);
        float frac = Mth.clamp(displayedGauge / 100.0F, 0.0F, 1.0F);
        boolean readyToFuse = !fused && (rawGauge >= 99.5F || player.getAbilities().instabuild);
        boolean empowered = TogetsuItem.isWaxingEmpowered(stack);

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float time = (gameTime % 24000L) + partialTick;
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.25F);

        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int cx = screenW / 2;
        int barW = 152;
        int barH = 6;
        int barX = cx - barW / 2;
        int barY = screenH - 86; // Đặt cao hẳn phía trên Action Bar vanilla (screenH - 68) -> 0 bao giờ bị chồng chữ!
        Font font = mc.font;

        // Nền tối thanh lịch phía sau thanh năng lượng
        g.fill(barX - 4, barY - 12, barX + barW + 4, barY + barH + 4, 0xA80B0D14);
        int borderCol = fused
                ? lerpArgb(0xFF7ADFFF, 0xFFFFFFFF, pulse * 0.6F)
                : (readyToFuse ? lerpArgb(0xFFFFD866, 0xFFFFFFFF, pulse) : 0xCC6B7894);
        g.renderOutline(barX - 4, barY - 12, barW + 8, barH + 16, borderCol);

        // Tiêu đề trạng thái, đếm ngược 15s Song Nguyệt, hồi đổi dạng (6s) & hồi chiêu ĐỘC LẬP giữa 2 dạng!
        String keyName = TogetsuClientState.getFuseKeyDisplayName();
        int transformRemTicks = TogetsuItem.getTransformCooldownRemainingTicks(stack, gameTime);
        int vorpalRemTicks = TogetsuItem.getVorpalCooldownRemainingTicks(stack, gameTime);
        int pinRemTicks = TogetsuItem.getPinCooldownRemainingTicks(stack, gameTime);
        String label;
        int labelCol;
        if (fused) {
            String leftStr = String.format(Locale.ROOT, "%.1fs", fuseTicksLeft / 20.0F);
            if (vorpalRemTicks > 0) {
                label = "☾ SONG NGUYỆT " + leftStr + " · Vorpal " + String.format(Locale.ROOT, "%.1fs", vorpalRemTicks / 20.0F);
                labelCol = 0xFFA8D8FF;
            } else {
                label = "✦ SONG NGUYỆT " + leftStr + " · [Shift+RMB] Vorpal";
                labelCol = lerpArgb(0xFF8CE6FF, 0xFFFFFFFF, pulse);
            }
        } else if (transformRemTicks > 0) {
            label = "☾ TOGETSU " + Math.round(rawGauge) + " · Đổi dạng " + String.format(Locale.ROOT, "%.1fs", transformRemTicks / 20.0F);
            labelCol = 0xFFFFDF80;
        } else if (readyToFuse) {
            label = "✦ SONG NGUYỆT SẴN SÀNG · [" + keyName + " / RMB]";
            labelCol = lerpArgb(0xFFFFD85A, 0xFFFFFFFF, pulse);
        } else if (pinRemTicks > 0) {
            label = "☾ TOGETSU " + Math.round(rawGauge) + "/100 · Liên Đâm " + String.format(Locale.ROOT, "%.1fs", pinRemTicks / 20.0F);
            labelCol = 0xFFD4DCEB;
        } else {
            label = "☾ TOGETSU " + Math.round(rawGauge) + "/100 · [RMB] 12 Liên Đâm";
            labelCol = 0xFFE6EEF8;
        }
        g.drawCenteredString(font, label, cx, barY - 10, labelCol);

        // Rãnh thanh gauge & vạch năng lượng Trắng–Xanh Nguyệt Quang
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF141824);
        int fillW = Mth.clamp(Math.round(barW * frac), 0, barW);
        if (fillW > 0) {
            int leftCol = fused ? 0xFF36B8FF : (readyToFuse ? 0xFFFFC947 : 0xFF6B85A8);
            int rightCol = fused ? 0xFFE8FAFF : 0xFFFFFFFF;
            g.fillGradient(barX, barY, barX + fillW, barY + barH, rightCol, leftCol);
            g.fill(barX, barY, barX + fillW, barY + 1, 0xCCFFFFFF);
        }

        // Huy hiệu Cường Hóa Trăng Non (Waxing Empowered) khi đang tích sát thương cộng thêm!
        if (empowered) {
            int pct = Math.round(TogetsuItem.getWaxingEmpowerBonus(stack) * 100.0F);
            String buff = "✦ Trăng Non +" + pct + "%";
            int bw = font.width(buff) + 8;
            int bx = barX + barW + 8;
            int by = barY - 7;
            g.fill(bx, by, bx + bw, by + 13, 0xCC3B0A16);
            g.renderOutline(bx, by, bw, 13, 0xFFFF6680);
            g.drawString(font, buff, bx + 4, by + 3, 0xFFFFDCE2, true);
        }
    }

    private static int lerpArgb(int c1, int c2, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int a = (int) Mth.lerp(t, (c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, c1 & 0xFF, c2 & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
