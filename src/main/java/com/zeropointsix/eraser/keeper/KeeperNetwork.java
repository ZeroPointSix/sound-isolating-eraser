package com.zeropointsix.eraser.keeper;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.keeper.client.KeeperVisuals;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class KeeperNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "keeper"), () -> "1", "1"::equals, "1"::equals);
    public record Flash() { }
    public record Visibility(UUID player, boolean hidden) { }
    public static void register() {
        CHANNEL.registerMessage(0, Flash.class, (message, buffer) -> {}, buffer -> new Flash(), (message, supplied) -> {
            var context = supplied.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> KeeperVisuals::flash));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Visibility.class, (message, buffer) -> {
            buffer.writeUUID(message.player()); buffer.writeBoolean(message.hidden());
        }, buffer -> new Visibility(buffer.readUUID(), buffer.readBoolean()), (message, supplied) -> {
            var context = supplied.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> KeeperVisuals.visibility(message.player(), message.hidden())));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void flash(ServerPlayer player) {
        if (!(player instanceof FakePlayer)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Flash());
    }
    public static void visibility(ServerPlayer player, boolean hidden) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new Visibility(player.getUUID(), hidden));
    }
    public static void tracking(ServerPlayer watcher, ServerPlayer target) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), new Visibility(target.getUUID(), KeeperSessions.active(target)));
    }
    private KeeperNetwork() { }
}
