package com.zeropointsix.eraser.eternalnight;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class CityClientRuntime {
    private static ResourceKey<Level> previous;
    private static int fadeTicks;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) { previous = null; fadeTicks = 0; return; }
        ResourceKey<Level> dimension = client.level.dimension();
        if (!dimension.equals(previous) && dimension.equals(EternalNightContent.LEVEL)) fadeTicks = 40;
        else if (fadeTicks > 0) fadeTicks--;
        previous = dimension;
    }

    @SubscribeEvent public static void fade(RenderGuiEvent.Post event) {
        if (fadeTicks <= 0) return;
        Minecraft client = Minecraft.getInstance();
        int alpha = Math.min(255, fadeTicks * 255 / 40);
        event.getGuiGraphics().fill(0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), alpha << 24);
    }
    private CityClientRuntime() { }
}
