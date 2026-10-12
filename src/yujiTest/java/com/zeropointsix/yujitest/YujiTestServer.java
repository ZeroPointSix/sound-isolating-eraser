package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import com.zeropointsix.eraser.yuji.BoneSpike;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "yuji_qa")
public final class YujiTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static int index = -1;
    private static boolean failed, complete;
    private static YujiMonster current;
    private static BoneSpike spike;
    private static String mode = "ready";
    private static int elapsed, mobHits, playerHits, summonsObserved;
    private static double chantRange;
    private static boolean effectObserved, spikeStuck;
    private static YujiMonster supportAlly;
    private static boolean supportBuffed, summonRefilled, golemTargeted;
    private static final HashSet<UUID> firstSummons = new HashSet<>();
    private static IronGolem golem;
    private static int golemHits;
    private static Vec3 initialPosition, initialPlayer;
    private static final ArrayList<BlockPos> walls = new ArrayList<>();

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void pass(String name, String message) throws Exception {
        Files.writeString(RESULTS.resolve("server-" + name + ".pass"), message + "\n");
        System.out.println("YUJI_QA_PASS " + name + ": " + message);
    }

    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (Boolean.getBoolean("yuji.qa.boundaries")) return;
        var level = event.getServer().overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        // Group combat and the 30-block boss must not spill off the old single-mob platform.
        for (BlockPos pos : BlockPos.betweenClosed(-64, 64, -64, 64, 64, 64)) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
    }

    private static void signal(ServerPlayer player, String phase) {
        player.sendSystemMessage(Component.literal("YUJI_QA:" + phase));
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("yuji.qa.boundaries")) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getGameProfile().getName().equals("YujiTester")) return;
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), .5, 66, 10.5, 180, 0);
        signal(player, "ready");
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (Boolean.getBoolean("yuji.qa.boundaries")) return;
        if (event.phase != TickEvent.Phase.END || failed || complete) return;
        var player = event.getServer().getPlayerList().getPlayerByName("YujiTester");
        if (player == null) return;
        try {
            switch (mode) {
                case "ready" -> { if (clientDone("ready")) advance(player); }
                case "gallery" -> { if (clientDone(current.kind().id())) startAi(player); }
                case "ai" -> {
                    elapsed++;
                    if (player.getHealth() < 100) player.setHealth(400);
                    effectObserved |= switch (current.kind()) {
                        case EKOU -> chantRange >= 3;
                        case WANGYU -> player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
                        case LIANGSHE -> player.hasEffect(YujiContent.BETRAYED.get());
                        case XIEYIN -> player.hasEffect(YujiContent.ENTHRALLED.get());
                        case SHIFOTOU -> player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
                        default -> false;
                    };
                    if (current.kind() == YujiKind.QIYU) observeSupport(player);
                    if (elapsed == (current.kind() == YujiKind.QIYU ? 500 : 180)) finishAi(player);
                }
                case "golem" -> {
                    elapsed++;
                    golemTargeted |= current.getTarget() == golem;
                    if (elapsed == 180) {
                        require(golemTargeted && golemHits > 0 && golem.getHealth() < golem.getMaxHealth(),
                                "hostile boss selects and damages an unprovoking iron golem");
                        pass("boss-iron-golem", "naturalTarget=" + golemTargeted + ", damageEvents=" + golemHits
                                + ", actualHealthLoss=" + (golem.getMaxHealth() - golem.getHealth()));
                        golem.discard(); golem = null;
                        startAudio(player);
                    }
                }
                case "audio" -> {
                    if (++elapsed == 10) {
                        current.invulnerableTime = 0;
                        current.hurt(current.damageSources().genericKill(), Float.MAX_VALUE);
                    }
                    require(elapsed < 160, "actual death sound playback timed out: " + current.kind());
                    if (clientDone("audio-" + current.kind().id())) advance(player);
                }
                case "bone-gallery" -> {
                    if (clientDone("bone_spike")) {
                        for (BlockPos p : BlockPos.betweenClosed(-1, 65, 5, 1, 67, 5)) {
                            player.serverLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                        }
                        player.teleportTo(player.serverLevel(), 2.3, 65.2, 3, 40, 18);
                        signal(player, "bone_spike_stuck");
                        spike.shoot(0, 0, 1, 1.6F, 0); elapsed = 0; mode = "bone-flight";
                    }
                }
                case "bone-flight" -> {
                    elapsed++;
                    if (elapsed == 10) { require(spike.stuck(), "real projectile collides with arena wall"); spikeStuck = true; }
                    if (elapsed == 215) {
                        require(spikeStuck && spike.isRemoved(), "stuck projectile expires after 200 server ticks");
                        require(clientDone("bone_spike_stuck"), "embedded spike is visibly rendered with its impact sound");
                        pass("bone_spike", "Real projectile hit wall, remained visibly embedded, played vanilla impact sound and expired after 200 ticks.");
                        pass("gallery", "11 real-player AI/collision/audio probes and rendered projectile completed.");
                        complete = true; signal(player, "cleanup");
                    }
                }
                default -> throw new AssertionError(mode);
            }
        } catch (Throwable failure) {
            failed = true;
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), failure.toString()); } catch (Exception ignored) { }
        }
    }

    private static boolean clientDone(String phase) { return Files.exists(RESULTS.resolve("client-" + phase + ".pass")); }

    private static void observeSupport(ServerPlayer player) {
        supportBuffed |= supportAlly.hasEffect(MobEffects.DAMAGE_BOOST) && supportAlly.hasEffect(MobEffects.MOVEMENT_SPEED)
                && supportAlly.hasEffect(MobEffects.DAMAGE_RESISTANCE) && supportAlly.hasEffect(MobEffects.REGENERATION);
        var summons = player.serverLevel().getEntitiesOfClass(YujiMonster.class, current.getBoundingBox().inflate(32),
                y -> y.isAlive() && current.getUUID().equals(y.summoner()));
        summonsObserved = Math.max(summonsObserved, summons.size());
        require(summons.size() <= YujiMonster.SUMMON_CAP, "natural summon count stays capped");
        for (YujiMonster child : summons) {
            require(child.kind() != YujiKind.QIYU && !child.kind().giant(), "only other eligible kinds are summoned");
            if (elapsed <= 200) firstSummons.add(child.getUUID());
            else summonRefilled |= !firstSummons.contains(child.getUUID());
        }
        if (elapsed == 200) {
            require(!summons.isEmpty(), "first natural summon wave exists");
            summons.get(0).discard();
        }
    }

    private static void advance(ServerPlayer player) throws Exception {
        if (current != null) current.discard();
        for (BlockPos p : walls) player.serverLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        walls.clear();
        for (var item : player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(64))) item.discard();
        for (var projectile : player.serverLevel().getEntitiesOfClass(BoneSpike.class, player.getBoundingBox().inflate(128))) projectile.discard();
        player.removeAllEffects();
        player.setDeltaMovement(Vec3.ZERO); player.hurtMarked = true;
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true; player.onUpdateAbilities();
        player.getInventory().clearContent();
        if (++index == YujiKind.values().length) {
            current = null;
            spike = YujiContent.BONE_SPIKE.get().create(player.serverLevel());
            spike.setNoGravity(true); spike.moveTo(.5, 66, .5, 0, 0);
            player.serverLevel().addFreshEntity(spike);
            player.teleportTo(player.serverLevel(), 1.5, 65, 1.5, 135, 23.7F);
            mode = "bone-gallery"; signal(player, "bone_spike"); return;
        }
        YujiKind kind = YujiKind.values()[index];
        current = YujiContent.MOBS.get(kind).get().create(player.serverLevel());
        current.setNoAi(true); current.setPersistenceRequired(); current.moveTo(.5, 65, .5, 0, 0); current.setYBodyRot(0);
        player.serverLevel().addFreshEntity(current);
        // Frame the actual (model-scaled) body: giants like xieyin/shifotou are far larger than their base profile.
        double height = current.getBbHeight(), width = current.getBbWidth();
        // Server view-distance is 3 chunks (48 blocks), so giants are framed just inside tracking range.
        double distance = Math.max(5.5, Math.min(46, Math.max(height * 1.7, width * 1.9)));
        double y = Math.max(65, 65 + height * .5 - 1.62);
        // Aim a little below centre on giants so the near-side legs and shadow stay off the bottom edge.
        float pitch = (float) Math.toDegrees(Math.atan2(y + 1.62 - (65 + height * (height > 10 ? .42 : .5)), distance));
        player.teleportTo(player.serverLevel(), .5, y, .5 + distance, 180, pitch);
        player.getInventory().setItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(YujiContent.id(kind.id() + "_spawn_egg"))));
        player.getInventory().selected = 0;
        mode = "gallery"; signal(player, kind.id());
        System.out.println("YUJI_QA_SERVER kind=" + kind.id() + " health=" + current.getHealth() + " height=" + current.getBbHeight());
    }

    private static void startAi(ServerPlayer player) {
        initialPosition = current.position();
        current.move(MoverType.SELF, new Vec3(0, -1, 0));
        require(Math.abs(current.getY() - 65) < .01 && current.verticalCollision, "physical floor collision: " + current.kind());
        require(current.getBoundingBox().getXsize() > 0 && current.getBoundingBox().getYsize() > 0, "nonempty collision body");
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); player.setHealth(400);
        player.setGameMode(GameType.SURVIVAL); player.getAbilities().flying = false; player.onUpdateAbilities();
        player.getFoodData().setFoodLevel(20); player.getInventory().clearContent();
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 17));
        player.teleportTo(player.serverLevel(), .5, 65, current.kind() == YujiKind.CHENNU ? 4 : 8.5,
                current.kind() == YujiKind.XIEYIN ? 0 : 180, 0);
        initialPlayer = player.position();
        if (current.kind() == YujiKind.WANGYU) {
            for (BlockPos p : BlockPos.betweenClosed(-5, 65, 3, 5, 68, 14)) {
                if (p.getX() == -5 || p.getX() == 5 || p.getZ() == 14) {
                    walls.add(p.immutable()); player.serverLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                }
            }
        }
        if (current.kind() == YujiKind.TANYU) {
            var item = new ItemEntity(player.serverLevel(), current.getX(), current.getY(), current.getZ(), new ItemStack(Items.ROTTEN_FLESH, 3));
            item.setNoPickUpDelay(); item.setDeltaMovement(Vec3.ZERO); player.serverLevel().addFreshEntity(item);
        }
        if (current.kind() == YujiKind.QIYU) {
            supportAlly = YujiContent.MOBS.get(YujiKind.YUCHI).get().create(player.serverLevel());
            supportAlly.setNoAi(true); supportAlly.moveTo(current.position().add(2, 0, 0));
            player.serverLevel().addFreshEntity(supportAlly);
            supportBuffed = false; summonRefilled = false; firstSummons.clear();
        }
        if (current.kind() == YujiKind.CHENNU) require(current.getTarget() == null, "giant starts neutral before real player attack");
        current.setNoAi(false); elapsed = 0; mobHits = 0; playerHits = 0; effectObserved = false; chantRange = 0; summonsObserved = 0;
        mode = "ai"; signal(player, "ai-" + current.kind().id());
    }

    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (mode.equals("golem") && event.getEntity() == golem && event.getSource().getEntity() == current) golemHits++;
        if (!mode.equals("ai") || current == null) return;
        if (event.getEntity() == current && event.getSource().getEntity() instanceof ServerPlayer) playerHits++;
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().getEntity() == current) {
            mobHits++;
            // 恶口: a magic hit landed from range while chanting proves the ranged chant, not a melee claw.
            if (current.kind() == YujiKind.EKOU && current.action().equals("chant")
                    && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC))
                chantRange = Math.max(chantRange, current.distanceTo(p));
        }
    }

    private static void finishAi(ServerPlayer player) throws Exception {
        double moved = current.position().distanceTo(initialPosition);
        require(current.getTarget() == player, "AI naturally tracks connected survival player: " + current.kind()
                + ", mob=" + current.position() + ", player=" + player.position() + ", target=" + current.getTarget());
        switch (current.kind()) {
            case YUCHI -> require(moved > .3 && mobHits == 0, "passive follow without direct attack");
            case SHASHENG -> require(mobHits >= 2, "real player receives double slash");
            case TOUDAO -> require(!current.displayedLoot().isEmpty() && player.getInventory().getItem(1).isEmpty(), "real inventory stack stolen");
            case EKOU -> require(effectObserved, "ranged chant damages real player from >= 3 blocks: " + chantRange);
            case WANGYU, LIANGSHE -> require(effectObserved, "AI applies source-specific status");
            case XIEYIN -> require(effectObserved && player.position().distanceTo(initialPlayer) > .3, "actual client moved by enthrallment");
            case QIYU -> require(moved > .3 && mobHits > 0 && supportBuffed && summonRefilled
                            && summonsObserved > 0 && summonsObserved <= YujiMonster.SUMMON_CAP,
                    "flute buffs allies and replenishes natural summons: buffs=" + supportBuffed + ", refill=" + summonRefilled);
            case TANYU -> require(current.layers() >= 3 && mobHits > 0, "real dropped flesh consumed and real player attacked");
            case CHENNU -> {
                CompoundTag tag = new CompoundTag(); current.addAdditionalSaveData(tag);
                require(playerHits > 0 && tag.hasUUID("YujiGrudge") && tag.getUUID("YujiGrudge").equals(player.getUUID()), "real client attack creates persisted grudge");
                require(mobHits > 0, "giant retaliates against real player");
            }
            case SHIFOTOU -> require(effectObserved && mobHits > 0, "boss gaze and combat affect connected player");
        }
        pass("ai-" + current.kind().id(), "realPlayer=" + player.getUUID() + ", floorCollision=true, aiTarget=true, moved=" + moved
                + ", playerDamageEvents=" + mobHits + ", hitsByPlayer=" + playerHits + ", statusObserved=" + effectObserved + ", chantRange=" + chantRange
                + ", summons=" + summonsObserved + (current.kind() == YujiKind.QIYU ? ", allyFourBuffs=" + supportBuffed + ", replenished=" + summonRefilled : ""));
        for (YujiMonster summoned : player.serverLevel().getEntitiesOfClass(YujiMonster.class, current.getBoundingBox().inflate(64),
                y -> current.getUUID().equals(y.summoner()))) summoned.discard();
        if (supportAlly != null) { supportAlly.discard(); supportAlly = null; }
        if (current.kind() == YujiKind.SHIFOTOU) {
            player.setGameMode(GameType.CREATIVE);
            golem = EntityType.IRON_GOLEM.create(player.serverLevel());
            golem.setNoAi(true); golem.moveTo(current.position().add(0, 0, current.getBbWidth() / 2 + 2));
            golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); golem.setHealth(400);
            player.serverLevel().addFreshEntity(golem);
            golemTargeted = false; golemHits = 0; elapsed = 0; mode = "golem";
            return;
        }
        startAudio(player);
    }

    private static void startAudio(ServerPlayer player) {
        current.setNoAi(true); player.setGameMode(GameType.CREATIVE); player.removeAllEffects();
        // Stay within the 16-block attenuation of entity sounds even for the 30-block-wide boss.
        player.teleportTo(player.serverLevel(), current.getX(), current.getY(), current.getZ() + Math.min(12, Math.max(5, current.getBbWidth() + 2)), 180, 0);
        mode = "audio"; elapsed = 0; signal(player, "audio-" + current.kind().id());
    }
}
