package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Dual MCP+SRG names with remap=false; see CityBlockTickMixin.
@Mixin(LevelTicks.class)
public abstract class CityScheduledTickMixin {
    @Inject(method = {"schedule", "m_183393_"}, at = @At("HEAD"), remap = false)
    private void citySchedule(ScheduledTick<?> tick, CallbackInfo ci) { CityDoorActions.scheduled(this, tick); }
}
