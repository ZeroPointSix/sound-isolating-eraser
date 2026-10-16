package com.zeropointsix.gravitytest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Test-only third-party extension, appended after the production Gather listener. */
@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ItemGuideExtensionProbe {
    static boolean imageRendered;
    record Image() implements TooltipComponent { }

    @SubscribeEvent
    public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(Image.class, image -> new ClientTooltipComponent() {
            public int getWidth(Font font) { return 96; }
            public int getHeight() { return 20; }
            public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
                graphics.renderItem(Items.DIAMOND.getDefaultInstance(), x, y);
                graphics.drawString(font, "Late image", x + 20, y + 4, 0x55FFFF);
                imageRendered = true;
            }
        });
    }

    private ItemGuideExtensionProbe() { }
}
