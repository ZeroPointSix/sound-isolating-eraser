package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.flare.FlareConfig;
import com.zeropointsix.eraser.yuji.flare.FlareLights;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("flare_restart_tests")
@PrefixGameTestTemplate(false)
public final class FlareRestartGameTests {
    @GameTest(templateNamespace = "yuji_tests", template = "empty", timeoutTicks = 1300)
    public static void flareSavedWorldSurvivesProcessRestart(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos center = new BlockPos(300, 200, 300);
        level.setChunkForced(center.getX() >> 4, center.getZ() >> 4, true);
        level.getChunkAt(center);
        String phase = System.getProperty("flare.qa.restartPhase", "");
        var lights = FlareLights.get(level);
        if (phase.equals("seed")) {
            h.assertTrue(lights.size() == 0, "seed phase starts in its own fresh world");
            try (var config = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
                FlareConfig.LIGHT_RADIUS.set(3);
                lights.illuminate(level, center, h.makeMockSurvivalPlayer());
            }
            h.assertTrue(lights.size() > 0, "production light field generated before shutdown");
            level.getDataStorage().save();
            System.out.println("FLARE_RESTART_SEEDED sources=" + lights.size() + " gameTime=" + level.getGameTime());
            h.succeed();
            return;
        }
        h.assertTrue(phase.equals("reload"), "restart phase must be seed or reload");
        var positions = new ArrayList<BlockPos>();
        long deadline = 0;
        for (Tag tag : lights.save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            h.assertTrue(pos.distSqr(center) <= 9 && level.getBlockState(pos).is(Blocks.LIGHT),
                    "both SavedData and chunk blocks survived the previous JVM: " + pos);
            positions.add(pos);
            deadline = Math.max(deadline, entry.getLong("Expires"));
        }
        h.assertTrue(!positions.isEmpty(), "new JVM loads actual saved light records");
        long remaining = deadline - level.getGameTime();
        h.assertTrue(remaining > 0 && remaining <= 1200, "saved original expiry remains intact: " + remaining);
        System.out.println("FLARE_RESTART_LOADED sources=" + positions.size() + " remainingTicks=" + remaining);
        h.runAfterDelay(remaining + 15, () -> {
            for (BlockPos pos : positions) h.assertTrue(level.getBlockState(pos).isAir(), "production cleanup after process restart: " + pos);
            h.assertTrue(lights.size() == 0, "production cleanup removes persisted records");
            level.setChunkForced(center.getX() >> 4, center.getZ() >> 4, false);
            System.out.println("FLARE_RESTART_CLEANUP_PASSED sources=" + positions.size());
            h.succeed();
        });
    }
    private FlareRestartGameTests() { }
}
