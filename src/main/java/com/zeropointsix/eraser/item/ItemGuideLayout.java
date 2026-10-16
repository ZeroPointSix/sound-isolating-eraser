package com.zeropointsix.eraser.item;

/** Geometry shared by the client and dedicated-server regression tests. */
public final class ItemGuideLayout {
    public static final int PADDING = 4;

    public static boolean fits(int width, int height, int screenWidth, int screenHeight) {
        return width + 2 * PADDING <= screenWidth && height + 2 * PADDING <= screenHeight;
    }

    public static int clamp(int origin, int size, int screenSize) {
        return Math.max(PADDING, Math.min(origin, screenSize - size - PADDING));
    }

    private ItemGuideLayout() { }
}
