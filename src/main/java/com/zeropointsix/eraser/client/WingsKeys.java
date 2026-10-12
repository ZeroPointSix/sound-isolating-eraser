package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.wings.WingsState;
import com.zeropointsix.eraser.wings.net.BlinkPacket;
import com.zeropointsix.eraser.wings.net.CycleTierPacket;
import com.zeropointsix.eraser.wings.net.HoverTogglePacket;
import com.zeropointsix.eraser.wings.net.WingsNet;
import com.zeropointsix.eraser.wings.net.SetDeployedPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** G 切档 / H 悬停 / R 闪雷 / 双击当前跳跃绑定收展。 */
@Mod.EventBusSubscriber(modid = "sound_isolating_eraser", value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WingsKeys {
    public static KeyMapping CYCLE_TIER;
    public static KeyMapping HOVER_TOGGLE;
    public static KeyMapping BLINK;

    private static long lastJumpPress = -1;
    private static InputConstants.Key lastJumpKey;
    private static KeyModifier lastJumpModifier;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        CYCLE_TIER = new KeyMapping("key.sound_isolating_eraser.cycle_tier",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G, "key.categories.sound_isolating_eraser");
        HOVER_TOGGLE = new KeyMapping("key.sound_isolating_eraser.hover",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H, "key.categories.sound_isolating_eraser");
        BLINK = new KeyMapping("key.sound_isolating_eraser.blink",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R, "key.categories.sound_isolating_eraser");
        event.register(CYCLE_TIER);
        event.register(HOVER_TOGGLE);
        event.register(BLINK);
    }

    @Mod.EventBusSubscriber(modid = "sound_isolating_eraser", value = Dist.CLIENT)
    public static final class InputHandler {
        private static boolean mouseStartedInGame;
        private static void consumeConflictingCuriosClick(Minecraft mc) {
            for (KeyMapping key : mc.options.keyMappings) {
                if (!key.getName().equals("key.curios.open.desc") || !cycleOwnsBinding(mc, key)) continue;
                // Forge 47.2 queues all same-key bindings regardless of conflict context.
                // Consume only the wing-owned click before Curios handles its END tick.
                while (key.consumeClick()) { }
            }
        }

        private static boolean cycleOwnsBinding(Minecraft mc, KeyMapping other) {
            if (mc.player == null || mc.screen != null
                    || !WingsState.wearing(mc.player)) return false;
            WingsClientData.WingInfo info = WingsClientData.get(mc.player);
            return info != null && info.deployed()
                    && CYCLE_TIER.getKey().equals(other.getKey())
                    && CYCLE_TIER.isActiveAndMatches(other.getKey());
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            onPress(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction());
        }

        @SubscribeEvent
        public static void beforeMouse(InputEvent.MouseButton.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            mouseStartedInGame = mc.player != null && mc.screen == null;
        }

        @SubscribeEvent
        public static void onMouse(InputEvent.MouseButton.Post event) {
            // A GUI click may close its screen before Post is fired.
            if (!mouseStartedInGame) {
                discardSkillClicks();
                lastJumpPress = -1;
                return;
            }
            onPress(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction());
        }

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || mc.player.onGround()
                    || !mc.options.keyJump.getKey().equals(lastJumpKey)
                    || mc.options.keyJump.getKeyModifier() != lastJumpModifier) {
                lastJumpPress = -1;
            }
        }

        private static void discardSkillClicks() {
            if (CYCLE_TIER == null) return;
            while (CYCLE_TIER.consumeClick()) { }
            while (HOVER_TOGGLE.consumeClick()) { }
            while (BLINK.consumeClick()) { }
        }

        private static void onPress(InputConstants.Key input, int action) {
            if (CYCLE_TIER == null) return;
            // Forge 47.2 queues inactive modifier variants too. Never dispatch from that queue.
            discardSkillClicks();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                lastJumpPress = -1;
                return;
            }
            if (action != GLFW.GLFW_PRESS) return;
            if (CYCLE_TIER.isActiveAndMatches(input)) {
                consumeConflictingCuriosClick(mc);
                WingsNet.CHANNEL.sendToServer(new CycleTierPacket());
            }
            if (HOVER_TOGGLE.isActiveAndMatches(input)) {
                WingsNet.CHANNEL.sendToServer(new HoverTogglePacket());
            }
            if (BLINK.isActiveAndMatches(input)) {
                WingsNet.CHANNEL.sendToServer(new BlinkPacket());
            }
            KeyMapping jump = mc.options.keyJump;
            if (jump.isActiveAndMatches(input)) {
                if (!mc.player.onGround()) {
                    long now = Util.getMillis();
                    WingsClientData.WingInfo w = WingsClientData.localOrFallback(mc.player);
                    if (lastJumpPress >= 0 && now - lastJumpPress < 300
                            && jump.getKey().equals(lastJumpKey)
                            && jump.getKeyModifier() == lastJumpModifier) {
                        WingsNet.CHANNEL.sendToServer(
                                new SetDeployedPacket(!w.deployed()));
                        lastJumpPress = -1;
                    } else {
                        lastJumpPress = now;
                        lastJumpKey = jump.getKey();
                        lastJumpModifier = jump.getKeyModifier();
                    }
                } else {
                    lastJumpPress = -1;
                }
            }
        }
    }
}
