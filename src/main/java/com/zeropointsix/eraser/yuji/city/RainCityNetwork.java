package com.zeropointsix.eraser.yuji.city;

import com.zeropointsix.eraser.yuji.YujiContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RainCityNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(YujiContent.id("city"), () -> "1", "1"::equals, "1"::equals);
    private record State(long elapsed, long bellAge) {
        void encode(FriendlyByteBuf b) { b.writeLong(elapsed); b.writeLong(bellAge); }
        static State decode(FriendlyByteBuf b) { return new State(b.readLong(), b.readLong()); }
    }
    public static void register() {
        CHANNEL.messageBuilder(State.class, 0, NetworkDirection.PLAY_TO_CLIENT).encoder(State::encode).decoder(State::decode)
                .consumerMainThread((message, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RainCityClient.update(message.elapsed, message.bellAge));
                    context.get().setPacketHandled(true);
                }).add();
    }
    public static void send(ServerPlayer p, long elapsed, long bellAge) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new State(elapsed, bellAge)); }
    private RainCityNetwork() { }
}
