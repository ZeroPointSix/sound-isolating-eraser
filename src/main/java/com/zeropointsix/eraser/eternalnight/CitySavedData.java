package com.zeropointsix.eraser.eternalnight;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
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
        if (exits.values().removeIf(until -> until <= now)) setDirty();
        return Map.copyOf(exits);
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
