package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.effect.BleedingMobEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

/**
 * Đăng ký các MobEffect của Abyssal Spire RPG.
 */
public final class ModEffects {
    public static final Holder<MobEffect> BLEEDING = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            AbyssalRpgMod.id("bleeding"),
            new BleedingMobEffect()
    );

    public static void init() {
        // Kích hoạt đăng ký static
    }

    private ModEffects() {}
}

