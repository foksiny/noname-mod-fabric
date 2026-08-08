package dev.noname;

import com.mojang.authlib.GameProfile;
import dev.noname.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Day-7 apparition: the moment day 7 starts (the day 6 → 7 transition while
 * the server is running — joining a world that is already on day 7 never
 * replays it), the fake player — the same flesh-skinned "你的朋友" as the
 * day-3 ghost — appears 2 blocks in front of every real player, facing them,
 * plays the day-7 glitch at its position, vanishes after 0.3 seconds, and 2
 * seconds after the event speaks one line in chat.
 *
 * <p>Then, when the sun reaches the middle of day 7 (tick-of-day 6000 = noon),
 * the fake player sends one more line in chat. Like the noon triggers of the
 * day-2 {@code null} visitor and the day-3 time-skip, it fires exactly once
 * per session and never replays when joining a world whose day-7 noon has
 * already passed.
 *
 * <p>Unlike the day-3 ghost (a pure tab-list entry), the apparition is a real
 * tracked {@link ServerPlayer} entity added straight into the world, so the
 * client renders it through vanilla networking: for a player-type entity the
 * client only creates it when it knows the UUID from the player list — which
 * it does here, because the ghost has been in the tab list since day 3 with
 * the flesh skin and the "你的朋友" display name. The apparition is removed
 * again after 6 ticks and, like the ghost, its connection is a dummy that
 * swallows every packet it tries to send.
 */
public final class Day7FakePlayerHandler {

    /** How long the apparition stays in the world, in ticks (20 tps → 6
     *  ticks = 0.3 seconds). */
    private static final int APPARITION_DURATION_TICKS = 20 * 3 / 10;

    /** Delay between the apparition and its chat line, in ticks (2 s). */
    private static final int CHAT_LINE_DELAY_TICKS = 20 * 2;

    /** The line the fake player says 2 seconds after appearing. */
    private static final String CHAT_LINE = "do i look beautiful? :)";

    /** Tick-of-day at noon (12:00) — when the day-7 lonely chat line fires. */
    private static final long NOON_TICK_OF_DAY = 6000L;

    /** Ticks per Minecraft day. */
    private static final long TICKS_PER_DAY = 24000L;

    /** The line the fake player sends in chat at day-7 noon. */
    private static final String LONELY_CHAT_LINE =
            "Day 9 is lonely. Food is harder to get.";

    /** How far in front of the player the apparition spawns, in blocks. */
    private static final double APPARITION_DISTANCE = 2.0D;

    /** Server tick at which the current apparitions must vanish. */
    private static long removeAtTick = -1;

    /** Remaining ticks before the chat line is broadcast; {@code -1} = none
     *  armed (pre-event or already said). */
    private static int ticksUntilChatLine = -1;

    /** The day observed on the previous server tick, so the apparition fires
     *  exactly on the day 6 → 7 transition while the server is running —
     *  joining a world that is already on day 7 (or later) never replays it.
     *  {@link Long#MIN_VALUE} = no observation yet (the first tick only
     *  records the current day and never fires). */
    private static long lastSeenDay = Long.MIN_VALUE;

    /** The apparitions currently in the world (one per real player). */
    private static final List<ServerPlayer> apparitions = new ArrayList<>();

    /** The tick-of-day observed on the previous server tick while day 7 is
     *  running, so the lonely line fires exactly when noon is crossed.
     *  {@link Long#MIN_VALUE} = no observation yet (the first day-7 tick of a
     *  session only records the current time, so joining mid-day-7 after noon
     *  never replays the line). */
    private static long lastSeenNoonTickOfDay = Long.MIN_VALUE;

    /** Whether the day-7 noon lonely line already fired this session —
     *  guards against a duplicate if the time is somehow rolled back. */
    private static boolean lonelyChatSent = false;

    private Day7FakePlayerHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Fire exactly when day 7 starts (the day 6 → 7 transition while the
        // server is running). The first tick of a session only records the
        // current day, so joining a world that is already on day 7 never
        // replays the event.
        long day = DayCounter.currentDay(overworld);
        long day7 = ModConfig.scaledDay(7);
        if (lastSeenDay == Long.MIN_VALUE) {
            lastSeenDay = day;
        } else if (ModConfig.isEnabled("day7_fake") && lastSeenDay < day7 && day >= day7) {
            EventQueue.queueEvent("day7_fake", Day7FakePlayerHandler::shouldRunDay7Event,
                    () -> spawnApparitions(server));
        }
        lastSeenDay = day;

        // Day-7 noon: the fake player sends one lonely line in chat, exactly
        // once per session. Joining a world whose day-7 noon has already
        // passed never replays it (the previous tick-of-day is recorded on
        // the first observed day-7 tick).
        if (!lonelyChatSent && ModConfig.isEnabled("day7_lonely") && day == day7) {
            long tickOfDay = overworld.getDayTime() % TICKS_PER_DAY;
            if (lastSeenNoonTickOfDay == Long.MIN_VALUE) {
                lastSeenNoonTickOfDay = tickOfDay;
            } else if (lastSeenNoonTickOfDay < NOON_TICK_OF_DAY
                    && tickOfDay >= NOON_TICK_OF_DAY) {
                lonelyChatSent = true;
                sendLonelyChatLine(server);
            }
            lastSeenNoonTickOfDay = tickOfDay;
        } else {
            // Reset the observer whenever we are not on day 7 so the first
            // day-7 tick of a later session records the time before testing
            // the threshold.
            lastSeenNoonTickOfDay = Long.MIN_VALUE;
        }

        // Vanish after the 0.3 s.
        if (!apparitions.isEmpty() && server.getTickCount() >= removeAtTick) {
            removeApparitions();
        }

        // The chat line 2 s after the event.
        if (ticksUntilChatLine >= 0) {
            tickChatLine(server);
        }
    }

