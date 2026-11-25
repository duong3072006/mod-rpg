package com.abyssalrpg.client.datagen;

import com.abyssalrpg.registry.ModDamageTypes;
import com.abyssalrpg.registry.ModEnchantments;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric Data Generator cho Abyssal Spire RPG (MC 1.21.1):
 * - Sinh dữ liệu data-driven cho bộ 4 Enchantment mới chuyên biệt của Dual Blade (`#abyssalrpg:enchantable/dual_blade`):
 *   `lifesteal`, `armor_pierce`, `critical_strike`, `deep_wounds` (đều có Cấp I–III, mốc Cấp I = 5%).
 * - Sinh DamageType `abyssalrpg:bleeding` cùng tag bỏ giáp/không giật lùi/không khóa i-frame.
 * - Sinh Item Tags (`enchantable/dual_blade`, `dual_blade_repair_items`, `minecraft:enchantable/durability`),
 *   Enchantment Tags (`dual_blade_boss_books`, `in_enchanting_table`, `non_treasure`), và recipe `dual_blade.json`.
 */
public final class AbyssalRpgDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(ModDynamicRegistryProvider::new);
        pack.addProvider(ModItemTagProvider::new);
        pack.addProvider(ModEnchantmentTagProvider::new);
        pack.addProvider(ModDamageTypeTagProvider::new);
        pack.addProvider(ModRecipeProvider::new);
    }

    @Override
    public void buildRegistry(RegistrySetBuilder registryBuilder) {
        registryBuilder.add(Registries.DAMAGE_TYPE, context -> context.register(
                ModDamageTypes.BLEEDING,
                new DamageType("abyssalrpg.bleeding", DamageScaling.NEVER, 0.0F, DamageEffects.HURT)
        ));

        registryBuilder.add(Registries.ENCHANTMENT, context -> {
            HolderGetter<Item> items = context.lookup(Registries.ITEM);
            HolderSet<Item> dualKnifeOnly = items.getOrThrow(ModEnchantments.DUAL_KNIFE_ENCHANTABLE);

            // 1. Lifesteal (Hút Máu, Lv I–III: 5% / 10% / 15% sát thương thực tế)
            context.register(
                    ModEnchantments.LIFESTEAL,
                    Enchantment.enchantment(Enchantment.definition(
                            dualKnifeOnly,
                            4,
                            3,
                            Enchantment.dynamicCost(14, 10),
                            Enchantment.dynamicCost(48, 10),
                            4,
                            EquipmentSlotGroup.MAINHAND
                    )).build(ModEnchantments.LIFESTEAL.location())
            );

            // 2. Armor Pierce (Xuyên Thủ, Lv I–III: 5% / 12% / 20% giáp vanilla & BossDefenseHolder)
            context.register(
                    ModEnchantments.ARMOR_PIERCE,
                    Enchantment.enchantment(Enchantment.definition(
                            dualKnifeOnly,
                            4,
                            3,
                            Enchantment.dynamicCost(15, 10),
                            Enchantment.dynamicCost(50, 10),
                            4,
                            EquipmentSlotGroup.MAINHAND
                    )).build(ModEnchantments.ARMOR_PIERCE.location())
            );

            // 3. Critical Strike (Chí Mạng, Lv I–III: 5% / 10% / 15% tỉ lệ x1.5 sát thương)
            context.register(
                    ModEnchantments.CRITICAL_STRIKE,
                    Enchantment.enchantment(Enchantment.definition(
                            dualKnifeOnly,
                            5,
                            3,
                            Enchantment.dynamicCost(12, 9),
                            Enchantment.dynamicCost(46, 9),
                            4,
                            EquipmentSlotGroup.MAINHAND
                    )).build(ModEnchantments.CRITICAL_STRIKE.location())
            );

            // 4. Deep Wounds (Xé Vết Thương, Lv I–III: +5% / +10% / +15% sát thương lên mục tiêu Chảy Máu & Bleed DPS)
            context.register(
                    ModEnchantments.DEEP_WOUNDS,
                    Enchantment.enchantment(Enchantment.definition(
                            dualKnifeOnly,
                            5,
                            3,
                            Enchantment.dynamicCost(12, 9),
                            Enchantment.dynamicCost(45, 9),
                            4,
                            EquipmentSlotGroup.MAINHAND
                    )).build(ModEnchantments.DEEP_WOUNDS.location())
            );
        });
    }

    private static final class ModDynamicRegistryProvider extends FabricDynamicRegistryProvider {
        ModDynamicRegistryProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        protected void configure(HolderLookup.Provider registries, Entries entries) {
            entries.addAll(registries.lookupOrThrow(Registries.DAMAGE_TYPE));
            entries.addAll(registries.lookupOrThrow(Registries.ENCHANTMENT));
        }

        @Override
        public String getName() {
            return "Abyssal Spire RPG Dynamic Registries";
        }
    }

    private static final class ModItemTagProvider extends FabricTagProvider.ItemTagProvider {
        ModItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        protected void addTags(HolderLookup.Provider wrapperLookup) {
            getOrCreateTagBuilder(ModEnchantments.DUAL_KNIFE_ENCHANTABLE)
                    .add(ModItems.DUAL_KNIFE);

            getOrCreateTagBuilder(ModEnchantments.DUAL_KNIFE_REPAIR_ITEMS)
                    .add(Items.DIAMOND);

            // Hỗ trợ Unbreaking & Mending cho Dual Knife, Giáp Nightmare Knight, Cinder Dragon & Spirit Hydra
            getOrCreateTagBuilder(ItemTags.DURABILITY_ENCHANTABLE)
                    .add(ModItems.DUAL_KNIFE)
                    .add(ModItems.NIGHTMARE_HELMET)
                    .add(ModItems.NIGHTMARE_CHESTPLATE)
                    .add(ModItems.NIGHTMARE_LEGGINGS)
                    .add(ModItems.NIGHTMARE_BOOTS)
                    .add(ModItems.CINDER_DRAGON_HELMET)
                    .add(ModItems.CINDER_DRAGON_CHESTPLATE)
                    .add(ModItems.CINDER_DRAGON_LEGGINGS)
                    .add(ModItems.CINDER_DRAGON_BOOTS)
                    .add(ModItems.SPIRIT_HYDRA_HELMET)
                    .add(ModItems.SPIRIT_HYDRA_CHESTPLATE)
                    .add(ModItems.SPIRIT_HYDRA_LEGGINGS)
                    .add(ModItems.SPIRIT_HYDRA_BOOTS);

            // Tương thích đầy đủ hệ thống Enchant Giáp Vanilla MC 1.21.1 (Protection, Unbreaking, Mending, Respiration, Feather Falling...)
            getOrCreateTagBuilder(ItemTags.ARMOR_ENCHANTABLE)
                    .add(ModItems.NIGHTMARE_HELMET)
                    .add(ModItems.NIGHTMARE_CHESTPLATE)
                    .add(ModItems.NIGHTMARE_LEGGINGS)
                    .add(ModItems.NIGHTMARE_BOOTS)
                    .add(ModItems.CINDER_DRAGON_HELMET)
                    .add(ModItems.CINDER_DRAGON_CHESTPLATE)
                    .add(ModItems.CINDER_DRAGON_LEGGINGS)
                    .add(ModItems.CINDER_DRAGON_BOOTS)
                    .add(ModItems.SPIRIT_HYDRA_HELMET)
                    .add(ModItems.SPIRIT_HYDRA_CHESTPLATE)
                    .add(ModItems.SPIRIT_HYDRA_LEGGINGS)
                    .add(ModItems.SPIRIT_HYDRA_BOOTS);
            getOrCreateTagBuilder(ItemTags.EQUIPPABLE_ENCHANTABLE)
                    .add(ModItems.NIGHTMARE_HELMET)
                    .add(ModItems.NIGHTMARE_CHESTPLATE)
                    .add(ModItems.NIGHTMARE_LEGGINGS)
                    .add(ModItems.NIGHTMARE_BOOTS)
                    .add(ModItems.CINDER_DRAGON_HELMET)
                    .add(ModItems.CINDER_DRAGON_CHESTPLATE)
                    .add(ModItems.CINDER_DRAGON_LEGGINGS)
                    .add(ModItems.CINDER_DRAGON_BOOTS)
                    .add(ModItems.SPIRIT_HYDRA_HELMET)
                    .add(ModItems.SPIRIT_HYDRA_CHESTPLATE)
                    .add(ModItems.SPIRIT_HYDRA_LEGGINGS)
                    .add(ModItems.SPIRIT_HYDRA_BOOTS);
            getOrCreateTagBuilder(ItemTags.HEAD_ARMOR_ENCHANTABLE)
                    .add(ModItems.NIGHTMARE_HELMET)
                    .add(ModItems.CINDER_DRAGON_HELMET)
                    .add(ModItems.SPIRIT_HYDRA_HELMET);
            getOrCreateTagBuilder(ItemTags.CHEST_ARMOR_ENCHANTABLE)
                    .add(ModItems.NIGHTMARE_CHESTPLATE)
                    .add(ModItems.CINDER_DRAGON_CHESTPLATE)
                    .add(ModItems.SPIRIT_HYDRA_CHESTPLATE);
            getOrCreateTagBuilder(ItemTags.LEG_ARMOR_ENCHANTABLE)
                    .add(ModItems.NIGHTMARE_LEGGINGS)
                    .add(ModItems.CINDER_DRAGON_LEGGINGS)
                    .add(ModItems.SPIRIT_HYDRA_LEGGINGS);
            getOrCreateTagBuilder(ItemTags.FOOT_ARMOR_ENCHANTABLE)
                    .add(ModItems.NIGHTMARE_BOOTS)
                    .add(ModItems.CINDER_DRAGON_BOOTS)
                    .add(ModItems.SPIRIT_HYDRA_BOOTS);
            getOrCreateTagBuilder(ItemTags.HEAD_ARMOR)
                    .add(ModItems.NIGHTMARE_HELMET)
                    .add(ModItems.CINDER_DRAGON_HELMET)
                    .add(ModItems.SPIRIT_HYDRA_HELMET);
            getOrCreateTagBuilder(ItemTags.CHEST_ARMOR)
                    .add(ModItems.NIGHTMARE_CHESTPLATE)
                    .add(ModItems.CINDER_DRAGON_CHESTPLATE)
                    .add(ModItems.SPIRIT_HYDRA_CHESTPLATE);
            getOrCreateTagBuilder(ItemTags.LEG_ARMOR)
                    .add(ModItems.NIGHTMARE_LEGGINGS)
                    .add(ModItems.CINDER_DRAGON_LEGGINGS)
                    .add(ModItems.SPIRIT_HYDRA_LEGGINGS);
            getOrCreateTagBuilder(ItemTags.FOOT_ARMOR)
                    .add(ModItems.NIGHTMARE_BOOTS)
                    .add(ModItems.CINDER_DRAGON_BOOTS)
                    .add(ModItems.SPIRIT_HYDRA_BOOTS);
        }
    }

    private static final class ModEnchantmentTagProvider extends FabricTagProvider<Enchantment> {
        ModEnchantmentTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, Registries.ENCHANTMENT, registriesFuture);
        }

        @Override
        protected void addTags(HolderLookup.Provider wrapperLookup) {
            getOrCreateTagBuilder(ModEnchantments.DUAL_KNIFE_BOSS_BOOKS)
                    .add(ModEnchantments.LIFESTEAL)
                    .add(ModEnchantments.ARMOR_PIERCE)
                    .add(ModEnchantments.CRITICAL_STRIKE)
                    .add(ModEnchantments.DEEP_WOUNDS);

            getOrCreateTagBuilder(EnchantmentTags.IN_ENCHANTING_TABLE)
                    .add(ModEnchantments.LIFESTEAL)
                    .add(ModEnchantments.ARMOR_PIERCE)
                    .add(ModEnchantments.CRITICAL_STRIKE)
                    .add(ModEnchantments.DEEP_WOUNDS);

            getOrCreateTagBuilder(EnchantmentTags.NON_TREASURE)
                    .add(ModEnchantments.LIFESTEAL)
                    .add(ModEnchantments.ARMOR_PIERCE)
                    .add(ModEnchantments.CRITICAL_STRIKE)
                    .add(ModEnchantments.DEEP_WOUNDS);
        }
    }

    private static final class ModDamageTypeTagProvider extends FabricTagProvider<DamageType> {
        ModDamageTypeTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, Registries.DAMAGE_TYPE, registriesFuture);
        }

        @Override
        protected void addTags(HolderLookup.Provider wrapperLookup) {
            getOrCreateTagBuilder(DamageTypeTags.BYPASSES_ARMOR)
                    .add(ModDamageTypes.BLEEDING);
            getOrCreateTagBuilder(DamageTypeTags.NO_KNOCKBACK)
                    .add(ModDamageTypes.BLEEDING);
            getOrCreateTagBuilder(DamageTypeTags.BYPASSES_COOLDOWN)
                    .add(ModDamageTypes.BLEEDING);
        }
    }

    private static final class ModRecipeProvider extends FabricRecipeProvider {
        ModRecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        public void buildRecipes(RecipeOutput exporter) {
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.DUAL_KNIFE)
                    .pattern("E E")
                    .pattern("SCS")
                    .pattern(" N ")
                    .define('E', Items.EMERALD_BLOCK)
                    .define('S', Items.DIAMOND_SWORD)
                    .define('C', ModItems.ABYSSAL_CORE)
                    .define('N', Items.NETHERITE_INGOT)
                    .unlockedBy(getHasName(ModItems.ABYSSAL_CORE), has(ModItems.ABYSSAL_CORE))
                    .unlockedBy(getHasName(Items.DIAMOND_SWORD), has(Items.DIAMOND_SWORD))
                    .save(exporter);

            // --- ĐỦ 12 CÔNG THỨC CHẾ TẠO GIÁP BOSS BẮT BUỘC KẸP GIÁP KIM CƯƠNG (`minecraft:diamond_*`) ---
            // Pattern chuẩn 1.21.1: Mũ 2 dòng ("SSS"/"SDS"), Áo 3 dòng ("SDS"/"SHS"/"SSS"), Quần 3 dòng ("SSS"/"SDS"/"S S"), Giày 2 dòng ("S S"/"SDS")

            // 1. Bộ Giáp Nightmare Knight (S = nightmare_armor_shard, H = nightmare_core, D = diamond_*)
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.NIGHTMARE_HELMET)
                    .pattern("SSS")
                    .pattern("SDS")
                    .define('S', ModItems.NIGHTMARE_ARMOR_SHARD)
                    .define('D', Items.DIAMOND_HELMET)
                    .unlockedBy(getHasName(ModItems.NIGHTMARE_ARMOR_SHARD), has(ModItems.NIGHTMARE_ARMOR_SHARD))
                    .unlockedBy(getHasName(Items.DIAMOND_HELMET), has(Items.DIAMOND_HELMET))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.NIGHTMARE_CHESTPLATE)
                    .pattern("SDS")
                    .pattern("SHS")
                    .pattern("SSS")
                    .define('S', ModItems.NIGHTMARE_ARMOR_SHARD)
                    .define('H', ModItems.NIGHTMARE_CORE)
                    .define('D', Items.DIAMOND_CHESTPLATE)
                    .unlockedBy(getHasName(ModItems.NIGHTMARE_CORE), has(ModItems.NIGHTMARE_CORE))
                    .unlockedBy(getHasName(ModItems.NIGHTMARE_ARMOR_SHARD), has(ModItems.NIGHTMARE_ARMOR_SHARD))
                    .unlockedBy(getHasName(Items.DIAMOND_CHESTPLATE), has(Items.DIAMOND_CHESTPLATE))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.NIGHTMARE_LEGGINGS)
                    .pattern("SSS")
                    .pattern("SDS")
                    .pattern("S S")
                    .define('S', ModItems.NIGHTMARE_ARMOR_SHARD)
                    .define('D', Items.DIAMOND_LEGGINGS)
                    .unlockedBy(getHasName(ModItems.NIGHTMARE_ARMOR_SHARD), has(ModItems.NIGHTMARE_ARMOR_SHARD))
                    .unlockedBy(getHasName(Items.DIAMOND_LEGGINGS), has(Items.DIAMOND_LEGGINGS))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.NIGHTMARE_BOOTS)
                    .pattern("S S")
                    .pattern("SDS")
                    .define('S', ModItems.NIGHTMARE_ARMOR_SHARD)
                    .define('D', Items.DIAMOND_BOOTS)
                    .unlockedBy(getHasName(ModItems.NIGHTMARE_ARMOR_SHARD), has(ModItems.NIGHTMARE_ARMOR_SHARD))
                    .unlockedBy(getHasName(Items.DIAMOND_BOOTS), has(Items.DIAMOND_BOOTS))
                    .save(exporter);

            // 2. Bộ Giáp Cinder Dragon (S = cinder_dragon_scale, H = cinder_dragon_heart, D = diamond_*)
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CINDER_DRAGON_HELMET)
                    .pattern("SSS")
                    .pattern("SDS")
                    .define('S', ModItems.CINDER_DRAGON_SCALE)
                    .define('D', Items.DIAMOND_HELMET)
                    .unlockedBy(getHasName(ModItems.CINDER_DRAGON_SCALE), has(ModItems.CINDER_DRAGON_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_HELMET), has(Items.DIAMOND_HELMET))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CINDER_DRAGON_CHESTPLATE)
                    .pattern("SDS")
                    .pattern("SHS")
                    .pattern("SSS")
                    .define('S', ModItems.CINDER_DRAGON_SCALE)
                    .define('H', ModItems.CINDER_DRAGON_HEART)
                    .define('D', Items.DIAMOND_CHESTPLATE)
                    .unlockedBy(getHasName(ModItems.CINDER_DRAGON_HEART), has(ModItems.CINDER_DRAGON_HEART))
                    .unlockedBy(getHasName(ModItems.CINDER_DRAGON_SCALE), has(ModItems.CINDER_DRAGON_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_CHESTPLATE), has(Items.DIAMOND_CHESTPLATE))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CINDER_DRAGON_LEGGINGS)
                    .pattern("SSS")
                    .pattern("SDS")
                    .pattern("S S")
                    .define('S', ModItems.CINDER_DRAGON_SCALE)
                    .define('D', Items.DIAMOND_LEGGINGS)
                    .unlockedBy(getHasName(ModItems.CINDER_DRAGON_SCALE), has(ModItems.CINDER_DRAGON_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_LEGGINGS), has(Items.DIAMOND_LEGGINGS))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.CINDER_DRAGON_BOOTS)
                    .pattern("S S")
                    .pattern("SDS")
                    .define('S', ModItems.CINDER_DRAGON_SCALE)
                    .define('D', Items.DIAMOND_BOOTS)
                    .unlockedBy(getHasName(ModItems.CINDER_DRAGON_SCALE), has(ModItems.CINDER_DRAGON_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_BOOTS), has(Items.DIAMOND_BOOTS))
                    .save(exporter);

            // 3. Bộ Giáp Spirit Hydra (S = spirit_hydra_scale, H = spirit_hydra_heart, D = diamond_*)
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.SPIRIT_HYDRA_HELMET)
                    .pattern("SSS")
                    .pattern("SDS")
                    .define('S', ModItems.SPIRIT_HYDRA_SCALE)
                    .define('D', Items.DIAMOND_HELMET)
                    .unlockedBy(getHasName(ModItems.SPIRIT_HYDRA_SCALE), has(ModItems.SPIRIT_HYDRA_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_HELMET), has(Items.DIAMOND_HELMET))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.SPIRIT_HYDRA_CHESTPLATE)
                    .pattern("SDS")
                    .pattern("SHS")
                    .pattern("SSS")
                    .define('S', ModItems.SPIRIT_HYDRA_SCALE)
                    .define('H', ModItems.SPIRIT_HYDRA_HEART)
                    .define('D', Items.DIAMOND_CHESTPLATE)
                    .unlockedBy(getHasName(ModItems.SPIRIT_HYDRA_HEART), has(ModItems.SPIRIT_HYDRA_HEART))
                    .unlockedBy(getHasName(ModItems.SPIRIT_HYDRA_SCALE), has(ModItems.SPIRIT_HYDRA_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_CHESTPLATE), has(Items.DIAMOND_CHESTPLATE))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.SPIRIT_HYDRA_LEGGINGS)
                    .pattern("SSS")
                    .pattern("SDS")
                    .pattern("S S")
                    .define('S', ModItems.SPIRIT_HYDRA_SCALE)
                    .define('D', Items.DIAMOND_LEGGINGS)
                    .unlockedBy(getHasName(ModItems.SPIRIT_HYDRA_SCALE), has(ModItems.SPIRIT_HYDRA_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_LEGGINGS), has(Items.DIAMOND_LEGGINGS))
                    .save(exporter);

            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.SPIRIT_HYDRA_BOOTS)
                    .pattern("S S")
                    .pattern("SDS")
                    .define('S', ModItems.SPIRIT_HYDRA_SCALE)
                    .define('D', Items.DIAMOND_BOOTS)
                    .unlockedBy(getHasName(ModItems.SPIRIT_HYDRA_SCALE), has(ModItems.SPIRIT_HYDRA_SCALE))
                    .unlockedBy(getHasName(Items.DIAMOND_BOOTS), has(Items.DIAMOND_BOOTS))
                    .save(exporter);
        }
    }
}
