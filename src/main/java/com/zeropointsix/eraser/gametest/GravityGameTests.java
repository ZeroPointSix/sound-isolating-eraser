package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.*;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityGameTests {
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "GravityQA"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 3, 3))));
        return player;
    }

    @GameTest(template = "empty")
    public static void geometryAndNegativeSnapping(GameTestHelper h) {
        BlockPos center = new BlockPos(-8, 70, -3);
        AABB box = GravityGeometry.bounds(center, 5);
        h.assertTrue(box.getXsize() == 5 && box.getYsize() == 5 && box.getZsize() == 5, "field must be 5 cubed");
        h.assertTrue(box.minX == -10 && box.maxX == -5, "negative coordinates must use exact grid bounds");
        h.assertTrue(GravityGeometry.bounds(center, 8).getYsize() == 8, "configured height must be exact");
        h.assertTrue(GravityGeometry.target(new Vec3(0.1, 70, 0), new Vec3(-1, 0, 0), 3)
                .equals(new BlockPos(-3, 70, 0)), "target must floor, not truncate");
        h.assertTrue(!GravityGeometry.inRange(Vec3.ZERO, new BlockPos(30, 0, 0), 3, 20), "remote targets must fail");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void necklaceOnlyAndInventoryDoesNotGrantPower(GameTestHelper h) {
        FakePlayer player = player(h);
        ItemStack jade = new ItemStack(ModItems.GRAVITY_JADE_PENDANT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, jade);
        h.assertTrue(!GravityEquipment.isEquipped(player), "holding the jade must not grant its power");
        h.assertTrue(ModItems.GRAVITY_JADE_PENDANT.get().canEquip(new SlotContext("necklace", player, 0, false, true), jade), "necklace allowed");
        h.assertTrue(!ModItems.GRAVITY_JADE_PENDANT.get().canEquip(new SlotContext("curio", player, 0, false, true), jade), "generic curio slot rejected");
        h.assertTrue(!ModItems.GRAVITY_JADE_PENDANT.get().canEquip(new SlotContext("ring", player, 0, false, true), jade), "ring rejected");
        var inventory = CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var handler = inventory.getStacksHandler("necklace").orElseThrow();
        handler.getStacks().setStackInSlot(0, jade);
        h.assertTrue(GravityEquipment.isEquipped(player), "real necklace slot grants power");
        handler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        h.assertTrue(!GravityEquipment.isEquipped(player), "unequipping revokes power immediately");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsUnequippedAndWrongDimension(GameTestHelper h) {
        FakePlayer player = player(h);
        BlockPos center = BlockPos.containing(player.getEyePosition().add(0, 0, 8));
        h.assertTrue(!GravityFieldController.canActivate(player, h.getLevel().dimension().location(), center), "no necklace must reject");
        CuriosApi.getCuriosInventory(player).resolve().orElseThrow().getStacksHandler("necklace")
                .orElseThrow().getStacks().setStackInSlot(0, new ItemStack(ModItems.GRAVITY_JADE_PENDANT.get()));
        h.assertTrue(!GravityFieldController.canActivate(player, Level.NETHER.location(), center), "stale dimension must reject");
        h.assertTrue(!GravityFieldController.canActivate(player, h.getLevel().dimension().location(), center.offset(1000, 0, 0)), "remote packet rejected");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void terrainOnlyCrushesOneExposedLayer(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos soil = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(soil.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(soil, Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(soil.above(), Blocks.GRASS.defaultBlockState());
        BlockPos leaves = soil.offset(1, 0, 0);
        BlockPos stone = soil.offset(2, 0, 0);
        BlockPos chest = soil.offset(3, 0, 0);
        level.setBlockAndUpdate(leaves, Blocks.OAK_LEAVES.defaultBlockState());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        GravityTerrainCrush.crush(level, player(h), new AABB(soil.offset(0, -1, 0), soil.offset(4, 3, 1)), 1);
        h.assertTrue(level.isEmptyBlock(soil), "exposed soil must break with probability 1");
        h.assertTrue(level.getBlockState(soil.below()).is(Blocks.DIRT), "lower soil must remain");
        h.assertTrue(level.isEmptyBlock(leaves), "tagged leaves must break");
        h.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "stone must remain");
        h.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "chest must remain");
        level.setBlockAndUpdate(soil, Blocks.DIRT.defaultBlockState());
        GravityTerrainCrush.crush(level, player(h), new AABB(soil, soil.offset(1, 1, 1)), 0);
        h.assertTrue(level.getBlockState(soil).is(Blocks.DIRT), "probability zero must never break soil");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 340)
    public static void fieldSlowsHurtsAndExpires(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(3, 4, 3));
        Cow cow = EntityType.COW.create(level);
        cow.setPos(Vec3.atCenterOf(center));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        level.addFreshEntity(cow);
        GravityFieldEntity field = GravityFieldEntity.create(level, UUID.randomUUID(), center);
        level.addFreshEntity(field);
        float health = cow.getHealth();
        h.runAtTickTime(22, () -> {
            h.assertTrue(cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "field continuously applies slowness");
            h.assertTrue(cow.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "slowness II amplifier");
            h.assertTrue(cow.getHealth() == health - 1, "one damage pulse after 20 ticks");
            cow.setPos(Vec3.atCenterOf(center.offset(6, 0, 0)));
        });
        h.runAtTickTime(34, () -> h.assertTrue(!cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowness expires quickly after leaving"));
        h.runAtTickTime(302, () -> {
            h.assertTrue(field.isRemoved(), "field must expire after 300 ticks");
            cow.discard();
            h.succeed();
        });
    }

    private GravityGameTests() { }
}
