package dev.noname;

import dev.noname.config.ModConfig;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Day-8 smile event: the moment day 8 starts (the day 7 → 8 transition while
 * the server is running — joining a world that is already on day 8 never
 * replays it), every real player's client is told to pop a window with an
 * empty title showing {@code ":)"} and to write a {@code .txt} file onto the
 * desktop whose content is "{@code ?em fo kniht uoy od tahw}".
 *
 * <p>Like the day-5 desktop event, it fires exactly once per session and
 * never replays when joining a world whose day 8 has already started.
 */
public final class Day8SmileHandler {

    /** The day observed on the previous server tick, so the event fires
     *  exactly on the day 7 → 8 transition while the server is running.
     *  {@link Long#MIN_VALUE} = no observation yet (the first tick only
     *  records the current day and never fires). */
    private static long lastSeenDay = Long.MIN_VALUE;

    /** Whether the day-8 smile event already fired this session — guards
     *  against a duplicate if the time is somehow rolled back. */
    private static boolean fired = false;

    private Day8SmileHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Fire exactly when day 8 starts (the day 7 → 8 transition while the
        // server is running). The first tick of a session only records the
        // current day, so joining a world that is already on day 8 never
        // replays the event.
        long day = DayCounter.currentDay(overworld);
        if (lastSeenDay == Long.MIN_VALUE) {
            lastSeenDay = day;
        } else if (lastSeenDay < ModConfig.scaledDay(8) && day >= ModConfig.scaledDay(8)
                && !fired && ModConfig.isEnabled("day8_smile")) {
            triggerNow(server);
        }
        lastSeenDay = day;
    }

    /**
     * Dev/test hook — tell every client to pop the smile window and write
     * the desktop file right now, regardless of the day. Dispatched by
     * {@code /noname event play day8_smile}. Marks the event as fired so the
     * natural day-8 trigger never repeats it this session.
     */
    public static void triggerNow(MinecraftServer server) {
        fired = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;   // never target the ghost itself
            }
            ServerPlayNetworking.send(player, NonameEventPayload.play("day8_smile"));
        }
    }

    /**
     * Dev/test hook — reset the session state so a later day-8 transition can
     * trigger the event again. Used by {@code /noname event stopall}; the
     * per-player client windows are closed by the stopall payload.
     */
    public static void stopAll() {
        fired = false;
        lastSeenDay = Long.MIN_VALUE;
    }
}
