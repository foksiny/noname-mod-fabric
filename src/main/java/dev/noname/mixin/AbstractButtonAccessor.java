package dev.noname.mixin;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link AbstractButton}'s private widget sprite set so
 * {@link MenuButtonGlitchMixin} can re-draw the button background in its
 * corrupted, band-torn form (including the hovered/disabled variants).
 */
@Mixin(AbstractButton.class)
public interface AbstractButtonAccessor {

    @Accessor("SPRITES")
    static WidgetSprites noname$getSprites() {
        throw new AssertionError();
    }
}
