package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Day-4+ doors that creak on their own. Every 2-4 minutes spent on day 4 or
 * later, there is a 20% chance that the door event occurs.
 *
 * <p>When the door event triggers, there is a 45% chance of a variant happening
 * where ALL doors around each player within a 30-block radius are rapidly spammed
 * open and shut for 10 entire seconds. Otherwise (55% chance), the standard behavior
 * occurs: the closest door to each player (within a 15-block radius) is toggled once.
 *
 * <p>Purely server-side: the block-state change and the sound sync to every
 * client through the normal block packets.
 */
public final class DoorHandler {

    /** Roll cadence: 2-4 minutes (2400-4800 ticks). */
    private static final int MIN_INTERVAL_TICKS = 20 * 60 * 2;
    private static final int MAX_INTERVAL_TICKS = 20 * 60 * 4;

    /** Probability that a roll actually triggers the door event. */
    private static final float TOGGLE_CHANCE = 0.20F;

    /** Chance that when the event fires, the 10-second 30-block rapid door spam variant occurs instead. */
    private static final float VARIANT_CHANCE = 0.45F;

    /** Max distance (blocks) from the player for standard single-door toggle. */
    private static final int MAX_RADIUS = 15;

    /** Radius (blocks) around each player for the rapid door spam variant. */
    private static final int VARIANT_RADIUS = 30;

    /** Duration of the rapid door spam variant in ticks (10 seconds). */
    private static final int SPAM_DURATION_TICKS = 20 * 10;

    /** Ticks remaining in the active rapid-spam variant; 0 = inactive. */
    private static int spamTicksLeft = 0;

    /** Ticks until the next roll; reset whenever the player is not on day 4+,
     *  so the first attempt happens 2-4 minutes into day 4. */
    private static int ticksUntilNextRoll = MIN_INTERVAL_TICKS;

    private DoorHandler() {
    }

    /** Server tick: handles active rapid spam and rolls for new events.
     *  Registered against {@code ServerTickEvents.START_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        if (spamTicksLeft > 0) {
            spamTicksLeft--;
            if (spamTicksLeft % 2 == 0) {
                spamAllDoorsNearPlayers(server);
            }
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null || DayCounter.currentDay(overworld) < ModConfig.scaledDay(4)
                || !ModConfig.isEnabled("door_creak")) {
            ticksUntilNextRoll = MIN_INTERVAL_TICKS;
            return;
        }
        if (--ticksUntilNextRoll > 0) {
            return;
        }
        ticksUntilNextRoll = MIN_INTERVAL_TICKS
                + overworld.getRandom().nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
        if (overworld.getRandom().nextFloat() >= ModConfig.chance("door_creak", TOGGLE_CHANCE)) {
            return;
        }
        triggerDoorEvent(server);
    }

    /** Dev/test hook — trigger the door event right now, bypassing the day-4
     *  gate and the roll timer. Dispatched by {@code /noname event play door_creak}. */
    public static void toggleDoorNow(MinecraftServer server) {
        triggerDoorEvent(server);
    }

    /** Stops any active rapid door spam variant. Called by {@code /noname event stopall}. */
    public static void stopAll() {
        spamTicksLeft = 0;
    }

    /** Triggers the door event, rolling 45% for the rapid spam variant or 55% for single closest door. */
    private static void triggerDoorEvent(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        boolean isVariant = overworld != null && overworld.getRandom().nextFloat() < VARIANT_CHANCE;
        if (isVariant) {
            spamTicksLeft = SPAM_DURATION_TICKS;
            spamAllDoorsNearPlayers(server);
        } else {
            toggleDoorNearEachPlayer(server);
        }
    }

    /** Toggles the closest door for every real online player. */
    private static void toggleDoorNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            toggleClosestDoor(player);
        }
    }

    /** Spams ALL doors within {@value #VARIANT_RADIUS} blocks around every real online player. */
    private static void spamAllDoorsNearPlayers(MinecraftServer server) {
        Map<ServerLevel, Set<BlockPos>> doorsByLevel = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            BlockPos center = player.blockPosition();
            Set<BlockPos> levelDoors = doorsByLevel.computeIfAbsent(level, k -> new HashSet<>());
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-VARIANT_RADIUS, -VARIANT_RADIUS, -VARIANT_RADIUS),
                    center.offset(VARIANT_RADIUS, VARIANT_RADIUS, VARIANT_RADIUS))) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
                    continue;
                }
                if (state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                    continue;
                }
                levelDoors.add(pos.immutable());
            }
        }

        for (var entry : doorsByLevel.entrySet()) {
            ServerLevel level = entry.getKey();
            for (BlockPos pos : entry.getValue()) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof DoorBlock door && state.hasProperty(DoorBlock.OPEN)) {
                    door.setOpen(null, level, state, pos, !door.isOpen(state));
                }
            }
        }
    }

    /** Finds the closest door within {@value #MAX_RADIUS} blocks and toggles
     *  it (open if closed, close if open). */
    private static void toggleClosestDoor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        BlockPos closest = null;
        double closestDistSq = (double) MAX_RADIUS * MAX_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-MAX_RADIUS, -MAX_RADIUS, -MAX_RADIUS),
                center.offset(MAX_RADIUS, MAX_RADIUS, MAX_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
                continue;
            }
            double distSq = pos.distToCenterSqr(player.getX(), player.getY(), player.getZ());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = pos.immutable();
            }
        }
        if (closest == null) {
            return;
        }
        BlockState state = level.getBlockState(closest);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(null, level, state, closest, !door.isOpen(state));
        }
    }
}
