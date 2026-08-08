package dev.noname.client;

import net.minecraft.client.Minecraft;

/**
 * Client side of the day-13+ stalker catch: the 0.1-second red static flash
 * that hits the victim's screen the moment the stalker (which has just walked
 * up behind them) disappears with the laggy2 blast. The server arms it via
 * the {@code day13_stalker} event payload; {@link StalkerStaticOverlay}
 * paints it.
 */
public final class StalkerStaticHandler {

    /** Whole flash length, in ticks (2 ticks = 0.1 seconds). */
    private static final int FLASH_TICKS = 2;

    /** Session tick at which the flash starts; {@code -1} = no flash. */
    private static int startTick = -1;

    private static int sessionTick = 0;

    private StalkerStaticHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        sessionTick++;
        if (mc.level == null || mc.player == null) {
            startTick = -1;
        }
    }

    /** {@return whether the red static is on screen right now} */
    public static boolean isVisible() {
        return startTick >= 0 && sessionTick >= startTick
                && sessionTick < startTick + FLASH_TICKS;
    }

    /** Arms the flash now — called by the {@code day13_stalker} event
     *  payload handler. */
    public static void triggerNow() {
        startTick = sessionTick;
    }

    /** Dev/test hook — hide the flash immediately. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        startTick = -1;
    }
}
