package com.zeropointsix.eraser.yuji;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class BoneSpike extends ThrowableProjectile implements GeoEntity {
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(BoneSpike.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int stuckTicks;
    public BoneSpike(EntityType<? extends BoneSpike> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() { entityData.define(STUCK, false); }
    public boolean stuck() { return entityData.get(STUCK); }
    @Override protected float getGravity() { return .015F; }
    @Override public void tick() {
        if (stuck()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && ++stuckTicks >= 200) discard();
            if (level().isClientSide && tickCount % 4 == 0)
                level().addParticle(particle("bone_shard"), getX(), getY(), getZ(), 0, .02, 0);
            return;
        }
        super.tick();
        if (!level().isClientSide && tickCount > 200) discard();
        if (level().isClientSide) {
            level().addParticle(particle("bone_shard"), getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
            if (tickCount % 2 == 0) level().addParticle(particle("karma_wisp"), getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
        }
    }
    private static SimpleParticleType particle(String name) { return YujiContent.PARTICLE.get(name).get(); }
    private void burst(double count) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(particle("bone_shard"), getX(), getY(), getZ(), (int) count * 2, .3, .3, .3, .08);
            server.sendParticles(particle("dust_ring"), getX(), getY(), getZ(), (int) count, .4, .1, .4, .05);
        }
    }
    @Override protected boolean canHitEntity(Entity entity) { return entity != getOwner() && super.canHitEntity(entity); }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide) {
            hit.getEntity().hurt(damageSources().thrown(this, getOwner()), 3);
            burst(6); discard();
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) {
            // Embed a third of the spike into the surface so the planted spike reads at a glance.
            Vec3 direction = getDeltaMovement().lengthSqr() > .0001 ? getDeltaMovement().normalize().scale(.3) : Vec3.ZERO;
            setPos(hit.getLocation().add(direction));
            setDeltaMovement(Vec3.ZERO); entityData.set(STUCK, true); stuckTicks = 0; setNoGravity(true);
            burst(8);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, .8F, 1.2F);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putBoolean("Stuck", stuck()); tag.putInt("StuckTicks", stuckTicks);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); entityData.set(STUCK, tag.getBoolean("Stuck")); stuckTicks = Math.max(0, tag.getInt("StuckTicks"));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 0, state -> stuck()
                ? software.bernie.geckolib.core.object.PlayState.STOP : state.setAndContinue(RawAnimation.begin().thenLoop("animation.bone_spike.fly"))));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
