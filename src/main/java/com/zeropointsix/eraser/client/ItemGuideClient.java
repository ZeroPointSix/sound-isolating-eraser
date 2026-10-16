package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.item.ItemGuideTooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ItemGuideClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemGuideTooltips.configureClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.isSameThread() || mc.getWindow() == null) return new ItemGuideTooltips.Modifiers(false, false);
            long window = mc.getWindow().getWindow();
            return new ItemGuideTooltips.Modifiers(Screen.hasShiftDown(),
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL));
        }, Component::keybind));
    }
    private ItemGuideClient() { }
}
