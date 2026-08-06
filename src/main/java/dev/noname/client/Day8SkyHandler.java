package dev.noname.client;

import dev.noname.DayCounter;
import dev.noname.ModSounds;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;

/**
 * Day-8+ red-fog event. Every 1-3 minutes spent on day 8 or later, there is
 * a 15% chance that, for 10 seconds, the fog turns reddish, the render
 * distance drops to the minimum and a horror ambience plays for the whole
 * event: 1.5 s fade-in, a fade-out that starts 1.5 s before the end (another
 * 1.5 s), then everything returns to normal.
 *
 * <p>Entirely client-side (sky colour and render distance are per-client),
 * gated on the client clock matching day 8 ({@link DayCounter}), exactly
 * like the day-5 flash handler.
 */
public final class Day8SkyHandler {

    /** Roll cadence: 1-3 minutes (1200-3600 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 3;

    /** Probability that a roll actually triggers the event. */
    private static final float EVENT_CHANCE = 0.15F;

    /** How long the whole event lasts (10 seconds). */
    private static final int EVENT_DURATION_TICKS = 20 * 10;

    /** Sound fade-in length (1.5 seconds). */
    private static final int FADE_IN_TICKS = 30;

    /** Sound fade-out length (1.5 seconds). */
    private static final int FADE_OUT_TICKS = 30;

    /** Sound fade-out starts 1.5 s before the event ends, so the ambience
     *  plays for the whole effect. */
    private static final int FADE_OUT_START_TICK = EVENT_DURATION_TICKS - FADE_OUT_TICKS;

    /** Smallest render distance the options allow. */
    private static final int MIN_RENDER_DISTANCE = 2;

    /** The rapidly-changing message: "why does it taste good? it's just an
     *  eye." plus the phrases it keeps flipping through. */
    private static final String[] PHRASES = {
            "why does it taste good? it's just an eye.",
            "it hurts but it feels good",
            "i can't feel my face",
            "my hands are all red",
            "DON'T LOOK AT ME!",
    };

    /** How long the message stays on screen (3 seconds). */
    private static final int TEXT_DURATION_TICKS = 20 * 3;

    /** How fast the message flips to a random phrase (every 2 ticks = 0.1 s). */
    private static final int TEXT_CHANGE_INTERVAL_TICKS = 2;

    /** The message appears at a random moment 3-7 s into the event (it lasts
     *  3 s and must finish before the event ends). */
    private static final int TEXT_EARLIEST_TICK = 20 * 3;
    private static final int TEXT_LATEST_TICK = 20 * 7;

    /** Never start the fade at exactly 0: the sound engine skips instances
     *  whose calculated volume is zero at play time. */
    private static final float FADE_START_VOLUME = 0.01F;

    /** Pitch of the ambience: really low, so the drone sounds deep. */
    private static final float HORROR_PITCH = 0.35F;

    /** Ticks until the next roll; reset whenever the player is not on day 8+,
     *  so the first attempt happens 1-3 minutes into day 8. */
    private static int ticksUntilNextRoll = MIN_ROLL_TICKS;

    /** Ticks left in the active event; {@code -1} = no event running. */
    private static int eventTicksLeft = -1;

    /** Ticks since the event started, driving the sound fade curve. */
    private static int soundTick = 0;

    /** Render distance to restore when the event ends. */
    private static int previousRenderDistance = -1;

    /** The ambience currently being played, if any. */
    private static FadingSoundInstance soundInstance;

    /** Event tick at which the message appears; {@code -1} = not scheduled. */
    private static int textAppearAtTick = -1;

    /** Ticks the message still has on screen; {@code -1} = not visible. */
    private static int textTicksLeft = -1;

    /** Ticks until the message flips to another random phrase. */
    private static int textChangeTicksLeft = 0;

    /** The phrase currently shown. */
    private static String currentPhrase = PHRASES[0];

