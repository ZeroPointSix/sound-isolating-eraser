package com.zeropointsix.eraser.eternalnight;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.brickrot.BrickrotContent;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.spawn.SpawnCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

/** Independent per-kind caps; the unmerged shadow PR is resolved by registry ID. */
public final class CitySpawns {
    private static final ResourceLocation SHADOW = new ResourceLocation(ModMain.MOD_ID, "shadow_tangler");
    private CitySpawns() { }

    @SubscribeEvent public static void naturalSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!EternalNightContent.isCity(event.getLevel().getLevel())) return;
        MobSpawnType reason = event.getSpawnType();
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION
                && reason != MobSpawnType.PATROL && reason != MobSpawnType.EVENT) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (!SHADOW.equals(id) && event.getEntity().getType() != ModEntities.NEON_TUMOR.get()
                && event.getEntity().getType() != BrickrotContent.WALL.get()) event.setSpawnCancelled(true);
    }

    public static void tick(ServerLevel level) {
        if (!NightConfig.SPAWNING.get() || level.getDifficulty() == Difficulty.PEACEFUL || level.getGameTime() % 100 != 0) return;
        EntityType<?> shadowType = shadowType();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            AABB nearby = player.getBoundingBox().inflate(64);
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, nearby);
            int shadowCount = (int) mobs.stream().filter(mob -> mob.getType() == shadowType).count();
            int neonCount = (int) mobs.stream().filter(mob -> mob.getType() == ModEntities.NEON_TUMOR.get()).count();
            for (int attempt = 0; attempt < 16; attempt++) {
                int x = player.getBlockX() + level.random.nextInt(81) - 40;
                int z = player.getBlockZ() + level.random.nextInt(81) - 40;
                if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) continue;
                boolean underground = player.getY() < 56;
                int y = underground ? CityChunkGenerator.SUBWAY_Y + 2 : player.getBlockY() + level.random.nextInt(17) - 8;
                // Check nearby occupied floors, not just the roof heightmap: wall lights live below rooftops.
                if (!level.getBlockState(new BlockPos(x, y - 1, z)).isSolid()) {
                    y = underground ? CityChunkGenerator.SUBWAY_Y + 1
                            : level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                }
                BlockPos feet = new BlockPos(x, y, z);
                if (!withinCountedRange(player, feet) || !level.hasChunkAt(feet)
                        || level.getNearestPlayer(x, y, z, 16, false) != null) continue;
                CityLayout.District district = CityLayout.district(x, z);
                int light = level.getMaxLocalRawBrightness(feet);
                boolean wall = adjacentWall(level, feet);
                if (shadowType != null && shadowCount < NightConfig.SHADOW_CAP.get()
                        && (district != CityLayout.District.COMMERCIAL || y < 56) && light <= 4 && wall) {
                    int group = Math.min(2 + level.random.nextInt(4), NightConfig.SHADOW_CAP.get() - shadowCount);
                    for (int n = 0; n < group; n++) {
                        BlockPos member = feet.offset(n % 2, 0, n / 2);
                        if (withinCountedRange(player, member) && level.getNearestPlayer(member.getX(), member.getY(), member.getZ(), 16, false) == null
                                && level.getMaxLocalRawBrightness(member) <= 4 && adjacentWall(level, member)
                                && spawn(level, shadowType, member, -1, false) != null) shadowCount++;
                    }
                }
                if (neonCount < NightConfig.NEON_CAP.get() && district == CityLayout.District.COMMERCIAL
                        && attachmentLight(level, feet) >= 12 && wall) {
                    int blockX = Math.floorDiv(x, 64) * 64, blockZ = Math.floorDiv(z, 64) * 64;
                    long large = level.getEntitiesOfClass(NeonTumorEntity.class,
                            new AABB(blockX, level.getMinBuildHeight(), blockZ, blockX + 64, level.getMaxBuildHeight(), blockZ + 64),
                            NeonTumorEntity::isLarge).size();
                    int size = large < 2 && level.random.nextInt(5) == 0 ? 2 : level.random.nextInt(2);
                    Mob mob = spawn(level, ModEntities.NEON_TUMOR.get(), feet, size, false);
                    if (mob != null) { neonCount++; mobs.add(mob); }
                }
                // Notion §6.7/§7: 砌腐墙 appears in subway tunnels and industrial underground.
                if (brickrotHabitat(district, x, y, z) && attempt == 0) {
                    long region = CityLayout.region(x, z);
                    CitySavedData data = CitySavedData.get(level);
                    if (!data.hasBoss(region)) {
                        Mob boss = spawn(level, BrickrotContent.WALL.get(), feet, -1, true);
                        if (boss != null && !data.reserveBoss(region, boss.getUUID())) boss.discard();
                    }
                }
            }
        }
    }

    public static EntityType<?> shadowType() {
        return ForgeRegistries.ENTITY_TYPES.containsKey(SHADOW) ? ForgeRegistries.ENTITY_TYPES.getValue(SHADOW) : null;
    }

    /**
     * Notion §6.7/§7: 地铁隧道、工业区地下. Subway tunnels in every district, plus
     * industrial space below the surface split. Not industrial rooftops, and not
     * non-tunnel space in residential/commercial.
     */
    public static boolean brickrotHabitat(CityLayout.District district, int x, int y, int z) {
        if (district == CityLayout.District.INDUSTRIAL && y < 56) return true;
        return subwayTunnel(x, y, z);
    }

    public static boolean subwayTunnel(int x, int y, int z) {
        return y >= CityChunkGenerator.SUBWAY_Y && y < CityChunkGenerator.SUBWAY_Y + 6
                && CityTemplates.subwayTunnelAt(x, z);
    }

    public static boolean withinCountedRange(ServerPlayer player, BlockPos feet) {
        return player.getBoundingBox().inflate(64).contains(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5)
                && (player.getY() >= 56 || feet.getY() < 56);
    }

    public static int attachmentLight(ServerLevel level, BlockPos feet) {
        int light = level.getMaxLocalRawBrightness(feet);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos anchor = feet.relative(direction);
            BlockState state = level.getBlockState(anchor);
            if (state.isFaceSturdy(level, anchor, direction.getOpposite()))
                light = Math.max(light, state.getLightEmission(level, anchor));
        }
        return light;
    }

    private static boolean adjacentWall(ServerLevel level, BlockPos feet) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.relative(direction);
            BlockState state = level.getBlockState(pos);
            if (state.isFaceSturdy(level, pos, direction.getOpposite())) return true;
        }
        return false;
    }

    private static Mob spawn(ServerLevel level, EntityType<?> type, BlockPos feet, int size, boolean persistent) {
        try (var reservation = SpawnCaps.tryReserve(level, type, feet)) {
            if (reservation == null) return null;
            Entity entity = type.create(level);
            if (!(entity instanceof Mob mob)) return null;
            mob.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            if (mob instanceof NeonTumorEntity tumor && size >= 0) tumor.setSizeIndex(size);
            if (!level.getBlockState(feet.below()).isSolid() || !level.noCollision(mob)
                    || !level.getFluidState(feet).isEmpty()) return null;
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.NATURAL, null, null);
            if (mob instanceof NeonTumorEntity tumor && size >= 0) tumor.setSizeIndex(size);
            if (persistent) mob.setPersistenceRequired();
            if (level.addFreshEntity(mob) && reservation.commit(mob)) return mob;
            mob.discard();
            return null;
        }
    }
}
