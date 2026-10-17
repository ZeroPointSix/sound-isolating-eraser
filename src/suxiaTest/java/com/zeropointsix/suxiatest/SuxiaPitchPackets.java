package com.zeropointsix.suxiatest;

import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "suxia_qa", value = Dist.CLIENT)
public final class SuxiaPitchPackets {
    private static final Path RESULTS = Path.of(System.getProperty("suxia.qa.results"));
    private static boolean tested;
    private static long sequence;
    private static final int PROBE_ID = Integer.MAX_VALUE - 100;

    private static void append(String file, String line) {
        try {
            Files.writeString(RESULTS.resolve(file), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static String state(LivingEntity mob) {
        try {
            var pitch = LivingEntity.class.getDeclaredField("lerpXRot");
            var steps = LivingEntity.class.getDeclaredField("lerpSteps");
            pitch.setAccessible(true); steps.setAccessible(true);
            return " id=" + mob.getId() + " age=" + mob.tickCount + " pitch=" + mob.getXRot()
                    + " pending=" + pitch.getDouble(mob) + " steps=" + steps.getInt(mob);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static void trace(String edge, ClientboundMoveEntityPacket packet) {
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.level == null) return;
        if (packet.getEntity(mc.level) instanceof SuxiaSummonEntity mob) {
            append("pitch-packets.log", ++sequence + " " + edge + " " + packet.getClass().getSimpleName()
                    + " rotation=" + packet.hasRotation() + " wirePitch=" + packet.getxRot() * 360.0F / 256
                    + state(mob));
        }
    }

    private static void settle(LivingEntity mob) { for (int i = 0; i < 3; i++) mob.aiStep(); }
    private static ClientboundMoveEntityPacket.Rot rot(float pitch) {
        return new ClientboundMoveEntityPacket.Rot(PROBE_ID, (byte) 0,
                (byte) Mth.floor(pitch * 256 / 360), true);
    }
    private static ClientboundMoveEntityPacket.Pos pos() {
        return new ClientboundMoveEntityPacket.Pos(PROBE_ID, (short) 1, (short) 0, (short) 0, true);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("suxia.qa.pitch") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SuxiaSummonEntity mob) {
            append("pitch-packets.log", ++sequence + " TICK" + state(mob));
        }
        if (tested) return;
        tested = true;
        int failures = 0;
        String[] cases = {"rot-only", "rot-pos", "pos-rot", "rot-tick-pos", "positive-rot-pos",
                "return-zero", "pending-return-zero", "pending-pos-return-zero", "pending-return-zero-pos",
                "posrot-overrides", "teleport-pos", "combat-rot-pos", "idle-combat", "combat-idle",
                "expired-lerp", "vanilla-control"};
        for (String test : cases) {
            if (mc.level.getEntity(PROBE_ID) != null) {
                append("pitch-regression.failed", "Probe ID is already occupied");
                throw new AssertionError("probe ID collision");
            }
            append("pitch-packets.log", "CASE_BEGIN " + test);
            Mob mob = test.equals("vanilla-control") ? EntityType.ZOMBIE.create(mc.level)
                    : ModEntities.SUXIA_SUMMON.get().create(mc.level);
            if (mob == null) throw new AssertionError("missing summon type");
            mob.setId(PROBE_ID);
            mob.setPos(mc.player.getX(), mc.player.getY() + 10, mc.player.getZ());
            mob.getPositionCodec().setBase(mob.position());
            mob.setNoGravity(true);
            if (mob instanceof SuxiaSummonEntity summon) summon.setOwnerUUID(mc.player.getUUID());
            mob.setAggressive(test.startsWith("combat"));
            mc.level.putNonPlayerEntity(PROBE_ID, mob);
            try {
                if (mc.level.getEntity(PROBE_ID) != mob) {
                    append("pitch-regression.failed", "Probe was not registered under packet ID");
                    throw new AssertionError("probe packet ID");
                }
                var listener = mc.player.connection;
                float target = test.startsWith("positive") ? 35 : -20;
                if (test.equals("return-zero")) { mob.setXRot(-20); target = 0; }
                if (test.equals("pos-rot")) listener.handleMoveEntity(pos());
                if (test.equals("teleport-pos")) {
                    mob.setXRot(target);
                    var packet = new ClientboundTeleportEntityPacket(mob);
                    mob.setXRot(0);
                    listener.handleTeleportEntity(packet);
                } else listener.handleMoveEntity(rot(target));
                if (test.equals("rot-tick-pos")) mob.aiStep();
                if (test.equals("idle-combat")) mob.setAggressive(true);
                if (test.equals("combat-idle")) mob.setAggressive(false);
                if (test.equals("expired-lerp")) {
                    settle(mob);
                    target = 35;
                    mob.setXRot(target);
                }
                if (test.equals("posrot-overrides")) {
                    target = 35;
                    listener.handleMoveEntity(new ClientboundMoveEntityPacket.PosRot(PROBE_ID,
                            (short) 1, (short) 0, (short) 0, (byte) 0, (byte) Mth.floor(target * 256 / 360), true));
                }
                if (!test.equals("rot-only") && !test.equals("pos-rot")
                        && !test.equals("pending-return-zero") && !test.equals("pending-return-zero-pos")) {
                    listener.handleMoveEntity(pos());
                }
                if (test.startsWith("pending-")) {
                    target = 0;
                    listener.handleMoveEntity(rot(0));
                    if (test.equals("pending-return-zero-pos")) listener.handleMoveEntity(pos());
                }
                boolean interpolation = true;
                if (test.equals("rot-pos")) {
                    interpolation = mob.getXRot() == 0;
                    mob.aiStep();
                    float first = mob.getXRot();
                    interpolation &= Math.abs(first - rot(target).getxRot() * 360.0F / 256 / 3) < 0.001;
                    append("pitch-regression.log", "INTERPOLATION first=" + first + " preserved=" + interpolation);
                }
                if (test.equals("vanilla-control")) target = 0;
                settle(mob);
                boolean passed = interpolation && Math.abs(mob.getXRot() - target) < 1.5;
                if (!passed) failures++;
                append("pitch-regression.log", test + " " + (passed ? "PASS" : "FAIL") + " expected=" + target + state(mob));
            } finally {
                mc.level.removeEntity(mob.getId(), Entity.RemovalReason.DISCARDED);
                if (mc.level.getEntity(PROBE_ID) != null) {
                    append("pitch-regression.failed", "Probe leaked into the live client test");
                    throw new AssertionError("probe cleanup");
                }
                append("pitch-packets.log", "CASE_END " + test);
            }
        }
        append("pitch-regression.log", "CASES=" + cases.length + " FAILURES=" + failures);
        if (failures == 0) append("pitch-regression.pass", "All " + cases.length + " actual client handler cases passed.");
        if (failures != 0 && !Boolean.getBoolean("suxia.qa.pitchDiagnoseOnly")) {
            append("pitch-regression.failed", "Packet regression failures=" + failures);
        }
    }
}
