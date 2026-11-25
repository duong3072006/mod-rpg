package com.abyssalrpg.client;

import com.abyssalrpg.client.hud.BossBarHudRenderer;
import com.abyssalrpg.client.hud.ExcaliburHudRenderer;
import com.abyssalrpg.client.hud.TogetsuHudRenderer;
import com.abyssalrpg.client.renderer.AltarEyeRenderer;
import com.abyssalrpg.client.renderer.CerberusArmorRenderer;
import com.abyssalrpg.client.renderer.CerberusGeoRenderer;
import com.abyssalrpg.client.renderer.CinderDragonArmorRenderer;
import com.abyssalrpg.client.renderer.CinderDragonGeoRenderer;
import com.abyssalrpg.client.renderer.CinderDragonHitboxPartRenderer;
import com.abyssalrpg.client.renderer.DualBladeGeoRenderer;
import com.abyssalrpg.client.renderer.ExcaliburGeoRenderer;
import com.abyssalrpg.client.renderer.HydraHitboxPartRenderer;
import com.abyssalrpg.client.renderer.JormungandrGeoRenderer;
import com.abyssalrpg.client.renderer.JormungandrHitboxPartRenderer;
import com.abyssalrpg.client.renderer.LeviathanGeoRenderer;
import com.abyssalrpg.client.renderer.NightmareArmorRenderer;
import com.abyssalrpg.client.renderer.NightmareHitboxPartRenderer;
import com.abyssalrpg.client.renderer.NightmareKnightGeoRenderer;
import com.abyssalrpg.client.renderer.SpiritHydraArmorRenderer;
import com.abyssalrpg.client.renderer.SpiritHydraGeoRenderer;
import com.abyssalrpg.client.renderer.TogetsuGeoRenderer;
import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.renderer.vfx.AbyssalOrbRenderer;
import com.abyssalrpg.client.renderer.vfx.DualBladeTrailRenderer;
import com.abyssalrpg.client.renderer.vfx.EnergySlashRenderer;
import com.abyssalrpg.client.renderer.vfx.ExcaliburVfxRenderer;
import com.abyssalrpg.client.renderer.vfx.LaserBeamRenderer;
import com.abyssalrpg.client.renderer.vfx.MagicCircleRenderer;
import com.abyssalrpg.client.renderer.vfx.NightmareBoneSpikeRenderer;
import com.abyssalrpg.client.renderer.vfx.SeveredHydraHeadRenderer;
import com.abyssalrpg.client.renderer.vfx.ShockwaveRingRenderer;
import com.abyssalrpg.client.renderer.vfx.TogetsuVfxRenderer;
import com.abyssalrpg.client.renderer.vfx.VenomProjectileRenderer;
import com.abyssalrpg.client.sculpt.SculptedBossRenderer;
import com.abyssalrpg.client.sculpt.SculptedCoreRigs;
import com.abyssalrpg.client.sculpt.SculptedMythicRigs;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.config.CinderDragonConfig;
import com.abyssalrpg.config.LeviathanConfig;
import com.abyssalrpg.config.NightmareKnightConfig;
import com.abyssalrpg.config.SpiritHydraConfig;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.entity.mythic.cerberus.CerberusForm;
import com.abyssalrpg.item.CerberusArmorItem;
import com.abyssalrpg.item.CinderDragonArmorItem;
import com.abyssalrpg.item.DualBladeItem;
import com.abyssalrpg.item.NightmareArmorItem;
import com.abyssalrpg.item.SpiritHydraArmorItem;
import com.abyssalrpg.item.TogetsuItem;
import com.abyssalrpg.network.CerberusVfxPayload;
import com.abyssalrpg.network.CinderDragonVfxPayload;
import com.abyssalrpg.network.DualBladeComboPayload;
import com.abyssalrpg.network.DualBladeDashVfxPayload;
import com.abyssalrpg.network.DualBladeVfxPayload;
import com.abyssalrpg.network.ExcaliburSyncPayload;
import com.abyssalrpg.network.HydraVfxPayload;
import com.abyssalrpg.network.JormungandrPathSyncPayload;
import com.abyssalrpg.network.LeviathanVfxPayload;
import com.abyssalrpg.network.NightmareVfxPayload;
import com.abyssalrpg.network.ScreenShakePayload;
import com.abyssalrpg.network.TogetsuSyncPayload;
import com.abyssalrpg.registry.ModBlockEntities;
import com.abyssalrpg.registry.ModBlocks;
import com.abyssalrpg.registry.ModEntities;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

