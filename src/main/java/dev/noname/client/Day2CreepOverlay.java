package dev.noname.client;

import dev.noname.DayCounter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The centered "why don't you like it? :(" message that pops up on the player's
 * screen once {@link Day2CreepHandler} arms it, three minutes into day 2.
 *
 * <p>Rendered from the HUD callback ({@code HudRenderCallback}), which fires
 * after the vanilla HUD is drawn.
 *
 * <p>The overlay is purely cosmetic: it draws centered shadow text near the
 * vertical middle of the screen for as long as {@link Day2CreepHandler} says
 * the message is visible, and never blocks input or the game loop.
 */
public final class Day2CreepOverlay {

    /** A subtle red tint so it reads as unwelcome rather than bright-white HUD text. */
    private static final int TEXT_COLOR = 0xFFFF5555;

    private Day2CreepOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!Day2CreepHandler.isMessageVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Font font = mc.font;

        Component message = Component.literal("why don't you like it? :(");
        int centerX = (gui.guiWidth() - font.width(message)) / 2;
        int centerY = gui.guiHeight() / 3;

        gui.drawString(font, message, centerX, centerY, TEXT_COLOR, false);
    }
}
