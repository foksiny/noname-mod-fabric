package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers which blocks were placed by players, per dimension. The cave
 * stalker ({@link CaveZombie}) asks this which blocks are the player's own:
 * it prefers digging those, and only digs natural terrain when there is no
 * other way to reach the player.
 *
 * <p>Recorded by {@link dev.noname.mixin.PlayerBlockPlaceMixin} on every
 * successful block-item placement and forgotten by
 * {@code PlayerBlockBreakEvents} when the player breaks the block. A position
 * also stops counting once its block was replaced by something else (fire,
 * water, pistons, an explosion), so the stalker can never be tricked into
 * mining a block that is no longer the player's.
 *
 * <p>Kept in memory only: blocks placed before the server started are not
 * remembered, and the list is wiped on restart.
 */
public final class PlayerPlacedBlocks {

    /** Dimension key -> placed position -> the state that was placed there. */
    private static final Map<ResourceKey<Level>, Map<Long, BlockState>> PLACED = new HashMap<>();

    private PlayerPlacedBlocks() {
    }

    /** Records a block just placed by a player. */
    public static void record(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return;
        }
        PLACED.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                .put(pos.asLong(), state);
    }

    /** Forgets a block (player broke it, or the stalker dug it out). */
    public static void remove(ServerLevel level, BlockPos pos) {
        Map<Long, BlockState> byLevel = PLACED.get(level.dimension());
        if (byLevel != null) {
            byLevel.remove(pos.asLong());
            if (byLevel.isEmpty()) {
                PLACED.remove(level.dimension());
            }
        }
    }

    /**
     * {@return true if {@code pos} still holds the very block a player
     * placed there} The block at the position must still match the one
     * recorded at placement time.
     */
    public static boolean isPlaced(ServerLevel level, BlockPos pos) {
        Map<Long, BlockState> byLevel = PLACED.get(level.dimension());
        if (byLevel == null) {
            return false;
        }
        BlockState recorded = byLevel.get(pos.asLong());
        return recorded != null
                && level.getBlockState(pos).getBlock() == recorded.getBlock();
    }

    /**
     * {@return every recorded player-placed position within {@code radius}
     * blocks (horizontally) of {@code center} in {@code level}'s dimension}
     * Positions whose block no longer matches what was recorded can be
     * filtered by the caller with {@link #isPlaced}.
     */
    public static List<BlockPos> placedWithin(ServerLevel level, BlockPos center, double radius) {
        Map<Long, BlockState> byLevel = PLACED.get(level.dimension());
        if (byLevel == null || byLevel.isEmpty()) {
            return List.of();
        }
        double radiusSq = radius * radius;
        List<BlockPos> result = new ArrayList<>();
        for (Long key : byLevel.keySet()) {
            BlockPos pos = BlockPos.of(key);
            long offX = pos.getX() - center.getX();
            long offZ = pos.getZ() - center.getZ();
            if (offX * offX + offZ * offZ <= radiusSq) {
                result.add(pos);
            }
        }
        return result;
    }
}
