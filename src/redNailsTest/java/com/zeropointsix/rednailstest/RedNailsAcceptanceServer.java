package com.zeropointsix.rednailstest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zeropointsix.eraser.rednails.RedNailsItem;
import com.zeropointsix.eraser.rednails.RedNailsInput;
import com.zeropointsix.eraser.rednails.RedNailsState;
import com.zeropointsix.eraser.rednails.WaterGhostBody;
import com.zeropointsix.eraser.rednails.WaterGhostEntity;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaPocketWatchItem;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/** Real input and normal AI only. Setup never calls grab/start/tick or sets a ghost target. */
@Mod.EventBusSubscriber(modid = "red_nails_qa")
public final class RedNailsAcceptanceServer {
    private static final boolean ENABLED = Boolean.getBoolean("rednails.qa.acceptance");
    private static final String CASE = System.getProperty("rednails.qa.case", "all");
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static volatile ServerPlayer player;
    private static WaterGhostEntity ghost;
    private static Boat boat;
    private static ItemStack ring;
    private static net.minecraft.world.entity.Entity redSummon, watchSummon;
    private static volatile String scenario = "boat";
    private static volatile int stage;
    private static int ticks, samples, airSamples, resistedTicks, convergedTicks, belowDamageTicks;
    private static long stageNanos, grabbedAt = -1;
    private static long releasedAt = -1, releaseCooldown, coexistAt = -1;
    private static boolean releasedLease, releasedOnFloor, lastHeldOnFloor;
    private static Vec3 boatStart;
    private static Vec3 previousBoatPosition;
    private static long boatMovedAt;
    private static long damageAt;
    private static int firstResistanceTick, lastResistanceTick;
    private static int distinctResistanceTicks, lastValidResistanceTick;
    private static double boatDistance, startY, minY, maxDistance;
    private static double playerStartY, playerMinY;
    private static String sampleFailure;
    private static float damage;
    private static boolean failed, beforeHeld, beforeWet;
    private static int beforeAir;
    private static boolean boatContact;
    private static int boatColumnSamples, clearFallbackSamples, releasedInputSamples, lastReleasedClientTick;
    private static Vec3 releasedPosition;
    private static long decisionTick = -1;
    private static boolean decisionHeld;
    private static double decisionDistanceSquared;
    private static JsonObject captureClientSnapshot;
    private static long captureSnapshotTick = -1, captureSnapshotSeenAt = -1, captureTraceSequence;
    private static boolean damageStartedHeld;
    private static UUID lifecycleItem, lifecycleOwner, lifecycleGhost;
    private static long[] lifecycleReady;
    private static float lifecycleHealth;
    private static long lifecycleGuiPress = Long.MIN_VALUE, lifecycleGuiRelease = Long.MIN_VALUE;
    private static int lifecycleDimensionEvents, lifecycleLogoutEvents, lifecycleDamageEvents;
    private static boolean lifecycleDimensionActive, lifecycleLogoutActive;
    private static final int SPEED_WINDOW = 100;
    private static final int SPEED_MATURITY_TICKS = 30;
    private static final double SPEED_LIMIT = 0.35;
    private static final double SPEED_APPROX_MIN_PER_SECOND = 6.5, SPEED_APPROX_MAX_PER_SECOND = 7.1;
    private static final int SPEED_MIN_NATIVE_SAMPLES = 20;
    private static final int SPEED_BOUNDARY_TICKS = AcceptanceObservations.SPEED_BOUNDARY_TICKS;
    private static final double SPEED_CADENCE_DRIFT = AcceptanceObservations.SPEED_CADENCE_DRIFT;
    private static final JsonArray speedResults = new JsonArray();
    private static final List<String> speedFailures = new ArrayList<>();
    private static SpeedRun speedRun;

    private static final class SpeedStats {
        int count;
        double sum, max;
        void add(double value) { count++; sum += value; max = Math.max(max, value); }
        JsonObject json() {
            var result = new JsonObject();
            result.addProperty("samples", count);
            result.addProperty("maxBlocksPerTick", max);
            result.addProperty("meanBlocksPerTick", count == 0 ? 0 : sum / count);
            return result;
        }
    }
    private static final class SpeedRun {
        final String name;
        final long warmupAt;
        final List<String> issues = new ArrayList<>();
        final JsonArray findings = new JsonArray();
        final JsonArray targetDamageEvents = new JsonArray();
        final SpeedStats ghostTravel = new SpeedStats(), ghostHorizontal = new SpeedStats();
        final SpeedStats matureTravel = new SpeedStats(), matureHorizontal = new SpeedStats();
        final SpeedStats targetTravel = new SpeedStats();
        long spawnedAt = -1, observationAt = -1, targetAt = -1, pursuitAt = -1, caughtAt = -1, lastTick = -1;
        long preCatchEndTick = -1, decisionTick = -1;
        int consecutivePursuit, inputSamples, lastClientTick = -1, observationClientTick = -1, observationSamples;
        int preCatchClientTick = -1, validServerModeTicks, invalidServerModeTicks;
        double initialDistance, observationDistance, finalDistance, lastGhostAdvance, closestDistance = Double.POSITIVE_INFINITY;
        double targetForwardDistance;
        Vec3 lastPlayer, lastBoat, lastGhost;
        Vec3 spawnPlayer, spawnGhost, observationPlayer, observationBoat, observationGhost;
        boolean lastHeld, matureStart, inconclusive, decisionModeValid, captureEntryObserved, captureEntryModeValid;
        JsonObject startInput, report;
        SpeedRun(String name, long now) { this.name = name; warmupAt = now; }
        boolean swimmer() { return name.equals("swimmer"); }
        boolean calibration() { return name.equals("calibration"); }
    }

