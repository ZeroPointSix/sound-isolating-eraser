package com.zeropointsix.flareguntest;

import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.client.FlareGunRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "flare_gun_qa", value = Dist.CLIENT)
public final class FlareGunTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("flare.gun.qa.results"));
    private static final Set<String> animated = new HashSet<>();
    private static String phase = "";
    private static int ticks, lastFrame = -1, reports;
    private static boolean failed;

    private static void fail(Throwable failure) {
        failed = true;
        failure.printStackTrace();
        try { Files.writeString(RESULTS.resolve("client.failed"), failure.toString()); } catch (Exception ignored) { }
    }

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (!message.startsWith("FLARE_GUN_QA:")) return;
        event.setCanceled(true);
        phase = message.substring("FLARE_GUN_QA:".length());
        ticks = 0;
        lastFrame = -1;
        var mc = Minecraft.getInstance();
        mc.options.setCameraType(phase.startsWith("third") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        try {
            if (phase.equals("ready")) {
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                mc.getConnection().sendChat("flare_gun_qa_ready");
                Files.writeString(RESULTS.resolve("client.ready"), "real client connected\n");
            }
            if (phase.equals("done")) {
                if (reports != 2) throw new AssertionError("Expected 2 vanilla reports, received " + reports);
                for (String expected : new String[]{"first-load", "first-fire", "third-load", "third-fire"})
                    if (!animated.contains(expected)) throw new AssertionError("Missing rendered bone motion: " + expected);
                Files.writeString(RESULTS.resolve("client.pass"), "Rendered first/third person load and fire bone motion; 2 vanilla reports, no extra reports on rejected shots.\n");
            }
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        if (event.getSound() != null && event.getSound().getLocation().toString().equals("minecraft:entity.firework_rocket.blast")) reports++;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        ticks++;
        if ((phase.endsWith("fire") && (ticks == 10 || ticks == 11)) || (phase.equals("empty") && ticks == 10))
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || ticks == lastFrame || ticks > 55
                || !(phase.endsWith("load") || phase.endsWith("fire") || phase.equals("empty"))) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            lastFrame = ticks;
            var renderer = (FlareGunRenderer) IClientItemExtensions.of(FlareContent.GUN.get()).getCustomRenderer();
            var root = renderer.getGeoModel().getBone("root").orElseThrow();
            var barrel = renderer.getGeoModel().getBone("barrel").orElseThrow();
            float angle = phase.endsWith("load") ? barrel.getRotX() : root.getRotX();
            if (Math.abs(angle) > .08F) animated.add(phase);
            Files.writeString(RESULTS.resolve("poses.csv"), phase + "," + ticks + "," + root.getRotX() + "," + barrel.getRotX() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (ticks <= 28 || ticks == 45) {
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(RESULTS.resolve(String.format("%s-%03d.png", phase, ticks)));
                }
            }
        } catch (Throwable failure) { fail(failure); }
    }
}
