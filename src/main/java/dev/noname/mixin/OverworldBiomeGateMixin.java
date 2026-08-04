package dev.noname.mixin;

import com.mojang.datafixers.util.Pair;
import dev.noname.RecentBiomeTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * Filters the removed "recent biomes" (see {@link RecentBiomeTracker}) out of
 * the overworld preset's parameter list. The preset's list is built by
 * {@code OverworldBiomeBuilder.addBiomes}; this mixin wraps the consumer so
 * the removed biome keys never make it into the list.
 */
@Mixin(MultiNoiseBiomeSourceParameterList.Preset.class)
public abstract class OverworldBiomeGateMixin {

    @Redirect(method = "generateOverworldBiomes",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;addBiomes(Ljava/util/function/Consumer;)V"))
    private static void noname$filterRecentBiomes(OverworldBiomeBuilder builder,
                                                  Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        ((OverworldBiomeBuilderAccessor) (Object) builder).noname$addBiomes(point -> {
            if (!RecentBiomeTracker.isRemoved(point.getSecond())) {
                consumer.accept(point);
            }
        });
    }
}
