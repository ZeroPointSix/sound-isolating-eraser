package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.eraser.EraserNetwork;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Predict the server's pull during client catch-up ticks, without changing input or position. */
public final class WaterGhostGrabClient {
    private static LocalPlayer player;
    private static ClientLevel level;
    private static UUID holder;
    private static Vec3 anchor;
    private static int expiresAt;

    public static void update(EraserNetwork.GrabAnchor message) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null
                || !mc.level.dimension().location().equals(message.dimension())) return;
        if (message.remainingTicks() <= 0) {
            if (message.holder().equals(holder)) clear();
            return;
        }
        player = mc.player;
        level = mc.level;
        holder = message.holder();
        anchor = message.position();
        expiresAt = player.tickCount + message.remainingTicks();
    }

    public static boolean isHeld(LivingEntity entity) {
        var mc = Minecraft.getInstance();
        if (player == null) return false;
        if (player != mc.player || level != mc.level || !player.isAlive()
                || player.tickCount >= expiresAt) {
            clear();
            return false;
        }
        return entity == player;
    }

    public static void pull(LivingEntity entity) {
        if (isHeld(entity)) {
            // Vanilla jump and sprint have already changed velocity when travel starts.
            // Recompute from this tick's position rather than replaying a stale impulse.
            entity.setDeltaMovement(anchor.subtract(entity.position()).scale(0.5));
            entity.fallDistance = 0;
        }
    }

    private static void clear() {
        player = null;
        level = null;
        holder = null;
        anchor = null;
    }

    private WaterGhostGrabClient() { }
}
