package com.zeropointsix.eraser.memory;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

/** Re-evaluates owner combat before ambient hostiles, including while already fighting. */
public final class SummonGuardGoal extends Goal {
    private final TamableAnimal summon;
    private final ToIntFunction<LivingEntity> assigned;
    private final int targetCapacity;
    private int untilScan;

    public SummonGuardGoal(TamableAnimal summon) { this(summon, target -> 0, Integer.MAX_VALUE); }
    public SummonGuardGoal(TamableAnimal summon, ToIntFunction<LivingEntity> assigned, int targetCapacity) {
        this.summon = summon;
        this.assigned = assigned;
        this.targetCapacity = targetCapacity;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override public boolean canUse() {
        var owner = summon.getOwner();
        return summon.isAlive() && !summon.isOrderedToSit() && owner != null && owner.isAlive()
                && !owner.isSpectator() && owner.level() == summon.level();
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { untilScan = 0; }
    @Override public void stop() { summon.setTarget(null); }
    @Override public void tick() {
        if (--untilScan <= 0) {
            untilScan = SummonCombatConfig.value(SummonCombatConfig.SCAN_TICKS);
            chooseTarget();
        }
    }

    private boolean eligible(LivingEntity target, LivingEntity owner) {
        return SummonTargets.allowed(summon, target, owner) && summon.canAttack(target)
                && assigned.applyAsInt(target) < targetCapacity;
    }
    private boolean recent(LivingEntity owner, int timestamp) {
        int age = owner.tickCount - timestamp;
        return age >= 0 && age < 200;
    }

    public void chooseTarget() {
        if (!canUse()) { summon.setTarget(null); return; }
        var owner = summon.getOwner();
        var attacker = owner.getLastHurtByMob();
        if (recent(owner, owner.getLastHurtByMobTimestamp()) && eligible(attacker, owner)) {
            summon.setTarget(attacker); return;
        }
        var commanded = owner.getLastHurtMob();
        if (recent(owner, owner.getLastHurtMobTimestamp()) && eligible(commanded, owner)) {
            summon.setTarget(commanded); return;
        }
        double radius = SummonCombatConfig.guardRadius();
        var nearby = summon.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(radius),
                mob -> mob.distanceToSqr(owner) <= radius * radius && eligible(mob, owner));
        var threats = nearby.stream().filter(mob -> mob.getTarget() == owner).toList();
        var hostiles = nearby.stream().filter(SummonTargets::hostile).toList();
        LivingEntity target = pick(threats);
        if (target == null) target = pick(hostiles);
        // Preserve an explicit owner engagement after its priority window, never an ambient chase outside the radius.
        var current = summon.getTarget();
        if (target == null && current != null && (current == attacker || current == commanded)
                && eligible(current, owner) && current.distanceToSqr(owner) <= 32 * 32) target = current;
        summon.setTarget(target);
    }

    private LivingEntity pick(List<? extends LivingEntity> candidates) {
        var current = summon.getTarget();
        if (candidates.contains(current)) return current;
        return candidates.stream().min(Comparator.<LivingEntity>comparingInt(assigned)
                .thenComparingDouble(summon::distanceToSqr).thenComparingInt(LivingEntity::getId)).orElse(null);
    }
}
