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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Always-on, transparent VHS screen effect: faint crawling static noise, thin
 * scanlines, a soft vignette and a random brightness flicker — like watching
 * the session through an old camcorder tape. Subtle on purpose: everything in
 * the game stays clearly readable underneath.
 *
 * <p>Rendered from the HUD callback, below the event overlays, so the day-8
 * message and other HUD text stay readable on top. Always active, unrelated
 * to the red-sky event.
 */
public final class VhsOverlay {

    /** Size of the generated static-noise texture. */
    private static final int STATIC_SIZE = 256;

    /** Size of the generated vignette texture. */
    private static final int VIGNETTE_SIZE = 128;

    /** One dark line every N pixels inside the picture area. */
    private static final int SCANLINE_SPACING = 3;

    /** Alpha of a scanline (0x0A = ~4%). */
    private static final int SCANLINE_COLOR = 0x0A000000;

    /** Alpha of the static noise layer. */
    private static final float STATIC_ALPHA = 0.06F;

    /** Max alpha of the per-frame brightness flicker. */
    private static final float FLICKER_MAX_ALPHA = 0.04F;

    /** Alpha of the vignette at the screen corners. */
    private static final float VIGNETTE_MAX_ALPHA = 0.45F;

    private static final ResourceLocation STATIC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            dev.noname.Noname.MODID, "vhs_static");
    private static final ResourceLocation VIGNETTE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            dev.noname.Noname.MODID, "vhs_vignette");

    private static final Random RANDOM = new Random();

    private static DynamicTexture staticTexture;
    private static DynamicTexture vignetteTexture;
    private static boolean initialized;

    private VhsOverlay() {
    }

    public static void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
        if (!initialized) {
            initTextures();
        }
        int width = gui.guiWidth();
        int height = gui.guiHeight();

        // Thin dark lines across the whole picture.
        for (int y = 0; y < height; y += SCANLINE_SPACING) {
            gui.fill(0, y, width, y + 1, SCANLINE_COLOR);
        }

        // Crawling static: the random u/v offset per frame makes the noise
        // move (the coordinates wrap around the 256x256 tile).
        int u = RANDOM.nextInt(STATIC_SIZE);
        int v = RANDOM.nextInt(STATIC_SIZE);
        blitAlpha(gui, STATIC_TEXTURE, 0, 0, width, height, u, v,
                STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, STATIC_ALPHA);

        // Soft darkening toward the screen corners (alpha baked into the
        // texture: 0 in the middle, up to VIGNETTE_MAX_ALPHA at the edges).
        blitAlpha(gui, VIGNETTE_TEXTURE, 0, 0, width, height, 0, 0,
                VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, 1.0F);

        // Random brightness flicker.
        int flicker = (int) (RANDOM.nextFloat() * FLICKER_MAX_ALPHA * 255.0F) << 24;
        if (flicker != 0) {
            gui.fill(0, 0, width, height, flicker);
        }
    }

    /**
     * Textured fullscreen draw that actually blends: {@code GuiGraphics.blit}
     * renders with the plain position-tex shader and blending disabled, which
     * would stamp an opaque quad (black + alpha = black screen). This mirrors
     * the coloured inner blit path instead: position-tex-color shader, blend
     * on, per-vertex alpha.
     */
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

    /** Re-register the textures after a client resource reload (F3+T closes
     *  every registered texture). */
    public static void reinstall() {
        if (initialized) {
            Minecraft.getInstance().getTextureManager().register(STATIC_TEXTURE, staticTexture);
            Minecraft.getInstance().getTextureManager().register(VIGNETTE_TEXTURE, vignetteTexture);
        }
    }

    private static void initTextures() {
        NativeImage noise = new NativeImage(STATIC_SIZE, STATIC_SIZE, false);
        for (int x = 0; x < STATIC_SIZE; x++) {
            for (int y = 0; y < STATIC_SIZE; y++) {
                int gray = RANDOM.nextInt(256);
                noise.setPixelRGBA(x, y, 0xFF000000 | gray << 16 | gray << 8 | gray);
            }
        }
        staticTexture = new DynamicTexture(noise);
        Minecraft.getInstance().getTextureManager().register(STATIC_TEXTURE, staticTexture);

        NativeImage vignette = new NativeImage(VIGNETTE_SIZE, VIGNETTE_SIZE, false);
        for (int x = 0; x < VIGNETTE_SIZE; x++) {
            for (int y = 0; y < VIGNETTE_SIZE; y++) {
                float dx = (float) x / (VIGNETTE_SIZE - 1) * 2.0F - 1.0F;
                float dy = (float) y / (VIGNETTE_SIZE - 1) * 2.0F - 1.0F;
                // Distance from the center, 0 in the middle and 1 at the
                // corners; the alpha rises gently from a fully transparent
                // center through a smoothstep, so the dark border is a
                // continuous gradient with no hard edge — darkest at the
                // very edges, nearly clear in the middle.
                float distance = (float) Math.sqrt(dx * dx + dy * dy) / 1.41421356F;
                float t = (float) Math.min(1.0D, Math.max(0.0D, distance * distance));
                float smooth = t * t * (3.0F - 2.0F * t);
                int alpha = (int) (smooth * VIGNETTE_MAX_ALPHA * 255.0F);
                vignette.setPixelRGBA(x, y, alpha << 24);
            }
        }
        vignetteTexture = new DynamicTexture(vignette);
        Minecraft.getInstance().getTextureManager().register(VIGNETTE_TEXTURE, vignetteTexture);
        Minecraft.getInstance().getTextureManager().bindForSetup(VIGNETTE_TEXTURE);
        RenderSystem.texParameter(3553, 10241, 9729); // GL_TEXTURE_MIN_FILTER -> GL_LINEAR
        RenderSystem.texParameter(3553, 10240, 9729); // GL_TEXTURE_MAG_FILTER -> GL_LINEAR

        initialized = true;
    }
}
