package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityDamage;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityRoundTwoTests {
    @GameTest(template = "empty", timeoutTicks = 340)
    public static void fifteenPulsesKeepZombieCowAndPlayerInField(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(8, 4, 8));
        var cow = EntityType.COW.create(level);
        var zombie = EntityType.ZOMBIE.create(level);
        var player = h.makeMockSurvivalPlayer();
        player.getFoodData().setFoodLevel(10);
        List<LivingEntity> victims = List.of(cow, zombie, player);
        List<Vec3> positions = List.of(Vec3.atCenterOf(center).add(-1.2,0,0),
                Vec3.atCenterOf(center).add(1.2,0,0), Vec3.atCenterOf(center).add(0,0,1.2));
        for (int i = 0; i < victims.size(); i++) {
            var entity = victims.get(i);
            entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            entity.getAttribute(Attributes.ARMOR).setBaseValue(0);
            entity.setHealth(100);
            entity.setNoGravity(true);
            if (entity instanceof Mob mob) mob.setNoAi(true);
            entity.setPos(positions.get(i));
            level.addFreshEntity(entity);
            level.setBlockAndUpdate(entity.blockPosition().above(3), Blocks.STONE.defaultBlockState());
        }
        var owner = h.spawn(EntityType.ARMOR_STAND, new BlockPos(14, 4, 8));
        owner.setNoGravity(true);
        GravityFieldEntity field = GravityFieldEntity.create(level, owner.getUUID(), center);
        level.addFreshEntity(field);
        for (int pulse = 1; pulse <= 15; pulse++) {
            final int count = pulse;
            h.runAtTickTime(pulse * 20 + 1, () -> {
                for (int i = 0; i < victims.size(); i++) {
                    var entity = victims.get(i);
                    h.assertTrue(Math.abs(entity.getHealth() - (100 - count)) < 0.01,
                            "pulse " + count + " deducts health for " + entity.getType());
                    h.assertTrue(entity.position().distanceToSqr(positions.get(i)) < 0.001,
                            "gravity damage does not move " + entity.getType() + " at pulse " + count);
                    h.assertTrue(field.fieldBounds().contains(entity.position()), "victim remains within field");
                    h.assertTrue(entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2,
                            "Slowness III refreshed throughout all 15 pulses");
                }
            });
        }
        h.runAtTickTime(310, () -> {
            h.assertTrue(field.isRemoved(), "field expires after 300 ticks");
            for (var entity : victims) {
                h.assertTrue(!entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowness clears within 10 ticks of expiry");
                entity.discard();
            }
            owner.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void damagePreservesExistingVelocityAndNormalKnockback(GameTestHelper h) {
        var level = h.getLevel();
        // Forge FakePlayer intentionally ignores every damage source.
        var player = h.makeMockSurvivalPlayer();
        var owner = EntityType.ZOMBIE.create(level);
        owner.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 4, 2))));
        player.setPos(owner.position().add(3,0,0));
        Vec3 velocity = new Vec3(0.13, -0.07, 0.05);
        player.setDeltaMovement(velocity);
        player.hurtMarked = false;
        player.hasImpulse = false;
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(GravityFieldEntity.DAMAGE_TYPE), owner, owner);
        float before = player.getHealth();
        h.assertTrue(GravityDamage.hurt(player, source, 1), "real hurt is not cancelled");
        h.assertTrue(player.getHealth() == before - 1, "player loses health");
        h.assertTrue(player.getDeltaMovement().equals(velocity), "pre-existing motion is preserved exactly");
        h.assertTrue(!player.hurtMarked && !player.hasImpulse, "pulse does not request a velocity sync");
        player.knockback(0.4, 1, 0);
        h.assertTrue(!player.getDeltaMovement().equals(velocity), "unrelated knockback is not globally suppressed");
        h.succeed();
    }
}
