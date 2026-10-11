package com.zeropointsix.eraser.eternalnight;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** The state schema is the supplied qihai art pack's contract, not a new asset. */
public final class NeonSignBlock extends HorizontalDirectionalBlock {
    public enum Color implements StringRepresentable {
        PINK, CYAN, PURPLE, RED, GREEN;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Color> COLOR = EnumProperty.create("color", Color.class);
    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, 3);

    public NeonSignBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5F).lightLevel(state -> 12).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(COLOR, Color.PINK).setValue(GLYPH, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR, GLYPH);
    }
}
