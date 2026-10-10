package com.zeropointsix.eraser.fertilizer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

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
            if (tree.level >= 3) return false;
            GrowthPlan plan = new GrowthPlan(level); plan.allowLogs(tree.trunk);
            try {
                Set<Long> logs = plan.tree(tree.root, tree.level + 1);
                if (!plan.commit(player)) return false;
                tree.trunk.addAll(logs); tree.level++; tree.touched = level.getGameTime(); data.put(tree);
                return true;
            } catch (GrowthPlan.UnsafePlacement ignored) { return false; }
        }
        if (!tree.doses.containsKey(player.getUUID()) && tree.doses.size() >= 256) return false;
        int dose = tree.doses.getOrDefault(player.getUUID(), 0) + 1;
        if (dose < FertilizerConfig.THRESHOLD.get()) {
            tree.doses.put(player.getUUID(), dose); tree.touched = level.getGameTime(); data.setDirty(); return true;
        }
        int count = FertilizerConfig.TREE_COUNT.get() - 1;
        if (!data.room(count)) return false;
        GrowthPlan plan = new GrowthPlan(level);
        List<FertilizerData.Plant> additions = new ArrayList<>();
        List<BlockPos> roots = new ArrayList<>(); roots.add(tree.root);
        // Stratified disk sampling gives a bounded, reproducible search for the complete grove.
        int radius = FertilizerConfig.GROVE_RADIUS.get();
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = -radius; x <= radius; x += 5) for (int z = -radius; z <= radius; z += 5)
            if (x * x + z * z <= radius * radius && x * x + z * z >= 25) candidates.add(tree.root.offset(x, 0, z));
        Collections.shuffle(candidates, new Random(level.random.nextLong()));
        try {
            for (BlockPos candidate : candidates) {
                if (additions.size() == count) break;
                BlockPos root = surface(level, candidate, 3);
                if (root == null || roots.stream().anyMatch(p -> p.distSqr(root) < 25)) continue;
                // A rejected tree may have partially filled its private buffer; use a fresh full batch on retry.
                FertilizerData.Plant p = new FertilizerData.Plant(root, true, level.getGameTime());
                p.trunk.addAll(plan.tree(root, 1)); p.grove = true;
                additions.add(p); roots.add(root);
            }
            if (additions.size() != count) return false;
            if (count > 0 && !plan.commit(player)) return false;
            tree.grove = true; tree.doses.clear(); tree.touched = level.getGameTime(); data.setDirty();
            additions.forEach(data::put);
            return true;
        } catch (GrowthPlan.UnsafePlacement ignored) { return false; }
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
