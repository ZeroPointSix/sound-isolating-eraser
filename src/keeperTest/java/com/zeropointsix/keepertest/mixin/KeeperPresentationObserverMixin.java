package com.zeropointsix.keepertest.mixin;

import com.zeropointsix.eraser.keeper.client.KeeperVisuals;
import com.zeropointsix.keepertest.KeeperTestClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeeperVisuals.class, remap = false)
public abstract class KeeperPresentationObserverMixin {
    @Inject(method = "framePresented", at = @At("RETURN"), remap = false)
    private static void keeperObservePresentedFrame(CallbackInfo callback) {
        KeeperTestClient.presented();
    }
}
