package com.zeropointsix.eraser.rednails;

import com.zeropointsix.eraser.eraser.EraserNetwork;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.network.PacketDistributor;

public final class WaterGhostGrab extends Goal {
    private static final String HOLDER = "sound_isolating_eraser_grabbed_by";
    private static final String UNTIL = "sound_isolating_eraser_grabbed_until";
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));
    private final WaterGhostBody body;
    private final PathfinderMob mob;
    private LivingEntity target;
    private int ticks, duration;
    private float damage;
    private double startY;
    private long cooldown;
    private Vec3 shoreWater;
    private boolean enteredWater;

    public WaterGhostGrab(WaterGhostBody body) {
        this.body = body; mob = body.mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }
    public long cooldownUntil() { return cooldown; }
    public void cooldownUntil(long value) { cooldown = value; }
    public LivingEntity held() { return target; }
    public static boolean grabbable(LivingEntity target) {
        if (target.getType().is(BOSSES) || target.getBbWidth() > 1.5 || target.getBbHeight() > 2.5
                || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof Player p && p.isCreative()) return false;
        return target.getPersistentData().getLong(UNTIL) <= target.level().getGameTime();
    }
    @Override public boolean canUse() {
        var prey = mob.getTarget();
        return allowed(prey) && (!(mob instanceof WaterGhostEntity hostile) || hostile.mayPursue())
                && cooldown <= mob.level().getGameTime() && mob.distanceToSqr(prey) <= 2.2 * 2.2
                && mob.hasLineOfSight(prey) && grabbable(prey);
    }
    private boolean allowed(LivingEntity prey) {
        if (prey == null || prey.level() != mob.level() || !prey.isAlive() || prey.isSpectator()) return false;
        if (mob instanceof WaterGhostSummonEntity summon) return summon.wantsToAttack(prey, summon.getOwner());
        return WaterGhostEntity.prey(prey);
    }
    @Override public boolean canContinueToUse() {
        return allowed(target) && mob.isAlive()
                && mob.getTarget() == target && ticks < duration && damage < 8
                && mob.distanceToSqr(target) <= 16;
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        target = mob.getTarget();
        if (!allowed(target) || !grabbable(target)) { target = null; return; }
        ticks = 0; damage = 0; startY = mob.getY();
        duration = body.swimming() ? 100 : 60;
        enteredWater = body.swimming();
        shoreWater = findShoreWater();
        target.stopRiding();
        body.stopMoving();
        body.grabbing(true);
        lock();
        mob.playSound(SoundEvents.DROWNED_AMBIENT, 1.5F, WaterGhostBody.GRAB_PITCH);
        mob.playSound(SoundEvents.GENERIC_SPLASH, 1, 0.7F);
    }
    private Vec3 findShoreWater() {
        if (!(mob instanceof WaterGhostSummonEntity summon)) return null;
        var owner = summon.getOwner();
        if (owner == null) return null;
        boolean ownerNearWater = owner.isInWater();
        for (var pos : BlockPos.withinManhattan(owner.blockPosition(), 2, 2, 2)) {
            if (WaterGhostBody.waterAt(mob, pos)) { ownerNearWater = true; break; }
        }
        if (!ownerNearWater) return null;
        Vec3 edge = null;
        // Look down from the bank into a pit; keep searching while already swimming so
        // flowing shallows over stone still walk into a column with water below.
        for (var pos : BlockPos.withinManhattan(mob.blockPosition(), 6, 4, 6)) {
            if (!WaterGhostBody.waterAt(mob, pos) || !WaterGhostBody.waterAt(mob, pos.above())) continue;
            Vec3 at = Vec3.atBottomCenterOf(pos);
            if (!mob.level().noCollision(mob, WaterGhostBody.SWIMMING.makeBoundingBox(at))) continue;
            // Reject water buried under the bank. Same-level shore water must still
            // be walkable; do not require sinking through the floor we are standing on.
            if (at.y + 0.5 < mob.getY()) {
                var descent = WaterGhostBody.SWIMMING.makeBoundingBox(new Vec3(at.x, mob.getY(), at.z))
                        .expandTowards(0, at.y - mob.getY(), 0);
                if (!mob.level().noCollision(mob, descent)) continue;
            }
            if (WaterGhostBody.waterAt(mob, pos.below())) return at;
            if (edge == null) edge = at;
        }
        return edge;
    }
    private void lock() {
        target.getPersistentData().putUUID(HOLDER, mob.getUUID());
        target.getPersistentData().putLong(UNTIL, mob.level().getGameTime() + 5);
    }
    private boolean blocked(Vec3 hold) {
        var holdBox = target.getDimensions(target.getPose()).makeBoundingBox(hold);
        // A boat above the destination can still support a dismounted player. Keep
        // pulling beside it until clear instead of aiming straight through its hull.
        var descentColumn = holdBox.expandTowards(0, Math.max(0, target.getY() - hold.y), 0);
        return !mob.level().getWorldBorder().isWithinBounds(holdBox)
                || mob.level().getBlockCollisions(target, holdBox).iterator().hasNext()
                || (target instanceof ServerPlayer && !mob.level().getEntities(target, descentColumn,
                        entity -> entity != mob && target.canCollideWith(entity)).isEmpty());
    }
    private boolean clearForDescent(AABB box) {
        for (var collision : mob.level().getBlockCollisions(mob, box)) {
            if (!collision.isEmpty()) return false;
        }
        // Entity collision queries inflate the box. A boat touching only its side
        // must not prevent sinking, but any actual overlapping shape still does.
        for (var collision : mob.level().getEntityCollisions(mob, box)) {
            for (var collisionBox : collision.toAabbs()) {
                if (box.intersects(collisionBox)) return false;
            }
        }
        var border = mob.level().getWorldBorder();
        return !border.isInsideCloseToBorder(mob, box)
                || !Shapes.joinIsNotEmpty(border.getCollisionShape(), Shapes.create(box), BooleanOp.AND);
    }
    private boolean targetCanDescendTo(double y) {
        if (!(target instanceof ServerPlayer) || target.getY() <= y) return true;
        var column = target.getBoundingBox().expandTowards(0, y - target.getY(), 0);
        return mob.level().getEntities(target, column,
                entity -> entity != mob && target.canCollideWith(entity)).isEmpty();
    }
    private void syncAnchor(ServerPlayer player, Vec3 hold, int remainingTicks) {
        if (player.connection != null) {
            EraserNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EraserNetwork.GrabAnchor(mob.getUUID(), mob.level().dimension().location(), hold, remainingTicks));
        }
    }
    @Override public void tick() {
        if (target == null) return;
        if (!canContinueToUse()) { stop(); return; }
        lock();
        if (shoreWater == null || !clearForDescent(mob.getBoundingBox().move(0, -0.08, 0))) {
            Vec3 found = findShoreWater();
            if (found != null) shoreWater = found;
        }
        Vec3 shoreStep = Vec3.ZERO;
        if (shoreWater != null) {
            Vec3 toward = shoreWater.subtract(mob.position()).multiply(1, 0, 1);
            if (toward.lengthSqr() > 0.04) {
                Vec3 step = toward.normalize().scale(0.12);
                if (mob.level().noCollision(mob, mob.getBoundingBox().move(step))) {
                    mob.setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90);
                    mob.yBodyRot = mob.getYRot();
                    shoreStep = step;
                }
            }
        }
        Vec3 forward = Vec3.directionFromRotation(0, mob.getYRot());
        Vec3 hold = mob.position().add(forward.scale(1.1)).add(0, body.swimming() ? 0 : 0.6, 0);
        // A dismounted boat must not cancel the grab. Terrain must not either: lift or skip
        // the relocation so a seafloor or pool lip cannot drop a live hold.
        if (blocked(hold)) hold = mob.position().add(0, body.swimming() ? 0 : 0.6, 0);
        if (!blocked(hold)) {
            if (target instanceof ServerPlayer player) {
                syncAnchor(player, hold, duration - ticks);
                Vec3 pull = hold.subtract(player.position()).scale(0.5);
                player.setDeltaMovement(pull);
                if (player.connection != null) {
                    // Snapshot the pull now: a later movement packet can replace delta
                    // with jump velocity before the tracker consumes hurtMarked.
                    player.serverLevel().getChunkSource().broadcastAndSend(player,
                            new ClientboundSetEntityMotionPacket(player.getId(), pull));
                    player.hurtMarked = false;
                } else player.hurtMarked = true;
            } else {
                target.setPos(hold);
                target.setDeltaMovement(Vec3.ZERO);
            }
            target.fallDistance = 0;
        } else if (target instanceof ServerPlayer player) {
            syncAnchor(player, Vec3.ZERO, 0);
        }
        if (body.swimming()) {
            if (!enteredWater) {
                enteredWater = true;
                duration = ticks + 100;
                startY = mob.getY();
            }
            double down = Math.max(-0.08, startY - 6 - mob.getY());
            Vec3 swim = new Vec3(shoreStep.x, 0, shoreStep.z);
            if (mob.getY() > startY - 6 && !mob.verticalCollisionBelow
                    // Pull the player off a supporting hull before sinking away from them.
                    && targetCanDescendTo(hold.y + down)
                    && clearForDescent(mob.getBoundingBox().move(shoreStep.x, down, shoreStep.z))
                    && !blocked(hold.add(shoreStep.x, down, shoreStep.z))) {
                swim = new Vec3(shoreStep.x, down, shoreStep.z);
            } else if (shoreStep.lengthSqr() > 0
                    && mob.level().noCollision(mob, mob.getBoundingBox().move(shoreStep))) {
                swim = shoreStep;
            }
            mob.setDeltaMovement(swim);
            if (target.isEyeInFluid(FluidTags.WATER)
                    && !target.canBreatheUnderwater()
                    && !target.hasEffect(MobEffects.WATER_BREATHING) && !target.hasEffect(MobEffects.CONDUIT_POWER)) {
                int air = target.getAirSupply() - 3;
                // Vanilla checks exactly -20; skipping past it would accidentally prevent drowning.
                if (air <= -20) { target.setAirSupply(0); target.hurt(target.damageSources().drown(), 2); }
                else target.setAirSupply(air);
            }
        } else if (shoreStep.lengthSqr() > 0) {
            mob.setDeltaMovement(shoreStep.x, mob.getDeltaMovement().y, shoreStep.z);
        }
        if (++ticks % 20 == 0) {
            target.hurt(mob.damageSources().mobAttack(mob), 2);
            if (mob instanceof WaterGhostEntity hostile) hostile.contact();
        }
    }
    public void damage(float amount) {
        if (target == null) return;
        damage += Math.max(0, amount);
        if (damage >= 8) {
            Vec3 forward = Vec3.directionFromRotation(0, mob.getYRot());
            stop();
            mob.knockback(0.6, forward.x, forward.z);
        }
    }
    @Override public void stop() {
        if (target == null) return;
        if (target instanceof ServerPlayer player) syncAnchor(player, Vec3.ZERO, 0);
        var tag = target.getPersistentData();
        if (tag.hasUUID(HOLDER) && tag.getUUID(HOLDER).equals(mob.getUUID())) { tag.remove(HOLDER); tag.remove(UNTIL); }
        target = null;
        shoreWater = null;
        body.grabbing(false);
        mob.setDeltaMovement(Vec3.ZERO);
        cooldown = mob.level().getGameTime() + 160;
    }
}
