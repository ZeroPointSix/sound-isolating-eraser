package com.zeropointsix.eraser.yuji.city;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class RecliningBuddha extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(RecliningBuddha.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public RecliningBuddha(EntityType<? extends RecliningBuddha> type, Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    @Override protected void defineSynchedData() { entityData.define(AGE, -1); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(AGE, tag.getInt("SceneAge")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("SceneAge", entityData.get(AGE)); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void tick() {
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            var data = RainCityData.get(server);
            int age = data.ringing() ? (int) Math.min(92, Math.max(0, server.getGameTime() - data.bellAt)) : -1;
            entityData.set(AGE, age);
            double progress = Math.max(0, Math.min(1, (age - 32) / 60.));
            setPos(0, 123 - 96 * progress * progress, 0);
        }
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "scene", 0, state -> {
            int age = entityData.get(AGE);
            String name = age < 0 ? "idle" : age < 32 ? "crack" : age < 92 ? "fall" : "rest";
            String animation = "animation.reclining_buddha." + name;
            return state.setAndContinue(name.equals("idle") || name.equals("rest") ? RawAnimation.begin().thenLoop(animation)
                    : RawAnimation.begin().thenPlayAndHold(animation));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
