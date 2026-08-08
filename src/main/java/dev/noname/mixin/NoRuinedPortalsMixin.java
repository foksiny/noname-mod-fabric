package dev.noname.mixin;

import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Stops abandoned (ruined) portals from ever generating: their structure is
 * {@link RuinedPortalStructure}, and every portal it would place starts from
 * {@code findGenerationPoint} returning a generation stub. Cancelling it makes
 * the structure report no generation point, so no portal frame, no lava pool
 * and no buried portal is ever placed in newly generated chunks. Existing
 * portals stay.
 */
@Mixin(RuinedPortalStructure.class)
public abstract class NoRuinedPortalsMixin {

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void noname$noRuinedPortals(Structure.GenerationContext context,
                                        CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        cir.setReturnValue(Optional.empty());
    }
}
