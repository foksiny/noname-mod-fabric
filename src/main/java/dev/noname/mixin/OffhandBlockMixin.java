package dev.noname.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The off-hand simply does not work — hold anything there and trying to use
 * it is refused. Stops an item-use before it starts ({@code startUsingItem})
 * and cuts short anything that somehow began in the off hand anyway
 * ({@code updateUsingItem}'s per-tick pass).
 */
@Mixin(LivingEntity.class)
public abstract class OffhandBlockMixin {

    @Inject(method = "startUsingItem", at = @At("HEAD"), cancellable = true)
    private void noname$noOffhandStart(InteractionHand hand, CallbackInfo ci) {
        if (hand == InteractionHand.OFF_HAND) {
            ci.cancel();
        }
    }

    @Inject(method = "updateUsingItem", at = @At("HEAD"))
    private void noname$noOffhandUse(ItemStack stack, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getUsedItemHand() == InteractionHand.OFF_HAND) {
            self.stopUsingItem();
        }
    }
}