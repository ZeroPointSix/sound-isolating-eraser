package com.zeropointsix.eraser.item;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserBarrierBlock;
import com.zeropointsix.eraser.block.EraserWallBlock;
import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The sound-isolating eraser. Clicking a surface draws a white mark and raises
 * a single translucent wall column of {@link ModMain#MAX_HEIGHT} cells starting
 * just off that surface. All five cells are checked atomically on the server:
 * if any cell cannot host the wall nothing is placed and no durability is spent.
 */
public class SoundIsolatingEraserItem extends Item {
    public SoundIsolatingEraserItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) {
            return reject(level, context.getPlayer(), context.getClickedPos());
        }
        BlockPos base = context.getClickedPos().relative(face);
        int height = columnHeight();
        for (int i = 0; i < height; i++) {
            BlockPos cell = base.above(i);
            if (level.isOutsideBuildHeight(cell) || !cellFits(level, cell, context)) {
                return reject(level, context.getPlayer(), context.getClickedPos());
            }
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction markFace = face.getOpposite();
        level.setBlock(base, ModBlocks.ERASER_ANCHOR.get().defaultBlockState()
                .setValue(EraserAnchorBlock.FACING, markFace), Block.UPDATE_ALL);
        for (int i = 1; i < height; i++) {
            level.setBlock(base.above(i), ModBlocks.ERASER_BARRIER.get().defaultBlockState()
                    .setValue(EraserBarrierBlock.LEVEL, i), Block.UPDATE_ALL);
        }
        level.playSound(null, base, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
        Player player = context.getPlayer();
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().hurtAndBreak(1, player,
                    holder -> holder.broadcastBreakEvent(context.getHand()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int getMaxDamage(net.minecraft.world.item.ItemStack stack) {
        return CommonConfig.ERASER_DURABILITY.get();
    }

    static int columnHeight() {
        return Math.max(1, Math.min(ModMain.MAX_HEIGHT, CommonConfig.BARRIER_HEIGHT.get()));
    }

    private static boolean cellFits(Level level, BlockPos cell, UseOnContext context) {
        BlockState existing = level.getBlockState(cell);
        if (!existing.isAir()) {
            if (!existing.getFluidState().isEmpty()
                    || EraserWallBlock.isEraserWall(existing)
                    || !existing.canBeReplaced()) {
                return false;
            }
        }
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(cell)).isEmpty();
    }

    private static InteractionResult reject(Level level, Player player, BlockPos pos) {
        if (level.isClientSide) {
            return InteractionResult.FAIL;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    "item.sound_isolating_eraser.sound_isolating_eraser.no_space"), true);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(),
                    SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        return InteractionResult.FAIL;
    }
}
