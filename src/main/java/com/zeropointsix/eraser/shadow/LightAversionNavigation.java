package com.zeropointsix.eraser.shadow;

import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

public final class LightAversionNavigation extends GroundPathNavigation {
    public LightAversionNavigation(ShadowTanglerEntity mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new LightAversionNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }
}
