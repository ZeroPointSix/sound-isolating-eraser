package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.YujiMonster;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa", value = Dist.CLIENT)
public final class YujiBoundaryClient {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static String command = "ready";
    private static int ticks;
    private static boolean failed;
    private static void pass(String phase, String evidence) throws Exception {
        String name = Minecraft.getInstance().getUser().getName();
        Files.writeString(RESULTS.resolve("client-" + phase + "-" + name + ".pass"), evidence + "\n");
        System.out.println("YUJI_BOUNDARY_CLIENT " + phase + " " + name + " " + evidence);
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("boundary-client-" + Minecraft.getInstance().getUser().getName() + ".failed"), error.toString()); }
        catch (Exception ignored) { }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        if (!Boolean.getBoolean("yuji.qa.boundaries")) return;
        String text = event.getMessage().getString();
        if (!text.startsWith("YUJI_BOUNDARY:")) return;
        command = text.substring("YUJI_BOUNDARY:".length()); ticks = 0; event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("yuji.qa.boundaries") || failed || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (ticks == 1) { mc.setScreen(null); mc.mouseHandler.grabMouse(); }
            if (command.equals("ready") && ticks == 60) { pass("ready", "real TCP-connected client UUID=" + mc.player.getUUID()); command = ""; }
            else if (command.startsWith("attack:") && ticks == 10) {
                int id = Integer.parseInt(command.substring(7)); var entity = mc.level.getEntity(id);
                if (entity == null) throw new AssertionError("attack entity did not reach actual client: " + id);
                mc.gameMode.attack(mc.player, entity); mc.player.swing(InteractionHand.MAIN_HAND);
                System.out.println("YUJI_BOUNDARY_CLIENT sent attack packet entity=" + id + " player=" + mc.player.getUUID()); command = "";
            } else if (command.startsWith("layers:") && ticks == 10) {
                String[] parts = command.split(":");
                var entity = mc.level.getEntity(Integer.parseInt(parts[2]));
                int expected = Integer.parseInt(parts[3]);
                if (!(entity instanceof YujiMonster monster) || monster.layers() != expected)
                    throw new AssertionError("peel result did not synchronize to actual client: " + command);
                pass(parts[1], "TCP-synchronized entity=" + entity.getUUID() + "; layers=" + expected); command = "";
            } else if (command.startsWith("health:") && ticks == 10) {
                String[] parts = command.split(":");
                float expected = Float.parseFloat(parts[2]);
                if (Math.abs(mc.player.getHealth() - expected) > .001)
                    throw new AssertionError("actual client health=" + mc.player.getHealth() + "; expected=" + expected);
                pass(parts[1], "TCP-synchronized player health=" + mc.player.getHealth()); command = "";
            } else if (command.equals("done")) { pass("boundaries", "real-client boundary sequence completed"); command = ""; }
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("yuji.qa.boundaries") || failed || event.phase != TickEvent.Phase.END || !command.startsWith("shot:") || ticks < 20) return;
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.level == null || mc.screen != null) return;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var colors = new HashSet<Integer>();
            for (int x = 0; x < image.getWidth(); x += 4) for (int y = 0; y < image.getHeight(); y += 4) colors.add(image.getPixelRGBA(x, y));
            if (colors.size() < 100) throw new AssertionError("empty real-client framebuffer");
            String phase = command.substring(5), name = mc.getUser().getName();
            image.writeToFile(RESULTS.resolve(phase + "-" + name + ".png"));
            pass(phase, "actual framebuffer; colors=" + colors.size() + ", player=" + mc.player.getUUID()); command = "";
        } catch (Throwable error) { fail(error); }
    }
}
