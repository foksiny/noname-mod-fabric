package dev.noname.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes elytra flight: the server never lets the gliding state start
 * ({@code tryToStartFallFlying}) and {@code isFallFlying} always reports
 * false, so the glide physics, rocket boosting and landing code all see a
 * normal falling player.
 */
@Mixin(Player.class)
public abstract class ElytraBlockMixin {

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void noname$noElytraStart(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Mixin(LivingEntity.class)
    public abstract static class NeverFallFlying {

        @Inject(method = "isFallFlying", at = @At("HEAD"), cancellable = true)
        private void noname$neverFallFlying(CallbackInfoReturnable<Boolean> cir) {
            cir.setReturnValue(false);
        }
    }
}