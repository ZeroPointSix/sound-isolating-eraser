package com.zeropointsix.eraser.yuji.flare.client;

import com.zeropointsix.eraser.yuji.flare.FlareEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

final class FlareSparkParticle extends TextureSheetParticle {
    private FlareSparkParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites, int color) {
        super(level, x, y, z, dx, dy, dz);
        xd = dx; yd = dy; zd = dz;
        lifetime = 18 + random.nextInt(6); quadSize = .18F + random.nextFloat() * .08F;
        gravity = .025F; friction = .94F; hasPhysics = false;
        var tint = FlareEffects.color(color); setColor(tint.x, tint.y, tint.z); pickSprite(sprites);
    }
    @Override public void tick() { super.tick(); alpha = Math.max(0, 1 - age / (float) lifetime); }
    @Override public int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    record Provider(SpriteSet sprites, int color) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                  double dx, double dy, double dz) {
            return new FlareSparkParticle(level, x, y, z, dx, dy, dz, sprites, color);
        }
    }
}
