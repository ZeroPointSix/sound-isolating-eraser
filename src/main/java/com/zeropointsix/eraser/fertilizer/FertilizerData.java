package com.zeropointsix.eraser.fertilizer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class FertilizerData extends SavedData {
    public static final int MAX_RECORDS = 10000;
    private final Map<Long, Plant> plants = new LinkedHashMap<>();
    private final Map<Long, Long> logs = new HashMap<>();
    private int cleanupCursor;

    public static final class Plant {
        public final BlockPos root;
        public final boolean tree;
        public int level = 1;
        public boolean grove;
        public long touched;
        public final Map<UUID, Integer> doses = new HashMap<>();
        public final Set<Long> trunk = new HashSet<>();
        public Plant(BlockPos root, boolean tree, long time) { this.root = root.immutable(); this.tree = tree; touched = time; }
    }

    public static FertilizerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FertilizerData::load, FertilizerData::new, "fertilizer_state");
    }

    public Plant at(ServerLevel level, BlockPos pos) {
        long key = logs.getOrDefault(pos.asLong(), pos.asLong());
        Plant plant = plants.get(key);
        if (plant != null && level.hasChunkAt(plant.root) && !valid(level, plant)) {
            remove(key);
            return null;
        }
        return plant;
    }

    private boolean valid(ServerLevel level, Plant p) {
        return p.tree ? level.getBlockState(p.root).is(FertilizerContent.LOG.get())
                : level.getBlockState(p.root).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
    }

    public boolean room(int count) { return plants.size() + count <= MAX_RECORDS; }
    public int size() { return plants.size(); }
    public void put(Plant p) {
        remove(p.root.asLong());
        plants.put(p.root.asLong(), p);
        for (long log : p.trunk) logs.put(log, p.root.asLong());
        setDirty();
    }

    public void remove(long key) {
        Plant old = plants.remove(key);
        if (old != null) { old.trunk.forEach(logs::remove); setDirty(); }
    }

    public void invalidateRoot(BlockPos root) { remove(root.asLong()); }

    public void cleanup(ServerLevel level) {
        List<Long> keys = new ArrayList<>(plants.keySet());
        if (keys.isEmpty()) return;
        int count = Math.min(128, keys.size());
        for (int i = 0; i < count; i++) {
            Plant p = plants.get(keys.get((cleanupCursor + i) % keys.size()));
            if (p == null || !level.hasChunkAt(p.root)) continue;
            if (!valid(level, p)) remove(p.root.asLong());
            else if (level.getGameTime() - p.touched > 12096000L) {
                if (p.tree) { p.doses.clear(); setDirty(); }
                else remove(p.root.asLong());
            }
        }
        cleanupCursor = (cleanupCursor + count) % keys.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 1);
        ListTag list = new ListTag();
        for (Plant p : plants.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Root", p.root.asLong()); entry.putBoolean("Tree", p.tree);
            entry.putInt("Level", p.level); entry.putBoolean("Grove", p.grove); entry.putLong("Touched", p.touched);
            entry.putLongArray("Trunk", p.trunk.stream().mapToLong(Long::longValue).toArray());
            ListTag players = new ListTag();
            p.doses.forEach((id, dose) -> { CompoundTag n = new CompoundTag(); n.putUUID("Player", id); n.putInt("Dose", dose); players.add(n); });
            entry.put("Players", players); list.add(entry);
        }
        tag.put("Plants", list);
        return tag;
    }

    public static FertilizerData load(CompoundTag tag) {
        FertilizerData data = new FertilizerData();
        for (Tag raw : tag.getList("Plants", Tag.TAG_COMPOUND)) {
            if (data.plants.size() >= MAX_RECORDS) break;
            CompoundTag n = (CompoundTag) raw;
            Plant p = new Plant(BlockPos.of(n.getLong("Root")), n.getBoolean("Tree"), n.getLong("Touched"));
            p.level = Math.max(1, Math.min(3, n.getInt("Level"))); p.grove = n.getBoolean("Grove");
            for (long log : n.getLongArray("Trunk")) {
                if (p.trunk.size() >= 16000) break;
                if (p.root.distSqr(BlockPos.of(log)) <= 4096) p.trunk.add(log);
            }
            for (Tag t : n.getList("Players", Tag.TAG_COMPOUND)) {
                CompoundTag d = (CompoundTag) t;
                if (d.hasUUID("Player") && p.doses.size() < 256) p.doses.put(d.getUUID("Player"), Math.max(0, Math.min(100, d.getInt("Dose"))));
            }
            data.put(p);
        }
        data.setDirty(false);
        return data;
    }
}
