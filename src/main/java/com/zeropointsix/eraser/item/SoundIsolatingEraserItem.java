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
    /** Client + known-shape only; neighbors notified once after the column is written. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

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
        if (!columnFits(level, base, height)) {
            return reject(level, context.getPlayer(), context.getClickedPos());
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction markFace = face.getOpposite();
        level.setBlock(base, ModBlocks.ERASER_ANCHOR.get().defaultBlockState()
                .setValue(EraserAnchorBlock.FACING, markFace), PLACE_FLAGS);
        for (int i = 1; i < height; i++) {
            level.setBlock(base.above(i), ModBlocks.ERASER_BARRIER.get().defaultBlockState()
                    .setValue(EraserBarrierBlock.LEVEL, i), PLACE_FLAGS);
        }
        // One neighbor pass for the whole column instead of five UPDATE_ALL storms.
        for (int i = 0; i < height; i++) {
            BlockPos cell = base.above(i);
            level.blockUpdated(cell, level.getBlockState(cell).getBlock());
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

    /**
     * Atomic fit check: one entity query for the whole column AABB, plus per-cell
     * block/fluid/replaceability checks. Avoids five LivingEntity scans per click.
     */
    private static boolean columnFits(Level level, BlockPos base, int height) {
        for (int i = 0; i < height; i++) {
            BlockPos cell = base.above(i);
            if (level.isOutsideBuildHeight(cell) || !blockFits(level, cell)) {
                return false;
            }
        }
        AABB columnBox = new AABB(
                base.getX(), base.getY(), base.getZ(),
                base.getX() + 1.0D, base.getY() + height, base.getZ() + 1.0D);
        return level.getEntitiesOfClass(LivingEntity.class, columnBox).isEmpty();
    }

    private static boolean blockFits(Level level, BlockPos cell) {
        BlockState existing = level.getBlockState(cell);
        if (!existing.isAir()) {
            if (!existing.getFluidState().isEmpty()
                    || EraserWallBlock.isEraserWall(existing)
                    || !existing.canBeReplaced()) {
                return false;
            }
        }
        return true;
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
