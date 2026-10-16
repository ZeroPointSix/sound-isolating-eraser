package com.zeropointsix.eraser.rednails;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

/** Shared physical behavior for the hostile and tame entities. */
public final class WaterGhostBody {
    public static final EntityDimensions SWIMMING = EntityDimensions.scalable(1.2F, 1.2F);
    public static final double SWIM_SPEED = 0.35;
    /** Design §声音: ambient 0.6, hurt/death 0.7, grab start 0.5. */
    public static final float AMBIENT_PITCH = 0.6F;
    public static final float HURT_DEATH_PITCH = 0.7F;
    public static final float GRAB_PITCH = 0.5F;
    public final PathfinderMob mob;
    public final WaterGhostGrab grab;
    private final EntityDataAccessor<Boolean> swimData, grabData;
    private float swim, swimOld, grabbing, grabbingOld;

    public WaterGhostBody(PathfinderMob mob, EntityDataAccessor<Boolean> swimData,
                          EntityDataAccessor<Boolean> grabData) {
        this.mob = mob;
        this.swimData = swimData;
        this.grabData = grabData;
        grab = new WaterGhostGrab(this);
        mob.setPathfindingMalus(BlockPathTypes.WATER, 0);
    }
    public static AttributeSupplier.Builder attributes(double followRange) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.ARMOR, 2).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.KNOCKBACK_RESISTANCE, 0)
                .add(Attributes.FOLLOW_RANGE, followRange);
    }
    public boolean swimming() { return mob.getEntityData().get(swimData); }
    public void swimming(boolean value) { mob.getEntityData().set(swimData, value); }
    public boolean grabbing() { return mob.getEntityData().get(grabData); }
    public void grabbing(boolean value) { mob.getEntityData().set(grabData, value); }
    public float swim(float pt) { return Mth.lerp(pt, swimOld, swim); }
    public float grab(float pt) { return Mth.lerp(pt, grabbingOld, grabbing); }
    public void stopMoving() {
        mob.getNavigation().stop();
        if (mob.getMoveControl() instanceof Movement movement) movement.halt();
        mob.setDeltaMovement(Vec3.ZERO);
    }

    public void tick() {
        swimOld = swim; grabbingOld = grabbing;
        swim = Mth.approach(swim, swimming() ? 1 : 0, 0.1F);
        grabbing = Mth.approach(grabbing, grabbing() ? 1 : 0, 0.25F);
        if (mob.level().isClientSide || !mob.isAlive()) return;
        if (mob.tickCount % 100 == 0 || !mob.hasEffect(MobEffects.REGENERATION)) {
            mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false));
        }
        boolean deep = mob.isInWater() && (mob.getFluidHeight(FluidTags.WATER) > 1
                || mob.isEyeInFluid(FluidTags.WATER));
        if (deep != swimming() && (deep || mob.level().noCollision(mob,
                mob.getType().getDimensions().makeBoundingBox(mob.position())))) swimming(deep);
        mob.setAggressive(mob.getTarget() != null);
    }
    public boolean travel() {
        if (!mob.isInWater()) return false;
        if (mob.isEffectiveAi()) mob.move(MoverType.SELF, mob.getDeltaMovement());
        return true;
    }
    public void dissolve() {
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY() + 0.6, mob.getZ(), 40, 0.6, 0.7, 0.6, 0.1);
            level.sendParticles(ParticleTypes.FALLING_WATER, mob.getX(), mob.getY() + 0.7, mob.getZ(), 20, 0.6, 0.6, 0.6, 0.02);
            mob.playSound(SoundEvents.GENERIC_SPLASH, 1.2F, 0.7F);
        }
    }
    public static boolean waterAt(PathfinderMob mob, BlockPos pos) {
        return mob.level().hasChunkAt(pos) && mob.level().getFluidState(pos).is(FluidTags.WATER);
    }
    public static boolean place(PathfinderMob mob, Vec3 pos, boolean water) {
        var level = mob.level();
        var block = BlockPos.containing(pos);
        var box = (water ? SWIMMING : mob.getType().getDimensions()).makeBoundingBox(pos);
        if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(box)
                || !level.noCollision(mob, box) || level.containsAnyLiquid(box) && !water
                || water && !waterAt(mob, block)
                || !water && !level.getBlockState(block.below()).isCollisionShapeFullBlock(level, block.below())) return false;
        ((WaterGhostPoseSource) mob).ghostBody().swimming(water);
        ((WaterGhostPoseSource) mob).ghostBody().stopMoving();
        mob.moveTo(pos.x, pos.y, pos.z, mob.getYRot(), 0);
        mob.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    public static final class Movement extends MoveControl {
        private final WaterGhostBody body;
        public Movement(WaterGhostBody body) { super(body.mob); this.body = body; }
        public void halt() { operation = Operation.WAIT; mob.setZza(0); }
        @Override public void tick() {
            if (body.grabbing()) { mob.setZza(0); return; }
            if (!mob.isInWater()) { super.tick(); return; }
            mob.setZza(0);
            if (operation == Operation.MOVE_TO) {
                Vec3 to = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
                double distance = to.length();
                if (distance < 0.15) {
                    operation = Operation.WAIT;
                    mob.setDeltaMovement(Vec3.ZERO);
                    return;
                }
                Vec3 direction = to.scale(1 / distance);
                float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 30));
                mob.yBodyRot = mob.getYRot();
                double speed = Math.min(distance, SWIM_SPEED * Mth.clamp(speedModifier, 0, 1));
                mob.setDeltaMovement(mob.getDeltaMovement().lerp(direction.scale(speed), 0.25));
            } else {
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.8));
            }
        }
    }
}
