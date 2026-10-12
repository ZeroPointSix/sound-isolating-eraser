package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa")
public final class YujiBoundaryServer {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static String stage = "waiting";
    private static int elapsed, firstHits, secondHits, contactIndex;
    private static boolean failed, complete;
    private static YujiMonster current, source;
    private static final ArrayList<Integer> hitTicks = new ArrayList<>();
    private static final ArrayList<Float> damage = new ArrayList<>();
    private static final String[] CONTACT = {"side", "upper", "outside-side", "above"};
    // Boss body is 30x40 (modelScale 5); offsets straddle its edges like the matching GameTests.
    private static final double[][] OFFSETS = {{15.2, 0}, {0, 39.8}, {15.31, 0}, {0, 40.01}};

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < .001, message + ": " + actual + " != " + expected);
    }
    private static void log(String message) throws Exception {
        Files.writeString(RESULTS.resolve("boundary-events.log"), message + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("YUJI_BOUNDARY " + message);
    }
    private static void pass(String name, String message) throws Exception {
        log("PASS " + name + " " + message);
        Files.writeString(RESULTS.resolve("server-" + name + ".pass"), message + "\n");
    }
    private static boolean ready(String name) { return Files.exists(RESULTS.resolve("client-" + name + ".pass")); }
    private static void signal(ServerPlayer p, String command) {
        p.sendSystemMessage(Component.literal("YUJI_BOUNDARY:" + command));
    }
    private static void next(String name) { stage = name; elapsed = 0; }
    private static void position(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch); p.setDeltaMovement(Vec3.ZERO); p.hurtMarked = true;
    }
    private static void setupPlayer(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); p.getInventory().clearContent();
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); p.setHealth(400); p.removeAllEffects();
        p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        // Hold exact geometry in survival mode without disabling damage or body collision.
        p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        p.invulnerableTime = 0; p.getFoodData().setFoodLevel(20);
    }
    private static YujiMonster spawn(ServerPlayer p, YujiKind kind, double x) {
        YujiMonster mob = YujiContent.MOBS.get(kind).get().create(p.serverLevel());
        mob.moveTo(x, 65, 0, 0, 0); mob.setNoGravity(true); mob.setPersistenceRequired();
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        p.serverLevel().addFreshEntity(mob); return mob;
    }
    private static void assertGrudge(ServerPlayer expected) throws Exception {
        CompoundTag tag = new CompoundTag(); current.addAdditionalSaveData(tag);
        log("grudge stage=" + stage + " saved=" + (tag.hasUUID("YujiGrudge") ? tag.getUUID("YujiGrudge") : "none")
                + " target=" + (current.getTarget() == null ? "none" : current.getTarget().getUUID())
                + " distance=" + current.distanceTo(expected) + " mobPos=" + current.position() + " playerPos=" + expected.position()
                + " firstHits=" + firstHits + " secondHits=" + secondHits);
        require(tag.hasUUID("YujiGrudge") && tag.getUUID("YujiGrudge").equals(expected.getUUID())
                && current.getTarget() == expected, "valid original grudge retained during " + stage);
    }
    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (!Boolean.getBoolean("yuji.qa.boundaries")) return;
        var server = event.getServer(); var level = server.overworld(); server.setDifficulty(Difficulty.NORMAL, true);
        level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        for (BlockPos p : BlockPos.betweenClosed(-16, 64, -16, 140, 64, 24)) level.setBlockAndUpdate(p, Blocks.SMOOTH_QUARTZ.defaultBlockState());
    }
    @SubscribeEvent public static void hurt(LivingDamageEvent event) {
        if (!Boolean.getBoolean("yuji.qa.boundaries") || current == null || failed || complete) return;
        try {
            if (event.getEntity() == current && event.getSource().getEntity() instanceof ServerPlayer p) {
                if (p.getGameProfile().getName().equals("YujiTester")) firstHits++; else secondHits++;
                log("playerAttack stage=" + stage + " player=" + p.getGameProfile().getName() + " uuid=" + p.getUUID() + " damage=" + event.getAmount());
            }
            if ((stage.equals("slash") || stage.equals("contact")) && event.getEntity() instanceof ServerPlayer p
                    && p.getGameProfile().getName().equals("YujiTester") && event.getSource().getEntity() == current) {
                hitTicks.add(current.tickCount); damage.add(event.getAmount());
                log("actualDamage stage=" + stage + " contact=" + (stage.equals("contact") ? CONTACT[contactIndex] : "n/a")
                        + " tick=" + current.tickCount + " amount=" + event.getAmount() + " attackAttribute=" + current.getAttributeValue(Attributes.ATTACK_DAMAGE)
                        + " intersects=" + current.getBoundingBox().intersects(p.getBoundingBox()) + " player=" + p.getUUID());
            }
        } catch (Throwable error) { fail(error); }
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("boundary-server.failed"), error.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("yuji.qa.boundaries") || failed || complete) return;
        var first = event.getServer().getPlayerList().getPlayerByName("YujiTester");
        var second = event.getServer().getPlayerList().getPlayerByName("YujiWitness");
        if (first == null || second == null) return;
        if (event.phase == TickEvent.Phase.START) {
            if (stage.equals("contact")) {
                current.setPos(0, 65, 0); current.setDeltaMovement(Vec3.ZERO);
                position(first, OFFSETS[contactIndex][0], 65 + OFFSETS[contactIndex][1], 0, 90, 0);
            }
            return;
        }
        try {
            elapsed++;
            require(elapsed < 400, "phase timed out: " + stage);
            switch (stage) {
                case "waiting" -> {
                    if (!ready("ready-YujiTester") || !ready("ready-YujiWitness")) return;
                    setupPlayer(first); setupPlayer(second);
                    current = spawn(first, YujiKind.CHENNU, 0);
                    position(first, 0, 65, 4, 180, 0); position(second, 4, 65, 0, 90, 0);
                    signal(first, "attack:" + current.getId()); next("grudge-first");
                }
                case "grudge-first" -> {
                    if (firstHits > 0) { assertGrudge(first); next("grudge-second"); }
                }
                case "grudge-second" -> {
                    if (elapsed == 30) signal(second, "attack:" + current.getId());
                    if (secondHits > 0) {
                        assertGrudge(first); pass("grudge-two-clients", "first=" + first.getUUID() + ", second=" + second.getUUID() + ", original retained after accepted second hit");
                        signal(first, "shot:grudge-two-clients"); next("grudge-proof");
                    }
                }
                case "grudge-proof" -> {
                    if (!ready("grudge-two-clients-YujiTester")) return;
                    CompoundTag tag = new CompoundTag(); current.addAdditionalSaveData(tag); current.discard();
                    current = spawn(first, YujiKind.CHENNU, 0); current.readAdditionalSaveData(tag);
                    secondHits = 0; next("grudge-reload");
                }
                case "grudge-reload" -> {
                    if (elapsed == 30) signal(second, "attack:" + current.getId());
                    if (secondHits > 0) {
                        assertGrudge(first); pass("grudge-reload", "NBT round-trip retains first UUID and target after second connected client attacks");
                        position(first, 128, 65, 0, 90, 0); secondHits = 0; next("grudge-128");
                    }
                }
                case "grudge-128" -> {
                    if (elapsed == 30) signal(second, "attack:" + current.getId());
                    if (secondHits > 0) {
                        assertGrudge(first); pass("grudge-128", "original target remains valid at exactly 128 blocks");
                        position(first, 129, 65, 0, 90, 0); secondHits = 0; next("grudge-release");
                    }
                }
                case "grudge-release" -> {
                    if (elapsed == 30) signal(second, "attack:" + current.getId());
                    if (secondHits > 0) {
                        assertGrudge(second); pass("grudge-release", "new attacker accepted after original player leaves 128 blocks");
                        current.discard(); setupPlayer(first); second.setGameMode(GameType.CREATIVE);
                        position(second, 12, 70, 12, 135, 12); position(first, 0, 65, 1.6, 180, 0);
                        current = spawn(first, YujiKind.SHASHENG, 0); current.setNoAi(true);
                        source = spawn(first, YujiKind.LIANGSHE, 10); next("betrayal");
                    }
                }
                case "betrayal" -> {
                    if (first.hasEffect(YujiContent.BETRAYED.get())
                            && Math.abs(current.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.9) < .001) {
                        close(current.getAttributeValue(Attributes.ATTACK_DAMAGE), 3.9, "natural betrayal applies +30 percent");
                        hitTicks.clear(); damage.clear(); first.setHealth(400); current.setNoAi(false); next("slash");
                    }
                }
                case "slash" -> {
                    if (hitTicks.size() >= 2) {
                        require(hitTicks.size() == 2, "exactly two observed slash hits");
                        close(damage.get(0), 3.9, "first actual slash"); close(damage.get(1), 3.9, "second actual slash");
                        require(hitTicks.get(1) - hitTicks.get(0) == 6, "second slash follows six ticks after first");
                        close(400 - first.getHealth(), 7.8, "real connected player loses 7.8 health");
                        pass("betrayal-double-slash", "damage=" + damage + ", ticks=" + hitTicks + ", actualHealthLoss=" + (400 - first.getHealth()));
                        current.setNoAi(true); source.setNoAi(true); position(first, 0, 65, 5.5, 180, 0);
                        signal(first, "shot:betrayal-double-slash"); next("slash-proof");
                    }
                }
                case "slash-proof" -> {
                    if (!ready("betrayal-double-slash-YujiTester")) return;
                    current.discard(); source.discard(); setupPlayer(first);
                    current = spawn(first, YujiKind.SHIFOTOU, 0);
                    // Isolate body contact from unrelated melee skills while retaining normal server AI ticks.
                    current.goalSelector.removeAllGoals(goal -> true);
                    contactIndex = 0; beginContact(first, second);
                }
                case "contact" -> {
                    if (elapsed < 45 || !ready("boss-" + CONTACT[contactIndex] + "-YujiWitness")) return;
                    boolean touching = contactIndex < 2;
                    require(current.getBoundingBox().intersects(first.getBoundingBox()) == touching, "geometry matches contact control");
                    require(hitTicks.size() == (touching ? 2 : 0), "two contact hits in forty-five ticks, or none outside body: " + hitTicks);
                    for (float amount : damage) close(amount, 10, "contact deals ten damage");
                    if (touching) require(hitTicks.get(0) == 20 && hitTicks.get(1) == 40, "contact damage once each second");
                    close(400 - first.getHealth(), touching ? 20 : 0, "actual contact health loss");
                    pass("boss-" + CONTACT[contactIndex], "offset=" + OFFSETS[contactIndex][0] + "," + OFFSETS[contactIndex][1]
                            + ", intersects=" + touching + ", damage=" + damage + ", ticks=" + hitTicks + ", healthLoss=" + (400 - first.getHealth()));
                    if (++contactIndex < CONTACT.length) beginContact(first, second);
                    else {
                        current.discard(); pass("boundaries", "All three independent findings passed targeted two-real-client probes; not sound/camera isolation coverage.");
                        signal(first, "done"); signal(second, "done"); complete = true;
                    }
                }
                default -> throw new AssertionError(stage);
            }
        } catch (Throwable error) { fail(error); }
    }
    private static void beginContact(ServerPlayer first, ServerPlayer second) throws Exception {
        first.setHealth(400); first.invulnerableTime = 0; first.removeAllEffects();
        position(first, OFFSETS[contactIndex][0], 65 + OFFSETS[contactIndex][1], 0, 90, 0);
        current.tickCount = 0; hitTicks.clear(); damage.clear(); next("contact");
        log("contactSetup name=" + CONTACT[contactIndex] + " player=" + first.getUUID() + " bossBody=" + current.getBoundingBox());
        signal(second, "shot:boss-" + CONTACT[contactIndex]);
    }
}
