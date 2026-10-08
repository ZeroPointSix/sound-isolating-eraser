package com.zeropointsix.eraser.pill;

import com.zeropointsix.eraser.ModMain;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PillNetwork {
    public record Status(int activeTicks, int withdrawalTicks, int activeDuration,
                         int withdrawalDuration, int packDamage, int packUses) {
        public static final Status EMPTY = new Status(0, 0, 1, 1, 0, 12);
        public static Status from(PillState state) {
            return new Status(state.remainingTicks(), state.withdrawalTicks(), state.activeDuration(),
                    state.withdrawalDuration(), state.packDamage(), state.packUses());
        }
        public static Status decode(FriendlyByteBuf b) {
            return new Status(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(activeTicks); b.writeVarInt(withdrawalTicks); b.writeVarInt(activeDuration);
            b.writeVarInt(withdrawalDuration); b.writeVarInt(packDamage); b.writeVarInt(packUses);
        }
    }

    // This data-only inbox deliberately has no client class references on a dedicated server.
    public static Status clientStatus = Status.EMPTY;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "pill_status"), () -> "1", "1"::equals, "1"::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Status.class, Status::encode, Status::decode, (message, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> clientStatus = message);
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sync(ServerPlayer player, PillState state) {
        if (!(player instanceof FakePlayer) && player.connection != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), Status.from(state));
        }
    }

    private PillNetwork() { }
}
