package dev.noname.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops magma blocks from ever generating in the overworld: every block a
 * worldgen feature places funnels into {@code WorldGenRegion.setBlock}, so
 * cancelling it there removes the underwater magma that ocean floors and
 * underwater ravines carry. Scoped to the overworld — the nether's own magma
 * patches (basalt deltas) still generate. Only new chunks are affected:
 * existing magma stays, and players can still place it themselves.
 */
@Mixin(WorldGenRegion.class)
public abstract class NoUnderwaterMagmaMixin {

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void noname$noUnderwaterMagma(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (state.is(Blocks.MAGMA_BLOCK)
                && ((WorldGenRegion) (Object) this).getLevel().dimension() == Level.OVERWORLD) {
            cir.setReturnValue(false);
        }
    }
}
