package dev.noname.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shields do nothing: {@code isBlocking} always reports false, so no damage
 * reduction, no knockback damping, no arrow deflection. Combined with the
 * shield recipe removal and the offhand gate, the item is a dead weight.
 */
@Mixin(LivingEntity.class)
public abstract class ShieldBlockMixin {

    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void noname$neverBlocking(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}