package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GravityNetwork {
    public record Activate(double distance, ResourceLocation dimension) { }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "gravity"), () -> "1", "1"::equals, "1"::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Activate.class,
                (message, buffer) -> {
                    buffer.writeDouble(message.distance());
                    buffer.writeResourceLocation(message.dimension());
                },
                buffer -> new Activate(buffer.readDouble(), buffer.readResourceLocation()),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        var sender = context.getSender();
                        if (sender != null && Double.isFinite(message.distance())
                                && message.distance() >= GravityConfig.minDistance()
                                && message.distance() <= GravityConfig.MAX_DISTANCE.get()) {
                            BlockPos center = GravityGeometry.target(sender.getEyePosition(),
                                    sender.getLookAngle(), message.distance());
                            GravityFieldController.activate(sender, message.dimension(), center);
                        }
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private GravityNetwork() { }
}
