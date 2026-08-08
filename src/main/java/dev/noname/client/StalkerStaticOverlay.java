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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * The red-static flash of the day-13+ stalker catch: for 0.1 seconds the
 * whole screen fills with TV static tinted blood red — the same crawling
 * noise {@link Day6Overlay} uses, but short and red instead of dark and
 * long.
 *
 * <p>Rendered from the HUD callback ({@code HudRenderCallback}) at the
 * {@code guiOverlay} render type so it draws on top of every HUD element.
 */
public final class StalkerStaticOverlay {

    /** Background: semi-transparent dark red under the noise. */
    private static final int BG_COLOR = 0x66000000;

    /** Size of the generated static-noise texture. */
    private static final int STATIC_SIZE = 256;

    /** Alpha of the static noise layer. */
    private static final float STATIC_ALPHA = 0.9F;

    private static final ResourceLocation STATIC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            dev.noname.Noname.MODID, "day13_static_noise");

    private static DynamicTexture staticTexture;
    private static boolean initialized;
    private static final Random RANDOM = new Random();

    private StalkerStaticOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!StalkerStaticHandler.isVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ensureStaticTexture();

        int width = gui.guiWidth();
        int height = gui.guiHeight();

        // Dark backdrop so the red noise pops.
        gui.fill(RenderType.guiOverlay(), 0, 0, width, height, BG_COLOR);

        // Crawling TV static: random u/v offset per frame makes the noise
        // move; the noise quad is tinted blood red.
        int u = RANDOM.nextInt(STATIC_SIZE);
        int v = RANDOM.nextInt(STATIC_SIZE);
        blitTinted(gui, STATIC_TEXTURE, 0, 0, width, height, u, v,
                STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, STATIC_SIZE,
                1.0F, 0.15F, 0.15F, STATIC_ALPHA);
    }

    /** Textured fullscreen draw that tints the texture red. */
    private static void blitTinted(GuiGraphics gui, ResourceLocation texture,
                                   int x, int y, int width, int height,
                                   int u, int v, int uWidth, int vHeight,
                                   int textureWidth, int textureHeight,
                                   float red, float green, float blue, float alpha) {
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
        builder.addVertex(matrix, x, y + height, 0).setUv(u0, v1)
                .setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x + width, y + height, 0).setUv(u1, v1)
                .setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x + width, y, 0).setUv(u1, v0)
                .setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x, y, 0).setUv(u0, v0)
                .setColor(red, green, blue, alpha);
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
