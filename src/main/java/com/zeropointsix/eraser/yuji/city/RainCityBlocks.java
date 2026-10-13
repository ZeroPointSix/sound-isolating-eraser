package com.zeropointsix.eraser.yuji.city;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class RainCityBlocks {
    public enum Mural implements StringRepresentable {
        YUCHI, SHASHENG, TOUDAO, EKOU, WANGYU, LIANGSHE, XIEYIN, QIYU, TANYU, CHENNU, SOUL, GOLDEN_BUDDHA;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Part implements StringRepresentable {
        LOWER, UPPER;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final class MuralBlock extends HorizontalDirectionalBlock {
        public static final EnumProperty<Mural> MURAL = EnumProperty.create("mural", Mural.class);
        public static final IntegerProperty TILE = IntegerProperty.create("tile", 0, 8);
        public MuralBlock() {
            super(BlockBehaviour.Properties.of().strength(1.5F));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MURAL, Mural.YUCHI).setValue(TILE, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, MURAL, TILE); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
        @Override public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    }
    public static final class WallCorpseBlock extends HorizontalDirectionalBlock {
        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
        public WallCorpseBlock() {
            super(BlockBehaviour.Properties.of().strength(1.5F));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LOWER));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, PART); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
        @Override public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    }
    public static final class HolyLightBlock extends Block {
        public static final BooleanProperty LIT = BooleanProperty.create("lit");
        public HolyLightBlock() {
            super(BlockBehaviour.Properties.of().strength(-1, 3600000).noCollission().lightLevel(s -> s.getValue(LIT) ? 15 : 0));
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LIT); }
        @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }
    }
    private RainCityBlocks() { }
}
