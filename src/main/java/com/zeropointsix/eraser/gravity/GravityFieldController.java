package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class GravityFieldController {
    private static final String COOLDOWN = ModMain.MOD_ID + ":gravity_cooldown";

    public static boolean canActivate(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !GravityEquipment.isEquipped(player)
                || remainingCooldown(player) > 0) return false;
        ServerLevel level = player.serverLevel();
        AABB box = GravityGeometry.bounds(level, player.position(), GravityConfig.RADIUS.get(),
                GravityConfig.BELOW.get(), GravityConfig.ABOVE.get());
        return GravityGeometry.chunksLoaded(level, player.position(), GravityConfig.RADIUS.get(), box);
    }

    public static boolean activate(ServerPlayer player) {
        if (!canActivate(player)) return false;
        GravityFieldEntity field = GravityFieldEntity.create(player.serverLevel(), player.getUUID(), player.position());
        if (!player.serverLevel().addFreshEntity(field)) return false;
        int ticks = GravityConfig.COOLDOWN.get();
        player.getPersistentData().putLong(COOLDOWN, clock(player) + ticks);
        player.getCooldowns().addCooldown(ModItems.GRAVITY_JADE_PENDANT.get(), ticks);
        GravityTerrainCrush.crush(player.serverLevel(), player, field.position(), field.radius(),
                field.fieldBounds(), GravityConfig.SURFACE_CHANCE.get());
        return true;
    }

    private static long clock(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static int remainingCooldown(ServerPlayer player) {
        return (int) Math.max(0, Math.min(72000, player.getPersistentData().getLong(COOLDOWN) - clock(player)));
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        event.getEntity().getPersistentData().putLong(COOLDOWN, old.getLong(COOLDOWN));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { syncCooldown(event.getEntity()); }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { syncCooldown(event.getEntity()); }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { syncCooldown(event.getEntity()); }

    private static void syncCooldown(Player player) {
        if (player instanceof ServerPlayer serverPlayer && remainingCooldown(serverPlayer) > 0) {
            player.getCooldowns().addCooldown(ModItems.GRAVITY_JADE_PENDANT.get(), remainingCooldown(serverPlayer));
        }
    }

    private GravityFieldController() { }
}
