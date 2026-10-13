package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityDamage;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.gravity.GravityTargets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityDragonTests {
    @GameTest(template = "empty")
    public static void dragonRetainsBodyReductionForCrushAndSameTickSlam(GameTestHelper h) {
        EnderDragon dragon = dragon(h);
        var owner = h.makeMockSurvivalPlayer();
        GravityFieldEntity field = GravityFieldEntity.create(h.getLevel(), owner.getUUID(), dragon.position());
        DamageSource source = new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(GravityFieldEntity.DAMAGE_TYPE), field, owner);
        h.assertTrue(dragon.getType().is(GravityTargets.HEAVY), "dragon remains a heavy target");
        h.assertTrue(GravityTargets.isHostile(dragon), "dragon is eligible for hostile damage");
        Vec3 motion = new Vec3(.1, .2, -.1);
        dragon.setDeltaMovement(motion);
        dragon.hurtMarked = true;
        dragon.hasImpulse = false;
        float pulse = GravityDamage.curve(dragon.getMaxHealth());
        float before = dragon.getHealth();
        h.assertTrue(GravityDamage.hurt(dragon, source, pulse), "player-owned crush is accepted");
        h.assertTrue(Math.abs(dragon.getHealth() - (before - bodyDamage(pulse))) < .001,
                "crush retains the dragon's native body reduction");
        h.assertTrue(dragon.invulnerableTime > 0, "crush establishes normal hit invulnerability");
        before = dragon.getHealth();
        float slam = pulse * 3;
        h.assertTrue(GravityDamage.hurt(dragon, source, slam), "same-tick slam is accepted");
        h.assertTrue(Math.abs(dragon.getHealth() - (before - bodyDamage(slam))) < .001,
                "slam bypasses cooldown but retains native body reduction");
        h.assertTrue(dragon.getDeltaMovement().equals(motion) && dragon.hurtMarked && !dragon.hasImpulse,
                "neither hit changes heavy-target motion or synchronization flags");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ownerlessCrushPreservesNativeDragonImmunity(GameTestHelper h) {
        EnderDragon dragon = dragon(h);
        GravityFieldEntity field = GravityFieldEntity.create(h.getLevel(), UUID.randomUUID(), dragon.position());
        DamageSource source = new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(GravityFieldEntity.DAMAGE_TYPE), field, null);
        float before = dragon.getHealth();
        GravityDamage.hurt(dragon, source, GravityDamage.curve(dragon.getMaxHealth()));
        h.assertTrue(dragon.getHealth() == before, "ownerless field does not bypass the dragon's native immunity");
        h.succeed();
    }

    private static EnderDragon dragon(GameTestHelper h) {
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(h.getLevel());
        dragon.setNoAi(true);
        dragon.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 6, 3))));
        return dragon;
    }

    private static float bodyDamage(float amount) {
        return amount / 4 + Math.min(amount, 1);
    }

    private GravityDragonTests() { }
}
