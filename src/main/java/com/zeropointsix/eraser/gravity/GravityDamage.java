package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class GravityDamage {
    private static final ThreadLocal<LivingEntity> VICTIM = new ThreadLocal<>();

    public static boolean hurt(LivingEntity entity, DamageSource source, float amount) {
        Vec3 velocity = entity.getDeltaMovement();
        boolean marked = entity.hurtMarked, impulse = entity.hasImpulse;
        LivingEntity previous = VICTIM.get();
        VICTIM.set(entity);
        try {
            return entity.hurt(source, amount);
        } finally {
            // Preserve voluntary motion, gravity and earlier hits; undo only this pulse's impulse.
            entity.setDeltaMovement(velocity);
            entity.hurtMarked = marked;
            entity.hasImpulse = impulse;
            if (previous == null) VICTIM.remove(); else VICTIM.set(previous);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void knockback(LivingKnockBackEvent event) {
        if (event.getEntity() == VICTIM.get()) event.setCanceled(true);
    }

    private GravityDamage() { }
}
