package dev.noname.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Totems of undying never trigger: the death-protection check always says
 * "no totem used", so the totem is neither consumed nor does the holder get
 * resurrected.
 */
@Mixin(LivingEntity.class)
public abstract class TotemBlockMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void noname$noTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}