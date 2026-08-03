package dev.noname;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/**
 * The Blood Flesh Block: the flesh block, but angrier. Same squishy
 * slime-block stickiness as {@link FleshBlock} (it extends the same base),
 * with a redder texture, but everything else is meaner:
 *
 * <ul>
 *   <li><b>Slow to break</b> — 5 seconds with the bare hand; 2 seconds with
 *       any pickaxe or axe ({@link #getDestroyProgress} overrides the normal
 *       hardness formula entirely so both timings are exact).</li>
 *   <li><b>Lethal while placed</b> — every second, every living entity within
 *       5 blocks takes 1 damage, and the block constantly spits blood drops
 *       (driven by {@link BloodFleshBlockHandler}).</li>
 * </ul>
 *
 * <p>Placement and removal are tracked through {@link #onPlace}/{@link
 * #onRemove} so the handler only ticks blocks that actually exist.
 */
public class BloodFleshBlock extends SlimeBlock {

    /** Destroy progress per tick by hand: 100 ticks = 5 seconds. */
    private static final float HAND_PROGRESS = 1.0F / 100.0F;

    /** Destroy progress per tick with a pickaxe or an axe: 40 ticks = 2 s. */
    private static final float TOOL_PROGRESS = 1.0F / 40.0F;

    public BloodFleshBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * Exact break times: 5 s by hand, 2 s with any pickaxe or axe. This
     * replaces the vanilla hardness formula, so the timings never depend on
     * the tool tier or enchantments.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        ItemStack stack = player.getMainHandItem();
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)) {
            return TOOL_PROGRESS;
        }
        return HAND_PROGRESS;
    }

    /** Starts the blood particles and the area damage while the block is in
     *  the world (server side only). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !state.is(oldState.getBlock())) {
            BloodFleshBlockHandler.track(serverLevel, pos);
        }
    }

    /** Stops the blood when the block is removed (server side only). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !state.is(newState.getBlock())) {
            BloodFleshBlockHandler.untrack(serverLevel, pos);
        }
    }
}
