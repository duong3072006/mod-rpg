package com.abyssalrpg.client.hud;

import com.abyssalrpg.client.ExcaliburClientState;
import com.abyssalrpg.client.TogetsuClientState;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.combat.ExcaliburCombatHandler;
import com.abyssalrpg.item.ExcaliburItem;
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
 * HUD Thanh Năng Lượng Biến Đổi Dạng (`0–100%`) cho **Excalibur & Excalibur Morgan**:
 * - Đặt gọn ngang tầm và ngay bên trái thanh Hotbar (tự tránh ô tay phụ), không che máu/giáp.
 * - Dạng Thường (Golden Excalibur): thanh năng lượng Vàng–Trắng rực rỡ tích tụ từ đòn đánh & hồi tự động -> đầy 100% sáng viền `[R] SẴN SÀNG`!
 * - Đang gồng `[R]`: thanh biến hình chạy mượt `0% -> 100%` ánh Đỏ Huyết–Vàng!
 * - Dạng Hắc Ám (Excalibur Morgan): thanh đếm ngược `20.0s` Đỏ Thẫm & Hắc Diện Thạch kèm hồi chiêu `[RMB]`!
 */
public final class ExcaliburHudRenderer {
    private static float displayedFill = 0.0F;

    private ExcaliburHudRenderer() {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EXCALIBUR)) {
            return;
        }

        long gameTime = mc.level.getGameTime();
        boolean morgan = ExcaliburItem.isMorgan(stack);
        boolean channeling = ExcaliburAnimManager.isChanneling(player.getId());
        float channelFrac = ExcaliburAnimManager.getChannelProgress(player.getId());

        long morganRemTicks = morgan ? Math.max(0L, ExcaliburItem.getMorganEndTick(stack) - gameTime) : 0L;
        long cdRemTicks = !morgan ? Math.max(0L, ExcaliburItem.getMorganCooldownUntil(stack) - gameTime) : 0L;
        long ultCdRemTicks = Math.max(0L, ExcaliburItem.getUltCooldownUntil(stack) - gameTime);
        float energyPct = ExcaliburClientState.getSyncedEnergy(player.getId(), stack);
        boolean creative = player.getAbilities().instabuild;
        boolean readyToTransform = !morgan && !channeling && cdRemTicks == 0L && (energyPct >= 99.5F || creative);

        float targetFrac;
        if (channeling) {
            targetFrac = channelFrac;
        } else if (morgan) {
            targetFrac = Mth.clamp(morganRemTicks / (float) ExcaliburCombatHandler.MORGAN_DURATION_TICKS, 0.0F, 1.0F);
        } else if (cdRemTicks > 0L) {
            targetFrac = 1.0F - Mth.clamp(cdRemTicks / (float) ExcaliburCombatHandler.MORGAN_COOLDOWN_TICKS, 0.0F, 1.0F);
        } else {
            targetFrac = Mth.clamp(energyPct / 100.0F, 0.0F, 1.0F);
        }
        displayedFill = Mth.lerp(0.24F, displayedFill, targetFrac);
        float frac = Mth.clamp(displayedFill, 0.0F, 1.0F);

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float time = (gameTime % 24000L) + partialTick;
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.25F);

        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int hotbarLeft = screenW / 2 - 91;

        int panelW = 114;
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

        // Nền tối + viền đổi sắc theo dạng: Vàng Thánh Quang / Đỏ Thẫm Morgan!
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC40B0910);
        int borderCol;
        if (channeling) {
            borderCol = lerpArgb(0xFFFFD84A, 0xFFFF2436, pulse);
        } else if (morgan) {
            borderCol = lerpArgb(0xFFCC1426, 0xFFFF4654, pulse);
        } else if (readyToTransform) {
            borderCol = lerpArgb(0xFFFFC838, 0xFFFFFFFF, pulse);
        } else {
            borderCol = 0xCCBF962E;
        }
        g.renderOutline(panelX, panelY, panelW, panelH, borderCol);

        String transformKey = TogetsuClientState.getFuseKeyDisplayName();
        String label;
        int labelCol;
        if (channeling) {
            int pct = Math.min(100, Math.round(channelFrac * 100.0F));
            label = "✦ BIẾN ĐỔI MORGAN " + pct + "%";
            labelCol = lerpArgb(0xFFFF7A7A, 0xFFFFFFFF, pulse);
        } else if (morgan) {
            String leftStr = String.format(Locale.ROOT, "%.1fs", morganRemTicks / 20.0F);
            if (ultCdRemTicks > 0L) {
                label = "✦ MORGAN " + leftStr + " · " + String.format(Locale.ROOT, "%.1fs", ultCdRemTicks / 20.0F);
                labelCol = 0xFFFF8A92;
            } else {
                label = "✦ MORGAN " + leftStr + " · [RMB]";
                labelCol = lerpArgb(0xFFFF424E, 0xFFFFD2D6, pulse);
            }
        } else if (cdRemTicks > 0L) {
            label = "✦ HỒI BIẾN ĐỔI " + String.format(Locale.ROOT, "%.1fs", cdRemTicks / 20.0F);
            labelCol = 0xFFD2A48C;
        } else if (readyToTransform) {
            label = "✦ SẴN SÀNG · [" + transformKey + "]";
            labelCol = lerpArgb(0xFFFFD85A, 0xFFFFFFFF, pulse);
        } else {
            label = "✦ EXCALIBUR " + Math.round(energyPct) + "% · [" + transformKey + "]";
            labelCol = 0xFFFFEAB0;
        }

        int rawTextW = Math.max(1, font.width(label));
        float maxTextW = panelW - 8.0F;
        float textScale = Math.min(0.70F, maxTextW / rawTextW);
        float centerX = panelX + panelW * 0.5F;
        float textY = panelY + 4.0F;

        g.pose().pushPose();
        g.pose().translate(centerX, textY, 0.0F);
        g.pose().scale(textScale, textScale, 1.0F);
        g.drawString(font, label, -rawTextW / 2, 0, labelCol, true);
        g.pose().popPose();

        // Rãnh thanh năng lượng & dải màu Vàng Thánh Quang / Đỏ Thẫm Morgan
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF1A141C);
        int fillW = Mth.clamp(Math.round(barW * frac), 0, barW);
        if (fillW > 0) {
            int leftCol;
            int rightCol;
            if (channeling || morgan) {
                leftCol = 0xFF9E0A18;
                rightCol = 0xFFFF3A48;
            } else if (cdRemTicks > 0L) {
                leftCol = 0xFF5A3B28;
                rightCol = 0xFFC27A44;
            } else {
                leftCol = 0xFFE6A41E;
                rightCol = readyToTransform ? 0xFFFFFFFF : 0xFFFFF09C;
            }
            g.fillGradient(barX, barY, barX + fillW, barY + barH, rightCol, leftCol);
            g.fill(barX, barY, barX + fillW, barY + 1, 0xCCFFFFFF);
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
