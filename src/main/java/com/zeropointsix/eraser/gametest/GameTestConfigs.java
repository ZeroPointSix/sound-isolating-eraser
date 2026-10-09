package com.zeropointsix.eraser.gametest;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Moves a loaded server config spec onto an in-memory copy for one GameTest.
 * File-backed specs autosave every {@code ConfigValue.set()} and Forge's file
 * watcher then reloads asynchronously, which can overwrite a value the test
 * just set (observed on CI in pillCreativeMilkAndOptionalEffectsConfiguration).
 * The copy keeps mutations deterministic and leaves the real TOML untouched.
 */
public final class GameTestConfigs {
    /** Restores the live config when closed; never throws. */
    public interface LiveConfig extends AutoCloseable {
        @Override void close();
    }

    public static LiveConfig sandbox(ForgeConfigSpec spec) {
        ModConfig config = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
                .filter(candidate -> candidate.getSpec() == spec)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("server config not registered for spec"));
        CommentedConfig live = config.getConfigData();
        spec.setConfig(CommentedConfig.copy(live));
        return () -> spec.setConfig(live);
    }

    private GameTestConfigs() {}
}
