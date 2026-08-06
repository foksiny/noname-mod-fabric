package dev.noname.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * From day 4 on, generated trees spawn with no leaves — until day 7, when
 * leaves come back: every call into {@link FoliagePlacer#createFoliage} (the
 * single public entry every foliage placer routes through) is short-circuited
 * on days 4-6 only, so no leaf blocks are placed; from day 7 on the call is
 * left alone and trees generate normally again.
 *
 * <p>The check is gated on {@link dev.noname.DayCounter#currentDay} over the
 * feature's {@link LevelSimulatedReader}; during worldgen that reader is a
 * {@link LevelAccessor} backed by the server's overworld, so the day count is
 * available. When it can't be resolved (e.g. a fully client-side preview with
 * no server overworld yet), the day reads as 0 and leaves are kept.
 */
@Mixin(FoliagePlacer.class)
public abstract class FoliagePlacerMixin {

    @Inject(method = "createFoliage(Lnet/minecraft/world/level/LevelSimulatedReader;Lnet/minecraft/world/level/levelgen/feature/foliageplacers/FoliagePlacer$FoliageSetter;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/TreeConfiguration;ILnet/minecraft/world/level/levelgen/feature/foliageplacers/FoliagePlacer$FoliageAttachment;II)V", at = @At("HEAD"), cancellable = true)
    private void noname$noLeavesFromDay4(
            LevelSimulatedReader level,
            FoliagePlacer.FoliageSetter foliageSetter,
            RandomSource random,
            TreeConfiguration config,
            int height,
            FoliagePlacer.FoliageAttachment attachment,
            int radius,
            int offset,
            CallbackInfo ci) {
        if (!(level instanceof LevelAccessor accessor)) {
            return;
        }
        long day = dev.noname.DayCounter.currentDay(accessor);
        if (day < dev.noname.config.ModConfig.scaledDay(4)
                || day >= dev.noname.config.ModConfig.scaledDay(7)
                || !dev.noname.config.ModConfig.isEnabled("leafless_trees")) {
            return;
        }
        // Days 4-6: don't place any leaves at all. From day 7 on the trees
        // are back to normal.
        ci.cancel();
    }
}
