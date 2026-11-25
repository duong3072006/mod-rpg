package com.abyssalrpg.client.hud;

import com.abyssalrpg.client.TogetsuClientState;
import com.abyssalrpg.combat.SeaWeaponsCombatHandler;
import com.abyssalrpg.item.SeaTridentItem;
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
 * HUD gọn bên trái thanh Hotbar hiển thị hồi chiêu 2 kỹ năng song thủ của **Hải Thần Đinh Ba & Đèn Lồng Biển**:
 * - Dòng 1: `[RMB]` **Sóng Triều** (Đèn Lồng Tay Trái)
 * - Dòng 2: `[R]` **Xoáy Nước** (Đinh Ba Tay Phải)
 */
public final class SeaWeaponsHudRenderer {
    private SeaWeaponsHudRenderer() {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.SEA_TRIDENT)) return;

        long now = mc.level.getGameTime();
        boolean creative = player.getAbilities().instabuild;
        long waveRem = creative ? 0L : Math.max(0L, SeaTridentItem.getWaveCooldownUntil(stack) - now);
        long whirlRem = creative ? 0L : Math.max(0L, SeaTridentItem.getWhirlpoolCooldownUntil(stack) - now);
        float waveFrac = 1.0F - Mth.clamp(waveRem / (float) SeaWeaponsCombatHandler.TIDAL_WAVE_COOLDOWN_TICKS, 0.0F, 1.0F);
        float whirlFrac = 1.0F - Mth.clamp(whirlRem / (float) SeaWeaponsCombatHandler.WHIRLPOOL_COOLDOWN_TICKS, 0.0F, 1.0F);

        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int hotbarLeft = screenW / 2 - 91;
        int panelW = 122;
        int panelH = 26;
        boolean offhandOnLeft = !player.getOffhandItem().isEmpty() && player.getMainArm() == HumanoidArm.RIGHT;
        int panelRight = offhandOnLeft ? (hotbarLeft - 33) : (hotbarLeft - 6);
        int panelX = Math.max(4, panelRight - panelW);
        int panelY = screenH - panelH;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC408131E);
        int border = (waveRem == 0L && whirlRem == 0L) ? 0xE858D8FF : 0xCC2A78A8;
        g.renderOutline(panelX, panelY, panelW, panelH, border);

        Font font = mc.font;
        String keyR = TogetsuClientState.getFuseKeyDisplayName();

        // Row 1: Lantern Colossal Tsunami (RMB)
        String waveText = waveRem == 0L
                ? "[RMB] Sóng Thần ★"
                : String.format(Locale.ROOT, "[RMB] Sóng Thần %.1fs", waveRem / 20.0F);
        drawRow(g, font, panelX + 4, panelY + 3, panelW - 8, waveText, waveFrac,
                waveRem == 0L ? 0xFFA8F6FF : 0xFF7CA8C4, 0xFF38C4FF, 0xFFD8FCFF);

        // Row 2: Trident 5-Trident Sky Volley ([R])
        String whirlText = whirlRem == 0L
                ? "[" + keyR + "] 5 Đinh Ba ★"
                : String.format(Locale.ROOT, "[%s] 5 Đinh Ba %.1fs", keyR, whirlRem / 20.0F);
        drawRow(g, font, panelX + 4, panelY + 14, panelW - 8, whirlText, whirlFrac,
                whirlRem == 0L ? 0xFF72C6FF : 0xFF6B8FB4, 0xFF1F6EE6, 0xFF88E4FF);
    }

    private static void drawRow(GuiGraphics g, Font font, int x, int y, int w, String label,
                                float frac, int textCol, int barLo, int barHi) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, label, 0, 0, textCol, true);
        g.pose().popPose();

        int barY = y + 7;
        g.fill(x, barY, x + w, barY + 2, 0xAA0C1D2E);
        int fillW = Math.round(w * Mth.clamp(frac, 0.0F, 1.0F));
        if (fillW > 0) {
            g.fill(x, barY, x + fillW, barY + 2, frac >= 0.99F ? barHi : barLo);
        }
    }
}
