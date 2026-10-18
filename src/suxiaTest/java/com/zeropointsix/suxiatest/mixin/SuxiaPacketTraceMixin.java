package com.zeropointsix.suxiatest.mixin;

import com.zeropointsix.suxiatest.SuxiaPitchPackets;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Development-only packet trace, never packaged in the mod. */
@Mixin(ClientPacketListener.class)
public abstract class SuxiaPacketTraceMixin {
    @Inject(method = "handleMoveEntity", at = @At("HEAD"), remap = false)
    private void beforeMove(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        SuxiaPitchPackets.trace("BEFORE", packet);
    }

    @Inject(method = "handleMoveEntity", at = @At("RETURN"), remap = false)
    private void afterMove(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        SuxiaPitchPackets.trace("AFTER", packet);
    }
}
