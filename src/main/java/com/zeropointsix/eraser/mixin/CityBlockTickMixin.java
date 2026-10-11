package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class CityBlockTickMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void cityBeginTick(BlockPos pos, Block block, CallbackInfo ci) {
        CityDoorActions.beginScheduled((ServerLevel) (Object) this, pos, block);
    }
    @Inject(method = "tickBlock", at = @At("RETURN"))
    private void cityEndTick(BlockPos pos, Block block, CallbackInfo ci) { CityDoorActions.end(); }
}
