package dev.noname.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Stops every ocean plant and coral from ever generating naturally: kelp,
 * seagrass, sea pickles and every coral block, plant, fan and wall fan (dead
 * variants included). Every block a worldgen feature places funnels into
 * {@code WorldGenRegion.setBlock} — kelp forests, seagrass beds, coral reefs,
 * all of it — so cancelling it there removes them all from chunk decoration.
 * Only new chunks are affected: existing plants stay, and players can still
 * place these blocks themselves.
 */
@Mixin(WorldGenRegion.class)
public abstract class NoOceanPlantsMixin {

    private static final Set<Block> OCEAN_PLANTS_AND_CORALS = Set.of(
            Blocks.KELP, Blocks.KELP_PLANT,
            Blocks.SEAGRASS, Blocks.TALL_SEAGRASS,
            Blocks.SEA_PICKLE,
            Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK,
            Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK,
            Blocks.HORN_CORAL_BLOCK,
            Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.DEAD_BRAIN_CORAL_BLOCK,
            Blocks.DEAD_BUBBLE_CORAL_BLOCK, Blocks.DEAD_FIRE_CORAL_BLOCK,
            Blocks.DEAD_HORN_CORAL_BLOCK,
            Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL,
            Blocks.FIRE_CORAL, Blocks.HORN_CORAL,
            Blocks.DEAD_TUBE_CORAL, Blocks.DEAD_BRAIN_CORAL,
            Blocks.DEAD_BUBBLE_CORAL, Blocks.DEAD_FIRE_CORAL,
            Blocks.DEAD_HORN_CORAL,
            Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN,
            Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN,
            Blocks.HORN_CORAL_FAN,
            Blocks.DEAD_TUBE_CORAL_FAN, Blocks.DEAD_BRAIN_CORAL_FAN,
            Blocks.DEAD_BUBBLE_CORAL_FAN, Blocks.DEAD_FIRE_CORAL_FAN,
            Blocks.DEAD_HORN_CORAL_FAN,
            Blocks.TUBE_CORAL_WALL_FAN, Blocks.BRAIN_CORAL_WALL_FAN,
            Blocks.BUBBLE_CORAL_WALL_FAN, Blocks.FIRE_CORAL_WALL_FAN,
            Blocks.HORN_CORAL_WALL_FAN,
            Blocks.DEAD_TUBE_CORAL_WALL_FAN, Blocks.DEAD_BRAIN_CORAL_WALL_FAN,
            Blocks.DEAD_BUBBLE_CORAL_WALL_FAN, Blocks.DEAD_FIRE_CORAL_WALL_FAN,
            Blocks.DEAD_HORN_CORAL_WALL_FAN
    );

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void noname$noOceanPlants(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (OCEAN_PLANTS_AND_CORALS.contains(state.getBlock())) {
            cir.setReturnValue(false);
        }
    }
}
