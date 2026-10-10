package com.zeropointsix.eraser.eraser;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserBarrierBlock;
import com.zeropointsix.eraser.block.EraserWallBlock;
import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

public final class EraserPlacement {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static int height() {
        return Math.max(1, Math.min(ModMain.MAX_HEIGHT, CommonConfig.barrierHeight()));
    }

    public static boolean fits(Level level, Player player, ItemStack stack, List<BlockPos> bases, Direction face) {
        if (face == Direction.DOWN || bases.isEmpty() || bases.size() > 12) return false;
        if (player != null && (!player.isAlive() || player.isSpectator() || !player.getAbilities().mayBuild)) return false;
        if (player != null && !player.getAbilities().instabuild
                && stack.getMaxDamage() - stack.getDamageValue() < bases.size()) return false;
        int height = height();
        for (BlockPos base : bases) {
            BlockPos support = base.relative(face.getOpposite());
            if (bases.size() > 1 && (!level.hasChunkAt(support)
                    || !level.getBlockState(support).isFaceSturdy(level, support, face))) return false;
            for (int i = 0; i < height; i++) {
                BlockPos cell = base.above(i);
                if (level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell)
                        || !level.hasChunkAt(cell)) return false;
                if (player != null && (!level.mayInteract(player, cell)
                        || !player.mayUseItemAt(cell, face, stack))) return false;
                BlockState old = level.getBlockState(cell);
                if (!old.getFluidState().isEmpty() || old.hasBlockEntity()
                        || EraserWallBlock.isEraserWall(old) || (!old.isAir() && !old.canBeReplaced())) return false;
            }
            if (!level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(base.getX(), base.getY(), base.getZ(),
                            base.getX() + 1, base.getY() + height, base.getZ() + 1)).isEmpty()) return false;
        }
        return true;
    }

    public static boolean place(Level level, Player player, ItemStack stack, List<BlockPos> bases, Direction face) {
        if (level.isClientSide || !fits(level, player, stack, bases, face)) return false;
        List<BlockSnapshot> snapshots = new ArrayList<>();
        boolean committed = false;
        boolean outerCapture = level.captureBlockSnapshots;
        // ItemStack.useOn also captures placements. Own this transaction so protection
        // events fire once, and neither failed writes nor rollback enter its snapshot list.
        level.captureBlockSnapshots = false;
        try {
            for (BlockPos base : bases) {
                for (int i = 0; i < height(); i++) {
                    BlockPos cell = base.above(i);
                    snapshots.add(BlockSnapshot.create(level.dimension(), level, cell));
                    BlockState state = i == 0 ? ModBlocks.ERASER_ANCHOR.get().defaultBlockState()
                            .setValue(EraserAnchorBlock.FACING, face.getOpposite())
                            .setValue(EraserAnchorBlock.STROKE, EraserStroke.at(bases, base))
                            : ModBlocks.ERASER_BARRIER.get().defaultBlockState().setValue(EraserBarrierBlock.LEVEL, i);
                    if (!level.setBlock(cell, state, FLAGS)) return false;
                }
            }
            if (player != null && ForgeEventFactory.onMultiBlockPlace(player, snapshots, face)) return false;
            committed = true;
            for (BlockSnapshot snapshot : snapshots) {
                BlockPos cell = snapshot.getPos();
                level.blockUpdated(cell, level.getBlockState(cell).getBlock());
            }
            return true;
        } finally {
            try {
                if (!committed) {
                    boolean restoring = level.restoringBlockSnapshots;
                    level.restoringBlockSnapshots = true;
                    try {
                        for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore(true, false);
                    } finally {
                        level.restoringBlockSnapshots = restoring;
                    }
                }
            } finally {
                level.captureBlockSnapshots = outerCapture;
            }
        }
    }

    private EraserPlacement() { }
}
