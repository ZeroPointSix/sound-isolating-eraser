package com.zeropointsix.eraser.cityqa;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import com.zeropointsix.eraser.yuji.city.RainCityContent;
import com.zeropointsix.eraser.yuji.city.RainCityData;
import com.zeropointsix.eraser.yuji.city.RainCityEntry;
import com.zeropointsix.eraser.yuji.city.RainCityEvents;
import com.zeropointsix.eraser.yuji.city.RainCityTemplates;
import com.zeropointsix.eraser.yuji.city.RecliningBuddha;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(RainCityQa.ID)
public final class RainCityQa {
    public static final String ID = "raincity_qa";
    private static final List<String> SHOTS = List.of("grey", "black", "buddha_idle", "buddha_fall", "beam", "returned");
    private final List<YujiKind> kinds = new ArrayList<>(RainCityContent.MARKS.keySet());
    private final EnumSet<YujiKind> pickedUp = EnumSet.noneOf(YujiKind.class);
    private String phase = "login";
    private long ticks, phaseTick, connectedAt = -1;
    private Vec3 home;
    private YujiMonster target;
    private long targetTick;
    private int actualKills;
    private boolean finished;

    public RainCityQa() {
        if (QaFiles.ENABLED) MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        ticks++;
        try {
            QaFiles.require(ticks - phaseTick < 2400, "Timed out waiting for phase " + phase);
            MinecraftServer server = event.getServer();
            ServerPlayer player = server.getPlayerList().getPlayerByName("RainCityQA");
            if (player == null) return;
            if (connectedAt < 0) connectedAt = ticks;
            if (ticks - connectedAt < 60) return;
            QaFiles.require(player.isAlive(), "QA player died during " + phase);
            if (!player.getAbilities().flying) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            ServerLevel city = server.getLevel(RainCityContent.LEVEL);
            QaFiles.require(city != null, "City dimension did not load");
            RainCityData data = RainCityData.get(city);
            switch (phase) {
                case "login" -> {
                    QaFiles.require(player.level().dimension().equals(Level.OVERWORLD), "Fresh QA player is not in the overworld");
                    QaFiles.require(!data.ringing() && data.players.isEmpty(), "QA world is not a fresh isolated world");
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    city.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    BlockPos safe = server.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            server.overworld().getSharedSpawnPos().offset(8, 0, 8));
                    teleport(player, server.overworld(), safe.getX() + .5, safe.getY(), safe.getZ() + .5, 17, 7);
                    home = player.position();
                    QaFiles.require(server.overworld().noCollision(player), "Home is obstructed");
                    QaFiles.require(RainCityEntry.request(player), "Production entry request rejected");
                    advance("entry");
                }
                case "entry" -> {
                    if (!RainCityContent.isCity(player.level())) return;
                    verifyArrival(player, city, state(data, player));
                    camera(player, city, 96.5, 132, 136.5, 180, 8);
                    QaFiles.require(city.noCollision(player), "Grey-rain camera intersects city blocks");
                    shot("grey");
                }
                case "grey" -> {
                    QaFiles.require(city.noCollision(player), "Grey-rain camera intersects city blocks");
                    if (!passed("grey")) return;
                    state(data, player).putLong("Entered", city.getGameTime() - 12000);
                    data.setDirty();
                    shot("black");
                }
                case "black" -> {
                    QaFiles.require(city.noCollision(player), "Black-rain camera intersects city blocks");
                    if (!passed("black")) return;
                    QaFiles.require(state(data, player).getInt("Kills") == 0, "Unexpected initial kills");
                    camera(player, city, 96.5, 230, 120.5, 180, 0);
                    advance("kills");
                }
                case "kills" -> {
                    collectDrops(player, city);
                    if (actualKills < 50) {
                        killOne(player, city, data);
                        return;
                    }
                    QaFiles.require(pickedUp.size() == 10, "Did not pick up all ten physical item-entity marks: " + pickedUp);
                    QaFiles.require(state(data, player).getInt("Kills") == 50, "Production kill counter must equal fifty");
                    QaFiles.require((state(data, player).getInt("Marks") & 1023) == 1023, "Personal ten-kind credit is incomplete");
                    QaFiles.require(RainCityEvents.eligible(player, state(data, player)), "Real kills and picked-up marks are not eligible");
                    city.getChunk(0, 0);
                    camera(player, city, .5, 127, 12.5, 180, 0);
                    advance("scene");
                }
                case "scene" -> {
                    RecliningBuddha buddha = buddha(city);
                    if (buddha == null) return;
                    followBuddha(player, city, buddha);
                    shot("buddha_idle");
                }
                case "buddha_idle" -> {
                    RecliningBuddha buddha = requireBuddha(city);
                    if (!passed("buddha_idle")) return;
                    ringActualBell(player, city, data);
                    QaFiles.require(data.ringing(), "Actual useItemOn did not start the bell scene");
                    followBuddha(player, city, buddha);
                    shot("buddha_fall");
                }
                case "buddha_fall" -> {
                    followBuddha(player, city, requireBuddha(city));
                    if (!passed("buddha_fall") || !data.open(city.getGameTime())) return;
                    cameraLookingAt(player, city, new Vec3(.5, 32, 12.5), new Vec3(0, 49, 0));
                    shot("beam");
                }
                case "beam" -> {
                    if (!passed("beam")) return;
                    QaFiles.require(city.getGameTime() - data.bellAt >= 92, "Beam requested before production unlock");
                    camera(player, city, .5, 34, .5, 180, 0);
                    advance("return");
                }
                case "return" -> {
                    if (RainCityContent.isCity(player.level())) return;
                    QaFiles.require(player.level().dimension().equals(Level.OVERWORLD), "Beam returned to the wrong dimension");
                    QaFiles.require(player.position().distanceToSqr(home) < .0001, "Home coordinates were not restored: " + player.position() + " != " + home);
                    QaFiles.require(!state(data, player).getBoolean("Active"), "Production return did not clear active state");
                    shot("returned");
                }
                case "returned" -> {
                    if (!passed("returned")) return;
                    for (String shot : SHOTS) QaFiles.require(passed(shot), "Missing verified screenshot " + shot);
                    QaFiles.write("server-success.json", Map.of("passed", true, "actualKills", actualKills,
                            "physicalMarkKinds", pickedUp.size(), "homeRestored", true, "actualBellInteraction", true,
                            "automaticBeamReturn", true, "screenshots", SHOTS, "ticks", ticks));
                    finished = true;
                }
                default -> throw new IllegalStateException("Unknown phase " + phase);
            }
        } catch (Throwable failure) {
            finished = true;
            QaFiles.failure("server", phase, failure);
        }
    }

    private void verifyArrival(ServerPlayer player, ServerLevel city, CompoundTag state) {
        BlockPos feet = player.blockPosition();
        BlockPos origin = RainCityTemplates.HOSPITAL;
        QaFiles.require(state.getBoolean("Active") && feet.asLong() == state.getLong("Arrival"), "Entry did not persist the actual arrival");
        QaFiles.require(feet.getX() >= origin.getX() && feet.getX() < origin.getX() + 32
                && feet.getZ() >= origin.getZ() && feet.getZ() < origin.getZ() + 32
                && feet.getY() > origin.getY() && feet.getY() <= origin.getY() + 5, "Entry missed the first hospital floor");
        QaFiles.require(city.noCollision(player) && city.getFluidState(feet).isEmpty(), "Hospital arrival is obstructed or submerged");
        QaFiles.require(city.getBlockState(feet.below()).isFaceSturdy(city, feet.below(), Direction.UP), "Hospital arrival lacks a supporting floor");
        boolean besideBed = false;
        for (Direction direction : Direction.Plane.HORIZONTAL)
            besideBed |= city.getBlockState(feet.relative(direction)).getBlock() instanceof BedBlock;
        QaFiles.require(besideBed, "Hospital arrival is not beside a bed");
        QaFiles.write("arrival.json", Map.of("passed", true, "x", player.getX(), "y", player.getY(), "z", player.getZ()));
    }

    private void killOne(ServerPlayer player, ServerLevel city, RainCityData data) {
        if (target == null) {
            YujiKind kind = kinds.get(actualKills < kinds.size() ? actualKills : 0);
            target = YujiContent.MOBS.get(kind).get().create(city);
            QaFiles.require(target != null, "Cannot create monster " + kind);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.moveTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
            QaFiles.require(city.addFreshEntity(target), "Production rejected monster " + kind);
            targetTick = ticks;
        }
        QaFiles.require(ticks - targetTick < 160, "Player-attributed damage did not kill " + target.kind());
        target.invulnerableTime = 0;
        target.hurt(city.damageSources().playerAttack(player), 1_000_000F);
        if (!target.isAlive()) {
            int credited = state(data, player).getInt("Kills");
            QaFiles.require(credited == actualKills + 1, "Real monster death was not credited exactly once");
            actualKills++;
            target = null;
        }
    }

    private void collectDrops(ServerPlayer player, ServerLevel city) {
        for (ItemEntity drop : city.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(12))) {
            for (var entry : RainCityContent.MARKS.entrySet()) {
                if (!drop.getItem().is(entry.getValue().get())) continue;
                drop.setNoPickUpDelay();
                drop.playerTouch(player);
                if (drop.isRemoved() && player.getInventory().contains(new ItemStack(entry.getValue().get()))) pickedUp.add(entry.getKey());
            }
        }
    }

    private void ringActualBell(ServerPlayer player, ServerLevel city, RainCityData data) {
        QaFiles.require(!data.ringing(), "Bell was already activated without an interaction");
        for (BlockPos pos : BlockPos.betweenClosed(-7, 10, -7, 7, 26, 7)) {
            if (!copper(city.getBlockState(pos))) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Vec3 eye = Vec3.atBottomCenterOf(pos.relative(side, 2));
                if (!city.noCollision(new AABB(eye.x - .3, eye.y, eye.z - .3, eye.x + .3, eye.y + 1.8, eye.z + .3))) continue;
                cameraLookingAt(player, city, eye, Vec3.atCenterOf(pos));
                QaFiles.require(player.position().distanceToSqr(Vec3.atCenterOf(pos)) < 20.25, "Bell is outside normal interaction reach");
                player.gameMode.useItemOn(player, city, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), side, pos.immutable(), false));
                QaFiles.require(data.ringing(), "Real copper-block interaction did not ring the bell");
                QaFiles.write("bell.json", Map.of("passed", true, "x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "gameTime", data.bellAt));
                return;
            }
        }
        throw new IllegalStateException("No reachable copper block found in the actual bronze-bell structure");
    }

    private static boolean copper(BlockState state) {
        return state.is(Blocks.CUT_COPPER) || state.is(Blocks.OXIDIZED_CUT_COPPER) || state.is(Blocks.WEATHERED_COPPER)
                || state.is(Blocks.EXPOSED_CUT_COPPER) || state.is(Blocks.WEATHERED_CUT_COPPER) || state.is(Blocks.OXIDIZED_COPPER);
    }

    private static CompoundTag state(RainCityData data, ServerPlayer player) {
        CompoundTag result = data.players.get(player.getUUID());
        QaFiles.require(result != null, "No production player state exists");
        return result;
    }

    private static RecliningBuddha buddha(ServerLevel city) {
        return city.getEntitiesOfClass(RecliningBuddha.class, new AABB(-32, 0, -32, 32, 160, 32)).stream().findFirst().orElse(null);
    }

    private static RecliningBuddha requireBuddha(ServerLevel city) {
        RecliningBuddha result = buddha(city);
        QaFiles.require(result != null, "Production Buddha disappeared");
        return result;
    }

    private static void followBuddha(ServerPlayer player, ServerLevel city, RecliningBuddha buddha) {
        cameraLookingAt(player, city, new Vec3(.5, buddha.getY() + 4, 12.5), new Vec3(0, buddha.getY() + 5, 0));
    }

    private static void cameraLookingAt(ServerPlayer player, ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from.add(0, player.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        camera(player, level, from.x, from.y, from.z, yaw, pitch);
    }

    private static void camera(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        teleport(player, level, x, y, z, yaw, pitch);
        // Grounded clients can cancel flight during the hospital/home transition.
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    private static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    private void advance(String next) {
        phase = next;
        phaseTick = ticks;
        org.slf4j.LoggerFactory.getLogger("RainCityQA").info("City QA phase: {}", next);
        QaFiles.write("server-phase.json", Map.of("phase", next, "tick", ticks));
    }

    private void shot(String name) {
        advance(name);
        QaFiles.write("phase.json", Map.of("phase", name, "tick", ticks,
                "dimension", name.equals("returned") ? "minecraft:overworld" : "qihai:yuji_fodu"));
    }

    private static boolean passed(String name) {
        var marker = QaFiles.read(name + ".pass.json");
        return marker != null && marker.get("passed").getAsBoolean() && name.equals(marker.get("phase").getAsString());
    }
}
