package com.abyssalrpg.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor an toàn cho `LivingEntity.attackStrengthTicker` phục vụ bài test kiểm định server-side `/abyss dualblade test`.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("attackStrengthTicker")
    void abyssalrpg$setAttackStrengthTicker(int ticks);
}

