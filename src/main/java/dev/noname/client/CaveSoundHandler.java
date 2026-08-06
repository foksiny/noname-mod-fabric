package dev.noname.client;

import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Every 30 seconds there is a 10% chance that a random cave sound plays at
 * the player's position. {@link SoundEvents#AMBIENT_CAVE} is the vanilla
 * cave ambience event (cave1-13), so each play picks a random cave sound
 * file with vanilla's pitch range.
 */
public final class CaveSoundHandler {

    /** 30 seconds, in ticks (20 ticks per second). */
    private static final int CHECK_INTERVAL_TICKS = 20 * 30;

    /** Chance (0.0 - 1.0) that the sound plays when the timer fires. */
    private static final float PLAY_CHANCE = 0.10F;

    private static final float VOLUME = 2.0F; // vanilla cave mood volume

    private static int ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

    private CaveSoundHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            // Not in a world: restart the timer so it's a fresh 30s after joining.
            ticksUntilNextCheck = CHECK_INTERVAL_TICKS;
            return;
        }

        if (--ticksUntilNextCheck > 0) {
            return;
        }
        ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

        if (ModConfig.isEnabled("cave_sounds")
                && mc.level.random.nextFloat() < ModConfig.chance("cave_sounds", PLAY_CHANCE)) {
            float pitch = mc.level.random.nextFloat() * 0.7F + 0.7F;
            // Bound to the player entity itself so the cave sound follows
            // them and can never be walked away from.
            PlayerSound.play(mc, SoundEvents.AMBIENT_CAVE.value(),
                    SoundSource.PLAYERS, VOLUME, pitch);
        }
    }
}
