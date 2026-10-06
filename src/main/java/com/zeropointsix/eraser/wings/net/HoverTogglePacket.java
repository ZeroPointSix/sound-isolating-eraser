package com.zeropointsix.eraser.wings.net;

import com.zeropointsix.eraser.wings.FlightEvents;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record HoverTogglePacket() {
    public static void encode(HoverTogglePacket p, FriendlyByteBuf buf) {}
    public static HoverTogglePacket decode(FriendlyByteBuf buf) { return new HoverTogglePacket(); }

    public static void handle(HoverTogglePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        FlightEvents.serverToggleHover(player);
        ctx.get().setPacketHandled(true);
    }
}
