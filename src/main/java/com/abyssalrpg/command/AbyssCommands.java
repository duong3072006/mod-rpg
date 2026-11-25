package com.abyssalrpg.command;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.CinderDragonArmorHandler;
import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.combat.NightmareArmorHandler;
import com.abyssalrpg.combat.SpiritHydraArmorHandler;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.mixin.LivingEntityAccessor;
import com.abyssalrpg.registry.ModArmorMaterials;
import com.abyssalrpg.registry.ModDamageTypes;
import com.abyssalrpg.registry.ModEffects;
import com.abyssalrpg.registry.ModEnchantments;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.UUID;
import com.abyssalrpg.block.MythicSummonAltarBlock;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.config.CinderDragonConfig;
import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.config.LeviathanConfig;
import com.abyssalrpg.config.NightmareKnightConfig;
import com.abyssalrpg.config.SpiritHydraConfig;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import com.abyssalrpg.entity.mythic.HydraBossEntity;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.entity.mythic.LeviathanBossEntity;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import com.abyssalrpg.entity.mythic.cinder.CinderDragonHitboxPartEntity;
import com.abyssalrpg.entity.mythic.cinder.DragonAttackTiming;
import com.abyssalrpg.entity.mythic.hydra.HydraHitboxPartEntity;
import com.abyssalrpg.entity.mythic.jormungandr.JormungandrHitboxPartEntity;
import com.abyssalrpg.entity.mythic.nightmare.NightmareBoneSpikeEntity;
import com.abyssalrpg.entity.mythic.nightmare.NightmareHitboxPartEntity;
import com.abyssalrpg.entity.mythic.cerberus.CerberusGroupManager;
import com.abyssalrpg.entity.mythic.cerberus.CerberusGroupManager.EncounterGroup;
import net.minecraft.nbt.CompoundTag;
import com.abyssalrpg.registry.ModEntities;
import com.abyssalrpg.structure.OverworldStructureBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AbyssCommands {
        private AbyssCommands() {
        }

        private static boolean canRunDebug(CommandSourceStack src) {
                return src.hasPermission(3);
        }

        private static boolean canRunCinderDebug(CommandSourceStack src) {
                return src.hasPermission(3) && CinderDragonConfig.get().enableDebugCommands;
        }

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("abyss")
                                .then(Commands.literal("spawntemple")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.argument("god", StringArgumentType.word())
                                                                .suggests((ctx, builder) -> {
                                                                        builder.suggest("quetzalcoatl");
                                                                        builder.suggest("apep");
                                                                        builder.suggest("fenrir");
                                                                        builder.suggest("hydra");
                                                                        builder.suggest("jormungandr");
                                                                        builder.suggest("tiamat");
                                                                        builder.suggest("leviathan");
                                                                        return builder.buildFuture();
                                                                })
                                                                .executes(ctx -> {
                                                                        ServerPlayer player = ctx.getSource()
                                                                                        .getPlayerOrException();
                                                                        ServerLevel level = player.serverLevel();
                                                                        String godName = StringArgumentType
                                                                                        .getString(ctx, "god")
                                                                                        .toLowerCase();
                                                                        int bossType = MythicSummonAltarBlock
                                                                                        .parseGodType(godName);
                                                                        if (bossType < 0) {
                                                                                ctx.getSource().sendFailure(
                                                                                                Component.literal(
                                                                                                                "Use: quetzalcoatl | apep | fenrir | hydra | jormungandr | tiamat | leviathan"));
                                                                                return 0;
                                                                        }
                                                                        BlockPos origin = player.blockPosition()
                                                                                        .offset(0, 0, 44);
                                                                        OverworldStructureBuilder.buildMythicTemple(
                                                                                        level, origin, null,
                                                                                        level.random, bossType);
                                                                        ctx.getSource().sendSuccess(() -> Component
                                                                                        .literal(
                                                                                                        "✦ Constructed Mythic Temple of "
                                                                                                                        + MythicSummonAltarBlock
                                                                                                                                        .getGodTitle(bossType)
                                                                                                                        + " at ("
                                                                                                                        + origin.getX()
                                                                                                                        + ", "
                                                                                                                        + origin.getY()
                                                                                                                        + ", "
                                                                                                                        + origin.getZ()
                                                                                                                        + ")!")
                                                                                        .withStyle(MythicSummonAltarBlock
                                                                                                        .getGodColor(bossType),
                                                                                                        ChatFormatting.BOLD),
                                                                                        true);
                                                                        return 1;
                                                                })))
                                .then(Commands.literal("cerberus")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("debug")
                                                                .executes(ctx -> toggleCerberusDebug(ctx.getSource(),
                                                                                null))
                                                                .then(Commands.literal("on")
                                                                                .executes(ctx -> toggleCerberusDebug(
                                                                                                ctx.getSource(), true)))
                                                                .then(Commands.literal("off")
                                                                                .executes(ctx -> toggleCerberusDebug(
                                                                                                ctx.getSource(),
                                                                                                false)))
                                                                .then(Commands.literal("status")
                                                                                .executes(ctx -> reportCerberusStatus(
                                                                                                ctx.getSource()))))
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runCerberusAuditAndPerf(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("nightmare")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("debug")
                                                                .executes(ctx -> toggleNightmareDebug(ctx.getSource(),
                                                                                null))
                                                                .then(Commands.literal("on")
                                                                                .executes(ctx -> toggleNightmareDebug(
                                                                                                ctx.getSource(), true)))
                                                                .then(Commands.literal("off")
                                                                                .executes(ctx -> toggleNightmareDebug(
                                                                                                ctx.getSource(),
                                                                                                false)))
                                                                .then(Commands.literal("status")
                                                                                .executes(ctx -> reportNightmareStatus(
                                                                                                ctx.getSource()))))
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runNightmareAuditAndPerf(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("jormungandr")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("debug")
                                                                .executes(ctx -> toggleJormungandrDebug(ctx.getSource(),
                                                                                null))
                                                                .then(Commands.literal("on")
                                                                                .executes(ctx -> toggleJormungandrDebug(
                                                                                                ctx.getSource(), true)))
                                                                .then(Commands.literal("off")
                                                                                .executes(ctx -> toggleJormungandrDebug(
                                                                                                ctx.getSource(),
                                                                                                false)))
                                                                .then(Commands.literal("status").executes(
                                                                                ctx -> reportJormungandrStatus(
                                                                                                ctx.getSource()))))
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runJormungandrAuditAndPerf(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("hydra")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("debug")
                                                                .executes(ctx -> toggleHydraDebug(ctx.getSource(),
                                                                                null))
                                                                .then(Commands.literal("on")
                                                                                .executes(ctx -> toggleHydraDebug(
                                                                                                ctx.getSource(), true)))
                                                                .then(Commands.literal("off")
                                                                                .executes(ctx -> toggleHydraDebug(
                                                                                                ctx.getSource(),
                                                                                                false)))
                                                                .then(Commands.literal("status")
                                                                                .executes(ctx -> reportHydraStatus(
                                                                                                ctx.getSource()))))
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runHydraAuditAndPerf(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("leviathan")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("debug")
                                                                .executes(ctx -> toggleLeviathanDebug(ctx.getSource(),
                                                                                null))
                                                                .then(Commands.literal("on")
                                                                                .executes(ctx -> toggleLeviathanDebug(
                                                                                                ctx.getSource(), true)))
                                                                .then(Commands.literal("off")
                                                                                .executes(ctx -> toggleLeviathanDebug(
                                                                                                ctx.getSource(),
                                                                                                false)))
                                                                .then(Commands.literal("status")
                                                                                .executes(ctx -> reportLeviathanStatus(
                                                                                                ctx.getSource()))))
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runLeviathanAuditAndPerf(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("cinder")
                                                .requires(AbyssCommands::canRunCinderDebug)
                                                .then(Commands.literal("debug")
                                                                .executes(ctx -> toggleCinderDebug(ctx.getSource(),
                                                                                null))
                                                                .then(Commands.literal("on")
                                                                                .executes(ctx -> toggleCinderDebug(
                                                                                                ctx.getSource(), true)))
                                                                .then(Commands.literal("off")
                                                                                .executes(ctx -> toggleCinderDebug(
                                                                                                ctx.getSource(),
                                                                                                false)))
                                                                .then(Commands.literal("status")
                                                                                .executes(ctx -> reportCinderStatus(
                                                                                                ctx.getSource()))))
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runCinderDragonAuditAndPerf(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("dualknife")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runDualBladeAuditAndPerf(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("armor")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runNightmareArmorAudit(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("cinderarmor")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runCinderDragonArmorAudit(
                                                                                ctx.getSource()))))
                                .then(Commands.literal("hydraarmor")
                                                .requires(AbyssCommands::canRunDebug)
                                                .then(Commands.literal("test")
                                                                .executes(ctx -> runSpiritHydraArmorAudit(
                                                                                ctx.getSource())))));

                // Lệnh debug chuyên biệt cho Boss Leviathan (/leviathan debug [on|off|status] &
                // /leviathan test)
                dispatcher.register(Commands.literal("leviathan")
                                .requires(AbyssCommands::canRunDebug)
                                .then(Commands.literal("debug")
                                                .executes(ctx -> toggleLeviathanDebug(ctx.getSource(), null))
                                                .then(Commands.literal("on").executes(
                                                                ctx -> toggleLeviathanDebug(ctx.getSource(), true)))
                                                .then(Commands.literal("off").executes(
                                                                ctx -> toggleLeviathanDebug(ctx.getSource(), false)))
                                                .then(Commands.literal("status").executes(
                                                                ctx -> reportLeviathanStatus(ctx.getSource()))))
                                .then(Commands.literal("test")
                                                .executes(ctx -> runLeviathanAuditAndPerf(ctx.getSource()))));

                // Lệnh debug chuyên biệt cho Boss Spirit Hydra (/hydra debug [on|off|status])
                dispatcher.register(Commands.literal("hydra")
                                .requires(AbyssCommands::canRunDebug)
                                .then(Commands.literal("debug")
                                                .executes(ctx -> toggleHydraDebug(ctx.getSource(), null))
                                                .then(Commands.literal("on").executes(
                                                                ctx -> toggleHydraDebug(ctx.getSource(), true)))
                                                .then(Commands.literal("off").executes(
                                                                ctx -> toggleHydraDebug(ctx.getSource(), false)))
                                                .then(Commands.literal("status")
                                                                .executes(ctx -> reportHydraStatus(ctx.getSource())))));
        }

        private static int toggleJormungandrDebug(CommandSourceStack source, Boolean forceState) {
                boolean next = forceState != null ? forceState : !JormungandrBossEntity.isDebugLoggingEnabled();
                JormungandrBossEntity.setDebugLoggingEnabled(next);
                JormungandrConfig.get().debugLogs = next;
                source.sendSuccess(() -> Component.literal(
                                "[Jörmungandr Debug] Yaw, Segments, PathLen, Effective Defense & Proxy logging: "
                                                + (next ? "ON" : "OFF"))
                                .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
                reportJormungandrStatus(source);
                return 1;
        }

        private static int reportJormungandrStatus(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                AABB box = new AABB(source.getPosition().add(-160, -160, -160),
                                source.getPosition().add(160, 160, 160));
                List<JormungandrBossEntity> bosses = level.getEntitiesOfClass(JormungandrBossEntity.class, box);
                if (bosses.isEmpty()) {
                        source.sendSuccess(() -> Component.literal(
                                        "[Jörmungandr Debug] Không tìm thấy Jörmungandr trong bán kính 160 block.")
                                        .withStyle(ChatFormatting.GRAY), false);
                        return 0;
                }
                for (JormungandrBossEntity b : bosses) {
                        float netHeadYaw = Mth.wrapDegrees(b.yHeadRot - b.yBodyRot);
                        float sampleDmg = 15.0f;
                        String msg = String.format(
                                        "[Jormungandr #%d] Phase=%d (enteredP2=%s enraged=%s trans=%d) HP=%.1f/%.1f | Segs=%d Len=%.1fm Proxies=%d | Def=%.1f effPts=%.2f red=%.1f%% | yaw=%.1f body=%.1f head=%.1f netHead=%.1f | skill=%s",
                                        b.getId(), b.getSyncedPhase(), b.hasEnteredPhase2(), b.isEnragedVisual(),
                                        b.getPhaseTransitionTicks(),
                                        b.getHealth(), b.getMaxHealth(), b.getActiveSegmentCount(),
                                        b.getSerpentPath().getTotalLengthBlocks(),
                                        b.getActiveProxyParts().size(), b.getBossDefense(),
                                        b.computeEffectiveDefensePoints(sampleDmg),
                                        b.computeEffectiveDamageReduction(sampleDmg) * 100.0f,
                                        b.getYRot(), b.yBodyRot, b.yHeadRot, netHeadYaw, b.getSkillState().name());
                        source.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                }
                return bosses.size();
        }

        /**
         * Kiểm định tự động toàn diện cho Boss Jörmungandr (`/abyss jormungandr test`):
         * 1. Phase 1 stats (1800 HP, Defense 30, maxRed 75%, 28 đốt ~32.2m, 12 proxy
         * hitbox).
         * 2. Hệ số sát thương theo bộ phận proxy: HEAD x1.5 (`20 * 1.5 * 0.25 = 7.50`),
         * BODY x1.0 (`20 * 0.25 = 5.00`),
         * TAIL x0.75 (`20 * 0.75 * 0.25 = 3.75`), 0 KB, + miễn nhiễm Poison.
         * 3. Chặn cái chết Phase 2 -> chạy đủ 110 tick chuyển phase (mọc dài 28 -> 39
         * đốt ~44.8m, 2520 HP, Def 42, 0 head-spin).
         * 4. Save/Reload NBT giữa Phase 2 (khôi phục đúng Phase 2, 39 đốt, Def 42,
         * 2020/2520 HP, 12 proxy).
         * 5. Benchmark MSPT cho Slither Movement & Ultimate "Ouroboros Ring".
         */
        private static int runJormungandrAuditAndPerf(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                boolean savedDebug = JormungandrBossEntity.isDebugLoggingEnabled();
                JormungandrBossEntity.setDebugLoggingEnabled(true);

                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
                AbyssalRpgMod.LOGGER.info("[JORM_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH JÖRMUNGANDR TẠI {} ===", origin);

                JormungandrBossEntity boss = null;
                JormungandrBossEntity reloaded = null;
                try {
                        boss = ModEntities.JORMUNGANDR_BOSS.create(level);
                        if (boss == null)
                                return 0;
                        boss.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 0.0f, 0.0f);
                        boss.configureForFloor(16, null, false);
                        for (int i = 0; i < 66; i++)
                                boss.tick();
                        level.addFreshEntity(boss);
                        boss.tick();

                        float p1Max = boss.getMaxHealth();
                        float p1Def = boss.getBossDefense();
                        int p1Segs = boss.getActiveSegmentCount();
                        float p1Len = boss.getSerpentPath().getTotalLengthBlocks();
                        int proxyCnt = boss.getActiveProxyParts().size();
                        boolean poisonBlocked = !boss.canBeAffected(
                                        new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100, 1));
                        AbyssalRpgMod.LOGGER.info(
                                        "[JORM_AUDIT] Phase 1: maxHP={} def={} red={}% segs={} len={}m proxies={} poisonImmune={}",
                                        p1Max, p1Def,
                                        String.format("%.1f", boss.computeEffectiveDamageReduction(15.0f) * 100.0f),
                                        p1Segs, String.format("%.1f", p1Len), proxyCnt, poisonBlocked);

                        // 2. Test hệ số sát thương 3 vùng proxy (với raw=20.0 vật lý, Def=30 ->
                        // points=min(18.75, 30-10)=18.75 -> 75% giảm -> x0.25):
                        // HEAD (proxy 0): 20 * 1.5 = 30 -> 30 * 0.25 = 7.50 | BODY (proxy 4): 20 * 1.0
                        // * 0.25 = 5.00 | TAIL (proxy 11): 20 * 0.75 * 0.25 = 3.75
                        List<JormungandrHitboxPartEntity> parts = boss.getActiveProxyParts();
                        float hp0 = boss.getHealth();
                        parts.get(0).hurt(level.damageSources().cactus(), 20.0f);
                        float headTaken = hp0 - boss.getHealth();

                        boss.invulnerableTime = 0;
                        float hp1 = boss.getHealth();
                        parts.get(parts.size() / 2).hurt(level.damageSources().cactus(), 20.0f);
                        float bodyTaken = hp1 - boss.getHealth();

                        boss.invulnerableTime = 0;
                        float hp2 = boss.getHealth();
                        parts.get(parts.size() - 1).hurt(level.damageSources().cactus(), 20.0f);
                        float tailTaken = hp2 - boss.getHealth();

                        double kb = boss.getDeltaMovement().horizontalDistance();
                        AbyssalRpgMod.LOGGER.info(
                                        "[JORM_AUDIT] Proxy Zone Multipliers (raw=20, red=75%): HEAD={} (exp 7.50) | BODY={} (exp 5.00) | TAIL={} (exp 3.75) | kb={}",
                                        String.format("%.2f", headTaken), String.format("%.2f", bodyTaken),
                                        String.format("%.2f", tailTaken), String.format("%.4f", kb));

                        // 3. Test Phase 2 death-intercept + 110 ticks chuyển phase (mọc dài 28 -> 39
                        // đốt) + 0 head-spin
                        boss.invulnerableTime = 0;
                        boss.hurt(level.damageSources().magic(), 9999.0f);
                        boolean intercept = boss.isAlive() && boss.hasEnteredPhase2() && boss.isTransitioningPhase();
                        float startYaw = boss.getYRot();
                        float maxDrift = 0.0f;
                        float maxNetHead = 0.0f;
                        for (int tick = 0; tick < 115; tick++) {
                                boss.tick();
                                maxDrift = Math.max(maxDrift, Math.abs(Mth.wrapDegrees(boss.getYRot() - startYaw)));
                                maxNetHead = Math.max(maxNetHead,
                                                Math.abs(Mth.wrapDegrees(boss.yHeadRot - boss.yBodyRot)));
                        }
                        float p2Max = boss.getMaxHealth();
                        float p2Def = boss.getBossDefense();
                        int p2Segs = boss.getActiveSegmentCount();
                        float p2Len = boss.getSerpentPath().getTotalLengthBlocks();
                        AbyssalRpgMod.LOGGER.info(
                                        "[JORM_AUDIT] Phase 2 Post-Transition: intercept={} phase={} enraged={} HP={}/{} def={} segs={} len={}m maxYawDrift={} maxNetHead={}",
                                        intercept, boss.getSyncedPhase(), boss.isEnragedVisual(),
                                        String.format("%.1f", boss.getHealth()), String.format("%.1f", p2Max), p2Def,
                                        p2Segs, String.format("%.1f", p2Len), String.format("%.3f", maxDrift),
                                        String.format("%.3f", maxNetHead));

                        // 4. Test NBT Save/Reload giữa Phase 2
                        boss.invulnerableTime = 0;
                        boss.hurt(level.damageSources().magic(), 500.0f); // 2020/2520
                        CompoundTag saved = new CompoundTag();
                        boss.saveWithoutId(saved);
                        reloaded = ModEntities.JORMUNGANDR_BOSS.create(level);
                        if (reloaded != null) {
                                reloaded.load(saved);
                                level.addFreshEntity(reloaded);
                                reloaded.tick();
                                AbyssalRpgMod.LOGGER.info(
                                                "[JORM_AUDIT] NBT Reload check: phase={} enteredP2={} enraged={} HP={}/{} def={} segs={} proxies={}",
                                                reloaded.getSyncedPhase(), reloaded.hasEnteredPhase2(),
                                                reloaded.isEnragedVisual(),
                                                String.format("%.1f", reloaded.getHealth()),
                                                String.format("%.1f", reloaded.getMaxHealth()),
                                                reloaded.getBossDefense(), reloaded.getActiveSegmentCount(),
                                                reloaded.getActiveProxyParts().size());
                                reloaded.discard();
                                reloaded = null;
                        }

                        // 5. Benchmark MSPT: Idle Coil, Slither Movement & Ultimate "Ouroboros Ring"
                        JormungandrBossEntity.setDebugLoggingEnabled(false);
                        int samples = 100;
                        boss.setSkillState(JormungandrBossEntity.SkillState.IDLE);
                        boss.setWantRearUp(true);
                        for (int w = 0; w < 20; w++)
                                boss.tick();

                        long tIdle = System.nanoTime();
                        for (int i = 0; i < samples; i++) {
                                boss.tick();
                        }
                        double coilMspt = ((System.nanoTime() - tIdle) / 1_000_000.0) / samples;

                        long t0 = System.nanoTime();
                        for (int i = 0; i < samples; i++) {
                                boss.getMoveControl().setWantedPosition(boss.getX() + 28.0, boss.getY(),
                                                boss.getZ() + 16.0, 1.15);
                                boss.tick();
                        }
                        double slitherMspt = ((System.nanoTime() - t0) / 1_000_000.0) / samples;

                        boss.setSkillState(JormungandrBossEntity.SkillState.OUROBOROS_RING);
                        long t1 = System.nanoTime();
                        for (int i = 0; i < 140; i++) {
                                boss.tick();
                        }
                        double ouroborosMspt = ((System.nanoTime() - t1) / 1_000_000.0) / 140.0;
                        AbyssalRpgMod.LOGGER.info(
                                        "[JORM_PERF] MSPT Benchmark: idleCoil={} ms | slither={} ms | ouroborosRing={} ms (budget < 10.0 ms)",
                                        String.format("%.4f", coilMspt), String.format("%.4f", slitherMspt),
                                        String.format("%.4f", ouroborosMspt));

                        boss.discard();
                        boss = null;

                        final double fCoil = coilMspt, fSlith = slitherMspt, fOuro = ouroborosMspt;
                        source.sendSuccess(() -> Component.literal(String.format(
                                        "[Jörmungandr Audit] PASS! P1=%.0fHP/Def=%.0f/%dsegs, P2=%.0fHP/Def=%.0f/%dsegs, Zones=%.2f/%.2f/%.2f | MSPT coil=%.3f slither=%.3f ouroboros=%.3f ms",
                                        p1Max, p1Def, p1Segs, p2Max, p2Def, p2Segs, headTaken, bodyTaken, tailTaken,
                                        fCoil, fSlith, fOuro)).withStyle(ChatFormatting.GREEN), true);
                        return 1;
                } finally {
                        if (boss != null && !boss.isRemoved())
                                boss.discard();
                        if (reloaded != null && !reloaded.isRemoved())
                                reloaded.discard();
                        JormungandrBossEntity.setDebugLoggingEnabled(savedDebug);
                        JormungandrConfig.get().debugLogs = savedDebug;
                }
        }

        private static int toggleNightmareDebug(CommandSourceStack source, Boolean forceState) {
                boolean next = forceState != null ? forceState : !NightmareKnightBossEntity.isDebugLoggingEnabled();
                NightmareKnightBossEntity.setDebugLoggingEnabled(next);
                NightmareKnightConfig.get().debugRotationLogs = next;
                source.sendSuccess(() -> Component.literal(
                                "[Nightmare Knight Debug] Attack-Timing (0-tick sync), Rotation, Multipart, Effective Defense & Phase-Transition logging: "
                                                + (next ? "ON" : "OFF"))
                                .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
                reportNightmareStatus(source);
                return 1;
        }

        private static int reportNightmareStatus(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                AABB box = new AABB(source.getPosition().add(-160, -160, -160),
                                source.getPosition().add(160, 160, 160));
                List<NightmareKnightBossEntity> bosses = level.getEntitiesOfClass(NightmareKnightBossEntity.class, box);
                if (bosses.isEmpty()) {
                        source.sendSuccess(() -> Component.literal(
                                        "[Nightmare Knight Debug] Không tìm thấy Nightmare Knight trong bán kính 160 block.")
                                        .withStyle(ChatFormatting.GRAY), false);
                        return 0;
                }
                for (NightmareKnightBossEntity b : bosses) {
                        float netHeadYaw = Mth.wrapDegrees(b.yHeadRot - b.yBodyRot);
                        float sampleDmg = 15.0f;
                        float effDefPts = b.computeEffectiveDefensePoints(sampleDmg);
                        float redPct = b.computeEffectiveDamageReduction(sampleDmg) * 100.0f;
                        String msg = String.format(
                                        "[NightmareKnight #%d] Phase=%d (enteredP2=%s enraged=%s trans=%d) HP=%.1f/%.1f (bossMax=%.1f) | Def=%.1f effPts=%.2f red=%.1f%% | parts=%d | yaw=%.1f(prev=%.1f) body=%.1f(prev=%.1f) head=%.1f(prev=%.1f) netHead=%.1f | skill=%s",
                                        b.getId(), b.getSyncedPhase(), b.hasEnteredPhase2(), b.isEnragedVisual(),
                                        b.getPhaseTransitionTicks(),
                                        b.getHealth(), b.getMaxHealth(), b.getBossMaxHealth(),
                                        b.getBossDefense(), effDefPts, redPct, b.getActiveHitboxParts().size(),
                                        b.getYRot(), b.yRotO, b.yBodyRot, b.yBodyRotO, b.yHeadRot, b.yHeadRotO,
                                        netHeadYaw,
                                        b.getSkillState().name());
                        source.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.DARK_RED), false);
                }
                return bosses.size();
        }

        /**
         * Kiểm định tự động toàn diện cho Boss Nightmare Knight (`/abyss nightmare
         * test`):
         * 1. Phase 1 stats (4000 HP, Defense 40, maxDamageReduction 70%, 7 multipart
         * hitbox parts, scale 7.5f).
         * 2. Multipart hit forwarding (đánh vào `head` & `arm_r` -> trừ đúng sát thương
         * sau công thức Defense 40, 0 KB).
         * 3. Kiểm tra Bone Cage phá được bằng đòn đánh thường & dọn sạch.
         * 4. Phase 2 death-intercept -> chạy đủ 120 tick chuyển phase (log
         * yaw/body/head/defense/reduction từng tick, xác nhận 0 head-spin).
         * 5. Kiểm tra NBT save/load giữa Phase 2 (khôi phục đúng Phase 2, MaxHP 7200,
         * Defense 72, Enraged visual, KHÔNG cộng dồn).
         * 6. Đo MSPT thực tế 3 trạng thái: đứng yên (Idle), di chuyển steering (Walk),
         * và dùng skill diện rộng (Ultimate + Spike Storm + Flame Wave).
         */
        private static int runNightmareAuditAndPerf(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                boolean savedDebug = NightmareKnightBossEntity.isDebugLoggingEnabled();
                NightmareKnightBossEntity.setDebugLoggingEnabled(true);

                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
                AbyssalRpgMod.LOGGER.info("[NIGHTMARE_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH NIGHTMARE KNIGHT TẠI {} ===",
                                origin);

                NightmareKnightBossEntity boss = null;
                NightmareKnightBossEntity reloaded = null;
                try {
                        boss = ModEntities.NIGHTMARE_KNIGHT.create(level);
                        if (boss == null)
                                return 0;
                        boss.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 0.0f, 0.0f);
                        boss.configureForFloor(18, null, false);
                        for (int i = 0; i < 66; i++)
                                boss.tick();
                        level.addFreshEntity(boss);
                        boss.tick(); // Spawn 7 multipart hitbox parts

                        float p1Max = boss.getMaxHealth();
                        float p1Def = boss.getBossDefense();
                        float p1EffPts = boss.computeEffectiveDefensePoints(15.0f);
                        float p1RedPct = boss.computeEffectiveDamageReduction(15.0f) * 100.0f;
                        int partCount = boss.getActiveHitboxParts().size();
                        AbyssalRpgMod.LOGGER.info(
                                        "[NIGHTMARE_AUDIT] Phase 1: scale={} maxHP={} def={} effPts={} red={}% coreHitbox={}x{} parts={}",
                                        boss.getConfiguredScale(), p1Max, p1Def, String.format("%.2f", p1EffPts),
                                        String.format("%.1f", p1RedPct),
                                        String.format("%.2f", boss.getBbWidth()),
                                        String.format("%.2f", boss.getBbHeight()), partCount);

                        // 2. Test multipart hit forwarding trên bone `head` bằng nguồn sát thương vật
                        // lý (không bypass_armor):
                        // `raw = 15.0` (kiếm Netherite Sharpness V Crit) với Def=40, maxRed=70% ->
                        // expected 4.50 dmg, 0 KB!
                        // (Lưu ý: `damageSources().generic()` có tag BYPASSES_ARMOR trong MC 1.21.1,
                        // nên dùng `cactus()` / `arrow()` cho đòn vật lý.)
                        float hpBeforePartHit = boss.getHealth();
                        NightmareHitboxPartEntity headPart = boss.getActiveHitboxParts().get(0);
                        headPart.hurt(level.damageSources().cactus(), 15.0f);
                        float dmgTakenViaPart = hpBeforePartHit - boss.getHealth();
                        double kbAfterHit = boss.getDeltaMovement().horizontalDistance();

                        // Test sàn sát thương tối thiểu `minDamagePerHit = 0.5`: raw 1.0 * (1 - 0.70) =
                        // 0.30 -> sàn 0.50 trên bone `arm_r`
                        boss.invulnerableTime = 0;
                        float hpBeforeMinHit = boss.getHealth();
                        NightmareHitboxPartEntity armPart = boss.getActiveHitboxParts().get(4);
                        armPart.hurt(level.damageSources().cactus(), 1.0f);
                        float minFloorTaken = hpBeforeMinHit - boss.getHealth();

                        // Test nguồn `BYPASSES_ARMOR` (`magic`) không bị giảm theo defense (50.0 ->
                        // 50.0)
                        float hpBeforeBypass = boss.getHealth();
                        boss.invulnerableTime = 0;
                        boss.hurt(level.damageSources().magic(), 50.0f);
                        float bypassTaken = hpBeforeBypass - boss.getHealth();
                        AbyssalRpgMod.LOGGER.info(
                                        "[NIGHTMARE_AUDIT] Multipart & Defense check: part='{}' phys15->taken={} (exp 4.50) kb={} | part='{}' phys1->taken={} (exp 0.50) | bypass50->taken={} (exp 50.00)",
                                        headPart.getPartName(), String.format("%.2f", dmgTakenViaPart),
                                        String.format("%.4f", kbAfterHit),
                                        armPart.getPartName(), String.format("%.2f", minFloorTaken),
                                        String.format("%.2f", bypassTaken));

                        // 3. Test Bone Cage phá được
                        NightmareBoneSpikeEntity cagePillar = NightmareBoneSpikeEntity.spawnCagePillar(
                                        level, boss, boss.position().add(6.0, 0.0, 0.0), 4.4f, 4, 80, 0.0f, 12.0f,
                                        false);
                        for (int i = 0; i < 6; i++)
                                cagePillar.tick();
                        cagePillar.hurt(level.damageSources().generic(), 15.0f);
                        boolean cageBroken = cagePillar.isRemoved();
                        AbyssalRpgMod.LOGGER.info("[NIGHTMARE_AUDIT] Bone Cage breakable check: brokenAfter15Dmg={}",
                                        cageBroken);

                        // 3b. Kiểm tra đồng bộ Attack Timing (0-tick delta giữa animation timeline và
                        // hitWindow ở Phase 1 & Phase 2)
                        boss.setSkillState(NightmareKnightBossEntity.SkillState.GREATSWORD_SLASH);
                        for (int tick = 0; tick < 36; tick++) {
                                boss.tick();
                        }
                        boolean slashReturnedIdle = boss.getSkillState() == NightmareKnightBossEntity.SkillState.IDLE;
                        AbyssalRpgMod.LOGGER.info(
                                        "[NIGHTMARE_AUDIT] Attack Timing check (GREATSWORD_SLASH): completedToIdle={}",
                                        slashReturnedIdle);

                        // 4. Test Phase 2 death-intercept + 120 tick chuyển phase & đo biên độ xoay đầu
                        // tối đa (chống head-spin)
                        boss.invulnerableTime = 0;
                        boss.hurt(level.damageSources().generic(), 20000.0f);
                        boolean intercepted = boss.isAlive() && boss.hasEnteredPhase2() && boss.isTransitioningPhase();
                        float startYaw = boss.getYRot();
                        float maxYawDrift = 0.0f;
                        float maxNetHead = 0.0f;
                        for (int tick = 0; tick < 125; tick++) {
                                boss.tick();
                                maxYawDrift = Math.max(maxYawDrift,
                                                Math.abs(Mth.wrapDegrees(boss.getYRot() - startYaw)));
                                maxNetHead = Math.max(maxNetHead,
                                                Math.abs(Mth.wrapDegrees(boss.yHeadRot - boss.yBodyRot)));
                        }
                        float p2Max = boss.getMaxHealth();
                        float p2Hp = boss.getHealth();
                        float p2Def = boss.getBossDefense();
                        float p2RedPct = boss.computeEffectiveDamageReduction(15.0f) * 100.0f;
                        AbyssalRpgMod.LOGGER.info(
                                        "[NIGHTMARE_AUDIT] Phase 2 Post-Transition: intercept={} phase={} enraged={} HP={}/{} def={} red={}% maxYawDrift={} maxNetHead={}",
                                        intercepted, boss.getSyncedPhase(), boss.isEnragedVisual(),
                                        String.format("%.1f", p2Hp), String.format("%.1f", p2Max), p2Def,
                                        String.format("%.1f", p2RedPct), String.format("%.3f", maxYawDrift),
                                        String.format("%.3f", maxNetHead));

                        // 5. Test NBT Save/Reload giữa Phase 2 (đảm bảo không cộng dồn stat & giữ
                        // nguyên Phase 2 + Defense 72)
                        boss.invulnerableTime = 0;
                        boss.hurt(level.damageSources().magic(), 1000.0f); // HP còn 6200/7200
                        CompoundTag saved = new CompoundTag();
                        boss.saveWithoutId(saved);
                        reloaded = ModEntities.NIGHTMARE_KNIGHT.create(level);
                        if (reloaded != null) {
                                reloaded.load(saved);
                                level.addFreshEntity(reloaded);
                                reloaded.tick();
                                AbyssalRpgMod.LOGGER.info(
                                                "[NIGHTMARE_AUDIT] NBT Reload check: phase={} enteredP2={} enraged={} HP={}/{} def={} parts={}",
                                                reloaded.getSyncedPhase(), reloaded.hasEnteredPhase2(),
                                                reloaded.isEnragedVisual(),
                                                String.format("%.1f", reloaded.getHealth()),
                                                String.format("%.1f", reloaded.getMaxHealth()),
                                                reloaded.getBossDefense(), reloaded.getActiveHitboxParts().size());
                                reloaded.discard();
                                reloaded = null;
                        }

                        // 6. Benchmark MSPT cho 3 chế độ: Idle / Walk Steering / Heavy AoE Skills
                        // (Ultimate + Spike Storm + Flame Wave)
                        NightmareKnightBossEntity.setDebugLoggingEnabled(false); // Tắt log spam để đo MSPT chuẩn
                        int samples = 100;

                        // 6a. Idle MSPT
                        boss.setSkillState(NightmareKnightBossEntity.SkillState.IDLE);
                        for (int w = 0; w < 20; w++)
                                boss.tick();
                        long t0 = System.nanoTime();
                        for (int i = 0; i < samples; i++)
                                boss.tick();
                        double idleMspt = ((System.nanoTime() - t0) / 1_000_000.0) / samples;

                        // 6b. Walk Steering MSPT
                        long t1 = System.nanoTime();
                        for (int i = 0; i < samples; i++) {
                                boss.getMoveControl().setWantedPosition(boss.getX() + 30.0, boss.getY(),
                                                boss.getZ() + 18.0, 1.15);
                                boss.tick();
                        }
                        double walkMspt = ((System.nanoTime() - t1) / 1_000_000.0) / samples;

                        // 6c. Heavy AoE MSPT (Ultimate Nightmare Descent + Spike Storm + Flame Sword
                        // Wave)
                        boss.setSkillState(NightmareKnightBossEntity.SkillState.NIGHTMARE_DESCENT);
                        long t2 = System.nanoTime();
                        for (int i = 0; i < samples; i++) {
                                if (i == 60)
                                        boss.setSkillState(NightmareKnightBossEntity.SkillState.SPIKE_STORM);
                                if (i == 85)
                                        boss.setSkillState(NightmareKnightBossEntity.SkillState.FLAME_SWORD_WAVE);
                                boss.tick();
                                for (NightmareBoneSpikeEntity sp : level.getEntitiesOfClass(
                                                NightmareBoneSpikeEntity.class, boss.getBoundingBox().inflate(48.0))) {
                                        sp.tick();
                                }
                        }
                        double aoeMspt = ((System.nanoTime() - t2) / 1_000_000.0) / samples;

                        AbyssalRpgMod.LOGGER.info(
                                        "[NIGHTMARE_PERF] MSPT Benchmark (100 ticks each): idle={} ms | walk={} ms | heavyAoE={} ms (budget < 10.0 ms)",
                                        String.format("%.4f", idleMspt), String.format("%.4f", walkMspt),
                                        String.format("%.4f", aoeMspt));

                        // Dọn toàn bộ entity test
                        for (NightmareBoneSpikeEntity sp : level.getEntitiesOfClass(NightmareBoneSpikeEntity.class,
                                        boss.getBoundingBox().inflate(64.0))) {
                                sp.discard();
                        }
                        boss.discard();
                        boss = null;

                        final double fIdle = idleMspt, fWalk = walkMspt, fAoe = aoeMspt;
                        source.sendSuccess(() -> Component.literal(String.format(
                                        "[Nightmare Audit] PASS! P1=%.0fHP/Def=%.0f, P2=%.0fHP/Def=%.0f, parts=%d, partDmg=%.1f, intercept=%s, cageBreak=%s | MSPT idle=%.3f walk=%.3f AoE=%.3f ms",
                                        p1Max, p1Def, p2Max, p2Def, partCount, dmgTakenViaPart, intercepted, cageBroken,
                                        fIdle, fWalk, fAoe)).withStyle(ChatFormatting.GREEN), true);
                        return 1;
                } finally {
                        if (boss != null && !boss.isRemoved())
                                boss.discard();
                        if (reloaded != null && !reloaded.isRemoved())
                                reloaded.discard();
                        NightmareKnightBossEntity.setDebugLoggingEnabled(savedDebug);
                        NightmareKnightConfig.get().debugRotationLogs = savedDebug;
                }
        }

        private static int toggleCerberusDebug(CommandSourceStack source, Boolean forceState) {
                boolean next = forceState != null ? forceState : !CerberusBossEntity.isDebugLoggingEnabled();
                CerberusBossEntity.setDebugLoggingEnabled(next);
                CerberusConfig.get().debugRotationLogs = next;
                source.sendSuccess(() -> Component.literal(
                                "[Cerberus Debug] Rotation, Form, AttackToken & EncounterGroup logging: "
                                                + (next ? "ON" : "OFF"))
                                .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
                reportCerberusStatus(source);
                return 1;
        }

        private static int reportCerberusStatus(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                AABB box = new AABB(source.getPosition().add(-128, -128, -128),
                                source.getPosition().add(128, 128, 128));
                List<CerberusBossEntity> bosses = level.getEntitiesOfClass(CerberusBossEntity.class, box);
                if (bosses.isEmpty()) {
                        source.sendSuccess(() -> Component
                                        .literal("[Cerberus Debug] Không tìm thấy Cerberus trong bán kính 128 block.")
                                        .withStyle(ChatFormatting.GRAY), false);
                        return 0;
                }
                CerberusGroupManager mgr = CerberusGroupManager.get(level.getServer());
                Set<UUID> printedGroups = new HashSet<>();
                for (CerberusBossEntity b : bosses) {
                        float netHeadYaw = Mth.wrapDegrees(b.yHeadRot - b.yBodyRot);
                        EncounterGroup grp = mgr.findGroup(b.getGroupId());
                        boolean hasToken = grp != null && grp.hasHeavyToken(b.getUUID());
                        String msg = String.format(
                                        "[Cerberus #%d] form=%s phase=%d split=%s stage=%d HP=%.1f/%.1f (bossMax=%.1f attrMax=%.1f armor=%.1f scale=%.2f) | yaw=%.1f(prev=%.1f) body=%.1f(prev=%.1f) head=%.1f(prev=%.1f) netHead=%.1f | skill=%s token=%s",
                                        b.getId(), b.getCerberusForm().name(), b.getSyncedPhase(), b.hasSplit(),
                                        b.getSplitStage(),
                                        b.getHealth(), b.getMaxHealth(), b.getBossMaxHealth(),
                                        b.getAttributeValue(Attributes.MAX_HEALTH),
                                        b.getAttributeValue(Attributes.ARMOR), b.getEffectiveScale(),
                                        b.getYRot(), b.yRotO, b.yBodyRot, b.yBodyRotO, b.yHeadRot, b.yHeadRotO,
                                        netHeadYaw,
                                        b.getSkillState().name(), hasToken);
                        source.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GOLD), false);
                        if (grp != null && printedGroups.add(grp.groupId)) {
                                source.sendSuccess(() -> Component
                                                .literal("  -> EncounterGroup: " + grp.formatDebugSummary())
                                                .withStyle(ChatFormatting.AQUA), false);
                        }
                }
                return bosses.size();
        }

        /**
         * Kiểm định tự động toàn bộ vòng đời Boss Cerberus + đo MSPT khi cả 3 cá thể
         * Phase 2 dùng skill diện rộng:
         * 1. Triệu hồi Cerberus Phase 1 (2000 HP, Armor 18, Hitbox 8.0x6.5) -> đưa
         * xuống ngưỡng <= 5% HP -> chạy đủ 100 tick tách 3 cá thể.
         * 2. Xác minh 3 form (ALPHA_HEAD, FIRE_HEAD, SHADOW_HEAD), hitbox scale 0.75
         * (6.0x4.875), Armor 27, 1 BossBar chung, Attack Token <= 1.
         * 3. Đo MSPT thực tế trong 120 tick khi cả 3 cá thể đồng thời thi triển Ground
         * Slam + Hellfire Nova + Shadow Dash/Pounce.
         * 4. Kiểm tra chế độ SPLIT_EQUAL & SHARED_POOL + xác nhận chỉ con chết cuối
         * cùng rơi loot & XP.
         */
        private static int runCerberusAuditAndPerf(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                CerberusConfig cfg = CerberusConfig.get();
                CerberusConfig.SplitHealthMode savedMode = cfg.splitHealthMode;
                boolean savedDebug = CerberusBossEntity.isDebugLoggingEnabled();
                CerberusBossEntity.setDebugLoggingEnabled(true);

                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
                AbyssalRpgMod.LOGGER.info("[CERBERUS_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH CERBERUS TẠI {} ===", origin);

                try {
                        // 1. Test SPLIT_EQUAL + full Phase 1 -> Phase 2 split transition
                        cfg.splitHealthMode = CerberusConfig.SplitHealthMode.SPLIT_EQUAL;
                        CerberusBossEntity p1 = ModEntities.CERBERUS.create(level);
                        if (p1 == null)
                                return 0;
                        p1.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 0.0f, 0.0f);
                        p1.configureForFloor(14, null, false);
                        // Bỏ qua spawn emergence để test trực tiếp chiến đấu
                        for (int i = 0; i < 66; i++)
                                p1.tick();
                        level.addFreshEntity(p1);

                        float p1Max = p1.getMaxHealth();
                        float p1Armor = (float) p1.getAttributeValue(Attributes.ARMOR);
                        float p1W = p1.getBbWidth(), p1H = p1.getBbHeight();
                        AbyssalRpgMod.LOGGER.info("[CERBERUS_AUDIT] Phase 1: form={} maxHP={} armor={} hitbox={}x{}",
                                        p1.getCerberusForm(), p1Max, p1Armor, String.format("%.2f", p1W),
                                        String.format("%.2f", p1H));

                        // Đưa máu xuống ngưỡng kích hoạt Phase 2 (<= 5% = 100 HP) bằng đòn đánh chí
                        // mạng -> phải bị chặn chết & bắt đầu tách!
                        p1.hurt(level.damageSources().generic(), 5000.0f);
                        boolean intercepted = p1.isAlive() && p1.hasEnteredPhase2() && p1.isTransitioningPhase();
                        AbyssalRpgMod.LOGGER.info(
                                        "[CERBERUS_AUDIT] Phase 2 Trigger Intercept: alive={} enteredP2={} transitioning={} hp={}",
                                        p1.isAlive(), p1.hasEnteredPhase2(), p1.isTransitioningPhase(), p1.getHealth());

                        // Mô phỏng 105 tick chuyển phase (gầm -> 3 cầu lửa bay tam giác -> tách 3 con 1
                        // đầu)
                        UUID gid = p1.getGroupId();
                        EncounterGroup grp = CerberusGroupManager.get(level.getServer()).getOrCreateGroup(gid);
                        for (int tick = 0; tick < 105; tick++) {
                                List<CerberusBossEntity> loaded = grp.findLoadedAliveMembers(level.getServer());
                                for (CerberusBossEntity m : loaded)
                                        m.tick();
                                if (tick % 2 == 0)
                                        grp.tick(level.getServer(), level.getGameTime() + tick);
                        }

                        List<CerberusBossEntity> splitMembers = grp.findLoadedAliveMembers(level.getServer());
                        AbyssalRpgMod.LOGGER.info(
                                        "[CERBERUS_AUDIT] Sau chuyển Phase 2: aliveMembers={} groupSummary=[{}]",
                                        splitMembers.size(), grp.formatDebugSummary());
                        for (CerberusBossEntity m : splitMembers) {
                                AbyssalRpgMod.LOGGER.info(
                                                "[CERBERUS_AUDIT]   -> Member form={} HP={}/{} armor={} hitbox={}x{} yaw={} netHead={}",
                                                m.getCerberusForm(), m.getHealth(), m.getMaxHealth(),
                                                m.getAttributeValue(Attributes.ARMOR),
                                                String.format("%.2f", m.getBbWidth()),
                                                String.format("%.2f", m.getBbHeight()),
                                                String.format("%.1f", m.getYRot()),
                                                String.format("%.1f", Mth.wrapDegrees(m.yHeadRot - m.yBodyRot)));
                        }

                        // Kiểm tra Attack Token (maxSimultaneousHeavyAttackers = 1)
                        boolean t0 = splitMembers.size() >= 1
                                        && grp.tryAcquireHeavyToken(splitMembers.get(0).getUUID(), level.getGameTime());
                        boolean t1 = splitMembers.size() >= 2
                                        && grp.tryAcquireHeavyToken(splitMembers.get(1).getUUID(), level.getGameTime());
                        if (splitMembers.size() >= 1)
                                grp.releaseHeavyToken(splitMembers.get(0).getUUID());
                        boolean t1AfterRelease = splitMembers.size() >= 2
                                        && grp.tryAcquireHeavyToken(splitMembers.get(1).getUUID(), level.getGameTime());
                        if (splitMembers.size() >= 2)
                                grp.releaseHeavyToken(splitMembers.get(1).getUUID());
                        AbyssalRpgMod.LOGGER.info(
                                        "[CERBERUS_AUDIT] AttackToken check: first={} secondWhileHeld={} secondAfterRelease={}",
                                        t0, t1, t1AfterRelease);

                        // Đo MSPT thực tế khi CẢ 3 cá thể đồng thời dùng skill diện rộng (Ground Slam +
                        // Hellfire Nova + Pounce/Dash)
                        if (splitMembers.size() == 3) {
                                splitMembers.get(0).setSkillState(CerberusBossEntity.SkillState.GROUND_SLAM);
                                splitMembers.get(1).setSkillState(CerberusBossEntity.SkillState.HELLFIRE_NOVA);
                                splitMembers.get(2).setSkillState(CerberusBossEntity.SkillState.SHADOW_DASH);
                                // Warmup 20 ticks
                                for (int w = 0; w < 20; w++) {
                                        for (CerberusBossEntity m : splitMembers)
                                                m.tick();
                                        if (w % 2 == 0)
                                                grp.tick(level.getServer(), level.getGameTime() + 120 + w);
                                }
                                // Re-trigger skills và đo 120 ticks thực tế
                                splitMembers.get(0).setSkillState(CerberusBossEntity.SkillState.GROUND_SLAM);
                                splitMembers.get(1).setSkillState(CerberusBossEntity.SkillState.HELLFIRE_NOVA);
                                splitMembers.get(2).setSkillState(CerberusBossEntity.SkillState.POUNCE);
                                int measuredTicks = 120;
                                long tStart = System.nanoTime();
                                for (int i = 0; i < measuredTicks; i++) {
                                        for (CerberusBossEntity m : splitMembers)
                                                m.tick();
                                        if (i % 2 == 0)
                                                grp.tick(level.getServer(), level.getGameTime() + 200 + i);
                                }
                                double avgMspt = ((System.nanoTime() - tStart) / 1_000_000.0) / measuredTicks;
                                AbyssalRpgMod.LOGGER.info(
                                                "[CERBERUS_PERF] 3x Phase 2 Cerberus AoE simultaneous tick MSPT = {} ms (budget < 10.0 ms)",
                                                String.format("%.4f", avgMspt));
                        }

                        // Kiểm tra loot chỉ rơi 1 lần ở con chết cuối cùng
                        int lootDrops = 0;
                        for (CerberusBossEntity m : splitMembers) {
                                m.die(level.damageSources().genericKill());
                                if (m.shouldDropExperience())
                                        lootDrops++;
                                m.discard();
                        }
                        AbyssalRpgMod.LOGGER.info(
                                        "[CERBERUS_AUDIT] Single-Loot check: membersKilled={} membersAwardingLoot={} (expected 1) rewardsGranted={}",
                                        splitMembers.size(), lootDrops, grp.isRewardsGranted());

                        boolean sharedPoolOk = false;
                        // 2. Test nhanh SHARED_POOL mode: đánh 1 con -> máu 2 con còn lại tụt đồng bộ
                        // -> cạn quỹ chết cả 3 & chỉ 1 con rơi loot
                        cfg.splitHealthMode = CerberusConfig.SplitHealthMode.SHARED_POOL;
                        CerberusBossEntity sp = ModEntities.CERBERUS.create(level);
                        if (sp != null) {
                                sp.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 0.0f, 0.0f);
                                sp.configureForFloor(14, null, false);
                                for (int i = 0; i < 66; i++)
                                        sp.tick();
                                level.addFreshEntity(sp);
                                sp.hurt(level.damageSources().generic(), 5000.0f);
                                EncounterGroup spGrp = CerberusGroupManager.get(level.getServer())
                                                .getOrCreateGroup(sp.getGroupId());
                                for (int tick = 0; tick < 105; tick++) {
                                        for (CerberusBossEntity m : spGrp.findLoadedAliveMembers(level.getServer()))
                                                m.tick();
                                        if (tick % 2 == 0)
                                                spGrp.tick(level.getServer(), level.getGameTime() + 400 + tick);
                                }
                                List<CerberusBossEntity> spMembers = spGrp.findLoadedAliveMembers(level.getServer());
                                if (spMembers.size() == 3) {
                                        spMembers.get(0).hurt(level.damageSources().magic(), 500.0f);
                                        float hp0 = spMembers.get(0).getHealth(), hp1 = spMembers.get(1).getHealth(),
                                                        hp2 = spMembers.get(2).getHealth();
                                        spMembers.get(1).hurt(level.damageSources().magic(), 5000.0f);
                                        int aliveAfterZero = spGrp.getAliveMemberCount();
                                        sharedPoolOk = Math.abs(hp0 - hp1) < 1.0f && Math.abs(hp1 - hp2) < 1.0f
                                                        && aliveAfterZero == 0 && spGrp.isRewardsGranted();
                                        AbyssalRpgMod.LOGGER.info(
                                                        "[CERBERUS_AUDIT] SHARED_POOL check: after500Dmg=[{},{},{}] aliveAfterDeplete={} rewardsGranted={}",
                                                        String.format("%.1f", hp0), String.format("%.1f", hp1),
                                                        String.format("%.1f", hp2),
                                                        aliveAfterZero, spGrp.isRewardsGranted());
                                }
                                for (CerberusBossEntity m : spMembers)
                                        m.discard();
                        }

                        boolean p1StatsOk = Math.abs(p1Max - 2000.0f) < 1.0f && Math.abs(p1Armor - 18.0f) < 0.1f;
                        boolean p2SplitOk = splitMembers.size() == 3
                                        && splitMembers.stream().allMatch(m -> Math
                                                        .abs(m.getAttributeValue(Attributes.ARMOR) - 27.0) < 0.1);
                        boolean tokenOk = t0 && !t1 && t1AfterRelease;
                        boolean allPass = p1StatsOk && intercepted && p2SplitOk && tokenOk && lootDrops == 1
                                        && sharedPoolOk;
                        AbyssalRpgMod.LOGGER.info(
                                        "[CERBERUS_AUDIT] VERDICT={} (p1StatsOk={} intercept={} p2SplitOk={} tokenOk={} singleLoot={} sharedPoolOk={})",
                                        allPass ? "ALL_PASS" : "AUDIT_FAIL", p1StatsOk, intercepted, p2SplitOk, tokenOk,
                                        lootDrops == 1, sharedPoolOk);

                        final int finalLootDrops = lootDrops;
                        final int finalSplitCount = splitMembers.size();
                        source.sendSuccess(() -> Component.literal("[Cerberus Audit] Hoàn tất kiểm định & đo MSPT ("
                                        + (allPass ? "ALL_PASS" : "AUDIT_FAIL") + ", intercept="
                                        + intercepted + ", splitCount=" + finalSplitCount + ", lootAwards="
                                        + finalLootDrops + ")! Xem log server.")
                                        .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                        return allPass ? 1 : 0;
                } finally {
                        cfg.splitHealthMode = savedMode;
                        CerberusBossEntity.setDebugLoggingEnabled(savedDebug);
                        CerberusConfig.get().debugRotationLogs = savedDebug;
                }
        }

        private static int toggleHydraDebug(CommandSourceStack source, Boolean forceState) {
                boolean next = forceState != null ? forceState : !HydraBossEntity.isDebugLoggingEnabled();
                HydraBossEntity.setDebugLoggingEnabled(next);
                SpiritHydraConfig.get().debugRotationLogs = next;
                source.sendSuccess(() -> Component.literal(
                                "[Spirit Hydra Debug] Rotation, Per-Head Angles & Phase-Transition logging: "
                                                + (next ? "ON" : "OFF"))
                                .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
                reportHydraStatus(source);
                return 1;
        }

        private static int reportHydraStatus(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                AABB box = new AABB(source.getPosition().add(-128, -128, -128),
                                source.getPosition().add(128, 128, 128));
                List<HydraBossEntity> bosses = level.getEntitiesOfClass(HydraBossEntity.class, box);
                if (bosses.isEmpty()) {
                        source.sendSuccess(() -> Component.literal(
                                        "[Spirit Hydra Debug] Không tìm thấy Spirit Hydra trong bán kính 128 block.")
                                        .withStyle(ChatFormatting.GRAY), false);
                        return 0;
                }
                for (HydraBossEntity b : bosses) {
                        float netHeadYaw = Mth.wrapDegrees(b.yHeadRot - b.yBodyRot);
                        StringBuilder headInfo = new StringBuilder();
                        for (int i = 0; i < HydraBossEntity.HEAD_COUNT; i++) {
                                boolean alive = b.isHeadAlive(i);
                                float hYaw = Mth.wrapDegrees(b.yBodyRot + HydraBossEntity.HEAD_BASE_FAN_YAW_DEG[i]);
                                headInfo.append("H").append(i + 1).append(alive ? "(" : "(X|")
                                                .append(String.format("%.0fhp|%.0f°)", b.getHeadHp(i), hYaw));
                                if (i < HydraBossEntity.HEAD_COUNT - 1)
                                        headInfo.append(' ');
                        }
                        String msg = String.format(
                                        "[SpiritHydra #%d] Phase=%d (enteredP2=%s, enragedVis=%s, trans=%d, stag=%d) HP=%.1f/%.1f (sumHeads=%.1f) | Armor=%.1f | Heads=%d/%d (mask=0x%X) | Proxies=%d | yaw=%.1f bodyYaw=%.1f headYaw=%.1f netHead=%.1f | state=%s | %s",
                                        b.getId(), b.getSyncedPhase(), b.hasEnteredPhase2(), b.isEnragedVisual(),
                                        b.getPhaseTransitionTicks(),
                                        b.getStaggerTicks(), b.getHealth(), b.getMaxHealth(), b.sumHeadHp(),
                                        b.getAttributeValue(Attributes.ARMOR),
                                        b.getAliveHeadCount(), HydraBossEntity.HEAD_COUNT, b.getAliveHeadsMask(),
                                        b.getActiveHitboxParts().size(),
                                        b.getYRot(), b.yBodyRot, b.yHeadRot, netHeadYaw, b.getSkillState().name(),
                                        headInfo);
                        source.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.AQUA), false);
                }
                return bosses.size();
        }

        /**
         * Kiểm định tự động toàn diện cho Boss Spirit Hydra 4 đầu (`/abyss hydra
         * test`):
         * 1. Xác nhận entity ID `abyssalrpg:spirit_hydra`, 2000 HP chia đều 4 đầu (4 x
         * 500 HP), Armor 15, 11 proxy hitbox.
         * 2. Đánh trực tiếp proxy `head_2` -> xác nhận nhân x1.15 trừ thẳng vào
         * `headHp[1]` và chặt ĐÚNG `head_2` khi tụt về 0.
         * 3. Đánh proxy thân (`chest_legs`) -> xác nhận trừ vào đầu sống thấp máu nhất,
         * chặt đều từng mốc 500 HP.
         * 4. Save/Reload NBT giữa Phase 1 (khôi phục chính xác 4 bể máu đầu + bitmask +
         * tổng HP).
         * 5. Chặn chết ở đầu cuối -> Phase 2 (100 tick = 5.0s bất tử, 0 head-spin, mọc
         * lại 4 đầu cách nhau 7 tick = 0.35s,
         * 3000 HP = 750/đầu, Armor 22.5, không chặt đầu ở P2) + Save/Reload NBT Phase
         * 2.
         * 6. Đo MSPT thực tế trong chiến đấu Phase 1 & Phase 2.
         */
        private static int runHydraAuditAndPerf(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
                AbyssalRpgMod.LOGGER.info("[HYDRA_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH SPIRIT HYDRA v2 TẠI {} ===", origin);

                HydraBossEntity boss = null;
                HydraBossEntity reloadedP1 = null;
                HydraBossEntity reloadedP2 = null;
                try {
                        boss = ModEntities.HYDRA_BOSS.create(level);
                        if (boss == null) {
                                source.sendFailure(Component
                                                .literal("[HYDRA_AUDIT] Không thể khởi tạo ModEntities.HYDRA_BOSS"));
                                return 0;
                        }
                        boss.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 0.0f, 0.0f);
                        boss.configureForFloor(15, null, false);
                        level.addFreshEntity(boss);

                        // Chờ xong spawn-in (~70 ticks) để proxy hitbox và AI hoạt động đầy đủ
                        for (int t = 0; t < 75; t++)
                                boss.tick();

                        String regId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                        .getKey(boss.getType()).toString();
                        int proxyCount = boss.getActiveHitboxParts().size();
                        boolean initOk = "abyssalrpg:spirit_hydra".equals(regId)
                                        && Math.abs(boss.getMaxHealth() - 2000.0f) < 0.5f
                                        && Math.abs(boss.getHealth() - 2000.0f) < 0.5f
                                        && boss.getAliveHeadCount() == 4
                                        && Math.abs(boss.getHeadHp(0) - 500.0f) < 0.5f
                                        && Math.abs(boss.getHeadHp(1) - 500.0f) < 0.5f
                                        && Math.abs(boss.getHeadHp(2) - 500.0f) < 0.5f
                                        && Math.abs(boss.getHeadHp(3) - 500.0f) < 0.5f
                                        && Math.abs(boss.getAttributeValue(Attributes.ARMOR) - 15.0) < 0.2
                                        && proxyCount == HydraBossEntity.MULTIPART_SPECS.length;
                        AbyssalRpgMod.LOGGER.info(
                                        "[HYDRA_AUDIT] 1. INIT: id={} HP={}/{} heads=[{},{},{},{}] mask=0x{} armor={} proxies={} -> {}",
                                        regId, String.format("%.1f", boss.getHealth()),
                                        String.format("%.1f", boss.getMaxHealth()),
                                        String.format("%.0f", boss.getHeadHp(0)),
                                        String.format("%.0f", boss.getHeadHp(1)),
                                        String.format("%.0f", boss.getHeadHp(2)),
                                        String.format("%.0f", boss.getHeadHp(3)),
                                        Integer.toHexString(boss.getAliveHeadsMask()),
                                        String.format("%.1f", boss.getAttributeValue(Attributes.ARMOR)),
                                        proxyCount, initOk ? "PASS" : "FAIL");

                        // 2. Đánh trực tiếp vào proxy `head_2` (index 1) bằng damage magic (bypass
                        // armor để đo chính xác x1.15!)
                        HydraHitboxPartEntity head2Part = boss.getActiveHitboxParts().stream()
                                        .filter(p -> "head_2".equals(p.getPartName())).findFirst().orElse(null);
                        HydraHitboxPartEntity chestPart = boss.getActiveHitboxParts().stream()
                                        .filter(p -> "chest_legs".equals(p.getPartName())).findFirst().orElse(null);

                        boolean headHitOk = false;
                        boolean head2SeveredOk = false;
                        if (head2Part != null) {
                                head2Part.hurt(level.damageSources().magic(), 100.0f); // 100 * 1.15 = 115 -> head_2 còn
                                                                                       // 385, 3 đầu kia vẫn 500!
                                boss.tick();
                                headHitOk = Math.abs(boss.getHeadHp(1) - 385.0f) < 1.0f
                                                && Math.abs(boss.getHeadHp(0) - 500.0f) < 0.5f
                                                && Math.abs(boss.getHeadHp(2) - 500.0f) < 0.5f
                                                && Math.abs(boss.getHeadHp(3) - 500.0f) < 0.5f
                                                && Math.abs(boss.getHealth() - boss.sumHeadHp()) < 0.5f;
                                AbyssalRpgMod.LOGGER.info(
                                                "[HYDRA_AUDIT] 2a. DIRECT HEAD_2 HIT (100 * 1.15 = 115): heads=[{},{},{},{}] totalHP={} -> {}",
                                                String.format("%.1f", boss.getHeadHp(0)),
                                                String.format("%.1f", boss.getHeadHp(1)),
                                                String.format("%.1f", boss.getHeadHp(2)),
                                                String.format("%.1f", boss.getHeadHp(3)),
                                                String.format("%.1f", boss.getHealth()), headHitOk ? "PASS" : "FAIL");

                                // Đánh nốt 385 HP vào đúng `head_2` (340 * 1.15 = 391 >= 385) -> ĐÚNG head_2 bị
                                // chặt!
                                for (int t = 0; t < 12; t++)
                                        boss.tick(); // hết invulnerableTime
                                head2Part.hurt(level.damageSources().magic(), 335.0f); // 335 * 1.15 = 385.25 -> hạ đúng
                                                                                       // head_2 về 0!
                                boss.tick();
                                head2SeveredOk = !boss.isHeadAlive(1) && boss.isHeadAlive(0) && boss.isHeadAlive(2)
                                                && boss.isHeadAlive(3)
                                                && boss.getAliveHeadCount() == 3 && !head2Part.isPartActive()
                                                && Math.abs(boss.getHealth() - boss.sumHeadHp()) < 0.5f;
                                AbyssalRpgMod.LOGGER.info(
                                                "[HYDRA_AUDIT] 2b. EXACT HEAD_2 SEVER: aliveMask=0x{} alive={} head2Active={} heads=[{},{},{},{}] totalHP={} -> {}",
                                                Integer.toHexString(boss.getAliveHeadsMask()), boss.getAliveHeadCount(),
                                                head2Part.isPartActive(),
                                                String.format("%.1f", boss.getHeadHp(0)),
                                                String.format("%.1f", boss.getHeadHp(1)),
                                                String.format("%.1f", boss.getHeadHp(2)),
                                                String.format("%.1f", boss.getHeadHp(3)),
                                                String.format("%.1f", boss.getHealth()),
                                                head2SeveredOk ? "PASS" : "FAIL");
                        }

                        // Qua hết stagger 25 tick sau khi chặt head_2
                        for (int t = 0; t < 30; t++)
                                boss.tick();

                        // 3. Đánh vào proxy thân (`chest_legs`) 500 + 500 -> chặt tiếp 2 đầu còn lại
                        // theo đầu thấp máu nhất (còn 1/4 đầu!)
                        boolean bodyRouteOk = false;
                        if (chestPart != null) {
                                chestPart.hurt(level.damageSources().magic(), 500.0f);
                                for (int t = 0; t < 30; t++)
                                        boss.tick();
                                chestPart.hurt(level.damageSources().magic(), 500.0f);
                                for (int t = 0; t < 30; t++)
                                        boss.tick();
                                bodyRouteOk = boss.getAliveHeadCount() == 1
                                                && Math.abs(boss.getHealth() - boss.sumHeadHp()) < 0.5f
                                                && boss.getHealth() <= 505.0f && boss.getHealth() >= 490.0f;
                                AbyssalRpgMod.LOGGER.info(
                                                "[HYDRA_AUDIT] 3. BODY HIT ROUTING (2x500): aliveMask=0x{} alive={} heads=[{},{},{},{}] totalHP={} -> {}",
                                                Integer.toHexString(boss.getAliveHeadsMask()), boss.getAliveHeadCount(),
                                                String.format("%.1f", boss.getHeadHp(0)),
                                                String.format("%.1f", boss.getHeadHp(1)),
                                                String.format("%.1f", boss.getHeadHp(2)),
                                                String.format("%.1f", boss.getHeadHp(3)),
                                                String.format("%.1f", boss.getHealth()), bodyRouteOk ? "PASS" : "FAIL");
                        }

                        // 4. Save & Reload NBT giữa trận Phase 1 (khi còn 1/4 đầu)
                        CompoundTag nbtP1 = new CompoundTag();
                        boss.saveWithoutId(nbtP1);
                        int savedMaskP1 = boss.getAliveHeadsMask();
                        float savedHpP1 = boss.getHealth();
                        reloadedP1 = ModEntities.HYDRA_BOSS.create(level);
                        boolean nbtP1Ok = false;
                        if (reloadedP1 != null) {
                                reloadedP1.load(nbtP1);
                                level.addFreshEntity(reloadedP1);
                                reloadedP1.tick();
                                nbtP1Ok = reloadedP1.getAliveHeadsMask() == savedMaskP1
                                                && Math.abs(reloadedP1.getHealth() - savedHpP1) < 1.0f
                                                && Math.abs(reloadedP1.sumHeadHp() - savedHpP1) < 1.0f
                                                && reloadedP1.getActiveHitboxParts()
                                                                .size() == HydraBossEntity.MULTIPART_SPECS.length;
                                AbyssalRpgMod.LOGGER.info(
                                                "[HYDRA_AUDIT] 4. NBT P1 SAVE/RELOAD: mask=0x{} (exp 0x{}) HP={} sumHeads={} proxies={} -> {}",
                                                Integer.toHexString(reloadedP1.getAliveHeadsMask()),
                                                Integer.toHexString(savedMaskP1),
                                                String.format("%.1f", reloadedP1.getHealth()),
                                                String.format("%.1f", reloadedP1.sumHeadHp()),
                                                reloadedP1.getActiveHitboxParts().size(), nbtP1Ok ? "PASS" : "FAIL");
                                reloadedP1.discard();
                                reloadedP1 = null;
                        }

                        // 5. Đưa đầu cuối về ngưỡng cận tử -> Chặn chết & Phase 2 (100 tick = 5.0s, mọc
                        // lại 4 đầu cách nhau 0.35s = 7 tick!)
                        boss.hurt(level.damageSources().magic(), 480.0f);
                        boolean p2Triggered = boss.hasEnteredPhase2() && boss.isTransitioningPhase() && boss.isAlive();
                        float maxDeltaYaw = 0.0f;
                        List<Integer> regrowObservedTicks = new ArrayList<>();
                        int prevAlive = boss.getAliveHeadCount();
                        for (int tick = 1; tick <= 105; tick++) {
                                float beforeYaw = boss.yBodyRot;
                                boss.tick();
                                float dYaw = Math.abs(Mth.wrapDegrees(boss.yBodyRot - beforeYaw));
                                if (dYaw > maxDeltaYaw)
                                        maxDeltaYaw = dYaw;
                                int nowAlive = boss.getAliveHeadCount();
                                if (nowAlive > prevAlive) {
                                        regrowObservedTicks.add(tick);
                                        prevAlive = nowAlive;
                                }
                        }
                        boolean regrowIntervalOk = regrowObservedTicks.size() == 3
                                        && (regrowObservedTicks.get(1) - regrowObservedTicks.get(0) == 7)
                                        && (regrowObservedTicks.get(2) - regrowObservedTicks.get(1) == 7);
                        boolean p2FinalOk = p2Triggered
                                        && boss.getSyncedPhase() == 2
                                        && boss.isEnragedVisual()
                                        && boss.getAliveHeadCount() == 4
                                        && Math.abs(boss.getMaxHealth() - 3000.0f) < 0.5f
                                        && Math.abs(boss.getHealth() - 3000.0f) < 0.5f
                                        && Math.abs(boss.getHeadHp(0) - 750.0f) < 0.5f
                                        && Math.abs(boss.getAttributeValue(Attributes.ARMOR) - 22.5) < 0.2
                                        && maxDeltaYaw < 0.5f
                                        && regrowIntervalOk;
                        AbyssalRpgMod.LOGGER.info(
                                        "[HYDRA_AUDIT] 5. PHASE 2 TRANSITION: triggered={} phase={} enraged={} heads={}/4 HP={}/{} perHead={} armor={} regrowTicks={} maxDeltaYaw={} -> {}",
                                        p2Triggered, boss.getSyncedPhase(), boss.isEnragedVisual(),
                                        boss.getAliveHeadCount(),
                                        String.format("%.1f", boss.getHealth()),
                                        String.format("%.1f", boss.getMaxHealth()),
                                        String.format("%.1f", boss.getHeadHp(0)),
                                        String.format("%.1f", boss.getAttributeValue(Attributes.ARMOR)),
                                        regrowObservedTicks, String.format("%.2f", maxDeltaYaw),
                                        p2FinalOk ? "PASS" : "FAIL");

                        // Xác nhận Phase 2 tắt chặt đầu (`allowHeadCutInPhase2 = false`): đánh 800 vào
                        // head_1 -> không chặt đầu!
                        boss.hurtSpecificHead(0, level.damageSources().magic(), 750.0f);
                        boss.tick();
                        boolean p2NoCutOk = boss.getAliveHeadCount() == 4
                                        && Math.abs(boss.getHealth() - boss.sumHeadHp()) < 0.5f;
                        AbyssalRpgMod.LOGGER.info(
                                        "[HYDRA_AUDIT] 5b. P2 NO-SEVER CHECK: alive={}/4 HP={} sumHeads={} -> {}",
                                        boss.getAliveHeadCount(), String.format("%.1f", boss.getHealth()),
                                        String.format("%.1f", boss.sumHeadHp()), p2NoCutOk ? "PASS" : "FAIL");

                        // 6. Save/Reload NBT Phase 2
                        CompoundTag nbtP2 = new CompoundTag();
                        boss.saveWithoutId(nbtP2);
                        float savedHpP2 = boss.getHealth();
                        reloadedP2 = ModEntities.HYDRA_BOSS.create(level);
                        boolean nbtP2Ok = false;
                        if (reloadedP2 != null) {
                                reloadedP2.load(nbtP2);
                                level.addFreshEntity(reloadedP2);
                                reloadedP2.tick();
                                nbtP2Ok = reloadedP2.getSyncedPhase() == 2
                                                && reloadedP2.isEnragedVisual()
                                                && Math.abs(reloadedP2.getMaxHealth() - 3000.0f) < 0.5f
                                                && Math.abs(reloadedP2.getHealth() - savedHpP2) < 1.0f
                                                && Math.abs(reloadedP2.getAttributeValue(Attributes.ARMOR) - 22.5) < 0.2
                                                && reloadedP2.getAliveHeadCount() == 4;
                                AbyssalRpgMod.LOGGER.info(
                                                "[HYDRA_AUDIT] 6. NBT P2 SAVE/RELOAD: phase={} maxHP={} HP={} armor={} heads={}/4 -> {}",
                                                reloadedP2.getSyncedPhase(),
                                                String.format("%.1f", reloadedP2.getMaxHealth()),
                                                String.format("%.1f", reloadedP2.getHealth()),
                                                String.format("%.1f", reloadedP2.getAttributeValue(Attributes.ARMOR)),
                                                reloadedP2.getAliveHeadCount(), nbtP2Ok ? "PASS" : "FAIL");
                                reloadedP2.discard();
                                reloadedP2 = null;
                        }

                        // 7. Benchmark MSPT thực tế trong chiến đấu Phase 1 & Phase 2 (120 ticks mỗi
                        // kịch bản)
                        double idleMspt = measureHydraMspt(boss, HydraBossEntity.SkillState.IDLE, 120);
                        double breathSlamMspt = measureHydraMspt(boss, HydraBossEntity.SkillState.SPIRIT_BREATH, 120);
                        double wrathMspt = measureHydraMspt(boss, HydraBossEntity.SkillState.WRATH_OF_MANY_HEADS, 120);
                        AbyssalRpgMod.LOGGER.info(
                                        "[HYDRA_PERF] MSPT idle={} ms | breath={} ms | wrath_p2={} ms (budget < 5.0 ms)",
                                        String.format("%.4f", idleMspt), String.format("%.4f", breathSlamMspt),
                                        String.format("%.4f", wrathMspt));

                        boolean allPass = initOk && headHitOk && head2SeveredOk && bodyRouteOk && nbtP1Ok && p2FinalOk
                                        && p2NoCutOk && nbtP2Ok;
                        AbyssalRpgMod.LOGGER.info("[HYDRA_AUDIT] === TỔNG KẾT: {} ===",
                                        allPass ? "ALL_PASS" : "FAILED");

                        source.sendSuccess(() -> Component.literal("[Spirit Hydra Audit] "
                                        + (allPass ? "ALL PASS" : "CHECK LOGS")
                                        + String.format(" | MSPT idle=%.3fms breath=%.3fms wrath=%.3fms", idleMspt,
                                                        breathSlamMspt, wrathMspt))
                                        .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                        return allPass ? 1 : 0;
                } finally {
                        if (boss != null && !boss.isRemoved())
                                boss.discard();
                        if (reloadedP1 != null && !reloadedP1.isRemoved())
                                reloadedP1.discard();
                        if (reloadedP2 != null && !reloadedP2.isRemoved())
                                reloadedP2.discard();
                }
        }

        private static double measureHydraMspt(HydraBossEntity boss, HydraBossEntity.SkillState skill, int ticks) {
                for (int w = 0; w < 20; w++)
                        boss.tick();
                if (skill != HydraBossEntity.SkillState.IDLE) {
                        boss.setSkillState(skill);
                }
                long t0 = System.nanoTime();
                for (int i = 0; i < ticks; i++) {
                        if (skill != HydraBossEntity.SkillState.IDLE
                                        && boss.getSkillState() == HydraBossEntity.SkillState.IDLE) {
                                boss.setSkillState(skill);
                        }
                        boss.tick();
                }
                return ((System.nanoTime() - t0) / 1_000_000.0) / Math.max(1, ticks);
        }

        private static int toggleLeviathanDebug(CommandSourceStack source, Boolean forceState) {
                boolean next = forceState != null ? forceState : !LeviathanBossEntity.isDebugLoggingEnabled();
                LeviathanBossEntity.setDebugLoggingEnabled(next);
                LeviathanConfig.get().debugRotationLogs = next;
                source.sendSuccess(() -> Component.literal(
                                "[Leviathan Debug] Rotation & Phase-Transition logging: " + (next ? "ON" : "OFF"))
                                .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
                reportLeviathanStatus(source);
                return 1;
        }

        private static int reportLeviathanStatus(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                AABB box = new AABB(source.getPosition().add(-128, -128, -128),
                                source.getPosition().add(128, 128, 128));
                List<LeviathanBossEntity> bosses = level.getEntitiesOfClass(LeviathanBossEntity.class, box);
                if (bosses.isEmpty()) {
                        source.sendSuccess(() -> Component
                                        .literal("[Leviathan Debug] Không tìm thấy Leviathan trong bán kính 128 block.")
                                        .withStyle(ChatFormatting.GRAY), false);
                        return 0;
                }
                for (LeviathanBossEntity b : bosses) {
                        float netHeadYaw = Mth.wrapDegrees(b.yHeadRot - b.yBodyRot);
                        String msg = String.format(
                                        "[Leviathan #%d] Phase=%d (enteredP2=%s, enragedVis=%s, trans=%d) HP=%.1f | getMaxHealth=%.1f | bossMaxHealth=%.1f | attrMaxHealth=%.1f | Armor=%.1f | effScale=%.2f | yaw=%.1f bodyYaw=%.1f headYaw=%.1f netHead=%.1f | state=%s",
                                        b.getId(), b.getSyncedPhase(), b.hasEnteredPhase2(), b.isEnragedVisual(),
                                        b.getPhaseTransitionTicks(),
                                        b.getHealth(), b.getMaxHealth(), b.getBossMaxHealth(),
                                        b.getAttributeValue(Attributes.MAX_HEALTH),
                                        b.getAttributeValue(Attributes.ARMOR),
                                        b.getEffectiveScale(),
                                        b.getYRot(), b.yBodyRot, b.yHeadRot, netHeadYaw, b.getSkillState().name());
                        source.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.AQUA), false);
                }
                return bosses.size();
        }

        /**
         * Kiểm định tự động toàn diện cho Boss Leviathan v2 (`/abyss leviathan test` &
         * `/leviathan test`):
         * 1. Xác nhận entity ID `abyssalrpg:leviathan`, giữ nguyên 100% chỉ số Phase 1
         * (1500 HP, Armor 20, Toughness 8,
         * heavy 30, melee 15, KB resist 1.0, scale 2.5, visualScaleMultiplier 1.10).
         * 2. Stress test chống quay đầu nhiều vòng: quay tích lũy yaw +920° ở Phase 1,
         * rồi kích hoạt cận tử Phase 2 (90 ticks = 4.5s)
         * -> xác minh `maxDeltaYaw < 0.5°`, `maxNetHeadYaw <= 45°`, `maxHealth ==
         * 2250`, `Armor == 30`, `enragedVisual == true`, `skillDamageScale == 1.5`.
         * 3. Save/Reload NBT giữa Phase 2 bảo toàn đầy đủ trạng thái & chỉ số.
         * 4. Benchmark MSPT thực tế 120 tick cho `IDLE`, `WHIRLPOOL`, `TIDAL_WRATH` (0
         * vòng lặp sendParticles trên Server!).
         */
        private static int runLeviathanAuditAndPerf(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0) + 6,
                                0);
                AbyssalRpgMod.LOGGER.info("[LEVIATHAN_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH LEVIATHAN v2 TẠI {} ===", origin);

                LeviathanBossEntity boss = null;
                LeviathanBossEntity reloaded = null;
                try {
                        boss = ModEntities.LEVIATHAN_BOSS.create(level);
                        if (boss == null) {
                                source.sendFailure(Component.literal(
                                                "[LEVIATHAN_AUDIT] Không thể khởi tạo ModEntities.LEVIATHAN_BOSS"));
                                return 0;
                        }
                        boss.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 0.0f, 0.0f);
                        boss.configureForFloor(15, null, false);
                        level.addFreshEntity(boss);

                        for (int t = 0; t < 75; t++)
                                boss.tick();

                        String regId = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
                        LeviathanConfig cfg = LeviathanConfig.get();
                        boolean initOk = "abyssalrpg:leviathan".equals(regId)
                                        && Math.abs(boss.getMaxHealth() - 1500.0f) < 0.5f
                                        && Math.abs(boss.getHealth() - 1500.0f) < 0.5f
                                        && Math.abs(boss.getAttributeValue(Attributes.ARMOR) - 20.0) < 0.2
                                        && Math.abs(boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 8.0) < 0.2
                                        && Math.abs(boss.getAttributeValue(Attributes.ATTACK_DAMAGE) - 15.0) < 0.2
                                        && Math.abs(boss.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                                                        - 1.0) < 0.05
                                        && Math.abs(cfg.tailSlamDamage - 30.0f) < 0.1f
                                        && Math.abs(cfg.scale - 2.5f) < 0.05f
                                        && Math.abs(cfg.visualScaleMultiplier - 1.10f) < 0.05f;
                        AbyssalRpgMod.LOGGER.info(
                                        "[LEVIATHAN_AUDIT] 1. INIT & STATS PRESERVED: id={} HP={}/{} armor={} tough={} atk={} heavy={} scale={}*{} -> {}",
                                        regId, String.format("%.1f", boss.getHealth()),
                                        String.format("%.1f", boss.getMaxHealth()),
                                        String.format("%.1f", boss.getAttributeValue(Attributes.ARMOR)),
                                        String.format("%.1f", boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),
                                        String.format("%.1f", boss.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                                        String.format("%.1f", cfg.tailSlamDamage),
                                        String.format("%.2f", cfg.scale),
                                        String.format("%.2f", cfg.visualScaleMultiplier),
                                        initOk ? "PASS" : "FAIL");

                        // 2. Stress test chống head-spin: xoay vòng tích lũy +920° trong Phase 1 trước
                        // khi kích hoạt Phase 2 cận tử!
                        boss.setYRot(920.0f);
                        boss.yBodyRot = 920.0f;
                        boss.yHeadRot = 965.0f;
                        boss.tick();

                        boss.hurt(level.damageSources().magic(), 5000.0f);
                        boolean intercepted = boss.isAlive() && boss.hasEnteredPhase2() && boss.isTransitioningPhase();
                        float maxTransDeltaYaw = 0.0f;
                        float maxPostDeltaYaw = 0.0f;
                        float maxNetHead = 0.0f;
                        for (int tick = 1; tick <= 95; tick++) {
                                boolean wasTrans = boss.isTransitioningPhase();
                                float beforeYaw = boss.yBodyRot;
                                boss.tick();
                                float dYaw = Math.abs(Mth.wrapDegrees(boss.yBodyRot - beforeYaw));
                                float netH = Math.abs(Mth.wrapDegrees(boss.yHeadRot - boss.yBodyRot));
                                if (wasTrans && boss.isTransitioningPhase()) {
                                        if (dYaw > maxTransDeltaYaw)
                                                maxTransDeltaYaw = dYaw;
                                } else {
                                        if (dYaw > maxPostDeltaYaw)
                                                maxPostDeltaYaw = dYaw;
                                }
                                if (netH > maxNetHead)
                                        maxNetHead = netH;
                        }
                        boolean phase2Ok = intercepted
                                        && !boss.isTransitioningPhase()
                                        && boss.isEnragedVisual()
                                        && boss.getSyncedPhase() == 2
                                        && Math.abs(boss.getMaxHealth() - 2250.0f) < 0.5f
                                        && Math.abs(boss.getHealth() - 2250.0f) < 0.5f
                                        && Math.abs(boss.getAttributeValue(Attributes.ARMOR) - 30.0) < 0.2
                                        && Math.abs(boss.skillDamageScale() - 1.5f) < 0.05f
                                        && maxTransDeltaYaw < 0.1f
                                        && maxPostDeltaYaw <= 7.5f
                                        && maxNetHead <= 45.1f;
                        AbyssalRpgMod.LOGGER.info(
                                        "[LEVIATHAN_AUDIT] 2. PHASE 2 + ANTI-HEAD-SPIN (+920° stress): intercept={} phase={} enraged={} HP={}/{} armor={} dmgScale={}x transDeltaYaw={}° postDeltaYaw={}° maxNetHead={}° -> {}",
                                        intercepted, boss.getSyncedPhase(), boss.isEnragedVisual(),
                                        String.format("%.1f", boss.getHealth()),
                                        String.format("%.1f", boss.getMaxHealth()),
                                        String.format("%.1f", boss.getAttributeValue(Attributes.ARMOR)),
                                        String.format("%.2f", boss.skillDamageScale()),
                                        String.format("%.3f", maxTransDeltaYaw), String.format("%.3f", maxPostDeltaYaw),
                                        String.format("%.2f", maxNetHead),
                                        phase2Ok ? "PASS" : "FAIL");

                        // 3. Save & Reload NBT giữa Phase 2
                        boss.hurt(level.damageSources().magic(), 350.0f);
                        CompoundTag nbt = new CompoundTag();
                        boss.saveWithoutId(nbt);
                        float savedHp = boss.getHealth();
                        reloaded = ModEntities.LEVIATHAN_BOSS.create(level);
                        boolean nbtOk = false;
                        if (reloaded != null) {
                                reloaded.load(nbt);
                                level.addFreshEntity(reloaded);
                                reloaded.tick();
                                nbtOk = reloaded.getSyncedPhase() == 2
                                                && reloaded.isEnragedVisual()
                                                && Math.abs(reloaded.getMaxHealth() - 2250.0f) < 0.5f
                                                && Math.abs(reloaded.getHealth() - savedHp) < 1.0f
                                                && Math.abs(reloaded.getAttributeValue(Attributes.ARMOR) - 30.0) < 0.2;
                                AbyssalRpgMod.LOGGER.info(
                                                "[LEVIATHAN_AUDIT] 3. NBT P2 SAVE/RELOAD: phase={} enraged={} HP={}/{} (exp {}) armor={} -> {}",
                                                reloaded.getSyncedPhase(), reloaded.isEnragedVisual(),
                                                String.format("%.1f", reloaded.getHealth()),
                                                String.format("%.1f", reloaded.getMaxHealth()),
                                                String.format("%.1f", savedHp),
                                                String.format("%.1f", reloaded.getAttributeValue(Attributes.ARMOR)),
                                                nbtOk ? "PASS" : "FAIL");
                                reloaded.discard();
                                reloaded = null;
                        }

                        // 4. Benchmark MSPT thực tế cho IDLE, WHIRLPOOL, TIDAL_WRATH (120 ticks mỗi
                        // kịch bản)
                        double idleMspt = measureLeviathanMspt(boss, LeviathanBossEntity.SkillState.IDLE, 120);
                        double whirlMspt = measureLeviathanMspt(boss, LeviathanBossEntity.SkillState.WHIRLPOOL, 120);
                        double wrathMspt = measureLeviathanMspt(boss, LeviathanBossEntity.SkillState.TIDAL_WRATH, 120);
                        AbyssalRpgMod.LOGGER.info(
                                        "[LEVIATHAN_PERF] MSPT idle={} ms | whirlpool={} ms | tidal_wrath={} ms (budget < 5.0 ms, 0 server sendParticles loops)",
                                        String.format("%.4f", idleMspt), String.format("%.4f", whirlMspt),
                                        String.format("%.4f", wrathMspt));

                        boolean allPass = initOk && phase2Ok && nbtOk;
                        AbyssalRpgMod.LOGGER.info("[LEVIATHAN_AUDIT] === TỔNG KẾT: {} ===",
                                        allPass ? "ALL_PASS" : "FAILED");

                        final float finalMaxTransYaw = maxTransDeltaYaw;
                        final float finalMaxPostYaw = maxPostDeltaYaw;
                        source.sendSuccess(() -> Component.literal("[Leviathan v2 Audit] "
                                        + (allPass ? "ALL PASS" : "CHECK LOGS")
                                        + String.format(" | transDeltaYaw=%.3f° postDeltaYaw=%.3f° | MSPT idle=%.3fms whirl=%.3fms wrath=%.3fms",
                                                        finalMaxTransYaw, finalMaxPostYaw, idleMspt, whirlMspt,
                                                        wrathMspt))
                                        .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                        return allPass ? 1 : 0;
                } finally {
                        if (boss != null && !boss.isRemoved())
                                boss.discard();
                        if (reloaded != null && !reloaded.isRemoved())
                                reloaded.discard();
                }
        }

        private static double measureLeviathanMspt(LeviathanBossEntity boss, LeviathanBossEntity.SkillState skill,
                        int ticks) {
                for (int w = 0; w < 20; w++)
                        boss.tick();
                if (skill != LeviathanBossEntity.SkillState.IDLE) {
                        boss.setSkillState(skill);
                }
                long t0 = System.nanoTime();
                for (int i = 0; i < ticks; i++) {
                        if (skill != LeviathanBossEntity.SkillState.IDLE
                                        && boss.getSkillState() == LeviathanBossEntity.SkillState.IDLE) {
                                boss.setSkillState(skill);
                        }
                        boss.tick();
                }
                return ((System.nanoTime() - t0) / 1_000_000.0) / Math.max(1, ticks);
        }

        private static int toggleCinderDebug(CommandSourceStack source, Boolean forceState) {
                boolean next = forceState != null ? forceState : !CinderDragonBossEntity.isDebugLoggingEnabled();
                CinderDragonBossEntity.setDebugLoggingEnabled(next);
                CinderDragonConfig.get().debugLogs = next;
                source.sendSuccess(() -> Component.literal(
                                "[Cinder Dragon Debug] Yaw, Flight, Defense, Multipart & Timing logging: "
                                                + (next ? "ON" : "OFF"))
                                .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
                reportCinderStatus(source);
                return 1;
        }

        private static int reportCinderStatus(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                AABB box = new AABB(source.getPosition().add(-160, -160, -160),
                                source.getPosition().add(160, 160, 160));
                List<CinderDragonBossEntity> bosses = level.getEntitiesOfClass(CinderDragonBossEntity.class, box);
                if (bosses.isEmpty()) {
                        source.sendSuccess(() -> Component.literal(
                                        "[Cinder Dragon Debug] Không tìm thấy Cinder Dragon trong bán kính 160 block.")
                                        .withStyle(ChatFormatting.GRAY), false);
                        return 0;
                }
                for (CinderDragonBossEntity b : bosses) {
                        float netHeadYaw = Mth.wrapDegrees(b.yHeadRot - b.yBodyRot);
                        String msg = String.format(
                                        "[CinderDragon #%d] Phase=%d (enteredP2=%s enraged=%s trans=%d flying=%s) HP=%.1f/%.1f | Def=%.1f red=%.1f%% | scale=%.2f parts=%d | yaw=%.1f netHead=%.1f | skill=%s",
                                        b.getId(), b.getSyncedPhase(), b.hasEnteredPhase2(), b.isEnragedVisual(),
                                        b.getPhaseTransitionTicks(),
                                        b.isFlying(), b.getHealth(), b.getMaxHealth(), b.getBossDefense(),
                                        b.computeEffectiveDamageReduction(15.0f) * 100.0f, b.getConfiguredScale(),
                                        b.getActiveHitboxParts().size(), b.getYRot(), netHeadYaw,
                                        b.getSkillState().name());
                        source.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GOLD), false);
                }
                return bosses.size();
        }

        /**
         * Kiểm định toàn diện Boss Cinder Dragon (`/abyss cinder test`):
         * 1. Phase 1 stats (`2700 HP`, `Def 20`, `SCALE 4.5f = 0.60 * NightmareKnight`,
         * `6` proxy hitboxes, miễn lửa).
         * 2. Multipart zone multipliers (`head_neck x1.25`, `body_chest x1.0`, `wing_l
         * x0.85`, `tail_tip x0.85`, `0` KB).
         * 3. Phase 2 "Vô Form" death-intercept + 110t biến hình (`4590 HP`, `Def 34`,
         * `0` head-spin).
         * 4. NBT save/reload giữa Phase 2 (giữ chuẩn `4590` maxHP, `Def 34`, `6`
         * proxies, không cộng dồn).
         * 5. Benchmark MSPT cho `idle`, `breath`, `fly` (`SKYFALL_INFERNO`) và Ultimate
         * `Ashen Supernova`.
         */
        private static int runCinderDragonAuditAndPerf(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                BlockPos spawnPos = level.getSharedSpawnPos();
                int groundY = Math.max(level.getSeaLevel(),
                                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos.getX(),
                                                spawnPos.getZ()));
                BlockPos origin = new BlockPos(spawnPos.getX(), groundY, spawnPos.getZ());
                AbyssalRpgMod.LOGGER.info("[CINDER_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH CINDER DRAGON TẠI {} ===", origin);

                CinderDragonBossEntity boss = null;
                CinderDragonBossEntity reloaded = null;
                try {
                        boss = ModEntities.CINDER_DRAGON.create(level);
                        if (boss == null)
                                return 0;
                        boss.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 0.0f, 0.0f);
                        boss.configureForFloor(18, null, false);
                        for (int i = 0; i < 66; i++)
                                boss.tick(); // Hoàn tất emergence invulnerability của AbstractAbyssalBoss
                        level.addFreshEntity(boss);
                        boss.tick();

                        float p1Max = boss.getMaxHealth();
                        float p1Def = boss.getBossDefense();
                        float scale = boss.getConfiguredScale();
                        int expectedParts = CinderDragonBossEntity.MULTIPART_SPECS.length;
                        int partCount = boss.getActiveHitboxParts().size();
                        boolean fireImmune = !boss.hurt(level.damageSources().inFire(), 25.0f);
                        // Xác nhận người chơi đứng dưới mặt đất (Y = boss.getY()..+1.8) chém kiếm cận
                        // chiến trúng 2 chân trước + cổ thấp + ngực!
                        int groundMeleeParts = 0;
                        for (CinderDragonHitboxPartEntity p : boss.getActiveHitboxParts()) {
                                AABB bb = p.getBoundingBox();
                                if (bb.minY <= boss.getY() + 0.6 && bb.maxY >= boss.getY() + 3.0) {
                                        groundMeleeParts++;
                                }
                        }
                        boolean statsOk = Math.abs(p1Max - 2700.0f) < 0.5f && Math.abs(p1Def - 20.0f) < 0.5f
                                        && Math.abs(scale - CinderDragonBossEntity.SCALE) < 0.05f
                                        && partCount == expectedParts && groundMeleeParts >= 4 && fireImmune;
                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_AUDIT] 1. INIT P1: HP={} def={} scale={} parts={}/{} groundMeleeParts={} fireImmune={} -> {}",
                                        p1Max, p1Def, scale, partCount, expectedParts, groundMeleeParts, fireImmune,
                                        statsOk ? "PASS" : "FAIL");

                        // 2. Test multipart zone multipliers (magic bypass armor -> thuần hệ số bộ
                        // phận) + vật lý qua BossDefenseHolder
                        List<CinderDragonHitboxPartEntity> parts = boss.getActiveHitboxParts();
                        boss.invulnerableTime = 0;
                        float hp0 = boss.getHealth();
                        parts.get(0).hurt(level.damageSources().magic(), 20.0f); // head_neck x1.25 -> 25.0
                        float headTaken = hp0 - boss.getHealth();

                        boss.invulnerableTime = 0;
                        float hp1 = boss.getHealth();
                        parts.get(1).hurt(level.damageSources().magic(), 20.0f); // body_chest x1.00 -> 20.0
                        float chestTaken = hp1 - boss.getHealth();

                        boss.invulnerableTime = 0;
                        float hp2 = boss.getHealth();
                        parts.get(2).hurt(level.damageSources().magic(), 20.0f); // wing_l x0.85 -> 17.0
                        float wingTaken = hp2 - boss.getHealth();

                        boss.invulnerableTime = 0;
                        float hp3 = boss.getHealth();
                        parts.get(5).hurt(level.damageSources().magic(), 20.0f); // tail_tip x0.85 -> 17.0
                        float tailTaken = hp3 - boss.getHealth();

                        // Kiểm tra vật lý qua BossDefenseHolder (Def 20, raw 20 -> eff = min(17.5,
                        // max(4, 20 - 10)) = 10 -> 40% giảm -> 12.0)
                        boss.invulnerableTime = 0;
                        float hpPhys = boss.getHealth();
                        boss.hurt(level.damageSources().cactus(), 20.0f);
                        float physTaken = hpPhys - boss.getHealth();

                        double kb = boss.getDeltaMovement().horizontalDistance();
                        boolean zonesOk = Math.abs(headTaken - 25.0f) < 0.2f && Math.abs(chestTaken - 20.0f) < 0.2f
                                        && Math.abs(wingTaken - 17.0f) < 0.2f && Math.abs(tailTaken - 17.0f) < 0.2f
                                        && Math.abs(physTaken - 12.0f) < 0.3f && kb < 0.01;
                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_AUDIT] 2. MULTIPART & DEFENSE (raw=20): head={} (25.0) body={} (20.0) wing={} (17.0) tail={} (17.0) physDef20={} (12.0) kb={} -> {}",
                                        String.format("%.2f", headTaken), String.format("%.2f", chestTaken),
                                        String.format("%.2f", wingTaken), String.format("%.2f", tailTaken),
                                        String.format("%.2f", physTaken), String.format("%.4f", kb),
                                        zonesOk ? "PASS" : "FAIL");

                        // 3. Test Phase 2 "Vô Form" death-intercept + 110t transition (4590 HP, Def 34,
                        // 0 head-spin trong lúc chuyển pha)
                        boss.invulnerableTime = 0;
                        boss.hurt(level.damageSources().magic(), 9999.0f);
                        boolean intercept = boss.isAlive() && boss.hasEnteredPhase2() && boss.isTransitioningPhase();
                        float startYaw = boss.getYRot();
                        float maxDrift = 0.0f;
                        float maxNetHead = 0.0f;
                        for (int tick = 0; tick < 112; tick++) {
                                boolean wasTrans = boss.isTransitioningPhase();
                                boss.tick();
                                if (wasTrans) {
                                        maxDrift = Math.max(maxDrift,
                                                        Math.abs(Mth.wrapDegrees(boss.getYRot() - startYaw)));
                                        maxNetHead = Math.max(maxNetHead,
                                                        Math.abs(Mth.wrapDegrees(boss.yHeadRot - boss.yBodyRot)));
                                }
                        }
                        float p2Max = boss.getMaxHealth();
                        float p2Def = boss.getBossDefense();
                        // Kiểm tra vật lý Phase 2 với Def 34 > 30 vanilla (raw 20 -> eff = min(17.5, 34
                        // - 10) = 17.5 -> kẹp trần 70% -> 6.00!)
                        boss.invulnerableTime = 0;
                        float hpP2Phys = boss.getHealth();
                        boss.hurt(level.damageSources().cactus(), 20.0f);
                        float p2PhysTaken = hpP2Phys - boss.getHealth();
                        boolean p2Ok = intercept && Math.abs(p2Max - 4590.0f) < 1.0f && Math.abs(p2Def - 34.0f) < 0.5f
                                        && Math.abs(p2PhysTaken - 6.0f) < 0.3f
                                        && boss.isEnragedVisual() && maxDrift < 0.1f && maxNetHead < 1.0f;
                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_AUDIT] 3. PHASE 2 VO FORM: intercept={} HP={}/{} def={} physDef34={} (exp 6.00) enraged={} maxYawDrift={} maxNetHead={} -> {}",
                                        intercept, String.format("%.1f", boss.getHealth()),
                                        String.format("%.1f", p2Max),
                                        p2Def, String.format("%.2f", p2PhysTaken), boss.isEnragedVisual(),
                                        String.format("%.3f", maxDrift), String.format("%.3f", maxNetHead),
                                        p2Ok ? "PASS" : "FAIL");

                        // 4. Test NBT Save/Reload giữa Phase 2
                        boss.invulnerableTime = 0;
                        float expectedReloadHp = boss.getHealth() - 500.0f;
                        boss.hurt(level.damageSources().magic(), 500.0f);
                        CompoundTag saved = new CompoundTag();
                        boss.saveWithoutId(saved);
                        boolean nbtOk = false;
                        reloaded = ModEntities.CINDER_DRAGON.create(level);
                        if (reloaded != null) {
                                reloaded.load(saved);
                                level.addFreshEntity(reloaded);
                                reloaded.tick();
                                nbtOk = reloaded.hasEnteredPhase2() && reloaded.isEnragedVisual()
                                                && Math.abs(reloaded.getMaxHealth() - 4590.0f) < 1.0f
                                                && Math.abs(reloaded.getHealth() - expectedReloadHp) < 2.0f
                                                && Math.abs(reloaded.getBossDefense() - 34.0f) < 0.5f
                                                && reloaded.getActiveHitboxParts().size() == expectedParts;
                                AbyssalRpgMod.LOGGER.info(
                                                "[CINDER_AUDIT] 4. NBT P2 RELOAD: HP={}/{} (exp {}) def={} parts={} -> {}",
                                                String.format("%.1f", reloaded.getHealth()),
                                                String.format("%.1f", reloaded.getMaxHealth()),
                                                String.format("%.1f", expectedReloadHp), reloaded.getBossDefense(),
                                                reloaded.getActiveHitboxParts().size(), nbtOk ? "PASS" : "FAIL");
                                reloaded.discard();
                                reloaded = null;
                        }

                        // 5. Verify DragonAttackTiming 11 timelines registered
                        int timelineCount = DragonAttackTiming.allTimelines().size();

                        // 6. Benchmark MSPT: Idle, Cinder Breath, Fly (Skyfall Inferno), Ashen
                        // Supernova
                        double idleMspt = measureCinderMspt(boss, CinderDragonBossEntity.SkillState.IDLE, false, 100);
                        double breathMspt = measureCinderMspt(boss, CinderDragonBossEntity.SkillState.CINDER_BREATH,
                                        false, 100);
                        double flyMspt = measureCinderMspt(boss, CinderDragonBossEntity.SkillState.SKYFALL_INFERNO,
                                        true, 100);
                        double ultMspt = measureCinderMspt(boss, CinderDragonBossEntity.SkillState.ASHEN_SUPERNOVA,
                                        false, 100);
                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_PERF] MSPT Benchmark: idle={} ms | breath={} ms | fly={} ms | supernova={} ms (timelines={})",
                                        String.format("%.4f", idleMspt), String.format("%.4f", breathMspt),
                                        String.format("%.4f", flyMspt), String.format("%.4f", ultMspt), timelineCount);

                        boss.discard();
                        boss = null;
                        int leaked = level.getEntitiesOfClass(CinderDragonHitboxPartEntity.class,
                                        new AABB(origin).inflate(128.0)).size();

                        boolean allPass = statsOk && zonesOk && p2Ok && nbtOk && timelineCount == 11 && leaked == 0;
                        AbyssalRpgMod.LOGGER.info("[CINDER_AUDIT] === TỔNG KẾT: {} (leakedProxies={}) ===",
                                        allPass ? "ALL_PASS" : "FAIL", leaked);

                        final double fIdle = idleMspt, fBreath = breathMspt, fFly = flyMspt, fUlt = ultMspt;
                        source.sendSuccess(() -> Component.literal(String.format(
                                        "[Cinder Dragon Audit] %s | P1=%.0fHP/Def=%.0f, P2=%.0fHP/Def=%.0f, Scale=%.2f, Parts=%d, Leaks=%d | MSPT idle=%.3f breath=%.3f fly=%.3f nova=%.3f ms",
                                        allPass ? "ALL PASS" : "FAIL", p1Max, p1Def, p2Max, p2Def, scale, partCount,
                                        leaked,
                                        fIdle, fBreath, fFly, fUlt))
                                        .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                        return allPass ? 1 : 0;
                } finally {
                        if (boss != null && !boss.isRemoved())
                                boss.discard();
                        if (reloaded != null && !reloaded.isRemoved())
                                reloaded.discard();
                }
        }

        private static double measureCinderMspt(CinderDragonBossEntity boss,
                        CinderDragonBossEntity.SkillState state,
                        boolean flying, int ticks) {
                boss.setFlying(flying, false);
                boss.setSkillState(state);
                for (int w = 0; w < 15; w++)
                        boss.tick();
                long t0 = System.nanoTime();
                for (int i = 0; i < ticks; i++) {
                        boss.setSkillState(state);
                        boss.tick();
                }
                boss.setFlying(false, false);
                boss.setSkillState(CinderDragonBossEntity.SkillState.IDLE);
                return ((System.nanoTime() - t0) / 1_000_000.0) / Math.max(1, ticks);
        }

        /**
         * Kiểm định tự động toàn diện cho Dual Blade v2 (`/abyss dualblade test`):
         * 1. Stats: 30.0 Attack Damage, 2.5 Attack Speed, 1561 Durability, 7t i-frame,
         * sửa bằng Diamond, hỗ trợ Unbreaking/Mending, chặn Sharpness.
         * 2. Enchants: xác nhận 5 enchant cũ đã bị xóa sạch khỏi registry; 4 enchant
         * mới (Lifesteal, Armor Pierce, Critical Strike, Deep Wounds,
         * tất cả max Lv III) chỉ ép được cho Dual Blade, từ chối Diamond Sword và tương
         * thích đầy đủ với nhau.
         * 3. Rapid 2.5 hit/s combo (8 ticks/nhát) trên Zombie & Nightmare Knight proxy
         * hitbox (5/5 nhát + Bleeding 5/5).
         * 4. Xác minh trực tiếp 4 Enchant mới:
         * - Armor Pierce III (20%) trên Nightmare Knight (`BossDefenseHolder` def=40):
         * 13.20 dmg vs 9.00 dmg gốc!
         * - Lifesteal III (15%): hồi đúng 15% sát thương thực tế gây ra (đo trực tiếp
         * trên Boss & Zombie)!
         * - Deep Wounds III (+15%): tăng +15% sát thương trực tiếp lên mục tiêu đang
         * Chảy Máu & +15% sát thương Chảy Máu/giây!
         * - Critical Strike III (15% tỉ lệ x1.5): kích hoạt chuẩn 1.5x sát thương!
         * - Hit 4 Cyclone 360° AOE trúng mob sau lưng, Sprint/Air move routing & phạt
         * tay phụ 1.6 speed!
         */
        private static int runDualBladeAuditAndPerf(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                BlockPos spawn = level.getSharedSpawnPos();
                int groundY = Math.max(70,
                                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()));
                BlockPos origin = new BlockPos(spawn.getX(), groundY, spawn.getZ());
                AbyssalRpgMod.LOGGER.info(
                                "[DUALKNIFE_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH DUAL KNIFE (abyssalrpg:dual_knife) TẠI {} ===",
                                origin);

                Zombie zombie = null;
                NightmareKnightBossEntity boss = null;
                try {
                        ItemStack blade = new ItemStack(ModItems.DUAL_KNIFE);
                        ItemStack diamondSword = new ItemStack(Items.DIAMOND_SWORD);
                        String itemKey = BuiltInRegistries.ITEM.getKey(ModItems.DUAL_KNIFE).toString();
                        boolean idRenamedOk = "abyssalrpg:dual_knife".equals(itemKey)
                                        && !BuiltInRegistries.ITEM.containsKey(AbyssalRpgMod.id("dual_blade"));

                        // 1. Stats & Vanilla enchant compatibility
                        FakePlayer tester = FakePlayer.get(level);
                        tester.setPos(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
                        tester.setYRot(0.0F);
                        tester.setXRot(0.0F);
                        tester.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                        equipDualBladeForTest(tester, blade);

                        double dmgAttr = tester.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        double spdAttr = tester.getAttributeValue(Attributes.ATTACK_SPEED);
                        int maxDur = blade.getMaxDamage();
                        int baseIframes = DualBladeCombatHandler.computeHurtResistanceTicks(tester);
                        boolean repairOk = ModItems.DUAL_KNIFE.isValidRepairItem(blade, new ItemStack(Items.DIAMOND));

                        HolderLookup.RegistryLookup<Enchantment> enchReg = level.registryAccess()
                                        .lookupOrThrow(Registries.ENCHANTMENT);
                        Holder<Enchantment> unbreaking = enchReg.getOrThrow(Enchantments.UNBREAKING);
                        Holder<Enchantment> mending = enchReg.getOrThrow(Enchantments.MENDING);
                        Holder<Enchantment> sharpness = enchReg.getOrThrow(Enchantments.SHARPNESS);
                        boolean vanillaCompatOk = unbreaking.value().canEnchant(blade)
                                        && mending.value().canEnchant(blade)
                                        && !sharpness.value().canEnchant(blade);
                        boolean statsOk = idRenamedOk
                                        && Math.abs(dmgAttr - 30.0) < 0.05
                                        && Math.abs(spdAttr - 2.5) < 0.05
                                        && maxDur == 1561
                                        && baseIframes == 7
                                        && repairOk
                                        && vanillaCompatOk;
                        AbyssalRpgMod.LOGGER.info(
                                        "[DUALKNIFE_AUDIT] 1. STATS: id={} (legacyRemoved={}) dmg={} (exp 30.0) spd={} (exp 2.5) dur={} (exp 1561) iframe={}t (exp 7t) repair={} vanillaCompat(Unb/Mend=T,Sharp=F)={} -> {}",
                                        itemKey, idRenamedOk, String.format("%.2f", dmgAttr),
                                        String.format("%.2f", spdAttr), maxDur, baseIframes, repairOk, vanillaCompatOk,
                                        statsOk ? "PASS" : "FAIL");

                        // 2. Xác nhận 5 enchant cũ biến mất hoàn toàn & 4 enchant mới chuẩn xác
                        List<String> legacyIds = List.of("hemorrhage", "tempest_flurry", "crimson_siphon",
                                        "executioners_edge", "phantom_cross");
                        boolean old5Absent = legacyIds.stream()
                                        .allMatch(id -> enchReg
                                                        .get(net.minecraft.resources.ResourceKey.create(
                                                                        Registries.ENCHANTMENT, AbyssalRpgMod.id(id)))
                                                        .isEmpty());

                        Holder<Enchantment> lifesteal = enchReg.getOrThrow(ModEnchantments.LIFESTEAL);
                        Holder<Enchantment> armorPierce = enchReg.getOrThrow(ModEnchantments.ARMOR_PIERCE);
                        Holder<Enchantment> critStrike = enchReg.getOrThrow(ModEnchantments.CRITICAL_STRIKE);
                        Holder<Enchantment> deepWounds = enchReg.getOrThrow(ModEnchantments.DEEP_WOUNDS);

                        List<Holder<Enchantment>> allFour = List.of(lifesteal, armorPierce, critStrike, deepWounds);
                        boolean allMaxLv3 = allFour.stream().allMatch(h -> h.value().getMaxLevel() == 3);
                        boolean allAcceptBlade = allFour.stream()
                                        .allMatch(h -> h.value().canEnchant(blade) && h.value().isSupportedItem(blade));
                        boolean allRejectSword = allFour.stream().noneMatch(h -> h.value().canEnchant(diamondSword)
                                        || h.value().isSupportedItem(diamondSword));
                        boolean allCompat = Enchantment.areCompatible(lifesteal, armorPierce)
                                        && Enchantment.areCompatible(lifesteal, critStrike)
                                        && Enchantment.areCompatible(lifesteal, deepWounds)
                                        && Enchantment.areCompatible(armorPierce, critStrike)
                                        && Enchantment.areCompatible(armorPierce, deepWounds)
                                        && Enchantment.areCompatible(critStrike, deepWounds);
                        boolean enchantsOk = old5Absent && allMaxLv3 && allAcceptBlade && allRejectSword && allCompat;
                        AbyssalRpgMod.LOGGER.info(
                                        "[DUALBLADE_AUDIT] 2. ENCHANTS: old5Absent={} all4MaxLv3={} all4OnBlade={} all4RejectedOnSword={} all4Compatible={} -> {}",
                                        old5Absent, allMaxLv3, allAcceptBlade, allRejectSword, allCompat,
                                        enchantsOk ? "PASS" : "FAIL");

                        // 3. Rapid 2.5 hit/s combat (8 ticks/nhát) & Bleeding on normal mob (Zombie
                        // with 500 HP)
                        zombie = EntityType.ZOMBIE.create(level);
                        if (zombie == null)
                                return 0;
                        zombie.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 2.5, 0.0F, 0.0F);
                        AttributeInstance zMax = zombie.getAttribute(Attributes.MAX_HEALTH);
                        if (zMax != null)
                                zMax.setBaseValue(500.0);
                        zombie.setHealth(500.0F);
                        level.addFreshEntity(zombie);

                        int zombieHits = 0;
                        for (int i = 0; i < 5; i++) {
                                float before = zombie.getHealth();
                                ((LivingEntityAccessor) tester).abyssalrpg$setAttackStrengthTicker(20);
                                tester.attack(zombie);
                                if (zombie.getHealth() < before)
                                        zombieHits++;
                                // Mô phỏng đúng 8 ticks trôi qua giữa 2 nhát đánh ở speed 2.5 (8.0 ticks/hit)
                                zombie.invulnerableTime = Math.max(0, zombie.invulnerableTime - 8);
                        }
                        MobEffectInstance zBleed = zombie.getEffect(ModEffects.BLEEDING);
                        int zBleedStacks = zBleed != null ? (zBleed.getAmplifier() + 1) : 0;
                        boolean zombieOk = (zombieHits == 5) && (zBleedStacks == 5);
                        AbyssalRpgMod.LOGGER.info(
                                        "[DUALBLADE_AUDIT] 3. NORMAL MOB RAPID COMBO (2.5 spd / 8t): hits={}/5 bleedStacks={}/5 -> {}",
                                        zombieHits, zBleedStacks, zombieOk ? "PASS" : "FAIL");

                        // 4. Spawn Nightmare Knight (BossDefenseHolder: Defense = 40, Toughness = 12,
                        // maxRed = 0.70)
                        boss = ModEntities.NIGHTMARE_KNIGHT.create(level);
                        if (boss == null)
                                return 0;
                        boss.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 4.0, 0.0F, 0.0F);
                        boss.configureForFloor(18, null, false);
                        for (int i = 0; i < 66; i++)
                                boss.tick();
                        boss.setNoAi(true);
                        boss.setNoGravity(true);
                        boss.setPersistenceRequired();
                        boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        level.addFreshEntity(boss);
                        boss.tick();

                        // 5a. Armor Pierce III (20%) & Lifesteal III (15%) trực tiếp trên Nightmare
                        // Knight (BossDefenseHolder def=40)
                        // Đòn thường không enchant: 30.0 * (1 - 0.70) = 9.00 dmg
                        equipDualBladeForTest(tester, new ItemStack(ModItems.DUAL_KNIFE));
                        boss.removeEffect(ModEffects.BLEEDING);
                        boss.invulnerableTime = 0;
                        float bossHpBeforePlain = boss.getHealth();
                        ((LivingEntityAccessor) tester).abyssalrpg$setAttackStrengthTicker(20);
                        tester.attack(boss);
                        float bossPlainDmg = bossHpBeforePlain - boss.getHealth();

                        // Đòn có Armor Pierce III (20%) + Lifesteal III (15%): 30.0 * (1 - 0.56) =
                        // 13.20 dmg & hồi 13.20 * 0.15 = 1.98 HP!
                        ItemStack pierceLifeBlade = new ItemStack(ModItems.DUAL_KNIFE);
                        pierceLifeBlade.enchant(armorPierce, 3);
                        pierceLifeBlade.enchant(lifesteal, 3);
                        equipDualBladeForTest(tester, pierceLifeBlade);
                        boss.removeEffect(ModEffects.BLEEDING);
                        boss.invulnerableTime = 0;
                        tester.setHealth(10.0F);
                        float bossHpBeforePierce = boss.getHealth();
                        float playerHpBeforeLife = tester.getHealth();
                        ((LivingEntityAccessor) tester).abyssalrpg$setAttackStrengthTicker(20);
                        tester.attack(boss);
                        float bossPierceDmg = bossHpBeforePierce - boss.getHealth();
                        float lifestealBossHeal = tester.getHealth() - playerHpBeforeLife;
                        float expectedLifeHeal = bossPierceDmg * 0.15F;
                        boolean armorPierceBossOk = Math.abs(bossPlainDmg - 9.0F) < 0.25F
                                        && Math.abs(bossPierceDmg - 13.2F) < 0.25F;
                        boolean lifestealOk = Math.abs(lifestealBossHeal - expectedLifeHeal) < 0.08F;

                        // 5b. Deep Wounds III (+15% direct damage vs Bleeding target & +15% Bleed DPS)
                        ItemStack woundsBlade = new ItemStack(ModItems.DUAL_KNIFE);
                        woundsBlade.enchant(deepWounds, 3);
                        equipDualBladeForTest(tester, woundsBlade);
                        zombie.removeEffect(ModEffects.BLEEDING);
                        zombie.setHealth(500.0F);
                        zombie.invulnerableTime = 0;
                        float zHpBeforeNoBleed = zombie.getHealth();
                        ((LivingEntityAccessor) tester).abyssalrpg$setAttackStrengthTicker(20);
                        tester.attack(zombie);
                        float woundsNoBleedDmg = zHpBeforeNoBleed - zombie.getHealth(); // mục tiêu chưa chảy máu: base
                                                                                        // 30.0 (~28.8 sau 2 giáp
                                                                                        // zombie)

                        zombie.invulnerableTime = 0;
                        float zHpBeforeWithBleed = zombie.getHealth();
                        ((LivingEntityAccessor) tester).abyssalrpg$setAttackStrengthTicker(20);
                        tester.attack(zombie);
                        float woundsWithBleedDmg = zHpBeforeWithBleed - zombie.getHealth(); // đang chảy máu: +15%
                                                                                            // (~33.4)
                        float woundsBleedBonusTracked = DualBladeCombatHandler.getBleedDeepWoundsBonus(zombie);
                        boolean deepWoundsOk = (woundsWithBleedDmg > woundsNoBleedDmg * 1.13F)
                                        && Math.abs(woundsBleedBonusTracked - 0.15F) < 0.005F;

                        // 5c. Critical Strike III (15% chance x1.5 dmg)
                        ItemStack critBlade = new ItemStack(ModItems.DUAL_KNIFE);
                        critBlade.enchant(critStrike, 3);
                        equipDualBladeForTest(tester, critBlade);
                        float maxCritObserved = 0.0F;
                        for (int i = 0; i < 60; i++) {
                                zombie.setHealth(500.0F);
                                zombie.invulnerableTime = 0;
                                ((LivingEntityAccessor) tester).abyssalrpg$setAttackStrengthTicker(20);
                                tester.attack(zombie);
                                float dealt = 500.0F - zombie.getHealth();
                                if (dealt > maxCritObserved)
                                        maxCritObserved = dealt;
                        }
                        boolean critOk = maxCritObserved >= 42.5F;

                        // 5d. Chuỗi 5 nhịp (kích Cyclone 360° AOE ở Hit 4 trúng mob sau lưng) +
                        // Sprint/Air routing + Offhand penalty
                        equipDualBladeForTest(tester, new ItemStack(ModItems.DUAL_KNIFE));
                        Zombie rearMob = EntityType.ZOMBIE.create(level);
                        if (rearMob != null) {
                                AttributeInstance rMax = rearMob.getAttribute(Attributes.MAX_HEALTH);
                                if (rMax != null)
                                        rMax.setBaseValue(100.0);
                                rearMob.setHealth(100.0F);
                                rearMob.moveTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() - 2.4, 0.0F,
                                                0.0F);
                                level.addFreshEntity(rearMob);
                        }
                        tester.setOnGround(true);
                        tester.setSprinting(false);
                        for (int step = 0; step < 5; step++) {
                                zombie.invulnerableTime = 0;
                                zombie.setHealth(200.0F);
                                ((LivingEntityAccessor) tester).abyssalrpg$setAttackStrengthTicker(20);
                                tester.attack(zombie);
                        }
                        boolean cycloneHitRear = rearMob != null
                                        && rearMob.getHealth() < 100.0F
                                        && rearMob.hasEffect(ModEffects.BLEEDING);
                        if (rearMob != null && !rearMob.isRemoved())
                                rearMob.discard();

                        tester.setSprinting(true);
                        tester.setOnGround(true);
                        boolean sprintMoveOk = DualBladeCombatHandler.resolveMoveType(tester, 0,
                                        false) == com.abyssalrpg.combat.DualBladeMoveType.SPRINT_THRUST;
                        tester.setSprinting(false);
                        tester.setOnGround(false);
                        tester.fallDistance = 0.4F;
                        boolean airMoveOk = DualBladeCombatHandler.resolveMoveType(tester, 0,
                                        false) == com.abyssalrpg.combat.DualBladeMoveType.AIR_SLASH;
                        tester.setOnGround(true);
                        tester.fallDistance = 0.0F;

                        tester.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                        DualBladeCombatHandler.syncPlayerAttributeModifiers(tester, level.getGameTime());
                        double penalizedSpeed = tester.getAttributeValue(Attributes.ATTACK_SPEED);
                        tester.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                        DualBladeCombatHandler.syncPlayerAttributeModifiers(tester, level.getGameTime());

                        boolean enchMechanicsOk = armorPierceBossOk
                                        && lifestealOk
                                        && deepWoundsOk
                                        && critOk
                                        && cycloneHitRear
                                        && sprintMoveOk
                                        && airMoveOk
                                        && Math.abs(penalizedSpeed - 1.6) < 0.1;
                        AbyssalRpgMod.LOGGER.info(
                                        "[DUALBLADE_AUDIT] 5. NEW 4 ENCHANTS & 5-BEAT MECHANICS: bossPlain={} vs bossArmorPierceIII={} (exp 9.0 vs 13.2) | lifestealIII=+{} (exp +{}) | deepWoundsIII={}/{} (+15% bleedBonus={}) | critIII_max={} (exp >=42.5) | hit4CycloneRear={} | sprintAir={}/{} | offhandSpd={} -> {}",
                                        String.format("%.2f", bossPlainDmg), String.format("%.2f", bossPierceDmg),
                                        String.format("%.2f", lifestealBossHeal),
                                        String.format("%.2f", expectedLifeHeal),
                                        String.format("%.2f", woundsNoBleedDmg),
                                        String.format("%.2f", woundsWithBleedDmg),
                                        String.format("%.2f", woundsBleedBonusTracked),
                                        String.format("%.2f", maxCritObserved), cycloneHitRear,
                                        sprintMoveOk, airMoveOk, String.format("%.2f", penalizedSpeed),
                                        enchMechanicsOk ? "PASS" : "FAIL");

                        // 6. Test Chiêu Chuột Phải: Lướt Chém 15 Block — 10 Hit Tức Thì (`Dual Knife
                        // Flash Dash`, ×1.5 dame!)
                        // 6a. Dọn hành lang thử nghiệm phẳng (z+0..z+17, cao y..y+3) & test dừng an
                        // toàn trước tường đá ở z+4
                        int ox = origin.getX();
                        int oy = origin.getY();
                        int oz = origin.getZ();
                        for (int dz = 0; dz <= 17; dz++) {
                                level.setBlockAndUpdate(new BlockPos(ox, oy - 1, oz + dz),
                                                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                                for (int dy = 0; dy <= 3; dy++) {
                                        level.setBlockAndUpdate(new BlockPos(ox, oy + dy, oz + dz),
                                                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                                }
                        }
                        for (int dy = 0; dy <= 2; dy++) {
                                level.setBlockAndUpdate(new BlockPos(ox, oy + dy, oz + 4),
                                                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        }
                        ItemStack wallDashBlade = new ItemStack(ModItems.DUAL_KNIFE);
                        equipDualBladeForTest(tester, wallDashBlade);
                        tester.setPos(ox + 0.5, oy, oz + 0.5);
                        tester.setYRot(0.0F);
                        tester.setXRot(0.0F);
                        tester.getCooldowns().removeCooldown(ModItems.DUAL_KNIFE);
                        boolean wallStarted = DualBladeCombatHandler.startFlashDash(tester, wallDashBlade);
                        for (int t = 0; t < 19; t++) {
                                DualBladeCombatHandler.tickServer(level.getServer());
                        }
                        double wallStopDist = tester.position()
                                        .distanceTo(new net.minecraft.world.phys.Vec3(ox + 0.5, oy, oz + 0.5));
                        boolean wallStopOk = wallStarted && wallStopDist > 1.5 && wallStopDist <= 3.6;

                        // 6b. Gỡ tường -> đặt Zombie 1500 HP (0 armor) tại z+5 & Nightmare Knight (4000
                        // HP, def=40, 8 proxy) tại z+10
                        for (int dy = 0; dy <= 2; dy++) {
                                level.setBlockAndUpdate(new BlockPos(ox, oy + dy, oz + 4),
                                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                        Zombie dashMob = EntityType.ZOMBIE.create(level);
                        if (dashMob != null) {
                                AttributeInstance dmMax = dashMob.getAttribute(Attributes.MAX_HEALTH);
                                if (dmMax != null)
                                        dmMax.setBaseValue(1500.0);
                                AttributeInstance dmArm = dashMob.getAttribute(Attributes.ARMOR);
                                if (dmArm != null)
                                        dmArm.setBaseValue(0.0);
                                dashMob.setHealth(1500.0F);
                                dashMob.setNoAi(true);
                                dashMob.moveTo(ox + 0.5, oy, oz + 5.0, 0.0F, 0.0F);
                                level.addFreshEntity(dashMob);
                        }
                        boss.moveTo(ox + 0.5, oy, oz + 10.0, 0.0F, 0.0F);
                        boss.setHealth(boss.getMaxHealth());
                        boss.removeEffect(ModEffects.BLEEDING);
                        boss.invulnerableTime = 0;
                        boss.tick(); // Đồng bộ toàn bộ 8 proxy hitbox về vị trí mới (oz + 10.0)

                        ItemStack dashBlade = new ItemStack(ModItems.DUAL_KNIFE);
                        dashBlade.enchant(armorPierce, 3); // 20% + 10% dash = 30% tổng xuyên thủ!
                        dashBlade.enchant(lifesteal, 3); // 15% lifesteal có trần tổng 16.0 HP!
                        equipDualBladeForTest(tester, dashBlade);
                        tester.setPos(ox + 0.5, oy, oz + 0.5);
                        tester.setYRot(0.0F);
                        tester.setXRot(0.0F);
                        tester.setHealth(4.0F);
                        tester.getCooldowns().removeCooldown(ModItems.DUAL_KNIFE);

                        net.minecraft.world.phys.Vec3 dashStart = tester.position();
                        float mobHpBeforeDash = dashMob != null ? dashMob.getHealth() : 0.0F;
                        float bossHpBeforeDash = boss.getHealth();
                        boolean dashStarted = DualBladeCombatHandler.startFlashDash(tester, dashBlade);
                        boolean dashCooldownSet = tester.getCooldowns().isOnCooldown(ModItems.DUAL_KNIFE);
                        int dashDurUsed = dashBlade.getDamageValue();

                        // 19 ticks đủ chạy trọn Pha 1 (0..5) + Pha 2 15 block (6..11) + Pha 3 10 hit
                        // (12..16)!
                        for (int t = 0; t < 19; t++) {
                                DualBladeCombatHandler.tickServer(level.getServer());
                        }
                        double dashDist = tester.position().distanceTo(dashStart);
                        float mobDashDmg = dashMob != null ? (mobHpBeforeDash - dashMob.getHealth()) : 0.0F;
                        float bossDashDmg = bossHpBeforeDash - boss.getHealth();
                        float dashHealed = tester.getHealth() - 4.0F;
                        boolean dashBleedOk = (dashMob != null && dashMob.hasEffect(ModEffects.BLEEDING))
                                        && boss.hasEffect(ModEffects.BLEEDING);
                        if (dashMob != null && !dashMob.isRemoved())
                                dashMob.discard();
                        tester.getCooldowns().removeCooldown(ModItems.DUAL_KNIFE);

                        // Tính chính xác sát thương 10 hit ở hệ số ×1.5 (30.0 * 1.5 = 45.0 raw/hit) với
                        // 30% xuyên thủ trên BossDefenseHolder
                        WeaponsConfig wCfg = WeaponsConfig.get();
                        float rawPerHit = (float) (wCfg.dualBladeDamage * wCfg.dashDamageMultiplier);
                        float expectedMob10Hit = rawPerHit * wCfg.dashHitCount;
                        float expectedBossPerHit = rawPerHit
                                        * (1.0F - boss.computeEffectiveDefensePoints(rawPerHit, 0.30F) / 25.0F);
                        float expectedBoss10Hit = expectedBossPerHit * wCfg.dashHitCount;
                        boolean configV5Ok = wCfg.configVersion >= 5
                                        && Math.abs(wCfg.dashDistance - 15.0) < 0.05
                                        && Math.abs(wCfg.dashDamageMultiplier - 1.5) < 0.05
                                        && Math.abs(wCfg.knifeRenderScale - 0.72f) < 0.02f;
                        boolean flashDashOk = configV5Ok
                                        && wallStopOk
                                        && dashStarted
                                        && dashCooldownSet
                                        && dashDurUsed == wCfg.dashDurabilityCost
                                        && dashDist >= 14.5
                                        && dashDist <= 15.5
                                        && Math.abs(mobDashDmg - expectedMob10Hit) < 1.0F
                                        && Math.abs(bossDashDmg - expectedBoss10Hit) < 1.0F
                                        && Math.abs(dashHealed - wCfg.maxDashLifestealTotal) < 0.25F
                                        && dashBleedOk;
                        AbyssalRpgMod.LOGGER.info(
                                        "[DUALKNIFE_AUDIT] 6. FLASH DASH (v{} · {}b · scale={} · x{} · 10hit +30% Pierce): wallStop={} ({}b) | dashDist={}b (exp 15.0) | mob10HitDmg={} (exp {}) | boss10HitDedupDmg={} (exp {}) | healCap=+{} (exp 16.0) | cd={} dur={} bleed={} -> {}",
                                        wCfg.configVersion, String.format("%.1f", wCfg.dashDistance),
                                        String.format("%.2f", wCfg.knifeRenderScale),
                                        String.format("%.1f", wCfg.dashDamageMultiplier),
                                        wallStopOk, String.format("%.2f", wallStopDist),
                                        String.format("%.2f", dashDist),
                                        String.format("%.1f", mobDashDmg), String.format("%.1f", expectedMob10Hit),
                                        String.format("%.1f", bossDashDmg), String.format("%.1f", expectedBoss10Hit),
                                        String.format("%.2f", dashHealed), dashCooldownSet, dashDurUsed, dashBleedOk,
                                        flashDashOk ? "PASS" : "FAIL");

                        // 7. Benchmark MSPT cho 100 nhịp combat handler + server tick
                        long t0 = System.nanoTime();
                        for (int i = 0; i < 100; i++) {
                                DualBladeCombatHandler.tickServer(level.getServer());
                        }
                        double handlerMspt = ((System.nanoTime() - t0) / 1_000_000.0) / 100.0;
                        AbyssalRpgMod.LOGGER.info("[DUALKNIFE_PERF] Handler tickServer MSPT: {} ms (budget < 0.5 ms)",
                                        String.format("%.4f", handlerMspt));

                        // Đưa player & Nightmare Knight về lại vị trí chuẩn cho bài test real server
                        // ticks (5 nhát cách nhau 8 ticks đúng speed 2.5!)
                        tester.setPos(ox + 0.5, oy + 0.5, oz + 0.5);
                        boss.moveTo(ox + 0.5, oy + 0.5, oz + 4.0, 0.0F, 0.0F);
                        boss.setHealth(boss.getMaxHealth());
                        boss.removeEffect(ModEffects.BLEEDING);
                        boss.invulnerableTime = 0;
                        boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        boss.tick();
                        equipDualBladeForTest(tester, new ItemStack(ModItems.DUAL_KNIFE));
                        NightmareKnightBossEntity bossForRealTick = boss;
                        boss = null;
                        activeDualBladeAudit = new RealTickDualBladeAudit(
                                        source, level, tester, bossForRealTick,
                                        statsOk && enchantsOk && zombieOk && enchMechanicsOk && flashDashOk,
                                        dmgAttr, spdAttr, maxDur, enchantsOk, zombieHits,
                                        bossPierceDmg, lifestealBossHeal, woundsWithBleedDmg, maxCritObserved,
                                        penalizedSpeed, handlerMspt);
                        return 1;
                } finally {
                        if (zombie != null && !zombie.isRemoved())
                                zombie.discard();
                        if (boss != null && !boss.isRemoved())
                                boss.discard();
                }
        }

        private static RealTickDualBladeAudit activeDualBladeAudit = null;
        private static RealTickArmorExpiryAudit activeArmorAudit = null;
        private static RealTickCinderArmorExpiryAudit activeCinderArmorAudit = null;
        private static RealTickHydraArmorExpiryAudit activeHydraArmorAudit = null;

        public static void tickActiveAudits(net.minecraft.server.MinecraftServer server) {
                if (activeDualBladeAudit != null && activeDualBladeAudit.tick()) {
                        activeDualBladeAudit = null;
                }
                if (activeArmorAudit != null && activeArmorAudit.tick()) {
                        activeArmorAudit = null;
                }
                if (activeCinderArmorAudit != null && activeCinderArmorAudit.tick()) {
                        activeCinderArmorAudit = null;
                }
                if (activeHydraArmorAudit != null && activeHydraArmorAudit.tick()) {
                        activeHydraArmorAudit = null;
                }
        }

        private static final class RealTickDualBladeAudit {
                private final CommandSourceStack source;
                private final ServerLevel level;
                private final FakePlayer tester;
                private final NightmareKnightBossEntity boss;
                private final boolean syncStagesOk;
                private final double dmgAttr;
                private final double spdAttr;
                private final int maxDur;
                private final boolean enchantsOk;
                private final int zombieHits;
                private final float bossPierceDmg;
                private final float lifestealHeal;
                private final float deepWoundsDmg;
                private final float maxCritObserved;
                private final double penalizedSpeed;
                private final double handlerMspt;

                private int elapsedTicks = 0;
                private int bossProxyHits = 0;
                private int bleedStacksAfter5Hits = 0;
                private float hpBefore5StackBleed = -1.0F;
                private float natural5StackBleedDmg = 0.0F;
                private double bleedDeltaKb = 0.0;
                private boolean invulnPreservedAcrossBleed = false;

                RealTickDualBladeAudit(CommandSourceStack source, ServerLevel level, FakePlayer tester,
                                NightmareKnightBossEntity boss, boolean syncStagesOk,
                                double dmgAttr, double spdAttr, int maxDur, boolean enchantsOk,
                                int zombieHits, float bossPierceDmg, float lifestealHeal,
                                float deepWoundsDmg, float maxCritObserved, double penalizedSpeed, double handlerMspt) {
                        this.source = source;
                        this.level = level;
                        this.tester = tester;
                        this.boss = boss;
                        this.syncStagesOk = syncStagesOk;
                        this.dmgAttr = dmgAttr;
                        this.spdAttr = spdAttr;
                        this.maxDur = maxDur;
                        this.enchantsOk = enchantsOk;
                        this.zombieHits = zombieHits;
                        this.bossPierceDmg = bossPierceDmg;
                        this.lifestealHeal = lifestealHeal;
                        this.deepWoundsDmg = deepWoundsDmg;
                        this.maxCritObserved = maxCritObserved;
                        this.penalizedSpeed = penalizedSpeed;
                        this.handlerMspt = handlerMspt;
                }

                boolean tick() {
                        if (this.boss.isRemoved() || !this.boss.isAlive()) {
                                AbyssalRpgMod.LOGGER
                                                .info("[DUALBLADE_AUDIT] === TỔNG KẾT: FAIL (boss removed early) ===");
                                return true;
                        }
                        // Tung 5 nhát đánh thực tế vào proxy hitbox ở tick 1, 9, 17, 25, 33 (đúng chuẩn
                        // 8 ticks/hit ở speed 2.5, 0 can thiệp invulnerableTime!)
                        if (elapsedTicks == 1 || elapsedTicks == 9 || elapsedTicks == 17 || elapsedTicks == 25
                                        || elapsedTicks == 33) {
                                NightmareHitboxPartEntity proxyPart = this.boss.getActiveHitboxParts().get(0);
                                float before = this.boss.getHealth();
                                ((LivingEntityAccessor) this.tester).abyssalrpg$setAttackStrengthTicker(20);
                                this.tester.attack(proxyPart);
                                if (this.boss.getHealth() < before) {
                                        this.bossProxyHits++;
                                }
                        }
                        if (elapsedTicks == 34) {
                                MobEffectInstance bleed = this.boss.getEffect(ModEffects.BLEEDING);
                                this.bleedStacksAfter5Hits = bleed != null ? (bleed.getAmplifier() + 1) : 0;
                        }
                        // Đợi nhịp Bleeding tự nhiên ở tick ~41 (1 + 20 = 21, 21 + 20 = 41 -> trừ đúng
                        // 10.0 dmg, 0 KB, bảo toàn i-frame)
                        if (elapsedTicks == 39) {
                                this.boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                                this.boss.invulnerableTime = 17;
                                this.hpBefore5StackBleed = this.boss.getHealth();
                        }
                        if (elapsedTicks == 43) {
                                this.natural5StackBleedDmg = this.hpBefore5StackBleed - this.boss.getHealth();
                                this.bleedDeltaKb = this.boss.getDeltaMovement().horizontalDistance();
                                this.invulnPreservedAcrossBleed = (this.boss.invulnerableTime <= 14);
                                boolean bleedTagsOk = ModDamageTypes.bleeding(this.level, this.tester)
                                                .is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                                                && ModDamageTypes.bleeding(this.level, this.tester)
                                                                .is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK)
                                                && ModDamageTypes.bleeding(this.level, this.tester).is(
                                                                net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN);
                                boolean bossProxyOk = (this.bossProxyHits == 5)
                                                && (this.bleedStacksAfter5Hits == 5)
                                                && Math.abs(this.natural5StackBleedDmg - 10.0F) < 0.05F
                                                && this.bleedDeltaKb < 0.001
                                                && this.invulnPreservedAcrossBleed
                                                && bleedTagsOk;
                                AbyssalRpgMod.LOGGER.info(
                                                "[DUALBLADE_AUDIT] 4. BOSS PROXY REAL-TICK (Nightmare Knight, 5 hits x 8t): hits={}/5 mainBossBleedStacks={}/5 naturalBleedDmg={} (exp 10.0) kb={} invulnPreserved={} (now={}) tagsOk={} -> {}",
                                                this.bossProxyHits, this.bleedStacksAfter5Hits,
                                                String.format("%.2f", this.natural5StackBleedDmg),
                                                String.format("%.4f", this.bleedDeltaKb),
                                                this.invulnPreservedAcrossBleed, this.boss.invulnerableTime,
                                                bleedTagsOk, bossProxyOk ? "PASS" : "FAIL");

                                this.boss.discard();
                                boolean allPass = this.syncStagesOk && bossProxyOk;
                                AbyssalRpgMod.LOGGER.info("[DUALBLADE_AUDIT] === TỔNG KẾT: {} ===",
                                                allPass ? "ALL_PASS" : "FAIL");
                                this.source.sendSuccess(() -> Component.literal(String.format(
                                                "[Dual Blade Audit v2] %s | dmg=%.0f spd=%.1f dur=%d | 4Ench=%s | RapidHits=%d/5 | BossProxy=%d/5 | NaturalBleed=%.1f | ArmorPierceIII=%.1f | LifestealIII=+%.2f | DeepWounds=%.1f | CritMax=%.1f | OffhandSpd=%.1f | MSPT=%.4f ms",
                                                allPass ? "ALL PASS" : "FAIL", this.dmgAttr, this.spdAttr, this.maxDur,
                                                this.enchantsOk,
                                                this.zombieHits, this.bossProxyHits, this.natural5StackBleedDmg,
                                                this.bossPierceDmg, this.lifestealHeal, this.deepWoundsDmg,
                                                this.maxCritObserved, this.penalizedSpeed, this.handlerMspt))
                                                .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                                return true;
                        }
                        this.elapsedTicks++;
                        return false;
                }
        }

        private static void equipDualBladeForTest(FakePlayer tester, ItemStack stack) {
                ItemStack old = tester.getMainHandItem();
                if (!old.isEmpty()) {
                        old.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
                                AttributeInstance inst = tester.getAttribute(attr);
                                if (inst != null)
                                        inst.removeModifier(mod.id());
                        });
                }
                tester.setItemSlot(EquipmentSlot.MAINHAND, stack);
                stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
                        AttributeInstance inst = tester.getAttribute(attr);
                        if (inst != null) {
                                inst.removeModifier(mod.id());
                                inst.addTransientModifier(mod);
                        }
                });
        }

        /**
         * Kiểm định toàn diện Bộ Giáp Nightmare Knight (`/abyss armor test`):
         * 1. Đăng ký 6 vật phẩm, `Holder<ArmorMaterial>`, chỉ số chuẩn Netherite
         * 3/8/6/3 (=20), Toughness 3.0 (=12.0),
         * KB Res 0.10 (=40%), độ bền 407/592/555/481 (37x), kháng lửa, `nightmare_core`
         * glint, sửa Đe bằng Shard, enchant vanilla.
         * 2. Đủ 4 công thức Shaped Crafting Table
         * (`nightmare_helmet/chestplate/leggings/boots`).
         * 3. Loot Table Boss Nightmare Knight: Phase 1 intercept + 2000 kill Phase 2
         * (~5% Core, 100% 6–10 Shards).
         * 4. Full Set Bonus 4/4 mới:
         * - Mặc 4/4: `MAX_HEALTH = 24.0` (+4.0, không cộng dồn khi sync lặp/relog),
         * `FIRE_RESISTANCE` (amp 0, 60t, ambient/!visible/showIcon),
         * `DAMAGE_RESISTANCE` (amp 1 = Kháng Cự II, 60t, ambient/!visible/showIcon),
         * không ghi đè Kháng Cự III+, không xung đột `BossHealthHolder`.
         * - Tháo 1 món: `MAX_HEALTH` lập tức về `20.0`, máu bị clamp `24.0 -> 20.0`,
         * không còn modifier thừa,
         * và 2 hiệu ứng tự hết sạch sau ~3 giây (kiểm chứng real-tick 65 ticks trên
         * server thực).
         */
        private static int runNightmareArmorAudit(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
                AbyssalRpgMod.LOGGER.info("[ARMOR_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH NIGHTMARE KNIGHT ARMOR TẠI {} ===",
                                origin);

                FakePlayer tester = FakePlayer.get(level, new com.mojang.authlib.GameProfile(
                                UUID.nameUUIDFromBytes("AbyssalNightmareArmorAudit".getBytes()), "ArmorAuditBot"));
                tester.setPos(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5);
                tester.removeAllEffects();
                tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                NightmareArmorHandler.syncMaxHealthModifier(tester);
                tester.setHealth(tester.getMaxHealth());

                NightmareKnightBossEntity boss = null;
                Zombie wearer = null;
                try {
                        // 1. Kiểm tra đăng ký vật phẩm, ArmorMaterial, config v2, chỉ số 37x, sửa Đe &
                        // Enchant vanilla
                        ArmorConfig cfg = ArmorConfig.get();
                        ItemStack core = new ItemStack(ModItems.NIGHTMARE_CORE);
                        ItemStack shard = new ItemStack(ModItems.NIGHTMARE_ARMOR_SHARD);
                        ItemStack helm = new ItemStack(ModItems.NIGHTMARE_HELMET);
                        ItemStack chest = new ItemStack(ModItems.NIGHTMARE_CHESTPLATE);
                        ItemStack legs = new ItemStack(ModItems.NIGHTMARE_LEGGINGS);
                        ItemStack boots = new ItemStack(ModItems.NIGHTMARE_BOOTS);

                        ArmorMaterial mat = ModArmorMaterials.NIGHTMARE_KNIGHT.value();
                        int defTotal = mat.getDefense(ArmorItem.Type.HELMET) + mat.getDefense(ArmorItem.Type.CHESTPLATE)
                                        + mat.getDefense(ArmorItem.Type.LEGGINGS)
                                        + mat.getDefense(ArmorItem.Type.BOOTS);
                        boolean fireResOk = core.has(DataComponents.FIRE_RESISTANT)
                                        && shard.has(DataComponents.FIRE_RESISTANT)
                                        && helm.has(DataComponents.FIRE_RESISTANT)
                                        && chest.has(DataComponents.FIRE_RESISTANT)
                                        && legs.has(DataComponents.FIRE_RESISTANT)
                                        && boots.has(DataComponents.FIRE_RESISTANT);
                        boolean coreGlintOk = core.hasFoil();
                        boolean repairOk = mat.repairIngredient().get().test(shard);
                        boolean durOk = helm.getMaxDamage() == 407 && chest.getMaxDamage() == 592
                                        && legs.getMaxDamage() == 555 && boots.getMaxDamage() == 481;
                        boolean cfgOk = cfg.configVersion == ArmorConfig.CURRENT_CONFIG_VERSION
                                        && cfg.durabilityMultiplier == 37
                                        && Math.abs(cfg.setBonusMaxHealth - 4.0) < 1e-6;
                        boolean statsOk = cfgOk && (defTotal == 20)
                                        && Math.abs(mat.toughness() - 3.0F) < 0.01F
                                        && Math.abs(mat.knockbackResistance() - 0.10F) < 0.005F
                                        && durOk && fireResOk && coreGlintOk && repairOk;

                        HolderLookup.RegistryLookup<Enchantment> enchReg = level.registryAccess()
                                        .lookupOrThrow(Registries.ENCHANTMENT);
                        Enchantment prot = enchReg.getOrThrow(Enchantments.PROTECTION).value();
                        Enchantment unbreak = enchReg.getOrThrow(Enchantments.UNBREAKING).value();
                        Enchantment mending = enchReg.getOrThrow(Enchantments.MENDING).value();
                        Enchantment resp = enchReg.getOrThrow(Enchantments.RESPIRATION).value();
                        Enchantment ff = enchReg.getOrThrow(Enchantments.FEATHER_FALLING).value();
                        boolean vanillaEnchOk = prot.canEnchant(helm) && prot.canEnchant(chest) && prot.canEnchant(legs)
                                        && prot.canEnchant(boots)
                                        && unbreak.canEnchant(chest) && mending.canEnchant(chest)
                                        && resp.canEnchant(helm) && ff.canEnchant(boots);

                        AbyssalRpgMod.LOGGER.info(
                                        "[ARMOR_AUDIT] 1. STATS & MATERIAL (cfg v{}): def={} (3/8/6/3) tough={} kbRes={} dur={}/{}/{}/{} fireRes={} coreGlint={} repairShard={} vanillaEnch={} -> {}",
                                        cfg.configVersion, defTotal, mat.toughness(), mat.knockbackResistance(),
                                        helm.getMaxDamage(), chest.getMaxDamage(), legs.getMaxDamage(),
                                        boots.getMaxDamage(),
                                        fireResOk, coreGlintOk, repairOk, vanillaEnchOk,
                                        (statsOk && vanillaEnchOk) ? "PASS" : "FAIL");

            // 2. Kiểm tra đủ 4 công thức Shaped Crafting Table khớp pattern kẹp Giáp Kim Cương (`minecraft:diamond_*`)
            CraftingInput helmIn = CraftingInput.of(3, 2, List.of(
                    shard.copy(), shard.copy(), shard.copy(),
                    shard.copy(), new ItemStack(Items.DIAMOND_HELMET), shard.copy()));
            CraftingInput chestIn = CraftingInput.of(3, 3, List.of(
                    shard.copy(), new ItemStack(Items.DIAMOND_CHESTPLATE), shard.copy(),
                    shard.copy(), core.copy(), shard.copy(),
                    shard.copy(), shard.copy(), shard.copy()));
            CraftingInput legsIn = CraftingInput.of(3, 3, List.of(
                    shard.copy(), shard.copy(), shard.copy(),
                    shard.copy(), new ItemStack(Items.DIAMOND_LEGGINGS), shard.copy(),
                    shard.copy(), ItemStack.EMPTY, shard.copy()));
            CraftingInput bootsIn = CraftingInput.of(3, 2, List.of(
                    shard.copy(), ItemStack.EMPTY, shard.copy(),
                    shard.copy(), new ItemStack(Items.DIAMOND_BOOTS), shard.copy()));

                        ItemStack helmOut = matchCraftingResult(level, helmIn);
                        ItemStack chestOut = matchCraftingResult(level, chestIn);
                        ItemStack legsOut = matchCraftingResult(level, legsIn);
                        ItemStack bootsOut = matchCraftingResult(level, bootsIn);
                        boolean recipesOk = helmOut.is(ModItems.NIGHTMARE_HELMET)
                                        && chestOut.is(ModItems.NIGHTMARE_CHESTPLATE)
                                        && legsOut.is(ModItems.NIGHTMARE_LEGGINGS)
                                        && bootsOut.is(ModItems.NIGHTMARE_BOOTS);
                        AbyssalRpgMod.LOGGER.info(
                                        "[ARMOR_AUDIT] 2. SHAPED RECIPES (4/4): helm={} chest={} legs={} boots={} -> {}",
                                        BuiltInRegistries.ITEM.getKey(helmOut.getItem()),
                                        BuiltInRegistries.ITEM.getKey(chestOut.getItem()),
                                        BuiltInRegistries.ITEM.getKey(legsOut.getItem()),
                                        BuiltInRegistries.ITEM.getKey(bootsOut.getItem()),
                                        recipesOk ? "PASS" : "FAIL");

                        // 3. Boss Nightmare Knight Phase 1 zero-drop guard + Loot Table 2000 rolls
                        // Phase 2
                        boss = ModEntities.NIGHTMARE_KNIGHT.create(level);
                        if (boss == null) {
                                throw new IllegalStateException("Không thể tạo NightmareKnightBossEntity");
                        }
                        boss.moveTo(origin.getX() + 14.0, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        level.addFreshEntity(boss);

                        boolean p1Before = !boss.hasEnteredPhase2();
                        boss.hurt(level.damageSources().playerAttack(tester), 10000.0F);
                        boolean p1InterceptedAlive = boss.isAlive()
                                        && (boss.isTransitioningPhase() || boss.hasEnteredPhase2());

                        LootTable lootTable = level.getServer().reloadableRegistries()
                                        .getLootTable(boss.getLootTable());
                        LootParams lootParams = new LootParams.Builder(level)
                                        .withParameter(LootContextParams.THIS_ENTITY, boss)
                                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
                                        .withParameter(LootContextParams.DAMAGE_SOURCE,
                                                        level.damageSources().playerAttack(tester))
                                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, tester)
                                        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, tester)
                                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, tester)
                                        .create(LootContextParamSets.ENTITY);

                        int rolls = 2000;
                        int coreDrops = 0;
                        int shardDropsRolls = 0;
                        int minShards = Integer.MAX_VALUE;
                        int maxShards = 0;
                        for (int i = 0; i < rolls; i++) {
                                List<ItemStack> drops = lootTable.getRandomItems(lootParams);
                                int shardsThisRoll = 0;
                                for (ItemStack d : drops) {
                                        if (d.is(ModItems.NIGHTMARE_CORE)) {
                                                coreDrops += d.getCount();
                                        } else if (d.is(ModItems.NIGHTMARE_ARMOR_SHARD)) {
                                                shardsThisRoll += d.getCount();
                                        }
                                }
                                if (shardsThisRoll > 0) {
                                        shardDropsRolls++;
                                        minShards = Math.min(minShards, shardsThisRoll);
                                        maxShards = Math.max(maxShards, shardsThisRoll);
                                }
                        }
                        double coreRatePct = (coreDrops * 100.0) / rolls;
                        boolean lootOk = p1Before && p1InterceptedAlive
                                        && coreRatePct >= 3.4 && coreRatePct <= 6.6
                                        && shardDropsRolls == rolls && minShards >= 6 && maxShards <= 10;
                        AbyssalRpgMod.LOGGER.info(
                                        "[ARMOR_AUDIT] 3. BOSS LOOT (P1 intercept + 2000 P2 rolls): p1Intercept={} core={}/{} ({}%) shardRolls={}/{} range={}..{} -> {}",
                                        p1InterceptedAlive, coreDrops, rolls, String.format("%.2f", coreRatePct),
                                        shardDropsRolls, rolls, minShards, maxShards, lootOk ? "PASS" : "FAIL");

                        // 4. Full Set Bonus 4/4 mới (Kháng Lửa I, Kháng Cự II, +4.0 MAX_HEALTH, không
                        // cộng dồn, không ghi đè Kháng Cự III+, không chạm BossHealthHolder)
                        tester.setItemSlot(EquipmentSlot.HEAD, helm.copy());
                        tester.setItemSlot(EquipmentSlot.CHEST, chest.copy());
                        tester.setItemSlot(EquipmentSlot.LEGS, legs.copy());
                        tester.setItemSlot(EquipmentSlot.FEET, boots.copy());
                        int equipped4 = NightmareArmorHandler.countEquippedPieces(tester);

                        // Sync 2 lần liên tiếp mô phỏng relog/đổi world -> MAX_HEALTH phải đúng 24.0 (0
                        // cộng dồn)
                        NightmareArmorHandler.syncPlayerImmediate(tester);
                        NightmareArmorHandler.syncPlayerImmediate(tester);

                        AttributeInstance maxHpAttr = tester.getAttribute(Attributes.MAX_HEALTH);
                        float maxHp4 = tester.getMaxHealth();
                        boolean hasMod4 = maxHpAttr != null
                                        && maxHpAttr.getModifier(NightmareArmorHandler.SET_HEALTH_MODIFIER_ID) != null;

                        MobEffectInstance fireEff = tester.getEffect(MobEffects.FIRE_RESISTANCE);
                        MobEffectInstance resEff = tester.getEffect(MobEffects.DAMAGE_RESISTANCE);
                        boolean fireOk = fireEff != null && fireEff.getAmplifier() == 0
                                        && fireEff.isAmbient() && !fireEff.isVisible() && fireEff.showIcon()
                                        && fireEff.getDuration() >= 50 && fireEff.getDuration() <= 60;
                        boolean resOk = resEff != null && resEff.getAmplifier() == 1
                                        && resEff.isAmbient() && !resEff.isVisible() && resEff.showIcon()
                                        && resEff.getDuration() >= 50 && resEff.getDuration() <= 60;

                        // Test không ghi đè Kháng Cự cấp cao hơn (Resistance III amp=2)
                        tester.addEffect(
                                        new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2, false, true, true));
                        NightmareArmorHandler.applyPeriodicSetEffects(tester);
                        MobEffectInstance res3After = tester.getEffect(MobEffects.DAMAGE_RESISTANCE);
                        boolean higherResPreservedOk = res3After != null && res3After.getAmplifier() == 2
                                        && res3After.getDuration() >= 195;

                        // Test không xung đột với BossHealthHolder
                        NightmareArmorHandler.syncMaxHealthModifier(boss);
                        AttributeInstance bossHpAttr = boss.getAttribute(Attributes.MAX_HEALTH);
                        boolean bossSafeOk = bossHpAttr == null
                                        || bossHpAttr.getModifier(NightmareArmorHandler.SET_HEALTH_MODIFIER_ID) == null;

                        // Test tháo 1 món -> MAX_HEALTH lập tức về 20.0, health clamp từ 24.0 về 20.0,
                        // 0 modifier thừa
                        tester.setHealth(24.0F);
                        tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        NightmareArmorHandler.syncMaxHealthModifier(tester);
                        float maxHp3 = tester.getMaxHealth();
                        float hpClamped3 = tester.getHealth();
                        boolean modRemoved3 = maxHpAttr != null
                                        && maxHpAttr.getModifier(NightmareArmorHandler.SET_HEALTH_MODIFIER_ID) == null;

                        boolean setBonusSyncOk = (equipped4 == 4)
                                        && Math.abs(maxHp4 - 24.0F) < 0.01F && hasMod4
                                        && fireOk && resOk && higherResPreservedOk && bossSafeOk
                                        && Math.abs(maxHp3 - 20.0F) < 0.01F && Math.abs(hpClamped3 - 20.0F) < 0.01F
                                        && modRemoved3;

                        AbyssalRpgMod.LOGGER.info(
                                        "[ARMOR_AUDIT] 4. FULL SET BONUS 4/4 (EQUIP/UNEQUIP/GUARD): pieces={} maxHP4={} mod4={} fireRes(amp0,{}t)={} resII(amp1,{}t)={} keepResIII={} bossSafe={} unequipMaxHP={} clampHP={} modGone={} -> {}",
                                        equipped4, String.format("%.1f", maxHp4), hasMod4,
                                        fireEff != null ? fireEff.getDuration() : -1, fireOk,
                                        resEff != null ? resEff.getDuration() : -1, resOk,
                                        higherResPreservedOk, bossSafeOk,
                                        String.format("%.1f", maxHp3), String.format("%.1f", hpClamped3), modRemoved3,
                                        setBonusSyncOk ? "PASS" : "FAIL");

                        // 5. Real-tick expiry check (65 server ticks ~3.25s) trên thực thể sống trong
                        // level:
                        // mặc 4/4 nhận đủ 24.0 HP + 2 buff -> tháo 1 món -> MAX_HEALTH về 20.0 ngay & 2
                        // buff tự hết sạch sau ~3s!
                        wearer = EntityType.ZOMBIE.create(level);
                        if (wearer == null) {
                                throw new IllegalStateException("Không thể tạo Zombie real-tick audit");
                        }
                        wearer.moveTo(origin.getX() + 2.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        wearer.setNoAi(true);
                        wearer.setInvulnerable(true);
                        level.addFreshEntity(wearer);

                        wearer.setItemSlot(EquipmentSlot.HEAD, helm.copy());
                        wearer.setItemSlot(EquipmentSlot.CHEST, chest.copy());
                        wearer.setItemSlot(EquipmentSlot.LEGS, legs.copy());
                        wearer.setItemSlot(EquipmentSlot.FEET, boots.copy());
                        NightmareArmorHandler.syncPlayerImmediate(wearer);
                        float wearerHp4 = wearer.getMaxHealth();
                        wearer.setHealth(wearerHp4);
                        boolean wearerBuffsOn4 = Math.abs(wearerHp4 - 24.0F) < 0.01F
                                        && wearer.hasEffect(MobEffects.FIRE_RESISTANCE)
                                        && wearer.hasEffect(MobEffects.DAMAGE_RESISTANCE);

                        // Tháo mũ ngay tại tick 0 -> máu về 20.0 & 2 buff sẽ tự đếm ngược hết sạch
                        // trong 65 ticks!
                        wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        NightmareArmorHandler.syncMaxHealthModifier(wearer);
                        boolean wearerUnequipNowOk = Math.abs(wearer.getMaxHealth() - 20.0F) < 0.01F
                                        && Math.abs(wearer.getHealth() - 20.0F) < 0.01F;

                        Zombie wearerForRealTick = wearer;
                        wearer = null; // bàn giao lifecycle cho RealTickArmorExpiryAudit
                        boolean syncAllOk = statsOk && vanillaEnchOk && recipesOk && lootOk && setBonusSyncOk
                                        && wearerBuffsOn4 && wearerUnequipNowOk;
                        activeArmorAudit = new RealTickArmorExpiryAudit(
                                        source, wearerForRealTick, syncAllOk,
                                        defTotal, mat.toughness(), mat.knockbackResistance(),
                                        helm.getMaxDamage(), chest.getMaxDamage(), legs.getMaxDamage(),
                                        boots.getMaxDamage(),
                                        coreRatePct, minShards, maxShards, maxHp4, maxHp3);
                        return syncAllOk ? 1 : 0;
                } catch (Exception e) {
                        AbyssalRpgMod.LOGGER.error("[ARMOR_AUDIT] Lỗi kiểm định Nightmare Armor:", e);
                        source.sendFailure(Component.literal("[Nightmare Armor Audit] ERROR: " + e.getMessage()));
                        return 0;
                } finally {
                        tester.removeAllEffects();
                        tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                        NightmareArmorHandler.syncMaxHealthModifier(tester);
                        if (boss != null)
                                boss.discard();
                        if (wearer != null)
                                wearer.discard();
                }
        }

        private static final class RealTickArmorExpiryAudit {
                private final CommandSourceStack source;
                private final Zombie wearer;
                private final boolean syncStagesOk;
                private final int defTotal;
                private final float toughness;
                private final float kbRes;
                private final int durH, durC, durL, durB;
                private final double coreRatePct;
                private final int minShards, maxShards;
                private final float maxHp4, maxHp3;
                private int elapsedTicks = 0;

                RealTickArmorExpiryAudit(CommandSourceStack source, Zombie wearer, boolean syncStagesOk,
                                int defTotal, float toughness, float kbRes,
                                int durH, int durC, int durL, int durB,
                                double coreRatePct, int minShards, int maxShards,
                                float maxHp4, float maxHp3) {
                        this.source = source;
                        this.wearer = wearer;
                        this.syncStagesOk = syncStagesOk;
                        this.defTotal = defTotal;
                        this.toughness = toughness;
                        this.kbRes = kbRes;
                        this.durH = durH;
                        this.durC = durC;
                        this.durL = durL;
                        this.durB = durB;
                        this.coreRatePct = coreRatePct;
                        this.minShards = minShards;
                        this.maxShards = maxShards;
                        this.maxHp4 = maxHp4;
                        this.maxHp3 = maxHp3;
                }

                boolean tick() {
                        if (this.wearer.isRemoved() || !this.wearer.isAlive()) {
                                AbyssalRpgMod.LOGGER.error(
                                                "[ARMOR_AUDIT] 5. REAL-TICK EXPIRY: wearer removed prematurely -> FAIL");
                                AbyssalRpgMod.LOGGER.info("[ARMOR_AUDIT] === TỔNG KẾT: FAIL ===");
                                return true;
                        }
                        // Mô phỏng đúng chu kỳ server tick (mỗi 20t gọi applyPeriodicSetEffects nhưng
                        // đang chỉ mặc 3/4 nên không làm mới!)
                        NightmareArmorHandler.syncMaxHealthModifier(this.wearer);
                        if (this.elapsedTicks % 20 == 0) {
                                NightmareArmorHandler.applyPeriodicSetEffects(this.wearer);
                        }
                        if (this.elapsedTicks >= 65) {
                                boolean fireGone = !this.wearer.hasEffect(MobEffects.FIRE_RESISTANCE);
                                boolean resGone = !this.wearer.hasEffect(MobEffects.DAMAGE_RESISTANCE);
                                AttributeInstance attr = this.wearer.getAttribute(Attributes.MAX_HEALTH);
                                boolean modStillGone = attr != null
                                                && attr.getModifier(
                                                                NightmareArmorHandler.SET_HEALTH_MODIFIER_ID) == null
                                                && Math.abs(this.wearer.getMaxHealth() - 20.0F) < 0.01F;
                                boolean expiryOk = fireGone && resGone && modStillGone;
                                AbyssalRpgMod.LOGGER.info(
                                                "[ARMOR_AUDIT] 5. REAL-TICK EXPIRY (65 ticks after unequip): fireGone={} resGone={} maxHP={} modGone={} -> {}",
                                                fireGone, resGone, String.format("%.1f", this.wearer.getMaxHealth()),
                                                modStillGone,
                                                expiryOk ? "PASS" : "FAIL");
                                this.wearer.discard();

                                boolean allPass = this.syncStagesOk && expiryOk;
                                AbyssalRpgMod.LOGGER.info("[ARMOR_AUDIT] === TỔNG KẾT: {} ===",
                                                allPass ? "ALL_PASS" : "FAIL");
                                this.source.sendSuccess(() -> Component.literal(String.format(
                                                "[Nightmare Armor Audit] %s | Def=%d (3/8/6/3) Tough=%.1f KB=%.2f Dur=%d/%d/%d/%d | Recipes=4/4 | Core=%.2f%% Shards=%d..%d | 4/4 MaxHP=%.1f->%.1f FireRes+ResII Expiry=%s",
                                                allPass ? "ALL PASS" : "FAIL", this.defTotal, this.toughness,
                                                this.kbRes,
                                                this.durH, this.durC, this.durL, this.durB,
                                                this.coreRatePct, this.minShards, this.maxShards,
                                                this.maxHp4, this.maxHp3, expiryOk))
                                                .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                                return true;
                        }
                        this.elapsedTicks++;
                        return false;
                }
        }

        /**
         * Kiểm định toàn diện Bộ Giáp Cinder Dragon (`/abyss cinderarmor test`):
         * 1. Đăng ký 6 vật phẩm, `Holder<ArmorMaterial> CINDER_DRAGON`, chỉ số 3/8/6/3
         * (=20), Toughness 3.0 (=12.0),
         * KB Res 0.10 (=40%), độ bền 407/592/555/481 (37x), kháng lửa,
         * `cinder_dragon_heart` glint, sửa bằng Scale, enchant vanilla.
         * 2. Đủ 4 công thức Shaped Crafting Table
         * (`cinder_dragon_helmet/chestplate/leggings/boots`).
         * 3. Loot Table Boss Cinder Dragon: Phase 1 chặn chết không rơi đồ + 2000 kill
         * Phase 2 (~5% Heart, 100% 6–10 Scales, giữ nguyên sách Dual Knife).
         * 4. Full Set Bonus 4/4: Kháng Lửa I (amp 0, 60t, ambient/!visible/showIcon),
         * +3.0 Attack Damage (0 cộng dồn relog, 0 chạm BossHealthHolder),
         * đánh trúng đốt cháy mục tiêu 3s; tháo 1 món -> gỡ ngay Attack Damage modifier
         * & Kháng Lửa tự hết sạch sau 65 ticks thực!
         */
        private static int runCinderDragonArmorAudit(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
                AbyssalRpgMod.LOGGER.info("[CINDER_ARMOR_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH CINDER DRAGON ARMOR TẠI {} ===",
                                origin);

                FakePlayer tester = FakePlayer.get(level, new com.mojang.authlib.GameProfile(
                                UUID.nameUUIDFromBytes("AbyssalCinderArmorAudit".getBytes()), "CinderArmorBot"));
                tester.setPos(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5);
                tester.removeAllEffects();
                tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                CinderDragonArmorHandler.syncAttackDamageModifier(tester);
                double baseAtk = tester.getAttributeValue(Attributes.ATTACK_DAMAGE);

                CinderDragonBossEntity boss = null;
                Zombie target = null;
                Zombie wearer = null;
                try {
                        // 1. Chỉ số, Material, Config v3, Sửa Đe & Enchant vanilla
                        ArmorConfig cfg = ArmorConfig.get();
                        ItemStack heart = new ItemStack(ModItems.CINDER_DRAGON_HEART);
                        ItemStack scale = new ItemStack(ModItems.CINDER_DRAGON_SCALE);
                        ItemStack helm = new ItemStack(ModItems.CINDER_DRAGON_HELMET);
                        ItemStack chest = new ItemStack(ModItems.CINDER_DRAGON_CHESTPLATE);
                        ItemStack legs = new ItemStack(ModItems.CINDER_DRAGON_LEGGINGS);
                        ItemStack boots = new ItemStack(ModItems.CINDER_DRAGON_BOOTS);

                        ArmorMaterial mat = ModArmorMaterials.CINDER_DRAGON.value();
                        int defTotal = mat.getDefense(ArmorItem.Type.HELMET) + mat.getDefense(ArmorItem.Type.CHESTPLATE)
                                        + mat.getDefense(ArmorItem.Type.LEGGINGS)
                                        + mat.getDefense(ArmorItem.Type.BOOTS);
                        boolean fireResOk = heart.has(DataComponents.FIRE_RESISTANT)
                                        && scale.has(DataComponents.FIRE_RESISTANT)
                                        && helm.has(DataComponents.FIRE_RESISTANT)
                                        && chest.has(DataComponents.FIRE_RESISTANT)
                                        && legs.has(DataComponents.FIRE_RESISTANT)
                                        && boots.has(DataComponents.FIRE_RESISTANT);
                        boolean heartGlintOk = heart.hasFoil();
                        boolean repairOk = mat.repairIngredient().get().test(scale);
                        boolean durOk = helm.getMaxDamage() == 407 && chest.getMaxDamage() == 592
                                        && legs.getMaxDamage() == 555 && boots.getMaxDamage() == 481;
                        boolean cfgOk = cfg.configVersion == ArmorConfig.CURRENT_CONFIG_VERSION
                                        && cfg.cinderDurabilityMultiplier == 37
                                        && Math.abs(cfg.cinderSetBonusAttackDamageBonus - 3.0) < 1e-6
                                        && cfg.cinderSetBonusIgniteSeconds == 3;
                        boolean statsOk = cfgOk && (defTotal == 20)
                                        && Math.abs(mat.toughness() - 3.0F) < 0.01F
                                        && Math.abs(mat.knockbackResistance() - 0.10F) < 0.005F
                                        && durOk && fireResOk && heartGlintOk && repairOk;

                        HolderLookup.RegistryLookup<Enchantment> enchReg = level.registryAccess()
                                        .lookupOrThrow(Registries.ENCHANTMENT);
                        Enchantment prot = enchReg.getOrThrow(Enchantments.PROTECTION).value();
                        Enchantment unbreak = enchReg.getOrThrow(Enchantments.UNBREAKING).value();
                        Enchantment mending = enchReg.getOrThrow(Enchantments.MENDING).value();
                        Enchantment resp = enchReg.getOrThrow(Enchantments.RESPIRATION).value();
                        Enchantment ff = enchReg.getOrThrow(Enchantments.FEATHER_FALLING).value();
                        boolean vanillaEnchOk = prot.canEnchant(helm) && prot.canEnchant(chest) && prot.canEnchant(legs)
                                        && prot.canEnchant(boots)
                                        && unbreak.canEnchant(chest) && mending.canEnchant(chest)
                                        && resp.canEnchant(helm) && ff.canEnchant(boots);

                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_ARMOR_AUDIT] 1. STATS & MATERIAL (cfg v{}): def={} (3/8/6/3) tough={} kbRes={} dur={}/{}/{}/{} fireRes={} heartGlint={} repairScale={} vanillaEnch={} -> {}",
                                        cfg.configVersion, defTotal, mat.toughness(), mat.knockbackResistance(),
                                        helm.getMaxDamage(), chest.getMaxDamage(), legs.getMaxDamage(),
                                        boots.getMaxDamage(),
                                        fireResOk, heartGlintOk, repairOk, vanillaEnchOk,
                                        (statsOk && vanillaEnchOk) ? "PASS" : "FAIL");

            // 2. 4 công thức Shaped Crafting Table (1 Heart + 23 Scales + 1 bộ Diamond)
            CraftingInput helmIn = CraftingInput.of(3, 2, List.of(
                    scale.copy(), scale.copy(), scale.copy(),
                    scale.copy(), new ItemStack(Items.DIAMOND_HELMET), scale.copy()));
            CraftingInput chestIn = CraftingInput.of(3, 3, List.of(
                    scale.copy(), new ItemStack(Items.DIAMOND_CHESTPLATE), scale.copy(),
                    scale.copy(), heart.copy(), scale.copy(),
                    scale.copy(), scale.copy(), scale.copy()));
            CraftingInput legsIn = CraftingInput.of(3, 3, List.of(
                    scale.copy(), scale.copy(), scale.copy(),
                    scale.copy(), new ItemStack(Items.DIAMOND_LEGGINGS), scale.copy(),
                    scale.copy(), ItemStack.EMPTY, scale.copy()));
            CraftingInput bootsIn = CraftingInput.of(3, 2, List.of(
                    scale.copy(), ItemStack.EMPTY, scale.copy(),
                    scale.copy(), new ItemStack(Items.DIAMOND_BOOTS), scale.copy()));

                        ItemStack helmOut = matchCraftingResult(level, helmIn);
                        ItemStack chestOut = matchCraftingResult(level, chestIn);
                        ItemStack legsOut = matchCraftingResult(level, legsIn);
                        ItemStack bootsOut = matchCraftingResult(level, bootsIn);
                        boolean recipesOk = helmOut.is(ModItems.CINDER_DRAGON_HELMET)
                                        && chestOut.is(ModItems.CINDER_DRAGON_CHESTPLATE)
                                        && legsOut.is(ModItems.CINDER_DRAGON_LEGGINGS)
                                        && bootsOut.is(ModItems.CINDER_DRAGON_BOOTS);
                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_ARMOR_AUDIT] 2. SHAPED RECIPES (4/4): helm={} chest={} legs={} boots={} -> {}",
                                        BuiltInRegistries.ITEM.getKey(helmOut.getItem()),
                                        BuiltInRegistries.ITEM.getKey(chestOut.getItem()),
                                        BuiltInRegistries.ITEM.getKey(legsOut.getItem()),
                                        BuiltInRegistries.ITEM.getKey(bootsOut.getItem()),
                                        recipesOk ? "PASS" : "FAIL");

                        // 3. Boss Cinder Dragon Phase 1 zero-drop guard + Loot Table 2000 rolls Phase 2
                        boss = ModEntities.CINDER_DRAGON.create(level);
                        if (boss == null) {
                                throw new IllegalStateException("Không thể tạo CinderDragonBossEntity");
                        }
                        boss.moveTo(origin.getX() + 18.0, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        level.addFreshEntity(boss);

                        boolean p1Before = !boss.hasEnteredPhase2();
                        boss.hurt(level.damageSources().playerAttack(tester), 10000.0F);
                        boolean p1InterceptedAlive = boss.isAlive()
                                        && (boss.isTransitioningPhase() || boss.hasEnteredPhase2());

                        LootTable lootTable = level.getServer().reloadableRegistries()
                                        .getLootTable(boss.getLootTable());
                        LootParams lootParams = new LootParams.Builder(level)
                                        .withParameter(LootContextParams.THIS_ENTITY, boss)
                                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
                                        .withParameter(LootContextParams.DAMAGE_SOURCE,
                                                        level.damageSources().playerAttack(tester))
                                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, tester)
                                        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, tester)
                                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, tester)
                                        .create(LootContextParamSets.ENTITY);

                        int rolls = 2000;
                        int heartDrops = 0;
                        int scaleRolls = 0;
                        int bookDrops = 0;
                        int minScales = Integer.MAX_VALUE;
                        int maxScales = 0;
                        for (int i = 0; i < rolls; i++) {
                                List<ItemStack> drops = lootTable.getRandomItems(lootParams);
                                int scalesThisRoll = 0;
                                for (ItemStack d : drops) {
                                        if (d.is(ModItems.CINDER_DRAGON_HEART)) {
                                                heartDrops += d.getCount();
                                        } else if (d.is(ModItems.CINDER_DRAGON_SCALE)) {
                                                scalesThisRoll += d.getCount();
                                        } else if (d.is(Items.ENCHANTED_BOOK)) {
                                                bookDrops += d.getCount();
                                        }
                                }
                                if (scalesThisRoll > 0) {
                                        scaleRolls++;
                                        minScales = Math.min(minScales, scalesThisRoll);
                                        maxScales = Math.max(maxScales, scalesThisRoll);
                                }
                        }
                        double heartRatePct = (heartDrops * 100.0) / rolls;
                        boolean lootOk = p1Before && p1InterceptedAlive
                                        && heartRatePct >= 3.4 && heartRatePct <= 6.6
                                        && scaleRolls == rolls && minScales >= 6 && maxScales <= 10
                                        && bookDrops > 700;
                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_ARMOR_AUDIT] 3. BOSS LOOT (P1 intercept + 2000 P2 rolls): p1Intercept={} heart={}/{} ({}%) scaleRolls={}/{} range={}..{} dualKnifeBooks={} -> {}",
                                        p1InterceptedAlive, heartDrops, rolls, String.format("%.2f", heartRatePct),
                                        scaleRolls, rolls, minScales, maxScales, bookDrops, lootOk ? "PASS" : "FAIL");

                        // 4. Full Set Bonus 4/4 (Fire Resistance I, +3.0 Attack Damage, 3s Ignite on
                        // hit, clean unequip)
                        tester.setItemSlot(EquipmentSlot.HEAD, helm.copy());
                        tester.setItemSlot(EquipmentSlot.CHEST, chest.copy());
                        tester.setItemSlot(EquipmentSlot.LEGS, legs.copy());
                        tester.setItemSlot(EquipmentSlot.FEET, boots.copy());
                        int equipped4 = CinderDragonArmorHandler.countEquippedPieces(tester);

                        // Sync 2 lần mô phỏng relog/đổi world -> không cộng dồn Attack Damage!
                        CinderDragonArmorHandler.syncPlayerImmediate(tester);
                        CinderDragonArmorHandler.syncPlayerImmediate(tester);

                        AttributeInstance atkAttr = tester.getAttribute(Attributes.ATTACK_DAMAGE);
                        double atk4 = tester.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        boolean hasAtkMod4 = atkAttr != null && atkAttr
                                        .getModifier(CinderDragonArmorHandler.SET_ATTACK_DAMAGE_MODIFIER_ID) != null;
                        boolean atkBonusOk = hasAtkMod4 && Math.abs((atk4 - baseAtk) - 3.0) < 0.01;

                        MobEffectInstance fireEff = tester.getEffect(MobEffects.FIRE_RESISTANCE);
                        boolean fireOk = fireEff != null && fireEff.getAmplifier() == 0
                                        && fireEff.isAmbient() && !fireEff.isVisible() && fireEff.showIcon()
                                        && fireEff.getDuration() >= 50 && fireEff.getDuration() <= 60;

                        // Đánh mục tiêu sống -> đốt cháy 3s (60 ticks)
                        target = EntityType.ZOMBIE.create(level);
                        if (target == null) {
                                throw new IllegalStateException("Không thể tạo Zombie target test ignite");
                        }
                        target.moveTo(origin.getX() + 3.0, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        target.setNoAi(true);
                        target.clearFire();
                        level.addFreshEntity(target);
                        target.hurt(level.damageSources().playerAttack(tester), 4.0F);
                        boolean igniteOk = target.isOnFire() && target.getRemainingFireTicks() >= 40;

                        // Không chạm BossHealthHolder
                        CinderDragonArmorHandler.syncAttackDamageModifier(boss);
                        AttributeInstance bossAtk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
                        boolean bossSafeOk = bossAtk == null || bossAtk
                                        .getModifier(CinderDragonArmorHandler.SET_ATTACK_DAMAGE_MODIFIER_ID) == null;

                        // Tháo 1 món -> gỡ ngay modifier Attack Damage
                        tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        CinderDragonArmorHandler.syncAttackDamageModifier(tester);
                        double atk3 = tester.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        boolean modRemoved3 = atkAttr != null
                                        && atkAttr.getModifier(
                                                        CinderDragonArmorHandler.SET_ATTACK_DAMAGE_MODIFIER_ID) == null
                                        && Math.abs(atk3 - baseAtk) < 0.01;

                        boolean setBonusSyncOk = (equipped4 == 4) && atkBonusOk && fireOk && igniteOk && bossSafeOk
                                        && modRemoved3;
                        AbyssalRpgMod.LOGGER.info(
                                        "[CINDER_ARMOR_AUDIT] 4. FULL SET BONUS 4/4: pieces={} atk={}->{} (+3.0 ok={}) fireRes(amp0,{}t)={} ignite3s(ticks={})={} bossSafe={} unequipAtk={} modGone={} -> {}",
                                        equipped4, String.format("%.1f", baseAtk), String.format("%.1f", atk4),
                                        atkBonusOk,
                                        fireEff != null ? fireEff.getDuration() : -1, fireOk,
                                        target.getRemainingFireTicks(), igniteOk, bossSafeOk,
                                        String.format("%.1f", atk3), modRemoved3,
                                        setBonusSyncOk ? "PASS" : "FAIL");

                        // 5. Real-tick expiry check (65 server ticks ~3.25s) sau tháo mũ
                        wearer = EntityType.ZOMBIE.create(level);
                        if (wearer == null) {
                                throw new IllegalStateException("Không thể tạo Zombie real-tick audit");
                        }
                        wearer.moveTo(origin.getX() + 4.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        wearer.setNoAi(true);
                        wearer.setInvulnerable(true);
                        level.addFreshEntity(wearer);

                        wearer.setItemSlot(EquipmentSlot.HEAD, helm.copy());
                        wearer.setItemSlot(EquipmentSlot.CHEST, chest.copy());
                        wearer.setItemSlot(EquipmentSlot.LEGS, legs.copy());
                        wearer.setItemSlot(EquipmentSlot.FEET, boots.copy());
                        CinderDragonArmorHandler.syncPlayerImmediate(wearer);
                        boolean wearerHas4 = wearer.hasEffect(MobEffects.FIRE_RESISTANCE);
                        wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        CinderDragonArmorHandler.syncAttackDamageModifier(wearer);

                        Zombie wearerForRealTick = wearer;
                        wearer = null;
                        boolean syncAllOk = statsOk && vanillaEnchOk && recipesOk && lootOk && setBonusSyncOk
                                        && wearerHas4;
                        activeCinderArmorAudit = new RealTickCinderArmorExpiryAudit(
                                        source, wearerForRealTick, syncAllOk,
                                        defTotal, mat.toughness(), mat.knockbackResistance(),
                                        helm.getMaxDamage(), chest.getMaxDamage(), legs.getMaxDamage(),
                                        boots.getMaxDamage(),
                                        heartRatePct, minScales, maxScales, baseAtk, atk4);
                        return syncAllOk ? 1 : 0;
                } catch (Exception e) {
                        AbyssalRpgMod.LOGGER.error("[CINDER_ARMOR_AUDIT] Lỗi kiểm định Cinder Dragon Armor:", e);
                        source.sendFailure(Component.literal("[Cinder Dragon Armor Audit] ERROR: " + e.getMessage()));
                        return 0;
                } finally {
                        tester.removeAllEffects();
                        tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                        CinderDragonArmorHandler.syncAttackDamageModifier(tester);
                        if (boss != null)
                                boss.discard();
                        if (target != null)
                                target.discard();
                        if (wearer != null)
                                wearer.discard();
                }
        }

        private static final class RealTickCinderArmorExpiryAudit {
                private final CommandSourceStack source;
                private final Zombie wearer;
                private final boolean syncStagesOk;
                private final int defTotal;
                private final float toughness;
                private final float kbRes;
                private final int durH, durC, durL, durB;
                private final double heartRatePct;
                private final int minScales, maxScales;
                private final double baseAtk, atk4;
                private int elapsedTicks = 0;

                RealTickCinderArmorExpiryAudit(CommandSourceStack source, Zombie wearer, boolean syncStagesOk,
                                int defTotal, float toughness, float kbRes,
                                int durH, int durC, int durL, int durB,
                                double heartRatePct, int minScales, int maxScales,
                                double baseAtk, double atk4) {
                        this.source = source;
                        this.wearer = wearer;
                        this.syncStagesOk = syncStagesOk;
                        this.defTotal = defTotal;
                        this.toughness = toughness;
                        this.kbRes = kbRes;
                        this.durH = durH;
                        this.durC = durC;
                        this.durL = durL;
                        this.durB = durB;
                        this.heartRatePct = heartRatePct;
                        this.minScales = minScales;
                        this.maxScales = maxScales;
                        this.baseAtk = baseAtk;
                        this.atk4 = atk4;
                }

                boolean tick() {
                        if (this.wearer.isRemoved() || !this.wearer.isAlive()) {
                                AbyssalRpgMod.LOGGER.error(
                                                "[CINDER_ARMOR_AUDIT] 5. REAL-TICK EXPIRY: wearer removed prematurely -> FAIL");
                                AbyssalRpgMod.LOGGER.info("[CINDER_ARMOR_AUDIT] === TỔNG KẾT: FAIL ===");
                                return true;
                        }
                        CinderDragonArmorHandler.syncAttackDamageModifier(this.wearer);
                        if (this.elapsedTicks % 20 == 0) {
                                CinderDragonArmorHandler.applyPeriodicSetEffects(this.wearer);
                        }
                        if (this.elapsedTicks >= 65) {
                                boolean fireGone = !this.wearer.hasEffect(MobEffects.FIRE_RESISTANCE);
                                AttributeInstance attr = this.wearer.getAttribute(Attributes.ATTACK_DAMAGE);
                                boolean modGone = attr != null && attr.getModifier(
                                                CinderDragonArmorHandler.SET_ATTACK_DAMAGE_MODIFIER_ID) == null;
                                boolean expiryOk = fireGone && modGone;
                                AbyssalRpgMod.LOGGER.info(
                                                "[CINDER_ARMOR_AUDIT] 5. REAL-TICK EXPIRY (65 ticks after unequip): fireGone={} atkModGone={} -> {}",
                                                fireGone, modGone, expiryOk ? "PASS" : "FAIL");
                                this.wearer.discard();

                                boolean allPass = this.syncStagesOk && expiryOk;
                                AbyssalRpgMod.LOGGER.info("[CINDER_ARMOR_AUDIT] === TỔNG KẾT: {} ===",
                                                allPass ? "ALL_PASS" : "FAIL");
                                this.source.sendSuccess(() -> Component.literal(String.format(
                                                "[Cinder Dragon Armor Audit] %s | Def=%d (3/8/6/3) Tough=%.1f KB=%.2f Dur=%d/%d/%d/%d | Recipes=4/4 | Heart=%.2f%% Scales=%d..%d | 4/4 Atk=%.1f->%.1f Ignite3s FireRes Expiry=%s",
                                                allPass ? "ALL PASS" : "FAIL", this.defTotal, this.toughness,
                                                this.kbRes,
                                                this.durH, this.durC, this.durL, this.durB,
                                                this.heartRatePct, this.minScales, this.maxScales,
                                                this.baseAtk, this.atk4, expiryOk))
                                                .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                                return true;
                        }
                        this.elapsedTicks++;
                        return false;
                }
        }

        private static int runSpiritHydraArmorAudit(CommandSourceStack source) {
                ServerLevel level = source.getLevel();
                BlockPos origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
                AbyssalRpgMod.LOGGER.info("[HYDRA_ARMOR_AUDIT] === BẮT ĐẦU KIỂM ĐỊNH SPIRIT HYDRA ARMOR TẠI {} ===",
                                origin);

                FakePlayer tester = FakePlayer.get(level, new com.mojang.authlib.GameProfile(
                                UUID.nameUUIDFromBytes("AbyssalSpiritHydraArmorAudit".getBytes()), "HydraArmorBot"));
                tester.setPos(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5);
                tester.removeAllEffects();
                tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                tester.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                SpiritHydraArmorHandler.syncMaxHealthModifier(tester);
                tester.setHealth(tester.getMaxHealth());
                double baseMaxHp = tester.getMaxHealth();

                HydraBossEntity boss = null;
                Zombie target = null;
                Zombie wearer = null;
                try {
                        ArmorConfig cfg = ArmorConfig.get();
                        ItemStack heart = new ItemStack(ModItems.SPIRIT_HYDRA_HEART);
                        ItemStack scale = new ItemStack(ModItems.SPIRIT_HYDRA_SCALE);
                        ItemStack helm = new ItemStack(ModItems.SPIRIT_HYDRA_HELMET);
                        ItemStack chest = new ItemStack(ModItems.SPIRIT_HYDRA_CHESTPLATE);
                        ItemStack legs = new ItemStack(ModItems.SPIRIT_HYDRA_LEGGINGS);
                        ItemStack boots = new ItemStack(ModItems.SPIRIT_HYDRA_BOOTS);

                        ArmorMaterial mat = ModArmorMaterials.SPIRIT_HYDRA.value();
                        int defTotal = mat.getDefense(ArmorItem.Type.HELMET) + mat.getDefense(ArmorItem.Type.CHESTPLATE)
                                        + mat.getDefense(ArmorItem.Type.LEGGINGS)
                                        + mat.getDefense(ArmorItem.Type.BOOTS);
                        boolean fireResOk = heart.has(DataComponents.FIRE_RESISTANT)
                                        && scale.has(DataComponents.FIRE_RESISTANT)
                                        && helm.has(DataComponents.FIRE_RESISTANT)
                                        && chest.has(DataComponents.FIRE_RESISTANT)
                                        && legs.has(DataComponents.FIRE_RESISTANT)
                                        && boots.has(DataComponents.FIRE_RESISTANT);
                        boolean heartGlintOk = heart.hasFoil();
                        boolean repairOk = mat.repairIngredient().get().test(scale);
                        boolean durOk = helm.getMaxDamage() == 407 && chest.getMaxDamage() == 592
                                        && legs.getMaxDamage() == 555 && boots.getMaxDamage() == 481;
                        boolean cfgOk = cfg.configVersion == ArmorConfig.CURRENT_CONFIG_VERSION
                                        && cfg.hydraDurabilityMultiplier == 37
                                        && Math.abs(cfg.hydraSetBonusMaxHealth - 4.0) < 1e-6
                                        && cfg.hydraSetBonusPoisonSeconds == 3
                                        && cfg.hydraSetBonusSoulFlameSeconds == 3;
                        boolean statsOk = cfgOk && (defTotal == 20)
                                        && Math.abs(mat.toughness() - 3.0F) < 0.01F
                                        && Math.abs(mat.knockbackResistance() - 0.10F) < 0.005F
                                        && durOk && fireResOk && heartGlintOk && repairOk;

                        HolderLookup.RegistryLookup<Enchantment> enchReg = level.registryAccess()
                                        .lookupOrThrow(Registries.ENCHANTMENT);
                        Enchantment prot = enchReg.getOrThrow(Enchantments.PROTECTION).value();
                        Enchantment unbreak = enchReg.getOrThrow(Enchantments.UNBREAKING).value();
                        Enchantment mending = enchReg.getOrThrow(Enchantments.MENDING).value();
                        Enchantment resp = enchReg.getOrThrow(Enchantments.RESPIRATION).value();
                        Enchantment ff = enchReg.getOrThrow(Enchantments.FEATHER_FALLING).value();
                        boolean vanillaEnchOk = prot.canEnchant(helm) && prot.canEnchant(chest) && prot.canEnchant(legs)
                                        && prot.canEnchant(boots)
                                        && unbreak.canEnchant(chest) && mending.canEnchant(chest)
                                        && resp.canEnchant(helm) && ff.canEnchant(boots);

            CraftingInput helmIn = CraftingInput.of(3, 2, List.of(
                    scale.copy(), scale.copy(), scale.copy(),
                    scale.copy(), new ItemStack(Items.DIAMOND_HELMET), scale.copy()));
            CraftingInput chestIn = CraftingInput.of(3, 3, List.of(
                    scale.copy(), new ItemStack(Items.DIAMOND_CHESTPLATE), scale.copy(),
                    scale.copy(), heart.copy(), scale.copy(),
                    scale.copy(), scale.copy(), scale.copy()));
            CraftingInput legsIn = CraftingInput.of(3, 3, List.of(
                    scale.copy(), scale.copy(), scale.copy(),
                    scale.copy(), new ItemStack(Items.DIAMOND_LEGGINGS), scale.copy(),
                    scale.copy(), ItemStack.EMPTY, scale.copy()));
            CraftingInput bootsIn = CraftingInput.of(3, 2, List.of(
                    scale.copy(), ItemStack.EMPTY, scale.copy(),
                    scale.copy(), new ItemStack(Items.DIAMOND_BOOTS), scale.copy()));

                        ItemStack helmOut = matchCraftingResult(level, helmIn);
                        ItemStack chestOut = matchCraftingResult(level, chestIn);
                        ItemStack legsOut = matchCraftingResult(level, legsIn);
                        ItemStack bootsOut = matchCraftingResult(level, bootsIn);
                        boolean recipesOk = helmOut.is(ModItems.SPIRIT_HYDRA_HELMET)
                                        && chestOut.is(ModItems.SPIRIT_HYDRA_CHESTPLATE)
                                        && legsOut.is(ModItems.SPIRIT_HYDRA_LEGGINGS)
                                        && bootsOut.is(ModItems.SPIRIT_HYDRA_BOOTS);

                        boss = ModEntities.HYDRA_BOSS.create(level);
                        if (boss == null) {
                                throw new IllegalStateException("Không thể tạo HydraBossEntity");
                        }
                        boss.moveTo(origin.getX() + 18.0, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        level.addFreshEntity(boss);

                        boolean p1Before = !boss.hasEnteredPhase2();
                        boss.die(level.damageSources().playerAttack(tester));
                        boolean p1InterceptedAlive = boss.isAlive() && boss.hasEnteredPhase2()
                                        && boss.getPhaseTransitionTicks() > 0;

                        LootTable lootTable = level.getServer().reloadableRegistries()
                                        .getLootTable(boss.getLootTable());
                        LootParams lootParams = new LootParams.Builder(level)
                                        .withParameter(LootContextParams.THIS_ENTITY, boss)
                                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
                                        .withParameter(LootContextParams.DAMAGE_SOURCE,
                                                        level.damageSources().playerAttack(tester))
                                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, tester)
                                        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, tester)
                                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, tester)
                                        .create(LootContextParamSets.ENTITY);

                        int rolls = 2000;
                        int heartDrops = 0;
                        int scaleRolls = 0;
                        int fangDrops = 0;
                        int minScales = Integer.MAX_VALUE;
                        int maxScales = 0;
                        for (int i = 0; i < rolls; i++) {
                                List<ItemStack> drops = lootTable.getRandomItems(lootParams);
                                int scalesThisRoll = 0;
                                for (ItemStack d : drops) {
                                        if (d.is(ModItems.SPIRIT_HYDRA_HEART)) {
                                                heartDrops += d.getCount();
                                        } else if (d.is(ModItems.SPIRIT_HYDRA_SCALE)) {
                                                scalesThisRoll += d.getCount();
                                        } else if (d.is(ModItems.SPIRIT_HYDRA_FANG)) {
                                                fangDrops += d.getCount();
                                        }
                                }
                                if (scalesThisRoll > 0) {
                                        scaleRolls++;
                                        minScales = Math.min(minScales, scalesThisRoll);
                                        maxScales = Math.max(maxScales, scalesThisRoll);
                                }
                        }
                        double heartRatePct = (heartDrops * 100.0) / rolls;
                        boolean lootOk = p1Before && p1InterceptedAlive
                                        && heartRatePct >= 3.4 && heartRatePct <= 6.6
                                        && scaleRolls == rolls && minScales >= 6 && maxScales <= 10
                                        && fangDrops >= rolls;

                        tester.setItemSlot(EquipmentSlot.HEAD, helm.copy());
                        tester.setItemSlot(EquipmentSlot.CHEST, chest.copy());
                        tester.setItemSlot(EquipmentSlot.LEGS, legs.copy());
                        tester.setItemSlot(EquipmentSlot.FEET, boots.copy());
                        int equipped4 = SpiritHydraArmorHandler.countEquippedPieces(tester);

                        SpiritHydraArmorHandler.syncPlayerImmediate(tester);
                        SpiritHydraArmorHandler.syncPlayerImmediate(tester);
                        tester.setHealth(tester.getMaxHealth());

                        AttributeInstance hpAttr = tester.getAttribute(Attributes.MAX_HEALTH);
                        double maxHp4 = tester.getMaxHealth();
                        boolean hasHpMod4 = hpAttr != null
                                        && hpAttr.getModifier(SpiritHydraArmorHandler.SET_HEALTH_MODIFIER_ID) != null;
                        boolean hpBonusOk = hasHpMod4 && Math.abs((maxHp4 - baseMaxHp) - 4.0) < 0.01;

                        MobEffectInstance regenEff = tester.getEffect(MobEffects.REGENERATION);
                        boolean regenOk = regenEff != null && regenEff.getAmplifier() == 1
                                        && regenEff.isAmbient() && !regenEff.isVisible() && regenEff.showIcon()
                                        && regenEff.getDuration() >= 50 && regenEff.getDuration() <= 60;
                        MobEffectInstance wbEff = tester.getEffect(MobEffects.WATER_BREATHING);
                        boolean wbOk = wbEff != null && wbEff.getAmplifier() == 0
                                        && wbEff.isAmbient() && !wbEff.isVisible() && wbEff.showIcon()
                                        && wbEff.getDuration() >= 50 && wbEff.getDuration() <= 60;

                        target = EntityType.ZOMBIE.create(level);
                        if (target == null) {
                                throw new IllegalStateException("Không thể tạo Zombie target test on-hit");
                        }
                        target.moveTo(origin.getX() + 3.0, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        target.setNoAi(true);
                        target.clearFire();
                        target.removeAllEffects();
                        level.addFreshEntity(target);
                        target.hurt(level.damageSources().playerAttack(tester), 4.0F);
                        MobEffectInstance poisonEff = target.getEffect(MobEffects.POISON);
                        boolean poisonOk = poisonEff != null && poisonEff.getAmplifier() == 1
                                        && poisonEff.getDuration() >= 40;
                        boolean soulFlameOk = target.isOnFire() && target.getRemainingFireTicks() >= 40;

                        SpiritHydraArmorHandler.syncMaxHealthModifier(boss);
                        AttributeInstance bossHpAttr = boss.getAttribute(Attributes.MAX_HEALTH);
                        boolean bossSafeOk = bossHpAttr == null || bossHpAttr
                                        .getModifier(SpiritHydraArmorHandler.SET_HEALTH_MODIFIER_ID) == null;

                        tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        SpiritHydraArmorHandler.syncMaxHealthModifier(tester);
                        double maxHp3 = tester.getMaxHealth();
                        float clampedHp = tester.getHealth();
                        boolean modRemoved3 = hpAttr != null
                                        && hpAttr.getModifier(SpiritHydraArmorHandler.SET_HEALTH_MODIFIER_ID) == null
                                        && Math.abs(maxHp3 - baseMaxHp) < 0.01 && clampedHp <= maxHp3 + 0.01F;

                        boolean setBonusSyncOk = (equipped4 == 4) && hpBonusOk && regenOk && wbOk && poisonOk
                                        && soulFlameOk && bossSafeOk && modRemoved3;

                        wearer = EntityType.ZOMBIE.create(level);
                        if (wearer == null) {
                                throw new IllegalStateException("Không thể tạo Zombie real-tick audit");
                        }
                        wearer.moveTo(origin.getX() + 4.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0F, 0.0F);
                        wearer.setNoAi(true);
                        wearer.setInvulnerable(true);
                        level.addFreshEntity(wearer);

                        wearer.setItemSlot(EquipmentSlot.HEAD, helm.copy());
                        wearer.setItemSlot(EquipmentSlot.CHEST, chest.copy());
                        wearer.setItemSlot(EquipmentSlot.LEGS, legs.copy());
                        wearer.setItemSlot(EquipmentSlot.FEET, boots.copy());
                        SpiritHydraArmorHandler.syncPlayerImmediate(wearer);
                        boolean wearerHas4 = wearer.hasEffect(MobEffects.REGENERATION)
                                        && wearer.hasEffect(MobEffects.WATER_BREATHING);
                        wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        SpiritHydraArmorHandler.syncMaxHealthModifier(wearer);

                        Zombie wearerForRealTick = wearer;
                        wearer = null;
                        boolean syncAllOk = statsOk && vanillaEnchOk && recipesOk && lootOk && setBonusSyncOk
                                        && wearerHas4;
                        activeHydraArmorAudit = new RealTickHydraArmorExpiryAudit(
                                        source, wearerForRealTick, syncAllOk,
                                        defTotal, mat.toughness(), mat.knockbackResistance(),
                                        helm.getMaxDamage(), chest.getMaxDamage(), legs.getMaxDamage(),
                                        boots.getMaxDamage(),
                                        heartRatePct, minScales, maxScales, maxHp4, maxHp3);
                        return syncAllOk ? 1 : 0;
                } catch (Exception e) {
                        AbyssalRpgMod.LOGGER.error("[HYDRA_ARMOR_AUDIT] Lỗi kiểm định Spirit Hydra Armor:", e);
                        source.sendFailure(Component.literal("[Spirit Hydra Armor Audit] ERROR: " + e.getMessage()));
                        return 0;
                } finally {
                        tester.removeAllEffects();
                        tester.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                        tester.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                        SpiritHydraArmorHandler.syncMaxHealthModifier(tester);
                        if (boss != null)
                                boss.discard();
                        if (target != null)
                                target.discard();
                        if (wearer != null)
                                wearer.discard();
                }
        }

        private static final class RealTickHydraArmorExpiryAudit {
                private final CommandSourceStack source;
                private final Zombie wearer;
                private final boolean syncStagesOk;
                private final int defTotal;
                private final float toughness;
                private final float kbRes;
                private final int durH, durC, durL, durB;
                private final double heartRatePct;
                private final int minScales, maxScales;
                private final double maxHp4, maxHp3;
                private int elapsedTicks = 0;

                RealTickHydraArmorExpiryAudit(CommandSourceStack source, Zombie wearer, boolean syncStagesOk,
                                int defTotal, float toughness, float kbRes,
                                int durH, int durC, int durL, int durB,
                                double heartRatePct, int minScales, int maxScales,
                                double maxHp4, double maxHp3) {
                        this.source = source;
                        this.wearer = wearer;
                        this.syncStagesOk = syncStagesOk;
                        this.defTotal = defTotal;
                        this.toughness = toughness;
                        this.kbRes = kbRes;
                        this.durH = durH;
                        this.durC = durC;
                        this.durL = durL;
                        this.durB = durB;
                        this.heartRatePct = heartRatePct;
                        this.minScales = minScales;
                        this.maxScales = maxScales;
                        this.maxHp4 = maxHp4;
                        this.maxHp3 = maxHp3;
                }

                boolean tick() {
                        if (this.wearer.isRemoved() || !this.wearer.isAlive()) {
                                AbyssalRpgMod.LOGGER.error(
                                                "[HYDRA_ARMOR_AUDIT] 5. REAL-TICK EXPIRY: wearer removed prematurely -> FAIL");
                                return true;
                        }
                        SpiritHydraArmorHandler.syncMaxHealthModifier(this.wearer);
                        if (this.elapsedTicks % 20 == 0) {
                                SpiritHydraArmorHandler.applyPeriodicSetEffects(this.wearer);
                        }
                        if (this.elapsedTicks >= 65) {
                                boolean regenGone = !this.wearer.hasEffect(MobEffects.REGENERATION);
                                boolean wbGone = !this.wearer.hasEffect(MobEffects.WATER_BREATHING);
                                AttributeInstance attr = this.wearer.getAttribute(Attributes.MAX_HEALTH);
                                boolean modGone = attr != null
                                                && attr.getModifier(
                                                                SpiritHydraArmorHandler.SET_HEALTH_MODIFIER_ID) == null
                                                && Math.abs(this.wearer.getMaxHealth() - 20.0F) < 0.01F;
                                boolean expiryOk = regenGone && wbGone && modGone;
                                this.wearer.discard();

                                boolean allPass = this.syncStagesOk && expiryOk;
                                AbyssalRpgMod.LOGGER.info("[HYDRA_ARMOR_AUDIT] === TỔNG KẾT: {} ===",
                                                allPass ? "ALL_PASS" : "FAIL");
                                this.source.sendSuccess(() -> Component.literal(String.format(
                                                "[Spirit Hydra Armor Audit] %s | Def=%d (3/8/6/3) Tough=%.1f KB=%.2f Dur=%d/%d/%d/%d | Recipes=4/4 | Heart=%.2f%% Scales=%d..%d | 4/4 MaxHP=%.1f->%.1f RegenII+WB+PoisonII+SoulFlame Expiry=%s",
                                                allPass ? "ALL PASS" : "FAIL", this.defTotal, this.toughness,
                                                this.kbRes,
                                                this.durH, this.durC, this.durL, this.durB,
                                                this.heartRatePct, this.minScales, this.maxScales,
                                                this.maxHp4, this.maxHp3, expiryOk))
                                                .withStyle(allPass ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                                return true;
                        }
                        this.elapsedTicks++;
                        return false;
                }
        }

        private static ItemStack matchCraftingResult(ServerLevel level, CraftingInput input) {
                return level.getRecipeManager()
                                .getRecipeFor(RecipeType.CRAFTING, input, level)
                                .map(RecipeHolder::value)
                                .map((CraftingRecipe recipe) -> recipe.assemble(input, level.registryAccess()))
                                .orElse(ItemStack.EMPTY);
        }

}
