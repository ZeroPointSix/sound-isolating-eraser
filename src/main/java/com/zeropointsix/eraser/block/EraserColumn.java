package com.zeropointsix.eraser.block;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared server-side column lifecycle helpers. Keeps linked destroy paths in one
 * place so anchor↔barrier removals do not re-enter each other, and so clearing
 * the upper cells is a single guarded pass instead of a recursive remove storm.
 */
final class EraserColumn {
    /**
     * Re-entrancy guard shared by anchor and barrier {@code onRemove}. Without
     * it, breaking one barrier removes the anchor, which removes every other
     * barrier, each of which tries to remove the (already gone) anchor again.
     */
    private static final ThreadLocal<Boolean> DESTROYING = ThreadLocal.withInitial(() -> false);

    private EraserColumn() {
    }

    static boolean isDestroying() {
        return Boolean.TRUE.equals(DESTROYING.get());
    }

    /**
     * Clears every barrier that still belongs to the column at {@code anchorPos}.
     * Safe to call while the anchor itself is being removed. Uses a full update
     * flag so adjacent fluids / redstone still react after the column vanishes.
     */
    static void clearBarriers(Level level, BlockPos anchorPos) {
        if (level.isClientSide || level.restoringBlockSnapshots || isDestroying()) {
            return;
        }
        DESTROYING.set(true);
        try {
            for (int i = 1; i < ModMain.MAX_HEIGHT; i++) {
                BlockPos target = anchorPos.above(i);
                BlockState above = level.getBlockState(target);
                if (above.getBlock() instanceof EraserBarrierBlock
                        && above.getValue(EraserBarrierBlock.LEVEL) == i) {
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        } finally {
            DESTROYING.set(false);
        }
    }

    /**
     * Removes the owning anchor (which clears the rest of the column). No-op when
     * a linked destroy is already in progress or the anchor is already gone.
     */
    static void destroyFromBarrier(Level level, BlockPos anchorPos) {
        if (level.isClientSide || level.restoringBlockSnapshots || isDestroying()) {
            return;
        }
        if (level.getBlockState(anchorPos).getBlock() instanceof EraserAnchorBlock) {
            level.removeBlock(anchorPos, false);
        }
    }
}
