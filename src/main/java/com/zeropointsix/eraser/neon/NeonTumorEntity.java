package com.zeropointsix.eraser.neon;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.monster.LightTier;
import com.zeropointsix.eraser.registry.ModEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NeonTumorEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(NeonTumorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(NeonTumorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIGHT_TIER = SynchedEntityData.defineId(NeonTumorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AWAKE_TICKS = SynchedEntityData.defineId(NeonTumorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ENGULF_TICKS = SynchedEntityData.defineId(NeonTumorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPAWN_TICKS = SynchedEntityData.defineId(NeonTumorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLAM_TICKS = SynchedEntityData.defineId(NeonTumorEntity.class, EntityDataSerializers.INT);
    private static final float[] HEALTH = {2, 6, 18};
    private static final double[] SPEED = {0.15, 0.18, 0.20};
    private static final double[] DAMAGE = {1, 2, 4};
    private static final int[] EXPERIENCE = {1, 2, 5};
    private static final float[] WIDTH = {0.5F, 1.0F, 2.5F};
    private static final float[] HEIGHT = {0.55F, 1.1F, 2.6F};
    private static final int[] COLORS = {0xB8326A, 0x4FC8F0, 0xAA78DA};
    private static final String[] SIZES = {"small", "medium", "large"};
    private static final DustParticleOptions STEAM = new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.65F), 1.5F);
    private static final RawAnimation DORMANT = RawAnimation.begin().thenLoop("animation.neon_tumor.idle_dormant");
    private static final RawAnimation AWAKE = RawAnimation.begin().thenLoop("animation.neon_tumor.idle_awake");
    private static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("animation.neon_tumor.crawl");
    private static final RawAnimation SPAWN = RawAnimation.begin().thenPlay("animation.neon_tumor.spawn");
    private static final RawAnimation ENGULF = RawAnimation.begin().thenPlay("animation.neon_tumor.engulf");
    private static final RawAnimation DIGEST = RawAnimation.begin().thenLoop("animation.neon_tumor.digest");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.neon_tumor.death");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Long> steamEntry = new HashMap<>();
    private final Map<UUID, Long> engulfCooldown = new HashMap<>();
    private final Map<UUID, Long> contactCooldown = new HashMap<>();
    private int leapCooldown;
    private int slamCooldown;
    private boolean puddleCreated;

    public NeonTumorEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setSizeIndex(1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6)
                .add(Attributes.ATTACK_DAMAGE, 2).add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ARMOR, 0).add(Attributes.KNOCKBACK_RESISTANCE, 0)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SIZE, 1);
        entityData.define(VARIANT, 0);
        entityData.define(LIGHT_TIER, LightTier.DARK.ordinal());
        entityData.define(AWAKE_TICKS, 0);
        entityData.define(ENGULF_TICKS, 0);
        entityData.define(SPAWN_TICKS, 0);
        entityData.define(SLAM_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DormantGoal(this));
        goalSelector.addGoal(2, new EngulfGoal(this));
        goalSelector.addGoal(3, new TendrilSlamGoal(this));
        goalSelector.addGoal(4, new TumorLeapGoal(this));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, false) {
            @Override public boolean canUse() { return !isDormant() && !isEngulfing() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isDormant() && !isEngulfing() && super.canContinueToUse(); }
            @Override protected void checkAndPerformAttack(LivingEntity target, double distance) { contact(target); }
        });
        goalSelector.addGoal(6, new SeekLightGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return canPursue(getLastHurtByMob()) && super.canUse(); }
            @Override public boolean canContinueToUse() { return canPursue(getTarget()) && super.canContinueToUse(); }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::canPursue));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, true, false, this::canPursue));
    }

    public int getSizeIndex() { return entityData.get(SIZE); }
    public int getVariant() { return entityData.get(VARIANT); }
    public void setVariant(int variant) { entityData.set(VARIANT, Mth.clamp(variant, 0, 2)); }
    public boolean isLarge() { return getSizeIndex() == 2; }
    public int getVariantColor() { return COLORS[getVariant()]; }
    public float getModelScale() { return WIDTH[getSizeIndex()]; }
    public LightTier getLightTier() { return LightTier.values()[entityData.get(LIGHT_TIER)]; }
    public LightTier getBehaviorTier() { return getAwakeTicks() > 0 && getLightTier() == LightTier.DARK ? LightTier.BRIGHT : getLightTier(); }
    public int getAwakeTicks() { return entityData.get(AWAKE_TICKS); }
    public int getSpawnTicks() { return entityData.get(SPAWN_TICKS); }
    public boolean isDormant() { return getBehaviorTier() == LightTier.DARK; }
    public boolean isEngulfing() { return entityData.get(ENGULF_TICKS) > 0; }
    public int getEngulfTicks() { return entityData.get(ENGULF_TICKS); }
    public boolean isSlamming() { return entityData.get(SLAM_TICKS) > 0 && isAlive(); }
    public boolean canLeap() { return getSizeIndex() > 0 && leapCooldown == 0; }
    public boolean canSlam() { return isLarge() && slamCooldown == 0; }

    public void setSizeIndex(int size) {
        int index = Mth.clamp(size, 0, 2);
        if (!level().isClientSide && index != 2 && isEngulfing()) releasePassenger();
        entityData.set(SIZE, index);
        refreshDimensions();
        if (!level().isClientSide) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[index]);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DAMAGE[index]);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(index == 2 ? 0.5 : 0);
            updateMovement();
            setHealth(HEALTH[index]);
        }
        xpReward = EXPERIENCE[index];
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(WIDTH[getSizeIndex()], HEIGHT[getSizeIndex()]);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key)) refreshDimensions();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(getModelScale() * 1.5);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData group, @Nullable CompoundTag nbt) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group, nbt);
        int roll = random.nextInt(10);
        setSizeIndex(roll < 4 ? 0 : roll < 8 ? 1 : 2);
        setVariant(random.nextInt(3));
        entityData.set(SPAWN_TICKS, 20);
        return result;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Size", getSizeIndex());
        nbt.putInt("Variant", getVariant());
        nbt.putInt("AwakeTicks", getAwakeTicks());
        nbt.putInt("SpawnTicks", getSpawnTicks());
        nbt.putInt("LeapCooldown", leapCooldown);
        nbt.putInt("SlamCooldown", slamCooldown);
        ListTag cooldowns = new ListTag();
        engulfCooldown.forEach((uuid, until) -> {
            if (until > level().getGameTime()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", uuid);
                entry.putLong("Until", until);
                cooldowns.add(entry);
            }
        });
        nbt.put("EngulfCooldowns", cooldowns);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        int size = nbt.contains("Size", Tag.TAG_ANY_NUMERIC) ? Mth.clamp(nbt.getInt("Size"), 0, 2) : 1;
        setSizeIndex(size);
        // Size owns base attributes; restoring health separately avoids healing on reload.
        setHealth(nbt.contains("Health", Tag.TAG_ANY_NUMERIC) ? nbt.getFloat("Health") : HEALTH[size]);
        setVariant(nbt.getInt("Variant"));
        entityData.set(AWAKE_TICKS, Mth.clamp(nbt.getInt("AwakeTicks"), 0, 200));
        entityData.set(SPAWN_TICKS, Mth.clamp(nbt.getInt("SpawnTicks"), 0, 20));
        entityData.set(ENGULF_TICKS, 0);
        leapCooldown = Mth.clamp(nbt.getInt("LeapCooldown"), 0, 60);
        slamCooldown = Mth.clamp(nbt.getInt("SlamCooldown"), 0, 50);
        engulfCooldown.clear();
        for (Tag tag : nbt.getList("EngulfCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            if (entry.hasUUID("Player") && entry.getLong("Until") > level().getGameTime()) {
                engulfCooldown.put(entry.getUUID("Player"), Math.min(entry.getLong("Until"), level().getGameTime() + 200));
            }
        }
        updateMovement();
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide && isAlive()) {
            decrement(AWAKE_TICKS);
            decrement(SPAWN_TICKS);
            decrement(SLAM_TICKS);
            if (leapCooldown > 0) leapCooldown--;
            if (slamCooldown > 0) slamCooldown--;
            entityData.set(LIGHT_TIER, LightTier.of(level(), blockPosition()).ordinal());
            updateMovement();
            if (!canPursue(getTarget()) && !isEngulfing()) setTarget(null);
            tickDigestion();
            tickSteam();
            if (!isDormant() && tickCount % 40 == 0 && level() instanceof ServerLevel server) {
                server.sendParticles(STEAM, getX(), getY() + 19.0 / 16.0 * getModelScale(), getZ(),
                        1, 0.1, 0.1, 0.1, 0.01);
            }
            if (tickCount % 20 == 0) {
                long now = level().getGameTime();
                engulfCooldown.values().removeIf(until -> until <= now);
                contactCooldown.values().removeIf(until -> until <= now);
            }
            if (!isEngulfing() && isVehicle()) ejectPassengers();
        }
        super.aiStep();
    }

    private void decrement(EntityDataAccessor<Integer> key) {
        if (entityData.get(key) > 0) entityData.set(key, entityData.get(key) - 1);
    }

    private void updateMovement() {
        double speed = SPEED[getSizeIndex()] * (getBehaviorTier() == LightTier.DIM ? 0.5 : isDormant() ? 0 : 1);
        if (getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() != speed) {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        }
    }

    public boolean canPursue(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !isDormant() && canAttack(target)
                && !(target instanceof NeonTumorEntity)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && distanceToSqr(target) <= (getBehaviorTier() == LightTier.DIM ? 9 : 256);
    }

    public void awaken() {
        if (!level().isClientSide) {
            entityData.set(AWAKE_TICKS, 200);
            updateMovement();
        }
    }

    public void contact(LivingEntity victim) {
        if (level().isClientSide || !isAlive() || isEngulfing() || !victim.isAlive()
                || !(victim instanceof Player || victim instanceof IronGolem)
                || victim instanceof Player player && (player.isCreative() || player.isSpectator())
                || !getBoundingBox().inflate(0.1).intersects(victim.getBoundingBox())) return;
        long now = level().getGameTime();
        if (contactCooldown.getOrDefault(victim.getUUID(), 0L) > now) return;
        contactCooldown.put(victim.getUUID(), now + 20);
        if (isDormant()) {
            awaken();
            setTarget(victim);
            return;
        }
        victim.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        victim.addEffect(new MobEffectInstance(ModEffects.CORRODED.get(), (getSizeIndex() + 1) * 40), this);
    }

    @Override public void playerTouch(Player player) { super.playerTouch(player); contact(player); }
    @Override public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolem golem) contact(golem);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL) || source.is(CorrosionDamage.TYPE)) return false;
        if (source.getDirectEntity() == source.getEntity() && source.getEntity() instanceof LivingEntity attacker
                && (attacker.getMainHandItem().getItem() instanceof SwordItem || attacker.getMainHandItem().getItem() instanceof AxeItem)) {
            amount *= 2;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && !level().isClientSide) {
            awaken();
            triggerAnim("hurt", "hurt");
        }
        return hit;
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBeAffected(MobEffectInstance effect) {
        return effect.getEffect() != ModEffects.CORRODED.get() && super.canBeAffected(effect);
    }

    private void tickSteam() {
        if (!isLarge() || getLightTier() != LightTier.BRIGHT) {
            steamEntry.clear();
            return;
        }
        long now = level().getGameTime();
        Set<UUID> inside = new HashSet<>();
        // Check exits every tick; even a brief departure resets continuous exposure.
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(5))) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || distanceToSqr(player) > 25) continue;
            inside.add(player.getUUID());
            long entered = steamEntry.computeIfAbsent(player.getUUID(), id -> now);
            if (now - entered >= 60) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        }
        steamEntry.keySet().retainAll(inside);
        if (level() instanceof ServerLevel server && tickCount % 5 == 0) {
            server.sendParticles(STEAM, getX(), getY() + 1.1, getZ(), 8, 2.5, 0.6, 2.5, 0.01);
        }
    }

    public boolean canEngulf(@Nullable LivingEntity target) {
        return isLarge() && !isDormant() && !isEngulfing() && !isVehicle() && isAlive()
                && target instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && !player.isPassenger() && player.hasEffect(MobEffects.CONFUSION)
                && distanceToSqr(player) <= 4 && hasLineOfSight(player)
                && engulfCooldown.getOrDefault(player.getUUID(), 0L) <= level().getGameTime();
    }

    public boolean beginEngulf(Player player) {
        if (level().isClientSide || !canEngulf(player)) return false;
        if (!player.startRiding(this, true)) return false;
        entityData.set(ENGULF_TICKS, 1);
        getNavigation().stop();
        playSound(SoundEvents.SLIME_SQUISH, 1, 0.6F);
        return true;
    }

    private void tickDigestion() {
        if (!isEngulfing()) return;
        if (!(getFirstPassenger() instanceof Player player) || !player.isAlive() || player.isCreative()
                || player.isSpectator() || player.level() != level() || !isLarge()) {
            releasePassenger();
            return;
        }
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        int elapsed = getEngulfTicks();
        if (elapsed % 20 == 0) {
            player.hurt(damageSources().mobAttack(this), 2);
            player.addEffect(new MobEffectInstance(ModEffects.CORRODED.get(), 120), this);
        }
        if (elapsed >= 80) releasePassenger();
        else entityData.set(ENGULF_TICKS, elapsed + 1);
    }

    public void releasePassenger() {
        entityData.set(ENGULF_TICKS, 0);
        for (Entity passenger : getPassengers()) {
            engulfCooldown.put(passenger.getUUID(), level().getGameTime() + 200);
        }
        // Clear the mount lock before ejectPassengers fires cancellable Forge mount events.
        ejectPassengers();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            entityData.set(ENGULF_TICKS, 0);
            engulfCooldown.put(passenger.getUUID(), level().getGameTime() + 200);
        }
    }

    @Override public boolean canRiderInteract() { return true; }
    @Override protected boolean canAddPassenger(Entity passenger) { return isLarge() && isAlive() && !isVehicle() && passenger instanceof Player; }
    @Override public double getPassengersRidingOffset() { return 0.25; }
    @Override protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (hasPassenger(passenger)) move.accept(passenger, getX(), getY() + 0.25, getZ());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (int radius = 2; radius <= 4; radius++) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * i / 4;
                Vec3 candidate = position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                AABB box = passenger.getDimensions(Pose.STANDING).makeBoundingBox(candidate);
                if (level().getWorldBorder().isWithinBounds(box) && level().noCollision(passenger, box)
                        && !level().noCollision(passenger, box.move(0, -0.2, 0))) return candidate;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    public void startLeap() { leapCooldown = 60; triggerAnim("attack", "lunge"); playSound(SoundEvents.SLIME_ATTACK, 1, 1); }
    public void startSlam() { slamCooldown = 50; entityData.set(SLAM_TICKS, 14); triggerAnim("attack", "slam"); playSound(SoundEvents.SLIME_ATTACK, 1, 1); }
    public void stopSlam() { entityData.set(SLAM_TICKS, 0); }

    @Override
    public void die(DamageSource source) {
        releasePassenger();
        super.die(source);
        if (!level().isClientSide && !puddleCreated) {
            puddleCreated = true;
            AreaEffectCloud cloud = new AreaEffectCloud(level(), getX(), getY(), getZ());
            cloud.setOwner(this);
            cloud.setRadius(new float[]{0.5F, 1F, 2F}[getSizeIndex()]);
            cloud.setWaitTime(0);
            cloud.setDuration(60);
            cloud.setRadiusOnUse(0);
            cloud.setRadiusPerTick(0);
            cloud.setDurationOnUse(0);
            cloud.setFixedColor(getVariantColor());
            cloud.addEffect(new MobEffectInstance(ModEffects.CORRODED.get(), 40));
            level().addFreshEntity(cloud);
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.7F, 1);
            ((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 12, getBbWidth() / 2, 0.1, getBbWidth() / 2, 0.02);
        }
    }

    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide) releasePassenger();
        super.remove(reason);
    }
    @Override protected ResourceLocation getDefaultLootTable() { return new ResourceLocation(ModMain.MOD_ID, "entities/neon_tumor_" + SIZES[getSizeIndex()]); }
    @Override public int getExperienceReward() { return EXPERIENCE[getSizeIndex()]; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.BEACON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SLIME_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SLIME_SQUISH; }
    @Override protected float getSoundVolume() { return isDormant() ? 0.5F : 1.0F; }
    @Override public void playAmbientSound() { playSound(SoundEvents.BEACON_AMBIENT, getSoundVolume(), new float[]{1.6F, 1.2F, 0.8F}[getSizeIndex()]); }
    @Override protected void playHurtSound(DamageSource source) { playSound(SoundEvents.SLIME_HURT, 1, 1.6F); }
    @Override protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.SLIME_SQUISH_SMALL, 0.3F, 1);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

    private PlayState animate(AnimationState<NeonTumorEntity> state) {
        state.getController().setAnimationSpeed(1);
        if (isDeadOrDying()) return state.setAndContinue(DEATH);
        if (getSpawnTicks() > 0) return state.setAndContinue(SPAWN);
        if (isEngulfing()) return state.setAndContinue(getEngulfTicks() <= 20 ? ENGULF : DIGEST);
        if (isDormant()) return state.setAndContinue(DORMANT);
        if (getBehaviorTier() == LightTier.DIM) state.getController().setAnimationSpeed(0.5);
        return state.setAndContinue(state.isMoving() ? CRAWL : AWAKE);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, this::animate));
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("lunge", RawAnimation.begin().thenPlay("animation.neon_tumor.lunge"))
                .triggerableAnim("slam", RawAnimation.begin().thenPlay("animation.neon_tumor.slam")));
        controllers.add(new AnimationController<>(this, "hurt", 0, state -> PlayState.STOP)
                .triggerableAnim("hurt", RawAnimation.begin().thenPlay("animation.neon_tumor.hurt")));
    }
}
