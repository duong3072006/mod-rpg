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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * HUD Thanh Đo Hợp Nhất (Fusion Gauge 0–100%) nhỏ gọn cho **Togetsu / Song Nguyệt**:
 * - Đặt gọn ngang tầm và ngay bên trái thanh công cụ (Hotbar), tự chừa khoảng nếu ô tay phụ nằm bên trái.
 * - Tự động co chữ tinh gọn nằm lọt hoàn toàn trong khung viền (`104 × 22 px`), 0 bao giờ tràn viền!
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
        int hotbarLeft = screenW / 2 - 91;

        // Kích thước nhỏ gọn ngang bằng chiều cao thanh hotbar (22px), đặt sát bên trái thanh công cụ
        int panelW = 104;
        int panelH = 22;
        boolean offhandOnLeft = !player.getOffhandItem().isEmpty() && player.getMainArm() == HumanoidArm.RIGHT;
        int panelRight = offhandOnLeft ? (hotbarLeft - 33) : (hotbarLeft - 6);
        int panelX = Math.max(4, panelRight - panelW);
        int panelY = screenH - panelH;

        int barW = panelW - 8;
        int barH = 4;
        int barX = panelX + 4;
        int barY = panelY + 14;
        Font font = mc.font;

        // Nền tối thanh lịch + viền gọn khớp chuẩn chiều cao hotbar
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xB80B0D14);
        int borderCol = fused
                ? lerpArgb(0xFF7ADFFF, 0xFFFFFFFF, pulse * 0.6F)
                : (readyToFuse ? lerpArgb(0xFFFFD866, 0xFFFFFFFF, pulse) : 0xCC6B7894);
        g.renderOutline(panelX, panelY, panelW, panelH, borderCol);

        // Nhãn gọn sạch hiển thị trạng thái, đếm ngược Song Nguyệt & phím tắt
        String keyName = TogetsuClientState.getFuseKeyDisplayName();
        int transformRemTicks = TogetsuItem.getTransformCooldownRemainingTicks(stack, gameTime);
        int vorpalRemTicks = TogetsuItem.getVorpalCooldownRemainingTicks(stack, gameTime);
        int pinRemTicks = TogetsuItem.getPinCooldownRemainingTicks(stack, gameTime);
        String label;
        int labelCol;
        if (fused) {
            String leftStr = String.format(Locale.ROOT, "%.1fs", fuseTicksLeft / 20.0F);
            if (vorpalRemTicks > 0) {
                label = "☾ SONG NGUYỆT " + leftStr + " · " + String.format(Locale.ROOT, "%.1fs", vorpalRemTicks / 20.0F);
                labelCol = 0xFFA8D8FF;
            } else {
                label = "✦ SONG NGUYỆT " + leftStr + " · [RMB]";
                labelCol = lerpArgb(0xFF8CE6FF, 0xFFFFFFFF, pulse);
            }
        } else if (transformRemTicks > 0) {
            label = "☾ " + Math.round(rawGauge) + "% · Đổi " + String.format(Locale.ROOT, "%.1fs", transformRemTicks / 20.0F);
            labelCol = 0xFFFFDF80;
        } else if (readyToFuse) {
            label = "✦ SẴN SÀNG · [" + keyName + "/RMB]";
            labelCol = lerpArgb(0xFFFFD85A, 0xFFFFFFFF, pulse);
        } else if (pinRemTicks > 0) {
            label = "☾ " + Math.round(rawGauge) + "% · Đâm " + String.format(Locale.ROOT, "%.1fs", pinRemTicks / 20.0F);
            labelCol = 0xFFD4DCEB;
        } else {
            label = "☾ TOGETSU " + Math.round(rawGauge) + "/100";
            labelCol = 0xFFE6EEF8;
        }

        // Tự động co tỷ lệ chữ <= 0.72f để luôn nằm gọn 100% bên trong khung
        int rawTextW = Math.max(1, font.width(label));
        float maxTextW = panelW - 8.0F;
        float textScale = Math.min(0.72F, maxTextW / rawTextW);
        float centerX = panelX + panelW * 0.5F;
        float textY = panelY + 4.0F;

        g.pose().pushPose();
        g.pose().translate(centerX, textY, 0.0F);
        g.pose().scale(textScale, textScale, 1.0F);
        g.drawString(font, label, -rawTextW / 2, 0, labelCol, true);
        g.pose().popPose();

        // Rãnh thanh gauge & vạch năng lượng Trắng–Xanh Nguyệt Quang
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF141824);
        int fillW = Mth.clamp(Math.round(barW * frac), 0, barW);
        if (fillW > 0) {
            int leftCol = fused ? 0xFF36B8FF : (readyToFuse ? 0xFFFFC947 : 0xFF6B85A8);
            int rightCol = fused ? 0xFFE8FAFF : 0xFFFFFFFF;
            g.fillGradient(barX, barY, barX + fillW, barY + barH, rightCol, leftCol);
            g.fill(barX, barY, barX + fillW, barY + 1, 0xCCFFFFFF);
        }

        // Huy hiệu Cường Hóa Trăng Non (Waxing Empowered) nhỏ gọn nằm ngay phía trên bảng thanh nộ
        if (empowered) {
            int pct = Math.round(TogetsuItem.getWaxingEmpowerBonus(stack) * 100.0F);
            String buff = "✦ Trăng Non +" + pct + "%";
            float bScale = 0.70F;
            int rawBw = font.width(buff);
            int pillW = Math.min(panelW, Math.round(rawBw * bScale) + 8);
            int pillH = 10;
            int bx = panelX + (panelW - pillW) / 2;
            int by = panelY - pillH - 2;
            g.fill(bx, by, bx + pillW, by + pillH, 0xCC3B0A16);
            g.renderOutline(bx, by, pillW, pillH, 0xFFFF6680);
            g.pose().pushPose();
            g.pose().translate(bx + pillW * 0.5F, by + 2.5F, 0.0F);
            g.pose().scale(bScale, bScale, 1.0F);
            g.drawString(font, buff, -rawBw / 2, 0, 0xFFFFDCE2, true);
            g.pose().popPose();
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
