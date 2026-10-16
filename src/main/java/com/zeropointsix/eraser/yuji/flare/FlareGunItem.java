package com.zeropointsix.eraser.yuji.flare;

import com.zeropointsix.eraser.yuji.city.RainCityContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class FlareGunItem extends Item implements GeoItem {
    private static final String VISUAL_AMMO = "FlareVisualAmmo";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.flare_gun.idle");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("animation.flare_gun.fire");
    private static final RawAnimation LOAD = RawAnimation.begin().thenPlay("animation.flare_gun.load");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public FlareGunItem() {
        super(new Properties().stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "action", 0, state -> state.setAndContinue(IDLE))
                .triggerableAnim("fire", FIRE).triggerableAnim("load", LOAD));
    }

    @Override public void initializeClient(java.util.function.Consumer<
            net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new com.zeropointsix.eraser.yuji.flare.client.FlareGunClientExtensions());
    }

    @Override public boolean onEntitySwing(ItemStack stack, LivingEntity entity) { return true; }

    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player)
                || !selected || player.getMainHandItem() != stack || player.getCooldowns().isOnCooldown(this)) return;
        int color = FlareContent.color(ammunition(player));
        int previous = stack.hasTag() && stack.getTag().contains(VISUAL_AMMO) ? stack.getTag().getInt(VISUAL_AMMO) : -1;
        if (color != previous) {
            stack.getOrCreateTag().putInt(VISUAL_AMMO, color);
            // Existing ammo selection is unchanged; animate only a newly available cartridge/color.
            if (color >= 0) triggerAnim(player, GeoItem.getOrAssignId(stack, server), "action", "load");
        }
    }

    public static ItemStack ammunition(Player player) {
        if (FlareContent.color(player.getOffhandItem()) >= 0) return player.getOffhandItem();
        for (ItemStack stack : player.getInventory().items)
            if (!stack.isEmpty() && FlareContent.color(stack) >= 0) return stack;
        return ItemStack.EMPTY;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(gun);
        if (!FlareConfig.ALL_DIMENSIONS.get() && !RainCityContent.isCity(level)) return InteractionResultHolder.fail(gun);
        ItemStack ammo = ammunition(player);
        int color = FlareContent.color(ammo);
        if (ammo.isEmpty()) {
            if (!level.isClientSide) level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, .7F, 1);
            return InteractionResultHolder.fail(gun);
        }
        if (color == 2 && !FlarePlacement.openSky(level, player.blockPosition())) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.qihai.flare.open_sky"), true);
            return InteractionResultHolder.fail(gun);
        }
        if (!level.isClientSide) {
            FlareProjectile flare = new FlareProjectile(FlareContent.FLARE.get(), level);
            flare.setOwner(player); flare.setColor(color);
            flare.setPos(player.getX(), player.getEyeY() - .1, player.getZ());
            if (color == 2) flare.shoot(0, 1, 0, 1.7F, 0);
            else flare.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.5F, .4F);
            if (!level.addFreshEntity(flare)) return InteractionResultHolder.fail(gun);
            if (!player.getAbilities().instabuild || !FlareConfig.CREATIVE_FREE.get()) ammo.shrink(1);
            player.getCooldowns().addCooldown(this, FlareConfig.COOLDOWN.get());
            // Remember an emptied gun even if the same color is replenished during cooldown.
            gun.getOrCreateTag().putInt(VISUAL_AMMO, ammunition(player).isEmpty() ? -1 : color);
            triggerAnim(player, GeoItem.getOrAssignId(gun, (ServerLevel) level), "action", "fire");
            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1, 1);
        }
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.qihai.flare_gun"));
    }
}
