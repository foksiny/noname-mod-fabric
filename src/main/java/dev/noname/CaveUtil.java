package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Cave detection shared by the cave-driven events: the cave stalker
 * ({@link CaveZombieHandler}) and the invisible digger
 * ({@link CaveDiggingSoundHandler}).
 */
public final class CaveUtil {

    private CaveUtil() {
    }

    /**
     * {@return true when the player is standing underground: feet on solid
     * ground, head in the air, opaque terrain overhead within 24 blocks and
     * at least 5 blocks of rock between them and the surface} The Alpha
     * terrain's {@code alpha_is_ocean} is consulted first so an ocean floor
     * never counts as a cave.
     */
    public static boolean isInCave(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        if (TerrainUtil.isOcean(level, pos)) {
            return false;
        }
        if (!level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.below()).isSolid()) {
            return false;
        }
        boolean cover = false;
        int maxY = Math.min(pos.getY() + 24, level.getMaxBuildHeight() - 1);
        for (int y = pos.getY() + 1; y <= maxY; y++) {
            BlockState state = level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
            if (state.isAir() || state.canBeReplaced()) {
                continue;
            }
            cover = state.isSolidRender(level, pos) && !state.is(BlockTags.LEAVES);
            break;
        }
        if (!cover) {
            return false;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos.getX(), pos.getZ());
        return surface - pos.getY() >= 5;
    }
}
