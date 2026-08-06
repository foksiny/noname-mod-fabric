package dev.noname.client;

import dev.noname.DayCounter;
import dev.noname.ModSounds;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;

import static dev.noname.client.PlayerSound.play;

/**
 * Day-6 middle-of-day event. When the client's day reaches day 6 and the
 * tick-of-day crosses noon (tick 6000 = 12:00), a fullscreen TV-static overlay
 * appears with a looping static sound. Then a sequence of red text lines
 * appears one by one:
 *
 * <ul>
 *   <li>T+0s: overlay + static sound starts</li>
 *   <li>T+2s: "why didn't you believe me?" (+ blip sound)</li>
 *   <li>T+5s: "i thought you were my friend" (+ blip sound)</li>
 *   <li>T+8s: "i hate you just like you hate me now" (+ blip sound)</li>
 *   <li>T+11s: "it's all your fault." (+ blip sound)</li>
 *   <li>T+15s: everything stops, overlay and sounds end</li>
 * </ul>
 *
 * <p>Fires once per session. If the player joins mid-day-6 after noon, the
 * event does not replay (the tick-of-day is recorded on the first observed
 * day-6 tick, like {@link Day3TimeSkipHandler}).
 */
public final class Day6Handler {

    /** Tick-of-day at noon (12:00) — the trigger threshold. */
    private static final long NOON_TICK_OF_DAY = 6000L;

    /** Ticks per Minecraft day. */
    private static final long TICKS_PER_DAY = 24000L;

    /** Total duration of the event in ticks (15 seconds). */
    private static final int EVENT_DURATION_TICKS = 20 * 15;

    /** When each line appears (relative to event start, in ticks). */
    private static final int LINE1_TICK = 20 * 2;   // 2s
    private static final int LINE2_TICK = 20 * 5;   // 5s
    private static final int LINE3_TICK = 20 * 8;   // 8s
    private static final int LINE4_TICK = 20 * 11;  // 11s

    /** The text lines in order. */
    private static final String[] LINES = {
            "why didn't you believe me?",
            "i thought you were my friend",
            "i hate you just like you hate me now",
            "it's all your fault."
    };

    /** The tick-of-day observed on the previous client tick while day 6 is
     *  running, so the event fires exactly when noon is crossed.
     *  {@link Long#MIN_VALUE} = no observation yet. */
    private static long lastSeenTickOfDay = Long.MIN_VALUE;

    /** Whether the event already happened this session. */
    private static boolean done = false;

    /** Ticks since the event started; -1 = not running. */
    private static int eventTick = -1;

    /** Index of the next line to show (0..4). */
    private static int nextLineIndex = 0;

    /** The currently visible lines (up to nextLineIndex). */
    private static String[] visibleLines = new String[0];

    /** The looping static sound instance. */
    private static Day6StaticSoundInstance staticSoundInstance;

    /** Saved render distance before the event started; -1 = not saved. */
    private static int savedRenderDistance = -1;

    private Day6Handler() {
    }

    public static void onClientTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            // Left the world mid-event: clean up.
            if (eventTick >= 0) {
                stopEvent(mc);
            }
            lastSeenTickOfDay = Long.MIN_VALUE;
            done = false;
            return;
        }

        // If event is running, tick it.
        if (eventTick >= 0) {
            tickEvent(mc);
            return;
        }

        // Only check for trigger on day 6.
        if (done || DayCounter.currentDay(level) != ModConfig.scaledDay(6)
                || !ModConfig.isEnabled("day6_static")) {
            if (DayCounter.currentDay(level) != ModConfig.scaledDay(6)) {
                lastSeenTickOfDay = Long.MIN_VALUE;
                done = false;
            }
            return;
        }

        long tickOfDay = level.getDayTime() % TICKS_PER_DAY;
        if (lastSeenTickOfDay == Long.MIN_VALUE) {
            lastSeenTickOfDay = tickOfDay;
            return;
        }

        // Crossed noon?
        if (lastSeenTickOfDay < NOON_TICK_OF_DAY && tickOfDay >= NOON_TICK_OF_DAY) {
            startEvent(mc);
            done = true;
        }
        lastSeenTickOfDay = tickOfDay;
    }

    private static void startEvent(Minecraft mc) {
        if (eventTick >= 0) {
            return;
        }
        eventTick = 0;
        nextLineIndex = 0;
        visibleLines = new String[0];

        // Save and set render distance to 2
        Options options = mc.options;
        savedRenderDistance = options.renderDistance().get();
        options.renderDistance().set(2);
        forceRenderDistanceRecompile(mc);

        // Start the looping TV static sound.
        staticSoundInstance = new Day6StaticSoundInstance();
        mc.getSoundManager().play(staticSoundInstance);
    }

    private static void tickEvent(Minecraft mc) {
        eventTick++;

        // Check if a new line should appear.
        if (nextLineIndex < LINES.length) {
            int targetTick = switch (nextLineIndex) {
                case 0 -> LINE1_TICK;
                case 1 -> LINE2_TICK;
                case 2 -> LINE3_TICK;
                case 3 -> LINE4_TICK;
                default -> Integer.MAX_VALUE;
            };
            if (eventTick >= targetTick) {
                // Show this line.
                String[] newLines = new String[nextLineIndex + 1];
                System.arraycopy(LINES, 0, newLines, 0, nextLineIndex + 1);
                visibleLines = newLines;
                nextLineIndex++;

                // Play the blip sound using PlayerSound pattern.
                play(mc, ModSounds.DAY6_BLIP, SoundSource.MASTER, 1.0F, 1.0F);
            }
        }

        // Event duration ended?
        if (eventTick >= EVENT_DURATION_TICKS) {
            stopEvent(mc);
        }
    }

    private static void stopEvent(Minecraft mc) {
        eventTick = -1;
        nextLineIndex = 0;
        visibleLines = new String[0];
        if (staticSoundInstance != null) {
            mc.getSoundManager().stop(staticSoundInstance);
            staticSoundInstance = null;
        }

        // Restore render distance
        if (savedRenderDistance >= 0) {
            mc.options.renderDistance().set(savedRenderDistance);
            savedRenderDistance = -1;
            forceRenderDistanceRecompile(mc);
        }

        // Play the laughs sound
        play(mc, ModSounds.DAY6_LAUGHS, SoundSource.MASTER, 1.7F, 1.0F);
    }

    /**
     * Forces the renderer to recompile chunks so the render distance change takes effect immediately.
     */
    private static void forceRenderDistanceRecompile(Minecraft mc) {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    /** {@return whether the overlay should be drawn}. */
    public static boolean isVisible() {
        return eventTick >= 0;
    }

    /** {@return the lines currently visible}. */
    public static String[] getVisibleLines() {
        return visibleLines;
    }

    /** Dev/test hook — start the event right now. */
    public static void triggerNow() {
        startEvent(Minecraft.getInstance());
    }

    /** Dev/test hook — stop the event immediately. */
    public static void stopAll() {
        if (eventTick >= 0) {
            stopEvent(Minecraft.getInstance());
        }
        lastSeenTickOfDay = Long.MIN_VALUE;
        done = false;
    }
}