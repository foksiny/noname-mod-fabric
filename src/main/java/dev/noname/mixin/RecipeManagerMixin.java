package dev.noname.mixin;

import com.google.gson.JsonElement;
import dev.noname.RecentRecipeTracker;
import dev.noname.config.ModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Drops the removed "recent recipes" (see {@link RecentRecipeTracker}) from
 * the raw recipe JSON map before parsing, so they never get registered.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void noname$dropRecentRecipes(Map<ResourceLocation, JsonElement> object,
                                          ResourceManager resourceManager,
                                          ProfilerFiller profilerFiller,
                                          CallbackInfo ci) {
        object.keySet().removeIf(RecentRecipeTracker::isRemoved);
        // The infinite-knife recipe is toggleable; when switched off it is
        // dropped just like the removed recipes above.
        if (!ModConfig.isEnabled("knife_craft")) {
            object.keySet().removeIf(id -> id.getNamespace().equals("noname")
                    && id.getPath().equals("infinite_knife"));
        }
    }
}