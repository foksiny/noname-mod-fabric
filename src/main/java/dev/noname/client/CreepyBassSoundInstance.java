package dev.noname.client;

import dev.noname.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The "creepy bass ambience" stinger as a tickable, fade-able sound.
 *
 * <p>The underlying OGG is played once (this is not a looping sound); a tick
 * callback ramps the {@link AbstractTickableSoundInstance#volume volume}
 * field smoothly in at the start and out at the end, and flips the engine's
 * "stopped" flag when the fade-out is complete — at which point the client
 * sound manager releases the source.
 *
 * <p>The pitch is held at a fixed low value (see {@link #PITCH}): there is no
 * random/positional pitch jitter — the stinger always sounds exactly the same.
 *
 * <p>Volume is reported back to whoever started the stinger through
 * {@link #onComplete} so the {@link CreepyBassStingerHandler} can restore the
 * client's render distance the instant the audio finishes.
 */
public final class CreepyBassSoundInstance extends AbstractTickableSoundInstance {

    /** Fixed low playback pitch — no randomization, always the same
     *  unsettling low rumble. Vanilla pitch range is ~0.5–2.0, well below
     *  the {@code 1.0} default. */
    private static final float PITCH = 0.65F;

    /** Full (pre-fade) volume. {@link AbstractTickableSoundInstance#volume}
     *  starts at 0 and rises toward this during the fade-in. */
    private static final float PEAK_VOLUME = 1.0F;

    /** Fade durations in client sound ticks (20ths of a second). The OGG
     *  track itself is {@code ~19.7 s}; the stinger's total length is
     *  {@code (FADE_IN + HOLD + FADE_OUT)} ticks, kept just under the audio's
     *  own length so the {@code onComplete} callback fires the instant the
     *  source falls silent (no trailing silence before the render distance
     *  is restored). */
    private static final int FADE_IN_TICKS = 20 * 3;     // 3 s up
    private static final int HOLD_TICKS = 20 * 14;      // 14 s at peak
    private static final int FADE_OUT_TICKS = 20 * 3;    // 3 s down
    private static final int TOTAL_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

    private int ticksPlayed;

    /** Called from the client tick thread when the fade-out finishes. */
    private final Runnable onComplete;

    CreepyBassSoundInstance(Runnable onComplete) {
        super(ModSounds.CREEPY_BASS, SoundSource.AMBIENT, RandomSource.create());
        this.onComplete = onComplete;
        this.volume = 0.0F;
        this.pitch = PITCH;
        // Non-positional ambience: not tied to a world position and doesn't
        // attenuate over distance, so the stinger is heard the same from
        // anywhere in the world.
        this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.looping = false;
    }

    @Override
    public void tick() {
        ticksPlayed++;

        if (ticksPlayed <= FADE_IN_TICKS) {
            // Linear fade-in 0 → PEAK_VOLUME.
            volume = PEAK_VOLUME * (ticksPlayed / (float) FADE_IN_TICKS);
        } else if (ticksPlayed <= FADE_IN_TICKS + HOLD_TICKS) {
            volume = PEAK_VOLUME;
        } else if (ticksPlayed < TOTAL_TICKS) {
            // Linear fade-out PEAK_VOLUME → 0.
            int into = ticksPlayed - FADE_IN_TICKS - HOLD_TICKS;
            volume = PEAK_VOLUME * (1.0F - (into / (float) FADE_OUT_TICKS));
        } else {
            volume = 0.0F;
            stop();
            onComplete.run();
        }
    }
}
