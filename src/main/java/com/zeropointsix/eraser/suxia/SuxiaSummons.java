package com.zeropointsix.eraser.suxia;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned state survives dropped/container watches and unloaded or disconnected owners. */
public final class SuxiaSummons extends SavedData {
    public static final int DURATION = 1200, COOLDOWN = 3600, DEATH_COOLDOWN = 12000;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public static final class Entry {
        public final UUID watch, owner;
        @Nullable public UUID summon;
        public long until, cooldown;
        public String dimension = "minecraft:overworld";
        Entry(UUID watch, UUID owner) { this.watch = watch; this.owner = owner; }
        public boolean active() { return summon != null; }
    }

    public static SuxiaSummons get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SuxiaSummons::load,
                SuxiaSummons::new, "sound_isolating_eraser_suxia");
    }

    @Nullable public Entry find(UUID watch) { return entries.get(watch); }

    @Nullable public Entry active(UUID owner) {
        return entries.values().stream().filter(e -> e.owner.equals(owner) && e.active()).findFirst().orElse(null);
    }

    public Entry bind(ItemStack stack, Player owner) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID("WatchUUID")) tag.putUUID("WatchUUID", UUID.randomUUID());
        UUID watch = tag.getUUID("WatchUUID");
        Entry entry = entries.get(watch);
        if (entry == null) {
            entry = new Entry(watch, tag.hasUUID("Owner") ? tag.getUUID("Owner") : owner.getUUID());
            entry.cooldown = tag.getLong("CooldownUntil");
            entries.put(watch, entry);
            setDirty();
        }
        tag.putUUID("Owner", entry.owner);
        if (!tag.contains("OwnerName")) tag.putString("OwnerName", owner.getName().getString());
        return entry;
    }

    public void begin(Entry entry, SuxiaSummonEntity summon, long now) {
        entry.summon = summon.getUUID();
        entry.until = now + DURATION;
        entry.dimension = summon.level().dimension().location().toString();
        setDirty();
    }

    public boolean finish(Entry entry, UUID summon, long now, boolean died) {
        if (!summon.equals(entry.summon)) return false;
        entry.summon = null;
        entry.until = 0;
        entry.cooldown = Math.max(entry.cooldown, now + (died ? DEATH_COOLDOWN : COOLDOWN));
        setDirty();
        return true;
    }

    public void sync(ItemStack stack, Entry entry, long now) {
        var tag = stack.getOrCreateTag();
        tag.putBoolean("Open", entry.active());
        tag.putLong("CooldownUntil", entry.cooldown);
        tag.putInt("CooldownRemaining", (int) Math.max(0, Math.min(DEATH_COOLDOWN, entry.cooldown - now)));
        tag.putLong("SummonUntil", entry.until);
        tag.putInt("Remaining", entry.active() ? (int) Math.max(0, Math.min(DURATION, entry.until - now)) : 0);
        if (entry.summon == null) tag.remove("SummonUUID");
        else tag.putUUID("SummonUUID", entry.summon);
    }

    public static SuxiaSummons load(CompoundTag nbt) {
        var data = new SuxiaSummons();
        ListTag list = nbt.getList("Watches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var t = list.getCompound(i);
            if (!t.hasUUID("Watch") || !t.hasUUID("Owner")) continue;
            var entry = new Entry(t.getUUID("Watch"), t.getUUID("Owner"));
            if (t.hasUUID("Summon")) entry.summon = t.getUUID("Summon");
            entry.until = t.getLong("Until");
            entry.cooldown = t.getLong("Cooldown");
            entry.dimension = t.getString("Dimension");
            data.entries.put(entry.watch, entry);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (var entry : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Watch", entry.watch);
            t.putUUID("Owner", entry.owner);
            if (entry.summon != null) t.putUUID("Summon", entry.summon);
            t.putLong("Until", entry.until);
            t.putLong("Cooldown", entry.cooldown);
            t.putString("Dimension", entry.dimension);
            list.add(t);
        }
        nbt.put("Watches", list);
        return nbt;
    }
}
