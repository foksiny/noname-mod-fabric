package dev.noname.mixin;

import dev.noname.PillarHandler;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@link PillarHandler#maybePlacePillar} once per newly generated
 * chunk. {@link ChunkGenerator#applyBiomeDecoration} is the single
 * per-chunk decoration entry, so hooking its {@code RETURN} runs exactly once
 * per generated chunk — after every feature — and gives each chunk its
 * day-6+ pillar roll.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGenPillarMixin {

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void noname$maybePlacePillar(WorldGenLevel level, ChunkAccess chunk,
                                         StructureManager structureManager, CallbackInfo ci) {
        PillarHandler.maybePlacePillar(level, chunk);
    }
}
