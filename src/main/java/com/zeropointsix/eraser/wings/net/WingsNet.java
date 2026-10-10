package com.zeropointsix.eraser.wings.net;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WingsNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "wings"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private WingsNet() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SetDeployedPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDeployedPacket::encode).decoder(SetDeployedPacket::decode)
                .consumerMainThread(SetDeployedPacket::handle).add();
        CHANNEL.messageBuilder(CycleTierPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CycleTierPacket::encode).decoder(CycleTierPacket::decode)
                .consumerMainThread(CycleTierPacket::handle).add();
        CHANNEL.messageBuilder(HoverTogglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HoverTogglePacket::encode).decoder(HoverTogglePacket::decode)
                .consumerMainThread(HoverTogglePacket::handle).add();
        CHANNEL.messageBuilder(BlinkPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlinkPacket::encode).decoder(BlinkPacket::decode)
                .consumerMainThread(BlinkPacket::handle).add();
        CHANNEL.messageBuilder(SyncWingsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncWingsPacket::encode).decoder(SyncWingsPacket::decode)
                .consumerMainThread(SyncWingsPacket::handle).add();
        CHANNEL.messageBuilder(BlinkFlashPacket.class, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlinkFlashPacket::encode).decoder(BlinkFlashPacket::decode)
                .consumerMainThread(BlinkFlashPacket::handle).add();
    }

    public static void syncToTracking(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                SyncWingsPacket.of(player));
    }
}
