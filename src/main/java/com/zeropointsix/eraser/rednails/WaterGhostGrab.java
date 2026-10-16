package com.zeropointsix.eraser.rednails;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class WaterGhostGrab extends Goal {
    private static final String HOLDER = "sound_isolating_eraser_grabbed_by";
    private static final String UNTIL = "sound_isolating_eraser_grabbed_until";
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));
    private final WaterGhostBody body;
    private final PathfinderMob mob;
    private LivingEntity target;
    private int ticks, duration;
    private float damage;
    private double startY;
    private long cooldown;
    private Vec3 shoreWater;
    private boolean enteredWater;

    public WaterGhostGrab(WaterGhostBody body) {
        this.body = body; mob = body.mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }
    public long cooldownUntil() { return cooldown; }
    public void cooldownUntil(long value) { cooldown = value; }
    public LivingEntity held() { return target; }
    public static boolean grabbable(LivingEntity target) {
        if (target.getType().is(BOSSES) || target.getBbWidth() > 1.5 || target.getBbHeight() > 2.5
                || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof Player p && p.isCreative()) return false;
        return target.getPersistentData().getLong(UNTIL) <= target.level().getGameTime();
    }
    @Override public boolean canUse() {
        var prey = mob.getTarget();
        return allowed(prey) && (!(mob instanceof WaterGhostEntity hostile) || hostile.mayPursue())
                && cooldown <= mob.level().getGameTime() && mob.distanceToSqr(prey) <= 2.2 * 2.2
                && mob.hasLineOfSight(prey) && grabbable(prey);
    }
    private boolean allowed(LivingEntity prey) {
        if (prey == null || prey.level() != mob.level() || !prey.isAlive() || prey.isSpectator()) return false;
        if (mob instanceof WaterGhostSummonEntity summon) return summon.wantsToAttack(prey, summon.getOwner());
        return WaterGhostEntity.prey(prey);
    }
    @Override public boolean canContinueToUse() {
        return allowed(target) && mob.isAlive()
                && mob.getTarget() == target && ticks < duration && damage < 8
                && mob.distanceToSqr(target) <= 16;
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        target = mob.getTarget();
        if (!allowed(target) || !grabbable(target)) { target = null; return; }
        ticks = 0; damage = 0; startY = mob.getY();
        duration = body.swimming() ? 100 : 60;
        enteredWater = body.swimming();
        shoreWater = findShoreWater();
        target.stopRiding();
        body.stopMoving();
        body.grabbing(true);
        lock();
        mob.playSound(SoundEvents.DROWNED_AMBIENT, 1.5F, 0.5F);
        mob.playSound(SoundEvents.GENERIC_SPLASH, 1, 0.7F);
    }
    private Vec3 findShoreWater() {
        if (!(mob instanceof WaterGhostSummonEntity summon) || body.swimming()) return null;
        var owner = summon.getOwner();
        if (owner == null) return null;
        boolean ownerNearWater = owner.isInWater();
        for (var pos : BlockPos.withinManhattan(owner.blockPosition(), 2, 1, 2)) {
            if (WaterGhostBody.waterAt(mob, pos)) { ownerNearWater = true; break; }
        }
        if (!ownerNearWater) return null;
        for (var pos : BlockPos.withinManhattan(mob.blockPosition(), 4, 1, 4)) {
            if (WaterGhostBody.waterAt(mob, pos) && WaterGhostBody.waterAt(mob, pos.above())) {
                Vec3 at = Vec3.atBottomCenterOf(pos);
                if (mob.level().noCollision(mob, WaterGhostBody.SWIMMING.makeBoundingBox(at))) return at;
            }
        }
        return null;
    }
    private void lock() {
        target.getPersistentData().putUUID(HOLDER, mob.getUUID());
        target.getPersistentData().putLong(UNTIL, mob.level().getGameTime() + 5);
    }
    @Override public void tick() {
        if (target == null) return;
        if (!canContinueToUse()) { stop(); return; }
        lock();
        if (!body.swimming() && shoreWater != null) {
            Vec3 toward = shoreWater.subtract(mob.position()).multiply(1, 0, 1);
            if (toward.lengthSqr() > 0.04) {
                Vec3 step = toward.normalize().scale(0.12);
                if (mob.level().noCollision(mob, mob.getBoundingBox().move(step))) {
                    mob.setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90);
                    mob.yBodyRot = mob.getYRot();
                    mob.setDeltaMovement(step.x, mob.getDeltaMovement().y, step.z);
                }
            }
        }
        Vec3 forward = Vec3.directionFromRotation(0, mob.getYRot());
        Vec3 hold = mob.position().add(forward.scale(1.1)).add(0, body.swimming() ? 0 : 0.6, 0);
        // A dismounted boat must not cancel the grab; only solid terrain blocks the hold.
        var holdBox = target.getDimensions(target.getPose()).makeBoundingBox(hold);
        if (!mob.level().getWorldBorder().isWithinBounds(holdBox)
                || mob.level().getBlockCollisions(target, holdBox).iterator().hasNext()) {
            stop(); return;
        }
        if (target instanceof ServerPlayer player) {
            player.setDeltaMovement(hold.subtract(player.position()).scale(0.5));
            player.hurtMarked = true;
        } else {
            target.setPos(hold);
            target.setDeltaMovement(Vec3.ZERO);
        }
        target.fallDistance = 0;
        if (body.swimming()) {
            if (!enteredWater) {
                enteredWater = true;
                duration = ticks + 100;
                startY = mob.getY();
            }
            double down = Math.max(-0.08, startY - 6 - mob.getY());
            if (mob.getY() > startY - 6 && !mob.verticalCollisionBelow
                    && mob.level().noCollision(mob, mob.getBoundingBox().move(0, down, 0))) {
                mob.setDeltaMovement(0, down, 0);
            } else mob.setDeltaMovement(Vec3.ZERO);
            if (target.isEyeInFluid(FluidTags.WATER) && !target.canBreatheUnderwater()
                    && !target.hasEffect(MobEffects.WATER_BREATHING) && !target.hasEffect(MobEffects.CONDUIT_POWER)) {
                int air = target.getAirSupply() - 3;
                // Vanilla checks exactly -20; skipping past it would accidentally prevent drowning.
                if (air <= -20) { target.setAirSupply(0); target.hurt(target.damageSources().drown(), 2); }
                else target.setAirSupply(air);
            }
        }
        if (++ticks % 20 == 0) {
            target.hurt(mob.damageSources().mobAttack(mob), 2);
            if (mob instanceof WaterGhostEntity hostile) hostile.contact();
        }
    }
    public void damage(float amount) {
        if (target == null) return;
        damage += Math.max(0, amount);
        if (damage >= 8) {
            Vec3 forward = Vec3.directionFromRotation(0, mob.getYRot());
            stop();
            mob.knockback(0.6, forward.x, forward.z);
        }
    }
    @Override public void stop() {
        if (target == null) return;
        var tag = target.getPersistentData();
        if (tag.hasUUID(HOLDER) && tag.getUUID(HOLDER).equals(mob.getUUID())) { tag.remove(HOLDER); tag.remove(UNTIL); }
        target = null;
        shoreWater = null;
        body.grabbing(false);
        mob.setDeltaMovement(Vec3.ZERO);
        cooldown = mob.level().getGameTime() + 160;
    }
}
