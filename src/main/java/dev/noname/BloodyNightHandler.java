package dev.noname;

import dev.noname.config.ModConfig;
import dev.noname.network.ModPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * From day 15 onward, every night has a 20% chance to become a <b>Bloody
 * Night</b>: a night during which the random events that show the
 * flesh-skinned fake player ({@link Day3StalkerHandler}, {@link
 * Day5PigHandler}, {@link Day10LookHandler}, {@link Day13StalkerHandler})
 * fire 1.3&times; more often.
 *
 * <p>The decision is rolled exactly once per night (per calendar day's
 * night) when the night window opens, then held until dawn; it re-rolls if
 * the world time is manipulated into a new night. The whole mechanic is
 * resolved against the overworld's day cycle, so a Bloody Night is a single
 * global state — never per-dimension, never per-player.
 *
 * <p>The decided state is broadcast to every client ({@link
 * dev.noname.network.BloodyNightPayload}) so the night's fog can be tinted
 * dark red ({@link dev.noname.client.BloodyNightClient}).
 *
 * <p>This handler must be ticked <i>before</i> the dependent event handlers
 * so its flag is current by the time they read it on the same tick (see the
 * registration order in {@link Noname}).
 */
public final class BloodyNightHandler {

    /** Ticks per Minecraft day. */
    private static final long TICKS_PER_DAY = 24000L;

    /** Tick-of-day at the start of night (19:00) — when monsters spawn and
     *  the sky darkens, matching {@link Day3TimeSkipHandler}. */
    private static final long NIGHT_START_TICK = 13000L;

    /** Probability that a night becomes a Bloody Night — 20%. */
    private static final float BLOODY_CHANCE = 0.20F;

    /** How much more often the fake-player events fire on a Bloody Night. */
    private static final float BLOODY_BOOST = 1.3F;

    private static final Logger LOGGER = LoggerFactory.getLogger("Noname/BloodyNight");

    /** The calendar day whose night the Bloody-Night flag was decided for;
     *  {@link Long#MIN_VALUE} when no night has been decided yet (or after a
     *  dawn reset), so the next night re-rolls. */
    private static long decidedNightDay = Long.MIN_VALUE;

    /** Whether the currently-decided night is bloody. */
    private static boolean bloody;

    /** Whether clients currently believe this night is bloody — the last
     *  value broadcast. */
    private static boolean lastBroadcast;

    private BloodyNightHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);
        long tickOfDay = overworld.getDayTime() % TICKS_PER_DAY;

        if (tickOfDay >= NIGHT_START_TICK) {
            // Night window for this calendar day. Roll exactly once per day:
            // a fresh night (or a /time jump into a new one) re-rolls.
            if (day >= ModConfig.scaledDay(15) && decidedNightDay != day) {
                bloody = overworld.getRandom().nextFloat() < BLOODY_CHANCE;
                decidedNightDay = day;
                if (bloody) {
                    LOGGER.info("Bloody night rises on day {}.", day);
                }
            }
            // Before day 15 no night is ever bloody, even if it somehow got
            // decided while a /time jump passed through an eligible day.
            if (day < ModConfig.scaledDay(15)) {
                bloody = false;
            }
        } else {
            // Daytime: clear the decision so the next night rolls fresh.
            bloody = false;
            decidedNightDay = Long.MIN_VALUE;
        }

        // Let the clients know whenever the state flips: a night that begins
        // bloody (or a /time jump that changes the decision) or a dawn that
        // clears it.
        if (bloody != lastBroadcast) {
            ModPayloads.sendBloodyNight(server, bloody);
            lastBroadcast = bloody;
        }
    }

    /**
     * {@return whether the night the given level is currently in is a Bloody
     * Night}. Resolves against the overworld's day cycle, so the answer is
     * the same in every dimension for any given tick.
     */
    public static boolean isBloodyNight(ServerLevel level) {
        if (level == null || !bloody) {
            return false;
        }
        ServerLevel overworld = level.getServer().overworld();
        if (overworld == null) {
            return false;
        }
        long tickOfDay = overworld.getDayTime() % TICKS_PER_DAY;
        if (tickOfDay < NIGHT_START_TICK) {
            return false;
        }
        long day = DayCounter.currentDay(overworld);
        return day == decidedNightDay && day >= ModConfig.scaledDay(15);
    }

    /** {@return whether the currently-decided night is bloody — the flag
     *  that is broadcast to clients}. */
    public static boolean currentBloody() {
        return bloody;
    }

    /**
     * {@return the given event's base chance scaled by the Bloody-Night
     * multiplier — {@code base} on a normal night, {@code base * 1.3} on a
     * Bloody Night}. Pair with {@link ModConfig#chance(String, float)} so
     * the user's per-event multiplier still applies on top.
     */
    public static float boost(float baseChance, ServerLevel level) {
        return baseChance * (isBloodyNight(level) ? BLOODY_BOOST : 1.0F);
    }
}
