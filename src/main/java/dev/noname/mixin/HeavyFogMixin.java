package dev.noname.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.noname.client.Day8SkyHandler;
import dev.noname.client.HeIsHereClient;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the always-on fog with Minecraft alpha's distance-fog system.
 *
 * <p>Back in alpha, fog was not per-biome or per-block ambient — it was one
 * uniform regional haze that knew almost nothing about the ground below it.
 * The colour was a cool, pale blue that got deeper and smokier at night, it
 * started just a few blocks in front of the camera, and it closed into a solid
 * wall of haze well before the far plane, so the horizon read as a soft blue
 * dome instead of a sky-blended fade. That is exactly what this mixin rebuilds
 * for the Overworld:
 *
 * <ul>
 *   <li>{@link FogRenderer#setupColor} — the fog colour is recomputed every
 *       frame from the sun angle: pale, washed-out blue during the day, deep
 *       indigo at night, blended hard toward that palette and away from the
 *       per-biome ambient colours (the "haze tints everything" alpha look).</li>
 *   <li>{@link FogRenderer#setupFog} — fog thickness is tied to the render
 *       distance: it starts dense close to the player and reaches full opacity
 *       about halfway across the draw distance, so the world always ends in a
 *       wall of haze rather than a hard chunk edge.</li>
 * </ul>
 *
 * <p>During the day-8 red-sky event and the "he is here" chase this mixin
 * stands down so those events can clamp their own fog.
 */
@Mixin(FogRenderer.class)
public abstract class HeavyFogMixin {

    /** Where the haze starts eating into the view, as a fraction of the
     *  render distance: the fog begins almost at the player's feet. */
    private static final float FOG_START_RATIO = 0.04F;

    /** Where the haze becomes a solid, opaque wall, as a fraction of the
     *  render distance: half-way out, everything further is soup. */
    private static final float FOG_END_RATIO = 0.5F;

    /** Day palette: pale sky blue. */
    private static final float DAY_RED = 0.6F;
    private static final float DAY_GREEN = 0.8F;
    private static final float DAY_BLUE = 1.0F;

    /** Dusk palette: yellow-orange haze while the sun sits on the horizon. */
    private static final float DUSK_RED = 1.0F;
    private static final float DUSK_GREEN = 0.68F;
    private static final float DUSK_BLUE = 0.3F;

    /** Night palette: solid black fog. */
    private static final float NIGHT_RED = 0.0F;
    private static final float NIGHT_GREEN = 0.0F;
    private static final float NIGHT_BLUE = 0.0F;

    /** How strongly the fog is pulled toward the alpha palette (1.0 would
     *  erase every biome tint completely). */
    private static final float PALETTE_BLEND = 0.9F;

    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
            at = @At("TAIL"))
    private static void noname$alphaFogColor(Camera camera, float partialTick, ClientLevel level,
                                             int renderDistance, float darkness, CallbackInfo ci) {
        if (Day8SkyHandler.isRedSkyActive() || HeIsHereClient.isChaseVisible()) {
            return; // the red-sky event and the chase clamp the fog themselves
        }
        if (level.dimension() != Level.OVERWORLD) {
            return; // keep the End/Nether on their own skies
        }

        // Day factor from the sun angle: 1 at noon, 0 once the sun is below
        // the horizon. Alpha fog shaded with the time of day, nothing else.
        float sunAngle = level.getSunAngle(partialTick);
        float c = Mth.cos(sunAngle);
        float day = Mth.clamp(c, 0.0F, 1.0F);

        // Dusk factor: peaks while the sun hugs the horizon, so the fog turns
        // yellow-orange around sunset before falling back to black at night.
        float dusk = Mth.clamp(1.0F - Mth.abs(c) * 1.5F, 0.0F, 1.0F);

        float eveningR = Mth.lerp(dusk, NIGHT_RED, DUSK_RED);
        float eveningG = Mth.lerp(dusk, NIGHT_GREEN, DUSK_GREEN);
        float eveningB = Mth.lerp(dusk, NIGHT_BLUE, DUSK_BLUE);

        float targetR = Mth.lerp(day, eveningR, DAY_RED);
        float targetG = Mth.lerp(day, eveningG, DAY_GREEN);
        float targetB = Mth.lerp(day, eveningB, DAY_BLUE);

        fogRed = Mth.lerp(PALETTE_BLEND, fogRed, targetR);
        fogGreen = Mth.lerp(PALETTE_BLEND, fogGreen, targetG);
        fogBlue = Mth.lerp(PALETTE_BLEND, fogBlue, targetB);
    }

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
            at = @At("TAIL"))
    private static void noname$alphaFogDistance(Camera camera, FogRenderer.FogMode fogMode,
                                                float farPlaneDistance, boolean thickFog,
                                                float partialTick, CallbackInfo ci) {
        if (Day8SkyHandler.isRedSkyActive() || HeIsHereClient.isChaseVisible()) {
            return; // the red-sky event and the chase clamp the fog themselves
        }
        if (camera.getEntity().level().dimension() != Level.OVERWORLD) {
            return;
        }
        float fogEnd = farPlaneDistance * FOG_END_RATIO;
        float fogStart = farPlaneDistance * FOG_START_RATIO;
        if (fogEnd <= fogStart) {
            fogStart = Math.max(0.0F, fogEnd * 0.3F); // guard tiny render distances
        }
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
    }
}