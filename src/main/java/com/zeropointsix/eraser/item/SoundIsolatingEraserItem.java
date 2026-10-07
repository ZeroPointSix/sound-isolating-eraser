package com.zeropointsix.eraser.item;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.eraser.EraserMode;
import com.zeropointsix.eraser.eraser.EraserPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SoundIsolatingEraserItem extends Item {
    public SoundIsolatingEraserItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Direction face = context.getClickedFace();
        BlockPos base = context.getClickedPos().relative(face);
        var bases = EraserMode.read(stack).bases(base, player == null ? Direction.NORTH : player.getDirection());
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!EraserPlacement.place(level, player, stack, bases, face)) return reject(level, player, base);
        level.playSound(null, base, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
        if (player != null && !player.getAbilities().instabuild) {
            stack.hurtAndBreak(bases.size(), player, holder -> holder.broadcastBreakEvent(context.getHand()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int getMaxDamage(ItemStack stack) { return CommonConfig.durability(); }

    private static InteractionResult reject(Level level, Player player, BlockPos pos) {
        if (!level.isClientSide && player != null) {
            player.displayClientMessage(Component.translatable(
                    "item.sound_isolating_eraser.sound_isolating_eraser.no_space"), true);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        return InteractionResult.FAIL;
    }
}
