package com.zeropointsix.eraser.block;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Upper cells of an eraser wall column. LEVEL is the cell index counting up
 * from the anchor, so the owning anchor is always {@code pos.below(LEVEL)}.
 * Breaking any barrier removes the anchor, which removes the column; a barrier
 * that loses its anchor destroys itself instead of lingering as an orphan.
 */
public class EraserBarrierBlock extends EraserWallBlock {
    public static final IntegerProperty LEVEL =
            IntegerProperty.create("level", 1, ModMain.MAX_HEIGHT - 1);

    public EraserBarrierBlock() {
        super();
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    public static BlockPos anchorPosOf(BlockState state, BlockPos pos) {
        return pos.below(state.getValue(LEVEL));
    }

    private static boolean anchored(BlockGetter level, BlockState state, BlockPos pos) {
        return level.getBlockState(anchorPosOf(state, pos)).getBlock() instanceof EraserAnchorBlock;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
            boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !anchored(level, state, pos)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock,
            BlockPos sourcePos, boolean notify) {
        if (!level.isClientSide && !anchored(level, state, pos)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!anchored(level, state, pos)) {
            level.destroyBlock(pos, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockPos anchorPos = anchorPosOf(state, pos);
            if (level.getBlockState(anchorPos).getBlock() instanceof EraserAnchorBlock) {
                level.removeBlock(anchorPos, false);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
