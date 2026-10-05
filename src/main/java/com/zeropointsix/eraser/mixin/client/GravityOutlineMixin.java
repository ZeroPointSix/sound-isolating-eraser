package com.zeropointsix.eraser.mixin.client;

import com.zeropointsix.eraser.client.GravitySense;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class GravityOutlineMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void gravityOutline(Entity entity, CallbackInfoReturnable<Boolean> result) {
        // Reuse vanilla's through-wall model-outline pass without changing synchronized entity flags.
        if (GravitySense.outlines(entity)) result.setReturnValue(true);
    }
}
