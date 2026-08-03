package dev.noname.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The "he is here" chase visuals, drawn on top of everything while
 * {@link HeIsHereClient#isChaseVisible()} is true:
 *
 * <ul>
 *   <li><b>White texts</b> — 3 to 5 of the {@link HeIsHereClient#PHRASES}
 *       phrases at a time, all the same size, at random places on the
 *       screen, re-rolled every few ticks so they "rapidly show".</li>
 *   <li><b>Red top text</b> — "he's &lt;blocks&gt; away from you", in red,
 *       shaking a little, above the screen.</li>
 *   <li><b>Blood splashes</b> — soft red blotches that appear and disappear
 *       around the screen; the closer the friend, the more there are
 *       ({@link HeIsHereClient#intensity()}).</li>
 * </ul>
 */
public final class HeIsHereOverlay {

    /** Color of the red "he's X away from you" text. */
    private static final int RED_TEXT_COLOR = 0xFFFF2222;

    /** Color of the white texts. */
    private static final int WHITE_TEXT_COLOR = 0xFFFFFFFF;

    /** How big the red distance text is drawn. */
    private static final float RED_TEXT_SCALE = 1.6F;

    /** How far the red text shakes from its anchor, in pixels. */
    private static final float SHAKE_RADIUS = 3.0F;

    /** How often the white texts re-roll to new phrases/positions, in ticks. */
    private static final int TEXT_REROLL_INTERVAL_TICKS = 3;

    /** How many white texts are shown at once (3 to 5). */
    private static final int MIN_TEXTS = 3;
    private static final int MAX_TEXTS = 5;

    /** Session tick of the last white-text re-roll. */
    private static int lastRerollTick = -1;

    /** The white texts currently shown: [phrase, x, y]. */
    private static final float[][] texts = new float[MAX_TEXTS][3];

    /** Count of currently shown white texts. */
    private static int textCount = 0;

    /** Generated soft red circle texture for the blood splashes. */
    private static final int SPLASH_SIZE = 64;
    private static final ResourceLocation SPLASH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            dev.noname.Noname.MODID, "he_is_here_splash");
    private static DynamicTexture splashTexture;
    private static boolean splashInit;

    private HeIsHereOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!HeIsHereClient.isChaseVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ensureSplashTexture();

        int width = gui.guiWidth();
        int height = gui.guiHeight();
        float intensity = HeIsHereClient.intensity();
        Font font = mc.font;

        // The red "he's X blocks away from you" text, shaking above the screen.
        Component distanceText = Component.literal("he's "
                + HeIsHereClient.distance() + " blocks away from you");
        float redTextWidth = font.width(distanceText) * RED_TEXT_SCALE;
        float shakeX = (HeIsHereClient.RANDOM.nextFloat() * 2.0F - 1.0F) * SHAKE_RADIUS;
        float shakeY = (HeIsHereClient.RANDOM.nextFloat() * 2.0F - 1.0F) * SHAKE_RADIUS;
        gui.pose().pushPose();
        gui.pose().translate((width - redTextWidth) / 2.0F + shakeX, 12.0F + shakeY, 2000.0F);
        gui.pose().scale(RED_TEXT_SCALE, RED_TEXT_SCALE, 1.0F);
        gui.drawString(font, distanceText, 0, 0, RED_TEXT_COLOR, true);
        gui.pose().popPose();

        // The white texts: 3-5 at random places, re-rolled every few ticks.
        rerollTexts(mc, width, height, font);
        for (int i = 0; i < textCount; i++) {
            String phrase = HeIsHereClient.PHRASES[(int) texts[i][0]];
            gui.drawString(font, Component.literal(phrase),
                    (int) texts[i][1], (int) texts[i][2], WHITE_TEXT_COLOR, true);
        }

        // The blood splashes, more and stronger the closer the friend is.
        for (HeIsHereClient.Splash splash : HeIsHereClient.splashes) {
            blitSplash(gui, splash.x - splash.size / 2.0F, splash.y - splash.size / 2.0F,
                    splash.size, splash.alpha() * (0.4F + 0.6F * intensity));
        }
    }

    /** Re-rolls the white texts (new phrases at new random places) every few
     *  ticks. */
    private static void rerollTexts(Minecraft mc, int width, int height, Font font) {
        int tick = HeIsHereClient.sessionTick();
        if (tick - lastRerollTick < TEXT_REROLL_INTERVAL_TICKS) {
            return;
        }
        lastRerollTick = tick;
        textCount = MIN_TEXTS + HeIsHereClient.RANDOM.nextInt(MAX_TEXTS - MIN_TEXTS + 1);
        for (int i = 0; i < textCount; i++) {
            texts[i][0] = HeIsHereClient.RANDOM.nextInt(HeIsHereClient.PHRASES.length);
            // Keep the phrase within the screen; a margin so it is readable.
            texts[i][1] = HeIsHereClient.RANDOM.nextInt(Math.max(1, width - 120));
            texts[i][2] = HeIsHereClient.RANDOM.nextInt(Math.max(1, height - 40));
        }
    }

    private static String[] phrases() {
        return HeIsHereClient.PHRASES;
    }

    /** Draws one soft red circle at the given screen position. */
    private static void blitSplash(GuiGraphics gui, float x, float y, float size, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        RenderSystem.setShaderTexture(0, SPLASH_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = gui.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        float u0 = 0.0F;
        float u1 = 1.0F;
        float v0 = 0.0F;
        float v1 = 1.0F;
        builder.addVertex(matrix, x, y + size, 0).setUv(u0, v1).setColor(1.0F, 0.1F, 0.1F, alpha);
        builder.addVertex(matrix, x + size, y + size, 0).setUv(u1, v1).setColor(1.0F, 0.1F, 0.1F, alpha);
        builder.addVertex(matrix, x + size, y, 0).setUv(u1, v0).setColor(1.0F, 0.1F, 0.1F, alpha);
        builder.addVertex(matrix, x, y, 0).setUv(u0, v0).setColor(1.0F, 0.1F, 0.1F, alpha);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** Generates the soft red circle texture the splashes are drawn with:
     *  fully red in the middle, transparent at the edge, with a smooth
     *  falloff so the splashes look shaded instead of flat. */
    private static void ensureSplashTexture() {
        if (splashInit) {
            return;
        }
        NativeImage image = new NativeImage(SPLASH_SIZE, SPLASH_SIZE, true);
        for (int x = 0; x < SPLASH_SIZE; x++) {
            for (int y = 0; y < SPLASH_SIZE; y++) {
                float dx = (float) x / (SPLASH_SIZE - 1) * 2.0F - 1.0F;
                float dy = (float) y / (SPLASH_SIZE - 1) * 2.0F - 1.0F;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float t = Math.min(1.0F, distance);
                float falloff = 1.0F - t * t * (3.0F - 2.0F * t);
                int alpha = (int) (falloff * 255.0F);
                // Red with a slight darkening at the core (NativeImage stores
                // pixels in ABGR order, R in the low byte).
                image.setPixelRGBA(x, y, 0xFF000000
                        | alpha << 24
                        | (int) (alpha * 0.25F) << 8
                        | alpha);
            }
        }
        splashTexture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(SPLASH_TEXTURE, splashTexture);
        splashInit = true;
    }

    /** Re-register the splash texture after a client resource reload (F3+T). */
    public static void reinstall() {
        if (splashInit) {
            Minecraft.getInstance().getTextureManager().register(SPLASH_TEXTURE, splashTexture);
        }
    }
}
