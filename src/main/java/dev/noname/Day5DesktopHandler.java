package dev.noname;

import dev.noname.config.ModConfig;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Day-5 desktop event: when the sun reaches the middle of day 5 (tick-of-day
 * 6000 = noon), the fake player sends one line in chat — "look at your
 * desktop" — and every real player's client is told to arm
 * {@link dev.noname.client.Day5DesktopClient}. The next time that player
 * actually looks at their desktop (the game window loses focus), the client
 * writes {@code hello.txt} onto the desktop, its content base64-encoded.
 *
 * <p>Like the noon triggers of the day-2 {@code null} visitor, the day-3
 * time-skip and the day-7 lonely line, it fires exactly once per session and
 * never replays when joining a world whose day-5 noon has already passed.
 */
public final class Day5DesktopHandler {

    /** Tick-of-day at noon (12:00) — when the desktop line fires. */
    private static final long NOON_TICK_OF_DAY = 6000L;

    /** Ticks per Minecraft day. */
    private static final long TICKS_PER_DAY = 24000L;

    /** The line the fake player sends in chat at day-5 noon. */
    private static final String CHAT_LINE = "look at your desktop";

    /** The tick-of-day observed on the previous server tick while day 5 is
     *  running, so the line fires exactly when noon is crossed.
     *  {@link Long#MIN_VALUE} = no observation yet (the first day-5 tick of a
     *  session only records the current time, so joining mid-day-5 after noon
     *  never replays the event). */
    private static long lastSeenNoonTickOfDay = Long.MIN_VALUE;

    /** Whether the day-5 noon event already fired this session — guards
     *  against a duplicate if the time is somehow rolled back. */
    private static boolean fired = false;

    private Day5DesktopHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Day-5 noon: the fake player tells everyone to look at their
        // desktop, exactly once per session. Joining a world whose day-5
        // noon has already passed never replays it (the previous tick-of-day
        // is recorded on the first observed day-5 tick).
        long day = DayCounter.currentDay(overworld);
        long day5 = ModConfig.scaledDay(5);
        if (!fired && ModConfig.isEnabled("day5_desktop") && day == day5) {
            long tickOfDay = overworld.getDayTime() % TICKS_PER_DAY;
            if (lastSeenNoonTickOfDay == Long.MIN_VALUE) {
                lastSeenNoonTickOfDay = tickOfDay;
            } else if (lastSeenNoonTickOfDay < NOON_TICK_OF_DAY
                    && tickOfDay >= NOON_TICK_OF_DAY) {
                fired = true;
                triggerNow(server);
            }
            lastSeenNoonTickOfDay = tickOfDay;
        } else {
            // Reset the observer whenever we are not on day 5 so the first
            // day-5 tick of a later session records the time before testing
            // the threshold.
            lastSeenNoonTickOfDay = Long.MIN_VALUE;
        }
    }

    /**
     * Dev/test hook — send the day-5 desktop line and arm every client right
     * now, regardless of the day. Dispatched by {@code /noname event play
     * day5_desktop}. Marks the event as fired so the natural day-5 trigger
     * never repeats it this session.
     */
    public static void triggerNow(MinecraftServer server) {
        fired = true;
        lastSeenNoonTickOfDay = Long.MIN_VALUE;

        // The fake player's chat line, broadcast like the other fake-player
        // lines (via PlayerList so it actually reaches the players).
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + CHAT_LINE), false);

        // Arm the desktop watcher on every real player's client; the file is
        // written the next time the game window loses focus.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;   // never target the ghost itself
            }
            ServerPlayNetworking.send(player, NonameEventPayload.play("day5_desktop"));
        }
    }

    /**
     * Dev/test hook — reset the session state so a later day-5 noon can
     * trigger the event again. Used by {@code /noname event stopall}; the
     * per-player client watchers are disarmed by the stopall payload.
     */
    public static void stopAll() {
        fired = false;
        lastSeenNoonTickOfDay = Long.MIN_VALUE;
    }
}
