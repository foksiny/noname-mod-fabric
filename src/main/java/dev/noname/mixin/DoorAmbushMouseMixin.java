package dev.noname.mixin;

import dev.noname.client.DoorAmbushClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Full mouse lockdown during the day-15+ door ambush: looking around,
 * scrolling the hotbar and every mouse button are dead for the 5 seconds.
 * The accumulated deltas are discarded on every blocked event too, so the
 * camera does not suddenly swing or the hotbar jump when the ambush ends.
 */
@Mixin(MouseHandler.class)
public abstract class DoorAmbushMouseMixin {

    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;
    @Shadow
    private double accumulatedScrollX;
    @Shadow
    private double accumulatedScrollY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void noname$lockDoorAmbushLook(double sensitivity, CallbackInfo ci) {
        if (!DoorAmbushClient.isActive()) {
            return;
        }
        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;
        ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void noname$lockDoorAmbushScroll(long windowPointer, double horizontalAmount,
                                             double verticalAmount, CallbackInfo ci) {
        if (!DoorAmbushClient.isActive()) {
            return;
        }
        this.accumulatedScrollX = 0.0D;
        this.accumulatedScrollY = 0.0D;
        ci.cancel();
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void noname$lockDoorAmbushButtons(long windowPointer, int button, int action,
                                              int mods, CallbackInfo ci) {
        if (!DoorAmbushClient.isActive()) {
            return;
        }
        ci.cancel();
    }
}
