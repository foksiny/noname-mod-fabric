package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Day-4+ doors that creak on their own. Every 2-4 minutes spent on day 4 or
 * later, there is a 15% chance that the closest door to each player (within
 * a 15-block radius) is toggled: a closed door opens, an open door closes.
 * The correct door sound (wooden, iron, …) plays at the door through the
 * vanilla {@link DoorBlock#setOpen} path, so it stays audible only to
 * players nearby.
 *
 * <p>Purely server-side: the block-state change and the sound sync to every
 * client through the normal block packets.
 */
public final class DoorHandler {

    /** Roll cadence: 2-4 minutes (2400-4800 ticks). */
    private static final int MIN_INTERVAL_TICKS = 20 * 60 * 2;
    private static final int MAX_INTERVAL_TICKS = 20 * 60 * 4;

    /** Probability that a roll actually toggles a door. */
    private static final float TOGGLE_CHANCE = 0.15F;

    /** Max distance (blocks) from the player a door may be toggled. */
    private static final int MAX_RADIUS = 15;

    /** Ticks until the next roll; reset whenever the player is not on day 4+,
     *  so the first attempt happens 2-4 minutes into day 4. */
    private static int ticksUntilNextRoll = MIN_INTERVAL_TICKS;

    private DoorHandler() {
    }

    /** Server tick: rolls the chance and toggles the nearest doors.
     *  Registered against {@code ServerTickEvents.START_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (DayCounter.currentDay(overworld) < 4) {
            ticksUntilNextRoll = MIN_INTERVAL_TICKS;
            return;
        }
        if (--ticksUntilNextRoll > 0) {
            return;
        }
        ticksUntilNextRoll = MIN_INTERVAL_TICKS
                + overworld.getRandom().nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
        if (overworld.getRandom().nextFloat() >= TOGGLE_CHANCE) {
            return;
        }
        toggleDoorNearEachPlayer(server);
    }

    /** Dev/test hook — toggle the closest door for every online player right
     *  now, bypassing the day-4 gate and the roll timer. Dispatched by
     *  {@code /noname event play door_creak}. */
    public static void toggleDoorNow(MinecraftServer server) {
        toggleDoorNearEachPlayer(server);
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
