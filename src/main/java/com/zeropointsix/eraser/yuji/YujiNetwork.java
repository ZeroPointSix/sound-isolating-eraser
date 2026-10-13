package com.zeropointsix.eraser.yuji;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class YujiNetwork {
    private record Look(int entityId, boolean turn) {}
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(YujiContent.id("yuji"), () -> "1", "1"::equals, "1"::equals);
    public static void register() {
        CHANNEL.registerMessage(0, Look.class, (message, buffer) -> {
            buffer.writeInt(message.entityId()); buffer.writeBoolean(message.turn());
        }, buffer -> new Look(buffer.readInt(), buffer.readBoolean()), (message, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.zeropointsix.eraser.yuji.client.YujiClient.look(message.entityId(), message.turn())));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void look(ServerPlayer player, int source, boolean turn) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Look(source, turn));
    }
    private YujiNetwork() {}
}
