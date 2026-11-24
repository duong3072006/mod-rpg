package com.abyssalrpg.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Trái Tim Hỏa Long (`abyssalrpg:cinder_dragon_heart`) — nguyên liệu hiếm (rơi đúng 5% từ Boss Cinder Dragon Phase 2).
 * Có hiệu ứng ánh sáng glint (`isFoil = true`) và kháng lửa/dung nham.
 */
public final class CinderDragonHeartItem extends Item {
    public CinderDragonHeartItem() {
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

