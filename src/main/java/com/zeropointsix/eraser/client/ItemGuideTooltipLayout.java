package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.item.ItemGuideCatalog;
import com.zeropointsix.eraser.item.ItemGuideLayout;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Retains vanilla fonts, styles, components and rendering, including the outer dark border. */
@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class ItemGuideTooltipLayout {
    private static ItemStack gatheredStack = ItemStack.EMPTY;
    private static List<FormattedText> gatheredLines = List.of();
    private static boolean reflowing;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        gatheredStack = event.getItemStack();
        gatheredLines = ItemGuideCatalog.find(gatheredStack) != null
                && event.getTooltipElements().stream().allMatch(element -> element.left().isPresent())
                ? event.getTooltipElements().stream().map(element -> element.left().orElseThrow()).toList()
                : List.of();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void position(RenderTooltipEvent.Pre event) {
        if (ItemGuideCatalog.find(event.getItemStack()) == null
                || event.getTooltipPositioner() != DefaultTooltipPositioner.INSTANCE) return;
        int width = event.getComponents().stream().mapToInt(c -> c.getWidth(event.getFont())).max().orElse(0);
        int height = event.getComponents().stream().mapToInt(c -> c.getHeight()).sum()
                + (event.getComponents().size() == 1 ? -2 : 0);
        if (!reflowing && !ItemGuideLayout.fits(width, height, event.getScreenWidth(), event.getScreenHeight())
                && gatheredStack == event.getItemStack() && !gatheredLines.isEmpty()) {
            // Mouse-side wrapping can make a tall tooltip unnecessarily narrow. Reflow the
            // original styled text across the viewport before asking vanilla to render it again.
            var lines = gatheredLines.stream().flatMap(line -> event.getFont()
                    .split(line, Math.max(1, event.getScreenWidth() - 2 * ItemGuideLayout.PADDING)).stream()).toList();
            event.setCanceled(true);
            reflowing = true;
            try {
                event.getGraphics().renderTooltip(event.getFont(), lines, event.getX(), event.getY());
            } finally {
                reflowing = false;
            }
            return;
        }
        var original = event.getTooltipPositioner().positionTooltip(event.getScreenWidth(), event.getScreenHeight(),
                event.getX(), event.getY(), width, height);
        int x = ItemGuideLayout.clamp(original.x(), width, event.getScreenWidth());
        int y = ItemGuideLayout.clamp(original.y(), height, event.getScreenHeight());
        // Pre coordinates are anchors, not the final content origin. Invert vanilla's
        // +12/-12 offset so its subsequent positioning preserves the four-unit margin.
        event.setX(x - 12);
        event.setY(y + 12);
    }

    private ItemGuideTooltipLayout() { }
}
