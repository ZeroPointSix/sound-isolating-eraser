package com.zeropointsix.gravitytest;

import com.zeropointsix.eraser.client.WingsClientData;
import com.zeropointsix.eraser.wings.net.HoverTogglePacket;
import com.zeropointsix.eraser.wings.net.SetDeployedPacket;
import com.zeropointsix.eraser.wings.net.WingsNet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** QA-only impairment of the real game connection, never a synthetic player move. */
final class WingsNetworkQa {
    private static final String HANDLER = "wings-qa-network-delay";
    private static final long MIN_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final String[] SCENARIOS = {"netCruise", "netHover", "netRepeat"};
    private static int scenario;
    private static int step;
    private static long startedAt;
    private static Channel channel;
    private static Delay delay;

    static void begin(Minecraft mc) {
        scenario = 0;
        step = 0;
        startedAt = System.nanoTime();
        command(mc, SCENARIOS[scenario]);
    }

    static boolean tick(Minecraft mc, Path results, String phase) throws Exception {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if (elapsedMs > 20000) throw new AssertionError("Network QA timed out: " + SCENARIOS[scenario] + " step=" + step);
        if (step == 0) {
            var info = WingsClientData.get(mc.player);
            double z = scenario * 32 + 0.5;
            if (!phase.equals(SCENARIOS[scenario]) || info == null || !info.deployed()
                    || Math.abs(mc.player.getX() - 0.5) > 2 || Math.abs(mc.player.getZ() - z) > 2) return false;
            if (delay == null) {
                channel = mc.getConnection().getConnection().channel();
                delay = new Delay();
                channel.pipeline().addLast(HANDLER, delay);
            }
            mc.options.keyUp.setDown(true);
            step = 1;
            startedAt = System.nanoTime();
        } else if (step == 1) {
            if (scenario == 2) WingsNet.CHANNEL.sendToServer(new SetDeployedPacket(true));
            if (scenario == 1 && elapsedMs >= 1800) {
                WingsNet.CHANNEL.sendToServer(new HoverTogglePacket());
                step = 2;
                startedAt = System.nanoTime();
            } else if (scenario != 1 && elapsedMs >= 5000) {
                mc.options.keyUp.setDown(false);
                step = 3;
                startedAt = System.nanoTime();
            }
        } else if (step == 2 && elapsedMs >= 3000) {
            require(WingsClientData.get(mc.player).tier() == 0, "delayed HOVER action synchronized");
            mc.options.keyUp.setDown(false);
            step = 3;
            startedAt = System.nanoTime();
        } else if (step == 3 && elapsedMs >= 700) {
            command(mc, SCENARIOS[scenario] + "Check");
            step = 4;
            startedAt = System.nanoTime();
        } else if (step == 4 && phase.equals(SCENARIOS[scenario] + "Check")) {
            if (++scenario < SCENARIOS.length) {
                command(mc, SCENARIOS[scenario]);
                step = 0;
                startedAt = System.nanoTime();
            } else {
                String metrics = channel.eventLoop().submit(() -> delay.report()).syncUninterruptibly().getNow();
                Files.writeString(results.resolve("wings-network-client.json"), metrics);
                restore(mc);
                require(Files.exists(results.resolve("wings-network-server.pass")), "server validated actual delayed movement");
                Files.writeString(results.resolve("wings-network.pass"),
                        "PASS real Minecraft TCP connection; 100 ms minimum delay plus 100 ms batching; CRUISE wall, STORM-to-HOVER and repeated deploy wall damage\n");
                return true;
            }
        }
        return false;
    }

    static void restore(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        if (channel != null && channel.pipeline().get(HANDLER) != null) channel.pipeline().remove(HANDLER);
        channel = null;
        delay = null;
    }

    private static void command(Minecraft mc, String word) {
        mc.player.connection.sendCommand("wingsqa phase " + word);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("WINGS_NETWORK_ASSERT " + message);
    }

    private record Pending(Object message, ChannelPromise promise, long queuedAt) { }

    private static final class Delay extends ChannelOutboundHandlerAdapter {
        private final ArrayDeque<Pending> pending = new ArrayDeque<>();
        private final List<Long> delays = new ArrayList<>();
        private io.netty.util.concurrent.ScheduledFuture<?> timer;
        private int moves;
        private int batches;
        private int largestMovementBatch;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            timer = ctx.executor().scheduleAtFixedRate(() -> drain(ctx, false), 100, 100, TimeUnit.MILLISECONDS);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            // Keep commands and actions ordered with movement; delaying just position packets
            // would introduce artificial overtaking rather than transport-like impairment.
            if (message instanceof Packet<?>) pending.addLast(new Pending(message, promise, System.nanoTime()));
            else super.write(ctx, message, promise);
        }

        private void drain(ChannelHandlerContext ctx, boolean closing) {
            long now = System.nanoTime();
            int movementBatch = 0;
            while (!pending.isEmpty() && (closing || now - pending.peekFirst().queuedAt() >= MIN_DELAY_NS)) {
                Pending packet = pending.removeFirst();
                if (!closing) delays.add(TimeUnit.NANOSECONDS.toMillis(now - packet.queuedAt()));
                if (packet.message() instanceof ServerboundMovePlayerPacket && !closing) {
                    movementBatch++;
                    moves++;
                }
                ctx.write(packet.message(), packet.promise());
            }
            if (movementBatch >= 2) batches++;
            largestMovementBatch = Math.max(largestMovementBatch, movementBatch);
            ctx.flush();
        }

        String report() {
            require(moves >= 60, "at least 60 real movement packets traversed the delay: " + moves);
            require(batches >= 1, "real movement packets were coalesced into at least one TCP flush: " + batches);
            List<Long> ordered = delays.stream().sorted().toList();
            long median = ordered.get(ordered.size() / 2);
            require(ordered.get(0) >= 100 && median <= 250,
                    "measured delay has >=100 ms minimum and bounded median: " + median);
            return "{\"transport\":\"real-client-tcp\",\"minimumConfiguredMs\":100,\"batchWindowMs\":100,"
                    + "\"forwardedMovementPackets\":" + moves + ",\"multiMoveBatches\":" + batches
                    + ",\"largestMovementBatch\":" + largestMovementBatch + ",\"minDelayMs\":" + ordered.get(0)
                    + ",\"medianDelayMs\":" + median + ",\"maxDelayMs\":" + ordered.get(ordered.size() - 1) + "}\n";
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            timer.cancel(false);
            drain(ctx, true);
        }
    }

    private WingsNetworkQa() { }
}
