package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * On day 3, when the sun reaches the middle of the day (tick-of-day 6000 =
 * noon, 12:00), the world time suddenly jumps forward to the start of night
 * (tick-of-day 13000 = 19:00, when monsters can spawn and the sky darkens).
 *
 * <p>The jump fires exactly once per session: the tick-of-day is watched while
 * day 3 is running, and the skip happens the first tick the threshold is
 * crossed. Joining a world whose day-3 noon has already passed never replays
 * the skip — the previous tick-of-day is recorded on the first observed
 * day-3 tick, so a join mid-afternoon just continues normally.
 *
 * <p>After the skip the day counter is unchanged (it is derived from the
 * total {@code dayTime / 24000}, and the skip keeps the day boundary aligned:
 * 6000 -> 13000 lands inside the same day). The player experiences the rest
 * of "day 3" as night.
 */
public final class Day3TimeSkipHandler {

    /** Tick-of-day at noon (12:00) — the trigger threshold. */
    private static final long NOON_TICK_OF_DAY = 6000L;

    /** Tick-of-day at the start of night (19:00) — where the time jumps to. */
    private static final long NIGHT_TICK_OF_DAY = 13000L;

    /** Ticks per Minecraft day. */
    private static final long TICKS_PER_DAY = 24000L;

    /** The tick-of-day observed on the previous server tick while day 3 is
     *  running, so the skip fires exactly when noon is crossed.
     *  {@link Long#MIN_VALUE} = no observation yet (the first day-3 tick of a
     *  session only records the current time, so joining mid-day-3 never
     *  replays the skip). */
    private static long lastSeenTickOfDay = Long.MIN_VALUE;

    /** Whether the skip already happened this session — guards against firing
     *  more than once if the time is somehow rolled back. */
    private static boolean done = false;

    private Day3TimeSkipHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (done || !ModConfig.isEnabled("day3_timeskip")) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        long day = DayCounter.currentDay(overworld);
        if (day != ModConfig.scaledDay(3)) {
            // Reset the observer whenever we are not on day 3 so the first
            // day-3 tick of a later session records the time before testing
            // the threshold.
            lastSeenTickOfDay = Long.MIN_VALUE;
            return;
        }

        long tickOfDay = overworld.getDayTime() % TICKS_PER_DAY;
        if (lastSeenTickOfDay == Long.MIN_VALUE) {
            lastSeenTickOfDay = tickOfDay;
            return;
        }

        if (lastSeenTickOfDay < NOON_TICK_OF_DAY && tickOfDay >= NOON_TICK_OF_DAY) {
            skipToNight(overworld);
            done = true;
        }
        lastSeenTickOfDay = tickOfDay;
    }

    /** Jumps the overworld time forward to the start of night, keeping the
     *  day number unchanged (adds whole-day multiples of 24000 as needed). */
    private static void skipToNight(ServerLevel overworld) {
        long dayTime = overworld.getDayTime();
        long dayBase = dayTime - (dayTime % TICKS_PER_DAY);
        long newTime = dayBase + NIGHT_TICK_OF_DAY;
        overworld.setDayTime(newTime);
        lastSeenTickOfDay = NIGHT_TICK_OF_DAY;
    }

    /** Dev/test hook — fire the skip right now, regardless of the day.
     *  Dispatched by {@code /noname event play day3_timeskip}. */
    public static void triggerNow(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            skipToNight(overworld);
            done = true;
        }
    }

    /** Cancels the armed skip and frees the once-per-session guard. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        lastSeenTickOfDay = Long.MIN_VALUE;
        done = false;
    }
}
