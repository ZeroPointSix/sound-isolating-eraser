package com.zeropointsix.eraser.suxia;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SuxiaMotion {
    public static final EntityDimensions CRAWLING = EntityDimensions.scalable(0.9F, 0.95F);
    public static final EntityDimensions DEAD = EntityDimensions.scalable(1.2F, 0.6F);
    private static final UUID SPEED_ID = UUID.fromString("b9cde461-2143-4b4f-bb95-df1a2ee1c226");
    private static final AttributeModifier CRAWL_SPEED = new AttributeModifier(
            SPEED_ID, "Suxia crawling", 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private float crawlO, crawl, deadO, dead;

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.ATTACK_DAMAGE, 15).add(Attributes.FOLLOW_RANGE, 32);
    }

    public static long clock(Level level) {
        return level.getServer() == null ? level.getGameTime()
                : level.getServer().overworld().getGameTime();
    }

    public static boolean canStand(Mob mob) {
        return mob.level().noCollision(mob, mob.getType().getDimensions().makeBoundingBox(mob.position()));
    }

    public static void speed(Mob mob, boolean crawling) {
        var attribute = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        if (crawling && !attribute.hasModifier(CRAWL_SPEED)) attribute.addTransientModifier(CRAWL_SPEED);
        if (!crawling) attribute.removeModifier(SPEED_ID);
    }

    public void tick(boolean crawling, boolean playingDead) {
        crawlO = crawl;
        deadO = dead;
        crawl = Mth.approach(crawl, crawling ? 1F : 0F, 0.17F);
        dead = Mth.approach(dead, playingDead ? 1F : 0F, playingDead ? 1F / 30F : 1F / 8F);
    }

    public float crawl(float pt) { return Mth.lerp(pt, crawlO, crawl); }
    public float dead(float pt) { return Mth.lerp(pt, deadO, dead); }

    public static float damage(Mob mob) {
        float base = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return (int) base > 0 ? base / 2F + mob.getRandom().nextInt((int) base) : base;
    }

    public static void smoke(Mob mob) {
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.4, mob.getZ(),
                    12, 0.3, 0.3, 0.3, 0.01);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.2, mob.getZ(),
                    8, 0.3, 0.2, 0.3, 0.02);
        }
    }

    /** Check loaded, supported, dry positions without loading new chunks. */
    public static boolean place(Mob mob, Vec3 at) {
        var level = mob.level();
        var pos = BlockPos.containing(at);
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())
                || !level.getFluidState(pos).isEmpty()) return false;
        var standing = mob.getType().getDimensions().makeBoundingBox(at);
        if (level.noCollision(mob, standing) && !level.containsAnyLiquid(standing)) {
            mob.setPos(at);
            setCrawling(mob, false);
            return true;
        }
        var crawling = CRAWLING.makeBoundingBox(at);
        if (level.noCollision(mob, crawling) && !level.containsAnyLiquid(crawling)) {
            mob.setPos(at);
            setCrawling(mob, true);
            return true;
        }
        return false;
    }

    private static void setCrawling(Mob mob, boolean value) {
        if (mob instanceof SuxiaSummonEntity summon) summon.setCrawling(value);
        else if (mob instanceof SuxiaEntity hostile) hostile.setCrawling(value);
    }

    public SuxiaMotion() { }
}
