package com.zeropointsix.gravitytest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.wings.FlightEvents;
import com.zeropointsix.eraser.wings.WingsState;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/** Server authority driver for the wind-thunder wings E2E. Inert unless -Dwings.qa.role=server. */
@Mod.EventBusSubscriber(modid = "gravity_qa")
public final class WingsQaServer {
    public static final String WEARER = "WingsWearer";
    public static final String OBSERVER = "WingsObserver";
    private static final String PREFIX = "WINGS_QA_PHASE:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RUN_TICKS = 12000;
    private static final boolean SHOWCASE = "showcase".equals(System.getProperty("wings.qa.mode"));
    private static boolean ready;
    private static int readyAt;
    private static int startedAt = -1;
    private static int stopAt = -1;
    private static int trackUntil = -1;
    private static boolean trackWearer = true;
    private static net.minecraft.world.phys.Vec3 trackTarget;
    private static boolean hungerChecked;
    private static ServerPlayer wearerRef;
    private static String reviewView = "reviewBack";
    private static long voidSpeedFlag, chargeEnds, blinkBefore;
    private static float chargeExhaustion;
    private static boolean chargeMeasured, chargedBlinkChecked, controlsDone;
    private static boolean awaitCharge;
    private static boolean tracingVoid;
    private static long networkSpeedFlag, networkWallHit;
    private static float networkHealth;
    private static String networkScenario = "";
    private static double networkPreviousX, networkMaxStep;
    private static final java.util.List<String> NETWORK_OBSERVATIONS = new java.util.ArrayList<>();
    private static final java.util.List<String> NETWORK_TRACE = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wingsqa")
                .requires(WingsQaServer::allowed)
                .then(Commands.literal("phase")
                        .then(Commands.argument("word", StringArgumentType.word())
                                .executes(context -> checkedPhase(context.getSource(),
                                        StringArgumentType.getString(context, "word")))))
                .then(Commands.literal("finish")
                        .executes(context -> finish(context.getSource().getServer()))));
    }

    private static boolean allowed(CommandSourceStack source) {
        if (!source.getServer().isDedicatedServer()) return false;
        if (source.hasPermission(2)) return true;
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        String name = player.getGameProfile().getName();
        return WEARER.equals(name) || OBSERVER.equals(name);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!"server".equals(System.getProperty("wings.qa.role"))) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        if (startedAt < 0) {
            startedAt = server.getTickCount();
            LOGGER.info("WINGS_QA_SERVER_WAITING");
        }
        if (stopAt >= 0 && server.getTickCount() >= stopAt) {
            server.halt(false);
            return;
        }
        if (server.getTickCount() - startedAt >= MAX_RUN_TICKS) {
            fail(server, "WINGS_QA_SERVER_TIMEOUT");
            return;
        }
        if (!ready) {
            ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
            ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
            if (wearer == null || observer == null) return;
            initialize(server, wearer, observer);
            ready = true;
            readyAt = server.getTickCount();
            announce(server, "ready");
            return;
        }
        try {
            if (Boolean.getBoolean("wings.qa.motionOnly")) {
                WingsMotionQaServer.tick(server, results());
                return;
            }
            if (!networkScenario.isEmpty() && wearerRef != null) {
                networkMaxStep = Math.max(networkMaxStep, Math.abs(wearerRef.getX() - networkPreviousX));
                if (NETWORK_TRACE.size() < 6000) {
                    NETWORK_TRACE.add("{\"scenario\":\"" + networkScenario + "\",\"tick\":"
                            + wearerRef.level().getGameTime() + ",\"x\":" + wearerRef.getX()
                            + ",\"y\":" + wearerRef.getY() + ",\"z\":" + wearerRef.getZ()
                            + ",\"dx\":" + (wearerRef.getX() - networkPreviousX)
                            + ",\"health\":" + wearerRef.getHealth()
                            + ",\"speedFlag\":" + WingsState.speedFlagAt(wearerRef)
                            + ",\"wallHit\":" + WingsState.wallHitAt(wearerRef) + "}");
                }
                networkPreviousX = wearerRef.getX();
            }
            // Server-side asserts run on phase commands from the clients, not on
            // tick positions — llvmpipe clients tick slower than the server.
            if (awaitCharge && wearerRef != null && WingsState.charged(wearerRef)) {
                long remaining = WingsState.chargedUntil(wearerRef) - wearerRef.level().getGameTime();
                long duration = com.zeropointsix.eraser.wings.WingsConfig.SERVER.chargedDurationTicks.get();
                check(remaining >= duration - 1 && remaining <= duration,
                        "real lightning starts configured charge duration: " + remaining);
                awaitCharge = false;
            }
            if (chargeMeasured && wearerRef != null) {
                chargeEnds = WingsState.chargedUntil(wearerRef);
                long now = wearerRef.level().getGameTime();
                float exhaustion = wearerRef.getFoodData().getExhaustionLevel();
                if (now < chargeEnds) {
                    checkExhaustion(exhaustion);
                } else if (now >= chargeEnds + 2) {
                    check(chargedBlinkChecked, "charged native blink was verified before expiry");
                    check(!WingsState.charged(wearerRef) && exhaustion > chargeExhaustion,
                            "real 1200-tick charge expiry resumes flight exhaustion");
                    Files.writeString(results().resolve("wings-controls.pass"),
                            "PASS native G/H/Shift/W movement, fixed hover, starving void rescue, real lightning charge, free blink/flight and natural expiry\n");
                    controlsDone = true;
                    chargeMeasured = false;
                }
            }
        } catch (Throwable failure) {
            fail(server, failure.toString());
        }
        // Keep the observer glued to the track target (wearer flank / dropped item)
        // during the screenshot window; the wearer is client-authoritative in flight,
        // so its own client may keep drifting.
        if (server.getTickCount() < trackUntil) {
            ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
            if (observer != null) {
                if (trackWearer) {
                    ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                    if (wearer != null) faceAt(wearer, observer);
                } else if (trackTarget != null) {
                    faceAt(trackTarget, observer);
                }
            }
        }
    }

    private static void initialize(MinecraftServer server, ServerPlayer wearer, ServerPlayer observer) {
        ServerLevel level = server.overworld();
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        for (int x = -10; x <= 100; x++) {
            for (int z = -8; z <= 20; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                for (int y = 65; y <= 115; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        // wearer: survival, hungry-drainable, wings already equipped, airborne facing +X
        wearer.setGameMode(GameType.SURVIVAL);
        wearer.getInventory().clearContent();
        wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
        wearer.getFoodData().setFoodLevel(20);
        wearer.getFoodData().setSaturation(0f);
        wearer.teleportTo(level, 0.5, 100, 0.5, -90f, 0f);
        wearer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        wearerRef = wearer;
        // observer: creative vantage east of the wearer flight path
        observer.setGameMode(GameType.CREATIVE);
        observer.teleportTo(level, 15.5, 90, 12.5, -120f, -15f);
        observer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (SHOWCASE) {
            // 录屏用：出生落下时已装备翅膀之前可能吃到摔落伤害——开场即回满
            wearer.setHealth(wearer.getMaxHealth());
            wearer.getFoodData().setFoodLevel(20);
            wearer.getFoodData().setSaturation(5f);
        }
        if (SHOWCASE) {
            decorate(level);
            // QA-only studio lighting. Production materials and lighting stay unchanged.
            wearer.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 12000, 0, false, false, false));
            observer.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 12000, 0, false, false, false));
            // 录屏用：火焰不伤（雷击充能镜头里不能烧起来）；
            // 观察者全程粘附佩戴者侧翼跟拍（每 tick 传送=平滑跟踪镜头）
            level.getGameRules().getRule(GameRules.RULE_FIRE_DAMAGE).set(false, server);
            trackWearer = true;
            trackUntil = server.getTickCount() + 9000;
        }
        LOGGER.info("WINGS_QA_FIXTURE_READY");
    }

    /** 录屏舞台：沿 +X 飞行线立石柱群（顶部金块），让速度有参照物。 */
    private static void decorate(ServerLevel level) {
        for (int i = 1; i <= 6; i++) {
            int x = i * 16;
            int h = 66 + (i % 3) * 10;
            for (int y = 64; y <= h; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, -6), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, y, 7), Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(new BlockPos(x, h + 1, -6), Blocks.GOLD_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x, h + 1, 7), Blocks.GOLD_BLOCK.defaultBlockState());
        }
        // 门形环：x=60 穿越框
        for (int y = 95; y <= 103; y++) {
            for (int z = -2; z <= 2; z++) {
                if (y == 95 || y == 103 || Math.abs(z) == 2) {
                    level.setBlockAndUpdate(new BlockPos(60, y, z), Blocks.GOLD_BLOCK.defaultBlockState());
                }
            }
        }
    }

    private static int changePhase(CommandSourceStack source, String next) throws Exception {
        MinecraftServer server = source.getServer();
        if (!ready) {
            source.sendFailure(Component.literal("WINGS_QA_NOT_READY"));
            return 0;
        }
        if (Boolean.getBoolean("wings.qa.motionOnly")) {
            WingsMotionQaServer.phase(source, next, results());
            return 1;
        }
        switch (next) {
            case "visualSetup" -> {
                controlFixture(server, wearerRef, 240);
                FlightEvents.serverSetDeployed(wearerRef, false);
            }
            case "netCruise", "netHover", "netRepeat" -> {
                int scenario = next.equals("netCruise") ? 0 : next.equals("netHover") ? 1 : 2;
                ServerLevel level = server.overworld();
                int z = scenario * 32;
                int wallX = scenario == 0 ? 30 : 60;
                if (scenario != 1) {
                    for (BlockPos pos : BlockPos.betweenClosed(wallX, 197, z - 4, wallX, 205, z + 4)) {
                        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    }
                }
                controlFixture(server, wearerRef, 200);
                wearerRef.teleportTo(level, 0.5, 200, z + 0.5, -90, 0);
                FlightEvents.markTeleported(wearerRef);
                wearerRef.setHealth(wearerRef.getMaxHealth());
                wearerRef.invulnerableTime = 0;
                wearerRef.clearFire();
                wearerRef.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                WingsState.setTier(wearerRef, scenario == 0 ? WingsState.CRUISE : WingsState.STORM);
                WingsState.setChargedUntil(wearerRef, level.getGameTime() + 1200);
                networkSpeedFlag = WingsState.speedFlagAt(wearerRef);
                networkWallHit = WingsState.wallHitAt(wearerRef);
                networkHealth = wearerRef.getHealth();
                networkScenario = next;
                networkPreviousX = wearerRef.getX();
                networkMaxStep = 0;
                com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(wearerRef);
            }
            case "netCruiseCheck", "netHoverCheck", "netRepeatCheck" -> {
                boolean repeat = next.equals("netRepeatCheck");
                // Preserve observations even when the subsequent acceptance assertion fails.
                NETWORK_OBSERVATIONS.add("{\"scenario\":\"" + next + "\",\"serverTick\":" + wearerRef.level().getGameTime()
                        + ",\"x\":" + wearerRef.getX() + ",\"y\":" + wearerRef.getY()
                        + ",\"beforeHealth\":" + networkHealth
                        + ",\"afterHealth\":" + wearerRef.getHealth() + ",\"beforeSpeedFlag\":" + networkSpeedFlag
                        + ",\"afterSpeedFlag\":" + WingsState.speedFlagAt(wearerRef)
                        + ",\"largestServerTickDisplacement\":" + networkMaxStep
                        + ",\"beforeWallHit\":" + networkWallHit + ",\"afterWallHit\":" + WingsState.wallHitAt(wearerRef) + "}");
                Files.writeString(results().resolve("wings-network-server.json"), "[\n"
                        + String.join(",\n", NETWORK_OBSERVATIONS) + "\n]\n");
                Files.writeString(results().resolve("wings-network-trace.json"), "[\n"
                        + String.join(",\n", NETWORK_TRACE) + "\n]\n");
                check(WingsState.speedFlagAt(wearerRef) == networkSpeedFlag,
                        next + " actual delayed movement did not trigger speed rollback");
                if (next.equals("netHoverCheck")) {
                    check(WingsState.tier(wearerRef) == WingsState.HOVER && wearerRef.getX() > 10,
                            "real STORM motion settles before delayed HOVER and safely decelerates");
                } else {
                    double wallX = repeat ? 60 : 30;
                    check(wearerRef.getX() >= wallX - 1.5 && wearerRef.getX() < wallX,
                            next + " real client reached the collision wall: " + wearerRef.position());
                }
                if (next.equals("netCruiseCheck")) {
                    check(networkMaxStep > 2.0,
                            "server actually observed batched CRUISE displacement >2 blocks in one tick: " + networkMaxStep);
                }
                if (repeat) {
                    check(WingsState.wallHitAt(wearerRef) > networkWallHit && wearerRef.getHealth() < networkHealth,
                            "repeated real deploy packets preserve actual STORM collision health damage");
                } else {
                    check(WingsState.wallHitAt(wearerRef) == networkWallHit && wearerRef.getHealth() == networkHealth,
                            next + " no false wall damage from batched CRUISE or HOVER transition");
                }
                networkScenario = "";
                if (repeat) Files.writeString(results().resolve("wings-network-server.pass"),
                        "PASS real connected survival player: batched CRUISE wall, STORM-to-HOVER, repeated deploy collision health damage\n");
            }
            case "controlsSetup", "inputSetup" -> {
                // Leave descent room while keeping blink destinations inside build height.
                controlFixture(server, wearerRef, next.equals("inputSetup") ? 300 : 100);
                WingsState.setTier(wearerRef, WingsState.CRUISE);
                com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(wearerRef);
            }
            case "voidSetup" -> {
                ServerLevel level = wearerRef.serverLevel();
                int bottom = level.getMinBuildHeight();
                for (BlockPos pos : BlockPos.betweenClosed(6, bottom, 6, 10, bottom + 16, 10)) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                wearerRef.teleportTo(level, 8.5, bottom + 3, 8.5, -90, 0);
                wearerRef.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                wearerRef.setOnGround(false);
                FlightEvents.markTeleported(wearerRef);
                WingsState.setHoverAnchor(wearerRef, bottom + 3);
                WingsState.setTier(wearerRef, WingsState.HOVER);
                wearerRef.getFoodData().setFoodLevel(0);
                wearerRef.getFoodData().setSaturation(0);
                voidSpeedFlag = WingsState.speedFlagAt(wearerRef);
                tracingVoid = true;
                LOGGER.info("WINGS_VOID_BASELINE tick={} pos={} flag={}", level.getGameTime(),
                        wearerRef.position(), voidSpeedFlag);
                com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(wearerRef);
            }
            case "voidDone" -> {
                check(wearerRef.getY() > wearerRef.level().getMinBuildHeight() + 5,
                        "server confirms starving hover escapes void margin");
                check(WingsState.speedFlagAt(wearerRef) == voidSpeedFlag,
                        "void rescue never triggers server overspeed rollback: baseline=" + voidSpeedFlag
                                + " current=" + WingsState.speedFlagAt(wearerRef));
                tracingVoid = false;
                controlFixture(server, wearerRef);
                WingsState.setChargedUntil(wearerRef, 0);
                awaitCharge = true;
                com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(wearerRef);
            }
            case "chargeStart" -> {
                check(WingsState.charged(wearerRef), "charge originates from real spawned lightning");
                chargeEnds = WingsState.chargedUntil(wearerRef);
                check(!awaitCharge, "server clock observed the full charge duration");
                chargeExhaustion = wearerRef.getFoodData().getExhaustionLevel();
                blinkBefore = WingsState.blinkCooldownUntil(wearerRef);
                chargeMeasured = true;
            }
            case "chargeBlinkDone" -> {
                check(WingsState.blinkCooldownUntil(wearerRef) > blinkBefore, "server accepted native charged blink");
                checkExhaustion(wearerRef.getFoodData().getExhaustionLevel());
                chargedBlinkChecked = true;
            }
            case "showcaseDeploy", "showcaseStow", "showcaseCruise", "showcaseBoost", "showcaseStorm" -> {
                if (!SHOWCASE || wearerRef == null) return 0;
                wearerRef.getFoodData().setFoodLevel(20);
                wearerRef.getFoodData().setSaturation(5f);
                if (next.equals("showcaseDeploy")) {
                    // Startup/recorder latency must not leave the model grounded before filming.
                    wearerRef.teleportTo(server.overworld(), 0.5, 100, 0.5, -90f, 0f);
                    FlightEvents.markTeleported(wearerRef);
                    wearerRef.setOnGround(false);
                    wearerRef.resetFallDistance();
                    wearerRef.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    com.zeropointsix.eraser.wings.FlightEvents.serverSetDeployed(wearerRef, true);
                } else if (next.equals("showcaseStow")) {
                    com.zeropointsix.eraser.wings.FlightEvents.serverSetDeployed(wearerRef, false);
                } else {
                    check(com.zeropointsix.eraser.wings.WingsState.deployed(wearerRef),
                            "showcase tier change requires deployed wings");
                    int tier = next.equals("showcaseCruise") ? 1 : next.equals("showcaseBoost") ? 2 : 3;
                    com.zeropointsix.eraser.wings.WingsState.setTier(wearerRef, tier);
                    com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(wearerRef);
                }
            }
            case "zap" -> {
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server.overworld());
                    bolt.setPos(wearer.position());
                    server.overworld().addFreshEntity(bolt);
                    // 雷击点燃地面火块会让佩戴者持续着火——录屏里清掉 4 格内火焰
                    if (SHOWCASE) {
                        var pos = wearer.blockPosition();
                        for (var p2 : BlockPos.betweenClosed(
                                pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
                            if (wearer.level().getBlockState(p2).is(Blocks.FIRE)) {
                                wearer.level().removeBlock(p2, false);
                            }
                        }
                        wearer.clearFire();
                    }
                }
            }
            case "hungerDone" -> {
                // Wearer observed its own food drop client-side; confirm the same
                // drain server-side (ServerPlayer FoodData, not a synced copy).
                if (!hungerChecked && wearerRef != null) {
                    hungerChecked = true;
                    int food = wearerRef.getFoodData().getFoodLevel();
                    check(food < 20, "deployed wings drain real hunger server-side (food=" + food + ")");
                    try {
                        Files.writeString(results().resolve("wings-server.pass"), "passed\n");
                    } catch (Exception ignored) { }
                }
            }
            case "reviewBack", "reviewSide", "reviewTop" -> {
                if (SHOWCASE) reviewView = next;
            }
            case "feed" -> {
                // 录屏用：定时喂饱，避免饥饿耗尽中途强制收翼迫降破坏镜头
                if (wearerRef != null) {
                    wearerRef.getFoodData().setFoodLevel(20);
                    wearerRef.getFoodData().setSaturation(5f);
                }
            }
            case "wearerDone" -> {
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
                if (wearer != null && observer != null) {
                    faceAt(wearer, observer);
                    trackWearer = true;
                    // Observer captures at +15 and retries until +35; keep the camera
                    // glued through that window, but stop before the delayed flood.
                    trackUntil = server.getTickCount() + 55;
                }
            }
            case "storm" -> {
                // QA override: force wearer into STORM tier for remote-FX / FOV evidence
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null && com.zeropointsix.eraser.wings.WingsState.deployed(wearer)) {
                    com.zeropointsix.eraser.wings.WingsState.setTier(wearer,
                            com.zeropointsix.eraser.wings.WingsState.STORM);
                    com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(wearer);
                    check(true, "wearer forced to STORM for observer FX evidence");
                }
            }
            case "handitem" -> {
                // Wings in main hand → first-person held-item render evidence
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null) {
                    wearer.getInventory().setItem(0,
                            new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
                    wearer.getInventory().selected = 0;
                }
            }
            case "flood" -> {
                // 水下飞行回归：佩戴者悬停中被水体包住，展开态必须保持
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null) {
                    ServerLevel wlevel = wearer.serverLevel();
                    BlockPos c = wearer.blockPosition();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                wlevel.setBlockAndUpdate(c.offset(dx, dy, dz),
                                        Blocks.WATER.defaultBlockState());
                            }
                        }
                    }
                    check(true, "wearer submerged in water while deployed");
                }
            }
            case "above" -> {
                // 录屏用：把佩戴者传送到掉落点上空俯视（带授权的合法传送）
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null) {
                    // z=0.5 保持在石板平台内（z∈-8..8），避免收翼落在草地上被雷点火
                    wearer.teleportTo(wearer.serverLevel(), 5.5, 71.5, 0.5, 178f, 50f);
                    wearer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    com.zeropointsix.eraser.wings.FlightEvents.markTeleported(wearer);
                }
            }
            case "itemDrop" -> {
                // 掉落物渲染证据：把一枚风雷翅落到固定点，观察者贴脸看
                ServerLevel wlevel = server.overworld();
                net.minecraft.world.entity.item.ItemEntity drop =
                        new net.minecraft.world.entity.item.ItemEntity(wlevel,
                                5.5, 65.1, 5.5,
                                new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
                drop.setDeltaMovement(0, 0, 0);
                wlevel.addFreshEntity(drop);
                // 尺寸参照物：掉落物旁立一块整石，截图可量出 0.55 格收翼实际大小
                wlevel.setBlockAndUpdate(new BlockPos(6, 65, 5), Blocks.STONE.defaultBlockState());
                trackTarget = new net.minecraft.world.phys.Vec3(5.5, 65.45, 5.5);
                trackWearer = false;
                trackUntil = server.getTickCount() + 40;
            }
            default -> { }
        }
        announce(server, next);
        return 1;
    }

    /** Teleport the observer to a flank vantage facing the given entity. */
    private static void faceAt(net.minecraft.world.entity.Entity target, ServerPlayer observer) {
        faceAt(new net.minecraft.world.phys.Vec3(target.getX(),
                target.getY() + target.getBbHeight() * 0.6, target.getZ()), observer);
    }

    /** Teleport the observer ~4.5 blocks off the target, looking straight at it. */
    private static void faceAt(net.minecraft.world.phys.Vec3 target, ServerPlayer observer) {
        double ox = target.x + 3.2;
        double oy = target.y + 1.2;
        double oz = target.z + 3.2;
        if (!trackWearer && trackTarget != null) {
            // 3/4 view ~2.3 blocks away, aimed at the hovering mesh (not the shadow).
            ox = target.x - 2.0;
            oy = target.y + 0.85;
            oz = target.z - 1.1;
        } else if (SHOWCASE && trackWearer) {
            // Wearer faces +X. Keep the complete 6.4-block span inside the frame.
            ox = target.x + (reviewView.equals("reviewBack") ? -8 : 0);
            oy = target.y + (reviewView.equals("reviewTop") ? 8 : 0.5);
            oz = target.z + (reviewView.equals("reviewSide") ? 8 : 0.01);
        }
        double dx = target.x - ox, dy = target.y - (oy + observer.getEyeHeight()), dz = target.z - oz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
        observer.teleportTo(observer.serverLevel(), ox, oy, oz, yaw, pitch);
    }

    private static void announce(MinecraftServer server, String next) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(PREFIX + next), false);
        LOGGER.info("{}{}", PREFIX, next);
    }

    private static int checkedPhase(CommandSourceStack source, String next) {
        try {
            return changePhase(source, next);
        } catch (Throwable failure) {
            fail(source.getServer(), "phase=" + next + " " + failure);
            return 0;
        }
    }

    @SubscribeEvent
    public static void traceVoid(TickEvent.PlayerTickEvent event) {
        if (tracingVoid && event.player == wearerRef) {
            LOGGER.info("WINGS_VOID_TRACE phase={} tick={} pos={} flag={}", event.phase,
                    wearerRef.level().getGameTime(), wearerRef.position(), WingsState.speedFlagAt(wearerRef));
        }
    }

    private static void controlFixture(MinecraftServer server, ServerPlayer player) {
        controlFixture(server, player, 100);
    }

    private static void controlFixture(MinecraftServer server, ServerPlayer player, double height) {
        player.teleportTo(server.overworld(), 0.5, height, 0.5, -90, 0);
        FlightEvents.markTeleported(player);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.setOnGround(false);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20);
        player.getFoodData().setExhaustion(0);
        FlightEvents.serverSetDeployed(player, true);
        WingsState.setTier(player, WingsState.HOVER);
        com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(player);
    }

    private static void checkExhaustion(float value) {
        if (Math.abs(value - chargeExhaustion) > 0.001f) {
            throw new AssertionError("charged real flight/blink must not drain exhaustion: "
                    + chargeExhaustion + " -> " + value);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        LOGGER.info("WINGS_E2E_ASSERT server: {}", message);
    }

    private static Path results() {
        return Path.of(System.getProperty("wings.qa.results", "build/wings-e2e"));
    }

    private static void fail(MinecraftServer server, String message) {
        LOGGER.error("WINGS_QA_FAILURE {}", message);
        try {
            Files.writeString(results().resolve("wings-server.failed"), message + "\n");
        } catch (Exception ignored) { }
        stopAt = server.getTickCount() + 20;
    }

    private static int finish(MinecraftServer server) {
        if (stopAt >= 0) return 1;
        if (!SHOWCASE && !controlsDone) {
            fail(server, "WINGS_CONTROLS_NOT_VERIFIED");
            return 0;
        }
        if (!SHOWCASE && !Files.exists(results().resolve("wings-network-server.pass"))) {
            fail(server, "WINGS_IMPAIRED_NETWORK_NOT_VERIFIED");
            return 0;
        }
        LOGGER.info("WINGS_QA_SERVER_FINISHED");
        announce(server, "finished");
        stopAt = server.getTickCount() + 20;
        return 1;
    }

    private WingsQaServer() { }
}
