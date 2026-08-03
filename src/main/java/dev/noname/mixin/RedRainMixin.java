package dev.noname.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.noname.DayCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Tints every rain (and snow) quad red from day 10 onwards: the rain
 * particle texture is white, so recoloring the vertices at the buffer build
 * site makes the whole sky fall red. The alpha channel is left untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class RedRainMixin {

    @Shadow
    private Minecraft minecraft;

    @ModifyArgs(method = "renderSnowAndRain",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private void noname$makeRainRed(Args args) {
        if (DayCounter.currentDay(this.minecraft.level) < 10) {
            return;
        }
        args.set(0, 1.0F);
        args.set(1, 0.12F);
        args.set(2, 0.12F);
    }
}