    private Day8SkyHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            // Leaving the world mid-event: put everything back.
            if (eventTicksLeft >= 0) {
                endEvent(mc);
            }
            ticksUntilNextRoll = MIN_ROLL_TICKS;
            return;
        }

        if (eventTicksLeft >= 0) {
            tickEvent(mc);
            return;
        }

        // Day 8+ only; before that keep the countdown fresh so the first
        // attempt is exactly 1-3 minutes into day 8.
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(8)
                || !ModConfig.isEnabled("day8_sky")) {
            ticksUntilNextRoll = MIN_ROLL_TICKS;
            return;
        }

        if (--ticksUntilNextRoll > 0) {
            return;
        }
        ticksUntilNextRoll = MIN_ROLL_TICKS
                + level.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1);

        if (level.getRandom().nextFloat() >= ModConfig.chance("day8_sky", EVENT_CHANCE)) {
            return;
        }
        startEvent(mc);
    }

    /** {@return whether the sky should currently be reddish}. */
    public static boolean isRedSkyActive() {
        return eventTicksLeft >= 0;
    }

    /** {@return whether the white message should currently be on screen}. */
    public static boolean isTextVisible() {
        return textTicksLeft >= 0;
    }

    /** {@return the phrase to draw right now}. */
    public static String getCurrentPhrase() {
        return currentPhrase;
    }

    /** {@return whether the "why does it taste good?" line should be drawn:
     *  from the moment the ambience has fully stopped until the event
     *  ends}. */
    public static boolean isMessageVisible() {
        return eventTicksLeft >= 0 && soundTick >= FADE_OUT_START_TICK + FADE_OUT_TICKS;
    }

    /**
     * Dev/test hook — start the event right now, regardless of the day-8
     * gate and the roll timer. Dispatched by {@code /noname event play
     * day8_sky}.
     */
    public static void triggerNow() {
        startEvent(Minecraft.getInstance());
    }

    /**
     * Dev/test hook — cancel an active event and restore everything
     * immediately. Used by {@code /noname event stopall}.
     */
    public static void stopAll() {
        if (eventTicksLeft >= 0) {
            endEvent(Minecraft.getInstance());
        }
        ticksUntilNextRoll = MIN_ROLL_TICKS;
    }

    private static void startEvent(Minecraft mc) {
        if (eventTicksLeft >= 0) {
            return;
        }
        eventTicksLeft = EVENT_DURATION_TICKS;
        soundTick = 0;

        previousRenderDistance = mc.options.renderDistance().get();
        mc.options.renderDistance().set(MIN_RENDER_DISTANCE);

        soundInstance = new FadingSoundInstance(ModSounds.HORROR_AMBIENCE, SoundSource.MASTER, HORROR_PITCH);
        // Start just above zero: SoundEngine.play() skips sounds with
        // volume exactly 0, so the fade-in must begin from a tiny value.
        soundInstance.setFadeVolume(0.01F);
        mc.getSoundManager().play(soundInstance);

        // Schedule the message at a random moment during the event.
        textAppearAtTick = TEXT_EARLIEST_TICK
                + mc.level.getRandom().nextInt(TEXT_LATEST_TICK - TEXT_EARLIEST_TICK + 1);
        textTicksLeft = -1;
    }

    private static void tickEvent(Minecraft mc) {
        eventTicksLeft--;
        soundTick++;
        updateFade(mc);
        tickText(mc);
        if (eventTicksLeft <= 0) {
            endEvent(mc);
        }
    }

    /** Shows the message for 3 seconds at its scheduled moment, flipping
     *  between random phrases every few ticks while it is visible. */
    private static void tickText(Minecraft mc) {
        if (textAppearAtTick >= 0 && soundTick >= textAppearAtTick) {
            textAppearAtTick = -1;
            textTicksLeft = TEXT_DURATION_TICKS;
            currentPhrase = PHRASES[mc.level.getRandom().nextInt(PHRASES.length)];
            textChangeTicksLeft = TEXT_CHANGE_INTERVAL_TICKS;
        }
        if (textTicksLeft < 0) {
            return;
        }
        textTicksLeft--;
        if (--textChangeTicksLeft <= 0) {
            currentPhrase = PHRASES[mc.level.getRandom().nextInt(PHRASES.length)];
            textChangeTicksLeft = TEXT_CHANGE_INTERVAL_TICKS;
        }
    }

    /** Fade in (1.5 s), hold, fade out from 5 s (1.5 s), then stop hard. */
    private static void updateFade(Minecraft mc) {
        if (soundInstance == null) {
            return;
        }
        float volume;
        if (soundTick < FADE_IN_TICKS) {
            volume = soundTick / (float) FADE_IN_TICKS;
        } else if (soundTick < FADE_OUT_START_TICK) {
            volume = 1.0F;
        } else if (soundTick < FADE_OUT_START_TICK + FADE_OUT_TICKS) {
            volume = 1.0F - (soundTick - FADE_OUT_START_TICK) / (float) FADE_OUT_TICKS;
        } else {
            // Fade-out finished: stop the sound completely.
            mc.getSoundManager().stop(soundInstance);
            soundInstance = null;
            return;
        }
        soundInstance.setFadeVolume(volume);
    }

    /** Restores the render distance and stops the ambience. */
    private static void endEvent(Minecraft mc) {
        eventTicksLeft = -1;
        soundTick = 0;
        textAppearAtTick = -1;
        textTicksLeft = -1;
        if (previousRenderDistance >= 0) {
            mc.options.renderDistance().set(previousRenderDistance);
            previousRenderDistance = -1;
        }
        if (soundInstance != null) {
            mc.getSoundManager().stop(soundInstance);
            soundInstance = null;
        }
    }
}
