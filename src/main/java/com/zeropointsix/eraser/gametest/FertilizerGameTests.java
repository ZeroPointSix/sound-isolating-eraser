package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.fertilizer.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FertilizerGameTests {
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "FertilizerQA"));
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 3, 2))));
        return p;
    }

    private static void use(FakePlayer player, BlockPos pos) {
        ItemStack stack = player.getMainHandItem();
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    @GameTest(template = "empty")
    public static void finalizedNumbersAndRecipe(GameTestHelper h) {
        h.assertTrue(FertilizerConfig.TREE_COUNT.get() == 15, "final table specifies 15 trees");
        h.assertTrue(FertilizerConfig.HEAL.get() == 6.0, "functional paragraph specifies 6 HP");
        h.assertTrue(FertilizerConfig.BONE_MEAL.get() == 10, "ten real bonemeal attempts");
        var recipe = h.getLevel().getRecipeManager().byKey(FertilizerContent.id("fertile_planks")).orElseThrow();
        ItemStack result = recipe.getResultItem(h.getLevel().registryAccess());
        h.assertTrue(result.is(FertilizerContent.PLANKS.get().asItem()) && result.getCount() == 4, "one fertile log produces four planks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bagSpendsOnlySuccessAndBecomesExactlyOneEmptyBag(GameTestHelper h) {
        FakePlayer p = player(h);
        ItemStack bag = new ItemStack(FertilizerContent.BAG.get());
        bag.getOrCreateTag().putInt("FertilizerCapacity", 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, bag);
        BlockPos crop = h.absolutePos(new BlockPos(4, 2, 4));
        h.getLevel().setBlock(crop, Blocks.STONE.defaultBlockState(), 3);
        use(p, crop);
        h.assertTrue(bag.getDamageValue() == 0, "invalid target must not spend a grain");
        for (int n = 0; n < 2; n++) {
            h.getLevel().setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            h.getLevel().setBlock(crop, Blocks.WHEAT.defaultBlockState(), 3);
            p.getCooldowns().removeCooldown(FertilizerContent.BAG.get());
            use(p, crop);
            h.assertTrue(((CropBlock) Blocks.WHEAT).isMaxAge(h.getLevel().getBlockState(crop)), "one use ripens wheat");
        }
        h.assertTrue(p.getMainHandItem().is(FertilizerContent.EMPTY_BAG.get()) && p.getMainHandItem().getCount() == 1, "last grain replaces bag once");
        h.assertTrue(!bag.isEnchanted() && !bag.hasFoil(), "bag has no glint");
        h.assertTrue(!bag.isRepairable(), "crafting repair cannot mint extra fertilizer");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fruitHealsSixAndOnlyClearsHarmfulEffects(GameTestHelper h) {
        FakePlayer p = player(h);
        p.setHealth(4); p.getFoodData().setFoodLevel(2);
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 1000));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1000));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1000));
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000));
        new ItemStack(FertilizerContent.FRUIT.get()).finishUsingItem(h.getLevel(), p);
        h.assertTrue(p.getHealth() == 10, "instant healing is exactly six HP before ticks");
        h.assertTrue(p.getFoodData().getFoodLevel() == 10, "fruit provides eight nutrition");
        h.assertTrue(!p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "clear all harmful effects once");
        h.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION) && p.hasEffect(MobEffects.GLOWING), "preserve beneficial and neutral effects");
        var effect = p.getEffect(MobEffects.REGENERATION);
        h.assertTrue(effect != null && effect.getAmplifier() == 1 && effect.getDuration() == 3600, "regeneration II lasts 3600 ticks");
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 1000));
        h.assertTrue(p.hasEffect(MobEffects.POISON), "fruit does not grant poison immunity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void auraCountsAllMaterialsAndPreservesExternalRegeneration(GameTestHelper h) {
        FakePlayer p = player(h);
        BlockPos center = p.blockPosition();
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-2,-2,-2), center.offset(2,2,2))) {
            h.getLevel().setBlock(q, Blocks.AIR.defaultBlockState(), 2); positions.add(q.immutable());
        }
        for (int i=0;i<9;i++) h.getLevel().setBlock(positions.get(i), FertilizerContent.LOG.get().defaultBlockState(), 2);
        h.assertTrue(!FertileAura.nearby(h.getLevel(), center), "nine blocks are below threshold");
        h.getLevel().setBlock(positions.get(9), FertilizerContent.PLANKS.get().defaultBlockState(), 2);
        h.getLevel().setBlock(positions.get(0), FertilizerContent.LEAVES.get().defaultBlockState(), 2);
        h.assertTrue(FertileAura.nearby(h.getLevel(), center), "leaves, planks and logs all count");
        try {
            FertileAura.update(p);
            h.assertTrue(p.getEffect(MobEffects.REGENERATION).getAmplifier() == 0, "aura only supplies I");
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 3600, 1));
            p.setPos(p.position().add(12,0,0)); FertileAura.update(p);
            var external = p.getEffect(MobEffects.REGENERATION);
            h.assertTrue(external != null && external.getAmplifier() == 1 && external.getDuration() == 3600, "leaving cannot remove fruit regeneration");
        } finally { FertileAura.clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void movingAwayRemovesOnlyOwnedAura(GameTestHelper h) {
        FakePlayer p = player(h); BlockPos c = p.blockPosition();
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) h.getLevel().setBlock(c.offset(x,-1,z), FertilizerContent.PLANKS.get().defaultBlockState(),2);
        try {
            FertileAura.update(p);
            h.assertTrue(p.hasEffect(MobEffects.REGENERATION), "house materials grant aura");
            p.setPos(p.position().add(20,0,0)); FertileAura.update(p);
            h.assertTrue(!p.hasEffect(MobEffects.REGENERATION), "owned aura stops on first update after leaving");
        } finally { FertileAura.clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedTreeDoesNotConsumeOrLeavePartialBlocks(GameTestHelper h) {
        FakePlayer p = player(h); BlockPos root = h.absolutePos(new BlockPos(5,2,5));
        h.getLevel().setBlock(root.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        h.getLevel().setBlock(root,Blocks.OAK_SAPLING.defaultBlockState(),3);
        h.getLevel().setBlock(root.above(2),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!FertilizerGrowth.use(h.getLevel(),root,p), "blocked canopy must reject generation");
        h.assertTrue(h.getLevel().getBlockState(root).is(Blocks.OAK_SAPLING), "original sapling remains");
        h.assertTrue(h.getLevel().getBlockState(root.above(2)).is(Blocks.STONE), "obstruction remains");
        h.assertTrue(FertilizerData.get(h.getLevel()).at(h.getLevel(),root)==null,"failed generation creates no saved identity");
        h.succeed();
    }

    @GameTest(template = "fertilizer_arena", timeoutTicks = 300, batch = "fertilizer_growth")
    public static void fifteenTreeGroveIndependentDosesPersistenceAndThreeTiers(GameTestHelper h) {
        ServerLevel level=h.getLevel(); FakePlayer a=player(h), b=player(h);
        BlockPos root=h.absolutePos(new BlockPos(40,9,40));
        for (int x=-30;x<=30;x++) for(int z=-30;z<=30;z++) {
            level.getChunkAt(root.offset(x,0,z));
            for(int y=-8;y<0;y++) level.setBlock(root.offset(x,y,z), (y==-1?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),2);
        }
        level.setBlock(root,Blocks.OAK_SAPLING.defaultBlockState(),3);
        int before=FertilizerData.get(level).size();
        h.assertTrue(FertilizerGrowth.use(level,root,a),"first use grows a fertile tree");
        var data=FertilizerData.get(level); var tree=data.at(level,root);
        h.assertTrue(tree!=null && tree.doses.get(a.getUUID())==1,"ten bonemeal attempts count as one dose");
        h.assertTrue(FertilizerGrowth.use(level,root,a),"A dose two");
        h.assertTrue(FertilizerGrowth.use(level,root,b),"B dose one");
        h.assertTrue(FertilizerGrowth.use(level,root,b),"B dose two");
        h.assertTrue(!tree.grove,"players cannot combine their doses");
        var restored=FertilizerData.load(data.save(new CompoundTag()));
        h.assertTrue(restored.at(level,root).doses.get(a.getUUID())==2 && restored.at(level,root).doses.get(b.getUUID())==2,"NBT round trip preserves independent counters");
        h.assertTrue(FertilizerGrowth.use(level,root,a),"A dose three");
        h.assertTrue(FertilizerGrowth.use(level,root,a),"A dose four");
        h.assertTrue(FertilizerGrowth.use(level,root,a),"fifth A dose generates the complete grove");
        h.assertTrue(data.size()-before==15 && tree.grove,"grove contains exactly fifteen total trees");
        level.setBlock(root.above(2), Blocks.STONE.defaultBlockState(), 2);
        h.assertTrue(!FertilizerGrowth.use(level,root,a),"old trunk identity cannot authorize overwriting a building");
        h.assertTrue(level.getBlockState(root.above(2)).is(Blocks.STONE),"player replacement remains intact");
        level.setBlock(root.above(2), FertilizerContent.LOG.get().defaultBlockState(), 2);
        h.assertTrue(FertilizerGrowth.use(level,root,a) && tree.level==2,"mature tree grows to tier two");
        h.assertTrue(level.getBlockState(root.below(4)).is(Blocks.ROOTED_DIRT),"tier two roots extend four blocks into soil");
        h.assertTrue(FertilizerGrowth.use(level,root,a) && tree.level==3,"tier three expands the trunk and canopy");
        h.assertTrue(level.getBlockState(root.offset(2,1,2)).is(FertilizerContent.LOG.get()),"tier three has a five by five trunk");
        h.assertTrue(!FertilizerGrowth.use(level,root,a),"tier three is a hard cap");
        level.setBlock(root,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(data.at(level,root)==null,"destroying the root removes its saved identity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionCancellationRollsBackWholeBatch(GameTestHelper h) {
        FakePlayer player=player(h); BlockPos pos=h.absolutePos(new BlockPos(5,2,5));
        // Distinct fixture names avoid collisions in Forge's generated event-handler wrappers.
        class SecondPlacementGuard {
            int placements;
            @SubscribeEvent public void denySecondPlacement(BlockEvent.EntityPlaceEvent event) {
                if (event.getEntity()==player && ++placements==2) event.setCanceled(true);
            }
        }
        SecondPlacementGuard guard=new SecondPlacementGuard(); MinecraftForge.EVENT_BUS.register(guard);
        try {
            GrowthPlan plan=new GrowthPlan(h.getLevel());
            plan.put(pos,FertilizerContent.LOG.get().defaultBlockState());
            plan.put(pos.above(),FertilizerContent.LOG.get().defaultBlockState());
            h.assertTrue(!plan.commit(player),"cancelled place event rejects whole batch");
            h.assertTrue(guard.placements==2,"first block was placed before protection rejected the second");
            h.assertTrue(h.getLevel().isEmptyBlock(pos) && h.getLevel().isEmptyBlock(pos.above()),"both planned blocks roll back");
        } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void itemStackCancellationKeepsLastGrainAndNoIdentity(GameTestHelper h) {
        FakePlayer p=player(h); BlockPos root=h.absolutePos(new BlockPos(5,2,5));
        h.getLevel().setBlock(root.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        h.getLevel().setBlock(root,Blocks.OAK_SAPLING.defaultBlockState(),3);
        ItemStack bag=new ItemStack(FertilizerContent.BAG.get());
        bag.getOrCreateTag().putInt("FertilizerCapacity",1);
        p.setItemInHand(InteractionHand.MAIN_HAND,bag);
        class BagPlacementGuard {
            int denied;
            @SubscribeEvent public void denyBagPlacement(BlockEvent.EntityMultiPlaceEvent event) {
                if(event.getEntity()==p) { denied++; event.setCanceled(true); }
            }
        }
        BagPlacementGuard guard=new BagPlacementGuard(); MinecraftForge.EVENT_BUS.register(guard);
        try {
            use(p,root);
            h.assertTrue(guard.denied>0,"full item use reached and was rejected by the multi-place protection hook");
            h.assertTrue(p.getMainHandItem().is(FertilizerContent.BAG.get()) && bag.getDamageValue()==0,"cancelled last grain remains in its original bag");
            h.assertTrue(h.getLevel().getBlockState(root).is(Blocks.OAK_SAPLING),"whole tree is restored");
            h.assertTrue(FertilizerData.get(h.getLevel()).at(h.getLevel(),root)==null,"cancelled item transaction leaves no plant record");
        } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.succeed();
    }

    @GameTest(template = "fertilizer_arena", timeoutTicks = 200, batch = "fertilizer_meadow")
    public static void tenDoseMeadowWorksOnExistingGrassAndIsConfigurable(GameTestHelper h) {
        ServerLevel level=h.getLevel(); FakePlayer p=player(h); BlockPos ground=h.absolutePos(new BlockPos(40,3,40));
        for(int x=-16;x<=16;x++) for(int z=-16;z<=16;z++) {
            level.getChunkAt(ground.offset(x,0,z));
            level.setBlock(ground.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        }
        level.setBlock(ground.above(),Blocks.GRASS.defaultBlockState(),2);
        int old=FertilizerConfig.THRESHOLD.get(); FertilizerConfig.THRESHOLD.set(10);
        try {
            for(int n=1;n<=10;n++) h.assertTrue(FertilizerGrowth.use(level,ground.above(),p),"valid existing grass accepts dose "+n);
            h.assertTrue(FertilizerData.get(level).at(level,ground).doses.isEmpty(),"ten-dose threshold completes and resets meadow counter");
            int plants=0;
            for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++)
                if(level.getBlockState(ground.offset(x,1,z)).getBlock() instanceof net.minecraft.world.level.block.BushBlock) plants++;
            h.assertTrue(plants>=100,"threshold produces a real broad grass and flower meadow");
        } finally { FertilizerConfig.THRESHOLD.set(old); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryBonemealOnFertileSaplingHonorsOuterCancellation(GameTestHelper h) {
        FakePlayer p=player(h); BlockPos root=h.absolutePos(new BlockPos(5,2,5));
        h.getLevel().setBlock(root.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        h.getLevel().setBlock(root,FertilizerContent.SAPLING.get().defaultBlockState().setValue(SaplingBlock.STAGE,1),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE_MEAL,64));
        class SaplingPlacementGuard {
            int denied;
            @SubscribeEvent public void denySaplingPlacement(BlockEvent.EntityMultiPlaceEvent event) {
                if(event.getEntity()==p) { denied++; event.setCanceled(true); }
            }
        }
        SaplingPlacementGuard guard=new SaplingPlacementGuard(); MinecraftForge.EVENT_BUS.register(guard);
        try {
            for(int i=0;i<40 && guard.denied==0;i++) use(p,root);
            h.assertTrue(guard.denied>0,"normal bonemeal reaches the outer protection hook");
            h.assertTrue(h.getLevel().getBlockState(root).is(FertilizerContent.SAPLING.get()),"ordinary bonemeal cancellation restores the sapling");
            h.assertTrue(FertilizerData.get(h.getLevel()).at(h.getLevel(),root)==null,"root rollback removes natural growth identity");
        } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leavesDropFruitSaplingsAndRespectShears(GameTestHelper h) {
        FakePlayer p=player(h); BlockPos pos=h.absolutePos(new BlockPos(5,3,5));
        var state=FertilizerContent.LEAVES.get().defaultBlockState();
        List<ItemStack> sheared=Block.getDrops(state,h.getLevel(),pos,null,p,new ItemStack(Items.SHEARS));
        h.assertTrue(sheared.size()==1 && sheared.get(0).is(FertilizerContent.LEAVES.get().asItem()),"shears drop only the leaf block");
        int fruit=0,saplings=0;
        for(int n=0;n<1000;n++) for(ItemStack drop:Block.getDrops(state,h.getLevel(),pos,null,p,ItemStack.EMPTY)) {
            if(drop.is(FertilizerContent.FRUIT.get())) fruit++;
            if(drop.is(FertilizerContent.SAPLING.get().asItem())) saplings++;
        }
        h.assertTrue(fruit>0 && saplings>0,"normal leaf loot closes the fruit and sapling economy");
        ItemStack fortune=new ItemStack(Items.DIAMOND_HOE);
        fortune.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE,3);
        boolean simultaneous=false;
        for(int n=0;n<10000 && !simultaneous;n++) {
            var drops=Block.getDrops(state,h.getLevel(),pos,null,p,fortune);
            simultaneous=drops.stream().anyMatch(s -> s.is(FertilizerContent.FRUIT.get()))
                    && drops.stream().anyMatch(s -> s.is(FertilizerContent.SAPLING.get().asItem()));
        }
        h.assertTrue(simultaneous,"fruit and sapling pools roll independently, not mutually exclusively");
        h.succeed();
    }
}
