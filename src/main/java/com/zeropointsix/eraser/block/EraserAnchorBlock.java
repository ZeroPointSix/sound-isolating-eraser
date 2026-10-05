package com.zeropointsix.eraser.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Bottom cell of an eraser wall column. Owns the white mark, the facing of the
 * clicked surface, and the whole column's lifecycle: when the anchor leaves the
 * world the barriers it spawned are removed with it.
 */
public class EraserAnchorBlock extends EraserWallBlock {
    /** Direction from the anchor back toward the clicked surface; the white mark is drawn on that face. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public EraserAnchorBlock() {
        super();
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            EraserColumn.clearBarriers(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
