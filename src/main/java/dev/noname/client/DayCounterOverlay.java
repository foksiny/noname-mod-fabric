package dev.noname.client;

import dev.noname.DayCounter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A small day counter pinned to the top-right corner of the screen, showing
 * the current in-game day (vanilla numbering: 0 is the first day). Matches the
 * counter used by the rest of the mod ({@link DayCounter}), with the mod's own
 * schedule in mind so the player can keep track of how far along they are.
 */
public final class DayCounterOverlay {

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private DayCounterOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        String text = "Day " + DayCounter.currentDay(mc.level);
        int x = gui.guiWidth() - mc.font.width(text) - 2;
        int y = 2;
        gui.drawString(mc.font, text, x, y, TEXT_COLOR, true);
    }
}