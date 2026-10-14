package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.BoneSpike;
import com.zeropointsix.eraser.yuji.YujiAfflictions;
import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class YujiGameTests {
    private static YujiMonster monster(GameTestHelper h, YujiKind kind) {
        YujiMonster mob = h.spawn(YujiContent.MOBS.get(kind).get(), new BlockPos(2, 2, 2));
        mob.setNoAi(true); mob.setNoGravity(true);
        return mob;
    }
    private static Player player(GameTestHelper h, YujiMonster mob) {
        Player player = h.makeMockSurvivalPlayer();
        player.setUUID(UUID.randomUUID()); player.moveTo(mob.position()); player.setNoGravity(true);
        return player;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .001, message + ": " + actual + " != " + expected);
    }
    private static CompoundTag state(Player p, String type, YujiMonster source) {
        return p.getPersistentData().getCompound("QihaiAfflictions").getCompound(type).getCompound(source.getStringUUID());
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiAllProfilesRegistered(GameTestHelper h) {
        h.assertTrue(YujiContent.MOBS.size() == 11, "ten monsters plus boss");
        h.assertTrue(YujiContent.PARTICLE.size() == 19, "nineteen particle providers");
        for (YujiKind kind : YujiKind.values()) {
            YujiMonster mob = monster(h, kind);
            close(h, mob.getMaxHealth(), kind == YujiKind.TANYU ? 38 : kind.health, "health " + kind);
            h.assertTrue(mob.getAttribute(Attributes.ATTACK_DAMAGE) != null, "safe combat attributes");
            h.assertTrue(!mob.getType().getDescriptionId().isEmpty(), "localized entity");
            mob.discard();
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiMilkCannotCureCustomEffects(GameTestHelper h) {
        for (var effect : new net.minecraft.world.effect.MobEffect[]{YujiContent.CURSED.get(), YujiContent.BETRAYED.get(), YujiContent.ENTHRALLED.get()}) {
            h.assertTrue(!new MobEffectInstance(effect, 100).isCurativeItem(new ItemStack(Items.MILK_BUCKET)), "milk must not cure " + effect);
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiTheftPriorityAndArmor(GameTestHelper h) {
        YujiMonster thief = monster(h, YujiKind.TOUDAO); Player p = player(h, thief);
        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD); enchanted.enchant(Enchantments.SHARPNESS, 3);
        enchanted.getOrCreateTag().putString("OwnerNote", "regression");
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64)); p.getInventory().setItem(1, enchanted);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        thief.steal(p);
        h.assertTrue(p.getInventory().getItem(1).isEmpty(), "enchanted stack prioritized");
        h.assertTrue(p.getInventory().getItem(0).getCount() == 64, "other stack untouched");
        h.assertTrue(!p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "worn armor untouched");
        h.assertTrue(ItemStack.matches(thief.displayedLoot(), enchanted), "loot keeps complete NBT");
        h.assertTrue(!thief.removeWhenFarAway(10000), "stolen items prevent despawn");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiStolenStackReloadAndNoLootRule(GameTestHelper h) {
        YujiMonster thief = monster(h, YujiKind.TOUDAO); Player p = player(h, thief);
        ItemStack expected = new ItemStack(Items.DIAMOND, 37); expected.getOrCreateTag().putString("Receipt", "yuji");
        p.getInventory().setItem(0, expected.copy()); thief.steal(p);
        CompoundTag saved = new CompoundTag(); thief.addAdditionalSaveData(saved); thief.discard();
        YujiMonster restored = monster(h, YujiKind.TOUDAO); restored.readAdditionalSaveData(saved);
        h.assertTrue(ItemStack.matches(restored.displayedLoot(), expected), "reload keeps stack and tag");
        boolean rule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        try {
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false, h.getLevel().getServer());
            restored.hurt(restored.damageSources().genericKill(), 10000);
            restored.die(restored.damageSources().genericKill());
            int total = h.getLevel().getEntitiesOfClass(ItemEntity.class, restored.getBoundingBox().inflate(1),
                    item -> item.getItem().is(Items.DIAMOND) && ItemStack.isSameItemSameTags(item.getItem(), expected))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum();
            h.assertTrue(total == 37, "property returned once even with doMobLoot=false: " + total);
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(rule, h.getLevel().getServer()); }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiConfiguredGrowthCapSurvivesReload(GameTestHelper h) {
        int liveCap = YujiConfig.MAX_LAYERS.get();
        YujiMonster greed = monster(h, YujiKind.TANYU), restored = monster(h, YujiKind.TANYU);
        try (var ignored = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            YujiConfig.MAX_LAYERS.set(3);
            greed.setLayers(99, true);
            h.assertTrue(greed.layers() == 3, "configured growth cap is enforced");
            close(h, greed.getMaxHealth(), 42, "configured layers set maximum health");
            close(h, greed.getAttributeValue(Attributes.ATTACK_DAMAGE), 5.5, "configured layers set attack damage");
            greed.setHealth(17);
            CompoundTag saved = new CompoundTag(); greed.addAdditionalSaveData(saved);
            YujiConfig.MAX_LAYERS.set(1);
            restored.readAdditionalSaveData(saved);
            h.assertTrue(restored.layers() == 1, "reload respects a reduced configured cap");
            close(h, restored.getMaxHealth(), 34, "reloaded health follows the new cap");
            close(h, restored.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.5, "reloaded attack follows the new cap");
            close(h, restored.getHealth(), 17, "changing the cap never heals stored damage");
            saved.putFloat("Health", 41);
            restored.readAdditionalSaveData(saved);
            close(h, restored.getHealth(), 34, "stored health is clamped to the reduced layer cap");
            YujiConfig.MAX_LAYERS.set(12);
            saved.putInt("YujiLayers", 12); saved.putFloat("Health", 60); saved.remove("Attributes");
            restored.readAdditionalSaveData(saved);
            close(h, restored.getHealth(), 60, "legacy health without attributes is not truncated to spawn health");
        } finally { greed.discard(); restored.discard(); }
        h.assertTrue(YujiConfig.MAX_LAYERS.get() == liveCap, "live layer config restored");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiConfiguredPullStrengthControlsVelocity(GameTestHelper h) {
        double livePull = YujiConfig.PULL_STRENGTH.get();
        YujiMonster source = monster(h, YujiKind.XIEYIN); Player p = player(h, source);
        try (var ignored = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            p.moveTo(source.position().add(8, 0, 0));
            YujiAfflictions.apply(p, source, "enthralled");
            for (double scale : new double[]{0, .5, 1, 3}) {
                YujiConfig.PULL_STRENGTH.set(scale);
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                state(p, "enthralled", source).putInt("Age", 0);
                YujiAfflictions.tickPlayer(p);
                close(h, p.getDeltaMovement().x, -.8 / 20 * scale, "pull velocity uses configured multiplier " + scale);
                close(h, p.getDeltaMovement().y, 0, "pull does not inject vertical movement");
                close(h, p.getDeltaMovement().z, 0, "pull stays directed toward its source");
                h.assertTrue(p.hasEffect(YujiContent.ENTHRALLED.get()), "zero pull does not erase the source effect");
            }
        } finally { source.discard(); YujiAfflictions.tickPlayer(p); }
        close(h, YujiConfig.PULL_STRENGTH.get(), livePull, "live pull config restored");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiConfiguredBetrayalTargetsAndBuff(GameTestHelper h) {
        boolean livePets = YujiConfig.AFFECT_PETS.get(), liveVillagers = YujiConfig.AFFECT_VILLAGERS.get();
        double liveBuff = YujiConfig.HOSTILE_BUFF.get();
        YujiMonster source = monster(h, YujiKind.LIANGSHE);
        // Recruitment scans 32 blocks. Keep this synchronous config probe away from other fixtures.
        source.moveTo(source.position().add(0, 96, 0));
        Player p = player(h, source);
        var wolf = h.spawn(EntityType.WOLF, new BlockPos(3, 2, 2));
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        var actors = java.util.List.of(source, wolf, villager, zombie);
        for (var actor : actors) { actor.setNoAi(true); actor.setNoGravity(true); actor.moveTo(source.position()); }
        wolf.tame(p); wolf.setOrderedToSit(true);
        double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double attack = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
        try (var ignored = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            YujiAfflictions.apply(p, source, "betrayed");
            for (boolean enabled : new boolean[]{false, true}) {
                YujiConfig.AFFECT_PETS.set(enabled); YujiConfig.AFFECT_VILLAGERS.set(enabled);
                for (double buff : new double[]{0, .5, 2}) {
                    YujiConfig.HOSTILE_BUFF.set(buff);
                    YujiAfflictions.recruit(p, true); YujiAfflictions.recruit(p, true);
                    h.assertTrue((wolf.getTarget() == p) == enabled, "pet recruitment obeys its switch");
                    h.assertTrue(wolf.isOrderedToSit() != enabled, "disabled pet interaction preserves sitting");
                    h.assertTrue(villager.getPersistentData().contains("QihaiBetrayal") == enabled,
                            "villager influence obeys its switch");
                    var trade = new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(
                            p, net.minecraft.world.InteractionHand.MAIN_HAND, villager);
                    YujiAfflictions.trade(trade);
                    h.assertTrue(trade.isCanceled() == enabled, "trade restriction obeys the villager switch");
                    close(h, zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * (1 + buff), "configured speed buff applies once");
                    close(h, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), attack * (1 + buff), "configured attack buff applies once");
                    for (var actor : actors) YujiAfflictions.restore(actor);
                    h.assertTrue(wolf.isOrderedToSit() && p.getUUID().equals(wolf.getOwnerUUID()), "pet state and ownership restored");
                    close(h, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), attack, "configured attack buff removed");
                }
            }
        } finally {
            source.discard(); YujiAfflictions.tickPlayer(p);
            for (var actor : actors) { YujiAfflictions.restore(actor); actor.discard(); }
        }
        h.assertTrue(YujiConfig.AFFECT_PETS.get() == livePets && YujiConfig.AFFECT_VILLAGERS.get() == liveVillagers,
                "live target switches restored");
        close(h, YujiConfig.HOSTILE_BUFF.get(), liveBuff, "live hostile buff restored");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiConfiguredLegacyCurseDamageCap(GameTestHelper h) {
        double liveCap = YujiConfig.MAX_CURSE_DAMAGE.get();
        var server = h.getLevel().getServer(); var difficulty = h.getLevel().getDifficulty();
        YujiMonster source = monster(h, YujiKind.EKOU); Player p = player(h, source);
        try (var ignored = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            YujiAfflictions.apply(p, source, "cursed");
            for (double cap : new double[]{1, 2, 5}) {
                YujiConfig.MAX_CURSE_DAMAGE.set(cap);
                p.setHealth(20); state(p, "cursed", source).putInt("Age", 2000);
                for (int tick = 0; tick < 30; tick++) { p.invulnerableTime = 0; YujiAfflictions.tickPlayer(p); }
                close(h, p.getHealth(), 20 - cap, "legacy curse respects configured cap " + cap);
            }
        } finally { source.discard(); YujiAfflictions.tickPlayer(p); server.setDifficulty(difficulty, true); }
        close(h, YujiConfig.MAX_CURSE_DAMAGE.get(), liveCap, "live legacy curse config restored");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGreedLayersClampAndReload(GameTestHelper h) {
        YujiMonster greed = monster(h, YujiKind.TANYU); greed.setLayers(99, true);
        h.assertTrue(greed.layers() == 12, "layer cap"); close(h, greed.getMaxHealth(), 78, "layer health");
        close(h, greed.getAttributeValue(Attributes.ATTACK_DAMAGE), 10, "layer damage");
        close(h, greed.getAttributeValue(Attributes.MOVEMENT_SPEED), .225, "eight-layer slowdown");
        greed.setHealth(31); CompoundTag saved = new CompoundTag(); greed.addAdditionalSaveData(saved);
        YujiMonster restored = monster(h, YujiKind.TANYU); restored.readAdditionalSaveData(saved);
        close(h, restored.getHealth(), 31, "reload never heals"); h.assertTrue(restored.layers() == 12, "layer persistence");
        restored.setLayers(-9, false); h.assertTrue(restored.layers() == 0, "negative layers clamp");
        close(h, restored.getHealth(), 30, "health clamped after peeling"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGreedReachedWaypointAdvancesAtEveryLayer(GameTestHelper h) {
        BlockPos start = h.absolutePos(new BlockPos(2, 2, 2));
        for (int layers = 0; layers <= 12; layers++) {
            YujiMonster greed = monster(h, YujiKind.TANYU);
            h.assertTrue(greed.getNavigation().canFloat(), "growth navigation retains the swim goal's float setting");
            greed.setLayers(layers, false);
            var path = new net.minecraft.world.level.pathfinder.Path(java.util.List.of(
                    new net.minecraft.world.level.pathfinder.Node(start.getX(), start.getY(), start.getZ()),
                    new net.minecraft.world.level.pathfinder.Node(start.getX(), start.getY(), start.getZ() + 4)),
                    start.offset(0, 0, 4), true);
            var reached = path.getNextEntityPos(greed);
            greed.moveTo(reached);
            greed.setOnGround(true);
            h.assertTrue(greed.getNavigation().moveTo(path, 1), "native path starts at layer " + layers);
            greed.getNavigation().tick();
            h.assertTrue(path.getNextNodeIndex() > 0,
                    "reached movement waypoint advances at layer " + layers + ", width=" + greed.getBbWidth());
            h.assertTrue(greed.position().equals(reached), "navigation never teleports the mob");
            greed.discard();
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGreedNearWaypointAdvancesWithoutSkippingDistantNodes(GameTestHelper h) {
        BlockPos start = h.absolutePos(new BlockPos(2, 2, 2));
        for (int layers = 0; layers <= 12; layers++) {
            for (double offset : new double[]{-.0001, .0001, -2, 2}) {
                YujiMonster greed = monster(h, YujiKind.TANYU);
                try {
                    greed.setLayers(layers, false);
                    var path = new net.minecraft.world.level.pathfinder.Path(java.util.List.of(
                            new net.minecraft.world.level.pathfinder.Node(start.getX(), start.getY(), start.getZ()),
                            new net.minecraft.world.level.pathfinder.Node(start.getX(), start.getY(), start.getZ() + 4)),
                            start.offset(0, 0, 4), true);
                    var position = path.getNextEntityPos(greed).add(offset, 0, offset);
                    greed.moveTo(position); greed.setOnGround(true);
                    h.assertTrue(greed.getNavigation().moveTo(path, 1), "native near-waypoint path starts");
                    greed.getNavigation().tick();
                    int expected = Math.abs(offset) < 1 ? 1 : 0;
                    h.assertTrue(path.getNextNodeIndex() == expected,
                            "native arrival tolerance handles near but not distant nodes: layers=" + layers
                                    + ", offset=" + offset + ", index=" + path.getNextNodeIndex());
                    h.assertTrue(greed.position().equals(position), "arrival detection never teleports");
                } finally { greed.discard(); }
            }
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGreedPeelsOnlyLargeAcceptedHit(GameTestHelper h) {
        YujiMonster greed = monster(h, YujiKind.TANYU); greed.setLayers(6, false); greed.setHealth(greed.getMaxHealth());
        greed.hurt(greed.damageSources().generic(), 5); h.assertTrue(greed.layers() == 6, "small hit cannot peel");
        greed.invulnerableTime = 0; greed.hurt(greed.damageSources().generic(), 6);
        h.assertTrue(greed.layers() == 5, "large hit peels once");
        greed.hurt(greed.damageSources().generic(), 6); h.assertTrue(greed.layers() == 5, "rejected hit cannot peel"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGreedDeathFeedsOnlyNearest(GameTestHelper h) {
        YujiMonster dead = monster(h, YujiKind.TANYU), near = monster(h, YujiKind.TANYU), far = monster(h, YujiKind.TANYU);
        near.setPos(dead.position().add(1, 0, 0)); far.setPos(dead.position().add(4, 0, 0));
        dead.hurt(dead.damageSources().mobAttack(near), 10000);
        h.assertTrue(near.layers() == 5 && far.layers() == 2, "nearest receives exactly three, without extra kill layer"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiEggLayersUseOneToThree(GameTestHelper h) {
        for (int i = 0; i < 30; i++) {
            YujiMonster greed = YujiContent.MOBS.get(YujiKind.TANYU).get().create(h.getLevel());
            greed.finalizeSpawn(h.getLevel(), h.getLevel().getCurrentDifficultyAt(h.absolutePos(BlockPos.ZERO)), MobSpawnType.SPAWN_EGG, null, null);
            h.assertTrue(greed.layers() >= 1 && greed.layers() <= 3, "egg layer bounds");
            close(h, greed.getHealth(), greed.getMaxHealth(), "fresh spawn full health");
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiCurseMultiSourceAndExitGrace(GameTestHelper h) {
        YujiMonster a = monster(h, YujiKind.EKOU), b = monster(h, YujiKind.EKOU); Player p = player(h, a);
        YujiAfflictions.apply(p, a, "cursed"); YujiAfflictions.apply(p, b, "cursed"); a.discard(); YujiAfflictions.tickPlayer(p);
        h.assertTrue(p.hasEffect(YujiContent.CURSED.get()), "another source keeps curse");
        p.moveTo(b.position().add(20, 0, 0));
        for (int i = 0; i < 59; i++) YujiAfflictions.tickPlayer(p);
        h.assertTrue(p.hasEffect(YujiContent.CURSED.get()), "three-second grace"); YujiAfflictions.tickPlayer(p);
        h.assertTrue(!p.hasEffect(YujiContent.CURSED.get()), "leaving clears curse after grace"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiEnthrallLongExposureStillCrouchable(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.XIEYIN); Player p = player(h, source);
        YujiAfflictions.apply(p, source, "enthralled"); state(p, "enthralled", source).putInt("Age", 240);
        p.setShiftKeyDown(true); for (int i = 0; i < 40; i++) YujiAfflictions.tickPlayer(p);
        h.assertTrue(p.getEffect(YujiContent.ENTHRALLED.get()).getAmplifier() == 1, "long exposure can fall from III to II");
        YujiAfflictions.tickPlayer(p);
        h.assertTrue(p.getEffect(YujiContent.ENTHRALLED.get()).getAmplifier() == 1, "does not rebound immediately to III"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiEnthrallDamageAndRangeClear(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.XIEYIN); Player p = player(h, source);
        YujiAfflictions.apply(p, source, "enthralled"); p.hurt(p.damageSources().generic(), 1);
        h.assertTrue(!p.hasEffect(YujiContent.ENTHRALLED.get()), "damage wakes player");
        YujiAfflictions.apply(p, source, "enthralled"); p.moveTo(source.position().add(17, 0, 0)); YujiAfflictions.tickPlayer(p);
        h.assertTrue(!p.hasEffect(YujiContent.ENTHRALLED.get()), "outside sixteen blocks clears lure"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiCrushRequiresTwoHighOpposingWalls(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.XIEYIN); Player p = player(h, source); BlockPos pos = p.blockPosition();
        h.getLevel().setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west(), Blocks.STONE.defaultBlockState());
        h.assertTrue(!YujiAfflictions.cramped(p), "single-height walls do not crush");
        h.getLevel().setBlockAndUpdate(pos.east().above(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west().above(), Blocks.STONE.defaultBlockState());
        h.assertTrue(YujiAfflictions.cramped(p), "opposing two-high walls crush"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiWeaknessShortRefreshStillOwned(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.WANGYU); Player p = player(h, source);
        YujiAfflictions.weakness(p, source, 400, 1); YujiAfflictions.weakness(p, source, 160, 1);
        h.assertTrue(p.getEffect(MobEffects.WEAKNESS).getDuration() == 400, "short refresh does not shorten stronger history");
        source.discard(); YujiAfflictions.tickPlayer(p);
        h.assertTrue(!p.hasEffect(MobEffects.WEAKNESS), "dead source leaves no stale weakness"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiWeaknessMultiSourcePreservesExternal(GameTestHelper h) {
        YujiMonster a = monster(h, YujiKind.WANGYU), b = monster(h, YujiKind.WANGYU); Player p = player(h, a);
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
        YujiAfflictions.weakness(p, a, 400, 1); YujiAfflictions.weakness(p, b, 160, 0);
        a.discard(); YujiAfflictions.tickPlayer(p);
        h.assertTrue(p.getEffect(MobEffects.WEAKNESS).getAmplifier() == 0, "strong source removal downgrades immediately");
        b.discard(); YujiAfflictions.tickPlayer(p);
        h.assertTrue(p.hasEffect(MobEffects.WEAKNESS) && p.getEffect(MobEffects.WEAKNESS).getDuration() >= 598,
                "external weakness survives both sources"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiWeaknessMilkDoesNotReappear(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.WANGYU); Player p = player(h, source);
        YujiAfflictions.weakness(p, source, 400, 1);
        p.curePotionEffects(new ItemStack(Items.MILK_BUCKET)); YujiAfflictions.tickPlayer(p);
        h.assertTrue(!p.hasEffect(MobEffects.WEAKNESS), "vanilla weakness remains curable by milk"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBetrayalBuffDoesNotStack(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.LIANGSHE); Player p = player(h, source);
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 2)); zombie.setNoAi(true);
        double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), damage = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
        YujiAfflictions.apply(p, source, "betrayed"); YujiAfflictions.recruit(p, true); YujiAfflictions.recruit(p, true);
        close(h, zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.3, "speed buff once");
        close(h, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), damage * 1.3, "attack buff once");
        YujiAfflictions.restore(zombie);
        close(h, zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), speed, "speed restored");
        h.assertTrue(zombie.getTarget() == null, "forced target restored"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBetrayalPetSittingRestores(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.LIANGSHE); Player p = player(h, source);
        var wolf = h.spawn(EntityType.WOLF, new BlockPos(3, 2, 2)); wolf.setNoAi(true); wolf.tame(p); wolf.setOrderedToSit(true);
        var cat = h.spawn(EntityType.CAT, new BlockPos(3, 2, 3)); cat.setNoAi(true); cat.tame(p); cat.setOrderedToSit(false);
        YujiAfflictions.apply(p, source, "betrayed"); YujiAfflictions.recruit(p, true);
        h.assertTrue(wolf.getTarget() == p && !wolf.isOrderedToSit(), "own wolf attacks owner");
        h.assertTrue(cat.isOrderedToSit(), "cat stays in place"); YujiAfflictions.restore(wolf); YujiAfflictions.restore(cat);
        h.assertTrue(wolf.isOrderedToSit() && !cat.isOrderedToSit(), "original orders restored");
        h.assertTrue(p.getUUID().equals(wolf.getOwnerUUID()) && p.getUUID().equals(cat.getOwnerUUID()), "ownership remains intact"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBetrayalPandaTargetsAndRestores(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.LIANGSHE); Player p = player(h, source);
        var panda = h.spawn(EntityType.PANDA, new BlockPos(3, 2, 2)); panda.setNoAi(true);
        var previous = h.spawn(EntityType.COW, new BlockPos(4, 2, 2)); previous.setNoAi(true);
        panda.setTarget(previous);
        double attack = panda.getAttributeValue(Attributes.ATTACK_DAMAGE);
        YujiAfflictions.recruit(p, false);
        h.assertTrue(panda.getTarget() == previous, "curse must not recruit a neutral panda");
        YujiAfflictions.apply(p, source, "betrayed");
        YujiAfflictions.recruit(p, true); YujiAfflictions.recruit(p, true);
        h.assertTrue(panda.getTarget() == p, "betrayal recruits panda even though it is not NeutralMob");
        close(h, panda.getAttributeValue(Attributes.ATTACK_DAMAGE), attack, "neutral panda does not receive hostile buff");
        YujiAfflictions.restore(panda);
        h.assertTrue(panda.getTarget() == previous, "panda restores original target after repeated recruitment");
        h.assertTrue(!panda.getPersistentData().contains("QihaiBetrayal"), "influence state cleared");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiNavigationKeepsClosedDoorsAndClimbsWalls(GameTestHelper h) {
        YujiMonster husk = monster(h, YujiKind.YUCHI), boss = monster(h, YujiKind.SHIFOTOU);
        // In 1.20.1 GroundPathNavigation.canOpenDoors delegates to canPassDoors; inspect the actual flags.
        var evaluator = husk.getNavigation().getNodeEvaluator();
        h.assertTrue(!evaluator.canOpenDoors(),
                "husk never opens closed doors");
        h.assertTrue(evaluator.canPassDoors(), "husk can still use an already open doorway");
        h.assertTrue(boss.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.WallClimberNavigation,
                "boss keeps approaching blocked targets when a ground path cannot reach them");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBetrayalGhastKeepsNativeAttack(GameTestHelper h) {
        var ghast = h.spawn(EntityType.GHAST, new BlockPos(2, 4, 2)); ghast.setNoAi(true);
        h.assertTrue(ghast.getAttribute(Attributes.ATTACK_DAMAGE) == null, "regression covers a non-melee enemy");
        h.assertTrue(ghast.goalSelector.getAvailableGoals().stream().noneMatch(g ->
                g.getGoal().getClass().getEnclosingClass() == YujiAfflictions.class), "no synthetic melee goal on ghast");
        h.assertTrue(ghast.targetSelector.getAvailableGoals().stream().anyMatch(g ->
                g.getGoal().getClass().getEnclosingClass() == YujiAfflictions.class && g.getFlags().contains(Goal.Flag.TARGET)),
                "only native targeting is locked"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiResistancesAndBossImmunity(GameTestHelper h) {
        YujiMonster giant = monster(h, YujiKind.CHENNU), boss = monster(h, YujiKind.SHIFOTOU);
        LivingHurtEvent blast = new LivingHurtEvent(giant, giant.damageSources().explosion(giant, giant), 10);
        YujiAfflictions.damage(blast); close(h, blast.getAmount(), 20, "giant explosion multiplier");
        LivingHurtEvent ordinary = new LivingHurtEvent(boss, boss.damageSources().generic(), 10);
        YujiAfflictions.damage(ordinary); close(h, ordinary.getAmount(), 1, "boss normal reduction");
        LivingHurtEvent bossBlast = new LivingHurtEvent(boss, boss.damageSources().explosion(boss, boss), 10);
        YujiAfflictions.damage(bossBlast); close(h, bossBlast.getAmount(), 3, "boss explosion reduction");
        h.assertTrue(boss.isInvulnerableTo(boss.damageSources().inFire()) && boss.isInvulnerableTo(boss.damageSources().inWall())
                && boss.isInvulnerableTo(boss.damageSources().fall()), "boss environmental immunity"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiSpikeFanBurstAndExpiry(GameTestHelper h) {
        YujiMonster source = monster(h, YujiKind.EKOU); Player p = player(h, source); p.moveTo(source.position().add(0, 0, 6));
        source.volley(p, false);
        var spikes = h.getLevel().getEntitiesOfClass(BoneSpike.class, source.getBoundingBox().inflate(2));
        h.assertTrue(spikes.size() == 5, "five-spike fan");
        for (BoneSpike spike : spikes) { close(h, spike.getDeltaMovement().length(), 1.6, "launch speed"); spike.discard(); }
        source.volley(p, true); spikes = h.getLevel().getEntitiesOfClass(BoneSpike.class, source.getBoundingBox().inflate(2));
        h.assertTrue(spikes.size() == 8, "eight-spike burst");
        BoneSpike stuck = spikes.get(0); CompoundTag data = new CompoundTag(); stuck.addAdditionalSaveData(data);
        data.putBoolean("Stuck", true); data.putInt("StuckTicks", 199); stuck.readAdditionalSaveData(data); stuck.tick();
        h.assertTrue(stuck.isRemoved(), "stuck spike expires after ten seconds across reload"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiPassiveAndGliderSafety(GameTestHelper h) {
        YujiMonster idle = monster(h, YujiKind.YUCHI), lure = monster(h, YujiKind.XIEYIN), flute = monster(h, YujiKind.QIYU);
        Player p = player(h, idle);
        h.assertTrue(!idle.doHurtTarget(p) && !lure.doHurtTarget(p), "passive monsters have no melee attack");
        h.assertTrue(!flute.causeFallDamage(30, 1, flute.damageSources().fall()), "glider is fall immune");
        idle.setHealth(8); h.assertTrue(idle.crawling(), "low-health crawl state"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 80)
    public static void yujiDoubleSlashDealsTwoTimedHits(GameTestHelper h) {
        YujiMonster slasher = monster(h, YujiKind.SHASHENG);
        var cow = h.spawn(EntityType.COW, new BlockPos(3, 2, 2)); cow.setNoAi(true); cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20); cow.setHealth(20);
        slasher.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); slasher.setNoAi(false);
        slasher.begin("chop", 24, cow);
        h.runAtTickTime(9, () -> close(h, cow.getHealth(), 17, "first slash deals three damage"));
        h.runAtTickTime(17, () -> { close(h, cow.getHealth(), 14, "second slash is not swallowed by hurt immunity"); h.succeed(); });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 80)
    public static void yujiGreedCollectsAndConsumesOnlyOneFlesh(GameTestHelper h) {
        YujiMonster greed = monster(h, YujiKind.TANYU); greed.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); greed.setNoAi(false);
        for (ItemStack stack : new ItemStack[]{new ItemStack(Items.ROTTEN_FLESH, 3), new ItemStack(Items.DIAMOND, 10)}) {
            ItemEntity item = new ItemEntity(h.getLevel(), greed.getX(), greed.getY(), greed.getZ(), stack);
            item.setNoPickUpDelay(); item.setNoGravity(true); item.setDeltaMovement(0, 0, 0); h.getLevel().addFreshEntity(item);
        }
        h.runAtTickTime(15, () -> {
            h.assertTrue(greed.layers() == 3, "one consumed flesh adds one layer");
            CompoundTag data = new CompoundTag(); greed.addAdditionalSaveData(data); var stored = data.getList("YujiHoard", 10);
            int flesh = 0, diamonds = 0;
            for (int i = 0; i < stored.size(); i++) {
                ItemStack stack = ItemStack.of(stored.getCompound(i));
                if (stack.is(Items.ROTTEN_FLESH)) flesh += stack.getCount();
                if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
            }
            h.assertTrue(flesh == 2 && diamonds == 10, "unconsumed items remain complete in saved hoard"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGiantGrudgeSurvivesReload(GameTestHelper h) {
        YujiMonster giant = monster(h, YujiKind.CHENNU); Player p = player(h, giant);
        h.assertTrue(giant.getTarget() == null, "giant starts neutral");
        giant.hurt(giant.damageSources().playerAttack(p), 10);
        h.assertTrue(giant.getTarget() == p, "attacker becomes grudge target");
        CompoundTag data = new CompoundTag(); giant.addAdditionalSaveData(data);
        YujiMonster restored = monster(h, YujiKind.CHENNU); restored.readAdditionalSaveData(data);
        CompoundTag after = new CompoundTag(); restored.addAdditionalSaveData(after);
        h.assertTrue(after.hasUUID("YujiGrudge") && after.getUUID("YujiGrudge").equals(p.getUUID()), "grudge survives reload"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiCurseRampsAndCapsDamage(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var difficulty = h.getLevel().getDifficulty();
        try {
            // Mob-attributed magic is difficulty-scaled; test raw card values in Normal.
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            YujiMonster curse = monster(h, YujiKind.EKOU); Player p = player(h, curse);
            YujiAfflictions.apply(p, curse, "cursed"); state(p, "cursed", curse).putInt("Age", 200);
            for (int i = 0; i < 30; i++) { p.invulnerableTime = 0; YujiAfflictions.tickPlayer(p); }
            close(h, p.getHealth(), 18, "curse ramps to two damage");
            p.setHealth(20); state(p, "cursed", curse).putInt("Age", 2000);
            for (int i = 0; i < 30; i++) { p.invulnerableTime = 0; YujiAfflictions.tickPlayer(p); }
            close(h, p.getHealth(), 17, "curse damage capped at three"); h.succeed();
        } finally { server.setDifficulty(difficulty, true); }
    }

    private static void boundaryStep(YujiMonster mob) throws ReflectiveOperationException {
        var method = YujiMonster.class.getDeclaredMethod("customServerAiStep");
        method.setAccessible(true);
        method.invoke(mob);
    }

    private static net.minecraftforge.common.util.FakePlayer voicePlayer(GameTestHelper h, YujiMonster source) {
        var p = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "YujiVoiceFixture"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.moveTo(source.getX() - 12, source.getY(), source.getZ());
        p.setNoGravity(true);
        h.getLevel().addNewPlayer(p);
        h.assertTrue(h.getLevel().getPlayerByUUID(p.getUUID()) == p, "voice recipient is queryable in the real server level");
        return p;
    }

    private record VoiceFixture(YujiMonster source, java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks) {
        void restore() {
            blocks.forEach((pos, state) -> source.level().setBlock(pos, state, 2));
            source.discard();
        }
    }

    private static VoiceFixture voiceSource(GameTestHelper h) throws ReflectiveOperationException {
        YujiMonster source = monster(h, YujiKind.WANGYU);
        source.moveTo(source.getX(), h.getLevel().getMaxBuildHeight() - 32, source.getZ());
        BlockPos center = source.blockPosition().west(12);
        var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        // An enclosed listener makes every sampled 6-10 block voice source occluded.
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, 0, -5), center.offset(5, 4, 5))) {
            blocks.put(pos.immutable(), h.getLevel().getBlockState(pos));
            boolean wall = Math.abs(pos.getX() - center.getX()) == 4 || Math.abs(pos.getZ() - center.getZ()) == 4;
            h.getLevel().setBlock(pos, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        var cooldown = YujiMonster.class.getDeclaredField("skillCooldown");
        cooldown.setAccessible(true); cooldown.setInt(source, 10000);
        return new VoiceFixture(source, blocks);
    }

    private static net.minecraft.world.phys.Vec3 emitVoice(YujiMonster source) throws ReflectiveOperationException {
        var method = YujiMonster.class.getDeclaredMethod("mimic");
        method.setAccessible(true); method.invoke(source);
        var point = YujiMonster.class.getDeclaredField("falseVoice");
        point.setAccessible(true);
        return (net.minecraft.world.phys.Vec3) point.get(source);
    }

    private static void faceVoice(Player p, net.minecraft.world.phys.Vec3 point, boolean toward) {
        var direction = point.subtract(p.getEyePosition()).normalize().scale(toward ? 1 : -1);
        p.setYRot((float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90);
        p.setXRot((float) -Math.toDegrees(Math.asin(direction.y)));
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiFakeVoiceReactionStaysWithRecipient(GameTestHelper h) throws ReflectiveOperationException {
        var fixture = voiceSource(h); YujiMonster source = fixture.source();
        var first = voicePlayer(h, source); var second = voicePlayer(h, source);
        try {
            source.setTarget(first);
            var point = emitVoice(source);
            h.assertTrue(point != null && first.hasEffect(MobEffects.WEAKNESS), "real mimic emits to first recipient");
            h.assertTrue(first.getEffect(MobEffects.WEAKNESS).getAmplifier() == 0, "first unheard source starts at weakness I");
            faceVoice(first, point, false); faceVoice(second, point, true);
            source.setTarget(second); boundaryStep(source);
            h.assertTrue(source.getTarget() == second, "replacement remains the actual AI target");
            h.assertTrue(!second.hasEffect(MobEffects.WEAKNESS), "replacement target cannot react to another player's fake sound");
            faceVoice(first, point, true); boundaryStep(source);
            h.assertTrue(first.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1, "original listener can still react after AI retargets");
        } finally { first.discard(); second.discard(); fixture.restore(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiFakeVoiceMissesDoNotCrossRecipients(GameTestHelper h) throws ReflectiveOperationException {
        var fixture = voiceSource(h); YujiMonster source = fixture.source();
        var first = voicePlayer(h, source); var second = voicePlayer(h, source);
        try {
            source.setTarget(first); emitVoice(source); emitVoice(source);
            h.assertTrue(first.hasEffect(MobEffects.WEAKNESS) && first.getEffect(MobEffects.WEAKNESS).getAmplifier() == 0,
                    "two missed sounds do not yet reach the third-sound threshold");
            source.setTarget(second); emitVoice(source);
            h.assertTrue(second.hasEffect(MobEffects.WEAKNESS) && second.getEffect(MobEffects.WEAKNESS).getAmplifier() == 0,
                    "new recipient does not inherit the first listener's two misses");
            emitVoice(source); emitVoice(source);
            h.assertTrue(second.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1
                    && state(second, "weakness", source).getInt("Remaining") == 400,
                    "three actual missed sounds still give weakness II for twenty seconds");
        } finally { first.discard(); second.discard(); fixture.restore(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiFakeVoiceWindowExpiresWithoutTarget(GameTestHelper h) throws ReflectiveOperationException {
        var fixture = voiceSource(h); YujiMonster source = fixture.source();
        var listener = voicePlayer(h, source);
        try {
            source.setTarget(listener); var point = emitVoice(source);
            h.assertTrue(point != null && listener.hasEffect(MobEffects.WEAKNESS), "real mimic opens a reaction window");
            var window = YujiMonster.class.getDeclaredField("voiceWindow");
            window.setAccessible(true);
            h.assertTrue(window.getInt(source) == 40, "reaction window begins at forty ticks");
            var start = listener.position();
            listener.moveTo(start.add(100, 0, 0)); faceVoice(listener, point, false); source.setTarget(null);
            for (int tick = 0; tick < 40; tick++) {
                boundaryStep(source);
                h.assertTrue(source.getTarget() == null, "listener stays outside automatic target acquisition");
            }
            h.assertTrue(window.getInt(source) == 0, "reaction window expires after forty production AI steps without a target");
            listener.moveTo(start); faceVoice(listener, point, true); source.setTarget(listener); boundaryStep(source);
            h.assertTrue(listener.getEffect(MobEffects.WEAKNESS).getAmplifier() == 0,
                    "lost AI target cannot pause the two-second reaction deadline");
        } finally { listener.discard(); fixture.restore(); }
        h.succeed();
    }

    private static void greedBiteSound(GameTestHelper h, boolean inReach) throws ReflectiveOperationException {
        YujiMonster greed = monster(h, YujiKind.TANYU);
        // Test grids may start underground; a relative +64 can still leave the real hit ray in terrain.
        greed.moveTo(greed.getX(), h.getLevel().getMaxBuildHeight() - 16, greed.getZ());
        BlockPos start = greed.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(start.offset(-1, 0, -1), start.offset(9, 4, 1)))
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        var cow = h.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setNoAi(true); cow.setNoGravity(true);
        cow.moveTo(greed.position().add(inReach ? 1 : 8, 0, 0));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20); cow.setHealth(20);
        h.assertTrue(h.getLevel().getEntity(cow.getUUID()) == cow, "bite target registered in the world");
        h.assertTrue(greed.hasLineOfSight(cow), "unobstructed bite fixture: " + greed.position() + " -> " + cow.position());
        h.assertTrue((greed.distanceTo(cow) <= greed.getBbWidth() + cow.getBbWidth() + 1) == inReach,
                "bite fixture is at the intended real attack range");
        var heardAt = new java.util.ArrayList<Integer>();
        int[] actionTick = {-1};
        java.util.function.Consumer<net.minecraftforge.event.PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == h.getLevel() && event.getPosition().distanceToSqr(greed.position()) < .01
                    && event.getSound() != null && event.getSound().value() == YujiContent.sound("entity.qihai.tanyu.bite")) {
                h.assertTrue(event.getSource() == net.minecraft.sounds.SoundSource.HOSTILE && event.getNewVolume() > 0,
                        "audible hostile bite sent through the real level sound event");
                heardAt.add(actionTick[0]);
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            greed.setTarget(cow); greed.begin("grab_bite", 24, cow);
            for (int tick = 0; tick < 24; tick++) {
                actionTick[0] = tick; boundaryStep(greed);
                h.assertTrue(heardAt.size() == (tick < 12 ? 0 : 1), "bite emitted exactly once at action tick 12: " + heardAt);
                close(h, 20 - cow.getHealth(), inReach && tick >= 12 ? 5 : 0, "bite damage timing and range at tick " + tick);
            }
            System.out.println("YUJI_GREED_BITE inReach=" + inReach + ", soundTicks=" + heardAt + ", damage=" + (20 - cow.getHealth()));
            h.succeed();
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            cow.discard(); greed.discard();
        }
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGreedBiteSoundMatchesHitTick(GameTestHelper h) throws ReflectiveOperationException { greedBiteSound(h, true); }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGreedMissStillPlaysBiteOnce(GameTestHelper h) throws ReflectiveOperationException { greedBiteSound(h, false); }
    private static void grudgeIs(GameTestHelper h, YujiMonster giant, Player expected) {
        CompoundTag data = new CompoundTag(); giant.addAdditionalSaveData(data);
        h.assertTrue(data.hasUUID("YujiGrudge") && data.getUUID("YujiGrudge").equals(expected.getUUID())
                && giant.getTarget() == expected, "R12 original valid grudge and target remain locked");
    }
    private static void playerHit(GameTestHelper h, YujiMonster giant, Player attacker) {
        giant.invulnerableTime = 0;
        h.assertTrue(giant.hurt(giant.damageSources().playerAttack(attacker), 10), "player hit accepted");
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiOriginalGrudgeSurvivesSecondAttacker(GameTestHelper h) {
        YujiMonster giant = monster(h, YujiKind.CHENNU);
        Player first = player(h, giant), second = player(h, giant);
        playerHit(h, giant, first); grudgeIs(h, giant, first);
        playerHit(h, giant, second); grudgeIs(h, giant, first);
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiReloadedGrudgeIgnoresSecondAttacker(GameTestHelper h) {
        YujiMonster giant = monster(h, YujiKind.CHENNU);
        // Forge's vanilla mock-login helper has no Netty channel; register its supported test player directly.
        var first = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "YujiGrudgeFixture"));
        first.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        first.moveTo(giant.position()); first.setNoGravity(true);
        h.getLevel().addNewPlayer(first);
        Player second = player(h, giant);
        h.assertTrue(h.getLevel().getPlayerByUUID(first.getUUID()) == first, "original player is queryable after reload");
        try {
            playerHit(h, giant, first);
            CompoundTag saved = new CompoundTag(); giant.addAdditionalSaveData(saved); giant.discard();
            YujiMonster restored = monster(h, YujiKind.CHENNU); restored.readAdditionalSaveData(saved);
            playerHit(h, restored, second); grudgeIs(h, restored, first);
            h.succeed();
        } finally { first.discard(); }
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiGrudgeClearsOnlyBeyond128Blocks(GameTestHelper h) {
        YujiMonster giant = monster(h, YujiKind.CHENNU);
        Player first = player(h, giant), second = player(h, giant);
        playerHit(h, giant, first);
        first.moveTo(giant.position().add(128, 0, 0));
        playerHit(h, giant, second); grudgeIs(h, giant, first);
        first.moveTo(giant.position().add(128.01, 0, 0));
        playerHit(h, giant, second); grudgeIs(h, giant, second);
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiDeadGrudgeAllowsNewAttacker(GameTestHelper h) {
        YujiMonster giant = monster(h, YujiKind.CHENNU);
        Player first = player(h, giant), second = player(h, giant);
        playerHit(h, giant, first); first.setHealth(0);
        playerHit(h, giant, second); grudgeIs(h, giant, second);
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBetrayalBuffAffectsBothTimedSlashes(GameTestHelper h) throws ReflectiveOperationException {
        YujiMonster blade = monster(h, YujiKind.SHASHENG), source = monster(h, YujiKind.LIANGSHE);
        Player p = player(h, blade);
        YujiAfflictions.apply(p, source, "betrayed"); YujiAfflictions.recruit(p, true);
        var cow = h.spawn(EntityType.COW, new BlockPos(3, 2, 2)); cow.setNoAi(true); cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20); cow.setHealth(20);
        try {
            close(h, blade.getAttributeValue(Attributes.ATTACK_DAMAGE), 3.9, "real betrayal attack attribute");
            blade.setTarget(cow); blade.begin("chop", 24, cow);
            for (int tick = 0; tick <= 12; tick++) {
                boundaryStep(blade);
                close(h, 20 - cow.getHealth(), tick < 6 ? 0 : tick < 12 ? 3.9 : 7.8,
                        "R04/R08 actual damage at action tick " + tick);
            }
            h.succeed();
        } finally { YujiAfflictions.restore(blade); }
    }
    private static void bossContact(GameTestHelper h, double x, double y, boolean touching) throws ReflectiveOperationException {
        var server = h.getLevel().getServer(); var difficulty = h.getLevel().getDifficulty();
        YujiMonster boss = monster(h, YujiKind.SHIFOTOU); Player p = player(h, boss);
        p.moveTo(boss.position().add(x, y, 0));
        h.assertTrue(h.getLevel().addFreshEntity(p), "contact player added to world entity queries");
        try {
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            h.assertTrue(boss.getBoundingBox().intersects(p.getBoundingBox()) == touching, "actual AABB contact control");
            h.assertTrue(h.getLevel().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(64))
                    .contains(p), "positive control: player is queryable");
            boss.tickCount = 19; boundaryStep(boss); close(h, p.getHealth(), 20, "no damage before interval");
            boss.tickCount = 20; boundaryStep(boss); close(h, p.getHealth(), touching ? 10 : 20, "R13 contact damage is ten");
            p.invulnerableTime = 0;
            for (int tick = 21; tick < 40; tick++) { boss.tickCount = tick; boundaryStep(boss); }
            close(h, p.getHealth(), touching ? 10 : 20, "no duplicate damage within the second");
            p.setHealth(20); p.invulnerableTime = 0;
            boss.tickCount = 40; boundaryStep(boss); close(h, p.getHealth(), touching ? 10 : 20, "next second contact damage");
            h.succeed();
        } finally { p.discard(); server.setDifficulty(difficulty, true); }
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBossCrushesSideContact(GameTestHelper h) throws ReflectiveOperationException { bossContact(h, 15.2, 0, true); }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBossCrushesUpperContact(GameTestHelper h) throws ReflectiveOperationException { bossContact(h, 0, 39.8, true); }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBossDoesNotCrushOutsideBody(GameTestHelper h) throws ReflectiveOperationException { bossContact(h, 15.31, 0, false); }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBossDoesNotCrushAboveBody(GameTestHelper h) throws ReflectiveOperationException { bossContact(h, 0, 40.01, false); }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiBossStatsRaisedPastVanillaCaps(GameTestHelper h) {
        YujiMonster boss = monster(h, YujiKind.SHIFOTOU);
        close(h, boss.getMaxHealth(), 12000, "boss health above 6000 baseline");
        close(h, boss.getHealth(), 12000, "boss spawns at full health");
        close(h, boss.getAttributeValue(Attributes.ATTACK_DAMAGE), 260, "boss damage above 150 baseline");
        close(h, boss.getAttributeValue(Attributes.ARMOR), 60, "boss armor not clamped to vanilla 30");
        close(h, boss.getArmorValue(), 60, "armor value used by combat rules");
        close(h, boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 20, "boss armor toughness");
        h.succeed();
    }
    private static YujiMonster rangedChanter(GameTestHelper h) {
        YujiMonster chanter = monster(h, YujiKind.EKOU);
        // Keep the ray in the ticketed chunk, clear of both generated terrain and other batch fixtures.
        BlockPos origin = chanter.blockPosition();
        chanter.moveTo((origin.getX() >> 4) * 16 + 1.5, h.getLevel().getMaxBuildHeight() - 16,
                (origin.getZ() >> 4) * 16 + 1.5);
        BlockPos start = chanter.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(start.offset(-1, 0, -1), start.offset(1, 3, 13)))
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        chanter.setInvulnerable(true);
        return chanter;
    }
    private static net.minecraft.world.entity.animal.Cow chantTarget(GameTestHelper h, YujiMonster chanter) {
        var cow = h.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setNoAi(true); cow.setNoGravity(true);
        cow.moveTo(chanter.position().add(0, 0, 12));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); cow.setHealth(40);
        h.assertTrue(h.getLevel().getEntity(cow.getUUID()) == cow, "chant target registered in the world");
        h.assertTrue(chanter.hasLineOfSight(cow), "unobstructed ranged chant fixture: "
                + chanter.position() + " -> " + cow.position());
        h.assertTrue(chanter.distanceTo(cow) > 10, "target well outside melee reach");
        return cow;
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 200)
    public static void yujiEkouChantHitsFromRange(GameTestHelper h) {
        YujiMonster chanter = rangedChanter(h);
        var cow = chantTarget(h, chanter);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(chanter.blockPosition())
                && h.getLevel().isPositionEntityTicking(cow.blockPosition()), "chant fixture is entity-ticking"))
                .thenExecute(() -> {
                    chanter.setNoAi(false); chanter.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                    chanter.begin("chant", 40, cow);
                    h.runAfterDelay(24, () -> close(h, cow.getHealth(), 34, "first chant hit lands at range"));
                    h.runAfterDelay(50, () -> { close(h, cow.getHealth(), 28, "second chant hit lands at range"); h.succeed(); });
                });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 60)
    public static void yujiEkouChantSurvivesExpiredCooldown(GameTestHelper h) throws ReflectiveOperationException {
        YujiMonster chanter = rangedChanter(h);
        var cow = chantTarget(h, chanter);
        // Keep a valid AI target when the initial skill cooldown expires midway through this cast.
        chanter.setTarget(cow);
        chanter.begin("chant", 40, cow);
        int cast = chanter.actionSerial();
        // Exercise every production action tick, independently of asynchronous chunk activation.
        for (int tick = 0; tick < 40; tick++) {
            boundaryStep(chanter);
            // On tick 39 the 40-tick cast has finished and a ready next cast is allowed.
            h.assertTrue(chanter.actionSerial() == cast + (tick == 39 ? 1 : 0),
                    "cast serial at action tick " + tick + ": " + chanter.actionSerial());
            close(h, cow.getHealth(), tick < 15 ? 40 : tick < 32 ? 34 : 28,
                    "exact chant damage at action tick " + tick);
            cow.tick(); // Advance the real hurt-immunity clock between the two pulses.
        }
        h.succeed();
    }
    private static void summonFloor(GameTestHelper h, YujiMonster qiyu, net.minecraft.world.level.block.state.BlockState state) {
        BlockPos base = qiyu.blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-6, 0, -6), base.offset(6, 0, 6))) h.getLevel().setBlockAndUpdate(pos, state);
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-6, 1, -6), base.offset(6, 8, 6)))
            if (!pos.equals(qiyu.blockPosition())) h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiQiyuSummonsCappedOtherKinds(GameTestHelper h) {
        YujiMonster qiyu = monster(h, YujiKind.QIYU);
        // A private floor high above the batch grid: summons land 2-4 blocks out, beyond this tiny template.
        qiyu.moveTo(qiyu.getX(), Math.floor(qiyu.getY()) + 64, qiyu.getZ());
        summonFloor(h, qiyu, Blocks.STONE.defaultBlockState());
        try {
            int total = 0;
            for (int cast = 0; cast < 8; cast++) total += qiyu.summonAllies();
            h.assertTrue(total == YujiMonster.SUMMON_CAP, "summons fill exactly to the cap: " + total);
            h.assertTrue(qiyu.livingSummons() == YujiMonster.SUMMON_CAP, "cap counted from living summons");
            h.assertTrue(qiyu.summonAllies() == 0, "no summons beyond cap");
            var summons = h.getLevel().getEntitiesOfClass(YujiMonster.class, qiyu.getBoundingBox().inflate(32), y -> qiyu.getUUID().equals(y.summoner()));
            for (YujiMonster s : summons) {
                h.assertTrue(s.kind() != YujiKind.QIYU && !s.kind().giant(), "summons other non-giant kinds: " + s.kind());
                CompoundTag tag = new CompoundTag(); s.addAdditionalSaveData(tag);
                h.assertTrue(tag.hasUUID("YujiSummoner") && tag.getUUID("YujiSummoner").equals(qiyu.getUUID()), "summoner persisted");
            }
            summons.get(0).discard();
            h.assertTrue(qiyu.livingSummons() == YujiMonster.SUMMON_CAP - 1, "dead summons free a slot");
            h.assertTrue(qiyu.summonAllies() == 1, "refills only the freed slot");
            for (YujiMonster s : h.getLevel().getEntitiesOfClass(YujiMonster.class, qiyu.getBoundingBox().inflate(32), y -> qiyu.getUUID().equals(y.summoner()))) s.discard();
            h.succeed();
        } finally { summonFloor(h, qiyu, Blocks.AIR.defaultBlockState()); }
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji")
    public static void yujiQiyuNeedsAudienceAndNotPeaceful(GameTestHelper h) {
        YujiMonster qiyu = monster(h, YujiKind.QIYU);
        var server = h.getLevel().getServer(); var difficulty = h.getLevel().getDifficulty();
        try {
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            h.assertTrue(!qiyu.canSummon(), "no summoning without a target or nearby player");
            var cow = h.spawn(EntityType.COW, new BlockPos(3, 2, 2)); cow.setNoAi(true);
            qiyu.setTarget(cow); h.assertTrue(qiyu.canSummon(), "summons once engaged");
            server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            h.assertTrue(!qiyu.canSummon(), "never summons on peaceful");
            h.succeed();
        } finally { server.setDifficulty(difficulty, true); }
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 60)
    public static void yujiQiyuBlessBuffsNearbyAllies(GameTestHelper h) {
        YujiMonster qiyu = monster(h, YujiKind.QIYU), ally = monster(h, YujiKind.SHASHENG);
        qiyu.setNoAi(false); qiyu.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        qiyu.begin("bless", 30, null);
        h.runAtTickTime(18, () -> {
            h.assertTrue(ally.hasEffect(MobEffects.DAMAGE_BOOST) && ally.hasEffect(MobEffects.MOVEMENT_SPEED)
                    && ally.hasEffect(MobEffects.DAMAGE_RESISTANCE) && ally.hasEffect(MobEffects.REGENERATION), "ally receives four buffs");
            h.assertTrue(!qiyu.hasEffect(MobEffects.DAMAGE_BOOST), "aura buffs allies, not itself"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 120)
    public static void yujiQiyuAuraRetriesWhileBusy(GameTestHelper h) {
        YujiMonster qiyu = monster(h, YujiKind.QIYU), ally = monster(h, YujiKind.YUCHI);
        qiyu.setNoAi(false); qiyu.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        // Occupy it exactly when the first aura is due (skill cooldown 20): it must retry, not wait 240 ticks.
        h.runAtTickTime(15, () -> qiyu.begin("strike", 24, null));
        h.runAtTickTime(90, () -> { h.assertTrue(ally.hasEffect(MobEffects.DAMAGE_BOOST), "aura fires after a busy action ends"); h.succeed(); });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 100)
    public static void yujiGiantDeathAnimationCompletes(GameTestHelper h) {
        YujiMonster boss = monster(h, YujiKind.SHIFOTOU);
        boss.hurt(boss.damageSources().genericKill(), 100000);
        h.runAtTickTime(55, () -> h.assertTrue(!boss.isRemoved(), "death animation retains entity before completion"));
        h.runAtTickTime(85, () -> { h.assertTrue(boss.isRemoved(), "boss removed after death animation completes"); h.succeed(); });
    }
}
