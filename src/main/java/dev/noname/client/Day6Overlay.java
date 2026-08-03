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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * The day-6 middle-of-day overlay: a fullscreen TV-static animation with a
 * sequence of red messages that appear one after another.
 *
 * <p>Rendered from the HUD callback ({@code HudRenderCallback}) at the
 * {@code guiOverlay} render type so it draws on top of every HUD element.
 */
public final class Day6Overlay {

    /** Red text colour. */
    private static final int TEXT_COLOR = 0xFFFF2222;

    /** Text scale for the messages. */
    private static final float TEXT_SCALE = 2.0F;

    /** Line height multiplier for spacing. */
    private static final float LINE_SPACING = 1.5F;

    /** Background: semi-transparent dark so the static noise is visible. */
    private static final int BG_COLOR = 0xCC000000;

    /** Size of the generated static-noise texture. */
    private static final int STATIC_SIZE = 256;

    /** Alpha of the static noise layer (more visible than VHS). */
    private static final float STATIC_ALPHA = 0.35F;

    private static final ResourceLocation STATIC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            dev.noname.Noname.MODID, "day6_static_noise");

    private static DynamicTexture staticTexture;
    private static boolean initialized;
    private static final Random RANDOM = new Random();

    private Day6Overlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!Day6Handler.isVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ensureStaticTexture();

        Font font = mc.font;
        int width = gui.guiWidth();
        int height = gui.guiHeight();

        // Fullscreen dark background.
        gui.fill(RenderType.guiOverlay(), 0, 0, width, height, BG_COLOR);

        // Crawling TV static: random u/v offset per frame makes the noise move.
        int u = RANDOM.nextInt(STATIC_SIZE);
        int v = RANDOM.nextInt(STATIC_SIZE);
        blitAlpha(gui, STATIC_TEXTURE, 0, 0, width, height, u, v,
                STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, STATIC_ALPHA);

        // Draw the current lines that have appeared.
        String[] lines = Day6Handler.getVisibleLines();
        if (lines.length == 0) {
            return;
        }

        float totalHeight = lines.length * font.lineHeight * TEXT_SCALE * LINE_SPACING;
        float startY = (height - totalHeight) / 2.0F;

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 2000.0F);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float lineWidth = font.width(line) * TEXT_SCALE;
            float x = (width - lineWidth) / 2.0F;
            float y = startY + i * font.lineHeight * TEXT_SCALE * LINE_SPACING;

            gui.pose().pushPose();
            gui.pose().translate(x, y, 0);
            gui.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            gui.drawString(font, Component.literal(line), 0, 0, TEXT_COLOR, true);
            gui.pose().popPose();
        }

        gui.pose().popPose();
    }

    /** Textured fullscreen draw that actually blends alpha. */
    private static void blitAlpha(GuiGraphics gui, ResourceLocation texture,
                                  int x, int y, int width, int height,
                                  int u, int v, int uWidth, int vHeight,
                                  int textureWidth, int textureHeight, float alpha) {
        float u0 = (float) u / textureWidth;
        float u1 = (float) (u + uWidth) / textureWidth;
        float v0 = (float) v / textureHeight;
        float v1 = (float) (v + vHeight) / textureHeight;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = gui.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(matrix, x, y + height, 0).setUv(u0, v1).setColor(1.0F, 1.0F, 1.0F, alpha);
        builder.addVertex(matrix, x + width, y + height, 0).setUv(u1, v1).setColor(1.0F, 1.0F, 1.0F, alpha);
        builder.addVertex(matrix, x + width, y, 0).setUv(u1, v0).setColor(1.0F, 1.0F, 1.0F, alpha);
        builder.addVertex(matrix, x, y, 0).setUv(u0, v0).setColor(1.0F, 1.0F, 1.0F, alpha);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void ensureStaticTexture() {
        if (initialized) {
            return;
        }
        NativeImage noise = new NativeImage(STATIC_SIZE, STATIC_SIZE, false);
        for (int x = 0; x < STATIC_SIZE; x++) {
            for (int y = 0; y < STATIC_SIZE; y++) {
                int gray = RANDOM.nextInt(256);
                noise.setPixelRGBA(x, y, 0xFF000000 | gray << 16 | gray << 8 | gray);
            }
        }
        staticTexture = new DynamicTexture(noise);
        Minecraft.getInstance().getTextureManager().register(STATIC_TEXTURE, staticTexture);
        initialized = true;
    }
}