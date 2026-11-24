package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    public static final ResourceKey<Level> DUNGEON_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            AbyssalRpgMod.id("dungeon")
    );

    private ModDimensions() {}
}

