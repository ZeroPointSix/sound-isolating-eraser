package com.zeropointsix.eraser.wings.net;

import com.zeropointsix.eraser.wings.FlightEvents;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record CycleTierPacket() {
    public static void encode(CycleTierPacket p, FriendlyByteBuf buf) {}
    public static CycleTierPacket decode(FriendlyByteBuf buf) { return new CycleTierPacket(); }

    public static void handle(CycleTierPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        FlightEvents.serverCycleTier(player);
        ctx.get().setPacketHandled(true);
    }
}
