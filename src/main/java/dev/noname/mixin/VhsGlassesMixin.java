package dev.noname.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Always-on "old VHS, 4D-glasses" screen shader.
 *
 * <p>Vanilla {@link GameRenderer} already owns a full-screen post processor
 * (the same one the creeper/spider/enderman view filters use) that runs right
 * after the world has been rendered and right before the HUD is drawn. That
 * chain is a perfect hook for a global VHS throwback, so this mixin keeps our
 * own {@code noname:shaders/post/vhs4d} chain loaded while a world exists:
 *
 * <ul>
 *   <li>The post-effect pipeline feeds each pass a rolling {@code Time}
 *       uniform, which the fragment shader drives the static, scanlines and
 *       tracking-band wobble with.</li>
 *   <li>The chain processes the whole world render (terrain + entities),
 *       while the HUD is drawn on top afterwards — readable, like a glitch
 *       overlay rather than a broken frame.</li>
 *   <li>Reinstalled automatically: the per-frame guard reattaches the effect
 *       after a resource reload (F3+T) or whenever vanilla's own camera
 *       entity effects (creeper screen etc.) give way.</li>
 * </ul>
 *
 * <p>The effect takes a back seat to nothing else — it is always on in-game,
 * matching the always-on vibe of {@link dev.noname.client.VhsOverlay} and the
 * fog mixins.
 */
@Mixin(GameRenderer.class)
public abstract class VhsGlassesMixin {

    /** The post-processor chain to keep installed. Config lives in {@code
     *  assets/noname/shaders/post/vhs4d.json}; its pass program is resolved
     *  from {@code assets/minecraft/shaders/program/vhs4d.*} (vanilla's shader
     *  loader only looks up pass programs in the {@code minecraft} namespace). */
    private static final ResourceLocation VHS_GLASSES_EFFECT =
            ResourceLocation.fromNamespaceAndPath("noname", "shaders/post/vhs4d.json");

    @Unique
    private static boolean effectFailedOnce;

    /**
     * Every frame, make sure our post-process chain is installed. Loading is
     * one-time per chain; the guard intentionally does nothing while a world
     * is not present (menus stay clean) or while one of vanilla's own entity
     * screen filters is active (spider / creeper / enderman vibes win).
     */
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void noname$keepVhsGlassesOn(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (effectFailedOnce) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            return;
        }
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (renderer.currentEffect() != null) {
            return;
        }
        ((GameRendererAccessor) renderer).noname$loadEffect(VHS_GLASSES_EFFECT);
        if (renderer.currentEffect() == null) {
            // Never retry: if the shader chain failed to build (missing assets,
            // compile error) we don't want a warning logged every single frame.
            effectFailedOnce = true;
        }
    }
}