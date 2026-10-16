package com.zeropointsix.eraser.eternalnight;

/** Client presentation clock, independent of chunk loading and Minecraft rendering. */
public final class CityEntryOverlay {
    public static final int FADE_IN_TICKS = 20;
    public static final int CAPTION_TICKS = 40;
    public static final int MIN_ENTRY_TICKS = FADE_IN_TICKS + CAPTION_TICKS;
    private int remaining;
    private int total = 1;
    private boolean hold;

    public void begin(int ticks, boolean hold) {
        this.total = Math.max(1, ticks);
        this.remaining = Math.max(0, ticks);
        this.hold = hold;
    }

    public void tick() {
        if (remaining > 0) remaining--;
    }

    public void clear() { begin(0, false); }

    public int alpha() {
        return hold ? 255 - remaining * 255 / total : remaining * 255 / total;
    }

    public boolean showsCaption() { return hold && remaining == 0; }
}
