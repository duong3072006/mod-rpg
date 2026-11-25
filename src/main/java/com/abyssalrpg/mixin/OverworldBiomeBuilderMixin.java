package com.abyssalrpg.mixin;

import com.abyssalrpg.registry.ModBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects our 4 custom Abyssal RPG Biomes cleanly into rare Overworld climate variant slots
 * with full native vanilla terrain, caves, rivers, ores, and climate transitions!
 */
@Mixin(OverworldBiomeBuilder.class)
public final class OverworldBiomeBuilderMixin {
    @Shadow @Final private ResourceKey<Biome>[][] MIDDLE_BIOMES_VARIANT;
    @Shadow @Final private ResourceKey<Biome>[][] PLATEAU_BIOMES_VARIANT;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void abyssalrpg$injectCustomBiomes(CallbackInfo ci) {
        if (System.getProperty("fabric-api.datagen") != null) {
            return;
        }
        // Cold snowy climate -> Frozen Ragnarok Tundra (Fenrir / Jormungandr / Leviathan realm)
        this.MIDDLE_BIOMES_VARIANT[0][1] = ModBiomes.FROZEN_RAGNAROK_TUNDRA;
        this.PLATEAU_BIOMES_VARIANT[0][2] = ModBiomes.FROZEN_RAGNAROK_TUNDRA;

        // Cool dark climate -> Abyssal Wasteland (Void Dragon / Shadow Fenrir / Catacombs realm)
        this.MIDDLE_BIOMES_VARIANT[1][3] = ModBiomes.ABYSSAL_WASTELAND;
        this.PLATEAU_BIOMES_VARIANT[1][3] = ModBiomes.ABYSSAL_WASTELAND;

        // Warm lush humid climate -> Jade Mythic Jungle (Spirit Hydra realm)
        this.MIDDLE_BIOMES_VARIANT[3][4] = ModBiomes.JADE_MYTHIC_JUNGLE;
        this.PLATEAU_BIOMES_VARIANT[3][4] = ModBiomes.JADE_MYTHIC_JUNGLE;

        // Hot arid climate -> Ashen Volcanic Peaks (Cinder Dragon / Cerberus / Obsidian Bastion realm)
        this.MIDDLE_BIOMES_VARIANT[4][0] = ModBiomes.ASHEN_VOLCANIC_PEAKS;
        this.PLATEAU_BIOMES_VARIANT[4][1] = ModBiomes.ASHEN_VOLCANIC_PEAKS;
    }
}

