package com.zeropointsix.eraser.wings.net;

import com.zeropointsix.eraser.client.WingsClientData;
import com.zeropointsix.eraser.wings.WingsState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record SyncWingsPacket(int entityId, boolean deployed, int tier,
                              long chargedUntil, long blinkCooldownUntil,
                              long blinkLockUntil, double hoverY) {
    public static SyncWingsPacket of(ServerPlayer p) {
        return new SyncWingsPacket(p.getId(), WingsState.deployed(p), WingsState.tier(p),
                WingsState.chargedUntil(p), WingsState.blinkCooldownUntil(p),
                WingsState.blinkLockUntil(p), WingsState.hoverY(p));
    }

    public static void encode(SyncWingsPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.entityId);
        buf.writeBoolean(p.deployed);
        buf.writeByte(p.tier);
        buf.writeLong(p.chargedUntil);
        buf.writeLong(p.blinkCooldownUntil);
        buf.writeLong(p.blinkLockUntil);
        buf.writeDouble(p.hoverY);
    }

    public static SyncWingsPacket decode(FriendlyByteBuf buf) {
        return new SyncWingsPacket(buf.readInt(), buf.readBoolean(), buf.readByte(),
                buf.readLong(), buf.readLong(), buf.readLong(), buf.readDouble());
    }

    public static void handle(SyncWingsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> WingsClientData.apply(p)));
        ctx.get().setPacketHandled(true);
    }
}
