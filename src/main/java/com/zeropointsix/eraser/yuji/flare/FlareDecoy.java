package com.zeropointsix.eraser.yuji.flare;

import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class FlareDecoy extends PathfinderMob {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(FlareDecoy.class, EntityDataSerializers.FLOAT);
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));
    private long expiresAt = -1;
    public FlareDecoy(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); setPersistenceRequired(); }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(RADIUS, 24F); }
    public double tauntRadius() { return entityData.get(RADIUS); }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.MOVEMENT_SPEED, 0);
    }
    public void ignite() {
        entityData.set(RADIUS, FlareConfig.TAUNT_RADIUS.get().floatValue());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(FlareConfig.RED_HEALTH.get());
        getAttribute(Attributes.ARMOR).setBaseValue(FlareConfig.RED_ARMOR.get());
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(FlareConfig.RED_KNOCKBACK.get());
        setHealth(getMaxHealth()); expiresAt = level().getGameTime() + FlareConfig.RED_LIFETIME.get();
    }
    @Override protected void registerGoals() { }
    @Override public boolean isPushable() { return false; }
    @Override public void travel(Vec3 movement) {
        setDeltaMovement(0, getDeltaMovement().y, 0); super.travel(Vec3.ZERO);
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { FlareEffects.decoy(level(), position(), tickCount, tauntRadius()); return; }
        if (expiresAt < 0) ignite();
        if (level().getGameTime() >= expiresAt || !isAlive()) { discard(); return; }
        if (tickCount % FlareConfig.TAUNT_INTERVAL.get() == 0) taunt();
        if (tickCount % 40 == 0) playSound(SoundEvents.FIRE_EXTINGUISH, .2F, .65F);
    }
    public void taunt() {
        double radius = FlareConfig.TAUNT_RADIUS.get();
        entityData.set(RADIUS, (float) radius);
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(radius), m -> m instanceof Enemy && m.isAlive())) {
            if (distanceToSqr(mob) > radius * radius || mob.getType().is(BOSSES)) continue;
            if (mob instanceof YujiMonster yuji && (yuji.kind() == YujiKind.CHENNU || yuji.kind() == YujiKind.SHIFOTOU)) continue;
            mob.setTarget(this);
        }
    }
    @Override public void die(DamageSource source) { super.die(source); discard(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putLong("FlareExpires", expiresAt); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); expiresAt = tag.contains("FlareExpires") ? tag.getLong("FlareExpires") : -1; }
}
