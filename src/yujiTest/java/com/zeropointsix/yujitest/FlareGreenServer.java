package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.flare.FallingSupplyCrate;
import com.zeropointsix.eraser.yuji.flare.FlareAirdropLoot;
import com.zeropointsix.eraser.yuji.flare.FlareConfig;
import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.FlareProjectile;
import com.zeropointsix.eraser.yuji.flare.SupplyCrateBlockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa")
public final class FlareGreenServer {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static final AABB AREA = new AABB(-32, 64, -32, 33, 150, 33);
    private static String phase = "waiting";
    private static int elapsed;
    private static long firedAt, spawnedAt, landedAt;
    private static FlareProjectile flare;
    private static FallingSupplyCrate falling;
    private static BlockPos landing;
    private static String selectedLoot;
    private static boolean survived79;

    private static boolean enabled() { return Boolean.getBoolean("flare.qa.green"); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void signal(ServerPlayer player, String next) {
        phase = next;
        player.sendSystemMessage(Component.literal("FLARE_GREEN:" + next
                + (landing == null ? "" : ":" + landing.getX() + ":" + landing.getY() + ":" + landing.getZ())));
    }

    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (!enabled()) return;
        var level = event.getServer().overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        for (BlockPos pos : BlockPos.betweenClosed(-32, 64, -32, 32, 64, 32))
            level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)
                || !player.getGameProfile().getName().equals("GreenFlareQA")) return;
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), .5, 65, .5, 0, -45);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.GREEN.get(), 2));
        signal(player, "armed");
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END || phase.equals("done") || phase.equals("failed")) return;
        var player = event.getServer().getPlayerList().getPlayerByName("GreenFlareQA");
        if (player == null) return;
        try {
            require(++elapsed < 1800, "green client evidence timed out in " + phase);
            var level = player.serverLevel();
            require(FlareConfig.GREEN_DELAY.get() == 80 && FlareConfig.DROP_ATTEMPTS.get() == 48,
                    "real client uses unmodified production flight/search defaults");
            if (phase.equals("armed")) {
                var projectiles = level.getEntitiesOfClass(FlareProjectile.class, AREA);
                if (projectiles.isEmpty()) return;
                require(projectiles.size() == 1 && projectiles.get(0).color() == 2, "one real green shot");
                require(player.getOffhandItem().getCount() == 1, "real client shot consumes exactly one round");
                flare = projectiles.get(0);
                firedAt = level.getGameTime() - flare.saveWithoutId(new CompoundTag()).getInt("FlareAge");
                player.teleportTo(level, .5, 65, 10.5, 180, -35);
                signal(player, "flight");
            }
            if (phase.equals("flight")) {
                long age = level.getGameTime() - firedAt;
                var crates = level.getEntitiesOfClass(FallingSupplyCrate.class, AREA);
                if (age < 79) require(!flare.isRemoved() && crates.isEmpty(), "no destruction or crate before green delay");
                if (age == 79) survived79 = !flare.isRemoved();
                if (crates.isEmpty()) return;
                require(survived79 && age >= 80 && crates.size() == 1 && flare.isRemoved(), "unique crate after the 80 tick delay");
                require(Files.exists(RESULTS.resolve("client-green-flight.pass")), "client rendered the green flight");
                falling = crates.get(0); spawnedAt = level.getGameTime();
                landing = BlockPos.containing(falling.getX(), 65, falling.getZ());
                player.teleportTo(level, falling.getX() + 6, 85, falling.getZ() + 8, 140, -25);
                signal(player, "descent");
            }
            if (phase.equals("descent") && level.getBlockEntity(landing) instanceof SupplyCrateBlockEntity crate) {
                require(falling.isRemoved(), "natural descent replaced the falling entity with a block");
                require(Files.exists(RESULTS.resolve("client-green-descent.pass")), "client rendered the falling crate");
                selectedLoot = crate.saveWithoutMetadata().getString("LootTable");
                require(FlareAirdropLoot.DEFAULT_TABLES.contains(selectedLoot),
                        "selected vanilla loot remains deferred before first opening");
                landedAt = level.getGameTime();
                player.teleportTo(level, landing.getX() + 2.8, 65, landing.getZ() + 2.8, 135, 25);
                signal(player, "landed");
            }
            if (phase.equals("landed") && Files.exists(RESULTS.resolve("client-green.pass"))) {
                require(player.containerMenu instanceof ChestMenu, "actual client use opened a server chest menu");
                var crate = (SupplyCrateBlockEntity) level.getBlockEntity(landing);
                require(crate != null && !crate.isEmpty(), "opened supply crate contains generated loot");
                String proof = "shot=1 spawned=1 landed=1 opened=1 survived79=" + survived79
                        + " firedAt=" + firedAt + " spawnedAt=" + spawnedAt + " landedAt=" + landedAt
                        + " landing=" + landing + " selectedLoot=" + selectedLoot;
                Files.writeString(RESULTS.resolve("server-green.pass"), proof + "\n");
                System.out.println("FLARE_GREEN_SERVER_PASSED " + proof);
                phase = "done";
            }
        } catch (Throwable failure) {
            phase = "failed";
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server-green.failed"), failure.toString()); } catch (Exception ignored) { }
        }
    }
}
