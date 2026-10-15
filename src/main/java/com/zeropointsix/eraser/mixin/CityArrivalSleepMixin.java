package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.NightEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arrival lying on the street is not a bed; vanilla would otherwise wake the visitor immediately. */
@Mixin(LivingEntity.class)
public abstract class CityArrivalSleepMixin {
    // Match the other city mixins: explicit userdev and release names, with a required target.
    @Inject(method = {"stopSleeping", "m_5796_"}, at = @At("HEAD"), remap = false, cancellable = true)
    private void keepCityArrivalSleep(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && NightEvents.keepArrivalSleep(player)) ci.cancel();
    }
}
