package com.zeropointsix.eraser.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class GravityJadePendantItem extends Item implements ICurioItem {
    public GravityJadePendantItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return "necklace".equals(context.identifier());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
