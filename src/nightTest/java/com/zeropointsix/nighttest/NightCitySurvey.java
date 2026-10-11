package com.zeropointsix.nighttest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zeropointsix.eraser.eternalnight.*;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Observational acceptance, not a replacement for the asserted sleep/exit lifecycle. */
final class NightCitySurvey {
    private record Target(String name, BlockPos origin, CityLayout.District district) { }
    private static final Path RESULTS = Path.of(System.getProperty("night.qa.results"));
    private static final TicketType<String> TICKET = TicketType.create("night_qa_survey", String::compareTo, 2400);
    private static final List<ChunkPos> tickets = new ArrayList<>();
    private static final JsonArray observations = new JsonArray();
    private static final JsonArray trajectory = new JsonArray();
    private static List<Target> targets;
    private static ServerPlayer player;
    private static ServerLevel city;
    private static Target target;
    private static CompletableFuture<Void> prepared;
    private static BlockPos door;
    private static Direction inward;
    private static JsonObject current;
    private static int index, phase, ticks, walked;
    private static long started;
    private static boolean ready, captured, crossed, done;
    private static com.zeropointsix.eraser.gametest.GameTestConfigs.LiveConfig liveConfig;

    static void start(ServerPlayer viewer) {
        player = viewer;
        city = viewer.server.getLevel(EternalNightContent.LEVEL);
        liveConfig = com.zeropointsix.eraser.gametest.GameTestConfigs.sandbox(NightConfig.SPEC);
        NightConfig.SPAWNING.set(true);
        NightConfig.EXIT_CHANCE.set(0.0);
        player.setGameMode(GameType.CREATIVE);
        targets = locate(city.getSeed());
        next();
    }

    static void ready() { ready = true; }
    static void captured() { captured = true; }
    static boolean finished() { return done; }

