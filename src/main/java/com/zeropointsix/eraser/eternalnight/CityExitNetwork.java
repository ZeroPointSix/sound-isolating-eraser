package com.zeropointsix.eraser.eternalnight;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Visual-only server snapshots; activation and expiry stay entirely server-authoritative. */
public final class CityExitNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "city_exits"), () -> "1", "1"::equals, "1"::equals);

    public static void register() {
        CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Snapshot::encode).decoder(Snapshot::decode).consumerMainThread(Snapshot::handle).add();
        CHANNEL.messageBuilder(Fade.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Fade::encode).decoder(Fade::decode).consumerMainThread(Fade::handle).add();
    }

    public static void fadeIn(ServerPlayer player, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Fade(ticks, true));
    }

    public static void sync(ServerLevel level) {
        var exits = CitySavedData.get(level).activeExits(level, level.getGameTime()).keySet();
        for (var player : level.players()) {
            List<BlockPos> nearby = exits.stream().map(BlockPos::of)
                    .filter(pos -> pos.distToCenterSqr(player.position()) <= 64 * 64).limit(256).toList();
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(nearby));
        }
    }

    public record Snapshot(List<BlockPos> doors) {
        public static void encode(Snapshot packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.doors.size());
            packet.doors.forEach(buffer::writeBlockPos);
        }
        public static Snapshot decode(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid city exit snapshot size");
            List<BlockPos> doors = new ArrayList<>(count);
            for (int i = 0; i < count; i++) doors.add(buffer.readBlockPos());
            return new Snapshot(List.copyOf(doors));
        }
        public static void handle(Snapshot packet, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityClientRuntime.setExits(packet.doors));
            context.get().setPacketHandled(true);
        }
    }

    /** {@code hold} fades to black before teleport; a later dimension change fades out. */
    public record Fade(int ticks, boolean hold) {
        public static void encode(Fade packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.ticks);
            buffer.writeBoolean(packet.hold);
        }
        public static Fade decode(FriendlyByteBuf buffer) {
            return new Fade(buffer.readVarInt(), buffer.readBoolean());
        }
        public static void handle(Fade packet, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityClientRuntime.beginFade(packet.ticks, packet.hold));
            context.get().setPacketHandled(true);
        }
    }
    private CityExitNetwork() { }
}
