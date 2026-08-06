package dev.noname.client;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Day-10+ screen whispers. Every 4 to 8 minutes spent on day 10 or later,
 * there is a 15% chance that one of the ghost's texts appears on the
 * player's screen in white for 1 second ({@link Day10WhisperOverlay} draws
 * it).
 *
 * <p>This is entirely client-side, gated on the client clock matching day 10
 * ({@link DayCounter}), exactly like {@link Day5FlashHandler}: the counter
 * only advances while the player is in a world on day 10+, and each attempt
 * cycle lasts a random 4 to 8 minutes.
 */
public final class Day10WhisperHandler {

    /** One cycle's range in ticks (20 tps → 4800-9600 ticks = 4-8 minutes). */
    private static final int INTERVAL_MIN_TICKS = 20 * 60 * 4;
    private static final int INTERVAL_MAX_TICKS = 20 * 60 * 8;

    /** How long the text stays on screen, in ticks (1 second). */
    private static final int DURATION_TICKS = 20;

    /** Probability that a cycle actually shows a text. */
    private static final float WHISPER_CHANCE = 0.15F;

    /** The texts the ghost whispers — one is picked at random per show. */
    private static final String[] PHRASES = {
            ".. / .-.. .. -.- . / .-- .... . -. / .. - / -... .-.. . . -.. ...",
            "thgir leef t'nseod ti",
            "why did you let this happen to me?",
            "eW91IGhhdGUgbWUsIGRvbid0IHlvdT8=",
    };

    /** Ticks until the next attempt; reset whenever the player is not on day
     *  10+, so the first attempt happens 4-8 minutes into day 10. */
    private static int ticksUntilNextAttempt = INTERVAL_MIN_TICKS;

    /** Session tick until which the text is on screen; {@code -1} = no text.
     *  Compared against the session counter, like {@link Day2CreepHandler}. */
    private static int textUntilSessionTick = -1;

    /** The text currently on screen (empty while none is showing). */
    private static String currentText = "";

    private static int sessionTick = 0;

    private Day10WhisperHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        sessionTick++;

        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            resetSession();
            return;
        }

        // Day 10+ only; before that (or while out of the world) keep the
        // countdown fresh so the first attempt is 4-8 minutes into day 10.
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(10)
                || !ModConfig.isEnabled("day10_whisper")) {
            ticksUntilNextAttempt = INTERVAL_MIN_TICKS;
            return;
        }

        if (--ticksUntilNextAttempt > 0) {
            return;
        }
        ticksUntilNextAttempt = INTERVAL_MIN_TICKS + level.getRandom()
                .nextInt(INTERVAL_MAX_TICKS - INTERVAL_MIN_TICKS + 1);

        if (level.getRandom().nextFloat() >= ModConfig.chance("day10_whisper", WHISPER_CHANCE)) {
            return;
        }
        showText(level);
    }

    /** {@return whether a whispered text should currently be drawn by the
     *  HUD renderer} */
    public static boolean isTextVisible() {
        return sessionTick < textUntilSessionTick;
    }

    /** {@return the text currently on screen, or "" while none is showing} */
    public static String currentText() {
        return currentText;
    }

    /**
     * Dev/test hook — show a random text right now, regardless of the day-10
     * gate and the minute timer. Dispatched by {@code /noname event play
     * day10_whisper}.
     */
    public static void triggerNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        showText(mc.level);
    }

    /** Arms a random text on the player's screen for 1 second. */
    private static void showText(ClientLevel level) {
        currentText = PHRASES[level.getRandom().nextInt(PHRASES.length)];
        textUntilSessionTick = sessionTick + DURATION_TICKS;
    }

    /**
     * Dev/test hook — hide any text immediately and restart the attempt
     * timer. Used by {@code /noname event stopall}.
     */
    public static void stopAll() {
        textUntilSessionTick = -1;
        currentText = "";
        ticksUntilNextAttempt = INTERVAL_MIN_TICKS;
    }

    /** Wipes all per-session state — called when leaving a world/saving. */
    private static void resetSession() {
        ticksUntilNextAttempt = INTERVAL_MIN_TICKS;
        textUntilSessionTick = -1;
        currentText = "";
    }
}
