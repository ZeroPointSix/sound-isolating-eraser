package com.zeropointsix.eraser.eternalnight;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Keeps first-time world generation out of the server's player tick. */
public final class CityEntryQueue {
    private static final int TIMEOUT_TICKS = 1200;
    private static final TicketType<UUID> TICKET = TicketType.create("eternal_night_entry",
            Comparator.comparing(UUID::toString), TIMEOUT_TICKS + 40);
    private static final Map<UUID, Entry> PENDING = new HashMap<>();

    private static final class Entry {
        final ServerPlayer player;
        final ServerLevel source, city;
        final int x, z;
        final long deadline;
        final CompletableFuture<Boolean> art;
        final List<ChunkPos> tickets = new ArrayList<>();
        CompletableFuture<Boolean> chunks;

        Entry(ServerPlayer player, ServerLevel city, int x, int z) {
            this.player = player; this.source = player.serverLevel(); this.city = city;
            this.x = x; this.z = z;
            deadline = player.server.overworld().getGameTime() + TIMEOUT_TICKS;
            // StructureTemplateManager is also used by vanilla's parallel world generation.
            art = CompletableFuture.supplyAsync(() -> CityTemplates.available(city), Util.backgroundExecutor());
        }
    }

    private CityEntryQueue() { }

    public static boolean request(ServerPlayer player) {
        if (PENDING.containsKey(player.getUUID())) return true;
        ServerLevel city = player.server.getLevel(EternalNightContent.LEVEL);
        if (city == null || !player.isAlive() || EternalNightContent.isCity(player.level())) return false;
        long night = Math.floorDiv(player.server.overworld().getDayTime(), NightRules.DAY_TICKS);
        long hash = CityLayout.hash(city.getSeed(), (int) night, (int) (night >>> 32));
        int x = Math.floorMod((int) hash, 256) * 64;
        int z = Math.floorMod((int) (hash >>> 32), 256) * 64;
        PENDING.put(player.getUUID(), new Entry(player, city, x, z));
        // Wake at the deep-sleep threshold so vanilla cannot skip this night while chunks load.
        player.stopSleepInBed(true, true);
        player.displayClientMessage(Component.translatable("message.sound_isolating_eraser.city.preparing"), true);
        return true;
    }

    public static boolean pending(ServerPlayer player) { return PENDING.containsKey(player.getUUID()); }

    public static void tick(MinecraftServer server) {
        Iterator<Entry> iterator = PENDING.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.player.server != server) continue;
            boolean remove = false;
            try {
                ServerPlayer player = entry.player;
                if (server.getPlayerList().getPlayer(player.getUUID()) != player || !player.isAlive()
                        || player.serverLevel() != entry.source) remove = true;
                else if (server.overworld().getGameTime() >= entry.deadline) {
                    failed(player); remove = true;
                } else if (entry.art.isDone()) {
                    if (!entry.art.join()) { failed(player); remove = true; }
                    else if (entry.chunks == null) prepareChunks(entry);
                    else if (entry.chunks.isDone()) {
                        if (!entry.chunks.join() || !NightEvents.finishEntry(player, entry.city, entry.x, entry.z)) failed(player);
                        remove = true;
                    }
                }
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(CityEntryQueue.class).warn("City entry preparation failed", failure);
                failed(entry.player); remove = true;
            } finally {
                if (remove) { release(entry); iterator.remove(); }
            }
        }
    }

    private static void prepareChunks(Entry entry) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        // Four chunks contain every candidate in the 31x31 arrival square (radius < 24).
        for (int dx = -1; dx <= 0; dx++) for (int dz = -1; dz <= 0; dz++) {
            ChunkPos pos = new ChunkPos((entry.x >> 4) + dx, (entry.z >> 4) + dz);
            entry.city.getChunkSource().addRegionTicket(TICKET, pos, 0, entry.player.getUUID());
            entry.tickets.add(pos);
            // Forge 1.20.1 getChunkFuture calls managedBlock when invoked ON the server thread.
            // Its off-thread branch safely schedules the request without blocking the tick loop.
            futures.add(CompletableFuture.supplyAsync(() -> entry.city.getChunkSource()
                            .getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true), Util.backgroundExecutor())
                    .thenCompose(future -> future)
                    .thenApply(result -> result.left().isPresent()));
        }
        entry.chunks = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().allMatch(future -> future.join()));
    }

    private static void failed(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.sound_isolating_eraser.city.entry_failed"), true);
    }
    private static void release(Entry entry) {
        for (ChunkPos pos : entry.tickets)
            entry.city.getChunkSource().removeRegionTicket(TICKET, pos, 0, entry.player.getUUID());
    }
    public static void cancel(UUID id) {
        Entry entry = PENDING.remove(id);
        if (entry != null) release(entry);
    }
    public static void clear() {
        PENDING.values().forEach(CityEntryQueue::release);
        PENDING.clear();
    }
}
