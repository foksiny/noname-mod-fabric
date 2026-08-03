package dev.noname.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Always-on higher contrast: after {@link LightTexture#updateLightTexture}
 * rebuilds the lightmap, every pixel of it is pushed away from the mid-grey —
 * dark shades get darker, bright ones brighter — so the whole world renders
 * punchier. The lightmap drives the colour of every block and entity, which
 * makes this a single cheap pass (256x256) for a global look change.
 */
@Mixin(LightTexture.class)
public abstract class ContrastMixin {

    /** How far pixels are pushed from the mid-grey (1.0 = unchanged). */
    private static final float CONTRAST = 1.35F;

    @Shadow
    @Final
    private NativeImage lightPixels;

    @Inject(method = "updateLightTexture", at = @At("TAIL"))
    private void noname$moreContrast(float partialTick, CallbackInfo ci) {
        if (this.lightPixels == null) {
            return;
        }
        for (int x = 0; x < this.lightPixels.getWidth(); x++) {
            for (int y = 0; y < this.lightPixels.getHeight(); y++) {
                int pixel = this.lightPixels.getPixelRGBA(x, y);
                int alpha = pixel >>> 24;
                int red = contrast(pixel & 0xFF);
                int green = contrast((pixel >>> 8) & 0xFF);
                int blue = contrast((pixel >>> 16) & 0xFF);
                this.lightPixels.setPixelRGBA(x, y, alpha << 24 | blue << 16 | green << 8 | red);
            }
        }
    }

    private static int contrast(int channel) {
        float value = channel / 255.0F;
        value = Mth.clamp(0.5F + (value - 0.5F) * CONTRAST, 0.0F, 1.0F);
        return (int) (value * 255.0F);
    }
}
