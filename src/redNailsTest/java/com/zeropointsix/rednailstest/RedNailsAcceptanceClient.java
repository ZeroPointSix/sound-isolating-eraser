package com.zeropointsix.rednailstest;

import com.zeropointsix.eraser.client.RedNailsClient;
import com.zeropointsix.eraser.rednails.WaterGhostEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "red_nails_qa", value = Dist.CLIENT)
public final class RedNailsAcceptanceClient {
    private static final boolean ENABLED = Boolean.getBoolean("rednails.qa.acceptance");
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static String phase = "";
    private static int ticks, attacks;
    private static boolean failed;
    private static void reset() {
        var mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
        RedNailsClient.KEY.setDown(false);
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            mc.player.setShiftKeyDown(false);
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        if (!ENABLED) return;
        String text = event.getMessage().getString();
        if (text.startsWith("RED_NAILS_ACCEPTANCE:")) {
            reset(); phase = text.substring("RED_NAILS_ACCEPTANCE:".length()); ticks = 0; attacks = 0;
            event.setCanceled(true);
        }
    }
    private static void signal(String name, String body) throws Exception {
        Path target = RESULTS.resolve("acceptance-client-" + name + ".signal");
        Path pending = RESULTS.resolve("acceptance-client-" + name + ".pending");
        Files.writeString(pending, body + "\n");
        Files.move(pending, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || failed || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (mc.screen instanceof DisconnectedScreen) throw new AssertionError("native acceptance disconnected phase=" + phase);
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            GLFW.glfwFocusWindow(mc.getWindow().getWindow());
            mc.setScreen(null); mc.mouseHandler.grabMouse();
            if (!mc.isWindowActive()) return;
            ticks++;
            if (phase.isEmpty()) {
                signal("ready", "real client loaded, active window, real world");
                return;
            }
            if (phase.endsWith("-ready") || phase.endsWith("-moving")) {
                if (mc.player.getVehicle() instanceof Boat) mc.options.keyUp.setDown(true);
                return;
            }
            if (phase.endsWith("-held")) {
                if (phase.startsWith("damage")) {
                    for (var entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof WaterGhostEntity ghost && ghost.waterGhostGrabAmount(1) > 0.5
                                && ghost.distanceToSqr(mc.player) < 16 && ticks >= 10 + attacks * 22
                                && mc.player.getAttackStrengthScale(0) >= 0.95 && attacks < 2) {
                            mc.gameMode.attack(mc.player, ghost);
                            mc.player.swing(InteractionHand.MAIN_HAND); attacks++;
                            signal("damage-attack-" + attacks, "real client attack packet tick=" + ticks);
                        }
                    }
                } else {
                    mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(true); mc.options.keySprint.setDown(true);
                    signal(phase.replace("-held", "-resist"), ticks + "\n" + "native movement+jump+sprint held; clientTick=" + ticks
                            + " position=" + mc.player.position() + " passenger=" + mc.player.isPassenger());
                }
                return;
            }
            if (phase.endsWith("-complete")) {
                reset();
                if (ticks == 5) {
                    String name = phase.replace("-complete", "");
                    screenshot(name);
                    signal(name, "real client completed " + name + " position=" + mc.player.position());
                }
                return;
            }
            if (phase.equals("coexist")) {
                RedNailsClient.KEY.setDown(ticks > 5);
                if (ticks == 20) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (phase.equals("item-recall")) {
                mc.options.keyShift.setDown(true);
                RedNailsClient.KEY.setDown(ticks > 5);
            } else if (phase.equals("item-resummon")) {
                RedNailsClient.KEY.setDown(ticks > 5);
            } else if (phase.equals("watch-recall")) {
                if (ticks == 10) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (phase.equals("complete") && ticks == 5) {
                reset(); screenshot("complete");
                Files.writeString(RESULTS.resolve("acceptance-client.pass"), "Real input, native world and OpenGL frames completed.\n");
            }
        } catch (Throwable error) {
            failed = true; reset(); error.printStackTrace();
            try { Files.writeString(RESULTS.resolve("acceptance-client.failed"), error.toString()); }
            catch (Exception ignored) { }
            try { screenshot("failure"); }
            catch (Exception ignored) { }
        }
    }
    private static void screenshot(String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            java.util.HashSet<Integer> colors = new java.util.HashSet<>();
            for (int y = 0; y < image.getHeight(); y += 8) for (int x = 0; x < image.getWidth(); x += 8) colors.add(image.getPixelRGBA(x, y));
            if (colors.size() <= 100) throw new AssertionError("nonblank native OpenGL frame required");
            image.writeToFile(RESULTS.resolve("acceptance-" + name + ".png"));
        }
    }
}
