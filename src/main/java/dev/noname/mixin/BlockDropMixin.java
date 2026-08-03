package dev.noname.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a block is broken, there is a 15% chance that its drops spawn 2 blocks
 * to a random side (horizontally) instead of where the block was broken.
 *
 * <p>In 1.21.1 {@link Block#dropResources} collects the loot and pops each
 * stack via {@link Block#popResource} (inside a lambda), so intercepting the
 * 3-arg {@code popResource} itself is the single choke point that covers
 * every block-break drop. When the roll hits, the stack is spawned at the
 * offset position with the exact same entity setup vanilla would use.
 */
@Mixin(Block.class)
public abstract class BlockDropMixin {

    /** Chance (0.0 - 1.0) that drops land off to the side. */
    private static final float SIDE_DROP_CHANCE = 0.15F;

    /** How far (in blocks) to the side the drops land. */
    private static final int SIDE_DROP_DISTANCE = 2;

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void noname$maybeOffsetDrop(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (level.getRandom().nextFloat() >= SIDE_DROP_CHANCE) {
            return;
        }

        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
        BlockPos target = pos.relative(side, SIDE_DROP_DISTANCE);

        // Same spawn setup as vanilla's popResource, just at the offset spot.
        double width = (double) EntityType.ITEM.getWidth();
        double spread = 1.0 - width;
        double half = width / 2.0;
        double x = Math.floor(target.getX()) + level.random.nextDouble() * spread + half;
        double y = Math.floor(target.getY()) + level.random.nextDouble() * spread;
        double z = Math.floor(target.getZ()) + level.random.nextDouble() * spread + half;

        ItemEntity drop = new ItemEntity(level, x, y, z, stack);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);

        ci.cancel();
    }
}
