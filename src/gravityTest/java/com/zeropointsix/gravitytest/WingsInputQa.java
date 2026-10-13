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
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.Util;
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
    private static final AtomicReference<DeployObservation> LAST_DEPLOY = new AtomicReference<>();
    private static final List<SavedBinding> SAVED = new ArrayList<>();
    private static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
    private static Channel channel;
    private static Step active;
    private static int elapsed;
    private static int[] before;
    private static int beforeTier;
    private static boolean beforeDeployed;
    private static long inputFinishedAt;
    private static Vec3 beforePosition;
    private static boolean restoreBindingsAfterObservation;

    private record SavedBinding(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) { }
    private record DeployObservation(int sequence, boolean deployed, long sentAt) { }
    private record Step(String name, Action input, Action check, int delay, int packet, int count) { }
    @FunctionalInterface
    private interface Action { void run() throws Exception; }

    static void begin(Minecraft mc) {
        for (KeyMapping key : List.of(WingsKeys.CYCLE_TIER, WingsKeys.HOVER_TOGGLE,
                WingsKeys.BLINK, mc.options.keyJump)) {
            SAVED.add(new SavedBinding(key, key.getKey(), key.getKeyModifier()));
        }
        KeyMapping curios = curiosBinding(mc);
        SAVED.add(new SavedBinding(curios, curios.getKey(), curios.getKeyModifier()));
        channel = mc.getConnection().getConnection().channel();
        channel.pipeline().addLast(MONITOR, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                if (message instanceof ServerboundCustomPayloadPacket packet && WINGS.equals(packet.getIdentifier())) {
                    FriendlyByteBuf payload = new FriendlyByteBuf(packet.getData().duplicate());
                    int id = payload.readVarInt();
                    if (id == 0) {
                        LAST_DEPLOY.set(new DeployObservation(PACKETS.get(0) + 1,
                                payload.readBoolean(), Util.getMillis()));
                    }
                    if (id >= 0 && id < PACKETS.length()) PACKETS.incrementAndGet(id);
                }
                super.write(ctx, message, promise);
            }
        });
        add("fresh airborne fixture", () -> mc.player.connection.sendCommand("wingsqa phase inputSetup"),
                () -> require(info(mc).deployed() && info(mc).tier() == 1, "fixture is deployed/cruise"), 40, -1, 0);
        KeyMapping[] skills = {WingsKeys.CYCLE_TIER, WingsKeys.HOVER_TOGGLE, WingsKeys.BLINK};
        for (int i = 0; i < skills.length; i++) {
            KeyMapping mapping = skills[i];
            int packet = i + 1;
            String label = mapping.getName();
            add(label + " reset airborne fixture", () -> {
                restoreBindings();
                // Held Shift is also a real descent input; leave enough altitude in BOOST.
                mc.player.connection.sendCommand("wingsqa phase inputSetup");
            }, () -> require(info(mc).deployed() && info(mc).tier() == 1 && mc.player.getY() > 995,
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
        add("R4 overlapping bindings reset airborne fixture", () -> {
            restoreBindings();
            mc.player.connection.sendCommand("wingsqa phase inputSetup");
        }, () -> require(info(mc).deployed() && info(mc).tier() == 1 && mc.player.getY() > 995,
                "R4 fixture is deployed/cruise"), 40, -1, 0);
        add("R4 Shift+K selects blink without bare K cycle", () -> {
            bind(WingsKeys.CYCLE_TIER, KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            bind(WingsKeys.BLINK, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            restoreBindingsAfterObservation = true;
            try {
                input("key", "shift+k");
            } catch (Exception | Error failure) {
                restoreBindingsAfterObservation = false;
                restore(mc);
                throw failure;
            }
        }, () -> {
            require(mc.screen == null, "R4 input stays in game");
            require(info(mc).tier() == beforeTier, "R4 modified blink must not cycle the bare binding");
        }, 10, 3, 1);
        add("R4 held Shift does not disable unrelated bare G", () -> input("key", "shift+g"),
                () -> checkAction(mc, 1), 10, 1, 1);
        add("R4 Shift+K selects cycle without bare K blink", () -> {
            bind(WingsKeys.CYCLE_TIER, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            bind(WingsKeys.BLINK, KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            restoreBindingsAfterObservation = true;
            input("key", "shift+k");
        }, () -> checkAction(mc, 1), 10, 1, 1);
        add("R4 Ctrl+K selects hover without bare K cycle", () -> {
            bind(WingsKeys.CYCLE_TIER, KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            bind(WingsKeys.HOVER_TOGGLE, KeyModifier.CONTROL, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            restoreBindingsAfterObservation = true;
            input("key", "ctrl+k");
        }, () -> checkAction(mc, 2), 10, 2, 1);
        add("R4 mouse overlap reset airborne fixture", () -> {
            restoreBindings();
            mc.player.connection.sendCommand("wingsqa phase inputSetup");
        }, () -> require(info(mc).deployed() && info(mc).tier() == 1 && mc.player.getY() > 195,
                "R4 mouse fixture is deployed/cruise"), 40, -1, 0);
        add("R4 Ctrl+mouse selects blink without bare mouse cycle", () -> {
            bind(WingsKeys.CYCLE_TIER, KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
            bind(WingsKeys.BLINK, KeyModifier.CONTROL, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
            restoreBindingsAfterObservation = true;
            input("keydown", "Control_L");
            input("click", "8");
            input("keyup", "Control_L");
        }, () -> {
            checkAction(mc, 3);
            require(info(mc).tier() == beforeTier, "R4 modified mouse blink must not cycle the bare binding");
        }, 10, 3, 1);
        add("R4 Shift+mouse selects cycle without bare mouse blink", () -> {
            bind(WingsKeys.CYCLE_TIER, KeyModifier.SHIFT, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
            bind(WingsKeys.BLINK, KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
            restoreBindingsAfterObservation = true;
            input("keydown", "Shift_L");
            input("click", "8");
            input("keyup", "Shift_L");
        }, () -> checkAction(mc, 1), 10, 1, 1);
        add("R4 mouse overlap cannot replay on keyboard", () -> input("key", "F8"),
                () -> { }, 10, -1, 0);
        add("modified G still leaves bare G to Curios", () -> {
            restoreBindings();
            bind(WingsKeys.CYCLE_TIER, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
            input("key", "g");
        }, () -> require(mc.screen != null && mc.screen.getClass().getName().startsWith("top.theillusivec4.curios."),
                "bare G opens Curios with Shift+G wing binding"), 10, -1, 0);
        add("close Curios before modified G", () -> input("key", "Escape"), () -> { }, 8, -1, 0);
        add("Shift+G cycles without Curios", () -> input("key", "shift+g"),
                () -> checkAction(mc, 1), 10, 1, 1);
        add("R4 modified Curios leaves bare G to cycle", () -> {
            restoreBindings();
            bind(curios, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
            input("key", "g");
        }, () -> checkAction(mc, 1), 10, 1, 1);
        add("R4 Shift+G selects Curios without bare G cycle", () -> input("key", "shift+g"),
                () -> require(mc.screen != null && mc.screen.getClass().getName().startsWith("top.theillusivec4.curios."),
                        "R4 modified Curios owns Shift+G"), 10, -1, 0);
        add("R4 close modified Curios", () -> {
            input("key", "Escape");
            restoreBindings();
        }, () -> require(mc.screen == null, "R4 modified Curios closed without replay"), 8, -1, 0);
        add("R4 modified blink wins over bare cycle and Curios", () -> {
            bind(WingsKeys.BLINK, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
            restoreBindingsAfterObservation = true;
            input("key", "shift+g");
        }, () -> {
            require(mc.screen == null, "R4 modified blink must consume the bare Curios click");
            require(info(mc).tier() == beforeTier, "R4 modified blink must not cycle G");
        }, 10, 3, 1);
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
        add("R4 modified skill does not double-press bare jump", () -> {
            bind(mc.options.keyJump, KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            bind(WingsKeys.BLINK, KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            restoreBindingsAfterObservation = true;
            input("key", "--delay", "100", "shift+k", "shift+k");
        }, () -> require(info(mc).deployed() == beforeDeployed,
                "R4 modified skill must not seed the bare jump double press"), 10, 3, 2);
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
            beforeDeployed = info(mc).deployed();
            beforePosition = mc.player.position();
            active.input().run();
            inputFinishedAt = Util.getMillis();
            return false;
        }
        long now = Util.getMillis();
        long settlingMillis = active.delay() * 50L;
        // xdotool blocks the render thread; catch-up ticks are not server response time.
        if (++elapsed < active.delay() || now - inputFinishedAt < settlingMillis) return false;
        int[] delta = counts();
        DeployObservation deployment = LAST_DEPLOY.get();
        boolean hasDeployment = deployment != null && deployment.sequence() > before[0];
        boolean excessPackets = false;
        for (int i = 0; i < delta.length; i++) {
            delta[i] -= before[i];
            excessPackets |= delta[i] > (i == active.packet() ? active.count() : 0);
        }
        if (!excessPackets && active.packet() == 0 && hasDeployment
                && now - deployment.sentAt() < settlingMillis) return false;
        System.out.println("WINGS_INPUT_OBSERVE " + active.name() + " packets=" + Arrays.toString(delta)
                + " elapsedMillis=" + (now - inputFinishedAt) + " ticks=" + elapsed
                + " deployRequest=" + (hasDeployment ? deployment.deployed() : "none")
                + " requestAgeMillis=" + (hasDeployment ? now - deployment.sentAt() : -1)
                + " beforeDeployed=" + beforeDeployed + " syncedDeployed=" + info(mc).deployed()
                + " position=" + mc.player.position() + " onGround=" + mc.player.onGround()
                + " velocity=" + mc.player.getDeltaMovement());
        // Restore the R4 bindings after input settles, even when its packet assertion fails.
        if (restoreBindingsAfterObservation) {
            restoreBindingsAfterObservation = false;
            restoreBindings();
        }
        for (int i = 0; i < delta.length; i++) {
            require(delta[i] == (i == active.packet() ? active.count() : 0),
                    active.name() + " packet=" + i + " actual=" + delta[i]);
        }
        if (active.packet() == 0) {
            require(hasDeployment && deployment.sequence() == before[0] + 1
                            && deployment.deployed() == !beforeDeployed,
                    active.name() + " deploy request must toggle the prior synchronized state");
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

    private static KeyMapping curiosBinding(Minecraft mc) {
        return Arrays.stream(mc.options.keyMappings)
                .filter(mapping -> mapping.getName().equals("key.curios.open.desc"))
                .findFirst().orElseThrow(() -> new AssertionError("Curios key binding missing"));
    }

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
