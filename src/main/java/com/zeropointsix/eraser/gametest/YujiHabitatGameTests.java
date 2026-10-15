package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class YujiHabitatGameTests {
    private static final java.util.Map<BlockPos, BlockState> originalBlocks = new java.util.LinkedHashMap<>();
    private static final java.util.Map<GameTestHelper, java.util.Map<BlockPos, BlockState>> expectedBlocks = new java.util.IdentityHashMap<>();
    private static final java.util.List<Mob> fixtureMobs = new java.util.ArrayList<>();

    private static int fixtureY(GameTestHelper h) {
        return h.getLevel().getMaxBuildHeight() - 16 - h.absolutePos(BlockPos.ZERO).getY();
    }

    private static BlockPos fixturePos(GameTestHelper h, BlockPos pos) {
        return pos.above(fixtureY(h));
    }

    private static Vec3 relative(GameTestHelper h, Vec3 point) {
        return h.relativeVec(point).subtract(0, fixtureY(h), 0);
    }

    private static void assertFixture(GameTestHelper h) {
        var expected = expectedBlocks.get(h);
        if (expected == null) return;
        expected.forEach((pos, state) -> {
            var actual = h.getLevel().getBlockState(pos);
            if (!actual.equals(state)) h.fail("habitat fixture changed at " + pos + " from " + state + " to " + actual);
        });
    }

    @AfterBatch(batch = "yuji_habitat")
    public static void restoreFixtures(ServerLevel level) {
        fixtureMobs.forEach(Mob::discard);
        originalBlocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
        fixtureMobs.clear(); originalBlocks.clear(); expectedBlocks.clear();
    }

    private static void fill(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, boolean stone) {
        // Verify before legitimate roof changes; never adopt unexpected world writes as the baseline.
        assertFixture(h);
        var expected = expectedBlocks.computeIfAbsent(h, key -> new java.util.LinkedHashMap<>());
        var state = (stone ? Blocks.STONE : Blocks.AIR).defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) {
            BlockPos absolute = h.absolutePos(fixturePos(h, pos)).immutable();
            originalBlocks.putIfAbsent(absolute, h.getLevel().getBlockState(absolute));
            expected.put(absolute, state);
            h.getLevel().setBlock(absolute, state, 3);
        }
    }

    private static void floor(GameTestHelper h) {
        fill(h, 0, 1, 0, 15, 7, 15, false);
        fill(h, 0, 0, 0, 15, 0, 15, true);
    }

    private static void room(GameTestHelper h, int x, boolean sealed) {
        fill(h, x, 1, 7, x, 4, 14, true);
        fill(h, x + 4, 1, 7, x + 4, 4, 14, true);
        fill(h, x, 1, 14, x + 4, 4, 14, true);
        fill(h, x, 4, 7, x + 4, 4, 14, true);
        if (sealed) fill(h, x, 1, 7, x + 4, 4, 7, true);
    }

    private static YujiMonster live(GameTestHelper h, YujiKind kind, Vec3 pos) {
        YujiMonster mob = h.spawn(YujiContent.MOBS.get(kind).get(), pos.add(0, fixtureY(h), 0));
        fixtureMobs.add(mob);
        mob.setPersistenceRequired();
        // Spawn is the sole placement. Vanilla goals/navigation/move control and gravity do the rest.
        mob.setNoAi(false); mob.setNoGravity(false);
        for (int tick = 1; tick < 650; tick++) h.runAtTickTime(tick, () -> {
            if (mob.isAlive()) assertFixture(h);
        });
        for (int tick = 40; tick < 650; tick += 40) h.runAtTickTime(tick, () -> {
            if (!mob.isAlive()) return;
            var path = mob.getNavigation().getPath();
            System.out.println("YUJI_HABITAT_TRACE kind=" + kind + ", origin=" + h.absolutePos(fixturePos(h, BlockPos.ZERO))
                    + ", pos=" + relative(h, mob.position()) + ", entityTick=" + mob.tickCount
                    + ", entityTicking=" + h.getLevel().isPositionEntityTicking(mob.blockPosition())
                    + ", support=" + h.getLevel().getBlockState(BlockPos.containing(mob.getX(), mob.getY() - .01, mob.getZ()))
                    + ", motion=" + mob.getDeltaMovement() + ", grounded=" + mob.onGround()
                    + ", collisions=" + mob.horizontalCollision + "/" + mob.verticalCollision
                    + ", effects=" + mob.getActiveEffects() + ", vehicle=" + mob.getVehicle()
                    + ", target=" + (mob.getTarget() == null ? "none" : mob.getTarget().getUUID())
                    + ", walls=" + wallTrace(h, mob)
                    + ", path=" + (path == null ? "none" : path.getNextNodeIndex() + "/" + path.getNodeCount()
                    + ", reachable=" + path.canReach() + ", next=" + (path.isDone() ? "done" : relative(h, path.getNextEntityPos(mob)))));
        });
        return mob;
    }

    private static String wallTrace(GameTestHelper h, YujiMonster mob) {
        var result = new StringBuilder();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result.append(direction).append('=');
            boolean found = false;
            for (int distance = 1; distance <= 5; distance++) {
                BlockPos pos = mob.blockPosition().relative(direction, distance);
                var lower = h.getLevel().getBlockState(pos);
                var upper = h.getLevel().getBlockState(pos.above());
                if (!lower.is(BlockTags.LEAVES) && !upper.is(BlockTags.LEAVES)
                        && lower.isCollisionShapeFullBlock(h.getLevel(), pos)
                        && upper.isCollisionShapeFullBlock(h.getLevel(), pos.above())) {
                    result.append(distance).append('@').append(pos).append(':').append(lower).append('/').append(upper);
                    found = true;
                    break;
                }
            }
            if (!found) result.append("none");
            result.append(';');
        }
        return result.toString();
    }

    private static void soundBody(GameTestHelper h, YujiMonster mob) {
        h.assertTrue(mob.isAlive() && !mob.isNoAi() && !mob.isNoGravity(), "live AI and gravity stay enabled");
        h.assertTrue(h.getLevel().noCollision(mob), "full collision body cannot overlap fixture blocks");
    }

    private static void insideRoom(GameTestHelper h, YujiMonster mob, int x) {
        soundBody(h, mob);
        Vec3 min = relative(h, new Vec3(mob.getBoundingBox().minX, mob.getY(), mob.getBoundingBox().minZ));
        Vec3 max = relative(h, new Vec3(mob.getBoundingBox().maxX, mob.getY(), mob.getBoundingBox().maxZ));
        h.assertTrue(min.x >= x + 1 && max.x <= x + 4 && min.z >= 7 && max.z <= 14,
                "whole body must be inside room " + x + ": " + min + " -> " + max);
        Vec3 pos = relative(h, mob.position());
        h.assertTrue(Math.abs(pos.y - 1) < .01 && mob.getBoundingBox().maxY <= h.absolutePos(fixturePos(h, new BlockPos(0, 4, 0))).getY(),
                "body must be below the roof, not on top of it: " + pos);
        h.assertTrue(pos.z >= 9, "must reach the room interior: " + pos);
        h.assertBlockPresent(Blocks.STONE, fixturePos(h, BlockPos.containing(pos.x, 4, pos.z)));
    }

    private static void insideCorridor(GameTestHelper h, YujiMonster mob, int wallX) {
        soundBody(h, mob);
        Vec3 min = relative(h, new Vec3(mob.getBoundingBox().minX, mob.getY(), mob.getBoundingBox().minZ));
        Vec3 max = relative(h, new Vec3(mob.getBoundingBox().maxX, mob.getY(), mob.getBoundingBox().maxZ));
        h.assertTrue(Math.abs(min.y - 1) < .01 && min.x >= wallX + 1 && max.x <= wallX + 3
                        && min.z >= 7 && max.z <= 15,
                "whole body must stay on corridor floor between walls: " + min + " -> " + max);
        h.assertBlockPresent(Blocks.STONE, fixturePos(h, BlockPos.containing(wallX, 1, relative(h, mob.position()).z)));
        h.assertBlockPresent(Blocks.STONE, fixturePos(h, BlockPos.containing(wallX + 3, 1, relative(h, mob.position()).z)));
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_habitat", timeoutTicks = 500)
    public static void liangsheWalksInsideAndStaysUnderRoof(GameTestHelper h) {
        floor(h); room(h, 5, false);
        YujiMonster mob = live(h, YujiKind.LIANGSHE, new Vec3(7.5, 1, 2.5));
        var outside = h.spawn(EntityType.COW, new Vec3(.5, 1 + fixtureY(h), 7.5));
        fixtureMobs.add(outside);
        outside.setNoAi(true);
        outside.setPersistenceRequired();
        // A live pursuit target suppresses random stroll without moving the monster into shelter.
        // Its initial 8-9.5-block distance makes pursuit stop, rather than chase or retreat.
        mob.setTarget(outside);
        h.assertTrue(mob.distanceTo(outside) >= 8 && mob.distanceTo(outside) <= 9.5,
                "outdoor target begins within the pursuit stopping band");
        for (int tick = 1; tick < 500; tick++) h.runAtTickTime(tick, () -> {
            if (!mob.isAlive()) return;
            h.assertTrue(outside.isAlive() && mob.getTarget() == outside,
                    "outdoor pursuit target remains active so random stroll cannot prove habitat selection");
        });
        Vec3 start = mob.position();
        h.startSequence().thenWaitUntil(() -> {
            insideRoom(h, mob, 5);
            Vec3 pos = relative(h, mob.position());
            h.assertTrue(pos.x > 6 && pos.x < 9 && pos.z >= 9 && pos.z < 14,
                    "Liangshe must walk into the room: " + pos);
            h.assertTrue(mob.distanceToSqr(start) > 36, "not a stationary shelter predicate");
        }).thenExecuteFor(80, () -> {
            insideRoom(h, mob, 5);
            Vec3 pos = relative(h, mob.position());
            h.assertTrue(pos.x > 6 && pos.x < 9 && pos.z >= 9 && pos.z < 14, "stays indoors after arriving");
            h.assertBlockPresent(Blocks.STONE, fixturePos(h, BlockPos.containing(pos.x, 4, pos.z)));
        }).thenExecute(() -> { mob.discard(); outside.discard(); }).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_habitat", timeoutTicks = 650)
    public static void liangsheRejectsSealedRoomAndUsesReachableRoom(GameTestHelper h) {
        floor(h); room(h, 1, true); room(h, 10, false);
        YujiMonster mob = live(h, YujiKind.LIANGSHE, new Vec3(3.5, 1, 4.5));
        h.startSequence().thenWaitUntil(() -> {
            soundBody(h, mob);
            Vec3 pos = relative(h, mob.position());
            h.assertTrue(pos.x > 10 && pos.x < 14 && pos.z >= 9, "must find the farther reachable room, not phase into sealed room: " + pos);
        }).thenExecute(mob::discard).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_habitat", timeoutTicks = 600)
    public static void xieyinWalksIntoBodyWidthCorridor(GameTestHelper h) {
        floor(h);
        fill(h, 5, 1, 7, 5, 7, 14, true); fill(h, 8, 1, 7, 8, 7, 14, true);
        YujiMonster mob = live(h, YujiKind.XIEYIN, new Vec3(7, 1, 2));
        Vec3 start = mob.position();
        h.startSequence().thenWaitUntil(() -> {
            soundBody(h, mob);
            h.assertTrue(Math.abs(mob.getBbWidth() - 1.8) < .001 && Math.abs(mob.getBbHeight() - 6.3) < .001,
                    "accepted giant dimensions stay unchanged");
            h.assertTrue(relative(h, mob.position()).z >= 8, "Xieyin must navigate into narrow corridor: " + relative(h, mob.position()));
            h.assertTrue(mob.distanceToSqr(start) > 30, "real displacement into shelter");
            insideCorridor(h, mob, 5);
        }).thenExecuteFor(80, () -> {
            insideCorridor(h, mob, 5);
            Vec3 pos = relative(h, mob.position());
            h.assertTrue(pos.x > 6 && pos.x < 8 && pos.z >= 8 && pos.z <= 14, "stays in corridor");
        }).thenExecute(mob::discard).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_habitat", timeoutTicks = 600)
    public static void xieyinRejectsTooLowCorridorAndChoosesTallOne(GameTestHelper h) {
        floor(h);
        fill(h, 1, 1, 7, 1, 7, 14, true); fill(h, 4, 1, 7, 4, 7, 14, true);
        fill(h, 1, 3, 7, 4, 3, 14, true);
        fill(h, 10, 1, 7, 10, 7, 14, true); fill(h, 13, 1, 7, 13, 7, 14, true);
        YujiMonster mob = live(h, YujiKind.XIEYIN, new Vec3(3, 1, 3));
        for (int tick = 40; tick < 600; tick += 40) h.runAtTickTime(tick, () -> {
            if (!mob.isAlive()) return;
            var path = mob.getNavigation().getPath();
            System.out.println("YUJI_HABITAT_LOW_CORRIDOR pos=" + relative(h, mob.position())
                    + ", entityTick=" + mob.tickCount + ", gameTime=" + h.getLevel().getGameTime()
                    + ", entityTicking=" + h.getLevel().isPositionEntityTicking(mob.blockPosition())
                    + ", corridorLoaded=" + h.getLevel().hasChunksAt(h.absolutePos(fixturePos(h, new BlockPos(7, 0, 4))), h.absolutePos(fixturePos(h, new BlockPos(17, 11, 14))))
                    + ", target=" + (mob.getTarget() == null ? "none" : mob.getTarget().getUUID())
                    + ", path=" + (path == null ? "none" : path.getNextNodeIndex() + "/" + path.getNodeCount()
                    + ", reachable=" + path.canReach() + ", next=" + (path.isDone() ? "done" : relative(h, path.getNextEntityPos(mob)))));
        });
        h.startSequence().thenWaitUntil(() -> {
            soundBody(h, mob);
            Vec3 pos = relative(h, mob.position());
            h.assertTrue(pos.x > 11 && pos.x < 13 && pos.z >= 8, "must reject head collision and use tall corridor: " + pos);
            insideCorridor(h, mob, 10);
        }).thenExecute(mob::discard).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_habitat", timeoutTicks = 650)
    public static void liangsheLeavesDestroyedShelterAndFindsAnother(GameTestHelper h) {
        floor(h); room(h, 1, false);
        YujiMonster mob = live(h, YujiKind.LIANGSHE, new Vec3(3.5, 1, 2.5));
        h.startSequence().thenWaitUntil(() -> insideRoom(h, mob, 1))
                .thenExecute(() -> {
                    // Only introduce the alternative after arrival: room preference is not specified.
                    fill(h, 1, 4, 7, 5, 4, 14, false);
                    room(h, 10, false);
                })
                .thenWaitUntil(() -> {
                    insideRoom(h, mob, 10);
                    Vec3 pos = relative(h, mob.position());
                    h.assertTrue(pos.x > 10 && pos.z >= 9, "roof removal invalidates cached shelter and triggers new navigation: " + pos);
                }).thenExecute(mob::discard).thenSucceed();
    }
}
