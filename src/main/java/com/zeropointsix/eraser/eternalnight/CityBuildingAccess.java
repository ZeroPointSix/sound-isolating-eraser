package com.zeropointsix.eraser.eternalnight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Collision-based entrance approach and floor reach for generated city buildings. */
public final class CityBuildingAccess {
    public record Placement(String name, BlockPos origin, CityLayout.District district) { }
    public record Approach(BlockPos door, BlockPos feet, Direction inward, float yaw, float pitch) { }

    private CityBuildingAccess() { }

    public static List<Placement> locateBuildings(long seed) {
        Map<String, Placement> found = new LinkedHashMap<>();
        for (int bx = -12; bx <= 12; bx++) for (int bz = -12; bz <= 12; bz++) {
            String civic = CityTemplates.largeCivic(seed, bx, bz);
            for (int lx = 1; lx <= 3; lx++) for (int lz = 1; lz <= 3; lz++) {
                String name;
                if (civic != null && lx <= 2 && lz <= 2) {
                    if (lx != 1 || lz != 1) continue;
                    name = civic;
                } else {
                    if (lx == 3 && lz == 3 && CityTemplates.hasStation(seed, bx, bz)) continue;
                    if (lx == 3 && lz == 1 && Math.floorMod(CityLayout.hash(seed + 3, bx, bz), 4) == 0) continue;
                    name = CityLayout.building(seed, bx * 4 + lx, bz * 4 + lz);
                }
                int x = (bx * 4 + lx) * 16, z = (bz * 4 + lz) * 16;
                found.putIfAbsent(name, new Placement(name, new BlockPos(x, CityLayout.plateau(bx, bz), z),
                        CityLayout.district(x, z)));
            }
        }
        if (found.size() != CityTemplates.BUILDINGS.size())
            throw new AssertionError("could not locate every original building class: " + found.keySet());
        return List.copyOf(found.values());
    }

