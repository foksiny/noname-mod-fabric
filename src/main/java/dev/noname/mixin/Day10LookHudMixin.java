package dev.noname.mixin;

import dev.noname.client.Day10LookClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints the big red "DO YOU EVEN CARE?" text on top of everything while the
 * day-10+ lag event runs. The text teleports to a new random position every
 * few frames and blinks at a random alpha.
 */
@Mixin(Gui.class)
public abstract class Day10LookHudMixin {

    private static final String TEXT = "DO YOU EVEN CARE?";
    private static final float SCALE = 2.5F;

    @Inject(method = "render", at = @At("TAIL"))
    private void noname$renderDay10LookText(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        float alpha = Day10LookClient.textAlpha();
        if (alpha <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int color = (int) (alpha * 255.0F) << 24 | 0xFF0000;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(SCALE, SCALE, 1.0F);
        guiGraphics.drawString(font, TEXT,
                (int) (Day10LookClient.textX() / SCALE),
                (int) (Day10LookClient.textY() / SCALE),
                color, true);
        guiGraphics.pose().popPose();
    }
}
