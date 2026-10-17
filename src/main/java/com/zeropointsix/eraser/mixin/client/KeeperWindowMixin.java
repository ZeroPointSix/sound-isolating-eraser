package com.zeropointsix.eraser.mixin.client;

import com.mojang.blaze3d.platform.Window;
import com.zeropointsix.eraser.keeper.client.KeeperVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class KeeperWindowMixin {
    @Inject(method = {"updateDisplay", "m_85435_"}, at = @At("RETURN"), remap = false)
    private void keeperFramePresented(CallbackInfo callback) {
        KeeperVisuals.framePresented();
    }
}
