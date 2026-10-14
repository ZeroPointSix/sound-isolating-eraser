package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.flare.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class FlareGameTests {
    private static Player player(GameTestHelper h) {
        Player player = h.makeMockSurvivalPlayer();
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        return player;
    }
    private static FlareDecoy decoy(GameTestHelper h) {
        FlareDecoy decoy = h.spawn(FlareContent.DECOY.get(), new BlockPos(4, 2, 4));
        decoy.ignite(); return decoy;
    }
    private static List<FlareProjectile> projectiles(GameTestHelper h, Player player) {
        return h.getLevel().getEntitiesOfClass(FlareProjectile.class, player.getBoundingBox().inflate(4), e -> e.getOwner() == player);
    }
    private static void fire(GameTestHelper h, Player player) { FlareContent.GUN.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND); }
    private static BlockPos platform(GameTestHelper h) {
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        return h.absolutePos(new BlockPos(3, 2, 3));
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareOffhandWinsAndCooldownConsumesOnce(GameTestHelper h) {
        Player p = player(h);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.YELLOW.get(), 2));
        p.getInventory().items.set(1, new ItemStack(FlareContent.RED.get(), 3));
        fire(h, p); fire(h, p);
        var shots = projectiles(h, p);
        h.assertTrue(shots.size() == 1 && shots.get(0).color() == 1, "offhand yellow wins; cooldown prevents second shot");
        h.assertTrue(p.getOffhandItem().getCount() == 1 && p.getInventory().items.get(1).getCount() == 3, "only one offhand round consumed");
        h.assertTrue(p.getCooldowns().isOnCooldown(FlareContent.GUN.get()), "cooldown active");
        for (int i = 0; i < 19; i++) p.getCooldowns().tick();
        h.assertTrue(p.getCooldowns().isOnCooldown(FlareContent.GUN.get()), "20-tick cooldown has not elapsed at tick 19");
        p.getCooldowns().tick();
        h.assertTrue(!p.getCooldowns().isOnCooldown(FlareContent.GUN.get()), "cooldown ends at tick 20");
        shots.forEach(FlareProjectile::discard); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareInventoryOrderEmptyAndCreative(GameTestHelper h) {
        Player p = player(h); fire(h, p);
        h.assertTrue(projectiles(h, p).isEmpty(), "no ammunition means no projectile");
        p.getInventory().items.set(12, new ItemStack(FlareContent.YELLOW.get()));
        p.getInventory().items.set(4, new ItemStack(FlareContent.RED.get()));
        h.assertTrue(FlareGunItem.ammunition(p).is(FlareContent.RED.get()), "hotbar precedes main inventory");
        p.getAbilities().instabuild = true; fire(h, p);
        h.assertTrue(p.getInventory().items.get(4).getCount() == 1, "creative preserves ammunition");
        projectiles(h, p).forEach(FlareProjectile::discard); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareGreenIndoorRejectionAndVerticalLaunch(GameTestHelper h) {
        Player p = player(h); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.GREEN.get(), 2));
        BlockPos roof = p.blockPosition().above(3); h.getLevel().setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
        fire(h, p);
        h.assertTrue(projectiles(h, p).isEmpty() && p.getOffhandItem().getCount() == 2, "indoor refusal consumes nothing");
        h.getLevel().removeBlock(roof, false); p.setXRot(70); p.setYRot(87); fire(h, p);
        var shots = projectiles(h, p);
        h.assertTrue(shots.size() == 1 && shots.get(0).getDeltaMovement().y > 0 && shots.get(0).getDeltaMovement().horizontalDistanceSqr() < 1.e-8,
                "green fires vertically regardless of view");
        shots.forEach(FlareProjectile::discard); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareDecoyTauntsOrdinaryEnemiesButNotBossOrChennu(GameTestHelper h) {
        Player p = player(h); FlareDecoy d = decoy(h);
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1)); zombie.setNoAi(true); zombie.setTarget(p);
        var ordinary = h.spawn(YujiContent.MOBS.get(YujiKind.SHASHENG).get(), new BlockPos(2, 2, 1)); ordinary.setNoAi(true); ordinary.setTarget(p);
        var chennu = h.spawn(YujiContent.MOBS.get(YujiKind.CHENNU).get(), new BlockPos(2, 2, 2)); chennu.setNoAi(true); chennu.setTarget(p);
        var boss = h.spawn(YujiContent.MOBS.get(YujiKind.SHIFOTOU).get(), new BlockPos(6, 2, 6)); boss.setNoAi(true); boss.setTarget(p);
        d.taunt();
        h.assertTrue(zombie.getTarget() == d && ordinary.getTarget() == d, "vanilla and Yuji hostile mobs are retargeted");
        h.assertTrue(chennu.getTarget() == p && boss.getTarget() == p, "grudge mob and boss preserve targets");
        d.discard(); zombie.discard(); ordinary.discard(); chennu.discard(); boss.discard(); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare", timeoutTicks = 100)
    public static void flareDecoyScheduledTauntAndExpiry(GameTestHelper h) {
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(
                h.absolutePos(new BlockPos(4, 2, 4))), "entity chunk ready")).thenExecute(() -> {
        Player p = player(h); FlareDecoy d;
        try (var config = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            FlareConfig.RED_LIFETIME.set(20); d = decoy(h);
        }
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1)); zombie.setNoAi(true); zombie.setTarget(p);
        h.runAfterDelay(11, () -> h.assertTrue(zombie.getTarget() == d, "tick-based taunt fires within ten ticks"));
        h.runAfterDelay(24, () -> { h.assertTrue(d.isRemoved(), "saved lifetime expires without manual activation"); zombie.discard(); h.succeed(); });
        });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareDecoyArmorDamageAndDeath(GameTestHelper h) {
        FlareDecoy d = decoy(h);
        h.assertTrue(d.getMaxHealth() == 30 && d.getArmorValue() == 6, "player-like health and armor defaults");
        var attacker = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        d.hurt(h.getLevel().damageSources().mobAttack(attacker), 10); attacker.discard();
        h.assertTrue(d.getHealth() > 20 && d.getHealth() < 30, "armor participates in real damage calculation");
        d.invulnerableTime = 0; d.hurt(h.getLevel().damageSources().genericKill(), 1000);
        h.assertTrue(d.isRemoved(), "lethal damage extinguishes immediately"); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare", timeoutTicks = 60)
    public static void flareLightFloorAndSaveReloadCleanup(GameTestHelper h) {
        Player p = player(h); BlockPos center = h.absolutePos(new BlockPos(4, 4, 4));
        FlareLights data = new FlareLights();
        try (var config = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            FlareConfig.LIGHT_RADIUS.set(3); FlareConfig.YELLOW_LIFETIME.set(30);
            data.illuminate(h.getLevel(), center, p);
        }
        h.assertTrue(data.size() > 0, "light positions recorded");
        FlareLights restored = FlareLights.load(data.save(new CompoundTag()));
        h.assertTrue(restored.size() == data.size(), "save and load preserve every light and its deadline");
        h.runAfterDelay(12, () -> {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
                if (pos.distSqr(center) <= 9 && (h.getLevel().getBlockState(pos).isAir() || h.getLevel().getBlockState(pos).is(Blocks.LIGHT)))
                    h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK, pos) >= 12, "actual block light >=12 at " + pos);
            }
        });
        h.runAfterDelay(36, () -> { restored.cleanup(h.getLevel()); h.assertTrue(restored.size() == 0, "all restored light records expire"); h.succeed(); });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareLightProtectionAndForeignBlocks(GameTestHelper h) {
        Player p = player(h); BlockPos center = h.absolutePos(new BlockPos(4, 4, 4));
        BlockPos solid = center.below(); h.getLevel().setBlock(solid, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        BlockPos foreign = center.above(); h.getLevel().setBlock(foreign, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
        class Guard {
            int denied;
            @SubscribeEvent public void onPlace(BlockEvent.EntityPlaceEvent event) {
                if (event.getPos().distSqr(center) <= 4) { denied++; event.setCanceled(true); }
            }
        }
        Guard guard = new Guard(); MinecraftForge.EVENT_BUS.register(guard);
        try (var config = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            FlareConfig.LIGHT_RADIUS.set(2); var data = new FlareLights(); data.illuminate(h.getLevel(), center, p);
            h.assertTrue(guard.denied > 0 && data.size() == 0, "protection events reject sources without leaked records");
            h.assertTrue(h.getLevel().getBlockState(solid).is(Blocks.DIAMOND_BLOCK), "non-air is never replaced");
            h.assertTrue(h.getLevel().getBlockState(foreign).is(Blocks.LIGHT), "foreign light not claimed or removed");
        } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.getLevel().removeBlock(foreign, false); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareExpiredUnloadedRecordsWaitForChunk(GameTestHelper h) {
        BlockPos far = new BlockPos(30000000, 10, 30000000);
        CompoundTag record = new CompoundTag(); record.putLong("Pos", far.asLong()); record.putLong("Expires", -1);
        ListTag records = new ListTag(); records.add(record); CompoundTag saved = new CompoundTag(); saved.put("Lights", records);
        FlareLights data = FlareLights.load(saved); data.cleanup(h.getLevel());
        h.assertTrue(!h.getLevel().hasChunkAt(far) && data.size() == 1, "unloaded chunk is not forced and cleanup remains durable");
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareWaterloggedAndAdjustedLightsStillExpire(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(3, 3, 3));
        h.getLevel().setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7).setValue(LightBlock.WATERLOGGED, true), 3);
        CompoundTag record = new CompoundTag(); record.putLong("Pos", pos.asLong()); record.putLong("Expires", -1);
        ListTag records = new ListTag(); records.add(record); CompoundTag saved = new CompoundTag(); saved.put("Lights", records);
        FlareLights data = FlareLights.load(saved); data.cleanup(h.getLevel());
        h.assertTrue(data.size() == 0 && h.getLevel().getBlockState(pos).is(Blocks.WATER), "modified owned light expires while water survives");
        h.getLevel().removeBlock(pos, false); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare", timeoutTicks = 220)
    public static void flareAirdropLandsGeneratesOnceAndExpiresEmpty(GameTestHelper h) {
        Player p = player(h); BlockPos pos = platform(h);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(pos), "entity chunk ready")).thenExecute(() -> {
        var entity = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), h.getLevel());
        entity.prepare(pos, p.getUUID()); entity.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        h.getLevel().addFreshEntity(entity);
        h.runAfterDelay(8, () -> {
            h.assertTrue(entity.isRemoved() && h.getLevel().getBlockEntity(pos) instanceof SupplyCrateBlockEntity, "falling entity becomes a container");
            var crate = (SupplyCrateBlockEntity) h.getLevel().getBlockEntity(pos);
            h.assertTrue(crate.saveWithoutMetadata().contains("LootTable"), "loot remains deferred before opening");
            crate.createMenu(1, p.getInventory(), p);
            h.assertTrue(!crate.isEmpty() && !crate.saveWithoutMetadata().contains("LootTable"), "opening creates real datapack loot");
            CompoundTag first = crate.saveWithoutMetadata(); crate.createMenu(2, p.getInventory(), p);
            h.assertTrue(first.equals(crate.saveWithoutMetadata()), "reopening does not regenerate loot");
            crate.clearContent();
        });
        h.runAfterDelay(115, () -> { h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "empty crate expires after five seconds"); h.succeed(); });
        });
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareCrateBreakDropsContentsNotContainer(GameTestHelper h) {
        BlockPos pos = platform(h); h.getLevel().setBlock(pos, FlareContent.CRATE.get().defaultBlockState(), 3);
        var crate = (SupplyCrateBlockEntity) h.getLevel().getBlockEntity(pos);
        crate.setItem(0, new ItemStack(Items.DIAMOND, 4)); h.getLevel().destroyBlock(pos, true);
        var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        h.assertTrue(items.stream().map(ItemEntity::getItem).filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 4, "all contents dropped exactly once");
        h.assertTrue(items.stream().noneMatch(e -> e.getItem().is(FlareContent.CRATE_ITEM.get())), "crate itself has no block drop");
        items.forEach(ItemEntity::discard); h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareCrateRejectsFluidShaftAndProtectedLanding(GameTestHelper h) {
        Player p = player(h); BlockPos pos = platform(h);
        h.assertTrue(FlarePlacement.safeGround(h.getLevel(), pos), "flat surface is valid");
        h.getLevel().setBlock(pos.above(4), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        h.assertTrue(!FlarePlacement.safeGround(h.getLevel(), pos), "canopy blocking the descent is rejected");
        h.getLevel().removeBlock(pos.above(4), false);
        h.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        h.assertTrue(!FlarePlacement.safeGround(h.getLevel(), pos), "water landing rejected"); h.getLevel().removeBlock(pos, false);
        h.getLevel().setBlock(pos.above(4).east(), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(!FlarePlacement.safeGround(h.getLevel(), pos), "adjacent high rim rejects shaft"); h.getLevel().removeBlock(pos.above(4).east(), false);
        class Guard { @SubscribeEvent public void deny(BlockEvent.EntityPlaceEvent event) { if (event.getPos().equals(pos)) event.setCanceled(true); } }
        Guard guard = new Guard(); MinecraftForge.EVENT_BUS.register(guard);
        try {
            var entity = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), h.getLevel());
            entity.prepare(pos, p.getUUID()); entity.setPos(Vec3.atBottomCenterOf(pos));
            h.assertTrue(!entity.land() && h.getLevel().getBlockState(pos).isAir(), "protection rechecked on actual touchdown");
        } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.succeed();
    }
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareSupplyLootAndUtilityTags(GameTestHelper h) {
        LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2)))).create(LootContextParamSets.CHEST);
        var loot = h.getLevel().getServer().getLootData().getLootTable(YujiContent.id("chests/yuji_supply_drop")).getRandomItems(params);
        for (var entry : List.of(FlareContent.GUN, FlareContent.RED, FlareContent.YELLOW, FlareContent.GREEN)) {
            ItemStack stack = new ItemStack(entry.get());
            int expected = entry == FlareContent.RED || entry == FlareContent.YELLOW ? 2 : 1;
            h.assertTrue(loot.stream().filter(s -> s.is(entry.get())).mapToInt(ItemStack::getCount).sum() == expected, "fixed supply quantity for " + entry.getId());
            h.assertTrue(stack.is(FlareEvents.PROPS) && !stack.is(FlareEvents.ODDITIES) && stack.getRarity() == Rarity.COMMON, "utility tag never changes rarity");
            h.assertTrue(!stack.isDamageableItem() && stack.getMaxStackSize() == (entry == FlareContent.GUN ? 1 : 16), "stack and durability defaults");
        }
        for (int i = 0; i < 30; i++) h.assertTrue(!h.getLevel().getServer().getLootData().getLootTable(YujiContent.id("chests/yuji_airdrop")).getRandomItems(params).isEmpty(), "airdrop always gives supplies");
        h.succeed();
    }
    private FlareGameTests() { }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare")
    public static void flareLandingProbeNeverPulsesObservers(GameTestHelper h) {
        Player p = player(h); BlockPos pos = h.absolutePos(new BlockPos(3, 3, 3));
        BlockPos observer = pos.east();
        h.getLevel().setBlock(observer, Blocks.OBSERVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ObserverBlock.FACING, net.minecraft.core.Direction.WEST), 16);
        h.assertTrue(FlarePlacement.canPlace(h.getLevel(), pos, FlareContent.CRATE.get().defaultBlockState(), p), "probe accepts valid location");
        h.assertTrue(h.getLevel().isEmptyBlock(pos) && h.getLevel().getBlockEntity(pos) == null, "probe leaves no block or container");
        h.assertTrue(!h.getLevel().getBlockTicks().hasScheduledTick(observer, Blocks.OBSERVER), "probe and rollback never schedule observer pulse");
        h.assertTrue(FlarePlacement.place(h.getLevel(), pos, FlareContent.CRATE.get().defaultBlockState(), p), "real placement succeeds");
        h.assertTrue(h.getLevel().getBlockTicks().hasScheduledTick(observer, Blocks.OBSERVER), "positive control: real placement notifies observer");
        h.succeed();
    }
}
