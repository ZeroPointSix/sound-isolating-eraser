package com.zeropointsix.eraser.wings.net;

import com.zeropointsix.eraser.wings.FlightEvents;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record BlinkPacket() {
    public static void encode(BlinkPacket p, FriendlyByteBuf buf) {}
    public static BlinkPacket decode(FriendlyByteBuf buf) { return new BlinkPacket(); }

    public static void handle(BlinkPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        FlightEvents.serverBlink(player);
        ctx.get().setPacketHandled(true);
    }
}
