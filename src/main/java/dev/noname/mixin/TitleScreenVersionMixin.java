package dev.noname.mixin;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * The "No Name 0.3.0 beta" version string pinned to the top-right corner of
 * the main menu. Most of the time it sits there quietly, but roughly every
 * one to two seconds it stutters and corrupts for a few frames, as if the
 * game is struggling to render it: the text tears into horizontal bands that
 * drift apart, ghosts and static bars crawl over it, its label garbles into
 * glitch glyphs, and once in a while the whole thing yanks away from its
 * spot before snapping back.
 *
 * <p>The offsets are only re-rolled a few times per second (not every frame),
 * so the text stutters in place instead of smoothly animating. Only the main
 * menu is touched; in-game screens render normally.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenVersionMixin {

    private static final String VERSION_TEXT = "No Name 0.3.0 beta";

    /** Time a "lag tick" takes: offsets are held for this long instead of
     *  being re-rolled every frame, which reads as choppy, delayed input. */
    private static final long LAG_UPDATE_INTERVAL_MS = 90L;
    /** Max number of horizontal bands the text can be torn into. */
    private static final int BAND_COUNT = 8;
    private static final char[] GLITCH_GLYPHS = {
            '#', '@', '%', '&', '$', '!', '?', '*', '+', '=', '<', '>',
            '|', '/', '\\', '_', '[', ']', '░', '▒', '▓', '█'
    };
    private static final Random RNG = new Random();

    @Unique
    private long noname$lastLagUpdate;
    @Unique
    private long noname$nextBurstAt;
    @Unique
    private boolean noname$burst;
    @Unique
    private int noname$burstTicks;

    @Unique
    private int noname$lagX;
    @Unique
    private int noname$lagY;
    @Unique
    private float noname$alpha = 1.0F;
    @Unique
    private final int[] noname$bandX = new int[BAND_COUNT];
    @Unique
    private final int[] noname$bandY = new int[BAND_COUNT];

    @Inject(method = "render", at = @At("TAIL"))
    private void noname$corruptTitleVersion(GuiGraphics gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        noname$updateLag();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = font.width(VERSION_TEXT);
        int height = 9;
        int x = gui.guiWidth() - width - 4 + noname$lagX;
        int y = 4 + noname$lagY;

        boolean frameDropped = noname$burst && RNG.nextInt(6) == 0;
        if (!frameDropped) {
            // Torn bands: each horizontal strip shows its own shifted copy of
            // the text, clipped to the strip — classic VHS horizontal tear.
            int bandHeight = noname$burst ? 3 + RNG.nextInt(3) : 4 + RNG.nextInt(2);
            int bands = (height + bandHeight - 1) / bandHeight;
            for (int band = 0; band < bands; band++) {
                int bandY = y + band * bandHeight;
                int bandBottom = Math.min(y + height, bandY + bandHeight);
                int textX = x + noname$bandX[band % BAND_COUNT];
                int textY = y + noname$bandY[band % BAND_COUNT];
                int flickerColor = (int) (noname$alpha * (0.85F + RNG.nextFloat() * 0.15F) * 255.0F) << 24 | 0xFFFFFF;
                gui.enableScissor(x, bandY, x + width, bandBottom);
                gui.drawString(font, VERSION_TEXT, textX, textY, flickerColor, false);
                if (noname$burst && (band & 1) == 0) {
                    // A smeared echo of the text dragged sideways into the band.
                    gui.drawString(font, VERSION_TEXT, textX - 20 - RNG.nextInt(14), textY, 0x26FFFFFF, false);
                }
                gui.disableScissor();
            }
        }

        if (noname$burst && !frameDropped) {
            // Static bars crawling over the text.
            for (int k = 0; k < 3; k++) {
                int barX = x + RNG.nextInt(width);
                int barWidth = 2 + RNG.nextInt(10);
                int barY = y + RNG.nextInt(Math.max(height - 2, 1));
                int color = 0x26FFFFFF;
                switch (RNG.nextInt(3)) {
                    case 1 -> color = 0x2600FFFF;
                    case 2 -> color = 0x26FF00FF;
                    default -> {
                    }
                }
                gui.fill(barX, barY, barX + barWidth, barY + 2, color);
            }
        }

        noname$drawLabel(gui, font, x, y);
    }

    /**
     * The text itself, drawn with ghost copies and a garbled double during
     * corruption bursts, matching the centering-free top-right placement.
     */
    private void noname$drawLabel(GuiGraphics gui, Font font, int x, int y) {
        boolean garbled = noname$burst && RNG.nextInt(3) == 0;
        String shown = garbled ? noname$garble(VERSION_TEXT) : VERSION_TEXT;
        int color = (int) (noname$alpha * 255.0F) << 24 | 0xFFFFFF;
        if (noname$burst) {
            gui.drawString(font, VERSION_TEXT, x + 2, y, 0x40FFFFFF, false);
            gui.drawString(font, VERSION_TEXT, x - 2, y, 0x40FFFFFF, false);
            gui.drawString(font, shown, x, y + 1, 0xA0FF00FF, false);
            gui.drawString(font, shown, x, y - 1, 0xA000FFFF, false);
        }
        gui.drawString(font, shown, x, y, color, false);
    }

    /** Randomly swaps characters for glitch glyphs. */
    private static String noname$garble(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (RNG.nextInt(3) == 0) {
                chars[i] = GLITCH_GLYPHS[RNG.nextInt(GLITCH_GLYPHS.length)];
            }
        }
        return new String(chars);
    }

    /**
     * Rolls the next "lag tick": held jitter, band offsets and burst state.
     * Called at most once per {@link #LAG_UPDATE_INTERVAL_MS}, so the text
     * stutters instead of smoothly animating. Bursts trigger roughly every
     * one to two seconds and last a handful of ticks.
     */
    private void noname$updateLag() {
        long now = Util.getMillis();
        if (noname$nextBurstAt == 0L) {
            noname$nextBurstAt = now + 1000L + RNG.nextInt(1001);
        }
        if (now - noname$lastLagUpdate < LAG_UPDATE_INTERVAL_MS) {
            return;
        }
        noname$lastLagUpdate = now;

        if (noname$burst) {
            noname$burstTicks++;
            if (noname$burstTicks > 4 + RNG.nextInt(8)) {
                noname$burst = false;
                noname$burstTicks = 0;
                noname$nextBurstAt = now + 1000L + RNG.nextInt(1001);
            }
        } else if (now >= noname$nextBurstAt) {
            noname$burst = true;
            noname$burstTicks = 0;
        }

        int maxX = noname$burst ? 10 : 1;
        int maxY = noname$burst ? 4 : 1;
        for (int band = 0; band < BAND_COUNT; band++) {
            noname$bandX[band] = RNG.nextInt(2 * maxX + 1) - maxX;
            noname$bandY[band] = RNG.nextInt(2 * maxY + 1) - maxY;
        }

        if (noname$burst) {
            // Sometimes the text yanks away from its own spot for a tick.
            noname$lagX = (RNG.nextInt(3) == 0 ? 1 : 0) * (RNG.nextInt(9) - 4);
            noname$lagY = (RNG.nextInt(3) == 0 ? 1 : 0) * (RNG.nextInt(7) - 3);
        } else {
            noname$lagX = RNG.nextInt(3) - 1;
            noname$lagY = RNG.nextInt(3) - 1;
        }
        noname$alpha = noname$burst ? 0.55F + RNG.nextFloat() * 0.45F : 1.0F;
    }
}
