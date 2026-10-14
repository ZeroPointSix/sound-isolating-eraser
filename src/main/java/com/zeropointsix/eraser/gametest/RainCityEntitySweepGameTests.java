package com.zeropointsix.eraser.gametest;
import com.google.gson.JsonParser;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import com.zeropointsix.eraser.yuji.city.*;
import java.io.InputStreamReader;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import com.zeropointsix.eraser.yuji.city.RainCityEvents;
import com.zeropointsix.eraser.yuji.city.RecliningBuddha;
import com.zeropointsix.eraser.yuji.city.RainCityRules;
import com.zeropointsix.eraser.yuji.city.RainCityTemplates;
import com.zeropointsix.eraser.yuji.city.RainCityData;
import com.zeropointsix.eraser.yuji.city.RainCitySpawns;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Kept out of RainCityGameTests: the city workflow counts that class's tests against batch yuji_city. */
@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class RainCityEntitySweepGameTests {
    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_city_iter", timeoutTicks = 400)
    public static void citySpawnTickDiscardsWithoutCorruptingEntityIteration(GameTestHelper h) {
        // Run the city sweep against the test level: its chunks are entity-ticking, so these mobs
        // are in the live entity lookup exactly as they are around a real player in the city.
        var level = h.getLevel(); var server = level.getServer();
        var difficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        java.util.List<com.zeropointsix.eraser.yuji.YujiMonster> mobs = new java.util.ArrayList<>();
        for (int i = 0; i < 300; i++) {
            var mob = com.zeropointsix.eraser.yuji.YujiContent.MOBS.get(com.zeropointsix.eraser.yuji.YujiKind.YUCHI).get().create(level);
            mob.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2 + (i % 3), 1.5)));
            mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true);
            mob.getPersistentData().putInt("YujiCityDay", 0);
            if (level.addFreshEntity(mob)) mobs.add(mob);
        }
        boolean[] ran = new boolean[1];
        h.onEachTick(() -> {
            if (ran[0] || level.getGameTime() % 20 != 0) return;
            ran[0] = true;
            try {
                RainCitySpawns.tick(level, new RainCityData(), java.util.List.of(), 0);
                h.assertTrue(mobs.stream().noneMatch(m -> m.isAlive()), "stray mobs discarded during the sweep");
                h.succeed();
            } finally {
                server.setDifficulty(difficulty, true);
                mobs.forEach(m -> { if (m.isAlive()) m.discard(); });
            }
        });
    }
    private RainCityEntitySweepGameTests() { }
}
