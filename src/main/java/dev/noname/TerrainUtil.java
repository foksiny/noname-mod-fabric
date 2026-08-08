package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime access to the Alpha terrain system's custom density functions.
 * <p>
 * The Alpha overworld data pack registers two helper functions under
 * {@code data/minecraft/worldgen/density_function/overworld/} that let the
 * mod's events (and the game's biome sampler) know a location is ocean:
 * <ul>
 *   <li>{@code alpha_is_ocean} — {@code 1.0} where the Alpha depth noise
 *       puts the column below sea level (depth noise negative), {@code 0.0}
 *       on land.</li>
 *   <li>{@code alpha_continentalness} — the Alpha land/ocean weight remapped
 *       to the vanilla {@code [-1, 1]} continentalness range so the
 *       {@code MultiNoiseBiomeSource} picks ocean biomes for Alpha oceans and
 *       never spawns the player on water.</li>
 * </ul>
 * The functions are evaluated at block coordinates through the same
 * {@link DensityFunction} the chunk generator uses, so the answers are always
 * consistent with the terrain that actually generates.
 * <p>
 * There is intentionally no {@code alpha_is_cave} function: like old Alpha,
 * caves are NOT carved by the density field but by the vanilla cave carver
 * (the biome {@code carvers} entries), so cave detection stays block-based
 * (see {@link CaveUtil}).
 */
public final class TerrainUtil {

    private TerrainUtil() {
    }

    private static final ResourceLocation ALPHA_IS_OCEAN =
            ResourceLocation.withDefaultNamespace("overworld/alpha_is_ocean");

    /** Cache of resolved density functions per level (registries are per-dimension save). */
    private static final Map<ResourceKey<? extends Registry<DensityFunction>>, DensityFunction> CACHE =
            new HashMap<>();

    /**
     * {@return true if the column at the given horizontal position is part of
     * an Alpha ocean} The check is purely terrain-driven: it samples the
     * Alpha depth noise (via {@code alpha_is_ocean}) at the sea-level block,
     * not the biome, so it matches the water the player actually swims in.
     */
    public static boolean isOcean(ServerLevel level, BlockPos pos) {
        int seaLevel = level.getSeaLevel();
        double v = sample(level, ALPHA_IS_OCEAN, pos.getX(), seaLevel, pos.getZ());
        return v > 0.5;
    }

    /** {@return the loaded Alpha density function, or null if not present} */
    private static DensityFunction get(ServerLevel level, ResourceLocation id) {
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DENSITY_FUNCTION);
        ResourceKey<? extends Registry<DensityFunction>> key = registry.key();
        DensityFunction cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        DensityFunction fn = registry.get(ResourceKey.create(
                net.minecraft.core.registries.Registries.DENSITY_FUNCTION, id));
        if (fn != null) {
            CACHE.put(key, fn);
        }
        return fn;
    }

    /** {@return the value of the given density function at a block position,
     *  or {@code 0.0} if the function is not registered} */
    private static double sample(ServerLevel level, ResourceLocation id, int x, int y, int z) {
        DensityFunction fn = get(level, id);
        if (fn == null) {
            return 0.0;
        }
        return fn.compute(new DensityFunction.SinglePointContext(x, y, z));
    }
}
