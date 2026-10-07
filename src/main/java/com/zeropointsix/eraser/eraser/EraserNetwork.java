package com.zeropointsix.eraser.eraser;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class EraserNetwork {
    public record Cycle(InteractionHand hand) { }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "eraser"), () -> "1", "1"::equals, "1"::equals);

    public static boolean cycle(ServerPlayer player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator() || !stack.is(ModItems.SOUND_ISOLATING_ERASER.get())) return false;
        EraserMode mode = EraserMode.read(stack).next();
        mode.write(stack);
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(mode.label(), true);
        return true;
    }

    public static void register() {
        CHANNEL.registerMessage(0, Cycle.class,
                (message, buffer) -> buffer.writeEnum(message.hand()),
                buffer -> new Cycle(buffer.readEnum(InteractionHand.class)),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) cycle(context.getSender(), message.hand());
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private EraserNetwork() { }
}
