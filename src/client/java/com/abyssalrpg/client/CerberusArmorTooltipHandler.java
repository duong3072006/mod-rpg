package com.abyssalrpg.client;

import com.abyssalrpg.combat.CerberusArmorHandler;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.CerberusArmorItem;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * Hiển thị Tooltip trực quan (EN/VI) cho Bộ Giáp Cerberus (`X/4` sống động theo số mảnh đang mặc)
 * và nguồn rơi từ Boss Cerberus Phase 2 cho `cerberus_heart` & `cerberus_hide`.
 */
public final class CerberusArmorTooltipHandler {
    private CerberusArmorTooltipHandler() {}

    public static void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == ModItems.CERBERUS_HEART) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_heart.desc")
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_heart.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (item == ModItems.CERBERUS_HIDE) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_hide.desc")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_hide.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!(item instanceof CerberusArmorItem)) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cerberusEnableSetBonus) {
            return;
        }

        int equipped = CerberusArmorHandler.countEquippedPieces(Minecraft.getInstance().player);
        boolean active = equipped >= 4;

        ChatFormatting headerColor = active ? ChatFormatting.RED : ChatFormatting.DARK_RED;
        ChatFormatting detailColor = active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;

        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_set.header", equipped)
                .withStyle(headerColor, ChatFormatting.BOLD));
        if (cfg.cerberusSetBonusFireResistance) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_set.fire_resistance")
                    .withStyle(detailColor));
        }
        if (cfg.cerberusSetBonusSpeed) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_set.speed",
                            toRoman(cfg.cerberusSetBonusSpeedAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.cerberusSetBonusStrength) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_set.strength",
                            toRoman(cfg.cerberusSetBonusStrengthAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.cerberusSetBonusMaxHealth > 0.0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_set.max_health",
                            formatNumber(cfg.cerberusSetBonusMaxHealth))
                    .withStyle(detailColor));
        }
        if (cfg.cerberusSetBonusIgniteSeconds > 0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_set.ignite",
                            cfg.cerberusSetBonusIgniteSeconds)
                    .withStyle(detailColor));
        }
        lines.add(Component.translatable("tooltip.abyssalrpg.cerberus_set.blades")
                .withStyle(active ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}

