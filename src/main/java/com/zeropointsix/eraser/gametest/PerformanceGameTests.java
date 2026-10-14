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

@GameTestHolder("qijie_tests")
@PrefixGameTestTemplate(false)
public final class PerformanceGameTests {
    private static final BlockPos CENTER = new BlockPos(12, 2, 12);

    @GameTest(template = "shadow_arena", timeoutTicks = 100)
    public static void fleeLightSearchHasPerTickBudgets(GameTestHelper helper) {
        // Keep every reachable floor position bright so a successful path cannot reset the cursor.
        for (int x = 1; x < 24; x++) for (int z = 1; z < 24; z++) {
            helper.setBlock(new BlockPos(x, 2, z),
                    Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }
        ShadowTanglerEntity mob = stationaryShadow(helper);
        BlockPos origin = helper.absolutePos(CENTER);
        FleeLightGoal goal = new FleeLightGoal(mob);
        int[] firstCursor = {0};
        helper.runAtTickTime(30, goal::start);
        for (int tick = 31; tick <= 70; tick++) {
            final int now = tick;
            helper.runAtTickTime(tick, () -> {
                if (now == 32) mob.moveTo(origin.east(), 0, 0);
                if (now == 33) mob.moveTo(origin.east(9), 0, 0);
                goal.tick();
                assertBudgets(helper, goal);
                if (now == 31) {
                    firstCursor[0] = (Integer) searchState(goal, "searchCursor");
                    helper.assertTrue(firstCursor[0] > 0 && mob.getNavigation().isDone(),
                            "bright fixture leaves an unfinished search after its first batch");
                }
                if (now == 32) {
                    helper.assertTrue(origin.equals(searchState(goal, "searchOrigin")),
                            "crossing one block preserves this search's anchor");
                    helper.assertTrue((Integer) searchState(goal, "searchCursor")
                                    == firstCursor[0] + goal.lastSearchPositions()
                                    && goal.lastSearchPositions() > 0,
                            "the next batch advances instead of rescanning its first positions");
                }
                if (now == 33) {
                    helper.assertTrue(origin.east(9).equals(searchState(goal, "searchOrigin")),
                            "a displacement greater than eight blocks starts a fresh search");
                    helper.assertTrue((Integer) searchState(goal, "searchCursor") == goal.lastSearchPositions(),
                            "a fresh anchor starts at the first batch");
                }
                if (now == 70) {
                    goal.stop();
                    mob.discard();
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template = "shadow_arena", timeoutTicks = 400)
    public static void fleeLightSearchPassesSealedRefugeToReachLaterBatch(GameTestHelper helper) {
        // Light the exterior so the sealed room cannot cast an accessible near shadow.
        for (int x = 7; x <= 11; x++) for (int z = 10; z <= 14; z++) {
            helper.setBlock(new BlockPos(x, 2, z),
                    Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }
        for (int x = 8; x <= 10; x++) for (int y = 1; y <= 4; y++) for (int z = 11; z <= 13; z++) {
            boolean shell = x == 8 || x == 10 || y == 1 || y == 4 || z == 11 || z == 13;
            helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
        }
        helper.setBlock(CENTER, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        ShadowTanglerEntity mob = stationaryShadow(helper);
        BlockPos sealed = helper.absolutePos(new BlockPos(9, 2, 12));
        var initialPosition = mob.position();
        FleeLightGoal goal = new FleeLightGoal(mob);
        boolean[] foundReachablePath = {false};
        boolean[] reachedDark = {false};
        int[] previousCursor = {0};
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(goal.canUse() && mob.brightnessAt(sealed) <= 6,
                    "adult bright mob has a nearby dark sealed refuge");
            BlockPos reachable = helper.absolutePos(new BlockPos(19, 2, 15));
            var fixturePath = mob.getNavigation().createPath(reachable, 0);
            helper.assertTrue(mob.onGround() && mob.brightnessAt(reachable) <= 6
                            && fixturePath != null && fixturePath.canReach(),
                    "stationary fixture can really pathfind to the later dark refuge: ground="
                            + mob.onGround() + " light=" + mob.brightnessAt(reachable)
                            + " path=" + fixturePath);
            goal.start();
        });
        helper.runAtTickTime(31, () -> {
            goal.tick();
            assertBudgets(helper, goal);
            previousCursor[0] = (Integer) searchState(goal, "searchCursor");
            helper.assertTrue(goal.lastPathTargets() > 0 && previousCursor[0] > 0
                            && mob.getNavigation().isDone(),
                    "first batch considers dark candidates but cannot enter the sealed refuge");
        });
        for (int tick = 32; tick <= 300; tick++) {
            final int now = tick;
            helper.runAtTickTime(tick, () -> {
                if (!foundReachablePath[0]) {
                    goal.tick();
                    assertBudgets(helper, goal);
                    var path = mob.getNavigation().getPath();
                    if (path != null && path.canReach() && !mob.getNavigation().isDone()) {
                        helper.assertTrue(!path.getTarget().equals(sealed)
                                        && mob.brightnessAt(path.getTarget()) <= 6
                                        && path.getTarget().distSqr(mob.blockPosition()) <= 256,
                                "a later batch selects a reachable dark target inside the current radius");
                        foundReachablePath[0] = true;
                        // Resume the real goal selector and movement, not just a path-existence check.
                        mob.setNoAi(false);
                    } else {
                        int cursor = (Integer) searchState(goal, "searchCursor");
                        helper.assertTrue(cursor > previousCursor[0],
                                "search advances before the reachable refuge: cursor=" + cursor
                                        + " previous=" + previousCursor[0] + " ground=" + mob.onGround()
                                        + " scanned=" + goal.lastSearchPositions() + " targets=" + goal.lastPathTargets()
                                        + " path=" + path);
                        previousCursor[0] = cursor;
                    }
                }
                if (foundReachablePath[0] && mob.brightnessAt(mob.blockPosition()) <= 6
                        && mob.position().subtract(initialPosition).horizontalDistanceSqr() >= 1) {
                    reachedDark[0] = true;
                }
                if (now == 70) helper.assertTrue(foundReachablePath[0], "a later search batch finds a reachable refuge");
                if (now == 300) {
                    helper.assertTrue(mob.isAlive() && reachedDark[0],
                            "real navigation escapes into darkness after skipping the sealed refuge");
                    goal.stop();
                    mob.discard();
                    helper.succeed();
                }
            });
        }
    }

    private static ShadowTanglerEntity stationaryShadow(GameTestHelper helper) {
        ShadowTanglerEntity mob = helper.spawn(ModEntities.SHADOW_TANGLER.get(), CENTER);
        mob.setNoAi(true);
        helper.assertTrue(!helper.getBlockState(CENTER.below()).getCollisionShape(
                helper.getLevel(), helper.absolutePos(CENTER.below())).isEmpty(), "fixture has solid ground");
        helper.assertTrue(mob.getY() == helper.absolutePos(CENTER).getY()
                && helper.getLevel().noCollision(mob, mob.getBoundingBox()), "fixture stands above its floor without collisions");
        // NoAI prevents the normal movement tick from establishing this real floor contact.
        System.out.println("QIJIE_STATIONARY_FIXTURE initial_on_ground=" + mob.onGround());
        mob.setOnGround(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        return mob;
    }

    private static void assertBudgets(GameTestHelper helper, FleeLightGoal goal) {
        helper.assertTrue(goal.lastSearchPositions() <= FleeLightGoal.POSITIONS_PER_TICK,
                "position scan respects tick budget");
        helper.assertTrue(goal.lastPathTargets() <= FleeLightGoal.PATH_TARGETS_PER_TICK,
                "multi-target pathfinder respects tick budget");
    }

    private static Object searchState(FleeLightGoal goal, String name) {
        try {
            var field = FleeLightGoal.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(goal);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect flee-light search state: " + name, exception);
        }
    }

    private PerformanceGameTests() { }
}
