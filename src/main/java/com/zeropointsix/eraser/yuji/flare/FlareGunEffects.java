package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Successful-shot effects only; never changes the projectile or places light blocks. */
final class FlareGunEffects {
    static void fire(ServerLevel level, Player player) {
        Vec3 forward = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 muzzle = player.getEyePosition().add(forward.scale(.7))
                .add(right.scale(side * .22)).add(0, -.18, 0);
        level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 4, .025, .025, .025, .015);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, .025, .025, .025, .012);
        // Broadcast once from the server so the shooter and other players hear the same shot.
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, .7F, 1.25F + level.random.nextFloat() * .12F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, .45F, 1.1F);
    }

    private FlareGunEffects() { }
}
