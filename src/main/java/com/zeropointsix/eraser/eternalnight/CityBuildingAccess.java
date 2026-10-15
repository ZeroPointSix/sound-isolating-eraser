package com.zeropointsix.eraser.eternalnight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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
        for (int dx = -2; dx <= 3; dx++) for (int dz = -2; dz <= 3; dz++)
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
        int entranceY = doors.isEmpty() ? origin.getY() : doors.get(0).pos().getY();
        List<Approach> ranked = new ArrayList<>();
        for (var info : doors) {
            var world = city.getBlockState(info.pos());
            if (!(world.getBlock() instanceof DoorBlock) || !world.hasProperty(DoorBlock.FACING)) continue;
            openDoor(city, info.pos());
            clearApproachSlabs(city, info.pos());
            // A survey must enter from the ground floor, never teleport onto an upper landing.
            if (info.pos().getY() != entranceY) continue;
            Direction facing = world.getValue(DoorBlock.FACING);
            for (Direction outward : new Direction[] {facing, facing.getOpposite()}) {
                for (int distance = 1; distance <= 4; distance++) {
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
        int expected = expectedFloors(city, origin, building);
        Set<BlockPos> checkedDoors = new HashSet<>();
        Approach fallback = null;
        for (Approach candidate : ranked) {
            if (!entranceWalkable(city, candidate)) continue;
            if (fallback == null) fallback = candidate;
            if (!checkedDoors.add(candidate.door())) continue;
            // Some original templates have a separate ground-floor room with its own entrance.
            // Survey the stair-connected entrance, not merely the nearest isolated room.
            int reached = floorCount(city, origin, size.getX(), size.getZ(), size.getY(),
                    candidate.door().relative(candidate.inward()));
            if (reached >= expected) return Optional.of(candidate);
        }
        if (fallback != null) return Optional.of(fallback);
        return Optional.empty();
    }

    public static void openDoor(ServerLevel city, BlockPos door) {
        var state = city.getBlockState(door);
        if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) return;
        city.setBlock(door, state.setValue(DoorBlock.OPEN, true), 3);
        var up = city.getBlockState(door.above());
        if (up.getBlock() == state.getBlock() && up.hasProperty(DoorBlock.OPEN))
            city.setBlock(door.above(), up.setValue(DoorBlock.OPEN, true), 3);
        clearApproachSlabs(city, door);
    }

    /**
     * Street templates sit at surface-1 with a bottom slab at door Y. A native
     * 1.8-tall player standing on that slab has their head in the lintel at
     * door Y+2. Clear the 1–3 street blocks outside each perimeter door so
     * the run-up is pavement at integer door Y.
     */
    public static void clearBuildingApproachSlabs(LevelAccessor level, StructureTemplate template, BlockPos origin,
                                                  StructurePlaceSettings settings) {
        for (var block : ForgeRegistries.BLOCKS) {
            if (!(block instanceof DoorBlock)) continue;
            for (var info : template.filterBlocks(origin, settings, block)) {
                if (!info.state().hasProperty(DoorBlock.HALF)
                        || info.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER)
                    continue;
                clearApproachSlabs(level, info.pos());
            }
        }
    }

    public static void clearApproachSlabs(LevelAccessor level, BlockPos door) {
        var state = level.getBlockState(door);
        if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.FACING)) return;
        Direction facing = state.getValue(DoorBlock.FACING);
        int flags = level instanceof ServerLevel ? 3 : 2 | 16;
        for (Direction outward : new Direction[] {facing, facing.getOpposite()}) {
            for (int distance = 1; distance <= 3; distance++) {
                BlockPos pos = door.relative(outward, distance);
                if (!CityLayout.street(pos.getX(), pos.getZ())) continue;
                if (bottomSlab(level.getBlockState(pos)))
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
            }
        }
    }

    public static boolean entranceWalkable(ServerLevel city, Approach approach) {
        return pathWalkable(city, approach);
    }

    /** Native teleport Y offset so a bottom slab is a walkable surface, not a shin collision. */
    public static double stepUp(ServerLevel city, BlockPos feet) {
        return bottomSlab(city, feet) ? 0.5 : 0;
    }

    public static int floorCount(ServerLevel city, BlockPos origin, int sizeX, int sizeZ, int sizeY, BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        TreeSet<Integer> floors = new TreeSet<>();
        Set<Integer> storeyY = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(sizeX - 1, sizeY - 1, sizeZ - 1))) {
            BlockState state = city.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                storeyY.add(pos.getY());
        }
        queue.add(start);
        int budget = 12000;
        while (!queue.isEmpty() && budget > 0) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos.asLong())) continue;
            budget--;
            if (pos.getX() < origin.getX() - 1 || pos.getZ() < origin.getZ() - 1
                    || pos.getX() > origin.getX() + sizeX || pos.getZ() > origin.getZ() + sizeZ
                    || pos.getY() < origin.getY() - 1 || pos.getY() > origin.getY() + sizeY + 1)
                continue;
            AABB body = bodyAt(pos.getX(), pos.getY(), pos.getZ());
            boolean ladder = ladderAt(city, pos) || ladderAt(city, pos.below());
            if ((!city.getBlockState(pos.below()).isSolid() && !ladder) || !city.noCollision(body)) continue;
            // Reach a standing landing at an authored storey height. The door itself can be
            // in a separate room; merely climbing past that height on a ladder is not arrival.
            if (pos.getX() >= origin.getX() && pos.getX() < origin.getX() + sizeX
                    && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + sizeZ
                    && storeyY.contains(pos.getY()) && !ladderAt(city, pos)
                    && city.getBlockState(pos.below()).isSolid())
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
        if (budget == 0 && !queue.isEmpty())
            throw new IllegalStateException("floor survey exceeded its node budget at " + origin.toShortString());
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
        if (!city.getBlockState(feet.below()).isSolid() || !walkableSpace(city, feet.getX(), feet.getY(), feet.getZ()))
            return null;
        Direction inward = outward.getOpposite();
        boolean onAxis = feet.getX() == door.getX() || feet.getZ() == door.getZ();
        float yaw;
        float pitch;
        if (onAxis) {
            yaw = inward.toYRot();
            pitch = 0f;
        } else {
            Vec3 eye = new Vec3(feet.getX() + .5, feet.getY() + 1.62, feet.getZ() + .5);
            Vec3 look = Vec3.atCenterOf(door).add(0, 0.5, 0);
            double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
        Approach found = new Approach(door, feet, inward, yaw, pitch);
        return pathWalkable(city, found) ? found : null;
    }

    private static boolean pathWalkable(ServerLevel city, Approach approach) {
        openDoor(city, approach.door());
        if (Math.abs(approach.feet().getY() - approach.door().getY()) > 1) return false;
        BlockPos interior = approach.door().relative(approach.inward());
        int interiorY = standY(city, interior.getX(), interior.getZ(), approach.door().getY());
        if (interiorY == Integer.MIN_VALUE || Math.abs(interiorY - approach.door().getY()) > 1) return false;
        if (!walkableSpace(city, interior.getX(), interiorY, interior.getZ())) return false;
        double fromY = approach.feet().getY() + stepUp(city, approach.feet());
        double toY = interiorY + stepUp(city, new BlockPos(interior.getX(), interiorY, interior.getZ()));
        Vec3 from = new Vec3(approach.feet().getX() + .5, fromY, approach.feet().getZ() + .5);
        Vec3 to = new Vec3(interior.getX() + .5, toY, interior.getZ() + .5);
        int steps = Math.max(4, (int) Math.ceil(from.distanceTo(to) / 0.25));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;
            BlockPos sample = BlockPos.containing(x, y, z);
            if (sample.getX() == approach.door().getX() && sample.getZ() == approach.door().getZ()
                    && Math.abs(sample.getY() - approach.door().getY()) <= 1)
                continue;
            if (!bodyFits(city, x, y, z)) return false;
        }
        return true;
    }

    private static int score(Approach found) {
        int dist = Math.abs(found.feet().getX() - found.door().getX())
                + Math.abs(found.feet().getZ() - found.door().getZ());
        int dy = Math.abs(found.feet().getY() - found.door().getY());
        int axis = (found.feet().getX() == found.door().getX() || found.feet().getZ() == found.door().getZ()) ? 0 : 50;
        int street = CityLayout.street(found.feet().getX(), found.feet().getZ()) ? 0 : 2000;
        // Dist 1 sits a 0.6-wide player against the door collision; native W needs a 2-block run-up.
        int distScore = dist == 2 ? 0 : dist == 3 ? 40 : dist == 1 ? 180 : dist * 80;
        return street + distScore + dy * 10 + axis + found.door().getY();
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
        for (int dy : new int[] {0, -1, 1, -2, 2, -3, -4}) {
            int y = nearY + dy;
            BlockPos below = new BlockPos(x, y - 1, z);
            if ((city.getBlockState(below).isSolid() || ladderAt(city, below.above()))
                    && walkableSpace(city, x, y, z)
                    && city.getFluidState(new BlockPos(x, y, z)).isEmpty())
                return y;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean walkableSpace(ServerLevel city, int x, int y, int z) {
        if (city.noCollision(bodyAt(x, y, z))) return true;
        if (!bottomSlab(city.getBlockState(new BlockPos(x, y, z)))) return false;
        // Native standing on a bottom slab is still 1.8 tall (feet at y+0.5, head at y+2.3).
        AABB raised = new AABB(x + .2, y + .5, z + .2, x + .8, y + .5 + 1.8, z + .8);
        return city.noCollision(raised);
    }

    private static boolean bodyFits(ServerLevel city, double x, double y, double z) {
        AABB body = new AABB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
        if (city.noCollision(body)) return true;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!bottomSlab(city.getBlockState(pos)) && !bottomSlab(city.getBlockState(pos.below()))) return false;
        double stand = Math.floor(y) + .5;
        AABB raised = new AABB(x - .3, stand, z - .3, x + .3, stand + 1.8, z + .3);
        return city.noCollision(raised);
    }

    private static boolean bottomSlab(ServerLevel city, BlockPos pos) {
        return bottomSlab(city.getBlockState(pos));
    }

    private static boolean bottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }
}
