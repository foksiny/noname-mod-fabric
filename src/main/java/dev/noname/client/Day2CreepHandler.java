package dev.noname.client;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;

/**
 * Day-2 "why don't you like it? :(" creep.
 *
 * <p>Once the player has been on day 2 for three minutes of game time
 * (3600 ticks), a centered message appears on their screen for 10 seconds,
 * read out by {@link Day2CreepOverlay}. Then, exactly 10 seconds after the
 * message popped up, the audio of music disc 11 (the unsettling static
 * record) starts playing for them.
 *
 * <p>This is per server-session, gated purely on the client clock matching
 * day 2 — so all stages fire once and don't replay for as long as the player
 * stays in the same world/connection.
 */
public final class Day2CreepHandler {

    /** 3 minutes in ticks (20 tps). After this many ticks in day 2 the
     *  message appears. */
    private static final int MESSAGE_DELAY_TICKS = 20 * 60 * 3;

    /** How long the on-screen message stays up, in ticks. */
    private static final int MESSAGE_DURATION_TICKS = 20 * 10;

    /** Disc 11 starts 10 seconds after the message first appears. */
    private static final int DISC_DELAY_TICKS = 20 * 10;

    /** Server-tick counter accumulated while the client sees day 2. */
    private static int ticksInDay2 = 0;

    /** First client tick (relative to the 3-min mark) at which the message
     *  is up; -1 means the message hasn't been shown yet this session. */
    private static int messageUpFromTick = -1;

    /** Last "session" tick when the message was active; used to gate the
     *  overlay window. {@code messageUpUntil} is exclusive. */
    private static int messageUpUntil = -1;

    /** True once disc 11 has played this session (don't replay it twice). */
    private static boolean discPlayed = false;

    /** The client tick on which disc 11 should start. */
    private static int discStartAtTick = -1;

    private static int sessionTick = 0;

    private Day2CreepHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        sessionTick++;

        ClientLevel level = mc.level;

        // Reset all state when not in a world (covers leaving/resuming a save).
        if (level == null || mc.player == null) {
            resetSession();
            return;
        }

        // Only count time spent on day 2.
        if (!isDay2(level)) {
            ticksInDay2 = 0;          // restart counting if we leave day 2
            return;
        }
        if (!ModConfig.isEnabled("day2_message")) {
            ticksInDay2 = 0;
            return;
        }
        ticksInDay2++;

        // 1) After 3 minutes in day 2, show the message for 10 seconds.
        //    Use >= (not ==) so a skipped/laggy tick can't make us miss the arm.
        if (messageUpFromTick < 0 && ticksInDay2 >= MESSAGE_DELAY_TICKS) {
            messageUpFromTick = sessionTick;
            messageUpUntil = sessionTick + MESSAGE_DURATION_TICKS;
            // Schedule the disc 10 s later than the message appears.
            if (ModConfig.isEnabled("disc_11")) {
                discStartAtTick = sessionTick + DISC_DELAY_TICKS;
            }
        }

        if (messageUpFromTick >= 0 && sessionTick >= messageUpUntil) {
            // Closing the overlay — let the player keep playing.
            messageUpUntil = -1;
        }

        // 2) Start disc 11 exactly 10 s after the message first appeared.
        if (!discPlayed && discStartAtTick >= 0 && sessionTick >= discStartAtTick) {
            discPlayed = true;
            playDisc11(mc);
        }
    }

    /** {@return whether the {@link ClientLevel} is currently on day 2}. */
    private static boolean isDay2(ClientLevel level) {
        return DayCounter.currentDay(level) == ModConfig.scaledDay(2);
    }

    private static void playDisc11(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        // Vanilla music-disc-11 sound, bound to the player entity so it
        // reads identically in single- and multiplayer and follows the
        // player (it can never be walked away from). Pitch 1.0, no
        // randomization; volume is generous so the creepy static record
        // dominates the soundscape.
        PlayerSound.play(mc, SoundEvents.MUSIC_DISC_11.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 4.0F, 1.0F);
    }

    /** Wipes all per-session state — called when leaving a world/saving. */
    private static void resetSession() {
        ticksInDay2 = 0;
        messageUpFromTick = -1;
        messageUpUntil = -1;
        discPlayed = false;
        discStartAtTick = -1;
    }

    /** {@return whether the on-screen "why don't you like it? :(" overlay
     *  should currently be drawn by the GUI renderer}. */
    public static boolean isMessageVisible() {
        return messageUpUntil > 0 && Day2CreepHandler.sessionTick < messageUpUntil;
    }

    /**
     * Dev/test hook — pop the "why don't you like it? :(" overlay immediately
     * (for the configured 10-second duration) and schedule disc 11 to start
     * 10 seconds after. Days don't matter here; this is purely manual.
     */
    public static void showMessageNow() {
        messageUpFromTick = sessionTick;
        messageUpUntil = sessionTick + MESSAGE_DURATION_TICKS;
        discStartAtTick = sessionTick + DISC_DELAY_TICKS;
    }

    /**
     * Dev/test hook — start playing music disc 11 right now, regardless of
     * the day-2 message timer.
     */
    public static void playDisc11Now() {
        playDisc11(Minecraft.getInstance());
    }

    /**
     * Dev/test hook — cancel any armed message/disc state for this session
     * and stop the music disc 11 audio if it's currently playing. Idempotent.
     */
    public static void stopAll() {
        messageUpFromTick = -1;
        messageUpUntil = -1;
        discStartAtTick = -1;
        discPlayed = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Disc 11 is a vanilla sound event — stop it by location, just
            // like the vanilla jukebox uses to silence a playing record.
            mc.getSoundManager().stop(
                    net.minecraft.sounds.SoundEvents.MUSIC_DISC_11.value().getLocation(),
                    null);
        }
    }
}
