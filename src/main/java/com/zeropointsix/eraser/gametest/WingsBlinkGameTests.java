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
