package dev.noname.client;

import dev.noname.DayCounter;
import dev.noname.ModSounds;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;

/**
 * Day-5+ screen flash. Every minute spent on day 5 or later, there is a 25%
 * chance that a black screen — with "i can't stop doing it" on it — covers
 * the player's view for half a second ({@link Day5FlashOverlay} draws it).
 * While the flash is up the screen is entirely black, so the player is blind
 * to the world for those 10 ticks.
 *
 * <p>This is entirely client-side, gated on the client clock matching day 5
 * ({@link DayCounter}), exactly like the creepy-bass stinger: the counter
 * only advances while the player is in a world on day 5+, and the attempt
 * cycle repeats every minute.
 */
public final class Day5FlashHandler {

    /** One full cycle in ticks (20 tps → 1200 ticks = 1 minute). */
    private static final int FLASH_INTERVAL_TICKS = 20 * 60;

    /** How long the flash stays on screen, in ticks (0.5 seconds). */
    private static final int FLASH_DURATION_TICKS = 20 / 2;

    /** Probability that a cycle actually flashes. */
    private static final float FLASH_CHANCE = 0.25F;

    /** Ticks until the next flash attempt; reset whenever the player is not
     *  on day 5+, so the first attempt happens 1 minute into day 5. */
    private static int ticksUntilNextAttempt = FLASH_INTERVAL_TICKS;

    /** Session tick until which the flash is on screen; {@code -1} = no
     *  flash. Compared against the session counter, like
     *  {@link Day2CreepHandler}. */
    private static int flashUntilSessionTick = -1;

    private static int sessionTick = 0;

    private Day5FlashHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        sessionTick++;

        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            resetSession();
            return;
        }

        // Day 5+ only; before that (or while out of the world) keep the
        // countdown fresh so the first attempt is exactly 1 minute into day 5.
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(5)
                || !ModConfig.isEnabled("day5_flash")) {
            ticksUntilNextAttempt = FLASH_INTERVAL_TICKS;
            return;
        }

        if (--ticksUntilNextAttempt > 0) {
            return;
        }
        ticksUntilNextAttempt = FLASH_INTERVAL_TICKS;

        if (level.getRandom().nextFloat() >= ModConfig.chance("day5_flash", FLASH_CHANCE)) {
            return;
        }
        startFlash(mc);
    }

    /** {@return whether the "i can't stop doing it" flash should currently be
     *  drawn by the HUD renderer}. */
    public static boolean isFlashVisible() {
        return sessionTick < flashUntilSessionTick;
    }

    /**
     * Dev/test hook — show the flash right now, regardless of the day-5 gate
     * and the minute timer. Dispatched by {@code /noname event play
     * day5_flash}.
     */
    public static void triggerFlashNow() {
        startFlash(Minecraft.getInstance());
    }

    /**
     * Arms the flash and plays the glitch. The sound is always played with
     * volume 1 and pitch 1 — a fixed pitch/speed, no variation.
     */
    private static void startFlash(Minecraft mc) {
        flashUntilSessionTick = sessionTick + FLASH_DURATION_TICKS;
        if (mc.player != null) {
            // Bound to the player entity itself so the glitch always follows
            // the player and can never be walked away from.
            PlayerSound.play(mc, ModSounds.DAY5_FLASH,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * Dev/test hook — hide any flash immediately and restart the attempt
     * timer. Used by {@code /noname event stopall}.
     */
    public static void stopAll() {
        flashUntilSessionTick = -1;
        ticksUntilNextAttempt = FLASH_INTERVAL_TICKS;
    }

    /** Wipes all per-session state — called when leaving a world/saving. */
    private static void resetSession() {
        ticksUntilNextAttempt = FLASH_INTERVAL_TICKS;
        flashUntilSessionTick = -1;
    }
}
