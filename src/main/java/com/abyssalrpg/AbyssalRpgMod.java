package com.abyssalrpg;

import com.abyssalrpg.combat.CinderDragonArmorHandler;
import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.combat.NightmareArmorHandler;
import com.abyssalrpg.combat.TogetsuCombatHandler;
import com.abyssalrpg.command.AbyssCommands;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.config.CinderDragonConfig;
import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.config.LeviathanConfig;
import com.abyssalrpg.config.NightmareKnightConfig;
import com.abyssalrpg.config.SpiritHydraConfig;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.entity.mythic.cerberus.CerberusGroupManager;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModBlockEntities;
import com.abyssalrpg.registry.ModBlocks;
import com.abyssalrpg.registry.ModCreativeTab;
import com.abyssalrpg.registry.ModEffects;
import com.abyssalrpg.registry.ModEntities;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
import com.abyssalrpg.registry.ModStructures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbyssalRpgMod implements ModInitializer {
    public static final String MOD_ID = "abyssalrpg";
    public static final Logger LOGGER = LoggerFactory.getLogger("AbyssalSpireRPG");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LeviathanConfig.load();
        SpiritHydraConfig.load();
        CerberusConfig.load();
        NightmareKnightConfig.load();
        JormungandrConfig.load();
        CinderDragonConfig.load();
        WeaponsConfig.load();
        ArmorConfig.load();
        ModSounds.init();
        ModEffects.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModEntities.init();
        ModItems.init();
        ModStructures.init();
        ModCreativeTab.init();

        ModNetworking.registerPayloads();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AbyssCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(CerberusGroupManager::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(DualBladeCombatHandler::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(TogetsuCombatHandler::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(NightmareArmorHandler::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(CinderDragonArmorHandler::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(AbyssCommands::tickActiveAudits);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            NightmareArmorHandler.syncPlayerImmediate(handler.getPlayer());
            CinderDragonArmorHandler.syncPlayerImmediate(handler.getPlayer());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            NightmareArmorHandler.syncPlayerImmediate(newPlayer);
            CinderDragonArmorHandler.syncPlayerImmediate(newPlayer);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            NightmareArmorHandler.syncPlayerImmediate(player);
            CinderDragonArmorHandler.syncPlayerImmediate(player);
        });

        LOGGER.info("Abyssal Spire RPG initialized — 13 Colossal Multi-Phase Bosses & 7 Mythic Boss Temples ready!");
    }
}
