package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Đăng ký các SoundEvent chuyên biệt cho Boss Leviathan (định nghĩa trong assets/abyssalrpg/sounds.json).
 */
public final class ModSounds {
    public static final SoundEvent LEVIATHAN_AMBIENT = register("entity.leviathan.ambient");
    public static final SoundEvent LEVIATHAN_ROAR = register("entity.leviathan.roar");
    public static final SoundEvent LEVIATHAN_ATTACK = register("entity.leviathan.attack");
    public static final SoundEvent LEVIATHAN_BEAM = register("entity.leviathan.beam");
    public static final SoundEvent LEVIATHAN_SLAM = register("entity.leviathan.slam");
    public static final SoundEvent LEVIATHAN_PHASE_CHANGE = register("entity.leviathan.phase_change");
    public static final SoundEvent LEVIATHAN_DEATH = register("entity.leviathan.death");

    // Spirit Hydra SoundEvents (khai báo trong assets/abyssalrpg/sounds.json)
    public static final SoundEvent HYDRA_AMBIENT = register("entity.spirit_hydra.ambient");
    public static final SoundEvent HYDRA_ROAR = register("entity.spirit_hydra.roar");
    public static final SoundEvent HYDRA_BITE = register("entity.spirit_hydra.bite");
    public static final SoundEvent HYDRA_BREATH = register("entity.spirit_hydra.breath");
    public static final SoundEvent HYDRA_SPIT = register("entity.spirit_hydra.spit");
    public static final SoundEvent HYDRA_HEAD_CUT = register("entity.spirit_hydra.head_cut");
    public static final SoundEvent HYDRA_HEAD_REGROW = register("entity.spirit_hydra.head_regrow");
    public static final SoundEvent HYDRA_PHASE_CHANGE = register("entity.spirit_hydra.phase_change");
    public static final SoundEvent HYDRA_DEATH = register("entity.spirit_hydra.death");

    // Cerberus SoundEvents (khai báo trong assets/abyssalrpg/sounds.json)
    public static final SoundEvent CERBERUS_AMBIENT = register("entity.cerberus.ambient");
    public static final SoundEvent CERBERUS_ROAR = register("entity.cerberus.roar");
    public static final SoundEvent CERBERUS_BITE = register("entity.cerberus.bite");
    public static final SoundEvent CERBERUS_BREATH = register("entity.cerberus.breath");
    public static final SoundEvent CERBERUS_SLAM = register("entity.cerberus.slam");
    public static final SoundEvent CERBERUS_HOWL = register("entity.cerberus.howl");
    public static final SoundEvent CERBERUS_PHASE_CHANGE = register("entity.cerberus.phase_change");
    public static final SoundEvent CERBERUS_SPLIT_BURST = register("entity.cerberus.split_burst");
    public static final SoundEvent CERBERUS_SHADOW_STEP = register("entity.cerberus.shadow_step");
    public static final SoundEvent CERBERUS_DEATH = register("entity.cerberus.death");

    // Nightmare Knight SoundEvents (khai báo trong assets/abyssalrpg/sounds.json)
    public static final SoundEvent NIGHTMARE_AMBIENT = register("entity.nightmare_knight.ambient");
    public static final SoundEvent NIGHTMARE_ROAR = register("entity.nightmare_knight.roar");
    public static final SoundEvent NIGHTMARE_SLASH = register("entity.nightmare_knight.slash");
    public static final SoundEvent NIGHTMARE_SLAM = register("entity.nightmare_knight.slam");
    public static final SoundEvent NIGHTMARE_THRUST = register("entity.nightmare_knight.thrust");
    public static final SoundEvent NIGHTMARE_CLAW = register("entity.nightmare_knight.claw");
    public static final SoundEvent NIGHTMARE_LEAP = register("entity.nightmare_knight.leap");
    public static final SoundEvent NIGHTMARE_SPIKE = register("entity.nightmare_knight.spike");
    public static final SoundEvent NIGHTMARE_SWORD_WAVE = register("entity.nightmare_knight.sword_wave");
    public static final SoundEvent NIGHTMARE_REALM = register("entity.nightmare_knight.realm");
    public static final SoundEvent NIGHTMARE_ULTIMATE = register("entity.nightmare_knight.ultimate");
    public static final SoundEvent NIGHTMARE_PHASE_CHANGE = register("entity.nightmare_knight.phase_change");
    public static final SoundEvent NIGHTMARE_DEATH = register("entity.nightmare_knight.death");

