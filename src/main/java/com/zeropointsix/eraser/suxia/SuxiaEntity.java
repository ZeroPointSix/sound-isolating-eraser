package com.zeropointsix.eraser.suxia;

import com.zeropointsix.eraser.eraser.EraserAggro;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;

public class SuxiaEntity extends Monster implements SuxiaPoseSource, VibrationSystem {
    private static final EntityDataAccessor<Boolean> CRAWL = SynchedEntityData.defineId(SuxiaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEAD = SynchedEntityData.defineId(SuxiaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMB = SynchedEntityData.defineId(SuxiaEntity.class, EntityDataSerializers.BOOLEAN);
    private final SuxiaMotion motion = new SuxiaMotion();
    private VibrationSystem.Data vibrations = new VibrationSystem.Data();
    private final VibrationSystem.User hearing = new Hearing();
    private final DynamicGameEventListener<VibrationSystem.Listener> listener =
            new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
    private long lastSense, lastTargetSense, ambushUntil;
    @Nullable private BlockPos sound;
    private long searchUntil;
    private float sleepingYaw;

    public SuxiaEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 20;
        lastSense = SuxiaMotion.clock(level);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CRAWL, false);
        entityData.define(DEAD, false);
        entityData.define(CLIMB, false);
    }

    @Override protected PathNavigation createNavigation(Level level) { return new WallClimberNavigation(this, level); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return isPlayingDead(); }
            @Override public void start() { getNavigation().stop(); }
            @Override public void tick() {
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
                setYRot(sleepingYaw); setYHeadRot(sleepingYaw); yBodyRot = sleepingYaw;
            }
        });
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(3, new InvestigateSound());
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
    }

    public boolean isCrawling() { return entityData.get(CRAWL); }
    public boolean isPlayingDead() { return entityData.get(DEAD); }
    public void setCrawling(boolean value) {
        entityData.set(CRAWL, value);
        if (!level().isClientSide) SuxiaMotion.speed(this, value);
    }
    public void setPlayingDead(boolean value) {
        if (value && !isPlayingDead()) sleepingYaw = getYRot();
        entityData.set(DEAD, value);
        if (value) { getNavigation().stop(); setCrawling(false); setTarget(null); }
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (CRAWL.equals(key) || DEAD.equals(key)) refreshDimensions();
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        return isPlayingDead() ? SuxiaMotion.DEAD : isCrawling() ? SuxiaMotion.CRAWLING : super.getDimensions(pose);
    }
    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) { return dimensions.height * 0.8F; }
    @Override public float suxiaCrawlAmount(float pt) { return motion.crawl(pt); }
    @Override public float suxiaPlayDeadAmount(float pt) { return motion.dead(pt); }
    @Override public boolean onClimbable() { return entityData.get(CLIMB); }

    public static boolean prey(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        return entity instanceof Player player ? !player.isCreative() && !player.isSpectator()
                : entity instanceof AbstractVillager;
    }

    public boolean smells(LivingEntity entity) {
        if (!prey(entity)) return false;
        var tag = entity.getPersistentData();
        long age = SuxiaMotion.clock(level()) - tag.getLong(SuxiaEvents.LAST_HURT);
        return entity.getHealth() < entity.getMaxHealth() * 0.5F
                || tag.contains(SuxiaEvents.LAST_HURT) && age >= 0 && age < 200;
    }

    public void perceive(LivingEntity target) {
        if (!prey(target)) return;
        long now = SuxiaMotion.clock(level());
        if (isPlayingDead()) {
            setPlayingDead(false);
            ambushUntil = now + 60;
            setCrawling(!SuxiaMotion.canStand(this));
        }
        lastSense = now;
        lastTargetSense = now;
        sound = null;
        setTarget(target);
        setAggressive(true);
    }

    public void investigate(BlockPos pos) {
        lastSense = SuxiaMotion.clock(level());
        sound = pos.immutable();
        searchUntil = 0;
        setTarget(null);
        if (isPlayingDead()) { setPlayingDead(false); ambushUntil = lastSense + 60; }
    }

    @Override public void tick() {
        super.tick();
        motion.tick(isCrawling(), isPlayingDead());
        if (!(level() instanceof ServerLevel level) || !isAlive() || isNoAi()) return;
        VibrationSystem.Ticker.tick(level, vibrations, hearing);
        long now = SuxiaMotion.clock(level);
        entityData.set(CLIMB, horizontalCollision && isCrawling() && !isPlayingDead());
        if (tickCount % 5 == 0) {
            double contactRange = isPlayingDead() ? 3 : 1.5;
            level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(contactRange),
                    e -> prey(e) && distanceToSqr(e) <= contactRange * contactRange
                            && (isPlayingDead() || getBoundingBox().inflate(0.2).intersects(e.getBoundingBox())))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(this::perceive);
        }
        if (tickCount % 20 == 0) {
            double radius = isPlayingDead() ? 3 : 24;
            level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius),
                    e -> distanceToSqr(e) <= radius * radius && smells(e))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(this::perceive);
        }
        if (getTarget() != null && (!prey(getTarget()) || now - lastTargetSense >= 160)) {
            setTarget(null); getNavigation().stop();
        }
        setAggressive(getTarget() != null);
        if (!isPlayingDead() && getTarget() == null && sound == null && now - lastSense >= 600 && !isInWaterOrBubble()) {
            setPlayingDead(true);
        }
        if (isPlayingDead()) {
            setYRot(sleepingYaw); setYHeadRot(sleepingYaw); yBodyRot = sleepingYaw;
            setDeltaMovement(0, getDeltaMovement().y, 0);
        } else if (tickCount % 5 == 0) {
            setCrawling(!SuxiaMotion.canStand(this) || getTarget() != null && distanceToSqr(getTarget()) > 16
                    || sound != null && distanceToSqr(Vec3.atCenterOf(sound)) > 16);
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !level().isClientSide && prey(source.getEntity())) perceive((LivingEntity) source.getEntity());
        return result;
    }
    @Override public void push(Entity entity) {
        if (!level().isClientSide && prey(entity)) perceive((LivingEntity) entity);
        super.push(entity);
    }
    @Override public boolean doHurtTarget(Entity target) {
        if (!prey(target)) return false;
        float damage = SuxiaMotion.damage(this);
        if (ambushUntil > SuxiaMotion.clock(level())) { damage *= 1.5F; ambushUntil = 0; }
        boolean hit = target.hurt(damageSources().mobAttack(this), damage);
        if (hit) {
            double resistance = ((LivingEntity) target).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.4 * Math.max(0, 1 - resistance), 0));
            doEnchantDamageEffects(this, target);
        }
        return hit;
    }

    @Override public VibrationSystem.Data getVibrationData() { return vibrations; }
    @Override public VibrationSystem.User getVibrationUser() { return hearing; }
    @Override public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> consumer) {
        if (level() instanceof ServerLevel server) consumer.accept(listener, server);
    }

    private final class Hearing implements VibrationSystem.User {
        private final PositionSource source = new EntityPositionSource(SuxiaEntity.this, 0.5F);
        @Override public int getListenerRadius() { return 16; }
        @Override public PositionSource getPositionSource() { return source; }
        @Override public TagKey<GameEvent> getListenableEvents() { return GameEventTags.WARDEN_CAN_LISTEN; }
        @Override public boolean canReceiveVibration(ServerLevel level, BlockPos pos, GameEvent event, GameEvent.Context context) {
            Entity entity = context.sourceEntity();
            if (!isAlive() || isNoAi() || entity instanceof SuxiaEntity || entity instanceof SuxiaSummonEntity
                    || entity instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
            double radius = isPlayingDead() ? 3 : 16;
            return distanceToSqr(Vec3.atCenterOf(pos)) <= radius * radius
                    && !EraserAggro.soundBlocked(SuxiaEntity.this, Vec3.atCenterOf(pos));
        }
        @Override public void onReceiveVibration(ServerLevel level, BlockPos pos, GameEvent event,
                @Nullable Entity entity, @Nullable Entity projectileOwner, float distance) {
            if (!canReceiveVibration(level, pos, event, GameEvent.Context.of(entity))) return;
            // A thrown decoy exposes its impact position, not its silent thrower's current position.
            if (entity instanceof Projectile || event == GameEvent.PROJECTILE_LAND) investigate(pos);
            else if (prey(entity)) perceive((LivingEntity) entity);
            else investigate(pos);
        }
    }

    private final class InvestigateSound extends Goal {
        InvestigateSound() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return sound != null && getTarget() == null && !isPlayingDead(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            if (sound == null) return;
            long now = SuxiaMotion.clock(level());
            if (searchUntil == 0 && distanceToSqr(Vec3.atBottomCenterOf(sound)) > 2.25) {
                if (tickCount % 10 == 0 && !getNavigation().moveTo(sound.getX() + 0.5, sound.getY(), sound.getZ() + 0.5, 1)) {
                    searchUntil = now + 100;
                }
                if (now - lastSense > 200) searchUntil = now + 100;
            } else {
                getNavigation().stop();
                if (searchUntil == 0) searchUntil = now + 100;
                if (now >= searchUntil) { sound = null; searchUntil = 0; }
            }
        }
        @Override public void stop() { getNavigation().stop(); }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SuxiaCrawling", isCrawling()); tag.putBoolean("SuxiaPlayingDead", isPlayingDead());
        tag.putLong("SuxiaLastSense", lastSense); tag.putLong("SuxiaAmbushUntil", ambushUntil);
        tag.putFloat("SuxiaSleepingYaw", sleepingYaw);
        if (sound != null) tag.putLong("SuxiaSound", sound.asLong());
        VibrationSystem.Data.CODEC.encodeStart(NbtOps.INSTANCE, vibrations).result().ifPresent(v -> tag.put("listener", v));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCrawling(tag.getBoolean("SuxiaCrawling")); setPlayingDead(tag.getBoolean("SuxiaPlayingDead"));
        lastSense = tag.contains("SuxiaLastSense") ? tag.getLong("SuxiaLastSense") : SuxiaMotion.clock(level());
        ambushUntil = tag.getLong("SuxiaAmbushUntil"); sleepingYaw = tag.getFloat("SuxiaSleepingYaw");
        sound = tag.contains("SuxiaSound") ? BlockPos.of(tag.getLong("SuxiaSound")) : null;
        if (tag.contains("listener")) VibrationSystem.Data.CODEC.parse(NbtOps.INSTANCE, tag.get("listener"))
                .result().ifPresent(v -> vibrations = v);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        var result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND || reason == MobSpawnType.STRUCTURE) setPersistenceRequired();
        return result;
    }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof Enemy || super.isAlliedTo(other); }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected SoundEvent getAmbientSound() { return isPlayingDead() ? null : SoundEvents.DROWNED_AMBIENT; }
    @Override public void playAmbientSound() {
        if (!isPlayingDead()) playSound(SoundEvents.DROWNED_AMBIENT, 1F, random.nextFloat() < 0.3F ? 0.5F : 0.6F);
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.DROWNED_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.DROWNED_DEATH; }
    @Override public float getVoicePitch() { return 0.7F; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) {
        if (!isPlayingDead()) playSound(isCrawling() ? SoundEvents.SPIDER_STEP : state.getSoundType().getStepSound(),
                isCrawling() ? 0.15F : 0.1F, 0.8F);
    }
}
