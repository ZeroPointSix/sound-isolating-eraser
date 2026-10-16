package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.keeper.KeeperSessions;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class KeeperContainerMixin {
    @Inject(method = {"handleContainerClick", "m_5914_", "handleContainerButtonClick", "m_6557_",
            "handleSetCreativeModeSlot", "m_5964_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void keeper$denyInventoryMutation(CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        // The first invocation may be on Netty; vanilla will enqueue the same handler.
        if (player.server.isSameThread() && KeeperSessions.active(player)) {
            player.containerMenu.broadcastFullState();
            ci.cancel();
        }
    }
}
