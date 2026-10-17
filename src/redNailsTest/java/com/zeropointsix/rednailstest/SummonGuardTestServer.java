package com.zeropointsix.rednailstest;

import com.zeropointsix.eraser.rednails.RedNailsItem;
import com.zeropointsix.eraser.rednails.RedNailsState;
import com.zeropointsix.eraser.rednails.WaterGhostSummonEntity;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaPocketWatchItem;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/** Opt-in dedicated-server proof: real client input, enabled guardian AI, no owner attack. */
@Mod.EventBusSubscriber(modid = "red_nails_qa")
public final class SummonGuardTestServer {
    private static final boolean ENABLED = Boolean.getBoolean("rednails.qa.guard");
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static final List<Zombie> ENEMIES = new ArrayList<>();
    private static final List<LivingEntity> FRIENDS = new ArrayList<>();
    private static ServerPlayer player;
    private static String kind = "suxia";
    private static int stage, ticks, firstHit = -1;
    private static long summonedAt = -1;
    private static boolean failed;

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void signal(String phase) {
        ticks = 0;
        player.sendSystemMessage(Component.literal("SUMMON_GUARD_QA:" + kind + "-" + phase));
    }
    private static boolean captured(String phase) { return Files.exists(RESULTS.resolve("client-" + kind + "-" + phase + ".pass")); }
    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (!ENABLED) return;
        var level = event.getServer().overworld();
        level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, event.getServer());
        for (var pos : BlockPos.betweenClosed(-20, 69, -20, 20, 69, 20)) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("NailsTester")) return;
        player = p; p.setGameMode(GameType.SURVIVAL);
        p.teleportTo(p.serverLevel(), 0.5, 70, -4.5, 0, 0);
    }
    private static void scene() {
        ENEMIES.forEach(LivingEntity::discard); ENEMIES.clear();
        FRIENDS.forEach(LivingEntity::discard); FRIENDS.clear();
        var level = player.serverLevel();
        for (int[] at : new int[][]{{-4, 2}, {0, 5}, {4, 2}, {-3, 8}, {3, 8}}) {
            var zombie = EntityType.ZOMBIE.create(level);
            zombie.moveTo(at[0] + .5, 70, at[1] + .5, 180, 0);
            // Idle hostiles prove acquisition does not depend on aggression toward the owner.
            zombie.setNoAi(true); zombie.setPersistenceRequired();
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            level.addFreshEntity(zombie); ENEMIES.add(zombie);
        }
        var villager = EntityType.VILLAGER.create(level); villager.setNoAi(true);
        villager.moveTo(-7.5, 70, -3.5, 0, 0); level.addFreshEntity(villager); FRIENDS.add(villager);
        var wolf = EntityType.WOLF.create(level); wolf.setTame(true); wolf.setOwnerUUID(player.getUUID()); wolf.setNoAi(true);
        wolf.setHealth(wolf.getMaxHealth()); wolf.moveTo(-5.5, 70, -3.5, 0, 0); level.addFreshEntity(wolf); FRIENDS.add(wolf);
        firstHit = -1; summonedAt = -1;
    }
    private static boolean guardian(LivingEntity entity) {
        return (kind.equals("suxia") ? entity instanceof SuxiaSummonEntity : entity instanceof WaterGhostSummonEntity)
                && entity instanceof TamableAnimal tame && player.getUUID().equals(tame.getOwnerUUID());
    }
    private static boolean summoned() {
        if (kind.equals("suxia")) return SuxiaSummons.get(player.getServer()).active(player.getUUID()) != null;
        var entry = RedNailsState.get(player.getServer()).read(RedNailsItem.findWorn(player));
        return entry != null && entry.active() > 0;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || player == null || failed) return;
        try {
            if (++ticks > 1000) throw new AssertionError("Guard QA timeout " + kind + "/" + stage);
            if (stage == 0 && ticks >= 30) {
                var inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
                var ring = inventory == null ? null : inventory.getStacksHandler("ring").orElse(null);
                if (ring == null) return;
                ring.getStacks().setStackInSlot(0, new ItemStack(ModItems.RED_NAILS.get()));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SUXIA_POCKET_WATCH.get()));
                scene(); stage = 1; signal("ready");
            } else if (stage == 1 && captured("ready")) {
                stage = 2; signal("fight");
            } else if (stage == 2) {
                long now = player.serverLevel().getGameTime();
                if (summonedAt < 0 && summoned()) summonedAt = now;
                if (ENEMIES.stream().anyMatch(z -> guardian(z.getLastHurtByMob())) && firstHit < 0) {
                    require(summonedAt >= 0, "Guardian exists before attacking");
                    firstHit = (int) (now - summonedAt);
                    require(firstHit <= 100, "First autonomous damage within 100 ticks: " + firstHit);
                }
                for (var friend : FRIENDS) require(friend.getHealth() == friend.getMaxHealth(), "Villager and own pet remain unharmed");
                require(player.getHealth() == player.getMaxHealth() && player.getLastHurtMob() == null,
                        "Owner remains unharmed and never issues an attack");
                if (firstHit >= 0 && ENEMIES.stream().noneMatch(LivingEntity::isAlive) && captured("fight")) {
                    require(ENEMIES.stream().allMatch(z -> guardian(z.getLastHurtByMob())), "Every zombie killed by its guardian");
                    Files.writeString(RESULTS.resolve("server-" + kind + "-combat.pass"),
                            "Real dedicated server and client input; firstHitTicks=" + firstHit + "; killed=5/5; friends=unharmed; ownerAttacks=0; idleHostiles=true\n");
                    stage = 3; signal("cleared");
                }
            } else if (stage == 3 && captured("cleared")) {
                if (kind.equals("suxia")) {
                    SuxiaPocketWatchItem.cancelOwner(player);
                    kind = "water"; scene(); stage = 1; signal("ready");
                } else {
                    RedNailsItem.cancelOwner(player);
                    Files.writeString(RESULTS.resolve("server-guard-complete.pass"), "Both guardian scenes passed.\n");
                    stage = 4;
                }
            } else if (stage == 4) ticks = 0;
        } catch (Throwable error) {
            failed = true; error.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
        }
    }
}
