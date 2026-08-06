package dev.noname.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.noname.client.MoonInfectionHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the vanilla moon texture for the flesh-infected phase grid from
 * day 1 onwards ({@link MoonInfectionHandler} regenerates that grid per
 * day, reaching a fully infected moon on day 10).
 *
 * <p>{@link LevelRenderer#renderSky} binds the moon texture through
 * {@link RenderSystem#setShaderTexture}, the same call it uses for the sun
 * and the clouds — the handler passes every other texture through untouched
 * and only substitutes the moon location. The sun, the sky dome and the
 * moon's phases all keep working; the infected texture uses the exact same
 * 4x2 phase-cell layout.
 */
@Mixin(LevelRenderer.class)
public abstract class MoonInfectionMixin {

    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private static void noname$infectedMoonTexture(int unit, ResourceLocation location) {
        if (MoonInfectionHandler.isMoonTexture(location)) {
            RenderSystem.setShaderTexture(unit, MoonInfectionHandler.textureForCurrentDay());
        } else {
            RenderSystem.setShaderTexture(unit, location);
        }
    }
}
