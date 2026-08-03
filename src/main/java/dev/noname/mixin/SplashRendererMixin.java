package dev.noname.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.gui.components.SplashRenderer.class)
public abstract class SplashRendererMixin {
    @Shadow
    @Final
    private String splash;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void noname$splashColors(net.minecraft.client.gui.GuiGraphics gui,
                                      int width,
                                      net.minecraft.client.gui.Font font,
                                      int alpha, CallbackInfo ci) {
        if ("blood".equals(this.splash)) {
            ci.cancel();
            // Replicate vanilla splash position/math but render in red.
            float scaleFactor = 1.8F
                    - Math.abs((float) Math.sin(((net.minecraft.Util.getMillis() % 1000L) / 1000.0F) * 2.0F * (float) Math.PI) * 0.1F);
            float scale = (scaleFactor * 100.0F) / (font.width(this.splash) + 32.0F);
            float xCenter = (float) width / 2.0F + 123.0F;
            float yCenter = 69.0F;
            gui.pose().pushPose();
            gui.pose().translate(xCenter, yCenter, 0.0F);
            gui.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-20.0F));
            gui.pose().scale(scale, scale, scale);
            int redColor = 0xFFFF0000 | (alpha & 0xFF);
            gui.drawCenteredString(font, this.splash, 0, -8, redColor);
            gui.pose().popPose();
        }
    }
}
