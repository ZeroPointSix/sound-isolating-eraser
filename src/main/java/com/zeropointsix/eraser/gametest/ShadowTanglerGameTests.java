package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.shadow.ShadowLight;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
import com.zeropointsix.eraser.shaxia.ShaxiaTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowTanglerGameTests {
    // GameTest places template y=0 at helper y=1, above its structure block.
    private static final BlockPos CENTER = new BlockPos(12, 2, 12);

    private static ShadowTanglerEntity spawn(GameTestHelper h, int brightness, boolean noAi) {
        h.assertTrue(!h.getBlockState(CENTER.below()).getCollisionShape(h.getLevel(), h.absolutePos(CENTER.below())).isEmpty(),
                "arena has solid support beneath the spawn position");
        h.assertTrue(h.getBlockState(CENTER).isAir() && h.getBlockState(CENTER.above()).isAir(),
                "spawn and light source are above the floor, not inside it");
        h.setBlock(CENTER, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness));
        ShadowTanglerEntity mob = h.spawn(ModEntities.SHADOW_TANGLER.get(), CENTER);
        mob.setNoAi(noAi);
        h.assertTrue(h.getLevel().noCollision(mob, mob.getBoundingBox()), "spawn has full collision clearance");
        return mob;
    }

    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.001D, message + ": " + actual + " != " + expected);
    }

    private static String escapeState(ShadowTanglerEntity mob) {
        var path = mob.getNavigation().getPath();
        StringBuilder result = new StringBuilder("position=").append(mob.position())
                .append(" tier=").append(mob.getLightTier()).append(" rawLight=").append(mob.brightnessAt(mob.blockPosition()))
                .append(" age=").append(mob.getSpawnAge()).append(" ticks=").append(mob.tickCount)
                .append(" flee=").append(mob.wantsToFlee()).append(" grounded=").append(mob.onGround())
                .append(" motion=").append(mob.getDeltaMovement()).append(" navigationDone=").append(mob.getNavigation().isDone());
        if (path == null) return result.append(" path=null").toString();
        result.append(" reachable=").append(path.canReach()).append(" next=").append(path.getNextNodeIndex())
                .append(" target=").append(path.getTarget()).append(" nodes=");
        for (int i = 0; i < path.getNodeCount(); i++) result.append(path.getNode(i).asBlockPos()).append(';');
        return result.toString();
    }

    private static void assertPromptEscape(GameTestHelper h, ShadowTanglerEntity mob, Vec3 origin) {
        h.assertTrue(mob.isAlive() && mob.getTarget() == null, "escape remains alive without a combat target");
        boolean dark = mob.getLightTier() == ShadowLight.DARK;
        // After the 24-tick spawn lock, vanilla's 0.10 speed moves about 0.7 blocks by tick 60.
        h.assertTrue(mob.position().subtract(origin).horizontalDistanceSqr() >= 0.25 || dark,
                "prompt horizontal escape progress: " + escapeState(mob));
        var path = mob.getNavigation().getPath();
        h.assertTrue(dark || (path != null && path.canReach() && !mob.getNavigation().isDone()),
                "escape follows a reachable path: " + escapeState(mob));
    }

    @GameTest(template = "shadow_arena")
    public static void shadowRegistrationAndSpawnLock(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 0, false);
        double x = mob.getX(), z = mob.getZ();
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 12));
        target.setNoAi(true);
        mob.setTarget(target);
        h.assertTrue(mob.getTarget() == null && !mob.doHurtTarget(target), "spawn prevents targeting and attacks");
        close(h, mob.getMaxHealth(), 20, "health");
        close(h, mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), 0.4, "base speed");
        close(h, mob.getAttributeBaseValue(Attributes.ARMOR), 0, "armor");
        close(h, mob.getBbWidth(), 0.6, "width");
        close(h, mob.getBbHeight(), 2, "height");
        h.assertTrue(ModItems.SHADOW_TANGLER_SPAWN_EGG.get().getColor(0) == 0xFFFFFF
                && ModItems.SHADOW_TANGLER_SPAWN_EGG.get().getColor(1) == 0xFFFFFF, "original egg art remains untinted");
        h.runAtTickTime(20, () -> {
            h.assertTrue(mob.isSpawning(), "first 24 ticks stay locked");
            close(h, mob.getX(), x, "spawn x");
            close(h, mob.getZ(), z, "spawn z");
        });
        h.runAtTickTime(26, () -> {
            h.assertTrue(!mob.isSpawning(), "spawn ends after 24 ticks");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena")
    public static void shadowDarkResistanceAndMelee(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 0, true);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 12));
        target.setNoAi(true);
        h.runAtTickTime(30, () -> {
            mob.refreshLightTier();
            h.assertTrue(mob.getLightTier() == ShadowLight.DARK, "enclosed room is dark");
            close(h, mob.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.4, "dark speed");
            mob.hurt(mob.damageSources().generic(), 8);
            close(h, mob.getHealth(), 16, "dark external damage halved");
            h.assertTrue(mob.doHurtTarget(target), "adult dark mob can hit");
            var slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            h.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() == 20, "one second slowness II");
            h.assertTrue(ShaxiaTargets.eligible(mob), "knife enemy fallback includes shadow");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena")
    public static void shadowBrightDamageAndHitProtection(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 11, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        h.runAtTickTime(30, () -> {
            mob.refreshLightTier();
            h.assertTrue(mob.getLightTier() == ShadowLight.BRIGHT, "brightness 11 is bright");
            close(h, mob.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.1, "bright speed");
            mob.invulnerableTime = 0;
            float before = mob.getHealth();
            h.assertTrue(mob.hurt(mob.damageSources().generic(), 4), "ordinary first hit");
            close(h, before - mob.getHealth(), 8, "bright external damage doubled");
            int protection = mob.invulnerableTime;
            mob.burnInLight();
            close(h, before - mob.getHealth(), 10, "light tick adds exactly two, not four");
            h.assertTrue(mob.invulnerableTime == protection, "light preserves existing protection");
            h.assertTrue(!mob.hurt(mob.damageSources().generic(), 4), "same hit remains protected");
            h.assertTrue(mob.hurt(mob.damageSources().generic(), 6), "stronger hit keeps vanilla difference rule");
            close(h, before - mob.getHealth(), 14, "light did not corrupt last-hit bookkeeping");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena", timeoutTicks = 140)
    public static void shadowDimRetreatAndPersistence(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 8, true);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 12));
        target.setNoAi(true);
        h.runAtTickTime(30, () -> {
            mob.refreshLightTier();
            h.assertTrue(mob.getLightTier() == ShadowLight.DIM, "brightness eight is dim");
            close(h, mob.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.24, "dim speed");
            mob.setTarget(target);
            h.assertTrue(mob.getTarget() == target, "can briefly pursue into dim light");
            mob.hurt(mob.damageSources().generic(), 4);
            close(h, mob.getHealth(), 16, "dim damage unchanged");
        });
        h.runAtTickTime(85, () -> {
            h.assertTrue(mob.isRetreating() && mob.getTarget() == null, "three seconds dim triggers retreat");
            CompoundTag saved = new CompoundTag();
            mob.saveWithoutId(saved);
            ShadowTanglerEntity loaded = ModEntities.SHADOW_TANGLER.get().create(h.getLevel());
            loaded.load(saved);
            h.assertTrue(!loaded.isSpawning() && loaded.isRetreating(), "reload does not replay spawn or erase retreat");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena", timeoutTicks = 100)
    public static void shadowDarkDeathAnimation(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 0, true);
        h.runAtTickTime(30, () -> {
            mob.hurt(mob.damageSources().genericKill(), 1000);
            h.assertTrue(!mob.isAlive() && !mob.isRemoved(), "dark death remains for its animation");
            h.assertTrue(mob.getLootTable().equals(ShadowTanglerEntity.id("entities/shadow_tangler")), "ink loot selected");
        });
        h.runAtTickTime(55, () -> {
            h.assertTrue(mob.isRemoved(), "dark corpse removed after 20 ticks");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena")
    public static void shadowBrightDeathShattersImmediately(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 15, true);
        h.runAtTickTime(30, () -> {
            var player = h.makeMockSurvivalPlayer();
            mob.hurt(mob.damageSources().playerAttack(player), 1000);
            h.assertTrue(mob.isRemoved(), "bright death removes immediately");
            h.assertTrue(mob.getLootTable().equals(ShadowTanglerEntity.id("entities/shadow_tangler_shattered")), "glass loot selected");
            int experience = h.getLevel().getEntitiesOfClass(ExperienceOrb.class,
                    mob.getBoundingBox().inflate(2)).stream().mapToInt(orb -> {
                        CompoundTag saved = new CompoundTag();
                        orb.saveWithoutId(saved);
                        return orb.getValue() * Math.max(1, saved.getInt("Count"));
                    }).sum();
            h.assertTrue(experience == 5, "shattering awards five XP exactly once: " + experience);
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena", timeoutTicks = 400)
    public static void shadowEscapesBrightArea(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 15, false);
        var origin = mob.position();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        h.runAtTickTime(60, () -> assertPromptEscape(h, mob, origin));
        h.runAtTickTime(100, () -> h.assertTrue(mob.position().subtract(origin).horizontalDistanceSqr() >= 1
                || mob.getLightTier() == ShadowLight.DARK, "escape continues beyond one block: " + escapeState(mob)));
        h.runAtTickTime(300, () -> {
            h.assertTrue(mob.isAlive() && mob.getLightTier() == ShadowLight.DARK, "real navigation reaches a dark refuge");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void shadowExactLightBoundaries(GameTestHelper h) {
        for (int light = 0; light <= 15; light++) {
            int expected = light <= 6 ? ShadowLight.DARK : light <= 10 ? ShadowLight.DIM : ShadowLight.BRIGHT;
            h.assertTrue(ShadowLight.tier(light) == expected, "light boundary " + light);
        }
        h.succeed();
    }

    private static void knifeDamage(GameTestHelper h, int light, float expected) {
        ShadowTanglerEntity mob = spawn(h, light, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        h.runAtTickTime(30, () -> {
            var player = h.makeMockSurvivalPlayer();
            player.setUUID(java.util.UUID.randomUUID());
            var stack = ModItems.SHAXIADAO.get().getDefaultInstance();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
            player.setOnGround(true);
            player.moveTo(h.absolutePos(new BlockPos(10, 2, 12)), 0, 0);
            ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, player, 100, "f_20922_");
            mob.invulnerableTime = 0;
            float before = mob.getHealth();
            player.attack(mob);
            close(h, before - mob.getHealth(), expected, "both knife segments use light multiplier");
            h.assertTrue(stack.getDamageValue() == 1, "one durability per actual knife strike");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena")
    public static void shadowKnifeDark(GameTestHelper h) { knifeDamage(h, 0, 4.25F); }

    @GameTest(template = "shadow_arena")
    public static void shadowKnifeDim(GameTestHelper h) { knifeDamage(h, 8, 8.5F); }

    @GameTest(template = "shadow_arena")
    public static void shadowKnifeBright(GameTestHelper h) { knifeDamage(h, 15, 17F); }

    @GameTest(template = "shadow_arena", timeoutTicks = 400)
    public static void shadowEscapesAcrossGlass(GameTestHelper h) {
        for (int x = 1; x < 24; x++) for (int z = 1; z < 24; z++) {
            h.setBlock(new BlockPos(x, 1, z), Blocks.GLASS);
        }
        ShadowTanglerEntity mob = spawn(h, 15, false);
        var origin = mob.position();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        h.runAtTickTime(60, () -> assertPromptEscape(h, mob, origin));
        h.runAtTickTime(100, () -> h.assertTrue(mob.position().subtract(origin).horizontalDistanceSqr() >= 1
                || mob.getLightTier() == ShadowLight.DARK, "glass escape continues beyond one block: " + escapeState(mob)));
        h.runAtTickTime(300, () -> {
            h.assertTrue(mob.isAlive() && mob.getLightTier() == ShadowLight.DARK, "glass floor is a valid dark refuge");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena", timeoutTicks = 160)
    public static void shadowMeleeWindupAndInterval(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 0, false);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 12));
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(200);
        int[] lastSwing = {-1}, previousHit = {-1}, hits = {0};
        boolean[] swinging = {false};
        float[] previousHealth = {200};
        for (int t = 1; t <= 130; t++) {
            final int tick = t;
            h.runAtTickTime(t, () -> {
                if (mob.swinging && !swinging[0]) lastSwing[0] = tick;
                swinging[0] = mob.swinging;
                if (target.getHealth() < previousHealth[0]) {
                    h.assertTrue(lastSwing[0] >= 24 && Math.abs(tick - lastSwing[0] - 5) <= 1,
                            "native AI waits for spawn and five-tick attack windup");
                    h.assertTrue(previousHit[0] < 0 || Math.abs(tick - previousHit[0] - 20) <= 1,
                            "stationary target receives one native hit every 20 ticks");
                    previousHit[0] = tick;
                    previousHealth[0] = target.getHealth();
                    hits[0]++;
                }
                if (tick == 130) {
                    h.assertTrue(hits[0] >= 2, "native AI actually attacked multiple times");
                    h.succeed();
                }
            });
        }
    }

    @GameTest(template = "shadow_arena", timeoutTicks = 200)
    public static void shadowRetreatCooldownStartsInDark(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 15, true);
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 12));
        target.setNoAi(true);
        h.runAtTickTime(30, () -> h.setBlock(CENTER,
                Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 8)));
        h.runAtTickTime(80, () -> {
            h.assertTrue(mob.getLightTier() == ShadowLight.DIM && mob.isRetreating(),
                    "retreat cannot expire before reaching dark ground");
            mob.setTarget(target);
            h.assertTrue(mob.getTarget() == null, "dim retreat cannot reacquire a target");
            h.setBlock(CENTER, Blocks.AIR);
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(mob.getLightTier() == ShadowLight.DARK && mob.isRetreating(),
                    "dark refuge retains a short cooldown");
        });
        h.runAtTickTime(140, () -> {
            h.assertTrue(!mob.isRetreating(), "dark cooldown eventually expires");
            mob.setTarget(target);
            h.assertTrue(mob.getTarget() == target, "pursuit resumes after cooling down in darkness");
            h.succeed();
        });
    }

    @GameTest(template = "shadow_arena", timeoutTicks = 150)
    public static void shadowAutomaticLightBurnCadence(GameTestHelper h) {
        ShadowTanglerEntity mob = spawn(h, 15, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        float[] before = {0}, afterDark = {0};
        h.runAtTickTime(35, () -> before[0] = mob.getHealth());
        h.runAtTickTime(95, () -> {
            close(h, before[0] - mob.getHealth(), 6, "three automatic burns in 60 ticks");
            h.setBlock(CENTER, Blocks.AIR);
        });
        h.runAtTickTime(110, () -> {
            h.assertTrue(mob.getLightTier() == ShadowLight.DARK, "light removal propagated");
            afterDark[0] = mob.getHealth();
        });
        h.runAtTickTime(140, () -> {
            close(h, mob.getHealth(), afterDark[0], "leaving bright light stops automatic damage");
            h.succeed();
        });
    }
}
