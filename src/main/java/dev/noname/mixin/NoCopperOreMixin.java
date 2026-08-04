package dev.noname.mixin;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops copper ore from ever being placed: every placed feature funnels into
 * {@code placeWithContext}, and the two copper carriers are the
 * {@code ore_copper_small} / {@code ore_copper_large} configured features.
 */
@Mixin(PlacedFeature.class)
public abstract class NoCopperOreMixin {

    @Inject(method = "placeWithContext", at = @At("HEAD"), cancellable = true)
    private void noname$noCopperOre(PlacementContext context, RandomSource random, BlockPos pos,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (context.getLevel() instanceof WorldGenLevel) {
            String feature = this.featurePath();
            if ("ore_copper_small".equals(feature) || "ore_copper_large".equals(feature)) {
                cir.setReturnValue(false);
            }
        }
    }

    private String featurePath() {
        PlacedFeature self = (PlacedFeature) (Object) this;
        return self.feature().unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("");
    }
}