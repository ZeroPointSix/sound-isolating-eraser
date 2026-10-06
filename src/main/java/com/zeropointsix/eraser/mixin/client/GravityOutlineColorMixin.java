package com.zeropointsix.eraser.mixin.client;

import com.zeropointsix.eraser.client.GravitySense;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class GravityOutlineColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void gravityWhite(CallbackInfoReturnable<Integer> result) {
        if (GravitySense.outlines((Entity) (Object) this)) result.setReturnValue(0xFFFFFF);
    }
}
