package dev.noname.client;

import dev.noname.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The TV static sound that loops continuously during the day-6 event.
 */
public final class Day6StaticSoundInstance extends AbstractTickableSoundInstance {

    private static final float VOLUME = 1.0F;
    private static final float PITCH = 1.0F;

    Day6StaticSoundInstance() {
        super(ModSounds.DAY6_STATIC, SoundSource.MASTER, RandomSource.create());
        this.volume = VOLUME;
        this.pitch = PITCH;
        this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.looping = true;
    }

    @Override
    public void tick() {
        // Volume stays constant; the sound engine handles the looping.
    }
}