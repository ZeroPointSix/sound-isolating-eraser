package com.zeropointsix.eraser.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared behavior of the eraser wall cells: a dirt-strength, translucent,
 * full-collision film that mobs can see through but cannot cross. Mirrors the
 * overrides vanilla glass uses so the column behaves like thin glass.
 */
public abstract class EraserWallBlock extends Block {
    protected EraserWallBlock() {
        super(Properties.of()
                .sound(SoundType.GLASS)
                // Dirt-level 0.5/0.5 per the design spec. Baked at registration,
                // so it cannot come from Forge config (not loaded yet).
                .strength(0.5F, 0.5F)
                .noOcclusion()
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isValidSpawn((state, level, pos, type) -> false)
                .isViewBlocking((state, level, pos) -> false));
    }

    public static boolean isEraserWall(BlockState state) {
        return state.getBlock() instanceof EraserWallBlock;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return isEraserWall(adjacentState) || super.skipRendering(state, adjacentState, side);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
            PathComputationType type) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }
}
