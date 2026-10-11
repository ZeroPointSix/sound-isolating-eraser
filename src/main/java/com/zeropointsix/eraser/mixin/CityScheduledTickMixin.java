package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelTicks.class)
public abstract class CityScheduledTickMixin {
    @Inject(method = "schedule", at = @At("HEAD"))
    private void citySchedule(ScheduledTick<?> tick, CallbackInfo ci) { CityDoorActions.scheduled(this, tick); }
}
