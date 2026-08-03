package dev.noname.mixin;

import dev.noname.client.Day10LookClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zooms the FOV in by 20% while the day-10+ lag event is running, ramping
 * in with the camera swing and out when the view returns.
 */
@Mixin(GameRenderer.class)
public abstract class Day10LookZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void noname$applyDay10Zoom(Camera camera, float f, boolean bl, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue((double) Day10LookClient.applyZoom(cir.getReturnValueD()));
    }
}
