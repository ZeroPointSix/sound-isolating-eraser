package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class NeonTumorInput {
    private NeonTumorInput() { }

    @SubscribeEvent
    public static void restrictMovement(MovementInputUpdateEvent event) {
        if (!(event.getEntity().getVehicle() instanceof NeonTumorEntity tumor)
                || !tumor.isAlive() || !tumor.isEngulfing()) return;
        // Suppress voluntary input, never the server's authoritative dismount packet.
        var input = event.getInput();
        input.shiftKeyDown = false;
        input.jumping = false;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
    }
}
