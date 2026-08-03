package dev.noname.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * The "i can't stop doing it" flash: while {@link Day5FlashHandler} says the
 * flash is visible, the whole screen is filled black (the player is blind to
 * the world) and the message is drawn centered on top of it.
 *
 * <p>Rendered from the HUD callback ({@code HudRenderCallback}), after the
 * vanilla HUD. Purely cosmetic — it never blocks input or the game loop.
 */
public final class Day5FlashOverlay {

    /** Same red as the day-2 overlay, so the mod's messages share a look. */
    private static final int TEXT_COLOR = 0xFFFF5555;

    /** Opaque black — nothing of the world shows through while it lasts. */
    private static final int BACKGROUND_COLOR = 0xFF000000;

    /** Message drawn 2.5× bigger than the default font size. */
    private static final float TEXT_SCALE = 2.5F;

    private Day5FlashOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!Day5FlashHandler.isFlashVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Font font = mc.font;

        // The guiOverlay render type has no depth test — it is the same type
        // vanilla's sleep blackout fills with, so this always paints on top
        // of every HUD element (chat, hotbar, camera overlays, F3, ...).
        gui.fill(RenderType.guiOverlay(), 0, 0, gui.guiWidth(), gui.guiHeight(), BACKGROUND_COLOR);

        // The text needs the pose pushed above the HUD's z-offsets (the
        // vanilla layered HUD lifts each layer by 200.0F) to also be on top.
        Component message = Component.literal("i can't stop doing it");
        float width = font.width(message) * TEXT_SCALE;
        float height = font.lineHeight * TEXT_SCALE;
        float x = (gui.guiWidth() - width) / 2.0F;
        float y = (gui.guiHeight() - height) / 2.0F;

        gui.pose().pushPose();
        gui.pose().translate(x, y, 2000.0F);
        gui.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        gui.drawString(font, message, 0, 0, TEXT_COLOR, false);
        gui.pose().popPose();
    }
}
