package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Custom Overworld RPG Biomes injected natively into OverworldBiomeBuilder!
 */
public final class ModBiomes {
    public static final ResourceKey<Biome> ABYSSAL_WASTELAND = key("abyssal_wasteland");
    public static final ResourceKey<Biome> ASHEN_VOLCANIC_PEAKS = key("ashen_volcanic_peaks");
    public static final ResourceKey<Biome> JADE_MYTHIC_JUNGLE = key("jade_mythic_jungle");
    public static final ResourceKey<Biome> FROZEN_RAGNAROK_TUNDRA = key("frozen_ragnarok_tundra");

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, AbyssalRpgMod.id(name));
    }

    private ModBiomes() {}
}

