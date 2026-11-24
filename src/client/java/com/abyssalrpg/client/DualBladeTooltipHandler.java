package com.abyssalrpg.client;

import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.registry.ModEnchantments;
import com.abyssalrpg.registry.ModItems;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener `ItemTooltipCallback.EVENT` phía Client:
 * HIỂN THỊ NGAY mô tả tác dụng & con số `%` rõ ràng (KHÔNG cần giữ Shift!) cho:
 * 1. Mọi cuốn Sách Enchant (`minecraft:enchanted_book`) chứa 1 trong 4 enchant Dual Blade mới.
 * 2. Chính thanh `abyssalrpg:dual_blade` với danh sách chi tiết công dụng `%` của từng enchant đang ép trên kiếm!
 */
public final class DualBladeTooltipHandler {
    private DualBladeTooltipHandler() {}

    public static void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (stack.isEmpty()) {
            return;
        }
        boolean isBook = stack.is(Items.ENCHANTED_BOOK);
        boolean isDualKnife = stack.is(ModItems.DUAL_KNIFE) || stack.is(ModItems.TOGETSU);
        if (!isBook && !isDualKnife) {
            return;
        }

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) {
            return;
        }

        WeaponsConfig cfg = WeaponsConfig.get();
        List<Component> descLines = new ArrayList<>();

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            if (level <= 0) {
                continue;
            }
            String roman = toRoman(level);
            if (holder.is(ModEnchantments.LIFESTEAL)) {
                int pct = Math.round(cfg.getLifestealPercent(level) * 100.0F);
                descLines.add(Component.translatable("tooltip.abyssalrpg.enchant.lifesteal", roman, pct)
                        .withStyle(ChatFormatting.GREEN));
            } else if (holder.is(ModEnchantments.ARMOR_PIERCE)) {
                int pct = Math.round(cfg.getArmorPiercePercent(level) * 100.0F);
                descLines.add(Component.translatable("tooltip.abyssalrpg.enchant.armor_pierce", roman, pct)
                        .withStyle(ChatFormatting.AQUA));
            } else if (holder.is(ModEnchantments.CRITICAL_STRIKE)) {
                int pct = Math.round(cfg.getCriticalChance(level) * 100.0F);
                descLines.add(Component.translatable("tooltip.abyssalrpg.enchant.critical_strike", roman, pct)
                        .withStyle(ChatFormatting.YELLOW));
            } else if (holder.is(ModEnchantments.DEEP_WOUNDS)) {
                int pct = Math.round(cfg.getDeepWoundsBonus(level) * 100.0F);
                descLines.add(Component.translatable("tooltip.abyssalrpg.enchant.deep_wounds", roman, pct)
                        .withStyle(ChatFormatting.RED));
            }
        }

        if (descLines.isEmpty()) {
            return;
        }

        // Chèn ngay phía trên dòng attribute (nếu có) hoặc cuối tooltip để người chơi đọc ngay không cần giữ Shift
        int insertAt = lines.size();
        for (int i = 1; i < lines.size(); i++) {
            String plain = lines.get(i).getString();
            if (plain.isEmpty() && i + 1 < lines.size()) {
                insertAt = i;
                break;
            }
        }
        lines.addAll(insertAt, descLines);
    }

    private static String toRoman(int lv) {
        return switch (lv) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(lv);
        };
    }
}

