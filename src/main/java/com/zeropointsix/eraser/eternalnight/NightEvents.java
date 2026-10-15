package com.zeropointsix.eraser.eternalnight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public final class NightEvents {
    public static final String PLAYER_TAG = "EternalNightCity";
    private static final Map<UUID, SleepAttempt> SLEEP = new HashMap<>();
    private static final Map<UUID, Vec3> PREVIOUS_POSITION = new HashMap<>();
    private static final Set<UUID> PENDING_RETURNS = new HashSet<>();
    private static final Map<UUID, Integer> ARRIVAL_SLEEP = new HashMap<>();
    private record SleepAttempt(long restTicks, boolean attempted) { }

    private NightEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sleep(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (EternalNightContent.isCity(player.level()) || CityEntryQueue.pending(player)) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            return;
        }
        if (event.getResultStatus() != null || !NightConfig.ENABLED.get()
                || !NightConfig.ENTRY_DIMENSIONS.get().contains(player.level().dimension().location().toString())) return;
        SLEEP.put(player.getUUID(), new SleepAttempt(player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), false));
    }

    /**
     * Deep-sleep entry is the same roll as {@link #playerTick}; server ticks also
     * call this because embedded GameTest channels and awaitingTeleport can skip
     * {@code PlayerTickEvent} the same way they skip white-door returns.
     */
    static void considerSleepEntry(ServerPlayer player) {
        if (EternalNightContent.isCity(player.level()) || CityEntryQueue.pending(player)
                || !NightConfig.ENABLED.get()
                || !NightConfig.ENTRY_DIMENSIONS.get().contains(player.level().dimension().location().toString())) {
            return;
        }
        if (!player.isSleeping()) {
            SLEEP.remove(player.getUUID());
            return;
        }
        if (!player.isSleepingLongEnough()) return;
        SleepAttempt sleep = SLEEP.get(player.getUUID());
        if (sleep == null) {
            sleep = new SleepAttempt(player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), false);
            SLEEP.put(player.getUUID(), sleep);
        }
        if (sleep.attempted) return;
        SLEEP.put(player.getUUID(), new SleepAttempt(sleep.restTicks, true));
        double chance = NightRules.entryProbability(sleep.restTicks, NightConfig.ENTRY_BASE.get(),
                NightConfig.ENTRY_DAILY.get(), NightConfig.ENTRY_CAP.get());
        if (NightRules.roll(chance, player.getRandom().nextDouble())) enter(player);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        considerSleepEntry(player);
        if (!EternalNightContent.isCity(player.level())) {
            PREVIOUS_POSITION.remove(player.getUUID());
            return;
        }
        Integer remaining = ARRIVAL_SLEEP.get(player.getUUID());
        if (remaining != null) {
            if (remaining <= 1) {
                ARRIVAL_SLEEP.remove(player.getUUID());
                if (player.isSleeping()) player.stopSleeping();
            } else {
                ARRIVAL_SLEEP.put(player.getUUID(), remaining - 1);
                if (player.getPose() != Pose.SLEEPING) player.setPose(Pose.SLEEPING);
            }
        }
        ServerLevel level = player.serverLevel();
        Vec3 current = player.position();
        Vec3 previous = PREVIOUS_POSITION.put(player.getUUID(), current);
        considerReturn(player, level, previous, current, player.getBoundingBox());
    }

    /**
     * Shared-exit return is based on the white plane volume and the movement
     * segment, not on the leaf staying open. Closed leaves, lag spikes longer
     * than four blocks, and standing inside the doorway all still send the
     * visitor home while the SavedData window is active.
     */
    public static void considerReturn(ServerPlayer player, ServerLevel level, Vec3 previous, Vec3 current, AABB box) {
        if (PENDING_RETURNS.contains(player.getUUID()) || !player.isAlive()) return;
        for (long packed : CitySavedData.get(level).activeExits(level, level.getGameTime()).keySet()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos)) continue;
            if (throughExit(previous, current, box, pos, exitFacing(level, pos))) {
                // Connection.tick restores pre-doTick coordinates; teleport after that.
                PENDING_RETURNS.add(player.getUUID());
                return;
            }
        }
    }

    static Direction exitFacing(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DoorBlock ? state.getValue(DoorBlock.FACING) : Direction.NORTH;
    }

    public static AABB exitVolume(BlockPos lower) {
        return new AABB(lower.getX(), lower.getY(), lower.getZ(),
                lower.getX() + 1, lower.getY() + 2, lower.getZ() + 1).inflate(0.15, 0.05, 0.15);
    }

    public static boolean throughExit(Vec3 previous, Vec3 current, AABB box, BlockPos lower, Direction facing) {
        AABB volume = exitVolume(lower);
        if (box != null && box.intersects(volume)) return true;
        if (previous == null || current == null) return false;
        if (volume.clip(previous, current).isPresent()) return true;
        return crossedDoor(previous, current, lower, facing);
    }

    public static boolean crossedDoor(Vec3 previous, Vec3 current, BlockPos lower, Direction facing) {
        Vec3 center = Vec3.atBottomCenterOf(lower);
        if (current.y < lower.getY() - 0.1 || current.y > lower.getY() + 2.1) return false;
        double before = (previous.x - center.x) * facing.getStepX() + (previous.z - center.z) * facing.getStepZ();
        double after = (current.x - center.x) * facing.getStepX() + (current.z - center.z) * facing.getStepZ();
        if (before * after > 0 || Math.abs(before - after) <= 0.001) return false;
        Vec3 crossing = previous.lerp(current, before / (before - after));
        double lateral = Math.abs((crossing.x - center.x) * facing.getStepZ() - (crossing.z - center.z) * facing.getStepX());
        return lateral <= 0.8 && crossing.y >= lower.getY() && crossing.y <= lower.getY() + 2;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!EternalNightContent.isCity(event.getLevel())) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        // Cancel before vanilla BedBlock/RespawnAnchorBlock can explode or alter spawn.
        if (state.getBlock() instanceof BedBlock || state.getBlock() instanceof RespawnAnchorBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
    }

    public static BlockPos lowerDoor(BlockPos pos, BlockState state) {
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    public static NightRules.DoorResult onDoorOpened(ServerPlayer player, BlockPos lower) {
        CompoundTag data = playerData(player);
        NightRules.Doors doors = new NightRules.Doors(data.getLongArray("Doors"));
        NightRules.DoorResult result = doors.open(lower.asLong(), NightConfig.DOOR_GUARANTEE.get(),
                NightConfig.EXIT_CHANCE.get(), player.getRandom().nextDouble());
        if (!result.counted()) return result;
        data.putLongArray("Doors", doors.save());
        player.getPersistentData().put(PLAYER_TAG, data);
        player.displayClientMessage(Component.translatable("message.sound_isolating_eraser.city.doors", result.count()), true);
        if (NightRules.milestone(result.count()) != 0) {
            player.sendSystemMessage(Component.translatable("message.sound_isolating_eraser.city.milestone." + result.count()));
        }
        if (result.exit()) {
            CitySavedData.get(player.serverLevel()).open(lower,
                    player.level().getGameTime() + NightConfig.EXIT_SECONDS.get() * 20L);
        }
        return result;
    }

    public static CompoundTag playerData(Player player) {
        return player.getPersistentData().getCompound(PLAYER_TAG);
    }

    public static boolean enter(ServerPlayer player) {
        return CityEntryQueue.request(player);
    }

    public static boolean keepArrivalSleep(Player player) {
        return ARRIVAL_SLEEP.containsKey(player.getUUID());
    }

    static boolean finishEntry(ServerPlayer player, ServerLevel city, int x, int z) {
        Optional<Vec3> destination = safeStreet(city, x, z);
        if (destination.isEmpty()) return false;
        CompoundTag data = playerData(player);
        data.putBoolean("Visiting", true);
        player.getPersistentData().put(PLAYER_TAG, data);
        player.stopSleepInBed(true, true);
        Vec3 pos = destination.get();
        player.teleportTo(city, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        BlockPos street = BlockPos.containing(pos.x, pos.y - 1, pos.z);
        player.startSleeping(street);
        ARRIVAL_SLEEP.put(player.getUUID(), 40);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.sound_isolating_eraser.city.arrival")));
        return true;
    }

    private static Optional<Vec3> safeStreet(ServerLevel city, int centerX, int centerZ) {
        for (int attempt = 0; attempt < 128; attempt++) {
            int x = centerX - 15 + city.random.nextInt(31), z = centerZ - 15 + city.random.nextInt(31);
            if (!CityLayout.street(x, z)) continue;
            if (city.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            int top = city.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (top < CityLayout.surface(x, z) || top > CityLayout.surface(x, z) + 2) continue;
            BlockPos feet = new BlockPos(x, top, z);
            AABB box = new AABB(x + 0.2, top, z + 0.2, x + 0.8, top + 1.8, z + 0.8);
            if (!city.getWorldBorder().isWithinBounds(feet) || !city.getBlockState(feet.below()).isSolid()
                    || !city.noCollision(box) || !city.getFluidState(feet).isEmpty()) continue;
            if (!city.getEntitiesOfClass(Monster.class, box.inflate(16)).isEmpty()) continue;
            return Optional.of(new Vec3(x + 0.5, top, z + 0.5));
        }
        return Optional.empty();
    }

    public static void returnHome(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        Vec3 target = null;
        BlockPos bed = player.getRespawnPosition();
        if (Level.OVERWORLD.equals(player.getRespawnDimension()) && bed != null
                && overworld.getBlockState(bed).getBlock() instanceof BedBlock) {
            target = Player.findRespawnPositionAndUseSpawnBlock(overworld, bed, player.getRespawnAngle(), false, true).orElse(null);
        }
        if (target == null) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            for (int radius = 0; radius <= 8 && target == null; radius++) {
                for (int dx = -radius; dx <= radius && target == null; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = spawn.getX() + dx, z = spawn.getZ() + dz;
                    int y = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (!overworld.getWorldBorder().isWithinBounds(feet) || !overworld.getFluidState(feet).isEmpty()
                            || !overworld.getFluidState(feet.below()).isEmpty()) continue;
                    target = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(
                            net.minecraft.world.entity.EntityType.PLAYER, overworld, feet, true);
                    if (target != null) break;
                }
            }
        }
        if (target == null) {
            // Never strand a visitor in the city: fall back to the shared spawn column.
            BlockPos spawn = overworld.getSharedSpawnPos();
            int y = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    spawn.getX(), spawn.getZ());
            target = new Vec3(spawn.getX() + 0.5, Math.max(y, overworld.getMinBuildHeight() + 1), spawn.getZ() + 0.5);
            System.out.println("NIGHT_UNSAFE_HOME " + player.getGameProfile().getName() + " fallback to shared spawn");
            player.displayClientMessage(Component.translatable("message.sound_isolating_eraser.city.unsafe_home"), true);
        }
        ARRIVAL_SLEEP.remove(player.getUUID());
        if (player.isSleeping()) player.stopSleeping();
        player.teleportTo(overworld, target.x, target.y, target.z, player.getRespawnAngle(), 0);
        // Door exits run inside connection.tick(), which restores firstGoodXYZ after doTick().
        player.connection.resetPosition();
        player.fallDistance = 0;
        player.getPersistentData().remove(PLAYER_TAG);
        PREVIOUS_POSITION.remove(player.getUUID());
    }

    @SubscribeEvent public static void spawnPoint(PlayerSetSpawnEvent event) {
        if (event.getSpawnLevel().equals(EternalNightContent.LEVEL)) event.setCanceled(true);
    }
    @SubscribeEvent public static void portal(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof Level level && EternalNightContent.isCity(level)) event.setCanceled(true);
    }
    @SubscribeEvent public static void crop(BlockEvent.CropGrowEvent.Pre event) {
        if (event.getLevel() instanceof Level level && EternalNightContent.isCity(level) && NightConfig.DISABLE_GROWTH.get())
            event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent public static void tree(SaplingGrowTreeEvent event) {
        if (event.getLevel() instanceof Level level && EternalNightContent.isCity(level) && NightConfig.DISABLE_GROWTH.get())
            event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent public static void bonemeal(net.minecraftforge.event.entity.player.BonemealEvent event) {
        if (EternalNightContent.isCity(event.getLevel()) && NightConfig.DISABLE_GROWTH.get()) event.setCanceled(true);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level) || !EternalNightContent.isCity(level)) return;
        CitySavedData.get(level).releaseBoss(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player && NightConfig.RESET_ON_DEATH.get()) {
            CompoundTag data = playerData(player);
            data.putBoolean("ReturnAfterDeath", true);
            data.remove("Doors");
            player.getPersistentData().put(PLAYER_TAG, data);
        }
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().put(PLAYER_TAG, playerData(event.getOriginal()).copy());
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && playerData(player).getBoolean("ReturnAfterDeath")) returnHome(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        CityEntryQueue.cancel(id);
        SLEEP.remove(id); PREVIOUS_POSITION.remove(id);
        PENDING_RETURNS.remove(id);
        ARRIVAL_SLEEP.remove(id);
    }
    @SubscribeEvent public static void serverStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        CityEntryQueue.clear();
    }
    @SubscribeEvent public static void serverStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        clearTransientState();
    }

    /** Drops in-memory visit/sleep/exit state so GameTests cannot leak across cases. */
    public static void clearTransientState() {
        SLEEP.clear();
        PREVIOUS_POSITION.clear();
        PENDING_RETURNS.clear();
        ARRIVAL_SLEEP.clear();
        CityDoorActions.clear();
        CityEntryQueue.clear();
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        CityEntryQueue.tick(server);
        // Backup scan: embedded GameTest channels and awaitingTeleport skips can
        // omit PlayerTickEvent, so deep-sleep entry and city visitors both run here.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            considerSleepEntry(player);
            if (!EternalNightContent.isCity(player.level())) continue;
            Vec3 current = player.position();
            Vec3 previous = PREVIOUS_POSITION.get(player.getUUID());
            considerReturn(player, player.serverLevel(), previous, current, player.getBoundingBox());
        }
        flushReturns(server);
    }

    public static void flushReturns(net.minecraft.server.MinecraftServer server) {
        for (UUID id : Set.copyOf(PENDING_RETURNS)) {
            PENDING_RETURNS.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive() && EternalNightContent.isCity(player.level())) {
                returnHome(player);
            }
        }
    }

    @SubscribeEvent public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !EternalNightContent.isCity(level)) return;
        if (level.getGameTime() % 20 == 0) {
            CityExitNetwork.sync(level);
            for (long packed : CitySavedData.get(level).activeExits(level, level.getGameTime()).keySet()) {
                BlockPos pos = BlockPos.of(packed);
                if (!level.hasChunkAt(pos)) continue;
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        12, 0.3, 0.7, 0.3, 0.0);
                if (level.getGameTime() % 80 == 0) level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4F, 0.6F);
            }
            CitySpawns.tick(level);
        }
    }
}
