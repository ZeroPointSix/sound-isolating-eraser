package com.zeropointsix.eraser.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Additional admission limits; existing local and story rules still apply. */
public final class SpawnCapConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue DIMENSION_CAP, CHUNK_CAP, PER_LOADED_CHUNK, MINIMUM_CAP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("spawnCaps");
        DIMENSION_CAP = builder.comment("Maximum tracked Yuji/shadow/neon entities per dimension. -1 disables this ceiling; 0 blocks new automatic spawns. Existing entities are never removed.")
                .defineInRange("dimensionCap", 512, -1, 1_000_000);
        CHUNK_CAP = builder.comment("Additional limit in the candidate's 16x16 chunk, shared by all controlled kinds. -1 disables; 0 blocks admission.")
                .defineInRange("chunkCap", 64, -1, 1_000_000);
        PER_LOADED_CHUNK = builder.comment("Scale the dimension budget by the server chunk cache's loaded chunk count, not by overlapping player areas. -1 disables scaling; 0 blocks admission.")
                .defineInRange("entitiesPerLoadedChunk", 2, -1, 1_000_000);
        MINIMUM_CAP = builder.comment("Floor for positive loaded-chunk scaling. Effective budget is min(dimensionCap, max(minimumCap, loadedChunks * entitiesPerLoadedChunk)), ignoring disabled ceilings.")
                .defineInRange("minimumScaledCap", 128, 0, 1_000_000);
        builder.pop();
        SPEC = builder.build();
    }

    public static int dimensionLimit(int loadedChunks) {
        return effectiveLimit(DIMENSION_CAP.get(), PER_LOADED_CHUNK.get(), MINIMUM_CAP.get(), loadedChunks);
    }

    public static int effectiveLimit(int ceiling, int perChunk, int minimum, int loadedChunks) {
        if (perChunk < 0) return ceiling;
        long scaled = perChunk == 0 ? 0 : Math.max(minimum, (long) Math.max(0, loadedChunks) * perChunk);
        int limit = (int) Math.min(Integer.MAX_VALUE, scaled);
        return ceiling < 0 ? limit : Math.min(ceiling, limit);
    }

    private SpawnCapConfig() { }
}
