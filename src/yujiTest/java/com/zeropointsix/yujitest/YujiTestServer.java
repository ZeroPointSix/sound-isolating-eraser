package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import com.zeropointsix.eraser.yuji.BoneSpike;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
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
    private static int elapsed, mobHits, playerHits;
    private static boolean effectObserved, spikeStuck;
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
        for (BlockPos pos : BlockPos.betweenClosed(-16, 64, -16, 16, 64, 24)) {
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
                        case EKOU -> player.hasEffect(YujiContent.CURSED.get());
                        case WANGYU -> player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
                        case LIANGSHE -> player.hasEffect(YujiContent.BETRAYED.get());
                        case XIEYIN -> player.hasEffect(YujiContent.ENTHRALLED.get());
                        case SHIFOTOU -> player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
                        default -> false;
                    };
                    if (elapsed == 180) finishAi(player);
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
                        spike.shoot(0, 0, 1, 1.6F, 0); elapsed = 0; mode = "bone-flight";
                    }
                }
                case "bone-flight" -> {
                    elapsed++;
                    if (elapsed == 10) { require(spike.stuck(), "real projectile collides with arena wall"); spikeStuck = true; }
                    if (elapsed == 215) {
                        require(spikeStuck && spike.isRemoved(), "stuck projectile expires after 200 server ticks");
                        pass("bone_spike", "Real rendered projectile collided with wall and expired; no independent audio in source pack.");
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
        double distance = Math.max(5.5, kind.height * 1.7);
        double y = Math.max(65, 65 + kind.height * .5 - 1.62);
        float pitch = (float) Math.toDegrees(Math.atan2(y + 1.62 - (65 + kind.height * .5), distance));
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
        if (current.kind() == YujiKind.CHENNU) require(current.getTarget() == null, "giant starts neutral before real player attack");
        current.setNoAi(false); elapsed = 0; mobHits = 0; playerHits = 0; effectObserved = false;
        mode = "ai"; signal(player, "ai-" + current.kind().id());
    }

    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (!mode.equals("ai") || current == null) return;
        if (event.getEntity() == current && event.getSource().getEntity() instanceof ServerPlayer) playerHits++;
        if (event.getEntity() instanceof ServerPlayer && event.getSource().getEntity() == current) mobHits++;
    }

    private static void finishAi(ServerPlayer player) throws Exception {
        double moved = current.position().distanceTo(initialPosition);
        require(current.getTarget() == player, "AI naturally tracks connected survival player: " + current.kind());
        switch (current.kind()) {
            case YUCHI -> require(moved > .3 && mobHits == 0, "passive follow without direct attack");
            case SHASHENG -> require(mobHits >= 2, "real player receives double slash");
            case TOUDAO -> require(!current.displayedLoot().isEmpty() && player.getInventory().getItem(1).isEmpty(), "real inventory stack stolen");
            case EKOU, WANGYU, LIANGSHE -> require(effectObserved, "AI applies source-specific status");
            case XIEYIN -> require(effectObserved && player.position().distanceTo(initialPlayer) > .3, "actual client moved by enthrallment");
            case QIYU -> require(moved > .3 && mobHits > 0, "flute monster pursues and strikes real player");
            case TANYU -> require(current.layers() >= 3 && mobHits > 0, "real dropped flesh consumed and real player attacked");
            case CHENNU -> {
                CompoundTag tag = new CompoundTag(); current.addAdditionalSaveData(tag);
                require(playerHits > 0 && tag.hasUUID("YujiGrudge") && tag.getUUID("YujiGrudge").equals(player.getUUID()), "real client attack creates persisted grudge");
                require(mobHits > 0, "giant retaliates against real player");
            }
            case SHIFOTOU -> require(effectObserved && mobHits > 0, "boss gaze and combat affect connected player");
        }
        pass("ai-" + current.kind().id(), "realPlayer=" + player.getUUID() + ", floorCollision=true, aiTarget=true, moved=" + moved
                + ", playerDamageEvents=" + mobHits + ", hitsByPlayer=" + playerHits + ", statusObserved=" + effectObserved);
        current.setNoAi(true); player.setGameMode(GameType.CREATIVE); player.removeAllEffects();
        player.teleportTo(player.serverLevel(), current.getX(), current.getY(), current.getZ() + Math.max(5, current.getBbWidth() + 2), 180, 0);
        mode = "audio"; elapsed = 0; signal(player, "audio-" + current.kind().id());
    }
}
