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
import java.util.Map;
import java.util.UUID;

/** World-owned shared exits and boss reservations survive unloads and restarts. */
public final class CitySavedData extends SavedData {
    private final Map<Long, Long> exits = new HashMap<>();
    private final Map<Long, UUID> bosses = new HashMap<>();

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
        return data;
    }

    public void open(BlockPos lowerDoor, long until) {
        exits.merge(lowerDoor.asLong(), until, Math::max);
        setDirty();
    }

    public boolean active(BlockPos lowerDoor, long now) {
        return exits.getOrDefault(lowerDoor.asLong(), Long.MIN_VALUE) > now;
    }

    public Map<Long, Long> activeExits(long now) {
        return activeExits(null, now);
    }

    /**
     * Drops exits whose window ended. When a level is supplied the expired door
     * is closed so players can see the shared exit disappear instead of walking
     * through an open leaf that no longer teleports.
     */
    public Map<Long, Long> activeExits(ServerLevel level, long now) {
        boolean[] removed = {false};
        exits.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            removed[0] = true;
            if (level != null) closeExpiredDoor(level, BlockPos.of(entry.getKey()));
            return true;
        });
        if (removed[0]) setDirty();
        return Map.copyOf(exits);
    }

    private static void closeExpiredDoor(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock) || !state.getValue(DoorBlock.OPEN)) return;
        level.setBlock(pos, state.setValue(DoorBlock.OPEN, false), 3);
        BlockPos up = pos.above();
        var upper = level.getBlockState(up);
        if (upper.getBlock() == state.getBlock() && upper.hasProperty(DoorBlock.HALF)
                && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
            level.setBlock(up, upper.setValue(DoorBlock.OPEN, false), 3);
    }

    public boolean reserveBoss(long region, UUID uuid) {
        if (bosses.putIfAbsent(region, uuid) != null) return false;
        setDirty();
        return true;
    }

    public boolean hasBoss(long region) { return bosses.containsKey(region); }

    /** Clears exits and boss reservations; intended for GameTest batch isolation. */
    public void clearForTesting() {
        if (exits.isEmpty() && bosses.isEmpty()) return;
        exits.clear();
        bosses.clear();
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
        return tag;
    }
}
