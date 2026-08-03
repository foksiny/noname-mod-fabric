package dev.noname.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Plays a sound event bound to the local player entity, on the client thread.
 *
 * <p>The vanilla {@code LocalPlayer.playSound} path emits the sound at the
 * player's coordinates at the moment of the call — a fixed world point — so
 * walking away makes it fade. An {@link EntityBoundSoundInstance} instead
 * re-binds its x/y/z to the player entity on every client tick, so the source
 * always stays right at the player: it can never be walked away from.
 */
public final class PlayerSound {

    private PlayerSound() {
    }

    /** Plays a sound that follows the player around the world. */
    public static void play(Minecraft mc, SoundEvent sound, SoundSource source,
                            float volume, float pitch) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.getSoundManager().play(new EntityBoundSoundInstance(
                sound, source, volume, pitch, mc.player, mc.level.getRandom().nextLong()));
    }
}