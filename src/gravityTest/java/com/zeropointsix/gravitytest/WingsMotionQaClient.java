package com.zeropointsix.gravitytest;

import com.zeropointsix.eraser.client.WingsClientData;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.wings.net.SetDeployedPacket;
import com.zeropointsix.eraser.wings.net.WingsNet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** No setPos/move/velocity override: actual LocalPlayer travel and actual TCP. */
final class WingsMotionQaClient {
    private static int scenario = -1, preparing = -1, step, ticks, remoteSamples;
    private static double startX, remoteX, recentX;
    private static long startedAt;
    private static boolean requested;
    private static final List<String> observations = new ArrayList<>();
    private static final List<String> trace = new ArrayList<>();

    static void tick(Minecraft mc, Path results, String role, String phase) throws Exception {
        boolean leader = role.equals("wingswearer");
        if (scenario == -1 && phase.equals("ready")) {
            Files.writeString(results.resolve(leader ? "wings-wearer.ready" : "wings-observer.ready"), "ready\n");
            if (leader && !requested) { command(mc, "motionSetup0"); requested = true; }
        }
        if (phase.startsWith("motionPrepare")) {
            int next = Integer.parseInt(phase.substring("motionPrepare".length()));
            if (next != preparing) {
                preparing = next;
                mc.options.keyJump.setDown(false);
                mc.options.keyUp.setDown(false);
                command(mc, "motionPrepared" + next);
            }
            return;
        }
        if (phase.startsWith("motionSetup")) {
            int next = Integer.parseInt(phase.substring("motionSetup".length()));
            if (next != scenario) {
                scenario = next; step = 0; ticks = 0; remoteSamples = 0;
                startedAt = System.nanoTime();
                WingsMotionQaServer.configure(scenario >= 4 && scenario < 8);
                mc.setScreen(null);
                mc.options.keyJump.setDown(false);
                mc.options.keyUp.setDown(false);
            }
        }
        if (scenario < 0 || step == 4) return;
        if ((System.nanoTime() - startedAt) / 1e9 > 90) throw new AssertionError("motion timeout " + scenario
                + " step=" + step + " pos=" + mc.player.position() + " info=" + WingsClientData.get(mc.player));
        var info = WingsClientData.get(mc.player);
        if (step == 0) {
            if (info == null || !info.deployed() || info.tier() != (scenario < 8 ? scenario % 4 : 3)
                    || Math.abs(mc.player.getX() - 0.5) > 3 || Math.abs(mc.player.getY() - 280) > 3) return;
            mc.options.keyUp.setDown(true);
            step = 1; ticks = 0;
        } else if (step == 1 && ++ticks == 100) {
            command(mc, "motionBegin");
        } else if (step == 1 && phase.equals("motionRun" + scenario)) {
            startX = recentX = mc.player.getX();
            step = 2; ticks = 0;
            if (scenario >= 8) {
                mc.options.keyUp.setDown(false);
                mc.options.keyJump.setDown(scenario == 9);
                if (scenario < 10) command(mc, "motionDowngrade");
                else {
                    WingsNet.CHANNEL.sendToServer(new SetDeployedPacket(false));
                    if (scenario == 11) WingsNet.CHANNEL.sendToServer(new SetDeployedPacket(true));
                }
            }
        } else if (step == 2) {
            ticks++;
            if (scenario == 10 && ticks == 3) WingsNet.CHANNEL.sendToServer(new SetDeployedPacket(true));
            trace.add("{\"scenario\":" + scenario + ",\"clientTick\":" + mc.player.tickCount
                    + ",\"gameTick\":" + mc.level.getGameTime() + ",\"x\":" + mc.player.getX()
                    + ",\"y\":" + mc.player.getY() + ",\"dx\":" + (mc.player.getX() - recentX)
                    + ",\"tier\":" + info.tier() + ",\"forward\":" + mc.player.input.up
                    + ",\"jump\":" + mc.player.input.jumping + ",\"fallFlying\":" + mc.player.isFallFlying() + "}");
            double speed = (mc.player.getX() - recentX) * 20;
            recentX = mc.player.getX();
            var remote = mc.level.players().stream().filter(p -> p != mc.player).findFirst().orElse(null);
            if (remote != null && ticks > 10) {
                var state = WingsClientData.get(remote);
                if (state != null && state.deployed() && state.tier() == info.tier() && remote.getX() > remoteX) remoteSamples++;
                remoteX = remote.getX();
            }
            if (ticks == 120) {
                double actual = (mc.player.getX() - startX) * 20 / ticks;
                double expected = WingsConfig.SERVER.tierSpeed(scenario < 8 ? scenario % 4 : scenario < 10 ? 1 : 0);
                observations.add("{\"scenario\":" + scenario + ",\"clientTicks\":" + ticks
                        + ",\"actualMps\":" + actual + ",\"configuredMps\":" + expected
                        + ",\"lastStepMps\":" + speed + ",\"remoteMovingSyncedSamples\":" + remoteSamples + "}");
                Files.writeString(results.resolve("wings-motion-" + role + ".json"), "[" + String.join(",\n", observations) + "]\n");
                Files.writeString(results.resolve("wings-motion-" + role + "-trace.json"), "[" + String.join(",\n", trace) + "]\n");
                if (scenario < 8) {
                    check(Math.abs(actual / expected - 1) < 0.10, "actual travel speed within 10%: " + actual + "/" + expected);
                    check(remoteSamples >= 40, "both equipped flyers receive moving remote state: " + remoteSamples);
                } else {
                    double cap = WingsConfig.SERVER.tierSpeed(scenario < 10 ? 1 : 0);
                    check(Math.abs(speed) <= cap * 1.1, "release/jump/stow loses old STORM speed: " + speed);
                    check(info.deployed() && info.tier() == (scenario < 10 ? 1 : 0), "local downgraded state");
                }
                if (scenario == 2) {
                    var image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                    try { image.writeToFile(results.resolve("wings-motion-" + role + ".png")); }
                    finally { image.close(); }
                }
                command(mc, "motionEnd");
                step = 3;
            }
        } else if (step == 3 && phase.equals("motionDone" + scenario)) {
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(false);
            step = 4;
            if (scenario == 11) Files.writeString(results.resolve("wings-motion-" + role + ".pass"), "PASS actual travel and remote simultaneous flight, 12 scenarios\n");
            else if (leader) command(mc, "motionSetup" + (scenario + 1));
        }
    }

    private static void command(Minecraft mc, String phase) { mc.player.connection.sendCommand("wingsqa phase " + phase); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("WINGS_MOTION_CLIENT_ASSERT " + message);
    }
}
