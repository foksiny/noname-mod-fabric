package dev.noname;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.world.phys.Vec3;

/**
 * Day-2 midday "null" visitor. The moment the tick-of-day crosses noon
 * (tick 6000 = 12:00) on day 2 — while the server is running; joining a
 * world that is already past day-2 noon never replays it — a fake player
 * called {@code null} joins the server like a real one
 * ({@link net.minecraft.server.players.PlayerList#placeNewPlayer} broadcasts
 * the vanilla yellow "{@code null joined the game}" message and adds it to
 * the tab list), then:
 *
 * <ul>
 *   <li>T+2 s: the null player "says" in chat
 *       {@code err.message=OMG I'M SO SORRY}</li>
 *   <li>T+3 s (1 s after the chat line): the null player leaves the game
 *       — the vanilla yellow "{@code null left the game"} message is
 *       broadcast, the tab-list entry is removed and the (already-discarded)
 *       entity is dropped from the player list.</li>
 * </ul>
 *
 * <p>The ghost is a real {@link ServerPlayer} in the
 * {@link net.minecraft.server.players.PlayerList} (hence tab list + the
 * vanilla join message), but like the day-3 ghost its connection is a dummy
 * that never sends anything, and its world entity is discarded right after
 * joining — so {@code null} is invisible to every client even while it
 * "speaks" and "leaves". The leave message is broadcast manually because the
 * vanilla {@code "<player> left the game"} line is driven by the real
 * connection's {@code onDisconnect}, and the dummy connection never actually
 * disconnects.
 */
public final class Day2NullJoinHandler {

    /** Day on which the null visitor appears. */
    private static final long TRIGGER_DAY = 2L;

    /** Tick-of-day at noon (12:00) — the trigger threshold, identical to the
     *  day-3 time-skip and the day-6 static events. */
    private static final long NOON_TICK_OF_DAY = 6000L;

    /** Ticks per Minecraft day. */
    private static final long TICKS_PER_DAY = 24000L;

    /** Delay between {@code null} joining and its chat line, in ticks (2 s). */
    private static final int CHAT_LINE_DELAY_TICKS = 20 * 2;

    /** Delay between the chat line and {@code null} leaving the game, in ticks
     *  (1 s). */
    private static final int LEAVE_DELAY_TICKS = 20 * 1;

    /** The chat line {@code null} sends 2 s after joining. */
    private static final String CHAT_LINE = "err.message=OMG I'M SO SORRY";

    /** The profile name shown in the join/leave messages and in the
     *  {@code <name> ...} chat prefix. Mirrored from
     *  {@link FakePlayerUtil#NULL_NAME} so the client-side skin hook and the
     *  server-spawned ghost reference the same identity. */
    private static final String NULL_NAME = FakePlayerUtil.NULL_NAME;

    /** A stable UUID for the {@code null} visitor — distinct from the day-3
     *  ghost's {@link FakePlayerUtil#FAKE_UUID} so the two never clash in the
     *  player list if they happen to coexist (e.g. {@code /noname event
     *  play} forcing both). Mirrored from {@link FakePlayerUtil#NULL_UUID}. */
    private static final java.util.UUID NULL_UUID = FakePlayerUtil.NULL_UUID;

    /** The tick-of-day observed on the previous server tick while day 2 is
     *  running, so the trigger fires exactly when noon is crossed.
     *  {@link Long#MIN_VALUE} = no observation yet (the first day-2 tick of a
     *  session only records the current time, so joining mid-day-2 after noon
     *  never replays the event). */
    private static long lastSeenTickOfDay = Long.MIN_VALUE;

    /** Whether the event already happened this session — guards against firing
     *  more than once if the time is somehow rolled back. */
    private static boolean done = false;

    /** The {@code null} player currently "in" the player list, or
     *  {@code null} if it has not joined yet (or has already left). */
    private static ServerPlayer nullPlayer = null;

    /** The dummy connection attached to {@link #nullPlayer}, kept so the
     *  ServerGamePacketListenerImpl's reference stays valid until after the
     *  leave finishes. */
    private static Connection nullConnection = null;

    /** Remaining ticks until the {@code null} player's chat line. {@code -1}
     *  means no chat line is armed (pre-join or already said). */
    private static int ticksUntilChatLine = -1;

    /** Remaining ticks until the {@code null} player leaves the game.
     *  {@code -1} means no leave is armed (pre-join or already gone). */
    private static int ticksUntilLeave = -1;

    private Day2NullJoinHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Watch the tick-of-day while day 2 is running; the trigger fires the
        // first tick it crosses noon. Joining a world that is already past
        // day-2 noon never replays (the previous tick-of-day is recorded on
        // the first observed day-2 tick).
        if (!done && DayCounter.currentDay(overworld) == TRIGGER_DAY) {
            long tickOfDay = overworld.getDayTime() % TICKS_PER_DAY;
            if (lastSeenTickOfDay == Long.MIN_VALUE) {
                lastSeenTickOfDay = tickOfDay;
            } else if (lastSeenTickOfDay < NOON_TICK_OF_DAY
                    && tickOfDay >= NOON_TICK_OF_DAY) {
                EventQueue.queueEvent("day2_null_join", () -> !done,
                        () -> spawnNullPlayer(server));
            }
            lastSeenTickOfDay = tickOfDay;
        } else {
            // Reset the observer whenever we are not on day 2 so the first
            // day-2 tick of a later session records the time before testing
            // the threshold.
            lastSeenTickOfDay = Long.MIN_VALUE;
        }

