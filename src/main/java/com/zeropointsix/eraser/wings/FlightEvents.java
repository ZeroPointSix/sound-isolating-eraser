package com.zeropointsix.eraser.wings;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.registry.ModParticles;
import com.zeropointsix.eraser.wings.net.WingsNet;
import java.util.Arrays;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side authority: state changes, hunger drain, safety, blink, drops. */
@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class FlightEvents {
    private FlightEvents() {}

    // ------------------------------------------------------------ actions
    public static void serverSetDeployed(ServerPlayer p, boolean want) {
        if (!WingsState.wearing(p)) return;
        if (want && p.getFoodData().getFoodLevel() < WingsConfig.SERVER.minFoodToDeploy.get()) {
            p.displayClientMessage(Component.translatable("message.sound_isolating_eraser.hungry"), true);
            return;
        }
        WingsState.setDeployed(p, want);
        if (want) {
            p.setNoGravity(true);
            WingsState.setHoverAnchor(p, p.getY());
            p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
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
        if (now < WingsState.blinkCooldownUntil(p)) return;

        long[] times = Arrays.stream(WingsState.blinkTimes(p))
                .filter(t -> now - t < cfg.blinkChainWindowTicks.get()).toArray();
        boolean chainFull = times.length >= cfg.blinkChainMax.get() - 1;
        long cd = chainFull ? cfg.blinkChainCooldownTicks.get() : cfg.blinkCooldownTicks.get();
        WingsState.setBlinkCooldownUntil(p, now + cd);
        long[] merged = Arrays.copyOf(times, times.length + 1);
        merged[times.length] = now;
        WingsState.setBlinkTimes(p, merged);

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
                WingsState.setDeployed(p, false);
                WingsNet.syncToTracking(p);
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
            // periodic state sync for late trackers
            if (p.tickCount % 40 == 0) {
                WingsNet.syncToTracking(p);
            }
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

    /** Respawn/leave safety: nothing leaks across deaths. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            WingsState.setDeployed(event.getEntity(), false);
        }
    }
}
