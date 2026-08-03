package dev.noname.mixin;

import dev.noname.client.Day10LookClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Advances the day-10+ lag event timeline at the end of every local player
 * tick and pins the player's rotation on the behind-pose during the hold.
 */
@Mixin(LocalPlayer.class)
public abstract class Day10LookTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noname$advanceDay10Look(CallbackInfo ci) {
        Day10LookClient.tick();
    }
}