        // The chat line 2 s after joining.
        if (ticksUntilChatLine >= 0) {
            tickChatLine(server);
        }

        // The "left the game" departure 1 s after the chat line.
        if (ticksUntilLeave >= 0) {
            tickLeave(server);
        }
    }

    /**
     * Dev/test hook — fire the {@code null} player join right now, regardless
     * of the day. Dispatched by {@code /noname event play day2_null_join}.
     * Idempotent: if a {@code null} visitor is already in the player list this
     * is a no-op.
     */
    public static void triggerNow(MinecraftServer server) {
        if (nullPlayer != null) {
            return;
        }
        EventQueue.queueEvent("day2_null_join", () -> nullPlayer == null,
                () -> spawnNullPlayer(server));
    }

    /**
     * Dev/test hook — forcibly remove the {@code null} visitor and cancel any
     * armed chat line / leave countdown. Used by {@code /noname event
     * stopall}.
     */
    public static void stopAll() {
        if (nullPlayer != null && nullPlayer.connection != null) {
            removeNullPlayer(nullPlayer.server);
        } else {
            // No entity/tabs to clean up — just drop the armed timers.
            nullPlayer = null;
            nullConnection = null;
            EventQueue.release("day2_null_join");
        }
        ticksUntilChatLine = -1;
        ticksUntilLeave = -1;
        // Reset the once-per-session guard so a dev re-trigger fires again.
        done = false;
        lastSeenTickOfDay = Long.MIN_VALUE;
    }

    /** Counts down to the {@code null} player's chat line and broadcasts it
     *  exactly once, then arms the 1-second leave. */
    private static void tickChatLine(MinecraftServer server) {
        if (--ticksUntilChatLine > 0) {
            return;
        }
        ticksUntilChatLine = -1;
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + NULL_NAME + "> " + CHAT_LINE), false);
        // Arm the 1-second-leave that follows the chat line.
        ticksUntilLeave = LEAVE_DELAY_TICKS;
    }

    /** Counts down to the {@code null} player leaving the game. */
    private static void tickLeave(MinecraftServer server) {
        if (--ticksUntilLeave > 0) {
            return;
        }
        ticksUntilLeave = -1;
        removeNullPlayer(server);
    }

    /** Spawns the {@code null} ghost, broadcasts the vanilla join message,
     *  makes it fully invisible/inaudible, and arms the 2-second chat line. */
    private static void spawnNullPlayer(MinecraftServer server) {
        done = true;
        // A dev re-trigger replaces any {@code null} visitor still around.
        if (nullPlayer != null) {
            removeNullPlayer(server);
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            // Defensive: should never happen, but release the lock if so.
            EventQueue.release("day2_null_join");
            return;
        }

        GameProfile profile = new GameProfile(NULL_UUID, NULL_NAME);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        // Pure tab-list / join-message ghost — uses the same dummy connection
        // as the day-3 ghost so every packet sent to it is swallowed and never
        // delivered.
        ServerPlayer ghost = new ServerPlayer(server, overworld, profile,
                ClientInformation.createDefault());
        BlockPos spawn = overworld.getSharedSpawnPos();
        ghost.setPos(Vec3.atCenterOf(spawn));

        Connection connection = FakePlayerHandler.createDummyConnection();
        ghost.connection = new ServerGamePacketListenerImpl(server,
                connection, ghost, cookie);

        // placeNewPlayer broadcasts the vanilla yellow
        // "null joined the game" message, sends the tab-list entry to every
        // client, and adds the entity to the world. We undo the entity part
        // right after, exactly like the day-3 ghost.
        server.getPlayerList().placeNewPlayer(connection, ghost, cookie);

        // Hide it completely and drop its world entity — a pure tab-list
        // ghost: invisible to every client, with no presence in the world.
        ghost.setInvisible(true);
        ghost.setSilent(true);
        ghost.setInvulnerable(true);
        ghost.noPhysics = true;
        ghost.discard();

        nullPlayer = ghost;
        nullConnection = connection;

        // The chat line goes out 2 s after joining; the leave follows 1 s
        // after the chat line (armed inside tickChatLine).
        ticksUntilChatLine = CHAT_LINE_DELAY_TICKS;
        ticksUntilLeave = -1;

        server.sendSystemMessage(
                Component.literal("[Noname] null player spawned: " + NULL_NAME));
    }

    /** Removes the {@code null} ghost from the player list and broadcasts the
     *  vanilla yellow "null left the game" message, then frees the event
     *  lock. Idempotent — safe to call with the player already gone. */
    private static void removeNullPlayer(MinecraftServer server) {
        if (nullPlayer != null && server.getPlayerList().getPlayer(NULL_UUID) != null) {
            // Vanilla's "<player> left the game" line is driven by the real
            // connection's onDisconnect; our dummy connection never actually
            // disconnects, so we broadcast it ourselves to match exactly the
            // message a real "/disconnect"-style departure would produce.
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("multiplayer.player.left",
                            nullPlayer.getDisplayName())
                            .withStyle(ChatFormatting.YELLOW), false);
            // Removes the tab-list entry and the player from the
            // PlayerList's internal maps; the entity itself was already
            // discarded at spawn time.
            server.getPlayerList().remove(nullPlayer);
        }
        nullPlayer = null;
        nullConnection = null;
        EventQueue.release("day2_null_join");
    }
}
