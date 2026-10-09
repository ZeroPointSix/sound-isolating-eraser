package com.zeropointsix.eraser.shaxia;

import com.zeropointsix.eraser.item.ShaxiadaoItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class ShaxiaStacks {
    public static boolean isKnife(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShaxiadaoItem;
    }

    public static void normalize(ItemStack stack, boolean restoreMissing) {
        if (!isKnife(stack)) return;
        ListTag original = stack.getEnchantmentTags();
        ListTag fixed = new ListTag();
        boolean found = false;
        for (int i = 0; i < original.size(); i++) {
            CompoundTag entry = original.getCompound(i);
            if (ShaxiaEnchantments.ID.equals(entry.getString("id"))) {
                found = true;
            } else {
                fixed.add(entry.copy());
            }
        }
        if (found || restoreMissing) {
            CompoundTag innate = new CompoundTag();
            innate.putString("id", ShaxiaEnchantments.ID);
            innate.putShort("lvl", (short) 1);
            fixed.add(innate);
        }
        if (!fixed.equals(original)) stack.getOrCreateTag().put("Enchantments", fixed);
    }

    public static boolean active(ItemStack stack) {
        return isKnife(stack) && EnchantmentHelper.getItemEnchantmentLevel(
                ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get(), stack) > 0;
    }

    private ShaxiaStacks() {}
}
