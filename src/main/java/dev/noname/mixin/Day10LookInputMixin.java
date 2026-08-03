package dev.noname.mixin;

import dev.noname.client.Day10LookClient;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes the player during the day-10+ lag event: all movement input is
 * zeroed while the event runs (the server pins the position too). Runs at
 * the tail of {@code KeyboardInput.tick} — after the key state was copied
 * into the {@link Input} fields but before {@code Player.aiStep} consumes
 * them, so the zeroing actually takes effect.
 */
@Mixin(KeyboardInput.class)
public abstract class Day10LookInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noname$lockDay10Input(boolean bl, float f, CallbackInfo ci) {
        if (!Day10LookClient.isMovementLocked()) {
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
