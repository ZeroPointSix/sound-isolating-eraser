package com.zeropointsix.eraser.eternalnight;

/** A seed-stable plan: 16-block tiles, 64-block blocks, and 512-block boss regions. */
public final class CityLayout {
    public enum District { RESIDENTIAL, COMMERCIAL, INDUSTRIAL }
    private CityLayout() { }

    public static long hash(long seed, int x, int z) {
        long v = seed ^ (x * 341873128712L) ^ (z * 132897987541L);
        v = (v ^ (v >>> 30)) * 0xbf58476d1ce4e5b9L;
        v = (v ^ (v >>> 27)) * 0x94d049bb133111ebL;
        return v ^ (v >>> 31);
    }

    public static District district(int x, int z) {
        int sample = (int) Math.floorMod(hash(243L, Math.floorDiv(x, 128), Math.floorDiv(z, 128)), 100L);
        return sample < 55 ? District.RESIDENTIAL : sample < 70 ? District.COMMERCIAL : District.INDUSTRIAL;
    }

    private static int wave(int block) {
        int n = Math.floorMod(block, 8);
        return n <= 4 ? n : 8 - n;
    }

    public static int plateau(int blockX, int blockZ) {
        return 64 + 3 * (wave(blockX) + wave(blockZ));
    }

    public static boolean street(int x, int z) {
        return Math.floorMod(Math.floorDiv(x, 16), 4) == 0
                || Math.floorMod(Math.floorDiv(z, 16), 4) == 0;
    }

    public static int surface(int x, int z) {
        int bx = Math.floorDiv(x, 64), bz = Math.floorDiv(z, 64);
        int localX = Math.floorMod(x, 64), localZ = Math.floorMod(z, 64);
        double tx = localX < 16 ? localX / 15.0 : 1;
        double tz = localZ < 16 ? localZ / 15.0 : 1;
        double west = plateau(bx - 1, bz - 1) * (1 - tz) + plateau(bx - 1, bz) * tz;
        double east = plateau(bx, bz - 1) * (1 - tz) + plateau(bx, bz) * tz;
        return (int) Math.round(west * (1 - tx) + east * tx);
    }

    public static long region(int x, int z) {
        return ((long) Math.floorDiv(x, 512) << 32) ^ (Math.floorDiv(z, 512) & 0xffffffffL);
    }

    public static String building(long seed, int tileX, int tileZ) {
        District district = district(tileX * 16, tileZ * 16);
        String[] pool = switch (district) {
            case RESIDENTIAL -> new String[]{"res_apartment_mid", "res_apartment_mid", "res_apartment_high",
                    "res_tenement", "res_tenement", "res_apartment_low", "shop_neon"};
            case COMMERCIAL -> new String[]{"office_tower", "office_tower", "office_dark", "shop_neon"};
            case INDUSTRIAL -> new String[]{"warehouse", "warehouse", "office_dark", "res_tenement"};
        };
        return pool[(int) Math.floorMod(hash(seed, tileX, tileZ), pool.length)];
    }
}
