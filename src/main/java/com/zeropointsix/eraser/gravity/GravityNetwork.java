package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GravityNetwork {
    /** C2S payload is the previewed cell so the server does not recompute from current look. */
    public record Activate(BlockPos center, ResourceLocation dimension) { }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "gravity"), () -> "2", "2"::equals, "2"::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Activate.class,
                (message, buffer) -> {
                    buffer.writeBlockPos(message.center());
                    buffer.writeResourceLocation(message.dimension());
                },
                buffer -> new Activate(buffer.readBlockPos(), buffer.readResourceLocation()),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        var sender = context.getSender();
                        if (sender != null) {
                            GravityFieldController.activate(sender, message.dimension(), message.center());
                        }
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private GravityNetwork() { }
}
