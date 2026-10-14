package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa")
public final class YujiIsolationServer {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static final Set<BlockPos> terrain = new HashSet<>();
    private static String stage = "waiting", item;
    private static int ticks, round, contacts, collisionTicks, crouchTicks;
    private static boolean failed, complete, ringBuilt, voiceWeaknessObserved;
    private static YujiMonster current;
    private static Panda panda;
    private static float pandaDamage;
    private static float clearedDamage;
    private static double highest, furthest;
    private static Vec3 habitatStart;
    private static ItemStack stolen;
    private static boolean enabled() { return Boolean.getBoolean("yuji.qa.isolation"); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void log(String text) throws Exception {
        Files.writeString(RESULTS.resolve("isolation-events.log"), text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("YUJI_ISOLATION " + text);
    }
    private static void pass(String name, String text) throws Exception {
        log("PASS " + name + " " + text);
        Files.writeString(RESULTS.resolve("server-" + name + ".pass"), text + "\n");
    }
    private static void next(String name) { stage = name; ticks = 0; }
    private static boolean ready(String name, ServerPlayer p) { return Files.exists(RESULTS.resolve("client-" + name + "-" + p.getGameProfile().getName() + ".pass")); }
    private static void signal(ServerPlayer p, String value) { p.sendSystemMessage(Component.literal("YUJI_ISOLATION:" + value)); }
    private static void signalBoth(ServerPlayer a, ServerPlayer b, String value) { signal(a, value); signal(b, value); }
    private static void place(ServerPlayer p, double x, double y, double z, float yaw) {
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.removeAllEffects();
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); p.setHealth(400);
        p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        p.getAbilities().flying = false; p.getAbilities().mayfly = false; p.onUpdateAbilities(); p.setNoGravity(false);
        p.teleportTo(p.serverLevel(), x, y, z, yaw, 0); p.setDeltaMovement(Vec3.ZERO); p.hurtMarked = true;
        p.getFoodData().setFoodLevel(20); p.invulnerableTime = 0;
    }
    private static void block(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        terrain.add(pos.immutable()); level.setBlockAndUpdate(pos, state);
    }
    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) block(level, pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
    }
    private static void clear(ServerLevel level) {
        if (current != null) { current.discard(); current = null; }
        if (panda != null) { panda.discard(); panda = null; }
        for (BlockPos pos : terrain) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        terrain.clear();
    }
    private static YujiMonster spawn(ServerLevel level, YujiKind kind, double x, double y, double z, boolean mobile) {
        var mob = YujiContent.MOBS.get(kind).get().create(level); mob.moveTo(x, y, z, 0, 0);
        mob.setPersistenceRequired(); mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        if (!mobile) mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        mob.setNoAi(true); level.addFreshEntity(mob); return mob;
    }
    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (!enabled()) return;
        var server = event.getServer(); var level = server.overworld(); server.setDifficulty(Difficulty.NORMAL, true);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
        // Every scenario uses real gravity and block collision on a fresh dedicated-server world.
        for (int x = -4; x <= 8; x++) for (int z = -4; z <= 8; z++) level.setChunkForced(x, z, true);
        for (BlockPos pos : BlockPos.betweenClosed(-50, 64, -50, 130, 64, 130)) level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
    }
    @SubscribeEvent public static void hurt(LivingDamageEvent event) {
        if (enabled() && panda != null && event.getSource().getEntity() == panda && event.getEntity() instanceof ServerPlayer)
            pandaDamage += event.getAmount();
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("isolation-server.failed"), error.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!enabled() || failed || complete || event.phase != TickEvent.Phase.END) return;
        var a = event.getServer().getPlayerList().getPlayerByName("YujiTester");
        var b = event.getServer().getPlayerList().getPlayerByName("YujiWitness");
        if (a == null || b == null) return;
        var level = a.serverLevel();
        try {
            // Joining the server precedes client world/render readiness; the launcher bounds startup wall time.
            if (stage.equals("waiting") && (!ready("isolation-ready", a) || !ready("isolation-ready", b))) return;
            ticks++; require(ticks < 950, "timeout: " + stage + " item=" + item + " mob=" + (current == null ? "none" : current.position()));
            ServerPlayer victim = round == 0 ? a : b, witness = round == 0 ? b : a;
            switch (stage) {
                case "waiting" -> {
                    if (ready("isolation-ready", a) && ready("isolation-ready", b)) { round = 0; beginVoice(a, b); }
                }
                case "voice-arm", "camera-arm" -> {
                    if (ready("armed-" + item, a) && ready("armed-" + item, b)) { current.setNoAi(false); next(stage.equals("voice-arm") ? "voice" : "camera"); }
                }
                case "voice" -> {
                    require(current.getTarget() == null || current.getTarget() == victim, "natural voice target is intended nearest player");
                    voiceWeaknessObserved |= victim.hasEffect(MobEffects.WEAKNESS);
                    require(!witness.hasEffect(MobEffects.WEAKNESS), "adjacent witness never receives voice weakness");
                    if (current.getTarget() == victim && !ringBuilt) {
                        fill(level, -4, 65, 5, 4, 68, 5); fill(level, -4, 65, 13, 4, 68, 13);
                        fill(level, -4, 65, 6, -4, 68, 12); fill(level, 4, 65, 6, 4, 68, 12); ringBuilt = true;
                    }
                    if (ticks == 240) {
                        require(voiceWeaknessObserved, "natural sound target actually received weakness during the observation window");
                        current.setNoAi(true); signalBoth(a, b, "check"); next("voice-proof");
                    }
                }
                case "voice-proof" -> {
                    if (!ready(item, a) || !ready(item, b) || !ready("shot-" + item, a) || !ready("shot-" + item, b)) return;
                    pass(item, "natural target=" + victim.getUUID() + "; adjacent witness=" + witness.getUUID() + "; both actual sound engines checked");
                    if (++round < 2) beginVoice(b, a); else { round = 0; beginCamera(a, b); }
                }
                case "camera" -> {
                    if (ticks == 180) {
                        require(!witness.hasEffect(YujiContent.ENTHRALLED.get()), "outside-range witness must not be enthralled");
                        signalBoth(a, b, "check"); current.setNoAi(true); next("camera-proof");
                    }
                }
                case "camera-proof" -> {
                    if (!ready(item, a) || !ready(item, b) || !ready("shot-" + item, a) || !ready("shot-" + item, b)) return;
                    pass(item, "natural source; actual client yaw/effect probes, victim=" + victim.getUUID() + "; witness=" + witness.getUUID());
                    current.discard(); next("camera-release");
                }
                case "camera-release" -> {
                    if (ticks == 30) {
                        item = "camera-clear-" + round;
                        signalBoth(a, b, "start:" + item + ":none"); next("camera-clear");
                    }
                }
                case "camera-clear" -> {
                    if (ticks == 100) { signalBoth(a, b, "check"); next("camera-clear-proof"); }
                }
                case "camera-clear-proof" -> {
                    if (!ready(item, a) || !ready(item, b) || !ready("shot-" + item, a) || !ready("shot-" + item, b)) return;
                    pass(item, "both clients remain free of effects, yaw and pitch drift after source removal");
                    if (++round < 2) beginCamera(b, a); else { round = 0; beginDoor(a, b); }
                }
                case "door-acquire" -> {
                    var doorPos = new BlockPos(0, 65, 6);
                    var closed = level.getBlockState(doorPos).setValue(DoorBlock.OPEN, false);
                    double barrier = doorPos.getZ() + closed.getCollisionShape(level, doorPos).bounds().minZ;
                    double front = current.getBoundingBox().maxZ;
                    if (current.getTarget() == a && front >= barrier - .35) {
                        require(front < barrier && level.noCollision(current), "mob reached door before it closes, without overlap");
                        require(round == 0 || current.crawling(), "second door round must actually crawl");
                        log(item + " reached barrier naturally: front=" + front + "; barrier=" + barrier + "; ticks=" + ticks);
                        door(level, false); contacts = 0; next("door-closed");
                    }
                }
                case "door-closed" -> {
                    var doorPos = new BlockPos(0, 65, 6);
                    var doorState = level.getBlockState(doorPos);
                    require(!doorState.getValue(DoorBlock.OPEN), "husk opened door");
                    // A closed door occupies a thin slice, not the entire block containing it.
                    double barrier = doorPos.getZ() + doorState.getCollisionShape(level, doorPos).bounds().minZ;
                    require(current.getBoundingBox().maxZ <= barrier + .001 && level.noCollision(current),
                            "husk crossed closed door: box=" + current.getBoundingBox() + " barrier=" + barrier);
                    if (current.horizontalCollision) contacts++;
                    require(current.getTarget() == a, "blocked husk retains the real player target");
                    if (ticks == 180) {
                        require(current.getZ() > 1.5, "husk actually approached barrier");
                        require(a.getHealth() == 400, "husk never damages player");
                        pass(item + "-closed", "z=" + current.getZ() + "; reached closed-door plane within .35 before 180 blocked ticks; crawling="
                                + current.crawling() + "; collisionTicks=" + contacts);
                        signalBoth(a, b, "shot:" + item + "-closed"); next("door-proof");
                    }
                }
                case "door-proof" -> {
                    if (ready("shot-" + item + "-closed", a) && ready("shot-" + item + "-closed", b)) { door(level, true); next("door-open"); }
                }
                case "door-open" -> {
                    if (current.getZ() > 7) {
                        require(a.getHealth() == 400, "open-door pursuit remains harmless");
                        pass(item + "-open", "same mob naturally crossed after opening: z=" + current.getZ());
                        if (++round < 2) beginDoor(a, b); else { round = 0; beginClimb(a, b); }
                    }
                }
                case "climb" -> {
                    if (current.horizontalCollision) contacts++;
                    if (current.verticalCollision && !current.onGround()) collisionTicks++;
                    highest = Math.max(highest, current.getY()); furthest = Math.max(furthest, current.getX());
                    require(!current.isNoGravity() && level.noCollision(current), "climbing keeps gravity and never overlaps solid blocks");
                    if (round < 2 && highest >= (round == 0 ? 86 : 81) && ticks % 20 == 0) {
                        // Remove the observation perch after reaching the crest. Leaving a trail of
                        // elevated perches creates extra obstacles inside the boss's 30 x 40 body.
                        int perchX = round == 1 ? 36 : 42, perchZ = round == 1 ? 36 : 0;
                        for (BlockPos pos : BlockPos.betweenClosed(perchX - 2, 93, perchZ - 2, perchX + 2, 93, perchZ + 2))
                            block(level, pos, Blocks.AIR.defaultBlockState());
                        // Keep the connected player beyond the navigator's 30-block arrival radius,
                        // but within aggro range, on the existing ground beyond both wall faces.
                        double offset = round == 1 ? 30 : 42;
                        double x = current.getX() + offset, z = round == 1 ? current.getZ() + offset : 0;
                        a.teleportTo(level, x, 65, z, 90, 0);
                    }
                    if (ticks % 20 == 0) log(item + " tick=" + ticks + " pos=" + current.position() + " wallContacts=" + contacts
                            + " ceilingContacts=" + collisionTicks + " target=" + (current.getTarget() == a) + " noBlockOverlap=" + level.noCollision(current));
                    if (round < 2 && highest >= (round == 0 ? 86 : 81) && current.getBoundingBox().minX > 19
                            && (round == 0 || current.getBoundingBox().minZ > 19)) {
                        require(contacts > 0 && level.noCollision(current), "climb uses real wall contact without phasing");
                        pass(item, "natural AI, gravity/collision; highest=" + highest + "; x=" + furthest + "; contacts=" + contacts);
                        signalBoth(a, b, "shot:" + item); next("climb-proof");
                    } else if (round == 2 && ticks == 220) {
                        require(contacts > 0 && collisionTicks > 0 && highest <= 68.001 && level.noCollision(current), "solid overhang blocks ascent without phasing: highest=" + highest);
                        pass(item, "solid ceiling respected; highest=" + highest + "; wallContacts=" + contacts + "; ceilingContacts=" + collisionTicks);
                        signalBoth(a, b, "shot:" + item); next("climb-proof");
                    }
                }
                case "climb-proof" -> {
                    if (!ready("shot-" + item, a) || !ready("shot-" + item, b)) return;
                    if (++round < 3) beginClimb(a, b); else {
                        clear(level); place(a, 0, 65, 0, 90); place(b, 40, 65, 0, 90);
                        current = spawn(level, YujiKind.LIANGSHE, 12, 65, 0, false); current.setNoAi(false);
                        panda = EntityType.PANDA.create(level); panda.moveTo(1.5, 65, 0, 90, 0); panda.setPersistenceRequired();
                        panda.setMainGene(Panda.Gene.NORMAL); panda.setHiddenGene(Panda.Gene.NORMAL); level.addFreshEntity(panda);
                        pandaDamage = 0; next("panda");
                    }
                }
                case "panda" -> {
                    if (pandaDamage > 0) {
                        require(panda.getTarget() == a && a.hasEffect(YujiContent.BETRAYED.get()), "panda attacks actual betrayed player");
                        pass("panda-attack", "natural betrayal -> actual panda damage=" + pandaDamage + "; target=" + a.getUUID());
                        signalBoth(a, b, "shot:panda-attack"); next("panda-proof");
                    }
                }
                case "panda-proof" -> {
                    if (ready("shot-panda-attack", a) && ready("shot-panda-attack", b)) { current.discard(); next("panda-clear"); }
                }
                case "panda-clear" -> {
                    if (ticks > 30 && !a.hasEffect(YujiContent.BETRAYED.get()) && panda.getTarget() != a) {
                        require(!panda.getPersistentData().contains("QihaiBetrayal"), "panda influence removed with source");
                        clearedDamage = pandaDamage; next("panda-restored");
                    }
                }
                case "panda-restored" -> {
                    require(pandaDamage == clearedDamage && panda.getTarget() != a, "no panda attacks after restored state");
                    if (ticks == 60) {
                        pass("panda-clear", "removed source clears target and influence; no lingering hostility");
                        round = 0; beginHabitat(a, b);
                    }
                }
                case "habitat-seek" -> {
                    require(level.noCollision(current) && !current.isNoAi() && !current.isNoGravity(), "habitat movement uses full-body collision, real AI and gravity");
                    if (ticks % 20 == 0) log(item + " tick=" + ticks + " pos=" + current.position() + " target=" + current.getTarget());
                    if (insideHabitat(level)) {
                        require(current.position().distanceToSqr(habitatStart) > 25, "habitat reached by actual displacement");
                        if (round == 1) require(Math.abs(current.getBbWidth() - 1.8) < .001 && Math.abs(current.getBbHeight() - 6.3) < .001,
                                "Xieyin retains giant collision dimensions");
                        next("habitat-hold");
                    }
                }
                case "habitat-hold" -> {
                    require(insideHabitat(level) && level.noCollision(current), "entire mob remains inside usable habitat, not beside its walls");
                    if (ticks == 80) {
                        require(a.hasEffect(round == 0 ? YujiContent.BETRAYED.get() : YujiContent.ENTHRALLED.get()), "habitat goal must not suppress natural aura");
                        pass(item, "real navigation " + habitatStart + " -> " + current.position() + "; full body=" + current.getBoundingBox()
                                + "; held 80 ticks; natural aura on TCP player=" + a.getUUID());
                        signalBoth(a, b, "habitat:" + item + ":" + current.getId() + ":" + (round == 0 ? 9 : 7));
                        next("habitat-proof");
                    }
                }
                case "habitat-proof" -> {
                    if (!ready(item, a) || !ready(item, b) || !ready("shot-" + item, a) || !ready("shot-" + item, b)) return;
                    if (round == 0) { round = 1; beginHabitat(a, b); }
                    else next("crouch-arm");
                }
                case "crouch-arm" -> {
                    var data = a.getPersistentData().getCompound("QihaiAfflictions");
                    if (a.hasEffect(YujiContent.ENTHRALLED.get()) && a.getEffect(YujiContent.ENTHRALLED.get()).getAmplifier() == 2) {
                        require(!a.isShiftKeyDown() && data.getInt("SneakCooldown") == 0 && data.getInt("Sneaking") == 0,
                                "partial-release probe starts at III without prior crouch or cooldown");
                        crouchTicks = 0; signal(a, "crouch:true"); next("crouch-partial");
                    }
                }
                case "crouch-partial" -> {
                    var data = a.getPersistentData().getCompound("QihaiAfflictions");
                    crouchTicks = a.isShiftKeyDown() ? crouchTicks + 1 : 0;
                    if (data.getInt("SneakCooldown") > 0) {
                        require(a.isShiftKeyDown(), "real client sends crouch input");
                        require(crouchTicks == 40, "first partial release requires exactly 40 continuous real crouch ticks: " + crouchTicks);
                        require(a.hasEffect(YujiContent.ENTHRALLED.get()) && a.getEffect(YujiContent.ENTHRALLED.get()).getAmplifier() == 1,
                                "two seconds crouching reduces III to II without clearing the live source");
                        require(current.isAlive() && current.distanceToSqr(a) <= 256, "partial release occurs within live aura");
                        pass("xieyin-crouch-partial", "TCP crouch reduced III to II at tick=" + crouchTicks + "; cooldown=" + data.getInt("SneakCooldown") + "; player=" + a.getUUID());
                        signal(a, "crouch:false"); beginTheft(a, b);
                    } else {
                        require(crouchTicks < 40 && a.hasEffect(YujiContent.ENTHRALLED.get())
                                && a.getEffect(YujiContent.ENTHRALLED.get()).getAmplifier() == 2,
                                "III remains until the 40th continuous crouch tick: " + crouchTicks);
                    }
                }
                case "theft-snatch" -> {
                    if (!current.displayedLoot().isEmpty()) {
                        require(ItemStack.matches(current.displayedLoot(), stolen), "natural theft keeps count 37 and custom NBT");
                        require(a.getInventory().getItem(0).isEmpty() && a.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "inventory stack removed but worn armor preserved");
                        pass("theft-natural-stack", "real player inventory -> natural snatch; exact stack=" + stolen.save(new CompoundTag()));
                        next("theft-sync");
                    }
                }
                case "theft-sync" -> {
                    if (ticks == 20) { signalBoth(a, b, "loot:" + current.getId()); next("theft-client-proof"); }
                }
                case "theft-client-proof" -> {
                    if (!ready("theft-loot", a) || !ready("theft-loot", b)) return;
                    CompoundTag saved = new CompoundTag(); current.addAdditionalSaveData(saved); current.discard();
                    current = spawn(level, YujiKind.TOUDAO, 0, 65, 0, false); current.readAdditionalSaveData(saved);
                    require(ItemStack.matches(current.displayedLoot(), stolen), "NBT round trip keeps complete stolen property");
                    current.setHealth(1); current.setNoAi(false); place(a, 0, 65, 2.5, 180);
                    level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false, level.getServer());
                    next("theft-kill");
                }
                case "theft-kill" -> {
                    if (ticks == 30) signal(a, "attack:" + current.getId());
                    if (!current.isAlive()) {
                        require(current.getKillCredit() == a, "restored thief was killed by the real client player");
                        next("theft-return");
                    }
                }
                case "theft-return" -> {
                    int total = level.getEntitiesOfClass(ItemEntity.class, current.getBoundingBox().inflate(8),
                            e -> ItemStack.isSameItemSameTags(e.getItem(), stolen)).stream().mapToInt(e -> e.getItem().getCount()).sum();
                    total += a.getInventory().items.stream().filter(s -> ItemStack.isSameItemSameTags(s, stolen)).mapToInt(ItemStack::getCount).sum();
                    total += b.getInventory().items.stream().filter(s -> ItemStack.isSameItemSameTags(s, stolen)).mapToInt(ItemStack::getCount).sum();
                    require(total == 37, "death restitution returns exactly 37 tagged items, not zero/duplicates: " + total);
                    if (ticks == 40) {
                        pass("theft-death-return", "real TCP attack killed NBT-restored thief; doMobLoot=false; 37 exact tagged items conserved for 40 ticks; not a disk/server restart test");
                        level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true, level.getServer());
                        clear(level); pass("isolation", "audio/camera, arrived-at-door crawl blocking, terrain, natural habitat, partial crouch release and theft restitution passed");
                        signalBoth(a, b, "done"); complete = true;
                    }
                }
                default -> throw new AssertionError(stage);
            }
        } catch (Throwable error) { fail(error); }
    }
    private static void beginVoice(ServerPlayer victim, ServerPlayer witness) {
        clear(victim.serverLevel()); place(victim, 0, 65, 9, 0); place(witness, 2, 65, 9, 0);
        current = spawn(victim.serverLevel(), YujiKind.WANGYU, 0, 65, 0, false); ringBuilt = false; voiceWeaknessObserved = false;
        item = "voice-" + round; signalBoth(victim, witness, "start:" + item + ":" + victim.getGameProfile().getName()); next("voice-arm");
    }
    private static void beginHabitat(ServerPlayer a, ServerPlayer b) {
        var level = a.serverLevel(); clear(level);
        place(a, round == 0 ? 0 : 10, 65, -2, 0); place(b, 15, 65, 3, 90);
        if (round == 0) {
            fill(level, -2, 65, 6, -2, 68, 14); fill(level, 2, 65, 6, 2, 68, 14);
            fill(level, -2, 65, 14, 2, 68, 14); fill(level, -2, 68, 6, 2, 68, 14);
        } else {
            fill(level, -1, 65, 6, -1, 72, 13); fill(level, 2, 65, 6, 2, 72, 13);
            // Isolate habitat movement from player pushing. Pull/camera are exercised in their own stages.
            place(a, 10.5, 65, -1.5, 0); place(b, 15.5, 65, 3.5, 90);
            for (BlockPos center : new BlockPos[]{new BlockPos(10, 65, -2), new BlockPos(15, 65, 3)}) {
                for (BlockPos wall : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 1, 1)))
                    if (wall.getX() != center.getX() || wall.getZ() != center.getZ())
                        block(level, wall, Blocks.GLASS.defaultBlockState());
            }
            require(level.noCollision(a) && level.noCollision(b), "habitat observers start clear inside physical enclosures");
        }
        current = spawn(level, round == 0 ? YujiKind.LIANGSHE : YujiKind.XIEYIN, round == 0 ? .5 : 1, 65, 0, true);
        habitatStart = current.position(); current.setNoAi(false);
        item = round == 0 ? "liangshe-indoor" : "xieyin-narrow"; next("habitat-seek");
    }
    private static boolean insideHabitat(ServerLevel level) {
        var box = current.getBoundingBox();
        return Math.abs(box.minY - 65) < .01 && box.maxY <= (round == 0 ? 68 : 73)
                && box.minX >= (round == 0 ? -1 : 0) && box.maxX <= 2 && box.minZ >= 6 && box.maxZ <= 13
                && current.getZ() >= (round == 0 ? 9 : 7)
                && (round != 0 || level.getBlockState(BlockPos.containing(current.getX(), 68, current.getZ())).is(Blocks.SMOOTH_QUARTZ));
    }
    private static void beginTheft(ServerPlayer a, ServerPlayer b) {
        clear(a.serverLevel()); place(a, 0, 65, 1.6, 180); place(b, 8, 65, 3, 90);
        stolen = new ItemStack(Items.DIAMOND, 37); stolen.getOrCreateTag().putString("YujiReceipt", "a70-real-client-property");
        a.getInventory().setItem(0, stolen.copy()); a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        a.containerMenu.broadcastChanges();
        current = spawn(a.serverLevel(), YujiKind.TOUDAO, 0, 65, 0, false); current.setNoAi(false);
        next("theft-snatch");
    }
    private static void beginCamera(ServerPlayer victim, ServerPlayer witness) {
        clear(victim.serverLevel()); place(victim, 12, 65, 0, 0); place(witness, 22, 65, 0, 0);
        current = spawn(victim.serverLevel(), YujiKind.XIEYIN, 0, 65, 0, false);
        item = "camera-" + round; signalBoth(victim, witness, "start:" + item + ":" + victim.getGameProfile().getName()); next("camera-arm");
    }
    private static void door(ServerLevel level, boolean open) {
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, open);
        block(level, new BlockPos(0, 65, 6), state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        block(level, new BlockPos(0, 66, 6), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }
    private static void beginDoor(ServerPlayer a, ServerPlayer b) {
        clear(a.serverLevel()); place(a, .5, 65, 13, 180); place(b, 8, 65, 8, 90);
        var level = a.serverLevel(); fill(level, -1, 65, 0, -1, 68, 16); fill(level, 1, 65, 0, 1, 68, 16);
        // Seal both ends so a path around the side walls cannot be mistaken for crossing the door.
        fill(level, -1, 65, -1, 1, 68, -1); fill(level, -1, 65, 17, 1, 68, 17);
        fill(level, 0, 67, 6, 0, 68, 6); door(level, true);
        current = spawn(level, YujiKind.YUCHI, .5, 65, 1, true);
        require(level.noCollision(current) && level.noCollision(a), "door fixture starts with both bodies clear of walls");
        if (round == 1) current.setHealth(current.getMaxHealth() * .2F);
        current.setNoAi(false); item = "door-" + (round == 0 ? "standing" : "crawling"); next("door-acquire");
    }
    private static void beginClimb(ServerPlayer a, ServerPlayer b) {
        clear(a.serverLevel()); var level = a.serverLevel();
        int top = round == 0 ? 85 : 80;
        fill(level, 17, 65, -45, 18, top, 45);
        if (round == 1) fill(level, -45, 65, 17, 45, top, 18);
        if (round == 2) fill(level, -20, 108, -20, 20, 108, 20);
        int x = round == 1 ? 36 : 42, z = round == 1 ? 36 : 0;
        fill(level, x - 2, 93, z - 2, x + 2, 93, z + 2);
        place(a, x, 94, z, 90); place(b, -30, 65, -30, -45); b.setGameMode(GameType.CREATIVE);
        current = spawn(level, YujiKind.SHIFOTOU, 0, 65, 0, true); current.setNoAi(false);
        highest = 65; furthest = 0; contacts = 0; collisionTicks = 0;
        item = new String[]{"climb-wall", "climb-corner", "climb-overhang"}[round]; next("climb");
    }
}
