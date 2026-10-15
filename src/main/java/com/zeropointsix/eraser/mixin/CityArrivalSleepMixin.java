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
    // Dual MCP+SRG names with remap=false (see LivingEntityWallVisibilityMixin): the handwritten
    // refmap has no entry for stopSleeping, so a remapped target crashed production Forge at boot.
    @Inject(method = {"stopSleeping()V", "m_5796_()V"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void keepCityArrivalSleep(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && NightEvents.keepArrivalSleep(player)) ci.cancel();
    }
}
