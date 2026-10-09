package com.zeropointsix.eraser.fertilizer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class FertilizerGrowth {
    private FertilizerGrowth() {}

    public static boolean use(ServerLevel level, BlockPos clicked, Player player) {
        if (!level.hasChunkAt(clicked) || player == null || !player.mayBuild()) return false;
        BlockState state = level.getBlockState(clicked);
        if (state.getBlock() instanceof CropBlock crop) {
            if (crop.isMaxAge(state)) return false;
            return BonemealBatch.apply(level, clicked, player, false);
        }
        if (state.is(BlockTags.SAPLINGS) && state.getBlock() instanceof SaplingBlock) return plant(level, clicked, player);
        FertilizerData data = FertilizerData.get(level);
        FertilizerData.Plant tree = state.is(FertilizerContent.LOG.get()) ? data.at(level, clicked) : null;
        if (tree != null && tree.tree) return feedTree(level, tree, player);
        BlockPos ground = clicked;
        if (state.getBlock() instanceof FlowerBlock || state.getBlock() instanceof TallGrassBlock || state.getBlock() instanceof DoublePlantBlock)
            ground = state.getBlock() instanceof DoublePlantBlock && level.getBlockState(clicked.below()).getBlock() instanceof DoublePlantBlock
                    ? clicked.below(2) : clicked.below();
        if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) return meadow(level, ground, player);
        if (state.getBlock() instanceof BonemealableBlock) return BonemealBatch.apply(level, clicked, player, false);
        return false;
    }

    public static boolean plant(ServerLevel level, BlockPos root, Player player) {
        FertilizerData data = FertilizerData.get(level);
        if (!data.room(1)) return false;
        GrowthPlan plan = new GrowthPlan(level);
        try {
            Set<Long> logs = plan.tree(root, 1);
            if (!plan.commit(player)) return false;
            FertilizerData.Plant p = new FertilizerData.Plant(root, true, level.getGameTime());
            p.trunk.addAll(logs);
            if (player != null) p.doses.put(player.getUUID(), 1);
            data.put(p);
            return true;
        } catch (GrowthPlan.UnsafePlacement ignored) { return false; }
    }

    private static boolean feedTree(ServerLevel level, FertilizerData.Plant tree, Player player) {
        FertilizerData data = FertilizerData.get(level);
        if (tree.grove) {
            // 已成林：继续施肥让整棵榕树升一级（更粗、更大，外圈再落气根），3 级封顶
            if (tree.level >= 3) return false;
            return growBanyan(level, data, tree, tree.level + 1, tree.seed, player);
        }
        if (!tree.doses.containsKey(player.getUUID()) && tree.doses.size() >= 256) return false;
        int dose = tree.doses.getOrDefault(player.getUUID(), 0) + 1;
        if (dose < FertilizerConfig.THRESHOLD.get()) {
            tree.doses.put(player.getUUID(), dose); tree.touched = level.getGameTime(); data.setDirty(); return true;
        }
        // 达到阈值：独木成林——原树长成榕树，主干加气根共 TREE_COUNT 根落地树干
        return growBanyan(level, data, tree, 1, level.random.nextLong(), player);
    }

    /** 在缓冲里按等级生长榕树，整批提交；任何必需部分被挡住都整体放弃，不扣量、不留残块。 */
    private static boolean growBanyan(ServerLevel level, FertilizerData data, FertilizerData.Plant tree, int tier, long seed, Player player) {
        GrowthPlan plan = new GrowthPlan(level);
        plan.allowLogs(tree.trunk);
        Set<Long> logs = new HashSet<>();
        boolean forming = !tree.grove;
        List<BanyanShape.Pillar> existing = null;
        if (!forming) {
            existing = new ArrayList<>();
            for (BlockPos p : tree.pillars) existing.add(new BanyanShape.Pillar(p.getX() - tree.root.getX(), p.getY() - tree.root.getY(), p.getZ() - tree.root.getZ()));
        }
        try {
            int wanted = FertilizerConfig.TREE_COUNT.get() - 1;
            List<BanyanShape.Pillar> pillars = BanyanShape.grow(banyanWorld(level, plan, tree.root, logs), tier, seed,
                    FertilizerConfig.GROVE_RADIUS.get(), existing, wanted);
            if (forming && pillars.size() != wanted) return false;
            if (!plan.commit(player)) return false;
            tree.grove = true; tree.level = tier; tree.seed = seed; tree.doses.clear();
            tree.pillars.clear();
            for (BanyanShape.Pillar p : pillars) tree.pillars.add(tree.root.offset(p.x(), p.y(), p.z()));
            tree.trunk.addAll(logs); tree.touched = level.getGameTime();
            data.put(tree);
            return true;
        } catch (GrowthPlan.UnsafePlacement ignored) { return false; }
    }

    private static BanyanShape.World banyanWorld(ServerLevel level, GrowthPlan plan, BlockPos root, Set<Long> logs) {
        return new BanyanShape.World() {
            @Override
            public int probe(int x, int y, int z) {
                BlockPos p = root.offset(x, y, z);
                if (!plan.writable(p)) return BanyanShape.BLOCKED;
                BlockState s = plan.get(p);
                if (s.is(FertilizerContent.LOG.get())) return plan.ownsLog(p) ? BanyanShape.LOG : BanyanShape.BLOCKED;
                if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.ROOTED_DIRT)) return BanyanShape.SOIL;
                if (!s.getFluidState().isEmpty() || level.getBlockEntity(p) != null) return BanyanShape.BLOCKED;
                if (s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS)) return BanyanShape.PASS;
                return BanyanShape.BLOCKED;
            }

            @Override
            public void put(int x, int y, int z, BanyanShape.Kind kind) {
                BlockPos p = root.offset(x, y, z);
                BlockState log = FertilizerContent.LOG.get().defaultBlockState();
                BlockState state = switch (kind) {
                    case LOG_X -> log.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                    case LOG_Y -> log;
                    case LOG_Z -> log.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
                    case LEAVES -> FertilizerContent.LEAVES.get().defaultBlockState();
                    case ROOTED_DIRT -> Blocks.ROOTED_DIRT.defaultBlockState();
                    case HANGING_ROOTS -> Blocks.HANGING_ROOTS.defaultBlockState();
                };
                plan.put(p, state);
                if (state.is(FertilizerContent.LOG.get())) logs.add(p.asLong());
            }
        };
    }

    private static BlockPos surface(ServerLevel level, BlockPos center, int delta) {
        for (int y = delta; y >= -delta; y--) {
            BlockPos root = center.above(y);
            if (!level.hasChunkAt(root) || !level.isInWorldBounds(root.below())) return null;
            BlockState soil = level.getBlockState(root.below());
            if ((soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.ROOTED_DIRT))
                    && level.getBlockState(root).canBeReplaced() && level.getFluidState(root).isEmpty()) return root;
        }
        return null;
    }

    private static boolean meadow(ServerLevel level, BlockPos ground, Player player) {
        FertilizerData data = FertilizerData.get(level);
        FertilizerData.Plant p = data.at(level, ground);
        if (p == null) {
            if (!data.room(1)) return false;
            p = new FertilizerData.Plant(ground, false, level.getGameTime());
        }
        if (!p.doses.containsKey(player.getUUID()) && p.doses.size() >= 256) return false;
        int dose = p.doses.getOrDefault(player.getUUID(), 0) + 1;
        if (dose < FertilizerConfig.THRESHOLD.get()) {
            // The dose is useful even if nearby grass is already fully grown.
            if (!BonemealBatch.apply(level, ground, player, true)) return false;
            p.doses.put(player.getUUID(), dose); p.touched = level.getGameTime(); data.put(p); return true;
        }
        GrowthPlan plan = new GrowthPlan(level);
        int radius = FertilizerConfig.MEADOW_RADIUS.get();
        try {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;
                BlockPos above = surface(level, ground.offset(x, 1, z), 3);
                if (above == null || !level.isEmptyBlock(above)) continue;
                plan.put(above.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                if (level.random.nextFloat() < 0.7F) {
                    BlockState plant = (level.random.nextInt(5) == 0
                            ? (level.random.nextBoolean() ? Blocks.POPPY : Blocks.DANDELION) : Blocks.GRASS).defaultBlockState();
                    plan.put(above, plant);
                }
            }
            if (!plan.commit(player)) return false;
            p.doses.remove(player.getUUID()); p.touched = level.getGameTime(); data.put(p); return true;
        } catch (GrowthPlan.UnsafePlacement ignored) { return false; }
    }
}
