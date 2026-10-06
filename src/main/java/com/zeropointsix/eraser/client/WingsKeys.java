package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.wings.net.BlinkPacket;
import com.zeropointsix.eraser.wings.net.CycleTierPacket;
import com.zeropointsix.eraser.wings.net.HoverTogglePacket;
import com.zeropointsix.eraser.wings.net.WingsNet;
import com.zeropointsix.eraser.wings.net.SetDeployedPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** G 切档 / H 悬停 / R 闪雷 / Space×2 收展 — per Notion §控制。 */
@Mod.EventBusSubscriber(modid = "sound_isolating_eraser", value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WingsKeys {
    public static KeyMapping CYCLE_TIER;
    public static KeyMapping HOVER_TOGGLE;
    public static KeyMapping BLINK;

    private static long lastJumpPress = 0;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        CYCLE_TIER = new KeyMapping("key.sound_isolating_eraser.cycle_tier",
                GLFW.GLFW_KEY_G, "key.categories.sound_isolating_eraser");
        HOVER_TOGGLE = new KeyMapping("key.sound_isolating_eraser.hover",
                GLFW.GLFW_KEY_H, "key.categories.sound_isolating_eraser");
        BLINK = new KeyMapping("key.sound_isolating_eraser.blink",
                GLFW.GLFW_KEY_R, "key.categories.sound_isolating_eraser");
        event.register(CYCLE_TIER);
        event.register(HOVER_TOGGLE);
        event.register(BLINK);
    }

    @Mod.EventBusSubscriber(modid = "sound_isolating_eraser", value = Dist.CLIENT)
    public static final class InputHandler {
        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            long now = System.currentTimeMillis();
            if (CYCLE_TIER.consumeClick()) {
                WingsNet.CHANNEL.sendToServer(new CycleTierPacket());
            }
            if (HOVER_TOGGLE.consumeClick()) {
                WingsNet.CHANNEL.sendToServer(new HoverTogglePacket());
            }
            if (BLINK.consumeClick()) {
                WingsNet.CHANNEL.sendToServer(new BlinkPacket());
            }
            // Space×2 收/展（空中双击空格展开）
            if (event.getKey() == GLFW.GLFW_KEY_SPACE
                    && event.getAction() == GLFW.GLFW_PRESS) {
                WingsClientData.WingInfo w = WingsClientData.get(mc.player);
                if (w != null && !mc.player.onGround()) {
                    if (now - lastJumpPress < 300) {
                        WingsNet.CHANNEL.sendToServer(
                                new SetDeployedPacket(!w.deployed()));
                        lastJumpPress = 0;
                    } else {
                        lastJumpPress = now;
                    }
                }
            }
        }
    }
}
