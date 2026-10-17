package com.zeropointsix.eraser.eraser;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class EraserNetwork {
    public record Cycle(InteractionHand hand) { }
    public record RedNailsKey(boolean down) { }
    public record GrabAnchor(UUID holder, ResourceLocation dimension, Vec3 position, int remainingTicks) { }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModMain.MOD_ID, "eraser"), () -> "3", "3"::equals, "3"::equals);

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
        CHANNEL.registerMessage(2, GrabAnchor.class,
                (message, buffer) -> {
                    buffer.writeUUID(message.holder());
                    buffer.writeResourceLocation(message.dimension());
                    buffer.writeDouble(message.position().x);
                    buffer.writeDouble(message.position().y);
                    buffer.writeDouble(message.position().z);
                    buffer.writeVarInt(message.remainingTicks());
                },
                buffer -> new GrabAnchor(buffer.readUUID(), buffer.readResourceLocation(),
                        new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readVarInt()),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> com.zeropointsix.eraser.client.WaterGhostGrabClient.update(message)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, RedNailsKey.class,
                (message, buffer) -> buffer.writeBoolean(message.down()),
                buffer -> new RedNailsKey(buffer.readBoolean()),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) com.zeropointsix.eraser.rednails.RedNailsInput.key(context.getSender(), message.down());
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
