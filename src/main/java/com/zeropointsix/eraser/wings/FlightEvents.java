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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
    /** 超速告警节流（仅提示用，不落盘）。 */
    private static final Map<UUID, Long> lastSpeedWarnAt = new WeakHashMap<>();

    // ------------------------------------------------------------ actions
    public static void serverSetDeployed(ServerPlayer p, boolean want) {
        if (want) {
            // 展开才校验穿戴与饥饿；收起路径必须无条件清状态（脱装备时也要回收
            // deployed/noGravity/fallFlying，否则残留泄漏）
            if (!WingsState.wearing(p)) return;
            if (p.getFoodData().getFoodLevel() < WingsConfig.SERVER.minFoodToDeploy.get()) {
                p.displayClientMessage(Component.translatable("message.sound_isolating_eraser.hungry"), true);
                return;
            }
        }
        WingsState.setDeployed(p, want);
        if (want) {
            p.setNoGravity(true);
            // 部署瞬间可能带着落体惯性——把超速治理基准锚在部署点，
            // 否则下一 tick 的合法速度余量会被误判拉回
            markTeleported(p);
            // 标记为鞘翅式飞行：原版服务器 floating 踢人与客户端动画都按 fall-flying 处理
            p.startFallFlying();
            WingsState.setHoverAnchor(p, p.getY());
            p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
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
        WingsState.setTier(p, next);
        WingsNet.syncToTracking(p);
    }

    public static void serverToggleHover(ServerPlayer p) {
        if (!WingsState.deployed(p)) return;
        if (WingsState.tier(p) == WingsState.HOVER) {
            WingsState.setTier(p, WingsState.prevTier(p));
        } else {
            WingsState.setTier(p, WingsState.HOVER);
        }
        WingsState.setHoverAnchor(p, p.getY());
        WingsNet.syncToTracking(p);
    }

    public static void serverBlink(ServerPlayer p) {
        if (!WingsState.deployed(p)) return;
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
        if (times.length == 0) {
            // 新链第一闪：走普通冷却，成功后锚定链窗
            if (now < WingsState.blinkCooldownUntil(p)) return;
            WingsState.setBlinkTimes(p, new long[]{now});
        } else if (times.length >= cfg.blinkChainMax.get() - 1) {
            // 链内第 N 闪补满：本次放行，随后进入长锁（窗口随之作废）
            WingsState.setBlinkLockUntil(p, now + cfg.blinkChainCooldownTicks.get());
            WingsState.setBlinkTimes(p, new long[0]);
        } else {
            long[] merged = Arrays.copyOf(times, times.length + 1);
            merged[times.length] = now;
            WingsState.setBlinkTimes(p, merged);
        }
        // 每次成功雷遁都刷新普通冷却：链内连闪不读它，但窗口结束后下一闪要等它
        WingsState.setBlinkCooldownUntil(p, now + cfg.blinkCooldownTicks.get());

        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        double dist = cfg.blinkDistance.get();
        Vec3 end = eye.add(dir.scale(dist));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 target = hit.getType() == HitResult.Type.MISS
                ? end
                : hit.getLocation().subtract(dir.scale(1.0));
        if (!level.getBlockState(BlockPos.containing(target)).isAir()) {
            target = hit.getLocation().subtract(dir.scale(1.0));
        }

        // departure FX: silver arc + ring
        spawnBlinkFx(level, eye, target);
        Vec3 from = p.position();
        p.teleportToWithTicket(target.x, target.y, target.z);
        // 雷瞬是服务端位移：标记授权传送，避免被超速治理/撞墙探测误判
        markTeleported(p);
        p.resetFallDistance();
        p.setDeltaMovement(dir.scale(0.6));

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
        ServerPlayer p = (ServerPlayer) event.player;
        WingsConfig.Server cfg = WingsConfig.SERVER;

        if (!WingsState.wearing(p)) {
            if (WingsState.deployed(p)) {
                serverSetDeployed(p, false);
            }
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
                    WingsState.setTier(p, WingsState.HOVER);
                    WingsNet.syncToTracking(p);
                }
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false));
            } else if (food < cfg.minFoodForFastTiers.get()
                    && WingsState.tier(p) >= WingsState.BOOST) {
                WingsState.setTier(p, WingsState.CRUISE);
                p.displayClientMessage(Component.translatable("message.sound_isolating_eraser.hungry"), true);
                WingsNet.syncToTracking(p);
            }
            // 服务端速度/位移校验（Notion 要求服务端权威）+ 撞墙伤害。
            // 客户端权威飞行下 horizontalCollision 不可信，统一用每 tick 位置增量。
            // 大位移不再有豁免档：合法传送/重生/跨维度在事件里显式 markTeleported 重置基准。
            {
                Vec3 prev = lastPositions.put(p.getUUID(), p.position());
                if (prev != null) {
                    ServerLevel level = p.serverLevel();
                    long now = level.getGameTime();
                    double dx = p.getX() - prev.x, dy = p.getY() - prev.y, dz = p.getZ() - prev.z;
                    double speedBpt3d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double speedBpt = Math.hypot(dx, dz);
                    // 超速治理：超出档位上限 +50% 余量的位移拉回上一认可位置
                    double allowedMs = cfg.tierSpeed(WingsState.tier(p)) * 1.5 + 5.0;
                    if (speedBpt3d * 20.0 > allowedMs) {
                        WingsState.setSpeedFlagAt(p, now);
                        p.teleportToWithTicket(prev.x, prev.y, prev.z);
                        // FakePlayer 的 connection.teleport 是空实现：补一次直接落位，
                        // 保证服务端视图坐标也回到上一认可位置（真实玩家已由包处理落位）
                        p.setPos(prev.x, prev.y, prev.z);
                        p.setDeltaMovement(Vec3.ZERO);
                        lastPositions.put(p.getUUID(), p.position());
                        Long lastWarn = lastSpeedWarnAt.get(p.getUUID());
                        if (lastWarn == null || now - lastWarn > 100) {
                            lastSpeedWarnAt.put(p.getUUID(), now);
                            p.displayClientMessage(Component.translatable(
                                    "message.sound_isolating_eraser.speed_cap"), true);
                        }
                    } else if (cfg.wallDamageEnabled.get()
                            && speedBpt * 20.0 > cfg.wallDamageThresholdSpeed.get()
                            && (WingsState.wallHitAt(p) == 0
                                    || now >= WingsState.wallHitAt(p) + 20)) {
                        // 扫掠碰撞：沿本 tick 实际水平位移向前扫（含 0.35 贴脸余量），
                        // 且命中面法线必须迎向运动方向——靠近墙面刹停/贴墙滑行都不算撞墙
                        Vec3 dir = new Vec3(dx / speedBpt, 0, dz / speedBpt);
                        Vec3 eyeFrom = prev.add(0, p.getEyeHeight(), 0);
                        BlockHitResult hit = level.clip(new ClipContext(eyeFrom,
                                eyeFrom.add(dir.scale(speedBpt + 0.35)),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                        if (hit.getType() != HitResult.Type.MISS
                                && dir.dot(Vec3.atLowerCornerOf(
                                        hit.getDirection().getNormal())) < -0.3) {
                            WingsState.setWallHitAt(p, now);
                            double speedMs = speedBpt * 20.0;
                            p.hurt(level.damageSources().flyIntoWall(),
                                    (float) Math.min(cfg.wallDamageCap.get(),
                                            (speedMs - cfg.wallDamageThresholdSpeed.get()) / 10.0));
                        }
                    }
                }
            }
            // periodic state sync for late trackers
            if (p.tickCount % 40 == 0) {
                WingsNet.syncToTracking(p);
            }
        } else if (p.tickCount % 40 == 0) {
            // 未部署也要周期性同步：客户端手势判定依赖 WingInfo（首次展开前没有包会发）
            WingsNet.syncToTracking(p);
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
        if (!(event.getSource().getDirectEntity() instanceof LightningBolt)) return;
        if (phantom.level().isClientSide) return;
        ItemStack drop = new ItemStack(ModItems.THUNDER_FEATHER.get());
        phantom.level().addFreshEntity(new ItemEntity(phantom.level(),
                phantom.getX(), phantom.getY(), phantom.getZ(), drop));
    }

    /** 服务端发起的位移（雷瞬/拉回等）调用：重置速度基准，下一 tick 不判超速。 */
    public static void markTeleported(Player p) {
        lastPositions.put(p.getUUID(), p.position());
    }

    /** 原版/指令传送（/tp、末影珍珠、紫颂果等）同样属于授权位移。 */
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
    }

    /** 登录即发初始 WingInfo，否则客户端连一次部署手势都无法判定。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        lastPositions.remove(event.getEntity().getUUID());
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
