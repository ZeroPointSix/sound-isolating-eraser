package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.flare.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class FlareRegressionGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "FlareRegression"));
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 3, 2))));
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        return player;
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareAmmoPriorityCooldownAndCreative(GameTestHelper h) {
        var p = player(h);
        try {
        p.getInventory().setItem(1, new ItemStack(FlareContent.RED.get(), 3));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.YELLOW.get(), 2));
        var gun = FlareContent.GUN.get();
        h.assertTrue(FlareGunItem.ammunition(p) == p.getOffhandItem(), "offhand precedes inventory");
        gun.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getOffhandItem().getCount() == 1 && p.getInventory().getItem(1).getCount() == 3, "exactly one offhand round consumed");
        gun.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getOffhandItem().getCount() == 1, "cooldown blocks second shot");
        p.getCooldowns().removeCooldown(gun);
        p.getAbilities().instabuild = true;
        gun.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getOffhandItem().getCount() == 1, "creative keeps ammo");
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        p.getInventory().setItem(9, new ItemStack(FlareContent.GREEN.get(), 4));
        h.assertTrue(FlareGunItem.ammunition(p) == p.getInventory().getItem(1), "hotbar precedes main inventory");
        p.getInventory().setItem(1, ItemStack.EMPTY);
        h.assertTrue(FlareGunItem.ammunition(p) == p.getInventory().getItem(9), "main inventory fallback");
        h.succeed();
        } finally {
            h.getLevel().getEntitiesOfClass(FlareProjectile.class, p.getBoundingBox().inflate(4), e -> e.getOwner() == p)
                    .forEach(FlareProjectile::discard);
        }
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareWaterloggedLightExpiresAfterReload(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 7, 1));
        level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
        CompoundTag saved = new CompoundTag(); ListTag entries = new ListTag();
        CompoundTag entry = new CompoundTag(); entry.putLong("Pos", pos.asLong()); entry.putLong("Expires", level.getGameTime() - 1); entries.add(entry);
        saved.put("Lights", entries);
        FlareLights loaded = FlareLights.load(saved);
        loaded.cleanup(level);
        h.assertTrue(loaded.size() == 0 && level.getBlockState(pos).is(Blocks.WATER), "waterlogged light expires without deleting water");
        level.removeBlock(pos, false);
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareNoAmmoOrRoofNeverConsumes(GameTestHelper h) {
        var p = player(h);
        var gun = FlareContent.GUN.get();
        h.assertTrue(!gun.use(h.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "empty gun refuses shot");
        h.assertTrue(!p.getCooldowns().isOnCooldown(gun), "dry click has no shot cooldown");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.GREEN.get(), 2));
        BlockPos roof = p.blockPosition().above(3);
        h.getLevel().setBlockAndUpdate(roof, Blocks.GLASS.defaultBlockState());
        gun.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getOffhandItem().getCount() == 2 && !p.getCooldowns().isOnCooldown(gun), "roof refuses without consumption");
        h.getLevel().removeBlock(roof, false);
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareAllFourAreOrdinaryProps(GameTestHelper h) {
        for (var item : new net.minecraft.world.item.Item[]{FlareContent.GUN.get(), FlareContent.RED.get(), FlareContent.YELLOW.get(), FlareContent.GREEN.get()}) {
            ItemStack stack = new ItemStack(item);
            h.assertTrue(stack.is(FlareEvents.PROPS) && !stack.is(FlareEvents.ODDITIES), "prop category: " + item);
            h.assertTrue(stack.getRarity() == Rarity.COMMON && !stack.isDamageableItem(), "rarity and no durability: " + item);
            h.assertTrue(stack.getMaxStackSize() == (item == FlareContent.GUN.get() ? 1 : 16), "stack size: " + item);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression", timeoutTicks = 640)
    public static void flareDecoyRealTickLifetimeAndExclusions(GameTestHelper h) {
        var decoy = h.spawn(FlareContent.DECOY.get(), new BlockPos(3, 3, 3));
        decoy.setNoGravity(true); decoy.ignite();
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 3, 3));
        zombie.setNoAi(true); zombie.setNoGravity(true);
        var yuji = h.spawn(YujiContent.MOBS.get(YujiKind.YUCHI).get(), new BlockPos(4, 3, 4));
        yuji.setNoAi(true); yuji.setNoGravity(true);
        var grudge = h.spawn(YujiContent.MOBS.get(YujiKind.CHENNU).get(), new BlockPos(5, 3, 5));
        grudge.setNoAi(true); grudge.setNoGravity(true);
        var target = h.spawn(EntityType.COW, new BlockPos(6, 3, 3));
        target.setNoAi(true); target.setNoGravity(true); grudge.setTarget(target);
        h.assertTrue(decoy.getMaxHealth() == 30 && decoy.getArmorValue() == 6
                && decoy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1, "decoy combat attributes");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(decoy.blockPosition()), "entity ticking"))
                .thenExecute(() -> {
                    h.runAfterDelay(10, () -> {
                        h.assertTrue(zombie.getTarget() == decoy && yuji.getTarget() == decoy, "production tick taunts both mobs within ten ticks");
                        h.assertTrue(grudge.getTarget() == target, "grudge target remains intact");
                    });
                    h.runAfterDelay(605, () -> { h.assertTrue(decoy.isRemoved(), "expires after 600 actual world ticks"); h.succeed(); });
                });
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareProtectionCancellationRestoresWorld(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 5, 2));
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        class Protection {
            int seen;
            @SubscribeEvent public void placed(BlockEvent.EntityPlaceEvent e) {
                if (e.getPos().equals(pos)) { seen++; e.setCanceled(true); }
            }
        }
        var protection = new Protection();
        MinecraftForge.EVENT_BUS.register(protection);
        try {
            h.assertTrue(!FlarePlacement.place(h.getLevel(), pos, Blocks.LIGHT.defaultBlockState(), player(h)), "real Forge cancellation denies placement");
            h.assertTrue(protection.seen == 1 && h.getLevel().isEmptyBlock(pos), "event fired and air restored");
            h.assertTrue(h.getLevel().getBlockEntity(pos) == null, "no leftover block entity");
        } finally { MinecraftForge.EVENT_BUS.unregister(protection); }
        h.getLevel().setBlockAndUpdate(pos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.assertTrue(!FlarePlacement.place(h.getLevel(), pos, Blocks.LIGHT.defaultBlockState(), player(h))
                && h.getLevel().getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "never overwrites non-air");
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareSavedLightsCleanupPreservesReplacement(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos first = h.absolutePos(new BlockPos(1, 6, 1));
        BlockPos replaced = first.east();
        var light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        level.setBlockAndUpdate(first, light); level.setBlockAndUpdate(replaced, Blocks.GOLD_BLOCK.defaultBlockState());
        CompoundTag saved = new CompoundTag(); ListTag entries = new ListTag();
        for (BlockPos pos : new BlockPos[]{first, replaced}) {
            CompoundTag entry = new CompoundTag(); entry.putLong("Pos", pos.asLong()); entry.putLong("Expires", level.getGameTime() - 1); entries.add(entry);
        }
        saved.put("Lights", entries);
        FlareLights loaded = FlareLights.load(FlareLights.load(saved).save(new CompoundTag()));
        h.assertTrue(loaded.size() == 2, "all saved coordinates survive load/save");
        loaded.cleanup(level);
        h.assertTrue(loaded.size() == 0 && level.isEmptyBlock(first), "expired saved light removed");
        h.assertTrue(level.getBlockState(replaced).is(Blocks.GOLD_BLOCK), "replacement not deleted by stale record");
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression", timeoutTicks = 220)
    public static void flareSupplyLootAndEmptyCountdown(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(pos, FlareContent.CRATE.get().defaultBlockState());
        var crate = (SupplyCrateBlockEntity) level.getBlockEntity(pos);
        crate.setLootTable(YujiContent.id("chests/yuji_airdrop"), 987654321L);
        h.assertTrue(crate.saveWithFullMetadata().contains("LootTable"), "loot remains unresolved until access");
        crate.unpackLootTable(player(h));
        h.assertTrue(!crate.isEmpty() && !crate.saveWithFullMetadata().contains("LootTable"), "first access generates real datapack loot");
        var saved = crate.saveWithFullMetadata(); crate.unpackLootTable(player(h));
        h.assertTrue(crate.saveWithFullMetadata().equals(saved), "second access never rerolls");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(pos), "container chunk ready"))
                .thenExecute(() -> {
                    crate.clearContent();
                    h.runAfterDelay(99, () -> h.assertTrue(level.getBlockState(pos).is(FlareContent.CRATE.get()), "empty crate lasts five seconds"));
                    h.runAfterDelay(105, () -> { h.assertTrue(level.isEmptyBlock(pos), "empty crate disappears"); h.succeed(); });
                });
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareSupplyBarrelContainsGuaranteedKit(GameTestHelper h) {
        var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        var loot = h.getLevel().getServer().getLootData().getLootTable(YujiContent.id("chests/yuji_supply_drop")).getRandomItems(params);
        for (var entry : java.util.Map.of(FlareContent.GUN.get(), 1, FlareContent.RED.get(), 2, FlareContent.YELLOW.get(), 2, FlareContent.GREEN.get(), 1).entrySet())
            h.assertTrue(loot.stream().filter(s -> s.is(entry.getKey())).mapToInt(ItemStack::getCount).sum() == entry.getValue(), "guaranteed supply: " + entry.getKey());
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_regression")
    public static void flareGreenOpenSkyDoesNotRequireSunlight(GameTestHelper h) {
        var end = h.getLevel().getServer().getLevel(Level.END);
        BlockPos pos = new BlockPos(0, end.getMaxBuildHeight() - 6, 0);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) end.getChunk(pos.offset(x, 0, z));
        h.assertTrue(!end.dimensionType().hasSkyLight(), "end is a no-skylight positive control");
        h.assertTrue(FlarePlacement.openSky(end, pos), "unobstructed end sky accepted");
        h.succeed();
    }

    private FlareRegressionGameTests() { }
}
