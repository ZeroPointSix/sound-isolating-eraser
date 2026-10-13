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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Independent server integration checks; these are not native-client/network E2E tests. */
@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WingsReviewRegressionTests {
    private WingsReviewRegressionTests() {}

    private static FakePlayer winged(GameTestHelper h, Vec3 position, int tier) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "WingReview"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position);
        player.setOnGround(false);
        player.setItemSlot(EquipmentSlot.CHEST,
                new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
        player.getFoodData().setFoodLevel(20);
        FlightEvents.serverSetDeployed(player, true);
        WingsState.setTier(player, tier);
        tick(player);
        h.assertTrue(WingsState.deployed(player), "fixture must remain deployed and airborne");
        h.assertTrue(WingsState.speedFlagAt(player) == 0, "fixture must start without a speed flag");
        return player;
    }

    private static void tick(FakePlayer player) {
        FlightEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    private static Vec3 wallDirection(GameTestHelper h) {
        for (int y = 1; y <= 6; y++) {
            for (int z = 1; z <= 4; z++) {
                h.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        return h.absoluteVec(new Vec3(1, 0, 0)).subtract(h.absoluteVec(Vec3.ZERO)).normalize();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewStormMovementSettledBeforeHover(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.stormSpeed.get() == 120.0
                        && WingsConfig.SERVER.hoverMaxSpeed.get() == 5.0,
                "review fixture requires default STORM=120 and HOVER=5 m/s");
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 3.5, 2.5)), WingsState.STORM);
        Vec3 start = player.position();
        Vec3 expected = start.add(6.0, 0, 0);
        player.setPos(expected);
        FlightEvents.serverToggleHover(player);
        tick(player);
        System.out.println("WINGS_REVIEW_STORM_HOVER expected=" + expected
                + " actual=" + player.position() + " speedFlag=" + WingsState.speedFlagAt(player));
        h.assertTrue(WingsState.tier(player) == WingsState.HOVER, "the hover request must be accepted");
        h.assertTrue(WingsState.speedFlagAt(player) == 0,
                "a legal 6-block STORM step before the hover request must not be flagged");
        h.assertTrue(player.position().distanceTo(expected) < 0.001,
                "a legal pre-transition STORM step must not be rolled back under the new HOVER limit");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewRepeatedDeployPreservesWallImpact(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.wallDamageEnabled.get()
                        && WingsConfig.SERVER.wallDamageThresholdSpeed.get() == 40.0,
                "review fixture requires enabled wall damage with the default 40 m/s threshold");
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        FakePlayer player = winged(h, wall.subtract(direction.scale(3.5)), WingsState.STORM);
        float healthBefore = player.getHealth();
        player.setPos(player.position().add(direction.scale(3.19)));
        FlightEvents.serverSetDeployed(player, true);
        tick(player);
        System.out.println("WINGS_REVIEW_REPEAT_DEPLOY wallHit=" + WingsState.wallHitAt(player)
                + " speedFlag=" + WingsState.speedFlagAt(player)
                + " healthBefore=" + healthBefore + " healthAfter=" + player.getHealth());
        h.assertTrue(WingsState.speedFlagAt(player) == 0, "the impact approach must be legal STORM movement");
        h.assertTrue(WingsState.wallHitAt(player) > 0,
                "repeating deploy after movement must not erase the wall-impact segment");
        h.assertTrue(player.getHealth() < healthBefore,
                "the real server-side wall impact must still damage the survival player");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewBatchedCruiseDoesNotBecomeHighSpeedImpact(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.cruiseSpeed.get() == 30.0
                        && WingsConfig.SERVER.wallDamageEnabled.get()
                        && WingsConfig.SERVER.wallDamageThresholdSpeed.get() == 40.0,
                "review fixture requires CRUISE=30 and enabled wall threshold=40 m/s");
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        FakePlayer player = winged(h, wall.subtract(direction.scale(3.31)), WingsState.CRUISE);
        float healthBefore = player.getHealth();
        long startTick = h.getLevel().getGameTime();
        Vec3 start = player.position();
        h.runAfterDelay(2, () -> {
            // Two 30 m/s client steps arrive together after two server ticks.
            // This constructs their server observation, not native socket delivery.
            player.setDeltaMovement(direction.scale(1.5));
            for (int i = 0; i < 2; i++) {
                player.setPos(player.position().add(direction.scale(1.5)));
            }
            tick(player);
            long elapsed = h.getLevel().getGameTime() - startTick;
            System.out.println("WINGS_REVIEW_BATCHED_CRUISE elapsedTicks=" + elapsed
                    + " distance=" + player.position().distanceTo(start)
                    + " wallHit=" + WingsState.wallHitAt(player)
                    + " speedFlag=" + WingsState.speedFlagAt(player)
                    + " healthBefore=" + healthBefore + " healthAfter=" + player.getHealth());
            h.assertTrue(elapsed >= 2, "fixture must use two real server ticks");
            h.assertTrue(WingsState.speedFlagAt(player) == 0,
                    "the two legal CRUISE steps must be accepted by the batching allowance");
            h.assertTrue(player.position().distanceTo(start.add(direction.scale(3.0))) < 0.001,
                    "the complete legal batched movement must remain accepted");
            h.assertTrue(WingsState.wallHitAt(player) == 0,
                    "two 30 m/s steps must not be treated as a single 60 m/s wall impact");
            h.assertTrue(player.getHealth() == healthBefore,
                    "packet batching must not damage a player travelling below the 40 m/s threshold");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewNormalStormImpactPositiveControl(GameTestHelper h) {
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        FakePlayer player = winged(h, wall.subtract(direction.scale(3.5)), WingsState.STORM);
        float healthBefore = player.getHealth();
        player.setPos(player.position().add(direction.scale(3.19)));
        tick(player);
        System.out.println("WINGS_REVIEW_NORMAL_IMPACT wallHit=" + WingsState.wallHitAt(player)
                + " speedFlag=" + WingsState.speedFlagAt(player)
                + " healthBefore=" + healthBefore + " healthAfter=" + player.getHealth());
        h.assertTrue(WingsState.speedFlagAt(player) == 0, "positive control uses legal STORM movement");
        h.assertTrue(WingsState.wallHitAt(player) > 0 && player.getHealth() < healthBefore,
                "the same wall geometry must cause damage without a repeated deploy request");
        h.succeed();
    }
}
