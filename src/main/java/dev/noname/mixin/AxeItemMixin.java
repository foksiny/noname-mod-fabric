package dev.noname.mixin;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Disables only the log-stripping part of right-clicking a block with an
 * axe — the vanilla 1.21 "use an axe on a log to peel it" feature. The
 * single private helper {@link AxeItem#getStripped} is the chokepoint that
 * maps a stripped-log lookup; injecting at its {@code HEAD} and returning
 * an empty {@link Optional} makes every axe right-click on a log fall
 * through to "nothing to do", so copper de-oxidation and wax removal
 * (which route through separate helpers) keep working.
 */
@Mixin(AxeItem.class)
public abstract class AxeItemMixin {

    @Inject(method = "getStripped(Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private void noname$noLogStripping(BlockState state,
                                       CallbackInfoReturnable<Optional<BlockState>> cir) {
        cir.setReturnValue(Optional.empty());
    }
}
