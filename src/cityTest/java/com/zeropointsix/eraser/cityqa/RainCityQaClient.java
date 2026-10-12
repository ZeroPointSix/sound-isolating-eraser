package com.zeropointsix.eraser.cityqa;

import com.google.gson.JsonObject;
import com.zeropointsix.eraser.yuji.city.RecliningBuddha;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.event.GeoRenderEvent;

@Mod.EventBusSubscriber(modid = RainCityQa.ID, value = Dist.CLIENT)
public final class RainCityQaClient {
    private static boolean connecting, failed, capturePending;
    private static String phase = "", dimension = "", lastScreen = "";
    private static int ticks, phaseTick, draws, phaseDraws, frames, lastDrawFrame = -1, lastWorldFrame = -1;
    private static double renderedBuddhaY = -1000;
    private static float fogFar = -1;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!QaFiles.ENABLED || failed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !phase.isEmpty() && mc.player.getAbilities().mayfly) {
            mc.player.getAbilities().flying = true;
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        try {
            String screen = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
            if (!screen.equals(lastScreen)) {
                lastScreen = screen;
                org.slf4j.LoggerFactory.getLogger("RainCityQA").info("City QA client screen: {}", screen);
                QaFiles.write("client-state.json", Map.of("screen", screen, "connecting", connecting, "tick", ticks));
            }
            if (!connecting && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) {
                connecting = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.fov().set(110);
                String address = "127.0.0.1:" + Integer.getInteger("yuji.city.qa.port", 25591);
                org.slf4j.LoggerFactory.getLogger("RainCityQA").info("City QA client connecting to {} from {}", address, screen);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                        new ServerData("Rain City QA", address, false), false);
            }
            if (connecting && mc.screen instanceof DisconnectedScreen)
                throw new IllegalStateException("QA client disconnected from its dedicated server");
            JsonObject request = QaFiles.read("phase.json");
            if (request == null) return;
            String next = request.get("phase").getAsString();
            if (!phase.equals(next)) {
                phase = next;
                dimension = request.get("dimension").getAsString();
                phaseTick = ticks;
                phaseDraws = draws;
                capturePending = false;
                fogFar = -1;
            }
            QaFiles.require(ticks - phaseTick < 2400 || Files.exists(QaFiles.directory().resolve(phase + ".pass.json")),
                    "Client timed out rendering phase " + phase);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public static void renderedBuddha(GeoRenderEvent.Entity.Post event) {
        if (!QaFiles.ENABLED || !(event.getEntity() instanceof RecliningBuddha buddha)) return;
        draws++;
        renderedBuddhaY = buddha.getY();
        lastDrawFrame = frames;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        if (QaFiles.ENABLED) fogFar = event.getFarPlaneDistance();
    }

    @SubscribeEvent
    public static void worldFrame(RenderLevelStageEvent event) {
        if (QaFiles.ENABLED && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) lastWorldFrame = frames;
    }

    @SubscribeEvent
    public static void screenshot(TickEvent.RenderTickEvent event) {
        if (!QaFiles.ENABLED || failed) return;
        if (event.phase == TickEvent.Phase.START) { frames++; return; }
        if (capturePending || phase.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.screen != null || lastWorldFrame != frames) return;
            if (!mc.level.dimension().location().toString().equals(dimension)) return;
            if (ticks - phaseTick < (phase.equals("buddha_fall") ? 4 : 60)) return;
            if (phase.equals("grey") && Math.abs(fogFar - 64) > .5) return;
            if ((phase.equals("black") || phase.equals("beam")) && Math.abs(fogFar - 32) > .5) return;
            if (phase.startsWith("buddha") && (draws <= phaseDraws || lastDrawFrame != frames)) return;
            if (phase.equals("buddha_fall")) {
                QaFiles.require(renderedBuddhaY > 35, "Client missed the real falling-Buddha capture window");
                if (renderedBuddhaY >= 120) return;
            }
            String capturedPhase = phase;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("phase", phase);
            metadata.put("dimension", dimension);
            metadata.put("fogFar", fogFar);
            metadata.put("phaseBuddhaRenders", draws - phaseDraws);
            metadata.put("buddhaDrawnInCapturedFrame", lastDrawFrame == frames);
            metadata.put("renderedBuddhaY", renderedBuddhaY);
            metadata.put("clientTick", ticks);
            metadata.put("x", mc.player.getX());
            metadata.put("y", mc.player.getY());
            metadata.put("z", mc.player.getZ());
            metadata.put("yaw", mc.player.getYRot());
            metadata.put("pitch", mc.player.getXRot());
            capturePending = true;
            Screenshot.grab(QaFiles.directory().toFile(), capturedPhase + ".png", mc.getMainRenderTarget(), message -> {
                try {
                    QaFiles.require(Files.isRegularFile(QaFiles.directory().resolve("screenshots/" + capturedPhase + ".png")),
                            "Minecraft did not write screenshot: " + message.getString());
                    QaFiles.write(capturedPhase + ".captured.json", metadata);
                } catch (Throwable failure) {
                    fail(failure);
                }
            });
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void fail(Throwable failure) {
        failed = true;
        QaFiles.failure("client", phase, failure);
    }

    private RainCityQaClient() { }
}
