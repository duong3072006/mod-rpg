package com.abyssalrpg.structure;

import com.abyssalrpg.block.MythicSummonAltarBlock;
import com.abyssalrpg.registry.ModBiomes;
import com.abyssalrpg.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Optional;

/**
 * Biome-aware Mythological Boss Summoning Temple ("Thần Điện Boss Thần Thoại").
 * Selects the matching Mythological God (0..6) based on the biome it generates in,
 * falling back to an even deterministic distribution across all 7 Gods in generic biomes.
 */
public class MythicTempleStructure extends Structure {
    public static final MapCodec<MythicTempleStructure> CODEC = simpleCodec(MythicTempleStructure::new);

    public MythicTempleStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        if (y < context.chunkGenerator().getSeaLevel() - 1) {
            return Optional.empty();
        }
        Holder<Biome> biome = context.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                context.randomState().sampler()
        );
        int bossType = chooseGodForBiome(biome, context.chunkPos());
        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new Piece(origin, bossType))));
    }

    private static int chooseGodForBiome(Holder<Biome> biome, ChunkPos chunkPos) {
        if (biome.is(ModBiomes.JADE_MYTHIC_JUNGLE) || biome.is(Biomes.JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE)) {
            return MythicSummonAltarBlock.GOD_QUETZALCOATL;
        }
        if (biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.SAVANNA)) {
            return MythicSummonAltarBlock.GOD_APEP;
        }
        if (biome.is(ModBiomes.FROZEN_RAGNAROK_TUNDRA) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES)) {
            return ((chunkPos.x + chunkPos.z) & 1) == 0
                    ? MythicSummonAltarBlock.GOD_FENRIR
                    : MythicSummonAltarBlock.GOD_JORMUNGANDR;
        }
        if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.DARK_FOREST)) {
            return MythicSummonAltarBlock.GOD_HYDRA;
        }
        if (biome.is(ModBiomes.ASHEN_VOLCANIC_PEAKS) || biome.is(ModBiomes.ABYSSAL_WASTELAND)) {
            return MythicSummonAltarBlock.GOD_TIAMAT;
        }
        if (biome.is(Biomes.BEACH) || biome.is(Biomes.STONY_SHORE)) {
            return MythicSummonAltarBlock.GOD_LEVIATHAN;
        }
        // Generic biomes distribute evenly across all 7 Mythological Gods
        return Math.floorMod(chunkPos.x * 31 + chunkPos.z * 17, MythicSummonAltarBlock.GOD_COUNT);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.MYTHIC_TEMPLE_TYPE;
    }

    public static class Piece extends StructurePiece {
        private final BlockPos origin;
        private final int bossType;

        public Piece(BlockPos origin, int bossType) {
            super(ModStructures.MYTHIC_TEMPLE_PIECE, 0, new BoundingBox(
                    origin.getX() - 40, origin.getY() - 14, origin.getZ() - 40,
                    origin.getX() + 40, origin.getY() + 54, origin.getZ() + 40
            ));
            this.origin = origin;
            this.bossType = bossType;
        }

        public Piece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(ModStructures.MYTHIC_TEMPLE_PIECE, tag);
            this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
            this.bossType = tag.getInt("BossType");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("OX", origin.getX());
            tag.putInt("OY", origin.getY());
            tag.putInt("OZ", origin.getZ());
            tag.putInt("BossType", bossType);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
            OverworldStructureBuilder.buildMythicTemple(level, origin, box, random, bossType);
        }
    }
}

