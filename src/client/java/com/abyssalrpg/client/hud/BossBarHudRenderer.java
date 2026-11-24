package com.abyssalrpg.client.hud;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.entity.AbyssalKnightBossEntity;
import com.abyssalrpg.entity.CryptLichBossEntity;
import com.abyssalrpg.entity.MoltenTitanBossEntity;
import com.abyssalrpg.entity.mythic.ApepBossEntity;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import com.abyssalrpg.entity.mythic.FenrirBossEntity;
import com.abyssalrpg.entity.mythic.HydraBossEntity;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.entity.mythic.LeviathanBossEntity;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import com.abyssalrpg.entity.mythic.QuetzalcoatlBossEntity;
import com.abyssalrpg.entity.mythic.TiamatBossEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ornate AAA RPG Boss Health Bar HUD (Elden Ring / L_Ender's Cataclysm style) +
 * Cinematic Animated Boss Summon Awakening Banner for all 11 Colossal Abyssal Bosses.
 */
public final class BossBarHudRenderer {
    private static final double RENDER_RANGE_SQR = 76.0 * 76.0;
    private static final double LEVIATHAN_RENDER_RANGE_SQR = 108.0 * 108.0;
    private static final double CINDER_RENDER_RANGE_SQR = 144.0 * 144.0;
    private static final double NIGHTMARE_RENDER_RANGE_SQR = 148.0 * 148.0;
    private static final int MAX_VISIBLE_BARS = 4;
    private static final ResourceLocation CERBERUS_CUSTOM_FRAME_TEX =
            AbyssalRpgMod.id("textures/gui/cerberus_bossbar_frame.png");

    private static final class BarVisualState {
        float displayHp = -1f;
        float trailHp = -1f;
        int trailHoldTicks = 0;
        float fadeAlpha = 0f;
        long lastSeenTick = 0L;
        int lastPhase = 1;
        float phaseBlend = 0f;
        int phaseFlashTicks = 0;
    }

    private record BossPalette(int lightRgb, int midRgb, int darkRgb, int borderRgb, String subtitle) {}

    private static final Map<Integer, BarVisualState> STATES = new HashMap<>();

    private BossBarHudRenderer() {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        long gameTime = mc.level.getGameTime();
        float time = (gameTime % 24000L) + partialTick;

        List<AbstractAbyssalBoss> bosses = new ArrayList<>();
        java.util.Set<String> seenCerberusGroups = new java.util.HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof AbstractAbyssalBoss boss && boss.isAlive()) {
                double maxRangeSqr;
                if (boss instanceof NightmareKnightBossEntity) {
                    maxRangeSqr = NIGHTMARE_RENDER_RANGE_SQR;
                } else if (boss instanceof CinderDragonBossEntity) {
                    maxRangeSqr = CINDER_RENDER_RANGE_SQR;
                } else if (boss instanceof LeviathanBossEntity || boss instanceof HydraBossEntity
                        || boss instanceof CerberusBossEntity || boss instanceof JormungandrBossEntity) {
                    maxRangeSqr = LEVIATHAN_RENDER_RANGE_SQR;
                } else {
                    maxRangeSqr = RENDER_RANGE_SQR;
                }
                if (boss.distanceToSqr(mc.player) <= maxRangeSqr) {
                    if (boss instanceof CerberusBossEntity cerberus) {
                        // Gộp toàn bộ các cá thể Cerberus cùng EncounterGroup vào ĐÚNG 1 thanh BossBar chung!
                        if (!seenCerberusGroups.add(cerberus.getSyncedGroupId())) {
                            continue;
                        }
                    }
                    bosses.add(boss);
                }
            }
        }
        if (bosses.isEmpty()) {
            if (!STATES.isEmpty() && gameTime % 40 == 0) STATES.clear();
            return;
        }

        // Priority: actively summoning bosses first, then closest boss
        bosses.sort(Comparator
                .comparing((AbstractAbyssalBoss b) -> !b.isSpawningIn())
                .thenComparingDouble(b -> b.distanceToSqr(mc.player)));

        if (STATES.size() > 64) {
            STATES.entrySet().removeIf(e -> gameTime - e.getValue().lastSeenTick > 100L);
        }

        int screenW = g.guiWidth();
        Font font = mc.font;
        int count = Math.min(MAX_VISIBLE_BARS, bosses.size());
        boolean compact = count >= 3;
        int barW = compact ? 228 : 272;
        int barH = compact ? 9 : 11;
        int rowSpacing = compact ? 30 : 38;
        int startY = 10;

        AbstractAbyssalBoss cinematicSpawning = null;

        for (int i = 0; i < count; i++) {
            AbstractAbyssalBoss boss = bosses.get(i);
            if (boss.isSpawningIn() && cinematicSpawning == null) {
                cinematicSpawning = boss;
            }
            int y = startY + i * rowSpacing;
            renderOrnateBossBar(g, font, boss, screenW / 2, y, barW, barH, compact, time, partialTick, gameTime);
        }

        // Clean overflow pill if more than 4 bosses are nearby
        if (bosses.size() > MAX_VISIBLE_BARS) {
            int extra = bosses.size() - MAX_VISIBLE_BARS;
            String msg = "+ " + extra + " Boss";
            int pw = font.width(msg) + 14;
            int px = (screenW - pw) / 2;
            int py = startY + count * rowSpacing - 2;
            g.fill(px, py, px + pw, py + 13, 0xCC120D1A);
            g.renderOutline(px, py, pw, 13, 0xAA8C6D32);
            g.drawCenteredString(font, msg, screenW / 2, py + 3, 0xFFE5C77B);
        }

        // Cinematic Summoning Awakening Banner at lower-upper center when a boss is dramatically emerging!
        if (cinematicSpawning != null) {
            renderSummonAwakeningBanner(g, font, cinematicSpawning, screenW, g.guiHeight(), time, partialTick);
        }
    }

    private static void renderOrnateBossBar(GuiGraphics g, Font font, AbstractAbyssalBoss boss,
                                            int centerX, int y, int barW, int barH, boolean compact,
                                            float time, float partialTick, long gameTime) {
        BossPalette pal = paletteFor(boss);
        int stateKey = (boss instanceof CerberusBossEntity cerb) ? cerb.getSyncedGroupId().hashCode() : boss.getId();
        BarVisualState st = STATES.computeIfAbsent(stateKey, k -> new BarVisualState());
        st.lastSeenTick = gameTime;

        float rawMaxHp;
        float currentHp;
        if (boss instanceof CerberusBossEntity cerb) {
            rawMaxHp = cerb.getSyncedGroupMaxHp();
            currentHp = cerb.getSyncedGroupTotalHp();
        } else {
            rawMaxHp = (boss instanceof BossHealthHolder holder && holder.getBossMaxHealth() > 0f)
                    ? holder.getBossMaxHealth()
                    : boss.getMaxHealth();
            currentHp = boss.getHealth();
        }
        float maxHp = Math.max(1f, rawMaxHp);
        float rawFrac = Mth.clamp(currentHp / maxHp, 0f, 1f);
        float spawnProg = boss.isSpawningIn() ? boss.getSpawnProgress(partialTick) : 1.0f;
        // During summon entrance, health bar surges dramatically from 0% -> 100%!
        float targetFrac = boss.isSpawningIn() ? (rawFrac * smoothStep(spawnProg)) : rawFrac;

        if (st.displayHp < 0f) {
            st.displayHp = targetFrac;
            st.trailHp = targetFrac;
        } else {
            if (targetFrac < st.displayHp - 0.002f) {
                st.trailHoldTicks = 12;
            }
            st.displayHp = Mth.lerp(0.24f, st.displayHp, targetFrac);
            if (st.trailHp < st.displayHp) {
                st.trailHp = st.displayHp;
            } else if (st.trailHoldTicks > 0) {
                st.trailHoldTicks--;
            } else {
                st.trailHp = Mth.lerp(0.08f, st.trailHp, st.displayHp);
            }
        }
        st.fadeAlpha = Mth.clamp(st.fadeAlpha + 0.12f, 0f, 1f);

        int phase = boss.getSyncedPhase();
        if (boss instanceof CerberusBossEntity cerb
                && (cerb.isEnragedVisual() || cerb.hasEnteredPhase2() || cerb.getCerberusForm().isSplitSingle())) {
            phase = Math.max(phase, 2);
        }
        // Chuyển màu mượt giữa Phase 1 (cam) -> Phase 2 (đỏ) kèm hiệu ứng nhấp nháy ngắn khi đổi phase!
        if (phase > st.lastPhase) {
            st.phaseFlashTicks = 24;
        }
        st.lastPhase = phase;
        float targetBlend = (phase >= 2) ? 1.0f : 0.0f;
        st.phaseBlend = Mth.lerp(0.10f, st.phaseBlend, targetBlend);
        if (st.phaseFlashTicks > 0) {
            st.phaseFlashTicks--;
        }
        if (boss instanceof CerberusBossEntity) {
            BossPalette p1 = new BossPalette(0xFF9E2C, 0xE05A12, 0x661B06, 0xFFD25A, pal.subtitle);
            BossPalette p2 = new BossPalette(0xFF3A3A, 0xC91414, 0x590606, 0xFF7B47, pal.subtitle);
            pal = new BossPalette(
                    lerpRgb(p1.lightRgb, p2.lightRgb, st.phaseBlend),
                    lerpRgb(p1.midRgb, p2.midRgb, st.phaseBlend),
                    lerpRgb(p1.darkRgb, p2.darkRgb, st.phaseBlend),
                    lerpRgb(p1.borderRgb, p2.borderRgb, st.phaseBlend),
                    pal.subtitle
            );
        }

        boolean casting = boss.getCastState() > 0 || boss.isSpawningIn();
        int barX = centerX - barW / 2;
        int barY = y + (compact ? 13 : 16);

        // 1. Subtle obsidian title backdrop vignette so name & stats are crystal-clear over bright sky!
        int bgPad = 20;
        int bgTop = y - 2;
        int bgBot = barY + barH + 5;
        g.fillGradient(barX - bgPad, bgTop, barX + barW + bgPad, bgBot, 0xB8090710, 0xD8110B1B);
        // Thin glowing top accent line
        int accentCol = withAlpha(casting ? pal.lightRgb : pal.borderRgb, casting ? 225 : 150);
        g.fill(barX - 8, bgTop, barX + barW + 8, bgTop + 1, accentCol);
        g.fill(barX - 8, bgBot - 1, barX + barW + 8, bgBot, withAlpha(pal.darkRgb, 180));

        // 2. Boss Title + Decorative Side Filigree + Phase Badge + Numeric HP!
        Component rawName = (boss instanceof CerberusBossEntity cerb)
                ? Component.literal(phase >= 2
                        ? "Cerberus - Chúa Tể Ba Đầu (" + cerb.getSyncedGroupAliveCount() + ")"
                        : "Cerberus - Chúa Tể Ba Đầu")
                : (boss.hasCustomName() ? boss.getCustomName() : boss.getDisplayName());
        String titleStr = rawName != null ? rawName.getString() : "Boss";
        int maxTitleW = barW - (compact ? 58 : 76);
        if (font.width(titleStr) > maxTitleW) {
            titleStr = font.plainSubstrByWidth(titleStr, Math.max(40, maxTitleW - 8)) + "...";
        }

        // Left Phase Badge (when Phase >= 2)
        if (phase >= 2) {
            String phStr = (phase >= 3) ? "III" : "II";
            int badgeW = font.width(phStr) + 8;
            int bx = barX - 2;
            int by = y + (compact ? 1 : 2);
            int phBg = (phase >= 3) ? 0xEE7A0E1B : 0xEE5C3409;
            int phRim = (phase >= 3) ? 0xFFFF4A5A : 0xFFFFC947;
            g.fill(bx, by, bx + badgeW, by + 10, phBg);
            g.renderOutline(bx, by, badgeW, 10, phRim);
            g.drawString(font, phStr, bx + 4, by + 1, 0xFFFFF3C4, true);
        }

        // Centered Boss Name with soft shadow & signature color tint (flashes briefly on phase change!)
        float flashPulse = st.phaseFlashTicks > 0 ? (0.5f + 0.5f * Mth.sin(st.phaseFlashTicks * 1.15f)) : 0.0f;
        int baseTitleRgb = (boss instanceof CerberusBossEntity)
                ? lerpRgb(0xFF8C1A, 0xFF2E2E, st.phaseBlend)
                : lerpRgb(pal.lightRgb, 0xFFFFFF, 0.35f);
        int titleColor = withAlpha(lerpRgb(baseTitleRgb, 0xFFF7D0, flashPulse * 0.75f), 255);
        int nameW = font.width(titleStr);
        int nameX = centerX - nameW / 2;
        int nameY = y + (compact ? 2 : 3);
        g.drawString(font, titleStr, nameX, nameY, titleColor, true);

        // Ornamental flanking diamond filigree beside the title
        int gemCol = withAlpha(pal.lightRgb, 230);
        if (nameX - 10 > barX + 18) {
            drawDiamond(g, nameX - 7, nameY + 4, 2, gemCol);
            drawDiamond(g, nameX + nameW + 6, nameY + 4, 2, gemCol);
        }

        // Clean right-aligned numeric HP pill ("480/550")
        String hpText = ((int) Math.ceil(currentHp)) + "/" + ((int) maxHp);
        int hpW = font.width(hpText);
        g.drawString(font, hpText, barX + barW - hpW + 2, nameY, 0xFFE6DCF5, true);

        // 3. Ornate Beveled Metallic Frame around the Health Trough
        float pulse = casting ? (0.5f + 0.5f * Mth.sin(time * 0.32f)) : 0f;
        int frameOuter = withAlpha(lerpRgb(pal.borderRgb, 0xFFFFFF, pulse * 0.45f), 255);
        int frameInner = 0xFF181122;

        // Outer metallic gold/elemental bevel
        g.fill(barX - 2, barY - 2, barX + barW + 2, barY + barH + 2, 0xFF0A0710);
        g.renderOutline(barX - 2, barY - 2, barW + 4, barH + 4, frameOuter);
        g.renderOutline(barX - 1, barY - 1, barW + 2, barH + 2, frameInner);

        // Recessed dark inner trough with subtle gradient
        g.fillGradient(barX, barY, barX + barW, barY + barH, 0xFF120C1A, 0xFF1E1529);

        // 4. Delayed Golden-White Damage Drain Trail (Elden Ring / Dark Souls style!)
        int trailPx = Mth.clamp(Math.round(barW * st.trailHp), 0, barW);
        int fillPx = Mth.clamp(Math.round(barW * st.displayHp), 0, barW);
        if (trailPx > fillPx) {
            g.fillGradient(barX + fillPx, barY, barX + trailPx, barY + barH, 0xFFFFF0A8, 0xFFD98626);
        }

        // 5. Lush Dual-Tone Elemental HP Bar Fill + Top Specular Rim + Animated Light Shimmer!
        if (fillPx > 0) {
            int topCol = withAlpha(lerpRgb(pal.lightRgb, 0xFFFFFF, 0.22f), 255);
            int midCol = withAlpha(pal.midRgb, 255);
            int botCol = withAlpha(pal.darkRgb, 255);
            int splitY = barY + Math.max(2, barH / 2);

            g.fillGradient(barX, barY, barX + fillPx, splitY, topCol, midCol);
            g.fillGradient(barX, splitY, barX + fillPx, barY + barH, midCol, botCol);
            // Crisp 1px top glossy bevel line
            g.fill(barX, barY, barX + fillPx, barY + 1, withAlpha(lerpRgb(pal.lightRgb, 0xFFFFFF, 0.55f), 210));

            // Animated gliding energy shimmer sweeping across the HP bar!
            float sweep = ((time * 0.018f) % 1.45f) - 0.22f;
            int shCenter = barX + (int) (sweep * barW);
            int shHalf = 16;
            int sx0 = Math.max(barX, shCenter - shHalf);
            int sx1 = Math.min(barX + fillPx, shCenter + shHalf);
            if (sx1 > sx0) {
                g.fill(sx0, barY, sx1, barY + barH, 0x36FFFFFF);
                int cx0 = Math.max(barX, shCenter - 5);
                int cx1 = Math.min(barX + fillPx, shCenter + 5);
                if (cx1 > cx0) {
                    g.fill(cx0, barY, cx1, barY + barH, 0x48FFFFFF);
                }
            }

            // Bright leading edge spark on the right tip of the active HP fill
            if (fillPx < barW - 1) {
                g.fill(barX + fillPx - 1, barY, barX + fillPx + 1, barY + barH, withAlpha(0xFFFFFF, 210));
            }
        }

        // 6. Crisp Segment Divider Notches (12 vạch cho Cerberus khớp NOTCHED_12, 10 vạch cho boss khác)
        int notches = (boss instanceof CerberusBossEntity) ? 12 : 10;
        for (int s = 1; s < notches; s++) {
            int nx = barX + (barW * s) / notches;
            g.fill(nx, barY + 1, nx + 1, barY + barH - 1, 0x5508050D);
        }

        // Nhấp nháy ngắn toàn thanh máu khi chuyển phase
        if (flashPulse > 0.05f) {
            int fa = Mth.clamp(Math.round(flashPulse * 115.0f), 0, 140);
            g.fill(barX, barY, barX + barW, barY + barH, withAlpha(0xFFE6A0, fa));
        }

        // Khung texture tùy chọn cho Cerberus (config cerberusCustomBossBarFrame, mặc định TẮT)
        if (boss instanceof CerberusBossEntity && !compact && CerberusConfig.get().cerberusCustomBossBarFrame) {
            g.blit(CERBERUS_CUSTOM_FRAME_TEX, centerX - 152, barY - 8, 0, 0, 304, 28, 304, 28);
        }

        // 7. Ornate Left & Right Diamond Finial Crests framing the bar!
        int midY = barY + barH / 2;
        drawDiamond(g, barX - 6, midY, 4, 0xFF0A0710);
        drawDiamond(g, barX - 6, midY, 3, frameOuter);
        drawDiamond(g, barX - 6, midY, 1, withAlpha(pal.lightRgb, 255));

        drawDiamond(g, barX + barW + 6, midY, 4, 0xFF0A0710);
        drawDiamond(g, barX + barW + 6, midY, 3, frameOuter);
        drawDiamond(g, barX + barW + 6, midY, 1, withAlpha(pal.lightRgb, 255));
    }

    /**
     * Cinematic RPG Boss Summon Awakening Banner that sweeps in smoothly during a Boss's 2.6s Summon Entrance!
     */
    private static void renderSummonAwakeningBanner(GuiGraphics g, Font font, AbstractAbyssalBoss boss,
                                                    int screenW, int screenH, float time, float partialTick) {
        float p = boss.getSpawnProgress(partialTick);
        // Smooth fade-in (0..0.18), hold (0.18..0.82), smooth fade-out (0.82..1.0)
        float alpha;
        if (p < 0.18f) alpha = smoothStep(p / 0.18f);
        else if (p > 0.82f) alpha = smoothStep((1f - p) / 0.18f);
        else alpha = 1f;
        if (alpha <= 0.03f) return;

        BossPalette pal = paletteFor(boss);
        int cx = screenW / 2;
        int cy = (int) (screenH * 0.28f);
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);

        float lineExpand = smoothStep(Mth.clamp(p * 2.2f, 0f, 1f));
        int halfLine = (int) (135 * lineExpand);

        // Dark cinematic horizontal ribbon
        int ribbonH = 38;
        g.fillGradient(cx - halfLine - 24, cy - ribbonH / 2, cx + halfLine + 24, cy + ribbonH / 2,
                withAlpha(0x090610, (int) (alpha * 165)), withAlpha(0x160E24, (int) (alpha * 205)));

        // Expanding glowing top & bottom ornamental divider lines
        int glowLine = withAlpha(lerpRgb(pal.lightRgb, 0xFFFFFF, 0.25f + 0.2f * Mth.sin(time * 0.4f)), a);
        g.fill(cx - halfLine, cy - ribbonH / 2, cx + halfLine, cy - ribbonH / 2 + 1, glowLine);
        g.fill(cx - halfLine, cy + ribbonH / 2 - 1, cx + halfLine, cy + ribbonH / 2, glowLine);

        if (halfLine > 10) {
            drawDiamond(g, cx - halfLine, cy - ribbonH / 2, 2, glowLine);
            drawDiamond(g, cx + halfLine, cy - ribbonH / 2, 2, glowLine);
            drawDiamond(g, cx - halfLine, cy + ribbonH / 2 - 1, 2, glowLine);
            drawDiamond(g, cx + halfLine, cy + ribbonH / 2 - 1, 2, glowLine);
        }

        // Top tag + Boss Name + Subtitle
        String tag = "✦  BOSS AWAKENING  ✦";
        Component nameComp = boss.hasCustomName() ? boss.getCustomName() : boss.getDisplayName();
        String bossName = nameComp != null ? nameComp.getString() : "Abyssal Boss";

        g.drawCenteredString(font, tag, cx, cy - 14, withAlpha(pal.borderRgb, a));
        g.drawCenteredString(font, bossName, cx, cy - 3, withAlpha(lerpRgb(pal.lightRgb, 0xFFFFFF, 0.45f), a));
        g.drawCenteredString(font, pal.subtitle, cx, cy + 8, withAlpha(0xC8BEDE, (int) (alpha * 220)));
    }

    private static void drawDiamond(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int span = r - Math.abs(dy);
            g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, argb);
        }
    }

    private static float smoothStep(float t) {
        float c = Mth.clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    private static int withAlpha(int rgb, int a) {
        return ((Mth.clamp(a, 0, 255) & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private static int lerpRgb(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static BossPalette paletteFor(AbstractAbyssalBoss boss) {
        if (boss instanceof QuetzalcoatlBossEntity)
            return new BossPalette(0x4CFF9E, 0x16B864, 0x0B5430, 0xFFD452, "Aztec Feathered Sky Sovereign");
        if (boss instanceof ApepBossEntity)
            return new BossPalette(0x38FFB0, 0x0FA86E, 0x084730, 0xD4B055, "Primordial Chaos Serpent");
        if (boss instanceof FenrirBossEntity)
            return new BossPalette(0xFF5A36, 0xC91C1C, 0x5E0B12, 0xB8C7D9, "Devourer of Ragnarok");
        if (boss instanceof HydraBossEntity hydra) {
            int alive = hydra.getAliveHeadCount();
            if (hydra.isEnragedVisual() || boss.getSyncedPhase() >= 2) {
                return new BossPalette(0xB46BFF, 0x4DF4FF, 0x1D2036, 0xE8FFFF,
                        "Spirit Hydra — CUỒNG BẠO (" + alive + "/" + HydraBossEntity.HEAD_COUNT + " Đầu)");
            }
            return new BossPalette(0x4DF4FF, 0x0EA5E9, 0x162436, 0xE8FFFF,
                    "Four-Headed Spectral Titan (" + alive + "/" + HydraBossEntity.HEAD_COUNT + " Đầu)");
        }
        if (boss instanceof JormungandrBossEntity jorm) {
            if (jorm.isEnragedVisual() || jorm.hasEnteredPhase2() || boss.getSyncedPhase() >= 2) {
                return new BossPalette(0xE8B8FF, 0xB838E6, 0x3D0C5E, 0xFF6B8A,
                        "Jörmungandr — CUỒNG BẠO (" + jorm.getActiveSegmentCount() + " Đốt)");
            }
            return new BossPalette(0xB7A6EE, 0x695CC8, 0x1D1A3B, 0x8A74D9,
                    "Mythic World Cobra Serpent (" + jorm.getActiveSegmentCount() + " Đốt)");
        }
        if (boss instanceof TiamatBossEntity)
            return new BossPalette(0xFF4736, 0xB81D1D, 0x520B0B, 0xE3A256, "Mother of Primordial Dragons");
        if (boss instanceof LeviathanBossEntity lev) {
            if (lev.isEnragedVisual() || boss.getSyncedPhase() >= 2) {
                return new BossPalette(0xFF5238, 0xCC1818, 0x5E0A0A, 0xFFB84D, "Abyssal Titan — CUỒNG BẠO (PHASE 2)");
            }
            return new BossPalette(0x38F2FF, 0x1096D6, 0x0A3D73, 0x5CD6FF, "Abyssal Deep-Sea Titan");
        }
        if (boss instanceof CerberusBossEntity cerb) {
            if (cerb.isEnragedVisual() || cerb.hasEnteredPhase2() || boss.getSyncedPhase() >= 2) {
                return new BossPalette(0xFF3E3E, 0xC91414, 0x590606, 0xFF7B47,
                        "Cerberus — CUỒNG BẠO (" + cerb.getSyncedGroupAliveCount() + " Cá Thể)");
            }
            return new BossPalette(0xFFA834, 0xD95814, 0x631908, 0xFFD25A, "Three-Headed Hound of the Underworld");
        }
        if (boss instanceof NightmareKnightBossEntity nk) {
            if (nk.isEnragedVisual() || nk.hasEnteredPhase2() || boss.getSyncedPhase() >= 2) {
                return new BossPalette(0xFF2E2E, 0xB80F1E, 0x3D040B, 0xFF6B35,
                        "Nightmare Knight — CUỒNG BẠO (PHASE 2)");
            }
            return new BossPalette(0xD92B4B, 0x7A1026, 0x260610, 0xB8949E,
                    "Colossal Spiked Bone-Demon Knight");
        }
        if (boss instanceof AbyssalKnightBossEntity)
            return new BossPalette(0xD585FF, 0x8932E0, 0x3D1273, 0xE6C163, "Dire Shadow Stalker");
        if (boss instanceof CryptLichBossEntity)
            return new BossPalette(0x52FFE8, 0x12A89C, 0x08484D, 0x6ECED4, "Colossal Abyssal Broodmother");
        if (boss instanceof MoltenTitanBossEntity)
            return new BossPalette(0xFFA938, 0xE64B0E, 0x6E1706, 0xFFBF47, "Volcanic Magma Hydra");
        if (boss instanceof CinderDragonBossEntity cd) {
            if (cd.isEnragedVisual() || cd.hasEnteredPhase2() || boss.getSyncedPhase() >= 2) {
                return new BossPalette(0xFF4E1A, 0xCC1610, 0x450504, 0xFFE070,
                        "Cinder Dragon — HỎA LONG VÔ FORM (PHASE 2)");
            }
            return new BossPalette(0xFF8C24, 0xB83214, 0x3D120A, 0xD98B45,
                    "Cinder Dragon — Hỏa Long Giáp Đá");
        }
        return new BossPalette(0xC084FC, 0x7E22CE, 0x3B0764, 0xE5C158, "Abyssal Spire Guardian");
    }
}

