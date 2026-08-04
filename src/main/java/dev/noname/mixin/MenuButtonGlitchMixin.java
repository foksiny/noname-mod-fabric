package dev.noname.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;
import java.util.Set;

/**
 * The menu buttons that would let the player leave render as if the game
 * itself is struggling: "Quit Game" and "Singleplayer" on the title screen,
 * and every button of the pause screen (the ESC menu inside a world).
 *
 * <p>The button stutters in place, updated only a few times per second so it
 * feels laggy, its sprite is torn into horizontal bands that drift and
 * smear, and every so often it bursts into heavy corruption — teleporting,
 * flickering, spawning static bars and garbling its own label. Each button
 * runs its own corruption schedule, so the pause menu's buttons glitch out
 * of sync with each other.
 *
 * <p>Only the drawing is touched (the render call is cancelled and re-drawn
 * by hand); the hitbox, hover state and the actual click action are vanilla,
 * so the buttons still work.
 */
@Mixin(AbstractButton.class)
public abstract class MenuButtonGlitchMixin {

    /** Max number of horizontal bands a button can be torn into (a 20px
     *  button with 3px bands needs 7; 8 covers any sane case). */
    private static final int BAND_COUNT = 8;
    /** Time a "lag tick" takes: offsets are held for this long instead of
     *  being re-rolled every frame, which reads as choppy, delayed input. */
    private static final long LAG_UPDATE_INTERVAL_MS = 90L;
    /** Title-screen buttons that are always glitched, matched by their
     *  vanilla translation key so any language works. */
    private static final Set<String> ALWAYS_GLITCHY_KEYS = Set.of("menu.quit", "menu.singleplayer");
    private static final char[] GLITCH_GLYPHS = {
            '#', '@', '%', '&', '$', '!', '?', '*', '+', '=', '<', '>',
            '|', '/', '\\', '_', '[', ']', '░', '▒', '▓', '█'
    };
    private static final Random RNG = new Random();

    @Unique
    private boolean noname$glitchButton;
    @Unique
    private boolean noname$glitchButtonChecked;

    @Unique
    private long noname$lastLagUpdate;
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

    /**
     * Replaces the vanilla render for glitched menu buttons with the
     * corrupted one; every other button renders normally.
     */
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void noname$corruptMenuButton(GuiGraphics gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!noname$isGlitchButton()) {
            return;
        }
        ci.cancel();
        noname$updateLag();

        AbstractButton button = (AbstractButton) (Object) this;
        WidgetSprites sprites = AbstractButtonAccessor.noname$getSprites();
        ResourceLocation sprite = sprites.get(button.active, button.isHoveredOrFocused());

        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        gui.pose().pushPose();
        // The whole button is drawn offset by the current lag.
        gui.pose().translate(noname$lagX, noname$lagY, 0.0F);

        boolean frameDropped = noname$burst && RNG.nextInt(6) == 0;
        if (!frameDropped) {
            // Torn bands: each horizontal strip shows its own shifted copy of
            // the button, clipped to the strip — classic VHS horizontal tear.
            int bandHeight = noname$burst ? 3 + RNG.nextInt(5) : 4 + RNG.nextInt(4);
            int bands = (height + bandHeight - 1) / bandHeight;
            for (int band = 0; band < bands; band++) {
                int bandY = y + band * bandHeight;
                int bandBottom = Math.min(y + height, bandY + bandHeight);
                gui.enableScissor(x, bandY, x + width, bandBottom);
                gui.setColor(1.0F, 1.0F, 1.0F, noname$alpha * (0.85F + RNG.nextFloat() * 0.15F));
                gui.blitSprite(sprite, x + noname$bandX[band % BAND_COUNT],
                        y + noname$bandY[band % BAND_COUNT], width, height);
                if (noname$burst && (band & 1) == 0) {
                    // A smeared echo of the button dragged sideways into the band.
                    gui.blitSprite(sprite, x + noname$bandX[band % BAND_COUNT] - 32 - RNG.nextInt(24),
                            y + noname$bandY[band % BAND_COUNT], width, height);
                }
                gui.disableScissor();
            }
        }
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (noname$burst && !frameDropped) {
            // Static bars crawling over the button.
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

        noname$drawLabel(gui, button, x, y, width, height);
        gui.pose().popPose();
    }

