package dev.noname.mixin;

import dev.noname.client.DoorAmbushClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kills every keybind during the day-15+ door ambush: inventory (E), chat
 * (T), hotbar slots, drop (Q), offhand swap (F), F5, F2... the whole of
 * {@code handleKeybinds} is skipped for the 5 seconds. Movement keys are
 * already zeroed by {@link DoorAmbushInputMixin} and the mouse by
 * {@link DoorAmbushMouseMixin}; Esc keeps working, since the pause key is
 * handled outside {@code handleKeybinds}.
 */
@Mixin(Minecraft.class)
public abstract class DoorAmbushKeybindsMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void noname$lockDoorAmbushKeybinds(CallbackInfo ci) {
        if (DoorAmbushClient.isActive()) {
            ci.cancel();
        }
    }
}
