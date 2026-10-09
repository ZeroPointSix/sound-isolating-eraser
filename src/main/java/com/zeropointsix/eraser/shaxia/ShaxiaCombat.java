package com.zeropointsix.eraser.shaxia;

import com.mojang.logging.LogUtils;
import com.zeropointsix.eraser.ModMain;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class ShaxiaCombat {
    public static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(ModMain.MOD_ID, "shaxiadao_true"));
    private static final Map<UUID, Attack> ATTACKS = new HashMap<>();
    // Forge resolves this 1.20.1 SRG name in both the development and production runtimes.
    private static final Field LAST_HURT = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20898_");
    private static boolean missingDamageTypeReported;

    private static final class Attack {
        final Player player;
        final Entity original;
        final LivingEntity target;
        final ItemStack stack;
        final float beforeHealth, beforeAbsorption, scale;
        final long tick;
        LivingAttackEvent attack;
        LivingHurtEvent hurt;
        LivingDamageEvent damage;
        LivingUseTotemEvent totem;
        boolean added;

        Attack(Player player, Entity original, LivingEntity target, ItemStack stack) {
            this.player = player;
            this.original = original;
            this.target = target;
            this.stack = stack;
            beforeHealth = target.getHealth();
            beforeAbsorption = target.getAbsorptionAmount();
            scale = ShaxiaConfig.flag(ShaxiaConfig.SCALE_COOLDOWN, true) ? player.getAttackStrengthScale(0.5F) : 1;
            tick = player.level().getGameTime();
        }
    }

    public static void begin(Player player, Entity original, ItemStack stack) {
        if (player.level().isClientSide) return;
        ATTACKS.remove(player.getUUID());
        ShaxiaStacks.normalize(stack, ShaxiaConfig.flag(ShaxiaConfig.RESTORE_MISSING, true));
        Entity parent = original instanceof PartEntity<?> part ? part.getParent() : original;
        // A protection-window difference hit remains ordinary; it cannot start another special strike.
        if (!(parent instanceof LivingEntity target) || !player.isAlive() || player.getMainHandItem() != stack
                || !ShaxiaStacks.active(stack) || !ShaxiaTargets.eligible(target) || target.invulnerableTime > 10
                || (player instanceof FakePlayer && !ShaxiaConfig.flag(ShaxiaConfig.ALLOW_FAKE_PLAYERS, false))) return;
        ATTACKS.put(player.getUUID(), new Attack(player, original, target, stack));
    }

    private static Attack match(LivingEntity target, DamageSource source) {
        if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getDirectEntity() instanceof Player player)
                || source.getEntity() != player) return null;
        Attack hit = ATTACKS.get(player.getUUID());
        return hit != null && hit.target == target && hit.stack == player.getMainHandItem()
                && hit.tick == player.level().getGameTime() ? hit : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void attack(LivingAttackEvent event) {
        Attack hit = match(event.getEntity(), event.getSource());
        if (hit != null) hit.attack = event;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void hurt(LivingHurtEvent event) {
        Attack hit = match(event.getEntity(), event.getSource());
        if (hit == null) return;
        hit.hurt = event;
        if (!event.isCanceled() && !hit.added && event.getAmount() > 0) {
            event.setAmount(event.getAmount() + (float) ShaxiaConfig.number(ShaxiaConfig.NORMAL_BONUS, 2.5) * hit.scale);
            hit.added = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void damage(LivingDamageEvent event) {
        Attack hit = match(event.getEntity(), event.getSource());
        if (hit != null) hit.damage = event;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void totem(LivingUseTotemEvent event) {
        Attack hit = match(event.getEntity(), event.getSource());
        if (hit != null) hit.totem = event;
    }

    public static void finish(Player player, LivingEntity target, ItemStack stack) {
        if (player.level().isClientSide) return;
        Attack hit = ATTACKS.remove(player.getUUID());
        if (hit == null || hit.target != target || hit.stack != stack || !target.isAlive()
                || hit.tick != player.level().getGameTime() || !ShaxiaStacks.active(stack)
                || hit.attack == null || hit.attack.isCanceled() || hit.hurt == null || hit.hurt.isCanceled()
                || (hit.damage != null && hit.damage.isCanceled()) || (hit.totem != null && !hit.totem.isCanceled())
                || !(target.getHealth() < hit.beforeHealth || target.getAbsorptionAmount() < hit.beforeAbsorption)) return;
        float extra = (float) ShaxiaConfig.number(ShaxiaConfig.TRUE_DAMAGE, 2) * hit.scale;
        if (extra <= 0) return;
        var type = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(TRUE_DAMAGE);
        if (type.isEmpty()) {
            if (!missingDamageTypeReported) {
                LogUtils.getLogger().error("Missing shaxiadao_true damage type; knife armor-bypassing damage is disabled");
                missingDamageTypeReported = true;
            }
            return;
        }
        int cooldown = target.invulnerableTime, hurtTime = target.hurtTime, duration = target.hurtDuration;
        float lastHurt;
        try {
            lastHurt = LAST_HURT.getFloat(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot preserve knife damage accounting", e);
        }
        try {
            hit.original.hurt(new DamageSource(type.get(), player, player), extra);
        } finally {
            // Keep the first hit's protection window; never restore health, absorption, drops or totems.
            target.invulnerableTime = cooldown;
            target.hurtTime = hurtTime;
            target.hurtDuration = duration;
            try {
                LAST_HURT.setFloat(target, lastHurt);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot restore knife damage accounting", e);
            }
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ATTACKS.clear();
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ATTACKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        ATTACKS.clear();
        missingDamageTypeReported = false;
    }

    private ShaxiaCombat() {}
}
