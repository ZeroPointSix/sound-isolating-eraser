package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.keeper.KeeperSessions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class KeeperDropMixin {
    @Inject(method = {"drop(Z)Z", "m_182294_(Z)Z"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void keeper$denyDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        if (KeeperSessions.active((ServerPlayer) (Object) this)) cir.setReturnValue(false);
    }
}
