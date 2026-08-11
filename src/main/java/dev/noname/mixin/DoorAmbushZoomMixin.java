package dev.noname.mixin;

import dev.noname.client.DoorAmbushClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zooms the FOV in while the day-15+ door ambush runs (quick fade-in,
 * hold, fade-out), the same hook {@link Day10LookZoomMixin} uses for the
 * day-10 lag event.
 */
@Mixin(GameRenderer.class)
public abstract class DoorAmbushZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void noname$applyDoorAmbushZoom(Camera camera, float f, boolean bl,
                                            CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue((double) DoorAmbushClient.applyZoom(cir.getReturnValueD()));
    }
}
