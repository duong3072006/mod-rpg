package com.abyssalrpg.client.hud;

import com.abyssalrpg.client.ExcaliburClientState;
import com.abyssalrpg.client.ExcaliburClientState.PassiveSnapshot;
import com.abyssalrpg.client.TogetsuClientState;
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

/**
 * HUD tổng hợp gọn đẹp cho **Excalibur**:
 * - Thanh năng lượng (`0–100`): `+2`/đòn, `+5`/mục tiêu hạ gục, đủ `100` nhận Sức Mạnh IV & sẵn sàng `[R]`!
 * - Dưới `100` năng lượng: nhắc trạng thái **Đỡ Hoàng Gia `[RMB]`** & đếm ngược hồi chiêu Tuyệt kỹ `25s`.
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
        PassiveSnapshot snap = ExcaliburClientState.getPassive(player.getId(), stack);
        float energy = Mth.clamp(snap.energy, 0.0F, ExcaliburItem.MAX_ENERGY);
        boolean fullEnergy = energy >= ExcaliburItem.MAX_ENERGY - 0.01F;
        boolean onUltCd = snap.ultCooldownTicks > 0;
        boolean ready = fullEnergy && !onUltCd;

        float targetFrac = Mth.clamp(energy / ExcaliburItem.MAX_ENERGY, 0.0F, 1.0F);
        displayedFill = Mth.lerp(0.24F, displayedFill, targetFrac);
        float frac = Mth.clamp(displayedFill, 0.0F, 1.0F);

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float time = (gameTime % 24000L) + partialTick;
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.25F);

        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int hotbarLeft = screenW / 2 - 91;

        int panelW = 128;
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

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC80B0912);
        int borderCol = ready ? lerpArgb(0xFFFFD248, 0xFFFFFFFF, pulse) : 0xCCDFB03A;
        g.renderOutline(panelX, panelY, panelW, panelH, borderCol);

        String ultKey = TogetsuClientState.getFuseKeyDisplayName();
        String label;
        int labelCol;
        if (ready) {
            label = "✦ NĂNG LƯỢNG 100/100 · [" + ultKey + "]";
            labelCol = lerpArgb(0xFFFFDE66, 0xFFFFFFFF, pulse);
        } else if (onUltCd && fullEnergy) {
            label = "✦ ĐẦY 100/100 · HỒI CHIÊU " + ExcaliburCombatHandler.formatTimer(snap.ultCooldownTicks);
            labelCol = 0xFFFFC97A;
        } else {
            label = "✦ NĂNG LƯỢNG " + Math.round(energy) + "/100 (+2/ĐÒN · +5/HẠ)";
            labelCol = 0xFFFFEAB0;
        }

        drawCenteredScaled(g, font, label, panelX + panelW * 0.5F, panelY + 4.0F, panelW - 8.0F, 0.68F, labelCol);

        g.fill(barX, barY, barX + barW, barY + barH, 0xFF1A141C);
        int fillW = Mth.clamp(Math.round(barW * frac), 0, barW);
        if (fillW > 0) {
            int leftCol = ready ? 0xFFF2B224 : 0xFFE09C1E;
            int rightCol = ready ? 0xFFFFFFFF : 0xFFFFF09C;
            g.fillGradient(barX, barY, barX + fillW, barY + barH, rightCol, leftCol);
            g.fill(barX, barY, barX + fillW, barY + 1, 0xCCFFFFFF);
        }

        int pillH = 11;
        int pillY = panelY - pillH - 2;
        StringBuilder status = new StringBuilder();
        if (ready) {
            status.append("✦ SỨC MẠNH IV · SẴN SÀNG [").append(ultKey).append("]");
        } else if (onUltCd) {
            String cdText = ExcaliburCombatHandler.formatTimer(snap.ultCooldownTicks);
            if (fullEnergy) {
                status.append("HỒI TUYỆT KỸ ").append(cdText).append(" · ĐỠ [RMB]");
            } else {
                int rem = Math.max(0, Math.round(ExcaliburItem.MAX_ENERGY - energy));
                status.append("HỒI CHIÊU ").append(cdText).append(" · CÒN ").append(rem).append(" NL");
            }
        } else {
            int rem = Math.max(0, Math.round(ExcaliburItem.MAX_ENERGY - energy));
            status.append("ĐỠ HOÀNG GIA [RMB] · CÒN ").append(rem).append(" NL");
        }

        int pillBg = ready ? 0xCC2C2208 : (onUltCd ? 0xCC241512 : 0xCC241808);
        int pillBorder = ready ? lerpArgb(0xFFFFCC38, 0xFFFFFFFF, pulse) : (onUltCd ? 0xFFE88A44 : 0xFFFFC840);
        int pillTextCol = ready ? lerpArgb(0xFFFFEA8A, 0xFFFFFFFF, pulse) : (onUltCd ? 0xFFFFC58A : 0xFFFFE078);

        g.fill(panelX, pillY, panelX + panelW, pillY + pillH, pillBg);
        g.renderOutline(panelX, pillY, panelW, pillH, pillBorder);
        drawCenteredScaled(g, font, status.toString(), panelX + panelW * 0.5F, pillY + 2.8F, panelW - 6.0F, 0.65F, pillTextCol);
    }

    private static void drawCenteredScaled(GuiGraphics g, Font font, String text,
                                           float centerX, float y, float maxWidth, float maxScale, int color) {
        int rawW = Math.max(1, font.width(text));
        float scale = Math.min(maxScale, maxWidth / rawW);
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -rawW / 2, 0, color, true);
        g.pose().popPose();
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
