package com.zeropointsix.eraser.pill;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.config.CommonConfig;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.registries.ForgeRegistries;

public final class PillEffects {
    public static final int REFRESH_INTERVAL = 40;
    public static final int PRESENTATION_DURATION = 60;
    public static final TagKey<MobEffect> SUPPRESSED = TagKey.create(Registries.MOB_EFFECT,
            new ResourceLocation(ModMain.MOD_ID, "pill_suppressed"));

    private record Dose(MobEffect effect, int amplifier) { }

    static boolean manages(MobEffect effect) {
        return effect != null && (effect == MobEffects.DAMAGE_BOOST || effect == MobEffects.MOVEMENT_SPEED
                || effect == MobEffects.DIG_SPEED || effect == MobEffects.DAMAGE_RESISTANCE
                || effect == MobEffects.MOVEMENT_SLOWDOWN || effect == MobEffects.DIG_SLOWDOWN
                || effect == MobEffects.WEAKNESS || effect == MobEffects.DARKNESS || effect == MobEffects.CONFUSION);
    }

    public static boolean suppressed(MobEffect effect) {
        return ForgeRegistries.MOB_EFFECTS.tags().getTag(SUPPRESSED).contains(effect);
    }

    private static List<Dose> boosts() {
        return List.of(new Dose(MobEffects.DAMAGE_BOOST, CommonConfig.value(CommonConfig.STRENGTH, 1)),
                new Dose(MobEffects.MOVEMENT_SPEED, CommonConfig.value(CommonConfig.SPEED, 1)),
                new Dose(MobEffects.DIG_SPEED, CommonConfig.value(CommonConfig.HASTE, 1)),
                new Dose(MobEffects.DAMAGE_RESISTANCE, CommonConfig.value(CommonConfig.RESISTANCE, 0)));
    }

    private static List<Dose> withdrawals() {
        return List.of(new Dose(MobEffects.MOVEMENT_SLOWDOWN, CommonConfig.value(CommonConfig.WITHDRAWAL_SLOWNESS, 1)),
                new Dose(MobEffects.DIG_SLOWDOWN, CommonConfig.value(CommonConfig.WITHDRAWAL_FATIGUE, 1)),
                new Dose(MobEffects.WEAKNESS, CommonConfig.value(CommonConfig.WITHDRAWAL_WEAKNESS, 1)),
                new Dose(MobEffects.DARKNESS, 0), new Dose(MobEffects.CONFUSION, 0));
    }

    private static void apply(ServerPlayer player, PillState state, Dose dose, int ticks) {
        state.capturePresentation(dose.effect(), player.getEffect(dose.effect()));
        state.changeEffects(() -> player.addEffect(new MobEffectInstance(dose.effect(),
                Math.min(PRESENTATION_DURATION, ticks), dose.amplifier(), false, false, true)));
    }

    private static void clear(ServerPlayer player, PillState state, List<Dose> doses) {
        for (Dose dose : doses) {
            if (!state.hasPresentation(dose.effect())) continue;
            MobEffectInstance external = state.releasePresentation(dose.effect());
            state.changeEffects(() -> {
                if (player.removeEffect(dose.effect()) && external != null) player.addEffect(external);
            });
        }
    }

    public static void consume(ServerPlayer player, int damage, int uses) {
        player.getCapability(PillProvider.CAPABILITY).ifPresent(state -> {
            clear(player, state, withdrawals());
            state.consume(CommonConfig.value(CommonConfig.PILL_DURATION, 48000),
                    CommonConfig.value(CommonConfig.WITHDRAWAL_DURATION, 12000), damage, uses);
            player.getActiveEffects().stream().map(MobEffectInstance::getEffect).filter(PillEffects::suppressed)
                    .toList().forEach(player::removeEffect);
            refresh(player, state);
            PillNetwork.sync(player, state);
        });
    }

    public static void tick(ServerPlayer player, PillState state) {
        state.tickExternalEffects(player);
        PillState.Transition transition = state.tick();
        if (transition == PillState.Transition.WITHDRAWAL) clear(player, state, boosts());
        if (transition == PillState.Transition.FINISHED) clear(player, state, withdrawals());
        if (transition != PillState.Transition.NONE || player.tickCount % REFRESH_INTERVAL == 0) {
            refresh(player, state);
        }
        if (transition != PillState.Transition.NONE || player.tickCount % 20 == 0) PillNetwork.sync(player, state);
    }

    public static void refresh(ServerPlayer player, PillState state) {
        if (state.active()) {
            for (Dose dose : boosts()) apply(player, state, dose, state.remainingTicks());
        } else if (state.withdrawing()) {
            for (Dose dose : withdrawals()) {
                if (dose.effect() == MobEffects.DARKNESS && !CommonConfig.value(CommonConfig.WITHDRAWAL_DARKNESS, true)) continue;
                if (dose.effect() == MobEffects.CONFUSION && !CommonConfig.value(CommonConfig.WITHDRAWAL_NAUSEA, false)) continue;
                apply(player, state, dose, state.withdrawalTicks());
            }
        }
    }

    public static void milkFinished(ServerPlayer player) {
        if (!CommonConfig.value(CommonConfig.ALLOW_MILK_CURE, false)) return;
        player.getCapability(PillProvider.CAPABILITY).ifPresent(state -> {
            if (state.withdrawing()) {
                state.cureWithdrawal();
                clear(player, state, withdrawals());
                PillNetwork.sync(player, state);
            }
        });
    }

    private PillEffects() { }
}
