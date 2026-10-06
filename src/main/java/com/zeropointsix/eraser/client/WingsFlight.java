package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.wings.WingsState;
import com.zeropointsix.eraser.registry.ModParticles;
import com.zeropointsix.eraser.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.client.player.Input;
import net.minecraft.world.phys.Vec3;

/** Client-authoritative wing flight physics, mirroring the server state. */
public final class WingsFlight {
    private WingsFlight() {}

    public static void tick(LocalPlayer p, WingsClientData.WingInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (!info.deployed()) {
            p.setNoGravity(false);
            return;
        }
        p.setNoGravity(true);

        // auto-retract when landed (server does the same; this is immediate UX)
        if (p.onGround() || p.isInWater()) {
            com.zeropointsix.eraser.wings.net.WingsNet.CHANNEL.sendToServer(
                    new com.zeropointsix.eraser.wings.net.SetDeployedPacket(false));
            p.setNoGravity(false);
            return;
        }

        int tier = info.tier();
        // 配置值是 m/s；deltaMovement 单位是 blocks/tick → ÷20 换算
        double maxSpeed = WingsConfig.SERVER.tierSpeed(tier) / 20.0;
        Input input = p.input;
        Vec3 delta = p.getDeltaMovement();
        Vec3 wish;
        if (tier == WingsState.HOVER) {
            wish = hoverWish(p, input, info);
        } else {
            wish = flightWish(p, input, maxSpeed);
        }

        double lerp = WingsConfig.SERVER.accelLerp.get() / 20.0;
        Vec3 next = delta.lerp(wish, Mth.clamp(lerp, 0, 1));
        // inertia: when no input, glide then decay
        boolean hasInput = input.up || input.down || input.left || input.right
                || input.jumping || input.shiftKeyDown;
        if (!hasInput && tier != WingsState.HOVER) {
            next = next.scale(0.985);
        }

        // safety: cap speed near unloaded chunks (Notion §4.6)；配置是 m/s → bpt
        double cap = WingsConfig.SERVER.unloadedChunkSpeedCap.get() / 20.0;
        Vec3 ahead = p.position().add(next.scale(4));
        if (!p.level().hasChunkAt(BlockPos.containing(ahead)) && next.length() > cap) {
            next = next.normalize().scale(cap);
        }
        // void guard: below minY+5 force up (Notion §4.6)
        int minY = p.level().getMinBuildHeight();
        if (p.getY() < minY + WingsConfig.SERVER.voidPushMargin.get()) {
            next = new Vec3(next.x * 0.3, 2.0, next.z * 0.3);
        }

        p.setDeltaMovement(next);
        p.resetFallDistance();

        // 高速撞墙的视觉爆发仍在客户端表现；伤害判定在服务端（客户端 hurt 无效）
        double speed = next.horizontalDistance();
        if (WingsConfig.SERVER.wallDamageEnabled.get()
                && p.horizontalCollision
                && speed * 20.0 > WingsConfig.SERVER.wallDamageThresholdSpeed.get()
                && p.tickCount % 5 == 0) {
            speedBurst(p, true);
        }

        spawnFlightParticles(p, tier, next);
        ambientSound(p, tier);
    }

    private static Vec3 hoverWish(LocalPlayer p, Input input, WingsClientData.WingInfo info) {
        double maxH = WingsConfig.SERVER.hoverMaxSpeed.get() / 20.0; // m/s → bpt
        Vec3 flat = inputToWorld(p, input, 0).scale(maxH * 0.5);
        double vy;
        if (input.jumping) vy = 0.3;      // 6 m/s 上升
        else if (input.shiftKeyDown) vy = -0.25; // 5 m/s 下降
        else {
            // 锚点轻微上下浮动：既有悬停呼吸感，周期性下坠也让原版 floating 踢人计数被重置
            double anchor = info.hoverY() + 0.35 * Mth.sin(p.level().getGameTime() * 0.18f);
            vy = Mth.clamp((anchor - p.getY()) * 0.4, -0.15, 0.15);
        }
        return new Vec3(flat.x, vy, flat.z).normalize().scale(Math.min(
                new Vec3(flat.x, vy, flat.z).length(), maxH));
    }

