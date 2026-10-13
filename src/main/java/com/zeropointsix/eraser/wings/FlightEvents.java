package com.zeropointsix.eraser.wings;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.registry.ModParticles;
import com.zeropointsix.eraser.wings.net.WingsNet;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side authority: state changes, hunger drain, safety, blink, drops. */
@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class FlightEvents {
    private FlightEvents() {}

    /** 上一 tick 位置（服务端速度校验/撞墙探测用；玩家对象弱引用，离线自动回收）。 */
    private static final Map<UUID, Vec3> lastPositions = new WeakHashMap<>();
    private static final Map<UUID, FlightSpeedBudget> speedBudgets = new WeakHashMap<>();
    private static final Map<UUID, Long> lastMovementTicks = new WeakHashMap<>();
    private static final Map<UUID, FlightSpeedTransition> speedTransitions = new WeakHashMap<>();
    /** 超速告警节流（仅提示用，不落盘）。 */
    private static final Map<UUID, Long> lastSpeedWarnAt = new WeakHashMap<>();

    // ------------------------------------------------------------ actions
    public static void serverSetDeployed(ServerPlayer p, boolean want) {
        boolean wasDeployed = WingsState.deployed(p);
        if (want) {
            // 展开才校验穿戴与饥饿；收起路径必须无条件清状态（脱装备时也要回收
            // deployed/noGravity/fallFlying，否则残留泄漏）
            if (!WingsState.wearing(p)) return;
            if (p.getFoodData().getFoodLevel() < WingsConfig.SERVER.minFoodToDeploy.get()) {
                p.displayClientMessage(Component.translatable("message.sound_isolating_eraser.hungry"), true);
                return;
            }
        }
        // Settle movement before changing flight state. Same-tick stow/deploy
        // requests must not erase unchecked movement or refill the budget.
        if (wasDeployed || (want && lastPositions.containsKey(p.getUUID()))) {
            settleMovement(p);
        }
        WingsState.setDeployed(p, want);
        if (want) {
            p.setNoGravity(true);
            // 部署瞬间可能带着落体惯性——把超速治理基准锚在部署点，
            // 否则下一 tick 的合法速度余量会被误判拉回
            lastPositions.putIfAbsent(p.getUUID(), p.position());
            lastMovementTicks.putIfAbsent(p.getUUID(), p.level().getGameTime());
            // 标记为鞘翅式飞行：原版服务器 floating 踢人与客户端动画都按 fall-flying 处理
            p.startFallFlying();
            WingsState.setHoverAnchor(p, p.getY());
            if (!wasDeployed) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        } else {
            p.stopFallFlying();
        }
        WingsNet.syncToTracking(p);
    }

    public static void serverCycleTier(ServerPlayer p) {
        if (!WingsState.deployed(p)) return;
        int next = (WingsState.tier(p) + 1) % WingsState.TIER_COUNT;
        int food = p.getFoodData().getFoodLevel();
        if (next >= WingsState.BOOST && food < WingsConfig.SERVER.minFoodForFastTiers.get()) {
            p.displayClientMessage(Component.translatable("message.sound_isolating_eraser.hungry"), true);
            next = WingsState.CRUISE;
        }
        changeTier(p, next);
        WingsNet.syncToTracking(p);
    }

    public static void serverToggleHover(ServerPlayer p) {
        if (!WingsState.deployed(p)) return;
        if (WingsState.tier(p) == WingsState.HOVER) {
            changeTier(p, WingsState.prevTier(p));
        } else {
            changeTier(p, WingsState.HOVER);
        }
        WingsState.setHoverAnchor(p, p.getY());
        WingsNet.syncToTracking(p);
    }

    private static void changeTier(ServerPlayer p, int next) {
        int previous = WingsState.tier(p);
        if (previous == next) return;
        settleMovement(p);
        WingsConfig.Server cfg = WingsConfig.SERVER;
        speedTransitions.computeIfAbsent(p.getUUID(), ignored -> new FlightSpeedTransition())
                .change(cfg.tierSpeed(previous), cfg.tierSpeed(next), cfg.accelLerp.get(),
                        p.level().getGameTime());
        WingsState.setTier(p, next);
    }

    public static void serverBlink(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || !WingsState.wearing(p) || !WingsState.deployed(p)) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        WingsConfig.Server cfg = WingsConfig.SERVER;

        // 链满长锁独立于窗口内剩余记录强制生效（此前可借窗口边缘残留记录绕过）
        if (now < WingsState.blinkLockUntil(p)) return;
        // 固定链窗：锚定链内首闪，而不是滚动截取最近 N tick——否则以小于窗口的间隔
        // 匀速续闪会让记录数永远凑不满上限，160t 长锁永远不会触发
        long[] times = WingsState.blinkTimes(p);
        if (times.length == 0 || now - times[0] >= cfg.blinkChainWindowTicks.get()) {
            times = new long[0]; // 链窗已结束（或无链），整条作废
        }
        if (times.length == 0 && now < WingsState.blinkCooldownUntil(p)) return;

        settleMovement(p);
        if (!p.isAlive()) return;
        var destination = BlinkDestination.find(p, cfg.blinkDistance.get());
        if (destination.isEmpty()) return;
        Vec3 target = destination.get();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 from = p.position();
        p.teleportToWithTicket(target.x, target.y, target.z);
        markTeleported(p);
        WingsState.setHoverAnchor(p, target.y);
        p.resetFallDistance();
        p.setDeltaMovement(dir.scale(0.6));

        // 只有安全落位后才结算连闪与消耗；上限为 1 时首闪即打满。
        if (times.length + 1 >= cfg.blinkChainMax.get()) {
            // 链内第 N 闪补满：本次放行，随后进入长锁（窗口随之作废）
            WingsState.setBlinkLockUntil(p, now + cfg.blinkChainCooldownTicks.get());
            WingsState.setBlinkCooldownUntil(p, now + cfg.blinkChainCooldownTicks.get());
            WingsState.setBlinkTimes(p, new long[0]);
        } else {
            long[] merged = Arrays.copyOf(times, times.length + 1);
            merged[times.length] = now;
            WingsState.setBlinkTimes(p, merged);
            WingsState.setBlinkCooldownUntil(p, now + cfg.blinkCooldownTicks.get());
        }

        // departure FX: silver arc + ring
        spawnBlinkFx(level, eye, target);

        level.playSound(null, from.x, from.y, from.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.3f);
        level.playSound(null, target.x, target.y, target.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.5f);

        if (!WingsState.charged(p)) {
            p.getFoodData().addExhaustion((float) cfg.blinkExhaust.get().doubleValue());
        }
        WingsNet.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.zeropointsix.eraser.wings.net.BlinkFlashPacket());
        if (cfg.blinkDamageEnabled.get()) {
            DamageSource src = p.damageSources().lightningBolt();
            for (Entity e : level.getEntities(p, AABB.ofSize(target, 4, 4, 4))) {
                e.hurt(src, (float) cfg.blinkDamage.get().doubleValue());
            }
        }
        WingsNet.syncToTracking(p);
    }

    private static void spawnBlinkFx(ServerLevel level, Vec3 from, Vec3 to) {
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = from.lerp(to, i / (double) steps);
            level.sendParticles(ModParticles.THUNDER_ARC.get(), pt.x, pt.y, pt.z,
                    2, 0.15, 0.15, 0.15, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ModParticles.TRAIL_DOT.get(), pt.x, pt.y, pt.z,
                        1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        level.sendParticles(ModParticles.IMPACT_RING.get(), from.x, from.y, from.z,
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ModParticles.IMPACT_RING.get(), to.x, to.y, to.z,
                1, 0.0, 0.0, 0.0, 0.0);
    }

    // ------------------------------------------------------------ ticking
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        // GameTest mock players are server-side Players but not ServerPlayers.
        if (!(event.player instanceof ServerPlayer p)) return;
        WingsConfig.Server cfg = WingsConfig.SERVER;

        if (!WingsState.wearing(p)) {
            if (WingsState.deployed(p)) {
                serverSetDeployed(p, false);
            }
            lastPositions.remove(p.getUUID());
            lastMovementTicks.remove(p.getUUID());
            speedTransitions.remove(p.getUUID());
            return;
        }

        if (WingsState.deployed(p)) {
            p.setNoGravity(true);
            if (p.onGround()) {
                serverSetDeployed(p, false);
                return;
            }
            // hunger drain per tick; free while 雷力充盈
            if (!WingsState.charged(p)) {
                double perTick = cfg.tierExhaustPerSec(WingsState.tier(p)) / 20.0;
                p.getFoodData().addExhaustion((float) perTick);
            }
            int food = p.getFoodData().getFoodLevel();
            if (food <= 0) {
                // starving: auto-drop to hover + slow fall
                if (WingsState.tier(p) != WingsState.HOVER) {
                    changeTier(p, WingsState.HOVER);
                    WingsNet.syncToTracking(p);
                }
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false));
            } else if (food < cfg.minFoodForFastTiers.get()
                    && WingsState.tier(p) >= WingsState.BOOST) {
                changeTier(p, WingsState.CRUISE);
                p.displayClientMessage(Component.translatable("message.sound_isolating_eraser.hungry"), true);
                WingsNet.syncToTracking(p);
            }
            settleMovement(p);
            // periodic state sync for late trackers
            if (p.tickCount % 40 == 0) {
                WingsNet.syncToTracking(p);
            }
        } else {
            // Only an actual stowed tick allows a later deployment to re-anchor.
            lastPositions.remove(p.getUUID());
            lastMovementTicks.remove(p.getUUID());
            speedTransitions.remove(p.getUUID());
            if (p.tickCount % 40 == 0) {
                // 未部署也要周期性同步：客户端手势判定依赖 WingInfo（首次展开前没有包会发）
                WingsNet.syncToTracking(p);
            }
        }
    }

    /** Validate and settle each observed segment once, before changing its flight state. */
    private static void settleMovement(ServerPlayer p) {
        settleMovement(p, false);
    }

    /** Each accepted position packet represents one client movement step, even in a TCP batch. */
    public static void settleClientMovement(ServerPlayer p) {
        if (WingsState.deployed(p) && WingsState.wearing(p)) settleMovement(p, true);
    }

    private static void settleMovement(ServerPlayer p, boolean clientStep) {
        UUID id = p.getUUID();
        Vec3 prev = lastPositions.get(id);
        long now = p.serverLevel().getGameTime();
        if (prev == null) {
            lastPositions.put(id, p.position());
            lastMovementTicks.put(id, now);
            return;
        }
        WingsConfig.Server cfg = WingsConfig.SERVER;
        double speed = cfg.tierSpeed(WingsState.tier(p));
        FlightSpeedTransition transition = speedTransitions.get(id);
        if (transition != null) speed = transition.speed(speed, now);
        double allowedMs = speed * 1.5 + 5.0;
        FlightSpeedBudget budget = speedBudgets.computeIfAbsent(id, ignored -> new FlightSpeedBudget());
        double distance = p.position().distanceTo(prev);
        if (!budget.accept(distance, allowedMs / 20.0, now)) {
            WingsState.setSpeedFlagAt(p, now);
            p.teleportToWithTicket(prev.x, prev.y, prev.z);
            // FakePlayer's connection teleport does not update its server position.
            p.setPos(prev.x, prev.y, prev.z);
            p.setDeltaMovement(Vec3.ZERO);
            lastMovementTicks.put(id, now);
            Long lastWarn = lastSpeedWarnAt.get(id);
            if (lastWarn == null || now - lastWarn > 100) {
                lastSpeedWarnAt.put(id, now);
                p.displayClientMessage(Component.translatable(
                        "message.sound_isolating_eraser.speed_cap"), true);
            }
            return;
        }
        if (distance <= 1.0e-9) return;
        // Empty server ticks do not consume the sample time of delayed client movement.
        long elapsed = clientStep ? 1
                : Math.max(1, Math.min(2, now - lastMovementTicks.getOrDefault(id, now)));
        lastPositions.put(id, p.position());
        lastMovementTicks.put(id, now);
        settleWallImpact(p, prev, elapsed);
    }

    private static void settleWallImpact(ServerPlayer p, Vec3 prev, long elapsed) {
        WingsConfig.Server cfg = WingsConfig.SERVER;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        double dx = p.getX() - prev.x, dz = p.getZ() - prev.z;
        double distance = Math.hypot(dx, dz);
        double speedMs = distance * 20.0 / elapsed;
        if (!cfg.wallDamageEnabled.get() || speedMs <= cfg.wallDamageThresholdSpeed.get()
                || (WingsState.wallHitAt(p) != 0 && now < WingsState.wallHitAt(p) + 20)) return;
        Vec3 dir = new Vec3(dx / distance, 0, dz / distance);
        Vec3 eyeFrom = prev.add(0, p.getEyeHeight(), 0);
        BlockHitResult hit = level.clip(new ClipContext(eyeFrom,
                eyeFrom.add(dir.scale(distance + 0.35)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS
                && dir.dot(Vec3.atLowerCornerOf(hit.getDirection().getNormal())) < -0.3) {
            WingsState.setWallHitAt(p, now);
            p.hurt(level.damageSources().flyIntoWall(),
                    (float) Math.min(cfg.wallDamageCap.get(),
                            (speedMs - cfg.wallDamageThresholdSpeed.get()) / 10.0));
        }
    }

    // ------------------------------------------------------------ world events
    /** 装备时免疫摔落伤害（Notion §4.6）。 */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && WingsState.wearing(p)) {
            event.setDamageMultiplier(0f);
            event.setCanceled(true);
        }
    }

    /** 被雷劈中：回满饥饿并给 60s 雷力充盈（代替灵力充能）。 */
    @SubscribeEvent
    public static void onStruck(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof Player p) || !WingsState.wearing(p)) return;
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20f);
        WingsState.setChargedUntil(p, p.level().getGameTime()
                + WingsConfig.SERVER.chargedDurationTicks.get());
        event.setCanceled(true); // 神雷炼翅，不收雷劈伤害
        p.clearFire();
        p.displayClientMessage(Component.translatable("message.sound_isolating_eraser.charged"), true);
        if (p instanceof ServerPlayer sp) WingsNet.syncToTracking(sp);
    }

    /** 幻翼被雷劈死：掉落雷鹏骨羽（Notion：落雷劈中幻翼获得）。 */
    @SubscribeEvent
    public static void onPhantomDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom)) return;
        // Vanilla thunderHit uses a typed source without a direct entity.
        if (!event.getSource().is(DamageTypes.LIGHTNING_BOLT)) return;
        if (phantom.level().isClientSide) return;
        ItemStack drop = new ItemStack(ModItems.THUNDER_FEATHER.get());
        phantom.level().addFreshEntity(new ItemEntity(phantom.level(),
                phantom.getX(), phantom.getY(), phantom.getZ(), drop));
    }

    /** 服务端发起的位移（雷瞬/拉回等）调用：重置速度基准，下一 tick 不判超速。 */
    public static void markTeleported(Player p) {
        lastPositions.put(p.getUUID(), p.position());
        lastMovementTicks.put(p.getUUID(), p.level().getGameTime());
        speedBudgets.remove(p.getUUID());
        speedTransitions.remove(p.getUUID());
    }

    /** 原版/指令传送（/tp、末影珍珠、紫颂果等）同样属于授权位移。 */
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
        speedBudgets.remove(event.getEntity().getUUID());
        lastMovementTicks.remove(event.getEntity().getUUID());
        speedTransitions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
        speedBudgets.remove(event.getEntity().getUUID());
        lastMovementTicks.remove(event.getEntity().getUUID());
        speedTransitions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
        speedBudgets.remove(event.getEntity().getUUID());
        lastMovementTicks.remove(event.getEntity().getUUID());
        speedTransitions.remove(event.getEntity().getUUID());
    }

    /** 登录即发初始 WingInfo，否则客户端连一次部署手势都无法判定。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
        speedBudgets.remove(event.getEntity().getUUID());
        lastMovementTicks.remove(event.getEntity().getUUID());
        speedTransitions.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer sp) WingsNet.syncToTracking(sp);
    }

    /** 新观察者开始跟踪某玩家时补发其翼状态（他人翅膀渲染的同步入口）。 */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target
                && event.getEntity() instanceof ServerPlayer watcher) {
            WingsNet.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> watcher),
                    com.zeropointsix.eraser.wings.net.SyncWingsPacket.of(target));
        }
    }

    /** Respawn/leave safety: nothing leaks across deaths. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            WingsState.setDeployed(event.getEntity(), false);
        }
    }
}
