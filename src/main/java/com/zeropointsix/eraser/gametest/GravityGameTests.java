package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.*;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityGameTests {
    private static final Set<Long> allyTickets = new HashSet<>();
    private static final List<Entity> allyEntities = new ArrayList<>();
    private static BlockPos allyRoof;
    private static BlockState allyRoofBefore;

    @AfterBatch(batch = "gravity_allies")
    public static void cleanUpAllies(ServerLevel level) {
        allyEntities.forEach(Entity::discard);
        allyEntities.clear();
        if (allyRoof != null) level.setBlockAndUpdate(allyRoof, allyRoofBefore);
        allyRoof = null;
        allyRoofBefore = null;
        for (long key : allyTickets) {
            ChunkPos chunk = new ChunkPos(key);
            level.setChunkForced(chunk.x, chunk.z, false);
        }
        allyTickets.clear();
    }

    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "GravityQA"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 6, 3))));
        player.getAbilities().mayBuild = false;
        return player;
    }

    private static void equip(FakePlayer player) {
        CuriosApi.getCuriosInventory(player).resolve().orElseThrow().getStacksHandler("necklace")
                .orElseThrow().getStacks().setStackInSlot(0, new ItemStack(ModItems.GRAVITY_JADE_PENDANT.get()));
    }

    private static void loadArea(GameTestHelper h, Vec3 center) {
        BlockPos pos = BlockPos.containing(center);
        for (int x = (pos.getX() >> 4) - 2; x <= (pos.getX() >> 4) + 2; x++) {
            for (int z = (pos.getZ() >> 4) - 2; z <= (pos.getZ() >> 4) + 2; z++) h.getLevel().getChunk(x, z);
        }
    }

    private static GravityFieldEntity field(GameTestHelper h, Vec3 center) {
        loadArea(h, center);
        GravityFieldEntity field = GravityFieldEntity.create(h.getLevel(), UUID.randomUUID(), center);
        h.getLevel().addFreshEntity(field);
        return field;
    }

    private static void stationary(GameTestHelper h, LivingEntity entity, Vec3 position) {
        entity.setPos(position);
        entity.setNoGravity(true);
        entity.noPhysics = true;
        if (entity instanceof Mob mob) mob.setNoAi(true);
        h.getLevel().addFreshEntity(entity);
    }

    @GameTest(template = "empty")
    public static void percentageCurveAnchorsAndInvalidConfiguration(GameTestHelper h) {
        double[][] anchors = {{12,2.4},{20,4},{24,4.8},{30,6},{50,10},{60,8.85},
                {100,6.28},{300,3},{500,2.13},{12000,0.25}};
        for (double[] pair : anchors) {
            h.assertTrue(Math.abs(GravityDamage.curve(pair[0], .2, 10, 300, .01) - pair[1]) <= .01,
                    "curve anchor " + pair[0]);
        }
        for (double reference : new double[]{0, 20, 50, Double.NaN, Double.POSITIVE_INFINITY}) {
            float damage = GravityDamage.curve(300, .2, 10, reference, .01);
            h.assertTrue(Float.isFinite(damage) && damage >= 0 && damage <= 10, "invalid reference safely bounded");
        }
        h.assertTrue(GravityDamage.curve(300, .2, 10, 300, .5) == 10, "increasing tail falls back to capped curve");
        h.assertTrue(GravityDamage.curve(20, .2, 0, 300, .01) == 0, "zero cap disables damage");
        h.assertTrue(GravityDamage.curve(Double.NaN) == 0, "invalid health never propagates NaN");
        h.assertTrue(GravityConfig.COOLDOWN.get() == 1200 && GravityConfig.DURATION.get() == 300, "default timers");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "gravity_geometry")
    public static void cylinderClipsHeightBorderAndNegativeCoordinates(GameTestHelper h) {
        Vec3 center = new Vec3(-24.25, 70, -23.75);
        AABB box = GravityGeometry.bounds(h.getLevel(), center, 16, 8, 16);
        h.assertTrue(box.getXsize() == 32 && box.getYsize() == 24 && box.getZsize() == 32, "cylinder broad bounds");
        h.assertTrue(GravityGeometry.contains(center, 16, box, center.add(15.9, 0, 0)), "15.9 included");
        h.assertTrue(GravityGeometry.contains(center, 16, box, center.add(16, 0, 0)), "exact circular boundary included");
        h.assertTrue(!GravityGeometry.contains(center, 16, box, center.add(16.1, 0, 0)), "16.1 excluded");
        h.assertTrue(!GravityGeometry.contains(center, 16, box, center.add(12, 0, 12)), "square corner excluded");
        var border = h.getLevel().getWorldBorder();
        double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
        try {
            border.setCenter(-24, -24);
            border.setSize(20);
            FakePlayer p = player(h);
            equip(p);
            p.setPos(-24.25, h.getLevel().getMinBuildHeight() + 1, -23.75);
            loadArea(h, p.position());
            h.assertTrue(GravityFieldController.activate(p), "border and bottom clipped cast succeeds");
            var fields = h.getLevel().getEntitiesOfClass(GravityFieldEntity.class, p.getBoundingBox().inflate(20));
            h.assertTrue(fields.size() == 1, "one clipped field");
            AABB clipped = fields.get(0).fieldBounds();
            h.assertTrue(clipped.minX == -34 && clipped.maxX == -14
                    && clipped.minY == h.getLevel().getMinBuildHeight(), "border/build floor exact");
            fields.forEach(GravityFieldEntity::discard);
            Vec3 top = new Vec3(-24, h.getLevel().getMaxBuildHeight() - 1, -24);
            h.assertTrue(GravityGeometry.bounds(h.getLevel(), top, 16, 8, 16).maxY
                    == h.getLevel().getMaxBuildHeight(), "build ceiling clipped");
        } finally {
            border.setCenter(x, z);
            border.setSize(size);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void necklaceOnlyAndInventoryDoesNotGrantPower(GameTestHelper h) {
        FakePlayer player = player(h);
        ItemStack jade = new ItemStack(ModItems.GRAVITY_JADE_PENDANT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, jade);
        player.setItemInHand(InteractionHand.OFF_HAND, jade.copy());
        player.getInventory().setItem(10, jade.copy());
        h.assertTrue(!GravityEquipment.isEquipped(player), "held/backpack jade never grants power");
        h.assertTrue(ModItems.GRAVITY_JADE_PENDANT.get().canEquip(new SlotContext("necklace", player, 0, false, true), jade), "necklace allowed");
        h.assertTrue(!ModItems.GRAVITY_JADE_PENDANT.get().canEquip(new SlotContext("curio", player, 0, false, true), jade), "curio rejected");
        h.assertTrue(!ModItems.GRAVITY_JADE_PENDANT.get().canEquip(new SlotContext("ring", player, 0, false, true), jade), "ring rejected");
        var handler = CuriosApi.getCuriosInventory(player).resolve().orElseThrow().getStacksHandler("necklace").orElseThrow();
        handler.getStacks().setStackInSlot(0, jade);
        h.assertTrue(GravityEquipment.isEquipped(player), "real necklace grants power");
        handler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        handler.getCosmeticStacks().setStackInSlot(0, jade);
        h.assertTrue(!GravityEquipment.isEquipped(player), "cosmetic necklace never grants power");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "gravity_cast")
    public static void serverPositionAndRejectedCastsKeepCooldownSafe(GameTestHelper h) {
        FakePlayer p = player(h);
        h.assertTrue(!GravityFieldController.activate(p), "unequipped rejected");
        equip(p);
        p.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!GravityFieldController.activate(p), "spectator rejected");
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().mayBuild = false;
        Vec3 center = p.position();
        p.setPos(1234567.25, 70, -1234567.75);
        h.assertTrue(!GravityFieldController.activate(p), "unloaded chunks rejected without loading");
        h.assertTrue(GravityFieldController.remainingCooldown(p) == 0, "rejection never consumes cooldown");
        p.setPos(center);
        loadArea(h, center);
        p.setYRot(157);
        p.setXRot(-80);
        h.assertTrue(GravityFieldController.activate(p), "one request immediately casts");
        var fields = h.getLevel().getEntitiesOfClass(GravityFieldEntity.class, p.getBoundingBox().inflate(2));
        h.assertTrue(fields.size() == 1 && fields.get(0).position().equals(center), "exact server feet, not look or block snap");
        h.assertTrue(GravityFieldController.remainingCooldown(p) == 1200, "60 second cooldown");
        h.assertTrue(!GravityFieldController.activate(p), "repeated intent blocked");
        p.setPos(center.add(4, 0, 0));
        h.assertTrue(fields.get(0).position().equals(center), "field remains fixed");
        h.assertTrue(!fields.get(0).shouldBeSaved(), "unload cannot save a delayed slam");
        fields.forEach(GravityFieldEntity::discard);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorAndSameTickHitCannotAbsorbCrush(GameTestHelper h) {
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        stationary(h, zombie, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 6, 3))));
        // Equipment attributes are installed by the first living-entity tick.
        h.runAtTickTime(2, () -> {
            h.assertTrue(zombie.getArmorValue() >= 20, "full diamond armor fixture");
            zombie.hurt(h.getLevel().damageSources().playerAttack(h.makeMockSurvivalPlayer()), 1);
            h.assertTrue(zombie.invulnerableTime > 0, "same-tick player hit establishes invulnerability");
            float before = zombie.getHealth();
            Vec3 velocity = new Vec3(.1, .2, -.1);
            zombie.setDeltaMovement(velocity);
            DamageSource crush = new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(GravityFieldEntity.DAMAGE_TYPE));
            GravityDamage.hurt(zombie, crush, GravityDamage.curve(20));
            h.assertTrue(Math.abs(zombie.getHealth() - (before - 4)) < .001, "full 4 points through armor and invulnerability");
            h.assertTrue(zombie.getDeltaMovement().equals(velocity), "crush preserves hover velocity");
            zombie.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gravity_allies", timeoutTicks = 330)
    public static void alliesLingerPassivesIgnoreAndAggressorsAreHostile(GameTestHelper h) {
        Vec3 center = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 7, 3)));
        BlockPos origin = BlockPos.containing(center);
        BlockPos outside = BlockPos.containing(center.add(18, 0, 0));
        // A loaded destination can still pause mob ticks while its entity-ticking ticket propagates.
        for (BlockPos pos : List.of(origin, outside)) {
            ChunkPos chunk = new ChunkPos(pos);
            if (!h.getLevel().getForcedChunks().contains(chunk.toLong())) {
                h.getLevel().setChunkForced(chunk.x, chunk.z, true);
                allyTickets.add(chunk.toLong());
            }
        }
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(h.getLevel().isPositionEntityTicking(origin)
                    && h.getLevel().isPositionEntityTicking(outside), "both ally locations are entity-ticking before timing begins");
        }).thenExecute(() -> verifyAlliesLinger(h, center));
    }

    private static void verifyAlliesLinger(GameTestHelper h, Vec3 center) {
        GravityFieldEntity field = field(h, center);
        allyEntities.add(field);
        var player = h.makeMockSurvivalPlayer();
        var pet = EntityType.WOLF.create(h.getLevel());
        pet.setTame(true);
        pet.setOwnerUUID(player.getUUID());
        var horse = EntityType.HORSE.create(h.getLevel());
        horse.setTamed(true);
        horse.setHealth(horse.getMaxHealth());
        var wild = EntityType.WOLF.create(h.getLevel());
        var cow = EntityType.COW.create(h.getLevel());
        var villager = EntityType.VILLAGER.create(h.getLevel());
        var iron = EntityType.IRON_GOLEM.create(h.getLevel());
        var snow = EntityType.SNOW_GOLEM.create(h.getLevel());
        var aggressor = EntityType.WOLF.create(h.getLevel());
        List<LivingEntity> entities = List.of(player, pet, horse, wild, cow, villager, iron, snow, aggressor);
        allyEntities.addAll(entities);
        for (int i = 0; i < entities.size(); i++) stationary(h, entities.get(i), center.add(i * .4, 0, 0));
        // Earlier weather tests may leave rain active; protect this passive fixture normally.
        allyRoof = snow.blockPosition().above(3);
        allyRoofBefore = h.getLevel().getBlockState(allyRoof);
        h.getLevel().setBlockAndUpdate(allyRoof, Blocks.STONE.defaultBlockState());
        // Snow golems also melt in warm biomes; fire resistance makes the fixture biome-independent.
        snow.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
        aggressor.setTarget(player);
        List<LivingEntity> allies = List.of(player, pet, horse);
        int[] exitTicks = new int[allies.size()];
        h.runAfterDelay(22, () -> {
            for (LivingEntity ally : List.of(player, pet, horse)) {
                h.assertTrue(ally.hasEffect(MobEffects.MOVEMENT_SPEED) && ally.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 2, "ally speed III");
                h.assertTrue(ally.hasEffect(MobEffects.JUMP) && ally.getEffect(MobEffects.JUMP).getAmplifier() == 2, "ally jump III");
                h.assertTrue(!ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && ally.getHealth() == ally.getMaxHealth(), "ally unharmed");
                ally.setPos(center.add(18, 0, 0));
                h.assertTrue(h.getLevel().isPositionEntityTicking(ally.blockPosition()), "ally exit destination remains entity-ticking");
            }
            for (int i = 0; i < allies.size(); i++) exitTicks[i] = allies.get(i).tickCount;
            for (LivingEntity neutral : List.of(wild, cow, villager, iron, snow)) {
                h.assertTrue(neutral.getActiveEffects().stream().allMatch(e -> neutral == snow
                                && e.getEffect() == MobEffects.FIRE_RESISTANCE)
                        && neutral.getHealth() == neutral.getMaxHealth(),
                        "passive unaffected: " + neutral.getType() + " health=" + neutral.getHealth()
                                + " effects=" + neutral.getActiveEffects());
            }
            h.assertTrue(aggressor.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "neutral attacking ally is hostile");
            field.discard();
            logAllyClocks(h, "exit", List.of(player, pet, horse));
        });
        h.runAfterDelay(60, () -> logAllyClocks(h, "mid", allies));
        h.runAfterDelay(95, () -> {
            logAllyClocks(h, "before", List.of(player, pet, horse));
            h.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED) && pet.hasEffect(MobEffects.JUMP), "buff still present before four seconds");
        });
        h.runAfterDelay(105, () -> {
            logAllyClocks(h, "after", List.of(player, pet, horse));
            for (int i = 0; i < allies.size(); i++) {
                LivingEntity ally = allies.get(i);
                h.assertTrue(ally.tickCount - exitTicks[i] == 83,
                        "ally ticks match elapsed server ticks: " + ally.getType() + " elapsed=" + (ally.tickCount - exitTicks[i]));
                h.assertTrue(!ally.hasEffect(MobEffects.MOVEMENT_SPEED) && !ally.hasEffect(MobEffects.JUMP),
                        "both ally buffs expire after four seconds: " + ally.getType());
            }
            h.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED) && !pet.hasEffect(MobEffects.JUMP), "buff expires after four seconds");
            entities.forEach(LivingEntity::discard);
            h.succeed();
        });
    }

    private static void logAllyClocks(GameTestHelper h, String phase, List<LivingEntity> allies) {
        for (LivingEntity ally : allies) {
            var speed = ally.getEffect(MobEffects.MOVEMENT_SPEED);
            var jump = ally.getEffect(MobEffects.JUMP);
            System.out.println("GRAVITY_ALLY_CLOCK phase=" + phase + " time=" + h.getLevel().getGameTime()
                    + " type=" + ally.getType().toShortString() + " tickCount=" + ally.tickCount
                    + " speed=" + (speed == null ? 0 : speed.getDuration()) + " jump=" + (jump == null ? 0 : jump.getDuration())
                    + " ticking=" + h.getLevel().isPositionEntityTicking(ally.blockPosition())
                    + " forced=" + h.getLevel().getForcedChunks().size() + " players=" + h.getLevel().players().size()
                    + " linger=" + GravityConfig.ALLY_LINGER.get() + " position=" + ally.position());
        }
    }

    @GameTest(template = "empty", batch = "gravity_hover", timeoutTicks = 200)
    public static void hostileHoversThenFallsOutsideCylinder(GameTestHelper h) {
        Vec3 center = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 7, 3)));
        BlockPos floor = BlockPos.containing(center).below();
        h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        Vec3 landing = center.add(-18, 0, 0);
        BlockPos landingFloor = BlockPos.containing(landing).below();
        for (BlockPos pos : BlockPos.betweenClosed(landingFloor.offset(-1, 0, -1), landingFloor.offset(1, 6, 1))) {
            h.getLevel().setBlockAndUpdate(pos, pos.getY() == landingFloor.getY()
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.setPos(center);
        // NoAI also disables travel(), so keep physics active while preventing walking.
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
        zombie.setHealth(300);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        h.getLevel().addFreshEntity(zombie);
        GravityFieldEntity field = field(h, center);
        h.runAtTickTime(85, () -> {
            double height = zombie.getY() - center.y;
            h.assertTrue(height > 2.5 && height <= 5.5, "bounded hover near 3 blocks: " + height
                    + " fieldRemoved=" + field.isRemoved() + " motion=" + zombie.getDeltaMovement());
            h.assertTrue(zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 4, "slowness V");
            zombie.getNavigation().stop();
            zombie.setPos(landing.add(0, 3, 0));
            zombie.setDeltaMovement(Vec3.ZERO);
        });
        h.runAtTickTime(145, () -> {
            h.assertTrue(!zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "hostile debuff fades outside");
            // Outside the cylinder, vanilla gravity must pull the zombie down. Allow a few
            // ticks of settling so a lagged tick does not fail a grounded assertion.
            h.assertTrue(zombie.onGround() || Math.abs(zombie.getY() - landing.y) <= 1.5,
                    "natural gravity after leaving: pos=" + zombie.position() + " ground=" + zombie.onGround()
                            + " motion=" + zombie.getDeltaMovement() + " removed=" + zombie.isRemoved());
            field.discard();
            zombie.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gravity_early")
    public static void earlyRemovalNeverSlams(GameTestHelper h) {
        Vec3 center = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 7, 3)));
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        stationary(h, zombie, center);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        GravityFieldEntity field = field(h, center);
        h.runAtTickTime(5, () -> field.discard());
        h.runAtTickTime(15, () -> {
            h.assertTrue(zombie.getHealth() == 20 && zombie.getDeltaMovement().y > -1, "early removal causes no slam or damage");
            zombie.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void terrainOnlyCrushesCylinderAndOneExposedLayer(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos soil = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(soil.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(soil, Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(soil.above(), Blocks.GRASS.defaultBlockState());
        BlockPos leaves = soil.offset(1, 0, 0), stone = soil.offset(-1, 0, 0), chest = soil.offset(0, 0, -1);
        BlockPos outside = soil.offset(2, 0, 2), flower = soil.offset(0, 0, 1);
        BlockPos tallGrass = soil.offset(1, 0, 1);
        level.setBlockAndUpdate(tallGrass.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(tallGrass, Blocks.TALL_GRASS.defaultBlockState());
        level.setBlockAndUpdate(tallGrass.above(), Blocks.TALL_GRASS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        level.setBlockAndUpdate(leaves, Blocks.OAK_LEAVES.defaultBlockState());
        level.setBlockAndUpdate(outside, Blocks.OAK_LEAVES.defaultBlockState());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(flower.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(flower, Blocks.DANDELION.defaultBlockState());
        FakePlayer p = player(h);
        p.getAbilities().mayBuild = true;
        AABB box = new AABB(soil.offset(-2, -1, -2), soil.offset(4, 3, 4));
        GravityTerrainCrush.crush(level, p, Vec3.atCenterOf(soil), 1.5, box, 1);
        h.assertTrue(level.isEmptyBlock(soil) && level.isEmptyBlock(leaves) && level.isEmptyBlock(flower), "fragile and exposed soil break");
        h.assertTrue(level.isEmptyBlock(tallGrass) && level.isEmptyBlock(tallGrass.above()), "both tall grass halves break");
        h.assertTrue(level.getBlockState(soil.below()).is(Blocks.DIRT), "lower layer remains");
        h.assertTrue(level.getBlockState(outside).is(Blocks.OAK_LEAVES), "square corner is outside cylinder");
        h.assertTrue(level.getBlockState(stone).is(Blocks.STONE) && level.getBlockState(chest).is(Blocks.CHEST), "stone and block entity preserved");
        level.setBlockAndUpdate(soil, Blocks.DIRT.defaultBlockState());
        GravityTerrainCrush.crush(level, p, Vec3.atCenterOf(soil), 2, box, 0);
        h.assertTrue(level.getBlockState(soil).is(Blocks.DIRT), "zero chance never breaks soil");
        level.setBlockAndUpdate(leaves, Blocks.OAK_LEAVES.defaultBlockState());
        p.getAbilities().mayBuild = false;
        GravityTerrainCrush.crush(level, p, Vec3.atCenterOf(soil), 2, box, 1);
        h.assertTrue(level.getBlockState(leaves).is(Blocks.OAK_LEAVES), "mayBuild protection retained");
        p.getAbilities().mayBuild = true;
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> deny = event -> {
            if (event.getPos().equals(leaves)) event.setCanceled(true);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
        try {
            GravityTerrainCrush.crush(level, p, Vec3.atCenterOf(soil), 2, box, 1);
            h.assertTrue(level.getBlockState(leaves).is(Blocks.OAK_LEAVES), "Forge protection cancellation retained");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "gravity_boundary", timeoutTicks = 50)
    public static void realHostilesRespectCircleAndTerrainIsNotRepeated(GameTestHelper h) {
        Vec3 center = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 7, 3)));
        var inside = EntityType.ZOMBIE.create(h.getLevel());
        var outside = EntityType.ZOMBIE.create(h.getLevel());
        var corner = EntityType.ZOMBIE.create(h.getLevel());
        List<LivingEntity> mobs = List.of(inside, outside, corner);
        List<Vec3> positions = List.of(center.add(15.9, 0, 0), center.add(16.1, 0, 0), center.add(12, 0, 12));
        for (int i = 0; i < mobs.size(); i++) {
            stationary(h, mobs.get(i), positions.get(i));
            mobs.get(i).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        }
        GravityFieldEntity field = field(h, center);
        BlockPos replanted = BlockPos.containing(center).offset(2, 0, 2);
        h.getLevel().setBlockAndUpdate(replanted, Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        h.runAtTickTime(22, () -> {
            h.assertTrue(inside.getHealth() == 16 && inside.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "15.9-block hostile affected");
            h.assertTrue(outside.getHealth() == 20 && !outside.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "16.1-block hostile unaffected");
            h.assertTrue(corner.getHealth() == 20 && !corner.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "AABB corner hostile unaffected");
            h.assertTrue(h.getLevel().getBlockState(replanted).is(Blocks.OAK_LEAVES), "active field never repeatedly crushes terrain");
            field.discard();
            mobs.forEach(LivingEntity::discard);
            h.succeed();
        });
    }

    private GravityGameTests() { }
}
