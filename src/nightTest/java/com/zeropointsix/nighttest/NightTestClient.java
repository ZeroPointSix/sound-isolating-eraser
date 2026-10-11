package com.zeropointsix.nighttest;

import com.zeropointsix.eraser.eternalnight.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = "night_qa", value = Dist.CLIENT)
public final class NightTestClient {
    private static final String ROLE = System.getProperty("night.qa.role");
    private static final Path RESULTS = Path.of(System.getProperty("night.qa.results", "build/night-e2e"));
    private static String phase = "";
    private static int ticks, beforeWhite;
    private static boolean finished, walking;

    @SubscribeEvent public static void chat(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("NIGHT_QA_")) { phase = text.substring(9); ticks = 0; event.setCanceled(true); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (ROLE == null || "server".equals(ROLE) || finished || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        try {
            ticks++;
            if (phase.equals("READY") && ticks == 80) {
                require(EternalNightContent.isCity(client.level), "client actually loaded the city dimension");
                client.setScreen(null);
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                client.mouseHandler.grabMouse();
                for (var state : EternalNightContent.NEON_SIGN.get().getStateDefinition().getPossibleStates()) {
                    var sprite = client.getBlockRenderer().getBlockModel(state).getParticleIcon();
                    require(!sprite.contents().name().getPath().contains("missingno"), "original neon model resolves all 80 states");
                }
                beforeWhite = capture(client, "original-art");
                client.player.connection.sendCommand("nightqa ready");
            }
            if (phase.equals("OPEN") && ticks == 5) nativeInput("click", "3");
            if (phase.equals("WHITE") && ticks == 40) {
                int after = capture(client, "shared-white-exit");
                require(after > beforeWhite + 150, "white exit appears in actual framebuffer: before=" + beforeWhite + ", after=" + after);
                Files.writeString(RESULTS.resolve(ROLE + "-white.pass"), "PASS original model states and shared white plane; before=" + beforeWhite + " after=" + after + "\n");
                client.player.connection.sendCommand("nightqa captured");
            }
            if (phase.equals("WALK") && !walking) { nativeInput("keydown", "w"); walking = true; }
            if (walking && client.level.dimension().equals(Level.OVERWORLD)) {
                nativeInput("keyup", "w"); walking = false;
                capture(client, "returned-home");
                Files.writeString(RESULTS.resolve(ROLE + "-return.pass"), "PASS native walking through shared exit returned client to overworld\n");
                finished = true;
            }
            if (walking && ticks > 300) throw new AssertionError("Native walk did not cross the exit");
        } catch (Throwable error) {
            error.printStackTrace();
            try { nativeInput("keyup", "w"); capture(client, "failed"); Files.writeString(RESULTS.resolve(ROLE + ".failed"), error.toString()); }
            catch (Exception ignored) { }
            finished = true;
        }
    }
    private static int capture(Minecraft client, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int white = 0;
            for (int y = image.getHeight() / 5; y < image.getHeight() * 3 / 4; y++)
                for (int x = image.getWidth() * 3 / 10; x < image.getWidth() * 7 / 10; x++) {
                    int color = image.getPixelRGBA(x, y);
                    if ((color & 255) > 220 && ((color >>> 8) & 255) > 220 && ((color >>> 16) & 255) > 220) white++;
                }
            image.writeToFile(RESULTS.resolve(ROLE + "-" + name + ".png"));
            return white;
        }
    }
    private static void nativeInput(String... keys) throws Exception {
        var args = new java.util.ArrayList<String>(); args.add("xdotool"); args.addAll(java.util.List.of(keys));
        require(new ProcessBuilder(args).inheritIO().start().waitFor() == 0, "native input succeeds");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
