package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.item.CerberusArmorItem;
import com.abyssalrpg.item.CerberusHeartItem;
import com.abyssalrpg.item.CinderDragonArmorItem;
import com.abyssalrpg.item.CinderDragonHeartItem;
import com.abyssalrpg.item.DualBladeItem;
import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.item.NightmareArmorItem;
import com.abyssalrpg.item.NightmareCoreItem;
import com.abyssalrpg.item.SpiritHydraArmorItem;
import com.abyssalrpg.item.SpiritHydraHeartItem;
import com.abyssalrpg.item.TogetsuItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

public final class ModItems {
    // Boss Relics & Trophies
    public static final Item SOUL_SHARD = register("soul_shard",
            new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final Item ABYSSAL_CORE = register("abyssal_core",
            new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    public static final Item OVERLORD_CROWN = register("overlord_crown",
            new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));

    public static final Item SPIRIT_HYDRA_FANG = register("spirit_hydra_fang",
            new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    public static final Item CERBERUS_HELLFANG = register("cerberus_hellfang",
            new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final Item NIGHTMARE_HEART = register("nightmare_heart",
            new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant()));

    public static final Item JORMUNGANDR_VENOM_FANG = register("jormungandr_venom_fang",
            new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant()));

    // Nightmare Knight Boss Armor Materials (Phase 2 Drops)
    public static final Item NIGHTMARE_CORE = register("nightmare_core", new NightmareCoreItem());
    public static final Item NIGHTMARE_ARMOR_SHARD = register("nightmare_armor_shard",
            new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    // Cinder Dragon Boss Armor Materials (Phase 2 Drops)
    public static final Item CINDER_DRAGON_HEART = register("cinder_dragon_heart", new CinderDragonHeartItem());
    public static final Item CINDER_DRAGON_SCALE = register("cinder_dragon_scale",
            new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    // Spirit Hydra Boss Armor Materials (Phase 2 Drops)
    public static final Item SPIRIT_HYDRA_HEART = register("spirit_hydra_heart", new SpiritHydraHeartItem());
    public static final Item SPIRIT_HYDRA_SCALE = register("spirit_hydra_scale",
            new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    // Cerberus Boss Armor Materials (Phase 2 Drops)
    public static final Item CERBERUS_HEART = register("cerberus_heart", new CerberusHeartItem());
    public static final Item CERBERUS_HIDE = register("cerberus_hide",
            new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    // Endgame 4-Piece Nightmare Knight 3D Armor Set (GeckoLib GeoArmorRenderer)
    public static Item NIGHTMARE_HELMET;
    public static Item NIGHTMARE_CHESTPLATE;
    public static Item NIGHTMARE_LEGGINGS;
    public static Item NIGHTMARE_BOOTS;

    // Endgame 4-Piece Cinder Dragon 3D Armor Set (GeckoLib GeoArmorRenderer + AutoGlowingGeoLayer)
    public static Item CINDER_DRAGON_HELMET;
    public static Item CINDER_DRAGON_CHESTPLATE;
    public static Item CINDER_DRAGON_LEGGINGS;
    public static Item CINDER_DRAGON_BOOTS;

    // Endgame 4-Piece Spirit Hydra 3D Armor Set (GeckoLib GeoArmorRenderer + AutoGlowingGeoLayer)
    public static Item SPIRIT_HYDRA_HELMET;
    public static Item SPIRIT_HYDRA_CHESTPLATE;
    public static Item SPIRIT_HYDRA_LEGGINGS;
    public static Item SPIRIT_HYDRA_BOOTS;

    // Endgame 4-Piece Cerberus Netherite 3D Armor Set (GeckoLib GeoArmorRenderer + Emissive Layer)
    public static Item CERBERUS_HELMET;
    public static Item CERBERUS_CHESTPLATE;
    public static Item CERBERUS_LEGGINGS;
    public static Item CERBERUS_BOOTS;

    // Signature Two-Handed Weapons (`abyssalrpg:dual_knife`, `abyssalrpg:togetsu`, & `abyssalrpg:excalibur`)
    public static Item DUAL_KNIFE;
    public static Item TOGETSU;
    public static Item EXCALIBUR;

    // Boss Spawn Eggs (13 Colossal Bosses: 6 GeckoLib + 3 Core Sculpted + 4 Mythic Sculpted)
    public static Item ABYSSAL_KNIGHT_SPAWN_EGG;
    public static Item CRYPT_LICH_SPAWN_EGG;
    public static Item MOLTEN_TITAN_SPAWN_EGG;
    public static Item CINDER_DRAGON_SPAWN_EGG;
    public static Item QUETZALCOATL_SPAWN_EGG;
    public static Item APEP_SPAWN_EGG;
    public static Item FENRIR_SPAWN_EGG;
    public static Item HYDRA_SPAWN_EGG;
    public static Item JORMUNGANDR_SPAWN_EGG;
    public static Item TIAMAT_SPAWN_EGG;
    public static Item LEVIATHAN_SPAWN_EGG;
    public static Item CERBERUS_SPAWN_EGG;
    public static Item NIGHTMARE_KNIGHT_SPAWN_EGG;

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, AbyssalRpgMod.id(name), item);
    }

    public static void init() {
        ModArmorMaterials.init();
        NIGHTMARE_HELMET = register("nightmare_helmet", new NightmareArmorItem(ArmorItem.Type.HELMET));
        NIGHTMARE_CHESTPLATE = register("nightmare_chestplate", new NightmareArmorItem(ArmorItem.Type.CHESTPLATE));
        NIGHTMARE_LEGGINGS = register("nightmare_leggings", new NightmareArmorItem(ArmorItem.Type.LEGGINGS));
        NIGHTMARE_BOOTS = register("nightmare_boots", new NightmareArmorItem(ArmorItem.Type.BOOTS));

        CINDER_DRAGON_HELMET = register("cinder_dragon_helmet", new CinderDragonArmorItem(ArmorItem.Type.HELMET));
        CINDER_DRAGON_CHESTPLATE = register("cinder_dragon_chestplate", new CinderDragonArmorItem(ArmorItem.Type.CHESTPLATE));
        CINDER_DRAGON_LEGGINGS = register("cinder_dragon_leggings", new CinderDragonArmorItem(ArmorItem.Type.LEGGINGS));
        CINDER_DRAGON_BOOTS = register("cinder_dragon_boots", new CinderDragonArmorItem(ArmorItem.Type.BOOTS));

        SPIRIT_HYDRA_HELMET = register("spirit_hydra_helmet", new SpiritHydraArmorItem(ArmorItem.Type.HELMET));
        SPIRIT_HYDRA_CHESTPLATE = register("spirit_hydra_chestplate", new SpiritHydraArmorItem(ArmorItem.Type.CHESTPLATE));
        SPIRIT_HYDRA_LEGGINGS = register("spirit_hydra_leggings", new SpiritHydraArmorItem(ArmorItem.Type.LEGGINGS));
        SPIRIT_HYDRA_BOOTS = register("spirit_hydra_boots", new SpiritHydraArmorItem(ArmorItem.Type.BOOTS));

        CERBERUS_HELMET = register("cerberus_helmet", new CerberusArmorItem(ArmorItem.Type.HELMET));
        CERBERUS_CHESTPLATE = register("cerberus_chestplate", new CerberusArmorItem(ArmorItem.Type.CHESTPLATE));
        CERBERUS_LEGGINGS = register("cerberus_leggings", new CerberusArmorItem(ArmorItem.Type.LEGGINGS));
        CERBERUS_BOOTS = register("cerberus_boots", new CerberusArmorItem(ArmorItem.Type.BOOTS));

        DUAL_KNIFE = register("dual_knife", new DualBladeItem());
        TOGETSU = register("togetsu", new TogetsuItem());
        EXCALIBUR = register("excalibur", new ExcaliburItem());

        ABYSSAL_KNIGHT_SPAWN_EGG = register("abyssal_knight_spawn_egg",
                new SpawnEggItem(ModEntities.ABYSSAL_KNIGHT_BOSS, 0x1D1A2B, 0x9B2CFF, new Item.Properties()));
        CRYPT_LICH_SPAWN_EGG = register("crypt_lich_spawn_egg",
                new SpawnEggItem(ModEntities.CRYPT_LICH_BOSS, 0x162A2C, 0x35F2D6, new Item.Properties()));
        MOLTEN_TITAN_SPAWN_EGG = register("molten_titan_spawn_egg",
                new SpawnEggItem(ModEntities.MOLTEN_TITAN_BOSS, 0x2B1610, 0xFF6B1A, new Item.Properties()));
        CINDER_DRAGON_SPAWN_EGG = register("cinder_dragon_spawn_egg",
                new SpawnEggItem(ModEntities.CINDER_DRAGON, 0x3D3530, 0xD43818, new Item.Properties()));
        QUETZALCOATL_SPAWN_EGG = register("quetzalcoatl_spawn_egg",
                new SpawnEggItem(ModEntities.QUETZALCOATL_BOSS, 0x168A3E, 0xFF2E93, new Item.Properties()));
        APEP_SPAWN_EGG = register("apep_spawn_egg",
                new SpawnEggItem(ModEntities.APEP_BOSS, 0x1E2623, 0x1BFF9E, new Item.Properties()));
        FENRIR_SPAWN_EGG = register("fenrir_spawn_egg",
                new SpawnEggItem(ModEntities.FENRIR_BOSS, 0x171B21, 0xFF3812, new Item.Properties()));
        HYDRA_SPAWN_EGG = register("hydra_spawn_egg",
                new SpawnEggItem(ModEntities.HYDRA_BOSS, 0x142622, 0xD4FF7A, new Item.Properties()));
        JORMUNGANDR_SPAWN_EGG = register("jormungandr_spawn_egg",
                new SpawnEggItem(ModEntities.JORMUNGANDR_BOSS, 0x17171C, 0x8A74D9, new Item.Properties()));
        TIAMAT_SPAWN_EGG = register("tiamat_spawn_egg",
                new SpawnEggItem(ModEntities.TIAMAT_BOSS, 0x241719, 0xFF2222, new Item.Properties()));
        LEVIATHAN_SPAWN_EGG = register("leviathan_spawn_egg",
                new SpawnEggItem(ModEntities.LEVIATHAN_BOSS, 0x0E2233, 0x1AE8FF, new Item.Properties()));
        CERBERUS_SPAWN_EGG = register("cerberus_spawn_egg",
                new SpawnEggItem(ModEntities.CERBERUS, 0x1B0C0E, 0xFF3A14, new Item.Properties()));
        NIGHTMARE_KNIGHT_SPAWN_EGG = register("nightmare_knight_spawn_egg",
                new SpawnEggItem(ModEntities.NIGHTMARE_KNIGHT, 0x14080C, 0xD91628, new Item.Properties()));
    }

    private ModItems() {}
}
