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

    /** Exclusive ranges partition every row without truncation. */
    public static java.util.List<int[]> pages(int[] heights, int budget) {
        if (budget < 1) throw new IllegalArgumentException("Positive page height required");
        var result = new java.util.ArrayList<int[]>();
        int start = 0;
        int used = 0;
        for (int i = 0; i < heights.length; i++) {
            if (heights[i] < 0 || heights[i] > budget) throw new IllegalArgumentException("Row must fit page");
            if (used + heights[i] > budget && i > start) {
                result.add(new int[]{start, i});
                start = i;
                used = 0;
            }
            used += heights[i];
        }
        if (start < heights.length) result.add(new int[]{start, heights.length});
        return java.util.List.copyOf(result);
    }

    private ItemGuideLayout() { }
}
