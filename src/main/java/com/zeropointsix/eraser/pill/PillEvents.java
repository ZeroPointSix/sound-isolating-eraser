package com.zeropointsix.eraser.pill;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.AnvilUpdateEvent;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class PillEvents {
    @SubscribeEvent
    public static void preventRepair(AnvilUpdateEvent event) {
        if (event.getLeft().is(ModItems.ENHANCEMENT_PILL_PACK.get()) && !event.getRight().isEmpty()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PillProvider provider = new PillProvider();
            event.addCapability(new ResourceLocation(ModMain.MOD_ID, "pill_effect"), provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.isAlive()) {
            player.getCapability(PillProvider.CAPABILITY).ifPresent(state -> PillEffects.tick(player, state));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void immune(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer player && PillEffects.suppressed(event.getEffectInstance().getEffect())) {
            player.getCapability(PillProvider.CAPABILITY).ifPresent(state -> {
                if (state.active()) event.setResult(Event.Result.DENY);
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void effectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PillProvider.CAPABILITY).ifPresent(state ->
                    state.externalEffectAdded(event.getEffectInstance()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void effectRemoved(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PillProvider.CAPABILITY).ifPresent(state ->
                    state.externalEffectRemoved(event.getEffect()));
        }
    }

    @SubscribeEvent
    public static void effectExpired(MobEffectEvent.Expired event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance() != null) {
            player.getCapability(PillProvider.CAPABILITY).ifPresent(state ->
                    state.externalEffectRemoved(event.getEffectInstance().getEffect()));
        }
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        try {
            event.getOriginal().getCapability(PillProvider.CAPABILITY).ifPresent(old ->
                    event.getEntity().getCapability(PillProvider.CAPABILITY).ifPresent(state -> {
                        state.load(old.save());
                        if (event.isWasDeath()) state.discardPresentations();
                    }));
        } finally {
            event.getOriginal().invalidateCaps();
        }
    }

    private static void restore(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            player.getCapability(PillProvider.CAPABILITY).ifPresent(state -> {
                PillEffects.refresh(serverPlayer, state);
                PillNetwork.sync(serverPlayer, state);
            });
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { restore(event.getEntity()); }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { restore(event.getEntity()); }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { restore(event.getEntity()); }

    @SubscribeEvent
    public static void finishUse(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().is(Items.MILK_BUCKET)) {
            PillEffects.milkFinished(player);
        }
    }

    private PillEvents() { }
}
