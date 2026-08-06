package dev.noname.mixin;

import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops bee nests from ever being naturally generated: every naturally placed
 * bee nest is put down by {@link BeehiveDecorator} while trees are generated,
 * so cancelling its {@code place} removes nests from worldgen entirely.
 * Player-placed nests are unaffected.
 */
@Mixin(BeehiveDecorator.class)
public abstract class NoBeeNestMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void noname$noBeeNests(TreeDecorator.Context context, CallbackInfo ci) {
        ci.cancel();
    }
}
