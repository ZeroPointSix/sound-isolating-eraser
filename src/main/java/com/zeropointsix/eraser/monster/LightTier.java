package com.zeropointsix.eraser.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public enum LightTier {
    DARK, DIM, BRIGHT;

    public static LightTier fromBrightness(int brightness) {
        return brightness <= 6 ? DARK : brightness <= 10 ? DIM : BRIGHT;
    }

    public static LightTier of(Level level, BlockPos pos) {
        return fromBrightness(level.getMaxLocalRawBrightness(pos));
    }
}