    public static void loadAround(ServerLevel city, BlockPos origin) {
        int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++)
            city.getChunk(cx + dx, cz + dz);
    }

    public static Optional<Approach> findApproach(ServerLevel city, BlockPos origin, String building) {
        var template = city.getStructureManager().get(CityTemplates.id("buildings/" + building)).orElse(null);
        if (template == null) return Optional.empty();
        var settings = new StructurePlaceSettings();
        List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo> doors
                = new ArrayList<>();
        for (var block : ForgeRegistries.BLOCKS)
            if (block instanceof DoorBlock)
                doors.addAll(template.filterBlocks(origin, settings, block));
        doors.removeIf(info -> !info.state().hasProperty(DoorBlock.HALF)
                || info.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER);
        doors.sort(Comparator.comparingInt(info -> info.pos().getY()));
        Approach street = null, any = null;
        for (var info : doors) {
            if (!(city.getBlockState(info.pos()).getBlock() instanceof DoorBlock)) continue;
            Direction outward = info.state().getValue(DoorBlock.FACING);
            Approach found = approachFrom(city, info.pos(), outward);
            if (found == null) found = approachFrom(city, info.pos(), outward.getOpposite());
            if (found == null) continue;
            if (CityLayout.street(found.feet().getX(), found.feet().getZ())) {
                if (street == null) street = found;
            } else if (any == null) any = found;
        }
        return Optional.ofNullable(street != null ? street : any);
    }

    public static void openDoor(ServerLevel city, BlockPos door) {
        var state = city.getBlockState(door);
        if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) return;
        city.setBlock(door, state.setValue(DoorBlock.OPEN, true), 3);
        var up = city.getBlockState(door.above());
        if (up.getBlock() == state.getBlock() && up.hasProperty(DoorBlock.OPEN))
            city.setBlock(door.above(), up.setValue(DoorBlock.OPEN, true), 3);
    }

    public static boolean entranceWalkable(ServerLevel city, Approach approach) {
        openDoor(city, approach.door());
        BlockPos inside = approach.door().relative(approach.inward());
        int y = standY(city, inside.getX(), inside.getZ(), approach.door().getY());
        if (y == Integer.MIN_VALUE || Math.abs(y - approach.door().getY()) > 1) return false;
        AABB body = new AABB(inside.getX() + .2, y, inside.getZ() + .2,
                inside.getX() + .8, y + 1.8, inside.getZ() + .8);
        return city.noCollision(body);
    }

    public static int floorCount(ServerLevel city, BlockPos origin, int sizeX, int sizeZ, int sizeY, BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        TreeSet<Integer> floors = new TreeSet<>();
        queue.add(start);
        int budget = 12000;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos.asLong())) continue;
            if (pos.getX() < origin.getX() - 1 || pos.getZ() < origin.getZ() - 1
                    || pos.getX() > origin.getX() + sizeX || pos.getZ() > origin.getZ() + sizeZ
                    || pos.getY() < origin.getY() - 1 || pos.getY() > origin.getY() + sizeY + 1)
                continue;
            AABB body = new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2,
                    pos.getX() + .8, pos.getY() + 1.8, pos.getZ() + .8);
            if (!city.getBlockState(pos.below()).isSolid() || !city.noCollision(body)) continue;
            floors.add(pos.getY());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                int nextY = standY(city, next.getX(), next.getZ(), pos.getY());
                if (nextY != Integer.MIN_VALUE && Math.abs(nextY - pos.getY()) <= 1)
                    queue.add(new BlockPos(next.getX(), nextY, next.getZ()));
            }
            for (int dy : new int[] {1, -1}) {
                int nextY = standY(city, pos.getX(), pos.getZ(), pos.getY() + dy);
                if (nextY != Integer.MIN_VALUE && Math.abs(nextY - pos.getY()) <= 1)
                    queue.add(new BlockPos(pos.getX(), nextY, pos.getZ()));
            }
        }
        int count = 0, last = Integer.MIN_VALUE;
        for (int y : floors) {
            if (last == Integer.MIN_VALUE || y >= last + 3) {
                count++;
                last = y;
            }
        }
        return count;
    }

    public static int expectedFloors(ServerLevel city, BlockPos origin, String building) {
        var template = city.getStructureManager().get(CityTemplates.id("buildings/" + building)).orElse(null);
        if (template == null) return 1;
        TreeSet<Integer> doorY = new TreeSet<>();
        var settings = new StructurePlaceSettings();
        for (var block : ForgeRegistries.BLOCKS) if (block instanceof DoorBlock)
            for (var info : template.filterBlocks(origin, settings, block))
                if (info.state().hasProperty(DoorBlock.HALF)
                        && info.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                    doorY.add(info.pos().getY());
        int count = 0, last = Integer.MIN_VALUE;
        for (int y : doorY) {
            if (last == Integer.MIN_VALUE || y >= last + 3) {
                count++;
                last = y;
            }
        }
        return Math.max(1, count);
    }

    private static Approach approachFrom(ServerLevel city, BlockPos door, Direction outward) {
        for (int distance = 3; distance >= 1; distance--) {
            BlockPos column = door.relative(outward, distance);
            int ground = standY(city, column.getX(), column.getZ(), door.getY());
            if (ground == Integer.MIN_VALUE) {
                int height = city.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                ground = standY(city, column.getX(), column.getZ(), height);
            }
            if (ground == Integer.MIN_VALUE) continue;
            BlockPos feet = new BlockPos(column.getX(), ground, column.getZ());
            AABB body = new AABB(feet.getX() + .2, feet.getY(), feet.getZ() + .2,
                    feet.getX() + .8, feet.getY() + 1.8, feet.getZ() + .8);
            if (!city.getBlockState(feet.below()).isSolid() || !city.noCollision(body)) continue;
            Direction inward = outward.getOpposite();
            Vec3 eye = new Vec3(feet.getX() + .5, feet.getY() + 1.62, feet.getZ() + .5);
            Vec3 look = Vec3.atCenterOf(door).add(0, 0.5, 0);
            double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            return new Approach(door, feet, inward, yaw, pitch);
        }
        return null;
    }

    private static int standY(ServerLevel city, int x, int z, int nearY) {
        for (int dy = 2; dy >= -4; dy--) {
            int y = nearY + dy;
            BlockPos below = new BlockPos(x, y - 1, z);
            AABB body = new AABB(x + .2, y, z + .2, x + .8, y + 1.8, z + .8);
            if (city.getBlockState(below).isSolid() && city.noCollision(body)
                    && city.getFluidState(new BlockPos(x, y, z)).isEmpty())
                return y;
        }
        return Integer.MIN_VALUE;
    }
}
