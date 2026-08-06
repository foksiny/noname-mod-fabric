package dev.noname.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The ghost's whispered texts: while {@link Day10WhisperHandler} says a text
 * is showing, one of the phrases is drawn centered on the player's screen in
 * white, 1.5× the default size, for 1 second. Long texts wrap onto multiple
 * lines so they always stay on screen.
 *
 * <p>Rendered from the HUD callback ({@code HudRenderCallback}), after the
 * vanilla HUD. Purely cosmetic — it never blocks input or the game loop.
 */
public final class Day10WhisperOverlay {

    /** Pure white — the ghost's voice, not a HUD message. */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    /** Text drawn 1.5× bigger than the default font size. */
    private static final float TEXT_SCALE = 1.5F;

    /** Margin kept from the screen edges so wrapped lines never clip. */
    private static final int EDGE_MARGIN = 30;

    private Day10WhisperOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!Day10WhisperHandler.isTextVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Font font = mc.font;
        String text = Day10WhisperHandler.currentText();
        if (text.isEmpty()) {
            return;
        }

        // Wrap at a width that survives the scale-up below, keeping the
        // whole block within the edge margin.
        int wrapWidth = Math.max(50,
                (int) ((gui.guiWidth() - EDGE_MARGIN * 2) / TEXT_SCALE));
        List<FormattedCharSequence> lines = font.split(Component.literal(text), wrapWidth);

        float lineHeight = font.lineHeight * TEXT_SCALE;
        float width = 0.0F;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line) * TEXT_SCALE);
        }
        float x = (gui.guiWidth() - width) / 2.0F;
        float y = (gui.guiHeight() - lineHeight * lines.size()) / 2.0F;

        // The text needs the pose pushed above the HUD's z-offsets (the
        // vanilla layered HUD lifts each layer by 200.0F) to be on top.
        gui.pose().pushPose();
        gui.pose().translate(x, y, 2000.0F);
        gui.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        int lineY = 0;
        for (FormattedCharSequence line : lines) {
            gui.drawString(font, line, 0, lineY, TEXT_COLOR, true);
            lineY += font.lineHeight;
        }
        gui.pose().popPose();
    }
}
