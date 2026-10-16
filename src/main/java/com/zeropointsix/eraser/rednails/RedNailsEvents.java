package com.zeropointsix.eraser.rednails;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class RedNailsEvents {
    @SubscribeEvent public static void player(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        RedNailsInput.tick(player);
        if (player.tickCount % 20 == 0) RedNailsItem.reconcileOwner(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof WaterGhostPoseSource ghost) {
            ghost.ghostBody().grab.damage(event.getAmount());
        }
    }
    @SubscribeEvent public static void allies(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof WaterGhostEntity && event.getEntity() instanceof Enemy) event.setNewTarget(null);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { RedNailsItem.cancelOwner(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { RedNailsItem.cancelOwner(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) RedNailsItem.cancelOwner(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { RedNailsInput.clear(); }
    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attributes {
        @SubscribeEvent public static void register(EntityAttributeCreationEvent event) {
            event.put(ModEntities.WATER_GHOST.get(), WaterGhostBody.attributes(16).build());
            event.put(ModEntities.WATER_GHOST_SUMMON.get(), WaterGhostBody.attributes(32).build());
        }
    }
    private RedNailsEvents() { }
}
