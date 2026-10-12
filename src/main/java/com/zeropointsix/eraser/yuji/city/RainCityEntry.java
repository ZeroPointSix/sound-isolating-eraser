package com.zeropointsix.eraser.yuji.city;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;

public final class RainCityEntry {
    private static final TicketType<UUID> TICKET = TicketType.create("yuji_city_entry", Comparator.comparing(UUID::toString), 1240);
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private record Pending(ServerPlayer player, ServerLevel source, ServerLevel city, List<ChunkPos> tickets,
                           CompletableFuture<Boolean> ready, long deadline) { }

    public static boolean request(ServerPlayer player) {
        if (PENDING.containsKey(player.getUUID())) return true;
        ServerLevel city = player.server.getLevel(RainCityContent.LEVEL);
        if (city == null || !player.level().dimension().equals(Level.OVERWORLD) || !player.isAlive()) return false;
        if (RainCityData.get(city).ringing() && !RainCityData.get(city).open(city.getGameTime())) return false;
        List<ChunkPos> tickets = new ArrayList<>();
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int x = 5; x <= 6; x++) for (int z = 5; z <= 6; z++) {
            var pos = new ChunkPos(x, z); tickets.add(pos);
            city.getChunkSource().addRegionTicket(TICKET, pos, 0, player.getUUID());
            futures.add(CompletableFuture.supplyAsync(() -> city.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true), Util.backgroundExecutor())
                    .thenCompose(f -> f).thenApply(r -> r.left().isPresent()));
        }
        var ready = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> futures.stream().allMatch(CompletableFuture::join));
        PENDING.put(player.getUUID(), new Pending(player, player.serverLevel(), city, tickets, ready, player.server.overworld().getGameTime() + 1200));
        player.displayClientMessage(Component.translatable("message.qihai.city.preparing"), true);
        return true;
    }
    public static void tick(MinecraftServer server) {
        var iterator = PENDING.values().iterator();
        while (iterator.hasNext()) {
            Pending p = iterator.next();
            if (p.player.server != server) continue;
            boolean remove = false;
            try {
                if (server.getPlayerList().getPlayer(p.player.getUUID()) != p.player || !p.player.isAlive() || p.player.serverLevel() != p.source) remove = true;
                else if (server.overworld().getGameTime() >= p.deadline) { fail(p.player); remove = true; }
                else if (p.ready.isDone()) { if (!p.ready.join() || !finish(p.player, p.city)) fail(p.player); remove = true; }
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(RainCityEntry.class).warn("Yuji entry failed", failure);
                fail(p.player); remove = true;
            } finally {
                if (remove) { release(p); iterator.remove(); }
            }
        }
    }
    public static BlockPos arrival(ServerLevel city) {
        BlockPos origin = RainCityTemplates.HOSPITAL;
        for (int y = 1; y <= 5; y++) for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) {
            BlockPos bed = origin.offset(x, y, z);
            if (!(city.getBlockState(bed).getBlock() instanceof BedBlock)) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos p = bed.relative(direction);
                if (city.getBlockState(p.below()).isFaceSturdy(city, p.below(), Direction.UP)
                        && city.noCollision(new AABB(p.getX() + .2, p.getY(), p.getZ() + .2, p.getX() + .8, p.getY() + 1.8, p.getZ() + .8))) return p;
            }
        }
        return null;
    }
    private static boolean finish(ServerPlayer player, ServerLevel city) {
        if (RainCityData.get(city).ringing() && !RainCityData.get(city).open(city.getGameTime())) return false;
        BlockPos arrival = arrival(city);
        if (arrival == null) return false;
        RainCityData data = RainCityData.get(city);
        CompoundTag state = data.players.get(player.getUUID());
        if (state == null || !state.getBoolean("Active")) {
            state = new CompoundTag(); state.putBoolean("Active", true); state.putLong("Entered", city.getGameTime());
            state.putDouble("HomeX", player.getX()); state.putDouble("HomeY", player.getY()); state.putDouble("HomeZ", player.getZ());
            state.putFloat("HomeYaw", player.getYRot()); state.putFloat("HomePitch", player.getXRot());
            state.putLong("Arrival", arrival.asLong());
            data.players.put(player.getUUID(), state); data.setDirty();
        }
        player.teleportTo(city, arrival.getX() + .5, arrival.getY(), arrival.getZ() + .5, 0, 0);
        player.setDeltaMovement(0, 0, 0); player.fallDistance = 0;
        player.displayClientMessage(Component.translatable("message.qihai.city.arrived"), false);
        return true;
    }
    private static void fail(ServerPlayer p) { p.displayClientMessage(Component.translatable("message.qihai.city.entry_failed"), false); }
    private static void release(Pending p) { for (ChunkPos c : p.tickets) p.city.getChunkSource().removeRegionTicket(TICKET, c, 0, p.player.getUUID()); }
    public static void cancel(UUID id) { Pending p = PENDING.remove(id); if (p != null) release(p); }
    public static void clear() { PENDING.values().forEach(RainCityEntry::release); PENDING.clear(); }
    private RainCityEntry() { }
}