    private static Vec3 flightWish(LocalPlayer p, Input input, double maxSpeed) {
        if (input.up) {
            // forward = look direction (3D), allows climb/dive
            return p.getLookAngle().normalize().scale(maxSpeed);
        }
        Vec3 flat = inputToWorld(p, input, p.getYRot());
        if (flat.lengthSqr() < 1.0e-4) {
            // no input: keep current heading, slight decay handled by caller
            return p.getDeltaMovement();
        }
        // banked turn: steer current velocity toward input heading
        Vec3 cur = p.getDeltaMovement();
        Vec3 target = flat.normalize().scale(maxSpeed);
        return cur.normalize().lerp(target.normalize(), 0.08).normalize().scale(maxSpeed);
    }

    private static Vec3 inputToWorld(LocalPlayer p, Input input, float yawDeg) {
        double f = input.up ? 1 : input.down ? -1 : 0;
        double s = input.left ? 1 : input.right ? -1 : 0;
        float yaw = (float) Math.toRadians(-yawDeg);
        double x = f * Math.sin(yaw) + s * Math.cos(yaw);
        double z = f * Math.cos(yaw) - s * Math.sin(yaw);
        return new Vec3(x, 0, z);
    }

    private static void spawnFlightParticles(LocalPlayer p, int tier, Vec3 v) {
        if (p.level().getGameTime() % 2 != 0) return;
        double speed = v.length();
        if (speed < 1.0) return;
        Vec3 back = v.normalize().scale(-0.4);
        double px = p.getX() + back.x, py = p.getY() + p.getBbHeight() * 0.6, pz = p.getZ() + back.z;
        p.level().addParticle(ModParticles.WIND_RIBBON.get(), px, py, pz, 0, -0.05, 0);
        if (tier >= WingsState.BOOST && p.level().getGameTime() % 5 == 0) {
            p.level().addParticle(ModParticles.THUNDER_ARC.get(),
                    px + (p.getRandom().nextDouble() - 0.5) * 1.5,
                    py + (p.getRandom().nextDouble() - 0.5) * 1.5,
                    pz + (p.getRandom().nextDouble() - 0.5) * 1.5, 0, 0, 0);
        }
        if (tier == WingsState.STORM) {
            p.level().addParticle(ModParticles.TRAIL_DOT.get(), px, py, pz, 0, -0.02, 0);
        }
    }

    private static void ambientSound(LocalPlayer p, int tier) {
        if (p.tickCount % 20 == 0) {
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(),
                    ModSounds.WIND_LOOP.get(), p.getSoundSource(),
                    0.4f + tier * 0.15f, 1.0f + tier * 0.05f, false);
        }
    }

    /** 突破音爆/冲霄一瞬：冲击环 + 雷鸣，由档位提升触发。 */
    public static void sonicBoom(LocalPlayer p) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.level().addParticle(ModParticles.IMPACT_RING.get(),
                    p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(),
                    Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3);
        }
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(),
                ModSounds.THUNDER_BOOM.get(), p.getSoundSource(), 0.8f, 1.0f, false);
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(),
                SoundEvents.GENERIC_EXPLODE, p.getSoundSource(), 0.4f, 1.6f, false);
    }

    public static void blinkFx(LocalPlayer p) {
        if (WingsConfig.CLIENT.blinkFlash.get()) {
            WingsClientData.flash(4); // ~4 tick white flash
        }
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(),
                ModSounds.THUNDER_BLINK.get(), p.getSoundSource(), 0.7f, 1.2f, false);
    }

    private static void speedBurst(LocalPlayer p, boolean crash) {
        for (int i = 0; i < 6; i++) {
            p.level().addParticle(ModParticles.TRAIL_DOT.get(),
                    p.getX() + (p.getRandom().nextDouble() - 0.5),
                    p.getY() + p.getRandom().nextDouble(),
                    p.getZ() + (p.getRandom().nextDouble() - 0.5), 0, 0.05, 0);
        }
    }
}
