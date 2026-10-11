package com.zeropointsix.eraser.yuji.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

public final class YujiParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    public YujiParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites; lifetime = 20; quadSize = .25F; hasPhysics = false;
        xd = dx; yd = dy; zd = dz; setSpriteFromAge(sprites);
    }
    @Override public void tick() { super.tick(); setSpriteFromAge(sprites); alpha = 1 - age / (float) lifetime; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
