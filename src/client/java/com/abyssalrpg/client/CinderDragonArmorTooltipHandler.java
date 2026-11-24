package com.abyssalrpg.client;

import com.abyssalrpg.combat.CinderDragonArmorHandler;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.CinderDragonArmorItem;
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
 * Hiển thị Tooltip trực quan (EN/VI) cho Bộ Giáp Cinder Dragon (`X/4` sống động theo số mảnh đang mặc):
 * Kháng Lửa, Sức Mạnh II, Tốc Độ I, Nhảy Cao I, +8 Tim (+16 Máu Tối Đa).
 */
public final class CinderDragonArmorTooltipHandler {
    private CinderDragonArmorTooltipHandler() {}

    public static void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == ModItems.CINDER_DRAGON_HEART) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_dragon_heart.desc")
                    .withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_dragon_heart.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (item == ModItems.CINDER_DRAGON_SCALE) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_dragon_scale.desc")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_dragon_scale.source")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!(item instanceof CinderDragonArmorItem)) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cinderEnableSetBonus) {
            return;
        }

        int equipped = CinderDragonArmorHandler.countEquippedPieces(Minecraft.getInstance().player);
        boolean active = equipped >= 4;

        ChatFormatting headerColor = active ? ChatFormatting.GOLD : ChatFormatting.DARK_RED;
        ChatFormatting detailColor = active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;

        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.header", equipped)
                .withStyle(headerColor, ChatFormatting.BOLD));
        if (cfg.cinderSetBonusFireResistance) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.fire_resistance")
                    .withStyle(detailColor));
        }
        if (cfg.cinderSetBonusResistance) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.resistance",
                            toRoman(cfg.cinderSetBonusResistanceAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.cinderSetBonusStrength) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.strength",
                            toRoman(cfg.cinderSetBonusStrengthAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.cinderSetBonusSpeed) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.speed",
                            toRoman(cfg.cinderSetBonusSpeedAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.cinderSetBonusJump) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.jump",
                            toRoman(cfg.cinderSetBonusJumpAmplifier + 1))
                    .withStyle(detailColor));
        }
        if (cfg.cinderSetBonusMaxHealth > 0.0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.max_health",
                            formatNumber(cfg.cinderSetBonusMaxHealth / 2.0),
                            formatNumber(cfg.cinderSetBonusMaxHealth))
                    .withStyle(detailColor));
        }
        if (cfg.cinderSetBonusAttackDamageBonus > 0.0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.attack_damage",
                            formatNumber(cfg.cinderSetBonusAttackDamageBonus))
                    .withStyle(detailColor));
        }
        if (cfg.cinderSetBonusIgniteSeconds > 0) {
            lines.add(Component.translatable("tooltip.abyssalrpg.cinder_set.ignite",
                            cfg.cinderSetBonusIgniteSeconds)
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
