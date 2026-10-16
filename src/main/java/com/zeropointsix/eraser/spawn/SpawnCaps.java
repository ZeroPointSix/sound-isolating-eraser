package com.zeropointsix.eraser.spawn;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.config.SpawnCapConfig;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Main-thread, tick-local admission accounting. Never cancels loading or removes existing mobs. */
@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class SpawnCaps {
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();

    public record Snapshot(int entities, int chunkEntities, int pending, int chunkPending,
                           int dimensionLimit, int chunkLimit, long allowed, long denied, long failed,
                           long scans) { }

    public static boolean controlled(EntityType<?> type) {
        if (type == ModEntities.SHADOW_TANGLER.get() || type == ModEntities.NEON_TUMOR.get()) return true;
        for (var entry : YujiContent.MOBS.entrySet())
            if (entry.getKey() != YujiKind.SHIFOTOU && entry.getValue().get() == type) return true;
        return false;
    }

    private static boolean counted(Entity entity) {
        return !entity.isRemoved() && controlled(entity.getType())
                && !(entity instanceof YujiMonster && entity.getPersistentData().getBoolean("YujiGuardian"));
    }

    private static void requireMainThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Spawn admission must run on the server thread");
    }

    /** Null means backpressure, not an unsuccessful position attempt. Close in the same call/tick. */
    @Nullable public static Reservation tryReserve(ServerLevel level, EntityType<?> type, BlockPos feet) {
        requireMainThread(level);
        long chunk = ChunkPos.asLong(feet);
        int limit = SpawnCapConfig.dimensionLimit(level.getChunkSource().getLoadedChunksCount());
        int chunkLimit = SpawnCapConfig.CHUNK_CAP.get();
        if (!controlled(type) || limit < 0 && chunkLimit < 0) return new Reservation(null, level, type, chunk);
        State state = state(level);
        if (limit >= 0 && state.live.size() + state.pending >= limit
                || chunkLimit >= 0 && state.chunks.getOrDefault(chunk, 0) + state.pendingChunks.getOrDefault(chunk, 0) >= chunkLimit) {
            state.denied++;
            return null;
        }
        state.pending++;
        change(state.pendingChunks, chunk, 1);
        state.allowed++;
        return new Reservation(state, level, type, chunk);
    }

    /** Position-independent early backpressure; tryReserve still performs the atomic final check. */
    public static boolean hasDimensionRoom(ServerLevel level, EntityType<?> type) {
        requireMainThread(level);
        int limit = SpawnCapConfig.dimensionLimit(level.getChunkSource().getLoadedChunksCount());
        if (!controlled(type) || limit < 0) return true;
        State state = state(level);
        if (state.live.size() + state.pending < limit) return true;
        state.denied++;
        return false;
    }

    public static Snapshot snapshot(ServerLevel level, BlockPos feet) {
        requireMainThread(level);
        State state = state(level);
        long chunk = ChunkPos.asLong(feet);
        return new Snapshot(state.live.size(), state.chunks.getOrDefault(chunk, 0), state.pending,
                state.pendingChunks.getOrDefault(chunk, 0),
                SpawnCapConfig.dimensionLimit(level.getChunkSource().getLoadedChunksCount()),
                SpawnCapConfig.CHUNK_CAP.get(), state.allowed, state.denied, state.failed, state.scans);
    }

    private static State state(ServerLevel level) {
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        if (state.tick != level.getGameTime() || state.dirty) {
            state.live.clear();
            state.chunks.clear();
            state.joins.clear();
            // One read-only snapshot per admission tick, not one full scan per group member.
            for (Entity entity : level.getAllEntities()) if (counted(entity)) state.record(entity);
            state.tick = level.getGameTime();
            state.dirty = false;
            state.scans++;
        }
        var iterator = state.joins.entrySet().iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next().getValue();
            // A cancellable join notification alone is not evidence of a successful insertion.
            if (level.getEntity(entity.getUUID()) == entity) {
                if (counted(entity)) state.record(entity);
                iterator.remove();
            } else if (entity.isRemoved()) iterator.remove();
        }
        return state;
    }

    private static void change(Map<Long, Integer> counts, long chunk, int delta) {
        int next = counts.getOrDefault(chunk, 0) + delta;
        if (next < 0) throw new IllegalStateException("Negative spawn reservation count");
        if (next == 0) counts.remove(chunk); else counts.put(chunk, next);
    }

    private static final class State {
        final Map<UUID, Long> live = new HashMap<>();
        final Map<Long, Integer> chunks = new HashMap<>(), pendingChunks = new HashMap<>();
        final Map<UUID, Entity> joins = new HashMap<>();
        long tick = Long.MIN_VALUE, allowed, denied, failed, scans;
        int pending;
        boolean dirty;

        void record(Entity entity) { move(entity.getUUID(), entity.chunkPosition().toLong()); }
        void move(UUID id, long chunk) {
            Long previous = live.put(id, chunk);
            if (previous != null && previous == chunk) return;
            if (previous != null) change(chunks, previous, -1);
            change(chunks, chunk, 1);
        }
        void remove(UUID id) {
            Long previous = live.remove(id);
            if (previous != null) change(chunks, previous, -1);
            joins.remove(id);
        }
    }

    public static final class Reservation implements AutoCloseable {
        private final State state;
        private final ServerLevel level;
        private final EntityType<?> type;
        private final long chunk;
        private boolean closed;
        private Entity committed;

        private Reservation(State state, ServerLevel level, EntityType<?> type, long chunk) {
            this.state = state; this.level = level; this.type = type; this.chunk = chunk;
        }

        /** Call only after the insertion API; a false result requires discarding this new candidate. */
        public boolean commit(Entity entity) {
            requireMainThread(level);
            if (closed) return committed == entity;
            if (entity.getType() != type || entity.level() != level || entity.isRemoved()
                    || level.getEntity(entity.getUUID()) != entity || entity.chunkPosition().toLong() != chunk) return false;
            if (state != null && counted(entity)) {
                state.record(entity);
                state.joins.remove(entity.getUUID());
            }
            committed = entity;
            close();
            return true;
        }

        @Override public void close() {
            requireMainThread(level);
            if (closed) return;
            closed = true;
            if (state != null) {
                state.pending--;
                change(state.pendingChunks, chunk, -1);
                if (committed == null) state.failed++;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && counted(event.getEntity())) {
            State state = STATES.get(level);
            if (state != null && state.tick == level.getGameTime())
                state.joins.put(event.getEntity().getUUID(), event.getEntity());
        }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            State state = STATES.get(level);
            if (state != null) state.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent public static void move(EntityEvent.EnteringSection event) {
        if (event.didChunkChange() && event.getEntity().level() instanceof ServerLevel level) {
            State state = STATES.get(level);
            if (state != null && state.live.containsKey(event.getEntity().getUUID()))
                state.move(event.getEntity().getUUID(), ChunkPos.asLong(event.getNewPos().x(), event.getNewPos().z()));
        }
    }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            State state = STATES.get(level);
            if (state != null) state.dirty = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            State state = STATES.get(level);
            if (state != null && state.pending == 0) {
                state.live.clear(); state.chunks.clear(); state.joins.clear();
                state.tick = Long.MIN_VALUE;
            }
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); }
    private SpawnCaps() { }
}
