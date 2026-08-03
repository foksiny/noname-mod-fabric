package dev.noname.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.noname.FakePlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * The client-side skin of the ghost player. The texture is generated
 * procedurally at runtime: every pixel is a shade of red, with mottled
 * patches, wobbly horizontal muscle-fiber striations and a fine grain, so the
 * whole body reads as raw, freshly-cut flesh.
 *
 * <p>The skin is a plain {@link PlayerSkin} pointing at
 * {@link #SKIN_TEXTURE}. The texture itself is not shipped as a normal
 * resource: on first use it is synthesised into a {@link DynamicTexture} and
 * registered with the {@link TextureManager} under that name, so any bind
 * (the 3-D player model, the tab-list head icon) resolves to it. On a client
 * resource reload (F3+T) the TextureManager drops registered textures, so
 * {@link #reinstall()} — hooked to the reload by
 * {@code dev.noname.client.NonameClient} — regenerates and re-registers it.
 * Until then (or on a dedicated server, where this class is never touched),
 * the fallback copy at
 * {@code assets/noname/textures/entity/flesh_skin.png} is used instead; it is
 * baked from the exact same generator, so both paths look identical.
 */
public final class FakeSkin {

    public static final ResourceLocation SKIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("noname", "textures/entity/flesh_skin.png");

    /** Width and height of the (WIDE-model) player skin texture. */
    private static final int SIZE = 64;

    /** Noise seeds — one per noise field so the layers are uncorrelated. */
    private static final int SEED_MOTTLE = 0x0F1E57;
    private static final int SEED_FIBERS = 0x24A9C3;
    private static final int SEED_GRAIN  = 0x5E71D0;
    private static final int SEED_VEINS  = 0x8B3A22;

    /** Base flesh color: a deep raw-muscle red, modulated per pixel. */
    private static final int BASE_R = 140;
    private static final int BASE_G = 27;
    private static final int BASE_B = 31;

    private static PlayerSkin skin;

    /** The installed dynamic texture; {@code null} until first installation. */
    private static DynamicTexture texture;

    private FakeSkin() {
    }

    public static PlayerSkin get() {
        if (skin == null) {
            ensureInstalled();
            skin = new PlayerSkin(
                    SKIN_TEXTURE,
                    null,   // no external url
                    null,   // no cape
                    null,   // no elytra
                    PlayerSkin.Model.WIDE,
                    true    // marked "secure" so the tab-list head icon keeps
                            // it: PlayerInfo.createSkinLookup() falls back to
                            // the default skin for remote players whose skin
                            // is not session-server-verified
            );
        }
        return skin;
    }

    /** {@return true if the profile belongs to the ghost player} */
    public static boolean isGhostProfile(com.mojang.authlib.GameProfile profile) {
        return FakePlayerUtil.FAKE_UUID.equals(profile.getId());
    }

    /**
     * Synthesises the flesh texture and registers it with the
     * {@link TextureManager} under {@link #SKIN_TEXTURE}, so every bind of the
     * skin resolves to it. Idempotent.
     */
    public static void ensureInstalled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || texture != null) {
            return;
        }
        texture = new DynamicTexture(generateFleshSkin());
        mc.getTextureManager().register(SKIN_TEXTURE, texture);
    }

    /**
     * Regenerates the texture after a client resource reload (F3+T), which
     * makes the {@link TextureManager} close and forget every registered
     * texture. Called by the reload listener registered in
     * {@code dev.noname.client.NonameClient}.
     */
    public static void reinstall() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        TextureManager manager = mc.getTextureManager();
        if (texture != null) {
            manager.release(SKIN_TEXTURE);
            texture = null;
        }
        ensureInstalled();
    }

    /** {@return a 64×64 player-skin image that is all red flesh}. */
    private static NativeImage generateFleshSkin() {
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixelRGBA(x, y, fleshPixel(x, y));
            }
        }
        return image;
    }

    /**
     * One flesh pixel: the base muscle-red scaled by four layered noise
     * fields — large mottled patches, wobbly horizontal striations (the
     * "grain" of the meat), softer vertical fibers and a fine grain — plus
     * darker blood-vessel webs. Every channel moves together, so the result
     * stays strictly in the red family.
     */
    private static int fleshPixel(int x, int y) {
        float px = x;
        float py = y;

        float mottle = fbm(px / 8.0F, py / 8.0F, 3, SEED_MOTTLE);
        float fibers = fbm(px / 2.0F, py / 6.0F, 2, SEED_FIBERS);
        float grain  = fbm(px * 1.5F, py * 1.5F, 2, SEED_GRAIN);
        float veins  = fbm(px * 2.2F, py * 2.2F, 2, SEED_VEINS);

        // Horizontal fiber bands, warped by the mottle so they never look
        // like a printed grid.
        float stripe = 0.5F + 0.5F * (float) Math.sin(py * 0.85F + mottle * 7.0F);

        float bright = (0.62F + 0.38F * mottle)      // big patches: 0.62..1.00
                * (0.85F + 0.30F * stripe)           // striations:  0.85..1.15
                * (0.85F + 0.30F * fibers)           // vertical:    0.85..1.15
                * (0.92F + 0.16F * grain);           // grain:       0.92..1.08
        // Blood vessels: only the darkest vein-noise pixels get darker.
        bright *= 0.82F + 0.18F * clamp01((veins - 0.18F) / 0.6F);

        int r = Math.round(BASE_R * bright);
        int g = Math.round(BASE_G * bright);
        int b = Math.round(BASE_B * bright);
        // NativeImage stores pixels in ABGR order (vanilla's LightTexture
        // packs the same way); packing R into the low byte is what makes
        // the texture render red instead of swapped-blue.
        return 0xFF000000
                | (clamp255(b) << 16)
                | (clamp255(g) << 8)
                | clamp255(r);
    }

    /** Deterministic value noise: one value per grid cell, bilinearly
     *  interpolated with a smoothstep, so the skin is identical on every
     *  machine and reload. */
    private static float valueNoise(float fx, float fy, int seed) {
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float tx = fx - x0;
        float ty = fy - y0;
        tx = tx * tx * (3.0F - 2.0F * tx);
        ty = ty * ty * (3.0F - 2.0F * ty);
        float n00 = hash2(x0, y0, seed);
        float n10 = hash2(x0 + 1, y0, seed);
        float n01 = hash2(x0, y0 + 1, seed);
        float n11 = hash2(x0 + 1, y0 + 1, seed);
        return lerp(lerp(n00, n10, tx), lerp(n01, n11, tx), ty);
    }

    /** Fractional Brownian motion — several octaves of {@link #valueNoise}
     *  summed with halved amplitude, so features appear at every scale. */
    private static float fbm(float fx, float fy, int octaves, int seed) {
        float sum = 0.0F;
        float amp = 0.5F;
        float total = 0.0F;
        for (int o = 0; o < octaves; o++) {
            sum += amp * valueNoise(fx, fy, seed + o * 101);
            total += amp;
            fx *= 2.0F;
            fy *= 2.0F;
            amp *= 0.5F;
        }
        return sum / total;
    }

    /** Integer-hash → pseudo-random value in {@code [0, 1]}. */
    private static float hash2(int x, int y, int seed) {
        int h = seed * 0x9E3779B1 + x * 0x85EBCA6B + y * 0xC2B2AE35;
        h = (h ^ (h >>> 13)) * 0x27D4EB2D;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0F;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
