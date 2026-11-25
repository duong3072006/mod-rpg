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
    public static Holder<ArmorMaterial> CINDER_DRAGON;
    public static Holder<ArmorMaterial> SPIRIT_HYDRA;

    public static void init() {
        ArmorConfig cfg = ArmorConfig.get();
        EnumMap<ArmorItem.Type, Integer> nightmareDef = Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.BOOTS, cfg.bootsDefense);
            map.put(ArmorItem.Type.LEGGINGS, cfg.leggingsDefense);
            map.put(ArmorItem.Type.CHESTPLATE, cfg.chestplateDefense);
            map.put(ArmorItem.Type.HELMET, cfg.helmetDefense);
            map.put(ArmorItem.Type.BODY, cfg.chestplateDefense + 2);
        });

        ArmorMaterial nightmareMat = new ArmorMaterial(
                nightmareDef,
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
                nightmareMat
        );

        EnumMap<ArmorItem.Type, Integer> cinderDef = Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.BOOTS, cfg.cinderBootsDefense);
            map.put(ArmorItem.Type.LEGGINGS, cfg.cinderLeggingsDefense);
            map.put(ArmorItem.Type.CHESTPLATE, cfg.cinderChestplateDefense);
            map.put(ArmorItem.Type.HELMET, cfg.cinderHelmetDefense);
            map.put(ArmorItem.Type.BODY, cfg.cinderChestplateDefense + 2);
        });

        ArmorMaterial cinderMat = new ArmorMaterial(
                cinderDef,
                cfg.cinderEnchantmentValue,
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(ModItems.CINDER_DRAGON_SCALE),
                List.of(new ArmorMaterial.Layer(AbyssalRpgMod.id("cinder_dragon"))),
                cfg.cinderToughness,
                cfg.cinderKnockbackResistance
        );

        CINDER_DRAGON = Registry.registerForHolder(
                BuiltInRegistries.ARMOR_MATERIAL,
                AbyssalRpgMod.id("cinder_dragon"),
                cinderMat
        );

        EnumMap<ArmorItem.Type, Integer> hydraDef = Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.BOOTS, cfg.hydraBootsDefense);
            map.put(ArmorItem.Type.LEGGINGS, cfg.hydraLeggingsDefense);
            map.put(ArmorItem.Type.CHESTPLATE, cfg.hydraChestplateDefense);
            map.put(ArmorItem.Type.HELMET, cfg.hydraHelmetDefense);
            map.put(ArmorItem.Type.BODY, cfg.hydraChestplateDefense + 2);
        });

        ArmorMaterial hydraMat = new ArmorMaterial(
                hydraDef,
                cfg.hydraEnchantmentValue,
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(ModItems.SPIRIT_HYDRA_SCALE),
                List.of(new ArmorMaterial.Layer(AbyssalRpgMod.id("spirit_hydra"))),
                cfg.hydraToughness,
                cfg.hydraKnockbackResistance
        );

        SPIRIT_HYDRA = Registry.registerForHolder(
                BuiltInRegistries.ARMOR_MATERIAL,
                AbyssalRpgMod.id("spirit_hydra"),
                hydraMat
        );
    }

    private ModArmorMaterials() {}
}

