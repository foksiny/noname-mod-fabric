package dev.noname;

import com.mojang.authlib.GameProfile;
import dev.noname.mixin.ConnectionAccessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

/**
 * When the player reaches day 3, a ghost player joins the server: it appears
 * in the tab list as "你的朋友", triggers the vanilla
 * "<player.name.err> joined the game" message, and is fully
 * invisible — the entity is removed from the world immediately, so nobody
 * can ever see it.
 *
 * <p>It is a real {@link ServerPlayer} in the
 * {@link net.minecraft.server.players.PlayerList} (hence tab list + join
 * message), but its connection is a dummy that never sends anything, and its
 * world entity is discarded right after joining. Starting 5 seconds after it
 * joins, it "speaks" in chat (one line every 5 seconds, only once per world —
 * the lines are recorded in {@link NonameSavedData}); one minute after
 * joining the "it hurts to see" sting plays.
 */
public final class FakePlayerHandler {

    /** Delay between the ghost joining and the "it hurts to see" sting, in
     *  server ticks (20 ticks = 1 second → 1200 ticks = 60 seconds). */
    private static final int IT_HURTS_DELAY_TICKS = 20 * 60;

    /** Delay between the ghost joining (or between two consecutive ghost chat
     *  lines) in server ticks — 5 seconds. */
    private static final int GHOST_LINE_DELAY_TICKS = 20 * 5;

    /** Lines the ghost "says" in chat after joining, one every 5 seconds. */
    private static final String[] GHOST_LINES = {
            "i promise i won't send more stuff like this :(",
            "i'm sorry if it disgusts you",
    };

    /** Remaining ticks before the "it hurts to see" sound plays; {@code -1}
     *  means no countdown is armed (pre-day-3 or already fired). */
    private static int ticksUntilItHurts = -1;

    /** Remaining ticks before the next ghost chat line; {@code -1} means no
     *  line is scheduled (pre-join or all lines said). */
    private static int ticksUntilGhostLine = -1;

    /** Index of the next {@link #GHOST_LINES} entry to say. */
    private static int ghostLineIndex = 0;

    private FakePlayerHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // The ghost joins once day 3 arrives (and only once per server session).
        if (DayCounter.currentDay(overworld) >= ModConfig.scaledDay(3)
                && ModConfig.isEnabled("ghost_join")
                && server.getPlayerList().getPlayer(FakePlayerUtil.FAKE_UUID) == null) {
            spawnGhost(server, overworld);
        }

        // Count the armed "it hurts to see" timer down every tick. It is armed
        // by the day-3 spawn (60 s), but the dev command can arm it before
        // day 3 too — the countdown must run then as well, so it is not gated
        // on the day check above.
        if (ticksUntilItHurts >= 0) {
            tickItHurts(server);
        }

