package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class GravitySlamEffects {
    private static final String START = ModMain.MOD_ID + ":gravity_slam_started";

    public static void watch(LivingEntity entity) {
        entity.getPersistentData().putLong(START, entity.level().getGameTime());
    }

    public static boolean isFalling(LivingEntity entity) {
        return entity.getPersistentData().contains(START)
                && entity.level().getGameTime() - entity.getPersistentData().getLong(START) <= 100;
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || !entity.getPersistentData().contains(START)) return;
        long elapsed = entity.level().getGameTime() - entity.getPersistentData().getLong(START);
        if (elapsed <= 0) return;
        if (elapsed > 100) entity.getPersistentData().remove(START);
        else if (entity.onGround()) {
            entity.getPersistentData().remove(START);
            land(entity);
        }
    }

    public static void land(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        var state = level.getBlockState(entity.getOnPos());
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    entity.getX(), entity.getY() + 0.1, entity.getZ(), 24, 0.5, 0.1, 0.5, 0.15);
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_BIG_FALL, SoundSource.HOSTILE, 1.0F, 0.65F);
    }

    private GravitySlamEffects() { }
}
