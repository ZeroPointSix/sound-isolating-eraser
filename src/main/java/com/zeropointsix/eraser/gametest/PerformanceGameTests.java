package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.shadow.FleeLightGoal;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerformanceGameTests {
    @GameTest(template = "shadow_arena", timeoutTicks = 100)
    public static void fleeLightSearchHasPerTickBudgets(GameTestHelper helper) {
        ShadowTanglerEntity mob = ModEntities.SHADOW_TANGLER.get().create(helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(12, 2, 12));
        mob.moveTo(origin, 0, 0);
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        helper.getLevel().addFreshEntity(mob);
        helper.setBlock(new BlockPos(12, 2, 12),
                Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        FleeLightGoal goal = new FleeLightGoal(mob);
        int[] visited = {0};
        helper.runAtTickTime(30, goal::start);
        for (int tick = 31; tick <= 70; tick++) {
            final int now = tick;
            helper.runAtTickTime(tick, () -> {
                goal.tick();
                helper.assertTrue(goal.lastSearchPositions() <= FleeLightGoal.POSITIONS_PER_TICK,
                        "position scan respects tick budget");
                helper.assertTrue(goal.lastPathTargets() <= FleeLightGoal.PATH_TARGETS_PER_TICK,
                        "multi-target pathfinder respects tick budget");
                visited[0] += goal.lastSearchPositions();
                if (now == 70) {
                    helper.assertTrue(visited[0] > FleeLightGoal.POSITIONS_PER_TICK,
                            "search continues beyond its first batch");
                    goal.stop();
                    mob.discard();
                    helper.succeed();
                }
            });
        }
    }

    private PerformanceGameTests() { }
}
