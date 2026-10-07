package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.client.WingsClientData;
import com.zeropointsix.eraser.wings.WingsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * 右下角风雷翅 HUD（64x16 铭牌）：四档图标 / 翼徽 / 雷闪冷却环 / 雷力充盈珠。
 * 无灵力条——消耗直接扣原版饥饿值。
 */
public final class WingsHud {
    private static final ResourceLocation ATLAS =
            new ResourceLocation(ModMain.MOD_ID, "textures/gui/wings_hud.png");

    private WingsHud() {}

    public static void render(ForgeGui gui, GuiGraphics g,
                              float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return;
        WingsClientData.WingInfo info = WingsClientData.get(mc.player);
        if (info == null || (!info.deployed() && info.deployAnim() <= 0.01f)) return;

        int x = w - 72, y = h - 30;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // backplate (0,32 64x16)
        g.blit(ATLAS, x, y, 0, 32, 64, 16, 64, 64);
        // slot0: tier icon
        int tier = Math.min(info.tier(), 3);
        g.blit(ATLAS, x, y, tier * 16, 0, 16, 16, 64, 64);
        // slot1: wing emblem
        g.blit(ATLAS, x + 16, y, 0, 16, 16, 16, 64, 64);
        // slot2: blink cooldown — ready ring (16,16); cooldown = dim disc reveal (32,16)。
        // 有效截止 = max(普通冷却, 链满长锁)：连闪打满后长锁也持续显示，不能 2s 就假就绪
        long game = mc.level.getGameTime();
        long until = Math.max(info.blinkCooldownUntil(), info.blinkLockUntil());
        g.blit(ATLAS, x + 32, y, 16, 16, 16, 16, 64, 64);
        if (until > game) {
            // 分母按当前生效的冷却类型取配置值，不写死 160
            long span = Math.max(1, info.blinkLockUntil() > info.blinkCooldownUntil()
                    ? WingsConfig.SERVER.blinkChainCooldownTicks.get()
                    : WingsConfig.SERVER.blinkCooldownTicks.get());
            float ratio = Math.min(1f, (until - game) / (float) span);
            int px = Math.max(1, (int) (ratio * 14));
            g.blit(ATLAS, x + 32, y + 16 - px, 32, 16 + (14 - px), 16, px, 64, 64);
        }
        // slot3: charged orb
        if (game < info.chargedUntil()) {
            g.blit(ATLAS, x + 48, y, 48, 16, 16, 16, 64, 64);
        }
        RenderSystem.disableBlend();

        // blink white flash (whole screen, ~4 ticks)
        if (WingsClientData.flashTicks() > 0) {
            g.fill(0, 0, w, h, 0x66FFFFFF);
        }
    }
}
