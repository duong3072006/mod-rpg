package com.abyssalrpg.client;

import com.abyssalrpg.combat.NightmareArmorHandler;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.NightmareArmorItem;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Hiển thị Tooltip trực quan (EN/VI) cho Bộ Giáp Nightmare Knight (`X/4` sống động theo số mảnh đang mặc)
 * cũng như mô tả nguồn rơi từ Boss Nightmare Knight Phase 2 cho `nightmare_core` & `nightmare_armor_shard`.
 */
public final class NightmareArmorTooltipHandler {
    private NightmareArmorTooltipHandler() {}

    public static void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == ModItems.NIGHTMARE_CORE) {
            lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_core.desc")
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_core.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (item == ModItems.NIGHTMARE_ARMOR_SHARD) {
            lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_armor_shard.desc")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_armor_shard.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!(item instanceof NightmareArmorItem)) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.enableSetBonus) {
            return;
        }

        int equipped = NightmareArmorHandler.countEquippedPieces(Minecraft.getInstance().player);
        boolean active = equipped >= 4;

        ChatFormatting headerColor = active ? ChatFormatting.GOLD : ChatFormatting.DARK_RED;
        ChatFormatting detailColor = active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;

        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_set.header", equipped)
                .withStyle(headerColor, ChatFormatting.BOLD));
        if (cfg.setBonusFireResistance) {
            lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_set.fire_resistance")
                    .withStyle(detailColor));
        }
        if (cfg.setBonusResistance) {
            lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_set.resistance",
                            romanLevel(cfg.setBonusResistanceAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.setBonusMaxHealth > 0.0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.nightmare_set.max_health",
                            formatHealthAmount(cfg.setBonusMaxHealth))
                    .withStyle(detailColor));
        }
    }

    private static String romanLevel(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    private static String formatHealthAmount(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

