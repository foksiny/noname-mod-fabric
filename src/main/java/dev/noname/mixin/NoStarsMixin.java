package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * From day 2 onwards the night sky has no stars anymore: {@link
 * LevelRenderer#renderSky} only draws the star field when {@link
 * ClientLevel#getStarBrightness} is positive, so redirecting that call to
 * return 0 while the world is on day 2+ makes the vanilla star-draw skip
 * cleanly (the sun, the moon and the sky dome are untouched).
 */
@Mixin(LevelRenderer.class)
public abstract class NoStarsMixin {

    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F"))
    private float noname$noStarsFromDay2(ClientLevel level, float tickDelta) {
        if (DayCounter.currentDay(level) >= ModConfig.scaledDay(2)) {
            return 0.0F;
        }
        return level.getStarBrightness(tickDelta);
    }
}
