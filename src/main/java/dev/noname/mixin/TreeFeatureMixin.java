package dev.noname.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * From day 4 on, generated trees have a 1% chance of spawning with the base
 * (bottom-most) trunk log already broken: the wood column the tree grew on
 * loses its lowest log, so the trunk appears to be floating on nothing.
 *
 * <p>Injects at the {@code RETURN} of {@link TreeFeature#place} (the single
 * entry that produces every tree, big or small) and, only when the feature
 * reports success, walks the column at the origin downward to the lowest log
 * block — that's the "base log" — and removes it. The day guard is read from
 * the {@link WorldGenLevel} via {@link dev.noname.DayCounter#currentDay}.
 */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {

    /** Chance that a generated tree spawns with its base log already removed. */
    private static final float BREAK_BASE_LOG_CHANCE = 0.01F;

    @Inject(method = "place", at = @At("RETURN"))
    private void noname$maybeBreakBaseLog(
            FeaturePlaceContext<TreeConfiguration> context,
            CallbackInfoReturnable<Boolean> cir) {
        Boolean ok = cir.getReturnValue();
        if (!Boolean.TRUE.equals(ok)) {
            return;
        }
        WorldGenLevel level = context.level();
        if (dev.noname.DayCounter.currentDay(level) < 4) {
            return;
        }
        RandomSource random = context.random();
        if (random.nextFloat() >= BREAK_BASE_LOG_CHANCE) {
            return;
        }

        // Walk the trunk column at the origin downward to the lowest log.
        BlockPos.MutableBlockPos cursor = context.origin().mutable();
        // First move up one step, since the origin's block is usually the dirt
        // the tree replaced; the trunk itself starts one block above.
        cursor.move(net.minecraft.core.Direction.UP);

        // Slide down to the bottom of the contiguous wood column.
        while (cursor.getY() > level.getMinBuildHeight()
                && isLog(level.getBlockState(cursor.below()))) {
            cursor.move(net.minecraft.core.Direction.DOWN);
        }

        if (!isLog(level.getBlockState(cursor))) {
            return; // nothing recognizable as a trunk log at this column
        }

        level.removeBlock(cursor, false);
    }

    private static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }
}
