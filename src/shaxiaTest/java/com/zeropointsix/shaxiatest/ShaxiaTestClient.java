package com.zeropointsix.shaxiatest;

import com.zeropointsix.eraser.shaxia.ShaxiaEnchantments;
import com.zeropointsix.eraser.shaxia.ShaxiaStacks;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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
    private static boolean moved;
    private static boolean tooltipTextObserved;
    private static boolean tooltipDrawnInFrame;
    private static boolean captured;
    private static int tooltipX, tooltipY, tooltipWidth, tooltipHeight;
    private static NativeImage negativeFrame;

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("SHAXIA_QA:")) {
            phase = text.substring(10);
            ticks = 0;
            moved = false;
            tooltipTextObserved = false;
            tooltipDrawnInFrame = false;
            captured = false;
            closeNegativeFrame();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void tooltipText(RenderTooltipEvent.GatherComponents event) {
        if (!ShaxiaStacks.active(event.getItemStack())) return;
        var lines = event.getTooltipElements().stream().flatMap(element -> element.left().stream())
                .map(text -> text.getString()).toList();
        tooltipTextObserved = lines.contains("针对畸界怪物具有特殊攻击效果") && lines.contains("畸界特攻");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void frameStart(ScreenEvent.Render.Pre event) {
        tooltipTextObserved = false;
        tooltipDrawnInFrame = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void negativeTooltipControl(RenderTooltipEvent.Pre event) {
        if (savedPhase() && ticks >= 45 && !captured && negativeFrame == null
                && ShaxiaStacks.active(event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent public static void tooltipDraw(RenderTooltipEvent.Color event) {
        if (!tooltipTextObserved || !ShaxiaStacks.active(event.getItemStack())) return;
        tooltipDrawnInFrame = true;
        tooltipX = event.getX();
        tooltipY = event.getY();
        tooltipWidth = event.getComponents().stream().mapToInt(c -> c.getWidth(event.getFont())).max().orElse(0);
        tooltipHeight = event.getComponents().stream().mapToInt(c -> c.getHeight()).sum() + 2;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frameComplete(ScreenEvent.Render.Post event) {
        if (failed || captured || !savedPhase() || ticks < 45) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(event.getScreen() == mc.screen && mc.screen instanceof AbstractContainerScreen,
                    "native inventory screen rendered");
            Slot hovered = hoveredSlot((AbstractContainerScreen<?>) mc.screen);
            require(hovered != null && hovered.getItem() == mc.player.getMainHandItem(),
                    "cursor actually hovers the knife in the captured frame");
            require(tooltipTextObserved, "exact Chinese tooltip gathered in the captured frame");
            // ClientTick END can sample another frame. Flush this screen's completed GUI before capture.
            event.getGuiGraphics().flush();
            if (negativeFrame == null) {
                require(!tooltipDrawnInFrame, "cancelled tooltip must not reach the Color render event");
                require(!Files.exists(RESULTS.resolve(ROLE + "-" + phase + ".pass")),
                        "negative control cannot produce a passing result");
                negativeFrame = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                negativeFrame.writeToFile(RESULTS.resolve(ROLE + "-" + phase + "-without-tooltip.png"));
                return;
            }
            require(tooltipDrawnInFrame, "uncancelled tooltip drawn in this exact screen frame");
            try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                screenshot.writeToFile(RESULTS.resolve(ROLE + "-" + phase + ".png"));
                var colors = new HashSet<Integer>();
                for (int x = 0; x < screenshot.getWidth(); x += 8) {
                    for (int y = 0; y < screenshot.getHeight(); y += 8) colors.add(screenshot.getPixelRGBA(x, y));
                }
                require(colors.size() > 100, "real client framebuffer is not blank");
                int scale = (int) mc.getWindow().getGuiScale();
                int[] visible = tooltipPixels(screenshot, scale);
                int[] absent = tooltipPixels(negativeFrame, scale);
                int minimum = 20 * scale * scale;
                require(visible[0] >= minimum && visible[1] >= minimum && visible[2] >= minimum,
                        "captured tooltip contains cyan title, gray Chinese text and green attributes");
                require(absent[0] < minimum && absent[2] < minimum,
                        "negative control lacks the tooltip text colors in the same region");
                Files.writeString(RESULTS.resolve(ROLE + "-" + phase + ".pass"),
                        "Real Forge creative tab, independent inventory, exact Chinese tooltip, no-glint and texture passed.\n"
                        + "Same-frame flushed screenshot and cancelled-tooltip negative control passed; region="
                        + tooltipX + "," + tooltipY + "," + tooltipWidth + "," + tooltipHeight
                        + "; textPixels=" + java.util.Arrays.toString(visible)
                        + "; negativePixels=" + java.util.Arrays.toString(absent) + "\n");
            }
            captured = true;
            closeNegativeFrame();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static int[] tooltipPixels(NativeImage screenshot, int scale) {
        int left = tooltipX * scale, top = tooltipY * scale;
        int right = (tooltipX + tooltipWidth) * scale, bottom = (tooltipY + tooltipHeight) * scale;
        require(left >= 0 && top >= 0 && right <= screenshot.getWidth() && bottom <= screenshot.getHeight(),
                "complete tooltip text bounds are inside the framebuffer");
        int[] colors = new int[3];
        for (int x = left; x < right; x++) for (int y = top; y < bottom; y++) {
            int pixel = screenshot.getPixelRGBA(x, y);
            int red = pixel & 255, green = (pixel >>> 8) & 255, blue = (pixel >>> 16) & 255;
            if (red < 130 && green > 170 && blue > 170) colors[0]++;
            if (red >= 120 && Math.abs(red - green) <= 16 && Math.abs(green - blue) <= 16) colors[1]++;
            if (red < 100 && green > 150 && blue < 100) colors[2]++;
        }
        return colors;
    }

    private static boolean savedPhase() {
        return phase.equals("saved") || phase.equals("restarted");
    }

    private static void closeNegativeFrame() {
        if (negativeFrame != null) negativeFrame.close();
        negativeFrame = null;
    }

    private static void fail(Throwable failure) {
        failure.printStackTrace();
        closeNegativeFrame();
        try { Files.writeString(RESULTS.resolve(ROLE + ".failed"), failure.toString()); } catch (Exception ignored) { }
        failed = true;
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
            boolean saved = savedPhase();
            require(!saved || captured || ticks < 100, "same-frame tooltip capture timed out");
            if (!saved && ROLE.equals("user") && ticks == 40) input("click", "1");
            if (saved && ticks == 20) {
                CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), false, mc.level.registryAccess());
                var creativeKnives = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.TOOLS_AND_UTILITIES)
                        .getDisplayItems().stream().filter(ShaxiaStacks::isKnife).toList();
                require(creativeKnives.size() == 1 && ShaxiaStacks.active(creativeKnives.get(0)),
                        "real creative tab contains exactly one factory-enchanted knife");
                var stack = mc.player.getMainHandItem();
                require(ShaxiaStacks.active(stack) && !stack.hasFoil(), "innate enchantment and glint synchronized");
                require(stack.getDamageValue() == (ROLE.equals("user") ? 5 : 0), "independent durability synchronized");
                var tooltip = stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL).stream().map(c -> c.getString()).toList();
                require(tooltip.contains("针对畸界怪物具有特殊攻击效果"), "exact Chinese tooltip loaded");
                require(ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get().getFullname(1).getString().equals("畸界特攻"),
                        "innate name has no level suffix");
                String sprite = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0).getParticleIcon().contents().name().toString();
                require(sprite.equals("sound_isolating_eraser:item/shaxiadao"), "item model resolves the Shaxiadao texture");
                input("key", "e");
            }
            if (saved && ticks >= 28 && ticks < 45 && !moved && mc.screen instanceof AbstractContainerScreen<?> screen) {
                ItemStack held = mc.player.getMainHandItem();
                Slot slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getItem() == held).findFirst()
                        .orElseThrow(() -> new AssertionError("knife stack is not shown in the inventory menu"));
                int scale = (int) mc.getWindow().getGuiScale();
                input("mousemove", Integer.toString(mc.getWindow().getX() + (screen.getGuiLeft() + slot.x + 8) * scale),
                        Integer.toString(mc.getWindow().getY() + (screen.getGuiTop() + slot.y + 8) * scale));
                moved = true;
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static Slot hoveredSlot(AbstractContainerScreen<?> screen) throws Exception {
        var field = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
        field.setAccessible(true);
        return (Slot) field.get(screen);
    }
}
