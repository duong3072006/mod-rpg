package com.abyssalrpg.client;

import com.abyssalrpg.combat.SpiritHydraArmorHandler;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.SpiritHydraArmorItem;
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
 * Hiển thị Tooltip trực quan (EN/VI) cho Bộ Giáp Spirit Hydra (`X/4` sống động theo số mảnh đang mặc)
 * và nguồn rơi từ Boss Spirit Hydra Phase 2 cho `spirit_hydra_heart` & `spirit_hydra_scale`.
 */
public final class SpiritHydraArmorTooltipHandler {
    private SpiritHydraArmorTooltipHandler() {}

    public static void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == ModItems.SPIRIT_HYDRA_HEART) {
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_heart.desc")
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_heart.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (item == ModItems.SPIRIT_HYDRA_SCALE) {
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_scale.desc")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_scale.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!(item instanceof SpiritHydraArmorItem)) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.hydraEnableSetBonus) {
            return;
        }

        int equipped = SpiritHydraArmorHandler.countEquippedPieces(Minecraft.getInstance().player);
        boolean active = equipped >= 4;

        ChatFormatting headerColor = active ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA;
        ChatFormatting detailColor = active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;

        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_set.header", equipped)
                .withStyle(headerColor, ChatFormatting.BOLD));
        if (cfg.hydraSetBonusRegeneration) {
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_set.regeneration",
                            toRoman(cfg.hydraSetBonusRegenerationAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.hydraSetBonusWaterBreathing) {
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_set.water_breathing")
                    .withStyle(detailColor));
        }
        if (cfg.hydraSetBonusMaxHealth > 0.0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_set.max_health",
                            formatNumber(cfg.hydraSetBonusMaxHealth))
                    .withStyle(detailColor));
        }
        if (cfg.hydraSetBonusPoisonSeconds > 0 || cfg.hydraSetBonusSoulFlameSeconds > 0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.spirit_hydra_set.on_hit",
                            toRoman(cfg.hydraSetBonusPoisonAmplifier + 1),
                            cfg.hydraSetBonusPoisonSeconds,
                            cfg.hydraSetBonusSoulFlameSeconds)
                    .withStyle(detailColor));
        }
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

