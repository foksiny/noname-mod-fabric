package dev.noname.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The white message of the day-8 red-sky event: "why does it taste good? it's
 * just an eye." — or one of the phrases it keeps flipping through — drawn in
 * the upper area of the screen while {@link Day8SkyHandler} says the message
 * is visible (3 seconds per event).
 *
 * <p>Rendered from the HUD callback ({@code HudRenderCallback}), on top of
 * the vanilla HUD, exactly like the day-5 flash overlay.
 */
public final class Day8SkyOverlay {

    /** White, as requested. */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    /** Drawn a bit bigger than the default font size. */
    private static final float TEXT_SCALE = 1.75F;

    /** Drawn in the upper third of the screen. */
    private static final float VERTICAL_POSITION = 0.22F;

    private Day8SkyOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!Day8SkyHandler.isTextVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Component message = Component.literal(Day8SkyHandler.getCurrentPhrase());
        float width = mc.font.width(message) * TEXT_SCALE;
        float height = mc.font.lineHeight * TEXT_SCALE;
        float x = (gui.guiWidth() - width) / 2.0F;
        float y = gui.guiHeight() * VERTICAL_POSITION;

        gui.pose().pushPose();
        gui.pose().translate(x, y, 2000.0F);
        gui.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        gui.drawString(mc.font, message, 0, 0, TEXT_COLOR, true);
        gui.pose().popPose();
    }
}
