package com.zeropointsix.eraser.shadow;

import com.zeropointsix.eraser.ModMain;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class ShadowTanglerEntity extends Monster implements GeoEntity {
    public static final ResourceKey<DamageType> LIGHT_BURN = ResourceKey.create(
            Registries.DAMAGE_TYPE, id("light_burn"));
    private static final EntityDataAccessor<Integer> LIGHT_TIER = SynchedEntityData.defineId(
            ShadowTanglerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPAWN_AGE = SynchedEntityData.defineId(
            ShadowTanglerEntity.class, EntityDataSerializers.INT);
    private static final UUID LIGHT_SPEED_ID = UUID.fromString("d3096da1-6129-4cbd-bf91-f61f296b6198");
    private static final RawAnimation IDLE = animation("idle", true);
    private static final RawAnimation WALK = animation("walk", true);
    private static final RawAnimation RUN = animation("run", true);
    private static final RawAnimation SPAWN = animation("spawn", false);
    private static final RawAnimation FLINCH = animation("light_flinch", true);
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.shadow_tangler.death");
    private static final RawAnimation ATTACK = animation("attack", false);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int dimTicks;
    private int brightTicks;
    private int retreatTicks;
    private boolean shattered;
    private int attackWindup;
    private LivingEntity pendingAttack;

    public ShadowTanglerEntity(EntityType<? extends ShadowTanglerEntity> type, Level level) {
        super(type, level);
        xpReward = 5;
        setCanPickUpLoot(false);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ModMain.MOD_ID, path);
    }

    private static RawAnimation animation(String name, boolean loop) {
        String key = "animation.shadow_tangler." + name;
        return loop ? RawAnimation.begin().thenLoop(key) : RawAnimation.begin().thenPlay(key);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.ARMOR, 0)
                .add(Attributes.MOVEMENT_SPEED, 0.40).add(Attributes.FOLLOW_RANGE, 35)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LIGHT_TIER, ShadowLight.DARK);
        entityData.define(SPAWN_AGE, 0);
    }

    public int getLightTier() { return entityData.get(LIGHT_TIER); }
    public int getSpawnAge() { return entityData.get(SPAWN_AGE); }
    public boolean isSpawning() { return getSpawnAge() < ShadowLight.SPAWN_TICKS; }
    public boolean isRetreating() { return retreatTicks > 0 || getLightTier() == ShadowLight.BRIGHT; }
    public boolean wantsToFlee() { return isRetreating() && getLightTier() != ShadowLight.DARK; }

    public int brightnessAt(BlockPos pos) {
        return level().getMaxLocalRawBrightness(pos);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new LightAversionNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return isSpawning(); }
            @Override public void tick() { getNavigation().stop(); }
        });
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new FleeLightGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1D, false) {
            @Override public boolean canUse() { return canPursue() && super.canUse(); }
            @Override public boolean canContinueToUse() { return canPursue() && super.canContinueToUse(); }
            @Override protected int getAttackInterval() { return 20; }
            @Override protected void checkAndPerformAttack(LivingEntity target, double distanceSqr) {
                if (distanceSqr <= getAttackReachSqr(target) && isTimeToAttack() && canPursue()) {
                    resetAttackCooldown();
                    pendingAttack = target;
                    attackWindup = 5;
                    swing(InteractionHand.MAIN_HAND);
                    triggerAnim("attack", "attack");
                }
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D) {
            @Override public boolean canUse() { return !isSpawning() && !wantsToFlee() && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    private boolean canPursue() { return !isSpawning() && !isRetreating() && isAlive(); }

    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target != null && !canPursue() ? null : target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return canPursue() && super.canAttack(target);
    }

    public void refreshLightTier() {
        if (level().isClientSide) return;
        int tier = ShadowLight.tier(brightnessAt(blockPosition()));
        int previous = getLightTier();
        entityData.set(LIGHT_TIER, tier);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        double modifier = ShadowLight.speedMultiplier(tier) - 1D;
        var existing = speed.getModifier(LIGHT_SPEED_ID);
        if ((existing != null && existing.getAmount() != modifier) || (existing == null && modifier != 0D)) {
            speed.removeModifier(LIGHT_SPEED_ID);
            if (modifier != 0D) speed.addTransientModifier(new AttributeModifier(
                    LIGHT_SPEED_ID, "Shadow light aversion", modifier, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (tier != previous) {
            getNavigation().stop();
            if (tier == ShadowLight.BRIGHT) playSound(SoundEvents.FIRE_EXTINGUISH, 0.7F, 0.7F);
        }
    }

    @Override
    public void tick() {
        if (!level().isClientSide && isAlive()) {
            refreshLightTier();
            if (retreatTicks > 0 && getLightTier() == ShadowLight.DARK) --retreatTicks;
            dimTicks = getLightTier() == ShadowLight.DIM
                    ? Math.min(ShadowLight.DIM_RETREAT_TICKS, dimTicks + 1) : 0;
            if (getLightTier() == ShadowLight.BRIGHT || dimTicks >= ShadowLight.DIM_RETREAT_TICKS) {
                retreatTicks = ShadowLight.RETREAT_COOLDOWN_TICKS;
                setTarget(null);
                setLastHurtByMob(null);
            }
            if (pendingAttack != null) {
                if (!canPursue() || !pendingAttack.isAlive() || getTarget() != pendingAttack) {
                    pendingAttack = null;
                    attackWindup = 0;
                } else if (--attackWindup <= 0) {
                    if (isWithinMeleeAttackRange(pendingAttack) && getSensing().hasLineOfSight(pendingAttack)) {
                        doHurtTarget(pendingAttack);
                    }
                    pendingAttack = null;
                }
            }
            if (isSpawning()) {
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
            }
            if (getLightTier() == ShadowLight.BRIGHT) {
                if (++brightTicks >= 20) {
                    brightTicks = 0;
                    burnInLight();
                }
            } else brightTicks = 0;
        }
        super.tick();
        if (!level().isClientSide && isSpawning()) {
            entityData.set(SPAWN_AGE, getSpawnAge() + 1);
        }
        if (level().isClientSide && isAlive()) {
            if (getLightTier() == ShadowLight.BRIGHT && tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, getRandomX(0.4), getY() + 0.9, getRandomZ(0.4), 0, 0.02, 0);
            } else if (tickCount % 10 == 0 && random.nextBoolean()) {
                level().addParticle(ParticleTypes.SQUID_INK, getRandomX(0.4), getY() + 0.3, getRandomZ(0.4), 0, 0.005, 0);
            }
        }
    }

    public void burnInLight() {
        if (!(level() instanceof ServerLevel) || !isAlive() || getLightTier() != ShadowLight.BRIGHT) return;
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(LIGHT_BURN));
        int previousInvulnerability = invulnerableTime;
        float previousLastHurt = lastHurt;
        try {
            hurt(source, 2F);
        } finally {
            // Light ticks must not consume a player's hit or reset an existing hit's protection.
            invulnerableTime = previousInvulnerability;
            lastHurt = previousLastHurt;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) refreshLightTier();
        return super.hurt(source, amount);
    }

    @Override
    protected float getDamageAfterArmorAbsorb(DamageSource source, float amount) {
        // Forge's hurt-event bonuses (including Shaxiadao) must receive the same light multiplier.
        if (!source.is(LIGHT_BURN) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= ShadowLight.damageMultiplier(getLightTier());
        }
        return super.getDamageAfterArmorAbsorb(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!canPursue()) return false;
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1), this);
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) refreshLightTier();
        shattered = getLightTier() == ShadowLight.BRIGHT;
        super.die(source);
        if (!dead || !(level() instanceof ServerLevel server)) return;
        if (shattered) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACK_STAINED_GLASS.defaultBlockState()),
                    getX(), getY() + 1, getZ(), 45, 0.35, 0.8, 0.35, 0.12);
            remove(RemovalReason.KILLED);
        } else {
            server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.3, getZ(), 20, 0.35, 0.2, 0.35, 0.02);
        }
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return id(shattered ? "entities/shadow_tangler_shattered" : "entities/shadow_tangler");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ShadowSpawnAge", getSpawnAge());
        tag.putInt("ShadowDimTicks", dimTicks);
        tag.putInt("ShadowBrightTicks", brightTicks);
        tag.putInt("ShadowRetreatTicks", retreatTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SPAWN_AGE, Math.max(0, Math.min(ShadowLight.SPAWN_TICKS, tag.getInt("ShadowSpawnAge"))));
        dimTicks = Math.max(0, Math.min(ShadowLight.DIM_RETREAT_TICKS, tag.getInt("ShadowDimTicks")));
        brightTicks = Math.max(0, Math.min(19, tag.getInt("ShadowBrightTicks")));
        retreatTicks = Math.max(0, Math.min(ShadowLight.RETREAT_COOLDOWN_TICKS, tag.getInt("ShadowRetreatTicks")));
        setCanPickUpLoot(false);
    }

    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) { return 1.74F; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SCULK_BLOCK_SPREAD; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() {
        return getLightTier() == ShadowLight.BRIGHT ? SoundEvents.GLASS_BREAK : SoundEvents.SCULK_BLOCK_BREAK;
    }
    @Override public float getVoicePitch() { return 0.6F; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.WOOL_STEP, 0.3F, 1F); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 0, state -> {
            RawAnimation chosen = isDeadOrDying() ? DEATH : isSpawning() ? SPAWN
                    : getLightTier() == ShadowLight.BRIGHT ? FLINCH
                    : getDeltaMovement().horizontalDistanceSqr() > 0.0225D ? RUN
                    : state.isMoving() ? WALK : IDLE;
            return state.setAndContinue(chosen);
        }));
        controllers.add(new AnimationController<>(this, "attack", 0, state ->
                isDeadOrDying() || isSpawning() || getLightTier() == ShadowLight.BRIGHT
                        || !state.getController().isPlayingTriggeredAnimation()
                        ? PlayState.STOP : PlayState.CONTINUE)
                .triggerableAnim("attack", ATTACK).receiveTriggeredAnimations());
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
