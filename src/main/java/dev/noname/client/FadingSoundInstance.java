package dev.noname.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A sound instance with an extra fade multiplier on top of its base volume.
 * Implements {@link TickableSoundInstance} so the sound engine registers it
 * in its per-tick list and re-reads {@link #getVolume()} every tick —
 * that re-read is what lets {@link Day8SkyHandler} fade the ambience in and
 * out smoothly. The fade must never start at exactly zero volume: the engine
 * skips instances whose calculated volume is zero at play time.
 */
public final class FadingSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {

    private float fadeVolume = 1.0F;

    public FadingSoundInstance(SoundEvent soundEvent, SoundSource soundSource, float pitch) {
        super(soundEvent, soundSource, RandomSource.create());
        this.pitch = pitch;
        // Non-positional ambience: not tied to a world position and doesn't
        // attenuate over distance, so the drone is heard the same from
        // anywhere in the world and can never be walked away from.
        this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        this.relative = true;
    }

    @Override
    public float getVolume() {
        return super.getVolume() * this.fadeVolume;
    }

    public void setFadeVolume(float fadeVolume) {
        this.fadeVolume = fadeVolume;
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public void tick() {
        // Volume is driven externally by Day8SkyHandler.
    }
}
