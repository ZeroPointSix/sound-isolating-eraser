package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.ModMain;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeHooks;

public final class GravityTerrainCrush {
    public static final TagKey<Block> FRAGILE = TagKey.create(Registries.BLOCK,
            new ResourceLocation(ModMain.MOD_ID, "gravity_fragile"));
    public static final TagKey<Block> SURFACE = TagKey.create(Registries.BLOCK,
            new ResourceLocation(ModMain.MOD_ID, "gravity_surface_fragile"));

    public static void crush(ServerLevel level, ServerPlayer player, AABB box, double chance) {
        if (!player.mayBuild()) return;
        List<BlockPos> fragile = new ArrayList<>();
        List<BlockPos> soil = new ArrayList<>();
        // Snapshot both sets before neighbor updates can expose a second layer.
        for (int x = (int) box.minX; x < box.maxX; x++) {
            for (int z = (int) box.minZ; z < box.maxZ; z++) {
                boolean selected = false;
                for (int y = (int) box.maxY - 1; y >= box.minY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(SURFACE)) {
                        if (!selected && exposed(level.getBlockState(pos.above()))) {
                            selected = true;
                            if (level.random.nextDouble() < chance) soil.add(pos);
                        }
                    } else if (state.is(FRAGILE)) {
                        fragile.add(pos);
                    }
                }
            }
        }
        fragile.forEach(pos -> breakBlock(level, player, pos));
        soil.forEach(pos -> breakBlock(level, player, pos));
    }

    private static boolean exposed(BlockState above) {
        return above.isAir() || (above.canBeReplaced() && above.getFluidState().isEmpty()
                && (above.getBlock() instanceof BushBlock || above.getBlock() instanceof VineBlock));
    }

    private static void breakBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0
                || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, net.minecraft.core.Direction.UP, player.getMainHandItem())) return;
        if (ForgeHooks.onBlockBreakEvent(level, player.gameMode.getGameModeForPlayer(), player, pos) != -1) {
            level.destroyBlock(pos, true, player);
        }
    }

    private GravityTerrainCrush() { }
}
