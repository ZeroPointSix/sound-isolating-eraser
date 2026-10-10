package com.zeropointsix.eraser.brickrot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraftforge.common.ForgeSpawnEggItem;

public final class BrickrotSpawnEggItem extends ForgeSpawnEggItem {
    public BrickrotSpawnEggItem(Properties properties) {
        super(BrickrotContent.WALL, 0x8E3B2E, 0x80807C, properties);
    }

    // Air/water use and dispenser behavior deliberately remain vanilla.
    @Override public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (context.getLevel().isClientSide || context.getPlayer() == null
                || getType(stack.getTag()) != BrickrotContent.WALL.get()
                || context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SpawnerBlockEntity)
            return super.useOn(context);

        CompoundTag original = stack.getTag();
        CompoundTag temporary = original == null ? new CompoundTag() : original.copy();
        CompoundTag entityTag = temporary.getCompound("EntityTag");
        ListTag previousRotation = entityTag.getList("Rotation", Tag.TAG_FLOAT);
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(context.getPlayer().getYRot()));
        rotation.add(FloatTag.valueOf(previousRotation.size() >= 2 ? previousRotation.getFloat(1) : 0));
        entityTag.put("Rotation", rotation);
        temporary.put("EntityTag", entityTag);
        // Vanilla applies EntityTag after its random spawn yaw; retain its spawning and consumption path.
        stack.setTag(temporary);
        try {
            return super.useOn(context);
        } finally {
            stack.setTag(original);
        }
    }
}
