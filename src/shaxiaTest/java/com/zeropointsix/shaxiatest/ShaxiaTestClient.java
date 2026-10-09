package com.zeropointsix.shaxiatest;

import com.zeropointsix.eraser.shaxia.ShaxiaEnchantments;
import com.zeropointsix.eraser.shaxia.ShaxiaStacks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "shaxia_qa", value = Dist.CLIENT)
public final class ShaxiaTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("shaxia.qa.results"));
    private static final String ROLE = System.getProperty("shaxia.qa.role");
    private static String phase = "";
    private static int ticks;
    private static boolean failed;

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("SHAXIA_QA:")) {
            phase = text.substring(10);
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

    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (ticks == 1) {
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
            }
            boolean saved = phase.equals("saved") || phase.equals("restarted");
            if (!saved && ROLE.equals("user") && ticks == 40) input("click", "1");
            if (saved && ticks == 20) {
                var stack = mc.player.getMainHandItem();
                require(ShaxiaStacks.active(stack) && !stack.hasFoil(), "innate enchantment and glint synchronized");
                require(stack.getDamageValue() == (ROLE.equals("user") ? 5 : 0), "independent durability synchronized");
                var tooltip = stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL).stream().map(c -> c.getString()).toList();
                require(tooltip.contains("针对畸界怪物具有特殊攻击效果"), "exact Chinese tooltip loaded");
                require(ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get().getFullname(1).getString().equals("畸界特攻"),
                        "innate name has no level suffix");
                String sprite = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0).getParticleIcon().contents().name().toString();
                require(!sprite.contains("missing"), "temporary item model resolves a real texture");
                input("key", "e");
            }
            if (saved && ticks == 28) {
                int scale = (int) mc.getWindow().getGuiScale();
                int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
                int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
                input("mousemove", Integer.toString((left + 16) * scale), Integer.toString((top + 150) * scale));
            }
            if (saved && ticks == 45) {
                require(mc.screen != null, "native inventory screen opened");
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    var colors = new HashSet<Integer>();
                    for (int x = 0; x < screenshot.getWidth(); x += 8) {
                        for (int y = 0; y < screenshot.getHeight(); y += 8) colors.add(screenshot.getPixelRGBA(x, y));
                    }
                    require(colors.size() > 100, "real client framebuffer is not blank");
                    screenshot.writeToFile(RESULTS.resolve(ROLE + "-" + phase + ".png"));
                }
                Files.writeString(RESULTS.resolve(ROLE + "-" + phase + ".pass"),
                        "Real Forge client inventory, Chinese tooltip, no-glint, model and framebuffer checks passed.\n");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve(ROLE + ".failed"), failure.toString()); } catch (Exception ignored) { }
            failed = true;
        }
    }
}
