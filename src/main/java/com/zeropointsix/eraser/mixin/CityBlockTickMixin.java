package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// No Mixin annotation processor runs in this build: the refmap is a static file, so every
// City mixin lists both the MCP (userdev) and SRG (production) names with remap=false,
// matching LivingEntityWallVisibilityMixin.
@Mixin(ServerLevel.class)
public abstract class CityBlockTickMixin {
    @Inject(method = {"tickBlock", "m_184112_"}, at = @At("HEAD"), remap = false)
    private void cityBeginTick(BlockPos pos, Block block, CallbackInfo ci) {
        CityDoorActions.beginScheduled((ServerLevel) (Object) this, pos, block);
    }
    @Inject(method = {"tickBlock", "m_184112_"}, at = @At("RETURN"), remap = false)
    private void cityEndTick(BlockPos pos, Block block, CallbackInfo ci) { CityDoorActions.end(); }
}
