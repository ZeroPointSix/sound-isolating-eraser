package com.zeropointsix.eraser.rednails;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Server authority also covers dropped items, unloaded ghosts and copied stacks. */
public final class RedNailsState extends SavedData {
    public static final int COOLDOWN = 12000;
    public static final int[] ORDER = {1, 0, 2, 3};
    private final Map<UUID, Entry> entries = new HashMap<>();

    public static final class Entry {
        public final UUID item, owner;
        public final UUID[] ghosts = new UUID[4];
        public final long[] ready = new long[4];
        public final long[] missingSince = {-1, -1, -1, -1};
        public Entry(UUID item, UUID owner) { this.item = item; this.owner = owner; }
        public int active() { return (int) Arrays.stream(ghosts).filter(g -> g != null).count(); }
        public int available(long now) {
            for (int i : ORDER) if (ghosts[i] == null && ready[i] <= now) return i;
            return -1;
        }
    }

    public static long clock(MinecraftServer server) { return server.overworld().getGameTime(); }
    public static RedNailsState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RedNailsState::load,
                RedNailsState::new, "sound_isolating_eraser_red_nails");
    }
    public Entry find(UUID id) { return entries.get(id); }
    public java.util.List<Entry> owned(UUID owner) {
        return entries.values().stream().filter(e -> e.owner.equals(owner)).toList();
    }
    public Entry read(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.hasUUID("ItemId") || !tag.hasUUID("Owner")) return null;
        UUID id = tag.getUUID("ItemId");
        Entry existing = entries.get(id);
        if (existing != null) return existing;
        Entry entry = new Entry(id, tag.getUUID("Owner"));
        long[] ready = tag.getLongArray("NailReadyAt");
        System.arraycopy(ready, 0, entry.ready, 0, Math.min(4, ready.length));
        // A missing ledger must never resurrect summons from a stale item mirror.
        entries.put(id, entry);
        setDirty();
        return entry;
    }
    public void begin(Entry entry, int nail, UUID ghost) {
        entries.put(entry.item, entry);
        entry.ghosts[nail] = ghost;
        entry.missingSince[nail] = -1;
        setDirty();
    }
    public boolean finish(Entry entry, int nail, UUID ghost, boolean died, long now) {
        if (nail < 0 || nail >= 4 || !ghost.equals(entry.ghosts[nail])) return false;
        entry.ghosts[nail] = null;
        entry.missingSince[nail] = -1;
        if (died) entry.ready[nail] = now + COOLDOWN;
        setDirty();
        return true;
    }
    public void sync(ItemStack stack, Entry entry, long now) {
        var tag = stack.getOrCreateTag();
        tag.putUUID("ItemId", entry.item);
        tag.putUUID("Owner", entry.owner);
        tag.putLongArray("NailReadyAt", entry.ready);
        ListTag out = new ListTag();
        int[] left = new int[4];
        for (int i = 0; i < 4; i++) {
            var t = new CompoundTag();
            if (entry.ghosts[i] != null) t.putUUID("Ghost", entry.ghosts[i]);
            out.add(t);
            left[i] = (int) Math.max(0, Math.min(COOLDOWN, entry.ready[i] - now));
        }
        tag.put("NailOut", out);
        tag.putIntArray("NailLeft", left);
    }
    public static RedNailsState load(CompoundTag tag) {
        var data = new RedNailsState();
        var list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int n = 0; n < list.size(); n++) {
            var t = list.getCompound(n);
            if (!t.hasUUID("Item") || !t.hasUUID("Owner")) continue;
            var entry = new Entry(t.getUUID("Item"), t.getUUID("Owner"));
            long[] ready = t.getLongArray("Ready");
            System.arraycopy(ready, 0, entry.ready, 0, Math.min(4, ready.length));
            for (int i = 0; i < 4; i++) if (t.hasUUID("Ghost" + i)) entry.ghosts[i] = t.getUUID("Ghost" + i);
            data.entries.put(entry.item, entry);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        for (Entry entry : entries.values()) {
            var t = new CompoundTag();
            t.putUUID("Item", entry.item);
            t.putUUID("Owner", entry.owner);
            t.putLongArray("Ready", entry.ready);
            for (int i = 0; i < 4; i++) if (entry.ghosts[i] != null) t.putUUID("Ghost" + i, entry.ghosts[i]);
            list.add(t);
        }
        tag.put("Items", list);
        return tag;
    }
}
