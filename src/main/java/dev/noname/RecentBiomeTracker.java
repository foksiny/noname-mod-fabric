package dev.noname;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

/**
 * The set of overworld biomes added to Minecraft after the 1.8 era. Anything
 * in this set is refused by {@code OverworldBiomeGateMixin} while the
 * overworld parameter list is being built, so those biomes can never be
 * chosen by the noise biome source. The biomes themselves stay registered —
 * only their climate points are removed.
 */
public final class RecentBiomeTracker {

    private RecentBiomeTracker() {
    }

    private static final Set<ResourceKey<Biome>> REMOVED = Set.of(
            // 1.17 Caves & Cliffs
            Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES,
            // 1.18 Caves & Cliffs II
            Biomes.MEADOW, Biomes.GROVE, Biomes.SNOWY_SLOPES,
            Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.STONY_PEAKS,
            // 1.19 The Wild Update
            Biomes.DEEP_DARK, Biomes.MANGROVE_SWAMP,
            // 1.20 Trails & Tales
            Biomes.CHERRY_GROVE);

    /** True if the biome key belongs to the removed "recent biomes" set. */
    public static boolean isRemoved(ResourceKey<Biome> key) {
        return REMOVED.contains(key);
    }
}
