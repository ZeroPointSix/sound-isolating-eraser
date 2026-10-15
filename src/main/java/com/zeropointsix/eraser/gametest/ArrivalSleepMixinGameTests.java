package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.eternalnight.NightEvents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CityArrivalSleepMixin really hooks LivingEntity.stopSleeping: arrival sleep survives a wake call. */
@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArrivalSleepMixinGameTests {
    @GameTest(template = "empty")
    public static void arrivalSleepCancelsStopSleeping(GameTestHelper h) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ArrivalQA"));
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        p.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        try {
            p.startSleeping(pos);
            NightEvents.beginArrivalSleepForTest(p, 40);
            p.stopSleeping();
            h.assertTrue(p.isSleeping(), "stopSleeping is cancelled during arrival sleep");
            NightEvents.clearTransientState();
            p.stopSleeping();
            h.assertTrue(!p.isSleeping(), "stopSleeping works normally outside arrival sleep");
        } finally {
            NightEvents.clearTransientState();
        }
        h.succeed();
    }
}
