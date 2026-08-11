package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Infected Block: a rotting, bruised-purple block with sickly green
 * veins that slowly consumes the world. Seeded once per player near their
 * bed on day 17 (see {@link InfectionHandler}), it spreads on every random
 * tick, transforming neighbouring blocks into more infected blocks.
 *
 * <p>The infection ramps with the passing days:
 * <ul>
 *   <li><b>Chance</b> — 40% per random tick on day 17, +15% per day, capped
 *       at 100% (day 21+).</li>
 *   <li><b>Neighbours per tick</b> — 1 block on days 17-21, then +1 every 5
 *       days, capped at 4. Combined with the natural exponential growth
 *       (every converted block starts spreading too), the world rots faster
 *       and faster.</li>
 * </ul>
 *
 * <p>What it converts: any solid, non-fluid block — terrain, trees, sand,
 * stone. What it never touches: air, fluids, blocks with block entities
 * (chests, furnaces, signs...), unbreakable blocks (bedrock, barrier) and
 * player-placed blocks ({@link PlayerPlacedBlocks}), so the player's base
 * survives while the world around it rots.
 *
 * <p>It only spreads through loaded chunks (like vanilla growth), and stops
 * entirely while {@link InfectionHandler} is paused (the {@code /noname
 * event stopall} command) or the {@code world_infection} config switch is
 * off. It is deliberately breakable by hand (~1.5 s, no drops), so the
 * player can fight it back.
 */
public class InfectedBlock extends Block {

    /** Base day the infection starts (scaled by the config speed level). */
    private static final int BASE_DAY = 17;

    /** Conversion chance per random tick on day 17. */
    private static final float BASE_CHANCE = 0.4F;

    /** Extra chance per day after the start day, capped at 100%. */
    private static final float CHANCE_PER_DAY = 0.15F;

    /** Neighbours converted per random tick on the start day. */
    private static final int BASE_NEIGHBOURS = 1;

    /** Every this many days after the start day, +1 neighbour per tick. */
    private static final int NEIGHBOURS_PER_STAGE = 5;

    /** Upper bound on neighbours converted per tick. */
    private static final int MAX_NEIGHBOURS = 4;

    public InfectedBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                           RandomSource random) {
        if (InfectionHandler.isPaused() || !ModConfig.isEnabled("world_infection")) {
            return;
        }
        long day = DayCounter.currentDay(level);
        long startDay = ModConfig.scaledDay(BASE_DAY);
        if (day < startDay) {
            return;
        }

        // Day-scaled conversion chance.
        float chance = Math.min(1.0F, BASE_CHANCE + CHANCE_PER_DAY * (day - startDay));
        if (random.nextFloat() >= chance) {
            return;
        }

        // Day-scaled number of neighbours to convert this tick.
        int count = Math.min(MAX_NEIGHBOURS,
                BASE_NEIGHBOURS + (int) ((day - startDay) / NEIGHBOURS_PER_STAGE));

        List<Direction> directions = new ArrayList<>(List.of(Direction.values()));
        for (int i = 0; i < count && !directions.isEmpty(); i++) {
            Direction direction = directions.remove(random.nextInt(directions.size()));
            BlockPos target = pos.relative(direction);
            if (canInfect(level, target)) {
                level.setBlock(target, ModBlocks.INFECTED_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    /**
     * {@return true if the block at {@code pos} may be consumed} Rejects
     * unloaded positions, air, fluids, block entities (inventories, signs),
     * unbreakable blocks (bedrock, barrier — {@code destroySpeed < 0}) and
     * blocks the player placed themselves.
     */
    private static boolean canInfect(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof InfectedBlock) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.getDestroySpeed(level, pos) >= 0.0F
                && !PlayerPlacedBlocks.isPlaced(level, pos);
    }
}