    // Jörmungandr SoundEvents (khai báo trong assets/abyssalrpg/sounds.json)
    public static final SoundEvent JORMUNGANDR_AMBIENT = register("entity.jormungandr.ambient");
    public static final SoundEvent JORMUNGANDR_ROAR = register("entity.jormungandr.roar");
    public static final SoundEvent JORMUNGANDR_STRIKE = register("entity.jormungandr.strike");
    public static final SoundEvent JORMUNGANDR_SPIT = register("entity.jormungandr.spit");
    public static final SoundEvent JORMUNGANDR_SLAM = register("entity.jormungandr.slam");
    public static final SoundEvent JORMUNGANDR_FLARE = register("entity.jormungandr.flare");
    public static final SoundEvent JORMUNGANDR_BURROW = register("entity.jormungandr.burrow");
    public static final SoundEvent JORMUNGANDR_PHASE_CHANGE = register("entity.jormungandr.phase_change");
    public static final SoundEvent JORMUNGANDR_DEATH = register("entity.jormungandr.death");

    // Cinder Dragon SoundEvents (khai báo trong assets/abyssalrpg/sounds.json)
    public static final SoundEvent CINDER_AMBIENT = register("entity.cinder_dragon.ambient");
    public static final SoundEvent CINDER_ROAR = register("entity.cinder_dragon.roar");
    public static final SoundEvent CINDER_HURT = register("entity.cinder_dragon.hurt");
    public static final SoundEvent CINDER_BITE = register("entity.cinder_dragon.bite");
    public static final SoundEvent CINDER_BREATH = register("entity.cinder_dragon.breath");
    public static final SoundEvent CINDER_FIREBALL = register("entity.cinder_dragon.fireball");
    public static final SoundEvent CINDER_WING_FLAP = register("entity.cinder_dragon.wing_flap");
    public static final SoundEvent CINDER_WING_SWEEP = register("entity.cinder_dragon.wing_sweep");
    public static final SoundEvent CINDER_SLAM = register("entity.cinder_dragon.slam");
    public static final SoundEvent CINDER_BEAM_CHARGE = register("entity.cinder_dragon.beam_charge");
    public static final SoundEvent CINDER_BEAM_FIRE = register("entity.cinder_dragon.beam_fire");
    public static final SoundEvent CINDER_ULTIMATE = register("entity.cinder_dragon.ultimate");
    public static final SoundEvent CINDER_PHASE_CHANGE = register("entity.cinder_dragon.phase_change");
    public static final SoundEvent CINDER_DEATH = register("entity.cinder_dragon.death");

    // Dual Knife Weapon, Critical Strike & Flash Dash SoundEvents
    public static final SoundEvent DUAL_BLADE_SLASH = register("item.dual_knife.slash");
    public static final SoundEvent DUAL_BLADE_CROSS_SLASH = register("item.dual_knife.cross_slash");
    public static final SoundEvent DUAL_BLADE_CRIT = register("item.dual_knife.crit");
    public static final SoundEvent DUAL_BLADE_DASH_CHARGE = register("item.dual_knife.dash_charge");
    public static final SoundEvent DUAL_BLADE_DASH_WHOOSH = register("item.dual_knife.dash_whoosh");
    public static final SoundEvent DUAL_BLADE_DASH_FLURRY = register("item.dual_knife.dash_flurry");

    // Excalibur SoundEvents (defined in assets/abyssalrpg/sounds.json)
    public static final SoundEvent EXCALIBUR_SLASH = register("item.excalibur.slash");
    public static final SoundEvent EXCALIBUR_CHARGE = register("item.excalibur.charge");
    public static final SoundEvent EXCALIBUR_BEAM = register("item.excalibur.beam");
    public static final SoundEvent MORGAN_TRANSFORM = register("item.excalibur.morgan_transform");

    // Sci-Fi Katana SoundEvents (defined in assets/abyssalrpg/sounds.json)
    public static final SoundEvent KATANA_SLASH = register("item.katana.slash");
    public static final SoundEvent KATANA_DRAW = register("item.katana.draw");
    public static final SoundEvent KATANA_SHEATH = register("item.katana.sheath");
    public static final SoundEvent KATANA_PARRY = register("item.katana.parry");

    private static SoundEvent register(String path) {
        ResourceLocation id = AbyssalRpgMod.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        // Kích hoạt đăng ký static
    }

    private ModSounds() {}
}

