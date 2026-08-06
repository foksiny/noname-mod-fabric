package dev.noname.mixin;

import dev.noname.PlayerPlacedBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds {@link PlayerPlacedBlocks}: every block-item placement funnels
 * through {@code BlockItem.placeBlock} (survival and creative, main and
 * offhand), so a successful placement here is remembered for the cave
 * stalker.
 */
@Mixin(BlockItem.class)
public abstract class PlayerBlockPlaceMixin {

    @Inject(method = "placeBlock",
            at = @At("RETURN"))
    private void noname$trackPlayerPlacedBlock(BlockPlaceContext context, BlockState state,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && context.getLevel() instanceof ServerLevel serverLevel) {
            PlayerPlacedBlocks.record(serverLevel, context.getClickedPos(), state);
        }
    }
}
