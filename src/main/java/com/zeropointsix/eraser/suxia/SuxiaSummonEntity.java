package com.zeropointsix.eraser.suxia;

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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
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
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 10, 2, false));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                e -> e instanceof Mob mob && getOwner() != null && mob.getTarget() == getOwner()
                        && mob.distanceToSqr(getOwner()) <= 256 && wantsToAttack(mob, getOwner())));
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
        if (target == null || owner == null || target == owner || !target.isAlive()
                || target instanceof AbstractVillager || target instanceof IronGolem
                || isAlliedTo(target)) return false;
        if (target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID())) return false;
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && getServer() != null
                    && getServer().isPvpAllowed() && owner instanceof Player p && p.canHarmPlayer(player);
        }
        return true;
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
