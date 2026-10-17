package com.zeropointsix.eraser.memory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/** One friendly-fire policy for Suxia and the red-nail guardians. */
public final class SummonTargets {
    public static boolean allowed(Mob summon, LivingEntity target, LivingEntity owner) {
        if (target == null || owner == null || target == owner || target == summon || !target.isAlive()
                || target.isSpectator() || target.level() != owner.level()
                || target instanceof AbstractVillager || target instanceof IronGolem
                || summon.isAlliedTo(target)) return false;
        if (target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID())) return false;
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && summon.getServer() != null
                    && summon.getServer().isPvpAllowed() && owner instanceof Player p && p.canHarmPlayer(player);
        }
        return true;
    }
    public static boolean hostile(LivingEntity target) { return target instanceof Enemy; }
    private SummonTargets() { }
}
