package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.structure.MythicTempleStructure;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructures {
    public static final StructurePieceType MYTHIC_TEMPLE_PIECE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            AbyssalRpgMod.id("mythic_temple_piece"),
            MythicTempleStructure.Piece::new
    );

    public static final StructureType<MythicTempleStructure> MYTHIC_TEMPLE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            AbyssalRpgMod.id("mythic_temple"),
            () -> MythicTempleStructure.CODEC
    );

    public static void init() {
        // Trigger static initialization
    }

    private ModStructures() {}
}
