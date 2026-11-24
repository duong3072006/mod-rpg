package com.abyssalrpg.block.entity;

import com.abyssalrpg.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lightweight BlockEntity attached to MythicSummonAltarBlock so the client renders a live 3D
 * floating, 180-degree scanning Eye surrounded by counter-rotating arcane halo rings and orbiting crystal shards.
 */
public class AltarEyeBlockEntity extends BlockEntity {
    public AltarEyeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR_EYE, pos, state);
    }
}

