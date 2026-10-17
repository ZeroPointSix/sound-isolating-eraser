package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Successful-shot effects only; never changes the projectile or places light blocks. */
final class FlareGunEffects {
    static void fire(ServerLevel level, Player player) {
        Vec3 muzzle = FlareGunPose.muzzle(player);
        // Each viewer resolves its own held-item pose; an eye offset is not a third-person muzzle.
        FlareGunNetwork.fire(player);
        // Broadcast once from the server so the shooter and other players hear the same shot.
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, .7F, 1.25F + level.random.nextFloat() * .12F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, .45F, 1.1F);
    }

    private FlareGunEffects() { }
}
