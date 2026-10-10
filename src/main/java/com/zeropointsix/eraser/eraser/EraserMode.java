package com.zeropointsix.eraser.eraser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public enum EraserMode {
    POINT, ACROSS, FORWARD, DIAGONAL, RING;

    private static final String NBT_KEY = "EraserDrawMode";

    public static EraserMode read(ItemStack stack) {
        int index = stack.hasTag() ? stack.getTag().getInt(NBT_KEY) : 0;
        return index >= 0 && index < values().length ? values()[index] : POINT;
    }

    public void write(ItemStack stack) { stack.getOrCreateTag().putInt(NBT_KEY, ordinal()); }
    public EraserMode next() { return values()[(ordinal() + 1) % values().length]; }
    public Component label() {
        return Component.translatable("eraser.mode." + name().toLowerCase(Locale.ROOT));
    }

    public List<BlockPos> bases(BlockPos origin, Direction facing) {
        Direction forward = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        Direction right = forward.getClockWise();
        List<BlockPos> result = new ArrayList<>();
        if (this == POINT) return List.of(origin.immutable());
        if (this == RING) {
            int[][] offsets = {{-1,-2},{0,-2},{1,-2},{2,-1},{2,0},{2,1},
                    {1,2},{0,2},{-1,2},{-2,1},{-2,0},{-2,-1}};
            for (int[] offset : offsets) result.add(origin.offset(offset[0], 0, offset[1]));
        } else {
            for (int i = -2; i <= 2; i++) {
                result.add(origin.relative(right, this == FORWARD ? 0 : i)
                        .relative(forward, this == ACROSS ? 0 : i));
            }
        }
        return List.copyOf(result);
    }
}