    /**
     * The label, drawn with the same centering as vanilla, plus ghost copies
     * and a garbled double during corruption bursts.
     */
    private void noname$drawLabel(GuiGraphics gui, AbstractButton button, int x, int y, int width, int height) {
        Font font = Minecraft.getInstance().font;
        String label = button.getMessage().getString();
        boolean garbled = noname$burst && RNG.nextInt(3) == 0;
        String shown = garbled ? noname$garble(label) : label;
        int textX = x + (width - font.width(shown)) / 2;
        int textY = y + (height - 9) / 2 + 1;
        int color = button.active ? 0xFFFFFFFF : 0xA0A0A0FF;
        if (noname$burst) {
            gui.drawString(font, label, textX + 2, textY, 0x40FFFFFF);
            gui.drawString(font, label, textX - 2, textY, 0x40FFFFFF);
            gui.drawString(font, shown, textX, textY + 1, 0xA0FF00FF);
            gui.drawString(font, shown, textX, textY - 1, 0xA000FFFF);
        }
        gui.drawString(font, shown, textX, textY, color);
    }

    /** Randomly swaps label characters for glitch glyphs. */
    private static String noname$garble(String label) {
        char[] chars = label.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (RNG.nextInt(3) == 0) {
                chars[i] = GLITCH_GLYPHS[RNG.nextInt(GLITCH_GLYPHS.length)];
            }
        }
        return new String(chars);
    }

    /**
     * Rolls the next "lag tick": held jitter, band offsets and burst state.
     * Called at most once per {@link #LAG_UPDATE_INTERVAL_MS}, so the button
     * stutters instead of smoothly animating.
     */
    private void noname$updateLag() {
        long now = Util.getMillis();
        if (now - noname$lastLagUpdate < LAG_UPDATE_INTERVAL_MS) {
            return;
        }
        noname$lastLagUpdate = now;

        if (noname$burst) {
            noname$burstTicks++;
            if (noname$burstTicks > 10 + RNG.nextInt(18) || RNG.nextInt(10) == 0) {
                noname$burst = false;
                noname$burstTicks = 0;
            }
        } else if (RNG.nextInt(150) == 0) {
            noname$burst = true;
            noname$burstTicks = 0;
        }

        int maxX = noname$burst ? 14 : 2;
        int maxY = noname$burst ? 8 : 1;
        for (int band = 0; band < BAND_COUNT; band++) {
            noname$bandX[band] = RNG.nextInt(2 * maxX + 1) - maxX;
            noname$bandY[band] = RNG.nextInt(2 * maxY + 1) - maxY;
        }

        if (noname$burst) {
            // Sometimes the button yanks away from its own spot for a tick.
            noname$lagX = (RNG.nextInt(3) == 0 ? 1 : 0) * (RNG.nextInt(57) - 28);
            noname$lagY = (RNG.nextInt(3) == 0 ? 1 : 0) * (RNG.nextInt(45) - 22);
        } else {
            noname$lagX = RNG.nextInt(3) - 1;
            noname$lagY = RNG.nextInt(3) - 1;
        }
        noname$alpha = noname$burst ? 0.6F + RNG.nextFloat() * 0.4F : 1.0F;
    }

    /**
     * Caches whether this widget is a glitchy menu button. Every button of
     * the pause screen qualifies while that screen is open; the title
     * screen's "Quit Game" and "Singleplayer" qualify by their vanilla
     * translation key ({@code menu.quit} / {@code menu.singleplayer}), so
     * any language works.
     */
    private boolean noname$isGlitchButton() {
        if (!noname$glitchButtonChecked) {
            noname$glitchButtonChecked = true;
            if (Minecraft.getInstance().screen instanceof PauseScreen) {
                noname$glitchButton = true;
                return true;
            }
            Component message = ((AbstractButton) (Object) this).getMessage();
            if (message instanceof MutableComponent mutable
                    && mutable.getContents() instanceof TranslatableContents contents) {
                noname$glitchButton = ALWAYS_GLITCHY_KEYS.contains(contents.getKey());
            }
        }
        return noname$glitchButton;
    }
}
