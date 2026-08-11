package dev.noname.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.noname.client.MoonInfectionHandler;
import dev.noname.client.SunGlitchHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the sun and moon textures while {@link LevelRenderer#renderSky}
 * draws the celestial bodies, and applies the sun's glitch tint.
 *
 * <p>{@code renderSky} binds both textures through the same
 * {@link RenderSystem#setShaderTexture} call, so one redirect handles the
 * two sky bodies:
 * <ul>
 *   <li><b>Sun</b> — replaced by {@link SunGlitchHandler}'s procedurally
 *       corrupted texture once the day-11 gate opens, and tinted through
 *       {@link RenderSystem#setShaderColor} (the vanilla sun quad is drawn
 *       with whatever shader color is active, so the tint rides on top of
 *       the corruption; the rain fade is preserved in the alpha channel).
 *   <li><b>Moon</b> — swapped for the flesh-infected phase grid from day 1
 *       onwards (the pre-existing {@link MoonInfectionHandler} feature),
 *       and the shader color is reset to vanilla (with the rain fade) so
 *       the sun glitch's tint never bleeds onto the moon.</li>
 * </ul>
 *
 * <p>The clouds, the sky dome and the stars never pass through this call,
 * so they stay untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class SkyTextureSwapMixin {

    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private static void noname$swapSkyTextures(int unit, ResourceLocation location) {
        if (SunGlitchHandler.isSunTexture(location)) {
            RenderSystem.setShaderTexture(unit, SunGlitchHandler.textureForThisFrame());
            SunGlitchHandler.applySunTint();
        } else if (MoonInfectionHandler.isMoonTexture(location)) {
            // The sun glitch tints the quad through the shader color; reset
            // it (plus the vanilla rain fade) before the moon draws.
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, SunGlitchHandler.rainFade());
            RenderSystem.setShaderTexture(unit, MoonInfectionHandler.textureForCurrentDay());
        } else {
            RenderSystem.setShaderTexture(unit, location);
        }
    }
}
