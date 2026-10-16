package com.zeropointsix.eraser.keeper;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class KeeperData {
    public static final int FILM = 12, VISITS = 8, DURATION = 300, COOLDOWN = 600;
    public static final String SESSION = ModMain.MOD_ID + "_photo_session";
    public static final String COOL_UNTIL = ModMain.MOD_ID + "_photo_cooldown";

    public static int film(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null || !tag.contains("Film", Tag.TAG_INT) ? FILM : Mth.clamp(tag.getInt("Film"), 0, FILM);
    }

    public static void refill(ItemStack stack, long today) {
        CompoundTag tag = stack.getOrCreateTag();
        int film = film(stack);
        if (tag.contains("LastDay", Tag.TAG_LONG)) {
            long last = tag.getLong("LastDay");
            if (today > last) {
                long elapsed = today - last;
                film += elapsed < 0 ? FILM : (int) Math.min(FILM, elapsed);
                tag.putLong("LastDay", today);
            }
        } else tag.putLong("LastDay", today);
        tag.putInt("Film", Math.min(FILM, film));
    }

    public static boolean owns(ItemStack stack, Player player) {
        return stack.hasTag() && stack.getTag().hasUUID("Owner")
                && stack.getTag().getUUID("Owner").equals(player.getUUID());
    }

    public static boolean blank(ItemStack stack) {
        return !stack.hasTag() || stack.getTag().getBoolean("Blank") || stack.getTag().getInt("Uses") >= VISITS;
    }

    public static boolean valid(CompoundTag tag) {
        if (!tag.hasUUID("PhotoId") || !tag.hasUUID("Owner")
                || ResourceLocation.tryParse(tag.getString("Dim")) == null) return false;
        for (String key : new String[]{"X", "Y", "Z", "Yaw", "Pitch", "Health", "Saturation"}) {
            if (!tag.contains(key, Tag.TAG_ANY_NUMERIC) || !Double.isFinite(tag.getDouble(key))) return false;
        }
        for (String key : new String[]{"Yaw", "Pitch", "Health", "Saturation"}) {
            if (!Float.isFinite(tag.getFloat(key))) return false;
        }
        return Math.abs(tag.getDouble("X")) <= 30000000 && Math.abs(tag.getDouble("Z")) <= 30000000
                && Math.abs(tag.getDouble("Y")) < 20000000 && tag.getFloat("Health") > 0
                && tag.getInt("Uses") >= 0 && tag.getInt("Uses") <= VISITS;
    }

    public static ItemStack capture(ServerPlayer player) {
        ItemStack photo = new ItemStack(ModItems.KEEPER_PHOTO.get());
        CompoundTag tag = photo.getOrCreateTag();
        tag.putUUID("PhotoId", UUID.randomUUID());
        tag.putUUID("Owner", player.getUUID());
        tag.putString("Dim", player.level().dimension().location().toString());
        tag.putDouble("X", player.getX()); tag.putDouble("Y", player.getY()); tag.putDouble("Z", player.getZ());
        tag.putFloat("Yaw", player.getYRot()); tag.putFloat("Pitch", player.getXRot());
        tag.putFloat("Health", player.getHealth());
        tag.putInt("Food", player.getFoodData().getFoodLevel());
        tag.putFloat("Saturation", player.getFoodData().getSaturationLevel());
        long time = player.server.overworld().getDayTime();
        tag.putLong("Day", Math.floorDiv(time, 24000));
        long minutes = Math.floorMod(time + 6000, 24000) * 1440 / 24000;
        tag.putString("Clock", String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60));
        tag.putInt("Uses", 0); tag.putBoolean("Blank", false);
        return photo;
    }

    public static void message(Player player, String item, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message." + ModMain.MOD_ID + ".keeper_" + item + "." + key, args), true);
    }

    private KeeperData() { }
}
