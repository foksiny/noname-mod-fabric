package dev.noname.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.animal.WolfVariants;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables the biome-based wolf variants that vanilla 1.21 added: the
 * spawn-time hook {@link WolfVariants#getSpawnVariant} would otherwise pick a
 * {@link WolfVariant} from the registry by matching the spawn biome against
 * each variant's biome set, so a wolf spawned in a taiga becomes "woods", in a
 * savanna "pale", etc. Here the call always returns the
 * {@link WolfVariants#DEFAULT default} variant, so every wolf — no matter
 * where it spawns — looks like the original pale wolf.
 */
@Mixin(WolfVariants.class)
public abstract class WolfVariantsMixin {

    @Inject(method = "getSpawnVariant(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"), cancellable = true)
    private void noname$alwaysDefaultVariant(RegistryAccess registryAccess, Holder<Biome> biome,
                                             CallbackInfoReturnable<Holder<WolfVariant>> cir) {
        Registry<WolfVariant> registry =
                registryAccess.registryOrThrow(Registries.WOLF_VARIANT);
        cir.setReturnValue(registry.getHolderOrThrow(WolfVariants.DEFAULT));
    }
}
