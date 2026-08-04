package dev.noname.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks right-clicking on entities with something held in the off hand
 * (feeding animals, shearing sheep, leashing...). Any off-hand item simply
 * never gets to act.
 */
@Mixin(Player.class)
public abstract class PlayerOffhandBlockMixin {

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void noname$noOffhandInteract(Entity entity, InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.OFF_HAND) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}