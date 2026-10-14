package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public final class GravityTargets {
    public static final TagKey<EntityType<?>> HEAVY = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation(ModMain.MOD_ID, "gravity_heavy"));

    public static boolean isAlly(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isSpectator()
                && (entity instanceof Player
                    || entity instanceof TamableAnimal pet && pet.isTame()
                    || entity instanceof AbstractHorse horse && horse.isTamed());
    }

    public static boolean isHostile(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !isAlly(entity)
                && (entity instanceof Enemy || entity instanceof Mob mob && isAlly(mob.getTarget()));
    }

    private GravityTargets() { }
}
