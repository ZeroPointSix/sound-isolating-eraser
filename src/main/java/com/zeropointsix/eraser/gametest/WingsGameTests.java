package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.wings.FlightEvents;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.wings.WingsState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WingsGameTests {
    private WingsGameTests() {
    }

    private static FakePlayer winged(GameTestHelper h, Vec3 pos, int food) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "WingsQA"));
        player.setPos(pos);
        player.setItemSlot(EquipmentSlot.CHEST,
                new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
        player.getFoodData().setFoodLevel(food);
        return player;
    }

    private static void tick(FakePlayer p) {
        FlightEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deployDeniedBelowMinFood(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)),
                WingsConfig.SERVER.minFoodToDeploy.get() - 1);
        FlightEvents.serverSetDeployed(p, true);
        h.assertTrue(!WingsState.deployed(p), "food<4 must refuse deploy");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deployStowRoundTrip(GameTestHelper h) {
        Vec3 pos = h.absoluteVec(new Vec3(2.5, 3, 2.5));
        FakePlayer p = winged(h, pos, 20);
        FlightEvents.serverSetDeployed(p, true);
        h.assertTrue(WingsState.deployed(p), "fed deploy must succeed");
        h.assertTrue(p.isNoGravity(), "deployed wings must lift gravity");
        h.assertTrue(WingsState.hoverY(p) == pos.y, "hover anchor = deploy Y");
        FlightEvents.serverSetDeployed(p, false);
        h.assertTrue(!WingsState.deployed(p) && !p.isNoGravity(),
                "stow must clear deployed+noGravity");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fastTiersGatedByHunger(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 5);
        FlightEvents.serverSetDeployed(p, true);
        FlightEvents.serverCycleTier(p); // HOVER -> CRUISE
        h.assertTrue(WingsState.tier(p) == WingsState.CRUISE, "food5: CRUISE ok");
        FlightEvents.serverCycleTier(p); // would be BOOST — must clamp back
        h.assertTrue(WingsState.tier(p) == WingsState.CRUISE,
                "food<6 must refuse BOOST/STORM");
        p.getFoodData().setFoodLevel(20);
        FlightEvents.serverCycleTier(p);
        h.assertTrue(WingsState.tier(p) == WingsState.BOOST,
                "fed player must reach BOOST");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void hungerDrainsAndStarveForcesHover(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        FlightEvents.serverSetDeployed(p, true);
        WingsState.setTier(p, WingsState.STORM);
        float before = p.getFoodData().getExhaustionLevel();
        tick(p);
        h.assertTrue(p.getFoodData().getExhaustionLevel() > before,
                "deployed STORM tick must drain hunger (exhaustion grows)");
        // 耗尽饥饿：强制降到悬停 + 缓降
        p.getFoodData().setFoodLevel(0);
        tick(p);
        h.assertTrue(WingsState.tier(p) == WingsState.HOVER,
                "food=0 must force HOVER");
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING),
                "food=0 must grant slow falling");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void chargedFreezesHungerDrain(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        FlightEvents.serverSetDeployed(p, true);
        WingsState.setTier(p, WingsState.STORM);
        WingsState.setChargedUntil(p,
                p.level().getGameTime() + WingsConfig.SERVER.chargedDurationTicks.get());
        float before = p.getFoodData().getExhaustionLevel();
        tick(p);
        h.assertTrue(p.getFoodData().getExhaustionLevel() == before,
                "charged flight must not drain hunger");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blinkChainsThreeThenLocksOut(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        p.setYRot(0); // look south, horizontal blink
        FlightEvents.serverSetDeployed(p, true);
        long now = h.getLevel().getGameTime();
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkTimes(p).length == 1,
                "first blink must be recorded");
        h.assertTrue(WingsState.blinkCooldownUntil(p) == now + 40,
                "single blink → 40t cooldown");
        // 链闪：窗口内第 2、3 下放行
        FlightEvents.serverBlink(p);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkTimes(p).length == 3,
                "chain window must allow 3 blinks");
        h.assertTrue(WingsState.blinkLockUntil(p) == now + 160,
                "3rd chained blink → 160t long lock");
        FlightEvents.serverBlink(p); // 4th must be rejected
        h.assertTrue(WingsState.blinkTimes(p).length == 3,
                "4th blink inside window must be rejected");
        // 位移本身依赖 teleportToWithTicket 的区块 ticket，FakePlayer 不生效；
        // 真实位移由客户端 E2E 覆盖，此处只验收状态机
        h.succeed();
    }

    /** 160t 链满长锁必须独立于窗口内残留记录生效（Review #1 边界绕过修复）。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blinkLongLockSurvivesWindowEdge(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        p.setYRot(0);
        FlightEvents.serverSetDeployed(p, true);
        long now = h.getLevel().getGameTime();
        // 模拟链满边界：窗内还剩 2 条记录，但长锁未到期 → 必须拒绝
        WingsState.setBlinkTimes(p, new long[]{now - 4, now - 2});
        WingsState.setBlinkLockUntil(p, now + 100);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkTimes(p).length == 2,
                "active 160t lock must reject blink despite in-window records");
        // 锁到期后放行（此时第 3 条记录会再次触发链满 → 新的长锁）
        WingsState.setBlinkLockUntil(p, now);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkTimes(p).length == 3,
                "expired lock must allow blink again");
        h.assertTrue(WingsState.blinkLockUntil(p) == now + 160,
                "3rd in-window blink sets the 160t lock");
        h.succeed();
    }

    /** 脱下已展开的翅膀：deployed/noGravity/fallFlying 必须全部回收。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void unequipStowsDeployedWings(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        FlightEvents.serverSetDeployed(p, true);
        h.assertTrue(WingsState.deployed(p) && p.isNoGravity(),
                "precondition: deployed with noGravity");
        p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        tick(p); // 服务端巡檢发现 !wearing && deployed → 强制收起
        h.assertTrue(!WingsState.deployed(p),
                "unequipping deployed wings must stow them");
        h.assertTrue(!p.isNoGravity(),
                "unequip stow must restore gravity");
        h.succeed();
    }

    /** 服务端超速治理：位移超过档位上限 +50% 容差 → 标记并拉回。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void speedGovernorFlagsOverspeed(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3.5, 2.5)), 20);
        FlightEvents.serverSetDeployed(p, true); // HOVER：上限 5 m/s，容差后 12.5 m/s
        tick(p); // 登记上一 tick 位置
        p.setPos(p.getX() + 2.5, p.getY(), p.getZ()); // 50 m/s > 12.5，且 < 20bpt 传送上限
        tick(p);
        h.assertTrue(WingsState.speedFlagAt(p) > 0,
                "server must flag position-delta overspeed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void lightningRefillsHungerAndCharges(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 3);
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, h.getLevel());
        EntityStruckByLightningEvent event = new EntityStruckByLightningEvent(p, bolt);
        FlightEvents.onStruck(event);
        h.assertTrue(event.isCanceled(), "wearing wings must cancel lightning damage");
        h.assertTrue(p.getFoodData().getFoodLevel() == 20,
                "lightning strike refills hunger to full");
        h.assertTrue(WingsState.charged(p), "strike grants 雷力充盈");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void phantomKilledByLightningDropsFeather(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Phantom phantom = new Phantom(EntityType.PHANTOM, level);
        phantom.setPos(h.absoluteVec(new Vec3(2.5, 3, 2.5)));
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(phantom.position());
        FlightEvents.onPhantomDeath(new LivingDeathEvent(phantom,
                new net.minecraft.world.damagesource.DamageSource(
                        level.registryAccess().registryOrThrow(
                                net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                                .getHolderOrThrow(DamageTypes.LIGHTNING_BOLT),
                        bolt, bolt, phantom.position())));
        long feathers = level.getEntitiesOfClass(ItemEntity.class,
                        phantom.getBoundingBox().inflate(3)).stream()
                .filter(e -> e.getItem().is(ModItems.THUNDER_FEATHER.get()))
                .count();
        h.assertTrue(feathers == 1, "lightning-killed phantom drops 雷鹏骨羽");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wingsCancelFallDamage(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        LivingFallEvent fall = new LivingFallEvent(p, 10f, 1.0f);
        FlightEvents.onFall(fall);
        h.assertTrue(fall.isCanceled(), "wearing wings cancels fall damage");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wallDamageAppliedServerSide(GameTestHelper h) {
        // 大墙面：fallFlying 姿态眼高 ~0.4，cover y 1–6 保稳命中
        for (int y = 1; y <= 6; y++) {
            for (int z = 1; z <= 4; z++) {
                h.setBlock(new BlockPos(5, y, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(1.5, 3.5, 2.5)), 20);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        FlightEvents.serverSetDeployed(p, true);
        h.assertTrue(WingsState.deployed(p), "wings must be deployed");
        // STORM 档限速 120 m/s（容差 185），50 m/s 位移不会被超速治理拦截，
        // 只走撞墙伤害路径（阈值 40 m/s）
        WingsState.setTier(p, WingsState.STORM);
        tick(p); // 登记上一 tick 位置
        // 结构可能被旋转放置：墙面在结构局部 +X，换算成世界方向再位移
        Vec3 relX = h.absoluteVec(new Vec3(1, 0, 0)).subtract(h.absoluteVec(Vec3.ZERO));
        p.setPos(p.getX() + relX.x * 2.5, p.getY(), p.getZ() + relX.z * 2.5);
        tick(p);
        // FakePlayer 无敌（canHurt=false 实测），只能断言服务端探测+触发；
        // hurt 本身是原版调用，真实客户端 E2E 覆盖端到端掉血。
        h.assertTrue(WingsState.wallHitAt(p) > 0,
                "server must detect wall hit at threshold speed"
                        + " speedFlag=" + WingsState.speedFlagAt(p)
                        + " pos=" + p.position());
        h.succeed();
    }
}
