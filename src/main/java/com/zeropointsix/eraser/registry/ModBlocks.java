package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserBarrierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ModMain.MOD_ID);

    public static final RegistryObject<EraserAnchorBlock> ERASER_ANCHOR =
            BLOCKS.register("eraser_anchor", EraserAnchorBlock::new);

    public static final RegistryObject<EraserBarrierBlock> ERASER_BARRIER =
            BLOCKS.register("eraser_barrier", EraserBarrierBlock::new);

    private ModBlocks() {
    }
}