        // Same for the ghost's chat lines: armed by the spawn (5 s), but also
        // cancellable via /noname event stopall.
        if (ticksUntilGhostLine >= 0) {
            tickGhostLines(server);
        }
    }

    /**
     * Dev/test hook — {@link dev.noname.command.NonameCommand} calls this to
     * force-spawn the ghost player now, regardless of day. Idempotent: if the
     * ghost is already in the player list, this is a no-op.
     */
    public static void forceSpawn(MinecraftServer server) {
        if (server.getPlayerList().getPlayer(FakePlayerUtil.FAKE_UUID) != null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        spawnGhost(server, overworld);
    }

    /**
     * Dev/test hook — play the "it hurts to see" stinger immediately to every
     * real player, ignoring the post-ghost one-minute countdown.
     */
    public static void playItHurtsNow(MinecraftServer server) {
        ticksUntilItHurts = 1;   // fire on the very next tick
    }

    /**
     * Dev/test hook — cancel the armed "it hurts to see" countdown so a
     * pending one-minute sting never fires. Used by {@code /noname event
     * stopall}.
     */
    public static void cancelArmedItHurts() {
        ticksUntilItHurts = -1;
    }

    /** {@return true if the ghost player is currently in the player list} */
    public static boolean isGhostSpawned(MinecraftServer server) {
        return server.getPlayerList().getPlayer(FakePlayerUtil.FAKE_UUID) != null;
    }

    /** {@return remaining server ticks before the "it hurts to see" sting
     *  plays, or {@code -1} if no countdown is armed (pre-day-3 or fired)} */
    public static int getItHurtsRemainingTicks() {
        return ticksUntilItHurts;
    }

    /**
     * Dev/test hook — cancel the scheduled ghost chat lines. Used by
     * {@code /noname event stopall}. Only the timer is cancelled — lines
     * already said stay recorded in the world's save, so a later spawn will
     * not repeat them.
     */
    public static void cancelArmedGhostLines() {
        ticksUntilGhostLine = -1;
    }

    /** {@return remaining server ticks before the next ghost chat line, or
     *  {@code -1} if none is scheduled (pre-join or all lines said)} */
    public static int getGhostLineRemainingTicks() {
        return ticksUntilGhostLine;
    }

    /** {@return true if every ghost chat line has already been said in this
     *  world (recorded in the per-world save)} */
    public static boolean allGhostLinesSent(MinecraftServer server) {
        return savedData(server).getGhostLinesSent() >= GHOST_LINES.length;
    }

    /** {@return the world's Noname save, loading it if needed} */
    private static NonameSavedData savedData(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(NonameSavedData.factory(), NonameSavedData.ID);
    }

    /**
     * Counts down to the ghost's next chat line and, when it elapses,
     * broadcasts it to every player as a chat-format message and records it
     * in the world's save so it never plays again. Lines are spaced 5 seconds
     * apart ({@link #GHOST_LINE_DELAY_TICKS}); the first is scheduled by the
     * spawn itself, so the ghost "speaks" 5 s after joining.
     *
     * <p>Note: must go through {@code PlayerList.broadcastSystemMessage}, not
     * {@link MinecraftServer#sendSystemMessage} — the server's own
     * {@code sendSystemMessage} only logs to the console in 1.21.1 and never
     * reaches the players.
     */
    private static void tickGhostLines(MinecraftServer server) {
        if (ticksUntilGhostLine < 0 || !ModConfig.isEnabled("ghost_chat")) {
            return;
        }
        if (--ticksUntilGhostLine > 0) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + GHOST_LINES[ghostLineIndex]), false);
        savedData(server).markGhostLineSent(ghostLineIndex);
        if (++ghostLineIndex < GHOST_LINES.length) {
            ticksUntilGhostLine = GHOST_LINE_DELAY_TICKS;
        } else {
            ticksUntilGhostLine = -1;
        }
    }

    /**
     * Counts down the one-minute timer set when the ghost spawned and, once
     * it elapses, broadcasts the "it hurts to see" sound to every real player
     * (played at their own position, so it reads as ambient to each of them).
     */
    private static void tickItHurts(MinecraftServer server) {
        if (ticksUntilItHurts < 0 || !ModConfig.isEnabled("it_hurts_to_see")) {
            return;
        }
        if (--ticksUntilItHurts > 0) {
            return;
        }
        ticksUntilItHurts = -1;

        var sound = ModSounds.IT_HURTS_TO_SEE;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Bound to the player entity itself so the stinger follows each
            // player and can never be walked away from.
            player.level().playSound(
                    null,
                    player,
                    sound,
                    net.minecraft.sounds.SoundSource.AMBIENT,
                    1.0F, 1.0F);
        }
    }

    private static void spawnGhost(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(FakePlayerUtil.FAKE_UUID, FakePlayerUtil.FAKE_NAME);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        // GhostPlayer overrides getTabListDisplayName() so the tab list shows
        // "你的朋友" while the profile name ("player.name.err") stays in use
        // for the join message.
        ServerPlayer ghost = new GhostPlayer(server, level, profile);
        BlockPos spawn = level.getSharedSpawnPos();
        ghost.setPos(Vec3.atCenterOf(spawn));

        // Dummy connection: every packet sent to it is swallowed and never delivered.
        Connection connection = createDummyConnection();
        ghost.connection = new ServerGamePacketListenerImpl(server, connection, ghost, cookie);

        // This adds it to the player list (tab), broadcasts the join message
        // and the tab-list entry (with the display name from the override
        // above) to all real players, and adds the entity to the world. We
        // undo the entity part right after.
        server.getPlayerList().placeNewPlayer(connection, ghost, cookie);

        // Hide it completely: invisible, silent, invulnerable, no physics,
        // and removed from the world — a pure tab-list ghost.
        ghost.setInvisible(true);
        ghost.setSilent(true);
        ghost.setInvulnerable(true);
        ghost.noPhysics = true;
        ghost.discard();

        // Start the one-minute countdown to the "it hurts to see" sting.
        // The sound plays once, 1200 ticks (60 s) after the ghost joins.
        ticksUntilItHurts = IT_HURTS_DELAY_TICKS;

        // The ghost starts "talking" 5 seconds after joining; its lines are
        // spaced 5 seconds apart (see tickGhostLines). Lines already said in
        // a previous session of this world stay said (per-world save), so on
        // a rejoin only the unsent lines (if any) are scheduled again.
        NonameSavedData data = savedData(server);
        if (data.getGhostLinesSent() < GHOST_LINES.length) {
            ticksUntilGhostLine = GHOST_LINE_DELAY_TICKS;
            ghostLineIndex = data.getGhostLinesSent();
        } else {
            ticksUntilGhostLine = -1;
        }

        server.sendSystemMessage(
                Component.literal("[Noname] Ghost player spawned: " + FakePlayerUtil.FAKE_NAME
                        + " (tab: " + FakePlayerUtil.FAKE_TAB_NAME + ")")
        );
    }

    /**
     * The ghost's player class: identical to {@link ServerPlayer} except its
     * tab-list display name. Vanilla's {@code PlayerList} reads the display
     * name from {@link ServerPlayer#getTabListDisplayName()} whenever the tab
     * list is (re)built, so overriding it here is all that's needed.
     */
    private static final class GhostPlayer extends ServerPlayer {

        GhostPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile, ClientInformation.createDefault());
        }

        @Override
        public Component getTabListDisplayName() {
            return Component.literal(FakePlayerUtil.FAKE_TAB_NAME);
        }
    }

    /**
     * A Connection that looks alive to the server but never delivers anything.
     * {@code PlayerList.placeNewPlayer()} calls
     * {@link Connection#setupInboundProtocol}, which writes a pipeline
     * configuration task into the channel and waits for it — a bare
     * {@code new Connection(...)} crashes there with an NPE ("this.channel is
     * null"). So the connection gets a real netty channel: an embedded one
     * with
     * <ul>
     *   <li>{@link UnconfiguredPipelineHandler.Inbound} — the handler a real
     *       pipeline uses to run the configuration task (it replaces itself
     *       with the real {@code PacketDecoder} and installs the bundler);</li>
     *   <li>a sink outbound handler — acknowledges and releases every packet
     *       written afterwards, so nothing is ever delivered or accumulated.</li>
     * </ul>
     */
    static Connection createDummyConnection() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        ReferenceCountUtil.release(msg);
                        promise.setSuccess();
                    }
                },
                new UnconfiguredPipelineHandler.Inbound()
        );
        ((ConnectionAccessor) connection).noname$setChannel(channel);
        return connection;
    }
}
