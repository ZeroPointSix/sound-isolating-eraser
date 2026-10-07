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
                "first blink anchors the fixed chain window");
        h.assertTrue(WingsState.blinkCooldownUntil(p) == now + 40,
                "single blink → 40t cooldown");
        // 链闪：固定窗口内第 2、3 下放行，第 3 下补满 → 160t 长锁且链窗作废
        FlightEvents.serverBlink(p);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkLockUntil(p) == now + 160,
                "3rd chained blink → 160t long lock");
        h.assertTrue(WingsState.blinkTimes(p).length == 0,
                "full chain consumes the window");
        FlightEvents.serverBlink(p); // 4th must be rejected by the long lock
        h.assertTrue(WingsState.blinkTimes(p).length == 0,
                "4th blink inside window must be rejected");
        // 位移本身依赖 teleportToWithTicket 的区块 ticket，FakePlayer 不生效；
        // 真实位移由客户端 E2E 覆盖，此处只验收状态机
        h.succeed();
    }

    /** R2-01：匀速间隔小于窗口的连闪不得无限续链——链窗锚定首闪，过期后按普通冷却拦截。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blinkSpacedPressesCannotExtendChain(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        p.setYRot(0);
        FlightEvents.serverSetDeployed(p, true);
        long now = h.getLevel().getGameTime();
        // 模拟 0/11 间隔连闪：链锚在 now-22，窗口(12t)已过；冷却 now-22+40=now+18 未到期
        WingsState.setBlinkTimes(p, new long[]{now - 22, now - 11});
        WingsState.setBlinkCooldownUntil(p, now - 22 + 40);
        FlightEvents.serverBlink(p); // 必须被拒绝：链窗已过期 + 普通冷却未到期
        h.assertTrue(WingsState.blinkTimes(p).length == 2,
                "expired chain window + live cooldown must reject blink");
        // 冷却到期后放行，并重锚新链窗
        WingsState.setBlinkCooldownUntil(p, now);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkTimes(p).length == 1
                && WingsState.blinkTimes(p)[0] == now,
                "expired cooldown must allow blink and anchor a new chain");
        // 冷却到期前一 tick 仍拒绝、到期 tick 放行
        WingsState.setBlinkTimes(p, new long[0]);
        WingsState.setBlinkCooldownUntil(p, now + 40);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkTimes(p).length == 0,
                "blink one tick before cooldown end must be rejected");
        WingsState.setBlinkCooldownUntil(p, now);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkTimes(p).length == 1,
                "blink at cooldown-expiry tick must be allowed");
        h.succeed();
    }

    /** R2-01：链内记录只有在锚点窗口内才连闪；锚过期则首闪时刻不重置窗口。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blinkChainWindowAnchoredAtFirstBlink(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3, 2.5)), 20);
        p.setYRot(0);
        FlightEvents.serverSetDeployed(p, true);
        long now = h.getLevel().getGameTime();
        // 链内已有 0/6 两条，窗口剩 1 tick：第 3 下在锚点窗口内 → 补满上锁
        WingsState.setBlinkTimes(p, new long[]{now - 11, now - 5});
        WingsState.setBlinkCooldownUntil(p, now - 11 + 40); // 普通冷却未到期但链内不受其拦截
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkLockUntil(p) == now + 160,
                "3rd blink inside anchored window fills the chain → 160t lock");
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
        // 锁到期后放行（链窗内记录补满 → 本次成功并重新上锁）
        WingsState.setBlinkLockUntil(p, now);
        FlightEvents.serverBlink(p);
        h.assertTrue(WingsState.blinkLockUntil(p) == now + 160,
                "in-window blink filling the chain re-applies the 160t lock");
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
        p.setPos(p.getX() + 2.5, p.getY(), p.getZ()); // 50 m/s > 12.5
        tick(p);
        h.assertTrue(WingsState.speedFlagAt(p) > 0,
                "server must flag position-delta overspeed");
        h.succeed();
    }

    /** R2-02：>20 格/t 不再有“传送豁免”——未授权的大位移同样拉回并打标。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void speedGovernorPullsBackHugeDelta(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3.5, 2.5)), 20);
        FlightEvents.serverSetDeployed(p, true);
        WingsState.setTier(p, WingsState.STORM);
        Vec3 from = p.position();
        tick(p); // 登记基准
        p.setPos(from.x + 30, from.y, from.z); // 600 m/s：旧实现直接豁免
        tick(p);
        h.assertTrue(WingsState.speedFlagAt(p) > 0,
                "server must flag a 30-block/tick unauthorized jump");
        h.assertTrue(Math.abs(p.getX() - from.x) < 0.01,
                "server must pull the player back to the last accepted position");
        h.succeed();
    }

    /** R2-02 对照：服务端发起的位移（传送/重生/跨维度）重置基准，不误伤。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void authorizedTeleportNotFlagged(GameTestHelper h) {
        FakePlayer p = winged(h, h.absoluteVec(new Vec3(2.5, 3.5, 2.5)), 20);
        FlightEvents.serverSetDeployed(p, true);
        tick(p); // 登记基准
        p.setPos(p.getX() + 24, p.getY(), p.getZ()); // 合法雷瞬量级
        FlightEvents.markTeleported(p);            // 服务端位移 → 基准重置
        tick(p);
        h.assertTrue(WingsState.speedFlagAt(p) == 0,
                "server-authorized teleport must not be flagged");
        h.assertTrue(Math.abs(p.getX() - (h.absoluteVec(new Vec3(2.5, 3.5, 2.5)).x + 24)) < 0.01,
                "authorized teleport keeps the new position");
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

    /** 在结构局部 (5,1..6,1..4) 立一面石墙，返回墙在世界中的单位法线反方向（即 +局部X 的世界向量）。 */
    private static Vec3 buildWall(GameTestHelper h) {
        for (int y = 1; y <= 6; y++) {
            for (int z = 1; z <= 4; z++) {
                h.setBlock(new BlockPos(5, y, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        return h.absoluteVec(new Vec3(1, 0, 0)).subtract(h.absoluteVec(Vec3.ZERO)).normalize();
    }

    /** R2-07：扫掠碰撞——只有本 tick 位移真的穿过/贴上墙面才算撞墙。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wallDamageAppliedServerSide(GameTestHelper h) {
        Vec3 relX = buildWall(h); // 墙面世界方向
        Vec3 wallWorld = h.absoluteVec(new Vec3(5, 3.5, 2.5)); // 墙面一点
        FakePlayer p = winged(h,
                wallWorld.subtract(relX.scale(3.5)).add(0, 0.0, 0), 20);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        FlightEvents.serverSetDeployed(p, true);
        WingsState.setTier(p, WingsState.STORM); // 位移 60 m/s < 185 容差，不被治理拦截
        Vec3 from = p.position();
        tick(p);
        // 3.19 格位移后贴脸到墙面（0.35 余量内）→ 判定撞上
        p.setPos(p.getX() + relX.x * 3.19, p.getY(), p.getZ() + relX.z * 3.19);
        tick(p);
        h.assertTrue(WingsState.wallHitAt(p) > 0,
                "swept segment reaching the wall face must count as impact"
                        + " from=" + from + " pos=" + p.position());
        h.succeed();
    }

    /** R2-07 对照：高速冲向墙面但在碰撞距离外刹停 → 不掉血（不标记）。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wallDamageNearMissDoesNotHurt(GameTestHelper h) {
        Vec3 relX = buildWall(h);
        Vec3 wallWorld = h.absoluteVec(new Vec3(5, 3.5, 2.5));
        FakePlayer p = winged(h, wallWorld.subtract(relX.scale(3.5)), 20);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        FlightEvents.serverSetDeployed(p, true);
        WingsState.setTier(p, WingsState.STORM);
        tick(p);
        // 2.4 格位移（48 m/s 超速阈值）后离墙面仍 ~1.1 格 → 扫掠不命中 → 不伤
        p.setPos(p.getX() + relX.x * 2.4, p.getY(), p.getZ() + relX.z * 2.4);
        tick(p);
        h.assertTrue(WingsState.wallHitAt(p) == 0,
                "stopping short of the wall must not deal damage"
                        + " pos=" + p.position());
        h.succeed();
    }
}
