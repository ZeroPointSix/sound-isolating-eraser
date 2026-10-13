package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GravityNetwork {
    /** Clients send intent only; the server owns the position and dimension. */
    public record Activate() { }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "gravity"), () -> "3", "3"::equals, "3"::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Activate.class,
                (message, buffer) -> { },
                buffer -> new Activate(),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        var sender = context.getSender();
                        if (sender != null) {
                            GravityFieldController.activate(sender);
                        }
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private GravityNetwork() { }
}
