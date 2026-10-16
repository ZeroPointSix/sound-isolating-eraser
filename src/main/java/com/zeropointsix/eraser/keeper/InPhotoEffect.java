package com.zeropointsix.eraser.keeper;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

public final class InPhotoEffect extends MobEffect {
    public InPhotoEffect() { super(MobEffectCategory.NEUTRAL, 0xA37B47); }
    @Override public List<ItemStack> getCurativeItems() { return List.of(); }
    @Override public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            @Override public boolean isVisibleInInventory(MobEffectInstance effect) { return false; }
            @Override public boolean isVisibleInGui(MobEffectInstance effect) { return false; }
        });
    }
}
