package com.zeropointsix.eraser.shaxia;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.item.ShaxiadaoItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ShaxiaEnchantments {
    public static final String ID = ModMain.MOD_ID + ":jijie_special_attack";
    public static final DeferredRegister<Enchantment> REGISTRY =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, ModMain.MOD_ID);
    public static final RegistryObject<Enchantment> JIJIE_SPECIAL_ATTACK =
            REGISTRY.register("jijie_special_attack", SpecialAttack::new);

    private static final class SpecialAttack extends Enchantment {
        private SpecialAttack() {
            super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        }

        @Override public int getMaxLevel() { return 1; }
        @Override public boolean canEnchant(ItemStack stack) { return stack.getItem() instanceof ShaxiadaoItem; }
        @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }
        @Override public boolean isDiscoverable() { return false; }
        @Override public boolean isTradeable() { return false; }
        @Override public boolean isAllowedOnBooks() { return false; }
        @Override public Component getFullname(int level) {
            return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.GRAY);
        }
        @Override protected boolean checkCompatibility(Enchantment other) {
            return !(other instanceof DamageEnchantment) && super.checkCompatibility(other);
        }
    }

    private ShaxiaEnchantments() {}
}
