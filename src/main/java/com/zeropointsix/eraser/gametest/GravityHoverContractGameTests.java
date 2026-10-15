package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityHoverContractGameTests {
    private static final Set<Long> tickets = new HashSet<>();
    private static final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
    private static Zombie zombie;
    private static GravityFieldEntity field;

    @AfterBatch(batch = "gravity_hover_contract")
    public static void cleanup(ServerLevel level) {
        if (field != null) field.discard();
        if (zombie != null) zombie.discard();
        field = null; zombie = null;
        previous.forEach((pos, state) -> level.setBlock(pos, state, 3));
        previous.clear();
        for (long key : tickets) {
            ChunkPos chunk = new ChunkPos(key);
            level.setChunkForced(chunk.x, chunk.z, false);
        }
        tickets.clear();
    }

    private static void dryShaft(ServerLevel level, BlockPos center) {
        // Seal the entire mob volume, not only the block at its feet, against flowing water.
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 6, 2))) {
            previous.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            boolean shell = pos.getY() == center.getY() - 1 || pos.getY() == center.getY() + 6
                    || Math.abs(pos.getX() - center.getX()) == 2 || Math.abs(pos.getZ() - center.getZ()) == 2;
            level.setBlock(pos, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @GameTest(template = "empty", batch = "gravity_hover_contract", timeoutTicks = 400)
    public static void hostileHoverRetainsOriginalDryPhysicsBounds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(3, 7, 3));
        BlockPos outside = origin.west(18);
        for (int x = (origin.getX() - 20) >> 4; x <= (origin.getX() + 20) >> 4; x++) {
            for (int z = (origin.getZ() - 20) >> 4; z <= (origin.getZ() + 20) >> 4; z++) {
                long key = ChunkPos.asLong(x, z);
                if (!level.getForcedChunks().contains(key)) {
                    level.setChunkForced(x, z, true); tickets.add(key);
                }
                level.getChunk(x, z);
            }
        }
        dryShaft(level, origin); dryShaft(level, outside);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(origin)
                        && level.isPositionEntityTicking(outside), "both dry physics locations are entity-ticking"))
                .thenExecute(() -> {
                    Vec3 center = Vec3.atBottomCenterOf(origin), landing = Vec3.atBottomCenterOf(outside);
                    zombie = EntityType.ZOMBIE.create(level);
                    zombie.setPos(center);
                    zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                    zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
                    zombie.setHealth(300);
                    zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    level.addFreshEntity(zombie);
                    field = GravityFieldEntity.create(level, UUID.randomUUID(), center);
                    level.addFreshEntity(field);
                    int[] exitTicks = new int[1];
                    h.runAfterDelay(85, () -> {
                        double height = zombie.getY() - center.y;
                        h.assertTrue(!zombie.isInWater(), "dry hover fixture has no water contact");
                        h.assertTrue(height > 2.5 && height <= 3.3, "original bounded hover: " + height);
                        h.assertTrue(zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != null
                                && zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 4, "slowness V retained");
                        System.out.println("GRAVITY_STRICT_HOVER_PASSED height=" + height + " ticks=" + zombie.tickCount);
                        zombie.getNavigation().stop();
                        zombie.setPos(landing.add(0, 3, 0));
                        zombie.setDeltaMovement(Vec3.ZERO);
                        exitTicks[0] = zombie.tickCount;
                    });
                    h.runAfterDelay(115, () -> {
                        int elapsed = zombie.tickCount - exitTicks[0];
                        h.assertTrue(elapsed == 30, "thirty real entity ticks since exit: " + elapsed);
                        h.assertTrue(!zombie.isInWater(), "dry landing fixture has no water contact");
                        h.assertTrue(!zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "debuff fades outside cylinder");
                        double error = Math.abs(zombie.getY() - landing.y);
                        h.assertTrue(zombie.onGround() && error <= .01, "original exact natural landing: "
                                + zombie.position() + " ground=" + zombie.onGround() + " error=" + error);
                        System.out.println("GRAVITY_STRICT_LANDING_PASSED entityTicks=" + elapsed
                                + " ground=" + zombie.onGround() + " error=" + error);
                        h.succeed();
                    });
                });
    }

    private GravityHoverContractGameTests() { }
}
