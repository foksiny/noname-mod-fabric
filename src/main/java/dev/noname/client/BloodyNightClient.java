package dev.noname.client;

import net.minecraft.client.Minecraft;

/**
 * Client-side counterpart of the server's Bloody Night decision: remembers
 * whether the current night is a Bloody Night (synced from the server via
 * {@link dev.noname.network.BloodyNightPayload}) and ramps a 0..1 intensity
 * towards that state, so the fog tint fades in and out smoothly instead of
 * snapping exactly at dusk/dawn.
 */
public final class BloodyNightClient {

    /** How far the intensity moves per tick — a full fade takes ~5 s. */
    private static final float RAMP_STEP = 1.0F / 100.0F;

    /** Whether the server says the current night is a Bloody Night. */
    private static boolean bloody;

    /** Smoothed 0..1 strength of the red fog tint. */
    private static float intensity;

    private BloodyNightClient() {
    }

    /** Updates the synced state (called from the payload receiver). */
    public static void set(boolean value) {
        bloody = value;
    }

    /** Drifts the intensity towards the synced state once per tick. */
    public static void onClientTick(Minecraft mc) {
        if (mc.level == null) {
            bloody = false;
            intensity = 0.0F;
            return;
        }
        float target = bloody ? 1.0F : 0.0F;
        if (intensity < target) {
            intensity = Math.min(target, intensity + RAMP_STEP);
        } else if (intensity > target) {
            intensity = Math.max(target, intensity - RAMP_STEP);
        }
    }

    /** {@return the smoothed 0..1 tint strength for this frame}. */
    public static float intensity() {
        return intensity;
    }

    /** {@return whether the red tint is currently visible at all}. */
    public static boolean isActive() {
        return intensity > 0.0F;
    }
}
