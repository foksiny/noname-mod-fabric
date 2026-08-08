package dev.noname;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

/**
 * The overworld biomes that already existed in Minecraft 1.5 and earlier,
 * i.e. everything added in 1.6 (the Horse Update, which introduced savanna
 * and badlands) or later is refused by {@code OverworldBiomeGateMixin} while
 * the overworld parameter list is being built, so those biomes can never be
 * chosen by the noise biome source. The biomes themselves stay registered —
 * only their climate points are removed.
 */
public final class RecentBiomeTracker {

    private RecentBiomeTracker() {
    }

    private static final Set<ResourceKey<Biome>> PRE_1_6 = Set.of(
            // 1.0 (Beta 1.8)
            Biomes.OCEAN, Biomes.PLAINS, Biomes.DESERT, Biomes.FOREST,
            Biomes.TAIGA, Biomes.SWAMP, Biomes.RIVER, Biomes.FROZEN_RIVER,
            Biomes.FROZEN_OCEAN, Biomes.BEACH, Biomes.MUSHROOM_FIELDS,
            Biomes.SNOWY_PLAINS, Biomes.WINDSWEPT_HILLS,
            // 1.2
            Biomes.JUNGLE, Biomes.SPARSE_JUNGLE);

    /** True if the biome key was added in Minecraft 1.6 or later. */
    public static boolean isRemoved(ResourceKey<Biome> key) {
        return !PRE_1_6.contains(key);
    }
}
