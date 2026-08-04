package dev.noname.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes running entirely: any attempt to start sprinting is turned into a
 * "stop sprinting" call, so the player walks everywhere like it's 2009.
 */
@Mixin(LivingEntity.class)
public abstract class SprintGateMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void noname$noSprinting(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        // Re-enter with false: the vanilla code then clears the flag and
        // removes the speed modifier exactly as if the player stopped
        // sprinting. The outer (true) call is cancelled.
        self.setSprinting(false);
        ci.cancel();
    }
}