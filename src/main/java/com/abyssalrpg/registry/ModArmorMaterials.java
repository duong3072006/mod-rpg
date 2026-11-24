package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.ArmorConfig;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;

/**
 * Đăng ký chuẩn MC 1.21.1 `Holder<ArmorMaterial>` vào `BuiltInRegistries.ARMOR_MATERIAL`.
 * Vật liệu sửa trên Đe (Anvil): `abyssalrpg:nightmare_armor_shard`.
 */
public final class ModArmorMaterials {
    public static Holder<ArmorMaterial> NIGHTMARE_KNIGHT;

    public static void init() {
        ArmorConfig cfg = ArmorConfig.get();
        EnumMap<ArmorItem.Type, Integer> defense = Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.BOOTS, cfg.bootsDefense);
            map.put(ArmorItem.Type.LEGGINGS, cfg.leggingsDefense);
            map.put(ArmorItem.Type.CHESTPLATE, cfg.chestplateDefense);
            map.put(ArmorItem.Type.HELMET, cfg.helmetDefense);
            map.put(ArmorItem.Type.BODY, cfg.chestplateDefense + 2);
        });

        ArmorMaterial material = new ArmorMaterial(
                defense,
                cfg.enchantmentValue,
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(ModItems.NIGHTMARE_ARMOR_SHARD),
                List.of(new ArmorMaterial.Layer(AbyssalRpgMod.id("nightmare_knight"))),
                cfg.toughness,
                cfg.knockbackResistance
        );

        NIGHTMARE_KNIGHT = Registry.registerForHolder(
                BuiltInRegistries.ARMOR_MATERIAL,
                AbyssalRpgMod.id("nightmare_knight"),
                material
        );
    }

    private ModArmorMaterials() {}
}

