package com.zeropointsix.gravitytest;

import com.zeropointsix.eraser.registry.ModItemTags;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeX11;

/** Observes native inventory hover rendering, never synthesizes a tooltip. */
@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT)
public final class ItemCategoryClientProbe {
    private static final String[] PHASES = {"baseline", "daoju", "both", "restored"};
    private static final ItemStack STICK = Items.STICK.getDefaultInstance();
    private static int phase;
    private static int sample;
    private static String originalLanguage;
    private static CompletableFuture<Void> languageReload;
    private static InventoryScreen screen;
    private static Item expectedItem;
    private static Rarity expectedRarity;
    private static String expectedLabel;
    private static int expectedColor;
    private static int renderedTooltips;
    private static long readyFrame;
    private static long hoverStarted;
    private static boolean mouseMoved;
    private static String observedText;
    private static Throwable renderFailure;
    private static boolean finished;

    static boolean tick(Minecraft mc, long frames, Path results, String role) throws Exception {
        if (!Boolean.getBoolean("qiwu.qa") || finished) return true;
        if (renderFailure != null) throw new AssertionError("Native tooltip failed", renderFailure);
        if (originalLanguage == null) originalLanguage = mc.getLanguageManager().getSelected();
        if (languageReload != null) {
            if (!languageReload.isDone() || mc.getOverlay() != null) return false;
            languageReload.join();
            languageReload = null;
        }
        if (phase == 4) {
            if (!Files.exists(results.resolve("server-category.pass"))) return false;
            if (changeLanguage(mc, originalLanguage)) return false;
            Files.writeString(results.resolve(role + "-category.pass"),
                    "14 native hovers; en_us/zh_cn; synced daoju/both/restored tags; original language restored\n");
            finished = true;
            return true;
        }
        if (!Files.exists(results.resolve("server-category-" + PHASES[phase] + ".pass"))) return false;
        if (mc.gameMode.hasInfiniteItems()) return false;
        if (phase > 0) {
            // Retain the same stack across all reloads to catch stale membership caches.
            if (STICK.is(ModItemTags.QIWU) != (phase == 2)
                    || STICK.is(ModItemTags.DAOJU) != (phase == 1 || phase == 2)) return false;
        }
        Item[] items = phase == 0 ? new Item[]{item("super_fertilizer_bag"),
                item("gravity_jade_pendant"), item("shaxiadao"),
                item("sound_isolating_eraser")} : new Item[]{Items.STICK};
        if (sample == items.length * 2) {
            expectedItem = null;
            mc.setScreen(null);
            Files.writeString(results.resolve(role + "-category-" + PHASES[phase] + ".pass"),
                    "native hover and both locales passed: " + PHASES[phase] + "\n");
            phase++;
            sample = 0;
            return false;
        }
        String language = sample < items.length ? "en_us" : "zh_cn";
        if (changeLanguage(mc, language)) return false;
        if (screen == null) {
            expectedItem = items[sample % items.length];
            if (mc.player.getInventory().items.stream().noneMatch(stack -> stack.is(expectedItem))) return false;
            expectedRarity = expectedItem.getDefaultInstance().getRarity();
            boolean qiwu = phase == 0 || phase == 2;
            expectedLabel = phase == 3 ? "" : (qiwu
                    ? (language.equals("zh_cn") ? "\u5947\u7269" : "Curio")
                    : (language.equals("zh_cn") ? "\u9053\u5177" : "Tool"));
            expectedColor = qiwu ? 0xFFAA00 : 0x8FAAC4;
            screen = new InventoryScreen(mc.player);
            mc.setScreen(screen);
            renderedTooltips = 0;
            observedText = null;
            mouseMoved = false;
            hoverStarted = System.nanoTime();
            readyFrame = frames + 2;
            return false;
        }
        if (System.nanoTime() - hoverStarted > 45_000_000_000L) {
            throw new AssertionError("Native hover timeout: phase=" + PHASES[phase] + " sample=" + sample
                    + " item=" + ForgeRegistries.ITEMS.getKey(expectedItem) + " rendered=" + renderedTooltips
                    + " mouse=" + mc.mouseHandler.xpos() + "," + mc.mouseHandler.ypos()
                    + " hovered=" + (screen.getSlotUnderMouse() == null ? "none" : screen.getSlotUnderMouse().getItem()));
        }
        if (!mouseMoved && frames >= readyFrame) {
            moveMouse(mc);
            mouseMoved = true;
            readyFrame = frames + 4;
            return false;
        }
        if (frames < readyFrame || renderedTooltips < 3) return false;
        String id = ForgeRegistries.ITEMS.getKey(expectedItem).getPath();
        String name = role + "-category-" + PHASES[phase] + "-" + language + "-" + id;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(results.resolve(name + ".png"));
        }
        Files.writeString(results.resolve(name + ".pass"), observedText + "\n");
        System.out.println("ITEM_CATEGORY_NATIVE_HOVER " + name + " " + observedText);
        expectedItem = null;
        mc.setScreen(null);
        screen = null;
        sample++;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tooltip(RenderTooltipEvent.GatherComponents event) {
        if (expectedItem == null || screen != Minecraft.getInstance().screen
                || !event.getItemStack().is(expectedItem)) return;
        try {
            List<FormattedText> lines = event.getTooltipElements().stream()
                    .flatMap(element -> element.left().stream()).toList();
            require(event.getItemStack().getRarity() == expectedRarity, "hover preserves item rarity");
            require(!lines.isEmpty() && lines.get(0) instanceof Component, "native item name exists");
            var nameColor = ((Component) lines.get(0)).getStyle().getColor();
            require(nameColor != null && nameColor.getValue() == expectedRarity.color.getColor(),
                    "native item name retains its original rarity color");
            long categories = lines.stream().map(FormattedText::getString)
                    .filter(text -> List.of("Curio", "Tool", "\u5947\u7269", "\u9053\u5177").contains(text)).count();
            if (expectedLabel.isEmpty()) {
                require(categories == 0, "removed tags must remove the native label");
            } else {
                require(categories == 1 && lines.size() > 1 && lines.get(1).getString().equals(expectedLabel),
                        "one translated category directly below the item name");
                require(lines.get(1) instanceof Component, "category retains its component style");
                var color = ((Component) lines.get(1)).getStyle().getColor();
                require(color != null && color.getValue() == expectedColor, "category color matches specification");
            }
            observedText = lines.stream().map(FormattedText::getString).toList().toString();
        } catch (Throwable failure) {
            renderFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rendered(RenderTooltipEvent.Color event) {
        if (expectedItem != null && screen == Minecraft.getInstance().screen
                && event.getItemStack().is(expectedItem) && observedText != null) renderedTooltips++;
    }

    private static boolean changeLanguage(Minecraft mc, String language) {
        if (mc.getLanguageManager().getSelected().equals(language)) return false;
        mc.getLanguageManager().setSelected(language);
        mc.options.languageCode = language;
        languageReload = mc.reloadResourcePacks();
        return true;
    }

    private static void moveMouse(Minecraft mc) throws Exception {
        var slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getItem().is(expectedItem))
                .findFirst().orElseThrow(() -> new AssertionError("Item missing from server-supplied inventory"));
        var window = mc.getWindow();
        GLFW.glfwFocusWindow(window.getWindow());
        int x = (screen.getGuiLeft() + slot.x + 8) * window.getScreenWidth() / window.getGuiScaledWidth();
        int y = (screen.getGuiTop() + slot.y + 8) * window.getScreenHeight() / window.getGuiScaledHeight();
        // Let screen-opening cursor resets settle, then deliver a real X11 motion event.
        Process input = new ProcessBuilder("xdotool", "mousemove", "--sync", "--window",
                Long.toString(GLFWNativeX11.glfwGetX11Window(window.getWindow())),
                Integer.toString(x), Integer.toString(y)).inheritIO().start();
        require(input.waitFor() == 0, "native mouse motion succeeds");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("sound_isolating_eraser", id));
    }

    private ItemCategoryClientProbe() { }
}
