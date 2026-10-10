package com.zeropointsix.eraser.item;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.pill.PillEffects;
import com.zeropointsix.eraser.pill.PillProvider;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class EnhancementPillPackItem extends Item {
    public EnhancementPillPackItem() { super(new Item.Properties().durability(12)); }

    @Override
    public int getMaxDamage(ItemStack stack) { return CommonConfig.value(CommonConfig.PILL_PACK_USES, 12); }
    @Override
    public int getUseDuration(ItemStack stack) { return CommonConfig.value(CommonConfig.USE_DURATION, 32); }
    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.EAT; }
    @Override
    public boolean isEnchantable(ItemStack stack) { return false; }
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) { return false; }
    @Override
    public boolean isRepairable(ItemStack stack) { return false; }
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
    @Override
    public boolean isFoil(ItemStack stack) { return false; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getDamageValue() >= stack.getMaxDamage()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || !player.isAlive()
                || stack.getDamageValue() >= stack.getMaxDamage()
                || !player.getCapability(PillProvider.CAPABILITY).isPresent()) return stack;
        boolean consume = !player.getAbilities().instabuild || CommonConfig.value(CommonConfig.CONSUME_IN_CREATIVE, false);
        int damage = stack.getDamageValue() + (consume ? 1 : 0);
        PillEffects.consume(player, damage, stack.getMaxDamage());
        player.awardStat(Stats.ITEM_USED.get(this));
        if (damage >= stack.getMaxDamage()) {
            ItemStack empty = new ItemStack(ModItems.EMPTY_PILL_PACK.get());
            if (stack.hasCustomHoverName()) empty.setHoverName(stack.getHoverName());
            return empty;
        }
        stack.setDamageValue(damage);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.sound_isolating_eraser.pill_count",
                Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.sound_isolating_eraser.pill_warning").withStyle(ChatFormatting.GRAY));
    }
}
