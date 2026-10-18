package com.zeropointsix.rednailstest.mixin;

import com.zeropointsix.rednailstest.RedNailsAcceptanceServer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records whether a real movement report changes position or overwrites a pending pull. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RedNailsServerPacketTraceMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), remap = false)
    private void beforePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceServer.traceCaptureMovementPacket(((ServerGamePacketListenerImpl) (Object) this).player, "BEFORE", packet);
    }
    @Inject(method = "handleMovePlayer", at = @At("RETURN"), remap = false)
    private void afterPlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceServer.traceCaptureMovementPacket(((ServerGamePacketListenerImpl) (Object) this).player, "AFTER", packet);
    }
    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), remap = false)
    private void beforeVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        RedNailsAcceptanceServer.traceCaptureMovementPacket(((ServerGamePacketListenerImpl) (Object) this).player, "BEFORE", packet);
    }
    @Inject(method = "handleMoveVehicle", at = @At("RETURN"), remap = false)
    private void afterVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        RedNailsAcceptanceServer.traceCaptureMovementPacket(((ServerGamePacketListenerImpl) (Object) this).player, "AFTER", packet);
    }
}
