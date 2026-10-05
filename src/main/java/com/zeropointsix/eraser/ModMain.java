package com.zeropointsix.eraser;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.registry.ModBlocks;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;

@Mod(ModMain.MOD_ID)
public final class ModMain {
    public static final String MOD_ID = "sound_isolating_eraser";
    /** One-shot rule: every click raises a column this many cells tall. */
    public static final int MAX_HEIGHT = 5;

    public ModMain() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::creativeItems);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
    }

    private void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SOUND_ISOLATING_ERASER.get());
        }
    }
}
