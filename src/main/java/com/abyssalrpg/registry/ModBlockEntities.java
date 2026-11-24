package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.block.entity.AltarEyeBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<AltarEyeBlockEntity> ALTAR_EYE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            AbyssalRpgMod.id("altar_eye"),
            BlockEntityType.Builder.of(
                    AltarEyeBlockEntity::new,
                    ModBlocks.MYTHIC_SUMMON_ALTAR
            ).build(null)
    );

    public static void init() {
        // Trigger static initialization
    }

    private ModBlockEntities() {}
}

