package com.zeropointsix.eraser.wings.net;

import com.zeropointsix.eraser.client.WingsFlight;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** S2C: tell the blinking player to play the local flash/shake/sound FX. */
public record BlinkFlashPacket() {
    public static void encode(BlinkFlashPacket p, FriendlyByteBuf buf) {}
    public static BlinkFlashPacket decode(FriendlyByteBuf buf) { return new BlinkFlashPacket(); }

    public static void handle(BlinkFlashPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (Minecraft.getInstance().player != null) {
                WingsFlight.blinkFx(Minecraft.getInstance().player);
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
