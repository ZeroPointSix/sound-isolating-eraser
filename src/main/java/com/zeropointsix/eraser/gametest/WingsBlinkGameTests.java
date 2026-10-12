package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.wings.FlightEvents;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.wings.WingsState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WingsBlinkGameTests {
    private WingsBlinkGameTests() {}

    // Keep the real chunk-ticket path; replace FakePlayer's inert network teleport
    // with a position update so assertions exercise serverBlink's destination.
    private static final class BlinkPlayer extends FakePlayer {
        private int teleports;

        BlinkPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "BlinkQA"));
        }

        @Override
        public void teleportTo(double x, double y, double z) {
            teleports++;
            setPos(x, y, z);
        }
    }

    private static BlinkPlayer player(GameTestHelper h, Vec3 relative) {
        BlinkPlayer p = new BlinkPlayer(h.getLevel());
        p.setPos(h.absoluteVec(relative));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
        p.getFoodData().setFoodLevel(20);
        Vec3 forward = h.absoluteVec(new Vec3(0, 0, 1)).subtract(h.absoluteVec(Vec3.ZERO));
        p.setYRot((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
        p.setXRot(0);
        FlightEvents.serverSetDeployed(p, true);
        p.setPose(Pose.STANDING);
        p.refreshDimensions();
        return p;
    }

    private static GameTestConfigs.LiveConfig config() {
        GameTestConfigs.LiveConfig saved = GameTestConfigs.sandbox(WingsConfig.SERVER_SPEC);
        WingsConfig.SERVER.blinkDistance.set(4.0);
        return saved;
    }

    private static void assertRejected(GameTestHelper h, BlinkPlayer p) {
        Vec3 before = p.position();
        CompoundTag state = p.getPersistentData().copy();
        float exhaustion = p.getFoodData().getExhaustionLevel();
        FlightEvents.serverBlink(p);
        h.assertTrue(p.teleports == 0 && p.position().equals(before),
                "rejected blink must not teleport, including backwards");
        h.assertTrue(p.getPersistentData().equals(state),
                "rejected blink must preserve the chain, cooldown and hover state");
        h.assertTrue(p.getFoodData().getExhaustionLevel() == exhaustion,
                "rejected blink must not consume hunger");
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkHorizontalPreservesFeetHeight(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            Vec3 expected = p.position().add(p.getLookAngle().scale(4));
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && p.position().distanceTo(expected) < 0.001,
                    "horizontal blink must travel four blocks without adding eye height");
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "destination must fit the player");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkStopsBeforeLowWall(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            h.setBlock(new BlockPos(2, 3, 4), Blocks.STONE);
            Vec3 before = p.position();
            FlightEvents.serverBlink(p);
            double distance = p.position().distanceTo(before);
            h.assertTrue(p.teleports == 1 && distance > 1.1 && distance < 1.21,
                    "the feet must stop before a wall below the eye ray: " + distance);
            h.assertTrue(Math.abs(p.getY() - before.y) < 0.001, "low walls must not lift the blink");
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "wall landing must be collision-free");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkSweepsFullWidthPastCorner(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.8, 3, 2.5));
            h.setBlock(new BlockPos(3, 3, 4), Blocks.STONE);
            Vec3 before = p.position();
            FlightEvents.serverBlink(p);
            double distance = p.position().distanceTo(before);
            h.assertTrue(p.teleports == 1 && distance > 1.1 && distance < 1.21,
                    "the player's shoulder must not pass through a corner missed by the centre ray");
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "corner landing must fit the full body");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkSecondSegmentStopsAtThinFence(GameTestHelper h) {
        try (var saved = config()) {
            WingsConfig.SERVER.blinkDistance.set(8.0);
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            h.setBlock(new BlockPos(2, 3, 8), Blocks.OAK_FENCE);
            Vec3 before = p.position();
            FlightEvents.serverBlink(p);
            double distance = p.position().distanceTo(before);
            h.assertTrue(p.teleports == 1 && distance > 5.5 && distance < 5.6,
                    "the second continuous sweep must stop at a thin fence: " + distance);
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "fence landing must fit");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkSharedSegmentEndpointStopsAtWall(GameTestHelper h) {
        assertSharedSegmentEndpoint(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkReverseSharedSegmentEndpointStopsAtWall(GameTestHelper h) {
        assertSharedSegmentEndpoint(h, true);
    }

    private static void assertSharedSegmentEndpoint(GameTestHelper h, boolean reverse) {
        try (var saved = config()) {
            WingsConfig.SERVER.blinkDistance.set(8.0);
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            if (reverse) p.setYRot(p.getYRot() + 180);
            Vec3 direction = p.getLookAngle().normalize();
            boolean alongX = Math.abs(direction.x) > Math.abs(direction.z);
            double component = alongX ? direction.x : direction.z;
            double sign = Math.copySign(1, component);
            BlockPos relativeWall = null;
            // Pick an exactly representable shared endpoint despite world-coordinate rounding.
            for (int z = 6; z <= 14; z++) {
                BlockPos candidate = new BlockPos(2, 3, z);
                BlockPos wall = h.absolutePos(candidate);
                double face = (alongX ? wall.getX() : wall.getZ()) + (sign < 0 ? 1 : 0);
                double entry = face - sign * p.getBbWidth() / 2.0 + sign * 1.0E-6;
                double origin = entry - component * 4;
                p.setPos(alongX ? origin : p.getX(), p.getY(), alongX ? p.getZ() : origin);
                Vec3 feet = p.position();
                Vec3 endpoint = feet.add(direction.scale(4));
                double extent = alongX
                        ? (sign > 0 ? p.getBoundingBox().maxX - feet.x : feet.x - p.getBoundingBox().minX)
                        : (sign > 0 ? p.getBoundingBox().maxZ - feet.z : feet.z - p.getBoundingBox().minZ);
                double actualEntry = face - sign * extent + sign * 1.0E-6;
                if ((alongX ? endpoint.x : endpoint.z) == actualEntry) {
                    relativeWall = candidate;
                    break;
                }
            }
            h.assertTrue(relativeWall != null,
                    "fixture must align the collision entry with the shared sweep endpoint");
            h.setBlock(relativeWall, Blocks.STONE);
            Vec3 before = p.position();
            FlightEvents.serverBlink(p);
            double distance = p.position().distanceTo(before);
            h.assertTrue(p.teleports == 1 && distance > 3.99 && distance < 4,
                    "a shared segment endpoint must stop before the wall, not cross it: " + distance);
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "endpoint landing must fit");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkFullChainHonorsShortConfiguredCooldown(GameTestHelper h) {
        try (var saved = config()) {
            WingsConfig.SERVER.blinkChainMax.set(1);
            WingsConfig.SERVER.blinkChainCooldownTicks.set(1);
            WingsConfig.SERVER.blinkCooldownTicks.set(40);
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            long now = h.getLevel().getGameTime();
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1, "the configured single-blink chain must succeed");
            h.assertTrue(WingsState.blinkLockUntil(p) == now + 1
                    && WingsState.blinkCooldownUntil(p) == now + 1,
                    "ordinary cooldown must not override the configured full-chain cooldown");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkUsesFlyingPoseUnderLowCeiling(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            p.setPose(Pose.FALL_FLYING);
            p.refreshDimensions();
            h.assertTrue(p.getBbHeight() < 1, "fixture must use the flying collision body");
            h.setBlock(new BlockPos(2, 4, 4), Blocks.STONE);
            Vec3 expected = p.position().add(p.getLookAngle().scale(4));
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && p.position().distanceTo(expected) < 0.001,
                    "the actual flying body must fit under a ceiling that blocks standing players");
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "flying landing must fit");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkDefaultDistanceCrossesSixSweeps(GameTestHelper h) {
        try (var saved = config()) {
            WingsConfig.SERVER.blinkDistance.set(24.0);
            BlinkPlayer p = player(h, new Vec3(2.5, 12, 2.5));
            Vec3 expected = p.position().add(p.getLookAngle().scale(24));
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && p.position().distanceTo(expected) < 0.001,
                    "the default blink must travel all 24 blocks across six continuous sweeps");
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "default-distance landing must fit");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkBlockedDoesNotSpendChainOrHunger(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.6999));
            h.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
            h.setBlock(new BlockPos(2, 4, 3), Blocks.STONE);
            long now = h.getLevel().getGameTime();
            WingsState.setBlinkTimes(p, new long[]{now, now});
            WingsState.setBlinkCooldownUntil(p, now + 40);
            assertRejected(h, p);
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkFlushWallCannotBeCrossed(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            BlockPos wall = h.absolutePos(new BlockPos(2, 3, 3));
            p.setPos(p.getX(), p.getY(), wall.getZ() - p.getBbWidth() / 2.0);
            h.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
            h.setBlock(new BlockPos(2, 4, 3), Blocks.STONE);
            assertRejected(h, p);
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkAlongFloorPreservesFullDistance(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            for (int z = 2; z <= 7; z++) h.setBlock(new BlockPos(2, 2, z), Blocks.STONE);
            Vec3 expected = p.position().add(p.getLookAngle().scale(4));
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && p.position().distanceTo(expected) < 0.001,
                    "touching the floor must not shorten a horizontal blink");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkDownwardStopsAboveFloor(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 6, 2.5));
            h.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
            p.setXRot(90);
            double floorTop = h.absolutePos(new BlockPos(2, 3, 2)).getY() + 1;
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && p.getY() > floorTop && p.getY() < floorTop + 0.01,
                    "downward blink must stop above the floor");
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "downward landing must fit");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkUpwardStopsBeforeCeiling(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            h.setBlock(new BlockPos(2, 6, 2), Blocks.STONE);
            p.setXRot(-90);
            double ceiling = h.absolutePos(new BlockPos(2, 6, 2)).getY();
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && p.getBoundingBox().maxY < ceiling
                            && p.getBoundingBox().maxY > ceiling - 0.01,
                    "upward blink must stop with head clearance below the ceiling");
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()), "ceiling landing must fit");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkUnequippedRequestIsRejected(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            assertRejected(h, p);
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkRejectsWorldBorderCrossing(GameTestHelper h) {
        WorldBorder border = h.getLevel().getWorldBorder();
        WorldBorder.Settings original = border.createSettings();
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            border.setCenter(p.getX(), p.getZ());
            border.setSize(6);
            assertRejected(h, p);
            h.succeed();
        } finally {
            border.applySettings(original);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkDoesNotLoadUnloadedChunks(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            p.setPos(1000000.5, 100, 1000000.5);
            BlockPos destination = BlockPos.containing(p.position().add(p.getLookAngle().scale(4)));
            h.assertTrue(!h.getLevel().hasChunkAt(destination), "test destination must start unloaded");
            assertRejected(h, p);
            h.assertTrue(!h.getLevel().hasChunkAt(destination), "rejected blink must not load the chunk");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkSingleChainLimitLocksFirstSuccess(GameTestHelper h) {
        try (var saved = config()) {
            WingsConfig.SERVER.blinkChainMax.set(1);
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            long now = h.getLevel().getGameTime();
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && WingsState.blinkTimes(p).length == 0,
                    "a one-blink chain must be consumed immediately");
            h.assertTrue(WingsState.blinkLockUntil(p) == now + WingsConfig.SERVER.blinkChainCooldownTicks.get(),
                    "a one-blink chain must start the configured long lock on its first success");
            Vec3 after = p.position();
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && p.position().equals(after), "long lock must reject the second request");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blinkVerticalUpdatesHoverAnchor(GameTestHelper h) {
        try (var saved = config()) {
            BlinkPlayer p = player(h, new Vec3(2.5, 3, 2.5));
            p.setXRot(-90);
            double expectedY = p.getY() + 4;
            FlightEvents.serverBlink(p);
            h.assertTrue(p.teleports == 1 && Math.abs(p.getY() - expectedY) < 0.001,
                    "vertical blink distance must be measured from feet");
            h.assertTrue(Math.abs(WingsState.hoverY(p) - expectedY) < 0.001,
                    "hover must hold the new altitude instead of pulling back after blink");
            h.succeed();
        }
    }
}