public class AbyssalRpgClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register ALL 4 Core Bosses with High-Poly Smooth-Shaded Organic 3D Sculpted
        // Renderers (ZERO boxy cubes!)
        EntityRendererRegistry.register(ModEntities.ABYSSAL_KNIGHT_BOSS, ctx -> new SculptedBossRenderer<>(ctx,
                SculptedCoreRigs.createShadowFenrir(), "abyssal_knight_boss", 2.2f, 0xB855FF, 0.92f));
        EntityRendererRegistry.register(ModEntities.CRYPT_LICH_BOSS, ctx -> new SculptedBossRenderer<>(ctx,
                SculptedCoreRigs.createBroodmotherSpider(), "crypt_lich_boss", 2.4f, 0x2AF5D6, 0.95f));
        EntityRendererRegistry.register(ModEntities.MOLTEN_TITAN_BOSS, ctx -> new SculptedBossRenderer<>(ctx,
                SculptedCoreRigs.createVolcanicHydra(), "molten_titan_boss", 2.8f, 0xFF6B14, 0.92f));
        EntityRendererRegistry.register(ModEntities.CINDER_DRAGON, CinderDragonGeoRenderer::new);
        EntityRendererRegistry.register(ModEntities.CINDER_DRAGON_HITBOX_PART, CinderDragonHitboxPartRenderer::new);

        // Register ALL 7 Mythological Titan Bosses with High-Poly Smooth-Shaded Organic
        // 3D Sculpted Renderers!
        EntityRendererRegistry.register(ModEntities.QUETZALCOATL_BOSS, ctx -> new SculptedBossRenderer<>(ctx,
                SculptedMythicRigs.createQuetzalcoatl(), "quetzalcoatl_boss", 2.8f, 0x2BFFB8, 0.92f));
        EntityRendererRegistry.register(ModEntities.APEP_BOSS, ctx -> new SculptedBossRenderer<>(ctx,
                SculptedMythicRigs.createApep(), "apep_boss", 3.0f, 0x1BFF9E, 0.92f));
        EntityRendererRegistry.register(ModEntities.FENRIR_BOSS, ctx -> new SculptedBossRenderer<>(ctx,
                SculptedMythicRigs.createFenrir(), "fenrir_boss", 2.6f, 0xFF3812, 0.92f));
        // Spirit Hydra & Leviathan use dedicated GeckoLib 4.8.3 GeoEntityRenderer +
        // AutoGlowingGeoLayer!
        EntityRendererRegistry.register(ModEntities.HYDRA_BOSS, SpiritHydraGeoRenderer::new);
        EntityRendererRegistry.register(ModEntities.HYDRA_HITBOX_PART, HydraHitboxPartRenderer::new);
        EntityRendererRegistry.register(ModEntities.JORMUNGANDR_BOSS, JormungandrGeoRenderer::new);
        EntityRendererRegistry.register(ModEntities.JORMUNGANDR_HITBOX_PART, JormungandrHitboxPartRenderer::new);
        EntityRendererRegistry.register(ModEntities.VENOM_PROJECTILE, VenomProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.TIAMAT_BOSS, ctx -> new SculptedBossRenderer<>(ctx,
                SculptedMythicRigs.createTiamat(), "tiamat_boss", 3.4f, 0xFF2820, 0.92f));
        EntityRendererRegistry.register(ModEntities.LEVIATHAN_BOSS, LeviathanGeoRenderer::new);
        EntityRendererRegistry.register(ModEntities.CERBERUS, CerberusGeoRenderer::new);
        EntityRendererRegistry.register(ModEntities.NIGHTMARE_KNIGHT, NightmareKnightGeoRenderer::new);
        EntityRendererRegistry.register(ModEntities.NIGHTMARE_HITBOX_PART, NightmareHitboxPartRenderer::new);

        // Register all Custom 3D Rendered Boss Spell & VFX Entities!
        EntityRendererRegistry.register(ModEntities.MAGIC_CIRCLE, MagicCircleRenderer::new);
        EntityRendererRegistry.register(ModEntities.ENERGY_SLASH, EnergySlashRenderer::new);
        EntityRendererRegistry.register(ModEntities.ABYSSAL_ORB, AbyssalOrbRenderer::new);
        EntityRendererRegistry.register(ModEntities.LASER_BEAM, LaserBeamRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHOCKWAVE_RING, ShockwaveRingRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEVERED_HYDRA_HEAD, SeveredHydraHeadRenderer::new);
        EntityRendererRegistry.register(ModEntities.NIGHTMARE_BONE_SPIKE, NightmareBoneSpikeRenderer::new);

        BlockEntityRenderers.register(ModBlockEntities.ALTAR_EYE, AltarEyeRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MYTHIC_SUMMON_ALTAR, RenderType.cutout());
        HudRenderCallback.EVENT.register(BossBarHudRenderer::render);
        HudRenderCallback.EVENT.register(TogetsuHudRenderer::render);
        HudRenderCallback.EVENT.register(ExcaliburHudRenderer::render);
        TogetsuClientState.initKeybinds();

        // Đăng ký GeoItemRenderer 3D fullbright qua Fabric BuiltinItemRendererRegistry
        // (thuần client, 0 leak sang main)
        final DualBladeGeoRenderer[] dualBladeRendererHolder = new DualBladeGeoRenderer[1];
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.DUAL_KNIFE,
                (stack, mode, matrices, vertexConsumers, light, overlay) -> {
                    if (dualBladeRendererHolder[0] == null) {
                        dualBladeRendererHolder[0] = new DualBladeGeoRenderer();
                    }
                    dualBladeRendererHolder[0].renderByItem(stack, mode, matrices, vertexConsumers, light, overlay);
                });

        final TogetsuGeoRenderer[] togetsuRendererHolder = new TogetsuGeoRenderer[1];
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.TOGETSU,
                (stack, mode, matrices, vertexConsumers, light, overlay) -> {
                    if (togetsuRendererHolder[0] == null) {
                        togetsuRendererHolder[0] = new TogetsuGeoRenderer();
                    }
                    togetsuRendererHolder[0].renderByItem(stack, mode, matrices, vertexConsumers, light, overlay);
                });

        final ExcaliburGeoRenderer[] excaliburRendererHolder = new ExcaliburGeoRenderer[1];
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.EXCALIBUR,
                (stack, mode, matrices, vertexConsumers, light, overlay) -> {
                    if (excaliburRendererHolder[0] == null) {
                        excaliburRendererHolder[0] = new ExcaliburGeoRenderer();
                    }
                    excaliburRendererHolder[0].renderByItem(stack, mode, matrices, vertexConsumers, light, overlay);
                });
        DualBladeItem.clientIsOffhandOccupied = () -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p != null && !p.getOffhandItem().isEmpty();
        };
        TogetsuItem.clientSkillKeyName = TogetsuClientState::getSkillKeyDisplayName;
        TogetsuItem.clientFuseKeyName = TogetsuClientState::getFuseKeyDisplayName;
        TogetsuItem.clientColorKeyName = TogetsuClientState::getColorKeyDisplayName;

        // Bơm GeoArmorRenderer 3D + selective glowmask cho bộ 4 món Giáp Nightmare Knight (split-sourceSet an toàn 100%)
        NightmareArmorItem.clientRenderProviderFactory = () -> new GeoRenderProvider() {
            private NightmareArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    @Nullable T livingEntity, ItemStack itemStack, @Nullable EquipmentSlot equipmentSlot,
                    @Nullable HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new NightmareArmorRenderer();
                }
                return this.renderer;
            }
        };

        // Bơm GeoArmorRenderer 3D + AutoGlowingGeoLayer chọn lọc cho bộ 4 món Giáp Cinder Dragon
        CinderDragonArmorItem.clientRenderProviderFactory = () -> new GeoRenderProvider() {
            private CinderDragonArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    @Nullable T livingEntity, ItemStack itemStack, @Nullable EquipmentSlot equipmentSlot,
                    @Nullable HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new CinderDragonArmorRenderer();
                }
                return this.renderer;
            }
        };

        // Bơm GeoArmorRenderer 3D + AutoGlowingGeoLayer chọn lọc cho bộ 4 món Giáp Spirit Hydra
        SpiritHydraArmorItem.clientRenderProviderFactory = () -> new GeoRenderProvider() {
            private SpiritHydraArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    @Nullable T livingEntity, ItemStack itemStack, @Nullable EquipmentSlot equipmentSlot,
                    @Nullable HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new SpiritHydraArmorRenderer();
                }
                return this.renderer;
            }
        };

        // Bơm GeoArmorRenderer 3D + Emissive Layer chọn lọc cho bộ 4 món Giáp Cerberus
        CerberusArmorItem.clientRenderProviderFactory = () -> new GeoRenderProvider() {
            private CerberusArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    @Nullable T livingEntity, ItemStack itemStack, @Nullable EquipmentSlot equipmentSlot,
                    @Nullable HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new CerberusArmorRenderer();
                }
                return this.renderer;
            }
        };

        // Hiển thị ngay mô tả % rõ ràng trên Sách Enchant, Dual Knife, Nightmare Knight, Cinder Dragon, Spirit Hydra & Cerberus Armor
        ItemTooltipCallback.EVENT.register(DualBladeTooltipHandler::onItemTooltip);
        ItemTooltipCallback.EVENT.register(NightmareArmorTooltipHandler::onItemTooltip);
        ItemTooltipCallback.EVENT.register(CinderDragonArmorTooltipHandler::onItemTooltip);
        ItemTooltipCallback.EVENT.register(SpiritHydraArmorTooltipHandler::onItemTooltip);
        ItemTooltipCallback.EVENT.register(CerberusArmorTooltipHandler::onItemTooltip);

        // Render vệt kiếm thư pháp đôi Catmull-Rom (`DualBladeTrailRenderer`), Song Nguyệt (`TogetsuVfxRenderer`) & Excalibur (`ExcaliburVfxRenderer`)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            DualBladeTrailRenderer.renderWorld(context, DualBladeAnimManager.getClientTick());
            TogetsuVfxRenderer.renderWorld(context, TogetsuAnimManager.getClientTick());
            ExcaliburVfxRenderer.renderWorld(context, ExcaliburAnimManager.getClientTick());
        });

        ClientPlayNetworking.registerGlobalReceiver(TogetsuSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        TogetsuClientState.handleSyncPayload(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ExcaliburSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        ExcaliburClientState.handleSyncPayload(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(DualBladeComboPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        DualBladeClientState.handleComboPayload(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(DualBladeVfxPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        DualBladeClientState.handleVfxPayload(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(DualBladeDashVfxPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        DualBladeClientState.handleDashVfxPayload(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ScreenShakePayload.TYPE, (payload, context) -> context.client()
                .execute(() -> ClientRpgState.triggerShake(payload.intensity(), payload.durationTicks())));

        ClientPlayNetworking.registerGlobalReceiver(JormungandrPathSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null
                            && context.client().level
                                    .getEntity(payload.entityId()) instanceof JormungandrBossEntity jorm) {
                        jorm.applyClientPathSync(payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(NightmareVfxPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        spawnClientNightmareVfx(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(HydraVfxPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        spawnClientHydraVfx(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(LeviathanVfxPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        spawnClientLeviathanVfx(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(CerberusVfxPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        spawnClientCerberusVfx(context.client().level, payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(CinderDragonVfxPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level != null) {
                        spawnClientCinderDragonVfx(context.client().level, payload);
                    }
                }));

        // Smooth Damped Boss-Impact Camera Screen Shake + Dual Blade Hold-to-Slash &
        // Bleeding Droplets
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DualBladeClientState.tickClient(client);
            TogetsuClientState.tickClient(client);
            ExcaliburClientState.tickClient(client);

            LocalPlayer player = client.player;
            if (player == null || client.isPaused() || client.level == null)
                return;

            if (ClientRpgState.shakeTicks > 0) {
                float frac = (float) ClientRpgState.shakeTicks / Math.max(1, ClientRpgState.shakeMaxTicks);
                float amp = ClientRpgState.shakeIntensity * frac * 0.95f;
                float t = player.tickCount * 2.1f;
                float dYaw = Mth.sin(t * 1.7f) * amp;
                float dPitch = Mth.cos(t * 2.3f) * amp * 0.7f;
                player.setYRot(player.getYRot() + dYaw);
                player.setXRot(Mth.clamp(player.getXRot() + dPitch, -89.5f, 89.5f));
                ClientRpgState.shakeTicks--;
            }
        });
    }

    private static final DustParticleOptions NK_WARN_CRIMSON = new DustParticleOptions(
            new Vector3f(0.88f, 0.10f, 0.16f), 1.35f);
    private static final DustParticleOptions NK_WARN_ENRAGED = new DustParticleOptions(
            new Vector3f(1.00f, 0.30f, 0.10f), 1.50f);

    /**
     * Render VFX siêu nhẹ của Nightmare Knight ngay trên Client theo `vfxQuality` +
     * `particleMultiplier` (mặc định 0.20):
     * - Đòn chém chỉ sinh 10-16 hạt tia lửa/vệt chém gọn đúng lúc va chạm (không
     * khói dày).
     * - Cảnh báo & kỹ năng diện rộng giảm 75-85% số hạt so với v1/v2.
     */
    private static void spawnClientNightmareVfx(ClientLevel level, NightmareVfxPayload p) {
        float mult = NightmareKnightConfig.get().getEffectiveParticleMultiplier();
        if (mult <= 0.01f)
            return;

        RandomSource rng = level.random;
        DustParticleOptions warn = p.enraged() ? NK_WARN_ENRAGED : NK_WARN_CRIMSON;
        ParticleOptions spark = p.enraged() ? ParticleTypes.FLAME : ParticleTypes.CRIT;
        double dx = p.dirX();
        double dz = p.dirZ();
        double len = Math.hypot(dx, dz);
        if (len > 1e-4) {
            dx /= len;
            dz /= len;
        } else {
            dx = 0.0;
            dz = 1.0;
        }

        switch (p.kind()) {
            case NightmareVfxPayload.KIND_FAN_TELEGRAPH -> {
                int rays = Math.max(4, Math.round(9 * (0.45f + mult * 0.55f)));
                double halfRad = Math.toRadians(p.radius() > 16.0f ? 52.0 : 76.0);
                for (int i = 0; i < rays; i++) {
                    double frac = (i / (double) Math.max(1, rays - 1)) * 2.0 - 1.0;
                    double ang = frac * halfRad;
                    double rx = dx * Math.cos(ang) - dz * Math.sin(ang);
                    double rz = dx * Math.sin(ang) + dz * Math.cos(ang);
                    double r = p.radius() * 0.85;
                    level.addParticle(warn, p.x() + rx * r, p.y() + 0.24, p.z() + rz * r, 0.0, 0.01, 0.0);
                }
            }
            case NightmareVfxPayload.KIND_LINE_TELEGRAPH -> {
                int steps = Math.max(4, Math.round(10 * (0.40f + mult * 0.60f)));
                for (int i = 1; i <= steps; i++) {
                    double d = (p.radius() * i) / steps;
                    level.addParticle(warn, p.x() + dx * d, p.y() + 0.22, p.z() + dz * d, 0.0, 0.01, 0.0);
                }
            }
            case NightmareVfxPayload.KIND_CAGE_TELEGRAPH -> {
                int pts = Math.max(5, Math.round(10 * (0.50f + mult * 0.50f)));
                for (int i = 0; i < pts; i++) {
                    double ang = (Math.PI * 2.0 * i) / pts;
                    level.addParticle(warn, p.x() + Math.cos(ang) * p.radius(), p.y() + 0.22,
                            p.z() + Math.sin(ang) * p.radius(), 0.0, 0.01, 0.0);
                }
            }
            case NightmareVfxPayload.KIND_SLASH_ARC -> {
                // 10-14 tia lửa + 3 vệt chém gọn dọc cung kiếm đúng khoảnh khắc va chạm
                int count = Math.max(8, Math.round(14 * (0.72f + mult * 0.45f)));
                for (int i = 0; i < count; i++) {
                    double frac = (i / (double) Math.max(1, count - 1)) * 2.0 - 1.0;
                    double ang = frac * Math.toRadians(76.0);
                    double rx = dx * Math.cos(ang) - dz * Math.sin(ang);
                    double rz = dx * Math.sin(ang) + dz * Math.cos(ang);
                    double px = p.x() + rx * (p.radius() * 0.78);
                    double pz = p.z() + rz * (p.radius() * 0.78);
                    level.addParticle(spark, px, p.y() + 2.2 + (rng.nextDouble() - 0.5) * 0.6, pz,
                            rx * 0.16, 0.04, rz * 0.16);
                    if (i % 4 == 1) {
                        level.addParticle(ParticleTypes.SWEEP_ATTACK, px, p.y() + 2.1, pz, 0.0, 0.0, 0.0);
                    }
                }
            }
            case NightmareVfxPayload.KIND_THRUST_STREAK -> {
                int count = Math.max(8, Math.round(12 * (0.75f + mult * 0.45f)));
                for (int i = 1; i <= count; i++) {
                    double d = 3.0 + (p.radius() - 3.0) * (i / (double) count);
                    level.addParticle(spark, p.x() + dx * d, p.y() + 2.0 + (rng.nextDouble() - 0.5) * 0.3,
                            p.z() + dz * d, dx * 0.25, 0.02, dz * 0.25);
                }
            }
            case NightmareVfxPayload.KIND_CLAW_ARC -> {
                int count = Math.max(6, Math.round(10 * (0.70f + mult * 0.45f)));
                for (int i = 0; i < count; i++) {
                    double frac = (i / (double) Math.max(1, count - 1)) * 2.0 - 1.0;
                    double ang = frac * Math.toRadians(58.0);
                    double rx = dx * Math.cos(ang) - dz * Math.sin(ang);
                    double rz = dx * Math.sin(ang) + dz * Math.cos(ang);
                    level.addParticle(spark, p.x() + rx * 7.2, p.y() + 2.0, p.z() + rz * 7.2,
                            rx * 0.12, 0.03, rz * 0.12);
                }
                level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x() + dx * 7.0, p.y() + 2.1, p.z() + dz * 7.0, 0.0, 0.0,
                        0.0);
            }
            case NightmareVfxPayload.KIND_SLAM_IMPACT -> {
                int count = Math.max(8, Math.round(16 * (0.65f + mult * 0.50f)));
                BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.BLACKSTONE.defaultBlockState());
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    double r = Math.min(8.0, p.radius() * 0.42);
                    double vx = Math.cos(ang) * 0.18;
                    double vz = Math.sin(ang) * 0.18;
                    level.addParticle((i % 2 == 0) ? spark : debris,
                            p.x() + Math.cos(ang) * r, p.y() + 0.45, p.z() + Math.sin(ang) * r,
                            vx, 0.16 + rng.nextDouble() * 0.12, vz);
                }
            }
            case NightmareVfxPayload.KIND_SPIKE_ERUPT -> {
                int count = Math.max(2, Math.round(6 * (0.45f + mult * 0.55f)));
                BlockParticleOption bone = new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.BONE_BLOCK.defaultBlockState());
                for (int i = 0; i < count; i++) {
                    level.addParticle(bone, p.x() + (rng.nextDouble() - 0.5) * p.radius(),
                            p.y() + 0.6 + rng.nextDouble() * 1.4,
                            p.z() + (rng.nextDouble() - 0.5) * p.radius(),
                            (rng.nextDouble() - 0.5) * 0.14, 0.16, (rng.nextDouble() - 0.5) * 0.14);
                }
            }
            case NightmareVfxPayload.KIND_PHASE_BURST -> {
                int count = Math.max(10, Math.round(22 * (0.55f + mult * 0.55f)));
                BlockParticleOption rock = new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.BLACKSTONE.defaultBlockState());
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = 1.5 + rng.nextDouble() * 3.5;
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : rock,
                            p.x() + Math.cos(ang) * r, p.y() + 4.0 + rng.nextDouble() * 8.0,
                            p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.22, 0.14, Math.sin(ang) * 0.22);
                }
            }
            case NightmareVfxPayload.KIND_REALM_PULSE -> {
                int count = Math.max(4, Math.round(8 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = p.radius() * (0.45 + 0.50 * rng.nextDouble());
                    level.addParticle(warn, p.x() + Math.cos(ang) * r, p.y() + 0.9,
                            p.z() + Math.sin(ang) * r, 0.0, 0.02, 0.0);
                }
            }
            case NightmareVfxPayload.KIND_FLAME_PATCH -> {
                int count = Math.max(1, Math.round(3 * (0.35f + mult * 0.65f)));
                for (int i = 0; i < count; i++) {
                    level.addParticle(ParticleTypes.FLAME,
                            p.x() + (rng.nextDouble() - 0.5) * p.radius(),
                            p.y() + 0.15,
                            p.z() + (rng.nextDouble() - 0.5) * p.radius(),
                            0.0, 0.03, 0.0);
                }
            }
            default -> {
            }
        }
    }

    private static final DustParticleOptions HYDRA_CYAN = new DustParticleOptions(new Vector3f(0.30f, 0.96f, 1.00f),
            1.35f);
    private static final DustParticleOptions HYDRA_ENRAGED = new DustParticleOptions(new Vector3f(0.72f, 0.44f, 1.00f),
            1.45f);

    /**
     * Render VFX siêu nhẹ cho Spirit Hydra 4 đầu trực tiếp phía Client theo
     * `SpiritHydraConfig.vfxQuality`:
     * tận dụng tối đa vẻ đẹp glowmask fullbright, giới hạn số hạt gọn sắc để giữ
     * FPS/MSPT cực mượt.
     */
    private static void spawnClientHydraVfx(ClientLevel level, HydraVfxPayload p) {
        float mult = SpiritHydraConfig.get().getEffectiveParticleMultiplier();
        if (mult <= 0.01f)
            return;

        RandomSource rng = level.random;
        DustParticleOptions glowDust = p.enraged() ? HYDRA_ENRAGED : HYDRA_CYAN;
        ParticleOptions soul = p.enraged() ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL_FIRE_FLAME;

        double dx = p.dirX(), dy = p.dirY(), dz = p.dirZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux = len > 1e-4 ? dx / len : 0.0;
        double uz = len > 1e-4 ? dz / len : 1.0;

        switch (p.kind()) {
            case HydraVfxPayload.KIND_BITE_TELEGRAPH -> {
                int steps = Math.max(4, Math.round(7 * (0.5f + mult * 0.6f)));
                for (int i = 1; i <= steps; i++) {
                    double frac = i / (double) steps;
                    level.addParticle(glowDust, p.x() + dx * frac, p.y() + dy * frac, p.z() + dz * frac, 0.0, 0.01,
                            0.0);
                }
            }
            case HydraVfxPayload.KIND_BITE_IMPACT -> {
                int count = Math.max(6, Math.round(12 * (0.6f + mult * 0.5f)));
                level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x(), p.y(), p.z(), 0.0, 0.0, 0.0);
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    level.addParticle(soul, p.x() + Math.cos(ang) * 0.8, p.y() + 0.3, p.z() + Math.sin(ang) * 0.8,
                            Math.cos(ang) * 0.14, 0.08, Math.sin(ang) * 0.14);
                }
            }
            case HydraVfxPayload.KIND_BREATH_TELEGRAPH -> {
                int steps = Math.max(5, Math.round(8 * (0.5f + mult * 0.5f)));
                for (int side = -1; side <= 1; side += 2) {
                    double ang = side * Math.toRadians(28.0);
                    double rx = ux * Math.cos(ang) - uz * Math.sin(ang);
                    double rz = ux * Math.sin(ang) + uz * Math.cos(ang);
                    for (int i = 1; i <= steps; i++) {
                        double d = (p.radius() * i) / steps;
                        level.addParticle(glowDust, p.x() + rx * d, p.y() - 0.5, p.z() + rz * d, 0.0, 0.01, 0.0);
                    }
                }
            }
            case HydraVfxPayload.KIND_BREATH_STREAM -> {
                int count = Math.max(6, Math.round(14 * (0.55f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double frac = (i + rng.nextDouble()) / count;
                    double spread = (rng.nextDouble() - 0.5) * Math.toRadians(34.0);
                    double rx = ux * Math.cos(spread) - uz * Math.sin(spread);
                    double rz = ux * Math.sin(spread) + uz * Math.cos(spread);
                    double d = 1.2 + frac * p.radius();
                    level.addParticle((i % 2 == 0) ? soul : glowDust,
                            p.x() + rx * d, p.y() - frac * 2.2, p.z() + rz * d,
                            rx * 0.18, -0.02, rz * 0.18);
                }
            }
            case HydraVfxPayload.KIND_SPIT_CHARGE -> {
                int count = Math.max(3, Math.round(6 * (0.5f + mult * 0.5f)));
                for (int i = 0; i < count; i++) {
                    level.addParticle(glowDust,
                            p.x() + (rng.nextDouble() - 0.5) * 1.2,
                            p.y() + (rng.nextDouble() - 0.5) * 1.2,
                            p.z() + (rng.nextDouble() - 0.5) * 1.2,
                            0.0, 0.04, 0.0);
                }
            }
            case HydraVfxPayload.KIND_SLAM_TELEGRAPH -> {
                int rays = Math.max(5, Math.round(9 * (0.5f + mult * 0.5f)));
                for (int i = 0; i < rays; i++) {
                    double frac = (i / (double) Math.max(1, rays - 1)) * 2.0 - 1.0;
                    double ang = frac * Math.toRadians(75.0);
                    double rx = ux * Math.cos(ang) - uz * Math.sin(ang);
                    double rz = ux * Math.sin(ang) + uz * Math.cos(ang);
                    level.addParticle(glowDust, p.x() + rx * p.radius() * 0.85, p.y() + 0.35,
                            p.z() + rz * p.radius() * 0.85, 0.0, 0.02, 0.0);
                }
            }
            case HydraVfxPayload.KIND_SLAM_IMPACT, HydraVfxPayload.KIND_WRATH_IMPACT -> {
                int count = Math.max(10, Math.round(20 * (0.55f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    double r = p.radius() * 0.55;
                    level.addParticle((i % 2 == 0) ? soul : glowDust,
                            p.x() + Math.cos(ang) * r, p.y() + 0.6, p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.22, 0.14, Math.sin(ang) * 0.22);
                }
            }
            case HydraVfxPayload.KIND_HEAD_SEVER, HydraVfxPayload.KIND_HEAD_REGROW -> {
                int count = Math.max(8, Math.round(16 * (0.6f + mult * 0.5f)));
                for (int i = 0; i < count; i++) {
                    double vx = (rng.nextDouble() - 0.5) * 0.28 + ux * 0.12;
                    double vy = 0.08 + rng.nextDouble() * 0.20;
                    double vz = (rng.nextDouble() - 0.5) * 0.28 + uz * 0.12;
                    level.addParticle((i % 2 == 0) ? ParticleTypes.SOUL : soul,
                            p.x(), p.y(), p.z(), vx, vy, vz);
                }
            }
            case HydraVfxPayload.KIND_NOVA_RING, HydraVfxPayload.KIND_WRATH_CHARGE,
                    HydraVfxPayload.KIND_PHASE_PULSE -> {
                int pts = Math.max(10, Math.round(22 * (0.5f + mult * 0.55f)));
                for (int i = 0; i < pts; i++) {
                    double ang = (Math.PI * 2.0 * i) / pts;
                    level.addParticle((i % 2 == 0) ? soul : glowDust,
                            p.x() + Math.cos(ang) * p.radius(), p.y() + 0.35, p.z() + Math.sin(ang) * p.radius(),
                            0.0, 0.04, 0.0);
                }
            }
            case HydraVfxPayload.KIND_CHAIN_LINE -> {
                int steps = Math.max(6, Math.round(10 * (0.55f + mult * 0.5f)));
                for (int i = 1; i <= steps; i++) {
                    double frac = i / (double) steps;
                    level.addParticle((i % 2 == 0) ? glowDust : soul,
                            p.x() + dx * frac, p.y() + dy * frac, p.z() + dz * frac, 0.0, 0.01, 0.0);
                }
            }
            case HydraVfxPayload.KIND_DEATH_BURST -> {
                int count = Math.max(8, Math.round(14 * (0.55f + mult * 0.5f)));
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = rng.nextDouble() * p.radius();
                    level.addParticle(ParticleTypes.SOUL, p.x() + Math.cos(ang) * r,
                            p.y() + rng.nextDouble() * 3.0, p.z() + Math.sin(ang) * r, 0.0, 0.09, 0.0);
                }
            }
            default -> {
            }
        }
    }

    private static final DustParticleOptions LEV_AQUA = new DustParticleOptions(new Vector3f(0.42f, 0.96f, 0.92f),
            1.40f);
    private static final DustParticleOptions LEV_FLAME = new DustParticleOptions(new Vector3f(1.00f, 0.45f, 0.12f),
            1.45f);
    private static final DustParticleOptions LEV_CRIMSON = new DustParticleOptions(new Vector3f(1.00f, 0.18f, 0.12f),
            1.55f);

    /**
     * Render VFX gọn nhẹ phía Client cho Boss Leviathan v2 theo
     * `LeviathanConfig.vfxQuality` & `particleMultiplier`:
     * thay thế toàn bộ vòng lặp `ServerLevel.sendParticles`, giữ cảnh báo rõ nét và
     * tôn glowmask fullbright.
     */
    private static void spawnClientLeviathanVfx(ClientLevel level, LeviathanVfxPayload p) {
        float mult = LeviathanConfig.get().getEffectiveParticleMultiplier();
        if (mult <= 0.01f)
            return;

        RandomSource rng = level.random;
        DustParticleOptions primaryDust = p.enraged() ? LEV_CRIMSON : LEV_AQUA;
        ParticleOptions ember = p.enraged() ? ParticleTypes.FLAME : ParticleTypes.BUBBLE_POP;
        double dx = p.dirX(), dy = p.dirY(), dz = p.dirZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux = len > 1e-4 ? dx / len : 0.0;
        double uy = len > 1e-4 ? dy / len : 0.0;
        double uz = len > 1e-4 ? dz / len : 1.0;

        switch (p.kind()) {
            case LeviathanVfxPayload.KIND_TAIL_TELEGRAPH -> {
                int rays = Math.max(5, Math.round(9 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < rays; i++) {
                    double frac = (i / (double) Math.max(1, rays - 1)) * 2.0 - 1.0;
                    double ang = frac * 1.05;
                    double rx = ux * Math.cos(ang) - uz * Math.sin(ang);
                    double rz = ux * Math.sin(ang) + uz * Math.cos(ang);
                    level.addParticle(p.enraged() ? LEV_CRIMSON : LEV_FLAME,
                            p.x() + rx * p.radius(), p.y(), p.z() + rz * p.radius(), 0.0, 0.02, 0.0);
                }
            }
            case LeviathanVfxPayload.KIND_TAIL_IMPACT, LeviathanVfxPayload.KIND_MELEE_SWEEP -> {
                int count = Math.max(6, Math.round(12 * (0.55f + mult * 0.50f)));
                level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x(), p.y() + 0.8, p.z(), 0.0, 0.0, 0.0);
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    double r = Math.min(8.0, p.radius() * 0.45);
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : ParticleTypes.SPLASH,
                            p.x() + Math.cos(ang) * r, p.y() + 0.5, p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.18, 0.10, Math.sin(ang) * 0.18);
                }
            }
            case LeviathanVfxPayload.KIND_BITE_TELEGRAPH, LeviathanVfxPayload.KIND_BEAM_TELEGRAPH -> {
                int steps = Math.max(5, Math.round(11 * (0.45f + mult * 0.55f)));
                for (int i = 1; i <= steps; i++) {
                    double d = (p.radius() * i) / steps;
                    level.addParticle(primaryDust,
                            p.x() + ux * d, p.y() + uy * d, p.z() + uz * d, 0.0, 0.01, 0.0);
                }
            }
            case LeviathanVfxPayload.KIND_BITE_DASH, LeviathanVfxPayload.KIND_TELEPORT_SPLASH -> {
                int count = Math.max(5, Math.round(10 * (0.50f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = rng.nextDouble() * p.radius();
                    level.addParticle((i % 2 == 0) ? ParticleTypes.SPLASH : ember,
                            p.x() + Math.cos(ang) * r, p.y() + rng.nextDouble() * 2.4, p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.14, 0.08, Math.sin(ang) * 0.14);
                }
            }
            case LeviathanVfxPayload.KIND_SUMMON_RING, LeviathanVfxPayload.KIND_WHIRLPOOL_RING,
                    LeviathanVfxPayload.KIND_ROAR_TELEGRAPH, LeviathanVfxPayload.KIND_ROAR_WAVE,
                    LeviathanVfxPayload.KIND_TIDAL_TELEGRAPH -> {
                int pts = Math.max(8, Math.round(18 * (0.45f + mult * 0.55f)));
                boolean fiery = (p.kind() == LeviathanVfxPayload.KIND_ROAR_TELEGRAPH
                        || p.kind() == LeviathanVfxPayload.KIND_ROAR_WAVE
                        || p.kind() == LeviathanVfxPayload.KIND_TIDAL_TELEGRAPH);
                for (int i = 0; i < pts; i++) {
                    double ang = (Math.PI * 2.0 * i) / pts;
                    level.addParticle(fiery ? (i % 2 == 0 ? LEV_FLAME : ParticleTypes.FLAME) : primaryDust,
                            p.x() + Math.cos(ang) * p.radius(), p.y() + 0.3, p.z() + Math.sin(ang) * p.radius(),
                            0.0, 0.03, 0.0);
                }
            }
            case LeviathanVfxPayload.KIND_WHIRLPOOL_SPIRAL -> {
                int pts = Math.max(8, Math.round(16 * (0.45f + mult * 0.55f)));
                double phase = level.getGameTime() * 0.28;
                for (int i = 0; i < pts; i++) {
                    double frac = (i + 1.0) / pts;
                    double rad = 3.0 + frac * p.radius();
                    double ang = phase + (i % 4) * (Math.PI * 0.5) + rad * 0.12;
                    level.addParticle((i % 2 == 0) ? ParticleTypes.SPLASH : LEV_FLAME,
                            p.x() + Math.cos(ang) * rad, p.y() + 0.5, p.z() + Math.sin(ang) * rad,
                            0.0, 0.06, 0.0);
                }
            }
            case LeviathanVfxPayload.KIND_STORM_CHARGE -> {
                int count = Math.max(5, Math.round(10 * (0.50f + mult * 0.50f)));
                for (int i = 0; i < count; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            p.x() + (rng.nextDouble() - 0.5) * p.radius(),
                            p.y() + (rng.nextDouble() - 0.5) * 3.0,
                            p.z() + (rng.nextDouble() - 0.5) * p.radius(),
                            0.0, 0.06, 0.0);
                }
            }
            case LeviathanVfxPayload.KIND_TIDAL_IMPACT, LeviathanVfxPayload.KIND_PHASE_CLIMAX -> {
                int count = Math.max(12, Math.round(24 * (0.55f + mult * 0.55f)));
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y() + 2.0, p.z(), 0.0, 0.0, 0.0);
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    double r = p.radius() * (0.35 + 0.55 * rng.nextDouble());
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : ParticleTypes.SPLASH,
                            p.x() + Math.cos(ang) * r, p.y() + 0.8 + rng.nextDouble() * 2.0,
                            p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.24, 0.18, Math.sin(ang) * 0.24);
                }
            }
            case LeviathanVfxPayload.KIND_PHASE_PULSE -> {
                int count = Math.max(6, Math.round(12 * (0.50f + mult * 0.50f)));
                double baseA = level.getGameTime() * 0.24;
                for (int i = 0; i < count; i++) {
                    double a = baseA + (Math.PI * 2.0 * i) / count;
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : primaryDust,
                            p.x() + Math.cos(a) * p.radius(),
                            p.y() + 1.8 + Math.sin(a * 2.0) * 1.4,
                            p.z() + Math.sin(a) * p.radius(),
                            0.0, 0.05, 0.0);
                }
            }
            case LeviathanVfxPayload.KIND_DEATH_BURST -> {
                int count = Math.max(6, Math.round(14 * (0.50f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = rng.nextDouble() * p.radius();
                    level.addParticle((i % 2 == 0) ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ember,
                            p.x() + Math.cos(ang) * r, p.y() + rng.nextDouble() * 3.0, p.z() + Math.sin(ang) * r,
                            0.0, 0.08, 0.0);
                }
            }
            default -> {
            }
        }
    }

    private static final DustParticleOptions CERB_ORANGE = new DustParticleOptions(new Vector3f(1.00f, 0.44f, 0.10f),
            1.35f);
    private static final DustParticleOptions CERB_ALPHA = new DustParticleOptions(new Vector3f(1.00f, 0.14f, 0.18f),
            1.40f);
    private static final DustParticleOptions CERB_FIRE = new DustParticleOptions(new Vector3f(1.00f, 0.62f, 0.12f),
            1.40f);
    private static final DustParticleOptions CERB_SHADOW = new DustParticleOptions(new Vector3f(0.72f, 0.22f, 1.00f),
            1.40f);

    /**
     * Render VFX siêu nhẹ phía Client cho Boss Cerberus v4 (cả Phase 1 & 3 cá thể
     * Phase 2):
     * thay thế 100% vòng lặp `ServerLevel.sendParticles`, tôn trọng
     * `CerberusConfig.vfxQuality` & `particleMultiplier`.
     */
    private static void spawnClientCerberusVfx(ClientLevel level, CerberusVfxPayload p) {
        float mult = CerberusConfig.get().getEffectiveParticleMultiplier();
        if (mult <= 0.01f)
            return;

        RandomSource rng = level.random;
        CerberusForm form = CerberusForm.byId(p.formId());
        DustParticleOptions roleDust = switch (form) {
            case ALPHA_HEAD -> CERB_ALPHA;
            case FIRE_HEAD -> CERB_FIRE;
            case SHADOW_HEAD -> CERB_SHADOW;
            default -> CERB_ORANGE;
        };
        ParticleOptions roleEmber = switch (form) {
            case ALPHA_HEAD -> ParticleTypes.CRIMSON_SPORE;
            case SHADOW_HEAD -> ParticleTypes.WITCH;
            default -> ParticleTypes.FLAME;
        };

        double dx = p.dirX(), dy = p.dirY(), dz = p.dirZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux = len > 1e-4 ? dx / len : 0.0;
        double uy = len > 1e-4 ? dy / len : 0.0;
        double uz = len > 1e-4 ? dz / len : 1.0;

        switch (p.kind()) {
            case CerberusVfxPayload.KIND_BITE_TELEGRAPH, CerberusVfxPayload.KIND_DASH_TELEGRAPH -> {
                int steps = Math.max(4, Math.round(9 * (0.45f + mult * 0.55f)));
                for (int i = 1; i <= steps; i++) {
                    double d = (p.radius() * i) / steps;
                    level.addParticle(roleDust, p.x() + ux * d, p.y() + 0.35, p.z() + uz * d, 0.0, 0.01, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_BITE_IMPACT -> {
                int count = Math.max(6, Math.round(12 * (0.55f + mult * 0.50f)));
                level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x(), p.y() + 0.4, p.z(), 0.0, 0.0, 0.0);
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    level.addParticle((i % 2 == 0) ? roleEmber : ParticleTypes.CRIT,
                            p.x() + Math.cos(ang) * 0.7, p.y() + 0.25, p.z() + Math.sin(ang) * 0.7,
                            Math.cos(ang) * 0.14, 0.06, Math.sin(ang) * 0.14);
                }
            }
            case CerberusVfxPayload.KIND_BREATH_TELEGRAPH -> {
                int steps = Math.max(4, Math.round(8 * (0.45f + mult * 0.55f)));
                for (int side = -1; side <= 1; side += 2) {
                    double ang = side * Math.toRadians(27.0);
                    double rx = ux * Math.cos(ang) - uz * Math.sin(ang);
                    double rz = ux * Math.sin(ang) + uz * Math.cos(ang);
                    for (int i = 1; i <= steps; i++) {
                        double d = (p.radius() * i) / steps;
                        level.addParticle(ParticleTypes.SMOKE, p.x() + rx * d, p.y() - 0.8, p.z() + rz * d, 0.0, 0.02,
                                0.0);
                    }
                }
            }
            case CerberusVfxPayload.KIND_BREATH_STREAM -> {
                int count = Math.max(6, Math.round(14 * (0.50f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double frac = (i + rng.nextDouble()) / count;
                    double spread = (rng.nextDouble() - 0.5) * Math.toRadians(48.0);
                    double rx = ux * Math.cos(spread) - uz * Math.sin(spread);
                    double rz = ux * Math.sin(spread) + uz * Math.cos(spread);
                    double d = 1.0 + frac * p.radius();
                    level.addParticle((i % 3 == 0) ? roleDust : ParticleTypes.FLAME,
                            p.x() + rx * d, p.y() - frac * Math.max(0.5, dy), p.z() + rz * d,
                            rx * 0.20, -0.02, rz * 0.20);
                }
            }
            case CerberusVfxPayload.KIND_SLAM_TELEGRAPH -> {
                int rays = Math.max(5, Math.round(9 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < rays; i++) {
                    double frac = (i / (double) Math.max(1, rays - 1)) * 2.0 - 1.0;
                    double ang = frac * 0.72;
                    double rx = ux * Math.cos(ang) - uz * Math.sin(ang);
                    double rz = ux * Math.sin(ang) + uz * Math.cos(ang);
                    level.addParticle(roleDust, p.x() + rx * p.radius(), p.y() + 0.35, p.z() + rz * p.radius(), 0.0,
                            0.02, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_SLAM_IMPACT, CerberusVfxPayload.KIND_POUNCE_IMPACT -> {
                int count = Math.max(10, Math.round(22 * (0.55f + mult * 0.50f)));
                BlockParticleOption rock = new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.BASALT.defaultBlockState());
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    double r = p.radius() * (0.30 + 0.55 * rng.nextDouble());
                    level.addParticle((i % 3 == 0) ? roleEmber : rock,
                            p.x() + Math.cos(ang) * r, p.y() + 0.4, p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.22, 0.16 + rng.nextDouble() * 0.12, Math.sin(ang) * 0.22);
                }
            }
            case CerberusVfxPayload.KIND_HOWL_CHARGE, CerberusVfxPayload.KIND_NOVA_CHARGE -> {
                int pts = Math.max(6, Math.round(12 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < pts; i++) {
                    double ang = (Math.PI * 2.0 * i) / pts + level.getGameTime() * 0.18;
                    level.addParticle((i % 2 == 0) ? roleEmber : roleDust,
                            p.x() + Math.cos(ang) * p.radius(), p.y() + 0.6 + rng.nextDouble() * 1.4,
                            p.z() + Math.sin(ang) * p.radius(), 0.0, 0.06, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_DASH_TRAIL -> {
                int count = Math.max(4, Math.round(8 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    level.addParticle((i % 2 == 0) ? roleEmber : roleDust,
                            p.x() + (rng.nextDouble() - 0.5) * p.radius(),
                            p.y() + 0.5 + rng.nextDouble() * 1.2,
                            p.z() + (rng.nextDouble() - 0.5) * p.radius(),
                            -ux * 0.12, 0.03, -uz * 0.12);
                }
            }
            case CerberusVfxPayload.KIND_NOVA_RING -> {
                int pts = Math.max(12, Math.round(24 * (0.50f + mult * 0.55f)));
                for (int i = 0; i < pts; i++) {
                    double ang = (Math.PI * 2.0 * i) / pts;
                    level.addParticle((i % 3 == 0) ? CERB_FIRE : ParticleTypes.FLAME,
                            p.x() + Math.cos(ang) * p.radius(), p.y() + 0.35, p.z() + Math.sin(ang) * p.radius(),
                            0.0, 0.03, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_SHADOW_VANISH, CerberusVfxPayload.KIND_SHADOW_APPEAR -> {
                int count = Math.max(8, Math.round(16 * (0.55f + mult * 0.50f)));
                ParticleOptions main = (p.kind() == CerberusVfxPayload.KIND_SHADOW_VANISH)
                        ? ParticleTypes.SQUID_INK
                        : ParticleTypes.WITCH;
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = rng.nextDouble() * p.radius();
                    level.addParticle((i % 2 == 0) ? main : CERB_SHADOW,
                            p.x() + Math.cos(ang) * r, p.y() + 0.6 + rng.nextDouble() * 1.6, p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.12, 0.06, Math.sin(ang) * 0.12);
                }
            }
            case CerberusVfxPayload.KIND_SHADOW_WARN -> {
                int pts = Math.max(5, Math.round(10 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < pts; i++) {
                    double ang = (Math.PI * 2.0 * i) / pts + level.getGameTime() * 0.25;
                    level.addParticle(ParticleTypes.WITCH,
                            p.x() + Math.cos(ang) * p.radius(), p.y() + 0.5, p.z() + Math.sin(ang) * p.radius(),
                            0.0, 0.05, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_PHASE_SPIRAL -> {
                int pts = Math.max(6, Math.round(12 * (0.50f + mult * 0.50f)));
                double baseA = level.getGameTime() * 0.28;
                for (int i = 0; i < pts; i++) {
                    double a = baseA + (Math.PI * 2.0 * i) / pts;
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                            p.x() + Math.cos(a) * p.radius(), p.y() + uy, p.z() + Math.sin(a) * p.radius(),
                            0.0, 0.06, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_SPLIT_EXPLOSION, CerberusVfxPayload.KIND_DEATH_CLIMAX -> {
                int count = Math.max(10, Math.round(20 * (0.55f + mult * 0.50f)));
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y() + 1.5, p.z(), 0.0, 0.0, 0.0);
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = rng.nextDouble() * p.radius();
                    level.addParticle((i % 2 == 0) ? roleEmber : ParticleTypes.FLAME,
                            p.x() + Math.cos(ang) * r, p.y() + 1.0 + rng.nextDouble() * 2.0, p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.22, 0.14, Math.sin(ang) * 0.22);
                }
            }
            case CerberusVfxPayload.KIND_SPLIT_FIREBALL -> {
                int count = Math.max(5, Math.round(10 * (0.50f + mult * 0.50f)));
                for (int i = 0; i < count; i++) {
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : roleEmber,
                            p.x() + (rng.nextDouble() - 0.5) * 1.1,
                            p.y() + 0.8 + (rng.nextDouble() - 0.5) * 1.1,
                            p.z() + (rng.nextDouble() - 0.5) * 1.1,
                            0.0, 0.03, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_ROLE_BURST, CerberusVfxPayload.KIND_HELLHOUND_SUMMON -> {
                int count = Math.max(6, Math.round(14 * (0.50f + mult * 0.50f)));
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = rng.nextDouble() * p.radius();
                    level.addParticle((i % 2 == 0) ? roleEmber : roleDust,
                            p.x() + Math.cos(ang) * r, p.y() + 0.6 + rng.nextDouble() * 1.2, p.z() + Math.sin(ang) * r,
                            0.0, 0.06, 0.0);
                }
            }
            case CerberusVfxPayload.KIND_FOOTSTEP_DUST -> {
                int count = Math.max(2, Math.round(5 * (0.45f + mult * 0.55f)));
                BlockParticleOption rock = new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.BASALT.defaultBlockState());
                for (int i = 0; i < count; i++) {
                    level.addParticle((i == 0) ? ParticleTypes.SMOKE : rock,
                            p.x() + (rng.nextDouble() - 0.5) * 0.8, p.y() + 0.15,
                            p.z() + (rng.nextDouble() - 0.5) * 0.8,
                            (rng.nextDouble() - 0.5) * 0.06, 0.05, (rng.nextDouble() - 0.5) * 0.06);
                }
            }
            case CerberusVfxPayload.KIND_FIRE_TRAIL -> {
                int count = Math.max(1, Math.round(3 * (0.40f + mult * 0.60f)));
                for (int i = 0; i < count; i++) {
                    level.addParticle(ParticleTypes.FLAME,
                            p.x() + (rng.nextDouble() - 0.5) * p.radius(), p.y() + 0.15,
                            p.z() + (rng.nextDouble() - 0.5) * p.radius(),
                            0.0, 0.02, 0.0);
                }
            }
            default -> {
            }
        }
    }

    private static final DustParticleOptions CINDER_EMBER = new DustParticleOptions(new Vector3f(1.00f, 0.48f, 0.10f),
            1.40f);
    private static final DustParticleOptions CINDER_WHITE_HOT = new DustParticleOptions(
            new Vector3f(1.00f, 0.92f, 0.56f), 1.50f);

    /**
     * Render VFX siêu nhẹ của Cinder Dragon ngay phía Client theo
     * `CinderDragonConfig.vfxQuality` (mặc định MINIMAL = 0.25):
     * 0 vòng lặp `ServerLevel.sendParticles` trên server; cảnh báo rõ nét, tối ưu
     * FPS cho máy tầm trung.
     */
    private static void spawnClientCinderDragonVfx(ClientLevel level, CinderDragonVfxPayload p) {
        float mult = CinderDragonConfig.get().getEffectiveParticleMultiplier();
        if (mult <= 0.01f)
            return;

        RandomSource rng = level.random;
        DustParticleOptions warn = p.enraged() ? CINDER_WHITE_HOT : CINDER_EMBER;
        double dx = p.dirX(), dy = p.dirY(), dz = p.dirZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux = len > 1e-4 ? dx / len : 0.0;
        double uy = len > 1e-4 ? dy / len : 0.0;
        double uz = len > 1e-4 ? dz / len : 1.0;

        switch (p.kind()) {
            case CinderDragonVfxPayload.KIND_BREATH_TELEGRAPH, CinderDragonVfxPayload.KIND_BEAM_CHARGE -> {
                int steps = Math.max(5, Math.round(11 * (0.45f + mult * 0.55f)));
                for (int i = 1; i <= steps; i++) {
                    double d = (p.radius() * 0.75 * i) / steps;
                    level.addParticle(warn, p.x() + ux * d, p.y() + uy * d, p.z() + uz * d, 0.0, 0.02, 0.0);
                }
            }
            case CinderDragonVfxPayload.KIND_BREATH_CONE -> {
                int count = Math.max(8, Math.round(16 * (0.50f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double frac = (i + rng.nextDouble()) / count;
                    double spread = (rng.nextDouble() - 0.5) * Math.toRadians(32.0);
                    double rx = ux * Math.cos(spread) - uz * Math.sin(spread);
                    double rz = ux * Math.sin(spread) + uz * Math.cos(spread);
                    double d = 1.4 + frac * p.radius();
                    level.addParticle((i % 3 == 0) ? warn : ParticleTypes.FLAME,
                            p.x() + rx * d, p.y() + uy * d + (rng.nextDouble() - 0.5) * 0.8, p.z() + rz * d,
                            rx * 0.22, -0.02, rz * 0.22);
                }
            }
            case CinderDragonVfxPayload.KIND_BEAM_STREAM -> {
                int count = Math.max(10, Math.round(18 * (0.55f + mult * 0.50f)));
                for (int i = 1; i <= count; i++) {
                    double d = (p.radius() * i) / count;
                    level.addParticle((i % 2 == 0) ? CINDER_WHITE_HOT : ParticleTypes.FLAME,
                            p.x() + ux * d, p.y() + uy * d, p.z() + uz * d,
                            ux * 0.14, uy * 0.14, uz * 0.14);
                }
            }
            case CinderDragonVfxPayload.KIND_FIREBALL_TARGET, CinderDragonVfxPayload.KIND_DIVE_TARGET,
                    CinderDragonVfxPayload.KIND_STOMP_RING, CinderDragonVfxPayload.KIND_SUPERNOVA_CHARGE -> {
                int pts = Math.max(8, Math.round(18 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < pts; i++) {
                    double ang = (Math.PI * 2.0 * i) / pts;
                    level.addParticle((i % 2 == 0) ? warn : ParticleTypes.FLAME,
                            p.x() + Math.cos(ang) * p.radius(), p.y() + 0.30, p.z() + Math.sin(ang) * p.radius(),
                            0.0, 0.03, 0.0);
                }
            }
            case CinderDragonVfxPayload.KIND_FAN_TELEGRAPH -> {
                int rays = Math.max(5, Math.round(10 * (0.45f + mult * 0.55f)));
                for (int i = 0; i < rays; i++) {
                    double frac = (i / (double) Math.max(1, rays - 1)) * 2.0 - 1.0;
                    double ang = frac * Math.toRadians(68.0);
                    double rx = ux * Math.cos(ang) - uz * Math.sin(ang);
                    double rz = ux * Math.sin(ang) + uz * Math.cos(ang);
                    level.addParticle(warn, p.x() + rx * p.radius() * 0.85, p.y() + 0.30,
                            p.z() + rz * p.radius() * 0.85, 0.0, 0.02, 0.0);
                }
            }
            case CinderDragonVfxPayload.KIND_SKYFALL_STRIP -> {
                int steps = Math.max(8, Math.round(14 * (0.50f + mult * 0.50f)));
                for (int i = -steps / 2; i <= steps / 2; i++) {
                    double d = (i / (double) Math.max(1, steps / 2)) * p.radius();
                    level.addParticle(warn, p.x() + ux * d, p.y() + 0.25, p.z() + uz * d, 0.0, 0.03, 0.0);
                }
            }
            case CinderDragonVfxPayload.KIND_WING_SLASH, CinderDragonVfxPayload.KIND_TAIL_ARC -> {
                int count = Math.max(7, Math.round(14 * (0.55f + mult * 0.50f)));
                level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x() + ux * 4.5, p.y() + 0.5, p.z() + uz * 4.5, 0.0, 0.0,
                        0.0);
                for (int i = 0; i < count; i++) {
                    double frac = (i / (double) Math.max(1, count - 1)) * 2.0 - 1.0;
                    double ang = frac * Math.toRadians(72.0);
                    double rx = ux * Math.cos(ang) - uz * Math.sin(ang);
                    double rz = ux * Math.sin(ang) + uz * Math.cos(ang);
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : warn,
                            p.x() + rx * p.radius() * 0.72, p.y() + 0.4, p.z() + rz * p.radius() * 0.72,
                            rx * 0.16, 0.04, rz * 0.16);
                }
            }
            case CinderDragonVfxPayload.KIND_FIREBALL_IMPACT, CinderDragonVfxPayload.KIND_DIVE_IMPACT,
                    CinderDragonVfxPayload.KIND_SUPERNOVA_BURST, CinderDragonVfxPayload.KIND_PHASE_BURST -> {
                int count = Math.max(10, Math.round(22 * (0.55f + mult * 0.55f)));
                if (p.kind() == CinderDragonVfxPayload.KIND_SUPERNOVA_BURST
                        || p.kind() == CinderDragonVfxPayload.KIND_DIVE_IMPACT) {
                    level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y() + 1.5, p.z(), 0.0, 0.0, 0.0);
                }
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count;
                    double r = p.radius() * (0.30 + 0.65 * rng.nextDouble());
                    level.addParticle((i % 2 == 0) ? ParticleTypes.FLAME : warn,
                            p.x() + Math.cos(ang) * r, p.y() + 0.5 + rng.nextDouble() * 1.8, p.z() + Math.sin(ang) * r,
                            Math.cos(ang) * 0.22, 0.14, Math.sin(ang) * 0.22);
                }
            }
            case CinderDragonVfxPayload.KIND_FIRE_PATCH -> {
                int count = Math.max(2, Math.round(4 * (0.40f + mult * 0.60f)));
                for (int i = 0; i < count; i++) {
                    level.addParticle(ParticleTypes.FLAME,
                            p.x() + (rng.nextDouble() - 0.5) * p.radius() * 1.5,
                            p.y() + 0.15,
                            p.z() + (rng.nextDouble() - 0.5) * p.radius() * 1.5,
                            0.0, 0.03, 0.0);
                }
            }
            case CinderDragonVfxPayload.KIND_DEATH_BURST -> {
                int count = Math.max(8, Math.round(16 * (0.50f + mult * 0.55f)));
                for (int i = 0; i < count; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = rng.nextDouble() * p.radius();
                    level.addParticle((i % 2 == 0) ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.FLAME,
                            p.x() + Math.cos(ang) * r, p.y() + rng.nextDouble() * 2.5, p.z() + Math.sin(ang) * r,
                            0.0, 0.08, 0.0);
                }
            }
            default -> {
            }
        }
    }
}
