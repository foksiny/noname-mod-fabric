package dev.noname.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/**
 * The one-second darkness of the day-3+ stalker flicker: a full-screen black
 * overlay whose opacity follows {@link StalkerDarknessHandler#getAlpha()} —
 * a fast fade-in, a hold, and a fade-out — so the screen visibly darkens
 * even though vanilla's 1-second darkness effect never renders (its 22-tick
 * blend window swallows short effects entirely).
 *
 * <p>Rendered from the HUD callback ({@code HudRenderCallback}), after the
 * vanilla HUD. Purely cosmetic — it never blocks input or the game loop.
 */
public final class StalkerDarknessOverlay {

    private StalkerDarknessOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        float alpha = StalkerDarknessHandler.getAlpha();
        if (alpha <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        int color = ((int) (alpha * 255.0F) << 24) | 0x000000;
        // The guiOverlay render type has no depth test — it is the same type
        // vanilla's sleep blackout fills with, so this always paints on top
        // of every HUD element (chat, hotbar, camera overlays, F3, ...).
        gui.fill(RenderType.guiOverlay(), 0, 0, gui.guiWidth(), gui.guiHeight(), color);
    }
}
