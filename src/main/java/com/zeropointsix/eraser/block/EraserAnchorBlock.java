package com.zeropointsix.eraser.block;

import com.zeropointsix.eraser.eraser.EraserStroke;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Bottom cell of an eraser wall column. Owns the white mark, the facing of the
 * clicked surface, and the whole column's lifecycle: when the anchor leaves the
 * world the barriers it spawned are removed with it.
 */
public class EraserAnchorBlock extends EraserWallBlock {
    /** Direction from the anchor back toward the clicked surface; the white mark is drawn on that face. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<EraserStroke> STROKE = EnumProperty.create("stroke", EraserStroke.class);

    public EraserAnchorBlock() {
        super();
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(STROKE, EraserStroke.POINT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STROKE);
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