    private static boolean frontal() { return scenario.startsWith("front"); }
    private static boolean shallow() { return scenario.equals("bottom") || scenario.equals("front-bottom"); }

    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static void sampleCheck(boolean ok, String why) {
        if (!ok) {
            if (sampleFailure == null) sampleFailure = why;
            System.out.println("RED_NAILS_ACCEPTANCE_SAMPLE_FAILURE " + why);
        }
    }
    private static void mark(String name, String detail) throws Exception {
        Files.writeString(RESULTS.resolve("acceptance-" + name + ".pass"), detail + "\n");
        System.out.println("RED_NAILS_ACCEPTANCE_PASS " + name + " " + detail);
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("acceptance-server.failed"), error.toString()); }
        catch (Exception ignored) { }
    }
    private static void phase(String name) {
        ticks = 0; stageNanos = System.nanoTime();
        player.sendSystemMessage(Component.literal("RED_NAILS_ACCEPTANCE:" + name));
        System.out.println("RED_NAILS_ACCEPTANCE_PHASE=" + name);
    }
    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (!ENABLED) return;
        check(java.util.Set.of("all", "boat", "bottom", "front", "front-bottom", "damage", "items", "lifecycle", "speed").contains(CASE), "unknown acceptance case " + CASE);
        var level = event.getServer().overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, event.getServer());
        level.setDefaultSpawnPos(new BlockPos(24, 75, 0), 0);
        if (CASE.equals("speed")) {
            // Closed, source-water channel: no bank, current or short-course interception.
            for (var p : BlockPos.betweenClosed(-12, 60, -64, 12, 78, 192)) {
                boolean wall = p.getX() == -12 || p.getX() == 12 || p.getZ() == -64 || p.getZ() == 192;
                var block = p.getY() == 60 || wall && p.getY() <= 75 ? Blocks.STONE
                        : p.getY() <= 74 ? Blocks.WATER : Blocks.AIR;
                level.setBlock(p, block.defaultBlockState(), 2);
            }
            for (var p : BlockPos.betweenClosed(22, 74, -8, 38, 74, 12)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
            return;
        }
        for (var p : BlockPos.betweenClosed(-18, 60, -18, 18, 74, 24)) {
            level.setBlock(p, (p.getY() == 60 ? Blocks.STONE : Blocks.WATER).defaultBlockState(), 2);
        }
        for (var p : BlockPos.betweenClosed(22, 74, -8, 38, 74, 12)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("NailsTester")) return;
        if (CASE.equals("lifecycle") && stage != 0) {
            try {
                check(stage == 50 && lifecycleLogoutEvents == 1, "rejoin follows the observed real logout");
                player = p;
                ring = RedNailsItem.findWorn(p);
                check(!ring.isEmpty() && p.getUUID().equals(lifecycleOwner), "same owner rejoins with the saved worn ring");
                check(p.level().dimension().equals(Level.NETHER), "rejoin retains the actual destination dimension");
                checkLifecycleCleared("rejoin login");
                stage = 51; phase("lifecycle-rejoin");
            } catch (Throwable error) { fail(error); }
            return;
        }
        player = p; p.setGameMode(GameType.SURVIVAL); stageNanos = System.nanoTime();
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void beforeDamage(LivingDamageEvent event) {
        if (ENABLED && CASE.equals("lifecycle") && stage >= 40 && stage < 99 && event.getEntity() == player) lifecycleDamageEvents++;
        if (ENABLED && ghost != null && event.getEntity() == ghost && event.getSource().getEntity() == player) {
            damageStartedHeld = ghost.ghostBody().grab.held() == player;
            System.out.println("RED_NAILS_ACCEPTANCE_DAMAGE_ENTRY held=" + damageStartedHeld
                    + " priorDamage=" + damage + " amount=" + event.getAmount());
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damaged(LivingDamageEvent event) {
        if (ENABLED && CASE.equals("speed") && speedRun != null && stage == 61 && event.getEntity() == player) {
            var observed = new JsonObject();
            observed.addProperty("serverTick", player.level().getGameTime());
            observed.addProperty("amount", event.getAmount());
            observed.addProperty("source", event.getSource().getMsgId());
            observed.addProperty("attackerIsGhost", event.getSource().getEntity() == ghost);
            observed.addProperty("held", ghost != null && ghost.ghostBody().grab.held() == player);
            speedRun.targetDamageEvents.add(observed);
        }
        if (ENABLED && event.getEntity() == ghost && grabbedAt >= 0 && event.getSource().getEntity() == player) {
            damage += event.getAmount();
            damageAt = player.level().getGameTime();
            System.out.println("RED_NAILS_ACCEPTANCE_DAMAGE tick=" + player.level().getGameTime()
                    + " amount=" + event.getAmount() + " cumulative=" + damage);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void beforeGhostTick(LivingEvent.LivingTickEvent event) {
        if (!ENABLED || ghost == null || event.getEntity() != ghost || player == null) return;
        decisionTick = ghost.level().getGameTime();
        decisionHeld = ghost.ghostBody().grab.held() == player;
        decisionDistanceSquared = ghost.distanceToSqr(player);
        if (stage >= 1 && stage <= 5) {
            try { captureDiagnostic("AI_ENTRY"); }
            catch (Exception error) { fail(error); }
        }
        if (CASE.equals("speed") && speedRun != null && stage == 61) {
            speedRun.decisionTick = decisionTick;
            speedRun.decisionModeValid = validSpeedServerMode();
        }
    }
    private static void setupCapture(String name) {
        scenario = name;
        if (ghost != null) ghost.discard();
        if (boat != null) boat.discard();
        player.stopRiding();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.setAirSupply(player.getMaxAirSupply());
        var level = player.serverLevel();
        if (shallow()) {
            for (var p : BlockPos.betweenClosed(-18, 71, -18, 18, 71, 24)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        } else {
            for (var p : BlockPos.betweenClosed(-18, 71, -18, 18, 71, 24)) level.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
        }
        float approachYaw = frontal() ? 45 : 0;
        player.teleportTo(level, 0.5, 75.1, -8.5, approachYaw, 0);
        boat = new Boat(level, 0.5, 74.5, -8.5);
        boat.setYRot(approachYaw); level.addFreshEntity(boat);
        check(player.startRiding(boat, true), "fixture player mounts a real vanilla boat");
        boatStart = boat.position(); boatDistance = 0;
        previousBoatPosition = boatStart; boatMovedAt = -1;
        grabbedAt = -1; ghost = null; samples = 0; airSamples = 0; resistedTicks = 0;
        releasedAt = -1;
        damage = 0; belowDamageTicks = 0; maxDistance = 0;
        convergedTicks = 0; sampleFailure = null;
        distinctResistanceTicks = 0; lastValidResistanceTick = -1; lastHeldOnFloor = false;
        firstResistanceTick = -1; lastResistanceTick = -1;
        captureClientSnapshot = null; captureSnapshotTick = -1; captureSnapshotSeenAt = -1;
        boatContact = false; boatColumnSamples = 0; clearFallbackSamples = 0;
        releasedInputSamples = 0; lastReleasedClientTick = -1;
        stage = 1; phase(name + "-ready");
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || player == null || failed || stage == 99) return;
        if (event.phase == TickEvent.Phase.START) {
            beforeHeld = ghost != null && ghost.ghostBody().grab.held() == player;
            beforeWet = beforeHeld && ghost.ghostBody().swimming() && player.isEyeInFluid(FluidTags.WATER);
            beforeAir = player.getAirSupply();
            if (stage >= 1 && stage <= 5) {
                try { captureDiagnostic("START"); }
                catch (Exception error) { fail(error); }
            }
            return;
        }
        try {
            ticks++;
            if (stage >= 1 && stage <= 5) captureDiagnostic("END");
            check(System.nanoTime() - stageNanos < 300_000_000_000L, "native acceptance stage timeout " + stage + " " + scenario);
            check(player.isAlive(), "real player survives the acceptance case");
            if (stage == 0 && Files.exists(RESULTS.resolve("acceptance-client-ready.signal"))) {
                if (CASE.equals("speed")) setupSpeed("swimmer");
                else if (CASE.equals("lifecycle")) setupLifecycle();
                else if (CASE.equals("items")) setupItems();
                else setupCapture(CASE.equals("all") ? "boat" : CASE);
            }
            else if (stage == 1) {
                boatDistance = Math.max(boatDistance, boat.position().subtract(boatStart).horizontalDistance());
                if (boatDistance >= 2 && ghost == null) {
                    ghost = ModEntities.WATER_GHOST.get().create(player.serverLevel());
                    check(ghost != null, "registered ghost creates");
                    // Approach the real hull diagonally with positive clearance on both axes.
                    // The lower start stays outside grab reach until the first native boat step reaches the hull.
                    double halfWidth = Math.max(ghost.getBbWidth(), WaterGhostBody.SWIMMING.width) * 0.5;
                    double spawnX = frontal() ? boat.getBoundingBox().minX - halfWidth - 0.20 : player.getX() + 1.4;
                    double spawnZ = frontal() ? boat.getBoundingBox().maxZ + halfWidth + 0.20 : player.getZ() + 4.5;
                    ghost.moveTo(spawnX, frontal() ? 73.35 : 73.5, spawnZ, frontal() ? 225 : 180, 0);
                    player.serverLevel().addFreshEntity(ghost);
                    check(!ghost.isNoAi() && ghost.getTarget() == null, "normal ghost AI starts without forced target");
                    check(frontal() || ghost.getBoundingBox().minX > boat.getBoundingBox().maxX,
                            "fixture starts with lateral clearance from the real boat");
                    System.out.println("RED_NAILS_ACCEPTANCE_APPROACH ghostBox=" + ghost.getBoundingBox()
                            + " boatBox=" + boat.getBoundingBox() + " lateralClearance="
                            + (ghost.getBoundingBox().minX - boat.getBoundingBox().maxX));
                    System.out.println("RED_NAILS_ACCEPTANCE_MOVING_BOAT distance=" + boatDistance + " speed=" + boat.getDeltaMovement().horizontalDistance());
                    stage = 2; phase(scenario + "-moving");
                }
            } else if (stage == 2 || stage == 3) {
                boatContact |= ghost.getBoundingBox().inflate(0.00001).intersects(boat.getBoundingBox());
                double advance = boat.position().subtract(previousBoatPosition).horizontalDistance();
                if (advance > 0.01) boatMovedAt = player.level().getGameTime();
                previousBoatPosition = boat.position();
                captureTick();
            }
            else if (stage == 4 && Files.exists(RESULTS.resolve("acceptance-client-" + scenario + ".signal"))) {
                releasedPosition = player.position(); stage = 5; phase(scenario + "-released");
            } else if (stage == 5) {
                releasedTick();
            } else if (CASE.equals("speed") && stage >= 60) speedTick();
            else if (CASE.equals("lifecycle") && stage >= 40) lifecycleTick();
            else if (stage >= 20) itemTick();
        } catch (Throwable error) { fail(error); }
    }
    private static JsonObject speedPolicy() {
        var policy = new JsonObject();
        policy.addProperty("scope", "Adjudicated 0.35 blocks/tick cap; boat capture acceptance is head-on or lateral interception, not mature rear catch-up.");
        policy.addProperty("boatCaptureGates", "Required native boat, front and front-bottom matrix cases retain natural capture and moving-dismount assertions.");
        policy.addProperty("rearBoatRole", "Diagnostic natural pursuit only; never reported as boat capture acceptance.");
        policy.addProperty("productionReferenceBlocksPerTick", SPEED_LIMIT);
        policy.addProperty("nominalTicksPerSecond", 20);
        policy.addProperty("maturePeakMinBlocksPerNominalSecond", SPEED_APPROX_MIN_PER_SECOND);
        policy.addProperty("maturePeakMaxBlocksPerNominalSecond", SPEED_APPROX_MAX_PER_SECOND);
        policy.addProperty("excludedInitialPursuitTicks", SPEED_MATURITY_TICKS);
        policy.addProperty("minimumMatureAndPreCatchSamples", SPEED_MIN_NATIVE_SAMPLES);
        policy.addProperty("allowedAggregateCadenceDrift", SPEED_CADENCE_DRIFT);
        policy.addProperty("packetAndPhaseBoundaryAllowanceTicks", SPEED_BOUNDARY_TICKS);
        policy.addProperty("cadenceRule", "Both clocks normally tick at 20 Hz: abs(clientTicks-serverTicks) <= 0.10*serverTicks+2; also check pre-catch forward displacement against native client speed with the same allowance.");
        policy.addProperty("modeRule", "Every pre-catch native sample must retain sprint-swim or the same moving boat. Only calibration's initial stationary-to-W transition allows two client ticks.");
        policy.addProperty("calibrationReference", "Calibration begins stationary: compare its aligned pre-catch client displacement, and require a later stable native boat window; never treat stationary warmup as a speed reference.");
        policy.addProperty("swimmerAndBoatReference", "Compare server and native client displacement over the same pre-catch window; warmup is a separate minimum-speed check. Natural acceleration is not client slowdown.");
        return policy;
    }
    private static void speedFinding(String classification, String detail) {
        var finding = new JsonObject();
        finding.addProperty("classification", classification);
        finding.addProperty("detail", detail);
        speedRun.findings.add(finding);
        speedRun.issues.add(classification + ": " + detail);
        if (classification.equals("measurement_inconclusive")) speedRun.inconclusive = true;
    }
    private static boolean validSpeedServerMode() {
        return speedRun.swimmer()
                ? player.isSwimming() && player.isSprinting() && player.isEyeInFluid(FluidTags.WATER) && !player.isPassenger()
                : boat != null && player.getVehicle() == boat && boat.isInWater();
    }
    private static boolean speedRateMatches(double measured, double expected, int serverTicks) {
        return AcceptanceObservations.speedRateMatches(measured, expected, serverTicks);
    }
    private static void setupSpeed(String name) throws Exception {
        if (name.equals("swimmer")) {
            check(WaterGhostBody.SWIM_SPEED == SPEED_LIMIT, "production speed retains the adjudicated 0.35 blocks/tick cap");
            Files.writeString(RESULTS.resolve("acceptance-speed-policy.json"), speedPolicy() + "\n");
            System.out.println("RED_NAILS_SPEED_PREDECLARED_POLICY " + speedPolicy());
        }
        if (ghost != null) ghost.discard();
        if (boat != null) boat.discard();
        ghost = null; boat = null;
        player.stopRiding();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.setAirSupply(player.getMaxAirSupply());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5);
        speedRun = new SpeedRun(name, player.level().getGameTime());
        scenario = "speed-" + name;
        var level = player.serverLevel();
        player.teleportTo(level, 0.5, speedRun.swimmer() ? 73 : 75.1, -40.5, 0, 0);
        if (!speedRun.swimmer()) {
            boat = new Boat(level, 0.5, 74.5, -40.5);
            boat.setYRot(0);
            level.addFreshEntity(boat);
            check(player.startRiding(boat, true), "speed fixture mounts a real vanilla boat");
        }
        stage = 60;
        phase(scenario + "-warmup");
    }
    private static void spawnSpeedGhost() {
        ghost = ModEntities.WATER_GHOST.get().create(player.serverLevel());
        check(ghost != null, "speed fixture creates registered ghost");
        ghost.moveTo(player.getX(), speedRun.swimmer() ? player.getY() : 73.5, player.getZ() - 5, 0, 0);
        player.serverLevel().addFreshEntity(ghost);
        check(!ghost.isNoAi() && ghost.getTarget() == null, "speed ghost starts with unmodified normal AI");
        speedRun.spawnedAt = player.level().getGameTime();
        speedRun.initialDistance = ghost.distanceTo(player);
        speedRun.spawnPlayer = player.position(); speedRun.spawnGhost = ghost.position();
        speedRun.lastGhost = ghost.position();
        check(ghost.getZ() < player.getZ() && speedRun.initialDistance >= 5 && speedRun.initialDistance <= 6,
                "speed ghost starts five blocks behind inside the unchanged six-block activation range");
        System.out.println("RED_NAILS_SPEED_SPAWN case=" + speedRun.name + " serverTick=" + speedRun.spawnedAt
                + " distance=" + speedRun.initialDistance + " ghost=" + ghost.position() + " player=" + player.position()
                + " boat=" + (boat == null ? "" : boat.getUUID()) + " matureStart=" + speedRun.matureStart);
    }
    private static JsonObject speedInput() throws Exception {
        var path = RESULTS.resolve("acceptance-client-speed-" + speedRun.name + "-input.signal");
        return Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : null;
    }
    private static String csvPosition(Vec3 pos) {
        return pos == null ? ",," : pos.x + "," + pos.y + "," + pos.z;
    }
    private static void observeSpeed(long now, JsonObject input) throws Exception {
        var run = speedRun;
        boolean held = ghost != null && ghost.ghostBody().grab.held() == player;
        boolean firstCatch = held && run.caughtAt < 0;
        boolean selected = ghost != null && ghost.getTarget() == player;
        boolean pursuing = selected && ghost.mayPursue();
        if (selected && run.targetAt < 0) run.targetAt = now;
        if (pursuing && run.pursuitAt < 0) run.pursuitAt = now;
        if (held && run.caughtAt < 0) run.caughtAt = now;
        boolean consecutive = run.lastTick >= 0 && now == run.lastTick + 1;
        Vec3 playerStep = consecutive && run.lastPlayer != null ? player.position().subtract(run.lastPlayer) : Vec3.ZERO;
        Vec3 boatStep = consecutive && boat != null && run.lastBoat != null ? boat.position().subtract(run.lastBoat) : Vec3.ZERO;
        Vec3 ghostStep = consecutive && ghost != null && run.lastGhost != null ? ghost.position().subtract(run.lastGhost) : Vec3.ZERO;
        run.lastGhostAdvance = ghostStep.z;
        boolean chasing = pursuing && ghost.ghostBody().swimming() && !held && !run.lastHeld;
        run.consecutivePursuit = chasing && consecutive ? run.consecutivePursuit + 1 : 0;
        boolean observing = stage == 61;
        boolean mature = observing && run.consecutivePursuit > SPEED_MATURITY_TICKS;
        boolean preCatch = observing && run.caughtAt < 0;
        boolean serverModeValid = validSpeedServerMode();
        if (observing) {
            run.observationSamples++;
            if (ghost != null && run.lastGhost != null && consecutive && !held && !run.lastHeld) {
                run.ghostTravel.add(ghostStep.length());
                run.ghostHorizontal.add(ghostStep.horizontalDistance());
                if (mature) {
                    run.matureTravel.add(ghostStep.length());
                    run.matureHorizontal.add(ghostStep.horizontalDistance());
                }
            }
            if (preCatch) {
                if (serverModeValid) run.validServerModeTicks++; else run.invalidServerModeTicks++;
                run.preCatchEndTick = now;
                if (input != null) run.preCatchClientTick = input.get("clientTick").getAsInt();
                if (consecutive) {
                    var movement = run.swimmer() ? playerStep : boatStep;
                    run.targetTravel.add(movement.horizontalDistance());
                    run.targetForwardDistance += movement.z;
                }
            }
            if (firstCatch) {
                run.captureEntryObserved = run.decisionTick == now;
                run.captureEntryModeValid = run.captureEntryObserved && run.decisionModeValid;
            }
            if (input != null) {
                int clientTick = input.get("clientTick").getAsInt();
                if (clientTick > run.lastClientTick && input.get("forwardInput").getAsBoolean()) run.inputSamples++;
                run.lastClientTick = Math.max(run.lastClientTick, clientTick);
            }
        }
        if (ghost != null) {
            run.finalDistance = ghost.distanceTo(player);
            run.closestDistance = Math.min(run.closestDistance, run.finalDistance);
        }
        var path = RESULTS.resolve("acceptance-speed-" + run.name + "-server.csv");
        if (!Files.exists(path)) Files.writeString(path,
                "serverTick,stage,observationTick,clientTick,clientPhase,clientForward,clientModeValid,playerX,playerY,playerZ,playerStep3d,playerStepHorizontal,playerSwimming,playerSprinting,playerEyeWater,passenger,boatUuid,boatX,boatY,boatZ,boatStepHorizontal,ghostUuid,ghostTick,ghostX,ghostY,ghostZ,ghostStep3d,ghostStepHorizontal,ghostSwimming,targetPlayer,mayPursue,held,consecutivePursuit,mature,distance,preCatch,serverModeValid,serverValidModeTicks,serverInvalidModeTicks,captureEntryObserved,captureEntryModeValid\n");
        Files.writeString(path, now + "," + stage + "," + (run.observationAt < 0 ? -1 : now - run.observationAt)
                + "," + (input == null ? -1 : input.get("clientTick").getAsInt())
                + "," + (input == null ? "" : input.get("phase").getAsString())
                + "," + (input != null && input.get("forwardInput").getAsBoolean())
                + "," + (input != null && input.get("modeValid").getAsBoolean())
                + "," + csvPosition(player.position()) + "," + playerStep.length() + "," + playerStep.horizontalDistance()
                + "," + player.isSwimming() + "," + player.isSprinting() + "," + player.isEyeInFluid(FluidTags.WATER)
                + "," + player.isPassenger() + "," + (boat == null ? "" : boat.getUUID())
                + "," + csvPosition(boat == null ? null : boat.position()) + "," + boatStep.horizontalDistance()
                + "," + (ghost == null ? "" : ghost.getUUID()) + "," + (ghost == null ? -1 : ghost.tickCount)
                + "," + csvPosition(ghost == null ? null : ghost.position()) + "," + ghostStep.length()
                + "," + ghostStep.horizontalDistance() + "," + (ghost != null && ghost.ghostBody().swimming())
                + "," + selected + "," + pursuing + "," + held + "," + run.consecutivePursuit + "," + mature
                + "," + (ghost == null ? "" : run.finalDistance) + "," + preCatch + "," + serverModeValid
                + "," + run.validServerModeTicks + "," + run.invalidServerModeTicks
                + "," + run.captureEntryObserved + "," + run.captureEntryModeValid + "\n", StandardOpenOption.APPEND);
        run.lastTick = now; run.lastPlayer = player.position();
        run.lastBoat = boat == null ? null : boat.position();
        run.lastGhost = ghost == null ? null : ghost.position();
        run.lastHeld = held;
    }
    private static void speedTick() throws Exception {
        long now = player.level().getGameTime();
        var run = speedRun;
        var input = speedInput();
        if (stage == 60 || stage == 61) observeSpeed(now, input);
        if (stage == 60) {
            // Let the real client acknowledge the new stationary boat before natural acquisition.
            if (run.calibration() && ghost == null && input != null && input.get("phaseTicks").getAsInt() >= 20
                    && input.get("boatUuid").getAsString().equals(boat.getUUID().toString())) {
                spawnSpeedGhost();
                return;
            }
            boolean ready = run.calibration()
                    ? ghost != null && run.pursuitAt >= 0 && run.consecutivePursuit > 0 && run.lastGhostAdvance > 0.01
                    : input != null && input.get("stable").getAsBoolean() && input.get("phaseTicks").getAsInt() >= 40
                        && (run.swimmer() ? player.isSwimming() && player.isSprinting() && !player.isPassenger()
                        : player.getVehicle() == boat && input.get("boatUuid").getAsString().equals(boat.getUUID().toString()));
            if (ready || now - run.warmupAt >= 200) {
                run.matureStart = !run.calibration() && ready;
                run.startInput = input == null ? null : input.deepCopy();
                if (!ready) speedFinding("measurement_inconclusive", run.calibration()
                        ? "stationary calibration acquisition did not start within 200 server ticks"
                        : "native target did not establish 20 consecutive stable swimming/boat ticks before the 200-tick warmup limit");
                if (ghost == null) spawnSpeedGhost();
                run.observationAt = now;
                run.observationDistance = ghost.distanceTo(player);
                run.observationPlayer = player.position(); run.observationGhost = ghost.position();
                run.observationBoat = boat == null ? null : boat.position();
                run.observationClientTick = input == null ? -1 : input.get("clientTick").getAsInt();
                run.lastClientTick = run.observationClientTick;
                run.preCatchClientTick = run.observationClientTick;
                run.preCatchEndTick = now;
                stage = 61;
                phase("speed-" + run.name + "-chase");
            }
        } else if (stage == 61 && now - run.observationAt >= SPEED_WINDOW) {
            finishSpeedObservation(now);
            // The fixed observation has ended; cleanup must not add a second hold while awaiting screenshots.
            ghost.discard(); ghost = null;
            stage = 62;
            phase("speed-" + run.name + "-finish");
        } else if (stage == 62) {
            var finished = RESULTS.resolve("acceptance-client-speed-" + run.name + "-finished.signal");
            if (Files.exists(finished) || ticks >= 200) {
                if (Files.exists(finished)) {
                    var client = JsonParser.parseString(Files.readString(finished)).getAsJsonObject();
                    run.report.add("client", client);
                    evaluateSpeedNativeEvidence(client);
                } else speedFinding("measurement_inconclusive", "native client completion evidence missing after 200 server ticks");
                classifySpeedResult();
                var issues = new JsonArray();
                for (String issue : run.issues) { issues.add(issue); speedFailures.add(run.name + ": " + issue); }
                run.report.add("failures", issues);
                run.report.add("findings", run.findings);
                speedResults.add(run.report);
                Files.writeString(RESULTS.resolve("acceptance-speed-" + run.name + ".json"), run.report + "\n");
                Files.writeString(RESULTS.resolve("acceptance-speed-" + run.name + "-observed.signal"), "Complete observation, not a pass.\n");
                if (ghost != null) ghost.discard();
                ghost = null;
                if (run.swimmer()) setupSpeed("boat");
                else if (!run.calibration()) setupSpeed("calibration");
                else {
                    var summary = new JsonObject();
                    summary.addProperty("units", "actual END tick position differences, blocks/tick; no wall-clock speed");
                    summary.addProperty("productionReferenceBlocksPerTick", SPEED_LIMIT);
                    summary.addProperty("nominalBlocksPerSecondAt20Ticks", SPEED_LIMIT * 20);
                    summary.add("predeclaredQaPolicy", speedPolicy());
                    summary.addProperty("observationServerTicksPerSegment", SPEED_WINDOW);
                    summary.addProperty("maturityExcludesFirstConsecutivePursuitTicks", SPEED_MATURITY_TICKS);
                    summary.addProperty("calibrationScope", "Stationary boat acquires natural pursuit before W; mature motion calibration only, not mature-boat catch-up acceptance.");
                    summary.add("segments", speedResults);
                    summary.addProperty("passed", speedFailures.isEmpty());
                    Files.writeString(RESULTS.resolve("acceptance-speed-summary.json"), summary + "\n");
                    player.stopRiding();
                    if (boat != null) boat.discard();
                    boat = null;
                    check(speedFailures.isEmpty(), "speed/pursuit failed after ALL three observations: " + String.join("; ", speedFailures));
                    mark("speed", "Natural swimmer catch, diagnostic rear boat pursuit and independent capped-speed calibration; boat interception remains mandatory in boat/front/front-bottom native cases.");
                    stage = 99; phase("complete");
                }
            }
        }
    }
    private static void evaluateSpeedNativeEvidence(JsonObject client) {
        var run = speedRun;
        int preServerTicks = (int) (run.preCatchEndTick - run.observationAt);
        int preClientTicks = run.preCatchClientTick - run.observationClientTick;
        int fullClientTicks = run.lastClientTick - run.observationClientTick;
        if (run.observationClientTick < 0 || !speedRateMatches(fullClientTicks, SPEED_WINDOW, SPEED_WINDOW))
            speedFinding("measurement_inconclusive", "client/server full-window tick cadence differs: " + fullClientTicks + "/" + SPEED_WINDOW);
        if (preServerTicks < SPEED_MIN_NATIVE_SAMPLES || preClientTicks < SPEED_MIN_NATIVE_SAMPLES)
            speedFinding("measurement_inconclusive", "need at least 20 pre-catch ticks on BOTH clocks: client=" + preClientTicks + " server=" + preServerTicks);
        if (!speedRateMatches(preClientTicks, preServerTicks, preServerTicks))
            speedFinding("measurement_inconclusive", "pre-catch client/server tick cadence differs: " + preClientTicks + "/" + preServerTicks);
        if (run.targetTravel.count != preServerTicks)
            speedFinding("measurement_inconclusive", "pre-catch server position samples are not consecutive");
        if (run.invalidServerModeTicks > 0)
            speedFinding("measurement_inconclusive", "pre-catch server lost sprint-swim or the original boat for " + run.invalidServerModeTicks + " ticks");
        if (run.caughtAt >= 0 && !run.captureEntryModeValid)
            speedFinding("measurement_inconclusive", "capture lacks an observed valid native mode immediately BEFORE the ghost's grab tick");
        if (!client.has("tickSamples")) {
            speedFinding("measurement_inconclusive", "complete native client tick history is missing");
            return;
        }
        int fullSamples = 0, previousFullTick = run.observationClientTick, preSamples = 0;
        int valid = 0, invalid = 0, transitions = 0, stableWindows = 0, gaps = 0;
        double clientForwardDistance = 0;
        for (var element : client.getAsJsonArray("tickSamples")) {
            var sample = element.getAsJsonObject();
            int clientTick = sample.get("clientTick").getAsInt();
            if (clientTick <= run.observationClientTick || clientTick > run.lastClientTick) continue;
            if (clientTick != previousFullTick + 1 || !sample.get("consecutive").getAsBoolean()) gaps++;
            previousFullTick = clientTick; fullSamples++;
            if (clientTick > run.preCatchClientTick) continue;
            preSamples++;
            boolean nativeMode = sample.get("modeValid").getAsBoolean() && sample.get("forwardInput").getAsBoolean()
                    && (run.swimmer() || sample.get("boatUuid").getAsString().equals(boat.getUUID().toString()));
            if (nativeMode) valid++;
            else if (run.calibration() && clientTick - run.observationClientTick <= SPEED_BOUNDARY_TICKS) transitions++;
            else invalid++;
            if (nativeMode && sample.get("stable").getAsBoolean()) stableWindows++;
            clientForwardDistance += sample.get("targetForwardStep").getAsDouble();
        }
        if (gaps > 0 || fullSamples != fullClientTicks || preSamples != preClientTicks)
            speedFinding("measurement_inconclusive", "native tick history has missing/nonconsecutive samples: full=" + fullSamples
                    + "/" + fullClientTicks + " preCatch=" + preSamples + "/" + preClientTicks + " gaps=" + gaps);
        if (invalid > 0 || valid < SPEED_MIN_NATIVE_SAMPLES)
            speedFinding("measurement_inconclusive", "pre-catch native input/mode validity: valid=" + valid + " invalid=" + invalid
                    + " calibrationStartupTransitions=" + transitions);
        double nativeMean = preSamples == 0 ? 0 : clientForwardDistance / preSamples;
        double warmupReference = run.startInput == null ? 0
                : run.startInput.get("stableWindowMeanBlocksPerTick").getAsDouble();
        if (run.calibration() && stableWindows == 0)
            speedFinding("measurement_inconclusive", "stationary-start calibration never established a later 20-tick stable native boat window");
        double serverMean = preServerTicks == 0 ? 0 : run.targetForwardDistance / preServerTicks;
        if (!speedRateMatches(serverMean, nativeMean, preServerTicks))
            speedFinding("measurement_inconclusive", "target actually advanced at " + serverMean + " blocks/server-tick versus native reference "
                    + nativeMean + "; aligned client/server displacement differs beyond the unchanged cadence allowance");
        if (!run.calibration() && !AcceptanceObservations.maintainsWarmupSpeed(nativeMean, warmupReference, preServerTicks))
            speedFinding("measurement_inconclusive", "pre-catch native client slowed below its stable warmup reference: " + nativeMean + " versus " + warmupReference);
        var evidence = new JsonObject();
        evidence.addProperty("preCatchServerTicks", preServerTicks);
        evidence.addProperty("preCatchClientTicks", preClientTicks);
        evidence.addProperty("preCatchClientToServerTickRatio", preServerTicks == 0 ? 0 : (double) preClientTicks / preServerTicks);
        evidence.addProperty("completeClientSamples", fullSamples);
        evidence.addProperty("preCatchClientSamples", preSamples);
        evidence.addProperty("validNativeModeSamples", valid);
        evidence.addProperty("invalidNativeModeSamples", invalid);
        evidence.addProperty("calibrationStartupTransitionSamples", transitions);
        evidence.addProperty("nonconsecutiveClientSamples", gaps);
        evidence.addProperty("stableNativeWindowObservations", stableWindows);
        evidence.addProperty("clientForwardDistance", clientForwardDistance);
        evidence.addProperty("clientForwardMeanBlocksPerClientTick", nativeMean);
        evidence.addProperty("serverForwardMeanBlocksPerServerTick", serverMean);
        evidence.addProperty("referenceBlocksPerClientTick", nativeMean);
        evidence.addProperty("referenceSource", "aligned pre-catch native client motion");
        evidence.addProperty("warmupReferenceBlocksPerClientTick", warmupReference);
        evidence.addProperty("nativeToWarmupSpeedRatio", warmupReference == 0 ? 0 : nativeMean / warmupReference);
        evidence.addProperty("serverToNativeSpeedRatio", nativeMean == 0 ? 0 : serverMean / nativeMean);
        run.report.add("nativeMeasurementValidation", evidence);
    }
    private static void classifySpeedResult() {
        var run = speedRun;
        boolean catchRequired = AcceptanceObservations.requiresRearCatch(run.name);
        String pursuit = run.pursuitAt < 0 ? "pursuit_not_started" : run.caughtAt < 0
                ? run.calibration() ? "pursuit_observed_calibration_only" : catchRequired
                    ? "not_caught_within_100_server_ticks" : "rear_pursuit_observed_no_catch_required" : "naturally_caught";
        if (run.pursuitAt < 0) speedFinding("pursuit_not_started", "Natural pursuit never started in THIS scenario; idle displacement is not swimming-speed evidence.");
        else if (catchRequired && run.caughtAt < 0)
            speedFinding("not_caught_within_100_server_ticks", "No natural catch in THIS rear-pursuit scenario; this is not a general speed conclusion.");
        String speedOutcome = "measured_by_separate_calibration";
        if (run.ghostTravel.max * 20 > SPEED_APPROX_MAX_PER_SECOND)
            speedFinding("outside_predeclared_speed_cap", "Observed unheld ghost motion exceeded the unchanged approximate 0.35 blocks/tick upper band.");
        if (run.calibration()) {
            if (run.matureTravel.count < SPEED_MIN_NATIVE_SAMPLES)
                speedFinding("measurement_inconclusive", "Calibration needs 20 mature position samples after 30 consecutive pursuit ticks; missing samples do not establish a production-speed failure.");
            double peak = run.matureTravel.max * 20;
            speedOutcome = run.inconclusive ? "measurement_inconclusive" : "within_predeclared_approximate_band";
            if (!run.inconclusive && (peak < SPEED_APPROX_MIN_PER_SECOND || peak > SPEED_APPROX_MAX_PER_SECOND)) {
                speedOutcome = "outside_predeclared_approximate_band";
                speedFinding(speedOutcome, "Mature observed peak=" + peak + " blocks per nominal 20-tick second; predeclared QA band is 6.5..7.1.");
            }
        }
        run.report.addProperty("measurementStatus", run.inconclusive ? "measurement_inconclusive" : "valid");
        run.report.addProperty("pursuitOutcome", pursuit);
        run.report.addProperty("speedOutcome", speedOutcome);
        run.report.addProperty("rearCatchRequired", catchRequired);
        run.report.addProperty("catchUpPassed", catchRequired && !run.inconclusive && run.pursuitAt >= 0 && run.caughtAt >= 0);
        run.report.addProperty("passed", run.issues.isEmpty());
        run.report.addProperty("maturePeakBlocksPerNominal20TickSecond", run.matureTravel.max * 20);
        run.report.add("predeclaredQaPolicy", speedPolicy());
    }
    private static void finishSpeedObservation(long now) {
        var run = speedRun;
        if (run.observationSamples != SPEED_WINDOW) speedFinding("measurement_inconclusive", "observation is not exactly 100 consecutive server END ticks");
        var report = new JsonObject();
        report.addProperty("scenario", run.name);
        report.addProperty("role", run.calibration() ? "stationary-start mature-speed calibration, NOT mature-boat catch-up"
                : run.swimmer() ? "mature swimmer rear-catch acceptance" : "mature boat rear-pursuit diagnostic, NOT interception acceptance");
        report.add("targetDamageEvents", run.targetDamageEvents.deepCopy());
        report.addProperty("matureNativeTargetAtSpawn", run.matureStart);
        report.add("startClientInput", run.startInput);
        report.addProperty("spawnServerTick", run.spawnedAt);
        report.addProperty("firstTargetServerTick", run.targetAt);
        report.addProperty("firstMayPursueServerTick", run.pursuitAt);
        report.addProperty("naturalCatchServerTick", run.caughtAt);
        report.addProperty("observationStartServerTick", run.observationAt);
        report.addProperty("observationEndServerTick", now);
        report.addProperty("observationSamples", run.observationSamples);
        report.addProperty("distinctNativeInputSamples", run.inputSamples);
        report.addProperty("clientTickAtObservationStart", run.observationClientTick);
        report.addProperty("lastClientTickObserved", run.lastClientTick);
        report.addProperty("clientTicksDuringServerWindow", run.lastClientTick - run.observationClientTick);
        report.addProperty("clientToServerTickRatio", (double) (run.lastClientTick - run.observationClientTick) / SPEED_WINDOW);
        report.addProperty("preCatchEndServerTick", run.preCatchEndTick);
        report.addProperty("preCatchEndClientTick", run.preCatchClientTick);
        report.addProperty("preCatchValidServerModeTicks", run.validServerModeTicks);
        report.addProperty("preCatchInvalidServerModeTicks", run.invalidServerModeTicks);
        report.addProperty("captureEntryObserved", run.captureEntryObserved);
        report.addProperty("captureEntryModeValid", run.captureEntryModeValid);
        report.addProperty("preCatchTargetForwardDistance", run.targetForwardDistance);
        report.addProperty("spawnDistance", run.initialDistance);
        report.addProperty("spawnPlayerPosition", csvPosition(run.spawnPlayer));
        report.addProperty("spawnGhostPosition", csvPosition(run.spawnGhost));
        report.addProperty("observationStartDistance", run.observationDistance);
        report.addProperty("observationStartPlayerPosition", csvPosition(run.observationPlayer));
        report.addProperty("observationStartBoatPosition", csvPosition(run.observationBoat));
        report.addProperty("observationStartGhostPosition", csvPosition(run.observationGhost));
        report.addProperty("observationEndPlayerPosition", csvPosition(player.position()));
        report.addProperty("observationEndBoatPosition", csvPosition(boat == null ? null : boat.position()));
        report.addProperty("observationEndGhostPosition", csvPosition(ghost.position()));
        report.addProperty("finalDistance", run.finalDistance);
        report.addProperty("closestDistance", run.closestDistance);
        report.addProperty("boatUuid", boat == null ? "" : boat.getUUID().toString());
        report.addProperty("ghostUuid", ghost.getUUID().toString());
        report.add("allUnheldGhost3d", run.ghostTravel.json());
        report.add("allUnheldGhostHorizontal", run.ghostHorizontal.json());
        report.add("matureGhost3d", run.matureTravel.json());
        report.add("matureGhostHorizontal", run.matureHorizontal.json());
        report.add("preCatchTargetServerHorizontal", run.targetTravel.json());
        run.report = report;
    }
    private static boolean onFixtureFloor() {
        return ghost.level().getBlockCollisions(ghost, ghost.getBoundingBox().move(0, -0.08, 0)).iterator().hasNext()
                && ghost.getBoundingBox().minY >= 72 && ghost.getBoundingBox().minY <= 72.08001
                && ghost.level().getBlockState(BlockPos.containing(ghost.getX(), 71, ghost.getZ())).is(Blocks.STONE);
    }
    private static void observeHoldGeometry(long now) throws Exception {
        Vec3 center = ghost.position().add(0, ghost.ghostBody().swimming() ? 0 : 0.6, 0);
        Vec3 front = center.add(Vec3.directionFromRotation(0, ghost.getYRot()).scale(1.1));
        var box = player.getDimensions(player.getPose()).makeBoundingBox(front);
        var column = box.expandTowards(0, Math.max(0, player.getY() - front.y), 0);
        boolean boatColumn = column.intersects(boat.getBoundingBox());
        boolean blockCollision = player.level().getBlockCollisions(player, box).iterator().hasNext();
        // Read-only invocation of the production predicate; it does not start or alter a grab.
        var blocked = ghost.ghostBody().grab.getClass().getDeclaredMethod("blocked", Vec3.class);
        blocked.setAccessible(true);
        boolean frontBlocked = (boolean) blocked.invoke(ghost.ghostBody().grab, front);
        boolean centerBlocked = (boolean) blocked.invoke(ghost.ghostBody().grab, center);
        if (boatColumn && !blockCollision) {
            boatColumnSamples++;
            if (frontBlocked && !centerBlocked) clearFallbackSamples++;
        }
        Files.writeString(RESULTS.resolve("acceptance-" + scenario + "-geometry.csv"),
                now + "," + boatContact + "," + boatColumn + "," + blockCollision + "," + frontBlocked
                        + "," + centerBlocked + "," + player.getY() + "," + ghost.getY() + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void releasedTick() throws Exception {
        check(ghost.ghostBody().grab.held() != player && !player.isPassenger(), "release remains free of grab and boat");
        var path = RESULTS.resolve("acceptance-client-" + scenario + "-released-input.signal");
        if (!Files.exists(path)) return;
        var input = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        int clientTick = input.get("clientTick").getAsInt();
        if (input.get("forwardImpulse").getAsFloat() > 0 && input.get("jumping").getAsBoolean()
                && clientTick > lastReleasedClientTick) releasedInputSamples++;
        lastReleasedClientTick = Math.max(lastReleasedClientTick, clientTick);
        if (input.get("phaseTicks").getAsInt() < 40 || releasedInputSamples < 20) return;
        double distance = player.position().subtract(releasedPosition).horizontalDistance();
        check(releasedInputSamples >= 20 && distance >= 0.75, "native movement recovers after release: samples="
                + releasedInputSamples + " distance=" + distance);
        mark(scenario + "-released", "distinctNativeInputSamples=" + releasedInputSamples + " horizontalDistance=" + distance);
        if (!CASE.equals("all")) { stage = 99; phase("complete"); }
        else if (scenario.equals("boat")) setupCapture("bottom");
        else if (scenario.equals("bottom")) setupCapture("front");
        else if (scenario.equals("front")) setupCapture("front-bottom");
        else if (scenario.equals("front-bottom")) setupCapture("damage");
        else setupItems();
    }
    private static JsonObject diagnosticVector(Vec3 value) {
        var json = new JsonObject();
        json.addProperty("x", value.x); json.addProperty("y", value.y); json.addProperty("z", value.z);
        return json;
    }
    private static JsonObject captureServerState() throws Exception {
        var json = new JsonObject();
        long now = player.level().getGameTime();
        json.addProperty("serverTick", now); json.addProperty("stage", stage);
        json.addProperty("scenario", scenario); json.addProperty("grabbedAt", grabbedAt); json.addProperty("releasedAt", releasedAt);
        json.add("position", diagnosticVector(player.position()));
        json.add("velocity", diagnosticVector(player.getDeltaMovement()));
        json.addProperty("hurtMarked", player.hurtMarked); json.addProperty("passenger", player.isPassenger());
        json.addProperty("onGround", player.onGround()); json.addProperty("pose", player.getPose().toString());
        json.addProperty("bodyWet", player.isInWater()); json.addProperty("eyesWet", player.isEyeInFluid(FluidTags.WATER));
        json.addProperty("horizontalCollision", player.horizontalCollision); json.addProperty("verticalCollision", player.verticalCollision);
        json.addProperty("sprinting", player.isSprinting()); json.addProperty("swimming", player.isSwimming());
        json.addProperty("air", player.getAirSupply());
        json.addProperty("leasePresent", player.getPersistentData().hasUUID("sound_isolating_eraser_grabbed_by"));
        if (boat != null) {
            json.addProperty("boatId", boat.getId());
            json.addProperty("boatUuid", boat.getUUID().toString());
            json.add("boatPosition", diagnosticVector(boat.position()));
            json.add("boatVelocity", diagnosticVector(boat.getDeltaMovement()));
        }
        if (ghost != null) {
            var grab = ghost.ghostBody().grab;
            json.addProperty("ghostId", ghost.getId());
            json.add("ghostPosition", diagnosticVector(ghost.position()));
            json.add("ghostVelocity", diagnosticVector(ghost.getDeltaMovement()));
            json.addProperty("held", grab.held() == player); json.addProperty("canContinue", grab.canContinueToUse());
            json.addProperty("targetMatches", ghost.getTarget() == player); json.addProperty("ghostAlive", ghost.isAlive());
            json.addProperty("playerAlive", player.isAlive()); json.addProperty("distanceSquared", ghost.distanceToSqr(player));
            json.addProperty("damage", damage); json.addProperty("cooldownUntil", grab.cooldownUntil());
            for (String fieldName : new String[] { "ticks", "duration" }) {
                var field = grab.getClass().getDeclaredField(fieldName); field.setAccessible(true);
                json.addProperty("grab_" + fieldName, field.getInt(grab));
            }
        }
        json.addProperty("snapshotTraceTick", captureSnapshotTick); json.addProperty("snapshotFirstSeenAtServerTick", captureSnapshotSeenAt);
        json.addProperty("snapshotAgeServerTicks", captureSnapshotSeenAt < 0 ? -1 : now - captureSnapshotSeenAt);
        if (captureClientSnapshot != null) json.add("latestClientSnapshot", captureClientSnapshot);
        return json;
    }
    private static synchronized void appendCaptureTrace(String stream, JsonObject json) throws Exception {
        json.addProperty("clockScope", "server-process");
        json.addProperty("sequence", ++captureTraceSequence); json.addProperty("monotonicNanos", System.nanoTime());
        Files.writeString(RESULTS.resolve("acceptance-" + scenario + "-" + stream + ".jsonl"), json + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void captureDiagnostic(String edge) throws Exception {
        if (edge.equals("END")) {
            var path = RESULTS.resolve("acceptance-client-" + scenario + "-diagnostic.signal");
            if (Files.exists(path)) {
                var snapshot = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                long observedTick = snapshot.get("traceTick").getAsLong();
                if (observedTick != captureSnapshotTick) {
                    captureSnapshotTick = observedTick; captureSnapshotSeenAt = player.level().getGameTime();
                }
                captureClientSnapshot = snapshot;
            }
        }
        var json = captureServerState(); json.addProperty("edge", edge);
        appendCaptureTrace("server-state", json);
    }
    public static void traceCaptureMovementPacket(ServerPlayer observed, String edge, Object packet) {
        if (!ENABLED || observed != player || stage < 1 || stage > 5) return;
        boolean mainThread = observed.getServer().isSameThread();
        var json = new JsonObject();
        json.addProperty("edge", mainThread ? "APPLY_" + edge : "RECEIVE_" + edge);
        json.addProperty("mainThread", mainThread); json.addProperty("scenario", scenario);
        json.addProperty("packetIdentity", System.identityHashCode(packet));
        json.addProperty("packet", packet.getClass().getSimpleName());
        if (packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket move) {
            json.addProperty("hasPosition", move.hasPosition()); json.addProperty("onGround", move.isOnGround());
            if (move.hasPosition()) {
                json.addProperty("wireX", move.getX(0)); json.addProperty("wireY", move.getY(0)); json.addProperty("wireZ", move.getZ(0));
            }
        } else if (packet instanceof net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket move) {
            json.addProperty("wireX", move.getX()); json.addProperty("wireY", move.getY()); json.addProperty("wireZ", move.getZ());
        }
        try {
            if (mainThread) json.add("state", captureServerState());
            appendCaptureTrace("server-packets", json);
        } catch (Exception error) { throw new IllegalStateException("Cannot record native capture movement packet", error); }
    }
    private static void captureTick() throws Exception {
        long now = player.level().getGameTime();
        boolean held = ghost.ghostBody().grab.held() == player;
        if (held && grabbedAt < 0) {
            // Record the natural capture before any assertion; client motion proof arrives asynchronously.
            grabbedAt = now; startY = ghost.getY(); minY = startY;
            playerStartY = player.getY(); playerMinY = playerStartY;
            System.out.println("RED_NAILS_ACCEPTANCE_CAPTURE tick=" + now + " boat=" + boat.getUUID()
                    + " boatPosition=" + boat.position() + " lastServerMovementTick=" + boatMovedAt
                    + " player=" + player.position() + " ghost=" + ghost.position());
            check(ghost.getTarget() == player && !ghost.isNoAi(), "normal AI naturally selected real player");
            check(!player.isPassenger(), "moving boat passenger was naturally dismounted");
            check(boatDistance >= 2, "boat moved under native client input before capture");
            check(ghost.ghostBody().swimming(), "boat capture starts in swimming posture");
            stage = 3; phase(scenario + "-held");
        }
        if (grabbedAt < 0) return;
        if (held || releasedAt < 0) {
            // An entry reference can be released by this tick's continuation check.
            // Only a hold that survives that check must satisfy the unchanged leash.
            sampleCheck(decisionTick == now, "real ghost tick observation exists tick=" + now);
            sampleCheck(!decisionHeld || !held || decisionDistanceSquared <= 16,
                    "original four-block ongoing hold limit for a retained AI tick hold tick=" + now);
            Files.writeString(RESULTS.resolve("acceptance-" + scenario + "-decision.csv"),
                    now + "," + decisionHeld + "," + decisionDistanceSquared + "," + ghost.distanceToSqr(player) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        if (held) {
            observeHoldGeometry(now);
            samples++; minY = Math.min(minY, ghost.getY());
            lastHeldOnFloor = onFixtureFloor();
            playerMinY = Math.min(playerMinY, player.getY());
            maxDistance = Math.max(maxDistance, ghost.distanceTo(player));
            var resist = RESULTS.resolve("acceptance-client-" + scenario + "-resist.signal");
            if (!scenario.equals("damage") && Files.exists(resist)) {
                var input = JsonParser.parseString(Files.readString(resist)).getAsJsonObject();
                int clientTick = input.get("clientTick").getAsInt();
                if (!input.get("passenger").getAsBoolean() && input.get("forwardImpulse").getAsFloat() > 0
                        && input.get("jumping").getAsBoolean() && input.get("sprintKey").getAsBoolean()) {
                    if (firstResistanceTick < 0) firstResistanceTick = clientTick;
                    lastResistanceTick = Math.max(lastResistanceTick, clientTick);
                    if (clientTick > lastValidResistanceTick) distinctResistanceTicks++;
                    lastValidResistanceTick = Math.max(lastValidResistanceTick, clientTick);
                    resistedTicks++;
                    if (ghost.distanceToSqr(player) <= 2.2 * 2.2) convergedTicks++;
                }
            }
            if (damage > 0 && damage < 8) belowDamageTicks++;
            boolean wet = ghost.ghostBody().swimming() && player.isEyeInFluid(FluidTags.WATER);
            int after = player.getAirSupply();
            if (beforeHeld && beforeWet && wet) {
                sampleCheck(AcceptanceObservations.validAirChange(beforeAir, after),
                        "per-tick oxygen before=" + beforeAir + " actual=" + after + " tick=" + now);
                airSamples++;
            }
            Files.writeString(RESULTS.resolve("acceptance-" + scenario + "-ticks.csv"),
                    now + "," + (now - grabbedAt) + "," + player.getX() + "," + player.getY() + "," + player.getZ()
                            + "," + ghost.getX() + "," + ghost.getY() + "," + ghost.getZ()
                            + "," + beforeAir + "," + after + "," + beforeWet + "," + wet + "," + damage
                            + "," + ghost.distanceTo(player) + "," + lastResistanceTick + ",true\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (samples % 5 == 0) System.out.println("RED_NAILS_ACCEPTANCE_PHYSICS tick=" + now
                    + " player=" + player.position() + " ghost=" + ghost.position() + " boat=" + boat.position()
                    + " ghostBox=" + ghost.getBoundingBox() + " boatBox=" + boat.getBoundingBox()
                    + " playerBox=" + player.getBoundingBox()
                    + " playerBoatOverlap=" + player.getBoundingBox().move(0, -0.08, 0).intersects(boat.getBoundingBox())
                    + " below=" + ghost.verticalCollisionBelow
                    + " stepClear=" + ghost.level().noCollision(ghost, ghost.getBoundingBox().move(0, -0.08, 0))
                    + " stepBlock=" + ghost.level().getBlockCollisions(ghost, ghost.getBoundingBox().move(0, -0.08, 0)).iterator().hasNext()
                    + " nearbyColliders=" + ghost.level().getEntities(ghost, ghost.getBoundingBox().move(0, -0.08, 0),
                            entity -> entity.canBeCollidedWith()).stream().map(entity -> entity.getType() + "#" + entity.getId()
                                    + ":" + entity.getBoundingBox()).toList());
            sampleCheck(startY - minY <= 6.00001, "unchanged maximum six-block descent tick=" + now);
        } else {
            if (releasedAt < 0) {
                releasedAt = now;
                releaseCooldown = ghost.ghostBody().grab.cooldownUntil();
                releasedLease = !player.getPersistentData().hasUUID("sound_isolating_eraser_grabbed_by");
                releasedOnFloor = lastHeldOnFloor || onFixtureFloor();
            }
            Files.writeString(RESULTS.resolve("acceptance-" + scenario + "-ticks.csv"),
                    now + "," + (now - grabbedAt) + "," + player.getX() + "," + player.getY() + "," + player.getZ()
                            + "," + ghost.getX() + "," + ghost.getY() + "," + ghost.getZ()
                            + "," + beforeAir + "," + player.getAirSupply() + "," + beforeWet + ",false," + damage
                            + "," + ghost.distanceTo(player) + "," + lastResistanceTick + ",false\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // Preserve the full observation window even when a real player escapes early.
            if (!scenario.equals("damage") && now < grabbedAt + 100) return;
            long duration = releasedAt - grabbedAt;
            long cooldown = releaseCooldown - releasedAt;
            System.out.println("RED_NAILS_ACCEPTANCE_RELEASE scenario=" + scenario + " duration=" + duration
                    + " samples=" + samples + " resisted=" + resistedTicks + " converged=" + convergedTicks
                    + " distinctResistanceClientTicks=" + distinctResistanceTicks
                    + " oxygen=" + airSamples + " ghostDescent=" + (startY - minY)
                    + " playerDescent=" + (playerStartY - playerMinY) + " maxDistance=" + maxDistance + " damage=" + damage);
            check(sampleFailure == null, "per-tick sample failure: " + sampleFailure);
            check(releaseCooldown == (scenario.equals("damage") ? damageAt : releasedAt) + 160,
                    "release starts exact 160-tick cooldown from its actual release tick: " + cooldown);
            check(releasedLease, "release clears holder lease");
            var motion = JsonParser.parseString(Files.readString(RESULTS.resolve("acceptance-client-" + scenario
                    + "-capture-motion.signal"))).getAsJsonObject();
            check(motion.get("boatUuid").getAsString().equals(boat.getUUID().toString())
                            && motion.get("forwardInput").getAsBoolean()
                            && motion.get("dismountedClientTick").getAsInt() == motion.get("lastMountedClientTick").getAsInt() + 1,
                    "same real boat naturally dismounts under uninterrupted native input: " + motion);
            if (!frontal()) check(motion.get("movingAtDismount").getAsBoolean(),
                    "moving-boat cases retain their strict continuous-motion requirement: " + motion);
            else {
                check(boatContact, "head-on regression reaches the original boat/ghost contact boundary");
                check(boatColumnSamples > 0 && clearFallbackSamples > 0,
                        "head-on geometry observes boat-only blocked front and clear center fallback: "
                                + boatColumnSamples + "/" + clearFallbackSamples);
            }
            if (scenario.equals("damage")) {
                check(damage >= 8 && damageStartedHeld && belowDamageTicks > 0 && duration < 99,
                        "real attacks cross eight damage while still held after a retained subthreshold hold");
            } else {
                check(duration >= 99 && duration <= 101, "unchanged 100-tick water hold despite movement/jump: " + duration);
                check(resistedTicks >= 40, "native movement/jump resistance sampled for >=40 server ticks: " + resistedTicks);
                check(convergedTicks >= 40, "resisting player converges inside capture reach in >=40 real samples: " + convergedTicks);
                check(lastResistanceTick - firstResistanceTick >= 20, "native resistance input continues across real client ticks");
                check(distinctResistanceTicks >= 20, "at least 20 distinct native input samples, not repeated stale signals: " + distinctResistanceTicks);
                check(airSamples >= 40, "full consecutive submerged interval has >=40 oxygen comparisons: " + airSamples);
                if (!shallow()) {
                    check(startY - minY >= 5.9, "deep-water case reaches six-block descent");
                    check(playerStartY - playerMinY >= 5.5, "actual resisting player is pulled down, with half-block synchronization tolerance");
                }
                if (shallow()) {
                    check(startY - minY > 0 && startY - minY < 6, "shallow-water descent stops before six blocks");
                    check(playerStartY - playerMinY > 1, "actual resisting player leaves the boat and descends in shallow water");
                    check(releasedOnFloor, "descent stopped at fixture stone floor, not at an entity collider");
                }
            }
            mark(scenario, "boatDistance=" + boatDistance + " duration=" + duration + " heldSamples=" + samples
                    + " resistedTicks=" + resistedTicks + " convergedTicks=" + convergedTicks
                    + " distinctResistanceClientTicks=" + distinctResistanceTicks
                    + " oxygenComparisons=" + airSamples + " descent=" + (startY - minY) + " playerDescent=" + (playerStartY - playerMinY)
                    + " maxPlayerDistance=" + maxDistance + " damage=" + damage + " subthresholdHeldTicks=" + belowDamageTicks
                    + " boatContact=" + boatContact + " boatColumnSamples=" + boatColumnSamples + " clearFallbackSamples=" + clearFallbackSamples
                    + " cooldown=" + cooldown + "; normal AI, real client, no forced target/grab");
            stage = 4; phase(scenario + "-complete");
        }
    }
    private static void setupLifecycle() {
        player.teleportTo(player.serverLevel(), 29.5, 75, 0.5, 0, 0);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.setAirSupply(player.getMaxAirSupply());
        lifecycleHealth = player.getHealth();
        lifecycleOwner = player.getUUID();
        ring = new ItemStack(ModItems.RED_NAILS.get());
        CuriosApi.getCuriosInventory(player).resolve().orElseThrow().getStacksHandler("ring").orElseThrow()
                .getStacks().setStackInSlot(0, ring);
        stage = 40; phase("lifecycle-rebind");
    }
    private static RedNailsState.Entry lifecycleEntry() {
        if (!ring.hasTag() || !ring.getTag().hasUUID("ItemId")) return null;
        return RedNailsState.get(player.getServer()).find(ring.getTag().getUUID("ItemId"));
    }
    private static void checkLifecycleHealthy(String when) {
        check(player.getHealth() == lifecycleHealth && lifecycleDamageEvents == 0,
                when + ": lifecycle recall must not damage the real owner");
        check(!player.hasEffect(MobEffects.DARKNESS) && !player.hasEffect(MobEffects.CONFUSION)
                        && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                when + ": lifecycle recall must not apply backlash effects");
    }
    private static void checkLifecycleIdentity(String when) {
        check(ring.hasTag() && ring.getTag().hasUUID("ItemId") && ring.getTag().getUUID("ItemId").equals(lifecycleItem)
                        && ring.getTag().hasUUID("Owner") && ring.getTag().getUUID("Owner").equals(lifecycleOwner),
                when + ": same item identity and owner binding survive");
        var entry = lifecycleEntry();
        check(entry != null && entry.item.equals(lifecycleItem) && entry.owner.equals(lifecycleOwner),
                when + ": original authoritative ledger remains present");
        check(Arrays.equals(entry.ready, lifecycleReady)
                        && Arrays.equals(ring.getTag().getLongArray("NailReadyAt"), lifecycleReady),
                when + ": all four exact cooldown deadlines remain unchanged");
        checkLifecycleHealthy(when);
    }
    private static void checkLifecycleCleared(String when) {
        checkLifecycleIdentity(when);
        check(lifecycleEntry().active() == 0, when + ": authoritative active summons are cleared");
        check(redSummon != null && redSummon.isRemoved(), when + ": original live world entity is removed");
        var resolved = RedNailsItem.resolve(player.getServer(), lifecycleGhost);
        check(resolved == null || resolved.isRemoved(), when + ": original ghost is absent from every loaded dimension");
        var out = ring.getTag().getList("NailOut", Tag.TAG_COMPOUND);
        for (int i = 0; i < out.size(); i++) check(!out.getCompound(i).hasUUID("Ghost"), when + ": item mirror clears ghost UUIDs");
        check(RedNailsItem.coolingCount(ring) == 0, when + ": item mirror adds no cooldown");
    }
    private static void rememberLifecycleGhost(RedNailsState.Entry entry) {
        check(entry != null && entry.active() == 1, "native mapped-key input creates exactly one active summon");
        UUID next = Arrays.stream(entry.ghosts).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        check(!next.equals(lifecycleGhost), "a new native activation cannot resurrect the removed ghost");
        redSummon = RedNailsItem.resolve(player.getServer(), next);
        check(redSummon != null && redSummon.isAlive() && !redSummon.isRemoved(), "activation creates a live registered world entity");
        lifecycleGhost = next;
        if (lifecycleItem == null) {
            lifecycleItem = entry.item;
            lifecycleReady = entry.ready.clone();
            check(Arrays.stream(lifecycleReady).allMatch(value -> value == 0), "fresh lifecycle ring starts with no cooldown");
        }
        checkLifecycleIdentity("active summon");
    }
    private static com.google.gson.JsonObject lifecycleSignal(String name) throws Exception {
        var path = RESULTS.resolve("acceptance-client-" + name + ".signal");
        return Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : null;
    }
    private static void checkClientLifecycleState(com.google.gson.JsonObject proof, int active, String dimension) {
        check(proof.get("itemId").getAsString().equals(lifecycleItem.toString())
                        && proof.get("owner").getAsString().equals(lifecycleOwner.toString()),
                "native client receives the unchanged item identity and owner");
        check(proof.get("dimension").getAsString().equals(dimension)
                        && proof.get("active").getAsInt() == active && proof.get("worldSummons").getAsInt() == active,
                "native client receives the actual dimension, item state and world entities");
        check(proof.get("health").getAsFloat() == lifecycleHealth && proof.get("cooling").getAsInt() == 0
                        && !proof.get("backlash").getAsBoolean(), "native client receives no damage, backlash or new cooldown");
        if (active == 1) check(proof.get("ghost").getAsString().equals(lifecycleGhost.toString()),
                "native client sees the same live world summon as the server");
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void beforeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!ENABLED || !CASE.equals("lifecycle") || event.getEntity() != player || failed) return;
        try {
            check(stage == 46 && event.getFrom().equals(Level.OVERWORLD) && event.getTo().equals(Level.NETHER),
                    "the real dimension event matches the requested cross-dimension transition");
            check(lifecycleEntry().active() == 1 && redSummon.isAlive() && !redSummon.isRemoved(),
                    "real dimension lifecycle begins with an active live red-nails summon");
            lifecycleDimensionActive = true;
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void afterDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!ENABLED || !CASE.equals("lifecycle") || event.getEntity() != player || failed) return;
        try {
            lifecycleDimensionEvents++;
            check(lifecycleDimensionActive, "observed active summon before production dimension handlers");
            checkLifecycleCleared("real dimension event");
            check(!RedNailsInput.chanting(player.getUUID()), "dimension transition cancels the production input state");
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void beforeLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ENABLED || !CASE.equals("lifecycle") || event.getEntity() != player || stage == 99 || failed) return;
        try {
            check(stage == 48 && lifecycleEntry().active() == 1 && redSummon.isAlive() && !redSummon.isRemoved(),
                    "actual network logout starts with an active live red-nails summon");
            lifecycleLogoutActive = true;
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void afterLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ENABLED || !CASE.equals("lifecycle") || event.getEntity() != player || stage == 99 || failed) return;
        try {
            lifecycleLogoutEvents++;
            check(lifecycleLogoutActive, "observed active summon before production logout handlers");
            checkLifecycleCleared("real logout event");
            check(!RedNailsInput.chanting(player.getUUID()), "logout cancels the production input state");
            mark("lifecycle-logout", "actual PlayerLoggedOutEvent; item=" + lifecycleItem + " ghost=" + lifecycleGhost
                    + " health=" + player.getHealth() + " ready=" + Arrays.toString(lifecycleReady)
                    + "; live summon recalled without damage, effects or cooldown");
            stage = 50; ticks = 0; stageNanos = System.nanoTime(); player = null;
        } catch (Throwable error) { fail(error); }
    }
    private static void lifecycleTick() throws Exception {
        checkLifecycleHealthy("lifecycle tick " + stage);
        var entry = lifecycleEntry();
        int active = entry == null ? 0 : entry.active();
        check(active <= 1, "one held mapped key must never create duplicate lifecycle summons");
        if (lifecycleItem != null) checkLifecycleIdentity("lifecycle tick " + stage);
        if (stage >= 40 && stage <= 43) check(active == 0, "old binding and canceled GUI chant never activate a summon");
        if (stage == 40) {
            check(!RedNailsInput.chanting(player.getUUID()), "old V binding must not start a chant after remapping");
            var proof = lifecycleSignal("lifecycle-rebind");
            if (proof == null || !proof.get("complete").getAsBoolean() || ticks < 40) return;
            check(proof.get("oldHeldTicks").getAsInt() >= 30 && !proof.get("mappedDown").getAsBoolean()
                            && proof.get("binding").getAsString().equals("key.keyboard.o"),
                    "native key-map dispatch holds old V without activating remapped O");
            stage = 41; phase("lifecycle-gui-start");
        } else if (stage == 41 && RedNailsInput.chanting(player.getUUID())) {
            lifecycleGuiPress = RedNailsInput.pressedAt(player.getUUID());
            check(RedNailsState.clock(player.getServer()) - lifecycleGuiPress < 20, "GUI opens during a real unfinished chant");
            stage = 42; phase("lifecycle-gui-open");
        } else if (stage == 42) {
            var proof = lifecycleSignal("lifecycle-gui-open");
            if (!RedNailsInput.chanting(player.getUUID()) && RedNailsInput.releasedAt(player.getUUID()) >= lifecycleGuiPress) {
                lifecycleGuiRelease = RedNailsInput.releasedAt(player.getUUID());
                check(lifecycleGuiRelease - lifecycleGuiPress < 20, "opening the real inventory cancels before the activation threshold");
            }
            if (ticks < 40 || proof == null || proof.get("phaseTicks").getAsInt() < 20 || lifecycleGuiRelease == Long.MIN_VALUE) return;
            check(proof.get("screenOpen").getAsBoolean() && proof.get("mappedDown").getAsBoolean(),
                    "native inventory remains open with the remapped key held");
            check(!RedNailsInput.chanting(player.getUUID()), "GUI cancellation leaves no server chant");
            stage = 43; phase("lifecycle-gui-closed");
        } else if (stage == 43) {
            check(!RedNailsInput.chanting(player.getUUID()) && RedNailsInput.pressedAt(player.getUUID()) == lifecycleGuiPress,
                    "closing the inventory while still holding O cannot restart the canceled chant");
            var proof = lifecycleSignal("lifecycle-gui-closed");
            if (ticks < 40 || proof == null || proof.get("phaseTicks").getAsInt() < 40) return;
            check(!proof.get("screenOpen").getAsBoolean() && proof.get("mappedDown").getAsBoolean(),
                    "native gameplay resumes with the same key still held");
            mark("lifecycle-gui-cancel", "real InventoryScreen interrupts native chant after "
                    + (lifecycleGuiRelease - lifecycleGuiPress) + " server ticks; closing while held does not resend input");
            stage = 44; phase("lifecycle-summon");
        } else if (stage == 44 && active == 1) {
            rememberLifecycleGhost(entry);
            check(RedNailsInput.pressedAt(player.getUUID()) > lifecycleGuiPress, "release and new O press starts a fresh production chant");
            mark("lifecycle-rebind", "runtime O binding updates actual item tooltip; old V is inert; fresh mapped O activates one real summon");
            stage = 45; phase("lifecycle-dimension-ready");
        } else if (stage == 45) {
            var proof = lifecycleSignal("lifecycle-dimension-ready");
            if (proof == null) return;
            checkClientLifecycleState(proof, 1, Level.OVERWORLD.location().toString());
            var destination = player.getServer().getLevel(Level.NETHER);
            check(destination != null, "native server exposes the real Nether dimension");
            for (var pos : BlockPos.betweenClosed(22, 74, -8, 38, 82, 12)) {
                boolean wall = pos.getY() == 74 || pos.getY() == 82 || pos.getX() == 22 || pos.getX() == 38
                        || pos.getZ() == -8 || pos.getZ() == 12;
                destination.setBlock(pos, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
            // This changes the player's actual ServerLevel and emits Forge's ordinary
            // lifecycle event; the QA never posts a synthetic event or calls cancelOwner.
            stage = 46;
            player.teleportTo(destination, 29.5, 75, 0.5, 0, 0);
            check(lifecycleDimensionEvents == 1 && player.level().dimension().equals(Level.NETHER),
                    "real player changed dimension and exactly one native lifecycle event was observed");
            phase("lifecycle-dimension-observe");
        } else if (stage == 46) {
            checkLifecycleCleared("dimension observation");
            var proof = lifecycleSignal("lifecycle-dimension-observe");
            if (ticks < 40 || proof == null || proof.get("phaseTicks").getAsInt() < 20) return;
            checkClientLifecycleState(proof, 0, Level.NETHER.location().toString());
            mark("lifecycle-dimension", "actual Overworld -> Nether; native event count=" + lifecycleDimensionEvents
                    + " item=" + lifecycleItem + " removedGhost=" + lifecycleGhost + " ready=" + Arrays.toString(lifecycleReady)
                    + "; server and client agree: recalled, no backlash, unchanged cooldown");
            stage = 47; phase("lifecycle-logout-summon");
        } else if (stage == 47 && active == 1) {
            rememberLifecycleGhost(entry);
            stage = 48; phase("lifecycle-logout");
        } else if (stage == 51) {
            checkLifecycleCleared("rejoin observation");
            check(!RedNailsInput.chanting(player.getUUID()), "rejoined player has no stale pending chant");
            var proof = lifecycleSignal("lifecycle-rejoin");
            if (ticks < 40 || proof == null || proof.get("phaseTicks").getAsInt() < 20) return;
            checkClientLifecycleState(proof, 0, Level.NETHER.location().toString());
            mark("lifecycle-rejoin", "new real client connection restores same ItemId/Owner in Nether; original ghost absent; health="
                    + lifecycleHealth + " ready=" + Arrays.toString(lifecycleReady) + "; no stale input or new cooldown");
            stage = 52; phase("lifecycle-resummon");
        } else if (stage == 52 && active == 1) {
            var proof = lifecycleSignal("lifecycle-resummon-active");
            if (proof == null) return;
            rememberLifecycleGhost(entry);
            checkClientLifecycleState(proof, 1, Level.NETHER.location().toString());
            mark("lifecycle-resummon", "fresh mapped key after actual reconnect creates and synchronizes new live ghost=" + lifecycleGhost);
            stage = 53; phase("lifecycle-final-recall");
        } else if (stage == 53 && active == 0) {
            checkLifecycleCleared("final native sneak-key recall");
            mark("lifecycle-cleanup", "native sneak+O recalls the post-rejoin summon without damage or cooldown");
            stage = 99; phase("complete");
        }
    }
    private static void setupItems() {
        if (ghost != null) ghost.discard();
        if (boat != null) boat.discard();
        player.stopRiding();
        player.teleportTo(player.serverLevel(), 29.5, 75, 0.5, 0, 0);
        player.setHealth(player.getMaxHealth()); player.setAirSupply(player.getMaxAirSupply());
        ring = new ItemStack(ModItems.RED_NAILS.get());
        var inventory = CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        inventory.getStacksHandler("ring").orElseThrow().getStacks().setStackInSlot(0, ring);
        player.getInventory().setItem(0, new ItemStack(ModItems.SUXIA_POCKET_WATCH.get()));
        player.getInventory().selected = 0;
        stage = 20; phase("coexist");
    }
    private static void itemTick() throws Exception {
        var entry = RedNailsState.get(player.getServer()).read(ring);
        var suxia = SuxiaSummons.get(player.getServer()).active(player.getUUID());
        if (stage == 20 && entry != null) check(entry.active() <= 1, "one sustained V hold must never produce a second summon");
        if (stage == 20 && entry != null && entry.active() == 1 && suxia != null) {
            var guardian = RedNailsItem.resolve(player.getServer(), entry.ghosts[1]);
            var summon = SuxiaPocketWatchItem.resolve(player.getServer(), suxia);
            check(guardian != null && summon != null, "real watch and V inputs summon both items");
            redSummon = guardian; watchSummon = summon;
            check(!guardian.wantsToAttack(summon, player), "red nails excludes owner's Suxia");
            check(!summon.wantsToAttack(guardian, player), "Suxia excludes owner's red nails");
            check(guardian.getTarget() != summon && summon.getTarget() != guardian, "coexisting summons never target each other");
            if (coexistAt < 0) coexistAt = player.level().getGameTime();
            var vInput = RESULTS.resolve("acceptance-client-coexist-input.signal");
            if (player.level().getGameTime() - coexistAt < 100 || !Files.exists(vInput)
                    || Integer.parseInt(Files.readString(vInput).trim()) < 60) return;
            check(entry.active() == 1, "holding V does not repeat summons");
            mark("coexist", "normal production summons coexist; reciprocal friendly exclusions; one sustained V hold yields one ring-finger ghost");
            stage = 21; phase("item-recall");
        } else if (stage == 21 && entry != null && entry.active() == 0) {
            check(redSummon.isRemoved(), "red recall removes the original world entity");
            check(!watchSummon.isRemoved(), "red recall preserves the watch world entity");
            check(suxia != null && SuxiaPocketWatchItem.resolve(player.getServer(), suxia) != null, "red recall leaves Suxia active");
            check(java.util.Arrays.stream(entry.ready).allMatch(v -> v == 0), "recall adds no red cooldown");
            mark("recall", "real sneak+V recalls red nails only; Suxia remains active; no cooldown");
            stage = 22; phase("item-resummon");
        } else if (stage == 22 && entry != null && entry.active() == 1) {
            check(suxia != null, "coexistence survives real resummon");
            redSummon = RedNailsItem.resolve(player.getServer(), entry.ghosts[1]);
            check(redSummon != null && !redSummon.isRemoved(), "resummon creates a live world entity");
            stage = 24; phase("watch-recall");
        } else if (stage == 24 && suxia == null) {
            check(entry != null && entry.active() == 1, "Suxia recall leaves red summon active");
            check(watchSummon.isRemoved() && !redSummon.isRemoved(), "watch recall removes only the watch world entity");
            mark("recall-watch", "real watch use recalls Suxia only and preserves the red summon");
            CuriosApi.getCuriosInventory(player).resolve().orElseThrow().getStacksHandler("ring").orElseThrow()
                    .getStacks().setStackInSlot(0, ItemStack.EMPTY);
            stage = 23; phase("item-unequip");
        } else if (stage == 23 && entry != null && entry.active() == 0) {
            check(redSummon.isRemoved(), "unequip removes the red world entity");
            check(java.util.Arrays.stream(entry.ready).allMatch(v -> v == 0), "unequip adds no cooldown");
            mark("unequip", "real Curios unequip clears summons with zero cooldown");
            stage = 99; phase("complete");
        }
    }
}
