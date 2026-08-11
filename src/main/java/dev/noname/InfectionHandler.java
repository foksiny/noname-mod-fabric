package dev.noname;

import dev.noname.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the world infection: the moment day 17 starts (the day 16 → 17
 * transition while the server is running — joining a world that is already
 * on day 17 never replays it), one {@link InfectedBlock} appears exactly 15
 * blocks away from each player's bed respawn point (or the world spawn when
 * the player has no bed), on the surface. From there the block spreads on
 * its own (see {@link InfectedBlock}), faster and faster as days pass.
 *
 * <p>Who gets seeded is remembered per-world in {@link NonameSavedData}
 * (keyed by player UUID), so nobody is seeded twice — and a player who joins
 * a world that is already past day 17 gets their own seed near their bed,
 * exactly once. Seeding is retried every few seconds while a player is
 * online and unseeded, so a bed whose chunks are not loaded yet (player in
 * another dimension, or chunks not loaded at join) still gets its seed once
 * the area loads.
 *
 * <p>The seed replaces the top block of the column (grass, sand, stone...)
 * 15 blocks out; if the exact ring spot is ocean or void, the next ring
 * position is tried. Player-placed blocks are never replaced by the seed.
 *
 * <p>Like all the other day-gated handlers the trigger day honours
 * {@link ModConfig}: {@link ModConfig#scaledDay(long)} shifts it with the
 * speed level and {@link ModConfig#isEnabled(String)} can turn the whole
 * event off. {@code /noname event stopall} pauses the spreading (existing
 * infected blocks stay where they are); {@code /noname event play
 * world_infection} seeds every online player immediately and unpauses.
 */
public final class InfectionHandler {

    /** Base day the infection takes root (scaled by the config speed level). */
    private static final int BASE_DAY = 17;

    /** Exact horizontal distance in blocks from the spawn anchor to the seed. */
    private static final int DISTANCE = 15;

    /** How often unseeded online players are retried, in ticks (5 s). */
    private static final int SEED_RETRY_TICKS = 20 * 5;

    /** The day observed on the previous server tick, so the event fires
     *  exactly on the day 16 → 17 transition while the server is running.
     *  {@link Long#MIN_VALUE} = no observation yet (the first tick only
     *  records the current day and never fires). */
    private static long lastSeenDay = Long.MIN_VALUE;

    /** Whether the once-per-session day-17 seeding already happened. */
    private static boolean done = false;

    /** Whether spreading is paused ({@code /noname event stopall}). */
    private static boolean paused = false;

    private static long lastSeedRetryTick = 0;

    private InfectionHandler() {
    }

    /** {@return true while the infection is paused and must not spread} */
    public static boolean isPaused() {
        return paused;
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Fire exactly when day 17 starts (the day 16 → 17 transition while
        // the server is running). The first tick of a session only records
        // the current day, so joining a world that is already on day 17
        // never replays the event.
        long day = DayCounter.currentDay(overworld);
        long startDay = ModConfig.scaledDay(BASE_DAY);
        if (lastSeenDay == Long.MIN_VALUE) {
            lastSeenDay = day;
        } else if (lastSeenDay < startDay && day >= startDay && !done
                && ModConfig.isEnabled("world_infection")) {
            EventQueue.queueEvent("world_infection", () -> !done, () -> start(server));
        }
        lastSeenDay = day;

        // Retry seeding for online players whose seed could not be placed
        // yet (bed chunks not loaded), every few seconds.
        if (day >= startDay && ModConfig.isEnabled("world_infection") && !paused
                && server.getTickCount() - lastSeedRetryTick >= SEED_RETRY_TICKS) {
            lastSeedRetryTick = server.getTickCount();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.getUUID().equals(FakePlayerUtil.FAKE_UUID)
                        && !savedData(server).isInfectionSeeded(player.getUUID())) {
                    trySeed(player);
                }
            }
        }
    }

    /**
     * Join hook: a player joining a world that is already past day 17 gets
     * their own seed near their bed, exactly once (the saved-data check
     * makes sure it never happens twice).
     */
    public static void onPlayerJoin(ServerGamePacketListenerImpl handler,
                                    PacketSender sender, MinecraftServer server) {
        ServerPlayer player = handler.player;
        if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        if (DayCounter.currentDay(overworld) >= ModConfig.scaledDay(BASE_DAY)
                && ModConfig.isEnabled("world_infection") && !paused
                && !savedData(server).isInfectionSeeded(player.getUUID())) {
            trySeed(player);
        }
    }

    /**
     * Dev/test hook — seed every online real player right now, regardless of
     * the day, and unpause the spreading. Dispatched by
     * {@code /noname event play world_infection}.
     */
    public static void triggerForAllPlayers(MinecraftServer server) {
        paused = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                trySeed(player);
            }
        }
    }

    /** Pauses the spreading. Used by {@code /noname event stopall} — the
     *  blocks already placed stay where they are. */
    public static void stopAll() {
        paused = true;
        EventQueue.release("world_infection");
    }

    // ------------------------------------------------------------------
    // Seeding

    /** The day-17 transition: seed every online real player, once. */
    private static void start(MinecraftServer server) {
        done = true;
        paused = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                trySeed(player);
            }
        }
        server.sendSystemMessage(
                Component.literal("[Noname] Day-17 infection has taken root near your bed"));
        EventQueue.release("world_infection");
    }

    /**
     * Places one infected block 15 blocks away from the player's spawn
     * anchor (their bed respawn point, or the world spawn when they have no
     * bed). {@code true} when a seed was placed; the player is only marked
     * as seeded on success, so a failed attempt (unloaded chunks, ocean on
     * every ring spot) is retried later.
     */
    private static boolean trySeed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel level;
        BlockPos anchor;
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null && server.getLevel(player.getRespawnDimension()) != null) {
            level = server.getLevel(player.getRespawnDimension());
            anchor = respawn;
        } else {
            level = server.overworld();
            anchor = level.getSharedSpawnPos();
        }

        BlockPos spot = findSpot(level, anchor);
        if (spot == null) {
            return false;
        }
        level.setBlock(spot, ModBlocks.INFECTED_BLOCK.defaultBlockState(), 3);
        savedData(server).markInfectionSeeded(player.getUUID());
        return true;
    }

    /**
     * {@return the first usable surface position on the ring of exactly
     * {@value #DISTANCE} blocks around {@code anchor}, in random order, or
     * {@code null} when every ring position is ocean, void or player-placed}
     */
    private static BlockPos findSpot(ServerLevel level, BlockPos anchor) {
        List<BlockPos> ring = new ArrayList<>();
        for (int dx = -DISTANCE; dx <= DISTANCE; dx++) {
            int dz = (int) Math.round(Math.sqrt(DISTANCE * DISTANCE - dx * dx));
            ring.add(new BlockPos(anchor.getX() + dx, 0, anchor.getZ() + dz));
            if (dz != 0) {
                ring.add(new BlockPos(anchor.getX() + dx, 0, anchor.getZ() - dz));
            }
        }

        // Random order, so the seed does not always land on the same side.
        var random = level.getRandom();
        for (int i = ring.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BlockPos tmp = ring.get(i);
            ring.set(i, ring.get(j));
            ring.set(j, tmp);
        }

        for (BlockPos pos : ring) {
            BlockPos surface = surfaceAt(level, pos.getX(), pos.getZ());
            if (surface != null && !PlayerPlacedBlocks.isPlaced(level, surface)) {
                return surface;
            }
        }
        return null;
    }

    /**
     * {@return the surface block position at the column (the top block that
     * blocks motion), or {@code null} when the column is unloaded, void,
     * air-only or covered by fluid (ocean/lava)}
     */
    private static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        if (!level.isLoaded(new BlockPos(x, level.getSeaLevel(), z))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinBuildHeight()) {
            return null;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return null;
        }
        return pos;
    }

    // ------------------------------------------------------------------

    private static NonameSavedData savedData(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                NonameSavedData.factory(), NonameSavedData.ID);
    }
}
