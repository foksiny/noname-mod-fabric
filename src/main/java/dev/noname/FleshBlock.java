package dev.noname;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The Flesh Block: a squishy, sticky block that builds the day-8+ flesh
 * trees. Extends {@link SlimeBlock} to inherit the slime-block stickiness —
 * entities landing on it take no fall damage and get slowed down while
 * walking on it — but overrides {@link #updateEntityAfterFallOn} so it never
 * bounces: flesh holds on to you instead of throwing you back up.
 *
 * <p>Deliberately soft ({@code strength(0.7F)}): a hand can break it in
 * roughly one second.
 */
public class FleshBlock extends SlimeBlock {

    public FleshBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * Sticky landing: absorb the fall (no fall damage, no bounce) so the
     * entity just sinks onto the block.
     */
    @Override
    public void updateEntityAfterFallOn(BlockGetter blockGetter, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(blockGetter, entity);
            return;
        }
        entity.resetFallDistance();
    }
}
