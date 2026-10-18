package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class SuxiaPitchPacketMixin {
    @Redirect(method = "handleMoveEntity(Lnet/minecraft/network/protocol/game/ClientboundMoveEntityPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getXRot()F"),
            require = 1, allow = 1)
    private float preservePendingSuxiaPitch(Entity entity) {
        // Only the Pos-only branch reads this getter. A following position packet
        // must not replace an unconsumed rotation with the last rendered pitch.
        return entity instanceof SuxiaSummonEntity summon
                ? summon.clientPitchInterpolationTarget() : entity.getXRot();
    }
}
