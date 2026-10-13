package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_death_tests")
@PrefixGameTestTemplate(false)
public final class YujiDeathGameTests {
    private static YujiMonster monster(GameTestHelper h, YujiKind kind) {
        YujiMonster mob = h.spawn(YujiContent.MOBS.get(kind).get(), new BlockPos(2, 2, 2));
        mob.setNoAi(true);
        mob.setNoGravity(true);
        return mob;
    }

    private static void kill(YujiMonster mob) {
        mob.hurt(mob.damageSources().genericKill(), Float.MAX_VALUE);
    }

    private static void lifetime(GameTestHelper h, YujiMonster mob, int ticks) {
        for (int tick = 1; tick <= ticks; tick++) {
            mob.tick();
            h.assertTrue(mob.deathTime == tick, "death clock advances from first tick: " + mob.kind() + "/" + tick);
            h.assertTrue(mob.isRemoved() == (tick == ticks), "removal matches animation plus six ticks: " + mob.kind() + "/" + tick);
        }
    }

    @GameTest(templateNamespace = "yuji_death_tests", template = "empty", batch = "yuji")
    public static void yuchiCrawlRemainsProneWhenStill(GameTestHelper h) {
        YujiMonster mob = monster(h, YujiKind.YUCHI);
        mob.setHealth(8);
        h.assertTrue(mob.locomotion(false).equals("crawl"), "stopping does not stand a crawling monster up");
        mob.discard();
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_death_tests", template = "empty", batch = "yuji")
    public static void yuchiFullHealthKillKeepsStandingPose(GameTestHelper h) {
        YujiMonster mob = monster(h, YujiKind.YUCHI);
        kill(mob);
        h.assertTrue(!mob.crawling(), "zero health must not classify a standing death as crawling");
        lifetime(h, mob, 30);
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_death_tests", template = "empty", batch = "yuji")
    public static void yuchiCrawlDeathUsesShortLifetime(GameTestHelper h) {
        YujiMonster mob = monster(h, YujiKind.YUCHI);
        mob.setHealth(8);
        kill(mob);
        h.assertTrue(mob.crawling(), "crawl posture is retained after lethal damage");
        lifetime(h, mob, 18);
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_death_tests", template = "empty", batch = "yuji")
    public static void yuchiKillCommandKeepsStandingPose(GameTestHelper h) {
        YujiMonster mob = monster(h, YujiKind.YUCHI);
        mob.kill();
        h.assertTrue(!mob.crawling(), "kill command snapshots the living posture");
        lifetime(h, mob, 30);
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_death_tests", template = "empty", batch = "yuji")
    public static void yujiAllDeathClocksMatchAssets(GameTestHelper h) {
        int[] duration = {30, 26, 22, 26, 30, 26, 26, 34, 32, 46, 66};
        for (YujiKind kind : YujiKind.values()) {
            YujiMonster mob = monster(h, kind);
            kill(mob);
            lifetime(h, mob, duration[kind.ordinal()]);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_death_tests", template = "empty", batch = "yuji")
    public static void yuchiDeathPoseSurvivesTrackingAndReload(GameTestHelper h) {
        for (boolean crawl : new boolean[]{false, true}) {
            YujiMonster mob = monster(h, YujiKind.YUCHI);
            if (crawl) mob.setHealth(8);
            kill(mob);
            String animation = crawl ? "crawl_death" : "death";
            h.assertTrue(mob.deathAnimation().equals(animation), "living pose selects death clip");
            YujiMonster tracked = monster(h, YujiKind.YUCHI);
            tracked.getEntityData().assignValues(mob.getEntityData().getNonDefaultValues());
            h.assertTrue(tracked.deathAnimation().equals(animation), "late tracker receives locked pose");
            tracked.discard();
            CompoundTag tag = new CompoundTag(); mob.addAdditionalSaveData(tag); mob.discard();
            YujiMonster restored = monster(h, YujiKind.YUCHI); restored.readAdditionalSaveData(tag);
            h.assertTrue(restored.deathAnimation().equals(animation), "corpse reload keeps locked pose");
            restored.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_death_tests", template = "empty", batch = "yuji")
    public static void yuchiHealedBeforeDeathUsesStandingClip(GameTestHelper h) {
        YujiMonster mob = monster(h, YujiKind.YUCHI);
        mob.setHealth(8); mob.setHealth(mob.getMaxHealth()); kill(mob);
        h.assertTrue(mob.deathAnimation().equals("death"), "healed monster does not reuse an old crawl state");
        lifetime(h, mob, 30);
        h.succeed();
    }
}
