package dev.noname.mixin;

import dev.noname.client.PigTintLayer;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the red tint layer ({@link PigTintLayer}) to the pig renderer so
 * infected day-5+ pigs render with a reddish overlay.
 */
@Mixin(PigRenderer.class)
public abstract class PigRendererTintMixin extends MobRenderer<Pig, PigModel<Pig>> {

    protected PigRendererTintMixin(EntityRendererProvider.Context context,
                                   PigModel<Pig> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void noname$addInfectedTintLayer(CallbackInfo ci) {
        this.addLayer(new PigTintLayer((PigRenderer) (Object) this));
    }
}
