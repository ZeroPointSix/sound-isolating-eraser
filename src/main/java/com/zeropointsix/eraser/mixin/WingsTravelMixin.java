package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.client.WingsFlight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only, deployed local wings still use Entity.move for real collision clipping. */
@Mixin(LivingEntity.class)
public abstract class WingsTravelMixin {
    @Inject(method = {"travel", "m_7023_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void travelWithWings(Vec3 input, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && WingsFlight.travel(player)) ci.cancel();
    }
}
