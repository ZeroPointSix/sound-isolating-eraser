package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class GravityFieldEntity extends Entity {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(ModMain.MOD_ID, "gravity_crush"));
    private static final EntityDataAccessor<Integer> RADIUS = SynchedEntityData.defineId(
            GravityFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BELOW = SynchedEntityData.defineId(
            GravityFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ABOVE = SynchedEntityData.defineId(
            GravityFieldEntity.class, EntityDataSerializers.INT);
    private UUID owner;
    private long expiresAt;
    private long lastTick = Long.MIN_VALUE;
    private int age;

    public GravityFieldEntity(EntityType<? extends GravityFieldEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static GravityFieldEntity create(ServerLevel level, UUID owner, BlockPos center) {
        return create(level, owner, Vec3.atBottomCenterOf(center));
    }

    public static GravityFieldEntity create(ServerLevel level, UUID owner, Vec3 center) {
        GravityFieldEntity field = new GravityFieldEntity(ModEntities.GRAVITY_FIELD.get(), level);
        field.owner = owner;
        field.entityData.set(RADIUS, GravityConfig.RADIUS.get());
        field.entityData.set(BELOW, GravityConfig.BELOW.get());
        field.entityData.set(ABOVE, GravityConfig.ABOVE.get());
        field.setPos(center);
        field.expiresAt = level.getGameTime() + GravityConfig.DURATION.get();
        return field;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 16);
        entityData.define(BELOW, 8);
        entityData.define(ABOVE, 16);
    }

    public int radius() { return entityData.get(RADIUS); }

    public AABB fieldBounds() {
        return GravityGeometry.bounds(level(), position(), radius(), entityData.get(BELOW), entityData.get(ABOVE));
    }

    public boolean contains(Vec3 point) {
        return GravityGeometry.contains(position(), radius(), fieldBounds(), point);
    }

    @Override
    public AABB getBoundingBoxForCulling() { return fieldBounds(); }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        long now = level.getGameTime();
        AABB bounds = fieldBounds();
        // A skipped chunk tick or reload is an interruption, not a natural expiry.
        if (now > expiresAt || lastTick != Long.MIN_VALUE && now != lastTick + 1
                || !GravityGeometry.chunksLoaded(level, position(), radius(), bounds)) {
            discard();
            return;
        }
        lastTick = now;
        age++;
        DamageSource source = new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE),
                this, owner == null ? null : level.getEntity(owner));
        var targets = level.getEntitiesOfClass(LivingEntity.class, bounds,
                e -> e.isAlive() && !e.isSpectator()
                        && GravityGeometry.contains(position(), radius(), bounds, e.position()));
        for (LivingEntity entity : targets) {
            if (GravityTargets.isAlly(entity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                        GravityConfig.ALLY_LINGER.get(), 2, false, false, true));
                entity.addEffect(new MobEffectInstance(MobEffects.JUMP,
                        GravityConfig.ALLY_LINGER.get(), 2, false, false, true));
            } else if (GravityTargets.isHostile(entity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        10, GravityConfig.SLOWNESS.get(), false, false, true));
                if (!entity.getType().is(GravityTargets.HEAVY)) hover(level, entity, bounds);
                if (age % 5 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + entity.getBbHeight() + 0.4,
                            entity.getZ(), 0, 0, -0.08, 0, 1);
                }
                if (age % 20 == 0) GravityDamage.hurt(entity, source, GravityDamage.curve(entity.getMaxHealth()));
            }
        }
        if (now >= expiresAt) {
            for (LivingEntity entity : targets) {
                if (!GravityTargets.isHostile(entity)) continue;
                if (!entity.getType().is(GravityTargets.HEAVY)) {
                    Vec3 motion = entity.getDeltaMovement();
                    entity.setDeltaMovement(motion.x, -2.5, motion.z);
                    entity.hurtMarked = true;
                    entity.hasImpulse = true;
                    GravitySlamEffects.watch(entity);
                } else if (entity.onGround()) {
                    GravitySlamEffects.land(entity);
                }
                GravityDamage.hurt(entity, source,
                        GravityDamage.curve(entity.getMaxHealth()) * GravityConfig.SLAM_MULTIPLIER.get().floatValue());
            }
            discard();
        }
    }

    private void hover(ServerLevel level, LivingEntity entity, AABB bounds) {
        if (GravityConfig.HOVER_HEIGHT.get() <= 0 || GravitySlamEffects.isFalling(entity)) return;
        Vec3 feet = entity.position();
        var ground = level.clip(new ClipContext(feet.add(0, 0.1, 0),
                new Vec3(feet.x, Math.max(level.getMinBuildHeight(), bounds.minY - 1), feet.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (ground.getType() == HitResult.Type.MISS) return;
        double target = ground.getLocation().y + GravityConfig.HOVER_HEIGHT.get();
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, Mth.clamp((target - feet.y) * 0.3, -0.25, 0.22), motion.z);
        entity.fallDistance = 0;
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    // Fields intentionally disappear on unload; reloading must never cause a delayed slam.
    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { expiresAt = Long.MIN_VALUE; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
