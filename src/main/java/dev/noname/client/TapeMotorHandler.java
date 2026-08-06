package dev.noname.client;

import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Starts the looping cassette-tape motor hum as soon as the player joins a
 * world and keeps it running for the whole session. The hum is a single,
 * looping {@link TapeMotorSoundInstance} at 40% volume that is (re)started
 * whenever the player is in a world and not already playing.
 *
 * <p>Part of the VHS effect: it stops immediately (and stays stopped) while
 * the "VHS effect" feature is disabled.
 */
public final class TapeMotorHandler {

    private static SoundInstance instance;

    private TapeMotorHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModConfig.isEnabled("vhs_effect")) {
            if (instance != null) {
                mc.getSoundManager().stop(instance);
                instance = null;
            }
            return;
        }
        if (mc.player == null || mc.level == null) {
            instance = null;
            return;
        }
        if (instance == null) {
            instance = new TapeMotorSoundInstance();
            mc.getSoundManager().play(instance);
        }
    }
}
