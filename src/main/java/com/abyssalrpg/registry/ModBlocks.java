package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.block.MythicSummonAltarBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final Block RUNIC_DEEPSLATE = register("runic_deepslate",
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 20.0f)
                    .sound(SoundType.DEEPSLATE_BRICKS)
                    .lightLevel(state -> 8)
                    .requiresCorrectToolForDrops()));

    public static final Block ABYSSAL_BRICK = register("abyssal_brick",
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 30.0f)
                    .sound(SoundType.NETHER_BRICKS)
                    .lightLevel(state -> 4)
                    .requiresCorrectToolForDrops()));

    public static final Block MYTHIC_SUMMON_ALTAR = register("mythic_summon_altar",
            new MythicSummonAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(-1.0f, 3600000.0f)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> 15)));

    private static <T extends Block> T register(String name, T block) {
        Registry.register(BuiltInRegistries.BLOCK, AbyssalRpgMod.id(name), block);
        Registry.register(BuiltInRegistries.ITEM, AbyssalRpgMod.id(name), new BlockItem(block, new Item.Properties()));
        return block;
    }

    public static void init() {
        // Trigger static initialization
    }

    private ModBlocks() {}
}
