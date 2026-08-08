package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locks the chat away once the horror takes hold: from day 15+ every attempt
 * to open the chat — the T key, the "/" command key, and the in-bed chat box
 * (they all end up as a {@link ChatScreen} handed to
 * {@link Minecraft#setScreen}) — is silently swallowed, so the screen never
 * opens and the key does nothing anymore.
 */
@Mixin(Minecraft.class)
public abstract class ChatBlockMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void noname$blockChatScreen(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof ChatScreen)) {
            return;
        }
        Minecraft self = (Minecraft) (Object) this;
        if (self.level == null
                || DayCounter.currentDay(self.level) < ModConfig.scaledDay(15)
                || !ModConfig.isEnabled("chat_block")) {
            return;
        }
        ci.cancel();
    }
}
