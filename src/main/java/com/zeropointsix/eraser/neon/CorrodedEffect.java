package com.zeropointsix.eraser.neon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CorrodedEffect extends MobEffect {
    public CorrodedEffect() {
        super(MobEffectCategory.HARMFUL, 0xB8326A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Refreshing duration must not reset or accelerate the per-entity damage clock.
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 20 != 0
                || entity instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        boolean armored = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) armored = true;
            if (stack.isDamageableItem()) {
                stack.hurtAndBreak(3, entity, wearer -> wearer.broadcastBreakEvent(slot));
            }
        }
        if (!armored) entity.hurt(CorrosionDamage.source(level), 0.5F);
        level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                2, 0.2, 0.3, 0.2, 0.01);
    }
}
