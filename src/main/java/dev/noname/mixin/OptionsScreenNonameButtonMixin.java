package dev.noname.mixin;

import dev.noname.client.config.NonameConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the mod's configuration button in the top-left corner of the vanilla
 * Options screen: a small "Noname" button that opens
 * {@link NonameConfigScreen}. Injected after the screen builds its own
 * widgets so nothing gets displaced.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenNonameButtonMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void noname$addConfigButton(CallbackInfo ci) {
        OptionsScreen screen = (OptionsScreen) (Object) this;
        ((ScreenAccessor) screen).noname$addRenderableWidget(Button.builder(
                        Component.literal("Noname"),
                        button -> Minecraft.getInstance().setScreen(
                                new NonameConfigScreen(screen)))
                .bounds(4, 4, 80, 20)
                .build());
    }
}
