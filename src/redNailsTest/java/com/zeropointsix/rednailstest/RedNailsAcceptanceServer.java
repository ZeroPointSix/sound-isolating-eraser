package com.zeropointsix.rednailstest;

import com.google.gson.JsonParser;
import com.zeropointsix.eraser.rednails.RedNailsItem;
import com.zeropointsix.eraser.rednails.RedNailsState;
import com.zeropointsix.eraser.rednails.WaterGhostEntity;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaPocketWatchItem;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
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
    private static ServerPlayer player;
    private static WaterGhostEntity ghost;
    private static Boat boat;
    private static ItemStack ring;
    private static net.minecraft.world.entity.Entity redSummon, watchSummon;
    private static String scenario = "boat";
    private static int stage, ticks, samples, airSamples, resistedTicks, convergedTicks, belowDamageTicks;
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
    private static boolean damageStartedHeld;

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
    private static void phase(String name) {
        ticks = 0; stageNanos = System.nanoTime();
        player.sendSystemMessage(Component.literal("RED_NAILS_ACCEPTANCE:" + name));
        System.out.println("RED_NAILS_ACCEPTANCE_PHASE=" + name);
    }
    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (!ENABLED) return;
        check(java.util.Set.of("all", "boat", "bottom", "front", "front-bottom", "damage", "items").contains(CASE), "unknown acceptance case " + CASE);
        var level = event.getServer().overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, event.getServer());
        level.setDefaultSpawnPos(new BlockPos(24, 75, 0), 0);
        for (var p : BlockPos.betweenClosed(-18, 60, -18, 18, 74, 24)) {
            level.setBlock(p, (p.getY() == 60 ? Blocks.STONE : Blocks.WATER).defaultBlockState(), 2);
        }
        for (var p : BlockPos.betweenClosed(22, 74, -8, 38, 74, 12)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("NailsTester")) return;
        player = p; p.setGameMode(GameType.SURVIVAL); stageNanos = System.nanoTime();
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void beforeDamage(LivingDamageEvent event) {
        if (ENABLED && ghost != null && event.getEntity() == ghost && event.getSource().getEntity() == player) {
            damageStartedHeld = ghost.ghostBody().grab.held() == player;
            System.out.println("RED_NAILS_ACCEPTANCE_DAMAGE_ENTRY held=" + damageStartedHeld
                    + " priorDamage=" + damage + " amount=" + event.getAmount());
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damaged(LivingDamageEvent event) {
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
        player.teleportTo(level, 0.5, 75.1, -8.5, 0, 0);
        boat = new Boat(level, 0.5, 74.5, -8.5);
        boat.setYRot(0); level.addFreshEntity(boat);
        check(player.startRiding(boat, true), "fixture player mounts a real vanilla boat");
        boatStart = boat.position(); boatDistance = 0;
        previousBoatPosition = boatStart; boatMovedAt = -1;
        grabbedAt = -1; ghost = null; samples = 0; airSamples = 0; resistedTicks = 0;
        releasedAt = -1;
        damage = 0; belowDamageTicks = 0; maxDistance = 0;
        convergedTicks = 0; sampleFailure = null;
        distinctResistanceTicks = 0; lastValidResistanceTick = -1; lastHeldOnFloor = false;
        firstResistanceTick = -1; lastResistanceTick = -1;
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
            return;
        }
        try {
            ticks++;
            check(System.nanoTime() - stageNanos < 300_000_000_000L, "native acceptance stage timeout " + stage + " " + scenario);
            check(player.isAlive(), "real player survives the acceptance case");
            if (stage == 0 && Files.exists(RESULTS.resolve("acceptance-client-ready.signal"))) {
                if (CASE.equals("items")) setupItems();
                else setupCapture(CASE.equals("all") ? "boat" : CASE);
            }
            else if (stage == 1) {
                boatDistance = Math.max(boatDistance, boat.position().subtract(boatStart).horizontalDistance());
                if (boatDistance >= 2 && ghost == null) {
                    ghost = ModEntities.WATER_GHOST.get().create(player.serverLevel());
                    check(ghost != null, "registered ghost creates");
                    // Separate regressions preserve the original head-on geometry and the moving-boat case.
                    ghost.moveTo(player.getX() + (frontal() ? 0 : 1.4), 73.5, player.getZ() + 4.5, 180, 0);
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
            } else if (stage >= 20) itemTick();
        } catch (Throwable error) {
            failed = true; error.printStackTrace();
            try { Files.writeString(RESULTS.resolve("acceptance-server.failed"), error.toString()); }
            catch (Exception ignored) { }
        }
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
            // Check the unchanged leash at AI tick entry. END includes the ghost's new
            // descent before the next client correction, not the AI decision position.
            sampleCheck(decisionTick == now, "real ghost tick observation exists tick=" + now);
            sampleCheck(!decisionHeld || decisionDistanceSquared <= 16,
                    "original four-block ongoing hold limit at AI tick entry tick=" + now);
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
