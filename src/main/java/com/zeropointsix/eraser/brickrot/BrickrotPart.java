package com.zeropointsix.eraser.brickrot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class BrickrotPart extends PartEntity<BrickrotWallEntity> implements GeoEntity {
    private final int index;
    private final EntityDimensions size;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BrickrotPart(BrickrotWallEntity parent, int index) {
        super(parent);
        this.index = index;
        float diameter = index == 9 ? 1.4F : index == 8 ? 2 : 2.5F;
        size = EntityDimensions.scalable(diameter, diameter);
        refreshDimensions();
    }

    public int index() { return index; }
    public String modelName() {
        if (index == 1) return getParent().phaseTwo() ? "brickrot_neck_breached" : "brickrot_neck";
        if (index < 4) return "brickrot_segment_grey";
        return index < 8 ? "brickrot_segment_red" : "brickrot_segment_tail";
    }

    @Override public EntityDimensions getDimensions(Pose pose) {
        return size == null ? EntityDimensions.scalable(2.5F, 2.5F) : size;
    }
    @Override public boolean isPickable() { return getParent().isPickable(); }
    @Override public boolean is(Entity other) { return this == other || getParent() == other; }
    @Override public boolean hurt(DamageSource source, float amount) {
        return getParent().hurtPart(index, source, amount);
    }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 2, state -> {
            BrickrotWallEntity head = getParent();
            String name = !head.isAlive() ? "segment_death"
                    : index >= 4 && index <= 7 && head.action() == BrickrotWallEntity.Action.SWEEP
                    ? "red_sweep" : index >= 8 ? "tail_pulse" : "segment_crawl";
            state.setControllerSpeed(head.action() == BrickrotWallEntity.Action.CHARGE ? 2.5F
                    : head.action() == BrickrotWallEntity.Action.IDLE ? 0.35F : 1F);
            return state.setAndContinue(!head.isAlive()
                    ? RawAnimation.begin().thenPlayAndHold("animation.brickrot." + name)
                    : name.equals("red_sweep") ? RawAnimation.begin().thenPlay("animation.brickrot." + name)
                    : RawAnimation.begin().thenLoop("animation.brickrot." + name));
        }));
    }
}
