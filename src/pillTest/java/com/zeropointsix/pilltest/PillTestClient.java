package com.zeropointsix.pilltest;

import com.zeropointsix.eraser.pill.PillNetwork;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "pill_qa", value = Dist.CLIENT)
public final class PillTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("pill.qa.results"));
    private static final String ROLE = System.getProperty("pill.qa.role");
    private static String phase = "";
    private static int ticks;
    private static boolean failed;

    @SubscribeEvent
    public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("PILL_QA:")) {
            phase = text.substring(8);
            ticks = 0;
            event.setCanceled(true);
        }
    }

    private static void input(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("xdotool");
        command.addAll(java.util.List.of(args));
        if (new ProcessBuilder(command).start().waitFor() != 0) throw new AssertionError("native input failed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (ticks == 1) {
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
                if (ROLE.equals("user")) input("mouseup", "3");
            }
            if (ROLE.equals("observer")) {
                require(PillNetwork.clientStatus.activeTicks() == 0 && PillNetwork.clientStatus.withdrawalTicks() == 0,
                        "private timer never reaches observer client");
            } else {
                if (phase.equals("cancel") && ticks == 30) input("mousedown", "3");
                if (phase.equals("cancel") && ticks == 38) input("mouseup", "3");
                if ((phase.equals("eat") || phase.equals("redose")) && ticks == 10) input("key", "1");
                if (phase.equals("milk") && ticks == 10) input("key", "2");
                if ((phase.equals("eat") || phase.equals("milk") || phase.equals("redose")) && ticks == 20) input("mousedown", "3");
                if (phase.equals("active") && ticks == 20) require(PillNetwork.clientStatus.activeTicks() > 0, "owner receives active HUD packet");
                if (phase.equals("milk_wait") && ticks == 20) require(PillNetwork.clientStatus.withdrawalTicks() > 0, "owner receives withdrawal HUD packet");
                if (phase.equals("saved") && ticks == 20) {
                    require(mc.player.getInventory().getItem(0).is(ModItems.EMPTY_PILL_PACK.get()), "empty model item synchronized");
                    require(PillNetwork.clientStatus.packDamage() == 12, "HUD changes to empty pack");
                }
            }
            if (ticks == 20 && (phase.equals("active") || phase.equals("milk_wait") || phase.equals("saved") || phase.equals("restarted"))) {
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    screenshot.writeToFile(RESULTS.resolve(ROLE + "-" + phase + ".png"));
                }
                if (phase.equals("saved") || phase.equals("restarted")) {
                    Files.writeString(RESULTS.resolve(ROLE + "-" + phase + ".pass"), "Real Forge client packet, inventory and HUD checks passed.\n");
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve(ROLE + ".failed"), failure.toString()); } catch (Exception ignored) { }
            failed = true;
        }
    }
}
