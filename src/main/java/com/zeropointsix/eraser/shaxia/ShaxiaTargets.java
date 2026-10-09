package com.zeropointsix.eraser.shaxia;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

public final class ShaxiaTargets {
    public static final TagKey<EntityType<?>> HOSTILE = tag("shaxiadao_hostile_targets");
    public static final TagKey<EntityType<?>> CONDITIONAL = tag("shaxiadao_conditional_targets");
    public static final TagKey<EntityType<?>> EXCLUDED = tag("shaxiadao_excluded_targets");

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(ModMain.MOD_ID, name));
    }

    public static boolean eligible(LivingEntity target) {
        if (!(target instanceof Mob mob) || target instanceof Player || target instanceof ArmorStand
                || target instanceof Animal || target instanceof AbstractVillager || !target.isAlive()) return false;
        EntityType<?> type = target.getType();
        String id = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type));
        if (type.is(EXCLUDED) || (ShaxiaConfig.SPEC.isLoaded() && ShaxiaConfig.EXCLUDE.get().contains(id))) return false;
        if (type.is(CONDITIONAL) || target instanceof NeutralMob) {
            return !ShaxiaConfig.flag(ShaxiaConfig.REQUIRE_PLAYER_INTENT, true) || playerIntent(mob);
        }
        return type.is(HOSTILE) || (ShaxiaConfig.SPEC.isLoaded() && ShaxiaConfig.INCLUDE.get().contains(id))
                || (ShaxiaConfig.flag(ShaxiaConfig.ENEMY_FALLBACK, true) && target instanceof Enemy);
    }

    private static boolean playerIntent(Mob mob) {
        if (mob.getTarget() instanceof Player player && player.isAlive() && !player.isSpectator()) return true;
        if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) instanceof Player player
                && player.isAlive() && !player.isSpectator()) return true;
        if (mob instanceof NeutralMob neutral && neutral.getRemainingPersistentAngerTime() > 0
                && neutral.getPersistentAngerTarget() != null) {
            Player player = mob.level().getPlayerByUUID(neutral.getPersistentAngerTarget());
            return player != null && player.isAlive() && !player.isSpectator();
        }
        return false;
    }

    private ShaxiaTargets() {}
}
