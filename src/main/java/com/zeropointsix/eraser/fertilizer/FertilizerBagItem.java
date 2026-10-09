package com.zeropointsix.eraser.fertilizer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class FertilizerBagItem extends Item {
    public FertilizerBagItem() { super(new Properties().durability(64).setNoRepair()); }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("FertilizerCapacity")
                ? Math.max(1, stack.getTag().getInt("FertilizerCapacity")) : FertilizerConfig.value(FertilizerConfig.CAPACITY);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()) return InteractionResult.FAIL;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        ServerLevel level = (ServerLevel) context.getLevel();
        ItemStack bag = context.getItemInHand();
        if (player.getCooldowns().isOnCooldown(this)
                || !level.mayInteract(player, context.getClickedPos())
                || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), bag)) return InteractionResult.FAIL;
        // Growth owns its placement transaction; the outer ItemStack transaction must not replay it.
        boolean capture = level.captureBlockSnapshots;
        level.captureBlockSnapshots = false;
        try {
            if (!FertilizerGrowth.use(level, context.getClickedPos(), player)) return InteractionResult.FAIL;
        } finally { level.captureBlockSnapshots = capture; }
        int capacity = getMaxDamage(bag);
        bag.getOrCreateTag().putInt("FertilizerCapacity", capacity);
        int used = bag.getDamageValue() + 1;
        if (used >= capacity) player.setItemInHand(context.getHand(), new ItemStack(FertilizerContent.EMPTY_BAG.get()));
        else bag.setDamageValue(used);
        player.getCooldowns().addCooldown(this, 4);
        return InteractionResult.CONSUME;
    }

    @Override public boolean isFoil(ItemStack stack) { return false; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean isBookEnchantable(ItemStack stack, ItemStack book) { return false; }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) { return false; }
}
