package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.client.WingsClientData;
import com.zeropointsix.eraser.client.WingsKeys;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** 仅测试模组使用：原生 X11 输入及真实客户端出站包回归。 */
final class WingsInputQa {
    private static final ResourceLocation WINGS = new ResourceLocation("sound_isolating_eraser", "wings");
    private static final String MONITOR = "wings-input-qa";
    // WingsNet 的四个 PLAY_TO_SERVER 消息编号：收展、切档、悬停、雷遁。
    private static final AtomicIntegerArray PACKETS = new AtomicIntegerArray(4);
    private static final List<SavedBinding> SAVED = new ArrayList<>();
    private static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
    private static Channel channel;
    private static Step active;
    private static int elapsed;
    private static int[] before;
    private static int beforeTier;
    private static Vec3 beforePosition;

    private record SavedBinding(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) { }
    private record Step(String name, Action input, Action check, int delay, int packet, int count) { }
    @FunctionalInterface
    private interface Action { void run() throws Exception; }

    static void begin(Minecraft mc) {
        for (KeyMapping key : List.of(WingsKeys.CYCLE_TIER, WingsKeys.HOVER_TOGGLE,
                WingsKeys.BLINK, mc.options.keyJump)) {
            SAVED.add(new SavedBinding(key, key.getKey(), key.getKeyModifier()));
        }
        channel = mc.getConnection().getConnection().channel();
        channel.pipeline().addLast(MONITOR, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                if (message instanceof ServerboundCustomPayloadPacket packet && WINGS.equals(packet.getIdentifier())) {
                    FriendlyByteBuf payload = new FriendlyByteBuf(packet.getData().duplicate());
                    int id = payload.readVarInt();
                    if (id >= 0 && id < PACKETS.length()) PACKETS.incrementAndGet(id);
                }
                super.write(ctx, message, promise);
            }
        });
        add("fresh airborne fixture", () -> mc.player.connection.sendCommand("wingsqa phase controlsSetup"),
                () -> require(info(mc).deployed() && info(mc).tier() == 1, "fixture is deployed/cruise"), 40, -1, 0);
        KeyMapping[] skills = {WingsKeys.CYCLE_TIER, WingsKeys.HOVER_TOGGLE, WingsKeys.BLINK};
        for (int i = 0; i < skills.length; i++) {
            KeyMapping mapping = skills[i];
            int packet = i + 1;
            String label = mapping.getName();
            add(label + " reset airborne fixture", () -> {
                restoreBindings();
                mc.player.connection.sendCommand("wingsqa phase controlsSetup");
            }, () -> require(info(mc).deployed() && info(mc).tier() == 1 && mc.player.getY() > 95,
                    "fixture reset before held Shift"), 30, -1, 0);
            add(label + " bare K rejected", () -> {
                restoreBindings();
                bind(mapping, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
                input("key", "k");
            }, () -> { }, 10, -1, 0);
            add(label + " unrelated key cannot replay rejected click", () -> input("key", "F8"),
                    () -> { }, 10, -1, 0);
            add(label + " Shift+K held emits exactly once", () -> input("keydown", "Shift_L", "k"),
                    () -> checkAction(mc, packet), 25, packet, 1);
            add(label + " release does not emit", () -> input("keyup", "k", "Shift_L"),
                    () -> { }, 8, -1, 0);
            add(label + " open inventory", () -> input("key", "e"),
                    () -> require(mc.screen != null, "inventory opened"), 8, -1, 0);
            add(label + " GUI input rejected", () -> input("key", "shift+k"),
                    () -> { }, 8, -1, 0);
            add(label + " close inventory without replay", () -> input("key", "Escape"),
                    () -> require(mc.screen == null, "inventory closed"), 8, -1, 0);
            add(label + " side button immediate", () -> {
                bind(mapping, KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
                input("click", "8");
            }, () -> checkAction(mc, packet), 8, packet, 1);
            add(label + " side button has no delayed keyboard replay", () -> input("key", "F8"),
                    () -> { }, 15, -1, 0);
            add(label + " bare modified mouse rejected", () -> {
                bind(mapping, KeyModifier.CONTROL, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
                input("click", "8");
            }, () -> { }, 10, -1, 0);
            add(label + " Ctrl+mouse emits exactly once", () -> {
                input("keydown", "Control_L");
                input("click", "8");
                input("keyup", "Control_L");
            }, () -> { }, 10, packet, 1);
        }
        add("modified G still leaves bare G to Curios", () -> {
            restoreBindings();
            bind(WingsKeys.CYCLE_TIER, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
            input("key", "g");
        }, () -> require(mc.screen != null && mc.screen.getClass().getName().startsWith("top.theillusivec4.curios."),
                "bare G opens Curios with Shift+G wing binding"), 10, -1, 0);
        add("close Curios before modified G", () -> input("key", "Escape"), () -> { }, 8, -1, 0);
        add("Shift+G cycles without Curios", () -> input("key", "shift+g"),
                () -> checkAction(mc, 1), 10, 1, 1);
        add("mouse binding pause menu", () -> {
            restoreBindings();
            bind(WingsKeys.CYCLE_TIER, KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
            input("key", "Escape");
        }, () -> require(mc.screen != null, "pause menu opened"), 8, -1, 0);
        add("GUI closing click must not trigger mouse skill", () -> clickReturnToGame(mc),
                () -> require(mc.screen == null, "native click returned to game"), 8, -1, 0);
        add("next in-game left click works exactly once", () -> input("click", "1"),
                () -> checkAction(mc, 1), 10, 1, 1);
        add("restore controls before jump rebind", WingsInputQa::restoreBindings, () -> { }, 10, -1, 0);
        add("jump K ignores old Space double press", () -> {
            bind(mc.options.keyJump, KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            input("key", "--delay", "100", "space", "space");
        }, () -> require(info(mc).deployed(), "old Space leaves wings deployed"), 8, -1, 0);
        add("jump K single press does not toggle", () -> input("key", "k"), () -> { }, 10, -1, 0);
        add("jump K double press stows", () -> input("key", "--delay", "100", "k", "k"),
                () -> require(!info(mc).deployed(), "custom jump stows wings"), 8, 0, 1);
        add("jump K double press deploys", () -> input("key", "--delay", "100", "k", "k"),
                () -> require(info(mc).deployed(), "custom jump deploys wings"), 8, 0, 1);
        add("jump Shift+K bare double press rejected", () -> {
            bind(mc.options.keyJump, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            input("key", "--delay", "100", "k", "k");
        }, () -> require(info(mc).deployed(), "bare jump chord does not stow"), 8, -1, 0);
        add("jump Shift+K double press stows", () -> input("key", "--delay", "100", "shift+k", "shift+k"),
                () -> require(!info(mc).deployed(), "modified jump stows wings"), 8, 0, 1);
        add("jump mouse double click deploys", () -> {
            bind(mc.options.keyJump, KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
            input("click", "--repeat", "2", "--delay", "100", "8");
        }, () -> require(info(mc).deployed(), "mouse jump deploys wings"), 8, 0, 1);
        add("mouse jump cannot replay on keyboard", () -> input("key", "F8"), () -> { }, 10, -1, 0);
        add("restored default double Space stows", () -> {
            restoreBindings();
            input("key", "--delay", "100", "space", "space");
        }, () -> require(!info(mc).deployed(), "default jump restored"), 8, 0, 1);
        add("restored default double Space deploys", () -> input("key", "--delay", "100", "space", "space"),
                () -> require(info(mc).deployed(), "default deploy still works"), 8, 0, 1);
    }

    static boolean tick(Minecraft mc, Path results) throws Exception {
        if (active == null) {
            active = STEPS.poll();
            if (active == null) {
                restore(mc);
                Files.writeString(results.resolve("wings-input-rebinding.pass"), "PASS native keyboard/mouse/modifier/jump bindings and outbound packet counts\n");
                return true;
            }
            elapsed = 0;
            before = counts();
            beforeTier = info(mc).tier();
            beforePosition = mc.player.position();
            active.input().run();
            return false;
        }
        if (++elapsed < active.delay()) return false;
        int[] delta = counts();
        for (int i = 0; i < delta.length; i++) {
            delta[i] -= before[i];
            require(delta[i] == (i == active.packet() ? active.count() : 0),
                    active.name() + " packet=" + i + " actual=" + delta[i]);
        }
        active.check().run();
        System.out.println("WINGS_INPUT_ASSERT " + active.name() + " packets=" + Arrays.toString(delta)
                + " tier=" + info(mc).tier() + " deployed=" + info(mc).deployed());
        active = null;
        return false;
    }

    private static void checkAction(Minecraft mc, int packet) {
        require(mc.screen == null, "input must stay in game");
        if (packet == 1) require(info(mc).tier() == (beforeTier + 1) % 4, "cycle changed exactly one tier");
        if (packet == 2) require(info(mc).tier() != beforeTier, "hover toggled once");
        if (packet == 3) {
            Vec3 delta = mc.player.position().subtract(beforePosition);
            require(Math.hypot(delta.x, delta.z) > 23, "blink produced real displacement: " + delta);
        }
    }

    private static WingsClientData.WingInfo info(Minecraft mc) { return WingsClientData.get(mc.player); }

    private static int[] counts() {
        int[] values = new int[PACKETS.length()];
        for (int i = 0; i < values.length; i++) values[i] = PACKETS.get(i);
        return values;
    }

    private static void add(String name, Action input, Action check, int delay, int packet, int count) {
        STEPS.add(new Step(name, input, check, delay, packet, count));
    }

    private static void bind(KeyMapping mapping, KeyModifier modifier, InputConstants.Key key) {
        mapping.setKeyModifierAndCode(modifier, key);
        KeyMapping.resetMapping();
    }

    private static void restoreBindings() {
        for (SavedBinding saved : SAVED) saved.mapping().setKeyModifierAndCode(saved.modifier(), saved.key());
        KeyMapping.resetMapping();
    }

    static void restore(Minecraft mc) {
        if (SAVED.isEmpty()) return;
        try {
            input("keyup", "k", "Shift_L", "Control_L");
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to release native QA keys", failure);
        } finally {
            restoreBindings();
            SAVED.clear();
            if (channel != null && channel.pipeline().get(MONITOR) != null) channel.pipeline().remove(MONITOR);
            channel = null;
        }
    }

    private static void input(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "xdotool";
        System.arraycopy(args, 0, command, 1, args.length);
        if (new ProcessBuilder(command).inheritIO().start().waitFor() != 0) throw new AssertionError("xdotool failed");
    }

    private static void clickReturnToGame(Minecraft mc) throws Exception {
        String label = Component.translatable("menu.returnToGame").getString();
        AbstractWidget button = mc.screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(w -> w.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new AssertionError("Return to Game button missing"));
        Process focus = new ProcessBuilder("xdotool", "getwindowfocus").start();
        String window = new String(focus.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        if (focus.waitFor() != 0) throw new AssertionError("Could not find focused native window");
        double scale = mc.getWindow().getGuiScale();
        input("mousemove", "--window", window, Integer.toString((int) ((button.getX() + button.getWidth() / 2) * scale)),
                Integer.toString((int) ((button.getY() + button.getHeight() / 2) * scale)));
        input("click", "1");
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }

    private WingsInputQa() { }
}
