package com.zeropointsix.eraser.neon;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class NeonTumorEvents {
    private NeonTumorEvents() { }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.getEntityBeingMounted().level().isClientSide && event.isDismounting()
                && event.getEntityBeingMounted() instanceof NeonTumorEntity tumor
                && tumor.isEngulfing() && tumor.isAlive() && event.getEntityMounting().isAlive()) {
            event.setCanceled(true);
        }
    }

    private static void release(Entity entity) {
        if (entity.getVehicle() instanceof NeonTumorEntity tumor) tumor.releasePassenger();
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { release(event.getEntity()); }
    @SubscribeEvent public static void onDeath(LivingDeathEvent event) { release(event.getEntity()); }
    @SubscribeEvent public static void onDimensionChange(EntityTravelToDimensionEvent event) {
        release(event.getEntity());
        if (event.getEntity() instanceof NeonTumorEntity tumor) tumor.releasePassenger();
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attributes {
        private Attributes() { }
        @SubscribeEvent public static void register(EntityAttributeCreationEvent event) {
            event.put(ModEntities.NEON_TUMOR.get(), NeonTumorEntity.createAttributes().build());
        }
    }
}
