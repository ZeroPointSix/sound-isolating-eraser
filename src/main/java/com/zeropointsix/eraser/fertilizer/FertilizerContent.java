package com.zeropointsix.eraser.fertilizer;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.grower.OakTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FertilizerContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModMain.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModMain.MOD_ID);
    public static final TagKey<Block> FERTILE = TagKey.create(Registries.BLOCK, id("fertile_blocks"));
    public static final RegistryObject<Block> LOG = BLOCKS.register("fertile_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG)) {
                @Override
                public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState replacement, boolean moved) {
                    if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server)
                        FertilizerData.get(server).invalidateRoot(pos);
                    super.onRemove(state, level, pos, replacement, moved);
                }
            });
    public static final RegistryObject<Block> LEAVES = BLOCKS.register("fertile_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)));
    public static final RegistryObject<Block> PLANKS = BLOCKS.register("fertile_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SAPLING = BLOCKS.register("fertile_sapling",
            () -> new SaplingBlock(new OakTreeGrower(), BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)) {
                @Override
                public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
                    if (state.getValue(STAGE) == 0) level.setBlock(pos, state.cycle(STAGE), 4);
                    else FertilizerGrowth.plant(level, pos, null);
                }
            });
    public static final RegistryObject<Item> BAG = ITEMS.register("super_fertilizer_bag", FertilizerBagItem::new);
    public static final RegistryObject<Item> EMPTY_BAG = ITEMS.register("empty_fertilizer_bag", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FRUIT = ITEMS.register("fertile_fruit", FertileFruitItem::new);

    static {
        for (RegistryObject<Block> block : java.util.List.of(LOG, LEAVES, PLANKS, SAPLING)) {
            ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(ModMain.MOD_ID, path); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    private FertilizerContent() {}
}
