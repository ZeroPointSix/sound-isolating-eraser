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
    @GameTest(template = "empty", batch = "gravity_expiry", timeoutTicks = 340)
    public static void fifteenPulsesAndOneNaturalSlamRespectHeavyTag(GameTestHelper h) {
        var level = h.getLevel();
        Vec3 center = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 8, 8)));
        BlockPos cell = BlockPos.containing(center);
        for (int x = (cell.getX() >> 4) - 2; x <= (cell.getX() >> 4) + 2; x++) {
            for (int z = (cell.getZ() >> 4) - 2; z <= (cell.getZ() >> 4) + 2; z++) level.getChunk(x, z);
        }
        var zombie = EntityType.ZOMBIE.create(level);
        var heavy = EntityType.WARDEN.create(level);
        List<LivingEntity> victims = List.of(zombie, heavy);
        for (int i = 0; i < victims.size(); i++) {
            var entity = victims.get(i);
            entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
            entity.setHealth(300);
            entity.setNoGravity(true);
            entity.noPhysics = true;
            ((Mob) entity).setNoAi(true);
            entity.setPos(center.add(i * 3, 0, 0));
            level.addFreshEntity(entity);
        }
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
        GravityFieldEntity field = GravityFieldEntity.create(level, java.util.UUID.randomUUID(), center);
        level.addFreshEntity(field);
        for (int pulse = 1; pulse <= 14; pulse++) {
            final int count = pulse;
            h.runAtTickTime(pulse * 20 + 1, () -> {
                for (var entity : victims) {
                    h.assertTrue(Math.abs(entity.getHealth() - (300 - count * 3)) < .01, "full periodic curve damage");
                    h.assertTrue(entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 4, "slowness V");
                }
                h.assertTrue(Math.abs(heavy.getY() - center.y) < .001, "heavy never lifted");
            });
        }
        h.runAtTickTime(290, () -> {
            GravityFieldEntity overlap = GravityFieldEntity.create(level, java.util.UUID.randomUUID(), center);
            level.addFreshEntity(overlap);
        });
        h.runAtTickTime(301, () -> {
            h.assertTrue(field.isRemoved(), "natural expiry at 300 ticks");
            for (var entity : victims) {
                h.assertTrue(Math.abs(entity.getHealth() - 246) < .01, "15 pulses plus exactly one curve x3 slam");
            }
            h.assertTrue(zombie.getDeltaMovement().y < -2, "slam wins over an active overlapping field");
            h.assertTrue(Math.abs(heavy.getY() - center.y) < .001
                    && Math.abs(heavy.getDeltaMovement().y) < .001, "heavy receives slam damage without motion");
            level.getEntitiesOfClass(GravityFieldEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(32))
                    .forEach(GravityFieldEntity::discard);
        });
        h.runAtTickTime(313, () -> {
            for (var entity : victims) {
                h.assertTrue(Math.abs(entity.getHealth() - 246) < .01, "no duplicate slam after removal");
                entity.discard();
            }
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
