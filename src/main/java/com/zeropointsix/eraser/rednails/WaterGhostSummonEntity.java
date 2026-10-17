package com.zeropointsix.eraser.rednails;

import com.zeropointsix.eraser.memory.SummonTargets;
import com.zeropointsix.eraser.memory.SummonCombatConfig;
import com.zeropointsix.eraser.memory.SummonGuardGoal;
import com.zeropointsix.eraser.memory.SummonMeleeAttackGoal;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WaterGhostSummonEntity extends TamableAnimal implements WaterGhostPoseSource {
    private static final EntityDataAccessor<Boolean> SWIM = SynchedEntityData.defineId(WaterGhostSummonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GRAB = SynchedEntityData.defineId(WaterGhostSummonEntity.class, EntityDataSerializers.BOOLEAN);
    private final WaterGhostBody body;
    private final SummonGuardGoal targets;
    private UUID itemId;
    private int nail;

    public WaterGhostSummonEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        WaterGhostBody.configureSummon(this);
        body = new WaterGhostBody(this, SWIM, GRAB);
        targets = new SummonGuardGoal(this, this::assigned, 2);
        moveControl = new WaterGhostBody.Movement(body);
        setPersistenceRequired();
        xpReward = 0;
        goalSelector.addGoal(1, body.grab);
        goalSelector.addGoal(2, new SummonMeleeAttackGoal(this, 1,
                () -> SummonCombatConfig.value(SummonCombatConfig.WATER_GHOST_ATTACK_TICKS)));
        goalSelector.addGoal(3, new GuardGoal());
        targetSelector.addGoal(1, targets);
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
    public UUID itemId() { return itemId; }
    public int nail() { return nail; }
    public void bind(Player owner, UUID item, int finger) {
        setTame(true); setOwnerUUID(owner.getUUID()); itemId = item; nail = finger;
    }
    @Override public void tick() {
        if (!level().isClientSide && tickCount % 20 == 0) WaterGhostBody.configureSummon(this);
        super.tick(); body.tick();
        if (level().isClientSide || !isAlive()) return;
        var entry = itemId == null ? null : RedNailsState.get(getServer()).find(itemId);
        if (entry == null || nail < 0 || nail > 3 || !getUUID().equals(entry.ghosts[nail])) {
            body.grab.stop(); discard(); return;
        }
        var owner = getOwner();
        if (!(owner instanceof Player player) || !owner.isAlive() || owner.level() != level()
                || !RedNailsItem.isWorn(player, itemId)) { finish(false); return; }
        double leash = getTarget() == null ? 12 : Math.max(12, SummonCombatConfig.guardRadius() + 4);
        if (distanceToSqr(owner) > leash * leash && !teleportNear(owner) && distanceToSqr(owner) > 4096) {
            finish(false); return;
        }
        if (getTarget() != null && !wantsToAttack(getTarget(), owner)) setTarget(null);
    }
    public Vec3 guardPosition(LivingEntity owner) {
        int direction = switch (nail) { case 0 -> 1; case 2 -> 2; case 3 -> 3; default -> 0; };
        return owner.position().add(Vec3.directionFromRotation(0, owner.getYRot() + direction * 90).scale(2.5));
    }
    public boolean teleportNear(LivingEntity owner) {
        if (body.grabbing()) body.grab.stop();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
            var pos = owner.blockPosition().offset(x, 0, z);
            if (WaterGhostBody.place(this, Vec3.atBottomCenterOf(pos), WaterGhostBody.waterAt(this, pos))) {
                getNavigation().stop(); return true;
            }
        }
        return false;
    }
    @Override public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return SummonTargets.allowed(this, target, owner);
    }
    @Override public boolean doHurtTarget(Entity target) {
        return !body.grabbing() && target instanceof LivingEntity living && wantsToAttack(living, getOwner())
                && super.doHurtTarget(target);
    }
    public int assigned(LivingEntity target) {
        var owner = getOwner();
        if (owner == null) return 2;
        return level().getEntitiesOfClass(WaterGhostSummonEntity.class, owner.getBoundingBox().inflate(64),
                ghost -> ghost != this && ghost.isAlive() && owner.getUUID().equals(ghost.getOwnerUUID())
                        && ghost.getTarget() == target).size();
    }
    public void chooseTarget() {
        targets.chooseTarget();
    }
    private final class GuardGoal extends Goal {
        GuardGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return getOwner() != null && getTarget() == null; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            var owner = getOwner();
            if (owner == null) return;
            var at = guardPosition(owner);
            if (position().distanceToSqr(at) > 9) getNavigation().moveTo(at.x, at.y, at.z, 1);
            else body.stopMoving();
            getLookControl().setLookAt(owner, 30, 30);
        }
        @Override public void stop() { body.stopMoving(); }
    }
    public void finish(boolean died) {
        if (level().isClientSide || itemId == null) return;
        body.grab.stop();
        RedNailsItem.ghostEnded(this, died);
        body.dissolve();
        if (!died) discard();
    }
    @Override public void die(DamageSource source) { super.die(source); if (dead) finish(true); }
    @Override public void remove(RemovalReason reason) { if (body != null) body.grab.stop(); super.remove(reason); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (itemId != null) tag.putUUID("ItemId", itemId);
        tag.putInt("Nail", nail); tag.putLong("GrabCooldown", body.grab.cooldownUntil());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        WaterGhostBody.configureSummon(this);
        itemId = tag.hasUUID("ItemId") ? tag.getUUID("ItemId") : null;
        nail = tag.getInt("Nail"); body.grab.cooldownUntil(tag.getLong("GrabCooldown"));
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override protected boolean canRide(Entity entity) { return false; }
    @Override protected boolean canAddPassenger(Entity entity) { return false; }
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public boolean canFallInLove() { return false; }
    @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) { return null; }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected SoundEvent getAmbientSound() { return isInWater() ? SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.DROWNED_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.DROWNED_DEATH; }
    @Override public float getVoicePitch() { return 0.7F; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.GENERIC_SPLASH, 0.3F, 0.7F); }
}
