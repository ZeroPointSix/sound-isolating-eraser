package com.zeropointsix.eraser;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.gravity.GravityConfig;
import com.zeropointsix.eraser.gravity.GravityNetwork;
import com.zeropointsix.eraser.registry.ModEntities;
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
        ModEntities.ENTITIES.register(bus);
        GravityNetwork.register();
        bus.addListener(this::creativeItems);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GravityConfig.SPEC);
    }

    private void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SOUND_ISOLATING_ERASER.get());
            event.accept(ModItems.GRAVITY_JADE_PENDANT.get());
        }
    }
}
