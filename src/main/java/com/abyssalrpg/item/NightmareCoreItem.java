package com.abyssalrpg.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Lõi Ác Mộng (`abyssalrpg:nightmare_core`) — vật phẩm hiếm (rơi đúng 5% từ Nightmare Knight Phase 2).
 * Có hiệu ứng ánh sáng tím-đỏ (`isFoil = true`) và kháng lửa/dung nham như vật phẩm Netherite/Boss Relic.
 */
public class NightmareCoreItem extends Item {
    public NightmareCoreItem() {
        super(new Item.Properties()
                .rarity(Rarity.EPIC)
                .fireResistant()
                .stacksTo(64));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}

