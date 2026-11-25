package com.abyssalrpg.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Trái Tim Cerberus (`abyssalrpg:cerberus_heart`) — nguyên liệu hiếm (rơi đúng 5% từ Boss Cerberus khi hạ gục Phase 2).
 * Có hiệu ứng ánh sáng glint (`isFoil = true`) và kháng lửa/dung nham.
 */
public final class CerberusHeartItem extends Item {
    public CerberusHeartItem() {
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

