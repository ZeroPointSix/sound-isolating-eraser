package com.zeropointsix.gravitytest;

import com.zeropointsix.eraser.gametest.GameTestConfigs;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.wings.FlightEvents;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.wings.WingsState;
import com.zeropointsix.eraser.wings.net.WingsNet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Real connected-player observations; fixture teleporting ends before measurement. */
final class WingsMotionQaServer {
    private static int scenario = -1;
    private static boolean running;
    private static GameTestConfigs.LiveConfig liveConfig;
    private static final Set<String> ready = new HashSet<>();
    private static final Set<String> prepared = new HashSet<>();
    private static final Map<String, Sample> starts = new HashMap<>();
    private static final Set<String> ended = new HashSet<>();
    private static final List<String> observations = new ArrayList<>();
    private static final List<String> trace = new ArrayList<>();
    private record Sample(double x, long tick, long wall, long flag) { }

    static void phase(CommandSourceStack source, String word, Path results) throws Exception {
        MinecraftServer server = source.getServer();
        if (word.startsWith("motionSetup")) {
            prepared.clear();
            announce(server, "motionPrepare" + word.substring("motionSetup".length()));
        } else if (word.startsWith("motionPrepared")) {
            prepared.add(source.getPlayerOrException().getGameProfile().getName());
            if (prepared.size() != 2) return;
            word = "motionSetup" + word.substring("motionPrepared".length());
            scenario = Integer.parseInt(word.substring("motionSetup".length()));
            ready.clear(); starts.clear(); ended.clear(); running = false;
            boolean custom = scenario >= 4 && scenario < 8;
            configure(custom);
            // Finish blocking chunk work before teleporting either client.
            if (scenario == 0) {
                for (int x = -1; x <= 100; x++) server.overworld().getChunk(x, 0);
            }
            int index = 0;
            for (String name : List.of(WingsQaServer.WEARER, WingsQaServer.OBSERVER)) {
                ServerPlayer p = server.getPlayerList().getPlayerByName(name);
                p.setGameMode(GameType.SURVIVAL);
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
                FlightEvents.serverSetDeployed(p, false);
                p.teleportTo(server.overworld(), 0.5, 280, index++ * 4 + 0.5, -90, 0);
                p.setDeltaMovement(Vec3.ZERO);
                p.setOnGround(false);
                FlightEvents.markTeleported(p);
                p.getFoodData().setFoodLevel(20);
                p.getFoodData().setSaturation(20);
                WingsState.setChargedUntil(p, p.level().getGameTime() + 2000);
                FlightEvents.serverSetDeployed(p, true);
                int target = scenario < 8 ? scenario % 4 : WingsState.STORM;
                for (int i = 0; i < target; i++) FlightEvents.serverCycleTier(p);
                WingsNet.syncToTracking(p);
            }
            announce(server, word);
        } else if (word.equals("motionBegin")) {
            ready.add(source.getPlayerOrException().getGameProfile().getName());
            if (ready.size() == 2) {
                for (String name : ready) {
                    ServerPlayer p = server.getPlayerList().getPlayerByName(name);
                    starts.put(name, new Sample(p.getX(), p.level().getGameTime(), System.nanoTime(), WingsState.speedFlagAt(p)));
                }
                running = true;
                announce(server, "motionRun" + scenario);
            }
        } else if (word.equals("motionDowngrade")) {
            ServerPlayer p = source.getPlayerOrException();
            p.getFoodData().setFoodLevel(WingsConfig.SERVER.minFoodForFastTiers.get() - 1);
        } else if (word.equals("motionEnd")) {
            ServerPlayer p = source.getPlayerOrException();
            String name = p.getGameProfile().getName();
            Sample start = starts.get(name);
            long ticks = p.level().getGameTime() - start.tick();
            double speed = (p.getX() - start.x()) * 20.0 / ticks;
            double wallSpeed = (p.getX() - start.x()) * 1e9 / (System.nanoTime() - start.wall());
            double target = WingsConfig.SERVER.tierSpeed(scenario < 8 ? scenario % 4 : scenario < 10 ? 1 : 0);
            observations.add("{\"scenario\":" + scenario + ",\"player\":\"" + name
                    + "\",\"ticks\":" + ticks + ",\"serverMps\":" + speed + ",\"wallMps\":" + wallSpeed
                    + ",\"configuredMps\":" + target + ",\"tier\":" + WingsState.tier(p)
                    + ",\"beforeFlag\":" + start.flag() + ",\"afterFlag\":" + WingsState.speedFlagAt(p) + "}");
            write(results);
            check(WingsState.speedFlagAt(p) == start.flag(), "no rollback during scenario " + scenario + " " + name);
            if (scenario < 8) check(Math.abs(speed / target - 1) < 0.10,
                    "server steady speed within 10%: scenario=" + scenario + " measured=" + speed + " target=" + target);
            else check(WingsState.tier(p) == (scenario < 10 ? WingsState.CRUISE : WingsState.HOVER),
                    "downgrade/stow final tier synchronized");
            ended.add(name);
            if (ended.size() == 2) {
                running = false;
                announce(server, "motionDone" + scenario);
                if (scenario == 11) {
                    restoreConfig();
                    Files.writeString(results.resolve("wings-motion-server.pass"), "PASS 12 scenarios, two actual survival players\n");
                }
            }
        }
    }

    static void tick(MinecraftServer server, Path results) throws Exception {
        if (!running) return;
        for (String name : starts.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name);
            trace.add("{\"scenario\":" + scenario + ",\"player\":\"" + name + "\",\"tick\":"
                    + p.level().getGameTime() + ",\"x\":" + p.getX() + ",\"y\":" + p.getY()
                    + ",\"z\":" + p.getZ() + ",\"tier\":" + WingsState.tier(p)
                    + ",\"deployed\":" + WingsState.deployed(p) + ",\"speedFlag\":" + WingsState.speedFlagAt(p) + "}");
        }
        if (server.getTickCount() % 20 == 0) write(results);
    }

    static void configure(boolean custom) {
        // ConfigValue.set autosaves live TOML; its watcher can race the four speed changes.
        if (liveConfig == null) liveConfig = GameTestConfigs.sandbox(WingsConfig.SERVER_SPEC);
        var c = WingsConfig.SERVER;
        c.hoverMaxSpeed.set(custom ? 8.0 : 5.0);
        c.cruiseSpeed.set(custom ? 24.0 : 30.0);
        c.boostSpeed.set(custom ? 48.0 : 60.0);
        c.stormSpeed.set(custom ? 96.0 : 120.0);
    }

    static void restoreConfig() {
        if (liveConfig == null) return;
        liveConfig.close();
        liveConfig = null;
    }

    private static void write(Path results) throws Exception {
        Files.writeString(results.resolve("wings-motion-server.json"), "[" + String.join(",\n", observations) + "]\n");
        Files.writeString(results.resolve("wings-motion-server-trace.json"), "[" + String.join(",\n", trace) + "]\n");
    }

    private static void announce(MinecraftServer server, String phase) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("WINGS_QA_PHASE:" + phase), false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("WINGS_MOTION_SERVER_ASSERT " + message);
    }
}
