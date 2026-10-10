package com.zeropointsix.eraser.pill;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.registries.ForgeRegistries;

/** Authoritative online-tick clock; potion durations never determine the phase. */
@AutoRegisterCapability
public final class PillState {
    public enum Transition { NONE, WITHDRAWAL, FINISHED }
    private int remainingTicks;
    private int withdrawalTicks;
    private int activeDuration;
    private int withdrawalDuration;
    private int packDamage;
    private int packUses = 12;
    // A null value means the effect has only a pill contribution.
    private final Map<MobEffect, MobEffectInstance> presentations = new HashMap<>();
    private int changingEffects;

    public boolean active() { return remainingTicks > 0; }
    public boolean withdrawing() { return withdrawalTicks > 0; }
    public int remainingTicks() { return remainingTicks; }
    public int withdrawalTicks() { return withdrawalTicks; }
    public int activeDuration() { return activeDuration; }
    public int withdrawalDuration() { return withdrawalDuration; }
    public int packDamage() { return packDamage; }
    public int packUses() { return packUses; }

    public void consume(int duration, int withdrawal, int damage, int uses) {
        activeDuration = Math.max(1, duration);
        withdrawalDuration = Math.max(1, withdrawal);
        remainingTicks = activeDuration;
        withdrawalTicks = 0;
        packUses = Math.max(1, uses);
        packDamage = Math.max(0, Math.min(damage, packUses));
    }

    public Transition tick() {
        if (active()) {
            if (--remainingTicks == 0) {
                withdrawalTicks = withdrawalDuration;
                return Transition.WITHDRAWAL;
            }
        } else if (withdrawing() && --withdrawalTicks == 0) {
            return Transition.FINISHED;
        }
        return Transition.NONE;
    }

    public void cureWithdrawal() { withdrawalTicks = 0; }

    void capturePresentation(MobEffect effect, MobEffectInstance original) {
        if (!presentations.containsKey(effect)) presentations.put(effect, copyEffect(original));
    }

    boolean hasPresentation(MobEffect effect) { return presentations.containsKey(effect); }

    MobEffectInstance releasePresentation(MobEffect effect) { return presentations.remove(effect); }

    void changeEffects(Runnable action) {
        changingEffects++;
        try { action.run(); }
        finally { changingEffects--; }
    }

    void externalEffectAdded(MobEffectInstance effect) {
        if (changingEffects != 0 || !presentations.containsKey(effect.getEffect())) return;
        MobEffectInstance external = presentations.get(effect.getEffect());
        if (external == null) presentations.put(effect.getEffect(), copyEffect(effect));
        else external.update(copyEffect(effect));
    }

    void externalEffectRemoved(MobEffect effect) {
        if (changingEffects == 0) presentations.remove(effect);
    }

    void tickExternalEffects(ServerPlayer player) {
        // These tracked vanilla buffs/debuffs have no periodic gameplay action.
        // Vanilla ticking preserves hidden-effect expiry and promotion correctly.
        presentations.replaceAll((effect, external) ->
                external != null && external.tick(player, () -> { }) ? external : null);
    }

    public void discardPresentations() { presentations.clear(); }

    private static MobEffectInstance copyEffect(MobEffectInstance effect) {
        return effect == null ? null : MobEffectInstance.load(effect.save(new CompoundTag()));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("remainingTicks", remainingTicks);
        tag.putInt("withdrawalTicks", withdrawalTicks);
        tag.putInt("activeDuration", activeDuration);
        tag.putInt("withdrawalDuration", withdrawalDuration);
        tag.putInt("packDamage", packDamage);
        tag.putInt("packUses", packUses);
        ListTag effects = new ListTag();
        presentations.forEach((effect, external) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("effect", ForgeRegistries.MOB_EFFECTS.getKey(effect).toString());
            if (external != null) entry.put("external", external.save(new CompoundTag()));
            effects.add(entry);
        });
        tag.put("presentations", effects);
        return tag;
    }

    public void load(CompoundTag tag) {
        remainingTicks = Math.max(0, tag.getInt("remainingTicks"));
        withdrawalTicks = remainingTicks > 0 ? 0 : Math.max(0, tag.getInt("withdrawalTicks"));
        activeDuration = Math.max(remainingTicks, tag.getInt("activeDuration"));
        withdrawalDuration = Math.max(1, Math.max(withdrawalTicks, tag.getInt("withdrawalDuration")));
        packUses = Math.max(1, tag.getInt("packUses"));
        packDamage = Math.max(0, Math.min(packUses, tag.getInt("packDamage")));
        presentations.clear();
        ListTag effects = tag.getList("presentations", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            CompoundTag entry = effects.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("effect"));
            MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (!PillEffects.manages(effect)) continue;
            MobEffectInstance external = entry.contains("external", Tag.TAG_COMPOUND)
                    ? MobEffectInstance.load(entry.getCompound("external")) : null;
            presentations.put(effect, external != null && external.getEffect() == effect ? external : null);
        }
    }
}
