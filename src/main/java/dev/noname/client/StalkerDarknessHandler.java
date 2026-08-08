package dev.noname.client;

import net.minecraft.client.Minecraft;

/**
 * Client side of the day-3+ stalker flicker's darkness hit. Vanilla's
 * darkness effect is useless for short bursts: {@code MobEffects.DARKNESS} is
 * registered with a 22-tick blend window, and the lightmap darkening only
 * targets full strength while the remaining duration is longer than that
 * window — a 1-second darkness never visually appears at all.
 *
 * <p>So, on top of the real server-side darkness II effect (which flashes
 * the status icon), the victim's client draws its own one-second darkness:
 * a full-screen black overlay that fades in within 3 ticks, holds, and fades
 * back out over the last 6 ticks ({@link StalkerDarknessOverlay} paints it).
 * The server arms it via the {@code day3_stalker} event payload.
 */
public final class StalkerDarknessHandler {

    /** Whole darkness length, in ticks (20 ticks = 1 second). */
    private static final int DARKNESS_TICKS = 20;

    /** Ticks to fade in from nothing to full strength. */
    private static final int FADE_IN_TICKS = 3;

    /** Ticks to fade back out at the end. */
    private static final int FADE_OUT_TICKS = 6;

    /** Peak overlay opacity — 90%, so the world still faintly shows through. */
    private static final float PEAK_ALPHA = 0.9F;

    /** Session tick at which the darkness starts; {@code -1} = no darkness. */
    private static int startTick = -1;

    /** Session tick at which the darkness ends; {@code -1} = no darkness. */
    private static int endTick = -1;

    private static int sessionTick = 0;

    private StalkerDarknessHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        sessionTick++;
        if (mc.level == null || mc.player == null) {
            startTick = -1;
            endTick = -1;
        }
    }

    /** {@return the current overlay opacity 0..1, or 0 when no darkness is
     *  on screen} */
    public static float getAlpha() {
        if (endTick < 0 || sessionTick < startTick || sessionTick >= endTick) {
            return 0.0F;
        }
        int elapsed = sessionTick - startTick;
        if (elapsed < FADE_IN_TICKS) {
            return PEAK_ALPHA * elapsed / FADE_IN_TICKS;
        }
        int remaining = endTick - sessionTick;
        if (remaining <= FADE_OUT_TICKS) {
            return PEAK_ALPHA * remaining / FADE_OUT_TICKS;
        }
        return PEAK_ALPHA;
    }

    /** Arms the darkness now — called by the {@code day3_stalker} event
     *  payload handler. */
    public static void triggerNow() {
        startTick = sessionTick;
        endTick = sessionTick + DARKNESS_TICKS;
    }

    /** Dev/test hook — hide the darkness immediately. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        startTick = -1;
        endTick = -1;
    }
}
