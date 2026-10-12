package com.zeropointsix.eraser.shadow;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShadowTanglerRegistration {
    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SHADOW_TANGLER.get(), ShadowTanglerEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void eggs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.SHADOW_TANGLER_SPAWN_EGG.get());
        }
    }

    private ShadowTanglerRegistration() { }
}
