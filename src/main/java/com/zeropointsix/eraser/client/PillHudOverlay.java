package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.pill.PillNetwork;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PillHudOverlay {
    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("pill_timer", (gui, graphics, partialTick, width, height) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui || !CommonConfig.value(CommonConfig.SHOW_PILL_HUD, true)) return;
            PillNetwork.Status state = PillNetwork.clientStatus;
            boolean active = state.activeTicks() > 0;
            int ticks = active ? state.activeTicks() : state.withdrawalTicks();
            if (ticks > 0) {
                int color = active ? 0xFF9FD0E4 : 0xFFB88F8F;
                ItemStack icon = new ItemStack(state.packDamage() >= state.packUses()
                        ? ModItems.EMPTY_PILL_PACK.get() : ModItems.ENHANCEMENT_PILL_PACK.get());
                if (icon.isDamageableItem()) icon.setDamageValue(Math.round((float) state.packDamage() / Math.max(1, state.packUses()) * icon.getMaxDamage()));
                graphics.renderItem(icon, 8, 8);
                int seconds = (ticks + 19) / 20;
                String time = String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
                graphics.drawString(minecraft.font, Component.translatable(active
                        ? "hud.sound_isolating_eraser.pill_active" : "hud.sound_isolating_eraser.pill_withdrawal", time), 28, 9, color);
                graphics.fill(28, 21, 76, 23, 0x88333333);
                int total = active ? state.activeDuration() : state.withdrawalDuration();
                graphics.fill(28, 21, 28 + Math.min(48, Math.max(1, (int) (48L * ticks / Math.max(1, total)))), 23, color);
            }
            if (minecraft.player.isUsingItem() && minecraft.player.getUseItem().is(ModItems.ENHANCEMENT_PILL_PACK.get())) {
                int total = minecraft.player.getUseItem().getUseDuration();
                int elapsed = total - minecraft.player.getUseItemRemainingTicks();
                int x = width / 2 - 8;
                int y = height / 2 + 10;
                graphics.fill(x, y, x + 16, y + 1, 0x88333333);
                graphics.fill(x, y, x + Math.min(16, 16 * elapsed / Math.max(1, total)), y + 1, 0xFF9FD0E4);
            }
        });
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
    public static final class ConnectionEvents {
        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            PillNetwork.clientStatus = PillNetwork.Status.EMPTY;
        }
    }

    private PillHudOverlay() { }
}
