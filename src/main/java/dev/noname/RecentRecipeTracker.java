package dev.noname;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The set of crafting recipes removed to match the pre-1.9 feel. Anything in
 * this set is dropped from the raw recipe JSON map by
 * {@code RecipeManagerMixin} before it is parsed, so these recipes can never
 * appear in the recipe book or be used in any crafting station.
 *
 * <p>Copper (added 1.17) is covered with prefixes because it has a huge
 * family of recipes; the exact names cover the few non-copper ones.
 */
public final class RecentRecipeTracker {

    private RecentRecipeTracker() {
    }

    private static final Set<String> REMOVED_PREFIXES = Set.of(
            "chiseled_copper", "copper", "cut_copper", "exposed_",
            "lightning_rod", "oxidized_", "raw_copper", "waxed_", "weathered_");

    private static final Set<String> REMOVED_EXACT = Set.of(
            "mace", "shield", "shield_decoration", "spyglass", "wind_charge");

    /** True if the recipe belongs to the removed "recent recipes" set. */
    public static boolean isRemoved(ResourceLocation id) {
        if (!id.getNamespace().equals("minecraft")) {
            return false;
        }
        String path = id.getPath();
        if (REMOVED_EXACT.contains(path)) {
            return true;
        }
        for (String prefix : REMOVED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
