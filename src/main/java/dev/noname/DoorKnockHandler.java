package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Day-3+ knocks on the player's own doors. Every 2-4 minutes per player
 * there is a 30% chance that, if a door the player placed is within
 * {@value #MAX_RADIUS} blocks, one of the nine door-knock clips
 * ({@link ModSounds#DOOR_KNOCK}) plays at that door — as if someone is
 * knocking from the other side.
 *
 * <p>Only doors tracked by {@link PlayerPlacedBlocks} count, so doors the
 * player found in villages or built by someone else never trigger. The sound
 * is played server-side at the door's position, so the player hears it with
 * the normal distance falloff, coming from the door itself.
 */
public final class DoorKnockHandler {

    /** Roll cadence: 2-4 minutes (2400-4800 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 2;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 4;

    /** Probability that a roll actually knocks on a door. */
    private static final float KNOCK_CHANCE = 0.30F;

    /** Max distance (blocks) from the player for a door to count. */
    private static final int MAX_RADIUS = 10;

    /** Base volume of the knock; audible with normal distance falloff. */
    private static final float BASE_VOLUME = 1.0F;

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    private DoorKnockHandler() {
    }

    /** Server tick: rolls for a knock per player. Registered against
     *  {@code ServerTickEvents.START_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        RandomSource random = overworld.getRandom();

        if (DayCounter.currentDay(overworld) < ModConfig.scaledDay(3)
                || !ModConfig.isEnabled("door_knock")) {
            ticksUntilRoll.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            UUID uuid = player.getUUID();
            int remaining = ticksUntilRoll.getOrDefault(uuid, nextRollInterval(random));
            if (remaining > 1) {
                ticksUntilRoll.put(uuid, remaining - 1);
                continue;
            }
            ticksUntilRoll.put(uuid, nextRollInterval(random));
            if (random.nextFloat() < ModConfig.chance("door_knock", KNOCK_CHANCE)) {
                knockOnDoor(player);
            }
        }
    }

    /** Dev/test hook — knock on a player-placed door near each player right
     *  now, bypassing the day gate and the roll timer. Dispatched by
     *  {@code /noname event play door_knock}. */
    public static void triggerNow(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            knockOnDoor(player);
        }
    }

    /** Cancels every armed roll. Called by {@code /noname event stopall}. */
    public static void stopAll() {
        ticksUntilRoll.clear();
    }

    /** Random 2-4 minutes (2400-4800 ticks) until the next roll. */
    private static int nextRollInterval(RandomSource random) {
        return MIN_ROLL_TICKS + random.nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1);
    }

    /** Finds the closest player-placed door within {@value #MAX_RADIUS}
     *  blocks and plays a random knock at it. Silently does nothing when
     *  there is no such door nearby. */
    private static void knockOnDoor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        BlockPos center = player.blockPosition();
        BlockPos doorPos = null;
        double closestDistSq = (double) MAX_RADIUS * MAX_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-MAX_RADIUS, -MAX_RADIUS, -MAX_RADIUS),
                center.offset(MAX_RADIUS, MAX_RADIUS, MAX_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
                continue;
            }
            if (state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            if (!PlayerPlacedBlocks.isPlaced(level, pos)) {
                continue;
            }
            double distSq = pos.distToCenterSqr(player.getX(), player.getY(), player.getZ());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                doorPos = pos.immutable();
            }
        }
        if (doorPos == null) {
            return;
        }
        float volume = BASE_VOLUME + random.nextFloat() * 0.2F - 0.1F;
        float pitch = random.nextFloat() * 0.2F + 0.9F;
        level.playSound(null, doorPos.getX() + 0.5D, doorPos.getY() + 0.5D,
                doorPos.getZ() + 0.5D, ModSounds.DOOR_KNOCK, SoundSource.BLOCKS,
                volume, pitch);
    }
}
