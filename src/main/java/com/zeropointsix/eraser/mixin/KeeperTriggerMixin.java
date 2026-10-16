package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.keeper.KeeperSessions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class KeeperTriggerMixin {
    @Inject(method = {"isIgnoringBlockTriggers()Z", "m_6090_()Z"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void keeper$ignoreTriggers(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && KeeperSessions.active(player)) cir.setReturnValue(true);
    }
}
