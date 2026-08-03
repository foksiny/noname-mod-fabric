package dev.noname.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The classic version string in the top-left corner of the screen, like old
 * Minecraft versions showed their version in-game. Always visible while
 * playing; skipped while the F3 debug screen is open so it does not overlap
 * the vanilla version line.
 */
public final class VersionOverlay {

    private static final String VERSION_TEXT = "Minecraft v1.21.1";

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private VersionOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        gui.drawString(mc.font, Component.literal(VERSION_TEXT), 2, 2, TEXT_COLOR, true);
    }
}
