package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.item.ItemGuideCatalog;
import com.zeropointsix.eraser.item.ItemGuideLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Retains native tooltip rendering and preserves every row, paging only when necessary. */
@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class ItemGuideTooltipLayout {
    private static final Component PAGE_ANCHOR = Component.empty();
    private static RenderTooltipEvent.GatherComponents gathered;
    private static boolean renderingPage;
    private static String pageKey = "";
    private static int page;
    private static int pageCount = 1;
    private static Screen pageScreen;
    private static int mouseX;
    private static int mouseY;
    private static long lastPagedRender;

    public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(Page.class, PageRenderer::new);
    }

    public static boolean isRenderingPage() { return renderingPage; }
    public static int pageIndex() { return page; }
    public static int pageCount() { return pageCount; }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        // LOWEST listeners may still append text or images. Read the final list in Pre.
        if (renderingPage) {
            // Forge 47.2.0 inserts an optional image after the first text element.
            // Remove only our sentinel after insertion, leaving the native page as the sole row.
            event.getTooltipElements().removeIf(element -> element.left().orElse(null) == PAGE_ANCHOR);
        } else {
            gathered = event;
            if (ItemGuideCatalog.find(event.getItemStack()) == null) { pageCount = 1; page = 0; pageKey = ""; }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void position(RenderTooltipEvent.Pre event) {
        if (ItemGuideCatalog.find(event.getItemStack()) == null
                || event.getTooltipPositioner() != DefaultTooltipPositioner.INSTANCE) return;
        int width = event.getComponents().stream().mapToInt(c -> c.getWidth(event.getFont())).max().orElse(0);
        int height = event.getComponents().stream().mapToInt(ClientTooltipComponent::getHeight).sum()
                + (event.getComponents().size() == 1 ? -2 : 0);
        if (!renderingPage) {
            pageCount = 1;
            if (!ItemGuideLayout.fits(width, height, event.getScreenWidth(), event.getScreenHeight())
                    && gathered != null && gathered.getItemStack() == event.getItemStack()) {
                reflow(event);
                return;
            }
            page = 0;
            pageKey = "";
        }
        var original = event.getTooltipPositioner().positionTooltip(event.getScreenWidth(), event.getScreenHeight(),
                event.getX(), event.getY(), width, height);
        int x = ItemGuideLayout.clamp(original.x(), width, event.getScreenWidth());
        int y = ItemGuideLayout.clamp(original.y(), height, event.getScreenHeight());
        // Pre uses mouse anchors; invert vanilla's offsets after clamping the final origin.
        event.setX(x - 12);
        event.setY(y + 12);
    }

    private static void reflow(RenderTooltipEvent.Pre event) {
        var font = event.getFont();
        int maxWidth = Math.max(1, event.getScreenWidth() - 2 * ItemGuideLayout.PADDING);
        int maxHeight = Math.max(1, event.getScreenHeight() - 2 * ItemGuideLayout.PADDING);
        var elements = List.copyOf(gathered.getTooltipElements());
        List<ClientTooltipComponent> rows = new ArrayList<>();
        for (var element : elements) {
            if (element.left().isPresent()) {
                var lines = font.split(element.left().orElseThrow(), maxWidth);
                if (lines.isEmpty()) rows.add(ClientTooltipComponent.create(Component.empty().getVisualOrderText()));
                else lines.forEach(line -> rows.add(ClientTooltipComponent.create(line)));
            } else {
                rows.add(ClientTooltipComponent.create(element.right().orElseThrow()));
            }
        }
        if (rows.isEmpty()) return;
        int total = rows.stream().mapToInt(ClientTooltipComponent::getHeight).sum();
        if (total > maxHeight) {
            // Repeated title and navigation row; oversized image components are fitted, not discarded.
            int budget = Math.max(1, maxHeight - 20);
            for (int i = 0; i < rows.size(); i++) {
                rows.set(i, fit(rows.get(i), font, maxWidth, i == 0 ? Math.min(10, budget) : budget));
            }
            var title = rows.get(0);
            budget = Math.max(1, maxHeight - title.getHeight() - 10);
            var body = rows.subList(1, rows.size());
            var ranges = ItemGuideLayout.pages(body.stream().mapToInt(ClientTooltipComponent::getHeight).toArray(), budget);
            String key = event.getItemStack().getItem() + ":" + event.getScreenWidth() + ":" + event.getScreenHeight()
                    + ":" + elements.stream().map(e -> e.map(text -> text.getString(), Object::toString)).toList();
            if (!key.equals(pageKey) || pageScreen != Minecraft.getInstance().screen) { pageKey = key; page = 0; }
            pageCount = Math.max(1, ranges.size());
            page = Math.min(page, pageCount - 1);
            List<ClientTooltipComponent> visible = new ArrayList<>();
            visible.add(title);
            if (!ranges.isEmpty()) {
                int[] range = ranges.get(page);
                visible.addAll(body.subList(range[0], range[1]));
            }
            visible.add(fit(ClientTooltipComponent.create(Component.translatable("guide.sound_isolating_eraser.page",
                    page + 1, pageCount).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()), font, maxWidth, 10));
            rows.clear();
            rows.addAll(visible);
            pageScreen = Minecraft.getInstance().screen;
            mouseX = event.getX();
            mouseY = event.getY();
            lastPagedRender = System.nanoTime();
        } else {
            page = 0;
            pageKey = "";
            for (int i = 0; i < rows.size(); i++) rows.set(i, fit(rows.get(i), font, maxWidth, maxHeight));
        }
        event.setCanceled(true);
        renderingPage = true;
        try {
            event.getGraphics().renderTooltip(font, List.of(PAGE_ANCHOR), Optional.of(new Page(List.copyOf(rows))),
                    event.getItemStack(), event.getX(), event.getY());
        } finally {
            renderingPage = false;
        }
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (pageCount <= 1 || event.getScreen() != pageScreen
                || System.nanoTime() - lastPagedRender > 250_000_000L
                || Math.abs(event.getMouseX() - mouseX) > 2 || Math.abs(event.getMouseY() - mouseY) > 2
                || event.getScrollDelta() == 0) return;
        page = Math.max(0, Math.min(pageCount - 1, page + (event.getScrollDelta() < 0 ? 1 : -1)));
        event.setCanceled(true);
    }

    private static ClientTooltipComponent fit(ClientTooltipComponent row, Font font, int width, int height) {
        float scale = Math.min(1, Math.min((float) width / Math.max(1, row.getWidth(font)),
                (float) height / Math.max(1, row.getHeight())));
        return scale < 1 ? new FittedRow(row, scale) : row;
    }

    private record Page(List<ClientTooltipComponent> rows) implements TooltipComponent { }

    private record PageRenderer(Page page) implements ClientTooltipComponent {
        public int getWidth(Font font) { return page.rows().stream().mapToInt(c -> c.getWidth(font)).max().orElse(0); }
        // Vanilla subtracts two for a single component; restore the original multi-row height.
        public int getHeight() { return page.rows().stream().mapToInt(ClientTooltipComponent::getHeight).sum() + 2; }
        public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffers) {
            for (int i = 0; i < page.rows().size(); i++) {
                var row = page.rows().get(i);
                row.renderText(font, x, y, pose, buffers);
                y += row.getHeight() + (i == 0 ? 2 : 0);
            }
        }
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            for (int i = 0; i < page.rows().size(); i++) {
                var row = page.rows().get(i);
                row.renderImage(font, x, y, graphics);
                y += row.getHeight() + (i == 0 ? 2 : 0);
            }
        }
    }

    private record FittedRow(ClientTooltipComponent row, float scale) implements ClientTooltipComponent {
        public int getWidth(Font font) { return (int) Math.ceil(row.getWidth(font) * scale); }
        public int getHeight() { return (int) Math.ceil(row.getHeight() * scale); }
        public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffers) {
            row.renderText(font, 0, 0, new Matrix4f(pose).translate(x, y, 0).scale(scale), buffers);
        }
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            row.renderImage(font, 0, 0, graphics);
            graphics.pose().popPose();
        }
    }

    private ItemGuideTooltipLayout() { }
}
