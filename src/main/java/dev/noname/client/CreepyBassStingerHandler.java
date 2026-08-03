package dev.noname.client;

import dev.noname.DayCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * From day 4 on, every 1–2 minutes (randomly) there is a 50% chance that the
 * client's render distance is clamped down to the minimum ({@link Options#RENDER_DISTANCE_TINY})
 * and the low, slow "creepy bass ambience" stinger plays with a fade in and
 * out. While the stinger is active the render distance stays at the minimum;
 * the moment the audio finishes the previous value is restored and a
 * {@link net.minecraft.client.renderer.LevelRenderer#allChanged()} recompile
 * is forced so the world view snaps back to normal.
 *
 * <p>This is entirely client-side: in singleplayer the local player's render
 * option is touched directly; in multiplayer (where the server already caps
 * the view via its own render-distance packet) the local option still governs
 * what the client <em>draws</em>, so the effect applies there too.
 *
 * <p>Day-4 check uses the {@link ClientLevel} directly — which on the client
 * keeps the live {@code dayTime} mirrored from the server.
 */
public final class CreepyBassStingerHandler {

    /** A stinger is only ever considered when the player is actually in a
     *  world (client tick). Before the next attempt, the handler waits a
     *  random 1–2 minutes. */
    private static final int MIN_WAIT_TICKS = 20 * 60;       // 60 s
    private static final int MAX_WAIT_TICKS = 20 * 120;      // 120 s

    /** Probability that the stinger actually fires when the timer elapses. */
    private static final float STING_CHANCE = 0.50F;

    private static int ticksUntilNextAttempt = MIN_WAIT_TICKS;

    /** Saved pre-stinger value of {@link Options#renderDistance()} while a
     *  stinger is active; {@code -1} means no stinger is currently running. */
    private static int savedRenderDistance = -1;

    private static boolean stingActive = false;

    private CreepyBassStingerHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        ClientLevel level = mc.level;

        // While a stinger is running, just wait for the sound to complete:
        // the {@link CreepyBassSoundInstance} calls {@link #onStingerComplete}
        // the instant its fade-out is done.
        if (stingActive) {
            return;
        }

        if (level == null || mc.player == null) {
            // Not in a world: reset the timer so the next session starts fresh.
            ticksUntilNextAttempt = MIN_WAIT_TICKS;
            return;
        }

        // Day 4+ only — gate on the same counter everything else uses.
        if (DayCounter.currentDay(level) < 4) {
            ticksUntilNextAttempt = MIN_WAIT_TICKS;
            return;
        }

        if (--ticksUntilNextAttempt > 0) {
            return;
        }

        // Roll a fresh random wait whether or not it fires this round.
        int wait = MIN_WAIT_TICKS
                + level.getRandom().nextInt(MAX_WAIT_TICKS - MIN_WAIT_TICKS + 1);
        ticksUntilNextAttempt = wait;

        if (level.getRandom().nextFloat() >= STING_CHANCE) {
            return;
        }
        triggerStinger(mc);
    }

    /**
     * Drops the local render distance to the minimum, forces a recompile so
     * the renderer picks up the new value immediately, and starts the
     * fade-in/out stinger.
     */
    private static void triggerStinger(Minecraft mc) {
        Options options = mc.options;
        savedRenderDistance = options.renderDistance().get();
        stingActive = true;

        options.renderDistance().set(net.minecraft.client.Options.RENDER_DISTANCE_TINY);
        forceRenderDistanceRecompile(mc);

        mc.getSoundManager().queueTickingSound(new CreepyBassSoundInstance(
                CreepyBassStingerHandler::onStingerComplete));
    }

    /**
     * Dev/test hook — fire the stinger right now, ignoring the day-4 gate and
     * the random 1–2 minute timer. Idempotent: if a stinger is already
     * running, this is a no-op.
     */
    public static void triggerStingerNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stingActive) {
            return;
        }
        triggerStinger(mc);
    }

    /**
     * Dev/test hook — stop any active stinger immediately: cancel the
     * rendering-distance drop, restore the saved value, and ask the client
     * sound manager to stop the {@code noname:creepy_bass} sound if it's
     * playing.
     */
    public static void stopAll() {
        if (stingActive) {
            Minecraft mc = Minecraft.getInstance();
            if (savedRenderDistance >= 0) {
                mc.options.renderDistance().set(savedRenderDistance);
                savedRenderDistance = -1;
                forceRenderDistanceRecompile(mc);
            }
            mc.getSoundManager().stop(
                    dev.noname.ModSounds.CREEPY_BASS.getLocation(),
                    net.minecraft.sounds.SoundSource.AMBIENT);
            stingActive = false;
        }
        // Always restart the timer so a fresh effect cycle can run later.
        ticksUntilNextAttempt = MIN_WAIT_TICKS;
    }

    /**
     * Called from {@link CreepyBassSoundInstance#tick} (client sound-tick
     * thread) once the fade-out is finished. Restores the saved render
     * distance and forces the renderer back to the player's normal view.
     */
    private static void onStingerComplete() {
        Minecraft mc = Minecraft.getInstance();
        if (savedRenderDistance >= 0) {
            mc.options.renderDistance().set(savedRenderDistance);
            savedRenderDistance = -1;
            forceRenderDistanceRecompile(mc);
        }
        stingActive = false;
    }

    /**
     * Vanilla doesn't always recompile the section buffer geometry when
     * {@link Options#renderDistance()} changes programmatically (the option
     * wiring only kicks the renderer in the options screen flow); force it so
     * the view snaps the same tick the value changes.
     */
    private static void forceRenderDistanceRecompile(Minecraft mc) {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }
}
