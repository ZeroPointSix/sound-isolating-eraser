package com.zeropointsix.rednailstest.mixin;

import com.zeropointsix.rednailstest.RedNailsAcceptanceClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Acceptance-only observers; never alter or synthesize a gameplay packet. */
@Mixin(ClientPacketListener.class)
public abstract class RedNailsClientPacketTraceMixin {
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), remap = false)
    private void beforeMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceClient.traceCapturePacket("BEFORE", packet);
    }
    @Inject(method = "handleSetEntityMotion", at = @At("RETURN"), remap = false)
    private void afterMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceClient.traceCapturePacket("AFTER", packet);
    }
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), remap = false)
    private void beforePosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceClient.traceCapturePacket("BEFORE", packet);
    }
    @Inject(method = "handleMovePlayer", at = @At("RETURN"), remap = false)
    private void afterPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceClient.traceCapturePacket("AFTER", packet);
    }
    @Inject(method = "handleSetEntityPassengersPacket", at = @At("HEAD"), remap = false)
    private void beforePassengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceClient.traceCapturePacket("BEFORE", packet);
    }
    @Inject(method = "handleSetEntityPassengersPacket", at = @At("RETURN"), remap = false)
    private void afterPassengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
        RedNailsAcceptanceClient.traceCapturePacket("AFTER", packet);
    }
}
