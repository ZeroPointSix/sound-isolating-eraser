package com.zeropointsix.neontest;

import com.zeropointsix.eraser.neon.NeonTumorEntity;
import com.zeropointsix.eraser.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "neon_qa")
public final class NeonTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("neon.qa.results"));
    private static int stage, elapsed, digestionHits;
    private static boolean failed, gallerySpawned;
    private static NeonTumorEntity captor;
    private static UUID logoutCaptor;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void pass(String name, String message) throws Exception {
        Files.writeString(RESULTS.resolve(name + ".pass"), message + "\n");
    }

    private static void signal(ServerPlayer player, String phase) {
        player.sendSystemMessage(Component.literal("NEON_QA:" + phase));
    }

    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, level.getServer());
        for (BlockPos pos : BlockPos.betweenClosed(-15, 64, -16, 18, 64, 20)) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
    }

    private static NeonTumorEntity spawn(ServerLevel level, double x, double z, int size, int variant) {
        NeonTumorEntity entity = ModEntities.NEON_TUMOR.get().create(level);
        entity.setSizeIndex(size);
        entity.setVariant(variant);
        entity.setNoAi(true);
        entity.setPersistenceRequired();
        entity.moveTo(x, 65, z, 0, 0);
        level.addFreshEntity(entity);
        return entity;
    }

    private static void capture(ServerPlayer player, boolean weak) {
        if (captor != null) captor.discard();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.removeAllEffects();
        player.setHealth(20);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(0);
        player.teleportTo(player.serverLevel(), 0, 65, 10, 180, 0);
        captor = spawn(player.serverLevel(), 0, 10, 2, 1);
        captor.awaken();
        if (weak) captor.setHealth(4);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, weak ? new ItemStack(Items.DIAMOND_SWORD) : ItemStack.EMPTY);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600));
        require(captor.beginEngulf(player), "real server capture succeeds");
        elapsed = 0;
    }

    @SubscribeEvent public static void damage(LivingHurtEvent event) {
        if (stage == 2 && event.getEntity() instanceof ServerPlayer
                && event.getSource().getEntity() == captor) digestionHits++;
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getGameProfile().getName().equals("NeonTester")) return;
        try {
            ServerLevel level = player.serverLevel();
            if (stage == 4) {
                require(!player.isPassenger(), "rejoined player is not trapped");
                require(level.getEntity(logoutCaptor) instanceof NeonTumorEntity, "captor remains in world after logout and rejoin");
                pass("server-rejoin", "Real reconnect: player free; original captor UUID remains in the world.");
                signal(player, "rejoined");
                stage = 5;
                return;
            }
            require(stage == 0, "unexpected player login phase");
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, 10, 71, 11, 148, 18);
            player.getInventory().setItem(0, new ItemStack(com.zeropointsix.eraser.registry.ModItems.NEON_TUMOR_SPAWN_EGG.get()));
            signal(player, "baseline");
        } catch (Throwable error) { fail(error); }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (stage == 3 && event.getEntity().getGameProfile().getName().equals("NeonTester")) {
            // Check on the next server tick, after all logout listeners and vanilla save work.
            stage = 4;
            elapsed = 0;
        }
    }

    private static void fail(Throwable error) {
        error.printStackTrace();
        failed = true;
        try { Files.writeString(RESULTS.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        var server = event.getServer();
        var player = server.getPlayerList().getPlayerByName("NeonTester");
        try {
            if (stage == 0 && player != null && !gallerySpawned && Files.exists(RESULTS.resolve("client-baseline.pass"))) {
                for (int size = 0; size < 3; size++) for (int variant = 0; variant < 3; variant++) {
                    spawn(player.serverLevel(), (variant - 1) * 5, -size * 5, size, variant);
                }
                gallerySpawned = true;
                signal(player, "gallery");
            }
            if (stage == 0 && player != null && Files.exists(RESULTS.resolve("client-gallery.pass"))) {
                capture(player, true);
                stage = 1;
                signal(player, "escape");
            } else if (stage == 1 && player != null) {
                elapsed++;
                if (!captor.isAlive() && !player.isPassenger()) {
                    pass("server-escape", "Real client shift dismount blocked; ray-picked attack from inside killed captor and released player.");
                    stage = 11;
                    elapsed = 0;
                    signal(player, "released-death");
                } else require(elapsed < 100, "client attack escape timed out");
            } else if (stage == 11 && player != null) {
                require(++elapsed < 120, "client did not accept death dismount");
                if (Files.exists(RESULTS.resolve("client-released-death.pass"))) {
                    capture(player, false);
                    stage = 2;
                    digestionHits = 0;
                    signal(player, "timeout");
                }
            } else if (stage == 2 && player != null) {
                elapsed++;
                if (elapsed == 40) require(player.getVehicle() == captor, "still captured before four-second deadline");
                if (elapsed >= 85) {
                    require(!player.isPassenger() && !captor.isEngulfing(), "four-second real-client timeout ejects");
                    require(digestionHits == 4, "exactly four digestion damage events, got " + digestionHits);
                    require(!captor.canEngulf(player), "cannot immediately re-swallow released player");
                    pass("server-timeout", "Four-second timeout and repeat-capture prevention passed; damage events=" + digestionHits + "; health=" + player.getHealth());
                    stage = 12;
                    elapsed = 0;
                    signal(player, "released-timeout");
                }
            } else if (stage == 12 && player != null) {
                require(++elapsed < 120, "client did not accept timed dismount");
                if (Files.exists(RESULTS.resolve("client-released-timeout.pass"))) {
                    capture(player, false);
                    logoutCaptor = captor.getUUID();
                    stage = 3;
                    signal(player, "logout");
                }
            } else if (stage == 3) {
                require(++elapsed < 120, "client did not log out while captured");
            } else if (stage == 4 && ++elapsed == 10) {
                require(server.overworld().getEntity(logoutCaptor) instanceof NeonTumorEntity tumor && !tumor.isVehicle(),
                        "logout must not move the captor into the player's save");
                pass("server-logout", "Real player disconnect left original captor in the world without a passenger.");
            } else if (stage == 5 && player != null && Files.exists(RESULTS.resolve("client-rejoined.pass"))) {
                capture(player, false);
                stage = 6;
            } else if (stage == 6 && player != null && ++elapsed == 20) {
                player.teleportTo(server.getLevel(net.minecraft.world.level.Level.NETHER), 0, 128, 0, 0, 0);
                require(!player.isPassenger() && !captor.isVehicle(), "real dimension transfer releases swallowed player");
                require(!captor.isRemoved(), "dimension transfer leaves captor in original world");
                pass("server-dimension", "Real swallowed player transferred to Nether and captor remained in Overworld.");
                signal(player, "dimension");
                stage = 7;
            }
        } catch (Throwable error) { fail(error); }
    }
}
