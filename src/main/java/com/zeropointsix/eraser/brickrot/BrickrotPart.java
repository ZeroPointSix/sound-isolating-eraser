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
        controllers.add(new AnimationController<>(this, "body", 0, state -> {
            BrickrotWallEntity head = getParent();
            if (!head.isAlive()) {
                state.setControllerSpeed(1);
                return state.setAndContinue(RawAnimation.begin().thenWait(index * 2)
                        .thenPlayAndHold("animation.brickrot.segment_death"));
            }
            if (index >= 4 && index <= 7 && head.action() == BrickrotWallEntity.Action.SWEEP) {
                state.setControllerSpeed(1);
                RawAnimation sweep = RawAnimation.begin();
                if (index > 4) sweep.thenWait(index - 4);
                return state.setAndContinue(sweep.thenPlay("animation.brickrot.red_sweep"));
            }
            boolean moving = head.position().distanceToSqr(head.xo, head.yo, head.zo) > 1.0E-6;
            state.setControllerSpeed(head.action() == BrickrotWallEntity.Action.CHARGE ? 2.5F
                    : moving ? 1F : 0.35F);
            // A one-time lead-in offsets the unchanged supplied looping animation.
            return state.setAndContinue(RawAnimation.begin().thenWait(Math.round(index * 2.6F))
                    .thenLoop("animation.brickrot." + (index >= 8 ? "tail_pulse" : "segment_crawl")));
        }));
    }
}
