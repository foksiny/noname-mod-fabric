package dev.noname;

import net.minecraft.world.entity.animal.Pig;

/**
 * Implemented on {@link Pig} by {@code PigInfectionMixin} so the day-5
 * infection state can be read and toggled without using a synced custom name
 * (a name would still be drawn above the pig whenever the crosshair is over
 * it, no matter the visibility flag). The flag lives in the pig's synched
 * entity data, so it syncs to clients for the red tint layer automatically
 * and survives save/load like any other entity data.
 */
public interface InfectedPig {

    /** Whether this pig is infected (day-5 infection marker). */
    boolean noname$isInfected();

    /** Marks or unmarks the pig as infected. */
    void noname$setInfected(boolean infected);
}