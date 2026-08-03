package dev.noname;

import net.minecraft.world.entity.Mob;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks which hostile mobs were spawned deliberately (spawn egg or
 * {@code /summon}) or on the first day. {@link
 * dev.noname.mixin.MobSpawnMixin} records them in {@code finalizeSpawn};
 * {@link dev.noname.mixin.HostileSpawnGateMixin} consumes the mark when the
 * mob reaches {@code ServerLevel.addFreshEntity}. Keyed by mob identity, so
 * the entry disappears once the mob itself is garbage-collected.
 */
public final class HostileSpawnTracker {

    private static final Map<Mob, Boolean> DELIBERATE_SPAWNS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private HostileSpawnTracker() {
    }

    /** Marks a hostile mob as deliberately spawned (or as first-day spawn). */
    public static void markDeliberate(Mob mob, boolean deliberate) {
        DELIBERATE_SPAWNS.put(mob, deliberate);
    }

    /**
     * {@return true if the mob was deliberately spawned — the entry is
     * consumed, since a mob only enters the world once}
     */
    public static boolean isDeliberateSpawn(Mob mob) {
        return Boolean.TRUE.equals(DELIBERATE_SPAWNS.remove(mob));
    }
}
