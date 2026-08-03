package dev.noname.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the light look like pre-Beta 1.8: the classic flat quadratic curve
 * {@code f(v) = v^2} (the old {@code (lightLevel/15)^2} formula) with the
 * gamma smoothing removed, then quantized into hard 1/16 steps so light
 * transitions show the blocky banding of the old per-block light levels.
 *
 * <p>{@link LightTexture#getBrightness(DimensionType, int)} converts light
 * levels (0-15) into the brightness used for every pixel of the lightmap
 * (both the sky and block axes), so it is the single choke point for the
 * whole lighting of blocks and entities.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private static void noname$classicLight(DimensionType dimensionType, int lightLevel, CallbackInfoReturnable<Float> cir) {
        float value = cir.getReturnValue();
        float quadratic = value * value;                    // (level/15)^2
        float banded = Mth.floor(quadratic * 16.0F) / 16.0F; // hard light-level steps
        cir.setReturnValue(banded);
    }
}
