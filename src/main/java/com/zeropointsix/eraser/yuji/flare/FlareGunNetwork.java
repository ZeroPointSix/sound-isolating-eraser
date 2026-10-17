package com.zeropointsix.eraser.yuji.flare;

import com.zeropointsix.eraser.yuji.YujiContent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import software.bernie.geckolib.animatable.GeoItem;

/** Server-authorized gun effects, separate from projectile flight and impact. */
public final class FlareGunNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            YujiContent.id("flare_gun"), () -> "1", "1"::equals, "1"::equals);
    private record Shot(int owner, long stack) { }

    public static void register() {
        CHANNEL.messageBuilder(Shot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((shot, buffer) -> { buffer.writeInt(shot.owner()); buffer.writeLong(shot.stack()); })
                .decoder(buffer -> new Shot(buffer.readInt(), buffer.readLong()))
                .consumerMainThread((shot, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.zeropointsix.eraser.yuji.flare.client.FlareGunMuzzleClient.shot(shot.owner(), shot.stack()));
                    context.get().setPacketHandled(true);
                }).add();
    }

    static void fire(Player player) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new Shot(player.getId(), GeoItem.getId(player.getMainHandItem())));
    }

    private FlareGunNetwork() { }
}
