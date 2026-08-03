package dev.noname.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Disables certain menu buttons, rendering them grayed out and non-clickable,
 * with a helper tooltip shown on hover.
 *
 * <ul>
 *   <li>Pause menu (in a world): "Open to LAN" — {@code error.unable.open.lanBtn}</li>
 *   <li>Main menu: "Multiplayer" — {@code error.unable.open.multiplayerBtn}</li>
 * </ul>
 */
public final class ButtonRestrictionsHandler {

    private ButtonRestrictionsHandler() {
    }

    public static void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (screen instanceof PauseScreen) {
            disableButton(screen, "menu.shareToLan", "error.unable.open.lanBtn");
        } else if (screen instanceof TitleScreen) {
            disableButton(screen, "menu.multiplayer", "error.unable.open.multiplayerBtn");
        }
    }

    private static void disableButton(Screen screen, String buttonKey, String tooltipKey) {
        screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> matchesKey(button, buttonKey))
                .forEach(button -> {
                    button.active = false;
                    button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                });
    }

    /**
     * Matches a button by its vanilla translation key (e.g. {@code menu.multiplayer}),
     * regardless of the client's language.
     */
    private static boolean matchesKey(Button button, String key) {
        Component message = button.getMessage();
        if (message instanceof MutableComponent mutable
                && mutable.getContents() instanceof TranslatableContents contents) {
            return key.equals(contents.getKey());
        }
        return Component.translatable(key).getString().equals(message.getString());
    }
}
