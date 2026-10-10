package com.zeropointsix.eraser.eraser;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.block.EraserWallBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class EraserAggro {
    public static final TagKey<EntityType<?>> ISOLATED = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation(ModMain.MOD_ID, "eraser_isolated_mobs"));

    public static boolean blocked(Entity observer, Entity target) {
        if (!(observer instanceof Mob) || !(target instanceof Player)
                || observer.level() != target.level() || !observer.getType().is(ISOLATED)) return false;
        Vec3 from = observer.getEyePosition(), to = target.getEyePosition();
        if (from.distanceToSqr(to) > 128 * 128) return false;
        var level = observer.level();
        BlockHitResult hit = BlockGetter.traverseBlocks(from, to, null, (context, pos) -> {
            if (!level.hasChunkAt(pos)) return null;
            var state = level.getBlockState(pos);
            return EraserWallBlock.isEraserWall(state)
                    ? state.getCollisionShape(level, pos).clip(from, to, pos) : null;
        }, context -> null);
        return hit != null;
    }

    @SubscribeEvent
    public static void acquiring(LivingChangeTargetEvent event) {
        if (!event.getEntity().level().isClientSide && blocked(event.getEntity(), event.getNewTarget())) {
            event.setNewTarget(null);
        }
    }

    @SubscribeEvent
    public static void maintaining(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || mob.tickCount % 5 != 0) return;
        if (blocked(mob, mob.getTarget())) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (blocked(mob, mob.getLastHurtByMob())) mob.setLastHurtByMob(null);
    }

    private EraserAggro() { }
}
