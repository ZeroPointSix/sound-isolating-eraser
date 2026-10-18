package com.zeropointsix.eraser.suxia;

import com.zeropointsix.eraser.memory.SummonCombatConfig;
import com.zeropointsix.eraser.memory.SummonGuardGoal;
import com.zeropointsix.eraser.memory.SummonMeleeAttackGoal;
import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SuxiaSummonEntity extends TamableAnimal implements SuxiaPoseSource {
    private static final EntityDataAccessor<Boolean> CRAWL = SynchedEntityData.defineId(
            SuxiaSummonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMB = SynchedEntityData.defineId(
            SuxiaSummonEntity.class, EntityDataSerializers.BOOLEAN);
    private final SuxiaMotion motion = new SuxiaMotion();
    @Nullable private UUID watch;

    public SuxiaSummonEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        SuxiaMotion.configureSummon(this);
        setPersistenceRequired();
        xpReward = 0;
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CRAWL, false);
        entityData.define(CLIMB, false);
    }

    @Override protected PathNavigation createNavigation(Level level) { return new WallClimberNavigation(this, level); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SummonMeleeAttackGoal(this, 1.2,
                () -> SummonCombatConfig.value(SummonCombatConfig.SUXIA_ATTACK_TICKS)));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 10, 2, false));
        goalSelector.addGoal(4, new IdleBehindOwnerGoal());
        targetSelector.addGoal(1, new SummonGuardGoal(this));
    }

    @Nullable private Vec3 idlePosition;

    private final class IdleBehindOwnerGoal extends Goal {
        private int repathTicks;

        IdleBehindOwnerGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            var owner = getOwner();
            return owner != null && owner.isAlive() && !owner.isSpectator()
                    && !isOrderedToSit() && getTarget() == null;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { repathTicks = 0; }
        @Override public void stop() { idlePosition = null; getNavigation().stop(); }

        @Override public void tick() {
            var owner = getOwner();
            if (owner == null) return;
            // Use yaw alone: looking straight up must not collapse the rear offset.
            Vec3 behind = owner.position().subtract(Vec3.directionFromRotation(0, owner.getYRot()).scale(2));
            idlePosition = behind;
            if (position().subtract(behind).horizontalDistanceSqr() <= 0.5 * 0.5
                    && Math.abs(getY() - behind.y) < 1) {
                getNavigation().stop();
                repathTicks = 0;
            } else if (--repathTicks <= 0) {
                repathTicks = 10;
                getNavigation().moveTo(behind.x, behind.y, behind.z, 1.1);
            }
            // Mirror the owner's view direction, not the owner's position or a nearby player.
            Vec3 gaze = getEyePosition().add(owner.getLookAngle().scale(8));
            // A vertical view ray has no horizontal heading for LookControl to recover.
            setYHeadRot(owner.getYRot());
            getLookControl().setLookAt(gaze.x, gaze.y, gaze.z, 360, 360);
        }
    }

    public float clientPitchInterpolationTarget() {
        return lerpSteps > 0 ? (float) lerpXRot : getXRot();
    }

    @Override protected float tickHeadTurn(float yaw, float walkAnimation) {
        float receivedHeadYaw = getYHeadRot();
        float amount = super.tickHeadTurn(yaw, walkAnimation);
        if (level().isClientSide && getOwnerUUID() != null && !isAggressive()) {
            // Delayed position interpolation can clamp the final head packet to an
            // old travel heading. Keep the server's idle look, not that local clamp.
            setYHeadRot(receivedHeadYaw);
        }
        return amount;
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (idlePosition == null || getTarget() != null || !getNavigation().isDone()) return;
        double error = position().subtract(idlePosition).horizontalDistanceSqr();
        if (error > 4 || Math.abs(getY() - idlePosition.y) >= 0.5) return;
        // Wall-climber paths finish at block granularity. Finish the short, level
        // approach after navigation, without teleporting or changing combat paths.
        if (error > 0.5 * 0.5) {
            getMoveControl().setWantedPosition(idlePosition.x, idlePosition.y, idlePosition.z, 1.1);
        } else {
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
        }
    }

    public void bind(Player owner, UUID watchId) {
        setTame(true);
        setOwnerUUID(owner.getUUID());
        watch = watchId;
    }

    public boolean isCrawling() { return entityData.get(CRAWL); }
    public void setCrawling(boolean value) {
        entityData.set(CRAWL, value);
        if (!level().isClientSide) SuxiaMotion.speed(this, value);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (CRAWL.equals(key)) refreshDimensions();
    }

    @Override public EntityDimensions getDimensions(Pose pose) {
        return isCrawling() ? SuxiaMotion.CRAWLING : super.getDimensions(pose);
    }

    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.8F;
    }

    @Override public float suxiaCrawlAmount(float pt) { return motion.crawl(pt); }
    @Override public boolean onClimbable() { return entityData.get(CLIMB); }

    @Override public void tick() {
        if (!level().isClientSide && tickCount % 20 == 0) SuxiaMotion.configureSummon(this);
        super.tick();
        motion.tick(isCrawling(), false);
        if (level().isClientSide || !isAlive()) return;
        entityData.set(CLIMB, horizontalCollision && isCrawling());
        var ledger = SuxiaSummons.get(getServer());
        var entry = watch == null ? null : ledger.find(watch);
        if (entry == null || !getUUID().equals(entry.summon)) { discard(); return; }
        var owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level()
                || SuxiaMotion.clock(level()) >= entry.until) { finish(false); return; }
        if (distanceToSqr(owner) > 4096 && !teleportNear(owner)) { finish(false); return; }
        if (getTarget() != null && !wantsToAttack(getTarget(), owner)) setTarget(null);
        setAggressive(getTarget() != null);
        if (tickCount % 5 == 0) {
            setCrawling(!SuxiaMotion.canStand(this) || getTarget() != null && distanceToSqr(getTarget()) > 16
                    || distanceToSqr(owner) > 36);
        }
    }

    private boolean teleportNear(LivingEntity owner) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
            Vec3 at = Vec3.atBottomCenterOf(owner.blockPosition().offset(x, 0, z));
            if (SuxiaMotion.place(this, at)) { getNavigation().stop(); return true; }
        }
        return false;
    }

    @Override public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return com.zeropointsix.eraser.memory.SummonTargets.allowed(this, target, owner);
    }

    @Override public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living) || !wantsToAttack(living, getOwner())) return false;
        boolean hit = target.hurt(damageSources().mobAttack(this), SuxiaMotion.damage(this));
        if (hit) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.4 * Math.max(0,
                    1 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)), 0));
            doEnchantDamageEffects(this, target);
        }
        return hit;
    }

    public void finish(boolean died) {
        if (level().isClientSide || watch == null) return;
        var ledger = SuxiaSummons.get(getServer());
        var entry = ledger.find(watch);
        if (entry == null || !ledger.finish(entry, getUUID(), SuxiaMotion.clock(level()), died)) return;
        var owner = getOwner();
        if (owner instanceof Player player) {
            if (died) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
            }
            player.displayClientMessage(Component.translatable("message.sound_isolating_eraser.suxia_pocket_watch."
                    + (died ? "fell" : "expired")), false);
            SuxiaPocketWatchItem.syncInventory(player);
        }
        SuxiaMotion.smoke(this);
        playSound(SoundEvents.CHAIN_PLACE, 0.7F, 0.8F);
        if (!died) discard();
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (dead) finish(true);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (watch != null) tag.putUUID("SuxiaWatch", watch);
        tag.putBoolean("SuxiaCrawling", isCrawling());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        SuxiaMotion.configureSummon(this);
        watch = tag.hasUUID("SuxiaWatch") ? tag.getUUID("SuxiaWatch") : null;
        setCrawling(tag.getBoolean("SuxiaCrawling"));
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override protected boolean canRide(Entity entity) { return false; }
    @Override protected boolean canAddPassenger(Entity entity) { return false; }
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public boolean canFallInLove() { return false; }
    @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) { return null; }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.DROWNED_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.DROWNED_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.DROWNED_DEATH; }
    @Override public float getVoicePitch() { return 0.7F; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(isCrawling() ? SoundEvents.SPIDER_STEP : state.getSoundType().getStepSound(),
                isCrawling() ? 0.15F : 0.1F, 0.8F);
    }
}
