package com.zeropointsix.eraser.yuji.city;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class RainCityData extends SavedData {
    public final Map<UUID, CompoundTag> players = new HashMap<>();
    public long bellAt = -1;
    public boolean guardiansSpawned;
    public int guardianMask;
    public UUID buddha;
    public static RainCityData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(RainCityData::load, RainCityData::new, "qihai_yuji_city");
    }
    public static RainCityData load(CompoundTag tag) {
        var data = new RainCityData();
        for (Tag t : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            if (p.hasUUID("Id")) data.players.put(p.getUUID("Id"), p.copy());
        }
        data.bellAt = tag.contains("BellAt") ? tag.getLong("BellAt") : -1;
        data.guardiansSpawned = tag.getBoolean("Guardians");
        data.guardianMask = tag.getInt("GuardianMask");
        if (tag.hasUUID("Buddha")) data.buddha = tag.getUUID("Buddha");
        return data;
    }
    public boolean ringing() { return bellAt >= 0; }
    public boolean open(long now) { return ringing() && now - bellAt >= 92; }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        players.forEach((id, value) -> { CompoundTag saved = value.copy(); saved.putUUID("Id", id); list.add(saved); });
        tag.put("Players", list); tag.putLong("BellAt", bellAt); tag.putBoolean("Guardians", guardiansSpawned);
        tag.putInt("GuardianMask", guardianMask);
        if (buddha != null) tag.putUUID("Buddha", buddha);
        return tag;
    }
}
