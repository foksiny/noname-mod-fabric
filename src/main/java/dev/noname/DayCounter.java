package dev.noname;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Day-counting helper matching vanilla's own counter (the one shown in the
 * F3 debug screen): day 0 is the first day of the world, day 1 starts after
 * the first full day has passed.
 */
public final class DayCounter {

    private DayCounter() {
    }

    /**
     * {@return the current day number, 0 = first day}
     *
     * <p>Works for any server-side level accessor, including
     * {@link net.minecraft.server.level.WorldGenRegion} during chunk
     * generation, which has no day time of its own.
     */
    public static long currentDay(LevelAccessor level) {
        if (level instanceof Level level1) {
            return level1.getDayTime() / 24000L;
        }
        MinecraftServer server = level.getServer();
        return server != null ? server.overworld().getDayTime() / 24000L : 0L;
    }
}
