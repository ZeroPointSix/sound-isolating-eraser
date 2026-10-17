package com.zeropointsix.eraser.rednails;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WaterGhostEntity extends Monster implements WaterGhostPoseSource {
    private static final EntityDataAccessor<Boolean> SWIM = SynchedEntityData.defineId(WaterGhostEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GRAB = SynchedEntityData.defineId(WaterGhostEntity.class, EntityDataSerializers.BOOLEAN);
    private final WaterGhostBody body;
    private int dryTicks;
    private long lastContact;
    private boolean pursuit;

    public WaterGhostEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        body = new WaterGhostBody(this, SWIM, GRAB);
        moveControl = new WaterGhostBody.Movement(body);
        xpReward = 15;
        setPersistenceRequired();
        goalSelector.addGoal(1, body.grab);
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1, false) {
            @Override public boolean canUse() { return mayPursue() && super.canUse(); }
            @Override public boolean canContinueToUse() { return pursuit && super.canContinueToUse(); }
            @Override public void start() {
                if (!pursuit) contact();
                pursuit = true;
                super.start();
            }
        });
        goalSelector.addGoal(3, new WaterGoal());
    }
    @Override protected void registerGoals() { }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(SWIM, false); entityData.define(GRAB, false);
    }
    @Override protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }
    @Override public WaterGhostBody ghostBody() { return body; }
    @Override public float waterGhostSwimAmount(float pt) { return body.swim(pt); }
    @Override public float waterGhostGrabAmount(float pt) { return body.grab(pt); }
    @Override public EntityDimensions getDimensions(Pose pose) { return entityData.get(SWIM) ? WaterGhostBody.SWIMMING : super.getDimensions(pose); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key); if (SWIM.equals(key)) refreshDimensions();
    }
    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions size) { return size.height * 0.8F; }
    @Override public void travel(Vec3 movement) { if (!body.travel()) super.travel(movement); }
    public static boolean prey(LivingEntity e) {
        return e.isAlive() && (e instanceof AbstractVillager || e instanceof Player p && !p.isCreative() && !p.isSpectator());
    }
    public boolean nearWater(LivingEntity e, int radius) {
        if (e.isInWater() || e.getVehicle() instanceof Boat) return true;
        for (BlockPos pos : BlockPos.withinManhattan(e.blockPosition(), radius, 1, radius)) {
            if (WaterGhostBody.waterAt(this, pos)) return true;
        }
        return false;
    }
    public boolean mayPursue() {
        var target = getTarget();
        if (target == null || !prey(target)) return false;
        return pursuit || getLastHurtByMob() == target && tickCount - getLastHurtByMobTimestamp() < 200
                || distanceToSqr(target) <= 36 && nearWater(target, 2);
    }
    public void contact() { lastContact = level().getGameTime(); }
    @Override public void tick() {
        super.tick(); body.tick();
        if (level().isClientSide || !isAlive()) return;
        dryTicks = isInWater() ? 0 : dryTicks + 1;
        // Initialize water posture before the first scan, without waiting ten
        // ticks for a moving target to leave the six-block pursuit start range.
        if (tickCount == 1 || tickCount % 10 == 0) updateTarget();
        if (getTarget() != null && !pursuit) getLookControl().setLookAt(getTarget(), 30, 30);
        if (isInWater() && getTarget() == null && tickCount % (40 + getId() % 41) == 0
                && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 0.5, getZ(), 1, 0.1, 0.1, 0.1, 0);
        }
    }
    private void updateTarget() {
        var target = getTarget();
        if (target != null && (!prey(target) || target.level() != level()
                || pursuit && level().getGameTime() - lastContact >= 200 && !nearWater(target, 8))) {
            setTarget(null); pursuit = false; body.stopMoving(); body.grab.stop();
        }
        if (getTarget() == null || !pursuit) {
            var attacker = getLastHurtByMob();
            if (attacker != null && prey(attacker) && tickCount - getLastHurtByMobTimestamp() < 200) {
                setTarget(attacker); pursuit = true; contact();
            } else {
                var nearest = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16),
                        e -> prey(e) && distanceToSqr(e) <= 256 && hasLineOfSight(e)
                                && (nearWater(e, 2) || isInWater())).stream()
                        .min(java.util.Comparator.comparingInt((LivingEntity e) ->
                                distanceToSqr(e) <= 36 && nearWater(e, 2) ? 0 : 1)
                                .thenComparingDouble(this::distanceToSqr)).orElse(null);
                // A dry passer-by is watched, but never initiates a chase.
                setTarget(nearest);
                if (nearest != null && distanceToSqr(nearest) <= 36 && nearWater(nearest, 2)) {
                    // The melee goal may not poll again before a moving boat leaves range.
                    pursuit = true; contact();
                }
            }
        }
    }
    @Override public boolean doHurtTarget(Entity target) {
        if (body.grabbing() || !(target instanceof LivingEntity living) || !prey(living)) return false;
        boolean hit = super.doHurtTarget(target);
        if (hit) contact();
        return hit;
    }
    private final class WaterGoal extends Goal {
        private BlockPos destination;
        WaterGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        private boolean clearWaterEntry(BlockPos pos) {
            var column = getDimensions(getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos))
                    .expandTowards(0, Math.max(0, getY() - pos.getY()), 0);
            return WaterGhostBody.waterAt(WaterGhostEntity.this, pos)
                    && level().noCollision(WaterGhostEntity.this, column);
        }
        @Override public boolean canUse() { return !pursuit && (isInWater() || dryTicks >= 200); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { destination = null; body.stopMoving(); }
        @Override public void tick() {
            if (isInWater()) {
                if (destination != null) { destination = null; body.stopMoving(); }
                var surface = blockPosition().mutable();
                for (int i = 0; i < 32 && WaterGhostBody.waterAt(WaterGhostEntity.this, surface.above()); i++) surface.move(0, 1, 0);
                double y = surface.getY() - 0.5;
                if (Math.abs(getY() - y) > 0.15) moveControl.setWantedPosition(getX(), y, getZ(), 0.2);
                else body.stopMoving();
            } else {
                var navigation = getNavigation();
                if (destination != null && !clearWaterEntry(destination)) {
                    destination = null; body.stopMoving();
                }
                if (destination != null && navigation.isDone()) {
                    Vec3 entry = Vec3.atBottomCenterOf(destination);
                    Vec3 step = entry.subtract(position()).multiply(1, 0, 1);
                    // A completed path can leave a wide body supported by the bank.
                    // Finish the collision-free horizontal entry; gravity supplies the drop.
                    if (level().noCollision(WaterGhostEntity.this, getBoundingBox().expandTowards(step))) {
                        moveControl.setWantedPosition(entry.x, getY(), entry.z, 1);
                    } else { destination = null; body.stopMoving(); }
                }
                if (destination == null && tickCount % 40 == 0) {
                    var water = new java.util.HashSet<BlockPos>();
                    for (var pos : BlockPos.withinManhattan(blockPosition(), 24, 8, 24)) {
                        if (pos.distSqr(blockPosition()) <= 24 * 24 && clearWaterEntry(pos)) {
                            water.add(pos.immutable());
                        }
                    }
                    // Retain vanilla's original approach accuracy, then verify the
                    // remaining entry against the actual body instead of assuming it is wet.
                    var path = navigation.createPath(water, 1);
                    if (path != null && path.canReach() && path.getEndNode() != null
                            && navigation.moveTo(path, 1)) destination = path.getTarget();
                }
            }
        }
        @Override public void stop() { body.stopMoving(); }
    }
    @Override public boolean isAlliedTo(Entity entity) { return entity instanceof Enemy || super.isAlliedTo(entity); }
    @Override public void die(DamageSource source) { super.die(source); if (dead) { body.grab.stop(); body.dissolve(); } }
    @Override public void remove(RemovalReason reason) { if (body != null) body.grab.stop(); super.remove(reason); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putLong("GrabCooldown", body.grab.cooldownUntil());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); body.grab.cooldownUntil(tag.getLong("GrabCooldown")); setPersistenceRequired();
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override protected boolean canRide(Entity entity) { return false; }
    @Override protected boolean canAddPassenger(Entity entity) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected SoundEvent getAmbientSound() { return isInWater() ? getTarget() == null ? null : SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT; }
    @Override public void playAmbientSound() {
        SoundEvent sound = getAmbientSound();
        if (sound != null) playSound(sound, getSoundVolume(), WaterGhostBody.AMBIENT_PITCH);
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.DROWNED_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.DROWNED_DEATH; }
    @Override public float getVoicePitch() { return WaterGhostBody.HURT_DEATH_PITCH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.GENERIC_SPLASH, 0.3F, 0.7F); }
}
