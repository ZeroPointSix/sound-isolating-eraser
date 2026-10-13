package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.shadow.FleeLightGoal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("qijie_performance")
@PrefixGameTestTemplate(false)
public final class FleeLightBenchmarkGameTests {
    @GameTest(template = "shadow_arena", timeoutTicks = 280)
    public static void concurrentFleeSearches(GameTestHelper helper) {
        List<FleeLightGoal> goals = new ArrayList<>();
        BlockPos center = new BlockPos(12, 2, 12);
        helper.setBlock(center, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        for (int i = 0; i < 8; i++) {
            BlockPos spawn = new BlockPos(9 + (i % 4) * 2, 2, 11 + (i / 4) * 2);
            var mob = helper.spawn(ModEntities.SHADOW_TANGLER.get(), spawn);
            mob.setNoAi(true);
            helper.assertTrue(!helper.getBlockState(spawn.below()).getCollisionShape(
                    helper.getLevel(), helper.absolutePos(spawn.below())).isEmpty(), "benchmark has solid ground");
            helper.assertTrue(mob.getY() == helper.absolutePos(spawn).getY()
                    && helper.getLevel().noCollision(mob, mob.getBoundingBox()), "benchmark mobs stand on the floor without overlap");
            // Freeze movement without disabling GroundPathNavigation's on-ground precondition.
            mob.setOnGround(true);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            mob.setHealth(200);
            mob.getRandom().setSeed(1000 + i);
            goals.add(new FleeLightGoal(mob));
            helper.runAtTickTime(30, () -> {
                BlockPos refuge = helper.absolutePos(new BlockPos(19, 2, 15));
                var path = mob.getNavigation().createPath(refuge, 0);
                helper.assertTrue(mob.onGround() && mob.brightnessAt(refuge) <= 6
                        && path != null && path.canReach(), "benchmark executes real reachable pathfinding");
            });
        }
        long[] samples = new long[200];
        // Includes empty, failed and resumed searches; same fixture can run on the baseline revision.
        for (int tick = 40; tick < 260; tick++) {
            final int index = tick - 40;
            helper.runAtTickTime(tick, () -> {
                if (index % 40 == 0) goals.forEach(FleeLightGoal::start);
                long start = System.nanoTime();
                goals.forEach(FleeLightGoal::tick);
                long elapsed = System.nanoTime() - start;
                if (index >= 20) samples[index - 20] = elapsed;
                if (index == 219) {
                    goals.forEach(FleeLightGoal::stop);
                    Arrays.sort(samples);
                    long sum = Arrays.stream(samples).sum();
                    long usedHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    System.out.printf(java.util.Locale.ROOT,
                            "QIJIE_FLEE_BENCH mobs=8 samples=200 mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f max_ms=%.3f heap_used_mib=%.1f%n",
                            sum / 200_000_000D, samples[99] / 1_000_000D, samples[189] / 1_000_000D,
                            samples[199] / 1_000_000D, usedHeap / 1048576D);
                    helper.succeed();
                }
            });
        }
    }

    private FleeLightBenchmarkGameTests() { }
}
