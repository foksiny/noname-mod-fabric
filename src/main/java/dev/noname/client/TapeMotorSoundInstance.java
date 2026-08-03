package dev.noname.client;

import dev.noname.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The always-on cassette-tape motor hum that loops in the background for the
 * whole session after joining a world. Non-attenuated, relative (plays "in
 * the player's head"), looping, at 40% volume.
 */
public final class TapeMotorSoundInstance extends AbstractTickableSoundInstance {

    private static final float VOLUME = 0.4F;
    private static final float PITCH = 1.0F;

    TapeMotorSoundInstance() {
        super(ModSounds.TAPE_MOTOR, SoundSource.AMBIENT, RandomSource.create());
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
