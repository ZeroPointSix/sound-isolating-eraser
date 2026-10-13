package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.wings.FlightEvents;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.wings.WingsState;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
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
        // Forge FakePlayer is invulnerable; validate the real impact branch, not health loss.
        h.assertTrue(WingsState.wallHitAt(player) > 0,
                "the same wall geometry must register impact without a repeated deploy request");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewStormMovementSettledBeforeCycleWrap(GameTestHelper h) {
        assertPreTransitionMovement(h, WingsState.HOVER, FlightEvents::serverCycleTier,
                "STORM cycle to HOVER");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewStormMovementSettledBeforeHungerDowngrade(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.minFoodForFastTiers.get() > 1,
                "fixture needs a positive food level below the fast-tier minimum");
        assertPreTransitionMovement(h, WingsState.CRUISE, player -> {
            player.getFoodData().setFoodLevel(WingsConfig.SERVER.minFoodForFastTiers.get() - 1);
            tick(player);
        }, "low-food STORM to CRUISE");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewStormMovementSettledBeforeStarvation(GameTestHelper h) {
        assertPreTransitionMovement(h, WingsState.HOVER, player -> {
            player.getFoodData().setFoodLevel(0);
            tick(player);
            h.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING),
                    "starvation must still apply its slow-fall safety effect");
        }, "starving STORM to HOVER");
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void reviewHoverDecelerationAcceptedOverTime(GameTestHelper h) {
        assertDeceleration(h, WingsState.HOVER, FlightEvents::serverToggleHover,
                "hover toggle");
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void reviewCycleDecelerationAcceptedOverTime(GameTestHelper h) {
        assertDeceleration(h, WingsState.HOVER, FlightEvents::serverCycleTier,
                "cycle wrap");
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void reviewHungerDecelerationAcceptedOverTime(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.minFoodForFastTiers.get() > 1,
                "fixture needs a positive food level below the fast-tier minimum");
        assertDeceleration(h, WingsState.CRUISE, player -> {
            player.getFoodData().setFoodLevel(WingsConfig.SERVER.minFoodForFastTiers.get() - 1);
            tick(player);
        }, "low food");
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void reviewStarvationDecelerationAcceptedOverTime(GameTestHelper h) {
        assertDeceleration(h, WingsState.HOVER, player -> {
            player.getFoodData().setFoodLevel(0);
            tick(player);
        }, "starvation");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewRepeatedDeploySettlesWallImpactOnlyOnce(GameTestHelper h) {
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        ImpactPlayer player = impactPlayer(h, wall.subtract(direction.scale(3.5)));
        Vec3 expected = player.position().add(direction.scale(3.19));
        player.setPos(expected);
        FlightEvents.serverSetDeployed(player, true);
        long firstImpact = WingsState.wallHitAt(player);
        h.assertTrue(firstImpact > 0 && player.wallImpactCalls == 1,
                "the initial repeated-deploy request must settle exactly one wall impact");
        for (int i = 0; i < 16; i++) {
            FlightEvents.serverSetDeployed(player, true);
            tick(player);
        }
        h.assertTrue(WingsState.wallHitAt(player) == firstImpact && player.wallImpactCalls == 1,
                "repeated same-tick requests must not apply the settled impact again");
        assertAccepted(h, player, expected, "repeated deploy wall settlement");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewStowPreservesWallImpactOnlyOnce(GameTestHelper h) {
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        ImpactPlayer player = impactPlayer(h, wall.subtract(direction.scale(3.5)));
        Vec3 expected = player.position().add(direction.scale(3.19));
        player.setPos(expected);
        FlightEvents.serverSetDeployed(player, false);
        long firstImpact = WingsState.wallHitAt(player);
        h.assertTrue(firstImpact > 0 && player.wallImpactCalls == 1,
                "stowing must settle the pending impact before clearing flight state");
        for (int i = 0; i < 16; i++) {
            FlightEvents.serverSetDeployed(player, false);
        }
        tick(player);
        h.assertTrue(!WingsState.deployed(player), "the stow request must remain effective");
        h.assertTrue(WingsState.wallHitAt(player) == firstImpact && player.wallImpactCalls == 1,
                "repeated stow requests and the following tick must not duplicate impact damage");
        assertAccepted(h, player, expected, "stow wall settlement");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewSameTickDeployStowCannotRefillMovementBudget(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.hoverMaxSpeed.get() == 5.0,
                "fixture requires the default 1.25-block two-tick HOVER allowance");
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 32.5, 2.5)), WingsState.HOVER);
        Vec3 accepted = player.position().add(1.0, 0, 0);
        player.setPos(accepted);
        tick(player);
        assertAccepted(h, player, accepted, "initial HOVER budget consumption");
        for (int i = 0; i < 16; i++) {
            FlightEvents.serverSetDeployed(player, true);
            FlightEvents.serverSetDeployed(player, false);
            FlightEvents.serverSetDeployed(player, true);
        }
        player.setPos(accepted.add(0.5, 0, 0));
        tick(player);
        h.assertTrue(WingsState.speedFlagAt(player) > 0,
                "same-tick state requests must not refill the already spent movement allowance");
        h.assertTrue(player.position().distanceTo(accepted) < 0.001,
                "the overspent movement must roll back to the last accepted position");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewBatchedFastImpactPositiveControl(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.boostSpeed.get() == 60.0
                        && WingsConfig.SERVER.wallDamageEnabled.get()
                        && WingsConfig.SERVER.wallDamageThresholdSpeed.get() == 40.0,
                "fixture requires BOOST=60 and enabled wall threshold=40 m/s");
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        FakePlayer player = winged(h, wall.subtract(direction.scale(4.71)), WingsState.BOOST);
        Vec3 expected = player.position().add(direction.scale(4.4));
        long startTick = h.getLevel().getGameTime();
        h.runAfterDelay(2, () -> {
            player.setDeltaMovement(direction.scale(2.2));
            player.setPos(expected);
            tick(player);
            h.assertTrue(h.getLevel().getGameTime() - startTick >= 2,
                    "the positive batching control must span two real server ticks");
            assertAccepted(h, player, expected, "batched 44 m/s impact approach");
            h.assertTrue(WingsState.wallHitAt(player) > 0,
                    "time-normalization must not suppress genuine over-threshold batched impacts");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void reviewDecelerationAllowanceDoesNotPermitPermanentStormSpeed(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.stormSpeed.get() == 120.0
                        && WingsConfig.SERVER.hoverMaxSpeed.get() == 5.0
                        && WingsConfig.SERVER.accelLerp.get() == 2.5,
                "fixture requires the default STORM/HOVER speeds and deceleration");
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 32.5, 2.5)), WingsState.STORM);
        Vec3 accepted = player.position().add(6.0, 0, 0);
        player.setDeltaMovement(new Vec3(6.0, 0, 0));
        player.setPos(accepted);
        FlightEvents.serverToggleHover(player);
        assertAccepted(h, player, accepted, "movement before expiring the transition allowance");
        h.runAfterDelay(80, () -> {
            player.setDeltaMovement(new Vec3(6.0, 0, 0));
            player.setPos(accepted.add(6.0, 0, 0));
            tick(player);
            h.assertTrue(WingsState.tier(player) == WingsState.HOVER,
                    "the fixture must remain in HOVER throughout the idle interval");
            h.assertTrue(WingsState.speedFlagAt(player) > 0,
                    "a past STORM tier must not authorize full STORM speed indefinitely");
            h.assertTrue(player.position().distanceTo(accepted) < 0.001,
                    "expired fast-tier movement must be rolled back after the transition settles");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewSingleFastPacketAfterIdleStillHitsWall(GameTestHelper h) {
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        ImpactPlayer player = impactPlayer(h, wall.subtract(direction.scale(3.5)));
        Vec3 expected = player.position().add(direction.scale(3.19));
        long started = h.getLevel().getGameTime();
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getLevel().getGameTime() - started >= 2,
                    "the fixture must remain stationary for two real server ticks");
            player.setDeltaMovement(direction.scale(3.19));
            player.setPos(expected);
            FlightEvents.settleClientMovement(player);
            h.assertTrue(WingsState.wallHitAt(player) > 0 && player.wallImpactCalls == 1,
                    "a single 63.8 m/s movement packet after idle must not be divided by idle time");
            tick(player);
            h.assertTrue(player.wallImpactCalls == 1,
                    "the following player tick must not reapply the packet's settled wall impact");
            assertAccepted(h, player, expected, "single fast packet after idle");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewSameTickCruisePacketsSettleAsSeparateSteps(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.cruiseSpeed.get() == 30.0
                        && WingsConfig.SERVER.wallDamageEnabled.get()
                        && WingsConfig.SERVER.wallDamageThresholdSpeed.get() == 40.0,
                "fixture requires CRUISE=30 and enabled wall threshold=40 m/s");
        Vec3 direction = wallDirection(h);
        Vec3 wall = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        FakePlayer player = winged(h, wall.subtract(direction.scale(3.31)), WingsState.CRUISE);
        Vec3 start = player.position();
        h.runAfterDelay(2, () -> {
            long packetTick = h.getLevel().getGameTime();
            for (int i = 1; i <= 2; i++) {
                Vec3 expected = start.add(direction.scale(1.5 * i));
                player.setDeltaMovement(direction.scale(1.5));
                player.setPos(expected);
                FlightEvents.settleClientMovement(player);
                assertAccepted(h, player, expected, "same-tick CRUISE packet " + i);
                h.assertTrue(WingsState.wallHitAt(player) == 0,
                        "each 30 m/s client step must remain below the wall-damage threshold");
            }
            h.assertTrue(h.getLevel().getGameTime() == packetTick,
                    "both observed client steps must settle within the same server tick");
            tick(player);
            h.assertTrue(WingsState.wallHitAt(player) == 0,
                    "the player tick must not re-aggregate already settled CRUISE steps into 60 m/s");
            Vec3 accepted = start.add(direction.scale(3.0));
            player.setPos(accepted.subtract(direction.scale(2.1)));
            FlightEvents.settleClientMovement(player);
            h.assertTrue(WingsState.speedFlagAt(player) > 0,
                    "both packets must consume shared credit, leaving only 2 of the 5-block allowance");
            h.assertTrue(player.position().distanceTo(accepted) < 0.001,
                    "a same-tick packet exceeding the remaining movement budget must roll back");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reviewHungerCoastingDecelerationAcceptedOverTime(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.minFoodForFastTiers.get() > 1,
                "fixture needs a positive food level below the fast-tier minimum");
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 32.5, 2.5)), WingsState.STORM);
        double[] speed = {WingsConfig.SERVER.stormSpeed.get() / 20.0};
        double target = WingsConfig.SERVER.cruiseSpeed.get() / 20.0;
        double lerp = Math.min(1.0, Math.max(0.0, WingsConfig.SERVER.accelLerp.get() / 20.0));
        Vec3[] expected = {player.position().add(speed[0], 0, 0)};
        player.setDeltaMovement(new Vec3(speed[0], 0, 0));
        player.setPos(expected[0]);
        player.getFoodData().setFoodLevel(WingsConfig.SERVER.minFoodForFastTiers.get() - 1);
        tick(player);
        h.assertTrue(WingsState.tier(player) == WingsState.CRUISE,
                "low food must select CRUISE before testing released-input coasting");
        assertAccepted(h, player, expected[0], "coasting pre-transition STORM movement");
        long started = h.getLevel().getGameTime();
        for (int i = 1; i <= 40; i++) {
            int step = i;
            h.runAfterDelay(step, () -> {
                // Mirror flightWish's capped no-direction target plus tick's 0.985 coasting damping.
                // This is server integration coverage of that recurrence, not native client input.
                double previousSpeed = speed[0];
                double wish = Math.min(speed[0], target);
                speed[0] = (speed[0] + (wish - speed[0]) * lerp) * 0.985;
                h.assertTrue(speed[0] <= previousSpeed,
                        "released-input coasting must not accelerate at step " + step);
                expected[0] = expected[0].add(speed[0], 0, 0);
                player.setDeltaMovement(new Vec3(speed[0], 0, 0));
                player.setPos(expected[0]);
                tick(player);
                h.assertTrue(WingsState.tier(player) == WingsState.CRUISE,
                        "coasting must retain the hunger-imposed CRUISE tier at step " + step);
                assertAccepted(h, player, expected[0], "released-input coasting step " + step);
                if (step == 40) {
                    h.assertTrue(h.getLevel().getGameTime() - started >= 40,
                            "released-input coasting must advance through 40 real server ticks");
                    System.out.println("WINGS_REVIEW_COASTING steps=" + step + " finalSpeed=" + speed[0]
                            + " speedFlag=" + WingsState.speedFlagAt(player));
                    h.succeed();
                }
            });
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reviewSameTickDoubleHoverCannotCreateStormAllowance(GameTestHelper h) {
        FakePlayer player = rememberedStormHover(h);
        Vec3 accepted = player.position().add(1.0, 0, 0);
        player.setPos(accepted);
        FlightEvents.settleClientMovement(player);
        assertAccepted(h, player, accepted, "initial HOVER movement before double H");
        float exhaustion = player.getFoodData().getExhaustionLevel();
        long toggleTick = h.getLevel().getGameTime();
        doubleHover(h, player);
        h.assertTrue(h.getLevel().getGameTime() == toggleTick,
                "both H actions must execute in the same server tick");
        player.setPos(accepted.add(0.5, 0, 0));
        FlightEvents.settleClientMovement(player);
        assertRejectedAt(h, player, accepted, "same-tick double H cannot refill spent HOVER credit");
        tick(player);
        assertHoverExhaustion(h, player, exhaustion, 1);
        h.runAfterDelay(1, () -> {
            player.setPos(accepted.add(6.0, 0, 0));
            FlightEvents.settleClientMovement(player);
            assertRejectedAt(h, player, accepted,
                    "instantaneous STORM selection cannot authorize the following HOVER tick");
            tick(player);
            assertHoverExhaustion(h, player, exhaustion, 2);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reviewEveryTickDoubleHoverCannotMaintainStormSpeed(GameTestHelper h) {
        assertDefaultTransitionConfig(h);
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 80.5, 2.5)), WingsState.STORM);
        FlightEvents.serverToggleHover(player);
        float exhaustion = player.getFoodData().getExhaustionLevel();
        double[] acceptedDistance = {0};
        for (int i = 1; i <= 60; i++) {
            int step = i;
            h.runAfterDelay(step, () -> {
                Vec3 before = player.position();
                doubleHover(h, player);
                Vec3 attempt = before.add(6.0, 0, 0);
                player.setPos(attempt);
                FlightEvents.settleClientMovement(player);
                boolean accepted = player.position().distanceTo(attempt) < 0.001;
                if (accepted) acceptedDistance[0] += 6.0;
                else assertRejectedAt(h, player, before, "double H over-budget movement step " + step);
                if (step >= 30) {
                    h.assertTrue(!accepted,
                            "double H every tick must not preserve full STORM movement at step " + step);
                }
                tick(player);
                assertHoverExhaustion(h, player, exhaustion, step);
                if (step == 60) {
                    // The unrenewed default decay and initial two-tick credit allow <160 blocks.
                    h.assertTrue(acceptedDistance[0] <= 160,
                            "double H must remain within the finite original transition budget");
                    System.out.println("WINGS_REVIEW_DOUBLE_H ticks=" + step
                            + " acceptedDistance=" + acceptedDistance[0]
                            + " tier=" + WingsState.tier(player)
                            + " exhaustion=" + (player.getFoodData().getExhaustionLevel() - exhaustion));
                    h.succeed();
                }
            });
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void reviewDoubleHoverAfterDecayCannotRearmStormAllowance(GameTestHelper h) {
        assertDefaultTransitionConfig(h);
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 80.5, 2.5)), WingsState.STORM);
        FlightEvents.serverToggleHover(player);
        h.runAfterDelay(80, () -> {
            Vec3 accepted = player.position().add(1.0, 0, 0);
            player.setPos(accepted);
            FlightEvents.settleClientMovement(player);
            assertAccepted(h, player, accepted, "settled HOVER movement after original STORM decay");
            float exhaustion = player.getFoodData().getExhaustionLevel();
            doubleHover(h, player);
            player.setPos(accepted.add(0.5, 0, 0));
            FlightEvents.settleClientMovement(player);
            assertRejectedAt(h, player, accepted, "expired-history double H preserves remaining credit");
            tick(player);
            assertHoverExhaustion(h, player, exhaustion, 1);
            h.runAfterDelay(1, () -> {
                player.setPos(accepted.add(6.0, 0, 0));
                FlightEvents.settleClientMovement(player);
                assertRejectedAt(h, player, accepted,
                        "double H after complete decay must not renew a STORM-speed grace period");
                tick(player);
                assertHoverExhaustion(h, player, exhaustion, 2);
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void reviewRealStormAccelerationRetainsHoverInertia(GameTestHelper h) {
        FakePlayer player = rememberedStormHover(h);
        float exhaustion = player.getFoodData().getExhaustionLevel();
        FlightEvents.serverToggleHover(player);
        h.assertTrue(WingsState.tier(player) == WingsState.STORM,
                "the positive control must actually remain in STORM while accelerating");
        double[] speed = {WingsConfig.SERVER.hoverMaxSpeed.get() / 20.0};
        double lerp = WingsConfig.SERVER.accelLerp.get() / 20.0;
        for (int i = 1; i <= 70; i++) {
            int step = i;
            h.runAfterDelay(step, () -> {
                double target = step <= 40 ? WingsConfig.SERVER.stormSpeed.get() / 20.0 : 0;
                speed[0] += (target - speed[0]) * lerp;
                Vec3 expected = player.position().add(speed[0], 0, 0);
                player.setDeltaMovement(new Vec3(speed[0], 0, 0));
                player.setPos(expected);
                FlightEvents.settleClientMovement(player);
                tick(player);
                assertAccepted(h, player, expected, "real-time acceleration/deceleration step " + step);
                if (step == 40) FlightEvents.serverToggleHover(player);
                h.assertTrue(WingsState.tier(player) == (step < 40 ? WingsState.STORM : WingsState.HOVER),
                        "positive-control tiers must follow real-time flight rather than instantaneous toggles");
                if (step == 70) {
                    double expectedExhaustion = 40 * WingsConfig.SERVER.stormExhaustPerSec.get() / 20.0
                            + 30 * WingsConfig.SERVER.hoverExhaustPerSec.get() / 20.0;
                    h.assertTrue(Math.abs(player.getFoodData().getExhaustionLevel()
                                    - exhaustion - expectedExhaustion) < 0.0001,
                            "real STORM acceleration must pay STORM exhaustion before HOVER deceleration");
                    h.succeed();
                }
            });
        }
    }

    private static void assertDefaultTransitionConfig(GameTestHelper h) {
        h.assertTrue(WingsConfig.SERVER.stormSpeed.get() == 120.0
                        && WingsConfig.SERVER.hoverMaxSpeed.get() == 5.0
                        && WingsConfig.SERVER.accelLerp.get() == 2.5,
                "double-H regressions require the default STORM/HOVER speeds and deceleration");
    }

    private static FakePlayer rememberedStormHover(GameTestHelper h) {
        assertDefaultTransitionConfig(h);
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 80.5, 2.5)), WingsState.HOVER);
        // Seed only the remembered H target, without granting any actual high-tier flight time.
        WingsState.setTier(player, WingsState.STORM);
        WingsState.setTier(player, WingsState.HOVER);
        return player;
    }

    private static void doubleHover(GameTestHelper h, FakePlayer player) {
        h.assertTrue(WingsState.tier(player) == WingsState.HOVER
                        && WingsState.prevTier(player) == WingsState.STORM,
                "double-H fixture must start in HOVER with STORM as its remembered tier");
        FlightEvents.serverToggleHover(player);
        h.assertTrue(WingsState.tier(player) == WingsState.STORM, "the first H must select STORM");
        FlightEvents.serverToggleHover(player);
        h.assertTrue(WingsState.tier(player) == WingsState.HOVER, "the second H must return to HOVER");
    }

    private static void assertRejectedAt(GameTestHelper h, FakePlayer player, Vec3 accepted, String label) {
        h.assertTrue(WingsState.speedFlagAt(player) > 0, label + " must raise a speed flag");
        h.assertTrue(player.position().distanceTo(accepted) < 0.001,
                label + " must roll back to the last accepted position");
    }

    private static void assertHoverExhaustion(GameTestHelper h, FakePlayer player,
            float before, int ticks) {
        h.assertTrue(WingsState.tier(player) == WingsState.HOVER,
                "zero-duration STORM toggles must finish in HOVER");
        double expected = ticks * WingsConfig.SERVER.hoverExhaustPerSec.get() / 20.0;
        h.assertTrue(Math.abs(player.getFoodData().getExhaustionLevel() - before - expected) < 0.0001,
                "HOVER ticks must retain their normal exhaustion cost, without subsidized STORM speed");
    }

    private static void assertPreTransitionMovement(GameTestHelper h, int expectedTier,
            Consumer<FakePlayer> transition, String label) {
        h.assertTrue(WingsConfig.SERVER.stormSpeed.get() == 120.0,
                "fixture requires default STORM=120 m/s");
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 32.5, 2.5)), WingsState.STORM);
        Vec3 expected = player.position().add(6.0, 0, 0);
        player.setPos(expected);
        transition.accept(player);
        tick(player);
        h.assertTrue(WingsState.tier(player) == expectedTier, label + " must select the requested tier");
        assertAccepted(h, player, expected, label + " pre-transition movement");
        System.out.println("WINGS_REVIEW_TRANSITION label=" + label + " actual=" + player.position()
                + " speedFlag=" + WingsState.speedFlagAt(player));
        h.succeed();
    }

    private static void assertDeceleration(GameTestHelper h, int expectedTier,
            Consumer<FakePlayer> transition, String label) {
        FakePlayer player = winged(h, h.absoluteVec(new Vec3(2.5, 32.5, 2.5)), WingsState.STORM);
        double[] speed = {WingsConfig.SERVER.stormSpeed.get() / 20.0};
        Vec3[] expected = {player.position().add(speed[0], 0, 0)};
        player.setDeltaMovement(new Vec3(speed[0], 0, 0));
        player.setPos(expected[0]);
        transition.accept(player);
        h.assertTrue(WingsState.tier(player) == expectedTier, label + " must change the flight tier");
        assertAccepted(h, player, expected[0], label + " pre-transition movement");
        double target = expectedTier == WingsState.HOVER ? 0.0
                : WingsConfig.SERVER.tierSpeed(expectedTier) / 20.0;
        double lerp = Math.min(1.0, Math.max(0.0, WingsConfig.SERVER.accelLerp.get() / 20.0));
        long started = h.getLevel().getGameTime();
        for (int i = 1; i <= 30; i++) {
            int step = i;
            h.runAfterDelay(step, () -> {
                // Reproduce the real client's velocity interpolation without invoking client code.
                speed[0] += (target - speed[0]) * lerp;
                expected[0] = expected[0].add(speed[0], 0, 0);
                player.setDeltaMovement(new Vec3(speed[0], 0, 0));
                player.setPos(expected[0]);
                tick(player);
                h.assertTrue(WingsState.tier(player) == expectedTier,
                        label + " must keep the downgraded tier at deceleration step " + step);
                assertAccepted(h, player, expected[0], label + " deceleration step " + step);
                if (step == 30) {
                    h.assertTrue(h.getLevel().getGameTime() - started >= 30,
                            "deceleration must advance through real server ticks");
                    System.out.println("WINGS_REVIEW_DECELERATION label=" + label + " steps=" + step
                            + " finalSpeed=" + speed[0] + " speedFlag=" + WingsState.speedFlagAt(player));
                    h.succeed();
                }
            });
        }
    }

    private static void assertAccepted(GameTestHelper h, FakePlayer player, Vec3 expected, String label) {
        h.assertTrue(WingsState.speedFlagAt(player) == 0, label + " must not raise a speed flag");
        h.assertTrue(player.position().distanceTo(expected) < 0.001,
                label + " must not roll back legitimate displacement");
    }

    private static ImpactPlayer impactPlayer(GameTestHelper h, Vec3 position) {
        ImpactPlayer player = new ImpactPlayer(h.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position);
        player.setOnGround(false);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
        player.getFoodData().setFoodLevel(20);
        FlightEvents.serverSetDeployed(player, true);
        WingsState.setTier(player, WingsState.STORM);
        tick(player);
        h.assertTrue(WingsState.deployed(player) && WingsState.speedFlagAt(player) == 0,
                "impact observer fixture must be deployed without speed flags");
        return player;
    }

    private static final class ImpactPlayer extends FakePlayer {
        private int wallImpactCalls;

        private ImpactPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "WingReviewImpact"));
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source.is(DamageTypes.FLY_INTO_WALL)) wallImpactCalls++;
            // FakePlayer is invulnerable; count settlement calls instead of asserting health loss.
            return super.hurt(source, amount);
        }
    }
}
