package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class SupplyCrateBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private long emptySince = -1;
    public SupplyCrateBlockEntity(BlockPos pos, BlockState state) { super(FlareContent.CRATE_ENTITY.get(), pos, state); }
    @Override public int getContainerSize() { return 27; }
    @Override protected Component getDefaultName() { return Component.translatable("block.qihai.supply_crate"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return ChestMenu.threeRows(id, inventory, this); }
    @Override public void unpackLootTable(@javax.annotation.Nullable Player player) {
        var original = lootTable;
        super.unpackLootTable(player);
        if (original != null && lootTable == null && level instanceof ServerLevel server
                && original.equals(com.zeropointsix.eraser.yuji.YujiContent.id("chests/yuji_airdrop"))) {
            var params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition));
            if (player != null) params.withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck());
            var table = server.getServer().getLootData().getLootTable(original);
            for (int i = 1; i < FlareConfig.LOOT_ROLLS.get(); i++) table.fill(this, params.create(LootContextParamSets.CHEST), server.random.nextLong());
            setChanged();
        }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items = NonNullList.withSize(27, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        emptySince = tag.contains("EmptySince") ? tag.getLong("EmptySince") : -1;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        tag.putLong("EmptySince", emptySince);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, SupplyCrateBlockEntity crate) {
        if (crate.lootTable != null) return;
        boolean empty = crate.items.stream().allMatch(ItemStack::isEmpty);
        if (!empty) {
            if (crate.emptySince >= 0) { crate.emptySince = -1; crate.setChanged(); }
        } else if (crate.emptySince < 0) { crate.emptySince = level.getGameTime(); crate.setChanged(); }
        else if (level.getGameTime() - crate.emptySince >= FlareConfig.EMPTY_LIFETIME.get()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
}