    private static boolean shouldRunDay7Event() {
        // Day 7 event only runs once - we track this with a static flag
        return !hasRunDay7Event;
    }

    /** Whether the day 7 event has already run this session. */
    private static boolean hasRunDay7Event = false;

    /**
     * Dev/test hook — spawn the apparition right now, regardless of the day.
     * Dispatched by {@code /noname event play day7_fake}. Does not mark the
     * day-7 trigger as done, so the natural trigger still fires when day 7
     * arrives.
     */
    public static void triggerNow(MinecraftServer server) {
        EventQueue.queueEvent("day7_fake", () -> true, () -> spawnApparitions(server));
    }

    /**
     * Dev/test hook — remove any apparition on screen and cancel the armed
     * chat line. Used by {@code /noname event stopall}.
     */
    public static void stopAll() {
        removeApparitions();
        ticksUntilChatLine = -1;
    }

    /**
     * Dev/test hook — send the day-7 noon lonely line right now, regardless
     * of the day. Dispatched by {@code /noname event play day7_lonely}. Marks
     * the noon line as sent so the natural day-7 trigger never repeats it
     * this session.
     */
    public static void triggerLonelyChatNow(MinecraftServer server) {
        lonelyChatSent = true;
        lastSeenNoonTickOfDay = Long.MIN_VALUE;
        sendLonelyChatLine(server);
    }

    /** Broadcasts the day-7 noon line as a chat message from the fake
     *  player. */
    private static void sendLonelyChatLine(MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + LONELY_CHAT_LINE), false);
    }

    /** Counts down to the fake player's chat line and broadcasts it exactly
     *  once. */
    private static void tickChatLine(MinecraftServer server) {
        if (--ticksUntilChatLine > 0) {
            return;
        }
        ticksUntilChatLine = -1;
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + CHAT_LINE), false);
    }

    /** Removes every apparition from the world and frees the event lock. */
    private static void removeApparitions() {
        for (ServerPlayer apparition : apparitions) {
            apparition.discard();
        }
        apparitions.clear();
        removeAtTick = -1;
        EventQueue.release("day7_fake");
    }

    /**
     * Spawns one apparition 2 blocks in front of each real player, in the
     * player's own dimension, facing them, and plays the day-7 glitch at its
     * position. Also arms the 2-second chat line.
     */
    private static void spawnApparitions(MinecraftServer server) {
        hasRunDay7Event = true;
        // A dev re-trigger replaces any apparition still on screen.
        removeApparitions();
        ticksUntilChatLine = CHAT_LINE_DELAY_TICKS;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;   // never target the ghost itself
            }

            // Where the player is facing, 2 blocks out (horizontal only).
            Vec3 inFront = player.position()
                    .add(facing(player.getYRot()).scale(APPARITION_DISTANCE));

            GameProfile profile = new GameProfile(FakePlayerUtil.FAKE_UUID, FakePlayerUtil.FAKE_NAME);
            ServerLevel level = player.serverLevel();
            ServerPlayer apparition = new ServerPlayer(server, level, profile,
                    ClientInformation.createDefault());
            apparition.moveTo(inFront.x, inFront.y, inFront.z, player.getYRot() + 180.0F, 0.0F);
            // The player model turns with yHeadRot/yBodyRot, not yRot.
            apparition.yHeadRot = apparition.getYRot();
            apparition.yBodyRot = apparition.getYRot();
            apparition.setInvulnerable(true);
            apparition.setSilent(true);
            apparition.noPhysics = true;
            // Same dummy connection as the ghost: the player "ticks" (sends
            // health updates etc.) while in the world — every packet is
            // swallowed and never delivered.
            apparition.connection = new ServerGamePacketListenerImpl(server,
                    FakePlayerHandler.createDummyConnection(), apparition,
                    CommonListenerCookie.createInitial(profile, false));

            level.addFreshEntity(apparition);
            // Positional glitch — bound to the player entity itself so it always
            // follows them and can never be walked away from.
            level.playSound(null, player,
                    ModSounds.DAY7_FAKE, SoundSource.AMBIENT, 1.0F, 1.0F);

            apparitions.add(apparition);
        }
        removeAtTick = server.getTickCount() + APPARITION_DURATION_TICKS;
    }

    /** {@return the horizontal facing unit vector for a yaw in degrees, in
     *  vanilla's convention (yaw 0 = +Z, turning left rotates counterclock-
     *  wise when seen from above)} */
    private static Vec3 facing(float yRot) {
        double rad = Math.toRadians(yRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }
}
