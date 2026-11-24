package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Khóa data-driven (Minecraft 1.21.1) cho bộ 4 Enchantment chuyên biệt của Dual Knife (`#abyssalrpg:enchantable/dual_knife`):
 * 1. `abyssalrpg:lifesteal` (Hút Máu I–III: 5% / 10% / 15% sát thương thực tế)
 * 2. `abyssalrpg:armor_pierce` (Xuyên Thủ I–III: 5% / 12% / 20% giáp vanilla & BossDefenseHolder)
 * 3. `abyssalrpg:critical_strike` (Chí Mạng I–III: 5% / 10% / 15% tỉ lệ x1.5 sát thương)
 * 4. `abyssalrpg:deep_wounds` (Xé Vết Thương I–III: +5% / +10% / +15% sát thương lên mục tiêu Chảy Máu & Bleed DPS)
 */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> LIFESTEAL = key("lifesteal");
    public static final ResourceKey<Enchantment> ARMOR_PIERCE = key("armor_pierce");
    public static final ResourceKey<Enchantment> CRITICAL_STRIKE = key("critical_strike");
    public static final ResourceKey<Enchantment> DEEP_WOUNDS = key("deep_wounds");

    public static final List<ResourceKey<Enchantment>> ALL_DUAL_KNIFE_ENCHANTS = List.of(
            LIFESTEAL,
            ARMOR_PIERCE,
            CRITICAL_STRIKE,
            DEEP_WOUNDS
    );

    /** Chỉ chứa `abyssalrpg:dual_knife` -> đảm bảo 4 enchant độc quyền KHÔNG thể ép vào kiếm/rìu vanilla. */
    public static final TagKey<Item> DUAL_KNIFE_ENCHANTABLE =
            TagKey.create(Registries.ITEM, AbyssalRpgMod.id("enchantable/dual_knife"));

    /** Vật liệu sửa chữa Dual Knife trên Anvil (mặc định `minecraft:diamond`). */
    public static final TagKey<Item> DUAL_KNIFE_REPAIR_ITEMS =
            TagKey.create(Registries.ITEM, AbyssalRpgMod.id("dual_knife_repair_items"));

    /** Tập sách phù phép Dual Knife dùng trong loot pool của toàn bộ 6 Boss. */
    public static final TagKey<Enchantment> DUAL_KNIFE_BOSS_BOOKS =
            TagKey.create(Registries.ENCHANTMENT, AbyssalRpgMod.id("dual_knife_boss_books"));

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, AbyssalRpgMod.id(path));
    }

    public static int getLevel(Level level, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty() || level == null) {
            return 0;
        }
        Optional<HolderLookup.RegistryLookup<Enchantment>> optLookup =
                level.registryAccess().lookup(Registries.ENCHANTMENT);
        if (optLookup.isEmpty()) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> holder = optLookup.get().get(key);
        return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    private ModEnchantments() {}
}
