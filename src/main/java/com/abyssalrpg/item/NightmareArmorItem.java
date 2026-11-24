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
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Supplier;

/**
 * 4 món Giáp Nightmare Knight 3D GeckoLib 4.8.3 (`helmet`, `chestplate`, `leggings`, `boots`).
 * - Kế thừa `ArmorItem` (MC 1.21.1 `Holder<ArmorMaterial>`) + implement `GeoItem`.
 * - An toàn 100% cho split sourceSet (`src/main` vs `src/client`): dùng `getRenderProvider()` kiểu `Object`
 *   chuẩn `SingletonGeoAnimatable` (không bao giờ import `GeoRenderProvider` hay `HumanoidModel` trong `src/main`).
 * - Kháng lửa/dung nham (`fireResistant()`), tương thích toàn bộ enchant giáp vanilla & sửa bằng `nightmare_armor_shard`.
 */
public final class NightmareArmorItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.nightmare_armor.idle");

    /** Bơm từ `AbyssalRpgClient` khi chạy Client (trả về instance `GeoRenderProvider`); luôn null trên Dedicated Server. */
    public static volatile Supplier<Object> clientRenderProviderFactory;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private volatile Object cachedRenderProvider;

    public NightmareArmorItem(ArmorItem.Type type) {
        super(
                ModArmorMaterials.NIGHTMARE_KNIGHT,
                type,
                new Item.Properties()
                        .durability(type.getDurability(ArmorConfig.get().durabilityMultiplier))
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
        controllers.add(new AnimationController<>(this, "nightmare_armor_idle", 0, state ->
                state.setAndContinue(IDLE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
