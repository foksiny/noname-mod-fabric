package dev.noname.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops short grass, tall grass and sweet berry bushes from ever generating
 * naturally: every block a worldgen feature places funnels into
 * {@code WorldGenRegion.setBlock} — grass patches, vegetation patches,
 * structure templates, all of it — so cancelling it there removes all three
 * from chunk decoration. Only new chunks are affected: existing grass stays,
 * and players can still place these blocks themselves.
 */
@Mixin(WorldGenRegion.class)
public abstract class NoNaturalGrassMixin {

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void noname$noNaturalGrass(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.SWEET_BERRY_BUSH)) {
            cir.setReturnValue(false);
        }
    }
}
