package com.zeropointsix.eraser.keeper;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class KeeperEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void hurt(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && KeeperSessions.active(player)
                && !event.getSource().is(DamageTypes.GENERIC_KILL)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void target(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player && KeeperSessions.active(player)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void attack(AttackEntityEvent event) {
        if (KeeperSessions.active(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void interact(PlayerInteractEvent event) {
        Player player = event.getEntity();
        if (!KeeperSessions.active(player) || !event.isCancelable()) return;
        if (event instanceof PlayerInteractEvent.RightClickItem
                && (player.level().isClientSide ? event.getItemStack().getItem() instanceof KeeperPhotoItem
                    : KeeperSessions.current(player, event.getItemStack()))) return;
        event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void breakBlock(BlockEvent.BreakEvent event) {
        if (KeeperSessions.active(event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && KeeperSessions.active(player)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Player player && KeeperSessions.active(player)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void pickup(EntityItemPickupEvent event) {
        if (KeeperSessions.active(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void xp(PlayerXpEvent.PickupXp event) {
        if (KeeperSessions.active(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void toss(ItemTossEvent event) {
        if (!KeeperSessions.active(event.getPlayer())) return;
        // Vanilla packet drops are blocked before removal; this covers other callers.
        event.setCanceled(true);
        var stack = event.getEntity().getItem();
        if (!event.getPlayer().getInventory().add(stack)) {
            if (event.getPlayer() instanceof ServerPlayer player) KeeperSessions.exit(player);
            event.setCanceled(false);
        }
    }
    @SubscribeEvent public static void open(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && KeeperSessions.active(player)) player.closeContainer();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void travel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof Player player && KeeperSessions.active(player) && !KeeperSessions.transferring(player)) event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) KeeperSessions.tick(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) KeeperSessions.login(player);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer target) KeeperNetwork.tracking(watcher, target);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) KeeperSessions.exit(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) KeeperSessions.died(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().remove(KeeperData.SESSION);
        long cooldown = event.getOriginal().getPersistentData().getLong(KeeperData.COOL_UNTIL);
        if (cooldown > 0) event.getEntity().getPersistentData().putLong(KeeperData.COOL_UNTIL, cooldown);
    }
    private KeeperEvents() { }
}
