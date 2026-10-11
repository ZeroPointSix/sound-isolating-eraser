package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
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

    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
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
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getGameProfile().getName().equals("YujiTester")) return;
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), .5, 66, 10.5, 180, 0);
        signal(player, "ready");
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || complete) return;
        var player = event.getServer().getPlayerList().getPlayerByName("YujiTester");
        if (player == null) return;
        String done = index < 0 ? "ready" : YujiKind.values()[index].id();
        if (!Files.exists(RESULTS.resolve("client-" + done + ".pass"))) return;
        try {
            if (current != null) current.discard();
            if (++index == YujiKind.values().length) {
                complete = true;
                signal(player, "cleanup");
                Files.writeString(RESULTS.resolve("server-gallery.pass"), "11 entity profiles synchronized through dedicated Forge server; removed after rendering.\n");
                return;
            }
            YujiKind kind = YujiKind.values()[index];
            current = YujiContent.MOBS.get(kind).get().create(player.serverLevel());
            current.setNoAi(true);
            current.setPersistenceRequired();
            current.moveTo(.5, 65, .5, 0, 0);
            current.setYBodyRot(0);
            player.serverLevel().addFreshEntity(current);
            double distance = Math.max(5.5, kind.height * 1.7);
            double y = Math.max(65, 65 + kind.height * .5 - 1.62);
            float pitch = (float) Math.toDegrees(Math.atan2(y + 1.62 - (65 + kind.height * .5), distance));
            player.teleportTo(player.serverLevel(), .5, y, .5 + distance, 180, pitch);
            player.getInventory().setItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(YujiContent.id(kind.id() + "_spawn_egg"))));
            player.getInventory().selected = 0;
            signal(player, kind.id());
            System.out.println("YUJI_QA_SERVER kind=" + kind.id() + " health=" + current.getHealth() + " height=" + current.getBbHeight());
        } catch (Throwable failure) {
            failed = true;
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), failure.toString()); } catch (Exception ignored) { }
        }
    }
}
