package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ERASER_ANCHOR.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ERASER_BARRIER.get(), RenderType.translucent());
        });
    }
}
