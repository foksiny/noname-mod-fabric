package dev.noname.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the post-1.8 attack cooldown: attack strength is always full, so
 * every swing hits for full damage right after the last one and the cooldown
 * bar in the HUD never appears.
 */
@Mixin(Player.class)
public abstract class AttackCooldownMixin {

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void noname$alwaysFullStrength(float f, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(1.0F);
    }
}