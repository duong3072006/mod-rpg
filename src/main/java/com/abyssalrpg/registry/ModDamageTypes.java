package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Khóa DamageType data-driven của mod (`data/abyssalrpg/damage_type/bleeding.json`).
 */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BLEEDING =
            ResourceKey.create(Registries.DAMAGE_TYPE, AbyssalRpgMod.id("bleeding"));

    private ModDamageTypes() {}

    public static DamageSource bleeding(Level level, @Nullable Entity attacker) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BLEEDING);
        if (attacker != null) {
            return new DamageSource(holder, attacker, attacker);
        }
        return new DamageSource(holder);
    }
}

