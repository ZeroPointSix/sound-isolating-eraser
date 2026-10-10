package com.zeropointsix.eraser.shadow;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public final class LightAversionNodeEvaluator extends WalkNodeEvaluator {
    private final Map<Long, Integer> lightCache = new HashMap<>();
    private ShadowTanglerEntity shadow;

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        shadow = (ShadowTanglerEntity) mob;
        lightCache.clear();
    }

    @Override
    public void done() {
        lightCache.clear();
        shadow = null;
        super.done();
    }

    private int brightness(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return lightCache.computeIfAbsent(pos.asLong(), ignored -> shadow.brightnessAt(pos));
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter region, int x, int y, int z, Mob mob) {
        BlockPathTypes type = super.getBlockPathType(region, x, y, z, mob);
        if (shadow != null && (type == BlockPathTypes.WALKABLE || type == BlockPathTypes.OPEN)
                && !shadow.wantsToFlee() && brightness(x, y, z) >= 11) {
            return BlockPathTypes.BLOCKED;
        }
        return type;
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node current) {
        int count = super.getNeighbors(neighbors, current);
        for (int i = 0; i < count; i++) {
            Node node = neighbors[i];
            if (node.type != BlockPathTypes.WALKABLE && node.type != BlockPathTypes.OPEN) continue;
            int brightness = brightness(node.x, node.y, node.z);
            // Nodes are reused during one search; do not accumulate the same light penalty.
            node.costMalus = Math.max(node.costMalus, brightness >= 11 ? 16F : brightness >= 7 ? 8F : 0F);
        }
        return count;
    }
}