    static void tick(MinecraftServer server) throws Exception {
        if (done) return;
        ticks++;
        if (phase == 0) {
            if (!prepared.isDone()) {
                if (System.nanoTime() - started > 180_000_000_000L) finishTarget("chunk preparation timed out");
                return;
            }
            prepared.join();
            if (!positionAtEntrance()) { finishTarget("no clear exterior approach to an original door"); return; }
            signal("SURVEY_READY:" + target.name());
            phase = 1; ticks = 0;
        } else if (phase == 1 && ready) {
            signal("SURVEY_WALK:" + target.name());
            phase = 2; ticks = 0;
        } else if (phase == 2) {
            if (ticks % 5 == 0) {
                JsonObject point = new JsonObject();
                point.addProperty("template", target.name()); point.addProperty("tick", city.getGameTime());
                point.addProperty("x", player.getX()); point.addProperty("y", player.getY()); point.addProperty("z", player.getZ());
                trajectory.add(point);
            }
            double depth = (player.getX() - door.getX() - .5) * inward.getStepX()
                    + (player.getZ() - door.getZ() - .5) * inward.getStepZ();
            if (depth >= 1.2 || ticks >= 160) {
                crossed = depth >= 1.2;
                if (crossed) walked++;
                current.addProperty("nativeEntranceWalkPassed", crossed);
                current.addProperty("finalPosition", player.blockPosition().toShortString());
                current.addProperty("doorState", city.getBlockState(door).toString());
                var hit = city.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(5)),
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                current.addProperty("forwardHit", hit.getBlockPos().toShortString());
                current.addProperty("forwardBlock", city.getBlockState(hit.getBlockPos()).toString());
                signal("SURVEY_STOP:" + target.name());
                phase = 3; ticks = 0;
            }
        } else if (phase == 3 && ticks >= 220) {
            var mobs = city.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64));
            JsonArray actual = new JsonArray();
            for (var mob : mobs) {
                JsonObject item = new JsonObject();
                item.addProperty("id", ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).toString());
                item.addProperty("uuid", mob.getUUID().toString());
                item.addProperty("position", mob.blockPosition().toShortString());
                actual.add(item);
            }
            current.add("observedEntitiesWithin64", actual);
            current.addProperty("citySpawningEnabled", NightConfig.SPAWNING.get());
            current.addProperty("observationTicks", ticks);
            current.addProperty("shadowRegistered", CitySpawns.shadowType() != null);
            if (!mobs.isEmpty()) {
                var mob = mobs.get(0);
                double dx = mob.getX() - player.getX(), dz = mob.getZ() - player.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(mob.getEyeY() - player.getEyeY(), Math.hypot(dx, dz)));
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
            }
            signal("SURVEY_CAPTURE:" + target.name());
            phase = 4; ticks = 0;
        } else if (phase == 4 && captured) {
            finishTarget(crossed ? "entrance crossed; upper floors not exhaustively covered" : "native walk could not cross this entrance");
        }
        if (ticks > 600 && phase != 0) finishTarget("native survey phase timed out: " + phase);
    }

    private static void next() {
        for (var chunk : tickets) city.getChunkSource().removeRegionTicket(TICKET, chunk, 0, target.name());
        tickets.clear();
        if (index >= targets.size()) { done = true; return; }
        target = targets.get(index++);
        current = new JsonObject();
        current.addProperty("template", target.name());
        current.addProperty("district", target.district().name());
        current.addProperty("origin", target.origin().toShortString());
        current.addProperty("nativeEntranceWalkPassed", false);
        ready = false; captured = false; crossed = false; phase = 0; ticks = 0;
        started = System.nanoTime();
        List<CompletableFuture<?>> chunks = new ArrayList<>();
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) {
            var chunk = new ChunkPos((target.origin().getX() >> 4) + dx, (target.origin().getZ() >> 4) + dz);
            tickets.add(chunk);
            city.getChunkSource().addRegionTicket(TICKET, chunk, 0, target.name());
            chunks.add(CompletableFuture.supplyAsync(() -> city.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true),
                    Util.backgroundExecutor()).thenCompose(future -> future));
        }
        prepared = CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new));
    }

    private static boolean positionAtEntrance() {
        var template = city.getStructureManager().get(CityTemplates.id("buildings/" + target.name())).orElseThrow();
        var candidates = new ArrayList<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo>();
        for (var block : ForgeRegistries.BLOCKS) if (block instanceof DoorBlock)
            candidates.addAll(template.filterBlocks(target.origin(), new StructurePlaceSettings(), block));
        candidates.sort(Comparator.comparingInt(info -> info.pos().getY()));
        for (var info : candidates) {
            if (info.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            BlockPos local = info.pos().subtract(target.origin());
            int[] distances = {local.getZ(), template.getSize().getZ() - 1 - local.getZ(),
                    local.getX(), template.getSize().getX() - 1 - local.getX()};
            Direction[] outward = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            int nearest = 0;
            for (int n = 1; n < distances.length; n++) if (distances[n] < distances[nearest]) nearest = n;
            if (distances[nearest] > 1) continue;
            BlockPos approach = info.pos().relative(outward[nearest], 3);
            AABB body = new AABB(approach.getX() + .2, approach.getY(), approach.getZ() + .2,
                    approach.getX() + .8, approach.getY() + 1.8, approach.getZ() + .8);
            if (!city.getBlockState(approach.below()).isSolid() || !city.noCollision(body)
                    || !(city.getBlockState(info.pos()).getBlock() instanceof DoorBlock)) continue;
            door = info.pos(); inward = outward[nearest].getOpposite();
            current.addProperty("door", door.toShortString());
            current.addProperty("approach", approach.toShortString());
            player.teleportTo(city, approach.getX() + .5, approach.getY(), approach.getZ() + .5, inward.toYRot(), 12);
            return true;
        }
        return false;
    }

    private static void finishTarget(String result) throws Exception {
        signal("SURVEY_STOP:" + target.name());
        current.addProperty("result", result);
        observations.add(current);
        System.out.println("NIGHT_CITY_SURVEY " + current);
        next();
        if (done) {
            liveConfig.close();
            JsonObject report = new JsonObject();
            report.addProperty("scope", "one generated entrance per original building template; natural entities observed for 220 ticks per accessible sample");
            report.addProperty("surveyConfiguration", "Creative viewer, city spawning enabled, random door exits disabled only during the entrance survey");
            report.addProperty("templatesLocated", targets.size()); report.addProperty("nativeEntrancesPassed", walked);
            report.addProperty("allFloorsVerified", false);
            report.addProperty("threeMonsterIntegrationVerified", false);
            report.add("observations", observations); report.add("nativeTrajectory", trajectory);
            Files.writeString(RESULTS.resolve("city-survey.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.writeString(RESULTS.resolve("server-survey.complete"), "COLLECTED city survey: " + walked + "/" + targets.size()
                    + " entrances crossed; consult deviations in city-survey.json; not full building/monster acceptance\n");
            signal("SURVEY_DONE");
        }
    }

    private static List<Target> locate(long seed) {
        Map<String, Target> found = new LinkedHashMap<>();
        for (int bx = -12; bx <= 12; bx++) for (int bz = -12; bz <= 12; bz++) {
            boolean large = Math.floorMod(CityLayout.hash(seed, bx, bz), 4) == 0;
            for (int lx = 1; lx <= 3; lx++) for (int lz = 1; lz <= 3; lz++) {
                String name;
                if (large && lx <= 2 && lz <= 2) {
                    if (lx != 1 || lz != 1) continue;
                    String[] pool = {"mall", "civic_center", "townhall", "library"};
                    name = pool[(int) Math.floorMod(CityLayout.hash(seed + 1, bx, bz), pool.length)];
                } else {
                    if (lx == 3 && lz == 3 && CityTemplates.hasStation(seed, bx, bz)) continue;
                    if (lx == 3 && lz == 1 && Math.floorMod(CityLayout.hash(seed + 3, bx, bz), 4) == 0) continue;
                    name = CityLayout.building(seed, bx * 4 + lx, bz * 4 + lz);
                }
                int x = (bx * 4 + lx) * 16, z = (bz * 4 + lz) * 16;
                found.putIfAbsent(name, new Target(name, new BlockPos(x, CityLayout.plateau(bx, bz), z), CityLayout.district(x, z)));
            }
        }
        if (found.size() != CityTemplates.BUILDINGS.size()) throw new AssertionError("could not locate every original building class");
        return List.copyOf(found.values());
    }

    private static void signal(String text) { player.sendSystemMessage(Component.literal("NIGHT_QA_" + text)); }
}
