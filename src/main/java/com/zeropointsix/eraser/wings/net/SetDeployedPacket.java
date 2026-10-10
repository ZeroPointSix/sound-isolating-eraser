package com.zeropointsix.eraser.wings.net;

import com.zeropointsix.eraser.wings.FlightEvents;
import com.zeropointsix.eraser.wings.WingsState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record SetDeployedPacket(boolean deployed) {
    public static void encode(SetDeployedPacket p, FriendlyByteBuf buf) { buf.writeBoolean(p.deployed); }
    public static SetDeployedPacket decode(FriendlyByteBuf buf) { return new SetDeployedPacket(buf.readBoolean()); }

    public static void handle(SetDeployedPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        FlightEvents.serverSetDeployed(player, p.deployed);
        ctx.get().setPacketHandled(true);
    }
}
