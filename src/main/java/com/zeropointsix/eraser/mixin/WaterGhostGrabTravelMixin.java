package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.client.WaterGhostGrabClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class WaterGhostGrabTravelMixin {
    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void applyGrabPull(Vec3 input, CallbackInfo ci) {
        WaterGhostGrabClient.pull((LivingEntity) (Object) this);
    }
}
