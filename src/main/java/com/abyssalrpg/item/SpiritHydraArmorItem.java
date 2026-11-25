package com.abyssalrpg.item;

import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.registry.ModArmorMaterials;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Supplier;

/**
 * 4 món Giáp Spirit Hydra 3D GeckoLib 4.8.3 (`helmet`, `chestplate`, `leggings`, `boots`).
 * - An toàn 100% split sourceSet (`src/main` vs `src/client`): dùng `getRenderProvider()` trả `Object`.
 * - Độ bền chuẩn Netherite 37x (`407 / 592 / 555 / 481`), kháng lửa/dung nham (`fireResistant()`),
 *   sửa trên Đe bằng `abyssalrpg:spirit_hydra_scale` & tương thích đầy đủ Enchant giáp vanilla.
 * - Animation 3 cổ/đầu Hồn Xà phụ, vây mang mũ & đuôi 6 đốt được điều khiển tập trung 100% tại
 *   `SpiritHydraArmorModel.setCustomAnimations(...)` (single-source-of-truth).
 */
public final class SpiritHydraArmorItem extends ArmorItem implements GeoItem {
    /** Bơm từ `AbyssalRpgClient` trên Client (trả về `GeoRenderProvider`); luôn null trên Dedicated Server. */
    public static volatile Supplier<Object> clientRenderProviderFactory;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private volatile Object cachedRenderProvider;

    public SpiritHydraArmorItem(ArmorItem.Type type) {
        super(
                ModArmorMaterials.SPIRIT_HYDRA,
                type,
                new Item.Properties()
                        .durability(type.getDurability(ArmorConfig.get().hydraDurabilityMultiplier))
                        .rarity(Rarity.EPIC)
                        .fireResistant()
        );
    }

    @Override
    public Object getRenderProvider() {
        Object provider = this.cachedRenderProvider;
        if (provider != null) {
            return provider;
        }
        Supplier<Object> factory = clientRenderProviderFactory;
        if (factory != null) {
            provider = factory.get();
            if (provider != null) {
                this.cachedRenderProvider = provider;
                return provider;
            }
        }
        return GeoItem.super.getRenderProvider();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spirit_hydra_armor_idle", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}

