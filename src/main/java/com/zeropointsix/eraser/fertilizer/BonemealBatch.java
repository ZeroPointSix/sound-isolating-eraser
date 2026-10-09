package com.zeropointsix.eraser.fertilizer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/** Retains vanilla growth and Forge bonemeal hooks, committing only after protection checks. */
final class BonemealBatch {
    static boolean apply(ServerLevel level, BlockPos pos, Player player, boolean allowFullMeadow) {
        if (allowFullMeadow) for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++)
            if (!level.hasChunkAt(pos.offset(x, 0, z))) return false;
        int start = level.capturedBlockSnapshots.size();
        boolean capture = level.captureBlockSnapshots;
        List<BlockSnapshot> snapshots = new ArrayList<>();
        boolean success = false;
        boolean accepted = false;
        level.captureBlockSnapshots = true;
        try {
            for (int i = 0; i < FertilizerConfig.BONE_MEAL.get(); i++) {
                var current = level.getBlockState(pos);
                if (current.getBlock() instanceof CropBlock crop && crop.isMaxAge(current)) break;
                int hook = ForgeEventFactory.onApplyBonemeal(player, level, pos, current, new ItemStack(Items.BONE_MEAL));
                if (hook < 0) return false;
                if (hook > 0) { accepted = true; continue; }
                if (current.getBlock() instanceof BonemealableBlock growable
                        && growable.isValidBonemealTarget(level, pos, current, false)) {
                    if (growable.isBonemealSuccess(level, level.random, pos, current))
                        growable.performBonemeal(level, level.random, pos, current);
                    accepted = true;
                } else if (allowFullMeadow) accepted = true;
                else break;
            }
            snapshots.addAll(level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()));
            level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()).clear();
            level.captureBlockSnapshots = false;
            for (BlockSnapshot snapshot : snapshots)
                if (!level.mayInteract(player, snapshot.getPos()) || !level.getWorldBorder().isWithinBounds(snapshot.getPos())) return false;
            boolean canceled = snapshots.size() > 1
                    ? ForgeEventFactory.onMultiBlockPlace(player, snapshots, Direction.UP)
                    : snapshots.size() == 1 && ForgeEventFactory.onBlockPlace(player, snapshots.get(0), Direction.UP);
            if (canceled) return false;
            success = accepted;
            if (success) for (BlockSnapshot snapshot : snapshots) {
                var state = level.getBlockState(snapshot.getPos());
                state.onPlace(level, snapshot.getPos(), snapshot.getReplacedBlock(), false);
                level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
                        snapshot.getReplacedBlock(), state, snapshot.getFlag(), 512);
            }
            return success;
        } finally {
            if (level.capturedBlockSnapshots.size() > start) {
                snapshots.addAll(level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()));
                level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()).clear();
            }
            boolean restoring = level.restoringBlockSnapshots;
            level.captureBlockSnapshots = false;
            try {
                if (!success) {
                    level.restoringBlockSnapshots = true;
                    for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore(true, false);
                }
            } finally {
                level.restoringBlockSnapshots = restoring;
                level.captureBlockSnapshots = capture;
            }
        }
    }

    private BonemealBatch() {}
}
