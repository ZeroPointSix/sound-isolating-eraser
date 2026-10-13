package com.zeropointsix.eraser.gametest;

import com.google.gson.JsonParser;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import com.zeropointsix.eraser.yuji.city.*;
import java.io.InputStreamReader;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import com.zeropointsix.eraser.yuji.city.RainCityEvents;
import com.zeropointsix.eraser.yuji.city.RecliningBuddha;
import com.zeropointsix.eraser.yuji.city.RainCityRules;
import com.zeropointsix.eraser.yuji.city.RainCityTemplates;
import com.zeropointsix.eraser.yuji.city.RainCityData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class RainCityGameTests {
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityBlocksCoverEverySuppliedState(GameTestHelper h) {
        h.assertTrue(RainCityContent.MURAL.get().getStateDefinition().getPossibleStates().size() == 432, "432 mural states");
        h.assertTrue(RainCityContent.CORPSE.get().getStateDefinition().getPossibleStates().size() == 8, "eight corpse states");
        var unlit = RainCityContent.LIGHT.get().defaultBlockState();
        h.assertTrue(unlit.getLightEmission() == 0 && unlit.setValue(RainCityBlocks.HolyLightBlock.LIT, true).getLightEmission() == 15, "bell lighting");
        h.assertTrue(RainCityContent.MARKS.size() == 10 && !RainCityContent.MARKS.containsKey(YujiKind.SHIFOTOU), "boss is not an exit requirement");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 400)
    public static void cityAllTemplatesAndPalettesResolve(GameTestHelper h) throws Exception {
        int count = 0;
        try (var stream = RainCityGameTests.class.getResourceAsStream("/data/qihai/yuji_city_manifest.json")) {
            var files = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject().getAsJsonObject("files");
            for (String path : files.keySet()) if (path.endsWith(".nbt")) {
                count++;
                String name = path.substring("data/qihai/structures/".length(), path.length() - 4);
                h.assertTrue(h.getLevel().getStructureManager().get(YujiContent.id(name)).isPresent(), "template " + name);
                try (var nbtStream = RainCityGameTests.class.getResourceAsStream("/" + path)) {
                    CompoundTag nbt = NbtIo.readCompressed(nbtStream);
                    h.assertTrue(nbt.getInt("DataVersion") == 3465, "1.20.1 structure " + name);
                    for (Tag t : nbt.getList("palette", Tag.TAG_COMPOUND)) {
                        CompoundTag state = (CompoundTag) t;
                        var id = new ResourceLocation(state.getString("Name"));
                        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "registered palette " + id);
                        var block = BuiltInRegistries.BLOCK.get(id);
                        CompoundTag properties = state.getCompound("Properties");
                        for (String key : properties.getAllKeys()) {
                            var property = block.getStateDefinition().getProperty(key);
                            h.assertTrue(property != null && property.getValue(properties.getString(key)).isPresent(), "palette property " + id + "/" + key);
                        }
                    }
                }
            }
        }
        h.assertTrue(count == 48, "all 48 structures"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityLootAndDimensionResolve(GameTestHelper h) {
        for (String name : new String[]{"common", "grocery", "hardware", "hospital", "mall", "pharmacy", "supply_drop"})
            h.assertTrue(h.getLevel().getServer().getLootData().getLootTable(YujiContent.id("chests/yuji_" + name)) != LootTable.EMPTY, "loot " + name);
        var city = h.getLevel().getServer().getLevel(RainCityContent.LEVEL);
        h.assertTrue(city != null && city.getChunkSource().getGenerator() instanceof RainCityGenerator, "dedicated city generator"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void citySaveRoundTripAndSceneBoundary(GameTestHelper h) {
        RainCityData data = new RainCityData(); UUID id = UUID.randomUUID();
        CompoundTag state = new CompoundTag(); state.putInt("Kills", 50); state.putInt("Marks", 1023); state.putInt("Horde", 130);
        state.putLong("Entered", 800); state.putBoolean("Active", true); data.players.put(id, state);
        data.bellAt = 5000; data.guardiansSpawned = true; data.buddha = UUID.randomUUID();
        RainCityData copy = RainCityData.load(data.save(new CompoundTag()));
        CompoundTag expected = state.copy(); expected.putUUID("Id", id);
        h.assertTrue(copy.players.get(id).equals(expected), "complete progress persisted");
        h.assertTrue(!copy.open(5091) && copy.open(5092), "animation completes before exit");
        h.assertTrue(copy.buddha.equals(data.buddha) && copy.guardiansSpawned, "scene identity survives restart"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityBellNeedsTenPhysicalMarksAndFiftyKills(GameTestHelper h) {
        var player = h.makeMockSurvivalPlayer(); CompoundTag state = new CompoundTag(); state.putInt("Kills", 50); state.putInt("Marks", 1023);
        h.assertTrue(!RainCityEvents.eligible(player, state), "no marks");
        for (var mark : RainCityContent.MARKS.values()) player.getInventory().add(new ItemStack(mark.get()));
        h.assertTrue(RainCityEvents.eligible(player, state), "all ten and fifty");
        state.putInt("Kills", 49); h.assertTrue(!RainCityEvents.eligible(player, state), "49 kills rejected");
        state.putInt("Kills", 50); player.getInventory().clearContent(); h.assertTrue(!RainCityEvents.eligible(player, state), "lost marks rejected"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityBorrowedMarksCannotReplacePersonalKills(GameTestHelper h) {
        var player = h.makeMockSurvivalPlayer();
        CompoundTag state = new CompoundTag(); state.putInt("Kills", 50);
        for (var mark : RainCityContent.MARKS.values()) player.getInventory().add(new ItemStack(mark.get()));
        h.assertTrue(!RainCityEvents.eligible(player, state), "borrowed marks without personal unlocks cannot ring the bell");
        for (YujiKind kind : RainCityContent.MARKS.keySet()) {
            state.putInt("Marks", 1023 & ~(1 << kind.ordinal()));
            h.assertTrue(!RainCityEvents.eligible(player, state), "personal kill still required for " + kind.id());
        }
        state.putInt("Marks", 1023);
        h.assertTrue(RainCityEvents.eligible(player, state), "physical marks and all personal kills permit the bell");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityGrowthIsIdempotentAndDoesNotHeal(GameTestHelper h) {
        YujiMonster mob = h.spawn(YujiContent.MOBS.get(YujiKind.YUCHI).get(), new BlockPos(2, 2, 2));
        mob.setNoAi(true); mob.setNoGravity(true); mob.setHealth(15);
        RainCitySpawns.grow(mob, 20); RainCitySpawns.grow(mob, 20);
        h.assertTrue(Math.abs(mob.getMaxHealth() - 60) < .01 && Math.abs(mob.getHealth() - 30) < .01, "modifier does not stack and preserves damage ratio");
        RainCitySpawns.grow(mob, 100);
        h.assertTrue(Math.abs(mob.getMaxHealth() - 60) < .01, "growth cap"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityHospitalHasSafeBedArrival(GameTestHelper h) {
        var city = h.getLevel().getServer().getLevel(RainCityContent.LEVEL);
        for (int x = 5; x <= 6; x++) for (int z = 5; z <= 6; z++) city.getChunk(x, z);
        BlockPos arrival = RainCityEntry.arrival(city);
        h.assertTrue(arrival != null && arrival.getY() > 128 && arrival.getY() < 134, "safe first-floor hospital arrival"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityHospitalArrivalSkipsFloodedBedside(GameTestHelper h) {
        ServerLevel city = city(h);
        for (int x = 5; x <= 6; x++) for (int z = 5; z <= 6; z++) city.getChunk(x, z);
        BlockPos original = RainCityEntry.arrival(city);
        h.assertTrue(original != null, "hospital begins with a safe arrival");
        BlockState before = city.getBlockState(original);
        try {
            city.setBlock(original, Blocks.WATER.defaultBlockState(), 2);
            BlockPos replacement = RainCityEntry.arrival(city);
            h.assertTrue(replacement != null && !replacement.equals(original), "flooded first choice must be skipped");
            h.assertTrue(RainCityPlacement.safe(city, Vec3.atBottomCenterOf(replacement)), "replacement must be safe for the full player");
        } finally { city.setBlock(original, before, 2); }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void citySafetyRejectsFeetHeadAndEdgeLiquids(GameTestHelper h) {
        ServerLevel level = h.getLevel(); BlockPos feet = h.absolutePos(new BlockPos(2, 2, 2));
        var saved = snapshot(level, feet.below(), feet, feet.above(), feet.east(), feet.east().above());
        try {
            level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 2);
            for (BlockPos pos : new BlockPos[]{feet, feet.above(), feet.east(), feet.east().above()})
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            Vec3 center = Vec3.atBottomCenterOf(feet);
            h.assertTrue(RainCityPlacement.safe(level, center), "dry supported fixture is safe");
            level.setBlock(feet, Blocks.WATER.defaultBlockState(), 2);
            h.assertTrue(!RainCityPlacement.safe(level, center), "feet water must fail despite an empty collision shape");
            level.setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(feet.above(), Blocks.LAVA.defaultBlockState(), 2);
            h.assertTrue(!RainCityPlacement.safe(level, center), "head lava must fail");
            level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(feet.east(), Blocks.WATER.defaultBlockState(), 2);
            h.assertTrue(!RainCityPlacement.safe(level, center.add(.45, 0, 0)), "fluid touching the body edge must fail");
            level.setBlock(feet.east(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(feet.above(), Blocks.STONE.defaultBlockState(), 2);
            h.assertTrue(!RainCityPlacement.safe(level, center), "head collision must fail");
        } finally { restore(level, saved); }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void citySafetyRequiresFullBorderAndBuildHeight(GameTestHelper h) {
        ServerLevel level = h.getLevel(); var border = level.getWorldBorder();
        Vec3 partial = new Vec3(border.getMinX() + .1, 100, border.getCenterZ());
        h.assertTrue(!RainCityPlacement.safe(level, partial), "center inside the border is insufficient when the body crosses it");
        h.assertTrue(!RainCityPlacement.safe(level, new Vec3(0, level.getMaxBuildHeight() - 1, 0)), "head must stay below the build ceiling");
        h.assertTrue(!RainCityPlacement.safe(level, new Vec3(0, level.getMinBuildHeight() - 1, 0)), "feet must stay above the build floor");
        h.assertTrue(!RainCityPlacement.safe(level, new Vec3(Double.NaN, 100, 0)), "invalid saved coordinates must be rejected");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityReturnRejectsUnsafeFallbackAndFindsSafeReplacement(GameTestHelper h) {
        ServerLevel level = h.getLevel(); BlockPos feet = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos fallback = new BlockPos(feet.getX() + 2, level.getMaxBuildHeight() - 2, feet.getZ());
        var saved = snapshot(level, feet.below(), feet, feet.above(), fallback.below(), fallback, fallback.above());
        try {
            level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(feet, Blocks.WATER.defaultBlockState(), 2);
            level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 2);
            Vec3 preferred = Vec3.atBottomCenterOf(feet);
            BlockPos outside = new BlockPos((int) Math.ceil(level.getWorldBorder().getMaxX()) + 32, 100, 0);
            h.assertTrue(RainCityPlacement.home(level, preferred, outside) == null, "no safe destination must remain a failure, not a forced teleport");
            level.setBlock(fallback.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(fallback, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(fallback.above(), Blocks.AIR.defaultBlockState(), 2);
            Vec3 replacement = RainCityPlacement.home(level, preferred, fallback);
            h.assertTrue(Vec3.atBottomCenterOf(fallback).equals(replacement), "invalid saved point must use the validated fallback");
            h.assertTrue(RainCityPlacement.safe(level, replacement), "fallback receives the same complete safety checks");
        } finally { restore(level, saved); }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityDeathRecoveryFromNetherPreservesProgressAndCancelsCleanly(GameTestHelper h) {
        ServerLevel city = city(h); RainCityData data = RainCityData.get(city);
        ServerPlayer player = netherPlayer(h); CompoundTag state = recoveryState();
        data.players.put(player.getUUID(), state);
        try {
            h.assertTrue(!RainCityEntry.request(player), "ordinary enter remains overworld-only");
            state.putBoolean("Active", false);
            h.assertTrue(!RainCityEntry.recoverAfterDeath(player), "death recovery needs an existing active run");
            state.putBoolean("Active", true);
            h.assertTrue(RainCityEntry.recoverAfterDeath(player), "an active Nether respawn may queue hospital recovery");
            assertRecoveryProgress(h, data.players.get(player.getUUID()));
            // FakePlayer is not in the connected player list: exercise asynchronous identity-loss cleanup.
            RainCityEntry.tick(city.getServer());
            h.assertTrue(!state.getBoolean("Active"), "an abandoned recovery must not hijack a later unrelated respawn");
            assertRecoveryProgress(h, state);
            h.assertTrue(!RainCityEntry.recoverAfterDeath(player), "cancelled recovery must not silently queue again");
        } finally { RainCityEntry.cancel(player.getUUID()); data.players.remove(player.getUUID()); data.setDirty(); }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityRejectedDeathRecoveryClearsActiveButKeepsProgress(GameTestHelper h) {
        ServerLevel city = city(h); RainCityData data = RainCityData.get(city);
        ServerPlayer player = netherPlayer(h); CompoundTag state = recoveryState(); long bell = data.bellAt;
        data.players.put(player.getUUID(), state);
        try {
            data.bellAt = city.getGameTime();
            h.assertTrue(!RainCityEntry.recoverAfterDeath(player), "entry is rejected while the bell scene is still running");
            h.assertTrue(!state.getBoolean("Active"), "synchronous rejection must clear the active retry state");
            assertRecoveryProgress(h, state);
        } finally { RainCityEntry.cancel(player.getUUID()); data.players.remove(player.getUUID()); data.bellAt = bell; data.setDirty(); }
        h.succeed();
    }
    private static ServerPlayer netherPlayer(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "Nether dimension is available for respawn recovery");
        return net.minecraftforge.common.util.FakePlayerFactory.get(nether,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CityRecovery"));
    }
    private static CompoundTag recoveryState() {
        CompoundTag state = new CompoundTag(); state.putBoolean("Active", true);
        state.putInt("Kills", 37); state.putInt("Marks", 511); state.putLong("Entered", 1234);
        state.putDouble("HomeX", 91.5); state.putDouble("HomeY", 72); state.putDouble("HomeZ", -34.5);
        return state;
    }
    private static void assertRecoveryProgress(GameTestHelper h, CompoundTag state) {
        h.assertTrue(state.getInt("Kills") == 37 && state.getInt("Marks") == 511 && state.getLong("Entered") == 1234,
                "recovery keeps kills, personal marks and elapsed-time origin");
        h.assertTrue(state.getDouble("HomeX") == 91.5 && state.getDouble("HomeY") == 72 && state.getDouble("HomeZ") == -34.5,
                "recovery keeps the original overworld home, never the Nether respawn coordinates");
    }
    private static java.util.Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos... positions) {
        var saved = new java.util.LinkedHashMap<BlockPos, BlockState>();
        for (BlockPos pos : positions) saved.put(pos, level.getBlockState(pos));
        return saved;
    }
    private static void restore(ServerLevel level, java.util.Map<BlockPos, BlockState> saved) {
        saved.forEach((pos, state) -> level.setBlock(pos, state, 2));
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityHospitalBedsKeepTheirMatchingHeadAndFoot(GameTestHelper h) {
        ServerLevel city = city(h);
        for (int x = 5; x <= 6; x++) for (int z = 5; z <= 6; z++) city.getChunk(x, z);
        BlockPos origin = RainCityTemplates.HOSPITAL;
        int heads = 0, feet = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(31, 29, 31))) {
            var state = city.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;
            Direction facing = state.getValue(BedBlock.FACING);
            BedPart part = state.getValue(BedBlock.PART);
            BlockPos partnerPos = pos.relative(part == BedPart.FOOT ? facing : facing.getOpposite());
            var partner = city.getBlockState(partnerPos);
            h.assertTrue(partner.is(state.getBlock()), "matching bed block at " + pos + " -> " + partnerPos);
            h.assertTrue(partner.getValue(BedBlock.FACING) == facing, "bed halves face the same direction at " + pos);
            h.assertTrue(partner.getValue(BedBlock.PART) != part, "bed connects a head to a foot at " + pos);
            if (part == BedPart.HEAD) heads++; else feet++;
        }
        h.assertTrue(heads == 50 && feet == 50,
                "all five hospital floors retain 50 complete beds; heads=" + heads + ", feet=" + feet);
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityRitualRoomsFitFullSizeXieyin(GameTestHelper h) {
        ServerLevel city = city(h);
        for (BlockPos pos : RainCityTemplates.RITUAL_GUARDIANS) {
            YujiMonster mob = YujiContent.MOBS.get(YujiKind.XIEYIN).get().create(city);
            h.assertTrue(mob != null, "Xieyin entity can be created");
            mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            h.assertTrue(Math.abs(mob.getBbHeight() - 6.3F) < .001F,
                    "room test must use the full 6.3-block Xieyin, not a reduced collision box");
            loadCollisionChunks(city, mob);
            h.assertTrue(city.getBlockState(pos.below()).isFaceSturdy(city, pos.below(), Direction.UP),
                    "ritual room has a supporting floor below " + pos);
            h.assertTrue(city.noCollision(mob), "full-size Xieyin collides at " + pos + ": " + mob.getBoundingBox());
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityAllSevenBottomGuardianPositionsAreClear(GameTestHelper h) {
        ServerLevel city = city(h);
        h.assertTrue(RainCityTemplates.BOTTOM_GUARDIANS.size() == 7, "seven distinct bottom guardians");
        for (int i = 0; i < 7; i++) {
            BlockPos pos = RainCityTemplates.BOTTOM_GUARDIANS.get(i);
            YujiMonster mob = YujiContent.MOBS.get(YujiKind.YUCHI).get().create(city);
            h.assertTrue(mob != null, "Yuchi guardian entity can be created");
            mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            loadCollisionChunks(city, mob);
            h.assertTrue(city.getBlockState(pos.below()).isFaceSturdy(city, pos.below(), Direction.UP), "guardian standing floor " + pos);
            h.assertTrue(city.noCollision(mob), "bottom guardian " + i + " collides at " + pos + ": " + mob.getBoundingBox());
        }
        h.succeed();
    }
    private static ServerLevel city(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(RainCityContent.LEVEL);
        h.assertTrue(city != null, "rain city dimension is available");
        return city;
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityWhiteRobePersistsWithoutChangingOtherKinds(GameTestHelper h) {
        YujiMonster original = YujiContent.MOBS.get(YujiKind.YUCHI).get().create(h.getLevel());
        original.setWhiteRobe(true);
        CompoundTag tag = new CompoundTag(); original.addAdditionalSaveData(tag);
        YujiMonster restored = YujiContent.MOBS.get(YujiKind.YUCHI).get().create(h.getLevel());
        restored.readAdditionalSaveData(tag);
        h.assertTrue(restored.whiteRobe(), "white robe survives reload and uses synced entity data");
        YujiMonster other = YujiContent.MOBS.get(YujiKind.SHASHENG).get().create(h.getLevel());
        other.setWhiteRobe(true); h.assertTrue(!other.whiteRobe(), "only Yuchi has the guardian variant"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityHordeKeepsItsDistantTarget(GameTestHelper h) {
        ServerLevel city = city(h);
        var target = net.minecraftforge.common.util.FakePlayerFactory.get(city, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CityWaveTarget"));
        target.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); target.moveTo(80, 220, 0);
        for (YujiKind kind : new YujiKind[]{YujiKind.YUCHI, YujiKind.CHENNU}) {
            YujiMonster mob = YujiContent.MOBS.get(kind).get().create(city);
            mob.moveTo(0, 220, 0); mob.setNoGravity(true); mob.getPersistentData().putBoolean("YujiHorde", true);
            loadCollisionChunks(city, mob); mob.setTarget(target); mob.tick();
            h.assertTrue(mob.getTarget() == target, "horde retains its known player at 80 blocks for " + kind.id());
            mob.discard();
        }
        h.succeed();
    }
    private static void loadCollisionChunks(ServerLevel city, YujiMonster mob) {
        var box = mob.getBoundingBox();
        int minX = Math.floorDiv((int) Math.floor(box.minX) - 1, 16);
        int maxX = Math.floorDiv((int) Math.floor(box.maxX) + 1, 16);
        int minZ = Math.floorDiv((int) Math.floor(box.minZ) - 1, 16);
        int maxZ = Math.floorDiv((int) Math.floor(box.maxZ) + 1, 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) city.getChunk(x, z);
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityClockLivesInSavedData(GameTestHelper h) {
        var city = h.getLevel().getServer().getLevel(RainCityContent.LEVEL);
        long before = city.getDayTime();
        city.setDayTime(before + 12345);
        // Extra dimensions wrap the overworld's level data; this is why the city keeps its own clock.
        h.assertTrue(city.getDayTime() == h.getLevel().getServer().overworld().getDayTime(), "city level time mirrors the overworld");
        RainCityData data = new RainCityData(); data.cityTime = RainCityRules.FIRST_NIGHT + 9 * RainCityRules.DAY;
        RainCityData copy = RainCityData.load(data.save(new CompoundTag()));
        h.assertTrue(copy.cityTime == data.cityTime && RainCityRules.day(copy.cityTime) == 10, "city clock survives restart at day ten");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city")
    public static void cityMissingTemplateIsSkipped(GameTestHelper h) {
        var manager = h.getLevel().getStructureManager();
        h.assertTrue(RainCityTemplates.lookup(manager, "yuji/does_not_exist").isEmpty(), "missing template resolves empty without throwing");
        h.assertTrue(RainCityTemplates.lookup(manager, "yuji/buildings/hospital").isPresent(), "real template still resolves");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city", timeoutTicks = 1200)
    public static void cityBuddhaRespawnsAfterRemoval(GameTestHelper h) {
        var city = h.getLevel().getServer().getLevel(RainCityContent.LEVEL);
        city.getChunk(0, 0);
        RainCityData data = new RainCityData();
        UUID[] first = new UUID[1];
        h.succeedWhen(() -> {
            RainCityEvents.ensureBuddha(city, data);
            h.assertTrue(data.buddha != null, "statue spawned or adopted");
            if (first[0] == null) {
                first[0] = data.buddha;
                for (var b : city.getEntitiesOfClass(RecliningBuddha.class, new net.minecraft.world.phys.AABB(new BlockPos(0, 123, 0)).inflate(16), e -> true)) b.discard();
                RainCityEvents.ensureBuddha(city, data);
            }
            h.assertTrue(!data.buddha.equals(first[0]), "removed statue is replaced");
            var alive = city.getEntitiesOfClass(RecliningBuddha.class, new net.minecraft.world.phys.AABB(new BlockPos(0, 123, 0)).inflate(16), RecliningBuddha::isAlive);
            h.assertTrue(alive.size() == 1 && alive.get(0).getUUID().equals(data.buddha), "exactly one live statue");
            RainCityEvents.ensureBuddha(city, data);
            h.assertTrue(city.getEntitiesOfClass(RecliningBuddha.class, new net.minecraft.world.phys.AABB(new BlockPos(0, 123, 0)).inflate(16), RecliningBuddha::isAlive).size() == 1, "no duplicate on later ticks");
        });
    }
    private RainCityGameTests() { }
}
