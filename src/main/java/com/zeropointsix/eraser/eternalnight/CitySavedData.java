package com.zeropointsix.eraser.eternalnight;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-owned shared exits and boss reservations survive unloads and restarts. */
public final class CitySavedData extends SavedData {
    private final Map<Long, Long> exits = new HashMap<>();
    private final Map<Long, UUID> bosses = new HashMap<>();
    /** Expired doors that could not close because their chunk was unloaded. */
    private final Set<Long> pendingClose = new HashSet<>();

    public static CitySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CitySavedData::load, CitySavedData::new,
                "eternal_night_city");
    }

    public static CitySavedData load(CompoundTag tag) {
        CitySavedData data = new CitySavedData();
        for (Tag entry : tag.getList("Exits", Tag.TAG_COMPOUND)) {
            CompoundTag exit = (CompoundTag) entry;
            data.exits.put(exit.getLong("Pos"), exit.getLong("Until"));
        }
        for (Tag entry : tag.getList("Bosses", Tag.TAG_COMPOUND)) {
            CompoundTag boss = (CompoundTag) entry;
            if (boss.hasUUID("UUID")) data.bosses.put(boss.getLong("Region"), boss.getUUID("UUID"));
        }
        for (long pos : tag.getLongArray("PendingClose")) data.pendingClose.add(pos);
        return data;
    }

    public void open(BlockPos lowerDoor, long until) {
        exits.merge(lowerDoor.asLong(), until, Math::max);
        pendingClose.remove(lowerDoor.asLong());
        setDirty();
    }

    public boolean active(BlockPos lowerDoor, long now) {
        return exits.getOrDefault(lowerDoor.asLong(), Long.MIN_VALUE) > now;
    }

    public boolean waitingToClose(BlockPos lowerDoor) {
        return pendingClose.contains(lowerDoor.asLong());
    }

    public Map<Long, Long> activeExits(long now) {
        return activeExits(null, now);
    }

    /**
     * Drops exits whose window ended. When a level is supplied the expired door
     * is closed so players can see the shared exit disappear instead of walking
     * through an open leaf that no longer teleports. Unloaded chunks keep a
     * pending close until they load again; the expired record cannot teleport.
     */
    public Map<Long, Long> activeExits(ServerLevel level, long now) {
        boolean dirty = false;
        var expired = exits.entrySet().iterator();
        while (expired.hasNext()) {
            var entry = expired.next();
            if (entry.getValue() > now) continue;
            expired.remove();
            dirty = true;
            if (level == null || !closeExpiredDoor(level, BlockPos.of(entry.getKey())))
                pendingClose.add(entry.getKey());
        }
        if (level != null) {
            var pending = pendingClose.iterator();
            while (pending.hasNext()) {
                long packed = pending.next();
                if (closeExpiredDoor(level, BlockPos.of(packed))) {
                    pending.remove();
                    dirty = true;
                }
            }
        }
        if (dirty) setDirty();
        return Map.copyOf(exits);
    }

    /** @return true when the door is closed or gone and this position can be dropped. */
    private static boolean closeExpiredDoor(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) return true;
        if (!state.getValue(DoorBlock.OPEN)) return true;
        level.setBlock(pos, state.setValue(DoorBlock.OPEN, false), 3);
        BlockPos up = pos.above();
        var upper = level.getBlockState(up);
        if (upper.getBlock() == state.getBlock() && upper.hasProperty(DoorBlock.HALF)
                && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
            level.setBlock(up, upper.setValue(DoorBlock.OPEN, false), 3);
        return true;
    }

    public boolean reserveBoss(long region, UUID uuid) {
        if (bosses.putIfAbsent(region, uuid) != null) return false;
        setDirty();
        return true;
    }

    public boolean hasBoss(long region) { return bosses.containsKey(region); }

    /** Clears exits, pending closes and boss reservations; intended for GameTest batch isolation. */
    public void clearForTesting() {
        if (exits.isEmpty() && bosses.isEmpty() && pendingClose.isEmpty()) return;
        exits.clear();
        bosses.clear();
        pendingClose.clear();
        setDirty();
    }

    /** Replaces in-memory maps from NBT as a process restart would via {@link #load}. */
    public void overwriteFrom(CompoundTag tag) {
        CitySavedData loaded = load(tag);
        exits.clear();
        exits.putAll(loaded.exits);
        bosses.clear();
        bosses.putAll(loaded.bosses);
        pendingClose.clear();
        pendingClose.addAll(loaded.pendingClose);
        setDirty();
    }

    public void releaseBoss(UUID uuid) {
        if (bosses.values().removeIf(uuid::equals)) setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag savedExits = new ListTag();
        exits.forEach((pos, until) -> {
            CompoundTag exit = new CompoundTag();
            exit.putLong("Pos", pos);
            exit.putLong("Until", until);
            savedExits.add(exit);
        });
        tag.put("Exits", savedExits);
        ListTag savedBosses = new ListTag();
        bosses.forEach((region, uuid) -> {
            CompoundTag boss = new CompoundTag();
            boss.putLong("Region", region);
            boss.putUUID("UUID", uuid);
            savedBosses.add(boss);
        });
        tag.put("Bosses", savedBosses);
        tag.putLongArray("PendingClose", pendingClose.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
