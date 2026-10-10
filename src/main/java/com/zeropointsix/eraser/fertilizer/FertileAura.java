package com.zeropointsix.eraser.fertilizer;

import com.zeropointsix.eraser.ModMain;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class FertileAura {
    private static final String MARKER = "FertilizerAuraOwned";
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static boolean applying;
    private static final class Lease {
        BlockPos pos;
        ServerLevel level;
        long scan;
        boolean active;
        MobEffectInstance owned;
    }

    public static boolean nearby(ServerLevel level, BlockPos center) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(FertilizerContent.FERTILE)
                    && ++count >= FertilizerConfig.AURA_THRESHOLD.get()) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) update(player);
    }

    public static void update(ServerPlayer player) {
        Lease lease = LEASES.computeIfAbsent(player.getUUID(), ignored -> new Lease());
        long now = player.serverLevel().getGameTime();
        BlockPos pos = player.blockPosition();
        if (lease.level != player.serverLevel() || !pos.equals(lease.pos) || now - lease.scan >= 5) {
            lease.level = player.serverLevel(); lease.pos = pos; lease.scan = now;
            lease.active = nearby(lease.level, pos);
        }
        MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
        if (current != lease.owned) { lease.owned = null; player.getPersistentData().remove(MARKER); }
        if (!lease.active || player.isSpectator() || !player.isAlive()) {
            release(player, lease); return;
        }
        if (current != null && lease.owned == null) return;
        int duration = current == null ? 50 : current.getDuration();
        if (current == null || duration < 50) {
            applying = true;
            try {
                // Renew by a multiple of the native 50-tick period, never reset its healing phase.
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, current == null ? duration : duration + 50, 0, true, false, true));
                lease.owned = player.getEffect(MobEffects.REGENERATION);
                if (lease.owned != null) player.getPersistentData().putBoolean(MARKER, true);
            } finally { applying = false; }
        }
    }

    private static void release(ServerPlayer player, Lease lease) {
        if (lease.owned != null && player.getEffect(MobEffects.REGENERATION) == lease.owned) player.removeEffect(MobEffects.REGENERATION);
        lease.owned = null; player.getPersistentData().remove(MARKER);
    }

    @SubscribeEvent
    public static void externalEffect(MobEffectEvent.Applicable event) {
        if (applying || event.getEffectInstance().getEffect() != MobEffects.REGENERATION
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        Lease lease = LEASES.get(player.getUUID());
        if (lease != null) release(player, lease);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MobEffectInstance old = player.getEffect(MobEffects.REGENERATION);
        if (player.getPersistentData().getBoolean(MARKER) && old != null && old.getAmplifier() == 0 && old.getDuration() <= 100)
            player.removeEffect(MobEffects.REGENERATION);
        player.getPersistentData().remove(MARKER); LEASES.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    public static void clear(ServerPlayer player) {
        Lease lease = LEASES.remove(player.getUUID());
        if (lease != null) release(player, lease);
    }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && level.getGameTime() % 200 == 0)
            FertilizerData.get(level).cleanup(level);
    }

    private FertileAura() {}
}
