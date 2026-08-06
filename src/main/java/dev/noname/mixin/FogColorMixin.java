package dev.noname.mixin;

import dev.noname.client.Day8SkyHandler;
import dev.noname.client.HeIsHereClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the fog red while the day-8 red-sky event is running. {@link
 * FogRenderer#setupColor} is the single place the (air) fog colour is
 * computed each frame, so tinting its result right after makes the whole
 * world — and with the minimum render distance, basically everything in view
 * — enveloped in red haze. {@link FogRenderer#setupFog} is additionally
 * pushed to a much closer start/end distance, making the fog feel heavy and
 * dense instead of just tinted.
 *
 * <p>While the "he is here" chase runs, the same two hooks instead tint the
 * fog red and pull it closer proportionally to how close the friend is
 * ({@link HeIsHereClient#intensity()}): a strong red fog that gets stronger
 * as the fake player approaches.
 */
@Mixin(FogRenderer.class)
public abstract class FogColorMixin {

    /** Fog distance (blocks) during the event: almost no visibility. */
    private static final float HEAVY_FOG_START = 4.0F;
    private static final float HEAVY_FOG_END = 18.0F;

    /** How strongly the fog is pulled toward the event colour. */
    private static final float COLOR_BLEND = 0.95F;

    /** The blood-red the fog is pulled toward. */
    private static final float TARGET_RED = 0.9F;
    private static final float TARGET_GREEN = 0.04F;
    private static final float TARGET_BLUE = 0.04F;

    /** Minimum red-fog strength during the "he is here" chase: even at 150
     *  blocks away there is a strong red haze, growing to ~1 as the friend
     *  closes in. */
    private static final float CHASE_FOG_BASE = 0.30F;

    /** Fog end distance (blocks) of the "he is here" chase: at 150 blocks it
     *  is a slightly-heavy haze, at arm's reach it closes in to a wall. */
    private static final float CHASE_FOG_END_FAR = 64.0F;
    private static final float CHASE_FOG_END_NEAR = 12.0F;

    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
            at = @At("TAIL"))
    private static void noname$redFog(Camera camera, float partialTick, ClientLevel level,
                                      int renderDistance, float darkness, CallbackInfo ci) {
        if (Day8SkyHandler.isRedSkyActive()) {
            // Pull almost all the way to blood red for a much higher contrast
            // against the normal day/night fog.
            fogRed = Mth.lerp(COLOR_BLEND, fogRed, TARGET_RED);
            fogGreen = Mth.lerp(COLOR_BLEND, fogGreen, TARGET_GREEN);
            fogBlue = Mth.lerp(COLOR_BLEND, fogBlue, TARGET_BLUE);
            return;
        }
        if (HeIsHereClient.isChaseVisible()) {
            float strength = CHASE_FOG_BASE
                    + (1.0F - CHASE_FOG_BASE) * HeIsHereClient.intensity();
            fogRed = Mth.lerp(strength, fogRed, TARGET_RED);
            fogGreen = Mth.lerp(strength, fogGreen, TARGET_GREEN);
            fogBlue = Mth.lerp(strength, fogBlue, TARGET_BLUE);
            return;
        }
    }

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
            at = @At("TAIL"))
    private static void noname$heavyFog(Camera camera, FogRenderer.FogMode fogMode,
                                        float farPlaneDistance, boolean thickFog,
                                        float partialTick, CallbackInfo ci) {
        if (Day8SkyHandler.isRedSkyActive()) {
            // Clamp the fog to a few blocks ahead, overriding whatever the biome
            // or the render distance computed — the world turns into a red wall.
            RenderSystem.setShaderFogStart(HEAVY_FOG_START);
            RenderSystem.setShaderFogEnd(HEAVY_FOG_END);
            return;
        }
        if (HeIsHereClient.isChaseVisible()) {
            float strength = CHASE_FOG_BASE
                    + (1.0F - CHASE_FOG_BASE) * HeIsHereClient.intensity();
            float end = Mth.lerp(strength, CHASE_FOG_END_FAR, CHASE_FOG_END_NEAR);
            RenderSystem.setShaderFogStart(end * 0.25F);
            RenderSystem.setShaderFogEnd(end);
            return;
        }
    }
}
