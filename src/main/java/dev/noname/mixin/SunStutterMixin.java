package dev.noname.mixin;

import dev.noname.client.SunGlitchHandler;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Makes the sun lag, stutter and teleport from day 11 onwards.
 *
 * <p>{@code LevelRenderer.renderSky} rotates the sky by the time of day and
 * draws the sun as a 60x60 quad at {@code y = 100} (vertices
 * {@code (±30, 100, ±30)}), the moon at {@code y = -100} and a soft glow
 * fan centred on {@code (0, 100, 0)}. Every {@code addVertex} call in the
 * method passes through this mixin, but only the four sun-quad vertices
 * match {@link SunGlitchHandler#isSunVertex}'s filter — the moon quad and
 * the glow fan are passed through untouched.
 *
 * <p>The displacement itself lives in {@link SunGlitchHandler}: the quad is
 * rotated back along the sky arc by the current lag angle (so the sun
 * renders at an earlier sky position, snapping forward whenever the lag
 * redraws — the stutter), plus a jitter that grows with the day. All four
 * vertices of one frame see the same snapshot, so the sun moves as a rigid
 * quad.
 */
@Mixin(LevelRenderer.class)
public abstract class SunStutterMixin {

    @ModifyArgs(method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;addVertex(Lorg/joml/Matrix4f;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static void noname$stutterSun(Args args) {
        float x = args.get(1);
        float y = args.get(2);
        float z = args.get(3);
        if (!SunGlitchHandler.isSunVertex(x, y, z)) {
            return;
        }
        args.set(1, SunGlitchHandler.displacedX(x));
        args.set(2, SunGlitchHandler.displacedY(y, z));
        args.set(3, SunGlitchHandler.displacedZ(y, z));
    }
}
