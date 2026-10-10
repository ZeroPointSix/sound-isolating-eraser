package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.brickrot.BrickrotContent;
import com.zeropointsix.eraser.brickrot.BrickrotPart;
import com.zeropointsix.eraser.brickrot.BrickrotTrail;
import com.zeropointsix.eraser.brickrot.BrickrotWallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.ForgeSpawnEggItem;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BrickrotGameTests {
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
                h.assertTrue(h.getLevel().getEntity(part.getId()) == part, "parts are registered in the live world lookup");
            h.succeed();
        });
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
        BrickrotWallEntity wall = wall(h);
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(32, 4, 31));
        villager.setNoAi(true);
        villager.setNoGravity(true);
        var light = new ItemEntity(h.getLevel(), wall.getX() + 20, wall.getY(), wall.getZ(), new ItemStack(Items.TORCH));
        light.setNoGravity(true);
        h.getLevel().addFreshEntity(light);
        h.assertTrue(wall.findQuarry() == light, "dropped light outranks closer living target");
        h.getLevel().setBlock(wall.blockPosition().offset(10, 1, 0), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(wall.detectable(light), "dropped light can lure around obstacles");
        light.discard();
        h.assertTrue(wall.findQuarry() == villager, "living target is acquired without lure");
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++)
            h.getLevel().setBlock(wall.blockPosition().offset(x, y, 3), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(!wall.detectable(villager), "living target requires line of sight");
        h.succeed();
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
        h.succeed();
    }

    @GameTest(template = "brickrot_empty", timeoutTicks = 100)
    public static void brickrotBiteHasWindupAndTwelveDamage(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(32, 4, 28));
        target.setNoAi(true);
        wall.setNoAi(false);
        h.runAfterDelay(54, () -> near(h, target.getHealth(), 20, "bite does not hit before windup"));
        h.runAfterDelay(60, () -> {
            near(h, target.getHealth(), 8, "bite hits for twelve");
            h.succeed();
        });
    }

    @GameTest(template = "brickrot_empty", timeoutTicks = 100)
    public static void brickrotSweepHitsOnlyOnce(GameTestHelper h) {
        BrickrotWallEntity wall = wall(h);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(35, 4, 15));
        target.setNoAi(true);
        wall.setNoAi(false);
        h.runAfterDelay(60, () -> near(h, target.getHealth(), 20, "sweep telegraph precedes damage"));
        h.runAfterDelay(70, () -> {
            near(h, target.getHealth(), 10, "four red sections apply a single ten-damage hit");
            h.succeed();
        });
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
        BrickrotWallEntity wall = wall(h);
        var light = new ItemEntity(h.getLevel(), wall.getX(), wall.getY(), wall.getZ() + 3, new ItemStack(Items.TORCH));
        light.setNoGravity(true);
        h.getLevel().addFreshEntity(light);
        wall.setNoAi(false);
        h.runAfterDelay(80, () -> {
            h.assertTrue(wall.action() == BrickrotWallEntity.Action.TRACK, "nearby lure holds attention without scan loops");
            h.succeed();
        });
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
}
