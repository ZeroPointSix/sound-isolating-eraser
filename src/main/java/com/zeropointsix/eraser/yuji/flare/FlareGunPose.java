package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shared first-person gun placement. The muzzle flash lives in camera space so it stays
 * on the rendered barrel when looking up or down, instead of sliding along world Y.
 */
public final class FlareGunPose {
    public static final float HAND_SIDE = .36F;
    public static final float HAND_DOWN = .72F;
    public static final float HAND_FORWARD = .90F;
    public static final float HAND_YAW = -4;
    public static final float HAND_SCALE = .65F;
    public static final double MUZZLE_FORWARD = .70;
    public static final double MUZZLE_SIDE = .22;
    public static final double MUZZLE_DOWN = .18;

    public static Vec3 muzzle(Player player) {
        Vec3 look = player.getLookAngle();
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 right = right(look, player.getYRot());
        Vec3 down = look.cross(right);
        if (down.lengthSqr() > 1.0E-12) down = down.normalize();
        return player.getEyePosition()
                .add(look.scale(MUZZLE_FORWARD))
                .add(right.scale(side * MUZZLE_SIDE))
                .add(down.scale(MUZZLE_DOWN));
    }

    static Vec3 right(Vec3 look, float yRot) {
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) {
            double yaw = Math.toRadians(yRot);
            return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        return right.normalize();
    }

    private FlareGunPose() { }
}
