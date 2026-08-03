package dev.noname.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.noname.FakePlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * The client-side skin of the day-2 {@code null} visitor: a completely
 * flat, opaque black 64×64 texture. Identical in shape to vanilla's
 * wide-model player skin, so the same texture serves both the 3-D player
 * model (which the {@code null} ghost never renders, since its entity is
 * discarded the moment it joins) and the tab-list head icon shown for the
 * ~3 seconds it appears in the player list.
 *
 * <p>Like {@link FakeSkin}, the texture is synthesised at runtime as a
 * {@link DynamicTexture} registered with the {@link TextureManager} under
 * {@link #SKIN_TEXTURE}, and rebuild on a client resource reload (F3+T)
 * via {@link #reinstall()} (hooked in {@code dev.noname.client.NonameClient}).
 * A virtually-identical fallback PNG ships at
 * {@code assets/noname/textures/entity/null_skin.png}, so any bind of the
 * skin resolves to black even before the runtime texture is installed (or on
 * a dedicated server, where this class is never touched).
 */
public final class NullSkin {

    public static final ResourceLocation SKIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("noname", "textures/entity/null_skin.png");

    /** Width and height of the (WIDE-model) player skin texture. */
    private static final int SIZE = 64;

    /** Solid opaque black, packed in NativeImage's ABGR order with full
     *  alpha (so the pixel is fully opaque, not transparent). */
    private static final int BLACK_RGBA = 0xFF000000;

    private static PlayerSkin skin;

    /** The installed dynamic texture; {@code null} until first installation. */
    private static DynamicTexture texture;

    private NullSkin() {
    }

    public static PlayerSkin get() {
        if (skin == null) {
            ensureInstalled();
            skin = new PlayerSkin(
                    SKIN_TEXTURE,
                    null,       // no external url
                    null,       // no cape
                    null,       // no elytra
                    PlayerSkin.Model.WIDE,
                    true        // marked "secure" so PlayerInfo.createSkinLookup()
                                // keeps this skin instead of falling back to the
                                // default skin for the unverified remote profile
            );
        }
        return skin;
    }

    /** {@return true if the profile belongs to the day-2 {@code null}
     *      visitor} */
    public static boolean isNullProfile(com.mojang.authlib.GameProfile profile) {
        return FakePlayerUtil.NULL_UUID.equals(profile.getId());
    }

    /**
     * Synthesises the solid black texture and registers it with the
     * {@link TextureManager} under {@link #SKIN_TEXTURE}, so every bind of
     * the skin (the 3-D model, the tab-list head icon) resolves to it.
     * Idempotent.
     */
    public static void ensureInstalled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || texture != null) {
            return;
        }
        texture = new DynamicTexture(generateBlackSkin());
        mc.getTextureManager().register(SKIN_TEXTURE, texture);
    }

    /**
     * Regenerates the texture after a client resource reload (F3+T), which
     * makes the {@link TextureManager} close and forget every registered
     * texture. Called by the reload listener registered in
     * {@code dev.noname.client.NonameClient}, alongside
     * {@link FakeSkin#reinstall()}.
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

    /** {@return a 64×64 fully-opaque black player-skin image}. */
    private static NativeImage generateBlackSkin() {
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixelRGBA(x, y, BLACK_RGBA);
            }
        }
        return image;
    }
}
