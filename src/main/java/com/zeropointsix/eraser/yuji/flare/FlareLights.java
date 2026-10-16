package com.zeropointsix.eraser.yuji.flare;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.Heightmap;

public final class FlareLights extends SavedData {
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private final Map<Long, Long> lights = new HashMap<>();
    private final Set<Long> quietLights = new HashSet<>();
    public static FlareLights get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FlareLights::load, FlareLights::new, "qihai_flare_lights");
    }
    public int size() { return lights.size(); }

    public boolean flightNearby(ServerLevel level, BlockPos pos, Player player) {
        if (!FlarePlacement.allowed(level, pos, player)) return false;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        // Low shots still illuminate the terrain without occupying crate clearance.
        BlockPos source = pos.getY() == surface || pos.getY() == surface + 1
                ? new BlockPos(pos.getX(), surface + 2, pos.getZ()) : pos;
        return flight(level, source, player);
    }

    public boolean flight(ServerLevel level, BlockPos pos, Player player) {
        if (!FlarePlacement.allowed(level, pos, player)) return false;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        // A cosmetic light must not occupy the two air blocks needed by a supply crate.
        if (pos.getY() == surface || pos.getY() == surface + 1) return false;
        long key = pos.asLong(), expiry = level.getGameTime() + 8;
        if (level.getBlockState(pos).equals(LIGHT)) {
            if (!lights.containsKey(key) || !FlarePlacement.canRenew(level, pos, LIGHT, player)) return false;
            lights.merge(key, expiry, Math::max); setDirty(); return true;
        }
        if (!FlarePlacement.placeVisualLight(level, pos, LIGHT, player)) return false;
        lights.put(key, expiry); quietLights.add(key); setDirty(); return true;
    }

    public void illuminate(ServerLevel level, BlockPos center, Player player) {
        int radius = FlareConfig.LIGHT_RADIUS.get();
        long expiry = level.getGameTime() + FlareConfig.YELLOW_LIFETIME.get();
        Set<BlockPos> air = new HashSet<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (cursor.distSqr(center) > radius * radius || !level.hasChunkAt(cursor) || level.isOutsideBuildHeight(cursor)) continue;
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || state.equals(LIGHT)) air.add(cursor.immutable());
        }
        Set<BlockPos> covered = new HashSet<>();
        var ordered = new ArrayList<>(air);
        ordered.sort(BlockPos::compareTo);
        for (BlockPos pos : ordered) {
            if (level.getBlockState(pos).equals(LIGHT)) {
                boolean durable = !quietLights.contains(pos.asLong()) || lights.get(pos.asLong()) >= expiry;
                if (lights.containsKey(pos.asLong()) && FlarePlacement.canRenew(level, pos, LIGHT, player)) {
                    lights.merge(pos.asLong(), expiry, Math::max); quietLights.remove(pos.asLong()); setDirty();
                    durable = true;
                }
                if (durable) cover(pos, air, covered);
            } else if (Math.floorMod(pos.getX(), 3) == 0 && Math.floorMod(pos.getY(), 3) == 0 && Math.floorMod(pos.getZ(), 3) == 0
                    && add(level, pos, player, expiry)) cover(pos, air, covered);
        }
        // Three steps of block-light attenuation is 15 - 3 = 12. Fill boundaries
        // and occluded pockets too; a six-block 3D lattice cannot meet that floor.
        for (BlockPos pos : ordered)
            if (!covered.contains(pos) && add(level, pos, player, expiry)) cover(pos, air, covered);
    }

    private boolean add(ServerLevel level, BlockPos pos, Player player, long expiry) {
        if (!FlarePlacement.place(level, pos, LIGHT, player)) return false;
        lights.put(pos.asLong(), expiry); quietLights.remove(pos.asLong()); setDirty(); return true;
    }

    private static void cover(BlockPos source, Set<BlockPos> air, Set<BlockPos> covered) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(source); visited.add(source);
        for (int distance = 0; distance <= 3; distance++) {
            int count = queue.size();
            while (count-- > 0) {
                BlockPos pos = queue.remove(); covered.add(pos);
                if (distance == 3) continue;
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (air.contains(next) && visited.add(next)) queue.add(next);
                }
            }
        }
    }

    public void cleanup(ServerLevel level) {
        var iterator = lights.entrySet().iterator();
        while (iterator.hasNext()) {
            var record = iterator.next();
            BlockPos pos = BlockPos.of(record.getKey());
            // Never load a chunk to clean it. Its record survives until it is loaded.
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.LIGHT)) { quietLights.remove(record.getKey()); iterator.remove(); setDirty(); continue; }
            if (level.getGameTime() < record.getValue()) continue;
            int flags = quietLights.remove(record.getKey()) ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE : Block.UPDATE_ALL;
            level.setBlock(pos, state.getFluidState().createLegacyBlock(), flags);
            iterator.remove(); setDirty();
        }
    }

    public static FlareLights load(CompoundTag tag) {
        var data = new FlareLights();
        for (Tag entry : tag.getList("Lights", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) entry;
            data.lights.put(record.getLong("Pos"), record.getLong("Expires"));
            if (record.getBoolean("Quiet")) data.quietLights.add(record.getLong("Pos"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag records = new ListTag();
        lights.forEach((pos, expires) -> {
            CompoundTag record = new CompoundTag(); record.putLong("Pos", pos); record.putLong("Expires", expires); records.add(record);
            if (quietLights.contains(pos)) record.putBoolean("Quiet", true);
        });
        tag.put("Lights", records); return tag;
    }
}
