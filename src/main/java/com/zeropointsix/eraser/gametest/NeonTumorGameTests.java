package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.monster.LightTier;
import com.zeropointsix.eraser.neon.CorrosionDamage;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import com.zeropointsix.eraser.neon.NeonTumorEvents;
import com.zeropointsix.eraser.neon.TendrilSlamGoal;
import com.zeropointsix.eraser.neon.TumorLeapGoal;
import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeonTumorGameTests {
    private static NeonTumorEntity tumor(GameTestHelper h, int size) {
        NeonTumorEntity tumor = h.spawn(ModEntities.NEON_TUMOR.get(), new BlockPos(2, 2, 2));
        tumor.setSizeIndex(size);
        tumor.setNoAi(true);
        tumor.setNoGravity(true);
        return tumor;
    }

    private static Player player(GameTestHelper h, NeonTumorEntity tumor) {
        Player player = h.makeMockSurvivalPlayer();
        player.setUUID(UUID.randomUUID());
        player.moveTo(tumor.getX(), tumor.getY(), tumor.getZ(), 0, 0);
        player.setOnGround(true);
        return player;
    }

    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.001, message + ": " + actual + " != " + expected);
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonSizeAttributesAndNbt(GameTestHelper h) {
        float[] hp = {2, 6, 18}, width = {0.5F, 1, 2.5F}, height = {0.55F, 1.1F, 2.6F};
        double[] damage = {1, 2, 4}, speed = {0.15, 0.18, 0.2};
        int[] xp = {1, 2, 5};
        for (int size = 0; size < 3; size++) {
            NeonTumorEntity tumor = tumor(h, size);
            tumor.awaken();
            close(h, tumor.getMaxHealth(), hp[size], "health");
            close(h, tumor.getBbWidth(), width[size], "width");
            close(h, tumor.getBbHeight(), height[size], "height");
            close(h, tumor.getAttributeValue(Attributes.ATTACK_DAMAGE), damage[size], "contact attack");
            close(h, tumor.getAttributeValue(Attributes.MOVEMENT_SPEED), speed[size], "active speed");
            close(h, tumor.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), size == 2 ? 0.5 : 0, "knockback");
            h.assertTrue(tumor.getExperienceReward() == xp[size], "experience");
            tumor.setVariant(size);
            tumor.setHealth(hp[size] - 1);
            CompoundTag nbt = new CompoundTag();
            tumor.addAdditionalSaveData(nbt);
            NeonTumorEntity restored = ModEntities.NEON_TUMOR.get().create(h.getLevel());
            restored.readAdditionalSaveData(nbt);
            close(h, restored.getHealth(), hp[size] - 1, "reload must not heal");
            h.assertTrue(restored.getVariant() == size && restored.getSizeIndex() == size, "size and variant survive reload");
            tumor.discard();
        }
        NeonTumorEntity explicit = ModEntities.NEON_TUMOR.get().create(h.getLevel());
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Size", 99);
        nbt.putInt("Variant", -5);
        explicit.readAdditionalSaveData(nbt);
        h.assertTrue(explicit.getSizeIndex() == 2 && explicit.getVariant() == 0, "invalid NBT clamped");
        close(h, explicit.getHealth(), 18, "explicit summon without Health starts at full size health");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonSpawnDistributionAndPersistence(GameTestHelper h) {
        int[] sizes = new int[3], variants = new int[3];
        for (int i = 0; i < 600; i++) {
            NeonTumorEntity tumor = ModEntities.NEON_TUMOR.get().create(h.getLevel());
            tumor.finalizeSpawn(h.getLevel(), h.getLevel().getCurrentDifficultyAt(h.absolutePos(BlockPos.ZERO)),
                    MobSpawnType.SPAWN_EGG, null, null);
            sizes[tumor.getSizeIndex()]++;
            variants[tumor.getVariant()]++;
            h.assertTrue(tumor.getSpawnTicks() == 20, "spawn animation starts once");
        }
        h.assertTrue(sizes[0] > 150 && sizes[0] < 330 && sizes[1] > 150 && sizes[1] < 330
                && sizes[2] > 60 && sizes[2] < 180, "40/40/20 size distribution");
        for (int count : variants) h.assertTrue(count > 120 && count < 280, "equal color distribution");
        NeonTumorEntity restored = ModEntities.NEON_TUMOR.get().create(h.getLevel());
        restored.readAdditionalSaveData(new CompoundTag());
        h.assertTrue(restored.getSizeIndex() == 1 && restored.getVariant() == 0 && restored.getSpawnTicks() == 0,
                "old saves/default explicit NBT do not replay spawning");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonLightBoundaries(GameTestHelper h) {
        h.assertTrue(LightTier.fromBrightness(6) == LightTier.DARK, "6 is dark");
        h.assertTrue(LightTier.fromBrightness(7) == LightTier.DIM, "7 is dim");
        h.assertTrue(LightTier.fromBrightness(10) == LightTier.DIM, "10 is dim");
        h.assertTrue(LightTier.fromBrightness(11) == LightTier.BRIGHT, "11 is bright");
        h.succeed();
    }

    private static void chamber(GameTestHelper h) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 4, 4, 4)) {
            if (pos.getX() == 0 || pos.getX() == 4 || pos.getY() == 0 || pos.getY() == 4
                    || pos.getZ() == 0 || pos.getZ() == 4) h.setBlock(pos, Blocks.STONE);
        }
    }

    @GameTest(template = "empty", batch = "neon", timeoutTicks = 280)
    public static void neonRealLightAndWakeExpiry(GameTestHelper h) {
        chamber(h);
        NeonTumorEntity tumor = tumor(h, 1);
        Player far = player(h, tumor);
        far.setPos(tumor.getX() + 4, tumor.getY(), tumor.getZ());
        h.runAtTickTime(15, () -> {
            h.assertTrue(tumor.isDormant(), "sealed darkness sleeps");
            close(h, tumor.getAttributeValue(Attributes.MOVEMENT_SPEED), 0, "dormant speed");
            h.setBlock(new BlockPos(2, 2, 2), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7));
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(tumor.getLightTier() == LightTier.DIM, "real block light dim");
            close(h, tumor.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.09, "half speed");
            h.assertTrue(!tumor.canPursue(far), "dim ignores target beyond three blocks");
            h.setBlock(new BlockPos(2, 2, 2), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11));
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(tumor.getLightTier() == LightTier.BRIGHT && tumor.canPursue(far), "bright extends pursuit");
            h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(tumor.isDormant(), "darkness returns");
            tumor.awaken();
            h.assertTrue(tumor.getAwakeTicks() == 200 && !tumor.isDormant(), "wake lasts ten seconds");
        });
        h.runAtTickTime(265, () -> {
            h.assertTrue(tumor.isDormant() && tumor.getAwakeTicks() == 0, "ten second wake expires");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "neon", timeoutTicks = 45)
    public static void neonFirstContactOnlyWakes(GameTestHelper h) {
        NeonTumorEntity tumor = tumor(h, 0);
        Player player = player(h, tumor);
        tumor.contact(player);
        close(h, player.getHealth(), 20, "sleeping first touch is harmless");
        h.assertTrue(tumor.getAwakeTicks() == 200 && !player.hasEffect(ModEffects.CORRODED.get()), "first touch wakes");
        h.runAfterDelay(21, () -> {
            tumor.contact(player);
            h.assertTrue(player.getHealth() < 20, "subsequent contact hurts");
            h.assertTrue(player.getEffect(ModEffects.CORRODED.get()).getDuration() == 40, "small corrosion duration");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonCorrosionArmorAndUnarmored(GameTestHelper h) {
        Player player = h.makeMockSurvivalPlayer();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        var items = new net.minecraft.world.item.Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], new ItemStack(items[i]));
        player.tickCount = 20;
        ModEffects.CORRODED.get().applyEffectTick(player, 0);
        for (EquipmentSlot slot : slots) h.assertTrue(player.getItemBySlot(slot).getDamageValue() == 3, "each armor piece loses three durability");
        close(h, player.getHealth(), 20, "armor prevents unarmored corrosion damage");
        for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
        player.tickCount = 40;
        ModEffects.CORRODED.get().applyEffectTick(player, 0);
        close(h, player.getHealth(), 19.5, "unarmored half-point damage");
        player.tickCount = 41;
        ModEffects.CORRODED.get().applyEffectTick(player, 0);
        close(h, player.getHealth(), 19.5, "no extra damage between seconds");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonBladeProjectileAndImmunities(GameTestHelper h) {
        NeonTumorEntity tumor = tumor(h, 2);
        Player player = player(h, tumor);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        tumor.hurt(h.getLevel().damageSources().playerAttack(player), 2);
        close(h, tumor.getHealth(), 14, "sword doubles direct damage");
        tumor.invulnerableTime = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        tumor.hurt(h.getLevel().damageSources().playerAttack(player), 2);
        close(h, tumor.getHealth(), 10, "axe doubles direct damage");
        tumor.invulnerableTime = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
        tumor.hurt(h.getLevel().damageSources().playerAttack(player), 2);
        close(h, tumor.getHealth(), 8, "melee trident stays normal");
        tumor.invulnerableTime = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Arrow arrow = EntityType.ARROW.create(h.getLevel());
        tumor.hurt(h.getLevel().damageSources().arrow(arrow, player), 2);
        close(h, tumor.getHealth(), 6, "projectile is not doubled when holding sword");
        tumor.invulnerableTime = 0;
        h.assertTrue(!tumor.hurt(h.getLevel().damageSources().fall(), 30), "fall immunity");
        h.assertTrue(!tumor.hurt(CorrosionDamage.source(h.getLevel()), 30), "corrosion damage immunity");
        h.assertTrue(!tumor.addEffect(new MobEffectInstance(ModEffects.CORRODED.get(), 40)), "same species cloud immunity");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "neon", timeoutTicks = 120)
    public static void neonEngulfTimeoutAndCooldown(GameTestHelper h) {
        NeonTumorEntity tumor = tumor(h, 2);
        tumor.awaken();
        Player player = player(h, tumor);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        h.assertTrue(tumor.beginEngulf(player), "nauseous player can be swallowed");
        player.stopRiding();
        h.assertTrue(player.getVehicle() == tumor, "manual dismount is cancelled");
        h.assertTrue(tumor.canRiderInteract(), "passenger can target its captor");
        h.runAfterDelay(81, () -> {
            h.assertTrue(!player.isPassenger() && !tumor.isEngulfing(), "ejected after four seconds");
            player.setPos(tumor.getX(), tumor.getY(), tumor.getZ());
            h.assertTrue(!tumor.canEngulf(player), "same-player cooldown starts on release");
            CompoundTag saved = new CompoundTag();
            tumor.addAdditionalSaveData(saved);
            NeonTumorEntity restored = ModEntities.NEON_TUMOR.get().create(h.getLevel());
            restored.readAdditionalSaveData(saved);
            restored.moveTo(tumor.position());
            h.assertTrue(!restored.canEngulf(player), "reloading cannot bypass swallow cooldown");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonEngulfEscapeDeathLogoutAndDimension(GameTestHelper h) {
        NeonTumorEntity tumor = tumor(h, 2);
        tumor.awaken();
        Player victim = player(h, tumor);
        victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        h.assertTrue(tumor.beginEngulf(victim), "capture for logout");
        NeonTumorEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(victim));
        h.assertTrue(!victim.isPassenger() && !tumor.isRemoved(), "logout leaves captor in the world");
        Player traveler = player(h, tumor);
        traveler.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        h.assertTrue(tumor.beginEngulf(traveler), "capture for dimension change");
        NeonTumorEvents.onDimensionChange(new EntityTravelToDimensionEvent(traveler, Level.NETHER));
        h.assertTrue(!traveler.isPassenger(), "dimension transfer releases");
        Player fighter = player(h, tumor);
        fighter.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        h.assertTrue(tumor.beginEngulf(fighter), "capture for escape");
        fighter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        tumor.hurt(h.getLevel().damageSources().playerAttack(fighter), 10);
        h.assertTrue(!tumor.isAlive() && !fighter.isPassenger(), "inside attack kills and immediately releases");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonEngulfEligibility(GameTestHelper h) {
        NeonTumorEntity tumor = tumor(h, 2);
        tumor.awaken();
        Player player = player(h, tumor);
        h.assertTrue(!tumor.canEngulf(player), "nausea is required");
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        h.assertTrue(tumor.canEngulf(player), "nearby nauseous survival player qualifies");
        player.setPos(tumor.getX() + 2.1, tumor.getY(), tumor.getZ());
        h.assertTrue(!tumor.canEngulf(player), "two block range");
        player.setPos(tumor.getX(), tumor.getY(), tumor.getZ());
        Player creative = new Player(h.getLevel(), tumor.blockPosition(), 0,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "neon-creative")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        creative.moveTo(tumor.position());
        creative.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        h.assertTrue(!tumor.canEngulf(creative), "creative immune");
        h.assertTrue(!tumor.canEngulf(EntityType.IRON_GOLEM.create(h.getLevel())), "golems never swallowed");
        tumor.setSizeIndex(1);
        h.assertTrue(!tumor.canEngulf(player), "medium cannot swallow");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "neon")
    public static void neonSlamTimingAndLeapCooldown(GameTestHelper h) {
        NeonTumorEntity tumor = tumor(h, 2);
        tumor.awaken();
        Player victim = player(h, tumor);
        victim.setPos(tumor.getX() + 3, tumor.getY(), tumor.getZ());
        tumor.setTarget(victim);
        tumor.setOnGround(true);
        TendrilSlamGoal slam = new TendrilSlamGoal(tumor);
        h.assertTrue(slam.canUse(), "slam in range");
        slam.start();
        for (int tick = 0; tick < 9; tick++) slam.tick();
        close(h, victim.getHealth(), 20, "no damage before tenth tick");
        slam.tick();
        h.assertTrue(victim.getHealth() < 20, "tenth tick hits");
        h.assertTrue(tumor.isSlamming() && !tumor.canSlam(), "slam visible and cooling down");
        slam.stop();
        h.assertTrue(!tumor.isSlamming(), "tendrils hidden after attack");
        TumorLeapGoal leap = new TumorLeapGoal(tumor);
        h.assertTrue(leap.canUse(), "medium or large grounded leap at three blocks");
        leap.start();
        for (int tick = 0; tick < 4; tick++) leap.tick();
        h.assertTrue(tumor.getDeltaMovement().y > 0 && !tumor.canLeap(), "leap launches and starts cooldown");
        leap.stop();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "neon", timeoutTicks = 30)
    public static void neonDeathCloudDurationAndImmediateApplication(GameTestHelper h) {
        NeonTumorEntity tumor = tumor(h, 1);
        tumor.setVariant(1);
        LivingEntity victim = h.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        victim.setNoGravity(true);
        ((net.minecraft.world.entity.Mob) victim).setNoAi(true);
        tumor.hurt(h.getLevel().damageSources().genericKill(), 1000);
        var clouds = h.getLevel().getEntitiesOfClass(AreaEffectCloud.class, tumor.getBoundingBox().inflate(0.5));
        h.assertTrue(clouds.size() == 1, "one puddle and no splitting");
        AreaEffectCloud cloud = clouds.get(0);
        close(h, cloud.getRadius(), 1, "medium radius");
        h.assertTrue(cloud.getDuration() == 60 && cloud.getWaitTime() == 0, "three seconds with no startup delay");
        h.assertTrue(cloud.getColor() == 0x4FC8F0, "puddle variant color");
        h.runAfterDelay(6, () -> {
            MobEffectInstance effect = victim.getEffect(ModEffects.CORRODED.get());
            h.assertTrue(effect != null && effect.getDuration() > 30 && effect.getDuration() <= 40,
                    "custom cloud effect applies for two seconds, not eight or half a second");
            h.succeed();
        });
    }

    private static void checked(Runnable assertion, Runnable cleanup) {
        try { assertion.run(); }
        catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
    }

    @GameTest(template = "empty", batch = "neon", timeoutTicks = 300)
    public static void neonSteamContinuousExposureAndLightGate(GameTestHelper h) {
        chamber(h);
        NeonTumorEntity tumor = tumor(h, 2);
        tumor.setPos(tumor.getX(), h.absolutePos(new BlockPos(2, 1, 2)).getY(), tumor.getZ());
        h.setBlock(new BlockPos(2, 1, 2), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        Player player = player(h, tumor);
        player.setNoGravity(true);
        player.moveTo(tumor.getX() + 4, tumor.getY(), tumor.getZ(), 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(player), "exposure player is tracked by the world");
        Runnable cleanup = player::discard;
        h.runAtTickTime(50, () -> checked(() -> {
            h.assertTrue(!player.hasEffect(MobEffects.CONFUSION), "less than three seconds does not cause nausea");
            player.setPos(tumor.getX() + 6, tumor.getY(), tumor.getZ());
        }, cleanup));
        h.runAtTickTime(52, () -> player.setPos(tumor.getX() + 4, tumor.getY(), tumor.getZ()));
        h.runAtTickTime(112, () -> checked(() -> {
            h.assertTrue(!player.hasEffect(MobEffects.CONFUSION), "59 completed exposure ticks must not trigger nausea");
        }, cleanup));
        h.runAtTickTime(113, () -> checked(() -> {
            h.assertTrue(player.hasEffect(MobEffects.CONFUSION)
                    && player.getEffect(MobEffects.CONFUSION).getDuration() >= 150, "60 completed exposure ticks trigger eight second nausea");
            h.setBlock(new BlockPos(2, 1, 2), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7));
        }, cleanup));
        h.runAtTickTime(140, () -> player.removeEffect(MobEffects.CONFUSION));
        h.runAtTickTime(205, () -> checked(() -> {
            h.assertTrue(!player.hasEffect(MobEffects.CONFUSION), "dim light stops steam");
            tumor.setSizeIndex(1);
            h.setBlock(new BlockPos(2, 1, 2), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }, cleanup));
        h.runAtTickTime(280, () -> {
            try {
                h.assertTrue(!player.hasEffect(MobEffects.CONFUSION), "medium size never produces steam");
                h.succeed();
            } finally { cleanup.run(); }
        });
    }

    @GameTest(template = "empty", batch = "neon-navigation", timeoutTicks = 280)
    public static void neonActuallyWalksTowardReachableLight(GameTestHelper h) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 12, 4, 4)) {
            boolean wall = pos.getX() == 0 || pos.getX() == 12 || pos.getY() == 0 || pos.getY() == 4
                    || pos.getZ() == 0 || pos.getZ() == 4;
            h.setBlock(pos, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        h.setBlock(new BlockPos(9, 1, 2), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        NeonTumorEntity tumor = tumor(h, 1);
        var start = h.absolutePos(new BlockPos(2, 1, 2));
        var destination = net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(9, 1, 2)));
        tumor.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        tumor.setNoGravity(false);
        h.runAtTickTime(20, () -> tumor.setNoAi(false));
        for (int sample = 40; sample <= 240; sample += 40) {
            int tick = sample;
            h.runAtTickTime(sample, () -> {
                var path = tumor.getNavigation().getPath();
                System.out.println("NEON_NAVIGATION tick=" + tick + ", position=" + tumor.position()
                        + ", light=" + tumor.getLightTier() + ", speed=" + tumor.getSpeed()
                        + ", alive=" + tumor.isAlive() + ", removed=" + tumor.isRemoved()
                        + ", wanted=" + tumor.getMoveControl().hasWanted()
                        + ", path=" + (path == null ? "null" : path.getNextNodeIndex() + "/" + path.getNodeCount()
                        + " reachable=" + path.canReach() + " nodes=" + java.util.stream.IntStream.range(0, path.getNodeCount())
                        .mapToObj(i -> path.getNode(i).asBlockPos().toString()).toList()));
            });
        }
        h.succeedWhen(() -> {
            h.assertTrue(tumor.getTarget() == null, "light seeking has no combat target");
            h.assertTrue(tumor.position().distanceToSqr(destination) < 9 && tumor.getLightTier() == LightTier.BRIGHT,
                    "real navigation must reach brighter blocks; position=" + tumor.position() + ", goal=" + destination
                            + ", light=" + tumor.getLightTier() + ", dormant=" + tumor.isDormant());
        });
    }
}
