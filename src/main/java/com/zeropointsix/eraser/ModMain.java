package com.zeropointsix.eraser;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.gravity.GravityConfig;
import com.zeropointsix.eraser.gravity.GravityNetwork;
import com.zeropointsix.eraser.eraser.EraserNetwork;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModBlocks;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.registry.ModParticles;
import com.zeropointsix.eraser.registry.ModSounds;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.wings.net.WingsNet;
import com.zeropointsix.eraser.pill.PillNetwork;
import com.zeropointsix.eraser.shaxia.ShaxiaConfig;
import com.zeropointsix.eraser.shaxia.ShaxiaEnchantments;
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
        com.zeropointsix.eraser.fertilizer.FertilizerContent.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,
                com.zeropointsix.eraser.fertilizer.FertilizerConfig.SPEC, "super-fertilizer-server.toml");
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ShaxiaEnchantments.REGISTRY.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModSounds.SOUNDS.register(bus);
        GravityNetwork.register();
        WingsNet.register();
        EraserNetwork.register();
        PillNetwork.register();
        bus.addListener(this::creativeItems);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CommonConfig.SPEC,
                MOD_ID + "-eraser-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GravityConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WingsConfig.SERVER_SPEC,
                MOD_ID + "-wings-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WingsConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ShaxiaConfig.SPEC,
                MOD_ID + "-shaxiadao-server.toml");
    }

    private void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.NEON_TUMOR_SPAWN_EGG.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SOUND_ISOLATING_ERASER.get());
            event.accept(ModItems.GRAVITY_JADE_PENDANT.get());
            com.zeropointsix.eraser.fertilizer.FertilizerContent.ITEMS.getEntries()
                    .forEach(item -> event.accept(item.get()));
            event.accept(ModItems.WIND_THUNDER_WINGS.get());
            event.accept(ModItems.ENHANCEMENT_PILL_PACK.get());
            event.accept(ModItems.EMPTY_PILL_PACK.get());
            event.accept(ModItems.SHAXIADAO.get().getDefaultInstance());
        }
    }
}
