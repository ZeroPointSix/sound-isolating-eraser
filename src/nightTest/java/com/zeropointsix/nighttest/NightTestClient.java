package com.zeropointsix.nighttest;

import com.zeropointsix.eraser.eternalnight.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = "night_qa", value = Dist.CLIENT)
public final class NightTestClient {
    private static final String ROLE = System.getProperty("night.qa.role");
    private static final Path RESULTS = Path.of(System.getProperty("night.qa.results", "build/night-e2e"));
    private static final boolean RELOADED = "reload".equals(System.getProperty("night.qa.session"));
    private static String phase = "";
    private static int ticks, hiddenFrames, homeFrames;
    private static boolean finished, walking, hidePlane, returning;
    private static boolean restarting, respawnClicked;
    private static boolean surveyWalking;
    private static boolean entryCaptionProven;
    private static int respawnFrames;
    private static java.util.List<net.minecraft.core.BlockPos> savedExits;
    private static Pixels negativeControl;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void entryCaption(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (ROLE == null || "server".equals(ROLE) || RELOADED || finished || entryCaptionProven
                || client.level == null || !client.level.dimension().equals(Level.OVERWORLD)
                || !CityClientRuntime.entryCaptionVisible()) return;
        event.getGuiGraphics().flush();
        try (var frame = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int dark = 0, bright = 0, samples = 0;
            for (int y = frame.getHeight() / 4; y < frame.getHeight() * 3 / 4; y++) {
                for (int x = frame.getWidth() / 8; x < frame.getWidth() * 7 / 8; x++) {
                    int pixel = frame.getPixelRGBA(x, y);
                    int r = pixel & 255, g = (pixel >>> 8) & 255, b = (pixel >>> 16) & 255;
                    samples++;
                    if (r < 10 && g < 10 && b < 10) dark++;
                    if (r > 180 && g > 180 && b > 180) bright++;
                }
            }
            require(dark > samples * 0.97 && bright >= 25,
                    "pre-teleport frame has black backdrop and visible caption: dark=" + dark + " bright=" + bright);
            frame.writeToFile(RESULTS.resolve(ROLE + "-entry-caption.png"));
            Files.writeString(RESULTS.resolve(ROLE + "-entry-caption.pass"),
                    "PASS rendered caption on black before leaving overworld; dark=" + dark + " bright=" + bright + "\n");
            entryCaptionProven = true;
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve(ROLE + ".failed"), failure.toString()); }
            catch (Exception ignored) { }
            finished = true;
        }
    }

    // QA-only negative control removes just the white plane; particles and the open door remain.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeRender(RenderLevelStageEvent event) {
        if (hidePlane && event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            savedExits = CityClientRuntime.activeExits();
            CityClientRuntime.setExits(java.util.List.of());
            hiddenFrames++;
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterRender(RenderLevelStageEvent event) {
        var client = Minecraft.getInstance();
        if (returning && event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                && client.level != null && client.level.dimension().equals(Level.OVERWORLD) && client.screen == null)
            homeFrames++;
        if (respawnClicked && event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                && client.level != null && client.level.dimension().equals(Level.OVERWORLD) && client.screen == null)
            respawnFrames++;
        if (savedExits != null && event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            CityClientRuntime.setExits(savedExits);
            savedExits = null;
        }
    }

    @SubscribeEvent public static void chat(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("NIGHT_QA_")) { phase = text.substring(9); ticks = 0; event.setCanceled(true); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (ROLE == null || "server".equals(ROLE) || finished || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        try {
            require(restarting || !(client.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen),
                    "native client disconnected before scenario completion");
            if (client.player == null || client.level == null) return;
            ticks++;
            if (phase.equals("SLEEP") && ticks == 40) {
                require(client.level.dimension().equals(Level.OVERWORLD), "native sleep starts in overworld");
                client.setScreen(null);
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                client.mouseHandler.grabMouse();
                capture(client, "before-sleep");
                nativeInput("click", "3");
            }
            // Server-side 100-tick evidence is authoritative; a slow renderer may miss this frame.
            if (phase.equals("SLEEP") && ticks == 80 && client.player.isSleeping()) {
                capture(client, "sleeping");
            }
            if (phase.equals("READY") && ticks >= 80) {
                if (ticks == 80) {
                    require(EternalNightContent.isCity(client.level), "client actually loaded the city dimension");
                    require(RELOADED || entryCaptionProven, "native frame proves caption before city teleport");
                    client.setScreen(null);
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                    client.mouseHandler.grabMouse();
                    for (var state : EternalNightContent.NEON_SIGN.get().getStateDefinition().getPossibleStates()) {
                        var sprite = client.getBlockRenderer().getBlockModel(state).getParticleIcon();
                        require(!sprite.contents().name().getPath().contains("missingno"), "original neon model resolves all 80 states");
                    }
                    capture(client, "original-art");
                }
                // CI can drop a single /nightqa ready; keep ACK-ing until the server leaves READY.
                if (ticks % 20 == 0) client.player.connection.sendCommand("nightqa ready");
            }
            if (phase.equals("OPEN") && ticks >= 5 && ticks % 10 == 0) nativeInput("click", "3");
            if (phase.equals("WHITE") && ticks == 20) {
                require(!CityClientRuntime.activeExits().isEmpty(), "server sent active exit snapshot");
                hiddenFrames = 0;
                hidePlane = true;
            }
            if (phase.equals("WHITE") && ticks == 40) {
                require(hiddenFrames >= 5, "negative control actually rendered without the white plane");
                negativeControl = capture(client, "negative-no-plane");
                hidePlane = false;
            }
            if (phase.equals("WHITE") && ticks == 60) {
                Pixels after = capture(client, "shared-white-exit");
                require(after.square() >= 1024 && after.square() > negativeControl.square() + 900
                        && after.white() > negativeControl.white() + 1000,
                        "continuous white plane exceeds particles-only negative control: negative=" + negativeControl + ", positive=" + after);
                Files.writeString(RESULTS.resolve(ROLE + (RELOADED ? "-reload" : "") + "-white.pass"), "PASS original models and shared white plane with rendered negative control; negative=" + negativeControl + " positive=" + after + "\n");
            }
            if (phase.equals("WHITE") && ticks >= 60 && ticks % 20 == 0)
                client.player.connection.sendCommand("nightqa captured");
            if (phase.equals("RESTART") && !restarting) {
                restarting = true;
                Files.writeString(RESULTS.resolve(ROLE + "-restart-ready.pass"), "PASS native client ready for intentional server shutdown\n");
            }
            if (phase.equals("WALK") && !walking) { nativeInput("keydown", "w"); walking = true; }
            if (walking && client.level.dimension().equals(Level.OVERWORLD)) {
                nativeInput("keyup", "w"); walking = false;
                returning = true; homeFrames = 0; phase = "RETURN"; ticks = 0;
            }
            if (returning && homeFrames >= 10) {
                capture(client, "returned-home");
                Files.writeString(RESULTS.resolve(ROLE + "-return.pass"), "PASS native walking through shared exit returned client to overworld\n");
                returning = false;
                phase = "HOME"; ticks = 0;
                finished = !"owner".equals(ROLE);
            }
            if (phase.equals("DEATH_OPEN") && ticks == 60) {
                require(RELOADED && EternalNightContent.isCity(client.level), "death scenario runs in the loaded city");
                client.setScreen(null);
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                client.mouseHandler.grabMouse();
                nativeInput("click", "3");
            }
            if (phase.equals("DIE") && ticks == 40) client.player.connection.sendCommand("nightqa die");
            if (phase.equals("DIE") && !respawnClicked && client.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
                String respawn = net.minecraft.network.chat.Component.translatable("deathScreen.respawn").getString();
                for (var child : client.screen.children()) {
                    if (!(child instanceof net.minecraft.client.gui.components.AbstractWidget button)
                            || !button.active || !button.getMessage().getString().equals(respawn)) continue;
                    capture(client, "death-screen");
                    double scaleX = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
                    double scaleY = (double) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
                    int x = client.getWindow().getX() + (int) ((button.getX() + button.getWidth() / 2.0) * scaleX);
                    int y = client.getWindow().getY() + (int) ((button.getY() + button.getHeight() / 2.0) * scaleY);
                    System.out.println("NIGHT_NATIVE_RESPAWN_CLICK x=" + x + " y=" + y
                            + " windowOrigin=" + client.getWindow().getX() + "," + client.getWindow().getY());
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                    nativeInput("mousemove", "--sync", Integer.toString(x), Integer.toString(y));
                    nativeInput("click", "1");
                    respawnClicked = true; respawnFrames = 0;
                    break;
                }
            }
            if (respawnClicked && respawnFrames >= 10) {
                require(client.player.isAlive() && client.player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == 0,
                        "native respawn loads a living player without duplicated diamonds");
                capture(client, "death-respawn");
                Files.writeString(RESULTS.resolve("owner-death.pass"), "PASS real DeathScreen, native Respawn-button click and rendered living overworld player with no diamonds\n");
                respawnClicked = false; phase = "HOME"; ticks = 0;
            }
            if (phase.startsWith("SURVEY_READY:") && ticks >= 80) {
                if (ticks == 80) {
                    require(EternalNightContent.isCity(client.level), "survey loads the actual city");
                    client.setScreen(null);
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                    client.mouseHandler.grabMouse();
                    capture(client, "entrance-before-" + phase.substring(13));
                    nativeInput("click", "3");
                }
                if (ticks % 20 == 0) client.player.connection.sendCommand("nightqa survey-ready");
            }
            if (phase.startsWith("SURVEY_WALK:") && !surveyWalking) {
                nativeInput("keydown", "w"); surveyWalking = true;
            }
            if (phase.startsWith("SURVEY_WALK:") && ticks % 10 == 0
                    && client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
                var state = client.level.getBlockState(hit.getBlockPos());
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                    System.out.println("NIGHT_SURVEY_NATIVE_CLICK target=" + hit.getBlockPos() + " state=" + state);
                    nativeInput("click", "3");
                }
            }
            if (phase.startsWith("SURVEY_STOP:") && ticks == 1) {
                nativeInput("keyup", "w"); surveyWalking = false;
                capture(client, "entrance-after-" + phase.substring(12));
            }
            if (phase.startsWith("SURVEY_CAPTURE:") && ticks >= 60) {
                if (ticks == 60) capture(client, "natural-mobs-" + phase.substring(15));
                if (ticks % 20 == 0) client.player.connection.sendCommand("nightqa survey-captured");
            }
            if (phase.equals("SURVEY_DONE")) {
                Files.writeString(RESULTS.resolve("owner-survey.complete"), "COLLECTED native generated-building and natural-entity screenshots; see server observations for failures\n");
                finished = true;
            }
            if (walking && ticks > 300) throw new AssertionError("Native walk did not cross the exit");
            if (returning && ticks > 300) throw new AssertionError("Overworld did not render after returning home");
            // Observer stays in READY until the owner finishes OPEN and WHITE is broadcast.
            int limit = phase.equals("READY") ? 8000 : 2400;
            if (!phase.isEmpty() && ticks > limit) throw new AssertionError("Native phase timed out: " + phase);
        } catch (Throwable error) {
            hidePlane = false;
            error.printStackTrace();
            try { nativeInput("keyup", "w"); capture(client, "failed"); Files.writeString(RESULTS.resolve(ROLE + ".failed"), error.toString()); }
            catch (Exception ignored) { }
            finished = true;
        }
    }
    private record Pixels(int white, int square) { }

    private static Pixels capture(Minecraft client, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int white = 0, largest = 0;
            int[] previous = new int[image.getWidth() + 1];
            for (int y = image.getHeight() / 6; y < image.getHeight() * 5 / 6; y++) {
                int[] row = new int[previous.length];
                // The viewing fixture centers the exit. Keep toast UI outside the
                // sample: its white icon can dominate the particles-only control.
                for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                    int color = image.getPixelRGBA(x, y);
                    if ((color & 255) > 220 && ((color >>> 8) & 255) > 220 && ((color >>> 16) & 255) > 220) {
                        white++;
                        row[x + 1] = 1 + Math.min(row[x], Math.min(previous[x], previous[x + 1]));
                        largest = Math.max(largest, row[x + 1]);
                    }
                }
                previous = row;
            }
            image.writeToFile(RESULTS.resolve(ROLE + (RELOADED ? "-reload" : "") + "-" + name + ".png"));
            return new Pixels(white, largest * largest);
        }
    }
    private static void nativeInput(String... keys) throws Exception {
        var args = new java.util.ArrayList<String>(); args.add("xdotool"); args.addAll(java.util.List.of(keys));
        require(new ProcessBuilder(args).inheritIO().start().waitFor() == 0, "native input succeeds");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
