package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

public final class ModCreativeTab {
    public static final CreativeModeTab ABYSSAL_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            AbyssalRpgMod.id("abyssal_tab"),
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.abyssalrpg.main"))
                    .icon(() -> new ItemStack(ModItems.DUAL_KNIFE != null ? ModItems.DUAL_KNIFE : ModItems.OVERLORD_CROWN))
                    .displayItems((params, output) -> {
                        // Signature Two-Handed Weapon (`abyssalrpg:dual_knife`) & Endgame Nightmare Knight Armor Set
                        if (ModItems.DUAL_KNIFE != null) {
                            output.accept(ModItems.DUAL_KNIFE);
                        }
                        if (ModItems.NIGHTMARE_HELMET != null) {
                            output.accept(ModItems.NIGHTMARE_HELMET);
                            output.accept(ModItems.NIGHTMARE_CHESTPLATE);
                            output.accept(ModItems.NIGHTMARE_LEGGINGS);
                            output.accept(ModItems.NIGHTMARE_BOOTS);
                        }
                        output.accept(ModItems.NIGHTMARE_CORE);
                        output.accept(ModItems.NIGHTMARE_ARMOR_SHARD);
                        params.holders().lookup(Registries.ENCHANTMENT).ifPresent(enchantRegistry -> {
                            for (ResourceKey<Enchantment> key : ModEnchantments.ALL_DUAL_KNIFE_ENCHANTS) {
                                enchantRegistry.get(key).ifPresent(holder -> {
                                    int maxLv = holder.value().getMaxLevel();
                                    for (int lv = 1; lv <= maxLv; lv++) {
                                        output.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, lv)));
                                    }
                                });
                            }
                        });

                        // Boss Temple Blocks
                        output.accept(ModBlocks.MYTHIC_SUMMON_ALTAR);
                        output.accept(ModBlocks.RUNIC_DEEPSLATE);
                        output.accept(ModBlocks.ABYSSAL_BRICK);

                        // Boss Relics & Trophy Drops
                        output.accept(ModItems.SOUL_SHARD);
                        output.accept(ModItems.ABYSSAL_CORE);
                        output.accept(ModItems.OVERLORD_CROWN);
                        output.accept(ModItems.SPIRIT_HYDRA_FANG);
                        output.accept(ModItems.CERBERUS_HELLFANG);
                        output.accept(ModItems.NIGHTMARE_HEART);
                        output.accept(ModItems.JORMUNGANDR_VENOM_FANG);

                        // All 13 Boss Spawn Eggs
                        output.accept(ModItems.ABYSSAL_KNIGHT_SPAWN_EGG);
                        output.accept(ModItems.CRYPT_LICH_SPAWN_EGG);
                        output.accept(ModItems.MOLTEN_TITAN_SPAWN_EGG);
                        output.accept(ModItems.CINDER_DRAGON_SPAWN_EGG);
                        output.accept(ModItems.QUETZALCOATL_SPAWN_EGG);
                        output.accept(ModItems.APEP_SPAWN_EGG);
                        output.accept(ModItems.FENRIR_SPAWN_EGG);
                        output.accept(ModItems.HYDRA_SPAWN_EGG);
                        output.accept(ModItems.JORMUNGANDR_SPAWN_EGG);
                        output.accept(ModItems.TIAMAT_SPAWN_EGG);
                        output.accept(ModItems.LEVIATHAN_SPAWN_EGG);
                        output.accept(ModItems.CERBERUS_SPAWN_EGG);
                        output.accept(ModItems.NIGHTMARE_KNIGHT_SPAWN_EGG);
                    })
                    .build()
    );

    public static void init() {
    }

    private ModCreativeTab() {}
}
