package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.brickrot.BrickrotContent;
import com.zeropointsix.eraser.brickrot.BrickrotEmergence;
import com.zeropointsix.eraser.brickrot.BrickrotPart;
import com.zeropointsix.eraser.brickrot.BrickrotTrail;
import com.zeropointsix.eraser.brickrot.BrickrotWallEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.ForgeSpawnEggItem;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BrickrotGameTests {
    private static int targetFixtureY(GameTestHelper h) {
        // Relative +100 can still sit inside a generated city. Keep the encounter
        // near the absolute build ceiling, outside terrain and ground-level lures.
        return h.getLevel().getMaxBuildHeight() - 16 - h.absolutePos(BlockPos.ZERO).getY();
    }

    private static BrickrotWallEntity wall(GameTestHelper h) {
        return wall(h, 4);
    }

    private static BrickrotWallEntity wall(GameTestHelper h, int y) {
        for (int x = 29; x <= 35; x++) for (int z = 0; z < 63; z++)
            h.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE);
        BrickrotWallEntity wall = h.spawn(BrickrotContent.WALL.get(), new BlockPos(32, y, 25));
        wall.setNoAi(true);
        wall.setNoGravity(true);
        wall.setYRot(0);
        return wall;
    }

    private static BrickrotWallEntity targetFixtureWall(GameTestHelper h, Map<BlockPos, BlockState> savedBlocks) {
        int y = targetFixtureY(h);
        int floorY = h.absolutePos(new BlockPos(32, y - 1, 25)).getY();
        int topY = h.absolutePos(new BlockPos(32, y + 8, 25)).getY();
        h.assertTrue(floorY >= h.getLevel().getMinBuildHeight() && topY < h.getLevel().getMaxBuildHeight(),
                "isolated target fixture must fit inside the world's build height");
        for (int x = 29; x <= 35; x++) for (int z = 0; z < 63; z++) {
            BlockPos pos = h.absolutePos(new BlockPos(x, y - 1, z));
            savedBlocks.putIfAbsent(pos, h.getLevel().getBlockState(pos));
            // A raised relative Y can still be underground; keep bodies and attack rays unobstructed.
            for (int offset = 0; offset <= 8; offset++) {
                setFixtureBlock(h, savedBlocks, h.absolutePos(new BlockPos(x, y + offset, z)),
                        Blocks.AIR.defaultBlockState());
            }
        }
        return wall(h, y);
    }

    private static void setFixtureBlock(GameTestHelper h, Map<BlockPos, BlockState> savedBlocks,
            BlockPos pos, BlockState state) {
        savedBlocks.putIfAbsent(pos, h.getLevel().getBlockState(pos));
        h.getLevel().setBlock(pos, state, 3);
    }

    private static void restoreFixtureBlocks(GameTestHelper h, Map<BlockPos, BlockState> savedBlocks) {
        savedBlocks.forEach((pos, state) -> h.getLevel().setBlock(pos, state, 3));
    }

    private static DamageSource damage(GameTestHelper h, ResourceKey<DamageType> type) {
        return new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    private static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.02, message + ": expected " + expected + ", got " + actual);
    }

    private static float hit(BrickrotWallEntity wall, int part, DamageSource source, float amount) {
        wall.invulnerableTime = 0;
        float before = wall.getHealth();
        wall.hurtPart(part, source, amount);
        return before - wall.getHealth();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotMultipartRegistration(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        near(h, wall.getMaxHealth(), 300, "boss health");
        h.assertTrue(wall.isMultipartEntity() && wall.getParts().length == 9, "one head plus nine parts");
        for (int i = 0; i < 9; i++) {
            BrickrotPart part = wall.getParts()[i];
            h.assertTrue(part.getParent() == wall && part.getId() == wall.getId() + i + 1, "stable parent and packet ids");
            h.assertTrue(!part.shouldBeSaved(), "parts never become independently saved entities");
        }
        h.assertTrue(((ForgeSpawnEggItem) BrickrotContent.EGG.get()).getType(null) == BrickrotContent.WALL.get(),
                "egg creates the boss");
        h.runAfterDelay(2, () -> {
            for (BrickrotPart part : wall.getParts())
                h.assertTrue(h.getLevel().getEntityOrPart(part.getId()) == part
                        && ServerboundInteractPacket.createAttackPacket(part, false).getTarget(h.getLevel()) == part,
                        "multipart lookup resolves the same part used by attack packets");
            wall.discard();
            for (BrickrotPart part : wall.getParts())
                h.assertTrue(h.getLevel().getEntityOrPart(part.getId()) == null, "discarding head removes every part lookup");
            h.succeed();
        });
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotEggEmergesBeforeScanning(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        Vec3 surface = wall.position();
        wall.finalizeSpawn(h.getLevel(), h.getLevel().getCurrentDifficultyAt(wall.blockPosition()),
                MobSpawnType.SPAWN_EGG, null, null);
        wall.setNoAi(false);
        wall.tick();
        h.assertTrue(wall.action() == BrickrotWallEntity.Action.EMERGE && wall.getY() < surface.y,
                "egg starts below the clicked surface with the original emergence animation");
        for (int i = 0; i < 60; i++) wall.tick();
        h.assertTrue(wall.action() == BrickrotWallEntity.Action.SCAN, "opening finishes in scan");
        near(h, wall.getY(), surface.y, "opening returns to the clicked surface");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotEggUsesClickingPlayerHeading(GameTestHelper h) {
        BlockPos floor = h.absolutePos(new BlockPos(32, 3, 32));
        h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        var player = h.makeMockSurvivalPlayer();
        var egg = (ForgeSpawnEggItem) BrickrotContent.EGG.get();
        h.assertTrue(egg.getColor(0) == 0x8E3B2E && egg.getColor(1) == 0x80807C,
                "spawn egg uses the supplied design colors");
        for (float yaw : new float[] {90, -135}) {
            player.setPos(Vec3.atCenterOf(floor).add(0, 1, -4));
            player.setYRot(yaw);
            player.setXRot(37);
            ItemStack stack = new ItemStack(egg, 2);
            stack.getOrCreateTag().putString("BrickrotTestMarker", "keep");
            CompoundTag entityTag = stack.getOrCreateTagElement("EntityTag");
            entityTag.putFloat("Health", 240);
            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(12));
            rotation.add(FloatTag.valueOf(17));
            entityTag.put("Rotation", rotation);
            CompoundTag before = stack.getTag().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            h.assertTrue(egg.useOn(context).consumesAction(), "ground egg interaction succeeds");
            h.assertTrue(stack.getCount() == 1 && before.equals(stack.getTag()),
                    "vanilla consumes one egg without changing its original NBT");
            var spawned = h.getLevel().getEntitiesOfClass(BrickrotWallEntity.class,
                    new AABB(floor.above()).inflate(2));
            h.assertTrue(spawned.size() == 1, "one click creates exactly one nearby head");
            BrickrotWallEntity head = spawned.get(0);
            near(h, head.getYRot(), yaw, "head uses the actual clicking player's nonzero yaw");
            near(h, head.getXRot(), 17, "unrelated entity rotation pitch is preserved");
            near(h, head.getHealth(), 240, "unrelated entity NBT survives vanilla loading");
            head.tick();
            h.assertTrue(head.action() == BrickrotWallEntity.Action.EMERGE, "first tick keeps the emergence opening");
            // During emergence the trail rises vertically; test the fully unfolded body after settling.
            for (int i = 0; i < 60; i++) head.tick();
            h.assertTrue(head.action() == BrickrotWallEntity.Action.SCAN, "opening settles before the direction check");
            near(h, head.getYRot(), yaw, "opening preserves the clicking player's yaw");
            Vec3 forward = Vec3.directionFromRotation(0, yaw);
            for (BrickrotPart part : head.getParts()) {
                Vec3 behind = part.position().subtract(head.position()).multiply(1, 0, 1).normalize();
                h.assertTrue(behind.dot(forward) < -0.99, "every unfolded part trails opposite the player's heading");
            }
            head.discard();
        }
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotMeleeAndBreachedNeck(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        DamageSource source = damage(h, DamageTypes.GENERIC);
        near(h, hit(wall, 0, source, 10), 10, "head damage");
        near(h, hit(wall, 1, source, 10), 10, "intact neck damage");
        near(h, hit(wall, 4, source, 10), 5, "body damage");
        wall.setHealth(149);
        hit(wall, 0, source, 1);
        h.assertTrue(wall.phaseTwo(), "below half health breaches neck");
        near(h, hit(wall, 1, source, 10), 20, "breached neck damage");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotProjectileExplosionAndSharedCooldown(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        near(h, hit(wall, 4, damage(h, DamageTypes.ARROW), 16), 4, "projectile quarter damage overrides part multiplier");
        DamageSource explosion = damage(h, DamageTypes.EXPLOSION);
        near(h, hit(wall, 4, explosion, 10), 20, "explosion double damage");
        float before = wall.getHealth();
        for (BrickrotPart part : wall.getParts()) part.hurt(explosion, 10);
        near(h, wall.getHealth(), before, "one explosion cannot damage all parts separately");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotImmunitiesAndStagger(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        near(h, hit(wall, 0, damage(h, DamageTypes.LAVA), 20), 0, "lava immunity");
        near(h, hit(wall, 0, damage(h, DamageTypes.IN_WALL), 20), 0, "wall suffocation immunity");
        wall.stagger();
        near(h, hit(wall, 0, damage(h, DamageTypes.GENERIC), 10), 15, "stagger amplifies damage");
        near(h, hit(wall, 4, damage(h, DamageTypes.ARROW), 16), 6, "stagger also amplifies projectiles");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotArcLengthTrail(GameTestHelper h) {
        BrickrotTrail trail = new BrickrotTrail();
        trail.reset(Vec3.ZERO, new Vec3(0, 0, 1));
        for (int i = 1; i <= 20; i++) trail.record(new Vec3(0, 0, i * 0.5), new Vec3(0, 0, 1));
        for (int i = 1; i <= 10; i++) trail.record(new Vec3(i, 0, 10), new Vec3(1, 0, 0));
        near(h, trail.sample(5).distanceTo(new Vec3(5, 0, 10)), 0, "straight distance sampling");
        near(h, trail.sample(15).distanceTo(new Vec3(0, 0, 5)), 0, "turn follows recorded path");
        trail.record(new Vec3(200, 0, 0), new Vec3(1, 0, 0));
        near(h, trail.sample(5).distanceTo(new Vec3(195, 0, 0)), 0, "teleport resets history");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotLightLureAndLineOfSight(GameTestHelper h) {
        // The 40-block lure scan must not include another parallel fixture's dropped items.
        var savedBlocks = new LinkedHashMap<BlockPos, BlockState>();
        BrickrotWallEntity wall = targetFixtureWall(h, savedBlocks);
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(32, targetFixtureY(h), 31));
        villager.setNoAi(true);
        villager.setNoGravity(true);
        var light = new ItemEntity(h.getLevel(), wall.getX() + 20, wall.getY(), wall.getZ(), new ItemStack(Items.TORCH));
        light.setNoGravity(true);
        light.setDeltaMovement(Vec3.ZERO);
        try {
            var baseline = h.getLevel().clip(new net.minecraft.world.level.ClipContext(wall.getEyePosition(),
                    villager.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, wall));
            System.out.println("BRICKROT_LURE_BASELINE_HIT " + baseline.getType() + " at=" + baseline.getBlockPos()
                    + " block=" + h.getLevel().getBlockState(baseline.getBlockPos()));
            for (int x = -2; x <= 2; x++) for (int y = 0; y <= 5; y++) for (int z = -1; z <= 8; z++)
                setFixtureBlock(h, savedBlocks, wall.blockPosition().offset(x, y, z), Blocks.AIR.defaultBlockState());
            h.getLevel().addFreshEntity(light);
            h.assertTrue(wall.findQuarry() == light, "dropped light outranks closer living target");
            setFixtureBlock(h, savedBlocks, wall.blockPosition().offset(10, 1, 0), Blocks.STONE.defaultBlockState());
            h.assertTrue(wall.detectable(light), "dropped light can lure around obstacles");
            light.discard();
            var selected = wall.findQuarry();
            System.out.println("BRICKROT_LURE_SELECTED actual=" + selected + " expected=" + villager
                    + " detectable=" + wall.detectable(villager) + " distance=" + wall.distanceTo(villager)
                    + " alive=" + villager.isAlive() + " eyes=" + wall.getEyePosition() + "/" + villager.getEyePosition());
            h.assertTrue(selected == villager, "living target is acquired without lure: " + selected);
            for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++)
                setFixtureBlock(h, savedBlocks, wall.blockPosition().offset(x, y, 3), Blocks.STONE.defaultBlockState());
            h.assertTrue(!wall.detectable(villager), "living target requires line of sight");
            h.succeed();
        } finally {
            wall.discard(); villager.discard(); light.discard();
            restoreFixtureBlocks(h, savedBlocks);
        }
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotHeldLightRange(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(32, 4, 55));
        villager.setNoAi(true);
        villager.setNoGravity(true);
        h.assertTrue(!wall.detectable(villager), "unlit targets beyond 24 are hidden even in daylight");
        villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LANTERN));
        h.assertTrue(wall.detectable(villager), "offhand light extends range to 40");
        h.assertTrue(!BrickrotWallEntity.luminous(new ItemStack(Items.DIRT)), "ordinary items do not glow");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty", timeoutTicks = 140)
    public static void brickrotHardImpactAndRecovery(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        h.getLevel().setBlock(wall.blockPosition().offset(0, 0, 2), Blocks.BASALT.defaultBlockState(), 3);
        wall.setNoAi(false);
        wall.beginCharge(wall.position().add(0, 0, 20));
        h.runAfterDelay(3, () -> h.assertTrue(wall.action() == BrickrotWallEntity.Action.STAGGER, "basalt causes stagger"));
        h.runAfterDelay(95, () -> h.assertTrue(wall.action() == BrickrotWallEntity.Action.STAGGER, "stagger lasts five seconds"));
        h.runAfterDelay(104, () -> {
            h.assertTrue(wall.action() == BrickrotWallEntity.Action.SCAN, "stagger recovers into scan");
            h.succeed();
        });
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotOrdinaryWallStopsWithoutStagger(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        BlockPos barrier = wall.blockPosition().offset(0, 0, 2);
        h.getLevel().setBlock(barrier, Blocks.STONE.defaultBlockState(), 3);
        wall.setNoAi(false);
        wall.beginCharge(wall.position().add(0, 0, 20));
        h.runAfterDelay(3, () -> {
            h.assertTrue(wall.action() == BrickrotWallEntity.Action.SCAN, "ordinary stone stops without stagger");
            h.assertTrue(h.getLevel().getBlockState(barrier).is(Blocks.STONE), "ordinary wall remains intact");
            h.succeed();
        });
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotChargeIsStraightAndBounded(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        Vec3 start = wall.position();
        wall.setNoAi(false);
        wall.beginCharge(start.add(0, 0, 20));
        h.runAfterDelay(10, () -> wall.setYRot(90));
        h.runAfterDelay(30, () -> {
            near(h, wall.getX(), start.x, "turning yaw cannot steer a charge");
            near(h, wall.getZ() - start.z, 24, "charge stops at 24 blocks");
            h.assertTrue(wall.action() == BrickrotWallEntity.Action.SCAN, "charge ends in scan");
            h.succeed();
        });
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotHeadOnlySaveAndReload(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        wall.setHealth(149);
        hit(wall, 0, damage(h, DamageTypes.GENERIC), 1);
        CompoundTag saved = new CompoundTag();
        wall.saveWithoutId(saved);
        h.assertTrue(!saved.contains("Passengers") && !saved.contains("Parts"), "only head state is serialized");
        BrickrotWallEntity loaded = BrickrotContent.WALL.get().create(h.getLevel());
        loaded.load(saved);
        near(h, loaded.getHealth(), 148, "health survives reload");
        h.assertTrue(loaded.phaseTwo() && loaded.getParts().length == 9, "phase and multipart layout survive reload");
        h.assertTrue(loaded.action() == BrickrotWallEntity.Action.SCAN, "reload starts a safe scan");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotMobGriefingAndFragileBlocks(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        BlockPos glass = wall.blockPosition().offset(0, 0, 2);
        h.getLevel().setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
        var rule = h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean original = rule.get();
        try {
            rule.set(false, h.getLevel().getServer());
            wall.setNoAi(false);
            wall.beginCharge(wall.position().add(0, 0, 20));
            wall.tick();
            h.assertTrue(h.getLevel().getBlockState(glass).is(Blocks.GLASS), "mobGriefing false protects glass");
            h.assertTrue(wall.action() == BrickrotWallEntity.Action.SCAN, "protected glass stops charge");
            rule.set(true, h.getLevel().getServer());
            wall.beginCharge(wall.position().add(0, 0, 20));
            wall.tick();
            h.assertTrue(h.getLevel().getBlockState(glass).isAir(), "glass is fragile when terrain damage is enabled");
        } finally {
            rule.set(original, h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template = "brickrot_burrow")
    public static void brickrotBurrowProtectionAndSaveRecovery(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h, 35);
        Vec3 surface = wall.position();
        h.assertTrue(surface.y - 26 > h.getLevel().getMinBuildHeight(), "fixture permits the full dive depth");
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(32, 35, 50));
        target.setNoAi(true);
        h.assertTrue(wall.beginBurrow(target), "safe emergence destination is accepted");
        near(h, hit(wall, 0, damage(h, DamageTypes.DROWN), 20), 0, "diving does not introduce drowning damage");
        wall.setNoAi(false);
        for (int i = 0; i < 65; i++) wall.tick();
        h.assertTrue(wall.underground(), "head and body enter underground stage");
        near(h, hit(wall, 0, damage(h, DamageTypes.GENERIC), 20), 0, "underground immunity");
        h.assertTrue(!wall.isPickable() && !wall.getParts()[0].isPickable(), "underground parts cannot be picked");
        h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(surface).below()).is(Blocks.STONE),
                "burrowing never destroys the entry floor");
        CompoundTag saved = new CompoundTag();
        wall.saveWithoutId(saved);
        BrickrotWallEntity loaded = BrickrotContent.WALL.get().create(h.getLevel());
        loaded.load(saved);
        near(h, loaded.position().distanceTo(surface), 0, "save during burrow reloads at safe surface");
        h.assertTrue(!loaded.noPhysics && !loaded.isNoGravity(), "reload restores ordinary collision and gravity");
        for (int i = 0; i < 120 && wall.action() != BrickrotWallEntity.Action.EMERGE; i++) wall.tick();
        h.assertTrue(wall.action() == BrickrotWallEntity.Action.EMERGE, "underground travel reaches the locked emergence point");
        for (int i = 0; i < 10; i++) wall.tick();
        near(h, target.getHealth(), 6, "eruption hits for fourteen damage");
        for (int i = 0; i < 50; i++) wall.tick();
        h.assertTrue(wall.action() == BrickrotWallEntity.Action.SCAN, "fully surfaced body resumes scanning");
        for (BrickrotPart part : wall.getParts())
            near(h, part.getY(), wall.getY(), "body is surfaced before scanning, without another body-length walk");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty", timeoutTicks = 100)
    public static void brickrotBiteHasWindupAndTwelveDamage(GameTestHelper h) {
        // findQuarry scans the shared level, where luminous drops outrank even nearby villagers.
        var savedBlocks = new LinkedHashMap<BlockPos, BlockState>();
        BrickrotWallEntity wall = targetFixtureWall(h, savedBlocks);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(32, targetFixtureY(h), 28));
        target.setNoAi(true);
        target.setNoGravity(true);
        var groundLure = new ItemEntity(h.getLevel(), wall.getX(),
                h.absolutePos(new BlockPos(32, 100, 25)).getY(), wall.getZ(), new ItemStack(Items.TORCH));
        groundLure.setNoGravity(true);
        groundLure.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(groundLure);
        try {
            h.assertTrue(groundLure.isAlive() && !wall.detectable(groundLure),
                    "luminous drops at the old relative +100 fixture stay outside the isolated encounter");
            var selected = wall.findQuarry();
            h.assertTrue(selected == target, "bite fixture selects its own target: " + selected);
            wall.setNoAi(false);
            // Like the sweep test, advance only this encounter rather than neighboring fixtures.
            for (int i = 0; i < 54; i++) wall.tick();
            h.assertTrue(wall.action() == BrickrotWallEntity.Action.BITE, "bite telegraph starts");
            near(h, target.getHealth(), 20, "bite does not hit before windup");
            for (int i = 0; i < 6; i++) wall.tick();
            near(h, target.getHealth(), 8, "bite hits for twelve");
            h.succeed();
        } finally {
            wall.discard(); target.discard(); groundLure.discard();
            restoreFixtureBlocks(h, savedBlocks);
        }
    }

    @GameTest(template = "brickrot_empty", timeoutTicks = 100)
    public static void brickrotSweepHitsOnlyOnce(GameTestHelper h) {
        var savedBlocks = new LinkedHashMap<BlockPos, BlockState>();
        BrickrotWallEntity wall = targetFixtureWall(h, savedBlocks);
        // Behind the head, inside the first red part's sweep radius, without a chase step.
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(33, targetFixtureY(h), 22));
        target.setNoAi(true);
        target.setNoGravity(true);
        try {
            wall.tick();
            h.assertTrue(wall.getParts()[3].distanceToSqr(target) < 36,
                    "fixture begins inside a red section's six-block sweep radius");
            var selected = wall.findQuarry();
            h.assertTrue(selected == target,
                    "fixture selects its own side target before advancing AI: " + selected);
            wall.setNoAi(false);
            // Advance only this encounter so neighboring parallel tests cannot move the fixture.
            for (int i = 0; i < 60; i++) wall.tick();
            h.assertTrue(wall.action() == BrickrotWallEntity.Action.SWEEP, "side target selects sweep");
            near(h, target.getHealth(), 20, "sweep telegraph precedes damage");
            for (int i = 0; i < 10; i++) wall.tick();
            near(h, target.getHealth(), 10, "four red sections apply a single ten-damage hit");
            h.succeed();
        } finally {
            wall.discard(); target.discard();
            restoreFixtureBlocks(h, savedBlocks);
        }
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotMovingBodyCrushesOnContact(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(32, 4, 15));
        target.setNoAi(true);
        wall.setNoAi(false);
        wall.beginCharge(wall.position().add(0, 0, 20));
        h.runAfterDelay(3, () -> near(h, target.getHealth(), 16, "short body contact is not missed between second boundaries"));
        h.runAfterDelay(10, () -> {
            near(h, target.getHealth(), 16, "overlapping parts do not multiply crushing damage");
            h.succeed();
        });
    }

    @GameTest(template = "brickrot_empty", timeoutTicks = 100)
    public static void brickrotCloseLureDoesNotRestartScan(GameTestHelper h) {
        var savedBlocks = new LinkedHashMap<BlockPos, BlockState>();
        BrickrotWallEntity wall = targetFixtureWall(h, savedBlocks);
        var light = new ItemEntity(h.getLevel(), wall.getX(), wall.getY(), wall.getZ() + 3, new ItemStack(Items.TORCH));
        light.setNoGravity(true);
        light.setDeltaMovement(Vec3.ZERO);
        light.setPickUpDelay(32767);
        h.getLevel().addFreshEntity(light);
        try {
            h.assertTrue(wall.findQuarry() == light, "lure fixture selects its own light");
            wall.setNoAi(false);
            for (int i = 0; i < 50; i++) wall.tick();
            for (int i = 0; i < 30; i++) {
                wall.tick();
                h.assertTrue(light.isAlive() && wall.distanceTo(light) <= 4, "stationary lure fixture remains nearby");
                h.assertTrue(wall.findQuarry() == light, "fixture keeps its own light");
                h.assertTrue(wall.action() == BrickrotWallEntity.Action.TRACK,
                        "nearby lure holds attention without scan loops: " + wall.action());
            }
            h.succeed();
        } finally {
            wall.discard(); light.discard();
            restoreFixtureBlocks(h, savedBlocks);
        }
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotReloadPreservesStagger(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        wall.stagger();
        CompoundTag saved = new CompoundTag();
        wall.saveWithoutId(saved);
        var loaded = BrickrotContent.WALL.get().create(h.getLevel());
        loaded.load(saved);
        h.assertTrue(loaded.action() == BrickrotWallEntity.Action.STAGGER, "reload cannot cancel stagger vulnerability");
        near(h, hit(loaded, 0, damage(h, DamageTypes.GENERIC), 10), 15, "stagger multiplier survives reload");
        h.succeed();
    }

    @GameTest(template = "brickrot_empty")
    public static void brickrotEmergenceCurve(GameTestHelper h) {
        Vec3 underground = new Vec3(0, -20, 0);
        Vec3 surface = new Vec3(5, 4, 20);
        for (int i = 1; i <= 9; i++) {
            near(h, BrickrotEmergence.settle(underground, surface, new Vec3(0, 1, 1), i * 2, i, 20)
                    .distanceTo(underground), 0, "emergence keeps the initial dive path");
            Vec3 settled = BrickrotEmergence.settle(underground, surface, new Vec3(0, 1, 1), i * 2, i, 60);
            near(h, settled.distanceTo(surface.add(0, 0, -i * 2)), 0, "all parts reach the surface by sixty ticks");
        }
        near(h, BrickrotEmergence.settle(underground, surface, Vec3.ZERO, 20, 9, 60).distanceTo(surface), 0,
                "zero direction remains finite");
        h.succeed();
    }
}
