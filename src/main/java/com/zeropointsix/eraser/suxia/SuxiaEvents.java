package com.zeropointsix.eraser.suxia;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class SuxiaEvents {
    public static final String LAST_HURT = ModMain.MOD_ID + "_last_hurt";
    private SuxiaEvents() { }

    @SubscribeEvent public static void blood(LivingDamageEvent event) {
        if (!event.getEntity().level().isClientSide && event.getAmount() > 0) {
            event.getEntity().getPersistentData().putLong(LAST_HURT, SuxiaMotion.clock(event.getEntity().level()));
        }
    }

    @SubscribeEvent public static void allies(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof SuxiaEntity && event.getEntity() instanceof Enemy) event.setNewTarget(null);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { SuxiaPocketWatchItem.cancelOwner(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { SuxiaPocketWatchItem.cancelOwner(event.getEntity()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { SuxiaPocketWatchItem.syncInventory(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) SuxiaPocketWatchItem.cancelOwner(player);
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attributes {
        @SubscribeEvent public static void register(EntityAttributeCreationEvent event) {
            event.put(ModEntities.SUXIA.get(), SuxiaMotion.attributes().build());
            event.put(ModEntities.SUXIA_SUMMON.get(), SuxiaMotion.attributes().build());
        }
        private Attributes() { }
    }
}
