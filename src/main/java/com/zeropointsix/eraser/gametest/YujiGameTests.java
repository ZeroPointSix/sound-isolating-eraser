package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.BoneSpike;
import com.zeropointsix.eraser.yuji.YujiAfflictions;
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
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 80)
    public static void yujiEkouChantHitsFromRange(GameTestHelper h) {
        YujiMonster chanter = monster(h, YujiKind.EKOU);
        var cow = h.spawn(EntityType.COW, new BlockPos(2, 2, 2)); cow.setNoAi(true); cow.setNoGravity(true);
        cow.moveTo(chanter.position().add(0, 0, 12)); cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); cow.setHealth(40);
        chanter.setNoAi(false); chanter.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        h.assertTrue(chanter.distanceTo(cow) > 10, "target well outside melee reach");
        chanter.begin("chant", 40, cow);
        // Hits are scheduled at chant ticks 15 and 32; sample with slack for batch start-up jitter.
        h.runAtTickTime(24, () -> close(h, cow.getHealth(), 34, "first chant hit lands at range"));
        h.runAtTickTime(50, () -> { close(h, cow.getHealth(), 28, "second chant hit lands at range"); h.succeed(); });
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
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji", timeoutTicks = 100)
    public static void yujiGiantDeathAnimationCompletes(GameTestHelper h) {
        YujiMonster boss = monster(h, YujiKind.SHIFOTOU);
        boss.hurt(boss.damageSources().genericKill(), 100000);
        h.runAtTickTime(55, () -> h.assertTrue(!boss.isRemoved(), "death animation retains entity before completion"));
        h.runAtTickTime(85, () -> { h.assertTrue(boss.isRemoved(), "boss removed after death animation completes"); h.succeed(); });
    }
}
