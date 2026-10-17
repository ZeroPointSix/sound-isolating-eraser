package com.zeropointsix.suxiatest;

import com.zeropointsix.eraser.client.SuxiaClient;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;

/** Captures the unmodified item decoration from the actual OpenGL hotbar. */
public final class SuxiaCooldownClient {
    static final boolean ENABLED = Boolean.getBoolean("suxia.qa.cooldownOnly");
    private static final int EXPECTED_DURATION = Boolean.getBoolean("suxia.qa.deathCooldown") ? 12000 : 3600;
    private static final Path OUT = Path.of(System.getProperty("suxia.qa.results"));
    private static String phase = "", pending = "";
    private static int ticks, initialRemaining, samples;
    private static boolean failed, captured;
    private static UUID watchId;
    private static long deadline;

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try (var shot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            shot.writeToFile(OUT.resolve("client-failure.png"));
        } catch (Exception ignored) { }
        try { Files.writeString(OUT.resolve("client.failed"), error.toString()); } catch (Exception ignored) { }
    }
    static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("SUXIA_COOLDOWN:")) {
            phase = text.substring("SUXIA_COOLDOWN:".length()); ticks = 0; samples = 0; captured = false;
            event.setCanceled(true);
        }
    }
    static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        var mc = Minecraft.getInstance();
        try {
            if (!pending.isEmpty()) {
                ticks++;
                if (ticks >= 40 && (pending.equals("rejoin") || Files.exists(OUT.resolve("restart-server-ready.signal")))) {
                    String address = "127.0.0.1:" + Integer.getInteger("suxia.qa.port", 25589);
                    ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                            new ServerData("Cooldown boundary QA", address, false), false);
                    pending = ""; ticks = 0;
                }
                return;
            }
            if (mc.level == null || mc.player == null || phase.isEmpty()) return;
            ticks++;
            mc.options.hideGui = false;
            if (ticks == 1) { mc.setScreen(null); mc.mouseHandler.grabMouse(); }
            if (ticks == 5 && (phase.equals("cool-ready") || phase.equals("cool-summoned")))
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            if (ticks == 5 && phase.equals("cool-drop")) {
                require(mc.player.drop(false), "actual client drop packet accepted");
            }
            if (phase.startsWith("cool-disconnect-") && ticks == 5) {
                pending = phase.endsWith("restart") ? "restart" : "rejoin";
                mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); phase = ""; ticks = 0;
                if (pending.equals("restart")) Files.writeString(OUT.resolve("client-ready-restart.pass"), "Disconnected actual client before server JVM stop.\n");
            }
        } catch (Throwable error) { fail(error); }
    }
    static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        try {
            if (phase.isEmpty() && watchId == null && mc.level.hasChunkAt(mc.player.blockPosition())
                    && mc.player.getMainHandItem().is(ModItems.SUXIA_POCKET_WATCH.get())) {
                Files.writeString(OUT.resolve("client-cool-ready.signal"), "Real loaded client with synchronized watch inventory.\n");
                return;
            }
            if (captured || !java.util.Set.of("cool-before", "cool-picked", "cool-rejoined", "cool-restarted").contains(phase)) return;
            if (ticks < (samples == 0 ? 60 : 100)) return;
            var stack = mc.player.getMainHandItem();
            var tag = stack.getTag();
            require(stack.is(ModItems.SUXIA_POCKET_WATCH.get()) && tag != null, "actual hotbar watch received");
            if (watchId == null) { watchId = tag.getUUID("WatchUUID"); deadline = tag.getLong("CooldownUntil"); }
            require(tag.getUUID("WatchUUID").equals(watchId) && tag.getLong("CooldownUntil") == deadline,
                    "same watch and exact deadline across all boundaries");
            require(tag.getInt("CooldownDuration") == EXPECTED_DURATION && !tag.getBoolean("Open"), "expected cooldown, closed item");
            int remaining = tag.getInt("CooldownRemaining");
            float fraction = SuxiaClient.cooldownFraction(stack);
            require(remaining > 0 && fraction > 0 && Math.abs(fraction - remaining / (float) EXPECTED_DURATION) < .00001,
                    "positive per-watch mask after " + phase);
            require(!mc.player.getCooldowns().isOnCooldown(stack.getItem()), "no global cooldown mask");
            var control = mc.player.getInventory().getItem(1);
            require(control.is(stack.getItem()) && SuxiaClient.cooldownFraction(control) == 0, "control watch stays ready");
            if (samples == 0) initialRemaining = remaining;
            else require(remaining < initialRemaining, "cooldown visibly counts down instead of frozen restore");
            String record;
            try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var colors = new HashSet<Integer>();
                for (int x = 0; x < shot.getWidth(); x += 4) for (int y = 0; y < shot.getHeight(); y += 4) colors.add(shot.getPixelRGBA(x, y));
                require(colors.size() > 100, "nonblank real framebuffer");
                int scale = (int) mc.getWindow().getGuiScale();
                int x0 = mc.getWindow().getGuiScaledWidth() / 2 - 91 + 3;
                int y0 = mc.getWindow().getGuiScaledHeight() - 19;
                int top = (int) Math.floor(16 * (1 - fraction));
                int matches = 0, tested = 0;
                for (int x = 2; x < 14; x++) for (int y = Math.max(top + 1, 2); y < 14; y++) {
                    int actual = shot.getPixelRGBA((x0 + x) * scale, (y0 + y) * scale);
                    int baseline = shot.getPixelRGBA((x0 + x + 20) * scale, (y0 + y) * scale);
                    boolean match = true, changed = false;
                    for (int shift : new int[]{0, 8, 16}) {
                        int a = (actual >>> shift) & 255, b = (baseline >>> shift) & 255;
                        match &= Math.abs(a - (127 + b * 128 / 255)) <= 6;
                        changed |= a - b > 20;
                    }
                    if (match && changed) matches++;
                    tested++;
                }
                record = "phase=" + phase + " sample=" + samples + " watch=" + watchId + " until=" + deadline
                        + " remaining=" + remaining + " duration=" + EXPECTED_DURATION + " fraction=" + fraction
                        + " controlFraction=0 maskPixels=" + matches + "/" + tested + " hudVisible=" + !mc.options.hideGui;
                shot.writeToFile(OUT.resolve(phase + "-" + samples + ".png"));
                Files.writeString(OUT.resolve(phase + "-" + samples + ".txt"), record + "\n");
                require(matches >= 12, "actual translucent mask differs from ready control in >=12 pixels: " + record);
            }
            System.out.println("SUXIA_COOLDOWN_FRAME=" + record);
            samples++;
            if (samples == 2) {
                Files.writeString(OUT.resolve("client-" + phase + ".pass"), record + "; countdown verified\n");
                captured = true;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
        } catch (Throwable error) { fail(error); }
    }
}
