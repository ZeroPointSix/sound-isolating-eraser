package com.zeropointsix.eraser.eraser;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

/** Two surface ports per tile, shared by the preview and persistent chalk models. */
public enum EraserStroke implements StringRepresentable {
    POINT(0), X(68), Z(17), NW_SE(136), NE_SW(34),
    E_SW(36), W_SE(72), NW_S(144), N_SW(33),
    NE_W(66), E_NW(132), SE_N(9), S_NE(18);

    public static final int[][] PORTS = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};
    public final int mask;

    EraserStroke(int mask) { this.mask = mask; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

    public static EraserStroke at(List<BlockPos> bases, BlockPos base) {
        int mask = 0;
        for (int i = 0; i < PORTS.length; i++) {
            if (bases.contains(base.offset(PORTS[i][0], 0, PORTS[i][1]))) mask |= 1 << i;
        }
        if (Integer.bitCount(mask) == 1) mask |= ((mask << 4) | (mask >> 4)) & 255;
        for (EraserStroke stroke : values()) if (stroke.mask == mask) return stroke;
        return POINT;
    }
}
