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
        List<Approach> ranked = new ArrayList<>();
        for (var info : doors) {
            var world = city.getBlockState(info.pos());
            if (!(world.getBlock() instanceof DoorBlock) || !world.hasProperty(DoorBlock.FACING)) continue;
            openDoor(city, info.pos());
            Direction facing = world.getValue(DoorBlock.FACING);
            for (Direction outward : new Direction[] {facing, facing.getOpposite()}) {
                for (int distance = 1; distance <= 3; distance++) {
                    Approach onAxis = candidateAt(city, info.pos(), outward, distance, null);
                    if (onAxis != null) ranked.add(onAxis);
                    if (distance > 2) continue;
                    Approach left = candidateAt(city, info.pos(), outward, distance, outward.getClockWise());
                    Approach right = candidateAt(city, info.pos(), outward, distance, outward.getCounterClockWise());
                    if (left != null) ranked.add(left);
                    if (right != null) ranked.add(right);
                }
            }
        }
        var size = template.getSize();
        ranked.removeIf(found -> {
            BlockPos interior = found.door().relative(found.inward());
            return !inBuilding(origin, size, interior);
        });
        ranked.sort(Comparator.comparingInt(found -> {
            int insideStart = inBuilding(origin, size, found.feet())
                    && !CityLayout.street(found.feet().getX(), found.feet().getZ()) ? 5000 : 0;
            return insideStart + score(found);
        }));
        for (Approach candidate : ranked)
            if (entranceWalkable(city, candidate)) return Optional.of(candidate);
        return Optional.empty();
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
        return pathWalkable(city, approach);
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
            AABB body = bodyAt(pos.getX(), pos.getY(), pos.getZ());
            boolean ladder = ladderAt(city, pos) || ladderAt(city, pos.below());
            if ((!city.getBlockState(pos.below()).isSolid() && !ladder) || !city.noCollision(body)) continue;
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
            if (ladderAt(city, pos) || ladderAt(city, pos.above())) queue.add(pos.above());
            if (ladderAt(city, pos.below())) queue.add(pos.below());
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

    private static Approach candidateAt(ServerLevel city, BlockPos door, Direction outward, int distance,
                                       Direction lateral) {
        BlockPos column = door.relative(outward, distance);
        if (lateral != null) column = column.relative(lateral);
        int ground = standY(city, column.getX(), column.getZ(), door.getY());
        if (ground == Integer.MIN_VALUE) {
            int height = city.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            ground = standY(city, column.getX(), column.getZ(), height);
        }
        if (ground == Integer.MIN_VALUE || ground > door.getY() || door.getY() - ground > 1) return null;
        BlockPos feet = new BlockPos(column.getX(), ground, column.getZ());
        if (!city.getBlockState(feet.below()).isSolid() || !city.noCollision(bodyAt(feet))) return null;
        Direction inward = outward.getOpposite();
        Vec3 eye = new Vec3(feet.getX() + .5, feet.getY() + 1.62, feet.getZ() + .5);
        Vec3 look = Vec3.atCenterOf(door).add(0, 0.5, 0);
        double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        Approach found = new Approach(door, feet, inward, yaw, pitch);
        return pathWalkable(city, found) ? found : null;
    }

    private static boolean pathWalkable(ServerLevel city, Approach approach) {
        openDoor(city, approach.door());
        if (Math.abs(approach.feet().getY() - approach.door().getY()) > 1) return false;
        BlockPos interior = approach.door().relative(approach.inward());
        int interiorY = standY(city, interior.getX(), interior.getZ(), approach.door().getY());
        if (interiorY == Integer.MIN_VALUE || Math.abs(interiorY - approach.door().getY()) > 1) return false;
        return city.noCollision(bodyAt(interior.getX(), interiorY, interior.getZ()));
    }

    private static int score(Approach found) {
        int dist = Math.abs(found.feet().getX() - found.door().getX())
                + Math.abs(found.feet().getZ() - found.door().getZ());
        int dy = Math.abs(found.feet().getY() - found.door().getY());
        int axis = (found.feet().getX() == found.door().getX() || found.feet().getZ() == found.door().getZ()) ? 0 : 1;
        return dist * 100 + dy * 10 + axis + (CityLayout.street(found.feet().getX(), found.feet().getZ()) ? 1 : 0);
    }

    private static boolean inBuilding(BlockPos origin, net.minecraft.core.Vec3i size, BlockPos pos) {
        return pos.getX() >= origin.getX() - 1 && pos.getX() <= origin.getX() + size.getX()
                && pos.getZ() >= origin.getZ() - 1 && pos.getZ() <= origin.getZ() + size.getZ();
    }

    private static boolean ladderAt(ServerLevel city, BlockPos pos) {
        return city.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LADDER);
    }

    private static AABB bodyAt(BlockPos pos) {
        return bodyAt(pos.getX(), pos.getY(), pos.getZ());
    }

    private static AABB bodyAt(int x, int y, int z) {
        return new AABB(x + .2, y, z + .2, x + .8, y + 1.8, z + .8);
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
