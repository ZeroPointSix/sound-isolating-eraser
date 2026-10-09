package com.zeropointsix.eraser.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.zeropointsix.eraser.shaxia.ShaxiaCombat;
import com.zeropointsix.eraser.shaxia.ShaxiaConfig;
import com.zeropointsix.eraser.shaxia.ShaxiaStacks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

public final class ShaxiadaoItem extends SwordItem {
    public ShaxiadaoItem() {
        super(Tiers.DIAMOND, 0, -1.6F, new Properties().durability(1561));
    }

    @Override public ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        ShaxiaStacks.normalize(stack, true);
        return stack;
    }

    @Override public boolean isFoil(ItemStack stack) { return false; }

    @Override public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return ImmutableMultimap.of();
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                        ShaxiaConfig.number(ShaxiaConfig.BASE_DAMAGE, 4) - 1, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                        ShaxiaConfig.number(ShaxiaConfig.ATTACK_SPEED, 2.4) - 4, AttributeModifier.Operation.ADDITION));
    }

    @Override public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return action != ToolActions.SWORD_SWEEP && super.canPerformAction(stack, action);
    }

    @Override public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return !(enchantment instanceof DamageEnchantment) && enchantment != Enchantments.SWEEPING_EDGE
                && super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return EnchantmentHelper.getEnchantments(book).keySet().stream()
                .noneMatch(e -> e instanceof DamageEnchantment || e == Enchantments.SWEEPING_EDGE)
                && super.isBookEnchantable(stack, book);
    }

    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity owner, int slot, boolean selected) {
        if (!level.isClientSide) ShaxiaStacks.normalize(stack, ShaxiaConfig.flag(ShaxiaConfig.RESTORE_MISSING, true));
        super.inventoryTick(stack, level, owner, slot, selected);
    }

    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        ShaxiaCombat.begin(player, target, stack);
        return false;
    }

    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player) ShaxiaCombat.finish(player, target, stack);
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.sound_isolating_eraser.shaxiadao").withStyle(ChatFormatting.GRAY));
    }
}
