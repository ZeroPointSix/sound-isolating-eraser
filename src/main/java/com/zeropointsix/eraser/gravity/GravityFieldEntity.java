package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;

public final class GravityFieldEntity extends Entity {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(ModMain.MOD_ID, "gravity_crush"));
    private static final EntityDataAccessor<BlockPos> CENTER = SynchedEntityData.defineId(
            GravityFieldEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(
            GravityFieldEntity.class, EntityDataSerializers.INT);
    private UUID owner;
    private long expiresAt;
    private int age;
    private float damage;
    private int slowness;

    public GravityFieldEntity(EntityType<? extends GravityFieldEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static GravityFieldEntity create(ServerLevel level, UUID owner, BlockPos center) {
        GravityFieldEntity field = new GravityFieldEntity(ModEntities.GRAVITY_FIELD.get(), level);
        field.owner = owner;
        field.entityData.set(CENTER, center.immutable());
        field.entityData.set(HEIGHT, GravityConfig.HEIGHT.get());
        field.setPos(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        field.expiresAt = level.getGameTime() + GravityConfig.DURATION.get();
        field.damage = GravityConfig.DAMAGE.get().floatValue();
        field.slowness = GravityConfig.SLOWNESS.get();
        return field;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(CENTER, BlockPos.ZERO);
        entityData.define(HEIGHT, 5);
    }

    public AABB fieldBounds() {
        return GravityGeometry.bounds(entityData.get(CENTER), entityData.get(HEIGHT));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return fieldBounds();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (level.getGameTime() > expiresAt) {
            discard();
            return;
        }
        age++;
        DamageSource source = new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE),
                this, owner == null ? null : level.getEntity(owner));
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, fieldBounds(),
                e -> e.isAlive() && !e.isSpectator())) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    10, slowness, false, false, true));
            if (age % 20 == 0 && damage > 0) GravityDamage.hurt(entity, source, damage);
        }
        if (level.getGameTime() >= expiresAt) discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putLong("Center", entityData.get(CENTER).asLong());
        tag.putInt("Height", entityData.get(HEIGHT));
        tag.putLong("ExpiresAt", expiresAt);
        tag.putInt("Age", age);
        tag.putFloat("Damage", damage);
        tag.putInt("Slowness", slowness);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        entityData.set(CENTER, BlockPos.of(tag.getLong("Center")));
        entityData.set(HEIGHT, Math.max(1, Math.min(31, tag.getInt("Height"))));
        expiresAt = tag.getLong("ExpiresAt");
        age = tag.getInt("Age");
        damage = Math.max(0, Math.min(100, tag.getFloat("Damage")));
        slowness = Math.max(2, Math.min(4, tag.getInt("Slowness")));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
