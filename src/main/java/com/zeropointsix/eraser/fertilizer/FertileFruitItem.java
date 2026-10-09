package com.zeropointsix.eraser.fertilizer;

import java.util.ArrayList;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FertileFruitItem extends Item {
    public FertileFruitItem() {
        super(new Properties().food(new FoodProperties.Builder().nutrition(8).saturationMod(1.2F).alwaysEat().build()));
    }

    @Override
    public FoodProperties getFoodProperties(ItemStack stack, LivingEntity entity) {
        return new FoodProperties.Builder().nutrition(FertilizerConfig.value(FertilizerConfig.NUTRITION))
                .saturationMod(FertilizerConfig.value(FertilizerConfig.SATURATION).floatValue()).alwaysEat().build();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            entity.heal(FertilizerConfig.HEAL.get().floatValue());
            for (MobEffectInstance effect : new ArrayList<>(entity.getActiveEffects())) {
                if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) entity.removeEffect(effect.getEffect());
            }
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, FertilizerConfig.REGEN_TICKS.get(), 1, false, false, true));
        }
        return result;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return false; }
}
