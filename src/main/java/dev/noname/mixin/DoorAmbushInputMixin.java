package dev.noname.mixin;

import dev.noname.client.DoorAmbushClient;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes the player during the day-15+ door ambush: all movement input is
 * zeroed while the event runs (the server pins the position too), the same
 * mechanism as {@link Day10LookInputMixin}. The look and every keybind
 * are locked by {@link DoorAmbushMouseMixin} and
 * {@link DoorAmbushKeybindsMixin}.
 */
@Mixin(KeyboardInput.class)
public abstract class DoorAmbushInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noname$lockDoorAmbushInput(boolean bl, float f, CallbackInfo ci) {
        if (!DoorAmbushClient.isMovementLocked()) {
            return;
        }
        Input input = (Input) (Object) this;
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
