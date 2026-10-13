package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.wings.WingsState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WingsFlightConnectionMixin {
    @Shadow(remap = false, aliases = {"f_9737_"}) private int aboveGroundTickCount;

    @Inject(method = {"tick", "m_9933_"}, at = @At("HEAD"), remap = false)
    private void allowAuthorizedWingHover(CallbackInfo ci) {
        // Only server-authorized wing flight bypasses the vanilla floating timer.
        // Movement validation and the wings speed governor remain active.
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (player.isAlive() && WingsState.wearing(player) && WingsState.deployed(player)) {
            aboveGroundTickCount = 0;
        }
    }
}
