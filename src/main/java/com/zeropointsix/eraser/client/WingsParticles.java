package com.zeropointsix.eraser.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/** 风带/电弧/冲击环/尾迹粒子（贴图见 assets/.../textures/particle/）。 */
public final class WingsParticles {
    private WingsParticles() {}

    private static abstract class Base extends TextureSheetParticle {
        Base(ClientLevel level, double x, double y, double z,
             double xd, double yd, double zd, SpriteSet sprites, int life) {
            super(level, x, y, z, xd, yd, zd);
            this.lifetime = life;
            this.setSpriteFromAge(sprites);
            this.xd = xd; this.yd = yd; this.zd = zd;
        }
        @Override public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
        @Override protected int getLightColor(float partialTick) {
            return 0xF000F0; // self-lit
        }
    }

    /** 青色风带：向后飘动、缓慢变长变细。 */
    public static class Ribbon extends Base {
        public Ribbon(ClientLevel l, double x, double y, double z,
                      double xd, double yd, double zd, SpriteSet s) {
            super(l, x, y, z, xd, yd, zd, s, 14);
            this.quadSize = 0.25f;
            this.setColor(0.5f, 0.95f, 1.0f);
        }
        @Override public void tick() {
            super.tick();
            this.quadSize *= 1.03f;
            this.alpha = 1.0f - age / (float) lifetime;
            this.xd *= 0.96; this.yd *= 0.96; this.zd *= 0.96;
        }
    }

    /** 银金电弧：快速闪动，寿命极短。 */
    public static class Arc extends Base {
        public Arc(ClientLevel l, double x, double y, double z,
                   double xd, double yd, double zd, SpriteSet s) {
            super(l, x, y, z, xd, yd, zd, s, 4);
            this.quadSize = 0.4f;
            this.setColor(1.0f, 0.9f, 0.5f);
        }
        @Override public void tick() {
            super.tick();
            this.alpha = Mth.clamp(1.2f - age * 0.35f, 0, 1);
        }
    }

    /** 青白冲击环：扩张淡出。 */
    public static class Ring extends Base {
        public Ring(ClientLevel l, double x, double y, double z,
                    double xd, double yd, double zd, SpriteSet s) {
            super(l, x, y, z, xd, yd, zd, s, 10);
            this.quadSize = 0.6f;
            this.setColor(0.7f, 1.0f, 1.0f);
        }
        @Override public void tick() {
            super.tick();
            this.quadSize += 0.35f;
            this.alpha = 1.0f - age / (float) lifetime;
        }
    }

    /** 星羽尾迹：停留 1.5s 后消隐（神霄御雷航迹）。 */
    public static class Dot extends Base {
        public Dot(ClientLevel l, double x, double y, double z,
                   double xd, double yd, double zd, SpriteSet s) {
            super(l, x, y, z, xd, yd, zd, s, 30);
            this.quadSize = 0.08f;
            this.setColor(0.6f, 0.95f, 1.0f);
        }
        @Override public void tick() {
            super.tick();
            this.xd = this.yd = this.zd = 0;
            if (age > 18) this.alpha = 1.0f - (age - 18) / 12f;
        }
    }
}
