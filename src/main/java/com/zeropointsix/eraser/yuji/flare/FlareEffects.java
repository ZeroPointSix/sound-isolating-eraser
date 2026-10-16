package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class FlareEffects {
    public static Vector3f color(int color) {
        return color == 1 ? new Vector3f(1, .86F, .12F)
                : color == 2 ? new Vector3f(.15F, 1, .3F) : new Vector3f(1, .12F, .06F);
    }

    private static SimpleParticleType spark(int color) {
        return color == 1 ? FlareContent.YELLOW_SPARK.get()
                : color == 2 ? FlareContent.GREEN_SPARK.get() : FlareContent.RED_SPARK.get();
    }

    public static void trail(Level level, Vec3 from, Vec3 to, int color) {
        double distance = from.distanceTo(to);
        int samples = Math.max(1, Math.min(8, (int) Math.ceil(distance * 4)));
        if (distance > 8) { from = to; samples = 1; }
        for (int i = 0; i < samples; i++) {
            Vec3 p = from.lerp(to, (i + 1.) / samples);
            level.addParticle(spark(color), true, p.x, p.y, p.z,
                    (level.random.nextDouble() - .5) * .025, -.01, (level.random.nextDouble() - .5) * .025);
            if (i % 2 == 0) level.addParticle(ParticleTypes.FIREWORK, true, p.x, p.y, p.z, 0, -.015, 0);
        }
        level.addParticle(new DustParticleOptions(color(color), 2.2F), true, to.x, to.y, to.z, 0, .01, 0);
    }

    public static void burst(Level level, Vec3 center, int color) {
        for (int i = 0; i < 36; i++) {
            double y = 1 - 2 * (i + .5) / 36;
            double ring = Math.sqrt(1 - y * y), angle = i * 2.399963229728653;
            level.addParticle(spark(color), true, center.x, center.y, center.z,
                    Math.cos(angle) * ring * .3, y * .3, Math.sin(angle) * ring * .3);
        }
        for (int i = 0; i < 12; i++)
            level.addParticle(ParticleTypes.FIREWORK, true, center.x, center.y, center.z,
                    (level.random.nextDouble() - .5) * .25, level.random.nextDouble() * .2,
                    (level.random.nextDouble() - .5) * .25);
    }

    public static void decoy(Level level, Vec3 center) {
        trail(level, center.add(0, .9, 0), center.add(0, 1.2, 0), 0);
    }

    private FlareEffects() { }
}
